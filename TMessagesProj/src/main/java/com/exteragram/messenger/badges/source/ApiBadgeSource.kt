/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.badges.source

import com.exteragram.messenger.api.db.ProfileDao
import com.exteragram.messenger.api.dto.BadgeDTO
import com.exteragram.messenger.api.dto.ProfileDTO
import com.exteragram.messenger.api.model.ProfileStatus
import java.util.concurrent.ConcurrentHashMap

private data class BadgeInfo(
    val badge: BadgeDTO?,
    val status: ProfileStatus,
    val canChangeBadge: Boolean,
    val number: Int?
)

class ApiBadgeSource(private val profileDao: ProfileDao) : BadgeSource {

    private val cache = ConcurrentHashMap<Long, BadgeInfo>()

    override fun getBadge(id: Long, isUser: Boolean): BadgeDTO? {
        return cache[id]?.badge
    }

    fun isDeveloper(id: Long): Boolean {
        return cache[id]?.status == ProfileStatus.DEVELOPER
    }

    fun getStatus(id: Long): ProfileStatus? {
        return cache[id]?.status
    }

    fun getNumber(id: Long): Int? {
        return cache[id]?.number
    }

    fun canChangeBadge(id: Long): Boolean {
        return cache[id]?.canChangeBadge == true || isDeveloper(id)
    }

    suspend fun updateLocalBadge(id: Long, badge: BadgeDTO?) {
        cache.computeIfPresent(id) { _, info ->
            info.copy(badge = badge)
        }
        profileDao.updateBadge(id, badge)
    }

    suspend fun loadToCache() {
        val profiles = profileDao.getAll()
        for (profile in profiles) {
            cache.putIfAbsent(profile.id, profile.toBadgeInfo())
        }
    }

    fun applySync(profiles: List<ProfileDTO>) {
        for (profile in profiles) {
            if (profile.deleted == true) {
                cache.remove(profile.id)
            } else {
                cache[profile.id] = profile.toBadgeInfo()
            }
        }
    }

    private fun ProfileDTO.toBadgeInfo() = BadgeInfo(badge, status, canChangeBadge == true, number)

    fun invalidateCache() {
        cache.clear()
    }
}