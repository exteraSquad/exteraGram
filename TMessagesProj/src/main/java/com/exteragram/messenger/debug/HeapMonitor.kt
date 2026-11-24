/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.debug

import android.app.Activity
import android.os.Debug
import android.os.SystemClock
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildVars
import org.telegram.messenger.DispatchQueue
import org.telegram.messenger.FileLog
import org.telegram.messenger.R
import org.telegram.messenger.Utilities
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.ForegroundDetector
import org.telegram.ui.LaunchActivity
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread

object HeapMonitor {

    const val MIN_LIMIT_MB = 64
    const val LIMIT_STEP_MB = 16

    private const val CHECK_INTERVAL_MS = 5_000L
    private const val COLLECT_INTERVAL_MS = 30_000L
    private const val DUMP_START_DELAY_MS = 300L
    private const val BUFFER_SIZE = 64 * 1024

    @Volatile
    private var watcher: LimitWatcher? = null
    @Volatile
    private var dumping = false
    private var alertRef: WeakReference<AlertDialog>? = null

    val dumpsDir: File by lazy { debugFilesDir("heapdumps") }

    val promptStepBytes: Long
        get() = Runtime.getRuntime().maxMemory() / 8

    @JvmStatic
    fun init() {
        if (heapMonitorEnabled) {
            watcher = LimitWatcher()
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (heapMonitorEnabled == enabled) return
        heapMonitorEnabled = enabled
        watcher?.stop()
        watcher = if (enabled) LimitWatcher() else null
    }

    fun getLimitMb(): Int = heapMonitorLimitMb.coerceIn(MIN_LIMIT_MB, getMaxLimitMb())

    fun getMaxLimitMb(): Int {
        val maxMb = (Runtime.getRuntime().maxMemory() shr 20).toInt()
        return maxOf(MIN_LIMIT_MB, (maxMb / LIMIT_STEP_MB - 1) * LIMIT_STEP_MB)
    }

    fun setLimitMb(limitMb: Int) {
        heapMonitorLimitMb = limitMb
        watcher?.resetPrompt()
    }

    fun getUsedBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    fun getLastDump(): File? = dumpsDir.listFiles()
        ?.filter { it.name.endsWith(".zip") }
        ?.maxByOrNull { it.lastModified() }

    fun getDumpsSize(): Long = dumpsDir.listFiles()?.sumOf { it.length() } ?: 0L

    fun deleteDumps(onDone: () -> Unit) {
        if (dumping) return
        Utilities.globalQueue.postRunnable {
            dumpsDir.listFiles()?.forEach { it.delete() }
            AndroidUtilities.runOnUIThread { onDone() }
        }
    }

    fun shareLastDump(activity: Activity) {
        getLastDump()?.let { share(activity, it) }
    }

    fun dumpAndShare(activity: Activity, resourcesProvider: Theme.ResourcesProvider?) {
        if (dumping) return
        dumping = true
        val progressDialog = AlertDialog(activity, AlertDialog.ALERT_TYPE_LOADING, resourcesProvider)
        progressDialog.setMessage("Dumping heap…")
        progressDialog.setCanceledOnTouchOutside(false)
        progressDialog.setCancelable(false)
        progressDialog.show()
        thread(name = "heapDump") {
            SystemClock.sleep(DUMP_START_DELAY_MS)
            val result = runCatching {
                dumpHeap { progress ->
                    AndroidUtilities.runOnUIThread {
                        if (progress == 0) {
                            progressDialog.setMessage("Compressing…")
                        }
                        progressDialog.setProgress(progress)
                    }
                }
            }
            AndroidUtilities.runOnUIThread {
                dumping = false
                try {
                    progressDialog.dismiss()
                } catch (e: Exception) {
                    FileLog.e(e)
                }
                result.onSuccess { share(activity, it) }.onFailure {
                    FileLog.e(it)
                    BulletinFactory.global().createSimpleBulletin(R.raw.error, "Heap dump failed: $it").show()
                }
            }
        }
    }

    private fun showLimitAlert(used: Long, limit: Long) {
        val activity = LaunchActivity.instance
        if (watcher == null || dumping || activity == null || activity.isFinishing || alertRef?.get()?.isShowing == true) {
            return
        }
        val maxMemory = Runtime.getRuntime().maxMemory()
        val dialog = AlertDialog.Builder(activity)
            .setTitle("Heap limit exceeded")
            .setMessage(
                "Java heap is at ${AndroidUtilities.formatFileSize(used, true, false)} of " +
                    "${AndroidUtilities.formatFileSize(maxMemory, true, false)} after GC, " +
                    "over the ${AndroidUtilities.formatFileSize(limit, true, false)} limit.\n\n" +
                    "The app freezes while dumping. The dump holds everything in memory, so share it only with people you trust."
            )
            .setPositiveButton("Dump & share") { _, _ -> dumpAndShare(activity, null) }
            .setNeutralButton("Turn off") { _, _ -> setEnabled(false) }
            .setNegativeButton("Later", null)
            .create()
        alertRef = WeakReference(dialog)
        try {
            dialog.show()
        } catch (e: Exception) {
            FileLog.e(e)
        }
    }

    @Suppress("DEPRECATION")
    internal fun collectGarbage() {
        val runtime = Runtime.getRuntime()
        runtime.gc()
        runtime.runFinalization()
        runtime.gc()
    }

    private fun dumpHeap(onProgress: (Int) -> Unit): File {
        dumpsDir.mkdirs()
        val name = "heap_${BuildVars.BUILD_VERSION_STRING}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}"
        val hprof = File(dumpsDir, "$name.hprof")
        val part = File(dumpsDir, "$name.zip.part")
        val zip = File(dumpsDir, "$name.zip")
        try {
            collectGarbage()
            watcher?.postponePrompt(getUsedBytes())
            Debug.dumpHprofData(hprof.absolutePath)
            compress(hprof, part, onProgress)
            if (!part.renameTo(zip)) {
                throw IOException("Can't rename ${part.name}")
            }
        } finally {
            hprof.delete()
            part.delete()
        }
        dumpsDir.listFiles()?.forEach { if (it != zip) it.delete() }
        return zip
    }

    private fun compress(source: File, target: File, onProgress: (Int) -> Unit) {
        val total = maxOf(1L, source.length())
        var done = 0L
        var reported = 0
        onProgress(0)
        ZipOutputStream(BufferedOutputStream(FileOutputStream(target), BUFFER_SIZE)).use { zip ->
            zip.setLevel(Deflater.BEST_SPEED)
            zip.putNextEntry(ZipEntry(source.name))
            FileInputStream(source).use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    zip.write(buffer, 0, read)
                    done += read
                    val progress = (done * 100 / total).toInt()
                    if (progress != reported) {
                        reported = progress
                        onProgress(progress)
                    }
                }
            }
            zip.closeEntry()
        }
    }

    private fun share(activity: Activity, file: File) {
        shareDebugFile(activity, file, "application/zip", "Share heap dump")
    }

    private class LimitWatcher : ForegroundDetector.Listener {

        private val queue = DispatchQueue("heapMonitorQueue")
        private val checkRunnable = Runnable { check() }

        @Volatile
        private var running = false
        private var nextPromptBytes = 0L
        private var lastCollectTime = 0L

        init {
            ForegroundDetector.getInstance()?.addListener(this)
            updateRunning()
        }

        override fun onBecameForeground() = updateRunning()

        override fun onBecameBackground() = updateRunning()

        fun stop() {
            ForegroundDetector.getInstance()?.removeListener(this)
            running = false
            queue.cleanupQueue()
            queue.recycle()
        }

        fun resetPrompt() {
            queue.postRunnable {
                nextPromptBytes = 0
                lastCollectTime = 0
            }
        }

        fun postponePrompt(usedBytes: Long) {
            queue.postRunnable {
                nextPromptBytes = maxOf(nextPromptBytes, usedBytes + promptStepBytes)
            }
        }

        private fun updateRunning() {
            val shouldRun = ForegroundDetector.getInstance()?.isForeground == true
            if (running == shouldRun) return
            running = shouldRun
            queue.postRunnable {
                queue.cancelRunnable(checkRunnable)
                if (running) {
                    queue.postRunnable(checkRunnable, CHECK_INTERVAL_MS)
                }
            }
        }

        private fun check() {
            if (!running) return
            val limit = getLimitMb().toLong() shl 20
            val used = getUsedBytes()
            if (used < limit) {
                nextPromptBytes = 0
            } else if (used >= nextPromptBytes && !dumping && !ApplicationLoader.mainInterfacePaused &&
                SystemClock.elapsedRealtime() - lastCollectTime >= COLLECT_INTERVAL_MS
            ) {
                lastCollectTime = SystemClock.elapsedRealtime()
                collectGarbage()
                val collected = getUsedBytes()
                if (collected >= maxOf(limit, nextPromptBytes)) {
                    nextPromptBytes = collected + promptStepBytes
                    AndroidUtilities.runOnUIThread { showLimitAlert(collected, limit) }
                }
            }
            if (running) {
                queue.postRunnable(checkRunnable, CHECK_INTERVAL_MS)
            }
        }
    }
}
