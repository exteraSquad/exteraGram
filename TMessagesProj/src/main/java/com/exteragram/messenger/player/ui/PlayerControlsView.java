/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;
import androidx.dynamicanimation.animation.SpringAnimation;

import com.exteragram.messenger.utils.ui.material.Material3Tokens;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.PlayPauseDrawable;
import org.telegram.ui.Components.RLottieDrawable;

import java.util.List;

public class PlayerControlsView extends View {

    public static final int BUTTON_PREV = 0;
    public static final int BUTTON_PLAY = 1;
    public static final int BUTTON_NEXT = 2;

    public interface Delegate {
        void onPrevious();

        void onPlayPause();

        void onNext();

        void onHold(int direction, boolean start);
    }

    private static final float SKIP_SIZE_DP = 56f;
    private static final float PLAY_WIDTH_DP = 96f;
    private static final float PLAY_HEIGHT_DP = 72f;
    private static final float GAP_DP = 8f;
    private static final float PRESS_GROWTH = 0.15f;
    private static final float SKIP_PRESSED_RADIUS_DP = 12f;
    private static final float PLAY_RADIUS_DP = 24f;
    private static final float PLAY_PRESSED_RADIUS_DP = 16f;
    private static final float SKIP_ICON_DP = 20f;
    private static final int PLAY_ICON_DP = 28;
    private static final long HOLD_DELAY_MS = 300;
    private static final float LOADING_STROKE_DP = 3f;
    private static final int LOADING_SIZE_DP = 26;

    private final Paint containerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF[] buttonRects = {new RectF(), new RectF(), new RectF()};
    private final Rect iconBounds = new Rect();

    private final RLottieDrawable prevDrawable;
    private final RLottieDrawable nextDrawable;
    private final PlayPauseDrawable playPauseDrawable;
    private final CircularProgressDrawable loadingDrawable;

    private final float[] widths = new float[3];
    private final float[] radii = new float[3];
    private final SpringAnimation[] widthSprings = new SpringAnimation[3];
    private final SpringAnimation[] radiusSprings = new SpringAnimation[3];

    private final AccessibilityHelper accessibilityHelper;

    private Delegate delegate;
    private int tonalColor;
    private int onTonalColor;
    private PorterDuffColorFilter skipIconFilter;
    private int accentColor;

    private boolean playing;
    private boolean loading;
    private boolean playEnabled = true;

    private int pressed = -1;
    private boolean holding;
    private boolean touchInside;
    private boolean sizesInitialized;

    private final Runnable holdRunnable = () -> {
        if (pressed == BUTTON_PREV || pressed == BUTTON_NEXT) {
            holding = true;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (delegate != null) {
                delegate.onHold(pressed == BUTTON_PREV ? -1 : 1, true);
            }
        }
    };

