/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui.material;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.graphics.RectF;
import android.os.Build;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;

import androidx.core.graphics.ColorUtils;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;

import me.vkryl.android.animator.BoolAnimator;

public final class SwitchUiHelper {

    private static final float MATERIAL3_TRACK_WIDTH = 52f;
    private static final float MATERIAL3_TRACK_HEIGHT = 32f;
    private static final float MATERIAL3_TRACK_OUTLINE_WIDTH = 2f;
    private static final float MATERIAL3_THUMB_BOX_SIZE = 32f;
    private static final float MATERIAL3_THUMB_RADIUS_UNSELECTED = 8f;
    private static final float MATERIAL3_THUMB_RADIUS_SELECTED = 12f;
    private static final float MATERIAL3_THUMB_RADIUS_PRESSED = 14f;
    private static final float MATERIAL3_THUMB_RADIUS_MORPHING = 11f;
    private static final float MATERIAL3_THUMB_MORPHING_SPAN = 5f;
    private static final float MATERIAL3_THUMB_ICON_SIZE = 16f;
    private static final float MATERIAL3_ICON_GRID = 24f;
    private static final float MATERIAL3_ICON_LIVE_AREA = 20f;
    private static final float MATERIAL3_ICON_STROKE = 2f;
    private static final float[] MATERIAL3_ICON_CROSS = {6f, 6f, 18f, 18f, 6f, 18f, 18f, 6f};
    private static final float[] MATERIAL3_ICON_CHECK = {4.5f, 13.1f, 9f, 17.6f, 9f, 17.6f, 19.8f, 6.8f};
    private static final float MATERIAL3_ICON_CLOCK_MINUTE_HAND = 8.5f;
    private static final float MATERIAL3_ICON_CLOCK_HOUR_HAND = 6.8f;
    private static final float MATERIAL3_STATE_LAYER_SIZE = 40f;

    private static final float MATERIAL3_SCALED_TRACK_HEIGHT_DP = 26f;
    private static final float MATERIAL3_SCALE = MATERIAL3_SCALED_TRACK_HEIGHT_DP / MATERIAL3_TRACK_HEIGHT;

    private static final float MATERIAL3_LIGHT_TRACK_TONE = 90f;
    private static final float MATERIAL3_DARK_TRACK_TONE = 22f;
    private static final float MATERIAL3_LIGHT_OUTLINE_TONE = 50f;
    private static final float MATERIAL3_DARK_OUTLINE_TONE = 60f;
    private static final float NON_MONET_LIGHT_OUTLINE_OPACITY = 0.55f;
    private static final float NON_MONET_DARK_OUTLINE_OPACITY = 0.8f;
    private static final double MATERIAL3_TRACK_MAX_CHROMA = 32.0;

    private static final long MATERIAL3_THUMB_PRESSED_DURATION = 100;
    private static final float MATERIAL3_THUMB_MORPHING_SPLIT_SELECTED = 150f / 250f;
    private static final float MATERIAL3_THUMB_MORPHING_SPLIT_UNSELECTED = 100f / 250f;
    private static final Interpolator MATERIAL3_THUMB_INTERPOLATOR = new AccelerateDecelerateInterpolator();

    private static final float MATERIAL3_THUMB_SPRING_DAMPING_RATIO = 0.65f;
    private static final float MATERIAL3_THUMB_SPRING_STIFFNESS = 510f;
    private static final float MATERIAL3_THUMB_SPRING_MIN_VISIBLE_CHANGE = 1f / 1000f;

    private static final int MATERIAL3_OVERLAY_PADDING_DP = 5;
    private static final int LEGACY_STATE_LAYER_RADIUS_DP = 18;


    private SwitchUiHelper() {
    }

    public static boolean isMaterial3SwitchStyle() {
        return ExteraConfig.getNewSwitchStyle();
    }

    private static float scaled(float units) {
        return dpf2(units * MATERIAL3_SCALE);
    }

