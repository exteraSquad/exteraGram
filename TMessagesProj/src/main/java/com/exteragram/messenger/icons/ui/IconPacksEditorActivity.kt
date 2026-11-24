/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.icons.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.View
import android.widget.EditText
import androidx.core.content.res.ResourcesCompat
import com.exteragram.messenger.editingIconPackId
import com.exteragram.messenger.icons.ExteraResources
import com.exteragram.messenger.icons.IconManager
import com.exteragram.messenger.icons.IconPack
import com.exteragram.messenger.icons.ui.components.NewIconPackBottomSheet
import com.exteragram.messenger.icons.ui.picker.IconPickerController
import com.exteragram.messenger.preferences.BasePreferencesActivity
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.Utilities
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.ActionBarMenuSubItem
import org.telegram.ui.ActionBar.ActionBarPopupWindow
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.TextCell
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Components.UniversalRecyclerView
import org.telegram.ui.LaunchActivity
import java.util.ArrayList
import java.util.Locale

open class IconPacksEditorActivity(private var iconPack: IconPack?) : BasePreferencesActivity(), NotificationCenter.NotificationCenterDelegate {

    companion object {
        private const val EDIT_INFO_BUTTON = 1
        private const val DONE_BUTTON = 2

        private const val FILTER_ALL = 0
        private const val FILTER_REPLACED = 1
        private const val FILTER_NOT_REPLACED = 2

        private val cachedIconItems = ArrayList<UItem>()
        private var isIconsLoaded = false
        private var isLoading = false
    }

    private var searching = false
    private var query: String? = null
    private var otherItem: ActionBarMenuItem? = null

    private val filterItems = arrayOfNulls<ActionBarMenuSubItem>(3)
    private var iconFilter = FILTER_ALL

    private var searchRunnable: Runnable? = null

    private var packItems: ArrayList<UItem>? = null
    private var packItemLowerNames: Array<String> = emptyArray()
    private var packItemsFor: IconPack? = null

    override fun createView(context: Context): View {
        val view = super.createView(context)

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
                    updateAdapter()
                }

