/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.preferences.components

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.Theme

class PreviewBackgroundDrawable(
    cornerRadiusDp: Float = 12f
) : Drawable() {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val rectF = RectF()
    private val radius = AndroidUtilities.dp(cornerRadiusDp).toFloat()

    var selectionProgress: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    override fun draw(canvas: Canvas) {
        backgroundPaint.color = PreviewColors.getBackgroundColor()

        val strokeColor = ColorUtils.blendARGB(
            PreviewColors.getOutlineColor(),
            Theme.getColor(Theme.key_windowBackgroundWhiteValueText),
            selectionProgress
        )
        strokePaint.color = strokeColor
        strokePaint.strokeWidth = AndroidUtilities.dp(AndroidUtilities.lerp(0.5f, 2f, selectionProgress)).toFloat()

        val strokeInset = strokePaint.strokeWidth / 2f
        rectF.set(
            bounds.left + strokeInset,
            bounds.top + strokeInset,
            bounds.right - strokeInset,
            bounds.bottom - strokeInset
        )

        canvas.drawRoundRect(rectF, radius, radius, backgroundPaint)
        canvas.drawRoundRect(rectF, radius, radius, strokePaint)
    }

    override fun setAlpha(alpha: Int) {
        backgroundPaint.alpha = alpha
        strokePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        backgroundPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
