/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.preferences.chats

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.ContextCompat
import com.exteragram.messenger.preferences.BasePreferencesActivity
import com.exteragram.messenger.swipeActionsLoop
import com.exteragram.messenger.swipeActionsReversed
import com.exteragram.messenger.preferences.chats.components.SwipeActionsCell
import com.exteragram.messenger.utils.chats.SwipeAction
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import java.util.ArrayList

class SwipeActionsPreferencesActivity : BasePreferencesActivity() {

    companion object {
        private const val PREVIEW_ID = 1
        private const val LOOP_ID = 2
        private const val REVERSED_ID = 3
        private const val ACTION_BASE_ID = 100
    }

    private lateinit var previewCell: SwipeActionsCell
    private var reorderIcon: Drawable? = null

    override fun getTitle(): String = getString(R.string.SwipeActions)

    override fun createView(context: Context): View {
        previewCell = SwipeActionsCell(context)
        val view = super.createView(context)
        listView.allowReorder(true)
        listView.listenReorder(this::onReorder)
        return view
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        reorderIcon = reorderIcon ?: ContextCompat.getDrawable(context, R.drawable.list_reorder)
        items.add(UItem.asCustom(PREVIEW_ID, previewCell))
        items.add(UItem.asShadow(getString(R.string.SwipeActionsInfo)))

        adapter.whiteSectionStart()
        items.add(UItem.asHeader(getString(R.string.SwipeActionsBehavior)))
        items.add(uCheck(LOOP_ID, getString(R.string.SwipeActionsLoop), swipeActionsLoop, linkAlias = "swipeActionsLoop"))
        items.add(uCheck(REVERSED_ID, getString(R.string.SwipeActionsReversed), swipeActionsReversed, linkAlias = "swipeActionsReversed"))
        adapter.whiteSectionEnd()
        items.add(UItem.asShadow(getString(R.string.SwipeActionsBehaviorInfo)))

        addSection(items, adapter, getString(R.string.SwipeActionsEnabled), SwipeAction.enabled())
        items.add(UItem.asShadow(getString(R.string.SwipeActionsOrderInfo)))

        val disabled = SwipeAction.disabled()
        if (disabled.isNotEmpty()) {
            addSection(items, adapter, getString(R.string.SwipeActionsDisabled), disabled)
            items.add(UItem.asShadow(null))
        }
    }

    private fun addSection(items: ArrayList<UItem>, adapter: UniversalAdapter, header: String, actions: List<SwipeAction>) =
        adapter.apply {
            whiteSectionStart()
            items.add(UItem.asHeader(header))
            reorderSectionStart()
            items.addAll(actions.map {
                uButton(ACTION_BASE_ID + it.ordinal, getString(it.titleRes), it.iconRes, searchable = false).apply {
                    object2 = reorderIcon
                }
            })
            reorderSectionEnd()
            whiteSectionEnd()
        }

    private fun onReorder(sectionId: Int, uItems: ArrayList<UItem>) {
        if (sectionId != 0) return
        val actions = ArrayList<SwipeAction>()
        actions.addAll(uItems.mapNotNull { fromId(it.id) })
        SwipeAction.setEnabled(actions)
        refresh()
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        when (item.id) {
            LOOP_ID -> {
                toggleBooleanSettingAndRefresh(item) { swipeActionsLoop = it }
                previewCell.updateActions()
                return
            }
            REVERSED_ID -> {
                toggleBooleanSettingAndRefresh(item) { swipeActionsReversed = it }
                return
            }
        }
        val action = fromId(item.id) ?: return
        action.isEnabled = !action.isEnabled
        refresh()
    }

    private fun refresh() {
        previewCell.updateActions()
        listView.adapter?.update(true)
    }

    private fun fromId(id: Int): SwipeAction? {
        val index = id - ACTION_BASE_ID
        val actions = SwipeAction.entries
        return if (index in actions.indices) actions[index] else null
    }
}