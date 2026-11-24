/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.translator.providers

import com.exteragram.messenger.translator.TranslatorUtils
import com.exteragram.messenger.translator.core.HttpTranslator
import com.exteragram.messenger.translator.core.ProviderLimits
import com.exteragram.messenger.translator.core.ProviderResponse
import com.exteragram.messenger.translator.core.TranslationError

import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response

import org.json.JSONArray

class GoogleTranslator private constructor() : HttpTranslator() {

    override val displayName = "Google"

    override val supportedLanguages: Set<String> = emptySet()

    override val limits = ProviderLimits.GOOGLE

    override fun buildRequest(texts: List<String>, fromLang: String, toLang: String, html: Boolean): Request {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("translate.googleapis.com")
            .addPathSegments("translate_a/t")
            .addQueryParameter("client", "gtx")
            .addQueryParameter("sl", fromLang)
            .addQueryParameter("tl", toLang)
            .addQueryParameter("dt", "t")
            .addQueryParameter("format", if (html) "html" else "text")
            .addQueryParameter("ie", "UTF-8")
            .addQueryParameter("oe", "UTF-8")
            .build()

        val body = FormBody.Builder()
            .apply { texts.forEach { add("q", it) } }
            .build()

        return Request.Builder()
            .url(url)
            .header("User-Agent", TranslatorUtils.formatUserAgent())
            .post(body)
            .build()
    }

    override fun parseResponse(response: Response, expected: Int): ProviderResponse {
        if (!response.isSuccessful) {
            return ProviderResponse.Failure(httpError(response))
        }
        val body = response.body.string()
        if (body.isEmpty()) {
            return ProviderResponse.Failure(TranslationError.Transient)
        }

        val array = JSONArray(body)
        val result = ArrayList<String>(expected)
        for (i in 0 until array.length()) {
            result.add(
                when (val item = array.get(i)) {
                    is JSONArray -> item.optString(0)
                    else -> item.toString()
                }
            )
        }
        if (result.size != expected) {
            return ProviderResponse.Failure(TranslationError.Transient)
        }
        return ProviderResponse.Success(result)
    }

    companion object {
        private val shared by lazy { GoogleTranslator() }

        @JvmStatic
        fun getInstance() = shared
    }
}