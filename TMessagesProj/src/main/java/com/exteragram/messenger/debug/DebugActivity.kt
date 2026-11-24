/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.debug

import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.util.size
import com.exteragram.messenger.api.ApiController
import com.exteragram.messenger.api.db.ExteraDatabase
import com.exteragram.messenger.plugins.PythonSdkUpdateDebug
import com.exteragram.messenger.plugins.analyzer.AnalyzerUpdateDebug
import com.exteragram.messenger.components.SupporterBottomSheet
import com.exteragram.messenger.components.SupporterWelcomeSheet
import com.exteragram.messenger.preferences.BasePreferencesActivity
import com.exteragram.messenger.utils.network.RemoteUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.R
import org.telegram.messenger.Utilities
import org.telegram.tgnet.ConnectionsManager
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter

class DebugActivity : BasePreferencesActivity() {

    enum class DebugItem {
        DEBUG_CAMERA_METRICS,
        FORCE_COMPACT_SAVED_MUSIC,
        DISABLE_API_REQUESTS,
        DISABLE_CHAT_FADE_WALLPAPER_BLEND,
        CHAT_FADE_USE_WHITE_BACKGROUND,
        PROFILE_COLOR_DESIGN,
        FORCE_REFRESH_REMOTE_CONFIG,
        CLEAR_DB,
        CLEAR_TRANSLATIONS,
        SHOW_SUPPORTER_WELCOME,
        SHOW_SUPPORTER_PROMO,
        SET_IPCONFIG_OVERRIDE,
        CLEAR_IPCONFIG_OVERRIDE,
        HEAP_MONITOR,
        DUMP_HEAP,
        SHARE_HEAP_DUMP,
        DELETE_HEAP_DUMPS,
        LOAD_MONITOR,
        CAPTURE_LOAD_REPORT,
        SHARE_LOAD_REPORT,
        DELETE_LOAD_REPORTS,
        FREEZE_MONITOR,
        SHARE_FREEZE_REPORT,
        DELETE_FREEZE_REPORTS,
        SHOW_ANALYZER_UPDATE_PREVIEW,
        CHECK_ANALYZER_UPDATE,
        SHOW_PYTHON_SDK_UPDATE_PREVIEW;

        fun getId(): Int = ordinal + 1
    }

