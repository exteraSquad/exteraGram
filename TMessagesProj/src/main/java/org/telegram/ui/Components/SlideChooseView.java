package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.utils.ui.material.MaterialSliderUiHelper;
import com.google.android.material.slider.Slider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;

public class SlideChooseView extends FrameLayout {

    private final SeekBarAccessibilityDelegate accessibilityDelegate;

    private Paint paint;
    private Paint linePaint;
    private TextPaint textPaint;
    private int lastDash;

    private int circleSize;
    private int gapSize;
    private int sideSide;
    private int lineSize;

    private int dashedFrom = -1;

    private boolean needDivider = false;

    private boolean moving;
    private boolean startMoving;
    private float xTouchDown;
    private float yTouchDown;

    private int startMovingPreset;

    private String[] optionsStr;
    private int[] optionsSizes;
    private Drawable[] leftDrawables;

    private int selectedIndex;
    private int minIndex = Integer.MIN_VALUE;
    private float selectedIndexTouch;
    private AnimatedFloat selectedIndexAnimatedHolder = new AnimatedFloat(this, 120, CubicBezierInterpolator.DEFAULT);
    private AnimatedFloat movingAnimatedHolder = new AnimatedFloat(this, 150, CubicBezierInterpolator.DEFAULT);

    private Callback callback;
    private final Theme.ResourcesProvider resourcesProvider;

    private boolean touchWasClose = false;

    private boolean allowSlide = true;
    private Slider materialSlider;
    private final View contentView;

    public SlideChooseView(Context context) {
        this(context, null);
    }

    public SlideChooseView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStrokeWidth(AndroidUtilities.dp(2));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        textPaint.setTextSize(AndroidUtilities.dp(13));

        accessibilityDelegate = new IntSeekBarAccessibilityDelegate() {
            @Override
            protected int getProgress() {
                return selectedIndex;
            }

            @Override
            protected void setProgress(int progress) {
                setOption(progress);
            }

            @Override
            protected int getMaxValue() {
                return optionsStr.length - 1;
            }

            @Override
            protected CharSequence getContentDescription(View host) {
                return selectedIndex < optionsStr.length ? optionsStr[selectedIndex] : null;
            }
        };

