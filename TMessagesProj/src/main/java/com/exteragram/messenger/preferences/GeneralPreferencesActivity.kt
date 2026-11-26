/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.preferences

import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import com.exteragram.messenger.*
import com.exteragram.messenger.translator.TranslationProviders
import com.exteragram.messenger.translator.TranslatorUtils
import com.exteragram.messenger.utils.addIf
import com.exteragram.messenger.utils.text.ZalgoFilter
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.tgnet.ConnectionsManager
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet
import org.telegram.ui.Components.TranslateAlert2
import org.telegram.ui.Components.UItem
import org.telegram.ui.Components.UniversalAdapter
import org.telegram.ui.PremiumPreviewFragment
import org.telegram.ui.RestrictedLanguagesSelectActivity
import java.util.ArrayList
import java.util.regex.Pattern

class GeneralPreferencesActivity : BasePreferencesActivity() {

    enum class GeneralItem {
        SHOW_TRANSLATE_BUTTON,
        SHOW_TRANSLATE_CHAT_BUTTON,
        TRANSLATION_PROVIDERS,
        TRANSLATION_TARGET_LANGUAGE,
        DO_NOT_TRANSLATE_LANGUAGES,
        DISABLE_NUMBER_ROUNDING,
        FORMAT_TIME_WITH_SECONDS,
        RELATIVE_LAST_SEEN,
        IN_APP_VIBRATION,
        FILTER_ZALGO,
        DOWNLOAD_SPEED_BOOST,
        UPLOAD_SPEED_BOOST,
        CUSTOM_SAVE_PATH,
        HIDE_PHONE_NUMBER,
        SHOW_ID_AND_DC,
        DEFAULT_PROFILE_TAB,
        HIDE_ARCHIVE_FOLDER,
        ARCHIVE_ON_PULL,
        DISABLE_UNARCHIVE_SWIPE,
        DISABLE_NOTIFICATION_DELAY;

        val id: Int = ordinal + 1
    }

    private lateinit var idOptions: Array<CharSequence>
    private lateinit var profileTabs: Array<CharSequence>
    private lateinit var translationProviders: Array<CharSequence>
    private val fiveMinutesAgo by lazy { ConnectionsManager.getInstance(currentAccount).currentTime - 60 * 5 }

    companion object {
        private val SAVE_PATH_PATTERN = Pattern.compile("^(?!\\.{1,2}$)[A-Za-z0-9._ -]{1,255}$")
    }

    override fun initializeOptionStrings() {
        idOptions = arrayOf(
            getString(R.string.Hide),
            "Telegram API",
            "Bot API"
        )
        profileTabs = arrayOf(
            getString(R.string.Default),
            getString(R.string.SharedMediaTab2),
            getString(R.string.SharedGroupsTab2)
        )
        translationProviders = TranslationProviders.names()
    }

    override fun getTitle(): String = getString(R.string.General)

