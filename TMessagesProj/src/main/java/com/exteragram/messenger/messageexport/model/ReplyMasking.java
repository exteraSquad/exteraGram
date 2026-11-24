/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.messageexport.model;

import android.text.TextUtils;
import android.util.SparseArray;

import com.exteragram.messenger.feed.FeedMessageUtils;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.IdentityHashMap;

final class ReplyMasking {

    private final IdentityHashMap<MessageObject, MessageObject> originalReplies = new IdentityHashMap<>();
    private final IdentityHashMap<MessageObject, TLRPC.MessageReplyHeader> originalHeaders = new IdentityHashMap<>();
    private static final int HIDDEN_TEXT = 1;
    private static final int HIDDEN_BOTH = 2;
    private static final int HIDDEN_AUTHOR = 3;

    private final IdentityHashMap<MessageObject, Integer> hiddenReplies = new IdentityHashMap<>();
    private final IdentityHashMap<MessageObject, Boolean> mediaOverrides = new IdentityHashMap<>();

    boolean hasReply(MessageObject message) {
        TLRPC.MessageReplyHeader header = getOriginalHeader(message);
        return header != null && (getOriginalReply(message) != null || header.quote_text != null);
    }

    void toggle(MessageObject message, ArrayList<MessageObject> messages) {
        Integer level = hiddenReplies.get(message);
        if (!hasMaskableText(message, messages)) {
            if (level == null) {
                hiddenReplies.put(message, HIDDEN_AUTHOR);
            } else {
                hiddenReplies.remove(message);
            }
            return;
        }
        if (level == null) {
            hiddenReplies.put(message, HIDDEN_TEXT);
        } else if (level < HIDDEN_AUTHOR) {
            hiddenReplies.put(message, level + 1);
        } else {
            hiddenReplies.remove(message);
        }
    }

    void toggleMedia(MessageObject message) {
        MessageObject reply = message.replyMessageObject;
        boolean current = reply != null && reply.messageOwner != null && reply.messageOwner.media != null && reply.messageOwner.media.spoiler;
        mediaOverrides.put(message, !current);
    }

    boolean isAuthorHidden(MessageObject message) {
        Integer level = hiddenReplies.get(message);
        return level != null && (level == HIDDEN_BOTH || level == HIDDEN_AUTHOR);
    }

    void sync(ArrayList<MessageObject> messages, HiddenText hiddenText, int account) {
        SparseArray<MessageObject> byId = indexById(messages);
        for (int i = 0; i < messages.size(); i++) {
            MessageObject message = messages.get(i);
            TLRPC.MessageReplyHeader header = getOriginalHeader(message);
            if (header == null) {
                continue;
            }
            MessageObject target = findLocalTarget(message, header, byId);
            if (target != null) {
                mediaOverrides.remove(message);
            }
            Integer level = hiddenReplies.get(message);
            boolean full = level != null && (level == HIDDEN_TEXT || level == HIDDEN_BOTH);
            if (header.quote_text != null) {
                syncQuote(message, header, target, full, hiddenText);
            } else {
                syncReply(message, target, full, hiddenText, account);
            }
            message.forceUpdate = true;
        }
    }

    private void syncQuote(MessageObject message, TLRPC.MessageReplyHeader header, MessageObject target, boolean full, HiddenText hiddenText) {
        ArrayList<TLRPC.MessageEntity> spoilers = new ArrayList<>();
        int length = header.quote_text.length();
        if (full) {
            spoilers.add(spoiler(0, length));
        } else if (target != null && target.messageOwner != null && target.messageOwner.message != null) {
            int quoteStart = (header.flags & 1024) != 0 ? header.quote_offset : target.messageOwner.message.indexOf(header.quote_text);
            if (quoteStart >= 0) {
                ArrayList<int[]> ranges = hiddenText.getPlainRanges(target, hasCaption(target));
                for (int i = 0; i < ranges.size(); i++) {
                    int from = Math.max(ranges.get(i)[0], quoteStart) - quoteStart;
                    int to = Math.min(ranges.get(i)[1], quoteStart + length) - quoteStart;
                    if (from < to) {
                        spoilers.add(spoiler(from, to - from));
                    }
                }
            }
        }
        if (spoilers.isEmpty()) {
            message.messageOwner.reply_to = header;
            originalHeaders.remove(message);
            return;
        }
        TLRPC.MessageReplyHeader copy = TLObject.deepCopy(header, TLRPC.MessageReplyHeader::TLdeserialize);
        if (copy == null) {
            return;
        }
        copy.quote_entities = new ArrayList<>(header.quote_entities);
        copy.quote_entities.addAll(spoilers);
        originalHeaders.put(message, header);
        message.messageOwner.reply_to = copy;
    }

