/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.adblock.interop;

import com.exteragram.messenger.adblock.backend.ScriptletsManager;
import com.exteragram.messenger.adblock.backend.SubscriptionsManager;
import com.exteragram.messenger.adblock.data.BlockResult;
import com.exteragram.messenger.adblock.data.UrlCosmeticResources;

import java.util.Collection;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class AdBlock {
    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static volatile long enginePtr = 0;

    public static boolean isReady() {
        return enginePtr != 0;
    }

    public static void build() {
        var subscriptionFiles = SubscriptionsManager.getInstance().getSubscriptionFilePaths();
        if (subscriptionFiles.isEmpty()) {
            return;
        }

        var filterSetPtr = NativeAdBlock.createFilterSet(new String[]{});
        for (var filePath : subscriptionFiles) {
            NativeAdBlock.addFilters(filterSetPtr, filePath);
        }

        var engine = NativeAdBlock.createEngine(filterSetPtr);
        useResources(engine, ScriptletsManager.getInstance().iterScriptlets());
        swap(engine);
    }

    public static void destroy() {
        swap(0);
    }

    public static void applyResources() {
        var scriptlets = ScriptletsManager.getInstance().iterScriptlets();
        lock.writeLock().lock();
        try {
            if (enginePtr != 0) {
                useResources(enginePtr, scriptlets);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private static void swap(long engine) {
        long oldEngine;
        lock.writeLock().lock();
        try {
            oldEngine = enginePtr;
            enginePtr = engine;
        } finally {
            lock.writeLock().unlock();
        }
        if (oldEngine != 0) {
            NativeAdBlock.destroyEngine(oldEngine);
        }
    }

    private static void useResources(long engine, Collection<ScriptletsManager.Scriptlet> scriptlets) {
        var count = scriptlets.size();
        var names = new String[count];
        var aliases = new String[count][];
        var extensions = new String[count];
        var contents = new String[count];

        int i = 0;
        for (var scriptlet : scriptlets) {
            names[i] = scriptlet.filename;
            aliases[i] = scriptlet.aliases != null ? scriptlet.aliases.toArray(new String[0]) : new String[0];
            extensions[i] = ScriptletsManager.getExtension(scriptlet.filename);
            contents[i] = scriptlet.content;
            i++;
        }

        NativeAdBlock.useResources(engine, names, aliases, extensions, contents);
    }

    public static BlockResult getBlockResult(String url, String sourceUrl, String resourceType) {
        lock.readLock().lock();
        try {
            return enginePtr != 0 ? NativeAdBlock.shouldBlock(enginePtr, url, sourceUrl, resourceType) : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public static UrlCosmeticResources getCosmeticResources(String url) {
        lock.writeLock().lock();
        try {
            return enginePtr != 0 ? NativeAdBlock.getCosmeticResources(enginePtr, url) : null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static String[] getHiddenSelectors(String[] classes, String[] ids, String[] exceptions) {
        lock.writeLock().lock();
        try {
            return enginePtr != 0 ? NativeAdBlock.getHiddenSelectors(enginePtr, classes, ids, exceptions) : null;
        } finally {
            lock.writeLock().unlock();
        }
    }
}
