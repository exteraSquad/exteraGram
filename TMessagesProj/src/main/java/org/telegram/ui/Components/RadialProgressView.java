/*
 * This is the source code of Telegram for Android v. 5.x.x.
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
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import com.exteragram.messenger.ExteraConfig;
import com.google.android.material.loadingindicator.LoadingIndicator;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;

import java.util.HashSet;

public class RadialProgressView extends View implements Theme.Colorable {

    public static final int STYLE_LEGACY = 0;
    public static final int STYLE_MATERIAL3_LOADING = 1;
    public static final int STYLE_MATERIAL3_PROGRESS_INDETERMINATE_FLAT = 2;
    public static final int STYLE_MATERIAL3_PROGRESS_INDETERMINATE_WAVY = 3;

    private int currentStyle = STYLE_LEGACY;
    private LoadingIndicator m3IndicatorView;
    private CircularProgressIndicator m3CircularProgressIndicator;
    private Drawable m3Drawable;

    private long lastUpdateTime;
    private float radOffset;
    private float currentCircleLength;
    private boolean risingCircleLength;
    private float currentProgressTime;
    private RectF cicleRect = new RectF();
    private boolean useSelfAlpha;
    private float drawingCircleLenght;

    private int progressColor;

    private DecelerateInterpolator decelerateInterpolator;
    private AccelerateInterpolator accelerateInterpolator;
    private Paint progressPaint;
    private static final float rotationTime = 2000;
    private static final float risingTime = 500;
    private int size;

    private float currentProgress;
    private float progressAnimationStart;
    private int progressTime;
    private float animatedProgress;
    private boolean toCircle;
    private float toCircleProgress;

    private boolean noProgress = true;
    private final Theme.ResourcesProvider resourcesProvider;

    public RadialProgressView(Context context) {
        this(context, null);
    }

    public RadialProgressView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        size = AndroidUtilities.dp(40);

        progressColor = getThemedColor(Theme.key_progressCircle);
        decelerateInterpolator = new DecelerateInterpolator();
        accelerateInterpolator = new AccelerateInterpolator();
        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setStrokeWidth(AndroidUtilities.dp(3));
        progressPaint.setColor(progressColor);
        themedProgressColor = progressColor;
    }

    // the key progressColor came from (Theme.key_progressCircle by default, see setProgressColorKey), and the color it gave
    private int progressColorKey = Theme.key_progressCircle;
    private int themedProgressColor;

    /** same as setProgressColor(getThemedColor(key)), but follows theme changes */
    public void setProgressColorKey(int key) {
        progressColorKey = key;
        setProgressColor(getThemedColor(key));
        themedProgressColor = progressColor;
    }

    @Override
    public void updateColors() {
        try {
            if (progressPaint == null || progressColor != themedProgressColor) {
                return; // custom color set by the caller
            }
            final int color = getThemedColor(progressColorKey);
            themedProgressColor = color;
            if (color != progressColor) {
                final int alpha = progressPaint.getAlpha();
                setProgressColor(color);
                themedProgressColor = color;
                if (useSelfAlpha) {
                    progressPaint.setAlpha(alpha);
                }
                invalidate();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void addColorKeys(HashSet<Integer> colors) {
        colors.add(progressColorKey);
    }

    public void setUseSelfAlpha(boolean value) {
        useSelfAlpha = value;
    }

    @Keep
    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
        if (useSelfAlpha) {
            Drawable background = getBackground();
            int a = (int) (alpha * 255);
            if (background != null) {
                background.setAlpha(a);
            }
            progressPaint.setAlpha(a);
            if (m3Drawable != null) {
                m3Drawable.setAlpha(a);
            }
        }
    }

    public void setNoProgress(boolean value) {
        noProgress = value;
    }

    public void setStyle(int style) {
        if (!ExteraConfig.getNewLoadingStyle() && style != STYLE_LEGACY) {
            style = STYLE_LEGACY;
        }
        if (currentStyle != style) {
            var oldM3Drawable = m3Drawable;
            currentStyle = style;
            if (currentStyle == STYLE_MATERIAL3_LOADING) {
                if (m3IndicatorView == null) {
                    m3IndicatorView = new LoadingIndicator(getContext());
                }
                m3IndicatorView.setIndicatorColor(progressColor);
                m3Drawable = m3IndicatorView.getDrawable();
            } else if (currentStyle == STYLE_MATERIAL3_PROGRESS_INDETERMINATE_FLAT ||
                    currentStyle == STYLE_MATERIAL3_PROGRESS_INDETERMINATE_WAVY) {
                if (m3CircularProgressIndicator == null) {
                    m3CircularProgressIndicator = new CircularProgressIndicator(getContext());
                    m3CircularProgressIndicator.setIndeterminate(true);
                }

                m3CircularProgressIndicator.setIndicatorColor(progressColor);
                m3CircularProgressIndicator.setIndicatorSize(size);
                m3CircularProgressIndicator.setTrackThickness((int) progressPaint.getStrokeWidth());
                m3CircularProgressIndicator.setTrackCornerRadius(AndroidUtilities.dp(2));
                m3CircularProgressIndicator.setIndicatorTrackGapSize(AndroidUtilities.dp(2));

                setWavy(currentStyle == STYLE_MATERIAL3_PROGRESS_INDETERMINATE_WAVY);

                m3Drawable = m3CircularProgressIndicator.getIndeterminateDrawable();
            } else {
                m3Drawable = null;
            }

            if (oldM3Drawable != null && oldM3Drawable != m3Drawable) {
                oldM3Drawable.setVisible(false, false);
                oldM3Drawable.setCallback(null);
            }
            if (m3Drawable != null) {
                m3Drawable.setCallback(this);
                setM3Visible(isShown(), true);
            }
            invalidate();
        }
    }


    public boolean isMaterial3ProgressStyle() {
        return currentStyle == STYLE_MATERIAL3_PROGRESS_INDETERMINATE_FLAT || currentStyle == STYLE_MATERIAL3_PROGRESS_INDETERMINATE_WAVY;
    }

    @Keep
    public void setSpecValues(int indicatorSize, int trackThickness, int trackCornerRadius, int indicatorTrackGapSize, int indicatorColor, int trackColor) {
        if (!isMaterial3ProgressStyle()) return;

        m3CircularProgressIndicator.setIndicatorSize(indicatorSize);
        m3CircularProgressIndicator.setTrackThickness(trackThickness);
        m3CircularProgressIndicator.setTrackCornerRadius(trackCornerRadius);
        m3CircularProgressIndicator.setIndicatorTrackGapSize(indicatorTrackGapSize);
        m3CircularProgressIndicator.setIndicatorColor(indicatorColor);
        m3CircularProgressIndicator.setTrackColor(trackColor);
    }

    @Keep
    public void setWavy(boolean isWavy) {
        if (isWavy) {
            m3CircularProgressIndicator.setIndicatorInset(0);
            setWavyValues(AndroidUtilities.dp(15), AndroidUtilities.dp(1.6f), AndroidUtilities.dp(5), 0.05f);
        } else {
            m3CircularProgressIndicator.setIndicatorInset(0);
            setWavyValues(0, 0, 0, 1f);
        }
    }

    public void setWavyValues(int wavelengthIndeterminate, int waveAmplitude, int waveSpeed, float waveAmplitudeRampProgressMin) {
        if (currentStyle != STYLE_MATERIAL3_PROGRESS_INDETERMINATE_WAVY) return;

        m3CircularProgressIndicator.setWavelengthIndeterminate(wavelengthIndeterminate);
        m3CircularProgressIndicator.setWaveAmplitude(waveAmplitude);
        m3CircularProgressIndicator.setWaveSpeed(waveSpeed);
        m3CircularProgressIndicator.setWaveAmplitudeRampProgressMin(waveAmplitudeRampProgressMin);
    }

    public void setProgress(float value) {
        currentProgress = value;
        if (animatedProgress > value) {
            animatedProgress = value;
        }
        progressAnimationStart = animatedProgress;
        progressTime = 0;
    }

    public void sync(RadialProgressView from) {
        lastUpdateTime = from.lastUpdateTime;
        radOffset = from.radOffset;
        toCircle = from.toCircle;
        toCircleProgress = from.toCircleProgress;
        noProgress = from.noProgress;
        currentCircleLength = from.currentCircleLength;
        drawingCircleLenght = from.drawingCircleLenght;
        currentProgressTime = from.currentProgressTime;
        currentProgress = from.currentProgress;
        progressTime = from.progressTime;
        animatedProgress = from.animatedProgress;
        risingCircleLength = from.risingCircleLength;
        progressAnimationStart = from.progressAnimationStart;
        updateAnimation(17 * 5);
    }

    private void updateAnimation() {
        long newTime = System.currentTimeMillis();
        long dt = newTime - lastUpdateTime;
        if (dt > 17) {
            dt = 17;
        }
        lastUpdateTime = newTime;
        updateAnimation(dt);
    }

    private void updateAnimation(long dt) {
        radOffset += 360 * dt / rotationTime;
        int count = (int) (radOffset / 360);
        radOffset -= count * 360;

        if (toCircle && toCircleProgress != 1f) {
            toCircleProgress += 16 / 220f;
            if (toCircleProgress > 1f) {
                toCircleProgress = 1f;
            }
        } else if (!toCircle && toCircleProgress != 0f) {
            toCircleProgress -= 16 / 400f;
            if (toCircleProgress < 0) {
                toCircleProgress = 0f;
            }
        }

        if (noProgress) {
            if (toCircleProgress == 0) {
                currentProgressTime += dt;
                if (currentProgressTime >= risingTime) {
                    currentProgressTime = risingTime;
                }
                if (risingCircleLength) {
                    currentCircleLength = 4 + 266 * accelerateInterpolator.getInterpolation(currentProgressTime / risingTime);
                } else {
                    currentCircleLength = 4 - 270 * (1.0f - decelerateInterpolator.getInterpolation(currentProgressTime / risingTime));
                }

                if (currentProgressTime == risingTime) {
                    if (risingCircleLength) {
                        radOffset += 270;
                        currentCircleLength = -266;
                    }
                    risingCircleLength = !risingCircleLength;
                    currentProgressTime = 0;
                }
            } else {
                if (risingCircleLength) {
                    float old = currentCircleLength;
                    currentCircleLength = 4 + 266 * accelerateInterpolator.getInterpolation(currentProgressTime / risingTime);
                    currentCircleLength += 360 * toCircleProgress;
                    float dx = old - currentCircleLength;
                    if (dx > 0) {
                        radOffset += old - currentCircleLength;
                    }
                } else {
                    float old = currentCircleLength;
                    currentCircleLength = 4 - 270 * (1.0f - decelerateInterpolator.getInterpolation(currentProgressTime / risingTime));
                    currentCircleLength -= 364 * toCircleProgress;
                    float dx = old - currentCircleLength;
                    if (dx > 0) {
                        radOffset += old - currentCircleLength;
                    }
                }
            }
        } else {
            float progressDiff = currentProgress - progressAnimationStart;
            if (progressDiff > 0) {
                progressTime += dt;
                if (progressTime >= 200.0f) {
                    animatedProgress = progressAnimationStart = currentProgress;
                    progressTime = 0;
                } else {
                    animatedProgress = progressAnimationStart + progressDiff * AndroidUtilities.decelerateInterpolator.getInterpolation(progressTime / 200.0f);
                }
            }
            currentCircleLength = Math.max(4, 360 * animatedProgress);
        }
        invalidate();
    }

    public void setSize(int value) {
        size = value;
        if (m3CircularProgressIndicator != null) {
            m3CircularProgressIndicator.setIndicatorSize(size);
        }
        invalidate();
    }

    public void setStrokeWidth(float value) {
        progressPaint.setStrokeWidth(AndroidUtilities.dp(value));
        if (m3CircularProgressIndicator != null) {
            m3CircularProgressIndicator.setTrackThickness(AndroidUtilities.dp(value));
        }
    }

    public void setProgressColor(int color) {
        progressColor = color;
        progressPaint.setColor(progressColor);
        if (m3IndicatorView != null) {
            m3IndicatorView.setIndicatorColor(progressColor);
        }
        if (m3CircularProgressIndicator != null) {
            m3CircularProgressIndicator.setIndicatorColor(progressColor);
        }
    }

    public void setTrackColor(int color) {
        if (m3CircularProgressIndicator != null) {
            m3CircularProgressIndicator.setTrackColor(color);
        }
    }

    public void toCircle(boolean toCircle, boolean animated) {
        this.toCircle = toCircle;
        if (!animated) {
            toCircleProgress = toCircle ? 1f : 0f;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (currentStyle != STYLE_LEGACY && m3Drawable != null) {
            int x = (getMeasuredWidth() - size) / 2;
            int y = (getMeasuredHeight() - size) / 2;
            m3Drawable.setBounds(x, y, x + size, y + size);
            m3Drawable.draw(canvas);
            return;
        }
        int x = (getMeasuredWidth() - size) / 2;
        int y = (getMeasuredHeight() - size) / 2;
        cicleRect.set(x, y, x + size, y + size);
        canvas.drawArc(cicleRect, radOffset, drawingCircleLenght = currentCircleLength, false, progressPaint);
        updateAnimation();
    }

    public void draw(Canvas canvas, float cx, float cy) {
        if (currentStyle != STYLE_LEGACY && m3Drawable != null) {
            int left = (int) (cx - size / 2f);
            int top = (int) (cy - size / 2f);
            m3Drawable.setBounds(left, top, left + size, top + size);
            m3Drawable.draw(canvas);
            return;
        }
        cicleRect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy +  size / 2f);
        canvas.drawArc(cicleRect, radOffset, drawingCircleLenght = currentCircleLength, false, progressPaint);
        updateAnimation();
    }

    public boolean isCircle() {
        return Math.abs(drawingCircleLenght) >= 360;
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        if (who == m3Drawable) {
            return true;
        }
        return super.verifyDrawable(who);
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        setM3Visible(isVisible, false);
    }

    @Override
    protected void onDetachedFromWindow() {
        setM3Visible(false, false);
        super.onDetachedFromWindow();
    }

    private void setM3Visible(boolean visible, boolean restart) {
        if (m3Drawable == null) {
            return;
        }
        m3Drawable.setVisible(visible, restart && visible);
    }
}