/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.hooks

class EventHookRecord(
    private val pluginId: String?,
    private val hookName: String?,
    private val matchSubstring: Boolean,
    private val priority: Int,
) : HookRecord {
    fun getPluginId(): String? = pluginId

    fun getHookName(): String? = hookName

    fun getPriority(): Int = priority

    fun isMatchSubstring(): Boolean = matchSubstring

    override fun cleanup() {
    }

    override fun matches(criteria: Any?): Boolean {
        val eventName = criteria as? String ?: return false
        val localHookName = hookName ?: return false

        return if (matchSubstring) {
            localHookName.isNotEmpty() && eventName.contains(localHookName)
        } else {
            localHookName == eventName
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        other as EventHookRecord
        return matchSubstring == other.matchSubstring &&
                pluginId == other.pluginId &&
                hookName == other.hookName
    }

    override fun hashCode(): Int {
        var result = pluginId?.hashCode() ?: 0
        result = 31 * result + (hookName?.hashCode() ?: 0)
        result = 31 * result + matchSubstring.hashCode()
        return result
    }
}