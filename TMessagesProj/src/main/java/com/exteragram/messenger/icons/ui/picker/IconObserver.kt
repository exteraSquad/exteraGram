/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.icons.ui.picker

import com.exteragram.messenger.editingIconPackId
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.LaunchActivity
import java.util.WeakHashMap

object IconObserver {
    private val iconSources = WeakHashMap<BaseFragment, MutableSet<Int>>()

    fun log(resId: Int) {
        if (editingIconPackId == null) {
            return
        }

        val fragment: BaseFragment = LaunchActivity.getSafeLastFragment() ?: return

        (fragment.parentActivity as? LaunchActivity)?.let {
            IconPickerController.setActive(it, true)
        }

        synchronized(iconSources) {
            val iconSet = iconSources.getOrPut(fragment) {
                HashSet()
            }
            iconSet.add(resId)
        }
    }

    fun removeSource(owner: BaseFragment) {
        synchronized(iconSources) {
            iconSources.remove(owner)
        }
    }

    fun getUsedIcons(): Set<Int> {
        val visibleFragments = LaunchActivity.getVisibleFragments()
        synchronized(iconSources) {
            return visibleFragments.flatMap { fragment ->
                iconSources[fragment] ?: emptySet()
            }.toSet()
        }
    }

    fun clear() {
        synchronized(iconSources) {
            iconSources.clear()
        }
    }
}