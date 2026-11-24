/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.feed;

import android.text.TextUtils;

import androidx.collection.LongSparseArray;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

final class FeedTimelineLoader {

    static final int PAGE_SIZE = 30;
    private static final int NEWER_LIMIT = 50;
    private static final int WINDOW_LIMIT = 500;
    private static final int DEPTH_CHANNEL_BATCH_SIZE = 64;
    private static final int UNREAD_BOUNDARY_MAX_MESSAGES = 200;
    private static final int MAX_ENUMERATION_REBUILDS = 3;

    private final int currentAccount;
    private final AtomicInteger channelCacheEpoch = new AtomicInteger();
    private volatile ChannelSet channelSetCache;

    FeedTimelineLoader(int currentAccount) {
        this.currentAccount = currentAccount;
    }

    static final class Cursor {
        int date;
        long uid;
        int mid;

        boolean isEmpty() {
            return date == 0;
        }

        void set(int date, long uid, int mid) {
            this.date = date;
            this.uid = uid;
            this.mid = mid;
        }
    }

    static final class ChannelSnapshot {
        final long dialogId;
        final int readInboxMax;
        final int unreadCount;
        final int topMessage;

        int depthMid;
        int depthDate;
        int holeEnd;
        boolean hasHole;
        boolean hasCached;
        boolean localStartReached;
        boolean incomplete;

        ChannelSnapshot(long dialogId, int readInboxMax, int unreadCount, int topMessage) {
            this.dialogId = dialogId;
            this.readInboxMax = readInboxMax;
            this.unreadCount = unreadCount;
            this.topMessage = topMessage;
        }
    }

    static final class ChannelEnumeration {
        final ArrayList<ChannelSnapshot> included = new ArrayList<>();
        final ArrayList<TLRPC.Chat> channels = new ArrayList<>();
        boolean hasChannels;
        boolean failed;
        int configGeneration;
        int cacheEpoch;
        FeedConfig.Snapshot config;
    }

    static final class OlderPage {
        final ArrayList<TLRPC.Message> messages = new ArrayList<>();
        final ArrayList<TLRPC.User> users = new ArrayList<>();
        final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        final ArrayList<long[]> backfillCandidates = new ArrayList<>();
        final ArrayList<long[]> unreadBackfillCandidates = new ArrayList<>();
        final HashMap<Long, Cursor> unreadStarts = new HashMap<>();
        final HashSet<Long> unreadStartUnknown = new HashSet<>();
        boolean hasIncomplete;
        boolean failed;
        int lastChunkRowCount;
        final Cursor last = new Cursor();
        final Cursor first = new Cursor();
    }

    static final class NewerPage {
        final ArrayList<TLRPC.Message> messages = new ArrayList<>();
        final ArrayList<TLRPC.User> users = new ArrayList<>();
        final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        final Cursor first = new Cursor();
        boolean hasMore;
        boolean failed;
    }

    static final class WindowPage {
        final ArrayList<TLRPC.Message> messages = new ArrayList<>();
        final ArrayList<TLRPC.User> users = new ArrayList<>();
        final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        boolean truncated;
        boolean failed;
    }

    private static final class ChannelSet {
        final ArrayList<long[]> channelRows = new ArrayList<>();
        final ArrayList<TLRPC.Chat> channels = new ArrayList<>();
        boolean hasChannels;
        boolean failed;
        final int cacheSession;
        final int baseGen;

        ChannelSet(int cacheSession, int baseGen) {
            this.cacheSession = cacheSession;
            this.baseGen = baseGen;
        }
    }

    synchronized void invalidateChannelCache() {
        channelCacheEpoch.incrementAndGet();
        channelSetCache = null;
    }

