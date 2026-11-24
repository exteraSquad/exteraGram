/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.reactions

import android.animation.LayoutTransition
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Build
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.view.ActionMode
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import com.exteragram.messenger.reactions.ReactionsEditHelper.Companion.STAR_ID
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.AndroidUtilities.replaceTags
import org.telegram.messenger.ChatObject
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController.formatPluralString
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.browser.Browser
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tl.TL_stars
import org.telegram.tgnet.tl.TL_stories
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.ShadowSectionCell
import org.telegram.ui.Cells.SlideIntChooseView
import org.telegram.ui.Cells.TextCheckCell
import org.telegram.ui.Cells.TextInfoPrivacyCell
import org.telegram.ui.ChatReactionsEditActivity.SELECT_TYPE_ALL
import org.telegram.ui.ChatReactionsEditActivity.SELECT_TYPE_NONE
import org.telegram.ui.ChatReactionsEditActivity.SELECT_TYPE_SOME
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.AnimatedEmojiSpan
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.Reactions.BackSpaceButtonView
import org.telegram.ui.Components.Reactions.CustomReactionEditText
import org.telegram.ui.Components.Reactions.ReactionsUtils
import org.telegram.ui.Components.Reactions.UpdateReactionsButton
import org.telegram.ui.Components.SectionsScrollView
import org.telegram.ui.SelectAnimatedEmojiDialog
import java.util.concurrent.CountDownLatch

