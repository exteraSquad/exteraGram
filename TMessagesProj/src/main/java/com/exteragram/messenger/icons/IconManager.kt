/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.icons

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.LongSparseArray
import android.util.SparseIntArray
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.collection.LruCache
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import com.exteragram.messenger.export.output.ExportFiles
import com.exteragram.messenger.icons.ui.components.InstallIconPackBottomSheet
import com.exteragram.messenger.icons.ui.components.ReplaceIconBottomSheet
import com.exteragram.messenger.icons.ui.picker.IconPickerController
import com.exteragram.messenger.utils.backgroundScope
import com.exteragram.messenger.utils.chats.ChatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessageObject
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.Utilities
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.LaunchActivity
import java.io.File
import org.simplifiles.SimpliFiles
import org.simplifiles.files.OverwritePolicy
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.max
import com.exteragram.messenger.IconPackType
import com.exteragram.messenger.editingIconPackId
import com.exteragram.messenger.iconPack
import com.exteragram.messenger.loadConfig
import androidx.core.util.size
import androidx.core.util.isEmpty

object IconManager {
    private val scope = backgroundScope()
    private val mutationDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val decodeDispatcher = Dispatchers.IO.limitedParallelism(2)
    private val prewarmDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val blacklistedIcons = setOf(
        "system", "smiles_popup",
        "camera_btn", "cancel_big",
        "chats_archive_box", "chats_archive_arrow", "chats_archive_muted",
        "chats_archive_pin", "chats_widget_preview", "circle_big",
        "contacts_widget_preview",
        "etg_splash", "ev_minus", "ev_plus",
        "filled_chatlink_large", "field_carret_empty",
        "dino_pic", "circle", "widgets_light_badgebg",
        "greydivider", "greydivider_bottom", "greydivider_top",
        "groups_limit1", "ic_ab_new", "ic_foreground", "ic_foreground_monet", "ic_foreground_solid", "ic_player",
        "ic_reply_icon", "icon_background_clip", "icon_background_clip_round",
        "icon_plane", "icplaceholder", "large_ads_info", "large_away", "large_greeting",
        "large_log_actions", "large_monetize", "large_quickreplies",
        "list_selector_ex", "livepin", "load_big", "location_empty",
        "login_arrow1", "login_phone1", "logo_middle",
        "map_pin", "map_pin2", "map_pin3", "map_pin_circle", "map_pin_cone2", "map_pin_photo", "msg_media_gallery",
        "music_empty", "no_passport", "no_password",
        "nophotos", "nophotos3", "paint_elliptical_brush",
        "paint_neon_brush", "paint_radial_brush", "phone_activate",
        "photo_placeholder_in", "photo_tooltip2",
        "photoview_placeholder", "screencast_big", "scrollbar_vertical_thumb",
        "scrollbar_vertical_thumb_inset",
        "newmsg_divider", "ic_launcher_dr",
        "smiles_info", "sms_bubble", "sms_devices", "stats_tooltip", "sticker", "story_camera",
        "theme_preview_image", "transparent", "venue_tooltip", "videopreview",
        "bluecounter", "photobadge", "photos_rounded", "calendar_date", "menu_copy", "redcircle",
        "tooltip_arrow", "tooltip_arrow_up", "newyear", "cards_chat", "community_cards",
        "ic_call_notification_answer", "ic_call_notification_decline",
        "shortcut_compose", "shortcut_user", "slide_dot_big", "slide_dot_small",
        "cocoon_logo", "cocoon_text", "telegram_logo", "telegram_logo_2",
        "keeper_icon", "diamond"
    )

    fun isBlacklisted(name: String) = blacklistedIcons.contains(name)
            || name.contains("avd")
            || name.endsWith("_solar")
            || name.endsWith("_remix")
            || name.contains("$")
            || name.contains("animationpin")
            || name.contains("googlepay")
            || name.contains("shadow")
            || name.startsWith("ic_monochrome")
            || name.startsWith("nocover")
            || name.startsWith("gradient_")
            || name.startsWith("stickers_back_")
            || name.startsWith("media_doc_")
            || name.startsWith("loading_animation")
            || name.startsWith("intro_")
            || name.startsWith("minibubble_")
            || name.startsWith("book_")
            || name.startsWith("call_")
            || name.startsWith("dice")
            || name.startsWith("msg_other_new_filled")
            || name.startsWith("profile_level")
            || name.startsWith("widget_")
            || name.startsWith("zoom_slide")
            || name.startsWith("zoom_round")
            || name.startsWith("popup_fixed_alert")
            || name.startsWith("search_dark")
            || name.startsWith("bar_selector")

    private class IntStringMap(expected: Int) {
        private var keys: IntArray = IntArray(0)
        private var values: Array<String?> = emptyArray()
        private var mask = 0
        private var shift = 0
        private var size = 0