    ChannelEnumeration enumerateChannels(FeedConfig config, int cacheSession, boolean forceRefresh) {
        ChannelSet set;
        int cacheEpoch;
        FeedConfig.Snapshot snapshot;
        boolean refresh = forceRefresh;
        for (int attempt = 0; ; attempt++) {
            synchronized (this) {
                set = channelSetCache;
                cacheEpoch = channelCacheEpoch.get();
            }
            snapshot = config.snapshot();
            if (refresh || set == null || set.cacheSession != cacheSession || set.baseGen != snapshot.getBaseGeneration()) {
                set = buildChannelSet(snapshot.getIncludeArchived(), cacheSession, snapshot.getBaseGeneration());
                if (!set.failed) {
                    synchronized (this) {
                        if (cacheEpoch == channelCacheEpoch.get()) {
                            channelSetCache = set;
                        }
                    }
                }
            }
            if (set.failed || attempt >= MAX_ENUMERATION_REBUILDS) {
                break;
            }
            synchronized (this) {
                if (cacheEpoch == channelCacheEpoch.get()) {
                    break;
                }
            }
            refresh = true;
        }
        ChannelEnumeration result = new ChannelEnumeration();
        result.hasChannels = set.hasChannels;
        result.failed = set.failed;
        result.config = snapshot;
        result.configGeneration = snapshot.getGeneration();
        result.cacheEpoch = cacheEpoch;
        result.channels.addAll(set.channels);
        for (int i = 0; i < set.channelRows.size(); i++) {
            long[] row = set.channelRows.get(i);
            if (!snapshot.isIncluded(row[0])) {
                continue;
            }
            result.included.add(new ChannelSnapshot(row[0], (int) row[1], (int) row[2], (int) row[3]));
        }
        return result;
    }

    synchronized int getChannelCacheEpoch() {
        return channelCacheEpoch.get();
    }

    synchronized boolean isEnumerationCurrent(ChannelEnumeration enumeration) {
        return enumeration != null && enumeration.cacheEpoch == channelCacheEpoch.get();
    }

    private ChannelSet buildChannelSet(boolean includeArchived, int cacheSession, int baseGen) {
        MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
        ChannelSet set = new ChannelSet(cacheSession, baseGen);
        ArrayList<long[]> rows = new ArrayList<>();
        ArrayList<Long> chatIds = new ArrayList<>();
        try {
            SQLiteCursor cursor = storage.getDatabase().queryFinalized("SELECT did, inbox_max, unread_count, last_mid, folder_id FROM dialogs WHERE did < 0");
            while (cursor.next()) {
                long did = cursor.longValue(0);
                if (!DialogObject.isChatDialog(did)) {
                    continue;
                }
                rows.add(new long[]{did, cursor.intValue(1), cursor.intValue(2), cursor.intValue(3), cursor.intValue(4)});
                chatIds.add(-did);
            }
            cursor.dispose();
        } catch (Exception e) {
            FileLog.e(e);
            set.failed = true;
            return set;
        }
        if (rows.isEmpty()) {
            return set;
        }
        ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        try {
            storage.getChatsInternal(TextUtils.join(",", chatIds), chats);
        } catch (Exception e) {
            FileLog.e(e);
            set.failed = true;
            return set;
        }
        LongSparseArray<TLRPC.Chat> chatById = new LongSparseArray<>();
        for (int i = 0; i < chats.size(); i++) {
            chatById.put(chats.get(i).id, chats.get(i));
        }
        for (int i = 0; i < rows.size(); i++) {
            long[] row = rows.get(i);
            long did = row[0];
            TLRPC.Chat chat = chatById.get(-did);
            if (!FeedController.isEligibleChannel(chat)) {
                continue;
            }
            if (row[4] == 1 && !includeArchived) {
                continue;
            }
            set.hasChannels = true;
            set.channels.add(chat);
            set.channelRows.add(new long[]{did, row[1], row[2], row[3]});
        }
        return set;
    }

