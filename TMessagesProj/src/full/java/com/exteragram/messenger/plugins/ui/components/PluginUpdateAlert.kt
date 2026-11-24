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
import com.exteragram.messenger.updater.UpdateAppAlertDialog
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.StickerImageView

abstract class PluginUpdateAlert protected constructor(
    activity: Activity,
    update: TLRPC.TL_help_appUpdate,
    account: Int,
) : UpdateAppAlertDialog(activity, update, account) {

    init {
        linearLayout.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0)
        if (update.sticker == null) {
            val sticker = StickerImageView(activity, account).apply {
                setStickerPackName("UtyaDuckFull")
                setStickerNum(95)
                imageReceiver.setAutoRepeat(1)
            }
            linearLayout.addView(
                sticker,
                0,
                LayoutHelper.createLinear(144, 144, Gravity.CENTER_HORIZONTAL, 0f, 28f, 0f, 0f),
            )
        }

        textView.setSingleLine(false)
        textView.ellipsize = null
        textView.gravity = Gravity.CENTER_HORIZONTAL
        textView.restyle(18f, Theme.key_windowBackgroundWhiteBlackText, 40f, 16f, 0f)

        messageTextView.typeface = AndroidUtilities.regular()
        messageTextView.restyle(14f, Theme.key_windowBackgroundWhiteGrayText, 21f, 4f, 0f)

        changelogTextView.typeface = AndroidUtilities.regular()
        changelogTextView.gravity = Gravity.START
        changelogTextView.restyle(15f, Theme.key_windowBackgroundWhiteBlackText, 28f, 20f + 14f, 12f)
    }

    override fun createButtonLayoutParams(bottomMargin: Int): FrameLayout.LayoutParams =
        super.createButtonLayoutParams(bottomMargin + 4).apply {
            val horizontal = backgroundPaddingLeft + dp(12f)
            leftMargin = horizontal
            rightMargin = horizontal
        }

    private fun TextView.restyle(size: Float, colorKey: Int, horizontal: Float, top: Float, bottom: Float) {
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, size)
        setTextColor(getThemedColor(colorKey))
        layoutParams = (layoutParams as LinearLayout.LayoutParams).apply {
            width = LayoutHelper.MATCH_PARENT
            setMargins(dp(horizontal), dp(top), dp(horizontal), dp(bottom))
        }
    }
}
