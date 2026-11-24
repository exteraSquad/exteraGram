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
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.AnimationUtils;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.SpringAnimation;

import com.exteragram.messenger.utils.ui.material.Material3Tokens;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;

public class WavySeekBar extends View {

    public interface Delegate {
        void onSeek(float progress);

        default void onDragStateChanged(boolean dragging) {
        }

        default CharSequence getTimeLabel(float progress) {
            return null;
        }

        default CharSequence getAccessibilityValue() {
            return null;
        }
    }

    private static final float LINE_WIDTH_DP = 4f;
    private static final float AMPLITUDE_DP = 3f;
    private static final float WAVELENGTH_DP = 40f;
    private static final float PHASE_SPEED_DP = 24f;
    private static final float RAMP = 0.1f;
    private static final float JUMP_STIFFNESS = 300f;
    private static final float JUMP_MIN_DELTA = 0.05f;
    private static final float HANDLE_WIDTH_DP = 4f;
    private static final float HANDLE_DRAG_WIDTH_DP = 2f;
    private static final float HANDLE_HEIGHT_DP = 18f;
    private static final float HANDLE_GAP_DP = 4f;
    private static final float EDGE_DP = 2f;
    private static final float WAVE_STEP_DP = 1.5f;
    private static final float BUBBLE_HEIGHT_DP = 26f;
    private static final float BUBBLE_OFFSET_DP = 30f;
    private static final float ACCESSIBILITY_STEP = 0.05f;
    private static final float LINE_CENTER_FROM_BOTTOM_DP = 16f;
    private static final float TOUCH_HALF_HEIGHT_DP = 24f;

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint bubbleTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path wavePath = new Path();
    private final RectF rect = new RectF();

    private final SpringAnimation amplitudeSpring;
    private final SpringAnimation handleSpring;
    private final SpringAnimation bubbleSpring;
    private final SpringAnimation jumpSpring;

    private Delegate delegate;

    private int waveColor;
    private int trackColor;
    private int bufferColor;
    private int bubbleColor;
    private int bubbleTextColor;

    private float progress;
    private float jumpOffset;
    private float bufferedProgress;
    private float amplitude;
    private float handleWidth;
    private float bubble;
    private float phase;
    private long lastFrameTime;
    private float playbackSpeed = 1f;
    private float lineWidthDp = LINE_WIDTH_DP;
    private float amplitudeDp = AMPLITUDE_DP;
    private float wavelength = WAVELENGTH_DP;
    private float phaseSpeedDp = PHASE_SPEED_DP;
    private float handleWidthDp = HANDLE_WIDTH_DP;
    private float handleHeightDp = HANDLE_HEIGHT_DP;
    private float collapse;
    private float collapsedLineWidth;
    private boolean playing;

    private boolean dragging;
    private boolean externalDragging;
    private boolean movedWhileDragging;
    private float dragProgress;
    private float downX;
    private final int touchSlop;

    private boolean loading;
    private float loadingProgress;


    public WavySeekBar(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeWidth(dpf2(lineWidthDp));
        bubbleTextPaint.setTextSize(dp(13));
        bubbleTextPaint.setTypeface(AndroidUtilities.bold());
        bubbleTextPaint.setTextAlign(Paint.Align.CENTER);
        amplitudeSpring = createSpring(1f, 260f, value -> amplitude = value);
        handleSpring = createSpring(Material3Tokens.FAST_SPATIAL_DAMPING, Material3Tokens.FAST_SPATIAL_STIFFNESS, value -> handleWidth = value);
        bubbleSpring = createSpring(Material3Tokens.FAST_SPATIAL_DAMPING, Material3Tokens.FAST_SPATIAL_STIFFNESS, value -> bubble = value);
        jumpSpring = createSpring(1f, JUMP_STIFFNESS, value -> jumpOffset = value);
        jumpSpring.setMinimumVisibleChange(1f / 1000f);
        handleWidth = dpf2(handleWidthDp);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setFocusable(true);
    }

