/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.translator.providers

import com.exteragram.messenger.translator.core.HttpTranslator
import com.exteragram.messenger.translator.core.ProviderLimits
import com.exteragram.messenger.translator.core.ProviderResponse
import com.exteragram.messenger.translator.core.TranslationError
import com.exteragram.messenger.utils.text.SealedString

import android.util.Base64

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

import org.json.JSONArray
import org.json.JSONObject

import org.telegram.messenger.FileLog

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class MicrosoftTranslator private constructor() : HttpTranslator() {

    override val displayName = "Microsoft"

    override val supportedLanguages: Set<String> = emptySet()

    override val limits = ProviderLimits.MICROSOFT

    override fun buildRequest(texts: List<String>, fromLang: String, toLang: String, html: Boolean): Request {
        val from = if (fromLang.equals("auto", ignoreCase = true)) "" else fromLang
        val path = "$ENDPOINT_PATH$from&to=$toLang&textType=${if (html) "html" else "plain"}"

        val body = JSONArray()
        for (text in texts) {
            body.put(JSONObject().put("Text", text))
        }

        return Request.Builder()
            .url(SCHEME + path)
            .header("X-Mt-Signature", signature(path))
            .header("User-Agent", USER_AGENT)
            .post(
                body.toString().toByteArray(StandardCharsets.UTF_8)
                    .toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull())
            )
            .build()
    }

    private fun signature(path: String): String {
        val nonce = UUID.randomUUID().toString().replace("-", "")

        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.US)
        format.timeZone = GMT
        val date = format.format(Date(Calendar.getInstance(GMT).timeInMillis))
            .lowercase(Locale.US) + "GMT"

        val payload = (SIGNATURE_SCHEME + URLEncoder.encode(path, "UTF-8") + date + nonce)
            .lowercase(Locale.US)

        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(Base64.decode(SIGNING_KEY, Base64.DEFAULT), HMAC_ALGORITHM))
        val hmac = Base64.encodeToString(
            mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8)),
            Base64.NO_WRAP
        )

        return "$SIGNATURE_SCHEME::$hmac::$date::$nonce"
    }

    override fun parseResponse(response: Response, expected: Int): ProviderResponse {
        val body = response.body.string()
        if (body.isEmpty()) {
            return ProviderResponse.Failure(
                if (response.isSuccessful) TranslationError.Transient else httpError(response)
            )
        }
        if (!body.startsWith("[")) {
            return ProviderResponse.Failure(failure(response, body))
        }

        val array = JSONArray(body)
        if (array.length() != expected) {
            return ProviderResponse.Failure(TranslationError.Transient)
        }
        return ProviderResponse.Success(
            List(array.length()) {
                array.getJSONObject(it).getJSONArray("translations").getJSONObject(0).optString("text")
            }
        )
    }

    private fun failure(response: Response, body: String): TranslationError {
        val code = JSONObject(body).optJSONObject("error")?.optInt("code") ?: 0
        return when {
            response.code == 429 -> TranslationError.RateLimited(retryAfterMs(response))
            code / 1000 == 401 -> {
                FileLog.e("translator: Microsoft rejected the signature, invalid credentials")
                TranslationError.Fatal
            }
            response.code in 500..599 -> TranslationError.Transient
            else -> TranslationError.Fatal
        }
    }

    companion object {

        private val SIGNING_KEY = SealedString.open(
            "VkoWrMP7SOoLpld96DpMgu62G2pGf-nql2-ArwaqpebuNvhBXWDd9dO607hKGNSk_kMvimVi2tfSTw9dSnzblT9IYMCO5KWoixFL73MvoMVQ7xGMhfkBHp-U6wBIuw"
        )

        private const val SCHEME = "https://"
        private const val ENDPOINT_PATH =
            "api.cognitive.microsofttranslator.com/translate?api-version=3.0&from="
        private const val SIGNATURE_SCHEME = "MSTranslatorAndroidApp"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val USER_AGENT = "okhttp/4.5.0"

        private val GMT: TimeZone = TimeZone.getTimeZone("GMT")

        private val shared by lazy { MicrosoftTranslator() }

        @JvmStatic
        fun getInstance() = shared
    }
}