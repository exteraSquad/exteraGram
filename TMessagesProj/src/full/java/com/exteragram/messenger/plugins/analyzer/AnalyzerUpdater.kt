/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.analyzer

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import com.exteragram.messenger.plugins.ui.components.AnalyzerUpdateAlert
import com.exteragram.messenger.plugins.updates.TelegramUpdateSource
import com.exteragram.messenger.pluginsEngine
import com.exteragram.messenger.pluginsPySdkAutoUpdate
import com.exteragram.messenger.pluginsPySdkBetaVersions
import com.exteragram.messenger.pluginsStaticAnalysis
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.LaunchActivity
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

internal object AnalyzerUpdater {
    enum class Status { IDLE, CHECKING, CURRENT, LOADING, FAILED }

    private val download = TelegramUpdateSource.Download()
    var status = Status.IDLE
        private set(value) {
            field = value
            refreshInfo()
        }

    val canCheck: Boolean
        get() = AnalyzerUpdate.enabled && wanted && !inProgress && prompt?.get()?.isShowing != true

    private val wanted: Boolean
        get() = pluginsEngine && pluginsStaticAnalysis

    private val inProgress: Boolean
        get() = status == Status.CHECKING || status == Status.LOADING
    private var prompt: WeakReference<AnalyzerUpdateAlert>? = null
    private var nextCheck = 0L

    fun check(force: Boolean = false, source: BaseFragment? = null, report: ((String) -> Unit)? = null) {
        if (!AnalyzerUpdate.enabled) {
            report?.invoke("Analyzer OTA is unavailable in this build")
            return
        }
        AndroidUtilities.runOnUIThread {
            val account = UserConfig.selectedAccount
            val blocker = when {
                !wanted -> "Plugin analysis is disabled"
                inProgress || prompt?.get()?.isShowing == true -> "An analyzer update check is already running"
                !UserConfig.getInstance(account).isClientActivated -> "No active account"
                !force && SystemClock.elapsedRealtime() < nextCheck -> "Analyzer update check is throttled"
                else -> null
            }
            if (blocker != null) {
                report?.invoke(blocker)
                return@runOnUIThread
            }
            status = Status.CHECKING
            TelegramUpdateSource.search(account, "plugin_analyzer") { response, error ->
                if (error != null || response == null) {
                    finish(false, if (force) Status.FAILED else Status.IDLE)
                    report?.invoke("Search failed: ${error?.text ?: "no response"}")
                } else {
                    PluginAnalyzer.selectUpdate(response) { result ->
                        val candidate = result.getOrNull()
                        if (account != UserConfig.selectedAccount || !wanted) {
                            finish(false)
                        } else if (result.isFailure) {
                            finish(false, if (force) Status.FAILED else Status.IDLE)
                            report?.invoke("Could not select an analyzer update")
                        } else if (candidate == null) {
                            finish(true, Status.CURRENT)
                            report?.invoke("No compatible analyzer update in the channel")
                        } else if (!force && isSnoozed(candidate.update)) {
                            finish(true)
                            report?.invoke("Analyzer ${candidate.update.version} is snoozed")
                        } else if (!force && pluginsPySdkAutoUpdate) {
                            install(account, candidate)
                        } else if (!offer(account, candidate, source)) {
                            report?.invoke("Could not show the update sheet")
                        }
                    }
                }
            }
        }
    }

    private fun offer(account: Int, candidate: Candidate, source: BaseFragment?): Boolean {
        val fragment = source ?: LaunchActivity.getSafeLastFragment()
        val activity = fragment?.parentActivity
        if (account != UserConfig.selectedAccount || !wanted || fragment == null ||
            fragment.isPaused || fragment.visibleDialog?.isShowing == true ||
            activity == null || activity.isFinishing || activity.isDestroyed
        ) {
            finish(false)
            return false
        }

        var confirmed = false
        val alert = AnalyzerUpdateAlert(activity, account, candidate.update.version, candidate.document) {
            if (!inProgress) {
                confirmed = true
                install(account, candidate)
            }
        }
        val shown = fragment.showDialog(alert) {
            if (!confirmed) snooze(candidate.update)
            prompt = null
            refreshInfo()
        } != null
        if (shown) prompt = WeakReference(alert)
        finish(shown)
        return shown
    }

