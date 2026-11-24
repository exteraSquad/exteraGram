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
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;

public final class FeedStore {

    private final ArrayList<MessageObject> messages = new ArrayList<>();
    private final FeedMessageIdentityMap identityMap = new FeedMessageIdentityMap();
    private final HashSet<Long> hiddenDialogIds = new HashSet<>();

    private final FeedTimelineLoader.Cursor oldestCursor = new FeedTimelineLoader.Cursor();
    private final FeedTimelineLoader.Cursor newestCursor = new FeedTimelineLoader.Cursor();
    private boolean endReached;
    private int count;

    public ArrayList<MessageObject> getMessages() {
        return messages;
    }

    public ArrayList<MessageObject> getVisibleMessages() {
        if (hiddenDialogIds.isEmpty()) {
            return new ArrayList<>(messages);
        }
        ArrayList<MessageObject> visible = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            MessageObject msg = messages.get(i);
            if (msg != null && !hiddenDialogIds.contains(msg.getDialogId())) {
                visible.add(msg);
            }
        }
        return visible;
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    public int getVisibleCount() {
        if (hiddenDialogIds.isEmpty()) {
            return messages.size();
        }
        int visible = 0;
        for (int i = 0; i < messages.size(); i++) {
            MessageObject msg = messages.get(i);
            if (msg != null && !hiddenDialogIds.contains(msg.getDialogId())) {
                visible++;
            }
        }
        return visible;
    }

    public boolean hasMessagesForDialog(long dialogId) {
        for (int i = 0; i < messages.size(); i++) {
            MessageObject messageObject = messages.get(i);
            if (messageObject != null && messageObject.getDialogId() == dialogId) {
                return true;
            }
        }
        return false;
    }

    public HashSet<Long> getLoadedDialogIds() {
        HashSet<Long> ids = new HashSet<>();
        for (int i = 0; i < messages.size(); i++) {
            MessageObject msg = messages.get(i);
            if (msg != null) {
                ids.add(msg.getDialogId());
            }
        }
        return ids;
    }

    public boolean isHidden(long dialogId) {
        return hiddenDialogIds.contains(dialogId);
    }

    public HashSet<Long> getHiddenSnapshot() {
        return new HashSet<>(hiddenDialogIds);
    }

    public boolean setHidden(long dialogId, boolean hidden) {
        boolean changed = hidden ? hiddenDialogIds.add(dialogId) : hiddenDialogIds.remove(dialogId);
        if (changed) {
            updateCount();
        }
        return changed;
    }

