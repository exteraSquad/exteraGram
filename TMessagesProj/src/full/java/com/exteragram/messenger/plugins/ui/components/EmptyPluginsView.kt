/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.EffectsTextView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.ViewHelper
import org.telegram.ui.Stories.recorder.ButtonWithCounterView

@SuppressLint("ClickableViewAccessibility")
class EmptyPluginsView @JvmOverloads constructor(
    context: Context,
    resourcesProvider: Theme.ResourcesProvider? = null,
) : FrameLayout(context) {
    private val textView: EffectsTextView
    private val titleView: TextView
    private val button: ButtonWithCounterView
    val backupImageView: BackupImageView

    init {
        val textViewLayout = LinearLayout(context).apply {
            ViewHelper.setPadding(this, 20f, 0f, 20f, 0f)
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            orientation = LinearLayout.VERTICAL
        }

        backupImageView = BackupImageView(context)
        textViewLayout.addView(
            backupImageView,
            LayoutHelper.createLinear(100, 100, Gravity.CENTER, 0f, 0f, 0f, 20f),
        )

        titleView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider))
            typeface = AndroidUtilities.bold()
            gravity = Gravity.CENTER
            visibility = GONE
        }
        textViewLayout.addView(
            titleView,
            LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0f, 0f, 0f, 8f),
        )

        textView = EffectsTextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
            setTextColor(Theme.getColor(Theme.key_emptyListPlaceholder, resourcesProvider))
            gravity = Gravity.CENTER_HORIZONTAL
            text = LocaleController.getString(R.string.NoResult)
            typeface = AndroidUtilities.regular()
            maxWidth = AndroidUtilities.dp(272f)
            setLineSpacing(AndroidUtilities.dp(2f).toFloat(), 1f)
            movementMethod = AndroidUtilities.LinkMovementMethodMy()
            setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText))
        }

        textViewLayout.addView(
            textView,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER
            ),
        )

        button = ButtonWithCounterView(context, resourcesProvider).setRound().apply {
            visibility = GONE
        }
        textViewLayout.addView(
            button,
            LayoutHelper.createLinear(256, 48, Gravity.CENTER, 0f, 20f, 0f, 0f),
        )
        addView(
            textViewLayout,
            LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER
            ),
        )

        setOnTouchListener { _, _ -> true }
    }

    fun setText(text: CharSequence?) {
        textView.text = text
    }

    fun setTitle(text: CharSequence?) {
        titleView.text = text
        titleView.visibility = if (text.isNullOrEmpty()) GONE else VISIBLE
    }

    fun setButton(text: CharSequence?, onClick: (() -> Unit)?) {
        if (text.isNullOrEmpty() || onClick == null) {
            button.visibility = GONE
            button.setOnClickListener(null)
            return
        }
        button.setText(text, false)
        button.setOnClickListener { onClick() }
        button.visibility = VISIBLE
    }

    override fun hasOverlappingRendering(): Boolean = false
}