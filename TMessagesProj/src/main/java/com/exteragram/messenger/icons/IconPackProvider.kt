/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.icons

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.R
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.security.MessageDigest

class IconPackProvider : ContentProvider() {

    companion object {
        private const val PATH_ICON = "icon"

        @JvmStatic
        fun getIconUri(packId: String, resourceName: String): Uri? {
            val source = IconPackStorage.resolveIconFile(packId, resourceName) ?: return null
            if (source.extension.lowercase() !in setOf("png", "webp", "jpg", "jpeg", "svg")) {
                return null
            }
            return Uri.Builder()
                .scheme("content")
                .authority("${ApplicationLoader.getApplicationId()}.icon_pack_provider")
                .appendPath(PATH_ICON)
                .appendPath(packId)
                .appendPath(resourceName)
                .appendQueryParameter("v", "${source.lastModified()}_${source.length()}")
                .build()
        }
    }

    override fun onCreate(): Boolean {
        context?.applicationContext?.let { applicationContext ->
            if (ApplicationLoader.applicationContext == null) {
                ApplicationLoader.applicationContext = applicationContext
            }
        }
        return true
    }

    override fun getType(uri: Uri): String? {
        return if (isIconUri(uri)) "image/png" else null
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") {
            throw SecurityException("Icon pack files are read-only")
        }
        if (!isIconUri(uri)) {
            throw FileNotFoundException(uri.toString())
        }
        val file = runCatching {
            resolveSource(uri)?.let { getRasterizedIcon(it) }
        }.getOrNull()
            ?: getFallbackIcon()
            ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun isIconUri(uri: Uri): Boolean {
        val path = uri.pathSegments
        return path.size == 3 && path[0] == PATH_ICON
    }

    private fun resolveSource(uri: Uri): File? {
        val path = uri.pathSegments
        if (!isIconUri(uri)) {
            return null
        }
        val source = IconPackStorage.resolveIconFile(path[1], path[2]) ?: return null
        return when (source.extension.lowercase()) {
            "png", "webp", "jpg", "jpeg", "svg" -> source
            else -> null
        }
    }

    private fun getFallbackIcon(): File? {
        val density = context?.resources?.displayMetrics?.densityDpi ?: return null
        return materializeCacheFile("default_$density.png") { context, output ->
            val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.notification)
                ?: return@materializeCacheFile false
            try {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun getRasterizedIcon(source: File): File? {
        val density = context?.resources?.displayMetrics?.densityDpi ?: return null
        val cacheKey = "${source.canonicalPath}:${source.lastModified()}:${source.length()}:$density"
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(cacheKey.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return materializeCacheFile("$digest.png") { context, output ->
            val bitmap = IconManager.createBitmapFromFile(
                source.absolutePath,
                R.drawable.notification,
                context.resources.displayMetrics.densityDpi,
                null
            ) ?: return@materializeCacheFile false
            try {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun materializeCacheFile(name: String, write: (Context, FileOutputStream) -> Boolean): File? {
        val context = context ?: return null
        val cacheDirectory = File(context.cacheDir, "notification_icons")
        if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
            return null
        }
        val target = File(cacheDirectory, name)
        if (target.isFile && target.length() > 0) {
            return target
        }
        synchronized(IconPackProvider::class.java) {
            if (target.isFile && target.length() > 0) {
                return target
            }
            val temporary = File(cacheDirectory, "$name.tmp")
            return try {
                val written = FileOutputStream(temporary).use { write(context, it) }
                if (!written) {
                    return null
                }
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
                target
            } catch (_: Exception) {
                null
            } finally {
                if (temporary.exists()) {
                    temporary.delete()
                }
            }
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}