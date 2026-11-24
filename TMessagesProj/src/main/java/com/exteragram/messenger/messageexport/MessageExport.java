/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.messageexport;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.util.SparseArray;

import com.exteragram.messenger.messageexport.appearance.MessageExportAppearance;
import com.exteragram.messenger.messageexport.model.MessageExportChatContext;
import com.exteragram.messenger.messageexport.model.MessageExportDocument;
import com.exteragram.messenger.messageexport.model.MessageExportSession;
import com.exteragram.messenger.messageexport.ui.MessageExportSheet;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.EmojiThemes;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;

public final class MessageExport {

    public static final int MAX_MESSAGES = 100;

    private MessageExport() {
    }

    public static boolean canExport(ChatActivity fragment, SparseArray<MessageObject>[] selected) {
        if (!isChatSupported(fragment)) {
            return false;
        }
        int count = 0;
        for (SparseArray<MessageObject> array : selected) {
            for (int i = 0; i < array.size(); i++) {
                if (!isMessageSupported(array.valueAt(i))) {
                    return false;
                }
                count++;
            }
        }
        return count > 0;
    }

    public static void open(ChatActivity fragment, SparseArray<MessageObject>[] selected) {
        Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        if (!canExport(fragment, selected)) {
            BulletinFactory.of(fragment).createErrorBulletin(getString(R.string.MessageExportUnavailable)).show();
            return;
        }
        if (countSelected(selected) > MAX_MESSAGES) {
            BulletinFactory.of(fragment).createErrorBulletin(formatString(R.string.MessageExportLimit, MAX_MESSAGES)).show();
            return;
        }
        MessageExportChatContext context = new MessageExportChatContext(fragment, selected);
        MessageExportDocument document = MessageExportDocument.create(context, selected, fragment.messages);
        document.preloadPhotos();
        Drawable wallpaper = fragment.getContentView() != null ? fragment.getContentView().getBackgroundImage() : null;
        ChatActivity.ThemeDelegate themeDelegate = fragment.themeDelegate;
        EmojiThemes chatPreset = themeDelegate != null ? themeDelegate.getCurrentTheme() : null;
        boolean chatMatchesApp = chatPreset == null && (themeDelegate == null || themeDelegate.getCurrentWallpaper() == null);
        MessageExportAppearance appearance = new MessageExportAppearance(context.account, context.resourcesProvider, wallpaper, chatPreset, chatMatchesApp);
        MessageExportSession session = new MessageExportSession(activity, document, appearance);
        fragment.showDialog(new MessageExportSheet(activity, fragment, session));
    }

    private static int countSelected(SparseArray<MessageObject>[] selected) {
        int count = 0;
        for (SparseArray<MessageObject> array : selected) {
            count += array.size();
        }
        return count;
    }

    static boolean isChatSupported(ChatActivity fragment) {
        if (fragment == null || fragment.isPeerNoForwards() || fragment.isReport()) {
            return false;
        }
        int mode = fragment.getChatMode();
        if (mode != ChatActivity.MODE_DEFAULT && mode != ChatActivity.MODE_SCHEDULED && mode != ChatActivity.MODE_SAVED && mode != ChatActivity.MODE_PINNED && mode != ChatActivity.MODE_SUGGESTIONS) {
            return false;
        }
        return !DialogObject.isEncryptedDialog(fragment.getDialogId());
    }

    static boolean isMessageSupported(MessageObject message) {
        if (message == null || message.messageOwner == null || message.isSponsored() || message.isDateObject) {
            return false;
        }
        TLRPC.Message owner = message.messageOwner;
        if (owner.noforwards) {
            return false;
        }
        if (owner instanceof TLRPC.TL_messageService) {
            return message.contentType == 0 || message.contentType == 1;
        }
        if (!(owner instanceof TLRPC.TL_message)) {
            return false;
        }
        if (message.getId() <= 0 || message.isSending() || message.isEditing() || message.isSendError()) {
            return false;
        }
        if (message.needDrawBluredPreview() || message.isVoiceOnce() || message.isRoundOnce() || message.isHiddenSensitive()) {
            return false;
        }
        return !(owner.media instanceof TLRPC.TL_messageMediaPaidMedia);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static SparseArray<MessageObject>[] createSelection() {
        return new SparseArray[]{new SparseArray<>(), new SparseArray<>()};
    }

    static boolean select(SparseArray<MessageObject>[] selected, long dialogId, MessageObject message) {
        SparseArray<MessageObject> target = selected[message.getDialogId() == dialogId ? 0 : 1];
        if (target.indexOfKey(message.getId()) >= 0) {
            return false;
        }
        target.put(message.getId(), message);
        return true;
    }
}
