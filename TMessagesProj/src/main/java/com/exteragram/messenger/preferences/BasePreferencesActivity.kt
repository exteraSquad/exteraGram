/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.preferences

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Parcelable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.collection.LongSparseArray
import androidx.core.util.Consumer
import androidx.recyclerview.widget.LinearLayoutManager
import com.exteragram.messenger.preferences.utils.SettingsRegistry
import com.exteragram.messenger.utils.ui.ChatHeaderUiHelper
import com.exteragram.messenger.utils.ui.PopupUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocaleController.formatString
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.messenger.SavedMessagesController
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.CheckBoxCell
import org.telegram.ui.ChatActivity
import org.telegram.ui.Cells.TextCell
import org.telegram.ui.Cells.TextCheckCell
import org.telegram.ui.Components.Bulletin
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.ItemOptions
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.ShareAlert
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Components.UniversalRecyclerView
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl
import java.util.ArrayList
import kotlin.math.roundToInt

abstract class BasePreferencesActivity : BaseFragment() {

    internal lateinit var listView: UniversalRecyclerView
    private var pinnedHeader: View? = null
    protected lateinit var layoutManager: LinearLayoutManager
    private var headerFadeView: View? = null
    private var recyclerViewState: Parcelable? = null

    protected open val pinnedHeaderHeight = 48f
    protected open val pinnedHeaderMargin = 7f
    protected open val pinnedHeaderGap = 4f
    protected open val pinnedHeaderInset: Float
        get() = pinnedHeaderHeight + pinnedHeaderGap + 4.5f
    private var pinnedHeaderVisibility = 1f
    private var pinnedHeaderVisible = true
    private var pinnedHeaderAnimator: ValueAnimator? = null
    private var topInset = AndroidUtilities.statusBarHeight

