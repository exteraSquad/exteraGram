/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.catalog.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.exteragram.messenger.badges.BadgesController
import com.exteragram.messenger.getAvatarCorners
import com.exteragram.messenger.plugins.catalog.db.PluginRepositoryEntity
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.Emoji
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Components.UniversalRecyclerView

@SuppressLint("ViewConstructor")
class CatalogRepositoryCell(
    context: Context,
    private val currentAccount: Int,
    private val resourcesProvider: Theme.ResourcesProvider?,
) : FrameLayout(context), NotificationCenter.NotificationCenterDelegate {
    private val avatarDrawable = AvatarDrawable()
    private val avatarView = BackupImageView(context)
    private val titleView = SimpleTextView(context).apply {
        setTextSize(16)
        setTypeface(AndroidUtilities.bold())
        setGravity(if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT)
        setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider))
        setDrawablePadding(dp(4f))
    }
    private val subtitleView = SimpleTextView(context).apply {
        setTextSize(14)
        setGravity(if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT)
        setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider))
    }
    private val badge = AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(titleView, dp(20f))
    private var needDivider = false

    init {
        setWillNotDraw(false)
        val gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP
        val padding = CatalogPluginCell.PADDING
        val textStart = CatalogPluginCell.TEXT_START
        addView(avatarView, LayoutHelper.createFrame(AVATAR_SIZE.toInt(), AVATAR_SIZE, gravity, if (LocaleController.isRTL) 0f else padding, padding, if (LocaleController.isRTL) padding else 0f, 0f))
        addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20f, gravity, if (LocaleController.isRTL) padding else textStart, padding, if (LocaleController.isRTL) textStart else padding, 0f))
        addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 20f, gravity, if (LocaleController.isRTL) padding else textStart, padding + AVATAR_SIZE - 20f, if (LocaleController.isRTL) textStart else padding, 0f))
    }

    fun set(repository: PluginRepositoryEntity, subtitle: CharSequence, divider: Boolean) {
        val chat = MessagesController.getInstance(currentAccount).getChat(repository.repositoryId)
        avatarView.setRoundRadius(getAvatarCorners(AVATAR_SIZE))
        if (chat != null) {
            avatarDrawable.setInfo(currentAccount, chat)
            avatarView.setForUserOrChat(chat, avatarDrawable)
        } else {
            avatarDrawable.setInfo(repository.repositoryId, repository.title, null)
            avatarView.setImageDrawable(avatarDrawable)
        }
        titleView.setText(Emoji.replaceEmoji(chat?.title ?: repository.title ?: repository.username ?: "", titleView.paint.fontMetricsInt, false))
        val badgeDocumentId = if (chat != null) {
            BadgesController.getBadge(chat)?.documentId
        } else {
            BadgesController.TRUSTED_BADGE.documentId.takeIf { BadgesController.isTrusted(repository.repositoryId) }
        }
        if (badgeDocumentId != null) {
            badge.set(badgeDocumentId, false)
            badge.color = Theme.getColor(Theme.key_chats_verifiedBackground, resourcesProvider)
            titleView.setRightDrawable(badge)
        } else {
            badge.set(null as TLRPC.Document?, false)
            titleView.setRightDrawable(null)
        }
        subtitleView.setText(subtitle)
        needDivider = divider
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(AVATAR_SIZE + CatalogPluginCell.PADDING * 2) + if (needDivider) 1 else 0, MeasureSpec.EXACTLY))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        badge.attach()
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        badge.detach()
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded)
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        if (id == NotificationCenter.emojiLoaded) {
            titleView.invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (needDivider) {
            val left = if (LocaleController.isRTL) 0f else dp(CatalogPluginCell.TEXT_START).toFloat()
            val right = measuredWidth - if (LocaleController.isRTL) dp(CatalogPluginCell.TEXT_START).toFloat() else 0f
            canvas.drawLine(left, measuredHeight - 1f, right, measuredHeight - 1f, Theme.getThemePaint(Theme.key_paint_divider, resourcesProvider))
        }
    }

    companion object {
        private const val AVATAR_SIZE = CatalogPluginCell.ICON_SIZE
    }

    class Factory : UItem.UItemFactory<CatalogRepositoryCell>() {
        override fun createView(
            context: Context,
            listView: RecyclerListView,
            currentAccount: Int,
            classGuid: Int,
            resourcesProvider: Theme.ResourcesProvider?,
        ): CatalogRepositoryCell = CatalogRepositoryCell(context, currentAccount, resourcesProvider)

        override fun bindView(
            view: View,
            item: UItem,
            divider: Boolean,
            adapter: UniversalAdapter,
            listView: UniversalRecyclerView,
        ) {
            val repository = item.`object` as? PluginRepositoryEntity ?: return
            (view as? CatalogRepositoryCell)?.set(repository, item.subtext ?: "", divider)
        }

        override fun equals(a: UItem, b: UItem): Boolean = a.id == b.id

        override fun contentsEquals(a: UItem, b: UItem): Boolean =
            a.`object` == b.`object` && a.subtext?.toString() == b.subtext?.toString()

        companion object {
            init {
                setup(Factory())
            }

            fun of(repository: PluginRepositoryEntity, subtitle: CharSequence): UItem =
                UItem.ofFactory(Factory::class.java).apply {
                    id = repository.repositoryId.hashCode()
                    `object` = repository
                    subtext = subtitle
                }
        }
    }
}
