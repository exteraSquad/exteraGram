/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.ui

import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.view.View
import androidx.core.content.edit
import com.exteragram.messenger.plugins.PluginsController
import com.exteragram.messenger.plugins.PythonPluginsEngine
import com.exteragram.messenger.plugins.analyzer.AnalyzerUpdate
import com.exteragram.messenger.plugins.analyzer.AnalyzerUpdater
import com.exteragram.messenger.plugins.analyzer.PluginAnalyzer
import com.exteragram.messenger.plugins.catalog.PluginCatalogController
import com.exteragram.messenger.plugins.ui.components.UnknownSourcesBottomSheet
import com.exteragram.messenger.pluginsCompactView
import com.exteragram.messenger.pluginsDevMode
import com.exteragram.messenger.pluginsDisableArtOpts
import com.exteragram.messenger.pluginsEngine
import com.exteragram.messenger.pluginsPySdkAutoUpdate
import com.exteragram.messenger.pluginsPySdkBetaVersions
import com.exteragram.messenger.pluginsSafeMode
import com.exteragram.messenger.pluginsStaticAnalysis
import com.exteragram.messenger.pluginsUnknownSources
import com.exteragram.messenger.preferences.BasePreferencesActivity
import com.exteragram.messenger.preferences.utils.SettingsRegistry
import com.exteragram.messenger.security.SensitiveRequestSwitch
import com.exteragram.messenger.utils.text.LocaleUtils
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.browser.Browser
import org.telegram.ui.Cells.TextCheckCell
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter

class PluginsInfoActivity : BasePreferencesActivity(),
    NotificationCenter.NotificationCenterDelegate {
    enum class PreferenceItem {
        DEVELOPER_MODE,
        COMPACT_VIEW,
        SAFE_MODE,
        SDK_AUTO_UPDATE,
        SDK_BETA_VERSIONS,
        CHECK_SDK_UPDATES,
        RESTORE_SDK_FROM_APK,
        DOCUMENTATION,
        TRUSTED_PLUGINS,
        PLUGINS_DISABLE_ART_OPTS,
        SDK_HEADER,
        UNKNOWN_SOURCES,
        STATIC_ANALYSIS,
        SENSITIVE_REQUESTS,
        CHECK_ANALYZER_UPDATES;

        fun getId(): Int = ordinal + 1
    }

    private var analyzerVersion: String? = null
    private var analyzerUpdatePending = false

    override fun getTitle(): String = getString(R.string.PluginsEngine)

    override fun onFragmentCreate(): Boolean {
        super.onFragmentCreate()

        NotificationCenter.getGlobalInstance().apply {
            addObserver(this@PluginsInfoActivity, NotificationCenter.pluginsPySdkInfoChanged)
            addObserver(this@PluginsInfoActivity, NotificationCenter.pluginAnalyzerUpdateChanged)
        }
        PythonPluginsEngine.Updater.notifyWhenChangeStatus = true
        return true
    }

    override fun onResume() {
        super.onResume()
        refreshAnalyzerInfo()
    }

    override fun onFragmentDestroy() {
        super.onFragmentDestroy()

        NotificationCenter.getGlobalInstance().apply {
            removeObserver(this@PluginsInfoActivity, NotificationCenter.pluginsPySdkInfoChanged)
            removeObserver(this@PluginsInfoActivity, NotificationCenter.pluginAnalyzerUpdateChanged)
        }
        PythonPluginsEngine.Updater.notifyWhenChangeStatus = false
        if (PythonPluginsEngine.Updater.status == 2 || PythonPluginsEngine.Updater.status == 5) {
            PythonPluginsEngine.Updater.status = 0
        }
        AnalyzerUpdater.clearResult()
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (fragmentView == null || finishing) return
        when (id) {
            NotificationCenter.pluginsPySdkInfoChanged -> listView.adapter.update(true)
            NotificationCenter.pluginAnalyzerUpdateChanged -> {
                listView.adapter.update(true)
                if (AnalyzerUpdater.status != AnalyzerUpdater.Status.CHECKING &&
                    AnalyzerUpdater.status != AnalyzerUpdater.Status.LOADING
                ) refreshAnalyzerInfo()
            }
        }
    }

    private fun refreshAnalyzerInfo() {
        PluginAnalyzer.getUpdateInfo { version, pending ->
            if (fragmentView == null || finishing) return@getUpdateInfo
            if (analyzerVersion != version || analyzerUpdatePending != pending) {
                analyzerVersion = version
                analyzerUpdatePending = pending
                listView.adapter.update(true)
            }
        }
    }

    private fun analyzerSubtitle(): CharSequence {
        val version = analyzerVersion?.let { "v$it" }
        return when (AnalyzerUpdater.status) {
            AnalyzerUpdater.Status.CHECKING -> getString(R.string.CheckingForUpdates)
            AnalyzerUpdater.Status.LOADING -> getString(R.string.LoadingUpdate)
            AnalyzerUpdater.Status.FAILED -> getString(R.string.PluginComponentUpdateFailed)
            AnalyzerUpdater.Status.CURRENT, AnalyzerUpdater.Status.IDLE -> when {
                analyzerUpdatePending -> getString(R.string.PluginAnalyzerUpdatePending)
                AnalyzerUpdater.status == AnalyzerUpdater.Status.CURRENT -> getString(R.string.LatestVersionInstalled) + (version?.let { " ($it)" } ?: "")
                else -> version ?: getString(R.string.PluginAnalyzerBundled)
            }
        }
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asHeader(getString(R.string.Settings)))
        items.add(
            UItem.asCheck(
                PreferenceItem.DEVELOPER_MODE.getId(),
                getString(R.string.PluginsDevMode),
                R.drawable.msg_settings
            )
                .setChecked(pluginsDevMode)
                .setEnabled(pluginsEngine && !pluginsSafeMode)
                .setSearchable(this)
                .setLinkAlias("pluginsDeveloperMode", this),
        )
        items.add(
            UItem.asCheck(
                PreferenceItem.COMPACT_VIEW.getId(),
                getString(R.string.PluginsCompactView),
                R.drawable.msg_topics
            )
                .setChecked(pluginsCompactView)
                .setEnabled(pluginsEngine)
                .setSearchable(this)
                .setLinkAlias("pluginsCompactView", this),
        )
        items.add(
            UItem.asCheck(
                PreferenceItem.PLUGINS_DISABLE_ART_OPTS.getId(),
                getString(R.string.PluginsDisableArt),
                R.drawable.msg_link2
            )
                .setChecked(pluginsDisableArtOpts)
                .setEnabled(pluginsEngine)
                .setSearchable(this)
                .setValue(getString(R.string.PluginsDisableArtInfo))
                .setMultiline(true)
                .setLinkAlias("pluginsDisableArtOpts", this),
        )
        items.add(
            UItem.asCheck(
                PreferenceItem.UNKNOWN_SOURCES.getId(),
                getString(R.string.PluginsUnknownSources),
                R.drawable.msg_download
            )
                .setChecked(pluginsUnknownSources)
                .setEnabled(pluginsEngine)
                .setSearchable(this)
                .setValue(getString(R.string.PluginsUnknownSourcesInfo2))
                .setMultiline(true)
                .setLinkAlias("pluginsUnknownSources", this),
        )
        items.add(
            UItem.asCheck(
                PreferenceItem.STATIC_ANALYSIS.getId(),
                getString(R.string.PluginsStaticAnalysis),
                R.drawable.msg_policy
            )
                .setChecked(pluginsStaticAnalysis)
                .setEnabled(pluginsEngine)
                .setSearchable(this)
                .setValue(getString(R.string.PluginsStaticAnalysisInfo))
                .setMultiline(true)
                .setLinkAlias("pluginsStaticAnalysis", this),
        )
        val sensitiveRequestsState = SensitiveRequestSwitch.getState()
        items.add(
            UItem.asCheck(
                PreferenceItem.SENSITIVE_REQUESTS.getId(),
                getString(R.string.SensitiveRequestsConfirm),
                R.drawable.msg_permissions
            )
                .setChecked(sensitiveRequestsState != SensitiveRequestSwitch.STATE_DISABLED)
                .setSearchable(this)
                .setValue(
                    when (sensitiveRequestsState) {
                        SensitiveRequestSwitch.STATE_PENDING -> LocaleController.formatString(R.string.SensitiveRequestsConfirmPending, SensitiveRequestSwitch.formatDisableAt())
                        SensitiveRequestSwitch.STATE_DISABLED -> getString(R.string.SensitiveRequestsConfirmDisabled)
                        else -> getString(R.string.SensitiveRequestsConfirmInfo)
                    }
                )
                .setMultiline(true)
                .setLinkAlias("sensitiveRequestConfirmations", this),
        )
        items.add(
            UItem.asCheck(
                PreferenceItem.SAFE_MODE.getId(),
                getString(R.string.PluginsSafeMode),
                R.drawable.msg_secret
            )
                .setChecked(pluginsSafeMode)
                .setSearchable(this)
                .setLinkAlias("pluginsSafeMode", this),
        )
        items.add(UItem.asShadow(getString(R.string.PluginsSafeModeInfo2)))

        items.add(
            UItem.asHeader(
                PreferenceItem.SDK_HEADER.getId(),
                if (SettingsRegistry.markAsNewFeature("Plugins-Python-SDK")) LocaleUtils.applyNewSpan(
                    getString(R.string.PluginSdkComponents)
                ) else getString(R.string.PluginSdkComponents),
            ),
        )
        val sdkStatus = PythonPluginsEngine.Updater.status
        val checkingAnalyzer = AnalyzerUpdater.status == AnalyzerUpdater.Status.CHECKING
        items.add(
            uButton(
                PreferenceItem.CHECK_SDK_UPDATES.getId(),
                "Python SDK",
                icon = R.drawable.msg_plugins,
                subtext = PythonPluginsEngine.Updater.getStateString(),
                linkAlias = "pluginsPySdkCheckUpdates",
            ).setEnabled(!checkingAnalyzer && (sdkStatus == 0 || sdkStatus == 2 || sdkStatus == 5)),
        )
        if (AnalyzerUpdate.enabled) {
            items.add(
                uButton(
                    PreferenceItem.CHECK_ANALYZER_UPDATES.getId(),
                    getString(R.string.PluginAnalyzerComponent),
                    icon = R.drawable.msg_policy,
                    subtext = analyzerSubtitle(),
                    linkAlias = "pluginAnalyzerCheckUpdates",
                ).setEnabled(sdkStatus != 1 && AnalyzerUpdater.canCheck),
            )
        }
        items.add(
            UItem.asCheck(
                PreferenceItem.SDK_AUTO_UPDATE.getId(),
                getString(R.string.PluginsPySdkAutoUpdate)
            )
                .setChecked(pluginsPySdkAutoUpdate)
                .setSearchable(this)
                .setLinkAlias("pluginsPySdkAutoUpdate", this),
        )
        items.add(
            UItem.asCheck(
                PreferenceItem.SDK_BETA_VERSIONS.getId(),
                getString(R.string.PluginsPySdkEnableBetaVersion)
            )
                .setChecked(pluginsPySdkBetaVersions)
                .setSearchable(this)
                .setLinkAlias("pluginsPySdkBetaVersions", this),
        )
        if (pluginsDevMode && !pluginsEngine && !PythonPluginsEngine.Updater.isSdkFromApk()) {
            items.add(
                UItem.asButton(
                    PreferenceItem.RESTORE_SDK_FROM_APK.getId(),
                    getString(R.string.RestoreSdkFromApk)
                )
                    .red()
                    .setIcon(R.drawable.msg_reset),
            )
        }
        items.add(UItem.asShadow())

        items.add(UItem.asHeader(getString(R.string.Links)))
        items.add(
            UItem.asButton(
                PreferenceItem.DOCUMENTATION.getId(),
                getString(R.string.PluginsDocumentation)
            )
                .setSearchable(this)
                .setIcon(R.drawable.menu_intro)
                .setLinkAlias("pluginsDocumentation", this),
        )
        items.add(
            UItem.asButton(
                PreferenceItem.TRUSTED_PLUGINS.getId(),
                getString(R.string.PluginsTrusted)
            )
                .accent()
                .setIcon(R.drawable.msg2_policy)
                .setSearchable(this)
                .setLinkAlias("trustedPlugins", this),
        )

        val htmlParsed: Spannable = SpannableString(
            Html.fromHtml(
                getString(R.string.PluginsPoweredBy),
                Html.FROM_HTML_MODE_LEGACY
            )
        )
        items.add(UItem.asShadow(LocaleUtils.formatWithHtmlURLs(htmlParsed)))
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        if (!item.enabled || item.id <= 0 || item.id > PreferenceItem.entries.size) return
        val clickedItem = PreferenceItem.entries[item.id - 1]

        if (view is TextCheckCell && (pluginsEngine || clickedItem == PreferenceItem.SAFE_MODE || clickedItem == PreferenceItem.SDK_AUTO_UPDATE || clickedItem == PreferenceItem.SDK_BETA_VERSIONS || clickedItem == PreferenceItem.SENSITIVE_REQUESTS)) {
            when (clickedItem) {
                PreferenceItem.DEVELOPER_MODE -> toggleBooleanSettingAndRefresh(item) { value ->
                    pluginsDevMode = value
                    PluginsController.getInstance().checkDevServers()
                    BulletinFactory.of(this@PluginsInfoActivity)
                        .createSimpleBulletin(
                            if (pluginsDevMode) R.raw.contact_check else R.raw.error,
                            getString(if (pluginsDevMode) R.string.PluginsDevServerLaunched else R.string.PluginsDevServerStopped),
                        )
                        .show()
                }

                PreferenceItem.COMPACT_VIEW -> toggleBooleanSettingAndRefresh(item) { value ->
                    pluginsCompactView = value
                    NotificationCenter.getGlobalInstance()
                        .postNotificationName(NotificationCenter.reloadInterface)
                }

                PreferenceItem.UNKNOWN_SOURCES -> {
                    if (pluginsUnknownSources) {
                        toggleBooleanSettingAndRefresh(item) { pluginsUnknownSources = it }
                    } else {
                        UnknownSourcesBottomSheet(this) {
                            toggleBooleanSettingAndRefresh(item) { pluginsUnknownSources = it }
                        }.show()
                    }
                }

                PreferenceItem.SAFE_MODE -> {
                    val prefs = PluginsController.getInstance().preferences
                    toggleBooleanSettingAndRefresh(item) { value ->
                        pluginsSafeMode = value
                        prefs.edit {
                            if (value) {
                                putString("crashed_plugin_id", "manual!")
                            } else {
                                remove("crashed_plugin_id")
                            }
                        }
                        PluginsController.getInstance().restart(value)
                    }
                }

                PreferenceItem.PLUGINS_DISABLE_ART_OPTS -> toggleBooleanSettingAndRefresh(item) { value ->
                    pluginsDisableArtOpts = value
                    PluginsController.applyArtOpts()
                    showRestartBulletin()
                }

                PreferenceItem.STATIC_ANALYSIS -> toggleBooleanSettingAndRefresh(item) { pluginsStaticAnalysis = it }

                PreferenceItem.SENSITIVE_REQUESTS -> {
                    if (SensitiveRequestSwitch.getState() == SensitiveRequestSwitch.STATE_ENFORCED) {
                        SensitiveRequestSwitch.requestDisable(this) { listView.adapter.update(true) }
                    } else {
                        SensitiveRequestSwitch.enforce()
                        listView.adapter.update(true)
                    }
                }

                PreferenceItem.SDK_AUTO_UPDATE -> toggleBooleanSettingAndRefresh(item) { pluginsPySdkAutoUpdate = it }

                PreferenceItem.SDK_BETA_VERSIONS -> toggleBooleanSettingAndRefresh(item) { pluginsPySdkBetaVersions = it }

                else -> Unit
            }
        } else if (clickedItem == PreferenceItem.DOCUMENTATION || clickedItem == PreferenceItem.TRUSTED_PLUGINS) {
            Browser.openUrl(
                parentActivity,
                if (clickedItem == PreferenceItem.DOCUMENTATION) "https://plugins.exteragram.app/" else "https://t.me/addlist/${PluginCatalogController.TRUSTED_FOLDER_SLUG}",
            )
        } else if (clickedItem == PreferenceItem.CHECK_SDK_UPDATES) {
            if (AnalyzerUpdater.status != AnalyzerUpdater.Status.CHECKING) {
                PythonPluginsEngine.Updater.checkUpdates(true, this)
            }
        } else if (clickedItem == PreferenceItem.CHECK_ANALYZER_UPDATES) {
            if (PythonPluginsEngine.Updater.status != 1) {
                AnalyzerUpdater.check(force = true, source = this)
            }
        } else if (clickedItem == PreferenceItem.RESTORE_SDK_FROM_APK) {
            PythonPluginsEngine.Updater.restoreSdkFromApk()
            BulletinFactory.of(this@PluginsInfoActivity)
                .createSimpleBulletin(R.raw.contact_check, getString(R.string.RestartRequired))
                .show()
            listView.adapter.update(true)
        }
    }
}
