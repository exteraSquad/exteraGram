/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.work.Configuration.Builder;
import androidx.work.Configuration.Provider;

import com.exteragram.messenger.appicons.AppIconController;
import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.ProxyDisableCondition;
import com.exteragram.messenger.debug.FreezeMonitor;
import com.exteragram.messenger.debug.HeapMonitor;
import com.exteragram.messenger.debug.LoadMonitor;
import com.exteragram.messenger.icons.IconManager;
import com.exteragram.messenger.plugins.utils.NativeCrashHandler;
import com.exteragram.messenger.plugins.PluginsController;
import com.exteragram.messenger.security.SensitiveRequestConfirmation;
import com.exteragram.messenger.security.SensitiveRequestSwitch;
import com.exteragram.messenger.updater.UpdateAppAlertDialog;
import com.exteragram.messenger.updater.UpdateLayout;
import com.exteragram.messenger.updater.UpdaterUtils;
import com.exteragram.messenger.utils.network.RemoteUtils;
import com.exteragram.messenger.utils.system.AppProcess;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import org.json.JSONObject;
import org.telegram.messenger.utils.Choreographer60FpsContent;
import org.telegram.messenger.voip.VideoCapturerDevice;
import org.telegram.utils.proxy.ProxySettings;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.ForegroundDetector;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.IUpdateLayout;

import java.io.File;
import java.util.Locale;

public class ApplicationLoader extends Application implements Provider {

    public static ApplicationLoader applicationLoaderInstance;

    @SuppressLint("StaticFieldLeak")
    public static volatile Context applicationContext;
    public static volatile NetworkInfo currentNetworkInfo;
    public static volatile Handler applicationHandler;

    private static ConnectivityManager connectivityManager;
    private static volatile boolean applicationInited = false;
    private static volatile  ConnectivityManager.NetworkCallback networkCallback;
    private static long lastNetworkCheckTypeTime;
    private static int lastKnownNetworkType = -1;
    private static final String PREF_PROXY_AUTO_DISABLED = "proxy_auto_disabled_by_vpn";
    private static volatile boolean exteraHookEmbeddedConfigured = false;

    public static long startTime;

    public static volatile boolean isScreenOn = false;
    public static volatile boolean mainInterfacePaused = true;
    public static volatile boolean mainInterfaceStopped = true;
    public static volatile boolean externalInterfacePaused = true;
    public static volatile boolean mainInterfacePausedStageQueue = true;
    public static boolean canDrawOverlays;
    public static volatile long mainInterfacePausedStageQueueTime;

    private static PushListenerController.IPushListenerServiceProvider pushProvider;
    private static IMapsProvider mapsProvider;
    private static ILocationServiceProvider locationServiceProvider;

    public static boolean isAndroidTestEnvironment() {
        return false;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }

    public static ILocationServiceProvider getLocationServiceProvider() {
        if (locationServiceProvider == null) {
            locationServiceProvider = applicationLoaderInstance.onCreateLocationServiceProvider();
            locationServiceProvider.init(applicationContext);
        }
        return locationServiceProvider;
    }

    protected ILocationServiceProvider onCreateLocationServiceProvider() {
        return new GoogleLocationProvider();
    }

    public static IMapsProvider getMapsProvider() {
        if (mapsProvider == null) {
            mapsProvider = applicationLoaderInstance.onCreateMapsProvider();
        }
        return mapsProvider;
    }

    protected IMapsProvider onCreateMapsProvider() {
        return new GoogleMapsProvider();
    }

    public static PushListenerController.IPushListenerServiceProvider getPushProvider() {
        if (pushProvider == null) {
            pushProvider = applicationLoaderInstance.onCreatePushProvider();
        }
        return pushProvider;
    }

    protected PushListenerController.IPushListenerServiceProvider onCreatePushProvider() {
        return PushListenerController.GooglePushListenerServiceProvider.INSTANCE;
    }

    public static String getApplicationId() {
        return BuildConfig.APPLICATION_ID;
    }

    protected boolean isStandalone() {
        return false;
    }

    protected boolean isBeta() {
        return false;
    }

    protected boolean isAndroidTestEnv() {
        return false;
    }

