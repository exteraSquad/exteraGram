/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.Utilities
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.ProgressButton
import org.telegram.ui.Components.ScaleStateListAnimator
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Components.UniversalRecyclerView

class RequiredPluginInfo(@JvmField val id: String) {
    @JvmField
    var name: String? = null

    @JvmField
    var requiredVersion: String? = null

    @JvmField
    var currentVersion: String? = null

    @JvmField
    var downloadUrl: String? = null

    @JvmField
    var state: Int = STATE_MISSING

    val isSatisfied: Boolean
        get() = state == STATE_SATISFIED

    fun title(): CharSequence = name ?: id

    fun subtitle(): CharSequence = when (state) {
        STATE_DISABLED -> LocaleController.getString(R.string.PluginRequiredDisabled)
        STATE_OUTDATED -> LocaleController.formatString(
            R.string.PluginRequiredOutdated,
            currentVersion ?: "",
            requiredVersion ?: ""
        )

        STATE_SATISFIED -> LocaleController.formatString(
            R.string.PluginVersionValue,
            currentVersion ?: ""
        )

        else -> if (requiredVersion.isNullOrEmpty()) {
            LocaleController.getString(R.string.PluginRequiredMissing)
        } else {
            LocaleController.formatString(R.string.PluginRequiredVersion, requiredVersion)
        }
    }

    fun actionText(): CharSequence = when (state) {
        STATE_DISABLED -> LocaleController.getString(R.string.Enable)
        STATE_OUTDATED -> LocaleController.getString(R.string.PluginRequiredUpdate)
        STATE_SATISFIED -> LocaleController.getString(R.string.PluginRequiredInstalled)
        else -> LocaleController.getString(R.string.BotDownloadFileDownload)
    }

    fun contentKey(): String = "$state|$name|$currentVersion|$requiredVersion"

    companion object {
        const val STATE_MISSING = 0
        const val STATE_DISABLED = 1
        const val STATE_OUTDATED = 2
        const val STATE_SATISFIED = 3
    }
}