        init {
            var capacity = 4
            while (capacity < expected * 2) capacity = capacity shl 1
            allocate(capacity)
        }

        private fun allocate(capacity: Int) {
            keys = IntArray(capacity)
            values = arrayOfNulls(capacity)
            mask = capacity - 1
            shift = 32 - Integer.numberOfTrailingZeros(capacity)
        }

        fun get(key: Int): String? {
            if (key == 0) return null
            var i = (key * GOLDEN) ushr shift
            while (true) {
                val k = keys[i]
                if (k == key) return values[i]
                if (k == 0) return null
                i = (i + 1) and mask
            }
        }

        fun put(key: Int, value: String) {
            if (key == 0) return
            if ((size + 1) * 2 > keys.size) grow()
            var i = (key * GOLDEN) ushr shift
            while (true) {
                val k = keys[i]
                if (k == 0) {
                    keys[i] = key
                    values[i] = value
                    size++
                    return
                }
                if (k == key) {
                    values[i] = value
                    return
                }
                i = (i + 1) and mask
            }
        }

        private fun grow() {
            val oldKeys = keys
            val oldValues = values
            allocate(keys.size shl 1)
            size = 0
            for (i in oldKeys.indices) {
                val k = oldKeys[i]
                if (k != 0) put(k, oldValues[i]!!)
            }
        }

        companion object {
            private const val GOLDEN = -1640531527
        }
    }

    val systemIcons = ConcurrentHashMap<String, Int>()

    @Volatile
    private var systemNames: IntStringMap? = null

    private const val RESOLVED_INDEX_ENTRIES = 2048
    private const val SYSTEM_NAMES_EXPECTED = 2048
    private const val PREWARM_MAX_ICONS = 256
    private const val PREWARM_BUDGET_MS = 150L
    private const val INITIALIZATION_TIMEOUT_MS = 1000L

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = max(1024, maxMemory / 8)

    private data class SourceCacheKey(
        val generation: Long,
        val packId: String,
        val location: String?,
        val fileName: String,
        val resId: Int,
        val density: Int
    )

    private class ResolvedIndex(private val capacity: Int) {
        private val entries = LongSparseArray<SourceCacheKey>()

        @Synchronized
        fun get(key: Long): SourceCacheKey? = entries.get(key)

        @Synchronized
        fun put(key: Long, value: SourceCacheKey) {
            if (entries.size >= capacity) entries.clear()
            entries.put(key, value)
        }

        @Synchronized
        fun clear() {
            entries.clear()
        }

        @Synchronized
        fun removeByResId(resId: Int) {
            for (i in entries.size - 1 downTo 0) {
                if ((entries.keyAt(i) shr 32).toInt() == resId) entries.removeAt(i)
            }
        }
    }

    private val resolvedCache = ResolvedIndex(RESOLVED_INDEX_ENTRIES)

