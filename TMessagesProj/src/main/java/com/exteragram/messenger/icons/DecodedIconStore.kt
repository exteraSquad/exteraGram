/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.icons

import android.graphics.Bitmap
import android.system.Os
import androidx.core.graphics.createBitmap
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.Utilities
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal object DecodedIconStore {

    private const val HEADER_BYTES = 16
    private const val MAGIC = 0x45544931
    private const val MAX_BYTES = 32L * 1024L * 1024L
    private const val PRUNE_TRIGGER_BYTES = 4L * 1024L * 1024L

    private val writtenSincePrune = AtomicLong(0)
    private val pruning = AtomicBoolean(false)

    @Volatile
    private var directory: File? = null

    private fun directory(): File? {
        directory?.let { return it }
        return try {
            val dir = File(ApplicationLoader.applicationContext.cacheDir, "icon_bitmaps")
            if (!dir.isDirectory && !dir.mkdirs()) return null
            directory = dir
            dir
        } catch (e: Exception) {
            FileLog.e("Failed to open the decoded icon cache", e)
            null
        }
    }

    private fun entryName(source: File, width: Int, height: Int, density: Int): String {
        var modified: Long
        var length: Long
        try {
            val stat = Os.stat(source.path)
            modified = stat.st_mtime
            length = stat.st_size
        } catch (_: Throwable) {
            modified = source.lastModified()
            length = source.length()
        }
        var hash = 1125899906842597L
        val path = source.path
        for (i in path.indices) hash = 31 * hash + path[i].code
        hash = 31 * hash + modified
        hash = 31 * hash + length
        hash = 31 * hash + width
        hash = 31 * hash + height
        hash = 31 * hash + density
        return java.lang.Long.toHexString(hash)
    }

    fun get(source: File, width: Int, height: Int, density: Int): Bitmap? {
        val dir = directory() ?: return null
        val file = File(dir, entryName(source, width, height, density))
        val expected = HEADER_BYTES.toLong() + width.toLong() * height * 4
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val channel: FileChannel = raf.channel
                if (channel.size() != expected) return null
                val buffer = ByteBuffer.allocateDirect(expected.toInt()).order(ByteOrder.nativeOrder())
                while (buffer.hasRemaining()) {
                    if (channel.read(buffer) < 0) return null
                }
                buffer.flip()
                if (buffer.int != MAGIC) return null
                if (buffer.int != width || buffer.int != height) return null
                buffer.int
                val bitmap = createBitmap(width, height)
                bitmap.copyPixelsFromBuffer(buffer)
                bitmap.density = density
                bitmap
            }
        } catch (_: FileNotFoundException) {
            null
        } catch (e: Throwable) {
            runCatching { file.delete() }
            null
        }
    }

    fun put(source: File, width: Int, height: Int, density: Int, bitmap: Bitmap) {
        if (bitmap.width != width || bitmap.height != height) return
        if (bitmap.config != Bitmap.Config.ARGB_8888) return
        val dir = directory() ?: return
        val file = File(dir, entryName(source, width, height, density))
        if (file.isFile) return
        val temporary = File(dir, file.name + ".tmp")
        val total = HEADER_BYTES + width * height * 4
        try {
            val buffer = ByteBuffer.allocateDirect(total).order(ByteOrder.nativeOrder())
            buffer.putInt(MAGIC)
            buffer.putInt(width)
            buffer.putInt(height)
            buffer.putInt(density)
            bitmap.copyPixelsToBuffer(buffer)
            buffer.flip()
            RandomAccessFile(temporary, "rw").use { raf ->
                val channel = raf.channel
                while (buffer.hasRemaining()) channel.write(buffer)
            }
            if (!temporary.renameTo(file)) {
                temporary.delete()
                return
            }
            if (writtenSincePrune.addAndGet(total.toLong()) >= PRUNE_TRIGGER_BYTES) {
                writtenSincePrune.set(0)
                Utilities.globalQueue.postRunnable { prune(dir) }
            }
        } catch (e: Throwable) {
            runCatching { if (temporary.exists()) temporary.delete() }
        }
    }

    private fun prune(dir: File) {
        if (!pruning.compareAndSet(false, true)) return
        try {
            val files = dir.listFiles() ?: return
            var total = 0L
            for (file in files) total += file.length()
            if (total <= MAX_BYTES) return
            files.sortBy { it.lastModified() }
            val target = MAX_BYTES * 3 / 4
            for (file in files) {
                if (total <= target) break
                val size = file.length()
                if (file.delete()) total -= size
            }
        } catch (e: Exception) {
            FileLog.e("Failed to prune the decoded icon cache", e)
        } finally {
            pruning.set(false)
        }
    }
}
