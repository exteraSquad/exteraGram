/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player.motion;

import android.graphics.Bitmap;
import android.provider.MediaStore;

import com.exteragram.messenger.player.PlayerCache;
import com.exteragram.messenger.player.TrackQuery;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

import okhttp3.Request;
import okhttp3.Response;

final class CanvasSource {

    interface Resolver {
        String resolve(TrackQuery query) throws Exception;
    }

    static final class Unavailable extends Exception {
    }

    record Reply(int code, byte[] body) {

        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private static final int LOCK_STRIPES = 16;
    private static final Object[] locks = new Object[LOCK_STRIPES];

    static {
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
    }

    private static final class Queues {
        static final DispatchQueue load = new DispatchQueue("playerCanvas");
        static final DispatchQueue prefetch = new DispatchQueue("playerCanvasPrefetch");
    }

    private final String directory;
    private final int version;
    private final Resolver resolver;
    private volatile Set<String> wantedKeys = Collections.emptySet();

    CanvasSource(String directory, int version, Resolver resolver) {
        this.directory = directory;
        this.version = version;
        this.resolver = resolver;
    }

    void setWantedKeys(Collection<String> keys) {
        wantedKeys = new HashSet<>(keys);
    }

    void fetch(TrackQuery query, boolean priority, CanvasRepository.Callback callback) {
        final String key = query.key();
        Utilities.globalQueue.postRunnable(() -> {
            if (!wantedKeys.contains(key)) {
                return;
            }
            final File dir = PlayerCache.directory(directory);
            if (dir == null) {
                AndroidUtilities.runOnUIThread(() -> callback.onResult(null));
                return;
            }
            final String name = Utilities.MD5(key + "\u0001" + version);
            final String cached = cached(dir, name);
            if (cached != null) {
                if (!cached.isEmpty()) {
                    final File thumb = new File(thumbPath(cached));
                    if (!thumb.exists()) {
                        Queues.prefetch.postRunnable(() -> createThumb(cached, thumb));
                    }
                }
                AndroidUtilities.runOnUIThread(() -> callback.onResult(cached.isEmpty() ? null : cached));
                return;
            }
            (priority ? Queues.load : Queues.prefetch).postRunnable(() -> {
                if (!wantedKeys.contains(key)) {
                    return;
                }
                String path = null;
                try {
                    synchronized (locks[((name.hashCode() ^ directory.hashCode()) & 0x7FFFFFFF) % LOCK_STRIPES]) {
                        path = load(query, dir, name);
                    }
                } catch (CancellationException e) {
                    return;
                } catch (Unavailable ignore) {
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                final String result = path;
                AndroidUtilities.runOnUIThread(() -> callback.onResult(result));
            });
        });
    }

    private String load(TrackQuery query, File dir, String name) throws Exception {
        final String cached = cached(dir, name);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }
        final File video = new File(dir, name + MotionCoverRepository.VIDEO_EXT);
        final String url = resolver.resolve(query);
        if (url == null) {
            new FileOutputStream(new File(dir, name + MotionCoverRepository.MISSING_EXT)).close();
        } else {
            download(url, video, () -> !wantedKeys.contains(query.key()));
            createThumb(video.getAbsolutePath(), new File(thumbPath(video.getAbsolutePath())));
        }
        MotionCoverRepository.trim(dir);
        return url != null ? video.getAbsolutePath() : null;
    }

    private static String cached(File dir, String name) {
        final File video = new File(dir, name + MotionCoverRepository.VIDEO_EXT);
        if (video.length() > 0) {
            if (!video.setLastModified(System.currentTimeMillis())) {
                FileLog.d("canvas: can't touch " + video);
            }
            return video.getAbsolutePath();
        }
        final File marker = new File(dir, name + MotionCoverRepository.MISSING_EXT);
        return System.currentTimeMillis() - marker.lastModified() < MotionCoverRepository.MISSING_TTL_MS ? "" : null;
    }

    private static void download(String url, File target, BooleanSupplier abort) throws Exception {
        final File temp = new File(target.getPath() + ".tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) {
                MotionCoverRepository.copy(new MotionCoverRepository.Part(url, 0, -1), out, 0, abort);
            }
            if (!temp.renameTo(target)) {
                throw new IllegalStateException("can't move " + temp);
            }
        } catch (Exception e) {
            MotionCoverRepository.delete(temp);
            throw e;
        }
    }

    private static void createThumb(String videoPath, File thumb) {
        final Bitmap frame = SendMessagesHelper.createVideoThumbnail(videoPath, MediaStore.Video.Thumbnails.FULL_SCREEN_KIND);
        if (frame == null) {
            return;
        }
        final File temp = new File(thumb.getPath() + ".tmp");
        try {
            try (FileOutputStream out = new FileOutputStream(temp)) {
                frame.compress(Bitmap.CompressFormat.JPEG, 90, out);
            }
            if (!temp.renameTo(thumb)) {
                MotionCoverRepository.delete(temp);
            }
        } catch (Exception e) {
            FileLog.e(e);
            MotionCoverRepository.delete(temp);
        } finally {
            frame.recycle();
        }
    }

    boolean owns(String path) {
        final File parent = new File(path).getParentFile();
        return parent != null && directory.equals(parent.getName());
    }

    static void post(Runnable runnable) {
        Queues.load.postRunnable(runnable);
    }

    static String thumbPath(String videoPath) {
        return videoPath.substring(0, videoPath.length() - MotionCoverRepository.VIDEO_EXT.length()) + MotionCoverRepository.THUMB_EXT;
    }

    static Reply execute(Request request) throws Exception {
        try (Response response = MotionCoverRepository.client().newCall(request).execute()) {
            return new Reply(response.code(), response.body().bytes());
        }
    }
}
