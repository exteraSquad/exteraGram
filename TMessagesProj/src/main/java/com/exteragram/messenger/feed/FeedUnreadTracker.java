/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.feed;

import androidx.collection.LongSparseArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;

final class FeedUnreadTracker {

    private static final long MARK_AS_READ_DEBOUNCE_MS = 1_000L;

    private final int currentAccount;
    private ArrayList<MessageObject> timeline;

    private final LongSparseArray<Integer> readInboxMaxByDialog = new LongSparseArray<>();
    private final LongSparseArray<Integer> pendingMaxReadId = new LongSparseArray<>();
    private boolean flushScheduled;
    private final Runnable flushRunnable = this::flush;

    FeedUnreadTracker(int currentAccount, ArrayList<MessageObject> timeline) {
        this.currentAccount = currentAccount;
        this.timeline = timeline;
    }

    void setTimeline(ArrayList<MessageObject> timeline) {
        if (flushScheduled) {
            AndroidUtilities.cancelRunOnUIThread(flushRunnable);
        }
        flush();
        this.timeline = timeline;
    }

    void clear() {
        if (flushScheduled) {
            AndroidUtilities.cancelRunOnUIThread(flushRunnable);
            flushScheduled = false;
        }
        flush();
        readInboxMaxByDialog.clear();
    }

    void applyReadInboxMax(long dialogId, int readInboxMax) {
        int current = readInboxMaxByDialog.get(dialogId, 0);
        if (readInboxMax > current) {
            readInboxMaxByDialog.put(dialogId, readInboxMax);
        }
    }

    boolean isUnread(MessageObject msg) {
        if (msg == null || msg.isSponsored()) {
            return false;
        }
        return msg.getRealId() > getEffectiveReadInboxMax(msg.getDialogId());
    }

    private int getEffectiveReadInboxMax(long dialogId) {
        return Math.max(readInboxMaxByDialog.get(dialogId, 0), pendingMaxReadId.get(dialogId, 0));
    }

    int findFirstUnreadIndex(ArrayList<MessageObject> messages) {
        if (messages == null || readInboxMaxByDialog.isEmpty()) {
            return -1;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (isUnread(messages.get(i))) {
                return i;
            }
        }
        return -1;
    }

    int countUnreadBelow(ArrayList<MessageObject> messages, int fromIndexExclusive) {
        if (messages == null || readInboxMaxByDialog.isEmpty()) {
            return 0;
        }
        int limit = Math.min(fromIndexExclusive, messages.size());
        int count = 0;
        for (int i = 0; i < limit; i++) {
            MessageObject msg = messages.get(i);
            if (msg == null || msg.isDateObject || msg.type == MessageObject.TYPE_LOADING || msg.isSponsored()) {
                continue;
            }
            if (isUnread(msg)) {
                count++;
            }
        }
        return count;
    }

    void onPostSeen(long dialogId, int realId) {
        if (dialogId == 0 || realId <= 0) {
            return;
        }
        if (realId <= getEffectiveReadInboxMax(dialogId)) {
            return;
        }
        Integer pending = pendingMaxReadId.get(dialogId);
        if (pending != null && pending >= realId) {
            return;
        }
        pendingMaxReadId.put(dialogId, realId);
        if (!flushScheduled) {
            flushScheduled = true;
            AndroidUtilities.runOnUIThread(flushRunnable, MARK_AS_READ_DEBOUNCE_MS);
        }
    }

    private void flush() {
        flushScheduled = false;
        if (pendingMaxReadId.isEmpty()) {
            return;
        }
        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        int currentDate = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        LongSparseArray<Integer> countDiffs = countPendingTimelineRows();
        for (int i = 0; i < pendingMaxReadId.size(); i++) {
            long dialogId = pendingMaxReadId.keyAt(i);
            int maxRealId = pendingMaxReadId.valueAt(i);
            int previousMax = readInboxMaxByDialog.get(dialogId, 0);
            if (maxRealId <= previousMax) {
                continue;
            }
            readInboxMaxByDialog.put(dialogId, maxRealId);
            messagesController.markDialogAsRead(dialogId, maxRealId, 0, currentDate, false, 0, Math.max(countDiffs.get(dialogId, 0), 1), true, 0);
        }
        pendingMaxReadId.clear();
    }

