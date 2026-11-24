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
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.exteragram.messenger.getSectionRadiusDp
import com.exteragram.messenger.plugins.catalog.CatalogPlugin
import com.exteragram.messenger.utils.text.LocaleUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.R
import org.telegram.messenger.utils.ViewOutlineProviderImpl
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LinkSpanDrawable
import org.telegram.ui.Components.RadialProgressView
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Components.UniversalRecyclerView
import kotlin.math.max
import androidx.core.view.isGone

@SuppressLint("ViewConstructor")
class CatalogPluginCell(
    context: Context,
    private val currentAccount: Int,
    private val resourcesProvider: Theme.ResourcesProvider?,
) : FrameLayout(context) {
    private var iconRadius = 0
    private val iconView = BackupImageView(context).apply {
        clipToOutline = true
        imageReceiver.setAutoRepeat(1)
    }
    private val placeholderView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        setImageResource(R.drawable.plugins_filled)
        colorFilter = PorterDuffColorFilter(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider), PorterDuff.Mode.SRC_IN)
        val padding = dp(10f)
        setPadding(padding, padding, padding, padding)
    }
    private val titleView = createTextView(context, 16f, Theme.key_windowBackgroundWhiteBlackText).apply {
        typeface = AndroidUtilities.bold()
        includeFontPadding = false
        gravity = gravity or Gravity.CENTER_VERTICAL
    }
    private val versionView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
        typeface = AndroidUtilities.bold()
        isSingleLine = true
        gravity = Gravity.CENTER
        setPadding(dp(6f), 0, dp(6f), 0)
    }
    private val newView = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
        typeface = AndroidUtilities.bold()
        isSingleLine = true
        gravity = Gravity.CENTER
        setPadding(dp(6f), 0, dp(6f), 0)
        text = LocaleController.getString(R.string.PluginCatalogNew)
        visibility = GONE
    }
    private val titleRow = TitleRow(context)
    private val descriptionView = LinksTextView(context).apply {
        setup(14f, Theme.key_windowBackgroundWhiteBlackText)
        setLineSpacing((dp(DESCRIPTION_LINE_HEIGHT) - paint.getFontMetricsInt(null)).toFloat(), 1f)
        includeFontPadding = false
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
    }
    private val authorView = LinksTextView(context).apply {
        setup(13f, Theme.key_windowBackgroundWhiteGrayText)
        isSingleLine = true
        includeFontPadding = false
        gravity = gravity or Gravity.CENTER_VERTICAL
        ellipsize = TextUtils.TruncateAt.END
    }
    private val tagsView = CatalogCategoryTagsView(context, resourcesProvider)
    private val textColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(titleRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
        addView(descriptionView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 4f, 0f, 0f))
        addView(authorView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, AUTHOR_HEIGHT.toInt(), 0f, 7f, 0f, 0f))
        addView(tagsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, CatalogCategoryTagsView.HEIGHT.toInt(), 0f, 9f, 0f, 0f))
    }
    private val deleteButton = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER
        setImageResource(R.drawable.msg_delete)
        colorFilter = PorterDuffColorFilter(Theme.getColor(Theme.key_text_RedRegular, resourcesProvider), PorterDuff.Mode.SRC_IN)
        background = Theme.createSelectorDrawable(Theme.multAlpha(Theme.getColor(Theme.key_text_RedRegular, resourcesProvider), 0.12f), 1, dp(20f))
        contentDescription = LocaleController.getString(R.string.PluginDelete)
        visibility = GONE
    }
    private val progressView = RadialProgressView(context, resourcesProvider).apply {
        setSize(dp(20f))
        setProgressColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider))
        visibility = GONE
    }
    private var needDivider = false
    private var boundEntryId: Int? = null

    init {
        setWillNotDraw(false)
        val isRTL = LocaleController.isRTL
        val start = if (isRTL) Gravity.RIGHT else Gravity.LEFT
        addView(placeholderView, LayoutHelper.createFrame(ICON_SIZE.toInt(), ICON_SIZE, start or Gravity.TOP, if (isRTL) 0f else PADDING, PADDING, if (isRTL) PADDING else 0f, 0f))
        addView(iconView, LayoutHelper.createFrame(ICON_SIZE.toInt(), ICON_SIZE, start or Gravity.TOP, if (isRTL) 0f else PADDING, PADDING, if (isRTL) PADDING else 0f, 0f))
        addView(textColumn, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), start or Gravity.TOP, if (isRTL) PADDING else TEXT_START, PADDING, if (isRTL) TEXT_START else PADDING, PADDING))
        addView(deleteButton, LayoutHelper.createFrameRelatively(40f, 40f, Gravity.CENTER_VERTICAL or Gravity.END, 0f, 0f, PADDING, 0f))
        addView(progressView, LayoutHelper.createFrameRelatively(40f, 40f, Gravity.CENTER_VERTICAL or Gravity.END, 0f, 0f, PADDING, 0f))
    }

    private fun createTextView(context: Context, size: Float, colorKey: Int) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, size)
        setTextColor(Theme.getColor(colorKey, resourcesProvider))
        typeface = AndroidUtilities.regular()
        isSingleLine = true
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT
    }

    private fun TextView.setup(size: Float, colorKey: Int) {
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, size)
        setTextColor(Theme.getColor(colorKey, resourcesProvider))
        setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, resourcesProvider))
        typeface = AndroidUtilities.regular()
        gravity = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT
    }

    fun set(
        plugin: CatalogPlugin,
        state: Int,
        versionLabel: CharSequence?,
        isNew: Boolean,
        loading: Boolean,
        detailsAvailable: Boolean,
        onDelete: OnClickListener?,
        divider: Boolean,
    ) {
        updateIconRadius()
        val metadata = plugin.metadata?.takeIf { it.error == null }
        val icon = metadata?.icon?.takeIf { ICON_PATTERN.matches(it) }
        if (icon != null) {
            loadIcon(icon)
        } else {
            showPlaceholder()
        }

        titleView.text = metadata?.name ?: plugin.fileName.substringBeforeLast('.')
        versionView.text = versionLabel
        versionView.visibility = if (versionLabel.isNullOrEmpty()) GONE else VISIBLE
        val versionColor = Theme.getColor(
            if (state == STATE_UPDATE) Theme.key_featuredStickers_addButton else Theme.key_windowBackgroundWhiteGrayText,
            resourcesProvider,
        )
        versionView.setTextColor(versionColor)
        versionView.background = Theme.createRoundRectDrawable(dp(8f), Theme.multAlpha(versionColor, 0.12f))
        newView.visibility = if (isNew) VISIBLE else GONE
        if (isNew) {
            newView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider))
            newView.background = Theme.createRoundRectDrawable(dp(8f), Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider))
        }

        descriptionView.text = when {
            metadata != null -> LocaleUtils.fullyFormatText(
                metadata.description?.take(MAX_DESCRIPTION_LENGTH)?.dropLastWhile { it.isHighSurrogate() }
                    ?: LocaleController.getString(R.string.PluginNoDescription)
            )
            plugin.isFailed -> LocaleController.getString(R.string.PluginCatalogInfoFailed)
            !detailsAvailable -> LocaleController.getString(R.string.PluginCatalogInfoUnavailable)
            else -> LocaleController.getString(R.string.PluginCatalogInfoLoading)
        }
        val categories = plugin.categories
        tagsView.setCategories(categories)
        tagsView.visibility = if (categories.isEmpty()) GONE else VISIBLE
        authorView.text = if (metadata != null) {
            LocaleUtils.formatWithUsernames(metadata.author ?: LocaleController.getString(R.string.PluginNoAuthor))
        } else {
            AndroidUtilities.formatFileSize(plugin.size)
        }

        val animated = boundEntryId == plugin.entryId
        boundEntryId = plugin.entryId
        val showDelete = !loading && state != STATE_GET && onDelete != null
        AndroidUtilities.updateViewVisibilityAnimated(deleteButton, showDelete, 0.5f, true, animated)
        deleteButton.setOnClickListener(onDelete)
        AndroidUtilities.updateViewVisibilityAnimated(progressView, loading, 0.5f, true, animated)
        val endMargin = dp(if (showDelete || loading) 56f else PADDING)
        (textColumn.layoutParams as LayoutParams).apply {
            if (LocaleController.isRTL) {
                if (leftMargin != endMargin) {
                    leftMargin = endMargin
                    textColumn.requestLayout()
                }
            } else if (rightMargin != endMargin) {
                rightMargin = endMargin
                textColumn.requestLayout()
            }
        }

        needDivider = divider
        invalidate()
    }

    private fun updateIconRadius() {
        val radius = dp(getIconRadius())
        if (iconRadius == radius) {
            return
        }
        iconRadius = radius
        iconView.setRoundRadius(radius)
        iconView.outlineProvider = ViewOutlineProviderImpl.boundsWithRoundRect(radius.toFloat())
        iconView.invalidateOutline()
        placeholderView.background = Theme.createRoundRectDrawable(radius, Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider))
    }

    private fun loadIcon(icon: String) {
        iconView.visibility = VISIBLE
        placeholderView.visibility = GONE
        val separator = icon.lastIndexOf('/')
        val setName = icon.substring(0, separator)
        val index = icon.substring(separator + 1).toInt()
        val mediaDataController = MediaDataController.getInstance(currentAccount)
        mediaDataController.setPlaceholderImageByIndex(iconView, setName, index, "100_100", resourcesProvider)
        val tag = iconView.tag
        val inputStickerSet = TLRPC.TL_inputStickerSetShortName().apply { short_name = setName }
        mediaDataController.getStickerSet(inputStickerSet, 0, false) { set ->
            if (iconView.tag == tag && set?.documents?.getOrNull(index) == null) {
                showPlaceholder()
            }
        }
    }

    private fun showPlaceholder() {
        iconView.tag = null
        iconView.setImageDrawable(null)
        iconView.visibility = GONE
        placeholderView.visibility = VISIBLE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        super.onMeasure(widthMeasureSpec, unspecified)
        if (alignDescription()) {
            super.onMeasure(widthMeasureSpec, unspecified)
        }
        val height = max(measuredHeight, dp(ICON_SIZE + PADDING * 2)) + if (needDivider) 1 else 0
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }

    private fun alignDescription(): Boolean {
        val layout = descriptionView.layout ?: return false
        if (descriptionView.isGone || layout.lineCount == 0) {
            return false
        }
        val firstLineBottom = layout.getLineBaseline(0) + descriptionView.paint.fontMetricsInt.descent
        val margin = max(dp(4f), dp(ICON_SIZE) - titleRow.measuredHeight - firstLineBottom)
        val params = descriptionView.layoutParams as LinearLayout.LayoutParams
        if (params.topMargin == margin) {
            return false
        }
        params.topMargin = margin
        return true
    }

    override fun onDraw(canvas: Canvas) {
        if (needDivider) {
            val left = if (LocaleController.isRTL) 0f else dp(TEXT_START).toFloat()
            val right = measuredWidth - if (LocaleController.isRTL) dp(TEXT_START).toFloat() else 0f
            canvas.drawLine(left, measuredHeight - 1f, right, measuredHeight - 1f, Theme.getThemePaint(Theme.key_paint_divider, resourcesProvider))
        }
    }

    private inner class LinksTextView(context: Context) : LinkSpanDrawable.LinksTextView(context, resourcesProvider) {
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN && hit(event.x.toInt(), event.y.toInt()) != null) {
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            return super.onTouchEvent(event)
        }
    }

    private inner class TitleRow(context: Context) : FrameLayout(context) {
        private val badges = arrayOf(versionView, newView)

        init {
            addView(titleView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            addView(versionView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            addView(newView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            var badgeWidth = 0
            for (badge in badges) {
                if (badge.visibility != GONE) {
                    badge.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(dp(TITLE_HEIGHT), MeasureSpec.EXACTLY))
                    badgeWidth += badge.measuredWidth + dp(if (badgeWidth == 0) TITLE_BADGE_GAP else BADGE_GAP)
                }
            }
            titleView.measure(
                MeasureSpec.makeMeasureSpec(max(0, width - badgeWidth), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(dp(TITLE_HEIGHT), MeasureSpec.EXACTLY),
            )
            setMeasuredDimension(width, dp(TITLE_HEIGHT))
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            val height = bottom - top
            val titleTop = (height - titleView.measuredHeight) / 2
            val width = right - left
            if (LocaleController.isRTL) {
                titleView.layout(width - titleView.measuredWidth, titleTop, width, titleTop + titleView.measuredHeight)
            } else {
                titleView.layout(0, titleTop, titleView.measuredWidth, titleTop + titleView.measuredHeight)
            }
            var offset = titleView.measuredWidth + dp(TITLE_BADGE_GAP)
            for (badge in badges) {
                if (badge.isGone) {
                    continue
                }
                val badgeTop = (height - badge.measuredHeight) / 2
                if (LocaleController.isRTL) {
                    badge.layout(width - offset - badge.measuredWidth, badgeTop, width - offset, badgeTop + badge.measuredHeight)
                } else {
                    badge.layout(offset, badgeTop, offset + badge.measuredWidth, badgeTop + badge.measuredHeight)
                }
                offset += badge.measuredWidth + dp(BADGE_GAP)
            }
        }

    }

    class Factory : UItem.UItemFactory<CatalogPluginCell>() {
        override fun createView(
            context: Context,
            listView: RecyclerListView,
            currentAccount: Int,
            classGuid: Int,
            resourcesProvider: Theme.ResourcesProvider?,
        ): CatalogPluginCell = CatalogPluginCell(context, currentAccount, resourcesProvider)

        override fun bindView(
            view: View,
            item: UItem,
            divider: Boolean,
            adapter: UniversalAdapter,
            listView: UniversalRecyclerView,
        ) {
            val plugin = item.`object` as? CatalogPlugin ?: return
            (view as? CatalogPluginCell)?.set(plugin, item.intValue, item.text, item.accent, item.checked, !item.locked, item.clickCallback, divider)
        }

        override fun equals(a: UItem, b: UItem): Boolean = a.id == b.id

        override fun contentsEquals(a: UItem, b: UItem): Boolean {
            val pluginA = a.`object` as? CatalogPlugin ?: return false
            val pluginB = b.`object` as? CatalogPlugin ?: return false
            return pluginA.documentId == pluginB.documentId &&
                pluginA.metadata == pluginB.metadata &&
                a.intValue == b.intValue &&
                TextUtils.equals(a.text, b.text) &&
                a.checked == b.checked &&
                a.accent == b.accent &&
                a.locked == b.locked
        }

        companion object {
            init {
                setup(Factory())
            }

            fun of(
                plugin: CatalogPlugin,
                state: Int,
                versionLabel: CharSequence?,
                isNew: Boolean,
                loading: Boolean,
                detailsAvailable: Boolean,
                onDelete: OnClickListener?,
            ): UItem = UItem.ofFactory(Factory::class.java).apply {
                id = (plugin.repositoryId * 31 + plugin.entryId).hashCode()
                `object` = plugin
                intValue = state
                text = versionLabel
                accent = isNew
                checked = loading
                locked = !detailsAvailable
                clickCallback = onDelete
            }
        }
    }

    companion object {
        const val STATE_GET = 0
        const val STATE_UPDATE = 1
        const val STATE_INSTALLED = 2

        private const val TITLE_BADGE_GAP = 8f
        private const val BADGE_GAP = 4f
        private const val TITLE_HEIGHT = 20f
        private const val AUTHOR_HEIGHT = 16f
        private const val DESCRIPTION_LINE_HEIGHT = 18.5f
        private const val MAX_DESCRIPTION_LENGTH = 512
        const val PADDING = 12f
        const val ICON_SIZE = 44f
        const val TEXT_START = PADDING + ICON_SIZE + PADDING
        private val ICON_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9_]*/\\d{1,9}$")

        fun getIconRadius(): Float = max(getSectionRadiusDp() - PADDING, 8f)
    }
}
