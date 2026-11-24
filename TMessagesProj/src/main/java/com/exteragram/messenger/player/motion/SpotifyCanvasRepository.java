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
import android.text.TextUtils;

import com.exteragram.messenger.player.TrackQuery;
import com.exteragram.messenger.utils.text.SealedString;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

public final class SpotifyCanvasRepository {

    static final String CLIENT_ID = SealedString.open("t4NoWyFsZorxOJPREALiGSJcAiA5j9n7AAinoWcXEhl7Pke_Tmb0XQ");
    static final String REDIRECT_URI = "http://127.0.0.1:8898/login";

    private static final String ORIGIN = "https://open.spotify.com";
    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
    private static final String BUILD = "https://open.spotifycdn.com/cdn/build/web-player/";
    private static final String PATHFINDER = "https://api-partner.spotify.com/pathfinder/v2/query";
    private static final String CANVASES = "https://spclient.wg.spotify.com/canvaz-cache/v0/canvases";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36";
    private static final String PREFS = "spotify";
    private static final String KEY_REFRESH_TOKEN = "refreshToken";
    private static final String KEY_SEARCH_HASH = "searchHash";
    private static final String CACHE_DIR = "spotify_canvas";
    private static final String SEARCH_CHUNK = "xpui-routes-search";
    private static final int CACHE_VERSION = 1;
    private static final int SEARCH_LIMIT = 10;
    private static final long TOKEN_MARGIN_MS = 60L * 1000;
    private static final long SEARCH_HASH_RETRY_MS = 60L * 60 * 1000;
    private static final MediaType PROTOBUF = MediaType.get("application/x-protobuf");
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Pattern BUNDLE = Pattern.compile("/web-player/(web-player\\.[0-9a-f]+\\.js)");
    private static final Pattern CHUNK_ID = Pattern.compile("(\\d+):\"" + SEARCH_CHUNK + "\"");
    private static final Pattern SEARCH_HASH = Pattern.compile("\"searchTracks\",\"query\",\"([0-9a-f]{64})\"");

    private static final Object stateLock = new Object();
    private static final Object refreshLock = new Object();
    private static final Object hashLock = new Object();
    private static final CanvasSource source = new CanvasSource(CACHE_DIR, CACHE_VERSION, SpotifyCanvasRepository::resolve);

    private static volatile String token;
    private static volatile long tokenExpiresAt;
    private static volatile int session;
    private static String searchHash;
    private static long searchHashRetryAt;

    private SpotifyCanvasRepository() {
    }

    public static boolean isLoggedIn() {
        return !TextUtils.isEmpty(prefs().getString(KEY_REFRESH_TOKEN, null));
    }

    public static void logout() {
        synchronized (stateLock) {
            clearSession();
        }
    }

    private static void clearSession() {
        session++;
        token = null;
        prefs().edit().remove(KEY_REFRESH_TOKEN).apply();
    }

    static void exchangeCode(String code, String verifier, Utilities.Callback<Boolean> callback) {
        final int expected = session;
        CanvasSource.post(() -> {
            boolean success = false;
            try {
                success = saveTokens(expected, requestTokens(new FormBody.Builder()
                        .add("grant_type", "authorization_code")
                        .add("code", code)
                        .add("redirect_uri", REDIRECT_URI)
                        .add("client_id", CLIENT_ID)
                        .add("code_verifier", verifier)
                        .build()));
            } catch (Exception e) {
                FileLog.e(e);
            }
            final boolean result = success;
            AndroidUtilities.runOnUIThread(() -> callback.run(result));
        });
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
        final String bearer = token();
        if (bearer == null) {
            throw new CanvasSource.Unavailable();
        }
        return findCanvas(query, bearer);
    }