    private LongSparseArray<Integer> countPendingTimelineRows() {
        LongSparseArray<Integer> counts = new LongSparseArray<>();
        for (int i = 0; i < timeline.size(); i++) {
            MessageObject msg = timeline.get(i);
            if (msg == null) {
                continue;
            }
            long dialogId = msg.getDialogId();
            int pendingMax = pendingMaxReadId.get(dialogId, 0);
            if (pendingMax == 0) {
                continue;
            }
            int realId = msg.getRealId();
            if (realId > readInboxMaxByDialog.get(dialogId, 0) && realId <= pendingMax) {
                counts.put(dialogId, counts.get(dialogId, 0) + 1);
            }
        }
        return counts;
    }

    void markAllRead() {
        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        HashSet<Long> covered = new HashSet<>();
        for (TLRPC.Dialog dialog : collectUnreadFeedDialogs()) {
            messagesController.markMentionsAsRead(dialog.id, 0);
            messagesController.markDialogAsRead(dialog.id, dialog.top_message, dialog.top_message, dialog.last_message_date, false, 0, 0, true, 0);
            readInboxMaxByDialog.put(dialog.id, dialog.top_message);
            covered.add(dialog.id);
        }
        FeedConfig config = FeedConfig.getInstance(currentAccount);
        boolean includeArchived = config.isIncludeArchived();
        for (int i = 0; i < timeline.size(); i++) {
            MessageObject msg = timeline.get(i);
            if (msg == null) {
                continue;
            }
            long dialogId = msg.getDialogId();
            if (!config.isShownInFeed(dialogId)) {
                continue;
            }
            if (!includeArchived) {
                TLRPC.Dialog dialog = messagesController.dialogs_dict.get(dialogId);
                if (dialog != null && dialog.folder_id == 1) {
                    continue;
                }
            }
            covered.add(dialogId);
            int realId = msg.getRealId();
            if (realId > readInboxMaxByDialog.get(dialogId, 0)) {
                readInboxMaxByDialog.put(dialogId, realId);
            }
        }
        for (Long dialogId : covered) {
            pendingMaxReadId.remove(dialogId);
        }
        if (pendingMaxReadId.isEmpty() && flushScheduled) {
            AndroidUtilities.cancelRunOnUIThread(flushRunnable);
            flushScheduled = false;
        }
    }

    int getUnreadCount() {
        int total = 0;
        for (TLRPC.Dialog dialog : collectUnreadFeedDialogs()) {
            total += dialog.unread_count;
        }
        return total;
    }

    private ArrayList<TLRPC.Dialog> collectUnreadFeedDialogs() {
        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        FeedConfig config = FeedConfig.getInstance(currentAccount);
        boolean includeArchived = config.isIncludeArchived();
        LongSparseArray<TLRPC.Dialog> dialogs = messagesController.dialogs_dict;
        ArrayList<TLRPC.Dialog> result = new ArrayList<>();
        for (int i = 0; i < dialogs.size(); i++) {
            TLRPC.Dialog dialog = dialogs.valueAt(i);
            if (dialog == null || dialog.unread_count <= 0) {
                continue;
            }
            long dialogId = dialog.id;
            if (!DialogObject.isChatDialog(dialogId) || !config.isShownInFeed(dialogId)) {
                continue;
            }
            if (!includeArchived && dialog.folder_id == 1) {
                continue;
            }
            if (!FeedController.isEligibleChannel(messagesController.getChat(-dialogId))) {
                continue;
            }
            result.add(dialog);
        }
        return result;
    }
}