    public static int getOverlayPadding() {
        return isMaterial3SwitchStyle() ? dp(MATERIAL3_OVERLAY_PADDING_DP) : 0;
    }

    public static int getStateLayerRadius() {
        return isMaterial3SwitchStyle() ? (int) scaled(MATERIAL3_STATE_LAYER_SIZE / 2f) : dp(LEGACY_STATE_LAYER_RADIUS_DP);
    }

    public static <K> SpringAnimation createThumbSpring(K target, FloatPropertyCompat<K> property) {
        return new SpringAnimation(target, property)
                .setMinimumVisibleChange(MATERIAL3_THUMB_SPRING_MIN_VISIBLE_CHANGE)
                .setSpring(new SpringForce()
                        .setDampingRatio(MATERIAL3_THUMB_SPRING_DAMPING_RATIO)
                        .setStiffness(MATERIAL3_THUMB_SPRING_STIFFNESS));
    }

    public static BoolAnimator createThumbPressedAnimator(View view) {
        return new BoolAnimator(view, CubicBezierInterpolator.Standard, MATERIAL3_THUMB_PRESSED_DURATION);
    }

    public static void setTrackBounds(RectF out, int viewWidth, int viewHeight) {
        final float halfWidth = scaled(MATERIAL3_TRACK_WIDTH / 2f);
        final float halfHeight = scaled(MATERIAL3_TRACK_HEIGHT / 2f);
        out.set(viewWidth / 2f - halfWidth, viewHeight / 2f - halfHeight, viewWidth / 2f + halfWidth, viewHeight / 2f + halfHeight);
    }

    public static float getTrackOutlineWidth() {
        return scaled(MATERIAL3_TRACK_OUTLINE_WIDTH);
    }

    public static int getUnselectedTrackColor(int trackColorKey, Theme.ResourcesProvider resourcesProvider) {
        if (!isMaterial3SwitchStyle()) {
            return Theme.getColor(trackColorKey, resourcesProvider);
        }
        return trackAtTone(trackColorKey, resourcesProvider, MATERIAL3_LIGHT_TRACK_TONE, MATERIAL3_DARK_TRACK_TONE);
    }

    public static int getUnselectedThumbColor(int trackColorKey, int thumbColorKey, Theme.ResourcesProvider resourcesProvider) {
        if (!isMaterial3SwitchStyle()) {
            return Theme.getColor(thumbColorKey, resourcesProvider);
        }
        final int outline = trackAtTone(trackColorKey, resourcesProvider, MATERIAL3_LIGHT_OUTLINE_TONE, MATERIAL3_DARK_OUTLINE_TONE);
        if (Theme.isCurrentThemeMonet(resourcesProvider)) {
            return outline;
        }
        final int track = trackAtTone(trackColorKey, resourcesProvider, MATERIAL3_LIGHT_TRACK_TONE, MATERIAL3_DARK_TRACK_TONE);
        return ColorUtils.blendARGB(track, outline, isLightSurface(resourcesProvider) ? NON_MONET_LIGHT_OUTLINE_OPACITY : NON_MONET_DARK_OUTLINE_OPACITY);
    }

    public static int getSelectedThumbColor(int thumbCheckedColorKey, Theme.ResourcesProvider resourcesProvider) {
        if (isMaterial3SwitchStyle() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Theme.isCurrentThemeMonet(resourcesProvider)) {
            return Theme.getColor(Theme.key_switchTrackBlueThumbChecked, resourcesProvider);
        }
        return Theme.getColor(thumbCheckedColorKey, resourcesProvider);
    }