class ReactionsEditActivity(
    private val chatId: Long,
    private val chatInfo: TLRPC.ChatFull?
) : BaseFragment(), NotificationCenter.NotificationCenterDelegate {

    private lateinit var info: TLRPC.ChatFull

    private lateinit var scrollView: SectionsScrollView
    private lateinit var switchLayout: LinearLayout
    private lateinit var enableReactionsCell: TextCheckCell
    private lateinit var editText: CustomReactionEditText
    private lateinit var actionButton: UpdateReactionsButton
    private lateinit var actionButtonContainer: FrameLayout
    private lateinit var bottomDialogLayout: FrameLayout
    private var paidCheckCell: TextCheckCell? = null
    private var selectAnimatedEmojiDialog: SelectAnimatedEmojiDialog? = null

    private val allAvailableReactions = ArrayList<TLRPC.TL_availableReaction>()
    private val selectedEmojisMap = LinkedHashMap<Long, AnimatedEmojiSpan>()
    private val selectedEmojisIds = ArrayList<Long>()
    private val helper = ReactionsEditHelper(currentAccount, chatId, allAvailableReactions)
    private var isChannel = false
    private var maxReactionsCount = 0

    private var initialSelectedIds = emptySet<Long>()
    private var initialPaid = false
    private var initialType = SELECT_TYPE_NONE

    private var selectedType = -1
    private var paid = false
    private var backupPaid = false
    private var reactionsCount = 0
    private var initialReactionsCount = 0
    private var selectedCustomReactions = 0
    private var boostsStatus: TL_stories.TL_premium_boostsStatus? = null
    private var emojiKeyboardVisible = false
    private var paused = false
    private var actionButtonShown = true
    private var observers: NotificationCenter.ObserversGroup? = null

    private val starEnabled get() = paidCheckCell?.isChecked == true
    private val checkAfterFastDeleteRunnable = Runnable { checkMaxCustomReactions(false) }

    override fun onFragmentCreate(): Boolean {
        val chat = messagesController.getChat(chatId)
            ?: messagesStorage.getChatSync(chatId)?.also { messagesController.putChat(it, true) }
            ?: return false

        info = chatInfo
            ?: messagesStorage.loadChatInfo(chatId, ChatObject.isChannel(chat), CountDownLatch(1), false, false)
            ?: return false

        isChannel = ChatObject.isChannelAndNotMegaGroup(chat)
        allAvailableReactions += mediaDataController.enabledReactionsList
        maxReactionsCount = if (isChannel) messagesController.boostsChannelLevelMax else allAvailableReactions.size

        if (isChannel) {
            messagesController.boostsController.getBoostsStats(-chatId) { status ->
                boostsStatus = status
                if (selectedEmojisMap.keys != initialSelectedIds) checkMaxCustomReactions(false)
            }
        }

        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512)
        observers = notificationCenter.createObserversGroup(this).add(NotificationCenter.dialogDeleted)
        return super.onFragmentCreate()
    }

    override fun createView(context: Context): View {
        actionBar.setTitle(getString(R.string.Reactions))
        actionBar.setBackButtonImage(R.drawable.ic_ab_back)
        actionBar.setAllowOverlayTitle(true)
        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                if (id == -1 && !checkChangesBeforeExit(true)) finishFragment()
            }
        })

        val contentLayout = SectionsScrollView.SectionsLinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView = SectionsScrollView(context, contentLayout, resourceProvider).apply {
            isFillViewport = true
            addView(contentLayout)
        }
        actionBar.setAdaptiveBackground(scrollView)

        addEnableReactionsCell(context, contentLayout)

        switchLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutTransition = LayoutTransition().apply {
                setDuration(200)
                enableTransitionType(LayoutTransition.CHANGING)
            }
        }
        contentLayout.addView(switchLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat()))
        fillSwitchLayout(context)

        val rootLayout = createRootLayout(context)
        rootLayout.addView(scrollView)

        actionButtonContainer = FrameLayout(context)
        val gradient = ImageView(context).apply {
            setImageResource(R.drawable.gradient_bottom)
            scaleType = ImageView.ScaleType.FIT_XY
            colorFilter = PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider), PorterDuff.Mode.SRC_ATOP)
        }
        actionButtonContainer.addView(gradient, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL))

        actionButton = UpdateReactionsButton(context, resourceProvider).apply {
            setRound()
            setDefaultState()
            setOnClickListener { applyChanges() }
        }
        actionButtonContainer.addView(actionButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48f, Gravity.BOTTOM, 13f, 13f, 13f, 13f))
        rootLayout.addView(actionButtonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 13f + 48f + 13f, Gravity.BOTTOM, 0f, 0f, 0f, 0f))

        bottomDialogLayout = object : FrameLayout(context) {
            override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                super.onLayout(changed, left, top, right, bottom)
                if (!emojiKeyboardVisible || !changed) return

                actionButtonContainer.translationY = -measuredHeight.toFloat()
                updateScrollViewMarginBottom(measuredHeight)
            }
        }
        bottomDialogLayout.visibility = View.INVISIBLE
        rootLayout.addView(bottomDialogLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM))

        fillInitialReactions()

        enableReactionsCell.setTextAndCheck(getString(R.string.EnableReactions), selectedType != SELECT_TYPE_NONE || paid, false)
        editText.addReactionsSpan()

        if (isChannel && info.paid_media_allowed && info.paid_reactions_available) enableStars(withToast = false)

        initialSelectedIds = selectedEmojisMap.keys.toSet()
        initialPaid = paid
        updateActionButton(animated = false)

        return rootLayout.also { fragmentView = it }
    }

    private fun createRootLayout(context: Context) = object : FrameLayout(context) {
        private val adjustPanLayoutHelper = object : AdjustPanLayoutHelper(this) {
            override fun onTransitionStart(keyboardVisible: Boolean, contentHeight: Int) {
                if (!actionButtonShown) return

                actionButtonContainer.visibility = VISIBLE
                actionButtonContainer.animate().alpha(if (keyboardVisible) 0f else 1f).withEndAction {
                    if (keyboardVisible) actionButtonContainer.visibility = INVISIBLE
                }.start()
            }

            override fun applyTranslation() = false

            override fun heightAnimationEnabled() =
                !inPreviewMode && !inBubbleMode && !AndroidUtilities.isTablet() && !AndroidUtilities.isInMultiwindow && parentLayout != null
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            adjustPanLayoutHelper.onAttach()
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            adjustPanLayoutHelper.onDetach()
        }
    }.apply {
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
    }

    private fun addEnableReactionsCell(context: Context, contentLayout: LinearLayout) {
        enableReactionsCell = TextCheckCell(context).apply {
            height = 56
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundUnchecked))
            setTypeface(AndroidUtilities.bold())
            setColors(Theme.key_windowBackgroundCheckText, Theme.key_switchTrackBlue, Theme.key_switchTrackBlueChecked, Theme.key_switchTrackBlueThumb, Theme.key_switchTrackBlueThumbChecked)
            setOnClickListener { onEnableReactionsClick() }
        }
        contentLayout.addView(enableReactionsCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

        val infoText = getString(if (isChannel) R.string.ReactionAddEmojiFromAnyPack else R.string.EnableReactionsGroupInfo)
        contentLayout.addView(createInfoCell(context, infoText),LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
    }

    private fun createInfoCell(context: Context, text: CharSequence, bottomPadding: Int = 16) =
        TextInfoPrivacyCell(context, 12, resourceProvider).apply {
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4))
            setTopPadding(12)
            setBottomPadding(bottomPadding)
            setText(text)
        }

    private fun fillSwitchLayout(context: Context) {
        val headerCell = HeaderCell(context).apply {
            setText(getString(R.string.AvailableReactions))
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
            setTextSize(15f)
            setTopMargin(14)
        }
        switchLayout.addView(headerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

        editText = createEditText(context)
        switchLayout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

        if (isChannel) {
            val ownPackInfo = TextInfoPrivacyCell(context, 12, resourceProvider).apply {
                setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4))
                text = AndroidUtilities.replaceSingleTag(
                    getString(R.string.ReactionCreateOwnPack),
                    Theme.key_chat_messageLinkIn, 0,
                    { Browser.openUrl(context, "https://t.me/stickers") },
                    resourceProvider
                )
            }
            switchLayout.addView(ownPackInfo, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
        } else {
            switchLayout.addView(ShadowSectionCell(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
        }

        switchLayout.addView(HeaderCell(context, resourceProvider).apply { setText(getString(R.string.MaximumReactionsHeader)) }, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

        val hasReactionsLimit = if (info is TLRPC.TL_chatFull) (info.flags and 1048576) != 0 else (info.flags2 and 8192) != 0
        reactionsCount = if (hasReactionsLimit) info.reactions_limit else messagesController.reactionsUniqMax
        initialReactionsCount = reactionsCount

        val slideView = SlideIntChooseView(context, resourceProvider).apply {
            set(reactionsCount, SlideIntChooseView.Options.make(0, if (isChannel) "MaximumReactionsValue" else "MaximumReactionsGroupValue", 1, messagesController.reactionsUniqMax)) {
                reactionsCount = it
                updateActionButton()
            }
        }
        switchLayout.addView(slideView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

        val paidAvailable = isChannel && info.paid_media_allowed
        val maxReactionsInfo = TextInfoPrivacyCell(context, 12, resourceProvider).apply {
            setTopPadding(12)
            setBottomPadding(if (paidAvailable) 16 else 70)
            text = getString(if (isChannel) R.string.MaximumReactionsInfo else R.string.MaximumReactionsGroupInfo)
        }
        switchLayout.addView(maxReactionsInfo, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

        if (!paidAvailable) return

        paidCheckCell = TextCheckCell(context).apply {
            setTextAndCheck(getString(R.string.ChannelEnablePaidReactions), false, false)
            setOnClickListener { toggleStarsEnabled() }
        }
        switchLayout.addView(paidCheckCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

        val paidInfo = AndroidUtilities.withLearnMore(getString(R.string.ChannelEnablePaidReactionsInfo)) {
            Browser.openUrl(context, getString(R.string.ChannelEnablePaidReactionsInfoLink))
        }
        switchLayout.addView(createInfoCell(context, paidInfo, bottomPadding = 70), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
    }

    private fun createEditText(context: Context) = object : CustomReactionEditText(context, resourceProvider, Int.MAX_VALUE) {
        override fun onLineCountChanged(oldLineCount: Int, newLineCount: Int) {
            if (newLineCount > oldLineCount) scrollView.smoothScrollBy(0, dp(30f))
        }

        override fun extendActionMode(actionMode: ActionMode?, menu: Menu) {
            super.extendActionMode(actionMode, menu)

            if (hasSelection()) {
                menu.add(Menu.NONE, android.R.id.cut, 1, android.R.string.cut)
                menu.add(Menu.NONE, android.R.id.copy, 2, android.R.string.copy)
            }

            if (helper.hasClipboard) menu.add(Menu.NONE, android.R.id.paste, 3, android.R.string.paste)
        }

        override fun onTextContextMenuItem(id: Int) = when (id) {
            R.id.menu_delete -> deleteSelectedEmojis()
            android.R.id.cut -> {
                helper.copyReactions(text, editTextSelectionStart, editTextSelectionEnd)
                deleteSelectedEmojis()
            }
            android.R.id.copy -> {
                helper.copyReactions(text, editTextSelectionStart, editTextSelectionEnd)
                setSelection(editTextSelectionEnd)
                true
            }
            android.R.id.paste -> {
                pasteReactionsFromClipboard()
                true
            }
            else -> super.onTextContextMenuItem(id)
        }
    }.apply {
        setOnFocused(::showKeyboard)
    }

    private fun fillInitialReactions() {
        when (val reactions = info.available_reactions) {
            is TLRPC.TL_chatReactionsAll -> {
                initialType = SELECT_TYPE_ALL
                appendDefaultReactions()
            }

            is TLRPC.TL_chatReactionsSome -> {
                initialType = SELECT_TYPE_SOME
                appendChatReactions(reactions.reactions)
            }

            is TLRPC.TL_chatReactionsNone -> {
                initialType = SELECT_TYPE_NONE
                val paidOnly = isChannel && info.paid_media_allowed && info.paid_reactions_available
                if (!restoreReactionsBackup() && !paidOnly) appendDefaultReactions()
            }
        }

        val enabled = initialType != SELECT_TYPE_NONE
        setCheckedEnableReactionCell(if (enabled) SELECT_TYPE_SOME else SELECT_TYPE_NONE, paid, false)
    }

    private inline fun appendToEditText(block: SpannableStringBuilder.() -> Unit) {
        editText.append(SpannableStringBuilder().apply(block))
    }

    private fun SpannableStringBuilder.appendReaction(reaction: TLRPC.TL_availableReaction) =
        ReactionsUtils.addReactionToEditText(reaction, selectedEmojisMap, selectedEmojisIds, this, selectAnimatedEmojiDialog, editText.fontMetricsInt)

    private fun SpannableStringBuilder.appendCustomReaction(reaction: TLRPC.TL_reactionCustomEmoji) =
        ReactionsUtils.addReactionToEditText(reaction, selectedEmojisMap, selectedEmojisIds, this, selectAnimatedEmojiDialog, editText.fontMetricsInt)

    private fun SpannableStringBuilder.appendReaction(documentId: Long) {
        val reaction = helper.findAvailableReaction(documentId)
        if (reaction != null) {
            appendReaction(reaction)
            return
        }

        appendCustomReaction(TLRPC.TL_reactionCustomEmoji().apply { document_id = documentId })
    }

    private fun appendDefaultReactions() = appendToEditText {
        allAvailableReactions.take(maxReactionsCount).forEach { appendReaction(it) }
    }

    private fun appendChatReactions(reactions: List<TLRPC.Reaction>) = appendToEditText {
        reactions
            .mapNotNull { reaction ->
                when (reaction) {
                    is TLRPC.TL_reactionEmoji -> mediaDataController.reactionsMap[reaction.emoticon]
                    is TLRPC.TL_reactionCustomEmoji -> reaction
                    else -> null
                }
            }
            .take(maxReactionsCount)
            .forEach { reaction ->
                when (reaction) {
                    is TLRPC.TL_availableReaction -> appendReaction(reaction)
                    is TLRPC.TL_reactionCustomEmoji -> appendCustomReaction(reaction)
                }
            }
    }

    private fun onEnableReactionsClick() {
        if (enableReactionsCell.isChecked) {
            backupPaid = starEnabled
            if (starEnabled) toggleStarsEnabled()
            setCheckedEnableReactionCell(SELECT_TYPE_NONE, paid = false, animated = true)
            return
        }

        setCheckedEnableReactionCell(SELECT_TYPE_SOME, paid, true)
        if (!backupPaid || paidCheckCell == null || starEnabled) return

        backupPaid = false
        enableStars()
    }

    private fun applyChanges() {
        if (actionButton.isLoading) return

        val status = boostsStatus
        if (selectedType != SELECT_TYPE_NONE && status != null && status.level < selectedCustomReactions) {
            ReactionsUtils.showLimitReachedDialogForReactions(-chatId, selectedCustomReactions, status)
            return
        }

        val allowPaid = paidCheckCell?.isChecked
        saveReactionsBackup()
        actionButton.isLoading = true
        messagesController.setCustomChatReactions(chatId, requestType, grabReactions(false), reactionsCount, allowPaid, ::onApplyError) { finishFragment() }
    }

    private fun onApplyError(error: TLRPC.TL_error) {
        if (isFinishing) return

        actionButton.isLoading = false
        if (error.text == "CHAT_NOT_MODIFIED") {
            finishFragment()
            return
        }

        AndroidUtilities.runOnUIThread({
            val status = boostsStatus
            if (status != null && error.text == "BOOSTS_REQUIRED") {
                ReactionsUtils.showLimitReachedDialogForReactions(-chatId, selectedCustomReactions, status)
                return@runOnUIThread
            }

            val text = if (error.text == "REACTIONS_TOO_MANY") formatPluralString("ReactionMaxCountError", maxReactionsCount) else error.text
            BulletinFactory.of(this).createErrorBulletin(text).show()
        }, if (boostsStatus == null) 200L else 0L)
    }

    private fun saveReactionsBackup() {
        if (selectedType != SELECT_TYPE_NONE || starEnabled) backupPaid = starEnabled
        helper.saveBackup(selectedEmojisIds, backupPaid)
    }

    private fun restoreReactionsBackup(): Boolean {
        val backup = helper.loadBackup(maxReactionsCount) ?: return false
        if (backup.paid) backupPaid = true
        if (backup.reactionIds.isEmpty()) return false

        appendToEditText {
            backup.reactionIds.forEach { appendReaction(it) }
        }
        return true
    }

    private fun pasteReactionsFromClipboard() {
        val dialog = selectAnimatedEmojiDialog ?: return

        val spans = helper.parseClipboard()
        val pasteStar = spans.removeAll { it.documentId == STAR_ID }
        if (pasteStar && paidCheckCell != null && !starEnabled) enableStars()
        if (!isChannel) spans.removeAll { helper.findAvailableReaction(it.documentId) == null }

        for (span in spans) {
            if (span.documentId in selectedEmojisMap) continue

            val before = selectedEmojisMap.size
            dialog.onEmojiClick(null, span)
            if (selectedEmojisMap.size == before) break
        }
    }

    private fun initSelectAnimatedEmojiDialog() {
        if (selectAnimatedEmojiDialog != null) return

        val accentColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider)
        val dialog = object : SelectAnimatedEmojiDialog(this@ReactionsEditActivity, context, false, null,
            TYPE_CHAT_REACTIONS, false, resourceProvider, 16, accentColor) {
            private var firstLayout = true

            init {
                setDrawBackground(false)
            }

            override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
                super.onLayout(changed, left, top, right, bottom)
                if (!firstLayout) return

                firstLayout = false
                onShow(null)
            }

            override fun onEmojiSelected(view: View?, documentId: Long?, document: TLRPC.Document?, gift: TL_stars.TL_starGiftUnique?, until: Int?) {
                val id = documentId ?: return
                if (id in selectedEmojisMap) removeReaction(id) else addReaction(id, document)
            }
        }.apply {
            setAnimationsEnabled(false)
            clipChildren = false
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
        }
        selectAnimatedEmojiDialog = dialog
        bottomDialogLayout.addView(dialog, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM))

        val backSpaceButtonView = BackSpaceButtonView(context, resourceProvider).apply {
            setOnBackspace(::onBackspace)
        }
        bottomDialogLayout.addView(backSpaceButtonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.BOTTOM or Gravity.RIGHT, 0f, 0f, 8f, 8f))

        selectedEmojisIds.forEach { dialog.setMultiSelected(it, false) }
    }

    private fun addReaction(documentId: Long, document: TLRPC.Document?) {
        if (!isChannel && helper.findAvailableReaction(documentId) == null) {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.CustomReactionsChannelsOnly)).show()
            return
        }

        val selectedCount = selectedEmojisMap.size - (if (STAR_ID in selectedEmojisMap) 1 else 0)
        if (selectedCount >= maxReactionsCount) {
            BulletinFactory.of(this).createErrorBulletin(formatPluralString("ReactionMaxCountError", maxReactionsCount)).show()
            return
        }

        try {
            val selectionEnd = editText.editTextSelectionEnd
            val span = ReactionsUtils.createAnimatedEmojiSpan(document, documentId, editText.fontMetricsInt).apply {
                cacheType = AnimatedEmojiDrawable.getCacheTypeForEnterView()
                setAdded()
            }
            selectedEmojisIds.add(selectionEnd.coerceIn(0, selectedEmojisIds.size), documentId)
            selectedEmojisMap[documentId] = span
            insertSpan(span, selectionEnd)
            editText.setSelection(selectionEnd + 1)
            selectAnimatedEmojiDialog?.setMultiSelected(documentId, true)
            checkMaxCustomReactions(true)
            animateChangesInNextRows(span)
        } catch (e: Exception) {
            FileLog.e(e)
        }
    }

    private fun removeReaction(documentId: Long) {
        selectedEmojisIds.remove(documentId)
        val span = selectedEmojisMap.remove(documentId) ?: return

        removeSpanAnimated(span)
        animateChangesInNextRows(span)
        selectAnimatedEmojiDialog?.setMultiSelected(documentId, true)
        checkMaxCustomReactions(false)
    }

    private fun forgetReaction(documentId: Long) {
        selectedEmojisMap.remove(documentId)
        selectedEmojisIds.remove(documentId)
        selectAnimatedEmojiDialog?.unselect(documentId)
        if (documentId == STAR_ID) onStarRemoved()
    }

    private fun onBackspace(isFast: Boolean) {
        if (deleteSelectedEmojis()) return

        val text = editText.text ?: return
        val selectionEnd = editText.editTextSelectionEnd
        val span = text.getSpans(0, text.length, AnimatedEmojiSpan::class.java).find { text.getSpanEnd(it) == selectionEnd } ?: return
        forgetReaction(span.documentId)

        if (isFast) {
            editText.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            AndroidUtilities.cancelRunOnUIThread(checkAfterFastDeleteRunnable)
            AndroidUtilities.runOnUIThread(checkAfterFastDeleteRunnable, 350)
            return
        }

        removeSpanAnimated(span)
        animateChangesInNextRows(span)
        checkMaxCustomReactions(false)
    }

    private fun deleteSelectedEmojis(): Boolean {
        if (!editText.hasSelection()) return false

        editText.text
            ?.getSpans(editText.editTextSelectionStart, editText.editTextSelectionEnd, AnimatedEmojiSpan::class.java)
            ?.forEach { forgetReaction(it.documentId) }
        editText.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
        checkMaxCustomReactions(false)
        return true
    }

    private fun insertSpan(span: AnimatedEmojiSpan, position: Int) {
        val spannable = SpannableString("b").apply {
            setSpan(span, 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        editText.text?.insert(position, spannable)
    }

    private fun removeSpanAnimated(span: AnimatedEmojiSpan) = span.setRemoved {
        val text = editText.text ?: return@setRemoved
        val start = text.getSpanStart(span)
        val end = text.getSpanEnd(span)
        if (start == -1 || end == -1) return@setRemoved

        val selectionEnd = editText.editTextSelectionEnd
        text.delete(start, end)
        editText.setSelection(if (end <= selectionEnd) selectionEnd - (end - start) else selectionEnd)
    }

    private fun animateChangesInNextRows(span: AnimatedEmojiSpan) {
        val text = editText.text ?: return
        val layout = editText.layout ?: return
        val nextLine = layout.getLineForOffset(text.getSpanStart(span)) + 1
        if (nextLine >= layout.lineCount) return

        text.getSpans(layout.getLineStart(nextLine), text.length, AnimatedEmojiSpan::class.java).forEach { it.setAnimateChanges() }
    }

    private fun toggleStarsEnabled() {
        if (starEnabled) disableStars() else enableStars()
    }

    private fun enableStars(withToast: Boolean = true) {
        val paidCell = paidCheckCell ?: return

        paidCell.isChecked = true

        val span = createStarSpan().apply {
            cacheType = AnimatedEmojiDrawable.getCacheTypeForEnterView()
            setAdded()
        }
        selectedEmojisIds.add(0, STAR_ID)
        selectedEmojisMap[STAR_ID] = span
        insertSpan(span, 0)
        selectAnimatedEmojiDialog?.setMultiSelected(STAR_ID, true)
        animateChangesInNextRows(span)

        setCheckedEnableReactionCell(selectedType, paid = true, animated = true)
        checkMaxCustomReactions(withToast)
        editText.updateAnimatedEmoji(true)
    }

    private fun disableStars() {
        selectedEmojisIds.remove(STAR_ID)
        selectedEmojisMap.remove(STAR_ID)?.let {
            removeSpanAnimated(it)
            animateChangesInNextRows(it)
        }
        selectAnimatedEmojiDialog?.setMultiSelected(STAR_ID, true)
        onStarRemoved()
        checkMaxCustomReactions(false)
        editText.updateAnimatedEmoji(true)
    }

    private fun onStarRemoved() {
        paidCheckCell?.isChecked = false

        if (selectedType == SELECT_TYPE_NONE) {
            setCheckedEnableReactionCell(SELECT_TYPE_NONE, paid = false, animated = true)
        } else {
            paid = false
        }
    }

    private fun createStarSpan() = object : AnimatedEmojiSpan(STAR_ID, null) {
        private val iconSize = dp(24f)
        private val bitmap = createBitmap(iconSize, iconSize).also {
            ContextCompat.getDrawable(context, R.drawable.star_small_inner)?.mutate()?.apply {
                setBounds(0, 0, iconSize, iconSize)
                draw(Canvas(it))
            }
        }

        override fun draw(canvas: Canvas, charSequence: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            super.draw(canvas, charSequence, start, end, x, top, y, bottom, paint)
            canvas.withSave {
                translate(x, (top + bottom) / 2f - iconSize / 2f)
                scale(extraScale, extraScale, x + iconSize / 2f, iconSize / 2f)
                drawBitmap(bitmap, 0f, 0f, null)
            }
        }

        override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?) =
            super.getSize(paint, text, start, end, fm) + dp(5f)
    }

    private fun setCheckedEnableReactionCell(selectType: Int, paid: Boolean, animated: Boolean) {
        if (selectedType == selectType && this.paid == paid) return

        val wasChecked = selectedType != SELECT_TYPE_NONE || this.paid
        this.paid = paid
        selectedType = selectType

        val checked = selectType != SELECT_TYPE_NONE || paid
        if (animated) checkMaxCustomReactions(false)
        if (animated && checked == wasChecked) return

        val color = Theme.getColor(if (checked) Theme.key_windowBackgroundChecked else Theme.key_windowBackgroundUnchecked)
        enableReactionsCell.isChecked = checked
        when {
            !animated -> enableReactionsCell.setBackgroundColor(color)
            checked -> enableReactionsCell.setBackgroundColorAnimated(true, color)
            else -> enableReactionsCell.setBackgroundColorAnimatedReverse(color)
        }

        if (checked) showReactionsSettings(animated) else hideReactionsSettings(animated)
    }

    private fun showReactionsSettings(animated: Boolean) {
        switchLayout.visibility = View.VISIBLE
        if (!animated) return

        switchLayout.animate().setListener(null).cancel()
        switchLayout.animate().alpha(1f).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT)
            .withEndAction { editText.isFocusableInTouchMode = true }
            .start()

        if (selectedEmojisMap.isNotEmpty()) return

        selectAnimatedEmojiDialog?.clearSelectedDocuments()
        editText.setText("")
        appendDefaultReactions()
        editText.addReactionsSpan()
        selectAnimatedEmojiDialog?.notifyDataSetChanged()
        checkMaxCustomReactions(false)
    }

    private fun hideReactionsSettings(animated: Boolean) {
        if (!animated) {
            switchLayout.visibility = View.INVISIBLE
            return
        }

        closeKeyboard()
        switchLayout.animate().setListener(null).cancel()
        switchLayout.animate().alpha(0f).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT)
            .withEndAction {
                editText.isFocusableInTouchMode = false
                switchLayout.visibility = View.INVISIBLE
            }
            .start()
    }

    private val hasChanges
        get() = selectedEmojisMap.keys != initialSelectedIds
            || initialPaid != paid
            || (selectedType == SELECT_TYPE_NONE) != (initialType == SELECT_TYPE_NONE)
            || reactionsCount != initialReactionsCount

    private val requestType: Int
        get() {
            val selectedIds = selectedEmojisMap.keys - STAR_ID
            if (selectedType == SELECT_TYPE_NONE || selectedIds.isEmpty()) return SELECT_TYPE_NONE

            val keepsAll = initialType == SELECT_TYPE_ALL && selectedIds == initialSelectedIds - STAR_ID
            val selectsAll = selectedIds == allAvailableReactions.mapTo(HashSet()) { it.activate_animation.id }
            return if (keepsAll || selectsAll) SELECT_TYPE_ALL else SELECT_TYPE_SOME
        }

    private fun updateActionButton(animated: Boolean = true) {
        val show = hasChanges
        if (show == actionButtonShown) return

        actionButtonShown = show
        actionButtonContainer.animate().setListener(null).cancel()
        if (!animated) {
            actionButtonContainer.alpha = if (show) 1f else 0f
            actionButtonContainer.visibility = if (show) View.VISIBLE else View.INVISIBLE
            return
        }

        actionButtonContainer.visibility = View.VISIBLE
        actionButtonContainer.animate().alpha(if (show) 1f else 0f).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT)
            .withEndAction { if (!show) actionButtonContainer.visibility = View.INVISIBLE }
            .start()
    }

    private fun checkChangesBeforeExit(invoked: Boolean): Boolean {
        if (!hasChanges) return false
        if (!invoked) return true

        AlertDialog.Builder(context, resourceProvider)
            .setTitle(getString(R.string.UnsavedChanges))
            .setMessage(getString(R.string.ReactionApplyChangesDialog))
            .setPositiveButton(getString(R.string.ApplyTheme)) { _, _ -> actionButton.performClick() }
            .setNegativeButton(getString(R.string.Discard)) { _, _ -> finishFragment() }
            .show()
        return true
    }

    private fun checkMaxCustomReactions(withToast: Boolean) {
        updateActionButton()

        val status = boostsStatus ?: return
        if (selectedType == SELECT_TYPE_NONE) {
            actionButton.removeLvlRequiredState()
            return
        }

        selectedCustomReactions = grabReactions(true).size
        if (status.level >= selectedCustomReactions) {
            actionButton.removeLvlRequiredState()
            return
        }

        if (withToast) {
            val text = replaceTags(formatPluralString("ReactionReachLvlForReactionShort", selectedCustomReactions, selectedCustomReactions))
            BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, text).show()
        }
        actionButton.setLvlRequiredState(selectedCustomReactions)
    }

    private fun grabReactions(onlyCustom: Boolean): List<TLRPC.Reaction> =
        selectedEmojisIds
            .filter { it != STAR_ID }
            .mapNotNull { id ->
                val available = helper.findAvailableReaction(id)
                when {
                    available == null -> TLRPC.TL_reactionCustomEmoji().apply { document_id = id }
                    onlyCustom -> null
                    else -> TLRPC.TL_reactionEmoji().apply { emoticon = available.reaction }
                }
            }

    private fun showKeyboard() {
        if (emojiKeyboardVisible) return

        emojiKeyboardVisible = true
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512)

        val height = bottomDialogLayout.measuredHeight
        updateScrollViewMarginBottom(height)
        bottomDialogLayout.visibility = View.VISIBLE
        bottomDialogLayout.translationY = height.toFloat()
        bottomDialogLayout.animate().setListener(null).cancel()
        bottomDialogLayout.animate().translationY(0f).withLayer().setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT)
            .setUpdateListener { actionButtonContainer.translationY = -(it.animatedValue as Float) * bottomDialogLayout.measuredHeight }
            .withEndAction { NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512) }
            .start()
    }

    private fun closeKeyboard() {
        if (!emojiKeyboardVisible) return

        emojiKeyboardVisible = false
        if (isClearFocusNotWorking) {
            switchLayout.isFocusableInTouchMode = true
            switchLayout.requestFocus()
        } else {
            editText.clearFocus()
        }

        updateScrollViewMarginBottom(0)
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512)
        bottomDialogLayout.animate().setListener(null).cancel()
        bottomDialogLayout.animate().translationY(bottomDialogLayout.measuredHeight.toFloat()).setDuration(350).withLayer().setInterpolator(CubicBezierInterpolator.DEFAULT)
            .setUpdateListener { actionButtonContainer.translationY = -(1f - it.animatedValue as Float) * bottomDialogLayout.measuredHeight }
            .withEndAction {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512)
                bottomDialogLayout.visibility = View.INVISIBLE
                if (isClearFocusNotWorking) switchLayout.isFocusableInTouchMode = false
            }
            .start()
    }

    private val isClearFocusNotWorking
        get() = Build.MODEL.lowercase().startsWith("zte") && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P

    private fun updateScrollViewMarginBottom(margin: Int) {
        scrollView.layoutParams = (scrollView.layoutParams as ViewGroup.MarginLayoutParams).apply { bottomMargin = margin }
    }

    override fun canBeginSlide() = !checkChangesBeforeExit(true) && super.canBeginSlide()

    override fun onTransitionAnimationEnd(isOpen: Boolean, backward: Boolean) {
        super.onTransitionAnimationEnd(isOpen, backward)
        if (isOpen && selectedType != SELECT_TYPE_NONE) editText.isFocusableInTouchMode = true
        if (!isOpen || backward) return

        initSelectAnimatedEmojiDialog()
        AndroidUtilities.runOnUIThread({
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512)
        }, 200)
    }

    override fun onResume() {
        super.onResume()
        if (!paused) return

        paused = false
        editText.isFocusable = true
        editText.isFocusableInTouchMode = true
        if (!emojiKeyboardVisible) return

        editText.removeReactionsSpan(false)
        AndroidUtilities.runOnUIThread({ editText.requestFocus() }, 250)
    }

    override fun onPause() {
        paused = true
        editText.isFocusable = false
        super.onPause()
    }

    override fun onBackPressed(invoked: Boolean): Boolean {
        if (emojiKeyboardVisible) {
            if (invoked) closeKeyboard()
            return false
        }

        return !checkChangesBeforeExit(invoked) && super.onBackPressed(invoked)
    }

    override fun onFragmentDestroy() {
        super.onFragmentDestroy()
        AndroidUtilities.cancelRunOnUIThread(checkAfterFastDeleteRunnable)
        observers?.removeAllObservers()
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (id != NotificationCenter.dialogDeleted || args[0] as Long != -chatId) return

        if (parentLayout?.lastFragment === this) finishFragment() else removeSelfFromStack()
    }
}