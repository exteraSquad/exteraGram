/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.preferences

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.os.CountDownTimer
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.TextView
import androidx.core.net.toUri
import com.exteragram.messenger.*
import com.exteragram.messenger.api.db.DatabaseHelper
import com.exteragram.messenger.api.dto.BoostySubscriberDTO
import com.exteragram.messenger.backup.PreferencesUtils
import com.exteragram.messenger.api.ApiController
import com.exteragram.messenger.badges.BadgesController
import com.exteragram.messenger.badges.DonationLauncher
import com.exteragram.messenger.components.BoostyBottomSheet
import com.exteragram.messenger.components.SupporterBottomSheet
import com.exteragram.messenger.components.supporter.SupportBannerCell
import com.exteragram.messenger.export.ui.ExportActivity
import com.exteragram.messenger.utils.network.RemoteUtils
import com.exteragram.messenger.utils.system.VibratorUtils
import com.exteragram.messenger.utils.addIf
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.LinkifyPort
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.messenger.browser.Browser
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_account
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.IconBackgroundColors
import org.telegram.ui.Components.ItemOptions
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Wallet.WalletSendEnterAmount
import java.util.ArrayList
import java.util.Locale

class OtherPreferencesActivity : BasePreferencesActivity(), NotificationCenter.NotificationCenterDelegate {

    companion object {
        private val DONATE_ORDER = listOf("boosty", "keeper", "wallet", "card")
        private const val DONATE_APP_DETAILS = "app"

        fun getDonates(): List<Donate> {
            val donates = parseDonates(RemoteUtils.DONATES).sortedBy { donate ->
                val name = donate.name.lowercase(Locale.ROOT)
                DONATE_ORDER.indexOfFirst { name.contains(it) }.takeIf { it >= 0 } ?: DONATE_ORDER.size
            }
            if (!DonationLauncher.isEnabled()) return donates
            return listOf(Donate(getString(R.string.DonateApp), DONATE_APP_DETAILS)) + donates.filterNot { DonationLauncher.isDonationEntry(it.name, it.details) }
        }

        private fun parseDonates(key: String): List<Donate> {
            val donates = RemoteUtils.getStringSetConfigValue(key, emptySet())
            val donateList = ArrayList<Donate>()
            for (d in donates) {
                val tokens = d.split("#")
                if (tokens.size != 2) continue
                donateList.add(Donate(tokens[0], tokens[1]))
            }
            return donateList
        }
    }

    enum class OtherItem {
        CRASHLYTICS,
        ANALYTICS,
        EXPORT_SETTINGS,
        EXPORT_DATA,
        RESET_SETTINGS,
        DELETE_ACCOUNT,
        SUPPORT_BANNER,
        DONATE;

        val id: Int = ordinal + 1
    }

    data class IconInfo(val iconResId: Int, val iconColor: Int)
    data class Donate(val name: String, val details: String)

    private var donates = emptyList<Donate>()
    private val subscribers = ArrayList<BoostySubscriberDTO>()
    private val defaultDonateIcon = IconInfo(R.drawable.msg_payment_card, Color.TRANSPARENT)

    private lateinit var supportBanner: SupportBannerCell

    override fun onFragmentCreate(): Boolean {
        notificationCenter.addObserver(this, NotificationCenter.updateInterfaces)
        return super.onFragmentCreate()
    }

    override fun onFragmentDestroy() {
        notificationCenter.removeObserver(this, NotificationCenter.updateInterfaces)
        super.onFragmentDestroy()
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (id == NotificationCenter.updateInterfaces && ::supportBanner.isInitialized && (args[0] as Int) and MessagesController.UPDATE_MASK_EMOJI_STATUS != 0) {
            supportBanner.update(ApiController.isWatchingDonation())
        }
    }

    override fun onResume() {
        super.onResume()
        supportBanner.update(false)
    }

