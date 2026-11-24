/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.chats;

import static org.telegram.messenger.AndroidUtilities.dp;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.feed.FeedMessageUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

public final class WidePosts {
    private static final int GROUP_SPAN = 1000;

    private static final Map<MessageObject.GroupedMessagePosition, GroupPositionState> GROUP_POSITION_STATES =
            new WeakHashMap<>();
    private static final Map<MessageObject, CommentsPostContext> COMMENTS_POST_CONTEXTS =
            new WeakHashMap<>();
    private static final Map<MessageObject, Boolean> WIDE_PREVIEWS = new WeakHashMap<>();
    private static final Map<MessageObject, Boolean> HIDDEN_PROFILES = new WeakHashMap<>();

    public static void allowInPreview(MessageObject messageObject) {
        if (messageObject != null) {
            WIDE_PREVIEWS.put(messageObject, Boolean.TRUE);
        }
    }

    public static boolean isAllowedInPreview(MessageObject messageObject) {
        return messageObject != null && WIDE_PREVIEWS.containsKey(messageObject);
    }

    public static void setProfileHidden(MessageObject messageObject, boolean hidden) {
        if (messageObject == null) {
            return;
        }
        if (hidden) {
            HIDDEN_PROFILES.put(messageObject, Boolean.TRUE);
        } else {
            HIDDEN_PROFILES.remove(messageObject);
        }
    }

    public static void registerCommentsPostContext(
            MessageObject messageObject,
            TLRPC.Chat sourceChannel,
            MessageObject originalMessage
    ) {
        if (messageObject == null
                || messageObject.messageOwner == null
                || !ChatObject.isChannelAndNotMegaGroup(sourceChannel)) {
            return;
        }
        TLRPC.Message message = messageObject.messageOwner;
        TLRPC.MessageFwdHeader fwdFrom = message.fwd_from;
        long senderChannelId = message.from_id != null ? message.from_id.channel_id : 0;
        long destinationChannelId = message.peer_id != null ? message.peer_id.channel_id : 0;
        long savedFromChannelId = fwdFrom != null && fwdFrom.saved_from_peer != null
                ? fwdFrom.saved_from_peer.channel_id
                : 0;
        boolean sourceMatches = senderChannelId == sourceChannel.id
                && destinationChannelId != 0
                && destinationChannelId != sourceChannel.id;
        boolean rootMessage = message.reply_to == null
                && fwdFrom != null
                && (message.isThreadMessage
                || savedFromChannelId == sourceChannel.id
                || fwdFrom.channel_post != 0
                || message.replies != null);
        if (!sourceMatches || !rootMessage) {
            return;
        }

        String postAuthor = null;
        if (originalMessage != null
                && originalMessage.messageOwner != null
                && originalMessage.getDialogId() == -sourceChannel.id) {
            postAuthor = originalMessage.messageOwner.post_author;
        }

        CommentsPostContext context = COMMENTS_POST_CONTEXTS.get(messageObject);
        if (context != null && context.channelId == sourceChannel.id) {
            context.updatePostAuthor(postAuthor);
        } else {
            COMMENTS_POST_CONTEXTS.put(
                    messageObject,
                    new CommentsPostContext(sourceChannel.id, postAuthor)
            );
        }
    }

    public static boolean isCommentsChannelPost(MessageObject messageObject) {
        return getCommentsChannelId(messageObject) != 0;
    }

    public static void copyCommentsPostContext(MessageObject from, MessageObject to) {
        if (from == null || to == null) {
            return;
        }
        CommentsPostContext context = COMMENTS_POST_CONTEXTS.get(from);
        if (context != null) {
            COMMENTS_POST_CONTEXTS.put(to, context);
        }
    }

    public static String getCommentsPostAuthor(MessageObject messageObject) {
        CommentsPostContext context = getCommentsPostContext(messageObject);
        return context != null ? context.postAuthor : null;
    }

