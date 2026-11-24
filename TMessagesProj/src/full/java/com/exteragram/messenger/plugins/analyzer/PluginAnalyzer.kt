/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.analyzer

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.os.RemoteException
import android.os.SystemClock
import com.exteragram.messenger.plugins.updates.sha256Hex
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.DispatchQueue
import org.telegram.messenger.FileLog
import org.telegram.tgnet.TLRPC
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object PluginAnalyzer {

    const val STATUS_DONE = 0
    const val STATUS_UNAVAILABLE = 1
    const val STATUS_FAILED = 2
    const val STATUS_TIMEOUT = 3
    const val STATUS_CRASHED = 4
    const val STATUS_TOO_LARGE = 5
    const val STATUS_MEMORY = 6

    class Request(
        val filePath: String,
        val name: String,
        val previousPath: String?,
        val useCache: Boolean,
    )

    class Result(
        val status: Int,
        val snapshot: File?,
        val report: AnalyzerReport? = null,
        val diff: AnalyzerDiff? = null,
        val message: String? = null,
        val sha256: String? = null,
        val diffUnavailable: Boolean = false,
    )

    class Task internal constructor() {
        @Volatile
        var cancelled = false
            private set
        private val released = AtomicBoolean(false)

        fun cancel() {
            cancelled = true
            release()
        }

        internal fun release() {
            if (released.compareAndSet(false, true)) {
                onTaskReleased()
            }
        }
    }

    private const val PREPARE_RETRY_MS = 60_000L
    private const val PREPARE_TIMEOUT_MS = 15_000L
    private const val CONNECT_TIMEOUT_MS = 10_000L
    private const val HARD_TIMEOUT_MS = 45_000L
    private const val PREVIOUS_TIMEOUT_MS = 35_000L
    private const val WATCH_INTERVAL_MS = 250L
    private const val MAX_FILE_BYTES = 64L * 1024 * 1024
    private const val SNAPSHOT_MAX_AGE_MS = 24L * 60 * 60 * 1000
    private const val MIB = 1024L * 1024
    private const val RECYCLE_ANON_BYTES = 160 * MIB
    private const val MIN_ANON_LIMIT_BYTES = 256 * MIB
    private const val MAX_ANON_LIMIT_BYTES = 640 * MIB

    private const val ABORT_NONE = 0
    private const val ABORT_TIMEOUT = 1
    private const val ABORT_MEMORY = 2
    private const val ABORT_RELEASED = 3

    private val queue = DispatchQueue("pluginAnalyzer")
    private val watchQueue = DispatchQueue("pluginAnalyzerWatch")

    @Volatile
    private var binder: IBinder? = null
    @Volatile
    private var prepared: Session? = null
    private var connection: ServiceConnection? = null
    private val pendingConnects = ArrayList<CountDownLatch>()
    private val liveTasks = AtomicInteger()
    private val snapshotCounter = AtomicInteger()
    private var trimCallbacksRegistered = false

    private class Running(val pid: Int) {
        val abort = AtomicInteger(ABORT_NONE)

        fun abort(reason: Int) {
            if (abort.compareAndSet(ABORT_NONE, reason)) {
                Process.killProcess(pid)
            }
        }
    }

    @Volatile
    private var running: Running? = null

    private val anonLimitBytes: Long by lazy {
        val memoryInfo = ActivityManager.MemoryInfo()
        val activityManager = ApplicationLoader.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        activityManager?.getMemoryInfo(memoryInfo)
        (memoryInfo.totalMem / 8).coerceIn(MIN_ANON_LIMIT_BYTES, MAX_ANON_LIMIT_BYTES)
    }

    private val trimCallbacks = object : ComponentCallbacks2 {
        @Suppress("DEPRECATION")
        override fun onTrimMemory(level: Int) {
            if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL || level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
                running?.abort(ABORT_MEMORY)
            }
        }

        override fun onConfigurationChanged(newConfig: Configuration) = Unit

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onLowMemory() {
            running?.abort(ABORT_MEMORY)
        }
    }

    private val snapshotDir: File
        get() = File(ApplicationLoader.applicationContext.cacheDir, "plugin_analyzer")

    private val cache by lazy { AnalyzerCache(File(ApplicationLoader.applicationContext.noBackupFilesDir, "plugin_analyzer/reports")) }

    private val buildId: String? by lazy { analyzerBuildId(ApplicationLoader.applicationContext) }
    private val updates by lazy { AnalyzerUpdateStore(ApplicationLoader.applicationContext) }
    private val prepareRetryAfter = HashMap<String, Long>()
    private var catalogUpdate: AnalyzerUpdate? = null
    private var updateCatalog: AnalysisCatalog? = null

    private class Session(val remote: IBinder, val pid: Int, val catalog: AnalysisCatalog, val update: AnalyzerUpdate?)

    internal fun getUpdateInfo(callback: (version: String?, pending: Boolean) -> Unit) {
        if (!AnalyzerUpdate.enabled) {
            callback(null, false)
            return
        }
        queue.postRunnable {
            val candidates = updates.candidates()
            val pending = candidates.any(updates::isPending)
            val active = candidates.firstOrNull { !updates.isPending(it) }
            AndroidUtilities.runOnUIThread { callback(active?.version, pending) }
        }
    }

    internal fun selectUpdate(
        response: TLRPC.messages_Messages,
        callback: (kotlin.Result<AnalyzerUpdater.Candidate?>) -> Unit,
    ) {
        queue.postRunnable {
            val result = runCatching { AnalyzerUpdater.select(response, updates::canStage) }.onFailure { FileLog.e(it) }
            AndroidUtilities.runOnUIThread { callback(result) }
        }
    }

    internal fun stageUpdate(update: AnalyzerUpdate, file: File, callback: (Boolean) -> Unit) {
        queue.postRunnable {
            val staged = runCatching { updates.stage(update, file) }.onFailure { FileLog.e(it) }.getOrDefault(false)
            AndroidUtilities.runOnUIThread { callback(staged) }
        }
    }

    fun analyze(request: Request, callback: (Result) -> Unit): Task {
        AnalyzerUpdater.check()
        val task = Task()
        liveTasks.incrementAndGet()
        queue.postRunnable {
            val result = run(request, task)
            if (task.cancelled) {
                result.snapshot?.delete()
                return@postRunnable
            }
            AndroidUtilities.runOnUIThread {
                if (task.cancelled) {
                    result.snapshot?.delete()
                } else {
                    task.release()
                    callback(result)
                }
            }
        }
        return task
    }

    private fun onTaskReleased() {
        if (liveTasks.decrementAndGet() > 0) {
            return
        }
        running?.abort(ABORT_RELEASED)
        AndroidUtilities.runOnUIThread {
            if (liveTasks.get() == 0) {
                unbind()
            }
        }
    }

    fun installCopy(snapshot: File?, sourcePath: String, sha256: String): File? {
        try {
            if (snapshot != null && snapshot.isFile && snapshot.sha256Hex() == sha256) {
                return snapshot
            }
            val source = File(sourcePath)
            if (!source.isFile || source.length() > MAX_FILE_BYTES) {
                return null
            }
            val copy = newSnapshot(source)
            source.copyTo(copy, overwrite = true)
            if (copy.sha256Hex() == sha256) {
                return copy
            }
            copy.delete()
        } catch (e: Exception) {
            FileLog.e(e)
        }
        return null
    }

    private fun newSnapshot(source: File): File {
        val dir = snapshotDir
        dir.mkdirs()
        val deadline = System.currentTimeMillis() - SNAPSHOT_MAX_AGE_MS
        dir.listFiles()?.forEach { if (it.lastModified() < deadline) it.delete() }
        val id = "${Process.myPid()}_${SystemClock.elapsedRealtimeNanos()}_${snapshotCounter.incrementAndGet()}"
        return File(dir, "$id.${source.extension.ifEmpty { "plugin" }}")
    }

    private fun run(request: Request, task: Task): Result {
        val source = File(request.filePath)
        if (!source.isFile) {
            return Result(STATUS_FAILED, null, message = "file not found")
        }
        if (source.length() > MAX_FILE_BYTES) {
            return Result(STATUS_TOO_LARGE, null)
        }
        val snapshot: File
        val sha256: String
        try {
            snapshot = newSnapshot(source)
            source.copyTo(snapshot, overwrite = true)
            sha256 = snapshot.sha256Hex()
        } catch (e: Exception) {
            FileLog.e(e)
            return Result(STATUS_FAILED, null, message = e.toString())
        }
        if (task.cancelled) {
            return Result(STATUS_FAILED, snapshot, sha256 = sha256)
        }
        if (request.useCache && request.previousPath == null) {
            cachedReport(sha256, request.name)?.let { return Result(STATUS_DONE, snapshot, report = it, sha256 = sha256) }
        }

        val previous = request.previousPath?.let { File(it) }?.takeIf { it.isFile && it.length() <= MAX_FILE_BYTES }
        val previousSha256 = try {
            previous?.sha256Hex()
        } catch (_: Exception) {
            null
        }

        var current: Running? = null
        var remote: IBinder? = null
        var trial: AnalyzerUpdate? = null
        var recheck = false
        try {
            val session = prepareRemote(task) ?: return Result(STATUS_CRASHED, snapshot, message = "service did not start", sha256 = sha256)
            remote = session.remote
            trial = session.update?.takeIf { updates.isPending(it) }
            val expectedBuildId = session.update?.id ?: buildId
            current = Running(session.pid)
            running = current
            if (task.cancelled) {
                current.abort(ABORT_RELEASED)
            }
            watch(current, SystemClock.elapsedRealtime() + HARD_TIMEOUT_MS)
            val result = call(remote, PluginAnalyzerService.CALL_ANALYZE, { data ->
                data.writeString(snapshot.absolutePath)
                data.writeString(request.name)
                data.writeString(sha256)
                data.writeInt(if (request.useCache && trial == null) 1 else 0)
            }) { reply ->
                when (reply.readInt()) {
                    PluginAnalyzerService.STATUS_OK -> {
                        if (reply.readString() != expectedBuildId) {
                            return@call Result(STATUS_FAILED, snapshot, message = "unexpected analyzer build", sha256 = sha256)
                        }
                        val report = reply.readString()?.let { AnalyzerReport.parse(it, session.catalog) }
                        if (trial != null && report != null && !task.cancelled && current.abort.get() == ABORT_NONE) {
                            runCatching { updates.accept(trial) }.onFailure { FileLog.e(it) }
                            AndroidUtilities.runOnUIThread { AnalyzerUpdater.refreshInfo() }
                        }
                        Result(STATUS_DONE, snapshot, report = report, sha256 = sha256)
                    }

                    PluginAnalyzerService.STATUS_UNAVAILABLE -> {
                        recheck = session.update != null
                        Result(STATUS_UNAVAILABLE, snapshot, message = reply.readString(), sha256 = sha256)
                    }
                    else -> {
                        recheck = session.update != null
                        Result(STATUS_FAILED, snapshot, message = reply.readString(), sha256 = sha256)
                    }
                }
            }
            if (result.report == null || request.previousPath == null || task.cancelled || current.abort.get() != ABORT_NONE) {
                return result
            }
            val diff = if (previous == null || previousSha256 == null) {
                null
            } else {
                val comparing = Running(session.pid)
                running = comparing
                compare(remote, comparing, request.name, sha256, previous, previousSha256, expectedBuildId)
            }
            return Result(STATUS_DONE, snapshot, report = result.report, diff = diff, sha256 = sha256, diffUnavailable = diff == null)
        } catch (e: RemoteException) {
            binder = null
            if (trial != null && current?.abort?.get() == ABORT_NONE) {
                runCatching { updates.recordLoadFailure(trial) }.onFailure { FileLog.e(it) }
                AndroidUtilities.runOnUIThread { AnalyzerUpdater.refreshInfo() }
            }
            val status = when (current?.abort?.get()) {
                ABORT_TIMEOUT -> STATUS_TIMEOUT
                ABORT_MEMORY -> STATUS_MEMORY
                else -> STATUS_CRASHED
            }
            return Result(status, snapshot, message = e.toString(), sha256 = sha256)
        } catch (e: Exception) {
            FileLog.e(e)
            return Result(STATUS_FAILED, snapshot, message = e.toString(), sha256 = sha256)
        } finally {
            running = null
            if (current != null && remote != null && current.abort.get() == ABORT_NONE && remote.isBinderAlive &&
                (recheck || liveTasks.get() > 1 && anonBytes(current.pid) > RECYCLE_ANON_BYTES)
            ) {
                recycle(current, remote)
            }
        }
    }

    private fun compare(
        remote: IBinder,
        current: Running,
        name: String,
        sha256: String,
        previous: File,
        previousSha256: String,
        expectedBuildId: String?,
    ): AnalyzerDiff? {
        watch(current, SystemClock.elapsedRealtime() + PREVIOUS_TIMEOUT_MS)
        return try {
            call(remote, PluginAnalyzerService.CALL_DIFF, { data ->
                data.writeString(name)
                data.writeString(sha256)
                data.writeString(previous.absolutePath)
                data.writeString(previous.name)
                data.writeString(previousSha256)
            }) { reply ->
                if (reply.readInt() != PluginAnalyzerService.STATUS_OK) {
                    FileLog.e("Plugin comparison failed: ${reply.readString()}")
                    null
                } else if (reply.readString() != expectedBuildId) {
                    null
                } else {
                    reply.readString()?.let(AnalyzerDiff::parse)
                }
            }
        } catch (e: RemoteException) {
            binder = null
            null
        } catch (e: Exception) {
            FileLog.e(e)
            null
        }
    }

    private fun cachedReport(sha256: String, name: String): AnalyzerReport? {
        val session = prepared?.takeIf { it.remote.isBinderAlive }
        val update = if (session != null) session.update else readyUpdates().firstOrNull()
        if (update != null && updates.isPending(update)) return null
        val id = update?.id ?: buildId ?: return null
        val json = cache.get(id, sha256, name) ?: return null
        return try {
            val catalog = session?.catalog ?: update?.let(::catalogFor) ?: AnalysisCatalog.bundled
            AnalyzerReport.parse(json, catalog)
        } catch (e: Exception) {
            FileLog.e(e)
            null
        }
    }

    private fun readyUpdates(): List<AnalyzerUpdate> {
        if (!AnalyzerUpdate.enabled) return emptyList()
        val now = SystemClock.elapsedRealtime()
        prepareRetryAfter.entries.removeAll { it.value <= now }
        return updates.candidates().filter { it.id !in prepareRetryAfter }
    }

    private fun catalogFor(update: AnalyzerUpdate): AnalysisCatalog {
        updateCatalog?.takeIf { catalogUpdate?.id == update.id }?.let { return it }
        val catalog = update.readCatalog(update.archive(ApplicationLoader.applicationContext))
        catalogUpdate = update
        updateCatalog = catalog
        return catalog
    }

    private fun prepareRemote(task: Task): Session? {
        var connection = connectRemote() ?: return null
        prepared?.takeIf { it.remote === connection.first }?.let { return it }
        val tried = HashSet<String>()
        while (true) {
            if (task.cancelled) return null
            val candidate = readyUpdates().firstOrNull { tried.add(it.id) } ?: break
            val catalog = try {
                catalogFor(candidate)
            } catch (e: Exception) {
                FileLog.e(e)
                runCatching { updates.invalidate(candidate) }.onFailure { FileLog.e(it) }
                continue
            }
            for (attempt in 1..2) {
                val (remote, pid) = connection
                val current = Running(pid)
                running = current
                if (task.cancelled) current.abort(ABORT_RELEASED)
                watch(current, SystemClock.elapsedRealtime() + PREPARE_TIMEOUT_MS)
                var died = false
                val (status, error) = try {
                    call(remote, PluginAnalyzerService.CALL_PREPARE, {
                        it.writeString(candidate.payload)
                        it.writeString(candidate.signature)
                    }) { it.readInt() to it.readString() }
                } catch (e: RemoteException) {
                    died = true
                    PluginAnalyzerService.PREPARE_RETRY to e.toString()
                } finally {
                    running = null
                }
                if (task.cancelled || current.abort.get() == ABORT_RELEASED) return null
                if (status == PluginAnalyzerService.PREPARE_OK && current.abort.get() == ABORT_NONE) {
                    runCatching { updates.clearLoadFailures(candidate) }.onFailure { FileLog.e(it) }
                    return Session(remote, pid, catalog, candidate).also { prepared = it }
                }
                if (error != null) FileLog.e("Analyzer update ${candidate.release} failed to start: $error")
                val rejected = status == PluginAnalyzerService.PREPARE_REJECT && current.abort.get() == ABORT_NONE
                val invalid = status == PluginAnalyzerService.PREPARE_INVALID_ARCHIVE && current.abort.get() == ABORT_NONE
                val failed = !rejected && !invalid && (died || current.abort.get() != ABORT_NONE ||
                    status == PluginAnalyzerService.PREPARE_LOAD_FAILED)
                val dropped = failed && runCatching { updates.recordLoadFailure(candidate) }
                    .onFailure { FileLog.e(it) }.getOrDefault(false)
                if (rejected) {
                    discardUpdate(candidate)
                } else if (invalid) {
                    runCatching { updates.invalidate(candidate) }.onFailure { FileLog.e(it) }
                } else if (!dropped && (attempt == 2 || current.abort.get() != ABORT_NONE)) {
                    prepareRetryAfter[candidate.id] = SystemClock.elapsedRealtime() + PREPARE_RETRY_MS
                }
                recycle(current, remote)
                connection = connectRemote() ?: return null
                if (rejected || invalid || dropped || current.abort.get() != ABORT_NONE) break
            }
        }
        return Session(connection.first, connection.second, AnalysisCatalog.bundled, null).also { prepared = it }
    }

    private fun discardUpdate(update: AnalyzerUpdate) {
        runCatching { updates.reject(update) }.onFailure { FileLog.e(it) }
    }

    private fun connectRemote(): Pair<IBinder, Int>? {
        repeat(2) {
            val remote = obtainBinder() ?: return null
            try {
                return remote to call(remote, PluginAnalyzerService.CALL_PID, {}) { it.readInt() }
            } catch (_: RemoteException) {
                binder = null
            }
        }
        return null
    }

    private fun watch(current: Running, deadline: Long) {
        watchQueue.postRunnable(object : Runnable {
            override fun run() {
                if (running !== current || current.abort.get() != ABORT_NONE) {
                    return
                }
                if (SystemClock.elapsedRealtime() >= deadline) {
                    current.abort(ABORT_TIMEOUT)
                } else if (anonBytes(current.pid) > anonLimitBytes) {
                    current.abort(ABORT_MEMORY)
                } else {
                    watchQueue.postRunnable(this, WATCH_INTERVAL_MS)
                }
            }
        }, WATCH_INTERVAL_MS)
    }

    private fun anonBytes(pid: Int): Long {
        return try {
            File("/proc/$pid/status").readLines()
                .firstOrNull { it.startsWith("RssAnon:") }
                ?.substringAfter(':')?.trim()?.substringBefore(' ')?.toLongOrNull()
                ?.times(1024) ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun recycle(current: Running, remote: IBinder) {
        binder = null
        prepared = null
        if (remote.isBinderAlive) Process.killProcess(current.pid)
        val deadline = SystemClock.elapsedRealtime() + CONNECT_TIMEOUT_MS
        while (remote.isBinderAlive && SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(20)
        }
        val latch = CountDownLatch(1)
        AndroidUtilities.runOnUIThread {
            unbind()
            latch.countDown()
        }
        latch.await(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }

    private inline fun <T> call(remote: IBinder, code: Int, write: (Parcel) -> Unit, read: (Parcel) -> T): T {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(PluginAnalyzerService.DESCRIPTOR)
            write(data)
            if (!remote.transact(code, data, reply, 0)) {
                throw RemoteException("transaction rejected")
            }
            return read(reply)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun obtainBinder(): IBinder? {
        binder?.takeIf { it.isBinderAlive }?.let { return it }
        val latch = CountDownLatch(1)
        AndroidUtilities.runOnUIThread { connect(latch) }
        latch.await(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return binder?.takeIf { it.isBinderAlive }
    }

    private fun connect(latch: CountDownLatch) {
        if (!trimCallbacksRegistered) {
            trimCallbacksRegistered = true
            ApplicationLoader.applicationContext.registerComponentCallbacks(trimCallbacks)
        }
        if (binder?.isBinderAlive == true) {
            latch.countDown()
            return
        }
        pendingConnects.add(latch)
        if (connection != null) {
            return
        }
        val context = ApplicationLoader.applicationContext
        val newConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                binder = service
                pendingConnects.forEach { it.countDown() }
                pendingConnects.clear()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                binder = null
            }

            override fun onBindingDied(name: ComponentName?) {
                binder = null
                if (connection === this) {
                    unbind()
                }
            }
        }
        val bound = try {
            context.bindService(Intent(context, PluginAnalyzerService::class.java), newConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            FileLog.e(e)
            false
        }
        if (bound) {
            connection = newConnection
        } else {
            pendingConnects.forEach { it.countDown() }
            pendingConnects.clear()
        }
    }

    private fun unbind() {
        val current = connection ?: return
        connection = null
        binder = null
        prepared = null
        pendingConnects.forEach { it.countDown() }
        pendingConnects.clear()
        try {
            ApplicationLoader.applicationContext.unbindService(current)
        } catch (_: Exception) {
        }
    }
}