    private fun install(account: Int, candidate: Candidate) {
        if (account != UserConfig.selectedAccount || !wanted ||
            !UserConfig.getInstance(account).isClientActivated
        ) {
            finish(false)
            return
        }
        status = Status.LOADING
        download.start(account, candidate.message, candidate.document) { file ->
            if (file == null) {
                finishInstall(candidate.update, false)
            } else {
                PluginAnalyzer.stageUpdate(candidate.update, file) { finishInstall(candidate.update, it) }
            }
        }
    }

    private fun finishInstall(update: AnalyzerUpdate, success: Boolean) {
        if (!success) snooze(update)
        finish(success, if (success) Status.IDLE else Status.FAILED)
        val fragment = LaunchActivity.getSafeLastFragment() ?: return
        if (fragment.isPaused || fragment.parentActivity == null) return
        val bulletin = BulletinFactory.of(fragment)
        if (success) {
            bulletin.createSimpleBulletin(R.raw.contact_check, getString(R.string.PluginAnalyzerUpdateReady)).show()
        } else {
            bulletin.createErrorBulletin(getString(R.string.PluginAnalyzerUpdateFailed)).show()
        }
    }

    private fun finish(success: Boolean, result: Status = Status.IDLE) {
        nextCheck = SystemClock.elapsedRealtime() + TimeUnit.MINUTES.toMillis(if (success) 60 else 5)
        status = result
    }

    fun clearResult() {
        if (status == Status.CURRENT || status == Status.FAILED) status = Status.IDLE
    }

    fun refreshInfo() {
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pluginAnalyzerUpdateChanged)
    }

    private val preferences by lazy {
        ApplicationLoader.applicationContext.getSharedPreferences("plugin_analyzer_updates", Context.MODE_PRIVATE)
    }

    private fun snooze(update: AnalyzerUpdate) {
        preferences.edit {
            putString(KEY_SNOOZED_ID, update.id)
            putLong(KEY_SNOOZED_UNTIL, System.currentTimeMillis() + SNOOZE_MS)
        }
    }

    private fun isSnoozed(update: AnalyzerUpdate): Boolean {
        if (preferences.getString(KEY_SNOOZED_ID, null) != update.id) return false
        val until = preferences.getLong(KEY_SNOOZED_UNTIL, 0)
        return System.currentTimeMillis() in (until - SNOOZE_MS)..<until
    }

    private const val KEY_SNOOZED_ID = "snoozed_id"
    private const val KEY_SNOOZED_UNTIL = "snoozed_until"
    private val SNOOZE_MS = TimeUnit.DAYS.toMillis(1)

    internal class Candidate(val update: AnalyzerUpdate, val message: TLRPC.Message, val document: TLRPC.Document)

    fun select(response: TLRPC.messages_Messages, canStage: (AnalyzerUpdate) -> Boolean): Candidate? =
        response.messages.asSequence().mapNotNull { message ->
            val document = (message.media as? TLRPC.TL_messageMediaDocument)?.document ?: return@mapNotNull null
            if (document.size !in 1..AnalyzerUpdate.MAX_ARCHIVE_BYTES) return@mapNotNull null
            val lines = message.message?.lineSequence()?.map(String::trim) ?: return@mapNotNull null
            val metadata = lines.dropWhile { it != "plugin_analyzer_stable" && it != "plugin_analyzer_beta" }.take(3).toList()
            if (metadata.size != 3 || !metadata[1].startsWith("manifest=") || !metadata[2].startsWith("signature=")) {
                return@mapNotNull null
            }
            val update = AnalyzerUpdate.parse(metadata[1].removePrefix("manifest="), metadata[2].removePrefix("signature="))
                ?: return@mapNotNull null
            if (metadata[0] != "plugin_analyzer_${update.channel}" ||
                (update.channel == "beta" && !pluginsPySdkBetaVersions) || !canStage(update)
            ) {
                return@mapNotNull null
            }
            Candidate(update, message, document)
        }.maxByOrNull { it.update.release }
}
