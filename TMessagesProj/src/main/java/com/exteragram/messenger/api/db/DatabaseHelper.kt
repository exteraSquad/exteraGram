/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.api.db

import com.exteragram.messenger.api.dto.AddedRegDateDTO
import com.exteragram.messenger.api.dto.BoostySubscriberDTO
import com.exteragram.messenger.api.dto.NowPlayingInfoDTO
import com.exteragram.messenger.api.dto.ProfileDTO
import com.exteragram.messenger.utils.backgroundScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.telegram.messenger.FileLog
import java.util.function.Consumer

object DatabaseHelper {
    private val scope = backgroundScope()

    suspend fun insertProfiles(profiles: List<ProfileDTO>): Boolean {
        return try {
            ExteraDatabase.getInstance().profileDao().insertAll(profiles)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            FileLog.e(t)
            false
        }
    }

    suspend fun deleteProfiles(ids: List<Long>): Boolean {
        return try {
            ExteraDatabase.getInstance().profileDao().deleteProfiles(ids)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            FileLog.e(t)
            false
        }
    }

    @JvmStatic
    fun getNowPlaying(id: Long, callback: Consumer<NowPlayingInfoDTO?>) {
        scope.launch {
            try {
                val profile = ExteraDatabase.getInstance().profileDao().getById(id)
                callback.accept(profile?.nowPlaying)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                FileLog.e(t)
                callback.accept(null)
            }
        }
    }

    @JvmStatic
    fun updateNowPlaying(id: Long, newNowPlaying: NowPlayingInfoDTO?, callback: Consumer<Int>) {
        scope.launch {
            try {
                val result =
                    ExteraDatabase.getInstance().profileDao().updateNowPlaying(id, newNowPlaying)
                callback.accept(result)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                FileLog.e(t)
                callback.accept(0)
            }
        }
    }

    @JvmStatic
    fun isRegDateAdded(userId: Long, callback: Consumer<Boolean>) {
        scope.launch {
            try {
                val isAdded = ExteraDatabase.getInstance().addedRegDateDao().isAdded(userId)
                callback.accept(isAdded)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                FileLog.e(t)
                callback.accept(false)
            }
        }
    }

    @JvmStatic
    fun setRegDateAdded(userId: Long) {
        scope.launch {
            try {
                ExteraDatabase.getInstance().addedRegDateDao().insert(AddedRegDateDTO(userId))
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                FileLog.e(t)
            }
        }
    }

    suspend fun insertBoostySubscribers(subscribers: List<BoostySubscriberDTO>): Boolean {
        return try {
            ExteraDatabase.getInstance().boostySubscriberDao().replaceSubscribers(subscribers)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            FileLog.e(t)
            false
        }
    }

    @JvmStatic
    fun getBoostySubscribers(callback: Consumer<List<BoostySubscriberDTO>>) {
        scope.launch {
            try {
                val subscribers = ExteraDatabase.getInstance().boostySubscriberDao().getAll()
                val sortedSubscribers = subscribers.sortedByDescending { it.totalAmountRub }
                callback.accept(sortedSubscribers)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                FileLog.e(t)
                callback.accept(emptyList())
            }
        }
    }
}
