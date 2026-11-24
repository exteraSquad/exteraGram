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

import androidx.collection.LongSparseArray;

import com.exteragram.messenger.feed.FeedMessageUtils;
import com.exteragram.messenger.utils.chats.WidePosts;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;

public final class MessageExportDocument {

    public static final int BLOCK_MESSAGES = 0;
    public static final int BLOCK_DATE = 1;
    public static final int BLOCK_ACTION = 2;

    private static final class Entry {
        final MessageObject message;
        final boolean adjacentToPrevious;
        boolean excluded;

        Entry(MessageObject message, boolean adjacentToPrevious) {
            this.message = message;
            this.adjacentToPrevious = adjacentToPrevious;
        }
    }

    public static final class Block {
        public final int type;
        public final ArrayList<MessageObject> messages = new ArrayList<>();
        public MessageObject.GroupedMessages group;
        public boolean pinnedTop;
        public boolean pinnedBottom;

        Block(int type) {
            this.type = type;
        }

        MessageObject first() {
            return messages.get(0);
        }

        MessageObject last() {
            return messages.get(messages.size() - 1);
        }
    }

    public final MessageExportChatContext context;
    public final ArrayList<Block> blocks = new ArrayList<>();

    private final ArrayList<Entry> entries;
    private final IdentityHashMap<MessageObject, Entry> entryByMessage = new IdentityHashMap<>();
    private final SparseArray<MessageObject> dateObjects = new SparseArray<>();
    private final HashSet<Integer> hiddenDays = new HashSet<>();
    private final LongSparseArray<MessageObject.GroupedMessages> groups = new LongSparseArray<>();
    private final ArrayList<MessageObject.GroupedMessages> changedGroups = new ArrayList<>();
    private int revision;
    private int parentWidth;

    private MessageExportDocument(MessageExportChatContext context, ArrayList<Entry> entries) {
        this.context = context;
        this.entries = entries;
        for (int i = 0; i < entries.size(); i++) {
            entryByMessage.put(entries.get(i).message, entries.get(i));
        }
        rebuild();
    }

    public static MessageExportDocument create(MessageExportChatContext context, SparseArray<MessageObject>[] selected, List<MessageObject> chatMessages) {
        IdentityHashMap<MessageObject, Integer> chatPositions = new IdentityHashMap<>();
        if (chatMessages != null) {
            int position = 0;
            for (int i = chatMessages.size() - 1; i >= 0; i--) {
                MessageObject message = chatMessages.get(i);
                if (message == null || message.isDateObject || message.messageOwner == null) {
                    continue;
                }
                chatPositions.put(message, position++);
            }
        }
        ArrayList<MessageObject> sources = new ArrayList<>();
        for (int a = selected.length - 1; a >= 0; a--) {
            ArrayList<MessageObject> part = new ArrayList<>(selected[a].size());
            for (int i = 0; i < selected[a].size(); i++) {
                part.add(selected[a].valueAt(i));
            }
            part.sort(Comparator.comparingInt(MessageObject::getId));
            sources.addAll(part);
        }
        ArrayList<Entry> entries = new ArrayList<>(sources.size());
        for (int i = 0; i < sources.size(); i++) {
            MessageObject source = sources.get(i);
            boolean adjacent = false;
            if (i > 0) {
                Integer prev = chatPositions.get(sources.get(i - 1));
                Integer current = chatPositions.get(source);
                adjacent = prev != null && current != null && current - prev == 1;
            }
            entries.add(new Entry(snapshot(context.account, source), adjacent));
        }
        return new MessageExportDocument(context, entries);
    }

    public int getRevision() {
        return revision;
    }

    public int getIncludedCount() {
        int count = 0;
        for (int i = 0; i < entries.size(); i++) {
            if (!entries.get(i).excluded) {
                count++;
            }
        }
        return count;
    }

    public void setParentWidth(int width) {
        parentWidth = width;
        for (int i = 0; i < entries.size(); i++) {
            applyParentWidth(entries.get(i).message);
        }
        revision++;
    }

    private void applyParentWidth(MessageObject message) {
        if (parentWidth <= 0) {
            return;
        }
        int width = parentWidth + MessageExportChatContext.getWidthCompensation(message);
        if (message.parentWidth != width) {
            message.parentWidth = width;
            message.resetLayout();
            message.forceUpdate = true;
        }
    }

    void setExcluded(MessageObject message, boolean excluded) {
        Entry entry = entryByMessage.get(message);
        if (entry != null && entry.excluded != excluded) {
            entry.excluded = excluded;
            entry.message.deleted = excluded;
            if (!excluded) {
                entry.message.deletedByThanos = false;
            }
            revision++;
        }
    }

    void touch() {
        revision++;
    }

