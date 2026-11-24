/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.style.ReplacementSpan;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.web.WebInstantView;

import java.util.Locale;

public class TextPaintImageReceiverSpan extends ReplacementSpan {

    private ImageReceiver imageReceiver;
    private int width;
    private int height;
    private boolean alignTop;
    private boolean baselineMode;
    private int depth;

    private int contentWidth;
    private int scrollX;
    private float lastDrawX;
    private int lastDrawTop;
    private boolean lastDrawn;

    public boolean isScrollable() {
        return contentWidth > width;
    }

    public int getMaxScroll() {
        return Math.max(0, contentWidth - width);
    }

    public void setScroll(int value) {
        scrollX = Utilities.clamp(value, getMaxScroll(), 0);
    }

    public int getScroll() {
        return scrollX;
    }

    public void setScrollableContentWidth(int fullWidth) {
        contentWidth = fullWidth;
    }

    public boolean containsPoint(float px, float py) {
        return lastDrawn && isScrollable()
            && px >= lastDrawX && px <= lastDrawX + width
            && py >= lastDrawTop && py <= lastDrawTop + height;
    }

    public TextPaintImageReceiverSpan(View parentView, TLRPC.Document document, Object parentObject, int w, int h, boolean top, boolean invert) {
        String filter = String.format(Locale.US, "%d_%d_i", w, h);
        width = w;
        height = h;
        contentWidth = w;
        imageReceiver = new ImageReceiver(parentView);
        imageReceiver.setInvalidateAll(true);
        if (invert) {
            imageReceiver.setDelegate((imageReceiver, set, thumb, memCache) -> {
                if (!imageReceiver.canInvertBitmap()) {
                    return;
                }
                imageReceiver.setColorFilter(new ColorMatrixColorFilter(new float[] {
                    -1.0f, 0, 0, 0, 255,
                    0, -1.0f, 0, 0, 255,
                    0, 0, -1.0f, 0, 255,
                    0, 0, 0, 1.0f, 0
                }));
            });
        }
        TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
        imageReceiver.setImage(ImageLocation.getForDocument(document), filter, ImageLocation.getForDocument(thumb, document), filter, -1, null, parentObject, 1);
        alignTop = top;
    }

    public TextPaintImageReceiverSpan(View parentView, WebInstantView.WebPhoto webPhoto, Object parentObject, int w, int h, boolean top, boolean invert) {
        width = w;
        height = h;
        contentWidth = w;
        imageReceiver = new ImageReceiver(parentView);
        imageReceiver.setInvalidateAll(true);
        if (invert) {
            imageReceiver.setDelegate((imageReceiver, set, thumb, memCache) -> {
                if (!imageReceiver.canInvertBitmap()) {
                    return;
                }
                imageReceiver.setColorFilter(new ColorMatrixColorFilter(new float[] {
                    -1.0f, 0, 0, 0, 255,
                    0, -1.0f, 0, 0, 255,
                    0, 0, -1.0f, 0, 255,
                    0, 0, 0, 1.0f, 0
                }));
            });
        }
        WebInstantView.loadPhoto(webPhoto, imageReceiver, () -> {});
        alignTop = top;
    }

    public TextPaintImageReceiverSpan(View parentView, Bitmap bitmap, int w, int h, boolean top, int color) {
        width = w;
        height = h;
        contentWidth = w;
        imageReceiver = new ImageReceiver(parentView);
        imageReceiver.setInvalidateAll(true);
        imageReceiver.setImageBitmap(bitmap);
        imageReceiver.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        alignTop = top;
    }

    public TextPaintImageReceiverSpan(View parentView, Bitmap bitmap, int w, int h, int color, int depth) {
        width = w;
        height = h;
        contentWidth = w;
        imageReceiver = new ImageReceiver(parentView);
        imageReceiver.setInvalidateAll(true);
        imageReceiver.setImageBitmap(bitmap);
        imageReceiver.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        this.depth = depth;
        this.baselineMode = true;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        if (fm != null) {
            if (baselineMode) {
                fm.top = fm.ascent = -(height - depth);
                fm.bottom = fm.descent = depth;
            } else if (alignTop) {
                int h = fm.descent - fm.ascent - AndroidUtilities.dp(4);
                fm.bottom = fm.descent = height - h;
                fm.top = fm.ascent = 0 - h;
            } else {
                fm.top = fm.ascent = (-height / 2) - AndroidUtilities.dp(4);
                fm.bottom = fm.descent = height - (height / 2) - AndroidUtilities.dp(4);
            }
        }
        return width;
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        canvas.save();
        if (baselineMode) {
            final int topY = y - (height - depth);
            lastDrawX = x;
            lastDrawTop = topY;
            lastDrawn = true;
            if (contentWidth > width) {
                canvas.clipRect(x, topY, x + width, topY + height);
                imageReceiver.setImageCoords((int) (x - scrollX), topY, contentWidth, height);
            } else {
                imageReceiver.setImageCoords((int) x, topY, width, height);
            }
        } else if (alignTop) {
            imageReceiver.setImageCoords((int) x, top - 1, width, height);
        } else {
            int h = (bottom - AndroidUtilities.dp(4)) - top;
            imageReceiver.setImageCoords((int) x, top + (h - height) / 2, width, height);
        }
        imageReceiver.draw(canvas);
        canvas.restore();
    }
}