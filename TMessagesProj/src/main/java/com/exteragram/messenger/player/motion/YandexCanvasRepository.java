/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player.motion;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import com.exteragram.messenger.player.TrackQuery;
import com.exteragram.messenger.utils.text.SealedString;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;

import okhttp3.FormBody;
import okhttp3.Request;

public final class YandexCanvasRepository {

    public record DeviceCode(String deviceCode, String userCode, String verificationUrl, long intervalMs, long expiresInMs) {
    }

    public static final int POLL_PENDING = 0;
    public static final int POLL_SUCCESS = 1;
    public static final int POLL_FAILED = 2;

    private static final String OAUTH_DEVICE_CODE_URL = "https://oauth.yandex.ru/device/code";
    private static final String OAUTH_TOKEN_URL = "https://oauth.yandex.ru/token";
    private static final String SEARCH_URL = "https://api.music.yandex.net/search";
    private static final String CLIENT_ID = SealedString.open("okHqhdCEJ2hLqOjsSTcZ_DBxUmZx6vZq2ucfkClTaOlbMA8zzL5dOw");
    private static final String CLIENT_SECRET = SealedString.open("qZBb82F9gU_ot9AomuVrkLfLto0rXl3kztHufEYv71NAm8HsA_g5TA");
    private static final String DEVICE_NAME = "exteraGram";
    private static final String USER_AGENT = "Yandex-Music-API";
    private static final String MUSIC_CLIENT = "YandexMusicAndroid/24023621";
    private static final String PENDING_ERROR = "authorization_pending";
    private static final String SLOW_DOWN_ERROR = "slow_down";
    private static final String PREFS = "yandexmusic";
    private static final String KEY_ACCESS_TOKEN = "accessToken";
    private static final String KEY_REFRESH_TOKEN = "refreshToken";
    private static final String KEY_EXPIRES_AT = "expiresAt";
    private static final String KEY_DEVICE_ID = "deviceId";
    private static final String CACHE_DIR = "yandex_canvas";
    private static final int CACHE_VERSION = 1;
    private static final int DEVICE_ID_LENGTH = 10;
    private static final int SEARCH_LIMIT = 10;
    private static final long TOKEN_MARGIN_MS = 60L * 1000;
    private static final long DEFAULT_INTERVAL_MS = 5L * 1000;
    private static final long DEFAULT_EXPIRES_MS = 5L * 60 * 1000;
    private static final String DEVICE_ID_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final Object stateLock = new Object();
    private static final Object refreshLock = new Object();
    private static final CanvasSource source = new CanvasSource(CACHE_DIR, CACHE_VERSION, YandexCanvasRepository::resolve);

    private static volatile int session;

    private YandexCanvasRepository() {
    }

    public static boolean isLoggedIn() {
        return !TextUtils.isEmpty(prefs().getString(KEY_ACCESS_TOKEN, null));
    }

    public static void logout() {
        synchronized (stateLock) {
            session++;
            prefs().edit().remove(KEY_ACCESS_TOKEN).remove(KEY_REFRESH_TOKEN).remove(KEY_EXPIRES_AT).apply();
        }
    }

    public static void requestDeviceCode(Utilities.Callback<DeviceCode> callback) {
        CanvasSource.post(() -> {
            DeviceCode code = null;
            try {
                code = requestDeviceCode();
            } catch (Exception e) {
                FileLog.e(e);
            }
            final DeviceCode result = code;
            AndroidUtilities.runOnUIThread(() -> callback.run(result));
        });
    }

    public static void pollToken(String deviceCode, Utilities.Callback<Integer> callback) {
        CanvasSource.post(() -> {
            final int result = pollToken(deviceCode);
            AndroidUtilities.runOnUIThread(() -> callback.run(result));
        });
    }

