/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.catalog

import com.exteragram.messenger.plugins.catalog.db.CatalogEntryWithMetadata
import com.exteragram.messenger.plugins.catalog.db.PluginMetadataEntity

class CatalogPlugin(
    val repositoryId: Long,
    val entryId: Int,
    val date: Int,
    val updatedAt: Int,
    val documentId: Long,
    val fileName: String,
    val size: Long,
    val caption: String?,
    val metadata: PluginMetadataEntity?,
    val views: Int,
    val publishedAt: Int,
) {
    val pluginId: String?
        get() = metadata?.takeIf { it.error == null }?.pluginId

    val isPending: Boolean
        get() = metadata == null

    val isFailed: Boolean
        get() = metadata?.error != null

    val categories: List<PluginCategory>
        get() = PluginCategory.parse(metadata?.takeIf { it.error == null }?.categories)

    companion object {
        fun from(row: CatalogEntryWithMetadata): CatalogPlugin = row.entry.run {
            CatalogPlugin(repositoryId, entryId, date, updatedAt, documentId, fileName, size, caption, row.metadata, views, date)
        }

        fun latestVersions(plugins: List<CatalogPlugin>): List<CatalogPlugin> {
            val sorted = plugins.sortedWith(compareByDescending<CatalogPlugin> { it.updatedAt }.thenByDescending { it.entryId })
            val readyFileNames = sorted.filter { it.pluginId != null }.mapTo(HashSet()) { it.fileName.lowercase() }
            val versions = LinkedHashMap<String, ArrayList<CatalogPlugin>>()
            for (plugin in sorted) {
                val pluginId = plugin.pluginId
                val key = when {
                    pluginId != null -> pluginId
                    readyFileNames.contains(plugin.fileName.lowercase()) -> continue
                    else -> "file:" + plugin.fileName.lowercase()
                }
                versions.getOrPut(key) { ArrayList() }.add(plugin)
            }
            return versions.values.map { group ->
                group.first().run {
                    CatalogPlugin(repositoryId, entryId, date, updatedAt, documentId, fileName, size, caption, metadata, group.maxOf { it.views }, group.minOf { it.date })
                }
            }
        }
    }
}
