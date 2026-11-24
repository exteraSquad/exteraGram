/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.updates

import com.exteragram.messenger.utils.network.RemoteUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.DownloadController
import org.telegram.messenger.FileLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.MessageObject
import org.telegram.tgnet.TLRPC
import java.io.File

internal object TelegramUpdateSource {

    fun search(
        account: Int,
        query: String,
        callback: (TLRPC.messages_Messages?, TLRPC.TL_error?) -> Unit,
    ) {
        RemoteUtils.searchMessages(account, 50, query, TLRPC.TL_inputMessagesFilterDocument(), { result, error ->
            AndroidUtilities.runOnUIThread { callback(result, error) }
        }, 3000)
    }

    class Download {
        private var listener: DownloadController.FileDownloadProgressListener? = null

        fun start(account: Int, message: TLRPC.Message, document: TLRPC.Document, callback: (File?) -> Unit) {
            if (listener != null) return
            val loader = FileLoader.getInstance(account)
            val file = loader.getPathToAttachForAccount(document)
            if (file.isFile) {
                callback(file)
                return
            }

            val controller = DownloadController.getInstance(account)
            val messageObject = MessageObject(account, message, false, true)
            val tag = controller.generateObserverTag()
            val observer = object : DownloadController.FileDownloadProgressListener {
                private val stalled = Runnable {
                    if (listener !== this) return@Runnable
                    FileLog.e("Update download stalled: ${FileLoader.getAttachFileName(document)}")
                    finish(null)
                    loader.cancelLoadFile(document)
                }

                fun resetStallTimer() {
                    AndroidUtilities.cancelRunOnUIThread(stalled)
                    AndroidUtilities.runOnUIThread(stalled, STALL_TIMEOUT_MS)
                }

                fun finish(downloaded: File?) {
                    if (listener !== this) return
                    listener = null
                    AndroidUtilities.cancelRunOnUIThread(stalled)
                    controller.removeLoadingFileObserver(this)
                    callback(downloaded)
                }

                override fun onFailedDownload(fileName: String, canceled: Boolean) = finish(null)
                override fun onSuccessDownload(fileName: String) = finish(loader.getPathToAttachForAccount(document))
                override fun onProgressDownload(fileName: String, downloadSize: Long, totalSize: Long) {
                    if (listener === this) resetStallTimer()
                }
                override fun onProgressUpload(fileName: String, uploadedSize: Long, totalSize: Long, isEncrypted: Boolean) = Unit
                override fun getObserverTag(): Int = tag
            }
            listener = observer
            try {
                observer.resetStallTimer()
                controller.addLoadingFileObserver(FileLoader.getAttachFileName(document), messageObject, observer)
                loader.loadFile(document, messageObject, FileLoader.PRIORITY_NORMAL, 0)
            } catch (e: Exception) {
                FileLog.e(e)
                observer.finish(null)
            }
        }

        private companion object {
            const val STALL_TIMEOUT_MS = 90_000L
        }
    }
}
