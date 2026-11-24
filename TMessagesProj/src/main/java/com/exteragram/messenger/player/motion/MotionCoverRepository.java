/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player.motion;

import android.text.TextUtils;
import android.util.Base64;

import com.exteragram.messenger.player.PlayerCache;
import com.exteragram.messenger.player.TrackQuery;
import com.exteragram.messenger.utils.network.ExteraHttpClient;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class MotionCoverRepository {

    public interface Callback {
        void onResult(String path);
    }

    private record Reply(int code, String body) {
    }

    record Part(String url, long offset, long length) {
    }

    private static final String WEB_PLAYER = "https://music.apple.com/us/browse";
    private static final String ORIGIN = "https://music.apple.com";
    private static final String CATALOG = "https://amp-api.music.apple.com/v1/catalog/";
    private static final String FALLBACK_STOREFRONT = "us";
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36";
    private static final String CACHE_DIR = "motion_covers";
    private static final String TOKEN_FILE = "token";
    static final String VIDEO_EXT = ".mp4";
    static final String MISSING_EXT = ".none";
    static final String THUMB_EXT = ".jpg";
    private static final String STREAM_INF = "#EXT-X-STREAM-INF:";
    private static final String MAP = "#EXT-X-MAP:";
    private static final String BYTERANGE = "#EXT-X-BYTERANGE:";
    private static final int CACHE_VERSION = 4;
    private static final int SEARCH_LIMIT = 10;
    private static final float VARIANT_FIT = 0.9f;
    private static final long MAX_VIDEO_BYTES = 48L * 1024 * 1024;
    private static final long MAX_CACHE_BYTES = 200L * 1024 * 1024;
    static final long MISSING_TTL_MS = 3L * 24 * 60 * 60 * 1000;
    private static final long TOKEN_MARGIN_MS = 60L * 60 * 1000;
    private static final Pattern SCRIPT = Pattern.compile("/assets/index(?:-legacy)?[~-][A-Za-z0-9_-]+\\.js");
    private static final Pattern JWT = Pattern.compile("ey[A-Za-z0-9_-]+\\.ey[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
    private static final Pattern RESOLUTION = Pattern.compile("RESOLUTION=(\\d+)x(\\d+)");
    private static final Pattern BANDWIDTH = Pattern.compile("[:,]BANDWIDTH=(\\d+)");
    private static final Pattern MAP_URI = Pattern.compile("URI=\"([^\"]+)\"");
    private static final Pattern MAP_RANGE = Pattern.compile("BYTERANGE=\"(\\d+)(?:@(\\d+))?\"");

    static final DispatchQueue queue = new DispatchQueue("playerMotionCovers");

    private static volatile String pendingKey;
    private static String token;
    private static long tokenExpiresAt;
    private static String storefront;

    private MotionCoverRepository() {
    }

    public static void fetch(TrackQuery query, int targetSide, Callback callback) {
        final String key = query.key();
        pendingKey = key;
        queue.postRunnable(() -> {
            String path = null;
            if (key.equals(pendingKey)) {
                try {
                    path = load(query, key, targetSide);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
            final String result = path;
            AndroidUtilities.runOnUIThread(() -> callback.onResult(result));
        });
    }

    private static String load(TrackQuery query, String key, int targetSide) throws Exception {
        final File dir = PlayerCache.directory(CACHE_DIR);
        if (dir == null) {
            return null;
        }
        final String name = Utilities.MD5(key + "\u0001" + CACHE_VERSION);
        final File video = new File(dir, name + VIDEO_EXT);
        if (video.length() > 0) {
            if (!video.setLastModified(System.currentTimeMillis())) {
                FileLog.d("motion covers: can't touch " + video);
            }
            return video.getAbsolutePath();
        }
        final File marker = new File(dir, name + MISSING_EXT);
        if (System.currentTimeMillis() - marker.lastModified() < MISSING_TTL_MS) {
            return null;
        }
        final String playlist = findVideo(query);
        if (playlist == null) {
            new FileOutputStream(marker).close();
        } else {
            download(playlist, video, targetSide);
        }
        trim(dir);
        return playlist != null ? video.getAbsolutePath() : null;
    }

    static JSONArray searchSongs(TrackQuery query) throws Exception {
        final String term = TextUtils.isEmpty(query.artist()) ? query.title() : query.artist() + " " + query.title();
        final JSONObject search = catalog("search", builder -> builder
                .addQueryParameter("types", "songs")
                .addQueryParameter("limit", String.valueOf(SEARCH_LIMIT))
                .addQueryParameter("term", term));
        final JSONObject results = search.optJSONObject("results");
        final JSONObject songs = results != null ? results.optJSONObject("songs") : null;
        return songs != null ? songs.optJSONArray("data") : null;
    }

    private static String findVideo(TrackQuery query) throws Exception {
        for (JSONObject song : MotionCoverMatcher.bestSongs(searchSongs(query), query.artist(), query.title(), query.duration())) {
            final String isrc = song.optString("isrc");
            if (isrc.isEmpty()) {
                continue;
            }
            final JSONObject releases = catalog("songs", builder -> builder
                    .addQueryParameter("include", "albums")
                    .addQueryParameter("extend[albums]", "editorialVideo")
                    .addQueryParameter("filter[isrc]", isrc));
            final String video = MotionCoverMatcher.pickVideo(releases.optJSONArray("data"), query.album(), song.optString("albumName"), TextUtils.isEmpty(query.artist()) ? song.optString("artistName") : query.artist());
            if (video != null) {
                return video;
            }
        }
        return null;
    }

    private static JSONObject catalog(String path, Utilities.Callback<HttpUrl.Builder> query) throws Exception {
        if (storefront == null) {
            final String country = Locale.getDefault().getCountry().toLowerCase(Locale.ROOT);
            storefront = country.length() == 2 ? country : FALLBACK_STOREFRONT;
        }
        Reply reply = catalogGet(path, query, token(false));
        if (reply.code() == 401) {
            reply = catalogGet(path, query, token(true));
        }
        if (reply.code() == 400 && !FALLBACK_STOREFRONT.equals(storefront)) {
            storefront = FALLBACK_STOREFRONT;
            reply = catalogGet(path, query, token(false));
        }
        if (reply.code() < 200 || reply.code() >= 300) {
            throw new IllegalStateException("apple music http " + reply.code());
        }
        return new JSONObject(reply.body());
    }

    private static Reply catalogGet(String path, Utilities.Callback<HttpUrl.Builder> query, String bearer) throws Exception {
        final HttpUrl.Builder url = HttpUrl.get(CATALOG + storefront + "/" + path).newBuilder();
        query.run(url);
        final Request request = new Request.Builder()
                .url(url.build())
                .header("Authorization", "Bearer " + bearer)
                .header("Origin", ORIGIN)
                .header("Referer", ORIGIN + "/")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build();
        try (Response response = client().newCall(request).execute()) {
            final ResponseBody body = response.body();
            return new Reply(response.code(), body.string());
        }
    }

    private static String token(boolean refresh) throws Exception {
        final long now = System.currentTimeMillis();
        if (!refresh) {
            if (token == null) {
                readToken();
            }
            if (token != null && now < tokenExpiresAt - TOKEN_MARGIN_MS) {
                return token;
            }
        }
        final HashSet<String> scripts = new HashSet<>();
        final Matcher script = SCRIPT.matcher(getText(WEB_PLAYER));
        while (script.find()) {
            if (!scripts.add(script.group())) {
                continue;
            }
            final Matcher jwt = JWT.matcher(getText(ORIGIN + script.group()));
            while (jwt.find()) {
                final long expiresAt = webPlayerTokenExpiry(jwt.group());
                if (expiresAt > now) {
                    token = jwt.group();
                    tokenExpiresAt = expiresAt;
                    writeToken();
                    return token;
                }
            }
        }
        throw new IllegalStateException("apple music token not found");
    }

    private static long webPlayerTokenExpiry(String jwt) {
        try {
            final String[] parts = jwt.split("\\.");
            final JSONObject header = new JSONObject(new String(Base64.decode(parts[0], Base64.URL_SAFE), StandardCharsets.UTF_8));
            final JSONObject payload = new JSONObject(new String(Base64.decode(parts[1], Base64.URL_SAFE), StandardCharsets.UTF_8));
            if (!"WebPlayKid".equals(header.optString("kid")) && !"AMPWebPlay".equals(payload.optString("iss"))) {
                return 0;
            }
            return payload.optLong("exp") * 1000;
        } catch (Exception e) {
            return 0;
        }
    }

    private static void readToken() {
        final File dir = PlayerCache.directory(CACHE_DIR);
        final String text = dir != null ? AndroidUtilities.readRes(new File(dir, TOKEN_FILE)) : null;
        if (text == null) {
            return;
        }
        final String[] lines = text.trim().split("\n");
        if (lines.length == 2) {
            try {
                tokenExpiresAt = Long.parseLong(lines[1].trim());
                token = lines[0].trim();
            } catch (NumberFormatException e) {
                FileLog.e(e);
            }
        }
    }

    private static void writeToken() {
        final File dir = PlayerCache.directory(CACHE_DIR);
        if (dir == null) {
            return;
        }
        try (FileOutputStream out = new FileOutputStream(new File(dir, TOKEN_FILE))) {
            out.write((token + "\n" + tokenExpiresAt).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void download(String masterUrl, File target, int targetSide) throws Exception {
        final String variantUrl = pickVariant(masterUrl, getText(masterUrl), targetSide);
        if (variantUrl == null) {
            throw new IllegalStateException("no h264 variant in " + masterUrl);
        }
        final ArrayList<Part> parts = parseParts(variantUrl, getText(variantUrl));
        if (parts.isEmpty()) {
            throw new IllegalStateException("empty playlist " + variantUrl);
        }
        final File temp = new File(target.getPath() + ".tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) {
                if (isSingleFile(parts)) {
                    final Part last = parts.get(parts.size() - 1);
                    copy(new Part(last.url(), 0, last.offset() + last.length()), out, 0);
                } else {
                    long written = 0;
                    for (Part part : parts) {
                        written = copy(part, out, written);
                    }
                }
            }
            if (!temp.renameTo(target)) {
                throw new IllegalStateException("can't move " + temp);
            }
        } catch (Exception e) {
            delete(temp);
            throw e;
        }
    }

    private static String pickVariant(String masterUrl, String text, int targetSide) {
        final int target = (int) (targetSide * VARIANT_FIT);
        final String[] lines = text.split("\n");
        String best = null;
        int bestSide = 0;
        long bestBandwidth = 0;
        for (int i = 0; i < lines.length - 1; i++) {
            final String line = lines[i].trim();
            final String uri = lines[i + 1].trim();
            if (!line.startsWith(STREAM_INF) || !line.contains("avc1") || line.contains("VIDEO-RANGE=PQ") || line.contains("VIDEO-RANGE=HLG") || uri.isEmpty() || uri.startsWith("#")) {
                continue;
            }
            final Matcher resolution = RESOLUTION.matcher(line);
            final Matcher bandwidthMatcher = BANDWIDTH.matcher(line);
            if (!resolution.find()) {
                continue;
            }
            final int side = Math.max(Integer.parseInt(resolution.group(1)), Integer.parseInt(resolution.group(2)));
            final long bandwidth = bandwidthMatcher.find() ? Long.parseLong(bandwidthMatcher.group(1)) : Long.MAX_VALUE;
            final boolean fits = side >= target;
            final boolean better;
            if (best == null || fits != (bestSide >= target)) {
                better = best == null || fits;
            } else if (side != bestSide) {
                better = fits ? side < bestSide : side > bestSide;
            } else {
                better = bandwidth < bestBandwidth;
            }
            if (better) {
                best = uri;
                bestSide = side;
                bestBandwidth = bandwidth;
            }
        }
        return best != null ? resolve(masterUrl, best) : null;
    }

    private static ArrayList<Part> parseParts(String playlistUrl, String text) {
        final ArrayList<Part> parts = new ArrayList<>();
        String lastUrl = null;
        long nextOffset = 0;
        long length = -1;
        long offset = -1;
        for (String raw : text.split("\n")) {
            final String line = raw.trim();
            if (line.startsWith(MAP)) {
                final Matcher uri = MAP_URI.matcher(line);
                if (!uri.find()) {
                    continue;
                }
                final String url = resolve(playlistUrl, uri.group(1));
                final Matcher range = MAP_RANGE.matcher(line);
                if (range.find()) {
                    final long mapLength = Long.parseLong(range.group(1));
                    final long mapOffset = range.group(2) != null ? Long.parseLong(range.group(2)) : 0;
                    parts.add(new Part(url, mapOffset, mapLength));
                    nextOffset = mapOffset + mapLength;
                } else {
                    parts.add(new Part(url, 0, -1));
                    nextOffset = 0;
                }
                lastUrl = url;
            } else if (line.startsWith(BYTERANGE)) {
                final String value = line.substring(BYTERANGE.length());
                final int at = value.indexOf('@');
                length = Long.parseLong(at >= 0 ? value.substring(0, at) : value);
                offset = at >= 0 ? Long.parseLong(value.substring(at + 1)) : -1;
            } else if (!line.isEmpty() && !line.startsWith("#")) {
                final String url = resolve(playlistUrl, line);
                if (length >= 0) {
                    final long start = offset >= 0 ? offset : url.equals(lastUrl) ? nextOffset : 0;
                    parts.add(new Part(url, start, length));
                    nextOffset = start + length;
                } else {
                    parts.add(new Part(url, 0, -1));
                    nextOffset = 0;
                }
                lastUrl = url;
                length = -1;
                offset = -1;
            }
        }
        return parts;
    }

    private static boolean isSingleFile(ArrayList<Part> parts) {
        final String url = parts.get(0).url();
        long expected = 0;
        for (Part part : parts) {
            if (!part.url().equals(url) || part.length() < 0 || part.offset() != expected) {
                return false;
            }
            expected += part.length();
        }
        return true;
    }

    static long copy(Part part, OutputStream out, long written) throws Exception {
        return copy(part, out, written, null);
    }

    static long copy(Part part, OutputStream out, long written, BooleanSupplier abort) throws Exception {
        final Request.Builder builder = new Request.Builder().url(part.url()).header("User-Agent", USER_AGENT);
        if (part.length() >= 0) {
            builder.header("Range", "bytes=" + part.offset() + "-" + (part.offset() + part.length() - 1));
        }
        long total = written;
        try (Response response = client().newCall(builder.build()).execute()) {
            final ResponseBody body = response.body();
            if (!response.isSuccessful() || part.offset() > 0 && response.code() != 206) {
                throw new IllegalStateException("motion cover http " + response.code());
            }
            try (InputStream in = body.byteStream()) {
                final byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (abort != null && abort.getAsBoolean()) {
                        throw new CancellationException("motion cover download aborted");
                    }
                    total += read;
                    if (total > MAX_VIDEO_BYTES) {
                        throw new IllegalStateException("motion cover is too large");
                    }
                    out.write(buffer, 0, read);
                }
            }
        }
        return total;
    }

    private static String getText(String url) throws Exception {
        final Request request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).build();
        try (Response response = client().newCall(request).execute()) {
            final ResponseBody body = response.body();
            if (!response.isSuccessful()) {
                throw new IllegalStateException("http " + response.code() + " for " + url);
            }
            return body.string();
        }
    }

    static OkHttpClient client() {
        return ExteraHttpClient.INSTANCE.getClient();
    }

    private static String resolve(String base, String uri) {
        return URI.create(base).resolve(uri).toString();
    }

    static void trim(File dir) {
        final long now = System.currentTimeMillis();
        final File[] markers = dir.listFiles((parent, name) -> name.endsWith(MISSING_EXT));
        if (markers != null) {
            for (File marker : markers) {
                if (now - marker.lastModified() >= MISSING_TTL_MS) {
                    delete(marker);
                }
            }
        }
        final File[] videos = dir.listFiles((parent, name) -> name.endsWith(VIDEO_EXT));
        if (videos == null) {
            return;
        }
        long total = 0;
        for (File video : videos) {
            total += video.length();
        }
        Arrays.sort(videos, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < videos.length - 1 && total > MAX_CACHE_BYTES; i++) {
            total -= videos[i].length();
            delete(videos[i]);
            delete(new File(dir, videos[i].getName().replace(VIDEO_EXT, THUMB_EXT)));
        }
    }

    static void delete(File file) {
        if (file.exists() && !file.delete()) {
            FileLog.d("motion covers: can't delete " + file);
        }
    }
}
