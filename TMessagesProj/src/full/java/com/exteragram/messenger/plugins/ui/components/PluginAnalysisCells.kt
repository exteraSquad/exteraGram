/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.ui.components

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ReplacementSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.exteragram.messenger.getSectionRadiusDp
import com.exteragram.messenger.plugins.analyzer.AnalyzerReport
import com.exteragram.messenger.utils.ui.UIUtil
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RadialProgressView
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Components.UniversalRecyclerView
import org.telegram.ui.Stories.recorder.ButtonWithCounterView
import kotlin.math.roundToInt

private const val CARD_PADDING = 12f
private const val ICON_SIZE = 40f
private const val GLYPH_SIZE = 20
private const val TEXT_GAP = 4f
private const val ARROW_BOX = 42f
private const val ARROW_INSET = (CARD_PADDING * 2 + ICON_SIZE - ARROW_BOX) / 2
private const val TEXT_START = CARD_PADDING * 2 + ICON_SIZE
private const val CHIP_HEIGHT = 32f
private const val BLOCK_GAP = 8f
private const val LINE_INSET_X = 6f
private const val LINE_INSET_Y = 4f
private const val BADGE_SIZE = 12f
private const val MIN_CONTRAST = 3.0
private const val BOTTOM_FADE = 16f

private const val TITLE_SIZE = 16f
private const val TITLE_LINE = 20f
private const val SUBTITLE_SIZE = 13f
private const val SUBTITLE_LINE = 17f
private const val CODE_SIZE = 13f
private const val CODE_LINE = 18f

internal fun analysisSeverityColor(severity: Int, resourcesProvider: Theme.ResourcesProvider?): Int {
    val red = Theme.getColor(Theme.key_text_RedRegular, resourcesProvider)
    val green = Theme.getColor(Theme.key_windowBackgroundWhiteGreenText, resourcesProvider)
    return when (severity) {
        AnalyzerReport.SEVERITY_CRITICAL -> red
        AnalyzerReport.SEVERITY_DANGER -> readable(blendHue(red, green, 0.25f), resourcesProvider)
        AnalyzerReport.SEVERITY_CAUTION -> readable(blendHue(red, green, 0.38f), resourcesProvider)
        else -> Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider)
    }
}

private fun readable(color: Int, resourcesProvider: Theme.ResourcesProvider?): Int {
    val background = ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), 255)
    if (ColorUtils.calculateLuminance(background) <= 0.5) {
        return color
    }
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    var result = color
    while (ColorUtils.calculateContrast(result, background) < MIN_CONTRAST && hsl[2] > 0.05f) {
        hsl[2] -= 0.02f
        result = ColorUtils.HSLToColor(hsl)
    }
    return result
}

private fun blendHue(from: Int, to: Int, ratio: Float): Int {
    val a = FloatArray(3)
    val b = FloatArray(3)
    val out = FloatArray(3)
    ColorUtils.colorToHSL(from, a)
    ColorUtils.colorToHSL(to, b)
    ColorUtils.blendHSL(a, b, ratio, out)
    return ColorUtils.HSLToColor(out)
}

internal fun ButtonWithCounterView.setDangerColors(resourcesProvider: Theme.ResourcesProvider?) {
    val danger = FloatArray(3)
    ColorUtils.colorToHSL(analysisSeverityColor(AnalyzerReport.SEVERITY_CRITICAL, resourcesProvider), danger)
    val background = withLightnessOf(danger, Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider))
    val text = withLightnessOf(danger, Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider))
    setColor(background)
    setColor(background, Theme.multAlpha(text, .15f))
    setTextColor(text)
}

private fun withLightnessOf(hsl: FloatArray, color: Int): Int {
    val source = FloatArray(3)
    ColorUtils.colorToHSL(color, source)
    return ColorUtils.setAlphaComponent(ColorUtils.HSLToColor(floatArrayOf(hsl[0], hsl[1], source[2])), Color.alpha(color))
}

