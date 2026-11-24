/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.text.TextUtils
import androidx.core.content.edit
import com.chaquo.python.PyObject
import com.exteragram.hook.HookHandle
import com.exteragram.messenger.editor
import com.exteragram.messenger.pinnedPlugins
import com.exteragram.messenger.plugins.analyzer.AnalyzerUpdater
import com.exteragram.messenger.plugins.hooks.EventHookRecord
import com.exteragram.messenger.plugins.hooks.HookRecord
import com.exteragram.messenger.plugins.hooks.MenuItemRecord
import com.exteragram.messenger.plugins.hooks.PartialHookRecord
import com.exteragram.messenger.plugins.hooks.PluginsHooks
import com.exteragram.messenger.plugins.hooks.XposedHookRecord
import com.exteragram.messenger.plugins.models.SettingItem
import com.exteragram.messenger.plugins.pip.PipController
import com.exteragram.messenger.plugins.catalog.PluginRepositoryLinks
import com.exteragram.messenger.plugins.ui.PluginsActivity
import com.exteragram.messenger.plugins.ui.components.InstallPluginBottomSheet
import com.exteragram.messenger.plugins.ui.components.SafeModeBottomSheet
import com.exteragram.messenger.plugins.utils.MenuContextBuilder
import com.exteragram.messenger.plugins.utils.NativeCrashHandler
import com.exteragram.messenger.plugins.utils.PluginsWatchdog
import com.exteragram.messenger.pluginsDisableArtOpts
import com.exteragram.messenger.pluginsEngine
import com.exteragram.messenger.pluginsSafeMode
import com.exteragram.messenger.preferences as exteraPreferences
import com.exteragram.messenger.preferences.utils.SettingsRegistry
import com.exteragram.messenger.utils.chats.ChatUtils
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import org.simplifiles.SimpliFiles
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.DispatchQueue
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessageObject
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.SendMessagesHelper
import org.telegram.messenger.Utilities
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.Bulletin
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.LaunchActivity
import java.io.File
import java.lang.reflect.Member
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class PluginsController private constructor() : PluginsHooks {

    val plugins = ConcurrentHashMap<String, Plugin>()

    val settings = ConcurrentHashMap<String, List<SettingItem>>()

    private val menuItemsById = ConcurrentHashMap<String, MenuItemRecord>()
    private val menuItemsByMenuType =
        ConcurrentHashMap<String, CopyOnWriteArrayList<MenuItemRecord>>()

    private val hooks = ConcurrentHashMap<String, MutableSet<HookRecord>>()
    private val interestedPluginsCache = ConcurrentHashMap<String, List<String>>()

    private val fileIconIdsByExtension = ConcurrentHashMap<String, Int>()
    private val fileIconDrawablesById = ConcurrentHashMap<Int, Drawable>()
    private val nextFileIconId = AtomicInteger(PLUGIN_FILE_ICON_ID_START)

    @Volatile
    private var substringMatchEventHooksCache: List<EventHookRecord> = emptyList()

    @Volatile
    private var exactMatchEventHooksCache: Map<String, List<EventHookRecord>> = emptyMap()

    @Volatile
    private var hookedMembersCache: Map<String, List<Member>> = emptyMap()

    private val hooksCacheLock = Any()

    @Volatile
    private var hooksCacheDirty = true

    @Volatile
    private var initialized = false

    @Volatile
    private var stopping = false

    private class PendingInitialization(var safeMode: Boolean) {
        val callbacks = ArrayList<Runnable>()
    }

    private var pendingInitialization: PendingInitialization? = null
    private val shutdownCallbacks = ArrayList<Runnable>()
    private val lifecycleGeneration = AtomicInteger()

    var pluginsDir: File = File(ApplicationLoader.getFilesDirFixed(), PluginsConstants.PLUGINS)

    var preferences: SharedPreferences =
        ApplicationLoader.applicationContext.getSharedPreferences(
            "plugin_settings",
            Activity.MODE_PRIVATE
        )

    val watchdog = PluginsWatchdog(this)

    private val updateNotificationRunnable = Runnable {
        NotificationCenter.getGlobalInstance()
            .postNotificationName(NotificationCenter.pluginsUpdated)
        NotificationCenter.getGlobalInstance()
            .postNotificationName(NotificationCenter.pluginMenuItemsUpdated)
    }

    fun getPluginEngine(pluginId: String?): PluginsEngine? {
        if (pluginId.isNullOrEmpty()) {
            return null
        }
        val plugin = plugins[pluginId] ?: return null
        plugin.cachedEngine?.let { return it }

        val engine = plugin.getEngine() ?: return null
        val engineInstance = getEngines()[engine]
        if (engineInstance != null) {
            plugin.cachedEngine = engineInstance
        }
        return engineInstance
    }

    fun init() {
        init(false, null)
    }

    fun init(onDone: Runnable?) {
        init(false, onDone)
    }

    fun init(startWithSafeMode: Boolean) {
        init(startWithSafeMode, null)
    }

    fun init(startWithSafeMode: Boolean, onDone: Runnable?) {
        if (!isPluginEngineSupported() || !pluginsEngine) {
            initialized = false
            onDone?.run()
            return
        }

        val generation = synchronized(this) {
            if (stopping) {
                val pending = pendingInitialization ?: PendingInitialization(startWithSafeMode).also {
                    pendingInitialization = it
                }
                pending.safeMode = startWithSafeMode
                if (onDone != null) {
                    pending.callbacks.add(onDone)
                }
                return
            }
            lifecycleGeneration.incrementAndGet()
        }
        NativeCrashHandler.checkAndHandleNativeCrash()
        watchdog.start()
        ensurePreferences()

        try {
            val hadCrash = preferences.getBoolean("had_crash", false)
            val crashedPluginId = preferences.getString("crashed_plugin_id", null)
            val manualCrash =
                crashedPluginId != null && crashedPluginId == "manual!" || startWithSafeMode

            preferences.edit().remove("had_crash").remove("crashed_plugin_id").apply()

            if (hadCrash) {
                if (crashedPluginId != null && !manualCrash) {
                    preferences.edit {
                        putBoolean(PREF_PLUGIN_ENABLED_KEY_PREFIX + crashedPluginId, false)
                    }
                } else {
                    editor.putBoolean(
                        "pluginsSafeMode",
                        true.also { pluginsSafeMode = true }).apply()
                }
                if (!manualCrash) {
                    AndroidUtilities.runOnUIThread({
                        val fragment = LaunchActivity.getLastFragment()
                        if (fragment != null) {
                            SafeModeBottomSheet(fragment).show()
                        }
                    }, 800)
                }
            } else {
                editor.putBoolean(
                    "pluginsSafeMode",
                    startWithSafeMode.also { pluginsSafeMode = it }).apply()
            }
        } catch (_: Exception) {
        }

        pluginsDir = File(ApplicationLoader.getFilesDirFixed(), PluginsConstants.PLUGINS)
        if (!pluginsDir.exists()) {
            SimpliFiles.directory(pluginsDir).create()
        }

        val engines = getEngines().values.toList()
        val loadedEngines = AtomicInteger(0)
        val callback = Runnable {
            if (loadedEngines.incrementAndGet() == engines.size) {
                if (lifecycleGeneration.get() == generation && !stopping) {
                    initialized = true
                }
                onDone?.run()
            }
        }

        runOnPluginsQueue {
            for (engine in engines) {
                if (lifecycleGeneration.get() != generation || stopping) {
                    callback.run()
                    continue
                }
                try {
                    engine.init(callback)
                } catch (t: Throwable) {
                    FileLog.e("Failed to initialize plugin engine", t)
                    callback.run()
                }
            }
        }
    }

    fun checkDevServers() {
        for (engine in getEngines().values) {
            engine.checkDevServer()
        }
    }

    fun shutdown(onDone: Runnable?) {
        synchronized(this) {
            if (onDone != null) {
                shutdownCallbacks.add(onDone)
            }
            if (stopping) {
                return
            }
            stopping = true
            lifecycleGeneration.incrementAndGet()
        }
        runOnPluginsQueue {
            val engines = getEngines().values.toList().sortedBy { it is PythonPluginsEngine }
            for (engine in engines) {
                if (engine is PythonPluginsEngine) {
                    engine.stopPluginTasks()
                }
            }
            fun shutdownNext(index: Int) {
                if (index == engines.size) {
                    for (pluginId in (plugins.keys + hooks.keys + settings.keys + menuItemsById.values.map { it.pluginId }).toSet()) {
                        runCatching { cleanupPlugin(pluginId) }
                            .onFailure { FileLog.e("Failed to clean up plugin $pluginId", it) }
                    }
                    watchdog.stop()
                    plugins.clear()
                    settings.clear()
                    interestedPluginsCache.clear()
                    initialized = false
                    val (callbacks, pending) = synchronized(this) {
                        stopping = false
                        val callbacks = ArrayList(shutdownCallbacks)
                        shutdownCallbacks.clear()
                        val pending = pendingInitialization
                        pendingInitialization = null
                        callbacks to pending
                    }
                    FileLog.d("Plugin system shut down.")
                    for (callback in callbacks) {
                        runCatching { callback.run() }.onFailure { FileLog.e(it) }
                    }
                    if (pending != null) {
                        init(pending.safeMode) {
                            for (callback in pending.callbacks) {
                                runCatching { callback.run() }.onFailure { FileLog.e(it) }
                            }
                        }
                    }
                    return
                }
                val completed = AtomicBoolean()
                val callback = Runnable {
                    if (completed.compareAndSet(false, true)) {
                        runOnPluginsQueue { shutdownNext(index + 1) }
                    }
                }
                try {
                    engines[index].shutdown(callback)
                } catch (t: Throwable) {
                    FileLog.e("Failed to shut down plugin engine", t)
                    callback.run()
                }
            }
            shutdownNext(0)
        }
    }

    fun restart() {
        restart(pluginsSafeMode)
    }

    fun restart(startWithSafeMode: Boolean) {
        FileLog.d("Restarting plugins engine...")
        shutdown {
            if (pluginsEngine) {
                init(startWithSafeMode) { FileLog.d("Plugins engine restarted.") }
            }
        }
    }

    fun isInitialized(): Boolean {
        return initialized
    }

    internal fun isPluginActive(pluginId: String?): Boolean {
        return !stopping && pluginsEngine && !pluginId.isNullOrEmpty() && plugins[pluginId]?.isEnabled() == true
    }

    internal fun isPluginActive(plugin: Plugin?): Boolean {
        return !stopping && pluginsEngine && plugin != null && plugins[plugin.getId()] === plugin && plugin.isEnabled()
    }

    private fun registerFileIconInternal(extension: String?, drawable: Drawable?): Int {
        val normalizedExtension = normalizeFileExtension(extension)
        if (normalizedExtension == null || drawable == null) {
            return PLUGIN_FILE_ICON_NONE
        }

        val existingId = fileIconIdsByExtension[normalizedExtension]
        if (existingId != null) {
            fileIconDrawablesById[existingId] = drawable
            return existingId
        }

        val newId = nextFileIconId.getAndIncrement()
        val activeId = fileIconIdsByExtension.putIfAbsent(normalizedExtension, newId) ?: newId
        fileIconDrawablesById[activeId] = drawable
        return activeId
    }

    private fun unregisterFileIconInternal(extension: String?) {
        val normalizedExtension = normalizeFileExtension(extension) ?: return
        val iconId = fileIconIdsByExtension.remove(normalizedExtension) ?: return
        fileIconDrawablesById.remove(iconId)
    }

    private fun getFileIconIdInternal(fileName: String?): Int {
        if (fileName.isNullOrEmpty() || fileIconIdsByExtension.isEmpty()) {
            return PLUGIN_FILE_ICON_NONE
        }
        val separatorIndex = fileName.lastIndexOf('.')
        val extension = if (separatorIndex >= 0) {
            fileName.substring(separatorIndex + 1)
        } else {
            fileName
        }
        val normalizedExtension = normalizeFileExtension(extension)
            ?: return PLUGIN_FILE_ICON_NONE
        return fileIconIdsByExtension[normalizedExtension] ?: PLUGIN_FILE_ICON_NONE
    }

    private fun clearFileIconsInternal() {
        fileIconIdsByExtension.clear()
        fileIconDrawablesById.clear()
    }

    private fun normalizeFileExtension(extension: String?): String? {
        if (extension.isNullOrEmpty()) {
            return null
        }
        return extension.lowercase(Locale.ROOT)
    }

    fun getPluginSettingsList(pluginId: String?): List<SettingItem>? {
        if (pluginId.isNullOrEmpty()) {
            return null
        }
        return settings[pluginId]
    }

    fun setPluginEnabled(
        pluginId: String?,
        enabled: Boolean,
        callback: Utilities.Callback<String>?
    ) {
        runOnPluginsQueue {
            if (enabled && (stopping || !initialized || !pluginsEngine)) {
                AndroidUtilities.runOnUIThread { callback?.run(LocaleController.getString(R.string.PluginsPySdkEngineDisabled)) }
                return@runOnPluginsQueue
            }
            val engine = getPluginEngine(pluginId)
            if (engine != null && pluginId != null) {
                engine.setPluginEnabled(pluginId, enabled, callback)
                interestedPluginsCache.clear()
            }
        }
    }

    fun deletePlugin(pluginId: String?, callback: Utilities.Callback<String>?) {
        runOnPluginsQueue {
            val engine = getPluginEngine(pluginId)
            if (engine != null && pluginId != null) {
                engine.deletePlugin(pluginId, callback)
            }
        }
    }

    fun cleanupPlugin(pluginId: String?) {
        try {
            removeHooksByPluginId(pluginId)
        } finally {
            try {
                invalidatePluginSettings(pluginId)
            } finally {
                removeMenuItemsByPluginId(pluginId)
            }
        }
    }

    fun getPluginPath(id: String?): String? {
        if (id.isNullOrEmpty()) {
            return null
        }
        return getPluginEngine(id)?.getPluginPath(id)
    }

    @JvmOverloads
    fun showInstallDialog(fragment: BaseFragment?, messageObject: MessageObject?, filePath: String? = null) {
        if (messageObject == null) {
            return
        }
        val params = InstallPluginBottomSheet.PluginInstallParams.of(messageObject, filePath)
        if (fragment != null && !params.trusted && messageObject.shouldHideLinks()) {
            BulletinFactory.of(fragment)
                .createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.PluginInstallFromStranger))
                .show()
            return
        }
        showInstallDialog(fragment, params)
    }

    fun openRepositoryLink(fragment: BaseFragment, username: String) {
        PluginRepositoryLinks.open(fragment, username)
    }

    fun openGitRepositoryLink(fragment: BaseFragment, url: String) {
        PluginRepositoryLinks.openGit(fragment, url)
    }

    @JvmOverloads
    fun showInstallDialog(fragment: BaseFragment?, filePath: String?, @Suppress("UNUSED_PARAMETER") trusted: Boolean = false) {
        if (filePath.isNullOrEmpty()) {
            return
        }
        showInstallDialog(fragment, InstallPluginBottomSheet.PluginInstallParams.untrusted(filePath))
    }

    private fun showInstallDialog(
        fragment: BaseFragment?,
        params: InstallPluginBottomSheet.PluginInstallParams?
    ) {
        if (fragment == null || !AndroidUtilities.isActivityRunning(fragment.parentActivity) || params?.filePath.isNullOrEmpty()) {
            return
        }

        val file = File(params.filePath)
        if (!pluginsEngine) {
            BulletinFactory.of(fragment)
                .createSimpleBulletin(
                    R.raw.error,
                    LocaleController.formatString(R.string.PluginNotEnabled, file.getName()),
                    LocaleController.getString(R.string.Enable),
                    Bulletin.DURATION_LONG,
                ) { fragment.presentFragment(PluginsActivity()) }
                .show()
            return
        }

        val engine = getPluginEngine(file) ?: return
        engine.showInstallDialog(fragment, params)
    }

    fun loadPluginSettings() {
        loadPluginSettings(null)
    }

    fun loadPluginSettings(pluginId: String?) {
        if (pluginId.isNullOrEmpty()) {
            for (id in plugins.keys) {
                val plugin = plugins[id]
                if (isPluginActive(plugin)) {
                    loadPluginSettings(id)
                } else if (plugin != null) {
                    invalidatePluginSettings(id)
                }
            }
            return
        }

        val task = Runnable {
            try {
                val engine = getPluginEngine(pluginId) ?: return@Runnable
                val pluginSettings = engine.loadPluginSettings(pluginId)
                if (pluginSettings == null) {
                    invalidatePluginSettings(pluginId)
                    return@Runnable
                }

                settings[pluginId] = pluginSettings
                FileLog.d("Registered settings for plugin $pluginId")
                notifyPluginSettingsRegistered(pluginId)
            } catch (e: Throwable) {
                FileLog.e(e)
                invalidatePluginSettings(pluginId)
            }
        }

        if (isOnPluginsQueueThread()) {
            task.run()
        } else {
            runOnPluginsQueue(task)
        }
    }

    fun hasPluginSettings(pluginId: String?): Boolean {
        return !pluginId.isNullOrEmpty() && settings.containsKey(pluginId)
    }

    fun invalidatePluginSettings(pluginId: String?) {
        if (!pluginId.isNullOrEmpty()) {
            val removed = settings.remove(pluginId)
            if (removed != null) {
                for (item in removed) {
                    runCatching { item.cleanup() }.onFailure { FileLog.e(it) }
                }
                notifyPluginSettingsUnregistered(pluginId)
            }
        }
    }

    @JvmOverloads
    fun clearPluginSettingsPreferences(pluginId: String?, clearEnabledState: Boolean = false) {
        if (pluginId.isNullOrEmpty()) {
            return
        }

        val engine = getPluginEngine(pluginId)
        if (engine != null) {
            engine.clearPluginSettings(pluginId)
        } else {
            for (e in getEngines().values) {
                e.clearPluginSettings(pluginId)
            }
        }

        ensurePreferences()
        if (clearEnabledState) {
            val enabledKey = PREF_PLUGIN_ENABLED_KEY_PREFIX + pluginId
            if (preferences.contains(enabledKey)) {
                preferences.edit { remove(enabledKey) }
            }
        }
    }

    fun getPluginSettingsPreferences(pluginId: String?): Map<String, *>? {
        return getPluginEngine(pluginId)?.getAllPluginSettings(pluginId!!)
    }

    fun hasPluginSettingsPreferences(pluginId: String?): Boolean {
        return !getPluginSettingsPreferences(pluginId).isNullOrEmpty()
    }

    fun getPluginSettingBoolean(pluginId: String?, key: String?, defaultValue: Boolean): Boolean {
        if (pluginId.isNullOrEmpty() || key.isNullOrEmpty()) {
            return defaultValue
        }
        val engine = getPluginEngine(pluginId)
        if (engine != null) {
            val value = engine.getPluginSetting(pluginId, key, defaultValue)
            if (value is Boolean) {
                return value
            }
        }
        return defaultValue
    }

    fun getPluginSettingString(pluginId: String?, key: String?, defaultValue: String?): String? {
        if (pluginId.isNullOrEmpty() || key.isNullOrEmpty()) {
            return defaultValue
        }
        val engine = getPluginEngine(pluginId)
        if (engine != null) {
            val value = engine.getPluginSetting(pluginId, key, defaultValue)
            if (value != null) {
                return value.toString()
            }
        }
        return defaultValue
    }

    fun getPluginSettingInt(pluginId: String?, key: String?, defaultValue: Int): Int {
        if (pluginId.isNullOrEmpty() || key.isNullOrEmpty()) {
            return defaultValue
        }
        val engine = getPluginEngine(pluginId)
        if (engine != null) {
            val value = engine.getPluginSetting(pluginId, key, defaultValue)
            if (value is Number) {
                return value.toInt()
            }
        }
        return defaultValue
    }

    fun setPluginSetting(pluginId: String?, key: String?, value: Any?) {
        setPluginSettingAndTriggerOnChange(pluginId, key, value, null)
    }

    fun setPluginSettingAndTriggerOnChange(
        pluginId: String?,
        key: String?,
        value: Any?,
        onChangeCallback: PyObject?
    ) {
        if (pluginId.isNullOrEmpty() || key.isNullOrEmpty()) {
            return
        }
        if (!isPluginActive(pluginId)) {
            return
        }
        val engine = getPluginEngine(pluginId)
        if (engine != null) {
            engine.setPluginSetting(pluginId, key, value)
            try {
                onChangeCallback?.call(value)
            } catch (e: Exception) {
                FileLog.e("Error executing on_change callback for $pluginId/$key", e)
            }
            loadPluginSettings(pluginId)
        }
    }

    private fun addHook(pluginId: String?, newHook: HookRecord?, logMessage: String) {
        if (pluginId.isNullOrEmpty() || newHook == null) {
            return
        }
        if (stopping || !pluginsEngine) {
            newHook.cleanup()
            return
        }
        val pluginHooks = hooks.computeIfAbsent(pluginId) { CopyOnWriteArraySet() }
        if (pluginHooks.add(newHook)) {
            FileLog.d(logMessage)
            interestedPluginsCache.clear()
            hooksCacheDirty = true
        }
    }

    fun addEventHook(pluginId: String?, hookName: String, matchSubstring: Boolean, priority: Int) {
        addHook(
            pluginId,
            EventHookRecord(pluginId, hookName, matchSubstring, priority),
            "Added event hook '$hookName' for plugin $pluginId"
        )
    }

    private fun removeHook(pluginId: String?, filter: (HookRecord) -> Boolean, logMessage: String) {
        if (pluginId.isNullOrEmpty()) {
            return
        }
        val pluginHooks = hooks[pluginId]
        if (!pluginHooks.isNullOrEmpty()) {
            val toKeep = ArrayList<HookRecord>()
            val toRemove = ArrayList<HookRecord>()
            for (record in pluginHooks) {
                if (filter(record)) {
                    toRemove.add(record)
                } else {
                    toKeep.add(record)
                }
            }

            if (toRemove.isNotEmpty()) {
                toRemove.forEach(HookRecord::cleanup)

                if (toKeep.isEmpty()) {
                    hooks.remove(pluginId)
                } else {
                    hooks[pluginId] = CopyOnWriteArraySet(toKeep)
                }

                FileLog.d(logMessage)
                interestedPluginsCache.clear()
                hooksCacheDirty = true
            }
        }
    }

    fun removeEventHook(pluginId: String?, hookName: String) {
        removeHook(pluginId, { record ->
            record is EventHookRecord && record.getHookName() == hookName
        }, "Removed event hook(s) matching name '$hookName' for plugin $pluginId")
    }

    fun addXposedHook(pluginId: String?, unhook: XC_MethodHook.Unhook?) {
        addHook(
            pluginId,
            if (unhook != null) XposedHookRecord(unhook) else null,
            "Added Xposed hook for plugin $pluginId"
        )
    }

    fun addXposedHooks(pluginId: String?, unhooks: ArrayList<XC_MethodHook.Unhook>?) {
        if (unhooks == null) {
            return
        }
        for (unhook in unhooks) {
            addXposedHook(pluginId, unhook)
        }
    }

    fun addPartialHook(pluginId: String?, handle: HookHandle?) {
        addHook(
            pluginId,
            if (handle != null) PartialHookRecord(handle) else null,
            "Added partial hook for plugin $pluginId"
        )
    }

    fun removePartialHook(pluginId: String?, handle: HookHandle?) {
        removeHook(pluginId, { record ->
            record is PartialHookRecord && record.matches(handle)
        }, "Removed partial hook for plugin $pluginId")
    }

    fun hasXposedHook(methodName: String, vararg declaringClasses: Class<*>): Boolean {
        rebuildHooksCacheIfNeeded()
        val members = hookedMembersCache[methodName] ?: return false
        return members.any { member -> declaringClasses.any { it == member.declaringClass } }
    }

    fun removeXposedHook(pluginId: String?, unhook: XC_MethodHook.Unhook?) {
        removeHook(pluginId, { record ->
            record is XposedHookRecord && record.matches(unhook)
        }, "Removed Xposed hook for plugin $pluginId")
    }

    fun removeHooksByPluginId(pluginId: String?) {
        if (!pluginId.isNullOrEmpty()) {
            val pluginHooks = hooks.remove(pluginId)
            if (pluginHooks != null) {
                for (record in pluginHooks) {
                    runCatching { record.cleanup() }.onFailure { FileLog.e(it) }
                }
                FileLog.d("Removed all (${pluginHooks.size}) hooks for plugin $pluginId")
                interestedPluginsCache.clear()
                hooksCacheDirty = true
            }
        }
    }

    fun addMenuItem(pluginId: String?, pyMenuItemData: PyObject?): String? {
        if (!isPluginEngineAvailable() || pyMenuItemData == null || pluginId.isNullOrEmpty()) {
            return null
        }
        return try {
            val record = MenuItemRecord(pluginId, pyMenuItemData)
            val menuType = record.menuType ?: return null

            val existing = menuItemsById[record.itemId]
            if (existing != null && existing.pluginId != pluginId) {
                FileLog.w("Plugin $pluginId tried to add a menu item: ${record.itemId}, which is already used by plugin ${existing.pluginId}")
                return null
            }
            if (existing != null) {
                menuItemsByMenuType[existing.menuType]?.remove(existing)
                existing.markRemoved()
            }

            menuItemsById[record.itemId] = record
            menuItemsByMenuType.compute(menuType) { _, currentCowList ->
                val mutableList = if (currentCowList == null) {
                    ArrayList<MenuItemRecord>()
                } else {
                    ArrayList(currentCowList).apply {
                        removeAll { it.itemId == record.itemId }
                    }
                }
                mutableList.add(record)
                mutableList.sortWith(Comparator.comparingInt<MenuItemRecord> { it.priority }
                    .reversed())
                CopyOnWriteArrayList(mutableList)
            }
            FileLog.d("Added menu item: ${record.itemId} for plugin $pluginId in type $menuType")
            notifyMenuItemsUpdated()
            record.itemId
        } catch (_: Exception) {
            null
        }
    }

    fun removeMenuItem(pluginId: String?, itemId: String?): Boolean {
        if (itemId.isNullOrEmpty()) {
            return false
        }

        val record = menuItemsById.remove(itemId)
        if (record != null && record.menuType != null) {
            if (record.pluginId != pluginId) {
                menuItemsById[itemId] = record
                return false
            }
            menuItemsByMenuType[record.menuType]?.remove(record)
            record.markRemoved()
            FileLog.d("Removed menu item: $itemId for plugin $pluginId")
            notifyMenuItemsUpdated()
            return true
        }
        return false
    }

    fun removeMenuItemsByPluginId(pluginId: String?) {
        if (pluginId.isNullOrEmpty()) {
            return
        }
        val itemsToRemove = ArrayList<String>()
        for (record in menuItemsById.values) {
            if (record.pluginId == pluginId) {
                itemsToRemove.add(record.itemId)
            }
        }
        if (itemsToRemove.isEmpty()) {
            return
        }
        for (itemId in itemsToRemove) {
            removeMenuItem(pluginId, itemId)
        }
        FileLog.d("Removed all menu items for plugin: $pluginId")
    }

    fun getMenuItemsForLocation(
        menuType: String?,
        builder: MenuContextBuilder?
    ): List<MenuItemRecord> {
        return if (builder == null) {
            getMenuItemsForLocation(menuType, HashMap())
        } else {
            getMenuItemsForLocation(menuType, builder.build())
        }
    }

    fun getMenuItemsForLocation(
        menuType: String?,
        contextData: Map<String, Any>?
    ): List<MenuItemRecord> {
        if (!isPluginEngineAvailable() || menuType.isNullOrEmpty()) {
            return Collections.emptyList()
        }

        val candidates = LinkedHashSet<MenuItemRecord>()
        val baseCandidates = menuItemsByMenuType[menuType]
        if (!baseCandidates.isNullOrEmpty()) {
            candidates.addAll(baseCandidates)
        }

        if (PluginsConstants.MenuItemTypes.MAIN_MENU == menuType) {
            val drawerCandidates = menuItemsByMenuType[PluginsConstants.MenuItemTypes.DRAWER_MENU]
            if (!drawerCandidates.isNullOrEmpty()) {
                candidates.addAll(drawerCandidates)
            }
        }

        if (candidates.isEmpty()) {
            return Collections.emptyList()
        }

        val result = ArrayList<MenuItemRecord>()
        for (record in candidates) {
            if (isPluginActive(record.pluginId)) {
                watchdog.onPluginExecutionStarted(record.pluginId)
                try {
                    if (record.checkCondition(contextData)) {
                        result.add(record)
                    }
                } finally {
                    watchdog.onPluginExecutionFinished(record.pluginId)
                }
            }
        }
        return result
    }

    fun notifyPluginsChanged() {
        AndroidUtilities.cancelRunOnUIThread(updateNotificationRunnable)
        AndroidUtilities.runOnUIThread(updateNotificationRunnable, 150)
    }

    fun executeOnAppEvent(eventType: String?) {
        if (eventType == PluginsConstants.APP_RESUME) {
            AnalyzerUpdater.check()
        }
        if (!initialized || !isPluginEngineAvailable() || eventType == null) {
            return
        }

        FileLog.d("Execute scripts on app event $eventType")
        for (engine in getEngines().values) {
            engine.executeOnAppEvent(eventType)
        }
    }

    private fun getInterestedPluginIds(eventName: String?): List<String> {
        if (eventName.isNullOrEmpty()) {
            return Collections.emptyList()
        }

        var potentialPluginIds = interestedPluginsCache[eventName]
        if (potentialPluginIds == null) {
            rebuildHooksCacheIfNeeded()
            val pluginMaxPriority = HashMap<String, Int>()

            exactMatchEventHooksCache[eventName]?.forEach { record ->
                val pluginId = record.getPluginId() ?: return@forEach
                pluginMaxPriority.compute(pluginId) { _, value ->
                    if (value == null) record.getPriority() else maxOf(value, record.getPriority())
                }
            }

            for (record in substringMatchEventHooksCache) {
                if (record.matches(eventName)) {
                    val pluginId = record.getPluginId() ?: continue
                    pluginMaxPriority.compute(pluginId) { _, value ->
                        if (value == null) record.getPriority() else maxOf(
                            value,
                            record.getPriority()
                        )
                    }
                }
            }

            potentialPluginIds = if (pluginMaxPriority.isEmpty()) {
                Collections.emptyList()
            } else {
                val sortedPlugins = ArrayList(pluginMaxPriority.entries)
                sortedPlugins.sortWith { e1, e2 ->
                    val priorityCompare = e2.value.compareTo(e1.value)
                    if (priorityCompare == 0) {
                        e1.key.compareTo(e2.key)
                    } else {
                        priorityCompare
                    }
                }

                ArrayList<String>().apply {
                    for (entry in sortedPlugins) {
                        if (isPluginActive(entry.key)) {
                            add(entry.key)
                        }
                    }
                }
            }

            interestedPluginsCache[eventName] = potentialPluginIds
            if (!potentialPluginIds.isNullOrEmpty()) {
                FileLog.d("Calculated and cached potential plugins for '$eventName': $potentialPluginIds")
            }
        }

        return potentialPluginIds ?: Collections.emptyList()
    }

    private fun rebuildHooksCacheIfNeeded() {
        if (!hooksCacheDirty) {
            return
        }
        synchronized(hooksCacheLock) {
            if (!hooksCacheDirty) {
                return
            }

            val exactMatchCache = HashMap<String, MutableList<EventHookRecord>>()
            val substringMatchCache = ArrayList<EventHookRecord>()
            val hookedMembers = HashMap<String, MutableList<Member>>()

            for (pluginHooks in hooks.values) {
                for (hook in pluginHooks) {
                    if (hook is XposedHookRecord) {
                        val member = hook.hookedMember ?: continue
                        hookedMembers.computeIfAbsent(member.name) { ArrayList() }.add(member)
                    } else if (hook is EventHookRecord) {
                        if (hook.isMatchSubstring()) {
                            substringMatchCache.add(hook)
                        } else {
                            val hookName = hook.getHookName() ?: continue
                            exactMatchCache.computeIfAbsent(hookName) { ArrayList() }.add(hook)
                        }
                    }
                }
            }

            exactMatchEventHooksCache = exactMatchCache
            substringMatchEventHooksCache = substringMatchCache
            hookedMembersCache = hookedMembers
            hooksCacheDirty = false
        }
    }

    private fun ensurePreferences() {
        preferences = ApplicationLoader.applicationContext.getSharedPreferences(
            "plugin_settings",
            Activity.MODE_PRIVATE
        )
    }

    private fun isOnPluginsQueueThread(): Boolean {
        return Utilities.pluginsQueue != null &&
                Utilities.pluginsQueue.handler != null &&
                Thread.currentThread() == Utilities.pluginsQueue.handler.looper.thread
    }

    private fun notifyPluginSettingsRegistered(pluginId: String) {
        AndroidUtilities.runOnUIThread {
            NotificationCenter.getGlobalInstance()
                .postNotificationName(NotificationCenter.pluginSettingsRegistered, pluginId)
        }
    }

    private fun notifyPluginSettingsUnregistered(pluginId: String) {
        AndroidUtilities.runOnUIThread {
            NotificationCenter.getGlobalInstance()
                .postNotificationName(NotificationCenter.pluginSettingsUnregistered, pluginId)
        }
    }

    private fun notifyMenuItemsUpdated() {
        AndroidUtilities.runOnUIThread {
            NotificationCenter.getGlobalInstance()
                .postNotificationName(NotificationCenter.pluginMenuItemsUpdated)
        }
    }

    private fun <T> executeGenericHook(
        hookName: String,
        initialObject: T?,
        caller: EngineHookCaller<T>
    ): T? {
        if (!isPluginEngineAvailable()) {
            return initialObject
        }

        val interestedPluginIds = getInterestedPluginIds(hookName)
        if (interestedPluginIds.isEmpty()) {
            return initialObject
        }

        var modifiedObject = initialObject
        for (pluginId in interestedPluginIds) {
            if (!isPluginActive(pluginId)) {
                continue
            }
            val engine = getPluginEngine(pluginId)
            if (engine != null) {
                watchdog.onPluginExecutionStarted(pluginId)
                try {
                    val hookResult = caller.call(engine, modifiedObject, pluginId)
                    modifiedObject = hookResult.result
                    if (hookResult.cancel) {
                        return null
                    } else if (hookResult.isFinal) {
                        return modifiedObject
                    }
                } finally {
                    watchdog.onPluginExecutionFinished(pluginId)
                }
            }
        }
        return modifiedObject
    }

    override fun executePreRequestHook(
        requestName: String,
        account: Int,
        request: TLObject
    ): TLObject? {
        return executeGenericHook(requestName, request) { engine, obj, pluginId ->
            engine.executePreRequestHook(requestName, account, obj, pluginId)
        }
    }

    override fun executePostRequestHook(
        requestName: String,
        account: Int,
        response: TLObject?,
        error: TLRPC.TL_error?,
    ): PluginsHooks.PostRequestResult {
        return executeGenericHook(
            requestName,
            PluginsHooks.PostRequestResult(response, error)
        ) { engine, obj, pluginId ->
            engine.executePostRequestHook(requestName, account, obj?.response, obj?.error, pluginId)
        } ?: PluginsHooks.PostRequestResult(response, error)
    }

    override fun executeUpdateHook(
        updateName: String,
        account: Int,
        update: TLRPC.Update
    ): TLRPC.Update? {
        return executeGenericHook(updateName, update) { engine, obj, pluginId ->
            engine.executeUpdateHook(updateName, account, obj, pluginId)
        }
    }

    override fun executeUpdatesHook(
        containerName: String,
        account: Int,
        updates: TLRPC.Updates
    ): TLRPC.Updates? {
        return executeGenericHook(containerName, updates) { engine, obj, pluginId ->
            engine.executeUpdatesHook(containerName, account, obj, pluginId)
        }
    }

    override fun executeSendMessageHook(
        account: Int,
        params: SendMessagesHelper.SendMessageParams,
    ): SendMessagesHelper.SendMessageParams? {
        return executeGenericHook(
            PluginsConstants.SEND_MESSAGE_HOOK,
            params
        ) { engine, obj, pluginId ->
            engine.executeSendMessageHook(account, obj, pluginId)
        }
    }

    private fun interface EngineHookCaller<T> {
        fun call(engine: PluginsEngine, obj: T?, pluginId: String): HookResult<T>
    }

    class HookResult<T>(
        var result: T?,
        var cancel: Boolean,
        var isFinal: Boolean,
    )

    class PluginValidationResult(
        var plugin: Plugin?,
        var error: String?,
    )

    interface PluginsEngine {
        fun isPlugin(file: File?, messageObject: MessageObject?): Boolean
        fun parsePluginMetadata(filePath: String?): Map<String, String>? = null
        fun isEngineAvailable(): Boolean
        fun init(callback: Runnable)
        fun checkDevServer()
        fun shutdown(callback: Runnable)
        fun setPluginEnabled(
            pluginId: String,
            enabled: Boolean,
            callback: Utilities.Callback<String>?
        )

        fun deletePlugin(pluginId: String, callback: Utilities.Callback<String>?)
        fun getPluginPath(id: String): String?
        fun canOpenInExternalApp(): Boolean
        fun openInExternalApp(id: String)
        fun sharePlugin(id: String)
        fun loadPluginSettings(id: String): List<SettingItem>?
        fun getPluginSetting(pluginId: String, key: String, defaultValue: Any?): Any?
        fun setPluginSetting(pluginId: String, key: String, value: Any?)
        fun clearPluginSettings(pluginId: String)
        fun getAllPluginSettings(pluginId: String): Map<String, *>?
        fun executeOnAppEvent(eventType: String)
        fun executePreRequestHook(
            requestName: String,
            account: Int,
            request: TLObject?,
            pluginId: String
        ): HookResult<TLObject>

        fun executePostRequestHook(
            requestName: String,
            account: Int,
            response: TLObject?,
            error: TLRPC.TL_error?,
            pluginId: String
        ): HookResult<PluginsHooks.PostRequestResult>

        fun executeUpdateHook(
            updateName: String,
            account: Int,
            update: TLRPC.Update?,
            pluginId: String
        ): HookResult<TLRPC.Update>

        fun executeUpdatesHook(
            containerName: String,
            account: Int,
            updates: TLRPC.Updates?,
            pluginId: String
        ): HookResult<TLRPC.Updates>

        fun executeSendMessageHook(
            account: Int,
            params: SendMessagesHelper.SendMessageParams?,
            pluginId: String
        ): HookResult<SendMessagesHelper.SendMessageParams>

        fun showInstallDialog(
            fragment: BaseFragment,
            params: InstallPluginBottomSheet.PluginInstallParams
        )

        fun loadPluginFromFile(
            filePath: String,
            pluginMetadata: Plugin?,
            callback: Utilities.Callback<String>?,
            delegate: PipController.InstallerDelegate?
        )

        fun openPluginSettings(id: String, fragment: BaseFragment)
        fun openPluginSettings(plugin: Plugin, fragment: BaseFragment)
        fun openPluginSetting(plugin: Plugin, linkAlias: String, fragment: BaseFragment)
        fun openPluginSetting(pluginId: String, linkAlias: String, fragment: BaseFragment)
    }

    private object SingletonHolder {
        val INSTANCE = PluginsController()
    }

    companion object {
        const val PREF_PLUGIN_ENABLED_KEY_PREFIX = "plugin_enabled_"
        const val PLUGIN_FILE_ICON_NONE = -1
        const val PLUGIN_FILE_ICON_ID_START = 101

        private val enginesMap = ConcurrentHashMap<String, PluginsEngine>(
            mapOf(
                PluginsConstants.PYTHON to PythonPluginsEngine(),
            ),
        )

        @JvmStatic
        fun getEngines(): ConcurrentHashMap<String, PluginsEngine> {
            return enginesMap
        }

        @JvmStatic
        fun getInstance(): PluginsController {
            return SingletonHolder.INSTANCE
        }

        @JvmStatic
        fun registerFileIcon(extension: String?, drawable: Drawable?): Int {
            return getInstance().registerFileIconInternal(extension, drawable)
        }

        @JvmStatic
        fun unregisterFileIcon(extension: String?) {
            getInstance().unregisterFileIconInternal(extension)
        }

        @JvmStatic
        fun clearFileIcons() {
            getInstance().clearFileIconsInternal()
        }

        @JvmStatic
        fun getFileIconId(fileName: String?): Int {
            return getInstance().getFileIconIdInternal(fileName)
        }

        @JvmStatic
        fun isPluginFileIcon(icon: Int): Boolean {
            if (icon < PLUGIN_FILE_ICON_ID_START) {
                return false
            }
            return getInstance().fileIconDrawablesById.containsKey(icon)
        }

        @JvmStatic
        fun getPluginFileIconDrawable(icon: Int): Drawable? {
            return getInstance().fileIconDrawablesById[icon]
        }

        @JvmStatic
        fun isPluginEngineSupported(): Boolean {
            return true
        }

        @JvmStatic
        fun isPluginEngineAvailable(): Boolean {
            if (!isPluginEngineSupported() || !pluginsEngine || pluginsSafeMode || getInstance().stopping) {
                return false
            }
            val builtInEngine = enginesMap[PluginsConstants.PYTHON]
            if (builtInEngine?.isEngineAvailable() == true) {
                return true
            }
            for (engine in getEngines().values) {
                if (engine === builtInEngine) {
                    continue
                }
                try {
                    if (engine.isEngineAvailable()) {
                        return true
                    }
                } catch (t: Throwable) {
                    FileLog.e("Error checking engine availability.", t)
                }
            }
            return false
        }

        @JvmStatic
        fun applyArtOpts() {
            if (exteraPreferences.getBoolean("pluginsEngine", false) && pluginsDisableArtOpts && isPluginEngineSupported()) {
                try {
                    XposedBridge.disableProfileSaver()
                } catch (e: Throwable) {
                    FileLog.e(e)
                }
            }
        }

        @JvmStatic
        fun isPlugin(messageObject: MessageObject?): Boolean {
            val path = ChatUtils.getInstance().getPathToMessage(messageObject)
            return messageObject != null &&
                    messageObject.documentName != null &&
                    !TextUtils.isEmpty(path) &&
                    isPlugin(File(path), messageObject) &&
                    isPluginEngineSupported()
        }

        @JvmStatic
        fun isPlugin(file: File?, messageObject: MessageObject?): Boolean {
            if (file == null) {
                return false
            }
            for (engine in getEngines().values) {
                if (engine.isPlugin(file, messageObject)) {
                    return true
                }
            }
            return false
        }

        @JvmStatic
        fun getPluginEngine(file: File?): PluginsEngine? {
            if (file == null) {
                return null
            }
            for (engine in getEngines().values) {
                if (engine.isPlugin(file, null)) {
                    return engine
                }
            }
            return null
        }

        @JvmStatic
        fun openPluginSettings(pluginId: String?) {
            openPluginSettings(pluginId, null)
        }

        @JvmStatic
        fun openPluginSettings(pluginId: String?, linkAlias: String?) {
            if (pluginId.isNullOrEmpty()) {
                return
            }

            val fragment = LaunchActivity.getLastFragment() ?: return
            if (!pluginsEngine) {
                BulletinFactory.of(fragment)
                    .createSimpleBulletin(
                        R.raw.error,
                        LocaleController.formatString(R.string.PluginEngineNotEnabled, pluginId),
                        LocaleController.getString(R.string.Enable),
                        Bulletin.DURATION_LONG,
                    ) { fragment.presentFragment(PluginsActivity()) }
                    .show()
                return
            }

            val plugin = getInstance().plugins[pluginId]
            if (plugin == null) {
                BulletinFactory.of(fragment)
                    .createEmojiBulletin(
                        SettingsRegistry.SHRUG_MAN,
                        LocaleController.formatString(R.string.PluginNotFound, pluginId)
                    )
                    .show()
                return
            }
            if (!getInstance().hasPluginSettings(pluginId)) {
                BulletinFactory.of(fragment)
                    .createEmojiBulletin(
                        SettingsRegistry.SHRUG_MAN,
                        LocaleController.formatString(
                            R.string.PluginHasNoSettings,
                            plugin.getName()
                        )
                    )
                    .show()
                return
            }

            val engine = getInstance().getPluginEngine(pluginId)
            if (engine != null) {
                if (linkAlias == null) {
                    engine.openPluginSettings(pluginId, fragment)
                } else {
                    engine.openPluginSetting(pluginId, linkAlias, fragment)
                }
            }
        }

        @JvmStatic
        fun isPluginPinned(pluginId: String?): Boolean {
            return !pluginId.isNullOrEmpty() && pinnedPlugins.contains(pluginId)
        }

        @JvmStatic
        fun setPluginPinned(pluginId: String?, isPinned: Boolean) {
            if (pluginId.isNullOrEmpty()) {
                return
            }
            val pinned = HashSet(pinnedPlugins)
            if (!isPinned) {
                pinned.remove(pluginId)
            } else {
                pinned.add(pluginId)
            }
            pinnedPlugins = pinned
            editor.putStringSet("pinnedPlugins", pinned).apply()
            getInstance().notifyPluginsChanged()
        }

        @JvmStatic
        fun runOnPluginsQueue(runnable: Runnable) {
            if (Utilities.pluginsQueue == null || !Utilities.pluginsQueue.isAlive) {
                synchronized(PluginsController::class.java) {
                    if (Utilities.pluginsQueue == null || !Utilities.pluginsQueue.isAlive) {
                        Utilities.pluginsQueue = DispatchQueue("pluginsQueue")
                    }
                }
            }
            Utilities.pluginsQueue.postRunnable(runnable)
        }
    }
}