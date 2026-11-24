/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.ai.ui.activities

import android.content.DialogInterface
import android.content.Intent
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.core.net.toUri
import com.exteragram.messenger.PLACEHOLDERS
import com.exteragram.messenger.ai.AiController
import com.exteragram.messenger.ai.clearSelectedService
import com.exteragram.messenger.ai.data.Service
import com.exteragram.messenger.ai.network.backend.OnDeviceAvailability
import com.exteragram.messenger.ai.onDeviceFastModel
import com.exteragram.messenger.ai.onDevicePreviewModel
import com.exteragram.messenger.ai.onDeviceReasoning
import com.exteragram.messenger.ai.setSelectedServices
import com.exteragram.messenger.ai.ui.OnDeviceModelDialogs
import com.exteragram.messenger.preferences.BasePreferencesActivity
import com.exteragram.messenger.utils.text.LocaleUtils
import com.exteragram.messenger.utils.ui.AddButtonDrawable
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.ItemOptions
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import java.util.ArrayList

class ServicesActivity : BasePreferencesActivity(), NotificationCenter.NotificationCenterDelegate {

    companion object {
        private const val NEW_SERVICE = 1
        private const val SERVICE_ID_BASE = 100
        private const val STATUS_POLL_INTERVAL = 5000L
    }

    private val statusPoll: Runnable = object : Runnable {
        override fun run() {
            context ?: return
            OnDeviceAvailability.refresh(this@ServicesActivity::reloadServices)
            if (OnDeviceAvailability.isDownloadInProgress()) {
                AndroidUtilities.runOnUIThread(this, STATUS_POLL_INTERVAL)
            }
        }
    }

    private var newServiceDrawable: AddButtonDrawable? = null

    override fun onFragmentCreate(): Boolean {
        notificationCenter.addObserver(this, NotificationCenter.servicesUpdated)
        OnDeviceAvailability.refresh(::reloadServices)
        return super.onFragmentCreate()
    }

    override fun onResume() {
        super.onResume()
        OnDeviceAvailability.refresh(::reloadServices)
        AndroidUtilities.cancelRunOnUIThread(statusPoll)
        AndroidUtilities.runOnUIThread(statusPoll, STATUS_POLL_INTERVAL)
    }

    override fun onPause() {
        super.onPause()
        AndroidUtilities.cancelRunOnUIThread(statusPoll)
    }

    override fun onFragmentDestroy() {
        AndroidUtilities.cancelRunOnUIThread(statusPoll)
        notificationCenter.removeObserver(this, NotificationCenter.servicesUpdated)
        super.onFragmentDestroy()
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (id == NotificationCenter.servicesUpdated) {
            reloadServices()
        }
    }

    override fun getTitle(): String = getString(R.string.Services)