    public static boolean isEnabledFor(boolean feed, boolean channelPosts) {
        return channelPosts && (feed
                ? ExteraConfig.getWidePostsInFeed()
                : ExteraConfig.getWidePostsInChannels());
    }

    public static boolean isEnabledFor(MessageObject messageObject) {
        if (messageObject == null || !ExteraConfig.getWidePostsInFeed() && !ExteraConfig.getWidePostsInChannels()) {
            return false;
        }
        return isEnabledFor(
                messageObject.searchType == ChatActivity.SEARCH_FEED,
                isChannelPostOrSponsored(messageObject)
        );
    }

    public static int getBubbleRight(int availableWidth) {
        return Math.max(dp(120), availableWidth - dp(9));
    }

    private static boolean shouldExpand(MessageObject messageObject) {
        return isEnabledFor(messageObject) && !messageObject.shouldDrawWithoutBackground();
    }

    public static boolean shouldDrawBackground(MessageObject messageObject) {
        return messageObject != null
                && (!messageObject.shouldDrawWithoutBackground()
                || messageObject.isRoundVideo() && messageObject.isVoiceTranscriptionOpen());
    }

    public static boolean isTextMessage(MessageObject messageObject) {
        return messageObject.type == MessageObject.TYPE_TEXT
                || messageObject.type == MessageObject.TYPE_ARTICLE
                || messageObject.type == MessageObject.TYPE_STORY_MENTION;
    }

    public static boolean isExpandableMedia(MessageObject messageObject) {
        return messageObject.type == MessageObject.TYPE_PHOTO
                || messageObject.type == MessageObject.TYPE_EXTENDED_MEDIA_PREVIEW
                || messageObject.type == MessageObject.TYPE_VIDEO
                || messageObject.type == MessageObject.TYPE_GIF;
    }

    public static int getImageHeight(int imageWidth, int imageHeight, int currentPhotoHeight, int targetWidth) {
        int targetHeight = Math.round(targetWidth * (imageHeight / (float) imageWidth));
        int telegramMaxHeight = Math.min(AndroidUtilities.getPhotoSize(), targetWidth + dp(100));
        int maxHeight = Math.max(currentPhotoHeight, telegramMaxHeight);
        return Math.max(dp(120), Math.min(targetHeight, maxHeight));
    }

    public static boolean isProfileResolved(MessageObject messageObject) {
        if (messageObject == null || !shouldEmbedProfileAvatar(messageObject)) {
            return true;
        }
        return isPeerResolved(messageObject.currentAccount, getEmbeddedProfileDialogId(messageObject))
                && isPeerResolved(messageObject.currentAccount, getFeedSignatureProfileDialogId(messageObject));
    }

    private static boolean isPeerResolved(int currentAccount, long dialogId) {
        if (dialogId > 0) {
            return MessagesController.getInstance(currentAccount).getUser(dialogId) != null;
        } else if (dialogId < 0) {
            return MessagesController.getInstance(currentAccount).getChat(-dialogId) != null;
        }
        return true;
    }

    public static boolean shouldEmbedProfileAvatar(MessageObject messageObject) {
        return shouldEmbedFeedChannelProfileAvatar(messageObject)
                || shouldEmbedCommentsChannelProfileAvatar(messageObject)
                || shouldEmbedAuthorProfileAvatar(messageObject);
    }

    public static boolean shouldSuppressExternalAvatar(MessageObject messageObject) {
        return isEnabledFor(messageObject)
                && messageObject.searchType == ChatActivity.SEARCH_FEED
                && messageObject.isSponsored();
    }

    private static boolean shouldEmbedFeedChannelProfileAvatar(MessageObject messageObject) {
        return isEmbeddedProfileAvatarMessage(messageObject)
                && FeedMessageUtils.resolveDisplayChannel(messageObject) != null;
    }

