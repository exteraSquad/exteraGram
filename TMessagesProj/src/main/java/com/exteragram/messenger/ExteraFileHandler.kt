/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger

import com.exteragram.messenger.backup.BackupBottomSheet
import com.exteragram.messenger.backup.PreferencesUtils
import com.exteragram.messenger.icons.IconManager
import com.exteragram.messenger.pillstack.custom.CustomPillIO
import com.exteragram.messenger.plugins.PluginsController
import com.exteragram.messenger.utils.chats.ChatUtils
import org.telegram.messenger.MessageObject
import org.telegram.ui.Components.MediaActionDrawable
import org.telegram.ui.ActionBar.BaseFragment
import java.io.File
import java.util.Locale

object ExteraFileHandler {

    @JvmStatic
    fun getFileIcon(file: File?, fileName: String?, fileMessage: MessageObject?): Int {
        val name = fileName?.takeIf { it.isNotEmpty() }?.lowercase(Locale.ROOT) ?: return MediaActionDrawable.ICON_FILE
        return when {
            PreferencesUtils.isBackupFile(name) -> MediaActionDrawable.ICON_SETTINGS
            name.endsWith(".icons") -> MediaActionDrawable.ICON_STICKERS
            CustomPillIO.isPillFile(name) -> MediaActionDrawable.ICON_PILL
            file != null && PluginsController.isPlugin(file, fileMessage) -> MediaActionDrawable.ICON_PLUGIN
            else -> PluginsController.getFileIconId(fileName)
                .takeIf { it != PluginsController.PLUGIN_FILE_ICON_NONE } ?: MediaActionDrawable.ICON_FILE
        }
    }

    @JvmStatic
    fun open(fragment: BaseFragment, messageObject: MessageObject?): Boolean {
        val fileName = messageObject?.documentName ?: return false
        val path = ChatUtils.getInstance().getPathToMessage(messageObject)
        if (path.isNullOrEmpty()) {
            return false
        }
        return open(fragment, File(path), fileName, messageObject, messageObject)
    }

    @JvmStatic
    fun open(fragment: BaseFragment, file: File, fileName: String?, source: MessageObject?): Boolean {
        return open(fragment, file, fileName, null, source)
    }

    private fun open(fragment: BaseFragment, file: File, fileName: String?, fileMessage: MessageObject?, source: MessageObject?): Boolean {
        val name = fileName?.lowercase(Locale.ROOT) ?: return false
        val path = file.absolutePath
        when {
            PreferencesUtils.getInstance().isBackup(file) -> BackupBottomSheet(fragment, file).showIfPossible()
            PluginsController.isPlugin(file, fileMessage) -> if (source != null) {
                PluginsController.getInstance().showInstallDialog(fragment, source, path)
            } else {
                PluginsController.getInstance().showInstallDialog(fragment, path)
            }
            name.endsWith(".icons") -> IconManager.handleIconPack(fragment, path)
            CustomPillIO.isPillFile(name) -> CustomPillIO.handleFile(fragment, path)
            else -> return false
        }
        return true
    }
}
