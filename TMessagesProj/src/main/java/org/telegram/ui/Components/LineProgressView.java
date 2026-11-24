/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.exteragram.messenger.ExteraConfig;
import com.google.android.material.progressindicator.LinearProgressIndicator;


import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.voip.CellFlickerDrawable;

public class LineProgressView extends LinearProgressIndicator {
    public static final int TYPE_FLAT = 0;
    public static final int TYPE_WAVE = 1;

    private long lastUpdateTime;
    private float animationProgressStart;
    private long currentProgressTime;
    private float animatedProgressValue;
    private float animatedAlphaValue = 1.0f;

    private int backColor;
    private int progressColor;

    private static DecelerateInterpolator decelerateInterpolator;
    private static Paint progressPaint;

    private final RectF rect = new RectF();
    private CellFlickerDrawable cellFlickerDrawable;

    private float currentProgress;
    public int type;

    public LineProgressView(@NonNull Context context) {
        this(context, null);
    }

    public LineProgressView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, com.google.android.material.R.attr.linearProgressIndicatorStyle);
    }

    public LineProgressView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (decelerateInterpolator == null) {
            decelerateInterpolator = new DecelerateInterpolator();
            progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            progressPaint.setStrokeCap(Paint.Cap.ROUND);
            progressPaint.setStrokeWidth(AndroidUtilities.dp(2));
        }
        init();
    }

    public void setProgressType(int type) {
        if (!ExteraConfig.getNewLoadingStyle()) {
            this.type = TYPE_FLAT;
            return;
        }
        if (this.type == type) {
            return;
        }

        this.type = type;
        if (type == TYPE_FLAT) {
            setWaveSpeed(0);
            setWavelength(0);
            setWaveAmplitude(0);
        } else if (type == TYPE_WAVE) {
            setWavelengthDeterminate(AndroidUtilities.dp(40));
            setWaveAmplitude(AndroidUtilities.dp(3));
            updateWaveSpeed(isAttachedToWindow());
            setWaveAmplitudeRampProgressMin(0.05f);
        }
    }

    private void updateWaveSpeed(boolean attached) {
        setWaveSpeed(type == TYPE_WAVE && attached ? AndroidUtilities.dp(15) : 0);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateWaveSpeed(true);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        updateWaveSpeed(false);
    }

    private void init() {
        setMax(1000);

        setTrackThickness(AndroidUtilities.dp(2));
        setTrackCornerRadius(AndroidUtilities.dp(1));
        setTrackStopIndicatorSize(AndroidUtilities.dp(2));
        setIndicatorTrackGapSize(AndroidUtilities.dp(2));
        setTrackCornerRadiusFraction(0.5f);
        setIndeterminate(false);

        setProgressType(TYPE_FLAT);
    }

    public void setProgressColor(int color) {
        progressColor = color;
        if (ExteraConfig.getNewLoadingStyle()) {
            setIndicatorColor(color);
        }
    }

    public void setBackColor(int color) {
        backColor = color;
        if (ExteraConfig.getNewLoadingStyle()) {
            setTrackColor(color);
        }
    }

    public void setProgress(float value, boolean animated) {
        currentProgress = value;
        if (ExteraConfig.getNewLoadingStyle()) {
            int targetProgress = (int) (value * getMax());
            setProgressCompat(targetProgress, animated);
        } else {
            if (!animated) {
                animatedProgressValue = value;
                animationProgressStart = value;
            } else {
                animationProgressStart = animatedProgressValue;
            }
            if (value != 1) {
                animatedAlphaValue = 1;
            }
            currentProgressTime = 0;

            lastUpdateTime = System.currentTimeMillis();
            invalidate();
        }
    }

    public float getCurrentProgress() {
        return currentProgress;
    }

    private void updateLegacyAnimation() {
        long newTime = System.currentTimeMillis();
        long dt = newTime - lastUpdateTime;
        lastUpdateTime = newTime;

        if (animatedProgressValue != 1 && animatedProgressValue != currentProgress) {
            float progressDiff = currentProgress - animationProgressStart;
            if (progressDiff > 0) {
                currentProgressTime += dt;
                if (currentProgressTime >= 300) {
                    animatedProgressValue = currentProgress;
                    animationProgressStart = currentProgress;
                    currentProgressTime = 0;
                } else {
                    animatedProgressValue = animationProgressStart + progressDiff * decelerateInterpolator.getInterpolation(currentProgressTime / 300.0f);
                }
            }
            invalidate();
        }
        if (animatedProgressValue >= 1 && animatedProgressValue == 1 && animatedAlphaValue != 0) {
            animatedAlphaValue -= dt / 200.0f;
            if (animatedAlphaValue <= 0) {
                animatedAlphaValue = 0.0f;
            }
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (ExteraConfig.getNewLoadingStyle()) {
            super.onDraw(canvas);
            return;
        }

        if (backColor != 0 && animatedProgressValue != 1) {
            progressPaint.setColor(backColor);
            progressPaint.setAlpha((int) (255 * animatedAlphaValue));
            rect.set(0, 0, getWidth(), getHeight());
            canvas.drawRoundRect(rect, getHeight() / 2f, getHeight() / 2f, progressPaint);
        }

        progressPaint.setColor(progressColor);
        progressPaint.setAlpha((int) (255 * animatedAlphaValue));
        rect.set(0, 0, getWidth() * animatedProgressValue, getHeight());
        canvas.drawRoundRect(rect, getHeight() / 2f, getHeight() / 2f, progressPaint);

        if (animatedAlphaValue > 0) {
            if (cellFlickerDrawable == null) {
                cellFlickerDrawable = new CellFlickerDrawable(160, 0);
                cellFlickerDrawable.drawFrame = false;
                cellFlickerDrawable.animationSpeedScale = 0.8f;
                cellFlickerDrawable.repeatProgress = 1.2f;
            }
            cellFlickerDrawable.setParentWidth(getMeasuredWidth());
            cellFlickerDrawable.draw(canvas, rect, getHeight() / 2f, null);
            invalidate();
        }

        updateLegacyAnimation();
    }
}
