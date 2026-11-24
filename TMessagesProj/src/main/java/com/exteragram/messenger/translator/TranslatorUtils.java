/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.translator;

import android.text.TextUtils;
import android.text.style.URLSpan;

import androidx.annotation.Nullable;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.translator.core.BaseTranslator;
import com.exteragram.messenger.translator.core.TranslationError;
import com.exteragram.messenger.translator.core.HttpTranslator;
import com.exteragram.messenger.translator.providers.GoogleTranslator;
import com.exteragram.messenger.translator.providers.TelegramTranslator;
import com.exteragram.messenger.utils.chats.ChatUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LanguageDetector;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.TranslateController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.TranslateAlert2;
import org.telegram.ui.RestrictedLanguagesSelectActivity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

public class TranslatorUtils {
    private static final String[] DEVICE_MODELS = {
            "Galaxy S6", "Galaxy S7", "Galaxy S8", "Galaxy S9", "Galaxy S10", "Galaxy S21",
            "Pixel 3", "Pixel 4", "Pixel 5",
            "OnePlus 6", "OnePlus 7", "OnePlus 8", "OnePlus 9", "Xperia XZ", "Xperia XZ2", "Xperia XZ3", "Xperia 1", "Xperia 5", "Xperia 10", "Xperia L4"
    };
    private static final String[] CHROME_VERSIONS = {
            "111.0.5563.57", "94.0.4606.81", "80.0.3987.119", "69.0.3497.100", "92.0.4515.159", "71.0.3578.99"
    };

    public static final String TARGET_LANG_FOLLOW_APP = "app";
    public static final String TARGET_LANG_SEND_DEFAULT = "en";
    private static final String TARGET_LANG_SEND_RECENT_KEY = "targetLangSendRecent";
    private static final int TARGET_LANG_SEND_RECENT_LIMIT = 3;

    private static List<TranslateController.Language> getLanguages() {
        return new ArrayList<>(TranslateController.getLanguages());
    }

    private static List<TranslateController.Language> getIndexedTargetLanguages() {
        return getCurrentTargetLanguages();
    }

    public static String normalizeLanguageCode(String language) {
        if (TextUtils.isEmpty(language)) {
            return null;
        }
        String code = language.trim().toLowerCase(Locale.US).replace('_', '-');
        if ("nb".equals(code)) {
            code = "no";
        }
        return code;
    }

    public static String primaryLanguageOf(String language) {
        String normalized = normalizeLanguageCode(language);
        if (TextUtils.isEmpty(normalized)) {
            return null;
        }
        int separator = normalized.indexOf('-');
        return separator >= 0 ? normalized.substring(0, separator) : normalized;
    }

    public static boolean isTargetLanguageFollowApp() {
        return TARGET_LANG_FOLLOW_APP.equalsIgnoreCase(ExteraConfig.getTargetLang());
    }

    public static boolean isRestrictedLanguage(String language) {
        if (TextUtils.isEmpty(language)) {
            return false;
        }
        if (TextUtils.equals(primaryLanguageOf(language), primaryLanguageOf(getResolvedTargetLanguageCode()))) {
            return true;
        }
        String family = primaryLanguageOf(language);
        if (TextUtils.isEmpty(family)) {
            return false;
        }
        for (String restricted : RestrictedLanguagesSelectActivity.getRestrictedLanguages()) {
            if (TextUtils.equals(family, primaryLanguageOf(restricted))) {
                return true;
            }
        }
        return false;
    }

    private static TranslateController.Language createLanguageItem(String code) {
        String normalizedCode = normalizeLanguageCode(code);
        TranslateController.Language language = new TranslateController.Language();
        language.code = normalizedCode;

        Locale appLocale = LocaleController.getInstance().getCurrentLocale();
        if (appLocale == null) {
            appLocale = Locale.getDefault();
        }

        Locale locale = Locale.forLanguageTag(normalizedCode == null ? "" : normalizedCode);
        String displayName = locale.getDisplayName(appLocale);
        String ownDisplayName = locale.getDisplayName(locale);

        if (TextUtils.isEmpty(displayName)) {
            displayName = TranslateAlert2.capitalFirst(TranslateAlert2.languageName(normalizedCode));
        }
        if (TextUtils.isEmpty(ownDisplayName)) {
            ownDisplayName = TranslateAlert2.capitalFirst(TranslateAlert2.systemLanguageName(normalizedCode, true));
        }
        if (TextUtils.isEmpty(displayName)) {
            displayName = normalizedCode == null ? "" : normalizedCode.toUpperCase(Locale.US);
        }
        if (TextUtils.isEmpty(ownDisplayName)) {
            ownDisplayName = displayName;
        }

        language.displayName = TranslateAlert2.capitalFirst(displayName);
        language.ownDisplayName = TranslateAlert2.capitalFirst(ownDisplayName);
        language.q = (language.displayName + " " + language.ownDisplayName).toLowerCase(Locale.US);
        return language;
    }

