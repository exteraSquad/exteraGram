/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.translator.core

data class ProviderLimits(
    val maxTextsPerRequest: Int,
    val maxCharsPerText: Int,
    val maxCharsPerRequest: Int,
    val maxConcurrent: Int,
    val minIntervalMs: Long,
    val maxAttempts: Int,
    val baseBackoffMs: Long,
    val maxBackoffMs: Long
) {
    companion object {

        @JvmField
        val GOOGLE = ProviderLimits(
            maxTextsPerRequest = 20,
            maxCharsPerText = 5000,
            maxCharsPerRequest = 8000,
            maxConcurrent = 2,
            minIntervalMs = 0,
            maxAttempts = 4,
            baseBackoffMs = 1_000,
            maxBackoffMs = 30_000
        )

        @JvmField
        val YANDEX = ProviderLimits(
            maxTextsPerRequest = 20,
            maxCharsPerText = 5000,
            maxCharsPerRequest = 5000,
            maxConcurrent = 2,
            minIntervalMs = 0,
            maxAttempts = 4,
            baseBackoffMs = 1_000,
            maxBackoffMs = 30_000
        )

        @JvmField
        val MICROSOFT = ProviderLimits(
            maxTextsPerRequest = 20,
            maxCharsPerText = 5000,
            maxCharsPerRequest = 10000,
            maxConcurrent = 4,
            minIntervalMs = 0,
            maxAttempts = 4,
            baseBackoffMs = 1_000,
            maxBackoffMs = 30_000
        )

    }
}