/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player.ui;

import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.dynamicanimation.animation.SpringAnimation;

import com.exteragram.messenger.utils.ui.material.Material3Tokens;

import org.telegram.messenger.Utilities;

public class ExpressiveStateDrawable extends Drawable implements Drawable.Callback {

    public static final int INNER_NONE = 0;
    public static final int INNER_LEFT = 1;
    public static final int INNER_RIGHT = 2;

    private static final float DOT_RADIUS_DP = 2f;

    private final Paint containerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path shapePath = new Path();
    private final float[] radii = new float[8];
    private final Rect rippleBounds = new Rect();
    private final RippleDrawable ripple;

    private final SpringAnimation pressShapeSpring;
    private final SpringAnimation selectShapeSpring;
    private final SpringAnimation selectContainerSpring;

    private final float pressedRadius;
    private float selectedRadius;
    private float inset;
    private float insetY = -1f;
    private int outlineColor;
    private int innerSide = INNER_NONE;
    private float innerRadius;

    private int containerColor;
    private int selectedContainerColor;
    private int contentColor;
    private int dotColor;
    private float dotOffset;
    private Runnable onProgressChanged;

    private float pressShape;
    private float selectShape;
    private float selectContainer;

    private boolean pressed;
    private boolean selected;
    private boolean alwaysShowContainer;
    private int alpha = 255;

    public ExpressiveStateDrawable(float pressedRadiusDp) {
        pressedRadius = dpf2(pressedRadiusDp);
        selectedRadius = pressedRadius;
        pressShapeSpring = createSpring(Material3Tokens.FAST_SPATIAL_DAMPING, Material3Tokens.FAST_SPATIAL_STIFFNESS, value -> pressShape = value);
        selectShapeSpring = createSpring(Material3Tokens.FAST_SPATIAL_DAMPING, Material3Tokens.FAST_SPATIAL_STIFFNESS, value -> selectShape = value);
        selectContainerSpring = createSpring(Material3Tokens.EFFECTS_DAMPING, Material3Tokens.EFFECTS_STIFFNESS, value -> selectContainer = Utilities.clamp01(value));
        ripple = new RippleDrawable(ColorStateList.valueOf(0), null, new ColorDrawable(Color.WHITE));
        ripple.setCallback(this);
    }

    private SpringAnimation createSpring(float damping, float stiffness, Utilities.Callback<Float> setter) {
        return Material3Tokens.createSpring(damping, stiffness, Material3Tokens.MIN_VISIBLE_CHANGE, value -> {
            setter.run(value);
            notifyProgress();
            invalidateSelf();
        });
    }

    private void notifyProgress() {
        if (onProgressChanged != null) {
            onProgressChanged.run();
        }
    }

    public ExpressiveStateDrawable setOnProgressChanged(Runnable onProgressChanged) {
        this.onProgressChanged = onProgressChanged;
        return this;
    }

    public float getPressProgress() {
        return pressShape;
    }

    public float getSelectProgress() {
        return selectContainer;
    }

    public ExpressiveStateDrawable setInnerCorners(int side, float radiusDp) {
        innerSide = side;
        innerRadius = dpf2(radiusDp);
        invalidateSelf();
        return this;
    }

    public ExpressiveStateDrawable setDot(int color, float offsetDp) {
        final float offset = dpf2(offsetDp);
        if (dotColor != color || dotOffset != offset) {
            dotColor = color;
            dotOffset = offset;
            invalidateSelf();
        }
        return this;
    }

    public ExpressiveStateDrawable setSelectedContainerColor(int color) {
        if (selectedContainerColor != color) {
            selectedContainerColor = color;
            invalidateSelf();
        }
        return this;
    }

    public ExpressiveStateDrawable setSelectedRadius(float radiusDp) {
        selectedRadius = dpf2(radiusDp);
        invalidateSelf();
        return this;
    }

