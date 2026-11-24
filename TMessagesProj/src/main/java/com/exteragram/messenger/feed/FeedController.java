/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.feed;

import android.util.SparseArray;
import android.util.SparseIntArray;

import androidx.collection.LongSparseArray;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;

public class FeedController implements NotificationCenter.NotificationCenterDelegate {

    private static final int MAX_BACKFILL_ROUNDS_PER_LOAD = 3;
    private static final int MAX_UNREAD_BACKFILL_ROUNDS = 8;
    private static final int MAX_STALE_ENUMERATION_RETRIES = 3;
    private static final long CLOSED_REFRESH_DEBOUNCE_MS = 1_000L;
    private static final int MAX_TIMELINES = 3;

    private static final FeedController[] Instance = new FeedController[UserConfig.MAX_ACCOUNT_COUNT];
    private static final Object[] lockObjects = new Object[UserConfig.MAX_ACCOUNT_COUNT];

    static {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            lockObjects[i] = new Object();
        }
    }

    public static FeedController peekInstance(int num) {
        return Instance[num];
    }

    public static FeedController getInstance(int num) {
        FeedController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (lockObjects[num]) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new FeedController(num);
                }
            }
        }
        return localInstance;
    }

    public final int currentAccount;

    private final LinkedHashMap<Integer, Timeline> timelines = new LinkedHashMap<>(MAX_TIMELINES + 1, 0.75f, true);
    private Timeline activeTimeline;
    private FeedStore store;
    private final FeedUnreadTracker unreadTracker;
    private final FeedTimelineLoader loader;
    private final FeedBackfillCoordinator backfill;

    private boolean loading;
    private boolean loadingNewer;
    private boolean olderPagingBoundsDirty;
    private boolean newerPagingBoundsDirty;
    private boolean hasChannels;
    private boolean hasIncludedChannels;
    private int cachedIncludedChannelCount = -1;
    private int uiActiveClients;
    private int resumedUiClients;
    private int sessionGeneration;
    private volatile int channelCacheSession;
    private int badgeConfigGeneration;

    private int heldGuid;
    private int heldLoadIndex;
    private int attemptRounds;
    private int unreadBackfillRounds;
    private int staleEnumerationRetries;
    private boolean initialUnreadScrollPending = true;
    private final ArrayList<int[]> initialLoadWaiters = new ArrayList<>();

    private final int closedRefreshGuid = ConnectionsManager.generateClassGuid();
    private boolean closedRefreshScheduled;
    private final Runnable closedRefreshRunnable = this::runClosedRefresh;

    public static final class SavedScrollPosition {
        public final long dialogId;
        public final int messageId;
        public final int offsetTop;

        private SavedScrollPosition(long dialogId, int messageId, int offsetTop) {
            this.dialogId = dialogId;
            this.messageId = messageId;
            this.offsetTop = offsetTop;
        }
    }

    private static final class Timeline {
        final int categoryId;
        final FeedStore store = new FeedStore();
        FeedConfig.Snapshot appliedConfig;
        SavedScrollPosition scrollPosition;
        HashMap<Long, FeedTimelineLoader.Cursor> unreadStarts = new HashMap<>();
        HashSet<Long> unreadStartUnknown = new HashSet<>();

        Timeline(int categoryId, FeedConfig.Snapshot appliedConfig) {
            this.categoryId = categoryId;
            this.appliedConfig = appliedConfig;
        }
    }

    private FeedController(int currentAccount) {
        this.currentAccount = currentAccount;
        activeTimeline = obtainTimeline(FeedConfig.getInstance(currentAccount).getSelectedCategoryId(), null);
        store = activeTimeline.store;
        badgeConfigGeneration = FeedConfig.getInstance(currentAccount).getGeneration();
        unreadTracker = new FeedUnreadTracker(currentAccount, store.getMessages());
        loader = new FeedTimelineLoader(currentAccount);
        backfill = new FeedBackfillCoordinator(currentAccount, this::onBackfillRoundFinished);
        AndroidUtilities.runOnUIThread(() -> {
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagesDidLoad);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.loadingMessagesFailed);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.messagesDeleted);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.historyCleared);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didReceiveNewMessages);
            FeedChannelRegistry.getInstance(currentAccount).addListener(this::onFeedChannelsChanged);
        });
    }

    private void onFeedChannelsChanged(HashSet<Long> added, HashSet<Long> removed) {
        loader.invalidateChannelCache();
        for (Long dialogId : removed) {
            deleteHistory(dialogId, Integer.MAX_VALUE);
        }
        if (!added.isEmpty()) {
            for (Timeline timeline : timelines.values()) {
                if (timeline != activeTimeline) {
                    timeline.appliedConfig = null;
                }
            }
        }
        if (added.isEmpty()) {
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.feedNeedReload, false);
        } else {
            reconcileChannelSet(needsReload -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.feedNeedReload, needsReload));
        }
    }

    public FeedStore getStore() {
        return store;
    }

    public int getActiveTimelineId() {
        return activeTimeline.categoryId;
    }

    private Timeline obtainTimeline(int categoryId, Timeline keep) {
        Timeline timeline = timelines.get(categoryId);
        if (timeline != null) {
            return timeline;
        }
        timeline = new Timeline(categoryId, FeedConfig.getInstance(currentAccount).snapshot());
        timelines.put(categoryId, timeline);
        Iterator<Timeline> iterator = timelines.values().iterator();
        while (timelines.size() > MAX_TIMELINES && iterator.hasNext()) {
            Timeline candidate = iterator.next();
            if (candidate != timeline && candidate != keep && candidate != activeTimeline) {
                iterator.remove();
                candidate.store.clear();
            }
        }
        return timeline;
    }

    public boolean activateSelectedTimeline() {
        FeedConfig config = FeedConfig.getInstance(currentAccount);
        int categoryId = config.getSelectedCategoryId();
        if (activeTimeline.categoryId == categoryId) {
            return false;
        }
        cancelLoads();
        Timeline previous = activeTimeline;
        activeTimeline = obtainTimeline(categoryId, previous);
        store = activeTimeline.store;
        unreadTracker.setTimeline(store.getMessages());
        trimInactiveTimeline(previous);
        FeedConfig.Snapshot snapshot = config.snapshot();
        if (snapshot.hasSameFilter(activeTimeline.appliedConfig)) {
            activeTimeline.appliedConfig = snapshot;
        }
        pruneTimelines();
        return true;
    }

    private void pruneTimelines() {
        FeedConfig config = FeedConfig.getInstance(currentAccount);
        Iterator<Timeline> iterator = timelines.values().iterator();
        while (iterator.hasNext()) {
            Timeline timeline = iterator.next();
            if (timeline == activeTimeline || timeline.categoryId == 0) {
                continue;
            }
            FeedConfig.Category category = config.getCategory(timeline.categoryId);
            if (category == null || category.isHidden()) {
                iterator.remove();
                timeline.store.clear();
            }
        }
    }

    private void trimInactiveTimeline(Timeline timeline) {
        int cap = getInactiveCacheCap() / 2;
        SavedScrollPosition position = timeline.scrollPosition;
        if (position != null) {
            MessageObject row = timeline.store.getMessage(position.dialogId, position.messageId);
            int index = row == null ? -1 : timeline.store.getMessages().indexOf(row);
            if (index >= cap && index + FeedTimelineLoader.PAGE_SIZE <= cap * 2) {
                cap = index + FeedTimelineLoader.PAGE_SIZE;
            }
        }
        timeline.store.trim(cap);
    }

    private boolean isTimelineSelected() {
        return activeTimeline.categoryId == FeedConfig.getInstance(currentAccount).getSelectedCategoryId();
    }

    private boolean isConfigApplied() {
        FeedConfig.Snapshot applied = activeTimeline.appliedConfig;
        return applied != null && applied.getGeneration() == FeedConfig.getInstance(currentAccount).getGeneration();
    }

    public ArrayList<MessageObject> getMessages() {
        return store.getMessages();
    }

    public boolean isLoading() {
        return loading || loadingNewer;
    }

    public boolean hasMessagesForDialog(long dialogId) {
        return store.hasMessagesForDialog(dialogId);
    }

    public boolean hasChannels() {
        return hasChannels;
    }

    public boolean hasIncludedChannels() {
        return hasIncludedChannels;
    }

    public int getIncludedChannelCount() {
        return cachedIncludedChannelCount;
    }

    public boolean selectCategory(int categoryId) {
        if (!FeedConfig.getInstance(currentAccount).selectCategory(categoryId)) {
            return false;
        }
        notifyCategoriesChanged();
        return true;
    }

    public void notifyCategoriesChanged() {
        pruneTimelines();
        int generation = FeedConfig.getInstance(currentAccount).getGeneration();
        if (badgeConfigGeneration != generation) {
            badgeConfigGeneration = generation;
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_READ_DIALOG_MESSAGE);
        }
    }

    public void setUiActive(boolean active) {
        if (active) {
            uiActiveClients++;
            if (uiActiveClients > 1) {
                return;
            }
            if (closedRefreshScheduled) {
                AndroidUtilities.cancelRunOnUIThread(closedRefreshRunnable);
                closedRefreshScheduled = false;
            }
            if (loadingNewer) {
                cancelLoads();
            }
        } else {
            if (uiActiveClients == 0) {
                return;
            }
            uiActiveClients--;
            if (uiActiveClients == 0) {
                cancelLoads();
                trimForInactiveCache();
            }
        }
    }

    private boolean isUiActive() {
        return uiActiveClients > 0;
    }

    public void setUiResumed(boolean resumed) {
        if (resumed) {
            resumedUiClients++;
        } else if (resumedUiClients > 0) {
            resumedUiClients--;
        }
    }

    public boolean hasResumedUi() {
        return resumedUiClients > 0;
    }

    public void clear() {
        sessionGeneration++;
        channelCacheSession++;
        activeTimeline.appliedConfig = FeedConfig.getInstance(currentAccount).snapshot();
        unreadTracker.clear();
        activeTimeline.scrollPosition = null;
        activeTimeline.unreadStarts = new HashMap<>();
        activeTimeline.unreadStartUnknown = new HashSet<>();
        store.clear();
        loading = false;
        loadingNewer = false;
        olderPagingBoundsDirty = false;
        newerPagingBoundsDirty = false;
        attemptRounds = 0;
        staleEnumerationRetries = 0;
        initialLoadWaiters.clear();
        backfill.cancel();
        backfill.clearExhausted();
        if (closedRefreshScheduled) {
            AndroidUtilities.cancelRunOnUIThread(closedRefreshRunnable);
            closedRefreshScheduled = false;
        }
    }

    public void cancelLoads() {
        sessionGeneration++;
        loading = false;
        loadingNewer = false;
        olderPagingBoundsDirty = false;
        newerPagingBoundsDirty = false;
        attemptRounds = 0;
        staleEnumerationRetries = 0;
        initialLoadWaiters.clear();
        backfill.cancel();
    }

    private static int getInactiveCacheCap() {
        return switch (SharedConfig.getDevicePerformanceClass()) {
            case SharedConfig.PERFORMANCE_CLASS_LOW -> 300;
            case SharedConfig.PERFORMANCE_CLASS_HIGH -> 1000;
            default -> 600;
        };
    }

    public void trimForInactiveCache() {
        if (isUiActive()) {
            return;
        }
        Iterator<Timeline> iterator = timelines.values().iterator();
        while (iterator.hasNext()) {
            Timeline timeline = iterator.next();
            if (timeline != activeTimeline) {
                iterator.remove();
                timeline.store.clear();
            }
        }
        if (!store.isEmpty()) {
            store.trim(getInactiveCacheCap());
        }
    }

    public boolean isIncludedChannelPost(long dialogId) {
        if (!DialogObject.isChatDialog(dialogId) || !FeedConfig.getInstance(currentAccount).isShownInFeed(dialogId)) {
            return false;
        }
        return isEligibleChannel(MessagesController.getInstance(currentAccount).getChat(-dialogId));
    }

    static boolean isEligibleChannel(TLRPC.Chat chat) {
        return chat != null && ChatObject.isChannelAndNotMegaGroup(chat) && !ChatObject.isCommunity(chat) && !ChatObject.isNotInChat(chat);
    }

    public boolean consumeInitialUnreadScroll() {
        boolean pending = initialUnreadScrollPending;
        initialUnreadScrollPending = false;
        return pending;
    }

    public int getUnreadCount() {
        return ExteraConfig.getShowFeedUnreadCounter() ? unreadTracker.getUnreadCount() : 0;
    }

    public void onPostSeen(MessageObject messageObject) {
        long dialogId = messageObject.getDialogId();
        int realId = messageObject.getRealId();
        if (!isUnreadStartLoaded(dialogId)) {
            return;
        }
        FeedTimelineLoader.Cursor start = activeTimeline.unreadStarts.get(dialogId);
        if (start != null && realId > start.mid) {
            start.set(messageObject.messageOwner.date, dialogId, realId);
        }
        unreadTracker.onPostSeen(dialogId, realId);
    }

    private boolean isUnreadStartLoaded(long dialogId) {
        if (activeTimeline.unreadStartUnknown.contains(dialogId)) {
            return false;
        }
        FeedTimelineLoader.Cursor start = activeTimeline.unreadStarts.get(dialogId);
        if (start == null || store.isEndReached()) {
            return true;
        }
        FeedTimelineLoader.Cursor oldest = store.getOldestCursor();
        return oldest.isEmpty() || FeedStore.compareTimeline(start.date, start.uid, start.mid, oldest.date, oldest.uid, oldest.mid) >= 0;
    }

    public void markAllRead() {
        unreadTracker.markAllRead();
    }

    public int findFirstUnreadIndex(ArrayList<MessageObject> messages) {
        return unreadTracker.findFirstUnreadIndex(messages);
    }

    public int countUnreadBelow(ArrayList<MessageObject> messages, int fromIndexExclusive) {
        return unreadTracker.countUnreadBelow(messages, fromIndexExclusive);
    }

    public void saveScrollPosition(int timelineId, long dialogId, int messageId, int offsetTop) {
        Timeline timeline = timelines.get(timelineId);
        if (timeline != null && dialogId != 0 && messageId > 0) {
            timeline.scrollPosition = new SavedScrollPosition(dialogId, messageId, offsetTop);
        }
    }

    public SavedScrollPosition getSavedScrollPosition() {
        return activeTimeline.scrollPosition;
    }

    public boolean hasNoSyntheticIds() {
        return store.hasNoSyntheticIds();
    }

    public MessageObject getMessage(long dialogId, int messageId) {
        return store.getMessage(dialogId, messageId);
    }

    public int resolveRealMessageId(long dialogId, int messageId) {
        return store.resolveRealMessageId(dialogId, messageId);
    }

    public long resolveRealDialogId(int messageId) {
        return store.resolveRealDialogId(messageId);
    }

    public boolean loadInitial(int guid, int loadIndex) {
        if (!ensureCurrentConfig()) {
            return false;
        }
        final FeedConfig config = FeedConfig.getInstance(currentAccount);
        final int expectedConfigGeneration = config.getGeneration();
        final int expectedCacheEpoch = loader.getChannelCacheEpoch();
        if (store.isEmpty()) {
            if (!loadMore(guid, loadIndex)) {
                initialLoadWaiters.add(new int[]{guid, loadIndex});
            }
            return false;
        }
        ArrayList<MessageObject> cachedMessages = store.getVisibleMessages();
        for (MessageObject messageObject : cachedMessages) {
            messageObject.viewsReloaded = false;
        }
        if (cachedMessages.isEmpty() && !store.isEndReached()) {
            if (!loadMore(guid, loadIndex)) {
                initialLoadWaiters.add(new int[]{guid, loadIndex});
            }
            return false;
        }
        if (!cachedMessages.isEmpty() && isConfigApplied()) {
            applyReadStateFromDialogs();
            postFeedResults(guid, loadIndex, cachedMessages, MessagesController.LOAD_BACKWARD, true, false);
            postFeedCount(guid);
            return true;
        }
        final int generation = sessionGeneration;
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            final FeedTimelineLoader.ChannelEnumeration enumeration = loader.enumerateChannels(config, channelCacheSession, true);
            AndroidUtilities.runOnUIThread(() -> {
                if (generation != sessionGeneration) {
                    return;
                }
                if (!isEnumerationCurrent(enumeration, config, expectedConfigGeneration, expectedCacheEpoch)) {
                    postFeedResults(guid, loadIndex, new ArrayList<>(), MessagesController.LOAD_BACKWARD, false, true);
                    postFeedCount(guid);
                    return;
                }
                applyEnumeration(enumeration);
                postFeedResults(guid, loadIndex, cachedMessages, MessagesController.LOAD_BACKWARD, false, enumeration.failed);
                postFeedCount(guid);
            });
        });
        return true;
    }

    private void applyReadStateFromDialogs() {
        LongSparseArray<TLRPC.Dialog> dialogs = MessagesController.getInstance(currentAccount).dialogs_dict;
        for (Long dialogId : store.getLoadedDialogIds()) {
            TLRPC.Dialog dialog = dialogs.get(dialogId);
            if (dialog == null) {
                continue;
            }
            int readInboxMax = dialog.read_inbox_max_id;
            if (readInboxMax <= 0 && dialog.unread_count <= 0) {
                readInboxMax = dialog.top_message;
            }
            unreadTracker.applyReadInboxMax(dialogId, readInboxMax);
        }
    }

    private boolean ensureCurrentConfig() {
        if (!isTimelineSelected()) {
            if (isUiActive()) {
                return false;
            }
            activateSelectedTimeline();
        }
        if (!isConfigApplied()) {
            applyConfigChange(needsReload -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.feedNeedReload, needsReload));
        }
        return true;
    }

    public void markConfigApplied() {
        activeTimeline.appliedConfig = FeedConfig.getInstance(currentAccount).snapshot();
    }

    public void applyConfigChange(Utilities.Callback<Boolean> onApplied) {
        reconcileChannelSet(onApplied);
    }

    private void reconcileChannelSet(Utilities.Callback<Boolean> onApplied) {
        if (!isTimelineSelected()) {
            if (onApplied != null) {
                onApplied.run(false);
            }
            return;
        }
        final int generation = sessionGeneration;
        final Timeline timeline = activeTimeline;
        final FeedConfig config = FeedConfig.getInstance(currentAccount);
        final int expectedConfigGeneration = config.getGeneration();
        final int expectedCacheEpoch = loader.getChannelCacheEpoch();
        if (store.isEmpty()) {
            loadChannels((channels, includedCount, failed, appliedConfig) -> {
                if (!failed && timeline == activeTimeline) {
                    timeline.appliedConfig = appliedConfig;
                }
                if (onApplied != null) {
                    onApplied.run(false);
                }
            });
            return;
        }
        final HashSet<Long> loadedBefore = store.getLoadedDialogIds();
        final HashSet<Long> hiddenBefore = store.getHiddenSnapshot();
        final FeedTimelineLoader.Cursor newest = new FeedTimelineLoader.Cursor();
        final FeedTimelineLoader.Cursor oldest = new FeedTimelineLoader.Cursor();
        newest.set(store.getNewestCursor().date, store.getNewestCursor().uid, store.getNewestCursor().mid);
        oldest.set(store.getOldestCursor().date, store.getOldestCursor().uid, store.getOldestCursor().mid);
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            final FeedTimelineLoader.ChannelEnumeration enumeration = loader.enumerateChannels(config, channelCacheSession, true);
            if (enumeration.failed) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (generation != sessionGeneration) {
                        return;
                    }
                    if (!isEnumerationCurrent(enumeration, config, expectedConfigGeneration, expectedCacheEpoch) && canRetryStaleEnumeration()) {
                        reconcileChannelSet(onApplied);
                        return;
                    }
                    if (onApplied != null) {
                        onApplied.run(false);
                    }
                });
                return;
            }
            ArrayList<Long> fillIds = new ArrayList<>();
            for (FeedTimelineLoader.ChannelSnapshot channel : enumeration.included) {
                if (!loadedBefore.contains(channel.dialogId) || hiddenBefore.contains(channel.dialogId)) {
                    fillIds.add(channel.dialogId);
                }
            }
            final FeedTimelineLoader.WindowPage page = fillIds.isEmpty() ? null : loader.loadChannelWindow(fillIds, newest, oldest);
            if (page != null && page.failed) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (generation != sessionGeneration) {
                        return;
                    }
                    if (!isEnumerationCurrent(enumeration, config, expectedConfigGeneration, expectedCacheEpoch) && canRetryStaleEnumeration()) {
                        reconcileChannelSet(onApplied);
                        return;
                    }
                    if (onApplied != null) {
                        onApplied.run(false);
                    }
                });
                return;
            }
            final ArrayList<MessageObject> messageObjects = page == null ? null : createMessageObjects(page.messages, page.users, page.chats);
            final boolean channelSetGrew = !fillIds.isEmpty();
            AndroidUtilities.runOnUIThread(() -> {
                if (generation != sessionGeneration) {
                    return;
                }
                if (!isEnumerationCurrent(enumeration, config, expectedConfigGeneration, expectedCacheEpoch) && canRetryStaleEnumeration()) {
                    reconcileChannelSet(onApplied);
                    return;
                }
                applyEnumeration(enumeration);
                timeline.appliedConfig = enumeration.config;
                HashSet<Long> includedSet = new HashSet<>();
                for (FeedTimelineLoader.ChannelSnapshot channel : enumeration.included) {
                    includedSet.add(channel.dialogId);
                }
                store.applyIncludedDialogs(includedSet);
                boolean truncated = page != null && page.truncated;
                if (page != null && !truncated && !messageObjects.isEmpty()) {
                    MessagesController messagesController = MessagesController.getInstance(currentAccount);
                    messagesController.putUsers(page.users, true);
                    messagesController.putChats(page.chats, true);
                    store.mergeRows(messageObjects);
                }
                if (channelSetGrew) {
                    store.setEndReached(false);
                    if (loading) {
                        olderPagingBoundsDirty = true;
                    }
                    if (loadingNewer) {
                        newerPagingBoundsDirty = true;
                    }
                }
                if (onApplied != null) {
                    onApplied.run(truncated);
                }
            });
        });
    }

    public void refreshReadState(Runnable onDone) {
        final int generation = sessionGeneration;
        final FeedConfig config = FeedConfig.getInstance(currentAccount);
        final int expectedConfigGeneration = config.getGeneration();
        final int expectedCacheEpoch = loader.getChannelCacheEpoch();
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            final FeedTimelineLoader.ChannelEnumeration enumeration = loader.enumerateChannels(config, channelCacheSession, true);
            AndroidUtilities.runOnUIThread(() -> {
                if (generation != sessionGeneration) {
                    return;
                }
                if (isEnumerationCurrent(enumeration, config, expectedConfigGeneration, expectedCacheEpoch)) {
                    applyEnumeration(enumeration);
                }
                if (onDone != null) {
                    onDone.run();
                }
            });
        });
    }

    public boolean loadMore(int guid, int loadIndex) {
        if (!ensureCurrentConfig()) {
            return false;
        }
        if (loading || store.isEndReached() && !store.getOldestCursor().isEmpty()) {
            return false;
        }
        loading = true;
        heldGuid = guid;
        heldLoadIndex = loadIndex;
        attemptRounds = 0;
        unreadBackfillRounds = 0;
        runAttempt();
        return true;
    }

    private void runAttempt() {
        final int guid = heldGuid;
        final int loadIndex = heldLoadIndex;
        final int generation = sessionGeneration;
        final FeedConfig config = FeedConfig.getInstance(currentAccount);
        final int expectedConfigGeneration = config.getGeneration();
        final int expectedCacheEpoch = loader.getChannelCacheEpoch();
        final boolean firstPage = store.getOldestCursor().isEmpty();
        final FeedTimelineLoader.Cursor cursor = new FeedTimelineLoader.Cursor();
        cursor.set(store.getOldestCursor().date, store.getOldestCursor().uid, store.getOldestCursor().mid);
        final HashSet<Long> exhausted = backfill.getExhaustedSnapshot();
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            final FeedTimelineLoader.ChannelEnumeration enumeration = loader.enumerateChannels(config, channelCacheSession, false);
            final ArrayList<FeedTimelineLoader.ChannelSnapshot> channels = enumeration.included;
            if (enumeration.failed) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (generation != sessionGeneration) {
                        return;
                    }
                    if (!isEnumerationCurrent(enumeration, config, expectedConfigGeneration, expectedCacheEpoch) && canRetryStaleEnumeration()) {
                        attemptRounds = 0;
                        runAttempt();
                        return;
                    }
                    loading = false;
                    postFeedResults(guid, loadIndex, new ArrayList<>(), MessagesController.LOAD_FROM_UNREAD, false, true);
                    postFeedCount(guid);
                    flushInitialLoadWaiters(true);
                });
                return;
            }
            if (channels.isEmpty()) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (generation != sessionGeneration) {
                        return;
                    }
                    if (!isEnumerationCurrent(enumeration, config, expectedConfigGeneration, expectedCacheEpoch)) {
                        if (canRetryStaleEnumeration()) {
                            olderPagingBoundsDirty = false;
                            attemptRounds = 0;
                            runAttempt();
                            return;
                        }
                        loading = false;
                        postFeedResults(guid, loadIndex, new ArrayList<>(), MessagesController.LOAD_FROM_UNREAD, false, true);
                        postFeedCount(guid);
                        flushInitialLoadWaiters(true);
                        return;
                    }
                    applyEnumeration(enumeration);
                    olderPagingBoundsDirty = false;
                    unreadTracker.clear();
                    loading = false;
                    store.setEndReached(true);
                    postFeedResults(guid, loadIndex, new ArrayList<>(), MessagesController.LOAD_FROM_UNREAD);
                    postFeedCount(guid);
                    flushInitialLoadWaiters();
                });
                return;
            }
            final FeedTimelineLoader.OlderPage page = loader.loadOlderPage(channels, cursor, exhausted);
            final ArrayList<MessageObject> messageObjects = createMessageObjects(page.messages, page.users, page.chats);
            AndroidUtilities.runOnUIThread(() -> {
                if (generation != sessionGeneration) {
                    return;
                }
                if (!isEnumerationCurrent(enumeration, config, expectedConfigGeneration, expectedCacheEpoch) && canRetryStaleEnumeration()) {
                    olderPagingBoundsDirty = false;
                    attemptRounds = 0;
                    runAttempt();
                    return;
                }
                if (olderPagingBoundsDirty) {
                    olderPagingBoundsDirty = false;
                    attemptRounds = 0;
                    runAttempt();
                    return;
                }
                applyEnumeration(enumeration);
                MessagesController messagesController = MessagesController.getInstance(currentAccount);
                pruneStaleExclusions(FeedConfig.getInstance(currentAccount), messagesController);

                if (page.failed) {
                    loading = false;
                    postFeedResults(guid, loadIndex, new ArrayList<>(), MessagesController.LOAD_FROM_UNREAD, false, true);
                    postFeedCount(guid);
                    flushInitialLoadWaiters(true);
                    return;
                }

                activeTimeline.unreadStarts = page.unreadStarts;
                activeTimeline.unreadStartUnknown = page.unreadStartUnknown;
                if (firstPage && !page.unreadBackfillCandidates.isEmpty() && unreadBackfillRounds < MAX_UNREAD_BACKFILL_ROUNDS) {
                    unreadBackfillRounds++;
                    backfill.startRound(page.unreadBackfillCandidates);
                    return;
                }

                store.getOldestCursor().set(page.last.date, page.last.uid, page.last.mid);
                if (firstPage && !page.first.isEmpty()) {
                    store.getNewestCursor().set(page.first.date, page.first.uid, page.first.mid);
                }

                messagesController.putUsers(page.users, true);
                messagesController.putChats(page.chats, true);
                ArrayList<MessageObject> addedObjects = store.appendMessages(messageObjects, false);

                if (addedObjects.isEmpty() && page.lastChunkRowCount == FeedTimelineLoader.PAGE_SIZE) {
                    runAttempt();
                    return;
                }

                boolean isEnd = !page.hasIncomplete && page.lastChunkRowCount < FeedTimelineLoader.PAGE_SIZE;
                if (!addedObjects.isEmpty() || isEnd || page.backfillCandidates.isEmpty() || attemptRounds >= MAX_BACKFILL_ROUNDS_PER_LOAD) {
                    loading = false;
                    store.setEndReached(isEnd);
                    postFeedResults(guid, loadIndex, addedObjects, MessagesController.LOAD_FROM_UNREAD);
                    postFeedCount(guid);
                    flushInitialLoadWaiters();
                } else {
                    attemptRounds++;
                    backfill.startRound(page.backfillCandidates);
                }
            });
        });
    }

    private void postFeedResults(int guid, int loadIndex, ArrayList<MessageObject> messages, int loadType) {
        postFeedResults(guid, loadIndex, messages, loadType, false, false);
    }

    private void postFeedResults(int guid, int loadIndex, ArrayList<MessageObject> messages, int loadType, boolean hasMoreNewer) {
        postFeedResults(guid, loadIndex, messages, loadType, hasMoreNewer, false);
    }

    private void postFeedResults(int guid, int loadIndex, ArrayList<MessageObject> messages, int loadType, boolean hasMoreNewer, boolean failed) {
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messagesDidLoad, 0L, messages.size(), messages, false, 0, 0, 0, 0, loadType, true, guid, loadIndex, 0, 0, ChatActivity.MODE_SEARCH, hasMoreNewer, failed);
    }

    private void postFeedCount(int guid) {
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.hashtagSearchUpdated, guid, store.getCount(), store.isEndReached(), 0, 0, 0);
    }

    private boolean isEnumerationCurrent(FeedTimelineLoader.ChannelEnumeration enumeration, FeedConfig config, int expectedConfigGeneration, int expectedCacheEpoch) {
        final boolean current = loader.isEnumerationCurrent(enumeration)
                && enumeration.configGeneration == config.getGeneration()
                && enumeration.configGeneration == expectedConfigGeneration
                && enumeration.cacheEpoch == expectedCacheEpoch;
        if (current) {
            staleEnumerationRetries = 0;
        }
        return current;
    }

    private boolean canRetryStaleEnumeration() {
        if (staleEnumerationRetries >= MAX_STALE_ENUMERATION_RETRIES) {
            staleEnumerationRetries = 0;
            return false;
        }
        staleEnumerationRetries++;
        return true;
    }

    private void applyEnumeration(FeedTimelineLoader.ChannelEnumeration enumeration) {
        if (enumeration.failed) {
            return;
        }
        hasChannels = enumeration.hasChannels;
        hasIncludedChannels = !enumeration.included.isEmpty();
        cachedIncludedChannelCount = enumeration.included.size();
        for (FeedTimelineLoader.ChannelSnapshot channel : enumeration.included) {
            int readInboxMax = channel.readInboxMax;
            if (readInboxMax <= 0 && channel.unreadCount <= 0) {
                readInboxMax = channel.topMessage;
            }
            unreadTracker.applyReadInboxMax(channel.dialogId, readInboxMax);
        }
    }

    private void flushInitialLoadWaiters() {
        flushInitialLoadWaiters(false);
    }

    private void flushInitialLoadWaiters(boolean failed) {
        if (initialLoadWaiters.isEmpty()) {
            return;
        }
        ArrayList<int[]> waiters = new ArrayList<>(initialLoadWaiters);
        initialLoadWaiters.clear();
        ArrayList<MessageObject> cachedMessages = store.getVisibleMessages();
        for (int[] waiter : waiters) {
            postFeedResults(waiter[0], waiter[1], cachedMessages, MessagesController.LOAD_BACKWARD, false, failed);
            postFeedCount(waiter[0]);
        }
    }

    private void onBackfillRoundFinished() {
        if (loading) {
            runAttempt();
        }
    }

    public boolean loadNewer(int guid, int loadIndex) {
        if (!ensureCurrentConfig()) {
            return false;
        }
        if (loadingNewer || store.getNewestCursor().isEmpty()) {
            return false;
        }
        loadingNewer = true;
        runLoadNewer(guid, loadIndex);
        return true;
    }

    private void runLoadNewer(int guid, int loadIndex) {
        final int generation = sessionGeneration;
        final FeedConfig config = FeedConfig.getInstance(currentAccount);
        final int expectedConfigGeneration = config.getGeneration();
        final int expectedCacheEpoch = loader.getChannelCacheEpoch();
        final FeedTimelineLoader.Cursor cursor = new FeedTimelineLoader.Cursor();
        cursor.set(store.getNewestCursor().date, store.getNewestCursor().uid, store.getNewestCursor().mid);
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            final FeedTimelineLoader.ChannelEnumeration enumeration = loader.enumerateChannels(config, channelCacheSession, false);
            final ArrayList<FeedTimelineLoader.ChannelSnapshot> channels = enumeration.included;
            if (enumeration.failed) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (generation != sessionGeneration) {
                        return;
                    }
                    if (!isEnumerationCurrent(enumeration, config, expectedConfigGeneration, expectedCacheEpoch) && canRetryStaleEnumeration()) {
                        runLoadNewer(guid, loadIndex);
                        return;
                    }
                    loadingNewer = false;
                    postNewerMessagesLoaded(guid, loadIndex, null, false, true);
                });
                return;
            }
            if (channels.isEmpty()) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (generation != sessionGeneration) {
                        return;
                    }
                    if (!isEnumerationCurrent(enumeration, config, expectedConfigGeneration, expectedCacheEpoch) && canRetryStaleEnumeration()) {
                        newerPagingBoundsDirty = false;
                        runLoadNewer(guid, loadIndex);
                        return;
                    }
                    newerPagingBoundsDirty = false;
                    loadingNewer = false;
                    postNewerMessagesLoaded(guid, loadIndex, null, false);
                    postFeedCount(guid);
                });
                return;
            }
            final FeedTimelineLoader.NewerPage page = loader.loadNewerPage(channels, cursor);
            final ArrayList<MessageObject> messageObjects = createMessageObjects(page.messages, page.users, page.chats);
            AndroidUtilities.runOnUIThread(() -> {
                if (generation != sessionGeneration) {
                    return;
                }
                if (!isEnumerationCurrent(enumeration, config, expectedConfigGeneration, expectedCacheEpoch) && canRetryStaleEnumeration()) {
                    newerPagingBoundsDirty = false;
                    runLoadNewer(guid, loadIndex);
                    return;
                }
                if (newerPagingBoundsDirty) {
                    newerPagingBoundsDirty = false;
                    if (store.getNewestCursor().isEmpty()) {
                        loadingNewer = false;
                        postNewerMessagesLoaded(guid, loadIndex, null, false);
                        postFeedCount(guid);
                    } else {
                        runLoadNewer(guid, loadIndex);
                    }
                    return;
                }
                loadingNewer = false;
                applyEnumeration(enumeration);
                if (page.failed) {
                    postNewerMessagesLoaded(guid, loadIndex, null, false, true);
                    return;
                }
                store.getNewestCursor().set(page.first.date, page.first.uid, page.first.mid);
                if (page.messages.isEmpty()) {
                    postNewerMessagesLoaded(guid, loadIndex, null, page.hasMore);
                    if (!page.hasMore) {
                        postFeedCount(guid);
                    }
                    return;
                }
                MessagesController messagesController = MessagesController.getInstance(currentAccount);
                messagesController.putUsers(page.users, true);
                messagesController.putChats(page.chats, true);
                ArrayList<MessageObject> addedObjects = store.appendMessages(messageObjects, true);
                postNewerMessagesLoaded(guid, loadIndex, addedObjects, page.hasMore);
                if (!page.hasMore) {
                    postFeedCount(guid);
                }
                trimForInactiveCache();
            });
        });
    }

    private void postNewerMessagesLoaded(int guid, int loadIndex, ArrayList<MessageObject> addedObjects, boolean hasMore) {
        postNewerMessagesLoaded(guid, loadIndex, addedObjects, hasMore, false);
    }

    private void postNewerMessagesLoaded(int guid, int loadIndex, ArrayList<MessageObject> addedObjects, boolean hasMore, boolean failed) {
        ArrayList<MessageObject> notifyObjects = new ArrayList<>();
        int loadType = MessagesController.LOAD_BACKWARD;
        if (addedObjects != null && !addedObjects.isEmpty()) {
            notifyObjects.addAll(addedObjects);
            Collections.reverse(notifyObjects);
            loadType = MessagesController.LOAD_FORWARD;
        }
        postFeedResults(guid, loadIndex, notifyObjects, loadType, hasMore, failed);
    }

    private ArrayList<MessageObject> createMessageObjects(ArrayList<TLRPC.Message> loadedMessages, ArrayList<TLRPC.User> users, ArrayList<TLRPC.Chat> chats) {
        HashMap<Long, TLRPC.User> usersDict = new HashMap<>();
        HashMap<Long, TLRPC.Chat> chatsDict = new HashMap<>();
        for (TLRPC.User user : users) {
            usersDict.put(user.id, user);
        }
        for (TLRPC.Chat chat : chats) {
            chatsDict.put(chat.id, chat);
        }
        ArrayList<MessageObject> messageObjects = new ArrayList<>(loadedMessages.size());
        for (TLRPC.Message msg : loadedMessages) {
            messageObjects.add(new MessageObject(currentAccount, msg, null, usersDict, chatsDict, null, null, true, true, 0, false, false, false, ChatActivity.SEARCH_FEED));
        }
        return messageObjects;
    }

    public void loadChannels(ChannelsCallback callback) {
        loadChannels(false, callback);
    }

    public void loadChannels(boolean forceRefresh, ChannelsCallback callback) {
        final FeedConfig config = FeedConfig.getInstance(currentAccount);
        final int sessionGen = sessionGeneration;
        final int expectedConfigGeneration = config.getGeneration();
        final int expectedCacheEpoch = loader.getChannelCacheEpoch();
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            FeedTimelineLoader.ChannelEnumeration enumeration = loader.enumerateChannels(config, channelCacheSession, forceRefresh);
            AndroidUtilities.runOnUIThread(() -> {
                if (sessionGen != sessionGeneration
                        || !isEnumerationCurrent(enumeration, config, expectedConfigGeneration, expectedCacheEpoch)) {
                    if (callback != null) {
                        callback.onChannels(new ArrayList<>(), 0, true, enumeration.config);
                    }
                    return;
                }
                applyEnumeration(enumeration);
                if (!enumeration.failed) {
                    MessagesController.getInstance(currentAccount).putChats(enumeration.channels, true);
                }
                if (callback != null) {
                    callback.onChannels(enumeration.channels, enumeration.included.size(), enumeration.failed, enumeration.config);
                }
            });
        });
    }

    public interface ChannelsCallback {
        void onChannels(ArrayList<TLRPC.Chat> channels, int includedCount, boolean failed, FeedConfig.Snapshot config);
    }

    private void pruneStaleExclusions(FeedConfig config, MessagesController messagesController) {
        HashSet<Long> stale = null;
        for (Long excludedId : config.getExcludedSnapshot()) {
            TLRPC.Chat chat = messagesController.getChat(-excludedId);
            if (chat != null && !isEligibleChannel(chat)) {
                if (stale == null) {
                    stale = new HashSet<>();
                }
                stale.add(excludedId);
            }
        }
        HashSet<Long> staleInCategories = null;
        for (FeedConfig.Category category : config.getCategories()) {
            for (Long channelId : category.getChannels()) {
                TLRPC.Chat chat = messagesController.getChat(-channelId);
                if (chat != null && !isEligibleChannel(chat)) {
                    if (staleInCategories == null) {
                        staleInCategories = new HashSet<>();
                    }
                    staleInCategories.add(channelId);
                }
            }
        }
        if (stale != null) {
            config.removeExcluded(stale);
        }
        if (staleInCategories != null) {
            config.removeFromCategories(staleInCategories);
        }
        if (stale != null || staleInCategories != null) {
            markConfigApplied();
        }
    }

    public void replaceMessage(MessageObject oldMessage, MessageObject newMessage) {
        store.replaceMessage(oldMessage, newMessage);
        for (Timeline timeline : timelines.values()) {
            if (timeline == activeTimeline) {
                continue;
            }
            MessageObject stale = timeline.store.getMessage(newMessage.getDialogId(), newMessage.getRealId());
            if (stale != null) {
                timeline.store.replaceMessage(stale, FeedMessageUtils.createReplacementFor(currentAccount, stale, newMessage));
            }
        }
    }

    public ArrayList<Integer> deleteMessages(long dialogId, ArrayList<Integer> realIds) {
        boolean[] removedAny = new boolean[1];
        ArrayList<Integer> deletedIds = store.deleteMessages(dialogId, realIds, removedAny);
        if (removedAny[0]) {
            onFeedRowsRemoved();
        }
        for (Timeline timeline : timelines.values()) {
            if (timeline != activeTimeline) {
                timeline.store.deleteMessages(dialogId, realIds, removedAny);
            }
        }
        return deletedIds;
    }

    public ArrayList<Integer> deleteHistory(long dialogId, int maxId) {
        boolean[] removedAny = new boolean[1];
        ArrayList<Integer> deletedIds = store.deleteHistory(dialogId, maxId, removedAny);
        if (removedAny[0]) {
            onFeedRowsRemoved();
        }
        for (Timeline timeline : timelines.values()) {
            if (timeline != activeTimeline) {
                timeline.store.deleteHistory(dialogId, maxId, removedAny);
            }
        }
        return deletedIds;
    }

    private void onFeedRowsRemoved() {
        if (loading) {
            olderPagingBoundsDirty = true;
        }
        if (loadingNewer) {
            newerPagingBoundsDirty = true;
        }
    }

    public ArrayList<MessageObject> updateViews(LongSparseArray<SparseIntArray> channelViews, LongSparseArray<SparseIntArray> channelForwards, LongSparseArray<SparseArray<TLRPC.MessageReplies>> channelReplies, boolean addingReplies) {
        ArrayList<MessageObject> updated = new ArrayList<>();
        for (Timeline timeline : timelines.values()) {
            ArrayList<MessageObject> target = timeline == activeTimeline ? updated : null;
            updateCounters(timeline.store, channelViews, true, target);
            updateCounters(timeline.store, channelForwards, false, target);
        }
        updateReplies(channelReplies, addingReplies, updated);
        return updated;
    }

    private void updateCounters(FeedStore target, LongSparseArray<SparseIntArray> counters, boolean views, ArrayList<MessageObject> updated) {
        if (counters == null) {
            return;
        }
        for (int i = 0; i < counters.size(); i++) {
            long dialogId = counters.keyAt(i);
            SparseIntArray values = counters.valueAt(i);
            for (int j = 0; j < values.size(); j++) {
                int realId = values.keyAt(j);
                MessageObject msg = target.getMessage(dialogId, realId);
                if (msg == null) {
                    continue;
                }
                int value = values.valueAt(j);
                if (views) {
                    if (value <= msg.messageOwner.views) {
                        continue;
                    }
                    msg.messageOwner.views = value;
                } else {
                    if (value <= msg.messageOwner.forwards) {
                        continue;
                    }
                    msg.messageOwner.forwards = value;
                }
                if (updated != null) {
                    addUpdated(updated, msg);
                }
            }
        }
    }

    private void updateReplies(LongSparseArray<SparseArray<TLRPC.MessageReplies>> channelReplies, boolean addingReplies, ArrayList<MessageObject> updated) {
        if (channelReplies == null) {
            return;
        }
        for (int i = 0; i < channelReplies.size(); i++) {
            long dialogId = channelReplies.keyAt(i);
            SparseArray<TLRPC.MessageReplies> values = channelReplies.valueAt(i);
            for (int j = 0; j < values.size(); j++) {
                int realId = values.keyAt(j);
                MessageObject msg = getMessage(dialogId, realId);
                TLRPC.MessageReplies replies = values.valueAt(j);
                if (msg == null || replies == null) {
                    continue;
                }
                if (addingReplies) {
                    if (msg.messageOwner.replies == null) {
                        msg.messageOwner.replies = new TLRPC.TL_messageReplies();
                    }
                    msg.messageOwner.replies.replies += replies.replies;
                    for (int k = 0; k < replies.recent_repliers.size(); k++) {
                        msg.messageOwner.replies.recent_repliers.remove(replies.recent_repliers.get(k));
                    }
                    msg.messageOwner.replies.recent_repliers.addAll(0, replies.recent_repliers);
                    while (msg.messageOwner.replies.recent_repliers.size() > 3) {
                        msg.messageOwner.replies.recent_repliers.remove(0);
                    }
                } else if (msg.messageOwner.replies != null && replies.replies_pts <= msg.messageOwner.replies.replies_pts && replies.read_max_id <= msg.messageOwner.replies.read_max_id && replies.max_id <= msg.messageOwner.replies.max_id) {
                    continue;
                } else {
                    msg.messageOwner.replies = replies;
                }
                msg.animateComments = true;
                addUpdated(updated, msg);
            }
        }
    }

    private static void addUpdated(ArrayList<MessageObject> updated, MessageObject msg) {
        if (!updated.contains(msg)) {
            updated.add(msg);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagesDidLoad) {
            backfill.onMessagesDidLoad(args);
        } else if (id == NotificationCenter.loadingMessagesFailed) {
            backfill.onLoadingMessagesFailed(args);
        } else if (id == NotificationCenter.messagesDeleted) {
            if (isUiActive() || (Boolean) args[2]) {
                return;
            }
            long channelId = (Long) args[1];
            if (channelId == 0) {
                return;
            }
            long dialogId = channelId > 0 ? -channelId : channelId;
            deleteMessages(dialogId, (ArrayList<Integer>) args[0]);
        } else if (id == NotificationCenter.historyCleared) {
            if (isUiActive()) {
                return;
            }
            long did = (Long) args[0];
            if (!DialogObject.isChatDialog(did)) {
                return;
            }
            deleteHistory(did, (Integer) args[1]);
        } else if (id == NotificationCenter.didReceiveNewMessages) {
            if (isUiActive() || (Boolean) args[2]) {
                return;
            }
            if (store.isEmpty() || store.getNewestCursor().isEmpty() || !isIncludedChannelPost((Long) args[0])) {
                return;
            }
            scheduleClosedRefresh();
        }
    }

    private void scheduleClosedRefresh() {
        if (closedRefreshScheduled) {
            return;
        }
        closedRefreshScheduled = true;
        AndroidUtilities.runOnUIThread(closedRefreshRunnable, CLOSED_REFRESH_DEBOUNCE_MS);
    }

    private void runClosedRefresh() {
        closedRefreshScheduled = false;
        if (isUiActive() || loadingNewer || store.isEmpty() || store.getNewestCursor().isEmpty()) {
            return;
        }
        loadNewer(closedRefreshGuid, 0);
    }
}
