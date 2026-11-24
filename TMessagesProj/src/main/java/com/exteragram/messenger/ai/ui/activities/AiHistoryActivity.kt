/*
 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.
*/

package com.exteragram.messenger.ai.ui.activities

import android.view.Gravity
import android.view.View
import com.exteragram.messenger.ai.AiController
import com.exteragram.messenger.ai.data.Message
import com.exteragram.messenger.ai.getConversationHistory
import com.exteragram.messenger.ai.saveConversationHistory
import com.exteragram.messenger.ai.saveHistory
import com.exteragram.messenger.ai.ui.MarkdownPreview
import com.exteragram.messenger.ai.ui.components.HistoryMessageCell
import com.exteragram.messenger.preferences.BasePreferencesActivity
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.messenger.Utilities
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.ItemOptions
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import java.util.ArrayList

class AiHistoryActivity : BasePreferencesActivity() {

    enum class HistoryItem {
        SAVE_HISTORY,
        CLEAR_HISTORY;

        val id: Int = ordinal + 1
    }

    private class Entry(
        val id: Int,
        val message: Message,
        var content: CharSequence = message.content() ?: "",
        var expanded: Boolean = false,
    )

    private val entries = ArrayList<Entry>()
    private var lastEntryId = 100

    override fun onFragmentCreate(): Boolean {
        getConversationHistory().forEach { message ->
            entries.add(Entry(lastEntryId++, message))
        }
        formatContents()
        return super.onFragmentCreate()
    }

    private fun formatContents() {
        if (entries.isEmpty()) return

        val snapshot = ArrayList(entries)
        Utilities.globalQueue.postRunnable {
            val formatted = snapshot.map { MarkdownPreview.format(it.message.content()) }
            AndroidUtilities.runOnUIThread {
                context ?: return@runOnUIThread
                snapshot.indices.forEach { i -> snapshot[i].content = formatted[i] }
                listView.adapter?.update(true)
            }
        }
    }

    override fun getTitle(): String = getString(R.string.MessageHistory)

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asRippleCheck(HistoryItem.SAVE_HISTORY.id, getString(R.string.MessageHistory))
                .setChecked(saveHistory))
        items.add(UItem.asShadow(getString(R.string.HistoryInfo)))

        if (entries.isEmpty()) return

        items.add(UItem.asHeader(LocaleController.formatPluralString("messages", entries.size)))
        entries.forEach { entry ->
            items.add(HistoryMessageCell.Factory.asHistoryCell(entry.id, entry.message, entry.content, entry.expanded))
        }
        items.add(UItem.asShadow(null))

        items.add(uButton(HistoryItem.CLEAR_HISTORY, getString(R.string.ClearHistory), R.drawable.msg_delete, red = true, searchable = false))
        items.add(UItem.asShadow(null))
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        if ((item.id <= 0) || (item.id > HistoryItem.entries.size)) {
            findEntry(item.id)?.let { entry ->
                entry.expanded = !entry.expanded
                listView.adapter?.update(true)
            }
            return
        }
        when (HistoryItem.entries[item.id - 1]) {
            HistoryItem.SAVE_HISTORY -> toggleBooleanSettingAndRefresh(item) { saveHistory = it }
            HistoryItem.CLEAR_HISTORY -> AiController.clearHistory(this, resourceProvider, true) {
                entries.clear()
                listView.adapter?.update(true)
            }
        }
    }

    override fun onLongClick(item: UItem, view: View, position: Int, x: Float, y: Float): Boolean {
        val message = (item.`object` as? Message) ?: return false
        ItemOptions.makeOptions(this, view).apply {
            add(R.drawable.msg_copy, getString(R.string.Copy)) {
                if (AndroidUtilities.addToClipboard(message.content()))
                    BulletinFactory.of(this@AiHistoryActivity).createCopyBulletin(getString(R.string.TextCopied)).show()
            }
            add(R.drawable.msg_delete, getString(R.string.Delete), true) { deleteTurn(item.id) }
            setGravity(if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT)
            setScrimViewBackground(listView.getClipBackground(view))
            setDismissOnMoveOutside(true)
            show()
        }
        return true
    }

    private fun findEntry(id: Int): Entry? = entries.firstOrNull { it.id == id }

    private fun deleteTurn(id: Int) {
        val index = entries.indexOfFirst { it.id == id }
        if (index == -1) return

        var from = if (Message.ASSISTANT_ROLE == entries[index].message.role()) index - 1 else index
        var to = from + 1
        if ((from < 0) || (to >= entries.size) ||
            (Message.ASSISTANT_ROLE == entries[from].message.role()) ||
            (Message.ASSISTANT_ROLE != entries[to].message.role())
        ) {
            from = index
            to = index
        }

        (to downTo from).forEach { entries.removeAt(it) }

        saveConversationHistory(ArrayList(entries.map { it.message }))
        listView.adapter?.update(true)
    }
}