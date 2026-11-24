/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.catalog.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "repositories")
data class PluginRepositoryEntity(
    @PrimaryKey val repositoryId: Long,
    val username: String?,
    val title: String?,
    val source: Int,
    val sortOrder: Int,
    val addedAt: Long,
    val syncedAt: Long = 0,
    val pluginsCount: Int = 0,
    val url: String? = null,
    val homepage: String? = null,
) {
    val isGit: Boolean
        get() = url != null

    companion object {
        const val SOURCE_FOLDER = 0
        const val SOURCE_USER = 1

        fun isGitId(repositoryId: Long): Boolean = repositoryId < 0
    }
}

@Entity(
    tableName = "entries",
    primaryKeys = ["repositoryId", "entryId"],
    indices = [Index("documentId")],
)
class CatalogEntryEntity(
    val repositoryId: Long,
    val entryId: Int,
    var date: Int,
    val documentId: Long,
    val fileName: String,
    val size: Long,
    val caption: String?,
    val message: ByteArray?,
    @ColumnInfo(defaultValue = "0") val views: Int = 0,
    @ColumnInfo(defaultValue = "0") var updatedAt: Int = date,
    val fileUrl: String? = null,
    val sha256: String? = null,
)

class CatalogEntryVersion(
    val entryId: Int,
    val date: Int,
    val documentId: Long,
    val updatedAt: Int,
)

@Entity(tableName = "metadata")
data class PluginMetadataEntity(
    @PrimaryKey val documentId: Long,
    val pluginId: String?,
    val name: String?,
    val author: String?,
    val description: String?,
    val version: String?,
    val icon: String?,
    val appVersion: String?,
    val sdkVersion: String?,
    val requirements: String?,
    val categories: String?,
    val error: String?,
    val parsedAt: Long,
    val parsedWithSdk: String?,
)

class CatalogEntryWithMetadata(
    @Embedded val entry: CatalogEntryEntity,
    @Relation(parentColumn = "documentId", entityColumn = "documentId")
    val metadata: PluginMetadataEntity?,
)
