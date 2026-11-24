/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.chats;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.utils.ui.GlyphBounds;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaDataController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.AnimatedEmojiDrawable;

import java.util.ArrayList;
import java.util.List;

public class SwipeActionsHelper {

    public static final int TRIGGER_DP = 50;

    private static final float VISIBLE_DP = 20f;
    private static final float STEP = 36f;
    private static final float FIRST_STEP = 44f;
    private static final float BUBBLE = 16f;
    private static final float ICON_WIDTH = 15f;
    private static final float ICON_HEIGHT = 16.5f;
    private static final float REACTION_SIZE = 20f;
    private static final float SPRING = 2000f;

    private final View parent;
    private final Theme.ResourcesProvider resourcesProvider;

    private final List<SwipeAction> actions = new ArrayList<>();
    private final SparseArray<Drawable> icons = new SparseArray<>();
    private final SparseArray<Drawable> spareIcons = new SparseArray<>();
    private final SparseArray<Float> iconFits = new SparseArray<>();

    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlineDarkenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private ColorFilter iconFilter;
    private int iconFilterColor;

    private Paint framePaint;
    private boolean frameGradient;
    private boolean frameDark;

    private Drawable reactionDrawable;
    private AnimatedEmojiDrawable animatedReaction;
    private long animatedReactionId;
    private ImageReceiver reactionImage;
    private String reactionEmojicon;

    private final AnimatedFloat liveAlpha = new AnimatedFloat(this::invalidate, 0, 280, CubicBezierInterpolator.EASE_OUT);

    private final FloatValueHolder position = new FloatValueHolder(0);
    private final SpringAnimation positionSpring = new SpringAnimation(position)
            .setSpring(new SpringForce(0)
                    .setStiffness(430f)
                    .setDampingRatio(.55f))
            .addUpdateListener((animation, value, velocity) -> invalidate());

