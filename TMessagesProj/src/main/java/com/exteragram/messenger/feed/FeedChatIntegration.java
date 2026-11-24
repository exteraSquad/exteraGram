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

import com.exteragram.messenger.feed.ads.FeedAdController;
import com.exteragram.messenger.feed.ads.FeedAdInjector;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_update;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

import java.util.ArrayList;
import java.util.HashSet;

public class FeedChatIntegration {

    public interface Host {
        ArrayList<MessageObject> getMessages();

        boolean isListReady();

        void notifyMessageRemoved(int messageIndex);

        void notifyMessageInserted(int messageIndex);

        void notifyAllMessagesChanged();

        void scrollToMessage(int messageIndex, int offsetPx);

        void scrollToMessageAnimated(int messageIndex, int offsetPx);

        int getLastVisibleMessageIndex();

        int getNewestVisibleMessageIndex();

        boolean canScrollToNewer();

        int getDistanceToNewerPx();

        boolean isListScrollIdle();

        boolean isScrollAnimationRunning();

        void setPagedownCount(int count);

        void setPagedownButtonVisible(boolean visible);

        boolean isPagedownButtonVisible();

        void invalidateVisiblePart();

        int nextStableId();

        boolean isFirstLoadComplete();

        void reloadFeed();

        void requestOlderFeedPage();

        ScrollAnchor captureScrollAnchor();

        void restoreScrollAnchor(ScrollAnchor anchor);

        void materializeRow(MessageObject row);

        void dematerializeRow(MessageObject row);

        void deleteRows(ArrayList<Integer> syntheticIds);

        int stableIdForDateHeader(int dateKeyInt);

        void onFeedListChanged();

        void showEmptyFeedState();

        void showEmptyFeedProgress();

        BaseFragment getFragment();
    }

    public static final class ScrollAnchor {
        public final MessageObject row;
        public final int offsetTop;

        public ScrollAnchor(MessageObject row, int offsetTop) {
            this.row = row;
            this.offsetTop = offsetTop;
        }
    }

    private final int currentAccount;
    private final Host host;
    private final boolean restoreDrawerScrollPosition;
    private boolean restoreSavedScrollPosition;

    private static final int PAGEDOWN_SCROLL_THRESHOLD = AndroidUtilities.dp(100);
    private static final int NEAR_NEWEST_THRESHOLD = AndroidUtilities.dp(160);
    private static final long REACTIONS_REFRESH_INTERVAL_MS = 15_000L;

    private boolean viewportActive;
    private boolean initialScrollApplied;
    private boolean readyToMarkAsRead;
    private boolean pendingDividerScroll;
    private ScrollAnchor pendingInitialScrollRestore;
    private boolean scrollPreservedNewerToUnread;
    private int preserveScrollLoadIndex = -1;
    private MessageObject unreadDivider;
    private int lastPagedownCount = -1;
    private boolean pagedownShownByScroll;
    private int totalScrollDy;
    private Runnable channelsChangedCallback;

    private long pendingHideDialogId;

    private boolean settleAtNewestScheduled;
    private boolean destroyed;
    private final Runnable settleAtNewestRunnable = this::settleAtNewestNow;
    private final int reactionsRequestGuid = ConnectionsManager.generateClassGuid();
    private final LongSparseArray<ArrayList<Integer>> pendingReactionIds = new LongSparseArray<>();
    private boolean reactionsRefreshScheduled;
    private final Runnable reactionsRefreshRunnable = this::flushReactionsRefresh;

    private void settleAtNewestNow() {
        settleAtNewestScheduled = false;
        if (!destroyed && viewportActive && host.isListReady()
                && !host.isScrollAnimationRunning() && !host.canScrollToNewer()) {
            settleUnreadDivider();
        }
    }

    private final FeedAdInjector adInjector;

    public FeedChatIntegration(int currentAccount, Host host, boolean restoreDrawerScrollPosition) {
        this.currentAccount = currentAccount;
        this.host = host;
        this.restoreDrawerScrollPosition = restoreDrawerScrollPosition;
        this.adInjector = new FeedAdInjector(currentAccount, host);
    }