internal fun analysisSafeColor(resourcesProvider: Theme.ResourcesProvider?): Int =
    Theme.getColor(Theme.key_windowBackgroundWhiteGreenText, resourcesProvider)

internal fun analysisInnerRadius(): Int = dp((getSectionRadiusDp() - CARD_PADDING).coerceAtLeast(8f))

private fun analysisLineRadiusDp(): Int = (getSectionRadiusDp() - CARD_PADDING - LINE_INSET_X).roundToInt().coerceAtLeast(4)

internal fun sheetBottomBackdrop(color: Int): Drawable = LayerDrawable(
    arrayOf(UIUtil.createBottomFade(color), ColorDrawable(color))
).apply {
    setLayerGravity(0, Gravity.TOP or Gravity.FILL_HORIZONTAL)
    setLayerHeight(0, dp(BOTTOM_FADE))
    setLayerInsetTop(1, dp(BOTTOM_FADE))
}

private fun TextView.setTextMetrics(sizeDp: Float, lineHeightDp: Float, face: Typeface = AndroidUtilities.regular()) {
    typeface = face
    setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp)
    includeFontPadding = false
    val metrics = paint.fontMetricsInt
    setLineSpacing((dp(lineHeightDp) - (metrics.descent - metrics.ascent)).coerceAtLeast(0).toFloat(), 1f)
}

private fun arrowView(context: Context, resourcesProvider: Theme.ResourcesProvider?) = ImageView(context).apply {
    scaleType = ImageView.ScaleType.FIT_CENTER
    val inset = dp((ARROW_BOX - GLYPH_SIZE) / 2)
    setPadding(inset, inset, inset, inset)
    setImageResource(R.drawable.arrow_more)
    colorFilter = PorterDuffColorFilter(
        Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider),
        PorterDuff.Mode.MULTIPLY
    )
}

private class TintedIcon(context: Context) {
    val container = FrameLayout(context)
    val icon = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }

    init {
        container.addView(icon, LayoutHelper.createFrame(GLYPH_SIZE, GLYPH_SIZE, Gravity.CENTER))
    }

    var backgroundColor = 0
        set(value) {
            field = value
            container.background = Theme.createRoundRectDrawable(analysisInnerRadius(), value)
        }

    fun bind(iconRes: Int, color: Int) {
        backgroundColor = Theme.multAlpha(color, 0.15f)
        if (iconRes == 0) {
            icon.setImageDrawable(null)
        } else {
            icon.setImageResource(iconRes)
            icon.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
    }
}

private class BadgeSpan(private val text: String, private val color: Int) : ReplacementSpan() {
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = AndroidUtilities.bold()
        textSize = dp(BADGE_SIZE).toFloat()
    }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int =
        dp(6f) + (textPaint.measureText(this.text) + dp(12f)).roundToInt()

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val metrics = paint.fontMetrics
        val center = y + (metrics.ascent + metrics.descent) / 2
        val height = dp(20f)
        val left = x + dp(6f)
        rect.set(left, center - height / 2f, left + textPaint.measureText(this.text) + dp(12f), center + height / 2f)
        backgroundPaint.color = Theme.multAlpha(color, 0.15f)
        val radius = minOf(analysisInnerRadius().toFloat(), height / 2f)
        canvas.drawRoundRect(rect, radius, radius, backgroundPaint)
        textPaint.color = color
        val textMetrics = textPaint.fontMetrics
        canvas.drawText(this.text, left + dp(6f), center - (textMetrics.ascent + textMetrics.descent) / 2, textPaint)
    }
}

