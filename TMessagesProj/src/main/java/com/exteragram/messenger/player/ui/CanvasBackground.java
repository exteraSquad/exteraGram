/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player.ui;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.player.motion.CanvasRepository;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.Arrays;

public class CanvasBackground {

    public static final int PREVIOUS = 0;
    public static final int CURRENT = 1;
    public static final int NEXT = 2;

    private static final int POOL = 4;
    private static final int[] CLAIM_ORDER = {CURRENT, NEXT, PREVIOUS};
    private static final float[] SCRIM_STOPS = {0f, 0.2f, 0.45f, 0.62f, 1f};
    private static final float[] SCRIM_ALPHA = {0.6f, 0.2f, 0.3f, 0.72f, 0.92f};
    private static final int SCRIM_SHIFTED_FROM = 2;
    private static final long FADE_IN_MS = 300;

    private static final class Slot {
        final ImageReceiver receiver;
        final ImageReceiver thumbReceiver;
        String path;
        long shownAt;
        boolean videoShown;

        Slot(View parent) {
            receiver = new ImageReceiver(parent);
            receiver.setAllowStartAnimation(true);
            receiver.setCrossfadeAlpha((byte) 0);
            thumbReceiver = new ImageReceiver(parent);
            thumbReceiver.setCrossfadeAlpha((byte) 0);
        }

        void load(String path, MessageObject parentObject) {
            this.path = path;
            shownAt = 0;
            videoShown = false;
            if (path == null) {
                receiver.clearImage();
                thumbReceiver.clearImage();
                return;
            }
            final float density = AndroidUtilities.density;
            final String filter = (int) (AndroidUtilities.displaySize.x / density) + "_" + (int) (AndroidUtilities.displaySize.y / density);
            receiver.setImage(ImageLocation.getForPath(path), filter, null, null, 0, null, parentObject, 1);
            thumbReceiver.setImage(ImageLocation.getForPath(CanvasRepository.getThumbPath(path)), filter, null, null, 0, null, parentObject, 1);
        }

        boolean checkVideo() {
            if (!videoShown) {
                final AnimatedFileDrawable animation = receiver.getAnimation();
                if (animation == null || !animation.isRunning() || !animation.hasBitmap()) {
                    return false;
                }
                videoShown = true;
                thumbReceiver.clearImage();
            }
            return true;
        }

        boolean hasFrame() {
            if (path == null) {
                return false;
            }
            if (shownAt == 0) {
                if (!checkVideo() && !thumbReceiver.hasBitmapImage()) {
                    return false;
                }
                shownAt = SystemClock.elapsedRealtime();
            }
            return true;
        }
    }

    private final Slot[] pool = new Slot[POOL];
    private final Slot[] slots = new Slot[3];
    private final Slot[] lastSlots = new Slot[3];
    private final boolean[] hadFrames = new boolean[3];
    private final Runnable onFrame;
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pageRect = new RectF();
    private Slot outgoing;
    private int outgoingDirection;
    private int scrimColor;
    private float scrimTop;
    private float scrimBottom;
    private float scrimShift;
    private float shaderShift;
    private int shaderColor;

    public CanvasBackground(View parent, Runnable onFrame) {
        this.onFrame = onFrame;
        for (int i = 0; i < pool.length; i++) {
            pool[i] = new Slot(parent);
        }
        System.arraycopy(pool, 0, slots, 0, slots.length);
    }

    public void setPaths(String[] paths, MessageObject[] parentObjects, int direction) {
        final Slot previous = direction == 0 ? outgoing : slots[CURRENT].hasFrame() ? slots[CURRENT] : null;
        System.arraycopy(slots, 0, lastSlots, 0, slots.length);
        Arrays.fill(slots, null);
        for (int role : CLAIM_ORDER) {
            if (paths[role] == null) {
                continue;
            }
            if (!isRole(lastSlots[role]) && TextUtils.equals(lastSlots[role].path, paths[role])) {
                slots[role] = lastSlots[role];
                continue;
            }
            for (Slot slot : pool) {
                if (!isRole(slot) && TextUtils.equals(slot.path, paths[role])) {
                    slots[role] = slot;
                    break;
                }
            }
        }
        for (int role : CLAIM_ORDER) {
            if (slots[role] != null) {
                continue;
            }
            final Slot last = lastSlots[role];
            if (!isRole(last) && last != previous) {
                slots[role] = last;
            } else {
                for (Slot slot : pool) {
                    if (!isRole(slot) && slot != previous) {
                        slots[role] = slot;
                        break;
                    }
                }
            }
            if (!TextUtils.equals(slots[role].path, paths[role])) {
                slots[role].load(paths[role], parentObjects[role]);
            }
        }
        if (direction != 0) {
            outgoing = previous != slots[CURRENT] ? previous : null;
            outgoingDirection = direction;
        }
        for (Slot slot : pool) {
            if (slot.path != null && slot != outgoing && !isRole(slot)) {
                slot.load(null, null);
            }
        }
        for (int role = 0; role < slots.length; role++) {
            hadFrames[role] = slots[role].hasFrame();
        }
    }

