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

import com.exteragram.messenger.ai.data.Message;
import com.exteragram.messenger.ai.data.Role;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.common.GenAiException;
import com.google.mlkit.genai.common.StreamingCallback;
import com.google.mlkit.genai.prompt.CachedContext;
import com.google.mlkit.genai.prompt.Candidate;
import com.google.mlkit.genai.prompt.CreateCachedContextRequest;
import com.google.mlkit.genai.prompt.GenerateContentRequest;
import com.google.mlkit.genai.prompt.GenerateContentResponse;
import com.google.mlkit.genai.prompt.GenerativeModel;
import com.google.mlkit.genai.prompt.ImagePart;
import com.google.mlkit.genai.prompt.PromptPrefix;
import com.google.mlkit.genai.prompt.SystemInstruction;
import com.google.mlkit.genai.prompt.TextPart;
import com.google.mlkit.genai.prompt.java.CachesFutures;
import com.google.mlkit.genai.prompt.java.GenerativeModelFutures;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.Utilities;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class OnDeviceBackend implements Backend {
    public static final int ERROR_UNAVAILABLE = -1;
    public static final int ERROR_DOWNLOAD_REQUIRED = -2;
    public static final int ERROR_DOWNLOADING = -3;
    public static final int ERROR_INPUT_TOO_LONG = -4;
    public static final int ERROR_INFERENCE_FAILED = -5;

    private static final int HISTORY_MESSAGES_LIMIT = 24;
    private static final int HISTORY_CHARS_LIMIT = 12000;
    private static final int TOKEN_LIMIT_FALLBACK = 4000;
    private static final int MAX_OUTPUT_TOKENS = 1024;
    private static final float MAX_TEMPERATURE = 1f;
    private static final int TOKEN_HEADROOM = 256;
    private static final long METADATA_TIMEOUT_SECONDS = 10;
    private static final long CACHE_TIMEOUT_SECONDS = 30;
    private static final int MAX_CACHED_CONTEXTS = 4;
    private static final String CACHE_NAME_PREFIX = "extera_role_";
    private static final String USER_TAG = "User: ";
    private static final String ASSISTANT_TAG = "Assistant: ";

    private static volatile boolean cachingUnsupported;

    @Override
    public int getHistoryCharsLimit() {
        return HISTORY_CHARS_LIMIT;
    }

    @Override
    public int getHistoryMessagesLimit() {
        return HISTORY_MESSAGES_LIMIT;
    }

    @Override
    public int getMaxOutputTokens() {
        return MAX_OUTPUT_TOKENS;
    }

    @Override
    public float getMaxTemperature() {
        return MAX_TEMPERATURE;
    }

    @Override
    public boolean supportsImages() {
        return true;
    }

    @Override
    public void execute(BackendRequest request, BackendSink sink) throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            sink.onError(ERROR_UNAVAILABLE, "on-device model requires android 8.0");
            return;
        }

        GenerativeModel model = null;
        int generation = OnDeviceAvailability.getConfigGeneration();
        try {
            model = OnDeviceAvailability.createModel();
            GenerativeModelFutures futures = GenerativeModelFutures.from(model);

            int status = futures.checkStatus().get(METADATA_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            OnDeviceAvailability.report(status, generation);

            if (!sink.isActive()) {
                return;
            }

            if (status == FeatureStatus.UNAVAILABLE) {
                sink.onError(ERROR_UNAVAILABLE, "on-device model is not available");
                return;
            }
            if (status == FeatureStatus.DOWNLOADABLE) {
                sink.onError(ERROR_DOWNLOAD_REQUIRED, "on-device model is not downloaded");
                return;
            }
            if (status == FeatureStatus.DOWNLOADING) {
                sink.onError(ERROR_DOWNLOADING, "on-device model is downloading");
                return;
            }

            OnDeviceAvailability.ensureMetadata(futures, generation);

            generate(request, sink, futures, model);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            if (sink.isActive()) {
                FileLog.e("AI_ON_DEVICE_FAILED", e);
                GenAiException genAiException = findGenAiException(e);
                String message = e.getMessage() != null ? e.getMessage() : "Unknown error";
                if (genAiException != null && genAiException.getErrorCode() == GenAiException.ErrorCode.REQUEST_TOO_LARGE) {
                    sink.onError(ERROR_INPUT_TOO_LONG, message);
                } else {
                    sink.onError(HttpURLConnection.HTTP_INTERNAL_ERROR, message);
                }
            }
        } finally {
            if (model != null) {
                try {
                    model.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    @Nullable
    private static GenAiException findGenAiException(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof GenAiException genAiException) {
                return genAiException;
            }
        }
        return null;
    }

    private static boolean isInferenceFailure(int errorCode) {
        return switch (errorCode) {
            case GenAiException.ErrorCode.BUSY,
                 GenAiException.ErrorCode.CANCELLED,
                 GenAiException.ErrorCode.NOT_AVAILABLE,
                 GenAiException.ErrorCode.NOT_SUPPORTED,
                 GenAiException.ErrorCode.REQUEST_TOO_LARGE,
                 GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED,
                 GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED,
                 GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE,
                 GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE,
                 GenAiException.ErrorCode.AICORE_INCOMPATIBLE -> false;
            default -> true;
        };
    }

    private void generate(BackendRequest request, BackendSink sink, GenerativeModelFutures futures, GenerativeModel model) throws Exception {
        Role role = request.role();
        String instruction = buildInstruction(role != null ? role.getPrompt() : null);
        boolean thinking = request.service().isReasoningEnabled() && OnDeviceAvailability.isThinkingSupported();

        SystemInstruction systemInstruction = null;
        String prefix = "";
        if (OnDeviceAvailability.isSystemPromptSupported()) {
            systemInstruction = new SystemInstruction(instruction);
        } else {
            prefix = instruction + "\n\n";
        }

        ImagePart imagePart = createImagePart(request.currentMessage());
        List<Message> history = request.history();
        int tokenLimit = resolveTokenLimit() - request.maxOutputTokens() - TOKEN_HEADROOM;

        String body;
        int fromIndex = 0;
        int tokens;
        while (true) {
            if (!sink.isActive()) {
                return;
            }
            body = buildPrompt(history, request.currentMessage(), fromIndex);
            tokens = countTokens(futures, createRequest(request, systemInstruction, null, imagePart, prefix + body, thinking));
            if (tokens <= tokenLimit || fromIndex >= history.size()) {
                break;
            }
            fromIndex += 2;
        }

        if (tokens > tokenLimit) {
            sink.onError(ERROR_INPUT_TOO_LONG, "prompt exceeds the on-device token limit");
            return;
        }

        if (!sink.isActive()) {
            return;
        }

        String cachedContextName = systemInstruction == null && imagePart == null ? ensureCachedContext(model, instruction) : null;
        GenerateContentRequest generateRequest = cachedContextName != null
                ? createRequest(request, null, cachedContextName, imagePart, body, thinking)
                : createRequest(request, systemInstruction, null, imagePart, prefix + body, thinking);

        StringBuffer accumulated = new StringBuffer();
        ListenableFuture<GenerateContentResponse> future;

        if (request.stream()) {
            future = futures.generateContent(generateRequest, new StreamingCallback() {
                @Override
                public void onNewText(String text) {
                    if (!sink.isActive() || TextUtils.isEmpty(text)) {
                        return;
                    }
                    accumulated.append(text);
                    sink.onChunk(accumulated.toString());
                }

                @Override
                public void onNewThought(String thought) {
                    if (sink.isActive()) {
                        sink.onThinking();
                    }
                }
            });
        } else {
            future = futures.generateContent(generateRequest);
        }

        sink.onCancellable(() -> future.cancel(true));

        GenerateContentResponse response;
        try {
            response = future.get();
        } catch (ExecutionException e) {
            GenAiException genAiException = findGenAiException(e);
            if (genAiException == null || !isInferenceFailure(genAiException.getErrorCode())) {
                throw e;
            }
            if (sink.isActive()) {
                FileLog.e("AI_ON_DEVICE_INFERENCE_FAILED", e);
                sink.onError(ERROR_INFERENCE_FAILED, genAiException.getMessage() != null ? genAiException.getMessage() : "Inference failed");
            }
            return;
        }
        if (!sink.isActive()) {
            return;
        }

        String text = extractText(response);
        if (TextUtils.isEmpty(text)) {
            text = accumulated.toString();
        }
        text = trimTrailing(text);
        if (text != null && text.startsWith(ASSISTANT_TAG.trim())) {
            text = text.substring(ASSISTANT_TAG.trim().length()).trim();
        }

        if (TextUtils.isEmpty(text)) {
            sink.onError(HttpURLConnection.HTTP_NO_CONTENT, "Response body is empty");
        } else {
            sink.onComplete(text);
        }
    }

    private GenerateContentRequest createRequest(BackendRequest request, @Nullable SystemInstruction systemInstruction, @Nullable String cachedContextName, @Nullable ImagePart imagePart, String prompt, boolean thinking) {
        TextPart textPart = new TextPart(prompt);
        GenerateContentRequest.Builder builder;
        if (systemInstruction != null && imagePart != null) {
            builder = new GenerateContentRequest.Builder(systemInstruction, imagePart, textPart);
        } else if (systemInstruction != null) {
            builder = new GenerateContentRequest.Builder(systemInstruction, textPart);
        } else if (imagePart != null) {
            builder = new GenerateContentRequest.Builder(imagePart, textPart);
        } else {
            builder = new GenerateContentRequest.Builder(textPart);
        }
        builder.setTemperature(request.temperature());
        builder.setMaxOutputTokens(request.maxOutputTokens());
        builder.setEnableThinking(thinking);
        if (cachedContextName != null) {
            builder.setCachedContextName(cachedContextName);
        }
        return builder.build();
    }

    @Nullable
    private String ensureCachedContext(GenerativeModel model, String instruction) {
        if (cachingUnsupported || TextUtils.isEmpty(instruction)) {
            return null;
        }
        String digest = Utilities.MD5(instruction);
        if (TextUtils.isEmpty(digest)) {
            return null;
        }

        String name = CACHE_NAME_PREFIX + digest;
        try {
            CachesFutures caches = CachesFutures.from(model);
            List<String> ours = new ArrayList<>();
            List<CachedContext> existing = caches.list().get(METADATA_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (existing != null) {
                for (CachedContext context : existing) {
                    String contextName = context == null ? null : context.getName();
                    if (contextName == null || !contextName.startsWith(CACHE_NAME_PREFIX)) {
                        continue;
                    }
                    if (name.equals(contextName)) {
                        return name;
                    }
                    ours.add(contextName);
                }
            }
            if (ours.size() >= MAX_CACHED_CONTEXTS) {
                for (String stale : ours) {
                    caches.delete(stale).get(METADATA_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }
            }

            CachedContext created = caches
                    .create(new CreateCachedContextRequest.Builder(name, new PromptPrefix(instruction)).build())
                    .get(CACHE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return created == null ? null : created.getName();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            cachingUnsupported = true;
            FileLog.e("AI_ON_DEVICE_CACHE", e);
            return null;
        }
    }

    private String buildInstruction(@Nullable String rolePrompt) {
        StringBuilder builder = new StringBuilder();
        if (!TextUtils.isEmpty(rolePrompt)) {
            builder.append(rolePrompt).append("\n\n");
        }
        builder.append("Unless told otherwise, always reply in the same language the user writes in.");
        Locale locale = LocaleController.getInstance().getCurrentLocale();
        String language = locale != null ? locale.getDisplayLanguage(Locale.ENGLISH) : null;
        if (!TextUtils.isEmpty(language)) {
            builder.append(" If the language is unclear, reply in ").append(language).append('.');
        }
        return builder.toString();
    }

    private String buildPrompt(List<Message> history, Message currentMessage, int fromIndex) {
        if (fromIndex >= history.size()) {
            return currentMessage.content();
        }
        StringBuilder builder = new StringBuilder();
        for (int i = fromIndex; i < history.size(); i++) {
            Message message = history.get(i);
            if (message == null || TextUtils.isEmpty(message.content())) {
                continue;
            }
            builder.append(Message.ASSISTANT_ROLE.equals(message.role()) ? ASSISTANT_TAG : USER_TAG)
                    .append(message.content())
                    .append("\n\n");
        }
        builder.append(USER_TAG).append(currentMessage.content())
                .append("\n\n").append(ASSISTANT_TAG.trim());
        return builder.toString();
    }

    @Nullable
    private ImagePart createImagePart(Message message) {
        byte[] data = message.getImageData();
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            return new ImagePart(data);
        } catch (Exception e) {
            FileLog.e("AI_ON_DEVICE_IMAGE_REJECTED", e);
            return null;
        }
    }

    private int resolveTokenLimit() {
        int limit = OnDeviceAvailability.getTokenLimit();
        return limit > 0 ? limit : TOKEN_LIMIT_FALLBACK;
    }

    private int countTokens(GenerativeModelFutures futures, GenerateContentRequest request) {
        try {
            return futures.countTokens(request).get(METADATA_TIMEOUT_SECONDS, TimeUnit.SECONDS).getTotalTokens();
        } catch (Exception e) {
            FileLog.e("AI_ON_DEVICE_COUNT_TOKENS", e);
            return 0;
        }
    }

    private String extractText(GenerateContentResponse response) {
        if (response == null) {
            return null;
        }
        List<Candidate> candidates = response.getCandidates();
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.get(0).getText();
    }

    private String trimTrailing(String text) {
        if (text == null) {
            return null;
        }
        int index = text.length();
        while (index > 0 && Character.isWhitespace(text.charAt(index - 1))) {
            index--;
        }
        return text.substring(0, index);
    }
}
