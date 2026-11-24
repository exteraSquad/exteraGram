package org.telegram.messenger;

import android.os.SystemClock;

import org.telegram.tgnet.ConnectionsManager;

public class ProxyPingController {
    private static final ProxyPingController INSTANCE = new ProxyPingController();

    private final Runnable pingRunnable = () -> {
        if (SharedConfig.isProxyEnabled() && SharedConfig.currentProxy != null) {
            var info = SharedConfig.currentProxy;
            int currentAccount = UserConfig.selectedAccount;
            int ping = ConnectionsManager.native_getCurrentPingTime(currentAccount);
            if (ping > 0) {
                info.ping = ping;
                info.availableCheckTime = SystemClock.elapsedRealtime();
                info.available = true;
            } else {
                info.ping = 0;
                info.available = false;
            }
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyPingUpdated, ping);
        }
        scheduleNextPing();
    };

    private void scheduleNextPing() {
        AndroidUtilities.cancelRunOnUIThread(pingRunnable);
        AndroidUtilities.runOnUIThread(pingRunnable, 10000);
    }

    public static void init() {
        INSTANCE.scheduleNextPing();
    }
}
