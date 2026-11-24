/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.ui

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import org.telegram.ui.ActionBar.BaseFragment

class PluginsInfoActivity : BaseFragment() {
    override fun createView(context: Context): View = FrameLayout(context)
}