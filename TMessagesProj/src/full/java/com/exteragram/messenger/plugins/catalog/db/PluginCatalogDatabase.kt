/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.catalog.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.telegram.messenger.ApplicationLoader

@Database(
    entities = [PluginRepositoryEntity::class, CatalogEntryEntity::class, PluginMetadataEntity::class],
    version = 4,
)
abstract class PluginCatalogDatabase : RoomDatabase() {
    abstract fun catalogDao(): PluginCatalogDao

    companion object {
        @Volatile
        private var INSTANCE: PluginCatalogDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE metadata ADD COLUMN categories TEXT")
                db.execSQL("ALTER TABLE metadata ADD COLUMN parsedWithSdk TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN views INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE entries ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE entries SET updatedAt = date")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE `repositories_new` (`repositoryId` INTEGER NOT NULL, `username` TEXT, `title` TEXT, `source` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, `syncedAt` INTEGER NOT NULL, `pluginsCount` INTEGER NOT NULL, `url` TEXT, `homepage` TEXT, PRIMARY KEY(`repositoryId`))")
                db.execSQL("INSERT INTO `repositories_new` (`repositoryId`, `username`, `title`, `source`, `sortOrder`, `addedAt`, `syncedAt`, `pluginsCount`) SELECT `channelId`, `username`, `title`, `source`, `sortOrder`, `addedAt`, `syncedAt`, `pluginsCount` FROM `repositories`")
                db.execSQL("DROP TABLE `repositories`")
                db.execSQL("ALTER TABLE `repositories_new` RENAME TO `repositories`")
                db.execSQL("CREATE TABLE `entries_new` (`repositoryId` INTEGER NOT NULL, `entryId` INTEGER NOT NULL, `date` INTEGER NOT NULL, `documentId` INTEGER NOT NULL, `fileName` TEXT NOT NULL, `size` INTEGER NOT NULL, `caption` TEXT, `message` BLOB, `views` INTEGER NOT NULL DEFAULT 0, `updatedAt` INTEGER NOT NULL DEFAULT 0, `fileUrl` TEXT, `sha256` TEXT, PRIMARY KEY(`repositoryId`, `entryId`))")
                db.execSQL("INSERT INTO `entries_new` (`repositoryId`, `entryId`, `date`, `documentId`, `fileName`, `size`, `caption`, `message`, `views`, `updatedAt`) SELECT `channelId`, `messageId`, `date`, `documentId`, `fileName`, `size`, `caption`, `message`, `views`, `updatedAt` FROM `entries`")
                db.execSQL("DROP TABLE `entries`")
                db.execSQL("ALTER TABLE `entries_new` RENAME TO `entries`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entries_documentId` ON `entries` (`documentId`)")
            }
        }

        fun getInstance(): PluginCatalogDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    ApplicationLoader.applicationContext,
                    PluginCatalogDatabase::class.java,
                    "plugin-catalog"
                ).fallbackToDestructiveMigrationOnDowngrade(true)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
