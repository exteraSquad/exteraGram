package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

public class ChatSearchTabs extends FrameLayout {

    public ViewPagerFixed.TabsView tabs;

    public ChatSearchTabs(@NonNull Context context) {
        super(context);
    }

    public void setTabs(ViewPagerFixed.TabsView tabs) {
        this.tabs = tabs;
        addView(tabs, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    public float shownT;
    public void setShown(float shownT) {
        this.shownT = shownT;
        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            child.setPivotX(child.getWidth() / 2f);
            child.setPivotY(0);
            child.setScaleX(lerp(0.8f, 1, shownT));
            child.setScaleY(lerp(0.8f, 1, shownT));
        }
        setAlpha(shownT);
        invalidate();
    }

    protected void onShownUpdate(boolean finish) {

    }

    private boolean shown;
    private float actionBarTagsT;
    private ValueAnimator actionBarTagsAnimator;
    public void show(boolean show) {
        show(show, true);
    }

    public void show(boolean show, boolean animated) {
        shown = show;
        if (actionBarTagsAnimator != null) {
            Animator a = actionBarTagsAnimator;
            actionBarTagsAnimator = null;
            a.cancel();
        }
        if (!animated) {
            actionBarTagsT = show ? 1f : 0f;
            setVisibility(show ? View.VISIBLE : View.GONE);
            setShown(actionBarTagsT);
            onShownUpdate(true);
            return;
        }
        if (show) {
            setVisibility(View.VISIBLE);
        }
        actionBarTagsAnimator = ValueAnimator.ofFloat(actionBarTagsT, show ? 1f : 0f);
        actionBarTagsAnimator.addUpdateListener(valueAnimator1 -> {
            actionBarTagsT = (float) valueAnimator1.getAnimatedValue();
            setShown(actionBarTagsT);
            onShownUpdate(false);
        });
        actionBarTagsAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        actionBarTagsAnimator.setDuration(320);
        actionBarTagsAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation != actionBarTagsAnimator) return;
                actionBarTagsT = show ? 1f : 0f;
                setShown(actionBarTagsT);
                if (!show) {
                    setVisibility(View.GONE);
                }
                onShownUpdate(true);
            }
        });
        actionBarTagsAnimator.start();
    }

    public boolean isShown() {
        return shown;
    }

    public boolean shown() {
        return shownT > 0.5f;
    }

    public int getCurrentHeight() {
        return (int) (getMeasuredHeight() * shownT);
    }
}