    public void refreshAds() {
        adInjector.refresh(unreadDivider);
        requestPendingInitialPosition();
    }

    public void setChannelsChangedCallback(Runnable callback) {
        channelsChangedCallback = callback;
    }

    public void notifyChannelsChanged() {
        if (channelsChangedCallback != null) {
            channelsChangedCallback.run();
        }
    }

    public void resetUiState() {
        resetUiState(false);
    }

    public void resetUiState(boolean restoreSavedScrollPosition) {
        this.restoreSavedScrollPosition = restoreSavedScrollPosition;
        resetMetadataRefresh();
        initialScrollApplied = false;
        readyToMarkAsRead = false;
        pendingDividerScroll = false;
        pendingInitialScrollRestore = null;
        scrollPreservedNewerToUnread = false;
        preserveScrollLoadIndex = -1;
        unreadDivider = null;
        lastPagedownCount = -1;
        pagedownShownByScroll = false;
        totalScrollDy = 0;
        pendingHideDialogId = 0;
        if (settleAtNewestScheduled) {
            AndroidUtilities.cancelRunOnUIThread(settleAtNewestRunnable);
            settleAtNewestScheduled = false;
        }
        adInjector.clear();
    }

    private boolean hasMaterializedPostRows() {
        ArrayList<MessageObject> messages = host.getMessages();
        for (int i = 0; i < messages.size(); i++) {
            if (FeedMessageUtils.isPostRow(messages.get(i))) {
                return true;
            }
        }
        return false;
    }

    public void onMessagesLoaded() {
        if (host.getFragment().isPaused()) {
            return;
        }
        if (!host.isListReady() || !hasMaterializedPostRows()) {
            return;
        }
        if (!initialScrollApplied) {
            initialScrollApplied = true;
            FeedController feedController = FeedController.getInstance(currentAccount);
            boolean initialUnreadScroll = feedController.consumeInitialUnreadScroll();
            FeedController.SavedScrollPosition savedPosition = restoreDrawerScrollPosition || restoreSavedScrollPosition
                    ? feedController.getSavedScrollPosition() : null;
            restoreSavedScrollPosition = false;
            MessageObject savedRow = savedPosition == null ? null
                    : feedController.getMessage(savedPosition.dialogId, savedPosition.messageId);
            if (savedRow != null && host.getMessages().contains(savedRow)) {
                applyUnreadDivider(false);
                refreshAds();
                pendingInitialScrollRestore = new ScrollAnchor(savedRow, savedPosition.offsetTop);
                requestPendingInitialPosition();
            } else {
                boolean scrollToDivider = initialUnreadScroll;
                if (!scrollToDivider && host.getDistanceToNewerPx() <= NEAR_NEWEST_THRESHOLD) {
                    scrollToDivider = true;
                }
                applyUnreadDivider(scrollToDivider);
            }
        }
        FeedAdController.getInstance(currentAccount).ensureLoaded(this::refreshAds);
    }

    public void onHostResumed() {
        if (!host.getMessages().isEmpty()) {
            onMessagesLoaded();
        }
        requestPendingInitialPosition();
    }

    private boolean hasPendingInitialPosition() {
        return pendingInitialScrollRestore != null || pendingDividerScroll;
    }

    private void requestPendingInitialPosition() {
        if (host.getFragment().isPaused() || !host.isListReady()) {
            return;
        }
        if (pendingInitialScrollRestore != null) {
            host.restoreScrollAnchor(pendingInitialScrollRestore);
            return;
        }
        if (!pendingDividerScroll) {
            return;
        }
        int index = unreadDivider == null ? -1 : host.getMessages().indexOf(unreadDivider);
        if (index < 0) {
            pendingDividerScroll = false;
            readyToMarkAsRead = true;
            return;
        }
        host.scrollToMessage(index, AndroidUtilities.dp(48));
    }

