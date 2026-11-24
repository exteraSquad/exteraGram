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

import org.json.JSONArray;
import org.json.JSONException;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.Utilities;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;

public class ScriptletsManager {

    private static final String PREFS_NAME = "ublock_scriptlets";
    private static final String KEY_DOWNLOADED = "__downloaded";
    private static final String BASE_URL = "https://raw.githubusercontent.com/gorhill/uBlock/master/src/web_accessible_resources/";
    private static final Map<String, ScriptletInfo> SCRIPTLETS_MAP = new HashMap<String, ScriptletInfo>() {{
        put("1x1.gif", new ScriptletInfo("1x1-transparent.gif"));
        put("2x2.png", new ScriptletInfo("2x2-transparent.png"));
        put("3x2.png", new ScriptletInfo("3x2-transparent.png"));
        put("32x32.png", new ScriptletInfo("32x32-transparent.png"));
        put("amazon_ads.js", new ScriptletInfo("amazon-adsystem.com/aax2/amzn_ads.js"));
        put("amazon_apstag.js", new ScriptletInfo(null));
        put("ampproject_v0.js", new ScriptletInfo("ampproject.org/v0.js"));
        put("chartbeat.js", new ScriptletInfo("static.chartbeat.com/chartbeat.js"));
        put("doubleclick_instream_ad_status.js", new ScriptletInfo("doubleclick.net/instream/ad_status.js"));
        put("empty", new ScriptletInfo(null));
        put("fingerprint2.js", new ScriptletInfo(null));
        put("fingerprint3.js", new ScriptletInfo(null));
        put("google-analytics_analytics.js", new ScriptletInfo(new String[]{
                "google-analytics.com/analytics.js",
                "googletagmanager_gtm.js",
                "googletagmanager.com/gtm.js"
        }));
        put("google-analytics_cx_api.js", new ScriptletInfo("google-analytics.com/cx/api.js"));
        put("google-analytics_ga.js", new ScriptletInfo("google-analytics.com/ga.js"));
        put("google-analytics_inpage_linkid.js", new ScriptletInfo("google-analytics.com/inpage_linkid.js"));
        put("google-ima.js", new ScriptletInfo("google-ima3"));
        put("googlesyndication_adsbygoogle.js", new ScriptletInfo(new String[]{
                "googlesyndication.com/adsbygoogle.js",
                "googlesyndication-adsbygoogle"
        }));
        put("googletagservices_gpt.js", new ScriptletInfo(new String[]{
                "googletagservices.com/gpt.js",
                "googletagservices-gpt"
        }));
        put("hd-main.js", new ScriptletInfo(null));
        put("nobab2.js", new ScriptletInfo(null));
        put("noeval.js", new ScriptletInfo(null));
        put("noeval-silent.js", new ScriptletInfo("silent-noeval.js"));
        put("nofab.js", new ScriptletInfo("fuckadblock.js-3.2.0"));
        put("noop-0.1s.mp3", new ScriptletInfo(new String[]{"noopmp3-0.1s", "abp-resource:blank-mp3"}));
        put("noop-0.5s.mp3", new ScriptletInfo(null));
        put("noop-1s.mp4", new ScriptletInfo(new String[]{"noopmp4-1s", "abp-resource:blank-mp4"}));
        put("noop.css", new ScriptletInfo(null));
        put("noop.html", new ScriptletInfo("noopframe"));
        put("noop.js", new ScriptletInfo(new String[]{"noopjs", "abp-resource:blank-js"}));
        put("noop.json", new ScriptletInfo(new String[]{"noopjson"}));
        put("noop.txt", new ScriptletInfo("nooptext"));
        put("noop-vast2.xml", new ScriptletInfo("noopvast-2.0"));
        put("noop-vast3.xml", new ScriptletInfo("noopvast-3.0"));
        put("noop-vast4.xml", new ScriptletInfo("noopvast-4.0"));
        put("noop-vmap1.xml", new ScriptletInfo(new String[]{"noop-vmap1.0.xml", "noopvmap-1.0"}));
        put("outbrain-widget.js", new ScriptletInfo("widgets.outbrain.com/outbrain.js"));
        put("popads.js", new ScriptletInfo(new String[]{"popads.net.js", "prevent-popads-net.js"}));
        put("popads-dummy.js", new ScriptletInfo(null));
        put("prebid-ads.js", new ScriptletInfo(null));
        put("scorecardresearch_beacon.js", new ScriptletInfo("scorecardresearch.com/beacon.js"));
        put("sensors-analytics.js", new ScriptletInfo(null));
        put("nitropay_ads.js", new ScriptletInfo(null));
    }};
    private static ScriptletsManager instance;
    private final DispatchQueue queue;
    private final OkHttpClient client;
    private final SharedPreferences prefs;
    private final Object lock = new Object();

