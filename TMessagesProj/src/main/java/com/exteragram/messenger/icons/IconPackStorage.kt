/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.icons

import com.exteragram.messenger.export.output.ExportFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.simplifiles.SimpliFiles
import org.simplifiles.archive.ArchiveSaveOptions
import org.simplifiles.archive.security.SecurityPolicy
import org.simplifiles.exception.ArchiveValidationException
import org.simplifiles.exception.CorruptedArchiveException
import org.simplifiles.exception.UnsupportedArchiveFormatException
import org.simplifiles.files.OverwritePolicy
import org.simplifiles.files.SimpliDirectory
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import java.io.File

enum class IconPackStorageError {
    INVALID_ARCHIVE,
    MISSING_METADATA,
    METADATA_TOO_LARGE,
    INVALID_METADATA,
    TOO_MANY_FILES,
    ARCHIVE_TOO_LARGE,
    FILE_TOO_LARGE,
    COMPRESSION_RATIO_TOO_HIGH,
    STORAGE_ERROR,
    UNKNOWN
}

sealed class IconPackStorageResult<out T> {
    data class Success<out T>(val value: T) : IconPackStorageResult<T>()
    data class Failure(val error: IconPackStorageError) : IconPackStorageResult<Nothing>()
}

object IconPackStorage {

    private const val MAX_METADATA_BYTES = 256L * 1024L

    private var cachedCustomPacks: Map<String, IconPack>? = null
    private val cachedPacksById = HashMap<String, IconPack?>()
    @Volatile
    private var iconPacksDirectory: File? = null
    private val resourceNameRegex = Regex("[A-Za-z0-9_]+")
    private val iconPackArchivePolicy = SecurityPolicy.builder()
        .maxEntries(3_000)
        .maxTotalUncompressedSize(100L * 1024L * 1024L)
        .maxSingleFileSize(10L * 1024L * 1024L)
        .maxCompressionRatio(250.0)
        .build()
    private val iconPackArchiveSaveOptions = ArchiveSaveOptions.builder()
        .compressionLevel(ArchiveSaveOptions.NO_COMPRESSION_LEVEL)
        .build()

    private class IconPackStorageException(val error: IconPackStorageError) : Exception(error.name)

    fun getIconPacksDirectory(): File {
        iconPacksDirectory?.let { return it }
        val filesDir = ApplicationLoader.applicationContext.filesDir
        val iconPacksDir = File(filesDir, "icon_packs")
        SimpliFiles.directory(iconPacksDir).create()
        iconPacksDirectory = iconPacksDir
        return iconPacksDir
    }

    private fun isPlainFileName(path: String): Boolean {
        if (path.isEmpty() || path == "." || path == "..") return false
        for (c in path) {
            if (c == '/' || c == '\\' || c == '\u0000') return false
        }
        return true
    }

    private fun isValidPackId(packId: String): Boolean {
        return packId.isNotBlank() &&
                !packId.contains('/') &&
                !packId.contains('\\') &&
                packId != "." &&
                packId != ".."
    }

    private fun parseMetadata(jsonObject: JSONObject, packRoot: File? = null, location: File? = null): IconPack {
        val packName = jsonObject.getString("packName")
        val packId = jsonObject.getString("packId")
        if (!isValidPackId(packId)) {
            throw SecurityException("Invalid icon pack id: $packId")
        }
        val author = jsonObject.optString("author", "Unknown")
        val version = jsonObject.optString("version", "1.0")
        val iconsJson = jsonObject.optJSONObject("icons")
        val iconsMap = mutableMapOf<String, String>()

        val canonicalPackRoot = packRoot?.canonicalFile
        iconsJson?.keys()?.forEach { iconName ->
            val iconPath = iconsJson.getString(iconName)
            val normalizedPath = if (packRoot != null && canonicalPackRoot != null) {
                SimpliFiles.directory(packRoot).resolveInside(iconPath)
                if (isPlainFileName(iconPath)) {
                    iconPath
                } else {
                    File(packRoot, iconPath).canonicalFile
                        .relativeTo(canonicalPackRoot)
                        .invariantSeparatorsPath
                }
            } else {
                iconPath
            }
            iconsMap[iconName] = normalizedPath
        }

        return IconPack(packId, packName, author, version, iconsMap, location = location)
    }

    private fun parseMetadataFile(file: File): IconPack? {
        return try {
            parseMetadata(parseMetadataJson(SimpliFiles.file(file).readText(MAX_METADATA_BYTES)), file.parentFile)
        } catch (e: Exception) {
            FileLog.e("Error parsing metadata.json", e)
            null
        }
    }

    private fun extractPackArchive(file: File, targetDir: File): JSONObject {
        val extracted = SimpliFiles.archive(file)
            .withPolicy(iconPackArchivePolicy)
            .extractTo(targetDir)

        val metadataFile = extracted.file("metadata.json")
        if (!metadataFile.exists()) {
            FileLog.e("Icon pack archive does not contain metadata.json")
            throw IconPackStorageException(IconPackStorageError.MISSING_METADATA)
        }
        if (metadataFile.size > MAX_METADATA_BYTES) {
            FileLog.e("Icon pack metadata.json is too large")
            throw IconPackStorageException(IconPackStorageError.METADATA_TOO_LARGE)
        }
        return parseMetadataJson(metadataFile.readText())
    }

