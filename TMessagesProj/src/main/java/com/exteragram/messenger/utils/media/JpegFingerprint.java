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

import org.telegram.messenger.FileLog;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class JpegFingerprint {

    private static final int MAX_SCAN_SIZE = 256 * 1024;

    private static final int[] ZIGZAG = {
            0, 1, 8, 16, 9, 2, 3, 10, 17, 24, 32, 25, 18, 11, 4, 5,
            12, 19, 26, 33, 40, 48, 41, 34, 27, 20, 13, 6, 7, 14, 21, 28,
            35, 42, 49, 56, 57, 50, 43, 36, 29, 22, 15, 23, 30, 37, 44, 51,
            58, 59, 52, 45, 38, 31, 39, 46, 53, 60, 61, 54, 47, 55, 62, 63
    };

    private static final int[] STANDARD_LUMA = {
            16, 11, 10, 16, 24, 40, 51, 61,
            12, 12, 14, 19, 26, 58, 60, 55,
            14, 13, 16, 24, 40, 57, 69, 56,
            14, 17, 22, 29, 51, 87, 80, 62,
            18, 22, 37, 56, 68, 109, 103, 77,
            24, 35, 55, 64, 81, 104, 113, 92,
            49, 64, 78, 87, 103, 121, 120, 101,
            72, 92, 95, 98, 112, 100, 103, 99
    };

    public int jfifVersion = -1;
    public int jfifUnits = -1;
    public int jfifDensityX;
    public int jfifDensityY;
    public boolean jfifThumbnail;

    public boolean exif;
    public boolean xmp;

    public int iccLength;
    public int iccVersion;
    public int iccYear;
    public String iccDescription;

    public boolean quantizationTable;
    public int quality = -1;

    public boolean progressive;
    public boolean frameBeforeQuantization;
    public boolean restartInterval;

    public int width;
    public int height;
    public int components;
    public int horizontalSampling;
    public int verticalSampling;
    public int huffmanTables;

    public String markerOrder = "";

    @Nullable
    public static JpegFingerprint parse(@NonNull String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return null;
        }

        try (InputStream stream = new BufferedInputStream(new FileInputStream(file), 16384)) {
            if (stream.read() != 0xFF || stream.read() != 0xD8) {
                return null;
            }

            JpegFingerprint jpeg = new JpegFingerprint();
            StringBuilder order = new StringBuilder();
            int scanned = 2;

            while (scanned < MAX_SCAN_SIZE) {
                if (stream.read() != 0xFF) {
                    break;
                }
                int marker = stream.read();
                while (marker == 0xFF) {
                    marker = stream.read();
                    scanned++;
                }
                scanned += 2;
                if (marker < 0 || marker == 0xD9 || marker == 0xDA) {
                    break;
                }
                if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                    continue;
                }

                int high = stream.read();
                int low = stream.read();
                if (high < 0 || low < 0) {
                    break;
                }
                int length = (high << 8 | low) - 2;
                if (length < 0) {
                    break;
                }
                scanned += 2 + length;

                if (order.length() > 0) {
                    order.append('>');
                }
                order.append(String.format(Locale.US, "%02X", marker));

                boolean parsed = marker == 0xE0 || marker == 0xE1 || marker == 0xE2 || marker == 0xDB
                        || marker == 0xC4 || marker == 0xDD || (marker >= 0xC0 && marker <= 0xC2);
                if (parsed) {
                    byte[] payload = new byte[length];
                    int read = 0;
                    while (read < length) {
                        int count = stream.read(payload, read, length - read);
                        if (count < 0) {
                            break;
                        }
                        read += count;
                    }
                    if (read < length) {
                        break;
                    }
                    jpeg.readSegment(marker, payload);
                } else {
                    long skipped = 0;
                    while (skipped < length) {
                        long count = stream.skip(length - skipped);
                        if (count <= 0) {
                            break;
                        }
                        skipped += count;
                    }
                    if (skipped < length) {
                        break;
                    }
                }
            }

            jpeg.markerOrder = order.toString();
            return jpeg;
        } catch (IOException e) {
            FileLog.e(e);
            return null;
        }
    }

    private void readSegment(int marker, byte[] payload) {
        if (marker == 0xE0) {
            if (payload.length >= 14 && startsWith(payload, "JFIF")) {
                jfifVersion = (payload[5] & 0xFF) << 8 | (payload[6] & 0xFF);
                jfifUnits = payload[7] & 0xFF;
                jfifDensityX = (payload[8] & 0xFF) << 8 | (payload[9] & 0xFF);
                jfifDensityY = (payload[10] & 0xFF) << 8 | (payload[11] & 0xFF);
                jfifThumbnail = payload[12] != 0 || payload[13] != 0;
            }
        } else if (marker == 0xE1) {
            exif = exif || startsWith(payload, "Exif");
            xmp = xmp || startsWith(payload, "http://ns.adobe.com/xap/1.0/");
        } else if (marker == 0xE2) {
            if (iccLength == 0 && payload.length >= 50 && startsWith(payload, "ICC_PROFILE")) {
                iccLength = (payload[14] & 0xFF) << 24 | (payload[15] & 0xFF) << 16 | (payload[16] & 0xFF) << 8 | (payload[17] & 0xFF);
                iccVersion = (payload[22] & 0xFF) << 8 | (payload[23] & 0xFF);
                iccYear = (payload[38] & 0xFF) << 8 | (payload[39] & 0xFF);
                iccDescription = readIccDescription(payload, 14);
            }
        } else if (marker == 0xDB) {
            int offset = 0;
            while (offset < payload.length) {
                int precision = (payload[offset] & 0xF0) >> 4;
                int id = payload[offset] & 0x0F;
                int size = precision == 0 ? 64 : 128;
                offset++;
                if (offset + size > payload.length) {
                    break;
                }
                if (id == 0 && precision == 0 && !quantizationTable) {
                    quantizationTable = true;
                    quality = standardQuality(payload, offset);
                }
                offset += size;
            }
        } else if (marker == 0xC4) {
            int offset = 0;
            while (offset + 17 <= payload.length) {
                int codes = 0;
                for (int i = 0; i < 16; i++) {
                    codes += payload[offset + 1 + i] & 0xFF;
                }
                huffmanTables++;
                offset += 17 + codes;
            }
        } else if (marker == 0xDD) {
            restartInterval = payload.length >= 2 && ((payload[0] & 0xFF) << 8 | (payload[1] & 0xFF)) > 0;
        } else if (marker >= 0xC0 && marker <= 0xC2 && payload.length >= 9) {
            progressive = marker == 0xC2;
            frameBeforeQuantization = !quantizationTable;
            height = (payload[1] & 0xFF) << 8 | (payload[2] & 0xFF);
            width = (payload[3] & 0xFF) << 8 | (payload[4] & 0xFF);
            components = payload[5] & 0xFF;
            horizontalSampling = (payload[7] & 0xF0) >> 4;
            verticalSampling = payload[7] & 0x0F;
        }
    }

    private static int readInt(byte[] payload, int offset) {
        return (payload[offset] & 0xFF) << 24 | (payload[offset + 1] & 0xFF) << 16 | (payload[offset + 2] & 0xFF) << 8 | (payload[offset + 3] & 0xFF);
    }

    @Nullable
    private static String readIccDescription(byte[] payload, int profile) {
        try {
            int tags = readInt(payload, profile + 128);
            for (int i = 0; i < Math.min(tags, 64); i++) {
                int entry = profile + 132 + i * 12;
                if (entry + 12 > payload.length) {
                    break;
                }
                if (payload[entry] != 'd' || payload[entry + 1] != 'e' || payload[entry + 2] != 's' || payload[entry + 3] != 'c') {
                    continue;
                }
                int offset = profile + readInt(payload, entry + 4);
                int size = readInt(payload, entry + 8);
                if (offset < profile || size < 12 || offset + size > payload.length) {
                    break;
                }
                if (payload[offset] == 'd') {
                    int length = Math.min(readInt(payload, offset + 8) - 1, size - 12);
                    return length > 0 ? new String(payload, offset + 12, length, StandardCharsets.US_ASCII) : null;
                }
                if (payload[offset] == 'm') {
                    int length = readInt(payload, offset + 20);
                    int string = offset + readInt(payload, offset + 24);
                    if (length > 0 && string >= offset && string + length <= offset + size) {
                        return new String(payload, string, length, StandardCharsets.UTF_16BE);
                    }
                }
                break;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static int standardQuality(byte[] payload, int offset) {
        for (int quality = 100; quality >= 1; quality--) {
            int scale = quality < 50 ? 5000 / quality : 200 - quality * 2;
            boolean matched = true;
            for (int i = 0; i < 64; i++) {
                int value = (STANDARD_LUMA[ZIGZAG[i]] * scale + 50) / 100;
                if (value < 1) {
                    value = 1;
                } else if (value > 255) {
                    value = 255;
                }
                if (value != (payload[offset + i] & 0xFF)) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return quality;
            }
        }
        return -1;
    }

    private static boolean startsWith(byte[] payload, String identifier) {
        if (payload.length <= identifier.length()) {
            return false;
        }
        for (int i = 0; i < identifier.length(); i++) {
            if (payload[i] != (byte) identifier.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    public String describe() {
        StringBuilder builder = new StringBuilder();
        if (jfifUnits >= 0) {
            builder.append(String.format(Locale.US, "jfif=%d.%02d/u%d/%dx%d", jfifVersion >> 8, jfifVersion & 0xFF, jfifUnits, jfifDensityX, jfifDensityY));
        } else {
            builder.append("jfif=none");
        }
        if (jfifThumbnail) {
            builder.append("/thumb");
        }
        if (iccLength > 0) {
            builder.append(String.format(Locale.US, " icc=%d/%04x/%d/%s", iccLength, iccVersion, iccYear, iccDescription != null ? iccDescription : "?"));
        } else {
            builder.append(" icc=none");
        }
        builder.append(quality > 0 ? String.format(Locale.US, " dqt=q%d", quality) : quantizationTable ? " dqt=custom" : " dqt=none");
        builder.append(String.format(Locale.US, " sof=%s/%dc/%dx%d/%dx%d", progressive ? "prog" : "base", components, horizontalSampling, verticalSampling, width, height));
        builder.append(String.format(Locale.US, " dht=%d", huffmanTables));
        if (exif) {
            builder.append(" exif");
        }
        if (xmp) {
            builder.append(" xmp");
        }
        if (restartInterval) {
            builder.append(" dri");
        }
        builder.append(" order=").append(markerOrder);
        return builder.toString();
    }
}