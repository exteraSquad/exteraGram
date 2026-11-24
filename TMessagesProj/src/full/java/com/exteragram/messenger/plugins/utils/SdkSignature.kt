/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.utils

import com.exteragram.messenger.plugins.updates.SignedArtifact
import com.exteragram.messenger.plugins.updates.sha256Hex
import org.telegram.messenger.FileLog
import java.io.File

object SdkSignature {

    private const val PUBLIC_KEY =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAER7oj20N/vwmRC/Zig0lX/xOQvh6kSkPOhuoZlIm7IYHyY0qo3YqMGVfGqjjqX4ZcsGiDy91ELUEElv3bCSfHTg=="

    private const val PAYLOAD_PREFIX = "extera-pysdk"

    @JvmStatic
    fun verify(archive: File, version: String?, abi: String?, signature: String?): Boolean {
        if (version.isNullOrEmpty() || abi.isNullOrEmpty() || signature.isNullOrEmpty()) {
            FileLog.e("Python SDK update is not signed")
            return false
        }
        if (!archive.exists()) {
            FileLog.e("Python SDK archive to verify does not exist")
            return false
        }

        return try {
            SignedArtifact.verify(PUBLIC_KEY, "$PAYLOAD_PREFIX|$version|$abi|${archive.sha256Hex()}", signature).also { valid ->
                if (!valid) FileLog.e("Python SDK signature does not match ($version, $abi)")
            }
        } catch (e: Exception) {
            FileLog.e("Failed to verify Python SDK signature", e)
            false
        }
    }
}
