/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Rect;
import android.text.Layout;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.TopViewCell;

public class ActionBarTitleCollapseHelper {

    private static final CubicBezierInterpolator TOP_TITLE_EASING = new CubicBezierInterpolator(0.8f, 0f, 0.8f, 0.15f);
    private static final int COLLAPSE_DISTANCE_DP = 48;

    private final int[] location = new int[2];
    private final Rect clip = new Rect();
    private TextView sourceTitle;
    private SimpleTextView morphedTitle;
    private boolean active;
    private boolean transformed;
    private float titleAlpha;
    private float fadeFraction = -1f;

    public boolean isActive() {
        return active;
    }

    public float getTitleAlpha() {
        return titleAlpha;
    }

    public boolean update(View actionBar, SimpleTextView barTitle, RecyclerView list, boolean centered) {
        final boolean wasActive = active;
        final float oldAlpha = titleAlpha;
        fadeFraction = -1f;

        TextView source = null;
        boolean firstItemVisible = false;
        for (int i = 0; i < list.getChildCount(); i++) {
            final View child = list.getChildAt(i);
            if (list.getChildAdapterPosition(child) == 0) {
                firstItemVisible = true;
                if (child instanceof TopViewCell) {
                    source = ((TopViewCell) child).titleView;
                }
                break;
            }
        }
        if (source != null && (source.getVisibility() != View.VISIBLE || source.getLayout() == null)) {
            source = null;
        }
        setSourceTitle(source);
        setMorphedTitle(barTitle);

        final Layout barLayout = barTitle == null ? null : barTitle.getLayout();
        if (list.getChildCount() == 0 || firstItemVisible && source == null) {
            active = false;
            resetTransforms();
        } else if (source == null) {
            active = true;
            titleAlpha = 1f;
            resetTransforms();
        } else {
            active = true;
            actionBar.getLocationInWindow(location);
            final int barX = location[0];
            final int barY = location[1];
            if (centered && barLayout != null && source.getLayout().getLineCount() == 1) {
                source.setAlpha(1f);
                morph(actionBar.getHeight(), barX, barY, barTitle, barLayout, source);
            } else {
                source.setClipBounds(null);
                resetTransforms();
                fade(actionBar.getHeight(), barY, source);
            }
        }
        return wasActive != active || oldAlpha != titleAlpha;
    }

    public void settle(RecyclerView list) {
        if (fadeFraction <= 0.01f || fadeFraction >= 1f) {
            return;
        }
        final float distance = dp(COLLAPSE_DISTANCE_DP);
        final int dy = Math.round(fadeFraction < 0.5f ? -fadeFraction * distance : (1f - fadeFraction) * distance);
        if (dy != 0) {
            list.smoothScrollBy(0, dy);
        }
    }

    private void fade(int barBottom, int barY, TextView source) {
        final Layout sourceLayout = source.getLayout();
        source.getLocationInWindow(location);
        final float textTop = location[1] - barY + source.getTotalPaddingTop() + sourceLayout.getLineTop(0);
        final float fraction = Math.max(0f, Math.min(1f, 1f - (textTop - barBottom) / dp(COLLAPSE_DISTANCE_DP)));
        fadeFraction = fraction;
        source.setAlpha(1f - fraction);
        titleAlpha = TOP_TITLE_EASING.getInterpolation(fraction);
    }

    private void morph(int barBottom, int barX, int barY, SimpleTextView barTitle, Layout barLayout, TextView source) {
        final Layout sourceLayout = source.getLayout();

        source.getLocationInWindow(location);
        final int sourceViewTop = location[1] - barY;
        final float sourceLeft = location[0] - barX + source.getTotalPaddingLeft();
        final float sourceTop = sourceViewTop + source.getTotalPaddingTop();
        final float sourceCenterX = sourceLeft + (sourceLayout.getLineLeft(0) + sourceLayout.getLineRight(0)) / 2f;
        final float sourceBaseline = sourceTop + sourceLayout.getLineBaseline(0);
        final float sourceAscent = sourceLayout.getLineBaseline(0) - sourceLayout.getLineTop(0);
        final float sourceDescent = sourceLayout.getLineBottom(0) - sourceLayout.getLineBaseline(0);

        final float restCenterX = barTitle.getTextStartX() + (barLayout.getLineLeft(0) + barLayout.getLineRight(0)) / 2f;
        final float restBaseline = barTitle.getTextStartY() + barLayout.getLineBaseline(0);

        final float morphStartBaseline = barBottom - sourceDescent;
        final float range = morphStartBaseline - restBaseline;
        final float progress;
        if (range > 0) {
            progress = Math.max(0f, Math.min(1f, (morphStartBaseline - sourceBaseline) / range));
        } else {
            progress = sourceBaseline <= restBaseline ? 1f : 0f;
        }

        if (sourceBaseline >= barBottom + sourceAscent) {
            titleAlpha = 0f;
            source.setClipBounds(null);
            resetTransforms();
            return;
        }

        titleAlpha = 1f;
        clip.set(0, Math.max(0, Math.min(source.getHeight(), barBottom - sourceViewTop)), source.getWidth(), source.getHeight());
        source.setClipBounds(clip);

        final float barTextSize = barTitle.getTextPaint().getTextSize();
        final float startScale = barTextSize > 0 ? source.getPaint().getTextSize() / barTextSize : 1f;
        final float scale = AndroidUtilities.lerp(startScale, 1f, progress);
        final float targetX = AndroidUtilities.lerp(sourceCenterX, restCenterX, progress);
        final float targetY = Math.max(sourceBaseline, restBaseline);
        final float pivotX = barTitle.getPivotX();
        final float pivotY = barTitle.getPivotY();
        final float translationY = targetY - restBaseline + (restBaseline - barTitle.getTop() - pivotY) * (1f - scale);
        transformed = true;
        barTitle.setScaleX(scale);
        barTitle.setScaleY(scale);
        barTitle.setTranslationX(targetX - restCenterX + (restCenterX - barTitle.getLeft() - pivotX) * (1f - scale));
        barTitle.setTranslationY(translationY);
        clip.set(0, 0, barTitle.getWidth(), (int) Math.ceil(pivotY + (barBottom - barTitle.getTop() - pivotY - translationY) / scale));
        barTitle.setClipBounds(clip);
    }

    private void setSourceTitle(TextView source) {
        if (sourceTitle != source) {
            if (sourceTitle != null) {
                sourceTitle.setAlpha(1f);
                sourceTitle.setClipBounds(null);
            }
            sourceTitle = source;
        }
    }

    private void setMorphedTitle(SimpleTextView barTitle) {
        if (morphedTitle != barTitle) {
            resetTransforms();
            morphedTitle = barTitle;
        }
    }

    private void resetTransforms() {
        if (morphedTitle == null || !transformed) {
            return;
        }
        transformed = false;
        morphedTitle.setScaleX(1f);
        morphedTitle.setScaleY(1f);
        morphedTitle.setTranslationX(0f);
        morphedTitle.setTranslationY(0f);
        morphedTitle.setClipBounds(null);
    }
}
