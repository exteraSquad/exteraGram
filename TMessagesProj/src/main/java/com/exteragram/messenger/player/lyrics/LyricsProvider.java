/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player.lyrics;

import com.exteragram.messenger.ExteraConfig;

import java.util.ArrayList;
import java.util.Set;

public enum LyricsProvider {
    LRCLIB("lrclib", "LRCLIB"),
    LRCMUX("lrcmux", "lrcmux"),
    DEEZER("deezer", "Deezer"),
    NETEASE("netease", "NetEase Cloud Music"),
    QQ_MUSIC("qqmusic", "QQ Music"),
    BETTER_LYRICS("betterlyrics", "Better Lyrics"),
    BINIMUM("binimum", "Binimum");

    public final String id;
    public final String title;

    LyricsProvider(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public boolean isEnabled() {
        return ExteraConfig.getLyricsProviders().contains(id);
    }

    public static ArrayList<LyricsProvider> enabled() {
        final Set<String> ids = ExteraConfig.getLyricsProviders();
        final ArrayList<LyricsProvider> out = new ArrayList<>();
        for (LyricsProvider provider : values()) {
            if (ids.contains(provider.id)) {
                out.add(provider);
            }
        }
        return out;
    }
}
