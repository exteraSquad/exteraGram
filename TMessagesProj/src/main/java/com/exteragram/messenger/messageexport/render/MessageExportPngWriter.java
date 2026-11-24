/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.messageexport.render;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

final class MessageExportPngWriter {

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final int IDAT_CHUNK_SIZE = 256 * 1024;
    private static final byte FILTER_UP = 2;

    private final File target;
    private final File partFile;
    private final int width;
    private final int height;
    private final boolean alpha;

    private final OutputStream out;
    private final Deflater deflater = new Deflater(6);
    private final CRC32 crc = new CRC32();

    private final byte[] row;
    private final byte[] previousRow;
    private final byte[] filtered;
    private final byte[] idat = new byte[IDAT_CHUNK_SIZE];
    private int idatLength;
    private int rowsWritten;
    private boolean closed;

    MessageExportPngWriter(File target, int width, int height, boolean alpha) throws IOException {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid PNG size " + width + "x" + height);
        }
        long rowBytes = 1L + width * (alpha ? 4L : 3L);
        if (rowBytes > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("PNG row too wide: " + width);
        }
        this.target = target;
        this.partFile = new File(target.getParentFile(), target.getName() + ".part");
        this.width = width;
        this.height = height;
        this.alpha = alpha;
        row = new byte[(int) rowBytes];
        previousRow = new byte[(int) rowBytes];
        filtered = new byte[(int) rowBytes];

        File dir = target.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Cannot create " + dir);
        }
        out = new BufferedOutputStream(new FileOutputStream(partFile), 64 * 1024);
        out.write(SIGNATURE);
        byte[] header = new byte[13];
        putInt(header, 0, width);
        putInt(header, 4, height);
        header[8] = 8;
        header[9] = (byte) (alpha ? 6 : 2);
        header[10] = 0;
        header[11] = 0;
        header[12] = 0;
        writeChunk("IHDR", header, header.length);
    }

    void writeRow(int[] argb) throws IOException {
        if (closed) {
            throw new IOException("Writer is closed");
        }
        if (rowsWritten >= height) {
            throw new IOException("Too many rows");
        }
        for (int x = 0, i = 1; x < width; x++) {
            int color = argb[x];
            row[i++] = (byte) (color >> 16);
            row[i++] = (byte) (color >> 8);
            row[i++] = (byte) color;
            if (alpha) {
                row[i++] = (byte) (color >>> 24);
            }
        }
        filtered[0] = FILTER_UP;
        for (int i = 1; i < row.length; i++) {
            filtered[i] = (byte) (row[i] - previousRow[i]);
        }
        System.arraycopy(row, 0, previousRow, 0, row.length);
        deflater.setInput(filtered, 0, filtered.length);
        while (!deflater.needsInput()) {
            drainDeflater();
        }
        rowsWritten++;
    }

    void finish() throws IOException {
        if (closed) {
            throw new IOException("Writer is closed");
        }
        if (rowsWritten != height) {
            throw new IOException("Expected " + height + " rows, got " + rowsWritten);
        }
        deflater.finish();
        while (!deflater.finished()) {
            drainDeflater();
        }
        flushIdat();
        writeChunk("IEND", idat, 0);
        out.flush();
        out.close();
        deflater.end();
        closed = true;
        if (target.exists() && !target.delete()) {
            throw new IOException("Cannot replace " + target);
        }
        if (!partFile.renameTo(target)) {
            throw new IOException("Cannot rename " + partFile);
        }
    }

    void abort() {
        if (!closed) {
            closed = true;
            try {
                out.close();
            } catch (IOException ignore) {
            }
            deflater.end();
        }
        partFile.delete();
    }

    private void drainDeflater() throws IOException {
        int count = deflater.deflate(idat, idatLength, idat.length - idatLength);
        idatLength += count;
        if (idatLength == idat.length) {
            flushIdat();
        }
    }

    private void flushIdat() throws IOException {
        if (idatLength > 0) {
            writeChunk("IDAT", idat, idatLength);
            idatLength = 0;
        }
    }

    private void writeChunk(String type, byte[] data, int length) throws IOException {
        byte[] typeBytes = {(byte) type.charAt(0), (byte) type.charAt(1), (byte) type.charAt(2), (byte) type.charAt(3)};
        writeInt(length);
        out.write(typeBytes);
        if (length > 0) {
            out.write(data, 0, length);
        }
        crc.reset();
        crc.update(typeBytes);
        if (length > 0) {
            crc.update(data, 0, length);
        }
        writeInt((int) crc.getValue());
    }

    private void writeInt(int value) throws IOException {
        out.write(value >>> 24);
        out.write(value >>> 16);
        out.write(value >>> 8);
        out.write(value);
    }

    private static void putInt(byte[] buffer, int offset, int value) {
        buffer[offset] = (byte) (value >>> 24);
        buffer[offset + 1] = (byte) (value >>> 16);
        buffer[offset + 2] = (byte) (value >>> 8);
        buffer[offset + 3] = (byte) value;
    }
}
