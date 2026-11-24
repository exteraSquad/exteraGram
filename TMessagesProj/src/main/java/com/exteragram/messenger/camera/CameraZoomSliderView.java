/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.camera;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.ui.ActionBar.Theme.multAlpha;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.Arrays;
import java.util.Locale;

public class CameraZoomSliderView extends View {

    public interface OnZoomChangeListener {
        void onZoomChanged(float zoom);
    }

    private static final double LOG_2 = Math.log(2.0);

    private static final int DEFAULT_PROTECTION_BACKGROUND = 0x99000000;
    private static final int EDGE_FADE_COLOR = 0x33000000;

    private static final long LENS_INACTIVE_DURATION_MS = 180L;
    private static final float LENS_INACTIVE_ALPHA = 0.4f;
    private static final float BACKGROUND_HEIGHT_DP = 48.0f;
    private static final float BACKGROUND_RADIUS_DP = BACKGROUND_HEIGHT_DP / 2.0f;
    private static final float SHADOW_MARGIN_DP = 8.0f;
    private static final float RULER_HEIGHT_DP = 64.0f;
    private static final float EXPANDED_WIDTH_DP = 288.0f;
    private static final float RULER_WIDTH_DELTA_DP = 30.0f;
    private static final float TOGGLE_SIZE_DP = 48.0f;
    private static final float SELECTOR_SIZE_DP = 44.0f;
    private static final float SELECTOR_INSET_DP = 2.0f;
    private static final float TICK_SPACING_DP = 8.0f;
    private static final float MAJOR_TICK_HEIGHT_DP = 12.0f;
    private static final float MINOR_TICK_HEIGHT_DP = 6.0f;
    private static final float TICK_STROKE_DP = 1.0f;
    private static final float MARKER_STROKE_DP = 4.0f;
    private static final float TICK_BASELINE_FROM_BOTTOM_DP = 22.0f;
    private static final float MARKER_BASELINE_FROM_BOTTOM_DP = 23.0f;
    private static final float LABEL_BOTTOM_PADDING_DP = 4.0f;
    private static final float BUBBLE_MIN_WIDTH_DP = 40.0f;
    private static final float BUBBLE_HORIZONTAL_PADDING_DP = 4.0f;
    private static final float BUBBLE_VERTICAL_PADDING_DP = 6.0f;
    private static final float BUBBLE_PORTRAIT_PADDING_DP = 8.0f;

    private static final long AUTO_COLLAPSE_TIMEOUT_MS = 1500L;
    private static final long MAX_ZOOM_ANIMATION_DURATION_MS = 500L;

    private static final long MORPH_DURATION_MS = 217L;
    private static final float WIDTH_DAMPING = 1.0f;
    private static final float WIDTH_STIFFNESS = 3800.0f;
    private static final float SELECTOR_DAMPING = 0.8f;
    private static final float SELECTOR_STIFFNESS = 800.0f;
    private static final float MAX_STICKINESS = 0.7f;
    private static final float MAX_VELOCITY_FOR_STICKINESS = 100.0f;
    private static final float MIN_VELOCITY_WITHOUT_STICKINESS = 1000.0f;
    private static final int MIN_ULTRAWIDE_INTERVALS = 3;
    private static final int PRIMARY_SEGMENT_UNSET = Integer.MIN_VALUE;
    private static final int TICKS_PER_OCTAVE = 5;
    private static final float HIDE_DECIMAL_POINT_THRESHOLD = 8.0f;

    private static final TimeInterpolator ZOOM_INTERPOLATOR = new ZoomLookupInterpolator();
    private static final TimeInterpolator MORPH_INTERPOLATOR =
            new CubicBezierInterpolator(0.4f, 0.0f, 0.2f, 1.0f);
    private static final PorterDuffXfermode XOR_XFERMODE =
            new PorterDuffXfermode(PorterDuff.Mode.XOR);
    private static final PorterDuffXfermode DST_OVER_XFERMODE =
            new PorterDuffXfermode(PorterDuff.Mode.DST_OVER);
    private static final PorterDuffXfermode DST_IN_XFERMODE =
            new PorterDuffXfermode(PorterDuff.Mode.DST_IN);

    private static final FloatPropertyCompat<CameraZoomSliderView> CONTROL_WIDTH =
            new FloatPropertyCompat<CameraZoomSliderView>("controlWidth") {
                @Override
                public float getValue(CameraZoomSliderView view) {
                    return view.animatedControlWidth;
                }

                @Override
                public void setValue(CameraZoomSliderView view, float value) {
                    view.animatedControlWidth = Math.max(0.0f, value);
                    view.invalidate();
                }
            };

