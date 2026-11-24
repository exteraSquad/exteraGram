/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.models

import android.view.View
import com.chaquo.python.PyObject
import com.exteragram.messenger.plugins.Plugin
import com.exteragram.messenger.plugins.PluginsConstants
import org.telegram.ui.Components.UItem

class CustomSetting private constructor(
    var onClickCallback: PyObject?,
    var createSubFragmentCallback: PyObject?,
    onLongClickCallback: PyObject?,
    linkAlias: String?,
) : SettingItem(PluginsConstants.Settings.TYPE_CUSTOM, null, onLongClickCallback, linkAlias) {
    var item: UItem? = null

    var factory: Factory<*>? = null

    var factoryArgs: PyObject? = null

    constructor(
        item: UItem,
        onClickCallback: PyObject?,
        createSubFragmentCallback: PyObject?,
        onLongClickCallback: PyObject?,
        linkAlias: String?,
    ) : this(onClickCallback, createSubFragmentCallback, onLongClickCallback, linkAlias) {
        this.item = item
        item.settingItem = this
    }

    constructor(
        factory: Factory<*>,
        onClickCallback: PyObject?,
        createSubFragmentCallback: PyObject?,
        onLongClickCallback: PyObject?,
        linkAlias: String?,
    ) : this(onClickCallback, createSubFragmentCallback, onLongClickCallback, linkAlias) {
        this.factory = factory
    }

    constructor(
        factory: Factory<*>,
        factoryArgs: PyObject?,
        onClickCallback: PyObject?,
        createSubFragmentCallback: PyObject?,
        onLongClickCallback: PyObject?,
        linkAlias: String?,
    ) : this(factory, onClickCallback, createSubFragmentCallback, onLongClickCallback, linkAlias) {
        this.factoryArgs = factoryArgs
    }

    constructor(
        view: View,
        onClickCallback: PyObject?,
        createSubFragmentCallback: PyObject?,
        onLongClickCallback: PyObject?,
        linkAlias: String?,
    ) : this(
        UItem.asCustom(view),
        onClickCallback,
        createSubFragmentCallback,
        onLongClickCallback,
        linkAlias
    )

    override fun cleanup() {
        super.cleanup()
        closeCallback(onClickCallback)
        closeCallback(createSubFragmentCallback)
        closeCallback(factoryArgs)
        onClickCallback = null
        createSubFragmentCallback = null
        factoryArgs = null
    }

    abstract class Factory<V : View> : UItem.UItemFactory<V>() {
        var isShadowValue = false

        var isClickableValue = true

        open fun create(plugin: Plugin, setting: CustomSetting, args: PyObject?): UItem? = null

        open fun onClick(plugin: Plugin, item: UItem, view: View) {
        }

        open fun onLongClick(plugin: Plugin, item: UItem, view: View) {
        }

        override fun isShadow(): Boolean = isShadowValue

        override fun isClickable(): Boolean = isClickableValue
    }
}