    public static ArrayList<TranslateController.Language> getCurrentTargetLanguages() {
        BaseTranslator translator = getCurrentTranslator();
        Set<String> supportedLanguages = translator.getSupportedLanguages();
        if (supportedLanguages == null || supportedLanguages.isEmpty()) {
            ArrayList<TranslateController.Language> languages = new ArrayList<>(TranslateController.getLanguages());
            if (translator == TelegramTranslator.getInstance()) {
                languages.removeIf(language -> language == null || TextUtils.isEmpty(language.code) || language.code.contains("-") || language.code.contains("_"));
            }
            return languages;
        }

        HashSet<String> languageCodes = new HashSet<>();
        for (String code : supportedLanguages) {
            String value = normalizeLanguageCode(code);
            if (!TextUtils.isEmpty(value)) {
                languageCodes.add(value);
            }
        }

        ArrayList<TranslateController.Language> result = new ArrayList<>();
        for (String code : languageCodes) {
            result.add(createLanguageItem(code));
        }
        result.sort(Comparator.comparing(language -> language.displayName == null ? "" : language.displayName, String::compareToIgnoreCase));
        return result;
    }

    private static String getResolvedAppLanguageCode() {
        Locale locale = LocaleController.getInstance().getCurrentLocale();
        if (locale == null) {
            locale = Locale.getDefault();
        }

        String language = normalizeLanguageCode(locale.getLanguage());
        String country = locale.getCountry();
        if (!TextUtils.isEmpty(language) && !TextUtils.isEmpty(country)) {
            String regional = normalizeLanguageCode(language + "-" + country);
            if (isTargetLanguageSupportedForCurrentProvider(regional)) {
                return regional;
            }
        }

        if (isTargetLanguageSupportedForCurrentProvider(language)) {
            return language;
        }

        String fallback = normalizeLanguageCode(LocaleController.getString(R.string.LanguageCode));
        if (isTargetLanguageSupportedForCurrentProvider(fallback)) {
            return fallback;
        }

        return language;
    }

    public static String getResolvedTargetLanguageCode(String language) {
        if (TARGET_LANG_FOLLOW_APP.equalsIgnoreCase(language)) {
            return getResolvedAppLanguageCode();
        }
        String value = normalizeLanguageCode(language);
        if (TextUtils.isEmpty(value)) {
            return getResolvedAppLanguageCode();
        }
        return value;
    }

    public static String getResolvedTargetLanguageCode() {
        return getResolvedTargetLanguageCode(ExteraConfig.getTargetLang());
    }

    public static CharSequence[] getTargetLanguageTitles() {
        List<TranslateController.Language> languages = getIndexedTargetLanguages();
        CharSequence[] result = new CharSequence[languages.size() + 1];
        result[0] = LocaleController.getString(R.string.TranslationTargetApp);
        for (int i = 0; i < languages.size(); ++i) {
            TranslateController.Language language = languages.get(i);
            result[i + 1] = language.displayName + (language.ownDisplayName == null || language.ownDisplayName.equalsIgnoreCase(language.displayName) ? "" : " – " + language.ownDisplayName);
        }
        return result;
    }

    public static int getTargetLanguageIndexByCode(String language) {
        if (TARGET_LANG_FOLLOW_APP.equalsIgnoreCase(language)) {
            return 0;
        }
        List<TranslateController.Language> languages = getIndexedTargetLanguages();
        String resolvedLanguage = getResolvedTargetLanguageCode(language);
        for (int i = 0; i < languages.size(); ++i) {
            if (TextUtils.equals(languages.get(i).code, resolvedLanguage)) {
                return i + 1;
            }
        }
        return 0;
    }

    public static String getTargetLanguageCodeByIndex(int idx) {
        if (idx == 0) {
            return TARGET_LANG_FOLLOW_APP;
        }
        List<TranslateController.Language> languages = getIndexedTargetLanguages();
        int languageIndex = idx - 1;
        if (languageIndex >= 0 && languageIndex < languages.size()) {
            return languages.get(languageIndex).code;
        }
        return null;
    }

