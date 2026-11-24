/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui.predictiveback;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.Utilities.clamp01;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

import androidx.annotation.ChecksSdkIntAtLeast;
import androidx.annotation.Nullable;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.ViewPagerActivity;

import java.util.List;

public final class PredictiveBackAnimationHelper {

    public static final float MAX_INTENSITY = 2f;

    private static final float MIN_CLOSING_SCALE = 0.85f;
    private static final float INITIAL_ENTERING_SCALE = 0.95f;
    private static final int DISPLAY_BOUNDS_MARGIN_DP = 8;
    private static final int TARGET_CORNER_RADIUS_DP = 40;
    private static final int ENTERING_START_OFFSET_DP = 96;
    private static final int SLIDE_PROGRESS_DISTANCE_DP = 6 * 56;
    private static final int POST_COMMIT_DURATION_MS = 375; // aosp default is 450, but too slow i think
    private static final float MAX_SCRIM_ALPHA_DARK = 0.8f;
    private static final float MAX_SCRIM_ALPHA_LIGHT = 0.2f;

    private final RectF startClosingRect = new RectF();
    private final RectF targetClosingRect = new RectF();
    private final RectF currentClosingRect = new RectF();
    private final RectF startEnteringRect = new RectF();
    private final RectF targetEnteringRect = new RectF();
    private final RectF currentEnteringRect = new RectF();
    private final RectF commitStartClosingRect = new RectF();
    private final RectF commitTargetClosingRect = new RectF();
    private final RectF commitStartEnteringRect = new RectF();
    private final RectF commitTargetEnteringRect = new RectF();
    private final Interpolator gestureInterpolator = new PathInterpolator(0.1f, 0.1f, 0f, 1f);
    private final Interpolator postCommitInterpolator = createEmphasizedInterpolator();
    private final Interpolator verticalMoveInterpolator = new DecelerateInterpolator();

    private float initialTouchY;
    private float progress;
    private float interpolatedProgress;
    private float closingAlpha = 1f;
    private float scrimAlphaMultiplier = 1f;
    private float startCornerRadius;
    private float targetCornerRadius;
    private float currentCornerRadius;

    @ChecksSdkIntAtLeast(api = 34)
    public static boolean isEnabled() {
        return Build.VERSION.SDK_INT >= 34 && ExteraConfig.getPredictiveBackIntensity() > 0;
    }

    public static float applyIntensity(float progress) {
        return clamp01(progress * Utilities.clamp(ExteraConfig.getPredictiveBackIntensity(), MAX_INTENSITY, 0f));
    }

