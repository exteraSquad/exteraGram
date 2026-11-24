/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.StateSet;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.Keep;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;

import com.exteragram.messenger.utils.ui.material.SwitchUiHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.BaseCell;

import me.vkryl.android.animator.BoolAnimator;

public class Switch extends View implements Theme.Colorable {
    private static final FloatPropertyCompat<Switch> PROGRESS_PROPERTY = new FloatPropertyCompat<>("progress") {
        @Override
        public float getValue(Switch object) {
            return object.getProgress();
        }

        @Override
        public void setValue(Switch object, float value) {
            object.setProgress(value);
        }
    };

    @Override
    public void updateColors() {
        colorSet = 0; // the ripple color is re-read from the theme on the next press
    }
    private final BoolAnimator animatorIconVisibility = new BoolAnimator(this, CubicBezierInterpolator.EASE_OUT_QUINT, 380L, true);
    private final BoolAnimator animatorPressed = SwitchUiHelper.createThumbPressedAnimator(this);

    private RectF rectF;

    private float progress;
    private ObjectAnimator checkAnimator;
    private SpringAnimation checkSpringAnimator;
    private ObjectAnimator iconAnimator;

    private boolean attachedToWindow;
    private boolean isChecked;
    private Paint paint;
    private Paint paint2;
    private final float[] iconLines = new float[8];
    private Paint outlinePaint;

    private int drawIconType;
    private float iconProgress = 1.0f;

    private OnCheckedChangeListener onCheckedChangeListener;

    private int trackColorKey = Theme.key_fill_RedNormal;
    private int trackCheckedColorKey = Theme.key_switch2TrackChecked;
    private int thumbColorKey = Theme.key_windowBackgroundWhite;
    private int thumbCheckedColorKey = Theme.key_windowBackgroundWhite;

    private Drawable iconDrawable;
    private int lastIconColor;

