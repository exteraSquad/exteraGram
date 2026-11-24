package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.exteragram.messenger.ExteraConfig;
import com.google.android.material.loadingindicator.LoadingIndicator;
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec;
import com.google.android.material.progressindicator.IndeterminateDrawable;

import org.telegram.messenger.AndroidUtilities;

public class CircularProgressDrawable extends Drawable implements Drawable.Callback {

    public static final int STYLE_LEGACY = 0;
    public static final int STYLE_MATERIAL3_LOADING = 1;
    public static final int STYLE_MATERIAL3_PROGRESS_FLAT = 2;
    public static final int STYLE_MATERIAL3_PROGRESS_WAVY = 3;

    private int currentStyle = STYLE_LEGACY;
    private LoadingIndicator m3IndicatorView;
    private Drawable m3Drawable;

    private IndeterminateDrawable<CircularProgressIndicatorSpec> circularIndicatorDrawable;
    private CircularProgressIndicatorSpec circularIndicatorSpec;

    public float size = AndroidUtilities.dp(18);
    public float thickness = AndroidUtilities.dp(2.25f);

    private int currentColor;
    private int trackColor;
    private long lastDrawTime;
    private boolean updatingM3Visibility;
    private static final long ORPHANED_DRAWABLE_STOP_DELAY_MS = 1000;

    public CircularProgressDrawable() {
        this(0xffffffff);
    }

    public CircularProgressDrawable(int color) {
        this.currentColor = color;
        setStyle(STYLE_MATERIAL3_LOADING, AndroidUtilities.getActivity());
        setColor(color);
    }
    public CircularProgressDrawable(float size, float thickness, int color) {
        this.size = size;
        this.thickness = thickness;
        this.currentColor = color;
        setStyle(STYLE_MATERIAL3_LOADING, AndroidUtilities.getActivity());
        setColor(color);
    }

    public CircularProgressDrawable(float size, float thickness, int trackColor, int indicatorColor) {
        this.size = size;
        this.thickness = thickness;
        this.trackColor = trackColor;
        this.currentColor = indicatorColor;
        setStyle(STYLE_MATERIAL3_PROGRESS_FLAT, AndroidUtilities.getActivity());
        setColor(indicatorColor);
        setTrackColor(trackColor);
    }

    public void setStyle(int style, Context context) {
        if (!ExteraConfig.getNewLoadingStyle() && style != STYLE_LEGACY) {
            style = STYLE_LEGACY;
        }
        if (currentStyle != style ||
                (style == STYLE_MATERIAL3_LOADING && m3Drawable == null) ||
                ((style == STYLE_MATERIAL3_PROGRESS_FLAT || style == STYLE_MATERIAL3_PROGRESS_WAVY) && circularIndicatorDrawable == null)) {

            var oldM3Drawable = m3Drawable;
            var oldCircularIndicatorDrawable = circularIndicatorDrawable;
            currentStyle = style;
            if (currentStyle == STYLE_MATERIAL3_LOADING && context != null) {
                circularIndicatorDrawable = null;
                circularIndicatorSpec = null;
                if (m3IndicatorView == null) {
                    m3IndicatorView = new LoadingIndicator(context);
                }
                m3IndicatorView.setIndicatorSize((int) size);
                m3IndicatorView.setIndicatorColor(currentColor);

                m3Drawable = m3IndicatorView.getDrawable();
                m3Drawable.setCallback(this);
                forceUpdateM3Visibility();

                if (!getBounds().isEmpty()) {
                    updateM3Bounds(getBounds());
                }
            } else if ((currentStyle == STYLE_MATERIAL3_PROGRESS_FLAT || currentStyle == STYLE_MATERIAL3_PROGRESS_WAVY) && context != null) {
                m3Drawable = null;
                m3IndicatorView = null;
                circularIndicatorSpec = new CircularProgressIndicatorSpec(context, null);
                circularIndicatorSpec.indicatorSize = (int) size;
                circularIndicatorSpec.trackThickness = (int) thickness;
                circularIndicatorSpec.indicatorColors = new int[]{currentColor};
                circularIndicatorSpec.trackColor = trackColor;
                circularIndicatorSpec.indicatorTrackGapSize = AndroidUtilities.dp(2);

                if (currentStyle == STYLE_MATERIAL3_PROGRESS_WAVY) {
                    circularIndicatorSpec.wavelengthIndeterminate = AndroidUtilities.dp(7);
                    circularIndicatorSpec.indicatorInset = 0;
                    circularIndicatorSpec.waveAmplitude = AndroidUtilities.dp(0.75f);
                    circularIndicatorSpec.waveSpeed = AndroidUtilities.dp(6);
                }

                circularIndicatorDrawable = IndeterminateDrawable.createCircularDrawable(context, circularIndicatorSpec);
                circularIndicatorDrawable.setCallback(this);
                forceUpdateM3Visibility();

                if (!getBounds().isEmpty()) {
                    updateCircularBounds(getBounds());
                }
            } else {
                m3Drawable = null;
                m3IndicatorView = null;
                circularIndicatorDrawable = null;
                circularIndicatorSpec = null;
            }

            if (oldM3Drawable != null && oldM3Drawable != m3Drawable) {
                oldM3Drawable.setVisible(false, false);
                oldM3Drawable.setCallback(null);
            }
            if (oldCircularIndicatorDrawable != null && oldCircularIndicatorDrawable != circularIndicatorDrawable) {
                oldCircularIndicatorDrawable.setVisible(false, false);
                oldCircularIndicatorDrawable.setCallback(null);
            }
            invalidateSelf();
        }
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable who) {
        invalidateSelf();
    }

