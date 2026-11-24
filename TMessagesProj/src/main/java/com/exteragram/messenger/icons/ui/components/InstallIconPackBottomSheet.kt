/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.icons.ui.components

import android.content.Context
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.StrikethroughSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.exteragram.messenger.components.CheckBoxRow
import com.exteragram.messenger.icons.iconPacksLayout
import com.exteragram.messenger.icons.IconManager
import com.exteragram.messenger.icons.IconPack
import com.exteragram.messenger.utils.text.LocaleUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.Utilities
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.EffectsTextView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.LaunchActivity
import org.telegram.ui.Stories.recorder.ButtonWithCounterView

class InstallIconPackBottomSheet(
    context: Context,
    private val iconPack: IconPack,
    private val installDelegate: InstallDelegate
) : BottomSheet(context, false) {

    fun interface InstallDelegate {
        fun onInstall(enable: Boolean, update: Boolean)
    }

    init {
        fixNavigationBar()
        setCustomView(createView(context))
        setOnDismissListener(Runnable {
            AndroidUtilities.runOnUIThread({
                Utilities.globalQueue.postRunnable {
                    iconPack.location?.deleteRecursively()
                }
            }, 1000)
        })
    }

    private fun createView(context: Context): View {
        val container = LinearLayout(context)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(0, AndroidUtilities.dp(16f), 0, 0)

        val previewView = IconPackPreviewView(context)
        previewView.setCircularMode(true)
        previewView.setRefreshTime(3000)
        previewView.setIconPack(iconPack)

        container.addView(
            previewView,
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL,
                0,
                0,
                0,
                16
            )
        )

        val titleView = TextView(context)
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
        titleView.typeface = AndroidUtilities.bold()
        titleView.gravity = Gravity.CENTER
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
        titleView.text = iconPack.name
        container.addView(
            titleView,
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                24f,
                0f,
                24f,
                0f
            )
        )

        val fragment = LaunchActivity.getSafeLastFragment()
        val installedPack = IconManager.findPackById(iconPack.id)
        val isUpdate = installedPack != null
        val subtitleView = EffectsTextView(context, resourcesProvider)
        subtitleView.gravity = Gravity.CENTER_HORIZONTAL
        subtitleView.typeface = AndroidUtilities.regular()
        subtitleView.movementMethod = AndroidUtilities.LinkMovementMethodMy()
        subtitleView.setLinkTextColor(getThemedColor(Theme.key_dialogTextLink))
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
        subtitleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText))
        var subtitle =
            SpannableStringBuilder(LocaleController.getString(R.string.PluginVersion)).append(" ")
        val versionLength = subtitle.length
        if (installedPack != null) {
            val oldVersion = installedPack.version
            val newVersion = iconPack.version
            subtitle.append(oldVersion).append(" -> ").append(newVersion)
            subtitle = LocaleUtils.replaceArrows(context, subtitle, R.drawable.msg_mini_arrow_mediathin)
            subtitle.setSpan(
                StrikethroughSpan(),
                versionLength,
                versionLength + oldVersion.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        } else if (!TextUtils.isEmpty(iconPack.version)) {
            subtitle.append(iconPack.version)
        }
        if (!TextUtils.isEmpty(iconPack.author)) {
            subtitle.append(" • ")
                .append(
                    LocaleUtils.formatWithUsernames(
                        iconPack.author,
                        fragment,
                        ::dismiss
                    )
                )
        }
        subtitleView.text = subtitle
        container.addView(
            subtitleView,
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                24f,
                4f,
                24f,
                24f
            )
        )

        val enableAfterInstallRow: CheckBoxRow?

        val button = ButtonWithCounterView(context, true, resourcesProvider)
        button.setRound()
        button.setCount(
            LocaleController.formatPluralString("IconCount", iconPack.icons.size),
            false
        )
        if (isUpdate) {
            button.setText(LocaleController.getString(R.string.UpdatePack), false)
        } else {
            button.setText(LocaleController.getString(R.string.InstallPack), false)
        }
        container.addView(
            button,
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 16, 0, 16, 16)
        )

        if (!iconPacksLayout.contains(iconPack.id)) {
            enableAfterInstallRow = CheckBoxRow(
                context,
                LocaleController.getString(R.string.EnableAfterInstallation),
                true,
                resourcesProvider = resourcesProvider,
            )
            container.addView(
                enableAfterInstallRow,
                LayoutHelper.createLinear(
                    LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL,
                    0,
                    0,
                    0,
                    8
                )
            )
        } else {
            enableAfterInstallRow = null
        }

        button.setOnClickListener {
            dismiss()
            AndroidUtilities.runOnUIThread({
                installDelegate.onInstall(enableAfterInstallRow?.isChecked == true, isUpdate)
            }, 200)
        }

        return container
    }
}
