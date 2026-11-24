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

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class OpusFingerprint {

    private static final int MAX_SCAN_SIZE = 256 * 1024;
    private static final int MAX_PACKET_SIZE = 64 * 1024;

    public final String vendor;

    private OpusFingerprint(String vendor) {
        this.vendor = vendor;
    }

    @Nullable
    public static OpusFingerprint parse(@NonNull String filePath) {
        try (DataInputStream stream = new DataInputStream(new BufferedInputStream(new FileInputStream(filePath)))) {
            byte[] packet = new byte[MAX_PACKET_SIZE];
            byte[] laces = new byte[255];
            int packetSize = 0;
            boolean headerRead = false;
            int scanned = 0;
            int serial = 0;
            int sequence = 0;
            boolean continued = false;
            while (scanned + 27 <= MAX_SCAN_SIZE) {
                if (stream.readInt() != 0x4F676753 || stream.readUnsignedByte() != 0) {
                    return null;
                }
                int flags = stream.readUnsignedByte();
                stream.readLong();
                int pageSerial = stream.readInt();
                int pageSequence = Integer.reverseBytes(stream.readInt());
                stream.readInt();
                int segmentCount = stream.readUnsignedByte();
                if (scanned == 0) {
                    if (flags != 2 || pageSequence != 0) {
                        return null;
                    }
                    serial = pageSerial;
                } else if (pageSerial != serial || pageSequence != sequence || (flags & 2) != 0) {
                    return null;
                }
                if ((flags & 1) != (continued ? 1 : 0) || (flags & ~7) != 0) {
                    return null;
                }
                sequence = pageSequence + 1;
                scanned += 27 + segmentCount;
                if (scanned > MAX_SCAN_SIZE) {
                    return null;
                }
                stream.readFully(laces, 0, segmentCount);
                for (int i = 0; i < segmentCount; i++) {
                    int length = laces[i] & 0xFF;
                    if (packetSize + length > MAX_PACKET_SIZE || scanned + length > MAX_SCAN_SIZE) {
                        return null;
                    }
                    stream.readFully(packet, packetSize, length);
                    packetSize += length;
                    scanned += length;
                    continued = length == 255;
                    if (continued) {
                        continue;
                    }
                    if (!headerRead) {
                        if (packetSize < 19 || !new String(packet, 0, 8, StandardCharsets.US_ASCII).equals("OpusHead")
                                || (packet[8] & 0xFF) > 15 || packet[9] == 0) {
                            return null;
                        }
                        headerRead = true;
                        packetSize = 0;
                    } else {
                        if (packetSize < 16 || !new String(packet, 0, 8, StandardCharsets.US_ASCII).equals("OpusTags")) {
                            return null;
                        }
                        ByteBuffer tags = ByteBuffer.wrap(packet, 8, packetSize - 8).order(ByteOrder.LITTLE_ENDIAN);
                        int vendorLength = tags.getInt();
                        if (vendorLength < 0 || vendorLength > tags.remaining() - 4) {
                            return null;
                        }
                        String vendor = new String(packet, tags.position(), vendorLength, StandardCharsets.UTF_8);
                        tags.position(tags.position() + vendorLength);
                        int comments = tags.getInt();
                        if (comments < 0 || comments > tags.remaining() / 4) {
                            return null;
                        }
                        for (int comment = 0; comment < comments; comment++) {
                            if (tags.remaining() < 4) {
                                return null;
                            }
                            int commentLength = tags.getInt();
                            if (commentLength < 0 || commentLength > tags.remaining()) {
                                return null;
                            }
                            tags.position(tags.position() + commentLength);
                        }
                        return new OpusFingerprint(vendor);
                    }
                }
                if ((flags & 4) != 0) {
                    return null;
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }
}
