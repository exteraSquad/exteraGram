/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.profilecolor;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;

import androidx.core.graphics.ColorUtils;

public final class ProfilePhotoFade {

    private static final float[] POSITIONS = {0f, 0.2f, 0.42f, 0.64f, 0.84f, 1f};
    private static final float[] ALPHAS = {0f, 0.07f, 0.3f, 0.64f, 0.9f, 1f};

    private final Paint paint = new Paint(Paint.DITHER_FLAG);
    private final int[] colors = new int[POSITIONS.length];
    private int shaderColor;
    private int shaderTop;
    private int shaderBottom;

    public void draw(Canvas canvas, int width, int height, int actionsHeight, int pageColor, float alpha) {
        if (alpha <= 0f || width <= 0 || height <= 0) {
            return;
        }
        pageColor = ColorUtils.setAlphaComponent(pageColor, 0xFF);
        final int top = Math.max(0, height - actionsHeight - dp(96));
        if (paint.getShader() == null || shaderColor != pageColor || shaderTop != top || shaderBottom != height) {
            shaderColor = pageColor;
            shaderTop = top;
            shaderBottom = height;
            for (int i = 0; i < colors.length; ++i) {
                colors[i] = ColorUtils.setAlphaComponent(pageColor, Math.round(0xFF * ALPHAS[i]));
            }
            paint.setShader(new LinearGradient(0, top, 0, height, colors, POSITIONS, Shader.TileMode.CLAMP));
        }
        paint.setAlpha(Math.round(0xFF * Math.min(1f, alpha)));
        canvas.drawRect(0, top, width, height, paint);
    }
}
