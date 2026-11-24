/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.ui;

import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import com.exteragram.messenger.pillstack.core.PillStackConfig;
import com.exteragram.messenger.pillstack.ui.pills.BasePill;

import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.ArrayList;
import java.util.List;

public class PillStackView extends FrameLayout {

    private final List<BasePill> pills = new ArrayList<>();
    private int currentIndex = 0;

    private final float touchSlop;
    private float startY;
    private float startX;
    private boolean isSwiping;
    private boolean maybeClick;
    private boolean longClickPerformed;

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (maybeClick && !isSwiping && !pills.isEmpty()) {
                longClickPerformed = pills.get(currentIndex).onPillLongClicked();
                if (longClickPerformed) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    maybeClick = false;
                    for (int i = 0; i < pills.size(); i++) {
                        pills.get(i).setPressed(false);
                    }
                }
            }
        }
    };

    private float currentSwipeProgress = 0f;
    private boolean isSwipingUp = false;
    private ValueAnimator currentAnimator;

    private float visibilityFactor = -1.0f;
    private boolean stackOnScreen = true;
    private static final float START_SCALE = 0.6f;

    public PillStackView(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClipChildren(false);
    }

    public void addPill(BasePill pill) {
        pills.add(pill);
        addView(pill);

        if (pills.size() - 1 != currentIndex) {
            pill.setAlpha(0f);
            pill.setScaleX(0.8f);
            pill.setScaleY(0.8f);
            pill.setVisibility(View.GONE);
        } else {
            pill.setVisibility(View.VISIBLE);
            pill.onPillSelected();
        }

        pill.onStackVisibilityChanged(stackOnScreen);
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (stackOnScreen == isVisible) {
            return;
        }
        stackOnScreen = isVisible;
        for (BasePill pill : pills) {
            pill.onStackVisibilityChanged(isVisible);
        }
    }

    public int getPillsCount() {
        return pills.size();
    }

    public void setCurrentIndex(int index) {
        if (index < 0 || index >= pills.size() || index == currentIndex) return;

        BasePill oldPill = pills.get(currentIndex);
        oldPill.setVisibility(View.GONE);
        oldPill.onPillUnselected();

        currentIndex = index;

        BasePill newPill = pills.get(currentIndex);
        newPill.setVisibility(View.VISIBLE);
        newPill.setAlpha(1f);
        newPill.setScaleX(1f);
        newPill.setScaleY(1f);
        newPill.setTranslationY(0f);
        newPill.onPillSelected();

        requestLayout();
    }

    public void clearPills() {
        if (!pills.isEmpty() && currentIndex < pills.size()) {
            pills.get(currentIndex).onPillUnselected();
        }
        pills.clear();
        removeAllViews();
        currentIndex = 0;
    }

    public void setVisibilityFactor(float factor) {
        if (this.visibilityFactor == factor) return;
        this.visibilityFactor = factor;
        if (visibilityFactor > 0.01f) {
            if (getVisibility() != VISIBLE) {
                setVisibility(VISIBLE);
            }
            setAlpha(visibilityFactor);
            setScaleX(lerp(START_SCALE, 1f, visibilityFactor));
            setScaleY(lerp(START_SCALE, 1f, visibilityFactor));
        } else {
            setVisibility(GONE);
        }
    }

    public void updateColors() {
        for (BasePill pill : pills) {
            pill.updateColors();
        }
    }

    public void refresh() {
        if (!pills.isEmpty()) {
            pills.get(currentIndex).onUpdateData(true);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (pills.isEmpty()) return super.onInterceptTouchEvent(ev);

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = ev.getRawX();
                startY = ev.getRawY();
                isSwiping = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getRawX() - startX;
                float dy = ev.getRawY() - startY;
                if (Math.abs(dy) > touchSlop || Math.abs(dx) > touchSlop) {
                    if (Math.abs(dy) > touchSlop && pills.size() > 1) {
                        isSwiping = true;

                        if (currentAnimator != null) {
                            currentAnimator.cancel();
                        }
                        float offsetDy = isSwipingUp ? -(currentSwipeProgress * getHeight()) : (currentSwipeProgress * getHeight());
                        startY = ev.getRawY() - offsetDy;

                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        return true;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (pills.isEmpty()) return super.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startX = event.getRawX();
                startY = event.getRawY();
                isSwiping = false;
                maybeClick = true;
                longClickPerformed = false;
                removeCallbacks(longPressRunnable);
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                BasePill pill = pills.get(currentIndex);
                pill.setPressed(true);
                pill.drawableHotspotChanged(event.getX() - pill.getLeft(), event.getY() - pill.getTop());
                return true;

            case MotionEvent.ACTION_MOVE:
                if (longClickPerformed) {
                    return true;
                }
                float dx = event.getRawX() - startX;
                float dy = event.getRawY() - startY;

                if (Math.abs(dy) > touchSlop || Math.abs(dx) > touchSlop) {
                    maybeClick = false;
                    removeCallbacks(longPressRunnable);
                }

                if (isSwiping) {
                    handleSwipeProgress(dy);
                    return true;
                } else if (Math.abs(dy) > touchSlop && pills.size() > 1) {
                    isSwiping = true;

                    if (currentAnimator != null) {
                        currentAnimator.cancel();
                    }
                    float offsetDy = isSwipingUp ? -(currentSwipeProgress * getHeight()) : (currentSwipeProgress * getHeight());
                    startY = event.getRawY() - offsetDy;

                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPressRunnable);
                if (isSwiping) {
                    float finalDy = event.getRawY() - startY;
                    finishSwipe(finalDy);
                    isSwiping = false;
                } else if (maybeClick && !longClickPerformed && event.getActionMasked() == MotionEvent.ACTION_UP) {
                    pills.get(currentIndex).onPillClicked();
                }
                for (int i = 0; i < pills.size(); i++) {
                    pills.get(i).setPressed(false);
                }
                maybeClick = false;
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void handleSwipeProgress(float dy) {
        if (pills.size() <= 1) return;
        int height = getHeight();
        if (height <= 0) return;

        isSwipingUp = dy < 0;
        float rawProgress = Math.abs(dy) / (float) height;

        int nextIndex = isSwipingUp ? (currentIndex + 1) : (currentIndex - 1);
        boolean isOutOfBounds = !PillStackConfig.getInfiniteScrolling() &&
                (nextIndex >= pills.size() || nextIndex < 0);

        if (isOutOfBounds) {
            currentSwipeProgress = rawProgress;
        } else {
            currentSwipeProgress = Math.min(rawProgress, 1.0f);
        }

        applyProgress(currentSwipeProgress, isSwipingUp);
    }

    private void applyProgress(float progress, boolean swipingUp) {
        BasePill currentPill = pills.get(currentIndex);
        int nextIndex = swipingUp ? (currentIndex + 1) : (currentIndex - 1);

        if (PillStackConfig.getInfiniteScrolling()) {
            if (nextIndex >= pills.size()) nextIndex = 0;
            if (nextIndex < 0) nextIndex = pills.size() - 1;
        }

        for (int i = 0; i < pills.size(); i++) {
            if (i != currentIndex && i != nextIndex) {
                if (pills.get(i).getVisibility() != View.GONE) {
                    pills.get(i).setVisibility(View.GONE);
                }
            }
        }

        if (!PillStackConfig.getInfiniteScrolling()) {
            if (nextIndex >= pills.size() || nextIndex < 0) {
                float height = getHeight();
                float dampedProgress = (float) (1.0 - (1.0 / (progress * 0.18f + 1.0)));
                currentPill.setTranslationY(swipingUp ? -(height * dampedProgress) : (height * dampedProgress));
                currentPill.setAlpha(1f);
                return;
            }
        }

        float clampedProgress = Math.min(progress, 1.0f);
        BasePill nextPill = pills.get(nextIndex);

        if (nextPill.getVisibility() != View.VISIBLE) {
            nextPill.setVisibility(View.VISIBLE);
        }

        float currentTranslationY = swipingUp ? -(getHeight() * clampedProgress) : (getHeight() * clampedProgress);
        currentPill.setTranslationY(currentTranslationY);
        currentPill.setAlpha(1f - clampedProgress);
        currentPill.setScaleX(1f - (0.2f * clampedProgress));
        currentPill.setScaleY(1f - (0.2f * clampedProgress));

        float nextScale = 0.8f + (0.2f * clampedProgress);
        nextPill.setScaleX(nextScale);
        nextPill.setScaleY(nextScale);
        nextPill.setAlpha(clampedProgress);

        float startOffset = swipingUp ? getHeight() : -getHeight();
        nextPill.setTranslationY(startOffset - (startOffset * clampedProgress));
    }

    private void finishSwipe(float dy) {
        int height = getHeight();
        if (height <= 0) {
            cancelSwipe(isSwipingUp);
            return;
        }
        float threshold = height * 0.25f;

        boolean canAnimate = true;
        if (!PillStackConfig.getInfiniteScrolling()) {
            int nextIndex = isSwipingUp ? (currentIndex + 1) : (currentIndex - 1);
            if (nextIndex >= pills.size() || nextIndex < 0) {
                canAnimate = false;
            }
        }

        if (Math.abs(dy) > threshold && canAnimate) {
            animateToNextPill(isSwipingUp);
        } else {
            cancelSwipe(isSwipingUp);
        }
    }

    private void animateToNextPill(boolean swipedUp) {
        if (currentAnimator != null) currentAnimator.cancel();

        currentAnimator = ValueAnimator.ofFloat(currentSwipeProgress, 1f);
        currentAnimator.setDuration(250);
        currentAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);

        currentAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            applyProgress(progress, swipedUp);
        });

        currentAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled) return;

                BasePill oldPill = pills.get(currentIndex);
                oldPill.setVisibility(View.GONE);
                oldPill.setPressed(false);
                oldPill.setScaleX(1f);
                oldPill.setScaleY(1f);
                oldPill.onPillUnselected();

                currentIndex = swipedUp ? (currentIndex + 1) : (currentIndex - 1);
                if (PillStackConfig.getInfiniteScrolling()) {
                    if (currentIndex >= pills.size()) currentIndex = 0;
                    if (currentIndex < 0) currentIndex = pills.size() - 1;
                }

                for (int i = 0; i < pills.size(); i++) {
                    if (i != currentIndex) {
                        pills.get(i).setVisibility(View.GONE);
                    }
                }

                BasePill newPill = pills.get(currentIndex);
                newPill.setVisibility(View.VISIBLE);
                newPill.setScaleX(1f);
                newPill.setScaleY(1f);
                newPill.setTranslationY(0f);
                newPill.setAlpha(1f);
                newPill.onPillSelected();
                currentSwipeProgress = 0f;

                PillStackConfig.saveLastActivePillId(newPill.getPillId());
            }
        });
        currentAnimator.start();
    }

    private void cancelSwipe(boolean swipedUp) {
        if (currentAnimator != null) currentAnimator.cancel();

        currentAnimator = ValueAnimator.ofFloat(currentSwipeProgress, 0f);
        currentAnimator.setDuration(200);

        currentAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            applyProgress(progress, swipedUp);
        });

        currentAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled) return;

                for (int i = 0; i < pills.size(); i++) {
                    if (i != currentIndex) {
                        BasePill p = pills.get(i);
                        p.setVisibility(View.GONE);
                        p.setPressed(false);
                        p.setScaleX(1f);
                        p.setScaleY(1f);
                    }
                }

                BasePill currentPill = pills.get(currentIndex);
                currentPill.setTranslationY(0f);
                currentPill.setAlpha(1f);
                currentPill.setScaleX(1f);
                currentPill.setScaleY(1f);

                currentSwipeProgress = 0f;
            }
        });
        currentAnimator.start();
    }
}