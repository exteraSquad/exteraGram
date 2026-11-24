/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.pip

import com.exteragram.messenger.plugins.PluginsController
import com.exteragram.messenger.utils.network.ExteraHttpClient
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import okhttp3.Request
import org.simplifiles.SimpliFiles
import org.simplifiles.archive.ArchiveExtractionOptions
import org.simplifiles.archive.CancellationToken
import org.simplifiles.archive.ExtractionTargetPolicy
import org.simplifiles.archive.security.SecurityPolicy
import org.simplifiles.files.OverwritePolicy
import org.telegram.messenger.FileLog
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

private typealias VersionMap = ConcurrentHashMap<String, MutableSet<String>>
private typealias RegistryMap = ConcurrentHashMap<String, VersionMap>

object PipController {

    interface InstallerDelegate {
        fun onProgress(text: String)
        fun isCancelled(): Boolean
    }

    private val client = ExteraHttpClient.client
    private val gson = Gson()

    private val libsDir: File
        get() = SimpliFiles.directory(
            File(PluginsController.getInstance().pluginsDir, "libs")
        ).create().file

    private val registryFile: File
        get() = File(libsDir, "registry.json")

    private val registry: RegistryMap = ConcurrentHashMap()
    private val installLocks = ConcurrentHashMap<String, Any>()
    private val wheelArchivePolicy = SecurityPolicy.builder()
        .maxEntries(50_000)
        .maxTotalUncompressedSize(500L * 1024L * 1024L)
        .maxSingleFileSize(250L * 1024L * 1024L)
        .maxCompressionRatio(500.0)
        .build()

    var pythonVersion: String = "3.11.17"

    private const val MAX_REGISTRY_BYTES = 4L * 1024L * 1024L
    private const val MAX_METADATA_BYTES = 4L * 1024L * 1024L
    private const val MAX_WHEEL_BYTES = 250L * 1024L * 1024L

    private const val ENV_SYS_PLATFORM = "linux"
    private const val ENV_PLATFORM_SYSTEM = "Linux"
    private const val ENV_OS_NAME = "posix"

    private val REGEX_NORMALIZE = Regex("[-_.]+")
    private val REGEX_REQ_PARSE = Regex("^([a-zA-Z0-9_.-]+)(.*)")
    private val REGEX_REQ_SPECS = Regex("^(===|==|>=|<=|>|<|~=|!=)\\s*(.*)")
    private val REGEX_REQ_EXTRA = Regex("\\[.*?]")
    private val REGEX_REQ_PAREN = Regex("[()]")
    private val REGEX_VERSION_SPLIT = Regex("([0-9]+)|([a-zA-Z]+)")
    private val REGEX_MARKER_TOKEN =
        Regex("""("[^"]*"|'[^']*'|===|==|!=|>=|<=|~=|>|<|\(|\)|\b(?:and|or|in|not)\b|[A-Za-z_][A-Za-z0-9_]*|\S+)""")
    private val REGEX_VERSION_WILDCARD = Regex("""(?i)(?:^|[.\-_])\*$""")

    private data class WheelCandidate(
        val version: String,
        val downloadUrl: String,
        val expectedSha256: String?
    )

    private data class ParsedVersion(
        val epoch: Int,
        val publicVersion: String,
        val parts: List<String>
    )

    init {
        loadRegistry()
    }

    private fun normalizePackageName(name: String): String {
        return name.lowercase().replace(REGEX_NORMALIZE, "-")
    }

    @Synchronized
    private fun loadRegistry() {
        if (!registryFile.exists()) {
            registry.clear()
            return
        }

        try {
            val type = object : TypeToken<Map<String, Map<String, Set<String>>>>() {}.type
            val data: Map<String, Map<String, Set<String>>> =
                gson.fromJson(SimpliFiles.file(registryFile).readText(MAX_REGISTRY_BYTES), type)

            registry.clear()
            data.forEach { (pkg, versions) ->
                val normalizedPkg = normalizePackageName(pkg)
                val versionMap = VersionMap()
                versions.forEach { (ver, plugins) ->
                    versionMap[ver] = ConcurrentHashMap.newKeySet<String>().apply {
                        addAll(plugins)
                    }
                }
                registry[normalizedPkg] = versionMap
            }
        } catch (e: Exception) {
            FileLog.e("PipController: Failed to load registry", e)
            registry.clear()
            runCatching {
                SimpliFiles.file(registryFile).moveTo(
                    File(libsDir, "registry.json.bak"),
                    OverwritePolicy.REPLACE
                )
            }.onFailure {
                FileLog.e("PipController: Failed to backup invalid registry", it)
            }
        }
    }

