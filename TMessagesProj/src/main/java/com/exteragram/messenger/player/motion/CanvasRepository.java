/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player.motion;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.player.TrackQuery;

import org.telegram.messenger.AndroidUtilities;

import java.util.Collection;

public final class CanvasRepository {

    public interface Callback {
        void onResult(String path);
    }

    private CanvasRepository() {
    }

    public static boolean isSupported() {
        return !AndroidUtilities.isTablet();
    }

    public static boolean isEnabled() {
        return isSupported() && (spotifyEnabled() || yandexEnabled());
    }

    public static void setWantedKeys(Collection<String> keys) {
        SpotifyCanvasRepository.setWantedKeys(keys);
        YandexCanvasRepository.setWantedKeys(keys);
    }

    public static void fetch(TrackQuery query, boolean priority, Callback callback) {
        if (spotifyEnabled()) {
            SpotifyCanvasRepository.fetch(query, priority, path -> {
                if (path != null || !yandexEnabled()) {
                    callback.onResult(path);
                } else {
                    YandexCanvasRepository.fetch(query, priority, callback);
                }
            });
        } else if (yandexEnabled()) {
            YandexCanvasRepository.fetch(query, priority, callback);
        } else {
            AndroidUtilities.runOnUIThread(() -> callback.onResult(null));
        }
    }

    public static boolean isPathEnabled(String videoPath) {
        return spotifyEnabled() && SpotifyCanvasRepository.owns(videoPath) || yandexEnabled() && YandexCanvasRepository.owns(videoPath);
    }

    public static String getThumbPath(String videoPath) {
        return CanvasSource.thumbPath(videoPath);
    }

    private static boolean spotifyEnabled() {
        return ExteraConfig.getPlayerSpotifyCanvas() && SpotifyCanvasRepository.isLoggedIn();
    }

    private static boolean yandexEnabled() {
        return ExteraConfig.getPlayerYandexCanvas() && YandexCanvasRepository.isLoggedIn();
    }
}
