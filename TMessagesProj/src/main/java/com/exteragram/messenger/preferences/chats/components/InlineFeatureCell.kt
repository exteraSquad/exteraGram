/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.preferences.chats.components

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Components.UniversalRecyclerView
import kotlin.math.roundToInt

data class InlineDocExample(val input: String, val result: String?)

sealed interface InlineDocBlock {
    data class Text(val text: CharSequence) : InlineDocBlock
    data class Codes(val codes: List<String>) : InlineDocBlock
    data class Examples(val examples: List<InlineDocExample>) : InlineDocBlock
}

data class InlineFeature(
    val icon: Int,
    val title: CharSequence,
    val subtitle: CharSequence,
    val badge: CharSequence?,
    val docs: List<InlineDocBlock>?
)

@SuppressLint("ViewConstructor")
class InlineFeatureCell(context: Context, private val resourcesProvider: Theme.ResourcesProvider?) : FrameLayout(context) {

    private val mono: Typeface = AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO)

    private val header = FrameLayout(context)
    private val iconView = ImageView(context)
    private val titleView = TextView(context)
    private val badgeView = TextView(context)
    private val subtitleView = TextView(context)
    private val arrowView = ImageView(context)
    private val docsView = LinearLayout(context)

    private var boundId = 0
    private var boundDocs: List<InlineDocBlock>? = null
    private var expanded = false
    private var progress = 0f
    private var animator: ValueAnimator? = null
    private var needDivider = false

    init {
        setWillNotDraw(false)
        val start = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT
        val end = if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT

        header.background = Theme.createSelectorDrawable(color(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL)

        iconView.scaleType = ImageView.ScaleType.CENTER
        iconView.colorFilter = PorterDuffColorFilter(color(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY)
        header.addView(iconView, LayoutHelper.createFrame(24, 24f, start or Gravity.CENTER_VERTICAL, 21f, 0f, 21f, 0f))

        titleView.typeface = AndroidUtilities.regular()
        titleView.setTextColor(color(Theme.key_windowBackgroundWhiteBlackText))
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
        titleView.isSingleLine = true
        titleView.ellipsize = TextUtils.TruncateAt.END
        titleView.gravity = start

        badgeView.typeface = AndroidUtilities.regular()
        badgeView.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText2))
        badgeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f)
        badgeView.isSingleLine = true
        badgeView.setPadding(dp(6f), dp(1f), dp(6f), dp(1f))
        badgeView.background = GradientDrawable().apply {
            cornerRadius = dp(6f).toFloat()
            setColor(chipColor())
        }

        subtitleView.typeface = AndroidUtilities.regular()
        subtitleView.setTextColor(color(Theme.key_windowBackgroundWhiteGrayText2))
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
        subtitleView.isSingleLine = true
        subtitleView.ellipsize = TextUtils.TruncateAt.END

        val subtitleRow = LinearLayout(context)
        subtitleRow.orientation = LinearLayout.HORIZONTAL
        subtitleRow.gravity = start or Gravity.CENTER_VERTICAL
        subtitleRow.addView(
            badgeView,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL,
                if (LocaleController.isRTL) 6 else 0, 0, if (LocaleController.isRTL) 0 else 6, 0
            )
        )
        subtitleRow.addView(subtitleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f))

        val textLayout = LinearLayout(context)
        textLayout.orientation = LinearLayout.VERTICAL
        textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
        textLayout.addView(subtitleRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 3f, 0f, 0f))
        header.addView(
            textLayout,
            LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), start or Gravity.CENTER_VERTICAL,
                if (LocaleController.isRTL) 56f else 71f, 0f, if (LocaleController.isRTL) 71f else 56f, 0f
            )
        )

        arrowView.scaleType = ImageView.ScaleType.CENTER
        arrowView.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.arrow_more))
        arrowView.colorFilter = PorterDuffColorFilter(color(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY)
        header.addView(arrowView, LayoutHelper.createFrame(24, 24f, end or Gravity.CENTER_VERTICAL, 18f, 0f, 18f, 0f))

        addView(header)

        docsView.orientation = LinearLayout.VERTICAL
        docsView.isClickable = true
        docsView.setPadding(
            dp(if (LocaleController.isRTL) 21f else 71f), dp(2f),
            dp(if (LocaleController.isRTL) 71f else 21f), dp(14f)
        )
        addView(docsView)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        header.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(60f), MeasureSpec.EXACTLY))
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
        val inset = dp(68f).toFloat()
        val y = measuredHeight - 1f
        val paint = resourcesProvider?.getPaint(Theme.key_paint_divider) ?: Theme.dividerPaint
        if (LocaleController.isRTL) {
            canvas.drawLine(0f, y, measuredWidth - inset, y, paint)
        } else {
            canvas.drawLine(inset, y, measuredWidth.toFloat(), y, paint)
        }
    }

    fun set(id: Int, feature: InlineFeature, divider: Boolean, onClick: OnClickListener?) {
        animator?.let {
            it.removeAllListeners()
            it.cancel()
        }
        animator = null
        val expand = feature.docs != null
        val animated = boundId == id && expanded != expand
        boundId = id
        expanded = expand

        header.tag = id
        header.setOnClickListener(onClick)
        iconView.setImageResource(feature.icon)
        titleView.text = feature.title
        subtitleView.text = feature.subtitle
        badgeView.text = feature.badge
        badgeView.visibility = if (feature.badge == null) View.GONE else View.VISIBLE

        if (feature.docs != null && feature.docs != boundDocs) {
            boundDocs = feature.docs
            buildDocs(feature.docs, onClick)
        }

        val target = if (expand) 1f else 0f
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
            if (!expand) {
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
        boundDocs = null
        docsView.removeAllViews()
    }

    private fun buildDocs(blocks: List<InlineDocBlock>, onClick: OnClickListener?) {
        docsView.removeAllViews()
        blocks.forEachIndexed { index, block ->
            val top = if (index == 0) 0 else 8
            when (block) {
                is InlineDocBlock.Text -> docsView.addView(text(block), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, top.toFloat(), 0f, 0f))
                is InlineDocBlock.Codes -> docsView.addView(codes(block), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, top.toFloat(), 0f, 0f))
                is InlineDocBlock.Examples -> block.examples.forEachIndexed { i, example ->
                    docsView.addView(
                        example(example, onClick),
                        LayoutHelper.createLinear(
                            LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                            if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT,
                            0, if (i == 0) top else 6, 0, 0
                        )
                    )
                }
            }
        }
    }

    private fun text(block: InlineDocBlock.Text): TextView = TextView(context).apply {
        text = block.text
        typeface = AndroidUtilities.regular()
        setTextColor(color(Theme.key_windowBackgroundWhiteGrayText2))
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
        setLineSpacing(dp(2f).toFloat(), 1f)
        gravity = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT
    }

    private fun codes(block: InlineDocBlock.Codes): TextView = TextView(context).apply {
        val builder = SpannableStringBuilder()
        val textColor = color(Theme.key_windowBackgroundWhiteBlackText)
        block.codes.forEachIndexed { index, code ->
            if (index > 0) {
                builder.append(' ')
            }
            val from = builder.length
            builder.append(code)
            builder.setSpan(CodeSpan(mono, chipColor(), textColor), from, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        text = builder
        typeface = AndroidUtilities.regular()
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
        setLineSpacing(dp(10f).toFloat(), 1f)
        setPadding(0, dp(3f), 0, dp(3f))
    }

    private fun example(example: InlineDocExample, onClick: OnClickListener?): TextView = TextView(context).apply {
        val builder = SpannableStringBuilder(example.input)
        if (example.result != null) {
            val from = builder.length
            builder.append(' ').append(example.result)
            builder.setSpan(
                ForegroundColorSpan(Theme.multAlpha(color(Theme.key_windowBackgroundWhiteBlackText), 0.4f)),
                from, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        text = builder
        typeface = mono
        setTextColor(color(Theme.key_windowBackgroundWhiteBlackText))
        setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
        setPadding(dp(8f), dp(4f), dp(8f), dp(4f))
        background = Theme.createSimpleSelectorRoundRectDrawable(
            dp(6f), chipColor(), Theme.multAlpha(color(Theme.key_windowBackgroundWhiteBlackText), 0.12f)
        )
        tag = example.input
        setOnClickListener(onClick)
    }

    private fun chipColor(): Int = Theme.multAlpha(color(Theme.key_windowBackgroundWhiteBlackText), 0.06f)

    private fun color(key: Int): Int = Theme.getColor(key, resourcesProvider)

    private class CodeSpan(private val typeface: Typeface, private val background: Int, private val color: Int) : ReplacementSpan() {

        private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
            prepare(paint)
            fm?.let { paint.getFontMetricsInt(it) }
            return (textPaint.measureText(text, start, end) + dp(12f)).toInt()
        }

        override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            prepare(paint)
            val width = textPaint.measureText(text, start, end)
            rect.set(x, y + textPaint.ascent() - dp(3f), x + width + dp(12f), y + textPaint.descent() + dp(3f))
            backgroundPaint.color = background
            canvas.drawRoundRect(rect, dp(6f).toFloat(), dp(6f).toFloat(), backgroundPaint)
            canvas.drawText(text, start, end, x + dp(6f), y.toFloat(), textPaint)
        }

        private fun prepare(paint: Paint) {
            textPaint.textSize = paint.textSize * 0.93f
            textPaint.typeface = typeface
            textPaint.color = color
        }
    }

    class Factory : UItem.UItemFactory<InlineFeatureCell>() {

        override fun createView(
            context: Context,
            listView: RecyclerListView?,
            currentAccount: Int,
            classGuid: Int,
            resourcesProvider: Theme.ResourcesProvider?,
        ): InlineFeatureCell = InlineFeatureCell(context, resourcesProvider)

        override fun bindView(
            view: View,
            item: UItem,
            divider: Boolean,
            adapter: UniversalAdapter?,
            listView: UniversalRecyclerView?,
        ) {
            val feature = item.`object` as? InlineFeature ?: return
            (view as InlineFeatureCell).set(item.id, feature, divider, item.clickCallback)
        }

        override fun isClickable(): Boolean = false

        override fun equals(a: UItem, b: UItem): Boolean = a.id == b.id

        override fun contentsEquals(a: UItem, b: UItem): Boolean = a.id == b.id && a.`object` == b.`object`

        companion object {
            init {
                setup(Factory())
            }

            @JvmStatic
            fun of(id: Int, feature: InlineFeature, onClick: OnClickListener): UItem {
                val item = UItem.ofFactory(Factory::class.java)
                item.id = id
                item.text = feature.title
                item.`object` = feature
                item.clickCallback = onClick
                return item
            }
        }
    }
}
