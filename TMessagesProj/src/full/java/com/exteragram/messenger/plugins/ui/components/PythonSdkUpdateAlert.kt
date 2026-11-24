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
import android.view.Gravity
import android.widget.FrameLayout
import com.exteragram.messenger.components.CheckBoxRow
import com.exteragram.messenger.plugins.PythonPluginsEngine
import com.exteragram.messenger.plugins.PythonPluginsEngine.Updater.Companion.PythonSdkUpdateInfo
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.LayoutHelper
import java.io.IOException

class PythonSdkUpdateAlert(
    fragment: BaseFragment,
    private val update: PythonSdkUpdateInfo,
) : PluginUpdateAlert(fragment.parentActivity, update, fragment.currentAccount) {

    internal var preview = false
    private var enableAutoUpdate = false
    private var installStarted = false

    init {
        if (appUpdate.can_not_skip) {
            setCanDismissWithSwipe(false)
            allowNestedScroll = false
            setCanDismissWithTouchOutside(false)
            setCancelable(false)
            setDelegate(object : BottomSheetDelegate() {
                override fun canDismiss(): Boolean = installStarted || isActivityGone()
            })
        }
    }

    private fun isActivityGone(): Boolean {
        val activity = context as? Activity ?: return false
        return activity.isFinishing || activity.isDestroyed
    }

    override fun hasSecondaryButton(): Boolean = false

    override fun getDoneButtonText(): String = LocaleController.getString(R.string.AppUpdateNow)

    override fun getTitleText(): String = LocaleController.getString(R.string.PluginsPySdkUpdate)

    override fun getExtraBottomContentHeight(): Int = AUTO_UPDATE_ROW_HEIGHT + BUTTON_GAP

    override fun addContentAfterDoneButton(container: FrameLayout) {
        val autoUpdateRow = CheckBoxRow(
            context,
            LocaleController.getString(R.string.EnableAutoUpdate),
            resourcesProvider = resourcesProvider,
        )
        autoUpdateRow.onCheckedChange = { enableAutoUpdate = it }
        container.addView(
            autoUpdateRow,
            LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT.toFloat(),
                Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM,
                0f,
                0f,
                0f,
                12f
            ),
        )
    }

    override fun onDone() {
        if (preview) {
            dismiss()
            return
        }
        if (enableAutoUpdate) {
            PythonPluginsEngine.Updater.enableAutoUpdate()
        }
        try {
            PythonPluginsEngine.Updater.savePythonSdkArchive(update, true)
        } catch (e: IOException) {
            FileLog.e(
                "Failed to load python-plugins-sdk file (${update.channel}, message id = ${update.message?.id})",
                e
            )
        }
        installStarted = true
        dismiss()
    }

    companion object {
        private const val AUTO_UPDATE_ROW_HEIGHT = 36
        private const val BUTTON_GAP = 12
    }
}