    private fun parseMetadataJson(text: String): JSONObject {
        try {
            return JSONObject(text)
        } catch (e: StackOverflowError) {
            throw org.json.JSONException("metadata.json is nested too deeply")
        }
    }

    private fun errorFromValidationException(e: ArchiveValidationException): IconPackStorageError {
        return when (e.report.issues.firstOrNull()?.code) {
            "archive.entries.too_many" -> IconPackStorageError.TOO_MANY_FILES
            "archive.total_size.too_large" -> IconPackStorageError.ARCHIVE_TOO_LARGE
            "archive.entry.size.too_large" -> IconPackStorageError.FILE_TOO_LARGE
            "archive.entry.compression_ratio.too_high" -> IconPackStorageError.COMPRESSION_RATIO_TOO_HIGH
            else -> IconPackStorageError.INVALID_ARCHIVE
        }
    }

    private fun errorFromException(e: Exception): IconPackStorageError {
        return when (e) {
            is IconPackStorageException -> e.error
            is ArchiveValidationException -> errorFromValidationException(e)
            is CorruptedArchiveException, is UnsupportedArchiveFormatException -> IconPackStorageError.INVALID_ARCHIVE
            is SecurityException -> IconPackStorageError.INVALID_METADATA
            is org.json.JSONException -> IconPackStorageError.INVALID_METADATA
            else -> IconPackStorageError.UNKNOWN
        }
    }

    private fun createTempCacheDirectory(prefix: String): SimpliDirectory {
        val tempDir = File(
            ApplicationLoader.applicationContext.cacheDir,
            "${prefix}_${System.currentTimeMillis()}"
        )
        return SimpliFiles.directory(tempDir).create()
    }

    private fun deleteDirectoryIfExists(directory: SimpliDirectory) {
        if (directory.exists()) {
            directory.deleteRecursively()
        }
    }

    private inline fun <T> withTempCacheDirectory(prefix: String, block: (SimpliDirectory) -> T): T {
        val temp = createTempCacheDirectory(prefix)
        return try {
            block(temp)
        } finally {
            deleteDirectoryIfExists(temp)
        }
    }

    @Synchronized
    fun findPackById(packId: String): IconPack? {
        if (!isValidPackId(packId)) return null
        cachedCustomPacks?.get(packId)?.let { return it }
        if (cachedPacksById.containsKey(packId)) return cachedPacksById[packId]

        var pack: IconPack? = null
        val packDir = File(getIconPacksDirectory(), packId)
        val packDirectory = SimpliFiles.directory(packDir)
        if (packDirectory.exists()) {
            val metadataFile = packDirectory.file("metadata.json")
            if (metadataFile.exists()) {
                pack = parseMetadataFile(metadataFile.file)
            }
        }
        cachedPacksById[packId] = pack
        return pack
    }

    private fun invalidatePack(packId: String) {
        cachedCustomPacks = null
        cachedPacksById.remove(packId)
    }

    private fun invalidateAllPacks() {
        cachedCustomPacks = null
        cachedPacksById.clear()
    }

    fun resolveIconFile(packId: String, resourceName: String): File? {
        if (!isValidPackId(packId) || !resourceName.matches(resourceNameRegex)) {
            return null
        }
        return try {
            val pack = findPackById(packId) ?: return null
            val iconPath = pack.icons[resourceName] ?: return null
            val packRoot = File(getIconPacksDirectory(), packId).canonicalFile
            val iconFile = File(packRoot, iconPath).canonicalFile
            val packRootPath = packRoot.path + File.separator
            if (!iconFile.path.startsWith(packRootPath) || !iconFile.isFile) {
                return null
            }
            iconFile
        } catch (e: Exception) {
            FileLog.e("Failed to resolve icon file for pack $packId", e)
            return null
        }
    }

    suspend fun bundlePack(packId: String): File? = withContext(Dispatchers.IO) {
        if (!isValidPackId(packId)) return@withContext null
        val pack = findPackById(packId) ?: return@withContext null
        val packDir = File(getIconPacksDirectory(), packId)
        val packDirectory = SimpliFiles.directory(packDir)
        if (!packDirectory.exists()) return@withContext null

        val sharedDir = File(ApplicationLoader.applicationContext.cacheDir, "shared_packs")
        val sharedDirectory = SimpliFiles.directory(sharedDir)
        try {
            if (sharedDirectory.exists()) {
                sharedDirectory.deleteRecursively()
            }
            sharedDirectory.create()

            val sanitizedName = ExportFiles.fileNameFromUserString(pack.name)
            val outputFile = sharedDirectory.file("$sanitizedName.icons").file

            packDirectory.zipTo(outputFile, iconPackArchiveSaveOptions).file
        } catch (e: Exception) {
            FileLog.e("Failed to bundle pack: $packId", e)
            null
        }
    }

