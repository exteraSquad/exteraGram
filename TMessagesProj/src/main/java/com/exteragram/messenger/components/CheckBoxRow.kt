/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.components

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.CheckBox2
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.ScaleStateListAnimator

@SuppressLint("ViewConstructor")
class CheckBoxRow @JvmOverloads constructor(
    context: Context,
    label: CharSequence,
    checked: Boolean = false,
    resourcesProvider: Theme.ResourcesProvider? = null,
) : LinearLayout(context) {

    private val checkBox = CheckBox2(context, 21, resourcesProvider).apply {
        setColor(
            Theme.key_radioBackgroundChecked,
            Theme.key_checkboxDisabled,
            Theme.key_checkboxCheck
        )
        drawUnchecked = true
        setChecked(checked, false)
        setDrawBackgroundAsArc(10)
    }

    var onCheckedChange: ((Boolean) -> Unit)? = null

    var isChecked: Boolean
        get() = checkBox.isChecked
        set(value) = checkBox.setChecked(value, true)

    init {
        orientation = HORIZONTAL
        setPadding(dp(8f), dp(6f), dp(12f), dp(6f))
        background = Theme.createRadSelectorDrawable(
            Theme.getColor(Theme.key_listSelector, resourcesProvider),
            18,
            18
        )
        ScaleStateListAnimator.apply(this, .05f, 1.2f)

        val checkBoxContainer = FrameLayout(context)
        checkBoxContainer.addView(
            checkBox,
            LayoutHelper.createFrame(21, 21f, Gravity.CENTER, 0f, 0f, 0f, 0f)
        )
        addView(
            checkBoxContainer,
            LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 0, 0, 6, 0)
        )

        val textView = TextView(context).apply {
            setTextColor(
                Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider)
            )
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
            typeface = AndroidUtilities.regular()
            text = label
        }
        addView(
            textView,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL
            )
        )

        setOnClickListener {
            checkBox.setChecked(!checkBox.isChecked, true)
            onCheckedChange?.invoke(checkBox.isChecked)
        }
    }
}