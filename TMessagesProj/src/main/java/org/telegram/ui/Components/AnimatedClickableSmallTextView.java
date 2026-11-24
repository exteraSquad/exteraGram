package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Picture;
import android.text.Layout;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.telegram.ui.ActionBar.Theme;

import me.vkryl.android.animator.FactorAnimator;

/**
 * {@link LinkSpanDrawable.ClickableSmallTextView} whose text can change animated: the old text moves
 * up and fades out while the new one comes from below.
 */
public class AnimatedClickableSmallTextView extends LinkSpanDrawable.ClickableSmallTextView implements FactorAnimator.Target {
    private static final long DURATION = 320;

    private final FactorAnimator changeAnimator = new FactorAnimator(0, this, CubicBezierInterpolator.EASE_OUT_QUINT, DURATION, 1f);
    private Picture outPicture;
    /** Where the old text was: a parent may move the view to the new text (e.g. to centre it), the old one stays. */
    private float outTranslationX;
    /** The layer the text view asks for (the gradient ellipsis); off while cross-fading, a layer would clip the old text. */
    private int requestedLayerType = LAYER_TYPE_NONE;
    private Paint requestedLayerPaint;
    private int changePrefix, changeSuffix, changeNewLength;
    private float oldChangeLeft, oldChangeRight;

    public AnimatedClickableSmallTextView(Context context) {
        super(context);
    }

    public AnimatedClickableSmallTextView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
    }

    /** Sets the text; when {@code animated} and the view is on screen with another text, cross-fades to it. */
    public boolean setTextAnimated(CharSequence text, boolean animated) {
        final CharSequence current = getText();
        if (TextUtils.equals(current, text)) {
            return setText(text);
        }
        if (animated && isAttachedToWindow() && isLaidOut() && getVisibility() == VISIBLE && getAlpha() > 0 && getWidth() > 0 && getHeight() > 0 && !TextUtils.isEmpty(current)) {
            measureChange(current, text);
            final Picture picture = new Picture();
            final Canvas canvas = picture.beginRecording(getWidth(), getHeight());
            drawContent(canvas, 1f, 0);
            picture.endRecording();
            changeAnimator.forceFactor(0f);
            outPicture = picture;
            outTranslationX = getTranslationX();
            super.setLayerType(LAYER_TYPE_NONE, null);
            changeAnimator.animateTo(1f);
        } else {
            changeAnimator.forceFactor(1f);
            finishChange();
        }
        invalidate();
        return setText(text);
    }

    private void measureChange(CharSequence from, CharSequence to) {
        final String a = from.toString(), b = to.toString();
        int prefix = 0;
        final int max = Math.min(a.length(), b.length());
        while (prefix < max && a.charAt(prefix) == b.charAt(prefix)) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < max - prefix && a.charAt(a.length() - 1 - suffix) == b.charAt(b.length() - 1 - suffix)) {
            suffix++;
        }
        while (prefix > 0 && Character.isHighSurrogate(a.charAt(prefix - 1))) {
            prefix--;
        }
        while (suffix > 0 && Character.isLowSurrogate(a.charAt(a.length() - suffix))) {
            suffix--;
        }
        final Layout layout = getLayout();
        if (layout == null || layout.getLineCount() != 1 || layout.getText().length() != a.length()) {
            prefix = 0;
            suffix = 0;
        }
        changePrefix = prefix;
        changeSuffix = suffix;
        changeNewLength = b.length();
        final float origin = getTextStartX() - getLeft();
        oldChangeLeft = prefix == 0 ? 0 : origin + layout.getPrimaryHorizontal(prefix);
        oldChangeRight = suffix == 0 ? getWidth() : origin + layout.getPrimaryHorizontal(a.length() - suffix);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (outPicture == null) {
            super.onDraw(canvas);
            return;
        }
        final float factor = changeAnimator.getFactor();
        final int offset = dp(6);
        final float shift = outTranslationX - getTranslationX();
        float newLeft = 0, newRight = getWidth();
        float oldLeft = oldChangeLeft, oldRight = oldChangeRight;
        final Layout layout = getLayout();
        if (layout != null && layout.getLineCount() == 1 && changeNewLength == getText().length() && layout.getText().length() == changeNewLength) {
            final float origin = getTextStartX() - getLeft();
            if (changePrefix > 0) {
                newLeft = origin + layout.getPrimaryHorizontal(changePrefix);
            }
            if (changeSuffix > 0) {
                newRight = origin + layout.getPrimaryHorizontal(changeNewLength - changeSuffix);
                if (Math.abs(newRight - (oldRight + shift)) > 1) {
                    newRight = getWidth();
                    oldRight = outPicture.getWidth();
                }
            }
            if (changePrefix > 0 && Math.abs(newLeft - (oldLeft + shift)) > 1) {
                newLeft = 0;
                oldLeft = 0;
            }
        } else {
            oldLeft = 0;
            oldRight = outPicture.getWidth();
        }
        if (newLeft > 0) {
            canvas.save();
            canvas.clipRect(0, 0, newLeft, getHeight());
            super.onDraw(canvas);
            canvas.restore();
        }
        if (newRight < getWidth()) {
            canvas.save();
            canvas.clipRect(newRight, 0, getWidth(), getHeight());
            super.onDraw(canvas);
            canvas.restore();
        }
        canvas.save();
        canvas.translate(shift, -offset * factor);
        canvas.clipRect(oldLeft, 0, oldRight, outPicture.getHeight());
        canvas.saveLayerAlpha(0, 0, outPicture.getWidth(), outPicture.getHeight(), (int) (0xFF * (1f - factor)));
        canvas.drawPicture(outPicture);
        canvas.restore();
        canvas.restore();
        canvas.save();
        canvas.clipRect(newLeft, 0, newRight, getHeight());
        drawContent(canvas, factor, offset * (1f - factor));
        canvas.restore();
    }

    private void drawContent(Canvas canvas, float alpha, float translationY) {
        if (alpha >= 1f && translationY == 0) {
            super.onDraw(canvas);
            return;
        }
        canvas.save();
        canvas.translate(0, translationY);
        canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (0xFF * alpha));
        super.onDraw(canvas);
        canvas.restore();
        canvas.restore();
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        invalidate();
    }

    @Override
    public void onFactorChangeFinished(int id, float finalFactor, FactorAnimator callee) {
        if (finalFactor == 1f) {
            finishChange();
        }
    }

    private void finishChange() {
        if (outPicture != null) {
            outPicture = null;
            super.setLayerType(requestedLayerType, requestedLayerPaint);
        }
        invalidate();
    }

    @Override
    public void setLayerType(int layerType, @Nullable Paint paint) {
        requestedLayerType = layerType;
        requestedLayerPaint = paint;
        if (outPicture == null) {
            super.setLayerType(layerType, paint);
        }
    }

    @Override
    public int getLayerType() {
        return requestedLayerType;
    }
}
