/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.ui.components

import android.view.View

interface PluginCellDelegate {
    fun sharePlugin()

    fun openInExternalApp()

    fun deletePlugin()

    fun togglePlugin(view: View)

    fun openPluginSettings()

    fun pinPlugin(view: View)

    fun canOpenInExternalApp(): Boolean
}