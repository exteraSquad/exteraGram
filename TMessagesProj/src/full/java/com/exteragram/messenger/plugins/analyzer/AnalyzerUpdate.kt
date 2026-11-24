/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.analyzer

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.AtomicFile
import com.exteragram.messenger.plugins.updates.SignedArtifact
import com.exteragram.messenger.plugins.updates.sha256Hex
import org.json.JSONArray
import org.json.JSONObject
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.FileLog
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

internal class AnalyzerUpdate private constructor(
    val payload: String,
    val signature: String,
    val release: Long,
    val channel: String,
    val version: String,
    private val minAppVersion: Int,
    private val maxAppVersion: Int,
    private val libraryHashes: Map<String, String>,
    private val textsHash: String,
) {
    val id: String = payload.toByteArray(Charsets.UTF_8).sha256Hex()
    private val entryNames = libraryHashes.keys.map(::libraryPath) + TEXTS

    fun isCompatible(): Boolean =
        enabled && processAbi in libraryHashes && (channel == "stable" || BuildConfig.FLAVOR_minApi == "beta") &&
            BuildConfig.EXTERA_ANALYZER_APP_VERSION_CODE in minAppVersion..maxAppVersion

    fun archive(context: Context): File = File(directory(context), "$id.zip")

    fun readLibrary(file: File): ByteArray {
        val abi = processAbi ?: throw IOException("Unsupported analyzer ABI")
        val hash = libraryHashes[abi] ?: throw IOException("Missing analyzer library for $abi")
        return readArchive(file) { readEntry(it, libraryPath(abi), hash, MAX_LIBRARY_BYTES) }
    }

    fun verifiesLibrary(bytes: ByteArray): Boolean {
        val hash = libraryHashes[processAbi] ?: return false
        return bytes.sha256Hex() == hash
    }

    fun readCatalog(file: File): AnalysisCatalog = readArchive(file, ::readCatalog)

    fun verifyArchive(file: File) {
        readArchive(file) { zip ->
            for ((abi, hash) in libraryHashes) {
                readEntry(zip, libraryPath(abi), hash, MAX_LIBRARY_BYTES)
            }
            readCatalog(zip)
        }
    }

    private fun libraryPath(abi: String): String = "$abi/$LIBRARY"

    private fun readCatalog(zip: ZipFile): AnalysisCatalog =
        AnalysisCatalog.parse(readEntry(zip, TEXTS, textsHash, MAX_TEXTS_BYTES).decodeToString())

    private fun <T> readArchive(file: File, read: (ZipFile) -> T): T {
        if (file.length() !in 1..MAX_ARCHIVE_BYTES) throw IOException("Invalid analyzer archive size")
        return ZipFile(file).use { zip ->
            if (zip.size() != entryNames.size || entryNames.any { zip.getEntry(it) == null }) {
                throw IOException("Invalid analyzer archive entries")
            }
            read(zip)
        }
    }

    private fun readEntry(zip: ZipFile, name: String, expectedHash: String, limit: Int): ByteArray {
        val entry = zip.getEntry(name)
        if (entry.size !in 1..limit.toLong()) throw IOException("Invalid analyzer entry size")
        val bytes = zip.getInputStream(entry).use { it.readBounded(limit) }
        if (bytes.sha256Hex() != expectedHash) throw IOException("Invalid analyzer entry digest")
        return bytes
    }

    companion object {
        private const val PUBLIC_KEY =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEmLNYz9fQ8N3cDsmBxHqN4ogLtDo6dluc5Aj9P4c1IIsgNWFFbqobAwDU1Pf4RqI33FubysDPy98zVekH4PO2Mg=="
        private const val ARM64 = "arm64-v8a"
        private const val ARMV7 = "armeabi-v7a"
        private const val LIBRARY = "libextera_analyzer.so"
        private const val TEXTS = "texts.json"
        private const val MAX_LIBRARY_BYTES = 32 * 1024 * 1024
        private const val MAX_TEXTS_BYTES = 2 * 1024 * 1024
        const val MAX_ARCHIVE_BYTES = 68L * 1024 * 1024
        const val REPORT_SCHEMA = 1
        private val HASH = Regex("[0-9a-f]{64}")
        private val SIGNATURE = Regex("[A-Za-z0-9+/]{1,100}={0,2}")
        private val VERSION = Regex("[0-9]+(\\.[0-9]+){1,3}(-[0-9A-Za-z.]{1,16})?")

        private val processAbi: String? by lazy {
            if (Process.is64Bit()) {
                ARM64.takeIf { it in Build.SUPPORTED_64_BIT_ABIS }
            } else {
                ARMV7.takeIf { it in Build.SUPPORTED_32_BIT_ABIS }
            }
        }

        val enabled: Boolean
            get() = !BuildConfig.IS_PM_BUILD && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && processAbi != null

        fun parse(payload: String, signature: String): AnalyzerUpdate? {
            if (!enabled || payload.length > 360 || !SIGNATURE.matches(signature)) return null
            val fields = payload.split('|')
            if (fields.size != 12 || fields[0] != "extera-analyzer" || fields[1] != "3") return null
            val release = fields[2].toLongOrNull()?.takeIf { it > 0 && it.toString() == fields[2] } ?: return null
            val channel = fields[3]
            val version = fields[4].takeIf { it.length <= 32 && VERSION.matches(it) } ?: return null
            val minApp = fields[5].toIntOrNull()?.takeIf { it > 0 && it.toString() == fields[5] } ?: return null
            val maxApp = fields[6].toIntOrNull()?.takeIf { it >= minApp && it.toString() == fields[6] } ?: return null
            val hashes = mapOf(ARM64 to fields[9], ARMV7 to fields[10])
            if (channel != "stable" && channel != "beta" || fields[7] != "1" || fields[8] != REPORT_SCHEMA.toString() ||
                hashes.values.any { !HASH.matches(it) } || !HASH.matches(fields[11])
            ) return null
            if (!SignedArtifact.verify(PUBLIC_KEY, payload, signature)) return null
            return AnalyzerUpdate(payload, signature, release, channel, version, minApp, maxApp, hashes, fields[11])
        }

        fun directory(context: Context): File = File(context.noBackupFilesDir, "plugin_analyzer/updates")
    }
}

