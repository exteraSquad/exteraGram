/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.badges

import androidx.core.content.edit
import com.exteragram.messenger.api.network.ApiClient
import com.exteragram.messenger.components.SupporterWelcomeSheet
import com.exteragram.messenger.preferences
import com.exteragram.messenger.utils.network.BotCloudStorage
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.ui.Components.ForegroundDetector
import org.telegram.ui.LaunchActivity
import org.telegram.ui.PhotoViewer

object SupporterWelcomeController : NotificationCenter.NotificationCenterDelegate {

    private const val CLOUD_KEY = "supporter_welcome"
    private const val SHOWN_KEY = "supporterWelcomeShown"
    private const val CLOUD_PENDING_KEY = "supporterWelcomeCloudPending"
    private const val RETRY_DELAY = 5000L

    private val requested = HashSet<Long>()
    private val ready = HashSet<Long>()
    private var started = false
    private var retryScheduled = false

    private val retryRunnable = Runnable {
        retryScheduled = false
        tryShow()
    }

    @JvmStatic
    fun check() {
        if (!ApiClient.requestsEnabled) return
        start()
        val shown = readSet(SHOWN_KEY)
        val cloudPending = readSet(CLOUD_PENDING_KEY)
        for (account in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
            val userConfig = UserConfig.getInstance(account)
            if (!userConfig.isClientActivated) continue
            val userId = userConfig.clientUserId
            if (!BadgesController.isSupporter(userId)) continue
            if (userId in cloudPending) {
                writeCloud(account, userId)
            }
            if (userId in shown || userId in requested || userId in ready) continue
            requested += userId
            BotCloudStorage.getInstance(account).get(listOf(CLOUD_KEY)) { values ->
                requested -= userId
                if (values == null) return@get
                if (values.containsKey(CLOUD_KEY)) {
                    addToSet(SHOWN_KEY, userId)
                } else if (BadgesController.isSupporter(userId)) {
                    ready += userId
                    tryShow()
                }
            }
        }
    }

    @JvmStatic
    fun tryShow() {
        if (ready.isEmpty()) return
        val account = UserConfig.selectedAccount
        val userId = UserConfig.getInstance(account).clientUserId
        if (userId !in ready) return
        if (!BadgesController.isSupporter(userId)) {
            ready -= userId
            return
        }
        val fragment = LaunchActivity.getSafeLastFragment()
        if (ApplicationLoader.mainInterfacePaused || SharedConfig.isWaitingForPasscodeEnter || fragment == null || fragment.parentActivity == null || fragment.currentAccount != account || fragment.visibleDialog != null || fragment.hasShownSheet() || PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
            scheduleRetry()
            return
        }
        ready -= userId
        if (SupporterWelcomeSheet.show(fragment, SupporterWelcomeSheet.MODE_GRANTED) != null) {
            markShown(account, userId)
        }
    }

    private fun start() {
        if (started) return
        started = true
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.activeAccountChanged)
        ForegroundDetector.getInstance()?.addListener(object : ForegroundDetector.Listener {
            override fun onBecameForeground() {
                scheduleRetry()
            }

            override fun onBecameBackground() {}
        })
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (id == NotificationCenter.activeAccountChanged) {
            scheduleRetry()
        }
    }

    private fun scheduleRetry() {
        if (retryScheduled || ready.isEmpty()) return
        retryScheduled = true
        AndroidUtilities.runOnUIThread(retryRunnable, RETRY_DELAY)
    }

    private fun markShown(account: Int, userId: Long) {
        addToSet(SHOWN_KEY, userId)
        addToSet(CLOUD_PENDING_KEY, userId)
        writeCloud(account, userId)
    }

    private fun writeCloud(account: Int, userId: Long) {
        val value = BadgesController.getNumber(userId)?.toString() ?: "1"
        BotCloudStorage.getInstance(account).set(CLOUD_KEY, value) { success ->
            if (success) {
                removeFromSet(CLOUD_PENDING_KEY, userId)
            }
        }
    }

    private fun readSet(key: String): Set<Long> {
        return preferences.getStringSet(key, null)?.mapNotNullTo(HashSet()) { it.toLongOrNull() } ?: emptySet()
    }

    private fun addToSet(key: String, userId: Long) {
        val values = HashSet(preferences.getStringSet(key, null) ?: emptySet())
        if (values.add(userId.toString())) {
            preferences.edit { putStringSet(key, values) }
        }
    }

    private fun removeFromSet(key: String, userId: Long) {
        val values = HashSet(preferences.getStringSet(key, null) ?: emptySet())
        if (values.remove(userId.toString())) {
            preferences.edit { putStringSet(key, values) }
        }
    }
}
