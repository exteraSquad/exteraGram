/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.ContextCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.Components.AnimatedFloat
import org.telegram.ui.Components.CircularProgressDrawable
import org.telegram.ui.Components.CubicBezierInterpolator
import androidx.core.graphics.withTranslation
import androidx.core.graphics.withScale

class PluginTranslateButton(context: Context, color: Int) : View(context) {

    private val translateIcon = requireNotNull(ContextCompat.getDrawable(context, R.drawable.msg_translate)).mutate()
    private val resetIcon = requireNotNull(ContextCompat.getDrawable(context, R.drawable.photo_undo2)).mutate()
    private val progressDrawable = CircularProgressDrawable(color)

    private val loadingProgress = AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT)
    private val switchProgress = AnimatedFloat(1f, this, 0, 250, CubicBezierInterpolator.EASE_OUT_QUINT)

    private var icon: Drawable = translateIcon
    private var previousIcon: Drawable? = null

    var state = STATE_TRANSLATE
        private set

    init {
        val filter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        translateIcon.colorFilter = filter
        resetIcon.colorFilter = filter
        updateContentDescription()
    }

    fun setState(state: Int) {
        if (this.state == state) {
            return
        }
        this.state = state
        if (state != STATE_LOADING) {
            val newIcon = if (state == STATE_RESET) resetIcon else translateIcon
            if (newIcon !== icon) {
                previousIcon = icon
                icon = newIcon
                switchProgress.set(0f, true)
            }
        }
        updateContentDescription()
        invalidate()
    }

    private fun updateContentDescription() {
        contentDescription = LocaleController.getString(
            if (state == STATE_RESET) R.string.ShowOriginalButton else R.string.TranslateMessage
        )
    }

    override fun onDraw(canvas: Canvas) {
        val loadingT = loadingProgress.set(if (state == STATE_LOADING) 1f else 0f)
        val offset = dp(10f)

        if (loadingT < 1f) {
            val switchT = switchProgress.set(1f)
            val alpha = 1f - loadingT
            val scale = AndroidUtilities.lerp(1f, .6f, loadingT)
            canvas.withTranslation(0f, -offset * loadingT) {
                previousIcon?.let {
                    if (switchT < 1f) {
                        drawIcon(
                            this,
                            it,
                            alpha * (1f - switchT),
                            scale * AndroidUtilities.lerp(1f, .4f, switchT)
                        )
                    } else {
                        previousIcon = null
                    }
                }
                drawIcon(
                    this,
                    icon,
                    alpha * switchT,
                    scale * AndroidUtilities.lerp(.4f, 1f, switchT)
                )
            }
        }

        if (loadingT > 0f) {
            val y = ((1f - loadingT) * offset).toInt()
            progressDrawable.setBounds(0, y, width, y + height)
            progressDrawable.alpha = (255 * loadingT).toInt()
            progressDrawable.draw(canvas)
            invalidate()
        }
    }

    private fun drawIcon(canvas: Canvas, drawable: Drawable, alpha: Float, scale: Float) {
        if (alpha <= 0f) {
            return
        }
        val left = (width - drawable.intrinsicWidth) / 2
        val top = (height - drawable.intrinsicHeight) / 2
        drawable.setBounds(left, top, left + drawable.intrinsicWidth, top + drawable.intrinsicHeight)
        drawable.alpha = (255 * alpha).toInt()
        canvas.withScale(scale, scale, width / 2f, height / 2f) {
            drawable.draw(this)
        }
    }

    companion object {
        const val STATE_TRANSLATE = 0
        const val STATE_LOADING = 1
        const val STATE_RESET = 2
    }
}
