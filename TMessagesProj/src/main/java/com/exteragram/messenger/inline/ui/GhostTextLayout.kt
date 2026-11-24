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
import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import android.widget.TextView
import androidx.core.graphics.withTranslation

import org.telegram.messenger.FileLog

import kotlin.math.abs

class GhostTextLayout {

    companion object {
        private const val EPSILON = 0.5f
    }

    private class GhostAlphaSpan : CharacterStyle(), UpdateAppearance {
        var alpha = 1f

        override fun updateDrawState(tp: TextPaint) {
            tp.alpha = (tp.alpha * alpha).toInt()
        }
    }

    private val ghostAlpha = GhostAlphaSpan()
    private var layout: StaticLayout? = null
    private var insertOffset = 0
    private var drawTop = 0f
    private var movedText = false

    var paragraphStart = 0
        private set

    var paragraphEnd = 0
        private set

    var detached = false
        private set

    var extraHeight = 0
        private set

    var cursorShiftX = 0f
        private set

    var cursorShiftY = 0f
        private set

    fun clear() {
        layout = null
        insertOffset = 0
        drawTop = 0f
        movedText = false
        detached = false
        paragraphStart = 0
        paragraphEnd = 0
        extraHeight = 0
        cursorShiftX = 0f
        cursorShiftY = 0f
    }

    fun isEmpty(): Boolean = layout == null

    fun hasMovedText(): Boolean = movedText

    fun rightEdge(): Float {
        val current = layout ?: return -1f
        if (detached || current.lineCount > 1) {
            return Float.POSITIVE_INFINITY
        }
        return current.getLineRight(0)
    }

    fun build(
        view: TextView,
        real: Layout,
        start: Int,
        end: Int,
        caret: Int,
        insert: CharSequence
    ): Boolean {
        clear()
        val source = view.text ?: return false
        if (start < 0 || end > source.length || caret < start || caret > end) {
            return false
        }
        val width = real.width
        if (width <= 0) {
            return false
        }
        val at = caret - start
        val text = SpannableStringBuilder(source, start, end)
        text.insert(at, insert)
        text.setSpan(ghostAlpha, at, at + insert.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val shadow = newLayout(view, real, text, width) ?: return false
        val firstLine = real.getLineForOffset(start)
        val lastLine = real.getLineForOffset(end)
        layout = shadow
        insertOffset = at
        paragraphStart = start
        paragraphEnd = end
        drawTop = real.getLineTop(firstLine).toFloat()
        extraHeight = maxOf(0, shadow.height - (real.getLineTop(lastLine + 1) - real.getLineTop(firstLine)))
        movedText = at > 0 && moved(real, shadow, start + at - 1, at - 1)
        cursorShiftX = shadow.getPrimaryHorizontal(at) - real.getPrimaryHorizontal(caret)
        cursorShiftY = shadow.getLineTop(shadow.getLineForOffset(at)) +
                drawTop - real.getLineTop(real.getLineForOffset(caret))
        return true
    }

    fun buildDetached(view: TextView, real: Layout, end: Int, insert: CharSequence): Boolean {
        clear()
        val width = real.width
        if (width <= 0) {
            return false
        }
        val text = SpannableStringBuilder(insert)
        text.setSpan(ghostAlpha, 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val shadow = newLayout(view, real, text, width) ?: return false
        layout = shadow
        detached = true
        drawTop = real.getLineBottom(real.getLineForOffset(end)).toFloat()
        extraHeight = shadow.height
        return true
    }

    fun setAlpha(value: Float) {
        ghostAlpha.alpha = value
    }

    fun draw(canvas: Canvas) {
        val current = layout ?: return
        canvas.withTranslation(0f, drawTop) {
            current.draw(this)
        }
    }

    fun readInsertedPositions(from: Int, count: Int, x: FloatArray, y: FloatArray) {
        val current = layout ?: return
        for (i in 0 until count) {
            val offset = insertOffset + from + i
            x[i] = current.getPrimaryHorizontal(offset)
            y[i] = drawTop + current.getLineBaseline(current.getLineForOffset(offset))
        }
    }

    private fun moved(real: Layout, shadow: StaticLayout, realOffset: Int, shadowOffset: Int): Boolean {
        if (abs(shadow.getPrimaryHorizontal(shadowOffset) - real.getPrimaryHorizontal(realOffset)) >= EPSILON) {
            return true
        }
        val shadowTop = shadow.getLineTop(shadow.getLineForOffset(shadowOffset)).toFloat()
        val realTop = real.getLineTop(real.getLineForOffset(realOffset)) - drawTop
        return abs(shadowTop - realTop) >= EPSILON
    }

    private fun newLayout(view: TextView, real: Layout, text: CharSequence, width: Int): StaticLayout? = try {
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, view.paint, width)
            .setAlignment(real.alignment)
            .setLineSpacing(view.lineSpacingExtra, view.lineSpacingMultiplier)
            .setIncludePad(view.includeFontPadding)
            .setBreakStrategy(view.breakStrategy)
            .setHyphenationFrequency(view.hyphenationFrequency)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setJustificationMode(view.justificationMode)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setUseLineSpacingFromFallbacks(view.isFallbackLineSpacing)
        }
        builder.build()
    } catch (e: Exception) {
        FileLog.e(e)
        null
    }
}
