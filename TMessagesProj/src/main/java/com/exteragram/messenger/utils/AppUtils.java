/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.utils;

import android.annotation.SuppressLint;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.util.Base64;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

import java.security.MessageDigest;
import java.util.Calendar;

public class AppUtils {

    public static int getNotificationIconColor() {
        return BuildVars.isBetaApp() ? 0xff747f9f : 0xfff54142;
    }

    public static int[] getDrawerIconPack() {
        switch (org.telegram.ui.ActionBar.Theme.getEventType()) {
            case 0:
                return new int[]{
                        R.drawable.msg_groups_ny,
                        R.drawable.msg_secret_ny,
                        R.drawable.msg_channel_ny,
                        R.drawable.msg_contacts_ny,
                        R.drawable.msg_calls_ny,
                        R.drawable.msg_saved_ny,
                        R.drawable.msg_nearby_ny
                };
            case 1:
                return new int[]{
                        R.drawable.msg_groups_14,
                        R.drawable.msg_secret_14,
                        R.drawable.msg_channel_14,
                        R.drawable.msg_contacts_14,
                        R.drawable.msg_calls_14,
                        R.drawable.msg_saved_14,
                        R.drawable.msg_nearby_14
                };
            case 2:
                return new int[]{
                        R.drawable.msg_groups_hw,
                        R.drawable.msg_secret_hw,
                        R.drawable.msg_channel_hw,
                        R.drawable.msg_contacts_hw,
                        R.drawable.msg_calls_hw,
                        R.drawable.msg_saved_hw,
                        R.drawable.msg_nearby_hw
                };
            default:
                return new int[]{
                        R.drawable.msg_groups,
                        R.drawable.msg_secret,
                        R.drawable.msg_channel,
                        R.drawable.msg_contacts,
                        R.drawable.msg_calls,
                        R.drawable.msg_saved,
                        R.drawable.msg_nearby
                };
        }
    }

    public static boolean isWinter() {
        Calendar calendar = Calendar.getInstance();
        int currentMonth = calendar.get(Calendar.MONTH);
        return currentMonth == Calendar.DECEMBER || currentMonth == Calendar.JANUARY || currentMonth == Calendar.FEBRUARY;
    }

    // do not change or remove this part of the code if you're making public fork
    private static final String EXPECTED_SIGNATURE = "tcaLgrODWBN9GQvrHPfGzA==";
    private static final String EXPECTED_PACKAGE_NAME = "com.exteragram.messenger";

    public static boolean isAppModified() {
        try {
            @SuppressLint("PackageManagerGetSignatures")
            PackageInfo packageInfo = ApplicationLoader.applicationContext.getPackageManager()
                    .getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), PackageManager.GET_SIGNATURES);

            String currentPackageName = packageInfo.packageName;

            Signature signature = packageInfo.signatures[0];

            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] signatureBytes = signature.toByteArray();
            byte[] md5Bytes = md.digest(signatureBytes);
            String currentSignature = Base64.encodeToString(md5Bytes, Base64.DEFAULT).trim();

            return !EXPECTED_PACKAGE_NAME.equals(currentPackageName)
                    || !EXPECTED_SIGNATURE.equals(currentSignature);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return true;
    }
}