internal class AnalyzerUpdateStore(private val context: Context) {
    private val stateFile = AtomicFile(File(AnalyzerUpdate.directory(context), "state.json"))
    private val ledger = AnalyzerUpdateLedger.open()

    private val bundledRelease = try {
        context.assets.open("plugin_analyzer/release").use { it.readBounded(64).decodeToString().trim().toLongOrNull() } ?: 0
    } catch (_: IOException) {
        0
    }

    private var state = readState()

    fun canStage(update: AnalyzerUpdate): Boolean {
        val trust = ledger ?: return false
        val floor = trust.floor
        return update.isCompatible() && update.release > bundledRelease &&
            (floor == null || update.release > floor.release || update.id == floor.id) &&
            listOfNotNull(state.pending, state.active, state.previous).none { it.release >= update.release } &&
            update.id !in state.rejected
    }

    fun candidates(): List<AnalyzerUpdate> = listOfNotNull(state.pending, state.active)
        .filter { it.release > bundledRelease && it.isCompatible() }

    fun retainedIds(): Set<String> = listOfNotNull(state.pending?.id, state.active?.id, state.previous?.id).toSet()

    fun isPending(update: AnalyzerUpdate?): Boolean = update != null && state.pending?.id == update.id

    fun stage(update: AnalyzerUpdate, source: File): Boolean {
        if (!canStage(update)) return false
        val staged = SignedArtifact.stage(source, update.archive(context), AnalyzerUpdate.MAX_ARCHIVE_BYTES) {
            update.verifyArchive(it)
            true
        }
        if (!staged) return false
        writeState(state.copy(pending = update))
        return true
    }

    fun accept(update: AnalyzerUpdate) {
        if (!isPending(update)) return
        val trust = requireNotNull(ledger)
        trust.advance(update)
        writeState(state.copy(floor = update.id, pending = null, active = update, previous = state.active ?: state.previous))
        trust.prune()
    }

    fun reject(update: AnalyzerUpdate) = remove(update, state.rejected.plus(update.id).distinct().takeLast(16))

    fun invalidate(update: AnalyzerUpdate) = remove(update, state.rejected)

    fun recordLoadFailure(update: AnalyzerUpdate): Boolean {
        if (update.id !in retainedIds()) return false
        val failures = (state.loadFailures[update.id] ?: 0) + 1
        if (failures >= MAX_LOAD_FAILURES) {
            reject(update)
            return true
        }
        writeState(state.copy(loadFailures = state.loadFailures + (update.id to failures)))
        return false
    }

