/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.analyzer

internal object AnalyzerNative {

    @JvmStatic
    external fun loadUpdate(library: ByteArray, payload: String, signature: String): Boolean

    @JvmStatic
    private fun verifyUpdate(library: ByteArray, payload: String, signature: String): Boolean {
        val update = AnalyzerUpdate.parse(payload, signature) ?: return false
        return update.isCompatible() && update.verifiesLibrary(library)
    }

    @JvmStatic
    external fun analyze(data: ByteArray, name: ByteArray): ByteArray

    @JvmStatic
    external fun diff(old: ByteArray, new: ByteArray): ByteArray
}
