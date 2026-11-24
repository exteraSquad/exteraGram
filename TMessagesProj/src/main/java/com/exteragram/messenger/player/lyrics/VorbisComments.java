/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player.lyrics;

import org.telegram.messenger.FileLog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class VorbisComments {

    private static final int MAX_BLOCK_SIZE = 4 * 1024 * 1024;
    private static final int MAX_OGG_PAGES = 64;
    private static final String[] LYRICS_KEYS = {"LYRICS", "SYNCEDLYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS"};

    private VorbisComments() {
    }

    public static String readLyrics(File file) {
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            final byte[] magic = new byte[4];
            in.readFully(magic);
            long start = 0;
            if (magic[0] == 'I' && magic[1] == 'D' && magic[2] == '3') {
                final byte[] header = new byte[6];
                in.readFully(header);
                start = 10 + (((header[2] & 0x7F) << 21) | ((header[3] & 0x7F) << 14) | ((header[4] & 0x7F) << 7) | (header[5] & 0x7F)) + ((header[1] & 0x10) != 0 ? 10 : 0);
                in.seek(start);
                in.readFully(magic);
            }
            final byte[] block;
            if (magic[0] == 'f' && magic[1] == 'L' && magic[2] == 'a' && magic[3] == 'C') {
                block = readFlacComments(in);
            } else if (magic[0] == 'O' && magic[1] == 'g' && magic[2] == 'g' && magic[3] == 'S') {
                in.seek(start);
                block = readOggComments(in);
            } else {
                block = null;
            }
            return block != null ? findLyrics(block) : null;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static byte[] readFlacComments(RandomAccessFile in) throws IOException {
        while (true) {
            final int type = in.read();
            if (type < 0) {
                return null;
            }
            final int length = (in.read() << 16) | (in.read() << 8) | in.read();
            if (length < 0) {
                return null;
            }
            if ((type & 0x7F) == 4) {
                if (length > MAX_BLOCK_SIZE) {
                    return null;
                }
                final byte[] block = new byte[length];
                in.readFully(block);
                return block;
            }
            if ((type & 0x80) != 0) {
                return null;
            }
            in.seek(in.getFilePointer() + length);
        }
    }

    private static byte[] readOggComments(RandomAccessFile in) throws IOException {
        final ByteArrayOutputStream packet = new ByteArrayOutputStream();
        final byte[] header = new byte[27];
        final byte[] segments = new byte[255];
        int packetIndex = 0;
        for (int page = 0; page < MAX_OGG_PAGES; page++) {
            in.readFully(header);
            if (header[0] != 'O' || header[1] != 'g' || header[2] != 'g' || header[3] != 'S') {
                return null;
            }
            final int count = header[26] & 0xFF;
            in.readFully(segments, 0, count);
            for (int i = 0; i < count; i++) {
                final int size = segments[i] & 0xFF;
                if (packetIndex == 1) {
                    if (packet.size() + size > MAX_BLOCK_SIZE) {
                        return null;
                    }
                    final byte[] data = new byte[size];
                    in.readFully(data);
                    packet.write(data, 0, size);
                } else {
                    in.seek(in.getFilePointer() + size);
                }
                if (size < 255) {
                    if (packetIndex == 1) {
                        return stripOggCommentHeader(packet.toByteArray());
                    }
                    packetIndex++;
                }
            }
        }
        return null;
    }

    private static byte[] stripOggCommentHeader(byte[] packet) {
        final int skip;
        if (packet.length >= 7 && packet[0] == 3 && new String(packet, 1, 6, StandardCharsets.US_ASCII).equals("vorbis")) {
            skip = 7;
        } else if (packet.length >= 8 && new String(packet, 0, 8, StandardCharsets.US_ASCII).equals("OpusTags")) {
            skip = 8;
        } else {
            return null;
        }
        final byte[] out = new byte[packet.length - skip];
        System.arraycopy(packet, skip, out, 0, out.length);
        return out;
    }

    private static String findLyrics(byte[] block) {
        int pos = 0;
        final int vendorLength = readInt(block, pos);
        pos += 4;
        if (vendorLength < 0 || pos + vendorLength + 4 > block.length) {
            return null;
        }
        pos += vendorLength;
        final int count = readInt(block, pos);
        pos += 4;
        final String[] values = new String[LYRICS_KEYS.length];
        for (int i = 0; i < count && pos + 4 <= block.length; i++) {
            final int length = readInt(block, pos);
            pos += 4;
            if (length < 0 || pos + length > block.length) {
                break;
            }
            final String comment = new String(block, pos, length, StandardCharsets.UTF_8);
            pos += length;
            final int eq = comment.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            final String key = comment.substring(0, eq).toUpperCase(Locale.ROOT);
            for (int k = 0; k < LYRICS_KEYS.length; k++) {
                if (values[k] == null && LYRICS_KEYS[k].equals(key)) {
                    final String value = comment.substring(eq + 1).trim();
                    if (!value.isEmpty()) {
                        values[k] = value;
                    }
                }
            }
        }
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static int readInt(byte[] b, int pos) {
        if (pos + 4 > b.length) {
            return -1;
        }
        return (b[pos] & 0xFF) | ((b[pos + 1] & 0xFF) << 8) | ((b[pos + 2] & 0xFF) << 16) | ((b[pos + 3] & 0xFF) << 24);
    }
}
