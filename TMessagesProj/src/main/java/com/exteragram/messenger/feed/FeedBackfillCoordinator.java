/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.feed;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;

final class FeedBackfillCoordinator {

    private static final int CHANNEL_BATCH_SIZE = 4;
    private static final int MESSAGES_PER_CHANNEL = 20;
    private static final long TIMEOUT_MS = 10_000L;

    private final int currentAccount;
    private final Runnable onRoundFinished;
    private final int guid = ConnectionsManager.generateClassGuid();

    private final HashSet<Long> pending = new HashSet<>();
    private final HashSet<Long> exhausted = new HashSet<>();
    private boolean running;
    private int roundId;
    private int loadIndex;

    FeedBackfillCoordinator(int currentAccount, Runnable onRoundFinished) {
        this.currentAccount = currentAccount;
        this.onRoundFinished = onRoundFinished;
    }

    HashSet<Long> getExhaustedSnapshot() {
        return new HashSet<>(exhausted);
    }

    void clearExhausted() {
        exhausted.clear();
    }

    void cancel() {
        running = false;
        roundId++;
        pending.clear();
        ConnectionsManager.getInstance(currentAccount).cancelRequestsForGuid(guid);
    }

    void startRound(ArrayList<long[]> candidates) {
        running = true;
        final int startedRoundId = ++roundId;
        pending.clear();
        int batch = Math.min(CHANNEL_BATCH_SIZE, candidates.size());
        for (int i = 0; i < batch; i++) {
            pending.add(candidates.get(i)[0]);
        }
        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        for (int i = 0; i < batch; i++) {
            long dialogId = candidates.get(i)[0];
            int offsetMid = (int) candidates.get(i)[1];
            messagesController.loadMessages(dialogId, 0, false, MESSAGES_PER_CHANNEL, offsetMid, 0, false, 0, guid, 0, 0, 0, 0, 0, loadIndex++, false);
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (startedRoundId == roundId && running) {
                exhausted.addAll(pending);
                finishRound();
            }
        }, TIMEOUT_MS);
    }

    void onMessagesDidLoad(Object... args) {
        if ((Integer) args[10] != guid) {
            return;
        }
        long dialogId = (Long) args[0];
        ArrayList<?> objects = (ArrayList<?>) args[2];
        if (objects.size() < MESSAGES_PER_CHANNEL) {
            exhausted.add(dialogId);
        }
        onResult(dialogId);
    }

    void onLoadingMessagesFailed(Object... args) {
        if ((Integer) args[0] != guid) {
            return;
        }
        long dialogId = 0;
        if (args[1] instanceof TLRPC.TL_messages_getHistory) {
            TLRPC.InputPeer peer = ((TLRPC.TL_messages_getHistory) args[1]).peer;
            if (peer != null) {
                dialogId = peer.channel_id != 0 ? -peer.channel_id : -peer.chat_id;
            }
        }
        if (dialogId != 0) {
            exhausted.add(dialogId);
        }
        onResult(dialogId);
    }

    private void onResult(long dialogId) {
        if (!running || !pending.remove(dialogId)) {
            return;
        }
        if (pending.isEmpty()) {
            finishRound();
        }
    }

    private void finishRound() {
        running = false;
        roundId++;
        pending.clear();
        onRoundFinished.run();
    }
}
