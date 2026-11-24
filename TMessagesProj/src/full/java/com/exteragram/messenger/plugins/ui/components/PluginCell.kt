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
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.exteragram.messenger.getSectionRadiusDp
import com.exteragram.messenger.plugins.Plugin
import com.exteragram.messenger.plugins.PluginsController
import com.exteragram.messenger.pluginsCompactView
import com.exteragram.messenger.utils.AppUtils
import com.exteragram.messenger.utils.text.LocaleUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.utils.ViewOutlineProviderImpl
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.EffectsTextView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.Switch
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Components.UniversalRecyclerView
import org.telegram.ui.LaunchActivity
import kotlin.math.max

@SuppressLint("ViewConstructor")
class PluginCell(context: Context) : FrameLayout(context),
    NotificationCenter.NotificationCenterDelegate {
    private val imageView: BackupImageView
    private val pluginNameView: TextView
    private val subtitleView: EffectsTextView
    private val descriptionView: EffectsTextView
    private val requirementsLayout: PluginRequirementsView
    private val checkBox: Switch
    private val settingsButton: ImageView
    private val shareButton: ImageView
    private val openInButton: ImageView
    private val deleteButton: ImageView
    private val pinButton: ImageView
    private val headerLayout: LinearLayout

    private var plugin: Plugin? = null
    private var pluginCellDelegate: PluginCellDelegate? = null
    private var compactMode = false

    init {
        isClickable = false
        clipChildren = false
        clipToPadding = false

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        addView(
            contentLayout,
            LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT.toFloat(),
                Gravity.FILL,
                16f,
                16f,
                16f,
                8f
            ),
        )

        headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        contentLayout.addView(
            headerLayout,
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT)
        )

        imageView = BackupImageView(context).apply {
            visibility = GONE
            outlineProvider = createPluginIconOutlineProvider()
            clipToOutline = true
            imageReceiver.setAutoRepeat(1)
        }
        headerLayout.addView(
            imageView,
            LayoutHelper.createLinear(56, 56, Gravity.LEFT or Gravity.TOP, 0f, 0f, 0f, 12f)
        )

        val textLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        headerLayout.addView(
            textLayout,
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT)
        )

        pluginNameView = TextView(context).apply {
            gravity = Gravity.LEFT
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
            ellipsize = TextUtils.TruncateAt.END
            typeface = AndroidUtilities.bold()
        }
        textLayout.addView(
            pluginNameView,
            LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT)
        )

        subtitleView = EffectsTextView(context).apply {
            gravity = Gravity.LEFT
            typeface = AndroidUtilities.regular()
            movementMethod = AndroidUtilities.LinkMovementMethodMy()
            setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText))
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
        }
        textLayout.addView(
            subtitleView,
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                0f,
                2f,
                0f,
                0f
            )
        )

        descriptionView = EffectsTextView(context).apply {
            gravity = Gravity.LEFT
            typeface = AndroidUtilities.regular()
            movementMethod = AndroidUtilities.LinkMovementMethodMy()
            setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText))
        }
        contentLayout.addView(
            descriptionView,
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                0,
                0,
                12,
                0,
                0
            )
        )

        requirementsLayout = PluginRequirementsView(context).apply {
            visibility = GONE
        }
        contentLayout.addView(
            requirementsLayout,
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                0,
                0,
                12,
                0,
                0
            )
        )

        val separator = View(context).apply {
            setBackgroundColor(Theme.getDividerColor(null))
        }
        contentLayout.addView(
            separator,
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                1f / AndroidUtilities.density,
                0,
                0,
                12,
                0,
                8
            )
        )

        val buttonsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        shareButton = createButton(context, R.drawable.msg_share, false) {
            pluginCellDelegate?.sharePlugin()
        }
        buttonsLayout.addView(
            shareButton,
            LayoutHelper.createFrame(40, 40f, Gravity.LEFT or Gravity.TOP, 0f, 0f, 8f, 0f)
        )

        openInButton = createButton(context, R.drawable.msg_openin, false) {
            pluginCellDelegate?.openInExternalApp()
        }
        buttonsLayout.addView(
            openInButton,
            LayoutHelper.createFrame(40, 40f, Gravity.LEFT or Gravity.TOP, 0f, 0f, 8f, 0f)
        )

        pinButton = createButton(context, R.drawable.msg_pin, false) {
            pluginCellDelegate?.pinPlugin(this)
        }
        buttonsLayout.addView(
            pinButton,
            LayoutHelper.createFrame(40, 40f, Gravity.LEFT or Gravity.TOP, 0f, 0f, 8f, 0f)
        )

        settingsButton = createButton(context, R.drawable.msg_settings, false) {
            pluginCellDelegate?.openPluginSettings()
        }.apply {
            visibility = GONE
        }
        buttonsLayout.addView(
            settingsButton,
            LayoutHelper.createFrame(40, 40f, Gravity.LEFT or Gravity.TOP, 0f, 0f, 8f, 0f)
        )

        deleteButton = createButton(context, R.drawable.msg_delete, true) {
            pluginCellDelegate?.deletePlugin()
        }

        addView(
            deleteButton,
            LayoutHelper.createFrame(40, 40f, Gravity.RIGHT or Gravity.BOTTOM, 0f, 0f, 16f, 8f)
        )
        contentLayout.addView(
            buttonsLayout,
            LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.BOTTOM or Gravity.LEFT)
        )

        checkBox = Switch(context).apply {
            setColors(
                Theme.key_switchTrack,
                Theme.key_switchTrackChecked,
                Theme.key_windowBackgroundWhite,
                Theme.key_windowBackgroundWhite,
            )
            isFocusable = false
        }
        addView(
            checkBox,
            LayoutHelper.createFrame(37, 40f, Gravity.RIGHT or Gravity.TOP, 0f, 16f, 24f, 0f)
        )

        setCompact(pluginsCompactView)
    }

    fun setCompact(compact: Boolean) {
        if (compactMode == compact) {
            return
        }
        compactMode = compact
        updateLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
            heightMeasureSpec,
        )
    }

    private fun updateLayout() {
        val plugin = plugin ?: return

        val textLayout = headerLayout.getChildAt(1) as LinearLayout
        headerLayout.orientation =
            if (compactMode) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL

        val imageLp = imageView.layoutParams as LinearLayout.LayoutParams
        imageLp.rightMargin = if (compactMode) dp(16f) else 0
        imageLp.bottomMargin = if (compactMode) 0 else dp(12f)
        imageLp.width = if (compactMode) dp(49f) else dp(56f)
        imageLp.height = if (compactMode) dp(49f) else dp(56f)

        val textLp = textLayout.layoutParams as LinearLayout.LayoutParams
        textLp.gravity = if (compactMode) Gravity.CENTER_VERTICAL else Gravity.LEFT

        pluginNameView.isSingleLine = compactMode
        subtitleView.isSingleLine = compactMode

        val showImage = plugin.getPack() != null && plugin.getIndex() >= 0
        val paddingRight =
            if ((compactMode && !plugin.hasError()) || (!compactMode && !showImage && !plugin.hasError())) dp(
                61f
            ) else 0
        pluginNameView.setPadding(0, 0, paddingRight, 0)
        pluginNameView.post {
            subtitleView.setPadding(0, 0, if (pluginNameView.lineCount > 1) 0 else paddingRight, 0)
        }

        requestLayout()
    }

    fun set(plugin: Plugin?, pluginCellDelegate: PluginCellDelegate?) {
        if (plugin == null || pluginCellDelegate == null) {
            return
        }

        this.pluginCellDelegate = pluginCellDelegate
        this.plugin = plugin

        updatePluginIconOutlineProvider()
        setPinned(PluginsController.isPluginPinned(plugin.getId()))

        openInButton.visibility =
            if (pluginCellDelegate.canOpenInExternalApp()) VISIBLE else GONE

        val showImage = plugin.getPack() != null && plugin.getIndex() >= 0
        imageView.visibility = if (showImage) VISIBLE else GONE

        if (showImage) {
            MediaDataController.getInstance(UserConfig.selectedAccount)
                .setPlaceholderImageByIndex(
                    imageView,
                    plugin.getPack(),
                    plugin.getIndex(),
                    "100_100"
                )
        } else {
            imageView.setImageDrawable(null)
        }

        pluginNameView.text = plugin.getName()
        subtitleView.text =
            SpannableStringBuilder(if (compactMode) "v" else LocaleController.getString(R.string.PluginVersion) + " ")
                .append(plugin.getVersion())
                .append(" • ")
                .append(LocaleUtils.formatWithUsernames(plugin.getAuthor()))

        when {
            plugin.isNotResponding() -> bindNotRespondingState()
            plugin.hasError() -> bindErrorState()
            else -> bindNormalState()
        }

        requirementsLayout.setRequirements(plugin.getRequirements())
        updateLayout()

        checkBox.setChecked(plugin.isEnabled(), false)
        checkBox.setOnClickListener {
            pluginCellDelegate.togglePlugin(this)
        }

        AndroidUtilities.updateViewVisibilityAnimated(
            settingsButton,
            plugin.isEnabled() && PluginsController.getInstance().hasPluginSettings(plugin.getId()),
            0.5f,
            true,
            false,
        )
    }

    private fun bindNotRespondingState() {
        descriptionView.text = LocaleController.getString(R.string.PluginIsNotResponding)
        descriptionView.setTextColor(Theme.getColor(Theme.key_text_RedRegular))
        descriptionView.typeface =
            AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO)
        descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
        descriptionView.setOnClickListener(null)
        checkBox.visibility = GONE
        updateDeleteButton()
    }

    private fun updateDeleteButton() {
        val plugin = plugin ?: return
        val notResponding = plugin.isNotResponding()
        deleteButton.colorFilter = PorterDuffColorFilter(
            Theme.getColor(if (!notResponding) Theme.key_text_RedRegular else Theme.key_windowBackgroundWhiteGrayIcon),
            PorterDuff.Mode.MULTIPLY,
        )
        deleteButton.background = Theme.createSelectorDrawable(
            if (!notResponding) Theme.multAlpha(
                Theme.getColor(Theme.key_text_RedRegular),
                0.12f
            ) else Theme.getColor(Theme.key_dialogButtonSelector),
            1,
            dp(20f),
        )
        deleteButton.setImageResource(if (!notResponding) R.drawable.msg_delete else R.drawable.ic_ab_other)
    }

    private fun bindErrorState() {
        val plugin = plugin ?: return
        descriptionView.text = plugin.getError()?.localizedMessage
        descriptionView.setTextColor(Theme.getColor(Theme.key_text_RedRegular))
        descriptionView.typeface =
            AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO)
        descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
        descriptionView.setOnClickListener {
            val errorMessage = AppUtils.stackTraceToString(plugin.getError())
            if (AndroidUtilities.addToClipboard(errorMessage)) {
                BulletinFactory.of(LaunchActivity.getSafeLastFragment())
                    .createCopyBulletin(LocaleController.getString(R.string.TextCopied)).show()
            }
        }
        checkBox.visibility = GONE
        updateDeleteButton()
    }

    private fun bindNormalState() {
        val plugin = plugin ?: return
        descriptionView.text = LocaleUtils.fullyFormatText(plugin.getDescription())
        descriptionView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
        descriptionView.typeface =
            AndroidUtilities.regular()
        descriptionView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
        descriptionView.setOnClickListener(null)
        checkBox.visibility = VISIBLE
        updateDeleteButton()
    }

    fun setChecked(checked: Boolean, animated: Boolean) {
        checkBox.setChecked(checked, animated)
    }

    fun setPinned(pinned: Boolean) {
        pinButton.setImageResource(if (pinned) R.drawable.msg_unpin else R.drawable.msg_pin)
    }

    private fun createButton(
        context: Context,
        @DrawableRes iconResId: Int,
        isRed: Boolean,
        onClickListener: OnClickListener,
    ): ImageView {
        return ImageView(context).apply {
            applyClickAnimation(this)
            scaleType = ImageView.ScaleType.CENTER
            setImageResource(iconResId)
            colorFilter = PorterDuffColorFilter(
                Theme.getColor(if (isRed) Theme.key_text_RedRegular else Theme.key_windowBackgroundWhiteGrayIcon),
                PorterDuff.Mode.MULTIPLY,
            )
            background = Theme.createSelectorDrawable(
                if (isRed) Theme.multAlpha(
                    Theme.getColor(Theme.key_text_RedRegular),
                    0.12f
                ) else Theme.getColor(Theme.key_dialogButtonSelector),
                1,
                dp(20f),
            )
            setOnClickListener(onClickListener)
        }
    }

    private fun applyClickAnimation(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    v.animate()
                        .scaleX(0.85f)
                        .scaleY(0.85f)
                        .setDuration(80)
                        .start()
                    true
                }

                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    val isClick =
                        event.x >= 0 && event.x <= v.width && event.y >= 0 && event.y <= v.height
                    if (isClick) {
                        v.performClick()
                    }
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(350)
                        .setInterpolator(OvershootInterpolator(1.5f))
                        .start()
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(350)
                        .setInterpolator(OvershootInterpolator(1.5f))
                        .start()
                    true
                }

                else -> false
            }
        }
    }

    private fun getPluginIconRadiusDp(): Int {
        return max(getSectionRadiusDp() - 16, 8)
    }

    private fun createPluginIconOutlineProvider() =
        ViewOutlineProviderImpl.boundsWithRoundRect(dp(getPluginIconRadiusDp().toFloat()).toFloat())

    private fun updatePluginIconOutlineProvider() {
        imageView.outlineProvider = createPluginIconOutlineProvider()
        imageView.invalidateOutline()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        NotificationCenter.getGlobalInstance()
            .addObserver(this, NotificationCenter.pluginSettingsRegistered)
        NotificationCenter.getGlobalInstance()
            .addObserver(this, NotificationCenter.pluginSettingsUnregistered)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        NotificationCenter.getGlobalInstance()
            .removeObserver(this, NotificationCenter.pluginSettingsRegistered)
        NotificationCenter.getGlobalInstance()
            .removeObserver(this, NotificationCenter.pluginSettingsUnregistered)
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        val plugin = plugin ?: return
        if (id == NotificationCenter.pluginSettingsRegistered || id == NotificationCenter.pluginSettingsUnregistered) {
            val pluginId = args.firstOrNull() as? String ?: return
            if (pluginId == plugin.getId()) {
                AndroidUtilities.updateViewVisibilityAnimated(
                    settingsButton,
                    id == NotificationCenter.pluginSettingsRegistered && plugin.isEnabled(),
                    0.5f,
                    true,
                    true,
                )
            }
        }
    }

    class Factory : UItem.UItemFactory<PluginCell>() {
        override fun createView(
            context: Context,
            listView: RecyclerListView,
            currentAccount: Int,
            classGuid: Int,
            resourcesProvider: Theme.ResourcesProvider?,
        ): PluginCell = PluginCell(context)

        override fun bindView(
            view: View,
            item: UItem,
            divider: Boolean,
            adapter: UniversalAdapter,
            listView: UniversalRecyclerView
        ) {
            if (view is PluginCell) {
                view.set(item.`object` as? Plugin, item.object2 as? PluginCellDelegate)
            }
        }

        override fun equals(a: UItem, b: UItem): Boolean = a.id == b.id

        override fun contentsEquals(a: UItem, b: UItem): Boolean {
            if (a.id != b.id) return false
            if (a.intValue != b.intValue) return false

            val plugin1 = a.`object` as? Plugin ?: return false
            val plugin2 = b.`object` as? Plugin ?: return false

            return plugin1 == plugin2
        }

        override fun isClickable(): Boolean = false

        companion object {
            init {
                setup(Factory())
            }

            @JvmStatic
            fun asPlugin(plugin: Plugin, pluginCellDelegate: PluginCellDelegate): UItem {
                return UItem.ofFactory(Factory::class.java).apply {
                    id = plugin.getId().hashCode()
                    `object` = plugin
                    object2 = pluginCellDelegate
                    intValue =
                        (if (plugin.isNotResponding()) 1 else 0) or (if (plugin.isEnabled()) 2 else 0) or (if (plugin.hasError()) 4 else 0)
                }
            }
        }
    }
}