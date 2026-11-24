/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player.motion;

import com.exteragram.messenger.player.TrackQuery;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Pattern;

final class MotionCoverMatcher {

    private static final String[] MOTION_KEYS = {"motionDetailSquare", "motionSquareVideo1x1"};

    private static final int MAX_DURATION_DIFF = 3;
    private static final int MAX_DURATION_MISMATCH = 10;
    private static final int MAX_CANDIDATES = 3;
    private static final String VARIOUS_ARTISTS = "variousartists";
    private static final String DJ_MIX = "djmix";
    private static final String[] VERSION_TAGS = {
            "remix", "live", "acoustic", "sped up", "speed up", "slowed", "reverb", "instrumental", "karaoke",
            "cover", "demo", "extended", "nightcore", "mixed", "acapella", "a cappella", "radio edit", "unplugged", "8d"
    };
    private static final Pattern ARTIST_SEPARATOR = Pattern.compile("\\s*(?:,|&|/|\\bfeat\\.?|\\bft\\.?|\\bx\\b|\\band\\b)\\s*", Pattern.CASE_INSENSITIVE);

    private MotionCoverMatcher() {
    }

    static ArrayList<JSONObject> bestSongs(JSONArray data, String artist, String title, int duration) {
        final ArrayList<JSONObject> best = new ArrayList<>();
        final String wantTitle = TrackQuery.normalize(title);
        final String wantArtist = TrackQuery.normalize(artist);
        if (data == null || wantTitle.isEmpty()) {
            return best;
        }
        final HashSet<String> wantTags = versionTags(title);
        final HashSet<String> seenIsrc = new HashSet<>();
        int bestScore = 0;
        for (int i = 0; i < data.length(); i++) {
            final JSONObject item = data.optJSONObject(i);
            final JSONObject attributes = item != null ? item.optJSONObject("attributes") : null;
            if (attributes == null) {
                continue;
            }
            final String rawTitle = attributes.optString("name");
            final String candidate = TrackQuery.normalize(rawTitle);
            int score;
            if (candidate.equals(wantTitle)) {
                score = 40;
            } else if (!candidate.isEmpty() && (candidate.contains(wantTitle) || wantTitle.contains(candidate))) {
                if (!versionTags(rawTitle).equals(wantTags)) {
                    continue;
                }
                score = 20;
            } else {
                continue;
            }
            if (!wantArtist.isEmpty()) {
                if (!artistMatches(attributes.optString("artistName"), artist)) {
                    continue;
                }
                score += 40;
            }
            final long durationMs = attributes.optLong("durationInMillis");
            final long diff = duration > 0 && durationMs > 0 ? Math.abs(Math.round(durationMs / 1000.0) - duration) : -1;
            if (diff > MAX_DURATION_MISMATCH || wantArtist.isEmpty() && (diff < 0 || diff > MAX_DURATION_DIFF)) {
                continue;
            }
            if (diff >= 0 && diff <= MAX_DURATION_DIFF) {
                score += 30;
            }
            if (score > bestScore) {
                bestScore = score;
                best.clear();
                seenIsrc.clear();
            }
            if (score == bestScore && best.size() < MAX_CANDIDATES && seenIsrc.add(attributes.optString("isrc"))) {
                best.add(attributes);
            }
        }
        return best;
    }

    static String pickVideo(JSONArray songs, String tagAlbum, String searchAlbum, String artist) {
        if (songs == null) {
            return null;
        }
        final String wantTag = albumKey(tagAlbum);
        final String wantSearch = albumKey(searchAlbum);
        String searchMatch = null;
        String fallback = null;
        for (int i = 0; i < songs.length(); i++) {
            final JSONObject song = songs.optJSONObject(i);
            final JSONObject relationships = song != null ? song.optJSONObject("relationships") : null;
            final JSONObject albums = relationships != null ? relationships.optJSONObject("albums") : null;
            final JSONArray data = albums != null ? albums.optJSONArray("data") : null;
            if (data == null) {
                continue;
            }
            for (int j = 0; j < data.length(); j++) {
                final JSONObject album = data.optJSONObject(j);
                final JSONObject attributes = album != null ? album.optJSONObject("attributes") : null;
                final String video = attributes != null ? motionUrl(attributes.optJSONObject("editorialVideo")) : null;
                if (video == null) {
                    continue;
                }
                final String name = albumKey(attributes.optString("name"));
                if (TrackQuery.normalize(name).contains(DJ_MIX)) {
                    continue;
                }
                if (!wantTag.isEmpty() && !name.isEmpty() && name.equals(wantTag)) {
                    return video;
                }
                if (searchMatch == null && !name.isEmpty() && name.equals(wantSearch)) {
                    searchMatch = video;
                }
                if (fallback == null && isOwnRelease(attributes, artist)) {
                    fallback = video;
                }
            }
        }
        return searchMatch != null ? searchMatch : fallback;
    }

    private static String motionUrl(JSONObject editorialVideo) {
        if (editorialVideo == null) {
            return null;
        }
        for (String key : MOTION_KEYS) {
            final JSONObject asset = editorialVideo.optJSONObject(key);
            final String video = asset != null ? asset.optString("video") : "";
            if (video.startsWith("https://")) {
                return video;
            }
        }
        return null;
    }

    private static String albumKey(String album) {
        final String key = TrackQuery.normalize(album);
        return key.isEmpty() && album != null ? album.trim().toLowerCase(Locale.ROOT) : key;
    }

    private static HashSet<String> versionTags(String title) {
        final HashSet<String> tags = new HashSet<>();
        if (title == null) {
            return tags;
        }
        final String words = " " + TrackQuery.NOT_WORD.matcher(title.toLowerCase(Locale.ROOT)).replaceAll(" ").trim() + " ";
        for (String tag : VERSION_TAGS) {
            if (words.contains(" " + tag + " ")) {
                tags.add(tag);
            }
        }
        return tags;
    }

    private static boolean isOwnRelease(JSONObject album, String artist) {
        if (album.optBoolean("isCompilation")) {
            return false;
        }
        final String albumArtist = TrackQuery.normalize(album.optString("artistName"));
        return !albumArtist.equals(VARIOUS_ARTISTS) && artistMatches(album.optString("artistName"), artist);
    }

    private static boolean artistMatches(String candidate, String wanted) {
        final String a = TrackQuery.normalize(candidate);
        final String b = TrackQuery.normalize(wanted);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        if (a.contains(b) || b.contains(a)) {
            return true;
        }
        for (String part : ARTIST_SEPARATOR.split(wanted)) {
            final String p = TrackQuery.normalize(part);
            if (p.length() >= 2 && a.contains(p)) {
                return true;
            }
        }
        return false;
    }
}
