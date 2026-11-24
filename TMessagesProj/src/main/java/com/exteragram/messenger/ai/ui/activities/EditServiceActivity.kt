/*
 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.
*/

package com.exteragram.messenger.ai.ui.activities

import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.graphics.Rect
import android.text.InputType
import android.text.TextUtils
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat
import com.exteragram.messenger.ai.DEFAULT_SERVICE
import com.exteragram.messenger.ai.AiController
import com.exteragram.messenger.ai.clearSelectedService
import com.exteragram.messenger.ai.setSelectedServices
import com.exteragram.messenger.ai.data.Service
import com.exteragram.messenger.ai.data.Suggestions
import com.exteragram.messenger.ai.network.Client
import com.exteragram.messenger.ai.network.GenerationCallback
import com.exteragram.messenger.preferences.BasePreferencesActivity
import com.exteragram.messenger.preferences.utils.SettingsRegistry
import com.exteragram.messenger.utils.addIf
import com.exteragram.messenger.utils.text.LocaleUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.BotWebViewVibrationEffect
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.EditTextCell
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import java.util.ArrayList
import java.util.Locale
import androidx.core.net.toUri
import org.telegram.messenger.LocaleController.getString

class EditServiceActivity @JvmOverloads constructor(
    private val currentService: Service? = null
) : BasePreferencesActivity() {

    enum class ServiceItem {
        PRESET_GEMINI,
        PRESET_OPENAI,
        PRESET_OPENROUTER,
        PRESET_PERPLEXITY,
        PRESET_CUSTOM,
        PASTE,
        SAVE,
        DELETE,
        REASONING;

        val id: Int = ordinal + 1
    }

    companion object {
        private val SERVICE_PRESETS = arrayOf(
            ServicePreset("Gemini", "https://generativelanguage.googleapis.com/v1beta", "gemini-3.5-flash"),
            ServicePreset("OpenAI", "https://api.openai.com/v1", "gpt-5.4-mini"),
            ServicePreset("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-5.4-mini"),
            ServicePreset("Perplexity", "https://api.perplexity.ai/v1/agent", "perplexity/glm-5.3-flash"),
            ServicePreset(null, null, null)
        )
    }

    private val clipChangedListener = ClipboardManager.OnPrimaryClipChangedListener { updateClipboardState() }

    private var clipboardManager: ClipboardManager? = null
    private var pasteString: String? = null
    private var pasteInput: ParsedServiceInput? = null

    private lateinit var urlCell: EditTextCell
    private lateinit var modelCell: EditTextCell
    private lateinit var keyCell: EditTextCell

    private var hasChanges = false
    private var updatingFields = false
    private var isTesting = false
    private var forceCustomPreset = false
    private var selectedPresetIndex = 0
    private var shiftDp = -4
    private var keyboardInset = 0

    private var initialUrl: String? = null
    private var initialModel: String? = null
    private var initialKey: String? = null
    private var initialReasoningEnabled = false
    private var reasoningEnabled = false

    private var testingClient: Client? = null
    private var testingRequestId: String? = null
    private var testingProgressDialog: AlertDialog? = null

    override fun onInsetsInternal(view: View, windowInsets: WindowInsetsCompat): WindowInsetsCompat {
        val bars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.statusBars())
        keyboardInset = maxOf(0, windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom - bars.bottom)
        return super.onInsetsInternal(view, windowInsets)
    }

    override fun onInsets(left: Int, top: Int, right: Int, bottom: Int) {
        super.onInsets(left, top, right, bottom + keyboardInset)
        if (keyboardInset > 0) {
            listView.post {
                listView.findFocus()?.let { focused ->
                    focused.requestRectangleOnScreen(Rect(0, 0, focused.width, focused.height), false)
                }
            }
        }
    }

    override fun createView(context: Context): View {
        clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        createFields(context)
        initializeState()
        updateClipboardState(context, false)

        val view = super.createView(context)
        actionBar.setAllowOverlayTitle(true)

        return view
    }

    override fun getTitle(): String =
        getString(if (currentService != null) R.string.EditService else R.string.NewService)

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        val reasoningText = if (SettingsRegistry.markAsNewFeature("AI-Service-Reasoning")) LocaleUtils.applyNewSpan(getString(R.string.AIReasoning)) else getString(R.string.AIReasoning)

        items.add(UItem.asHeader(getString(R.string.ServiceProvider)))
        SERVICE_PRESETS.indices.forEach { i ->
            val itemId = when (i) {
                0 -> ServiceItem.PRESET_GEMINI.id
                1 -> ServiceItem.PRESET_OPENAI.id
                2 -> ServiceItem.PRESET_OPENROUTER.id
                3 -> ServiceItem.PRESET_PERPLEXITY.id
                else -> ServiceItem.PRESET_CUSTOM.id
            }
            items.add(UItem.asRadio(itemId, getPresetTitle(i)).setChecked(i == selectedPresetIndex).setEnabled(!isTesting))
        }
        items.add(UItem.asShadow(getString(R.string.ServicesInfo)))

        items.add(UItem.asHeader(getString(R.string.ServiceInfo)))
        items.add(UItem.asCustom(urlCell))
        items.add(UItem.asCustom(modelCell))
        items.add(UItem.asCustom(keyCell))
        items.add(uCheck(ServiceItem.REASONING, reasoningText, reasoningEnabled, getString(R.string.AIReasoningInfo), searchable = false).apply {
            setEnabled(!isTesting)
        })
        items.add(UItem.asShadow())

        var hasActions = false
        pasteInput?.let { input ->
            hasActions = true
            items.add(uButton(ServiceItem.PASTE, getString(if (input.hasServiceFields()) R.string.ServicePasteService else R.string.ServicePasteKey), R.drawable.msg_copy, accent = true, searchable = false).apply {
                setEnabled(!isTesting)
            })
        }
        if (hasChanges) {
            hasActions = true
            items.add(uButton(ServiceItem.SAVE, getString(R.string.ServiceTestAndSave), R.drawable.ic_ab_done, accent = true, searchable = false).apply {
                setEnabled(!isTesting)
            })
        }
        if (currentService != null) {
            hasActions = true
            items.add(uButton(ServiceItem.DELETE, getString(R.string.Delete), R.drawable.msg_delete, red = true, searchable = false).apply {
                setEnabled(!isTesting)
            })
        }
        items.addIf(hasActions, UItem.asShadow())
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        if (isTesting || item.id <= 0 || item.id > ServiceItem.entries.size) return
        val clickedItem = ServiceItem.entries[item.id - 1]
        when (clickedItem) {
            ServiceItem.PRESET_GEMINI -> applyPreset(0)
            ServiceItem.PRESET_OPENAI -> applyPreset(1)
            ServiceItem.PRESET_OPENROUTER -> applyPreset(2)
            ServiceItem.PRESET_PERPLEXITY -> applyPreset(3)
            ServiceItem.PRESET_CUSTOM -> applyPreset(4)
            ServiceItem.PASTE -> {
                val input = pasteInput ?: parseServiceInput(pasteString)
                input?.let { applyParsedServiceInput(it) }
            }
            ServiceItem.SAVE -> saveConfig()
            ServiceItem.DELETE -> confirmDeleteService()
            ServiceItem.REASONING -> {
                toggleBooleanSettingAndRefresh(item) { reasoningEnabled = it }
                updateFormState()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        clipboardManager?.apply {
            removePrimaryClipChangedListener(clipChangedListener)
            addPrimaryClipChangedListener(clipChangedListener)
        }
        updateClipboardState()

        val preferences = MessagesController.getGlobalMainSettings()
        if (!preferences.getBoolean("view_animations", true)) {
            keyCell.editText.requestFocus()
            AndroidUtilities.showKeyboard(keyCell.editText)
        }
    }

    override fun onPause() {
        clipboardManager?.removePrimaryClipChangedListener(clipChangedListener)
        super.onPause()
    }

    override fun onFragmentDestroy() {
        stopTestingRequest()
        hideTestingProgressDialog()
        testingRequestId = null
        testingClient = null
        super.onFragmentDestroy()
    }

    override fun onTransitionAnimationEnd(isOpen: Boolean, backward: Boolean) {
        if (!isOpen) return
        keyCell.editText.requestFocus()
        AndroidUtilities.showKeyboard(keyCell.editText)
    }

    private fun createFields(context: Context) {
        urlCell = object : EditTextCell(context, getString(R.string.ServiceURL), false, false, 128, resourceProvider) {
            override fun onTextChanged(newText: CharSequence) = updateFormState()
        }.apply {
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            editText.setRawInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
            editText.imeOptions = EditorInfo.IME_ACTION_NEXT or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            editText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                    modelCell.editText.requestFocus()
                    AndroidUtilities.showKeyboard(modelCell.editText)
                    true
                } else {
                    false
                }
            }
        }

        modelCell = object : EditTextCell(context, getString(R.string.ServiceModel), false, false, 64, resourceProvider) {
            override fun onTextChanged(newText: CharSequence) = updateFormState()
        }.apply {
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            editText.setRawInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
            editText.imeOptions = EditorInfo.IME_ACTION_NEXT or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            editText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                    keyCell.editText.requestFocus()
                    AndroidUtilities.showKeyboard(keyCell.editText)
                    true
                } else {
                    false
                }
            }
        }

        keyCell = object : EditTextCell(context, getString(R.string.ServiceKey), false, false, 256, resourceProvider) {
            override fun onTextChanged(newText: CharSequence) = updateFormState()
        }.apply {
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            editText.setRawInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
            editText.imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            editText.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    saveConfig()
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun initializeState() {
        val service = currentService ?: DEFAULT_SERVICE
        initialUrl = service.url
        initialModel = service.model
        initialKey = service.key
        initialReasoningEnabled = service.isReasoningEnabled

        reasoningEnabled = initialReasoningEnabled
        urlCell.setText(safeString(initialUrl))
        modelCell.setText(safeString(initialModel))
        keyCell.setText(safeString(initialKey))
        hasChanges = false
        selectedPresetIndex = findPresetIndex(initialUrl, initialModel)
        forceCustomPreset = selectedPresetIndex == getCustomPresetIndex()
    }

    private fun updateFormState() {
        if (updatingFields || !hasFields()) return

        val oldHasChanges = hasChanges
        val oldPresetIndex = selectedPresetIndex
        val oldPasteVisible = pasteInput != null
        val oldPasteService = pasteInput?.hasServiceFields() == true

        hasChanges = !TextUtils.equals(getFieldText(urlCell), safeString(initialUrl)) ||
            !TextUtils.equals(getFieldText(modelCell), safeString(initialModel)) ||
            !TextUtils.equals(getFieldText(keyCell), safeString(initialKey)) ||
            reasoningEnabled != initialReasoningEnabled
        val detectedPresetIndex = findPresetIndex(getFieldText(urlCell), getFieldText(modelCell))
        selectedPresetIndex = if (forceCustomPreset) getCustomPresetIndex() else detectedPresetIndex
        updateClipboardState(false)

        val newPasteVisible = pasteInput != null
        val newPasteService = pasteInput?.hasServiceFields() == true
        if (fragmentView != null && listView.adapter != null && (oldHasChanges != hasChanges ||
                oldPresetIndex != selectedPresetIndex ||
                oldPasteVisible != newPasteVisible ||
                oldPasteService != newPasteService)) {
            listView.adapter.update(true)
        }
    }

    private fun setTesting(testing: Boolean) {
        isTesting = testing
        listView.adapter?.update(true)
    }

    private fun applyPreset(index: Int) {
        val preset = SERVICE_PRESETS[index]
        if (preset.url == null || preset.model == null) {
            forceCustomPreset = true
            updatingFields = true
            urlCell.setText("")
            modelCell.setText("")
            updatingFields = false
            updateFormState()
            urlCell.editText.requestFocus()
            AndroidUtilities.showKeyboard(urlCell.editText)
            return
        }
        forceCustomPreset = false
        updatingFields = true
        urlCell.setText(preset.url)
        modelCell.setText(preset.model)
        updatingFields = false
        updateFormState()
        keyCell.editText.requestFocus()
        AndroidUtilities.showKeyboard(keyCell.editText)
    }

    private fun getPresetTitle(index: Int): String {
        val preset = SERVICE_PRESETS[index]
        return preset.name ?: getString(R.string.ServiceProviderCustom)
    }

    private fun findPresetIndex(url: String?, model: String?): Int {
        for (i in 0 until SERVICE_PRESETS.size - 1) {
            val preset = SERVICE_PRESETS[i]
            if (TextUtils.equals(url, preset.url) && TextUtils.equals(model, preset.model))
                return i
        }
        return getCustomPresetIndex()
    }

    private fun getCustomPresetIndex(): Int = SERVICE_PRESETS.size - 1

    private fun saveConfig() {
        if (isTesting || !validateFields()) return

        val service = Service(getFieldText(urlCell), getFieldText(modelCell), getFieldText(keyCell), reasoningEnabled)
        val existingService = findExistingService(service)
        val reasoningChanged = currentService != null && currentService.isReasoningEnabled != reasoningEnabled

        if (existingService != null && existingService != currentService) {
            if (currentService != null) {
                BulletinFactory.of(this).createErrorBulletin(getString(R.string.ServiceDuplicate)).show()
                return
            }
            useExistingService(existingService)
            return
        }
        if (existingService != null && !reasoningChanged) {
            applySelection(existingService)
            return
        }

        testAndSave(service)
    }

    private fun testAndSave(service: Service) {
        testingClient = Client.Builder().apply {
            serviceOverride(service)
            roleOverride(Suggestions.ASSISTANT.role)
        }.build()
        showTestingProgressDialog()
        setTesting(true)
        testingRequestId = testingClient?.getResponse("Say 'hi'.", object : GenerationCallback {
            override fun onChunk(string: String) {}

            override fun onResponse(finalResponse: String) = AndroidUtilities.runOnUIThread {
                clearTestingState()
                if (currentService != null) {
                    AiController.getInstance().updateService(currentService, service)
                } else {
                    AiController.getInstance().addService(service)
                }
                applySelection(service)
            }

            override fun onError(errorCode: Int, errorMessage: String) = AndroidUtilities.runOnUIThread {
                clearTestingState()
                AiController.showErrorBulletin(this@EditServiceActivity, errorCode, errorMessage)
                showFieldError(getFieldForError(errorCode, errorMessage))
            }
        })
    }

    private fun useExistingService(existingService: Service) {
        if (existingService.isReasoningEnabled != reasoningEnabled) {
            existingService.isReasoningEnabled = reasoningEnabled
            AiController.getInstance().saveServices()
        }
        applySelection(existingService)
        BulletinFactory.global().createSimpleBulletin(
            R.raw.chats_infotip,
            getString(R.string.ServiceAlreadyExists),
            getString(R.string.ServiceAlreadyExistsInfo)
        ).show()
    }

    private fun applySelection(service: Service) {
        setSelectedServices(service)
        notificationCenter.postNotificationName(NotificationCenter.servicesUpdated)
        finishFragment()
    }

    private fun findExistingService(service: Service): Service? =
        AiController.getInstance().all.firstOrNull { it == service }

    private fun validateFields(): Boolean {
        if (!isValidServiceUrl(getFieldText(urlCell))) {
            showFieldError(urlCell)
            return false
        }
        if (getFieldText(modelCell).isEmpty()) {
            showFieldError(modelCell)
            return false
        }
        if (getFieldText(keyCell).isEmpty()) {
            showFieldError(keyCell)
            return false
        }
        return true
    }

    private fun isValidServiceUrl(url: String): Boolean {
        if (url.isEmpty()) return false
        val uri = url.toUri()
        val scheme = uri.scheme
        return ("http".equals(scheme, ignoreCase = true) || "https".equals(scheme, ignoreCase = true))
                && !uri.host.isNullOrEmpty()
    }

    private fun getFieldForError(errorCode: Int, errorMessage: String?): EditTextCell? =
        when (errorCode) {
            401, 403 -> keyCell
            400 -> modelCell
            404 -> if (errorMessage != null && errorMessage.lowercase(Locale.ROOT).contains("model")) modelCell else urlCell
            else -> null
        }

    private fun showFieldError(cell: EditTextCell?) {
        cell ?: return
        BotWebViewVibrationEffect.APP_ERROR.vibrate()
        shiftDp = -shiftDp
        AndroidUtilities.shakeViewSpring(cell, shiftDp.toFloat())
        cell.editText.requestFocus()
        AndroidUtilities.showKeyboard(cell.editText)
    }

    private fun applyParsedServiceInput(input: ParsedServiceInput) {
        updatingFields = true
        if (!input.url.isNullOrEmpty()) urlCell.setText(input.url)
        if (!input.model.isNullOrEmpty()) modelCell.setText(input.model)
        if (!input.key.isNullOrEmpty()) keyCell.setText(input.key)
        forceCustomPreset = false
        updatingFields = false
        updateFormState()
    }

    private fun showTestingProgressDialog() {
        parentActivity ?: return
        testingProgressDialog = AlertDialog(parentActivity, AlertDialog.ALERT_TYPE_SPINNER).apply {
            setOnCancelListener { cancelTesting() }
        }
        showDialog(testingProgressDialog)
    }

    private fun cancelTesting() {
        stopTestingRequest()
        clearTestingState()
    }

    private fun stopTestingRequest() {
        if (testingClient != null && !testingRequestId.isNullOrEmpty())
            testingClient?.stopRequest(testingRequestId)
    }

    private fun hideTestingProgressDialog() {
        testingProgressDialog?.let { dialog ->
            try {
                dialog.dismiss()
            } catch (_: Exception) {}
        }
        testingProgressDialog = null
    }

    private fun clearTestingState() {
        hideTestingProgressDialog()
        setTesting(false)
        testingRequestId = null
        testingClient = null
    }

    private fun parseServiceInput(text: String?): ParsedServiceInput? {
        if (text.isNullOrEmpty()) return null
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        val rawLines = trimmed.split(Regex("\\r?\\n"))
        val lines = rawLines.map { it.trim() }.filter { it.isNotEmpty() }

        if (lines.size >= 3 && isLikelyService(lines[0], lines[1], lines[2]))
            return ParsedServiceInput(lines[0], lines[1], lines[2])

        for (i in 0..lines.size - 3)
            if (isLikelyService(lines[i], lines[i + 1], lines[i + 2]))
                return ParsedServiceInput(lines[i], lines[i + 1], lines[i + 2])

        if (lines.size == 1 && isLikelyApiKey(trimmed))
            return ParsedServiceInput(null, null, trimmed)
        return null
    }

    private fun isLikelyService(url: String, model: String, key: String): Boolean =
        isLikelyServiceUrl(url) && isLikelyModel(model) && isLikelyApiKey(key)

    private fun isLikelyServiceUrl(text: String): Boolean =
        text.isNotEmpty() && (text.startsWith("https://") || text.startsWith("http://"))

    private fun isLikelyModel(text: String): Boolean =
        text.isNotEmpty() && text.length <= 128 && !text.matches(Regex(".*\\s+.*"))

    private fun isLikelyApiKey(text: String): Boolean =
        text.isNotEmpty() && text.length >= 20 && text.length <= 512 && text.matches(Regex("[A-Za-z0-9_.\\-]+"))

    private fun updateClipboardState() = updateClipboardState(true)

    private fun updateClipboardState(updateList: Boolean) =
        updateClipboardState(fragmentView?.context ?: context, updateList)

    private fun updateClipboardState(context: Context?, updateList: Boolean) {
        if (clipboardManager == null || context == null || !hasFields()) return

        val oldPasteVisible = pasteInput != null
        val oldPasteService = pasteInput?.hasServiceFields() == true

        val clipText = readClipboardText(context)
        val input = parseServiceInput(clipText)
        if (input != null && input.differsFrom(getFieldText(urlCell), getFieldText(modelCell), getFieldText(keyCell))) {
            pasteInput = input
            pasteString = clipText
        } else {
            pasteInput = null
            pasteString = null
        }

        val newPasteVisible = pasteInput != null
        val newPasteService = pasteInput?.hasServiceFields() == true
        if (updateList && fragmentView != null && (oldPasteVisible != newPasteVisible || oldPasteService != newPasteService)) {
            listView.adapter?.update(true)
        }
    }

    private fun readClipboardText(context: Context): String? {
        val clip = clipboardManager?.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        return try {
            val clipText = clip.getItemAt(0).coerceToText(context)
            clipText?.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun confirmDeleteService() {
        if (currentService == null || parentActivity == null) return
        val dialog = AlertDialog.Builder(parentActivity).apply {
            setTitle(getString(R.string.Delete))
            setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.DeleteServiceInfo, currentService.shortModel)))
            setPositiveButton(getString(R.string.Delete)) { _, _ -> deleteCurrentService() }
            setNegativeButton(getString(R.string.Cancel), null)
        }.create()

        showDialog(dialog)
        (dialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView)
            ?.setTextColor(Theme.getColor(Theme.key_text_RedBold))
    }

    private fun deleteCurrentService() {
        val wasSelected = currentService?.isSelected == true
        if (currentService != null && AiController.getInstance().removeService(currentService)) {
            if (wasSelected) {
                clearSelectedService()
                if (!AiController.getInstance().isServicesEmpty)
                    setSelectedServices(AiController.getInstance().all[0])
            }
            notificationCenter.postNotificationName(NotificationCenter.servicesUpdated)
            finishFragment()
        }
    }

    private fun hasFields(): Boolean =
        ::urlCell.isInitialized && ::modelCell.isInitialized && ::keyCell.isInitialized

    private fun getFieldText(field: EditTextCell?): String = field?.text?.toString()?.trim() ?: ""

    private fun safeString(value: String?): String = value ?: ""

    private class ServicePreset(val name: String?, val url: String?, val model: String?)

    private class ParsedServiceInput(val url: String?, val model: String?, val key: String?) {
        fun hasServiceFields(): Boolean = !TextUtils.isEmpty(url) || !TextUtils.isEmpty(model)

        fun differsFrom(currentUrl: String, currentModel: String, currentKey: String): Boolean =
            (!url.isNullOrEmpty() && url != currentUrl)
                    || (!model.isNullOrEmpty() && model != currentModel)
                    || (!key.isNullOrEmpty() && key != currentKey)
    }
}