    private final FloatValueHolder visibility = new FloatValueHolder(0);
    private final SpringAnimation visibilitySpring = new SpringAnimation(visibility)
            .setMinValue(0f)
            .setMaxValue(SPRING)
            .setSpring(new SpringForce(0)
                    .setStiffness(SpringForce.STIFFNESS_MEDIUM)
                    .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY))
            .addUpdateListener((animation, value, velocity) -> invalidate());

    private final FloatValueHolder fill = new FloatValueHolder(0);
    private final SpringAnimation fillSpring = new SpringAnimation(fill)
            .setMinValue(0f)
            .setSpring(new SpringForce(0)
                    .setStiffness(400f)
                    .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY))
            .addUpdateListener((animation, value, velocity) -> invalidate());

    private final FloatValueHolder ring = new FloatValueHolder(0);
    private final SpringAnimation ringSpring = new SpringAnimation(ring)
            .setMinValue(0f)
            .setSpring(new SpringForce(0)
                    .setStiffness(200f)
                    .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY))
            .addUpdateListener((animation, value, velocity) -> invalidate());

    private boolean beyondMax;
    private boolean looped;
    private boolean reversed;
    private int selected;
    private int slot;
    private boolean armed;
    private boolean stepped;
    private boolean scrubbing;
    private float anchorY;
    private float lastX;
    private float lastY;
    private float driftX;
    private float driftY;
    private int direction;

    public SwipeActionsHelper(View parent, Theme.ResourcesProvider resourcesProvider) {
        this.parent = parent;
        this.resourcesProvider = resourcesProvider;
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeCap(Paint.Cap.ROUND);
        outlinePaint.setStrokeWidth(dp(2));
        outlineDarkenPaint.setStyle(Paint.Style.STROKE);
        outlineDarkenPaint.setStrokeCap(Paint.Cap.ROUND);
        outlineDarkenPaint.setStrokeWidth(dp(2));
    }

    private void invalidate() {
        parent.invalidate();
    }

    public void setReaction(int account, String emojicon, long documentId) {
        if (documentId != 0) {
            if (animatedReactionId != documentId) {
                detach();
                animatedReaction = AnimatedEmojiDrawable.make(account, AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, documentId);
                animatedReaction.addView(parent);
                animatedReactionId = documentId;
            }
            reactionDrawable = animatedReaction;
            return;
        }
        final TLRPC.TL_availableReaction available = emojicon != null
                ? MediaDataController.getInstance(account).getReactionsMap().get(emojicon)
                : null;
        if (available != null && available.center_icon != null) {
            if (reactionImage == null || !TextUtils.equals(reactionEmojicon, emojicon)) {
                detach();
                reactionImage = new ImageReceiver(parent);
                reactionImage.ignoreNotifications = true;
                reactionImage.onAttachedToWindow();
                reactionImage.setImage(ImageLocation.getForDocument(available.center_icon), "40_40_lastreactframe",
                        DocumentObject.getSvgThumb(available.static_icon, Theme.key_windowBackgroundGray, 1f), "webp", available, 1);
                reactionEmojicon = emojicon;
            }
            return;
        }
        detach();
        reactionDrawable = emojicon != null ? Emoji.getEmojiDrawable(emojicon) : null;
    }

    public void detach() {
        if (animatedReaction != null) {
            animatedReaction.removeView(parent);
            animatedReaction = null;
            animatedReactionId = 0;
        }
        if (reactionImage != null) {
            reactionImage.onDetachedFromWindow();
            reactionImage = null;
            reactionEmojicon = null;
        }
        reactionDrawable = null;
    }

    public void start(List<SwipeAction> available) {
        actions.clear();
        actions.addAll(available);
        looped = ExteraConfig.getSwipeActionsLoop();
        reversed = ExteraConfig.getSwipeActionsReversed();
        selected = 0;
        slot = 0;
        armed = false;
        stepped = false;
        scrubbing = false;
        liveAlpha.set(0, true);
        anchorY = 0;
        driftX = 0;
        driftY = 0;
        direction = 0;
        positionSpring.cancel();
        positionSpring.getSpring().setFinalPosition(0);
        position.setValue(0);
        visibilitySpring.cancel();
        visibilitySpring.getSpring().setFinalPosition(0);
        visibility.setValue(0);
        fillSpring.cancel();
        fillSpring.getSpring().setFinalPosition(0);
        fill.setValue(0);
        ringSpring.cancel();
        ringSpring.getSpring().setFinalPosition(0);
        ring.setValue(0);
        beyondMax = false;
    }

    public int size() {
        return actions.size();
    }

    @Nullable
    public SwipeAction getSelected() {
        return selected >= 0 && selected < actions.size() ? actions.get(selected) : null;
    }

    public void update(float offsetX, float x, float y) {
        if (actions.size() < 2) {
            return;
        }
        if (Math.abs(offsetX) < dp(TRIGGER_DP)) {
            armed = false;
            scrubbing = false;
            return;
        }
        if (!armed) {
            armed = true;
            lastX = x;
            lastY = y;
            driftX = 0;
            driftY = 0;
            return;
        }
        final float movedX = Math.abs(x - lastX);
        final float movedY = y - lastY;
        lastX = x;
        lastY = y;
        if (!scrubbing) {
            driftX = driftX * .7f + movedX * .3f;
            driftY = driftY * .7f + Math.abs(movedY) * .3f;
            if (driftY < dp(.5f) || driftY <= driftX * 1.5f) {
                return;
            }
            scrubbing = true;
            anchorY = y;
            return;
        }
        final float travel = (y - anchorY) * (direction < 0 ? -1 : 1) * (reversed ? -1 : 1);
        final float threshold = dp(stepped ? STEP : FIRST_STEP);
        if (travel >= threshold) {
            anchorY = y;
            stepped = true;
            if (looped || selected < actions.size() - 1) {
                move(1);
            }
        } else if (travel <= -threshold) {
            anchorY = y;
            stepped = true;
            if (looped || selected > 0) {
                move(-1);
            }
        }
    }

    private int chooseDirection(float centerY) {
        final float needed = dp(FIRST_STEP) + (actions.size() - 2) * dp(STEP);
        final float below = parent.getHeight() - centerY;
        return below >= needed || below >= centerY ? 1 : -1;
    }

    public void select(int index) {
        scrubbing = true;
        if (index != selected && index >= 0 && index < actions.size()) {
            int delta = index - selected;
            if (looped) {
                delta = Math.floorMod(delta, actions.size());
                if (delta > actions.size() / 2) {
                    delta -= actions.size();
                }
            }
            selected = index;
            slot += delta;
            positionSpring.getSpring().setFinalPosition(slot * SPRING);
            positionSpring.start();
            invalidate();
        }
    }

    public boolean remove(SwipeAction action) {
        if (scrubbing || actions.size() < 2) {
            return false;
        }
        final int index = actions.indexOf(action);
        if (index < 0 || index == selected && visibility.getValue() != 0) {
            return false;
        }
        actions.remove(index);
        if (selected >= actions.size()) {
            selected = actions.size() - 1;
            slot = selected;
            positionSpring.cancel();
            positionSpring.getSpring().setFinalPosition(selected * SPRING);
            position.setValue(selected * SPRING);
        }
        invalidate();
        return true;
    }

    private void move(int delta) {
        slot += delta;
        selected = Math.floorMod(slot, actions.size());
        try {
            parent.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignore) {}
        positionSpring.getSpring().setFinalPosition(slot * SPRING);
        positionSpring.start();
        invalidate();
    }

    public void draw(Canvas canvas, float translationX, boolean out, float centerY, float bubbleRight) {
        if (actions.isEmpty() || Thread.currentThread() != Looper.getMainLooper().getThread()) {
            return;
        }
        if (direction == 0) {
            direction = chooseDirection(centerY);
        }
        final Paint backgroundPaint = Theme.getThemePaint(Theme.key_paint_chatActionBackground, resourcesProvider);
        final Paint darkenPaint = Theme.chat_actionBackgroundGradientDarkenPaint;
        if (outlinePaint.getColor() != backgroundPaint.getColor()) {
            outlinePaint.setColor(backgroundPaint.getColor());
        }
        if (outlineDarkenPaint.getColor() != darkenPaint.getColor()) {
            outlineDarkenPaint.setColor(darkenPaint.getColor());
        }
        if (outlinePaint.getShader() != backgroundPaint.getShader()) {
            outlinePaint.setShader(backgroundPaint.getShader());
        }
        if (outlineDarkenPaint.getShader() != darkenPaint.getShader()) {
            outlineDarkenPaint.setShader(darkenPaint.getShader());
        }

        framePaint = backgroundPaint;
        frameGradient = resourcesProvider != null && resourcesProvider.hasGradientService();
        frameDark = ColorUtils.calculateLuminance(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)) <= .5f;

        final int wasDarkenColor = outlineDarkenPaint.getColor();
        final float fillProgress = fill.getValue() / SPRING;
        if (fillProgress > 1) {
            beyondMax = true;
        }
        if (visibility.getValue() == 0) {
            fillSpring.cancel();
            fillSpring.getSpring().setFinalPosition(0);
            fill.setValue(0f);
            ringSpring.cancel();
            ringSpring.getSpring().setFinalPosition(0);
            ring.setValue(0f);
            beyondMax = false;
        }

        final boolean filled = fillSpring.getSpring().getFinalPosition() == SPRING;
        final float progress = filled ? 1f
                : MathUtils.clamp((-translationX - dp(VISIBLE_DP)) / dp(TRIGGER_DP - VISIBLE_DP), 0, 1);
        if (progress == 1f && !filled) {
            fillSpring.getSpring().setFinalPosition(SPRING);
            fillSpring.start();
            ringSpring.getSpring().setFinalPosition(SPRING);
            ringSpring.start();
        }

        final float endVisibleValue = translationX <= -dp(VISIBLE_DP) ? SPRING : 0;
        if (endVisibleValue != visibilitySpring.getSpring().getFinalPosition()) {
            visibilitySpring.getSpring().setFinalPosition(endVisibleValue);
            if (!visibilitySpring.isRunning()) {
                visibilitySpring.start();
            }
        }

        final float iconProgress = visibility.getValue() / SPRING;
        final float width = parent.getMeasuredWidth();
        final float x = Math.max(width + translationX * (out ? .5f : 1f),
                Math.min((bubbleRight + width) / 2f, width));
        final float scale = beyondMax ? fillProgress : iconProgress;
        final float clearScale = beyondMax ? 0f : 1f - fillProgress;
        final float carriage = position.getValue() / SPRING;
        final float step = dp(STEP) * direction;
        final float live = liveAlpha.set(scrubbing);

        final int count = actions.size();
        int first = (int) Math.ceil(carriage - 2f);
        int last = (int) Math.floor(carriage + 2f);
        if (!looped || count < 2) {
            first = Math.max(first, 0);
            last = Math.min(last, count - 1);
        }
        for (int s = first; s <= last; s++) {
            if (s != slot) {
                drawBubble(canvas, x, centerY + (s - carriage) * step, scale, clearScale, iconProgress, progress, Math.abs(s - carriage), live, s, false);
            }
        }
        drawBubble(canvas, x, centerY + (slot - carriage) * step, scale, clearScale, iconProgress, progress, Math.abs(slot - carriage), 1f, slot, true);

        outlineDarkenPaint.setColor(wasDarkenColor);
        darkenPaint.setColor(wasDarkenColor);
    }

    @SuppressWarnings("deprecation")
    private void drawBubble(Canvas canvas, float x, float y, float scale, float clearScale, float iconProgress, float progress, float distance, float live, int slot, boolean current) {
        if (distance >= 2f) {
            return;
        }
        final float positionFactor = Math.max(0f, 1f - distance);
        final float alphaFactor = Math.max(positionFactor, (2f - distance) * .45f);
        final float shrink = (.7f + .3f * positionFactor) * (.85f + .15f * CubicBezierInterpolator.EASE_OUT_BACK.getInterpolation(live));
        scale *= shrink;
        clearScale *= shrink;
        iconProgress *= alphaFactor * live;
        if (iconProgress <= 0f) {
            return;
        }

        final Paint backgroundPaint = framePaint;
        final Paint darkenPaint = Theme.chat_actionBackgroundGradientDarkenPaint;
        final boolean gradient = frameGradient;
        final boolean dark = frameDark;
        final float radius = dp(BUBBLE);

        if (current && progress > 0 && fill.getValue() == 0) {
            final float inset = outlinePaint.getStrokeWidth() / 2f;
            setRect(x, y, radius * scale - inset);
            applyServiceShaderMatrix();
            int wasAlpha = outlinePaint.getAlpha();
            outlinePaint.setAlpha((int) (wasAlpha * iconProgress));
            canvas.drawArc(rect, -90, 360 * progress, false, outlinePaint);
            outlinePaint.setAlpha(wasAlpha);
            if (gradient) {
                wasAlpha = outlineDarkenPaint.getAlpha();
                if (dark) {
                    outlineDarkenPaint.setColor(Color.WHITE);
                }
                outlineDarkenPaint.setAlpha((int) (wasAlpha * iconProgress));
                canvas.drawArc(rect, -90, 360 * progress, false, outlineDarkenPaint);
            }
        }

        setRect(x, y, radius * scale);
        applyServiceShaderMatrix();
        path.rewind();
        path.addRoundRect(rect, radius * scale, radius * scale, Path.Direction.CW);

        int wasAlpha = backgroundPaint.getAlpha();
        backgroundPaint.setAlpha((int) (iconProgress * .6f * progress * wasAlpha));
        canvas.drawPath(path, backgroundPaint);
        backgroundPaint.setAlpha(wasAlpha);

        if (gradient) {
            wasAlpha = darkenPaint.getAlpha();
            if (dark) {
                darkenPaint.setColor(Color.WHITE);
            }
            darkenPaint.setAlpha((int) (iconProgress * .6f * progress * wasAlpha));
            canvas.drawPath(path, darkenPaint);
            darkenPaint.setAlpha(wasAlpha);
        }

        if (clearScale != 0f) {
            setRect(x, y, radius * clearScale);
            path.rewind();
            path.addRoundRect(rect, radius, radius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(path, Region.Op.DIFFERENCE);
        }

        setRect(x, y, radius * scale);
        applyServiceShaderMatrix();
        path.rewind();
        path.addRoundRect(rect, radius * scale, radius * scale, Path.Direction.CW);

        wasAlpha = backgroundPaint.getAlpha();
        backgroundPaint.setAlpha((int) (iconProgress * .4f * wasAlpha));
        canvas.drawPath(path, backgroundPaint);
        backgroundPaint.setAlpha(wasAlpha);

        if (gradient) {
            wasAlpha = darkenPaint.getAlpha();
            if (dark) {
                darkenPaint.setColor(Color.WHITE);
            }
            darkenPaint.setAlpha((int) (iconProgress * .4f * wasAlpha));
            canvas.drawPath(path, darkenPaint);
            darkenPaint.setAlpha(wasAlpha);
        }

        if (clearScale != 0f) {
            canvas.restore();
        }

        final float ringProgress = ring.getValue() / SPRING;
        if (current && ringProgress != 0 && ringProgress != 1) {
            final float outScale = 1f + ringProgress;
            final float wasWidth = outlinePaint.getStrokeWidth();
            final float strokeWidth = (1f - ringProgress) * wasWidth;
            if (strokeWidth != 0f) {
                setRect(x, y, radius * outScale - strokeWidth);
                applyServiceShaderMatrix();
                wasAlpha = outlinePaint.getAlpha();
                outlinePaint.setAlpha((int) (wasAlpha * iconProgress));
                outlinePaint.setStrokeWidth(strokeWidth);
                canvas.drawRoundRect(rect, radius * outScale, radius * outScale, outlinePaint);
                outlinePaint.setStrokeWidth(wasWidth);
                outlinePaint.setAlpha(wasAlpha);
                if (gradient) {
                    wasAlpha = outlineDarkenPaint.getAlpha();
                    if (dark) {
                        outlineDarkenPaint.setColor(Color.WHITE);
                    }
                    outlineDarkenPaint.setAlpha((int) (wasAlpha * iconProgress));
                    outlineDarkenPaint.setStrokeWidth(strokeWidth);
                    canvas.drawRoundRect(rect, radius * outScale, radius * outScale, outlineDarkenPaint);
                    outlineDarkenPaint.setStrokeWidth(wasWidth);
                }
            }
        }

        final SwipeAction action = actions.get(Math.floorMod(slot, actions.size()));
        if (action == SwipeAction.REACTION && reactionImage != null) {
            final float half = dp(REACTION_SIZE) / 2f * scale;
            reactionImage.setImageCoords(x - half, y - half, half * 2f, half * 2f);
            reactionImage.setAlpha(iconProgress);
            reactionImage.draw(canvas);
            return;
        }
        if (action == SwipeAction.REACTION && reactionDrawable != null) {
            final float half = dp(REACTION_SIZE) / 2f * scale;
            setRect(x, y, half);
            path.rewind();
            path.addRoundRect(rect, half * .35f, half * .35f, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(path);
            reactionDrawable.setAlpha((int) (iconProgress * 0xFF));
            reactionDrawable.setBounds((int) (x - half), (int) (y - half), (int) (x + half), (int) (y + half));
            reactionDrawable.draw(canvas);
            reactionDrawable.setAlpha(0xFF);
            canvas.restore();
            return;
        }
        final boolean classic = actions.size() == 1 && action == SwipeAction.REPLY;
        final Drawable icon = classic ? Theme.getThemeDrawable(Theme.key_drawable_replyIcon, resourcesProvider) : getIcon(action, (Math.floorDiv(slot, actions.size()) & 1) != 0);
        if (icon == null) {
            return;
        }
        final Float fit = iconFits.get(action.iconRes);
        final float iconScale = classic ? scale : scale * action.iconTrim * (fit != null ? fit : 1f);
        final float halfWidth = icon.getIntrinsicWidth() / 2f * iconScale;
        final float halfHeight = icon.getIntrinsicHeight() / 2f * iconScale;
        icon.setColorFilter(getIconFilter());
        icon.setAlpha((int) (iconProgress * 0xFF));
        icon.setBounds((int) (x - halfWidth), (int) (y - halfHeight), (int) (x + halfWidth), (int) (y + halfHeight));
        icon.draw(canvas);
    }

    private void setRect(float x, float y, float radius) {
        rect.set(x - radius, y - radius, x + radius, y + radius);
    }

    private void applyServiceShaderMatrix() {
        final int width = parent.getMeasuredWidth();
        final float top = parent.getY() + rect.top;
        if (resourcesProvider != null) {
            resourcesProvider.applyServiceShaderMatrix(width, AndroidUtilities.displaySize.y, 0, top);
        } else {
            Theme.applyServiceShaderMatrix(width, AndroidUtilities.displaySize.y, 0, top);
        }
    }

    private ColorFilter getIconFilter() {
        final int color = Theme.getColor(Theme.key_chat_serviceIcon, resourcesProvider);
        if (iconFilter == null || iconFilterColor != color) {
            iconFilterColor = color;
            iconFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY);
        }
        return iconFilter;
    }

    private float measureIconFit(Drawable source) {
        final int width = source.getIntrinsicWidth();
        final int height = source.getIntrinsicHeight();
        if (width <= 0 || height <= 0) {
            return 1f;
        }
        final RectF glyph = GlyphBounds.measure(source);
        if (glyph == null) {
            return 1f;
        }
        return Math.min(dpf2(ICON_WIDTH) / (glyph.width() * width), dpf2(ICON_HEIGHT) / (glyph.height() * height));
    }

    @Nullable
    private Drawable getIcon(SwipeAction action, boolean spare) {
        final SparseArray<Drawable> cache = spare ? spareIcons : icons;
        Drawable drawable = cache.get(action.iconRes);
        if (drawable == null) {
            drawable = ContextCompat.getDrawable(parent.getContext(), action.iconRes);
            if (drawable == null) {
                return null;
            }
            if (iconFits.get(action.iconRes) == null) {
                iconFits.put(action.iconRes, measureIconFit(drawable));
            }
            drawable = drawable.mutate();
            cache.put(action.iconRes, drawable);
        }
        return drawable;
    }

}
