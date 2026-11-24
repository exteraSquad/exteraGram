/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.adblock.backend;

import androidx.annotation.Nullable;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.adblock.interop.AdBlock;
import com.exteragram.messenger.adblock.interop.NativeAdBlock;
import com.exteragram.messenger.utils.network.RemoteUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;

import java.util.ArrayList;

public class AdBlockManager {

    private static final String[] FILTERS = new String[]{
            "https://ublockorigin.github.io/uAssetsCDN/filters/filters.min.txt",
            "https://ublockorigin.github.io/uAssetsCDN/filters/badware.min.txt",
            "https://ublockorigin.github.io/uAssetsCDN/filters/privacy.min.txt",
            "https://ublockorigin.github.io/uAssetsCDN/filters/unbreak.min.txt",
            "https://ublockorigin.github.io/uAssetsCDN/filters/quick-fixes.min.txt",
            "https://filters.adtidy.org/extension/ublock/filters/11.txt",
            "https://filters.adtidy.org/extension/ublock/filters/2_without_easylist.txt",
            "https://cdn.jsdelivr.net/gh/uBlockOrigin/uAssetsCDN@main/thirdparties/easylist.txt",
            "https://cdn.jsdelivr.net/gh/uBlockOrigin/uAssetsCDN@main/thirdparties/easyprivacy.txt",
            "https://cdn.jsdelivr.net/gh/dimisa-RUAdList/RUAdListCDN@main/lists/ruadlist.ubo.min.txt"
    };

    private static final long PRELOAD_DELAY = 10_000;

    private static final ArrayList<Runnable> readyCallbacks = new ArrayList<>();
    private static DispatchQueue queue;
    private static boolean loading;

    private static synchronized DispatchQueue getQueue() {
        if (queue == null) {
            queue = new DispatchQueue("AdBlockManager");
        }
        return queue;
    }

    public static boolean isAvailable() {
        return RemoteUtils.getBooleanConfigValue(RemoteUtils.USE_ADBLOCK, false) && !NativeAdBlock.isLoadFailed();
    }

    public static boolean isActive() {
        return ExteraConfig.getEnableAdBlock() && AdBlock.isReady();
    }

    public static void initialize() {
        if (ExteraConfig.getEnableAdBlock()) {
            getQueue().postRunnable(() -> load(null));
        }
    }

    public static void preload() {
        if (ExteraConfig.getEnableAdBlock()) {
            getQueue().postRunnable(() -> load(null), PRELOAD_DELAY);
        }
    }

    public static void setEnabled(boolean enabled, @Nullable Runnable onReady) {
        ExteraConfig.setEnableAdBlock(enabled);
        getQueue().postRunnable(() -> {
            if (enabled) {
                load(onReady);
            } else {
                readyCallbacks.clear();
                AdBlock.destroy();
            }
        });
    }

    private static void load(Runnable onReady) {
        if (!ExteraConfig.getEnableAdBlock()) {
            return;
        }
        if (AdBlock.isReady()) {
            if (onReady != null) {
                AndroidUtilities.runOnUIThread(onReady);
            }
            return;
        }
        if (!RemoteUtils.getBooleanConfigValue(RemoteUtils.USE_ADBLOCK, false) || !NativeAdBlock.loadLibraries()) {
            return;
        }
        if (onReady != null) {
            readyCallbacks.add(onReady);
        }
        if (loading) {
            return;
        }
        loading = true;

        var scriptlets = ScriptletsManager.getInstance();
        if (!scriptlets.isDownloaded()) {
            scriptlets.download(success -> getQueue().postRunnable(AdBlock::applyResources));
        }

        var subscriptions = SubscriptionsManager.getInstance();
        if (subscriptions.hasFilters()) {
            build();
            subscriptions.update(FILTERS, null);
        } else {
            subscriptions.update(FILTERS, () -> getQueue().postRunnable(AdBlockManager::build));
        }
    }

    private static void build() {
        loading = false;
        if (ExteraConfig.getEnableAdBlock()) {
            AdBlock.build();
        }
        if (readyCallbacks.isEmpty()) {
            return;
        }
        var callbacks = new ArrayList<>(readyCallbacks);
        readyCallbacks.clear();
        if (AdBlock.isReady()) {
            AndroidUtilities.runOnUIThread(() -> {
                for (var callback : callbacks) {
                    callback.run();
                }
            });
        }
    }
}
