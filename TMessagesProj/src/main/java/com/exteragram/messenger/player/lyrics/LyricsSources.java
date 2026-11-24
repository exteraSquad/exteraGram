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

import com.exteragram.messenger.player.TrackQuery;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.RequestBody;

final class LyricsSources {

    private static final int MAX_DURATION_DIFF = 5;
    private static final int MIN_PREFIX_MATCH = 4;
    private static final int SEARCH_LIMIT = 10;

    private static final String DEEZER_AUTH = "https://auth.deezer.com/login/anonymous?jo=p&rto=c&i=c";
    private static final String DEEZER_SEARCH = "https://api.deezer.com/search";
    private static final String DEEZER_PIPE = "https://pipe.deezer.com/api";
    private static final String DEEZER_QUERY = "query TrackLyrics($trackId: String!) { track(trackId: $trackId) { lyrics { text synchronizedLines { milliseconds line } synchronizedWordByWordLines { start words { start end word } } } } }";
    private static final long DEEZER_TOKEN_TTL_MS = 4 * 60 * 1000L;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final String NETEASE_SEARCH = "https://music.163.com/api/cloudsearch/pc";
    private static final String NETEASE_LYRIC = "https://music.163.com/api/song/lyric";
    private static final String NETEASE_REFERER = "https://music.163.com/";

    private static final String QQ_SEARCH = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp";
    private static final String QQ_LYRIC = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg";
    private static final String QQ_REFERER = "https://y.qq.com/";

    private static final String BETTER_LYRICS = "https://lyrics-api.boidu.dev/getLyrics";
    private static final String BINIMUM_SEARCH = "https://lyrics-api.binimum.org/";
    private static final String CHINESE = "zh";
    private static final String[] TTML_HEADERS = {"Accept", "application/ttml+xml, application/xml;q=0.9, */*;q=0.5"};

    private static final Pattern BRACKETS = Pattern.compile("\\s*[(\\[（【][^)\\]）】]*[)\\]）】]");
    private static final Pattern TIME_PREFIX = Pattern.compile("^(?:\\[\\d{1,3}:[\\d:.\\-]+])+");
    private static final Pattern META_TAG = Pattern.compile("^\\[[a-zA-Z#]+:.*]$");
    private static final Pattern CJK_CREDIT = Pattern.compile("^[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\s/&·]{1,16}[:：]");
    private static final Pattern LATIN_CREDIT = Pattern.compile("^(?:lyrics|music|words|composed|arranged|produced|written|lyricist|composer|arranger|producer)(?:\\s+by)?\\s*[:：]", Pattern.CASE_INSENSITIVE);
    private static final Pattern PREAMBLE_CREDIT = Pattern.compile("^[^:：]{1,40}：");
    private static final Pattern INSTRUMENTAL = Pattern.compile("纯音乐");
    private static final Pattern WORD_TAG = Pattern.compile("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>");
    private static final Pattern YRC_LINE = Pattern.compile("^\\[(\\d+),(\\d+)]");
    private static final Pattern YRC_WORD = Pattern.compile("\\((\\d+),(\\d+),-?\\d+\\)");
    private static final Pattern ENTITY = Pattern.compile("&(#\\d+|#x[0-9a-fA-F]+|amp|quot|apos|lt|gt);");

    private static String deezerToken;
    private static long deezerTokenTime;

    private LyricsSources() {
    }

