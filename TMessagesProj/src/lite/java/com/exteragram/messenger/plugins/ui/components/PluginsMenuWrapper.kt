/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.ui.components

import android.widget.LinearLayout
import com.exteragram.messenger.plugins.hooks.MenuItemRecord
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.PopupSwipeBackLayout

open class PluginsMenuWrapper {
    val swipeBack: LinearLayout

    constructor(
        swipeBackLayout: PopupSwipeBackLayout,
        type: String?,
        contextData: Map<String, Any>?,
        resourcesProvider: Theme.ResourcesProvider?,
    ) {
        swipeBack = LinearLayout(swipeBackLayout.context)
    }

    constructor(
        swipeBackLayout: PopupSwipeBackLayout,
        existingItems: List<MenuItemRecord>?,
        type: String?,
        contextData: Map<String, Any>?,
        resourcesProvider: Theme.ResourcesProvider?,
    ) {
        swipeBack = LinearLayout(swipeBackLayout.context)
    }

    protected open fun closeMenu() {
    }

    open fun rebuildMenu(pluginMenuItems: List<MenuItemRecord>?) {
    }
}