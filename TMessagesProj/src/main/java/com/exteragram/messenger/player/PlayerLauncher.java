/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player;

import android.content.Context;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AudioPlayerAlert;

public final class PlayerLauncher {

    private PlayerLauncher() {
    }

    public static BottomSheet create(Context context, Theme.ResourcesProvider resourcesProvider) {
        final MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        if (ExteraConfig.getNewPlayerStyle() && (playing == null || playing.isMusic())) {
            return new MusicPlayerSheet(context, resourcesProvider);
        }
        return new AudioPlayerAlert(context, resourcesProvider);
    }

    public static boolean isShowing() {
        return MusicPlayerSheet.instance != null && MusicPlayerSheet.instance.isShowing()
                || AudioPlayerAlert.instance != null && AudioPlayerAlert.instance.isShowing();
    }

    public static void onPlaybackOrderChanged() {
        if (MusicPlayerSheet.instance != null) {
            MusicPlayerSheet.instance.updateOrderButtons(true);
        }
    }
}
