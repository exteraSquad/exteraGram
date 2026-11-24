/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.ai.network;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;

import com.exteragram.messenger.ai.AiConfig;
import com.exteragram.messenger.ai.AiController;
import com.exteragram.messenger.ai.data.Message;
import com.exteragram.messenger.ai.data.Role;
import com.exteragram.messenger.ai.data.Service;
import com.exteragram.messenger.ai.network.backend.Backend;
import com.exteragram.messenger.ai.network.backend.BackendRequest;
import com.exteragram.messenger.ai.network.backend.BackendSink;
import com.exteragram.messenger.ai.network.backend.OnDeviceBackend;
import com.exteragram.messenger.ai.network.backend.RestBackend;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

public class Client {
    private static final int MAX_IMAGE_SIDE = 2048;
    private static final int MAX_INLINE_IMAGE_BYTES = 4 * 1024 * 1024;
    private static final int TEMPERATURE_STEPS = 20;
    private static final int STORAGE_MESSAGES_LIMIT = 64;
    private static final int STORAGE_CHARS_LIMIT = 64000;

    private static final ExecutorService REQUEST_EXECUTOR = Executors.newCachedThreadPool();

    private final AtomicBoolean isGenerating = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, Future<?>> activeRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Runnable> activeCancellables = new ConcurrentHashMap<>();

    private final Service serviceOverride;
    private final Role roleOverride;
    private final RestBackend restBackend = new RestBackend();
    private final OnDeviceBackend onDeviceBackend = new OnDeviceBackend();

    private Client(Builder builder) {
        this.serviceOverride = builder.serviceOverride;
        this.roleOverride = builder.roleOverride;
    }

    public static String getMimeType(String imagePath) {
        if (TextUtils.isEmpty(imagePath)) return null;
        String mimeType = "image/jpeg";
        String lowerImagePath = imagePath.toLowerCase(Locale.ROOT);
        if (lowerImagePath.endsWith(".png")) {
            mimeType = "image/png";
        } else if (lowerImagePath.endsWith(".webp")) {
            mimeType = "image/webp";
        } else if (lowerImagePath.endsWith(".heic") || lowerImagePath.endsWith(".heif")) {
            mimeType = "image/heic";
        }
        return mimeType;
    }

