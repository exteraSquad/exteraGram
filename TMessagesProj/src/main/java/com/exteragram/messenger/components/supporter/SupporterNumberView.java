/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.components.supporter;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Build;
import android.text.TextPaint;
import android.view.HapticFeedbackConstants;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

public class SupporterNumberView extends View {

    private static final int CYCLES = 2;
    private static final long COLUMN_DURATION = 900;
    private static final long STAGGER = 140;
    private static final long FADE_DURATION = 200;
    private static final long PULSE_DURATION = 240;

    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private String text = "";
    private int[] digits = new int[0];
    private float digitWidth;
    private int digitCount;
    private int color1, color2;
    private int shaderWidth = -1;

    private ValueAnimator animator;
    private float time = -1;
    private int landedCount;
    private Runnable onLanded;

    public SupporterNumberView(Context context) {
        super(context);
        paint.setTypeface(AndroidUtilities.bold());
        paint.setTextSize(dp(34));
        paint.setTextAlign(Paint.Align.CENTER);
        for (int d = 0; d <= 9; d++) {
            digitWidth = Math.max(digitWidth, paint.measureText(String.valueOf(d)));
        }
    }

    public void setNumber(String text) {
        this.text = text;
        digits = new int[text.length()];
        digitCount = 0;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            digits[i] = Character.isDigit(c) ? Character.digit(c, 10) : -1;
            if (digits[i] >= 0) {
                digitCount++;
            }
        }
        setContentDescription(text);
        invalidate();
    }

    public void setColors(int color1, int color2) {
        this.color1 = color1;
        this.color2 = color2;
        shaderWidth = -1;
        invalidate();
    }

    public void start(Runnable onLanded) {
        this.onLanded = onLanded;
        if (animator != null) {
            animator.cancel();
        }
        final long total = Math.max(0, digitCount - 1) * STAGGER + COLUMN_DURATION + PULSE_DURATION;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ValueAnimator.areAnimatorsEnabled()) {
            time = total;
            landedCount = digitCount;
            invalidate();
            if (onLanded != null) {
                onLanded.run();
            }
            return;
        }
        time = 0;
        landedCount = 0;
        animator = ValueAnimator.ofFloat(0, total);
        animator.setDuration(total);
        animator.setInterpolator(null);
        animator.addUpdateListener(a -> {
            time = (float) a.getAnimatedValue();
            checkLanded();
            invalidate();
        });
        animator.start();
    }

    private void checkLanded() {
        final int landed = time < COLUMN_DURATION ? 0 : Math.min(digitCount, (int) ((time - COLUMN_DURATION) / STAGGER) + 1);
        while (landedCount < landed) {
            landedCount++;
            if (landedCount < digitCount) {
                try {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                } catch (Exception ignored) {
                }
            } else if (onLanded != null) {
                onLanded.run();
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(50), MeasureSpec.EXACTLY));
    }

    private float charWidth(int i) {
        return digits[i] >= 0 ? digitWidth : paint.measureText(text, i, i + 1);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (time < 0 || text.isEmpty()) {
            return;
        }
        float totalWidth = 0;
        for (int i = 0; i < text.length(); i++) {
            totalWidth += charWidth(i);
        }
        if (shaderWidth != (int) totalWidth) {
            shaderWidth = (int) totalWidth;
            final float left = (getWidth() - totalWidth) / 2f;
            paint.setShader(color1 == color2 ? null : new LinearGradient(left, 0, left + totalWidth, 0, color1, color2, Shader.TileMode.CLAMP));
            paint.setColor(color1);
        }

        final float lineHeight = paint.getTextSize() * 1.1f;
        final float baseline = getHeight() / 2f - (paint.descent() + paint.ascent()) / 2f;
        final float appear = Utilities.clamp(time / FADE_DURATION, 1f, 0f);

        canvas.save();
        canvas.clipRect(0, getHeight() / 2f - lineHeight * .6f, getWidth(), getHeight() / 2f + lineHeight * .6f);
        float x = (getWidth() - totalWidth) / 2f;
        int column = 0;
        for (int i = 0; i < text.length(); i++) {
            final float w = charWidth(i);
            final float cx = x + w / 2f;
            if (digits[i] < 0) {
                paint.setAlpha((int) (255 * appear));
                canvas.drawText(text, i, i + 1, cx, baseline, paint);
            } else {
                final float local = (time - column * STAGGER) / COLUMN_DURATION;
                final float t = Utilities.clamp(local, 1f, 0f);
                final float position = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(t) * (CYCLES * 10 + digits[i]);
                final int base = (int) Math.floor(position);
                final float fraction = position - base;
                final float pulseT = Utilities.clamp((local - 1f) * COLUMN_DURATION / PULSE_DURATION, 1f, 0f);
                final float pulse = 1f + .14f * (float) Math.sin(Math.PI * pulseT);

                canvas.save();
                canvas.scale(pulse, pulse, cx, getHeight() / 2f);
                drawDigit(canvas, base % 10, cx, baseline - fraction * lineHeight, fraction, appear);
                if (fraction > 0) {
                    drawDigit(canvas, (base + 1) % 10, cx, baseline + (1f - fraction) * lineHeight, 1f - fraction, appear);
                }
                canvas.restore();
                column++;
            }
            x += w;
        }
        canvas.restore();
    }

    private void drawDigit(Canvas canvas, int digit, float cx, float y, float distance, float appear) {
        paint.setAlpha((int) (255 * appear * (1f - distance)));
        canvas.drawText(String.valueOf(digit), cx, y, paint);
    }
}
