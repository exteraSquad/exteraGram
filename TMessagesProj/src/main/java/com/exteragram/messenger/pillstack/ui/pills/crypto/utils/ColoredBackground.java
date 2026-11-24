/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.ui.pills.crypto.utils;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class ColoredBackground extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ColoredBackground() {
        this(0xFF1BA4ED, 0xFF1488E1);
    }

    public ColoredBackground(int topColor, int bottomColor) {
        LinearGradient gradient = new LinearGradient(0, 0, 0, dp(28), new int[]{topColor, bottomColor}, new float[]{0, 1}, Shader.TileMode.CLAMP);
        paint.setShader(gradient);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(1));
        LinearGradient strokeGradient = new LinearGradient(0, 0, 0, dp(28), new int[]{0x4dffffff, 0, 0x1affffff}, new float[]{0, 0.5f, 1}, Shader.TileMode.CLAMP);
        strokePaint.setShader(strokeGradient);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        float rad = dp(14);
        AndroidUtilities.rectTmp.set(bounds);
        canvas.drawRoundRect(AndroidUtilities.rectTmp, rad, rad, paint);

        if (Theme.isCurrentThemeDark() && !Theme.isCurrentThemeMonet()) {
            float sw = dp(1);
            strokePaint.setStrokeWidth(sw);
            AndroidUtilities.rectTmp.inset(sw / 2.0f, sw / 2.0f);
            canvas.drawRoundRect(AndroidUtilities.rectTmp, rad, rad, strokePaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        strokePaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        strokePaint.setColorFilter(colorFilter);
    }

    @SuppressWarnings("deprecation")
    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }
}
