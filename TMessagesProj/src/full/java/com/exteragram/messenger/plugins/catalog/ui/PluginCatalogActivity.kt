/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.catalog.ui

import android.content.Context
import android.content.DialogInterface
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.exteragram.messenger.TabIconsMode
import com.exteragram.messenger.badges.BadgesController
import com.exteragram.messenger.plugins.Plugin
import com.exteragram.messenger.plugins.PluginsController
import com.exteragram.messenger.plugins.catalog.CatalogPlugin
import com.exteragram.messenger.plugins.catalog.GitPluginRepository
import com.exteragram.messenger.plugins.catalog.PluginCatalogController
import com.exteragram.messenger.plugins.catalog.PluginCategory
import com.exteragram.messenger.plugins.catalog.PluginRepositoryLinks
import com.exteragram.messenger.plugins.catalog.db.PluginRepositoryEntity
import com.exteragram.messenger.plugins.ui.components.EmptyPluginsView
import com.exteragram.messenger.plugins.utils.PluginsWatchdog
import com.exteragram.messenger.preferences.BasePreferencesActivity
import com.exteragram.messenger.utils.AppUtils
import com.exteragram.messenger.utils.text.LocaleUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.browser.Browser
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.ActionBarPopupWindow
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.FilterTabsView
import com.exteragram.messenger.utils.ui.ChatHeaderUiHelper
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.ChatAvatarContainer
import org.telegram.ui.Components.ItemOptions
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl
import kotlin.math.min

