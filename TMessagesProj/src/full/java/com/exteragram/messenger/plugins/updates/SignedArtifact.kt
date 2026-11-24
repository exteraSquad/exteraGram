/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.updates

import android.system.Os
import android.util.Base64
import org.telegram.messenger.FileLog
import java.io.File
import java.io.IOException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

internal object SignedArtifact {

    fun verify(publicKey: String, payload: String, signature: String): Boolean = try {
        val key = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(Base64.decode(publicKey, Base64.DEFAULT)))
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(key)
        verifier.update(payload.toByteArray(Charsets.UTF_8))
        verifier.verify(Base64.decode(signature, Base64.DEFAULT))
    } catch (e: Exception) {
        FileLog.e(e)
        false
    }

    fun stage(source: File, target: File, maxBytes: Long, verify: (File) -> Boolean): Boolean {
        val dir = requireNotNull(target.parentFile)
        dir.mkdirs()
        val temporary = File.createTempFile(".update-", ".tmp", dir)
        try {
            source.inputStream().use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var size = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        size += read
                        if (size > maxBytes) throw IOException("Update exceeds its size limit")
                        output.write(buffer, 0, read)
                    }
                    output.fd.sync()
                }
            }
            if (!verify(temporary)) return false
            Os.rename(temporary.path, target.path)
            return true
        } finally {
            temporary.delete()
        }
    }
}

private val HEX = "0123456789abcdef".toCharArray()

internal fun ByteArray.toHex(): String {
    val chars = CharArray(size * 2)
    for (i in indices) {
        val v = this[i].toInt() and 0xFF
        chars[i * 2] = HEX[v ushr 4]
        chars[i * 2 + 1] = HEX[v and 0x0F]
    }
    return String(chars)
}

internal fun ByteArray.sha256Hex(): String = MessageDigest.getInstance("SHA-256").digest(this).toHex()

internal fun File.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().toHex()
}
