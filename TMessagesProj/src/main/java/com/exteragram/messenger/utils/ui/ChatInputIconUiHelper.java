/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui;

import androidx.annotation.Nullable;

import com.exteragram.messenger.IconPackType;
import com.exteragram.messenger.icons.IconManager;

import org.telegram.messenger.R;
import org.telegram.ui.Components.ChatActivityEnterViewAnimatedIconView.State;

import java.util.function.ToIntFunction;

public final class ChatInputIconUiHelper {
    private ChatInputIconUiHelper() {
    }

    @Nullable
    public static ToIntFunction<State> getStaticIcons() {
        return IconManager.INSTANCE.isBasePackOnly(IconPackType.DEFAULT) ? null : ChatInputIconUiHelper::getStaticIconResource;
    }

    private static int getStaticIconResource(State state) {
        return switch (state) {
            case VOICE -> R.drawable.input_mic_pressed;
            case VIDEO -> R.drawable.input_video_pressed;
            case STICKER -> R.drawable.msg_sticker;
            case KEYBOARD -> R.drawable.input_keyboard;
            case SMILE -> R.drawable.input_smile;
            case GIF -> R.drawable.msg_gif;
        };
    }
}
