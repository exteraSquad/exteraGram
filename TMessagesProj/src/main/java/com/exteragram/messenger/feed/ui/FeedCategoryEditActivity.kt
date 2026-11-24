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
import android.content.DialogInterface
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import com.exteragram.messenger.feed.FeedConfig
import com.exteragram.messenger.feed.FeedController
import com.exteragram.messenger.icons.IconManager
import com.exteragram.messenger.preferences.BasePreferencesActivity
import com.exteragram.messenger.utils.ui.UIUtil
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.BotWebViewVibrationEffect
import org.telegram.messenger.LocaleController.formatString
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_stars
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.EditTextCell
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.ScaleStateListAnimator
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.SelectAnimatedEmojiDialog
import org.telegram.ui.Stories.recorder.ButtonWithCounterView
import java.util.ArrayList
import java.util.Locale

class FeedCategoryEditActivity(
    categoryId: Int,
    private val selectOnCreate: Boolean,
) : BasePreferencesActivity() {

    companion object {
        private const val MAX_TITLE_LENGTH = 24
        private const val MENU_OTHER = 2
        private const val MENU_SELECT_ALL = 3
        private const val MENU_DESELECT_ALL = 4
        private const val MENU_DELETE = 5
        private const val BUTTON_AREA_DP = 12f + 48f + 12f

        private val BY_TITLE = Comparator<TLRPC.Chat> { c1, c2 ->
            val t1 = c1.title?.lowercase() ?: ""
            val t2 = c2.title?.lowercase() ?: ""
            t1.compareTo(t2)
        }

        @JvmStatic
        fun showDeleteDialog(fragment: BaseFragment, category: FeedConfig.Category, onDeleted: Runnable?) {
            val activity = fragment.parentActivity ?: return
            val account = fragment.currentAccount
            val dialog = AlertDialog.Builder(activity, fragment.resourceProvider).apply {
                setTitle(getString(R.string.FeedCategoryDelete))
                setMessage(AndroidUtilities.replaceTags(formatString(R.string.FeedCategoryDeleteConfirm, category.title)))
                setPositiveButton(getString(R.string.Delete)) { _, _ ->
                    FeedConfig.getInstance(account).deleteCategory(category.id)
                    FeedController.getInstance(account).notifyCategoriesChanged()
                    onDeleted?.run()
                }
                setNegativeButton(getString(R.string.Cancel), null)
            }.create()
            fragment.showDialog(dialog)
            (dialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView)
                ?.setTextColor(Theme.getColor(Theme.key_text_RedBold, fragment.resourceProvider))
        }
    }

    private val initialCategory: FeedConfig.Category? = FeedConfig.getInstance(currentAccount).getCategory(categoryId)
    private var emojiId: Long = initialCategory?.emojiId ?: 0L
    private val selectedChannels = HashSet<Long>(initialCategory?.channels ?: emptySet())
    private val channels = ArrayList<TLRPC.Chat>()
    private var channelsLoaded = false
    private var query: String? = null
    private var searching = false

    private lateinit var iconCell: FrameLayout
    private lateinit var iconButton: FrameLayout
    private lateinit var iconView: BackupImageView
    private lateinit var titleCell: EditTextCell
    private var otherItem: View? = null
    private var buttonContainer: FrameLayout? = null
    private var emojiDialog: SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow? = null

    override fun getTitle(): String =
        getString(if (initialCategory != null) R.string.FeedCategoryEdit else R.string.FeedCategoryNew)

    override fun createView(context: Context): View {
        createHeaderViews(context)
        val view = super.createView(context)

        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                when (id) {
                    -1 -> if (onBackPressed(true)) finishFragment()
                    MENU_SELECT_ALL -> setAllSelected(true)
                    MENU_DESELECT_ALL -> setAllSelected(false)
                    MENU_DELETE -> initialCategory?.let { category -> showDeleteDialog(this@FeedCategoryEditActivity, category) { finishFragment() } }
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
                    showSaveButton(false)
                }

                override fun onSearchCollapse() {
                    searching = false
                    otherItem?.visibility = View.VISIBLE
                    showSaveButton(true)
                    query = null
                    listView.adapter?.update(true)
                }

                override fun onTextChanged(editText: EditText) {
                    query = editText.text.toString().trim().lowercase(Locale.getDefault())
                    listView.adapter?.update(true)
                }
            })
        searchItem.setSearchPaddingStart(7)
        searchItem.setSearchFieldHint(getString(R.string.Search))
        otherItem = menu.addItem(MENU_OTHER, R.drawable.ic_ab_other).apply {
            contentDescription = getString(R.string.AccDescrMoreOptions)
            addSubItem(MENU_SELECT_ALL, R.drawable.msg_select, getString(R.string.SelectAll))
            addSubItem(MENU_DESELECT_ALL, R.drawable.msg_cancel, getString(R.string.DeselectAll))
            if (initialCategory != null) {
                addColoredGap()
                addSubItem(MENU_DELETE, R.drawable.msg_delete, getString(R.string.FeedCategoryDelete)).apply {
                    setColors(getThemedColor(Theme.key_text_RedRegular), getThemedColor(Theme.key_text_RedRegular))
                    setSelectorColor(Theme.multAlpha(getThemedColor(Theme.key_text_RedRegular), .12f))
                }
            }
        }

        val button = ButtonWithCounterView(context, true, resourceProvider).apply {
            setRound()
            setText(getString(if (initialCategory != null) R.string.Save else R.string.Create), false)
            setOnClickListener { save() }
        }
        buttonContainer = FrameLayout(context).apply {
            background = UIUtil.createBottomFade(getThemedColor(Theme.key_windowBackgroundGray))
            addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48f, Gravity.BOTTOM, 16f, 12f, 16f, 12f))
        }
        (view as FrameLayout).addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM))
        listView.setPadding(0, listView.paddingTop, 0, dp(BUTTON_AREA_DP))

        loadChannels()
        return view
    }

    override fun onInsets(left: Int, top: Int, right: Int, bottom: Int) {
        super.onInsets(left, top, right, bottom)
        buttonContainer?.setPadding(0, 0, 0, bottom)
        listView.setPadding(0, listView.paddingTop, 0, bottom + dp(BUTTON_AREA_DP))
    }

    private fun showSaveButton(show: Boolean) {
        val container = buttonContainer ?: return
        container.animate().cancel()
        if (show) {
            container.visibility = View.VISIBLE
        }
        container.animate()
            .alpha(if (show) 1f else 0f)
            .translationY(if (show) 0f else dp(24f).toFloat())
            .setDuration(220)
            .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
            .withEndAction { if (!show) container.visibility = View.GONE }
            .start()
    }

    private fun createHeaderViews(context: Context) {
        iconCell = FrameLayout(context).apply {
            setPadding(0, dp(12f), 0, dp(12f))
        }
        iconView = BackupImageView(context)
        iconButton = createIconCircle(context, iconView).apply {
            setOnClickListener { openEmojiDialog() }
        }
        ScaleStateListAnimator.apply(iconButton)
        iconCell.addView(iconButton, LayoutHelper.createFrame(100, 100, Gravity.CENTER))
        bindIcon(iconView)

        titleCell = EditTextCell(context, getString(R.string.FeedCategoryName), false, false, MAX_TITLE_LENGTH, resourceProvider).apply {
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            editText.imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            hideKeyboardOnEnter()
            setText(initialCategory?.title ?: "")
        }
    }

    private fun createIconCircle(context: Context, imageView: BackupImageView): FrameLayout {
        return FrameLayout(context).apply {
            background = Theme.createCircleDrawable(dp(100f), getThemedColor(Theme.key_windowBackgroundWhite))
            addView(imageView, LayoutHelper.createFrame(64, 64, Gravity.CENTER))
        }
    }

    private fun bindIcon(imageView: BackupImageView) {
        if (emojiId == 0L) {
            imageView.setAnimatedEmojiDrawable(null)
            imageView.setImageResource(R.drawable.menu_smile_add)
            imageView.setColorFilter(PorterDuffColorFilter(getThemedColor(Theme.key_dialogEmptyImage), PorterDuff.Mode.SRC_IN))
            IconManager.requestDrawableForSize(R.drawable.menu_smile_add, dp(64f)) { drawable ->
                if (emojiId == 0L) {
                    imageView.setImageDrawable(drawable)
                }
            }
        } else {
            imageView.setAnimatedEmojiDrawable(AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_LARGE, currentAccount, emojiId))
            imageView.setColorFilter(null)
            imageView.setEmojiColorFilter(PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN))
        }
    }

    private fun openEmojiDialog() {
        if (emojiDialog != null || !iconButton.isAttachedToWindow) {
            return
        }
        AndroidUtilities.hideKeyboard(titleCell.editText)
        titleCell.editText.clearFocus()
        val context = iconButton.context
        val popup = arrayOfNulls<SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow>(1)
        val scrimIconView = BackupImageView(context)
        val scrimButton = createIconCircle(context, scrimIconView).apply {
            setOnClickListener { popup[0]?.dismiss() }
        }
        bindIcon(scrimIconView)
        val layout = object : SelectAnimatedEmojiDialog(this, context, true, null, SelectAnimatedEmojiDialog.TYPE_AI_STYLE_ICON, resourceProvider) {
            override fun isBottom(): Boolean = false

            override fun onEmojiSelected(view: View, documentId: Long?, document: TLRPC.Document?, gift: TL_stars.TL_starGiftUnique?, until: Int?) {
                emojiId = documentId ?: 0L
                bindIcon(iconView)
                bindIcon(scrimIconView)
                popup[0]?.dismiss()
            }
        }
        layout.setSelected(if (emojiId != 0L) emojiId else null)
        val container = FrameLayout(context).apply {
            addView(scrimButton, LayoutHelper.createFrame(100, 100, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
            addView(layout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0f, 100f + 8f, 0f, 0f))
        }
        popup[0] = object : SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow(container, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            private var dismissing = false

            override fun dismiss() {
                if (dismissing) {
                    return
                }
                dismissing = true
                emojiDialog = null
                layout.onDismiss { dismissWindow() }
            }

            private fun dismissWindow() {
                super.dismiss()
            }
        }
        emojiDialog = popup[0]
        val popupWidth = minOf(dp(340f - 16f), (AndroidUtilities.displaySize.x * .95f).toInt())
        val location = IntArray(2)
        iconButton.getLocationInWindow(location)
        popup[0]?.showAtLocation(iconButton, Gravity.TOP or Gravity.LEFT, location[0] + (iconButton.width - popupWidth) / 2, location[1])
        popup[0]?.dimBehind()
        layout.onShow { popup[0]?.dismiss() }
    }

    private fun loadChannels() {
        FeedController.getInstance(currentAccount).loadChannels(true) { loaded, _, failed, _ ->
            if (failed) return@loadChannels
            channels.clear()
            loaded.forEach { chat ->
                channels.add(messagesController.getChat(chat.id) ?: chat)
            }
            val initialChannels = initialCategory?.channels ?: emptySet()
            channels.sortWith(compareByDescending<TLRPC.Chat> { initialChannels.contains(-it.id) }.then(BY_TITLE))
            channelsLoaded = true
            listView.adapter?.update(true)
        }
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        val config = FeedConfig.getInstance(currentAccount)
        val currentQuery = query
        val filtering = !TextUtils.isEmpty(currentQuery)
        if (!filtering) {
            items.add(UItem.asCustomShadow(iconCell))
            items.add(UItem.asCustom(titleCell))
            items.add(UItem.asShadow())
        }

        val shown = ArrayList<UItem>()
        val hidden = ArrayList<UItem>()
        channels.forEach { chat ->
            if (filtering && (chat.title == null || !chat.title.lowercase(Locale.getDefault()).contains(currentQuery!!))) {
                return@forEach
            }
            val item = UItem.asUserCheckbox(chat.id.hashCode(), chat).setChecked(selectedChannels.contains(-chat.id))
            (if (config.isExcluded(-chat.id)) hidden else shown).add(item)
        }
        if (shown.isNotEmpty()) {
            items.add(UItem.asHeader(getString(R.string.FeedChannels)))
            items.addAll(shown)
        }
        if (hidden.isNotEmpty()) {
            if (shown.isNotEmpty()) {
                items.add(UItem.asShadow())
            }
            items.add(UItem.asHeader(getString(R.string.FeedHiddenFromAll)))
            items.addAll(hidden)
        }
        if (!filtering && (shown.isNotEmpty() || hidden.isNotEmpty())) {
            items.add(UItem.asShadow(getString(R.string.FeedCategoryChannelsInfo)))
        }
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        val chat = item.`object` as? TLRPC.Chat
        if (chat != null) {
            toggleBooleanSettingAndRefresh(item) { checked ->
                if (checked) selectedChannels.add(-chat.id) else selectedChannels.remove(-chat.id)
            }
        }
    }

    private fun setAllSelected(selected: Boolean) {
        if (selected) {
            channels.forEach { selectedChannels.add(-it.id) }
        } else {
            selectedChannels.clear()
        }
        listView.adapter?.update(true)
    }

    override fun onBackPressed(invoked: Boolean): Boolean {
        if (searching) {
            if (invoked) {
                actionBar.closeSearchField()
            }
            return false
        }
        if (hasChanges()) {
            if (invoked) {
                showUnsavedDialog()
            }
            return false
        }
        return super.onBackPressed(invoked)
    }

    override fun isSwipeBackEnabled(event: MotionEvent?): Boolean {
        return !hasChanges() && super.isSwipeBackEnabled(event)
    }

    private fun currentTitle(): String = titleCell.text?.toString()?.trim() ?: ""

    private fun hasChanges(): Boolean {
        val category = initialCategory
            ?: return currentTitle().isNotEmpty() || emojiId != 0L || selectedChannels.isNotEmpty()
        return currentTitle() != category.title || emojiId != category.emojiId || selectedChannels != category.channels
    }

    private fun showUnsavedDialog() {
        val activity = parentActivity ?: return
        val dialog = AlertDialog.Builder(activity, resourceProvider).apply {
            setTitle(getString(R.string.UnsavedChanges))
            setMessage(getString(R.string.FeedCategoryUnsavedChanges))
            setPositiveButton(getString(R.string.Save)) { _, _ -> save() }
            setNegativeButton(getString(R.string.PassportDiscard)) { _, _ -> finishFragment() }
        }.create()
        showDialog(dialog)
    }

    private fun save() {
        val title = currentTitle()
        if (title.isEmpty()) {
            BotWebViewVibrationEffect.APP_ERROR.vibrate()
            AndroidUtilities.shakeViewSpring(titleCell)
            titleCell.editText.requestFocus()
            AndroidUtilities.showKeyboard(titleCell.editText)
            return
        }
        val channelIds = if (channelsLoaded) {
            selectedChannels.filterTo(HashSet()) { id -> channels.any { -it.id == id } }
        } else {
            selectedChannels
        }
        val controller = FeedController.getInstance(currentAccount)
        val category = FeedConfig.getInstance(currentAccount).saveCategory(initialCategory?.id ?: 0, title, emojiId, channelIds)
        if (initialCategory != null || !selectOnCreate || !controller.selectCategory(category.id)) {
            controller.notifyCategoriesChanged()
        }
        finishFragment()
    }

    override fun onFragmentDestroy() {
        emojiDialog?.dismiss()
        super.onFragmentDestroy()
    }
}
