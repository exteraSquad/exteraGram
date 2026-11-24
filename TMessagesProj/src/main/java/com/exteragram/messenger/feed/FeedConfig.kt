/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.feed

import android.app.Activity
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.UserConfig
import java.util.HashSet
import androidx.core.content.edit

private const val CONFIG_NAME = "feedconfig"
private const val KEY_EXCLUDED_CHANNELS = "excludedChannels"
private const val KEY_INCLUDE_ARCHIVED = "includeArchived"
private const val KEY_CATEGORIES = "categories"
private const val KEY_SELECTED_CATEGORY = "selectedCategory"
private const val KEY_NEXT_CATEGORY_ID = "nextCategoryId"
private const val FIRST_CATEGORY_ID = 2

class FeedConfig private constructor(account: Int) {

    data class Snapshot(
        val includeArchived: Boolean,
        val excludedChannels: Set<Long>,
        val categoryChannels: Set<Long>?,
        val generation: Int,
        val baseGeneration: Int,
    ) {
        fun hasSameFilter(other: Snapshot?): Boolean {
            return other != null
                && includeArchived == other.includeArchived
                && categoryChannels == other.categoryChannels
                && (categoryChannels != null || excludedChannels == other.excludedChannels)
        }

        fun isIncluded(dialogId: Long): Boolean {
            return categoryChannels?.contains(dialogId) ?: !excludedChannels.contains(dialogId)
        }
    }

    data class Category(
        val id: Int,
        val title: String,
        val emojiId: Long,
        val channels: Set<Long>,
        val isHidden: Boolean = false,
    )

    private val preferences: SharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences(
        CONFIG_NAME + account,
        Activity.MODE_PRIVATE
    )
    @Volatile
    private var excludedChannels: Set<Long> = emptySet()
    @Volatile
    private var includeArchived = false
    @Volatile
    private var generation = 0
    @Volatile
    private var baseGeneration = 0
    @Volatile
    private var categories: List<Category> = emptyList()
    @Volatile
    private var selectedCategory: Category? = null
    @Volatile
    private var categoriesVersion = 0
    private var nextCategoryId = FIRST_CATEGORY_ID

    init {
        includeArchived = preferences.getBoolean(KEY_INCLUDE_ARCHIVED, false)
        excludedChannels = preferences.getStringSet(KEY_EXCLUDED_CHANNELS, null)
            ?.mapNotNullTo(HashSet()) { it.toLongOrNull() } ?: emptySet()
        categories = readCategories(preferences.getString(KEY_CATEGORIES, null))
        val selectedId = preferences.getInt(KEY_SELECTED_CATEGORY, 0)
        selectedCategory = categories.firstOrNull { it.id == selectedId && !it.isHidden }
        nextCategoryId = maxOf(preferences.getInt(KEY_NEXT_CATEGORY_ID, FIRST_CATEGORY_ID), (categories.maxOfOrNull { it.id } ?: 0) + 1)
    }

    fun isIncludeArchived(): Boolean {
        return includeArchived
    }

    @Synchronized
    fun setIncludeArchived(value: Boolean) {
        if (includeArchived == value) {
            return
        }
        includeArchived = value
        generation++
        baseGeneration++
        preferences.edit { putBoolean(KEY_INCLUDE_ARCHIVED, value) }
    }

    fun isExcluded(dialogId: Long): Boolean {
        return excludedChannels.contains(dialogId)
    }

    fun isShownInFeed(dialogId: Long): Boolean {
        return selectedCategory?.channels?.contains(dialogId) ?: !excludedChannels.contains(dialogId)
    }

    fun getExcludedCount(): Int {
        return excludedChannels.size
    }

    @Synchronized
    fun setExcluded(dialogId: Long, excluded: Boolean) {
        val updated = HashSet(excludedChannels)
        val changed = if (excluded) {
            updated.add(dialogId)
        } else {
            updated.remove(dialogId)
        }
        if (!changed) {
            return
        }
        applyExcluded(updated)
    }

    fun getExcludedSnapshot(): Set<Long> {
        return excludedChannels
    }

    @Synchronized
    fun removeExcluded(ids: Set<Long>) {
        if (ids.isEmpty()) {
            return
        }
        val updated = HashSet(excludedChannels)
        if (!updated.removeAll(ids)) {
            return
        }
        applyExcluded(updated)
    }

    @Synchronized
    fun clearExcluded() {
        if (excludedChannels.isEmpty()) {
            return
        }
        applyExcluded(emptySet())
    }

    @Synchronized
    fun excludeAll(ids: Collection<Long>) {
        val updated = HashSet(excludedChannels)
        if (!updated.addAll(ids)) {
            return
        }
        applyExcluded(updated)
    }

    private fun applyExcluded(updated: Set<Long>) {
        excludedChannels = updated
        if (selectedCategory == null) {
            generation++
        }
        preferences.edit { putStringSet(KEY_EXCLUDED_CHANNELS, updated.mapTo(HashSet()) { it.toString() }) }
    }

    fun getGeneration(): Int {
        return generation
    }

    @Synchronized
    fun snapshot(): Snapshot {
        return Snapshot(includeArchived, excludedChannels, selectedCategory?.channels, generation, baseGeneration)
    }