    override fun createView(context: Context): View {
        initializeOptionStrings()

        actionBar.apply {
            setBackButtonImage(R.drawable.ic_ab_back)
            setAllowOverlayTitle(false)
            setTitle(this@BasePreferencesActivity.getTitle())
            setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
                override fun onItemClick(id: Int) {
                    if (id == -1) finishFragment()
                }
            })
            setCastShadows(false)
            setAddToContainer(false)
        }

        val contentView = HeaderContentView(context)
        contentView.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray))

        actionBar.menu ?: actionBar.createMenu()

        val simpleGlass = baseSimpleGlass

        actionBar.apply {
            setGlassPadding(dp(12f))
            setGlassTitleTextSize(20)
            setupGlass(simpleGlass.factory, BlurredBackgroundProviderImpl.topPanelChatActivity(getResourceProvider()))
            setMenuGlass(simpleGlass.menuFactory, simpleGlass.menuGlassSource)
            setMenuRise(dp(8f))
            setMenuOverlayProvider(::getSimpleMenuOverlay)
            ChatHeaderUiHelper.applyChatHeaderGlassStyle(this, true)
            setGlassSearchMorph(true)
            setTitleColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText))
            setItemsColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), false)
            setItemsBackgroundColor(getThemedColor(Theme.key_listSelector), false)
        }

        pinnedHeader = createPinnedHeader(context, simpleGlass.factory)

        listView = UniversalRecyclerView(this, ::fillItems, ::onClick, ::onLongClick).apply {
            setSections()
            if (needHideTitle()) actionBar.setAdaptiveBackground(this, true)
            adapter.setApplyBackground(false)
            setClipToPadding(false)
            setPadding(0, getListTopPadding(topInset) + getPinnedHeaderOffset(), 0, 0)

            setLayoutManager(LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false).also {
                this@BasePreferencesActivity.layoutManager = it
            })
            recyclerViewState?.let {
                this@BasePreferencesActivity.layoutManager.onRestoreInstanceState(it)
                recyclerViewState = null
            }
        }

        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
        simpleGlass.setViews(contentView, listView, null, resourceProvider)

        if (hasHeaderFade()) {
            headerFadeView = View(context).apply {
                background = simpleGlass.createActionBarFadeDrawable(this)
            }
            contentView.addView(headerFadeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 0, Gravity.TOP))
        }

        pinnedHeader?.let {
            setPinnedHeaderVisibility(pinnedHeaderVisibility)
            contentView.addView(it, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, pinnedHeaderHeight, Gravity.TOP, pinnedHeaderMargin, 0f, pinnedHeaderMargin, 0f))
            updatePinnedHeaderPosition()
        }

        contentView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP))

        fragmentView = contentView
        return fragmentView
    }

    internal fun updateHeaderFade() {
        headerFadeView?.let(baseSimpleGlass::updateActionBarFadeDrawable)
    }

    override fun clearViews() {
        if (fragmentView != null && ::layoutManager.isInitialized) {
            recyclerViewState = layoutManager.onSaveInstanceState()
        }
        super.clearViews()
    }

    private inner class HeaderContentView(context: Context) : FrameLayout(context), Theme.Colorable {

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            headerFadeView?.let {
                val height = actionBar.measuredHeight + getPinnedHeaderOffset()
                if (it.measuredHeight != height) {
                    it.layoutParams.height = height
                    it.measure(MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
                }
            }
        }

        override fun updateColors() {
            setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray))
        }
    }

    override fun onResume() {
        super.onResume()
        listView.adapter?.update(false)
        Bulletin.addDelegate(this, object : Bulletin.Delegate {
            override fun getTopOffset(tag: Int): Int =
                AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() + getPinnedHeaderOffset()
            override fun getBottomOffset(tag: Int): Int = bottomInset
        })
    }

    override fun onPause() {
        super.onPause()
        Bulletin.removeDelegate(this)
    }

    abstract fun getTitle(): String

    protected open fun initializeOptionStrings() {}

    fun scrollToItem(itemId: Int) {
        if (!::listView.isInitialized) return
        val position = listView.findPositionByItemId(itemId)
        if (position in 0 until (listView.adapter?.itemCount ?: 0)) {
            layoutManager.scrollToPositionWithOffset(position, dp(80f))
            listView.highlightRow { listView.findPositionByItemId(itemId) }
        } else {
            SettingsRegistry.getInstance().onSettingNotFound(this)
        }
    }

    protected fun showListDialog(
        item: UItem,
        options: Array<out CharSequence>,
        title: String,
        currentValue: Int,
        onSelected: PopupUtils.OnItemClickListener
    ) = showListDialog(item, options, null, title, currentValue, onSelected)

    @JvmOverloads
    protected fun showListDialog(
        item: UItem,
        options: Array<out CharSequence>,
        icons: IntArray?,
        title: String,
        currentValue: Int,
        onSelected: PopupUtils.OnItemClickListener,
        useRadioCell: Boolean = icons == null,
        skipIfValueNotUpdated: Boolean = true
    ) {
        parentActivity ?: return
        PopupUtils.showDialog(options, icons, title, currentValue, context, { i ->
            if (skipIfValueNotUpdated && currentValue == i) return@showDialog
            onSelected.onClick(i)

            val view = listView.findViewByItemId(item.id)
            if (view is TextCell) view.setValue(options[i], true)
            listView.adapter?.update(true)
        }, resourceProvider, useRadioCell)
    }

    protected fun showRestartBulletin() {
        BulletinFactory.of(this).createSimpleBulletin(
            R.raw.info,
            getString(R.string.RestartRequired),
            getString(R.string.BotUnblock)
        ) {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val mainIntent = Intent.makeRestartActivityTask(intent?.component)
            mainIntent.setPackage(context.packageName)
            context.startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
        }.show()
    }

    internal fun toggleBooleanSettingAndRefresh(item: UItem, stateSetter: Consumer<Boolean>) {
        val view = listView.findViewByItemId(item.id)
        val newValue = !item.checked
        stateSetter.accept(newValue)

        item.setChecked(newValue)
        when (view) {
            is CheckBoxCell -> view.setChecked(newValue, true)
            is TextCheckCell -> view.isChecked = newValue
        }
        listView.adapter?.update(true)
    }

    protected open fun needHideTitle(): Boolean = false

    protected open fun hasHeaderFade(): Boolean = true

    override fun isLightStatusBar(): Boolean {
        actionBar ?: return super.isLightStatusBar()
        return ChatHeaderUiHelper.isLightChatStatusBar(actionBar, getThemedColor(Theme.key_windowBackgroundGray), true)
    }

    protected abstract fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter)

    protected open fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {}

    protected open fun onLongClick(item: UItem, view: View, position: Int, x: Float, y: Float): Boolean {
        SettingsRegistry.getInstance().getFirstSettingLink(this.javaClass, item)?.let {
            showCopyLinkOptions(view, it)
            return true
        }
        return false
    }

    protected fun showCopyLinkOptions(view: View, settingLink: String) {
        ItemOptions.makeOptions(this, view).apply {
            add(R.drawable.msg_copy, getString(R.string.CopyLink)) {
                if (AndroidUtilities.addToClipboard(settingLink))
                    BulletinFactory.of(this@BasePreferencesActivity).createCopyBulletin(getString(R.string.LinkCopied)).show()
            }

            add(R.drawable.msg_share, getString(R.string.ShareLink)) { showShareLinkAlert(settingLink) }

            setScrimViewBackground(listView.getClipBackground(view))
            setDismissOnMoveOutside(true)
            show()
        }
    }

    protected fun showShareLinkAlert(
        link: String,
        @StringRes savedMessagesRes: Int = R.string.SettingLinkToSavedMessages,
        @StringRes userRes: Int = R.string.SettingLinkToUser,
        @StringRes chatsRes: Int = R.string.SettingLinkToChats,
    ) {
        showDialog(object : ShareAlert(context, null, link, false, link, false, resourceProvider) {
            override fun onSend(dialogIds: LongSparseArray<TLRPC.Dialog>?, count: Int, topic: TLRPC.TL_forumTopic?, showToast: Boolean) {
                if (!showToast) return

                val messageText: CharSequence = when {
                    dialogIds?.size() == 1 -> {
                        val dialogId = dialogIds.valueAt(0).id
                        if (dialogId == 0L || dialogId == userConfig.clientUserId)
                            AndroidUtilities.replaceSingleTag(getString(savedMessagesRes), -1, AndroidUtilities.REPLACING_TAG_TYPE_LINKBOLD, SavedMessagesController::openSavedMessages)
                        else
                            AndroidUtilities.replaceSingleTag(formatString(userRes, messagesController.getPeerName(dialogId, true)), -1, AndroidUtilities.REPLACING_TAG_TYPE_LINKBOLD) {
                                this@BasePreferencesActivity.presentFragment(ChatActivity.of(dialogId))
                            }
                    }
                    else -> AndroidUtilities.replaceTags(formatString(chatsRes, LocaleController.formatPluralString("Chats", count)))
                }

                BulletinFactory.of(this@BasePreferencesActivity).createSimpleBulletin(R.raw.forward, messageText, 2).apply {
                    hideAfterBottomSheet = false
                    show(true)
                }
            }
        })
    }

    @Deprecated("Deprecated in Java")
    override fun isSupportEdgeToEdge(): Boolean = true

    override fun onInsets(left: Int, top: Int, right: Int, bottom: Int) {
        topInset = top
        listView.apply {
            setPadding(0, getListTopPadding(top) + getPinnedHeaderOffset(), 0, bottom)
            setClipToPadding(false)
        }
        updatePinnedHeaderPosition()
    }

    protected open fun getListTopPadding(topInset: Int): Int =
        topInset + ActionBar.getCurrentActionBarHeight()

    protected open fun createPinnedHeader(context: Context, glassFactory: BlurredBackgroundDrawableViewFactory): View? = null

    protected fun getPinnedHeaderOffset(): Int =
        if (pinnedHeader != null) (dp(pinnedHeaderInset) * pinnedHeaderVisibility).roundToInt() else 0

    protected fun setPinnedHeaderVisible(visible: Boolean, animated: Boolean) {
        if (pinnedHeaderVisible == visible && (animated || pinnedHeaderAnimator == null)) return
        pinnedHeaderVisible = visible
        pinnedHeaderAnimator?.cancel()
        pinnedHeaderAnimator = null
        val target = if (visible) 1f else 0f
        if (!animated || fragmentView == null) {
            setPinnedHeaderVisibility(target)
            return
        }
        pinnedHeaderAnimator = ValueAnimator.ofFloat(pinnedHeaderVisibility, target).apply {
            addUpdateListener { animation -> setPinnedHeaderVisibility(animation.animatedValue as Float) }
            duration = 350
            interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
            start()
        }
    }

    private fun setPinnedHeaderVisibility(visibility: Float) {
        pinnedHeaderVisibility = visibility
        val header = pinnedHeader ?: return
        val scale = AndroidUtilities.lerp(0.98f, 1f, visibility)
        header.alpha = visibility
        header.scaleX = scale
        header.scaleY = scale
        header.visibility = if (visibility > 0f) View.VISIBLE else View.GONE
        if (fragmentView == null) return
        val atTop = !listView.canScrollVertically(-1)
        listView.setPadding(0, getListTopPadding(topInset) + getPinnedHeaderOffset(), 0, listView.paddingBottom)
        if (atTop) listView.scrollToPosition(0)
        updatePinnedHeaderPosition()
        headerFadeView?.requestLayout()
    }

    private fun updatePinnedHeaderPosition() {
        pinnedHeader?.translationY = getListTopPadding(topInset) + dp(pinnedHeaderGap) - (1f - pinnedHeaderVisibility) * dp(pinnedHeaderHeight)
    }

    internal fun uCheck(
        enumEntry: Enum<*>,
        text: CharSequence,
        checked: Boolean,
        subtext: CharSequence? = null,
        checkBoxIcon: Int = 0,
        searchable: Boolean = true,
        linkAlias: String? = null,
        multiline: Boolean = true,
        icon: Int = 0,
    ): UItem = uCheck(
        id = enumEntry.ordinal + 1,
        text = text,
        checked = checked,
        subtext = subtext,
        checkBoxIcon = checkBoxIcon,
        searchable = searchable,
        linkAlias = linkAlias,
        multiline = multiline,
        icon = icon,
    )

    internal fun uCheck(
        id: Int,
        text: CharSequence,
        checked: Boolean,
        subtext: CharSequence? = null,
        checkBoxIcon: Int = 0,
        searchable: Boolean = true,
        linkAlias: String? = null,
        multiline: Boolean = true,
        icon: Int = 0,
    ): UItem = when {
        subtext != null -> UItem.asCheck(id, text, subtext, multiline)
        else -> UItem.asCheck(id, text)
    }.apply {
        setChecked(checked)
        if (icon != 0) iconResId = icon
        if (checkBoxIcon != 0) setCheckBoxIcon(checkBoxIcon)
        if (searchable) setSearchable(this@BasePreferencesActivity)
        linkAlias?.let { setLinkAlias(it, this@BasePreferencesActivity) }
    }

    internal fun uButton(
        enumEntry: Enum<*>,
        text: CharSequence,
        icon: Int = 0,
        value: CharSequence? = null,
        subtext: CharSequence? = null,
        showDivider: Boolean? = null,
        searchable: Boolean = true,
        linkAlias: String? = null,
        red: Boolean = false,
        accent: Boolean = false,
        prioritizeTitleOverValue: Boolean? = null,
    ): UItem = uButton(
        id = enumEntry.ordinal + 1,
        text = text,
        icon = icon,
        value = value,
        subtext = subtext,
        showDivider = showDivider,
        searchable = searchable,
        linkAlias = linkAlias,
        red = red,
        accent = accent,
        prioritizeTitleOverValue = prioritizeTitleOverValue,
    )

    internal fun uButton(
        id: Int,
        text: CharSequence,
        icon: Int = 0,
        value: CharSequence? = null,
        subtext: CharSequence? = null,
        showDivider: Boolean? = null,
        searchable: Boolean = true,
        linkAlias: String? = null,
        red: Boolean = false,
        accent: Boolean = false,
        prioritizeTitleOverValue: Boolean? = null,
    ): UItem = when {
        !subtext.isNullOrEmpty() -> UItem.asButtonWithSubtext(id, icon, text, subtext, 64, 60)
        !value.isNullOrEmpty() && icon != 0 -> UItem.asButton(id, icon, text, value)
        !value.isNullOrEmpty() -> UItem.asButton(id, text, value)
        icon != 0 -> UItem.asButton(id, icon, text)
        else -> UItem.asButton(id, text)
    }.apply {
        if (red) red()
        if (accent) accent()
        if (searchable) setSearchable(this@BasePreferencesActivity)
        linkAlias?.let { setLinkAlias(it, this@BasePreferencesActivity) }
        showDivider?.let { showDivider(it) }
        prioritizeTitleOverValue?.let { prioritizeTitleOverValue(it) }
    }
}