        contentView = new View(context) {
            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                drawContent(canvas);
            }
        };
        contentView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(contentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setOptions(int selected, String... options) {
        setOptions(selected, null, options);
    }

    public void setOptions(int selected, Drawable[] leftDrawables, String... options) {
        this.optionsStr = options;
        this.leftDrawables = leftDrawables;
        selectedIndex = optionsStr.length > 0 ? selected : 0;
        optionsSizes = new int[optionsStr.length];
        for (int i = 0; i < optionsStr.length; i++) {
            optionsSizes[i] = (int) Math.ceil(textPaint.measureText(optionsStr[i]));
        }
        if (this.leftDrawables != null) {
            for (Drawable drawable : this.leftDrawables) {
                drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
            }
        }
        updateMaterialSliderState();
        requestLayout();
        invalidate();
    }

    public void setMinAllowedIndex(int index) {
        if (index != -1 && optionsStr != null) {
            index = Math.min(index, optionsStr.length - 1);
        }
        if (minIndex != index) {
            minIndex = index;
            if (selectedIndex < index) {
                selectedIndex = index;
            }
            updateMaterialSliderState();
            invalidate();
        }
    }

    public void setDashedFrom(int from) {
        dashedFrom = from;
        updateMaterialSliderState();
        invalidate();
    }

    public void setNeedDivider(boolean divider) {
        needDivider = divider;
        invalidate();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        updateMaterialSliderState();
        return isUsingMaterialSlider() || super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        updateMaterialSliderState();
        if (!allowSlide) {
            return true;
        }
        float x = event.getX();
        float y = event.getY();
        float indexTouch = MathUtils.clamp((x - sideSide + circleSize / 2f) / (lineSize + gapSize * 2 + circleSize), 0, optionsStr.length - 1);
        boolean isClose = Math.abs(indexTouch - Math.round(indexTouch)) < .35f;
        if (isClose) {
            indexTouch = Math.round(indexTouch);
        }
        if (minIndex != Integer.MIN_VALUE) {
            indexTouch = Math.max(indexTouch, minIndex);
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            xTouchDown = x;
            yTouchDown = y;
            selectedIndexTouch = indexTouch;
            startMovingPreset = selectedIndex;
            startMoving = true;
            invalidate();
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (!moving) {
                if (Math.abs(xTouchDown - x) > Math.abs(yTouchDown - y)) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
            }
            if (startMoving) {
                if (Math.abs(xTouchDown - x) >= AndroidUtilities.touchSlop) {
                    moving = true;
                    startMoving = false;
                }
            }
            if (moving) {
                selectedIndexTouch = indexTouch;
                invalidate();
                if (Math.round(selectedIndexTouch) != selectedIndex && isClose) {
                    setOption(Math.round(selectedIndexTouch));
                }
            }
            invalidate();
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (!moving) {
                selectedIndexTouch = indexTouch;
                if (event.getAction() == MotionEvent.ACTION_UP && Math.round(selectedIndexTouch) != selectedIndex) {
                    setOption(Math.round(selectedIndexTouch));
                }
            } else {
                if (selectedIndex != startMovingPreset) {
                    setOption(selectedIndex);
                }
            }
            if (callback != null) {
                callback.onTouchEnd();
            }
            startMoving = false;
            moving = false;
            invalidate();
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        return true;
    }

    private void setOption(int index) {
        if (optionsStr == null || optionsStr.length == 0) {
            return;
        }
        if (selectedIndex != index) {
            AndroidUtilities.vibrateCursor(this);
        }
        selectedIndex = index;
        if (callback != null) {
            callback.onOptionSelected(index);
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        updateMaterialSliderState();
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(74), MeasureSpec.EXACTLY));
        circleSize = AndroidUtilities.dp(6);
        gapSize = AndroidUtilities.dp(2);
        sideSide = AndroidUtilities.dp(22);
        lineSize = (getMeasuredWidth() - circleSize * optionsStr.length - gapSize * 2 * (optionsStr.length - 1) - sideSide * 2) / Math.max(1, optionsStr.length - 1);
    }

    private void drawContent(Canvas canvas) {
        updateMaterialSliderState();
        boolean useMaterialSlider = isUsingMaterialSlider();
        float selectedIndexAnimated = selectedIndexAnimatedHolder.set(selectedIndex);
        if (useMaterialSlider) {
            updateMaterialSliderValue(selectedIndexAnimated);
        }
        float movingAnimated = movingAnimatedHolder.set(moving ? 1 : 0);
        int cy = getMeasuredHeight() / 2 + AndroidUtilities.dp(11);

        for (int a = 0; a < optionsStr.length; a++) {
            int cx = sideSide + (lineSize + gapSize * 2 + circleSize) * a + circleSize / 2;
            float t = Math.max(0, 1f - Math.abs(a - selectedIndexAnimated));
            float ut = MathUtils.clamp(selectedIndexAnimated - a + 1f, 0, 1);
            int color = ColorUtils.blendARGB(getThemedColor(Theme.key_switchTrack), Theme.multAlpha(getThemedColor(Theme.key_switchTrackChecked), minIndex != Integer.MIN_VALUE && a <= minIndex ? .50f : 1.0f), ut);
            if (!allowSlide) {
                color = AndroidUtilities.getTransparentColor(color, 0.5f);
            }
            if (!useMaterialSlider) {
                paint.setColor(color);
                linePaint.setColor(color);
                canvas.drawCircle(cx, cy, AndroidUtilities.lerp(circleSize / 2f, AndroidUtilities.dpf2(6), t), paint);
                if (a != 0) {
                    int x = cx - circleSize / 2 - gapSize - lineSize;
                    int width = lineSize;
                    if (dashedFrom != -1 && a - 1 >= dashedFrom) {
                        x += AndroidUtilities.dpf2(3);
                        width -= AndroidUtilities.dpf2(3);
                        float dash = width / AndroidUtilities.dpf2(13);
                        if (lastDash != dash) {
                            float gap = (width - dash * AndroidUtilities.dpf2(8)) / (dash - 1);
                            linePaint.setPathEffect(new DashPathEffect(new float[]{AndroidUtilities.dpf2(6), gap}, 0));
                            lastDash = (int) dash;
                        }
                        canvas.drawLine(x + AndroidUtilities.dpf2(1), cy, x + width - AndroidUtilities.dpf2(1), cy, linePaint);
                    } else {
                        float nt = MathUtils.clamp(1f - Math.abs(a - selectedIndexAnimated - 1), 0, 1);
                        float nct = MathUtils.clamp(1f - Math.min(Math.abs(a - selectedIndexAnimated), Math.abs(a - selectedIndexAnimated - 1)), 0, 1);
                        width -= AndroidUtilities.dpf2(3) * nct;
                        x += AndroidUtilities.dpf2(3) * nt;
                        canvas.drawRect(x, cy - AndroidUtilities.dpf2(1), x + width, cy + AndroidUtilities.dpf2(1), paint);
                    }
                }
            }
            int size = optionsSizes[a];
            String text = optionsStr[a];
            textPaint.setColor(AndroidUtilities.getTransparentColor(ColorUtils.blendARGB(getThemedColor(Theme.key_windowBackgroundWhiteGrayText), getThemedColor(Theme.key_windowBackgroundWhiteBlueText), t), allowSlide ? 1.0f : 0.5f));
            if (leftDrawables != null) {
                canvas.save();
                if (a == 0) {
                    canvas.translate(AndroidUtilities.dp(12), AndroidUtilities.dp(15.5f));
                } else if (a == optionsStr.length - 1) {
                    canvas.translate(getMeasuredWidth() - size - AndroidUtilities.dp(22) - AndroidUtilities.dp(10), AndroidUtilities.dp(28) - AndroidUtilities.dp(12.5f));
                } else {
                    canvas.translate(cx - size / 2 - AndroidUtilities.dp(10), AndroidUtilities.dp(28) - AndroidUtilities.dp(12.5f));
                }
                leftDrawables[a].setColorFilter(textPaint.getColor(), PorterDuff.Mode.MULTIPLY);
                leftDrawables[a].draw(canvas);
                canvas.restore();
                canvas.save();
                canvas.translate((leftDrawables[a].getIntrinsicWidth() / 2f) - AndroidUtilities.dp(a == 0 ? 3 : 2), 0);
            }

            if (a == 0) {
                canvas.drawText(text, AndroidUtilities.dp(22), AndroidUtilities.dp(28), textPaint);
            } else if (a == optionsStr.length - 1) {
                canvas.drawText(text, getMeasuredWidth() - size - AndroidUtilities.dp(22), AndroidUtilities.dp(28), textPaint);
            } else {
                canvas.drawText(text, cx - size / 2, AndroidUtilities.dp(28), textPaint);
            }

            if (leftDrawables != null) {
                canvas.restore();
            }
        }

        if (!useMaterialSlider) {
            float cx = sideSide + (lineSize + gapSize * 2 + circleSize) * selectedIndexAnimated + circleSize / 2f;
            paint.setColor(AndroidUtilities.getTransparentColor(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_switchTrackChecked), 80), allowSlide ? 1.0f : 0.5f));
            canvas.drawCircle(cx, cy, AndroidUtilities.dp(12 * movingAnimated), paint);
            paint.setColor(AndroidUtilities.getTransparentColor(getThemedColor(Theme.key_switchTrackChecked), allowSlide ? 1.0f : 0.5f));
            canvas.drawCircle(cx, cy, AndroidUtilities.dp(6), paint);
        }

        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(21), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(21) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        accessibilityDelegate.onInitializeAccessibilityNodeInfoInternal(this, info);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        return super.performAccessibilityAction(action, arguments) || accessibilityDelegate.performAccessibilityActionInternal(this, action, arguments);
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    private void initMaterialSlider(Context context) {
        materialSlider = MaterialSliderUiHelper.create(context);
        materialSlider.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        materialSlider.setFocusable(false);
        materialSlider.setFocusableInTouchMode(false);
        materialSlider.setLayoutDirection(LAYOUT_DIRECTION_LTR);
        materialSlider.setVisibility(GONE);
        addView(materialSlider, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP, 6, 24, 6, 0));
    }

    private boolean canUseMaterialSlider() {
        return ExteraConfig.getNewSliderStyle()
                && optionsStr != null
                && optionsStr.length > 1
                && dashedFrom < 0
                && minIndex <= 0;
    }

    private boolean isUsingMaterialSlider() {
        return materialSlider != null && materialSlider.getVisibility() == VISIBLE;
    }

    private void updateMaterialSliderState() {
        boolean useMaterialSlider = canUseMaterialSlider();
        if (useMaterialSlider && materialSlider == null) {
            initMaterialSlider(getContext());
        }
        if (materialSlider == null) {
            return;
        }
        int visibility = useMaterialSlider ? VISIBLE : GONE;
        if (materialSlider.getVisibility() != visibility) {
            materialSlider.setVisibility(visibility);
            if (contentView != null) {
                contentView.invalidate();
            }
        }
        if (useMaterialSlider) {
            boolean enabled = isEnabled() && allowSlide;
            if (materialSlider.isEnabled() != enabled) {
                materialSlider.setEnabled(enabled);
            }
            float valueTo = optionsStr.length - 1;
            if (materialSlider.getValue() > valueTo) {
                updateMaterialSliderValue(valueTo);
            }
            if (materialSlider.getValueFrom() != 0) {
                materialSlider.setValueFrom(0);
            }
            if (materialSlider.getValueTo() != valueTo) {
                materialSlider.setValueTo(valueTo);
            }
            if (materialSlider.getStepSize() != 0) {
                materialSlider.setStepSize(0);
            }
            MaterialSliderUiHelper.applyDiscreteStyle(materialSlider, optionsStr.length);
            updateMaterialSliderColors();
        }
    }

    private void updateMaterialSliderColors() {
        int alpha = allowSlide ? 255 : 128;
        int activeColor = ColorUtils.setAlphaComponent(getThemedColor(Theme.key_switchTrackChecked), alpha);
        int inactiveColor = ColorUtils.setAlphaComponent(getThemedColor(Theme.key_switchTrack), alpha);
        int tickColor = ColorUtils.setAlphaComponent(getThemedColor(Theme.key_windowBackgroundWhite), alpha);
        MaterialSliderUiHelper.applyDiscreteColors(materialSlider, activeColor, inactiveColor, tickColor);
    }

    private void updateMaterialSliderValue(float value) {
        if (materialSlider == null || optionsStr == null || optionsStr.length < 2) {
            return;
        }
        MaterialSliderUiHelper.setValue(materialSlider, MathUtils.clamp(value, 0, optionsStr.length - 1));
    }

    public interface Callback {
        void onOptionSelected(int index);

        default void onTouchEnd() {

        }
    }

    public void setAllowSlide(boolean allowSlide) {
        this.allowSlide = allowSlide;
        if (materialSlider != null) {
            materialSlider.setEnabled(isEnabled() && allowSlide);
            updateMaterialSliderColors();
        }
        invalidate();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (materialSlider != null) {
            materialSlider.setEnabled(enabled && allowSlide);
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (contentView != null) {
            contentView.invalidate();
        }
    }
}
