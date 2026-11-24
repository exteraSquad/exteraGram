/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui.tabs;

import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import me.vkryl.android.animator.FactorAnimator;
import com.exteragram.messenger.utils.ui.material.Material3Tokens;

public final class TabsVisibilityAnimator {

    private static final float MIN_VISIBLE_CHANGE = 0.001f;

    private final int id;
    private final FactorAnimator.Target target;
    private final FloatValueHolder positionHolder;
    private final SpringAnimation positionSpring;
    @Nullable
    private final FloatValueHolder alphaHolder;
    @Nullable
    private final SpringAnimation alphaSpring;

    private boolean value;
    private float floatValue;
    private float alphaValue;

    public TabsVisibilityAnimator(int id, FactorAnimator.Target target, boolean startValue, boolean withAlpha) {
        this.id = id;
        this.target = target;
        this.value = startValue;
        this.floatValue = startValue ? 1f : 0f;
        this.alphaValue = floatValue;

        positionHolder = new FloatValueHolder(floatValue);
        positionSpring = new SpringAnimation(positionHolder)
                .setMinimumVisibleChange(MIN_VISIBLE_CHANGE)
                .setSpring(new SpringForce(floatValue));
        positionSpring.addUpdateListener((animation, newValue, velocity) -> {
            if (floatValue != newValue) {
                floatValue = newValue;
                notifyChanged();
            }
        });
        positionSpring.addEndListener((animation, canceled, endValue, velocity) -> {
            if (!canceled) {
                target.onFactorChangeFinished(id, endValue, null);
            }
        });

        if (withAlpha) {
            alphaHolder = new FloatValueHolder(alphaValue);
            alphaSpring = new SpringAnimation(alphaHolder)
                    .setMinimumVisibleChange(MIN_VISIBLE_CHANGE)
                    .setSpring(new SpringForce(alphaValue)
                            .setStiffness(Material3Tokens.EFFECTS_STIFFNESS)
                            .setDampingRatio(Material3Tokens.EFFECTS_DAMPING));
            alphaSpring.addUpdateListener((animation, newValue, velocity) -> {
                if (alphaValue != newValue) {
                    alphaValue = newValue;
                    notifyChanged();
                }
            });
        } else {
            alphaHolder = null;
            alphaSpring = null;
        }
    }

    public void setValue(boolean value, boolean animated) {
        if (this.value == value && animated) {
            return;
        }
        this.value = value;
        final float toValue = value ? 1f : 0f;
        if (animated) {
            final boolean material3 = MainTabsUiHelper.isMaterial3NavigationBar();
            positionSpring.getSpring()
                    .setStiffness(material3 ? Material3Tokens.STANDARD_SPATIAL_STIFFNESS : Material3Tokens.DEFAULT_SPATIAL_STIFFNESS)
                    .setDampingRatio(material3 ? Material3Tokens.STANDARD_SPATIAL_DAMPING : Material3Tokens.DEFAULT_SPATIAL_DAMPING);
            positionSpring.animateToFinalPosition(toValue);
            if (alphaSpring != null) {
                alphaSpring.animateToFinalPosition(toValue);
            }
        } else {
            final boolean changed = floatValue != toValue || alphaValue != toValue;
            snapTo(toValue);
            if (changed) {
                notifyChanged();
                target.onFactorChangeFinished(id, toValue, null);
            }
        }
    }

    public void changeValueSilently(boolean newValue) {
        value = newValue;
    }

    public void changeValueSilently(float newValue) {
        snapTo(newValue);
    }

    public boolean getValue() {
        return value;
    }

    public float getFloatValue() {
        return floatValue;
    }

    public float getAlphaValue() {
        return alphaValue;
    }

    private void snapTo(float newValue) {
        positionSpring.cancel();
        positionHolder.setValue(newValue);
        floatValue = newValue;
        if (alphaSpring != null && alphaHolder != null) {
            alphaSpring.cancel();
            alphaHolder.setValue(newValue);
        }
        alphaValue = newValue;
    }

    private void notifyChanged() {
        target.onFactorChanged(id, floatValue, -1f, null);
    }
}
