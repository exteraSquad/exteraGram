/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import org.telegram.messenger.AndroidUtilities;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MediaUtils {

    private static final String[] GEO_TAGS = new String[]{
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD
    };

    private static final String[] GEO_TAGS_API_24 = new String[]{
            ExifInterface.TAG_GPS_AREA_INFORMATION,
            ExifInterface.TAG_GPS_DOP,
            ExifInterface.TAG_GPS_MEASURE_MODE,
            ExifInterface.TAG_GPS_SPEED_REF,
            ExifInterface.TAG_GPS_SPEED,
            ExifInterface.TAG_GPS_STATUS,
            ExifInterface.TAG_GPS_DEST_LATITUDE,
            ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
            ExifInterface.TAG_GPS_DEST_LONGITUDE,
            ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
            ExifInterface.TAG_GPS_DEST_BEARING,
            ExifInterface.TAG_GPS_DEST_BEARING_REF,
            ExifInterface.TAG_GPS_DEST_DISTANCE,
            ExifInterface.TAG_GPS_DEST_DISTANCE_REF
    };

    public static boolean removeGeolocation(@NonNull String sourcePath, @NonNull String destinationPath) throws IOException {
        File sourceFile = new File(sourcePath);
        if (!sourceFile.exists()) {
            return false;
        }

        File destinationFile = new File(destinationPath);

        if (!AndroidUtilities.copyFile(sourceFile, destinationFile)) {
            destinationFile.delete();
            throw new IOException("failed to copy " + sourcePath + " to " + destinationPath);
        }

        List<String> tagsToRemove = new ArrayList<>(Arrays.asList(GEO_TAGS));
        tagsToRemove.addAll(getApi24GeoTags());

        ExifInterface exif = new ExifInterface(destinationFile.getAbsolutePath());

        boolean metadataChanged = false;

        for (String tag : tagsToRemove) {
            if (exif.getAttribute(tag) != null) {
                try {
                    exif.setAttribute(tag, null);
                    metadataChanged = true;
                } catch (Exception ignored) {
                }
            }
        }

        if (metadataChanged) {
            try {
                exif.saveAttributes();
                return true;
            } catch (IOException e) {
                destinationFile.delete();
            }
        }
        return false;
    }

    private static List<String> getApi24GeoTags() {
        return Arrays.asList(GEO_TAGS_API_24);
    }

    @Nullable
    public static String getPhotoPlatform(@Nullable JpegFingerprint jpeg) {
        if (jpeg == null || jpeg.jfifUnits < 0) {
            return null;
        }

        boolean skia = jpeg.iccVersion == 0x0430 && jpeg.iccYear == 2016
                || jpeg.iccDescription != null && jpeg.iccDescription.startsWith("Google/Skia/");

        if (jpeg.jfifUnits == 0) {
            if (jpeg.jfifDensityX != 1) {
                return null;
            }
            if (skia) {
                return "Android";
            }
            if (jpeg.iccLength == 0) {
                if (jpeg.quality == 72) {
                    return "iOS";
                }
                if (jpeg.quality == 75) {
                    return "macOS";
                }
            }
            return null;
        }

        if (jpeg.jfifUnits == 1 && jpeg.jfifDensityX == 72) {
            if (skia) {
                return "Android";
            }
            if (jpeg.iccVersion == 0x0240) {
                return "macOS";
            }
            return jpeg.iccVersion == 0x0440 && jpeg.iccLength == 480 ? "Desktop" : null;
        }

        return jpeg.iccVersion == 0x0240 ? "macOS" : "Desktop";
    }

    @Nullable
    public static String getVoicePlatform(@Nullable OpusFingerprint opus) {
        if (opus == null) {
            return null;
        }
        return switch (opus.vendor) {
            case "libopus unknown-fixed" -> "Android";
            case "libopus 1.5.1" -> "iOS";
            case "libopus 1.3.1-fixed" -> "macOS";
            case "libopus unknown" -> "Telegram X";
            case "tweb" -> "Web K";
            case "telegram-web-a" -> "Web A";
            default -> opus.vendor.matches("Lavf[0-9]+\\.[0-9]+\\.[0-9]+") ? "Desktop" : null;
        };
    }

    @Nullable
    public static String getRoundVideoPlatform(@Nullable Mp4Fingerprint mp4) {
        if (mp4 == null || mp4.fragmented) {
            return null;
        }
        if ("isom".equals(mp4.majorBrand) && mp4.minorVersion == 512
                && mp4.compatibleBrands.size() == 4
                && mp4.compatibleBrands.containsAll(Arrays.asList("isom", "iso2", "avc1", "mp41"))) {
            if (mp4.encoder == null
                    && "VideoHandle".equals(mp4.videoHandler) && "SoundHandle".equals(mp4.audioHandler)
                    && "eng".equals(mp4.videoLanguage) && "eng".equals(mp4.audioLanguage)) {
                return "Android";
            }
            if (mp4.encoder != null && mp4.encoder.matches("Lavf[0-9]+\\.[0-9]+\\.[0-9]+")
                    && "VideoHandler".equals(mp4.videoHandler) && "SoundHandler".equals(mp4.audioHandler)
                    && "und".equals(mp4.videoLanguage) && "und".equals(mp4.audioLanguage)) {
                return "Desktop";
            }
        }
        if (mp4.encoder == null && "mp42".equals(mp4.majorBrand) && mp4.minorVersion == 1
                && mp4.compatibleBrands.size() == 3
                && mp4.compatibleBrands.containsAll(Arrays.asList("isom", "mp41", "mp42"))
                && "Core Media Video".equals(mp4.videoHandler) && "Core Media Audio".equals(mp4.audioHandler)
                && "und".equals(mp4.videoLanguage) && "und".equals(mp4.audioLanguage)) {
            return "iOS";
        }
        return null;
    }

}