    private boolean drawRipple;
    private RippleDrawable rippleDrawable;
    private Paint ripplePaint;
    private int[] pressedState = new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed};
    private int colorSet;

    private boolean bitmapsCreated;
    private Bitmap[] overlayBitmap;
    private Canvas[] overlayCanvas;
    private Bitmap overlayMaskBitmap;
    private Canvas overlayMaskCanvas;
    private float overlayCx;
    private float overlayCy;
    private float overlayRad;
    private Paint overlayEraserPaint;
    private Paint overlayMaskPaint;

    private Theme.ResourcesProvider resourcesProvider;

    private int overrideColorProgress;

    private float overrideAlpha = 1.0f;

    public interface OnCheckedChangeListener {
        void onCheckedChanged(Switch view, boolean isChecked);
    }

    public Switch(Context context) {
        this(context, null);
    }

    public Switch(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        rectF = new RectF();

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint2.setStyle(Paint.Style.STROKE);
        paint2.setStrokeCap(Paint.Cap.ROUND);
        paint2.setStrokeWidth(dp(2));
        outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outlinePaint.setStyle(Paint.Style.STROKE);

        setHapticFeedbackEnabled(true);
    }

    @Keep
    public void setProgress(float value) {
        if (progress == value) {
            return;
        }
        progress = value;
        invalidate();
    }

    @Keep
    public float getProgress() {
        return progress;
    }

    @Keep
    public void setIconProgress(float value) {
        if (iconProgress == value) {
            return;
        }
        iconProgress = value;
        invalidate();
    }

    @Keep
    public float getIconProgress() {
        return iconProgress;
    }

    private void cancelCheckAnimator() {
        if (checkAnimator != null) {
            checkAnimator.cancel();
            checkAnimator = null;
        }
        if (checkSpringAnimator != null) {
            checkSpringAnimator.cancel();
            checkSpringAnimator = null;
        }
    }

    private void cancelIconAnimator() {
        if (iconAnimator != null) {
            iconAnimator.cancel();
            iconAnimator = null;
        }
    }

    public void setDrawIconType(int type) {
        drawIconType = type;
    }

    public void setDrawRipple(boolean value) {
        if (value == drawRipple) {
            return;
        }
        drawRipple = value;

        if (rippleDrawable == null) {
            ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            ripplePaint.setColor(0xffffffff);
            Drawable maskDrawable;
            if (Build.VERSION.SDK_INT >= 23) {
                maskDrawable = null;
            } else {
                maskDrawable = new Drawable() {
                    @Override
                    public void draw(Canvas canvas) {
                        android.graphics.Rect bounds = getBounds();
                        canvas.drawCircle(bounds.centerX(), bounds.centerY(), SwitchUiHelper.getStateLayerRadius(), ripplePaint);
                    }

                    @Override
                    public void setAlpha(int alpha) {

                    }

                    @Override
                    public void setColorFilter(ColorFilter colorFilter) {

                    }

                    @Override
                    public int getOpacity() {
                        return PixelFormat.UNKNOWN;
                    }
                };
            }
            ColorStateList colorStateList = new ColorStateList(
                new int[][]{StateSet.WILD_CARD},
                new int[]{0}
            );
            rippleDrawable = new BaseCell.RippleDrawableSafe(colorStateList, null, maskDrawable);
            rippleDrawable.setCallback(this);
        }
        if (Build.VERSION.SDK_INT >= 23) {
            rippleDrawable.setRadius(SwitchUiHelper.getStateLayerRadius());
        }
        if (isChecked && colorSet != 2 || !isChecked && colorSet != 1) {
            int color = Theme.getColor(isChecked ? Theme.key_switchTrackBlueSelectorChecked : Theme.key_switchTrackBlueSelector, resourcesProvider);
            color = processColor(color);
            ColorStateList colorStateList = new ColorStateList(
                new int[][]{StateSet.WILD_CARD},
                new int[]{color}
            );
            rippleDrawable.setColor(colorStateList);
            colorSet = isChecked ? 2 : 1;
        }
        if (Build.VERSION.SDK_INT >= 28 && value) {
            rippleDrawable.setHotspot(isChecked ? 0 : dp(100), dp(18));
        }
        rippleDrawable.setState(value ? pressedState : StateSet.NOTHING);
        animatorPressed.setValue(value, attachedToWindow);
        invalidate();
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || rippleDrawable != null && who == rippleDrawable;
    }

    protected int processColor(int color) {
        return color;
    }

    public void setColors(int track, int trackChecked, int thumb, int thumbChecked) {
        trackColorKey = track;
        trackCheckedColorKey = trackChecked;
        thumbColorKey = thumb;
        thumbCheckedColorKey = thumbChecked;
    }

    private void animateToCheckedState(boolean newCheckedState) {
        if (SwitchUiHelper.isMaterial3SwitchStyle()) {
            if (checkAnimator != null) {
                checkAnimator.cancel();
                checkAnimator = null;
            }
            if (checkSpringAnimator == null) {
                checkSpringAnimator = SwitchUiHelper.createThumbSpring(this, PROGRESS_PROPERTY);
            }
            checkSpringAnimator.animateToFinalPosition(newCheckedState ? 1f : 0f);
            return;
        }
        cancelCheckAnimator();
        checkAnimator = ObjectAnimator.ofFloat(this, "progress", newCheckedState ? 1 : 0);
        checkAnimator.setDuration(200);
        checkAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        checkAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                checkAnimator = null;
            }
        });
        checkAnimator.start();
    }

    private void animateIcon(boolean newCheckedState) {
        iconAnimator = ObjectAnimator.ofFloat(this, "iconProgress", newCheckedState ? 1 : 0);
        iconAnimator.setDuration(200);
        iconAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                iconAnimator = null;
            }
        });
        iconAnimator.start();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachedToWindow = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attachedToWindow = false;
        destroyBitmaps();
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        onCheckedChangeListener = listener;
    }

    public void setChecked(boolean checked, boolean animated) {
        setChecked(checked, drawIconType, animated);
    }

    public void setChecked(boolean checked, int iconType, boolean animated) {
        if (checked != isChecked) {
            isChecked = checked;
            if (attachedToWindow && animated) {
                animateToCheckedState(checked);
            } else {
                cancelCheckAnimator();
                setProgress(checked ? 1.0f : 0.0f);
            }
            if (onCheckedChangeListener != null) {
                onCheckedChangeListener.onCheckedChanged(this, checked);
            }
        }
        setDrawIconType(iconType, animated);
    }

    public void setIcon(int icon) {
        if (icon != 0) {
            iconDrawable = getResources().getDrawable(icon).mutate();
            if (iconDrawable != null) {
                iconDrawable.setColorFilter(new PorterDuffColorFilter(lastIconColor = Theme.getColor(isChecked ? trackCheckedColorKey : trackColorKey, resourcesProvider), PorterDuff.Mode.MULTIPLY));
            }
        } else {
            iconDrawable = null;
        }
        invalidate();
    }

    public void setIconVisible(boolean visible, boolean animated) {
        animatorIconVisibility.setValue(visible, animated);
    }

    public void setDrawIconType(int iconType, boolean animated) {
        if (drawIconType != iconType) {
            drawIconType = iconType;
            if (attachedToWindow && animated) {
                animateIcon(iconType == 0);
            } else {
                cancelIconAnimator();
                setIconProgress(iconType == 0 ? 1.0f : 0.0f);
            }
        }
    }

    public boolean hasIcon() {
        return iconDrawable != null;
    }

    public boolean isChecked() {
        return isChecked;
    }

    public void setOverrideColor(int override) {
        if (overrideColorProgress == override) {
            return;
        }
        overrideColorProgress = override;
        overlayCx = 0;
        overlayCy = 0;
        overlayRad = 0;
        invalidate();
    }

    public void setOverrideColorProgress(float cx, float cy, float rad) {
        overlayCx = cx;
        overlayCy = cy;
        overlayRad = rad;
        invalidate();
    }

    private void checkBitmaps() {
        if (overrideColorProgress == 0) {
            return;
        }
        int padding = SwitchUiHelper.getOverlayPadding();
        int bitmapWidth = getMeasuredWidth() + 4 * padding;
        int bitmapHeight = getMeasuredHeight() + 4 * padding;

        if (bitmapsCreated && overlayBitmap != null && overlayBitmap[0] != null &&
                (overlayBitmap[0].getWidth() != bitmapWidth || overlayBitmap[0].getHeight() != bitmapHeight)) {
            destroyBitmaps();
        }

        if (!bitmapsCreated) {
            if (bitmapWidth <= 0 || bitmapHeight <= 0) {
                return;
            }
            try {
                overlayBitmap = new Bitmap[2];
                overlayCanvas = new Canvas[2];
                for (int a = 0; a < 2; a++) {
                    overlayBitmap[a] = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
                    overlayCanvas[a] = new Canvas(overlayBitmap[a]);
                }
                overlayMaskBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
                overlayMaskCanvas = new Canvas(overlayMaskBitmap);

                overlayEraserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                overlayEraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

                overlayMaskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                overlayMaskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                bitmapsCreated = true;
            } catch (Throwable ignored) {
            }
        }
    }

    private void destroyBitmaps() {
        if (bitmapsCreated) {
            if (overlayBitmap != null) {
                for (int i = 0; i < overlayBitmap.length; i++) {
                    if (overlayBitmap[i] != null) {
                        overlayBitmap[i].recycle();
                        overlayBitmap[i] = null;
                    }
                }
                overlayBitmap = null;
            }
            if (overlayMaskBitmap != null) {
                overlayMaskBitmap.recycle();
                overlayMaskBitmap = null;
            }
        }
        overlayCanvas = null;
        overlayMaskCanvas = null;
        bitmapsCreated = false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getVisibility() != VISIBLE) {
            return;
        }

        if (overrideColorProgress != 0) {
            checkBitmaps();
            if (!bitmapsCreated) {
                overrideColorProgress = 0;
            }
        }

        final boolean newStyle = SwitchUiHelper.isMaterial3SwitchStyle();
        final int overlayPadding = SwitchUiHelper.getOverlayPadding();
        final int stateLayerRadius = SwitchUiHelper.getStateLayerRadius();
        final boolean fadeOut = newStyle && overrideAlpha < 1.0f;
        if (fadeOut) {
            canvas.saveLayerAlpha(-stateLayerRadius, -stateLayerRadius, getMeasuredWidth() + stateLayerRadius, getMeasuredHeight() + stateLayerRadius, (int) (255 * overrideAlpha));
        }

        int x = 0;
        float y = 0;
        int width = dp(31);
        float thumbCx;
        float thumbCy;
        if (newStyle) {
            thumbCx = SwitchUiHelper.getThumbCenterX(getMeasuredWidth(), progress);
            thumbCy = getMeasuredHeight() / 2f;
        } else {
            x = (getMeasuredWidth() - width) / 2;
            y = (getMeasuredHeight() - AndroidUtilities.dpf2(14)) / 2;
            thumbCx = x + dp(8) + (int) (dp(16) * progress);
            thumbCy = getMeasuredHeight() / 2;
        }
        int tx = (int) thumbCx;
        int ty = (int) thumbCy;

        final int trackColor = processColor(SwitchUiHelper.getUnselectedTrackColor(trackColorKey, resourcesProvider));
        final int trackCheckedColor = processColor(Theme.getColor(trackCheckedColorKey, resourcesProvider));
        final int unselectedThumbColor = processColor(SwitchUiHelper.getUnselectedThumbColor(trackColorKey, thumbColorKey, resourcesProvider));
        final int selectedThumbColor = processColor(SwitchUiHelper.getSelectedThumbColor(thumbCheckedColorKey, resourcesProvider));

        int color1;
        int color2;
        float colorProgress;
        int r1;
        int r2;
        int g1;
        int g2;
        int b1;
        int b2;
        int a1;
        int a2;
        int red;
        int green;
        int blue;
        int alpha;
        int color;

        for (int a = 0; a < 2; a++) {
            if (a == 1 && overrideColorProgress == 0) {
                continue;
            }
            Canvas canvasToDraw = a == 0 ? canvas : overlayCanvas[0];
            int offset = a == 0 ? 0 : overlayPadding;

            if (a == 1) {
                overlayBitmap[0].eraseColor(0);
                paint.setColor(0xff000000);
                overlayMaskCanvas.drawRect(overlayPadding, overlayPadding, overlayMaskBitmap.getWidth() - overlayPadding, overlayMaskBitmap.getHeight() - overlayPadding, paint);
                overlayMaskCanvas.drawCircle(overlayCx - getX() + overlayPadding, overlayCy - getY() + overlayPadding, overlayRad, overlayEraserPaint);
            }
            if (overrideColorProgress == 1) {
                colorProgress = a == 0 ? 0 : 1;
            } else if (overrideColorProgress == 2) {
                colorProgress = a == 0 ? 1 : 0;
            } else {
                colorProgress = Utilities.clamp01(progress);
            }

            color1 = trackColor;
            color2 = trackCheckedColor;
            if (a == 0 && iconDrawable != null && lastIconColor != (isChecked ? color2 : color1)) {
                iconDrawable.setColorFilter(new PorterDuffColorFilter(lastIconColor = (isChecked ? color2 : color1), PorterDuff.Mode.MULTIPLY));
            }

            r1 = Color.red(color1);
            r2 = Color.red(color2);
            g1 = Color.green(color1);
            g2 = Color.green(color2);
            b1 = Color.blue(color1);
            b2 = Color.blue(color2);
            a1 = Color.alpha(color1);
            a2 = Color.alpha(color2);

            red = (int) (r1 + (r2 - r1) * colorProgress);
            green = (int) (g1 + (g2 - g1) * colorProgress);
            blue = (int) (b1 + (b2 - b1) * colorProgress);
            alpha = (int) (a1 + (a2 - a1) * colorProgress);
            color = ((alpha & 0xff) << 24) | ((red & 0xff) << 16) | ((green & 0xff) << 8) | (blue & 0xff);
            paint.setColor(color);
            paint2.setColor(color);

            if (newStyle) {
                SwitchUiHelper.setTrackBounds(rectF, getMeasuredWidth(), getMeasuredHeight());
                rectF.offset(offset, offset);
                float trackRadius = rectF.height() / 2f;
                canvasToDraw.drawRoundRect(rectF, trackRadius, trackRadius, paint);

                float outlineAlpha = 1f - colorProgress;
                if (outlineAlpha > 0) {
                    float outlineWidth = SwitchUiHelper.getTrackOutlineWidth();
                    outlinePaint.setColor(Theme.multAlpha(unselectedThumbColor, outlineAlpha));
                    outlinePaint.setStrokeWidth(outlineWidth);
                    rectF.inset(outlineWidth / 2f, outlineWidth / 2f);
                    canvasToDraw.drawRoundRect(rectF, trackRadius - outlineWidth / 2f, trackRadius - outlineWidth / 2f, outlinePaint);
                }
            } else {
                rectF.set(x, y, x + width, y + AndroidUtilities.dpf2(14));
                canvasToDraw.drawRoundRect(rectF, AndroidUtilities.dpf2(7), AndroidUtilities.dpf2(7), paint);
                canvasToDraw.drawCircle(tx + offset, ty + offset, AndroidUtilities.dpf2(10), paint);
            }
            if (a == 0 && rippleDrawable != null) {
                rippleDrawable.setBounds(tx - stateLayerRadius, ty - stateLayerRadius, tx + stateLayerRadius, ty + stateLayerRadius);
                rippleDrawable.draw(canvasToDraw);
            } else if (a == 1) {
                canvasToDraw.drawBitmap(overlayMaskBitmap, -overlayPadding, -overlayPadding, overlayMaskPaint);
            }
        }
        if (overrideColorProgress != 0) {
            canvas.drawBitmap(overlayBitmap[0], -overlayPadding, -overlayPadding, null);
        }

        for (int a = 0; a < 2; a++) {
            if (a == 1 && overrideColorProgress == 0) {
                continue;
            }
            Canvas canvasToDraw = a == 0 ? canvas : overlayCanvas[1];
            int offset = a == 0 ? 0 : overlayPadding;

            if (a == 1) {
                overlayBitmap[1].eraseColor(0);
            }
            if (overrideColorProgress == 1) {
                colorProgress = a == 0 ? 0 : 1;
            } else if (overrideColorProgress == 2) {
                colorProgress = a == 0 ? 1 : 0;
            } else {
                colorProgress = Utilities.clamp01(progress);
            }

            color1 = unselectedThumbColor;
            color2 = selectedThumbColor;

            r1 = Color.red(color1);
            r2 = Color.red(color2);
            g1 = Color.green(color1);
            g2 = Color.green(color2);
            b1 = Color.blue(color1);
            b2 = Color.blue(color2);
            a1 = Color.alpha(color1);
            a2 = Color.alpha(color2);

            red = (int) (r1 + (r2 - r1) * colorProgress);
            green = (int) (g1 + (g2 - g1) * colorProgress);
            blue = (int) (b1 + (b2 - b1) * colorProgress);
            alpha = (int) (a1 + (a2 - a1) * colorProgress);
            paint.setColor(((alpha & 0xff) << 24) | ((red & 0xff) << 16) | ((green & 0xff) << 8) | (blue & 0xff));

            if (newStyle) {
                float withIconProgress;
                if (drawIconType == 1 || drawIconType == 2 || iconAnimator != null) {
                    withIconProgress = 1f;
                } else {
                    withIconProgress = iconDrawable != null ? animatorIconVisibility.getFloatValue() : 0f;
                }
                SwitchUiHelper.setThumbBounds(rectF, thumbCx + offset, thumbCy + offset, progress, isChecked, withIconProgress, animatorPressed.getFloatValue());
                float thumbRadius = rectF.height() / 2f;
                canvasToDraw.drawRoundRect(rectF, thumbRadius, thumbRadius, paint);
            } else {
                canvasToDraw.drawCircle(tx + offset, ty + offset, dp(8), paint);
            }

            if (a == 0) {
                if (iconDrawable != null) {
                    final float factor = animatorIconVisibility.getFloatValue();
                    if (factor > 0) {
                        final int iconWidth = iconDrawable.getIntrinsicWidth();
                        final int iconHeight = iconDrawable.getIntrinsicHeight();
                        float scale = factor;
                        if (newStyle) {
                            scale *= SwitchUiHelper.getThumbIconScale(iconWidth, iconHeight);
                        }
                        canvas.save();
                        canvas.translate(thumbCx - iconWidth / 2f, thumbCy - iconHeight / 2f);
                        canvas.scale(scale, scale, iconWidth / 2f, iconHeight / 2f);
                        iconDrawable.setBounds(0, 0, iconWidth, iconHeight);
                        iconDrawable.draw(canvas);
                        canvas.restore();
                    }
                } else if (drawIconType == 1 && newStyle) {
                    paint2.setStrokeWidth(SwitchUiHelper.getIconStrokeWidth());
                    SwitchUiHelper.setCheckIconLines(iconLines, thumbCx, thumbCy, progress);
                    canvasToDraw.drawLines(iconLines, paint2);
                } else if (drawIconType == 1) {
                    paint2.setStrokeWidth(dp(2));
                    int ix = (int) (tx - (dp(10.8f) - dp(1.3f) * progress));
                    int iy = (int) (ty - (dp(8.5f) - dp(0.5f) * progress));
                    int startX2 = (int) AndroidUtilities.dpf2(4.6f) + ix;
                    int startY2 = (int) (AndroidUtilities.dpf2(9.5f) + iy);
                    int endX2 = startX2 + dp(2);
                    int endY2 = startY2 + dp(2);

                    int startX = (int) AndroidUtilities.dpf2(7.5f) + ix;
                    int startY = (int) AndroidUtilities.dpf2(5.4f) + iy;
                    int endX = startX + dp(7);
                    int endY = startY + dp(7);

                    startX = (int) (startX + (startX2 - startX) * progress);
                    startY = (int) (startY + (startY2 - startY) * progress);
                    endX = (int) (endX + (endX2 - endX) * progress);
                    endY = (int) (endY + (endY2 - endY) * progress);
                    canvasToDraw.drawLine(startX, startY, endX, endY, paint2);

                    startX = (int) AndroidUtilities.dpf2(7.5f) + ix;
                    startY = (int) AndroidUtilities.dpf2(12.5f) + iy;
                    endX = startX + dp(7);
                    endY = startY - dp(7);
                    canvasToDraw.drawLine(startX, startY, endX, endY, paint2);
                } else if (drawIconType == 2 || iconAnimator != null) {
                    float iconAnimAlpha = (1.0f - iconProgress);
                    paint2.setAlpha((int) (255 * iconAnimAlpha));
                    paint2.setStrokeWidth(newStyle ? SwitchUiHelper.getIconStrokeWidth() : dp(2));
                    canvasToDraw.drawLine(thumbCx, thumbCy, thumbCx, thumbCy - (newStyle ? SwitchUiHelper.getClockHandLength(true) : dp(5)), paint2);
                    canvasToDraw.save();
                    canvasToDraw.rotate(-90 * iconProgress, thumbCx, thumbCy);
                    canvasToDraw.drawLine(thumbCx, thumbCy, thumbCx + (newStyle ? SwitchUiHelper.getClockHandLength(false) : dp(4)), thumbCy, paint2);
                    canvasToDraw.restore();
                }
            }
            if (a == 1) {
                canvasToDraw.drawBitmap(overlayMaskBitmap, -overlayPadding, -overlayPadding, overlayMaskPaint);
            }
        }
        if (overrideColorProgress != 0) {
            canvas.drawBitmap(overlayBitmap[1], -overlayPadding, -overlayPadding, null);
        }
        if (fadeOut) {
            canvas.restore();
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(isChecked);
        //info.setContentDescription(isChecked ? LocaleController.getString(R.string.NotificationsOn) : LocaleController.getString(R.string.NotificationsOff));
    }

    @Override
    public void setAlpha(float alpha) {
        if (SwitchUiHelper.isMaterial3SwitchStyle()) {
            overrideAlpha = alpha;
            super.setAlpha(1.0f);
            invalidate();
        } else {
            overrideAlpha = 1.0f;
            super.setAlpha(alpha);
        }
    }
}