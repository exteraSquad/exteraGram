/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.system;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.biometrics.BiometricManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.exteragram.messenger.utils.network.RemoteUtils;
import com.exteragram.messenger.utils.ui.UIUtil;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.BasePermissionsActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SystemUtils {

    public static final String GOOGLE_PACKAGE_NAME = "com.google.android.googlequicksearchbox";

    private static final long ROUND_VIDEO_MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final long ROUND_VIDEO_MAX_DURATION_MS = 60_000L;
    private static final long ROUND_VIDEO_MIN_DURATION_MS = 10_000L;

    private static final List<ExternalApp> externalApps = new ArrayList<>();
    public static Boolean isLensAvailable = null;
    public static String lensActivityName;

    public static boolean isPermissionGranted(String perm) {
        return ApplicationLoader.applicationContext.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestPermissions(Activity activity, int code, String... perms) {
        if (activity == null) return;
        activity.requestPermissions(perms, code);
    }

    public static boolean isVideoPermissionGranted() {
        if (Build.VERSION.SDK_INT >= 33)
            return isPermissionGranted(Manifest.permission.READ_MEDIA_VIDEO);
        else return isStoragePermissionGranted();
    }

    public static boolean isImagesAndVideoPermissionGranted() {
        if (Build.VERSION.SDK_INT >= 33)
            return isImagesPermissionGranted() && isVideoPermissionGranted();
        else return isStoragePermissionGranted();
    }

    public static void requestImagesAndVideoPermission(Activity activity) {
        requestImagesAndVideoPermission(activity, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE);
    }

    public static void requestImagesAndVideoPermission(Activity activity, int code) {
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(activity, code, Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO);
        else requestPermissions(activity, code, Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    public static boolean isImagesPermissionGranted() {
        if (Build.VERSION.SDK_INT >= 33)
            return isPermissionGranted(Manifest.permission.READ_MEDIA_IMAGES);
        else return isStoragePermissionGranted();
    }

    public static void requestImagesPermission(Activity activity) {
        requestImagesPermission(activity, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE);
    }

    public static void requestImagesPermission(Activity activity, int code) {
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(activity, code, Manifest.permission.READ_MEDIA_IMAGES);
        else requestPermissions(activity, code, Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    public static boolean isAudioPermissionGranted() {
        if (Build.VERSION.SDK_INT >= 33)
            return isPermissionGranted(Manifest.permission.READ_MEDIA_AUDIO);
        else return isStoragePermissionGranted();
    }

    public static void requestAudioPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(activity, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE, Manifest.permission.READ_MEDIA_AUDIO);
        else
            requestPermissions(activity, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    public static boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= 33)
            return isImagesPermissionGranted() && isVideoPermissionGranted() && isAudioPermissionGranted();
        else return isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    public static void requestStoragePermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(activity, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE, Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO);
        else
            requestPermissions(activity, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    @SuppressWarnings("deprecation")
    public static boolean hasBiometrics() {
        if (Build.VERSION.SDK_INT >= 29) {
            BiometricManager biometricManager = ApplicationLoader.applicationContext.getSystemService(BiometricManager.class);
            if (biometricManager == null) {
                return false;
            }
            if (Build.VERSION.SDK_INT >= 30) {
                return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS;
            } else {
                return biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS;
            }
        } else {
            return androidx.biometric.BiometricManager.from(ApplicationLoader.applicationContext)
                    .canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS;
        }
    }

    public static File getFileFromBitmap(Bitmap bitmap) throws IOException {
        File file = new File(AndroidUtilities.getCacheDir(), "temp.jpeg");
        file.createNewFile();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
        }
        return file;
    }

    public static void addFileToClipboard(File file, Runnable callback) {
        try {
            Context context = ApplicationLoader.applicationContext;
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            Uri uri = FileProvider.getUriForFile(context, ApplicationLoader.getApplicationId() + ".provider", file);
            ClipData clip = ClipData.newUri(context.getContentResolver(), "label", uri);
            clipboard.setPrimaryClip(clip);
            callback.run();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static boolean isAppInstalled(String pkg) {
        try {
            ApplicationLoader.applicationContext.getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isLensAvailable() {
        if (isLensAvailable == null) {
            try {
                checkLensAvailability();
            } catch (Exception e) {
                FileLog.e(e);
                isLensAvailable = false;
            }
        }
        return isLensAvailable;
    }

    public static void checkLensAvailability() {
        Intent intent = new Intent("android.intent.action.SEND")
                .setDataAndType(Uri.parse("content://" +
                        ApplicationLoader.getApplicationId() +
                        ".provider"), "image/jpeg")
                .setPackage(GOOGLE_PACKAGE_NAME);

        List<ResolveInfo> activities = ApplicationLoader.applicationContext
                .getPackageManager()
                .queryIntentActivities(intent, 0);

        for (ResolveInfo resolveInfo : activities) {
            String activityName = resolveInfo.activityInfo.name;
            if (activityName.contains("Lens")) {
                isLensAvailable = true;
                lensActivityName = activityName;
                break;
            }
        }

        if (isLensAvailable == null) {
            isLensAvailable = false;
        }
    }

    public static void shareImageWithGoogleLens(Activity activity, Uri imageUri) {
        Intent intent = new Intent(Intent.ACTION_SEND)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .setClassName(GOOGLE_PACKAGE_NAME, lensActivityName)
                .setDataAndType(imageUri, "image/jpeg")
                .putExtra(Intent.EXTRA_STREAM, imageUri);

        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            FileLog.e(e);
        }
    }

    public static int getRoundVideoResolution() {
        return RemoteUtils.getIntConfigValue(RemoteUtils.ROUND_VIDEO_RESOLUTION, 512);
    }

    public static int getRoundVideoBitrate() {
        return RemoteUtils.getIntConfigValue(RemoteUtils.ROUND_VIDEO_BITRATE, 1000);
    }

    public static int getRoundAudioBitrate() {
        return RemoteUtils.getIntConfigValue(RemoteUtils.ROUND_AUDIO_BITRATE, 64);
    }

    public static long getRoundVideoMaxDurationMs() {
        long totalBitrate =
                (Math.max(1, getRoundVideoBitrate()) + Math.max(0, getRoundAudioBitrate())) * 1024L;

        long durationMs = ROUND_VIDEO_MAX_FILE_SIZE_BYTES * 8_000L / totalBitrate;
        return Utilities.clamp(durationMs, ROUND_VIDEO_MAX_DURATION_MS, ROUND_VIDEO_MIN_DURATION_MS);
    }

    private static final long EXTERNAL_AUDIO_OUTPUT_CACHE_MS = 500L;
    private static volatile long lastExternalAudioOutputCheck = -1L;
    private static volatile boolean lastExternalAudioOutput;

    public static boolean hasExternalAudioOutput(AudioManager audioManager) {
        if (audioManager == null) {
            return false;
        }
        final long now = SystemClock.elapsedRealtime();
        final long checkedAt = lastExternalAudioOutputCheck;
        if (checkedAt >= 0 && now - checkedAt < EXTERNAL_AUDIO_OUTPUT_CACHE_MS) {
            return lastExternalAudioOutput;
        }
        boolean found = false;
        try {
            for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                final int type = device.getType();
                if ((
                    type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                    type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                ) && device.isSink()) {
                    found = true;
                    break;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
            return lastExternalAudioOutput;
        }
        lastExternalAudioOutput = found;
        lastExternalAudioOutputCheck = now;
        return found;
    }

    public static List<ExternalApp> getExternalApps() {
        updateExternalApps();
        return externalApps;
    }

    private static void updateExternalApps() {
        if (!externalApps.isEmpty()) {
            externalApps.clear();
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("tel:00000000000"));

        PackageManager packageManager = ApplicationLoader.applicationContext.getPackageManager();
        List<ResolveInfo> resolveInfoList = packageManager.queryIntentActivities(intent, 0);

        for (ResolveInfo resolveInfo : resolveInfoList) {
            addExternalApp(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name);
        }
    }

    private static void addExternalApp(String packageName, String activityName) {
        if (isAppInstalled(packageName)) {
            externalApps.add(new ExternalApp(packageName, activityName));
        }
    }

    public static class ExternalApp {
        private final String activityName;
        private final String packageName;
        private final String appName;
        private final Drawable appIcon;

        public ExternalApp(String packageName, String activityName) {
            this.packageName = packageName;
            this.activityName = activityName;
            this.appIcon = fetchIcon();
            this.appName = fetchName();
        }

        public String getName() {
            return appName;
        }

        public Drawable getIcon() {
            return appIcon;
        }

        private String fetchName() {
            final PackageManager pm = ApplicationLoader.applicationContext.getPackageManager();
            ApplicationInfo info = null;
            String name = null;
            try {
                info = pm.getApplicationInfo(packageName, 0);
            } catch (Exception e) {
                name = LocaleController.getString(R.string.NumberUnknown);
            }
            return info == null ? name : (String) pm.getApplicationLabel(info);
        }

        private Drawable fetchIcon() {
            Drawable icon;
            try {
                icon = ApplicationLoader.applicationContext.getPackageManager().getApplicationIcon(packageName);
                icon = new BitmapDrawable(ApplicationLoader.applicationContext.getResources(), UIUtil.drawableToBitmap(icon, dp(24), dp(24)));
            } catch (Exception e) {
                icon = ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.msg_media);
            }
            return icon;
        }

        public void open(Activity activity, String phone) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("tel:" + phone));
                intent.setClassName(packageName, activityName);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivityForResult(intent, 500);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }
}