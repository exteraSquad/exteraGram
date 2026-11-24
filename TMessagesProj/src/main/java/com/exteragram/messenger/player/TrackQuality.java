/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player;

import android.media.MediaExtractor;
import android.media.MediaFormat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;

public final class TrackQuality {

    private static final HashMap<Long, Info> cache = new HashMap<>();

    private TrackQuality() {
    }

    public static final class Info {
        public String format;
        public int bitrateKbps;
        public int sampleRate;
        public int bitsPerSample;
        public boolean probed;

        public String formatBitrate() {
            return bitrateKbps > 0 ? LocaleController.formatString(R.string.PlayerQualityKbps, bitrateKbps) : null;
        }

        public String formatSampleRate() {
            if (sampleRate <= 0) {
                return null;
            }
            final float khz = sampleRate / 1000f;
            final String value = khz == (int) khz ? String.valueOf((int) khz) : String.format(Locale.US, "%.1f", khz);
            return LocaleController.formatString(R.string.PlayerQualityKhz, value);
        }

        public String formatBitDepth() {
            return bitsPerSample > 0 ? LocaleController.formatString(R.string.PlayerQualityBits, bitsPerSample) : null;
        }
    }

    public static Info get(MessageObject messageObject) {
        final TLRPC.Document document = messageObject != null ? messageObject.getDocument() : null;
        if (document == null) {
            return null;
        }
        final Info cached = cache.get(document.id);
        return cached != null ? cached : describe(document, messageObject.getDuration(), null);
    }

    public static void load(MessageObject messageObject, Utilities.Callback<Info> callback) {
        final TLRPC.Document document = messageObject != null ? messageObject.getDocument() : null;
        if (document == null || cache.containsKey(document.id)) {
            return;
        }
        final File file = PlayerActions.getLocalFile(messageObject);
        if (file == null || !file.exists()) {
            return;
        }
        final double duration = messageObject.getDuration();
        Utilities.globalQueue.postRunnable(() -> {
            final Probe probe = probe(file);
            AndroidUtilities.runOnUIThread(() -> {
                final Info info = describe(document, duration, probe);
                cache.put(document.id, info);
                callback.run(info);
            });
        });
    }

    private static Info describe(TLRPC.Document document, double duration, Probe probe) {
        final Info info = new Info();
        info.probed = probe != null;
        info.format = probe != null && probe.format != null ? probe.format : formatFromDocument(document);
        long bitrate = probe != null ? probe.bitrate : 0;
        if (bitrate <= 0 && document.size > 0 && duration > 1) {
            bitrate = (long) (document.size * 8 / duration);
        }
        info.bitrateKbps = (int) Math.round(bitrate / 1000.0);
        if (probe != null) {
            info.sampleRate = probe.sampleRate;
            info.bitsPerSample = probe.bitsPerSample;
        }
        return info;
    }

    private static String formatFromDocument(TLRPC.Document document) {
        final String name = FileLoader.getDocumentFileName(document);
        final int dot = name != null ? name.lastIndexOf('.') : -1;
        if (dot >= 0 && dot < name.length() - 1) {
            final String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            return switch (ext) {
                case "m4a", "aac" -> "AAC";
                case "oga", "ogg" -> "OGG";
                default -> ext.length() <= 4 ? ext.toUpperCase(Locale.ROOT) : null;
            };
        }
        return formatFromMime(document.mime_type);
    }

    private static String formatFromMime(String mime) {
        if (mime == null) {
            return null;
        }
        return switch (mime.toLowerCase(Locale.ROOT)) {
            case "audio/mpeg", "audio/mp3" -> "MP3";
            case "audio/flac", "audio/x-flac" -> "FLAC";
            case "audio/mp4a-latm", "audio/aac", "audio/mp4", "audio/x-m4a" -> "AAC";
            case "audio/alac" -> "ALAC";
            case "audio/opus" -> "Opus";
            case "audio/vorbis" -> "Vorbis";
            case "audio/ogg" -> "OGG";
            case "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave" -> "WAV";
            case "audio/ac3" -> "AC3";
            case "audio/eac3" -> "E-AC3";
            default -> null;
        };
    }

    private static Probe probe(File file) {
        final Probe probe = new Probe();
        final MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getAbsolutePath());
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                final MediaFormat format = extractor.getTrackFormat(i);
                final String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime == null || !mime.startsWith("audio/")) {
                    continue;
                }
                probe.format = "audio/raw".equals(mime) ? formatFromHeader(file) : formatFromMime(mime);
                if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    probe.sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                }
                if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                    probe.bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE);
                }
                break;
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            extractor.release();
        }
        if ("FLAC".equals(probe.format)) {
            probe.bitsPerSample = readFlacBitsPerSample(file);
        }
        return probe;
    }

    private static String formatFromHeader(File file) {
        try (InputStream in = new FileInputStream(file)) {
            final byte[] header = new byte[12];
            if (in.read(header) != header.length) {
                return null;
            }
            if (header[0] == 'f' && header[1] == 'L' && header[2] == 'a' && header[3] == 'C') {
                return "FLAC";
            }
            if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F' && header[8] == 'W' && header[9] == 'A' && header[10] == 'V' && header[11] == 'E') {
                return "WAV";
            }
            if (header[0] == 'F' && header[1] == 'O' && header[2] == 'R' && header[3] == 'M' && header[8] == 'A' && header[9] == 'I' && header[10] == 'F') {
                return "AIFF";
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    private static int readFlacBitsPerSample(File file) {
        try (InputStream in = new FileInputStream(file)) {
            final byte[] header = new byte[4 + 4 + 18];
            if (in.read(header) != header.length || header[0] != 'f' || header[1] != 'L' || header[2] != 'a' || header[3] != 'C' || (header[4] & 0x7F) != 0) {
                return 0;
            }
            final int packed = ((header[8 + 12] & 0xFF) << 8) | (header[8 + 13] & 0xFF);
            return ((packed >> 4) & 0x1F) + 1;
        } catch (Exception e) {
            FileLog.e(e);
            return 0;
        }
    }

    private static final class Probe {
        String format;
        int sampleRate;
        long bitrate;
        int bitsPerSample;
    }
}
