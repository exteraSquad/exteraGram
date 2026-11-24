/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import com.exteragram.messenger.ExteraFileHandler
import com.exteragram.messenger.backup.PreferencesUtils
import com.exteragram.messenger.components.SupporterBottomSheet
import com.exteragram.messenger.feed.ui.FeedActivity
import com.exteragram.messenger.plugins.PluginsController
import com.exteragram.messenger.pluginsEngine
import com.exteragram.messenger.pluginsSafeMode
import com.exteragram.messenger.preferences.MainPreferencesActivity
import com.exteragram.messenger.preferences.OtherPreferencesActivity
import com.exteragram.messenger.preferences.utils.SettingsRegistry
import com.exteragram.messenger.utils.chats.ChatUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessageObject
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.EmojiPacksAlert
import org.telegram.ui.LaunchActivity
import org.telegram.ui.LaunchActivity.getSafeLastFragment
import org.telegram.ui.ProfileActivity
import java.io.File
import java.util.Collections
import java.util.Locale

object IntentsController {
    const val SAFE_MODE_INTENT: String = "com.exteragram.plugins.safemode"
    const val PLUGIN_REPOSITORY_LINK: String = "exteraPluginRepo"
    const val PLUGIN_REPOSITORY_QUERY_KEY: String = "c"
    const val PLUGIN_REPOSITORY_GIT_QUERY_KEY: String = "git"
    private val VIEW_FILE_EXTENSIONS = setOf("plugin", "extera", "icons", "pill")