    public static File getFilesDirFixed() {
        for (int a = 0; a < 10; a++) {
            File path = ApplicationLoader.applicationContext.getFilesDir();
            if (path != null) {
                return path;
            }
        }
        try {
            ApplicationInfo info = applicationContext.getApplicationInfo();
            File path = new File(info.dataDir, "files");
            path.mkdirs();
            return path;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new File("/data/data/com.exteragram.messenger/files");
    }

    public static File getFilesDirFixed(String child) {
        try {
            File path = getFilesDirFixed();
            File dir = new File(path, child);
            dir.mkdirs();

            return dir;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public static void postInitApplication() {
        if (applicationInited || applicationContext == null) {
            return;
        }
        applicationInited = true;
        NativeLoader.initNativeLibs(ApplicationLoader.applicationContext);

        try {
            LocaleController.getInstance(); //TODO improve
        } catch (Exception e) {
            e.printStackTrace();
        }

        IconManager.INSTANCE.initialize(false);

        try {
            connectivityManager = (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            BroadcastReceiver networkStateReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try {
                        currentNetworkInfo = connectivityManager.getActiveNetworkInfo();
                    } catch (Throwable ignore) {

                    }
                    checkProxyForNetworkState();

                    boolean isSlow = isConnectionSlow();
                    for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                        ConnectionsManager.getInstance(a).checkConnection();
                        FileLoader.getInstance(a).onNetworkChanged(isSlow);
                    }
                }
            };
            IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            ApplicationLoader.applicationContext.registerReceiver(networkStateReceiver, filter);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            final IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            final BroadcastReceiver mReceiver = new ScreenReceiver();
            applicationContext.registerReceiver(mReceiver, filter);
        } catch (Exception e) {
            e.printStackTrace();
        }

        LiteMode.registerBatteryReceiver();

        try {
            PowerManager pm = (PowerManager) ApplicationLoader.applicationContext.getSystemService(Context.POWER_SERVICE);
            isScreenOn = pm.isScreenOn();
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("screen state = " + isScreenOn);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        SharedConfig.loadConfig();
        SharedPrefsHelper.init(applicationContext);
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) { //TODO improve account
            UserConfig.getInstance(a).loadConfig();
            MessagesController.getInstance(a);
            if (a == 0) {
                SharedConfig.pushStringStatus = "__FIREBASE_GENERATING_SINCE_" + ConnectionsManager.getInstance(a).getCurrentTime() + "__";
            } else {
                ConnectionsManager.getInstance(a);
            }
            TLRPC.User user = UserConfig.getInstance(a).getCurrentUser();
            if (user != null) {
                MessagesController.getInstance(a).putUser(user, true);
                SendMessagesHelper.getInstance(a).checkUnsentMessages();
            }
        }
        checkProxyForNetworkState();

        ApplicationLoader app = (ApplicationLoader) ApplicationLoader.applicationContext;
        app.initPushServices();
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("app initied");
        }

        MediaController.getInstance();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) { //TODO improve account
            ContactsController.getInstance(a).checkAppAccount();
            DownloadController.getInstance(a);
        }
        BillingController.getInstance().startConnection();
        // HTML widgets: the isolated engine's process up front, after the app's own start, so that a widget's first
        // frame does not wait for it.
        AndroidUtilities.runOnUIThread(() -> Utilities.globalQueue.postRunnable(
                () -> org.telegram.utils.twidget.WidgetSandboxSession.prewarmEngine(applicationContext)), 3000);
    }

    public ApplicationLoader() {
        super();
    }

    @NonNull
    @Override
    public androidx.work.Configuration getWorkManagerConfiguration() {
        return new Builder().build();
    }

    @Override
    public void onCreate() {
        applicationLoaderInstance = this;
        try {
            applicationContext = getApplicationContext();
        } catch (Throwable ignore) {

        }
        super.onCreate();
        if (AppProcess.isPluginAnalyzer()) {
            return;
        }

        // The widget engine's isolated process has no files, network or accounts: none of the client starts there.
        if (org.telegram.utils.twidget.WidgetEngineService.isEngineProcess()) {
            return;
        }

        // AndroidUtilities must be initialized before FileLog
        final String helloWorld = AndroidUtilities.getHelloWorld();

        if (BuildVars.LOGS_ENABLED) {
            FileLog.d(helloWorld);
            FileLog.d("app start time = " + (startTime = SystemClock.elapsedRealtime()));
            try {
                final PackageInfo info = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
                final String abi;
                switch (info.versionCode % 10) {
                    case 1:
                    case 2:
                        abi = "store bundled " + Build.CPU_ABI + " " + Build.CPU_ABI2;
                        break;
                    default:
                    case 9:
                        abi = "universal " + Build.CPU_ABI + " " + Build.CPU_ABI2;
                        break;
                }
                FileLog.d("buildVersion = " + String.format(Locale.US, "v%s (%d[%d]) %s", info.versionName, info.versionCode / 10, info.versionCode % 10, abi));
            } catch (Exception e) {
                FileLog.e(e);
            }
            FileLog.d("device = manufacturer=" + Build.MANUFACTURER + ", device=" + Build.DEVICE + ", model=" + Build.MODEL + ", product=" + Build.PRODUCT);
        }
        if (applicationContext == null) {
            applicationContext = getApplicationContext();
        }

        NativeLoader.initNativeLibs(ApplicationLoader.applicationContext);
        NativeCrashHandler.init(NativeCrashHandler.getCrashFlagPath());

        try {
            ConnectionsManager.native_setJava(false);
        } catch (UnsatisfiedLinkError error) {
            throw new RuntimeException("can't load native libraries " +  Build.CPU_ABI + " lookup folder " + NativeLoader.getAbiFolder());
        }
        SensitiveRequestSwitch.init(applicationContext);
        configureEmbeddedExteraHook();
        PluginsController.applyArtOpts();
        new ForegroundDetector(this) {
            @Override
            public void onActivityStarted(Activity activity) {
                boolean wasInBackground = isBackground();
                super.onActivityStarted(activity);
                if (wasInBackground) {
                    ensureCurrentNetworkGet(true);
                }
            }
        };
        if (BuildConfig.DEBUG_VERSION) {
            new ANRDetector(FileLog::dumpANR);
            SensitiveRequestConfirmation.verifyPolicy();
        }

        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("load libs time = " + (SystemClock.elapsedRealtime() - startTime));
        }

        if (BuildVars.DEBUG_VERSION || ExteraConfig.getPluginsEngine()) {
            var oldDefaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
                var stackTrace = Log.getStackTraceString(e);
                try {
                    // we cant check crash source, so we just check if plugins engine is enabled
                    if (ExteraConfig.getPluginsEngine()) {
                        var editor = PluginsController.getInstance().getPreferences().edit();
                        editor.putBoolean("had_crash", true).apply();

                        String pluginId = null;
                        for (String id : PluginsController.getInstance().getPlugins().keySet()) {
                            if (stackTrace.contains(id)) {
                                pluginId = id;
                            }
                        }
                        if (pluginId != null) {
                            editor.putString("crashed_plugin_id", pluginId).apply();
                            FileLog.e("Plugin crash detected for plugin: " + pluginId);
                        }
                    }

                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("label", stackTrace);
                    clipboard.setPrimaryClip(clip);
                } catch (Exception ex) {
                    FileLog.e(ex);
                }
                FileLog.e(stackTrace);
                if (oldDefaultUncaughtExceptionHandler != null) {
                    oldDefaultUncaughtExceptionHandler.uncaughtException(t, e);
                }
            });
        }

        applicationHandler = new Handler(applicationContext.getMainLooper());

        RemoteUtils.initCached();
        AndroidUtilities.runOnUIThread(ApplicationLoader::startPushService);

        AppIconController.fixLauncherIconIfNeeded();
        ProxyRotationController.init();
        ProxyPingController.init();

        // extera debug
        HeapMonitor.init();
        LoadMonitor.init();
        FreezeMonitor.init();
        // extera debug

        ApplicationLoader app = (ApplicationLoader) ApplicationLoader.applicationContext;
        app.initFirebase();

        //if (BuildConfig.DEBUG_PRIVATE_VERSION) {
        //    Choreographer60FpsContent.getInstance().addFrameCallback(debugEverySecondChecks, 1);
        //}
    }

    private static void configureEmbeddedExteraHook() {
        if (!BuildConfig.EXTERA_HOOK_EMBEDDED || exteraHookEmbeddedConfigured) {
            return;
        }

        synchronized (ApplicationLoader.class) {
            if (exteraHookEmbeddedConfigured) {
                return;
            }

            try {
                Class.forName("com.exteragram.hook.ExteraHook").getMethod("useEmbeddedRuntime").invoke(null);
                exteraHookEmbeddedConfigured = true;
            } catch (Throwable t) {
                throw new RuntimeException("Failed to configure embedded exteraHook runtime", t);
            }
        }
    }

    private final Runnable debugEverySecondChecks = () -> AndroidUtilities.runOnUIThread(() -> {
        NotificationCenter.sanitize();
    });

    public static void startPushService() {
        SharedPreferences preferences = MessagesController.getGlobalNotificationsSettings();
        boolean enabled;
        if (preferences.contains("pushService")) {
            enabled = preferences.getBoolean("pushService", true);
        } else {
            enabled = MessagesController.getMainSettings(UserConfig.selectedAccount).getBoolean("keepAliveService", false);
        }
        if (enabled) {
            try {
                applicationContext.startService(new Intent(applicationContext, NotificationsService.class));
            } catch (Throwable ignore) {

            }
        } else {
            applicationContext.stopService(new Intent(applicationContext, NotificationsService.class));
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (AppProcess.isPluginAnalyzer()) {
            return;
        }
        try {
            LocaleController.getInstance().onDeviceConfigurationChange(newConfig);
            AndroidUtilities.checkDisplaySize(applicationContext, newConfig);
            VideoCapturerDevice.checkScreenCapturerSize();
            AndroidUtilities.resetTabletFlag();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static FirebaseAnalytics firebaseAnalytics;
    private static FirebaseCrashlytics firebaseCrashlytics;

    private void initFirebase() {
        AndroidUtilities.runOnUIThread(() -> {
            firebaseAnalytics = FirebaseAnalytics.getInstance(this);
            firebaseCrashlytics = FirebaseCrashlytics.getInstance();
            firebaseCrashlytics.setCustomKey("app_flavor", BuildVars.IS_LITE_VERSION ? "lite" : "full");
            firebaseAnalytics.setAnalyticsCollectionEnabled(ExteraConfig.getUseGoogleAnalytics());
            firebaseCrashlytics.setCrashlyticsCollectionEnabled(ExteraConfig.getUseGoogleCrashlytics());
        });
    }

    public static FirebaseAnalytics getFirebaseAnalytics() {
        return firebaseAnalytics;
    }

    public static FirebaseCrashlytics getFirebaseCrashlytics() {
        return firebaseCrashlytics;
    }

    private void initPushServices() {
        AndroidUtilities.runOnUIThread(() -> {
            if (getPushProvider().hasServices()) {
                getPushProvider().onRequestPushToken();
            } else {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("No valid " + getPushProvider().getLogTitle() + " APK found.");
                }
                SharedConfig.pushStringStatus = "__NO_GOOGLE_PLAY_SERVICES__";
                PushListenerController.sendRegistrationToServer(getPushProvider().getPushType(), null);
            }
        }, 1000);
    }

    private boolean checkPlayServices() {
        try {
            int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
            return resultCode == ConnectionResult.SUCCESS;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return true;
    }

    private static long lastNetworkCheck = -1;
    private static void ensureCurrentNetworkGet() {
        final long now = System.currentTimeMillis();
        ensureCurrentNetworkGet(now - lastNetworkCheck > 5000);
        lastNetworkCheck = now;
    }

    private static void ensureCurrentNetworkGet(boolean force) {
        if (force || currentNetworkInfo == null) {
            try {
                if (connectivityManager == null) {
                    connectivityManager = (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                }
                currentNetworkInfo = connectivityManager.getActiveNetworkInfo();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    if (networkCallback == null) {
                        networkCallback = new ConnectivityManager.NetworkCallback() {
                            @Override
                            public void onAvailable(@NonNull Network network) {
                                lastKnownNetworkType = -1;
                                checkProxyForNetworkState();
                            }

                            @Override
                            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                                lastKnownNetworkType = -1;
                                checkProxyForNetworkState();
                            }

                            @Override
                            public void onLost(@NonNull Network network) {
                                lastKnownNetworkType = -1;
                                checkProxyForNetworkState();
                            }
                        };
                        connectivityManager.registerDefaultNetworkCallback(networkCallback);
                    }
                }
            } catch (Throwable ignore) {

            }
        }
    }

    public static void checkProxyForNetworkState() {
        if (applicationContext == null) {
            return;
        }
        try {
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            boolean autoDisabled = preferences.getBoolean(PREF_PROXY_AUTO_DISABLED, false);

            boolean shouldDisable = ExteraConfig.isProxyDisabledOn(ProxyDisableCondition.VPN) && isVpnEnabled()
                    || ExteraConfig.isProxyDisabledOn(ProxyDisableCondition.MOBILE_DATA) && hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    || ExteraConfig.isProxyDisabledOn(ProxyDisableCondition.WIFI) && hasTransport(NetworkCapabilities.TRANSPORT_WIFI);

            if (!shouldDisable) {
                if (!autoDisabled) {
                    return;
                }
                String proxyAddress = preferences.getString("proxy_ip", "");

                SharedPreferences.Editor editor = preferences.edit()
                        .putBoolean(PREF_PROXY_AUTO_DISABLED, false);
                if (!TextUtils.isEmpty(proxyAddress)) {
                    editor.putBoolean("proxy_enabled", true);
                }
                editor.apply();

                if (!TextUtils.isEmpty(proxyAddress)) {
                    ConnectionsManager.setProxySettings(true, ProxySettings.fromSharedPreferences(preferences));
                    NotificationCenter.getGlobalInstance().postNotificationNameOnUIThread(NotificationCenter.proxySettingsChanged);
                }
                return;
            }

            if (autoDisabled) {
                return;
            }
            boolean proxyEnabled = preferences.getBoolean("proxy_enabled", false);
            String proxyAddress = preferences.getString("proxy_ip", "");
            if (!proxyEnabled || TextUtils.isEmpty(proxyAddress)) {
                return;
            }
            preferences.edit()
                    .putBoolean(PREF_PROXY_AUTO_DISABLED, true)
                    .putBoolean("proxy_enabled", false)
                    .apply();
            ConnectionsManager.setProxySettings(false, null);
            NotificationCenter.getGlobalInstance().postNotificationNameOnUIThread(NotificationCenter.proxySettingsChanged);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static boolean hasTransport(int transport) {
        try {
            if (connectivityManager == null) {
                connectivityManager = (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            }
            if (connectivityManager == null) {
                return false;
            }
            Network activeNetwork = connectivityManager.getActiveNetwork();
            if (activeNetwork == null) {
                return false;
            }
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
            return capabilities != null && capabilities.hasTransport(transport);
        } catch (Throwable ignore) {
            return false;
        }
    }

    private static boolean isVpnEnabled() {
        try {
            if (connectivityManager == null) {
                connectivityManager = (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            }
            if (connectivityManager == null) {
                return false;
            }
            Network activeNetwork = connectivityManager.getActiveNetwork();
            if (activeNetwork != null) {
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
                if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return true;
                }
            }
            ensureCurrentNetworkGet(false);
            return currentNetworkInfo != null && currentNetworkInfo.getType() == ConnectivityManager.TYPE_VPN;
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static boolean isRoaming() {
        try {
            ensureCurrentNetworkGet(false);
            return currentNetworkInfo != null && currentNetworkInfo.isRoaming();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    public static boolean isConnectedOrConnectingToWiFi() {
        try {
            ensureCurrentNetworkGet(false);
            if (currentNetworkInfo != null && (currentNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI || currentNetworkInfo.getType() == ConnectivityManager.TYPE_ETHERNET)) {
                NetworkInfo.State state = currentNetworkInfo.getState();
                if (state == NetworkInfo.State.CONNECTED || state == NetworkInfo.State.CONNECTING || state == NetworkInfo.State.SUSPENDED) {
                    return true;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    public static boolean isConnectedToWiFi() {
        try {
            ensureCurrentNetworkGet(false);
            if (currentNetworkInfo != null && (currentNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI || currentNetworkInfo.getType() == ConnectivityManager.TYPE_ETHERNET) && currentNetworkInfo.getState() == NetworkInfo.State.CONNECTED) {
                return true;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    public static boolean isConnectionSlow() {
        try {
            ensureCurrentNetworkGet(false);
            if (currentNetworkInfo != null && currentNetworkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
                switch (currentNetworkInfo.getSubtype()) {
                    case TelephonyManager.NETWORK_TYPE_1xRTT:
                    case TelephonyManager.NETWORK_TYPE_CDMA:
                    case TelephonyManager.NETWORK_TYPE_EDGE:
                    case TelephonyManager.NETWORK_TYPE_GPRS:
                    case TelephonyManager.NETWORK_TYPE_IDEN:
                        return true;
                }
            }
        } catch (Throwable ignore) {

        }
        return false;
    }

    public static int getAutodownloadNetworkType() {
        try {
            ensureCurrentNetworkGet(false);
            if (currentNetworkInfo == null) {
                return StatsController.TYPE_MOBILE;
            }
            if (currentNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI || currentNetworkInfo.getType() == ConnectivityManager.TYPE_ETHERNET) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && (lastKnownNetworkType == StatsController.TYPE_MOBILE || lastKnownNetworkType == StatsController.TYPE_WIFI) && System.currentTimeMillis() - lastNetworkCheckTypeTime < 5000) {
                    return lastKnownNetworkType;
                }
                if (connectivityManager.isActiveNetworkMetered()) {
                    lastKnownNetworkType = StatsController.TYPE_MOBILE;
                } else {
                    lastKnownNetworkType = StatsController.TYPE_WIFI;
                }
                lastNetworkCheckTypeTime = System.currentTimeMillis();
                return lastKnownNetworkType;
            }
            if (currentNetworkInfo.isRoaming()) {
                return StatsController.TYPE_ROAMING;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return StatsController.TYPE_MOBILE;
    }

    public static int getCurrentNetworkType() {
        if (isConnectedOrConnectingToWiFi()) {
            return StatsController.TYPE_WIFI;
        } else if (isRoaming()) {
            return StatsController.TYPE_ROAMING;
        } else {
            return StatsController.TYPE_MOBILE;
        }
    }

    public static boolean isNetworkOnlineFast() {
        try {
            ensureCurrentNetworkGet(false);
            if (currentNetworkInfo == null) {
                return true;
            }
            if (currentNetworkInfo.isConnectedOrConnecting() || currentNetworkInfo.isAvailable()) {
                return true;
            }

            NetworkInfo netInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
            if (netInfo != null && netInfo.isConnectedOrConnecting()) {
                return true;
            } else {
                netInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                if (netInfo != null && netInfo.isConnectedOrConnecting()) {
                    return true;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
            return true;
        }
        return false;
    }

    public static boolean isNetworkOnlineRealtime() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
            if (netInfo != null && (netInfo.isConnectedOrConnecting() || netInfo.isAvailable())) {
                return true;
            }

            netInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

            if (netInfo != null && netInfo.isConnectedOrConnecting()) {
                return true;
            } else {
                netInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                if (netInfo != null && netInfo.isConnectedOrConnecting()) {
                    return true;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
            return true;
        }
        return false;
    }

    public static boolean isNetworkOnline() {
        boolean result = isNetworkOnlineRealtime();
        if (BuildVars.DEBUG_PRIVATE_VERSION) {
            boolean result2 = isNetworkOnlineFast();
            if (result != result2) {
                FileLog.d("network online mismatch");
            }
        }
        return result;
    }

    public boolean checkApkInstallPermissions(final Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !applicationContext.getPackageManager().canRequestPackageInstalls()) {
            AlertsCreator.createApkRestrictedDialog(context, null).show();
            return false;
        }
        return true;
    }

    public boolean openApkInstall(Activity activity, TLRPC.Document document) {
        UpdaterUtils.installUpdate(activity, document);
        return true;
    }

    public boolean showUpdateAppPopup(Activity activity, TLRPC.TL_help_appUpdate update, int account) {
        try {
            (new UpdateAppAlertDialog(activity, update, account)).show();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return true;
    }

    public boolean showCustomUpdateAppPopup(Context context, BetaUpdate update, int account) {
        return false;
    }

    public IUpdateLayout takeUpdateLayout(Activity activity, ViewGroup sideMenuContainer) {
        return new UpdateLayout(activity, sideMenuContainer);
    }

    public TLRPC.Update parseTLUpdate(int constructor) {
        return null;
    }

    public void processUpdate(int currentAccount, TLRPC.Update update) {

    }

    public boolean onSuggestionFill(String suggestion, CharSequence[] output, boolean[] closeable) {
        return false;
    }

    public boolean onSuggestionClick(String suggestion) {
        return false;
    }

    public void addItemOptions(ItemOptions itemOptions) {

    }

    public boolean checkRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        return false;
    }

    public boolean consumePush(int account, JSONObject json) {
        return false;
    }

    public void onResume() {

    }

    public boolean onPause() {
        return false;
    }

    public BaseFragment openSettings(int n) {
        return null;
    }

    public boolean isCustomUpdate() {
        return false;
    }
    public void downloadUpdate() {}
    public void cancelDownloadingUpdate() {}
    public boolean isDownloadingUpdate() {
        return false;
    }
    public float getDownloadingUpdateProgress() {
        return 0.0f;
    }
    public void checkUpdate(boolean force, Runnable whenDone) {}
    public BetaUpdate getUpdate() {
        return null;
    }
    public File getDownloadedUpdateFile() {
        return null;
    }
}