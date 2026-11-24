/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.chats;

import androidx.annotation.Nullable;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum SwipeAction {

    REACTION(DoubleTapUtils.ACTION_REACTIONS, R.drawable.msg_reactions2, R.string.DoubleTapSetting, 1f),
    REPLY(DoubleTapUtils.ACTION_REPLY, R.drawable.menu_reply, R.string.Reply, 1.09f),
    COPY(DoubleTapUtils.ACTION_COPY, R.drawable.msg_copy, R.string.Copy, 1f),
    FORWARD(DoubleTapUtils.ACTION_FORWARD, R.drawable.msg_forward, R.string.Forward, 1.09f),
    EDIT(DoubleTapUtils.ACTION_EDIT, R.drawable.msg_edit, R.string.Edit, 1f),
    SAVE(DoubleTapUtils.ACTION_SAVE, R.drawable.msg_saved, R.string.Save, 1f),
    REPEAT(DoubleTapUtils.ACTION_REPEAT, R.drawable.msg_repeat, R.string.Repeat, 1.09f),
    DELETE(DoubleTapUtils.ACTION_DELETE, R.drawable.msg_delete, R.string.Delete, 1.09f),
    TRANSLATE(DoubleTapUtils.ACTION_TRANSLATE, R.drawable.msg_translate, R.string.TranslateMessage, 1.09f);

    public final int actionId;
    public final int iconRes;
    public final int titleRes;
    public final float iconTrim;

    SwipeAction(int actionId, int iconRes, int titleRes, float iconTrim) {
        this.actionId = actionId;
        this.iconRes = iconRes;
        this.titleRes = titleRes;
        this.iconTrim = iconTrim;
    }

    @Nullable
    public static String quickReactionEmoticon(int account) {
        final MediaDataController controller = MediaDataController.getInstance(account);
        final String setting = controller.getDoubleTapReaction();
        if (setting == null) {
            return null;
        }
        if (setting.startsWith("animated_")) {
            return setting;
        }
        final TLRPC.TL_availableReaction available = controller.getReactionsMap().get(setting);
        return available != null ? available.reaction : null;
    }

    @Nullable
    public static SwipeAction of(int actionId) {
        for (var action : values()) {
            if (action.actionId == actionId) return action;
        }
        return null;
    }

    public static List<SwipeAction> enabled() {
        var result = new ArrayList<SwipeAction>();
        for (var part : ExteraConfig.getSwipeActions().split(",")) {
            SwipeAction action;
            try {
                action = of(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignore) {
                continue;
            }
            if (action != null && !result.contains(action)) result.add(action);
        }
        return result;
    }

    public static List<SwipeAction> disabled() {
        var result = new ArrayList<>(Arrays.asList(values()));
        result.removeAll(enabled());
        return result;
    }

    public static void setEnabled(List<SwipeAction> actions) {
        var raw = new StringBuilder();
        for (var action : actions) {
            if (raw.length() > 0) raw.append(',');
            raw.append(action.actionId);
        }
        ExteraConfig.setSwipeActions(raw.toString());
    }

    public boolean isEnabled() {
        return enabled().contains(this);
    }

    public void setEnabled(boolean enabled) {
        var actions = enabled();
        if (enabled == actions.contains(this)) return;
        if (enabled) {
            var position = 0;
            for (var candidate : values()) {
                if (candidate == this) break;
                if (actions.contains(candidate)) position++;
            }
            actions.add(position, this);
        } else {
            actions.remove(this);
        }
        setEnabled(actions);
    }
}
