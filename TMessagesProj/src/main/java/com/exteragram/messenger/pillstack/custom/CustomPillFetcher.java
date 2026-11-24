/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.custom;

import android.location.Location;
import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.exteragram.messenger.pillstack.ui.pills.crypto.utils.PillStackCurrencies;
import com.exteragram.messenger.utils.network.ExteraHttpClient;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Stories.recorder.Weather;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okio.BufferedSource;

public final class CustomPillFetcher {

    public static final int MAX_RESPONSE_BYTES = 512 * 1024;

    public static final int ERROR_NONE = 0;
    public static final int ERROR_NETWORK = 1;
    public static final int ERROR_TOO_LARGE = 2;
    public static final int ERROR_BAD_URL = 3;
    public static final int ERROR_LOCATION = 4;

    public static final String VAR_LANG = "lang";
    public static final String VAR_CURRENCY = "currency";
    public static final String VAR_LAT = "lat";
    public static final String VAR_LON = "lon";
    public static final String VAR_SECRET_PREFIX = "secret.";

    private static final Pattern VARIABLE = Pattern.compile("\\{(lang|currency|lat|lon|secret\\.[A-Za-z_][A-Za-z0-9_]*)\\}");

    private static OkHttpClient client;
    private static final SparseArray<ArrayList<Utilities.Callback<Response>>> pending = new SparseArray<>();

    private CustomPillFetcher() {
    }

    public static final class Response {
        public int error;
        public int status;
        public long ms;
        public long bytes;
        public long time;
        @Nullable
        public JsonElement json;
        @Nullable
        public String body;

        public boolean isSuccessful() {
            return error == ERROR_NONE && status >= 200 && status < 300;
        }

        static Response error(int error) {
            Response response = new Response();
            response.error = error;
            response.time = System.currentTimeMillis() / 1000;
            return response;
        }
    }