private fun withBadge(title: CharSequence, badge: CharSequence?, color: Int): CharSequence {
    if (badge.isNullOrEmpty()) {
        return title
    }
    return SpannableStringBuilder(title).apply {
        val from = length
        append(badge.toString().replace(' ', '\u00A0'))
        setSpan(BadgeSpan(badge.toString(), color), from, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

class AnalysisSummary(
    val iconRes: Int,
    val color: Int,
    val title: CharSequence,
    val subtitle: CharSequence?,
    val chips: List<Chip> = emptyList(),
    val loading: Boolean = false,
    val badge: CharSequence? = null,
    val badgeColor: Int = 0,
) {
    class Chip(val iconRes: Int, val text: CharSequence, val color: Int)
}

class AnalysisEvidence(
    val lines: List<Line>,
    val more: Int,
) {
    class Line(val label: CharSequence?, val line: Int, val code: CharSequence, val copy: CharSequence = code)
}

class AnalysisStatementRow(
    val key: String,
    val iconRes: Int,
    val color: Int,
    val title: CharSequence,
    val subtitle: CharSequence?,
    val values: List<String>,
    val evidence: AnalysisEvidence?,
    val notes: List<CharSequence> = emptyList(),
)

private class ChipsLayout(context: Context) : ViewGroup(context) {
    private val gap = dp(6f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        var x = 0
        var y = 0
        var rowHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(dp(CHIP_HEIGHT), MeasureSpec.EXACTLY)
            )
            if (x > 0 && x + child.measuredWidth > width) {
                x = 0
                y += rowHeight + gap
                rowHeight = 0
            }
            x += child.measuredWidth + gap
            rowHeight = maxOf(rowHeight, child.measuredHeight)
        }
        setMeasuredDimension(width, if (childCount == 0) 0 else y + rowHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val rtl = LocaleController.isRTL
        var x = 0
        var y = 0
        var rowHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (x > 0 && x + child.measuredWidth > width) {
                x = 0
                y += rowHeight + gap
                rowHeight = 0
            }
            val left = if (rtl) width - x - child.measuredWidth else x
            child.layout(left, y, left + child.measuredWidth, y + child.measuredHeight)
            x += child.measuredWidth + gap
            rowHeight = maxOf(rowHeight, child.measuredHeight)
        }
    }
}

@SuppressLint("ViewConstructor")
class AnalysisSummaryCell(
    context: Context,
    private val resourcesProvider: Theme.ResourcesProvider?,
) : FrameLayout(context) {
    private val content = LinearLayout(context)
    private val icon = TintedIcon(context)
    private val progressView = RadialProgressView(context, resourcesProvider).apply {
        setSize(dp(GLYPH_SIZE - 2f))
        setStrokeWidth(2f)
        setStyle(RadialProgressView.STYLE_MATERIAL3_PROGRESS_INDETERMINATE_FLAT)
    }
    private val titleView = TextView(context).apply {
        setTextMetrics(TITLE_SIZE, TITLE_LINE, AndroidUtilities.bold())
        setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider))
    }
    private val subtitleView = TextView(context).apply {
        setTextMetrics(SUBTITLE_SIZE, SUBTITLE_LINE)
        setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider))
    }
    private val arrowView = arrowView(context, resourcesProvider).apply {
        rotation = if (LocaleController.isRTL) 90f else -90f
    }
    private val dividerView = View(context)
    private val chipsLayout = ChipsLayout(context)
    private var wasLoading: Boolean? = null

    init {
        icon.container.addView(progressView, LayoutHelper.createFrame(GLYPH_SIZE, GLYPH_SIZE, Gravity.CENTER))

        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(CARD_PADDING), dp(CARD_PADDING), dp(CARD_PADDING), dp(CARD_PADDING))
        addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat()))

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            minimumHeight = dp(ICON_SIZE)
            addView(icon.container, LayoutHelper.createLinear(ICON_SIZE.toInt(), ICON_SIZE.toInt(), Gravity.TOP))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
                addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, TEXT_GAP, 0f, 0f))
            }, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, CARD_PADDING.toInt(), 0, 0, 0))
            addView(arrowView, LayoutHelper.createLinear(ARROW_BOX.toInt(), ARROW_BOX.toInt(), Gravity.TOP, 4, ((ICON_SIZE - ARROW_BOX) / 2).toInt(), 0, 0))
        }
        content.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
        content.addView(dividerView, LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, 1).apply {
            topMargin = dp(CARD_PADDING)
            bottomMargin = dp(CARD_PADDING - 1)
        })
        content.addView(chipsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
    }

    fun set(summary: AnalysisSummary?, onClick: OnClickListener?) {
        summary ?: return
        content.setOnClickListener(onClick)
        content.isClickable = onClick != null
        content.background = if (onClick != null) {
            Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), Theme.RIPPLE_MASK_ALL)
        } else {
            null
        }
        arrowView.visibility = if (onClick != null) VISIBLE else GONE

        val animated = wasLoading == true && !summary.loading
        wasLoading = summary.loading
        val fromColor = icon.backgroundColor
        icon.bind(if (summary.loading) 0 else summary.iconRes, summary.color)
        val toColor = icon.backgroundColor
        if (summary.loading) {
            progressView.setProgressColor(summary.color)
        }
        val blend = if (animated && fromColor != toColor) {
            icon.backgroundColor = fromColor
            ValueAnimator.AnimatorUpdateListener { icon.backgroundColor = ColorUtils.blendARGB(fromColor, toColor, it.animatedFraction) }
        } else {
            null
        }
        AndroidUtilities.updateViewVisibilityAnimated(progressView, summary.loading, 0.5f, animated)
        AndroidUtilities.updateViewVisibilityAnimated(icon.icon, !summary.loading, 0.5f, true, 1f, animated, blend)
        titleView.text = withBadge(summary.title, summary.badge, summary.badgeColor)
        subtitleView.text = summary.subtitle
        subtitleView.visibility = if (summary.subtitle.isNullOrEmpty()) GONE else VISIBLE

        chipsLayout.removeAllViews()
        for (chip in summary.chips) {
            chipsLayout.addView(chipView(chip))
        }
        val hasChips = summary.chips.isNotEmpty()
        chipsLayout.visibility = if (hasChips) VISIBLE else GONE
        dividerView.visibility = if (hasChips) VISIBLE else GONE
        dividerView.setBackgroundColor(Theme.getDividerColor(resourcesProvider))
    }

    private fun chipView(chip: AnalysisSummary.Chip) = TextView(context).apply {
        setTextMetrics(SUBTITLE_SIZE, SUBTITLE_LINE, AndroidUtilities.bold())
        setTextColor(chip.color)
        gravity = Gravity.CENTER_VERTICAL
        isSingleLine = true
        setPadding(dp(8f), 0, dp(10f), 0)
        background = Theme.createRoundRectDrawable(analysisInnerRadius(), Theme.multAlpha(chip.color, 0.15f))
        compoundDrawablePadding = dp(6f)
        ContextCompat.getDrawable(context, chip.iconRes)?.mutate()?.let { drawable ->
            drawable.colorFilter = PorterDuffColorFilter(chip.color, PorterDuff.Mode.SRC_IN)
            drawable.setBounds(0, 0, dp(16f), dp(16f))
            setCompoundDrawablesRelative(drawable, null, null, null)
        }
        text = chip.text
    }

    class Factory : UItem.UItemFactory<AnalysisSummaryCell>() {
        override fun createView(
            context: Context,
            listView: RecyclerListView?,
            currentAccount: Int,
            classGuid: Int,
            resourcesProvider: Theme.ResourcesProvider?,
        ): AnalysisSummaryCell = AnalysisSummaryCell(context, resourcesProvider)

        override fun bindView(view: View, item: UItem, divider: Boolean, adapter: UniversalAdapter?, listView: UniversalRecyclerView?) {
            (view as? AnalysisSummaryCell)?.set(item.`object` as? AnalysisSummary, item.clickCallback)
        }

        override fun equals(a: UItem, b: UItem): Boolean = a.id == b.id

        override fun contentsEquals(a: UItem, b: UItem): Boolean =
            a.id == b.id && a.`object` === b.`object` && (a.clickCallback == null) == (b.clickCallback == null)

        override fun isClickable(): Boolean = false

        companion object {
            init {
                setup(Factory())
            }

            @JvmStatic
            fun of(id: Int, summary: AnalysisSummary, onClick: OnClickListener?): UItem = UItem.ofFactory(Factory::class.java).apply {
                this.id = id
                `object` = summary
                clickCallback = onClick
            }
        }
    }
}