    @Synchronized
    private fun snapshotRegistry(): Map<String, Map<String, Set<String>>> {
        return registry.mapValues { (_, versions) ->
            versions.mapValues { (_, plugins) -> plugins.toSet() }
        }
    }

    @Synchronized
    private fun restoreRegistry(snapshot: Map<String, Map<String, Set<String>>>) {
        registry.clear()
        snapshot.forEach { (pkg, versions) ->
            val versionMap = VersionMap()
            versions.forEach { (version, plugins) ->
                versionMap[version] = ConcurrentHashMap.newKeySet<String>().apply {
                    addAll(plugins)
                }
            }
            registry[pkg] = versionMap
        }
    }

    @Synchronized
    private fun saveRegistry() {
        try {
            SimpliFiles.file(registryFile).writeTextAtomic(gson.toJson(snapshotRegistry()))
        } catch (e: Exception) {
            FileLog.e("PipController: Failed to save registry", e)
        }
    }

    fun cleanup() {
        cleanupInternal()
    }

    private val PREINSTALLED_PACKAGES = setOf(
        "beautifulsoup4", "debugpy", "lxml", "packaging", "pillow", "requests", "pyyaml"
    ).map { normalizePackageName(it) }.toSet()

    fun installDependencies(
        requirements: List<String>,
        pluginId: String,
        delegate: InstallerDelegate? = null
    ): List<String> {
        val paths = mutableListOf<String>()
        val installed = mutableSetOf<Pair<String, String>>()
        val registrySnapshot = snapshotRegistry()

        try {
            for (req in requirements) {
                if (delegate?.isCancelled() == true) throw IOException("Installation cancelled")
                if (req.isBlank()) continue
                val (pkg, specs) = parseRequirement(req)
                val normalizedPkg = normalizePackageName(pkg)

                if (PREINSTALLED_PACKAGES.contains(normalizedPkg)) {
                    continue
                }

                resolveAndInstall(normalizedPkg, specs, installed, pluginId, delegate)
            }

            updateRegistryForPlugin(pluginId, installed)

            for ((pkg, version) in installed) {
                paths.add(getLibPath(pkg, version).absolutePath)
            }

            saveRegistry()
        } catch (e: Exception) {
            FileLog.e("PipController: Failed to install dependencies for $pluginId: $requirements", e)
            restoreRegistry(registrySnapshot)
            removeOrphanedDirectories()
            throw e
        } finally {
            installLocks.clear()
        }

        return paths
    }

    private fun resolveAndInstall(
        pkg: String,
        specs: List<Pair<String, String>>,
        installedAccumulator: MutableSet<Pair<String, String>>,
        pluginId: String,
        delegate: InstallerDelegate?
    ) {
        if (delegate?.isCancelled() == true) throw IOException("Installation cancelled")

        val lock = installLocks.computeIfAbsent(pkg) { Any() }

        val versionToUse = synchronized(lock) {
            if (delegate?.isCancelled() == true) throw IOException("Installation cancelled")

            val activeVersionEntry = registry[pkg]?.entries?.firstOrNull { it.value.isNotEmpty() }
            if (activeVersionEntry != null) {
                val activeVersion = activeVersionEntry.key
                val users = activeVersionEntry.value.joinToString(", ")

                if (checkVersionSatisfies(activeVersion, specs)) {
                    FileLog.d("PipController: Reusing active version $activeVersion of $pkg (used by $users) for $pluginId")
                    return@synchronized activeVersion
                } else {
                    val specDisplay =
                        if (specs.isNotEmpty()) specs.joinToString(",") { "${it.first}${it.second}" } else "(latest)"
                    throw IOException("Dependency conflict for '$pkg': Active version $activeVersion (used by $users) does not satisfy requirement '$specDisplay'.")
                }
            }

            var ver = findInstalledVersion(pkg, specs)

            if (ver == null) {
                ver = findVersionOnDisk(pkg, specs)

                if (ver != null) {
                    FileLog.d("PipController: Found $pkg $ver on disk, adopting.")
                } else {
                    ver = installPackage(pkg, specs, delegate)
                }
            }
            ver
        }

        val existingEntry = installedAccumulator.find { it.first == pkg }
        if (existingEntry != null) {
            val existingVersion = existingEntry.second
            if (VersionComparator.compare(versionToUse, existingVersion) > 0) {
                FileLog.d("PipController: Upgrading $pkg from $existingVersion to $versionToUse for $pluginId")
                installedAccumulator.remove(existingEntry)
            } else {
                return
            }
        }

        registry.computeIfAbsent(pkg) { VersionMap() }
            .computeIfAbsent(versionToUse) { Collections.newSetFromMap(ConcurrentHashMap()) }
            .add(pluginId)

        installedAccumulator.add(pkg to versionToUse)

        val metadataFile = findMetadataFile(pkg, versionToUse)
        if (metadataFile != null && metadataFile.exists()) {
            val dependencies = parseDependenciesFromMetadata(metadataFile)
            for (depReq in dependencies) {
                if (delegate?.isCancelled() == true) throw IOException("Installation cancelled")
                val (depPkg, depSpecs) = parseRequirement(depReq)
                resolveAndInstall(
                    normalizePackageName(depPkg),
                    depSpecs,
                    installedAccumulator,
                    pluginId,
                    delegate
                )
            }
        }
    }

