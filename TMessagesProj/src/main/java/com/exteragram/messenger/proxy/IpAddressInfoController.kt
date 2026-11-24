/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.proxy

import android.os.SystemClock
import androidx.core.net.toUri
import com.exteragram.messenger.utils.network.ExteraHttpClient
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.telegram.messenger.FileLog
import org.telegram.messenger.Utilities
import java.io.IOException
import java.net.InetAddress
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit

object IpAddressInfoController {

    private const val MAX_CACHE_SIZE = 100
    private const val INFO_CACHE_TTL = 24 * 60 * 60 * 1000L
    private const val UNAVAILABLE_CACHE_TTL = 5 * 60 * 1000L

    private val ipv4Regex = "^\\d{1,3}(?:\\.\\d{1,3}){3}$".toRegex()
    private val ipv6CharsRegex = "^[0-9a-f:.]+$".toRegex(RegexOption.IGNORE_CASE)

    private val cache = object : LinkedHashMap<String, CacheEntry>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }

    private val httpClient = ExteraHttpClient.client.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .build()

    data class IpAddressInfo(
        @JvmField val ip: String,
        @JvmField val country: String,
        @JvmField val countryCode: String,
        @JvmField val region: String,
        @JvmField val city: String,
        @JvmField val isp: String,
        @JvmField val organization: String,
        @JvmField val domain: String,
        @JvmField val timezone: String
    )

    private data class CacheEntry(
        val info: IpAddressInfo?,
        val time: Long,
        val ttl: Long
    )

    @JvmStatic
    fun extractIpAddress(url: String?): String? {
        if (url.isNullOrEmpty()) {
            return null
        }

        var host = try {
            url.toUri().host ?: if (!url.contains("://")) "http://$url".toUri().host else null
        } catch (e: Exception) {
            FileLog.e(e)
            null
        }

        if (host.isNullOrEmpty()) {
            return null
        }

        if (host.startsWith("[") && host.endsWith("]") && host.length > 2) {
            host = host.substring(1, host.length - 1)
        }

        if (!isIpAddress(host)) {
            return null
        }

        return try {
            val address = InetAddress.getByName(host)
            if (
                address.isAnyLocalAddress ||
                address.isLoopbackAddress ||
                address.isLinkLocalAddress ||
                address.isSiteLocalAddress ||
                address.isMulticastAddress
            ) {
                null
            } else {
                address.hostAddress
            }
        } catch (e: Exception) {
            FileLog.e(e)
            null
        }
    }

    @JvmStatic
    fun requestIpAddressInfo(
        ipAddress: String,
        callback: Utilities.Callback<IpAddressInfo?>
    ): Call? {
        val cachedInfo = getCachedInfo(ipAddress)
        if (cachedInfo != null) {
            callback.run(cachedInfo.info)
            return null
        }

        val url = "https://ipwho.is".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegment(ipAddress)
            ?.build()
        if (url == null) {
            callback.run(null)
            return null
        }

        val call = httpClient.newCall(Request.Builder().url(url).build())
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) {
                    return
                }
                FileLog.e(e)
                putCachedInfo(ipAddress, null)
                callback.run(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val info = try {
                        if (response.isSuccessful) {
                            parseIpAddressInfo(ipAddress, response.body.string())
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        FileLog.e(e)
                        null
                    }

                    if (!call.isCanceled()) {
                        putCachedInfo(ipAddress, info)
                        callback.run(info)
                    }
                }
            }
        })
        return call
    }

    @JvmStatic
    fun joinInfo(vararg values: String?): String {
        return values.filter { !it.isNullOrEmpty() }.joinToString(", ")
    }

    private fun isIpAddress(host: String?): Boolean {
        if (host.isNullOrEmpty()) {
            return false
        }
        if (host.indexOf(':') >= 0) {
            return ipv6CharsRegex.matches(host)
        }
        if (!ipv4Regex.matches(host)) {
            return false
        }
        return host.split('.').all {
            Utilities.parseInt(it) in 0..255
        }
    }

    private fun getCachedInfo(ipAddress: String): CacheEntry? {
        return synchronized(cache) {
            val entry = cache[ipAddress]
            if (entry == null) {
                null
            } else if (SystemClock.elapsedRealtime() - entry.time <= entry.ttl) {
                entry
            } else {
                cache.remove(ipAddress)
                null
            }
        }
    }

    private fun putCachedInfo(ipAddress: String, info: IpAddressInfo?) {
        synchronized(cache) {
            cache[ipAddress] = CacheEntry(
                info = info,
                time = SystemClock.elapsedRealtime(),
                ttl = if (info != null) INFO_CACHE_TTL else UNAVAILABLE_CACHE_TTL
            )
        }
    }

    private fun parseIpAddressInfo(ipAddress: String, response: String): IpAddressInfo? {
        val json = JSONObject(response)
        if (!json.optBoolean("success", false)) {
            return null
        }

        val connectionJson = json.optJSONObject("connection")
        val timezoneJson = json.optJSONObject("timezone")
        return IpAddressInfo(
            ip = json.optString("ip", ipAddress),
            country = json.optString("country", ""),
            countryCode = json.optString("country_code", ""),
            region = json.optString("region", ""),
            city = json.optString("city", ""),
            isp = connectionJson?.optString("isp", "") ?: "",
            organization = connectionJson?.optString("org", "") ?: "",
            domain = connectionJson?.optString("domain", "") ?: "",
            timezone = timezoneJson?.optString("id", "") ?: ""
        )
    }
}