    override fun fillItems(items: ArrayList<UItem>, adapter: UniversalAdapter) {
        items.add(UItem.asHeader(getString(R.string.TranslateMessages)))
        items.add(uCheck(GeneralItem.SHOW_TRANSLATE_BUTTON, getString(R.string.ShowTranslateButton), contextTranslateValue, linkAlias = "showTranslateButton"))
        items.add(uCheck(GeneralItem.SHOW_TRANSLATE_CHAT_BUTTON, getString(R.string.ShowTranslateChatButton), chatTranslateValue, checkBoxIcon = if (isChatTranslateLocked) R.drawable.permission_locked else 0, linkAlias = "showTranslateChatButton"))
        items.add(uButton(GeneralItem.TRANSLATION_PROVIDERS, getString(R.string.TranslationProvider), value = translationProviders[translationProvider], linkAlias = "translationProvider"))
        items.add(uButton(GeneralItem.TRANSLATION_TARGET_LANGUAGE, getString(R.string.TranslationTarget), value = getCurrentLangName(), linkAlias = "translationTargetLanguage"))
        items.add(uButton(GeneralItem.DO_NOT_TRANSLATE_LANGUAGES, getString(R.string.DoNotTranslate), value = doNotTranslateValue, linkAlias = "doNotTranslateLanguages"))
        items.add(UItem.asShadow(getString(R.string.TranslateMessagesInfo1)))

        items.add(UItem.asHeader(getString(R.string.General)))
        items.add(uCheck(GeneralItem.DISABLE_NUMBER_ROUNDING, getString(R.string.DisableNumberRounding), disableNumberRounding, "1.23K -> 1,234", linkAlias = "disableNumberRounding", multiline = false))
        items.add(uCheck(GeneralItem.FORMAT_TIME_WITH_SECONDS, getString(R.string.FormatTimeWithSeconds), formatTimeWithSeconds, "12:34 -> 12:34:56", linkAlias = "formatTimeWithSeconds", multiline = false))
        items.add(uCheck(GeneralItem.IN_APP_VIBRATION, getString(R.string.InAppVibration), inAppVibration, linkAlias = "inAppVibration"))
        items.add(uCheck(GeneralItem.DISABLE_NOTIFICATION_DELAY, getString(R.string.DisableNotificationDelay), disableNotificationDelay, linkAlias = "disableNotificationDelay"))
        items.add(uCheck(GeneralItem.FILTER_ZALGO, getString(R.string.FilterZalgo), filterZalgo, linkAlias = "filterZalgo"))
        items.add(UItem.asShadow(LocaleController.formatString(R.string.FilterZalgoInfo, ZalgoFilter.filter("Z̷͍͌ā̸̜l̸̞̂g̷͍̝o̶̩̓"))))

        items.add(UItem.asHeader(getString(R.string.DownloadSpeedBoost)))
        items.add(UItem.asSlideView(
            GeneralItem.DOWNLOAD_SPEED_BOOST.id,
            arrayOf(
                getString(R.string.BlurOff),
                getString(R.string.SpeedFast),
                getString(R.string.Ultra)
            ),
            downloadSpeedBoost
        ) { downloadSpeedBoost = it }.setLinkAlias("downloadSpeedBoost", this))
        items.add(uCheck(GeneralItem.UPLOAD_SPEED_BOOST, getString(R.string.UploadSpeedBoost), uploadSpeedBoost, linkAlias = "uploadSpeedBoost"))
        items.add(UItem.asShadow(getString(R.string.SpeedBoostInfo)))

        items.add(UItem.asHeader(getString(R.string.StorageSettings)))
        items.add(uButton(GeneralItem.CUSTOM_SAVE_PATH, getString(R.string.CustomSavePath), value = customSavePathDisplayValue, linkAlias = "customSavePath"))
        items.add(UItem.asShadow(customSavePathInfo))

        items.add(UItem.asHeader(getString(R.string.MainTabsProfile)))
        items.add(uCheck(GeneralItem.RELATIVE_LAST_SEEN, getString(R.string.RelativeLastSeen), relativeLastSeen, LocaleController.formatDateOnline(fiveMinutesAgo.toLong(), null, BooleanArray(1)), linkAlias = "relativeLastSeen", multiline = false))
        items.add(uCheck(GeneralItem.HIDE_PHONE_NUMBER, getString(R.string.HidePhoneNumber), hidePhoneNumber, linkAlias = "hidePhoneNumber"))
        items.add(uButton(GeneralItem.DEFAULT_PROFILE_TAB, getString(R.string.DefaultProfileTab), value = profileTabs[defaultProfileTab.ordinal], linkAlias = "defaultProfileTab"))
        items.add(uButton(GeneralItem.SHOW_ID_AND_DC, getString(R.string.ShowIdAndDc), value = idOptions[showIdAndDc], linkAlias = "showIdAndDc"))
        items.add(UItem.asShadow(getString(R.string.ShowIdAndDcInfo)))

        items.add(UItem.asHeader(getString(R.string.ArchivedChats)))
        items.add(uCheck(GeneralItem.HIDE_ARCHIVE_FOLDER, getString(R.string.HideArchiveFolder), hideArchiveFolder, linkAlias = "hideArchiveFolder"))
        items.addIf(!hideArchiveFolder, uCheck(GeneralItem.ARCHIVE_ON_PULL, getString(R.string.ArchiveOnPull), archiveOnPull, linkAlias = "archiveOnPull"))
        items.add(uCheck(GeneralItem.DISABLE_UNARCHIVE_SWIPE, getString(R.string.DisableUnarchiveSwipe), disableUnarchiveSwipe, linkAlias = "disableUnarchiveSwipe"))
        items.add(UItem.asShadow(getString(R.string.DisableUnarchiveSwipeInfo)))
    }

