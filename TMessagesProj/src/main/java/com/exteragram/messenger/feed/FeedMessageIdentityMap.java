/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.feed;

import org.telegram.messenger.MessageObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

final class FeedMessageIdentityMap {

    private final HashMap<MessageCompositeID, Integer> generatedIds = new HashMap<>();
    private final ConcurrentHashMap<Integer, MessageCompositeID> realIdsByGeneratedId = new ConcurrentHashMap<>();
    private final HashMap<MessageCompositeID, MessageObject> messagesByRealId = new HashMap<>();
    private final HashMap<GroupKey, ArrayList<MessageObject>> groupMembers = new HashMap<>();
    private int lastGeneratedId = Integer.MAX_VALUE - 10;

    boolean register(MessageObject msg) {
        int realId = msg.getRealId();
        MessageCompositeID compositeId = new MessageCompositeID(msg.getDialogId(), realId);
        Integer id = generatedIds.get(compositeId);
        if (id == null) {
            id = lastGeneratedId--;
            generatedIds.put(compositeId, id);
        }
        realIdsByGeneratedId.put(id, compositeId);
        boolean added = false;
        if (!messagesByRealId.containsKey(compositeId)) {
            messagesByRealId.put(compositeId, msg);
            addToGroup(msg, compositeId.dialog_id);
            added = true;
        }
        msg.messageOwner.realId = realId;
        msg.messageOwner.id = id;
        return added;
    }

    void replace(MessageObject newMessage) {
        MessageCompositeID compositeId = new MessageCompositeID(newMessage.getDialogId(), newMessage.getRealId());
        generatedIds.put(compositeId, newMessage.getId());
        realIdsByGeneratedId.put(newMessage.getId(), compositeId);
        MessageObject old = messagesByRealId.put(compositeId, newMessage);
        if (old != null) {
            removeFromGroup(old, compositeId.dialog_id);
        }
        addToGroup(newMessage, compositeId.dialog_id);
    }

    void purge(MessageObject msg) {
        MessageCompositeID compositeId = new MessageCompositeID(msg.getDialogId(), msg.getRealId());
        generatedIds.remove(compositeId);
        messagesByRealId.remove(compositeId);
        realIdsByGeneratedId.remove(msg.getId());
        removeFromGroup(msg, compositeId.dialog_id);
    }

    MessageObject getByRealId(long dialogId, int realId) {
        return messagesByRealId.get(new MessageCompositeID(dialogId, realId));
    }

    MessageObject getByAnyId(long dialogId, int messageId) {
        MessageObject message = messagesByRealId.get(new MessageCompositeID(dialogId, messageId));
        if (message != null) {
            return message;
        }
        int realId = resolveRealMessageId(dialogId, messageId);
        return realId != messageId ? messagesByRealId.get(new MessageCompositeID(dialogId, realId)) : null;
    }

    int resolveRealMessageId(long dialogId, int messageId) {
        MessageCompositeID realId = realIdsByGeneratedId.get(messageId);
        if (realId != null && realId.dialog_id == dialogId) {
            return realId.id;
        }
        return messageId;
    }

    long resolveRealDialogId(int messageId) {
        MessageCompositeID realId = realIdsByGeneratedId.get(messageId);
        return realId != null ? realId.dialog_id : 0;
    }

    boolean isEmpty() {
        return realIdsByGeneratedId.isEmpty();
    }

    void clear() {
        generatedIds.clear();
        realIdsByGeneratedId.clear();
        messagesByRealId.clear();
        groupMembers.clear();
        lastGeneratedId = Integer.MAX_VALUE - 10;
    }

    private void addToGroup(MessageObject msg, long dialogId) {
        if (!msg.hasValidGroupId()) {
            msg.isPrimaryGroupMessage = false;
            return;
        }
        GroupKey key = new GroupKey(dialogId, msg.messageOwner.grouped_id);
        ArrayList<MessageObject> members = groupMembers.get(key);
        if (members == null) {
            members = new ArrayList<>();
            groupMembers.put(key, members);
        }
        if (!members.contains(msg)) {
            members.add(msg);
        }
        electPrimary(members);
    }

    private void removeFromGroup(MessageObject msg, long dialogId) {
        if (!msg.hasValidGroupId()) {
            return;
        }
        GroupKey key = new GroupKey(dialogId, msg.messageOwner.grouped_id);
        ArrayList<MessageObject> members = groupMembers.get(key);
        if (members == null) {
            return;
        }
        members.remove(msg);
        msg.isPrimaryGroupMessage = false;
        if (members.isEmpty()) {
            groupMembers.remove(key);
        } else {
            electPrimary(members);
        }
    }

    private static void electPrimary(ArrayList<MessageObject> members) {
        MessageObject primary = null;
        for (int i = 0; i < members.size(); i++) {
            MessageObject member = members.get(i);
            if (primary == null || member.getRealId() > primary.getRealId()) {
                primary = member;
            }
        }
        for (int i = 0; i < members.size(); i++) {
            members.get(i).isPrimaryGroupMessage = members.get(i) == primary;
        }
    }

    private static final class GroupKey {
        final long dialog_id;
        final long groupedId;

        GroupKey(long dialogId, long groupedId) {
            this.dialog_id = dialogId;
            this.groupedId = groupedId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GroupKey that = (GroupKey) o;
            return dialog_id == that.dialog_id && groupedId == that.groupedId;
        }

        @Override
        public int hashCode() {
            int result = Long.hashCode(dialog_id);
            result = 31 * result + Long.hashCode(groupedId);
            return result;
        }
    }

    private static final class MessageCompositeID {
        final long dialog_id;
        final int id;

        MessageCompositeID(long dialogId, int messageId) {
            dialog_id = dialogId;
            id = messageId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MessageCompositeID that = (MessageCompositeID) o;
            return dialog_id == that.dialog_id && id == that.id;
        }

        @Override
        public int hashCode() {
            int result = Long.hashCode(dialog_id);
            result = 31 * result + id;
            return result;
        }
    }
}
