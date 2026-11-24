/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.ui

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.chaquo.python.PyObject
import com.exteragram.messenger.plugins.Plugin
import com.exteragram.messenger.plugins.PluginsConstants
import com.exteragram.messenger.plugins.PluginsController
import com.exteragram.messenger.plugins.PythonPluginsEngine
import com.exteragram.messenger.plugins.models.CustomSetting
import com.exteragram.messenger.plugins.models.DividerSetting
import com.exteragram.messenger.plugins.models.EditTextSetting
import com.exteragram.messenger.plugins.models.HeaderSetting
import com.exteragram.messenger.plugins.models.InputSetting
import com.exteragram.messenger.plugins.models.SelectorSetting
import com.exteragram.messenger.plugins.models.SettingItem
import com.exteragram.messenger.plugins.models.SwitchSetting
import com.exteragram.messenger.plugins.models.TextSetting
import com.exteragram.messenger.plugins.ui.components.PluginEditTextCell
import com.exteragram.messenger.plugins.ui.components.PluginTranslateButton
import com.exteragram.messenger.preferences.BasePreferencesActivity
import com.exteragram.messenger.preferences.utils.SettingsRegistry
import com.exteragram.messenger.utils.text.LocaleUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController.formatString
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.ActionBarMenu
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.NotificationsCheckCell
import org.telegram.ui.Cells.RadioColorCell
import org.telegram.ui.Cells.TextCell
import org.telegram.ui.Cells.TextCheckCell
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import java.util.Objects

