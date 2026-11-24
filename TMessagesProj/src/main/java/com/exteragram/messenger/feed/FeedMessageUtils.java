/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.feed;

import com.exteragram.messenger.utils.chats.DoubleTapUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;

import java.util.ArrayList;
import java.util.Calendar;

public final class FeedMessageUtils {

    private FeedMessageUtils() {
    }

    public static boolean isPostRow(MessageObject row) {
        return row != null && !row.isDateObject && row.type != MessageObject.TYPE_LOADING && !row.isSponsored();
    }

    public static TLRPC.Chat resolveDisplayChannel(MessageObject messageObject) {
        if (messageObject == null
                || messageObject.messageOwner == null
                || messageObject.searchType != ChatActivity.SEARCH_FEED
                || messageObject.preview
                || messageObject.isSponsored()
                || messageObject.type == MessageObject.TYPE_JOINED_CHANNEL
                || !messageObject.messageOwner.post
                || messageObject.messageOwner.action != null
                || messageObject.messageOwner.peer_id == null
                || messageObject.messageOwner.peer_id.channel_id == 0) {
            return null;
        }
        TLRPC.Chat channel = MessagesController.getInstance(messageObject.currentAccount)
                .getChat(messageObject.messageOwner.peer_id.channel_id);
        return ChatObject.isChannelAndNotMegaGroup(channel) ? channel : null;
    }

    public static boolean hasProfileSignature(MessageObject messageObject) {
        TLRPC.Chat channel = resolveDisplayChannel(messageObject);
        return channel != null && channel.signature_profiles;
    }

    public static boolean shouldMergePosts(
            MessageObject upper,
            TLRPC.Chat upperChannel,
            MessageObject lower,
            TLRPC.Chat lowerChannel
    ) {
        return upperChannel != null
                && lowerChannel != null
                && upperChannel.id == lowerChannel.id
                && upper.contentType == lower.contentType
                && !(upper.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup)
                && upper.isOutOwner() == lower.isOutOwner()
                && Math.abs(upper.messageOwner.date - lower.messageOwner.date) <= 5 * 60
                && lower.messageOwner.paid_message_stars <= 0
                && DialogObject.getPeerDialogId(upper.messageOwner.guestchat_via_from)
                    == DialogObject.getPeerDialogId(lower.messageOwner.guestchat_via_from);
    }

    public static MessageObject createUnreadDivider(int currentAccount, int stableId) {
        TLRPC.Message dateMsg = new TLRPC.TL_message();
        dateMsg.message = "";
        dateMsg.id = 0;
        MessageObject dateObj = new MessageObject(currentAccount, dateMsg, false, false);
        dateObj.type = MessageObject.TYPE_LOADING;
        dateObj.contentType = 2;
        dateObj.stableId = stableId;
        return dateObj;
    }

    public static MessageObject createDateHeader(int currentAccount, MessageObject baseMsg, int stableId) {
        TLRPC.Message dateMsg = new TLRPC.TL_message();
        dateMsg.message = LocaleController.formatDateChat(baseMsg.messageOwner.date);
        dateMsg.id = 0;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(((long) baseMsg.messageOwner.date) * 1000);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        dateMsg.date = (int) (calendar.getTimeInMillis() / 1000);
        MessageObject dateObj = new MessageObject(currentAccount, dateMsg, false, false);
        dateObj.type = MessageObject.TYPE_DATE;
        dateObj.contentType = 1;
        dateObj.isDateObject = true;
        dateObj.stableId = stableId;
        return dateObj;
    }

    public static TLRPC.InputPeer getInputPeerForMessageRequest(MessagesController messagesController, long dialogId, boolean feedSearch, MessageObject messageObject) {
        return messagesController.getInputPeer(feedSearch && messageObject != null ? messageObject.getDialogId() : dialogId);
    }

    public static boolean matchesPlaybackNotification(int currentAccount, MessageObject messageObject, int messageId) {
        if (messageObject == null) {
            return false;
        }
        if (messageObject.getId() == messageId) {
            return true;
        }
        FeedController feedController = FeedController.peekInstance(currentAccount);
        if (feedController == null) {
            return false;
        }
        long dialogId = feedController.resolveRealDialogId(messageId);
        return dialogId != 0 && dialogId == messageObject.getDialogId() && feedController.resolveRealMessageId(dialogId, messageId) == messageObject.getFeedRealId();
    }

    public static int getPlaybackScrollMessageId(boolean feedSearch, long dialogId, MessageObject messageObject) {
        if (messageObject != null && messageObject.searchType == ChatActivity.SEARCH_FEED && !feedSearch && messageObject.getDialogId() == dialogId) {
            return messageObject.getRealId();
        }
        return messageObject != null ? messageObject.getId() : 0;
    }

