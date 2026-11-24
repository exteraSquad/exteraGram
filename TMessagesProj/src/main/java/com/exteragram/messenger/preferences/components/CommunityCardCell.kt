/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.preferences.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.exteragram.messenger.AvatarCornerType
import com.exteragram.messenger.getAvatarCorners
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.utils.DrawableUtils
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Components.UniversalRecyclerView

@SuppressLint("ViewConstructor")
class CommunityCardCell(
    context: Context,
    private val resourcesProvider: Theme.ResourcesProvider?,
) : FrameLayout(context), Theme.Colorable {

    private val avatarView = BackupImageView(context).apply {
        setRoundRadius(getAvatarCorners(AVATAR_SIZE.toFloat(), false, AvatarCornerType.COMMUNITY, allowSingleRadius = false))
    }
    private val titleView = TextView(context).apply {
        setTypeface(AndroidUtilities.regular())
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
        setSingleLine(true)
        ellipsize = TextUtils.TruncateAt.END
    }
    private val subtitleView = TextView(context).apply {
        setTypeface(AndroidUtilities.regular())
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
        setSingleLine(true)
        ellipsize = TextUtils.TruncateAt.END
    }
    private val arrowView = ImageView(context).apply {
        setImageResource(R.drawable.msg_inputarrow)
        scaleType = ImageView.ScaleType.CENTER
    }
    private var needDivider = false

    init {
        val rtl = LocaleController.isRTL
        val start = if (rtl) Gravity.RIGHT else Gravity.LEFT
        val end = if (rtl) Gravity.LEFT else Gravity.RIGHT

        addView(
            avatarView,
            LayoutHelper.createFrame(AVATAR_SIZE, AVATAR_SIZE.toFloat(), start or Gravity.CENTER_VERTICAL, if (rtl) 0f else 18f, 0f, if (rtl) 18f else 0f, 0f)
        )

        titleView.gravity = start
        subtitleView.gravity = start
        val textBlock = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
            addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 2f, 0f, 0f))
        }
        addView(
            textBlock,
            LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), start or Gravity.CENTER_VERTICAL,
                if (rtl) 48f else 71f, 0f, if (rtl) 71f else 48f, 0f
            )
        )

        if (rtl) arrowView.scaleX = -1f
        addView(arrowView, LayoutHelper.createFrame(24, 24f, end or Gravity.CENTER_VERTICAL, if (rtl) 16f else 0f, 0f, if (rtl) 0f else 16f, 0f))

        updateColors()
    }

    fun set(chat: TLRPC.Chat?, title: CharSequence?, subtitle: CharSequence?, divider: Boolean) {
        titleView.text = title
        subtitleView.text = subtitle
        if (chat != null) {
            avatarView.setForUserOrChat(chat, AvatarDrawable(chat))
        } else {
            avatarView.setImageDrawable(AvatarDrawable().apply { setInfo(0L, title?.toString(), null) })
        }
        if (needDivider != divider) {
            needDivider = divider
            requestLayout()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(60f) + if (needDivider) 1 else 0, MeasureSpec.EXACTLY))
    }

    override fun dispatchDraw(canvas: Canvas) {
        Theme.dialogs_communityCardsDrawable?.let {
            DrawableUtils.drawCommunityCardDrawable(
                canvas, it,
                avatarView.left + avatarView.width / 2f,
                avatarView.top + avatarView.height / 2f,
                avatarView.height.toFloat()
            )
        }
        super.dispatchDraw(canvas)
        if (needDivider) {
            val paint = resourcesProvider?.getPaint(Theme.key_paint_divider) ?: Theme.dividerPaint
            val inset = dp(68f).toFloat()
            val y = measuredHeight - 1f
            if (LocaleController.isRTL) {
                canvas.drawLine(0f, y, measuredWidth - inset, y, paint)
            } else {
                canvas.drawLine(inset, y, measuredWidth.toFloat(), y, paint)
            }
        }
    }

    override fun updateColors() {
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider))
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider))
        arrowView.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider))
    }

    class Factory : UItem.UItemFactory<CommunityCardCell>() {

        override fun createView(
            context: Context,
            listView: RecyclerListView?,
            currentAccount: Int,
            classGuid: Int,
            resourcesProvider: Theme.ResourcesProvider?,
        ): CommunityCardCell = CommunityCardCell(context, resourcesProvider)

        override fun bindView(
            view: View,
            item: UItem,
            divider: Boolean,
            adapter: UniversalAdapter?,
            listView: UniversalRecyclerView?,
        ) {
            (view as CommunityCardCell).set(item.`object` as? TLRPC.Chat, item.text, item.subtext, divider)
        }

        override fun equals(a: UItem, b: UItem): Boolean = a.id == b.id

        override fun contentsEquals(a: UItem, b: UItem): Boolean =
            a.id == b.id && a.`object` === b.`object` && TextUtils.equals(a.text, b.text) && TextUtils.equals(a.subtext, b.subtext)

        companion object {
            init {
                setup(Factory())
            }

            @JvmStatic
            fun of(id: Int, chat: TLRPC.Chat?, title: CharSequence, subtitle: CharSequence?): UItem =
                UItem.ofFactory(Factory::class.java).apply {
                    this.id = id
                    text = title
                    subtext = subtitle
                    `object` = chat
                }
        }
    }

    private companion object {
        const val AVATAR_SIZE = 30
    }
}
