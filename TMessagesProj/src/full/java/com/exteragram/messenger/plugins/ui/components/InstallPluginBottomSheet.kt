/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.ui.components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ClickableSpan
import android.text.style.StrikethroughSpan
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import com.exteragram.messenger.badges.BadgesController
import com.exteragram.messenger.components.CheckBoxRow
import com.exteragram.messenger.editor
import com.exteragram.messenger.getSectionRadiusDp
import com.exteragram.messenger.plugins.Plugin
import com.exteragram.messenger.plugins.PluginsConstants
import com.exteragram.messenger.plugins.PluginsController
import com.exteragram.messenger.plugins.analyzer.AnalyzerReport
import com.exteragram.messenger.plugins.analyzer.PluginAnalyzer
import com.exteragram.messenger.plugins.pip.PipController
import com.exteragram.messenger.pluginsSafeMode
import com.exteragram.messenger.pluginsStaticAnalysis
import com.exteragram.messenger.pluginsUnknownSources
import com.exteragram.messenger.preferences
import com.exteragram.messenger.translator.TranslatorUtils
import com.exteragram.messenger.utils.chats.ChatUtils
import com.exteragram.messenger.utils.text.LocaleUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.AndroidUtilities.dpf2
import org.telegram.messenger.FileLoader
import org.telegram.messenger.LanguageDetector
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MessageObject
import org.telegram.messenger.R
import org.telegram.messenger.TranslateController
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.messenger.browser.Browser
import org.telegram.messenger.utils.ViewOutlineProviderImpl
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.BottomSheetWithRecyclerListView
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.EffectsTextView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LinkPath
import org.telegram.ui.Components.LoadingDrawable
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.ScaleStateListAnimator
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl
import org.telegram.ui.Stories.recorder.ButtonWithCounterView
import org.telegram.ui.Stories.recorder.HintView2
import org.telegram.utils.glass.source.GlassSourceColor
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class InstallPluginBottomSheet(
    private val fragment: BaseFragment,
    validationResult: PluginsController.PluginValidationResult,
    private val params: PluginInstallParams,
) : BottomSheetWithRecyclerListView(
    fragment.parentActivity,
    fragment,
    false,
    false,
    false,
    true,
    ActionBarType.FADING,
    fragment.resourceProvider,
) {
    private val plugin = requireNotNull(validationResult.plugin)
    private var enableAfterInstallation = false
    private var installing = false

    @Volatile
    private var cancellationRequested = false
    private var delayedLoadingRunnable: Runnable? = null
    private var currentHint: HintView2? = null
    private val isUpdate = PluginsController.getInstance().plugins.containsKey(plugin.getId())
    private val button: ButtonWithCounterView

    private var contentReady = false
    private var adapter: UniversalAdapter? = null
    private var titleText: CharSequence? = null
    private var sheetContainer: FrameLayout? = null

    private var headerView: View? = null
    private var descriptionView: View? = null
    private var actionsPill: LinearLayout? = null
    private var translateButton: PluginTranslateButton? = null
    private var bottomContainer: LinearLayout? = null
    private var enableAfterInstallRow: View? = null
    private var riskAcknowledged = false

    private var analysisRunning = pluginsStaticAnalysis
    private var analysisTask: PluginAnalyzer.Task? = null
    private var analysisSnapshot: File? = null
    private var analysisSha256: String? = null
    private var dismissed = false
    private val analysisSection = PluginAnalysisSection(resourcesProvider, ::onAnalysisChanged, ::openReport)

    private var errorView: View? = null
    private var errorTextView: TextView? = null
    private var errorText: String? = null

    private val nameText = TranslatableText(plugin.getName(), { it }, ::onTextTransitionUpdate)
    private val descriptionText = TranslatableText(
        plugin.getDescription(),
        { LocaleUtils.fullyFormatText(it, fragment, ::dismiss) },
        ::onTextTransitionUpdate,
    )
    private var translating = false

    private val requirements: List<RequiredPluginInfo> =
        params.requiredPlugins?.toList() ?: emptyList()
    private val unknownSourceBlocked = !params.trusted && !pluginsUnknownSources
    private var blocked = unknownSourceBlocked || params.blockedTitle != null

    private val requirementDelegate =
        Utilities.Callback<RequiredPluginInfo> { info -> onRequirementAction(info) }

    init {
        setDelegate(object : BottomSheetDelegate() {
            override fun canDismiss(): Boolean = !installing
        })

        val activity = fragment.parentActivity as Activity

        occupyNavigationBar = true
        setApplyBottomPadding(false)
        topPadding = 0.2f

        setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray))
        actionBar.apply {
            setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray))
            setCastShadows(false)
        }
        showShadow = false

        titleText = plugin.getName()
        updateTitle()

        takeTranslationIntoAccount = true
        recyclerListView.apply {
            clipChildren = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0)
            setSections()
            itemAnimator = object : DefaultItemAnimator() {
                override fun onMoveAnimationUpdate(holder: RecyclerView.ViewHolder) {
                    super.onMoveAnimationUpdate(holder)
                    containerView.invalidate()
                }
            }.apply {
                supportsChangeAnimations = false
                setDelayAnimations(false)
                setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                setDurations(350)
            }
        }
        recyclerListView.setOnItemClickListener { _, position ->
            val item = adapter?.getItem(position - 1) ?: return@setOnItemClickListener
            val url = item.object2 as? String ?: return@setOnItemClickListener
            Browser.openUrl(context, url)
        }

        headerView = buildHeader(activity)
        descriptionView = buildDescription(activity)
        errorView = buildError(activity)
        checkTranslatable()

        button = ButtonWithCounterView(activity, true, resourcesProvider).apply {
            setRound()
        }
        applyButtonState(false)

        bottomContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = sheetBottomBackdrop(getThemedColor(Theme.key_windowBackgroundGray))
            @Suppress("DEPRECATION")
            setPadding(0, 0, 0, AndroidUtilities.navigationBarHeight)

            addView(
                button,
                LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, dp(48f)).apply {
                    val horizontal = backgroundPaddingLeft + dp(SECTION_GAP)
                    setMargins(horizontal, dp(12F), horizontal, dp(SECTION_GAP))
                }
            )
            if (!plugin.isEnabled() && !pluginsSafeMode) {
                enableAfterInstallRow = createEnableAfterInstallRow(activity).also { row ->
                    row.visibility = if (blocked) View.GONE else View.VISIBLE
                    addView(
                        row,
                        LayoutHelper.createLinear(
                            LayoutHelper.WRAP_CONTENT,
                            LayoutHelper.WRAP_CONTENT,
                            Gravity.CENTER_HORIZONTAL,
                            0f,
                            0f,
                            0f,
                            8f
                        )
                    )
                }
            }

            addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
                val height = bottom - top
                if (recyclerListView.paddingBottom != height) {
                    recyclerListView.setPadding(
                        backgroundPaddingLeft,
                        0,
                        backgroundPaddingLeft,
                        height
                    )
                }
            }
        }
        sheetContainer?.addView(
            bottomContainer,
            LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        val menu = actionBar.createMenu()
        menu.setPadding(0, 0, dp(3.5f), 0)
        menu.addItem(1, R.drawable.msg_openin)
        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                when (id) {
                    -1 -> dismiss()
                    1 -> openPluginFile()
                }
            }
        })

        if (pluginsStaticAnalysis) {
            startAnalysis()
        } else {
            analysisSection.setDisabled()
        }

        contentReady = true
        adapter?.update(false)

        layoutManager.scrollToPositionWithOffset(0, 0)
    }

    override fun getTitle(): CharSequence = titleText ?: ""

    override fun createAdapter(listView: RecyclerListView): RecyclerListView.SelectionAdapter {
        return UniversalAdapter(
            listView,
            context,
            currentAccount,
            baseFragment?.classGuid ?: 0,
            true,
            this::fillItems,
            resourcesProvider,
        ).also { adapter = it }
    }

    private fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        if (!contentReady) {
            return
        }
        headerView?.let {
            items.add(UItem.asCustomShadow(it))
            items.add(spacer(20F))
        }
        descriptionView?.let { items.add(UItem.asCustom(it)) }

        if (requirements.isNotEmpty()) {
            addSectionGap(items)
            items.add(pluginSheetHeader(LocaleController.getString(R.string.PluginRequired)))
            for (info in requirements) {
                items.add(RequiredPluginCell.Factory.asRequirement(info, requirementDelegate))
            }
        }

        val pipRequirements = plugin.getRequirements()
        if (!pipRequirements.isNullOrEmpty()) {
            addSectionGap(items)
            items.add(pluginSheetHeader(LocaleController.getString(R.string.PluginRequirements)))
            for ((index, requirement) in pipRequirements.withIndex()) {
                items.add(pipRow(1000 + index, requirement))
            }
        }

        params.bundledPackages?.takeIf { it.isNotEmpty() }?.let { packages ->
            addSectionGap(items)
            items.add(pluginSheetHeader(LocaleController.getString(R.string.PluginBundledPackages)))
            for ((index, wheel) in packages.withIndex()) {
                items.add(wheelRow(1500 + index, wheel))
            }
        }

        if (analysisSection.isVisible) {
            addSectionGap(items)
            analysisSection.fill(items)
        }

        if (errorText != null) {
            errorView?.let {
                addSectionGap(items)
                items.add(UItem.asCustomShadow(it))
            }
        }
    }

    private fun pipRow(id: Int, requirement: String): UItem {
        val trimmed = requirement.trim()
        val name = PluginRequirementsView.requirementName(trimmed)
        val url = PluginRequirementsView.requirementUrl(trimmed)
            ?: return UItem.asSettingsCell(id, trimmed, null)
        return UItem.asButton(id, name, trimmed.removePrefix(name).trim().ifEmpty { null })
            .also { it.object2 = url }
    }

    private fun wheelRow(id: Int, fileName: String): UItem {
        val wheelExt = ".whl"
        val parts = fileName
            .takeIf { it.endsWith(wheelExt, ignoreCase = true) }
            ?.dropLast(wheelExt.length)
            ?.split("-")
        if (parts == null || parts.size < 2) {
            return UItem.asSettingsCell(id, fileName, null)
        }
        return UItem.asSettingsCell(id, parts[0].replace('_', '-'), parts[1])
    }

    private fun spacer(heightDp: Float): UItem =
        UItem.asSpace(dp(heightDp)).also { it.transparent = true }

    private fun addSectionGap(items: ArrayList<UItem>) {
        if (items.lastOrNull()?.viewType != UniversalAdapter.VIEW_TYPE_SHADOW) {
            items.add(spacer(SECTION_GAP))
        }
    }

    override fun onViewCreated(containerView: FrameLayout) {
        super.onViewCreated(containerView)
        sheetContainer = containerView
    }

    override fun onPreDraw(canvas: Canvas, top: Int, progressToFullView: Float) {
        actionsPill?.let {
            val alpha = 1f - progressToFullView
            it.alpha = alpha
            for (i in 0 until it.childCount) {
                it.getChildAt(i).isClickable = alpha > 0.5f
            }
        }
    }

    private fun onRequirementAction(info: RequiredPluginInfo) {
        if (info.isSatisfied) {
            return
        }
        if (info.state == RequiredPluginInfo.STATE_DISABLED) {
            PluginsController.getInstance().setPluginEnabled(info.id, true) { error ->
                AndroidUtilities.runOnUIThread {
                    if (error == null) {
                        val installed = PluginsController.getInstance().plugins[info.id]
                        info.currentVersion = installed?.getVersion()
                        info.name = installed?.getName() ?: info.name
                        info.state = RequiredPluginInfo.STATE_SATISFIED
                        errorText = null
                        adapter?.update(true)
                        checkRequirementsSatisfied()
                    } else {
                        showError(error)
                    }
                }
            }
            return
        }

        val url = info.downloadUrl ?: return
        dismiss()
        Browser.openUrl(context, url)
    }

    private fun checkRequirementsSatisfied() {
        if (unknownSourceBlocked || !blocked || requirements.isEmpty() || requirements.any { !it.isSatisfied }) {
            return
        }
        blocked = false
        applyButtonState(true)
        enableAfterInstallRow?.visibility = View.VISIBLE
    }

    private fun startAnalysis() {
        val installedPath = if (isUpdate) PluginsController.getInstance().getPluginPath(plugin.getId()) else null
        val request = PluginAnalyzer.Request(
            filePath = params.filePath,
            name = File(params.filePath).name,
            previousPath = installedPath,
            useCache = params.trusted,
        )
        analysisTask = PluginAnalyzer.analyze(request) { result ->
            analysisTask = null
            analysisRunning = false
            analysisSnapshot = result.snapshot
            analysisSha256 = result.sha256
            analysisSection.setResult(result)
        }
    }

    private fun onAnalysisChanged() {
        applyButtonState(true)
        adapter?.update(true)
    }

    private fun openReport() {
        if (installing) {
            return
        }
        val confirmTitle = LocaleController.getString(
            if (isUpdate) R.string.PluginAnalysisConfirmUpdate else R.string.PluginAnalysisConfirmInstall
        )
        val acknowledge = {
            riskAcknowledged = true
            applyButtonState(true)
            onInstallClick()
        }
        if (needsRiskAcknowledgement() && !blocked) {
            if (analysisSection.hasReport) {
                analysisSection.openReport(fragment, confirmTitle, acknowledge)
            } else {
                AlertDialog.Builder(context, resourcesProvider).apply {
                    setTitle(LocaleController.getString(R.string.PluginAnalysisFailed))
                    setMessage(LocaleController.getString(R.string.PluginAnalysisIncompleteConfirmInfo))
                    setPositiveButton(confirmTitle) { _, _ -> acknowledge() }
                    setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                }.create().also {
                    it.show()
                    it.redPositive()
                }
            }
        } else {
            analysisSection.openReport(fragment, null, null)
        }
    }

    private fun needsRiskAcknowledgement(): Boolean =
        !params.trusted && !riskAcknowledged &&
            (analysisSection.maxSeverity >= AnalyzerReport.SEVERITY_CRITICAL || analysisSection.isIncomplete)

    private fun applyButtonState(animated: Boolean): Unit = button.run {
        updateColors()
        val disabledColor = ColorUtils.compositeColors(
            ColorUtils.setAlphaComponent(
                getThemedColor(Theme.key_featuredStickers_addButton),
                180
            ),
            getThemedColor(Theme.key_windowBackgroundWhite)
        )
        val installTitle = LocaleController.getString(if (isUpdate) R.string.UpdatePlugin else R.string.InstallPlugin)

        val blockedTitle = if (unknownSourceBlocked) {
            LocaleController.getString(R.string.PluginInstallBlocked)
        } else {
            params.blockedTitle
        }
        val blockedSubtitle = if (unknownSourceBlocked) {
            LocaleController.getString(R.string.PluginInstallBlockedInfo)
        } else {
            params.blockedSubtitle
        }
        if (blocked && blockedTitle != null) {
            setColor(disabledColor)
            setText(blockedTitle, animated)
            setSubText(blockedSubtitle, animated)
            setOnClickListener(null)
            isClickable = false
            isFocusable = false
            return@run
        }
        if (analysisRunning) {
            setColor(disabledColor)
            setText(LocaleController.getString(R.string.PluginAnalyzing), animated)
            setSubText(null, animated)
            setOnClickListener(null)
            isClickable = false
            isFocusable = false
            return@run
        }
        if (needsRiskAcknowledgement()) {
            setDangerColors(resourcesProvider)
            setText(
                LocaleController.getString(
                    when {
                        analysisSection.hasReport -> R.string.PluginAnalysisViewReport
                        isUpdate -> R.string.PluginAnalysisConfirmUpdate
                        else -> R.string.PluginAnalysisConfirmInstall
                    }
                ),
                animated
            )
            setSubText(null, animated)
            isFocusable = true
            setOnClickListener { openReport() }
            return@run
        }

        setColor(getThemedColor(Theme.key_featuredStickers_addButton))
        setText(installTitle, animated)
        setSubText(null, animated)
        isFocusable = true
        setOnClickListener { onInstallClick() }
    }

    private fun buildHeader(activity: Activity): View {
        val contentLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }

        val customIcon = params.iconDrawable
        if (customIcon != null || (plugin.getPack() != null && plugin.getIndex() >= 0)) {
            val imageContainer = FrameLayout(activity).apply {
                clipChildren = false
                clipToPadding = false
            }
            val imageView = BackupImageView(activity)
            imageView.outlineProvider = ViewOutlineProviderImpl.boundsWithRoundRect(dpf2(12f))
            imageView.clipToOutline = true
            imageView.imageReceiver.setAutoRepeat(1)
            imageContainer.addView(imageView, LayoutHelper.createFrame(78, 78, Gravity.CENTER))

            val badgeBorder = FrameLayout(activity).apply {
                background =
                    Theme.createCircleDrawable(
                        dp(28f),
                        getThemedColor(Theme.key_windowBackgroundGray)
                    )
            }
            val badgeFill = FrameLayout(activity).apply {
                background = Theme.createCircleDrawable(
                    dp(24f),
                    getThemedColor(Theme.key_featuredStickers_addButton)
                )
            }
            badgeBorder.addView(badgeFill, LayoutHelper.createFrame(24, 24, Gravity.CENTER))

            val badgeIcon = ImageView(activity).apply {
                setImageResource(R.drawable.plugins_filled)
                colorFilter = PorterDuffColorFilter(
                    getThemedColor(Theme.key_featuredStickers_buttonText),
                    PorterDuff.Mode.SRC_IN
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            badgeBorder.addView(badgeIcon, LayoutHelper.createFrame(16, 16, Gravity.CENTER))

            imageContainer.addView(
                badgeBorder,
                LayoutHelper.createFrame(28, 28f, Gravity.END or Gravity.BOTTOM, 0f, 0f, -4f, -4f)
            )
            contentLayout.addView(
                imageContainer,
                LayoutHelper.createLinear(78, 78, Gravity.CENTER_HORIZONTAL, 0f, 28f, 0f, 0f)
            )
            if (customIcon != null) {
                imageView.setImageDrawable(customIcon)
            } else {
                MediaDataController.getInstance(UserConfig.selectedAccount)
                    .setPlaceholderImageByIndex(
                        imageView,
                        plugin.getPack(),
                        plugin.getIndex(),
                        "150_150",
                        resourcesProvider
                    )
            }
        } else {
            val imageView = ImageView(activity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageResource(R.drawable.plugins_filled)
                colorFilter = PorterDuffColorFilter(
                    getThemedColor(Theme.key_featuredStickers_buttonText),
                    PorterDuff.Mode.SRC_IN
                )
                background = Theme.createCircleDrawable(
                    dp(78f),
                    getThemedColor(Theme.key_featuredStickers_addButton)
                )
                val p = dp(16f)
                setPadding(p, p, p, p)
            }
            contentLayout.addView(
                imageView,
                LayoutHelper.createLinear(78, 78, Gravity.CENTER_HORIZONTAL, 0f, 28f, 0f, 0f)
            )
        }

        val pluginNameView = object : TextView(activity) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                setMeasuredDimension(measuredWidth, nameText.getHeight(measuredHeight))
            }

            override fun onDraw(canvas: Canvas) {
                val saveCount = nameText.beforeDraw(canvas)
                super.onDraw(canvas)
                nameText.afterDraw(canvas, saveCount)
            }
        }.apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText))
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
            typeface = AndroidUtilities.bold()
            text = plugin.getName()
        }
        nameText.view = pluginNameView
        contentLayout.addView(
            pluginNameView,
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                0,
                40f,
                16f,
                40f,
                0f
            )
        )

        val subtitleView = EffectsTextView(activity, fragment.resourceProvider).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            typeface = AndroidUtilities.regular()
            movementMethod = AndroidUtilities.LinkMovementMethodMy()
            setLinkTextColor(getThemedColor(Theme.key_dialogTextLink))
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
            setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText))
        }
        var version =
            SpannableStringBuilder(LocaleController.getString(R.string.PluginVersion)).append(" ")
        val versionLength = version.length
        if (isUpdate) {
            val installedPlugin = PluginsController.getInstance().plugins[plugin.getId()]
            if (installedPlugin != null) {
                val oldVersion = installedPlugin.getVersion() ?: ""
                val newVersion = plugin.getVersion() ?: ""
                version.append(oldVersion).append(" -> ").append(newVersion)
                version = LocaleUtils.replaceArrows(context, version, R.drawable.msg_mini_arrow_mediathin)
                version.setSpan(
                    StrikethroughSpan(),
                    versionLength,
                    versionLength + oldVersion.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        } else {
            version.append(plugin.getVersion())
        }
        version.append(" • ")
            .append(LocaleUtils.formatWithUsernames(plugin.getAuthor(), fragment, ::dismiss))
        subtitleView.text = version
        subtitleView.setOnLinkLongPressListener { span -> showLinkMenu(subtitleView, span) }
        contentLayout.addView(
            subtitleView,
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                0,
                21f,
                4f,
                21f,
                0f
            )
        )

        val icon = if (params.trusted) R.drawable.trusted_mini else R.drawable.unknown_mini
        val color =
            getThemedColor(if (params.trusted) Theme.key_windowBackgroundWhiteGreenText else Theme.key_text_RedRegular)
        val text =
            if (params.trusted) LocaleController.getString(R.string.PluginSourceTrusted) else LocaleController.getString(
                R.string.PluginSourceUnknown
            )

        val trustedBadge = LinearLayout(activity).apply {
            ScaleStateListAnimator.apply(this, .05f, 1.5f)
            orientation = LinearLayout.HORIZONTAL
            background = Theme.createRoundRectDrawable(
                dp(20f),
                dp(20f),
                ColorUtils.compositeColors(
                    AndroidUtilities.multiplyAlphaComponent(color, 0.1f),
                    getThemedColor(Theme.key_windowBackgroundWhite)
                )
            )
            setPadding(dp(12f), dp(6f), dp(16f), dp(6f))
            gravity = Gravity.CENTER
        }

        val trustedBadgeIcon = ImageView(activity).apply {
            setImageResource(icon)
            colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
        trustedBadge.addView(
            trustedBadgeIcon,
            LayoutHelper.createLinear(14, 14, Gravity.CENTER_VERTICAL, 0f, 0f, 6f, 0f)
        )

        val trustedBadgeText = TextView(activity).apply {
            typeface = AndroidUtilities.regular()
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
            this.text = text
        }
        trustedBadge.addView(trustedBadgeText)
        contentLayout.addView(
            trustedBadge,
            LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER,
                0f,
                12f,
                0f,
                0f
            )
        )
        trustedBadge.setOnClickListener { view -> showSourceHint(view) }

        val iconColor = getThemedColor(Theme.key_windowBackgroundWhiteGrayIcon)
        val openIn = ImageView(activity).apply {
            ScaleStateListAnimator.apply(this, .15f, 1.5f)
            setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.msg_openin)?.mutate())
            colorFilter = PorterDuffColorFilter(iconColor, PorterDuff.Mode.MULTIPLY)
            scaleType = ImageView.ScaleType.CENTER
            setOnClickListener { openPluginFile() }
            background = Theme.createSelectorDrawable(
                getThemedColor(Theme.key_dialogButtonSelector), 1,
                dp(20f)
            )
        }
        val translate = PluginTranslateButton(activity, iconColor).apply {
            ScaleStateListAnimator.apply(this, .15f, 1.5f)
            visibility = View.GONE
            setOnClickListener { onTranslateClick() }
            background = Theme.createSelectorDrawable(
                getThemedColor(Theme.key_dialogButtonSelector), 1,
                dp(20f)
            )
        }
        translateButton = translate
        val pill = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            val padding = dp(2f)
            setPadding(padding, padding, padding, padding)
            background = BlurredBackgroundDrawable(GlassSourceColor().apply {
                setColor(getThemedColor(Theme.key_windowBackgroundGray))
            })
                .setColorProvider(BlurredBackgroundProviderImpl.topPanelChatActivity(resourcesProvider))
                .setRadius(dpf2(22f))
                .apply { setShadowAlpha(0f) }
            addView(translate, LayoutHelper.createLinear(40, 40))
            addView(openIn, LayoutHelper.createLinear(40, 40))
        }
        actionsPill = pill

        if (params.trusted && !preferences.getBoolean("trusted_source_hint", false) ||
            !params.trusted && !preferences.getBoolean("unknown_source_hint", false)
        ) {
            AndroidUtilities.runOnUIThread({
                trustedBadge.performClick()
                editor
                    .putBoolean(
                        if (params.trusted) "trusted_source_hint" else "unknown_source_hint",
                        true
                    )
                    .apply()
            }, 600)
        }

        return FrameLayout(activity).apply {
            clipChildren = false
            clipToPadding = false
            addView(
                contentLayout,
                LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT.toFloat()
                )
            )
            addView(
                pill,
                LayoutHelper.createFrame(
                    LayoutHelper.WRAP_CONTENT,
                    44f,
                    Gravity.RIGHT or Gravity.TOP,
                    0f,
                    14f,
                    12f,
                    0f
                )
            )
        }
    }

    private fun buildDescription(activity: Activity): View {
        return object : EffectsTextView(activity) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                setMeasuredDimension(measuredWidth, descriptionText.getHeight(measuredHeight))
            }

            override fun onDraw(canvas: Canvas) {
                val saveCount = descriptionText.beforeDraw(canvas)
                super.onDraw(canvas)
                descriptionText.afterDraw(canvas, saveCount)
            }
        }.apply {
            gravity = Gravity.LEFT
            typeface = AndroidUtilities.regular()
            movementMethod = AndroidUtilities.LinkMovementMethodMy()
            setLinkTextColor(getThemedColor(Theme.key_dialogTextLink))
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
            setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText))
            text = LocaleUtils.fullyFormatText(plugin.getDescription(), fragment, ::dismiss)
            setPadding(dp(16f), dp(14f), dp(16f), dp(12F))
            setOnLinkLongPressListener { span -> showLinkMenu(this, span) }
        }.also { descriptionText.view = it }
    }

    private fun showLinkMenu(textView: TextView, span: ClickableSpan) {
        val url = (span as? URLSpan)?.url ?: span.toString()
        textView.performHapticFeedback(
            HapticFeedbackConstants.LONG_PRESS,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
        BottomSheet.Builder(textView.context)
            .setTitle(url)
            .setItems(
                arrayOf(
                    LocaleController.getString(R.string.Open),
                    LocaleController.getString(R.string.Copy)
                )
            ) { _, which ->
                if (which == 0) {
                    span.onClick(textView)
                } else if (which == 1) {
                    AndroidUtilities.addToClipboard(url)
                    if (AndroidUtilities.shouldShowClipboardToast()) {
                        val text = LocaleController.getString(
                            when {
                                url.startsWith("@") -> R.string.UsernameCopied
                                url.startsWith("#") || url.startsWith("$") -> R.string.HashtagCopied
                                else -> R.string.LinkCopied
                            }
                        )
                        BulletinFactory.of(topBulletinContainer, resourcesProvider)
                            .createSimpleBulletin(R.raw.copy, text)
                            .show()
                    }
                }
            }
            .show()
    }

    private fun buildError(activity: Activity): View {
        val red = getThemedColor(Theme.key_text_RedRegular)
        val radius = dp(getSectionRadiusDp().toFloat())
        val plate = TextView(activity).apply {
            typeface = AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
            setTextColor(red)
            setPadding(dp(16f), dp(14f), dp(16f), dp(12F))
            background = Theme.createRoundRectDrawable(
                radius,
                radius,
                ColorUtils.compositeColors(
                    AndroidUtilities.multiplyAlphaComponent(red, 0.1f),
                    getThemedColor(Theme.key_windowBackgroundWhite)
                )
            )
            ScaleStateListAnimator.apply(this, .02f, 1.5f)
            setOnClickListener {
                val error = errorText ?: return@setOnClickListener
                if (AndroidUtilities.addToClipboard(error)) {
                    BulletinFactory.of(topBulletinContainer, resourcesProvider)
                        .createCopyBulletin(LocaleController.getString(R.string.TextCopied))
                        .show()
                }
            }
        }
        errorTextView = plate
        return FrameLayout(activity).apply {
            setPadding(dp(SECTION_GAP), 0, dp(SECTION_GAP), dp(SECTION_GAP))
            addView(
                plate,
                LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT.toFloat()
                )
            )
        }
    }

    private fun showError(message: String) {
        val text = message.trim()
        val appearing = errorText == null
        errorText = text
        errorTextView?.text = text

        adapter?.update(true)
        if (appearing) {
            scrollToError()
        }
    }

    private fun scrollToError() {
        val count = recyclerListView.adapter?.itemCount ?: return
        recyclerListView.smoothScrollToPosition(count - 1)
    }

    private fun showSourceHint(view: View) {
        currentHint?.hide()
        currentHint = null

        val hintText =
            LocaleController.getString(if (params.trusted) R.string.PluginSourceTrustedInfo else R.string.PluginSourceUnknownInfo)

        val hintView = HintView2(context, HintView2.DIRECTION_BOTTOM)
            .setMultilineText(true)
            .setBgColor(getThemedColor(Theme.key_undo_background))
            .setTextColor(getThemedColor(Theme.key_undo_infoColor))
            .setText(AndroidUtilities.replaceTags(hintText))
            .setTextAlign(Layout.Alignment.ALIGN_CENTER)
            .allowBlur(true)
            .setRounding(12f)
        hintView.setMaxWidthPx(HintView2.cutInFancyHalf(hintView.text, hintView.textPaint))

        container.addView(
            hintView,
            LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                100f,
                Gravity.TOP or Gravity.FILL_HORIZONTAL,
                32f,
                0f,
                32f,
                0f
            )
        )
        currentHint = hintView

        container.post {
            val badgeLoc = IntArray(2)
            view.getLocationInWindow(badgeLoc)

            val containerLoc = IntArray(2)
            container.getLocationInWindow(containerLoc)

            badgeLoc[0] -= containerLoc[0]
            badgeLoc[1] -= containerLoc[1]

            val yPos = badgeLoc[1] - dp(100f) - dp(6f)
            val jointX = badgeLoc[0] + view.measuredWidth / 2f

            hintView.translationY = yPos.toFloat()
            hintView.setJointPx(0f, (-dp(32f) + jointX))
            hintView.setDuration(5500)
            hintView.show()
        }
    }

    private fun createEnableAfterInstallRow(activity: Activity): View =
        CheckBoxRow(
            activity,
            LocaleController.getString(R.string.EnableAfterInstallation),
            enableAfterInstallation,
            resourcesProvider,
        ).apply {
            onCheckedChange = { enableAfterInstallation = it }
        }

    private fun openPluginFile() {
        if (installing) {
            return
        }
        val file = File(params.filePath)
        if (!file.exists()) {
            return
        }
        if (PluginFileViewer.canOpen(file)) {
            val fileName = file.name.takeIf { it.contains('.') }
                ?: (plugin.getId() + PluginsConstants.PLUGINS_EXT)
            if (PluginFileViewer.open(fragment, file, fileName)) {
                dismiss()
            }
        } else {
            PluginFileViewer.openExternally(fragment, file, file.name)
        }
    }

    private fun checkTranslatable() {
        if (!LanguageDetector.hasSupport()) {
            return
        }
        for (text in arrayOf(nameText, descriptionText)) {
            val original = text.original
            if (original.isNullOrBlank()) {
                continue
            }
            LanguageDetector.detectLanguage(original, { language ->
                AndroidUtilities.runOnUIThread {
                    if (!language.isNullOrEmpty() && language != TranslateController.UNKNOWN_LANGUAGE && !TranslatorUtils.isRestrictedLanguage(language)) {
                        text.language = language
                        translateButton?.visibility = View.VISIBLE
                    }
                }
            }, null)
        }
    }

    private fun onTranslateClick() {
        val button = translateButton ?: return
        if (translating) {
            return
        }
        if (button.state == PluginTranslateButton.STATE_RESET) {
            showTexts(false)
            button.setState(PluginTranslateButton.STATE_TRANSLATE)
            return
        }

        val texts = arrayOf(nameText, descriptionText).filter { it.language != null && it.translation == null }
        if (texts.isEmpty()) {
            showTexts(true)
            button.setState(PluginTranslateButton.STATE_RESET)
            return
        }

        translating = true
        button.setState(PluginTranslateButton.STATE_LOADING)
        val targetLanguage = TranslatorUtils.getResolvedTargetLanguageCode()
        var pending = texts.size
        var failed = false
        for (text in texts) {
            text.startLoading()
            translateText(text.original.orEmpty(), text.language ?: "auto", targetLanguage) { result ->
                if (result == null) {
                    failed = true
                } else {
                    text.translation = result
                }
                if (--pending == 0) {
                    onTranslationFinished(texts, !failed)
                }
            }
        }
    }

    private fun translateText(text: String, fromLanguage: String, toLanguage: String, done: (String?) -> Unit) {
        var finished = false
        val finish = { result: String? ->
            AndroidUtilities.runOnUIThread {
                if (!finished) {
                    finished = true
                    done(result)
                }
            }
        }
        TranslatorUtils.translate(text, fromLanguage, toLanguage, object : TranslatorUtils.TranslateCallback {
            override fun onSuccess(translatedText: String?) {
                finish(translatedText?.takeIf { it.isNotBlank() })
            }

            override fun onFailed() {
                finish(null)
            }
        })
    }

    private fun onTranslationFinished(texts: List<TranslatableText>, success: Boolean) {
        translating = false
        if (success) {
            showTexts(true)
            translateButton?.setState(PluginTranslateButton.STATE_RESET)
        } else {
            translateButton?.setState(PluginTranslateButton.STATE_TRANSLATE)
            if (isShowing) {
                BulletinFactory.of(topBulletinContainer, resourcesProvider)
                    .createErrorBulletin(LocaleController.getString(R.string.TranslationFailedAlert2))
                    .show()
            }
        }
        texts.forEach { it.stopLoading() }
    }

    private fun showTexts(translated: Boolean) {
        nameText.show(translated)
        descriptionText.show(translated)
        titleText = if (translated) nameText.translation ?: nameText.original else nameText.original
        updateTitle()
    }

    private fun onTextTransitionUpdate() {
        containerView?.invalidate()
    }

    private fun onInstallClick() {
        if (installing) {
            if (!cancellationRequested) {
                cancellationRequested = true
                button.isLoading = true
                button.setSubText(null, true)
            }
            return
        }
        if (analysisRunning || needsRiskAcknowledgement()) {
            return
        }

        val engine = PluginsController.getEngines()[
            plugin.getEngine() ?: PluginsConstants.PYTHON
        ] ?: return

        installing = true
        cancellationRequested = false

        setCanDismissWithSwipe(false)
        setCanDismissWithTouchOutside(false)

        delayedLoadingRunnable?.let(AndroidUtilities::cancelRunOnUIThread)
        delayedLoadingRunnable = Runnable { button.isLoading = true }
        AndroidUtilities.runOnUIThread(delayedLoadingRunnable!!, 250)

        val expected = analysisSha256
        if (expected == null) {
            install(engine, params.filePath)
            return
        }
        val snapshot = analysisSnapshot
        Utilities.globalQueue.postRunnable {
            val copy = PluginAnalyzer.installCopy(snapshot, params.filePath, expected)
            AndroidUtilities.runOnUIThread {
                when {
                    dismissed -> if (copy != snapshot) copy?.delete()
                    copy == null -> restartAnalysis()
                    else -> {
                        if (copy != snapshot) {
                            snapshot?.delete()
                            analysisSnapshot = copy
                        }
                        install(engine, copy.absolutePath)
                    }
                }
            }
        }
    }

    private fun restartAnalysis() {
        delayedLoadingRunnable?.let(AndroidUtilities::cancelRunOnUIThread)
        delayedLoadingRunnable = null
        button.isLoading = false
        setCancelable(true)
        setCanDismissWithSwipe(true)
        setCanDismissWithTouchOutside(true)
        installing = false
        cancellationRequested = false
        riskAcknowledged = false
        analysisSnapshot?.delete()
        analysisSnapshot = null
        analysisSha256 = null
        analysisRunning = true
        analysisSection.setRunning()
        startAnalysis()
    }

    private fun install(engine: PluginsController.PluginsEngine, installPath: String) {
        engine.loadPluginFromFile(installPath, plugin, { loadError ->
            AndroidUtilities.runOnUIThread {
                delayedLoadingRunnable?.let(AndroidUtilities::cancelRunOnUIThread)
                delayedLoadingRunnable = null
                button.isLoading = false
                setCancelable(true)
                setCanDismissWithSwipe(true)
                setCanDismissWithTouchOutside(true)
                installing = false

                if (cancellationRequested) {
                    cancellationRequested = false
                    applyButtonState(true)
                    return@runOnUIThread
                }

                if (loadError != null) {
                    applyButtonState(true)
                    if (!loadError.contains("Installation cancelled")) {
                        showError(loadError)
                    }
                    return@runOnUIThread
                }
                if (enableAfterInstallation && !pluginsSafeMode) {
                    installing = true
                    setCanDismissWithSwipe(false)
                    setCanDismissWithTouchOutside(false)
                    button.isLoading = true

                    PluginsController.getInstance()
                        .setPluginEnabled(plugin.getId(), true) { enableError ->
                            AndroidUtilities.runOnUIThread {
                                installing = false
                                setCanDismissWithSwipe(true)
                                setCanDismissWithTouchOutside(true)
                                button.isLoading = false

                                if (enableError != null) {
                                    applyButtonState(true)
                                    showError(enableError)
                                    return@runOnUIThread
                                }
                                dismiss()
                                showSuccessBulletin(fragment, plugin)
                            }
                        }
                    return@runOnUIThread
                }
                dismiss()
                showSuccessBulletin(fragment, plugin)
            }
        }, object : PipController.InstallerDelegate {
            override fun onProgress(text: String) {
                AndroidUtilities.runOnUIThread {
                    delayedLoadingRunnable?.let(AndroidUtilities::cancelRunOnUIThread)
                    delayedLoadingRunnable = null
                    if (cancellationRequested) {
                        return@runOnUIThread
                    }
                    button.isLoading = false
                    button.setText(LocaleController.getString(R.string.Cancel), true)
                    button.setSubText(text, true)
                }
            }

            override fun isCancelled(): Boolean = cancellationRequested
        })
    }

    private fun showSuccessBulletin(fragment: BaseFragment, plugin: Plugin) {
        val bf = BulletinFactory.of(fragment)
        val name = plugin.getName()

        if (plugin.getPack() != null && plugin.getIndex() >= 0) {
            val inputStickerSet = TLRPC.TL_inputStickerSetShortName().apply {
                short_name = plugin.getPack()
            }

            val bulletinHasBeenShown = AtomicBoolean(false)

            val fallbackRunnable = Runnable {
                if (!bulletinHasBeenShown.getAndSet(true)) {
                    showSimpleSuccessBulletin(plugin, bf)
                }
            }

            AndroidUtilities.runOnUIThread(fallbackRunnable, 300)

            MediaDataController.getInstance(UserConfig.selectedAccount)
                .getStickerSet(inputStickerSet, 0, true) { set ->
                    AndroidUtilities.runOnUIThread {
                        if (bulletinHasBeenShown.get()) {
                            return@runOnUIThread
                        }

                        var document: TLRPC.Document? = null
                        if (set?.documents != null && set.documents.isNotEmpty()) {
                            val index = plugin.getIndex()
                            if (index >= 0 && index < set.documents.size) {
                                document = set.documents[index]
                            }
                        }

                        if (document != null && !bulletinHasBeenShown.getAndSet(true)) {
                            AndroidUtilities.cancelRunOnUIThread(fallbackRunnable)
                            val text = AndroidUtilities.replaceTags(
                                LocaleController.formatString(
                                    if (isUpdate) R.string.PluginUpdated else R.string.PluginInstalled,
                                    name
                                )
                            )
                            val installedPlugin =
                                PluginsController.getInstance().plugins[plugin.getId()]
                            val bulletin =
                                if (installedPlugin != null && PluginsController.getInstance()
                                        .hasPluginSettings(plugin.getId()) && installedPlugin.isEnabled()
                                ) {
                                    bf.createEmojiBulletin(
                                        document,
                                        text,
                                        LocaleController.getString(R.string.Settings),
                                    ) { PluginsController.openPluginSettings(plugin.getId()) }
                                } else {
                                    bf.createSimpleBulletin(document, text)
                                }
                            bulletin.show()
                        }
                    }
                }
        } else {
            showSimpleSuccessBulletin(plugin, bf)
        }
    }

    private fun showSimpleSuccessBulletin(plugin: Plugin, bf: BulletinFactory) {
        val name = plugin.getName()
        val text = LocaleController.formatString(
            if (isUpdate) R.string.PluginUpdated else R.string.PluginInstalled,
            name
        )
        val installedPlugin = PluginsController.getInstance().plugins[plugin.getId()]

        if (installedPlugin != null && PluginsController.getInstance()
                .hasPluginSettings(plugin.getId()) && installedPlugin.isEnabled()
        ) {
            bf.createSimpleBulletin(
                R.raw.contact_check,
                text,
                LocaleController.getString(R.string.Settings)
            ) {
                PluginsController.openPluginSettings(plugin.getId())
            }.show()
        } else {
            bf.createSimpleBulletin(R.raw.contact_check, text).show()
        }
    }

    override fun dismiss() {
        currentHint?.hide()
        currentHint = null
        analysisTask?.cancel()
        analysisTask = null
        analysisSnapshot?.delete()
        analysisSnapshot = null
        dismissed = true
        super.dismiss()
    }

    override fun onSwipeStarts() {
        currentHint?.hide()
        currentHint = null
    }

    class PluginInstallParams(
        val filePath: String,
        source: MessageObject?,
    ) {
        @JvmOverloads
        constructor(filePath: String, @Suppress("UNUSED_PARAMETER") trusted: Boolean = false) : this(filePath, null as MessageObject?)

        val trusted: Boolean = source != null && isTrustedSource(source) && isMessageFile(source, filePath)

        var iconDrawable: Drawable? = null

        var requiredPlugins: List<RequiredPluginInfo>? = null
        var bundledPackages: List<String>? = null

        var blockedTitle: String? = null
        var blockedSubtitle: String? = null

        companion object {
            @JvmStatic
            @JvmOverloads
            fun of(messageObject: MessageObject, path: String? = null): PluginInstallParams =
                PluginInstallParams(path ?: ChatUtils.getInstance().getPathToMessage(messageObject), messageObject)

            @JvmStatic
            fun untrusted(filePath: String): PluginInstallParams = PluginInstallParams(filePath)

            private fun isTrustedSource(messageObject: MessageObject): Boolean {
                val channelId = if (messageObject.isForwarded) {
                    messageObject.forwardedFromId?.let { -it } ?: return false
                } else if (messageObject.isFromChannel && !messageObject.isFromChat) {
                    -messageObject.dialogId
                } else {
                    return false
                }
                return BadgesController.isTrusted(channelId) || BadgesController.isExtera(channelId)
            }

            private fun isMessageFile(messageObject: MessageObject, filePath: String?): Boolean {
                val target = canonicalPath(filePath) ?: return false
                val loader = FileLoader.getInstance(messageObject.currentAccount)
                val document = messageObject.document
                val candidates = mutableListOf(
                    ChatUtils.getInstance().getPathToMessage(messageObject),
                    loader.getPathToMessage(messageObject.messageOwner)?.path,
                    document?.let { loader.getPathToAttach(it, true)?.path },
                    document?.let { loader.getPathToAttach(it, false)?.path },
                )
                messageObject.messageOwner?.rich_message?.documents?.forEach {
                    candidates.add(loader.getPathToAttach(it, true)?.path)
                    candidates.add(loader.getPathToAttach(it, false)?.path)
                }
                return candidates.any { canonicalPath(it) == target }
            }

            private fun canonicalPath(path: String?): String? =
                path?.takeIf { it.isNotEmpty() }?.let { runCatching { File(it).canonicalPath }.getOrNull() }
        }
    }

    companion object {
        private const val SECTION_GAP = 12f
    }
}