    public static String getTargetLanguageTitle() {
        if (isTargetLanguageFollowApp()) {
            return LocaleController.getString(R.string.TranslationTargetApp);
        }
        return getLanguageTitleSystem(getResolvedTargetLanguageCode());
    }

    private static String getStoredSendTargetLanguage() {
        return ExteraConfig.getPreferences().getString("targetLangSend", null);
    }

    private static void storeRecentSendTargetLanguage(String language) {
        String normalizedLanguage = normalizeLanguageCode(language);
        if (TextUtils.isEmpty(normalizedLanguage)) {
            return;
        }

        ArrayList<String> recentLanguages = getRecentSendTargetLanguages();
        recentLanguages.removeIf(code -> TextUtils.equals(code, normalizedLanguage));
        recentLanguages.add(0, normalizedLanguage);
        while (recentLanguages.size() > TARGET_LANG_SEND_RECENT_LIMIT) {
            recentLanguages.remove(recentLanguages.size() - 1);
        }

        ExteraConfig.getEditor().putString(TARGET_LANG_SEND_RECENT_KEY, TextUtils.join(",", recentLanguages)).apply();
    }

    public static ArrayList<String> getRecentSendTargetLanguages() {
        ArrayList<String> result = new ArrayList<>();
        String stored = ExteraConfig.getPreferences().getString(TARGET_LANG_SEND_RECENT_KEY, null);
        if (TextUtils.isEmpty(stored)) {
            return result;
        }

        HashSet<String> added = new HashSet<>();
        for (String part : stored.split(",")) {
            String code = normalizeLanguageCode(part);
            if (TextUtils.isEmpty(code) || added.contains(code) || !isTargetLanguageSupportedForCurrentProvider(code)) {
                continue;
            }
            added.add(code);
            result.add(code);
            if (result.size() >= TARGET_LANG_SEND_RECENT_LIMIT) {
                break;
            }
        }
        return result;
    }

    public static String getResolvedSendTargetLanguageCode() {
        String raw = getStoredSendTargetLanguage();
        if (TextUtils.isEmpty(raw)) {
            return TARGET_LANG_SEND_DEFAULT;
        }
        return getResolvedTargetLanguageCode(raw);
    }

    public static int getSendTargetLanguageIndex() {
        String raw = getStoredSendTargetLanguage();
        return getTargetLanguageIndexByCode(TextUtils.isEmpty(raw) ? TARGET_LANG_SEND_DEFAULT : raw);
    }

    public static String getSendTargetLanguageTitle() {
        return getLanguageTitleSystem(getResolvedSendTargetLanguageCode());
    }

    public static boolean isSendTargetLanguageFollowApp() {
        return TARGET_LANG_FOLLOW_APP.equalsIgnoreCase(getStoredSendTargetLanguage());
    }

    public static void setSendTargetLanguage(String language) {
        String targetLanguage = TARGET_LANG_FOLLOW_APP.equalsIgnoreCase(language) ? TARGET_LANG_FOLLOW_APP : normalizeLanguageCode(language);
        if (TextUtils.isEmpty(targetLanguage)) {
            ExteraConfig.getEditor().remove("targetLangSend").apply();
            return;
        }
        if (!TextUtils.equals(targetLanguage, TARGET_LANG_FOLLOW_APP)) {
            storeRecentSendTargetLanguage(targetLanguage);
        }
        if (TextUtils.equals(targetLanguage, TARGET_LANG_SEND_DEFAULT)) {
            ExteraConfig.getEditor().remove("targetLangSend").apply();
            return;
        }
        ExteraConfig.getEditor().putString("targetLangSend", targetLanguage).apply();
    }

