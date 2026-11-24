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
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.SparseArray
import androidx.core.util.size
import com.exteragram.messenger.plugins.PluginsController
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.BuildVars
import org.telegram.messenger.DispatchQueue
import org.telegram.messenger.FileLog
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.ForegroundDetector
import org.telegram.ui.LaunchActivity
import java.io.File
import java.io.RandomAccessFile
import java.lang.ref.WeakReference
import java.lang.reflect.Modifier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object FreezeMonitor {

    const val MIN_THRESHOLD_MS = 200
    const val MAX_THRESHOLD_MS = 2000
    const val THRESHOLD_STEP_MS = 100

    private const val PING_INTERVAL_MS = 200L
    private const val SAMPLE_START_MS = 100L
    private const val SAMPLE_INTERVAL_MS = 20L
    private const val MAX_SAMPLING_MS = 120_000L
    private const val SUSPEND_GAP_MS = 2_000L
    private const val MAX_FRAMES = 150
    private const val STACK_KEY_FRAMES = 40
    private const val TOP_STACKS = 5
    private const val TOP_FRAMES = 10
    private const val HOT_FRAMES = 60
    private const val HOT_FRAME_MIN_PERCENT = 5
    private const val THREAD_DUMP_FRAMES = 20
    private const val THREAD_DUMP_MAX = 40
    private const val TOP_OBSERVER_IDS = 8
    private const val TOP_OBSERVER_CLASSES = 12
    private const val REPORT_MAX_BYTES = 2_000_000L
    private const val REPORT_TRIM_BYTES = 1_000_000
    private const val REPORT_NAME = "freeze_report.txt"

    private val APP_FRAME_PREFIXES = arrayOf("org.telegram.", "com.exteragram.", "com.chaquo.")
    private val IDLE_TOP_FRAMES = arrayOf(
        "android.os.MessageQueue.nativePollOnce", "java.lang.Object.wait", "java.lang.Thread.sleep",
        "sun.misc.Unsafe.park", "jdk.internal.misc.Unsafe.park", "java.net.", "libcore.io.Linux.",
        "android.system.Os.", "dalvik.system.VMStack.getThreadStackTrace"
    )
    private val COUNTED_CLASSES = arrayOf(
        "org.telegram.ui.LaunchActivity",
        "org.telegram.ui.ActionBar.BaseFragment",
        "org.telegram.ui.ChatActivity",
        "org.telegram.ui.DialogsActivity",
        "org.telegram.ui.ProfileActivity",
        "org.telegram.ui.Components.ChatActivityEnterView",
        "org.telegram.ui.Components.EmojiView",
        "org.telegram.ui.Cells.ChatMessageCell",
        "org.telegram.messenger.ImageReceiver",
        "org.telegram.ui.Components.RLottieDrawable",
        "org.telegram.ui.Components.AnimatedFileDrawable",
        "org.telegram.ui.Components.AnimatedEmojiDrawable",
        "android.graphics.Bitmap"
    )
    private val GC_STATS = arrayOf("art.gc.blocking-gc-count", "art.gc.blocking-gc-time", "art.gc.gc-count", "art.gc.gc-time")

    @Volatile
    private var watchdog: Watchdog? = null
    @Volatile
    private var freezes = 0
    private val reportLock = Any()
    private val notificationNames: Map<Int, String> by lazy {
        NotificationCenter::class.java.fields
            .filter { Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType }
            .associate { it.getInt(null) to it.name }
    }

    val reportsDir: File by lazy { debugFilesDir("freezereports") }

    private val reportFile: File
        get() = File(reportsDir, REPORT_NAME)

    @JvmStatic
    fun init() {
        if (freezeMonitorEnabled) {
            watchdog = Watchdog()
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (freezeMonitorEnabled == enabled) return
        freezeMonitorEnabled = enabled
        watchdog?.stop()
        watchdog = if (enabled) Watchdog() else null
    }

    fun getThresholdMs(): Int = freezeMonitorThresholdMs.coerceIn(MIN_THRESHOLD_MS, MAX_THRESHOLD_MS)

    fun setThresholdMs(thresholdMs: Int) {
        freezeMonitorThresholdMs = thresholdMs
    }

    fun getReport(): File? = reportFile.takeIf { it.length() > 0 }

    fun getReportsSize(): Long = reportsDir.listFiles()?.sumOf { it.length() } ?: 0L

    fun deleteReports(onDone: () -> Unit) {
        Utilities.globalQueue.postRunnable {
            synchronized(reportLock) {
                reportsDir.listFiles()?.forEach { it.delete() }
                freezes = 0
            }
            AndroidUtilities.runOnUIThread { onDone() }
        }
    }

    fun shareReport(activity: Activity) {
        Utilities.globalQueue.postRunnable {
            val result = runCatching { copyReport() }
            AndroidUtilities.runOnUIThread {
                result.onSuccess { shareDebugFile(activity, it, "text/plain", "Share freeze report") }.onFailure {
                    FileLog.e(it)
                    BulletinFactory.global().createSimpleBulletin(R.raw.error, "Freeze report failed: $it").show()
                }
            }
        }
    }

    private fun copyReport(): File {
        synchronized(reportLock) {
            val copy = File(reportsDir, "freeze_${BuildVars.BUILD_VERSION_STRING}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.txt")
            reportsDir.listFiles()?.forEach { if (it.name != REPORT_NAME) it.delete() }
            reportFile.copyTo(copy, true)
            return copy
        }
    }

    private fun appendReport(text: String) {
        synchronized(reportLock) {
            try {
                reportsDir.mkdirs()
                val file = reportFile
                file.appendText(text)
                if (file.length() > REPORT_MAX_BYTES) {
                    val tail = ByteArray(REPORT_TRIM_BYTES)
                    RandomAccessFile(file, "r").use {
                        it.seek(it.length() - REPORT_TRIM_BYTES)
                        it.readFully(tail)
                    }
                    file.writeBytes("##### (older records trimmed)\n".toByteArray() + tail)
                }
            } catch (e: Exception) {
                FileLog.e(e)
            }
        }
    }

    private fun showFreezeBulletin(durationMs: Long) {
        val activity = LaunchActivity.instance
        if (watchdog == null || activity == null || activity.isFinishing) return
        BulletinFactory.global().createSimpleBulletin(R.raw.error, "Main thread froze for ${formatSeconds(durationMs)}", "Share") {
            shareReport(activity)
        }.show()
    }

    private fun formatSeconds(ms: Long): String = String.format(Locale.US, "%.1f s", ms / 1000.0)

    private fun formatMb(bytes: Long): String = String.format(Locale.US, "%.1f MB", bytes / 1048576.0)

    private fun percent(count: Int, total: Int): String = (count * 100 / total).toString().padStart(4)

    private fun appLine(): String =
        "app ${BuildVars.BUILD_VERSION_STRING} (${BuildVars.BUILD_VERSION}), ${Build.MANUFACTURER} ${Build.MODEL}, " +
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    private fun heapLine(): String {
        val used = HeapMonitor.getUsedBytes()
        val max = Runtime.getRuntime().maxMemory()
        return "java ${formatMb(used)} / ${formatMb(max)} (${used * 100 / max}%), native ${formatMb(Debug.getNativeHeapAllocatedSize())}"
    }

    private fun gcStats(): LongArray = LongArray(GC_STATS.size) { Debug.getRuntimeStat(GC_STATS[it])?.toLongOrNull() ?: 0L }

    private fun frameNames(trace: Array<StackTraceElement>, limit: Int): List<String> =
        trace.take(limit).map { it.toString() }

    private fun screenName(): String = runCatching {
        LaunchActivity.getLastFragmentIncludeMainTabs()?.javaClass?.name ?: "none"
    }.getOrDefault("unknown")

    private fun fragmentStack(): String = runCatching {
        val stack = ArrayList(LaunchActivity.instance?.getActionBarLayout()?.getFragmentStack() ?: emptyList())
        "(${stack.size}) " + stack.joinToString(" > ") { it.javaClass.simpleName }
    }.getOrDefault("unavailable")

    private fun otherThreads(mainThread: Thread): String {
        val out = StringBuilder()
        var count = 0
        try {
            for ((thread, trace) in Thread.getAllStackTraces()) {
                if (thread == mainThread || thread == Thread.currentThread()) continue
                val state = thread.state
                if (state != Thread.State.RUNNABLE && state != Thread.State.BLOCKED) continue
                val frames = frameNames(trace, THREAD_DUMP_FRAMES)
                val top = frames.firstOrNull() ?: continue
                if (IDLE_TOP_FRAMES.any { top.startsWith(it) }) continue
                out.append('"').append(thread.name).append("\" ").append(state).append('\n')
                frames.forEach { out.append("    at ").append(it).append('\n') }
                if (++count >= THREAD_DUMP_MAX) break
            }
        } catch (e: Exception) {
            out.append("thread dump failed: ").append(e).append('\n')
        }
        return if (out.length > 0) out.toString() else "(none)\n"
    }

    private fun delegateName(delegate: Any?): String {
        if (delegate == null) return "null"
        val type = delegate.javaClass
        if (type.name.endsWith("WeakObserversGroupImpl")) {
            val target = runCatching {
                val field = type.getDeclaredField("reference")
                field.isAccessible = true
                (field.get(delegate) as WeakReference<*>).get()
            }.getOrElse { return type.name }
            return "${target?.javaClass?.name ?: "cleared"} (weak)"
        }
        return type.name
    }

    private fun observers(): String {
        val out = StringBuilder()
        val centers = arrayOf(
            "account" to NotificationCenter.getInstance(UserConfig.selectedAccount),
            "global" to NotificationCenter.getGlobalInstance()
        )
        for ((label, center) in centers) {
            try {
                val field = NotificationCenter::class.java.getDeclaredField("observers")
                field.isAccessible = true
                val observers = field.get(center) as SparseArray<*>
                var total = 0
                val perId = ArrayList<Pair<Int, String>>()
                val byClass = HashMap<String, Int>()
                for (i in 0 until observers.size) {
                    val delegates = observers.valueAt(i) as List<*>
                    total += delegates.size
                    perId.add(delegates.size to (notificationNames[observers.keyAt(i)] ?: observers.keyAt(i).toString()))
                    for (j in delegates.indices) {
                        val name = delegateName(delegates[j]).substringAfterLast('.')
                        byClass[name] = (byClass[name] ?: 0) + 1
                    }
                }
                out.append("$label: $total registrations in ${perId.size} ids\n")
                out.append("  biggest ids: ")
                    .append(perId.sortedByDescending { it.first }.take(TOP_OBSERVER_IDS).joinToString(", ") { "${it.second} ${it.first}" })
                    .append('\n')
                out.append("  by class: ")
                    .append(byClass.entries.sortedByDescending { it.value }.take(TOP_OBSERVER_CLASSES).joinToString(", ") { "${it.key} ${it.value}" })
                    .append('\n')
            } catch (e: Exception) {
                out.append("$label: failed $e\n")
            }
        }
        return out.toString()
    }

    private fun countInstances(classes: Array<Class<*>>): LongArray {
        val vmDebug = Class.forName("dalvik.system.VMDebug")
        return try {
            vmDebug.getDeclaredMethod("countInstancesOfClasses", classes.javaClass, Boolean::class.javaPrimitiveType)
                .invoke(null, classes, true) as LongArray
        } catch (e: ReflectiveOperationException) {
            val method = vmDebug.getDeclaredMethod("countInstancesOfClass", Class::class.java, Boolean::class.javaPrimitiveType)
            LongArray(classes.size) { method.invoke(null, classes[it], true) as Long }
        }
    }

    private fun liveObjects(): String = try {
        val classes = Array<Class<*>>(COUNTED_CLASSES.size) { Class.forName(COUNTED_CLASSES[it]) }
        HeapMonitor.collectGarbage()
        val counts = countInstances(classes)
        classes.indices.joinToString("\n", postfix = "\n") { "${counts[it].toString().padStart(7)}  ${classes[it].name}" }
    } catch (e: Throwable) {
        "unavailable: $e\n"
    }

    private fun environment(): String {
        val plugins = runCatching {
            PluginsController.getInstance().plugins.values
                .filter { it.isEnabled() }
                .map { "${it.getId()} ${it.getVersion()}" }
                .sorted()
                .joinToString(", ")
                .ifEmpty { "none" }
        }.getOrElse { "unavailable: $it" }
        val premium = runCatching { UserConfig.getInstance(UserConfig.selectedAccount).isPremium }.getOrNull()
        return "-- environment --\n" +
            "${appLine()}\n" +
            "process uptime ${(SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()) / 1000} s, " +
            "foreground ${ForegroundDetector.getInstance()?.isForeground}\n" +
            "heap now: ${heapLine()}\n" +
            "accounts active ${UserConfig.getActivatedAccountsCount()}, premium $premium\n" +
            "enabled plugins: $plugins\n"
    }

    private class Freeze(
        val durationMs: Long,
        val thresholdMs: Int,
        val samples: Int,
        val maxGapMs: Long,
        val screen: String,
        val fragmentStack: String,
        val gcBefore: LongArray,
        val heapBefore: String,
        val states: Map<String, Int>,
        val hot: Map<String, Int>,
        val hotDepth: Map<String, Int>,
        val topFrames: Map<String, Int>,
        val stacks: Map<List<String>, Int>,
        val otherThreads: String?,
        val observers: String?
    )

    private class Watchdog : ForegroundDetector.Listener {

        private val queue = DispatchQueue("freezeMonitorQueue")
        private val mainHandler = Handler(Looper.getMainLooper())
        private val mainThread = Looper.getMainLooper().thread
        private val checkRunnable = Runnable { check() }

        @Volatile
        private var running = false
        @Volatile
        private var stopped = false

        init {
            ForegroundDetector.getInstance()?.addListener(this)
            updateRunning()
        }

        override fun onBecameForeground() = updateRunning()

        override fun onBecameBackground() = updateRunning()

        fun stop() {
            ForegroundDetector.getInstance()?.removeListener(this)
            stopped = true
            running = false
            queue.cleanupQueue()
            queue.recycle()
        }

        private fun updateRunning() {
            val shouldRun = !stopped && ForegroundDetector.getInstance()?.isForeground == true
            if (running == shouldRun) return
            running = shouldRun
            queue.postRunnable {
                queue.cancelRunnable(checkRunnable)
                if (running) {
                    queue.postRunnable(checkRunnable)
                }
            }
        }

        private fun check() {
            if (!running) return
            try {
                val ping = CountDownLatch(1)
                val posted = SystemClock.uptimeMillis()
                if (mainHandler.post { ping.countDown() } && !ping.await(SAMPLE_START_MS, TimeUnit.MILLISECONDS)) {
                    sample(ping, posted)?.let { record(it) }
                }
            } catch (e: Exception) {
                FileLog.e(e)
            }
            if (running) {
                queue.postRunnable(checkRunnable, PING_INTERVAL_MS)
            }
        }

        private fun sample(ping: CountDownLatch, posted: Long): Freeze? {
            val threshold = getThresholdMs()
            val gcBefore = gcStats()
            val heapBefore = heapLine()
            val screen = screenName()
            val fragmentStack = fragmentStack()
            val states = HashMap<String, Int>()
            val hot = HashMap<String, Int>()
            val hotDepth = HashMap<String, Int>()
            val topFrames = HashMap<String, Int>()
            val stacks = HashMap<List<String>, Int>()
            var threadDump: String? = null
            var observersDump: String? = null
            var samples = 0
            var maxGap = 0L
            var lastTick = posted
            while (true) {
                if (stopped) return null
                val now = SystemClock.uptimeMillis()
                maxGap = maxOf(maxGap, now - lastTick)
                lastTick = now
                if (now - posted < MAX_SAMPLING_MS) {
                    val state = mainThread.state.name
                    val frames = frameNames(mainThread.stackTrace, MAX_FRAMES)
                    samples++
                    states[state] = (states[state] ?: 0) + 1
                    if (frames.isNotEmpty()) {
                        topFrames[frames[0]] = (topFrames[frames[0]] ?: 0) + 1
                        val key = frames.take(STACK_KEY_FRAMES)
                        stacks[key] = (stacks[key] ?: 0) + 1
                        frames.forEachIndexed { index, frame ->
                            if (index > (hotDepth[frame] ?: -1)) {
                                hotDepth[frame] = index
                            }
                        }
                        for (frame in frames.toHashSet()) {
                            hot[frame] = (hot[frame] ?: 0) + 1
                        }
                    }
                    if (threadDump == null && now - posted + SAMPLE_INTERVAL_MS >= threshold) {
                        threadDump = otherThreads(mainThread)
                        observersDump = observers()
                    }
                }
                if (ping.await(SAMPLE_INTERVAL_MS, TimeUnit.MILLISECONDS)) break
            }
            val duration = SystemClock.uptimeMillis() - posted
            if (duration < threshold) return null
            return Freeze(
                duration, threshold, samples, maxGap, screen, fragmentStack, gcBefore, heapBefore,
                states, hot, hotDepth, topFrames, stacks, threadDump, observersDump
            )
        }

        private fun record(freeze: Freeze) {
            val gcAfter = gcStats()
            val number = ++freezes
            val samples = freeze.samples
            val out = StringBuilder()
            out.append("\n=== Freeze #$number at ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}: ")
                .append("main thread blocked ${freeze.durationMs} ms\n")
            out.append("threshold ${freeze.thresholdMs} ms, $samples samples every $SAMPLE_INTERVAL_MS ms from $SAMPLE_START_MS ms\n")
            if (freeze.maxGapMs > SUSPEND_GAP_MS) {
                out.append("WARNING: the monitor thread itself stalled for ${freeze.maxGapMs} ms, the system probably froze the whole process; this record may be bogus\n")
            }
            out.append("screen at start: ${freeze.screen}\n")
            out.append("fragment stack ${freeze.fragmentStack}\n")
            out.append("GC during freeze: blocking ${gcAfter[0] - freeze.gcBefore[0]} (${gcAfter[1] - freeze.gcBefore[1]} ms), ")
                .append("all ${gcAfter[2] - freeze.gcBefore[2]} (${gcAfter[3] - freeze.gcBefore[3]} ms)\n")
            out.append("heap before: ${freeze.heapBefore}\n")
            out.append("heap after:  ${heapLine()}\n")
            out.append("main thread states: ")
                .append(freeze.states.entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} ${it.value}" })
                .append('\n')

            if (samples > 0) {
                out.append("\n-- hot app frames (share of samples, outermost first) --\n")
                freeze.hot.entries
                    .filter { it.value * 100 >= samples * HOT_FRAME_MIN_PERCENT && APP_FRAME_PREFIXES.any { prefix -> it.key.startsWith(prefix) } }
                    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenByDescending { freeze.hotDepth[it.key] ?: 0 })
                    .take(HOT_FRAMES)
                    .forEach { out.append("${percent(it.value, samples)}%  ${it.key}\n") }
                out.append("\n-- top of stack --\n")
                freeze.topFrames.entries.sortedByDescending { it.value }.take(TOP_FRAMES)
                    .forEach { out.append("${percent(it.value, samples)}%  ${it.key}\n") }
                out.append("\n-- most frequent stacks --\n")
                freeze.stacks.entries.sortedByDescending { it.value }.take(TOP_STACKS).forEach { (stack, count) ->
                    out.append("[$count samples, ${count * 100 / samples}%]\n")
                    stack.forEach { out.append("    at ").append(it).append('\n') }
                }
            }
            if (freeze.otherThreads != null) {
                out.append("\n-- other busy threads at the threshold --\n").append(freeze.otherThreads)
            }
            if (freeze.observers != null) {
                out.append("\n-- NotificationCenter observers at the threshold --\n").append(freeze.observers)
            }
            out.append('\n').append(environment())
            out.append("\n-- live objects (after GC) --\n").append(liveObjects())
            appendReport(out.toString())

            AndroidUtilities.runOnUIThread { showFreezeBulletin(freeze.durationMs) }
        }
    }
}