    private static boolean isLightSurface(Theme.ResourcesProvider resourcesProvider) {
        return ColorTones.isLightSurface(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
    }

    private static int trackAtTone(int trackColorKey, Theme.ResourcesProvider resourcesProvider, float lightTone, float darkTone) {
        return ColorTones.withTone(Theme.getColor(trackColorKey, resourcesProvider), isLightSurface(resourcesProvider) ? lightTone : darkTone, MATERIAL3_TRACK_MAX_CHROMA);
    }

    public static float getThumbCenterX(int viewWidth, float thumbPosition) {
        return viewWidth / 2f + (thumbPosition * 2f - 1f) * scaled((MATERIAL3_TRACK_WIDTH - MATERIAL3_THUMB_BOX_SIZE) / 2f);
    }

    public static void setThumbBounds(RectF out, float centerX, float centerY, float progress, boolean checked, float withIconProgress, float pressedProgress) {
        final float restingRadius = lerp(MATERIAL3_THUMB_RADIUS_UNSELECTED, MATERIAL3_THUMB_RADIUS_SELECTED, withIconProgress);
        final float motionTime = (float) (Math.acos(1f - 2f * Utilities.clamp01(progress)) / Math.PI);
        final float morphTime = checked ? motionTime : 1f - motionTime;
        final float morphSplit = checked ? MATERIAL3_THUMB_MORPHING_SPLIT_SELECTED : MATERIAL3_THUMB_MORPHING_SPLIT_UNSELECTED;
        final float fromRadius = checked ? restingRadius : MATERIAL3_THUMB_RADIUS_SELECTED;
        final float toRadius = checked ? MATERIAL3_THUMB_RADIUS_SELECTED : restingRadius;

        float radius;
        float span;
        if (morphTime < morphSplit) {
            final float morphProgress = MATERIAL3_THUMB_INTERPOLATOR.getInterpolation(morphTime / morphSplit);
            radius = lerp(fromRadius, MATERIAL3_THUMB_RADIUS_MORPHING, morphProgress);
            span = MATERIAL3_THUMB_MORPHING_SPAN * morphProgress;
        } else {
            final float morphProgress = MATERIAL3_THUMB_INTERPOLATOR.getInterpolation((morphTime - morphSplit) / (1f - morphSplit));
            radius = lerp(MATERIAL3_THUMB_RADIUS_MORPHING, toRadius, morphProgress);
            span = MATERIAL3_THUMB_MORPHING_SPAN * (1f - morphProgress);
        }
        if (pressedProgress > 0) {
            radius = lerp(radius, MATERIAL3_THUMB_RADIUS_PRESSED, pressedProgress);
            span *= 1f - pressedProgress;
        }

        final float scaledRadius = scaled(radius);
        final float scaledSpan = scaled(span);
        out.set(centerX - scaledSpan - scaledRadius, centerY - scaledRadius, centerX + scaledSpan + scaledRadius, centerY + scaledRadius);
    }

    public static float getThumbIconScale(int intrinsicWidth, int intrinsicHeight) {
        return scaled(MATERIAL3_THUMB_ICON_SIZE * MATERIAL3_ICON_LIVE_AREA / MATERIAL3_ICON_GRID) / Math.max(intrinsicWidth, intrinsicHeight);
    }

    private static float iconGrid(float units) {
        return scaled(units * MATERIAL3_THUMB_ICON_SIZE / MATERIAL3_ICON_GRID);
    }

    public static float getIconStrokeWidth() {
        return iconGrid(MATERIAL3_ICON_STROKE);
    }

    public static void setCheckIconLines(float[] out, float centerX, float centerY, float checkProgress) {
        final float progress = Utilities.clamp01(checkProgress);
        for (int i = 0; i < out.length; i++) {
            final float value = iconGrid(lerp(MATERIAL3_ICON_CROSS[i], MATERIAL3_ICON_CHECK[i], progress) - MATERIAL3_ICON_GRID / 2f);
            out[i] = value + (i % 2 == 0 ? centerX : centerY);
        }
    }

    public static float getClockHandLength(boolean minute) {
        return iconGrid(minute ? MATERIAL3_ICON_CLOCK_MINUTE_HAND : MATERIAL3_ICON_CLOCK_HOUR_HAND);
    }
}
