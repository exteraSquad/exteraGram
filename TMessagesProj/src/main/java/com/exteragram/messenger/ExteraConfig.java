/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger;

import android.app.Activity;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.exteragram.messenger.camera.CameraXUtils;
import com.exteragram.messenger.icons.BaseIconSet;
import com.exteragram.messenger.icons.EmptyIconSet;
import com.exteragram.messenger.icons.SolarIconSet;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.TLRPC;

import java.util.Arrays;

public class ExteraConfig {

    private static final Object sync = new Object();

    // Appearance
    public static float avatarCorners;
    public static boolean hideActionBarStatus;
    public static boolean hideAllChats;
    public static boolean centerTitle;
    public static int tabIcons; // icons with titles - 0, titles - 1, icons - 2
    public static int tabStyle;
    public static int actionBarTitle;

    public static boolean useSolarIcons;

    public static boolean squareFab;
    public static boolean forceBlur;
    public static boolean forceSnow;
    public static boolean useSystemFonts;
    public static boolean newSwitchStyle;
    public static boolean disableDividers;

    public static int eventType;
    public static boolean alternativeOpenAnimation;
    public static boolean changeStatus, newGroup, newSecretChat, newChannel, contacts, calls, peopleNearby, archivedChats, savedMessages, scanQr, inviteFriends, telegramFeatures;

    // General
    public static int cameraType;
    public static boolean useCameraXOptimizedMode;
    public static int cameraResolution;

    public static boolean disableNumberRounding;
    public static boolean formatTimeWithSeconds;
    public static boolean disableProximitySensor;
    public static int tabletMode;

    public static int downloadSpeedBoost;
    public static boolean uploadSpeedBoost;

    public static boolean hidePhoneNumber;
    public static int showIdAndDc;

    public static boolean disableAnimatedAvatars;
    public static boolean premiumAutoPlayback;
    public static boolean hidePremiumStickersTab;
    public static boolean hideFeaturedEmoji;
    public static boolean hideSendAsChannel;

    public static boolean archiveOnPull;
    public static boolean disableUnarchiveSwipe;

    // Chats
    public static float stickerSize;

    public static int stickerShape;

    public static boolean hideStickerTime;
    public static boolean unlimitedRecentStickers;
    public static boolean hideCategories;

    public static int doubleTapAction;
    public static int doubleTapActionOutOwner;

    public static int bottomButton;
    public static boolean hideKeyboardOnScroll;
    public static boolean permissionsShortcut;
    public static boolean administratorsShortcut;
    public static boolean membersShortcut;
    public static boolean recentActionsShortcut;
    public static boolean disableJumpToNextChannel;
    public static boolean showActionTimestamps;
    public static boolean hideShareButton;
    public static boolean dateOfForwardedMsg;
    public static boolean showMessageID;
    public static boolean addCommaAfterMention;

    public static int sendPhotosQuality;
    public static boolean hideCameraTile;
    public static boolean disableEdgeAction;

    public static boolean staticZoom;
    public static int videoMessagesCamera; // front rear ask
    public static boolean rememberLastUsedCamera;
    public static boolean pauseOnMinimize;
    public static boolean disablePlayback;

    // Updates
    public static long lastUpdateCheckTime;
    public static long updateScheduleTimestamp;
    public static boolean checkUpdatesOnLaunch;

    // Other
    private static final long[] OFFICIAL_CHANNELS = {1233768168, 1524581881, 1571726392, 1632728092, 1638754701, 1779596027, 1172503281};
    private static final long[] DEVS = {963080346, 1282540315, 1374434073, 388099852, 1972014627, 168769611, 480000401, 5307590670L, 639891381, 1773117711, 5330087923L};
    public static long channelToSave;
    public static String targetLanguage;
    public static final CharSequence[] supportedLanguages = new CharSequence[]{
            "Arabic (AR)", "Azerbaijani (AZ)", "Belarusian (BE)", "Catalan (CA)", "Chinese (ZH)",
            "Croatian (HR)", "Czech (CS)", "Dutch (NL)", "English (EN)", "Finnish (FI)",
            "French (FR)", "German (DE)", "Hungarian (HU)", "Indonesian (IN)", "Italian (IT)", "Japanese (JA)",
            "Korean (KO)", "Malay (MS)", "Norwegian (NO)", "Persian (FA)", "Polish (PL)",
            "Portuguese (PT)", "Russian (RU)", "Serbian (SR)", "Slovak (SK)",
            "Spanish (ES)", "Swedish (SV)", "Turkish (TR)", "Ukrainian (UK)", "Uzbek (UZ)"
    };
    public static int voiceHintShowcases;
    public static boolean useGoogleCrashlytics;
    public static boolean useGoogleAnalytics;

    private static boolean configLoaded;

    public static SharedPreferences preferences;
    public static SharedPreferences.Editor editor;

    static {
        loadConfig();
    }

