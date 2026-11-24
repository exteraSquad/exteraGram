/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.reactions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.Spanned
import androidx.core.content.edit
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.Emoji
import org.telegram.messenger.MessagesController
import org.telegram.tgnet.TLRPC
import org.telegram.ui.Components.AnimatedEmojiSpan
import java.util.TreeMap

class ReactionsEditHelper(
    private val account: Int,
    private val chatId: Long,
    private val availableReactions: List<TLRPC.TL_availableReaction>
) {

    data class Backup(val reactionIds: List<Long>, val paid: Boolean)

    private val backupKey get() = "chat_reactions_backup_$chatId"

    private val clipboard
        get() = ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    val hasClipboard get() = clipboard?.hasPrimaryClip() == true

    fun findAvailableReaction(documentId: Long) =
        availableReactions.find { it.activate_animation.id == documentId }

    fun saveBackup(selectedIds: List<Long>, paid: Boolean) {
        val ids = selectedIds.filter { it != STAR_ID }
        if (ids.isEmpty()) return

        val value = (if (paid) listOf(STAR_ID) + ids else ids).joinToString(",")
        MessagesController.getMainSettings(account).edit {
            putString(backupKey, value)
        }
    }

    fun loadBackup(limit: Int): Backup? {
        val ids = MessagesController.getMainSettings(account).getString(backupKey, null)
            ?.split(',')
            ?.mapNotNull { part -> part.toLongOrNull()?.takeIf { it != 0L } }
            ?: return null

        return Backup(ids.filter { it != STAR_ID }.take(limit), STAR_ID in ids)
    }

    fun copyReactions(text: Spanned?, selectionStart: Int, selectionEnd: Int) {
        if (text == null || selectionStart == selectionEnd) return

        val spans = text.getSpans(minOf(selectionStart, selectionEnd), maxOf(selectionStart, selectionEnd), AnimatedEmojiSpan::class.java)
        if (spans.isEmpty()) return

        val copied = spans
            .sortedBy { text.getSpanStart(it) }
            .joinToString(" ") { reactionToText(it.documentId) }

        clipboard?.setPrimaryClip(ClipData.newPlainText("reactions", copied))
    }

    private fun reactionToText(documentId: Long) = when (documentId) {
        STAR_ID -> STAR_EMOJI
        else -> findAvailableReaction(documentId)?.reaction ?: "tg://emoji?id=$documentId"
    }

    fun parseClipboard(): MutableList<AnimatedEmojiSpan> {
        val clip = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }
        val text = clip?.getItemAt(0)?.coerceToText(ApplicationLoader.applicationContext)
        if (text.isNullOrEmpty()) return mutableListOf()

        val found = TreeMap<Int, AnimatedEmojiSpan>()
        val covered = TreeMap<Int, Int>()

        if (text is Spanned) {
            for (span in text.getSpans(0, text.length, AnimatedEmojiSpan::class.java)) {
                val start = text.getSpanStart(span)
                covered[start] = text.getSpanEnd(span)
                found[start] = AnimatedEmojiSpan(span.documentId, null)
            }
        }

        for (match in EMOJI_LINK.findAll(text)) {
            val id = match.groupValues[1].toLongOrNull()?.takeIf { it != 0L } ?: continue
            found[match.range.first] = AnimatedEmojiSpan(id, null)
        }

        for (range in Emoji.parseEmojis(text)) {
            val cover = covered.floorEntry(range.start)
            if (cover != null && range.start < cover.value) continue

            val code = range.code.toString().replace(VARIATION_SELECTOR, "")
            if (code == STAR_EMOJI) {
                found[range.start] = AnimatedEmojiSpan(STAR_ID, null)
                continue
            }

            val reaction = availableReactions.find { it.reaction.replace(VARIATION_SELECTOR, "") == code } ?: continue
            found[range.start] = AnimatedEmojiSpan(reaction.activate_animation, null)
        }

        return found.values.toMutableList()
    }

    companion object {
        const val STAR_ID = -1L

        private const val STAR_EMOJI = "⭐"
        private const val VARIATION_SELECTOR = "\uFE0F"
        private val EMOJI_LINK = Regex("""tg://emoji\?id=(\d+)""")
    }
}
