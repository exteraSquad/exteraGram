/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.hardware.biometrics.BiometricManager;
import android.hardware.fingerprint.FingerprintManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.exteragram.messenger.updater.UserAgentGenerator;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import org.json.JSONArray;
import org.json.JSONTokener;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.URLSpanNoUnderline;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

public final class ExteraUtils {

    public static final DispatchQueue translateQueue = new DispatchQueue("translateQueue", false);

    private static Typeface systemEmojiTypeface;
    public static boolean loadSystemEmojiFailed = false;

    public static Drawable drawFab() {
        return drawFab(false);
    }

    public static Drawable drawFab(boolean altColor) {
        int r = AndroidUtilities.dp(ExteraConfig.squareFab ? 16 : 100);
        int c = Theme.getColor(altColor ? Theme.key_dialogFloatingButton : Theme.key_chats_actionBackground);
        int pc = Theme.getColor(altColor ? Theme.key_dialogFloatingButtonPressed : Theme.key_chats_actionPressedBackground);
        return Theme.createSimpleSelectorRoundRectDrawable(r, c, pc);
    }

    public static String getDC(TLRPC.User user) {
        return getDC(user, null);
    }

    public static String getDC(TLRPC.Chat chat) {
        return getDC(null, chat);
    }

    public static String getDC(TLRPC.User user, TLRPC.Chat chat) {
        int DC = 0, myDC = getConnectionsManager().getCurrentDatacenterId();
        if (user != null) {
            if (UserObject.isUserSelf(user) && myDC != -1) {
                DC = myDC;
            } else {
                DC = user.photo != null ? user.photo.dc_id : -1;
            }
        } else if (chat != null) {
            DC = chat.photo != null ? chat.photo.dc_id : -1;
        }
        if (DC == -1 || DC == 0) {
            return getDCName(0);
        } else {
            return String.format(Locale.ROOT, "DC%d, %s", DC, getDCName(DC));
        }
    }

    public static String getDCName(int dc) {
        switch (dc) {
            case 1:
            case 3:
                return "Miami FL, USA";
            case 2:
            case 4:
                return "Amsterdam, NL";
            case 5:
                return "Singapore, SG";
            default:
                return null;
        }
    }

