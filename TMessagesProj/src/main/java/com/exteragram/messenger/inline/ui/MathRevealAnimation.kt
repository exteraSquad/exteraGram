/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.inline.ui

import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.text.Editable
import android.text.Layout
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import android.widget.TextView

import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withScale

import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.Components.CubicBezierInterpolator

class MathRevealAnimation(private val view: TextView, private val onFinished: Runnable) {

    companion object {
        private const val GHOST_ALPHA = 0.4f
        private const val BASE_DURATION = 200f
        private const val PER_CHAR = 28f
        private const val MAX_DURATION = 420f
        private const val HIGHLIGHT_DURATION = 800f
        private const val WAVE = 3.5f
        private const val SCALE = 1.12f
        private const val PIVOT_RATIO = 0.32f
    }

    private class MaskSpan(private val owner: MathRevealAnimation) : CharacterStyle(), UpdateAppearance {
        override fun updateDrawState(tp: TextPaint) {
            if (owner.maskSpan === this) {
                tp.alpha = 0
            }
        }
    }

    private val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG)

    private var maskSpan: MaskSpan? = null
    private var rangeStart = -1
    private var rangeEnd = -1
    private var startedAt = 0L
    private var duration = BASE_DURATION
    private var fromX = FloatArray(0)
    private var fromY = FloatArray(0)
    private var toX = FloatArray(0)
    private var toY = FloatArray(0)
    private var targetLayout: Layout? = null

    fun isRunning(): Boolean = rangeStart >= 0

    fun isCaretOutside(caret: Int): Boolean = caret < rangeStart || caret > rangeEnd

    fun begin(text: Editable, from: Int, count: Int, originX: FloatArray, originY: FloatArray) {
        rangeStart = from
        rangeEnd = from + count
        fromX = originX
        fromY = originY
        toX = FloatArray(count)
        toY = FloatArray(count)
        targetLayout = null
        startedAt = SystemClock.elapsedRealtime()
        duration = minOf(MAX_DURATION, BASE_DURATION + PER_CHAR * count)
        val span = MaskSpan(this)
        maskSpan = span
        text.setSpan(span, rangeStart, rangeEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    fun cancel() = stop(false)

    fun draw(canvas: Canvas, accent: Int) {
        val layout = view.layout
        val text = view.text
        if (layout == null || text == null || rangeEnd > text.length) {
            stop(true)
            return
        }
        val count = rangeEnd - rangeStart
        if (targetLayout !== layout) {
            targetLayout = layout
            for (i in 0 until count) {
                val offset = rangeStart + i
                toX[i] = layout.getPrimaryHorizontal(offset)
                toY[i] = layout.getLineBaseline(layout.getLineForOffset(offset)).toFloat()
            }
        }
        val elapsed = (SystemClock.elapsedRealtime() - startedAt).toFloat()
        val progress = (elapsed / duration).coerceIn(0f, 1f)
        val highlight = (elapsed / HIGHLIGHT_DURATION).coerceIn(0f, 1f)
        val tint = ColorUtils.blendARGB(
            accent, view.currentTextColor, CubicBezierInterpolator.EASE_BOTH.getInterpolation(highlight)
        )
        val tintAlpha = Color.alpha(tint)
        val pivotY = view.paint.textSize * PIVOT_RATIO
        paint.set(view.paint)
        paint.color = tint
        for (i in 0 until count) {
            val offset = rangeStart + i
            val wave = AndroidUtilities.cascade(progress, i.toFloat(), count.toFloat(), WAVE)
            val eased = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(wave)
            val popped = CubicBezierInterpolator.EASE_OUT_BACK.getInterpolation(wave)
            val x = AndroidUtilities.lerp(fromX[i], toX[i], eased)
            val y = AndroidUtilities.lerp(fromY[i], toY[i], eased)
            val scale = AndroidUtilities.lerp(SCALE, 1f, popped)
            paint.alpha = (tintAlpha * AndroidUtilities.lerp(GHOST_ALPHA, 1f, eased)).toInt()
            canvas.withScale(scale, scale, x + paint.measureText(text, offset, offset + 1) / 2f, y - pivotY) {
                drawText(text, offset, offset + 1, x, y, paint)
            }
        }
        if (progress < 1f || highlight < 1f) {
            view.invalidate()
        } else {
            stop(true)
        }
    }

    private fun stop(deferred: Boolean) {
        val span = maskSpan
        if (rangeStart < 0 && span == null) {
            return
        }
        rangeStart = -1
        rangeEnd = -1
        maskSpan = null
        targetLayout = null
        if (span != null) {
            if (deferred) {
                AndroidUtilities.runOnUIThread(this::removeMasks)
            } else {
                removeMasks()
            }
        }
        onFinished.run()
        view.invalidate()
    }

    private fun removeMasks() {
        val text = view.text
        if (text is Spannable) {
            for (span in text.getSpans(0, text.length, MaskSpan::class.java)) {
                text.removeSpan(span)
            }
        }
    }
}
