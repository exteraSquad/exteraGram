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
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.isGone
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.browser.Browser
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.ScaleStateListAnimator

class PluginRequirementsView @JvmOverloads constructor(
    context: Context,
    private val resourcesProvider: Theme.ResourcesProvider? = null,
) : ViewGroup(context) {
    private val itemSpacing = dp(4f)
    private val lineSpacing = dp(4f)

    fun setRequirements(requirements: List<String>?) {
        removeAllViews()
        if (requirements.isNullOrEmpty()) {
            visibility = GONE
            return
        }
        visibility = VISIBLE

        for (req in requirements) {
            val chip = TextView(context).apply {
                text = req
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)

                val color = getThemedColor(Theme.key_featuredStickers_addButton)
                setTextColor(color)
                typeface = AndroidUtilities.regular()
                background = Theme.createRoundRectDrawable(
                    dp(6f),
                    ColorUtils.setAlphaComponent(color, 30),
                )
                setPadding(
                    dp(6f),
                    dp(2f),
                    dp(6f),
                    dp(2f),
                )

                ScaleStateListAnimator.apply(this)
                setOnClickListener {
                    requirementUrl(req)?.let { Browser.openUrl(context, it) }
                }
            }

            addView(chip)
        }
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)

        val maxWidth = widthSize - paddingLeft - paddingRight
        var x = 0
        var y = paddingTop
        var rowHeight = 0
        var maxChildWidth = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.isGone) continue

            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight

            if (x + childWidth > maxWidth) {
                x = 0
                y += rowHeight + lineSpacing
                rowHeight = 0
            }

            x += childWidth + itemSpacing
            rowHeight = maxOf(rowHeight, childHeight)
            maxChildWidth = maxOf(maxChildWidth, x - itemSpacing)
        }

        y += rowHeight + paddingBottom

        setMeasuredDimension(
            if (widthMode == MeasureSpec.EXACTLY) widthSize else maxChildWidth + paddingRight + paddingLeft,
            y,
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        var x = paddingLeft
        var y = paddingTop
        var rowHeight = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.isGone) continue

            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight

            if (x + childWidth > width - paddingRight) {
                x = paddingLeft
                y += rowHeight + lineSpacing
                rowHeight = 0
            }

            child.layout(x, y, x + childWidth, y + childHeight)
            x += childWidth + itemSpacing
            rowHeight = maxOf(rowHeight, childHeight)
        }
    }

    private fun getThemedColor(key: Int): Int = Theme.getColor(key, resourcesProvider)

    companion object {
        private val NAME_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")
        private val EXTRAS_PATTERN = Regex("\\[[A-Za-z0-9,._\\s-]*]")

        @JvmStatic
        fun requirementName(requirement: String): String {
            val trimmed = requirement.trim()
            val name = NAME_PATTERN.matchAt(trimmed, 0)?.value ?: return trimmed
            return name + EXTRAS_PATTERN.matchAt(trimmed, name.length)?.value.orEmpty()
        }

        @JvmStatic
        fun requirementUrl(requirement: String): String? {
            val trimmed = requirement.trim()
            if (trimmed.contains("://")) {
                return null
            }
            val project = NAME_PATTERN.matchAt(trimmed, 0)?.value ?: return null
            return "https://pypi.org/project/$project/"
        }
    }
}