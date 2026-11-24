/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.components.supporter;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.text.TextPaint;

import org.telegram.messenger.AndroidUtilities;

public final class SupporterArrowEngraving {

    private static final int SIZE = 1024;
    private static final float CENTER_X = .4976f;
    private static final float CENTER_Y = .2813f;
    private static final float ANGLE = 19.49f;
    private static final float TEXT_SIZE = .058f;
    private static final float LETTER_SPACING = .12f;
    private static final float BLUR = .0012f;

    private static final int TEXTURE_UNIT = 3;

    private int enabledHandle;
    private int samplerHandle;
    private int texture;
    private volatile Bitmap pending;
    private boolean ready;

    public static Bitmap createMask(String text) {
        final Bitmap bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ALPHA_8);
        final Canvas canvas = new Canvas(bitmap);
        final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(AndroidUtilities.bold());
        paint.setTextSize(SIZE * TEXT_SIZE);
        paint.setLetterSpacing(LETTER_SPACING);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setMaskFilter(new BlurMaskFilter(SIZE * BLUR, BlurMaskFilter.Blur.NORMAL));
        final Paint.FontMetrics metrics = paint.getFontMetrics();
        final float x = SIZE * CENTER_X;
        final float y = SIZE * CENTER_Y;
        canvas.rotate(ANGLE, x, y);
        canvas.drawText(text, x, y - (metrics.ascent + metrics.descent) / 2f, paint);
        return bitmap;
    }

    public void init(int program) {
        enabledHandle = GLES20.glGetUniformLocation(program, "engraving");
        samplerHandle = GLES20.glGetUniformLocation(program, "u_Engraving");
    }

    public void set(Bitmap mask) {
        pending = mask;
    }

    public void apply() {
        final Bitmap mask = pending;
        if (mask != null) {
            pending = null;
            if (texture == 0) {
                final int[] textures = new int[1];
                GLES20.glGenTextures(1, textures, 0);
                texture = textures[0];
            }
            final int[] activeTexture = new int[1];
            GLES20.glGetIntegerv(GLES20.GL_ACTIVE_TEXTURE, activeTexture, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + TEXTURE_UNIT);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mask, 0);
            GLES20.glUniform1i(samplerHandle, TEXTURE_UNIT);
            GLES20.glActiveTexture(activeTexture[0]);
            ready = true;
        }
        GLES20.glUniform1f(enabledHandle, ready ? 1f : 0f);
    }

    public void destroy() {
        if (texture != 0) {
            GLES20.glDeleteTextures(1, new int[]{texture}, 0);
            texture = 0;
        }
        ready = false;
    }
}