    OlderPage loadOlderPage(ArrayList<ChannelSnapshot> channels, Cursor cursor, HashSet<Long> backfillExhausted) {
        OlderPage page = new OlderPage();
        boolean firstPage = cursor.isEmpty();
        page.last.set(cursor.date, cursor.uid, cursor.mid);
        try {
            ArrayList<Long> ids = new ArrayList<>(channels.size());
            for (ChannelSnapshot channel : channels) {
                ids.add(channel.dialogId);
            }
            String idList = TextUtils.join(",", ids);
            MessagesStorage storage = MessagesStorage.getInstance(currentAccount);

            HashMap<Long, Integer> holes = new HashMap<>();
            SQLiteCursor holesCursor = storage.getDatabase().queryFinalized("SELECT uid, max(end) FROM messages_holes WHERE uid IN (" + idList + ") GROUP BY uid");
            while (holesCursor.next()) {
                holes.put(holesCursor.longValue(0), holesCursor.intValue(1));
            }
            holesCursor.dispose();
            for (ChannelSnapshot channel : channels) {
                Integer hole = holes.get(channel.dialogId);
                channel.hasHole = hole != null;
                channel.holeEnd = channel.hasHole ? hole : 0;
            }
            loadChannelDepths(storage, channels);

            int safeDate = 0;
            for (ChannelSnapshot channel : channels) {
                channel.incomplete = !(channel.localStartReached || backfillExhausted.contains(channel.dialogId));
                if (channel.incomplete) {
                    page.hasIncomplete = true;
                    safeDate = Math.max(safeDate, channel.depthDate);
                    long offsetMid;
                    if (channel.hasCached) {
                        offsetMid = channel.depthMid;
                    } else {
                        int top = Math.max(channel.holeEnd, channel.topMessage);
                        offsetMid = top > 0 ? top + 1 : 0;
                    }
                    page.backfillCandidates.add(new long[]{channel.dialogId, offsetMid, channel.depthDate});
                }
            }
            page.backfillCandidates.sort((a, b) -> Long.compare(b[2], a[2]));

            Cursor unreadBoundary = loadUnreadStarts(storage, channels, page);
            if (firstPage) {
                collectUnreadBackfillCandidates(page, unreadBoundary, safeDate);
            } else {
                unreadBoundary = null;
            }

            if (safeDate == Integer.MAX_VALUE) {
                return page;
            }
            ArrayList<Long> usersToLoad = new ArrayList<>();
            ArrayList<Long> chatsToLoad = new ArrayList<>();
            int totalRows = 0;
            while (true) {
                page.lastChunkRowCount = loadChunk(storage, idList, safeDate, page, usersToLoad, chatsToLoad);
                totalRows += page.lastChunkRowCount;
                if (page.lastChunkRowCount < PAGE_SIZE || unreadBoundary == null || totalRows >= UNREAD_BOUNDARY_MAX_MESSAGES) {
                    break;
                }
                if (compareDesc(page.last, unreadBoundary) >= 0) {
                    break;
                }
            }
            completeTrailingAlbum(storage, page, usersToLoad, chatsToLoad);
            for (long did : ids) {
                if (!chatsToLoad.contains(-did)) {
                    chatsToLoad.add(-did);
                }
            }
            if (!usersToLoad.isEmpty()) {
                storage.getUsersInternal(usersToLoad, page.users);
            }
            if (!chatsToLoad.isEmpty()) {
                storage.getChatsInternal(TextUtils.join(",", chatsToLoad), page.chats);
            }
        } catch (Exception e) {
            FileLog.e(e);
            page.failed = true;
        }
        clusterGroupedMessages(page.messages);
        return page;
    }

