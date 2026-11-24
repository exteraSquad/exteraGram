/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.nowplaying

import androidx.collection.LruCache
import com.exteragram.messenger.api.db.DatabaseHelper
import com.exteragram.messenger.api.dto.NowPlayingDTO
import com.exteragram.messenger.api.dto.NowPlayingInfoDTO
import com.exteragram.messenger.api.model.NowPlayingServiceType
import com.exteragram.messenger.api.network.ApiClient
import com.exteragram.messenger.debug.forceCompactSavedMusic
import com.exteragram.messenger.nowplaying.ui.components.NowPlayingCardData
import com.exteragram.messenger.utils.chats.ChatUtils
import com.exteragram.messenger.utils.network.ExteraHttpClient
import com.exteragram.messenger.utils.network.RemoteUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.ProfileMusicView
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import java.util.function.Consumer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object NowPlayingController {

    private val ARTISTS_SPLITTER = "(?i)\\s*(?:,|&|\\bfeat\\b\\.?|\\bft\\b\\.?)\\s*".toRegex()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val itunesCache = LruCache<String, List<ItunesTrack>>(50)

    @JvmStatic
    fun shouldShowCard(nowPlayingCardData: NowPlayingCardData?): Boolean {
        if (nowPlayingCardData == null) return false
        val isTelegram = nowPlayingCardData.nowPlayingDTO.platform == "TELEGRAM"
        return if (isTelegram) {
            !isSeparateStylesSupported()
        } else {
            true
        }
    }

    @JvmStatic
    fun isSeparateStylesSupported(): Boolean {
        return forceCompactSavedMusic || RemoteUtils.getBooleanConfigValue(
            RemoteUtils.SEPARATE_MUSIC_STYLES,
            false
        )
    }

    @JvmStatic
    fun getCurrentPlayingTrack(
        userId: Long,
        savedMusic: TLRPC.Document?,
        checkApi: Boolean,
        callback: BiConsumer<NowPlayingDTO?, Long>
    ): Job {
        return scope.launch {
            val startTime = System.currentTimeMillis()

            val liveTrackDeferred = async(Dispatchers.IO) {
                if (!checkApi || !ApiClient.requestsEnabled) return@async null
                try {
                    val response = ApiClient.apiService.getCurrentPlayingTrack(userId)
                    if (response.isSuccessful) response.body() else null
                } catch (t: Throwable) {
                    FileLog.e(t)
                    null
                }
            }

            val savedTrackDeferred = async(Dispatchers.IO) {
                processSavedMusic(savedMusic)
            }

            val liveTrack = liveTrackDeferred.await()

            val finalTrack = if (liveTrack != null && liveTrack.isPlaying) {
                savedTrackDeferred.cancel()
                liveTrack
            } else {
                savedTrackDeferred.await()
            }

            withContext(Dispatchers.Main) {
                callback.accept(finalTrack, System.currentTimeMillis() - startTime)
            }
        }
    }

    private suspend fun processSavedMusic(savedMusic: TLRPC.Document?): NowPlayingDTO? {
        if (savedMusic == null) return null

        val title = ProfileMusicView.getTitle(savedMusic) as? String
            ?: getString(R.string.AudioUnknownTitle)
        val author = ProfileMusicView.getAuthor(savedMusic) as? String
            ?: getString(R.string.AudioUnknownArtist)

        if (title == null || author == null) return null

        val baseArtistsList =
            author.split(ARTISTS_SPLITTER).map { it.trim() }.filter { it.isNotEmpty() }
        val baseDto = NowPlayingDTO(
            trackName = title,
            artists = baseArtistsList,
            albumName = null,
            coverUrl = null,
            previewUrl = null,
            songUrl = null,
            isPlaying = true,
            deviceName = null,
            platform = "TELEGRAM",
            duration = null
        )

        if (isSeparateStylesSupported()) {
            return baseDto
        }

        val itunesTracks = fetchItunesTrack(author, title)

        if (itunesTracks.isNullOrEmpty()) {
            return baseDto
        }

        val resultToUse = itunesTracks.find { track ->
            val (isArtistMatch, _) = hasCommonArtist(baseDto.artists, track.artistName)
            track.trackName.equals(baseDto.trackName, ignoreCase = true) && isArtistMatch
        } ?: itunesTracks.first()

        val (_, finalArtists) = hasCommonArtist(
            baseDto.artists,
            resultToUse.artistName
        )

        return baseDto.copy(
            artists = finalArtists,
            albumName = resultToUse.collectionName ?: baseDto.albumName,
            coverUrl = if (!resultToUse.artworkUrl100.isNullOrEmpty()) {
                resultToUse.artworkUrl100.replace("100x100", "300x300")
            } else {
                baseDto.coverUrl
            },
            platform = baseDto.platform
        )
    }

    private fun hasCommonArtist(
        baseArtists: List<String>?,
        itunesArtists: String?
    ): Pair<Boolean, List<String>?> {
        if (itunesArtists.isNullOrBlank()) return false to baseArtists

        val itunesArtistList = itunesArtists
            .split(ARTISTS_SPLITTER)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (baseArtists.isNullOrEmpty()) return false to itunesArtistList

        val common = baseArtists.any { base ->
            val baseLower = base.lowercase()
            val baseTranslit = AndroidUtilities.translitSafe(baseLower)

            itunesArtistList.any { itunes ->
                val itunesLower = itunes.lowercase()
                val itunesTranslit = AndroidUtilities.translitSafe(itunesLower)

                itunesLower == baseLower ||
                        itunesLower == baseTranslit ||
                        itunesTranslit == baseLower
            }
        }

        val finalArtists = if (common) itunesArtistList else baseArtists
        return common to finalArtists
    }

    @JvmStatic
    fun getNowPlayingInfo(callback: Consumer<NowPlayingInfoDTO?>) {
        scope.launch {
            val result = getNowPlayingInfoInternal()
            callback.accept(result)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun updateNowPlayingInfo(
        newNowPlaying: NowPlayingInfoDTO?,
        cache: Boolean = true,
        callback: Consumer<Boolean>
    ) {
        scope.launch {
            val success = updateNowPlayingInfoInternal(newNowPlaying, cache)
            callback.accept(success)
        }
    }

    private suspend fun getNowPlayingInfoInternal(): NowPlayingInfoDTO? {
        val userId = ChatUtils.getInstance().userConfig.getClientUserId()
        return dbGetNowPlaying(userId) ?: fetchProfileNowPlayingInfo(userId)
    }

    private suspend fun fetchProfileNowPlayingInfo(userId: Long): NowPlayingInfoDTO? = withContext(Dispatchers.IO) {
        if (!ApiClient.requestsEnabled) return@withContext null

        try {
            val response = ApiClient.apiService.getProfile(userId)
            if (!response.isSuccessful) {
                return@withContext null
            }

            val profile = response.body() ?: return@withContext null
            DatabaseHelper.insertProfiles(listOf(profile))
            profile.nowPlaying
        } catch (t: Throwable) {
            FileLog.e(t)
            null
        }
    }

    private suspend fun updateNowPlayingInfoInternal(
        newNowPlaying: NowPlayingInfoDTO?,
        cache: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val query =
                if (newNowPlaying == null || newNowPlaying.serviceType == NowPlayingServiceType.NONE) {
                    "clear_now_playing"
                } else {
                    "set_now_playing ${newNowPlaying.serviceType.name} ${newNowPlaying.username}"
                }

            val message = suspendCancellableCoroutine { continuation ->
                ChatUtils.getInstance().sendBotRequest(query, cache) { result ->
                    continuation.resume(result)
                }
            }

            if (message == "ok") {
                val updatedRows = dbUpdateNowPlaying(
                    ChatUtils.getInstance().userConfig.getClientUserId(),
                    newNowPlaying
                )
                return@withContext updatedRows > 0
            } else {
                return@withContext false
            }
        } catch (_: Exception) {
            return@withContext false
        }
    }

    private suspend fun dbGetNowPlaying(userId: Long): NowPlayingInfoDTO? =
        suspendCancellableCoroutine { continuation ->
            DatabaseHelper.getNowPlaying(userId) { result ->
                continuation.resume(result)
            }
        }

    private suspend fun dbUpdateNowPlaying(userId: Long, data: NowPlayingInfoDTO?): Int =
        suspendCancellableCoroutine { continuation ->
            DatabaseHelper.updateNowPlaying(userId, data) { updatedRows ->
                continuation.resume(updatedRows)
            }
        }

    @Serializable
    data class ItunesSearchResponse(
        val resultCount: Int,
        val results: List<ItunesTrack>
    )

    @Serializable
    data class ItunesTrack(
        val trackName: String? = null,
        val artistName: String? = null,
        val collectionName: String? = null,
        val artworkUrl100: String? = null,
        val previewUrl: String? = null,
        val trackTimeMillis: Long? = null
    )

    private val httpClient = ExteraHttpClient.client.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val jsonParser = Json { ignoreUnknownKeys = true }

    private suspend fun fetchItunesTrack(
        performer: String,
        title: String
    ): List<ItunesTrack>? {
        if (performer.isBlank() && title.isBlank()) {
            return null
        }

        val cacheKey = "$performer - $title"
        itunesCache[cacheKey]?.let {
            return it
        }

        val httpUrl = "https://itunes.apple.com/search".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("term", "$title - $performer")
            ?.addQueryParameter("entity", "song")
            ?.addQueryParameter("limit", "5")
            ?.build()

        if (httpUrl == null) {
            return null
        }

        val request = Request.Builder().url(httpUrl).build()

        return try {
            val response = httpClient.newCall(request).await()
            if (!response.isSuccessful) return null

            val tracks =
                jsonParser.decodeFromString<ItunesSearchResponse>(response.body.string()).results
            tracks.let { itunesCache.put(cacheKey, it) }
            tracks
        } catch (e: Exception) {
            FileLog.e(e)
            null
        }
    }

    suspend fun Call.await(): Response {
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                cancel()
            }

            enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isCancelled) return
                    continuation.resumeWithException(e)
                }
            })
        }
    }
}