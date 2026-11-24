/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.badges

import com.exteragram.messenger.api.ApiController
import com.exteragram.messenger.getApiBotInfo
import com.exteragram.messenger.utils.network.RemoteUtils
import org.telegram.messenger.UserConfig
import org.telegram.messenger.browser.Browser
import org.telegram.ui.ActionBar.BaseFragment
import java.util.Locale

object DonationLauncher {

    private const val APP_NAME = "donate"
    private const val START_CUSTOM_BADGE = "custom"
    private const val REMOTE_NAME = "universal"

    @JvmStatic
    fun isEnabled(): Boolean = donates().any { isDonationEntry(it.substringBefore('#'), it.substringAfter('#', "")) }

    @JvmStatic
    fun isDonationEntry(name: String, details: String): Boolean {
        return isDonationLink(details) || name.lowercase(Locale.ROOT).contains(REMOTE_NAME)
    }

    private fun donates(): Set<String> = RemoteUtils.getStringSetConfigValue(RemoteUtils.DONATES, emptySet())

    @JvmStatic
    fun isDonationLink(url: String): Boolean {
        val path = url.trim().substringAfter("://", url.trim()).substringBefore('?').trimEnd('/').lowercase()
        return path.removePrefix("www.").startsWith("t.me/") && path.substringAfter('/').let {
            it.equals(getApiBotInfo().second + "/" + APP_NAME, ignoreCase = true)
        }
    }

    @JvmStatic
    fun open(fragment: BaseFragment, customBadge: Boolean) {
        val activity = fragment.parentActivity ?: return
        val details = donates().map { it.substringAfter('#', "") }
        val url = if (details.any { isDonationLink(it) }) {
            buildString {
                append("https://t.me/").append(getApiBotInfo().second).append('/').append(APP_NAME)
                if (customBadge) append("?startapp=").append(START_CUSTOM_BADGE)
            }
        } else {
            donates().firstOrNull { isDonationEntry(it.substringBefore('#'), "") }?.substringAfter('#', "") ?: return
        }
        Browser.openUrl(activity, url)
        ApiController.watchDonation(UserConfig.getInstance(fragment.currentAccount).clientUserId)
    }
}