    private int loadChunk(MessagesStorage storage, String idList, int safeDate, OlderPage page, ArrayList<Long> usersToLoad, ArrayList<Long> chatsToLoad) throws Exception {
        StringBuilder query = new StringBuilder("SELECT data, mid, date, uid FROM messages_v2 WHERE uid IN (");
        query.append(idList).append(") AND mid > 0");
        if (safeDate > 0) {
            query.append(" AND date >= ").append(safeDate);
        }
        if (!page.last.isEmpty()) {
            appendCursorBound(query, page.last, true, false);
        }
        query.append(" ORDER BY date DESC, uid DESC, mid DESC LIMIT ").append(PAGE_SIZE);
        int rawCount = 0;
        SQLiteCursor cursor = storage.getDatabase().queryFinalized(query.toString());
        while (cursor.next()) {
            rawCount++;
            page.last.set(cursor.intValue(2), cursor.longValue(3), cursor.intValue(1));
            if (page.first.isEmpty()) {
                page.first.set(page.last.date, page.last.uid, page.last.mid);
            }
            TLRPC.Message message = readMessage(cursor);
            if (message != null) {
                page.messages.add(message);
                MessagesStorage.addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);
            }
        }
        cursor.dispose();
        return rawCount;
    }

    static int getEffectiveReadInboxMax(int readInboxMax, int unreadCount, int topMessage) {
        return readInboxMax <= 0 && unreadCount <= 0 ? topMessage : readInboxMax;
    }

    private Cursor loadUnreadStarts(MessagesStorage storage, ArrayList<ChannelSnapshot> channels, OlderPage page) {
        Cursor boundary = null;
        StringBuilder query = new StringBuilder();
        int termCount = 0;
        for (int i = 0; i < channels.size(); i++) {
            ChannelSnapshot channel = channels.get(i);
            int readMax = getEffectiveReadInboxMax(channel.readInboxMax, channel.unreadCount, channel.topMessage);
            if (channel.topMessage > readMax) {
                if (channel.incomplete && (!channel.hasCached || channel.depthMid > readMax + 1)) {
                    page.unreadStartUnknown.add(channel.dialogId);
                } else {
                    if (query.length() > 0) {
                        query.append(" UNION ALL ");
                    }
                    query.append("SELECT uid, mid, date FROM (SELECT uid, mid, date FROM messages_v2 WHERE uid = ")
                            .append(channel.dialogId)
                            .append(" AND mid > ")
                            .append(readMax)
                            .append(" ORDER BY mid ASC LIMIT 1)");
                    termCount++;
                }
            }
            if (termCount > 0 && (termCount == DEPTH_CHANNEL_BATCH_SIZE || i == channels.size() - 1)) {
                SQLiteCursor cursor = null;
                try {
                    cursor = storage.getDatabase().queryFinalized(query.toString());
                    while (cursor.next()) {
                        Cursor start = new Cursor();
                        start.set(cursor.intValue(2), cursor.longValue(0), cursor.intValue(1));
                        page.unreadStarts.put(start.uid, start);
                        if (boundary == null || compareDesc(start, boundary) > 0) {
                            boundary = start;
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    if (cursor != null) {
                        cursor.dispose();
                    }
                }
                query.setLength(0);
                termCount = 0;
            }
        }
        return boundary;
    }

    private static void collectUnreadBackfillCandidates(OlderPage page, Cursor unreadBoundary, int safeDate) {
        if (!page.unreadStartUnknown.isEmpty()) {
            for (long[] candidate : page.backfillCandidates) {
                if (page.unreadStartUnknown.contains(candidate[0])) {
                    page.unreadBackfillCandidates.add(candidate);
                }
            }
            return;
        }
        if (unreadBoundary == null || unreadBoundary.date >= safeDate) {
            return;
        }
        for (long[] candidate : page.backfillCandidates) {
            if (candidate[2] > unreadBoundary.date) {
                page.unreadBackfillCandidates.add(candidate);
            }
        }
    }

    private static void appendCursorBound(StringBuilder query, Cursor cursor, boolean older, boolean inclusive) {
        String cmp = older ? "<" : ">";
        query.append(" AND (date ").append(cmp).append(' ').append(cursor.date)
                .append(" OR date = ").append(cursor.date)
                .append(" AND (uid ").append(cmp).append(' ').append(cursor.uid)
                .append(" OR uid = ").append(cursor.uid)
                .append(" AND mid ").append(cmp).append(inclusive ? "= " : " ").append(cursor.mid).append("))");
    }

    private static int compareDesc(Cursor a, Cursor b) {
        if (a.date != b.date) {
            return a.date > b.date ? -1 : 1;
        }
        if (a.uid != b.uid) {
            return a.uid > b.uid ? -1 : 1;
        }
        return -Integer.compare(a.mid, b.mid);
    }

    NewerPage loadNewerPage(ArrayList<ChannelSnapshot> channels, Cursor cursor) {
        NewerPage page = new NewerPage();
        page.first.set(cursor.date, cursor.uid, cursor.mid);
        try {
            ArrayList<Long> ids = new ArrayList<>(channels.size());
            for (ChannelSnapshot channel : channels) {
                ids.add(channel.dialogId);
            }
            MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
            ArrayList<Long> usersToLoad = new ArrayList<>();
            ArrayList<Long> chatsToLoad = new ArrayList<>();
            StringBuilder query = new StringBuilder("SELECT data, mid, date, uid FROM messages_v2 WHERE uid IN (");
            query.append(TextUtils.join(",", ids)).append(") AND mid > 0");
            appendCursorBound(query, cursor, false, false);
            query.append(" ORDER BY date ASC, uid ASC, mid ASC LIMIT ").append(NEWER_LIMIT);
            SQLiteCursor sqlCursor = storage.getDatabase().queryFinalized(query.toString());
            int rawRowCount = 0;
            while (sqlCursor.next()) {
                rawRowCount++;
                page.first.set(sqlCursor.intValue(2), sqlCursor.longValue(3), sqlCursor.intValue(1));
                TLRPC.Message message = readMessage(sqlCursor);
                if (message != null) {
                    page.messages.add(message);
                    MessagesStorage.addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);
                }
            }
            sqlCursor.dispose();
            page.hasMore = rawRowCount == NEWER_LIMIT;
            if (!usersToLoad.isEmpty()) {
                storage.getUsersInternal(usersToLoad, page.users);
            }
            if (!chatsToLoad.isEmpty()) {
                storage.getChatsInternal(TextUtils.join(",", chatsToLoad), page.chats);
            }
        } catch (Exception e) {
            FileLog.e(e);
            page.failed = true;
        }
        clusterGroupedMessages(page.messages);
        return page;
    }

    WindowPage loadChannelWindow(ArrayList<Long> dialogIds, Cursor newest, Cursor oldest) {
        WindowPage page = new WindowPage();
        if (dialogIds.isEmpty() || newest.isEmpty() || oldest.isEmpty()) {
            return page;
        }
        try {
            MessagesStorage storage = MessagesStorage.getInstance(currentAccount);
            ArrayList<Long> usersToLoad = new ArrayList<>();
            ArrayList<Long> chatsToLoad = new ArrayList<>();
            StringBuilder query = new StringBuilder("SELECT data, mid, date, uid FROM messages_v2 WHERE uid IN (");
            query.append(TextUtils.join(",", dialogIds)).append(") AND mid > 0");
            appendCursorBound(query, newest, true, true);
            appendCursorBound(query, oldest, false, true);
            query.append(" ORDER BY date DESC, uid DESC, mid DESC LIMIT ").append(WINDOW_LIMIT + 1);
            int rawCount = 0;
            SQLiteCursor cursor = storage.getDatabase().queryFinalized(query.toString());
            while (cursor.next()) {
                rawCount++;
                if (rawCount > WINDOW_LIMIT) {
                    page.truncated = true;
                    break;
                }
                TLRPC.Message message = readMessage(cursor);
                if (message != null) {
                    page.messages.add(message);
                    MessagesStorage.addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);
                }
            }
            cursor.dispose();
            if (!usersToLoad.isEmpty()) {
                storage.getUsersInternal(usersToLoad, page.users);
            }
            if (!chatsToLoad.isEmpty()) {
                storage.getChatsInternal(TextUtils.join(",", chatsToLoad), page.chats);
            }
        } catch (Exception e) {
            FileLog.e(e);
            page.failed = true;
            page.messages.clear();
            page.users.clear();
            page.chats.clear();
        }
        clusterGroupedMessages(page.messages);
        return page;
    }

    private void completeTrailingAlbum(MessagesStorage storage, OlderPage page, ArrayList<Long> usersToLoad, ArrayList<Long> chatsToLoad) throws Exception {
        if (page.messages.isEmpty()) {
            return;
        }
        TLRPC.Message tail = page.messages.get(page.messages.size() - 1);
        if (tail.grouped_id == 0) {
            return;
        }
        SQLiteCursor cursor = storage.getDatabase().queryFinalized(
                "SELECT data, mid, date, uid FROM messages_v2 WHERE uid = " + tail.dialog_id
                        + " AND mid > 0 AND mid < " + tail.id
                        + " ORDER BY date DESC, mid DESC LIMIT 9");
        try {
            while (cursor.next()) {
                TLRPC.Message message = readMessage(cursor);
                if (message == null || message.grouped_id != tail.grouped_id) {
                    break;
                }
                page.messages.add(message);
                MessagesStorage.addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);
            }
        } finally {
            cursor.dispose();
        }
    }

    private TLRPC.Message readMessage(SQLiteCursor cursor) throws Exception {
        NativeByteBuffer data = cursor.byteBufferValue(0);
        if (data == null) {
            return null;
        }
        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
        if (message == null) {
            data.reuse();
            return null;
        }
        message.readAttachPath(data, UserConfig.getInstance(currentAccount).clientUserId);
        data.reuse();
        if (message instanceof TLRPC.TL_messageEmpty || message.action != null) {
            return null;
        }
        message.id = cursor.intValue(1);
        message.date = cursor.intValue(2);
        message.dialog_id = cursor.longValue(3);
        return message;
    }

    private static void loadChannelDepths(MessagesStorage storage, ArrayList<ChannelSnapshot> channels) throws Exception {
        LongSparseArray<ChannelSnapshot> channelByDialogId = new LongSparseArray<>(channels.size());
        for (int i = 0; i < channels.size(); i++) {
            ChannelSnapshot channel = channels.get(i);
            channel.depthMid = 0;
            channel.depthDate = Integer.MAX_VALUE;
            channel.hasCached = false;
            channel.localStartReached = false;
            channelByDialogId.put(channel.dialogId, channel);
        }
        for (int start = 0; start < channels.size(); start += DEPTH_CHANNEL_BATCH_SIZE) {
            int end = Math.min(start + DEPTH_CHANNEL_BATCH_SIZE, channels.size());
            StringBuilder query = new StringBuilder();
            for (int i = start; i < end; i++) {
                if (query.length() > 0) {
                    query.append(" UNION ALL ");
                }
                ChannelSnapshot channel = channels.get(i);
                int bound = Math.max(channel.holeEnd, 1);
                query.append("SELECT uid, mid, date FROM (SELECT uid, mid, date FROM messages_v2 WHERE uid = ")
                        .append(channel.dialogId)
                        .append(" AND mid >= ")
                        .append(bound)
                        .append(" ORDER BY date ASC, mid ASC LIMIT 1)");
            }
            SQLiteCursor cursor = storage.getDatabase().queryFinalized(query.toString());
            try {
                while (cursor.next()) {
                    ChannelSnapshot channel = channelByDialogId.get(cursor.longValue(0));
                    if (channel == null) {
                        continue;
                    }
                    channel.depthMid = cursor.intValue(1);
                    channel.depthDate = cursor.intValue(2);
                    channel.hasCached = true;
                }
            } finally {
                cursor.dispose();
            }
        }
        for (int i = 0; i < channels.size(); i++) {
            ChannelSnapshot channel = channels.get(i);
            channel.localStartReached = !channel.hasHole && channel.hasCached;
        }
    }

    private static void clusterGroupedMessages(ArrayList<TLRPC.Message> list) {
        if (list.size() < 3) {
            return;
        }
        HashMap<Long, ArrayList<TLRPC.Message>> groups = new HashMap<>();
        boolean anyMultiMemberGroup = false;
        for (int i = 0; i < list.size(); i++) {
            long groupedId = list.get(i).grouped_id;
            if (groupedId == 0) {
                continue;
            }
            ArrayList<TLRPC.Message> members = groups.get(groupedId);
            if (members == null) {
                members = new ArrayList<>();
                groups.put(groupedId, members);
            } else {
                anyMultiMemberGroup = true;
            }
            members.add(list.get(i));
        }
        if (!anyMultiMemberGroup) {
            return;
        }
        ArrayList<TLRPC.Message> ordered = new ArrayList<>(list.size());
        HashSet<Long> emitted = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            TLRPC.Message message = list.get(i);
            long groupedId = message.grouped_id;
            if (groupedId == 0) {
                ordered.add(message);
            } else if (emitted.add(groupedId)) {
                ordered.addAll(groups.get(groupedId));
            }
        }
        list.clear();
        list.addAll(ordered);
    }
}
