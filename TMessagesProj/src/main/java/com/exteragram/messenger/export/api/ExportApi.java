/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export.api;

import com.exteragram.messenger.export.ExportSettings;
import com.exteragram.messenger.export.output.OutputFile;
import com.exteragram.messenger.export.output.Result;

import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_stories;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class ExportApi {

    public interface Listener {
        void onApiError(TLRPC.TL_error error);

        void onIoError(Result result);
    }

    public interface Progress<T> {
        boolean run(T value);
    }

    public record DownloadProgress(long randomId, String path, int itemIndex, long ready, long total) {
        public static final DownloadProgress EMPTY = new DownloadProgress(0, "", 0, 0, 0);
    }

    public static class StartInfo {
        public int userpicsCount;
        public int storiesCount;
        public int profileMusicCount;
        public int dialogsCount;
    }

    private record FileProgress(long ready, long total) {
    }

    private interface ResponseCallback {
        void run(TLObject response);
    }

    private interface FailCallback {
        boolean run(TLRPC.TL_error error);
    }

    private static final int USERPICS_SLICE_LIMIT = 100;
    private static final int FILE_CHUNK_SIZE = 512 * 1024;
    private static final int CHATS_SLICE_LIMIT = 100;
    private static final int MESSAGES_SLICE_LIMIT = 100;
    private static final int TOP_PEER_SLICE_LIMIT = 100;
    private static final int LOCATION_CACHE_SIZE = 100_000;
    private static final int MAX_EMOJI_PER_REQUEST = 100;
    private static final int STORIES_SLICE_LIMIT = 100;
    private static final int PROFILE_MUSIC_SLICE_LIMIT = 100;

    private enum StartStep {
        UserpicsCount, StoriesCount, ProfileMusicCount, SplitRanges, DialogsCount, LeftChannelsCount
    }

    private static class StartProcess {
        Utilities.Callback<StartInfo> done;
        final ArrayDeque<StartStep> steps = new ArrayDeque<>();
        int splitIndex;
        final StartInfo info = new StartInfo();
    }

    private static class ChatsProcess {
        Progress<Integer> progress;
        Utilities.Callback<ExportData.DialogsInfo> done;
        final ExportData.DialogsInfo info = new ExportData.DialogsInfo();
        int processedCount;
        final HashMap<Long, Integer> indexByPeer = new HashMap<>();
    }

    private static class DialogsProcess extends ChatsProcess {
        int splitIndexPlusOne;
        int offsetDate;
        int offsetId;
        TLRPC.InputPeer offsetPeer = new TLRPC.TL_inputPeerEmpty();
    }

    private static class LeftChannelsProcess extends ChatsProcess {
        int fullCount;
        int offset;
        boolean finished;
    }

    private static class UserpicsProcess {
        Progress<Integer> start;
        Progress<DownloadProgress> fileProgress;
        Progress<ArrayList<ExportData.Photo>> handleSlice;
        Runnable finish;
        int processed;
        ArrayList<ExportData.Photo> slice;
        long maxId;
        boolean lastSlice;
        int fileIndex;
    }

    private static class StoriesProcess {
        Progress<Integer> start;
        Progress<DownloadProgress> fileProgress;
        Progress<ExportData.StoriesSlice> handleSlice;
        Runnable finish;
        int processed;
        ExportData.StoriesSlice slice;
        int offsetId;
        boolean lastSlice;
        int fileIndex;
    }

    private static class ProfileMusicProcess {
        Progress<Integer> start;
        Progress<DownloadProgress> fileProgress;
        Progress<ArrayList<ExportData.Message>> handleSlice;
        Runnable finish;
        int offset;
        int processed;
        ArrayList<ExportData.Message> slice;
        boolean lastSlice;
        int fileIndex;
    }

    private static class ContactsProcess {
        Utilities.Callback<ExportData.ContactsList> done;
        ExportData.ContactsList result;
        int topPeersOffset;
    }

    private static class ChatProcess {
        ExportData.DialogInfo info;
        Progress<ExportData.DialogInfo> start;
        Progress<DownloadProgress> fileProgress;
        Progress<ExportData.MessagesSlice> handleSlice;
        Runnable done;
        Utilities.Callback<TLRPC.messages_Messages> requestDone;
        int localSplitIndex;
        int largestIdPlusOne = 1;
        final ExportData.ParseMediaContext context = new ExportData.ParseMediaContext();
        ExportData.MessagesSlice slice;
        boolean lastSlice;
        int fileIndex;
        int topicRootId;
        int topicOffsetId;
    }

    private static class OtherDataProcess {
        final ExportData.File file = new ExportData.File();
        Utilities.Callback<ExportData.File> done;
    }

    private static class FileProcess {
        final OutputFile file;
        String relativePath;
        Progress<FileProgress> progress;
        Utilities.Callback<String> done;
        long randomId;
        ExportData.FileLocation location;
        ExportData.FileOrigin origin;
        ExportData.File source;
        long offset;
        long size;
        int requestId;

        FileProcess(String path, OutputFile.Stats stats) {
            file = new OutputFile(path, stats);
        }
    }

    private final int account;
    private final DispatchQueue queue;
    private final Listener listener;
    private final HashSet<Integer> requests = new HashSet<>();
    private final ArrayList<TLRPC.TL_messageRange> splits = new ArrayList<>();
    private final HashMap<Long, ExportData.Document> resolvedCustomEmoji = new HashMap<>();
    private final LinkedHashSet<Long> unresolvedCustomEmoji = new LinkedHashSet<>();
    private final Map<String, String> fileCache = new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > LOCATION_CACHE_SIZE;
        }
    };

    private ExportSettings settings;
    private OutputFile.Stats stats;
    private Long takeoutId;
    private long selfId;
    private boolean cancelled;

    private StartProcess startProcess;
    private DialogsProcess dialogsProcess;
    private LeftChannelsProcess leftChannelsProcess;
    private UserpicsProcess userpicsProcess;
    private StoriesProcess storiesProcess;
    private ProfileMusicProcess profileMusicProcess;
    private ContactsProcess contactsProcess;
    private ChatProcess chatProcess;
    private OtherDataProcess otherDataProcess;
    private FileProcess fileProcess;

    public ExportApi(int account, DispatchQueue queue, Listener listener) {
        this.account = account;
        this.queue = queue;
        this.listener = listener;
    }

    private void error(TLRPC.TL_error error) {
        if (!cancelled) {
            listener.onApiError(error);
        }
    }

    private void error(String text) {
        TLRPC.TL_error error = new TLRPC.TL_error();
        error.text = "API_ERROR: " + text;
        error(error);
    }

    private void ioError(Result result) {
        if (!cancelled) {
            listener.onIoError(result);
        }
    }

    private int send(TLObject request, int datacenterId, int connectionType, int flags, ResponseCallback done, FailCallback fail) {
        int[] token = new int[1];
        token[0] = ConnectionsManager.getInstance(account).sendRequest(request, (response, error) -> {
            if (response != null) {
                response.disableFree = true;
            }
            queue.postRunnable(() -> {
                requests.remove(token[0]);
                try {
                    if (cancelled) {
                        return;
                    }
                    if (error != null) {
                        if (fail == null || !fail.run(error)) {
                            error(error);
                        }
                    } else if (response == null) {
                        error("EMPTY_RESPONSE");
                    } else {
                        done.run(response);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                    error(e.getClass().getSimpleName());
                } finally {
                    if (response != null) {
                        response.disableFree = false;
                        response.freeResources();
                    }
                }
            });
        }, null, null, flags, datacenterId, connectionType, true);
        requests.add(token[0]);
        return token[0];
    }

    private int plainRequest(TLObject request, ResponseCallback done) {
        return send(request, ConnectionsManager.DEFAULT_DATACENTER_ID, ConnectionsManager.ConnectionTypeGeneric, 0, done, null);
    }

    private int mainRequest(TLObject request, ResponseCallback done) {
        return mainRequest(request, done, null);
    }

    private int mainRequest(TLObject request, ResponseCallback done, FailCallback fail) {
        TakeoutRequests.TL_invokeWithTakeout wrapper = new TakeoutRequests.TL_invokeWithTakeout();
        wrapper.takeout_id = takeoutId != null ? takeoutId : 0;
        wrapper.query = request;
        return send(wrapper, ConnectionsManager.DEFAULT_DATACENTER_ID, ConnectionsManager.ConnectionTypeGeneric, 0, done, fail);
    }

    private int splitRequest(int index, TLObject request, ResponseCallback done, FailCallback fail) {
        TakeoutRequests.TL_invokeWithMessagesRange wrapper = new TakeoutRequests.TL_invokeWithMessagesRange();
        wrapper.range = splits.get(index);
        wrapper.query = request;
        return mainRequest(wrapper, done, fail);
    }

    private int fileRequest(ExportData.FileLocation location, long offset) {
        TLRPC.TL_upload_getFile request = new TLRPC.TL_upload_getFile();
        request.location = location.data;
        request.offset = offset;
        request.limit = FILE_CHUNK_SIZE;
        TakeoutRequests.TL_invokeWithTakeout wrapper = new TakeoutRequests.TL_invokeWithTakeout();
        wrapper.takeout_id = takeoutId != null ? takeoutId : 0;
        wrapper.query = request;
        int datacenterId = location.dcId != 0 ? location.dcId : ConnectionsManager.DEFAULT_DATACENTER_ID;
        return send(wrapper, datacenterId, ConnectionsManager.ConnectionTypeDownload, ConnectionsManager.RequestFlagForceDownload, response -> {
            fileProcess.requestId = 0;
            filePartDone(offset, response);
        }, error -> {
            fileProcess.requestId = 0;
            String text = error.text != null ? error.text : "";
            if (text.equals("TAKEOUT_FILE_EMPTY") && otherDataProcess != null) {
                filePartDone(0, new TLRPC.TL_upload_file());
            } else if (text.equals("LOCATION_INVALID") || text.equals("VERSION_INVALID") || text.equals("LOCATION_NOT_AVAILABLE")) {
                filePartUnavailable();
            } else if (text.equals("TAKEOUT_FILE_TOO_BIG")) {
                fileProcess.source.skipReason = ExportData.File.SkipReason.FileSize;
                filePartUnavailable();
            } else if (error.code == 400 && text.startsWith("FILE_REFERENCE_")) {
                filePartRefreshReference(offset);
            } else {
                return false;
            }
            return true;
        });
    }

    private boolean useOnlyLastSplit() {
        return (settings.types & ExportSettings.NonChannelChatsMask) == 0;
    }

    public void startExport(ExportSettings settings, OutputFile.Stats stats, Utilities.Callback<StartInfo> done) {
        this.settings = settings;
        this.stats = stats;
        startProcess = new StartProcess();
        startProcess.done = done;
        if (settings.has(ExportSettings.Userpics)) {
            startProcess.steps.add(StartStep.UserpicsCount);
        }
        if (settings.has(ExportSettings.Stories)) {
            startProcess.steps.add(StartStep.StoriesCount);
        }
        if (settings.has(ExportSettings.ProfileMusic)) {
            startProcess.steps.add(StartStep.ProfileMusicCount);
        }
        if (settings.has(ExportSettings.AnyChatsMask)) {
            startProcess.steps.add(StartStep.SplitRanges);
            startProcess.steps.add(StartStep.DialogsCount);
        }
        if (settings.has(ExportSettings.GroupsChannelsMask) && !settings.onlySinglePeer()) {
            startProcess.steps.add(StartStep.LeftChannelsCount);
        }
        startMainSession(this::sendNextStartRequest);
    }

    private void startMainSession(Runnable done) {
        long sizeLimit = settings.media.sizeLimit;
        boolean hasFiles = (settings.media.types != 0 && sizeLimit > 0)
                || settings.has(ExportSettings.Userpics)
                || settings.has(ExportSettings.Stories);
        TakeoutRequests.TL_account_initTakeoutSession init = new TakeoutRequests.TL_account_initTakeoutSession();
        init.contacts = settings.has(ExportSettings.Contacts);
        init.files = hasFiles;
        init.file_max_size = Math.min(Math.max(sizeLimit, 0), ExportSettings.MaxFileSize);
        init.message_users = settings.has(ExportSettings.PersonalChats | ExportSettings.BotChats);
        init.message_chats = settings.has(ExportSettings.PrivateGroups);
        init.message_megagroups = settings.has(ExportSettings.PrivateGroups | ExportSettings.PublicGroups);
        init.message_channels = settings.has(ExportSettings.ChannelsMask);

        TLRPC.TL_users_getUsers getUsers = new TLRPC.TL_users_getUsers();
        getUsers.id.add(new TLRPC.TL_inputUserSelf());
        plainRequest(getUsers, response -> {
            if (response instanceof Vector<?> vector) {
                for (Object object : vector.objects) {
                    if (object instanceof TLRPC.User user && user.self) {
                        selfId = user.id;
                    }
                }
            }
            if (selfId == 0) {
                error("Could not retrieve selfId.");
                return;
            }
            plainRequest(init, takeout -> {
                if (!(takeout instanceof TakeoutRequests.TL_account_takeout result)) {
                    error("Unexpected takeout response.");
                    return;
                }
                takeoutId = result.id;
                done.run();
            });
        });
    }

    private void sendNextStartRequest() {
        StartStep step = startProcess.steps.poll();
        if (step == null) {
            StartProcess process = startProcess;
            startProcess = null;
            process.done.run(process.info);
            return;
        }
        switch (step) {
            case UserpicsCount -> requestUserpicsCount();
            case StoriesCount -> requestStoriesCount();
            case ProfileMusicCount -> requestProfileMusicCount();
            case SplitRanges -> requestSplitRanges();
            case DialogsCount -> requestDialogsCount();
            case LeftChannelsCount -> requestLeftChannelsCount();
        }
    }

    private TLRPC.TL_photos_getUserPhotos userPhotosRequest(long maxId, int limit) {
        TLRPC.TL_photos_getUserPhotos request = new TLRPC.TL_photos_getUserPhotos();
        request.user_id = new TLRPC.TL_inputUserSelf();
        request.max_id = maxId;
        request.limit = limit;
        return request;
    }

    private TL_stories.TL_stories_getStoriesArchive storiesRequest(int offsetId, int limit) {
        TL_stories.TL_stories_getStoriesArchive request = new TL_stories.TL_stories_getStoriesArchive();
        request.peer = new TLRPC.TL_inputPeerSelf();
        request.offset_id = offsetId;
        request.limit = limit;
        return request;
    }

    private static int photosCount(TLRPC.photos_Photos photos) {
        return photos instanceof TLRPC.TL_photos_photosSlice ? photos.count : photos.photos.size();
    }

    private void requestUserpicsCount() {
        mainRequest(userPhotosRequest(0, 0), response -> {
            if (!(response instanceof TLRPC.photos_Photos photos)) {
                error("Unexpected userpics response.");
                return;
            }
            startProcess.info.userpicsCount = photosCount(photos);
            sendNextStartRequest();
        });
    }

    private void requestStoriesCount() {
        mainRequest(storiesRequest(0, 0), response -> {
            if (!(response instanceof TL_stories.TL_stories_stories stories)) {
                error("Unexpected stories response.");
                return;
            }
            startProcess.info.storiesCount = stories.count;
            sendNextStartRequest();
        });
    }

    private TLRPC.TL_getSavedMusic savedMusicRequest(int offset, int limit) {
        TLRPC.TL_getSavedMusic request = new TLRPC.TL_getSavedMusic();
        request.id = new TLRPC.TL_inputUserSelf();
        request.offset = offset;
        request.limit = limit;
        return request;
    }

    private void requestProfileMusicCount() {
        mainRequest(savedMusicRequest(0, 0), response -> {
            if (!(response instanceof TLRPC.TL_savedMusic music)) {
                error("Unexpected profile music response.");
                return;
            }
            startProcess.info.profileMusicCount = music.count;
            sendNextStartRequest();
        });
    }

    private void requestSplitRanges() {
        mainRequest(new TakeoutRequests.TL_messages_getSplitRanges(), response -> {
            splits.clear();
            if (response instanceof Vector<?> vector) {
                for (Object object : vector.objects) {
                    if (object instanceof TLRPC.TL_messageRange range) {
                        splits.add(range);
                    }
                }
            }
            if (splits.isEmpty()) {
                TLRPC.TL_messageRange range = new TLRPC.TL_messageRange();
                range.min_id = 1;
                range.max_id = Integer.MAX_VALUE;
                splits.add(range);
            }
            startProcess.splitIndex = useOnlyLastSplit() ? splits.size() - 1 : 0;
            sendNextStartRequest();
        });
    }

    private void requestDialogsCount() {
        if (settings.onlySinglePeer()) {
            startProcess.info.dialogsCount = settings.singlePeer instanceof TLRPC.TL_inputPeerChannel ? 1 : splits.size();
            sendNextStartRequest();
            return;
        }
        TLRPC.TL_messages_getDialogs request = new TLRPC.TL_messages_getDialogs();
        request.offset_peer = new TLRPC.TL_inputPeerEmpty();
        request.limit = 1;
        splitRequest(startProcess.splitIndex, request, response -> {
            if (!(response instanceof TLRPC.messages_Dialogs dialogs) || response instanceof TLRPC.TL_messages_dialogsNotModified) {
                error("Unexpected dialogsNotModified received.");
                return;
            }
            startProcess.info.dialogsCount += dialogs instanceof TLRPC.TL_messages_dialogsSlice ? dialogs.count : dialogs.dialogs.size();
            if (++startProcess.splitIndex >= splits.size()) {
                sendNextStartRequest();
            } else {
                requestDialogsCount();
            }
        }, null);
    }

    private void requestLeftChannelsCount() {
        leftChannelsProcess = new LeftChannelsProcess();
        requestLeftChannelsSliceGeneric(() -> {
            startProcess.info.dialogsCount += leftChannelsProcess.fullCount;
            sendNextStartRequest();
        });
    }

    private void requestLeftChannelsSliceGeneric(Runnable done) {
        TakeoutRequests.TL_channels_getLeftChannels request = new TakeoutRequests.TL_channels_getLeftChannels();
        request.offset = leftChannelsProcess.offset;
        mainRequest(request, response -> {
            if (!(response instanceof TLRPC.messages_Chats chats)) {
                error("Unexpected left channels response.");
                return;
            }
            LeftChannelsProcess process = leftChannelsProcess;
            process.offset += chats.chats.size();
            boolean slice = chats instanceof TLRPC.TL_messages_chatsSlice;
            process.fullCount = slice ? chats.count : chats.chats.size();
            process.finished = !slice || chats.chats.isEmpty();
            appendChatsSlice(process, process.info.left, ExportParser.parseLeftChannelsInfo(chats).left, splits.size() - 1);
            if (process.progress != null && !process.progress.run(process.info.left.size())) {
                return;
            }
            done.run();
        });
    }

    public void requestDialogsList(Progress<Integer> progress, Utilities.Callback<ExportData.DialogsInfo> done) {
        dialogsProcess = new DialogsProcess();
        dialogsProcess.splitIndexPlusOne = splits.size();
        dialogsProcess.progress = progress;
        dialogsProcess.done = done;
        requestDialogsSlice();
    }

    private void requestDialogsSlice() {
        if (settings.onlySinglePeer()) {
            requestSinglePeerDialog();
            return;
        }
        TLRPC.TL_messages_getDialogs request = new TLRPC.TL_messages_getDialogs();
        request.offset_date = dialogsProcess.offsetDate;
        request.offset_id = dialogsProcess.offsetId;
        request.offset_peer = dialogsProcess.offsetPeer;
        request.limit = CHATS_SLICE_LIMIT;
        splitRequest(dialogsProcess.splitIndexPlusOne - 1, request, response -> {
            if (!(response instanceof TLRPC.messages_Dialogs dialogs) || response instanceof TLRPC.TL_messages_dialogsNotModified) {
                error("Unexpected dialogsNotModified received.");
                return;
            }
            boolean finished = !(dialogs instanceof TLRPC.TL_messages_dialogsSlice) || dialogs.dialogs.isEmpty();
            ExportData.DialogsInfo info = ExportParser.parseDialogsInfo(dialogs);
            dialogsProcess.processedCount += info.chats.size();
            ExportData.DialogInfo last = info.chats.isEmpty() ? new ExportData.DialogInfo() : info.chats.get(info.chats.size() - 1);
            appendChatsSlice(dialogsProcess, dialogsProcess.info.chats, info.chats, dialogsProcess.splitIndexPlusOne - 1);
            if (!dialogsProcess.progress.run(dialogsProcess.processedCount)) {
                return;
            }
            if (!finished && last.topMessageDate > 0) {
                dialogsProcess.offsetId = last.topMessageId;
                dialogsProcess.offsetDate = last.topMessageDate;
                dialogsProcess.offsetPeer = last.input;
            } else if (!useOnlyLastSplit() && --dialogsProcess.splitIndexPlusOne > 0) {
                dialogsProcess.offsetId = 0;
                dialogsProcess.offsetDate = 0;
                dialogsProcess.offsetPeer = new TLRPC.TL_inputPeerEmpty();
            } else {
                requestLeftChannelsIfNeeded();
                return;
            }
            requestDialogsSlice();
        }, null);
    }

    private void requestSinglePeerDialog() {
        TLRPC.InputPeer peer = settings.singlePeer;
        if (peer instanceof TLRPC.TL_inputPeerUser || peer instanceof TLRPC.TL_inputPeerSelf) {
            TLRPC.TL_users_getUsers request = new TLRPC.TL_users_getUsers();
            if (peer instanceof TLRPC.TL_inputPeerUser) {
                TLRPC.TL_inputUser user = new TLRPC.TL_inputUser();
                user.user_id = peer.user_id;
                user.access_hash = peer.access_hash;
                request.id.add(user);
            } else {
                request.id.add(new TLRPC.TL_inputUserSelf());
            }
            mainRequest(request, response -> {
                ArrayList<TLRPC.User> users = new ArrayList<>();
                if (response instanceof Vector<?> vector) {
                    for (Object object : vector.objects) {
                        if (object instanceof TLRPC.User user) {
                            users.add(user);
                        }
                    }
                }
                appendSinglePeerDialogs(singlePeerDialogsFromUsers(peer, users));
            });
        } else if (peer instanceof TLRPC.TL_inputPeerChat) {
            TLRPC.TL_messages_getChats request = new TLRPC.TL_messages_getChats();
            request.id.add(peer.chat_id);
            mainRequest(request, response -> appendSinglePeerDialogs(singlePeerDialogsFromChats(peer.chat_id, response)));
        } else if (peer instanceof TLRPC.TL_inputPeerChannel) {
            TLRPC.TL_channels_getChannels request = new TLRPC.TL_channels_getChannels();
            TLRPC.TL_inputChannel channel = new TLRPC.TL_inputChannel();
            channel.channel_id = peer.channel_id;
            channel.access_hash = peer.access_hash;
            request.id.add(channel);
            mainRequest(request, response -> appendSinglePeerDialogs(singlePeerDialogsFromChats(peer.channel_id, response)));
        } else {
            error("Unexpected peer type in requestSinglePeerDialog.");
        }
    }

    private static ExportData.DialogsInfo singlePeerDialogsFromUsers(TLRPC.InputPeer peer, List<TLRPC.User> users) {
        ExportData.DialogsInfo result = new ExportData.DialogsInfo();
        for (TLRPC.User user : users) {
            boolean matches = peer instanceof TLRPC.TL_inputPeerSelf ? user.self : user.id == peer.user_id;
            if (matches) {
                result.chats.add(ExportParser.dialogInfoFromUser(ExportParser.parseUser(user)));
            }
        }
        return result;
    }

    private static ExportData.DialogsInfo singlePeerDialogsFromChats(long chatId, TLObject response) {
        ExportData.DialogsInfo result = new ExportData.DialogsInfo();
        if (response instanceof TLRPC.messages_Chats chats) {
            for (TLRPC.Chat chat : chats.chats) {
                if (chat.id == chatId) {
                    result.chats.add(ExportParser.dialogInfoFromChat(ExportParser.parseChat(chat)));
                }
            }
        }
        return result;
    }

    private void appendSinglePeerDialogs(ExportData.DialogsInfo info) {
        int migratedRequestId = 0;
        int last = dialogsProcess.splitIndexPlusOne - 1;
        for (ExportData.DialogInfo chat : info.chats) {
            if (chat.isSupergroup() && migratedRequestId == 0) {
                migratedRequestId = requestSinglePeerMigrated(chat);
                continue;
            } else if (chat.isChannel()) {
                continue;
            }
            for (int i = last; i != 0; i--) {
                chat.splits.add(i - 1);
                chat.messagesCountPerSplit.add(0);
            }
        }
        if (migratedRequestId == 0) {
            dialogsProcess.processedCount += info.chats.size();
        }
        appendChatsSlice(dialogsProcess, dialogsProcess.info.chats, info.chats, dialogsProcess.splitIndexPlusOne - 1);
        if (migratedRequestId != 0 || !dialogsProcess.progress.run(dialogsProcess.processedCount)) {
            return;
        }
        finishDialogsList();
    }

    private int requestSinglePeerMigrated(ExportData.DialogInfo info) {
        if (!(info.input instanceof TLRPC.TL_inputPeerChannel input)) {
            return 0;
        }
        TLRPC.TL_channels_getFullChannel request = new TLRPC.TL_channels_getFullChannel();
        TLRPC.TL_inputChannel channel = new TLRPC.TL_inputChannel();
        channel.channel_id = input.channel_id;
        channel.access_hash = input.access_hash;
        request.channel = channel;
        return mainRequest(request, response -> {
            ExportData.DialogsInfo migrated = new ExportData.DialogsInfo();
            if (response instanceof TLRPC.TL_messages_chatFull full && full.full_chat != null && full.full_chat.migrated_from_chat_id != 0) {
                TLRPC.TL_messages_chats chats = new TLRPC.TL_messages_chats();
                chats.chats = full.chats;
                migrated = singlePeerDialogsFromChats(full.full_chat.migrated_from_chat_id, chats);
            }
            appendSinglePeerDialogs(migrated);
        });
    }

    private void requestLeftChannelsIfNeeded() {
        if (settings.has(ExportSettings.GroupsChannelsMask) && leftChannelsProcess != null) {
            leftChannelsProcess.progress = count -> dialogsProcess.progress.run(dialogsProcess.processedCount + count);
            leftChannelsProcess.done = result -> {
                dialogsProcess.info.left = result.left;
                finishDialogsList();
            };
            requestLeftChannelsSlice();
        } else {
            finishDialogsList();
        }
    }

    private void requestLeftChannelsSlice() {
        if (leftChannelsProcess.finished) {
            leftChannelsProcess.done.run(leftChannelsProcess.info);
            return;
        }
        requestLeftChannelsSliceGeneric(() -> {
            if (leftChannelsProcess.finished) {
                leftChannelsProcess.done.run(leftChannelsProcess.info);
            } else {
                requestLeftChannelsSlice();
            }
        });
    }

    private void finishDialogsList() {
        ExportParser.finalizeDialogsInfo(dialogsProcess.info, settings);
        DialogsProcess process = dialogsProcess;
        dialogsProcess = null;
        process.done.run(process.info);
    }

    private void appendChatsSlice(ChatsProcess process, ArrayList<ExportData.DialogInfo> to, ArrayList<ExportData.DialogInfo> from, int splitIndex) {
        boolean groupsRequested = settings.has(ExportSettings.GroupsMask);
        for (ExportData.DialogInfo info : from) {
            boolean goodByTypes = settings.has(ExportParser.settingsFromDialogType(info.type));
            if (!goodByTypes && !(info.migratedToChannelId != 0 && groupsRequested)) {
                continue;
            }
            if (info.migratedToChannelId != 0) {
                Integer index = process.indexByPeer.get(-info.migratedToChannelId);
                if (index != null && ExportParser.addMigrateFromSlice(to.get(index), info, splitIndex, splits.size())) {
                    continue;
                } else if (!goodByTypes) {
                    continue;
                }
            }
            Integer existing = process.indexByPeer.get(info.peerId);
            ExportData.DialogInfo target;
            if (existing == null) {
                process.indexByPeer.put(info.peerId, to.size());
                to.add(info);
                target = info;
            } else {
                target = to.get(existing);
            }
            target.splits.add(splitIndex);
            target.messagesCountPerSplit.add(0);
        }
    }

    public void requestPersonalInfo(Utilities.Callback<ExportData.PersonalInfo> done) {
        TLRPC.TL_users_getFullUser request = new TLRPC.TL_users_getFullUser();
        request.id = new TLRPC.TL_inputUserSelf();
        mainRequest(request, response -> {
            if (!(response instanceof TLRPC.TL_users_userFull full) || full.users.isEmpty()) {
                error("Unexpected personal info response.");
                return;
            }
            done.run(ExportParser.parsePersonalInfo(full));
        });
    }

    public void requestOtherData(String suggestedPath, Utilities.Callback<ExportData.File> done) {
        otherDataProcess = new OtherDataProcess();
        otherDataProcess.done = done;
        otherDataProcess.file.suggestedPath = suggestedPath;
        otherDataProcess.file.location = new ExportData.FileLocation();
        otherDataProcess.file.location.data = new TakeoutRequests.TL_inputTakeoutFileLocation();
        loadFile(otherDataProcess.file, ExportData.FileOrigin.EMPTY, null, relativePath -> {
            OtherDataProcess process = otherDataProcess;
            otherDataProcess = null;
            process.file.relativePath = relativePath;
            if (relativePath.isEmpty()) {
                process.file.skipReason = ExportData.File.SkipReason.Unavailable;
            }
            process.done.run(process.file);
        });
    }

    public void requestUserpics(Progress<Integer> start, Progress<DownloadProgress> progress, Progress<ArrayList<ExportData.Photo>> slice, Runnable finish) {
        userpicsProcess = new UserpicsProcess();
        userpicsProcess.start = start;
        userpicsProcess.fileProgress = progress;
        userpicsProcess.handleSlice = slice;
        userpicsProcess.finish = finish;
        mainRequest(userPhotosRequest(userpicsProcess.maxId, USERPICS_SLICE_LIMIT), response -> {
            if (!(response instanceof TLRPC.photos_Photos photos)) {
                error("Unexpected userpics response.");
                return;
            }
            if (!userpicsProcess.start.run(photosCount(photos))) {
                return;
            }
            handleUserpicsSlice(photos);
        });
    }

    private void handleUserpicsSlice(TLRPC.photos_Photos photos) {
        if (!(photos instanceof TLRPC.TL_photos_photosSlice)) {
            userpicsProcess.lastSlice = true;
        }
        ArrayList<ExportData.Photo> slice = ExportParser.parseUserpicsSlice(photos.photos, userpicsProcess.processed);
        if (slice.isEmpty()) {
            userpicsProcess.lastSlice = true;
        }
        userpicsProcess.slice = slice;
        userpicsProcess.fileIndex = 0;
        loadNextUserpic();
    }

    private void loadNextUserpic() {
        ArrayList<ExportData.Photo> list = userpicsProcess.slice;
        for (; userpicsProcess.fileIndex < list.size(); userpicsProcess.fileIndex++) {
            ExportData.File file = list.get(userpicsProcess.fileIndex).image.file;
            boolean ready = processFileLoad(file, ExportData.FileOrigin.EMPTY,
                    value -> userpicsProcess.fileProgress.run(downloadProgress(userpicsProcess.fileIndex, value)),
                    path -> {
                        markLoaded(userpicsProcess.slice.get(userpicsProcess.fileIndex).image.file, path);
                        loadNextUserpic();
                    }, 0, file.size, null);
            if (!ready) {
                return;
            }
        }
        finishUserpicsSlice();
    }

    private void finishUserpicsSlice() {
        ArrayList<ExportData.Photo> slice = userpicsProcess.slice;
        userpicsProcess.slice = null;
        if (!slice.isEmpty()) {
            userpicsProcess.processed += slice.size();
            userpicsProcess.maxId = slice.get(slice.size() - 1).id;
            if (!userpicsProcess.handleSlice.run(slice)) {
                return;
            }
        }
        if (userpicsProcess.lastSlice) {
            UserpicsProcess process = userpicsProcess;
            userpicsProcess = null;
            process.finish.run();
            return;
        }
        mainRequest(userPhotosRequest(userpicsProcess.maxId, USERPICS_SLICE_LIMIT), response -> {
            if (!(response instanceof TLRPC.photos_Photos photos)) {
                error("Unexpected userpics response.");
                return;
            }
            handleUserpicsSlice(photos);
        });
    }

    public void requestStories(Progress<Integer> start, Progress<DownloadProgress> progress, Progress<ExportData.StoriesSlice> slice, Runnable finish) {
        storiesProcess = new StoriesProcess();
        storiesProcess.start = start;
        storiesProcess.fileProgress = progress;
        storiesProcess.handleSlice = slice;
        storiesProcess.finish = finish;
        mainRequest(storiesRequest(storiesProcess.offsetId, STORIES_SLICE_LIMIT), response -> {
            if (!(response instanceof TL_stories.TL_stories_stories stories)) {
                error("Unexpected stories response.");
                return;
            }
            if (!storiesProcess.start.run(stories.count)) {
                return;
            }
            handleStoriesSlice(stories);
        });
    }

    private void handleStoriesSlice(TL_stories.TL_stories_stories stories) {
        ExportData.StoriesSlice slice = ExportParser.parseStoriesSlice(stories.stories, storiesProcess.processed);
        if (slice.lastId == 0) {
            storiesProcess.lastSlice = true;
        }
        storiesProcess.slice = slice;
        storiesProcess.fileIndex = 0;
        loadNextStory();
    }

    private void loadNextStory() {
        ArrayList<ExportData.Story> list = storiesProcess.slice.list;
        for (; storiesProcess.fileIndex < list.size(); storiesProcess.fileIndex++) {
            ExportData.Story story = list.get(storiesProcess.fileIndex);
            ExportData.FileOrigin origin = new ExportData.FileOrigin(0, null, 0, story.id, 0);
            boolean ready = processFileLoad(story.file(), origin,
                    value -> storiesProcess.fileProgress.run(downloadProgress(storiesProcess.fileIndex, value)),
                    path -> {
                        markLoaded(storiesProcess.slice.list.get(storiesProcess.fileIndex).file(), path);
                        loadNextStory();
                    }, 0, story.file().size, null);
            if (!ready) {
                return;
            }
            boolean thumbReady = processFileLoad(story.thumb().file, origin,
                    value -> storiesProcess.fileProgress.run(downloadProgress(storiesProcess.fileIndex, value)),
                    path -> {
                        markLoaded(storiesProcess.slice.list.get(storiesProcess.fileIndex).thumb().file, path);
                        loadNextStory();
                    }, 0, story.file().size, null);
            if (!thumbReady) {
                return;
            }
        }
        finishStoriesSlice();
    }

    private void finishStoriesSlice() {
        ExportData.StoriesSlice slice = storiesProcess.slice;
        storiesProcess.slice = null;
        if (slice.lastId != 0) {
            storiesProcess.processed += slice.list.size();
            storiesProcess.offsetId = slice.lastId;
            if (!storiesProcess.handleSlice.run(slice)) {
                return;
            }
        }
        if (storiesProcess.lastSlice) {
            StoriesProcess process = storiesProcess;
            storiesProcess = null;
            process.finish.run();
            return;
        }
        mainRequest(storiesRequest(storiesProcess.offsetId, STORIES_SLICE_LIMIT), response -> {
            if (!(response instanceof TL_stories.TL_stories_stories stories)) {
                error("Unexpected stories response.");
                return;
            }
            handleStoriesSlice(stories);
        });
    }

    public void requestProfileMusic(Progress<Integer> start, Progress<DownloadProgress> progress, Progress<ArrayList<ExportData.Message>> slice, Runnable finish) {
        profileMusicProcess = new ProfileMusicProcess();
        profileMusicProcess.start = start;
        profileMusicProcess.fileProgress = progress;
        profileMusicProcess.handleSlice = slice;
        profileMusicProcess.finish = finish;
        mainRequest(savedMusicRequest(0, PROFILE_MUSIC_SLICE_LIMIT), response -> {
            if (!profileMusicProcess.start.run(response instanceof TLRPC.TL_savedMusic music ? music.count : 0)) {
                return;
            }
            handleProfileMusicSlice(response);
        });
    }

    private void handleProfileMusicSlice(TLObject response) {
        ArrayList<ExportData.Message> slice = new ArrayList<>();
        if (response instanceof TLRPC.TL_savedMusic music) {
            if (music.documents.size() < PROFILE_MUSIC_SLICE_LIMIT) {
                profileMusicProcess.lastSlice = true;
            }
            ExportData.ParseMediaContext context = new ExportData.ParseMediaContext();
            context.selfPeerId = selfId;
            slice = ExportParser.parseProfileMusicSlice(context, music.documents, profileMusicProcess.offset);
            profileMusicProcess.offset += music.documents.size();
        } else {
            profileMusicProcess.lastSlice = true;
        }
        if (slice.isEmpty()) {
            profileMusicProcess.lastSlice = true;
        }
        profileMusicProcess.slice = slice;
        profileMusicProcess.fileIndex = 0;
        loadNextProfileMusic();
    }

    private void loadNextProfileMusic() {
        ArrayList<ExportData.Message> list = profileMusicProcess.slice;
        for (; profileMusicProcess.fileIndex < list.size(); profileMusicProcess.fileIndex++) {
            ExportData.Message message = list.get(profileMusicProcess.fileIndex);
            ExportData.FileOrigin origin = new ExportData.FileOrigin(0, null, message.id, 0, 0);
            long controllingSize = message.file().size;
            boolean ready = processFileLoad(message.file(), origin,
                    value -> profileMusicProcess.fileProgress.run(downloadProgress(profileMusicProcess.fileIndex, value)),
                    path -> {
                        markLoaded(profileMusicProcess.slice.get(profileMusicProcess.fileIndex).file(), path);
                        loadNextProfileMusic();
                    }, ExportSettings.MediaSettings.File, controllingSize, null);
            if (!ready) {
                return;
            }
            boolean thumbReady = processFileLoad(message.thumb().file, origin,
                    value -> profileMusicProcess.fileProgress.run(downloadProgress(profileMusicProcess.fileIndex, value)),
                    path -> {
                        markLoaded(profileMusicProcess.slice.get(profileMusicProcess.fileIndex).thumb().file, path);
                        loadNextProfileMusic();
                    }, ExportSettings.MediaSettings.File, controllingSize, null);
            if (!thumbReady) {
                return;
            }
        }
        finishProfileMusicSlice();
    }

    private void finishProfileMusicSlice() {
        ArrayList<ExportData.Message> slice = profileMusicProcess.slice;
        profileMusicProcess.slice = null;
        if (!slice.isEmpty()) {
            profileMusicProcess.processed += slice.size();
            if (!profileMusicProcess.handleSlice.run(slice)) {
                return;
            }
        }
        if (profileMusicProcess.lastSlice) {
            ProfileMusicProcess process = profileMusicProcess;
            profileMusicProcess = null;
            process.finish.run();
            return;
        }
        mainRequest(savedMusicRequest(profileMusicProcess.offset, PROFILE_MUSIC_SLICE_LIMIT), this::handleProfileMusicSlice);
    }

    public void requestContacts(Utilities.Callback<ExportData.ContactsList> done) {
        contactsProcess = new ContactsProcess();
        contactsProcess.done = done;
        mainRequest(new TakeoutRequests.TL_contacts_getSaved(), response -> {
            ArrayList<TakeoutRequests.TL_savedPhoneContact> contacts = new ArrayList<>();
            if (response instanceof Vector<?> vector) {
                for (Object object : vector.objects) {
                    if (object instanceof TakeoutRequests.TL_savedPhoneContact contact) {
                        contacts.add(contact);
                    }
                }
            }
            contactsProcess.result = ExportParser.parseContactsList(contacts);
            requestTopPeersSlice();
        });
    }

    private void requestTopPeersSlice() {
        TLRPC.TL_contacts_getTopPeers request = new TLRPC.TL_contacts_getTopPeers();
        request.correspondents = true;
        request.bots_inline = true;
        request.phone_calls = true;
        request.offset = contactsProcess.topPeersOffset;
        request.limit = TOP_PEER_SLICE_LIMIT;
        mainRequest(request, response -> {
            if (!(response instanceof TLRPC.contacts_TopPeers topPeers) || !ExportParser.appendTopPeers(contactsProcess.result, topPeers)) {
                error("Unexpected data in ApiWrap::requestTopPeersSlice.");
                return;
            }
            boolean loaded = true;
            if (topPeers instanceof TLRPC.TL_contacts_topPeers data) {
                for (TLRPC.TL_topPeerCategoryPeers category : data.categories) {
                    if (contactsProcess.topPeersOffset + category.peers.size() < category.count) {
                        loaded = false;
                        break;
                    }
                }
            }
            if (loaded) {
                ContactsProcess process = contactsProcess;
                contactsProcess = null;
                process.done.run(process.result);
            } else {
                ExportData.ContactsList result = contactsProcess.result;
                contactsProcess.topPeersOffset = Math.max(Math.max(result.correspondents.size(), result.inlineBots.size()), result.phoneCalls.size());
                requestTopPeersSlice();
            }
        });
    }

    public void requestSessions(Utilities.Callback<ExportData.SessionsList> done) {
        mainRequest(new TL_account.getAuthorizations(), response -> {
            if (!(response instanceof TL_account.authorizations authorizations)) {
                error("Unexpected sessions response.");
                return;
            }
            mainRequest(new TL_account.getWebAuthorizations(), webResponse -> {
                TL_account.webAuthorizations web = webResponse instanceof TL_account.webAuthorizations w ? w : null;
                done.run(ExportParser.parseSessionsList(authorizations, web));
            });
        });
    }

    public void requestMessages(ExportData.DialogInfo info, Progress<ExportData.DialogInfo> start, Progress<DownloadProgress> progress,
                                Progress<ExportData.MessagesSlice> slice, Runnable done) {
        chatProcess = new ChatProcess();
        chatProcess.context.selfPeerId = selfId;
        chatProcess.info = info;
        chatProcess.start = start;
        chatProcess.fileProgress = progress;
        chatProcess.handleSlice = slice;
        chatProcess.done = done;
        if (info.splits.isEmpty()) {
            if (chatProcess.start.run(info)) {
                finishMessages();
            }
            return;
        }
        requestMessagesCount(0);
    }

    public void requestTopicMessages(ExportData.DialogInfo info, int topicRootId, Progress<Integer> start, Progress<DownloadProgress> progress,
                                     Progress<ExportData.MessagesSlice> slice, Runnable done) {
        chatProcess = new ChatProcess();
        chatProcess.context.selfPeerId = selfId;
        chatProcess.info = info;
        chatProcess.topicRootId = topicRootId;
        chatProcess.fileProgress = progress;
        chatProcess.handleSlice = slice;
        chatProcess.done = done;
        if (!(info.input instanceof TLRPC.TL_inputPeerChannel input)) {
            error("Topic export requires a channel peer.");
            return;
        }
        TLRPC.TL_channels_getMessages rootRequest = new TLRPC.TL_channels_getMessages();
        TLRPC.TL_inputChannel channel = new TLRPC.TL_inputChannel();
        channel.channel_id = input.channel_id;
        channel.access_hash = input.access_hash;
        rootRequest.channel = channel;
        rootRequest.id.add(topicRootId);
        mainRequest(rootRequest, rootResponse -> {
            ExportData.MessagesSlice root = rootResponse instanceof TLRPC.messages_Messages messages && !(rootResponse instanceof TLRPC.TL_messages_messagesNotModified)
                    ? ExportParser.parseMessagesSlice(chatProcess.context, messages, info.relativePath)
                    : new ExportData.MessagesSlice();
            root.list.removeIf(message -> message.date == 0);
            requestTopicReplies(0, 0, MESSAGES_SLICE_LIMIT, result -> {
                int count = messagesCount(result);
                if (count < 0) {
                    error("Unexpected messagesNotModified received.");
                    return;
                }
                if (!start.run(count + root.list.size())) {
                    return;
                }
                if (!root.list.isEmpty()) {
                    startMessagesSlice(root);
                } else {
                    requestTopicMessagesSlice();
                }
            });
        }, error -> {
            requestTopicReplies(0, 0, MESSAGES_SLICE_LIMIT, result -> {
                int count = messagesCount(result);
                if (count < 0) {
                    error("Unexpected messagesNotModified received.");
                    return;
                }
                if (start.run(count)) {
                    requestTopicMessagesSlice();
                }
            });
            return true;
        });
    }

    private void requestTopicMessagesSlice() {
        int offsetId = chatProcess.topicOffsetId == 0 ? 1 : chatProcess.topicOffsetId + 1;
        requestTopicReplies(offsetId, -MESSAGES_SLICE_LIMIT, MESSAGES_SLICE_LIMIT, result -> {
            if (result instanceof TLRPC.TL_messages_messagesNotModified) {
                error("Unexpected messagesNotModified received.");
                return;
            }
            if (result instanceof TLRPC.TL_messages_messages) {
                chatProcess.lastSlice = true;
            }
            startMessagesSlice(ExportParser.parseMessagesSlice(chatProcess.context, result, chatProcess.info.relativePath));
        });
    }

    private void requestTopicReplies(int offsetId, int addOffset, int limit, Utilities.Callback<TLRPC.messages_Messages> done) {
        TLRPC.TL_messages_getReplies request = new TLRPC.TL_messages_getReplies();
        request.peer = chatProcess.info.input;
        request.msg_id = chatProcess.topicRootId;
        request.offset_id = offsetId;
        request.add_offset = addOffset;
        request.limit = limit;
        mainRequest(request, response -> {
            if (!(response instanceof TLRPC.messages_Messages messages)) {
                error("Unexpected messages response.");
                return;
            }
            done.run(messages);
        });
    }

    private static int messagesCount(TLRPC.messages_Messages messages) {
        if (messages instanceof TLRPC.TL_messages_messagesNotModified) {
            return -1;
        } else if (messages instanceof TLRPC.TL_messages_messagesSlice || messages instanceof TLRPC.TL_messages_channelMessages) {
            return messages.count;
        }
        return messages.messages.size();
    }

    private void requestMessagesCount(int localSplitIndex) {
        requestChatMessages(chatProcess.info.splits.get(localSplitIndex), 0, 0, 0, 1, result -> {
            int count = messagesCount(result);
            if (count < 0) {
                error("Unexpected messagesNotModified received.");
                return;
            }
            if (!ExportParser.singleMessageAfter(result, settings.singlePeerFrom)) {
                messagesCountLoaded(localSplitIndex, 0);
                return;
            }
            requestMessagesCountFromDate(localSplitIndex, count);
        });
    }

    private void requestMessagesCountFromDate(int localSplitIndex, int count) {
        if (settings.singlePeerFrom <= 0 || chatProcess.info.onlyMyMessages) {
            checkFirstMessageDate(localSplitIndex, count);
            return;
        }
        requestChatMessages(chatProcess.info.splits.get(localSplitIndex), 0, settings.singlePeerFrom - 1, 0, 1, result -> {
            boolean hasOffset = (result instanceof TLRPC.TL_messages_messagesSlice || result instanceof TLRPC.TL_messages_channelMessages)
                    && (result.flags & 4) != 0;
            checkFirstMessageDate(localSplitIndex, hasOffset ? result.offset_id_offset : count);
        });
    }

    private void checkFirstMessageDate(int localSplitIndex, int count) {
        if (settings.singlePeerTill <= 0) {
            messagesCountLoaded(localSplitIndex, count);
            return;
        }
        requestChatMessages(chatProcess.info.splits.get(localSplitIndex), 1, 0, -1, 1, result ->
                messagesCountLoaded(localSplitIndex, ExportParser.singleMessageBefore(result, settings.singlePeerTill) ? count : 0));
    }

    private void messagesCountLoaded(int localSplitIndex, int count) {
        chatProcess.info.messagesCountPerSplit.set(localSplitIndex, count);
        if (localSplitIndex + 1 < chatProcess.info.splits.size()) {
            requestMessagesCount(localSplitIndex + 1);
        } else if (chatProcess.start.run(chatProcess.info)) {
            requestMessagesSlice();
        }
    }

    private void requestMessagesSlice() {
        int count = chatProcess.info.messagesCountPerSplit.get(chatProcess.localSplitIndex);
        if (count == 0) {
            startMessagesSlice(new ExportData.MessagesSlice());
            return;
        }
        int offsetDate = chatProcess.largestIdPlusOne == 1 && settings.singlePeerFrom > 0 ? settings.singlePeerFrom - 1 : 0;
        requestChatMessages(chatProcess.info.splits.get(chatProcess.localSplitIndex), chatProcess.largestIdPlusOne, offsetDate,
                -MESSAGES_SLICE_LIMIT, MESSAGES_SLICE_LIMIT, result -> {
                    if (result instanceof TLRPC.TL_messages_messagesNotModified) {
                        error("Unexpected messagesNotModified received.");
                        return;
                    }
                    if (result instanceof TLRPC.TL_messages_messages) {
                        chatProcess.lastSlice = true;
                    }
                    startMessagesSlice(ExportParser.parseMessagesSlice(chatProcess.context, result, chatProcess.info.relativePath));
                });
    }

    private void requestChatMessages(int splitIndex, int offsetId, int offsetDate, int addOffset, int limit, Utilities.Callback<TLRPC.messages_Messages> done) {
        chatProcess.requestDone = done;
        ResponseCallback doneHandler = response -> {
            if (!(response instanceof TLRPC.messages_Messages messages)) {
                error("Unexpected messages response.");
                return;
            }
            Utilities.Callback<TLRPC.messages_Messages> callback = chatProcess.requestDone;
            chatProcess.requestDone = null;
            callback.run(messages);
        };
        ExportData.DialogInfo info = chatProcess.info;
        TLRPC.InputPeer realPeerInput = splitIndex >= 0 ? info.input : info.migratedFromInput;
        int realSplitIndex = splitIndex >= 0 ? splitIndex : splits.size() + splitIndex;
        if (info.onlyMyMessages) {
            TLRPC.TL_messages_search request = new TLRPC.TL_messages_search();
            request.peer = realPeerInput;
            request.q = "";
            request.from_id = info.isMonoforum ? info.monoforumBroadcastInput : new TLRPC.TL_inputPeerSelf();
            request.filter = new TLRPC.TL_inputMessagesFilterEmpty();
            request.offset_id = offsetId;
            request.add_offset = addOffset;
            request.limit = limit;
            splitRequest(realSplitIndex, request, doneHandler, null);
        } else {
            TLRPC.TL_messages_getHistory request = new TLRPC.TL_messages_getHistory();
            request.peer = realPeerInput;
            request.offset_id = offsetDate != 0 ? 0 : offsetId;
            request.offset_date = offsetDate;
            request.add_offset = addOffset;
            request.limit = limit;
            splitRequest(realSplitIndex, request, doneHandler, error -> {
                if ("CHANNEL_PRIVATE".equals(error.text) && realPeerInput instanceof TLRPC.TL_inputPeerChannel && !chatProcess.info.onlyMyMessages) {
                    chatProcess.info.onlyMyMessages = true;
                    Utilities.Callback<TLRPC.messages_Messages> callback = chatProcess.requestDone;
                    chatProcess.requestDone = null;
                    requestChatMessages(splitIndex, offsetId, offsetDate, addOffset, limit, callback);
                    return true;
                }
                return false;
            });
        }
    }

    private void startMessagesSlice(ExportData.MessagesSlice slice) {
        if (slice.list.isEmpty()) {
            chatProcess.lastSlice = true;
        }
        chatProcess.slice = slice;
        chatProcess.fileIndex = 0;
        collectMessagesCustomEmoji(slice);
        resolveCustomEmoji();
    }

    private static long customEmojiId(String data) {
        try {
            return Long.parseLong(data);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void collectMessagesCustomEmoji(ExportData.MessagesSlice slice) {
        for (ExportData.Message message : slice.list) {
            for (ExportData.TextPart part : message.text) {
                if (part.type == ExportData.TextPart.Type.CustomEmoji) {
                    long id = customEmojiId(part.additional);
                    if (id != 0 && !resolvedCustomEmoji.containsKey(id)) {
                        unresolvedCustomEmoji.add(id);
                    }
                }
            }
            for (ExportData.Reaction reaction : message.reactions) {
                if (reaction.type == ExportData.Reaction.Type.CustomEmoji) {
                    long id = customEmojiId(reaction.documentId);
                    if (id != 0 && !resolvedCustomEmoji.containsKey(id)) {
                        unresolvedCustomEmoji.add(id);
                    }
                }
            }
        }
    }

    private void resolveCustomEmoji() {
        if (unresolvedCustomEmoji.isEmpty()) {
            loadNextMessageFile();
            return;
        }
        ArrayList<Long> ids = new ArrayList<>(Math.min(unresolvedCustomEmoji.size(), MAX_EMOJI_PER_REQUEST));
        for (Long id : unresolvedCustomEmoji) {
            if (ids.size() >= MAX_EMOJI_PER_REQUEST) {
                break;
            }
            ids.add(id);
        }
        ids.forEach(unresolvedCustomEmoji::remove);
        Runnable finalize = () -> {
            for (Long id : ids) {
                if (!resolvedCustomEmoji.containsKey(id)) {
                    ExportData.Document unavailable = new ExportData.Document();
                    unavailable.file.skipReason = ExportData.File.SkipReason.Unavailable;
                    resolvedCustomEmoji.put(id, unavailable);
                }
            }
            resolveCustomEmoji();
        };
        TLRPC.TL_messages_getCustomEmojiDocuments request = new TLRPC.TL_messages_getCustomEmojiDocuments();
        request.document_id = ids;
        mainRequest(request, response -> {
            if (response instanceof Vector<?> vector) {
                for (Object object : vector.objects) {
                    if (object instanceof TLRPC.Document document) {
                        ExportData.Document parsed = ExportParser.parseDocument(chatProcess.context, document, chatProcess.info.relativePath, 0);
                        resolvedCustomEmoji.put(parsed.id, parsed);
                    }
                }
            }
            finalize.run();
        }, error -> {
            FileLog.e("export: failed to get documents for emoji");
            finalize.run();
            return true;
        });
    }

    private String customEmojiReady(String data, ExportData.Message message) {
        long id = customEmojiId(data);
        if (id == 0) {
            return data;
        }
        ExportData.Document document = resolvedCustomEmoji.get(id);
        if (document == null) {
            return ExportData.TextPart.UNAVAILABLE_EMOJI;
        }
        ExportData.File file = document.file;
        ExportData.FileOrigin origin = new ExportData.FileOrigin(0, null, 0, 0, id);
        boolean ready = processFileLoad(file, origin,
                value -> chatProcess.fileProgress.run(downloadProgress(chatProcess.fileIndex, value)),
                path -> {
                    ExportData.Document resolved = resolvedCustomEmoji.get(id);
                    if (resolved != null) {
                        markLoaded(resolved.file, path);
                    }
                    loadNextMessageFile();
                }, ExportSettings.MediaSettings.Sticker, file.size, message);
        if (!ready) {
            return null;
        }
        return switch (file.skipReason) {
            case Unavailable -> ExportData.TextPart.UNAVAILABLE_EMOJI;
            case FileType, FileSize, DateLimits -> "";
            case None -> file.relativePath;
        };
    }

    private boolean messageCustomEmojiReady(ExportData.Message message) {
        for (ExportData.TextPart part : message.text) {
            if (part.type == ExportData.TextPart.Type.CustomEmoji) {
                String data = customEmojiReady(part.additional, message);
                if (data == null) {
                    return false;
                }
                part.additional = data;
            }
        }
        for (ExportData.Reaction reaction : message.reactions) {
            if (reaction.type == ExportData.Reaction.Type.CustomEmoji) {
                String data = customEmojiReady(reaction.documentId, message);
                if (data == null) {
                    return false;
                }
                reaction.documentId = data;
            }
        }
        return true;
    }

    private static int mediaType(ExportData.Media media) {
        if (media.content instanceof ExportData.Photo) {
            return ExportSettings.MediaSettings.Photo;
        } else if (media.content instanceof ExportData.Document document) {
            if (document.isSticker) {
                return ExportSettings.MediaSettings.Sticker;
            } else if (document.isVideoMessage) {
                return ExportSettings.MediaSettings.VideoMessage;
            } else if (document.isVoiceMessage) {
                return ExportSettings.MediaSettings.VoiceMessage;
            } else if (document.isAnimated) {
                return ExportSettings.MediaSettings.GIF;
            } else if (document.isVideoFile) {
                return ExportSettings.MediaSettings.Video;
            }
            return ExportSettings.MediaSettings.File;
        }
        return 0;
    }

    private void loadNextMessageFile() {
        ArrayList<ExportData.Message> list = chatProcess.slice.list;
        for (; chatProcess.fileIndex < list.size(); chatProcess.fileIndex++) {
            ExportData.Message message = list.get(chatProcess.fileIndex);
            if (ExportParser.skipMessageByDate(message, settings)) {
                continue;
            }
            if (!messageCustomEmojiReady(message)) {
                return;
            }
            ExportData.FileOrigin origin = currentFileMessageOrigin(message);
            int type = mediaType(message.media);
            long controllingSize = message.file().size;
            boolean ready = processFileLoad(message.file(), origin,
                    value -> chatProcess.fileProgress.run(downloadProgress(chatProcess.fileIndex, value)),
                    path -> {
                        markLoaded(chatProcess.slice.list.get(chatProcess.fileIndex).file(), path);
                        loadNextMessageFile();
                    }, type, controllingSize, message);
            if (!ready) {
                return;
            }
            boolean thumbReady = processFileLoad(message.thumb().file, origin,
                    value -> chatProcess.fileProgress.run(downloadProgress(chatProcess.fileIndex, value)),
                    path -> {
                        markLoaded(chatProcess.slice.list.get(chatProcess.fileIndex).thumb().file, path);
                        loadNextMessageFile();
                    }, type, controllingSize, message);
            if (!thumbReady) {
                return;
            }
        }
        finishMessagesSlice();
    }

    private ExportData.FileOrigin currentFileMessageOrigin(ExportData.Message message) {
        if (chatProcess.topicRootId != 0) {
            return new ExportData.FileOrigin(0, chatProcess.info.input, message.id, 0, 0);
        }
        int splitIndex = chatProcess.info.splits.get(chatProcess.localSplitIndex);
        int split = splitIndex >= 0 ? splitIndex : splits.size() + splitIndex;
        TLRPC.InputPeer peer = splitIndex >= 0 ? chatProcess.info.input : chatProcess.info.migratedFromInput;
        return new ExportData.FileOrigin(split, peer, message.id, 0, 0);
    }

    private void finishMessagesSlice() {
        ExportData.MessagesSlice slice = chatProcess.slice;
        chatProcess.slice = null;
        if (chatProcess.topicRootId != 0) {
            if (!slice.list.isEmpty()) {
                chatProcess.topicOffsetId = Math.max(chatProcess.topicOffsetId, slice.list.get(slice.list.size() - 1).id);
                if (!chatProcess.handleSlice.run(slice)) {
                    return;
                }
            }
            if (chatProcess.lastSlice) {
                finishMessages();
            } else {
                requestTopicMessagesSlice();
            }
            return;
        }
        if (!slice.list.isEmpty()) {
            chatProcess.largestIdPlusOne = slice.list.get(slice.list.size() - 1).id + 1;
            if (chatProcess.info.splits.get(chatProcess.localSplitIndex) < 0) {
                slice = ExportParser.adjustMigrateMessageIds(slice);
            }
            if (!chatProcess.handleSlice.run(slice)) {
                return;
            }
        }
        if (chatProcess.lastSlice && ++chatProcess.localSplitIndex < chatProcess.info.splits.size()) {
            chatProcess.lastSlice = false;
            chatProcess.largestIdPlusOne = 1;
        }
        if (!chatProcess.lastSlice) {
            requestMessagesSlice();
        } else {
            finishMessages();
        }
    }

    private void finishMessages() {
        ChatProcess process = chatProcess;
        chatProcess = null;
        process.done.run();
    }

    private DownloadProgress downloadProgress(int itemIndex, FileProgress value) {
        return new DownloadProgress(fileProcess.randomId, fileProcess.relativePath, itemIndex, value.ready(), value.total());
    }

    private static void markLoaded(ExportData.File file, String relativePath) {
        file.relativePath = relativePath;
        if (relativePath.isEmpty() && file.skipReason == ExportData.File.SkipReason.None) {
            file.skipReason = ExportData.File.SkipReason.Unavailable;
        }
    }

    private boolean processFileLoad(ExportData.File file, ExportData.FileOrigin origin, Progress<FileProgress> progress,
                                    Utilities.Callback<String> done, int type, long controllingSize, ExportData.Message message) {
        if (!file.relativePath.isEmpty() || file.skipReason != ExportData.File.SkipReason.None) {
            return true;
        } else if (file.location == null && !file.hasContent()) {
            file.skipReason = ExportData.File.SkipReason.Unavailable;
            return true;
        } else if (writePreloadedFile(file, origin)) {
            return !file.relativePath.isEmpty();
        } else if (message != null && ExportParser.skipMessageByDate(message, settings)) {
            file.skipReason = ExportData.File.SkipReason.DateLimits;
            return true;
        } else if ((settings.media.types & type) != type) {
            file.skipReason = ExportData.File.SkipReason.FileType;
            return true;
        } else if (controllingSize > settings.media.sizeLimit) {
            file.skipReason = ExportData.File.SkipReason.FileSize;
            return true;
        }
        loadFile(file, origin, progress, done);
        return false;
    }

    private boolean writePreloadedFile(ExportData.File file, ExportData.FileOrigin origin) {
        String key = file.location != null ? ExportParser.computeLocationKey(file.location) : null;
        String cached = key != null ? fileCache.get(key) : null;
        if (cached != null) {
            file.relativePath = cached;
            return true;
        } else if (file.hasContent()) {
            FileProcess process = prepareFileProcess(file, origin);
            Result result = process.file.writeBlock(file.content);
            if (result.isSuccess()) {
                file.relativePath = process.relativePath;
                if (key != null) {
                    fileCache.put(key, file.relativePath);
                }
            } else {
                ioError(result);
            }
            return true;
        }
        return false;
    }

    private FileProcess prepareFileProcess(ExportData.File file, ExportData.FileOrigin origin) {
        String relativePath = OutputFile.prepareRelativePath(settings.path, file.suggestedPath);
        FileProcess result = new FileProcess(settings.path + relativePath, stats);
        result.relativePath = relativePath;
        result.location = file.location;
        result.size = file.size;
        result.origin = origin;
        result.source = file;
        result.randomId = Utilities.random.nextLong();
        return result;
    }

    private void loadFile(ExportData.File file, ExportData.FileOrigin origin, Progress<FileProgress> progress, Utilities.Callback<String> done) {
        fileProcess = prepareFileProcess(file, origin);
        fileProcess.progress = progress;
        fileProcess.done = done;
        if (progress != null && !progress.run(new FileProgress(fileProcess.file.size(), fileProcess.size))) {
            return;
        }
        loadFilePart();
    }

    private void loadFilePart() {
        if (fileProcess == null || fileProcess.requestId != 0 || (fileProcess.size > 0 && fileProcess.offset >= fileProcess.size)) {
            return;
        }
        long offset = fileProcess.offset;
        fileProcess.offset += FILE_CHUNK_SIZE;
        fileProcess.requestId = fileRequest(fileProcess.location, offset);
    }

    private void filePartDone(long offset, TLObject response) {
        if (response instanceof TLRPC.TL_upload_fileCdnRedirect) {
            error("Cdn redirect is not supported.");
            return;
        }
        if (!(response instanceof TLRPC.TL_upload_file data)) {
            error("Unexpected file part response.");
            return;
        }
        ByteBuffer bytes = data.bytes != null ? data.bytes.buffer : null;
        int length = bytes != null ? data.bytes.limit() : 0;
        if (length == 0) {
            if (fileProcess.size > 0 && fileProcess.file.size() < fileProcess.size) {
                error("Empty bytes received in file part.");
                return;
            }
            Result result = fileProcess.file.writeBlock(new byte[0]);
            if (!result.isSuccess()) {
                ioError(result);
                return;
            }
        } else {
            bytes.position(0);
            bytes.limit(length);
            Result result = fileProcess.file.writeBlock(bytes);
            if (!result.isSuccess()) {
                ioError(result);
                return;
            }
            if (fileProcess.progress != null) {
                fileProcess.progress.run(new FileProgress(fileProcess.file.size(), fileProcess.size));
            }
            if (fileProcess.size == 0 || fileProcess.size > fileProcess.offset) {
                loadFilePart();
                return;
            }
        }
        FileProcess process = fileProcess;
        fileProcess = null;
        String key = process.location != null ? ExportParser.computeLocationKey(process.location) : null;
        if (key != null) {
            fileCache.put(key, process.relativePath);
        }
        process.done.run(process.relativePath);
    }

    private void filePartUnavailable() {
        FileLog.e("export: file unavailable");
        FileProcess process = fileProcess;
        fileProcess = null;
        process.done.run("");
    }

    private void filePartRetryReference(long offset) {
        fileProcess.requestId = fileRequest(fileProcess.location, offset);
    }

    private void filePartRefreshReference(long offset) {
        ExportData.FileOrigin origin = fileProcess.origin;
        FailCallback unavailable = error -> {
            fileProcess.requestId = 0;
            filePartUnavailable();
            return true;
        };
        if (origin.storyId() != 0) {
            TL_stories.TL_stories_getStoriesByID request = new TL_stories.TL_stories_getStoriesByID();
            request.peer = new TLRPC.TL_inputPeerSelf();
            request.id.add(origin.storyId());
            fileProcess.requestId = mainRequest(request, response -> {
                fileProcess.requestId = 0;
                ExportData.StoriesSlice stories = response instanceof TL_stories.TL_stories_stories result
                        ? ExportParser.parseStoriesSlice(result.stories, 0)
                        : new ExportData.StoriesSlice();
                for (ExportData.Story story : stories.list) {
                    if (story.id == origin.storyId() && (ExportParser.refreshFileReference(fileProcess.location, story.file().location)
                            || ExportParser.refreshFileReference(fileProcess.location, story.thumb().file.location))) {
                        filePartRetryReference(offset);
                        return;
                    }
                }
                filePartUnavailable();
            }, unavailable);
        } else if (origin.customEmojiId() != 0) {
            TLRPC.TL_messages_getCustomEmojiDocuments request = new TLRPC.TL_messages_getCustomEmojiDocuments();
            request.document_id.add(origin.customEmojiId());
            String folder = chatProcess != null ? chatProcess.info.relativePath : "";
            fileProcess.requestId = mainRequest(request, response -> {
                fileProcess.requestId = 0;
                if (response instanceof Vector<?> vector) {
                    for (Object object : vector.objects) {
                        if (object instanceof TLRPC.Document document && document.id == origin.customEmojiId()) {
                            ExportData.Document parsed = ExportParser.parseDocument(new ExportData.ParseMediaContext(), document, folder, 0);
                            if (ExportParser.refreshFileReference(fileProcess.location, parsed.file.location)
                                    || ExportParser.refreshFileReference(fileProcess.location, parsed.thumb.file.location)) {
                                filePartRetryReference(offset);
                                return;
                            }
                        }
                    }
                }
                filePartUnavailable();
            }, unavailable);
        } else if (origin.messageId() == 0) {
            error("FILE_REFERENCE error for non-message file.");
        } else {
            ResponseCallback extract = response -> {
                fileProcess.requestId = 0;
                if (!(response instanceof TLRPC.messages_Messages messages) || response instanceof TLRPC.TL_messages_messagesNotModified) {
                    error("Unexpected messagesNotModified received.");
                    return;
                }
                ExportData.ParseMediaContext context = new ExportData.ParseMediaContext();
                context.selfPeerId = selfId;
                String folder = chatProcess != null ? chatProcess.info.relativePath : "";
                for (ExportData.Message message : ExportParser.parseMessagesSlice(context, messages, folder).list) {
                    if (message.id == origin.messageId() && (ExportParser.refreshFileReference(fileProcess.location, message.file().location)
                            || ExportParser.refreshFileReference(fileProcess.location, message.thumb().file.location))) {
                        filePartRetryReference(offset);
                        return;
                    }
                }
                filePartUnavailable();
            };
            TLRPC.InputPeer peer = origin.peer();
            if (peer instanceof TLRPC.TL_inputPeerChannel || peer instanceof TLRPC.TL_inputPeerChannelFromMessage) {
                TLRPC.TL_channels_getMessages request = new TLRPC.TL_channels_getMessages();
                if (peer instanceof TLRPC.TL_inputPeerChannel) {
                    TLRPC.TL_inputChannel channel = new TLRPC.TL_inputChannel();
                    channel.channel_id = peer.channel_id;
                    channel.access_hash = peer.access_hash;
                    request.channel = channel;
                } else {
                    TLRPC.TL_inputChannelFromMessage channel = new TLRPC.TL_inputChannelFromMessage();
                    channel.peer = peer.peer;
                    channel.msg_id = peer.msg_id;
                    channel.channel_id = peer.channel_id;
                    request.channel = channel;
                }
                request.id.add(origin.messageId());
                fileProcess.requestId = mainRequest(request, extract, unavailable);
            } else {
                TLRPC.TL_messages_getMessages request = new TLRPC.TL_messages_getMessages();
                request.id.add(origin.messageId());
                fileProcess.requestId = splitRequest(origin.split(), request, extract, unavailable);
            }
        }
    }

    public void finishExport(Runnable done) {
        TakeoutRequests.TL_account_finishTakeoutSession request = new TakeoutRequests.TL_account_finishTakeoutSession();
        request.success = true;
        mainRequest(request, response -> {
            takeoutId = null;
            done.run();
        }, error -> {
            FileLog.e("export: finishTakeoutSession failed " + error.text);
            takeoutId = null;
            done.run();
            return true;
        });
    }

    public void cancelExportFast() {
        if (cancelled) {
            return;
        }
        cancelled = true;
        ConnectionsManager connectionsManager = ConnectionsManager.getInstance(account);
        for (int requestId : requests) {
            connectionsManager.cancelRequest(requestId, true);
        }
        requests.clear();
        if (takeoutId != null) {
            TakeoutRequests.TL_account_finishTakeoutSession request = new TakeoutRequests.TL_account_finishTakeoutSession();
            TakeoutRequests.TL_invokeWithTakeout wrapper = new TakeoutRequests.TL_invokeWithTakeout();
            wrapper.takeout_id = takeoutId;
            wrapper.query = request;
            connectionsManager.sendRequest(wrapper, (response, error) -> {
            });
            takeoutId = null;
        }
    }
}