    private static ImagePayload loadImagePayload(String imagePath) {
        if (TextUtils.isEmpty(imagePath)) return null;
        File imageFile = new File(imagePath);
        if (!imageFile.exists() || !imageFile.isFile()) {
            return null;
        }
        if (imageFile.length() == 0) {
            return null;
        }

        if (imageFile.length() > MAX_INLINE_IMAGE_BYTES) {
            return compressImage(imagePath);
        }

        try (FileInputStream fis = new FileInputStream(imageFile);
             ByteArrayOutputStream bos = new ByteArrayOutputStream((int) Math.min(imageFile.length(), MAX_INLINE_IMAGE_BYTES))) {

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }
            return new ImagePayload(bos.toByteArray(), getMimeType(imagePath));

        } catch (IOException e) {
            FileLog.e("Error loading image: " + imagePath, e);
            return null;
        }
    }

    private static ImagePayload compressImage(String imagePath) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imagePath, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            while (bounds.outWidth / options.inSampleSize > MAX_IMAGE_SIDE || bounds.outHeight / options.inSampleSize > MAX_IMAGE_SIDE) {
                options.inSampleSize *= 2;
            }

            Bitmap bitmap = BitmapFactory.decodeFile(imagePath, options);
            if (bitmap == null) {
                return null;
            }

            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                int quality = 85;
                do {
                    output.reset();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output);
                    quality -= 10;
                } while (output.size() > MAX_INLINE_IMAGE_BYTES && quality >= 55);
                if (output.size() > MAX_INLINE_IMAGE_BYTES) {
                    return null;
                }
                return new ImagePayload(output.toByteArray(), "image/jpeg");
            } finally {
                bitmap.recycle();
            }
        } catch (Exception e) {
            FileLog.e("Error compressing image: " + imagePath, e);
            return null;
        }
    }

    private Service getSelectedService() {
        return serviceOverride != null ? serviceOverride : AiController.getInstance().getSelected();
    }

    private Backend resolveBackend(Service service) {
        return service != null && service.isOnDevice() ? onDeviceBackend : restBackend;
    }

    private Role getActiveRole() {
        return roleOverride != null ? roleOverride : (serviceOverride == null ? AiController.getInstance().getSelectedRole() : null);
    }

    public String getResponse(String prompt, GenerationCallback callback) {
        return getResponse(prompt, false, false, null, callback);
    }

    public String getResponse(String prompt, boolean saveHistory, boolean stream, String imagePath, GenerationCallback callback) {
        String requestId = UUID.randomUUID().toString();
        FutureTask<Void> task = new FutureTask<>(() -> {
            isGenerating.set(true);

            try {
                Service selectedService = getSelectedService();
                Backend backend = resolveBackend(selectedService);

                ImagePayload imagePayload = null;
                if (backend.supportsImages() && AiController.canSendImage(imagePath)) {
                    imagePayload = loadImagePayload(imagePath);
                }

                if (!activeRequests.containsKey(requestId)) {
                    return;
                }

                Message currentMessage = new Message(Message.USER_ROLE, prompt,
                        imagePayload != null ? imagePayload.data() : null,
                        imagePayload != null ? imagePayload.mimeType() : null);

                List<Message> history = saveHistory
                        ? historyForRequest(AiConfig.getConversationHistory(), backend)
                        : new ArrayList<>();

                if (!activeRequests.containsKey(requestId)) {
                    return;
                }

                BackendRequest request = new BackendRequest(
                        selectedService,
                        getActiveRole(),
                        history,
                        currentMessage,
                        stream,
                        AiConfig.getTemperature() / (float) TEMPERATURE_STEPS * backend.getMaxTemperature(),
                        backend.getMaxOutputTokens()
                );

                backend.execute(request, new RequestSink(requestId, currentMessage, saveHistory, callback));
            } catch (Exception e) {
                if (!activeRequests.containsKey(requestId)) {
                    return;
                }
                FileLog.e("AI Error: ", e);
                notifyErrorAndFinish(requestId, callback,
                        HttpURLConnection.HTTP_INTERNAL_ERROR,
                        e.getMessage() != null ? e.getMessage() : "Unknown error"
                );
            }
        }, null);
        activeRequests.put(requestId, task);
        REQUEST_EXECUTOR.execute(task);

        return requestId;
    }

    private void sendStreamChunk(String requestId, String content, GenerationCallback callback) {
        if (!TextUtils.isEmpty(content)) {
            AndroidUtilities.runOnUIThread(() -> {
                if (activeRequests.containsKey(requestId) && callback != null) {
                    callback.onChunk(content);
                }
            });
        }
    }

    private void notifyThinking(String requestId, GenerationCallback callback) {
        AndroidUtilities.runOnUIThread(() -> {
            if (activeRequests.containsKey(requestId) && callback != null) {
                callback.onThinking();
            }
        });
    }

    private ArrayList<Message> historyForRequest(ArrayList<Message> history, Backend backend) {
        ArrayList<Message> trimmed = new ArrayList<>();
        int charsLimit = backend.getHistoryCharsLimit();
        int messagesLimit = backend.getHistoryMessagesLimit();
        int chars = 0;

        for (int i = history.size() - 1; i > 0 && trimmed.size() < messagesLimit; i--) {
            Message assistant = history.get(i);
            if (assistant == null || !Message.ASSISTANT_ROLE.equals(assistant.role()) || TextUtils.isEmpty(assistant.content())) {
                continue;
            }

            Message user = history.get(i - 1);
            if (user == null || Message.ASSISTANT_ROLE.equals(user.role()) || TextUtils.isEmpty(user.content())) {
                continue;
            }

            int length = user.content().length() + assistant.content().length();
            if (chars + length > charsLimit || trimmed.size() + 2 > messagesLimit) {
                continue;
            }

            trimmed.add(0, copyTextMessage(user));
            trimmed.add(1, copyTextMessage(assistant));
            chars += length;
        }

        return trimmed;
    }

    private void saveTurn(Message currentMessage, String response) {
        ArrayList<Message> history = AiConfig.getConversationHistory();
        history.add(copyTextMessage(currentMessage));
        history.add(new Message(Message.ASSISTANT_ROLE, response));

        int chars = 0;
        for (Message message : history) {
            chars += message.content() == null ? 0 : message.content().length();
        }
        while (history.size() > 2 && (history.size() > STORAGE_MESSAGES_LIMIT || chars > STORAGE_CHARS_LIMIT)) {
            Message dropped = history.remove(0);
            chars -= dropped.content() == null ? 0 : dropped.content().length();
        }
        if (history.size() > 2 && Message.ASSISTANT_ROLE.equals(history.get(0).role())) {
            history.remove(0);
        }

        AiConfig.saveConversationHistory(history);
    }

    private Message copyTextMessage(Message message) {
        return new Message(message.role(), message.content());
    }

    private void notifyResponseAndFinish(String requestId, GenerationCallback callback, String response) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                if (activeRequests.containsKey(requestId) && callback != null) {
                    callback.onResponse(response);
                }
            } finally {
                finishRequest(requestId);
            }
        });
    }

    private void notifyErrorAndFinish(String requestId, GenerationCallback callback, int errorCode, String errorMessage) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                if (activeRequests.containsKey(requestId) && callback != null) {
                    callback.onError(errorCode, errorMessage);
                }
            } finally {
                finishRequest(requestId);
            }
        });
    }

    public boolean isGenerating() {
        return isGenerating.get();
    }

    private void finishRequest(String requestId) {
        activeCancellables.remove(requestId);
        activeRequests.remove(requestId);
        if (activeRequests.isEmpty()) {
            isGenerating.set(false);
        }
    }

    public void stopRequest(String requestId) {
        if (TextUtils.isEmpty(requestId)) {
            return;
        }
        Runnable canceller = activeCancellables.remove(requestId);
        if (canceller != null) {
            try {
                canceller.run();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        Future<?> task = activeRequests.remove(requestId);
        if (task != null) {
            task.cancel(true);
        }
        if (activeRequests.isEmpty()) {
            isGenerating.set(false);
        }
    }

    private final class RequestSink implements BackendSink {
        private final String requestId;
        private final Message currentMessage;
        private final boolean saveHistory;
        private final GenerationCallback callback;

        RequestSink(String requestId, Message currentMessage, boolean saveHistory, GenerationCallback callback) {
            this.requestId = requestId;
            this.currentMessage = currentMessage;
            this.saveHistory = saveHistory;
            this.callback = callback;
        }

        @Override
        public boolean isActive() {
            return activeRequests.containsKey(requestId);
        }

        @Override
        public void onCancellable(Runnable canceller) {
            if (canceller != null) {
                activeCancellables.put(requestId, canceller);
            }
        }

        @Override
        public void onThinking() {
            notifyThinking(requestId, callback);
        }

        @Override
        public void onChunk(String accumulatedResponse) {
            sendStreamChunk(requestId, accumulatedResponse, callback);
        }

        @Override
        public void onComplete(String response) {
            if (!isActive()) {
                return;
            }
            if (TextUtils.isEmpty(response)) {
                onError(HttpURLConnection.HTTP_NO_CONTENT, "Response body is empty");
                return;
            }
            if (saveHistory) {
                saveTurn(currentMessage, response);
            }
            notifyResponseAndFinish(requestId, callback, response);
        }

        @Override
        public void onError(int errorCode, String errorMessage) {
            notifyErrorAndFinish(requestId, callback, errorCode, errorMessage);
        }
    }

    public static class Builder {
        private Service serviceOverride;
        private Role roleOverride;

        public Builder serviceOverride(Service serviceOverride) {
            this.serviceOverride = serviceOverride;
            return this;
        }

        public Builder roleOverride(Role roleOverride) {
            this.roleOverride = roleOverride;
            return this;
        }

        public Client build() {
            return new Client(this);
        }
    }

    private record ImagePayload(byte[] data, String mimeType) {
    }
}