    public static Drawable getTransitionBackground(List<BaseFragment> fragmentsStack, @Nullable BaseFragment fallbackFragment) {
        BaseFragment fragment = fallbackFragment;
        if (fragmentsStack != null && fragmentsStack.size() > 1) {
            fragment = fragmentsStack.get(fragmentsStack.size() - 2);
        }
        while (fragment instanceof ViewPagerActivity) {
            BaseFragment visibleFragment = ((ViewPagerActivity) fragment).getCurrentVisibleFragment();
            if (visibleFragment == null || visibleFragment == fragment) {
                break;
            }
            fragment = visibleFragment;
        }

        if (fragment instanceof ProfileActivity) {
            return new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray, fragment.getResourceProvider()));
        }

        View fragmentView = fragment != null ? fragment.fragmentView : null;
        Drawable background = fragmentView != null ? fragmentView.getBackground() : null;
        if (background instanceof ColorDrawable && Color.alpha(((ColorDrawable) background).getColor()) == 0) {
            background = null;
        }
        return background != null ? background : new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray, fragment != null ? fragment.getResourceProvider() : null));
    }

    public static void drawTransitionBackground(Canvas canvas, @Nullable Drawable background, int width, int height, Rect previousBounds) {
        if (background == null) {
            return;
        }
        background.copyBounds(previousBounds);
        background.setBounds(0, 0, width, height);
        background.draw(canvas);
        background.setBounds(previousBounds);
    }

    public void start(int width, int height, float touchY, boolean swipeFromLeft, float startCornerRadius) {
        this.initialTouchY = touchY;
        this.progress = 0f;
        this.interpolatedProgress = 0f;
        this.closingAlpha = 1f;
        this.scrimAlphaMultiplier = 1f;
        this.startCornerRadius = startCornerRadius;
        this.targetCornerRadius = Math.max(startCornerRadius, dp(TARGET_CORNER_RADIUS_DP));
        this.currentCornerRadius = startCornerRadius;
        startClosingRect.set(0, 0, Math.max(1, width), Math.max(1, height));
        targetClosingRect.set(startClosingRect);
        scaleCentered(targetClosingRect, MIN_CLOSING_SCALE);
        if (swipeFromLeft) {
            targetClosingRect.offset(startClosingRect.right - targetClosingRect.right - dp(DISPLAY_BOUNDS_MARGIN_DP), 0f);
        }
        currentClosingRect.set(startClosingRect);

        startEnteringRect.set(startClosingRect);
        final float nonRoundedHeight = startClosingRect.height() - 2f * startCornerRadius;
        scaleCentered(startEnteringRect, Utilities.clamp(nonRoundedHeight / startClosingRect.height(), INITIAL_ENTERING_SCALE, MIN_CLOSING_SCALE));
        startEnteringRect.offset(-Math.max(startEnteringRect.width() * (1f - MIN_CLOSING_SCALE), dp(ENTERING_START_OFFSET_DP)), 0f);
        targetEnteringRect.set(startEnteringRect);
        scaleCentered(targetEnteringRect, MIN_CLOSING_SCALE);
        currentEnteringRect.set(startEnteringRect);
    }

    public void update(float progress, float touchY) {
        this.progress = clamp01(progress);
        interpolatedProgress = gestureInterpolator.getInterpolation(this.progress);
        closingAlpha = 1f;
        scrimAlphaMultiplier = 1f;

        interpolate(currentClosingRect, startClosingRect, targetClosingRect, interpolatedProgress);
        final float yOffset = getYOffset(currentClosingRect.height(), touchY);
        currentClosingRect.offset(0f, yOffset);

        interpolate(currentEnteringRect, startEnteringRect, targetEnteringRect, interpolatedProgress);
        currentEnteringRect.offset(0f, yOffset);
        currentCornerRadius = lerp(startCornerRadius, targetCornerRadius, interpolatedProgress);
    }

    private float getYOffset(float height, float touchY) {
        final float screenHeight = startClosingRect.height();
        final float rawYDelta = touchY - initialTouchY;
        final float yDirection = rawYDelta < 0 ? -1f : 1f;
        final float deltaYRatio = Math.min(screenHeight / 2f, Math.abs(rawYDelta)) / (screenHeight / 2f);
        final float interpolatedYRatio = verticalMoveInterpolator.getInterpolation(deltaYRatio);
        return yDirection * interpolatedYRatio * Math.max(0f, (screenHeight - height) / 2f - dp(DISPLAY_BOUNDS_MARGIN_DP));
    }

    public void getClosingRect(RectF out) {
        out.set(currentClosingRect);
    }

    public void getEnteringRect(RectF out) {
        out.set(currentEnteringRect);
    }

    public float getClosingScale() {
        return currentClosingRect.width() / startClosingRect.width();
    }

    public float getEnteringScale() {
        return currentEnteringRect.width() / startClosingRect.width();
    }

    public float getClosingAlpha() {
        return closingAlpha;
    }

    public int getScrimAlpha(boolean darkTheme) {
        return (int) (0xFF * (darkTheme ? MAX_SCRIM_ALPHA_DARK : MAX_SCRIM_ALPHA_LIGHT) * scrimAlphaMultiplier);
    }

    public void setScrimAlphaMultiplier(float alphaMultiplier) {
        scrimAlphaMultiplier = clamp01(alphaMultiplier);
    }

    public float getCornerRadius() {
        return currentCornerRadius;
    }

    public float getProgress() {
        return progress;
    }

    public float getSlideDistance() {
        return interpolatedProgress * dp(SLIDE_PROGRESS_DISTANCE_DP);
    }

    public int getPostCommitDuration() {
        return POST_COMMIT_DURATION_MS;
    }

    public void prepareCommit() {
        commitStartClosingRect.set(currentClosingRect);
        commitStartEnteringRect.set(currentEnteringRect);
        commitTargetEnteringRect.set(startClosingRect);
        commitTargetClosingRect.set(startClosingRect);
        commitTargetClosingRect.offset(currentClosingRect.left + dp(ENTERING_START_OFFSET_DP), 0f);
    }

    public void updateCommitProgress(float linearProgress) {
        final float clamped = clamp01(linearProgress);
        final float progress = postCommitInterpolator.getInterpolation(clamped);
        closingAlpha = Math.max(1f - clamped * 5f, 0f);
        scrimAlphaMultiplier = 1f - clamped;
        interpolate(currentClosingRect, commitStartClosingRect, commitTargetClosingRect, progress);
        interpolate(currentEnteringRect, commitStartEnteringRect, commitTargetEnteringRect, progress);
        currentCornerRadius = lerp(targetCornerRadius, startCornerRadius, progress);
    }

    public void reset() {
        startClosingRect.setEmpty();
        targetClosingRect.setEmpty();
        currentClosingRect.setEmpty();
        startEnteringRect.setEmpty();
        targetEnteringRect.setEmpty();
        currentEnteringRect.setEmpty();
        commitStartClosingRect.setEmpty();
        commitTargetClosingRect.setEmpty();
        commitStartEnteringRect.setEmpty();
        commitTargetEnteringRect.setEmpty();
        progress = 0f;
        interpolatedProgress = 0f;
        closingAlpha = 1f;
        scrimAlphaMultiplier = 1f;
        initialTouchY = 0f;
        startCornerRadius = 0f;
        targetCornerRadius = 0f;
        currentCornerRadius = 0f;
    }

    private static void interpolate(RectF out, RectF from, RectF to, float progress) {
        out.set(
                lerp(from.left, to.left, progress),
                lerp(from.top, to.top, progress),
                lerp(from.right, to.right, progress),
                lerp(from.bottom, to.bottom, progress)
        );
    }

    private static void scaleCentered(RectF rect, float scale) {
        final float centerX = rect.centerX();
        final float centerY = rect.centerY();
        final float halfWidth = rect.width() * scale / 2f;
        final float halfHeight = rect.height() * scale / 2f;
        rect.set(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight);
    }

    private static Interpolator createEmphasizedInterpolator() {
        final Path path = new Path();
        path.moveTo(0f, 0f);
        path.cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f);
        path.cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f);
        return new PathInterpolator(path);
    }
}