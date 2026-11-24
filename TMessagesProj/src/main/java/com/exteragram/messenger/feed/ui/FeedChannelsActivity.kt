/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.feed.ui

import android.content.Context
import android.text.TextUtils
import android.view.View
import android.widget.EditText
import com.exteragram.messenger.feed.FeedChannelActions
import com.exteragram.messenger.feed.FeedConfig
import com.exteragram.messenger.feed.FeedController
import com.exteragram.messenger.preferences.BasePreferencesActivity
import com.exteragram.messenger.showFeedTab
import com.exteragram.messenger.showFeedUnreadCounter
import com.exteragram.messenger.widePostsInFeed
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.INavigationLayout.REBUILD_FLAG_REBUILD_LAST
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.ItemOptions
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import java.util.ArrayList
import java.util.Locale

class FeedChannelsActivity : BasePreferencesActivity(), NotificationCenter.NotificationCenterDelegate {

    enum class SettingItem {
        FEED_TAB,
        WIDE_POSTS,
        UNREAD_COUNTER,
        ARCHIVE,
        CREATE_CATEGORY;

        val id: Int = ordinal + 1000
    }

    companion object {
        private const val MENU_SELECT_ALL = 1
        private const val MENU_DESELECT_ALL = 2
        private const val MENU_OTHER = 3
        private const val CATEGORY_ITEM_BASE = 2000

        private val BY_TITLE = Comparator<TLRPC.Chat> { c1, c2 ->
            val t1 = c1.title?.lowercase() ?: ""
            val t2 = c2.title?.lowercase() ?: ""
            t1.compareTo(t2)
        }
    }

    private var otherItem: ActionBarMenuItem? = null
    private val channels = ArrayList<TLRPC.Chat>()
    private var query: String? = null
    private var searching = false

    override fun getTitle(): String = getString(R.string.FeedSettings)

    override fun onFragmentCreate(): Boolean {
        notificationCenter.addObserver(this, NotificationCenter.feedNeedReload)
        notificationCenter.addObserver(this, NotificationCenter.dialogDeleted)
        return super.onFragmentCreate()
    }

    override fun onFragmentDestroy() {
        notificationCenter.removeObserver(this, NotificationCenter.feedNeedReload)
        notificationCenter.removeObserver(this, NotificationCenter.dialogDeleted)
        super.onFragmentDestroy()
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (id == NotificationCenter.feedNeedReload) {
            reloadChannels()
        } else if (id == NotificationCenter.dialogDeleted) {
            removeChannel(args[0] as Long)
        }
    }

