/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui

import kotlin.math.roundToInt
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.ReplacementSpan
import org.telegram.messenger.AndroidUtilities.dp

class ColorRectSpan(private val color: Int) : ReplacementSpan() {
    companion object {
        private val colorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val offset = dp(2f)
    }

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val boxSize = paint.textSize.toInt()
        val textWidth = paint.measureText(text, start, end)
        return (textWidth + offset + boxSize).roundToInt()
    }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val textPaint = paint as TextPaint

        if (text is Spanned) {
            val spans = text.getSpans(start, end, CharacterStyle::class.java)
            for (span in spans) {
                if (span === this) continue
                span.updateDrawState(textPaint)
            }
        }

        canvas.drawText(text!!, start, end, x, y.toFloat(), textPaint)

        val boxSize = textPaint.textSize * 0.9f
        val textWidth = textPaint.measureText(text, start, end)

        val centerY = (bottom + top) / 2f
        val halfSize = boxSize / 2f

        val rectLeft = x + textWidth + offset
        val rectTop = centerY - halfSize
        val rectRight = rectLeft + boxSize
        val rectBottom = centerY + halfSize

        val cornerRadius = boxSize * 0.285f

        colorPaint.color = color
        canvas.drawRoundRect(
            rectLeft,
            rectTop,
            rectRight,
            rectBottom,
            cornerRadius,
            cornerRadius,
            colorPaint
        )
    }
}
