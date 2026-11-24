/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.messageexport.render;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;

import com.exteragram.messenger.messageexport.appearance.MessageExportBackground;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.util.ArrayDeque;

final class MessageExportJob {

    interface Callback {
        void onDone(File file);

        void onError(boolean noSpace);
    }

    private static final int STRIP_HEIGHT = 1024;
    private static final int OVERDRAW = 64;
    private static final long CONTENT_WAIT_MS = 8000;
    private static final long CONTENT_RETRY_MS = 50;

    private final MessageExportLayout layout;
    private final MessageExportRenderHost host;
    private final MessageExportBackground background;
    private final MessageExportWatermark watermark;
    private final Theme.ResourcesProvider provider;
    private final Callback callback;
    private final float scale;
    private final int backgroundPeriod;
    private final int outputWidth;
    private final long outputHeight;
    private final File file;

    private final DispatchQueue writerQueue = new DispatchQueue("messageExportWriter");
    private final ArrayDeque<Bitmap> freeStrips = new ArrayDeque<>(2);
    private final Bitmap[] strips = new Bitmap[2];

    private int outputTop;
    private int stripsInFlight;
    private MessageExportPngWriter writer;
    private long waitingSince;
    private boolean contentWaitExpired;
    private boolean cancelled;
    private boolean finished;
    private boolean completing;
    private boolean retryScheduled;
    private final Runnable retryRender = this::retryRenderStep;

    MessageExportJob(MessageExportLayout layout, MessageExportRenderHost host, MessageExportBackground background, MessageExportWatermark watermark, Theme.ResourcesProvider provider, int backgroundPeriod, float scale, File file, Callback callback) {
        this.layout = layout;
        this.host = host;
        this.background = background;
        this.watermark = watermark;
        this.provider = provider;
        this.backgroundPeriod = backgroundPeriod;
        this.scale = scale;
        this.file = file;
        this.callback = callback;
        this.outputWidth = Math.max(1, Math.round(layout.width * scale));
        this.outputHeight = Math.max(1, (long) Math.ceil(layout.totalHeight * (double) scale));
    }

    void start() {
        if (outputHeight > Integer.MAX_VALUE) {
            fail(new IllegalStateException("Image is too tall: " + outputHeight), false);
            return;
        }
        try {
            int stripHeight = (int) Math.min(STRIP_HEIGHT, outputHeight);
            for (int i = 0; i < strips.length; i++) {
                strips[i] = Bitmap.createBitmap(outputWidth, stripHeight, Bitmap.Config.ARGB_8888);
                freeStrips.add(strips[i]);
            }
        } catch (Throwable e) {
            fail(e, false);
            return;
        }
        int height = (int) outputHeight;
        writerQueue.postRunnable(() -> {
            if (!hasEnoughSpace()) {
                AndroidUtilities.runOnUIThread(() -> fail(new IllegalStateException("Not enough storage"), true));
                return;
            }
            try {
                writer = new MessageExportPngWriter(file, outputWidth, height, background == null);
                AndroidUtilities.runOnUIThread(this::renderStep);
            } catch (Throwable e) {
                AndroidUtilities.runOnUIThread(() -> fail(e, isNoSpace(e)));
            }
        });
    }

    private boolean hasEnoughSpace() {
        File directory = file.getParentFile();
        while (directory != null && !directory.exists()) {
            directory = directory.getParentFile();
        }
        if (directory == null) {
            return true;
        }
        long usable = directory.getUsableSpace();
        return usable <= 0 || usable >= outputWidth * outputHeight / 8;
    }

    void cancel() {
        if (finished) {
            return;
        }
        cancelled = true;
        finished = true;
        AndroidUtilities.cancelRunOnUIThread(retryRender);
        writerQueue.postRunnable(() -> {
            if (writer != null) {
                writer.abort();
                writer = null;
            }
            file.delete();
            writerQueue.recycle();
        });
        releaseStrips();
    }

