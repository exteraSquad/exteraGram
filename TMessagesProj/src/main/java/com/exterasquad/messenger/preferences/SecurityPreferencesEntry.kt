package com.exterasquad.messenger.preferences

import android.os.Environment
import android.widget.Toast
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.BaseFragment
import com.exterasquad.messenger.ExteraConfig
import ua.itaysonlab.tgkit.ktx.*
import ua.itaysonlab.tgkit.preference.types.TGKitTextIconRow
import java.io.File

class SecurityPreferencesEntry : BasePreferencesEntry {
    override fun getPreferences(bf: BaseFragment) = tgKitScreen(LocaleController.getString("exteraSecurity", R.string.exteraSecurity)) {

        category(LocaleController.getString("exteraPrivacy", R.string.exteraPrivacy)) {
            switch {
                title = LocaleController.getString("exteraHideProxySponsor", R.string.exteraHideProxySponsor)

                contract({
                    return@contract ExteraConfig.hideProxySponsor
                }) {
                    ExteraConfig.hideProxySponsor = it
                }
            }
            textIcon {
                title = LocaleController.getString("exteraPreviousClientCleaner", R.string.exteraPreviousClientCleaner)

                listener = TGKitTextIconRow.TGTIListener {
                    val file = File(Environment.getExternalStorageDirectory(), "Telegram")
                    file.deleteRecursively()
                    Toast.makeText(bf.parentActivity, LocaleController.getString("exteraRemovedSuccessfully", R.string.exteraRemovedSuccessfully), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}