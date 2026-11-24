/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.utils

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.edit
import com.exteragram.messenger.plugins.Plugin
import com.exteragram.messenger.plugins.PluginsController
import com.exteragram.messenger.plugins.pip.PipController
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.LaunchActivity
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

class PluginsWatchdog(private val controller: PluginsController) {
    private data class ExecutionInfo(val pluginId: String)

    private val executingPlugins = ConcurrentHashMap<Thread, ExecutionInfo>()
    private val frozenExecutions = ConcurrentHashMap<Thread, ExecutionInfo>()
    private val scheduledChecks = ConcurrentHashMap<Thread, ScheduledFuture<*>>()
    private var scheduler: ScheduledExecutorService? = null

    fun start() {
        val currentScheduler = scheduler
        if (currentScheduler != null && !currentScheduler.isShutdown) {
            return
        }

        scheduler = ScheduledThreadPoolExecutor(1).apply {
            removeOnCancelPolicy = true
        }
    }

    fun stop() {
        for (check in scheduledChecks.values) {
            check.cancel(false)
        }
        scheduledChecks.clear()
        scheduler?.shutdownNow()
        scheduler = null
        for (info in frozenExecutions.values) {
            controller.plugins[info.pluginId]?.setNotResponding(false)
        }
        frozenExecutions.clear()
        executingPlugins.clear()
    }

    fun onPluginExecutionStarted(pluginId: String?) {
        if (pluginId == null) {
            return
        }
        val thread = Thread.currentThread()
        val info = ExecutionInfo(pluginId)
        executingPlugins[thread] = info
        val previousFrozenExecution = frozenExecutions.remove(thread)
        if (previousFrozenExecution != null) {
            if (previousFrozenExecution.pluginId == pluginId) {
                frozenExecutions[thread] = info
            } else if (frozenExecutions.values.none { it.pluginId == previousFrozenExecution.pluginId }) {
                controller.plugins[previousFrozenExecution.pluginId]?.setNotResponding(false)
                NotificationCenter.getGlobalInstance()
                    .postNotificationNameOnUIThread(NotificationCenter.pluginIsNotResponding)
            }
        }
        scheduledChecks.remove(thread)?.cancel(false)
        val currentScheduler = scheduler ?: return
        try {
            scheduledChecks[thread] = currentScheduler.schedule({
                try {
                    var stateChanged = false
                    executingPlugins.computeIfPresent(thread) { _, currentInfo ->
                        if (currentInfo === info) {
                            frozenExecutions[thread] = info
                            val plugin = controller.plugins[pluginId]
                            if (plugin != null && !plugin.isNotResponding()) {
                                plugin.setNotResponding(true)
                                stateChanged = true
                            }
                        }
                        currentInfo
                    }
                    if (stateChanged) {
                        NotificationCenter.getGlobalInstance()
                            .postNotificationNameOnUIThread(NotificationCenter.pluginIsNotResponding)
                    }
                } catch (e: Exception) {
                    FileLog.e(e)
                }
            }, 5, TimeUnit.SECONDS)
        } catch (_: RejectedExecutionException) {
            executingPlugins.remove(thread, info)
        }
    }

    fun onPluginExecutionFinished(pluginId: String) {
        val thread = Thread.currentThread()
        val info = executingPlugins[thread]
        if (info != null && info.pluginId == pluginId && executingPlugins.remove(thread, info)) {
            scheduledChecks.remove(thread)?.cancel(false)

            val frozenExecution = frozenExecutions.remove(thread)
            if (frozenExecution != null && frozenExecutions.values.none { it.pluginId == frozenExecution.pluginId }) {
                controller.plugins[frozenExecution.pluginId]?.setNotResponding(false)
                NotificationCenter.getGlobalInstance()
                    .postNotificationNameOnUIThread(NotificationCenter.pluginIsNotResponding)
            }
        }
    }

    fun forceDisablePlugin(pluginId: String, activity: Activity?) {
        disablePluginPref(pluginId)
        restartApp(activity)
    }

    fun forceDeletePlugin(pluginId: String, activity: Activity?) {
        disablePluginPref(pluginId)
        PluginsController.setPluginPinned(pluginId, false)
        controller.cleanupPlugin(pluginId)
        try {
            PipController.uninstallDependencies(pluginId)
        } catch (e: Exception) {
            FileLog.e("Failed to uninstall dependencies for frozen plugin $pluginId", e)
        }
        controller.clearPluginSettingsPreferences(pluginId, true)
        File(controller.pluginsDir, "$pluginId.py").delete()
        controller.plugins.remove(pluginId)
        controller.notifyPluginsChanged()
        restartApp(activity)
    }

    private fun disablePluginPref(pluginId: String) {
        controller.preferences.edit {
            putBoolean(PluginsController.PREF_PLUGIN_ENABLED_KEY_PREFIX + pluginId, false)
        }
    }

    private fun restartApp(activity: Activity?) {
        AndroidUtilities.runOnUIThread({
            if (activity != null) {
                val pm: PackageManager = activity.packageManager
                val intent: Intent? = pm.getLaunchIntentForPackage(activity.packageName)
                activity.finishAffinity()
                if (intent != null) {
                    activity.startActivity(intent)
                }
            }
            exitProcess(0)
        }, 200)
    }

    companion object {
        @JvmStatic
        fun showNotRespondingAlert(plugin: Plugin) {
            val fragment = LaunchActivity.getSafeLastFragment() ?: return
            val activity = fragment.parentActivity
            val builder = AlertDialog.Builder(activity, fragment.resourceProvider)
                .setTitle(
                    LocaleController.formatString(
                        R.string.PluginIsNotRespondingAlert,
                        plugin.getName()
                    )
                )
                .setItems(
                    arrayOf(
                        LocaleController.getString(R.string.WaitMore),
                        LocaleController.getString(R.string.Disable),
                        LocaleController.getString(R.string.Delete)
                    ),
                    intArrayOf(
                        R.drawable.msg_recent,
                        R.drawable.msg_block,
                        R.drawable.msg_delete
                    )
                ) { _, which ->
                    if (which == 1) {
                        PluginsController.getInstance().watchdog.forceDisablePlugin(
                            plugin.getId(),
                            activity
                        )
                    } else if (which == 2) {
                        PluginsController.getInstance().watchdog.forceDeletePlugin(
                            plugin.getId(),
                            activity
                        )
                    }
                }

            val dialog = builder.create()
            dialog.show()
            dialog.setItemColor(
                dialog.itemsCount - 1,
                Theme.getColor(Theme.key_text_RedBold),
                Theme.getColor(Theme.key_text_RedRegular)
            )
        }
    }
}