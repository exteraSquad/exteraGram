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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class Mp4Fingerprint {

    private static final int MAX_BOXES = 4096;

    public String majorBrand;
    public long minorVersion;
    public final ArrayList<String> compatibleBrands = new ArrayList<>();
    public String videoHandler;
    public String audioHandler;
    public String videoLanguage;
    public String audioLanguage;
    public String encoder;
    public boolean fragmented;

    private int boxes;
    private boolean movie;

    private static class Track {
        String type;
        String name;
        String language;
        boolean media;
    }

    @Nullable
    public static Mp4Fingerprint parse(@NonNull String filePath) {
        try (RandomAccessFile file = new RandomAccessFile(filePath, "r")) {
            Mp4Fingerprint fingerprint = new Mp4Fingerprint();
            fingerprint.readBoxes(file, file.length(), "", null);
            return fingerprint.majorBrand != null && fingerprint.movie
                    && fingerprint.videoHandler != null && fingerprint.audioHandler != null
                    && fingerprint.videoLanguage != null && fingerprint.audioLanguage != null ? fingerprint : null;
        } catch (IOException ignored) {
            return null;
        }
    }

    private void readBoxes(RandomAccessFile file, long end, String parent, Track track) throws IOException {
        while (file.getFilePointer() < end) {
            long start = file.getFilePointer();
            if (++boxes > MAX_BOXES || end - start < 8) {
                throw new IOException();
            }
            long size = file.readInt() & 0xFFFFFFFFL;
            byte[] typeBytes = new byte[4];
            file.readFully(typeBytes);
            String type = new String(typeBytes, StandardCharsets.ISO_8859_1);
            int header = 8;
            if (size == 1) {
                if (end - start < 16) {
                    throw new IOException();
                }
                size = file.readLong();
                header = 16;
            } else if (size == 0) {
                size = end - start;
            }
            if (size < header || size > end - start) {
                throw new IOException();
            }
            long boxEnd = start + size;
            long payload = size - header;
            if (type.equals("ftyp") && parent.isEmpty()) {
                if (majorBrand != null || payload < 8 || payload > 256 || payload % 4 != 0) {
                    throw new IOException();
                }
                file.readFully(typeBytes);
                majorBrand = new String(typeBytes, StandardCharsets.ISO_8859_1);
                minorVersion = file.readInt() & 0xFFFFFFFFL;
                while (file.getFilePointer() < boxEnd) {
                    file.readFully(typeBytes);
                    compatibleBrands.add(new String(typeBytes, StandardCharsets.ISO_8859_1));
                }
            } else if (type.equals("moov") && parent.isEmpty()) {
                if (movie) {
                    throw new IOException();
                }
                movie = true;
                readBoxes(file, boxEnd, type, null);
            } else if (type.equals("trak") && parent.equals("moov")) {
                Track next = new Track();
                readBoxes(file, boxEnd, type, next);
                if ("vide".equals(next.type)) {
                    if (videoHandler != null) {
                        throw new IOException();
                    }
                    videoHandler = next.name;
                    videoLanguage = next.language;
                } else if ("soun".equals(next.type)) {
                    if (audioHandler != null) {
                        throw new IOException();
                    }
                    audioHandler = next.name;
                    audioLanguage = next.language;
                }
            } else if (type.equals("mdia") && parent.equals("trak")) {
                if (track.media) {
                    throw new IOException();
                }
                track.media = true;
                readBoxes(file, boxEnd, type, track);
            } else if (type.equals("hdlr") && parent.equals("mdia")) {
                if (track.type != null || payload < 24 || payload > 256 || file.readInt() != 0) {
                    throw new IOException();
                }
                file.readInt();
                file.readFully(typeBytes);
                track.type = new String(typeBytes, StandardCharsets.ISO_8859_1);
                file.skipBytes(12);
                byte[] name = new byte[(int) payload - 24];
                file.readFully(name);
                int length = 0;
                while (length < name.length && name[length] != 0) {
                    length++;
                }
                track.name = new String(name, 0, length, StandardCharsets.UTF_8);
            } else if (type.equals("mdhd") && parent.equals("mdia")) {
                if (track.language != null || payload < 24) {
                    throw new IOException();
                }
                int version = file.readInt();
                if (version != 0 && version != 0x01000000 || payload < (version == 0 ? 24 : 36)) {
                    throw new IOException();
                }
                file.skipBytes(version == 0 ? 16 : 28);
                int language = file.readUnsignedShort();
                track.language = new String(new char[]{
                        (char) (((language >> 10) & 31) + 96),
                        (char) (((language >> 5) & 31) + 96),
                        (char) ((language & 31) + 96)
                });
            } else if (type.equals("udta") && parent.equals("moov")) {
                readBoxes(file, boxEnd, type, null);
            } else if (type.equals("meta") && (parent.equals("udta") || parent.equals("moov"))) {
                if (payload < 4 || file.readInt() != 0) {
                    throw new IOException();
                }
                readBoxes(file, boxEnd, type, null);
            } else if (type.equals("ilst") && parent.equals("meta")
                    || type.equals("©too") && parent.equals("ilst")) {
                readBoxes(file, boxEnd, type, null);
            } else if (type.equals("data") && parent.equals("©too")) {
                if (encoder != null || payload < 8 || payload > 1024 || file.readInt() != 1) {
                    throw new IOException();
                }
                file.readInt();
                byte[] value = new byte[(int) payload - 8];
                file.readFully(value);
                encoder = new String(value, StandardCharsets.UTF_8);
            } else if (type.equals("mvex") || type.equals("moof")) {
                fragmented = true;
            }
            file.seek(boxEnd);
        }
    }

    public String describe() {
        return "ftyp=" + majorBrand + "/" + minorVersion + "/" + String.join(",", compatibleBrands)
                + " video=" + videoHandler + "/" + videoLanguage
                + " audio=" + audioHandler + "/" + audioLanguage
                + " encoder=" + encoder + " fragmented=" + fragmented;
    }
}
