/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import com.exteragram.messenger.plugins.hooks.MenuItemRecord
import com.exteragram.messenger.plugins.hooks.PluginsHooks
import com.exteragram.messenger.plugins.models.SettingItem
import com.exteragram.messenger.plugins.ui.components.InstallPluginBottomSheet
import com.exteragram.messenger.plugins.utils.MenuContextBuilder
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessageObject
import org.telegram.messenger.R
import org.telegram.messenger.SendMessagesHelper
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.BulletinFactory
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class PluginsController private constructor() : PluginsHooks {
    val plugins = ConcurrentHashMap<String, Plugin>()

    val preferences: SharedPreferences =
        ApplicationLoader.applicationContext.getSharedPreferences("plugin_settings", Activity.MODE_PRIVATE)

    fun init() {
    }

    fun init(startWithSafeMode: Boolean) {
    }

    fun init(startWithSafeMode: Boolean, onDone: Runnable?) {
        onDone?.run()
    }

    fun init(onDone: Runnable?) {
        onDone?.run()
    }

    fun shutdown(onDone: Runnable?) {
        onDone?.run()
    }

    fun isInitialized(): Boolean = false

    fun isLifecycleActive(): Boolean = false

    fun restart() {
    }

    fun restart(startWithSafeMode: Boolean) {
    }

    @JvmOverloads
    fun showInstallDialog(fragment: BaseFragment, messageObject: MessageObject?, filePath: String? = null) {
        BulletinFactory.of(fragment)
            .createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.PluginsNotSupported))
            .show()
    }

    @JvmOverloads
    fun showInstallDialog(fragment: BaseFragment, filePath: String?, @Suppress("UNUSED_PARAMETER") trusted: Boolean = false) {
        BulletinFactory.of(fragment)
            .createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.PluginsNotSupported))
            .show()
    }

    fun openRepositoryLink(fragment: BaseFragment, username: String) {
        BulletinFactory.of(fragment)
            .createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.PluginsNotSupported))
            .show()
    }

    fun openGitRepositoryLink(fragment: BaseFragment, url: String) {
        openRepositoryLink(fragment, url)
    }

    fun loadPluginSettings() {
    }

    fun loadPluginSettings(pluginId: String?) {
    }

    fun getMenuItemsForLocation(menuType: String?, builder: MenuContextBuilder?): List<MenuItemRecord> = Collections.emptyList()

    fun getMenuItemsForLocation(menuType: String?, contextData: Map<String, Any>?): List<MenuItemRecord> = Collections.emptyList()

    override fun executePreRequestHook(requestName: String, account: Int, request: TLObject?): TLObject? = request

    override fun executePostRequestHook(
        requestName: String,
        account: Int,
        response: TLObject?,
        error: TLRPC.TL_error?,
    ): PluginsHooks.PostRequestResult = PluginsHooks.PostRequestResult(response, error)

    override fun executeUpdateHook(updateName: String, account: Int, update: TLRPC.Update?): TLRPC.Update? = update

    override fun executeUpdatesHook(containerName: String, account: Int, updates: TLRPC.Updates?): TLRPC.Updates? = updates

    override fun executeSendMessageHook(
        account: Int,
        params: SendMessagesHelper.SendMessageParams?,
    ): SendMessagesHelper.SendMessageParams? = params

    fun executeOnAppEvent(eventType: String?) {
    }

    fun hasXposedHook(methodName: String, vararg declaringClasses: Class<*>): Boolean = false

    interface PluginsEngine {
        fun isPlugin(file: File): Boolean

        fun init(callback: Runnable?)

        fun shutdown(callback: Runnable?)

        fun openInExternalApp(id: String)

        fun loadPluginSettings(id: String): List<SettingItem>

        fun executeOnAppEvent(eventType: String)

        fun executeUpdateHook(updateName: String, account: Int, update: TLRPC.Update?, pluginId: String): HookResult<TLRPC.Update>

        fun executeUpdatesHook(containerName: String, account: Int, updates: TLRPC.Updates?, pluginId: String): HookResult<TLRPC.Updates>

        fun executeSendMessageHook(
            account: Int,
            params: SendMessagesHelper.SendMessageParams?,
            pluginId: String,
        ): HookResult<SendMessagesHelper.SendMessageParams>

        fun showInstallDialog(fragment: BaseFragment, params: InstallPluginBottomSheet.PluginInstallParams)
    }

    class HookResult<T>(
        var result: T,
        var cancel: Boolean,
        var isFinal: Boolean,
    )

    companion object {
        const val PLUGIN_FILE_ICON_NONE = -1
        const val PLUGIN_FILE_ICON_ID_START = 101

        private object SingletonHolder {
            val instance = PluginsController()
        }

        @JvmStatic
        fun getInstance(): PluginsController = SingletonHolder.instance

        @JvmStatic
        fun registerFileIcon(extension: String?, drawable: Drawable?): Int = PLUGIN_FILE_ICON_NONE

        @JvmStatic
        fun unregisterFileIcon(extension: String?) {
        }

        @JvmStatic
        fun clearFileIcons() {
        }

        @JvmStatic
        fun getFileIconId(fileName: String?): Int = PLUGIN_FILE_ICON_NONE

        @JvmStatic
        fun isPluginFileIcon(icon: Int): Boolean = false

        @JvmStatic
        fun getPluginFileIconDrawable(icon: Int): Drawable? = null

        @JvmStatic
        fun isPluginEngineSupported(): Boolean = false

        @JvmStatic
        fun applyArtOpts() {
        }

        @JvmStatic
        fun isPlugin(messageObject: MessageObject?): Boolean {
            return messageObject?.documentName?.lowercase()?.endsWith(PluginsConstants.PLUGINS_EXT) == true
        }

        @JvmStatic
        fun isPlugin(file: File?, messageObject: MessageObject?): Boolean {
            return file?.name?.lowercase()?.endsWith(PluginsConstants.PLUGINS_EXT) == true
        }

        @JvmStatic
        fun openPluginSettings(pluginId: String?) {
        }

        @JvmStatic
        fun openPluginSettings(pluginId: String?, linkAlias: String?) {
        }
    }
}