    private val sourceCache = object : LruCache<SourceCacheKey, Bitmap>(cacheSize) {
        override fun sizeOf(key: SourceCacheKey, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    private val activePacks = CopyOnWriteArrayList<IconPack>()
    private val iconOwnerMap = ConcurrentHashMap<String, IconPack>()
    @Volatile
    private var basePreinstalledMap: SparseIntArray? = null
    private var initializationJob: Job? = null
    private var prewarmJob: Job? = null
    private val initialPacksLoaded = CountDownLatch(1)
    private var activePacksPublished = false
    @Volatile
    private var initializationTimedOut = false
    @Volatile
    private var initializationGeneration = 0L

    private fun resolvedCacheKey(resId: Int, density: Int): Long {
        return (resId.toLong() shl 32) or (density.toLong() and 0xffffffffL)
    }

    private fun sourceCacheKey(pack: IconPack, fileName: String, resId: Int, density: Int): SourceCacheKey {
        return SourceCacheKey(initializationGeneration, pack.id, pack.location?.absolutePath, fileName, resId, density)
    }

    private fun invalidateIconCaches(packId: String, resId: Int) {
        resolvedCache.removeByResId(resId)
        sourceCache.snapshot().keys
            .filter { it.packId == packId && it.resId == resId }
            .forEach { sourceCache.remove(it) }
    }

    @Synchronized
    private fun publishBitmap(
        generation: Long,
        resourceName: String,
        pack: IconPack,
        resId: Int,
        density: Int,
        bitmap: Bitmap
    ): Boolean {
        if (generation != initializationGeneration || iconOwnerMap[resourceName] != pack) return false
        val fileName = pack.icons[resourceName] ?: return false
        val sourceKey = sourceCacheKey(pack, fileName, resId, density)
        sourceCache.put(sourceKey, bitmap)
        resolvedCache.put(resolvedCacheKey(resId, density), sourceKey)
        return true
    }

    private enum class ActivePacksUpdate {
        STALE,
        UNCHANGED,
        CHANGED,
        INITIAL
    }

    init {
        initialize()
    }

    fun prefetchCustomPacks() {
        scope.launch {
            syncInstalledCustomPacks(IconPackStorage.getCustomPacks())
        }
    }

    @Synchronized
    private fun syncInstalledCustomPacks(packs: List<IconPack>): Boolean {
        var changed = false
        for (pack in packs) {
            val id = pack.id
            if (!iconPacksLayout.contains(id) && !iconPacksHidden.contains(id)) {
                iconPacksHidden.add(id)
                changed = true
            }
        }
        if (changed) saveIconPacksLayout()
        return changed
    }

    private fun rebuildOwnerMap() {
        iconOwnerMap.clear()
        for (i in activePacks.indices.reversed()) {
            val pack = activePacks[i]
            if (pack.isBase) continue
            for (key in pack.icons.keys) {
                iconOwnerMap[key] = pack
            }
        }
        rebuildBasePreinstalledMap()
    }

    private fun rebuildBasePreinstalledMap() {
        val merged = SparseIntArray()
        for (i in activePacks.indices) {
            val pack = activePacks[i]
            val map = pack.preinstalledMap ?: continue
            if (!pack.isBase) continue
            for (j in 0 until map.size) {
                val from = map.keyAt(j)
                if (merged.indexOfKey(from) < 0) {
                    merged.put(from, map.valueAt(j))
                }
            }
        }
        basePreinstalledMap = if (merged.isEmpty()) null else merged
    }

    @Synchronized
    private fun updateActivePacks(generation: Long, newActivePacks: List<IconPack>): ActivePacksUpdate {
        if (generation != initializationGeneration) {
            return ActivePacksUpdate.STALE
        }
        val initial = !activePacksPublished
        activePacksPublished = true
        if (!initial && activePacks == newActivePacks) {
            return ActivePacksUpdate.UNCHANGED
        }

        activePacks.clear()
        activePacks.addAll(newActivePacks)
        rebuildOwnerMap()

        resolvedCache.clear()
        sourceCache.evictAll()

        return if (initial) ActivePacksUpdate.INITIAL else ActivePacksUpdate.CHANGED
    }

    private fun resolvePackIconFile(pack: IconPack, iconFileName: String): File? {
        val packRoot = pack.location ?: File(IconPackStorage.getIconPacksDirectory(), pack.id)
        return try {
            SimpliFiles.directory(packRoot).file(iconFileName).file
        } catch (e: Exception) {
            FileLog.e("Failed to resolve icon file for pack ${pack.id}", e)
            null
        }
    }

    @Synchronized
    private fun launchPrewarm(generation: Long) {
        if (generation != initializationGeneration) return
        prewarmJob?.cancel()
        prewarmJob = scope.launch(prewarmDispatcher) {
            val density = AndroidUtilities.displayMetrics.densityDpi
            val deadline = SystemClock.elapsedRealtime() + PREWARM_BUDGET_MS
            var count = 0
            for ((resourceName, pack) in iconOwnerMap) {
                if (!isActive || generation != initializationGeneration) return@launch
                val resId = systemIcons[resourceName] ?: continue
                if (count++ >= PREWARM_MAX_ICONS) return@launch
                if (SystemClock.elapsedRealtime() >= deadline) return@launch
                val bitmap = getPackIconBitmap(pack, resId, density, null, resourceName, false) ?: continue
                if (!isActive || generation != initializationGeneration) return@launch
                publishBitmap(generation, resourceName, pack, resId, density, bitmap)
            }
        }
    }

    fun getDrawable(
        resId: Int,
        density: Int,
        theme: Resources.Theme?,
        resources: Resources = ApplicationLoader.applicationContext.resources
    ): Drawable? {
        if (iconOwnerMap.isEmpty()) return null
        val targetDensity = if (density == 0) AndroidUtilities.displayMetrics.densityDpi else density
        val key = resolvedCacheKey(resId, targetDensity)
        val cachedBitmap = resolvedCache.get(key)?.let { sourceCache[it] }
        if (cachedBitmap != null) {
            return cachedBitmap.toIconDrawable(resources)
        }

        val resourceName = systemNames?.get(resId) ?: try {
            ApplicationLoader.applicationContext.resources.getResourceEntryName(resId)
        } catch (_: Exception) {
            return null
        }

        val pack = iconOwnerMap[resourceName]
        if (pack != null) {
            val generation = initializationGeneration
            val bitmap = getPackIconBitmap(pack, resId, targetDensity, theme, resourceName, false)
            if (bitmap != null) {
                publishBitmap(generation, resourceName, pack, resId, targetDensity, bitmap)
                return bitmap.toIconDrawable(resources)
            }
        }

        return null
    }

    private fun Bitmap.toIconDrawable(resources: Resources): Drawable {
        val drawable = toDrawable(resources)
        val sourceDensity = ApplicationLoader.applicationContext.resources.displayMetrics.densityDpi
        val requestDensity = resources.displayMetrics.densityDpi
        if (density != Bitmap.DENSITY_NONE && sourceDensity != requestDensity) {
            drawable.setTargetDensity(requestDensity * density / sourceDensity)
        }
        return drawable
    }

    fun getCachedDrawable(resId: Int): Drawable? {
        if (iconOwnerMap.isEmpty()) return null
        return resolvedCache.get(resolvedCacheKey(resId, AndroidUtilities.displayMetrics.densityDpi))
            ?.let { sourceCache[it] }
            ?.toDrawable(ApplicationLoader.applicationContext.resources)
    }

    fun getPackIconDrawable(pack: IconPack, resId: Int): Drawable? {
        return getPackIconBitmap(pack, resId, AndroidUtilities.displayMetrics.densityDpi, null)
            ?.toDrawable(ApplicationLoader.applicationContext.resources)
    }

    private fun cachedPackIconBitmap(pack: IconPack, resId: Int, density: Int): Bitmap? {
        val resourceName = systemNames?.get(resId) ?: return null
        val iconFileName = pack.icons[resourceName] ?: return null
        return sourceCache[sourceCacheKey(pack, iconFileName, resId, density)]
    }

    fun getCachedPackIconDrawable(pack: IconPack, resId: Int): Drawable? {
        return cachedPackIconBitmap(pack, resId, AndroidUtilities.displayMetrics.densityDpi)
            ?.toDrawable(ApplicationLoader.applicationContext.resources)
    }

    fun requestPackIconDrawable(pack: IconPack, resId: Int, callback: Utilities.Callback<Drawable>) {
        val density = AndroidUtilities.displayMetrics.densityDpi
        cachedPackIconBitmap(pack, resId, density)?.let {
            callback.run(it.toDrawable(ApplicationLoader.applicationContext.resources))
            return
        }
        scope.launch(decodeDispatcher) {
            val bitmap = getPackIconBitmap(pack, resId, density, null) ?: return@launch
            withContext(Dispatchers.Main) {
                callback.run(bitmap.toDrawable(ApplicationLoader.applicationContext.resources))
            }
        }
    }

    fun requestDrawable(resId: Int, callback: Utilities.Callback<Drawable>) {
        if (iconOwnerMap.isEmpty()) return
        getCachedDrawable(resId)?.let {
            callback.run(it)
            return
        }
        val density = AndroidUtilities.displayMetrics.densityDpi
        scope.launch(decodeDispatcher) {
            val drawable = getDrawable(resId, density, null) ?: return@launch
            withContext(Dispatchers.Main) { callback.run(drawable) }
        }
    }

    fun requestDrawableForSize(resId: Int, sizePx: Int, callback: Utilities.Callback<Drawable>) {
        if (iconOwnerMap.isEmpty() || sizePx <= 0) return
        val resourceName = systemNames?.get(resId) ?: return
        val pack = iconOwnerMap[resourceName] ?: return
        val iconFileName = pack.icons[resourceName] ?: return
        scope.launch(decodeDispatcher) {
            val file = resolvePackIconFile(pack, iconFileName) ?: return@launch
            val density = AndroidUtilities.displayMetrics.densityDpi
            val bitmap = decodeIconFile(file.absolutePath, sizePx, sizePx, density) ?: return@launch
            withContext(Dispatchers.Main) {
                callback.run(bitmap.toDrawable(ApplicationLoader.applicationContext.resources))
            }
        }
    }

    private fun getPackIconBitmap(
        pack: IconPack,
        resId: Int,
        density: Int,
        theme: Resources.Theme?,
        knownResourceName: String? = null,
        cacheResult: Boolean = true
    ): Bitmap? {
        val resourceName = knownResourceName ?: systemNames?.get(resId) ?: try {
            ApplicationLoader.applicationContext.resources.getResourceEntryName(resId)
        } catch (_: Exception) {
            return null
        }

        val iconFileName = pack.icons[resourceName] ?: return null
        val key = sourceCacheKey(pack, iconFileName, resId, density)
        val cached = sourceCache[key]
        if (cached != null) return cached
        val file = resolvePackIconFile(pack, iconFileName) ?: return null

        val bitmap = createBitmapFromFile(file.absolutePath, resId, density, theme)
        if (bitmap != null && cacheResult) {
            sourceCache.put(key, bitmap)
        }
        return bitmap
    }

    fun createBitmapFromFile(
        path: String,
        originalResId: Int,
        density: Int,
        theme: Resources.Theme?
    ): Bitmap? {
        return try {
            val originalDrawable =
                (ApplicationLoader.applicationContext.resources as? ExteraResources)?.getOriginalDrawable(
                    originalResId
                )
                    ?: ResourcesCompat.getDrawableForDensity(
                        ApplicationLoader.applicationContext.resources,
                        originalResId,
                        density,
                        theme
                    )

            val targetW = maxOf(1, originalDrawable?.intrinsicWidth ?: AndroidUtilities.dp(24f))
            val targetH = maxOf(1, originalDrawable?.intrinsicHeight ?: AndroidUtilities.dp(24f))
            decodeIconFile(path, targetW, targetH, density)
        } catch (e: OutOfMemoryError) {
            FileLog.e("Out of memory loading icon bitmap: $path", e)
            null
        } catch (e: Exception) {
            FileLog.e("Error loading icon bitmap: $path", e)
            null
        }
    }

    private fun decodeIconFile(path: String, targetW: Int, targetH: Int, density: Int): Bitmap? {
        return try {
            val sourceFile = File(path)
            DecodedIconStore.get(sourceFile, targetW, targetH, density)?.let { return it }

            if (path.endsWith(".svg", ignoreCase = true)) {
                val bitmap = SvgDocumentPreview.renderIcon(sourceFile, targetW, targetH) ?: return null
                bitmap.density = density
                DecodedIconStore.put(sourceFile, targetW, targetH, density, bitmap)
                return bitmap
            }

            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, options)
            if (options.outWidth <= 0 || options.outHeight <= 0 ||
                options.outWidth.toLong() * options.outHeight > 100_000_000L) return null

            options.inSampleSize = 1
            if (options.outHeight > targetH || options.outWidth > targetW) {
                val halfHeight: Int = options.outHeight / 2
                val halfWidth: Int = options.outWidth / 2
                while (halfHeight / options.inSampleSize >= targetH && halfWidth / options.inSampleSize >= targetW) {
                    options.inSampleSize *= 2
                }
            }
            val maxPixels = maxOf(targetW.toLong() * targetH, 1_048_576L)
            while (true) {
                val width = (options.outWidth.toLong() + options.inSampleSize - 1) / options.inSampleSize
                val height = (options.outHeight.toLong() + options.inSampleSize - 1) / options.inSampleSize
                if (width * height <= maxPixels) break
                options.inSampleSize *= 2
            }
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            val bitmap = BitmapFactory.decodeFile(path, options) ?: return null

            val result = if (bitmap.width != targetW || bitmap.height != targetH) {
                val scaled = bitmap.scale(targetW, targetH, filter = true)
                if (scaled != bitmap) {
                    bitmap.recycle()
                }
                scaled.density = density
                scaled
            } else {
                bitmap.density = density
                bitmap
            }
            DecodedIconStore.put(sourceFile, targetW, targetH, density, result)
            result
        } catch (e: OutOfMemoryError) {
            FileLog.e("Out of memory loading icon bitmap: $path", e)
            null
        } catch (e: Exception) {
            FileLog.e("Error loading icon bitmap: $path", e)
            null
        }
    }

    fun saveCustomIcon(packId: String, resId: Int, tempFile: File, originalName: String?) {
        val resourceName = systemNames?.get(resId) ?: try {
            ApplicationLoader.applicationContext.resources.getResourceEntryName(resId)
        } catch (_: Exception) {
            return
        }

        scope.launch(mutationDispatcher) {
            var stagedFile: File? = null
            try {
                val packToEdit = IconPackStorage.findPackById(packId) ?: return@launch
                val ext = tempFile.extension
                val sanitized = originalName?.let(ExportFiles::fileNameFromUserString).orEmpty()
                val baseName = sanitized.ifEmpty { resourceName }
                val stem = baseName.substringBeforeLast('.', baseName).ifEmpty { resourceName }
                    .take(64).dropLastWhile { it.isHighSurrogate() }
                val packDirectory = SimpliFiles.directory(
                    File(IconPackStorage.getIconPacksDirectory(), packToEdit.id)
                ).create()
                var destination: File
                do {
                    destination = packDirectory.file("${stem}_${UUID.randomUUID()}.$ext").file
                } while (destination.exists())
                stagedFile = destination
                SimpliFiles.file(tempFile).copyTo(destination, OverwritePolicy.ERROR)

                val updatedMap = packToEdit.icons.toMutableMap()
                val previousFileName = updatedMap.put(resourceName, destination.name)
                val updatedPack = packToEdit.copy(icons = updatedMap)
                if (!IconPackStorage.saveIconPackMetadata(updatedPack)) return@launch
                stagedFile = null

                if (previousFileName != null && previousFileName !in updatedMap.values) {
                    resolvePackIconFile(packToEdit, previousFileName)?.let { oldFile ->
                        runCatching { if (oldFile.exists()) SimpliFiles.file(oldFile).delete() }
                            .onFailure { FileLog.e("Failed to delete old icon", it) }
                    }
                }

                val density = AndroidUtilities.displayMetrics.densityDpi
                val preDecoded = createBitmapFromFile(destination.absolutePath, resId, density, null)
                withContext(Dispatchers.Main) {
                    val index = activePacks.indexOfFirst { it.id == updatedPack.id }
                    if (index != -1) {
                        activePacks[index] = updatedPack
                        rebuildOwnerMap()
                        invalidateIconCaches(updatedPack.id, resId)
                        preDecoded?.let {
                            publishBitmap(initializationGeneration, resourceName, updatedPack, resId, density, it)
                        }
                    }
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.iconPackUpdated)
                }
            } catch (e: Exception) {
                FileLog.e("Failed to save custom icon", e)
            } finally {
                runCatching { if (tempFile.exists()) SimpliFiles.file(tempFile).delete() }
                stagedFile?.let { runCatching { if (it.exists()) SimpliFiles.file(it).delete() } }
            }
        }
    }

