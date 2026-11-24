/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.chats;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

public class DoubleTapUtils {

    public static final int MAX_SETTING_INDEX = 9;

    public static final int ACTION_DISABLE = 0;
    public static final int ACTION_REACTIONS = 1;
    public static final int ACTION_REPLY = 2;
    public static final int ACTION_COPY = 3;
    public static final int ACTION_FORWARD = 4;
    public static final int ACTION_EDIT = 5;
    public static final int ACTION_SAVE = 6;
    public static final int ACTION_REPEAT = 7;
    public static final int ACTION_DELETE = 8;
    public static final int ACTION_TRANSLATE = 9;

    public static CharSequence[] getDoubleTapActions(boolean out) {
        if (!out) {
            return new CharSequence[]{
                    LocaleController.getString(R.string.Disable),
                    LocaleController.getString(R.string.Reactions),
                    LocaleController.getString(R.string.Reply),
                    LocaleController.getString(R.string.Copy),
                    LocaleController.getString(R.string.Forward),
                    LocaleController.getString(R.string.Save),
                    LocaleController.getString(R.string.Repeat),
                    LocaleController.getString(R.string.Delete),
                    LocaleController.getString(R.string.TranslateMessage),
            };
        }
        return new CharSequence[]{
                LocaleController.getString(R.string.Disable),
                LocaleController.getString(R.string.Reactions),
                LocaleController.getString(R.string.Reply),
                LocaleController.getString(R.string.Copy),
                LocaleController.getString(R.string.Forward),
                LocaleController.getString(R.string.Edit),
                LocaleController.getString(R.string.Save),
                LocaleController.getString(R.string.Repeat),
                LocaleController.getString(R.string.Delete),
                LocaleController.getString(R.string.TranslateMessage)
        };
    }

    public static CharSequence getDoubleTapActionLabel(int setting, boolean out) {
        CharSequence[] actions = getDoubleTapActions(out);
        return actions[Math.min(Math.max(setting, 0), actions.length - 1)];
    }

    public static int getDoubleTapActionIcon(int setting, boolean out) {
        int[] icons = getDoubleTapIcons(out);
        return icons[Math.min(Math.max(setting, 0), icons.length - 1)];
    }

    public static int[] getDoubleTapIcons(boolean out) {
        if (!out) {
            return new int[]{
                    R.drawable.msg_block,
                    R.drawable.msg_reactions2,
                    R.drawable.menu_reply,
                    R.drawable.msg_copy,
                    R.drawable.msg_forward,
                    R.drawable.msg_saved,
                    R.drawable.msg_repeat,
                    R.drawable.msg_delete,
                    R.drawable.msg_translate
            };
        }
        return new int[]{
                R.drawable.msg_block,
                R.drawable.msg_reactions2,
                R.drawable.menu_reply,
                R.drawable.msg_copy,
                R.drawable.msg_forward,
                R.drawable.msg_edit,
                R.drawable.msg_saved,
                R.drawable.msg_repeat,
                R.drawable.msg_delete,
                R.drawable.msg_translate
        };
    }

    public static int getActionId(int setting, boolean out) {
        if (setting == ACTION_TRANSLATE) {
            setting = MAX_SETTING_INDEX;
        } else {
            setting = sanitizeSetting(setting);
        }
        if (!out) {
            return switch (setting) {
                case 1 -> ACTION_REACTIONS;
                case 2 -> ACTION_REPLY;
                case 3 -> ACTION_COPY;
                case 4 -> ACTION_FORWARD;
                case 5 -> ACTION_SAVE;
                case 6 -> ACTION_REPEAT;
                case 7 -> ACTION_DELETE;
                case 8 -> ACTION_TRANSLATE;
                default -> ACTION_DISABLE;
            };
        } else {
            return switch (setting) {
                case 1 -> ACTION_REACTIONS;
                case 2 -> ACTION_REPLY;
                case 3 -> ACTION_COPY;
                case 4 -> ACTION_FORWARD;
                case 5 -> ACTION_EDIT;
                case 6 -> ACTION_SAVE;
                case 7 -> ACTION_REPEAT;
                case 8 -> ACTION_DELETE;
                case 9 -> ACTION_TRANSLATE;
                default -> ACTION_DISABLE;
            };
        }
    }

    public static int sanitizeSetting(int setting) {
        if (setting < 0) {
            return 0;
        }
        return Math.min(setting, MAX_SETTING_INDEX);
    }
}
