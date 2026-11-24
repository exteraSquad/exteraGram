/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui.material;

import static com.google.android.material.slider.LabelFormatter.LABEL_GONE;
import static com.google.android.material.slider.TickVisibilityMode.TICK_VISIBILITY_AUTO_LIMIT;
import static com.google.android.material.slider.TickVisibilityMode.TICK_VISIBILITY_HIDDEN;
import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.content.res.ColorStateList;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.math.MathUtils;

import com.google.android.material.slider.Slider;

public final class MaterialSliderUiHelper {

    private MaterialSliderUiHelper() {
    }

    public static Slider create(Context context) {
        Slider slider = new Slider(new ContextThemeWrapper(
                context,
                com.google.android.material.R.style.Theme_Material3_DayNight
        ));
        slider.setTrackHeight(dp(8));
        slider.setThumbHeight(dp(24));
        slider.setThumbWidth(dp(3));
        slider.setTrackStopIndicatorSize(0);
        slider.setHaloRadius(0);
        slider.setLabelBehavior(LABEL_GONE);
        return slider;
    }

    public static void applyContinuousStyle(Slider slider) {
        if (slider.getTickVisibilityMode() != TICK_VISIBILITY_HIDDEN) {
            slider.setTickVisibilityMode(TICK_VISIBILITY_HIDDEN);
        }
        if (slider.getContinuousModeTickCount() != 0) {
            slider.setContinuousModeTickCount(0);
        }
    }

    public static void applyDiscreteStyle(Slider slider, int tickCount) {
        if (slider.getTickVisibilityMode() != TICK_VISIBILITY_AUTO_LIMIT) {
            slider.setTickVisibilityMode(TICK_VISIBILITY_AUTO_LIMIT);
        }
        if (slider.getTickActiveRadius() != dp(2)) {
            slider.setTickActiveRadius(dp(2));
        }
        if (slider.getTickInactiveRadius() != dp(2)) {
            slider.setTickInactiveRadius(dp(2));
        }
        if (slider.getContinuousModeTickCount() != tickCount) {
            slider.setContinuousModeTickCount(tickCount);
        }
    }

    /**
     * Material sliders validate their value lazily, so an out of bounds value set here blows up
     * later in {@code onSizeChanged()} with a stack trace that points nowhere near the caller.
     * Clamping into the currently configured range keeps the legacy {@code SeekBarView} behaviour,
     * which simply pinned the thumb to the closest end.
     */
    public static void setValue(Slider slider, float value) {
        float clamped = MathUtils.clamp(value, slider.getValueFrom(), slider.getValueTo());
        if (Math.abs(slider.getValue() - clamped) > 0.0001f) {
            slider.setValue(clamped);
        }
    }

    public static void applyColors(Slider slider, int activeColor, int inactiveColor) {
        if (!hasColor(slider.getTrackActiveTintList(), activeColor)) {
            slider.setTrackActiveTintList(ColorStateList.valueOf(activeColor));
        }
        if (!hasColor(slider.getThumbTintList(), activeColor)) {
            slider.setThumbTintList(ColorStateList.valueOf(activeColor));
        }
        if (!hasColor(slider.getTrackInactiveTintList(), inactiveColor)) {
            slider.setTrackInactiveTintList(ColorStateList.valueOf(inactiveColor));
        }
    }

    public static void applyDiscreteColors(Slider slider, int activeColor, int inactiveColor, int tickColor) {
        applyColors(slider, activeColor, inactiveColor);
        if (!hasColor(slider.getTickActiveTintList(), tickColor)) {
            slider.setTickActiveTintList(ColorStateList.valueOf(tickColor));
        }
        if (!hasColor(slider.getTickInactiveTintList(), tickColor)) {
            slider.setTickInactiveTintList(ColorStateList.valueOf(tickColor));
        }
    }

    private static boolean hasColor(ColorStateList colors, int color) {
        return colors != null && colors.getDefaultColor() == color;
    }
}