    override fun needHideTitle(): Boolean = true

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asTopView(getTitle(), getString(R.string.ServicesInfo), PLACEHOLDERS, "\uD83D\uDD11"))
        items.add(UItem.asHeader(getString(R.string.Services)))
        AiController.getInstance().all.forEachIndexed { i, service ->
            val title = if (service.isOnDevice) applyOnDeviceBadge(service.model) else service.model
            val subtitle = if (service.isOnDevice) getString(R.string.AIOnDevice) else service.url
            val item = UItem.asRadio2(SERVICE_ID_BASE + i, title, subtitle).setChecked(service.isSelected)
            item.`object` = service
            items.add(item)
        }

        val item = UItem.asButton(NEW_SERVICE, getNewServiceDrawable(), getString(R.string.NewService)).accent()
        item.pad = 61
        items.add(item)
    }

    private fun getNewServiceDrawable(): AddButtonDrawable {
        val drawable = newServiceDrawable ?: AddButtonDrawable(context).also { newServiceDrawable = it }
        drawable.updateColors(getResourceProvider())
        return drawable
    }

    private fun applyOnDeviceBadge(title: CharSequence): CharSequence = when {
        OnDeviceAvailability.isDownloadInProgress() -> {
            val percent = OnDeviceAvailability.getDownloadPercent()
            val badge = if (percent >= 0) "$percent%" else getString(R.string.AIOnDeviceBadgeDownloading)
            LocaleUtils.applyBadge(title, badge, Theme.key_chats_unreadCounterMuted, Theme.key_chats_unreadCounterText)
        }
        OnDeviceAvailability.needsDownload() ->
            LocaleUtils.applyBadge(title, getString(R.string.AIOnDeviceBadgeDownload), Theme.key_chats_unreadCounterMuted, Theme.key_chats_unreadCounterText)
        OnDeviceAvailability.isReady() ->
            LocaleUtils.applyBadge(title, getString(R.string.AIOnDeviceBadgeReady), Theme.key_featuredStickers_addButton, Theme.key_featuredStickers_buttonText)
        else ->
            LocaleUtils.applyBadge(title, getString(R.string.AIOnDeviceBadgeUnavailable), Theme.key_chats_unreadCounterMuted, Theme.key_chats_unreadCounterText)
    }

    private fun reloadServices() {
        AiController.getInstance().loadServices()
        if (context == null || fragmentView == null) return
        listView.adapter?.update(true)
    }

    private fun onDeviceUnavailable(service: Service) {
        context ?: return
        reloadServices()
        when {
            OnDeviceAvailability.isReady() -> {
                if (!service.isSelected) {
                    setSelectedServices(service)
                    reloadServices()
                }
            }
            OnDeviceAvailability.needsDownload() -> OnDeviceModelDialogs.showDownloadDialog(this)
            else -> BulletinFactory.of(this).createErrorBulletin(getString(R.string.AIErrorOnDeviceInfo)).show()
        }
    }

    private fun showOnDeviceOptions(view: View) {
        ItemOptions.makeOptions(this, view).apply {
            if (OnDeviceAvailability.isThinkingSupported()) {
                addChecked(onDeviceReasoning, R.drawable.msg_discuss, getString(R.string.AIReasoning)) {
                    onDeviceReasoning = !onDeviceReasoning
                    reloadServices()
                }
            }
            addChecked(onDevicePreviewModel, R.drawable.msg_fave, getString(R.string.AIOnDevicePreviewModel)) {
                changeModelConfig { onDevicePreviewModel = !onDevicePreviewModel }
            }
            addChecked(onDeviceFastModel, R.drawable.msg_speed, getString(R.string.AIOnDeviceFastModel)) {
                changeModelConfig { onDeviceFastModel = !onDeviceFastModel }
            }
            addGap()
            add(R.drawable.msg_reset, getString(R.string.AIOnDeviceRefresh)) { OnDeviceAvailability.refresh(this@ServicesActivity::reloadServices) }
            add(R.drawable.msg_settings, getString(R.string.AIOnDeviceManage)) { openAiCoreSettings() }
            setGravity(if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT)
            setScrimViewBackground(listView.getClipBackground(view))
            setDismissOnMoveOutside(true)
            show()
        }
    }

    private fun changeModelConfig(change: () -> Unit) {
        change()
        OnDeviceAvailability.onModelConfigChanged(::reloadServices)
        AndroidUtilities.cancelRunOnUIThread(statusPoll)
        AndroidUtilities.runOnUIThread(statusPoll, STATUS_POLL_INTERVAL)
    }

    private fun openAiCoreSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = "package:com.google.android.aicore".toUri()
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.AIOnDeviceManageUnavailable)).show()
        }
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        val service = item.`object` as? Service
        if (item.id >= SERVICE_ID_BASE && service != null) {
            if (service.isOnDevice && !OnDeviceAvailability.isReady()) {
                when {
                    OnDeviceAvailability.isDownloadInProgress() ->
                        BulletinFactory.of(this).createSimpleBulletin(
                            R.raw.ic_download,
                            getString(R.string.AIErrorOnDeviceDownloading),
                            getString(R.string.AIOnDeviceManage)
                        ) { openAiCoreSettings() }.show()
                    OnDeviceAvailability.needsDownload() -> OnDeviceModelDialogs.showDownloadDialog(this)
                    else -> OnDeviceAvailability.refresh { onDeviceUnavailable(service) }
                }
                return
            }
            if (!service.isSelected) {
                setSelectedServices(service)
                listView.adapter?.update(true)
            }
        } else if (item.id == NEW_SERVICE) {
            presentFragment(EditServiceActivity())
        }
    }

    override fun onLongClick(item: UItem, view: View, position: Int, x: Float, y: Float): Boolean {
        val service = item.`object` as? Service ?: return false
        if (service.isOnDevice) {
            showOnDeviceOptions(view)
            return true
        }

        ItemOptions.makeOptions(this, view).apply {
            add(R.drawable.msg_edit, getString(R.string.Edit)) { presentFragment(EditServiceActivity(service)) }
            add(R.drawable.msg_copy, getString(R.string.Copy)) {
                if (AndroidUtilities.addToClipboard("${service.url}\n${service.model}\n${service.key}"))
                    BulletinFactory.of(this@ServicesActivity).createCopyBulletin(getString(R.string.TextCopied)).show()
            }
            add(R.drawable.msg_delete, getString(R.string.Delete), true) { confirmDeleteService(service) }
            setGravity(if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT)
            setScrimViewBackground(listView.getClipBackground(view))
            setDismissOnMoveOutside(true)
            show()
        }
        return true
    }

    private fun confirmDeleteService(service: Service) {
        val activity = parentActivity ?: return
        val dialog = AlertDialog.Builder(activity)
            .setTitle(getString(R.string.Delete))
            .setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.DeleteServiceInfo, service.shortModel)))
            .setPositiveButton(getString(R.string.Delete)) { _, _ -> deleteService(service) }
            .setNegativeButton(getString(R.string.Cancel), null)
            .create()
        showDialog(dialog)
        (dialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView)?.setTextColor(Theme.getColor(Theme.key_text_RedBold))
    }

    private fun deleteService(service: Service) {
        val wasSelected = service.isSelected
        if (!AiController.getInstance().removeService(service)) return

        if (wasSelected) {
            clearSelectedService()
            if (!AiController.getInstance().isServicesEmpty) {
                setSelectedServices(AiController.getInstance().all[0])
            }
        }

        notificationCenter.postNotificationName(NotificationCenter.servicesUpdated)
        if (fragmentView != null) listView.adapter?.update(true)
    }
}