    override fun onClick(item: UItem, view: View, position: Int, x: Float, y: Float) {
        if (item.id <= 0 || item.id > GeneralItem.entries.size) return
        val clickedItem = GeneralItem.entries[item.id - 1]
        when (clickedItem) {
            GeneralItem.SHOW_TRANSLATE_BUTTON -> {
                toggleBooleanSettingAndRefresh(item) { enabled -> messagesController.translateController.isContextTranslateEnabled = enabled }
                handleContextTranslateClick()
            }
            GeneralItem.SHOW_TRANSLATE_CHAT_BUTTON -> handleChatTranslateClick(item)
            GeneralItem.TRANSLATION_PROVIDERS ->
                showListDialog(item, translationProviders, getString(R.string.TranslationProvider), translationProvider) { i ->
                    translationProvider = i
                    TranslatorUtils.ensureTargetLanguageCompatibleWithProvider()
                    parentLayout.rebuildFragments(0)
                }
            GeneralItem.TRANSLATION_TARGET_LANGUAGE ->
                presentFragment(RestrictedLanguagesSelectActivity(RestrictedLanguagesSelectActivity.TYPE_TARGET_LANGUAGE))
            GeneralItem.DO_NOT_TRANSLATE_LANGUAGES ->
                presentFragment(RestrictedLanguagesSelectActivity())
            GeneralItem.DISABLE_NUMBER_ROUNDING ->
                toggleBooleanSettingAndRefresh(item) { disableNumberRounding = it }
            GeneralItem.FORMAT_TIME_WITH_SECONDS -> {
                toggleBooleanSettingAndRefresh(item) { formatTimeWithSeconds = it }
                handleFormatTimeWithSecondsClick()
            }
            GeneralItem.RELATIVE_LAST_SEEN -> toggleBooleanSettingAndRefresh(item) { relativeLastSeen = it }
            GeneralItem.IN_APP_VIBRATION -> toggleBooleanSettingAndRefresh(item) { inAppVibration = it }
            GeneralItem.DISABLE_NOTIFICATION_DELAY -> toggleBooleanSettingAndRefresh(item) { disableNotificationDelay = it }
            GeneralItem.FILTER_ZALGO -> toggleBooleanSettingAndRefresh(item) { filterZalgo = it }
            GeneralItem.UPLOAD_SPEED_BOOST -> toggleBooleanSettingAndRefresh(item) { uploadSpeedBoost = it }
            GeneralItem.CUSTOM_SAVE_PATH -> showCustomSavePathDialog()
            GeneralItem.HIDE_PHONE_NUMBER -> {
                toggleBooleanSettingAndRefresh(item) { hidePhoneNumber = it }
                handleHidePhoneNumberClick()
            }
            GeneralItem.SHOW_ID_AND_DC ->
                showListDialog(item, idOptions, getString(R.string.ShowIdAndDc), showIdAndDc) { i ->
                    showIdAndDc = i
                    parentLayout.rebuildFragments(0)
                }
            GeneralItem.DEFAULT_PROFILE_TAB ->
                showListDialog(item, profileTabs, getString(R.string.DefaultProfileTab), defaultProfileTab.ordinal) { i ->
                    defaultProfileTab = DefaultProfileTab.entries[i]
                }
            GeneralItem.HIDE_ARCHIVE_FOLDER -> {
                toggleBooleanSettingAndRefresh(item) { hideArchiveFolder = it }
                MessagesController.getInstance(currentAccount).checkArchiveFolder()
            }
            GeneralItem.ARCHIVE_ON_PULL -> toggleBooleanSettingAndRefresh(item) { archiveOnPull = it }
            GeneralItem.DISABLE_UNARCHIVE_SWIPE -> toggleBooleanSettingAndRefresh(item) { disableUnarchiveSwipe = it }
            else -> {}
        }
    }

