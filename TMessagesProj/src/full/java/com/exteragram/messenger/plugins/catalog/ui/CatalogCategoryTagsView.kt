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
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.ContextCompat
import com.exteragram.messenger.plugins.catalog.PluginCategory
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.LocaleController
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.Text

@SuppressLint("ViewConstructor")
class CatalogCategoryTagsView(
    context: Context,
    private val resourcesProvider: Theme.ResourcesProvider?,
) : View(context) {
    private class Tag(val category: PluginCategory, val text: Text, val icon: Drawable?) {
        var color = 0
        var compact = false
        val fullWidth = dp(PADDING_START * 2 + ICON_SIZE + PADDING_END) + text.currentWidth.toInt()
        val width: Int
            get() = if (compact) dp(PADDING_START * 2 + ICON_SIZE) else fullWidth
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var tags: List<Tag> = emptyList()

    fun setCategories(categories: List<PluginCategory>) {
        if (tags.map { it.category } == categories) {
            updateColors()
            return
        }
        tags = categories.map { category ->
            Tag(
                category,
                Text(LocaleController.getString(category.titleRes).uppercase(), 10f, AndroidUtilities.bold()),
                ContextCompat.getDrawable(context, category.iconRes)?.mutate(),
            )
        }
        updateColors()
        requestLayout()
    }

    private fun updateColors() {
        for (tag in tags) {
            tag.color = Theme.getColor(tag.category.colorKey, resourcesProvider)
            tag.icon?.colorFilter = PorterDuffColorFilter(tag.color, PorterDuff.Mode.SRC_IN)
        }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        tags.forEach { it.compact = false }
        for (i in tags.indices.reversed()) {
            if (i == 0 || totalWidth() <= width) break
            tags[i].compact = true
        }
        setMeasuredDimension(width, dp(HEIGHT))
    }

    private fun totalWidth(): Int = tags.sumOf { it.width } + dp(GAP) * (tags.size - 1).coerceAtLeast(0)

    override fun onDraw(canvas: Canvas) {
        val isRTL = LocaleController.isRTL
        val height = dp(HEIGHT).toFloat()
        val radius = dp(RADIUS).toFloat()
        val dark = Theme.isCurrentThemeDark()
        var x = if (isRTL) measuredWidth.toFloat() else 0f
        canvas.save()
        canvas.clipRect(0, 0, measuredWidth, measuredHeight)
        for (tag in tags) {
            val width = tag.width
            val left = if (isRTL) x - width else x
            paint.color = Theme.multAlpha(tag.color, if (dark) .20f else .10f)
            AndroidUtilities.rectTmp.set(left, 0f, left + width, height)
            canvas.drawRoundRect(AndroidUtilities.rectTmp, radius, radius, paint)

            val iconCenterX = if (tag.compact) {
                left + width / 2f
            } else if (isRTL) {
                left + width - dp(PADDING_START) - dp(ICON_SIZE) / 2f
            } else {
                left + dp(PADDING_START) + dp(ICON_SIZE) / 2f
            }
            tag.icon?.let {
                val half = dp(ICON_DRAW_SIZE) / 2
                it.setBounds((iconCenterX - half).toInt(), (height / 2f - half).toInt(), (iconCenterX + half).toInt(), (height / 2f + half).toInt())
                it.draw(canvas)
            }
            if (!tag.compact) {
                val textX = if (isRTL) {
                    left + dp(PADDING_END)
                } else {
                    left + dp(PADDING_START * 2 + ICON_SIZE)
                }
                tag.text.draw(canvas, textX, height / 2f, tag.color, 1f)
            }
            x = if (isRTL) left - dp(GAP) else left + width + dp(GAP)
        }
        canvas.restore()
    }

    companion object {
        const val HEIGHT = 18f
        private const val RADIUS = 5f
        private const val GAP = 4f
        private const val PADDING_START = 5f
        private const val PADDING_END = 6f
        private const val ICON_SIZE = 11f
        private const val ICON_DRAW_SIZE = 16f
    }
}
