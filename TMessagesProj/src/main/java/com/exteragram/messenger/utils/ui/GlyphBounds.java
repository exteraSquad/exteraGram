/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;

public final class GlyphBounds {

    private static final int SAMPLE_SIZE = 96;
    private static final int ALPHA_THRESHOLD = 24;

    private static Bitmap sample;
    private static Canvas sampleCanvas;
    private static int[] pixels;

    private GlyphBounds() {
    }

    @Nullable
    public static synchronized RectF measure(@Nullable Drawable drawable) {
        if (drawable == null) {
            return null;
        }
        if (sample == null) {
            sample = Bitmap.createBitmap(SAMPLE_SIZE, SAMPLE_SIZE, Bitmap.Config.ARGB_8888);
            sampleCanvas = new Canvas(sample);
            pixels = new int[SAMPLE_SIZE * SAMPLE_SIZE];
        }
        int intrinsicWidth = drawable.getIntrinsicWidth();
        int intrinsicHeight = drawable.getIntrinsicHeight();
        float aspect = intrinsicWidth > 0 && intrinsicHeight > 0 ? intrinsicHeight / (float) intrinsicWidth : 1f;
        int width = aspect <= 1f ? SAMPLE_SIZE : Math.max(1, Math.round(SAMPLE_SIZE / aspect));
        int height = aspect <= 1f ? Math.max(1, Math.round(SAMPLE_SIZE * aspect)) : SAMPLE_SIZE;
        sample.eraseColor(Color.TRANSPARENT);
        Rect oldBounds = drawable.copyBounds();
        drawable.setBounds(0, 0, width, height);
        drawable.draw(sampleCanvas);
        drawable.setBounds(oldBounds);
        sample.getPixels(pixels, 0, width, 0, 0, width, height);
        int left = width, top = height, right = -1, bottom = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((pixels[y * width + x] >>> 24) > ALPHA_THRESHOLD) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        if (right < left || bottom < top) {
            return null;
        }
        return new RectF(left / (float) width, top / (float) height, (right + 1) / (float) width, (bottom + 1) / (float) height);
    }

    public static float maxUpscale(@Nullable Drawable drawable, float drawnWidth) {
        if (drawable instanceof BitmapDrawable bitmapDrawable && bitmapDrawable.getBitmap() != null && drawnWidth > 0) {
            return Math.max(1f, bitmapDrawable.getBitmap().getWidth() / drawnWidth);
        }
        return Float.MAX_VALUE;
    }
}
