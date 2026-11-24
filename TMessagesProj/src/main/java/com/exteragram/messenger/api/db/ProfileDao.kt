/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.api.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.exteragram.messenger.api.dto.BadgeDTO
import com.exteragram.messenger.api.dto.NowPlayingInfoDTO
import com.exteragram.messenger.api.dto.ProfileDTO

@Dao
interface ProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<ProfileDTO>)

    @Query("SELECT * FROM ProfileDTO")
    suspend fun getAll(): List<ProfileDTO>

    @Query("SELECT * FROM ProfileDTO WHERE id = :id")
    suspend fun getById(id: Long): ProfileDTO?

    @Query("DELETE FROM ProfileDTO WHERE id IN (:ids)")
    suspend fun deleteProfiles(ids: List<Long>): Int

    @Query("UPDATE ProfileDTO SET nowPlaying = :newNowPlaying WHERE id = :id")
    suspend fun updateNowPlaying(id: Long, newNowPlaying: NowPlayingInfoDTO?): Int

    @Query("UPDATE ProfileDTO SET badge = :badge WHERE id = :id")
    suspend fun updateBadge(id: Long, badge: BadgeDTO?): Int
}