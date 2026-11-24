/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.api.dto

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.exteragram.messenger.api.model.ProfileStatus
import com.exteragram.messenger.api.model.ProfileType

@Entity
data class ProfileDTO(
    @PrimaryKey
    val id: Long,
    val type: ProfileType,
    val status: ProfileStatus,
    val badge: BadgeDTO?,
    val nowPlaying: NowPlayingInfoDTO?,
    val deleted: Boolean?,
    val canChangeBadge: Boolean?,
    val number: Int? = null
)