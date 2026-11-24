package org.telegram.ui.Components;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.StateListAnimator;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import java.util.function.Consumer;

public class ScaleStateListAnimator {

    public static void apply(View view) {
        apply(view, .1f, 1.5f, null, null);
    }

    public static void apply(View view, float scale, float tension) {
        apply(view, scale, tension, null, null);
    }

    public static void apply(View view, Consumer<Float> pressedValueUpdater, Consumer<Float> releasedValueUpdater) {
        apply(view, .1f, 1.5f, pressedValueUpdater, releasedValueUpdater);
    }

    public static void apply(View view, float scale, float tension, Consumer<Float> pressedValueUpdater, Consumer<Float> releasedValueUpdater) {
        if (view == null) {
            return;
        }

        AnimatorSet pressedAnimatorSet = new AnimatorSet();
        AnimatorSet pressedScaleAnimator = new AnimatorSet();
        pressedScaleAnimator.playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, 1f - scale),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f - scale)
        );
        pressedScaleAnimator.setDuration(80);

        AnimatorSet defaultAnimatorSet = new AnimatorSet();
        AnimatorSet defaultScaleAnimator = new AnimatorSet();
        defaultScaleAnimator.playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f)
        );
        defaultScaleAnimator.setInterpolator(new OvershootInterpolator(tension));
        defaultScaleAnimator.setDuration(350);


        StateListAnimator scaleStateListAnimator = new StateListAnimator();

        if (pressedValueUpdater != null) {
            ValueAnimator pressedValueAnimator = ValueAnimator.ofFloat(0f, 1f);
            pressedValueAnimator.setDuration(80);
            pressedValueAnimator.addUpdateListener(animation -> pressedValueUpdater.accept((Float) animation.getAnimatedValue()));
            pressedAnimatorSet.playTogether(pressedScaleAnimator, pressedValueAnimator);
            scaleStateListAnimator.addState(new int[]{android.R.attr.state_pressed}, pressedAnimatorSet);
        } else {
            scaleStateListAnimator.addState(new int[]{android.R.attr.state_pressed}, pressedScaleAnimator);
        }

        if (releasedValueUpdater != null) {
            ValueAnimator defaultValueAnimator = ValueAnimator.ofFloat(1f, 0f);
            defaultValueAnimator.setDuration(350);
            defaultValueAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            defaultValueAnimator.addUpdateListener(animation -> releasedValueUpdater.accept((Float) animation.getAnimatedValue()));
            defaultAnimatorSet.playTogether(defaultScaleAnimator, defaultValueAnimator);
            scaleStateListAnimator.addState(new int[0], defaultAnimatorSet);
        } else {
            scaleStateListAnimator.addState(new int[0], defaultScaleAnimator);
        }


        view.setStateListAnimator(scaleStateListAnimator);
    }

    public static void reset(View view) {
        view.setStateListAnimator(null);
    }
}