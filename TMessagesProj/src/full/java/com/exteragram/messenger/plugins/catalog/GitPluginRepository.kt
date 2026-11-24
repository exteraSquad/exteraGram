/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.catalog

import com.exteragram.messenger.utils.network.ExteraHttpClient
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.OffsetDateTime

object GitPluginRepository {
    const val INDEX_FILE_NAME = "extera-plugins.json"
    const val SCHEMA_VERSION = 1

    private const val MAX_INDEX_SIZE = 1024L * 1024
    private const val MAX_CATEGORIES = 3
    private const val BUFFER_SIZE = 16 * 1024
    private val GITHUB_URL = Regex("^github\\.com/([A-Za-z0-9-]+)/([A-Za-z0-9._-]+?)(?:\\.git)?(?:/tree/([^/]+)(/.+)?)?$", RegexOption.IGNORE_CASE)
    private val GITHUB_BLOB_URL = Regex("^(github\\.com/[^/]+/[^/]+)/blob/(.+)/${Regex.escape(INDEX_FILE_NAME)}$", RegexOption.IGNORE_CASE)
    private val SHA256 = Regex("^[0-9a-f]{64}$")

    class IndexPlugin(
        val id: String,
        val name: String,
        val version: String,
        val fileUrl: String,
        val fileName: String,
        val sha256: String,
        val size: Long,
        val description: String?,
        val author: String?,
        val icon: String?,
        val appVersion: String?,
        val sdkVersion: String?,
        val requirements: String?,
        val categories: String?,
        val updatedAt: Int?,
    )

    class Index(
        val name: String,
        val homepage: String?,
        val plugins: List<IndexPlugin>,
    )

    sealed class IndexResult {
        class Loaded(val index: Index, val etag: String?) : IndexResult()
        data object NotModified : IndexResult()
        data object Unsupported : IndexResult()
        data object Failed : IndexResult()
    }

    fun normalizeUrl(input: String?): String? {
        var value = input?.trim()?.trimEnd('/') ?: return null
        if (value.isEmpty() || value.any { it.isWhitespace() }) {
            return null
        }
        val bare = value.removePrefix("https://").removePrefix("http://").removePrefix("www.").let {
            GITHUB_BLOB_URL.matchEntire(it)?.destructured?.let { (repository, path) -> "$repository/tree/$path" } ?: it
        }
        GITHUB_URL.matchEntire(bare)?.let { match ->
            val (owner, repo, ref, dir) = match.destructured
            return buildString {
                append("https://github.com/").append(owner.lowercase()).append('/').append(repo.lowercase())
                if (ref.isNotEmpty()) {
                    append("/tree/").append(ref).append(dir)
                }
            }
        }
        if (!value.contains("://")) {
            value = "https://$value"
        }
        val url = value.toHttpUrlOrNull() ?: return null
        return url.toString().takeIf { url.isHttps && url.pathSegments.lastOrNull() == INDEX_FILE_NAME }
    }

    fun getIndexUrls(repositoryUrl: String): List<String> {
        val match = GITHUB_URL.matchEntire(repositoryUrl.removePrefix("https://")) ?: return listOf(repositoryUrl)
        val (owner, repo, ref, dir) = match.destructured
        val path = dir.trim('/').let { if (it.isEmpty()) INDEX_FILE_NAME else "$it/$INDEX_FILE_NAME" }
        val raw = "https://raw.githubusercontent.com/$owner/$repo/${ref.ifEmpty { "HEAD" }}/$path"
        return if (ref.isEmpty()) listOf(raw) else listOf(raw, "https://cdn.jsdelivr.net/gh/$owner/$repo@$ref/$path")
    }

    fun getWebUrl(repositoryUrl: String, homepage: String?): String? =
        homepage ?: repositoryUrl.takeIf { GITHUB_URL.matches(it.removePrefix("https://")) }

    fun loadIndex(url: String, etag: String?): IndexResult {
        val request = Request.Builder().url(url).apply {
            if (etag != null) {
                header("If-None-Match", etag)
            }
        }.build()
        return try {
            ExteraHttpClient.client.newCall(request).execute().use { response ->
                when {
                    response.code == 304 -> IndexResult.NotModified
                    !response.isSuccessful -> {
                        FileLog.e("PluginCatalog: index request $url failed with ${response.code}")
                        IndexResult.Failed
                    }

                    else -> {
                        val body = response.body.source()
                        if (body.request(MAX_INDEX_SIZE + 1)) {
                            FileLog.e("PluginCatalog: index $url is too large")
                            return IndexResult.Failed
                        }
                        val json = JSONObject(body.buffer.readUtf8())
                        if (json.optInt("schema", 0) > SCHEMA_VERSION) {
                            IndexResult.Unsupported
                        } else {
                            parseIndex(json, response.request.url)?.let { IndexResult.Loaded(it, response.header("ETag")) } ?: IndexResult.Failed
                        }
                    }
                }
            }
        } catch (e: Exception) {
            FileLog.e("PluginCatalog: failed to load index $url", e)
            IndexResult.Failed
        }
    }