    public ExpressiveStateDrawable setInset(float insetDp) {
        inset = Math.max(0, dpf2(insetDp));
        insetY = -1f;
        updateRippleBounds();
        invalidateSelf();
        return this;
    }

    public ExpressiveStateDrawable setInset(float insetXDp, float insetYDp) {
        inset = Math.max(0, dpf2(insetXDp));
        insetY = Math.max(0, dpf2(insetYDp));
        updateRippleBounds();
        invalidateSelf();
        return this;
    }

    public ExpressiveStateDrawable setOutline(int color, float widthDp) {
        final float width = dpf2(widthDp);
        if (outlineColor != color || outlinePaint.getStrokeWidth() != width) {
            outlineColor = color;
            outlinePaint.setStyle(Paint.Style.STROKE);
            outlinePaint.setStrokeWidth(width);
            invalidateSelf();
        }
        return this;
    }

    public int getContentColor() {
        return contentColor;
    }

    public ExpressiveStateDrawable setColors(int contentColor, int containerColor) {
        if (this.contentColor != contentColor || this.containerColor != containerColor) {
            this.contentColor = contentColor;
            this.containerColor = containerColor;
            ripple.setColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(contentColor, (int) (Color.alpha(contentColor) * Material3Tokens.PRESSED_STATE_LAYER_OPACITY))));
            invalidateSelf();
        }
        return this;
    }

    public ExpressiveStateDrawable setAlwaysShowContainer(boolean alwaysShowContainer) {
        if (this.alwaysShowContainer != alwaysShowContainer) {
            this.alwaysShowContainer = alwaysShowContainer;
            invalidateSelf();
        }
        return this;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected, boolean animated) {
        if (this.selected == selected && (animated || selectShape == (selected ? 1f : 0f))) {
            return;
        }
        this.selected = selected;
        final float target = selected ? 1f : 0f;
        if (animated) {
            Material3Tokens.animate(selectShapeSpring, selectShape, target);
            Material3Tokens.animate(selectContainerSpring, selectContainer, target);
        } else {
            selectShapeSpring.cancel();
            selectContainerSpring.cancel();
            selectShape = selectContainer = target;
            notifyProgress();
            invalidateSelf();
        }
    }

    public void setPressedState(boolean pressed) {
        if (this.pressed == pressed) {
            return;
        }
        this.pressed = pressed;
        Material3Tokens.animate(pressShapeSpring, pressShape, pressed ? 1f : 0f);
    }

    public float getCornerRadius() {
        final Rect bounds = getBounds();
        final float circle = Math.max(0, Math.min(bounds.width() - inset * 2, bounds.height() - (insetY < 0 ? inset : insetY) * 2) / 2f);
        final float shape = lerp(circle, Math.min(selectedRadius, circle), selectShape);
        return Math.max(0, Math.min(circle, lerp(shape, Math.min(pressedRadius, circle), pressShape)));
    }

    @Override
    protected boolean onStateChange(@NonNull int[] state) {
        boolean pressed = false;
        boolean enabled = false;
        for (int s : state) {
            if (s == android.R.attr.state_pressed) {
                pressed = true;
            } else if (s == android.R.attr.state_enabled) {
                enabled = true;
            }
        }
        setPressedState(pressed && enabled);
        return ripple.setState(state);
    }

    @Override
    public void setHotspot(float x, float y) {
        ripple.setHotspot(x, y);
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        ripple.setVisible(visible, restart);
        return super.setVisible(visible, restart);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        updateRippleBounds();
    }

    private void updateRippleBounds() {
        final Rect bounds = getBounds();
        final int x = Math.round(inset);
        final int y = Math.round(insetY < 0 ? inset : insetY);
        rippleBounds.set(bounds.left + x, bounds.top + y, bounds.right - x, bounds.bottom - y);
        if (rippleBounds.width() < 0 || rippleBounds.height() < 0) {
            rippleBounds.setEmpty();
        }
        ripple.setBounds(rippleBounds);
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    public void jumpToCurrentState() {
        pressShapeSpring.cancel();
        pressShape = pressed ? 1f : 0f;
        ripple.jumpToCurrentState();
        notifyProgress();
        invalidateSelf();
    }

    public Drawable createShapeFill(int color) {
        return new ShapeFill(color);
    }

    private boolean prepareShape(Rect bounds) {
        rect.set(bounds);
        rect.inset(inset, insetY < 0 ? inset : insetY);
        return !rect.isEmpty();
    }

    private float getInnerRadius(float r) {
        return innerSide == INNER_NONE ? r : Math.max(0, Math.min(r, lerp(Math.min(innerRadius, r), r, selectShape)));
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable who) {
        invalidateSelf();
    }

    @Override
    public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
        scheduleSelf(what, when);
    }

    @Override
    public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
        unscheduleSelf(what);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (!prepareShape(getBounds())) {
            return;
        }
        final float r = getCornerRadius();
        final float inner = getInnerRadius(r);
        final float containerAlpha = alwaysShowContainer ? 1f : selectContainer;
        if (containerAlpha > 0 && containerColor != 0) {
            containerPaint.setColor(containerColor);
            containerPaint.setAlpha((int) (containerPaint.getAlpha() * containerAlpha * alpha / 255f));
            drawShape(canvas, r, inner, containerPaint);
        }
        if (selectContainer > 0 && selectedContainerColor != 0) {
            containerPaint.setColor(selectedContainerColor);
            containerPaint.setAlpha((int) (containerPaint.getAlpha() * selectContainer * alpha / 255f));
            drawShape(canvas, r, inner, containerPaint);
        }
        if (contentColor != 0) {
            final int save = canvas.save();
            canvas.clipPath(buildShape(r, inner));
            ripple.draw(canvas);
            canvas.restoreToCount(save);
        }
        if (outlineColor != 0) {
            final float half = outlinePaint.getStrokeWidth() / 2f;
            rect.inset(half, half);
            outlinePaint.setColor(outlineColor);
            outlinePaint.setAlpha((int) (Color.alpha(outlineColor) * alpha / 255f));
            canvas.drawRoundRect(rect, Math.max(0, r - half), Math.max(0, r - half), outlinePaint);
            rect.inset(-half, -half);
        }
        if (dotColor != 0 && selectContainer > 0) {
            dotPaint.setColor(dotColor);
            dotPaint.setAlpha((int) (Color.alpha(dotColor) * selectContainer * alpha / 255f));
            canvas.drawCircle(rect.centerX(), rect.centerY() + dotOffset, dpf2(DOT_RADIUS_DP) * (0.5f + 0.5f * selectContainer), dotPaint);
        }
    }

    private void drawShape(Canvas canvas, float r, float inner, Paint paint) {
        if (innerSide == INNER_NONE || inner == r) {
            canvas.drawRoundRect(rect, r, r, paint);
            return;
        }
        canvas.drawPath(buildShape(r, inner), paint);
    }

    private Path buildShape(float r, float inner) {
        final float left = innerSide == INNER_NONE ? r : innerSide == INNER_LEFT ? inner : r;
        final float right = innerSide == INNER_NONE ? r : innerSide == INNER_RIGHT ? inner : r;
        radii[0] = radii[1] = radii[6] = radii[7] = left;
        radii[2] = radii[3] = radii[4] = radii[5] = right;
        shapePath.rewind();
        shapePath.addRoundRect(rect, radii, Path.Direction.CW);
        return shapePath;
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        ripple.setAlpha(alpha);
        invalidateSelf();
    }

    private final class ShapeFill extends Drawable {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ShapeFill(int color) {
            paint.setColor(color);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (!prepareShape(getBounds())) {
                return;
            }
            final float r = getCornerRadius();
            drawShape(canvas, r, getInnerRadius(r), paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @SuppressWarnings("deprecation")
        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @SuppressWarnings("deprecation")
    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
