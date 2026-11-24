/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui.material;

import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.Utilities;

public final class Material3Tokens {

    public static final float FAST_SPATIAL_DAMPING = 0.6f;
    public static final float FAST_SPATIAL_STIFFNESS = 800f;
    public static final float DEFAULT_SPATIAL_DAMPING = 0.8f;
    public static final float DEFAULT_SPATIAL_STIFFNESS = 380f;
    public static final float STANDARD_SPATIAL_DAMPING = 0.9f;
    public static final float STANDARD_SPATIAL_STIFFNESS = 700f;
    public static final float EFFECTS_DAMPING = 1f;
    public static final float EFFECTS_STIFFNESS = 1600f;
    public static final float MIN_VISIBLE_CHANGE = 1f / 500f;
    public static final float PRESSED_STATE_LAYER_OPACITY = 0.1f;

    private Material3Tokens() {
    }

    public static SpringAnimation createSpring(float damping, float stiffness, float minimumVisibleChange, Utilities.Callback<Float> onUpdate) {
        final SpringAnimation spring = new SpringAnimation(new FloatValueHolder(0f));
        spring.setMinimumVisibleChange(minimumVisibleChange);
        spring.setSpring(new SpringForce(0f).setDampingRatio(damping).setStiffness(stiffness));
        spring.addUpdateListener((animation, value, velocity) -> onUpdate.run(value));
        return spring;
    }

    public static void animate(SpringAnimation spring, float from, float to) {
        spring.cancel();
        spring.setStartValue(from);
        spring.getSpring().setFinalPosition(to);
        spring.start();
    }
}
