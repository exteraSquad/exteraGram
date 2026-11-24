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
import com.exteragram.messenger.plugins.PluginsConstants

class EditTextSetting(
    var key: String?,
    var hint: String?,
    var defaultValue: String?,
    var multiline: Boolean,
    var maxLength: Int,
    var mask: String?,
    var onChangeCallback: PyObject?,
) : SettingItem(PluginsConstants.Settings.TYPE_EDIT_TEXT) {
    override fun cleanup() {
        super.cleanup()
        closeCallback(onChangeCallback)
        onChangeCallback = null
    }
}