/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.security;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.CancellationSignal;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.exteragram.messenger.icons.IconManager;
import com.exteragram.messenger.utils.AppUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;

import java.util.Date;

public final class SensitiveRequestSwitch {

    public static final int STATE_ENFORCED = 0;
    public static final int STATE_PENDING = 1;
    public static final int STATE_DISABLED = 2;

    private static final String CHANNEL_ID = "sensitive_requests";
    private static final int NOTIFICATION_ID = 4100;
    private static final int CREDENTIAL_REQUEST_CODE = 0x5780;
    private static final String KEY_NOTIFIED_DISABLE_AT = "notified_disable_at";

    private static final Runnable updateNotification = SensitiveRequestSwitch::updateNotification;

    private static Runnable pendingCredentialSuccess;

    private SensitiveRequestSwitch() {
    }

    private static native boolean native_init(String directory);

    private static native long native_disableAt();

    private static native long native_scheduleDisable();

    private static native void native_enforce();

    public static void init(Context context) {
        try {
            native_init(context.getFilesDir().getAbsolutePath());
        } catch (Throwable e) {
            FileLog.e(e);
            return;
        }
        AndroidUtilities.runOnUIThread(updateNotification);
    }

    public static int getState() {
        long disableAt = native_disableAt();
        if (disableAt == 0) {
            return STATE_ENFORCED;
        }
        return System.currentTimeMillis() >= disableAt ? STATE_DISABLED : STATE_PENDING;
    }

    public static long getDisableAt() {
        return native_disableAt();
    }

    public static String formatDisableAt() {
        Date date = new Date(native_disableAt());
        return LocaleController.formatString(R.string.formatDateAtTime, LocaleController.getInstance().getFormatterDayMonth().format(date), LocaleController.getInstance().getFormatterDay().format(date));
    }

    public static void enforce() {
        native_enforce();
        preferences().edit().remove(KEY_NOTIFIED_DISABLE_AT).apply();
        AndroidUtilities.cancelRunOnUIThread(updateNotification);
        cancelNotification();
    }

    public static void requestDisable(BaseFragment fragment, Runnable onChanged) {
        Activity activity = fragment != null ? fragment.getParentActivity() : null;
        if (activity == null || getState() != STATE_ENFORCED) {
            return;
        }
        AlertDialog dialog = new AlertDialog.Builder(activity, fragment.getResourceProvider())
                .setTitle(getString(R.string.SensitiveRequestsDisableTitle))
                .setMessage(getString(R.string.SensitiveRequestsDisableText))
                .setPositiveButton(getString(R.string.SensitiveRequestsDisableAction), (d, which) -> authenticate(fragment, () -> {
                    if (native_scheduleDisable() != 0) {
                        updateNotification();
                    }
                    if (onChanged != null) {
                        onChanged.run();
                    }
                }))
                .setNegativeButton(getString(R.string.Cancel), null)
                .create();
        fragment.showDialog(dialog);
        dialog.redPositive();
    }

    public static boolean onActivityResult(int requestCode, int resultCode) {
        if (requestCode != CREDENTIAL_REQUEST_CODE) {
            return false;
        }
        Runnable onSuccess = pendingCredentialSuccess;
        pendingCredentialSuccess = null;
        if (onSuccess != null && resultCode == Activity.RESULT_OK) {
            onSuccess.run();
        }
        return true;
    }

    private static void authenticate(BaseFragment fragment, Runnable onSuccess) {
        Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        KeyguardManager keyguard = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguard == null || !keyguard.isDeviceSecure()) {
            fragment.showDialog(new AlertDialog.Builder(activity, fragment.getResourceProvider())
                    .setTitle(getString(R.string.SensitiveRequestsAuthTitle))
                    .setMessage(getString(R.string.SensitiveRequestsScreenLockRequired))
                    .setPositiveButton(getString(R.string.OK), null)
                    .create());
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                biometricPrompt(activity, onSuccess);
            } else {
                deviceCredential(activity, keyguard, onSuccess);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @SuppressWarnings("deprecation")
    @androidx.annotation.RequiresApi(29)
    private static void biometricPrompt(Activity activity, Runnable onSuccess) {
        android.hardware.biometrics.BiometricPrompt.Builder builder = new android.hardware.biometrics.BiometricPrompt.Builder(activity)
                .setTitle(getString(R.string.SensitiveRequestsAuthTitle))
                .setSubtitle(getString(R.string.SensitiveRequestsDisableTitle));
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
                    | android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        } else {
            builder.setDeviceCredentialAllowed(true);
        }
        builder.build().authenticate(new CancellationSignal(), activity.getMainExecutor(), new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult result) {
                onSuccess.run();
            }
        });
    }

    @SuppressWarnings("deprecation")
    private static void deviceCredential(Activity activity, KeyguardManager keyguard, Runnable onSuccess) {
        Intent intent = keyguard.createConfirmDeviceCredentialIntent(getString(R.string.SensitiveRequestsAuthTitle), getString(R.string.SensitiveRequestsDisableTitle));
        if (intent == null) {
            return;
        }
        pendingCredentialSuccess = onSuccess;
        activity.startActivityForResult(intent, CREDENTIAL_REQUEST_CODE);
    }

    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("sensitive_requests", Context.MODE_PRIVATE);
    }

    private static void updateNotification() {
        AndroidUtilities.cancelRunOnUIThread(updateNotification);
        try {
            int state = getState();
            if (state == STATE_PENDING) {
                long disableAt = getDisableAt();
                AndroidUtilities.runOnUIThread(updateNotification, Math.max(1000, disableAt - System.currentTimeMillis() + 1000));
                showNotification(createBuilder()
                        .setContentTitle(getString(R.string.SensitiveRequestsPendingNotificationTitle))
                        .setContentText(getString(R.string.SensitiveRequestsPendingNotificationText))
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(R.string.SensitiveRequestsPendingNotificationText)))
                        .setWhen(disableAt)
                        .setShowWhen(true)
                        .setUsesChronometer(true)
                        .setChronometerCountDown(true)
                        .setTimeoutAfter(Math.max(1, disableAt - System.currentTimeMillis())));
            } else if (state == STATE_DISABLED) {
                long disableAt = getDisableAt();
                SharedPreferences preferences = preferences();
                if (preferences.getLong(KEY_NOTIFIED_DISABLE_AT, 0) != disableAt) {
                    preferences.edit().putLong(KEY_NOTIFIED_DISABLE_AT, disableAt).apply();
                    showNotification(createBuilder()
                            .setContentTitle(getString(R.string.SensitiveRequestsDisabledNotificationTitle))
                            .setContentText(getString(R.string.SensitiveRequestsDisabledNotificationText))
                            .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(R.string.SensitiveRequestsDisabledNotificationText)))
                            .setAutoCancel(true));
                }
            } else {
                cancelNotification();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static NotificationCompat.Builder createBuilder() {
        Context context = ApplicationLoader.applicationContext;
        Intent intent = new Intent(context, LaunchActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(IconManager.getNotificationIcon())
                .setColor(AppUtils.getNotificationColor())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent);
    }

    @SuppressLint("MissingPermission")
    private static void showNotification(NotificationCompat.Builder builder) {
        NotificationManagerCompat manager = NotificationManagerCompat.from(ApplicationLoader.applicationContext);
        manager.createNotificationChannel(new NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(getString(R.string.SensitiveRequestsNotificationChannel))
                .build());
        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private static void cancelNotification() {
        try {
            NotificationManagerCompat.from(ApplicationLoader.applicationContext).cancel(NOTIFICATION_ID);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
