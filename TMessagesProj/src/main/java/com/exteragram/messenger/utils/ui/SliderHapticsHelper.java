/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui;

import android.media.AudioAttributes;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;

public final class SliderHapticsHelper {

    private static final float PROGRESS_DRAG_MAX_SCALE = 0.2f;
    private static final float VELOCITY_MAX_BUMP = 0.15f;
    private static final float DRAG_PROGRESS_THRESHOLD = 0.015f;
    private static final int LOW_TICKS = 5;
    private static final float MAX_VELOCITY = 2000f;
    private static final float LOWER_BOOKEND_SCALE = 0.05f;
    private static final float UPPER_BOOKEND_SCALE = 1f;
    private static final float EXPONENT = 1f / 0.89f;
    private static final long FALLBACK_TICK_MS = 4;
    private static final long FALLBACK_INTERVAL_MS = 40;

    private static final long RAMP_STEP_MS = 25;
    private static final float RAMP_START_SCALE = 0.05f;
    private static final float RAMP_END_SCALE = 0.4f;

    private static Vibrator vibrator;
    private static long rampEndTime;
    private static Boolean primitivesSupported;
    private static long dragIntervalMs;

    private VelocityTracker velocityTracker;
    private float velocity;
    private long lastDragTime;
    private float lastDragProgress = -1f;
    private boolean vibratedAtLower;
    private boolean vibratedAtUpper;

    public void onTouchEvent(MotionEvent event) {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            velocityTracker.clear();
            velocity = 0;
            lastDragProgress = -1f;
        }
        velocityTracker.addMovement(event);
        velocityTracker.computeCurrentVelocity(1000);
        velocity = Math.abs(velocityTracker.getXVelocity());
    }

    public void onDrag(View view, float progress, int stepsCount, boolean stepChanged, boolean stop) {
        if (view == null || !ExteraConfig.getInAppVibration() || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        final boolean atLower = progress <= 0f;
        final boolean atUpper = progress >= 1f;
        final boolean continuous = stepsCount * DRAG_PROGRESS_THRESHOLD > 1f;
        if (!continuous) {
            if (stepChanged) {
                if (atLower || atUpper) {
                    vibrateOnEdge(view);
                } else if (isSupported()) {
                    vibrateDrag(progress, false);
                } else {
                    AndroidUtilities.vibrateCursor(view);
                }
            }
            return;
        }
        if (atLower || atUpper) {
            if (atLower ? !vibratedAtLower : !vibratedAtUpper) {
                vibrateOnEdge(view);
                vibratedAtLower = atLower;
                vibratedAtUpper = atUpper;
            }
            return;
        }
        vibratedAtLower = vibratedAtUpper = false;
        if (!stop) {
            vibrateDrag(progress, true);
        }
    }

    private void vibrateDrag(float progress, boolean continuous) {
        final long now = SystemClock.elapsedRealtime();
        final boolean supported = isSupported();
        if (now - lastDragTime < (supported ? dragIntervalMs : FALLBACK_INTERVAL_MS)) {
            return;
        }
        if (Math.abs(progress - lastDragProgress) < DRAG_PROGRESS_THRESHOLD) {
            return;
        }
        final float v = Math.min(velocity / MAX_VELOCITY, 1f);
        final float scale = (float) Math.pow(PROGRESS_DRAG_MAX_SCALE * progress * progress + VELOCITY_MAX_BUMP * v * v, EXPONENT);
        if (scale <= 0f) {
            return;
        }
        if (supported) {
            final VibrationEffect.Composition composition = VibrationEffect.startComposition();
            if (continuous) {
                for (int i = 0; i < LOW_TICKS; i++) {
                    composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, scale);
                }
            } else {
                composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, scale);
            }
            vibrate(composition.compose());
        } else if (vibrator().hasAmplitudeControl()) {
            vibrate(VibrationEffect.createOneShot(FALLBACK_TICK_MS, Math.max(1, Math.round(scale * 255))));
        }
        lastDragTime = now;
        lastDragProgress = progress;
    }

    private void vibrateOnEdge(View view) {
        if (!isSupported()) {
            AndroidUtilities.vibrateCursor(view);
            return;
        }
        final float v = Math.min(velocity / MAX_VELOCITY, 1f);
        final float scale = (float) Math.pow((UPPER_BOOKEND_SCALE - LOWER_BOOKEND_SCALE) * v * v + LOWER_BOOKEND_SCALE, EXPONENT);
        vibrate(VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, scale)
            .compose());
    }

    public static boolean vibratePrimitive(int primitive, float scale) {
        if (!ExteraConfig.getInAppVibration()) {
            return true;
        }
        if (!isSupported()) {
            return false;
        }
        vibrate(VibrationEffect.startComposition().addPrimitive(primitive, scale).compose());
        return true;
    }

    public static void startLongPressRamp(long durationMs) {
        if (!ExteraConfig.getInAppVibration() || !isSupported()) {
            return;
        }
        final int steps = Math.max(1, (int) (durationMs / RAMP_STEP_MS));
        final int delay = (int) Math.max(0, RAMP_STEP_MS - dragIntervalMs / LOW_TICKS);
        final VibrationEffect.Composition composition = VibrationEffect.startComposition();
        for (int i = 0; i < steps; i++) {
            final float t = (i + 1f) / steps;
            composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, AndroidUtilities.lerp(RAMP_START_SCALE, RAMP_END_SCALE, t * t), i == 0 ? 0 : delay);
        }
        vibrate(composition.compose());
        rampEndTime = SystemClock.elapsedRealtime() + durationMs;
    }

    public static void cancelLongPressRamp() {
        if (rampEndTime == 0) {
            return;
        }
        final boolean running = SystemClock.elapsedRealtime() < rampEndTime;
        rampEndTime = 0;
        if (running) {
            try {
                vibrator().cancel();
            } catch (Exception ignore) {}
        }
    }

    private static Vibrator vibrator() {
        if (vibrator == null) {
            vibrator = ApplicationLoader.applicationContext.getSystemService(Vibrator.class);
        }
        return vibrator;
    }

    private static boolean isSupported() {
        if (primitivesSupported == null) {
            boolean supported = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    final Vibrator v = vibrator();
                    supported = v != null && v.hasVibrator() && v.areAllPrimitivesSupported(
                        VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                        VibrationEffect.Composition.PRIMITIVE_TICK,
                        VibrationEffect.Composition.PRIMITIVE_CLICK
                    );
                    if (supported) {
                        dragIntervalMs = (long) v.getPrimitiveDurations(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)[0] * LOW_TICKS;
                    }
                } catch (Exception ignore) {
                    supported = false;
                }
            }
            primitivesSupported = supported;
        }
        return primitivesSupported;
    }

    @SuppressWarnings("deprecation")
    private static void vibrate(VibrationEffect effect) {
        try {
            final Vibrator v = vibrator();
            if (v == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH));
            } else {
                v.vibrate(effect, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build());
            }
        } catch (Exception ignore) {}
    }
}
