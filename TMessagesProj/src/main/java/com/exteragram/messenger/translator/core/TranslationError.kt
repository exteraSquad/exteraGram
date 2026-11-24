/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.translator.core

sealed class TranslationError {

    data class RateLimited(val retryAfterMs: Long = 0) : TranslationError()

    data object Transient : TranslationError()

    data object LanguageUnsupported : TranslationError()

    data object Fatal : TranslationError()

    data object Cancelled : TranslationError()

    val isRetryable: Boolean
        get() = this is RateLimited || this is Transient
}

sealed class ProviderResponse {

    class Success(val texts: List<String>) : ProviderResponse()

    class Failure(val error: TranslationError) : ProviderResponse()
}