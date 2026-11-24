/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.ui.components

import org.telegram.messenger.MessageObject
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet

class InstallPluginBottomSheet(
    fragment: BaseFragment,
    needFocus: Boolean,
) : BottomSheet(fragment.parentActivity, needFocus) {
    class PluginInstallParams @JvmOverloads constructor(
        val filePath: String,
        @Suppress("UNUSED_PARAMETER") trusted: Boolean = false,
    ) {
        val trusted: Boolean = false

        companion object {
            @JvmStatic
            fun of(messageObject: MessageObject?): PluginInstallParams = PluginInstallParams("")

            @JvmStatic
            fun untrusted(filePath: String): PluginInstallParams = PluginInstallParams(filePath)
        }
    }
}