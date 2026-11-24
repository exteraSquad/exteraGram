/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.backup;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.ai.AiConfig;
import com.exteragram.messenger.ai.network.backend.OnDeviceAvailability;
import com.exteragram.messenger.pillstack.core.PillStackConfig;
import com.exteragram.messenger.pillstack.custom.CustomPillStore;
import com.exteragram.messenger.plugins.PluginsController;
import com.exteragram.messenger.translator.TranslationProviders;
import com.exteragram.messenger.utils.chats.ChatUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ShareAlert;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PreferencesUtils {

    public static final String EXTENSION = ".extera";
    private static final String MAIN_CONFIG = "mainconfig";
    private static final int MAX_STRING_LENGTH = 1024 * 1024;
    private static final long MAX_BACKUP_FILE_SIZE = 4 * 1024 * 1024;
    private static final Set<String> excludedExteraKeys = Set.of(
            "pluginsUnknownSources",
            "pluginsDevMode",
            "pluginsSafeMode",
            "pluginsStaticAnalysis",
            "pluginsDisableArtOpts",
            "editingIconPackId",
            "iconPacksLayout",
            "iconPacksHidden",
            "updateScheduleTimestamp",
            "sdkUpdateScheduleTimestamp",
            "selectedService",
            "selectedServiceId",
            "lastActivePillId",
            "customWeatherLocation",
            "customWeatherAddress",
            "debugCameraMetrics",
            "forceCompactSavedMusic",
            "disableApiRequests",
            "disableChatFadeWallpaperBlend",
            "chatFadeUseWhiteBackground",
            "heapMonitorEnabled",
            "heapMonitorLimitMb",
            "loadMonitorEnabled",
            "loadMonitorCpuPercent",
            "freezeMonitorEnabled",
            "freezeMonitorThresholdMs"
    );
    private static final BackupItem[] extraExteraKeys = new BackupItem[]{
            new BackupItem("bottomNavigationBarMode", Integer.class),
            new BackupItem("mainMenuLayout", String.class),
            new BackupItem("mainMenuHiddenItems", String.class),
            new BackupItem("targetLangSend", String.class),
            new BackupItem("pluginsEngine", Boolean.class),
            new BackupItem("pinnedPlugins", Set.class),
            new BackupItem("hideStickerTime", Boolean.class),
            new BackupItem("tabCounter", Boolean.class),
            new BackupItem("springAnimations", Boolean.class),
            new BackupItem("aospTransitions", Boolean.class),
            new BackupItem("stickerCornerRoundness", Integer.class),

            new BackupItem("saveHistory", Boolean.class),
            new BackupItem("responseStreaming", Boolean.class),
            new BackupItem("temperature", Integer.class),
            new BackupItem("showResponseOnly", Boolean.class),
            new BackupItem("insertAsQuote", Boolean.class),
            new BackupItem("onDeviceReasoning", Boolean.class),
            new BackupItem("onDevicePreviewModel", Boolean.class),
            new BackupItem("onDeviceFastModel", Boolean.class),
            new BackupItem("replaceTelegramEditor", Boolean.class),
            new BackupItem("replaceTelegramSummaries", Boolean.class),
            new BackupItem("selectedRole", String.class),

            new BackupItem("infiniteScrolling", Boolean.class),
            new BackupItem("useCurrentLocation", Boolean.class),
            new BackupItem("gramTargetCurrency", String.class),
            new BackupItem("btcTargetCurrency", String.class),
            new BackupItem("usdTargetCurrency", String.class),
    };
    private static final BackupItem[] aiConfigKeys = new BackupItem[]{
            new BackupItem("roles", String.class),
    };
    private static final BackupItem[] pillStackConfigKeys = new BackupItem[]{
            new BackupItem("activePills", String.class),
            new BackupItem("hiddenPills", String.class),
    };
    private static final BackupItem[] customPillsKeys = new BackupItem[]{
            new BackupItem(CustomPillStore.KEY_PILLS, String.class),
    };
    private static final BackupItem[] mainConfigKeys = new BackupItem[]{
            new BackupItem("ChatSwipeAction", Integer.class),
            new BackupItem("mediaColumnsCount", Integer.class),
            new BackupItem("bubbleRadius", Integer.class),
            new BackupItem("fons_size", Integer.class),
    };
    private final static String[] configs = new String[]{
            ExteraConfig.CONFIG_NAME,
            AiConfig.CONFIG_NAME,
            PillStackConfig.CONFIG_NAME,
            CustomPillStore.CONFIG_NAME,
            MAIN_CONFIG,
    };
    private static PreferencesUtils instance;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static PreferencesUtils getInstance() {
        if (instance == null) {
            instance = new PreferencesUtils();
        }
        return instance;
    }

    public static void clearPreferences() {
        AiConfig.getEditor().clear().apply();
        PillStackConfig.getEditor().clear().apply();
        ExteraConfig.getEditor().clear().apply();
        ExteraConfig.reloadConfig();
        PillStackConfig.reloadConfig();
    }

    private static Context getContext() {
        if (ApplicationLoader.applicationContext != null) {
            return ApplicationLoader.applicationContext;
        }
        return AndroidUtilities.getActivity();
    }

    public static SharedPreferences getPreferences(String name) {
        return getContext().getSharedPreferences(name, Activity.MODE_PRIVATE);
    }

    public static String generateBackupName(String custom) {
        return (custom != null ? custom : "backup") + "-" +
                Utilities.generateRandomString(4) +
                EXTENSION;
    }

    public static boolean isBackupFile(@Nullable String name) {
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(EXTENSION);
    }

    private BackupItem findBackupItem(String config, String key) {
        if (TextUtils.isEmpty(config) || TextUtils.isEmpty(key)) {
            return null;
        }
        switch (config) {
            case ExteraConfig.CONFIG_NAME -> {
                for (BackupItem item : extraExteraKeys) {
                    if (item.key.equals(key)) {
                        return item;
                    }
                }
                if (excludedExteraKeys.contains(key)) {
                    return null;
                }
                for (BackupItem item : ExteraConfig.getBackupKeys()) {
                    if (item.key.equals(key)) {
                        return item;
                    }
                }
                return null;
            }
            case AiConfig.CONFIG_NAME -> {
                return findBackupItem(aiConfigKeys, key);
            }
            case PillStackConfig.CONFIG_NAME -> {
                return findBackupItem(pillStackConfigKeys, key);
            }
            case CustomPillStore.CONFIG_NAME -> {
                return findBackupItem(customPillsKeys, key);
            }
            case MAIN_CONFIG -> {
                return findBackupItem(mainConfigKeys, key);
            }
        }
        return null;
    }

    private BackupItem findBackupItem(BackupItem[] items, String key) {
        for (BackupItem item : items) {
            if (item.key.equals(key)) {
                return item;
            }
        }
        return null;
    }

    private boolean isExpectedValue(String config, String key, Object value) {
        BackupItem item = findBackupItem(config, key);
        if (item == null || value == null) {
            return false;
        }

        JsonElement element = value instanceof JsonElement
                ? (JsonElement) value
                : gson.toJsonTree(value);

        try {
            if (item.clazz.equals(Boolean.class)) {
                return element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean();
            }
            if (item.clazz.equals(Float.class)) {
                return element.isJsonPrimitive()
                        && element.getAsJsonPrimitive().isNumber()
                        && isExpectedFloat(key, element.getAsFloat());
            }
            if (item.clazz.equals(String.class)) {
                return element.isJsonPrimitive()
                        && element.getAsJsonPrimitive().isString()
                        && isExpectedString(config, key, element.getAsString());
            }
            if (item.clazz.equals(Set.class)) {
                return isExpectedStringSet(key, element);
            }
            if (item.clazz.equals(Long.class)) {
                return false;
            }

            Integer number = getExactInteger(element);
            return number != null && isExpectedInteger(key, number);
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    private Integer getExactInteger(JsonElement element) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        try {
            return new BigDecimal(element.getAsString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    private Long getExactLong(JsonElement element) {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        try {
            return new BigDecimal(element.getAsString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    private boolean isExpectedFloat(String key, float f) {
        if (!Float.isFinite(f)) {
            return false;
        }
        return switch (key) {
            case "avatarCorners" -> f >= 0 && f <= 28f;
            case "sectionRadius" -> f >= 0 && f <= ExteraConfig.SECTION_RADIUS_MAX_DP;
            case "stickerSize" -> f >= 4 && f <= 20f;
            case "predictiveBackIntensity" -> f >= 0 && f <= 5f;
            case "flashWarmth", "flashIntensity" -> f >= 0 && f <= 1;
            default -> false;
        };
    }

    private boolean isExpectedInteger(String key, int n) {
        return switch (key) {
            case "tabIcons", "tabletMode", "downloadSpeedBoost", "showIdAndDc", "stickerShape", "bottomButton",
                 "videoMessagesCamera", "cameraType", "iconPack", "bottomNavigationBarMode",
                 "dividerStyle", "glassOutlineStyle", "stickerTimeMode", "transitionAnimation" ->
                    n >= 0 && n <= 2;
            case "translationProvider" -> n >= 0 && n <= TranslationProviders.getLastIndex();
            case "titleText", "doubleTapSeekDuration", "stickerCornerRoundness" -> n >= 0 && n <= 3;
            case "eventType" -> n >= 0 && n <= 4;
            case "ChatSwipeAction" -> n >= 0 && n <= 5;
            case "doNotUseProxy" -> n >= 0 && n <= 7;
            case "doubleTapAction" -> n >= 0 && n <= 8;
            case "doubleTapActionOutOwner" -> n >= 0 && n <= 9;
            case "temperature" -> n >= 0 && n <= 20;
            case "mediaColumnsCount" -> n >= 2 && n <= 9;
            case "bubbleRadius" -> n >= 0 && n <= 17;
            case "fons_size" -> n >= 12 && n <= 30;
            default -> false;
        };
    }

    private boolean isExpectedString(String config, String key, String value) {
        if (value == null || value.length() > MAX_STRING_LENGTH) {
            return false;
        }
        switch (key) {
            case "targetLang", "targetLangSend" -> {
                return value.equalsIgnoreCase("app")
                        || value.matches("^[a-zA-Z]{1,8}(-[a-zA-Z0-9]{1,8})*$");
            }
            case "swipeActions" -> {
                return value.isEmpty() || value.matches("^\\d{1,2}(,\\d{1,2})*$");
            }
            case "customSavePath" -> {
                return value.matches("^(?!\\.{1,2}$)[A-Za-z0-9._ -]{1,255}$");
            }
            case "selectedRole" -> {
                return !TextUtils.isEmpty(value) && value.length() <= 256;
            }
            case "mainMenuLayout" -> {
                return isExpectedMainMenuLayout(value, true);
            }
            case "mainMenuHiddenItems" -> {
                return isExpectedMainMenuLayout(value, false);
            }
        }
        if (AiConfig.CONFIG_NAME.equals(config) && key.equals("roles")) {
            return isExpectedRoles(value);
        }
        if (PillStackConfig.CONFIG_NAME.equals(config)
                && (key.equals("activePills") || key.equals("hiddenPills"))) {
            return isExpectedPillsLayout(value);
        }
        if (CustomPillStore.CONFIG_NAME.equals(config) && key.equals(CustomPillStore.KEY_PILLS)) {
            return isExpectedCustomPills(value);
        }
        if (key.equals("gramTargetCurrency")
                || key.equals("btcTargetCurrency")
                || key.equals("usdTargetCurrency")) {
            return value.matches("^[A-Z]{3,5}$");
        }
        return !TextUtils.isEmpty(value);
    }

    private boolean isExpectedStringSet(String key, JsonElement element) {
        if (!key.equals("pinnedPlugins") || !element.isJsonArray() || element.getAsJsonArray().size() > 1000) {
            return false;
        }
        HashSet<String> values = new HashSet<>();
        for (JsonElement entry : element.getAsJsonArray()) {
            if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                return false;
            }
            String value = entry.getAsString();
            if (TextUtils.isEmpty(value) || value.length() > 255 || !values.add(value)) {
                return false;
            }
        }
        return true;
    }

    private boolean isExpectedMainMenuLayout(String value, boolean allowDividers) {
        JsonElement element = gson.fromJson(value, JsonElement.class);
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() > 100) {
            return false;
        }
        HashSet<Integer> menuItems = new HashSet<>();
        for (JsonElement entry : element.getAsJsonArray()) {
            Integer id = getExactInteger(entry);
            if (id == null) {
                return false;
            }
            if (id == -1) {
                if (!allowDividers) {
                    return false;
                }
            } else if (!menuItems.add(id)) {
                return false;
            }
        }
        return true;
    }

    private boolean isExpectedRoles(String value) {
        JsonElement element = gson.fromJson(value, JsonElement.class);
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() > 100) {
            return false;
        }
        HashSet<String> names = new HashSet<>();
        for (JsonElement entry : element.getAsJsonArray()) {
            if (!entry.isJsonObject()) {
                return false;
            }
            JsonObject role = entry.getAsJsonObject();
            if (!isRequiredString(role, "name", 256)
                    || !isRequiredString(role, "prompt", 65536)
                    || !names.add(role.get("name").getAsString())) {
                return false;
            }
            if (role.has("emojiId") && !role.get("emojiId").isJsonNull() && getExactLong(role.get("emojiId")) == null) {
                return false;
            }
            if (role.has("isSuggestion")
                    && !role.get("isSuggestion").isJsonNull()
                    && (!role.get("isSuggestion").isJsonPrimitive()
                    || !role.get("isSuggestion").getAsJsonPrimitive().isBoolean())) {
                return false;
            }
        }
        return true;
    }

    private boolean isRequiredString(JsonObject object, String key, int maxLength) {
        return object.has(key)
                && !object.get(key).isJsonNull()
                && object.get(key).isJsonPrimitive()
                && object.get(key).getAsJsonPrimitive().isString()
                && !TextUtils.isEmpty(object.get(key).getAsString())
                && object.get(key).getAsString().length() <= maxLength;
    }

    private boolean isExpectedCustomPills(String value) {
        JsonElement element = gson.fromJson(value, JsonElement.class);
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() > CustomPillStore.MAX_PILLS) {
            return false;
        }
        return CustomPillStore.parseList(value).size() == element.getAsJsonArray().size();
    }

    private boolean isExpectedPillsLayout(String value) {
        if (value.length() > 4096 || TextUtils.isEmpty(value)) {
            return TextUtils.isEmpty(value);
        }
        HashSet<Integer> ids = new HashSet<>();
        for (String part : value.split(",")) {
            try {
                int id = Integer.parseInt(part.trim());
                if (id <= 0 || id > 100000 || !ids.add(id)) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return ids.size() <= 100;
    }

    public void exportSettings(BaseFragment fragment) {
        File file = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), generateBackupName(null));

        if (file.exists()) {
            file.delete();
        }

        try {
            FileOutputStream stream = new FileOutputStream(file);
            OutputStreamWriter writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
            writer.write(getBackup(true));
            writer.flush();
            writer.close();

            fragment.showDialog(new ShareAlert(fragment.getParentActivity(), null, null, file.getAbsolutePath(), null, null, false, null, null, false, false, false, null, null) {
                @Override
                protected void onSend(LongSparseArray<TLRPC.Dialog> dids, int count, TLRPC.TL_forumTopic topic, boolean showToast) {
                    if (showToast) {
                        AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.SettingsSaved)).show(), 250);
                    }
                }
            });
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public String getBackup(boolean encrypt) {
        AiConfig.ensureConfigMigrated();
        JsonObject jsonPrefs = new JsonObject();

        for (String config : configs) {
            JsonObject object = toJsonObject(config, getPreferences(config).getAll());
            if (!object.isEmpty()) {
                jsonPrefs.add(config, object);
            }
        }

        String backupData = gson.toJson(jsonPrefs);

        if (encrypt) {
            return InvisibleEncryptor.encode(backupData);
        } else {
            return backupData;
        }
    }

    private JsonObject toJsonObject(String config, Map<String, ?> map) {
        JsonObject jsonObject = new JsonObject();

        for (Map.Entry<String, ?> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (isExpectedValue(config, key, value)) {
                JsonElement jsonValue = gson.toJsonTree(value);
                jsonObject.add(key, jsonValue);
            }
        }

        return jsonObject;
    }

    public void importSettings(File backupFile, Activity activity, INavigationLayout parentLayout) {
        if (!isBackup(backupFile)) {
            return;
        }

        AiConfig.ensureConfigMigrated();
        JsonObject jsonObject = getJsonObject(backupFile);

        for (String config : configs) {
            importConfig(jsonObject, config);
        }

        ExteraConfig.reloadConfig();
        PillStackConfig.setConfigLoaded(false);
        CustomPillStore.reload();
        PillStackConfig.reloadConfig();
        SharedConfig.reloadConfig();
        OnDeviceAvailability.onModelConfigChanged(null);

        PluginsController.getInstance().restart();
        LocaleController.getInstance().recreateFormatters();
        Theme.reloadAllResources(activity);

        parentLayout.rebuildFragments(0);

        NotificationCenter currentAccount = AccountInstance.getInstance(UserConfig.selectedAccount).getNotificationCenter();
        currentAccount.postNotificationName(NotificationCenter.reloadInterface);
        currentAccount.postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_CHAT);
        currentAccount.postNotificationName(NotificationCenter.mainUserInfoChanged);
        currentAccount.postNotificationName(NotificationCenter.dialogFiltersUpdated);
    }

    private void importConfig(JsonObject jsonObject, String configType) {
        JsonObject configObject = getConfigObject(jsonObject, configType);
        if (configObject != null) {
            SharedPreferences.Editor editor = getPreferences(configType).edit();
            for (String key : configObject.keySet()) {
                JsonElement value = configObject.get(key);
                BackupItem item = findBackupItem(configType, key);
                if (item == null || !isExpectedValue(configType, key, value)) {
                    continue;
                }
                if (item.clazz.equals(Boolean.class)) {
                    editor.putBoolean(key, value.getAsBoolean());
                } else if (item.clazz.equals(Float.class)) {
                    editor.putFloat(key, value.getAsFloat());
                } else if (item.clazz.equals(String.class)) {
                    editor.putString(key, value.getAsString());
                } else if (item.clazz.equals(Set.class)) {
                    HashSet<String> values = new HashSet<>();
                    for (JsonElement entry : value.getAsJsonArray()) {
                        values.add(entry.getAsString());
                    }
                    editor.putStringSet(key, values);
                } else {
                    editor.putInt(key, value.getAsInt());
                }

                if (ExteraConfig.CONFIG_NAME.equals(configType) && key.equals("iconPack")) {
                    editor.remove("iconPacksLayout");
                    editor.remove("iconPacksHidden");
                }
            }
            editor.apply();
        }
    }

    private JsonObject getConfigObject(JsonObject jsonObject, String configType) {
        if (jsonObject.has(configType) && jsonObject.get(configType).isJsonObject()) {
            return jsonObject.getAsJsonObject(configType);
        }
        if (MAIN_CONFIG.equals(configType)) {
            for (String key : jsonObject.keySet()) {
                if (key.matches("^" + MAIN_CONFIG + "\\d+$") && jsonObject.get(key).isJsonObject()) {
                    return jsonObject.getAsJsonObject(key);
                }
            }
        }
        return null;
    }

    public JsonObject getJsonObject(File file) {
        try {
            String data = readAndDecryptFile(file);

            if (data != null) {
                return gson.fromJson(data, JsonObject.class);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    private String readAndDecryptFile(File file) throws IOException {
        if (file.length() > MAX_BACKUP_FILE_SIZE) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            String data = stringBuilder.toString();

            if (InvisibleEncryptor.isEncrypted(data)) {
                data = InvisibleEncryptor.decode(data);
            }

            return data;
        }
    }

    private boolean checkKeys(JsonObject jsonObject) {
        for (String config : configs) {
            JsonObject configObject = getConfigObject(jsonObject, config);
            if (configObject != null) {
                for (String key : configObject.keySet()) {
                    JsonElement value = configObject.get(key);
                    if (isExpectedValue(config, key, value)) {
                        return true;
                    } else {
                        FileLog.e("Unexpected value: " + key + " " + value);
                    }
                }
            }
        }
        return false;
    }

    public boolean isBackup(MessageObject messageObject) {
        var path = ChatUtils.getInstance().getPathToMessage(messageObject);
        return messageObject != null && messageObject.getDocumentName() != null && !TextUtils.isEmpty(path) && isBackup(new File(path));
    }

    public boolean isBackup(File file) {
        if (file == null || !isBackupFile(file.getName())) {
            return false;
        }
        JsonObject jsonObject = getJsonObject(file);
        return jsonObject != null && checkKeys(jsonObject);
    }

    public int getDiff(File file) {
        return getDiff(getJsonObject(file));
    }

    public int getDiff(JsonObject newSettings) {
        if (newSettings == null) {
            return 0;
        }
        int differenceCount = 0;
        JsonObject currentSettings = gson.fromJson(getBackup(false), JsonObject.class);

        for (String sourceConfigType : newSettings.keySet()) {
            String configType = sourceConfigType.matches("^" + MAIN_CONFIG + "\\d+$")
                    ? MAIN_CONFIG
                    : sourceConfigType;
            if (!newSettings.get(sourceConfigType).isJsonObject()) {
                continue;
            }
            JsonObject newConfig = newSettings.getAsJsonObject(sourceConfigType);
            if (currentSettings.has(configType)) {
                JsonObject currentConfig = currentSettings.getAsJsonObject(configType);

                for (String key : newConfig.keySet()) {
                    Object v1 = newConfig.get(key), v2 = currentConfig.get(key);
                    if (!currentConfig.has(key) || !v1.equals(v2)) {
                        if (isExpectedValue(configType, key, v1)) {
                            differenceCount++;
                        }
                    }
                }
            } else {
                for (String key : newConfig.keySet()) {
                    if (isExpectedValue(configType, key, newConfig.get(key))) {
                        differenceCount++;
                    }
                }
            }
        }

        return differenceCount;
    }

    public static class BackupItem {
        public String key;
        public Class<?> clazz;

        public BackupItem(String key) {
            this(key, Boolean.class);
        }

        public BackupItem(String key, Class<?> clazz) {
            this.key = key;
            this.clazz = clazz;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (this == obj) {
                return true;
            }

            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }

            BackupItem other = (BackupItem) obj;

            return key.equals(other.key);
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }
}