    public static boolean shouldEmbedAuthorProfileAvatar(MessageObject messageObject) {
        if (!isEmbeddedProfileAvatarMessage(messageObject)
                || messageObject.searchType == ChatActivity.SEARCH_FEED) {
            return false;
        }
        TLRPC.Chat chat = getBroadcastChannel(messageObject);
        return chat != null
                && (chat.signature_profiles || messageObject.currentEvent != null)
                && ChatObject.isChannelAndNotMegaGroup(chat);
    }

    private static boolean shouldEmbedCommentsChannelProfileAvatar(MessageObject messageObject) {
        boolean embeddedProfileMessage = isEmbeddedProfileAvatarMessage(messageObject);
        long channelId = getCommentsChannelId(messageObject);
        CommentsPostContext context = getCommentsPostContext(messageObject);
        TLRPC.Chat chat = messageObject != null && channelId != 0
                ? MessagesController.getInstance(messageObject.currentAccount).getChat(channelId)
                : null;
        boolean result = embeddedProfileMessage
                && channelId != 0
                && (context != null || ChatObject.isChannelAndNotMegaGroup(chat));
        return result;
    }

    public static long getEmbeddedProfileDialogId(MessageObject messageObject) {
        if (shouldEmbedFeedChannelProfileAvatar(messageObject)) {
            TLRPC.Chat channel = FeedMessageUtils.resolveDisplayChannel(messageObject);
            return channel != null ? -channel.id : 0;
        }
        if (shouldEmbedCommentsChannelProfileAvatar(messageObject)) {
            long channelId = getCommentsChannelId(messageObject);
            return channelId != 0 ? -channelId : 0;
        }
        if (shouldEmbedAuthorProfileAvatar(messageObject)
                && messageObject.messageOwner.from_id != null) {
            return DialogObject.getPeerDialogId(messageObject.messageOwner.from_id);
        }
        return 0;
    }

    public static long getFeedSignatureProfileDialogId(MessageObject messageObject) {
        if (!FeedMessageUtils.hasProfileSignature(messageObject)
                || messageObject.messageOwner.from_id == null) {
            return 0;
        }
        long dialogId = DialogObject.getPeerDialogId(messageObject.messageOwner.from_id);
        long channelDialogId = DialogObject.getPeerDialogId(messageObject.messageOwner.peer_id);
        return dialogId != channelDialogId ? dialogId : 0;
    }

    public static boolean shouldDrawFeedAuthorSignature(MessageObject messageObject) {
        return FeedMessageUtils.hasProfileSignature(messageObject);
    }

    public static String getFeedPostAuthor(MessageObject messageObject) {
        return FeedMessageUtils.hasProfileSignature(messageObject)
                ? messageObject.messageOwner.post_author
                : null;
    }

    public static boolean hasSameEmbeddedProfileHeader(MessageObject first, MessageObject second) {
        boolean firstFeed = shouldEmbedFeedChannelProfileAvatar(first);
        boolean secondFeed = shouldEmbedFeedChannelProfileAvatar(second);
        return firstFeed == secondFeed
                && getEmbeddedProfileDialogId(first) == getEmbeddedProfileDialogId(second);
    }