    @Override
    public void invalidateSelf() {
        super.invalidateSelf();
        if (!updatingM3Visibility && getCallback() == null && SystemClock.elapsedRealtime() - lastDrawTime > ORPHANED_DRAWABLE_STOP_DELAY_MS) {
            stopM3Drawables();
        }
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
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        updateM3Visibility(restart);
        return changed;
    }

    private long start = -1;
    public static final FastOutSlowInInterpolator interpolator = new FastOutSlowInInterpolator();
    private float[] segment = new float[2];
    private void updateSegment() {
        final long now = SystemClock.elapsedRealtime();
        final long t = (now - start) % 5400;
        getSegments(t, segment);
    }

    public static void getSegments(float t, float[] segments) {
        segments[0] = Math.max(0, 1520 * t / 5400f - 20);
        segments[1] = 1520 * t / 5400f;
        for (int i = 0; i < 4; ++i) {
            segments[1] += interpolator.getInterpolation((t - i * 1350) / 667f) * 250;
            segments[0] += interpolator.getInterpolation((t - (667 + i * 1350)) / 667f) * 250;
        }
    }

    private final Paint paint = new Paint(); {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    private float angleOffset;
    private final RectF bounds = new RectF();

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (currentStyle == STYLE_MATERIAL3_LOADING && m3Drawable != null) {
            lastDrawTime = SystemClock.elapsedRealtime();
            ensureManualDrawVisible();
            updateM3Visibility(false);
            m3Drawable.draw(canvas);
            return;
        }
        if ((currentStyle == STYLE_MATERIAL3_PROGRESS_FLAT || currentStyle == STYLE_MATERIAL3_PROGRESS_WAVY) && circularIndicatorDrawable != null) {
            lastDrawTime = SystemClock.elapsedRealtime();
            ensureManualDrawVisible();
            updateM3Visibility(false);
            circularIndicatorDrawable.draw(canvas);
            return;
        }

        if (start < 0) {
            start = SystemClock.elapsedRealtime();
        }
        updateSegment();
        canvas.drawArc(
            bounds,
            angleOffset + segment[0],
            segment[1] - segment[0],
            false,
            paint
        );
        invalidateSelf();
    }

    public void reset() {
        start = -1;
    }

    public void setAngleOffset(float angleOffset) {
        this.angleOffset = angleOffset;
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);

