/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;

import com.exteragram.messenger.export.ui.ExportUiHelper;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;

public class ExportService extends Service implements NotificationCenter.NotificationCenterDelegate {

    private static final int NOTIFICATION_ID = 41;
    private static final int RESULT_NOTIFICATION_ID = 42;
    private static final long NOTIFY_INTERVAL = 500;
    private static final long WAKE_LOCK_TIMEOUT = 10 * 60 * 1000L;
    private static final String ACTION_STOP = "com.exteragram.messenger.export.STOP";

    private final NotificationCenter.ObserversGroup[] observers = new NotificationCenter.ObserversGroup[UserConfig.MAX_ACCOUNT_COUNT];
    private NotificationCompat.Builder builder;
    private int notificationAccount = -1;
    private PowerManager.WakeLock wakeLock;
    private boolean finished;
    private long lastNotifyTime;
    private int lastProgress = -1;
    private String lastText;

    public static void start() {
        try {
            ApplicationLoader.applicationContext.startService(new Intent(ApplicationLoader.applicationContext, ExportService.class));
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            observers[a] = NotificationCenter.getInstance(a).createObserversGroup(this).add(NotificationCenter.exportStateChanged);
        }
        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "exteraGram:export");
            wakeLock.setReferenceCounted(false);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int account = processingAccount();
        if (account < 0) {
            stopSelf();
            return Service.START_NOT_STICKY;
        }
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            ExportController.getInstance(account).cancel();
            return Service.START_NOT_STICKY;
        }
        ExportState.Processing state = (ExportState.Processing) ExportController.getInstance(account).getState();
        if (builder == null) {
            NotificationManagerCompat.from(this).cancel(RESULT_NOTIFICATION_ID);
            NotificationsController.checkOtherNotificationsChannel();
            builder = new NotificationCompat.Builder(ApplicationLoader.applicationContext, NotificationsController.OTHER_NOTIFICATIONS_CHANNEL);
            builder.setSmallIcon(android.R.drawable.stat_sys_download);
            builder.setWhen(System.currentTimeMillis());
            builder.setChannelId(NotificationsController.OTHER_NOTIFICATIONS_CHANNEL);
            builder.setContentTitle(LocaleController.getString(R.string.ExportProgressTitle));
            builder.setOnlyAlertOnce(true);
            builder.setOngoing(true);
            Intent stopIntent = new Intent(ApplicationLoader.applicationContext, ExportService.class).setAction(ACTION_STOP);
            builder.addAction(0, LocaleController.getString(R.string.ExportStopAction), PendingIntent.getService(ApplicationLoader.applicationContext, NOTIFICATION_ID, stopIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }
        setNotificationAccount(account);
        applyProgress(state);
        try {
            startForeground(NOTIFICATION_ID, builder.build());
        } catch (Throwable e) {
            FileLog.e(e);
        }
        acquireWakeLock();
        return Service.START_NOT_STICKY;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id != NotificationCenter.exportStateChanged || builder == null || finished) {
            return;
        }
        int processingAccount = processingAccount();
        if (processingAccount >= 0) {
            setNotificationAccount(processingAccount);
            if (applyProgress((ExportState.Processing) ExportController.getInstance(processingAccount).getState())) {
                notifyBuilder(NOTIFICATION_ID);
            }
            acquireWakeLock();
            return;
        }
        ExportState state = ExportController.getInstance(account).getState();
        finished = true;
        setNotificationAccount(account);
        if (state instanceof ExportState.Finished finishedState) {
            builder.setContentTitle(LocaleController.getString(R.string.ExportFinished));
            builder.setContentText(ExportUiHelper.summary(finishedState));
            builder.setSmallIcon(android.R.drawable.stat_sys_download_done);
        } else if (state instanceof ExportState.ApiError apiError && apiError.isDelay()) {
            builder.setContentTitle(LocaleController.getString(R.string.ExportDelayTitle));
            builder.setContentText(LocaleController.formatString(R.string.ExportAvailableFrom, ExportUiHelper.delayDate(apiError.delayUntil())));
            builder.setSmallIcon(android.R.drawable.stat_notify_error);
        } else if (state instanceof ExportState.ApiError || state instanceof ExportState.IoError) {
            builder.setContentTitle(LocaleController.getString(R.string.ExportFailed));
            builder.setContentText(LocaleController.getString(R.string.ExportFailedNotification));
            builder.setSmallIcon(android.R.drawable.stat_notify_error);
        } else {
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
            stopSelf();
            return;
        }
        builder.setProgress(0, 0, false);
        builder.clearActions();
        builder.setSubText(null);
        builder.setOngoing(false);
        builder.setAutoCancel(true);
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        } catch (Throwable e) {
            FileLog.e(e);
        }
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
        notifyBuilder(RESULT_NOTIFICATION_ID);
        stopSelf();
    }

    private boolean applyProgress(ExportState.Processing state) {
        int progress = Math.round(state.progress() * 1000);
        String entity = ExportUiHelper.entityTitle(state);
        String text = entity.isEmpty() ? ExportUiHelper.stepTitle(state) : entity;
        long now = SystemClock.elapsedRealtime();
        if (progress == lastProgress && text.equals(lastText) || now - lastNotifyTime < NOTIFY_INTERVAL) {
            return false;
        }
        lastNotifyTime = now;
        lastProgress = progress;
        lastText = text;
        builder.setProgress(1000, progress, state.substepsTotal == 0);
        builder.setContentText(text);
        builder.setSubText(ExportUiHelper.percent(state) + "%");
        return true;
    }

    private void notifyBuilder(int id) {
        try {
            NotificationManagerCompat.from(this).notify(id, builder.build());
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private void setNotificationAccount(int account) {
        if (notificationAccount == account) {
            return;
        }
        notificationAccount = account;
        Intent intent = ExportUiHelper.createShowIntent(ApplicationLoader.applicationContext, account);
        builder.setContentIntent(PendingIntent.getActivity(ApplicationLoader.applicationContext, NOTIFICATION_ID, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
    }

    private static int processingAccount() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (ExportController.getInstance(a).isRunning()) {
                return a;
            }
        }
        return -1;
    }

    private void acquireWakeLock() {
        if (wakeLock != null) {
            try {
                wakeLock.acquire(WAKE_LOCK_TIMEOUT);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        FileLog.d("ExportService timeout, type = " + fgsType);
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        } catch (Throwable ignore) {
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        for (NotificationCenter.ObserversGroup group : observers) {
            if (group != null) {
                group.removeAllObservers();
            }
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        if (!finished) {
            NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
        }
    }
}