    public void setViewportActive(boolean active) {
        if (viewportActive == active) {
            return;
        }
        viewportActive = active;
        if (!active) {
            if (settleAtNewestScheduled) {
                AndroidUtilities.cancelRunOnUIThread(settleAtNewestRunnable);
                settleAtNewestScheduled = false;
            }
            cancelPendingReactionsRefresh();
            return;
        }
        onHostResumed();
        if (!host.getMessages().isEmpty()) {
            onVisiblePartInvalidated();
        }
    }

    public boolean canMarkVisibleAsRead() {
        return viewportActive && !host.getFragment().isPaused()
                && initialScrollApplied && readyToMarkAsRead
                && !pendingDividerScroll && pendingInitialScrollRestore == null
                && !BaseFragment.hasSheets(host.getFragment());
    }

    public void onPostCellVisible(MessageObject messageObject, boolean scrolledPast, boolean atNewest) {
        if (messageObject == null || messageObject.isSponsored()) {
            return;
        }
        requestReactionsRefresh(messageObject);
        if (!canMarkVisibleAsRead() || !scrolledPast && !atNewest) {
            return;
        }
        FeedController.getInstance(currentAccount).onPostSeen(messageObject);
    }

    private void requestReactionsRefresh(MessageObject messageObject) {
        if (destroyed || !viewportActive || messageObject.messageOwner == null) {
            return;
        }
        int realId = messageObject.getRealId();
        long dialogId = messageObject.getDialogId();
        if (realId <= 0 || dialogId == 0) {
            return;
        }
        if (messageObject.messageOwner.action != null && !messageObject.canSetReaction()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - messageObject.reactionsLastCheckTime <= REACTIONS_REFRESH_INTERVAL_MS) {
            return;
        }
        messageObject.reactionsLastCheckTime = now;
        ArrayList<Integer> messageIds = pendingReactionIds.get(dialogId);
        if (messageIds == null) {
            messageIds = new ArrayList<>();
            pendingReactionIds.put(dialogId, messageIds);
        }
        messageIds.add(realId);
        if (!reactionsRefreshScheduled) {
            reactionsRefreshScheduled = true;
            AndroidUtilities.runOnUIThread(reactionsRefreshRunnable);
        }
    }

