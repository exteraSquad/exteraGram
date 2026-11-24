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
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.exteragram.messenger.icons.IconManager;
import com.exteragram.messenger.player.PlayerCovers;
import com.exteragram.messenger.utils.ui.material.Material3Tokens;

import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;

public class PlayerCoverView extends View {

    public interface Delegate {
        void onSwipe(int direction);

        void onCoverLoaded(MessageObject messageObject, ImageReceiver receiver);

        boolean canDrag();

        MessageObject getNeighbor(int direction);

        boolean hasCanvas(MessageObject messageObject);

        void onPageMoved();

        boolean canTap();

        void onTap();
    }

    private static final int SLOTS = 3;
    private static final int COVER_SIZE_DP = 360;
    private static final float GAP_DP = 24f;
    private static final float SIDE_SCALE = 0.86f;
    private static final float SIDE_ALPHA = 0.55f;
    private static final float PAUSED_SCALE = 0.94f;
    private static final float SWAP_SHIFT_DP = 28f;
    private static final float SWAP_SCALE = 0.94f;
    private static final float COMMIT_FRACTION = 0.3f;
    private static final float FLING_VELOCITY_DP = 600f;
    private static final float RUBBER_DP = 56f;
    private static final float ICON_FRACTION = 0.3f;
    private static final long MOTION_FADE_MS = 400;
    private static final long CANVAS_MORPH_MS = 250;
    private static final float SWIPE_TILT_DEGREES = 10f;
    private static final float SWIPE_TILT_VELOCITY_DP = 2400f;
    private static final float CAMERA_DISTANCE_FACTOR = 4f;
    private static final long SWIPE_TILT_IDLE_MS = 60;

    private final ImageReceiver[] receivers = new ImageReceiver[SLOTS];
    private final MessageObject[] messages = new MessageObject[SLOTS];
    private final boolean[] placeholders = new boolean[SLOTS];
    private final int[] radii = new int[SLOTS];
    private final float[] slotScales = new float[SLOTS];
    private final ImageReceiver[] motionReceivers = new ImageReceiver[SLOTS];
    private final String[] motionPaths = new String[SLOTS];
    private final long[] motionShownAt = new long[SLOTS];
    private final float[] canvasAmounts = new float[SLOTS];
    private final float[] canvasTargets = new float[SLOTS];
    private final RectF thumbRect = new RectF();
    private final Paint containerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF coverRect = new RectF();
    private final RectF drawRect = new RectF();
    private Drawable placeholderIcon;
    private int placeholderIconColor;
    private final SpringAnimation offsetSpring;
    private final SpringAnimation swapSpring;
    private final SpringAnimation pauseSpring;
    private final SpringAnimation tiltSpring;
    private final Camera camera = new Camera();
    private final Matrix tiltMatrix = new Matrix();
    private final Runnable settleTilt = () -> setSwipeTiltVelocity(0f);
    private final int touchSlop;
    private boolean dragAllowed;
    private boolean moved;

    private Delegate delegate;
    private int current;
    private int neighbor = -1;
    private int neighborDirection;
    private int outgoing = -1;
    private int outgoingDirection;
    private boolean crossfade;
    private float swap = 1f;
    private float swapShift = 1f;
    private float offset;
    private float offsetVelocity;
    private int awaitingDirection;
    private float pauseScale = 1f;
    private float swipeTilt;
    private float compact;
    private float radius;
    private float thumbRadius;
    private float thumbAlpha;
    private float canvasPageWidth;
    private float pagerClipRight = -1f;
    private float pager;
    private long canvasMorphAt;
    private float hideProgress;
    private int containerColor;
    private boolean playing = true;
    private boolean motionRunning = true;

    private boolean tracking;
    private boolean dragging;
    private float downX;
    private float downY;
    private float dragStartOffset;
    private VelocityTracker velocityTracker;

