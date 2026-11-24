package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;

public class CrossfadeDrawable extends Drawable {

    private final Drawable topDrawable;
    private final Drawable bottomDrawable;
    private Callback topCallback, bottomCallback;

    private float progress;
    float globalAlpha = 255f;

    public CrossfadeDrawable(Drawable topDrawable, Drawable bottomDrawable) {
        this.topDrawable = topDrawable;
        this.bottomDrawable = bottomDrawable;

        if (topDrawable != null) {
            topDrawable.setCallback(topCallback = new Callback() {
                @Override
                public void invalidateDrawable(@NonNull Drawable drawable) {
                    if (progress < 1.0f) {
                        CrossfadeDrawable.this.invalidateSelf();
                    }
                }
                @Override
                public void scheduleDrawable(@NonNull Drawable drawable, @NonNull Runnable runnable, long l) {
                    if (progress < 1.0f) {
                        CrossfadeDrawable.this.scheduleSelf(runnable, l);
                    }
                }
                @Override
                public void unscheduleDrawable(@NonNull Drawable drawable, @NonNull Runnable runnable) {
                    if (progress < 1.0f) {
                        CrossfadeDrawable.this.unscheduleSelf(runnable);
                    }
                }
            });
        }
        if (bottomDrawable != null) {
            bottomDrawable.setCallback(bottomCallback = new Callback() {
                @Override
                public void invalidateDrawable(@NonNull Drawable drawable) {
                    if (progress > 0.0f) {
                        CrossfadeDrawable.this.invalidateSelf();
                    }
                }
                @Override
                public void scheduleDrawable(@NonNull Drawable drawable, @NonNull Runnable runnable, long l) {
                    if (progress > 0.0f) {
                        CrossfadeDrawable.this.scheduleSelf(runnable, l);
                    }
                }
                @Override
                public void unscheduleDrawable(@NonNull Drawable drawable, @NonNull Runnable runnable) {
                    if (progress > 0.0f) {
                        CrossfadeDrawable.this.unscheduleSelf(runnable);
                    }
                }
            });
        }
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        topDrawable.setBounds(bounds);
        bottomDrawable.setBounds(bounds);
    }

    @Override
    public void draw(Canvas canvas) {
        updateChildVisibility(false);
        int topAlpha, bottomAlpha;
        topDrawable.setAlpha(topAlpha = (int) (globalAlpha * (1.0f - progress)));
        bottomDrawable.setAlpha(bottomAlpha = (int) (globalAlpha * progress));
        if (topAlpha > 0) {
            topDrawable.draw(canvas);
        }
        if (bottomAlpha > 0) {
            bottomDrawable.draw(canvas);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        globalAlpha = alpha;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        topDrawable.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return topDrawable.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return topDrawable.getIntrinsicHeight();
    }

    public float getProgress() {
        return progress;
    }

    public void setProgress(float value) {
        progress = value;
        updateChildVisibility(false);
        invalidateSelf();
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        updateChildVisibility(restart);
        return changed;
    }

    private ValueAnimator animator;
    public void animateToProgress(float value) {
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(getProgress(), value);
        animator.addUpdateListener(a -> {
            setProgress((float) a.getAnimatedValue());
            invalidateSelf();
        });
        animator.setDuration((long) (200 * Math.abs(getProgress() - value)));
        animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animator.start();
    }

    private void updateChildVisibility(boolean restart) {
        topDrawable.setVisible(isVisible() && progress < 1.0f, restart);
        bottomDrawable.setVisible(isVisible() && progress > 0.0f, restart);
    }
}