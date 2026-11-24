/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.notifications

import android.view.View
import com.exteragram.messenger.preferences.BasePreferencesActivity
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.BotWebViewVibrationEffect
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import java.util.ArrayList

class AccountNotificationsActivity : BasePreferencesActivity() {

    companion object {
        private const val ACCOUNT_ITEM_ID = 1000
    }

    private var shiftDp = -3f

    override fun getTitle(): String = getString(R.string.SettingsAccounts)

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asHeader(getString(R.string.ShowNotificationsFor)))
        AccountNotifications.getAccounts().forEach { account ->
            items.add(
                UItem.asUserCheckbox(ACCOUNT_ITEM_ID + account, UserConfig.getInstance(account).currentUser)
                    .setChecked(account == UserConfig.selectedAccount || AccountNotifications.isEnabled(account))
            )
        }
        items.add(UItem.asShadow(getString(R.string.AccountNotificationsInfo)))
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        if (item.viewType != UniversalAdapter.VIEW_TYPE_USER_CHECKBOX) {
            return
        }
        val account = item.id - ACCOUNT_ITEM_ID
        if (account == UserConfig.selectedAccount) {
            shiftDp = -shiftDp
            AndroidUtilities.shakeViewSpring(view, shiftDp)
            BotWebViewVibrationEffect.APP_ERROR.vibrate()
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.AccountNotificationsCurrent)).show()
            return
        }
        toggleBooleanSettingAndRefresh(item) { enabled -> AccountNotifications.setEnabled(account, enabled) }
    }
}