    private static DeviceCode requestDeviceCode() throws Exception {
        final CanvasSource.Reply reply = CanvasSource.execute(new Request.Builder()
                .url(OAUTH_DEVICE_CODE_URL)
                .header("User-Agent", USER_AGENT)
                .post(new FormBody.Builder()
                        .add("client_id", CLIENT_ID)
                        .add("device_id", deviceId())
                        .add("device_name", DEVICE_NAME)
                        .build())
                .build());
        if (reply.code() != 200) {
            throw new IllegalStateException("yandex device code http " + reply.code());
        }
        final JSONObject json = new JSONObject(reply.text());
        final String deviceCode = json.optString("device_code");
        final String userCode = json.optString("user_code");
        final String verificationUrl = json.optString("verification_url");
        if (deviceCode.isEmpty() || userCode.isEmpty() || !verificationUrl.startsWith("https://")) {
            throw new IllegalStateException("yandex device code malformed");
        }
        final long interval = json.optLong("interval") * 1000;
        final long expires = json.optLong("expires_in") * 1000;
        return new DeviceCode(deviceCode, userCode, verificationUrl,
                interval > 0 ? interval : DEFAULT_INTERVAL_MS,
                expires > 0 ? expires : DEFAULT_EXPIRES_MS);
    }

    private static int pollToken(String deviceCode) {
        final int expected = session;
        try {
            final CanvasSource.Reply reply = requestTokens(new FormBody.Builder()
                    .add("grant_type", "device_code")
                    .add("code", deviceCode)
                    .add("client_id", CLIENT_ID)
                    .add("client_secret", CLIENT_SECRET)
                    .build());
            if (reply.code() == 400 && (reply.text().contains(PENDING_ERROR) || reply.text().contains(SLOW_DOWN_ERROR))) {
                return POLL_PENDING;
            }
            return saveTokens(expected, reply) ? POLL_SUCCESS : POLL_FAILED;
        } catch (Exception e) {
            FileLog.e(e);
            return POLL_FAILED;
        }
    }

    static void setWantedKeys(Collection<String> keys) {
        source.setWantedKeys(keys);
    }

    static void fetch(TrackQuery query, boolean priority, CanvasRepository.Callback callback) {
        source.fetch(query, priority, callback);
    }

    static boolean owns(String path) {
        return source.owns(path);
    }

    private static String resolve(TrackQuery query) throws Exception {
        final String token = token();
        if (token == null) {
            throw new CanvasSource.Unavailable();
        }
        final ArrayList<JSONObject> songs = MotionCoverMatcher.bestSongs(searchTracks(query, token), query.artist(), query.title(), query.duration());
        for (JSONObject song : songs) {
            final String video = song.optString("videoUri");
            if (video.startsWith("https://")) {
                return video;
            }
        }
        return null;
    }

    private static JSONArray searchTracks(TrackQuery query, String token) throws Exception {
        final String term = TextUtils.isEmpty(query.artist()) ? query.title() : query.artist() + " " + query.title();
        final String url = Uri.parse(SEARCH_URL).buildUpon()
                .appendQueryParameter("text", term)
                .appendQueryParameter("type", "track")
                .appendQueryParameter("page", "0")
                .appendQueryParameter("nocorrect", "false")
                .build()
                .toString();
        final CanvasSource.Reply reply = CanvasSource.execute(new Request.Builder()
                .url(url)
                .header("Authorization", "OAuth " + token)
                .header("X-Yandex-Music-Client", MUSIC_CLIENT)
                .header("User-Agent", USER_AGENT)
                .build());
        if (reply.code() == 401 || reply.code() == 403) {
            invalidateToken();
        }
        if (reply.code() < 200 || reply.code() >= 300) {
            throw new IllegalStateException("yandex search http " + reply.code());
        }
        final JSONObject result = new JSONObject(reply.text()).optJSONObject("result");
        final JSONObject tracks = result != null ? result.optJSONObject("tracks") : null;
        final JSONArray items = tracks != null ? tracks.optJSONArray("results") : null;
        final JSONArray songs = new JSONArray();
        if (items == null) {
            return songs;
        }
        for (int i = 0; i < items.length() && songs.length() < SEARCH_LIMIT; i++) {
            final JSONObject track = items.optJSONObject(i);
            if (track == null || !track.optBoolean("available", true)) {
                continue;
            }
            final JSONObject attributes = new JSONObject()
                    .put("name", track.optString("title"))
                    .put("artistName", artistNames(track.optJSONArray("artists")))
                    .put("durationInMillis", track.optLong("durationMs"))
                    .put("isrc", track.optString("realId", track.optString("id")))
                    .put("videoUri", track.optString("backgroundVideoUri"));
            songs.put(new JSONObject().put("attributes", attributes));
        }
        return songs;
    }

