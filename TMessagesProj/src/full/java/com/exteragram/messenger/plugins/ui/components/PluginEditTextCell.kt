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
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.exteragram.messenger.plugins.Plugin
import com.exteragram.messenger.plugins.PluginsController
import com.exteragram.messenger.plugins.models.EditTextSetting
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.EditTextCell
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.Components.UniversalRecyclerView
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

@SuppressLint("ViewConstructor")
class PluginEditTextCell(
    context: Context,
    resourcesProvider: Theme.ResourcesProvider?,
) : EditTextCell(context, null, false, false, -1, resourcesProvider) {
    private var pendingSaveRunnable: Runnable? = null
    private var valueToSave: String? = null
    private var currentSetting: EditTextSetting? = null
    private var pluginId: String? = null

    private val saveTextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(newText: CharSequence, start: Int, before: Int, count: Int) {
            valueToSave = newText.toString()
            scheduleSave()
        }

        override fun afterTextChanged(s: Editable) {
        }
    }

    init {
        layoutParams =
            RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider))
        editText.addTextChangedListener(saveTextWatcher)
    }

    private fun scheduleSave() {
        pendingSaveRunnable?.let(AndroidUtilities::cancelRunOnUIThread)

        val keyToSave = currentSetting?.key
        val valToSave = valueToSave
        val pluginId = pluginId
        val setting = currentSetting

        if (keyToSave == null || valToSave == null || pluginId == null || setting == null) {
            return
        }

        pendingSaveRunnable = Runnable {
            pendingSaveRunnable = null
            PluginsController.runOnPluginsQueue {
                val savedValue = if (setting.maxLength > 0 && valToSave.length > setting.maxLength) {
                    valToSave.substring(0, setting.maxLength)
                } else {
                    valToSave
                }
                PluginsController.getInstance()
                    .setPluginSettingAndTriggerOnChange(
                        pluginId,
                        keyToSave,
                        savedValue,
                        setting.onChangeCallback
                    )
            }
        }

        AndroidUtilities.runOnUIThread(pendingSaveRunnable, SAVE_DEBOUNCE_MS.toLong())
    }

    override fun onFocusChanged(focused: Boolean) {
        super.onFocusChanged(focused)
        if (!focused) {
            flushPendingSave()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        flushPendingSave()
    }

    private fun flushPendingSave() {
        pendingSaveRunnable?.let {
            AndroidUtilities.cancelRunOnUIThread(it)
            it.run()
            pendingSaveRunnable = null
        }
    }

    @JvmOverloads
    fun bind(pluginId: String, setting: EditTextSetting, hint: CharSequence? = setting.hint) {
        val isSameKey = currentSetting != null && TextUtils.equals(currentSetting?.key, setting.key)

        if (!isSameKey) {
            flushPendingSave()
        }

        this.pluginId = pluginId
        currentSetting = setting

        setWatchersEnabled(false)
        setMultiline(setting.multiline)

        val newInputType =
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
                    if (setting.multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0

        if (editText.inputType != newInputType) {
            editText.inputType = newInputType
        }

        if (setting.maxLength > 0) {
            setShowLimitWhenNear(setting.maxLength / minOf(setting.maxLength, 4))
        } else {
            setShowLimitWhenNear(-1)
        }

        val filters = ArrayList<InputFilter>()
        createInputFilter(setting.mask)?.let(filters::add)
        if (setting.maxLength > 0) {
            filters.add(InputFilter.LengthFilter(setting.maxLength))
        }
        editText.filters = if (filters.isNotEmpty()) {
            filters.toTypedArray()
        } else {
            emptyArray()
        }

        val storedValue = PluginsController.getInstance()
            .getPluginSettingString(pluginId, setting.key, setting.defaultValue)
        val currentValue = editText.text.toString()

        if (!TextUtils.equals(storedValue, currentValue)) {
            if (editText.hasFocus() && isSameKey) {
                setWatchersEnabled(true)
                return
            }
            setText(storedValue)
        }
        valueToSave = storedValue

        (hint ?: setting.hint)?.let { editText.hint = it }

        setWatchersEnabled(true)
    }

    private fun setWatchersEnabled(enabled: Boolean) {
        editText.removeTextChangedListener(saveTextWatcher)
        if (enabled) {
            editText.addTextChangedListener(saveTextWatcher)
        }
    }

    private fun createInputFilter(maskRegex: String?): InputFilter? {
        if (TextUtils.isEmpty(maskRegex)) return null

        val maskPattern = try {
            maskRegex?.let { Pattern.compile(it) }
        } catch (e: PatternSyntaxException) {
            FileLog.e("Invalid mask for EditText: $maskRegex", e)
            return null
        }

        return InputFilter { source, start, end, _, _, _ ->
            var keepOriginal = true
            val sb = StringBuilder(end - start)

            for (i in start until end) {
                val char = source[i]
                if (maskPattern?.matcher(char.toString())?.matches() ?: false) {
                    sb.append(char)
                } else {
                    keepOriginal = false
                }
            }

            if (keepOriginal) null else sb.toString()
        }
    }

    class Factory : UItem.UItemFactory<PluginEditTextCell>() {
        override fun createView(
            context: Context,
            listView: RecyclerListView,
            currentAccount: Int,
            classGuid: Int,
            resourcesProvider: Theme.ResourcesProvider?,
        ): PluginEditTextCell = PluginEditTextCell(context, resourcesProvider)

        override fun bindView(
            view: View,
            item: UItem,
            divider: Boolean,
            adapter: UniversalAdapter,
            listView: UniversalRecyclerView
        ) {
            if (view is PluginEditTextCell && item.settingItem is EditTextSetting && item.`object` is Plugin) {
                view.bind((item.`object` as Plugin).getId(), item.settingItem as EditTextSetting, item.text)
                view.setDivider(divider)
            }
        }

        override fun isClickable(): Boolean = false

        companion object {
            init {
                setup(Factory())
            }

            @JvmStatic
            fun `as`(plugin: Plugin, setting: EditTextSetting): UItem {
                return UItem.ofFactory(Factory::class.java).apply {
                    `object` = plugin
                    settingItem = setting
                }
            }
        }
    }

    companion object {
        private const val SAVE_DEBOUNCE_MS = 750
    }
}