        int width = right - left, height = bottom - top;
        bounds.set(
            left + (width - thickness / 2f - size) / 2f,
            top + (height - thickness / 2f - size) / 2f,
            left + (width + thickness / 2f + size) / 2f,
            top + (height + thickness / 2f + size) / 2f
        );
        paint.setStrokeWidth(thickness);

        updateM3Bounds(getBounds());
        updateCircularBounds(getBounds());
    }

    private void updateM3Bounds(android.graphics.Rect parentBounds) {
        if (m3Drawable != null) {
            int w = parentBounds.width();
            int h = parentBounds.height();
            int m3Size = (int) size;

            int left = parentBounds.left + (w - m3Size) / 2;
            int top = parentBounds.top + (h - m3Size) / 2;

            m3Drawable.setBounds(left, top, left + m3Size, top + m3Size);
        }
    }

    private void updateCircularBounds(android.graphics.Rect parentBounds) {
        if (circularIndicatorDrawable != null) {
            int w = parentBounds.width();
            int h = parentBounds.height();
            int indSize = (int) size;

            int left = parentBounds.left + (w - indSize) / 2;
            int top = parentBounds.top + (h - indSize) / 2;

            circularIndicatorDrawable.setBounds(left, top, left + indSize, top + indSize);
        }
    }

    public void setColor(int color) {
        this.currentColor = color;
        paint.setColor(color);
        if (m3IndicatorView != null) {
            m3IndicatorView.setIndicatorColor(color);
        }
        if (circularIndicatorDrawable != null) {
            circularIndicatorSpec.indicatorColors = new int[]{color};
        }
    }

    public void setTrackColor(int color) {
        this.trackColor = color;
        if (circularIndicatorDrawable != null) {
            circularIndicatorSpec.trackColor = color;
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        if (m3Drawable != null) {
            m3Drawable.setAlpha(alpha);
        }
        if (circularIndicatorDrawable != null) {
            circularIndicatorDrawable.setAlpha(alpha);
        }
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        if (m3Drawable != null) {
            m3Drawable.setColorFilter(colorFilter);
        }
        if (circularIndicatorDrawable != null) {
            circularIndicatorDrawable.setColorFilter(colorFilter);
        }
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return (int) (size + thickness);
    }

    @Override
    public int getIntrinsicHeight() {
        return (int) (size + thickness);
    }

    public void setWavyValues(float amplitude, float wavelength, float wavespeed) {
        if (circularIndicatorSpec == null) {
            return;
        }
        circularIndicatorSpec.waveAmplitude = AndroidUtilities.dp(amplitude);
        circularIndicatorSpec.wavelengthIndeterminate = AndroidUtilities.dp(wavelength);
        circularIndicatorSpec.waveSpeed = AndroidUtilities.dp(wavespeed);
    }

    private void updateM3Visibility(boolean restart) {
        boolean visible = isVisible();
        updatingM3Visibility = true;
        if (m3Drawable != null && (m3Drawable.isVisible() != visible || restart)) {
            m3Drawable.setVisible(visible, restart && visible);
        }
        if (circularIndicatorDrawable != null && (circularIndicatorDrawable.isVisible() != visible || restart)) {
            circularIndicatorDrawable.setVisible(visible, restart && visible);
        }
        updatingM3Visibility = false;
    }

    private void forceUpdateM3Visibility() {
        boolean visible = isVisible();
        updatingM3Visibility = true;
        if (m3Drawable != null) {
            m3Drawable.setVisible(visible, false);
        }
        if (circularIndicatorDrawable != null) {
            circularIndicatorDrawable.setVisible(visible, false);
        }
        updatingM3Visibility = false;
    }

    private void stopM3Drawables() {
        if (m3Drawable != null && m3Drawable.isVisible()) {
            m3Drawable.setVisible(false, false);
        }
        if (circularIndicatorDrawable != null && circularIndicatorDrawable.isVisible()) {
            circularIndicatorDrawable.setVisible(false, false);
        }
    }

    private void ensureManualDrawVisible() {
        if (getCallback() == null && !isVisible()) {
            setVisible(true, false);
        }
    }
}