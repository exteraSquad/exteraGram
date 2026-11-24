/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.preferences

import android.view.View
import com.exteragram.messenger.preferences.utils.SettingsRegistry
import com.exteragram.messenger.utils.text.LocaleUtils
import org.telegram.messenger.LocaleController.getString
import org.telegram.ui.Cells.TextCheckCell2
import org.telegram.ui.Components.UItem
import java.util.function.BooleanSupplier
import kotlin.reflect.KMutableProperty0

class SwitchGroup @JvmOverloads constructor(
    private val fragment: BasePreferencesActivity,
    private val id: Int,
    private val titleRes: Int = 0,
    private val title: CharSequence? = null,
    private val linkAlias: String? = null,
    private val searchable: Boolean = true,
) {
    @JvmOverloads
    constructor(
        fragment: BasePreferencesActivity,
        enum: Enum<*>,
        titleRes: Int = 0,
        title: CharSequence? = null,
        linkAlias: String? = null,
        searchable: Boolean = true,
    ) : this(fragment, enum.ordinal + 1, titleRes, title, linkAlias, searchable)

    private val children = mutableListOf<Child>()

    private var onChanged: (() -> Unit)? = null
    private var expanded = false

    val isExpanded: Boolean
        get() = expanded

    fun onChanged(runnable: () -> Unit) = apply { onChanged = runnable }

    fun markNew(alias: String) = apply { children.last().newFeatureAlias = alias }

    fun add(
        enum: Enum<*>,
        text: CharSequence?,
        property: KMutableProperty0<Boolean>,
    ) = add(Child(enum.ordinal + 1, 0, text, null, { property.get() }, { property.set(it) }))

    fun add(
        enum: Enum<*>,
        textRes: Int = 0,
        property: KMutableProperty0<Boolean>,
        text: CharSequence? = null,
    ) = add(Child(enum.ordinal + 1, textRes, text, null, { property.get() }, { property.set(it) }))

    fun add(
        enum: Enum<*>,
        textRes: Int = 0,
        getter: BooleanSupplier,
        setter: Setter,
        text: CharSequence? = null,
    ) = add(Child(enum.ordinal + 1, textRes, text, null, getter::getAsBoolean, setter::set))

    fun addIf(
        visible: BooleanSupplier,
        enum: Enum<*>,
        text: CharSequence?,
        property: KMutableProperty0<Boolean>,
    ) = add(Child(enum.ordinal + 1, 0, text, visible::getAsBoolean, { property.get() }, { property.set(it) }))

    fun addIf(
        visible: BooleanSupplier,
        enum: Enum<*>,
        textRes: Int = 0,
        property: KMutableProperty0<Boolean>,
        text: CharSequence? = null,
    ) = add(Child(enum.ordinal + 1, textRes, text, visible::getAsBoolean, { property.get() }, { property.set(it) }))

    fun addIf(
        visible: BooleanSupplier,
        enum: Enum<*>,
        textRes: Int = 0,
        getter: BooleanSupplier,
        setter: Setter,
        text: CharSequence? = null,
    ) = add(Child(enum.ordinal + 1, textRes, text, visible::getAsBoolean, getter::getAsBoolean, setter::set))

    fun fill(items: ArrayList<UItem>) {
        val enabled = count(enabledOnly = true)
        val total = count(enabledOnly = false)
        val item = UItem.asExteraExpandableSwitch(
            id, title(), "%d/%d".format(enabled, total)
        ) { view -> onSwitchClick(view) }.apply {
            setChecked(enabled > 0)
            setCollapsed(!expanded)
            if (searchable) setSearchable(fragment)
            linkAlias?.let { setLinkAlias(it, fragment) }
        }
        items.add(item)

        if (!expanded) return
        for (child in children) {
            if (!child.isVisible())
                continue

            val checkbox = UItem.asRoundCheckbox(child.id, child.text()).apply {
                setChecked(child.getter()).pad()
            }
            items.add(checkbox)
        }
    }

    fun onClick(item: UItem) {
        if (item.id == id) {
            expanded = !expanded
            item.setCollapsed(!expanded)
            fragment.listView.adapter.update(true)
            return
        }
        val child = children.firstOrNull { it.id == item.id } ?: return
        fragment.toggleBooleanSettingAndRefresh(item) { checked -> child.setter(checked) }
        onChanged?.invoke()
    }

    private fun add(child: Child) = apply { children.add(child) }

    private fun onSwitchClick(view: View) {
        val item = fragment.listView.findItemByItemId((view as TextCheckCell2).id)
        val checked = !item.checked

        for (child in children) {
            if (child.isVisible()) child.setter(checked)
        }

        item.setChecked(checked)
        fragment.listView.adapter.update(true)
        onChanged?.invoke()
    }

    private fun title(): CharSequence = title ?: getString(titleRes)

    private fun count(enabledOnly: Boolean) =
        children.count { it.isVisible() && (!enabledOnly || it.getter()) }

    fun interface Setter {
        fun set(checked: Boolean)
    }

    private class Child(
        val id: Int,
        val textRes: Int,
        val text: CharSequence?,
        val visible: (() -> Boolean)?,
        val getter: () -> Boolean,
        val setter: (Boolean) -> Unit,
    ) {
        var newFeatureAlias: String? = null

        fun isVisible() = visible?.invoke() ?: true

        fun text(): CharSequence {
            val value = text ?: getString(textRes)
            val alias = newFeatureAlias
            return if (alias != null && SettingsRegistry.markAsNewFeature(alias)) {
                LocaleUtils.applyNewSpan(value)
            } else {
                value
            }
        }
    }
}