/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player.lyrics;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Lyrics {

    public static final int SOURCE_FILE = 0;
    public static final int SOURCE_ONLINE = 1;

    public static final int VOICE_MAIN = 0;
    public static final int VOICE_SECOND = 1;

    public static final class Word {
        public final long start;
        public final long end;
        public final int from;
        public final int to;

        Word(long start, long end, int from, int to) {
            this.start = start;
            this.end = end;
            this.from = from;
            this.to = to;
        }
    }

    public static final class Line {
        public final long time;
        public final String text;
        public final Word[] words;
        public final Line background;
        public final int voice;
        public final String translation;
        public final String romanization;

        Line(long time, String text) {
            this(time, text, null);
        }

        Line(long time, String text, Word[] words) {
            this(time, text, words, null, VOICE_MAIN, null, null);
        }

        Line(long time, String text, Word[] words, Line background, int voice, String translation, String romanization) {
            this.time = time;
            this.text = text;
            this.words = words;
            this.background = background;
            this.voice = voice;
            this.translation = TextUtils.isEmpty(translation) ? null : translation;
            this.romanization = TextUtils.isEmpty(romanization) ? null : romanization;
        }

        Line withWords(Word[] words, Line background) {
            return new Line(time, text, words, background, voice, translation, romanization);
        }

        Line withExtras(Line background, int voice, String translation, String romanization) {
            return new Line(time, text, words, background, voice, translation, romanization);
        }

        public boolean isBlank() {
            return text.isEmpty();
        }

        public long getEnd() {
            long end = words != null ? words[words.length - 1].end : -1;
            if (background != null) {
                end = Math.max(end, background.getEnd());
            }
            return end;
        }
    }

    static final class LineBuilder {
        private final StringBuilder text = new StringBuilder();
        private final ArrayList<long[]> words = new ArrayList<>();

        void add(String part, long start, long end) {
            if (part == null) {
                return;
            }
            int lead = 0;
            while (lead < part.length() && Character.isWhitespace(part.charAt(lead))) {
                lead++;
            }
            if (lead == part.length()) {
                if (!words.isEmpty()) {
                    final long[] last = words.get(words.size() - 1);
                    if (last[1] < 0 && start >= last[0]) {
                        last[1] = start;
                    }
                }
                space(part);
                return;
            }
            int trail = part.length();
            while (Character.isWhitespace(part.charAt(trail - 1))) {
                trail--;
            }
            space(part.substring(0, lead));
            final int from = text.length();
            text.append(part, lead, trail);
            words.add(new long[]{Math.max(0, start), end < 0 ? -1 : Math.max(start, end), from, text.length()});
            space(part.substring(trail));
        }

        void appendText(String part) {
            if (part == null || part.trim().isEmpty()) {
                if (part != null) {
                    space(part);
                }
                return;
            }
            int lead = 0;
            while (Character.isWhitespace(part.charAt(lead))) {
                lead++;
            }
            int trail = part.length();
            while (Character.isWhitespace(part.charAt(trail - 1))) {
                trail--;
            }
            space(part.substring(0, lead));
            final boolean attached = lead == 0 && !words.isEmpty() && words.get(words.size() - 1)[3] == text.length();
            text.append(part, lead, trail);
            if (attached) {
                words.get(words.size() - 1)[3] = text.length();
            }
            space(part.substring(trail));
        }

        void closeLast(long end) {
            if (!words.isEmpty() && end >= 0) {
                final long[] last = words.get(words.size() - 1);
                if (last[1] < 0) {
                    last[1] = Math.max(last[0], end);
                }
            }
        }

        void space(String whitespace) {
            if (!text.isEmpty() && !whitespace.isEmpty() && !Character.isWhitespace(text.charAt(text.length() - 1))) {
                text.append(' ');
            }
        }

        boolean isEmpty() {
            return words.isEmpty();
        }

        boolean hasText() {
            return !text.toString().trim().isEmpty();
        }

        long firstStart() {
            return words.isEmpty() ? -1 : words.get(0)[0];
        }

        void reset() {
            text.setLength(0);
            words.clear();
        }

        Line build(long time) {
            int length = text.length();
            while (length > 0 && Character.isWhitespace(text.charAt(length - 1))) {
                length--;
            }
            final String content = text.substring(0, length);
            if (words.isEmpty()) {
                return new Line(time, content);
            }
            final Word[] out = new Word[words.size()];
            for (int i = 0; i < out.length; i++) {
                final long[] w = words.get(i);
                out[i] = new Word(w[0], w[1], (int) w[2], (int) w[3]);
            }
            return new Line(time, content, out);
        }
    }

    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern WORD_TIME = Pattern.compile("<(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?>");
    private static final Pattern OFFSET = Pattern.compile("^\\[offset:\\s*([+-]?\\d+)\\s*]", Pattern.CASE_INSENSITIVE);
    private static final Pattern META = Pattern.compile("^\\[[a-zA-Z#]+:.*]$");
    private static final long LAST_WORD_MIN_MS = 500;
    private static final long LAST_WORD_MS_PER_CHAR = 150;
    private static final long PAUSE_MS = 5000;
    private static final long SUBTITLE_TOLERANCE_MS = 1000;

    public final ArrayList<Line> lines;
    public final boolean synced;
    public final boolean timedWords;
    public final boolean instrumental;
    public final int source;
    public final String provider;
    public final String translationLanguage;
    public final boolean hasTranslation;
    public final boolean hasRomanization;

    private Lyrics(ArrayList<Line> lines, boolean synced, boolean instrumental, int source, String provider, String translationLanguage) {
        this.lines = lines;
        this.synced = synced;
        boolean words = false;
        boolean translation = false;
        boolean romanization = false;
        for (Line line : lines) {
            words |= line.words != null;
            translation |= line.translation != null;
            romanization |= line.romanization != null;
        }
        this.timedWords = words;
        this.hasTranslation = translation;
        this.hasRomanization = romanization;
        this.instrumental = instrumental;
        this.source = source;
        this.provider = provider;
        this.translationLanguage = translation ? translationLanguage : null;
    }

    public String getCreditName() {
        if (source == SOURCE_FILE || provider == null) {
            return null;
        }
        String name = provider;
        final int merged = name.indexOf(" + ");
        if (merged >= 0) {
            name = name.substring(0, merged);
        }
        final int separator = name.lastIndexOf(" · ");
        if (separator >= 0) {
            name = name.substring(separator + 3);
        } else if (name.equalsIgnoreCase(LyricsRepository.LRCMUX_NAME)) {
            return null;
        }
        name = name.trim();
        return name.isEmpty() ? null : name;
    }

    public boolean hasTranslationFor(Locale locale) {
        if (!hasTranslation || translationLanguage == null || locale == null) {
            return false;
        }
        final String wanted = locale.getLanguage();
        final String actual = Locale.forLanguageTag(translationLanguage.replace('_', '-')).getLanguage();
        return !TextUtils.isEmpty(wanted) && wanted.equalsIgnoreCase(actual);
    }

    public static Lyrics instrumental(int source, String provider) {
        return new Lyrics(new ArrayList<>(), false, true, source, provider, null);
    }

    static Lyrics synced(ArrayList<Line> lines, int source, String provider) {
        return new Lyrics(lines, true, false, source, provider, null);
    }

    Lyrics withSubtitles(Lyrics translation, String language, Lyrics romanization) {
        if (!synced) {
            return this;
        }
        final String[] translations = alignSubtitles(translation);
        final String[] romanizations = alignSubtitles(romanization);
        if (translations == null && romanizations == null) {
            return this;
        }
        final ArrayList<Line> out = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            final Line line = lines.get(i);
            final String tr = line.translation != null ? line.translation : translations != null ? translations[i] : null;
            final String ro = line.romanization != null ? line.romanization : romanizations != null ? romanizations[i] : null;
            out.add(tr == line.translation && ro == line.romanization ? line : line.withExtras(line.background, line.voice, tr, ro));
        }
        return new Lyrics(out, synced, instrumental, source, provider, translationLanguage != null ? translationLanguage : language);
    }

    private String[] alignSubtitles(Lyrics subtitles) {
        if (subtitles == null || !subtitles.synced) {
            return null;
        }
        final TreeMap<Long, Integer> byTime = new TreeMap<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).isBlank()) {
                byTime.put(lines.get(i).time, i);
            }
        }
        final String[] out = new String[lines.size()];
        final long[] distance = new long[lines.size()];
        boolean any = false;
        for (Line subtitle : subtitles.lines) {
            if (subtitle.isBlank()) {
                continue;
            }
            final Map.Entry<Long, Integer> floor = byTime.floorEntry(subtitle.time);
            final Map.Entry<Long, Integer> ceiling = byTime.ceilingEntry(subtitle.time);
            Map.Entry<Long, Integer> nearest = floor;
            if (nearest == null || ceiling != null && ceiling.getKey() - subtitle.time < subtitle.time - nearest.getKey()) {
                nearest = ceiling;
            }
            if (nearest == null) {
                continue;
            }
            final long d = Math.abs(nearest.getKey() - subtitle.time);
            final int index = nearest.getValue();
            if (d <= SUBTITLE_TOLERANCE_MS && (out[index] == null || d < distance[index])) {
                out[index] = subtitle.text;
                distance[index] = d;
                any = true;
            }
        }
        return any ? out : null;
    }

    static String formatTime(long ms, char open, char close) {
        final long t = Math.max(0, ms);
        return String.format(Locale.US, "%c%02d:%02d.%03d%c", open, t / 60000, t / 1000 % 60, t % 1000, close);
    }

    public static Lyrics parse(String text, int source) {
        return parse(text, source, null);
    }

    public static Lyrics parse(String text, int source, String provider) {
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        final String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        final String[] raw = normalized.split("\n");
        long offset = 0;
        final ArrayList<Line> timed = new ArrayList<>();
        for (String row : raw) {
            final String line = row.trim();
            final Matcher off = OFFSET.matcher(line);
            if (off.find()) {
                try {
                    offset = Long.parseLong(off.group(1));
                } catch (NumberFormatException ignore) {
                }
                continue;
            }
            final Matcher m = TIME.matcher(line);
            ArrayList<Long> times = null;
            int end = 0;
            while (m.find() && m.start() == end) {
                if (times == null) {
                    times = new ArrayList<>();
                }
                times.add(toMillis(m.group(1), m.group(2), m.group(3)));
                end = m.end();
            }
            if (times == null) {
                continue;
            }
            final String rest = line.substring(end);
            if (times.size() == 1) {
                final Line words = parseWords(rest, Math.max(0, times.get(0) - offset), offset);
                if (words != null) {
                    timed.add(words);
                    continue;
                }
            }
            final String content = WORD_TIME.matcher(rest).replaceAll("").trim();
            for (long t : times) {
                timed.add(new Line(Math.max(0, t - offset), content));
            }
        }
        if (!timed.isEmpty()) {
            return fromTimedLines(timed, source, provider);
        }
        final ArrayList<Line> plain = new ArrayList<>();
        for (String row : raw) {
            final String line = row.trim();
            if (META.matcher(line).matches()) {
                continue;
            }
            if (line.isEmpty() && (plain.isEmpty() || plain.get(plain.size() - 1).isBlank())) {
                continue;
            }
            plain.add(new Line(-1, line));
        }
        while (!plain.isEmpty() && plain.get(plain.size() - 1).isBlank()) {
            plain.remove(plain.size() - 1);
        }
        return plain.isEmpty() ? null : new Lyrics(plain, false, false, source, provider, null);
    }

    private static Line parseWords(String rest, long lineTime, long offset) {
        final Matcher m = WORD_TIME.matcher(rest);
        final LineBuilder builder = new LineBuilder();
        long start = lineTime;
        int pos = 0;
        while (m.find()) {
            builder.add(rest.substring(pos, m.start()), start, -1);
            start = Math.max(0, toMillis(m.group(1), m.group(2), m.group(3)) - offset);
            pos = m.end();
        }
        if (pos == 0) {
            return null;
        }
        builder.add(rest.substring(pos), start, -1);
        return builder.isEmpty() ? null : builder.build(lineTime);
    }

    static Lyrics fromTimedLines(ArrayList<Line> timed, int source, String provider) {
        return fromTimedLines(timed, source, provider, null);
    }

    static Lyrics fromTimedLines(ArrayList<Line> timed, int source, String provider, String translationLanguage) {
        timed.sort(Comparator.comparingLong(l -> l.time));
        final ArrayList<Line> kept = new ArrayList<>(timed.size());
        for (int i = 0; i < timed.size(); i++) {
            final Line l = timed.get(i);
            if (l.isBlank() && (kept.isEmpty() || kept.get(kept.size() - 1).isBlank() || i == timed.size() - 1)) {
                continue;
            }
            kept.add(l);
        }
        if (kept.isEmpty()) {
            return null;
        }
        boolean words = false;
        for (int i = 0; i < kept.size(); i++) {
            final Line l = kept.get(i);
            final long next = i + 1 < kept.size() ? kept.get(i + 1).time : Long.MAX_VALUE;
            if (l.words != null || l.background != null && l.background.words != null) {
                kept.set(i, l.withWords(l.words != null ? resolveWordEnds(l.words, next) : null,
                        l.background != null && l.background.words != null ? l.background.withWords(resolveWordEnds(l.background.words, next), null) : l.background));
                words = true;
            }
        }
        if (!words) {
            return new Lyrics(kept, true, false, source, provider, translationLanguage);
        }
        final ArrayList<Line> out = new ArrayList<>(kept.size() + 8);
        if (!kept.get(0).isBlank() && kept.get(0).time >= PAUSE_MS) {
            out.add(new Line(0, ""));
        }
        for (int i = 0; i < kept.size(); i++) {
            final Line l = kept.get(i);
            out.add(l);
            final Line next = i + 1 < kept.size() ? kept.get(i + 1) : null;
            final long end = l.getEnd();
            if (next != null && !l.isBlank() && !next.isBlank() && end > l.time && next.time - end >= PAUSE_MS) {
                out.add(new Line(end, ""));
            }
        }
        return new Lyrics(out, true, false, source, provider, translationLanguage);
    }

    private static Word[] resolveWordEnds(Word[] source, long next) {
        final Word[] words = source.clone();
        for (int i = 0; i < words.length; i++) {
            final Word w = words[i];
            long end = w.end;
            if (end < 0) {
                end = i + 1 < words.length ? words[i + 1].start : Math.min(next, w.start + Math.max(LAST_WORD_MIN_MS, (w.to - w.from) * LAST_WORD_MS_PER_CHAR));
            }
            end = Math.max(end, w.start + 1);
            if (end != w.end) {
                words[i] = new Word(w.start, end, w.from, w.to);
            }
        }
        return words;
    }

    private static long toMillis(String min, String sec, String frac) {
        long ms = Long.parseLong(min) * 60_000L + Long.parseLong(sec) * 1000L;
        if (frac != null) {
            final int f = Integer.parseInt(frac);
            if (frac.length() == 1) {
                ms += f * 100L;
            } else if (frac.length() == 2) {
                ms += f * 10L;
            } else {
                ms += f;
            }
        }
        return ms;
    }

    public int indexAt(long ms) {
        if (!synced) {
            return -1;
        }
        int lo = 0;
        int hi = lines.size() - 1;
        int found = -1;
        while (lo <= hi) {
            final int mid = (lo + hi) >>> 1;
            if (lines.get(mid).time <= ms) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return found;
    }

    JSONObject toJson() throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("synced", synced);
        o.put("instrumental", instrumental);
        if (translationLanguage != null) {
            o.put("translationLanguage", translationLanguage);
        }
        final JSONArray array = new JSONArray();
        for (Line line : lines) {
            array.put(lineToJson(line));
        }
        o.put("lines", array);
        return o;
    }

    private static JSONObject lineToJson(Line line) throws JSONException {
        final JSONObject o = new JSONObject();
        o.put("t", line.time);
        o.put("x", line.text);
        if (line.words != null) {
            final JSONArray words = new JSONArray();
            for (Word w : line.words) {
                words.put(w.start).put(w.end).put(w.from).put(w.to);
            }
            o.put("w", words);
        }
        if (line.background != null) {
            o.put("bg", lineToJson(line.background));
        }
        if (line.voice != VOICE_MAIN) {
            o.put("v", line.voice);
        }
        if (line.translation != null) {
            o.put("tr", line.translation);
        }
        if (line.romanization != null) {
            o.put("ro", line.romanization);
        }
        return o;
    }

    static Lyrics fromJson(JSONObject o, int source, String provider) throws JSONException {
        if (o.optBoolean("instrumental", false)) {
            return instrumental(source, provider);
        }
        final JSONArray array = o.getJSONArray("lines");
        final ArrayList<Line> lines = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            final Line line = lineFromJson(array.getJSONObject(i));
            if (line == null) {
                return null;
            }
            lines.add(line);
        }
        if (lines.isEmpty()) {
            return null;
        }
        return new Lyrics(lines, o.optBoolean("synced", false), false, source, provider, o.isNull("translationLanguage") ? null : o.optString("translationLanguage", null));
    }

    private static Line lineFromJson(JSONObject o) throws JSONException {
        final String text = o.getString("x");
        Word[] words = null;
        final JSONArray w = o.optJSONArray("w");
        if (w != null) {
            if (w.length() == 0 || w.length() % 4 != 0) {
                return null;
            }
            words = new Word[w.length() / 4];
            for (int i = 0; i < words.length; i++) {
                final int from = w.getInt(i * 4 + 2);
                final int to = w.getInt(i * 4 + 3);
                if (from < 0 || to < from || to > text.length()) {
                    return null;
                }
                words[i] = new Word(w.getLong(i * 4), w.getLong(i * 4 + 1), from, to);
            }
        }
        final JSONObject bg = o.optJSONObject("bg");
        final Line background = bg != null ? lineFromJson(bg) : null;
        return new Line(o.getLong("t"), text, words, background, o.optInt("v", VOICE_MAIN),
                o.isNull("tr") ? null : o.optString("tr", null), o.isNull("ro") ? null : o.optString("ro", null));
    }
}
