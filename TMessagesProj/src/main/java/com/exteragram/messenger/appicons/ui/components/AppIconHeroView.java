/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.appicons.ui.components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.exteragram.messenger.appicons.AppIcon;
import com.exteragram.messenger.appicons.AppIconController;
import com.exteragram.messenger.appicons.AppIconPreviewLoader;
import com.exteragram.messenger.utils.text.LocaleUtils;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

@SuppressLint("ViewConstructor")
public class AppIconHeroView extends LinearLayout implements Theme.Colorable {

    public static final int PREVIEW_SIZE_DP = 128;
    public static final int PREVIEW_MIN_SIZE_DP = 64;

    private static final int COLLAPSED_PREVIEW_SIZE_DP = 40;
    private static final float TEXT_COLLAPSE_PART = 0.35f;
    private static final int TEXT_COLLAPSE_SHIFT_DP = 12;

    private static final int TITLE_TOP_DP = 18;
    private static final int TITLE_HEIGHT_DP = 28;
    private static final int SUBTITLE_TOP_DP = 6;
    private static final int SUBTITLE_HEIGHT_DP = 20;

    private static final int LINK_TOUCH_SLOP_DP = 4;

    private int previewSizeDp = PREVIEW_SIZE_DP;

    private final AppIconPreviewView previewView;
    private final AnimatedTextView titleView;
    private final AnimatedTextView subtitleView;

    private final BaseFragment fragment;
    private final Theme.ResourcesProvider resourcesProvider;

    private AppIcon icon;
    private float collapse;
    private int linkColor;
    private ValueAnimator linkColorAnimator;
    private CharSequence subtitleLinks;
    private ClickableSpan pressedLink;
    private Runnable previewReadyListener;

    public AppIconHeroView(@NonNull Context context, @NonNull BaseFragment fragment) {
        super(context);
        this.fragment = fragment;
        this.resourcesProvider = fragment.getResourceProvider();

        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setClipToPadding(false);
        setClipChildren(false);

        previewView = new AppIconPreviewView(context, resourcesProvider);
        previewView.setCrossfade(true);
        previewView.setOnPreviewReady(this::onPreviewReady);
        addView(previewView, LayoutHelper.createLinear(PREVIEW_SIZE_DP, PREVIEW_SIZE_DP, Gravity.CENTER_HORIZONTAL));

        titleView = addText(22, AndroidUtilities.bold(), TITLE_HEIGHT_DP, TITLE_TOP_DP);
        subtitleView = addText(14, AndroidUtilities.regular(), SUBTITLE_HEIGHT_DP, SUBTITLE_TOP_DP);
        updateColors();

        boolean anySubtitle = false;
        for (AppIcon icon : AppIconController.getAvailableIcons()) {
            anySubtitle |= subtitleOf(icon) != null;
        }
        subtitleView.setVisibility(anySubtitle ? VISIBLE : GONE);
    }

    private static CharSequence subtitleOf(AppIcon icon) {
        final CharSequence description = icon.getDescription();
        if (!TextUtils.isEmpty(description)) {
            return description;
        }
        return icon.getAuthor();
    }

    @Override
    public void updateColors() {
        final int text = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        titleView.setTextColor(text);
        subtitleView.setTextColor(ColorUtils.setAlphaComponent(text, 0xB3));
        updateLinkColor(false);
    }

    private void updateLinkColor(boolean animated) {
        final int color = AppIconPreviewLoader.getAccentTextColor(icon, Theme.key_windowBackgroundWhiteLinkText, resourcesProvider);
        if (linkColor == color) {
            return;
        }
        if (linkColorAnimator != null) {
            linkColorAnimator.cancel();
            linkColorAnimator = null;
        }
        if (!animated || linkColor == 0) {
            linkColor = color;
            subtitleView.invalidate();
            return;
        }
        final int from = linkColor;
        linkColorAnimator = ValueAnimator.ofFloat(0f, 1f).setDuration(280);
        linkColorAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        linkColorAnimator.addUpdateListener(animation -> {
            linkColor = ColorUtils.blendARGB(from, color, (float) animation.getAnimatedValue());
            subtitleView.invalidate();
        });
        linkColorAnimator.start();
    }