    fun bundlePackBlocking(packId: String): File? = kotlinx.coroutines.runBlocking {
        bundlePack(packId)
    }

    @Synchronized
    fun getCustomPacks(): List<IconPack> {
        cachedCustomPacks?.values?.let { return ArrayList(it) }

        val packs = mutableListOf<IconPack>()
        getIconPacksDirectory().listFiles()?.forEach { packDir ->
            val packDirectory = SimpliFiles.directory(packDir)
            val metadataFile = packDirectory.file("metadata.json")
            if (metadataFile.exists()) {
                parseMetadataFile(metadataFile.file)?.let { packs.add(it) }
            }
        }
        cachedCustomPacks = packs.associateBy { it.id }
        cachedPacksById.clear()
        return packs
    }

    @Synchronized
    fun saveIconPackMetadata(iconPack: IconPack): Boolean {
        if (!isValidPackId(iconPack.id)) {
            FileLog.e("Invalid icon pack id: ${iconPack.id}")
            return false
        }
        try {
            val packDir = SimpliFiles.directory(File(getIconPacksDirectory(), iconPack.id)).create()
            val metadataFile = packDir.file("metadata.json")
            val jsonObject = JSONObject()
            jsonObject.put("schemaVersion", 1)
            jsonObject.put("packName", iconPack.name)
            jsonObject.put("packId", iconPack.id)
            jsonObject.put("author", iconPack.author)
            jsonObject.put("version", iconPack.version)
            val iconsJson = JSONObject()
            for ((key, value) in iconPack.icons) {
                iconsJson.put(key, value)
            }
            jsonObject.put("icons", iconsJson)
            metadataFile.writeTextAtomic(jsonObject.toString(4))
            invalidatePack(iconPack.id)
            return true
        } catch (e: Exception) {
            FileLog.e("Error saving metadata", e)
            return false
        }
    }

    @Synchronized
    fun deletePack(packId: String) {
        if (!isValidPackId(packId)) return
        val packDir = File(getIconPacksDirectory(), packId)
        val packDirectory = SimpliFiles.directory(packDir)
        if (packDirectory.exists()) {
            deleteDirectoryIfExists(packDirectory)
            invalidatePack(packId)
        }
    }

    suspend fun installPack(file: File): IconPackStorageResult<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                withTempCacheDirectory("install") { temp ->
                    val metadata = extractPackArchive(file, temp.file)

                    val parsedPack = parseMetadata(metadata, temp.file)
                    val packId = parsedPack.id

                    val packDir = File(getIconPacksDirectory(), packId)
                    val trashDir = File(getIconPacksDirectory(), "${packId}_trash_${System.currentTimeMillis()}")
                    val packDirectory = SimpliFiles.directory(packDir)
                    val trashDirectory = SimpliFiles.directory(trashDir)

                    if (packDirectory.exists()) {
                        runCatching {
                            packDirectory.moveTo(trashDir, OverwritePolicy.ERROR)
                        }.onFailure {
                            if (!packDirectory.deleteRecursively()) {
                                return@withTempCacheDirectory IconPackStorageResult.Failure(IconPackStorageError.STORAGE_ERROR)
                            }
                        }
                    }

                    runCatching {
                        temp.moveTo(packDir, OverwritePolicy.ERROR)
                    }.onFailure {
                        runCatching {
                            temp.copyTo(packDir, OverwritePolicy.ERROR)
                        }.onFailure {
                            if (trashDirectory.exists()) {
                                if (packDirectory.exists()) packDirectory.deleteRecursively()
                                trashDirectory.moveTo(packDir, OverwritePolicy.ERROR)
                            }
                            return@withTempCacheDirectory IconPackStorageResult.Failure(IconPackStorageError.STORAGE_ERROR)
                        }
                    }

                    if (trashDirectory.exists()) {
                        trashDirectory.deleteRecursively()
                    }

                    synchronized(this@IconPackStorage) {
                        invalidateAllPacks()
                    }
                    IconPackStorageResult.Success(Unit)
                }
            } catch (e: IconPackStorageException) {
                FileLog.e("Pack installation failed: ${e.error}")
                IconPackStorageResult.Failure(e.error)
            } catch (e: Exception) {
                FileLog.e("Pack installation failed", e)
                IconPackStorageResult.Failure(errorFromException(e))
            }
        }
    }

    suspend fun parsePackFromZip(file: File): IconPackStorageResult<IconPack> = withContext(Dispatchers.IO) {
        val temp = createTempCacheDirectory("preview")
        try {
            val metadata = extractPackArchive(file, temp.file)
            val pack = parseMetadata(metadata, temp.file, temp.file)
            IconPackStorageResult.Success(pack)
        } catch (e: IconPackStorageException) {
            FileLog.e("Failed to parse pack for preview: ${e.error}")
            deleteDirectoryIfExists(temp)
            IconPackStorageResult.Failure(e.error)
        } catch (e: Exception) {
            FileLog.e("Failed to parse pack for preview", e)
            deleteDirectoryIfExists(temp)
            IconPackStorageResult.Failure(errorFromException(e))
        }
    }
}