    public static String getAppName() {
        try {
            return ApplicationLoader.applicationContext.getString(R.string.exteraAppName) + (BuildVars.isBetaApp() ? " β" : "");
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "exteraGram";
    }

    public static boolean notSubbedTo(long id) {
        TLRPC.Chat chat = getMessagesController().getChat(id);
        return chat == null || chat.left || chat.kicked;
    }

    public static int[] getDrawerIconPack() {
        switch (Theme.getEventType()) {
            case 0:
                return new int[]{
                        R.drawable.msg_groups_ny,
                        R.drawable.msg_secret_ny,
                        R.drawable.msg_channel_ny,
                        R.drawable.msg_contacts_ny,
                        R.drawable.msg_calls_ny,
                        R.drawable.msg_saved_ny,
                        R.drawable.msg_invite_ny,
                        R.drawable.msg_help_ny,
                        R.drawable.msg_nearby_ny
                };
            case 1:
                return new int[]{
                        R.drawable.msg_groups_14,
                        R.drawable.msg_secret_14,
                        R.drawable.msg_channel_14,
                        R.drawable.msg_contacts_14,
                        R.drawable.msg_calls_14,
                        R.drawable.msg_saved_14,
                        R.drawable.msg_invite_14,
                        R.drawable.msg_help_14,
                        R.drawable.msg_nearby_14
                };
            case 2:
                return new int[]{
                        R.drawable.msg_groups_hw,
                        R.drawable.msg_secret_hw,
                        R.drawable.msg_channel_hw,
                        R.drawable.msg_contacts_hw,
                        R.drawable.msg_calls_hw,
                        R.drawable.msg_saved_hw,
                        R.drawable.msg_invite_hw,
                        R.drawable.msg_help_hw,
                        R.drawable.msg_nearby_hw
                };
            default:
                return new int[]{
                        R.drawable.msg_groups,
                        R.drawable.msg_secret,
                        R.drawable.msg_channel,
                        R.drawable.msg_contacts,
                        R.drawable.msg_calls,
                        R.drawable.msg_saved,
                        R.drawable.msg_invite,
                        R.drawable.msg_help,
                        R.drawable.msg_nearby
                };
        }
    }

    public static int getNotificationIconColor() {
        return BuildVars.isBetaApp() ? 0xff747f9f : 0xfff54142;
    }

    public static File getSystemEmojiFontPath() {
        try (var br = new BufferedReader(new FileReader("/system/etc/fonts.xml"))) {
            String line;
            var ignored = false;
            while ((line = br.readLine()) != null) {
                var trimmed = line.trim();
                if (trimmed.startsWith("<family") && trimmed.contains("ignore=\"true\"")) {
                    ignored = true;
                } else if (trimmed.startsWith("</family>")) {
                    ignored = false;
                } else if (trimmed.startsWith("<font") && !ignored) {
                    var start = trimmed.indexOf(">");
                    var end = trimmed.indexOf("<", 1);
                    if (start > 0 && end > 0) {
                        var font = trimmed.substring(start + 1, end);
                        if (font.toLowerCase().contains("emoji")) {
                            File file = new File("/system/fonts/" + font);
                            if (file.exists()) {
                                FileLog.d("emoji font file fonts.xml = " + font);
                                return file;
                            }
                        }
                    }
                }
            }
            br.close();

            var fileAOSP = new File("/system/fonts/NotoColorEmoji.ttf");
            if (fileAOSP.exists()) {
                return fileAOSP;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public static Typeface getSystemEmojiTypeface() {
        if (!loadSystemEmojiFailed && systemEmojiTypeface == null) {
            var font = getSystemEmojiFontPath();
            if (font != null) {
                systemEmojiTypeface = Typeface.createFromFile(font);
            }
            if (systemEmojiTypeface == null) {
                loadSystemEmojiFailed = true;
            }
        }
        return systemEmojiTypeface;
    }

    public interface OnTranslationSuccess {
        void run(CharSequence translated);
    }

    public interface OnTranslationFail {
        void run();
    }

    public static void translate(CharSequence text, String target, OnTranslationSuccess onSuccess, OnTranslationFail onFail) {
        if (!translateQueue.isAlive()) {
            translateQueue.start();
        }
        translateQueue.postRunnable(() -> {
            String uri;
            HttpURLConnection connection;
            try {
                uri = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=";
                uri += Uri.encode(target);
                uri += "&dt=t&ie=UTF-8&oe=UTF-8&otf=1&ssel=0&tsel=0&kc=7&dt=at&dt=bd&dt=ex&dt=ld&dt=md&dt=qca&dt=rw&dt=rm&dt=ss&q=";
                uri += Uri.encode(text.toString());
                connection = (HttpURLConnection) new URI(uri).toURL().openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", UserAgentGenerator.generate());
                connection.setRequestProperty("Content-Type", "application/json");

                StringBuilder textBuilder = new StringBuilder();
                try (Reader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    int c;
                    while ((c = reader.read()) != -1) textBuilder.append((char) c);
                }
                JSONTokener tokener = new JSONTokener(textBuilder.toString());
                JSONArray array = new JSONArray(tokener);
                JSONArray array1 = array.getJSONArray(0);
                StringBuilder result = new StringBuilder();
                for (int i = 0; i < array1.length(); ++i) {
                    String blockText = array1.getJSONArray(i).getString(0);
                    if (blockText != null && !blockText.equals("null"))
                        result.append(blockText);
                }
                if (text.length() > 0 && text.charAt(0) == '\n') result.insert(0, "\n");
                if (onSuccess != null)
                    AndroidUtilities.runOnUIThread(() -> onSuccess.run(result.toString()));
            } catch (Exception e) {
                e.printStackTrace();
                if (onFail != null)
                    AndroidUtilities.runOnUIThread(onFail::run);
            }
        });
    }

    public static String getActionBarTitle() {
        return getActionBarTitle(ExteraConfig.actionBarTitle);
    }

    public static String getActionBarTitle(int num) {
        TLRPC.User user = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
        String title;
        if (num == 0) {
            title = LocaleController.getString("exteraAppName", R.string.exteraAppName);
        } else if (num == 1) {
            title = LocaleController.getString("SearchAllChatsShort", R.string.SearchAllChatsShort);
        } else if (num == 2) {
            if (!TextUtils.isEmpty(UserObject.getPublicUsername(user))) {
                title = UserObject.getPublicUsername(user);
            } else {
                title = UserObject.getFirstName(user);
            }
        } else {
            title = UserObject.getFirstName(user);
        }
        return title;
    }

    public static boolean hasGps() {
        boolean hasGps;
        try {
            hasGps = ApplicationLoader.applicationContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
        } catch (Throwable e) {
            hasGps = false;
        }
        return hasGps;
    }

    public static CombinedDrawable createCircleDrawableWithIcon(Context context, int iconRes, int size) {
        Drawable drawable = iconRes != 0 ? Objects.requireNonNull(ContextCompat.getDrawable(context, iconRes)).mutate() : null;
        OvalShape ovalShape = new OvalShape();
        ovalShape.resize(size, size);
        ShapeDrawable defaultDrawable = new ShapeDrawable(ovalShape);
        Paint paint = defaultDrawable.getPaint();
        paint.setColor(0xffffffff);
        CombinedDrawable combinedDrawable = new CombinedDrawable(defaultDrawable, drawable);
        combinedDrawable.setCustomSize(size, size);
        return combinedDrawable;
    }

    public static CharSequence formatWithUsernames(String text, BaseFragment fragment) {
        int start = -1, end;
        boolean parse = false;
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(text);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '@') {
                start = i;
                parse = true;
            }
            if (parse && (i + 1 == text.length() || (!Character.isAlphabetic(text.charAt(i + 1)) && !Character.isDigit(text.charAt(i + 1))))) {
                end = i + 1;
                parse = false;
                String username = text.substring(start, end);
                try {
                    URLSpanNoUnderline urlSpan = new URLSpanNoUnderline(username) {
                        @Override
                        public void onClick(View widget) {
                            getMessagesController().openByUserName(username.substring(1), fragment, 1);
                        }
                    };
                    stringBuilder.setSpan(urlSpan, start, end, 0);
                    if (i + 1 == text.length()) {
                        return stringBuilder;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                    return text;
                }
            }
        }
        return text;
    }

    public static CharSequence formatWithURLs(CharSequence charSequence) {
        Spannable spannable = new SpannableString(charSequence);
        URLSpan[] spans = spannable.getSpans(0, charSequence.length(), URLSpan.class);
        for (URLSpan urlSpan : spans) {
            URLSpan span = urlSpan;
            int start = spannable.getSpanStart(span), end = spannable.getSpanEnd(span);
            spannable.removeSpan(span);
            span = new URLSpanNoUnderline(span.getURL()) {
                @Override
                public void onClick(View widget) {
                    super.onClick(widget);
                }
            };
            spannable.setSpan(span, start, end, 0);
        }
        return spannable;
    }

    public static void showDialog(ArrayList<? extends CharSequence> items, String title, int selected, Context context, OnItemClickListener listener) {
        showDialog(items.stream().map(String::valueOf).toArray(CharSequence[]::new), null, title, selected, context, listener, null);
    }

    public static void showDialog(ArrayList<? extends CharSequence> items, int[] icons, String title, int selected, Context context, OnItemClickListener listener) {
        showDialog(items.stream().map(String::valueOf).toArray(CharSequence[]::new), icons, title, selected, context, listener, null);
    }

    public static void showDialog(CharSequence[] items, String title, int selected, Context context, OnItemClickListener listener) {
        showDialog(items, null, title, selected, context, listener, null);
    }

    public static void showDialog(CharSequence[] items, int[] icons, String title, int selected, Context context, OnItemClickListener listener) {
        showDialog(items, icons, title, selected, context, listener, null);
    }

    public static void showDialog(CharSequence[] items, int[] icons, String title, int selected, Context context, OnItemClickListener listener, Theme.ResourcesProvider resourcesProvider) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(title);
        if (icons == null) {
            final LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            builder.setView(linearLayout);
            for (int a = 0; a < items.length; a++) {
                RadioColorCell cell = new RadioColorCell(context);
                cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
                cell.setTag(a);
                cell.setCheckColor(Theme.getColor(Theme.key_radioBackground, resourcesProvider), Theme.getColor(Theme.key_dialogRadioBackgroundChecked, resourcesProvider));
                cell.setTextAndValue(items[a], selected == a);
                cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
                linearLayout.addView(cell);
                cell.setOnClickListener(v -> {
                    Integer which = (Integer) v.getTag();
                    builder.getDismissRunnable().run();
                    listener.onClick(which);
                });
            }
        } else {
            builder.setItems(items, icons, (dialog, which) -> {
                builder.getDismissRunnable().run();
                listener.onClick(which);
            });
            builder.create();
        }
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.show();
    }

    public interface OnItemClickListener {
        void onClick(int i);
    }

    public static String getNavigationBarColorKey() {
        return SharedConfig.useLNavigation ? Theme.key_chat_messagePanelBackground : Theme.key_windowBackgroundGray;
    }

    public static boolean hasBiometrics() {
        if (Build.VERSION.SDK_INT >= 29) {
            BiometricManager biometricManager = ApplicationLoader.applicationContext.getSystemService(BiometricManager.class);
            if (biometricManager == null) {
                return false;
            }
            if (Build.VERSION.SDK_INT >= 30) {
                return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS;
            } else {
                //noinspection deprecation
                return biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS;
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            FingerprintManager fingerprintManager = ApplicationLoader.applicationContext.getSystemService(FingerprintManager.class);
            if (fingerprintManager == null) {
                return false;
            }
            return fingerprintManager.isHardwareDetected() && fingerprintManager.hasEnrolledFingerprints();
        }
        return false;
    }

    public static String getName(long did) {
        int currentAccount = UserConfig.selectedAccount;
        String name = null;
        if (DialogObject.isEncryptedDialog(did)) {
            TLRPC.EncryptedChat encryptedChat = getMessagesController().getEncryptedChat(DialogObject.getEncryptedChatId(did));
            if (encryptedChat != null) {
                TLRPC.User user = getMessagesController().getUser(encryptedChat.user_id);
                if (user != null)
                    name = ContactsController.formatName(user.first_name, user.last_name);
            }
        } else if (DialogObject.isUserDialog(did)) {
            TLRPC.User user = getMessagesController().getUser(did);
            if (user != null) name = ContactsController.formatName(user.first_name, user.last_name);
        } else {
            TLRPC.Chat chat = getMessagesController().getChat(-did);
            if (chat != null) name = chat.title;
        }
        return did == UserConfig.getInstance(currentAccount).getClientUserId() ? LocaleController.getString("SavedMessages", R.string.SavedMessages) : name;
    }

    private static boolean useFallback;

    public interface UserSuccess {
        void run(TLRPC.User user);
    }

    public interface OnSearchSuccess {
        void run(long id);
    }

    public interface OnSearchFail {
        void run(long id);
    }

    public static void openById(Long userId, Activity activity, OnSearchSuccess success, OnSearchFail fail) {
        if (userId == 0 || activity == null) {
            return;
        }
        TLRPC.User user = getMessagesController().getUser(userId);
        if (user != null) {
            useFallback = false;
            success.run(userId);
        } else {
            searchUser(userId, true, true, user1 -> {
                if (user1 != null && user1.access_hash != 0) {
                    useFallback = false;
                    success.run(userId);
                } else {
                    if (!useFallback) {
                        useFallback = true;
                        openById(0x100000000L + userId, activity, success, fail);
                    } else {
                        useFallback = false;
                        fail.run(userId);
                    }
                }
            });
        }
    }

    private static void searchUser(long userId, boolean searchUser, boolean cache, UserSuccess callback) {
        final long bot_id = 1696868284L;
        TLRPC.User bot = getMessagesController().getUser(bot_id);
        if (bot == null) {
            if (searchUser) {
                resolveUser("tgdb_bot", bot_id, user -> searchUser(userId, false, false, callback));
            } else {
                callback.run(null);
            }
            return;
        }

        String key = "user_search_" + userId;
        RequestDelegate requestDelegate = (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (cache && (!(response instanceof TLRPC.messages_BotResults) || ((TLRPC.messages_BotResults) response).results.isEmpty())) {
                searchUser(userId, searchUser, false, callback);
                return;
            }

            if (response instanceof TLRPC.messages_BotResults) {
                TLRPC.messages_BotResults res = (TLRPC.messages_BotResults) response;
                if (!cache && res.cache_time != 0) {
                    getMessageStorage().saveBotCache(key, res);
                }
                if (res.results.isEmpty()) {
                    callback.run(null);
                    return;
                }
                TLRPC.BotInlineResult result = res.results.get(0);
                if (result.send_message == null || TextUtils.isEmpty(result.send_message.message)) {
                    callback.run(null);
                    return;
                }
                String[] lines = result.send_message.message.split("\n");
                if (lines.length < 3) {
                    callback.run(null);
                    return;
                }
                var user1 = new TLRPC.TL_user();
                for (String line : lines) {
                    line = line.replaceAll("\\p{C}", "").trim();
                    if (line.startsWith("\uD83C\uDD94")) {
                        user1.id = Utilities.parseLong(line.replaceAll("\\D+", "").trim());
                    } else if (line.startsWith("\uD83D\uDCE7")) {
                        user1.username = line.substring(line.indexOf('@') + 1).trim();
                    }
                }
                if (user1.id == 0) {
                    callback.run(null);
                    return;
                }
                if (user1.username != null) {
                    resolveUser(user1.username, user1.id, user -> {
                        if (user != null) {
                            callback.run(user);
                        } else {
                            user1.username = null;
                            callback.run(user1);
                        }
                    });
                } else {
                    callback.run(user1);
                }
            } else {
                callback.run(null);
            }
        });

        if (cache) {
            getMessageStorage().getBotCache(key, requestDelegate);
        } else {
            TLRPC.TL_messages_getInlineBotResults req = new TLRPC.TL_messages_getInlineBotResults();
            req.query = String.valueOf(userId);
            req.bot = getMessagesController().getInputUser(bot);
            req.offset = "";
            req.peer = new TLRPC.TL_inputPeerEmpty();
            getConnectionsManager().sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors);
        }
    }

    private static void resolveUser(String userName, long userId, UserSuccess callback) {
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = userName;
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response != null) {
                TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                getMessagesController().putUsers(res.users, false);
                getMessagesController().putChats(res.chats, false);
                getMessageStorage().putUsersAndChats(res.users, res.chats, true, true);
                callback.run(res.peer.user_id == userId ? getMessagesController().getUser(userId) : null);
            } else {
                callback.run(null);
            }
        }));
    }

    public static MessagesController getMessagesController() {
        return MessagesController.getInstance(UserConfig.selectedAccount);
    }

    public static MessagesStorage getMessageStorage() {
        return MessagesStorage.getInstance(UserConfig.selectedAccount);
    }

    public static ConnectionsManager getConnectionsManager() {
        return ConnectionsManager.getInstance(UserConfig.selectedAccount);
    }

    public static FileLoader getFileLoader() {
        return FileLoader.getInstance(UserConfig.selectedAccount);
    }

    public static void addMessageToClipboard(MessageObject selectedObject, Runnable callback) {
        String path = getPathToMessage(selectedObject);
        if (!TextUtils.isEmpty(path)) {
            addFileToClipboard(new File(path), callback);
        }
    }

    public static void addFileToClipboard(File file, Runnable callback) {
        try {
            Context context = ApplicationLoader.applicationContext;
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            Uri uri = FileProvider.getUriForFile(context, ApplicationLoader.getApplicationId() + ".provider", file);
            ClipData clip = ClipData.newUri(context.getContentResolver(), "label", uri);
            clipboard.setPrimaryClip(clip);
            callback.run();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static String getPathToMessage(MessageObject messageObject) {
        String path = messageObject.messageOwner.attachPath;
        if (!TextUtils.isEmpty(path)) {
            File temp = new File(path);
            if (!temp.exists()) {
                path = null;
            }
        }
        if (TextUtils.isEmpty(path)) {
            path = getFileLoader().getPathToMessage(messageObject.messageOwner).toString();
            File temp = new File(path);
            if (!temp.exists()) {
                path = null;
            }
        }
        if (TextUtils.isEmpty(path)) {
            path = getFileLoader().getPathToAttach(messageObject.getDocument(), true).toString();
            File temp = new File(path);
            if (!temp.exists()) {
                return null;
            }
        }
        return path;
    }

    public static String capitalize(String s) {
        if (s == null)
            return null;
        char[] chars = s.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (i == 0) {
                chars[i] = Character.toUpperCase(chars[i]);
            } else if (Character.isLetter(chars[i])) {
                chars[i] = Character.toLowerCase(chars[i]);
            }
        }
        return new String(chars);
    }

    public static boolean isGooglePlayServicesAvailable(Context context) {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context);
        return resultCode == ConnectionResult.SUCCESS;
    }

    private static String getPerformanceClassString() {
        switch (SharedConfig.getDevicePerformanceClass()) {
            case SharedConfig.PERFORMANCE_CLASS_LOW:
                return "Low";
            case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                return "Average";
            case SharedConfig.PERFORMANCE_CLASS_HIGH:
                return "High";
            default:
                return "N/A";
        }
    }

    public static void logEvents(Context context) {
        if (ApplicationLoader.getFirebaseAnalytics() == null) {
            return;
        }
        Bundle params = new Bundle();
        params.putString("android_version", Build.VERSION.RELEASE);
        params.putString("version", BuildConfig.VERSION_NAME);
        params.putInt("version_code", BuildConfig.VERSION_CODE);
        params.putBoolean("has_play_services", isGooglePlayServicesAvailable(context));
        params.putString("device", Build.MANUFACTURER + " " + Build.MODEL);
        params.putString("os_version", Build.VERSION.RELEASE);
        params.putString("performance_class", getPerformanceClassString());
        params.putString("locale", LocaleController.getSystemLocaleStringIso639());
        ApplicationLoader.getFirebaseAnalytics().logEvent("stats", params);
    }
}