    private SpringAnimation createSpring(float damping, float stiffness, Utilities.Callback<Float> setter) {
        return Material3Tokens.createSpring(damping, stiffness, 1f / 100f, value -> {
            setter.run(value);
            invalidate();
        });
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public void setWaveStyle(float lineWidthDp, float amplitudeDp, float wavelengthDp, float speedDp, float handleWidthDp, float handleHeightDp) {
        this.lineWidthDp = lineWidthDp;
        this.amplitudeDp = amplitudeDp;
        this.wavelength = wavelengthDp;
        this.phaseSpeedDp = speedDp;
        this.handleWidthDp = handleWidthDp;
        this.handleHeightDp = handleHeightDp;
        handleSpring.cancel();
        handleWidth = targetHandleWidth();
        amplitudeSpring.cancel();
        amplitude = targetAmplitude();
        invalidate();
    }

    private boolean isDragActive() {
        return dragging || externalDragging;
    }

    private float targetAmplitude() {
        return playing && !isDragActive() ? dpf2(amplitudeDp) : 0f;
    }

    private float targetHandleWidth() {
        return dpf2(isDragActive() ? HANDLE_DRAG_WIDTH_DP : handleWidthDp);
    }

    private void animateDragState() {
        if (isAttachedToWindow()) {
            Material3Tokens.animate(handleSpring, handleWidth, targetHandleWidth());
            Material3Tokens.animate(amplitudeSpring, amplitude, targetAmplitude());
        } else {
            handleSpring.cancel();
            amplitudeSpring.cancel();
            handleWidth = targetHandleWidth();
            amplitude = targetAmplitude();
        }
        lastFrameTime = 0;
        invalidate();
    }

    public void setExternalDragging(boolean externalDragging) {
        if (this.externalDragging == externalDragging) {
            return;
        }
        this.externalDragging = externalDragging;
        animateDragState();
    }

    public void setCollapse(float collapse, float collapsedLineWidth) {
        collapse = Utilities.clamp01(collapse);
        if (this.collapse != collapse || this.collapsedLineWidth != collapsedLineWidth) {
            this.collapse = collapse;
            this.collapsedLineWidth = collapsedLineWidth;
            invalidate();
        }
    }

    public void setColors(int waveColor, int trackColor, int bufferColor, int bubbleColor, int bubbleTextColor) {
        if (this.waveColor == waveColor && this.trackColor == trackColor && this.bufferColor == bufferColor && this.bubbleColor == bubbleColor && this.bubbleTextColor == bubbleTextColor) {
            return;
        }
        this.waveColor = waveColor;
        this.trackColor = trackColor;
        this.bufferColor = bufferColor;
        this.bubbleColor = bubbleColor;
        this.bubbleTextColor = bubbleTextColor;
        invalidate();
    }

    public boolean isDragging() {
        return dragging;
    }

    public float getProgress() {
        return dragging ? dragProgress : progress;
    }

    public void setProgress(float progress) {
        setProgress(progress, true);
    }

    public void setProgress(float progress, boolean animated) {
        progress = Utilities.clamp01(progress);
        if (!animated) {
            cancelJump();
        }
        if (this.progress != progress) {
            if (animated && !dragging && isAttachedToWindow() && Math.abs(this.progress - progress) >= JUMP_MIN_DELTA) {
                Material3Tokens.animate(jumpSpring, this.progress + jumpOffset - progress, 0f);
            }
            this.progress = progress;
            if (!dragging) {
                invalidate();
            }
        }
    }

    public void setBufferedProgress(float bufferedProgress) {
        bufferedProgress = Utilities.clamp01(bufferedProgress);
        if (this.bufferedProgress != bufferedProgress) {
            this.bufferedProgress = bufferedProgress;
            invalidate();
        }
    }

    public void setPlaying(boolean playing, float speed, boolean animated) {
        playbackSpeed = Math.max(0.1f, speed);
        if (this.playing == playing && (animated || amplitude == targetAmplitude())) {
            return;
        }
        this.playing = playing;
        final float target = targetAmplitude();
        if (animated && isAttachedToWindow()) {
            Material3Tokens.animate(amplitudeSpring, amplitude, target);
        } else {
            amplitudeSpring.cancel();
            amplitude = target;
        }
        lastFrameTime = 0;
        invalidate();
    }

    public void setLoading(boolean loading, float loadingProgress) {
        if (this.loading != loading || this.loadingProgress != loadingProgress) {
            this.loading = loading;
            this.loadingProgress = Utilities.clamp01(loadingProgress);
            if (loading && dragging) {
                cancelDrag();
            }
            invalidate();
        }
    }

    private float getLineStart() {
        return dpf2(EDGE_DP);
    }

    private float getLineEnd() {
        return getWidth() - dpf2(EDGE_DP);
    }

    private float getCenterY() {
        return getHeight() - dpf2(LINE_CENTER_FROM_BOTTOM_DP);
    }

    private float progressAt(float x) {
        final float start = getLineStart();
        final float end = getLineEnd();
        if (end <= start) {
            return 0f;
        }
        return Utilities.clamp01((x - start) / (end - start));
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (loading || !isEnabled()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (Math.abs(event.getY() - getCenterY()) > dpf2(TOUCH_HALF_HEIGHT_DP)) {
                    return false;
                }
                downX = event.getX();
                movedWhileDragging = false;
                dragging = true;
                dragProgress = progressAt(event.getX());
                cancelJump();
                getParent().requestDisallowInterceptTouchEvent(true);
                animateDragState();
                Material3Tokens.animate(bubbleSpring, bubble, 1f);
                if (delegate != null) {
                    delegate.onDragStateChanged(true);
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging) {
                    return false;
                }
                if (Math.abs(event.getX() - downX) > touchSlop) {
                    movedWhileDragging = true;
                }
                dragProgress = progressAt(event.getX());
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging) {
                    return false;
                }
                dragProgress = progressAt(event.getX());
                finishDrag(true);
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    finishDrag(movedWhileDragging);
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void finishDrag(boolean commit) {
        dragging = false;
        getParent().requestDisallowInterceptTouchEvent(false);
        animateDragState();
        Material3Tokens.animate(bubbleSpring, bubble, 0f);
        if (commit) {
            progress = dragProgress;
            if (delegate != null) {
                delegate.onSeek(dragProgress);
            }
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
        }
        if (delegate != null) {
            delegate.onDragStateChanged(false);
        }
        invalidate();
    }

    private void cancelDrag() {
        dragging = false;
        handleSpring.cancel();
        amplitudeSpring.cancel();
        bubbleSpring.cancel();
        handleWidth = targetHandleWidth();
        amplitude = targetAmplitude();
        bubble = 0f;
        if (delegate != null) {
            delegate.onDragStateChanged(false);
        }
    }

    private void cancelJump() {
        jumpSpring.cancel();
        jumpOffset = 0f;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        amplitudeSpring.cancel();
        handleSpring.cancel();
        bubbleSpring.cancel();
        cancelJump();
        if (dragging) {
            cancelDrag();
        }
        externalDragging = false;
        amplitude = targetAmplitude();
        handleWidth = targetHandleWidth();
        bubble = 0f;
        lastFrameTime = 0;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (getWidth() <= 0) {
            return;
        }
        final float cy = getCenterY();
        final float x0 = getLineStart();
        final float x1 = getLineEnd();
        if (loading) {
            drawLoading(canvas, x0, x1, cy);
            return;
        }
        final float p = dragging ? dragProgress : Utilities.clamp01(progress + jumpOffset);
        final float hx = lerp(x0, x1, p);
        drawWave(canvas, x0, x1, cy, p, hx);
        drawHandle(canvas, hx, cy);
        drawBubble(canvas, hx, p);
        if (needsFrames()) {
            postInvalidateOnAnimation();
        } else {
            lastFrameTime = 0;
        }
    }

    private boolean needsFrames() {
        return playing && amplitude > 0 && collapse < 1f && isShown() && getAlpha() > 0;
    }

    private void advancePhase() {
        final long now = AnimationUtils.currentAnimationTimeMillis();
        if (lastFrameTime != 0 && playing) {
            final float dt = Math.min(0.05f, (now - lastFrameTime) / 1000f);
            phase += dt * 2f * (float) Math.PI * phaseSpeedDp * Math.min(playbackSpeed, 2f) / wavelength;
            if (phase > 2f * Math.PI * 1000) {
                phase -= 2f * (float) Math.PI * 1000;
            }
        }
        lastFrameTime = now;
    }

    private void drawWave(Canvas canvas, float x0, float x1, float cy, float p, float hx) {
        advancePhase();
        final float lineWidth = getLineWidth();
        final float half = lerp(handleWidth / 2f + dpf2(HANDLE_GAP_DP), lineWidth / 2f, collapse);
        final float startIn = Math.min(x1, hx + half);
        linePaint.setStrokeWidth(lineWidth);
        if (startIn < x1) {
            final float bx = Math.min(x1, lerp(x0, x1, Math.max(bufferedProgress, p)));
            if (x1 - bx >= 0.5f) {
                linePaint.setColor(trackColor);
                canvas.drawLine(startIn, cy, x1, cy, linePaint);
            }
            if (bx > startIn) {
                linePaint.setColor(bufferColor);
                canvas.drawLine(startIn, cy, bx, cy, linePaint);
            }
        }
        final float endA = hx - half;
        if (endA > x0) {
            final float ramp = p < RAMP ? p / RAMP : p > 1f - RAMP ? (1f - p) / RAMP : 1f;
            final float a = amplitude * Utilities.clamp01(ramp) * (1f - collapse);
            linePaint.setColor(waveColor);
            if (a < 0.25f) {
                canvas.drawLine(x0, cy, endA, cy, linePaint);
            } else {
                final float k = 2f * (float) Math.PI / dpf2(wavelength);
                final float step = dpf2(WAVE_STEP_DP);
                wavePath.rewind();
                wavePath.moveTo(x0, cy + a * (float) Math.sin(x0 * k + phase));
                for (float x = x0 + step; x < endA; x += step) {
                    wavePath.lineTo(x, cy + a * (float) Math.sin(x * k + phase));
                }
                wavePath.lineTo(endA, cy + a * (float) Math.sin(endA * k + phase));
                canvas.drawPath(wavePath, linePaint);
            }
        }
    }

    private float getLineWidth() {
        return lerp(dpf2(lineWidthDp), collapsedLineWidth, collapse);
    }

    private void drawHandle(Canvas canvas, float hx, float cy) {
        final float lineWidth = getLineWidth();
        final float w = lerp(handleWidth, lineWidth, collapse);
        final float h = lerp(dpf2(handleHeightDp), lineWidth, collapse);
        fillPaint.setColor(waveColor);
        rect.set(hx - w / 2f, cy - h / 2f, hx + w / 2f, cy + h / 2f);
        canvas.drawRoundRect(rect, w / 2f, w / 2f, fillPaint);
    }

    private void drawBubble(Canvas canvas, float hx, float p) {
        if (bubble <= 0.01f || delegate == null) {
            return;
        }
        final CharSequence label = delegate.getTimeLabel(p);
        if (label == null) {
            return;
        }
        final String text = label.toString();
        final float textWidth = bubbleTextPaint.measureText(text);
        final float w = textWidth + dp(20);
        final float h = dpf2(BUBBLE_HEIGHT_DP);
        final float left = Math.max(-dp(4), Math.min(getWidth() - w + dp(4), hx - w / 2f));
        final float top = getCenterY() - dpf2(BUBBLE_OFFSET_DP) - h / 2f + dp(2);
        final float scale = 0.6f + 0.4f * Utilities.clamp01(bubble);
        canvas.save();
        canvas.scale(scale, scale, left + w / 2f, top + h);
        fillPaint.setColor(bubbleColor);
        fillPaint.setAlpha((int) (fillPaint.getAlpha() * Utilities.clamp01(bubble)));
        rect.set(left, top, left + w, top + h);
        canvas.drawRoundRect(rect, h / 2f, h / 2f, fillPaint);
        bubbleTextPaint.setColor(bubbleTextColor);
        bubbleTextPaint.setAlpha((int) (0xFF * Utilities.clamp01(bubble)));
        final Paint.FontMetrics fm = bubbleTextPaint.getFontMetrics();
        canvas.drawText(text, left + w / 2f, top + h / 2f - (fm.ascent + fm.descent) / 2f, bubbleTextPaint);
        canvas.restore();
    }

    private void drawLoading(Canvas canvas, float x0, float x1, float cy) {
        linePaint.setStrokeWidth(dpf2(lineWidthDp));
        linePaint.setColor(trackColor);
        canvas.drawLine(x0, cy, x1, cy, linePaint);
        if (loadingProgress > 0) {
            linePaint.setColor(waveColor);
            canvas.drawLine(x0, cy, Math.max(x0 + 0.5f, lerp(x0, x1, loadingProgress)), cy, linePaint);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(SeekBar.class.getName());
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_PERCENT, 0f, 100f, getProgress() * 100f));
        final CharSequence value = delegate != null ? delegate.getAccessibilityValue() : null;
        if (value != null) {
            info.setText(value);
        }
        if (isEnabled() && !loading) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (super.performAccessibilityAction(action, arguments)) {
            return true;
        }
        if (loading || delegate == null) {
            return false;
        }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            final float delta = action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? ACCESSIBILITY_STEP : -ACCESSIBILITY_STEP;
            progress = Utilities.clamp01(progress + delta);
            delegate.onSeek(progress);
            invalidate();
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
            return true;
        }
        return false;
    }
}
