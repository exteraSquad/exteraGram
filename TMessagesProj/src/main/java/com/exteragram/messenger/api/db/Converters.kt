/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.api.db

import androidx.room.TypeConverter
import com.exteragram.messenger.api.dto.BadgeDTO
import com.exteragram.messenger.api.dto.NowPlayingInfoDTO
import com.google.gson.Gson
import java.math.BigDecimal

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromBadgeDTO(badgeDTO: BadgeDTO?): String? {
        return gson.toJson(badgeDTO)
    }

    @TypeConverter
    fun toBadgeDTO(json: String?): BadgeDTO? {
        return gson.fromJson(json, BadgeDTO::class.java)
    }

    @TypeConverter
    fun fromNowPlayingInfoDTO(nowPlayingInfoDTO: NowPlayingInfoDTO?): String? {
        return gson.toJson(nowPlayingInfoDTO)
    }

    @TypeConverter
    fun toNowPlayingInfoDTO(json: String?): NowPlayingInfoDTO? {
        return gson.fromJson(json, NowPlayingInfoDTO::class.java)
    }

    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? {
        return value?.toString()
    }

    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? {
        return value?.let { BigDecimal(it) }
    }
}