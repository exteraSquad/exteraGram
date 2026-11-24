/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player;

import android.text.TextUtils;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.audioinfo.AudioInfo;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AudioInfoCache {

    private static final int MAX_ENTRIES = 3;

    private static final LinkedHashMap<String, AudioInfo> cache = new LinkedHashMap<>(MAX_ENTRIES + 1, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, AudioInfo> eldest) {
            return size() > MAX_ENTRIES;
        }
    };
    private static final HashSet<String> pending = new HashSet<>();

    private AudioInfoCache() {
    }

    public static AudioInfo get(File file) {
        if (file == null) {
            return null;
        }
        final String key = key(file);
        synchronized (cache) {
            final AudioInfo cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        final AudioInfo info = AudioInfo.getAudioInfo(file);
        if (info != null) {
            synchronized (cache) {
                cache.put(key, info);
            }
        }
        return info;
    }

    public static void preload(MessageObject messageObject) {
        if (messageObject == null || !messageObject.isMusic()) {
            return;
        }
        final File file = getFile(messageObject);
        if (!file.exists()) {
            return;
        }
        final String key = key(file);
        synchronized (cache) {
            if (cache.containsKey(key) || !pending.add(key)) {
                return;
            }
        }
        Utilities.globalQueue.postRunnable(() -> {
            final AudioInfo info = AudioInfo.getAudioInfo(file);
            synchronized (cache) {
                pending.remove(key);
                if (info != null) {
                    cache.put(key, info);
                }
            }
        });
    }

    private static File getFile(MessageObject messageObject) {
        final String attachPath = messageObject.messageOwner.attachPath;
        if (!TextUtils.isEmpty(attachPath)) {
            final File file = new File(attachPath);
            if (file.exists()) {
                return file;
            }
        }
        return FileLoader.getInstance(messageObject.currentAccount).getPathToMessage(messageObject.messageOwner);
    }

    private static String key(File file) {
        return file.getAbsolutePath() + ":" + file.length() + ":" + file.lastModified();
    }
}
