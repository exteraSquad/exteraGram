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
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;

public class FeedChannelRegistry implements NotificationCenter.NotificationCenterDelegate {

    public interface Listener {
        void onFeedChannelsChanged(HashSet<Long> added, HashSet<Long> removed);
    }

    private static final FeedChannelRegistry[] instances = new FeedChannelRegistry[UserConfig.MAX_ACCOUNT_COUNT];
    private static final Object[] locks = new Object[UserConfig.MAX_ACCOUNT_COUNT];

    static {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            locks[i] = new Object();
        }
    }

    public static FeedChannelRegistry getInstance(int num) {
        FeedChannelRegistry local = instances[num];
        if (local == null) {
            synchronized (locks[num]) {
                local = instances[num];
                if (local == null) {
                    instances[num] = local = new FeedChannelRegistry(num);
                }
            }
        }
        return local;
    }

    private static final long REBUILD_DEBOUNCE_MS = 500L;

    public final int currentAccount;

    private final HashSet<Long> channelIds = new HashSet<>();
    private final ArrayList<Listener> listeners = new ArrayList<>();
    private boolean built;
    private boolean rebuildScheduled;
    private final Runnable rebuildRunnable = () -> {
        rebuildScheduled = false;
        rebuild(true);
    };

    private FeedChannelRegistry(int currentAccount) {
        this.currentAccount = currentAccount;
        AndroidUtilities.runOnUIThread(() ->
                NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.dialogsNeedReload));
    }

    public void addListener(Listener listener) {
        ensureBuilt();
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public HashSet<Long> getChannelIds() {
        ensureBuilt();
        return new HashSet<>(channelIds);
    }

    public boolean isMember(long dialogId) {
        ensureBuilt();
        return channelIds.contains(dialogId);
    }

    private void ensureBuilt() {
        if (!built) {
            built = true;
            rebuild(false);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.dialogsNeedReload) {
            ensureBuilt();
            if (!rebuildScheduled) {
                rebuildScheduled = true;
                AndroidUtilities.runOnUIThread(rebuildRunnable, REBUILD_DEBOUNCE_MS);
            }
        }
    }

    private void rebuild(boolean notify) {
        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        LongSparseArray<TLRPC.Dialog> dialogs = messagesController.dialogs_dict;
        HashSet<Long> current = new HashSet<>();
        for (int i = 0; i < dialogs.size(); i++) {
            TLRPC.Dialog dialog = dialogs.valueAt(i);
            if (dialog == null || !DialogObject.isChatDialog(dialog.id)) {
                continue;
            }
            if (FeedController.isEligibleChannel(messagesController.getChat(-dialog.id))) {
                current.add(dialog.id);
            }
        }

        HashSet<Long> added = null;
        for (Long id : current) {
            if (!channelIds.contains(id)) {
                if (added == null) {
                    added = new HashSet<>();
                }
                added.add(id);
            }
        }
        HashSet<Long> removed = null;
        for (Long id : channelIds) {
            if (!current.contains(id)) {
                if (removed == null) {
                    removed = new HashSet<>();
                }
                removed.add(id);
            }
        }
        if (added == null && removed == null) {
            return;
        }
        channelIds.clear();
        channelIds.addAll(current);
        if (!notify) {
            return;
        }
        final HashSet<Long> addedFinal = added != null ? added : new HashSet<>();
        final HashSet<Long> removedFinal = removed != null ? removed : new HashSet<>();
        for (int i = listeners.size() - 1; i >= 0; i--) {
            listeners.get(i).onFeedChannelsChanged(addedFinal, removedFinal);
        }
    }
}