    public static void loadConfig() {
        synchronized (sync) {
            if (configLoaded) {
                return;
            }

            preferences = ApplicationLoader.applicationContext.getSharedPreferences("exteraconfig", Activity.MODE_PRIVATE);
            editor = preferences.edit();

            // General
            cameraType = preferences.getInt("cameraType", CameraXUtils.isCameraXSupported() ? 1 : 0);
            useCameraXOptimizedMode = preferences.getBoolean("useCameraXOptimizedMode", SharedConfig.getDevicePerformanceClass() != SharedConfig.PERFORMANCE_CLASS_HIGH);
            cameraResolution = preferences.getInt("cameraResolution", CameraXUtils.getCameraResolution());

            disableNumberRounding = preferences.getBoolean("disableNumberRounding", false);
            formatTimeWithSeconds = preferences.getBoolean("formatTimeWithSeconds", false);
            disableProximitySensor = preferences.getBoolean("disableProximitySensor", false);
            tabletMode = preferences.getInt("tabletMode", 0);

            downloadSpeedBoost = preferences.getInt("downloadSpeedBoost", 0);
            uploadSpeedBoost = preferences.getBoolean("uploadSpeedBoost", false);

            hidePhoneNumber = preferences.getBoolean("hidePhoneNumber", false);
            showIdAndDc = preferences.getInt("showIdAndDc", 1);

            disableAnimatedAvatars = preferences.getBoolean("disableAnimatedAvatars", false);
            premiumAutoPlayback = preferences.getBoolean("premiumAutoPlayback", false);
            hidePremiumStickersTab = preferences.getBoolean("hidePremiumStickersTab", true);
            hideFeaturedEmoji = preferences.getBoolean("hideFeaturedEmoji", false);
            hideSendAsChannel = preferences.getBoolean("hideSendAsChannel", false);

            archiveOnPull = preferences.getBoolean("archiveOnPull", false);
            disableUnarchiveSwipe = preferences.getBoolean("disableUnarchiveSwipe", true);

            // Appearance
            avatarCorners = preferences.getFloat("avatarCorners", 30.0f);
            hideActionBarStatus = preferences.getBoolean("hideActionBarStatus", false);
            hideAllChats = preferences.getBoolean("hideAllChats", false);
            centerTitle = preferences.getBoolean("centerTitle", false);
            tabIcons = preferences.getInt("tabIcons", 1);
            tabStyle = preferences.getInt("tabStyle", 1);
            actionBarTitle = preferences.getInt("actionBarTitle", 0);

            useSolarIcons = preferences.getBoolean("useSolarIcons", true);

            squareFab = preferences.getBoolean("squareFab", true);
            forceBlur = preferences.getBoolean("forceBlur", false);
            forceSnow = preferences.getBoolean("forceSnow", false);
            useSystemFonts = preferences.getBoolean("useSystemFonts", true);
            newSwitchStyle = preferences.getBoolean("newSwitchStyle", true);
            disableDividers = preferences.getBoolean("disableDividers", false);

            eventType = preferences.getInt("eventType", 0);
            alternativeOpenAnimation = preferences.getBoolean("alternativeOpenAnimation", true);

            changeStatus = preferences.getBoolean("changeStatus", true);
            newGroup = preferences.getBoolean("newGroup", true);
            newSecretChat = preferences.getBoolean("newSecretChat", false);
            newChannel = preferences.getBoolean("newChannel", false);
            contacts = preferences.getBoolean("contacts", true);
            calls = preferences.getBoolean("calls", false);
            peopleNearby = preferences.getBoolean("peopleNearby", false);
            archivedChats = preferences.getBoolean("archivedChats", true);
            savedMessages = preferences.getBoolean("savedMessages", true);
            scanQr = preferences.getBoolean("scanQr", true);
            inviteFriends = preferences.getBoolean("inviteFriends", false);
            telegramFeatures = preferences.getBoolean("telegramFeatures", true);

            // Chats
            stickerSize = preferences.getFloat("stickerSize", 14.0f);
            stickerShape = preferences.getInt("stickerShape", 1);

            hideStickerTime = preferences.getBoolean("hideStickerTime", false);
            unlimitedRecentStickers = preferences.getBoolean("unlimitedRecentStickers", false);
            hideCategories = preferences.getBoolean("hideCategories", true);

            doubleTapAction = preferences.getInt("doubleTapAction", 1);
            doubleTapActionOutOwner = preferences.getInt("doubleTapActionOutOwner", 1);

            bottomButton = preferences.getInt("bottomButton", 2);
            hideKeyboardOnScroll = preferences.getBoolean("hideKeyboardOnScroll", true);
            permissionsShortcut = preferences.getBoolean("permissionsShortcut", false);
            administratorsShortcut = preferences.getBoolean("administratorsShortcut", false);
            membersShortcut = preferences.getBoolean("membersShortcut", false);
            recentActionsShortcut = preferences.getBoolean("recentActionsShortcut", true);
            disableJumpToNextChannel = preferences.getBoolean("disableJumpToNextChannel", false);
            showActionTimestamps = preferences.getBoolean("showActionTimestamps", true);
            hideShareButton = preferences.getBoolean("hideShareButton", true);
            dateOfForwardedMsg = preferences.getBoolean("dateOfForwardedMsg", false);
            showMessageID = preferences.getBoolean("showMessageID", false);
            addCommaAfterMention = preferences.getBoolean("addCommaAfterMention", true);

            sendPhotosQuality = preferences.getInt("sendPhotosQuality", 1);
            disableEdgeAction = preferences.getBoolean("disableEdgeAction", false);
            hideCameraTile = preferences.getBoolean("hideCameraTile", false);

            staticZoom = preferences.getBoolean("staticZoom", false);
            videoMessagesCamera = preferences.getInt("videoMessagesCamera", 0);
            rememberLastUsedCamera = preferences.getBoolean("rememberLastUsedCamera", false);
            pauseOnMinimize = preferences.getBoolean("pauseOnMinimize", true);
            disablePlayback = preferences.getBoolean("disablePlayback", true);

            // Updates
            lastUpdateCheckTime = preferences.getLong("lastUpdateCheckTime", 0);
            updateScheduleTimestamp = preferences.getLong("updateScheduleTimestamp", 0);
            checkUpdatesOnLaunch = preferences.getBoolean("checkUpdatesOnLaunch", true);

            // Other
            channelToSave = preferences.getLong("channelToSave", 0);
            targetLanguage = preferences.getString("targetLanguage", (String) supportedLanguages[8]);
            voiceHintShowcases = preferences.getInt("voiceHintShowcases", 0);
            useGoogleCrashlytics = preferences.getBoolean("useGoogleCrashlytics", true);
            useGoogleAnalytics = preferences.getBoolean("useGoogleAnalytics", true);

            configLoaded = true;
        }
    }

