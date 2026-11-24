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
import android.graphics.RectF
import android.view.View
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.LocaleController
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Components.UniversalRecyclerView
import kotlin.math.min

@SuppressLint("ViewConstructor")
class CatalogPluginSkeletonCell(
    context: Context,
    private val resourcesProvider: Theme.ResourcesProvider?,
) : FlickerLoadingView(context, resourcesProvider) {
    private val rect = RectF()
    private var needDivider = false
    private var variant = 0

    init {
        setIsSingleCell(true)
    }

    fun set(variant: Int, divider: Boolean) {
        this.variant = variant
        needDivider = divider
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(HEIGHT) + if (needDivider) 1 else 0)
    }

    override fun onDraw(canvas: Canvas) {
        updateColors()
        updateGradient()
        val paint = getPaint()
        val textEnd = measuredWidth - dp(CatalogPluginCell.PADDING)

        val padding = CatalogPluginCell.PADDING
        val iconEnd = padding + CatalogPluginCell.ICON_SIZE
        drawRect(canvas, padding, padding, dp(iconEnd).toFloat(), iconEnd, CatalogPluginCell.getIconRadius(), paint)
        drawRect(canvas, TEXT_START, 18f, min(dp(TEXT_START + TITLE_WIDTHS[variant % TITLE_WIDTHS.size]), textEnd).toFloat(), 26f, 4f, paint)
        drawRect(canvas, TEXT_START, 44f, min(dp(TEXT_START + 200f), textEnd).toFloat(), 52f, 4f, paint)
        drawRect(canvas, TEXT_START, 68f, min(dp(TEXT_START + 88f), textEnd).toFloat(), 76f, 4f, paint)

        if (needDivider) {
            val left = if (LocaleController.isRTL) 0f else dp(TEXT_START).toFloat()
            val right = measuredWidth - if (LocaleController.isRTL) dp(TEXT_START).toFloat() else 0f
            canvas.drawLine(left, measuredHeight - 1f, right, measuredHeight - 1f, Theme.getThemePaint(Theme.key_paint_divider, resourcesProvider))
        }
        invalidate()
    }

    private fun drawRect(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, radius: Float, paint: android.graphics.Paint) {
        rect.set(dp(left).toFloat(), dp(top).toFloat(), right, dp(bottom).toFloat())
        if (LocaleController.isRTL) {
            rect.set(measuredWidth - rect.right, rect.top, measuredWidth - rect.left, rect.bottom)
        }
        canvas.drawRoundRect(rect, dp(radius).toFloat(), dp(radius).toFloat(), paint)
    }

    class Factory : UItem.UItemFactory<CatalogPluginSkeletonCell>() {
        override fun createView(
            context: Context,
            listView: RecyclerListView,
            currentAccount: Int,
            classGuid: Int,
            resourcesProvider: Theme.ResourcesProvider?,
        ): CatalogPluginSkeletonCell = CatalogPluginSkeletonCell(context, resourcesProvider)

        override fun bindView(
            view: View,
            item: UItem,
            divider: Boolean,
            adapter: UniversalAdapter,
            listView: UniversalRecyclerView,
        ) {
            (view as? CatalogPluginSkeletonCell)?.set(item.intValue, divider)
        }

        override fun equals(a: UItem, b: UItem): Boolean = a.id == b.id

        override fun contentsEquals(a: UItem, b: UItem): Boolean = a.intValue == b.intValue

        override fun isClickable(): Boolean = false

        companion object {
            init {
                setup(Factory())
            }

            fun of(id: Int, variant: Int): UItem = UItem.ofFactory(Factory::class.java).apply {
                this.id = id
                intValue = variant
            }
        }
    }

    companion object {
        private const val HEIGHT = 91f
        private const val TEXT_START = CatalogPluginCell.TEXT_START
        private val TITLE_WIDTHS = floatArrayOf(120f, 88f, 144f)
    }
}