@SuppressLint("ViewConstructor")
class RequiredPluginCell(context: Context, private val resourcesProvider: Theme.ResourcesProvider?) :
    LinearLayout(context) {

    private val textView: SimpleTextView
    private val subtitleView: SimpleTextView
    private val actionButton: ProgressButton

    private var info: RequiredPluginInfo? = null
    private var delegate: Utilities.Callback<RequiredPluginInfo>? = null
    private var needDivider = false

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(LEFT_PADDING.toFloat()), 0, dp(BUTTON_RIGHT_PADDING.toFloat()), 0)

        textView = SimpleTextView(context).apply {
            setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText))
            setTextSize(16)
            setGravity(if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        subtitleView = SimpleTextView(context).apply {
            setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText))
            setTextSize(13)
            setGravity(if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        val texts = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(
                textView,
                LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT)
            )
            addView(
                subtitleView,
                LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    0f, LINE_GAP, 0f, 0f
                )
            )
        }

        actionButton = ProgressButton(context)
        ScaleStateListAnimator.apply(actionButton, .05f, 1.5f)
        actionButton.setOnClickListener {
            info?.let { current -> delegate?.run(current) }
        }

        val textsParams =
            LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL)
        val gap = BUTTON_GAP_DP.toFloat()
        val buttonParams = LayoutHelper.createLinear(
            LayoutHelper.WRAP_CONTENT, BUTTON_HEIGHT_DP, Gravity.CENTER_VERTICAL,
            if (LocaleController.isRTL) 0f else gap, 0f, if (LocaleController.isRTL) gap else 0f, 0f
        )
        if (LocaleController.isRTL) {
            addView(actionButton, buttonParams)
            addView(texts, textsParams)
        } else {
            addView(texts, textsParams)
            addView(actionButton, buttonParams)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(
                dp(HEIGHT_DP.toFloat()) + if (needDivider) 1 else 0,
                MeasureSpec.EXACTLY
            )
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (!needDivider) {
            return
        }
        val paint = resourcesProvider?.getPaint(Theme.key_paint_divider) ?: Theme.dividerPaint
        canvas.drawLine(
            if (LocaleController.isRTL) 0f else dp(LEFT_PADDING.toFloat()).toFloat(),
            (measuredHeight - 1).toFloat(),
            (measuredWidth - if (LocaleController.isRTL) dp(LEFT_PADDING.toFloat()) else 0).toFloat(),
            (measuredHeight - 1).toFloat(),
            paint
        )
    }

    fun setNeedDivider(divider: Boolean) {
        needDivider = divider
        setWillNotDraw(!divider)
        invalidate()
    }

    fun set(info: RequiredPluginInfo?, delegate: Utilities.Callback<RequiredPluginInfo>?) {
        if (info == null) {
            return
        }
        this.info = info
        this.delegate = delegate

        textView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText))
        textView.text = info.title()

        subtitleView.setTextColor(
            getThemedColor(
                if (info.isSatisfied) Theme.key_windowBackgroundWhiteGrayText
                else Theme.key_text_RedRegular
            )
        )
        subtitleView.text = info.subtitle()

        val accent = getThemedColor(Theme.key_featuredStickers_addButton)
        actionButton.text = info.actionText()
        if (info.isSatisfied) {
            actionButton.setTextColor(accent)
            actionButton.background = null
            actionButton.isClickable = false
            actionButton.isFocusable = false
        } else {
            actionButton.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText))
            actionButton.setProgressColor(getThemedColor(Theme.key_featuredStickers_buttonProgress))
            actionButton.background = Theme.AdaptiveRipple.createRect(
                accent,
                getThemedColor(Theme.key_featuredStickers_addButtonPressed),
                14f
            )
            actionButton.isClickable = true
            actionButton.isFocusable = true
        }
        requestLayout()
    }

    fun setLoading(loading: Boolean) {
        actionButton.setDrawProgress(loading, true)
    }

    private fun getThemedColor(key: Int): Int = Theme.getColor(key, resourcesProvider)

    companion object {
        private const val HEIGHT_DP = 64
        private const val LEFT_PADDING = 21
        private const val LINE_GAP = 4f

        private const val BUTTON_RIGHT_PADDING = 18
        private const val BUTTON_HEIGHT_DP = 28
        private const val BUTTON_GAP_DP = 12
    }

    class Factory : UItem.UItemFactory<RequiredPluginCell>() {
        override fun createView(
            context: Context,
            listView: RecyclerListView?,
            currentAccount: Int,
            classGuid: Int,
            resourcesProvider: Theme.ResourcesProvider?,
        ): RequiredPluginCell = RequiredPluginCell(context, resourcesProvider)

        override fun bindView(
            view: View,
            item: UItem,
            divider: Boolean,
            adapter: UniversalAdapter?,
            listView: UniversalRecyclerView?,
        ) {
            if (view is RequiredPluginCell) {
                @Suppress("UNCHECKED_CAST")
                view.set(
                    item.`object` as? RequiredPluginInfo,
                    item.object2 as? Utilities.Callback<RequiredPluginInfo>
                )
                view.setNeedDivider(divider)
            }
        }

        override fun equals(a: UItem, b: UItem): Boolean = a.id == b.id

        override fun contentsEquals(a: UItem, b: UItem): Boolean =
            a.id == b.id && TextUtils.equals(a.text, b.text)

        override fun isClickable(): Boolean = false

        companion object {
            init {
                setup(Factory())
            }

            @JvmStatic
            fun asRequirement(
                info: RequiredPluginInfo,
                delegate: Utilities.Callback<RequiredPluginInfo>?,
            ): UItem = UItem.ofFactory(Factory::class.java).apply {
                id = info.id.hashCode()
                text = info.contentKey()
                `object` = info
                object2 = delegate
            }
        }
    }
}