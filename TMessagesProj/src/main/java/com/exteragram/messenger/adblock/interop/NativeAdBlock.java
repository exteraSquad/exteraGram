/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.adblock.interop;

import com.exteragram.messenger.adblock.data.BlockResult;
import com.exteragram.messenger.adblock.data.FilterListMetadata;
import com.exteragram.messenger.adblock.data.UrlCosmeticResources;

import org.telegram.messenger.FileLog;

public class NativeAdBlock {
    private static volatile Boolean loaded;

    public static synchronized boolean loadLibraries() {
        if (loaded == null) {
            try {
                System.loadLibrary("etgadblock");
                loaded = true;
            } catch (Throwable e) {
                FileLog.e(e);
                loaded = false;
            }
        }
        return loaded;
    }

    public static boolean isLoadFailed() {
        return Boolean.FALSE.equals(loaded);
    }

    public static native long createFilterSet(String[] filters);

    public static native FilterListMetadata addFilters(long filterSetPtr, String filePath);

    public static native void destroyFilterSet(long filterSetPtr);

    public static native void useResources(long enginePtr, String[] names, String[][] aliases, String[] extensions, String[] contents);

    public static native long createEngine(long filterSetPtr);

    public static native void destroyEngine(long enginePtr);

    public static native UrlCosmeticResources getCosmeticResources(long enginePtr, String url);

    public static native String[] getHiddenSelectors(long enginePtr, String[] classes, String[] ids, String[] exceptions);

    public static native BlockResult shouldBlock(long enginePtr, String url, String sourceUrl, String resourceType);
}
