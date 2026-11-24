/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.adblock.backend;

import android.content.SharedPreferences;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.exteragram.messenger.backup.PreferencesUtils;
import com.exteragram.messenger.utils.network.ExteraHttpClient;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DispatchQueue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;

public class SubscriptionsManager {

    public static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15";
    private static final String PREFS_NAME = "ublock_subscriptions";
    private static final String KEY_METADATA = "metadata_";
    private static final String KEY_REDIRECT = "redirect_";
    private static final String KEY_LEGACY_CONTENT = "content_";
    private static final int MAX_REDIRECTS = 3;
    private static final Pattern redirectPattern = Pattern.compile("!\\s*Redirect:\\s*(\\S+)");
    private static SubscriptionsManager instance;
    private final DispatchQueue queue;
    private final OkHttpClient client;
    private final SharedPreferences prefs;
    private final Object lock = new Object();

    public SubscriptionsManager() {
        this.queue = new DispatchQueue("SubscriptionsManager");
        this.client = ExteraHttpClient.INSTANCE.getClient();
        this.prefs = PreferencesUtils.getPreferences(PREFS_NAME);
    }

    public static SubscriptionsManager getInstance() {
        if (instance == null) {
            instance = new SubscriptionsManager();
        }
        return instance;
    }

    private File getFileForUrl(String url) {
        var dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "adblock");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        var name = Base64.encodeToString(url.getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP);
        return new File(dir, name + ".txt");
    }

    public void update(@NonNull String[] defaultUrls, @Nullable Runnable onComplete) {
        queue.postRunnable(() -> {
            migrateLegacyContent();
            var urls = new LinkedHashSet<String>();
            long now = System.currentTimeMillis();
            for (var metadata : getSubscriptions()) {
                if (now >= metadata.expires || !getFileForUrl(metadata.url).exists()) {
                    urls.add(metadata.url);
                }
            }
            synchronized (lock) {
                for (var url : defaultUrls) {
                    if (!prefs.contains(KEY_METADATA + url) && !prefs.contains(KEY_REDIRECT + url)) {
                        urls.add(url);
                    }
                }
            }
            for (var url : urls) {
                fetchSubscription(url, 0);
            }
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    private void migrateLegacyContent() {
        var values = prefs.getAll();
        SharedPreferences.Editor editor = null;
        for (var entry : values.entrySet()) {
            var key = entry.getKey();
            if (!key.startsWith(KEY_LEGACY_CONTENT)) {
                continue;
            }
            var url = key.substring(KEY_LEGACY_CONTENT.length());
            if (values.containsKey(KEY_METADATA + url) && !values.containsKey(KEY_REDIRECT + url)
                    && entry.getValue() instanceof String content) {
                var file = getFileForUrl(url);
                if (!file.isFile() && !writeFilterFile(file, content)) {
                    continue;
                }
            }
            if (editor == null) {
                editor = prefs.edit();
            }
            editor.remove(key);
        }
        if (editor != null) {
            editor.apply();
        }
    }

    private boolean writeFilterFile(File file, String content) {
        var tempFile = new File(file.getPath() + ".tmp");
        try (var fos = new FileOutputStream(tempFile)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            tempFile.delete();
            return false;
        }
        if (!tempFile.renameTo(file)) {
            tempFile.delete();
            return false;
        }
        return true;
    }

    public boolean hasFilters() {
        return !getSubscriptionFilePaths().isEmpty();
    }

    private void unsubscribe(String url) {
        synchronized (lock) {
            var editor = prefs.edit();
            editor.remove(KEY_METADATA + url);
            editor.apply();
        }
        var file = getFileForUrl(url);
        if (file.exists()) {
            file.delete();
        }
    }

    @NonNull
    public List<FilterMetadata> getSubscriptions() {
        List<FilterMetadata> result = new ArrayList<>();
        synchronized (lock) {
            var all = prefs.getAll();
            for (var key : all.keySet()) {
                if (key.startsWith(KEY_METADATA)) {
                    try {
                        var json = prefs.getString(key, null);
                        if (json != null) {
                            result.add(FilterMetadata.fromJson(new JSONObject(json)));
                        }
                    } catch (JSONException e) {
                    }
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    @NonNull
    public List<String> getSubscriptionFilePaths() {
        var paths = new ArrayList<String>();
        var subscriptions = getSubscriptions();
        for (var metadata : subscriptions) {
            var file = getFileForUrl(metadata.url);
            if (file.exists()) {
                paths.add(file.getAbsolutePath());
            }
        }
        return paths;
    }

    private boolean fetchSubscription(String url, int redirects) {
        try {
            var request = new Request.Builder().url(url).header("User-Agent", USER_AGENT).build();
            try (var response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return false;
                }

                var content = response.body().string();

                var redirectUrl = extractRedirect(content);
                if (redirectUrl != null) {
                    if (redirects >= MAX_REDIRECTS || !fetchSubscription(redirectUrl, redirects + 1)) {
                        return false;
                    }
                    unsubscribe(url);
                    synchronized (lock) {
                        prefs.edit().putString(KEY_REDIRECT + url, redirectUrl).apply();
                    }
                    return true;
                }

                var metadata = parseMetadata(url, content);

                if (!writeFilterFile(getFileForUrl(url), content)) {
                    return false;
                }

                synchronized (lock) {
                    var editor = prefs.edit();
                    editor.putString(KEY_METADATA + url, metadata.toJson().toString());
                    editor.apply();
                }
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private String extractRedirect(String content) {
        var matcher = redirectPattern.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    private FilterMetadata parseMetadata(String url, String content) {
        var title = extractMetadataValue(content, "Title");
        var homepage = extractMetadataValue(content, "Homepage");
        var rulesCount = countRules(content);
        var expires = calculateExpiration(content);

        return new FilterMetadata(
                url,
                title != null ? title : "Unnamed list",
                homepage != null ? homepage : "",
                rulesCount,
                expires
        );
    }

    private String extractMetadataValue(String content, String key) {
        var pattern = Pattern.compile("!\\s*" + key + ":\\s*(.+)");
        var matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private int countRules(String content) {
        var count = 0;
        var lines = content.split("\n");
        for (var line : lines) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("!")) {
                count++;
            }
        }
        return count;
    }

    private long calculateExpiration(String content) {
        var expires = extractMetadataValue(content, "Expires");
        if (expires == null) {
            return System.currentTimeMillis() + TimeUnit.DAYS.toMillis(5);
        }

        try {
            var value = Integer.parseInt(expires.replaceAll("[^0-9]", ""));
            if (expires.contains("hour")) {
                return System.currentTimeMillis() + TimeUnit.HOURS.toMillis(value);
            } else if (expires.contains("day")) {
                return System.currentTimeMillis() + TimeUnit.DAYS.toMillis(value);
            } else {
                return System.currentTimeMillis() + TimeUnit.DAYS.toMillis(5);
            }
        } catch (NumberFormatException e) {
            return System.currentTimeMillis() + TimeUnit.DAYS.toMillis(5);
        }
    }

    public static class FilterMetadata {
        public final String url;
        public final String title;
        public final String homepage;
        public final int rulesCount;
        public final long expires;

        private FilterMetadata(String url, String title, String homepage,
                               int rulesCount, long expires) {
            this.url = url;
            this.title = title;
            this.homepage = homepage;
            this.rulesCount = rulesCount;
            this.expires = expires;
        }

        private static FilterMetadata fromJson(JSONObject json) throws JSONException {
            return new FilterMetadata(
                    json.getString("url"),
                    json.getString("title"),
                    json.getString("homepage"),
                    json.getInt("rulesCount"),
                    json.getLong("expires")
            );
        }

        private JSONObject toJson() throws JSONException {
            var json = new JSONObject();
            json.put("url", url);
            json.put("title", title);
            json.put("homepage", homepage);
            json.put("rulesCount", rulesCount);
            json.put("expires", expires);
            return json;
        }
    }
}