    private static boolean isEmbeddedProfileAvatarMessage(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null || HIDDEN_PROFILES.containsKey(messageObject)) {
            return false;
        }
        if (!(shouldExpand(messageObject)
                || isEnabledFor(messageObject) && isEmbeddedProfileAvatarMessageWithoutBackground(messageObject))
                || messageObject.isOutOwner()
                || messageObject.isSponsored()
                || messageObject.isExpiredStory()
                || !messageObject.needDrawAvatar()
                || (messageObject.messageOwner.fwd_from != null
                    && messageObject.messageOwner.fwd_from.psa_type != null
                    && !messageObject.messageOwner.fwd_from.psa_type.isEmpty())) {
            return false;
        }
        return getChannelId(messageObject) != 0;
    }

    public static boolean isEmbeddedProfileAvatarMessageWithoutBackground(MessageObject messageObject) {
        return messageObject != null
                && (messageObject.type == MessageObject.TYPE_ROUND_VIDEO
                || messageObject.type == MessageObject.TYPE_STICKER
                || messageObject.type == MessageObject.TYPE_ANIMATED_STICKER
                || messageObject.type == MessageObject.TYPE_EMOJIS);
    }

    public static boolean shouldHideAuxiliaryActions(MessageObject messageObject) {
        return isEnabledFor(messageObject) && !messageObject.isSponsored();
    }

    public static boolean isWideGroupedMedia(MessageObject.GroupedMessages groupedMessages) {
        return isGroupedLayout(groupedMessages) && !groupedMessages.isDocuments;
    }

    public static int getScaledGroupWidth(MessageObject.GroupedMessages groupedMessages) {
        if (groupedMessages == null || groupedMessages.posArray == null || groupedMessages.posArray.isEmpty()) {
            return 0;
        }
        GroupPositionState state = GROUP_POSITION_STATES.get(groupedMessages.posArray.get(0));
        return state != null ? state.scaledForWidth : 0;
    }

    public static int getDefaultGroupWidth() {
        int width = AndroidUtilities.displaySize.x;
        if (AndroidUtilities.isTablet() && AndroidUtilities.isTabletTwoPane()) {
            return width - AndroidUtilities.getTabletLeftFragmentSize(width, 0, 0);
        }
        return width;
    }

    public static void updateGroupedLayout(MessageObject.GroupedMessages groupedMessages, int groupWidth) {
        if (!isWideGroupedMedia(groupedMessages) || groupWidth <= 0) {
            restoreGroupedLayout(groupedMessages);
            return;
        }
        MessageObject messageObject = getPrimaryGroupMessage(groupedMessages);
        if (!shouldExpand(messageObject)) {
            restoreGroupedLayout(groupedMessages);
            return;
        }
        boolean suppressExternalAvatar = shouldEmbedProfileAvatar(messageObject)
                || shouldSuppressExternalAvatar(messageObject);

        for (MessageObject.GroupedMessagePosition position : groupedMessages.posArray) {
            if (position == null) {
                continue;
            }
            GroupPositionState state = GROUP_POSITION_STATES.get(position);
            if (state == null) {
                state = new GroupPositionState(position);
                GROUP_POSITION_STATES.put(position, state);
            }
            state.restore(position);
            if (suppressExternalAvatar) {
                removeGroupAvatarOffset(position);
            }
        }

        int baseVisualWidth = getBaseGroupVisualWidth(groupedMessages);
        if (baseVisualWidth <= 0) {
            restoreGroupedLayout(groupedMessages);
            return;
        }
        int targetRight = Math.max(dp(120), groupWidth - dp(9));
        int targetVisualWidth = Utilities.clamp(
                Math.round(targetRight * GROUP_SPAN / (float) groupWidth),
                GROUP_SPAN,
                1
        );
        float scale = targetVisualWidth / (float) baseVisualWidth;

        for (MessageObject.GroupedMessagePosition position : groupedMessages.posArray) {
            if (position == null) {
                continue;
            }
            GroupPositionState state = GROUP_POSITION_STATES.get(position);
            if (state == null) {
                continue;
            }
            int baseWidth = position.pw;
            boolean hasLeftSpanOffset = position.leftSpanOffset != 0;
            position.pw = Math.max(1, Math.round(baseWidth * scale));
            position.leftSpanOffset = !hasLeftSpanOffset
                    ? 0
                    : Math.max(0, targetVisualWidth - position.pw);
        }

        normalizeGroupRows(groupedMessages, targetVisualWidth, suppressExternalAvatar);
        int trailingSpan = GROUP_SPAN - targetVisualWidth;
        boolean changed = false;
        for (MessageObject.GroupedMessagePosition position : groupedMessages.posArray) {
            if (position == null) {
                continue;
            }
            GroupPositionState state = GROUP_POSITION_STATES.get(position);
            if (state == null) {
                continue;
            }
            boolean keepFullSpan = suppressExternalAvatar
                    ? position.spanSize == GROUP_SPAN && position.leftSpanOffset > 0
                    : state.spanSize == GROUP_SPAN && state.leftSpanOffset > 0;
            if (keepFullSpan) {
                position.spanSize = GROUP_SPAN;
            } else {
                position.spanSize = position.pw;
                if ((position.flags & MessageObject.POSITION_FLAG_RIGHT) != 0) {
                    position.spanSize += trailingSpan;
                }
            }
            state.scaledForWidth = groupWidth;
            changed |= state.apply(position);
        }

        if (changed) {
            groupedMessages.cachedWidthForCaption = -1;
        }
    }

    private static boolean isChannelPostOrSponsored(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return false;
        }
        if (messageObject.preview && !WIDE_PREVIEWS.containsKey(messageObject) || messageObject.type == MessageObject.TYPE_JOINED_CHANNEL) {
            return false;
        }
        if (messageObject.isSponsored()) {
            return true;
        }
        long commentsChannelId = getCommentsChannelId(messageObject);
        if (commentsChannelId == 0 && messageObject.messageOwner.peer_id == null) {
            return false;
        }
        long channelId = commentsChannelId != 0 ? commentsChannelId : getChannelId(messageObject);
        if (channelId == 0) {
            return false;
        }

        TLRPC.Chat chat = MessagesController.getInstance(messageObject.currentAccount).getChat(channelId);
        if (chat != null) {
            return ChatObject.isChannelAndNotMegaGroup(chat);
        }
        return messageObject.messageOwner.post || commentsChannelId != 0;
    }

    private static long getChannelId(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return 0;
        }
        long channelId = getCommentsChannelId(messageObject);
        if (channelId != 0) {
            return channelId;
        }
        channelId = messageObject.messageOwner.peer_id != null
                ? messageObject.messageOwner.peer_id.channel_id
                : 0;
        if (channelId == 0) {
            long dialogId = messageObject.getDialogId();
            channelId = dialogId < 0 ? -dialogId : 0;
        }
        return channelId;
    }

    private static long getCommentsChannelId(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return 0;
        }
        CommentsPostContext context = getCommentsPostContext(messageObject);
        return context != null ? context.channelId : 0;
    }

    private static CommentsPostContext getCommentsPostContext(MessageObject messageObject) {
        if (messageObject == null) {
            return null;
        }
        return COMMENTS_POST_CONTEXTS.get(messageObject);
    }

    private static TLRPC.Chat getBroadcastChannel(MessageObject messageObject) {
        long channelId = getChannelId(messageObject);
        return channelId != 0
                ? MessagesController.getInstance(messageObject.currentAccount).getChat(channelId)
                : null;
    }

    private static boolean isGroupedLayout(MessageObject.GroupedMessages groupedMessages) {
        return groupedMessages != null
                && (groupedMessages.posArray != null && groupedMessages.posArray.size() > 1
                || groupedMessages.messages != null && groupedMessages.messages.size() > 1);
    }

    private static MessageObject getPrimaryGroupMessage(MessageObject.GroupedMessages groupedMessages) {
        if (groupedMessages == null) {
            return null;
        }
        int flags = groupedMessages.reversed
                ? MessageObject.POSITION_FLAG_BOTTOM | MessageObject.POSITION_FLAG_RIGHT
                : MessageObject.POSITION_FLAG_TOP | MessageObject.POSITION_FLAG_LEFT;
        if (groupedMessages.messages != null) {
            for (MessageObject messageObject : groupedMessages.messages) {
                MessageObject.GroupedMessagePosition position = groupedMessages.getPosition(messageObject);
                if (position != null && (position.flags & flags) == flags) {
                    return messageObject;
                }
            }
        }
        return groupedMessages.messages != null && !groupedMessages.messages.isEmpty()
                ? groupedMessages.messages.get(0)
                : null;
    }

    private static int getBaseGroupVisualWidth(MessageObject.GroupedMessages groupedMessages) {
        int maxRow = 0;
        for (MessageObject.GroupedMessagePosition position : groupedMessages.posArray) {
            if (position != null) {
                maxRow = Math.max(maxRow, position.maxY);
            }
        }

        int baseWidth = 0;
        for (int row = 0; row <= maxRow; row++) {
            int rowWidth = 0;
            for (MessageObject.GroupedMessagePosition position : groupedMessages.posArray) {
                if (position == null || position.minY > row || position.maxY < row) {
                    continue;
                }
                rowWidth += position.pw;
            }
            baseWidth = Math.max(baseWidth, rowWidth);
        }
        return Math.min(GROUP_SPAN, baseWidth);
    }

    private static void removeGroupAvatarOffset(MessageObject.GroupedMessagePosition position) {
        if (position.edge) {
            position.pw = Math.max(1, position.pw - MessageObject.GroupedMessages.GROUPED_AVATAR_OFFSET);
            if (position.spanSize != GROUP_SPAN) {
                position.spanSize = Math.max(1, position.spanSize - MessageObject.GroupedMessages.GROUPED_AVATAR_OFFSET);
            }
        } else if ((position.flags & MessageObject.POSITION_FLAG_RIGHT) != 0) {
            if (position.spanSize != GROUP_SPAN) {
                position.spanSize += MessageObject.GroupedMessages.GROUPED_AVATAR_OFFSET;
            } else if (position.leftSpanOffset != 0) {
                position.leftSpanOffset = Math.max(0, position.leftSpanOffset - MessageObject.GroupedMessages.GROUPED_AVATAR_OFFSET);
            }
        }
    }

    private static void normalizeGroupRows(
            MessageObject.GroupedMessages groupedMessages,
            int targetVisualWidth,
            boolean suppressExternalAvatar
    ) {
        int maxRow = 0;
        for (MessageObject.GroupedMessagePosition position : groupedMessages.posArray) {
            if (position != null) {
                maxRow = Math.max(maxRow, position.maxY);
            }
        }

        for (int row = 0; row <= maxRow; row++) {
            int rowWidth = 0;
            MessageObject.GroupedMessagePosition rightmost = null;
            for (MessageObject.GroupedMessagePosition position : groupedMessages.posArray) {
                if (position == null || position.minY > row || position.maxY < row) {
                    continue;
                }
                rowWidth += position.pw;
                boolean rightEdge = (position.flags & MessageObject.POSITION_FLAG_RIGHT) != 0;
                boolean currentRightEdge = rightmost != null
                        && (rightmost.flags & MessageObject.POSITION_FLAG_RIGHT) != 0;
                if (rightmost == null
                        || rightEdge && !currentRightEdge
                        || rightEdge == currentRightEdge && position.maxX > rightmost.maxX) {
                    rightmost = position;
                }
            }
            if (rightmost == null) {
                continue;
            }
            rightmost.pw = Math.max(1, rightmost.pw + targetVisualWidth - rowWidth);
            GroupPositionState state = GROUP_POSITION_STATES.get(rightmost);
            boolean hasLeftSpanOffset = suppressExternalAvatar
                    ? rightmost.leftSpanOffset > 0
                    : state != null && state.leftSpanOffset > 0;
            if (hasLeftSpanOffset) {
                rightmost.leftSpanOffset = Math.max(0, targetVisualWidth - rightmost.pw);
            }
        }
    }

    private static void restoreGroupedLayout(MessageObject.GroupedMessages groupedMessages) {
        if (groupedMessages == null || groupedMessages.posArray == null || groupedMessages.posArray.isEmpty()) {
            return;
        }

        boolean changed = false;
        for (MessageObject.GroupedMessagePosition position : groupedMessages.posArray) {
            if (position == null) {
                continue;
            }
            GroupPositionState state = GROUP_POSITION_STATES.remove(position);
            if (state != null) {
                state.restore(position);
                changed = true;
            }
        }
        if (changed) {
            groupedMessages.cachedWidthForCaption = -1;
        }
    }

    public static final class CommentsPostAuthorLoader {
        private final int currentAccount;
        private final TLRPC.Chat channel;
        private final ArrayList<MessageObject> messages = new ArrayList<>();
        private ChatActivity chatActivity;
        private MessageObject originalMessage;

        public CommentsPostAuthorLoader(int currentAccount, TLRPC.Chat channel) {
            this.currentAccount = currentAccount;
            this.channel = channel;
        }

        public MessageObject getOriginalMessage() {
            return originalMessage;
        }

        public int load(int messageId, BooleanSupplier cancelled) {
            TLRPC.TL_channels_getMessages req = new TLRPC.TL_channels_getMessages();
            req.channel = MessagesController.getInputChannel(channel);
            req.id.add(messageId);
            return ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) ->
                    AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).doOnIdle(() -> {
                        if (cancelled != null && cancelled.getAsBoolean()) {
                            return;
                        }
                        if (response instanceof TLRPC.messages_Messages) {
                            TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                            MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                            MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                            for (int a = 0, N = res.messages.size(); a < N; a++) {
                                TLRPC.Message message = res.messages.get(a);
                                if (message != null && message.id == messageId) {
                                    originalMessage = new MessageObject(currentAccount, message, true, true);
                                    break;
                                }
                            }
                        }
                        apply();
                    })));
        }

        public void setMessages(ArrayList<MessageObject> messages, ChatActivity chatActivity) {
            this.messages.clear();
            if (messages != null) {
                this.messages.addAll(messages);
            }
            this.chatActivity = chatActivity;
            apply();
        }

        private void apply() {
            if (messages.isEmpty()) {
                return;
            }
            for (int a = 0, N = messages.size(); a < N; a++) {
                MessageObject messageObject = messages.get(a);
                registerCommentsPostContext(messageObject, channel, originalMessage);
                messageObject.forceUpdate = true;
            }
            if (originalMessage != null && chatActivity != null) {
                chatActivity.updateWidePostsCommentsAuthor(messages);
            }
        }
    }

    private static final class CommentsPostContext {
        final long channelId;
        String postAuthor;

        CommentsPostContext(long channelId, String postAuthor) {
            this.channelId = channelId;
            this.postAuthor = postAuthor;
        }

        void updatePostAuthor(String postAuthor) {
            if (postAuthor != null) {
                this.postAuthor = postAuthor;
            }
        }
    }

    private static final class GroupPositionState {
        final int width;
        final int spanSize;
        final int leftSpanOffset;

        private int appliedWidth;
        private int appliedSpanSize;
        private int appliedLeftSpanOffset;
        private int scaledForWidth;

        GroupPositionState(MessageObject.GroupedMessagePosition position) {
            width = appliedWidth = position.pw;
            spanSize = appliedSpanSize = position.spanSize;
            leftSpanOffset = appliedLeftSpanOffset = position.leftSpanOffset;
        }

        boolean apply(MessageObject.GroupedMessagePosition position) {
            boolean changed = appliedWidth != position.pw
                    || appliedSpanSize != position.spanSize
                    || appliedLeftSpanOffset != position.leftSpanOffset;
            appliedWidth = position.pw;
            appliedSpanSize = position.spanSize;
            appliedLeftSpanOffset = position.leftSpanOffset;
            return changed;
        }

        void restore(MessageObject.GroupedMessagePosition position) {
            position.pw = width;
            position.spanSize = spanSize;
            position.leftSpanOffset = leftSpanOffset;
        }
    }

    private WidePosts() {
    }
}