    private fun parseIndex(json: JSONObject, baseUrl: HttpUrl): Index? {
        val name = json.stringOrNull("name")
        val plugins = json.optJSONArray("plugins")
        if (json.optInt("schema", 0) < 1 || name == null || plugins == null) {
            return null
        }
        val seen = HashSet<String>()
        val parsed = (0 until plugins.length()).mapNotNull { i ->
            plugins.optJSONObject(i)?.let { parsePlugin(it, baseUrl) }?.takeIf { seen.add(it.id) }
        }
        return Index(name, json.stringOrNull("homepage")?.toHttpUrlOrNull()?.takeIf { it.isHttps }?.toString(), parsed)
    }

    private fun parsePlugin(json: JSONObject, baseUrl: HttpUrl): IndexPlugin? {
        val id = json.stringOrNull("id") ?: return null
        val name = json.stringOrNull("name") ?: return null
        val version = json.stringOrNull("version") ?: return null
        val sha256 = json.stringOrNull("sha256")?.takeIf { SHA256.matches(it) } ?: return null
        val size = json.optLong("size", 0).takeIf { it > 0 } ?: return null
        val fileUrl = json.stringOrNull("file")?.let(baseUrl::resolve)?.takeIf { it.isHttps } ?: return null
        val fileName = fileUrl.pathSegments.lastOrNull()?.takeIf(::isSafeFileName) ?: return null
        return IndexPlugin(
            id = id,
            name = name,
            version = version,
            fileUrl = fileUrl.toString(),
            fileName = fileName,
            sha256 = sha256,
            size = size,
            description = json.stringOrNull("description"),
            author = json.stringOrNull("author"),
            icon = json.stringOrNull("icon"),
            appVersion = json.stringOrNull("app_version"),
            sdkVersion = json.stringOrNull("sdk_version"),
            requirements = json.stringOrList("requirements", Int.MAX_VALUE, ", "),
            categories = json.stringOrList("category", MAX_CATEGORIES, ","),
            updatedAt = json.stringOrNull("updated_at")?.let(::parseTime),
        )
    }

    fun downloadFile(fileUrl: String, fileName: String, sha256: String, size: Long): File? {
        if (!isSafeFileName(fileName) || !SHA256.matches(sha256)) {
            return null
        }
        val dir = File(ApplicationLoader.applicationContext.cacheDir, "plugin_catalog/$sha256")
        val file = File(dir, fileName)
        if (file.length() == size && hash(file) == sha256) {
            return file
        }
        val temp = try {
            dir.mkdirs()
            File.createTempFile("plugin", ".part", dir)
        } catch (e: IOException) {
            FileLog.e("PluginCatalog: cannot create a temp file for $fileName", e)
            return null
        }
        return try {
            ExteraHttpClient.client.newCall(Request.Builder().url(fileUrl).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }
                val digest = MessageDigest.getInstance("SHA-256")
                var written = 0L
                response.body.byteStream().use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var read = input.read(buffer)
                        while (read >= 0) {
                            written += read
                            if (written > size) {
                                throw IOException("file is larger than $size bytes")
                            }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            read = input.read(buffer)
                        }
                    }
                }
                if (written != size || digest.digest().toHex() != sha256) {
                    throw IOException("size or sha256 mismatch")
                }
            }
            if (!temp.renameTo(file)) {
                throw IOException("cannot move ${temp.name}")
            }
            file
        } catch (e: Exception) {
            FileLog.e("PluginCatalog: failed to download $fileUrl", e)
            temp.delete()
            null
        }
    }

    fun documentId(repositoryId: Long, pluginId: String, sha256: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest("$repositoryId:$pluginId:$sha256".toByteArray())
        return digest.take(8).fold(0L) { acc, b -> acc shl 8 or (b.toLong() and 0xff) } or Long.MIN_VALUE
    }

    private fun isSafeFileName(name: String): Boolean =
        name.isNotEmpty() && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it == '\u0000' }

    private fun hash(file: File): String? {
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                var read = input.read(buffer)
                while (read >= 0) {
                    digest.update(buffer, 0, read)
                    read = input.read(buffer)
                }
            }
        } catch (_: IOException) {
            return null
        }
        return digest.digest().toHex()
    }

    private fun parseTime(value: String): Int? = try {
        OffsetDateTime.parse(value).toEpochSecond().toInt()
    } catch (_: Exception) {
        null
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun JSONObject.stringOrNull(key: String): String? =
        (opt(key) as? String)?.trim()?.takeIf { it.isNotEmpty() }

    private fun JSONObject.stringOrList(key: String, limit: Int, separator: String): String? {
        val values = when (val value = opt(key)) {
            is String -> listOf(value)
            is JSONArray -> (0 until value.length()).mapNotNull { value.opt(it) as? String }
            else -> emptyList()
        }
        return values.map { it.trim() }.filter { it.isNotEmpty() }.take(limit).joinToString(separator).ifEmpty { null }
    }
}
