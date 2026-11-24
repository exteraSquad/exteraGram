/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.SpeedIconDrawable;

public class SpeedTextDrawable extends Drawable implements Drawable.Callback {

    private static final int TEXT_SIZE_DP = 16;
    private static final int WIDTH_DP = 48;
    private static final int HEIGHT_DP = 24;

    private final AnimatedTextView.AnimatedTextDrawable textDrawable;

    public SpeedTextDrawable() {
        textDrawable = new AnimatedTextView.AnimatedTextDrawable(false, true, true);
        textDrawable.setCallback(this);
        textDrawable.setAnimationProperties(.3f, 0, 165, CubicBezierInterpolator.EASE_OUT_QUINT);
        textDrawable.setGravity(Gravity.CENTER);
        textDrawable.setTypeface(AndroidUtilities.getTypeface("fonts/num.otf"));
        textDrawable.setTextSize(dp(TEXT_SIZE_DP));
    }

    public void setValue(float value, boolean animated) {
        final String text = SpeedIconDrawable.formatNumber(value) + "x";
        if (!animated || !TextUtils.equals(textDrawable.getText(), text)) {
            textDrawable.cancelAnimation();
            textDrawable.setText(text, animated);
            invalidateSelf();
        }
    }

    public void setColor(int color) {
        textDrawable.setTextColor(color);
        invalidateSelf();
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable who) {
        invalidateSelf();
    }

    @Override
    public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
        scheduleSelf(what, when);
    }

    @Override
    public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
        unscheduleSelf(what);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        textDrawable.setBounds(getBounds());
        textDrawable.draw(canvas);
    }

    @Override
    public int getIntrinsicWidth() {
        return dp(WIDTH_DP);
    }

    @Override
    public int getIntrinsicHeight() {
        return dp(HEIGHT_DP);
    }

    @Override
    public void setAlpha(int alpha) {
        textDrawable.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @SuppressWarnings("deprecation")
    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
