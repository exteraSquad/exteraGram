/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.messageexport.model;

import android.util.SparseArray;

import com.exteragram.messenger.ExteraConfig;

import com.exteragram.messenger.utils.chats.WidePosts;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;

import java.util.HashSet;

public final class MessageExportChatContext {

    public static final int CARD_MARGIN = 12;

    public final int account;
    public final TLRPC.Chat chat;
    public final TLRPC.User user;
    public final Theme.ResourcesProvider resourcesProvider;

    private final boolean isChat;
    private final boolean isPrivate;
    private final boolean isMegagroup;
    private final boolean isForum;
    private final boolean isMonoForum;
    private final boolean isThreadChat;
    private final boolean isForumGeneral;
    private final boolean isSavedChat;
    private final boolean isBot;
    private final boolean isRepliesChat;
    private final boolean hasDiscussion;
    private final long linkedChatId;
    final boolean forumAsMessages;
    private final HashSet<Long> pinnedKeys = new HashSet<>();

    public MessageExportChatContext(ChatActivity fragment, SparseArray<MessageObject>[] selected) {
        account = fragment.getCurrentAccount();
        chat = fragment.getCurrentChat();
        user = fragment.getCurrentUser();
        resourcesProvider = fragment.getResourceProvider();
        isChat = chat != null || UserObject.isUserSelf(user) || UserObject.isReplyUser(user);
        isPrivate = user != null && !isChat;
        isMegagroup = ChatObject.isChannel(chat) && chat != null && chat.megagroup;
        isForum = ChatObject.isForum(chat);
        isMonoForum = ChatObject.isMonoForum(chat);
        isThreadChat = (fragment.getThreadId() != 0 || isForum && fragment.isTopic) && !isMonoForum;
        isForumGeneral = isForum && fragment.isTopic && fragment.getTopicId() == 1;
        isSavedChat = fragment.getChatMode() == ChatActivity.MODE_SAVED;
        isBot = user != null && user.bot;
        isRepliesChat = UserObject.isReplyUser(user);
        hasDiscussion = ChatObject.isChannel(chat) && chat != null && chat.has_link && !chat.megagroup;
        TLRPC.ChatFull chatInfo = fragment.getCurrentChatInfo();
        linkedChatId = chatInfo != null ? chatInfo.linked_chat_id : 0;
        forumAsMessages = fragment.isForumInViewAsMessagesMode();
        for (SparseArray<MessageObject> array : selected) {
            for (int i = 0; i < array.size(); i++) {
                MessageObject message = array.valueAt(i);
                if (fragment.isMessagePinned(message.getId())) {
                    pinnedKeys.add(pinnedKey(message));
                }
            }
        }
    }

    private static long pinnedKey(MessageObject message) {
        long groupId = message.getGroupId();
        return groupId != 0 ? groupId : -message.getId();
    }

    public boolean isForum() {
        return isForum;
    }

    public boolean isMonoForum() {
        return isMonoForum;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    private boolean hasAdmins() {
        return chat != null && !ChatObject.isChannelAndNotMegaGroup(chat) && !UserObject.isBotForum(user);
    }

    public boolean isAdmin(long uid) {
        return hasAdmins() && MessagesController.getInstance(account).isAdmin(chat.id, uid);
    }

    public boolean isOwner(long uid) {
        return hasAdmins() && MessagesController.getInstance(account).isOwner(chat.id, uid);
    }

    public String getAdminRank(long uid) {
        return hasAdmins() ? MessagesController.getInstance(account).getAdminRank(chat.id, uid) : null;
    }

    boolean isSelfOrReplies() {
        return UserObject.isUserSelf(user) || UserObject.isReplyUser(user);
    }

    public void apply(ChatMessageCell cell, MessageObject message) {
        cell.isChat = (isChat || isPrivate && ExteraConfig.getMessageExportAuthors()) && !hidesSource(message);
        cell.isMegagroup = isMegagroup;
        cell.isForum = isForum;
        cell.isMonoForum = isMonoForum;
        cell.isThreadChat = isThreadChat;
        cell.isForumGeneral = isForumGeneral;
        cell.isSavedChat = isSavedChat;
        cell.isBot = isBot;
        cell.isRepliesChat = isRepliesChat;
        cell.hasDiscussion = hasDiscussion;
        cell.linkedChatId = linkedChatId;
        cell.isPinned = pinnedKeys.contains(pinnedKey(message));
    }

    void applyMessage(MessageObject message) {
        WidePosts.setProfileHidden(message, hidesSource(message));
        boolean avatar = showsSource(message) || isPrivate && ExteraConfig.getMessageExportAuthors() && !message.isOutOwner();
        boolean notime = !ExteraConfig.getMessageExportTime();
        if (message.forceAvatar != avatar || message.notime != notime) {
            message.forceAvatar = avatar;
            message.notime = notime;
            message.resetLayout();
        }
    }

    public static int getWidthCompensation(MessageObject message) {
        if (message == null || message.isCompactMode() || WidePosts.isEnabledFor(message)) {
            return 0;
        }
        boolean plainText = message.type == MessageObject.TYPE_TEXT && !hasCode(message);
        boolean single = message.getGroupId() == 0;
        boolean singleFile = message.type == MessageObject.TYPE_FILE && single;
        if (message.forceAvatar && (plainText || singleFile)) {
            return AndroidUtilities.dp(52);
        }
        if (message.getDialogId() >= 0 && message.messageOwner.paid_message_stars > 0 || message.messageOwner.suggested_post != null) {
            return 0;
        }
        boolean singleMedia = single && (message.type == MessageObject.TYPE_PHOTO || message.type == MessageObject.TYPE_VIDEO || message.type == MessageObject.TYPE_GIF);
        return plainText || singleFile || singleMedia ? AndroidUtilities.dp(CARD_MARGIN) * 2 : 0;
    }

    private static boolean hasCode(MessageObject message) {
        for (TLRPC.MessageEntity entity : message.messageOwner.entities) {
            if (entity instanceof TLRPC.TL_messageEntityPre) {
                return true;
            }
        }
        return false;
    }

    private static boolean hidesSource(MessageObject message) {
        return !ExteraConfig.getMessageExportSource() && isIncomingPost(message);
    }

    private static boolean isIncomingPost(MessageObject message) {
        return message.messageOwner != null && message.messageOwner.post && !message.isOutOwner();
    }

    public static boolean showsSource(MessageObject message) {
        return ExteraConfig.getMessageExportSource() && isIncomingPost(message);
    }
}