    public void preloadPhotos() {
        FileLoader loader = FileLoader.getInstance(context.account);
        for (int i = 0; i < entries.size(); i++) {
            MessageObject message = entries.get(i).message;
            if (message.type != MessageObject.TYPE_PHOTO || message.mediaExists || message.needDrawBluredPreview()) {
                continue;
            }
            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, AndroidUtilities.getPhotoSize());
            if (size == null || size instanceof TLRPC.TL_photoStrippedSize || size.location == null) {
                continue;
            }
            if (!loader.getPathToAttach(size, true).exists()) {
                loader.loadFile(ImageLocation.getForObject(size, message.photoThumbsObject), message, null, FileLoader.PRIORITY_HIGH, message.shouldEncryptPhotoOrVideo() ? 2 : 0);
            }
        }
    }

    ArrayList<MessageObject> getMessages() {
        ArrayList<MessageObject> messages = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            messages.add(entries.get(i).message);
        }
        return messages;
    }

    void invalidateAll() {
        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).message.forceUpdate = true;
        }
        revision++;
    }

    void setDateHidden(MessageObject dateObject, boolean hidden) {
        int day = dateObject.messageOwner.date;
        if (hidden ? hiddenDays.add(day) : hiddenDays.remove(day)) {
            revision++;
        }
    }

    public ArrayList<MessageObject.GroupedMessages> consumeChangedGroups() {
        ArrayList<MessageObject.GroupedMessages> result = new ArrayList<>(changedGroups);
        changedGroups.clear();
        return result;
    }

    void rebuild() {
        blocks.clear();
        LongSparseArray<Integer> previousSizes = new LongSparseArray<>();
        Block current = null;
        MessageObject previous = null;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.excluded) {
                continue;
            }
            MessageObject message = entry.message;
            long groupId = message.getGroupId();
            boolean continuesGroup = current != null && groupId != 0 && current.group != null && current.group.groupId == groupId;
            if (previous == null || startOfDay(previous.messageOwner.date) != startOfDay(message.messageOwner.date)) {
                if (!hiddenDays.contains(startOfDay(message.messageOwner.date))) {
                    Block date = new Block(BLOCK_DATE);
                    date.messages.add(getDateObject(message));
                    blocks.add(date);
                }
                current = null;
            } else if (!continuesGroup && (!entry.adjacentToPrevious || entries.get(i - 1).excluded)) {
                current = null;
            }
            previous = message;
            if (isAction(message)) {
                Block action = new Block(BLOCK_ACTION);
                action.messages.add(message);
                blocks.add(action);
                current = null;
                continue;
            }
            context.applyMessage(message);
            applyParentWidth(message);
            if (current != null && continuesGroup) {
                current.messages.add(message);
                current.group.messages.add(message);
                continue;
            }
            current = new Block(BLOCK_MESSAGES);
            current.messages.add(message);
            if (groupId != 0) {
                current.group = obtainGroup(groupId, previousSizes);
                current.group.messages.add(message);
            }
            blocks.add(current);
        }
        for (int i = 0; i < groups.size(); i++) {
            MessageObject.GroupedMessages group = groups.valueAt(i);
            int index = previousSizes.indexOfKey(group.groupId);
            int previousSize = index >= 0 ? previousSizes.valueAt(index) : group.messages.size();
            if (index < 0) {
                group.messages.clear();
            }
            if (previousSize != group.messages.size() && !changedGroups.contains(group)) {
                changedGroups.add(group);
            }
        }
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            if (block.type != BLOCK_MESSAGES) {
                continue;
            }
            if (block.group != null) {
                if (block.messages.size() > 1) {
                    block.group.calculate();
                } else {
                    block.group = null;
                }
            }
            Block prev = i > 0 ? blocks.get(i - 1) : null;
            Block next = i < blocks.size() - 1 ? blocks.get(i + 1) : null;
            block.pinnedTop = prev != null && prev.type == BLOCK_MESSAGES && canJoin(prev.last(), block.first());
            block.pinnedBottom = next != null && next.type == BLOCK_MESSAGES && canJoin(block.last(), next.first());
        }
    }

    private MessageObject.GroupedMessages obtainGroup(long groupId, LongSparseArray<Integer> previousSizes) {
        MessageObject.GroupedMessages group = groups.get(groupId);
        if (group == null) {
            group = new MessageObject.GroupedMessages();
            group.groupId = groupId;
            groups.put(groupId, group);
        }
        if (previousSizes.indexOfKey(groupId) < 0) {
            previousSizes.put(groupId, group.messages.size());
            group.messages.clear();
        }
        return group;
    }

    private MessageObject getDateObject(MessageObject base) {
        int day = startOfDay(base.messageOwner.date);
        MessageObject object = dateObjects.get(day);
        if (object != null) {
            return object;
        }
        TLRPC.Message owner = new TLRPC.TL_message();
        owner.message = LocaleController.formatDateChat(base.messageOwner.date);
        owner.id = 0;
        owner.date = day;
        object = new MessageObject(context.account, owner, false, false);
        object.type = 10;
        object.contentType = 1;
        object.isDateObject = true;
        object.stableId = ChatActivity.lastStableId++;
        dateObjects.put(day, object);
        return object;
    }

    private static int startOfDay(int date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(date * 1000L);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return (int) (calendar.getTimeInMillis() / 1000);
    }

    public static boolean isAction(MessageObject message) {
        return message.contentType == 1 && !message.isDateObject;
    }

    private static MessageObject snapshot(int account, MessageObject source) {
        if (source.messageOwner instanceof TLRPC.TL_messageService) {
            TLRPC.Message service = TLObject.deepCopy(source.messageOwner, TLRPC.Message::TLdeserialize);
            if (service == null) {
                service = source.messageOwner;
            }
            MessageObject copy = new MessageObject(account, service, source.replyMessageObject, null, null, null, null, true, true, 0, false, false, false);
            copy.copyStableParams(source);
            if (copy.stableId == 0) {
                copy.stableId = ChatActivity.lastStableId++;
            }
            copy.preview = true;
            return copy;
        }
        TLRPC.TL_message owner = FeedMessageUtils.copyMessage(source.messageOwner);
        owner.entities = new ArrayList<>(source.messageOwner.entities);
        owner.voiceTranscriptionOpen = false;
        if (owner.rich_message != null) {
            TL_iv.RichMessage richMessage = TLObject.deepCopy(owner.rich_message, TL_iv.RichMessage::TLdeserialize);
            if (richMessage != null) {
                owner.rich_message = richMessage;
            }
        }
        MessageObject copy = new MessageObject(account, owner, source.replyMessageObject, null, null, null, null, true, true, 0, false, false, false);
        FeedMessageUtils.copyTranslationState(source, copy);
        owner.translatedText = copyText(owner.translatedText);
        owner.summaryText = copyText(owner.summaryText);
        owner.translatedSummaryText = copyText(owner.translatedSummaryText);
        copy.copyStableParams(source);
        if (copy.stableId == 0) {
            copy.stableId = ChatActivity.lastStableId++;
        }
        copy.isSpoilersRevealed = false;
        copy.expandedQuotes = source.expandedQuotes != null ? new HashSet<>(source.expandedQuotes) : null;
        copy.updateTranslation(false);
        copy.preview = true;
        WidePosts.allowInPreview(copy);
        return copy;
    }

    private static TLRPC.TL_textWithEntities copyText(TLRPC.TL_textWithEntities source) {
        if (source == null) {
            return null;
        }
        TLRPC.TL_textWithEntities copy = new TLRPC.TL_textWithEntities();
        copy.text = source.text;
        copy.entities = new ArrayList<>(source.entities);
        return copy;
    }

    private boolean canJoin(MessageObject prev, MessageObject next) {
        if (prev.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) {
            return false;
        }
        if (prev.isOutOwner() != next.isOutOwner() || Math.abs(next.messageOwner.date - prev.messageOwner.date) > 5 * 60) {
            return false;
        }
        if (next.messageOwner.paid_message_stars > 0) {
            return false;
        }
        boolean join;
        if (prev.isImportedForward() || next.isImportedForward()) {
            join = prev.isImportedForward() && next.isImportedForward() && sameImportedSender(prev, next);
        } else if (context.chat != null) {
            long fromId = next.getFromChatId();
            join = fromId == prev.getFromChatId() && !(fromId < 0 && context.chat.megagroup);
            if (join && context.forumAsMessages && topicId(prev) != topicId(next)) {
                join = false;
            }
        } else if (context.isSelfOrReplies()) {
            join = !prev.isPrivateForward() && !next.isPrivateForward() && prev.getSenderId() == next.getSenderId();
        } else {
            join = true;
        }
        if (DialogObject.getPeerDialogId(prev.messageOwner.guestchat_via_from) != DialogObject.getPeerDialogId(next.messageOwner.guestchat_via_from)) {
            join = false;
        }
        if (context.chat != null && context.chat.megagroup && (isChannelRepost(prev) || isChannelRepost(next))) {
            join = false;
        }
        boolean prevEmbed = WidePosts.shouldEmbedProfileAvatar(prev);
        boolean nextEmbed = WidePosts.shouldEmbedProfileAvatar(next);
        if (prevEmbed != nextEmbed || prevEmbed && !WidePosts.hasSameEmbeddedProfileHeader(prev, next)) {
            join = false;
        }
        return join;
    }

    private long topicId(MessageObject message) {
        return message.replyToForumTopic == null ? MessageObject.getTopicId(context.account, message.messageOwner, MessagesStorage.FORUM_TYPE_CHAT) : message.replyToForumTopic.id;
    }

    private static boolean isChannelRepost(MessageObject message) {
        return message.getFromChatId() <= 0 && message.messageOwner.fwd_from != null && message.messageOwner.fwd_from.saved_from_peer instanceof TLRPC.TL_peerChannel;
    }

    private static boolean sameImportedSender(MessageObject prev, MessageObject next) {
        TLRPC.MessageFwdHeader a = prev.messageOwner.fwd_from;
        TLRPC.MessageFwdHeader b = next.messageOwner.fwd_from;
        if (Math.abs(a.date - b.date) > 5 * 60) {
            return false;
        }
        if (a.from_name != null && b.from_name != null) {
            return a.from_name.equals(b.from_name);
        }
        if (a.from_id != null && b.from_id != null) {
            return MessageObject.getPeerId(a.from_id) == MessageObject.getPeerId(b.from_id);
        }
        return false;
    }
}