@SuppressLint("ViewConstructor")
class AnalysisStatementCell(
    context: Context,
    private val resourcesProvider: Theme.ResourcesProvider?,
) : FrameLayout(context) {
    private val mono = AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO)

    private val header = FrameLayout(context)
    private val icon = TintedIcon(context)
    private val titleView = TextView(context)
    private val subtitleView = TextView(context)
    private val arrowView = arrowView(context, resourcesProvider)
    private val docsView = LinearLayout(context)

    private var boundKey: String? = null
    private var boundRow: AnalysisStatementRow? = null
    private var expanded = false
    private var progress = 0f
    private var animator: ValueAnimator? = null
    private var needDivider = false
    private var onCopy: ((CharSequence) -> Unit)? = null

    init {
        setWillNotDraw(false)
        val rtl = LocaleController.isRTL
        val start = if (rtl) Gravity.RIGHT else Gravity.LEFT
        val end = if (rtl) Gravity.LEFT else Gravity.RIGHT

        header.background = Theme.createSelectorDrawable(color(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL)
        header.minimumHeight = dp(TEXT_START)
        header.addView(
            icon.container,
            LayoutHelper.createFrame(ICON_SIZE.toInt(), ICON_SIZE, start or Gravity.TOP, CARD_PADDING, CARD_PADDING, CARD_PADDING, 0f)
        )

        titleView.setTextMetrics(TITLE_SIZE, TITLE_LINE, AndroidUtilities.bold())
        titleView.setTextColor(color(Theme.key_windowBackgroundWhiteBlackText))
        titleView.maxLines = 2
        titleView.ellipsize = TextUtils.TruncateAt.END
        titleView.gravity = start

        subtitleView.setTextMetrics(SUBTITLE_SIZE, SUBTITLE_LINE)
        subtitleView.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText))
        subtitleView.maxLines = 2
        subtitleView.ellipsize = TextUtils.TruncateAt.END
        subtitleView.gravity = start

        val textLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
            addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, TEXT_GAP, 0f, 0f))
        }
        val textEnd = ARROW_INSET + ARROW_BOX + 4f
        header.addView(
            textLayout,
            LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), start or Gravity.CENTER_VERTICAL,
                if (rtl) textEnd else TEXT_START, CARD_PADDING, if (rtl) TEXT_START else textEnd, CARD_PADDING
            )
        )
        header.addView(
            arrowView,
            LayoutHelper.createFrame(ARROW_BOX.toInt(), ARROW_BOX, end or Gravity.TOP, ARROW_INSET, ARROW_INSET, ARROW_INSET, 0f)
        )

        addView(header)

        docsView.orientation = LinearLayout.VERTICAL
        docsView.setPadding(dp(CARD_PADDING), 0, dp(CARD_PADDING), dp(CARD_PADDING))
        addView(docsView)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        header.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        docsView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val docsHeight = (docsView.measuredHeight * progress).roundToInt()
        setMeasuredDimension(width, header.measuredHeight + docsHeight + if (needDivider) 1 else 0)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        header.layout(0, 0, header.measuredWidth, header.measuredHeight)
        docsView.layout(0, header.measuredHeight, docsView.measuredWidth, header.measuredHeight + docsView.measuredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        if (!needDivider) {
            return
        }
        val inset = dp(TEXT_START).toFloat()
        val y = measuredHeight - 1f
        val paint = resourcesProvider?.getPaint(Theme.key_paint_divider) ?: Theme.dividerPaint
        if (LocaleController.isRTL) {
            canvas.drawLine(0f, y, measuredWidth - inset, y, paint)
        } else {
            canvas.drawLine(inset, y, measuredWidth.toFloat(), y, paint)
        }
    }

    fun set(row: AnalysisStatementRow, expand: Boolean, divider: Boolean, onClick: OnClickListener?, onCopy: ((CharSequence) -> Unit)? = null) {
        this.onCopy = onCopy
        animator?.let {
            it.removeAllListeners()
            it.cancel()
        }
        animator = null
        val expandable = row.evidence != null || row.values.isNotEmpty() || row.notes.isNotEmpty()
        val open = expand && expandable
        val animated = boundKey == row.key && expanded != open
        boundKey = row.key
        expanded = open

        icon.bind(row.iconRes, row.color)
        titleView.text = row.title
        subtitleView.text = row.subtitle
        subtitleView.visibility = if (row.subtitle.isNullOrEmpty()) GONE else VISIBLE
        arrowView.visibility = if (expandable) VISIBLE else GONE
        header.tag = row.key
        header.setOnClickListener(if (expandable) onClick else null)
        header.isClickable = expandable

        if (open && row !== boundRow) {
            boundRow = row
            buildDocs(row)
        }

        val target = if (open) 1f else 0f
        arrowView.animate().cancel()
        if (animated) {
            arrowView.animate().rotation(target * 180).setDuration(340).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start()
            animator = ValueAnimator.ofFloat(progress, target).apply {
                duration = 340
                interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
                addUpdateListener { setProgress(it.animatedValue as Float) }
                doOnEnd { if (!expanded) clearDocs() }
                start()
            }
        } else {
            arrowView.rotation = target * 180
            setProgress(target)
            if (!open) {
                clearDocs()
            }
        }

        needDivider = divider
        requestLayout()
        invalidate()
    }

    private fun setProgress(value: Float) {
        progress = value
        docsView.alpha = value
        requestLayout()
    }

    private fun clearDocs() {
        boundRow = null
        docsView.removeAllViews()
    }

    private fun buildDocs(row: AnalysisStatementRow) {
        docsView.removeAllViews()
        val hasValues = row.values.isNotEmpty() || row.notes.isNotEmpty()
        if (hasValues) {
            val block = block()
            row.values.forEach { value ->
                block.addView(monoText(value, Theme.key_windowBackgroundWhiteBlackText).copying(value), lineParams())
            }
            row.notes.forEach { note ->
                block.addView(noteText(note), lineParams())
            }
            docsView.addView(block, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
        }
        val evidence = row.evidence ?: return
        if (evidence.lines.isEmpty()) {
            return
        }
        val gutterPaint = monoText("", Theme.key_windowBackgroundWhiteGrayText).paint
        val groups = evidence.lines.groupBy { it.label?.toString() }
        groups.entries.forEachIndexed { groupIndex, (label, lines) ->
            val block = block()
            if (label != null) {
                block.addView(monoText(label, Theme.key_windowBackgroundWhiteGrayText).inset(), lineParams())
            }
            val gutterWidth = lines.filter { it.line > 0 }.maxOfOrNull { gutterPaint.measureText(it.line.toString()) }?.toInt() ?: 0
            for (line in lines) {
                block.addView(codeLine(line, gutterWidth), lineParams())
            }
            if (groupIndex == groups.size - 1 && evidence.more > 0) {
                block.addView(
                    monoText(LocaleController.formatString(R.string.PluginAnalysisMorePlaces, evidence.more), Theme.key_windowBackgroundWhiteGrayText).inset(),
                    lineParams()
                )
            }
            val gap = if (groupIndex > 0 || hasValues) BLOCK_GAP else 0f
            docsView.addView(block, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, gap, 0f, 0f))
        }
    }

    private fun block() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val x = dp(CARD_PADDING - LINE_INSET_X)
        val y = dp(CARD_PADDING - LINE_INSET_Y)
        setPadding(x, y, x, y)
        background = Theme.createRoundRectDrawable(
            analysisInnerRadius(),
            Theme.multAlpha(color(Theme.key_windowBackgroundWhiteBlackText), 0.06f)
        )
    }

    private fun lineParams() = LayoutHelper.createLinear(
        LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, BLOCK_GAP - LINE_INSET_Y * 2, 0f, 0f
    )

    private fun <T : View> T.inset(): T = apply {
        setPadding(dp(LINE_INSET_X), dp(LINE_INSET_Y), dp(LINE_INSET_X), dp(LINE_INSET_Y))
    }

    private fun noteText(text: CharSequence) = TextView(context).apply {
        setTextMetrics(SUBTITLE_SIZE, CODE_LINE)
        setTextColor(color(Theme.key_windowBackgroundWhiteGrayText))
        this.text = text
    }.inset()

    private fun monoText(text: CharSequence, colorKey: Int) = TextView(context).apply {
        setTextMetrics(CODE_SIZE, CODE_LINE, mono)
        setTextColor(color(colorKey))
        this.text = text
    }

    private fun <T : View> T.copying(text: CharSequence): T = inset().apply {
        val radius = analysisLineRadiusDp()
        background = Theme.createRadSelectorDrawable(color(Theme.key_listSelector), radius, radius)
        setOnClickListener { onCopy?.invoke(text) }
    }

    private fun codeLine(line: AnalysisEvidence.Line, gutterWidth: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        copying(line.copy)
        if (gutterWidth > 0) {
            addView(
                monoText(if (line.line > 0) line.line.toString() else "", Theme.key_windowBackgroundWhiteGrayText).apply { gravity = Gravity.END },
                LinearLayout.LayoutParams(gutterWidth, LayoutHelper.WRAP_CONTENT).apply { marginEnd = dp(CARD_PADDING) }
            )
        }
        addView(
            monoText(codeBreaks(line.code), Theme.key_windowBackgroundWhiteBlackText).apply {
                maxLines = 4
                ellipsize = TextUtils.TruncateAt.END
            },
            LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f)
        )
    }

    private fun codeBreaks(code: CharSequence): CharSequence {
        if (code is Spanned) {
            return code
        }
        val out = StringBuilder(code.length + 8)
        for (i in code.indices) {
            val ch = code[i]
            out.append(ch)
            if ((ch == '.' || ch == '(' || ch == ',' || ch == '[' || ch == '=') && i + 1 < code.length && !code[i + 1].isWhitespace()) {
                out.append('\u200B')
            }
        }
        return out
    }

    private fun color(key: Int): Int = Theme.getColor(key, resourcesProvider)

    class Factory : UItem.UItemFactory<AnalysisStatementCell>() {
        override fun createView(
            context: Context,
            listView: RecyclerListView?,
            currentAccount: Int,
            classGuid: Int,
            resourcesProvider: Theme.ResourcesProvider?,
        ): AnalysisStatementCell = AnalysisStatementCell(context, resourcesProvider)

        override fun bindView(view: View, item: UItem, divider: Boolean, adapter: UniversalAdapter?, listView: UniversalRecyclerView?) {
            val row = item.`object` as? AnalysisStatementRow ?: return
            @Suppress("UNCHECKED_CAST")
            (view as AnalysisStatementCell).set(row, item.checked, divider, item.clickCallback, item.object2 as? (CharSequence) -> Unit)
        }

        override fun isClickable(): Boolean = false

        override fun equals(a: UItem, b: UItem): Boolean = a.id == b.id

        override fun contentsEquals(a: UItem, b: UItem): Boolean =
            a.id == b.id && a.checked == b.checked && a.`object` === b.`object`

        companion object {
            init {
                setup(Factory())
            }

            @JvmStatic
            fun of(row: AnalysisStatementRow, expanded: Boolean, onClick: OnClickListener?, onCopy: ((CharSequence) -> Unit)? = null): UItem =
                UItem.ofFactory(Factory::class.java).apply {
                    id = row.key.hashCode()
                    `object` = row
                    object2 = onCopy
                    checked = expanded
                    clickCallback = onClick
                }
        }
    }
}
