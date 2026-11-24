/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.badges;

import android.content.SharedPreferences;

import com.exteragram.messenger.utils.network.RemoteUtils;

import org.telegram.messenger.FileLog;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class CachedRemoteSet {
    private final String remoteKey;
    private final Set<Long> defaultSet;
    private volatile Set<Long> cachedValues;
    private volatile boolean listenerInitialized = false;
    /**
     * @noinspection FieldCanBeLocal
     */
    private SharedPreferences.OnSharedPreferenceChangeListener changeListener;

    public CachedRemoteSet(String remoteKey, Set<Long> defaultSet) {
        this.remoteKey = remoteKey;
        this.defaultSet = Set.copyOf(defaultSet);
        initializeListener();
    }

    private void initializeListener() {
        if (!listenerInitialized) {
            RemoteUtils.initCached();
            if (RemoteUtils.sharedPreferences != null) {
                changeListener = (prefs, key) -> {
                    if (this.remoteKey.equals(key)) {
                        cachedValues = null;
                    }
                };
                RemoteUtils.sharedPreferences.registerOnSharedPreferenceChangeListener(changeListener);
                listenerInitialized = true;
            }
        }
    }

    private Set<Long> getSet() {
        if (!listenerInitialized) {
            initializeListener();
        }

        Set<Long> currentSet = cachedValues;
        if (currentSet == null) {
            SharedPreferences remotePrefs = RemoteUtils.sharedPreferences;
            Set<Long> resultSet;

            if (remotePrefs != null && remotePrefs.contains(remoteKey)) {
                Set<String> sourceSet = remotePrefs.getStringSet(remoteKey, Collections.emptySet());
                resultSet = new HashSet<>(sourceSet.size());
                for (String s : sourceSet) {
                    try {
                        resultSet.add(Long.parseLong(s));
                    } catch (NumberFormatException e) {
                        FileLog.e("Failed to parse long from remote config for key " + remoteKey + ": " + s, e);
                    }
                }
            } else {
                resultSet = this.defaultSet;
            }

            Set<Long> concurrentSet = ConcurrentHashMap.newKeySet(resultSet.size());
            concurrentSet.addAll(resultSet);

            cachedValues = concurrentSet;
            currentSet = concurrentSet;
        }

        return Collections.unmodifiableSet(currentSet);
    }

    public boolean contains(long id) {
        return getSet().contains(id);
    }
}
