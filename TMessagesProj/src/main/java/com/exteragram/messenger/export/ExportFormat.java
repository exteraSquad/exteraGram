/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.time.FastDateFormat;

import java.util.Locale;
import java.util.TimeZone;

public final class ExportFormat {

    private static final FastDateFormat FILE_NAME_DATE = FastDateFormat.getInstance("dd-MM-yyyy_HH-mm-ss", Locale.US);
    private static final FastDateFormat FOLDER_DATE = FastDateFormat.getInstance("yyyy-MM-dd_HH-mm-ss", Locale.US);
    private static final FastDateFormat DATE_TIME = FastDateFormat.getInstance("dd.MM.yyyy HH:mm:ss", Locale.US);
    private static final FastDateFormat ISO_DATE = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
    private static final FastDateFormat DATE_TEXT = FastDateFormat.getInstance("d MMMM yyyy", Locale.US);
    private static final FastDateFormat TIME_TEXT = FastDateFormat.getInstance("HH:mm", Locale.US);

    private ExportFormat() {
    }

    private static long millis(int date) {
        return date * 1000L;
    }

    public static String fileNameDatePart(int date) {
        return date != 0 ? "@" + FILE_NAME_DATE.format(millis(date)) : "";
    }

    public static String folderDatePart(long millis) {
        return FOLDER_DATE.format(millis);
    }

    public static String isoDate(int date) {
        return ISO_DATE.format(millis(date));
    }

    public static String dateTime(int date) {
        return date != 0 ? DATE_TIME.format(millis(date)) : "";
    }

    public static String dateTimeWithZone(int date) {
        if (date == 0) {
            return "";
        }
        int offsetMinutes = TimeZone.getDefault().getOffset(millis(date)) / 60_000;
        int absMinutes = Math.abs(offsetMinutes);
        String zone = String.format(Locale.US, "UTC%c%02d:%02d", offsetMinutes < 0 ? '-' : '+', absMinutes / 60, absMinutes % 60);
        return DATE_TIME.format(millis(date)) + " " + zone;
    }

    public static String dateText(int date) {
        return DATE_TEXT.format(millis(date));
    }

    public static String timeText(int date) {
        return TIME_TEXT.format(millis(date));
    }

    public static String duration(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;
        return hours > 0
                ? String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
                : String.format(Locale.US, "%d:%02d", minutes, secs);
    }

    public static String phoneNumber(String phone) {
        if (phone == null || phone.isEmpty()) {
            return "";
        }
        return PhoneFormat.getInstance().format(phone.startsWith("+") ? phone : "+" + phone);
    }

    public static String fileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024L * 1024) {
            return String.format(Locale.US, "%.1f KB", size / 1024.0);
        } else if (size < 1024L * 1024 * 1024) {
            return String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024));
        }
        return String.format(Locale.US, "%.2f GB", size / (1024.0 * 1024 * 1024));
    }

    public static String number(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    public static String padLeft(String value, int length, char filler) {
        if (value.length() >= length) {
            return value;
        }
        StringBuilder result = new StringBuilder(length);
        for (int i = value.length(); i < length; i++) {
            result.append(filler);
        }
        return result.append(value).toString();
    }
}