class PluginCatalogActivity(private val repositoryId: Long) : BasePreferencesActivity(),
    NotificationCenter.NotificationCenterDelegate {
    private var plugins: List<CatalogPlugin> = emptyList()
    private var sortedPlugins: List<CatalogPlugin> = emptyList()
    private var loaded = false
    private var searching = false
    private var query: String? = null
    private var emptyView: EmptyPluginsView? = null
    private val requested = HashSet<Int>()
    private val installing = HashSet<Int>()
    private var repository: PluginRepositoryEntity? = null
    private var otherItem: ActionBarMenuItem? = null
    private var avatarContainer: ChatAvatarContainer? = null
    private var filterTabsView: FilterTabsView? = null
    private var tabCategories: List<PluginCategory?> = emptyList()
    private var selectedCategory: PluginCategory? = null
    private var sortOrder = PluginCatalogController.sortOrder
    private var refreshing = false

    override val pinnedHeaderHeight = TABS_HEIGHT + TABS_PADDING * 2
    override val pinnedHeaderMargin = 4f
    override val pinnedHeaderGap = 0f

    init {
        setPinnedHeaderVisible(false, false)
    }

    private val controller: PluginCatalogController
        get() = PluginCatalogController.getInstance(currentAccount)

    override fun getTitle(): String =
        messagesController.getChat(repositoryId)?.title ?: repository?.title ?: getString(R.string.PluginCatalog)

    override fun createView(context: Context): View {
        val contentView = super.createView(context)

        actionBar.menu
            .addItem(SEARCH_ITEM_ID, R.drawable.outline_header_search)
            .setIsSearchField(true)
            .setActionBarMenuItemSearchListener(object : ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                override fun onSearchExpand() {
                    searching = true
                    setPinnedHeaderVisible(false, true)
                    otherItem?.visibility = View.GONE
                    avatarContainer?.visibility = View.GONE
                    listView.adapter.update(true)
                    listView.scrollToPosition(0)
                }

                override fun onSearchCollapse() {
                    searching = false
                    query = null
                    updateTabs(true)
                    otherItem?.visibility = View.VISIBLE
                    avatarContainer?.visibility = View.VISIBLE
                    listView.adapter.update(true)
                    listView.scrollToPosition(0)
                }

                override fun onTextChanged(editText: EditText) {
                    query = editText.text.toString()
                    listView.adapter.update(true)
                    listView.scrollToPosition(0)
                }
            })
            .apply {
                setSearchPaddingStart(7)
                setSearchFieldHint(getString(R.string.Search))
            }
        avatarContainer = object : ChatAvatarContainer(context, null, false, resourceProvider) {
            override fun useAnimatedSubtitle(): Boolean = true
        }.apply {
            ChatHeaderUiHelper.setupGlassAvatarContainer(this)
            setOccupyStatusBar(!AndroidUtilities.isTablet())
            isEnabled = false
        }
        actionBar.setTitle(null)
        actionBar.addView(
            avatarContainer,
            0,
            LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.TOP or Gravity.LEFT, ChatHeaderUiHelper.getAvatarContainerLeftMargin(false) + HEADER_INSET, 0f, 100f + HEADER_INSET, 0f),
        )
        updateHeader()
        otherItem = actionBar.menu.addItem(OTHER_ITEM_ID, R.drawable.ic_ab_other).apply {
            contentDescription = getString(R.string.AccDescrMoreOptions)
            setOpensMenuOnClick(true)
        }
        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                when (id) {
                    -1 -> finishFragment()
                    OTHER_ITEM_ID -> showMenu()
                }
            }
        })

        listView.setOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(parentActivity.currentFocus)
                }
            }
        })

        fragmentView = contentView
        updateTabs(false)
        return contentView
    }

    private fun showMenu() {
        val anchor = otherItem ?: return
        val o = ItemOptions.makeOptions(this, anchor, true)

        val sort = o.makeSwipeback()
        sort.add(R.drawable.ic_ab_back, getString(R.string.Back), o::closeSwipeback)
        sort.addGap()
        val currentOrder = effectiveSortOrder
        SORT_TITLES.forEachIndexed { order, titleRes ->
            if (order == SORT_POPULAR && PluginRepositoryEntity.isGitId(repositoryId)) {
                return@forEachIndexed
            }
            sort.addChecked(currentOrder == order, getString(titleRes)) {
                o.dismiss()
                setSortOrder(order)
            }
        }

        o.add(R.drawable.msg_filter, getString(R.string.SortBy), getString(SORT_TITLES.getOrElse(currentOrder) { SORT_TITLES[SORT_UPDATED] })) { o.openSwipeback(sort) }
        o.last?.setRightIcon(R.drawable.msg_arrowright)
        o.addGap()
        o.add(R.drawable.msg_retry, getString(R.string.Refresh)) { refresh() }
        if (!PluginRepositoryEntity.isGitId(repositoryId)) {
            o.add(R.drawable.msg_channel, getString(R.string.OpenChannel2)) { presentFragment(ChatActivity.of(-repositoryId)) }
        } else {
            repository?.url?.let { GitPluginRepository.getWebUrl(it, repository?.homepage) }?.let { webUrl ->
                o.add(R.drawable.msg_openin, getString(R.string.PluginCatalogOpenRepository)) { Browser.openUrl(parentActivity, webUrl) }
            }
        }
        repositoryLink()?.let { link ->
            o.add(R.drawable.msg_share, getString(R.string.ShareLink)) {
                showShareLinkAlert(
                    link,
                    R.string.PluginRepositoryLinkToSavedMessages,
                    R.string.PluginRepositoryLinkToUser,
                    R.string.PluginRepositoryLinkToChats,
                )
            }
            o.add(R.drawable.msg_copy, getString(R.string.CopyLink)) {
                if (AndroidUtilities.addToClipboard(link)) {
                    BulletinFactory.of(this).createCopyBulletin(getString(R.string.LinkCopied)).show()
                }
            }
        }

        o.setGravity(Gravity.RIGHT)
        o.setSwipebackGravity(false, false)
        (o.layout as? ActionBarPopupWindow.ActionBarPopupWindowLayout)?.swipeBack?.setStickToRight(true)
        o.setOnTopOfScrim()
        val padding = Rect()
        ContextCompat.getDrawable(context, R.drawable.popup_fixed_alert4)?.getPadding(padding)
        o.translate(0f, -padding.top.toFloat())
        actionBar.showMenuOverlay(anchor, o)
    }

    private val effectiveSortOrder: Int
        get() = if (sortOrder == SORT_POPULAR && PluginRepositoryEntity.isGitId(repositoryId)) SORT_UPDATED else sortOrder

    private fun setSortOrder(order: Int) {
        if (effectiveSortOrder == order) {
            return
        }
        sortOrder = order
        PluginCatalogController.sortOrder = order
        sortedPlugins = sortPlugins(plugins)
        listView.adapter.update(true)
        listView.scrollToPosition(0)
    }

    private fun sortPlugins(source: List<CatalogPlugin>): List<CatalogPlugin> = when (effectiveSortOrder) {
        SORT_POPULAR -> source.sortedByDescending { it.views }
        SORT_NEWEST -> source.sortedByDescending { it.publishedAt }
        SORT_NAME -> source.sortedBy { (it.metadata?.name ?: it.fileName).lowercase() }
        else -> source
    }

    override fun createPinnedHeader(context: Context, glassFactory: BlurredBackgroundDrawableViewFactory): View =
        FilterTabsView(context, resourceProvider).apply {
            val background = glassFactory.create(this, BlurredBackgroundProviderImpl.topPanel(resourceProvider))
            background.setRadius(AndroidUtilities.dp(18f).toFloat())
            background.setPadding(AndroidUtilities.dp(6.666f))
            setPadding(0, AndroidUtilities.dp(TABS_PADDING), 0, AndroidUtilities.dp(TABS_PADDING))
            setBlurredBackground(background)
            setFillWidth(true)
            setForcedTabIcons(TabIconsMode.ICONS_AND_TITLES)
            setDelegate(object : FilterTabsView.FilterTabsViewDelegate {
                override fun onPageSelected(tab: FilterTabsView.Tab, forward: Boolean) {
                    selectedCategory = tabCategories.getOrNull(tab.id)
                    updateHeader()
                    this@PluginCatalogActivity.listView.adapter.update(true)
                    this@PluginCatalogActivity.listView.scrollToPosition(0)
                }

                override fun onPageScrolled(progress: Float) {}

                override fun onSamePageSelected() {
                    this@PluginCatalogActivity.listView.smoothScrollToPosition(0)
                }

                override fun getTabCounter(tabId: Int): Int = 0

                override fun didSelectTab(tabView: FilterTabsView.TabView, selected: Boolean): Boolean = false

                override fun isTabMenuVisible(): Boolean = false

                override fun onDeletePressed(id: Int) {}

                override fun onPageReorder(fromId: Int, toId: Int) {}

                override fun canPerformActions(): Boolean = !searching
            })
        }.also { filterTabsView = it }

    private fun updateTabs(animated: Boolean) {
        val view = filterTabsView ?: return
        val present = PluginCategory.entries.filter { category -> plugins.any { category in it.categories } }
        if (selectedCategory !in present) {
            selectedCategory = null
        }
        val categories = if (present.isEmpty()) emptyList() else listOf(null) + present
        if (categories != tabCategories) {
            tabCategories = categories
            view.removeTabs()
            categories.forEachIndexed { index, category ->
                if (category == null) {
                    view.addTab(index, 0, getString(R.string.FilterAllChatsShort), R.drawable.filter_plugins)
                } else {
                    view.addTab(index, category.ordinal + 1, getString(category.titleRes), category.iconRes)
                }
            }
            view.selectTabWithStableId(selectedCategory?.let { it.ordinal + 1 } ?: 0)
            view.finishAddingTabs(animated)
        }
        setPinnedHeaderVisible(categories.isNotEmpty() && !searching, animated)
    }

    override fun onFragmentCreate(): Boolean {
        notificationCenter.addObserver(this, NotificationCenter.pluginCatalogUpdated)
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.pluginsUpdated)
        reloadCatalog()
        controller.loadRepositories { repositories ->
            repository = repositories.firstOrNull { it.repositoryId == repositoryId }
            updateList()
        }
        controller.syncRepository(repositoryId, force = false, loadMetadata = true)
        return super.onFragmentCreate()
    }

    override fun onFragmentDestroy() {
        notificationCenter.removeObserver(this, NotificationCenter.pluginCatalogUpdated)
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.pluginsUpdated)
        super.onFragmentDestroy()
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        when (id) {
            NotificationCenter.pluginCatalogUpdated -> {
                if (args.getOrNull(0) == repositoryId) {
                    reloadCatalog()
                }
            }

            NotificationCenter.pluginsUpdated -> updateList()
        }
    }

    private fun reloadCatalog() {
        controller.loadCatalog(repositoryId) { result ->
            plugins = result
            sortedPlugins = sortPlugins(result)
            val firstLoad = !loaded
            loaded = true
            updateList(!firstLoad)
            if (firstLoad) {
                resumeDelayedFragmentAnimation()
            }
        }
    }

    override fun needDelayOpenAnimation(): Boolean = true

    private fun refresh() {
        if (refreshing) {
            return
        }
        refreshing = true
        updateHeader()
        controller.syncRepository(repositoryId, force = true, loadMetadata = true) { changed ->
            refreshing = false
            if (fragmentView == null) {
                return@syncRepository
            }
            updateHeader()
            val bulletin = when {
                changed == null -> BulletinFactory.of(this).createSimpleBulletin(R.raw.error, getString(R.string.PluginCatalogRefreshFailed))
                changed == 0 -> BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.PluginCatalogUpToDate))
                else -> BulletinFactory.of(this).createSimpleBulletin(R.raw.done, LocaleController.formatPluralString("PluginCatalogNewVersions", changed))
            }
            bulletin.show()
        }
    }

    private fun repositoryLink(): String? {
        val repository = repository ?: return null
        val username = messagesController.getChat(repositoryId)?.let(ChatObject::getPublicUsername) ?: repository.username
        return PluginRepositoryLinks.build(repository, username)
    }

    private fun updateHeader() {
        val container = avatarContainer ?: return
        val chat = messagesController.getChat(repositoryId)
        if (chat != null) {
            container.setTitle(chat.title, chat.scam, chat.fake, chat.verified, false, BadgesController.getBadge(chat), chat.emoji_status, false)
            container.setChatAvatar(chat)
        } else {
            container.setTitle(getTitle())
            container.avatarImageView.setImageDrawable(AvatarDrawable().apply { setInfo(repositoryId, getTitle(), null) })
        }
        val category = selectedCategory
        val count = if (category == null) plugins.size else plugins.count { category in it.categories }
        container.setSubtitle(if (loaded && !refreshing) LocaleController.formatPluralString("PluginCatalogPlugins", count) else getString(R.string.PluginCatalogUpdating))
    }

    private fun updateList(animated: Boolean = true) {
        if (fragmentView != null) {
            updateTabs(animated)
            updateHeader()
            val atTop = !listView.canScrollVertically(-1)
            listView.adapter.update(animated)
            if (atTop) {
                listView.scrollToPosition(0)
            }
        }
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        val detailsAvailable = controller.isMetadataAvailable()
        val category = selectedCategory?.takeIf { !searching }
        val filtered = filterByQuery(sortedPlugins).filter { category == null || category in it.categories }
        val visible = if (detailsAvailable) filtered.filter { !it.isPending } else filtered
        val skeletons = if (detailsAvailable && !searching && category == null) min(filtered.count { it.isPending }, MAX_SKELETONS) else 0
        if (visible.isEmpty() && skeletons == 0) {
            if (!searching && (!loaded || controller.isSyncing(repositoryId))) {
                items.add(UItem.asHeader(getString(R.string.PluginCatalogAllPlugins)))
                addSkeletons(items, MAX_SKELETONS)
                items.add(UItem.asShadow())
            } else {
                items.add(UItem.asFullscreenCustom(getEmptyView(), AndroidUtilities.dp(if (searching) 148f else 74f), true).setTransparent(true))
            }
            return
        }

        val states = visible.associateWith(::getState)
        val updates = if (searching) emptyList() else visible.filter { states[it] == CatalogPluginCell.STATE_UPDATE }
        if (updates.isNotEmpty()) {
            items.add(UItem.asHeader(getString(R.string.PluginCatalogUpdates)))
            updates.forEach { items.add(createItem(it, CatalogPluginCell.STATE_UPDATE, detailsAvailable)) }
            items.add(UItem.asShadow())
        }
        val installed = if (searching) emptyList() else visible.filter { states[it] == CatalogPluginCell.STATE_INSTALLED }
        if (installed.isNotEmpty()) {
            items.add(UItem.asHeader(getString(R.string.PluginCatalogInstalled)))
            installed.forEach { items.add(createItem(it, CatalogPluginCell.STATE_INSTALLED, detailsAvailable)) }
            items.add(UItem.asShadow())
        }
        val others = visible.filter { it !in updates && it !in installed }
        if (others.isEmpty() && skeletons == 0) {
            return
        }
        items.add(UItem.asHeader(category?.let { getString(it.titleRes) } ?: getString(R.string.PluginCatalogAllPlugins)))
        for (plugin in others) {
            items.add(createItem(plugin, states[plugin] ?: CatalogPluginCell.STATE_GET, detailsAvailable))
        }
        addSkeletons(items, skeletons)
        if (!detailsAvailable && others.any { it.isPending }) {
            items.add(UItem.asShadow(getString(R.string.PluginCatalogEngineUnavailable)))
        } else {
            items.add(UItem.asShadow())
        }
    }

    private fun addSkeletons(items: ArrayList<UItem>, count: Int) {
        for (i in 0 until count) {
            items.add(CatalogPluginSkeletonCell.Factory.of(SKELETON_ID_START + i, i))
        }
    }

    private fun createItem(plugin: CatalogPlugin, state: Int, detailsAvailable: Boolean): UItem {
        val version = plugin.metadata?.takeIf { it.error == null }?.version
        val installed = plugin.pluginId?.let { PluginsController.getInstance().plugins[it] }
        val versionLabel = if (state == CatalogPluginCell.STATE_UPDATE && installed?.getVersion() != null && version != null) {
            LocaleUtils.replaceArrows(context, "${installed.getVersion()} -> $version", R.drawable.msg_mini_arrow_mediathin)
        } else {
            version
        }
        val onDelete = installed?.let { target -> View.OnClickListener { confirmDelete(target) } }
        val isNew = state == CatalogPluginCell.STATE_GET && plugin.publishedAt >= connectionsManager.currentTime - NEW_PERIOD
        return CatalogPluginCell.Factory.of(plugin, state, versionLabel, isNew, installing.contains(plugin.entryId), detailsAvailable, onDelete)
    }

    private fun confirmDelete(plugin: Plugin) {
        if (plugin.isNotResponding()) {
            PluginsWatchdog.showNotRespondingAlert(plugin)
            return
        }
        val dialog = AlertDialog.Builder(parentActivity, resourceProvider)
            .setTitle(getString(R.string.PluginDelete))
            .setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.PluginDeleteInfo, plugin.getName())))
            .setPositiveButton(getString(R.string.Delete)) { _, _ ->
                PluginsController.getInstance().deletePlugin(plugin.getId()) { error ->
                    AndroidUtilities.runOnUIThread {
                        if (fragmentView == null || error == null) {
                            return@runOnUIThread
                        }
                        BulletinFactory.of(this).createSimpleBulletin(R.raw.error, error).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.Cancel), null)
            .create()
        showDialog(dialog)
        (dialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView)?.setTextColor(Theme.getColor(Theme.key_text_RedBold))
    }

    private fun filterByQuery(source: List<CatalogPlugin>): List<CatalogPlugin> {
        val text = query?.trim()?.lowercase()
        if (!searching || text.isNullOrEmpty()) {
            return source
        }
        return source.filter { plugin ->
            val metadata = plugin.metadata
            listOfNotNull(metadata?.name, metadata?.author, metadata?.description, plugin.fileName)
                .any { it.lowercase().contains(text) }
        }
    }

    private fun getState(plugin: CatalogPlugin): Int {
        val pluginId = plugin.pluginId ?: return CatalogPluginCell.STATE_GET
        val installed = PluginsController.getInstance().plugins[pluginId] ?: return CatalogPluginCell.STATE_GET
        val catalogVersion = plugin.metadata?.version?.let { VERSION_NUMBER.find(it)?.value } ?: return CatalogPluginCell.STATE_INSTALLED
        val installedVersion = installed.getVersion()?.let { VERSION_NUMBER.find(it)?.value } ?: return CatalogPluginCell.STATE_INSTALLED
        val newer = try {
            AppUtils.compareVersionValues(catalogVersion, installedVersion) > 0
        } catch (_: NumberFormatException) {
            false
        }
        return if (newer) CatalogPluginCell.STATE_UPDATE else CatalogPluginCell.STATE_INSTALLED
    }

    private fun getEmptyView(): EmptyPluginsView {
        val view = emptyView ?: EmptyPluginsView(context, resourceProvider).also { emptyView = it }
        val tag = if (searching) 1 else 2
        if (view.tag != tag) {
            MediaDataController.getInstance(currentAccount).setPlaceholderImage(
                view.backupImageView,
                "AnimatedEmojies",
                if (searching) "🔎" else "📂",
                "100_100",
            )
            view.setText(getString(if (searching) R.string.PluginsNotFound else R.string.PluginCatalogEmpty))
            view.tag = tag
        }
        return view
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        val plugin = item.`object` as? CatalogPlugin ?: return
        if (!requested.add(plugin.entryId)) {
            return
        }
        val showProgress = Runnable {
            installing.add(plugin.entryId)
            updateList()
        }
        AndroidUtilities.runOnUIThread(showProgress, PROGRESS_DELAY)
        controller.downloadPlugin(plugin) { file, messageObject ->
            AndroidUtilities.cancelRunOnUIThread(showProgress)
            requested.remove(plugin.entryId)
            if (fragmentView == null) {
                return@downloadPlugin
            }
            if (installing.remove(plugin.entryId)) {
                updateList()
            }
            if (file == null) {
                BulletinFactory.of(this).createSimpleBulletin(
                    R.raw.error,
                    LocaleController.formatString(R.string.PluginCatalogDownloadFailed, plugin.fileName),
                ).show()
                return@downloadPlugin
            }
            if (messageObject != null) {
                PluginsController.getInstance().showInstallDialog(this, messageObject, file.absolutePath)
            } else {
                PluginsController.getInstance().showInstallDialog(this, file.absolutePath)
            }
        }
    }

    override fun onLongClick(item: UItem, view: View, position: Int, x: Float, y: Float): Boolean {
        val plugin = item.`object` as? CatalogPlugin ?: return false
        if (PluginRepositoryEntity.isGitId(repositoryId)) {
            return false
        }
        ItemOptions.makeOptions(this, view).apply {
            add(R.drawable.msg_message, getString(R.string.ViewInChat)) {
                presentFragment(ChatActivity.of(-repositoryId, plugin.entryId))
            }
            setScrimViewBackground(listView.getClipBackground(view))
            setGravity(if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT)
            setDismissOnMoveOutside(true)
            show()
        }
        return true
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

    companion object {
        private const val SEARCH_ITEM_ID = 0
        private const val OTHER_ITEM_ID = 3
        private const val SKELETON_ID_START = 100
        private const val MAX_SKELETONS = 4
        private const val PROGRESS_DELAY = 300L
        private const val HEADER_INSET = 6f
        private const val TABS_HEIGHT = 36f
        private const val TABS_PADDING = 7f
        private const val SORT_UPDATED = 0
        private const val SORT_POPULAR = 1
        private const val SORT_NEWEST = 2
        private const val SORT_NAME = 3
        private val SORT_TITLES = intArrayOf(
            R.string.PluginCatalogSortUpdated,
            R.string.PluginCatalogSortPopular,
            R.string.PluginCatalogSortNewest,
            R.string.PluginCatalogSortName,
        )
        private val VERSION_NUMBER = Regex("\\d+(?:\\.\\d+)*")
        private const val NEW_PERIOD = 24 * 60 * 60
    }
}