    public static void setTargetLanguage(String language) {
        String targetLanguage = TARGET_LANG_FOLLOW_APP.equalsIgnoreCase(language) ? TARGET_LANG_FOLLOW_APP : normalizeLanguageCode(language);
        if (TextUtils.isEmpty(targetLanguage)) {
            targetLanguage = TARGET_LANG_FOLLOW_APP;
        }

        ExteraConfig.setTargetLang(targetLanguage);
        ExteraConfig.getEditor().putString("targetLang", targetLanguage).apply();

        if (!MessagesController.getGlobalMainSettings().getBoolean("translate_button_restricted_languages_changed", false)) {
            MessagesController.getGlobalMainSettings().edit().remove("translate_button_restricted_languages").apply();
            RestrictedLanguagesSelectActivity.invalidateRestrictedLanguages();
            RestrictedLanguagesSelectActivity.checkRestrictedLanguages(false);
            for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; ++i) {
                try {
                    MessagesController.getInstance(i).getTranslateController().checkRestrictedLanguagesUpdate();
                } catch (Exception ignore) {
                }
            }
        }
    }

    public static boolean isTargetLanguageSupportedForCurrentProvider(String language) {
        if (TARGET_LANG_FOLLOW_APP.equalsIgnoreCase(language)) {
            return true;
        }
        String normalizedLanguage = normalizeLanguageCode(language);
        if (TextUtils.isEmpty(normalizedLanguage)) {
            return false;
        }

        BaseTranslator translator = getCurrentTranslator();
        Set<String> supportedLanguages = translator.getSupportedLanguages();
        if (supportedLanguages == null || supportedLanguages.isEmpty()) {
            return translator != TelegramTranslator.getInstance() || !normalizedLanguage.contains("-");
        }

        for (String code : supportedLanguages) {
            if (TextUtils.equals(normalizeLanguageCode(code), normalizedLanguage)) {
                return true;
            }
        }
        return false;
    }

    public static void ensureTargetLanguageCompatibleWithProvider() {
        if (!isTargetLanguageSupportedForCurrentProvider(ExteraConfig.getTargetLang())) {
            setTargetLanguage(TARGET_LANG_FOLLOW_APP);
        }
    }

    public static String formatUserAgent() {
        String androidVersion = String.valueOf(Utilities.random.nextInt(7) + 6);
        String deviceModel = DEVICE_MODELS[Utilities.random.nextInt(DEVICE_MODELS.length)];
        String chromeVersion = CHROME_VERSIONS[Utilities.random.nextInt(CHROME_VERSIONS.length)];
        return String.format("Mozilla/5.0 (Linux; Android %s; %s) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/%s Mobile Safari/537.36", androidVersion, deviceModel, chromeVersion);
    }

    public static String getLanguageTitleSystem(String isoCode) {
        if ("none".equals(isoCode)) {
            return LocaleController.getString(R.string.None);
        }
        String title = TranslateAlert2.capitalFirst(TranslateAlert2.languageName(isoCode));
        if (!TextUtils.isEmpty(title)) {
            return title;
        }
        String code = normalizeLanguageCode(isoCode);
        return TextUtils.isEmpty(code) ? "" : code.toUpperCase(Locale.US);
    }

    public static String getLanguageDisplayName(String isoCode) {
        String code = normalizeLanguageCode(isoCode);
        if (TextUtils.isEmpty(code)) {
            return null;
        }
        Locale locale = Locale.forLanguageTag(code);
        String name = locale.getDisplayName(locale);
        if (TextUtils.isEmpty(locale.getLanguage()) || TextUtils.isEmpty(name) || name.equalsIgnoreCase(code)) {
            return null;
        }
        return TranslateAlert2.capitalFirst(name);
    }

    public static String getLangCodeByIndex(int idx) {
        List<TranslateController.Language> languages = getLanguages();
        if (idx >= 0 && idx < languages.size()) {
            return languages.get(idx).code;
        }
        return null;
    }

    public static int getLanguageIndexByIso(String iso) {
        List<TranslateController.Language> languages = getLanguages();
        return IntStream.range(0, languages.size())
                .filter(idx -> TextUtils.equals(languages.get(idx).code, iso))
                .findFirst()
                .orElse(-1);
    }

    public static String[] getLanguageTitles() {
        return getLanguages().stream()
                .map(lang -> lang.displayName + (lang.ownDisplayName == null ? "" : " – " + lang.ownDisplayName))
                .toArray(String[]::new);
    }

    private static final int DETECTED_LANGUAGES_LIMIT = 200;
    private static final LinkedHashMap<String, String> detectedLanguages = new LinkedHashMap<>(16, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > DETECTED_LANGUAGES_LIMIT;
        }
    };

    @Nullable
    public static String getDetectedLanguage(MessageObject message) {
        if (message == null) {
            return null;
        }
        if (message.messageOwner != null && !TextUtils.isEmpty(message.messageOwner.originalLanguage)) {
            return message.messageOwner.originalLanguage;
        }
        return detectedLanguages.get(detectedLanguageKey(message));
    }

    public static boolean isMessageLanguageRestricted(MessageObject message) {
        String language = getDetectedLanguage(message);
        return language != null && !TranslateController.UNKNOWN_LANGUAGE.equals(language) && isRestrictedLanguage(language);
    }

    public static void detectMessageLanguage(MessageObject message, MessageObject.GroupedMessages group, Utilities.Callback<String> done) {
        if (message == null) {
            if (done != null) {
                done.run(null);
            }
            return;
        }
        String cached = getDetectedLanguage(message);
        if (cached != null) {
            if (done != null) {
                done.run(cached);
            }
            return;
        }
        CharSequence text = ChatUtils.getInstance().getMessageText(message, group);
        if (TextUtils.isEmpty(text) || !LanguageDetector.hasSupport()) {
            if (done != null) {
                done.run(null);
            }
            return;
        }
        String key = detectedLanguageKey(message);
        LanguageDetector.detectLanguage(text.toString(), language -> AndroidUtilities.runOnUIThread(() -> {
            String detected = TextUtils.isEmpty(language) ? TranslateController.UNKNOWN_LANGUAGE : language;
            detectedLanguages.put(key, detected);
            if (done != null) {
                done.run(detected);
            }
        }), err -> AndroidUtilities.runOnUIThread(() -> {
            if (done != null) {
                done.run(null);
            }
        }));
    }

    private static String detectedLanguageKey(MessageObject message) {
        return message.getDialogId() + ":" + message.getId();
    }

    public static void translateWithAlert(MessageObject selectedObject, MessageObject.GroupedMessages selectedObjectGroup, TLRPC.InputPeer peer, int msgId, BaseFragment fragment) {
        if (selectedObject == null || fragment == null || fragment.getContext() == null) {
            return;
        }

        ChatActivity chatActivity = fragment instanceof ChatActivity ? (ChatActivity) fragment : null;

        Utilities.CallbackReturn<URLSpan, Boolean> onLinkPress = link -> {
            if (chatActivity != null) {
                chatActivity.didPressMessageUrl(link, false, selectedObject, null);
            }
            return true;
        };

        ArrayList<TLRPC.MessageEntity> entities = selectedObject.messageOwner != null ? selectedObject.messageOwner.entities : null;
        CharSequence text = ChatUtils.getInstance().getMessageText(selectedObject, selectedObjectGroup);
        detectMessageLanguage(selectedObject, selectedObjectGroup, lang -> {
            if (fragment.getContext() == null) {
                return;
            }
            String fromLang = TranslateController.UNKNOWN_LANGUAGE.equals(lang) ? null : lang;
            TranslateAlert2.showAlert(fragment.getContext(), fragment, UserConfig.selectedAccount, peer, msgId, false, fromLang, TranslateAlert2.getToLanguage(), text, entities, false, onLinkPress, () -> {
                if (chatActivity != null) {
                    chatActivity.dimBehindView(false);
                }
            });
        });
    }

    public static void translateWithDefault(CharSequence text, TLRPC.InputPeer peer, int msgId, String toLang, ArrayList<TLRPC.MessageEntity> entities, TranslateCallback callback) {
        String resolvedToLang = getResolvedTargetLanguageCode(toLang);
        TLRPC.TL_messages_translateText req = new TLRPC.TL_messages_translateText();
        TLRPC.TL_textWithEntities textWithEntities = new TLRPC.TL_textWithEntities();
        textWithEntities.text = text == null ? "" : text.toString();
        if (entities != null) {
            textWithEntities.entities = entities;
        }
        if (peer != null) {
            req.flags |= 1;
            req.peer = peer;
            req.id.add(msgId);
        } else {
            req.flags |= 2;
            req.text.add(textWithEntities);
        }
        String lang = resolvedToLang;
        if (lang != null) {
            lang = lang.trim();
        }
        if ("nb".equals(lang)) {
            lang = "no";
        }
        req.to_lang = lang;

        int reqId = ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (res, err) -> {
            if (err != null && "TRANSLATIONS_DISABLED_ALT".equalsIgnoreCase(err.text)) {
                GoogleTranslator.getInstance().translate(textWithEntities.text, "auto", resolvedToLang, entities, callback);
            } else if (res instanceof TLRPC.TL_messages_translateResult tr &&
                    !tr.result.isEmpty() &&
                    tr.result.get(0) != null &&
                    tr.result.get(0).text != null
            ) {
                TLRPC.TL_textWithEntities translatedTextWithEntities = tr.result.get(0);
                TLRPC.TL_textWithEntities preprocessedText = TranslateAlert2.preprocess(textWithEntities, translatedTextWithEntities);
                String translatedText = preprocessedText != null && preprocessedText.text != null ? preprocessedText.text : translatedTextWithEntities.text;
                if (TextUtils.isEmpty(translatedText)) {
                    AndroidUtilities.runOnUIThread(callback::onFailed);
                    return;
                }
                AndroidUtilities.runOnUIThread(() -> {
                    callback.onSuccess(tr, err);
                    callback.onSuccess(preprocessedText != null ? preprocessedText : translatedTextWithEntities);
                    callback.onSuccess(translatedText);
                });
            } else {
                AndroidUtilities.runOnUIThread(callback::onFailed);
            }
        });

        callback.onReqId(reqId);
    }

    public interface TranslateCallback {
        default void onSuccess(String translatedText) {}
        default void onSuccess(TLRPC.TL_textWithEntities translatedTextWithEntities) {}
        default void onSuccess(TLObject object, TLRPC.TL_error error) {}
        void onFailed();
        default void onFailed(TranslationError error) {
            onFailed();
        }
        default void onReqId(int reqId) {}
    }

    public static void translate(CharSequence text, TranslateCallback callback) {
        translate(text, TranslateAlert2.getToLanguage(), (ArrayList<TLRPC.MessageEntity>) null, callback);
    }

    public static void translate(CharSequence text, ArrayList<TLRPC.MessageEntity> entities, TranslateCallback callback) {
        translate(text, TranslateAlert2.getToLanguage(), entities, callback);
    }

    public static void translate(CharSequence text, String toLang, TranslateCallback callback) {
        translate(text, toLang, (ArrayList<TLRPC.MessageEntity>) null, callback);
    }

    public static void translate(CharSequence text, String toLang, ArrayList<TLRPC.MessageEntity> entities, TranslateCallback callback) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        String targetLanguage = getResolvedTargetLanguageCode(toLang);
        if (LanguageDetector.hasSupport()) {
            LanguageDetector.detectLanguage(text.toString(), lng -> {
                String fromLang = "auto";
                if (lng != null && !lng.equals("und")) {
                    fromLang = lng;
                }
                translate(text, fromLang, targetLanguage, entities, callback);
            }, e -> translate(text, "auto", targetLanguage, entities, callback));
        } else {
            translate(text, "auto", targetLanguage, entities, callback);
        }
    }

    public static void translate(CharSequence text, String fromLang, String toLang, TranslateCallback callback) {
        translate(text, fromLang, toLang, null, callback);
    }

    public static void translate(CharSequence text, String fromLang, String toLang, ArrayList<TLRPC.MessageEntity> entities, TranslateCallback callback) {
        BaseTranslator translator = getCurrentTranslator();
        if (translator == TelegramTranslator.getInstance()) {
            translateWithDefault(text, null, 0, toLang, entities, callback);
            return;
        }
        if (!translator.isLanguageSupported(toLang)) {
            translator = GoogleTranslator.getInstance();
        }
        if (translator instanceof HttpTranslator httpTranslator && entities != null && !entities.isEmpty()) {
            httpTranslator.translate(text.toString(), fromLang, toLang, entities, callback);
            return;
        }
        BaseTranslator selectedTranslator = translator;
        selectedTranslator.translate(text.toString(), fromLang, toLang, new TranslateCallback() {
            @Override
            public void onSuccess(String translatedText) {
                if (TextUtils.isEmpty(translatedText)) {
                    callback.onFailed();
                    return;
                }
                callback.onSuccess(translatedText);
                TLRPC.TL_textWithEntities textWithEntities = new TLRPC.TL_textWithEntities();
                textWithEntities.text = translatedText;
                callback.onSuccess(textWithEntities);
            }

            @Override
            public void onSuccess(TLObject object, TLRPC.TL_error error) {
                callback.onSuccess(object, error);
            }

            @Override
            public void onFailed() {
                callback.onFailed();
            }

            @Override
            public void onFailed(TranslationError error) {
                callback.onFailed(error);
            }

            @Override
            public void onReqId(int reqId) {
                callback.onReqId(reqId);
            }
        });
    }

    public static BaseTranslator getCurrentTranslator() {
        return TranslationProviders.current();
    }

    public static boolean isAlternativeProvider() {
        return TranslationProviders.isAlternative();
    }

    public static String getCurrentTranslatorName() {
        return getCurrentTranslator().getDisplayName();
    }
}
