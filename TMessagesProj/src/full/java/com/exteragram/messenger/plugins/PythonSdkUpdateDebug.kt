/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins

import com.exteragram.messenger.plugins.PythonPluginsEngine.Updater.Companion.PythonSdkUpdateInfo
import com.exteragram.messenger.plugins.ui.components.PythonSdkUpdateAlert
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.BaseFragment

object PythonSdkUpdateDebug {
    val isAvailable = true

    fun showPreview(fragment: BaseFragment) {
        if (fragment.parentActivity == null) return
        val update = PythonSdkUpdateInfo().apply {
            version = "1.4.7"
            document = TLRPC.TL_document().apply { size = 6_400_000 }
            text = "Preview of the Python SDK release notes."
        }
        fragment.showDialog(PythonSdkUpdateAlert(fragment, update).apply { preview = true })
    }
}
