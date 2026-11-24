/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.catalog.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
abstract class PluginCatalogDao {
    @Query("SELECT * FROM repositories ORDER BY source, sortOrder, addedAt")
    abstract fun getRepositories(): List<PluginRepositoryEntity>

    @Query("SELECT * FROM repositories WHERE repositoryId = :repositoryId")
    abstract fun getRepository(repositoryId: Long): PluginRepositoryEntity?

    @Query("SELECT * FROM repositories WHERE url = :url")
    abstract fun getRepositoryByUrl(url: String): PluginRepositoryEntity?

    @Query("SELECT MIN(repositoryId) FROM repositories")
    abstract fun getMinRepositoryId(): Long?

    @Upsert
    abstract fun upsertRepositories(repositories: List<PluginRepositoryEntity>)

    @Query("DELETE FROM repositories WHERE source = :source AND repositoryId NOT IN (:keepIds)")
    abstract fun deleteRepositoriesExcept(source: Int, keepIds: List<Long>)

    @Query("DELETE FROM repositories WHERE repositoryId = :repositoryId")
    abstract fun deleteRepository(repositoryId: Long)

    @Query("UPDATE repositories SET syncedAt = :syncedAt, pluginsCount = :pluginsCount WHERE repositoryId = :repositoryId")
    abstract fun updateSyncState(repositoryId: Long, syncedAt: Long, pluginsCount: Int)

    @Query("UPDATE repositories SET title = :title, homepage = :homepage WHERE repositoryId = :repositoryId")
    abstract fun updateGitInfo(repositoryId: Long, title: String, homepage: String?)

    @Query("UPDATE repositories SET pluginsCount = :pluginsCount WHERE repositoryId = :repositoryId")
    abstract fun updatePluginsCount(repositoryId: Long, pluginsCount: Int)

    @Transaction
    @Query("SELECT * FROM entries WHERE repositoryId = :repositoryId ORDER BY date DESC, entryId DESC")
    abstract fun getEntries(repositoryId: Long): List<CatalogEntryWithMetadata>

    @Query("SELECT * FROM entries WHERE repositoryId = :repositoryId AND documentId NOT IN (SELECT documentId FROM metadata WHERE parsedWithSdk IS :sdkVersion AND error IS NOT :retryError) ORDER BY date DESC, entryId DESC")
    abstract fun getEntriesWithoutMetadata(repositoryId: Long, sdkVersion: String?, retryError: String): List<CatalogEntryEntity>

    @Query("SELECT documentId FROM metadata WHERE error = :error")
    abstract fun getDocumentIdsWithError(error: String): List<Long>

    @Query("SELECT entryId, date, documentId, updatedAt FROM entries WHERE repositoryId = :repositoryId")
    abstract fun getEntryVersions(repositoryId: Long): List<CatalogEntryVersion>

    @Query("SELECT * FROM entries WHERE repositoryId = :repositoryId AND entryId = :entryId")
    abstract fun getEntry(repositoryId: Long, entryId: Int): CatalogEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract fun insertEntries(entries: List<CatalogEntryEntity>)

    @Query("DELETE FROM entries WHERE repositoryId = :repositoryId")
    abstract fun deleteEntries(repositoryId: Long)

    @Upsert
    abstract fun upsertMetadata(metadata: PluginMetadataEntity)

    @Upsert
    abstract fun upsertMetadata(metadata: List<PluginMetadataEntity>)

    @Query("DELETE FROM metadata WHERE documentId NOT IN (SELECT documentId FROM entries)")
    abstract fun pruneMetadata()

    @Query("DELETE FROM entries WHERE repositoryId NOT IN (SELECT repositoryId FROM repositories)")
    protected abstract fun pruneEntries()

    @Transaction
    open fun replaceEntries(repositoryId: Long, entries: List<CatalogEntryEntity>, metadata: List<PluginMetadataEntity>) {
        deleteEntries(repositoryId)
        insertEntries(entries)
        upsertMetadata(metadata)
        pruneMetadata()
    }

    @Transaction
    open fun removeRepository(repositoryId: Long) {
        deleteRepository(repositoryId)
        deleteEntries(repositoryId)
        pruneMetadata()
    }

    @Transaction
    open fun replaceFolderRepositories(repositories: List<PluginRepositoryEntity>) {
        deleteRepositoriesExcept(PluginRepositoryEntity.SOURCE_FOLDER, repositories.map { it.repositoryId })
        upsertRepositories(repositories)
        pruneEntries()
        pruneMetadata()
    }
}