    public static boolean needsLocation(CustomPillSpec spec) {
        if (!spec.http) {
            return false;
        }
        if (usesLocation(spec.url) || CustomPillSpec.METHOD_POST.equals(spec.method) && usesLocation(spec.body)) {
            return true;
        }
        for (CustomPillSpec.Header header : spec.headers) {
            if (usesLocation(header.value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean usesLocation(@Nullable String value) {
        return value != null && (value.contains("{" + VAR_LAT + "}") || value.contains("{" + VAR_LON + "}"));
    }

    public static void fetch(CustomPillSpec spec, @Nullable Map<String, String> secrets, Utilities.Callback<Response> callback) {
        final int key = spec.id;
        if (key != 0) {
            ArrayList<Utilities.Callback<Response>> callbacks = pending.get(key);
            if (callbacks != null) {
                callbacks.add(callback);
                return;
            }
            callbacks = new ArrayList<>();
            callbacks.add(callback);
            pending.put(key, callbacks);
        }
        final Utilities.Callback<Response> deliver = response -> AndroidUtilities.runOnUIThread(() -> {
            if (key == 0) {
                callback.run(response);
                return;
            }
            ArrayList<Utilities.Callback<Response>> callbacks = pending.get(key);
            pending.remove(key);
            if (callbacks != null) {
                for (Utilities.Callback<Response> c : callbacks) {
                    c.run(response);
                }
            }
        });
        if (needsLocation(spec)) {
            if (!Weather.isLocationPermissionGranted()) {
                deliver.run(Response.error(ERROR_LOCATION));
                return;
            }
            Weather.getUserLocation(false, location -> {
                if (location == null) {
                    deliver.run(Response.error(ERROR_LOCATION));
                } else {
                    execute(spec, secrets, location, deliver);
                }
            });
        } else {
            execute(spec, secrets, null, deliver);
        }
    }

    private static void execute(CustomPillSpec spec, @Nullable Map<String, String> secrets, @Nullable Location location, Utilities.Callback<Response> deliver) {
        Request request;
        try {
            request = buildRequest(spec, secrets, location);
        } catch (Exception e) {
            FileLog.e(e);
            request = null;
        }
        if (request == null) {
            deliver.run(Response.error(ERROR_BAD_URL));
            return;
        }
        final long start = SystemClock.elapsedRealtime();
        getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                deliver.run(Response.error(ERROR_NETWORK));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull okhttp3.Response httpResponse) {
                Response response = new Response();
                response.time = System.currentTimeMillis() / 1000;
                response.status = httpResponse.code();
                try (ResponseBody responseBody = httpResponse.body()) {
                    BufferedSource source = responseBody.source();
                    if (source.request(MAX_RESPONSE_BYTES + 1L)) {
                        response.error = ERROR_TOO_LARGE;
                        response.ms = SystemClock.elapsedRealtime() - start;
                        deliver.run(response);
                        return;
                    }
                    MediaType contentType = responseBody.contentType();
                    Charset charset = contentType != null ? contentType.charset(StandardCharsets.UTF_8) : StandardCharsets.UTF_8;
                    response.bytes = source.getBuffer().size();
                    String text = source.getBuffer().readString(charset != null ? charset : StandardCharsets.UTF_8);
                    response.ms = SystemClock.elapsedRealtime() - start;
                    response.body = text;
                    response.json = parseJson(text);
                } catch (Exception e) {
                    FileLog.e(e);
                    response.error = ERROR_NETWORK;
                    response.ms = SystemClock.elapsedRealtime() - start;
                }
                deliver.run(response);
            }
        });
    }

    @Nullable
    private static JsonElement parseJson(@Nullable String text) {
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return null;
        }
        try {
            return JsonParser.parseString(trimmed);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static Request buildRequest(CustomPillSpec spec, @Nullable Map<String, String> secrets, @Nullable Location location) {
        if (!CustomPillSpec.isValidUrl(spec.url)) {
            return null;
        }
        HttpUrl url = HttpUrl.parse(substitute(spec.url.trim(), secrets, location, true));
        if (url == null || !url.isHttps() && !CustomPillSpec.isLocalHost(url.host()) && spec.usesSecrets()) {
            return null;
        }
        Request.Builder builder = new Request.Builder()
                .url(url)
                .header("User-Agent", "exteraGram/" + BuildVars.BUILD_VERSION_STRING + " (Android; Custom Pill)")
                .header("Accept", "application/json, text/plain, */*");
        for (CustomPillSpec.Header header : spec.headers) {
            if (!CustomPillSpec.isValidHeaderName(header.name)) {
                continue;
            }
            String value = substitute(header.value, secrets, location, false).replace("\r", "").replace("\n", "");
            builder.header(header.name, value);
        }
        if (CustomPillSpec.METHOD_POST.equals(spec.method)) {
            String body = substitute(spec.body, secrets, location, false);
            String trimmed = body.trim();
            MediaType type = MediaType.parse(trimmed.startsWith("{") || trimmed.startsWith("[") ? "application/json; charset=utf-8" : "text/plain; charset=utf-8");
            builder.post(RequestBody.create(body, type));
        }
        return builder.build();
    }

    public static String substitute(@Nullable String value, @Nullable Map<String, String> secrets, @Nullable Location location, boolean encode) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        Matcher matcher = VARIABLE.matcher(value);
        StringBuilder builder = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            builder.append(value, last, matcher.start());
            String replacement = resolveVariable(matcher.group(1), secrets, location);
            builder.append(encode ? Uri.encode(replacement) : replacement);
            last = matcher.end();
        }
        builder.append(value, last, value.length());
        return builder.toString();
    }

    private static String resolveVariable(String name, @Nullable Map<String, String> secrets, @Nullable Location location) {
        if (name.startsWith(VAR_SECRET_PREFIX)) {
            String secret = secrets != null ? secrets.get(name.substring(VAR_SECRET_PREFIX.length())) : null;
            return secret != null ? secret : "";
        }
        return switch (name) {
            case VAR_LANG -> LocaleController.getInstance().getCurrentLocale().getLanguage();
            case VAR_CURRENCY -> PillStackCurrencies.resolveTargetCurrency(UserConfig.selectedAccount, PillStackCurrencies.TARGET_AUTO);
            case VAR_LAT -> location != null ? String.format(Locale.US, "%.4f", location.getLatitude()) : "";
            case VAR_LON -> location != null ? String.format(Locale.US, "%.4f", location.getLongitude()) : "";
            default -> "";
        };
    }

    private static OkHttpClient getClient() {
        if (client == null) {
            client = ExteraHttpClient.INSTANCE.getClient().newBuilder()
                    .followSslRedirects(false)
                    .build();
        }
        return client;
    }
}
