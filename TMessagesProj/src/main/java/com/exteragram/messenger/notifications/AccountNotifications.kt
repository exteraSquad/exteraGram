/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.notifications

import androidx.core.content.edit
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationsController
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig

object AccountNotifications {

    private const val KEY_PREFIX = "accountNotifications_"

    fun isEnabled(account: Int): Boolean =
        MessagesController.getGlobalNotificationsSettings().getBoolean(key(account), true)

    fun setEnabled(account: Int, enabled: Boolean) {
        MessagesController.getGlobalNotificationsSettings().edit { putBoolean(key(account), enabled) }
        apply(account)
    }

    @JvmStatic
    fun shouldShow(account: Int): Boolean =
        account == UserConfig.selectedAccount || SharedConfig.showNotificationsForAllAccounts && isEnabled(account)

    @JvmStatic
    fun applyAll() {
        getAccounts().forEach(::apply)
    }

    @JvmStatic
    fun getAccounts(): List<Int> =
        (0 until UserConfig.MAX_ACCOUNT_COUNT).filter { UserConfig.getInstance(it).isClientActivated }

    @JvmStatic
    fun getEnabledCount(): Int = getAccounts().count { it == UserConfig.selectedAccount || isEnabled(it) }

    private fun apply(account: Int) {
        val controller = NotificationsController.getInstance(account)
        if (shouldShow(account)) {
            controller.showNotifications()
        } else {
            controller.hideNotifications()
        }
    }

    private fun key(account: Int): String = KEY_PREFIX + UserConfig.getInstance(account).clientUserId
}