    public static MessageObject getForwardingMessageObject(int currentAccount, boolean feedSearch, MessageObject messageObject) {
        if (!feedSearch || messageObject == null || messageObject.getId() == messageObject.getRealId()) {
            return messageObject;
        }
        TLRPC.Message message = copyMessage(messageObject.messageOwner);
        message.id = messageObject.getRealId();
        message.realId = 0;
        message.dialog_id = messageObject.getDialogId();
        MessageObject result = new MessageObject(currentAccount, message, messageObject.replyMessageObject, null, null, null, null, false, true, 0, false, false, false);
        result.isPrimaryGroupMessage = messageObject.isPrimaryGroupMessage;
        result.localGroupId = messageObject.localGroupId;
        result.copyStableParams(messageObject);
        return result;
    }

    public static MessageObject createReplacement(int currentAccount, long dialogId, MessageObject source) {
        if (source == null) {
            return null;
        }
        FeedController feedController = FeedController.getInstance(currentAccount);
        MessageObject old = feedController.getMessage(dialogId, source.getRealId());
        if (old == null) {
            return null;
        }
        MessageObject replacement = createReplacementFor(currentAccount, old, source);
        feedController.replaceMessage(old, replacement);
        return replacement;
    }

    static MessageObject createReplacementFor(int currentAccount, MessageObject old, MessageObject source) {
        TLRPC.Message msg = copyMessage(source.messageOwner);
        msg.id = old.getId();
        msg.realId = old.getRealId();
        msg.dialog_id = old.getDialogId();
        MessageObject replacement = new MessageObject(currentAccount, msg, old.replyMessageObject, null, null, null, null, true, true, 0, false, false, false, ChatActivity.SEARCH_FEED);
        replacement.isPrimaryGroupMessage = old.isPrimaryGroupMessage;
        replacement.localGroupId = old.localGroupId;
        replacement.copyStableParams(old);
        return replacement;
    }

