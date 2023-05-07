/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.utils;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.hardware.biometrics.BiometricManager;
import android.hardware.fingerprint.FingerprintManager;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.core.content.FileProvider;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.ui.BasePermissionsActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class SystemUtils {

    public static boolean loadSystemEmojiFailed = false;
    private static Typeface systemEmojiTypeface;

    @RequiresApi(api = 23)
    public static boolean isPermissionGranted(String perm) {
        return ApplicationLoader.applicationContext.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    @RequiresApi(api = 23)
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

    @RequiresApi(api = 23)
    public static void requestImagesAndVideoPermission(Activity activity) {
        requestImagesAndVideoPermission(activity, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE);
    }

    @RequiresApi(api = 23)
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

    @RequiresApi(api = 23)
    public static void requestImagesPermission(Activity activity) {
        requestImagesPermission(activity, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE);
    }

    @RequiresApi(api = 23)
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

    @RequiresApi(api = 23)
    public static void requestAudioPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(activity, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE, Manifest.permission.READ_MEDIA_AUDIO);
        else
            requestPermissions(activity, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    public static boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (Build.VERSION.SDK_INT >= 33)
                return isImagesPermissionGranted() && isVideoPermissionGranted() && isAudioPermissionGranted();
            else return isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE);
        } else return true;
    }

    @RequiresApi(api = 23)
    public static void requestStoragePermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(activity, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE, Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO);
        else
            requestPermissions(activity, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    public static boolean hasBiometrics() {
        if (Build.VERSION.SDK_INT >= 29) {
            BiometricManager biometricManager = ApplicationLoader.applicationContext.getSystemService(BiometricManager.class);
            if (biometricManager == null) {
                return false;
            }
            if (Build.VERSION.SDK_INT >= 30) {
                return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS;
            } else {
                //noinspection deprecation
                return biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS;
            }
        } else if (Build.VERSION.SDK_INT >= 23) {
            FingerprintManager fingerprintManager = ApplicationLoader.applicationContext.getSystemService(FingerprintManager.class);
            if (fingerprintManager == null) {
                return false;
            }
            return fingerprintManager.isHardwareDetected() && fingerprintManager.hasEnrolledFingerprints();
        }
        return false;
    }

    public static boolean hasGps() {
        boolean hasGps;
        try {
            hasGps = ApplicationLoader.applicationContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
        } catch (Throwable e) {
            hasGps = false;
        }
        return hasGps;
    }

    public static File getSystemEmojiFontPath() {
        try (var br = new BufferedReader(new FileReader("/system/etc/fonts.xml"))) {
            String line;
            var ignored = false;
            while ((line = br.readLine()) != null) {
                var trimmed = line.trim();
                if (trimmed.startsWith("<family") && trimmed.contains("ignore=\"true\"")) {
                    ignored = true;
                } else if (trimmed.startsWith("</family>")) {
                    ignored = false;
                } else if (trimmed.startsWith("<font") && !ignored) {
                    var start = trimmed.indexOf(">");
                    var end = trimmed.indexOf("<", 1);
                    if (start > 0 && end > 0) {
                        var font = trimmed.substring(start + 1, end);
                        if (font.toLowerCase().contains("emoji")) {
                            File file = new File("/system/fonts/" + font);
                            if (file.exists()) {
                                FileLog.d("emoji font file fonts.xml = " + font);
                                return file;
                            }
                        }
                    }
                }
            }
            br.close();

            var fileAOSP = new File("/system/fonts/NotoColorEmoji.ttf");
            if (fileAOSP.exists()) {
                return fileAOSP;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public static Typeface getSystemEmojiTypeface() {
        if (!loadSystemEmojiFailed && systemEmojiTypeface == null) {
            var font = getSystemEmojiFontPath();
            if (font != null) {
                systemEmojiTypeface = Typeface.createFromFile(font);
            }
            if (systemEmojiTypeface == null) {
                loadSystemEmojiFailed = true;
            }
        }
        return systemEmojiTypeface;
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
}