    private static final FloatPropertyCompat<CameraZoomSliderView> SELECTOR_OFFSET =
            new FloatPropertyCompat<CameraZoomSliderView>("selectorOffset") {
                @Override
                public float getValue(CameraZoomSliderView view) {
                    return view.animatedSelectorOffset;
                }

                @Override
                public void setValue(CameraZoomSliderView view, float value) {
                    view.animatedSelectorOffset = value;
                    view.invalidate();
                }
            };

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint toggleTextPaint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG
    );
    private final Paint selectedToggleTextPaint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG
    );
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rulerLabelPaint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG
    );
    private final Paint edgeFadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubbleTextPaint = new Paint(
            Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG
    );

    private final RectF controlBounds = new RectF();
    private final RectF compactBounds = new RectF();
    private final RectF rulerBounds = new RectF();
    private final RectF compactTouchBounds = new RectF();
    private final RectF rulerTouchBounds = new RectF();
    private final RectF selectorBounds = new RectF();
    private final RectF bubbleBounds = new RectF();
    private final SparseArray<String> primaryLabels = new SparseArray<>();

    private final int touchSlop;
    private ValueAnimator expandedAnimator;
    private boolean externalZoomGesture;
    private final SpringAnimation widthSpring;
    private final SpringAnimation selectorSpring;

    private float minZoom = 0.5f;
    private float maxZoom = 30.0f;
    private float zoom = 1.0f;
    private float[] toggleStops = {0.5f, 1.0f, 2.0f, 5.0f};
    private float[] rulerStops = {0.5f, 1.0f, 2.0f, 5.0f, 10.0f, 30.0f};
    private int[] rebuiltPrimaryTickIndices = new int[0];
    private int intervalCount;
    private int oneXTick = -1;
    private float tickSpacing;

    private int protectionBackgroundColor = DEFAULT_PROTECTION_BACKGROUND;
    private int primaryColor = 0xffa8c7fa;
    private int minorTickColor = 0xffe3e3e3;
    private int secondaryFixedColor = 0xffd7e3f9;
    private int onSecondaryFixedColor = 0xff283345;
    private int unselectedToggleColor = Color.WHITE;
    private float displayNormalizationFactor = 1.0f;

    private boolean expanded;
    private float expandedProgress;
    private float animatedControlWidth;
    private int selectedToggleIndex;
    private float animatedSelectorOffset;
    private float pendingConfigurationSelectorX = Float.NaN;

    private float downX;
    private float downY;
    private float lastTouchX;
    private boolean movedPastSlop;
    private boolean dragging;
    private boolean compactGestureDown;
    private boolean dragStartedFromCompact;
    private int pressedToggleIndex = -1;
    private int lastDescribedZoom = Integer.MIN_VALUE;
    private int stickyTick = -1;
    private float stickyDistance;
    private float stickyFactor;
    private float dragTick;
    private int dragPrimarySegment = PRIMARY_SEGMENT_UNSET;
    private VelocityTracker velocityTracker;

    private ValueAnimator zoomAnimator;
    private ValueAnimator lensInactiveAnimator;
    private boolean selectedLensInactive;
    private float lensInactiveProgress;
    private boolean selectedShowsStopValue;
    private OnZoomChangeListener onZoomChangeListener;

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (!compactGestureDown || movedPastSlop || expanded) {
                return;
            }
            dragStartedFromCompact = true;
            beginDrag(downX);
            setExpanded(true, true);
        }
    };

    private final Runnable autoCollapseRunnable = new Runnable() {
        @Override
        public void run() {
            if (expanded && !dragging && !externalZoomGesture) {
                setExpanded(false, true);
            }
        }
    };

    public CameraZoomSliderView(Context context) {
        this(context, null);
    }

    public CameraZoomSliderView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CameraZoomSliderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        tickSpacing = Math.max(1.0f, Math.round(dp(TICK_SPACING_DP)));

        widthSpring = createSpring(
                CONTROL_WIDTH,
                WIDTH_DAMPING,
                WIDTH_STIFFNESS,
                dp(0.1f)
        );
        selectorSpring = createSpring(
                SELECTOR_OFFSET,
                SELECTOR_DAMPING,
                SELECTOR_STIFFNESS,
                1.0f
        );
        configurePaints();
        rebuildScale();
        selectedToggleIndex = findToggleSegment(zoom);
        animatedSelectorOffset = getSelectorOffset(selectedToggleIndex);
        animatedControlWidth = getCompactWidth();

        setClickable(true);
        setFocusable(true);
        updateAccessibilityDescription();
    }

    private SpringAnimation createSpring(
            FloatPropertyCompat<CameraZoomSliderView> property,
            float damping,
            float stiffness,
            float minimumVisibleChange
    ) {
        SpringAnimation animation = new SpringAnimation(this, property);
        animation.setSpring(new SpringForce()
                .setDampingRatio(damping)
                .setStiffness(stiffness));
        animation.setMinimumVisibleChange(minimumVisibleChange);
        return animation;
    }

    public void prepareZoomConfigurationTransition() {
        pendingConfigurationSelectorX = Float.NaN;
        if (!isLaidOut() || toggleStops.length == 0) {
            return;
        }
        updateLayoutBounds();
        if (!compactBounds.isEmpty()) {
            pendingConfigurationSelectorX = compactBounds.left + animatedSelectorOffset;
        }
    }

    public void cancelZoomConfigurationTransition() {
        pendingConfigurationSelectorX = Float.NaN;
    }

    public void setZoomConfiguration(
            float minZoom,
            float maxZoom,
            float[] toggleStops,
            float[] rulerStops,
            float configuredZoom,
            boolean animated
    ) {
        if (!Float.isFinite(minZoom) || !Float.isFinite(maxZoom)
                || minZoom <= 0.0f || maxZoom <= minZoom) {
            throw new IllegalArgumentException("Zoom range must satisfy 0 < minZoom < maxZoom");
        }
        float previousSelectorX = pendingConfigurationSelectorX;
        pendingConfigurationSelectorX = Float.NaN;
        boolean animateConfiguration = animated
                && isLaidOut()
                && Float.isFinite(previousSelectorX);
        this.minZoom = minZoom;
        this.maxZoom = maxZoom;
        this.toggleStops = sanitizeStops(toggleStops, minZoom, maxZoom);
        this.rulerStops = sanitizeStops(rulerStops, minZoom, maxZoom);
        zoom = Utilities.clamp(configuredZoom, maxZoom, minZoom);
        rebuildScale();
        if (animateConfiguration) {
            selectedToggleIndex = findToggleSegment(zoom);
            float availableWidth = Math.max(
                    0.0f,
                    getWidth() - getPaddingLeft() - getPaddingRight()
            );
            float compactWidth = Math.min(
                    Math.max(0.0f, availableWidth - dp(SHADOW_MARGIN_DP) * 2.0f),
                    Math.round(getCompactWidth())
            );
            float compactLeft = centeredChildLeft(availableWidth, compactWidth);
            selectorSpring.cancel();
            animatedSelectorOffset = previousSelectorX - compactLeft;
            animateSelectorTo(selectedToggleIndex, true);
            updateTargetControlWidth(true);
        } else {
            syncSelectedToggle(false);
            updateTargetControlWidth(false);
        }
        updateAccessibilityDescription();
        requestLayout();
        invalidate();
    }

    public void setZoom(float zoom) {
        setZoom(zoom, false);
    }

    public void setZoom(float zoom, boolean animated) {
        float target = Utilities.clamp(zoom, maxZoom, minZoom);
        cancelZoomAnimator();
        if (animated && isLaidOut()) {
            animateZoomTo(target, false);
        } else {
            setZoomInternal(target, false, true);
        }
    }

    public float getZoom() {
        return zoom;
    }

    public float getMinimumZoom() {
        return minZoom;
    }

    public float getMaximumZoom() {
        return maxZoom;
    }

    public void setExternalZoomGestureActive(boolean active) {
        if (externalZoomGesture == active) {
            return;
        }
        externalZoomGesture = active;
        if (active) {
            setExpanded(true, true);
        } else {
            resetAutoCollapseTimeout();
        }
    }

    public void setExpanded(boolean expanded, boolean animated) {
        float targetProgress = expanded ? 1.0f : 0.0f;
        float targetWidth = expanded ? getExpandedBackgroundWidth() : getCompactWidth();
        if (this.expanded == expanded
                && Math.abs(expandedProgress - targetProgress) < 0.0001f
                && Math.abs(animatedControlWidth - targetWidth) < 0.1f) {
            if (expanded) {
                resetAutoCollapseTimeout();
            }
            return;
        }

        this.expanded = expanded;
        removeCallbacks(longPressRunnable);
        removeCallbacks(autoCollapseRunnable);

        animateExpandedProgress(targetProgress, animated);
        if (!animated || !isLaidOut()) {
            widthSpring.cancel();
            animatedControlWidth = targetWidth;
            invalidate();
        } else {
            widthSpring.animateToFinalPosition(targetWidth);
        }

        if (expanded && !dragging) {
            resetAutoCollapseTimeout();
        }
        updateAccessibilityDescription();
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    public void setColors(
            int backgroundColor,
            int minorTickColor,
            int primaryColor,
            int secondaryFixedColor,
            int onSecondaryFixedColor
    ) {
        protectionBackgroundColor = backgroundColor;
        this.minorTickColor = minorTickColor;
        this.primaryColor = primaryColor;
        this.secondaryFixedColor = secondaryFixedColor;
        this.onSecondaryFixedColor = onSecondaryFixedColor;
        configurePaintColors();
        invalidate();
    }

    public void setToggleTextColor(int color) {
        unselectedToggleColor = color;
        invalidate();
    }

    public void setDisplayNormalizationFactor(float factor) {
        float target = Float.isFinite(factor) && factor > 0.0f ? factor : 1.0f;
        if (Math.abs(displayNormalizationFactor - target) < 0.0001f) {
            return;
        }
        displayNormalizationFactor = target;
        rebuildScale();
        syncSelectedToggle(false);
        updateAccessibilityDescription();
        invalidate();
    }

    public float[] getToggleStops() {
        return Arrays.copyOf(toggleStops, toggleStops.length);
    }

    public void setOnZoomChangeListener(OnZoomChangeListener listener) {
        onZoomChangeListener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float bubbleHeight = getBubbleHeight();
        float bubblePadding = dp(BUBBLE_PORTRAIT_PADDING_DP);
        int desiredWidth = Math.round(
                Math.max(getExpandedBackgroundWidth(), getCompactWidth())
                        + dp(SHADOW_MARGIN_DP) * 2.0f
        ) + getPaddingLeft() + getPaddingRight();
        int desiredHeight = Math.round(
                bubbleHeight + bubblePadding * 2.0f + dp(RULER_HEIGHT_DP)
        ) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(
                resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec)
        );
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        updateLayoutBounds();

        boolean pillDrawn = !controlBounds.isEmpty()
                && drawPillBackground(canvas, controlBounds, dp(BACKGROUND_RADIUS_DP));

        float compactAlpha = Utilities.clamp01(1.0f - expandedProgress);
        float rulerAlpha = Utilities.clamp01(expandedProgress);
        int contentLayer = canvas.saveLayer(
                0.0f,
                0.0f,
                getWidth(),
                getHeight(),
                null
        );
        if (compactAlpha > 0.001f) {
            int compactLayer = canvas.saveLayerAlpha(
                    0.0f,
                    0.0f,
                    getWidth(),
                    getHeight(),
                    Math.round(compactAlpha * 255.0f)
            );
            drawToggleRow(canvas, 1.0f);
            canvas.restoreToCount(compactLayer);
        }
        if (rulerAlpha > 0.001f) {
            int rulerLayer = canvas.saveLayerAlpha(
                    0.0f,
                    0.0f,
                    getWidth(),
                    getHeight(),
                    Math.round(rulerAlpha * 255.0f)
            );
            drawBubble(canvas, 1.0f);
            drawRuler(canvas, 1.0f);
            canvas.restoreToCount(rulerLayer);
        }
        if (!pillDrawn) {
            drawProtectionBackground(canvas);
        }
        canvas.restoreToCount(contentLayer);
    }

    private void updateLayoutBounds() {
        float availableWidth = Math.max(
                0.0f,
                getWidth() - getPaddingLeft() - getPaddingRight()
        );
        float contentWidth = Math.max(0.0f, availableWidth - dp(SHADOW_MARGIN_DP) * 2.0f);
        float rulerBottom = getHeight() - getPaddingBottom();
        float backgroundTop = rulerBottom - dp(RULER_HEIGHT_DP);
        float backgroundBottom = backgroundTop + dp(BACKGROUND_HEIGHT_DP);

        float expandedWidth = Math.min(
                contentWidth,
                Math.round(getExpandedBackgroundWidth())
        );
        float compactWidth = Math.min(
                contentWidth,
                Math.round(getCompactWidth())
        );
        if (animatedControlWidth <= 0.0f) {
            animatedControlWidth = expanded ? expandedWidth : compactWidth;
        }
        float backgroundWidth = Math.min(contentWidth, Math.round(animatedControlWidth));
        float backgroundLeft = centeredChildLeft(availableWidth, backgroundWidth);
        controlBounds.set(
                backgroundLeft,
                backgroundTop,
                backgroundLeft + backgroundWidth,
                backgroundBottom
        );
        float compactLeft = centeredChildLeft(availableWidth, compactWidth);
        compactBounds.set(
                compactLeft,
                backgroundTop,
                compactLeft + compactWidth,
                backgroundBottom
        );

        float rulerWidth = Math.min(contentWidth, getExpandedRulerWidth());
        float rulerLeft = centeredChildLeft(availableWidth, rulerWidth);
        rulerBounds.set(
                rulerLeft,
                backgroundTop,
                rulerLeft + rulerWidth,
                rulerBottom
        );
        compactTouchBounds.set(compactBounds);
        compactTouchBounds.bottom = rulerBottom;
        rulerTouchBounds.set(rulerBounds);
    }

    private float centeredChildLeft(float availableWidth, float childWidth) {
        return getPaddingLeft() + (int) ((availableWidth - childWidth) / 2.0f);
    }

    protected boolean drawPillBackground(Canvas canvas, RectF pillBounds, float cornerRadius) {
        return false;
    }

    private void drawProtectionBackground(Canvas canvas) {
        if (controlBounds.isEmpty()) {
            return;
        }
        backgroundPaint.setColor(protectionBackgroundColor);
        backgroundPaint.setXfermode(DST_OVER_XFERMODE);
        canvas.drawRoundRect(
                controlBounds,
                dp(BACKGROUND_RADIUS_DP),
                dp(BACKGROUND_RADIUS_DP),
                backgroundPaint
        );
        backgroundPaint.setXfermode(null);
    }

    private void drawToggleRow(Canvas canvas, float alpha) {
        if (toggleStops.length == 0 || compactBounds.isEmpty()) {
            return;
        }

        float selectorInset = dp(SELECTOR_INSET_DP);
        float selectorSize = dp(SELECTOR_SIZE_DP);
        float selectorLeft = compactBounds.left
                + Math.round(animatedSelectorOffset)
                + selectorInset;
        selectorBounds.set(
                selectorLeft,
                compactBounds.top + selectorInset,
                selectorLeft + selectorSize,
                compactBounds.top + selectorInset + selectorSize
        );
        drawToggleLabels(canvas, alpha, unselectedToggleColor);
        if (expanded) {
            return;
        }
        selectorPaint.setColor(multAlpha(secondaryFixedColor, alpha));
        selectorPaint.setXfermode(XOR_XFERMODE);
        canvas.drawOval(selectorBounds, selectorPaint);
        selectorPaint.setColor(multAlpha(onSecondaryFixedColor, alpha));
        selectorPaint.setXfermode(DST_OVER_XFERMODE);
        canvas.drawOval(selectorBounds, selectorPaint);
        selectorPaint.setXfermode(null);
    }

    public void setSelectedLensInactive(boolean inactive) {
        if (selectedLensInactive == inactive) {
            return;
        }
        selectedLensInactive = inactive;
        if (lensInactiveAnimator != null) {
            lensInactiveAnimator.cancel();
        }
        lensInactiveAnimator = ValueAnimator.ofFloat(lensInactiveProgress, inactive ? 1.0f : 0.0f);
        lensInactiveAnimator.setDuration(LENS_INACTIVE_DURATION_MS);
        lensInactiveAnimator.addUpdateListener(animation -> {
            lensInactiveProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        lensInactiveAnimator.start();
    }

    private void drawToggleLabels(Canvas canvas, float alpha, int color) {
        for (int i = 0; i < toggleStops.length; i++) {
            boolean selected = i == selectedToggleIndex;
            Paint paint = selected ? selectedToggleTextPaint : toggleTextPaint;
            float labelAlpha = selected && !expanded
                    ? alpha * AndroidUtilities.lerp(1.0f, LENS_INACTIVE_ALPHA, lensInactiveProgress)
                    : alpha;
            paint.setColor(multAlpha(color, labelAlpha));
            float centerX = compactBounds.left + (i + 0.5f) * dp(TOGGLE_SIZE_DP);
            Paint.FontMetricsInt metrics = paint.getFontMetricsInt();
            int textHeight = metrics.descent - metrics.ascent;
            float textTop = compactBounds.top
                    + (int) ((compactBounds.height() - textHeight) / 2.0f);
            float baseline = textTop - metrics.ascent;
            canvas.drawText(getToggleLabel(i), centerX, baseline, paint);
        }
    }

    private void drawRuler(Canvas canvas, float alpha) {
        if (rulerBounds.isEmpty()) {
            return;
        }

        int rulerLayer = canvas.saveLayer(
                rulerBounds.left,
                controlBounds.top,
                rulerBounds.right,
                controlBounds.bottom,
                null
        );
        canvas.clipRect(
                rulerBounds.left,
                controlBounds.top,
                rulerBounds.right,
                controlBounds.bottom
        );

        float centerX = rulerBounds.centerX();
        float currentTick = zoomToTick(zoom);
        float tickBaseline = controlBounds.bottom - dp(TICK_BASELINE_FROM_BOTTOM_DP);
        float markerBaseline = controlBounds.bottom - dp(MARKER_BASELINE_FROM_BOTTOM_DP);
        int firstTick = Math.max(
                0,
                (int) Math.floor(currentTick - rulerBounds.width() / 2.0f / tickSpacing) - 1
        );
        int lastTick = Math.min(
                intervalCount,
                (int) Math.ceil(currentTick + rulerBounds.width() / 2.0f / tickSpacing) + 1
        );
        Paint.FontMetricsInt labelMetrics = rulerLabelPaint.getFontMetricsInt();
        int labelHeight = labelMetrics.descent - labelMetrics.ascent;
        float labelTop = controlBounds.bottom - dp(LABEL_BOTTOM_PADDING_DP) - labelHeight;
        float labelBaseline = labelTop - labelMetrics.ascent;

        for (int tick = firstTick; tick <= lastTick; tick++) {
            float x = centerX + (tick - currentTick) * tickSpacing;
            String label = primaryLabels.get(tick);
            boolean primary = label != null;
            tickPaint.setColor(multAlpha(primary ? primaryColor : minorTickColor, alpha));
            tickPaint.setStrokeWidth(dp(TICK_STROKE_DP));
            float height = dp(primary ? MAJOR_TICK_HEIGHT_DP : MINOR_TICK_HEIGHT_DP);
            canvas.drawLine(x, tickBaseline - height, x, tickBaseline, tickPaint);

            if (primary) {
                rulerLabelPaint.setColor(multAlpha(primaryColor, alpha));
                canvas.drawText(label, x, labelBaseline, rulerLabelPaint);
            }
        }

        markerPaint.setColor(multAlpha(primaryColor, alpha));
        markerPaint.setStrokeWidth(dp(MARKER_STROKE_DP));
        canvas.drawLine(
                centerX,
                markerBaseline - dp(MAJOR_TICK_HEIGHT_DP),
                centerX,
                markerBaseline,
                markerPaint
        );

        edgeFadePaint.setShader(new LinearGradient(
                rulerBounds.left,
                0.0f,
                rulerBounds.right,
                0.0f,
                new int[]{EDGE_FADE_COLOR, Color.BLACK, Color.BLACK, EDGE_FADE_COLOR},
                new float[]{0.0f, 1.0f / 3.0f, 2.0f / 3.0f, 1.0f},
                Shader.TileMode.CLAMP
        ));
        edgeFadePaint.setXfermode(DST_IN_XFERMODE);
        canvas.drawRect(
                rulerBounds.left,
                controlBounds.top,
                rulerBounds.right,
                controlBounds.bottom,
                edgeFadePaint
        );
        edgeFadePaint.setShader(null);
        edgeFadePaint.setXfermode(null);
        canvas.restoreToCount(rulerLayer);
    }

    private void drawBubble(Canvas canvas, float alpha) {
        String text = formatBubble(zoom);
        if (text.isEmpty()) {
            return;
        }
        Paint.FontMetricsInt metrics = bubbleTextPaint.getFontMetricsInt();
        float horizontalPadding = dp(BUBBLE_HORIZONTAL_PADDING_DP);
        float width = Math.max(
                dp(BUBBLE_MIN_WIDTH_DP),
                (float) Math.ceil(bubbleTextPaint.measureText(text))
        ) + horizontalPadding * 2.0f;
        float height = getBubbleHeight();
        float outerPadding = dp(BUBBLE_PORTRAIT_PADDING_DP);
        float centerX = controlBounds.centerX();
        float centerY = controlBounds.top - outerPadding - height / 2.0f;

        bubbleBounds.set(
                centerX - width / 2.0f,
                centerY - height / 2.0f,
                centerX + width / 2.0f,
                centerY + height / 2.0f
        );
        float bubbleAlpha = Utilities.clamp01(alpha);
        if (bubbleAlpha <= 0.001f) {
            return;
        }
        bubblePaint.setColor(secondaryFixedColor);
        bubbleTextPaint.setColor(onSecondaryFixedColor);
        float baseline = bubbleBounds.top
                + dp(BUBBLE_VERTICAL_PADDING_DP)
                - metrics.ascent;

        int alphaLayer = canvas.saveLayerAlpha(
                0.0f,
                0.0f,
                getWidth(),
                getHeight(),
                Math.round(bubbleAlpha * 255.0f)
        );
        canvas.drawRoundRect(bubbleBounds, height / 2.0f, height / 2.0f, bubblePaint);
        canvas.drawText(text, bubbleBounds.centerX(), baseline, bubbleTextPaint);
        canvas.restoreToCount(alphaLayer);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || toggleStops.length == 0) {
            return false;
        }

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            updateLayoutBounds();
            boolean rulerState = expanded;
            RectF target = rulerState ? rulerTouchBounds : compactTouchBounds;
            if (!target.contains(event.getX(), event.getY())) {
                return false;
            }
            downX = event.getX();
            downY = event.getY();
            lastTouchX = downX;
            movedPastSlop = false;
            compactGestureDown = !rulerState;
            dragStartedFromCompact = false;
            pressedToggleIndex = rulerState || !compactBounds.contains(downX, downY)
                    ? -1
                    : findToggleIndexAt(downX);
            setPressed(true);
            requestParentIntercept(false);
            obtainVelocityTracker(event);
            if (rulerState) {
                beginDrag(downX);
            } else {
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
            }
            return true;
        }

        if (velocityTracker != null) {
            velocityTracker.addMovement(event);
        }

        if (action == MotionEvent.ACTION_MOVE) {
            if (dragging) {
                float distance = (float) Math.hypot(
                        event.getX() - downX,
                        event.getY() - downY
                );
                if (!movedPastSlop && distance > touchSlop) {
                    movedPastSlop = true;
                }
                moveDrag(event.getX());
                return true;
            }
            if (!compactGestureDown) {
                return true;
            }
            float dx = event.getX() - downX;
            float dy = event.getY() - downY;
            if (!movedPastSlop && Math.hypot(dx, dy) > touchSlop) {
                movedPastSlop = true;
                removeCallbacks(longPressRunnable);
                if (Math.abs(dx) >= Math.abs(dy)) {
                    dragStartedFromCompact = true;
                    beginDrag(downX);
                    setExpanded(true, true);
                    moveDrag(event.getX());
                }
            }
            return true;
        }

        if (action == MotionEvent.ACTION_UP) {
            removeCallbacks(longPressRunnable);
            if (dragging) {
                boolean tapGesture = !movedPastSlop && !dragStartedFromCompact;
                if (tapGesture) {
                    setZoomFromRulerTap(event.getX());
                }
                finishDrag(true);
            } else if (!movedPastSlop && pressedToggleIndex >= 0) {
                int releasedIndex = findToggleIndexAt(event.getX());
                if (compactBounds.contains(event.getX(), event.getY())
                        && releasedIndex == pressedToggleIndex) {
                    selectToggle(releasedIndex);
                }
                clearTouchState();
                performClick();
            } else {
                clearTouchState();
            }
            recycleVelocityTracker();
            return true;
        }

        if (action == MotionEvent.ACTION_CANCEL) {
            removeCallbacks(longPressRunnable);
            if (dragging) {
                finishDrag(false);
            } else {
                clearTouchState();
            }
            recycleVelocityTracker();
            return true;
        }

        return super.onTouchEvent(event);
    }

    private void beginDrag(float x) {
        if (stopZoomAnimator()) {
            syncSelectedToggle(true);
        }
        dragging = true;
        lastTouchX = x;
        dragTick = zoomToTick(zoom);
        dragPrimarySegment = PRIMARY_SEGMENT_UNSET;
        stickyTick = -1;
        stickyDistance = 0.0f;
        stickyFactor = 0.0f;
        removeCallbacks(autoCollapseRunnable);
    }

    private void moveDrag(float x) {
        float delta = x - lastTouchX;
        lastTouchX = x;
        if (Math.abs(delta) < 0.0001f) {
            return;
        }

        float currentTick = dragTick;
        float velocity = getCurrentXVelocity();

        if (stickyTick >= 0) {
            float remainingStickyDistance = Math.max(
                    0.0f,
                    tickSpacing * stickyFactor - stickyDistance
            );
            float absoluteDelta = Math.abs(delta);
            if (absoluteDelta <= remainingStickyDistance) {
                stickyDistance += absoluteDelta;
                setDragTick(stickyTick);
                return;
            }
            delta = Math.copySign(absoluteDelta - remainingStickyDistance, delta);
            currentTick = stickyTick;
            stickyTick = -1;
            stickyDistance = 0.0f;
            stickyFactor = 0.0f;
        }

        if (rebuiltPrimaryTickIndices.length > 0) {
            int primarySegment = findPrimarySegment(currentTick, delta);
            if (dragPrimarySegment == PRIMARY_SEGMENT_UNSET) {
                dragPrimarySegment = primarySegment;
            } else if (primarySegment != dragPrimarySegment) {
                int previousPrimarySegment = dragPrimarySegment;
                dragPrimarySegment = primarySegment;
                float stickiness = calculateStickiness(velocity);
                if (stickiness > 0.0f) {
                    int crossedPrimaryTick = Math.max(
                            previousPrimarySegment,
                            primarySegment
                    );
                    float stickyThreshold = tickSpacing * stickiness;
                    float distancePastPrimary = Math.abs(
                            currentTick - crossedPrimaryTick
                    ) * tickSpacing;
                    if (distancePastPrimary < stickyThreshold) {
                        float remainingStickyDistance = stickyThreshold - distancePastPrimary;
                        float absoluteDelta = Math.abs(delta);
                        if (absoluteDelta <= remainingStickyDistance) {
                            stickyTick = crossedPrimaryTick;
                            stickyDistance = distancePastPrimary + absoluteDelta;
                            stickyFactor = stickiness;
                            setDragTick(stickyTick);
                            return;
                        }
                        delta = Math.copySign(
                                absoluteDelta - remainingStickyDistance,
                                delta
                        );
                        currentTick = crossedPrimaryTick;
                    }
                }
            }
        }

        float targetTick = Utilities.clamp(currentTick - delta / tickSpacing, intervalCount, 0.0f);
        setDragTick(targetTick);
    }

    private void setDragTick(float tick) {
        dragTick = Utilities.clamp(tick, intervalCount, 0.0f);
        setTickInternal(dragTick, true);
    }

    private void finishDrag(boolean completed) {
        dragging = false;
        compactGestureDown = false;
        dragStartedFromCompact = false;
        stickyTick = -1;
        stickyDistance = 0.0f;
        stickyFactor = 0.0f;
        dragPrimarySegment = PRIMARY_SEGMENT_UNSET;
        pressedToggleIndex = -1;
        setPressed(false);
        requestParentIntercept(true);
        resetAutoCollapseTimeout();
        if (completed) {
            performClick();
        }
    }

    private void setZoomFromRulerTap(float x) {
        float targetTick = zoomToTick(zoom) + (x - rulerBounds.centerX()) / tickSpacing;
        animateZoomTo(tickToZoom(targetTick), true);
    }

    private void selectToggle(int index) {
        if (index < 0 || index >= toggleStops.length) {
            return;
        }
        if (index == selectedToggleIndex && selectedShowsStopValue) {
            return;
        }
        animateZoomTo(toggleStops[index], true, index);
    }

    private void clearTouchState() {
        dragging = false;
        compactGestureDown = false;
        dragStartedFromCompact = false;
        movedPastSlop = false;
        pressedToggleIndex = -1;
        stickyTick = -1;
        stickyDistance = 0.0f;
        stickyFactor = 0.0f;
        dragPrimarySegment = PRIMARY_SEGMENT_UNSET;
        setPressed(false);
        requestParentIntercept(true);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return expanded ? SeekBar.class.getName() : View.class.getName();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(getAccessibilityClassName());
        info.setContentDescription(formatBubble(zoom));
        if (expanded) {
            info.setScrollable(true);
            info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                    AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,
                    minZoom,
                    maxZoom,
                    zoom
            ));
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
        } else {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_CLICK) {
            setExpanded(!expanded, true);
            return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
            cancelZoomAnimator();
            setTickInternal(zoomToTick(zoom) + 1.0f, true);
            resetAutoCollapseTimeout();
            return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            cancelZoomAnimator();
            setTickInternal(zoomToTick(zoom) - 1.0f, true);
            resetAutoCollapseTimeout();
            return true;
        }
        if (action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId() && arguments != null && arguments.containsKey(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)) {
            cancelZoomAnimator();
            setZoomInternal(
                    arguments.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE),
                    true,
                    true
            );
            resetAutoCollapseTimeout();
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        cancelTransientSprings();
        tickSpacing = Math.max(1.0f, Math.round(dp(TICK_SPACING_DP)));
        configurePaints();
        rebuildScale();
        settleTransientAnimationValues();
        requestLayout();
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        cancelTransientSprings();
        settleTransientAnimationValues();
        if (expanded && !dragging) {
            resetAutoCollapseTimeout();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelZoomConfigurationTransition();
        removeCallbacks(longPressRunnable);
        removeCallbacks(autoCollapseRunnable);
        animate().cancel();
        cancelZoomAnimator();
        cancelTransientSprings();
        settleTransientAnimationValues();
        clearTouchState();
        recycleVelocityTracker();
    }

    private void animateExpandedProgress(float target, boolean animated) {
        if (expandedAnimator != null) {
            expandedAnimator.cancel();
            expandedAnimator = null;
        }
        if (!animated || !isLaidOut()) {
            expandedProgress = target;
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(expandedProgress, target);
        expandedAnimator = animator;
        animator.setDuration(MORPH_DURATION_MS);
        animator.setInterpolator(MORPH_INTERPOLATOR);
        animator.addUpdateListener(animation -> {
            expandedProgress = Utilities.clamp01((float) animation.getAnimatedValue());
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (expandedAnimator == animation) {
                    expandedAnimator = null;
                }
            }
        });
        animator.start();
    }

    private void cancelTransientSprings() {
        if (expandedAnimator != null) {
            expandedAnimator.cancel();
            expandedAnimator = null;
        }
        widthSpring.cancel();
        selectorSpring.cancel();
    }

    private void settleTransientAnimationValues() {
        expandedProgress = expanded ? 1.0f : 0.0f;
        animatedControlWidth = expanded ? getExpandedBackgroundWidth() : getCompactWidth();
        animatedSelectorOffset = getSelectorOffset(selectedToggleIndex);
    }

    private void rebuildScale() {
        float octaveCount = (float) (Math.log(maxZoom / minZoom) / LOG_2);
        float displayOneZoom = displayNormalizationFactor;
        if (minZoom < displayOneZoom && maxZoom >= displayOneZoom) {
            float ultrawideOctaves = (float) (
                    Math.log(displayOneZoom / minZoom) / LOG_2
            );
            oneXTick = Math.max(
                    MIN_ULTRAWIDE_INTERVALS,
                    Math.round(ultrawideOctaves * TICKS_PER_OCTAVE)
            );
            int teleTicks = 0;
            if (maxZoom > displayOneZoom) {
                float teleOctaves = (float) (
                        Math.log(maxZoom / displayOneZoom) / LOG_2
                );
                teleTicks = Math.max(1, Math.round(teleOctaves * TICKS_PER_OCTAVE));
            }
            intervalCount = oneXTick + teleTicks;
        } else {
            oneXTick = -1;
            intervalCount = Math.max(1, Math.round(octaveCount * TICKS_PER_OCTAVE));
        }
        primaryLabels.clear();

        int[] indices = new int[rulerStops.length];
        int indexCount = 0;
        for (float stop : rulerStops) {
            if (stop < minZoom || stop > maxZoom) {
                continue;
            }
            int tick = Math.round(zoomToTick(stop));
            primaryLabels.put(tick, formatRuler(stop));
            boolean duplicate = false;
            for (int i = 0; i < indexCount; i++) {
                if (indices[i] == tick) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                indices[indexCount++] = tick;
            }
        }
        rebuiltPrimaryTickIndices = Arrays.copyOf(indices, indexCount);
        Arrays.sort(rebuiltPrimaryTickIndices);
    }

    private void setTickInternal(float tick, boolean fromUser) {
        setZoomInternal(tickToZoom(tick), fromUser, true);
    }

    private float tickToZoom(float tick) {
        tick = Utilities.clamp(tick, intervalCount, 0.0f);
        if (tick <= 0.0f) {
            return minZoom;
        }
        if (tick >= intervalCount) {
            return maxZoom;
        }
        if (oneXTick >= 0) {
            if (tick == oneXTick) {
                return displayNormalizationFactor;
            }
            if (tick <= oneXTick) {
                float normalized = tick / oneXTick;
                return (float) (
                        minZoom * Math.exp(
                                Math.log(displayNormalizationFactor / minZoom) * normalized
                        )
                );
            }
            float normalized = (tick - oneXTick) / (intervalCount - oneXTick);
            return (float) (
                    displayNormalizationFactor * Math.exp(
                            Math.log(maxZoom / displayNormalizationFactor) * normalized
                    )
            );
        }
        float normalized = tick / intervalCount;
        return (float) (
                minZoom * Math.exp(Math.log(maxZoom / minZoom) * normalized)
        );
    }

    private void setZoomInternal(float value, boolean fromUser, boolean syncToggle) {
        zoom = Utilities.clamp(value, maxZoom, minZoom);
        if (syncToggle) {
            syncSelectedToggle(!expanded);
        }
        updateAccessibilityDescription();
        invalidate();
        if (fromUser && onZoomChangeListener != null) {
            onZoomChangeListener.onZoomChanged(zoom);
        }
    }

    private float zoomToTick(float value) {
        value = Utilities.clamp(value, maxZoom, minZoom);
        if (value <= minZoom) {
            return 0.0f;
        }
        if (value >= maxZoom) {
            return intervalCount;
        }
        if (oneXTick >= 0) {
            if (value == displayNormalizationFactor) {
                return oneXTick;
            }
            if (value <= displayNormalizationFactor) {
                float normalized = (float) (
                        Math.log(value / minZoom)
                                / Math.log(displayNormalizationFactor / minZoom)
                );
                return normalized * oneXTick;
            }
            float normalized = (float) (
                    Math.log(value / displayNormalizationFactor)
                            / Math.log(maxZoom / displayNormalizationFactor)
            );
            return oneXTick + normalized * (intervalCount - oneXTick);
        }
        float normalized = (float) (
                Math.log(value / minZoom)
                        / Math.log(maxZoom / minZoom)
        );
        return normalized * intervalCount;
    }

    private void syncSelectedToggle(boolean animated) {
        selectedShowsStopValue = false;
        int index = findToggleSegment(zoom);
        if (index < 0) {
            selectedToggleIndex = -1;
            animatedSelectorOffset = 0.0f;
            return;
        }
        if (selectedToggleIndex != index) {
            selectedToggleIndex = index;
            animateSelectorTo(index, animated);
        } else if (!animated) {
            selectorSpring.cancel();
            animatedSelectorOffset = getSelectorOffset(index);
        }
    }

    private int findToggleSegment(float value) {
        if (toggleStops.length == 0) {
            return -1;
        }
        int index = 0;
        for (int i = 1; i < toggleStops.length; i++) {
            if (value < toggleStops[i]) {
                break;
            }
            index = i;
        }
        return index;
    }

    private void animateSelectorTo(int index, boolean animated) {
        float target = getSelectorOffset(index);
        if (!animated || !isLaidOut()) {
            selectorSpring.cancel();
            animatedSelectorOffset = target;
            invalidate();
        } else {
            selectorSpring.animateToFinalPosition(target);
        }
    }

    private void animateZoomTo(float target, boolean fromUser) {
        animateZoomTo(target, fromUser, -1);
    }

    private void animateZoomTo(float target, boolean fromUser, int toggleTarget) {
        boolean claimsToggle = toggleTarget >= 0 && toggleTarget < toggleStops.length;
        if (claimsToggle) {
            stopZoomAnimator();
        } else {
            cancelZoomAnimator(true);
        }
        target = Utilities.clamp(target, maxZoom, minZoom);
        if (claimsToggle) {
            selectedToggleIndex = toggleTarget;
            selectedShowsStopValue = true;
            animateSelectorTo(toggleTarget, true);
        }
        if (Math.abs(target - zoom) < 0.0001f) {
            setZoomInternal(target, fromUser, !claimsToggle);
            return;
        }

        float start = zoom;
        float ratio = Math.max(start, target) / Math.min(start, target);
        long duration = Math.min(
                MAX_ZOOM_ANIMATION_DURATION_MS,
                (long) Math.rint(ratio * MAX_ZOOM_ANIMATION_DURATION_MS / 3.0f)
        );
        final float finalTarget = target;
        ValueAnimator animator = ValueAnimator.ofFloat(start, target);
        zoomAnimator = animator;
        animator.setDuration(duration);
        animator.setInterpolator(ZOOM_INTERPOLATOR);
        animator.addUpdateListener(animation -> setZoomInternal(
                (float) animation.getAnimatedValue(),
                fromUser,
                !claimsToggle
        ));
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled && Math.abs(zoom - finalTarget) > 0.0001f) {
                    setZoomInternal(finalTarget, fromUser, !claimsToggle);
                }
                if (zoomAnimator == animation) {
                    zoomAnimator = null;
                    syncSelectedToggle(true);
                }
            }
        });
        animator.start();
    }

    private boolean stopZoomAnimator() {
        ValueAnimator animator = zoomAnimator;
        if (animator == null) {
            return false;
        }
        zoomAnimator = null;
        animator.cancel();
        return true;
    }

    private void cancelZoomAnimator() {
        cancelZoomAnimator(false);
    }

    private void cancelZoomAnimator(boolean animateSelector) {
        if (stopZoomAnimator()) {
            syncSelectedToggle(animateSelector);
        }
    }

    private int findToggleIndexAt(float x) {
        if (x < compactBounds.left || x > compactBounds.right || toggleStops.length == 0) {
            return -1;
        }
        int index = (int) ((x - compactBounds.left) / dp(TOGGLE_SIZE_DP));
        return Math.max(0, Math.min(toggleStops.length - 1, index));
    }

    private int findPrimarySegment(float tick, float deltaX) {
        if (rebuiltPrimaryTickIndices.length == 0) {
            return PRIMARY_SEGMENT_UNSET;
        }
        int first = rebuiltPrimaryTickIndices[0];
        if (tick < first || (tick == first && deltaX >= 0.0f)) {
            return -1;
        }
        for (int i = 0; i < rebuiltPrimaryTickIndices.length - 1; i++) {
            int next = rebuiltPrimaryTickIndices[i + 1];
            if (deltaX < 0.0f ? tick < next : tick <= next) {
                return rebuiltPrimaryTickIndices[i];
            }
        }
        return rebuiltPrimaryTickIndices[rebuiltPrimaryTickIndices.length - 1];
    }

    private float calculateStickiness(float velocity) {
        float speed = Math.abs(velocity);
        if (speed <= MAX_VELOCITY_FOR_STICKINESS) {
            return MAX_STICKINESS;
        }
        if (speed >= MIN_VELOCITY_WITHOUT_STICKINESS) {
            return 0.0f;
        }
        return MAX_STICKINESS * (
                (MIN_VELOCITY_WITHOUT_STICKINESS - speed)
                        / (MIN_VELOCITY_WITHOUT_STICKINESS
                        - MAX_VELOCITY_FOR_STICKINESS)
        );
    }

    private void obtainVelocityTracker(MotionEvent event) {
        recycleVelocityTracker();
        velocityTracker = VelocityTracker.obtain();
        velocityTracker.addMovement(event);
    }

    private float getCurrentXVelocity() {
        if (velocityTracker == null) {
            return 0.0f;
        }
        velocityTracker.computeCurrentVelocity(1000);
        return velocityTracker.getXVelocity();
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private void updateTargetControlWidth(boolean animated) {
        float target = expanded ? getExpandedBackgroundWidth() : getCompactWidth();
        if (!animated || !isLaidOut()) {
            widthSpring.cancel();
            animatedControlWidth = target;
            invalidate();
        } else {
            widthSpring.animateToFinalPosition(target);
        }
    }

    private void resetAutoCollapseTimeout() {
        removeCallbacks(autoCollapseRunnable);
        AccessibilityManager accessibilityManager = (AccessibilityManager) getContext()
                .getSystemService(Context.ACCESSIBILITY_SERVICE);
        boolean touchExplorationEnabled = accessibilityManager != null
                && accessibilityManager.isTouchExplorationEnabled();
        if (expanded && !dragging && !externalZoomGesture && !touchExplorationEnabled) {
            postDelayed(autoCollapseRunnable, AUTO_COLLAPSE_TIMEOUT_MS);
        }
    }

    private void requestParentIntercept(boolean allowIntercept) {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(!allowIntercept);
        }
    }

    private void configurePaints() {
        backgroundPaint.setStyle(Paint.Style.FILL);
        selectorPaint.setStyle(Paint.Style.FILL);
        bubblePaint.setStyle(Paint.Style.FILL);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);
        markerPaint.setStrokeCap(Paint.Cap.ROUND);

        toggleTextPaint.setTextAlign(Paint.Align.CENTER);
        toggleTextPaint.setTextSize(dp(14.0f));
        toggleTextPaint.setTypeface(AndroidUtilities.bold());

        selectedToggleTextPaint.setTextAlign(Paint.Align.CENTER);
        selectedToggleTextPaint.setTextSize(dp(16.0f));
        selectedToggleTextPaint.setTypeface(AndroidUtilities.bold());

        rulerLabelPaint.setTextAlign(Paint.Align.CENTER);
        rulerLabelPaint.setTextSize(dp(11.0f));
        rulerLabelPaint.setTypeface(AndroidUtilities.bold());

        bubbleTextPaint.setTextAlign(Paint.Align.CENTER);
        bubbleTextPaint.setTextSize(dp(16.0f));
        bubbleTextPaint.setTypeface(AndroidUtilities.bold());
        configurePaintColors();
    }

    private void configurePaintColors() {
        tickPaint.setColor(minorTickColor);
        markerPaint.setColor(primaryColor);
        rulerLabelPaint.setColor(primaryColor);
        selectorPaint.setColor(secondaryFixedColor);
        bubblePaint.setColor(secondaryFixedColor);
        bubbleTextPaint.setColor(onSecondaryFixedColor);
    }

    private String getToggleLabel(int index) {
        if (index == selectedToggleIndex) {
            return formatBubble(selectedShowsStopValue ? toggleStops[index] : zoom);
        }
        return formatToggle(toggleStops[index]);
    }

    private String formatBubble(float value) {
        return formatZoomNumber(value) + "×";
    }

    private String formatToggle(float value) {
        return formatZoomNumber(value);
    }

    private String formatRuler(float value) {
        return formatZoomNumber(value);
    }

    private String formatZoomNumber(float value) {
        float displayValue = normalizeDisplayZoom(value);
        Locale locale = Locale.getDefault();
        if (displayValue % 1.0f == 0.0f) {
            return String.format(locale, "%.0f", displayValue);
        }
        String formatted = String.format(locale, "%.1f", displayValue);
        if (formatted.startsWith("0")) {
            formatted = formatted.substring(1);
        }
        return formatted;
    }

    private float normalizeDisplayZoom(float value) {
        value /= displayNormalizationFactor;
        float timesTen = value * 10.0f;
        if (value < 1.0f) {
            return (float) Math.floor(timesTen) / 10.0f;
        }

        float lower = (float) Math.floor(timesTen);
        if (lower % 5.0f == 0.0f) {
            timesTen = lower;
        } else {
            float upper = (float) Math.ceil(timesTen);
            if (upper % 5.0f == 0.0f) {
                timesTen = upper;
            }
        }
        float rounded = (float) Math.rint(timesTen) / 10.0f;
        if (rounded % 1.0f == 0.0f || rounded >= HIDE_DECIMAL_POINT_THRESHOLD) {
            return (float) Math.rint(value);
        }
        return rounded;
    }

    private void updateAccessibilityDescription() {
        int displayedValue = Math.round(normalizeDisplayZoom(zoom) * 10.0f);
        if (displayedValue == lastDescribedZoom) {
            return;
        }
        lastDescribedZoom = displayedValue;
        setContentDescription(formatBubble(zoom));
    }

    private float getExpandedBackgroundWidth() {
        return dp(EXPANDED_WIDTH_DP);
    }

    private float getCompactWidth() {
        return dp(TOGGLE_SIZE_DP) * toggleStops.length;
    }

    private float getExpandedRulerWidth() {
        return dp(EXPANDED_WIDTH_DP - RULER_WIDTH_DELTA_DP);
    }

    private float getSelectorOffset(int index) {
        return dp(TOGGLE_SIZE_DP) * Math.max(0, index);
    }

    private float getBubbleHeight() {
        Paint.FontMetricsInt metrics = bubbleTextPaint.getFontMetricsInt();
        return metrics.descent - metrics.ascent
                + dp(BUBBLE_VERTICAL_PADDING_DP) * 2.0f;
    }

    private static float[] sanitizeStops(float[] stops, float min, float max) {
        if (stops == null || stops.length == 0) {
            return new float[0];
        }
        float[] result = new float[stops.length];
        int count = 0;
        for (float stop : stops) {
            if (Float.isFinite(stop) && stop >= min && stop <= max) {
                result[count++] = stop;
            }
        }
        result = Arrays.copyOf(result, count);
        Arrays.sort(result);
        if (result.length < 2) {
            return result;
        }
        int unique = 1;
        for (int i = 1; i < result.length; i++) {
            if (Float.compare(result[i], result[unique - 1]) != 0) {
                result[unique++] = result[i];
            }
        }
        return Arrays.copyOf(result, unique);
    }

    private static final class ZoomLookupInterpolator implements TimeInterpolator {
        private static final float[] VALUES = {
                0.0f, 0.0008f, 0.0016f, 0.0024f, 0.0032f, 0.0057f, 0.0083f,
                0.0109f, 0.0134f, 0.0171f, 0.0218f, 0.0266f, 0.0313f, 0.0360f,
                0.0431f, 0.0506f, 0.0581f, 0.0656f, 0.0733f, 0.0835f, 0.0937f,
                0.1055f, 0.1179f, 0.1316f, 0.1466f, 0.1627f, 0.1810f, 0.2003f,
                0.2226f, 0.2468f, 0.2743f, 0.3060f, 0.3408f, 0.3852f, 0.4317f,
                0.4787f, 0.5177f, 0.5541f, 0.5834f, 0.6123f, 0.6333f, 0.6542f,
                0.6739f, 0.6887f, 0.7035f, 0.7183f, 0.7308f, 0.7412f, 0.7517f,
                0.7621f, 0.7725f, 0.7805f, 0.7879f, 0.7953f, 0.8027f, 0.8101f,
                0.8175f, 0.8230f, 0.8283f, 0.8336f, 0.8388f, 0.8441f, 0.8494f,
                0.8546f, 0.8592f, 0.8630f, 0.8667f, 0.8705f, 0.8743f, 0.8780f,
                0.8818f, 0.8856f, 0.8893f, 0.8927f, 0.8953f, 0.8980f, 0.9007f,
                0.9034f, 0.9061f, 0.9087f, 0.9114f, 0.9141f, 0.9168f, 0.9194f,
                0.9218f, 0.9236f, 0.9255f, 0.9274f, 0.9293f, 0.9312f, 0.9331f,
                0.9350f, 0.9368f, 0.9387f, 0.9406f, 0.9425f, 0.9444f, 0.9460f,
                0.9473f, 0.9486f, 0.9499f, 0.9512f, 0.9525f, 0.9538f, 0.9551f,
                0.9564f, 0.9577f, 0.9590f, 0.9603f, 0.9616f, 0.9629f, 0.9642f,
                0.9654f, 0.9663f, 0.9672f, 0.9680f, 0.9689f, 0.9697f, 0.9706f,
                0.9715f, 0.9723f, 0.9732f, 0.9741f, 0.9749f, 0.9758f, 0.9766f,
                0.9775f, 0.9784f, 0.9792f, 0.9801f, 0.9808f, 0.9813f, 0.9819f,
                0.9824f, 0.9829f, 0.9835f, 0.9840f, 0.9845f, 0.9850f, 0.9856f,
                0.9861f, 0.9866f, 0.9872f, 0.9877f, 0.9882f, 0.9887f, 0.9893f,
                0.9898f, 0.9903f, 0.9909f, 0.9914f, 0.9917f, 0.9920f, 0.9922f,
                0.9925f, 0.9928f, 0.9931f, 0.9933f, 0.9936f, 0.9939f, 0.9942f,
                0.9944f, 0.9947f, 0.9950f, 0.9953f, 0.9955f, 0.9958f, 0.9961f,
                0.9964f, 0.9966f, 0.9969f, 0.9972f, 0.9975f, 0.9977f, 0.9979f,
                0.9981f, 0.9982f, 0.9983f, 0.9984f, 0.9986f, 0.9987f, 0.9988f,
                0.9989f, 0.9991f, 0.9992f, 0.9993f, 0.9994f, 0.9995f, 0.9995f,
                0.9996f, 0.9996f, 0.9997f, 0.9997f, 0.9997f, 0.9998f, 0.9998f,
                0.9998f, 0.9999f, 0.9999f, 1.0f, 1.0f
        };

        @Override
        public float getInterpolation(float input) {
            if (input <= 0.0f) {
                return 0.0f;
            }
            if (input >= 1.0f) {
                return 1.0f;
            }
            int index = Math.min((int) (input * 200.0f), 199);
            float fraction = (input - index * 0.005f) / 0.005f;
            return VALUES[index] + fraction * (VALUES[index + 1] - VALUES[index]);
        }
    }
}