    private val deeplinkCallbacks: Map<String, (Uri) -> Unit> = mapOf(
        "extera" to {
            AndroidUtilities.runOnUIThread {
                LaunchActivity.instance.presentFragment(MainPreferencesActivity())
            }
        },
        "export" to {
            getSafeLastFragment()?.let { fragment ->
                PreferencesUtils.getInstance().exportSettings(fragment)
            }
        },
        "feed" to {
            AndroidUtilities.runOnUIThread {
                FeedActivity.presentFeed(getSafeLastFragment())
            }
        },
        "support" to {
            getSafeLastFragment()?.let { fragment ->
                fragment.showDialog(SupporterBottomSheet(fragment, null, fragment.resourceProvider))
            }
        },
        "donate" to {
            AndroidUtilities.runOnUIThread {
                LaunchActivity.instance.presentFragment(OtherPreferencesActivity())
            }
        },
        "emoji" to { uri ->
            uri.getQueryParameter("id")?.toLongOrNull()?.let { emojiId ->
                getSafeLastFragment()?.let { fragment ->
                    AnimatedEmojiDrawable.findDocument(UserConfig.selectedAccount, emojiId)
                        ?.let { document ->
                            MessageObject.getInputStickerSet(document)?.let { inputStickerSet ->
                                fragment.showDialog(
                                    EmojiPacksAlert(
                                        fragment,
                                        fragment.parentActivity,
                                        fragment.resourceProvider,
                                        ArrayList(
                                            Collections.singletonList(inputStickerSet)
                                        )
                                    ).apply {
                                        setPreviewEmoji(document)
                                    })
                            }
                        }
                }
            }
        },
        "user" to { uri ->
            uri.getQueryParameter("id")?.toLongOrNull()?.let { userId ->
                getSafeLastFragment()?.let { currentFragment ->
                    val fragment = ProfileActivity(Bundle().apply {
                        putLong("user_id", userId)
                    })
                    val spinner = AlertDialog(
                        currentFragment.getParentActivity(),
                        AlertDialog.ALERT_TYPE_SPINNER
                    ).apply {
                        setCanCancel(false)
                        show()
                    }
                    ChatUtils.getInstance().searchUserById(userId) { user ->
                        spinner.dismiss()
                        if (user != null) {
                            AndroidUtilities.runOnUIThread {
                                LaunchActivity.instance.presentFragment(
                                    fragment,
                                    false,
                                    false
                                )
                            }
                            if (AndroidUtilities.isTablet()) {
                                LaunchActivity.instance.actionBarLayout.showLastFragment()
                                LaunchActivity.instance.rightActionBarLayout.showLastFragment()
                            }
                        } else {
                            LaunchActivity.instance.showBulletin { factory ->
                                factory.createErrorBulletin(
                                    LocaleController.getString(
                                        R.string.UserNotFound
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
        "chat" to { uri ->
            uri.getQueryParameter("id")?.toLongOrNull()?.let { chatId ->
                getSafeLastFragment()?.let { currentFragment ->
                    val fragment = ChatActivity(Bundle().apply {
                        putLong("chat_id", chatId)
                    })
                    val spinner = AlertDialog(
                        currentFragment.getParentActivity(),
                        AlertDialog.ALERT_TYPE_SPINNER
                    ).apply {
                        setCanCancel(false)
                        show()
                    }
                    ChatUtils.getInstance().searchChatById(chatId) { chat ->
                        spinner.dismiss()
                        if (chat != null) {
                            AndroidUtilities.runOnUIThread {
                                LaunchActivity.instance.presentFragment(
                                    fragment,
                                    false,
                                    false
                                )
                            }
                            if (AndroidUtilities.isTablet()) {
                                LaunchActivity.instance.actionBarLayout.showLastFragment()
                                LaunchActivity.instance.rightActionBarLayout.showLastFragment()
                            }
                        } else {
                            LaunchActivity.instance.showBulletin { factory ->
                                factory.createErrorBulletin(
                                    LocaleController.getString(
                                        R.string.ChatNotFound
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    )
    private val callbacks: Map<String, (Uri) -> Unit> = mapOf(
        SettingsRegistry.deeplinkPrefix to { uri ->
            SettingsRegistry.getInstance().handleLink(
                uri.getQueryParameter(SettingsRegistry.deeplinkSettingQueryKey),
                uri.getQueryParameter(SettingsRegistry.deeplinkPluginIdQueryKey)
            )
        },
        PLUGIN_REPOSITORY_LINK to { uri ->
            val username = uri.getQueryParameter(PLUGIN_REPOSITORY_QUERY_KEY)
            val gitUrl = uri.getQueryParameter(PLUGIN_REPOSITORY_GIT_QUERY_KEY)
            val fragment = getSafeLastFragment()
            if (fragment != null) {
                if (!username.isNullOrEmpty()) {
                    PluginsController.getInstance().openRepositoryLink(fragment, username)
                } else if (!gitUrl.isNullOrEmpty()) {
                    PluginsController.getInstance().openGitRepositoryLink(fragment, gitUrl)
                }
            }
        }
    )
    private val actionCallbacks: Map<String, (Intent) -> Boolean> = mapOf(
        SAFE_MODE_INTENT to {
            pluginsSafeMode = true
            if (pluginsEngine && PluginsController.getInstance().isInitialized()) {
                PluginsController.getInstance().restart(true)
            }
            true
        },
        Intent.ACTION_VIEW to { intent ->
            val uri = intent.data
            val ext = uri?.let { getViewFileExtension(it) }
            val fragment = getSafeLastFragment()
            if (uri == null || ext == null || fragment == null || ext !in VIEW_FILE_EXTENSIONS || AndroidUtilities.isInternalUri(uri)) {
                false
            } else {
                val file = try {
                    getTempFileFromIntent(uri, ext)
                } catch (e: Exception) {
                    FileLog.e(e)
                    null
                }
                if (file == null) {
                    BulletinFactory.of(fragment).createErrorBulletin(LocaleController.getString(R.string.ErrorOccurred)).show()
                    true
                } else {
                    ExteraFileHandler.open(fragment, file, file.name, null)
                }
            }
        }
    )

    private fun getViewFileExtension(uri: Uri): String? {
        val displayName = if (ContentResolver.SCHEME_CONTENT == uri.scheme) {
            try {
                ApplicationLoader.applicationContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            } catch (e: Exception) {
                FileLog.e(e)
                null
            }
        } else {
            null
        }
        return (displayName ?: uri.path)?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
    }

    fun getTempFileFromIntent(uri: Uri, extension: String): File {
        val tempDir = File(ApplicationLoader.getFilesDirFixed(), "temp")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }

        val tempFile = File(tempDir, "temp_file_${System.currentTimeMillis()}.$extension")
        ApplicationLoader.applicationContext.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        tempFile.deleteOnExit()
        return tempFile
    }

    fun handleIntent(intent: Intent): Boolean {
        intent.action?.run {
            if (this in actionCallbacks) {
                if (actionCallbacks[this]!!.invoke(intent)) return true
            }
        }
        val data = intent.data
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0 || data == null || data.scheme == null || intent.action != Intent.ACTION_VIEW) return false

        when (data.scheme) {
            "tg" -> {
                when (data.host) {
                    in deeplinkCallbacks -> {
                        deeplinkCallbacks[data.host]!!.invoke(data)
                        return true
                    }
                }
            }

            "https", "http" -> {
                when (data.host) {
                    "t.me", "telegram.org", "telegram.me", "telegram.dog" -> {
                        if (data.pathSegments.isNotEmpty()) {
                            when (val firstSegment = data.pathSegments[0]) {
                                in callbacks -> {
                                    callbacks[firstSegment]!!.invoke(data)
                                    return true
                                }
                            }
                        }
                    }
                }
            }
        }
        return false
    }
}