    private AnimatedTextView addText(int textSizeDp, Typeface typeface, int heightDp, int topMarginDp) {
        AnimatedTextView textView = new AnimatedTextView(getContext(), true, true, false);
        textView.setGravity(Gravity.CENTER);
        textView.setTypeface(typeface);
        textView.setTextSize(dp(textSizeDp));
        textView.setIncludeFontPadding(false);
        textView.setAllowCancel(true);
        textView.setAnimationProperties(0.35f, 0, 260, CubicBezierInterpolator.EASE_OUT_QUINT);
        addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, heightDp, Gravity.CENTER_HORIZONTAL, 24, topMarginDp, 24, 0));
        return textView;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    public void setOnPreviewReady(Runnable listener) {
        previewReadyListener = listener;
    }

    private void onPreviewReady() {
        updateLinkColor(true);
        if (previewReadyListener != null) {
            previewReadyListener.run();
        }
    }

    public void set(AppIcon icon) {
        if (this.icon == icon) {
            return;
        }
        final boolean animated = this.icon != null;
        this.icon = icon;
        previewView.setIcon(icon);
        updateLinkColor(animated);

        titleView.setText(icon.getTitle(), animated);
        final CharSequence subtitle = subtitleOf(icon);
        subtitleLinks = subtitle == null ? "" : LocaleUtils.formatWithUsernames(subtitle, fragment);
        subtitleView.setText(colorizeLinks(subtitleLinks), animated);
    }

    private CharSequence colorizeLinks(CharSequence text) {
        if (!(text instanceof Spanned)) {
            return text;
        }
        final SpannableStringBuilder builder = new SpannableStringBuilder(text);
        for (ClickableSpan span : builder.getSpans(0, builder.length(), ClickableSpan.class)) {
            final int start = builder.getSpanStart(span);
            final int end = builder.getSpanEnd(span);
            builder.removeSpan(span);
            builder.setSpan(new CharacterStyle() {
                @Override
                public void updateDrawState(TextPaint paint) {
                    paint.setColor(Theme.multAlpha(linkColor, paint.getAlpha() / 255f));
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return builder;
    }

    public boolean dispatchLinkTouch(MotionEvent event) {
        final float x = event.getX() - getX() - subtitleView.getX();
        final float y = event.getY() - getY() - subtitleView.getY();
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            pressedLink = hitLink(x, y);
            return pressedLink != null;
        }
        if (pressedLink == null) {
            return false;
        }
        if (action == MotionEvent.ACTION_UP && pressedLink == hitLink(x, y)) {
            pressedLink.onClick(subtitleView);
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            pressedLink = null;
        }
        return true;
    }

    private ClickableSpan hitLink(float x, float y) {
        if (subtitleView.getVisibility() != VISIBLE || subtitleView.getAlpha() <= 0f
                || y < 0 || y > subtitleView.getHeight() || !(subtitleLinks instanceof Spanned spanned)) {
            return null;
        }
        final TextPaint paint = subtitleView.getPaint();
        final float textLeft = (subtitleView.getWidth() - subtitleView.getDrawable().getCurrentWidth()) / 2f;
        for (ClickableSpan span : spanned.getSpans(0, spanned.length(), ClickableSpan.class)) {
            final int start = spanned.getSpanStart(span);
            final int end = spanned.getSpanEnd(span);
            final float left = textLeft + paint.measureText(spanned, 0, start) - dp(LINK_TOUCH_SLOP_DP);
            final float right = left + paint.measureText(spanned, start, end) + dp(LINK_TOUCH_SLOP_DP) * 2;
            if (x >= left && x <= right) {
                return span;
            }
        }
        return null;
    }

    public void setCollapse(float value) {
        if (collapse == value) {
            return;
        }
        collapse = value;
        applyCollapse();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        applyCollapse();
    }

    private void applyCollapse() {
        final float textCollapse = Math.min(1f, collapse / TEXT_COLLAPSE_PART);
        final float shift = -dp(TEXT_COLLAPSE_SHIFT_DP) * textCollapse;
        titleView.setAlpha(1f - textCollapse);
        titleView.setTranslationY(shift);
        subtitleView.setAlpha(1f - textCollapse);
        subtitleView.setTranslationY(shift);

        final int size = previewView.getMeasuredHeight();
        if (size <= 0) {
            return;
        }
        final float center = previewView.getTop() + size / 2f;
        final float collapsedCenter = AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() / 2f - getTop();
        final float scale = AndroidUtilities.lerp(1f, dp(COLLAPSED_PREVIEW_SIZE_DP) / (float) size, collapse);
        previewView.setPivotX(previewView.getMeasuredWidth() / 2f);
        previewView.setPivotY(size / 2f);
        previewView.setScaleX(scale);
        previewView.setScaleY(scale);
        previewView.setTranslationY((collapsedCenter - center) * collapse);
    }

    public int getTextBlockHeight() {
        int height = dp(TITLE_TOP_DP + TITLE_HEIGHT_DP);
        if (subtitleView.getVisibility() == VISIBLE) {
            height += dp(SUBTITLE_TOP_DP + SUBTITLE_HEIGHT_DP);
        }
        return height;
    }

    public void setPreviewSizeDp(int sizeDp) {
        if (previewSizeDp == sizeDp) {
            return;
        }
        previewSizeDp = sizeDp;
        final LayoutParams params = (LayoutParams) previewView.getLayoutParams();
        params.width = params.height = dp(sizeDp);
        previewView.requestLayout();
    }

    public int getPreviewSize() {
        return dp(previewSizeDp);
    }
}
