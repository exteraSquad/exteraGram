/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.translator

import android.os.SystemClock

import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.Utilities
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.Bulletin
import org.telegram.ui.Components.TranslateAlert2

import com.exteragram.messenger.translator.core.HttpTranslator
import com.exteragram.messenger.translator.core.TranslationError

object ChatTranslationBridge {

    private const val FAILURE_NOTICE_INTERVAL = 60 * 1000L

    private val lastFailureNotice = HashMap<Long, Long>()

    @JvmStatic
    fun translateMessages(
        dialogId: Long,
        isTranscription: Boolean,
        ids: List<Int>,
        sources: List<TLRPC.TL_textWithEntities>,
        callbacks: List<Utilities.Callback4<Boolean, Int, TLRPC.TL_textWithEntities, String>>,
        toLang: String,
        onFinished: Runnable
    ) {
        translate(dialogId, sources, toLang) { translated, cancelled ->
            if (!cancelled) {
                for (i in callbacks.indices) {
                    callbacks[i].run(isTranscription, ids[i], translated?.getOrNull(i), toLang)
                }
            }
            onFinished.run()
        }
    }

    @JvmStatic
    fun translateTexts(
        dialogId: Long,
        sources: List<TLRPC.TL_textWithEntities>,
        toLang: String,
        onDone: Utilities.Callback<List<TLRPC.TL_textWithEntities>?>
    ) {
        translate(dialogId, sources, toLang) { translated, _ ->
            onDone.run(translated?.filterNotNull()?.takeIf { it.size == sources.size })
        }
    }

    private fun translate(
        dialogId: Long,
        sources: List<TLRPC.TL_textWithEntities>,
        toLang: String,
        onDone: (List<TLRPC.TL_textWithEntities?>?, Boolean) -> Unit
    ) {
        val translator = TranslatorUtils.getCurrentTranslator() as? HttpTranslator
        if (translator == null) {
            onDone(null, false)
            return
        }
        FormattedTranslation.translate(dialogId, sources, "auto", toLang, translator, false) { results, error ->
            val cancelled = error is TranslationError.Cancelled
            val translated = if (cancelled) null else List(sources.size) { i ->
                results.getOrNull(i)?.let { received -> TranslateAlert2.preprocess(null, received) }
            }
            onDone(translated, cancelled)
            if (error != null && !cancelled) {
                notifyFailure(dialogId, error)
            }
        }
    }

    private fun notifyFailure(dialogId: Long, error: TranslationError) {
        val now = SystemClock.elapsedRealtime()
        val last = lastFailureNotice[dialogId]
        if (last != null && now - last < FAILURE_NOTICE_INTERVAL) {
            return
        }
        lastFailureNotice[dialogId] = now
        NotificationCenter.getGlobalInstance().postNotificationName(
            NotificationCenter.showBulletin,
            Bulletin.TYPE_ERROR,
            getString(
                if (error is TranslationError.RateLimited) R.string.TranslationFailedAlert1
                else R.string.TranslationFailedAlert2
            )
        )
    }
}