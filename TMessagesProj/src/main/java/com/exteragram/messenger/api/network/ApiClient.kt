/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.api.network

import com.exteragram.messenger.debug.disableApiRequests
import com.exteragram.messenger.utils.network.ExteraHttpClient
import com.google.gson.GsonBuilder
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    private const val BASE_URL = "https://api.exteragram.app/api/v1/"

    internal val requestsEnabled: Boolean
        get() = !disableApiRequests

    val apiService: ApiService by lazy {
        val okHttpClient = ExteraHttpClient.client.newBuilder()
            .addInterceptor { chain ->
                if (requestsEnabled) {
                    chain.proceed(chain.request())
                } else {
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(503)
                        .message("extera API requests disabled")
                        .body(ByteArray(0).toResponseBody())
                        .build()
                }
            }
            .build()
        val gson = GsonBuilder().create()
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        retrofit.create(ApiService::class.java)
    }
}