    public PlayerCoverView(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        for (int i = 0; i < SLOTS; i++) {
            final int index = i;
            receivers[i] = new ImageReceiver(this);
            receivers[i].setDelegate((imageReceiver, set, thumb, memCache) -> {
                if (set && (index == current || index == neighbor) && delegate != null && messages[index] != null) {
                    delegate.onCoverLoaded(messages[index], imageReceiver);
                }
            });
            motionReceivers[i] = new ImageReceiver(this);
            motionReceivers[i].setCrossfadeAlpha((byte) 2);
            slotScales[i] = 1f;
        }
        final Drawable icon = ContextCompat.getDrawable(context, R.drawable.player_cover_placeholder);
        placeholderIcon = icon != null ? icon.mutate() : null;
        IconManager.INSTANCE.requestDrawableForSize(R.drawable.player_cover_placeholder, dp(COVER_SIZE_DP * ICON_FRACTION), drawable -> {
            placeholderIcon = drawable.mutate();
            placeholderIcon.setColorFilter(new PorterDuffColorFilter(placeholderIconColor, PorterDuff.Mode.SRC_IN));
            invalidate();
        });
        offsetSpring = new SpringAnimation(new FloatValueHolder(0f));
        offsetSpring.setMinimumVisibleChange(0.5f);
        offsetSpring.setSpring(new SpringForce(0f).setDampingRatio(Material3Tokens.DEFAULT_SPATIAL_DAMPING).setStiffness(Material3Tokens.DEFAULT_SPATIAL_STIFFNESS));
        offsetSpring.addUpdateListener((animation, value, velocity) -> {
            final float target = offsetSpring.getSpring().getFinalPosition();
            if (offset != target && Math.signum(value - target) == -Math.signum(offset - target) && getPagerAmount() > 0f) {
                value = target;
                velocity = 0f;
                offsetSpring.skipToEnd();
            }
            offset = value;
            offsetVelocity = velocity;
            setSwipeTiltVelocity(velocity);
            invalidatePage();
        });
        offsetSpring.addEndListener((animation, canceled, value, velocity) -> {
            if (!canceled) {
                setSwipeTiltVelocity(0f);
                onOffsetSettled();
            }
        });
        swapSpring = Material3Tokens.createSpring(Material3Tokens.DEFAULT_SPATIAL_DAMPING, Material3Tokens.DEFAULT_SPATIAL_STIFFNESS, Material3Tokens.MIN_VISIBLE_CHANGE, value -> {
            swap = value;
            invalidate();
        });
        swapSpring.addEndListener((animation, canceled, value, velocity) -> {
            if (!canceled && crossfade) {
                releaseOutgoing();
            }
        });
        pauseSpring = Material3Tokens.createSpring(Material3Tokens.DEFAULT_SPATIAL_DAMPING, Material3Tokens.DEFAULT_SPATIAL_STIFFNESS, 1f / 2000f, value -> {
            pauseScale = value;
            invalidate();
        });
        tiltSpring = Material3Tokens.createSpring(0.45f, 260f, 1f / 50f, value -> {
            swipeTilt = value;
            invalidate();
        });
    }

    private void setSwipeTiltVelocity(float velocity) {
        if (getPagerAmount() > 0f) {
            tiltSpring.cancel();
            swipeTilt = 0f;
            return;
        }
        final float target = Utilities.clamp(velocity / dpf2(SWIPE_TILT_VELOCITY_DP), 1f, -1f) * SWIPE_TILT_DEGREES;
        if (tiltSpring.getSpring().getFinalPosition() != target || !tiltSpring.isRunning() && swipeTilt != target) {
            tiltSpring.animateToFinalPosition(target);
        }
    }