    public static boolean isExtera(@NonNull TLRPC.Chat chat) {
        return Arrays.stream(OFFICIAL_CHANNELS).anyMatch(id -> id == chat.id);
    }

    public static boolean isExteraDev(@NonNull TLRPC.User user) {
        return Arrays.stream(DEVS).anyMatch(id -> id == user.id);
    }

    public static int getAvatarCorners(float size) {
        return getAvatarCorners(size, false);
    }

    public static int getAvatarCorners(float size, boolean toPx) {
        if (avatarCorners == 0) {
            return 0;
        }
        return (int) (avatarCorners * (size / 56.0f) * (toPx ? 1 : AndroidUtilities.density));
    }

    public static void toggleDrawerElements(int id) {
        switch (id) {
            case 1:
                editor.putBoolean("newGroup", newGroup ^= true).apply();
                break;
            case 2:
                editor.putBoolean("newSecretChat", newSecretChat ^= true).apply();
                break;
            case 3:
                editor.putBoolean("newChannel", newChannel ^= true).apply();
                break;
            case 4:
                editor.putBoolean("contacts", contacts ^= true).apply();
                break;
            case 5:
                editor.putBoolean("calls", calls ^= true).apply();
                break;
            case 6:
                editor.putBoolean("peopleNearby", peopleNearby ^= true).apply();
                break;
            case 7:
                editor.putBoolean("archivedChats", archivedChats ^= true).apply();
                break;
            case 8:
                editor.putBoolean("savedMessages", savedMessages ^= true).apply();
                break;
            case 9:
                editor.putBoolean("scanQr", scanQr ^= true).apply();
                break;
            case 10:
                editor.putBoolean("inviteFriends", inviteFriends ^= true).apply();
                break;
            case 11:
                editor.putBoolean("telegramFeatures", telegramFeatures ^= true).apply();
                break;
            case 12:
                editor.putBoolean("changeStatus", changeStatus ^= true).apply();
                break;
        }
    }

    public static void setChannelToSave(long id) {
        editor.putLong("channelToSave", channelToSave = id).apply();
    }

    public static void toggleLogging() {
        ApplicationLoader.applicationContext.getSharedPreferences("systemConfig", Activity.MODE_PRIVATE).edit().putBoolean("logsEnabled", BuildVars.LOGS_ENABLED ^= true).apply();
        if (!BuildVars.LOGS_ENABLED) FileLog.cleanupLogs();
    }

    public static boolean getLogging() {
        return ApplicationLoader.applicationContext.getSharedPreferences("systemConfig", Activity.MODE_PRIVATE).getBoolean("logsEnabled", false); //BuildVars.DEBUG_VERSION);
    }

    public static String getCurrentLangName() {
        return targetLanguage.substring(0, targetLanguage.indexOf("(") - 1);
    }

    public static String getCurrentLangCode() {
        return targetLanguage.substring(targetLanguage.indexOf("(") + 1, targetLanguage.indexOf(")"));
    }

    public static BaseIconSet getIconPack() {
        return useSolarIcons ? new SolarIconSet() : new EmptyIconSet();
    }

    public static int getPhotosQuality() {
        switch (sendPhotosQuality) {
            case 0:
                return 800;
            case 1:
                return 1280;
            case 2:
                return 2560;
            default:
                return 1;
        }
    }

    public static void clearPreferences() {
        configLoaded = false;
        ExteraConfig.editor.clear().apply();
        ExteraConfig.loadConfig();
    }
}
