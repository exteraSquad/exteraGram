/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.analyzer

import com.exteragram.messenger.plugins.ui.components.AnalyzerUpdateAlert
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.BulletinFactory

object AnalyzerUpdateDebug {

    @JvmStatic
    val isAvailable: Boolean
        get() = AnalyzerUpdate.enabled

    @JvmStatic
    fun showPreview(fragment: BaseFragment) {
        val activity = fragment.parentActivity ?: return
        val document = TLRPC.TL_document().apply { size = 6_400_000 }
        val alert = AnalyzerUpdateAlert(activity, fragment.currentAccount, "1.2.0", document) {
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check, "Preview only, nothing was downloaded").show()
        }
        fragment.showDialog(alert)
    }

    @JvmStatic
    fun checkNow(report: (String) -> Unit) {
        AnalyzerUpdater.check(force = true, report = report)
    }
}
