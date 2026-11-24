/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.analyzer

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.exteragram.messenger.plugins.updates.sha256Hex
import com.exteragram.messenger.plugins.updates.toHex
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

class AnalyzerCache(private val root: File) {

    private val key: SecretKey? by lazy { loadKey() }

    fun prune(buildIds: Set<String>) {
        root.listFiles()?.forEach { dir ->
            if (dir.name !in buildIds) {
                dir.deleteRecursively()
            }
        }
    }

    fun get(buildId: String, sha256: String, name: String): String? {
        val file = file(buildId, sha256, name)
        if (!file.isFile || file.lastModified() < System.currentTimeMillis() - MAX_AGE_MS) {
            return null
        }
        return try {
            val content = file.readText()
            val split = content.indexOf('\n')
            if (split <= 0) {
                return null
            }
            val json = content.substring(split + 1)
            val expected = sign(buildId, sha256, name, json) ?: return null
            if (MessageDigest.isEqual(expected.toByteArray(), content.substring(0, split).toByteArray())) json else null
        } catch (_: Exception) {
            null
        }
    }

    fun put(buildId: String, sha256: String, name: String, json: String) {
        val mac = sign(buildId, sha256, name, json) ?: return
        try {
            val file = file(buildId, sha256, name)
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(mac + "\n" + json)
            if (!tmp.renameTo(file)) {
                tmp.delete()
            }
        } catch (_: Exception) {
        }
    }

    fun expire() {
        val deadline = System.currentTimeMillis() - MAX_AGE_MS
        root.walkTopDown().filter { it.isFile && it.lastModified() < deadline }.forEach { it.delete() }
    }

    private fun file(buildId: String, sha256: String, name: String) =
        File(File(root, buildId), sha256 + "." + name.toByteArray().sha256Hex().take(NAME_KEY_LENGTH) + EXT)

    private fun sign(buildId: String, sha256: String, name: String, json: String): String? {
        val key = key ?: return null
        return try {
            val mac = Mac.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256)
            mac.init(key)
            mac.update("$buildId\n$sha256\n".toByteArray())
            mac.update(name.toByteArray().sha256Hex().toByteArray())
            mac.update('\n'.code.toByte())
            mac.doFinal(json.toByteArray()).toHex()
        } catch (_: Exception) {
            null
        }
    }

    private fun loadKey(): SecretKey? = try {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        store.getKey(KEY_ALIAS, null) as? SecretKey ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE)
            .apply {
                init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN).build())
            }
            .generateKey()
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "plugin_analyzer_cache"
        private const val EXT = ".json"
        private const val NAME_KEY_LENGTH = 16
    }
}
