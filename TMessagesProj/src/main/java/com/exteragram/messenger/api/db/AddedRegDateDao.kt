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
import com.exteragram.messenger.api.dto.AddedRegDateDTO

@Dao
interface AddedRegDateDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(addedRegDate: AddedRegDateDTO)

    @Query("SELECT EXISTS(SELECT 1 FROM AddedRegDateDTO WHERE userId = :userId)")
    suspend fun isAdded(userId: Long): Boolean
}
