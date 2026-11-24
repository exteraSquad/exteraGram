/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.api.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.exteragram.messenger.api.ApiController
import com.exteragram.messenger.api.dto.AddedRegDateDTO
import com.exteragram.messenger.api.dto.BoostySubscriberDTO
import com.exteragram.messenger.api.dto.ProfileDTO
import org.telegram.messenger.ApplicationLoader

@Database(entities = [ProfileDTO::class, AddedRegDateDTO::class, BoostySubscriberDTO::class], version = 6)
@TypeConverters(Converters::class)
abstract class ExteraDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun addedRegDateDao(): AddedRegDateDao
    abstract fun boostySubscriberDao(): BoostySubscriberDao

    companion object {
        @Volatile
        private var INSTANCE: ExteraDatabase? = null

        private class Callback : RoomDatabase.Callback() {
            override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                super.onDestructiveMigration(db)
                ApiController.resetSyncState()
            }
        }

        fun getInstance(): ExteraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    ApplicationLoader.applicationContext,
                    ExteraDatabase::class.java,
                    "extera-database"
                ).fallbackToDestructiveMigration(true)
                    .addCallback(Callback())
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}