    private void animateOffset(float target, float velocity) {
        offsetSpring.cancel();
        offsetSpring.setStartValue(offset);
        offsetSpring.setStartVelocity(velocity);
        offsetVelocity = velocity;
        offsetSpring.getSpring().setFinalPosition(target);
        offsetSpring.start();
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public void setColors(int containerColor, int iconColor) {
        this.containerColor = containerColor;
        placeholderIconColor = iconColor;
        if (placeholderIcon != null) {
            placeholderIcon.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
        }
        invalidate();
    }

    public void setCoverRect(float left, float top, float size, float radius, float compact) {
        if (coverRect.left != left || coverRect.top != top || coverRect.width() != size || this.radius != radius || this.compact != compact) {
            coverRect.set(left, top, left + size, top + size);
            this.radius = radius;
            this.compact = compact;
            updateMotionPlayback();
            invalidate();
        }
    }

    public void setCanvasLayout(float thumbLeft, float thumbTop, float thumbSize, float thumbRadius, float thumbAlpha, float pageWidth) {
        if (thumbRect.left != thumbLeft || thumbRect.top != thumbTop || thumbRect.width() != thumbSize || this.thumbRadius != thumbRadius || this.thumbAlpha != thumbAlpha || canvasPageWidth != pageWidth) {
            thumbRect.set(thumbLeft, thumbTop, thumbLeft + thumbSize, thumbTop + thumbSize);
            this.thumbRadius = thumbRadius;
            this.thumbAlpha = thumbAlpha;
            canvasPageWidth = pageWidth;
            invalidate();
        }
    }

    public void setPagerClipRight(float right) {
        if (pagerClipRight != right) {
            pagerClipRight = right;
            invalidate();
        }
    }

    public void setCurrentCanvas(float amount) {
        canvasTargets[current] = amount;
        if (canvasAmounts[current] != amount) {
            canvasAmounts[current] = amount;
            updateMotionPlayback();
            invalidatePage();
        }
    }

    public float getCurrentCanvas() {
        return canvasAmounts[current];
    }

    public float getPageOffset() {
        return offset;
    }

    public float getPageWidth() {
        return getPage();
    }

    public float getSideCanvas(int side) {
        float amount = 0f;
        if (neighbor >= 0 && neighborDirection == side) {
            amount = canvasAmounts[neighbor];
        }
        if (outgoing >= 0 && !crossfade && outgoingDirection == -side) {
            amount = Math.max(amount, canvasAmounts[outgoing]);
        }
        return Utilities.clamp01(amount);
    }

    public float getRhythmOffset() {
        if (getPagerAmount() <= 0f) {
            return 0f;
        }
        if (canvasAmounts[current] < 1f || offset == 0f) {
            return offset;
        }
        final int side = offset > 0f ? -1 : 1;
        final boolean hasSide = neighbor >= 0 && neighborDirection == side || outgoing >= 0 && !crossfade && outgoingDirection == -side;
        return hasSide ? offset + side * getPage() : offset;
    }

    public void refreshCanvas() {
        if (delegate == null) {
            return;
        }
        boolean changed = false;
        for (int i = 0; i < SLOTS; i++) {
            if (i != current && messages[i] != null) {
                canvasTargets[i] = delegate.hasCanvas(messages[i]) ? 1f : 0f;
                changed |= canvasTargets[i] != canvasAmounts[i];
            }
        }
        if (changed) {
            invalidatePage();
        }
    }

    private void stepCanvasMorph() {
        final long now = SystemClock.elapsedRealtime();
        final float step = canvasMorphAt == 0 ? 0f : (now - canvasMorphAt) / (float) CANVAS_MORPH_MS;
        boolean morphing = false;
        for (int i = 0; i < SLOTS; i++) {
            if (i != current && canvasAmounts[i] != canvasTargets[i]) {
                final float target = canvasTargets[i];
                final float amount = canvasAmounts[i];
                canvasAmounts[i] = target > amount ? Math.min(target, amount + step) : Math.max(target, amount - step);
                morphing = true;
            }
        }
        canvasMorphAt = morphing ? now : 0;
        if (morphing) {
            invalidatePage();
        }
    }

    private void invalidatePage() {
        invalidate();
        if (delegate != null) {
            delegate.onPageMoved();
        }
    }

    public RectF getCoverRect() {
        return coverRect;
    }

    public float getCoverRadius() {
        return radius;
    }

    public float getHideProgress() {
        return hideProgress;
    }

    public float getCompactProgress() {
        return compact;
    }

    public void setHideProgress(float hideProgress) {
        if (this.hideProgress != hideProgress) {
            this.hideProgress = hideProgress;
            updateMotionPlayback();
            invalidate();
        }
    }

    public void setPlaying(boolean playing, boolean animated) {
        this.playing = playing;
        updateMotionPlayback();
        final float target = playing ? 1f : PAUSED_SCALE;
        if (animated && isAttachedToWindow()) {
            if (pauseSpring.getSpring().getFinalPosition() != target || !pauseSpring.isRunning() && pauseScale != target) {
                Material3Tokens.animate(pauseSpring, pauseScale, target);
            }
        } else {
            pauseSpring.cancel();
            pauseScale = target;
            invalidate();
        }
    }

    public void setMotionCover(MessageObject messageObject, String path) {
        final MessageObject shown = messages[current];
        if (shown == null || messageObject == null || shown.getId() != messageObject.getId() || shown.getDialogId() != messageObject.getDialogId() || TextUtils.equals(motionPaths[current], path)) {
            return;
        }
        clearMotion(current);
        if (path != null) {
            motionPaths[current] = path;
            motionReceivers[current].setAllowStartAnimation(motionRunning);
            motionReceivers[current].setImage(ImageLocation.getForPath(path), COVER_SIZE_DP + "_" + COVER_SIZE_DP, null, null, 0, null, shown, 1);
        }
        invalidate();
    }

    private void clearMotion(int slot) {
        if (motionPaths[slot] != null) {
            motionPaths[slot] = null;
            motionShownAt[slot] = 0;
            motionReceivers[slot].clearImage();
        }
    }

    private float getMotionAlpha(int slot) {
        if (motionPaths[slot] == null) {
            return 0f;
        }
        if (motionShownAt[slot] == 0) {
            final AnimatedFileDrawable animation = motionReceivers[slot].getAnimation();
            if (animation == null || !animation.isRunning() || !animation.hasBitmap()) {
                return 0f;
            }
            motionShownAt[slot] = SystemClock.elapsedRealtime();
        }
        final float progress = Utilities.clamp01((SystemClock.elapsedRealtime() - motionShownAt[slot]) / (float) MOTION_FADE_MS);
        if (progress < 1f) {
            invalidate();
        }
        return CubicBezierInterpolator.EASE_OUT.getInterpolation(progress);
    }

    private void updateMotionPlayback() {
        final boolean run = playing && compact < 1f && canvasAmounts[current] < 1f && hideProgress < 1f;
        if (run == motionRunning) {
            return;
        }
        motionRunning = run;
        for (int i = 0; i < SLOTS; i++) {
            if (motionPaths[i] == null) {
                continue;
            }
            motionReceivers[i].setAllowStartAnimation(run);
            if (run) {
                motionReceivers[i].startAnimation();
            } else {
                motionReceivers[i].stopAnimation();
            }
        }
    }

    public boolean hasBitmap() {
        return !placeholders[current] && receivers[current].hasBitmapImage();
    }

    public ImageReceiver getImageReceiver() {
        return receivers[current];
    }

    public MessageObject getMessageObject() {
        return messages[current];
    }

    private float getPagerAmount() {
        float amount = canvasAmounts[current];
        if (neighbor >= 0) {
            amount = Math.max(amount, canvasAmounts[neighbor]);
        }
        if (outgoing >= 0 && !crossfade) {
            amount = Math.max(amount, canvasAmounts[outgoing]);
        }
        return Utilities.clamp01(amount);
    }

    private float getPage() {
        float page = coverRect.width() + dpf2(GAP_DP);
        if (pagerClipRight >= 0f && compact < 0.5f) {
            page = Math.max(page, Math.max(coverRect.right, pagerClipRight - coverRect.left));
        }
        return lerp(page, canvasPageWidth, getPagerAmount());
    }

    private int freeSlot() {
        for (int i = 0; i < SLOTS; i++) {
            if (i != current && i != neighbor && i != outgoing) {
                return i;
            }
        }
        final int slot = outgoing;
        outgoing = -1;
        return slot;
    }

    private void load(int slot, MessageObject messageObject) {
        messages[slot] = messageObject;
        slotScales[slot] = 1f;
        canvasAmounts[slot] = messageObject != null && delegate != null && delegate.hasCanvas(messageObject) ? 1f : 0f;
        canvasTargets[slot] = canvasAmounts[slot];
        clearMotion(slot);
        setSlotRadius(slot, radius);
        if (messageObject == null) {
            receivers[slot].clearImage();
            placeholders[slot] = true;
        } else {
            placeholders[slot] = !PlayerCovers.setCover(receivers[slot], messageObject, COVER_SIZE_DP, true);
        }
    }

    private void setSlotRadius(int slot, float value) {
        final int r = (int) Math.max(1, value);
        if (radii[slot] != r) {
            radii[slot] = r;
            receivers[slot].setRoundRadius(r);
            motionReceivers[slot].setRoundRadius(r);
        }
    }

    private void releaseSlot(int slot) {
        if (slot >= 0 && slot != current) {
            receivers[slot].clearImage();
            clearMotion(slot);
            messages[slot] = null;
            placeholders[slot] = false;
            slotScales[slot] = 1f;
            canvasAmounts[slot] = 0f;
            canvasTargets[slot] = 0f;
        }
    }

    private void handOverPauseScale(int previous) {
        slotScales[previous] = pauseScale;
        pauseSpring.cancel();
        pauseScale = slotScales[current];
        final float target = playing ? 1f : PAUSED_SCALE;
        if (pauseScale != target) {
            Material3Tokens.animate(pauseSpring, pauseScale, target);
        }
    }

    private void releaseOutgoing() {
        releaseSlot(outgoing);
        outgoing = -1;
        crossfade = false;
        swap = 1f;
        invalidate();
    }

    private void releaseNeighbor() {
        releaseSlot(neighbor);
        neighbor = -1;
    }

    private void notifyCurrentLoaded() {
        if (delegate != null && messages[current] != null && !placeholders[current] && receivers[current].hasBitmapImage()) {
            delegate.onCoverLoaded(messages[current], receivers[current]);
        }
    }

    public void setCover(MessageObject messageObject, boolean animated, int direction) {
        if (awaitingDirection != 0 && neighbor >= 0 && neighborDirection == awaitingDirection) {
            final int d = awaitingDirection;
            awaitingDirection = 0;
            swapSpring.cancel();
            releaseSlot(outgoing);
            outgoing = current;
            outgoingDirection = d;
            crossfade = false;
            swap = 1f;
            current = neighbor;
            neighbor = -1;
            if (messages[current] != messageObject) {
                load(current, messageObject);
            }
            handOverPauseScale(outgoing);
            offset += d * getPage();
            animateOffset(0f, offsetVelocity);
            notifyCurrentLoaded();
            invalidatePage();
            return;
        }
        awaitingDirection = 0;
        abortDrag();
        releaseNeighbor();
        final boolean attached = animated && isAttachedToWindow() && !coverRect.isEmpty();
        if (attached && compact < 0.5f && hideProgress < 0.5f) {
            swapSpring.cancel();
            releaseSlot(outgoing);
            outgoing = current;
            outgoingDirection = direction;
            crossfade = false;
            swap = 1f;
            current = freeSlot();
            load(current, messageObject);
            handOverPauseScale(outgoing);
            offset += direction * getPage();
            animateOffset(0f, 0f);
        } else if (attached) {
            offsetSpring.cancel();
            offset = 0f;
            releaseSlot(outgoing);
            outgoing = current;
            outgoingDirection = direction;
            crossfade = true;
            swapShift = 1f - compact;
            current = freeSlot();
            load(current, messageObject);
            handOverPauseScale(outgoing);
            swap = 0f;
            Material3Tokens.animate(swapSpring, 0f, 1f);
        } else {
            offsetSpring.cancel();
            swapSpring.cancel();
            offset = 0f;
            swap = 1f;
            releaseSlot(outgoing);
            outgoing = -1;
            crossfade = false;
            load(current, messageObject);
        }
        notifyCurrentLoaded();
        invalidatePage();
    }

    public void refreshCover() {
        final MessageObject messageObject = messages[current];
        if (messageObject != null) {
            placeholders[current] = !PlayerCovers.setCover(receivers[current], messageObject, COVER_SIZE_DP, true);
            invalidate();
        }
    }

    public void cancelPendingSwipe() {
        if (awaitingDirection != 0) {
            awaitingDirection = 0;
            animateOffset(0f, offsetVelocity);
        }
    }

    private void onOffsetSettled() {
        if (awaitingDirection == 0 && Math.abs(offset) < 1f) {
            offset = 0f;
            if (!crossfade) {
                releaseSlot(outgoing);
                outgoing = -1;
            }
            if (!tracking) {
                releaseNeighbor();
            }
            invalidatePage();
        }
    }

    private void ensureNeighbor(int direction) {
        if (neighbor >= 0 && neighborDirection == direction) {
            return;
        }
        if (outgoing >= 0 && !crossfade && outgoingDirection == -direction) {
            releaseNeighbor();
            neighbor = outgoing;
            neighborDirection = direction;
            outgoing = -1;
            return;
        }
        if (neighbor < 0) {
            neighbor = freeSlot();
        }
        neighborDirection = direction;
        load(neighbor, delegate != null ? delegate.getNeighbor(direction) : null);
    }

    private float rubber(float value) {
        final float limit = dpf2(RUBBER_DP);
        final float abs = Math.abs(value);
        return Math.signum(value) * limit * (1f - 1f / (abs / limit * 0.55f + 1f));
    }

    private void setDragOffset(float value) {
        if (value != 0) {
            final int direction = value < 0 ? 1 : -1;
            if (delegate != null && delegate.canDrag()) {
                ensureNeighbor(direction);
            } else {
                value = rubber(value);
            }
        }
        offset = value;
        invalidatePage();
    }

    private void abortDrag() {
        if (tracking || dragging) {
            tracking = false;
            dragging = false;
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            recycleVelocityTracker();
        }
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private void finishDrag(float velocity, boolean cancel) {
        final float page = getPage();
        final int direction = offset < 0 ? 1 : -1;
        final boolean fling = Math.abs(velocity) > dpf2(FLING_VELOCITY_DP) && Math.signum(velocity) == -direction;
        final boolean commit = !cancel && Math.abs(offset) > 0.5f && delegate != null && delegate.canDrag() && (Math.abs(offset) > page * COMMIT_FRACTION || fling);
        if (commit) {
            ensureNeighbor(direction);
            awaitingDirection = direction;
            animateOffset(-direction * page, velocity);
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            delegate.onSwipe(direction);
        } else {
            animateOffset(0f, cancel ? 0f : velocity);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        for (int i = 0; i < SLOTS; i++) {
            receivers[i].onAttachedToWindow();
            motionReceivers[i].onAttachedToWindow();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        abortDrag();
        offsetSpring.cancel();
        swapSpring.cancel();
        pauseSpring.cancel();
        tiltSpring.cancel();
        removeCallbacks(settleTilt);
        swipeTilt = 0f;
        awaitingDirection = 0;
        offset = 0f;
        swap = 1f;
        releaseNeighbor();
        releaseSlot(outgoing);
        outgoing = -1;
        crossfade = false;
        for (int i = 0; i < SLOTS; i++) {
            receivers[i].onDetachedFromWindow();
            motionReceivers[i].onDetachedFromWindow();
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (coverRect.isEmpty() || hideProgress >= 1f) {
            return;
        }
        stepCanvasMorph();
        final float alpha = 1f - hideProgress;
        final float page = getPage();
        pager = getPagerAmount();
        final boolean clip = pagerClipRight >= 0f && (outgoing >= 0 && !crossfade || neighbor >= 0 || offset != 0f);
        if (clip) {
            canvas.save();
            canvas.clipRect(0f, 0f, pagerClipRight, getHeight());
        }
        if (outgoing >= 0) {
            final float outgoingScale = lerp(slotScales[outgoing], 1f, compact);
            if (crossfade) {
                drawLayer(canvas, outgoing, -outgoingDirection * dpf2(SWAP_SHIFT_DP) * swapShift * swap, outgoingScale, alpha * (1f - Utilities.clamp01(swap)));
            } else {
                drawSide(canvas, outgoing, offset - outgoingDirection * page, page, alpha, outgoingScale);
            }
        }
        if (neighbor >= 0) {
            drawSide(canvas, neighbor, offset + neighborDirection * page, page, alpha, lerp(slotScales[neighbor], 1f, compact));
        }
        final float playScale = lerp(pauseScale, 1f, compact);
        if (outgoing >= 0 && crossfade) {
            final float dx = outgoingDirection * dpf2(SWAP_SHIFT_DP) * swapShift * (1f - swap);
            drawLayer(canvas, current, dx, playScale * lerp(SWAP_SCALE, 1f, Utilities.clamp01(swap)), alpha * Utilities.clamp01(swap));
        } else {
            drawSide(canvas, current, offset, page, alpha, playScale);
        }
        if (clip) {
            canvas.restore();
        }
    }

    private void drawSide(Canvas canvas, int index, float x, float page, float alpha, float extraScale) {
        final float t = page > 0 ? Utilities.clamp01(Math.abs(x) / page) * (1f - pager) : 0f;
        drawLayer(canvas, index, x, lerp(1f, SIDE_SCALE, t) * extraScale, alpha * lerp(1f, SIDE_ALPHA, t));
    }

    private void drawLayer(Canvas canvas, int index, float dx, float scale, float alpha) {
        if (alpha <= 0f || index < 0) {
            return;
        }
        drawRect.set(coverRect);
        drawRect.offset(dx, 0);
        if (scale != 1f) {
            final float inset = drawRect.width() * (1f - scale) / 2f;
            drawRect.inset(inset, inset);
        }
        float r = Math.min(radius * scale, drawRect.width() / 2f);
        final float canvasAmount = Utilities.clamp01(canvasAmounts[index]);
        if (canvasAmount > 0f) {
            final float page = getPage();
            final float visible = page > 0 ? 1f - Utilities.clamp01(Math.abs(dx) / page) : 1f;
            alpha *= lerp(1f, visible * thumbAlpha, canvasAmount);
            lerp(drawRect, thumbRect, canvasAmount, drawRect);
            r = lerp(r, thumbRadius, canvasAmount);
            if (alpha <= 0f) {
                return;
            }
        }
        if (drawRect.right < 0 || drawRect.left > getWidth()) {
            return;
        }
        final float rotationY = swipeTilt * (1f - compact) * (1f - pager) * (1f - canvasAmount);
        final boolean tilted = rotationY != 0f;
        if (tilted) {
            camera.save();
            camera.setLocation(0f, 0f, -Math.max(8f, drawRect.width() * CAMERA_DISTANCE_FACTOR / 72f));
            camera.rotateY(rotationY);
            camera.getMatrix(tiltMatrix);
            camera.restore();
            tiltMatrix.preTranslate(-drawRect.centerX(), -drawRect.centerY());
            tiltMatrix.postTranslate(drawRect.centerX(), drawRect.centerY());
            canvas.save();
            canvas.concat(tiltMatrix);
        }
        final ImageReceiver receiver = receivers[index];
        final boolean hasImage = !placeholders[index] && receiver.hasBitmapImage();
        if (!hasImage || receiver.getCurrentAlpha() < 1f) {
            containerPaint.setColor(containerColor);
            containerPaint.setAlpha((int) (Color.alpha(containerColor) * alpha));
            canvas.drawRoundRect(drawRect, r, r, containerPaint);
            if (placeholderIcon != null && (placeholders[index] || !hasImage)) {
                final int size = (int) (drawRect.width() * ICON_FRACTION);
                final int left = (int) (drawRect.centerX() - size / 2f);
                final int top = (int) (drawRect.centerY() - size / 2f);
                placeholderIcon.setBounds(left, top, left + size, top + size);
                placeholderIcon.setAlpha((int) (0xFF * alpha));
                placeholderIcon.draw(canvas);
            }
        }
        if (!placeholders[index]) {
            setSlotRadius(index, r);
            receiver.setImageCoords(drawRect.left, drawRect.top, drawRect.width(), drawRect.height());
            receiver.setAlpha(alpha);
            receiver.draw(canvas);
        }
        final float motionAlpha = getMotionAlpha(index);
        if (motionAlpha > 0f) {
            final ImageReceiver motionReceiver = motionReceivers[index];
            setSlotRadius(index, r);
            motionReceiver.setImageCoords(drawRect.left, drawRect.top, drawRect.width(), drawRect.height());
            motionReceiver.setAlpha(alpha * motionAlpha);
            motionReceiver.draw(canvas);
        }
        if (tilted) {
            canvas.restore();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && !coverRect.contains(event.getX(), event.getY())) {
            return false;
        }
        return super.dispatchTouchEvent(event);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (hideProgress > 0.5f || compact > 0.5f || awaitingDirection != 0 || delegate == null) {
                    return false;
                }
                dragAllowed = delegate.canDrag();
                if (!dragAllowed && !delegate.canTap()) {
                    return false;
                }
                tracking = true;
                dragging = false;
                moved = false;
                downX = event.getX();
                downY = event.getY();
                recycleVelocityTracker();
                velocityTracker = VelocityTracker.obtain();
                velocityTracker.addMovement(event);
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (!tracking) {
                    return false;
                }
                if (velocityTracker != null) {
                    velocityTracker.addMovement(event);
                }
                final float dx = event.getX() - downX;
                final float dy = event.getY() - downY;
                if (!dragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                    moved = true;
                }
                if (!dragging && dragAllowed && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    dragging = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    offsetSpring.cancel();
                    dragStartOffset = offset;
                    downX = event.getX();
                }
                if (dragging) {
                    setDragOffset(dragStartOffset + event.getX() - downX);
                    if (velocityTracker != null) {
                        velocityTracker.computeCurrentVelocity(1000);
                        setSwipeTiltVelocity(velocityTracker.getXVelocity());
                    }
                    removeCallbacks(settleTilt);
                    postDelayed(settleTilt, SWIPE_TILT_IDLE_MS);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (!tracking) {
                    return false;
                }
                tracking = false;
                removeCallbacks(settleTilt);
                if (dragging) {
                    dragging = false;
                    getParent().requestDisallowInterceptTouchEvent(false);
                    float velocity = 0f;
                    if (velocityTracker != null) {
                        velocityTracker.addMovement(event);
                        velocityTracker.computeCurrentVelocity(1000);
                        velocity = velocityTracker.getXVelocity();
                    }
                    finishDrag(velocity, event.getActionMasked() == MotionEvent.ACTION_CANCEL);
                } else if (!moved && event.getActionMasked() == MotionEvent.ACTION_UP && delegate != null && delegate.canTap()) {
                    delegate.onTap();
                }
                moved = false;
                recycleVelocityTracker();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

}
