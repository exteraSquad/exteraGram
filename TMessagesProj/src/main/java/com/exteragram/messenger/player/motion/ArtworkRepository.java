/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player.motion;

import android.text.TextUtils;
import android.util.LruCache;

import com.exteragram.messenger.player.PlayerCache;
import com.exteragram.messenger.player.TrackQuery;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;

public final class ArtworkRepository {

    public interface Callback {
        void onResult(String url);
    }

    private static final String CACHE_DIR = "artwork";
    private static final String URL_EXT = ".url";
    private static final String MISSING_EXT = ".none";
    private static final int CACHE_VERSION = 1;
    private static final int ARTWORK_SIZE = 1200;
    private static final int MAX_URL_BYTES = 4096;
    private static final long MISSING_TTL_MS = 3L * 24 * 60 * 60 * 1000;
    private static final String NONE = "";

    private static final LruCache<String, String> memory = new LruCache<>(128);
    private static final HashMap<String, ArrayList<Callback>> pending = new HashMap<>();

    private ArtworkRepository() {
    }

    public static String getCached(TrackQuery query) {
        if (query == null) {
            return null;
        }
        final String url = memory.get(query.key());
        return TextUtils.isEmpty(url) ? null : url;
    }

    public static boolean isResolved(TrackQuery query) {
        return query == null || memory.get(query.key()) != null;
    }

    public static void fetch(TrackQuery query, Callback callback) {
        if (query == null) {
            return;
        }
        final String key = query.key();
        final String cached = memory.get(key);
        if (cached != null) {
            callback.onResult(cached.isEmpty() ? null : cached);
            return;
        }
        ArrayList<Callback> callbacks = pending.get(key);
        if (callbacks != null) {
            callbacks.add(callback);
            return;
        }
        callbacks = new ArrayList<>();
        callbacks.add(callback);
        pending.put(key, callbacks);
        MotionCoverRepository.queue.postRunnable(() -> {
            String url = null;
            boolean resolved = false;
            try {
                url = load(query, key);
                resolved = true;
            } catch (Throwable e) {
                FileLog.e(e);
            }
            final String result = url;
            final boolean known = resolved;
            AndroidUtilities.runOnUIThread(() -> {
                if (known) {
                    memory.put(key, result != null ? result : NONE);
                }
                final ArrayList<Callback> list = pending.remove(key);
                if (list != null) {
                    for (Callback c : list) {
                        c.onResult(result);
                    }
                }
            });
        });
    }

    private static String load(TrackQuery query, String key) throws Exception {
        final File dir = PlayerCache.directory(CACHE_DIR);
        final String name = Utilities.MD5(key + "\u0001" + CACHE_VERSION);
        final File file = dir != null ? new File(dir, name + URL_EXT) : null;
        final File marker = dir != null ? new File(dir, name + MISSING_EXT) : null;
        if (file != null && file.length() > 0) {
            final String url = read(file);
            if (!TextUtils.isEmpty(url)) {
                return url;
            }
        }
        if (marker != null && System.currentTimeMillis() - marker.lastModified() < MISSING_TTL_MS) {
            return null;
        }
        final String url = find(query);
        if (file != null && url != null) {
            try (OutputStream out = new FileOutputStream(file)) {
                out.write(url.getBytes(StandardCharsets.UTF_8));
            }
        } else if (marker != null) {
            new FileOutputStream(marker).close();
        }
        return url;
    }

    private static String find(TrackQuery query) throws Exception {
        for (JSONObject song : MotionCoverMatcher.bestSongs(MotionCoverRepository.searchSongs(query), query.artist(), query.title(), query.duration())) {
            final JSONObject artwork = song.optJSONObject("artwork");
            final String template = artwork != null ? artwork.optString("url") : "";
            if (!template.startsWith("https://")) {
                continue;
            }
            final int side = Math.max(1, Math.min(ARTWORK_SIZE, Math.min(artwork.optInt("width", ARTWORK_SIZE), artwork.optInt("height", ARTWORK_SIZE))));
            return template.replace("{w}", String.valueOf(side)).replace("{h}", String.valueOf(side)).replace("{f}", "jpg");
        }
        return null;
    }

    private static String read(File file) throws Exception {
        final byte[] buffer = new byte[(int) Math.min(file.length(), MAX_URL_BYTES)];
        int read = 0;
        try (InputStream in = new FileInputStream(file)) {
            while (read < buffer.length) {
                final int n = in.read(buffer, read, buffer.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
        }
        final String url = new String(buffer, 0, read, StandardCharsets.UTF_8).trim();
        return url.startsWith("https://") ? url : null;
    }
}
