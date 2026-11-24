/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.ui.components.templates

import android.content.Context
import android.view.View
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBarMenu
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Components.UniversalFragment

class UniversalFragment(
    var delegate: UniversalFragmentDelegate? = null,
) : UniversalFragment() {

    override fun createView(context: Context): View {
        var view: View? = delegate?.beforeCreateView()
        if (view != null) {
            return view
        }

        view = super.createView(context)
        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                if (id == -1) {
                    if (onBackPressed(true)) {
                        finishFragment()
                    }
                } else {
                    delegate?.onMenuItemClick(id)
                }
            }
        })

        val newView = delegate?.afterCreateView(view)
        return newView ?: view
    }

    fun getActionBarMenu(): ActionBarMenu = actionBar.createMenu()

    override fun getTitle(): CharSequence? = delegate?.getTitle()

    fun setTitle(title: CharSequence?, animated: Boolean, duration: Long) {
        if (animated) {
            actionBar.setTitleAnimated(title, false, if (duration > 0) duration else 300)
            return
        }
        actionBar.setTitle(title)
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        delegate?.fillItems(items, adapter)
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        delegate?.onClick(item, view, position, x, y)
    }

    override fun onLongClick(item: UItem, view: View, position: Int, x: Float, y: Float): Boolean {
        return delegate?.onLongClick(item, view, position, x, y) ?: false
    }

    override fun onFragmentCreate(): Boolean {
        delegate?.onFragmentCreate()
        return super.onFragmentCreate()
    }

    override fun onFragmentDestroy() {
        delegate?.onFragmentDestroy()
        super.onFragmentDestroy()
    }

    override fun onBackPressed(invoked: Boolean): Boolean {
        val result = delegate?.onBackPressed()
        if (result != null) {
            return !result
        }
        return super.onBackPressed(invoked)
    }

    interface UniversalFragmentDelegate {
        fun beforeCreateView(): View? = null

        fun afterCreateView(view: View): View? = view

        fun onFragmentCreate() {}

        fun onFragmentDestroy() {}

        fun onBackPressed(): Boolean? = null

        fun getTitle(): CharSequence? = null

        fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {}

        fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {}

        fun onLongClick(item: UItem, view: View, position: Int, x: Float, y: Float): Boolean = false

        fun onMenuItemClick(id: Int) {}
    }
}