class PluginSettingsActivity : BasePreferencesActivity,
    NotificationCenter.NotificationCenterDelegate, PluginSettingsTranslation.Listener {

    private lateinit var plugin: Plugin
    private var customTitle: String? = null
    private var settingItems: List<SettingItem>? = null
    private var createSubFragmentCallback: PyObject? = null
    private var targetSettingName: String? = null
    private var resetItem: ActionBarMenuItem? = null
    private var targetSettingItemId: Int? = null
    private var settingsLinkPrefix: String? = null
    private val translation by lazy { PluginSettingsTranslation.of(plugin.getId()) }
    private val visibleTexts = LinkedHashSet<String>()
    private var translateItem: ActionBarMenuItem? = null
    private var translateButton: PluginTranslateButton? = null

    constructor() : super()

    @JvmOverloads
    constructor(
        plugin: Plugin,
        targetSettingName: String? = null
    ) : this() {
        this.plugin = plugin
        this.targetSettingName = targetSettingName
    }

    @JvmOverloads
    constructor(
        plugin: Plugin,
        title: String?,
        settingsList: List<SettingItem>?,
        createSubFragmentCallback: PyObject?,
        targetSettingName: String? = null
    ) : this() {
        this.plugin = plugin
        this.customTitle = title
        this.settingItems = settingsList
        this.createSubFragmentCallback = createSubFragmentCallback
        this.targetSettingName = targetSettingName
    }

    fun setSettingsLinkPrefix(settingsLinkPrefix: String?): PluginSettingsActivity = apply {
        this.settingsLinkPrefix = settingsLinkPrefix
    }

    override fun getTitle(): String =
        translation.text(customTitle ?: plugin.getName()).orEmpty()

    override fun onFragmentCreate(): Boolean {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.pluginSettingsRegistered)
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.pluginSettingsUnregistered)
        translation.addListener(this)
        translation.resetFailed()
        return super.onFragmentCreate()
    }

    override fun onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.pluginSettingsRegistered)
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.pluginSettingsUnregistered)
        translation.removeListener(this)
        super.onFragmentDestroy()
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        when (id) {
            NotificationCenter.pluginSettingsRegistered -> {
                val updatedPluginId = args.firstOrNull() as? String
                if (updatedPluginId != null && plugin.getId() != updatedPluginId) return

                val callback = createSubFragmentCallback
                if (callback != null) {
                    PluginsController.runOnPluginsQueue {
                        if (!PluginsController.getInstance().isPluginActive(plugin)) return@runOnPluginsQueue
                        val subSettingsList = try {
                            val pySubDefinitions = callback.call() ?: return@runOnPluginsQueue
                            val engine = PluginsController.getEngines()[PluginsConstants.PYTHON] as? PythonPluginsEngine
                                ?: return@runOnPluginsQueue
                            engine.parsePySettingDefinitions(pySubDefinitions.asList())
                        } catch (_: Exception) {
                            return@runOnPluginsQueue
                        }

                        AndroidUtilities.runOnUIThread {
                            settingItems = subSettingsList
                            listView.adapter?.update(true)
                        }
                    }
                } else {
                    listView.adapter?.update(true)
                    resetItem?.let {
                        AndroidUtilities.updateViewVisibilityAnimated(
                            it,
                            PluginsController.getInstance().hasPluginSettingsPreferences(plugin.getId()),
                            0.5f,
                            true,
                            1f,
                            true
                        ) { actionBar?.requestLayout() }
                    }
                }
            }

            NotificationCenter.pluginSettingsUnregistered -> {
                val unregisteredPluginId = args.firstOrNull() as? String ?: return
                if (plugin.getId() == unregisteredPluginId && !PluginsController.getInstance().hasPluginSettings(plugin.getId())) {
                    finishFragment()
                }
            }
        }
    }

    override fun createView(context: Context): View {
        val contentView = super.createView(context)

        val menu: ActionBarMenu = actionBar.createMenu()
        translateItem = menu.addItem(1, 0).apply {
            val button = PluginTranslateButton(context, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider))
            addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
            translateButton = button
            contentDescription = button.contentDescription
            AndroidUtilities.updateViewVisibilityAnimated(this, false, 0.5f, false)
            setOnClickListener { onTranslateClick() }
        }

        if (createSubFragmentCallback == null) {
            resetItem = menu.addItem(0, R.drawable.msg_reset).apply {
                contentDescription = getString(R.string.Reset)
                AndroidUtilities.updateViewVisibilityAnimated(
                    this,
                    PluginsController.getInstance().hasPluginSettingsPreferences(plugin.getId()),
                    0.5f,
                    false
                )
                tag = null
                setOnClickListener {
                    val dialog = AlertDialog.Builder(getContext(), resourceProvider).apply {
                        setTitle(getString(R.string.ResetSettings))
                        setMessage(AndroidUtilities.replaceTags(formatString(R.string.ResetPluginSettingsInfo, plugin.getName())))
                        setPositiveButton(getString(R.string.Reset)) { _, _ ->
                            fragmentView?.findFocus()?.clearFocus()
                            AndroidUtilities.updateViewVisibilityAnimated(it, false, 0.5f, true, 1f, true) {
                                actionBar?.requestLayout()
                            }
                            PluginsController.runOnPluginsQueue {
                                PluginsController.getInstance().clearPluginSettingsPreferences(plugin.getId())
                                PluginsController.getInstance().loadPluginSettings(plugin.getId())
                                AndroidUtilities.runOnUIThread {
                                    BulletinFactory.of(this@PluginSettingsActivity).createSimpleBulletin(
                                        R.raw.info,
                                        formatString(R.string.ResetPluginSettings, plugin.getName())
                                    ).show()
                                }
                            }
                        }
                        setNegativeButton(getString(R.string.Cancel), null)
                    }.create()

                    showDialog(dialog)
                    (dialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView)
                        ?.setTextColor(Theme.getColor(Theme.key_text_RedBold))
                }
            }
        }

        checkTargetSetting()
        updateTranslateItem(false)
        fragmentView = contentView
        return contentView
    }

    override fun onTranslationChanged(failed: Boolean) {
        listView.adapter?.update(true)
        actionBar?.setTitle(getTitle())
        updateTranslateItem(true)
        if (failed && isLastFragment)
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.TranslationFailedAlert2)).show()
    }

    private fun onTranslateClick() {
        if (!translation.isLoading)
            translation.setEnabled(!translation.enabled)
    }

    private fun updateTranslateItem(animated: Boolean) {
        AndroidUtilities.updateViewVisibilityAnimated(
            translateItem,
            translation.isTranslatable || translation.enabled,
            0.5f,
            true,
            1f,
            animated
        ) { actionBar?.requestLayout() }
        translateButton?.let {
            it.setState(
                when {
                    !translation.enabled -> PluginTranslateButton.STATE_TRANSLATE
                    translation.isLoading -> PluginTranslateButton.STATE_LOADING
                    else -> PluginTranslateButton.STATE_RESET
                }
            )
            translateItem?.contentDescription = it.contentDescription
        }
    }

    private fun tr(text: String?): String? {
        text?.let(visibleTexts::add)
        return translation.text(text)
    }

    fun checkTargetSetting() {
        targetSettingItemId?.let {
            scrollToItem(it)
            targetSettingItemId = null
        }
    }

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        val javaSettings = settingItems
            ?: PluginsController.getInstance().getPluginSettingsList(plugin.getId())
        if (javaSettings.isNullOrEmpty()) return

        visibleTexts.clear()
        tr(customTitle ?: plugin.getName())

        var found = false
        for (settingDef in javaSettings) {
            val iconResId = settingDef.icon
                ?.takeIf { it.isNotEmpty() }
                ?.let { iconName ->
                    val context = ApplicationLoader.applicationContext
                    context.resources.getIdentifier(iconName, "drawable", context.packageName)
                } ?: 0

            val item = try {
                when (settingDef) {
                    is HeaderSetting -> {
                        settingDef.text?.let { text ->
                            UItem.asHeader(tr(text)).apply {
                                settingItem = settingDef
                            }
                        }
                    }

                    is SwitchSetting -> {
                        val key = settingDef.key
                        val text = settingDef.text

                        if (key != null && text != null) {
                            val currentValue = PluginsController.getInstance()
                                .getPluginSettingBoolean(plugin.getId(), key, settingDef.defaultValue)

                            UItem.asCheck(0, tr(text)).apply {
                                setChecked(currentValue)
                                drawLine = false
                                settingDef.subtext?.let { subtext ->
                                    textValue = tr(subtext)
                                    multiline = true
                                }
                                if (iconResId != 0)
                                    this.iconResId = iconResId
                                object2 = key
                                settingItem = settingDef
                            }
                        } else null
                    }

                    is InputSetting -> {
                        val key = settingDef.key
                        val text = settingDef.text

                        if (key != null && text != null) {
                            val currentValue = PluginsController.getInstance()
                                .getPluginSettingString(plugin.getId(), key, settingDef.defaultValue)

                            UItem.asButton(0, tr(text), currentValue).apply {
                                tr(settingDef.subtext)
                                if (iconResId != 0)
                                    this.iconResId = iconResId
                                object2 = key
                                settingItem = settingDef
                            }
                        } else null
                    }

                    is EditTextSetting -> {
                        if (settingDef.key != null && settingDef.hint != null) {
                            PluginEditTextCell.Factory.`as`(plugin, settingDef).apply {
                                text = tr(settingDef.hint)
                            }
                        } else null
                    }

                    is SelectorSetting -> {
                        val key = settingDef.key
                        val text = settingDef.text

                        if (key != null && text != null && settingDef.items.isNotEmpty()) {
                            var currentValue = PluginsController.getInstance().getPluginSettingInt(plugin.getId(), key, settingDef.defaultValue)
                            if (currentValue !in settingDef.items.indices) {
                                currentValue = settingDef.defaultValue.coerceIn(settingDef.items.indices)
                                PluginsController.getInstance().setPluginSetting(plugin.getId(), key, currentValue)
                            }

                            val selectorItems = settingDef.items.map { tr(it).orEmpty() }.toTypedArray()
                            UItem.asButton(0, tr(text), selectorItems[currentValue]).apply {
                                texts = selectorItems
                                intValue = currentValue
                                if (iconResId != 0)
                                    this.iconResId = iconResId
                                object2 = key
                                settingItem = settingDef
                            }
                        } else null
                    }

                    is TextSetting -> {
                        UItem.asButton(0, tr(settingDef.text)).apply {
                            settingItem = settingDef
                            if (iconResId != 0)
                                this.iconResId = iconResId
                            accent = settingDef.accent
                            red = settingDef.red
                            if (!settingDef.subtext.isNullOrEmpty()) {
                                subtext = tr(settingDef.subtext)
                                intValue = 60
                            }
                        }
                    }

                    is DividerSetting -> {
                        val formattedText = tr(settingDef.text)?.let {
                            LocaleUtils.fullyFormatText(it, this, null)
                        } ?: ""
                        UItem.asShadow(formattedText)
                    }

                    is CustomSetting -> {
                        val custom = settingDef.item ?: settingDef.factory?.create(plugin, settingDef, settingDef.factoryArgs)
                        custom?.apply {
                            settingItem = settingDef
                        }
                    }

                    else -> null
                }
            } catch (e: Exception) {
                Log.e("PluginSettings", "Error creating item", e)
                null
            }

            if (item != null) {
                item.id = getStableId(settingDef)
                val linkAlias = item.settingItem?.linkAlias
                if (!targetSettingName.isNullOrEmpty() && linkAlias == targetSettingName) {
                    targetSettingItemId = item.id
                    targetSettingName = null
                    found = true
                }
                items.add(item)
            }
        }

        if (!found && !targetSettingName.isNullOrEmpty()) {
            SettingsRegistry.getInstance().onSettingNotFound(this)
            targetSettingName = null
        }

        translation.check(visibleTexts)
        updateTranslateItem(true)
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        if (!PluginsController.getInstance().isPluginActive(plugin)) return
        val settingItem = item.settingItem ?: return

        when (settingItem) {
            is TextSetting -> {
                val createSubFragmentCallback = settingItem.createSubFragmentCallback
                if (createSubFragmentCallback != null) {
                    openSubFragmentNative(item, createSubFragmentCallback)
                    return
                }

                settingItem.onClickCallback?.let { callback ->
                    try {
                        callback.call(view)
                    } catch (_: Exception) {}
                }
                return
            }

            is CustomSetting -> {
                val createSubFragmentCallback = settingItem.createSubFragmentCallback
                if (createSubFragmentCallback != null) {
                    openSubFragmentNative(item, createSubFragmentCallback)
                    return
                }

                try {
                    val factory = settingItem.factory
                    factory?.onClick(plugin, item, view)
                        ?: settingItem.onClickCallback?.call(view)
                } catch (_: Exception) {}
                return
            }
        }

        val key = item.object2 as? String ?: return

        when (view) {
            is TextCheckCell -> toggleSwitch(item, key, !view.isChecked) { view.isChecked = it }
            is NotificationsCheckCell -> toggleSwitch(item, key, !view.isChecked) { view.isChecked = it }
            is TextCell -> {
                when (item.settingItem) {
                    is SelectorSetting -> showSelectorDialog(item, view, key)
                    is InputSetting -> showStringInputDialog(item, view, key)
                }
            }
        }
    }

    private fun toggleSwitch(item: UItem, key: String, newValue: Boolean, setViewChecked: (Boolean) -> Unit) {
        setViewChecked(newValue)
        item.setChecked(newValue)
        val onChangeCallback = (item.settingItem as? SwitchSetting)?.onChangeCallback
        PluginsController.runOnPluginsQueue {
            PluginsController.getInstance().setPluginSettingAndTriggerOnChange(
                plugin.getId(), key, newValue, onChangeCallback
            )
        }
    }

    override fun onLongClick(item: UItem, view: View, position: Int, x: Float, y: Float): Boolean {
        if (!PluginsController.getInstance().isPluginActive(plugin)) return false
        val settingItem = item.settingItem ?: return false

        settingItem.onLongClickCallback?.let { callback ->
            try {
                callback.call(view)
            } catch (_: Exception) {}
            return true
        }

        if (!settingItem.linkAlias.isNullOrEmpty()) {
            settingItem.getLink(plugin.getId(), settingsLinkPrefix)?.let { link ->
                showCopyLinkOptions(view, link)
                return true
            }
        }

        if (settingItem is CustomSetting) {
            settingItem.factory?.let { factory ->
                try {
                    factory.onLongClick(plugin, item, view)
                } catch (_: Exception) {}
                return true
            }
        }

        return false
    }

    private fun showStringInputDialog(item: UItem, view: View, key: String) {
        val inputSetting = item.settingItem as? InputSetting ?: return
        if (parentActivity == null) return

        var dialog: AlertDialog? = null
        val builder = AlertDialog.Builder(context, resourceProvider).apply {
            setTitle(item.text)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        inputSetting.subtext?.let { subtext ->
            val textView = TextView(context).apply {
                setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider))
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
                text = translation.text(subtext)
            }
            container.addView(
                textView,
                LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT,
                    24f,
                    5f,
                    24f,
                    12f
                )
            )
        }

        val editText = EditTextBoldCursor(context).apply {
            lineYFix = true
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
            setText(
                PluginsController.getInstance()
                    .getPluginSettingString(plugin.getId(), key, inputSetting.defaultValue)
            )
            setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider))
            setHintColor(Theme.getColor(Theme.key_groupcreate_hintText, resourceProvider))
            setHintText(getString(R.string.EnterValue))
            isFocusable = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setCursorColor(
                Theme.getColor(
                    Theme.key_windowBackgroundWhiteInputFieldActivated,
                    resourceProvider
                )
            )
            setLineColors(
                Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resourceProvider),
                Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourceProvider),
                Theme.getColor(Theme.key_text_RedRegular, resourceProvider),
            )
            background = null
            setPadding(0, AndroidUtilities.dp(6f), 0, AndroidUtilities.dp(6f))
        }

        val onDone = {
            val text = editText.text?.toString().orEmpty()
            dialog?.dismiss()
            (view as TextCell).setValue(text, true)
            PluginsController.runOnPluginsQueue {
                PluginsController.getInstance()
                    .setPluginSettingAndTriggerOnChange(plugin.getId(), key, text, inputSetting.onChangeCallback)
            }
        }

        container.addView(
            editText,
            LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                24f,
                0f,
                24f,
                10f
            )
        )
        builder.makeCustomMaxHeight()
        builder.setView(container)
        builder.setWidth(AndroidUtilities.dp(292f))

        builder.setPositiveButton(getString(R.string.Done)) { _, _ -> onDone() }
        builder.setNegativeButton(getString(R.string.Cancel)) { dialogInterface, _ -> dialogInterface.dismiss() }

        dialog = builder.create().apply {
            setOnDismissListener { AndroidUtilities.hideKeyboard(editText) }
            setOnShowListener {
                editText.requestFocus()
                editText.setSelection(editText.length())
                AndroidUtilities.showKeyboard(editText)
            }
            setDismissDialogByButtons(false)
        }
        showDialog(dialog)
    }

    private fun showSelectorDialog(item: UItem, view: View, key: String) {
        val selectorSetting = item.settingItem as? SelectorSetting ?: return
        if (parentActivity == null) return

        var dialog: Dialog? = null
        val linearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val selectorItems = selectorSetting.items.map { translation.text(it).orEmpty() }
        val currentValue = PluginsController.getInstance()
            .getPluginSettingInt(plugin.getId(), key, selectorSetting.defaultValue)

        for (i in selectorItems.indices) {
            val cell = RadioColorCell(parentActivity).apply {
                setPadding(AndroidUtilities.dp(4f), 0, AndroidUtilities.dp(4f), 0)
                setCheckColor(
                    Theme.getColor(Theme.key_radioBackground),
                    Theme.getColor(Theme.key_dialogRadioBackgroundChecked)
                )
                setTextAndValue(selectorItems[i], currentValue == i)
                background = Theme.createSelectorDrawable(
                    Theme.getColor(Theme.key_listSelector),
                    Theme.RIPPLE_MASK_ALL
                )
            }
            linearLayout.addView(cell)
            cell.setOnClickListener {
                dialog?.dismiss()
                (view as TextCell).setValue(selectorItems[i], true)
                PluginsController.runOnPluginsQueue {
                    PluginsController.getInstance()
                        .setPluginSettingAndTriggerOnChange(plugin.getId(), key, i, selectorSetting.onChangeCallback)
                }
            }
        }

        dialog = AlertDialog.Builder(parentActivity).apply {
            setTitle(item.text)
            setView(linearLayout)
            setNegativeButton(getString(R.string.Cancel), null)
        }.create()

        showDialog(dialog)
    }

    private fun getStableId(item: SettingItem): Int = when (item) {
        is SwitchSetting -> Objects.hash("switch", item.key)
        is InputSetting -> Objects.hash("input", item.key)
        is EditTextSetting -> Objects.hash("edit", item.key)
        is SelectorSetting -> Objects.hash("selector", item.key)
        is HeaderSetting -> Objects.hash("header", item.text)
        is DividerSetting -> Objects.hash("divider", item.text)
        is TextSetting -> Objects.hash("text", item.text)
        is CustomSetting -> Objects.hash(
            "custom",
            item.item?.id ?: item.factory?.hashCode(),
            item.factoryArgs?.hashCode()
        )
        else -> item.hashCode()
    }

    private fun openSubFragmentNative(item: UItem, callback: PyObject) {
        PluginsController.runOnPluginsQueue {
            if (!PluginsController.getInstance().isPluginActive(plugin))
                return@runOnPluginsQueue

            val subSettingsList = try {
                val pySubDefinitions = callback.call() ?: return@runOnPluginsQueue
                val engine = PluginsController.getEngines()[PluginsConstants.PYTHON] as? PythonPluginsEngine
                    ?: return@runOnPluginsQueue
                engine.parsePySettingDefinitions(pySubDefinitions.asList())
            } catch (e: Exception) {
                FileLog.e("Error opening subfragment", e)
                return@runOnPluginsQueue
            }

            AndroidUtilities.runOnUIThread {
                if (subSettingsList.isNotEmpty()) {
                    val originalText = (item.settingItem as? TextSetting)?.text ?: item.text
                    val title = originalText?.toString()?.takeIf { it.isNotEmpty() }
                        ?: customTitle
                        ?: plugin.getName()

                    val subPrefix = if (settingsLinkPrefix.isNullOrEmpty()) {
                        item.settingItem?.linkAlias.orEmpty()
                    } else {
                        "$settingsLinkPrefix:${item.settingItem?.linkAlias.orEmpty()}"
                    }

                    presentFragment(
                        PluginSettingsActivity(plugin, title, subSettingsList, callback)
                            .setSettingsLinkPrefix(subPrefix)
                    )
                }
            }
        }
    }
}