/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.translator.core

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

import com.exteragram.messenger.translator.FormattedTranslation
import com.exteragram.messenger.translator.TranslatorUtils
import org.telegram.tgnet.TLRPC
import com.exteragram.messenger.utils.network.ExteraHttpClient

abstract class HttpTranslator : BaseTranslator() {

    abstract val limits: ProviderLimits

    open val client: OkHttpClient
        get() = ExteraHttpClient.client

    abstract fun buildRequest(texts: List<String>, fromLang: String, toLang: String, html: Boolean = false): Request

    abstract fun parseResponse(response: Response, expected: Int): ProviderResponse

    final override fun translate(
        text: String,
        fromLang: String,
        toLang: String,
        callback: TranslatorUtils.TranslateCallback
    ) {
        if (text.isBlank()) {
            callback.onFailed(TranslationError.Fatal)
            return
        }
        TranslationDispatcher.enqueue(this, MANUAL_TAG, listOf(text), fromLang, toLang, true) { texts, error ->
            val result = texts?.firstOrNull()
            if (result.isNullOrEmpty()) {
                callback.onFailed(error ?: TranslationError.Fatal)
            } else {
                callback.onSuccess(result)
            }
        }
    }

    fun translate(
        text: String,
        fromLang: String,
        toLang: String,
        entities: ArrayList<TLRPC.MessageEntity>?,
        callback: TranslatorUtils.TranslateCallback
    ) {
        val source = TLRPC.TL_textWithEntities().apply {
            this.text = text
            this.entities = entities ?: ArrayList()
        }
        FormattedTranslation.translate(MANUAL_TAG, listOf(source), fromLang, toLang, this, true) { results, error ->
            val result = results.firstOrNull()
            if (result == null || result.text.isNullOrEmpty()) {
                callback.onFailed(error ?: TranslationError.Fatal)
            } else {
                callback.onSuccess(result.text)
                callback.onSuccess(result)
            }
        }
    }

    protected fun httpError(response: Response): TranslationError = when {
        response.code == 429 -> TranslationError.RateLimited(retryAfterMs(response))
        response.code in 500..599 -> TranslationError.Transient
        else -> TranslationError.Fatal
    }

    protected fun retryAfterMs(response: Response): Long {
        val header = response.header("Retry-After")?.trim() ?: return 0
        return (header.toLongOrNull() ?: 0L) * 1000L
    }

    companion object {
        const val MANUAL_TAG = 0L
    }
}