    static Lyrics requestDeezer(TrackQuery q) throws Exception {
        final String body = LyricsRepository.get(HttpUrl.get(DEEZER_SEARCH).newBuilder()
                .addQueryParameter("q", searchText(q))
                .addQueryParameter("limit", String.valueOf(SEARCH_LIMIT))
                .build());
        if (body == null) {
            return null;
        }
        final JSONArray data = new JSONObject(body).optJSONArray("data");
        String bestId = null;
        int bestDiff = Integer.MAX_VALUE;
        for (int i = 0; data != null && i < data.length(); i++) {
            final JSONObject track = data.optJSONObject(i);
            if (track == null) {
                continue;
            }
            final JSONObject artist = track.optJSONObject("artist");
            final ArrayList<String> artists = new ArrayList<>();
            if (artist != null) {
                artists.add(artist.optString("name"));
            }
            String title = track.optString("title_short");
            if (TextUtils.isEmpty(title)) {
                title = track.optString("title");
            }
            final int diff = matchDiff(q, title, artists, track.optInt("duration"));
            if (diff >= 0 && diff < bestDiff) {
                bestDiff = diff;
                bestId = track.optString("id");
            }
        }
        if (TextUtils.isEmpty(bestId)) {
            return null;
        }
        final JSONObject request = new JSONObject()
                .put("operationName", "TrackLyrics")
                .put("query", DEEZER_QUERY)
                .put("variables", new JSONObject().put("trackId", bestId));
        String response;
        try {
            response = deezerPipe(request, false);
        } catch (LyricsRepository.HttpError e) {
            if (e.code != 401 && e.code != 403) {
                throw e;
            }
            response = deezerPipe(request, true);
        }
        if (response == null) {
            return null;
        }
        final JSONObject root = new JSONObject(response).optJSONObject("data");
        final JSONObject track = root != null ? root.optJSONObject("track") : null;
        final JSONObject lyrics = track != null ? track.optJSONObject("lyrics") : null;
        if (lyrics == null) {
            return null;
        }
        final String name = LyricsProvider.DEEZER.title;
        final Lyrics words = fromDeezerWords(lyrics.optJSONArray("synchronizedWordByWordLines"), name);
        if (words != null) {
            return words;
        }
        final JSONArray synced = lyrics.optJSONArray("synchronizedLines");
        if (synced != null && synced.length() > 0) {
            final ArrayList<Lyrics.Line> lines = new ArrayList<>(synced.length());
            for (int i = 0; i < synced.length(); i++) {
                final JSONObject line = synced.optJSONObject(i);
                if (line != null) {
                    lines.add(new Lyrics.Line(Math.max(0, line.optLong("milliseconds")), line.optString("line").trim()));
                }
            }
            final Lyrics found = lines.isEmpty() ? null : Lyrics.fromTimedLines(lines, Lyrics.SOURCE_ONLINE, name);
            if (found != null) {
                return found;
            }
        }
        return Lyrics.parse(lyrics.isNull("text") ? null : lyrics.optString("text"), Lyrics.SOURCE_ONLINE, name);
    }