                override fun onTextChanged(editText: EditText) {
                    searchRunnable?.let { AndroidUtilities.cancelRunOnUIThread(it) }
                    val runnable = Runnable {
                        query = editText.text.toString()
                        updateAdapter()
                    }
                    searchRunnable = runnable
                    AndroidUtilities.runOnUIThread(runnable, 200)
                }
            })
        searchItem.setSearchPaddingStart(7)

        val other = menu.addItem(3, R.drawable.ic_ab_other)
        otherItem = other
        other.addSwipeBackItem(R.drawable.msg_select, null, getString(R.string.IconPickerFilter), createFilterLayout(context))
        other.addSubItem(EDIT_INFO_BUTTON, R.drawable.msg_edit, getString(R.string.Edit))
        if (editingIconPackId != null) {
            other.addSubItem(DONE_BUTTON, R.drawable.ic_ab_done, getString(R.string.IconPickerSaveAndExit))
                .setColors(getThemedColor(Theme.key_featuredStickers_addButtonPressed), getThemedColor(Theme.key_featuredStickers_addButtonPressed))
        }

        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                when (id) {
                    -1 -> finishFragment()
                    DONE_BUTTON -> {
                        editingIconPackId = null
                        (parentActivity as? LaunchActivity)?.let { IconPickerController.setActive(it, false) }
                        finishFragment()
                    }
                    EDIT_INFO_BUTTON -> NewIconPackBottomSheet(this@IconPacksEditorActivity, context, iconPack).show()
                }
            }
        })

        return view
    }

    private fun updateAdapter() {
        if (fragmentView != null) {
            listView.adapter?.update(true)
        }
    }

    private fun createFilterLayout(context: Context): ActionBarPopupWindow.ActionBarPopupWindowLayout {
        val layout = ActionBarPopupWindow.ActionBarPopupWindowLayout(context, 0, resourceProvider)
        layout.setFitItems(true)

        val backItem = ActionBarMenuItem.addItem(layout, R.drawable.msg_arrow_back, getString(R.string.Back), false, resourceProvider)
        backItem.setOnClickListener { otherItem?.popupLayout?.swipeBack?.closeForeground() }

        val gap = ActionBarPopupWindow.GapView(context, resourceProvider)
        gap.setTag(R.id.fit_width_tag, 1)
        layout.addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8))

        createFilterItem(layout, getString(R.string.IconPickerAllIcons), FILTER_ALL)
        createFilterItem(layout, getString(R.string.IconPickerReplacedIcons), FILTER_REPLACED)
        createFilterItem(layout, getString(R.string.IconPickerNotReplacedIcons), FILTER_NOT_REPLACED)
        updateFilterChecks()

        return layout
    }

    private fun createFilterItem(layout: ActionBarPopupWindow.ActionBarPopupWindowLayout, text: String, filter: Int) {
        val item = ActionBarMenuSubItem(layout.context, true, false, false, resourceProvider)
        item.setTextAndIcon(text, 0)
        item.minimumWidth = dp(196f)
        layout.addView(item, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48))
        item.setOnClickListener { setIconFilter(filter) }
        filterItems[filter] = item
    }

    private fun setIconFilter(filter: Int) {
        if (iconFilter == filter) return
        iconFilter = filter
        updateFilterChecks()
        updateAdapter()
    }

    private fun updateFilterChecks() {
        filterItems.forEachIndexed { i, item -> item?.setChecked(iconFilter == i) }
    }

    override fun onFragmentCreate(): Boolean {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.iconPackUpdated)
        loadIconsAsync()
        return super.onFragmentCreate()
    }

    override fun onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.iconPackUpdated)
        super.onFragmentDestroy()
    }

    private fun loadIconsAsync() {
        if (isIconsLoaded && cachedIconItems.isNotEmpty()) return
        if (isLoading) return
        isLoading = true

        Utilities.globalQueue.postRunnable {
            val systemIcons = HashMap(IconManager.systemIcons)
            if (systemIcons.isEmpty()) {
                AndroidUtilities.runOnUIThread {
                    isLoading = false
                    IconManager.initialize(true)
                }
                return@postRunnable
            }

            val tempItems = ArrayList<UItem>(1500)
            for ((name, resId) in systemIcons) {
                tempItems.add(EditorIconCell.Factory.asIcon(resId, name, null))
            }
            tempItems.sortBy { it.text.toString() }

            AndroidUtilities.runOnUIThread {
                cachedIconItems.clear()
                cachedIconItems.addAll(tempItems)
                isIconsLoaded = true
                isLoading = false
                packItems = null
                updateAdapter()
            }
        }
    }

    override fun getTitle(): String = iconPack?.name ?: getString(R.string.NewIconPack)

    private fun getPackItems(): ArrayList<UItem> {
        packItems?.let { if (packItemsFor == iconPack) return it }

        val built = ArrayList<UItem>(cachedIconItems.size)
        val lowerNames = Array(cachedIconItems.size) { "" }
        cachedIconItems.forEachIndexed { i, cachedItem ->
            built.add(EditorIconCell.Factory.asIcon(cachedItem.id, cachedItem.text, iconPack))
            lowerNames[i] = cachedItem.text?.toString()?.lowercase(Locale.getDefault()) ?: ""
        }
        packItems = built
        packItemLowerNames = lowerNames
        packItemsFor = iconPack
        return built
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        if (context == null || !isIconsLoaded) return

        val source = getPackItems()
        val currentQuery = query
        val lowerQuery = if (searching && !currentQuery.isNullOrEmpty()) currentQuery.lowercase(Locale.getDefault()) else null
        val packedIcons = iconPack?.icons

        for (i in source.indices) {
            if (lowerQuery != null && !packItemLowerNames[i].contains(lowerQuery)) continue
            val item = source[i]
            if (iconFilter != FILTER_ALL) {
                val resourceName = item.text?.toString()
                val isReplaced = resourceName != null && packedIcons?.containsKey(resourceName) == true
                if (iconFilter == FILTER_REPLACED && !isReplaced) continue
                if (iconFilter == FILTER_NOT_REPLACED && isReplaced) continue
            }
            items.add(item)
        }
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        IconManager.showReplaceAlert(context, item.id, iconPack)
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (id != NotificationCenter.iconPackUpdated) return
        context ?: return

        if (!isIconsLoaded) {
            loadIconsAsync()
        }

        iconPack?.let { pack ->
            val updatedPack = IconManager.findPackById(pack.id)
            if (updatedPack == null) {
                finishFragment()
                return
            }
            iconPack = updatedPack
            actionBar.setTitle(getTitle())
        }

        val state = if (fragmentView != null) layoutManager.onSaveInstanceState() else null
        updateAdapter()
        if (state != null) {
            layoutManager.onRestoreInstanceState(state)
        }
    }

    @SuppressLint("ViewConstructor")
    class EditorIconCell(context: Context, resourcesProvider: Theme.ResourcesProvider?) : TextCell(context, resourcesProvider) {

        private var boundResId = 0

        class Factory : UItem.UItemFactory<EditorIconCell>() {

            override fun createView(
                context: Context,
                listView: RecyclerListView?,
                currentAccount: Int,
                classGuid: Int,
                resourcesProvider: Theme.ResourcesProvider?,
            ): EditorIconCell = EditorIconCell(context, resourcesProvider)

            override fun equals(a: UItem, b: UItem): Boolean = a.id == b.id

            override fun contentsEquals(a: UItem, b: UItem): Boolean {
                if (a.id != b.id) return false
                if (!TextUtils.equals(a.text, b.text)) return false

                val packA = a.`object` as? IconPack
                val packB = b.`object` as? IconPack
                if (packA === packB) return true
                if (packA == null || packB == null) return false

                val name = a.text.toString()
                return packA.icons[name] == packB.icons[name]
            }

            override fun bindView(
                view: View,
                item: UItem,
                divider: Boolean,
                adapter: UniversalAdapter?,
                listView: UniversalRecyclerView?,
            ) {
                val cell = view as EditorIconCell
                cell.reset()
                cell.boundResId = item.id

                val resources = cell.context.resources
                val original = if (resources is ExteraResources) {
                    resources.getOriginalDrawable(item.id)
                } else {
                    ResourcesCompat.getDrawable(resources, item.id, null)
                }

                val pack = item.`object` as? IconPack
                val replaced = if (pack != null) {
                    IconManager.getCachedPackIconDrawable(pack, item.id)
                } else {
                    IconManager.getCachedDrawable(item.id)
                }

                cell.setTextAndIconAndValueDrawable(item.text, original?.mutate(), replaced, divider)
                cell.setIsIcon(true)
                cell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText)
                cell.setOffsetFromImage(68)

                if (replaced == null) {
                    val requestedResId = item.id
                    val onLoaded = Utilities.Callback<Drawable> { loaded ->
                        if (cell.boundResId == requestedResId) {
                            cell.valueImageView.setImageDrawable(loaded)
                        }
                    }
                    if (pack != null) {
                        IconManager.requestPackIconDrawable(pack, requestedResId, onLoaded)
                    } else {
                        IconManager.requestDrawable(requestedResId, onLoaded)
                    }
                }
            }

            companion object {
                init {
                    setup(Factory())
                }

                @JvmStatic
                fun asIcon(id: Int, text: CharSequence?, pack: IconPack?): UItem {
                    val item = UItem.ofFactory(Factory::class.java)
                    item.id = id
                    item.text = text
                    item.`object` = pack
                    return item
                }
            }
        }
    }

    override fun onBackPressed(invoked: Boolean): Boolean {
        if (searching) {
            if (invoked) actionBar.closeSearchField()
            return false
        }
        return super.onBackPressed(invoked)
    }
}