    public static ArrayList<MessageObject> createReplacements(int currentAccount, long dialogId, ArrayList<MessageObject> sources) {
        ArrayList<MessageObject> replacements = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            MessageObject replacement = createReplacement(currentAccount, dialogId, sources.get(i));
            if (replacement != null) {
                replacements.add(replacement);
            }
        }
        return replacements;
    }

    public static boolean isAllowedFeedOption(int option) {
        return switch (option) {
            case ChatActivity.OPTION_REPLY, ChatActivity.OPTION_FORWARD, ChatActivity.OPTION_COPY, ChatActivity.OPTION_COPY_LINK,
                 ChatActivity.OPTION_COPY_PHONE_NUMBER, ChatActivity.OPTION_COPY_PHOTO, ChatActivity.OPTION_SAVE_MESSAGE,
                 ChatActivity.OPTION_SAVE_TO_GALLERY, ChatActivity.OPTION_SAVE_TO_GALLERY2, ChatActivity.OPTION_GENERATE,
                 ChatActivity.OPTION_SAVE_TO_DOWNLOADS_OR_MUSIC, ChatActivity.OPTION_SHARE,
                 ChatActivity.OPTION_OPEN_IN_EXTERNAL_APP, ChatActivity.OPTION_TRANSLATE,
                 ChatActivity.OPTION_STATISTICS, ChatActivity.OPTION_UNVOTE,
                 ChatActivity.OPTION_VIEW_STATISTICS -> true;
            default -> false;
        };
    }

    public static boolean isAllowedDoubleTapAction(int action) {
        return switch (action) {
            case DoubleTapUtils.ACTION_REACTIONS, DoubleTapUtils.ACTION_REPLY, DoubleTapUtils.ACTION_COPY,
                 DoubleTapUtils.ACTION_FORWARD, DoubleTapUtils.ACTION_SAVE, DoubleTapUtils.ACTION_TRANSLATE -> true;
            default -> false;
        };
    }

    public static boolean isAllowedSwipeAction(int action) {
        return action != DoubleTapUtils.ACTION_REACTIONS && isAllowedDoubleTapAction(action);
    }

    public static void filterAllowedOptions(
            ArrayList<CharSequence> items,
            ArrayList<Integer> options,
            ArrayList<Integer> icons
    ) {
        for (int i = options.size() - 1; i >= 0; i--) {
            if (!isAllowedFeedOption(options.get(i))) {
                icons.remove(i);
                items.remove(i);
                options.remove(i);
            }
        }
    }

    public static void copyFeedPostLink(ChatActivity fragment, MessageObject messageObject) {
        if (fragment == null || messageObject == null) {
            return;
        }
        TLRPC.Chat channel = fragment.getMessagesController().getChat(-messageObject.getDialogId());
        if (!ChatObject.isChannel(channel)) {
            return;
        }
        TLRPC.TL_channels_exportMessageLink req = new TLRPC.TL_channels_exportMessageLink();
        req.id = messageObject.getRealId();
        req.channel = MessagesController.getInputChannel(channel);
        fragment.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.TL_exportedMessageLink) {
                String link = ((TLRPC.TL_exportedMessageLink) response).link;
                if (AndroidUtilities.addToClipboard(link) && BulletinFactory.canShowBulletin(fragment)) {
                    BulletinFactory.of(fragment).createCopyLinkBulletin(link.contains("/c/")).show();
                }
            }
        }));
    }

    public static void copyTranslationState(MessageObject source, MessageObject target) {
        if (source == null || target == null || source == target || source.messageOwner == null || target.messageOwner == null) {
            return;
        }
        target.messageOwner.translatedText = source.messageOwner.translatedText;
        target.messageOwner.translatedToLanguage = source.messageOwner.translatedToLanguage;
        target.messageOwner.translatedVoiceTranscription = source.messageOwner.translatedVoiceTranscription;
        target.messageOwner.translatedPoll = source.messageOwner.translatedPoll;
        target.messageOwner.summaryText = source.messageOwner.summaryText;
        target.messageOwner.summarizedOpen = source.messageOwner.summarizedOpen;
        target.messageOwner.translatedSummaryText = source.messageOwner.translatedSummaryText;
        target.messageOwner.translatedSummaryLanguage = source.messageOwner.translatedSummaryLanguage;
    }

    public static TLRPC.TL_message copyMessage(TLRPC.Message source) {
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = source.id;
        message.from_id = source.from_id;
        message.from_boosts_applied = source.from_boosts_applied;
        message.peer_id = source.peer_id;
        message.saved_peer_id = source.saved_peer_id;
        message.date = source.date;
        message.expire_date = source.expire_date;
        message.action = source.action;
        message.message = source.message;
        message.media = source.media;
        message.flags = source.flags;
        message.flags2 = source.flags2;
        message.mentioned = source.mentioned;
        message.media_unread = source.media_unread;
        message.out = source.out;
        message.unread = source.unread;
        message.entities = source.entities;
        message.via_bot_name = source.via_bot_name;
        message.reply_markup = source.reply_markup;
        message.views = source.views;
        message.forwards = source.forwards;
        message.replies = source.replies;
        message.edit_date = source.edit_date;
        message.silent = source.silent;
        message.post = source.post;
        message.from_scheduled = source.from_scheduled;
        message.legacy = source.legacy;
        message.edit_hide = source.edit_hide;
        message.pinned = source.pinned;
        message.fwd_from = source.fwd_from;
        message.via_bot_id = source.via_bot_id;
        message.via_business_bot_id = source.via_business_bot_id;
        message.reply_to = source.reply_to;
        message.post_author = source.post_author;
        message.grouped_id = source.grouped_id;
        message.reactions = source.reactions;
        message.restriction_reason = source.restriction_reason;
        message.ttl_period = source.ttl_period;
        message.quick_reply_shortcut_id = source.quick_reply_shortcut_id;
        message.effect = source.effect;
        message.noforwards = source.noforwards;
        message.invert_media = source.invert_media;
        message.offline = source.offline;
        message.factcheck = source.factcheck;
        message.send_state = source.send_state;
        message.fwd_msg_id = source.fwd_msg_id;
        message.params = source.params;
        message.random_id = source.random_id;
        message.local_id = source.local_id;
        message.attachPath = source.attachPath;
        message.dialog_id = source.dialog_id;
        message.ttl = source.ttl;
        message.destroyTime = source.destroyTime;
        message.destroyTimeMillis = source.destroyTimeMillis;
        message.layer = source.layer;
        message.seq_in = source.seq_in;
        message.seq_out = source.seq_out;
        message.with_my_score = source.with_my_score;
        message.replyMessage = source.replyMessage;
        message.reqId = source.reqId;
        message.realId = source.realId;
        message.stickerVerified = source.stickerVerified;
        message.isThreadMessage = source.isThreadMessage;
        message.voiceTranscription = source.voiceTranscription;
        message.voiceTranscriptionOpen = source.voiceTranscriptionOpen;
        message.voiceTranscriptionRated = source.voiceTranscriptionRated;
        message.voiceTranscriptionFinal = source.voiceTranscriptionFinal;
        message.voiceTranscriptionForce = source.voiceTranscriptionForce;
        message.voiceTranscriptionId = source.voiceTranscriptionId;
        message.premiumEffectWasPlayed = source.premiumEffectWasPlayed;
        message.originalLanguage = source.originalLanguage;
        message.translatedToLanguage = source.translatedToLanguage;
        message.translatedText = source.translatedText;
        message.replyStory = source.replyStory;
        message.quick_reply_shortcut = source.quick_reply_shortcut;
        message.reactions_are_possible = source.reactions_are_possible;
        message.video_processing_pending = source.video_processing_pending;
        message.report_delivery_until_date = source.report_delivery_until_date;
        message.paid_message_stars = source.paid_message_stars;
        message.suggested_post = source.suggested_post;
        message.paid_suggested_post_stars = source.paid_suggested_post_stars;
        message.paid_suggested_post_ton = source.paid_suggested_post_ton;
        message.schedule_repeat_period = source.schedule_repeat_period;
        message.from_rank = source.from_rank;
        message.guestchat_via_from = source.guestchat_via_from;
        message.rich_message = source.rich_message;
        return message;
    }
}