    private void renderStep() {
        if (cancelled || completing || finished) {
            return;
        }
        if (outputTop >= outputHeight) {
            if (stripsInFlight == 0) {
                complete();
            }
            return;
        }
        Bitmap strip = freeStrips.poll();
        if (strip == null) {
            return;
        }
        int rows = (int) Math.min(strip.getHeight(), outputHeight - outputTop);
        float logicalTop = outputTop / scale;
        float logicalHeight = rows / scale;
        float from = logicalTop - OVERDRAW;
        float to = logicalTop + logicalHeight + OVERDRAW;
        long now = SystemClock.elapsedRealtime();
        for (int i = 0; i < layout.blocks.size(); i++) {
            if (layout.tops[i] + layout.heights[i] < from || layout.tops[i] > to) {
                continue;
            }
            MessageExportListView view = host.obtain(layout.blocks.get(i), layout.width);
            if (!contentWaitExpired && !view.isContentReady()) {
                if (waitingSince == 0) {
                    waitingSince = now;
                }
                if (now - waitingSince < CONTENT_WAIT_MS) {
                    freeStrips.addFirst(strip);
                    if (!retryScheduled) {
                        retryScheduled = true;
                        AndroidUtilities.runOnUIThread(retryRender, CONTENT_RETRY_MS);
                    }
                    return;
                }
                contentWaitExpired = true;
            }
        }
        waitingSince = 0;
        try {
            Canvas canvas = new Canvas(strip);
            canvas.save();
            canvas.scale(scale, scale);
            if (background != null) {
                background.draw(canvas, layout.width, logicalTop, logicalHeight, backgroundPeriod);
            } else {
                strip.eraseColor(0);
            }
            canvas.translate(0, -logicalTop);
            for (int i = 0; i < layout.blocks.size(); i++) {
                if (layout.tops[i] + layout.heights[i] < from || layout.tops[i] > to) {
                    continue;
                }
                MessageExportListView view = host.obtain(layout.blocks.get(i), layout.width);
                canvas.save();
                canvas.translate(0, layout.tops[i]);
                view.setServiceOrigin(layout.tops[i]);
                view.draw(canvas);
                canvas.restore();
            }
            watermark.draw(canvas, layout.width, layout.contentBottom, provider);
            canvas.restore();
            canvas.setBitmap(null);
        } catch (Throwable e) {
            fail(e, false);
            return;
        }
        outputTop += rows;
        stripsInFlight++;
        writerQueue.postRunnable(() -> writeStrip(strip, rows));
        AndroidUtilities.runOnUIThread(this::renderStep);
    }

    private void retryRenderStep() {
        retryScheduled = false;
        renderStep();
    }

    private void writeStrip(Bitmap strip, int rows) {
        Throwable error = null;
        try {
            if (writer != null) {
                int[] row = new int[outputWidth];
                for (int y = 0; y < rows; y++) {
                    strip.getPixels(row, 0, outputWidth, 0, y, outputWidth, 1);
                    writer.writeRow(row);
                }
            }
        } catch (Throwable e) {
            error = e;
        }
        Throwable result = error;
        AndroidUtilities.runOnUIThread(() -> {
            stripsInFlight--;
            if (cancelled) {
                return;
            }
            if (result != null) {
                fail(result, isNoSpace(result));
                return;
            }
            freeStrips.add(strip);
            renderStep();
        });
    }

    private void complete() {
        if (completing) {
            return;
        }
        completing = true;
        AndroidUtilities.cancelRunOnUIThread(retryRender);
        writerQueue.postRunnable(() -> {
            Throwable error = null;
            MessageExportPngWriter current = writer;
            writer = null;
            if (current == null) {
                error = new IllegalStateException("Writer is gone");
            } else {
                try {
                    current.finish();
                } catch (Throwable e) {
                    error = e;
                    current.abort();
                }
            }
            Throwable result = error;
            AndroidUtilities.runOnUIThread(() -> {
                if (cancelled) {
                    return;
                }
                if (result != null) {
                    fail(result, isNoSpace(result));
                    return;
                }
                finished = true;
                writerQueue.postRunnable(writerQueue::recycle);
                releaseStrips();
                callback.onDone(file);
            });
        });
    }

    private void fail(Throwable error, boolean noSpace) {
        FileLog.e(error);
        if (finished) {
            return;
        }
        cancel();
        callback.onError(noSpace);
    }

    private static boolean isNoSpace(Throwable error) {
        String message = error != null ? error.getMessage() : null;
        return message != null && (message.contains("ENOSPC") || message.contains("No space"));
    }

    private void releaseStrips() {
        freeStrips.clear();
        for (int i = 0; i < strips.length; i++) {
            Bitmap strip = strips[i];
            strips[i] = null;
            if (strip != null && stripsInFlight == 0) {
                strip.recycle();
            }
        }
    }
}
