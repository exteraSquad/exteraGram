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
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import com.exteragram.messenger.plugins.PluginsController
import com.exteragram.messenger.plugins.hooks.MenuItemRecord
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.ActionBarMenuSubItem
import org.telegram.ui.ActionBar.ActionBarPopupWindow
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AnimatedFloat
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.PopupSwipeBackLayout

open class PluginsMenuWrapper(
    swipeBackLayout: PopupSwipeBackLayout,
    existingItems: List<MenuItemRecord>?,
    private val menuType: String,
    private val contextData: Map<String, Any>,
    private val resourcesProvider: Theme.ResourcesProvider?,
) {
    val menuItemsContainer: LinearLayout
    val swipeBack: LinearLayout

    constructor(
        swipeBackLayout: PopupSwipeBackLayout,
        type: String,
        contextData: Map<String, Any>,
        resourcesProvider: Theme.ResourcesProvider?,
    ) : this(swipeBackLayout, null, type, contextData, resourcesProvider)

    init {
        val context = swipeBackLayout.context

        swipeBack = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val backCell = ActionBarMenuSubItem(context, true, false, resourcesProvider).apply {
            setItemHeight(44)
            setTextAndIcon(LocaleController.getString(R.string.Back), R.drawable.msg_arrow_back)
            getTextView().setPadding(
                if (LocaleController.isRTL) 0 else AndroidUtilities.dp(40f),
                0,
                if (LocaleController.isRTL) AndroidUtilities.dp(40f) else 0,
                0
            )
            setOnClickListener { swipeBackLayout.closeForeground() }
        }
        swipeBack.addView(
            backCell,
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT)
        )

        menuItemsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val swipeBackScrollView = createScrollView(context).apply {
            addView(menuItemsContainer)
        }

        swipeBack.addView(swipeBackScrollView)

        rebuildMenu(existingItems)
    }

    fun rebuildMenu(existingItems: List<MenuItemRecord>?) {
        menuItemsContainer.removeAllViews()

        val items = existingItems ?: PluginsController.getInstance()
            .getMenuItemsForLocation(menuType, contextData)
        val context = menuItemsContainer.context

        menuItemsContainer.addView(
            createGap(),
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, GAP_ITEM_HEIGHT)
        )

        var totalHeight = 0
        for (record in items) {
            if (record.text.isNullOrEmpty()) {
                continue
            }

            val item = ActionBarMenuSubItem(context, false, false, resourcesProvider).apply {
                setTextAndIcon(record.text, record.iconResId)
                minimumWidth = AndroidUtilities.dp(196f)
                setOnClickListener {
                    closeMenu()
                    record.executeClick(contextData)
                }
                if (record.hasLongClick()) {
                    setOnLongClickListener {
                        closeMenu()
                        record.executeLongClick(contextData)
                        true
                    }
                }
            }

            var itemHeight = ITEM_HEIGHT
            if (!TextUtils.isEmpty(record.subtext)) {
                item.setSubtext(record.subtext)
                item.setItemHeight(SUBTITLE_ITEM_HEIGHT)
                itemHeight = SUBTITLE_ITEM_HEIGHT
            }

            menuItemsContainer.addView(
                item,
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, itemHeight)
            )
            totalHeight += itemHeight
            item.tag = record
        }

        val maxHeight = AndroidUtilities.dp(436f)
        val parent = menuItemsContainer.parent as? View ?: return
        val layoutParams = (parent.layoutParams as? LinearLayout.LayoutParams)
            ?: LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT)

        layoutParams.height =
            if (totalHeight > maxHeight && kotlin.math.abs(totalHeight - maxHeight) > SUBTITLE_ITEM_HEIGHT * 2) {
                maxHeight
            } else {
                LayoutHelper.WRAP_CONTENT
            }
        parent.layoutParams = layoutParams
    }

    private fun createScrollView(context: Context): ScrollView {
        return object : ScrollView(context) {
            private val alphaFloat =
                AnimatedFloat(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT)
            private var topShadowDrawable: Drawable? = null
            private var wasCanScrollVertically = false

            override fun onNestedScroll(
                target: View,
                dxConsumed: Int,
                dyConsumed: Int,
                dxUnconsumed: Int,
                dyUnconsumed: Int
            ) {
                super.onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed)
                val canScrollUp = canScrollVertically(-1)
                if (wasCanScrollVertically != canScrollUp) {
                    invalidate()
                    wasCanScrollVertically = canScrollUp
                }
            }

            override fun dispatchDraw(canvas: Canvas) {
                super.dispatchDraw(canvas)
                val alpha = 0.5f * alphaFloat.set(if (canScrollVertically(-1)) 1f else 0f)
                if (alpha <= 0f) {
                    return
                }

                if (topShadowDrawable == null) {
                    topShadowDrawable = ContextCompat.getDrawable(context, R.drawable.header_shadow)
                }

                topShadowDrawable?.let { drawable ->
                    drawable.setBounds(0, scrollY, width, scrollY + drawable.intrinsicHeight)
                    drawable.alpha = (0xFF * alpha).toInt()
                    drawable.draw(canvas)
                }
            }
        }
    }

    private fun createGap(): View {
        return ActionBarPopupWindow.GapView(menuItemsContainer.context, resourcesProvider).apply {
            setDividerVisible(false)
        }
    }

    protected open fun closeMenu() {
    }

    companion object {
        const val ITEM_HEIGHT = 48
        const val SUBTITLE_ITEM_HEIGHT = 56
        const val GAP_ITEM_HEIGHT = 8
    }
}