    public boolean applyIncludedDialogs(HashSet<Long> includedDialogIds) {
        boolean changed = false;
        HashSet<Long> loaded = getLoadedDialogIds();
        for (Long dialogId : loaded) {
            if (!includedDialogIds.contains(dialogId)) {
                changed |= hiddenDialogIds.add(dialogId);
            }
        }
        for (Iterator<Long> it = hiddenDialogIds.iterator(); it.hasNext(); ) {
            Long dialogId = it.next();
            if (includedDialogIds.contains(dialogId) || !loaded.contains(dialogId)) {
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            updateCount();
        }
        return changed;
    }

    FeedTimelineLoader.Cursor getOldestCursor() {
        return oldestCursor;
    }

    FeedTimelineLoader.Cursor getNewestCursor() {
        return newestCursor;
    }

    public boolean isEndReached() {
        return endReached;
    }

    void setEndReached(boolean value) {
        endReached = value;
        updateCount();
    }

    public int getCount() {
        return count;
    }

    private void updateCount() {
        int visible = getVisibleCount();
        count = visible == 0 ? 0 : visible + (endReached ? 0 : 3);
    }

    public void clear() {
        messages.clear();
        identityMap.clear();
        hiddenDialogIds.clear();
        endReached = false;
        count = 0;
        oldestCursor.set(0, 0, 0);
        newestCursor.set(0, 0, 0);
    }

    ArrayList<MessageObject> appendMessages(ArrayList<MessageObject> messageObjects, boolean toFront) {
        ArrayList<MessageObject> added = new ArrayList<>(messageObjects.size());
        for (MessageObject msg : messageObjects) {
            if (identityMap.register(msg)) {
                added.add(msg);
            }
        }
        if (toFront) {
            ArrayList<MessageObject> front = new ArrayList<>(added);
            Collections.reverse(front);
            messages.addAll(0, front);
        } else {
            messages.addAll(added);
        }
        updateCount();
        return added;
    }

    ArrayList<MessageObject> mergeRows(ArrayList<MessageObject> messageObjects) {
        ArrayList<MessageObject> added = new ArrayList<>(messageObjects.size());
        for (MessageObject msg : messageObjects) {
            if (identityMap.register(msg)) {
                added.add(msg);
            }
        }
        int searchFrom = 0;
        int i = 0;
        while (i < added.size()) {
            MessageObject head = added.get(i);
            int batchEnd = i + 1;
            long groupId = head.getGroupId();
            while (groupId != 0 && batchEnd < added.size()
                    && added.get(batchEnd).getGroupId() == groupId
                    && added.get(batchEnd).getDialogId() == head.getDialogId()) {
                batchEnd++;
            }
            int index = findMergeIndex(head, searchFrom);
            for (int j = i; j < batchEnd; j++) {
                messages.add(index++, added.get(j));
            }
            searchFrom = index;
            i = batchEnd;
        }
        updateCount();
        return added;
    }

    private int findMergeIndex(MessageObject msg, int fromIndex) {
        int index = fromIndex;
        while (index < messages.size()) {
            MessageObject existing = messages.get(index);
            if (existing != null && compareTimeline(existing.messageOwner.date, existing.getDialogId(), existing.getRealId(),
                    msg.messageOwner.date, msg.getDialogId(), msg.getRealId()) < 0) {
                break;
            }
            index++;
        }
        while (index > 0 && index < messages.size()) {
            MessageObject prev = messages.get(index - 1);
            MessageObject next = messages.get(index);
            if (prev == null || next == null || prev.getGroupId() == 0
                    || prev.getGroupId() != next.getGroupId() || prev.getDialogId() != next.getDialogId()) {
                break;
            }
            index++;
        }
        return index;
    }

    public void replaceMessage(MessageObject oldMessage, MessageObject newMessage) {
        if (oldMessage == null || newMessage == null) {
            return;
        }
        int index = messages.indexOf(oldMessage);
        if (index >= 0) {
            messages.set(index, newMessage);
        }
        identityMap.replace(newMessage);
    }

    ArrayList<Integer> deleteMessages(long dialogId, ArrayList<Integer> realIds, boolean[] removedAny) {
        ArrayList<Integer> deletedIds = new ArrayList<>();
        if (realIds == null) {
            return deletedIds;
        }
        HashSet<Integer> realIdsSet = new HashSet<>(realIds);
        HashSet<Integer> deletedSyntheticIds = new HashSet<>();
        boolean removed = false;
        for (int i = 0; i < realIds.size(); i++) {
            MessageObject msg = identityMap.getByRealId(dialogId, realIds.get(i));
            if (msg == null) {
                continue;
            }
            messages.remove(msg);
            removed = true;
            purgeRow(msg, deletedIds, deletedSyntheticIds);
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            MessageObject msg = messages.get(i);
            if (msg == null || msg.getDialogId() != dialogId || !realIdsSet.contains(msg.getRealId())) {
                continue;
            }
            removed = true;
            messages.remove(i);
            purgeRow(msg, deletedIds, deletedSyntheticIds);
        }
        if (removed) {
            if (!hasMessagesForDialog(dialogId)) {
                hiddenDialogIds.remove(dialogId);
            }
            onRowsRemoved();
        }
        removedAny[0] = removed;
        return deletedIds;
    }

    ArrayList<Integer> deleteHistory(long dialogId, int maxId, boolean[] removedAny) {
        ArrayList<Integer> deletedIds = new ArrayList<>();
        HashSet<Integer> deletedSyntheticIds = new HashSet<>();
        boolean removed = false;
        for (int i = messages.size() - 1; i >= 0; i--) {
            MessageObject msg = messages.get(i);
            if (msg == null || msg.getDialogId() != dialogId || msg.getRealId() <= 0 || msg.getRealId() > maxId) {
                continue;
            }
            removed = true;
            messages.remove(i);
            purgeRow(msg, deletedIds, deletedSyntheticIds);
        }
        if (removed) {
            if (!hasMessagesForDialog(dialogId)) {
                hiddenDialogIds.remove(dialogId);
            }
            onRowsRemoved();
        }
        removedAny[0] = removed;
        return deletedIds;
    }

    boolean trim(int cap) {
        if (cap <= 0 || messages.size() <= cap) {
            return false;
        }
        MessageObject cut = messages.get(cap - 1);
        final int cutDate = cut.messageOwner.date;
        final long cutUid = cut.getDialogId();
        final int cutMid = cut.getRealId();
        boolean trimmed = false;
        for (int i = messages.size() - 1; i >= 0; i--) {
            MessageObject msg = messages.get(i);
            if (msg != null && compareTimeline(msg.messageOwner.date, msg.getDialogId(), msg.getRealId(), cutDate, cutUid, cutMid) < 0) {
                messages.remove(i);
                identityMap.purge(msg);
                trimmed = true;
            }
        }
        if (!trimmed) {
            return false;
        }
        if (messages.isEmpty()) {
            oldestCursor.set(0, 0, 0);
        } else {
            int oldDate = 0;
            long oldUid = 0;
            int oldMid = 0;
            for (int i = 0; i < messages.size(); i++) {
                MessageObject msg = messages.get(i);
                if (msg == null) {
                    continue;
                }
                if (oldDate == 0 || compareTimeline(msg.messageOwner.date, msg.getDialogId(), msg.getRealId(), oldDate, oldUid, oldMid) < 0) {
                    oldDate = msg.messageOwner.date;
                    oldUid = msg.getDialogId();
                    oldMid = msg.getRealId();
                }
            }
            oldestCursor.set(oldDate, oldUid, oldMid);
        }
        endReached = false;
        updateCount();
        return true;
    }

    private void onRowsRemoved() {
        boolean hasRows = rebuildPagingCursorsFromLoadedRows();
        if (!hasRows) {
            endReached = false;
        }
        updateCount();
    }

    private boolean rebuildPagingCursorsFromLoadedRows() {
        boolean removedOldest = !oldestCursor.isEmpty();
        boolean removedNewest = !newestCursor.isEmpty();
        int newestDate = 0;
        long newestUid = 0;
        int newestMid = 0;
        int oldestDate = 0;
        long oldestUid = 0;
        int oldestMid = 0;
        for (int i = 0; i < messages.size(); i++) {
            MessageObject msg = messages.get(i);
            if (!isPagingRow(msg)) {
                continue;
            }
            int date = msg.messageOwner.date;
            long uid = msg.getDialogId();
            int mid = msg.getRealId();
            if (newestDate == 0 || compareTimeline(date, uid, mid, newestDate, newestUid, newestMid) > 0) {
                newestDate = date;
                newestUid = uid;
                newestMid = mid;
            }
            if (oldestDate == 0 || compareTimeline(date, uid, mid, oldestDate, oldestUid, oldestMid) < 0) {
                oldestDate = date;
                oldestUid = uid;
                oldestMid = mid;
            }
        }
        if (newestDate == 0) {
            oldestCursor.set(0, 0, 0);
            newestCursor.set(0, 0, 0);
            return false;
        }
        if (removedOldest && compareTimeline(oldestDate, oldestUid, oldestMid, oldestCursor.date, oldestCursor.uid, oldestCursor.mid) > 0) {
            endReached = false;
        }
        newestCursor.set(newestDate, newestUid, newestMid);
        oldestCursor.set(oldestDate, oldestUid, oldestMid);
        return true;
    }

    private static boolean isPagingRow(MessageObject msg) {
        return msg != null && !msg.isDateObject && msg.messageOwner != null && msg.getRealId() > 0;
    }

    static int compareTimeline(int date, long uid, int mid, int otherDate, long otherUid, int otherMid) {
        if (date != otherDate) {
            return Integer.compare(date, otherDate);
        }
        if (uid != otherUid) {
            return Long.compare(uid, otherUid);
        }
        return Integer.compare(mid, otherMid);
    }

    private void purgeRow(MessageObject msg, ArrayList<Integer> deletedIds, HashSet<Integer> deletedSyntheticIds) {
        identityMap.purge(msg);
        if (deletedSyntheticIds.add(msg.getId())) {
            deletedIds.add(msg.getId());
        }
    }

    public boolean hasNoSyntheticIds() {
        return identityMap.isEmpty();
    }

    public MessageObject getMessage(long dialogId, int messageId) {
        return identityMap.getByAnyId(dialogId, messageId);
    }

    public int resolveRealMessageId(long dialogId, int messageId) {
        return identityMap.resolveRealMessageId(dialogId, messageId);
    }

    public long resolveRealDialogId(int messageId) {
        return identityMap.resolveRealDialogId(messageId);
    }
}
