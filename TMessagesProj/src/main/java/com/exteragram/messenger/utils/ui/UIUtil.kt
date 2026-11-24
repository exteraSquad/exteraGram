/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import com.exteragram.messenger.squareFab
import org.telegram.messenger.AndroidUtilities
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.BaseCell
import org.telegram.ui.Components.ScaleStateListAnimator
import java.util.WeakHashMap
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object UIUtil {

    @JvmStatic
    fun createBottomFade(@ColorInt color: Int): GradientDrawable =
        createFade(color, GradientDrawable.Orientation.TOP_BOTTOM)

    @JvmStatic
    fun createTopFade(@ColorInt color: Int): GradientDrawable =
        createFade(color, GradientDrawable.Orientation.BOTTOM_TOP)

    private fun createFade(
        @ColorInt color: Int,
        orientation: GradientDrawable.Orientation
    ): GradientDrawable = GradientDrawable(
        orientation,
        intArrayOf(
            ColorUtils.setAlphaComponent(color, 0),
            ColorUtils.setAlphaComponent(color, 60),
            color
        )
    )

    private const val FAB_SIZE_DEFAULT = 56
    private const val FAB_SIZE_MINI = 40
    private const val FAB_SQUARE_CORNER_RADIUS_FACTOR = 16
    private const val RIPPLE_PRESS_GAP = 4f

    private val scaleAnimatorRelayoutListeners = WeakHashMap<View, View.OnLayoutChangeListener>()

    @JvmStatic
    @JvmOverloads
    fun applyScaleStateListAnimator(
        view: View,
        selectorRad: Float = 12f,
        top: Boolean = false,
        bottom: Boolean = false,
        basePadding: Int = 3,
        scale: Float = 0.04f,
        tension: Float = 1.5f
    ) {
        scaleAnimatorRelayoutListeners.remove(view)?.let(view::removeOnLayoutChangeListener)
        view.stateListAnimator?.jumpToCurrentState()

        val appliedWidth = view.width
        val appliedHeight = view.height
        val relayout = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v.width != appliedWidth || v.height != appliedHeight) {
                applyScaleStateListAnimator(v, selectorRad, top, bottom, basePadding, scale, tension)
            }
        }
        view.addOnLayoutChangeListener(relayout)
        scaleAnimatorRelayoutListeners[view] = relayout

        val appliedLargestDp = max(appliedWidth, appliedHeight) / AndroidUtilities.density
        val effectiveScale = if (appliedLargestDp > 0f) {
            min(scale, 2f * RIPPLE_PRESS_GAP / appliedLargestDp)
        } else {
            scale
        }

        val updateRipple: (Float) -> Unit = { aFloat ->
            (view.background as? BaseCell.RippleDrawableSafe)?.let { b ->
                (b.mask as? Theme.RippleRadMaskDrawable)?.let { m ->
                    val widthDp = view.width / AndroidUtilities.density
                    val heightDp = view.height / AndroidUtilities.density

                    val shrink = 1f - effectiveScale * aFloat
                    val padH = ((RIPPLE_PRESS_GAP - widthDp * effectiveScale / 2f) * aFloat / shrink).coerceAtLeast(0f)
                    val padV = ((RIPPLE_PRESS_GAP - heightDp * effectiveScale / 2f) * aFloat / shrink).coerceAtLeast(0f)
                    m.setPadding(padH, padV, padH, padV)

                    val outerRadius = (selectorRad - basePadding * aFloat) / shrink
                    val innerRadius = (selectorRad - basePadding) * aFloat / shrink
                    m.setRadius(
                        if (top) outerRadius else innerRadius,
                        if (bottom) outerRadius else innerRadius
                    )
                }
            }
        }
        ScaleStateListAnimator.apply(view, effectiveScale, tension, updateRipple, updateRipple)
        view.stateListAnimator?.jumpToCurrentState()
        updateRipple(if (view.isPressed) 1f else 0f)
    }

    @JvmStatic
    fun getFabSquareCornerRadiusDp(size: Int): Float =
        ceil(FAB_SQUARE_CORNER_RADIUS_FACTOR * size / FAB_SIZE_DEFAULT.toFloat())

    @JvmStatic
    @JvmOverloads
    fun createFabBackground(
        size: Int = FAB_SIZE_DEFAULT,
        @ColorInt color: Int = Theme.getColor(Theme.key_chats_actionBackground),
        @ColorInt pressedColor: Int = Theme.getColor(Theme.key_chats_actionPressedBackground)
    ): Drawable {
        val (finalColor, finalPressedColor) = when (size) {
            FAB_SIZE_MINI -> {
                val blended = ColorUtils.blendARGB(
                    Theme.getColor(Theme.key_windowBackgroundWhite),
                    Color.WHITE,
                    0.1f
                )
                val pressed = Theme.blendOver(
                    Theme.getColor(Theme.key_windowBackgroundWhite), Theme.getColor(
                        Theme.key_listSelector
                    )
                )
                blended to pressed
            }
            else -> color to pressedColor
        }

        val cornerRadiusDp = if (squareFab) {
            getFabSquareCornerRadiusDp(size)
        } else {
            (size / 2f)
        }

        return Theme.createSimpleSelectorRoundRectDrawable(
            AndroidUtilities.dp(cornerRadiusDp),
            finalColor,
            finalPressedColor
        )
    }

    @JvmStatic
    fun drawableToBitmap(drawable: Drawable, width: Int, height: Int): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    fun adjustHsl(color: Int, luminance: Float, saturation: Float = -1.0f): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        if (saturation > 0.0f) {
            hsl[1] = (hsl[1] * saturation).coerceAtMost(1.0f)
        }
        hsl[2] = (hsl[2] * luminance).coerceAtMost(1.0f)
        return ColorUtils.HSLToColor(hsl)
    }

    private val nowPlayingPattern: FloatArray = floatArrayOf(
        // 1
        -5.5f, 20f, 20f, 0.350f,
        -5.5f, -20f, 20f, 0.350f,

        // 2
        -36f, -42f, 22f, 0.375f,
        -36f, 0f, 25f, 0.425f,
        -36f, 42f, 22f, 0.375f,

        // 3
        -70f, 22f, 23f, 0.350f,
        -70f, -22f, 23f, 0.350f,

        // 4
        -99f, 46f, 21f, 0.275f,
        -99f, 0f, 22f, 0.325f,
        -99f, -46f, 21f, 0.275f,

        // 5
        -128f, -23f, 20f, 0.225f,
        -128f, 23f, 20f, 0.225f,
    )

    fun drawNowPlayingPattern(
        canvas: Canvas,
        pattern: Drawable,
        w: Float,
        h: Float,
        alpha: Float
    ) {
        if (alpha <= 0.0f) return

        var i = 0
        while (i < nowPlayingPattern.size) {
            val x = nowPlayingPattern[i]
            val y = nowPlayingPattern[i + 1]
            val size = nowPlayingPattern[i + 2]
            val thisAlpha = nowPlayingPattern[i + 3]

            pattern.setBounds(
                (w + AndroidUtilities.dpf2(x) - AndroidUtilities.dpf2(size) / 2.0f).toInt(),
                (h / 2f + AndroidUtilities.dpf2(y) - AndroidUtilities.dpf2(size) / 2.0f).toInt(),
                (w + AndroidUtilities.dpf2(x) + AndroidUtilities.dpf2(size) / 2.0f).toInt(),
                (h / 2f + AndroidUtilities.dpf2(y) + AndroidUtilities.dpf2(size) / 2.0f).toInt()
            )
            pattern.alpha = (0xFF * alpha * thisAlpha).toInt()
            pattern.draw(canvas)
            i += 4
        }
    }

    @JvmOverloads
    fun getAdaptedSurfaceColor(provider: Theme.ResourcesProvider? = null): Int {
        val surface = Theme.getColor(Theme.key_windowBackgroundWhite, provider)

        if (surface != Theme.getColor(Theme.key_windowBackgroundGray, provider)) {
            return surface
        }

        return adaptColor(surface) { hsv ->
            hsv[2] = if (hsv[2] > 0.5f) (hsv[2] - 0.03f).coerceAtLeast(0f)
            else (hsv[2] + 0.03f).coerceAtMost(1f)
        }
    }

    fun adaptColor(
        color: Int,
        transform: (hsv: FloatArray) -> Unit
    ): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        transform(hsv)
        return Color.HSVToColor(Color.alpha(color), hsv)
    }
}