    override fun createView(context: Context): View {
        supportBanner = SupportBannerCell(context, currentAccount, resourceProvider, { SupporterBottomSheet.showAlert(this) }, ::handleSupportButtonClick)
        DatabaseHelper.getBoostySubscribers {
            AndroidUtilities.runOnUIThread {
                subscribers.addAll(it)
                if (it.isNotEmpty() && fragmentView != null) listView.adapter.update(true)
            }
        }
        return super.createView(context)
    }

    override fun getTitle(): String = getString(R.string.LocalOther)

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        donates = getDonates()
        val isDarkTheme = Theme.isCurrentThemeDark()
        val donateIcons = mapOf(
            "keeper" to IconInfo(R.drawable.keeper_icon, -0xe28c0b),
            "wallet" to IconInfo(R.drawable.settings_gram_24, -0xeb751e),
            "card" to IconInfo(R.drawable.filled_bank_card, IconBackgroundColors.GRAY.top),
            "boosty" to IconInfo(R.drawable.boosty_icon, if (isDarkTheme) -0x111112 else -0xdbd4d4)
        )

        if (donates.isNotEmpty() || BadgesController.isSupporter(userConfig.clientUserId)) {
            items.add(UItem.asCustomShadow(supportBanner).setId(OtherItem.SUPPORT_BANNER.id))
        }
        addDonateSection(items, donates, donateIcons)

        items.add(UItem.asHeader("Google"))
        items.add(uCheck(OtherItem.CRASHLYTICS, "Crashlytics", useGoogleCrashlytics, icon = R.drawable.msg_report, linkAlias = "crashlytics"))
        items.add(uCheck(OtherItem.ANALYTICS, "Analytics", useGoogleAnalytics, icon = R.drawable.msg_data, linkAlias = "analytics"))
        items.add(UItem.asShadow(getString(R.string.AnalyticsInfo)))

