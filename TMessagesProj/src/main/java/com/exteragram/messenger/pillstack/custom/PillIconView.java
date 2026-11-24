/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.custom;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.exteragram.messenger.utils.ui.GlyphBounds;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.Components.AnimatedEmojiDrawable;

import java.util.Objects;

public class PillIconView extends android.view.View {

    private static final RectF FULL_GLYPH = new RectF(0, 0, 1, 1);

    private final int emojiSize;
    private float glyphFit;
    @Nullable
    private RectF glyphBounds;
    private boolean glyphPending;
    private int customEmojiCacheType = AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW;
    @Nullable
    private CustomPillSpec.Icon icon;
    private int iconRes;
    @Nullable
    private Drawable drawable;
    private boolean tintable;
    @Nullable
    private String fallbackText;
    @Nullable
    private TextPaint textPaint;
    @Nullable
    private AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable customEmoji;
    private boolean showCustomEmoji;
    private boolean attached;
    private int color = Color.WHITE;

    public PillIconView(Context context, int sizeDp) {
        super(context);
        emojiSize = dp(sizeDp);
        NotificationCenter.getGlobalInstance().listen(this, NotificationCenter.emojiLoaded, args -> {
            if (glyphPending) {
                updateGlyphBounds();
            }
            invalidate();
        });
    }

    public void setIcon(@Nullable CustomPillSpec.Icon icon) {
        if (iconRes == 0 && Objects.equals(this.icon, icon)) {
            return;
        }
        this.icon = icon;
        iconRes = 0;
        drawable = null;
        fallbackText = null;
        tintable = false;
        showCustomEmoji = false;
        if (icon != null) {
            if (icon.isCustomEmoji()) {
                if (customEmoji == null) {
                    customEmoji = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this, false, emojiSize, customEmojiCacheType);
                    if (attached) {
                        customEmoji.attach();
                    }
                }
                customEmoji.set(icon.getDocumentId(), customEmojiCacheType, false);
                customEmoji.setColor(color);
                showCustomEmoji = true;
            } else if (icon.isEmoji()) {
                Drawable emoji = Emoji.getEmojiDrawable(icon.value);
                if (emoji != null) {
                    drawable = emoji;
                    updateGlyphBounds();
                } else {
                    fallbackText = icon.value;
                }
            } else {
                setDrawableRes(CustomPillStore.resolveIconRes(icon));
            }
        }
        invalidate();
    }

    public void setIconRes(int res) {
        if (iconRes == res) {
            return;
        }
        icon = null;
        iconRes = res;
        fallbackText = null;
        showCustomEmoji = false;
        setDrawableRes(res);
        invalidate();
    }

    private void setDrawableRes(int res) {
        Drawable d;
        try {
            d = res != 0 ? ContextCompat.getDrawable(getContext(), res) : null;
        } catch (Exception e) {
            d = ContextCompat.getDrawable(getContext(), R.drawable.msg_filled_datausage);
        }
        drawable = d != null ? d.mutate() : null;
        tintable = drawable != null;
        applyColor();
        updateGlyphBounds();
    }

    public void setCustomEmojiCacheType(int cacheType) {
        customEmojiCacheType = cacheType;
    }

    public void setGlyphFit(float glyphFit) {
        this.glyphFit = glyphFit;
        updateGlyphBounds();
        invalidate();
    }

    private void updateGlyphBounds() {
        glyphBounds = null;
        glyphPending = false;
        if (glyphFit <= 0 || drawable == null) {
            return;
        }
        if (drawable instanceof Emoji.EmojiDrawable emoji && !emoji.isLoaded()) {
            glyphPending = true;
            emoji.preload();
            return;
        }
        glyphBounds = GlyphBounds.measure(drawable);
    }

    public void setColor(int color) {
        if (this.color == color) {
            return;
        }
        this.color = color;
        applyColor();
        invalidate();
    }

    private void applyColor() {
        if (tintable && drawable != null) {
            drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        }
        if (customEmoji != null) {
            customEmoji.setColor(color);
        }
        if (textPaint != null) {
            textPaint.setColor(color);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        if (glyphPending) {
            updateGlyphBounds();
        }
        if (customEmoji != null) {
            customEmoji.attach();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
        if (customEmoji != null) {
            customEmoji.detach();
        }
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == drawable || who == customEmoji || super.verifyDrawable(who);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        float fitSize = glyphFit * Math.min(width, height);
        if (showCustomEmoji && customEmoji != null) {
            int size = glyphFit > 0 ? Math.round(fitSize) : Math.min(width, height);
            int left = (width - size) / 2;
            int top = (height - size) / 2;
            customEmoji.setBounds(left, top, left + size, top + size);
            customEmoji.draw(canvas);
        } else if (drawable != null && glyphFit > 0) {
            int intrinsicWidth = drawable.getIntrinsicWidth();
            int intrinsicHeight = drawable.getIntrinsicHeight();
            float aspect = intrinsicWidth > 0 && intrinsicHeight > 0 ? intrinsicHeight / (float) intrinsicWidth : 1f;
            RectF glyph = glyphBounds != null ? glyphBounds : FULL_GLYPH;
            float boxWidth = fitSize / Math.max(glyph.width(), glyph.height() * aspect);
            float boxHeight = boxWidth * aspect;
            float left = width / 2f - glyph.centerX() * boxWidth;
            float top = height / 2f - glyph.centerY() * boxHeight;
            drawable.setBounds(Math.round(left), Math.round(top), Math.round(left + boxWidth), Math.round(top + boxHeight));
            drawable.draw(canvas);
        } else if (drawable != null) {
            int intrinsicWidth = drawable.getIntrinsicWidth();
            int intrinsicHeight = drawable.getIntrinsicHeight();
            if (intrinsicWidth <= 0 || intrinsicHeight <= 0 || !tintable) {
                intrinsicWidth = width;
                intrinsicHeight = height;
            }
            float scale = Math.min(1f, Math.min(width / (float) intrinsicWidth, height / (float) intrinsicHeight));
            int drawWidth = Math.round(intrinsicWidth * scale);
            int drawHeight = Math.round(intrinsicHeight * scale);
            int left = (width - drawWidth) / 2;
            int top = (height - drawHeight) / 2;
            drawable.setBounds(left, top, left + drawWidth, top + drawHeight);
            drawable.draw(canvas);
        } else if (fallbackText != null) {
            if (textPaint == null) {
                textPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
                textPaint.setTypeface(AndroidUtilities.regular());
                textPaint.setColor(color);
                textPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
            }
            textPaint.setTextSize(glyphFit > 0 ? fitSize : height * 0.8f);
            float y = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f;
            canvas.drawText(fallbackText, width / 2f, y, textPaint);
        }
    }
}
