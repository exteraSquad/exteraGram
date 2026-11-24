/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.feed;

import android.view.Gravity;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.ItemOptions;

import java.util.ArrayList;
import java.util.function.Consumer;

public final class FeedChannelActions {

    private FeedChannelActions() {
    }

    public static boolean canLeave(TLRPC.Chat chat) {
        return chat != null && !chat.creator && !ChatObject.isNotInChat(chat);
    }

    public static void showAvatarMenu(ChatActivity fragment, ChatMessageCell cell, TLRPC.Chat chat,
                                      Runnable openChannel, Runnable onFeedChanged,
                                      Consumer<ArrayList<Integer>> onDeletedRows) {
        if (fragment == null || cell == null || chat == null) {
            return;
        }
        ItemOptions.makeOptions(fragment, cell)
                .add(chat.broadcast ? R.drawable.msg_channel : R.drawable.msg_discussion,
                        LocaleController.getString(chat.broadcast ? R.string.OpenChannel2 : R.string.OpenGroup2),
                        openChannel)
                .add(R.drawable.menu_hide_gift, LocaleController.getString(R.string.FeedHideChannel),
                        () -> fragment.hideFeedChannelWithUndo(-chat.id, chat.title))
                .addIf(canLeave(chat), R.drawable.msg_leave,
                        LocaleController.getString(chat.broadcast ? R.string.LeaveChannelMenu : R.string.LeaveMegaMenu),
                        true, () -> leaveChannel(fragment, chat, onFeedChanged, onDeletedRows))
                .setDrawScrim(false)
                .setGravity(Gravity.LEFT)
                .forceBottom(true)
                .show();
    }

    public static void leaveChannel(BaseFragment fragment, TLRPC.Chat chat,
                                    Runnable onFeedChanged,
                                    Consumer<ArrayList<Integer>> onDeletedRows) {
        if (fragment == null || chat == null || fragment.getParentActivity() == null) {
            return;
        }
        AlertsCreator.createClearOrDeleteDialogAlert(fragment, false, chat, null, false, true, false, false, param -> {
            long dialogId = -chat.id;
            if (ChatObject.isNotInChat(chat)) {
                fragment.getMessagesController().deleteDialog(dialogId, 0, param);
            } else {
                fragment.getMessagesController().deleteParticipantFromChat(
                        chat.id,
                        fragment.getMessagesController().getUser(fragment.getUserConfig().getClientUserId()),
                        null,
                        param,
                        param);
            }
            deleteFeedRows(fragment, dialogId, onDeletedRows);
            if (onFeedChanged != null) {
                onFeedChanged.run();
            }
        });
    }

    private static void deleteFeedRows(BaseFragment fragment, long dialogId,
                                       Consumer<ArrayList<Integer>> onDeletedRows) {
        ArrayList<Integer> deletedIds = FeedController.getInstance(fragment.getCurrentAccount()).deleteHistory(dialogId, Integer.MAX_VALUE);
        if (onDeletedRows != null) {
            onDeletedRows.accept(deletedIds);
        }
    }
}
