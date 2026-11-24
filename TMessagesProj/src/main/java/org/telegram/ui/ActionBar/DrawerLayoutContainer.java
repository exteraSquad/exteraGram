/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.ActionBar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.exteragram.messenger.drawer.DrawerContainer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

public class DrawerLayoutContainer extends FrameLayout {

    private INavigationLayout parentActionBarLayout;
    private ActionBarLayout actionBarLayout;
    private DrawerContainer drawerContainer;
    private boolean inLayout;

    private BitmapDrawable previewBlurDrawable;
    private PreviewForegroundDrawable previewForegroundDrawable;
    private boolean drawCurrentPreviewFragmentAbove;
    private float previewStartY;

    public DrawerLayoutContainer(Context context) {
        super(context);

        ViewCompat.setOnApplyWindowInsetsListener(this, this::onApplyWindowInsets);
        setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    public void setParentActionBarLayout(INavigationLayout layout) {
        parentActionBarLayout = layout;
    }

    public INavigationLayout getParentActionBarLayout() {
        return parentActionBarLayout;
    }

    public void setActionBarLayout(ActionBarLayout actionBarLayout) {
        this.actionBarLayout = actionBarLayout;
    }

    public void setDrawerContainer(@Nullable DrawerContainer container) {
        if (drawerContainer == container) {
            return;
        }
        if (drawerContainer != null) {
            drawerContainer.dispose();
            removeView(drawerContainer);
        }
        drawerContainer = container;
        if (container != null) {
            addView(container, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
    }

    public DrawerContainer getDrawerContainer() {
        return drawerContainer;
    }

    public boolean isDrawCurrentPreviewFragmentAbove() {
        return drawCurrentPreviewFragmentAbove;
    }

    public void setDrawCurrentPreviewFragmentAbove(boolean value) {
        if (this.drawCurrentPreviewFragmentAbove != value) {
            this.drawCurrentPreviewFragmentAbove = value;
            if (value) {
                createBlurDrawable();
                previewForegroundDrawable = null;
            } else {
                previewStartY = 0;
                previewBlurDrawable = null;
                previewForegroundDrawable = null;
            }
            invalidate();
        }
    }

    private void createBlurDrawable() {
        int measuredWidth = getMeasuredWidth();
        int measuredHeight = getMeasuredHeight();
        int w = (int) (measuredWidth / 6.0f);
        int h = (int) (measuredHeight / 6.0f);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(1.0f / 6.0f, 1.0f / 6.0f);
        // Capture the live drawer/content layers, but skip the custom preview-above overlay.
        super.dispatchDraw(canvas);
        Utilities.stackBlurBitmap(bitmap, Math.max(7, Math.max(w, h) / 180));
        previewBlurDrawable = new BitmapDrawable(bitmap);
        previewBlurDrawable.setBounds(0, 0, measuredWidth, measuredHeight);
    }

    public boolean onTouchEvent(MotionEvent ev) {
        if (drawerContainer != null && drawerContainer.handleEdgeSwipeTouch(ev)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (drawerContainer != null && drawerContainer.getVisibility() == VISIBLE) {
            return false;
        }
        if (drawerContainer != null && drawerContainer.handleEdgeSwipeIntercept(ev)) {
            return true;
        }
        return parentActionBarLayout != null && parentActionBarLayout.checkTransitionAnimation() && !parentActionBarLayout.isIncomingFragmentTouchable();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        inLayout = true;
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);

            if (child.getVisibility() == GONE) {
                continue;
            }

            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            try {
                child.layout(lp.leftMargin, lp.topMargin + getPaddingTop(), lp.leftMargin + child.getMeasuredWidth(), lp.topMargin + child.getMeasuredHeight() + getPaddingTop());
            } catch (Exception e) {
                FileLog.e(e);
                if (BuildVars.DEBUG_VERSION) {
                    throw e;
                }
            }
        }
        inLayout = false;
    }

    @Override
    public void requestLayout() {
        if (!inLayout) {
            super.requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        setMeasuredDimension(widthSize, heightSize);

        final int newDisplayWidth = widthSize
            - systemAndCutoutInsets.left
            - systemAndCutoutInsets.right;

        final int newDisplayHeight = heightSize
            - systemAndCutoutInsets.top
            - systemAndCutoutInsets.bottom;

        AndroidUtilities.displaySize.x = newDisplayWidth;
        AndroidUtilities.displaySize.y = newDisplayHeight;

        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);

            if (child.getVisibility() == GONE) {
                continue;
            }

            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            final int contentWidthSpec = MeasureSpec.makeMeasureSpec(widthSize - lp.leftMargin - lp.rightMargin, MeasureSpec.EXACTLY);
            final int contentHeightSpec;
            if (lp.height > 0) {
                contentHeightSpec = MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY);
            } else {
                contentHeightSpec = MeasureSpec.makeMeasureSpec(heightSize - lp.topMargin - lp.bottomMargin, MeasureSpec.EXACTLY);
            }
            if (child instanceof ActionBarLayout) {
                ActionBarLayout actionBarLayout = (ActionBarLayout) child;
                //fix keyboard measuring
                if (actionBarLayout.storyViewerAttached()) {
                    child.forceLayout();
                }
            }
            child.measure(contentWidthSpec, contentHeightSpec);
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (actionBarLayout != null && actionBarLayout.getParent() == this) {
            actionBarLayout.parentDraw(this, canvas);
        }

        super.dispatchDraw(canvas);

        if (drawCurrentPreviewFragmentAbove && parentActionBarLayout != null) {
            if (previewBlurDrawable != null) {
                previewBlurDrawable.setAlpha((int) (parentActionBarLayout.getCurrentPreviewFragmentAlpha() * 255));
                previewBlurDrawable.draw(canvas);
            }
            parentActionBarLayout.drawCurrentPreviewFragment(canvas, previewForegroundDrawable);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (drawCurrentPreviewFragmentAbove && parentActionBarLayout != null) {
            final int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_MOVE) {
                if (previewStartY == 0) {
                    previewStartY = ev.getY();
                    MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                    super.dispatchTouchEvent(event);
                    event.recycle();
                } else {
                    parentActionBarLayout.movePreviewFragment(previewStartY - ev.getY());
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
                parentActionBarLayout.finishPreviewFragment();
            }
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return drawCurrentPreviewFragmentAbove;
    }

    private final Paint internalNavbarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public Paint getInternalNavbarPaint() {
        return internalNavbarPaint;
    }

    public void setInternalNavigationBarColor(int color) {
        if (internalNavbarPaint.getColor() != color) {
            internalNavbarPaint.setColor(color);
            invalidate();

            for (int a = 0, N = getChildCount(); a < N; a++) {
                getChildAt(a).invalidate();
            }
        }
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        super.addView(child, index, params);
        if (lastWindowInsetsCompat != null) {
            dispatchApplyWindowInsetsInternal(child, lastWindowInsetsCompat);
        }
    }

    private @Nullable WindowInsetsCompat lastWindowInsetsCompat;
    private @NonNull Insets systemAndCutoutInsets = Insets.NONE;
    private @NonNull Insets systemAndCutoutAndImeInsets = Insets.NONE;

    private void dispatchApplyWindowInsetsInternal(View child, WindowInsetsCompat insets) {
        boolean canApplyInsets = child instanceof ActionBarLayout || child.getTag() == null;
        if (canApplyInsets) {
            ViewCompat.dispatchApplyWindowInsets(child, insets);
        }
    }

    @NonNull
    private WindowInsetsCompat onApplyWindowInsets(@NonNull View ignoredV, @NonNull WindowInsetsCompat insets) {
        lastWindowInsetsCompat = insets;

        final Insets systemInsets = AndroidUtilities.getDefaultWindowInsets(insets, false);
        final Insets systemAndImeInsets = AndroidUtilities.getDefaultWindowInsets(insets, true);

        if (!systemAndCutoutInsets.equals(systemInsets) || !systemAndCutoutAndImeInsets.equals(systemAndImeInsets)) {
            AndroidUtilities.statusBarHeight = systemInsets.top;
            AndroidUtilities.navigationBarHeight = systemInsets.bottom;

            systemAndCutoutInsets = systemInsets;
            systemAndCutoutAndImeInsets = systemAndImeInsets;
            requestLayout();
        }

        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            dispatchApplyWindowInsetsInternal(child, insets);
        }

        invalidate();
        return WindowInsetsCompat.CONSUMED;
    }

    private static class PreviewForegroundDrawable extends Drawable {

        private final GradientDrawable topDrawable;
        private final GradientDrawable bottomDrawable;

        public PreviewForegroundDrawable() {
            super();
            topDrawable = new GradientDrawable();
            topDrawable.setStroke(AndroidUtilities.dp(1), Theme.getColor(Theme.key_actionBarDefault));
            topDrawable.setCornerRadius(AndroidUtilities.dp(6));
            bottomDrawable = new GradientDrawable();
            bottomDrawable.setStroke(1, Theme.getColor(Theme.key_divider));
            bottomDrawable.setCornerRadius(AndroidUtilities.dp(6));
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final Rect bounds = getBounds();

            canvas.save();
            canvas.clipRect(bounds.left, bounds.top, bounds.right, bounds.top + ActionBar.getCurrentActionBarHeight());
            topDrawable.draw(canvas);
            canvas.restore();

            canvas.save();
            canvas.clipRect(bounds.left, bounds.top + ActionBar.getCurrentActionBarHeight(), bounds.right, bounds.bottom);
            bottomDrawable.draw(canvas);
            canvas.restore();
        }

        @Override
        protected void onBoundsChange(@NonNull Rect bounds) {
            super.onBoundsChange(bounds);
            topDrawable.setBounds(bounds);
            bottomDrawable.setBounds(bounds);
        }

        @Override
        public void setAlpha(int i) {
            topDrawable.setAlpha(i);
            bottomDrawable.setAlpha(i);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
