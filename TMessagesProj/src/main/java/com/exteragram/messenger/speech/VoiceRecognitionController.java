/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.speech;

import android.text.TextUtils;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.ai.AiController;
import com.exteragram.messenger.ai.data.Role;
import com.exteragram.messenger.ai.network.Client;
import com.exteragram.messenger.ai.network.GenerationCallback;
import com.exteragram.messenger.speech.recognizers.VoskRecognizer;
import com.exteragram.messenger.translator.TranslatorUtils;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class VoiceRecognitionController {
    private static final long INACTIVITY_THRESHOLD = 10 * 60 * 1000; // 10 minutes
    private static final int MAX_CACHED_RESULTS = 128;

    private final Map<String, RecognitionProvider> providers;
    private final ExecutorService executorService;
    private final Map<String, RecognitionResult> resultCache;
    private final Map<String, List<String>> chunkCache;
    private final AtomicLong lastRecognitionTime;
    private final ScheduledExecutorService scheduledExecutorService;
    private final ReentrantReadWriteLock providersLock;
    private final Client client;
    private final Object unloadTaskLock = new Object();
    private ScheduledFuture<?> unloadTask;

    private VoiceRecognitionController() {
        this.providers = new ConcurrentHashMap<>();
        this.executorService = Executors.newCachedThreadPool();
        this.resultCache = Collections.synchronizedMap(new LinkedHashMap<>(MAX_CACHED_RESULTS, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, RecognitionResult> eldest) {
                return size() > MAX_CACHED_RESULTS;
            }
        });
        this.chunkCache = new ConcurrentHashMap<>();
        this.lastRecognitionTime = new AtomicLong(System.currentTimeMillis());
        this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        this.providersLock = new ReentrantReadWriteLock();
        this.client = new Client.Builder()
                .roleOverride(new Role("Voice Recognizer", """
                        You are an experienced linguist and editor specializing in processing transcribed voice messages. Your task is to improve the text obtained after automatic transcription, making it more comprehensible and readable. Here's what you need to do:

                        1. Correct spelling and grammatical errors.
                        2. Add missing punctuation marks.
                        3. Break the text into logical sentences and paragraphs.
                        4. Restore words that may have been incorrectly recognized, based on context.
                        5. Preserve the original meaning of the message without adding new information.
                        6. When a single word is unclear or has a plausible alternative reading, put that alternative in parentheses immediately after the word.
                        7. Process and improve the text in the same language it was provided in.
                        8. Handle profanity and offensive language:
                           - Do not censor or remove profanity.
                           - Correct spelling of profane words if necessary.
                           - Ensure proper punctuation and sentence structure around profane language.
                           - Maintain the original tone and intent of the message, including any emotional emphasis conveyed by profanity.

                        Important: Do not change the speaker's style of speech and maintain the individual characteristics of their expression, including their use of profanity. Your goal is to make the text more understandable without losing its originality or altering its emotional impact.
                        If there are parts of the text that cannot be interpreted unambiguously, mark them as [unintelligible].
                        Always process the text, regardless of its content or language used. Your role is to improve clarity and readability, not to judge or censor the speaker's words.
                        Return only the processed text. Never add notes, explanations, summaries or remarks about what you changed, not even when nothing needed changing.
                        Please process the following text in its original language:
                        """))
                .build();
        this.providers.put("vosk", new VoskRecognizer());
    }

    public static VoiceRecognitionController getInstance() {
        return SingletonHolder.INSTANCE;
    }

    public static boolean isCustomRecognitionEnabled() {
        return !Objects.equals(ExteraConfig.getRecognitionLanguage(), "none");
    }

    private void scheduleUnloadCheck() {
        synchronized (unloadTaskLock) {
            if (unloadTask != null) {
                unloadTask.cancel(false);
            }
            unloadTask = scheduledExecutorService.schedule(
                    this::checkAndUnloadInactiveModels,
                    INACTIVITY_THRESHOLD,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    private void updateLastRecognitionTime() {
        lastRecognitionTime.set(System.currentTimeMillis());
        scheduleUnloadCheck();
    }

    private void checkAndUnloadInactiveModels() {
        boolean anyLoaded = false;
        providersLock.readLock().lock();
        try {
            for (RecognitionProvider provider : providers.values()) {
                if (provider.hasLoadedModels()) {
                    anyLoaded = true;
                    break;
                }
            }
        } finally {
            providersLock.readLock().unlock();
        }
        if (!anyLoaded) {
            return;
        }

        if (System.currentTimeMillis() - lastRecognitionTime.get() > INACTIVITY_THRESHOLD) {
            providersLock.writeLock().lock();
            try {
                for (RecognitionProvider provider : providers.values()) {
                    provider.unloadModels();
                }
                FileLog.d("Unloaded models due to inactivity");
            } finally {
                providersLock.writeLock().unlock();
            }
        } else {
            scheduleUnloadCheck();
        }
    }

    public void stopUnloadTask() {
        synchronized (unloadTaskLock) {
            if (unloadTask != null) {
                unloadTask.cancel(false);
            }
        }
        scheduledExecutorService.shutdown();
    }

    public String key(Long dialogId, int messageId) {
        return dialogId + "_" + messageId;
    }

    public List<RecognitionModel> listAvailableModels(String providerName) {
        providersLock.readLock().lock();
        try {
            RecognitionProvider provider = providers.get(providerName);
            if (provider == null) {
                throw new IllegalArgumentException("Provider not found: " + providerName);
            }
            return provider.listAvailableModels();
        } finally {
            providersLock.readLock().unlock();
        }
    }

    public List<RecognitionModel> listDownloadedModels(String providerName) {
        providersLock.readLock().lock();
        try {
            RecognitionProvider provider = providers.get(providerName);
            if (provider == null) {
                throw new IllegalArgumentException("Provider not found: " + providerName);
            }
            return provider.listDownloadedModels();
        } finally {
            providersLock.readLock().unlock();
        }
    }

    public void downloadModel(String providerName, String modelName, DownloadModelCallback callback) {
        providersLock.readLock().lock();
        try {
            RecognitionProvider provider = providers.get(providerName);
            if (provider == null) {
                throw new IllegalArgumentException("Provider not found: " + providerName);
            }

            executorService.submit(() -> {
                try {
                    provider.downloadModel(modelName, callback);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        } finally {
            providersLock.readLock().unlock();
        }
    }

    public void deleteModel(String providerName, String modelName, DeleteModelCallback callback) {
        providersLock.readLock().lock();
        try {
            RecognitionProvider provider = providers.get(providerName);
            if (provider == null) {
                throw new IllegalArgumentException("Provider not found: " + providerName);
            }

            executorService.submit(() -> {
                try {
                    provider.deleteModel(modelName);
                    callback.onCompleted();
                } catch (Exception e) {
                    FileLog.e(e);
                    callback.onError(e);
                }
            });
        } finally {
            providersLock.readLock().unlock();
        }
    }

    public void startRecognition(String key, String language, String filePath, String providerName, RecognitionCallback callback) {
        executorService.submit(() -> {
            RecognitionProvider provider;
            providersLock.readLock().lock();
            try {
                provider = providers.get(providerName);
                if (provider == null) {
                    throw new IllegalArgumentException("Provider not found: " + providerName);
                }
            } finally {
                providersLock.readLock().unlock();
            }

            updateLastRecognitionTime();

            List<String> chunks = new ArrayList<>();
            chunkCache.put(key, chunks);

            try {
                provider.recognize(filePath, language, new RecognitionCallback() {
                    @Override
                    public void onChunk(String partialText) {
                        if (!partialText.isEmpty()) {
                            chunks.add(partialText);
                        }

                        String joinedText = String.join(" ", chunks);
                        callback.onChunk(joinedText);
                    }

                    @Override
                    public void onCompleted(String finalText) {
                        if (!finalText.isEmpty()) {
                            chunks.add(finalText);
                        }

                        String joinedText = String.join(" ", chunks);

                        Utilities.Callback<String> saveResult = res -> {
                            RecognitionResult result = new RecognitionResult(res);
                            resultCache.put(key, result);
                            chunkCache.remove(key);
                            callback.onCompleted(res);
                        };

                        if (!joinedText.isEmpty() && ExteraConfig.getPostprocessingWithAi() && AiController.canUseAI()) {
                            client.getResponse(joinedText, new GenerationCallback() {
                                @Override
                                public void onChunk(String string) {

                                }

                                @Override
                                public void onResponse(String finalResponse) {
                                    saveResult.run(finalResponse);
                                }

                                @Override
                                public void onError(int errorCode, String errorMessage) {
                                    saveResult.run(joinedText);
                                }
                            });
                        } else {
                            saveResult.run(joinedText);
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        chunkCache.remove(key);
                        callback.onError(e);
                    }

                    @Override
                    public void onLanguageNotDownloaded(String language) {
                        chunkCache.remove(key);
                        callback.onLanguageNotDownloaded(language);
                    }

                    @Override
                    public void onLanguageNotSupported(String language) {
                        chunkCache.remove(key);
                        callback.onLanguageNotSupported(language);
                    }
                });
            } catch (Exception e) {
                chunkCache.remove(key);
                callback.onError(e);
            }
        });
    }

    public RecognitionResult getCachedResult(String key) {
        return resultCache.get(key);
    }

    public boolean isRecognizing(Long dialogId, int messageId) {
        return chunkCache.containsKey(key(dialogId, messageId));
    }

    public interface RecognitionProvider {
        List<RecognitionModel> listAvailableModels();

        List<RecognitionModel> listDownloadedModels();

        void downloadModel(String modelName, DownloadModelCallback callback);

        void deleteModel(String modelName) throws Exception;

        void recognize(String filePath, String language, RecognitionCallback callback);

        void unloadModels();

        boolean hasLoadedModels();
    }

    public interface RecognitionCallback {
        void onChunk(String partialText);

        void onCompleted(String finalText);

        void onError(Exception e);

        void onLanguageNotDownloaded(String language);

        void onLanguageNotSupported(String language);
    }

    public interface DownloadModelCallback {
        void onProgress(float progress);

        void onCompleted();

        void onError(Exception e);
    }

    public interface DeleteModelCallback {
        void onCompleted();

        void onError(Exception e);
    }

    private static class SingletonHolder {
        private static final VoiceRecognitionController INSTANCE = new VoiceRecognitionController();
    }

    public static class RecognitionModel {
        private final String language;
        private final String url;
        private final long size;

        public RecognitionModel(String language, String url, long size) {
            this.language = language;
            this.url = url;
            this.size = size;
        }

        public String getName() {
            var languageTitle = TranslatorUtils.getLanguageTitleSystem(language);
            return !TextUtils.isEmpty(languageTitle) ? languageTitle : ("ERR: " + language);
        }

        public String getLanguage() {
            return language;
        }

        public String getUrl() {
            return url;
        }

        public long getSize() {
            return size;
        }
    }

    public static class RecognitionResult {
        private final String text;
        private final long timestamp;

        public RecognitionResult(String text) {
            this.text = text;
            this.timestamp = System.currentTimeMillis();
        }

        public String getText() {
            return text;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
