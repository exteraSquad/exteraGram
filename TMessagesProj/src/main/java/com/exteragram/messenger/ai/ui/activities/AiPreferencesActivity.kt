/*
 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.
*/

package com.exteragram.messenger.ai.ui.activities

import android.text.TextUtils
import android.view.View
import com.exteragram.messenger.PLACEHOLDERS
import com.exteragram.messenger.ai.AiController
import com.exteragram.messenger.ai.getConversationHistory
import com.exteragram.messenger.ai.getReplaceTelegramEditor
import com.exteragram.messenger.ai.getReplaceTelegramSummaries
import com.exteragram.messenger.ai.insertAsQuote
import com.exteragram.messenger.ai.responseStreaming
import com.exteragram.messenger.ai.saveHistory
import com.exteragram.messenger.ai.selectedRole
import com.exteragram.messenger.ai.setReplaceTelegramEditor
import com.exteragram.messenger.ai.setReplaceTelegramSummaries
import com.exteragram.messenger.ai.showResponseOnly
import com.exteragram.messenger.ai.temperature
import com.exteragram.messenger.preferences.BasePreferencesActivity
import com.exteragram.messenger.preferences.utils.SettingsRegistry
import com.exteragram.messenger.utils.text.LocaleUtils
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import java.net.MalformedURLException
import java.net.URL
import java.util.ArrayList
import java.util.Locale

class AiPreferencesActivity : BasePreferencesActivity() {

    enum class PreferenceItem {
        ENDPOINT,
        ROLE,
        HISTORY,
        TEMPERATURE,
        RESPONSE_STREAMING,
        SHOW_RESPONSE_ONLY,
        INSERT_AS_QUOTE,
        REPLACE_TELEGRAM_EDITOR,
        REPLACE_TELEGRAM_SUMMARIES;

        val id: Int = ordinal + 1
    }

    override fun getTitle(): String = getString(R.string.AIChat)

    override fun needHideTitle(): Boolean = true

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asTopView(getTitle(), getString(R.string.AIChatInfo2), PLACEHOLDERS, "\uD83E\uDD16"))
        items.add(UItem.asShadow())

        items.add(UItem.asHeader(getString(R.string.General)))
        items.add(uButton(PreferenceItem.ENDPOINT, getString(R.string.Services), R.drawable.msg_language, getEndpointValue(), linkAlias = "aiServices", prioritizeTitleOverValue = true))
        items.add(uButton(PreferenceItem.ROLE, getString(R.string.Roles), R.drawable.msg_openprofile, selectedRole, linkAlias = "aiRoles", prioritizeTitleOverValue = true))
        items.add(uButton(PreferenceItem.HISTORY, getString(R.string.MessageHistory), R.drawable.msg_discuss, getHistoryValue(), linkAlias = "aiHistory", prioritizeTitleOverValue = true))
        items.add(UItem.asShadow(getString(R.string.HistoryInfo)))

        items.add(UItem.asHeader(getString(R.string.AIReplaceTelegram)))
        items.add(uCheck(PreferenceItem.REPLACE_TELEGRAM_EDITOR, getString(R.string.AIFeaturesEditor), getReplaceTelegramEditor(), getString(R.string.AIReplaceTelegramEditorInfo), linkAlias = "replaceTelegramEditor"))
        items.add(uCheck(PreferenceItem.REPLACE_TELEGRAM_SUMMARIES, getString(R.string.AIFeaturesSummaries), getReplaceTelegramSummaries(), getString(R.string.AIReplaceTelegramSummariesInfo), linkAlias = "replaceTelegramSummaries").showDivider(false))
        items.add(UItem.asShadow(getString(R.string.AIReplaceTelegramInfo)))

        items.add(UItem.asHeader(getString(R.string.AIGeneration)))
        items.add(uCheck(PreferenceItem.RESPONSE_STREAMING, getString(R.string.ResponseStreaming), responseStreaming, getString(R.string.ResponseStreamingInfo), linkAlias = "responseStreaming"))
        items.add(uCheck(PreferenceItem.SHOW_RESPONSE_ONLY, getString(R.string.ShowResponseOnly), showResponseOnly, linkAlias = "showResponseOnly"))
        items.add(uCheck(PreferenceItem.INSERT_AS_QUOTE, getString(R.string.InsertResponseAsQuote), insertAsQuote, linkAlias = "insertResponseAsQuote").showDivider(false))
        items.add(UItem.asShadow())

        val tempHeader = if (SettingsRegistry.markAsNewFeature("aiTemperature")) {
            LocaleUtils.applyNewSpan(getString(R.string.AITemperature))
        } else {
            getString(R.string.AITemperature)
        }
        items.add(UItem.asHeader(tempHeader))
        items.add(createTemperatureSliderItem().apply {
            showDivider(false)
            setSearchable(this@AiPreferencesActivity)
            setLinkAlias("aiTemperature", this@AiPreferencesActivity)
        })
        items.add(UItem.asShadow(getString(R.string.AITemperatureInfo)))
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        if (item.id <= 0 || item.id > PreferenceItem.entries.size) return
        val clickedItem = PreferenceItem.entries[item.id - 1]
        when (clickedItem) {
            PreferenceItem.ENDPOINT -> presentFragment(ServicesActivity())
            PreferenceItem.ROLE -> presentFragment(RolesActivity())
            PreferenceItem.HISTORY -> presentFragment(AiHistoryActivity())
            PreferenceItem.RESPONSE_STREAMING -> toggleBooleanSettingAndRefresh(item) { responseStreaming = it }
            PreferenceItem.SHOW_RESPONSE_ONLY -> toggleBooleanSettingAndRefresh(item) { showResponseOnly = it }
            PreferenceItem.INSERT_AS_QUOTE -> toggleBooleanSettingAndRefresh(item) { insertAsQuote = it }
            PreferenceItem.REPLACE_TELEGRAM_EDITOR -> toggleBooleanSettingAndRefresh(item) { setReplaceTelegramEditor(it) }
            PreferenceItem.REPLACE_TELEGRAM_SUMMARIES -> toggleBooleanSettingAndRefresh(item) { setReplaceTelegramSummaries(it) }
            else -> {}
        }
    }

    private fun getHistoryValue(): CharSequence {
        if (!saveHistory) return getString(R.string.BlurOff)
        val size = getConversationHistory().size
        return if (size == 0) getString(R.string.BlockedEmpty) else LocaleController.formatPluralString("messages", size)
    }

    private fun createTemperatureSliderItem(): UItem =
        UItem.asIntSlideView(1, 0, temperature, 20, this::formatTemperature, ::temperature::set).apply {
            id = PreferenceItem.TEMPERATURE.id
            text = getString(R.string.AITemperature)
        }

    private fun formatTemperature(value: Int): CharSequence =
        String.format(Locale.US, "%.1f", value / 10.0f)

    private fun getEndpointValue(): String {
        if (AiController.getInstance().getSelected().isOnDevice)
            return if (AiController.canUseAI()) getString(R.string.AIOnDevice) else getString(R.string.BlockedEmpty)

        return try {
            val host = URL(AiController.getInstance().getSelected().url).host
            if (TextUtils.isEmpty(host) || !AiController.canUseAI()) {
                getString(R.string.BlockedEmpty)
            } else if (host.contains("generativelanguage.googleapis")) {
                "Gemini"
            } else {
                host
            }
        } catch (_: MalformedURLException) {
            getString(R.string.BlockedEmpty)
        }
    }
}