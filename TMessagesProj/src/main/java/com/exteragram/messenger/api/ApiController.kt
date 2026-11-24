/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.api

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.exteragram.messenger.preferences
import com.exteragram.messenger.api.db.DatabaseHelper
import com.exteragram.messenger.api.db.ExteraDatabase
import com.exteragram.messenger.api.dto.ProfileDTO
import com.exteragram.messenger.api.model.ProfileStatus
import com.exteragram.messenger.api.model.ProfileType
import com.exteragram.messenger.api.network.ApiClient
import com.exteragram.messenger.api.worker.SyncWorker
import com.exteragram.messenger.badges.BadgesController
import com.exteragram.messenger.utils.backgroundScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.ui.Components.ForegroundDetector
import retrofit2.Response
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object ApiController {
    private val scope = backgroundScope()
    private val started = AtomicBoolean(false)
    private val syncMutex = Mutex()

    private const val SYNC_TIMESTAMP_HEADER = "X-Sync-Timestamp"

    private const val PROFILES_SYNC_TIME_KEY = "lastProfilesSyncTime"
    private const val BOOSTY_SYNC_TIME_KEY = "lastBoostySyncTime"
    private const val SYNC_FAILURE_TIME_KEY = "lastSyncFailureTime"
    private const val PROFILES_SCHEMA_KEY = "profilesSchema"
    private const val PROFILES_SCHEMA = 1

    private val PROFILES_SYNC_INTERVAL = Duration.ofMinutes(10).toMillis()
    private val BOOSTY_SYNC_INTERVAL = Duration.ofMinutes(40).toMillis()
    private val FAILURE_BACKOFF = Duration.ofMinutes(5).toMillis()
    private val DONATION_WATCH_INTERVAL = Duration.ofSeconds(10).toMillis()
    private val DONATION_WATCH_DURATION = Duration.ofMinutes(15).toMillis()

    private var donationWatch: Job? = null

    @JvmStatic
    fun init() {
        if (!started.compareAndSet(false, true)) return
        sync()
        scheduleWorker()
        ForegroundDetector.getInstance().addListener(object : ForegroundDetector.Listener {
            override fun onBecameForeground() {
                sync()
            }

            override fun onBecameBackground() {}
        })
    }

    @JvmStatic
    @JvmOverloads
    fun sync(prefs: SharedPreferences = preferences, force: Boolean = false) {
        if (!ApiClient.requestsEnabled) return

        if (!force) {
            val now = System.currentTimeMillis()
            if (elapsedSince(now, prefs.getLong(SYNC_FAILURE_TIME_KEY, 0L)) < FAILURE_BACKOFF) return
            val profilesDue = isProfilesSchemaStale(prefs) || elapsedSince(now, prefs.getLong(PROFILES_SYNC_TIME_KEY, 0L)) >= PROFILES_SYNC_INTERVAL
            val boostyDue = elapsedSince(now, prefs.getLong(BOOSTY_SYNC_TIME_KEY, 0L)) >= BOOSTY_SYNC_INTERVAL
            if (!profilesDue && !boostyDue) return
        }

        scope.launch {
            performSync(prefs, force)
        }
    }

    @JvmStatic
    fun isProfilesSchemaStale(prefs: SharedPreferences = preferences): Boolean {
        return prefs.getInt(PROFILES_SCHEMA_KEY, 0) != PROFILES_SCHEMA
    }

    suspend fun performSync(prefs: SharedPreferences = preferences, force: Boolean = false): Boolean = syncMutex.withLock {
        if (!ApiClient.requestsEnabled) return@withLock true

        val now = System.currentTimeMillis()
        if (!force && elapsedSince(now, prefs.getLong(SYNC_FAILURE_TIME_KEY, 0L)) < FAILURE_BACKOFF) {
            return@withLock false
        }

        val boostyDue = force || elapsedSince(now, prefs.getLong(BOOSTY_SYNC_TIME_KEY, 0L)) >= BOOSTY_SYNC_INTERVAL
        val fullProfilesSync = isProfilesSchemaStale(prefs)
        val profilesDue = force || fullProfilesSync || elapsedSince(now, prefs.getLong(PROFILES_SYNC_TIME_KEY, 0L)) >= PROFILES_SYNC_INTERVAL
        if (!boostyDue && !profilesDue) {
            return@withLock true
        }

        var success = true
        var attempted = false

        // sync boosty subscribers
        if (boostyDue) {
            attempted = true
            try {
                val boostyResponse = ApiClient.apiService.getBoostySubscribers()
                if (boostyResponse.isSuccessful && boostyResponse.body() != null) {
                    if (DatabaseHelper.insertBoostySubscribers(boostyResponse.body()!!)) {
                        prefs.edit { putLong(BOOSTY_SYNC_TIME_KEY, now) }
                    } else {
                        success = false
                    }
                } else {
                    success = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                FileLog.e(t)
                success = false
            }
        }

        // sync profiles
        if (profilesDue) {
            attempted = true
            try {
                val lastSyncTimestamp = if (fullProfilesSync) null else prefs.getString("lastSyncTimestamp", null)
                val profilesEtag = if (fullProfilesSync) null else prefs.getString("profilesEtag", null)
                val apiService = ApiClient.apiService

                val response = if (lastSyncTimestamp == null) {
                    apiService.getAllProfiles(profilesEtag)
                } else {
                    apiService.getUpdates(lastSyncTimestamp, profilesEtag)
                }

                if (response.code() == 304) {
                    prefs.edit {
                        putString("lastSyncTimestamp", syncWatermark(response))
                        putLong(PROFILES_SYNC_TIME_KEY, now)
                    }
                } else if (response.isSuccessful && response.body() != null) {
                    val profiles = response.body()!!
                    val stored = profiles.isEmpty() || storeProfiles(profiles)
                    if (stored) {
                        prefs.edit {
                            putString("lastSyncTimestamp", syncWatermark(response))
                            val etag = response.headers()["ETag"]
                            if (etag != null) putString("profilesEtag", etag) else remove("profilesEtag")
                            putLong(PROFILES_SYNC_TIME_KEY, now)
                            putInt(PROFILES_SCHEMA_KEY, PROFILES_SCHEMA)
                        }
                    } else {
                        success = false
                    }
                } else {
                    success = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                FileLog.e(t)
                success = false
            }
        }

        if (!ApiClient.requestsEnabled) return@withLock true

        if (attempted) {
            prefs.edit {
                if (success) {
                    remove(SYNC_FAILURE_TIME_KEY)
                } else {
                    putLong(SYNC_FAILURE_TIME_KEY, now)
                }
            }
        }
        success
    }

    private suspend fun storeProfiles(profiles: List<ProfileDTO>): Boolean {
        val (profilesToDelete, profilesToInsert) = profiles.partition { it.deleted == true }
        var stored = true
        if (profilesToDelete.isNotEmpty()) {
            stored = DatabaseHelper.deleteProfiles(profilesToDelete.map { it.id }) && stored
        }
        if (profilesToInsert.isNotEmpty()) {
            stored = DatabaseHelper.insertProfiles(profilesToInsert) && stored
        }
        if (stored) {
            BadgesController.onProfilesSynced(profiles)
        }
        return stored
    }

    @JvmStatic
    fun applyTransfer(fromId: Long, toId: Long) {
        scope.launch {
            syncMutex.withLock {
                try {
                    val origin = ExteraDatabase.getInstance().profileDao().getById(fromId)
                    if (origin != null) {
                        storeProfiles(listOf(origin.copy(deleted = true), origin.copy(id = toId, deleted = false)))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    FileLog.e(t)
                }
                fetchProfiles(fromId, toId)
            }
        }
    }

    @JvmStatic
    fun isWatchingDonation(): Boolean = donationWatch?.isActive == true

    @JvmStatic
    fun watchDonation(userId: Long) {
        donationWatch?.cancel()
        val wasSupporter = BadgesController.isSupporter(userId)
        val couldChangeBadge = BadgesController.canChangeBadge(userId)
        donationWatch = scope.launch {
            val deadline = System.currentTimeMillis() + DONATION_WATCH_DURATION
            while (System.currentTimeMillis() < deadline) {
                delay(DONATION_WATCH_INTERVAL)
                syncMutex.withLock { fetchProfiles(userId) }
                if (BadgesController.isSupporter(userId) != wasSupporter || BadgesController.canChangeBadge(userId) != couldChangeBadge) {
                    break
                }
            }
        }
    }

    private suspend fun fetchProfiles(vararg ids: Long) {
        if (!ApiClient.requestsEnabled) return
        val profiles = ArrayList<ProfileDTO>()
        for (id in ids) {
            try {
                val response = ApiClient.apiService.getProfile(id)
                if (response.isSuccessful) {
                    response.body()?.let(profiles::add)
                } else if (response.code() == 404) {
                    profiles.add(ProfileDTO(id, ProfileType.USER, ProfileStatus.DEFAULT, null, null, true, false))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                FileLog.e(t)
            }
        }
        if (profiles.isNotEmpty()) {
            storeProfiles(profiles)
        }
    }

    private fun syncWatermark(response: Response<*>): String {
        response.headers()[SYNC_TIMESTAMP_HEADER]?.let {
            try {
                return Instant.parse(it).toString()
            } catch (e: DateTimeParseException) {
                FileLog.e(e)
            }
        }
        return Instant.now().toString()
    }

    private fun elapsedSince(now: Long, timestamp: Long): Long {
        return if (timestamp <= 0L || timestamp > now) Long.MAX_VALUE else now - timestamp
    }

    @JvmStatic
    @JvmOverloads
    fun resetSyncState(prefs: SharedPreferences = preferences) {
        prefs.edit {
            remove("lastSyncTimestamp")
            remove("profilesEtag")
            remove(PROFILES_SYNC_TIME_KEY)
            remove(BOOSTY_SYNC_TIME_KEY)
            remove(SYNC_FAILURE_TIME_KEY)
        }
    }

    @JvmStatic
    fun scheduleWorker() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, FAILURE_BACKOFF, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(ApplicationLoader.applicationContext).enqueueUniquePeriodicWork(
                "api_sync_work",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        } catch (t: Throwable) {
            FileLog.e(t)
        }
    }
}