    private static Lyrics fromDeezerWords(JSONArray array, String name) {
        if (array == null || array.length() == 0) {
            return null;
        }
        final ArrayList<Lyrics.Line> lines = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            final JSONObject line = array.optJSONObject(i);
            final JSONArray words = line != null ? line.optJSONArray("words") : null;
            if (words == null) {
                continue;
            }
            final Lyrics.LineBuilder builder = new Lyrics.LineBuilder();
            for (int j = 0; j < words.length(); j++) {
                final JSONObject word = words.optJSONObject(j);
                if (word != null && !word.isNull("word")) {
                    builder.space(" ");
                    builder.add(word.optString("word"), word.optLong("start"), word.optLong("end", -1));
                }
            }
            if (!builder.isEmpty()) {
                lines.add(builder.build(Math.max(0, line.optLong("start"))));
            }
        }
        return lines.isEmpty() ? null : Lyrics.fromTimedLines(lines, Lyrics.SOURCE_ONLINE, name);
    }

    private static String deezerPipe(JSONObject request, boolean refresh) throws Exception {
        final String token = deezerToken(refresh);
        if (token == null) {
            return null;
        }
        return LyricsRepository.request(HttpUrl.get(DEEZER_PIPE),
                new String[]{"Authorization", "Bearer " + token},
                RequestBody.create(request.toString(), JSON), 1);
    }

    private static synchronized String deezerToken(boolean refresh) throws Exception {
        final long now = SystemClock.elapsedRealtime();
        if (!refresh && deezerToken != null && now - deezerTokenTime < DEEZER_TOKEN_TTL_MS) {
            return deezerToken;
        }
        deezerToken = null;
        final String body = LyricsRepository.get(HttpUrl.get(DEEZER_AUTH));
        final String token = body != null ? new JSONObject(body).optString("jwt", null) : null;
        if (!TextUtils.isEmpty(token)) {
            deezerToken = token;
            deezerTokenTime = now;
        }
        return deezerToken;
    }

    static Lyrics requestNetEase(TrackQuery q) throws Exception {
        final String[] headers = {"Referer", NETEASE_REFERER};
        final String body = LyricsRepository.request(HttpUrl.get(NETEASE_SEARCH).newBuilder()
                .addQueryParameter("type", "1")
                .addQueryParameter("limit", String.valueOf(SEARCH_LIMIT))
                .addQueryParameter("s", searchText(q))
                .build(), headers, null, 1);
        if (body == null) {
            return null;
        }
        final JSONObject result = new JSONObject(body).optJSONObject("result");
        final JSONArray songs = result != null ? result.optJSONArray("songs") : null;
        long bestId = 0;
        int bestDiff = Integer.MAX_VALUE;
        for (int i = 0; songs != null && i < songs.length(); i++) {
            final JSONObject song = songs.optJSONObject(i);
            if (song == null) {
                continue;
            }
            final int diff = matchDiff(q, song.optString("name"), names(song.optJSONArray("ar")), (int) Math.round(song.optLong("dt") / 1000.0));
            if (diff >= 0 && diff < bestDiff) {
                bestDiff = diff;
                bestId = song.optLong("id");
            }
        }
        if (bestId == 0) {
            return null;
        }
        final String lyric = LyricsRepository.request(HttpUrl.get(NETEASE_LYRIC).newBuilder()
                .addQueryParameter("id", String.valueOf(bestId))
                .addQueryParameter("lv", "-1")
                .addQueryParameter("kv", "-1")
                .addQueryParameter("tv", "-1")
                .addQueryParameter("yv", "-1")
                .addQueryParameter("rv", "-1")
                .build(), headers, null, 1);
        if (lyric == null) {
            return null;
        }
        final JSONObject root = new JSONObject(lyric);
        Lyrics found = null;
        try {
            final String yrc = neteaseText(root, "yrc");
            found = yrc != null ? fromCatalogText(q, yrcToLrc(yrc), LyricsProvider.NETEASE.title) : null;
        } catch (NumberFormatException e) {
            FileLog.e(e);
        }
        if (found == null || !found.timedWords) {
            found = fromCatalogText(q, neteaseText(root, "lrc"), LyricsProvider.NETEASE.title);
        }
        return found == null ? null : found.withSubtitles(
                Lyrics.parse(neteaseText(root, "tlyric"), Lyrics.SOURCE_ONLINE),
                CHINESE,
                Lyrics.parse(neteaseText(root, "romalrc"), Lyrics.SOURCE_ONLINE));
    }

    private static String neteaseText(JSONObject root, String name) {
        final JSONObject o = root.optJSONObject(name);
        return o == null || o.isNull("lyric") ? null : o.optString("lyric", null);
    }

    private static String yrcToLrc(String yrc) {
        if (TextUtils.isEmpty(yrc)) {
            return null;
        }
        final StringBuilder sb = new StringBuilder(yrc.length());
        for (String row : yrc.split("\r\n|\r|\n")) {
            final Matcher line = YRC_LINE.matcher(row);
            if (!line.find()) {
                continue;
            }
            sb.append(Lyrics.formatTime(Long.parseLong(line.group(1)), '[', ']'));
            final Matcher word = YRC_WORD.matcher(row);
            word.region(line.end(), row.length());
            long end = -1;
            int pos = -1;
            while (word.find()) {
                if (pos >= 0) {
                    sb.append(row, pos, word.start()).append(Lyrics.formatTime(end, '<', '>'));
                }
                final long start = Long.parseLong(word.group(1));
                end = start + Long.parseLong(word.group(2));
                sb.append(Lyrics.formatTime(start, '<', '>'));
                pos = word.end();
            }
            if (pos >= 0) {
                sb.append(row, pos, row.length()).append(Lyrics.formatTime(end, '<', '>'));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    static Lyrics requestQqMusic(TrackQuery q) throws Exception {
        final String[] headers = {"Referer", QQ_REFERER};
        final String body = LyricsRepository.request(HttpUrl.get(QQ_SEARCH).newBuilder()
                .addQueryParameter("format", "json")
                .addQueryParameter("p", "1")
                .addQueryParameter("n", String.valueOf(SEARCH_LIMIT))
                .addQueryParameter("w", searchText(q))
                .build(), headers, null, 1);
        if (body == null) {
            return null;
        }
        final JSONObject data = new JSONObject(body).optJSONObject("data");
        final JSONObject song = data != null ? data.optJSONObject("song") : null;
        final JSONArray list = song != null ? song.optJSONArray("list") : null;
        String bestMid = null;
        int bestDiff = Integer.MAX_VALUE;
        for (int i = 0; list != null && i < list.length(); i++) {
            final JSONObject item = list.optJSONObject(i);
            if (item == null) {
                continue;
            }
            final int diff = matchDiff(q, item.optString("songname"), names(item.optJSONArray("singer")), item.optInt("interval"));
            if (diff >= 0 && diff < bestDiff) {
                bestDiff = diff;
                bestMid = item.optString("songmid");
            }
        }
        if (TextUtils.isEmpty(bestMid)) {
            return null;
        }
        final String lyric = LyricsRepository.request(HttpUrl.get(QQ_LYRIC).newBuilder()
                .addQueryParameter("format", "json")
                .addQueryParameter("nobase64", "1")
                .addQueryParameter("songmid", bestMid)
                .build(), headers, null, 1);
        if (lyric == null) {
            return null;
        }
        final JSONObject o = new JSONObject(lyric);
        final Lyrics found = fromCatalogText(q, o.isNull("lyric") ? null : unescape(o.optString("lyric")), LyricsProvider.QQ_MUSIC.title);
        return found == null ? null : found.withSubtitles(Lyrics.parse(o.isNull("trans") ? null : unescape(o.optString("trans")), Lyrics.SOURCE_ONLINE), CHINESE, null);
    }

    static Lyrics requestBetterLyrics(TrackQuery q) throws Exception {
        if (TextUtils.isEmpty(q.artist())) {
            return null;
        }
        final HttpUrl.Builder url = HttpUrl.get(BETTER_LYRICS).newBuilder()
                .addQueryParameter("s", q.title())
                .addQueryParameter("a", q.artist());
        if (q.duration() > 0) {
            url.addQueryParameter("d", String.valueOf(q.duration()));
        }
        if (!TextUtils.isEmpty(q.album())) {
            url.addQueryParameter("al", q.album());
        }
        final String body = LyricsRepository.get(url.build());
        if (body == null) {
            return null;
        }
        final JSONObject o = new JSONObject(body);
        return o.isNull("ttml") ? null : TtmlLyricsReader.read(o.optString("ttml"), Lyrics.SOURCE_ONLINE, LyricsProvider.BETTER_LYRICS.title, LocaleController.getInstance().getCurrentLocale());
    }

    static Lyrics requestBinimum(TrackQuery q) throws Exception {
        if (TextUtils.isEmpty(q.artist())) {
            return null;
        }
        final HttpUrl.Builder url = HttpUrl.get(BINIMUM_SEARCH).newBuilder()
                .addQueryParameter("track", q.title())
                .addQueryParameter("artist", q.artist());
        if (!TextUtils.isEmpty(q.album())) {
            url.addQueryParameter("album", q.album());
        }
        if (q.duration() > 0) {
            url.addQueryParameter("duration", String.valueOf(q.duration()));
        }
        final String body = LyricsRepository.get(url.build());
        final JSONArray results = body != null ? new JSONObject(body).optJSONArray("results") : null;
        String wordUrl = null;
        String lineUrl = null;
        int wordDiff = Integer.MAX_VALUE;
        int lineDiff = Integer.MAX_VALUE;
        for (int i = 0; results != null && i < results.length(); i++) {
            final JSONObject result = results.optJSONObject(i);
            final String link = result != null && !result.isNull("lyricsUrl") ? result.optString("lyricsUrl") : null;
            final HttpUrl parsed = link != null ? HttpUrl.parse(link) : null;
            if (parsed == null || !parsed.isHttps()) {
                continue;
            }
            final ArrayList<String> artists = new ArrayList<>();
            artists.add(result.optString("artist_name"));
            final int diff = matchDiff(q, result.optString("track_name"), artists, result.optInt("duration"));
            if (diff < 0) {
                continue;
            }
            final String timing = result.optString("timing_type");
            if ("word".equalsIgnoreCase(timing) && diff < wordDiff) {
                wordDiff = diff;
                wordUrl = link;
            } else if ("line".equalsIgnoreCase(timing) && diff < lineDiff) {
                lineDiff = diff;
                lineUrl = link;
            }
        }
        final String link = wordUrl != null ? wordUrl : lineUrl;
        if (link == null) {
            return null;
        }
        final String ttml = LyricsRepository.request(HttpUrl.get(link), TTML_HEADERS, null, 1);
        return ttml == null ? null : TtmlLyricsReader.read(ttml, Lyrics.SOURCE_ONLINE, LyricsProvider.BINIMUM.title, LocaleController.getInstance().getCurrentLocale());
    }

    private static Lyrics fromCatalogText(TrackQuery q, String text, String name) {
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        if (INSTRUMENTAL.matcher(text).find()) {
            return Lyrics.instrumental(Lyrics.SOURCE_ONLINE, name);
        }
        final String title = TrackQuery.normalize(stripBrackets(q.title()));
        final StringBuilder sb = new StringBuilder(text.length());
        boolean content = false;
        for (String row : text.split("\r\n|\r|\n")) {
            final String line = row.trim();
            final Matcher time = TIME_PREFIX.matcher(line);
            final String body = WORD_TAG.matcher(time.find() ? line.substring(time.end()) : line).replaceAll("").trim();
            if (!body.isEmpty() && !META_TAG.matcher(body).matches()) {
                if (CJK_CREDIT.matcher(body).find() || LATIN_CREDIT.matcher(body).find() || !content && PREAMBLE_CREDIT.matcher(body).find()) {
                    continue;
                }
                if (!content && !title.isEmpty() && body.contains(" - ") && TrackQuery.normalize(body).contains(title)) {
                    continue;
                }
                content = true;
            }
            sb.append(line).append('\n');
        }
        return Lyrics.parse(LyricsRepository.stripPromo(sb.toString()), Lyrics.SOURCE_ONLINE, name);
    }

    static boolean plausible(TrackQuery q, String title, String artist, int duration) {
        if (q.duration() > 0 && duration > 0 && Math.abs(duration - q.duration()) > MAX_DURATION_DIFF) {
            return false;
        }
        if (TextUtils.isEmpty(title)) {
            return true;
        }
        final ArrayList<String> artists = new ArrayList<>(1);
        if (!TextUtils.isEmpty(artist)) {
            artists.add(artist);
        }
        return titleMatches(q.title(), title) || !artists.isEmpty() && !TextUtils.isEmpty(q.artist()) && artistMatches(q.artist(), artists);
    }

    private static int matchDiff(TrackQuery q, String title, ArrayList<String> artists, int duration) {
        if (q.duration() > 0 && duration > 0) {
            final int diff = Math.abs(duration - q.duration());
            if (diff > MAX_DURATION_DIFF) {
                return -1;
            }
            if (!titleMatches(q.title(), title) || !artistMatches(q.artist(), artists)) {
                return -1;
            }
            return diff;
        }
        return titleMatches(q.title(), title) && artistMatches(q.artist(), artists) ? MAX_DURATION_DIFF : -1;
    }

    private static boolean titleMatches(String query, String candidate) {
        String a = TrackQuery.normalize(stripBrackets(query));
        if (a.isEmpty()) {
            a = TrackQuery.normalize(query);
        }
        String b = TrackQuery.normalize(stripBrackets(candidate));
        if (b.isEmpty()) {
            b = TrackQuery.normalize(candidate);
        }
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        return Math.min(a.length(), b.length()) >= MIN_PREFIX_MATCH && (a.startsWith(b) || b.startsWith(a));
    }

    private static boolean artistMatches(String query, ArrayList<String> artists) {
        if (TextUtils.isEmpty(query)) {
            return true;
        }
        final String a = TrackQuery.normalize(query);
        if (a.isEmpty()) {
            return true;
        }
        for (String artist : artists) {
            final String b = TrackQuery.normalize(artist);
            if (!b.isEmpty() && (a.contains(b) || b.contains(a))) {
                return true;
            }
        }
        return false;
    }

    private static ArrayList<String> names(JSONArray array) {
        final ArrayList<String> out = new ArrayList<>();
        for (int i = 0; array != null && i < array.length(); i++) {
            final JSONObject o = array.optJSONObject(i);
            if (o != null && !o.isNull("name")) {
                out.add(o.optString("name"));
            }
        }
        return out;
    }

    private static String searchText(TrackQuery q) {
        final String title = stripBrackets(q.title());
        final String clean = title.isEmpty() ? q.title() : title;
        return TextUtils.isEmpty(q.artist()) ? clean : q.artist() + " " + clean;
    }

    private static String stripBrackets(String s) {
        return s == null ? "" : BRACKETS.matcher(s).replaceAll("").trim();
    }

    private static String unescape(String s) {
        if (s == null || s.indexOf('&') < 0) {
            return s;
        }
        final Matcher m = ENTITY.matcher(s);
        final StringBuilder sb = new StringBuilder(s.length());
        int last = 0;
        while (m.find()) {
            sb.append(s, last, m.start());
            final String e = m.group(1);
            switch (e) {
                case "amp":
                    sb.append('&');
                    break;
                case "quot":
                    sb.append('"');
                    break;
                case "apos":
                    sb.append('\'');
                    break;
                case "lt":
                    sb.append('<');
                    break;
                case "gt":
                    sb.append('>');
                    break;
                default:
                    try {
                        final int code = e.charAt(1) == 'x' ? Integer.parseInt(e.substring(2), 16) : Integer.parseInt(e.substring(1));
                        sb.appendCodePoint(code);
                    } catch (IllegalArgumentException ignored) {
                        sb.append(m.group());
                    }
                    break;
            }
            last = m.end();
        }
        sb.append(s, last, s.length());
        return sb.toString();
    }
}
