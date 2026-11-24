/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player.lyrics;

import android.os.SystemClock;
import android.text.TextUtils;
import android.util.LruCache;

import com.exteragram.messenger.player.PlayerCache;
import com.exteragram.messenger.player.PlayerCovers;
import com.exteragram.messenger.player.TrackQuery;
import com.exteragram.messenger.utils.network.ExteraHttpClient;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.audioinfo.AudioInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class LyricsRepository {

    public static final int STATUS_OK = 0;
    public static final int STATUS_NOT_FOUND = 1;
    public static final int STATUS_ERROR = 2;

    public static final String LRCLIB_NAME = LyricsProvider.LRCLIB.title;

    public interface Callback {
        void onResult(Lyrics lyrics, int status);
    }

    private static final String LRCLIB_GET = "https://lrclib.net/api/get";
    private static final String LRCLIB_SEARCH = "https://lrclib.net/api/search";
    private static final String LRCMUX = "https://api.lrcmux.dev/get";
    static final String LRCMUX_NAME = LyricsProvider.LRCMUX.title;
    private static final String CACHE_DIR = "lyrics";
    private static final int CACHE_VERSION = 4;
    private static final int MAX_DURATION_DIFF = 3;
    private static final int ALIGN_WINDOW = 4;
    private static final float MIN_ALIGNED = 0.9f;
    private static final int MISSING_CACHE_SIZE = 64;
    private static final int MIN_TAG_LINES = 4;
    private static final Pattern PROMO = Pattern.compile(
            "https?://|www\\.|t\\.me/|\\b[\\w-]+\\.(?:com|net|org|ru|su|ua|by|kz|io|me|to|fm|cc|co|lc|ly|gg|tv|info|biz|xyz|club|top|site|online|pro|app|link|music)\\b|(?:^|\\s)@\\w{3,}",
            Pattern.CASE_INSENSITIVE);
    private static final int LRCLIB_MAX_ATTEMPTS = 3;
    private static final long RETRY_BASE_DELAY_MS = 500;
    private static final long RETRY_MAX_DELAY_MS = 2000;
    private static final long FALLBACK_TIMEOUT_MS = 20_000;
    private static final int LATE_LYRICS_SECONDS = 5;

    private static final ThreadPoolExecutor fallbackPool = createFallbackPool();

    private static ThreadPoolExecutor createFallbackPool() {
        final int threads = LyricsProvider.values().length - 1;
        final ThreadPoolExecutor pool = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    private static final LruCache<String, Lyrics> memory = new LruCache<>(24);
    private static final LruCache<String, Boolean> missing = new LruCache<>(MISSING_CACHE_SIZE);

    private LyricsRepository() {
    }

    public static Lyrics fromTags(MessageObject messageObject) {
        final AudioInfo audioInfo = PlayerCovers.getPlayingAudioInfo(messageObject);
        if (audioInfo == null) {
            return null;
        }
        final Lyrics lyrics = Lyrics.parse(stripPromo(audioInfo.getLyrics()), Lyrics.SOURCE_FILE);
        if (lyrics == null || !lyrics.synced && textLines(lyrics) < MIN_TAG_LINES) {
            return null;
        }
        return lyrics;
    }

    static String stripPromo(String text) {
        if (TextUtils.isEmpty(text)) {
            return text;
        }
        final StringBuilder sb = new StringBuilder(text.length());
        for (String line : text.split("\r\n|\r|\n", -1)) {
            if (!PROMO.matcher(line).find()) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private static int textLines(Lyrics lyrics) {
        int count = 0;
        for (Lyrics.Line line : lyrics.lines) {
            if (!line.text.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public static String key(TrackQuery query) {
        return key(query, LyricsProvider.enabled());
    }

    private static String key(TrackQuery query, ArrayList<LyricsProvider> providers) {
        if (providers.size() == 2 && providers.contains(LyricsProvider.LRCLIB) && providers.contains(LyricsProvider.LRCMUX)) {
            return query.key();
        }
        final StringBuilder sb = new StringBuilder(query.key()).append('\u0003');
        for (LyricsProvider provider : providers) {
            sb.append(provider.id).append(',');
        }
        return sb.toString();
    }

    public static Lyrics getCached(TrackQuery query) {
        return query == null ? null : memory.get(key(query));
    }

    public static boolean isKnownMissing(TrackQuery query) {
        return query != null && missing.get(key(query)) != null;
    }

    public static void loadCached(TrackQuery query, Callback callback) {
        final String key = key(query);
        final Lyrics hit = memory.get(key);
        if (hit != null) {
            callback.onResult(hit, STATUS_OK);
            return;
        }
        Utilities.externalNetworkQueue.postRunnable(() -> {
            final Lyrics disk = readDisk(key);
            AndroidUtilities.runOnUIThread(() -> {
                if (disk != null) {
                    memory.put(key, disk);
                }
                callback.onResult(disk, disk != null ? STATUS_OK : STATUS_NOT_FOUND);
            });
        });
    }

    public static void fetch(TrackQuery query, Callback callback) {
        final ArrayList<LyricsProvider> providers = LyricsProvider.enabled();
        final String key = key(query, providers);
        final Lyrics hit = memory.get(key);
        if (hit != null) {
            callback.onResult(hit, STATUS_OK);
            return;
        }
        Utilities.externalNetworkQueue.postRunnable(() -> {
            Lyrics result = readDisk(key);
            int status = STATUS_OK;
            final Lyrics[] shown = new Lyrics[1];
            if (result == null) {
                final Outcome outcome = request(query, providers, early -> {
                    shown[0] = early;
                    AndroidUtilities.runOnUIThread(() -> {
                        memory.put(key, early);
                        missing.remove(key);
                        callback.onResult(early, STATUS_OK);
                    });
                });
                result = outcome.lyrics;
                if (result != null) {
                    if (outcome.cacheable) {
                        writeDisk(key, result);
                    }
                } else {
                    status = outcome.failed ? STATUS_ERROR : STATUS_NOT_FOUND;
                }
            }
            final Lyrics lyrics = result;
            final int finalStatus = status;
            final boolean alreadyShown = lyrics != null && lyrics == shown[0];
            AndroidUtilities.runOnUIThread(() -> {
                if (lyrics != null) {
                    memory.put(key, lyrics);
                    missing.remove(key);
                } else if (finalStatus == STATUS_NOT_FOUND) {
                    missing.put(key, Boolean.TRUE);
                }
                if (!alreadyShown && (lyrics != null || shown[0] == null)) {
                    callback.onResult(lyrics, finalStatus);
                }
            });
        });
    }

    private interface EarlyResult {
        void show(Lyrics lyrics);
    }

    private record Outcome(Lyrics lyrics, boolean failed, boolean cacheable) {
    }

    private static boolean hasWordTiming(LyricsProvider provider) {
        return provider == LyricsProvider.LRCMUX || provider == LyricsProvider.DEEZER || provider == LyricsProvider.NETEASE
                || provider == LyricsProvider.BETTER_LYRICS || provider == LyricsProvider.BINIMUM;
    }

    private static boolean fits(TrackQuery query, Lyrics lyrics) {
        if (lyrics == null || query.duration() <= 0 || !lyrics.synced || lyrics.lines.isEmpty()) {
            return true;
        }
        final long limit = (query.duration() + LATE_LYRICS_SECONDS) * 1000L;
        Lyrics.Line first = null;
        for (Lyrics.Line line : lyrics.lines) {
            if (!line.isBlank()) {
                first = line;
                break;
            }
        }
        return first != null && first.time < query.duration() * 1000L && lyrics.lines.get(lyrics.lines.size() - 1).time <= limit;
    }

    private static int rank(Lyrics lyrics, boolean hasText) {
        if (lyrics == null) {
            return 0;
        }
        if (lyrics.timedWords) {
            return 3;
        }
        if (lyrics.synced) {
            return 2;
        }
        if (lyrics.instrumental) {
            return hasText ? 0 : 2;
        }
        return 1;
    }

    private static Outcome request(TrackQuery query, ArrayList<LyricsProvider> providers, EarlyResult early) {
        Lyrics primary = null;
        boolean failed = false;
        if (providers.contains(LyricsProvider.LRCLIB)) {
            try {
                primary = requestLrclib(query);
                if (!fits(query, primary)) {
                    primary = null;
                }
            } catch (Throwable e) {
                FileLog.e(e);
                failed = true;
            }
            if (primary != null && (primary.instrumental || primary.timedWords)) {
                return new Outcome(primary, false, true);
            }
        }
        final boolean primarySynced = primary != null && primary.synced;
        final ArrayList<LyricsProvider> fallbacks = new ArrayList<>(providers);
        fallbacks.remove(LyricsProvider.LRCLIB);
        if (primarySynced) {
            fallbacks.removeIf(provider -> !hasWordTiming(provider));
            if (!fallbacks.isEmpty()) {
                early.show(primary);
            }
        }
        final int count = fallbacks.size();
        final ExecutorCompletionService<Lyrics> completion = new ExecutorCompletionService<>(fallbackPool);
        final HashMap<Future<Lyrics>, Integer> indices = new HashMap<>();
        for (int i = 0; i < count; i++) {
            final LyricsProvider provider = fallbacks.get(i);
            indices.put(completion.submit(() -> {
                final Lyrics found = requestProvider(provider, query);
                return fits(query, found) ? found : null;
            }), i);
        }
        final Lyrics[] found = new Lyrics[count];
        final boolean[] done = new boolean[count];
        final long deadline = SystemClock.elapsedRealtime() + FALLBACK_TIMEOUT_MS;
        Lyrics best = primary;
        Lyrics earlySource = primarySynced && count > 0 ? primary : null;
        Lyrics earlyShown = earlySource;
        for (int pending = count; pending > 0; pending--) {
            Future<Lyrics> future = null;
            try {
                future = completion.poll(Math.max(0, deadline - SystemClock.elapsedRealtime()), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (future == null) {
                failed = true;
                break;
            }
            final int index = indices.get(future);
            done[index] = true;
            try {
                found[index] = future.get();
            } catch (Throwable e) {
                FileLog.e(e);
                failed = true;
            }
            boolean hasText = primary != null && !primary.instrumental;
            for (Lyrics l : found) {
                hasText |= l != null && !l.instrumental;
            }
            int bestIndex = -1;
            best = primary;
            int bestRank = rank(primary, hasText);
            for (int i = 0; i < count; i++) {
                final Lyrics candidate = found[i];
                if (primarySynced && candidate != null && !candidate.timedWords) {
                    continue;
                }
                final int r = rank(candidate, hasText);
                if (r > bestRank) {
                    best = candidate;
                    bestRank = r;
                    bestIndex = i;
                }
            }
            if (bestRank == 0) {
                best = null;
            }
            boolean settled = bestRank == 3;
            for (int i = 0; settled && i < bestIndex; i++) {
                settled = done[i] || !hasWordTiming(fallbacks.get(i));
            }
            if (settled) {
                break;
            }
            if (earlySource == null && best != null && best.synced) {
                earlySource = best;
                earlyShown = polish(best, primary);
                early.show(earlyShown);
            }
        }
        for (Future<Lyrics> future : indices.keySet()) {
            future.cancel(true);
        }
        if (best != null && (best.synced || best.instrumental)) {
            return new Outcome(best == earlySource ? earlyShown : polish(best, primary), failed, true);
        }
        return new Outcome(best, failed, !failed);
    }

    private static Lyrics polish(Lyrics best, Lyrics primary) {
        if (best.synced && !best.timedWords && primary != null && best != primary && !primary.synced && !primary.instrumental) {
            final Lyrics punctuated = punctuate(best, primary);
            if (punctuated != null) {
                return punctuated;
            }
        }
        return best;
    }

    private static Lyrics requestProvider(LyricsProvider provider, TrackQuery query) throws Exception {
        return switch (provider) {
            case LRCLIB -> requestLrclib(query);
            case LRCMUX -> requestLrcmux(query);
            case DEEZER -> LyricsSources.requestDeezer(query);
            case NETEASE -> LyricsSources.requestNetEase(query);
            case QQ_MUSIC -> LyricsSources.requestQqMusic(query);
            case BETTER_LYRICS -> LyricsSources.requestBetterLyrics(query);
            case BINIMUM -> LyricsSources.requestBinimum(query);
        };
    }

    private static Lyrics requestLrclib(TrackQuery q) throws Exception {
        Exception getError = null;
        if (!TextUtils.isEmpty(q.artist())) {
            final HttpUrl.Builder url = HttpUrl.get(LRCLIB_GET).newBuilder()
                    .addQueryParameter("artist_name", q.artist())
                    .addQueryParameter("track_name", q.title());
            if (!TextUtils.isEmpty(q.album())) {
                url.addQueryParameter("album_name", q.album());
            }
            if (q.duration() > 0) {
                url.addQueryParameter("duration", String.valueOf(q.duration()));
            }
            try {
                final String body = get(url.build(), LRCLIB_MAX_ATTEMPTS);
                if (body != null) {
                    final Lyrics found = fromLrclib(new JSONObject(body));
                    if (found != null) {
                        return found;
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
                getError = e;
            }
        }
        final Lyrics found = searchLrclib(q);
        if (found == null && getError != null) {
            throw getError;
        }
        return found;
    }

    private static Lyrics searchLrclib(TrackQuery q) throws Exception {
        final HttpUrl.Builder url = HttpUrl.get(LRCLIB_SEARCH).newBuilder().addQueryParameter("track_name", q.title());
        if (!TextUtils.isEmpty(q.artist())) {
            url.addQueryParameter("artist_name", q.artist());
        }
        final String body = get(url.build(), LRCLIB_MAX_ATTEMPTS);
        if (body == null) {
            return null;
        }
        final JSONArray array = new JSONArray(body);
        Lyrics best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < array.length(); i++) {
            final JSONObject o = array.optJSONObject(i);
            if (o == null || !LyricsSources.plausible(q, o.optString("trackName"), o.optString("artistName"), (int) Math.round(o.optDouble("duration", 0)))) {
                continue;
            }
            final Lyrics found = fromLrclib(o);
            if (found == null) {
                continue;
            }
            int score = found.timedWords ? 50 : found.synced ? 40 : 0;
            if (q.duration() > 0 && o.has("duration")) {
                final int diff = (int) Math.abs(Math.round(o.optDouble("duration", 0)) - q.duration());
                score -= diff <= MAX_DURATION_DIFF ? diff : 20 + Math.min(diff, 60);
            }
            if (score > bestScore) {
                bestScore = score;
                best = found;
            }
        }
        return best;
    }

    private static String optString(JSONObject o, String name) {
        if (o == null || o.isNull(name)) {
            return null;
        }
        final String s = o.optString(name, null);
        return TextUtils.isEmpty(s) ? null : s;
    }

    private static Lyrics fromLrclib(JSONObject o) {
        if (o.optBoolean("instrumental", false)) {
            return Lyrics.instrumental(Lyrics.SOURCE_ONLINE, LRCLIB_NAME);
        }
        if (o.optBoolean("hasWordSync", false)) {
            final Lyrics words = LrclibWordFile.read(optString(o, "lyricsfile"), Lyrics.SOURCE_ONLINE, LRCLIB_NAME);
            if (words != null && words.timedWords) {
                return words;
            }
        }
        final Lyrics synced = Lyrics.parse(optString(o, "syncedLyrics"), Lyrics.SOURCE_ONLINE, LRCLIB_NAME);
        if (synced != null && synced.synced) {
            return synced;
        }
        final Lyrics plain = Lyrics.parse(optString(o, "plainLyrics"), Lyrics.SOURCE_ONLINE, LRCLIB_NAME);
        return plain != null ? plain : synced;
    }

    private static Lyrics requestLrcmux(TrackQuery q) throws Exception {
        final HttpUrl.Builder url = HttpUrl.get(LRCMUX).newBuilder()
                .addQueryParameter("title", q.title())
                .addQueryParameter("level", "word")
                .addQueryParameter("format", "json");
        if (!TextUtils.isEmpty(q.artist())) {
            url.addQueryParameter("artist", q.artist());
        }
        if (!TextUtils.isEmpty(q.album())) {
            url.addQueryParameter("album", q.album());
        }
        if (q.duration() > 0) {
            url.addQueryParameter("duration", String.valueOf(q.duration()));
        }
        final String body = get(url.build());
        if (body == null) {
            return null;
        }
        final JSONObject o = new JSONObject(body);
        final JSONObject track = o.optJSONObject("track");
        final JSONObject meta = o.optJSONObject("meta");
        if (track != null && !LyricsSources.plausible(q, optString(track, "title"), optString(track, "artist"), track.optInt("duration", 0))) {
            return null;
        }
        final JSONObject source = meta != null ? meta.optJSONObject("source") : null;
        final String sourceName = source != null ? optString(source, "name") : null;
        final String provider = sourceName != null ? LRCMUX_NAME + " · " + sourceName : LRCMUX_NAME;
        if (meta != null && meta.optBoolean("instrumental", false)) {
            return Lyrics.instrumental(Lyrics.SOURCE_ONLINE, provider);
        }
        final JSONArray lines = o.optJSONArray("lines");
        if (lines == null || lines.length() == 0) {
            return null;
        }
        if (meta == null || "none".equals(meta.optString("level"))) {
            final StringBuilder text = new StringBuilder();
            for (int i = 0; i < lines.length(); i++) {
                final JSONObject line = lines.optJSONObject(i);
                if (line != null) {
                    text.append(line.optString("text", "")).append('\n');
                }
            }
            return Lyrics.parse(text.toString(), Lyrics.SOURCE_ONLINE, provider);
        }
        final ArrayList<Lyrics.Line> timed = new ArrayList<>(lines.length());
        for (int i = 0; i < lines.length(); i++) {
            final JSONObject line = lines.optJSONObject(i);
            if (line == null) {
                continue;
            }
            final long start = Math.max(0, line.optLong("start", 0));
            final JSONArray words = line.optJSONArray("words");
            if (words != null && words.length() > 0) {
                final Lyrics.LineBuilder builder = new Lyrics.LineBuilder();
                for (int j = 0; j < words.length(); j++) {
                    final JSONObject word = words.optJSONObject(j);
                    if (word != null) {
                        builder.add(word.optString("text", ""), word.optLong("start", start), word.optLong("end", -1));
                    }
                }
                if (!builder.isEmpty()) {
                    timed.add(builder.build(start));
                    continue;
                }
            }
            timed.add(new Lyrics.Line(start, line.optString("text", "").trim()));
        }
        return timed.isEmpty() ? null : Lyrics.fromTimedLines(timed, Lyrics.SOURCE_ONLINE, provider);
    }

    private static int firstLetter(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static String withCase(String token, String original) {
        final int t = firstLetter(token);
        final int o = firstLetter(original);
        if (t < 0 || o < 0) {
            return token;
        }
        final boolean upper = Character.isUpperCase(original.charAt(o));
        final char c = token.charAt(t);
        if (Character.isUpperCase(c) == upper) {
            return token;
        }
        return token.substring(0, t) + (upper ? Character.toUpperCase(c) : Character.toLowerCase(c)) + token.substring(t + 1);
    }

    private static void appendWord(StringBuilder sb, String word) {
        if (word.isEmpty()) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append(' ');
        }
        sb.append(word);
    }

    private static Lyrics punctuate(Lyrics timed, Lyrics plain) {
        final ArrayList<String> tokens = new ArrayList<>();
        final ArrayList<String> keys = new ArrayList<>();
        for (Lyrics.Line line : plain.lines) {
            for (String word : line.text.split("\\s+")) {
                final String key = TrackQuery.normalize(word);
                if (!key.isEmpty()) {
                    tokens.add(word);
                    keys.add(key);
                }
            }
        }
        if (keys.isEmpty()) {
            return null;
        }
        int pos = 0;
        int total = 0;
        int matched = 0;
        final ArrayList<Lyrics.Line> out = new ArrayList<>(timed.lines.size());
        for (Lyrics.Line line : timed.lines) {
            final StringBuilder sb = new StringBuilder();
            for (String word : line.text.split("\\s+")) {
                final String key = TrackQuery.normalize(word);
                if (key.isEmpty()) {
                    appendWord(sb, word);
                    continue;
                }
                total++;
                int hit = -1;
                for (int j = pos, end = Math.min(pos + ALIGN_WINDOW, keys.size()); j < end; j++) {
                    if (keys.get(j).equals(key)) {
                        hit = j;
                        break;
                    }
                }
                if (hit < 0) {
                    appendWord(sb, word);
                    continue;
                }
                matched++;
                appendWord(sb, withCase(tokens.get(hit), word));
                pos = hit + 1;
            }
            out.add(new Lyrics.Line(line.time, sb.toString()));
        }
        if (total == 0 || matched < total * MIN_ALIGNED) {
            return null;
        }
        return Lyrics.synced(out, timed.source, timed.provider + " + " + LRCLIB_NAME);
    }

    static final class HttpError extends Exception {
        @Serial
        private static final long serialVersionUID = 1L;

        final int code;

        HttpError(int code) {
            super("lyrics http " + code);
            this.code = code;
        }
    }

    static String get(HttpUrl url) throws Exception {
        return get(url, 1);
    }

    private static String get(HttpUrl url, int maxAttempts) throws Exception {
        return request(url, null, null, maxAttempts);
    }

    static String request(HttpUrl url, String[] headers, RequestBody body, int maxAttempts) throws Exception {
        final Request.Builder builder = new Request.Builder()
                .url(url)
                .header("User-Agent", "exteraGram/" + BuildVars.BUILD_VERSION_STRING + " (https://github.com/exteraGram/exteraGram)")
                .header("Accept", "application/json");
        for (int i = 0; headers != null && i + 1 < headers.length; i += 2) {
            builder.header(headers[i], headers[i + 1]);
        }
        if (body != null) {
            builder.post(body);
        }
        final Request request = builder.build();
        for (int attempt = 1; ; attempt++) {
            final long delay;
            try (Response response = ExteraHttpClient.INSTANCE.getClient().newCall(request).execute()) {
                final int code = response.code();
                if (code == 404) {
                    return null;
                }
                if (code >= 200 && code < 300) {
                    return response.body().string();
                }
                if ((code != 429 && code != 503) || attempt >= maxAttempts) {
                    throw new HttpError(code);
                }
                delay = retryDelay(response.header("Retry-After"), attempt);
            }
            Thread.sleep(delay);
        }
    }

    private static long retryDelay(String retryAfter, int attempt) {
        if (retryAfter != null) {
            try {
                return Math.min(Long.parseLong(retryAfter.trim()) * 1000L, RETRY_MAX_DELAY_MS);
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.min(RETRY_BASE_DELAY_MS << (attempt - 1), RETRY_MAX_DELAY_MS);
    }

    private static File cacheFile(String key) {
        final File dir = PlayerCache.directory(CACHE_DIR);
        return dir != null ? new File(dir, Utilities.MD5(key) + ".json") : null;
    }

    private static Lyrics readDisk(String key) {
        try {
            final File f = cacheFile(key);
            if (f == null || !f.exists()) {
                return null;
            }
            final String text = AndroidUtilities.readRes(f);
            if (text == null) {
                return null;
            }
            final JSONObject o = new JSONObject(text);
            if (o.optInt("v", 0) != CACHE_VERSION) {
                return null;
            }
            String provider = optString(o, "provider");
            if (provider == null) {
                provider = LRCLIB_NAME;
            }
            return Lyrics.fromJson(o, Lyrics.SOURCE_ONLINE, provider);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    private static void writeDisk(String key, Lyrics lyrics) {
        try {
            final File f = cacheFile(key);
            if (f == null) {
                return;
            }
            final JSONObject o = lyrics.toJson();
            o.put("v", CACHE_VERSION);
            o.put("provider", lyrics.provider);
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(o.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