private class TranslatableText(
    val original: String?,
    private val format: (String?) -> CharSequence?,
    private val onTransitionUpdate: () -> Unit,
) {
    var view: TextView? = null
    var language: String? = null
    var translation: String? = null
    private var shownText = original

    private val loadingPath = LinkPath(true)
    private val loadingDrawable = LoadingDrawable().apply {
        usePath(loadingPath)
        setAppearByGradient(true)
        setRadiiDp(4f)
    }
    private var loadingLayout: Layout? = null
    private var loading = false

    private var oldLayout: Layout? = null
    private var oldHeight = 0
    private var transitionProgress = 1f
    private var transitionAnimator: ValueAnimator? = null

    fun startLoading() {
        loading = true
        loadingDrawable.reset()
        loadingDrawable.resetDisappear()
        view?.invalidate()
    }

    fun stopLoading() {
        loadingDrawable.disappear()
        view?.invalidate()
    }

    fun show(translated: Boolean) {
        val view = view ?: return
        val text = if (translated) translation ?: original else original
        if (text == shownText) {
            return
        }
        shownText = text
        transitionAnimator?.cancel()
        oldLayout = view.layout
        oldHeight = view.height
        view.text = format(text)
        transitionProgress = 0f
        transitionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 350
            interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
            addUpdateListener {
                transitionProgress = it.animatedValue as Float
                view.requestLayout()
                view.invalidate()
                onTransitionUpdate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    oldLayout = null
                    transitionProgress = 1f
                    transitionAnimator = null
                    view.requestLayout()
                    view.invalidate()
                }
            })
            start()
        }
    }

    fun getHeight(measuredHeight: Int): Int =
        if (oldLayout == null) measuredHeight else AndroidUtilities.lerp(oldHeight, measuredHeight, transitionProgress)

    fun beforeDraw(canvas: Canvas): Int {
        val view = view ?: return -1
        val layout = oldLayout ?: return -1
        canvas.save()
        canvas.clipRect(0, 0, view.width, view.height)
        canvas.translate(view.totalPaddingLeft.toFloat(), view.totalPaddingTop.toFloat())
        canvas.saveLayerAlpha(0f, 0f, layout.width.toFloat(), layout.height.toFloat(), (255 * (1f - transitionProgress)).toInt())
        layout.draw(canvas)
        canvas.restore()
        canvas.restore()
        return canvas.saveLayerAlpha(0f, 0f, view.width.toFloat(), view.height.toFloat(), (255 * transitionProgress).toInt())
    }

    fun afterDraw(canvas: Canvas, saveCount: Int) {
        if (saveCount >= 0) {
            canvas.restoreToCount(saveCount)
        }
        drawLoading(canvas)
    }

    private fun drawLoading(canvas: Canvas) {
        val view = view ?: return
        if (!loading) {
            return
        }
        if (loadingDrawable.isDisappeared) {
            loading = false
            return
        }
        val layout = view.layout ?: return
        if (layout !== loadingLayout) {
            loadingLayout = layout
            loadingPath.rewind()
            loadingPath.setCurrentLayout(layout, 0, view.totalPaddingLeft.toFloat(), view.totalPaddingTop.toFloat())
            layout.getSelectionPath(0, layout.text.length, loadingPath)
            loadingDrawable.updateBounds()
        }
        val color = view.currentTextColor
        loadingDrawable.setColors(
            Theme.multAlpha(color, .05f),
            Theme.multAlpha(color, .15f),
            Theme.multAlpha(color, .1f),
            Theme.multAlpha(color, .3f)
        )
        loadingDrawable.draw(canvas)
        view.invalidate()
    }
}

internal fun pluginSheetHeader(text: CharSequence): UItem =
    UItem.asHeader(text).onBind { view ->
        val cell = view as? HeaderCell ?: return@onBind
        val textView = cell.textView
        val params = textView.layoutParams as? FrameLayout.LayoutParams ?: return@onBind
        val topMargin = dp(6F)
        val minHeight = dp(34F)
        if (params.topMargin != topMargin || textView.minHeight != minHeight) {
            params.topMargin = topMargin
            textView.minHeight = minHeight
            cell.requestLayout()
        }
    }
