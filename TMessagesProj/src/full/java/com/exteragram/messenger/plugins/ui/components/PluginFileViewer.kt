/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.ui.components

import com.exteragram.messenger.plugins.PluginsConstants
import com.exteragram.messenger.utils.MarkdownUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessageObject
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_iv
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.BulletinFactory
import java.io.File

object PluginFileViewer {
    private const val MAX_FILE_SIZE = 512 * 1024
    private const val MAX_BLOCK_LENGTH = 8192
    private const val SNIFF_LENGTH = 8192

    @JvmStatic
    fun canOpen(file: File?): Boolean {
        if (file == null || !file.exists() || !file.isFile || file.length() > MAX_FILE_SIZE) {
            return false
        }
        return isText(file)
    }

    private fun isText(file: File): Boolean {
        return try {
            file.inputStream().use { stream ->
                val buffer = ByteArray(SNIFF_LENGTH)
                val read = stream.read(buffer)
                read > 0 && buffer.take(read).none { it == 0.toByte() }
            }
        } catch (t: Throwable) {
            FileLog.e(t)
            false
        }
    }

    @JvmStatic
    fun openExternally(fragment: BaseFragment?, file: File?, fileName: String? = null): Boolean {
        val activity = fragment?.parentActivity ?: return false
        if (file == null || !file.exists() || !file.isFile) {
            return false
        }

        val zip = isZip(file)
        val text = !zip && isText(file)
        var name = fileName?.takeIf { it.isNotBlank() } ?: file.name
        if (!name.contains('.')) {
            name += when {
                zip -> ".zip"
                text -> ".txt"
                else -> ".bin"
            }
        }

        return try {
            AndroidUtilities.openForView(
                file,
                name,
                when {
                    zip -> "application/zip"
                    text -> "text/plain"
                    else -> "application/octet-stream"
                },
                activity,
                fragment.resourceProvider,
                false
            )
        } catch (t: Throwable) {
            FileLog.e(t)
            BulletinFactory.of(fragment)
                .createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.ErrorOccurred))
                .show()
            false
        }
    }

    private fun isZip(file: File): Boolean {
        return try {
            file.inputStream().use { stream ->
                val header = ByteArray(4)
                if (stream.read(header) != 4) {
                    return false
                }
                header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                        (header[2] == 0x03.toByte() || header[2] == 0x05.toByte() || header[2] == 0x07.toByte())
            }
        } catch (t: Throwable) {
            FileLog.e(t)
            false
        }
    }

    fun open(fragment: BaseFragment?, file: File?, fileName: String? = null): Boolean {
        val activity = fragment?.parentActivity ?: return false
        if (file == null || !file.exists() || !file.isFile) {
            return false
        }
        if (!canOpen(file)) {
            return openExternally(fragment, file, fileName)
        }

        val progress = AlertDialog(activity, AlertDialog.ALERT_TYPE_SPINNER, fragment.resourceProvider)
        progress.setCanceledOnTouchOutside(false)
        val cancelled = booleanArrayOf(false)
        progress.setOnCancelListener { cancelled[0] = true }
        progress.showDelayed(150)

        Utilities.globalQueue.postRunnable {
            val messageObject = try {
                createMessageObject(file, normalizeFileName(file, fileName))
            } catch (t: Throwable) {
                FileLog.e(t)
                null
            }

            AndroidUtilities.runOnUIThread {
                try {
                    progress.dismiss()
                } catch (_: Throwable) {
                }
                if (cancelled[0]) {
                    return@runOnUIThread
                }
                if (messageObject != null) {
                    fragment.createArticleViewer(false).open(messageObject)
                } else {
                    BulletinFactory.of(fragment)
                        .createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.ErrorOccurred))
                        .show()
                }
            }
        }

        return true
    }

    private fun createMessageObject(file: File, fileName: String): MessageObject {
        val fileText = file.readText(Charsets.UTF_8)

        val page = TL_iv.TL_page().apply {
            local = file
            url = fileName
        }
        MarkdownUtils.appendPreformattedBlocks(page.blocks, fileText, "python", MAX_BLOCK_LENGTH)

        val webPage = TLRPC.TL_webPage().apply {
            id = file.absolutePath.hashCode().toLong()
            url = fileName
            display_url = fileName
            title = fileName
            flags = flags or TLObject.FLAG_2 or TLObject.FLAG_10
            cached_page = page
        }

        val userId = UserConfig.getInstance(UserConfig.selectedAccount).clientUserId
        val message = TLRPC.TL_message().apply {
            id = 0
            date = (System.currentTimeMillis() / 1000L).toInt()
            this.message = fileName
            out = true
            peer_id = TLRPC.TL_peerUser().apply {
                user_id = userId
            }
            from_id = TLRPC.TL_peerUser().apply {
                user_id = userId
            }
            media = TLRPC.TL_messageMediaWebPage().apply {
                webpage = webPage
            }
        }
        return MessageObject(UserConfig.selectedAccount, message, false, true)
    }

    private fun normalizeFileName(file: File, fileName: String?): String {
        val name = fileName?.takeIf { it.isNotBlank() } ?: file.name
        return if (name.contains('.')) name else name + PluginsConstants.PLUGINS_EXT
    }
}