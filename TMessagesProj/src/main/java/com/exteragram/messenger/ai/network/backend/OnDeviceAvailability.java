/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.ai.network.backend;

import android.os.Build;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.exteragram.messenger.ai.AiConfig;
import com.google.mlkit.genai.common.DownloadCallback;
import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.common.GenAiException;
import com.google.mlkit.genai.prompt.Generation;
import com.google.mlkit.genai.prompt.GenerationConfig;
import com.google.mlkit.genai.prompt.GenerativeModel;
import com.google.mlkit.genai.prompt.ModelConfig;
import com.google.mlkit.genai.prompt.ModelPreference;
import com.google.mlkit.genai.prompt.ModelReleaseStage;
import com.google.mlkit.genai.prompt.java.GenerativeModelFutures;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class OnDeviceAvailability {
    public static final int STATUS_UNKNOWN = -1;
    public static final int MODEL_FAST = 1;
    public static final int MODEL_PREVIEW = 2;

    private static final String STATUS_KEY = "onDeviceFeatureStatus";
    private static final String MODEL_NAME_KEY = "onDeviceModelName";
    private static final String THINKING_KEY = "onDeviceThinkingSupported";
    private static final String SYSTEM_PROMPT_KEY = "onDeviceSystemPromptSupported";
    private static final String TOKEN_LIMIT_KEY = "onDeviceTokenLimit";
    private static final String OTHER_MODEL_SUPPORTED_KEY = "onDeviceOtherModelSupported";
    private static final int MIN_SUPPORTED_SDK = Build.VERSION_CODES.O;
    private static final long STATUS_TIMEOUT_SECONDS = 10;
    private static final long WARMUP_TIMEOUT_SECONDS = 30;

    private static final AtomicBoolean refreshing = new AtomicBoolean();
    private static final Set<Integer> downloadingModels = ConcurrentHashMap.newKeySet();
    private static final Map<Integer, Integer> downloadPercents = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> sessionStatuses = new ConcurrentHashMap<>();
    private static final AtomicBoolean restored = new AtomicBoolean();
    private static final AtomicBoolean refreshedOnce = new AtomicBoolean();
    private static final AtomicBoolean warmedUp = new AtomicBoolean();
    private static final AtomicInteger configGeneration = new AtomicInteger();
    private static final List<Runnable> pendingCallbacks = new ArrayList<>();
    private static boolean recheckRequested;

    private static volatile int status = STATUS_UNKNOWN;
    private static volatile boolean supportedThisSession;
    private static volatile boolean otherModelSupported;
    private static volatile String modelName;
    private static volatile int modelNameGeneration;
    private static volatile boolean thinkingSupported;
    private static volatile boolean systemPromptSupported;
    private static volatile int tokenLimit;

    private OnDeviceAvailability() {
    }

    public interface DownloadListener {
        void onStarted(long totalBytes);

        void onProgress(float progress, long downloadedBytes);

        void onCompleted();

        void onFailed(Exception e);
    }

    public static int getModelKey() {
        return (AiConfig.getOnDevicePreviewModel() ? MODEL_PREVIEW : 0) | (AiConfig.getOnDeviceFastModel() ? MODEL_FAST : 0);
    }

    static GenerativeModel createModel() {
        return createModel(getModelKey());
    }

    private static GenerativeModel createModel(int modelKey) {
        ModelConfig.Builder modelConfig = ModelConfig.builder();
        modelConfig.setReleaseStage((modelKey & MODEL_PREVIEW) != 0 ? ModelReleaseStage.PREVIEW : ModelReleaseStage.STABLE);
        modelConfig.setPreference((modelKey & MODEL_FAST) != 0 ? ModelPreference.FAST : ModelPreference.FULL);
        GenerationConfig.Builder config = new GenerationConfig.Builder();
        config.setModelConfig(modelConfig.build());
        return Generation.INSTANCE.getClient(config.build());
    }

    static int getConfigGeneration() {
        return configGeneration.get();
    }

    public static void onModelConfigChanged(@Nullable Runnable onDone) {
        ensureRestored();
        configGeneration.incrementAndGet();
        warmedUp.set(false);
        thinkingSupported = false;
        systemPromptSupported = false;
        tokenLimit = 0;
        AiConfig.getEditor()
                .remove(THINKING_KEY)
                .remove(SYSTEM_PROMPT_KEY)
                .remove(TOKEN_LIMIT_KEY)
                .apply();
        refresh(onDone);
    }

    public static int getStatus() {
        if (Build.VERSION.SDK_INT < MIN_SUPPORTED_SDK) {
            return FeatureStatus.UNAVAILABLE;
        }
        ensureRestored();
        if (refreshedOnce.compareAndSet(false, true)) {
            refresh(null);
        }
        return status;
    }

    public static boolean isReady() {
        return getStatus() == FeatureStatus.AVAILABLE;
    }

    @Nullable
    public static String getModelName() {
        ensureRestored();
        return modelName;
    }

    public static boolean isThinkingSupported() {
        ensureRestored();
        return thinkingSupported;
    }

    public static boolean isSystemPromptSupported() {
        ensureRestored();
        return systemPromptSupported;
    }

    public static int getTokenLimit() {
        ensureRestored();
        return tokenLimit;
    }

    static void ensureMetadata(GenerativeModelFutures futures, int generation) {
        ensureRestored();
        if (tokenLimit > 0) {
            return;
        }
        readMetadata(futures, generation);
    }

    public static boolean isSupported() {
        return isStatusSupported(getStatus()) || supportedThisSession || otherModelSupported;
    }

    private static boolean isStatusSupported(int value) {
        return value == FeatureStatus.AVAILABLE
                || value == FeatureStatus.DOWNLOADABLE
                || value == FeatureStatus.DOWNLOADING;
    }

    public static boolean needsDownload() {
        return getStatus() == FeatureStatus.DOWNLOADABLE;
    }

    public static boolean isDownloading() {
        return downloadingModels.contains(getModelKey());
    }

    public static boolean isDownloadInProgress() {
        return isDownloading() || getStatus() == FeatureStatus.DOWNLOADING;
    }

    public static int getDownloadPercent() {
        Integer percent = downloadPercents.get(getModelKey());
        return percent != null ? percent : -1;
    }

    static void report(int result, int generation) {
        ensureRestored();
        refreshedOnce.set(true);
        if (generation == configGeneration.get()) {
            apply(result);
        }
    }

    public static void refresh(@Nullable Runnable onDone) {
        if (Build.VERSION.SDK_INT < MIN_SUPPORTED_SDK) {
            apply(FeatureStatus.UNAVAILABLE);
            runOnDone(onDone);
            return;
        }

        synchronized (pendingCallbacks) {
            if (onDone != null) {
                pendingCallbacks.add(onDone);
            }
            if (!refreshing.compareAndSet(false, true)) {
                recheckRequested = true;
                return;
            }
        }

        ensureRestored();
        refreshedOnce.set(true);
        runOnWorker(OnDeviceAvailability::check);
    }

    private static void check() {
        while (true) {
            int generation = configGeneration.get();
            int modelKey = getModelKey();
            int result = checkStatus(generation);
            if (generation != configGeneration.get()) {
                continue;
            }
            sessionStatuses.put(modelKey, result);
            if (result == FeatureStatus.UNAVAILABLE) {
                updateOtherModelSupported(modelKey);
            }

            if (modelNameGeneration != generation && modelName != null) {
                modelName = null;
                AiConfig.getEditor().remove(MODEL_NAME_KEY).apply();
                notifyServicesUpdated();
            }
            apply(result);

            List<Runnable> callbacks;
            synchronized (pendingCallbacks) {
                if (recheckRequested || generation != configGeneration.get()) {
                    recheckRequested = false;
                    continue;
                }
                refreshing.set(false);
                callbacks = new ArrayList<>(pendingCallbacks);
                pendingCallbacks.clear();
            }
            for (Runnable callback : callbacks) {
                runOnDone(callback);
            }
            return;
        }
    }

    private static void updateOtherModelSupported(int currentModelKey) {
        boolean supported = false;
        for (int modelKey = 0; modelKey <= (MODEL_PREVIEW | MODEL_FAST) && !supported; modelKey++) {
            if (modelKey == currentModelKey) {
                continue;
            }
            Integer status = sessionStatuses.get(modelKey);
            if (status == null) {
                status = probeStatus(modelKey);
                sessionStatuses.put(modelKey, status);
            }
            supported = isStatusSupported(status);
        }
        if (supported != otherModelSupported) {
            otherModelSupported = supported;
            AiConfig.getEditor().putBoolean(OTHER_MODEL_SUPPORTED_KEY, supported).apply();
            notifyServicesUpdated();
        }
    }

    private static int probeStatus(int modelKey) {
        GenerativeModel model = null;
        try {
            model = createModel(modelKey);
            Integer value = GenerativeModelFutures.from(model).checkStatus().get(STATUS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return value == null ? STATUS_UNKNOWN : value;
        } catch (Throwable throwable) {
            FileLog.e("AI_ON_DEVICE_PROBE_FAILED", throwable);
            return FeatureStatus.UNAVAILABLE;
        } finally {
            closeQuietly(model);
        }
    }

    private static int checkStatus(int generation) {
        GenerativeModel model = null;
        int result = FeatureStatus.UNAVAILABLE;
        try {
            model = createModel();
            GenerativeModelFutures futures = GenerativeModelFutures.from(model);
            Integer value = futures.checkStatus().get(STATUS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            result = value == null ? STATUS_UNKNOWN : value;
            if (result == FeatureStatus.AVAILABLE) {
                readMetadata(futures, generation);
            } else if (isStatusSupported(result) && readModelName(futures, generation)) {
                notifyServicesUpdated();
            }
        } catch (Throwable throwable) {
            FileLog.e("AI_ON_DEVICE_STATUS_FAILED", throwable);
        } finally {
            closeQuietly(model);
        }
        return result;
    }

    private static boolean readModelName(GenerativeModelFutures futures, int generation) {
        try {
            String name = futures.getBaseModelName().get(STATUS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (generation != configGeneration.get() || TextUtils.isEmpty(name)) {
                return false;
            }
            modelNameGeneration = generation;
            if (name.equals(modelName)) {
                return false;
            }
            modelName = name;
            AiConfig.getEditor().putString(MODEL_NAME_KEY, name).apply();
            return true;
        } catch (Exception e) {
            FileLog.e("AI_ON_DEVICE_MODEL_NAME", e);
            return false;
        }
    }

    private static void readMetadata(GenerativeModelFutures futures, int generation) {
        boolean changed = readModelName(futures, generation);
        try {
            boolean supported = Boolean.TRUE.equals(futures.isThinkingModeAvailable().get(STATUS_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            if (generation != configGeneration.get()) {
                return;
            }
            if (supported != thinkingSupported) {
                thinkingSupported = supported;
                AiConfig.getEditor().putBoolean(THINKING_KEY, supported).apply();
                changed = true;
            }
        } catch (Exception e) {
            FileLog.e("AI_ON_DEVICE_THINKING", e);
        }
        try {
            boolean supported = Boolean.TRUE.equals(futures.isSystemPromptAvailable().get(STATUS_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            if (generation != configGeneration.get()) {
                return;
            }
            if (supported != systemPromptSupported) {
                systemPromptSupported = supported;
                AiConfig.getEditor().putBoolean(SYSTEM_PROMPT_KEY, supported).apply();
            }
        } catch (Exception e) {
            FileLog.e("AI_ON_DEVICE_SYSTEM_PROMPT", e);
        }
        try {
            Integer limit = futures.getTokenLimit().get(STATUS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (generation != configGeneration.get()) {
                return;
            }
            if (limit != null && limit > 0 && limit != tokenLimit) {
                tokenLimit = limit;
                AiConfig.getEditor().putInt(TOKEN_LIMIT_KEY, limit).apply();
            }
        } catch (Exception e) {
            FileLog.e("AI_ON_DEVICE_TOKEN_LIMIT", e);
        }
        if (changed) {
            notifyServicesUpdated();
        }
    }

    public static void warmup() {
        if (!AiConfig.ON_DEVICE_SERVICE.getId().equals(AiConfig.getSelectedServiceId()) || !isReady()) {
            return;
        }
        if (!warmedUp.compareAndSet(false, true)) {
            return;
        }
        runOnWorker(() -> {
            GenerativeModel model = null;
            try {
                model = createModel();
                GenerativeModelFutures.from(model).warmup().get(WARMUP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                warmedUp.set(false);
            } catch (Throwable throwable) {
                warmedUp.set(false);
                FileLog.e("AI_ON_DEVICE_WARMUP", throwable);
            } finally {
                closeQuietly(model);
            }
        });
    }

    public static void download(int modelKey, DownloadListener listener) {
        if (Build.VERSION.SDK_INT < MIN_SUPPORTED_SDK) {
            listener.onFailed(new IllegalStateException("unsupported android version"));
            return;
        }
        if (!downloadingModels.add(modelKey)) {
            return;
        }
        downloadPercents.remove(modelKey);
        notifyServicesUpdated();
        runOnWorker(() -> runDownload(modelKey, listener));
    }

    private static void runDownload(int modelKey, DownloadListener listener) {
        GenerativeModel model = null;
        try {
            model = createModel(modelKey);
            GenerativeModelFutures futures = GenerativeModelFutures.from(model);
            long[] total = new long[]{0};

            futures.download(new DownloadCallback() {
                @Override
                public void onDownloadStarted(long bytesToDownload) {
                    total[0] = bytesToDownload;
                    listener.onStarted(bytesToDownload);
                }

                @Override
                public void onDownloadProgress(long totalBytesDownloaded) {
                    float progress = total[0] > 0 ? Math.min(1f, (float) totalBytesDownloaded / total[0]) : -1f;
                    int percent = progress < 0 ? -1 : (int) (progress * 100);
                    Integer previous = downloadPercents.put(modelKey, percent);
                    if (previous != null && previous == percent) {
                        return;
                    }
                    notifyServicesUpdated();
                    listener.onProgress(progress, totalBytesDownloaded);
                }

                @Override
                public void onDownloadFailed(GenAiException exception) {
                    FileLog.e("AI_ON_DEVICE_DOWNLOAD_FAILED", exception);
                }
            }).get();

            int result = futures.checkStatus().get(STATUS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (modelKey == getModelKey()) {
                refresh(null);
            }

            if (result == FeatureStatus.AVAILABLE) {
                listener.onProgress(1f, total[0]);
                listener.onCompleted();
            } else if (result != FeatureStatus.DOWNLOADING) {
                listener.onFailed(new IllegalStateException("model is not available after download, status=" + result));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            FileLog.e("AI_ON_DEVICE_DOWNLOAD", e);
            listener.onFailed(e);
        } catch (Exception e) {
            FileLog.e("AI_ON_DEVICE_DOWNLOAD", e);
            listener.onFailed(e);
        } finally {
            closeQuietly(model);
            downloadPercents.remove(modelKey);
            downloadingModels.remove(modelKey);
            notifyServicesUpdated();
        }
    }

    private static void ensureRestored() {
        if (restored.compareAndSet(false, true)) {
            status = AiConfig.getPreferences().getInt(STATUS_KEY, STATUS_UNKNOWN);
            modelName = AiConfig.getPreferences().getString(MODEL_NAME_KEY, null);
            thinkingSupported = AiConfig.getPreferences().getBoolean(THINKING_KEY, false);
            systemPromptSupported = AiConfig.getPreferences().getBoolean(SYSTEM_PROMPT_KEY, false);
            tokenLimit = AiConfig.getPreferences().getInt(TOKEN_LIMIT_KEY, 0);
            otherModelSupported = AiConfig.getPreferences().getBoolean(OTHER_MODEL_SUPPORTED_KEY, false);
        }
    }

    private static void apply(int result) {
        if (isStatusSupported(result)) {
            supportedThisSession = true;
        }
        if (status == result) {
            return;
        }
        status = result;
        AiConfig.getEditor().putInt(STATUS_KEY, result).apply();
        notifyServicesUpdated();
    }

    private static void notifyServicesUpdated() {
        AndroidUtilities.runOnUIThread(() ->
                NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.servicesUpdated));
    }

    private static void runOnWorker(Runnable action) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                action.run();
            } finally {
                executor.shutdown();
            }
        });
    }

    private static void closeQuietly(@Nullable GenerativeModel model) {
        if (model == null) {
            return;
        }
        try {
            model.close();
        } catch (Throwable ignored) {
        }
    }

    private static void runOnDone(@Nullable Runnable onDone) {
        if (onDone != null) {
            AndroidUtilities.runOnUIThread(onDone);
        }
    }
}