    private fun updateRegistryForPlugin(
        pluginId: String,
        currentlyNeeded: Set<Pair<String, String>>
    ) {
        val toRemove = mutableListOf<Pair<String, String>>()
        registry.forEach { (pkg, versions) ->
            versions.forEach { (version, plugins) ->
                val key = pkg to version
                if (plugins.contains(pluginId) && !currentlyNeeded.contains(key)) {
                    plugins.remove(pluginId)
                    if (plugins.isEmpty()) {
                        toRemove.add(key)
                    }
                }
            }
        }

        toRemove.forEach { (pkg, version) ->
            registry[pkg]?.remove(version)
            if (registry[pkg]?.isEmpty() == true) {
                registry.remove(pkg)
            }
            deletePackage(pkg, version)
        }
    }

    private fun cleanupInternal() {
        val toRemove = mutableListOf<Pair<String, String>>()

        registry.forEach { (pkg, versions) ->
            versions.forEach { (version, plugins) ->
                if (plugins.isEmpty()) {
                    toRemove.add(pkg to version)
                }
            }
        }

        toRemove.forEach { (pkg, version) ->
            registry[pkg]?.remove(version)
            if (registry[pkg]?.isEmpty() == true) {
                registry.remove(pkg)
            }
            deletePackage(pkg, version)
        }

        removeOrphanedDirectories()
    }

