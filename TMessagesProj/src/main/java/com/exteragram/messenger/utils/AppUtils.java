/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils;

import android.annotation.SuppressLint;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.PackageInfoCompat;

import com.exteragram.messenger.utils.text.LocaleUtils;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.Calendar;

public class AppUtils {

    // do not change or remove this part of the code if you're making public fork
    private static final String EXPECTED_SIGNATURE = "VdBS+IkXbbu+mQuHS4vyXw==";
    private static final String EXPECTED_PACKAGE_NAME = "com.exteragram.messenger";

    private static Gson gson;

    public static Gson getGson() {
        if (gson == null) {
            gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .serializeSpecialFloatingPointValues()
                    .addSerializationExclusionStrategy(new ExclusionStrategy() {
                        @Override
                        public boolean shouldSkipField(FieldAttributes f) {
                            if (f.getDeclaringClass().getPackage() == null) {
                                return false;
                            }
                            String packageName = f.getDeclaringClass().getPackage().getName();
                            return packageName.startsWith("android.") ||
                                    packageName.startsWith("androidx.");
                        }

                        @Override
                        public boolean shouldSkipClass(Class<?> clazz) {
                            if (clazz.getPackage() == null) {
                                return false;
                            }
                            String packageName = clazz.getPackage().getName();
                            return packageName.startsWith("android.") ||
                                    packageName.startsWith("androidx.");
                        }
                    })
                    .create();
        }
        return gson;
    }