    fun getCategories(): List<Category> {
        return categories
    }

    fun getVisibleCategories(): List<Category> {
        return categories.filterNot { it.isHidden }
    }

    fun getCategory(id: Int): Category? {
        return categories.firstOrNull { it.id == id }
    }

    fun getSelectedCategory(): Category? {
        return selectedCategory
    }

    fun getSelectedCategoryId(): Int {
        return selectedCategory?.id ?: 0
    }

    fun getCategoriesVersion(): Int {
        return categoriesVersion
    }

    @Synchronized
    fun selectCategory(id: Int): Boolean {
        val category = if (id == 0) null else getCategory(id)?.takeIf { !it.isHidden } ?: return false
        if (category?.id == selectedCategory?.id) {
            return false
        }
        selectedCategory = category
        generation++
        categoriesVersion++
        preferences.edit { putInt(KEY_SELECTED_CATEGORY, category?.id ?: 0) }
        return true
    }

    @Synchronized
    fun saveCategory(id: Int, title: String, emojiId: Long, channels: Set<Long>): Category {
        val categoryId = if (id > 0 && getCategory(id) != null) id else nextCategoryId++
        val category = Category(categoryId, title, emojiId, HashSet(channels), getCategory(categoryId)?.isHidden ?: false)
        val updated = ArrayList(categories)
        val index = updated.indexOfFirst { it.id == categoryId }
        if (index >= 0) {
            updated[index] = category
        } else {
            updated.add(category)
        }
        applyCategories(updated)
        return category
    }

    @Synchronized
    fun setCategoryHidden(id: Int, hidden: Boolean): Boolean {
        val index = categories.indexOfFirst { it.id == id }
        if (index < 0 || categories[index].isHidden == hidden) {
            return false
        }
        val updated = ArrayList(categories)
        updated[index] = categories[index].copy(isHidden = hidden)
        applyCategories(updated)
        return true
    }

    @Synchronized
    fun deleteCategory(id: Int) {
        val updated = categories.filterNot { it.id == id }
        if (updated.size == categories.size) {
            return
        }
        applyCategories(updated)
    }

    @Synchronized
    fun reorderCategories(ids: List<Int>) {
        val byId = categories.associateBy { it.id }
        val updated = ids.mapNotNull { byId[it] }.toMutableList()
        categories.filterTo(updated) { it.id !in ids }
        if (updated == categories) {
            return
        }
        applyCategories(updated)
    }

    @Synchronized
    fun removeFromCategories(ids: Set<Long>) {
        if (ids.isEmpty() || categories.none { category -> category.channels.any { it in ids } }) {
            return
        }
        applyCategories(categories.map { category ->
            if (category.channels.any { it in ids }) category.copy(channels = category.channels - ids) else category
        })
    }

    private fun applyCategories(updated: List<Category>) {
        val previous = selectedCategory
        val selected = previous?.let { current -> updated.firstOrNull { it.id == current.id && !it.isHidden } }
        categories = updated
        selectedCategory = selected
        categoriesVersion++
        if (previous?.id != selected?.id || previous?.channels != selected?.channels) {
            generation++
        }
        preferences.edit {
            putString(KEY_CATEGORIES, writeCategories(updated))
            putInt(KEY_SELECTED_CATEGORY, selected?.id ?: 0)
            putInt(KEY_NEXT_CATEGORY_ID, nextCategoryId)
        }
    }

    private fun readCategories(json: String?): List<Category> {
        if (json.isNullOrEmpty()) {
            return emptyList()
        }
        return try {
            val array = JSONArray(json)
            val result = ArrayList<Category>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val channelsArray = obj.optJSONArray("channels")
                val channels = HashSet<Long>()
                if (channelsArray != null) {
                    for (j in 0 until channelsArray.length()) {
                        channels.add(channelsArray.getLong(j))
                    }
                }
                result.add(Category(obj.getInt("id"), obj.optString("title"), obj.optLong("emoji"), channels, obj.optBoolean("hidden")))
            }
            result
        } catch (e: Exception) {
            FileLog.e(e)
            emptyList()
        }
    }

    private fun writeCategories(list: List<Category>): String {
        val array = JSONArray()
        list.forEach { category ->
            array.put(JSONObject().apply {
                put("id", category.id)
                put("title", category.title)
                put("emoji", category.emojiId)
                put("channels", JSONArray().apply { category.channels.forEach { put(it) } })
                if (category.isHidden) {
                    put("hidden", true)
                }
            })
        }
        return array.toString()
    }

    companion object {
        private val instances = arrayOfNulls<FeedConfig>(UserConfig.MAX_ACCOUNT_COUNT)
        private val lockObjects = Array(UserConfig.MAX_ACCOUNT_COUNT) { Any() }

        @JvmStatic
        fun getInstance(num: Int): FeedConfig {
            var localInstance = instances[num]
            if (localInstance == null) {
                synchronized(lockObjects[num]) {
                    localInstance = instances[num]
                    if (localInstance == null) {
                        localInstance = FeedConfig(num)
                        instances[num] = localInstance
                    }
                }
            }
            return localInstance!!
        }
    }
}
