/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export;

import android.content.SharedPreferences;
import android.os.SystemClock;

import com.exteragram.messenger.export.api.ExportApi;
import com.exteragram.messenger.export.api.ExportData;
import com.exteragram.messenger.export.output.AbstractWriter;
import com.exteragram.messenger.export.output.ExportFiles;
import com.exteragram.messenger.export.output.OutputFile;
import com.exteragram.messenger.export.output.Result;

import org.telegram.messenger.AndroidUtilities;
import android.content.Context;
import java.io.File;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public class ExportController {

    private static final String PREF_DELAY_UNTIL = "export_delay_until";
    public static final String PEERS_PREFERENCES = "export_peers";
    private static final String PREF_LAST_PATH = "export_last_path";
    private static final String PREF_LAST_TIME = "export_last_time";
    private static final String PREF_LAST_FILES = "export_last_files";
    private static final String PREF_LAST_BYTES = "export_last_bytes";

    public record LastExport(String path, long time, int filesCount, long bytesCount) {
    }

    private static final ExportController[] Instance = new ExportController[UserConfig.MAX_ACCOUNT_COUNT];
    private static volatile DispatchQueue queue;

    private final int account;
    private ExportState state;
    private Process process;
    private ExportSettings lastSettings;

    private ExportController(int account) {
        this.account = account;
    }

    public static ExportController getInstance(int account) {
        ExportController localInstance = Instance[account];
        if (localInstance == null) {
            synchronized (ExportController.class) {
                localInstance = Instance[account];
                if (localInstance == null) {
                    Instance[account] = localInstance = new ExportController(account);
                }
            }
        }
        return localInstance;
    }

    private static DispatchQueue queue() {
        DispatchQueue localQueue = queue;
        if (localQueue == null) {
            synchronized (ExportController.class) {
                localQueue = queue;
                if (localQueue == null) {
                    queue = localQueue = new DispatchQueue("exportQueue");
                }
            }
        }
        return localQueue;
    }

    public ExportState getState() {
        return state;
    }

    public boolean isRunning() {
        return state instanceof ExportState.Processing;
    }

    public void start(ExportSettings settings) {
        if (isRunning()) {
            return;
        }
        lastSettings = settings.copy();
        Process newProcess = new Process(settings.copy());
        state = new ExportState.Processing();
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.exportStateChanged);
        ExportService.start();
        queue().postRunnable(() -> {
            process = newProcess;
            newProcess.start();
        });
    }

    public void cancel() {
        queue().postRunnable(() -> {
            if (process != null) {
                process.cancel();
            }
        });
    }

    public boolean canRetry() {
        return lastSettings != null && !isRunning();
    }

    public void retry() {
        if (canRetry()) {
            start(lastSettings);
        }
    }

    public long getDelayUntil() {
        long until = MessagesController.getMainSettings(account).getLong(PREF_DELAY_UNTIL, 0);
        return until > System.currentTimeMillis() ? until : 0;
    }

    public LastExport getLastExport() {
        SharedPreferences preferences = MessagesController.getMainSettings(account);
        String path = preferences.getString(PREF_LAST_PATH, null);
        if (path == null) {
            return null;
        }
        return new LastExport(path, preferences.getLong(PREF_LAST_TIME, 0), preferences.getInt(PREF_LAST_FILES, 0), preferences.getLong(PREF_LAST_BYTES, 0));
    }

    private void saveTerminalState(ExportState newState) {
        if (newState instanceof ExportState.Finished finished) {
            if (lastSettings != null && lastSettings.onlySinglePeer()) {
                ApplicationLoader.applicationContext.getSharedPreferences(PEERS_PREFERENCES, Context.MODE_PRIVATE).edit()
                        .putLong(new File(finished.path).getAbsolutePath(), DialogObject.getPeerDialogId(lastSettings.singlePeer))
                        .apply();
            }
            MessagesController.getMainSettings(account).edit()
                    .remove(PREF_DELAY_UNTIL)
                    .putString(PREF_LAST_PATH, finished.path)
                    .putLong(PREF_LAST_TIME, System.currentTimeMillis())
                    .putInt(PREF_LAST_FILES, finished.filesCount)
                    .putLong(PREF_LAST_BYTES, finished.bytesCount)
                    .apply();
        } else if (newState instanceof ExportState.ApiError apiError && apiError.isDelay()) {
            MessagesController.getMainSettings(account).edit().putLong(PREF_DELAY_UNTIL, apiError.delayUntil()).apply();
        }
    }

    public void clearState() {
        if (!isRunning()) {
            state = null;
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.exportStateChanged);
        }
    }

    private void updateState(Process source, ExportState newState) {
        AndroidUtilities.runOnUIThread(() -> {
            if (source.publishedTerminal) {
                return;
            }
            if (!(newState instanceof ExportState.Processing)) {
                source.publishedTerminal = true;
                saveTerminalState(newState);
            }
            state = newState instanceof ExportState.Cancelled ? null : newState;
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.exportStateChanged);
        });
    }

    private class Process implements ExportApi.Listener {

        private final ExportSettings settings;
        private final OutputFile.Stats stats = new OutputFile.Stats();
        private final ArrayList<ExportState.Processing.Step> steps = new ArrayList<>();
        private final ExportApi api;
        private AbstractWriter writer;
        private final long startedAt = SystemClock.elapsedRealtime();
        private boolean stopped;
        private boolean publishedTerminal;
        private int chatsExported;
        private int messagesExported;

        private int stepIndex = -1;
        private int[] substepsInStep = new int[ExportState.Processing.Step.values().length];
        private int substepsTotal;
        private int substepsPassed;
        private ExportState.Processing.Step lastProcessingStep = ExportState.Processing.Step.Initializing;

        private ExportData.DialogsInfo dialogsInfo = new ExportData.DialogsInfo();
        private int dialogIndex = -1;
        private int messagesWritten;
        private int messagesCount;
        private int userpicsWritten;
        private int userpicsCount;
        private int storiesWritten;
        private int storiesCount;
        private int profileMusicWritten;
        private int profileMusicCount;

        Process(ExportSettings settings) {
            this.settings = settings;
            this.api = new ExportApi(account, queue(), this);
        }

        void start() {
            String path = ExportFiles.createExportFolder(settings.outputFolder, settings.onlySinglePeer());
            if (path == null) {
                ioError(Result.error(ExportFiles.baseFolder(settings.outputFolder).getAbsolutePath()));
                return;
            }
            settings.path = path;
            writer = AbstractWriter.create(settings.format);
            fillExportSteps();
            exportNext();
        }

        void cancel() {
            if (stopped) {
                return;
            }
            api.cancelExportFast();
            stop(new ExportState.Cancelled());
        }

        @Override
        public void onApiError(TLRPC.TL_error error) {
            if (stopped) {
                return;
            }
            FileLog.e("export: api error " + error.code + " " + error.text);
            api.cancelExportFast();
            stop(new ExportState.ApiError(error.text));
        }

        @Override
        public void onIoError(Result result) {
            ioError(result);
        }

        private void ioError(Result result) {
            if (stopped) {
                return;
            }
            FileLog.e("export: io error " + result.path);
            api.cancelExportFast();
            stop(new ExportState.IoError(result.path));
        }

        private void stop(ExportState terminalState) {
            stopped = true;
            if (process == this) {
                process = null;
            }
            updateState(this, terminalState);
        }

        private boolean ioCatchError(Result result) {
            if (!result.isSuccess()) {
                ioError(result);
                return true;
            }
            return false;
        }

        private void setState(ExportState.Processing newState) {
            if (!stopped) {
                updateState(this, newState);
            }
        }

        private void fillExportSteps() {
            steps.add(ExportState.Processing.Step.Initializing);
            if (settings.onlySingleTopic()) {
                steps.add(ExportState.Processing.Step.Topic);
                return;
            }
            if (settings.has(ExportSettings.AnyChatsMask)) {
                steps.add(ExportState.Processing.Step.DialogsList);
            }
            if (settings.has(ExportSettings.PersonalInfo)) {
                steps.add(ExportState.Processing.Step.PersonalInfo);
            }
            if (settings.has(ExportSettings.Userpics)) {
                steps.add(ExportState.Processing.Step.Userpics);
            }
            if (settings.has(ExportSettings.Stories)) {
                steps.add(ExportState.Processing.Step.Stories);
            }
            if (settings.has(ExportSettings.ProfileMusic)) {
                steps.add(ExportState.Processing.Step.ProfileMusic);
            }
            if (settings.has(ExportSettings.Contacts)) {
                steps.add(ExportState.Processing.Step.Contacts);
            }
            if (settings.has(ExportSettings.Sessions)) {
                steps.add(ExportState.Processing.Step.Sessions);
            }
            if (settings.has(ExportSettings.OtherData)) {
                steps.add(ExportState.Processing.Step.OtherData);
            }
            if (settings.has(ExportSettings.AnyChatsMask)) {
                steps.add(ExportState.Processing.Step.Dialogs);
            }
        }

        private void fillSubstepsInSteps(ExportApi.StartInfo info) {
            int[] result = new int[ExportState.Processing.Step.values().length];
            result[ExportState.Processing.Step.Initializing.ordinal()] = 1;
            if (settings.onlySingleTopic()) {
                result[ExportState.Processing.Step.Topic.ordinal()] = 1;
            } else if (settings.has(ExportSettings.AnyChatsMask)) {
                result[ExportState.Processing.Step.DialogsList.ordinal()] = 1;
                result[ExportState.Processing.Step.Dialogs.ordinal()] = info.dialogsCount;
            }
            if (settings.has(ExportSettings.PersonalInfo)) {
                result[ExportState.Processing.Step.PersonalInfo.ordinal()] = 1;
            }
            if (settings.has(ExportSettings.Userpics)) {
                result[ExportState.Processing.Step.Userpics.ordinal()] = 1;
            }
            if (settings.has(ExportSettings.Stories)) {
                result[ExportState.Processing.Step.Stories.ordinal()] = 1;
            }
            if (settings.has(ExportSettings.ProfileMusic) && !settings.onlySinglePeer()) {
                result[ExportState.Processing.Step.ProfileMusic.ordinal()] = 1;
            }
            if (settings.has(ExportSettings.Contacts)) {
                result[ExportState.Processing.Step.Contacts.ordinal()] = 1;
            }
            if (settings.has(ExportSettings.Sessions)) {
                result[ExportState.Processing.Step.Sessions.ordinal()] = 1;
            }
            if (settings.has(ExportSettings.OtherData)) {
                result[ExportState.Processing.Step.OtherData.ordinal()] = 1;
            }
            substepsInStep = result;
            substepsTotal = 0;
            for (int count : result) {
                substepsTotal += count;
            }
        }

        private ExportState.Processing prepareState(ExportState.Processing.Step step) {
            if (step != lastProcessingStep) {
                substepsPassed += substepsInStep[lastProcessingStep.ordinal()];
                lastProcessingStep = step;
            }
            ExportState.Processing result = new ExportState.Processing();
            result.step = step;
            result.substepsPassed = substepsPassed;
            result.substepsNow = substepsInStep[step.ordinal()];
            result.substepsTotal = substepsTotal;
            for (ExportState.Processing.Step item : steps) {
                if (item == ExportState.Processing.Step.Initializing) {
                    continue;
                }
                result.stepCount++;
                if (item == step) {
                    result.stepIndex = result.stepCount;
                }
            }
            return result;
        }

        private void fillBytes(ExportState.Processing state, ExportApi.DownloadProgress progress) {
            if (!progress.path().isEmpty()) {
                state.bytesName = progress.path().substring(progress.path().lastIndexOf('/') + 1);
            }
            state.bytesLoaded = progress.ready();
            state.bytesCount = progress.total();
        }

        private void exportNext() {
            if (stopped) {
                return;
            }
            if (++stepIndex >= steps.size()) {
                if (ioCatchError(writer.finish())) {
                    return;
                }
                api.finishExport(() -> {
                    if (stopped) {
                        return;
                    }
                    int duration = (int) ((SystemClock.elapsedRealtime() - startedAt) / 1000);
                    stop(new ExportState.Finished(settings.path, stats.filesCount(), stats.bytesCount(), chatsExported, messagesExported, duration));
                });
                return;
            }
            switch (steps.get(stepIndex)) {
                case Initializing -> initialize();
                case DialogsList -> collectDialogsList();
                case PersonalInfo -> exportPersonalInfo();
                case Userpics -> exportUserpics();
                case Stories -> exportStories();
                case ProfileMusic -> exportProfileMusic();
                case Contacts -> exportContacts();
                case Sessions -> exportSessions();
                case OtherData -> exportOtherData();
                case Dialogs -> exportDialogs();
                case Topic -> exportTopic();
            }
        }

        private void initialize() {
            setState(prepareState(ExportState.Processing.Step.Initializing));
            api.startExport(settings, stats, info -> {
                if (ioCatchError(writer.start(settings, stats))) {
                    return;
                }
                fillSubstepsInSteps(info);
                exportNext();
            });
        }

        private ExportState.Processing stateDialogsList(int processed) {
            ExportState.Processing result = prepareState(ExportState.Processing.Step.DialogsList);
            result.entityIndex = processed;
            result.entityCount = Math.max(processed, substepsInStep[ExportState.Processing.Step.Dialogs.ordinal()]);
            return result;
        }

        private void collectDialogsList() {
            setState(stateDialogsList(0));
            api.requestDialogsList(count -> {
                setState(stateDialogsList(Math.max(count - 1, 0)));
                return true;
            }, result -> {
                dialogsInfo = result;
                exportNext();
            });
        }

        private void exportPersonalInfo() {
            setState(prepareState(ExportState.Processing.Step.PersonalInfo));
            api.requestPersonalInfo(result -> {
                if (ioCatchError(writer.writePersonal(result))) {
                    return;
                }
                exportNext();
            });
        }

        private ExportState.Processing stateUserpics(ExportApi.DownloadProgress progress) {
            ExportState.Processing result = prepareState(ExportState.Processing.Step.Userpics);
            result.entityIndex = userpicsWritten + progress.itemIndex();
            result.entityCount = Math.max(userpicsCount, result.entityIndex);
            fillBytes(result, progress);
            return result;
        }

        private void exportUserpics() {
            api.requestUserpics(count -> {
                if (ioCatchError(writer.writeUserpicsStart(count))) {
                    return false;
                }
                userpicsWritten = 0;
                userpicsCount = count;
                setState(stateUserpics(ExportApi.DownloadProgress.EMPTY));
                return true;
            }, progress -> {
                setState(stateUserpics(progress));
                return true;
            }, slice -> {
                if (ioCatchError(writer.writeUserpicsSlice(slice))) {
                    return false;
                }
                userpicsWritten += slice.size();
                setState(stateUserpics(ExportApi.DownloadProgress.EMPTY));
                return true;
            }, () -> {
                if (ioCatchError(writer.writeUserpicsEnd())) {
                    return;
                }
                exportNext();
            });
        }

        private ExportState.Processing stateStories(ExportApi.DownloadProgress progress) {
            ExportState.Processing result = prepareState(ExportState.Processing.Step.Stories);
            result.entityIndex = storiesWritten + progress.itemIndex();
            result.entityCount = Math.max(storiesCount, result.entityIndex);
            fillBytes(result, progress);
            return result;
        }

        private void exportStories() {
            api.requestStories(count -> {
                if (ioCatchError(writer.writeStoriesStart(count))) {
                    return false;
                }
                storiesWritten = 0;
                storiesCount = count;
                setState(stateStories(ExportApi.DownloadProgress.EMPTY));
                return true;
            }, progress -> {
                setState(stateStories(progress));
                return true;
            }, slice -> {
                if (ioCatchError(writer.writeStoriesSlice(slice))) {
                    return false;
                }
                storiesWritten += slice.list.size();
                setState(stateStories(ExportApi.DownloadProgress.EMPTY));
                return true;
            }, () -> {
                if (ioCatchError(writer.writeStoriesEnd())) {
                    return;
                }
                exportNext();
            });
        }

        private ExportState.Processing stateProfileMusic(ExportApi.DownloadProgress progress) {
            ExportState.Processing result = prepareState(ExportState.Processing.Step.ProfileMusic);
            result.entityIndex = profileMusicWritten + progress.itemIndex();
            result.entityCount = Math.max(profileMusicCount, result.entityIndex);
            fillBytes(result, progress);
            return result;
        }

        private void exportProfileMusic() {
            api.requestProfileMusic(count -> {
                if (ioCatchError(writer.writeProfileMusicStart(count))) {
                    return false;
                }
                profileMusicWritten = 0;
                profileMusicCount = count;
                setState(stateProfileMusic(ExportApi.DownloadProgress.EMPTY));
                return true;
            }, progress -> {
                setState(stateProfileMusic(progress));
                return true;
            }, slice -> {
                if (ioCatchError(writer.writeProfileMusicSlice(slice))) {
                    return false;
                }
                profileMusicWritten += slice.size();
                setState(stateProfileMusic(ExportApi.DownloadProgress.EMPTY));
                return true;
            }, () -> {
                if (ioCatchError(writer.writeProfileMusicEnd())) {
                    return;
                }
                exportNext();
            });
        }

        private void exportContacts() {
            setState(prepareState(ExportState.Processing.Step.Contacts));
            api.requestContacts(result -> {
                if (ioCatchError(writer.writeContactsList(result))) {
                    return;
                }
                exportNext();
            });
        }

        private void exportSessions() {
            setState(prepareState(ExportState.Processing.Step.Sessions));
            api.requestSessions(result -> {
                if (ioCatchError(writer.writeSessionsList(result))) {
                    return;
                }
                exportNext();
            });
        }

        private void exportOtherData() {
            setState(prepareState(ExportState.Processing.Step.OtherData));
            api.requestOtherData("lists/other_data.json", result -> {
                if (ioCatchError(writer.writeOtherData(result))) {
                    return;
                }
                exportNext();
            });
        }

        private void exportDialogs() {
            if (ioCatchError(writer.writeDialogsStart(dialogsInfo))) {
                return;
            }
            exportNextDialog();
        }

        private ExportState.Processing stateDialogs(ExportApi.DownloadProgress progress) {
            ExportState.Processing result = prepareState(ExportState.Processing.Step.Dialogs);
            ExportData.DialogInfo dialog = dialogsInfo.itemAt(dialogIndex);
            result.entityIndex = dialogIndex;
            result.entityCount = dialogsInfo.size();
            if (dialog != null) {
                result.entityName = dialog.name;
                result.entityDialogId = DialogObject.getPeerDialogId(dialog.input);
                result.entityType = switch (dialog.type) {
                    case Self -> ExportState.Processing.EntityType.SavedMessages;
                    case Replies -> ExportState.Processing.EntityType.RepliesMessages;
                    case VerifyCodes -> ExportState.Processing.EntityType.VerifyCodes;
                    default -> ExportState.Processing.EntityType.Chat;
                };
            }
            result.itemIndex = messagesWritten + progress.itemIndex();
            result.itemCount = Math.max(messagesCount, result.itemIndex);
            fillBytes(result, progress);
            return result;
        }

        private ExportState.Processing stateTopic(ExportApi.DownloadProgress progress) {
            ExportState.Processing result = prepareState(ExportState.Processing.Step.Topic);
            result.entityType = ExportState.Processing.EntityType.Topic;
            result.entityName = settings.singleTopicTitle;
            result.entityDialogId = DialogObject.getPeerDialogId(settings.singlePeer);
            result.entityIndex = 0;
            result.entityCount = 1;
            result.itemIndex = messagesWritten + progress.itemIndex();
            result.itemCount = Math.max(messagesCount, result.itemIndex);
            fillBytes(result, progress);
            return result;
        }

        private void exportTopic() {
            ExportData.DialogInfo info = new ExportData.DialogInfo();
            info.type = ExportData.DialogInfo.Type.PublicSupergroup;
            info.name = settings.singleTopicTitle;
            info.input = settings.singlePeer;
            info.peerId = -settings.singlePeer.channel_id;
            if (ioCatchError(writer.writeDialogStart(info))) {
                return;
            }
            api.requestTopicMessages(info, settings.singleTopicRootId, count -> {
                messagesWritten = 0;
                messagesCount = count;
                setState(stateTopic(ExportApi.DownloadProgress.EMPTY));
                return true;
            }, progress -> {
                setState(stateTopic(progress));
                return true;
            }, slice -> {
                if (ioCatchError(writer.writeDialogSlice(slice))) {
                    return false;
                }
                messagesWritten += slice.list.size();
                messagesExported += slice.list.size();
                setState(stateTopic(ExportApi.DownloadProgress.EMPTY));
                return true;
            }, () -> {
                if (ioCatchError(writer.writeDialogEnd())) {
                    return;
                }
                chatsExported++;
                exportNext();
            });
        }

        private void exportNextDialog() {
            if (stopped) {
                return;
            }
            ExportData.DialogInfo info = dialogsInfo.itemAt(++dialogIndex);
            if (info == null) {
                if (ioCatchError(writer.writeDialogsEnd())) {
                    return;
                }
                exportNext();
                return;
            }
            api.requestMessages(info, start -> {
                if (ioCatchError(writer.writeDialogStart(start))) {
                    return false;
                }
                messagesWritten = 0;
                messagesCount = 0;
                for (int count : start.messagesCountPerSplit) {
                    messagesCount += count;
                }
                setState(stateDialogs(ExportApi.DownloadProgress.EMPTY));
                return true;
            }, progress -> {
                setState(stateDialogs(progress));
                return true;
            }, slice -> {
                if (ioCatchError(writer.writeDialogSlice(slice))) {
                    return false;
                }
                messagesWritten += slice.list.size();
                messagesExported += slice.list.size();
                setState(stateDialogs(ExportApi.DownloadProgress.EMPTY));
                return true;
            }, () -> {
                if (ioCatchError(writer.writeDialogEnd())) {
                    return;
                }
                chatsExported++;
                exportNextDialog();
            });
        }
    }
}
