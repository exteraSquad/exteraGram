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
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.BotWebViewVibrationEffect
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RLottieImageView
import org.telegram.ui.Stories.recorder.ButtonWithCounterView

class UnknownSourcesBottomSheet(fragment: BaseFragment, onConfirm: () -> Unit) :
    BottomSheet(fragment.parentActivity, false, fragment.resourceProvider) {
    init {
        val activity = fragment.parentActivity as Activity

        fixNavigationBar()

        val frameLayout = FrameLayout(activity)
        val linearLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        frameLayout.addView(linearLayout)

        val imageView = RLottieImageView(activity).apply {
            colorFilter = PorterDuffColorFilter(getThemedColor(Theme.key_chats_actionIcon), PorterDuff.Mode.SRC_IN)
            setAnimation(R.raw.ic_ban, 50, 50)
            playAnimation()
            scaleType = ImageView.ScaleType.CENTER
            setPadding(dp(3f), 0, 0, dp(3f))
            background = Theme.createCircleDrawable(
                dp(80f),
                getThemedColor(Theme.key_windowBackgroundWhiteValueText)
            )
        }
        linearLayout.addView(
            imageView,
            LayoutHelper.createLinear(80, 80, Gravity.CENTER, 0f, 14f, 0f, 0f),
        )

        val title = TextView(activity).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText))
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
            typeface = AndroidUtilities.bold()
            text = LocaleController.getString(R.string.PluginsUnknownSources)
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
            text = AndroidUtilities.replaceTags(
                LocaleController.getString(R.string.PluginsUnknownSourcesInfo)
            )
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

        linearLayout.addView(
            makeHint(
                activity,
                R.drawable.msg_permissions,
                R.string.PluginsUnknownSourcesAccount,
                R.string.PluginsUnknownSourcesAccountInfo,
            ),
            LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT.toFloat(),
                0,
                0f,
                20f,
                0f,
                0f
            ),
        )
        linearLayout.addView(
            makeHint(
                activity,
                R.drawable.msg_folders,
                R.string.PluginsUnknownSourcesFiles,
                R.string.PluginsUnknownSourcesFilesInfo,
            ),
            LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT.toFloat(),
                0,
                0f,
                16f,
                0f,
                0f
            ),
        )
        linearLayout.addView(
            makeHint(
                activity,
                R.drawable.msg_language,
                R.string.PluginsUnknownSourcesCode,
                R.string.PluginsUnknownSourcesCodeInfo,
            ),
            LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT.toFloat(),
                0,
                0f,
                16f,
                0f,
                0f
            ),
        )

        val red = getThemedColor(Theme.key_text_RedBold)
        val warning = TextView(activity).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            typeface = AndroidUtilities.bold()
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
            setTextColor(red)
            setPadding(dp(21f), dp(10f), dp(21f), dp(10f))
            background = Theme.createRoundRectDrawable(
                dp(12f),
                Theme.multAlpha(red, if (Theme.isCurrentThemeDark()) .2f else .15f)
            )
            text = LocaleController.getString(R.string.PluginsUnknownSourcesWarning)
        }
        linearLayout.addView(
            warning,
            LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT.toFloat(),
                0,
                16f,
                20f,
                16f,
                0f
            ),
        )

        val cancel = ButtonWithCounterView(activity, true, resourcesProvider).apply {
            setRound()
            setText(LocaleController.getString(R.string.Cancel), false)
            setOnClickListener { dismiss() }
        }
        linearLayout.addView(
            cancel,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48f, 0, 16f, 15f, 16f, 8f),
        )

        val allow = ButtonWithCounterView(activity, false, resourcesProvider).apply {
            setRound()
            setNeutral()
            setText(LocaleController.getString(R.string.Allow), false)
        }
        linearLayout.addView(
            allow,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48f, 0, 16f, 0f, 16f, 0f),
        )

        allow.setTimer(READ_TIME, null)
        allow.setOnClickListener {
            if (allow.isTimerActive) {
                AndroidUtilities.shakeViewSpring(allow, 3f)
                BotWebViewVibrationEffect.APP_ERROR.vibrate()
                return@setOnClickListener
            }
            dismiss()
            onConfirm()
        }

        val scrollView = ScrollView(activity)
        scrollView.addView(frameLayout)
        setCustomView(scrollView)
    }

    private fun makeHint(context: Context, iconResId: Int, title: Int, info: Int): View {
        val hint = FrameLayout(context)

        val imageView = ImageView(context).apply {
            setImageResource(iconResId)
            setColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText))
        }
        hint.addView(
            imageView,
            LayoutHelper.createFrameRelatively(
                ICON_SIZE,
                ICON_SIZE,
                Gravity.START or Gravity.TOP,
                ITEM_HORIZONTAL_PADDING,
                6f,
                0f,
                0f
            ),
        )

        val textLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val titleView = TextView(context).apply {
            gravity = LayoutHelper.getAbsoluteGravityStart()
            typeface = AndroidUtilities.bold()
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
            setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText))
            text = LocaleController.getString(title)
        }
        textLayout.addView(
            titleView,
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT),
        )
        val infoView = TextView(context).apply {
            gravity = LayoutHelper.getAbsoluteGravityStart()
            typeface = AndroidUtilities.regular()
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
            setTextColor(getThemedColor(Theme.key_dialogTextGray3))
            setLineSpacing(dp(2f).toFloat(), 1f)
            text = LocaleController.getString(info)
        }
        textLayout.addView(
            infoView,
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                0f,
                2f,
                0f,
                0f
            ),
        )
        hint.addView(
            textLayout,
            LayoutHelper.createFrameRelatively(
                LayoutHelper.MATCH_PARENT.toFloat(),
                LayoutHelper.WRAP_CONTENT.toFloat(),
                Gravity.START or Gravity.TOP,
                ITEM_TEXT_PADDING,
                0f,
                ITEM_HORIZONTAL_PADDING,
                0f
            ),
        )

        return hint
    }

    companion object {
        private const val ITEM_HORIZONTAL_PADDING = 27f
        private const val ITEM_TEXT_PADDING = 68f
        private const val ICON_SIZE = 24f
        private const val READ_TIME = 30
    }
}