    private fun removeOrphanedDirectories() {
        val expectedDirs = mutableSetOf<String>()
        registry.forEach { (pkg, versions) ->
            versions.keys.forEach { version ->
                try {
                    expectedDirs.add(getLibPath(pkg, version).canonicalPath)
                } catch (_: IOException) {
                }
            }
        }

        libsDir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                try {
                    if (!expectedDirs.contains(file.canonicalPath)) {
                        FileLog.d("PipController: Removing orphaned library: ${file.name}")
                        SimpliFiles.directory(file).deleteRecursively()
                    }
                } catch (e: Exception) {
                    FileLog.e(e)
                }
            }
        }
    }

    fun activeLibraryPaths(): Set<String> {
        val paths = mutableSetOf<String>()
        registry.forEach { (pkg, versions) ->
            versions.forEach { (version, plugins) ->
                if (plugins.isNotEmpty()) {
                    try {
                        paths.add(getLibPath(pkg, version).absolutePath)
                    } catch (e: IOException) {
                        FileLog.e("PipController: Failed to resolve path for $pkg $version", e)
                    }
                }
            }
        }
        return paths
    }

    fun uninstallDependencies(pluginId: String) {
        var changed = false
        val toRemove = mutableListOf<Pair<String, String>>()

        registry.forEach { (pkg, versions) ->
            versions.forEach { (version, plugins) ->
                if (plugins.remove(pluginId)) {
                    changed = true
                    if (plugins.isEmpty()) {
                        toRemove.add(pkg to version)
                    }
                }
            }
        }

        toRemove.forEach { (pkg, version) ->
            registry[pkg]?.remove(version)
            if (registry[pkg]?.isEmpty() == true) {
                registry.remove(pkg)
            }
            deletePackage(pkg, version)
        }

        if (changed) {
            saveRegistry()
        }
    }

    private fun installPackage(
        pkg: String,
        specs: List<Pair<String, String>>,
        delegate: InstallerDelegate?
    ): String {
        val specDisplay =
            if (specs.isNotEmpty()) specs.joinToString(",") { "${it.first}${it.second}" } else "(latest)"

        FileLog.d("PipController: Installing $pkg $specDisplay")
        delegate?.onProgress("Resolving $pkg...")

        val url = "https://pypi.org/pypi/$pkg/json"
        val request = Request.Builder().url(url).build()

        var candidate: WheelCandidate? = null

        executeWithRetry(request, delegate).use { response ->
            if (!response.isSuccessful) {
                if (response.code == 404) throw IOException("Package $pkg not found on PyPI")
                throw IOException("Failed to fetch metadata for $pkg: ${response.code}")
            }

            val bodyString = response.body.string()
            val json = gson.fromJson(bodyString, JsonObject::class.java)
                ?: throw IOException("Invalid metadata response from PyPI for $pkg")

            val releases = json.getAsJsonObject("releases")
                ?: throw IOException("No releases found for $pkg on PyPI")

            val availableVersions = releases.keySet().toList()
            val validVersions = filterPreReleases(
                availableVersions.filter { checkVersionSatisfies(it, specs) },
                specs
            ).sortedWith(Comparator { a, b -> VersionComparator.compare(b, a) })

            val allowYanked = specs.any { it.first == "==" || it.first == "===" }

            for (version in validVersions) {
                val artifacts = releases.getAsJsonArray(version) ?: continue

                for (elem in artifacts) {
                    val artifact = elem.asJsonObject
                    val wheelCandidate = selectWheelCandidate(version, artifact, allowYanked) ?: continue
                    candidate = wheelCandidate
                    break
                }

                if (candidate != null) {
                    break
                }
            }

            if (candidate == null) {
                val info = json.getAsJsonObject("info")
                val projectRequiresPython = info?.getStringOrNull("requires_python")
                if (projectRequiresPython != null) {
                    val pythonSpecs = parseSpecs(projectRequiresPython)
                    if (!checkVersionSatisfies(pythonVersion, pythonSpecs)) {
                        throw IOException(
                            "Package $pkg requires Python $projectRequiresPython, but current is $pythonVersion"
                        )
                    }
                }
            }
        }

        val selectedCandidate = candidate
            ?: throw IOException("No pure-Python wheel found for $pkg. Binary packages are not supported.")

        val targetDir = getLibPath(pkg, selectedCandidate.version)
        val tempDownloadDir = SimpliFiles.directory(
            File(libsDir, "tmp_${pkg}_${System.currentTimeMillis()}")
        ).create().file
        val tempExtractDir = File(tempDownloadDir, "extracted")

        try {
            delegate?.onProgress("Downloading $pkg ${selectedCandidate.version}...")

            val wheelFile = File(tempDownloadDir, "package.whl")

            val dlRequest = Request.Builder().url(selectedCandidate.downloadUrl).build()
            executeWithRetry(dlRequest, delegate).use { dlResponse ->
                if (!dlResponse.isSuccessful) throw IOException("Download failed: ${dlResponse.code}")

                val declaredLength = dlResponse.body.contentLength()
                if (declaredLength > MAX_WHEEL_BYTES) {
                    throw IOException("Wheel for $pkg is too large: $declaredLength bytes (limit $MAX_WHEEL_BYTES)")
                }

                SimpliFiles.file(wheelFile)
                    .writeFrom(SizeLimitedInputStream(dlResponse.body.byteStream(), MAX_WHEEL_BYTES))
            }

            if (selectedCandidate.expectedSha256 != null) {
                val calculatedSha256 = calculateSha256(wheelFile)
                if (!calculatedSha256.equals(selectedCandidate.expectedSha256, ignoreCase = true)) {
                    throw IOException("Checksum mismatch")
                }
            }

            delegate?.onProgress("Extracting $pkg ${selectedCandidate.version}...")
            val extractionOptions = ArchiveExtractionOptions.builder()
                .cancellationToken(CancellationToken { delegate?.isCancelled() == true })
                .targetPolicy(ExtractionTargetPolicy.REPLACE)
                .build()
            SimpliFiles.archive(wheelFile)
                .withPolicy(wheelArchivePolicy)
                .extractToDirectory(tempExtractDir, extractionOptions)
            if (targetDir.exists()) {
                SimpliFiles.directory(targetDir).deleteRecursively()
            }
            SimpliFiles.directory(tempExtractDir).moveTo(targetDir, OverwritePolicy.REPLACE)

        } catch (e: Exception) {
            FileLog.e("PipController: Failed to download/install $pkg", e)
            throw e
        } finally {
            runCatching {
                SimpliFiles.directory(tempDownloadDir).deleteRecursively()
            }.onFailure {
                FileLog.e("PipController: Failed to delete temp package directory", it)
            }
        }

        return selectedCandidate.version
    }

    private fun selectWheelCandidate(
        version: String,
        artifact: JsonObject,
        allowYanked: Boolean
    ): WheelCandidate? {
        val type = artifact.getStringOrNull("packagetype") ?: return null
        if (type != "bdist_wheel") return null

        if (!allowYanked && artifact.getBooleanOrNull("yanked") == true) return null

        val filename = artifact.getStringOrNull("filename") ?: return null
        if (!isPurePythonWheelCompatible(filename)) return null

        val requiresPython = artifact.getStringOrNull("requires_python")
        if (requiresPython != null && !checkVersionSatisfies(pythonVersion, parseSpecs(requiresPython))) {
            return null
        }

        val url = artifact.getStringOrNull("url") ?: return null
        val expectedSha256 = artifact.getAsJsonObject("digests")?.getStringOrNull("sha256")
        return WheelCandidate(version, url, expectedSha256)
    }

    private fun JsonObject.getStringOrNull(name: String): String? {
        if (!has(name)) return null
        val element = get(name)
        if (element == null || element.isJsonNull) return null
        return element.asString
    }

    private fun JsonObject.getBooleanOrNull(name: String): Boolean? {
        if (!has(name)) return null
        val element = get(name)
        if (element == null || element.isJsonNull) return null
        return runCatching { element.asBoolean }.getOrNull()
    }

    private fun isPurePythonWheelCompatible(filename: String): Boolean {
        if (!filename.endsWith(".whl")) return false

        val parts = filename.removeSuffix(".whl").split("-")
        if (parts.size < 5) return false

        val pythonTag = parts[parts.size - 3]
        val abiTag = parts[parts.size - 2]
        val platformTag = parts[parts.size - 1]

        if (abiTag != "none" || platformTag != "any") return false
        return pythonTag.split(".").any { isPythonTagCompatible(it) }
    }

    private fun isPythonTagCompatible(tag: String): Boolean {
        val versionParts = pythonVersion.split(".")
        val major = versionParts.getOrNull(0)?.toIntOrNull() ?: return false
        val minor = versionParts.getOrNull(1)?.toIntOrNull() ?: return false
        val lowerTag = tag.lowercase()

        if (lowerTag == "py$major") return true
        if (lowerTag == "cp$major$minor") return true

        val taggedMinor = lowerTag.removePrefix("py$major").toIntOrNull()
        return taggedMinor != null && taggedMinor <= minor
    }

    private fun executeWithRetry(request: Request, delegate: InstallerDelegate?): okhttp3.Response {
        var attempt = 0
        var lastException: IOException? = null
        while (attempt < 3) {
            if (delegate?.isCancelled() == true) throw IOException("Installation cancelled")
            try {
                return client.newCall(request).execute()
            } catch (e: IOException) {
                lastException = e
                attempt++
                FileLog.w("PipController: Network error, retrying ($attempt/3): ${e.message}")
                try {
                    Thread.sleep(1000L * attempt)
                } catch (_: InterruptedException) {
                }
            }
        }
        if (delegate?.isCancelled() == true) throw IOException("Installation cancelled")
        throw lastException ?: IOException("Unknown network error")
    }

    private fun findVersionOnDisk(pkg: String, specs: List<Pair<String, String>>): String? {
        val pkgPrefix = "${pkg}-"
        val versions = libsDir.listFiles()?.asSequence()
            ?.filter { it.isDirectory && it.name.startsWith(pkgPrefix) }
            ?.map { it.name.substring(pkgPrefix.length) }
            ?.filter { version ->
                version.isNotEmpty() && checkVersionSatisfies(version, specs)
            }
            ?.toList()
            ?: return null

        return filterPreReleases(versions, specs).maxWithOrNull(VersionComparator)
    }

    private fun findInstalledVersion(pkg: String, specs: List<Pair<String, String>>): String? {
        val versions = registry[pkg] ?: return null
        return filterPreReleases(
            versions.keys.filter { checkVersionSatisfies(it, specs) },
            specs
        )
            .maxWithOrNull(VersionComparator)
    }

    private fun getLibPath(pkg: String, version: String): File {
        if (version.contains("/") || version.contains("\\") || version.contains("..")) {
            throw IOException("Invalid version: $version")
        }
        return File(libsDir, "${pkg}-$version")
    }

    private fun deletePackage(pkg: String, version: String) {
        val libPath = getLibPath(pkg, version)
        if (libPath.exists()) {
            runCatching {
                SimpliFiles.directory(libPath).deleteRecursively()
                FileLog.d("PipController: Deleted package ${libPath.name}")
            }.onFailure {
                FileLog.e("PipController: Failed to delete package ${libPath.name}", it)
            }
        }
    }

    private fun parseRequirement(req: String): Pair<String, List<Pair<String, String>>> {
        var cleanReq = req.split(";")[0].trim()
        cleanReq = cleanReq.replace(REGEX_REQ_EXTRA, "").trim()
        cleanReq = cleanReq.replace(REGEX_REQ_PAREN, "").trim()

        val matchResult = REGEX_REQ_PARSE.find(cleanReq) ?: return cleanReq to emptyList()
        val (name, rest) = matchResult.destructured

        return name to parseSpecs(rest)
    }

    private fun parseSpecs(specsString: String): List<Pair<String, String>> {
        val specs = mutableListOf<Pair<String, String>>()
        if (specsString.isNotBlank()) {
            val parts = specsString.split(",")
            for (part in parts) {
                val p = part.trim()
                val opMatch = REGEX_REQ_SPECS.find(p)
                if (opMatch != null) {
                    val (op, ver) = opMatch.destructured
                    specs.add(op to ver.trim())
                }
            }
        }
        return specs
    }

    private fun parseDependenciesFromMetadata(metadataFile: File): List<String> {
        val deps = mutableListOf<String>()
        try {
            SimpliFiles.file(metadataFile).forEachLine(MAX_METADATA_BYTES) { line ->
                if (line.startsWith("Requires-Dist:")) {
                    val rawReq = line.substring("Requires-Dist:".length).trim()

                    if (rawReq.contains(";")) {
                        val parts = rawReq.split(";", limit = 2)
                        val marker = parts[1].trim()

                        if (!isMarkerCompatible(marker)) {
                            return@forEachLine
                        }
                        deps.add(parts[0].trim())
                    } else {
                        deps.add(rawReq)
                    }
                }
            }
        } catch (e: Exception) {
            FileLog.e("PipController: Failed to parse metadata", e)
        }
        return deps
    }

    private fun isMarkerCompatible(marker: String): Boolean {
        return MarkerParser(marker).parse()
    }

    private fun findMetadataFile(pkg: String, version: String): File? {
        val libDir = getLibPath(pkg, version)
        val distInfo = libDir.listFiles { _, name -> name.endsWith(".dist-info") }?.firstOrNull()
        if (distInfo != null) return File(distInfo, "METADATA")
        return null
    }

    private class MarkerParser(marker: String) {
        private val tokens = REGEX_MARKER_TOKEN.findAll(marker).map { it.value }.toList()
        private var position = 0

        fun parse(): Boolean {
            if (tokens.isEmpty()) return true
            return try {
                parseOr()
            } catch (e: Exception) {
                FileLog.e("PipController: Failed to evaluate marker: ${tokens.joinToString(" ")}", e)
                true
            }
        }

        private fun parseOr(): Boolean {
            var value = parseAnd()
            while (matchKeyword("or")) {
                val right = parseAnd()
                value = value || right
            }
            return value
        }

        private fun parseAnd(): Boolean {
            var value = parseFactor()
            while (matchKeyword("and")) {
                val right = parseFactor()
                value = value && right
            }
            return value
        }

        private fun parseFactor(): Boolean {
            if (match("(")) {
                val value = parseOr()
                match(")")
                return value
            }
            return parseComparison()
        }

        private fun parseComparison(): Boolean {
            val left = next() ?: return true
            val operator = nextOperator() ?: return true
            val right = next() ?: return true
            return evaluateComparison(left, operator, right)
        }

        private fun nextOperator(): String? {
            val token = peek() ?: return null
            val lower = token.lowercase()
            if (lower == "not" && peek(1)?.lowercase() == "in") {
                position += 2
                return "not in"
            }
            return when (lower) {
                "==", "!=", ">=", "<=", ">", "<", "~=", "===", "in" -> {
                    position++
                    lower
                }

                else -> null
            }
        }

        private fun evaluateComparison(left: String, operator: String, right: String): Boolean {
            val leftVariable = markerVariableName(left)
            val rightVariable = markerVariableName(right)

            val leftValue = markerValue(leftVariable) ?: unquote(left)
            val rightValue = markerValue(rightVariable) ?: unquote(right)

            if (leftVariable != null && markerValue(leftVariable) == null) return true
            if (rightVariable != null && markerValue(rightVariable) == null) return true

            val leftIsVersion = leftVariable == "python_version" || leftVariable == "python_full_version"
            val rightIsVersion = rightVariable == "python_version" || rightVariable == "python_full_version"

            if (leftIsVersion && !rightIsVersion) {
                return checkVersionSatisfies(leftValue, listOf(operator to rightValue))
            }
            if (rightIsVersion && !leftIsVersion) {
                return checkVersionSatisfies(
                    rightValue,
                    listOf(invertVersionOperator(operator) to leftValue)
                )
            }

            return when (operator) {
                "==", "===" -> leftValue.equals(rightValue, ignoreCase = true)
                "!=" -> !leftValue.equals(rightValue, ignoreCase = true)
                "in" -> rightValue.contains(leftValue, ignoreCase = true)
                "not in" -> !rightValue.contains(leftValue, ignoreCase = true)
                else -> true
            }
        }

        private fun markerVariableName(token: String): String? {
            if (token.startsWith("'") || token.startsWith("\"")) return null
            val lower = token.lowercase()
            return if (lower.matches(Regex("[a-z_][a-z0-9_]*"))) lower else null
        }

        private fun markerValue(name: String?): String? {
            return when (name) {
                "sys_platform" -> ENV_SYS_PLATFORM
                "platform_system" -> ENV_PLATFORM_SYSTEM
                "os_name" -> ENV_OS_NAME
                "python_version" -> pythonVersion.split(".")
                    .take(2)
                    .joinToString(".")
                "python_full_version" -> pythonVersion
                "implementation_name" -> "cpython"
                "platform_python_implementation" -> "CPython"
                "extra" -> ""
                else -> null
            }
        }

        private fun invertVersionOperator(operator: String): String {
            return when (operator) {
                ">" -> "<"
                ">=" -> "<="
                "<" -> ">"
                "<=" -> ">="
                else -> operator
            }
        }

        private fun unquote(value: String): String {
            if (value.length >= 2) {
                val first = value.first()
                val last = value.last()
                if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                    return value.substring(1, value.length - 1)
                }
            }
            return value
        }

        private fun match(expected: String): Boolean {
            if (peek() != expected) return false
            position++
            return true
        }

        private fun matchKeyword(expected: String): Boolean {
            if (peek()?.lowercase() != expected) return false
            position++
            return true
        }

        private fun next(): String? {
            val token = peek() ?: return null
            position++
            return token
        }

        private fun peek(offset: Int = 0): String? {
            return tokens.getOrNull(position + offset)
        }
    }

    private fun checkVersionSatisfies(version: String, specs: List<Pair<String, String>>): Boolean {
        if (specs.isEmpty()) return true
        for ((operator, spec) in specs) {
            val cmp = VersionComparator.compare(version, spec)
            val satisfied = when (operator) {
                "===" -> version == spec
                "==" -> {
                    if (isWildcardVersionSpec(spec)) matchesVersionWildcard(version, spec)
                    else cmp == 0
                }
                ">=" -> cmp >= 0
                "<=" -> cmp <= 0
                ">" -> cmp > 0
                "<" -> cmp < 0
                "~=" -> {
                    if (cmp < 0) false
                    else {
                        val parts = spec.split(".").toMutableList()

                        if (parts.size >= 2) {
                            parts.removeAt(parts.lastIndex)

                            val lastIdx = parts.lastIndex
                            val lastPartStr = parts[lastIdx]

                            val numericValue = lastPartStr.toIntOrNull()

                            if (numericValue != null) {
                                parts[lastIdx] = (numericValue + 1).toString()
                                val upperLimit = parts.joinToString(".")
                                VersionComparator.compare(version, upperLimit) < 0
                            } else {
                                true
                            }
                        } else {
                            true
                        }
                    }
                }

                "!=" -> {
                    if (isWildcardVersionSpec(spec)) !matchesVersionWildcard(version, spec)
                    else cmp != 0
                }
                else -> true
            }
            if (!satisfied) return false
        }
        return true
    }

    private fun isWildcardVersionSpec(spec: String): Boolean {
        return REGEX_VERSION_WILDCARD.containsMatchIn(spec.trim())
    }

    private fun filterPreReleases(
        versions: List<String>,
        specs: List<Pair<String, String>>
    ): List<String> {
        if (versions.isEmpty() || specsAllowPreRelease(specs)) {
            return versions
        }

        val stableVersions = versions.filterNot { isPreReleaseVersion(it) }
        return stableVersions.ifEmpty { versions }
    }

    private fun specsAllowPreRelease(specs: List<Pair<String, String>>): Boolean {
        return specs.any { (_, spec) -> isPreReleaseVersion(spec) }
    }

    private fun isPreReleaseVersion(version: String): Boolean {
        return parseVersion(version).parts.any { part ->
            when (part) {
                "dev", "alpha", "a", "beta", "b", "rc", "c" -> true
                else -> false
            }
        }
    }

    private fun matchesVersionWildcard(version: String, spec: String): Boolean {
        val prefix = spec.trim()
            .substringBefore("+")
            .removeSuffix(".*")
            .removeSuffix("-*")
            .removeSuffix("_*")
            .trimEnd('.', '-', '_')
        val prefixVersion = parseVersion(prefix)
        val candidateVersion = parseVersion(version)
        if (prefix.contains("!") && prefixVersion.epoch != candidateVersion.epoch) {
            return false
        }

        val prefixParts = parseVersionReleaseParts(prefix)
        if (prefixParts.isEmpty()) return true

        val versionParts = parseVersionReleaseParts(version)
        if (versionParts.size < prefixParts.size) {
            return false
        }

        return prefixParts.indices.all { index -> versionParts[index] == prefixParts[index] }
    }

    private fun parseVersionReleaseParts(version: String): List<Int> {
        val publicVersion = parseVersion(version).publicVersion
        return publicVersion.split(Regex("[.\\-_]"))
            .mapNotNull { part -> part.toIntOrNull() }
    }

    private class SizeLimitedInputStream(
        private val delegate: InputStream,
        private val maxBytes: Long
    ) : InputStream() {
        private var total = 0L

        private fun track(read: Int): Int {
            if (read > 0) {
                total += read
                if (total > maxBytes) {
                    throw IOException("Download exceeds maximum allowed size of $maxBytes bytes")
                }
            }
            return read
        }

        override fun read(): Int {
            val b = delegate.read()
            if (b != -1) track(1)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int = track(delegate.read(b, off, len))

        override fun available(): Int = delegate.available()

        override fun close() = delegate.close()
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun parseVersion(version: String): ParsedVersion {
        val normalized = version.trim().lowercase().removePrefix("v")
        val withoutLocal = normalized.substringBefore("+")
        val epochSplit = withoutLocal.split("!", limit = 2)
        val epoch = if (epochSplit.size == 2) epochSplit[0].toIntOrNull() ?: 0 else 0
        val publicVersion = if (epochSplit.size == 2) epochSplit[1] else epochSplit[0]
        val parts = REGEX_VERSION_SPLIT.findAll(publicVersion).map { it.value }.toList()
        return ParsedVersion(epoch, publicVersion, parts)
    }

    private object VersionComparator : Comparator<String> {
        override fun compare(v1: String, v2: String): Int {
            val parsed1 = parseVersion(v1)
            val parsed2 = parseVersion(v2)
            val epochCmp = parsed1.epoch.compareTo(parsed2.epoch)
            if (epochCmp != 0) return epochCmp

            val p1 = parsed1.parts
            val p2 = parsed2.parts
            val len = max(p1.size, p2.size)

            for (i in 0 until len) {
                var part1 = p1.getOrNull(i)
                var part2 = p2.getOrNull(i)

                if (part1 == null && part2?.toIntOrNull() != null) part1 = "0"
                if (part2 == null && part1?.toIntOrNull() != null) part2 = "0"

                part1 = part1 ?: ""
                part2 = part2 ?: ""

                if (part1 == part2) continue

                val n1 = part1.toIntOrNull()
                val n2 = part2.toIntOrNull()

                if (n1 != null && n2 != null) {
                    val cmp = n1.compareTo(n2)
                    if (cmp != 0) return cmp
                } else if (n1 != null) {
                    return 1
                } else if (n2 != null) {
                    return -1
                } else {
                    val w1 = getWeight(part1)
                    val w2 = getWeight(part2)
                    if (w1 != w2) return w1.compareTo(w2)

                    val cmp = part1.compareTo(part2)
                    if (cmp != 0) return cmp
                }
            }
            return 0
        }

        private fun getWeight(s: String): Int {
            return when {
                s == "post" -> 110
                s.isEmpty() -> 100
                s == "rc" || s == "c" -> 80
                s == "beta" || s == "b" -> 70
                s == "alpha" || s == "a" -> 60
                s == "dev" -> 50
                else -> 0
            }
        }
    }
}