    private boolean isRole(Slot slot) {
        for (Slot role : slots) {
            if (role == slot) {
                return true;
            }
        }
        return false;
    }

    public void setScrimShift(float shift) {
        scrimShift = shift;
    }

    public boolean hasFrame(int role) {
        return slots[role].hasFrame();
    }

    public void setScrimColor(int color) {
        scrimColor = color;
    }

    public void onAttachedToWindow() {
        for (Slot slot : pool) {
            slot.receiver.onAttachedToWindow();
            slot.thumbReceiver.onAttachedToWindow();
        }
    }

    public void onDetachedFromWindow() {
        for (Slot slot : pool) {
            slot.receiver.onDetachedFromWindow();
            slot.thumbReceiver.onDetachedFromWindow();
        }
    }

    public boolean draw(Canvas canvas, RectF rect, float radius, float alpha, float currentAlpha, float offset, float pageWidth, float previousAlpha, float nextAlpha) {
        boolean arrived = false;
        for (int role = 0; role < slots.length; role++) {
            final boolean hasFrame = slots[role].hasFrame();
            arrived |= hasFrame && !hadFrames[role];
            hadFrames[role] = hasFrame;
        }
        if (arrived) {
            AndroidUtilities.runOnUIThread(onFrame);
        }
        if (outgoing != null && offset == 0f) {
            if (!isRole(outgoing)) {
                outgoing.load(null, null);
            }
            outgoing = null;
        }
        if (rect.isEmpty() || alpha <= 0f) {
            return false;
        }
        if (scrimTop != rect.top || scrimBottom != rect.bottom || shaderColor != scrimColor || shaderShift != scrimShift) {
            scrimTop = rect.top;
            scrimBottom = rect.bottom;
            shaderShift = scrimShift;
            final float[] stops = SCRIM_STOPS.clone();
            final float shift = rect.height() > 0 ? scrimShift / rect.height() : 0f;
            for (int i = stops.length - 2; i >= SCRIM_SHIFTED_FROM; i--) {
                stops[i] = Math.min(stops[i + 1] - 0.01f, stops[i] + shift);
            }
            shaderColor = scrimColor;
            final int[] colors = new int[SCRIM_ALPHA.length];
            for (int i = 0; i < colors.length; i++) {
                colors[i] = ColorUtils.setAlphaComponent(scrimColor, (int) (0xFF * SCRIM_ALPHA[i]));
            }
            scrimPaint.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, colors, stops, Shader.TileMode.CLAMP));
        }
        boolean animating = drawPage(canvas, slots[CURRENT], rect, radius, alpha * Utilities.clamp01(currentAlpha), offset);
        final int side = offset > 0f ? -1 : offset < 0f ? 1 : 0;
        if (side != 0) {
            final Slot slot = outgoing != null && outgoingDirection == -side ? outgoing : slots[side < 0 ? PREVIOUS : NEXT];
            animating |= drawPage(canvas, slot, rect, radius, alpha * Utilities.clamp01(side < 0 ? previousAlpha : nextAlpha), offset + side * pageWidth);
        }
        return animating;
    }

    private static void drawReceiver(Canvas canvas, ImageReceiver receiver, RectF rect, int radius, float offsetX, float alpha) {
        receiver.setRoundRadius(radius, radius, 0, 0);
        receiver.setImageCoords(rect.left + offsetX, rect.top, rect.width(), rect.height());
        receiver.setAlpha(alpha);
        receiver.draw(canvas);
    }

    private boolean drawPage(Canvas canvas, Slot slot, RectF rect, float radius, float alpha, float offsetX) {
        if (alpha <= 0f || Math.abs(offsetX) >= rect.width() || !slot.hasFrame()) {
            return false;
        }
        final float fade = Utilities.clamp01((SystemClock.elapsedRealtime() - slot.shownAt) / (float) FADE_IN_MS);
        final float pageAlpha = alpha * CubicBezierInterpolator.EASE_OUT.getInterpolation(fade);
        pageRect.set(rect);
        pageRect.offset(offsetX, 0);
        pageRect.intersect(rect);
        canvas.save();
        canvas.clipRect(pageRect);
        final int r = (int) radius;
        if (!slot.checkVideo()) {
            drawReceiver(canvas, slot.thumbReceiver, rect, r, offsetX, pageAlpha);
        }
        drawReceiver(canvas, slot.receiver, rect, r, offsetX, pageAlpha);
        scrimPaint.setAlpha((int) (0xFF * pageAlpha));
        canvas.drawRoundRect(rect.left + offsetX, rect.top, rect.right + offsetX, rect.bottom, radius, radius, scrimPaint);
        canvas.restore();
        return fade < 1f;
    }
}