    public PlayerControlsView(Context context) {
        super(context);
        prevDrawable = createSkipDrawable();
        nextDrawable = createSkipDrawable();
        playPauseDrawable = new PlayPauseDrawable(PLAY_ICON_DP);
        playPauseDrawable.setCallback(this);
        playPauseDrawable.setParent(this);
        loadingDrawable = new CircularProgressDrawable(dpf2(LOADING_SIZE_DP), dpf2(LOADING_STROKE_DP), 0);
        loadingDrawable.setCallback(this);
        loadingDrawable.setVisible(false, false);
        for (int i = 0; i < 3; i++) {
            final int index = i;
            widthSprings[i] = createSpring(value -> widths[index] = value);
            radiusSprings[i] = createSpring(value -> radii[index] = value);
        }
        accessibilityHelper = new AccessibilityHelper(this);
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper);
    }

    private RLottieDrawable createSkipDrawable() {
        final RLottieDrawable drawable = new RLottieDrawable(R.raw.player_prev, dp(SKIP_ICON_DP), dp(SKIP_ICON_DP), false, null);
        drawable.setAllowDecodeSingleFrame(true);
        drawable.setMasterParent(this);
        drawable.setCallback(this);
        return drawable;
    }

    private SpringAnimation createSpring(Utilities.Callback<Float> setter) {
        return Material3Tokens.createSpring(Material3Tokens.FAST_SPATIAL_DAMPING, Material3Tokens.FAST_SPATIAL_STIFFNESS, 1f / 10f, value -> {
            setter.run(value);
            invalidate();
        });
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public void setColors(int tonalColor, int onTonalColor, int accentColor, int onAccentColor) {
        this.tonalColor = tonalColor;
        this.accentColor = accentColor;
        if (this.onTonalColor != onTonalColor || skipIconFilter == null) {
            this.onTonalColor = onTonalColor;
            skipIconFilter = new PorterDuffColorFilter(onTonalColor, PorterDuff.Mode.SRC_IN);
            prevDrawable.setColorFilter(skipIconFilter);
            nextDrawable.setColorFilter(skipIconFilter);
        }
        playPauseDrawable.setColor(onAccentColor);
        loadingDrawable.setColor(onAccentColor);
        invalidate();
    }

    public void setPlaying(boolean playing, boolean animated) {
        playPauseDrawable.setPause(playing, animated);
        if (this.playing != playing) {
            this.playing = playing;
            updateTargets(animated);
            accessibilityHelper.invalidateVirtualView(BUTTON_PLAY);
        }
    }

    public void setLoading(boolean loading) {
        if (this.loading != loading) {
            this.loading = loading;
            loadingDrawable.setVisible(loading && isAttachedToWindow(), false);
            invalidate();
        }
    }

    public void setPlayEnabled(boolean enabled) {
        if (playEnabled != enabled) {
            playEnabled = enabled;
            invalidate();
        }
    }

    public void playSkipAnimation(boolean next) {
        final RLottieDrawable drawable = next ? nextDrawable : prevDrawable;
        drawable.setProgress(0f, false);
        if (isAttachedToWindow()) {
            drawable.start();
        }
        invalidate();
    }

    public static int getHeightDp() {
        return (int) PLAY_HEIGHT_DP;
    }

    private float getPlayWidth() {
        return dpf2(PLAY_WIDTH_DP);
    }

    private float getPlayHeight() {
        return dpf2(PLAY_HEIGHT_DP);
    }

    private float getSkipSize() {
        return dpf2(SKIP_SIZE_DP);
    }

    private void updateTargets(boolean animated) {
        final float skip = getSkipSize();
        final float play = playing ? getPlayWidth() : getPlayHeight();
        float wPrev = skip;
        float wPlay = play;
        float wNext = skip;
        if (pressed == BUTTON_PLAY) {
            wPlay = play * (1f + PRESS_GROWTH);
            wPrev = skip - play * PRESS_GROWTH / 2f;
            wNext = wPrev;
        } else if (pressed == BUTTON_PREV) {
            wPrev = skip * (1f + PRESS_GROWTH);
            wPlay = play - skip * PRESS_GROWTH;
        } else if (pressed == BUTTON_NEXT) {
            wNext = skip * (1f + PRESS_GROWTH);
            wPlay = play - skip * PRESS_GROWTH;
        }
        final float rSkip = skip / 2f;
        final float rPrev = pressed == BUTTON_PREV ? dpf2(SKIP_PRESSED_RADIUS_DP) : rSkip;
        final float rNext = pressed == BUTTON_NEXT ? dpf2(SKIP_PRESSED_RADIUS_DP) : rSkip;
        final float rPlay = pressed == BUTTON_PLAY ? dpf2(PLAY_PRESSED_RADIUS_DP) : playing ? dpf2(PLAY_RADIUS_DP) : getPlayHeight() / 2f;
        final float[] w = {wPrev, wPlay, wNext};
        final float[] r = {rPrev, rPlay, rNext};
        for (int i = 0; i < 3; i++) {
            if (animated && sizesInitialized && isAttachedToWindow()) {
                Material3Tokens.animate(widthSprings[i], widths[i], w[i]);
                Material3Tokens.animate(radiusSprings[i], radii[i], r[i]);
            } else {
                widthSprings[i].cancel();
                radiusSprings[i].cancel();
                widths[i] = w[i];
                radii[i] = r[i];
            }
        }
        sizesInitialized = true;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = (int) Math.ceil(getPlayHeight());
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw) {
            updateTargets(false);
        }
    }

    private void computeRects() {
        final float cx = getWidth() / 2f;
        final float cy = getHeight() / 2f;
        final float gap = dpf2(GAP_DP);
        final float total = widths[0] + widths[1] + widths[2] + gap * 2;
        final float gx = cx - total / 2f;
        final float skip = getSkipSize();
        final float play = getPlayHeight();
        buttonRects[0].set(gx, cy - skip / 2f, gx + widths[0], cy + skip / 2f);
        final float playLeft = gx + widths[0] + gap;
        buttonRects[1].set(playLeft, cy - play / 2f, playLeft + widths[1], cy + play / 2f);
        final float nextLeft = gx + widths[0] + widths[1] + gap * 2;
        buttonRects[2].set(nextLeft, cy - skip / 2f, nextLeft + widths[2], cy + skip / 2f);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (!sizesInitialized) {
            updateTargets(false);
        }
        computeRects();
        drawSkip(canvas, buttonRects[0], radii[0], prevDrawable, false);
        drawSkip(canvas, buttonRects[2], radii[2], nextDrawable, true);
        drawPlay(canvas, buttonRects[1], radii[1]);
    }

    private void drawSkip(Canvas canvas, RectF r, float radius, RLottieDrawable drawable, boolean flip) {
        containerPaint.setColor(tonalColor);
        final float rad = Math.min(radius, Math.min(r.width(), r.height()) / 2f);
        canvas.drawRoundRect(r, rad, rad, containerPaint);
        final int size = dp(SKIP_ICON_DP);
        final int left = (int) (r.centerX() - size / 2f);
        final int top = (int) (r.centerY() - size / 2f);
        drawable.setBounds(left, top, left + size, top + size);
        canvas.save();
        if (flip) {
            canvas.rotate(180f, r.centerX(), r.centerY());
        }
        drawable.draw(canvas);
        canvas.restore();
    }

    private void drawPlay(Canvas canvas, RectF r, float radius) {
        containerPaint.setColor(accentColor);
        if (!playEnabled) {
            containerPaint.setAlpha((int) (containerPaint.getAlpha() * 0.6f));
        }
        final float rad = Math.min(radius, Math.min(r.width(), r.height()) / 2f);
        canvas.drawRoundRect(r, rad, rad, containerPaint);
        if (loading) {
            final int half = dp(LOADING_SIZE_DP) / 2;
            loadingDrawable.setBounds((int) r.centerX() - half, (int) r.centerY() - half, (int) r.centerX() + half, (int) r.centerY() + half);
            loadingDrawable.draw(canvas);
            return;
        }
        final int size = dp(PLAY_ICON_DP);
        iconBounds.set((int) (r.centerX() - size / 2f), (int) (r.centerY() - size / 2f), (int) (r.centerX() + size / 2f), (int) (r.centerY() + size / 2f));
        playPauseDrawable.setBounds(iconBounds);
        playPauseDrawable.setAlpha(playEnabled ? 0xFF : 0x99);
        playPauseDrawable.draw(canvas);
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == prevDrawable || who == nextDrawable || who == playPauseDrawable || who == loadingDrawable || super.verifyDrawable(who);
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable drawable) {
        invalidate();
    }

    private int hitTest(float x, float y) {
        computeRects();
        final float slop = dpf2(4);
        for (int i = 0; i < 3; i++) {
            final RectF r = buttonRects[i];
            if (x >= r.left - slop && x <= r.right + slop && y >= 0 && y <= getHeight()) {
                return i;
            }
        }
        return -1;
    }

    private boolean isButtonEnabled(int button) {
        return button != BUTTON_PLAY || playEnabled;
    }

    @Override
    public boolean dispatchHoverEvent(MotionEvent event) {
        return accessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                final int button = hitTest(event.getX(), event.getY());
                if (button < 0 || !isButtonEnabled(button)) {
                    return false;
                }
                pressed = button;
                holding = false;
                touchInside = true;
                updateTargets(true);
                if (button != BUTTON_PLAY) {
                    AndroidUtilities.runOnUIThread(holdRunnable, HOLD_DELAY_MS);
                }
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (pressed < 0) {
                    return false;
                }
                final boolean inside = hitTest(event.getX(), event.getY()) == pressed;
                if (!inside && touchInside && !holding) {
                    touchInside = false;
                    AndroidUtilities.cancelRunOnUIThread(holdRunnable);
                    release();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (pressed < 0) {
                    return false;
                }
                AndroidUtilities.cancelRunOnUIThread(holdRunnable);
                final int button = pressed;
                final boolean wasHolding = holding;
                final boolean click = event.getActionMasked() == MotionEvent.ACTION_UP && touchInside && !wasHolding;
                release();
                if (wasHolding) {
                    if (delegate != null) {
                        delegate.onHold(button == BUTTON_PREV ? -1 : 1, false);
                    }
                } else if (click) {
                    performClick(button);
                }
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    private void release() {
        pressed = -1;
        holding = false;
        touchInside = false;
        getParent().requestDisallowInterceptTouchEvent(false);
        updateTargets(true);
    }

    private void performClick(int button) {
        if (delegate == null) {
            return;
        }
        if (button == BUTTON_PREV) {
            playSkipAnimation(false);
            delegate.onPrevious();
        } else if (button == BUTTON_NEXT) {
            playSkipAnimation(true);
            delegate.onNext();
        } else {
            delegate.onPlayPause();
        }
        accessibilityHelper.sendEventForVirtualView(button, AccessibilityEvent.TYPE_VIEW_CLICKED);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        prevDrawable.setCallback(this);
        nextDrawable.setCallback(this);
        loadingDrawable.setVisible(loading, false);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        AndroidUtilities.cancelRunOnUIThread(holdRunnable);
        if (holding && delegate != null) {
            delegate.onHold(pressed == BUTTON_PREV ? -1 : 1, false);
        }
        pressed = -1;
        holding = false;
        prevDrawable.stop();
        nextDrawable.stop();
        loadingDrawable.setVisible(false, false);
        for (int i = 0; i < 3; i++) {
            widthSprings[i].cancel();
            radiusSprings[i].cancel();
        }
        sizesInitialized = false;
    }

    public void recycle() {
        prevDrawable.recycle(true);
        nextDrawable.recycle(true);
    }

    private class AccessibilityHelper extends ExploreByTouchHelper {

        AccessibilityHelper(@NonNull View host) {
            super(host);
        }

        @Override
        protected int getVirtualViewAt(float x, float y) {
            final int button = hitTest(x, y);
            return button < 0 ? INVALID_ID : button;
        }

        @Override
        protected void getVisibleVirtualViews(List<Integer> virtualViewIds) {
            virtualViewIds.add(BUTTON_PREV);
            virtualViewIds.add(BUTTON_PLAY);
            virtualViewIds.add(BUTTON_NEXT);
        }

        @SuppressWarnings("deprecation")
        @Override
        protected void onPopulateNodeForVirtualView(int virtualViewId, @NonNull AccessibilityNodeInfoCompat node) {
            computeRects();
            final RectF r = buttonRects[Math.max(0, Math.min(2, virtualViewId))];
            node.setBoundsInParent(new Rect((int) r.left, (int) r.top, (int) r.right, (int) r.bottom));
            node.setClassName(Button.class.getName());
            node.setEnabled(isButtonEnabled(virtualViewId));
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
            if (virtualViewId == BUTTON_PREV) {
                node.setContentDescription(getString(R.string.AccDescrPrevious));
            } else if (virtualViewId == BUTTON_NEXT) {
                node.setContentDescription(getString(R.string.Next));
            } else {
                node.setContentDescription(getString(playing ? R.string.AccActionPause : R.string.AccActionPlay));
            }
        }

        @Override
        protected boolean onPerformActionForVirtualView(int virtualViewId, int action, @Nullable Bundle arguments) {
            if (action == AccessibilityNodeInfoCompat.ACTION_CLICK && isButtonEnabled(virtualViewId)) {
                performClick(virtualViewId);
                return true;
            }
            return false;
        }
    }
}
