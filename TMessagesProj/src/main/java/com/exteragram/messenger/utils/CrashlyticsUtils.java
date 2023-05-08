/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.utils;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.SharedConfig;

public class CrashlyticsUtils {

    public static boolean isGooglePlayServicesAvailable(Context context) {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context);
        return resultCode == ConnectionResult.SUCCESS;
    }

    private static String getPerformanceClassString() {
        switch (SharedConfig.getDevicePerformanceClass()) {
            case SharedConfig.PERFORMANCE_CLASS_LOW:
                return "Low";
            case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                return "Average";
            case SharedConfig.PERFORMANCE_CLASS_HIGH:
                return "High";
            default:
                return "N/A";
        }
    }

    public static void logEvents(Context context) {
        if (ApplicationLoader.getFirebaseAnalytics() == null) {
            return;
        }
        Bundle params = new Bundle();
        params.putString("android_version", Build.VERSION.RELEASE);
        params.putString("version", BuildConfig.VERSION_NAME);
        params.putInt("version_code", BuildConfig.VERSION_CODE);
        params.putBoolean("has_play_services", isGooglePlayServicesAvailable(context));
        params.putString("device", Build.MANUFACTURER + " " + Build.MODEL);
        params.putString("performance_class", getPerformanceClassString());
        params.putString("locale", LocaleController.getSystemLocaleStringIso639());
        ApplicationLoader.getFirebaseAnalytics().logEvent("stats", params);
    }
}
