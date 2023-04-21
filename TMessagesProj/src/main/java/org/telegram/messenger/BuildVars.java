/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 */

package org.telegram.messenger;

import android.os.Build;

import com.exteragram.messenger.ExteraConfig;

public class BuildVars {

    public static boolean DEBUG_VERSION = BuildConfig.BUILD_TYPE.equals("debug");
    public static boolean LOGS_ENABLED = ExteraConfig.getLogging();
    public static boolean DEBUG_PRIVATE_VERSION = DEBUG_VERSION;
    public static boolean USE_CLOUD_STRINGS = true;
    public static boolean CHECK_UPDATES = false;
    public static boolean NO_SCOPED_STORAGE = Build.VERSION.SDK_INT <= 29;
    public static boolean PM_BUILD = BuildConfig.IS_PM_BUILD;
    public static int BUILD_VERSION;
    public static String BUILD_VERSION_STRING;
    public static int APP_ID;
    public static String APP_HASH;

    // SafetyNet key for Google Identity SDK, set it to empty to disable
    public static String SAFETYNET_KEY = "";
    public static String SMS_HASH;
    public static String PLAYSTORE_APP_URL = "https://play.google.com/store/apps/details?id=com.exteragram.messenger";
    public static String GOOGLE_AUTH_CLIENT_ID = "760348033671-81kmi3pi84p11ub8hp9a1funsv0rn2p9.apps.googleusercontent.com";

    // You can use this flag to disable Google Play Billing (If you're making fork and want it to be in Google Play)
    public static boolean IS_BILLING_UNAVAILABLE = PM_BUILD;

    static {
        BUILD_VERSION = BuildConfig.VERSION_CODE;
        BUILD_VERSION_STRING = BuildConfig.VERSION_NAME;

        APP_ID = BuildConfig.APP_ID;
        // Obtain your own APP_ID at https://core.telegram.org/api/obtaining_api_id
        APP_HASH = BuildConfig.APP_HASH;
        // Obtain your own APP_HASH at https://core.telegram.org/api/obtaining_api_id
        SMS_HASH = isBetaApp() ? "2P1CNXYRAK6" : "UfajQkYoxTu";
        // Using our SMS_HASH you will not be able to get the SMS Retriever to work, generate your own keys with https://raw.githubusercontent.com/googlearchive/android-credentials/master/sms-verification/bin/sms_retriever_hash_v9.sh
    }

    public static boolean useInvoiceBilling() {
        return true;
    }

    private static boolean hasDirectCurrency() {
        return false;
    }

    public static boolean isStandaloneApp() {
        return true;
    }

    public static boolean isBetaApp() {
        return DEBUG_VERSION;
    }
}
