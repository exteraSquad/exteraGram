/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.models

import com.chaquo.python.PyObject
import com.exteragram.messenger.preferences.utils.SettingsRegistry

abstract class SettingItem protected constructor(
    var type: String,
    var icon: String? = null,
    var onLongClickCallback: PyObject? = null,
    var linkAlias: String? = null,
) {
    protected fun closeCallback(callback: PyObject?) {
        try {
            callback?.close()
        } catch (_: Exception) {
        }
    }

    open fun cleanup() {
        closeCallback(onLongClickCallback)
        onLongClickCallback = null
    }

    fun getLink(pluginId: String?, prefix: String?): String? {
        if (linkAlias.isNullOrEmpty() || pluginId.isNullOrEmpty()) {
            return null
        }

        val setting = if (prefix == null) linkAlias else "$prefix:$linkAlias"
        return "https://t.me/${SettingsRegistry.deeplinkPrefix}?" +
                "${SettingsRegistry.deeplinkPluginIdQueryKey}=$pluginId&" +
                "${SettingsRegistry.deeplinkSettingQueryKey}=$setting"
    }
}