    public static int getNotificationColor() {
        if (true) { // force for now
            int color = 0;
            if (Theme.getActiveTheme().hasAccentColors()) {
                color = Theme.getActiveTheme().getAccentColor(Theme.getActiveTheme().currentAccentId);
            }
            if (color == 0) {
                color = Theme.getColor(Theme.key_actionBarDefault) | 0xff000000;
            }
            float brightness = AndroidUtilities.computePerceivedBrightness(color);
            if (brightness >= 0.721f || brightness <= 0.279f) {
                color = Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader) | 0xff000000;
            }
            return color;
        } else {
            return ContextCompat.getColor(ApplicationLoader.applicationContext, R.color.ic_background);
        }
    }

    public static boolean isWinter() {
        Calendar calendar = Calendar.getInstance();
        int currentMonth = calendar.get(Calendar.MONTH);
        return currentMonth == Calendar.DECEMBER || currentMonth == Calendar.JANUARY || currentMonth == Calendar.FEBRUARY;
    }

    public static int getSwipeVelocity() {
        return AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? 1250 : 850;
    }

    @SuppressWarnings("deprecation")
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

    @Keep
    public static void log(final String msg) {
        logInternal(msg, null, 5);
    }

    @Keep
    public static void log(final Throwable throwable) {
        logInternal("", throwable, 5);
    }

    @Keep
    public static void log(final String msg, final Throwable throwable) {
        logInternal(msg, throwable, 5);
    }

    private static void logInternal(final String msg, final Throwable throwable, final int stackDepth) {
        var stackTrace = Thread.currentThread().getStackTrace();
        int index = Math.min(stackDepth, stackTrace.length - 1);
        index = Math.max(3, index);

        StackTraceElement stackTraceElement = stackTrace[index];
        String className = stackTraceElement.getClassName();
        if (className.contains(".")) {
            className = className.substring(className.lastIndexOf('.') + 1);
        }
        if (className.contains("$")) {
            className = className.substring(className.lastIndexOf('$') + 1);
        }

        String methodName = stackTraceElement.getMethodName();
        String tag = "[" + className + "]";
        String logMessage = String.format("[%s] %s", methodName, msg);
        if (throwable != null) {
            Log.e(tag, logMessage, throwable);
        } else {
            Log.d(tag, logMessage);
        }
    }

    @Keep
    public static void printObjectDetails(Object obj) {
        if (obj == null) {
            return;
        }
        try {
            logInternal(obj.getClass().getName() + ": " + getGson().toJson(obj), null, 6);
        } catch (Exception e) {
            logInternal(obj.getClass().getName(), e, 6);
        }
    }

    @Keep
    public static Object getPrivateField(Object obj, String fieldName) {
        try {
            Class<?> clazz = obj.getClass();
            Field field = null;
            Class<?> currentClass = clazz;
            while (currentClass != null) {
                try {
                    field = currentClass.getDeclaredField(fieldName);
                    break;
                } catch (NoSuchFieldException e) {
                    currentClass = currentClass.getSuperclass();
                }
            }
            if (field != null) {
                field.setAccessible(true);
                return field.get(obj);
            }
        } catch (Exception e) {
            logInternal(obj.getClass().getName(), e, 6);
        }
        return null;
    }

    @Keep
    public static void setPrivateField(Object obj, String fieldName, Object value) {
        try {
            Class<?> clazz = obj.getClass();
            Field field = null;
            Class<?> currentClass = clazz;
            while (currentClass != null) {
                try {
                    field = currentClass.getDeclaredField(fieldName);
                    break;
                } catch (NoSuchFieldException e) {
                    currentClass = currentClass.getSuperclass();
                }
            }
            if (field != null) {
                field.setAccessible(true);
                field.set(obj, value);
            }
        } catch (Exception e) {
            logInternal(obj.getClass().getName(), e, 6);
        }
    }

    @Keep
    public static Object getPrivateStaticField(Class<?> clazz, String fieldName) {
        try {
            Field field = null;
            Class<?> currentClass = clazz;
            while (currentClass != null) {
                try {
                    field = currentClass.getDeclaredField(fieldName);
                    break;
                } catch (NoSuchFieldException e) {
                    currentClass = currentClass.getSuperclass();
                }
            }
            if (field != null) {
                field.setAccessible(true);
                return field.get(null);
            }
        } catch (Exception e) {
            logInternal(clazz.getName(), e, 6);
        }
        return null;
    }

    @Keep
    public static void setPrivateStaticField(Class<?> clazz, String fieldName, Object value) {
        try {
            Field field = null;
            Class<?> currentClass = clazz;
            while (currentClass != null) {
                try {
                    field = currentClass.getDeclaredField(fieldName);
                    break;
                } catch (NoSuchFieldException e) {
                    currentClass = currentClass.getSuperclass();
                }
            }
            if (field != null) {
                field.setAccessible(true);
                field.set(null, value);
            }
        } catch (Exception e) {
            logInternal(clazz.getName(), e, 6);
        }
    }

    public static String stackTraceToString(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }

    public static String getVersionText() {
        StringBuilder builder = new StringBuilder();

        builder.append(LocaleUtils.getAppName())
                .append(" ");

        if (BuildVars.IS_LITE_VERSION) {
            builder.append("Lite ");
        }

        builder.append(BuildVars.BUILD_VERSION_STRING);

        try {
            PackageInfo info = ApplicationLoader.applicationContext
                    .getPackageManager()
                    .getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            builder.append(" (").append(PackageInfoCompat.getLongVersionCode(info)).append(")");
        } catch (PackageManager.NameNotFoundException e) {
            FileLog.e(e);
        }

        if (AppUtils.isAppModified()) {
            builder.append("\nbased on @exteraGram");
        }

        return builder.toString();
    }

    public static boolean compareVersions(String operator, String version1, String version2) {
        int cmp = compareVersionValues(version1, version2);

        return switch (operator) {
            case "==" -> cmp == 0;
            case ">"  -> cmp > 0;
            case "<"  -> cmp < 0;
            case ">=" -> cmp >= 0;
            case "<=" -> cmp <= 0;
            default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
        };
    }

    public static boolean compareVersions(String operator, int version1, int version2) {
        int cmp = Integer.compare(version1, version2);

        return switch (operator) {
            case "==" -> cmp == 0;
            case ">"  -> cmp > 0;
            case "<"  -> cmp < 0;
            case ">=" -> cmp >= 0;
            case "<=" -> cmp <= 0;
            default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
        };
    }

    public static int compareVersionValues(String version1, String version2) {
        String[] parts1 = version1.split("\\.");
        String[] parts2 = version2.split("\\.");

        int maxLength = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < maxLength; i++) {
            int v1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int v2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;

            if (v1 != v2) {
                return Integer.compare(v1, v2);
            }
        }

        return 0;
    }
}