    public ScriptletsManager() {
        this.queue = new DispatchQueue("ScriptletsManager");
        this.client = ExteraHttpClient.INSTANCE.getClient();
        this.prefs = PreferencesUtils.getPreferences(PREFS_NAME);
    }

    public static ScriptletsManager getInstance() {
        if (instance == null) {
            instance = new ScriptletsManager();
        }
        return instance;
    }

    public static String getExtension(String name) {
        if (name.endsWith(".css")) {
            return ".css";
        }
        if (name.endsWith(".gif")) {
            return ".gif";
        }
        if (name.endsWith(".html")) {
            return ".html";
        }
        if (name.endsWith(".js")) {
            return ".js";
        }
        if (name.endsWith(".json")) {
            return ".json";
        }
        if (name.endsWith(".mp3")) {
            return ".mp3";
        }
        if (name.endsWith(".mp4")) {
            return ".mp4";
        }
        if (name.endsWith(".png")) {
            return ".png";
        }
        if (name.endsWith(".txt")) {
            return ".txt";
        }
        if (name.endsWith(".xml")) {
            return ".xml";
        }
        return ".txt";
    }

    public void download(@Nullable Utilities.Callback<Boolean> callback) {
        queue.postRunnable(() -> {
            boolean success = true;
            for (var entry : SCRIPTLETS_MAP.entrySet()) {
                if (!downloadScriptlet(entry.getKey(), entry.getValue())) {
                    success = false;
                    break;
                }
            }
            if (success) {
                synchronized (lock) {
                    prefs.edit().putBoolean(KEY_DOWNLOADED, true).apply();
                }
            }
            if (callback != null) {
                callback.run(success);
            }
        });
    }

    private boolean downloadScriptlet(String filename, ScriptletInfo info) {
        synchronized (lock) {
            if (prefs.contains(filename)) {
                return true;
            }
        }
        var request = new Request.Builder().url(BASE_URL + filename).header("User-Agent", SubscriptionsManager.USER_AGENT).build();
        try (var response = client.newCall(request).execute()) {
            if (response.code() == 404) {
                return true;
            }
            if (!response.isSuccessful()) {
                return false;
            }
            var content = response.body().bytes();
            var base64Content = Base64.encodeToString(content, Base64.NO_WRAP);

            synchronized (lock) {
                var editor = prefs.edit();
                editor.putString(filename, base64Content);

                if (info.alias != null) {
                    var aliases = new JSONArray();
                    if (info.alias instanceof String) {
                        aliases.put(info.alias);
                    } else if (info.alias instanceof String[]) {
                        for (var alias : (String[]) info.alias) {
                            aliases.put(alias);
                        }
                    }
                    editor.putString(filename + "_aliases", aliases.toString());
                }
                editor.apply();
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean isDownloaded() {
        synchronized (lock) {
            return prefs.getBoolean(KEY_DOWNLOADED, false);
        }
    }

    @NonNull
    public Collection<Scriptlet> iterScriptlets() {
        List<Scriptlet> scriptlets = new ArrayList<>();

        synchronized (lock) {
            for (var entry : SCRIPTLETS_MAP.entrySet()) {
                var filename = entry.getKey();
                var content = prefs.getString(filename, null);

                if (content != null) {
                    var aliases = new ArrayList<String>();
                    var aliasesJson = prefs.getString(filename + "_aliases", null);

                    if (aliasesJson != null) {
                        try {
                            var aliasArray = new JSONArray(aliasesJson);
                            for (int i = 0; i < aliasArray.length(); i++) {
                                aliases.add(aliasArray.getString(i));
                            }
                        } catch (JSONException e) {
                        }
                    }

                    scriptlets.add(new Scriptlet(filename, aliases, content));
                }
            }
        }

        return Collections.unmodifiableList(scriptlets);
    }

    public static class Scriptlet {
        public final String filename;
        public final List<String> aliases;
        public final String content;

        private Scriptlet(String filename, List<String> aliases, String content) {
            this.filename = filename;
            this.aliases = aliases;
            this.content = content;
        }
    }

    private static class ScriptletInfo {
        final Object alias;  // String or String[]

        ScriptletInfo(Object alias) {
            this.alias = alias;
        }
    }
}