    private void flushReactionsRefresh() {
        reactionsRefreshScheduled = false;
        if (destroyed || !viewportActive) {
            pendingReactionIds.clear();
            return;
        }
        for (int i = 0; i < pendingReactionIds.size(); i++) {
            TLRPC.TL_messages_getMessagesReactions req = new TLRPC.TL_messages_getMessagesReactions();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(pendingReactionIds.keyAt(i));
            req.id.addAll(pendingReactionIds.valueAt(i));
            int requestId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                if (!(response instanceof TLRPC.Updates updates)) {
                    return;
                }
                for (int j = 0; j < updates.updates.size(); j++) {
                    if (updates.updates.get(j) instanceof TL_update.TL_updateMessageReactions update) {
                        update.updateUnreadState = false;
                    }
                }
                MessagesController.getInstance(currentAccount).processUpdates(updates, false);
            });
            ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(requestId, reactionsRequestGuid);
        }
        pendingReactionIds.clear();
    }

    private void cancelPendingReactionsRefresh() {
        AndroidUtilities.cancelRunOnUIThread(reactionsRefreshRunnable);
        reactionsRefreshScheduled = false;
        pendingReactionIds.clear();
    }

    private void resetMetadataRefresh() {
        cancelPendingReactionsRefresh();
        ConnectionsManager.getInstance(currentAccount).cancelRequestsForGuid(reactionsRequestGuid);
    }

    public void markAllRead() {
        FeedController.getInstance(currentAccount).markAllRead();
        applyUnreadDivider(false);
        refreshAds();
        host.invalidateVisiblePart();
    }

    public void settleUnreadDivider() {
        if (!canMarkVisibleAsRead() || !host.isListReady()) {
            return;
        }
        int topVisibleIndex = host.getLastVisibleMessageIndex();
        if (topVisibleIndex == Integer.MIN_VALUE) {
            return;
        }
        ArrayList<MessageObject> messages = host.getMessages();
        int limit = host.canScrollToNewer()
                ? Math.min(topVisibleIndex, messages.size() - 1) : messages.size() - 1;
        if (limit >= 0) {
            FeedController feedController = FeedController.getInstance(currentAccount);
            for (int i = 0; i <= limit; i++) {
                MessageObject msg = messages.get(i);
                if (msg == null || msg.isDateObject || msg.type == MessageObject.TYPE_LOADING || msg.isSponsored()) {
                    continue;
                }
                feedController.onPostSeen(msg);
            }
        }
        applyUnreadDivider(false);
        refreshAds();
        updatePagedownCounter();
    }

    public void onScrollAnimationFinished() {
        if (!destroyed && viewportActive && !settleAtNewestScheduled) {
            settleAtNewestScheduled = true;
            AndroidUtilities.runOnUIThread(settleAtNewestRunnable);
        }
    }

    public void destroy() {
        destroyed = true;
        resetMetadataRefresh();
        if (settleAtNewestScheduled) {
            AndroidUtilities.cancelRunOnUIThread(settleAtNewestRunnable);
            settleAtNewestScheduled = false;
        }
        pendingInitialScrollRestore = null;
    }

    public void saveScrollPosition(int timelineId) {
        if (!host.isListReady()) {
            return;
        }
        ScrollAnchor anchor = host.captureScrollAnchor();
        if (anchor != null && anchor.row != null) {
            FeedController.getInstance(currentAccount).saveScrollPosition(timelineId,
                    anchor.row.getDialogId(), anchor.row.getRealId(), anchor.offsetTop);
        }
    }

    public void applyUnreadDivider() {
        applyUnreadDivider(true);
    }

    public void onReadStateRefreshed() {
        ScrollAnchor anchor = host.captureScrollAnchor();
        boolean preservePendingInitialPosition = hasPendingInitialPosition();
        applyUnreadDivider(false);
        refreshAds();
        if (!preservePendingInitialPosition || !hasPendingInitialPosition()) {
            host.restoreScrollAnchor(anchor);
        }
        lastPagedownCount = -1;
        updatePagedownCounter();
        host.invalidateVisiblePart();
    }

    public void onPreserveScrollLoadStarted(int loadIndex) {
        preserveScrollLoadIndex = loadIndex;
    }

    public boolean consumePreserveScrollLoad(int loadIndex) {
        if (preserveScrollLoadIndex != loadIndex) {
            return false;
        }
        preserveScrollLoadIndex = -1;
        return true;
    }

    public void beforePreservedNewerMessagesInserted() {
        scrollPreservedNewerToUnread = host.isListScrollIdle()
                && !host.isScrollAnimationRunning()
                && host.getDistanceToNewerPx() <= NEAR_NEWEST_THRESHOLD;
    }

    public boolean afterPreservedNewerMessagesInserted() {
        boolean scrollToUnread = scrollPreservedNewerToUnread;
        applyUnreadDivider(scrollToUnread, true);
        scrollPreservedNewerToUnread = false;
        return scrollToUnread;
    }

    public void applyUnreadDivider(boolean scrollToDivider) {
        applyUnreadDivider(scrollToDivider, false);
    }

    private void applyUnreadDivider(boolean scrollToDivider, boolean itemizeScrollChanges) {
        if (!host.isListReady()) {
            return;
        }
        ArrayList<MessageObject> messages = host.getMessages();
        if (messages.isEmpty()) {
            unreadDivider = null;
            readyToMarkAsRead = false;
            return;
        }
        int oldIndex = unreadDivider == null ? -1 : messages.indexOf(unreadDivider);
        MessageObject existing = oldIndex >= 0 ? unreadDivider : null;
        if (oldIndex >= 0) {
            messages.remove(oldIndex);
        }
        unreadDivider = null;

        int index = FeedController.getInstance(currentAccount).findFirstUnreadIndex(messages);
        if (index < 0) {
            pendingDividerScroll = false;
            if (oldIndex >= 0) {
                if (scrollToDivider && !itemizeScrollChanges) {
                    host.notifyAllMessagesChanged();
                } else {
                    host.notifyMessageRemoved(oldIndex);
                    host.invalidateVisiblePart();
                }
            }
            readyToMarkAsRead = true;
            return;
        }
        index = findDividerInsertIndex(messages, index);
        MessageObject divider = existing != null ? existing : FeedMessageUtils.createUnreadDivider(currentAccount, host.nextStableId());
        messages.add(index, divider);
        unreadDivider = divider;

        if (scrollToDivider) {
            readyToMarkAsRead = false;
            if (itemizeScrollChanges) {
                if (oldIndex < 0) {
                    host.notifyMessageInserted(index);
                } else if (oldIndex != index) {
                    host.notifyMessageRemoved(oldIndex);
                    host.notifyMessageInserted(index);
                }
            } else {
                host.notifyAllMessagesChanged();
            }
            pendingDividerScroll = true;
            requestPendingInitialPosition();
            host.invalidateVisiblePart();
            return;
        }
        readyToMarkAsRead = true;
        if (oldIndex < 0) {
            host.notifyMessageInserted(index);
            host.invalidateVisiblePart();
        } else if (oldIndex != index) {
            host.notifyMessageRemoved(oldIndex);
            host.notifyMessageInserted(index);
            host.invalidateVisiblePart();
        }
    }

    public boolean scrollToUnreadDividerIfAbove() {
        if (!host.isListReady()) {
            return false;
        }
        ArrayList<MessageObject> messages = host.getMessages();
        if (unreadDivider == null || !messages.contains(unreadDivider)) {
            applyUnreadDivider(false);
            refreshAds();
            messages = host.getMessages();
        }
        int dividerIndex = messages.indexOf(unreadDivider);
        if (dividerIndex < 0) {
            return false;
        }
        int newestVisibleIndex = host.getNewestVisibleMessageIndex();
        if (newestVisibleIndex == Integer.MIN_VALUE || dividerIndex >= newestVisibleIndex) {
            return false;
        }
        host.scrollToMessageAnimated(dividerIndex, AndroidUtilities.dp(48));
        host.invalidateVisiblePart();
        return true;
    }

    private static int findDividerInsertIndex(ArrayList<MessageObject> messages, int index) {
        MessageObject messageObject = messages.get(index);
        long groupId = messageObject.getGroupId();
        if (groupId == 0) {
            return index + 1;
        }
        long dialogId = messageObject.getDialogId();
        int insertIndex = index + 1;
        while (insertIndex < messages.size()) {
            MessageObject groupedMessage = messages.get(insertIndex);
            if (groupedMessage == null || groupedMessage.getGroupId() != groupId || groupedMessage.getDialogId() != dialogId) {
                break;
            }
            insertIndex++;
        }
        return insertIndex;
    }

    public void onVisiblePartInvalidated() {
        if (!viewportActive || host.getFragment().isPaused()) {
            return;
        }
        if (pendingInitialScrollRestore != null) {
            host.restoreScrollAnchor(pendingInitialScrollRestore);
            pendingInitialScrollRestore = null;
        }
        maybeScrollToDivider();
        updatePagedownCounter();
    }

    private void maybeScrollToDivider() {
        if (!pendingDividerScroll) {
            return;
        }
        if (!host.isListReady() || unreadDivider == null) {
            pendingDividerScroll = false;
            readyToMarkAsRead = true;
            return;
        }
        int lastVisible = host.getLastVisibleMessageIndex();
        if (lastVisible == Integer.MIN_VALUE) {
            return;
        }
        int targetIndex = host.getMessages().indexOf(unreadDivider);
        if (targetIndex >= 0 && targetIndex > lastVisible) {
            host.scrollToMessage(targetIndex, AndroidUtilities.dp(48));
        }
        pendingDividerScroll = false;
        readyToMarkAsRead = true;
    }

    private void updatePagedownCounter() {
        if (!host.isListReady()) {
            return;
        }
        if (host.isScrollAnimationRunning()) {
            return;
        }
        int newestVisibleIndex = host.getNewestVisibleMessageIndex();
        int count = newestVisibleIndex == Integer.MIN_VALUE ? 0
                : FeedController.getInstance(currentAccount).countUnreadBelow(host.getMessages(), newestVisibleIndex);
        if (count != lastPagedownCount) {
            lastPagedownCount = count;
            host.setPagedownCount(count);
        }
        if (count > 0) {
            pagedownShownByScroll = false;
            host.setPagedownButtonVisible(true);
        } else if (!host.canScrollToNewer()) {
            pagedownShownByScroll = false;
            host.setPagedownButtonVisible(false);
        }
    }

    public void onScrolled(int dy) {
        if (!viewportActive || !host.isListReady() || host.isScrollAnimationRunning()) {
            return;
        }
        if (!host.canScrollToNewer()) {
            totalScrollDy = 0;
            pagedownShownByScroll = false;
            host.setPagedownButtonVisible(false);
            if (dy > 0 && !settleAtNewestScheduled) {
                settleAtNewestScheduled = true;
                AndroidUtilities.runOnUIThread(settleAtNewestRunnable);
            }
            return;
        }
        if (lastPagedownCount > 0) {
            return;
        }
        boolean visible = host.isPagedownButtonVisible();
        if (dy > 0) {
            if (!visible) {
                totalScrollDy += dy;
                if (totalScrollDy > PAGEDOWN_SCROLL_THRESHOLD) {
                    totalScrollDy = 0;
                    pagedownShownByScroll = true;
                    host.setPagedownButtonVisible(true);
                }
            }
        } else if (dy < 0) {
            if (pagedownShownByScroll && visible) {
                totalScrollDy += dy;
                if (totalScrollDy < -PAGEDOWN_SCROLL_THRESHOLD) {
                    totalScrollDy = 0;
                    host.setPagedownButtonVisible(false);
                }
            }
        }
    }

    public void onMessagesDeleted() {
        if (unreadDivider == null) {
            return;
        }
        ArrayList<MessageObject> messages = host.getMessages();
        int dividerIndex = messages.indexOf(unreadDivider);
        boolean hasNewerMessage = false;
        for (int i = 0; i < dividerIndex; i++) {
            MessageObject m = messages.get(i);
            if (FeedMessageUtils.isPostRow(m)) {
                hasNewerMessage = true;
                break;
            }
        }
        if (!hasNewerMessage) {
            unreadDivider = null;
            if (dividerIndex >= 0 && host.isListReady()) {
                messages.remove(dividerIndex);
                host.notifyMessageRemoved(dividerIndex);
            }
        }
    }

    public void loadReplyMessages(ArrayList<MessageObject> feedMessages, int chatMode, int classGuid) {
        if (feedMessages == null || feedMessages.isEmpty()) {
            return;
        }
        LongSparseArray<ArrayList<MessageObject>> byDialog = new LongSparseArray<>();
        for (int i = 0; i < feedMessages.size(); i++) {
            MessageObject messageObject = feedMessages.get(i);
            if (messageObject == null || messageObject.isDateObject) {
                continue;
            }
            long did = messageObject.getDialogId();
            if (did == 0) {
                continue;
            }
            ArrayList<MessageObject> group = byDialog.get(did);
            if (group == null) {
                group = new ArrayList<>();
                byDialog.put(did, group);
            }
            group.add(messageObject);
        }
        for (int i = 0; i < byDialog.size(); i++) {
            MediaDataController.getInstance(currentAccount).loadReplyMessagesForMessages(byDialog.valueAt(i), byDialog.keyAt(i), chatMode, 0, null, classGuid, null);
        }
    }

    public void reconcileWithStore() {
        if (!host.isListReady()) {
            return;
        }
        FeedStore store = FeedController.getInstance(currentAccount).getStore();
        ArrayList<MessageObject> messages = host.getMessages();
        int localPostCount = 0;
        for (int i = 0; i < messages.size(); i++) {
            if (FeedMessageUtils.isPostRow(messages.get(i))) {
                localPostCount++;
            }
        }
        ArrayList<MessageObject> target = store.getVisibleMessages();
        if (localPostCount == 0) {
            if (!target.isEmpty() && host.isFirstLoadComplete()) {
                host.reloadFeed();
            } else if (!store.isEmpty() && !store.isEndReached()) {
                host.requestOlderFeedPage();
            }
            return;
        }
        if (store.isEmpty()) {
            host.reloadFeed();
            return;
        }
        HashSet<MessageObject> targetSet = new HashSet<>(target);

        ArrayList<Integer> purgedIds = null;
        ArrayList<MessageObject> hiddenRows = null;
        for (int i = 0; i < messages.size(); i++) {
            MessageObject row = messages.get(i);
            if (!FeedMessageUtils.isPostRow(row) || targetSet.contains(row)) {
                continue;
            }
            if (store.getMessage(row.getDialogId(), row.getId()) == row) {
                if (hiddenRows == null) {
                    hiddenRows = new ArrayList<>();
                }
                hiddenRows.add(row);
            } else {
                if (purgedIds == null) {
                    purgedIds = new ArrayList<>();
                }
                purgedIds.add(row.getId());
            }
        }
        int removals = (purgedIds == null ? 0 : purgedIds.size()) + (hiddenRows == null ? 0 : hiddenRows.size());
        if (removals == 0 && localPostCount == target.size()) {
            return;
        }

        ScrollAnchor anchor = host.captureScrollAnchor();
        boolean preservePendingInitialPosition = hasPendingInitialPosition();
        if (purgedIds != null) {
            host.deleteRows(purgedIds);
        }
        if (hiddenRows != null) {
            for (int a = 0; a < hiddenRows.size(); a++) {
                int index = messages.indexOf(hiddenRows.get(a));
                if (index >= 0) {
                    messages.remove(index);
                    host.dematerializeRow(hiddenRows.get(a));
                    host.notifyMessageRemoved(index);
                }
            }
        }

        HashSet<MessageObject> localSet = new HashSet<>();
        for (int i = 0; i < messages.size(); i++) {
            if (FeedMessageUtils.isPostRow(messages.get(i))) {
                localSet.add(messages.get(i));
            }
        }
        boolean inserted = false;
        int localIndex = 0;
        for (int t = 0; t < target.size(); t++) {
            MessageObject row = target.get(t);
            if (localSet.contains(row)) {
                while (localIndex < messages.size() && messages.get(localIndex) != row) {
                    localIndex++;
                }
                if (localIndex < messages.size()) {
                    localIndex++;
                }
                continue;
            }
            host.materializeRow(row);
            int insertIndex = getInsertIndex(messages, row, target, t, localIndex);
            messages.add(insertIndex, row);
            host.notifyMessageInserted(insertIndex);
            localIndex = insertIndex + 1;
            inserted = true;
        }

        boolean changed = removals > 0 || inserted;
        changed |= normalizeDateHeaders(messages);
        if (!changed) {
            return;
        }
        host.onFeedListChanged();
        applyUnreadDivider(false);
        refreshAds();
        onFeedExclusionsChanged();
        if (!preservePendingInitialPosition || !hasPendingInitialPosition()) {
            host.restoreScrollAnchor(anchor);
        }
        host.invalidateVisiblePart();
        if (store.getVisibleCount() == 0) {
            if (store.isEndReached()) {
                host.showEmptyFeedState();
            } else {
                host.showEmptyFeedProgress();
                host.requestOlderFeedPage();
            }
        }
    }

    private static int getInsertIndex(ArrayList<MessageObject> messages, MessageObject row, ArrayList<MessageObject> target, int targetIndex, int localIndex) {
        int index = Math.min(localIndex, messages.size());
        if (targetIndex > 0 && index < messages.size()) {
            MessageObject previousTarget = target.get(targetIndex - 1);
            MessageObject item = messages.get(index);
            if (previousTarget != null && item != null && item.isDateObject
                    && previousTarget.dateKeyInt != row.dateKeyInt
                    && item.dateKeyInt == previousTarget.dateKeyInt) {
                index++;
            }
        }
        return index;
    }

    private boolean normalizeDateHeaders(ArrayList<MessageObject> messages) {
        boolean changed = false;
        boolean seenPostsSinceHeader = false;
        MessageObject lastPost = null;
        int i = 0;
        while (i < messages.size()) {
            MessageObject item = messages.get(i);
            if (item == null || item.type == MessageObject.TYPE_LOADING || item.isSponsored()) {
                i++;
                continue;
            }
            if (item.isDateObject) {
                if (!seenPostsSinceHeader) {
                    messages.remove(i);
                    host.notifyMessageRemoved(i);
                    changed = true;
                    continue;
                }
                if (lastPost.dateKeyInt != item.dateKeyInt) {
                    messages.add(i, createDateHeader(lastPost));
                    host.notifyMessageInserted(i);
                    changed = true;
                    seenPostsSinceHeader = false;
                    lastPost = null;
                    i++;
                    continue;
                }
                seenPostsSinceHeader = false;
                lastPost = null;
                i++;
                continue;
            }
            if (lastPost != null && lastPost.dateKeyInt != item.dateKeyInt) {
                messages.add(i, createDateHeader(lastPost));
                host.notifyMessageInserted(i);
                changed = true;
                seenPostsSinceHeader = false;
                lastPost = null;
                i++;
                continue;
            }
            seenPostsSinceHeader = true;
            lastPost = item;
            i++;
        }
        if (seenPostsSinceHeader) {
            messages.add(createDateHeader(lastPost));
            host.notifyMessageInserted(messages.size() - 1);
            changed = true;
        }
        return changed;
    }

    private MessageObject createDateHeader(MessageObject baseMsg) {
        return FeedMessageUtils.createDateHeader(currentAccount, baseMsg, host.stableIdForDateHeader(baseMsg.dateKeyInt));
    }

    public ArrayList<Integer> collectLocalRowIds(long dialogId, ArrayList<Integer> realIds, int maxId) {
        ArrayList<Integer> ids = new ArrayList<>();
        HashSet<Integer> realIdSet = realIds != null ? new HashSet<>(realIds) : null;
        ArrayList<MessageObject> messages = host.getMessages();
        for (int i = 0; i < messages.size(); i++) {
            MessageObject row = messages.get(i);
            if (!FeedMessageUtils.isPostRow(row) || row.getDialogId() != dialogId) {
                continue;
            }
            int realId = row.getRealId();
            boolean match = realIdSet != null ? realIdSet.contains(realId) : realId > 0 && realId <= maxId;
            if (match) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    public static void mergeDeletedIds(ArrayList<Integer> ids, ArrayList<Integer> more) {
        for (int i = 0; i < more.size(); i++) {
            if (!ids.contains(more.get(i))) {
                ids.add(more.get(i));
            }
        }
    }

    public void hideChannelWithUndo(long dialogId, CharSequence title) {
        FeedConfig config = FeedConfig.getInstance(currentAccount);
        FeedController controller = FeedController.getInstance(currentAccount);
        config.setExcluded(dialogId, true);
        controller.markConfigApplied();
        controller.getStore().setHidden(dialogId, true);
        pendingHideDialogId = dialogId;
        reconcileWithStore();
        onFeedExclusionsChanged();
        notifyChannelsChanged();

        final long undoDialogId = dialogId;
        BulletinFactory.of(host.getFragment()).createUndoBulletin(
                AndroidUtilities.replaceTags(LocaleController.formatString(R.string.FeedChannelHidden, title)),
                this::undoHideChannel,
                () -> {
                    if (pendingHideDialogId == undoDialogId) {
                        pendingHideDialogId = 0;
                    }
                }
        ).show();
    }

    private void undoHideChannel() {
        if (pendingHideDialogId == 0) {
            return;
        }
        long dialogId = pendingHideDialogId;
        pendingHideDialogId = 0;

        FeedController controller = FeedController.getInstance(currentAccount);
        FeedConfig.getInstance(currentAccount).setExcluded(dialogId, false);
        controller.markConfigApplied();
        controller.getStore().setHidden(dialogId, false);
        reconcileWithStore();
        onFeedExclusionsChanged();
        notifyChannelsChanged();
    }

    public void onFeedExclusionsChanged() {
        lastPagedownCount = -1;
        updatePagedownCounter();
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_READ_DIALOG_MESSAGE);
    }
}
