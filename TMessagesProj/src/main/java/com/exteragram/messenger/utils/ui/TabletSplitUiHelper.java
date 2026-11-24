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
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;

public final class TabletSplitUiHelper {

    private static final float DEFAULT_FRACTION = 0.35f;
    private static final float MAX_FRACTION = 0.6f;
    private static final int MIN_LEFT_PANE_DP = 320;
    private static final int MIN_RIGHT_PANE_DP = 360;
    private static final int GUTTER_DP = 12;
    private static final int CORNER_DP = 16;

    public interface Callback {
        boolean canResize();

        void onResized();
    }

    public static int getRightPaneStart(int fullWidth, int insetLeft, int insetRight) {
        return insetLeft + getLeftWidth(fullWidth - insetLeft - insetRight, ExteraConfig.getTabletLeftFraction()) + dp(GUTTER_DP);
    }

    public static boolean canFitTwoPanes(int width) {
        return width >= dp(MIN_LEFT_PANE_DP) + dp(GUTTER_DP) + dp(MIN_RIGHT_PANE_DP);
    }

    private static int getLeftWidth(int contentWidth, float fraction) {
        final int min = dp(MIN_LEFT_PANE_DP);
        final int max = Math.max(min, Math.min((int) (contentWidth * MAX_FRACTION), contentWidth - dp(GUTTER_DP) - dp(MIN_RIGHT_PANE_DP)));
        return Utilities.clamp(Math.round(contentWidth * fraction), max, min);
    }

    private final ViewGroup container;
    private final Callback callback;
    private final HandleView handleView;
    private final int touchSlop;

    private int insetLeft;
    private int contentWidth;
    private int leftWidth;

    private boolean tracking;
    private boolean dragging;
    private boolean snapped;
    private float startX, startY;
    private int startWidth;
    private int defaultWidth;
    private int rawWidth;
    private int dragWidth;
    private float snapProgress;
    private ValueAnimator snapAnimator;
    private PointerIcon resizeIcon;

    public TabletSplitUiHelper(ViewGroup container, Callback callback) {
        this.container = container;
        this.callback = callback;
        handleView = new HandleView(container.getContext());
        touchSlop = ViewConfiguration.get(container.getContext()).getScaledTouchSlop();
    }

    public View getHandleView() {
        return handleView;
    }

    public void update(int width, int insetLeft, int insetRight) {
        this.insetLeft = insetLeft;
        contentWidth = width - insetLeft - insetRight;
        leftWidth = insetLeft + (dragging ? dragWidth : getLeftWidth(contentWidth, ExteraConfig.getTabletLeftFraction()));
    }

    public int getLeftPaneWidth() {
        return leftWidth;
    }

    public int getRightPaneStart() {
        return leftWidth + dp(GUTTER_DP);
    }

