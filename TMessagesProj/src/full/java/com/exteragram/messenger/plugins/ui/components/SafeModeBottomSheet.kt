/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.ui.components

import android.app.Activity
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.exteragram.messenger.PLACEHOLDERS
import com.exteragram.messenger.editor
import com.exteragram.messenger.plugins.PluginsController
import com.exteragram.messenger.pluginsSafeMode
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.StickerImageView
import org.telegram.ui.Stories.recorder.ButtonWithCounterView

class SafeModeBottomSheet(fragment: BaseFragment) :
    BottomSheet(fragment.parentActivity, false, fragment.resourceProvider) {
    init {
        val activity = fragment.parentActivity as Activity

        fixNavigationBar()

        val frameLayout = FrameLayout(activity)
        val linearLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        frameLayout.addView(linearLayout)

        val imageView = StickerImageView(activity, currentAccount).apply {
            setStickerPackName(PLACEHOLDERS)
            setStickerNum(10)
            imageReceiver.setAutoRepeat(1)
        }
        linearLayout.addView(
            imageView,
            LayoutHelper.createLinear(144, 144, Gravity.CENTER_HORIZONTAL, 0f, 16f, 0f, 0f),
        )

        val title = TextView(activity).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText))
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
            typeface = AndroidUtilities.bold()
            text = LocaleController.getString(R.string.PluginsSafeMode)
        }
        linearLayout.addView(
            title,
            LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT.toFloat(),
                0,
                40f,
                20f,
                40f,
                0f
            ),
        )

        val description = TextView(activity).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            typeface = AndroidUtilities.regular()
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
            setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText))
            text = LocaleController.getString(R.string.PluginsSafeModeInfo)
        }
        linearLayout.addView(
            description,
            LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT.toFloat(),
                0,
                21f,
                8f,
                21f,
                0f
            ),
        )

        val button = ButtonWithCounterView(activity, true, resourcesProvider).apply {
            setRound()
            setText(LocaleController.getString(R.string.Disable), false)
            setOnClickListener {
                dismiss()
                editor.putBoolean(
                    "pluginsSafeMode",
                    false.also { pluginsSafeMode = false }).apply()
                PluginsController.getInstance().restart(false)
            }
        }
        linearLayout.addView(
            button,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48f, 0, 16f, 28f, 16f, 16f),
        )

        setCustomView(frameLayout)
    }
}