    private static String findCanvas(TrackQuery query, String bearer) throws Exception {
        final ArrayList<JSONObject> songs = MotionCoverMatcher.bestSongs(searchTracks(query, bearer), query.artist(), query.title(), query.duration());
        if (songs.isEmpty()) {
            return null;
        }
        final ArrayList<String> uris = new ArrayList<>();
        for (JSONObject song : songs) {
            uris.add(song.optString("isrc"));
        }
        final HashMap<String, String> canvases = canvases(uris, bearer);
        for (String uri : uris) {
            final String url = canvases.get(uri);
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    private static JSONArray searchTracks(TrackQuery query, String bearer) throws Exception {
        final String term = TextUtils.isEmpty(query.artist()) ? query.title() : query.artist() + " " + query.title();
        final String hash = searchHash(false);
        JSONObject response = hash != null ? search(term, bearer, hash) : null;
        if (response == null || response.optJSONObject("data") == null) {
            final String fresh = searchHash(true);
            if (fresh != null && !fresh.equals(hash)) {
                response = search(term, bearer, fresh);
            }
        }
        if (response == null) {
            throw new IllegalStateException("spotify search unavailable");
        }
        final JSONObject data = response.optJSONObject("data");
        final JSONObject searchV2 = data != null ? data.optJSONObject("searchV2") : null;
        final JSONObject tracks = searchV2 != null ? searchV2.optJSONObject("tracksV2") : null;
        final JSONArray items = tracks != null ? tracks.optJSONArray("items") : null;
        final JSONArray songs = new JSONArray();
        if (items == null) {
            return songs;
        }
        for (int i = 0; i < items.length(); i++) {
            final JSONObject item = items.optJSONObject(i);
            final JSONObject wrapper = item != null ? item.optJSONObject("item") : null;
            final JSONObject track = wrapper != null ? wrapper.optJSONObject("data") : null;
            final String uri = track != null ? track.optString("uri") : "";
            if (!uri.startsWith("spotify:track:")) {
                continue;
            }
            final JSONObject duration = track.optJSONObject("duration");
            final JSONObject attributes = new JSONObject()
                    .put("name", track.optString("name"))
                    .put("artistName", artistNames(track.optJSONObject("artists")))
                    .put("durationInMillis", duration != null ? duration.optLong("totalMilliseconds") : 0)
                    .put("isrc", uri);
            songs.put(new JSONObject().put("attributes", attributes));
        }
        return songs;
    }

    private static JSONObject search(String term, String bearer, String hash) throws Exception {
        final JSONObject variables = new JSONObject()
                .put("searchTerm", term)
                .put("offset", 0)
                .put("limit", SEARCH_LIMIT)
                .put("numberOfTopResults", 5)
                .put("includeAudiobooks", false)
                .put("includePreReleases", false)
                .put("includeAuthors", false);
        final JSONObject body = new JSONObject()
                .put("variables", variables)
                .put("operationName", "searchTracks")
                .put("extensions", new JSONObject().put("persistedQuery", new JSONObject().put("version", 1).put("sha256Hash", hash)));
        final CanvasSource.Reply reply = CanvasSource.execute(new Request.Builder()
                .url(PATHFINDER)
                .header("Authorization", "Bearer " + bearer)
                .header("App-Platform", "WebPlayer")
                .header("User-Agent", USER_AGENT)
                .post(RequestBody.create(body.toString(), JSON))
                .build());
        final int code = reply.code();
        if (code >= 400 && code < 500 && code != 401 && code != 429) {
            return null;
        }
        if (code < 200 || code >= 300) {
            if (code == 401) {
                invalidateToken(bearer);
            }
            throw new IllegalStateException("spotify search http " + code);
        }
        return new JSONObject(reply.text());
    }

    private static String artistNames(JSONObject artists) {
        final JSONArray items = artists != null ? artists.optJSONArray("items") : null;
        if (items == null) {
            return "";
        }
        final StringBuilder names = new StringBuilder();
        for (int i = 0; i < items.length(); i++) {
            final JSONObject artist = items.optJSONObject(i);
            final JSONObject profile = artist != null ? artist.optJSONObject("profile") : null;
            final String name = profile != null ? profile.optString("name") : "";
            if (!name.isEmpty()) {
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(name);
            }
        }
        return names.toString();
    }

    private static HashMap<String, String> canvases(ArrayList<String> uris, String bearer) throws Exception {
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (String uri : uris) {
            final ByteArrayOutputStream track = new ByteArrayOutputStream();
            writeField(track, 1, uri.getBytes(StandardCharsets.UTF_8));
            writeField(body, 1, track.toByteArray());
        }
        final CanvasSource.Reply reply = CanvasSource.execute(new Request.Builder()
                .url(CANVASES)
                .header("Authorization", "Bearer " + bearer)
                .header("Accept", "application/protobuf")
                .header("User-Agent", USER_AGENT)
                .post(RequestBody.create(body.toByteArray(), PROTOBUF))
                .build());
        if (reply.code() < 200 || reply.code() >= 300) {
            if (reply.code() == 401) {
                invalidateToken(bearer);
            }
            throw new IllegalStateException("spotify canvas http " + reply.code());
        }
        final HashMap<String, String> result = new HashMap<>();
        final ByteBuffer buffer = ByteBuffer.wrap(reply.body());
        while (buffer.hasRemaining()) {
            final long key = readVarint(buffer);
            final byte[] canvas = readValue(buffer, (int) (key & 7));
            if (key >> 3 != 1 || canvas == null) {
                continue;
            }
            String url = null;
            String uri = null;
            final ByteBuffer fields = ByteBuffer.wrap(canvas);
            while (fields.hasRemaining()) {
                final long fieldKey = readVarint(fields);
                final byte[] value = readValue(fields, (int) (fieldKey & 7));
                if (value == null) {
                    continue;
                }
                if (fieldKey >> 3 == 2) {
                    url = new String(value, StandardCharsets.UTF_8);
                } else if (fieldKey >> 3 == 5) {
                    uri = new String(value, StandardCharsets.UTF_8);
                }
            }
            if (uri != null && url != null && url.startsWith("https://") && url.endsWith(".mp4")) {
                result.put(uri, url);
            }
        }
        return result;
    }

    private static void writeField(ByteArrayOutputStream out, int number, byte[] value) {
        writeVarint(out, (long) number << 3 | 2);
        writeVarint(out, value.length);
        out.write(value, 0, value.length);
    }

    private static void writeVarint(ByteArrayOutputStream out, long value) {
        while ((value & ~0x7FL) != 0) {
            out.write((int) (value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write((int) value);
    }

    private static long readVarint(ByteBuffer buffer) {
        long result = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            final byte b = buffer.get();
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
        }
        throw new IllegalStateException("malformed varint");
    }

    private static byte[] readValue(ByteBuffer buffer, int wireType) {
        switch (wireType) {
            case 0:
                readVarint(buffer);
                return null;
            case 1:
                buffer.position(buffer.position() + 8);
                return null;
            case 2: {
                final byte[] value = new byte[(int) readVarint(buffer)];
                buffer.get(value);
                return value;
            }
            case 5:
                buffer.position(buffer.position() + 4);
                return null;
            default:
                throw new IllegalStateException("unsupported wire type " + wireType);
        }
    }

    private static String token() throws Exception {
        synchronized (refreshLock) {
            final int expected = session;
            final String cached = token;
            if (cached != null && System.currentTimeMillis() < tokenExpiresAt - TOKEN_MARGIN_MS) {
                return cached;
            }
            final String refreshToken = prefs().getString(KEY_REFRESH_TOKEN, null);
            if (TextUtils.isEmpty(refreshToken)) {
                return null;
            }
            final CanvasSource.Reply reply = requestTokens(new FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refreshToken)
                    .add("client_id", CLIENT_ID)
                    .build());
            if (reply.code() == 400 || reply.code() == 401) {
                FileLog.d("spotify canvas: refresh token rejected");
                synchronized (stateLock) {
                    if (session == expected) {
                        clearSession();
                    }
                }
                return null;
            }
            final boolean saved = saveTokens(expected, reply);
            if (session != expected) {
                return null;
            }
            if (!saved) {
                throw new IllegalStateException("spotify token http " + reply.code());
            }
            return token;
        }
    }

    private static void invalidateToken(String bearer) {
        synchronized (stateLock) {
            if (bearer.equals(token)) {
                token = null;
            }
        }
    }

    private static CanvasSource.Reply requestTokens(FormBody body) throws Exception {
        return CanvasSource.execute(new Request.Builder().url(TOKEN_URL).header("User-Agent", USER_AGENT).post(body).build());
    }

    private static boolean saveTokens(int expected, CanvasSource.Reply reply) throws Exception {
        if (reply.code() != 200) {
            return false;
        }
        final JSONObject json = new JSONObject(reply.text());
        final String accessToken = json.optString("access_token");
        if (accessToken.isEmpty()) {
            return false;
        }
        final String refreshToken = json.optString("refresh_token");
        synchronized (stateLock) {
            if (session != expected) {
                return false;
            }
            if (!refreshToken.isEmpty()) {
                prefs().edit().putString(KEY_REFRESH_TOKEN, refreshToken).apply();
            }
            tokenExpiresAt = System.currentTimeMillis() + json.optLong("expires_in") * 1000;
            token = accessToken;
        }
        return true;
    }

    private static String searchHash(boolean refresh) throws Exception {
        synchronized (hashLock) {
            if (searchHash == null) {
                searchHash = prefs().getString(KEY_SEARCH_HASH, null);
            }
            final long now = System.currentTimeMillis();
            if ((refresh || searchHash == null) && now >= searchHashRetryAt) {
                searchHashRetryAt = now + SEARCH_HASH_RETRY_MS;
                final String scraped = scrapeSearchHash();
                if (scraped != null) {
                    searchHash = scraped;
                    prefs().edit().putString(KEY_SEARCH_HASH, scraped).apply();
                }
            }
            return searchHash;
        }
    }

    private static String scrapeSearchHash() throws Exception {
        final Matcher bundleMatcher = BUNDLE.matcher(getText(ORIGIN + "/"));
        if (!bundleMatcher.find()) {
            throw new IllegalStateException("spotify web player bundle not found");
        }
        final String bundle = getText(BUILD + bundleMatcher.group(1));
        final Matcher inline = SEARCH_HASH.matcher(bundle);
        if (inline.find()) {
            return inline.group(1);
        }
        final Matcher id = CHUNK_ID.matcher(bundle);
        if (!id.find()) {
            return null;
        }
        final Matcher hash = Pattern.compile("[\\{,]" + id.group(1) + ":\"([0-9a-f]{8})\"").matcher(bundle);
        if (!hash.find()) {
            return null;
        }
        final Matcher chunk = SEARCH_HASH.matcher(getText(BUILD + SEARCH_CHUNK + "." + hash.group(1) + ".js"));
        return chunk.find() ? chunk.group(1) : null;
    }

    private static String getText(String url) throws Exception {
        final CanvasSource.Reply reply = CanvasSource.execute(new Request.Builder().url(url).header("User-Agent", USER_AGENT).build());
        if (reply.code() < 200 || reply.code() >= 300) {
            throw new IllegalStateException("http " + reply.code() + " for " + url);
        }
        return reply.text();
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