    override fun getTitle(): String = "Debug"

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asCheck(DebugItem.DEBUG_CAMERA_METRICS.getId(), "Metrics in InstantCameraView")
            .setChecked(debugCameraMetrics))
        items.add(UItem.asCheck(DebugItem.FORCE_COMPACT_SAVED_MUSIC.getId(), "Compact Saved Music view")
            .setChecked(forceCompactSavedMusic))
        items.add(UItem.asCheck(DebugItem.DISABLE_API_REQUESTS.getId(), "Disable exteraGram API requests")
            .setChecked(disableApiRequests))
        items.add(UItem.asCheck(DebugItem.DISABLE_CHAT_FADE_WALLPAPER_BLEND.getId(), "Disable chat fade wallpaper blend")
            .setChecked(disableChatFadeWallpaperBlend))
        items.add(UItem.asCheck(DebugItem.CHAT_FADE_USE_WHITE_BACKGROUND.getId(), "Use windowBackgroundWhite for chat fade")
            .setChecked(chatFadeUseWhiteBackground))
        items.add(UItem.asCheck(DebugItem.PROFILE_COLOR_DESIGN.getId(), "Profile color design")
            .setChecked(profileColorDesign))
        items.add(UItem.asShadow())

        items.add(UItem.asButton(DebugItem.FORCE_REFRESH_REMOTE_CONFIG.getId(), "Refresh Remote Config"))
        items.add(UItem.asButton(DebugItem.CLEAR_DB.getId(), "Clear exteraDatabase"))
        items.add(UItem.asButton(DebugItem.CLEAR_TRANSLATIONS.getId(), "Clear translations cache"))
        items.add(UItem.asButton(DebugItem.SHOW_SUPPORTER_WELCOME.getId(), "Show supporter welcome sheet"))
        items.add(UItem.asButton(DebugItem.SHOW_SUPPORTER_PROMO.getId(), "Show supporter promo sheet"))
        items.add(UItem.asShadow())

        items.add(UItem.asButton(DebugItem.SET_IPCONFIG_OVERRIDE.getId(), "Set ipconfigv3 override", getIpConfigOverrideValue()))
        items.add(UItem.asButton(DebugItem.CLEAR_IPCONFIG_OVERRIDE.getId(), "Clear ipconfigv3 override"))
        items.add(UItem.asShadow())

        if (PythonSdkUpdateDebug.isAvailable) {
            items.add(UItem.asButton(DebugItem.SHOW_PYTHON_SDK_UPDATE_PREVIEW.getId(), "Show Python SDK update sheet"))
        }
        if (AnalyzerUpdateDebug.isAvailable) {
            items.add(UItem.asButton(DebugItem.SHOW_ANALYZER_UPDATE_PREVIEW.getId(), "Show analyzer update sheet"))
            items.add(UItem.asButton(DebugItem.CHECK_ANALYZER_UPDATE.getId(), "Check analyzer update now"))
        }
        if (PythonSdkUpdateDebug.isAvailable || AnalyzerUpdateDebug.isAvailable) {
            items.add(UItem.asShadow())
        }

        items.add(UItem.asCheck(DebugItem.HEAP_MONITOR.getId(), "Heap limit monitor")
            .setChecked(heapMonitorEnabled))
        if (heapMonitorEnabled) {
            items.add(createHeapLimitSliderItem())
        }
        items.add(UItem.asButton(DebugItem.DUMP_HEAP.getId(), "Dump & share heap", getHeapUsageValue()))
        val lastDump = HeapMonitor.getLastDump()
        if (lastDump != null) {
            items.add(UItem.asButton(DebugItem.SHARE_HEAP_DUMP.getId(), "Share last heap dump", AndroidUtilities.formatFileSize(lastDump.length())))
        }
        val dumpsSize = HeapMonitor.getDumpsSize()
        if (dumpsSize > 0) {
            items.add(UItem.asButton(DebugItem.DELETE_HEAP_DUMPS.getId(), "Delete heap dumps", AndroidUtilities.formatFileSize(dumpsSize)))
        }
        items.add(UItem.asShadow())

        items.add(UItem.asCheck(DebugItem.LOAD_MONITOR.getId(), "Load monitor")
            .setChecked(loadMonitorEnabled))
        if (loadMonitorEnabled) {
            items.add(createLoadThresholdSliderItem())
        }
        items.add(UItem.asButton(DebugItem.CAPTURE_LOAD_REPORT.getId(), "Capture & share load report", LoadMonitor.getSummary()))
        val lastReport = LoadMonitor.getLastReport()
        if (lastReport != null) {
            items.add(UItem.asButton(DebugItem.SHARE_LOAD_REPORT.getId(), "Share last load report", AndroidUtilities.formatFileSize(lastReport.length())))
        }
        val reportsSize = LoadMonitor.getReportsSize()
        if (reportsSize > 0) {
            items.add(UItem.asButton(DebugItem.DELETE_LOAD_REPORTS.getId(), "Delete load reports", AndroidUtilities.formatFileSize(reportsSize)))
        }
        items.add(UItem.asShadow())

        items.add(UItem.asCheck(DebugItem.FREEZE_MONITOR.getId(), "Freeze monitor")
            .setChecked(freezeMonitorEnabled))
        if (freezeMonitorEnabled) {
            items.add(createFreezeThresholdSliderItem())
        }
        val freezeReport = FreezeMonitor.getReport()
        if (freezeReport != null) {
            items.add(UItem.asButton(DebugItem.SHARE_FREEZE_REPORT.getId(), "Share freeze report", AndroidUtilities.formatFileSize(freezeReport.length())))
        }
        val freezeReportsSize = FreezeMonitor.getReportsSize()
        if (freezeReportsSize > 0) {
            items.add(UItem.asButton(DebugItem.DELETE_FREEZE_REPORTS.getId(), "Delete freeze reports", AndroidUtilities.formatFileSize(freezeReportsSize)))
        }
        items.add(UItem.asShadow())
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        if (item.id <= 0 || item.id > DebugItem.entries.size) return
        when (DebugItem.entries[item.id - 1]) {
            DebugItem.DEBUG_CAMERA_METRICS -> toggleBooleanSettingAndRefresh(item) { debugCameraMetrics = it }
            DebugItem.FORCE_COMPACT_SAVED_MUSIC -> toggleBooleanSettingAndRefresh(item) { forceCompactSavedMusic = it }
            DebugItem.DISABLE_API_REQUESTS -> toggleBooleanSettingAndRefresh(item) { disableApiRequests = it }
            DebugItem.DISABLE_CHAT_FADE_WALLPAPER_BLEND -> toggleBooleanSettingAndRefresh(item) { disableChatFadeWallpaperBlend = it }
            DebugItem.CHAT_FADE_USE_WHITE_BACKGROUND -> toggleBooleanSettingAndRefresh(item) { chatFadeUseWhiteBackground = it }
            DebugItem.PROFILE_COLOR_DESIGN -> toggleBooleanSettingAndRefresh(item) { profileColorDesign = it }
            DebugItem.FORCE_REFRESH_REMOTE_CONFIG -> RemoteUtils.forceRefresh()
            DebugItem.CLEAR_DB -> {
                Utilities.globalQueue.postRunnable {
                    ExteraDatabase.getInstance().clearAllTables()
                    ApiController.resetSyncState()
                    AndroidUtilities.runOnUIThread {
                        BulletinFactory.of(this).createSimpleBulletin(
                            R.raw.contact_check,
                            "Successfully cleared all tables."
                        ).show()
                    }
                }
            }
            DebugItem.CLEAR_TRANSLATIONS -> {
                messagesController.translateController.clearTranslationCache()

                val dialogMessages = messagesController.dialogMessage
                if (dialogMessages != null) {
                    for (i in 0 until dialogMessages.size()) {
                        val dialogMessage = dialogMessages.valueAt(i) ?: continue
                        for (j in 0 until dialogMessage.size) {
                            messagesController.translateController.clearMessageTranslationState(
                                dialogMessage[j]
                            )
                        }
                    }
                }

                val dialogMessagesByIds = messagesController.dialogMessagesByIds
                if (dialogMessagesByIds != null) {
                    for (i in 0 until dialogMessagesByIds.size) {
                        messagesController.translateController.clearMessageTranslationState(
                            dialogMessagesByIds.valueAt(i)
                        )
                    }
                }

                val dialogMessagesByRandomIds = messagesController.dialogMessagesByRandomIds
                if (dialogMessagesByRandomIds != null) {
                    for (i in 0 until dialogMessagesByRandomIds.size()) {
                        messagesController.translateController.clearMessageTranslationState(
                            dialogMessagesByRandomIds.valueAt(i)
                        )
                    }
                }

                AndroidUtilities.runOnUIThread {
                    BulletinFactory.of(this).createSimpleBulletin(
                        R.raw.contact_check,
                        "Translation cache cleared."
                    ).show()
                }
            }
            DebugItem.SHOW_SUPPORTER_WELCOME -> SupporterWelcomeSheet.show(this, SupporterWelcomeSheet.MODE_GRANTED)
            DebugItem.SHOW_SUPPORTER_PROMO -> showDialog(SupporterBottomSheet(this, null, resourceProvider))
            DebugItem.SET_IPCONFIG_OVERRIDE -> showIpConfigOverrideDialog()
            DebugItem.CLEAR_IPCONFIG_OVERRIDE -> {
                ConnectionsManager.setDebugDnsConfigOverride(null)
                listView.adapter.update(true)
                BulletinFactory.of(this).createSimpleBulletin(
                    R.raw.contact_check,
                    "ipconfigv3 override cleared."
                ).show()
            }
            DebugItem.HEAP_MONITOR -> toggleBooleanSettingAndRefresh(item) { HeapMonitor.setEnabled(it) }
            DebugItem.DUMP_HEAP -> parentActivity?.let { HeapMonitor.dumpAndShare(it, resourceProvider) }
            DebugItem.SHARE_HEAP_DUMP -> parentActivity?.let { HeapMonitor.shareLastDump(it) }
            DebugItem.DELETE_HEAP_DUMPS -> HeapMonitor.deleteDumps {
                listView.adapter.update(true)
                BulletinFactory.of(this).createSimpleBulletin(
                    R.raw.contact_check,
                    "Heap dumps deleted."
                ).show()
            }
            DebugItem.LOAD_MONITOR -> toggleBooleanSettingAndRefresh(item) { LoadMonitor.setEnabled(it) }
            DebugItem.CAPTURE_LOAD_REPORT -> parentActivity?.let { LoadMonitor.captureAndShare(it) }
            DebugItem.SHARE_LOAD_REPORT -> parentActivity?.let { LoadMonitor.shareLastReport(it) }
            DebugItem.DELETE_LOAD_REPORTS -> LoadMonitor.deleteReports {
                listView.adapter.update(true)
                BulletinFactory.of(this).createSimpleBulletin(
                    R.raw.contact_check,
                    "Load reports deleted."
                ).show()
            }
            DebugItem.FREEZE_MONITOR -> toggleBooleanSettingAndRefresh(item) { FreezeMonitor.setEnabled(it) }
            DebugItem.SHARE_FREEZE_REPORT -> parentActivity?.let { FreezeMonitor.shareReport(it) }
            DebugItem.DELETE_FREEZE_REPORTS -> FreezeMonitor.deleteReports {
                listView.adapter.update(true)
                BulletinFactory.of(this).createSimpleBulletin(
                    R.raw.contact_check,
                    "Freeze reports deleted."
                ).show()
            }
            DebugItem.SHOW_PYTHON_SDK_UPDATE_PREVIEW -> PythonSdkUpdateDebug.showPreview(this)
            DebugItem.SHOW_ANALYZER_UPDATE_PREVIEW -> AnalyzerUpdateDebug.showPreview(this)
            DebugItem.CHECK_ANALYZER_UPDATE -> AnalyzerUpdateDebug.checkNow { message ->
                if (parentActivity == null || finishing) return@checkNow
                BulletinFactory.of(this).createSimpleBulletin(R.raw.info, message).show()
            }
        }
    }

    private fun createFreezeThresholdSliderItem(): UItem {
        val step = FreezeMonitor.THRESHOLD_STEP_MS
        return UItem.asIntSlideView(
            1,
            FreezeMonitor.MIN_THRESHOLD_MS / step,
            FreezeMonitor.getThresholdMs() / step,
            FreezeMonitor.MAX_THRESHOLD_MS / step,
            { "${it * step} ms" },
            { FreezeMonitor.setThresholdMs(it * step) }
        )
    }

    private fun createLoadThresholdSliderItem(): UItem {
        return UItem.asIntSlideView(
            1,
            LoadMonitor.MIN_CPU_PERCENT,
            LoadMonitor.getCpuPercent(),
            LoadMonitor.MAX_CPU_PERCENT,
            { "$it% of a core" },
            { LoadMonitor.setCpuPercent(it) }
        )
    }

    private fun createHeapLimitSliderItem(): UItem {
        val step = HeapMonitor.LIMIT_STEP_MB
        return UItem.asIntSlideView(
            1,
            HeapMonitor.MIN_LIMIT_MB / step,
            HeapMonitor.getLimitMb() / step,
            HeapMonitor.getMaxLimitMb() / step,
            { "${it * step} MB" },
            { HeapMonitor.setLimitMb(it * step) }
        )
    }

    private fun getHeapUsageValue(): String {
        val used = AndroidUtilities.formatFileSize(HeapMonitor.getUsedBytes(), true, false)
        val max = AndroidUtilities.formatFileSize(Runtime.getRuntime().maxMemory(), true, false)
        return "$used / $max"
    }

    private fun getIpConfigOverrideValue(): String {
        val config = ConnectionsManager.getDebugDnsConfigOverride()
        if (config.isNullOrBlank()) return "Not set"
        return "${config.length} chars"
    }

    private fun showIpConfigOverrideDialog() {
        val activity = parentActivity ?: return
        val editText = EditTextBoldCursor(activity).apply {
            lineYFix = true
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
            setText(ConnectionsManager.getDebugDnsConfigOverride())
            setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider))
            setHintColor(Theme.getColor(Theme.key_dialogTextHint, resourceProvider))
            setHintText("Base64 from Firebase Remote Config")
            gravity = Gravity.TOP or Gravity.START
            minLines = 6
            maxLines = 10
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setCursorColor(Theme.getColor(Theme.key_dialogInputFieldActivated, resourceProvider))
            setLineColors(
                Theme.getColor(Theme.key_dialogInputField, resourceProvider),
                Theme.getColor(Theme.key_dialogInputFieldActivated, resourceProvider),
                Theme.getColor(Theme.key_text_RedRegular, resourceProvider)
            )
            background = null
            setPadding(0, AndroidUtilities.dp(6f), 0, AndroidUtilities.dp(6f))
            setSelection(text?.length ?: 0)
        }

        val container = FrameLayout(activity)
        container.addView(
            editText,
            LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT.toFloat(),
                Gravity.TOP or Gravity.START,
                24f,
                12f,
                24f,
                0f
            )
        )

        val dialog = AlertDialog.Builder(activity)
            .setTitle("ipconfigv3 override")
            .setView(container)
            .setPositiveButton("Apply") { _, _ ->
                val value = editText.text?.toString()?.trim().orEmpty()
                if (TextUtils.isEmpty(value)) {
                    ConnectionsManager.setDebugDnsConfigOverride(null)
                    listView.adapter.update(true)
                    BulletinFactory.of(this).createSimpleBulletin(
                        R.raw.contact_check,
                        "ipconfigv3 override cleared."
                    ).show()
                    return@setPositiveButton
                }

                val applied = ConnectionsManager.setAndApplyDebugDnsConfigOverride(currentAccount, value)
                listView.adapter.update(true)
                BulletinFactory.of(this).createSimpleBulletin(
                    if (applied) R.raw.contact_check else R.raw.error,
                    if (applied) "ipconfigv3 override saved and submitted." else "Failed to decode ipconfigv3 override."
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .create()
        showDialog(dialog)
    }
}
