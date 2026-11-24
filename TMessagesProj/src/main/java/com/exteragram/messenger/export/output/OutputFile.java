/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export.output;

import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class OutputFile {

    private final File file;
    private final Stats stats;
    private boolean opened;
    private long offset;

    public OutputFile(String path, Stats stats) {
        this.file = new File(path);
        this.stats = stats;
    }

    public File file() {
        return file;
    }

    public long size() {
        return offset;
    }

    public boolean empty() {
        return offset == 0;
    }

    public Result writeBlock(String block) {
        return writeBlock(block.getBytes(StandardCharsets.UTF_8));
    }

    public Result writeBlock(byte[] block) {
        return writeBlock(ByteBuffer.wrap(block));
    }

    public Result writeBlock(ByteBuffer block) {
        if (!opened) {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                return Result.error(file.getAbsolutePath());
            }
        }
        try (FileOutputStream stream = new FileOutputStream(file, opened)) {
            FileChannel channel = stream.getChannel();
            int size = block.remaining();
            while (block.hasRemaining()) {
                channel.write(block);
            }
            if (!opened) {
                opened = true;
                if (stats != null) {
                    stats.incrementFiles();
                }
            }
            offset += size;
            if (stats != null) {
                stats.incrementBytes(size);
            }
            return Result.SUCCESS;
        } catch (IOException e) {
            FileLog.e(e);
            return Result.error(file.getAbsolutePath());
        }
    }

    public static String prepareRelativePath(String folder, String suggested) {
        if (!new File(folder + suggested).exists()) {
            return suggested;
        }
        int nameStart = suggested.lastIndexOf('/') + 1;
        int position = suggested.indexOf('.', nameStart);
        String base = position >= 0 ? suggested.substring(0, position) : suggested;
        String extension = position >= 0 ? suggested.substring(position) : "";
        for (int attempt = 1; ; attempt++) {
            String relativePath = base + " (" + attempt + ")" + extension;
            if (!new File(folder + relativePath).exists()) {
                return relativePath;
            }
        }
    }

    public static class Stats {
        private final AtomicInteger files = new AtomicInteger();
        private final AtomicLong bytes = new AtomicLong();

        public void incrementFiles() {
            files.incrementAndGet();
        }

        public void incrementBytes(long count) {
            bytes.addAndGet(count);
        }

        public int filesCount() {
            return files.get();
        }

        public long bytesCount() {
            return bytes.get();
        }
    }
}