    private val doNotTranslateValue: CharSequence
        get() {
            val langCodes = RestrictedLanguagesSelectActivity.getRestrictedLanguages()
            val accusative = BooleanArray(1)
            return langCodes.mapNotNull { lang ->
                TranslateAlert2.capitalFirst(TranslateAlert2.languageName(lang, accusative))
            }.joinToString(", ")
        }

    private val contextTranslateValue: Boolean
        get() = messagesController.translateController.isContextTranslateEnabled

    private fun handleContextTranslateClick() {
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateSearchSettings)
        parentLayout.rebuildFragments(0)
    }

    private val chatTranslateValue: Boolean
        get() = messagesController.translateController.isChatTranslateEnabled

    private val isChatTranslateLocked: Boolean
        get() = !userConfig.isPremium && !TranslatorUtils.isAlternativeProvider()

    private fun handleChatTranslateClick(item: UItem) {
        if (!item.checked && isChatTranslateLocked) {
            BulletinFactory.of(this).createSimpleBulletin(
                R.raw.msg_translate,
                getString(R.string.ShowTranslateChatButtonLocked),
                getString(R.string.MoreInfo)
            ) { showDialog(PremiumFeatureBottomSheet(this, PremiumPreviewFragment.PREMIUM_FEATURE_TRANSLATIONS, false)) }.show()
            return
        }
        toggleBooleanSettingAndRefresh(item) { enabled -> messagesController.translateController.isChatTranslateEnabled = enabled }
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateSearchSettings)
        parentLayout.rebuildFragments(0)
    }

    private fun handleFormatTimeWithSecondsClick() {
        LocaleController.getInstance().recreateFormatters()
        parentLayout.rebuildFragments(0)
    }

    private fun handleHidePhoneNumberClick() {
        notificationCenter.postNotificationName(NotificationCenter.mainUserInfoChanged)
        parentLayout.rebuildFragments(0)
    }

    private val customSavePathDisplayValue: String
        get() = if (TextUtils.isEmpty(customSavePath)) getString(R.string.CustomSavePathDefault) else customSavePath

    private val customSavePathInfo: String
        get() = if (TextUtils.isEmpty(customSavePath)) getString(R.string.CustomSavePathInfo)
        else LocaleController.formatString(R.string.CustomSavePathInfoFolder, customSavePath)

    private fun showCustomSavePathDialog() {
        context ?: return
        val editText = EditTextBoldCursor(context).apply {
            lineYFix = true
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
            setText(customSavePath)
            setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider))
            setHintColor(Theme.getColor(Theme.key_groupcreate_hintText, resourceProvider))
            setHintText(getString(R.string.CustomSavePathHint))
            isFocusable = true
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            background = null
            setLineColors(
                Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resourceProvider),
                Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourceProvider),
                Theme.getColor(Theme.key_text_RedRegular, resourceProvider)
            )
            setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourceProvider))
            setPadding(0, AndroidUtilities.dp(6f), 0, AndroidUtilities.dp(6f))
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(editText, LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                24f, 0f, 24f, 10f
            ))
        }

        val builder = AlertDialog.Builder(context, resourceProvider).apply {
            setTitle(getString(R.string.CustomSavePath))
            makeCustomMaxHeight()
            setView(container)
            setWidth(AndroidUtilities.dp(292f))
            setPositiveButton(getString(R.string.Done)) { dialogInterface, _ ->
                val value = editText.text?.toString()?.trim() ?: ""
                if (value.isNotEmpty() && !SAVE_PATH_PATTERN.matcher(value).matches()) {
                    AndroidUtilities.shakeView(editText)
                    return@setPositiveButton
                }
                customSavePath = value
                listView.adapter.update(true)
                dialogInterface.dismiss()
            }
            setNegativeButton(getString(R.string.Cancel)) { dialogInterface, _ -> dialogInterface.dismiss() }
        }

        val dialog = builder.create().apply {
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
}