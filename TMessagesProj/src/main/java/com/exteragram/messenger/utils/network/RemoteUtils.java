/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.network;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.exteragram.messenger.utils.AppUtils;
import com.exteragram.messenger.utils.chats.ChatUtils;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class RemoteUtils {

    public static final String REMOTE_CONFIG_TAG = "remote_config";
    public static final String TAG = "RemoteUtils";
    public static final String ROUND_VIDEO_RESOLUTION = "round_video_resolution",
            ROUND_VIDEO_BITRATE = "round_video_bitrate",
            ROUND_AUDIO_BITRATE = "round_audio_bitrate",
            USE_ADBLOCK = "use_adblock",
            LOCAL_PREMIUM_EMOJIS = "local_premium_emojis",
            DNS_URL = "dns_url",
            HIDE_ADS = "hide_ads",
            DONATES = "donates",
            DONATES_BANKS = "donates_banks",
            DONATES_AMOUNT_USD = "donates_amount_usd",
            DONATES_CUSTOM_BADGE_AMOUNT_USD = "donates_custom_badge_amount_usd",
            DONATES_TON_MARKUP_PERCENT = "donates_ton_markup_percent",
            TRUSTED_PLUGINS = "trusted_plugins",
            SEARCH_BOT = "search_bot",
            EXTERA_API_BOT = "extera_api_bot",
            SEPARATE_MUSIC_STYLES = "separate_music_styles",
            PREFERENCES_PREVIEW_STYLE = "preferences_preview_style",
            SUPPORTERS_CHANNEL_LINK = "supporters_channel_link";

    private static final long CHANNEL_ID = 2227431611L;
    private static final String CHANNEL_USERNAME = "XS6GEcz5ZXMu82UvXQc";
    private static final String PREFS_NAME = "exteraremoteconfig";
    private static final int SEARCH_MAX_ATTEMPTS = 3;
    private static final String LAST_FETCH_ATTEMPT_KEY = "__last_fetch_attempt_time";
    private static final long CONFIG_REFRESH_INTERVAL = Duration.ofMinutes(10).toMillis();
    private static final long CONFIG_RETRY_INTERVAL = Duration.ofSeconds(30).toMillis();
    private static final long MESSAGES_REQUEST_TIMEOUT = Duration.ofSeconds(60).toMillis();
    public static SharedPreferences sharedPreferences;

    private static final AtomicBoolean fetchInProgress = new AtomicBoolean();
    private static volatile long lastFailedFetchTime;

    private static final Object messagesRequestLock = new Object();
    private static ArrayList<Utilities.Callback2<TLRPC.messages_Messages, TLRPC.TL_error>> pendingMessagesCallbacks;
    private static Runnable messagesTimeoutRunnable;
    private static int messagesRequestGeneration;

    @Nullable
    private static SharedPreferences getPrefs() {
        if (sharedPreferences == null) {
            initCached();
        }
        return sharedPreferences;
    }

    public static void initCached() {
        if (sharedPreferences == null) {
            sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    public static void init() {
        initCached();
        long now = System.currentTimeMillis();
        long lastSuccess = sharedPreferences.getLong(LAST_FETCH_ATTEMPT_KEY, 0L);
        if (Math.abs(now - lastSuccess) < CONFIG_REFRESH_INTERVAL) {
            return;
        }
        if (lastFailedFetchTime != 0L && Math.abs(now - lastFailedFetchTime) < CONFIG_RETRY_INTERVAL) {
            return;
        }
        loadConfig();
    }

    public static void forceRefresh() {
        initCached();
        lastFailedFetchTime = 0L;
        loadConfig();
    }

    private static void loadConfig() {
        if (!fetchInProgress.compareAndSet(false, true)) {
            return;
        }
        getMessages((messages, error) -> {
            fetchInProgress.set(false);
            if (error != null || messages == null) {
                lastFailedFetchTime = System.currentTimeMillis();
                return;
            }

            Set<String> receivedKeys = new HashSet<>();
            boolean sawConfigMessage = false;

            for (int i = messages.messages.size() - 1; i >= 0; i--) {
                TLRPC.Message msg = messages.messages.get(i);
                if (msg instanceof TLRPC.TL_message) {
                    if (msg.message.startsWith(RemoteUtils.REMOTE_CONFIG_TAG)) {
                        sawConfigMessage = true;
                        String[] split = msg.message.split("\n");
                        if (split.length > 1) {
                            for (String line : split) {
                                String[] keyValue = line.split("=", 2);
                                if (keyValue.length == 2) {
                                    String key = keyValue[0].trim();
                                    String newValue = keyValue[1].trim();
                                    if (!newValue.equals("null")) {
                                        updateValue(key, newValue);
                                        receivedKeys.add(key);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!sawConfigMessage || receivedKeys.isEmpty()) {
                lastFailedFetchTime = System.currentTimeMillis();
                return;
            }

            lastFailedFetchTime = 0L;
            sharedPreferences.edit().putLong(LAST_FETCH_ATTEMPT_KEY, System.currentTimeMillis()).apply();
            removeOldPreferences(receivedKeys);
        });
    }

    private static void removeOldPreferences(Set<String> receivedKeys) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Map<String, ?> allEntries = sharedPreferences.getAll();
        for (String key : allEntries.keySet()) {
            if (!receivedKeys.contains(key) && !LAST_FETCH_ATTEMPT_KEY.equals(key)) {
                editor.remove(key);
            }
        }
        editor.apply();
    }

    private static void updateValue(String key, String newValue) {
        Object currentValue = sharedPreferences.getAll().get(key);
        Object parsedValue = parseConfigValue(newValue);
        if (!areValuesEqual(currentValue, parsedValue)) {
            saveToPreferences(key, newValue);
        }
    }

    private static boolean areValuesEqual(Object currentValue, Object newValue) {
        if (currentValue == null || newValue == null) {
            return currentValue == newValue;
        }
        return currentValue.equals(newValue);
    }

    private static void saveToPreferences(String key, String value) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Object parsedValue = parseConfigValue(value);
        saveConfigValueToPreferences(editor, key, parsedValue);
        editor.apply();
    }

    private static Object parseConfigValue(String value) {
        if (value.matches("-?\\d+")) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                return Float.parseFloat(value);
            }
        }
        if (value.matches("-?\\d+(\\.\\d+)")) {
            return Float.parseFloat(value);
        }
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            String listContent = value.substring(1, value.length() - 1);
            if (listContent.isEmpty()) {
                return new HashSet<>();
            }
            String[] items = listContent.split(",\\s*");
            return new HashSet<>(Arrays.asList(items));
        }
        return value;
    }

    private static void saveConfigValueToPreferences(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Set) {
            //noinspection unchecked
            editor.putStringSet(key, (Set<String>) value);
        } else if (value instanceof String) {
            editor.putString(key, (String) value);
        }
    }

    public static void getMessages(Utilities.Callback2<TLRPC.messages_Messages, TLRPC.TL_error> callback) {
        final int generation;
        synchronized (messagesRequestLock) {
            if (pendingMessagesCallbacks != null) {
                pendingMessagesCallbacks.add(callback);
                return;
            }
            generation = ++messagesRequestGeneration;
            pendingMessagesCallbacks = new ArrayList<>();
            pendingMessagesCallbacks.add(callback);
            messagesTimeoutRunnable = () -> {
                TLRPC.TL_error timeoutError = new TLRPC.TL_error();
                timeoutError.code = 408;
                timeoutError.text = "REQUEST_TIMEOUT";
                deliverMessagesResult(generation, null, timeoutError);
            };
            AndroidUtilities.runOnUIThread(messagesTimeoutRunnable, MESSAGES_REQUEST_TIMEOUT);
        }

        if (ApplicationLoader.applicationHandler == null) {
            TLRPC.TL_error error = new TLRPC.TL_error();
            error.code = 503;
            error.text = "APP_NOT_READY";
            deliverMessagesResult(generation, null, error);
            return;
        }

        AccountInstance accountInstance = AccountInstance.getInstance(UserConfig.selectedAccount);
        TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
        req.offset_id = 0;
        req.limit = 100;

        Runnable sendReq = () -> {
            accountInstance.getConnectionsManager().sendRequest(req, (resp, error) -> {
                if (error != null || resp == null) {
                    deliverMessagesResult(generation, null, error);
                    return;
                }
                deliverMessagesResult(generation, (TLRPC.messages_Messages) resp, null);
            });
        };

        AndroidUtilities.runOnUIThread(() -> {
            req.peer = accountInstance.getMessagesController().getInputPeer(-CHANNEL_ID);
            if (req.peer.access_hash != 0L) {
                sendReq.run();
                return;
            }
            ChatUtils.getInstance().resolveChannel(CHANNEL_USERNAME, arg -> {
                if (arg != null && arg.id == CHANNEL_ID) {
                    req.peer = new TLRPC.TL_inputPeerChannel();
                    req.peer.channel_id = arg.id;
                    req.peer.access_hash = arg.access_hash;
                    sendReq.run();
                } else {
                    TLRPC.TL_error error = new TLRPC.TL_error();
                    error.code = 400;
                    error.text = "CHANNEL_RESOLVE_FAILED";
                    deliverMessagesResult(generation, null, error);
                }
            });
        });
    }

    private static void deliverMessagesResult(int generation, TLRPC.messages_Messages res, TLRPC.TL_error error) {
        ArrayList<Utilities.Callback2<TLRPC.messages_Messages, TLRPC.TL_error>> callbacks;
        Runnable timeoutRunnable;
        synchronized (messagesRequestLock) {
            if (generation != messagesRequestGeneration || pendingMessagesCallbacks == null) {
                return;
            }
            callbacks = pendingMessagesCallbacks;
            timeoutRunnable = messagesTimeoutRunnable;
            pendingMessagesCallbacks = null;
            messagesTimeoutRunnable = null;
        }
        if (timeoutRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(timeoutRunnable);
        }
        for (Utilities.Callback2<TLRPC.messages_Messages, TLRPC.TL_error> cb : callbacks) {
            cb.run(res, error);
        }
    }

    public static void searchMessages(String query, TLRPC.MessagesFilter filter, Utilities.Callback2<TLRPC.messages_Messages, TLRPC.TL_error> callback, int timeout) {
        searchMessages(50, query, filter, callback, timeout);
    }

    public static void searchMessages(String query, Utilities.Callback2<TLRPC.messages_Messages, TLRPC.TL_error> callback, int timeout) {
        searchMessages(50, query, new TLRPC.TL_inputMessagesFilterEmpty(), callback, timeout);
    }

    public static void searchMessages(int limit, String query, TLRPC.MessagesFilter filter, Utilities.Callback2<TLRPC.messages_Messages, TLRPC.TL_error> callback, int timeout) {
        searchMessages(UserConfig.selectedAccount, limit, query, filter, callback, timeout);
    }

    public static void searchMessages(int account, int limit, String query, TLRPC.MessagesFilter filter, Utilities.Callback2<TLRPC.messages_Messages, TLRPC.TL_error> callback, int timeout) {
        AccountInstance accountInstance = AccountInstance.getInstance(account);

        TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.q = query;
        req.offset_id = 0;
        req.limit = limit;
        req.filter = filter;

        AtomicBoolean completed = new AtomicBoolean(false);
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger reqId = new AtomicInteger();
        AtomicReference<Runnable> checkReq = new AtomicReference<>(() -> {});

        Runnable timeoutCallback = () -> {
            if (completed.compareAndSet(false, true)) {
                TLRPC.TL_error error = new TLRPC.TL_error();
                error.code = 408;
                error.text = "REQUEST_TIMEOUT";
                callback.run(null, error);
            }
        };

        Runnable sendReq = () -> {
            if (completed.get()) {
                return;
            }
            attempts.incrementAndGet();
            reqId.set(accountInstance.getConnectionsManager().sendRequest(req, (resp, error) -> {
                if (!completed.compareAndSet(false, true)) {
                    return;
                }
                AndroidUtilities.cancelRunOnUIThread(checkReq.get());
                if (error != null || resp == null) {
                    callback.run(null, error);
                    return;
                }
                TLRPC.messages_Messages res = (TLRPC.messages_Messages) resp;
                callback.run(res, null);
            }));
        };

        checkReq.set(() -> {
            if (completed.get()) {
                return;
            }
            accountInstance.getConnectionsManager().cancelRequest(reqId.get(), false);
            if (attempts.get() < SEARCH_MAX_ATTEMPTS) {
                AndroidUtilities.runOnUIThread(sendReq);
                AndroidUtilities.runOnUIThread(checkReq.get(), timeout);
            } else {
                timeoutCallback.run();
            }
        });

        AndroidUtilities.runOnUIThread(() -> {
            req.peer = accountInstance.getMessagesController().getInputPeer(-CHANNEL_ID);
            if (req.peer.access_hash != 0L) {
                sendReq.run();
                AndroidUtilities.runOnUIThread(checkReq.get(), timeout);
                return;
            }
            ChatUtils.getInstance(account).resolveChannel(CHANNEL_USERNAME, arg -> {
                if (arg != null && arg.id == CHANNEL_ID) {
                    req.peer = new TLRPC.TL_inputPeerChannel();
                    req.peer.channel_id = arg.id;
                    req.peer.access_hash = arg.access_hash;
                    sendReq.run();
                    AndroidUtilities.runOnUIThread(checkReq.get(), timeout);
                } else {
                    if (completed.compareAndSet(false, true)) {
                        TLRPC.TL_error error = new TLRPC.TL_error();
                        error.code = 400;
                        error.text = "CHANNEL_RESOLVE_FAILED";
                        callback.run(null, error);
                    }
                }
            });
        });
    }

    public static Integer getIntConfigValue(String key, int defaultValue) {
        try {
            SharedPreferences prefs = getPrefs();
            if (prefs == null) {
                return defaultValue;
            }
            Object value = prefs.getAll().get(key);
            if (value instanceof String) {
                return Integer.parseInt((String) value);
            } else if (value instanceof Long) {
                return ((Long) value).intValue();
            } else if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (Exception e) {
            AppUtils.log("Error getting int config value for key: " + key, e);
        }
        return defaultValue;
    }

    public static Long getLongConfigValue(String key, long defaultValue) {
        try {
            SharedPreferences prefs = getPrefs();
            if (prefs == null) {
                return defaultValue;
            }
            Object value = prefs.getAll().get(key);
            if (value instanceof String) {
                return Long.parseLong((String) value);
            } else if (value instanceof Long) {
                return (Long) value;
            }
        } catch (Exception e) {
            AppUtils.log("Error getting value for key: " + key, e);
        }
        return defaultValue;
    }

    public static Float getFloatConfigValue(String key, float defaultValue) {
        try {
            SharedPreferences prefs = getPrefs();
            if (prefs == null) {
                return defaultValue;
            }
            Object value = prefs.getAll().get(key);
            if (value instanceof String) {
                return Float.parseFloat((String) value);
            } else if (value instanceof Float) {
                return (Float) value;
            } else if (value instanceof Long) {
                return ((Long) value).floatValue();
            } else if (value instanceof Integer) {
                return ((Integer) value).floatValue();
            }
        } catch (Exception e) {
            AppUtils.log("Error getting value for key: " + key, e);
        }
        return defaultValue;
    }

    public static Boolean getBooleanConfigValue(String key, boolean defaultValue) {
        try {
            SharedPreferences prefs = getPrefs();
            if (prefs == null) {
                return defaultValue;
            }
            try {
                return prefs.getBoolean(key, defaultValue);
            } catch (ClassCastException e) {
                Object value = prefs.getAll().get(key);
                return value instanceof String ? Boolean.parseBoolean((String) value) : defaultValue;
            }
        } catch (Exception e) {
            AppUtils.log("Error getting value for key: " + key, e);
        }
        return defaultValue;
    }

    public static Set<String> getStringSetConfigValue(String key, Set<String> defaultValue) {
        try {
            SharedPreferences prefs = getPrefs();
            if (prefs == null) {
                return defaultValue;
            }
            Object value = prefs.getAll().get(key);
            if (value instanceof Set) {
                //noinspection unchecked
                return (Set<String>) value;
            } else if (value instanceof String) {
                String[] items = ((String) value).split(",\\s*");
                return new HashSet<>(Arrays.asList(items));
            }
        } catch (Exception e) {
            AppUtils.log("Error getting value for key: " + key, e);
        }
        return defaultValue;
    }

    public static String getStringConfigValue(String key, String defaultValue) {
        try {
            SharedPreferences prefs = getPrefs();
            if (prefs == null) {
                return defaultValue;
            }
            Object value = prefs.getAll().get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        } catch (Exception e) {
            AppUtils.log("Error getting value for key: " + key, e);
        }
        return defaultValue;
    }
}