    override fun createView(context: Context): View {
        val view = super.createView(context)

        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                when (id) {
                    -1 -> finishFragment()
                    MENU_SELECT_ALL -> setAllExcluded(false)
                    MENU_DESELECT_ALL -> setAllExcluded(true)
                }
            }
        })

        val menu = actionBar.createMenu()
        val searchItem = menu.addItem(0, R.drawable.outline_header_search)
            .setIsSearchField(true)
            .setActionBarMenuItemSearchListener(object : ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                override fun onSearchExpand() {
                    searching = true
                    otherItem?.visibility = View.GONE
                }

                override fun onSearchCollapse() {
                    searching = false
                    query = null
                    otherItem?.visibility = View.VISIBLE
                    listView.adapter?.update(true)
                }

                override fun onTextChanged(editText: EditText) {
                    query = editText.text.toString().trim().lowercase(Locale.getDefault())
                    listView.adapter?.update(true)
                }
            })
        searchItem.setSearchPaddingStart(7)
        searchItem.setSearchFieldHint(getString(R.string.Search))

        val other = menu.addItem(MENU_OTHER, R.drawable.ic_ab_other)
        other.addSubItem(MENU_SELECT_ALL, R.drawable.msg_select, getString(R.string.SelectAll))
        other.addSubItem(MENU_DESELECT_ALL, R.drawable.msg_cancel, getString(R.string.DeselectAll))
        otherItem = other

        listView.allowReorder(true)
        listView.listenReorder { _, items ->
            FeedConfig.getInstance(currentAccount).reorderCategories(items.mapNotNull { (it.`object` as? FeedConfig.Category)?.id })
            FeedController.getInstance(currentAccount).notifyCategoriesChanged()
        }

        reloadChannels()
        return view
    }

    override fun onResume() {
        super.onResume()
        listView.adapter?.update(true)
    }

    override fun onBackPressed(invoked: Boolean): Boolean {
        if (searching) {
            if (invoked) {
                actionBar.closeSearchField()
            }
            return false
        }
        return super.onBackPressed(invoked)
    }

    private fun reloadChannels() {
        FeedController.getInstance(currentAccount).loadChannels(true) { loaded, _, failed, _ ->
            if (failed) return@loadChannels
            channels.clear()
            loaded.forEach { chat ->
                channels.add(messagesController.getChat(chat.id) ?: chat)
            }
            channels.sortWith(BY_TITLE)
            listView.adapter?.update(true)
        }
    }

    private fun removeChannel(dialogId: Long) {
        if (channels.removeIf { -it.id == dialogId }) {
            listView.adapter?.update(true)
        }
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        val config = FeedConfig.getInstance(currentAccount)
        val currentQuery = query
        val filtering = !TextUtils.isEmpty(currentQuery)
        if (!filtering) {
            items.add(UItem.asHeader(getString(R.string.General)))
            items.add(uCheck(SettingItem.FEED_TAB.id, getString(R.string.FeedBottomTab), showFeedTab, subtext = getString(R.string.FeedBottomTabInfo), linkAlias = "feedBottomTab"))
            items.add(uCheck(SettingItem.WIDE_POSTS.id, getString(R.string.WidePostsInFeed), widePostsInFeed, linkAlias = "feedWidePosts"))
            items.add(uCheck(SettingItem.UNREAD_COUNTER.id, getString(R.string.FeedUnreadCounter), showFeedUnreadCounter, linkAlias = "feedUnreadCounter"))
            items.add(uCheck(SettingItem.ARCHIVE.id, getString(R.string.FeedIncludeArchived), config.isIncludeArchived(), linkAlias = "feedIncludeArchived"))
            items.add(UItem.asShadow(getString(R.string.FeedIncludeArchivedInfo)))
            addCategoriesSection(items, adapter)
        }

        val shown = ArrayList<UItem>()
        val hidden = ArrayList<UItem>()
        channels.forEach { chat ->
            if (filtering && (chat.title == null || !chat.title.lowercase(Locale.getDefault()).contains(currentQuery!!))) {
                return@forEach
            }
            val included = !config.isExcluded(-chat.id)
            val item = UItem.asUserCheckbox(chat.id.hashCode(), chat).setChecked(included)
            (if (included) shown else hidden).add(item)
        }

        if (shown.isNotEmpty()) {
            items.add(UItem.asHeader(getString(R.string.FeedShownChannels)))
            items.addAll(shown)
        }
        if (hidden.isNotEmpty()) {
            if (shown.isNotEmpty()) {
                items.add(UItem.asShadow())
            }
            items.add(UItem.asHeader(getString(R.string.FeedHiddenChannels)))
            items.addAll(hidden)
        }
        if (!filtering && (shown.isNotEmpty() || hidden.isNotEmpty())) {
            items.add(UItem.asShadow(getString(R.string.FeedChannelsInfo)))
        }
    }

    private fun addCategoriesSection(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        adapter.whiteSectionStart()
        items.add(UItem.asHeader(getString(R.string.FeedCategories)))
        adapter.reorderSectionStart()
        FeedConfig.getInstance(currentAccount).getCategories().forEach { category ->
            items.add(FeedCategoryCell.Factory.of(CATEGORY_ITEM_BASE + category.id, category) { toggleCategoryHidden(category.id) })
        }
        adapter.reorderSectionEnd()
        items.add(uButton(SettingItem.CREATE_CATEGORY.id, getString(R.string.FeedCategoryCreate), R.drawable.msg_add, accent = true, searchable = false))
        adapter.whiteSectionEnd()
        items.add(UItem.asShadow(getString(R.string.FeedCategoriesInfo)))
    }

    private fun toggleCategoryHidden(categoryId: Int) {
        val config = FeedConfig.getInstance(currentAccount)
        val category = config.getCategory(categoryId) ?: return
        if (config.setCategoryHidden(categoryId, !category.isHidden)) {
            FeedController.getInstance(currentAccount).notifyCategoriesChanged()
            listView.adapter?.update(true)
        }
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        val category = item.`object` as? FeedConfig.Category
        if (category != null) {
            presentFragment(FeedCategoryEditActivity(category.id, false))
            return
        }
        val chat = item.`object` as? TLRPC.Chat
        if (chat != null) {
            toggleBooleanSettingAndRefresh(item) { included ->
                FeedConfig.getInstance(currentAccount).setExcluded(-chat.id, !included)
            }
            return
        }

        val settingItem = SettingItem.entries.firstOrNull { it.id == item.id } ?: return
        when (settingItem) {
            SettingItem.FEED_TAB -> {
                toggleBooleanSettingAndRefresh(item) { showFeedTab = it }
                listView.adapter?.update(true)
                notificationCenter.postNotificationName(NotificationCenter.feedTabVisibleToggled)
            }
            SettingItem.WIDE_POSTS -> {
                toggleBooleanSettingAndRefresh(item) { widePostsInFeed = it }
                parentLayout.rebuildFragments(if (AndroidUtilities.isTablet()) REBUILD_FLAG_REBUILD_LAST else 0)
            }
            SettingItem.UNREAD_COUNTER -> {
                toggleBooleanSettingAndRefresh(item) { showFeedUnreadCounter = it }
                notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, 0)
            }
            SettingItem.ARCHIVE -> {
                val config = FeedConfig.getInstance(currentAccount)
                config.setIncludeArchived(!config.isIncludeArchived())
                reloadChannels()
            }
            SettingItem.CREATE_CATEGORY -> presentFragment(FeedCategoryEditActivity(0, false))
        }
    }

    override fun onLongClick(item: UItem, view: View, position: Int, x: Float, y: Float): Boolean {
        val chat = (item.`object` as? TLRPC.Chat) ?: return super.onLongClick(item, view, position, x, y)
        ItemOptions.makeOptions(this, view).apply {
            setScrimViewBackground(listView.getClipBackground(view))
            add(R.drawable.msg_channel, getString(R.string.OpenChannel2)) { presentFragment(ChatActivity.of(-chat.id)) }
            addIf(FeedChannelActions.canLeave(chat), R.drawable.msg_leave, getString(R.string.LeaveChannelMenu), true) {
                FeedChannelActions.leaveChannel(this@FeedChannelsActivity, chat, null, null)
            }
            setDismissOnMoveOutside(true)
            show()
        }
        return true
    }

    private fun setAllExcluded(excluded: Boolean) {
        val config = FeedConfig.getInstance(currentAccount)
        if (excluded) {
            config.excludeAll(channels.map { -it.id })
        } else {
            config.clearExcluded()
        }
        listView.adapter?.update(true)
    }
}