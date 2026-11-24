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
import android.widget.FrameLayout
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC

internal class AnalyzerUpdateAlert(
    activity: Activity,
    account: Int,
    analyzerVersion: String,
    updateDocument: TLRPC.Document,
    private val onConfirm: () -> Unit,
) : PluginUpdateAlert(activity, TLRPC.TL_help_appUpdate().apply {
    version = analyzerVersion
    document = updateDocument
    text = getString(R.string.PluginAnalyzerUpdateInfo)
}, account) {

    override fun getTitleText(): String = getString(R.string.PluginAnalyzerUpdateTitle)

    override fun getDoneButtonText(): String = getString(R.string.AppUpdateNow)

    override fun addContentAfterDoneButton(container: FrameLayout) {
        addRemindLaterButton(container, ::dismiss, true)
    }

    override fun onDone() {
        onConfirm()
        dismiss()
    }
}