        items.add(uButton(OtherItem.EXPORT_DATA, getString(R.string.ExportData), R.drawable.msg_archive, linkAlias = "exportData"))
        items.add(uButton(OtherItem.EXPORT_SETTINGS, getString(R.string.ExportSettings), R.drawable.msg_settings, linkAlias = "exportSettings"))
        items.add(uButton(OtherItem.RESET_SETTINGS, getString(R.string.ResetSettings), R.drawable.msg_reset, linkAlias = "resetSettings"))
        items.add(uButton(OtherItem.DELETE_ACCOUNT, getString(R.string.DeleteAccount), R.drawable.msg_clearcache, red = true, linkAlias = "deleteAccount"))
        items.add(UItem.asShadow())
    }

    override fun onLongClick(item: UItem, view: View, position: Int, x: Float, y: Float): Boolean {
        val id = item.id
        if (id >= OtherItem.DONATE.id && id < OtherItem.DONATE.id + donates.size)
            return handleDonateLongClick(item, view)
        return super.onLongClick(item, view, position, x, y)
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        val id = item.id
        if (id >= OtherItem.DONATE.id && id < OtherItem.DONATE.id + donates.size) {
            getDonate(id)?.let { handleDonateClick(it, view) }
            return
        }

        if (id <= 0 || id >= OtherItem.DONATE.id) return
        val clickedItem = OtherItem.entries[id - 1]
        when (clickedItem) {
            OtherItem.CRASHLYTICS -> {
                toggleBooleanSettingAndRefresh(item) { useGoogleCrashlytics = it }
                handleCrashlyticsClick()
            }
            OtherItem.ANALYTICS -> {
                toggleBooleanSettingAndRefresh(item) { useGoogleAnalytics = it }
                handleAnalyticsClick()
            }
            OtherItem.RESET_SETTINGS -> handleResetSettingsClick()
            OtherItem.DELETE_ACCOUNT -> handleDeleteAccountClick()
            OtherItem.EXPORT_SETTINGS -> PreferencesUtils.getInstance().exportSettings(this)
            OtherItem.EXPORT_DATA -> ExportActivity.open(this)
            else -> {}
        }
    }

    private fun addDonateSection(
        items: ArrayList<UItem>,
        donateList: List<Donate>,
        donateIcons: Map<String, IconInfo>
    ) {
        if (donateList.isEmpty()) return

        donateList.forEachIndexed { i, donate ->
            val name = donate.name.lowercase()
            val iconInfo = if (donate.details == DONATE_APP_DETAILS) {
                IconInfo(R.drawable.filled_bank_card, IconBackgroundColors.GREEN.top)
            } else {
                donateIcons.entries
                    .firstOrNull { (key) -> name.contains(key) }
                    ?.value ?: defaultDonateIcon
            }
            val value = if (name.contains("boosty") && subscribers.isNotEmpty()) LocaleController.formatPluralString("BoostyPeopleCount", subscribers.size) else null
            val item = uButton(OtherItem.DONATE.id + i, donate.name, value = value, searchable = true).apply {
                if (iconInfo.iconColor == Color.TRANSPARENT) setIcon(iconInfo.iconResId)
                else setColorfulIcon(iconInfo.iconResId, iconInfo.iconColor)
            }
            items.add(item)
        }

        items.add(UItem.asShadow())
    }

    private fun handleSupportButtonClick() {
        if (!DonationLauncher.isEnabled() || BadgesController.isSupporter(userConfig.clientUserId)) {
            SupporterBottomSheet.showAlert(this)
        } else {
            DonationLauncher.open(this, false)
        }
    }

    private fun getDonate(id: Int): Donate? {
        val idx = id - OtherItem.DONATE.id
        if (idx in donates.indices) return donates[idx]
        return null
    }

    private fun handleCrashlyticsClick() =
        ApplicationLoader.getFirebaseCrashlytics()?.isCrashlyticsCollectionEnabled = useGoogleCrashlytics

    private fun handleAnalyticsClick() = ApplicationLoader.getFirebaseAnalytics()?.let {
        it.setAnalyticsCollectionEnabled(useGoogleAnalytics)
        if (!useGoogleAnalytics) it.resetAnalyticsData()
    }

    private fun handleResetSettingsClick() {
        val dialog = AlertDialog.Builder(parentActivity).apply {
            setMessage(AndroidUtilities.replaceTags(getString(R.string.ResetPreferencesInfo)))
            setTitle(getString(R.string.ResetSettings))
            setNegativeButton(getString(R.string.Cancel), null)
            setPositiveButton(getString(R.string.Reset)) { _, _ ->
                PreferencesUtils.clearPreferences()
                parentLayout.rebuildFragments(0)
                notificationCenter.postNotificationName(NotificationCenter.mainUserInfoChanged)
                notificationCenter.postNotificationName(NotificationCenter.dialogFiltersUpdated)
                LocaleController.getInstance().recreateFormatters()
                Theme.reloadAllResources(parentActivity)
                BulletinFactory.of(this@OtherPreferencesActivity)
                    .createErrorBulletin(getString(R.string.ResetPreferences), resourceProvider).show()
            }
        }.create()
        showDialog(dialog)
        (dialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView)
            ?.setTextColor(Theme.getColor(Theme.key_text_RedBold))
    }

    private fun handleDeleteAccountClick() {
        val builder = AlertDialog.Builder(parentActivity).apply {
            setMessage(getString(R.string.TosDeclineDeleteAccount))
            setTitle(getString(R.string.DeleteAccount))
            setPositiveButton(getString(R.string.Deactivate)) { _, _ ->
                val progressDialog = AlertDialog(parentActivity, AlertDialog.ALERT_TYPE_SPINNER).apply {
                    setCanCancel(false)
                    show()
                }

                Utilities.globalQueue.postRunnable({
                    connectionsManager.sendRequest(TL_account.deleteAccount().apply {
                        reason = "ЭКСТЕРАГРАМ"
                    }) { response, error -> AndroidUtilities.runOnUIThread {
                        try {
                            progressDialog.dismiss()
                        } catch (e: Exception) {
                            FileLog.e(e)
                        }

                        if (response is TLRPC.TL_boolTrue) {
                            messagesController.performLogout(0)
                            return@runOnUIThread
                        }

                        if (error == null || error.code != -1000) {
                            val errorText = getString(R.string.ErrorOccurred) + error?.text?.let { "\n$it" }.orEmpty()
                            AlertDialog.Builder(parentActivity).apply {
                                setTitle(getString(R.string.AppName))
                                setMessage(errorText)
                                setPositiveButton(getString(R.string.OK), null)
                                show()
                            }
                        }
                    }}
                }, 500)
            }
            setNegativeButton(getString(R.string.Cancel), null)
        }

        builder.create().apply {
            setOnShowListener {
                val button = this.getButton(AlertDialog.BUTTON_POSITIVE) as? TextView ?: return@setOnShowListener
                button.setTextColor(Theme.getColor(Theme.key_text_RedBold))
                button.isEnabled = false
                val buttonText = button.text

                object : CountDownTimer(30000, 100) {
                    override fun onTick(millisUntilFinished: Long) {
                        button.text = String.format(Locale.getDefault(), "%s • %d", buttonText, millisUntilFinished / 1000 + 1)
                    }

                    override fun onFinish() {
                        button.text = buttonText
                        button.isEnabled = true
                    }
                }.start()
            }
        }.also { showDialog(it) }
    }

    private fun handleDonateClick(donate: Donate, view: View) {
        val nameLower = donate.name.lowercase(Locale.getDefault())
        val userId = UserConfig.getInstance(currentAccount).clientUserId
        when {
            donate.details == DONATE_APP_DETAILS -> DonationLauncher.open(this, false)

            nameLower.contains("wallet") && messagesController.config.walletAvailable.get() -> {
                presentFragment(WalletSendEnterAmount(currentAccount, donate.details).setComment(userId.toString(), false))
            }

            nameLower.contains("keeper") || nameLower.contains("wallet") -> {
                val activity = parentActivity ?: return
                val intent = Intent(Intent.ACTION_VIEW, "ton://transfer/${donate.details}?text=$userId".toUri())
                val (own, external) = activity.packageManager.queryIntentActivities(intent, 0)
                    .partition { it.activityInfo.packageName == activity.packageName }
                if (external.isEmpty()) {
                    copyDonateDetails(donate.details)
                    return
                }
                try {
                    if (external.size == 1) {
                        activity.startActivity(intent.setPackage(external[0].activityInfo.packageName))
                    } else {
                        val excluded = own.map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }.toTypedArray()
                        activity.startActivity(Intent.createChooser(intent, null).putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, excluded))
                    }
                } catch (e: ActivityNotFoundException) {
                    FileLog.e(e)
                    copyDonateDetails(donate.details)
                }
            }

            nameLower.contains("card") -> {
                val banks = parseDonates(RemoteUtils.DONATES_BANKS)
                if (banks.isEmpty()) {
                    copyDonateDetails(donate.details)
                    return
                }
                ItemOptions.makeOptions(this, view).apply {
                    banks.forEach { bank -> add(bank.name, bank.details) { copyDonateDetails(bank.details) } }
                    setScrimViewBackground(listView.getClipBackground(view))
                    setDismissOnMoveOutside(true)
                    show()
                }
            }

            nameLower.contains("boosty") && subscribers.isNotEmpty() -> {
                parentActivity ?: return
                showDialog(object : BoostyBottomSheet(parentActivity, subscribers) {
                    override fun onButtonClick() = Browser.openUrl(parentActivity, donate.details)
                })
            }

            LinkifyPort.WEB_URL.matcher(donate.details).matches() -> {
                Browser.openUrl(parentActivity, donate.details)
            }

            else -> copyDonateDetails(donate.details)
        }
    }

    private fun copyDonateDetails(details: String) {
        if (AndroidUtilities.addToClipboard(details))
            BulletinFactory.of(this).createCopyBulletin(getString(R.string.TextCopied)).show()
    }

    private fun handleDonateLongClick(item: UItem, view: View): Boolean {
        val donate = getDonate(item.id) ?: return false
        if (donate.details == DONATE_APP_DETAILS) return false
        copyDonateDetails(donate.details)
        view.performHapticFeedback(
            VibratorUtils.getType(HapticFeedbackConstants.KEYBOARD_TAP),
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        )
        return false
    }
}