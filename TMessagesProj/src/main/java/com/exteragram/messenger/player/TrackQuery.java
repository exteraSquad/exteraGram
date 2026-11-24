/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player;

import android.text.TextUtils;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.audioinfo.AudioInfo;

import java.util.Locale;
import java.util.regex.Pattern;

public record TrackQuery(String artist, String title, String album, int duration) {

    public static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    private static final Pattern AUDIO_EXT = Pattern.compile("\\.(mp3|m4a|flac|ogg|oga|opus|wav|aac|alac|wma)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOPIC_CHANNEL = Pattern.compile("\\s+-\\s+Topic$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DECORATION = Pattern.compile("\\s*[(\\[][^)\\]]*(official|lyric|video|audio|visualizer|remaster|hq|hd)[^)\\]]*[)\\]]", Pattern.CASE_INSENSITIVE);

    public static TrackQuery forMessage(MessageObject messageObject) {
        if (messageObject == null || messageObject.isVoice() || messageObject.isRoundVideo()) {
            return null;
        }
        final AudioInfo audioInfo = PlayerCovers.getPlayingAudioInfo(messageObject);
        String artist = clean(messageObject.getMusicAuthor(false));
        String title = clean(messageObject.getMusicTitle(false));
        if (title != null) {
            title = AUDIO_EXT.matcher(title).replaceFirst("").replace('_', ' ').trim();
        }
        if (TextUtils.isEmpty(artist) && title != null) {
            final int dash = title.indexOf(" - ");
            if (dash > 0) {
                artist = title.substring(0, dash).trim();
                title = title.substring(dash + 3).trim();
            }
        }
        title = stripDecorations(title);
        artist = stripTopicChannel(artist);
        if (TextUtils.isEmpty(title)) {
            return null;
        }
        return new TrackQuery(artist, title, clean(audioInfo != null ? audioInfo.getAlbum() : null), (int) Math.round(messageObject.getDuration()));
    }

    public String key() {
        return (artist == null ? "" : artist.toLowerCase(Locale.ROOT)) + "\u0001" + title.toLowerCase(Locale.ROOT) + "\u0001" + duration;
    }

    public static String normalize(String s) {
        return s == null ? "" : NOT_WORD.matcher(s.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private static String stripTopicChannel(String s) {
        if (s == null) {
            return null;
        }
        final String out = TOPIC_CHANNEL.matcher(s).replaceFirst("").trim();
        return out.isEmpty() ? s : out;
    }

    private static String stripDecorations(String s) {
        if (s == null) {
            return null;
        }
        final String out = DECORATION.matcher(s).replaceAll("").trim();
        return out.isEmpty() ? s : out;
    }
}
