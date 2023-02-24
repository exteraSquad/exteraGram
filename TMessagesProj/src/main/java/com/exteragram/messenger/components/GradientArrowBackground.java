/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;

public class GradientArrowBackground extends Drawable {

    private long lastUpdateTime = 0;
    private final float[] progress = new float[]{0.0f, -0.5f};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public GradientArrowBackground(Context context, int color) {
        super();
        paint.setColor(color);
    }

    private void update() {
        long newTime = System.currentTimeMillis();
        long dt = newTime - lastUpdateTime;
        lastUpdateTime = newTime;
        if (dt > 16) {
            dt = 16;
        }
        for (int a = 0; a < 2; a++) {
            if (progress[a] >= 1.0f) {
                progress[a] = 0.0f;
            }
            progress[a] += dt / 2000.0f;
            if (progress[a] > 1.0f) {
                progress[a] = 1.0f;
            }
        }
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        update();
    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return AndroidUtilities.dp(108);
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(108);
    }
}