    fun resetCustomIcon(packId: String, resId: Int) {
        val resourceName = systemNames?.get(resId) ?: try {
            ApplicationLoader.applicationContext.resources.getResourceEntryName(resId)
        } catch (_: Exception) {
            return
        }

        scope.launch(mutationDispatcher) {
            val packToEdit = IconPackStorage.findPackById(packId) ?: return@launch
            val iconFileName = packToEdit.icons[resourceName] ?: return@launch
            val updatedMap = packToEdit.icons.toMutableMap()
            updatedMap.remove(resourceName)

            val updatedPack = packToEdit.copy(icons = updatedMap)
            if (!IconPackStorage.saveIconPackMetadata(updatedPack)) return@launch

            if (iconFileName !in updatedMap.values) {
                runCatching {
                    val file = resolvePackIconFile(packToEdit, iconFileName)
                    if (file != null && file.exists()) {
                        SimpliFiles.file(file).delete()
                    }
                }.onFailure { FileLog.e("Failed to delete old icon", it) }
            }

            withContext(Dispatchers.Main) {
                val index = activePacks.indexOfFirst { it.id == updatedPack.id }
                if (index != -1) {
                    activePacks[index] = updatedPack
                    rebuildOwnerMap()
                    invalidateIconCaches(updatedPack.id, resId)
                }

                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.iconPackUpdated)
            }
        }
    }

    fun getIcon(resId: Int): Int {
        val map = basePreinstalledMap ?: return resId
        val mapped = map.get(resId, -1)
        return if (mapped != -1) mapped else resId
    }

    @JvmStatic
    fun getNotificationIcon(): IconCompat {
        return try {
            loadConfig()

            val resourceName = "notification"
            val customPack = iconPacksLayout
                .asSequence()
                .filterNot { it.startsWith(IconPack.PREFIX_BASE) }
                .mapNotNull { IconPackStorage.findPackById(it) }
                .firstOrNull { it.icons.containsKey(resourceName) }
            if (customPack != null) {
                val icon = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    IconPackProvider.getIconUri(customPack.id, resourceName)
                        ?.let { uri ->
                            try {
                                ApplicationLoader.applicationContext.grantUriPermission(
                                    "com.android.systemui",
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            } catch (e: Exception) {
                                FileLog.e(e)
                            }
                            IconCompat.createWithContentUri(uri)
                        }
                } else {
                    getPackIconBitmap(customPack, R.drawable.notification, AndroidUtilities.displayMetrics.densityDpi, null, resourceName)
                        ?.let { IconCompat.createWithBitmap(it) }
                }
                if (icon != null) return icon
            }

            val mappedResource = iconPacksLayout
                .asSequence()
                .filter { it.startsWith(IconPack.PREFIX_BASE) }
                .mapNotNull { BaseIconPacks.getBasePack(it)?.preinstalledMap }
                .map { it.get(R.drawable.notification, -1) }
                .firstOrNull { it != -1 }
                ?: R.drawable.notification
            IconCompat.createWithResource(ApplicationLoader.applicationContext, mappedResource)
        } catch (e: Exception) {
            FileLog.e("Failed to resolve notification icon", e)
            IconCompat.createWithResource(ApplicationLoader.applicationContext, R.drawable.notification)
        }
    }

    @JvmStatic
    fun getNotificationSystemIcon(): Icon =
        getNotificationIcon().toIcon(ApplicationLoader.applicationContext)

    @Synchronized
    fun initialize(update: Boolean = false) {
        if (initializationJob?.isActive == true && !update) return
        if (update) {
            initializationJob?.cancel()
        }
        prewarmJob?.cancel()
        val generation = ++initializationGeneration

        initializationJob = scope.launch {
            val result = try {
                loadConfig()

                val newActivePacks = mutableListOf<IconPack>()
                for (packId in iconPacksLayout) {
                    val pack = if (packId.startsWith(IconPack.PREFIX_BASE)) {
                        BaseIconPacks.getBasePack(packId)
                    } else {
                        IconPackStorage.findPackById(packId)
                    }
                    if (pack != null) {
                        newActivePacks.add(pack)
                    }
                }

                if (generation != initializationGeneration) {
                    return@launch
                }

                updateActivePacks(generation, newActivePacks)
            } finally {
                initialPacksLoaded.countDown()
            }

            if (systemIcons.isEmpty()) {
                try {
                    val fields = R.drawable::class.java.fields
                    val names = IntStringMap(SYSTEM_NAMES_EXPECTED)
                    for (field in fields) {
                        val name = field.name
                        if (!isBlacklisted(name)) {
                            val id = field.getInt(null)
                            systemIcons[name] = id
                            names.put(id, name)
                        }
                    }
                    systemNames = names
                } catch (e: Exception) {
                    FileLog.e(e)
                }
            }

            when (result) {
                ActivePacksUpdate.STALE -> return@launch
                ActivePacksUpdate.UNCHANGED -> {
                    if (update) {
                        resolvedCache.clear()
                        sourceCache.evictAll()
                        withContext(Dispatchers.Main) {
                            if (generation != initializationGeneration) {
                                return@withContext
                            }
                            NotificationCenter.getGlobalInstance()
                                .postNotificationName(NotificationCenter.iconPackUpdated)
                        }
                    }
                    launchPrewarm(generation)
                    return@launch
                }
                ActivePacksUpdate.CHANGED, ActivePacksUpdate.INITIAL -> Unit
            }

            withContext(Dispatchers.Main) {
                if (generation != initializationGeneration) {
                    return@withContext
                }
                if (editingIconPackId != null) {
                    val pack = activePacks.firstOrNull { it.id == editingIconPackId }
                    val activeCustomPack = activePacks.firstOrNull { !it.isBase }

                    if (pack == null || pack.id != activeCustomPack?.id) {
                        editingIconPackId = null
                        LaunchActivity.getSafeLastFragment()?.parentActivity?.let { activity ->
                            if (activity is LaunchActivity) {
                                IconPickerController.setActive(activity, false)
                            }
                        }
                    } else {
                        LaunchActivity.getSafeLastFragment()?.parentActivity?.let { activity ->
                            if (activity is LaunchActivity) {
                                IconPickerController.setActive(activity, true)
                            }
                        }
                    }
                }

                if (result == ActivePacksUpdate.INITIAL && !initializationTimedOut) {
                    return@withContext
                }

                NotificationCenter.getGlobalInstance()
                    .postNotificationName(NotificationCenter.iconPackUpdated)
                LaunchActivity.getSafeLastFragment()?.let {
                    Theme.reloadAllResources(it.parentActivity)
                    if (result == ActivePacksUpdate.INITIAL) {
                        (it.parentActivity as? LaunchActivity)?.rebuildAllFragments(true)
                    } else {
                        it.parentLayout?.rebuildFragments(0)
                    }
                }
            }
            launchPrewarm(generation)
        }
    }

    fun awaitInitialization() {
        try {
            if (!initialPacksLoaded.await(INITIALIZATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                initializationTimedOut = true
                FileLog.e("Icon packs were not loaded in ${INITIALIZATION_TIMEOUT_MS}ms")
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    fun setActiveCustomPack(packId: String?) {
        if (packId == null) return
        if (!iconPacksLayout.contains(packId)) {
            iconPacksLayout.add(packId)
            iconPacksHidden.remove(packId)
            saveIconPacksLayout()
            initialize(true)
        }
    }

    fun findPackById(packId: String): IconPack? = IconPackStorage.findPackById(packId)

    fun bundlePackBlocking(packId: String): File? = IconPackStorage.bundlePackBlocking(packId)

    fun getCustomPacks(): List<IconPack> = IconPackStorage.getCustomPacks()

    fun saveIconPackMetadata(iconPack: IconPack): Boolean {
        val savedPack = runBlocking(mutationDispatcher) {
            val currentIcons = IconPackStorage.findPackById(iconPack.id)?.icons
            val packToSave = currentIcons?.let { iconPack.copy(icons = it) } ?: iconPack
            if (IconPackStorage.saveIconPackMetadata(packToSave)) packToSave else null
        } ?: return false

        scope.launch(Dispatchers.Main) {
            val index = activePacks.indexOfFirst { it.id == savedPack.id }
            if (index != -1) {
                activePacks[index] = savedPack
                rebuildOwnerMap()
                resolvedCache.clear()
                sourceCache.evictAll()
            }

            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.iconPackUpdated)
        }
        return true
    }

    fun deletePack(packId: String) {
        scope.launch(mutationDispatcher) {
            IconPackStorage.deletePack(packId)
            withContext(Dispatchers.Main) {
                if (iconPacksLayout.contains(packId) || iconPacksHidden.contains(packId)) {
                    iconPacksLayout.remove(packId)
                    iconPacksHidden.remove(packId)
                    saveIconPacksLayout()
                }
                initialize(true)
            }
        }
    }

    fun isIconPack(messageObject: MessageObject?): Boolean =
        isIconPack(ChatUtils.getInstance().getPathToMessage(messageObject), messageObject)

    fun isIconPack(path: String, messageObject: MessageObject?): Boolean =
        messageObject?.documentName != null && path.isNotEmpty() && path.endsWith(".icons")

    fun handleIconPack(baseFragment: BaseFragment, messageObject: MessageObject) {
        handleIconPack(baseFragment, ChatUtils.getInstance().getPathToMessage(messageObject))
    }

    private fun iconPackErrorText(error: IconPackStorageError): String {
        return LocaleController.getString(
            when (error) {
                IconPackStorageError.INVALID_ARCHIVE -> R.string.IconPackErrorInvalidArchive
                IconPackStorageError.MISSING_METADATA -> R.string.IconPackErrorMissingMetadata
                IconPackStorageError.METADATA_TOO_LARGE -> R.string.IconPackErrorMetadataTooLarge
                IconPackStorageError.INVALID_METADATA -> R.string.IconPackErrorInvalidMetadata
                IconPackStorageError.TOO_MANY_FILES -> R.string.IconPackErrorTooManyFiles
                IconPackStorageError.ARCHIVE_TOO_LARGE -> R.string.IconPackErrorArchiveTooLarge
                IconPackStorageError.FILE_TOO_LARGE -> R.string.IconPackErrorFileTooLarge
                IconPackStorageError.COMPRESSION_RATIO_TOO_HIGH -> R.string.IconPackErrorCompressionRatioTooHigh
                IconPackStorageError.STORAGE_ERROR -> R.string.IconPackErrorStorage
                IconPackStorageError.UNKNOWN -> R.string.UnknownError
            }
        )
    }

    private fun showIconPackError(baseFragment: BaseFragment, error: IconPackStorageError) {
        BulletinFactory.of(baseFragment).createSimpleBulletin(R.raw.error, iconPackErrorText(error)).show()
    }

    fun handleIconPack(baseFragment: BaseFragment, path: String) {
        scope.launch {
            val file = File(path)
            val packResult = IconPackStorage.parsePackFromZip(file)
            withContext(Dispatchers.Main) {
                when (packResult) {
                    is IconPackStorageResult.Success -> {
                        val pack = packResult.value
                        val bottomSheet = InstallIconPackBottomSheet(baseFragment.parentActivity, pack) { enable, update ->
                            scope.launch {
                                val installResult = IconPackStorage.installPack(file)
                                withContext(Dispatchers.Main) {
                                    when (installResult) {
                                        is IconPackStorageResult.Success -> {
                                            BulletinFactory.of(baseFragment).createSimpleBulletin(
                                                R.raw.contact_check,
                                                LocaleController.formatString(
                                                    if (update) R.string.PluginUpdated else R.string.PluginInstalled,
                                                    pack.name
                                                )
                                            ).show()
                                            if (enable) {
                                                setActiveCustomPack(pack.id)
                                            } else {
                                                syncInstalledCustomPacks(listOf(pack))
                                                initialize(true)
                                            }
                                        }
                                        is IconPackStorageResult.Failure -> showIconPackError(baseFragment, installResult.error)
                                    }
                                }
                            }
                        }
                        baseFragment.showDialog(bottomSheet)
                    }
                    is IconPackStorageResult.Failure -> showIconPackError(baseFragment, packResult.error)
                }
            }
        }
    }

    private val resultCallbacks = android.util.SparseArray<(Uri?) -> Unit>()

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        val callback = resultCallbacks.get(requestCode) ?: return false
        resultCallbacks.remove(requestCode)
        callback.invoke(data?.data)
        return true
    }

    fun startIconPicker(activity: Activity, selectFromFiles: Boolean, callback: (Uri?) -> Unit) {
        val requestCode = 43
        resultCallbacks.put(requestCode, callback)

        val intent = if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(activity) && !selectFromFiles) {
            ActivityResultContracts.PickVisualMedia().createIntent(
                activity,
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } else {
            val action = if (selectFromFiles) Intent.ACTION_OPEN_DOCUMENT else Intent.ACTION_GET_CONTENT
            Intent(action).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "image/svg+xml"))
                if (selectFromFiles) {
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
            }
        }
        activity.startActivityForResult(intent, requestCode)
    }

    @JvmOverloads
    fun showReplaceAlert(context: Context, resId: Int, iconPack: IconPack? = null) {
        val packToEdit = iconPack ?: if (editingIconPackId != null) {
            activePacks.firstOrNull { it.id == editingIconPackId }
        } else {
            activePacks.firstOrNull { !it.isBase }
        } ?: return

        val baseFragment = LaunchActivity.getSafeLastFragment() ?: return
        baseFragment.showDialog(ReplaceIconBottomSheet(context, resId, packToEdit))
    }

    fun isBasePackOnly(basePackType: IconPackType): Boolean {
        return iconPack == basePackType && activePacks.none { !it.isBase }
    }
}