    fun clearLoadFailures(update: AnalyzerUpdate) {
        if (update.id in state.loadFailures) {
            writeState(state.copy(loadFailures = state.loadFailures - update.id))
        }
    }

    private fun remove(update: AnalyzerUpdate, rejected: List<String>) {
        val active = state.active?.id == update.id
        writeState(state.copy(
            pending = state.pending?.takeUnless { it.id == update.id },
            active = if (active) state.previous else state.active,
            previous = state.previous?.takeUnless { active || it.id == update.id },
            rejected = rejected,
        ))
    }

    private fun readState(): State {
        val trust = ledger ?: return State()
        val floor = trust.floor?.id
        val empty = State(floor = floor)
        return try {
            val sealed = stateFile.openRead().use { it.readBounded(8192).decodeToString() }
            val separator = sealed.indexOf('\n')
            if (separator < 0) return empty
            val json = sealed.substring(separator + 1)
            val actual = trust.seal(json)
            if (!MessageDigest.isEqual(actual.toByteArray(), sealed.substring(0, separator).toByteArray())) return empty
            val data = JSONObject(json)
            val savedFloor = data.optString("floor").ifEmpty { null }
            val pending = data.update("approved_pending")
            val active = data.update("active")
            val previous = data.update("previous")
            val rejected = data.optJSONArray("rejected")?.let { array ->
                List(array.length().coerceAtMost(16)) { array.getString(it) }
            }.orEmpty()
            val loadFailures = data.optJSONObject("load_failures")?.let { failures ->
                failures.keys().asSequence().associateWith { failures.optInt(it) }.filterValues { it > 0 }
            }.orEmpty()
            if (savedFloor == floor) {
                State(floor, pending, active, previous, rejected, loadFailures)
            } else if (floor != null && pending?.id == floor) {
                State(floor, null, pending, active ?: previous, rejected, loadFailures)
            } else {
                empty
            }
        } catch (_: FileNotFoundException) {
            empty
        } catch (e: Exception) {
            FileLog.e(e)
            empty
        }
    }

    private fun writeState(requested: State) {
        val previous = state
        val keep = listOfNotNull(requested.active?.id, requested.pending?.id, requested.previous?.id)
        val next = requested.copy(loadFailures = requested.loadFailures.filterKeys { it in keep })
        val json = JSONObject().put("floor", next.floor.orEmpty()).put("rejected", JSONArray(next.rejected))
        if (next.loadFailures.isNotEmpty()) json.put("load_failures", JSONObject(next.loadFailures))
        next.active?.let { json.put("active", it.toJson()) }
        next.pending?.let { json.put("approved_pending", it.toJson()) }
        next.previous?.let { json.put("previous", it.toJson()) }
        val body = json.toString()
        val bytes = ("${requireNotNull(ledger).seal(body)}\n$body").toByteArray(Charsets.UTF_8)
        val stream = stateFile.startWrite()
        try {
            stream.write(bytes)
            stream.fd.sync()
        } catch (e: Exception) {
            stateFile.failWrite(stream)
            throw e
        }
        stateFile.finishWrite(stream)
        if (!stateFile.openRead().use { it.readBounded(8192) }.contentEquals(bytes)) {
            throw IOException("Could not commit analyzer update state")
        }
        state = next
        listOfNotNull(previous.active, previous.pending, previous.previous)
            .filter { it.id !in keep }.forEach { it.archive(context).delete() }
    }

    private fun JSONObject.update(name: String): AnalyzerUpdate? =
        optJSONObject(name)?.let { AnalyzerUpdate.parse(it.getString("payload"), it.getString("signature")) }

    private fun AnalyzerUpdate.toJson(): JSONObject = JSONObject().put("payload", payload).put("signature", signature)

    private data class State(
        val floor: String? = null,
        val pending: AnalyzerUpdate? = null,
        val active: AnalyzerUpdate? = null,
        val previous: AnalyzerUpdate? = null,
        val rejected: List<String> = emptyList(),
        val loadFailures: Map<String, Int> = emptyMap(),
    )

    private companion object {
        const val MAX_LOAD_FAILURES = 3
    }
}

private fun InputStream.readBounded(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        if (output.size() > limit - read) throw IOException("Analyzer entry exceeds its size limit")
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