    private boolean hasMaskableText(MessageObject message, ArrayList<MessageObject> messages) {
        TLRPC.MessageReplyHeader header = getOriginalHeader(message);
        if (header == null) {
            return false;
        }
        if (!TextUtils.isEmpty(header.quote_text)) {
            return true;
        }
        MessageObject source = findLocalTarget(message, header, indexById(messages));
        if (source == null) {
            source = getOriginalReply(message);
        }
        return source != null && source.messageOwner != null && !TextUtils.isEmpty(source.messageOwner.message);
    }

    MessageObject findMediaTarget(MessageObject message, ArrayList<MessageObject> messages) {
        TLRPC.MessageReplyHeader header = getOriginalHeader(message);
        if (header == null) {
            return null;
        }
        MessageObject target = findLocalTarget(message, header, indexById(messages));
        return target != null && target.messageOwner != null && hasSpoilerMedia(target.messageOwner.media) ? target : null;
    }

    private static SparseArray<MessageObject> indexById(ArrayList<MessageObject> messages) {
        SparseArray<MessageObject> byId = new SparseArray<>();
        for (int i = 0; i < messages.size(); i++) {
            byId.put(messages.get(i).getId(), messages.get(i));
        }
        return byId;
    }

    private static MessageObject findLocalTarget(MessageObject message, TLRPC.MessageReplyHeader header, SparseArray<MessageObject> byId) {
        boolean local = header.reply_to_peer_id == null || MessageObject.getPeerId(header.reply_to_peer_id) == message.getDialogId();
        return local ? byId.get(header.reply_to_msg_id) : null;
    }

    private void syncReply(MessageObject message, MessageObject target, boolean full, HiddenText hiddenText, int account) {
        MessageObject originalReply = getOriginalReply(message);
        MessageObject source = target != null ? target : originalReply;
        ArrayList<TLRPC.MessageEntity> spoilers = new ArrayList<>();
        boolean mediaSpoiler = false;
        boolean mediaChanged = false;
        if (source != null && source.messageOwner != null && hasSpoilerMedia(source.messageOwner.media)) {
            Boolean override = mediaOverrides.get(message);
            if (override != null) {
                mediaSpoiler = override;
            } else if (target != null && target.messageOwner != null && hasSpoilerMedia(target.messageOwner.media)) {
                mediaSpoiler = target.messageOwner.media.spoiler;
            } else {
                mediaSpoiler = source.messageOwner.media.spoiler;
            }
            boolean originalSpoiler = originalReply != null && originalReply.messageOwner != null && originalReply.messageOwner.media != null && originalReply.messageOwner.media.spoiler;
            mediaChanged = mediaSpoiler != originalSpoiler;
        }
        if (source != null && source.messageOwner != null && !TextUtils.isEmpty(source.messageOwner.message)) {
            if (full) {
                spoilers.add(spoiler(0, source.messageOwner.message.length()));
            } else if (target != null) {
                ArrayList<int[]> ranges = hiddenText.getPlainRanges(target, hasCaption(target));
                for (int i = 0; i < ranges.size(); i++) {
                    spoilers.add(spoiler(ranges.get(i)[0], ranges.get(i)[1] - ranges.get(i)[0]));
                }
            }
        }
        if (spoilers.isEmpty() && !mediaChanged) {
            message.replyMessageObject = originalReply;
            originalReplies.remove(message);
            return;
        }
        TLRPC.TL_message owner = FeedMessageUtils.copyMessage(source.messageOwner);
        owner.entities = new ArrayList<>(source.messageOwner.entities);
        owner.entities.addAll(spoilers);
        if (mediaChanged) {
            TLRPC.MessageMedia media = TLObject.deepCopy(source.messageOwner.media, TLRPC.MessageMedia::TLdeserialize);
            if (media != null) {
                media.spoiler = mediaSpoiler;
                owner.media = media;
            }
        }
        originalReplies.put(message, originalReply);
        message.replyMessageObject = new MessageObject(account, owner, false, false);
    }

    private TLRPC.MessageReplyHeader getOriginalHeader(MessageObject message) {
        TLRPC.MessageReplyHeader header = originalHeaders.get(message);
        return header != null ? header : message.messageOwner != null ? message.messageOwner.reply_to : null;
    }

    private MessageObject getOriginalReply(MessageObject message) {
        return originalReplies.containsKey(message) ? originalReplies.get(message) : message.replyMessageObject;
    }

    private static boolean hasSpoilerMedia(TLRPC.MessageMedia media) {
        return media instanceof TLRPC.TL_messageMediaPhoto || media instanceof TLRPC.TL_messageMediaDocument;
    }

    private static boolean hasCaption(MessageObject message) {
        return !TextUtils.isEmpty(message.caption);
    }

    private static TLRPC.MessageEntity spoiler(int offset, int length) {
        TLRPC.TL_messageEntitySpoiler entity = new TLRPC.TL_messageEntitySpoiler();
        entity.offset = offset;
        entity.length = length;
        return entity;
    }
}
