/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.ai.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.exteragram.messenger.ai.network.backend.OnDeviceAvailability;
import com.exteragram.messenger.icons.IconManager;
import com.exteragram.messenger.utils.AppUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.LaunchActivity;

public final class OnDeviceModelNotifications {

    private static final String CHANNEL_ID = "ai_on_device_model";
    private static final int NOTIFICATION_ID = 8732834;
    private static final long STALE_TIMEOUT = 5 * 60 * 1000L;

    private OnDeviceModelNotifications() {
    }

    public static void showProgress(int modelKey, int percent) {
        NotificationCompat.Builder builder = createBuilder(modelKey)
                .setContentTitle(getString(R.string.DownloadingModel))
                .setSilent(true)
                .setOngoing(false)
                .setTimeoutAfter(STALE_TIMEOUT);
        if (percent >= 0) {
            builder.setContentText(percent + "%");
            builder.setProgress(100, percent, false);
        } else {
            builder.setProgress(0, 0, true);
        }
        notify(modelKey, builder);
    }

    public static void showCompleted(int modelKey) {
        notify(modelKey, createBuilder(modelKey)
                .setContentTitle(getString(R.string.AIOnDeviceNotificationReady))
                .setContentText(getString(R.string.AIOnDeviceNotificationReadyInfo))
                .setAutoCancel(true));
    }

    public static void cancel(int modelKey) {
        try {
            NotificationManagerCompat.from(ApplicationLoader.applicationContext).cancel(NOTIFICATION_ID + modelKey);
        } catch (Exception e) {
            FileLog.e("AI_ON_DEVICE_NOTIFICATION", e);
        }
    }

    private static NotificationCompat.Builder createBuilder(int modelKey) {
        Context context = ApplicationLoader.applicationContext;
        Intent intent = new Intent(context, LaunchActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(IconManager.getNotificationIcon())
                .setColor(AppUtils.getNotificationColor())
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setSubText(getModelLabel(modelKey))
                .setContentIntent(pendingIntent);
    }

    private static String getModelLabel(int modelKey) {
        if ((modelKey & OnDeviceAvailability.MODEL_PREVIEW) != 0 && (modelKey & OnDeviceAvailability.MODEL_FAST) != 0) {
            return getString(R.string.AIOnDevicePreviewModel) + ", " + getString(R.string.AIOnDeviceFastModel);
        }
        if ((modelKey & OnDeviceAvailability.MODEL_PREVIEW) != 0) {
            return getString(R.string.AIOnDevicePreviewModel);
        }
        if ((modelKey & OnDeviceAvailability.MODEL_FAST) != 0) {
            return getString(R.string.AIOnDeviceFastModel);
        }
        return null;
    }

    private static void notify(int modelKey, NotificationCompat.Builder builder) {
        try {
            Context context = ApplicationLoader.applicationContext;
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            notificationManager.createNotificationChannel(new NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName(getString(R.string.AIOnDeviceNotificationChannel))
                    .setLightsEnabled(false)
                    .setVibrationEnabled(false)
                    .build());
            notificationManager.notify(NOTIFICATION_ID + modelKey, builder.build());
        } catch (Exception e) {
            FileLog.e("AI_ON_DEVICE_NOTIFICATION", e);
        }
    }
}