    public void measureHandle(int height) {
        handleView.measure(View.MeasureSpec.makeMeasureSpec(dp(GUTTER_DP) + dp(CORNER_DP) * 2, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
    }

    public void layoutHandle() {
        final int x = leftWidth - dp(CORNER_DP);
        handleView.layout(x, 0, x + handleView.getMeasuredWidth(), handleView.getMeasuredHeight());
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return handleTouch(ev, true);
    }

    public boolean onTouchEvent(MotionEvent ev) {
        return handleTouch(ev, false);
    }

    public PointerIcon onResolvePointerIcon(MotionEvent ev) {
        if (!dragging && !isOverHandle(ev.getX())) {
            return null;
        }
        if (resizeIcon == null) {
            resizeIcon = PointerIcon.getSystemIcon(container.getContext(), PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW);
        }
        return resizeIcon;
    }

    private boolean isOverHandle(float x) {
        return handleView.getVisibility() == View.VISIBLE && contentWidth > 0
                && Math.abs(x - leftWidth - dp(GUTTER_DP) / 2f) <= dp(GUTTER_DP / 2f + 8) && callback.canResize();
    }

    private boolean handleTouch(MotionEvent ev, boolean intercept) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = false;
                tracking = isOverHandle(ev.getX());
                startX = ev.getX();
                startY = ev.getY();
                return tracking && !intercept;
            case MotionEvent.ACTION_MOVE:
                if (!tracking) {
                    return false;
                }
                if (!dragging) {
                    final float dx = ev.getX() - startX;
                    final float dy = ev.getY() - startY;
                    if (Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx)) {
                        tracking = false;
                        return false;
                    }
                    if (Math.abs(dx) <= touchSlop) {
                        return !intercept;
                    }
                    startDrag(ev.getX());
                }
                updateDrag(ev.getX());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                final boolean wasDragging = dragging;
                tracking = false;
                if (wasDragging) {
                    finishDrag();
                }
                return wasDragging;
        }
        return false;
    }

    private void startDrag(float x) {
        dragging = true;
        startX = x;
        startWidth = leftWidth - insetLeft;
        rawWidth = startWidth;
        dragWidth = startWidth;
        defaultWidth = getLeftWidth(contentWidth, DEFAULT_FRACTION);
        snapped = startWidth == defaultWidth;
        snapProgress = snapped ? 1f : 0f;
        final ViewParent parent = container.getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true);
        }
        handleView.invalidate();
    }

    private void updateDrag(float x) {
        rawWidth = getLeftWidth(contentWidth, (startWidth + x - startX) / contentWidth);
        final int distance = Math.abs(rawWidth - defaultWidth);
        final boolean snap = snapped ? distance < dp(32) : distance < dp(12);
        if (snap != snapped) {
            snapped = snap;
            if (snap) {
                AndroidUtilities.vibrateCursor(handleView);
            }
            animateSnap(snap);
        }
        applyDragWidth();
    }

    private void animateSnap(boolean snap) {
        if (snapAnimator != null) {
            snapAnimator.cancel();
        }
        snapAnimator = ValueAnimator.ofFloat(snapProgress, snap ? 1f : 0f);
        snapAnimator.addUpdateListener(animation -> {
            snapProgress = (float) animation.getAnimatedValue();
            applyDragWidth();
        });
        snapAnimator.setDuration(220);
        snapAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        snapAnimator.start();
    }

    private void applyDragWidth() {
        if (!dragging) {
            return;
        }
        final int width = lerp(rawWidth, defaultWidth, snapProgress);
        if (width != dragWidth) {
            dragWidth = width;
            container.requestLayout();
        }
    }

    private void finishDrag() {
        if (snapAnimator != null) {
            snapAnimator.cancel();
            snapAnimator = null;
        }
        if (snapped) {
            dragWidth = defaultWidth;
        }
        dragging = false;
        final boolean changed = dragWidth != startWidth;
        if (changed) {
            ExteraConfig.setTabletLeftFraction(snapped ? DEFAULT_FRACTION : (float) dragWidth / contentWidth);
        }
        container.requestLayout();
        handleView.invalidate();
        if (changed) {
            callback.onResized();
        }
    }

    private final class HandleView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path gutterPath = new Path();
        private final Path cornerPath = new Path();
        private final Path circlePath = new Path();
        private final AnimatedFloat activeProgress = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);

        HandleView(Context context) {
            super(context);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            final float r = dp(CORNER_DP);
            final float left = r;
            final float right = w - r;
            gutterPath.rewind();
            gutterPath.addRect(left, 0, right, h, Path.Direction.CW);
            addCorner(left, 0, left - r, r);
            addCorner(left, h, left - r, h - r);
            addCorner(right, 0, right + r, r);
            addCorner(right, h, right + r, h - r);
        }

        private void addCorner(float x, float y, float cx, float cy) {
            final float r = dp(CORNER_DP);
            cornerPath.rewind();
            cornerPath.addRect(Math.min(x, cx), Math.min(y, cy), Math.max(x, cx), Math.max(y, cy), Path.Direction.CW);
            circlePath.rewind();
            circlePath.addCircle(cx, cy, r, Path.Direction.CW);
            cornerPath.op(circlePath, Path.Op.DIFFERENCE);
            gutterPath.op(cornerPath, Path.Op.UNION);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float progress = activeProgress.set(dragging);
            final float width = lerp(dpf2(4), dpf2(6), progress);
            final float height = lerp(dpf2(36), dpf2(72), progress);
            final float cx = getWidth() / 2f;
            final float cy = getHeight() / 2f;
            paint.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
            canvas.drawPath(gutterPath, paint);
            paint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), lerp(0.5f, 1f, progress)));
            AndroidUtilities.rectTmp.set(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f);
            canvas.drawRoundRect(AndroidUtilities.rectTmp, width / 2f, width / 2f, paint);
        }
    }
}