    private static String artistNames(JSONArray artists) {
        if (artists == null) {
            return "";
        }
        final StringBuilder names = new StringBuilder();
        for (int i = 0; i < artists.length(); i++) {
            final JSONObject artist = artists.optJSONObject(i);
            final String name = artist != null ? artist.optString("name") : "";
            if (!name.isEmpty()) {
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(name);
            }
        }
        return names.toString();
    }

    private static String token() throws Exception {
        synchronized (refreshLock) {
            final int expected = session;
            final SharedPreferences prefs = prefs();
            final String access = prefs.getString(KEY_ACCESS_TOKEN, null);
            if (TextUtils.isEmpty(access)) {
                return null;
            }
            final long expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0);
            final String refresh = prefs.getString(KEY_REFRESH_TOKEN, null);
            if (System.currentTimeMillis() < expiresAt - TOKEN_MARGIN_MS || TextUtils.isEmpty(refresh)) {
                return access;
            }
            final CanvasSource.Reply reply = requestTokens(new FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refresh)
                    .add("client_id", CLIENT_ID)
                    .add("client_secret", CLIENT_SECRET)
                    .build());
            if (reply.code() == 400 || reply.code() == 401) {
                FileLog.d("yandex canvas: refresh token rejected");
                synchronized (stateLock) {
                    if (session == expected) {
                        logout();
                    }
                }
                return null;
            }
            if (!saveTokens(expected, reply)) {
                throw new IllegalStateException("yandex token http " + reply.code());
            }
            return session == expected ? prefs.getString(KEY_ACCESS_TOKEN, null) : null;
        }
    }

    private static void invalidateToken() {
        prefs().edit().putLong(KEY_EXPIRES_AT, 0).apply();
    }

    private static CanvasSource.Reply requestTokens(FormBody body) throws Exception {
        return CanvasSource.execute(new Request.Builder()
                .url(OAUTH_TOKEN_URL)
                .header("User-Agent", USER_AGENT)
                .post(body)
                .build());
    }

    private static boolean saveTokens(int expected, CanvasSource.Reply reply) throws Exception {
        if (reply.code() != 200) {
            return false;
        }
        final JSONObject json = new JSONObject(reply.text());
        final String access = json.optString("access_token");
        if (access.isEmpty()) {
            return false;
        }
        synchronized (stateLock) {
            if (session != expected) {
                return false;
            }
            final SharedPreferences.Editor editor = prefs().edit()
                    .putString(KEY_ACCESS_TOKEN, access)
                    .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + json.optLong("expires_in") * 1000);
            final String refresh = json.optString("refresh_token");
            if (!refresh.isEmpty()) {
                editor.putString(KEY_REFRESH_TOKEN, refresh);
            }
            editor.apply();
        }
        return true;
    }

    private static String deviceId() {
        final SharedPreferences prefs = prefs();
        final String stored = prefs.getString(KEY_DEVICE_ID, null);
        if (!TextUtils.isEmpty(stored)) {
            return stored;
        }
        final SecureRandom random = new SecureRandom();
        final StringBuilder id = new StringBuilder(DEVICE_ID_LENGTH);
        for (int i = 0; i < DEVICE_ID_LENGTH; i++) {
            id.append(DEVICE_ID_ALPHABET.charAt(random.nextInt(DEVICE_ID_ALPHABET.length())));
        }
        final String value = id.toString();
        prefs.edit().putString(KEY_DEVICE_ID, value).apply();
        return value;
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
