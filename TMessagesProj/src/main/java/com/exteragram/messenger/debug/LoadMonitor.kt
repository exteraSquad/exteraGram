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
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.os.health.HealthStats
import android.os.health.SystemHealthManager
import android.system.Os
import android.system.OsConstants
import org.json.JSONArray
import org.json.JSONObject
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildVars
import org.telegram.messenger.DispatchQueue
import org.telegram.messenger.FileLog
import org.telegram.messenger.LiteMode
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.ForegroundDetector
import org.telegram.ui.LaunchActivity
import java.io.File
import java.io.FileInputStream
import java.lang.ref.WeakReference
import java.lang.reflect.Modifier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LoadMonitor {

    const val MIN_CPU_PERCENT = 1
    const val MAX_CPU_PERCENT = 50
    const val MIN_EPISODE_MINUTES = 5

    private const val SAMPLE_INTERVAL_MS = 60_000L
    private const val MIN_EPISODE_MS = MIN_EPISODE_MINUTES * 60_000L
    private const val ALERT_DELAY_MS = 1_000L
    private const val HEALTH_REUSE_MS = 60_000L
    private const val MAX_SAMPLES = 240
    private const val MAX_REPORTS = 10
    private const val TOP_THREADS = 30
    private const val SAMPLE_TOP_THREADS = 3
    private const val ALERT_TOP_THREADS = 3
    private const val MIN_SAMPLE_MS = 1_000L
    private const val COMM_LENGTH = 15
    private const val ORIGIN_MIN_MS = 200L
    private const val ORIGIN_MAX_ATTEMPTS = 8

    private const val TRIGGER_MANUAL = "manual"
    private const val TRIGGER_BACKGROUND = "background"
    private const val OTHER_THREADS = "<unattributed>"

    private const val STATE_FOREGROUND = 0
    private const val STATE_BACKGROUND = 1
    private const val STATE_SCREEN_OFF = 2
    private val STATE_NAMES = arrayOf("foreground", "background", "screenOff")
    private val SKIPPED_FRAMES = arrayOf(
        "java.", "javax.", "kotlin.", "android.", "com.android.", "dalvik.", "libcore.", "sun.", "jdk.",
        "de.robv.android.xposed.", LoadMonitor::class.java.name
    )

    @Volatile
    private var sampler: Sampler? = null
    @Volatile
    private var pendingAlert: PendingAlert? = null
    private var alertRef: WeakReference<AlertDialog>? = null
    private val healthKeyNames = HashMap<String, Map<Int, String>>()
    private val threadOrigins = HashMap<String, String>()
    private val originAttempts = HashMap<String, Int>()

    val reportsDir: File by lazy { debugFilesDir("loadreports") }

    private val clockTicks: Long by lazy {
        Os.sysconf(OsConstants._SC_CLK_TCK).takeIf { it > 0 } ?: 100L
    }

    @JvmStatic
    fun init() {
        if (loadMonitorEnabled) {
            sampler = Sampler()
        }
    }

    @JvmStatic
    fun currentStateName(): String = STATE_NAMES[currentState()]

    fun setEnabled(enabled: Boolean) {
        if (loadMonitorEnabled == enabled) return
        loadMonitorEnabled = enabled
        sampler?.stop()
        sampler = if (enabled) Sampler() else null
        if (!enabled) {
            pendingAlert = null
        }
    }

    fun getCpuPercent(): Int = loadMonitorCpuPercent.coerceIn(MIN_CPU_PERCENT, MAX_CPU_PERCENT)

    fun setCpuPercent(percent: Int) {
        loadMonitorCpuPercent = percent
    }

    fun getSummary(): String = sampler?.summary ?: "${formatDuration(Process.getElapsedCpuTime())} CPU since launch"

    fun getLastReport(): File? = reportsDir.listFiles()
        ?.filter { it.name.endsWith(".json") }
        ?.maxByOrNull { it.lastModified() }

    fun getReportsSize(): Long = reportsDir.listFiles()?.sumOf { it.length() } ?: 0L

    fun deleteReports(onDone: () -> Unit) {
        Utilities.globalQueue.postRunnable {
            reportsDir.listFiles()?.forEach { it.delete() }
            AndroidUtilities.runOnUIThread { onDone() }
        }
    }

    fun shareLastReport(activity: Activity) {
        getLastReport()?.let { share(activity, it) }
    }

    fun captureAndShare(activity: Activity) {
        val current = sampler
        val task = Runnable {
            val result = runCatching {
                writeReport(current?.captureReport(TRIGGER_MANUAL) ?: baseReport(TRIGGER_MANUAL), TRIGGER_MANUAL)
            }
            AndroidUtilities.runOnUIThread {
                result.onSuccess { share(activity, it) }.onFailure {
                    FileLog.e(it)
                    BulletinFactory.global().createSimpleBulletin(R.raw.error, "Load report failed: $it").show()
                }
            }
        }
        if (current != null) {
            current.queue.postRunnable(task)
        } else {
            Utilities.globalQueue.postRunnable(task)
        }
    }

    private fun share(activity: Activity, file: File) {
        shareDebugFile(activity, file, "application/json", "Share load report")
    }

    private fun showPendingAlert() {
        val alert = pendingAlert ?: return
        val activity = LaunchActivity.instance
        if (sampler == null || activity == null || activity.isFinishing || alertRef?.get()?.isShowing == true) {
            return
        }
        pendingAlert = null
        val dialog = AlertDialog.Builder(activity)
            .setTitle("High background load")
            .setMessage(alert.message)
            .setPositiveButton("Share") { _, _ -> share(activity, alert.file) }
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

    private fun currentState(): Int {
        val powerManager = ApplicationLoader.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager != null && !powerManager.isInteractive) {
            return STATE_SCREEN_OFF
        }
        return if (ForegroundDetector.getInstance()?.isForeground == true) STATE_FOREGROUND else STATE_BACKGROUND
    }

    private fun isPlugged(): Boolean? = runCatching {
        ApplicationLoader.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.let { it.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0 }
    }.getOrNull()

    private fun batteryJson(): JSONObject = JSONObject()
        .put("level", LiteMode.getBatteryLevel())
        .put("plugged", isPlugged())

    private fun uidRxBytes(): Long = TrafficStats.getUidRxBytes(Process.myUid())

    private fun uidTxBytes(): Long = TrafficStats.getUidTxBytes(Process.myUid())

    private fun readThreads(): HashMap<Int, ThreadStat> {
        val result = HashMap<Int, ThreadStat>()
        val tids = File("/proc/self/task").list() ?: return result
        val buffer = ByteArray(1024)
        for (tid in tids) {
            val id = tid.toIntOrNull() ?: continue
            val length = try {
                FileInputStream("/proc/self/task/$tid/stat").use { it.read(buffer) }
            } catch (e: Exception) {
                continue
            }
            if (length > 0) {
                parseStat(String(buffer, 0, length))?.let { result[id] = it }
            }
        }
        return result
    }

    private fun parseStat(stat: String): ThreadStat? {
        val open = stat.indexOf('(')
        val close = stat.lastIndexOf(')')
        if (open < 0 || close <= open || close + 2 >= stat.length) return null
        val fields = stat.substring(close + 2).split(' ')
        if (fields.size < 13) return null
        val utime = fields[11].toLongOrNull() ?: return null
        val stime = fields[12].toLongOrNull() ?: return null
        return ThreadStat(stat.substring(open + 1, close), utime + stime)
    }

    private fun ticksToMs(ticks: Long): Long = ticks * 1000 / clockTicks

    private fun threadDeltas(previous: Map<Int, ThreadStat>, current: Map<Int, ThreadStat>): HashMap<String, Long> {
        val result = HashMap<String, Long>()
        for ((tid, stat) in current) {
            val old = previous[tid]
            val ticks = if (old != null && old.name == stat.name) stat.ticks - old.ticks else stat.ticks
            if (ticks > 0) {
                result[stat.name] = (result[stat.name] ?: 0L) + ticksToMs(ticks)
            }
        }
        return result
    }

    private fun topThreads(threads: Map<String, Long>, limit: Int): List<Pair<String, Long>> =
        threads.entries.sortedByDescending { it.value }.take(limit).map { it.key to it.value }

    private fun threadsJson(threads: List<Pair<String, Long>>, withOrigin: Boolean = false): JSONArray {
        val array = JSONArray()
        for ((name, ms) in threads) {
            val json = JSONObject().put("name", name).put("cpuMs", ms)
            if (withOrigin) {
                json.put("at", originOf(name))
            }
            array.put(json)
        }
        return array
    }

    private fun originOf(name: String): String? = synchronized(threadOrigins) { threadOrigins[name] }

    private fun resolveOrigins(names: List<String>) {
        val pending = synchronized(threadOrigins) {
            names.filter {
                it != OTHER_THREADS && !threadOrigins.containsKey(it) && (originAttempts[it] ?: 0) < ORIGIN_MAX_ATTEMPTS
            }
        }
        if (pending.isEmpty()) return
        val traces = runCatching { Thread.getAllStackTraces() }.getOrNull() ?: return
        synchronized(threadOrigins) {
            for (name in pending) {
                originAttempts[name] = (originAttempts[name] ?: 0) + 1
            }
            for ((thread, trace) in traces) {
                val name = thread.name.take(COMM_LENGTH)
                if (!pending.contains(name) || threadOrigins.containsKey(name)) continue
                appFrame(trace)?.let { threadOrigins[name] = it }
            }
        }
    }

    private fun appFrame(trace: Array<StackTraceElement>): String? {
        for (element in trace) {
            if (SKIPPED_FRAMES.any { element.className.startsWith(it) }) continue
            return "${element.className.substringAfterLast('.')}.${element.methodName}:${element.lineNumber}"
        }
        return null
    }

    private fun takeHealthStats(): HealthStats? = runCatching {
        val manager = ApplicationLoader.applicationContext.getSystemService(Context.SYSTEM_HEALTH_SERVICE) as? SystemHealthManager
        manager?.takeMyUidSnapshot()
    }.onFailure { FileLog.e(it) }.getOrNull()

    private fun healthKeyName(dataType: String, key: Int): String {
        val names = synchronized(healthKeyNames) {
            healthKeyNames.getOrPut(dataType) {
                runCatching {
                    val className = if (dataType.contains('.')) dataType else "android.os.health.$dataType"
                    Class.forName(className).fields
                        .filter { Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType }
                        .associate { it.getInt(null) to it.name }
                }.getOrDefault(emptyMap())
            }
        }
        return names[key] ?: key.toString()
    }

    private fun healthJson(stats: HealthStats): JSONObject {
        val type = stats.dataType
        val json = JSONObject().put("type", type)
        if (stats.measurementKeyCount > 0) {
            val measurements = JSONObject()
            for (i in 0 until stats.measurementKeyCount) {
                val key = stats.getMeasurementKeyAt(i)
                measurements.put(healthKeyName(type, key), stats.getMeasurement(key))
            }
            json.put("measurements", measurements)
        }
        if (stats.timerKeyCount > 0) {
            val timers = JSONObject()
            for (i in 0 until stats.timerKeyCount) {
                val key = stats.getTimerKeyAt(i)
                timers.put(healthKeyName(type, key), JSONArray().put(stats.getTimerCount(key)).put(stats.getTimerTime(key)))
            }
            json.put("timers", timers)
        }
        if (stats.measurementsKeyCount > 0) {
            val maps = JSONObject()
            for (i in 0 until stats.measurementsKeyCount) {
                val key = stats.getMeasurementsKeyAt(i)
                val values = JSONObject()
                for ((name, value) in stats.getMeasurements(key)) {
                    values.put(name, value)
                }
                maps.put(healthKeyName(type, key), values)
            }
            json.put("measurementMaps", maps)
        }
        if (stats.timersKeyCount > 0) {
            val maps = JSONObject()
            for (i in 0 until stats.timersKeyCount) {
                val key = stats.getTimersKeyAt(i)
                val values = JSONObject()
                for ((name, timer) in stats.getTimers(key)) {
                    values.put(name, JSONArray().put(timer.count).put(timer.time))
                }
                maps.put(healthKeyName(type, key), values)
            }
            json.put("timerMaps", maps)
        }
        if (stats.statsKeyCount > 0) {
            val maps = JSONObject()
            for (i in 0 until stats.statsKeyCount) {
                val key = stats.getStatsKeyAt(i)
                val values = JSONObject()
                for ((name, nested) in stats.getStats(key)) {
                    values.put(name, healthJson(nested))
                }
                maps.put(healthKeyName(type, key), values)
            }
            json.put("stats", maps)
        }
        return json
    }

    private fun baseReport(trigger: String): JSONObject {
        val context = ApplicationLoader.applicationContext
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val lifetime = HashMap<String, Long>()
        for (stat in readThreads().values) {
            lifetime[stat.name] = (lifetime[stat.name] ?: 0L) + ticksToMs(stat.ticks)
        }
        val lifetimeTop = topThreads(lifetime, TOP_THREADS)
        resolveOrigins(lifetimeTop.map { it.first })
        return JSONObject()
            .put("format", 1)
            .put("trigger", trigger)
            .put("createdAt", System.currentTimeMillis())
            .put("app", JSONObject()
                .put("version", BuildVars.BUILD_VERSION_STRING)
                .put("code", BuildVars.BUILD_VERSION)
                .put("debug", BuildVars.DEBUG_VERSION))
            .put("device", JSONObject()
                .put("manufacturer", Build.MANUFACTURER)
                .put("model", Build.MODEL)
                .put("sdk", Build.VERSION.SDK_INT)
                .put("cores", Runtime.getRuntime().availableProcessors()))
            .put("battery", batteryJson())
            .put("settings", JSONObject()
                .put("accounts", UserConfig.getActivatedAccountsCount())
                .put("proxy", SharedConfig.isProxyEnabled())
                .put("logs", BuildVars.LOGS_ENABLED)
                .put("powerSave", powerManager?.isPowerSaveMode == true)
                .put("ignoringBatteryOptimizations", powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true))
            .put("process", JSONObject()
                .put("uptimeMs", SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime())
                .put("cpuMs", Process.getElapsedCpuTime())
                .put("state", STATE_NAMES[currentState()])
                .put("threads", threadsJson(lifetimeTop, true)))
            .put("healthNow", takeHealthStats()?.let { healthJson(it) })
    }

    private fun writeReport(report: JSONObject, trigger: String): File {
        reportsDir.mkdirs()
        val name = "load_${BuildVars.BUILD_VERSION_STRING}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}_$trigger.json"
        val file = File(reportsDir, name)
        file.writeText(report.toString(1))
        reportsDir.listFiles()
            ?.filter { it.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_REPORTS)
            ?.forEach { it.delete() }
        return file
    }

    private fun formatDuration(ms: Long): String {
        val seconds = ms / 1000
        return when {
            ms < 1000 -> "${ms}ms"
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${seconds % 3600 / 60}m"
        }
    }

    private fun formatPercent(cpuMs: Long, wallMs: Long): String =
        String.format(Locale.US, "%.1f%%", if (wallMs > 0) cpuMs * 100.0 / wallMs else 0.0)

    private fun describeEpisode(wallMs: Long, cpuMs: Long, rxBytes: Long, txBytes: Long, threads: List<Pair<String, Long>>): String {
        val builder = StringBuilder()
            .append("In ${formatDuration(wallMs)} in the background, the app used ${formatPercent(cpuMs, wallMs)} of one core: ")
            .append("${formatDuration(cpuMs)} of CPU")
        if (rxBytes >= 0 && txBytes >= 0) {
            builder.append(", ${AndroidUtilities.formatFileSize(rxBytes)} received, ${AndroidUtilities.formatFileSize(txBytes)} sent")
        }
        builder.append('.')
        if (threads.isNotEmpty()) {
            builder.append("\n\nBusiest threads: ")
                .append(threads.joinToString(", ") { "${it.first} ${formatDuration(it.second)}" })
                .append('.')
        }
        builder.append("\n\nThe report holds thread names, traffic counters and battery stats, no message content.")
        return builder.toString()
    }

    private class ThreadStat(val name: String, val ticks: Long)

    private class PendingAlert(val file: File, val message: String)

    private class Episode(
        val startedAt: Long,
        val startRealtime: Long,
        val cpuMs: Long,
        val rxBytes: Long,
        val txBytes: Long,
        val threads: Map<Int, ThreadStat>,
        val health: HealthStats?
    ) {
        var reported = false
    }

    private class Sampler : ForegroundDetector.Listener, NotificationCenter.NotificationCenterDelegate {

        val queue = DispatchQueue("loadMonitorQueue")
        private val sampleRunnable = Runnable {
            sample()
            scheduleNext()
        }

        private val startedAt = System.currentTimeMillis()
        private var startHealth: HealthStats? = null
        private var startBattery: JSONObject? = null
        private var recentHealth: HealthStats? = null
        private var recentHealthRealtime = 0L
        private var lastRealtime = 0L
        private var lastCpuMs = 0L
        private var lastRxBytes = -1L
        private var lastTxBytes = -1L
        private var lastState = STATE_FOREGROUND
        private var threads = HashMap<Int, ThreadStat>()
        private var episode: Episode? = null

        private val wallMs = LongArray(STATE_NAMES.size)
        private val cpuMs = LongArray(STATE_NAMES.size)
        private val rxBytes = LongArray(STATE_NAMES.size)
        private val txBytes = LongArray(STATE_NAMES.size)
        private val threadMs = HashMap<String, LongArray>()
        private val samples = ArrayDeque<JSONObject>()
        private var observers: NotificationCenter.ObserversGroup? = null

        @Volatile
        var summary: String? = null
            private set

        init {
            ForegroundDetector.getInstance()?.addListener(this)
            AndroidUtilities.runOnUIThread {
                observers = NotificationCenter.getGlobalInstance().createObserversGroup(this).add(NotificationCenter.screenStateChanged)
            }
            queue.postRunnable {
                startHealth = recentHealthStats()
                startBattery = batteryJson()
                lastRealtime = SystemClock.elapsedRealtime()
                lastCpuMs = Process.getElapsedCpuTime()
                lastRxBytes = uidRxBytes()
                lastTxBytes = uidTxBytes()
                threads = readThreads()
                updateState()
                scheduleNext()
            }
        }

        fun stop() {
            ForegroundDetector.getInstance()?.removeListener(this)
            AndroidUtilities.runOnUIThread {
                observers?.removeAllObservers()
                observers = null
            }
            queue.cleanupQueue()
            queue.recycle()
        }

        override fun onBecameForeground() {
            queue.postRunnable { sample() }
            AndroidUtilities.runOnUIThread({ showPendingAlert() }, ALERT_DELAY_MS)
        }

        override fun onBecameBackground() {
            queue.postRunnable { sample() }
        }

        override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
            if (id == NotificationCenter.screenStateChanged) {
                queue.postRunnable { sample() }
            }
        }

        private fun scheduleNext() {
            queue.cancelRunnable(sampleRunnable)
            queue.postRunnable(sampleRunnable, SAMPLE_INTERVAL_MS)
        }

        private fun sample() {
            val now = SystemClock.elapsedRealtime()
            val cpu = Process.getElapsedCpuTime()
            val rx = uidRxBytes()
            val tx = uidTxBytes()
            val current = readThreads()
            val state = lastState
            val interval = now - lastRealtime
            val cpuDelta = cpu - lastCpuMs
            val rxDelta = if (rx >= 0 && lastRxBytes >= 0) rx - lastRxBytes else 0L
            val txDelta = if (tx >= 0 && lastTxBytes >= 0) tx - lastTxBytes else 0L
            val deltas = threadDeltas(threads, current)
            val other = cpuDelta - deltas.values.sum()
            if (other > 0) {
                deltas[OTHER_THREADS] = other
            }
            resolveOrigins(topThreads(deltas, SAMPLE_TOP_THREADS).filter { it.second >= ORIGIN_MIN_MS }.map { it.first })

            wallMs[state] += interval
            cpuMs[state] += cpuDelta
            rxBytes[state] += rxDelta
            txBytes[state] += txDelta
            for ((name, ms) in deltas) {
                threadMs.getOrPut(name) { LongArray(STATE_NAMES.size) }[state] += ms
            }
            if (interval >= MIN_SAMPLE_MS) {
                samples.addLast(JSONObject()
                    .put("t", System.currentTimeMillis())
                    .put("state", STATE_NAMES[state])
                    .put("wallMs", interval)
                    .put("cpuMs", cpuDelta)
                    .put("rxBytes", rxDelta)
                    .put("txBytes", txDelta)
                    .put("plugged", isPlugged())
                    .put("threads", threadsJson(topThreads(deltas, SAMPLE_TOP_THREADS))))
                while (samples.size > MAX_SAMPLES) {
                    samples.removeFirst()
                }
            }

            lastRealtime = now
            lastCpuMs = cpu
            lastRxBytes = rx
            lastTxBytes = tx
            threads = current
            summary = "${formatDuration(cpuMs.sum())} CPU, ${AndroidUtilities.formatFileSize(rxBytes.sum() + txBytes.sum())}"

            checkEpisode(now, cpu, rx, tx, current)
            updateState()
        }

        private fun updateState() {
            lastState = currentState()
            if (lastState == STATE_FOREGROUND) {
                episode = null
            } else if (episode == null) {
                episode = Episode(
                    System.currentTimeMillis(),
                    lastRealtime,
                    lastCpuMs,
                    lastRxBytes,
                    lastTxBytes,
                    threads,
                    recentHealthStats()
                )
            }
        }

        private fun checkEpisode(now: Long, cpu: Long, rx: Long, tx: Long, current: Map<Int, ThreadStat>) {
            val episode = episode ?: return
            val elapsed = now - episode.startRealtime
            if (episode.reported || elapsed < MIN_EPISODE_MS) return
            val used = cpu - episode.cpuMs
            if (used * 100 < getCpuPercent() * elapsed) return
            episode.reported = true
            val file = runCatching { writeReport(buildReport(TRIGGER_BACKGROUND), TRIGGER_BACKGROUND) }
                .onFailure { FileLog.e(it) }
                .getOrNull() ?: return
            val rxUsed = if (rx >= 0 && episode.rxBytes >= 0) rx - episode.rxBytes else -1L
            val txUsed = if (tx >= 0 && episode.txBytes >= 0) tx - episode.txBytes else -1L
            val busiest = topThreads(threadDeltas(episode.threads, current), ALERT_TOP_THREADS)
            pendingAlert = PendingAlert(file, describeEpisode(elapsed, used, rxUsed, txUsed, busiest))
            if (ForegroundDetector.getInstance()?.isForeground == true) {
                AndroidUtilities.runOnUIThread { showPendingAlert() }
            }
        }

        private fun recentHealthStats(): HealthStats? {
            val now = SystemClock.elapsedRealtime()
            if (recentHealth == null || now - recentHealthRealtime > HEALTH_REUSE_MS) {
                recentHealth = takeHealthStats()
                recentHealthRealtime = now
            }
            return recentHealth
        }

        fun captureReport(trigger: String): JSONObject {
            sample()
            return buildReport(trigger)
        }

        fun buildReport(trigger: String): JSONObject {
            val report = baseReport(trigger)
            val states = JSONObject()
            for (state in STATE_NAMES.indices) {
                states.put(STATE_NAMES[state], JSONObject()
                    .put("wallMs", wallMs[state])
                    .put("cpuMs", cpuMs[state])
                    .put("rxBytes", rxBytes[state])
                    .put("txBytes", txBytes[state]))
            }
            val threadsArray = JSONArray()
            for ((name, byState) in threadMs.entries.sortedByDescending { it.value.sum() }.take(TOP_THREADS)) {
                val thread = JSONObject().put("name", name).put("cpuMs", byState.sum()).put("at", originOf(name))
                for (state in STATE_NAMES.indices) {
                    thread.put(STATE_NAMES[state], byState[state])
                }
                threadsArray.put(thread)
            }
            report.put("monitor", JSONObject()
                .put("startedAt", startedAt)
                .put("sampleIntervalMs", SAMPLE_INTERVAL_MS)
                .put("batteryAtStart", startBattery)
                .put("states", states)
                .put("threads", threadsArray)
                .put("samples", JSONArray(samples)))
            episode?.let {
                val now = SystemClock.elapsedRealtime()
                val cpu = Process.getElapsedCpuTime()
                val rx = uidRxBytes()
                val tx = uidTxBytes()
                report.put("episode", JSONObject()
                    .put("startedAt", it.startedAt)
                    .put("wallMs", now - it.startRealtime)
                    .put("cpuMs", cpu - it.cpuMs)
                    .put("rxBytes", if (rx >= 0 && it.rxBytes >= 0) rx - it.rxBytes else -1L)
                    .put("txBytes", if (tx >= 0 && it.txBytes >= 0) tx - it.txBytes else -1L)
                    .put("threads", threadsJson(topThreads(threadDeltas(it.threads, readThreads()), TOP_THREADS), true)))
                it.health?.let { health -> report.put("healthEpisodeStart", healthJson(health)) }
            }
            startHealth?.let { report.put("healthStart", healthJson(it)) }
            return report
        }
    }
}
