/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.ai.network.backend;

import static com.exteragram.messenger.translator.TranslatorUtils.formatUserAgent;

import android.text.TextUtils;
import android.util.Base64;

import com.exteragram.messenger.ai.data.Message;
import com.exteragram.messenger.ai.data.Role;
import com.exteragram.messenger.ai.data.Service;
import com.exteragram.messenger.ai.network.ProxyDns;
import com.exteragram.messenger.utils.network.ExteraHttpClient;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class RestBackend implements Backend {
    private static final int STREAM_SYMBOLS_LIMIT = SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_AVERAGE ? 10 : 20;
    private static final int HISTORY_MESSAGES_LIMIT = 32;
    private static final int HISTORY_CHARS_LIMIT = 24000;
    private static final int MAX_OUTPUT_TOKENS = 4096;
    private static final float MAX_TEMPERATURE = 2f;
    private static final int MAX_PLAIN_ERROR_LENGTH = 200;
    private static final String THINK_OPEN_TAG = "<think>";
    private static final String THINK_CLOSE_TAG = "</think>";
    private static final String REASONING_EFFORT_NONE = "none";
    private static final String REASONING_EFFORT_MINIMAL = "minimal";
    private static final String REASONING_EFFORT_LOW = "low";
    private static final String REASONING_EFFORT_DEFAULT = "";
    private static final String[] REASONING_ERROR_KEYWORDS = {"reasoning", "thinking", "effort"};
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String GEMINI_CHAT_COMPLETIONS_URL = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
    private static final String PERPLEXITY_AGENT_URL = "https://api.perplexity.ai/v1/agent";
    private static final String[] ERROR_MESSAGE_KEYS = {"error", "message", "detail"};
    private static final String[] QUOTA_ERROR_CODES = {"insufficient_quota", "credit_balance_exhausted", "billing_hard_limit_reached"};

    private static final Map<String, RequestOptions> requestOptionsFallbacks = new ConcurrentHashMap<>();

    private final OkHttpClient httpClient;

    public RestBackend() {
        this.httpClient = ExteraHttpClient.INSTANCE.getClient().newBuilder()
                .dns(ProxyDns.INSTANCE)
                .connectTimeout(1, TimeUnit.MINUTES)
                .readTimeout(5, TimeUnit.MINUTES)
                .build();
    }

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
        Endpoint endpoint = resolveEndpoint(request.service().getUrl());
        if (endpoint == null) {
            sink.onError(HttpURLConnection.HTTP_INTERNAL_ERROR, "Failed to create request body");
            return;
        }

        Service service = request.service();
        String fallbackKey = endpoint.url() + '\n' + service.getModel() + '\n' + service.isReasoningEnabled();
        RequestOptions initialOptions = new RequestOptions(
                getDisabledReasoningEffort(service),
                !endpoint.responsesApi() && isOpenAiService(endpoint.url().toLowerCase(Locale.ROOT)),
                false
        );
        RequestOptions options = requestOptionsFallbacks.getOrDefault(fallbackKey, initialOptions);

        while (true) {
            Request httpRequest = createRequest(request, endpoint, options);
            if (httpRequest == null) {
                sink.onError(HttpURLConnection.HTTP_INTERNAL_ERROR, "Failed to create request body");
                return;
            }

            if (!sink.isActive()) {
                return;
            }

            Call call = httpClient.newCall(httpRequest);
            sink.onCancellable(call::cancel);

            try (Response response = call.execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = null;
                    try {
                        errorBody = response.body().string();
                        FileLog.e("AI_ERROR_RESPONSE_BODY (" + response.code() + "): " + errorBody);
                    } catch (IOException ioException) {
                        FileLog.e("AI_ERROR_READING_RESPONSE_BODY: ", ioException);
                    }
                    String errorMessage = parseErrorMessage(errorBody, response.message());
                    RequestOptions fallbackOptions = getFallbackOptions(options, response.code(), errorMessage);
                    if (fallbackOptions != null) {
                        FileLog.d("AI_REQUEST_FALLBACK: " + options + " -> " + fallbackOptions);
                        options = fallbackOptions;
                        continue;
                    }
                    int errorCode = response.code() == 429 && isQuotaError(errorBody) ? 402 : response.code();
                    sink.onError(errorCode, errorMessage);
                    return;
                }

                if (!options.equals(initialOptions)) {
                    requestOptionsFallbacks.put(fallbackKey, options);
                }
                handleResponse(response, request, endpoint, sink);
                return;
            }
        }
    }

    private void handleResponse(Response response, BackendRequest request, Endpoint endpoint, BackendSink sink) throws IOException {
        ResponseBody body = response.body();
        if (request.stream()) {
            handleStreamResponse(body, sink, endpoint.responsesApi());
            return;
        }

        String responseBody = body.string();
        String content = parseResponseContent(responseBody, endpoint.responsesApi());
        if (TextUtils.isEmpty(content)) {
            String errorMessage = extractErrorMessage(responseBody);
            if (!TextUtils.isEmpty(errorMessage)) {
                sink.onError(HttpURLConnection.HTTP_INTERNAL_ERROR, errorMessage);
                return;
            }
        }
        if (content == null) {
            sink.onError(HttpURLConnection.HTTP_INTERNAL_ERROR, "Failed to parse response");
            return;
        }
        sink.onComplete(content);
    }

    private Endpoint resolveEndpoint(String baseUrl) {
        if (TextUtils.isEmpty(baseUrl)) {
            return null;
        }
        String lowerBaseUrl = baseUrl.toLowerCase(Locale.ROOT);
        if (isGeminiService(lowerBaseUrl)) {
            return new Endpoint(GEMINI_CHAT_COMPLETIONS_URL, false);
        }
        if (isPerplexityService(lowerBaseUrl) && !lowerBaseUrl.contains("/router")) {
            return new Endpoint(PERPLEXITY_AGENT_URL, true);
        }

        String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String lowerUrl = url.toLowerCase(Locale.ROOT);
        if (lowerUrl.endsWith("/responses") || lowerUrl.endsWith("/agent")) {
            return new Endpoint(url, true);
        }
        return new Endpoint(lowerUrl.endsWith(CHAT_COMPLETIONS_PATH) ? url : url + CHAT_COMPLETIONS_PATH, false);
    }

    private Request createRequest(BackendRequest request, Endpoint endpoint, RequestOptions options) {
        Service selectedService = request.service();
        boolean responsesApi = endpoint.responsesApi();

        JSONObject jsonBody = new JSONObject();
        JSONArray messagesArray = new JSONArray();

        try {
            Role activeRole = request.role();
            if (activeRole != null && !TextUtils.isEmpty(activeRole.getPrompt())) {
                if (responsesApi) {
                    jsonBody.put("instructions", activeRole.getPrompt());
                } else {
                    messagesArray.put(new JSONObject()
                            .put("role", Message.SYSTEM_ROLE)
                            .put("content", activeRole.getPrompt()));
                }
            }

            for (Message message : request.history()) {
                messagesArray.put(createMessageObject(message, responsesApi));
            }
            messagesArray.put(createMessageObject(request.currentMessage(), responsesApi));

            jsonBody.put("model", selectedService.getModel());
            jsonBody.put(responsesApi ? "input" : "messages", messagesArray);
            jsonBody.put("stream", request.stream());
            if (!options.defaultTemperature()) {
                jsonBody.put("temperature", request.temperature());
            }
            applyReasoningEffort(jsonBody, options.reasoningEffort(), responsesApi || isOpenRouterService(selectedService.getUrl().toLowerCase(Locale.ROOT)));
            if (responsesApi) {
                jsonBody.put("max_output_tokens", request.maxOutputTokens());
            } else {
                jsonBody.put(options.maxCompletionTokens() ? "max_completion_tokens" : "max_tokens", request.maxOutputTokens());
            }
            if (responsesApi) {
                jsonBody.put("store", false);
            }

            FileLog.d("AI_REQUEST_URL: " + endpoint.url());
            FileLog.d("AI_REQUEST_MODEL: " + selectedService.getModel());
            FileLog.d("AI_REQUEST_MESSAGES: " + messagesArray.length() + ", stream=" + request.stream() + ", image=" + (request.currentMessage().getImageData() != null));

        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }

        RequestBody body = RequestBody.create(
                jsonBody.toString(),
                MediaType.parse("application/json")
        );

        Request.Builder requestBuilder = new Request.Builder()
                .url(endpoint.url())
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + selectedService.getKey())
                .addHeader("User-Agent", formatUserAgent())
                .addHeader("HTTP-Referer", "exteragram.app")
                .addHeader("X-Title", "exteraGram");

        return requestBuilder.post(body).build();
    }

    private String getDisabledReasoningEffort(Service selectedService) {
        if (selectedService.isReasoningEnabled()) {
            return null;
        }

        String baseUrl = selectedService.getUrl();
        String model = selectedService.getModel();
        String lowerBaseUrl = baseUrl == null ? "" : baseUrl.toLowerCase(Locale.ROOT);
        String lowerModel = model == null ? "" : model.toLowerCase(Locale.ROOT);

        if (isOpenRouterService(lowerBaseUrl)) {
            return REASONING_EFFORT_NONE;
        } else if (isGeminiService(lowerBaseUrl) && isGeminiReasoningModel(lowerModel)) {
            return getGeminiReasoningEffort(lowerModel);
        } else if (isOpenAiService(lowerBaseUrl) && isOpenAiReasoningModel(lowerModel)) {
            return getOpenAiReasoningEffort(lowerModel);
        } else if (isPerplexityService(lowerBaseUrl) && (isGeminiReasoningModel(lowerModel) || isOpenAiReasoningModel(lowerModel))) {
            return REASONING_EFFORT_MINIMAL;
        }
        return null;
    }

    private void applyReasoningEffort(JSONObject jsonBody, String effort, boolean reasoningObject) throws Exception {
        if (TextUtils.isEmpty(effort)) {
            return;
        }
        if (reasoningObject) {
            jsonBody.put("reasoning", new JSONObject().put("effort", effort));
        } else {
            jsonBody.put("reasoning_effort", effort);
        }
    }

    private RequestOptions getFallbackOptions(RequestOptions options, int code, String errorMessage) {
        if ((code != HttpURLConnection.HTTP_BAD_REQUEST && code != 422) || TextUtils.isEmpty(errorMessage)) {
            return null;
        }
        String lowerMessage = errorMessage.toLowerCase(Locale.ROOT);
        if (!options.maxCompletionTokens() && lowerMessage.contains("max_tokens") && lowerMessage.contains("max_completion_tokens")) {
            return new RequestOptions(options.reasoningEffort(), true, options.defaultTemperature());
        }
        if (!options.defaultTemperature() && lowerMessage.contains("temperature")) {
            return new RequestOptions(options.reasoningEffort(), options.maxCompletionTokens(), true);
        }
        if (!TextUtils.isEmpty(options.reasoningEffort()) && containsReasoningKeyword(lowerMessage)) {
            return new RequestOptions(getNextReasoningEffort(options.reasoningEffort()), options.maxCompletionTokens(), options.defaultTemperature());
        }
        return null;
    }

    private boolean containsReasoningKeyword(String lowerMessage) {
        for (String keyword : REASONING_ERROR_KEYWORDS) {
            if (lowerMessage.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String getNextReasoningEffort(String effort) {
        return switch (effort) {
            case REASONING_EFFORT_NONE -> REASONING_EFFORT_MINIMAL;
            case REASONING_EFFORT_MINIMAL -> REASONING_EFFORT_LOW;
            default -> REASONING_EFFORT_DEFAULT;
        };
    }

    private boolean isOpenRouterService(String lowerBaseUrl) {
        return lowerBaseUrl.contains("openrouter.ai");
    }

    private boolean isGeminiService(String lowerBaseUrl) {
        return lowerBaseUrl.contains("generativelanguage.googleapis");
    }

    private boolean isGeminiReasoningModel(String lowerModel) {
        lowerModel = stripProviderPrefix(lowerModel);
        return lowerModel.startsWith("gemini-2.5")
                || lowerModel.startsWith("gemini-3")
                || lowerModel.contains("thinking");
    }

    private boolean isOpenAiService(String lowerBaseUrl) {
        return lowerBaseUrl.contains("api.openai.com");
    }

    private boolean isPerplexityService(String lowerBaseUrl) {
        return lowerBaseUrl.contains("api.perplexity.ai");
    }

    private String getGeminiReasoningEffort(String lowerModel) {
        if (lowerModel.contains("gemini-2.5") && !lowerModel.contains("pro")) {
            return REASONING_EFFORT_NONE;
        }
        if (lowerModel.contains("gemini-3") && lowerModel.contains("pro")) {
            return REASONING_EFFORT_LOW;
        }
        return REASONING_EFFORT_MINIMAL;
    }

    private boolean isOpenAiReasoningModel(String lowerModel) {
        lowerModel = stripProviderPrefix(lowerModel);
        return !lowerModel.contains("gpt-5-chat")
                && (lowerModel.startsWith("gpt-5")
                || lowerModel.startsWith("o1")
                || lowerModel.startsWith("o3")
                || lowerModel.startsWith("o4"));
    }

    private String getOpenAiReasoningEffort(String lowerModel) {
        lowerModel = stripProviderPrefix(lowerModel);
        if (lowerModel.startsWith("o")) {
            return REASONING_EFFORT_LOW;
        }
        return supportsOpenAiNoReasoning(lowerModel) ? REASONING_EFFORT_NONE : REASONING_EFFORT_MINIMAL;
    }

    private boolean supportsOpenAiNoReasoning(String lowerModel) {
        return lowerModel.startsWith("gpt-5.1")
                || lowerModel.startsWith("gpt-5.2")
                || lowerModel.startsWith("gpt-5.3")
                || lowerModel.startsWith("gpt-5.4")
                || lowerModel.startsWith("gpt-5.5");
    }

    private String stripProviderPrefix(String lowerModel) {
        int index = lowerModel.indexOf('/');
        return index >= 0 ? lowerModel.substring(index + 1) : lowerModel;
    }

    private JSONObject createMessageObject(Message message, boolean responsesApi) throws Exception {
        JSONObject messageJson = new JSONObject();
        if (responsesApi) {
            messageJson.put("type", "message");
        }
        messageJson.put("role", message.role());

        if (message.getImageData() != null && !TextUtils.isEmpty(message.getMimeType())) {
            JSONArray contentArray = new JSONArray();

            if (!TextUtils.isEmpty(message.content())) {
                contentArray.put(new JSONObject()
                        .put("type", responsesApi ? "input_text" : "text")
                        .put("text", message.content()));
            }

            String base64Image = Base64.encodeToString(message.getImageData(), Base64.NO_WRAP);
            String dataUrl = "data:" + message.getMimeType() + ";base64," + base64Image;
            if (responsesApi) {
                contentArray.put(new JSONObject()
                        .put("type", "input_image")
                        .put("image_url", dataUrl));
            } else {
                contentArray.put(new JSONObject()
                        .put("type", "image_url")
                        .put("image_url", new JSONObject().put("url", dataUrl)));
            }

            messageJson.put("content", contentArray);
        } else {
            messageJson.put("content", message.content());
        }
        return messageJson;
    }

    private void handleStreamResponse(ResponseBody body, BackendSink sink, boolean responsesApi) {
        var responseBuilder = new StringBuilder();
        var reasoningFilter = new ReasoningContentFilter();
        var chunkLength = 0;
        String streamError = null;

        try (var reader = new BufferedReader(new InputStreamReader(body.byteStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!sink.isActive()) {
                    break;
                }
                if (TextUtils.isEmpty(line)) continue;
                if (line.startsWith("data:")) {
                    line = line.substring("data:".length()).trim();

                    StreamResponsePart part;
                    if (line.equals("[DONE]")) {
                        part = StreamResponsePart.DONE;
                    } else if (responsesApi) {
                        part = parseResponsesStreamPart(line);
                    } else {
                        part = parseStreamResponsePart(line);
                    }

                    if (part.error() != null) {
                        streamError = part.error();
                        break;
                    }
                    if (part.hasReasoning()) {
                        sink.onThinking();
                    }

                    String content = reasoningFilter.filter(part.content());
                    if (reasoningFilter.consumeReasoningSignal()) {
                        sink.onThinking();
                    }

                    if (!TextUtils.isEmpty(content)) {
                        responseBuilder.append(content);
                        chunkLength += content.length();
                        if (chunkLength >= STREAM_SYMBOLS_LIMIT) {
                            sink.onChunk(responseBuilder.toString());
                            chunkLength = 0;
                        }
                    }

                    if (part.done()) {
                        if (chunkLength > 0) {
                            sink.onChunk(responseBuilder.toString());
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            if (sink.isActive()) {
                FileLog.e(e);
                streamError = e.getMessage() != null ? e.getMessage() : "Unknown error";
            }
        }

        if (!sink.isActive()) {
            return;
        }

        if (streamError != null) {
            sink.onError(HttpURLConnection.HTTP_INTERNAL_ERROR, streamError);
            return;
        }

        String pendingContent = reasoningFilter.flush();
        if (!TextUtils.isEmpty(pendingContent)) {
            responseBuilder.append(pendingContent);
            sink.onChunk(responseBuilder.toString());
        }

        var finalResponse = trimTrailing(responseBuilder.toString());
        if (!TextUtils.isEmpty(finalResponse)) {
            sink.onComplete(finalResponse);
        } else {
            sink.onError(HttpURLConnection.HTTP_NO_CONTENT, "Response body is empty");
        }
    }

    private String parseResponseContent(String jsonResponse, boolean responsesApi) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            if (responsesApi) {
                String text = parseResponsesOutput(jsonObject);
                return text != null ? stripReasoningMarkup(text) : null;
            }

            JSONArray choices = jsonObject.optJSONArray("choices");

            if (choices != null && choices.length() > 0) {
                JSONObject choice = choices.getJSONObject(0);
                JSONObject message = choice.optJSONObject("message");

                if (message == null || !message.has("content") || message.isNull("content")) {
                    return null;
                }

                return stripReasoningMarkup(getContentText(message.opt("content")));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    private String parseResponsesOutput(JSONObject response) {
        JSONArray output = response.optJSONArray("output");
        if (output == null) {
            return null;
        }

        StringBuilder text = new StringBuilder();
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            JSONArray content = item != null && "message".equals(item.optString("type")) ? item.optJSONArray("content") : null;
            if (content == null) {
                continue;
            }
            for (int j = 0; j < content.length(); j++) {
                JSONObject part = content.optJSONObject(j);
                if (part != null && "output_text".equals(part.optString("type"))) {
                    text.append(part.optString("text"));
                }
            }
        }
        return text.toString();
    }

    private StreamResponsePart parseStreamResponsePart(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONArray choices = jsonObject.optJSONArray("choices");

            if (choices != null && choices.length() > 0) {
                JSONObject choice = choices.getJSONObject(0);
                JSONObject delta = choice.optJSONObject("delta");
                if (delta == null) {
                    return new StreamResponsePart("", false);
                }

                Object content = delta.opt("content");
                return new StreamResponsePart(getContentText(content), hasReasoning(delta) || hasThinkingPart(content));
            }
        } catch (Exception ignored) {
        }
        return new StreamResponsePart("", false);
    }

    private StreamResponsePart parseResponsesStreamPart(String jsonResponse) {
        try {
            JSONObject event = new JSONObject(jsonResponse);
            String type = event.optString("type");
            switch (type) {
                case "response.output_text.delta" -> {
                    return new StreamResponsePart(event.optString("delta"), false);
                }
                case "response.completed", "response.incomplete" -> {
                    return StreamResponsePart.DONE;
                }
                case "response.failed", "error" -> {
                    return new StreamResponsePart("", false, true, getResponsesStreamError(event));
                }
                case "response.output_item.added" -> {
                    JSONObject item = event.optJSONObject("item");
                    return new StreamResponsePart("", item != null && "reasoning".equals(item.optString("type")));
                }
                default -> {
                    return new StreamResponsePart("", type.startsWith("response.reasoning"));
                }
            }
        } catch (Exception ignored) {
        }
        return new StreamResponsePart("", false);
    }

    private String getResponsesStreamError(JSONObject event) {
        JSONObject error = event.optJSONObject("error");
        if (error == null) {
            JSONObject response = event.optJSONObject("response");
            error = response != null ? response.optJSONObject("error") : null;
        }
        String message = error != null ? error.optString("message") : event.optString("message");
        return TextUtils.isEmpty(message) ? "Unknown error" : message;
    }

    private String getContentText(Object content) {
        if (content == null || content == JSONObject.NULL) {
            return "";
        }
        if (!(content instanceof JSONArray array)) {
            return content.toString();
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < array.length(); i++) {
            Object item = array.opt(i);
            if (item instanceof String string) {
                text.append(string);
            } else if (item instanceof JSONObject part) {
                String type = part.optString("type");
                if ((type.isEmpty() || "text".equals(type) || "output_text".equals(type)) && !part.isNull("text")) {
                    text.append(part.optString("text"));
                }
            }
        }
        return text.toString();
    }

    private boolean hasThinkingPart(Object content) {
        if (!(content instanceof JSONArray array)) {
            return false;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject part = array.optJSONObject(i);
            if (part != null && ("thinking".equals(part.optString("type")) || "reasoning".equals(part.optString("type")))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasReasoning(JSONObject message) {
        return hasValue(message, "reasoning")
                || hasValue(message, "reasoning_content")
                || hasValue(message, "reasoning_details");
    }

    private boolean hasValue(JSONObject object, String key) {
        if (object == null || !object.has(key) || object.isNull(key)) {
            return false;
        }
        Object value = object.opt(key);
        if (value instanceof String string) {
            return !TextUtils.isEmpty(string);
        }
        if (value instanceof JSONArray array) {
            return array.length() > 0;
        }
        return value != null && value != JSONObject.NULL;
    }

    private String stripReasoningMarkup(String content) {
        StringBuilder result = new StringBuilder(content.length());
        String lowerContent = content.toLowerCase(Locale.ROOT);
        int index = 0;
        while (index < content.length()) {
            int openIndex = lowerContent.indexOf(THINK_OPEN_TAG, index);
            if (openIndex < 0) {
                result.append(content, index, content.length());
                break;
            }

            result.append(content, index, openIndex);
            int closeIndex = lowerContent.indexOf(THINK_CLOSE_TAG, openIndex + THINK_OPEN_TAG.length());
            if (closeIndex < 0) {
                break;
            }
            index = closeIndex + THINK_CLOSE_TAG.length();
        }
        return trimLeading(result.toString());
    }

    private String trimLeading(String text) {
        int index = 0;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return text.substring(index);
    }

    private String trimTrailing(String text) {
        int index = text.length();
        while (index > 0 && Character.isWhitespace(text.charAt(index - 1))) {
            index--;
        }
        return text.substring(0, index);
    }

    private String parseErrorMessage(String errorBody, String fallback) {
        String message = extractErrorMessage(errorBody);
        if (!TextUtils.isEmpty(message)) {
            return message;
        }
        return !TextUtils.isEmpty(fallback) ? fallback.toLowerCase(Locale.ROOT) : "";
    }

    private static boolean isQuotaError(String errorBody) {
        if (TextUtils.isEmpty(errorBody) || !errorBody.trim().startsWith("{")) {
            return false;
        }
        try {
            JSONObject error = new JSONObject(errorBody.trim()).optJSONObject("error");
            if (error == null) {
                return false;
            }
            String type = error.optString("type");
            String code = error.optString("code");
            for (String quotaCode : QUOTA_ERROR_CODES) {
                if (quotaCode.equals(type) || quotaCode.equals(code)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private String extractErrorMessage(String errorBody) {
        if (TextUtils.isEmpty(errorBody)) {
            return null;
        }
        String trimmed = errorBody.trim();
        try {
            if (trimmed.startsWith("[")) {
                JSONArray array = new JSONArray(trimmed);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    JSONObject error = item == null ? null : item.optJSONObject("error");
                    String message = error == null ? null : error.optString("message", null);
                    if (!TextUtils.isEmpty(message)) {
                        return message;
                    }
                }
                return null;
            }
            if (trimmed.startsWith("{")) {
                JSONObject json = new JSONObject(trimmed);
                JSONObject error = json.optJSONObject("error");
                if (error != null) {
                    return error.optString("message", null);
                }
                for (String key : ERROR_MESSAGE_KEYS) {
                    if (json.opt(key) instanceof String message && !message.isEmpty()) {
                        return message;
                    }
                }
                return null;
            }
        } catch (Exception ignored) {
            return null;
        }
        return !trimmed.startsWith("<") && trimmed.length() <= MAX_PLAIN_ERROR_LENGTH && trimmed.indexOf('\n') < 0 ? trimmed : null;
    }

    private static class ReasoningContentFilter {
        private String pending = "";
        private boolean inReasoning;
        private boolean reasoningSignal;

        String filter(String chunk) {
            if (TextUtils.isEmpty(chunk)) {
                return null;
            }

            String text = pending + chunk;
            pending = "";
            StringBuilder result = new StringBuilder(text.length());
            int index = 0;

            while (index < text.length()) {
                String lowerText = text.toLowerCase(Locale.ROOT);
                if (inReasoning) {
                    reasoningSignal = true;
                    int closeIndex = lowerText.indexOf(THINK_CLOSE_TAG, index);
                    if (closeIndex < 0) {
                        pending = getCloseTagPrefixSuffix(text, index);
                        return result.toString();
                    }
                    index = closeIndex + THINK_CLOSE_TAG.length();
                    inReasoning = false;
                } else {
                    int openIndex = lowerText.indexOf(THINK_OPEN_TAG, index);
                    if (openIndex < 0) {
                        pending = getOpenTagPrefixSuffix(text, index);
                        reasoningSignal = !pending.isEmpty();
                        int safeEnd = text.length() - pending.length();
                        if (safeEnd > index) {
                            result.append(text, index, safeEnd);
                        }
                        return result.toString();
                    }
                    result.append(text, index, openIndex);
                    index = openIndex + THINK_OPEN_TAG.length();
                    inReasoning = true;
                    reasoningSignal = true;
                }
            }

            return result.toString();
        }

        boolean consumeReasoningSignal() {
            boolean value = reasoningSignal;
            reasoningSignal = false;
            return value;
        }

        String flush() {
            String value = inReasoning ? "" : pending;
            pending = "";
            return value;
        }

        private String getOpenTagPrefixSuffix(String text, int start) {
            int max = Math.min(THINK_OPEN_TAG.length() - 1, text.length() - start);
            String lowerText = text.toLowerCase(Locale.ROOT);
            for (int length = max; length > 0; length--) {
                String suffix = lowerText.substring(text.length() - length);
                if (THINK_OPEN_TAG.startsWith(suffix)) {
                    return text.substring(text.length() - length);
                }
            }
            return "";
        }

        private String getCloseTagPrefixSuffix(String text, int start) {
            int max = Math.min(THINK_CLOSE_TAG.length() - 1, text.length() - start);
            String lowerText = text.toLowerCase(Locale.ROOT);
            for (int length = max; length > 0; length--) {
                String suffix = lowerText.substring(text.length() - length);
                if (THINK_CLOSE_TAG.startsWith(suffix)) {
                    return text.substring(text.length() - length);
                }
            }
            return "";
        }
    }

    private record Endpoint(String url, boolean responsesApi) {
    }

    private record RequestOptions(String reasoningEffort, boolean maxCompletionTokens, boolean defaultTemperature) {
    }

    private record StreamResponsePart(String content, boolean hasReasoning, boolean done, String error) {
        static final StreamResponsePart DONE = new StreamResponsePart("", false, true, null);

        StreamResponsePart(String content, boolean hasReasoning) {
            this(content, hasReasoning, false, null);
        }
    }
}
