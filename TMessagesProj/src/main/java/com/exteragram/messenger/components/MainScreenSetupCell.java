/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.ExteraUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.LayoutHelper;

import java.util.Objects;

@SuppressLint("ViewConstructor")
public class MainScreenSetupCell extends FrameLayout {

    private final FrameLayout preview;
    //private final SeekBarView sizeBar;

    private final RectF rect = new RectF();
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float statusProgress;
    private float titleProgress;
    private float centeredTitleProgress;
    private float hideAllChatsProgress;
    private float roundedStyleProgress = 0f;
    private float chipsStyleProgress = 0f;
    private float textStyleProgress = 0f;
    private float pillsStyleProgress = 0f;
    private int oldStyle;
    private int currentStyle;
    private String titleText, tabName;
    private int allChatsPadding;

    private ValueAnimator animator;

    private final int startCornersSize = 0;
    private final int endCornersSize = 30;
    private int lastWidth;

    private final AltSeekbar seekBar;

    public interface onDrag {
        void run(float progress);
    }

    public MainScreenSetupCell(Context context, INavigationLayout fragment) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        seekBar = new AltSeekbar(context, (float p) -> {
            ExteraConfig.editor.putFloat("avatarCorners", ExteraConfig.avatarCorners = p).apply();
            invalidate();
            fragment.rebuildAllFragmentViews(false, false);
        }, false, startCornersSize, endCornersSize, LocaleController.getString("AvatarCorners", R.string.AvatarCorners), LocaleController.getString("AvatarCornersLeft", R.string.AvatarCornersLeft), LocaleController.getString("AvatarCornersRight", R.string.AvatarCornersRight));
        seekBar.setProgress((ExteraConfig.avatarCorners - startCornersSize) / (float) (endCornersSize - startCornersSize));
        addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_switchTrack), 0x3F));
        outlinePaint.setStrokeWidth(Math.max(2, AndroidUtilities.dp(1f)));

        preview = new FrameLayout(context) {
            @SuppressLint("DrawAllocation")
            @Override
            protected void onDraw(Canvas canvas) {
                int color = Theme.getColor(Theme.key_switchTrack);
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);
                float w = getMeasuredWidth();
                float h = getMeasuredHeight();

                textPaint.setColor(ColorUtils.blendARGB(0x00, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), titleProgress));
                textPaint.setTextSize(AndroidUtilities.dp(20));
                titleText = (String) TextUtils.ellipsize(titleText, textPaint, w - AndroidUtilities.dp(130 + 35 * statusProgress), TextUtils.TruncateAt.END);
                textPaint.setTextSize(AndroidUtilities.dp(18 + 2 * titleProgress));
                textPaint.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));

                rect.set(0, 0, w, h);
                Theme.dialogs_onlineCirclePaint.setColor(Color.argb(20, r, g, b));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(8), AndroidUtilities.dp(8), Theme.dialogs_onlineCirclePaint);

                float stroke = outlinePaint.getStrokeWidth() / 2;
                rect.set(stroke, stroke, w - stroke, h - stroke);
                canvas.drawRoundRect(rect, AndroidUtilities.dp(8), AndroidUtilities.dp(8), outlinePaint);

                Drawable search = ContextCompat.getDrawable(context, R.drawable.ic_ab_search).mutate();
                search.setColorFilter(new PorterDuffColorFilter(Color.argb(204, r, g, b), PorterDuff.Mode.MULTIPLY));
                search.setBounds((int) w - AndroidUtilities.dp(39), AndroidUtilities.dp(22), (int) w - AndroidUtilities.dp(12), AndroidUtilities.dp(49));
                search.draw(canvas);

                Theme.dialogs_onlineCirclePaint.setColor(Color.argb(204, r, g, b));
                for (int i = 0; i < 3; i++) {
                    float start = 28 + 6.1f * i;
                    canvas.drawRoundRect(
                            AndroidUtilities.dpf2(20),
                            AndroidUtilities.dpf2(start),
                            AndroidUtilities.dpf2(20 + 20),
                            AndroidUtilities.dpf2(start + 2.8f),
                            AndroidUtilities.dp(10),
                            AndroidUtilities.dp(10),
                            Theme.dialogs_onlineCirclePaint
                    );
                }

                float width = textPaint.measureText(titleText);
                float titleStart = centeredTitleProgress * ((w - width - AndroidUtilities.dp(30) * statusProgress) / 2 - AndroidUtilities.dp(78)) + AndroidUtilities.dp(78);
                float titleEnd = titleStart + width;

                Theme.dialogs_onlineCirclePaint.setColor(ColorUtils.blendARGB(0x00, ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_switchTrack), 0x5F), titleProgress * statusProgress));
                canvas.drawRoundRect(titleEnd + AndroidUtilities.dp(5), AndroidUtilities.dp(22), titleEnd + AndroidUtilities.dp(30), AndroidUtilities.dp(47), AndroidUtilities.dp(4), AndroidUtilities.dp(4), Theme.dialogs_onlineCirclePaint);
                canvas.drawText(titleText, titleStart, AndroidUtilities.dp(42), textPaint);

                textPaint.setTextSize(AndroidUtilities.dp(15));

                float startY = h - AndroidUtilities.dp(4) - stroke - AndroidUtilities.dp(87);

                Theme.dialogs_onlineCirclePaint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_switchTrack), 0x3F));
                if (!ExteraConfig.disableDividers)
                    canvas.drawLine(stroke * 2, startY + AndroidUtilities.dp(4), getMeasuredWidth() - stroke * 2, startY + AndroidUtilities.dp(4f), Theme.dialogs_onlineCirclePaint);

                @SuppressLint("DrawAllocation") Path tab = new Path();
                tab.addRect(0, startY + AndroidUtilities.dp(4), getMeasuredWidth(), startY + AndroidUtilities.dp(10), Path.Direction.CCW);
                canvas.clipPath(tab, Region.Op.DIFFERENCE);

                // tabs start
                textPaint.setColor(color);
                textPaint.setTextSize(AndroidUtilities.dp(15));

                for (int i = 1; i < 3; i++) {
                    String s = i == 1 ? LocaleController.getString("FilterGroups", R.string.FilterGroups) : LocaleController.getString("FilterBots", R.string.FilterBots);
                    float sw = (int) Math.ceil(textPaint.measureText(s));
                    float startX = (w - w / (i == 1 ? 2 : 6)) - sw / 2;
                    canvas.drawText(s, startX, startY - AndroidUtilities.dp(13), textPaint);
                }

                float sw = textPaint.measureText(tabName);
                float startX = w / 6 - sw / 2;
                textPaint.setColor(ColorUtils.blendARGB(0x00, Theme.getColor(Theme.key_windowBackgroundWhiteValueText), hideAllChatsProgress));
                textPaint.setTextSize(AndroidUtilities.dp(12 + 3 * hideAllChatsProgress));
                Theme.dialogs_onlineCirclePaint.setColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhiteValueText), ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteValueText), 0x2F), chipsStyleProgress));
                Theme.dialogs_onlineCirclePaint.setColor(ColorUtils.blendARGB(0x00, Theme.dialogs_onlineCirclePaint.getColor(), hideAllChatsProgress));

                canvas.drawRoundRect(
                        startX - allChatsPadding * hideAllChatsProgress - AndroidUtilities.dp(11) * chipsStyleProgress - AndroidUtilities.dp(3) * pillsStyleProgress,
                        startY + AndroidUtilities.dp(6) * textStyleProgress - AndroidUtilities.dpf2(37.5f) * chipsStyleProgress,
                        startX + sw + allChatsPadding * hideAllChatsProgress + AndroidUtilities.dp(11) * chipsStyleProgress + AndroidUtilities.dp(3) * pillsStyleProgress,
                        startY + AndroidUtilities.dp(8) - AndroidUtilities.dp(4) * roundedStyleProgress - AndroidUtilities.dpf2(9.5f) * chipsStyleProgress,
                        AndroidUtilities.dpf2(8 + 15 * pillsStyleProgress),
                        AndroidUtilities.dpf2(8 + 15 * pillsStyleProgress),
                        Theme.dialogs_onlineCirclePaint);
                canvas.drawText(tabName, startX, startY - AndroidUtilities.dp(14), textPaint);
                // tabs end //

                h -= startY + AndroidUtilities.dp(4);
                canvas.translate(0, startY + AndroidUtilities.dp(4));
                Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_chats_onlineCircle));
                canvas.drawCircle(AndroidUtilities.dp(63), h / 2.0f + AndroidUtilities.dp(21), AndroidUtilities.dp(7), Theme.dialogs_onlineCirclePaint);

                Theme.dialogs_onlineCirclePaint.setColor(Color.argb(204, r, g, b));
                canvas.drawRoundRect(AndroidUtilities.dp(83), h / 2.0f - AndroidUtilities.dp(8), AndroidUtilities.dp(170), h / 2.0f - AndroidUtilities.dp(16), w / 2.0f, w / 2.0f, Theme.dialogs_onlineCirclePaint);

                @SuppressLint("DrawAllocation") Path online = new Path();
                online.addCircle(AndroidUtilities.dp(63), h / 2.0f + AndroidUtilities.dp(21), AndroidUtilities.dp(12), Path.Direction.CCW);
                canvas.clipPath(online, Region.Op.DIFFERENCE);

                Theme.dialogs_onlineCirclePaint.setColor(Color.argb(90, r, g, b));
                canvas.drawRoundRect(AndroidUtilities.dp(83), h / 2.0f + AndroidUtilities.dp(8), AndroidUtilities.dp(230), h / 2.0f + AndroidUtilities.dp(16), w / 2.0f, w / 2.0f, Theme.dialogs_onlineCirclePaint);
                canvas.drawRoundRect(AndroidUtilities.dp(15), h / 2.0f - AndroidUtilities.dp(28), AndroidUtilities.dp(71), h / 2.0f + AndroidUtilities.dp(28), ExteraConfig.getAvatarCorners(56), ExteraConfig.getAvatarCorners(56), Theme.dialogs_onlineCirclePaint);
            }
        };
        preview.setWillNotDraw(false);
        addView(preview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.CENTER, 21, 112, 21, 21));
        updateStatus(false);
        updateCenteredTitle(false);
        updateTabStyle(false);
        updateTabName(false);
        updateTitle(false);
    }

    public void updateStatus(boolean animate) {
        float to = !ExteraConfig.hideActionBarStatus ? 1 : 0;
        if (to == statusProgress && animate || !UserConfig.getInstance(UserConfig.selectedAccount).isPremium())
            return;
        if (animate) {
            animator = ValueAnimator.ofFloat(statusProgress, to).setDuration(250);
            animator.setInterpolator(Easings.easeInOutQuad);
            animator.addUpdateListener(animation -> {
                statusProgress = (Float) animation.getAnimatedValue();
                titleText = ExteraUtils.getActionBarTitle();
                invalidate();
            });
            animator.start();
        } else {
            statusProgress = to;
            invalidate();
        }
    }

    public void updateCenteredTitle(boolean animate) {
        float to = ExteraConfig.centerTitle ? 1 : 0;
        if (to == centeredTitleProgress && animate)
            return;
        if (animate) {
            animator = ValueAnimator.ofFloat(centeredTitleProgress, to).setDuration(250);
            animator.setInterpolator(Easings.easeInOutQuad);
            animator.addUpdateListener(animation -> {
                centeredTitleProgress = (Float) animation.getAnimatedValue();
                invalidate();
            });
            animator.start();
        } else {
            centeredTitleProgress = to;
            invalidate();
        }
    }

    public void updateTabName(boolean animate) {
        if (Objects.equals(tabName, getTabName()) && animate)
            return;
        if (animate) {
            animator = ValueAnimator.ofFloat(1f, 0f).setDuration(250);
            animator.setInterpolator(Easings.easeInOutQuad);
            animator.addUpdateListener(animation -> {
                hideAllChatsProgress = (Float) animation.getAnimatedValue();
                invalidate();
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    tabName = getTabName();
                    allChatsPadding = isAllChatsTab() ? AndroidUtilities.dp(6) : 0;
                    animator.setFloatValues(0f, 1f);
                    animator.removeAllListeners();
                    animator.start();
                }
            });
            animator.start();
        } else {
            tabName = getTabName();
            allChatsPadding = isAllChatsTab() ? AndroidUtilities.dp(6) : 0;
            hideAllChatsProgress = 1f;
            invalidate();
        }
    }

    public void updateTitle(boolean animate) {
        if (Objects.equals(titleText, ExteraUtils.getActionBarTitle()) && animate)
            return;
        if (animate) {
            animator = ValueAnimator.ofFloat(1f, 0f).setDuration(250);
            animator.setInterpolator(Easings.easeInOutQuad);
            animator.addUpdateListener(animation -> {
                titleProgress = (Float) animation.getAnimatedValue();
                invalidate();
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    titleText = ExteraUtils.getActionBarTitle();
                    animator.setFloatValues(0f, 1f);
                    animator.removeAllListeners();
                    animator.start();
                }
            });
            animator.start();
        } else {
            titleText = ExteraUtils.getActionBarTitle();
            titleProgress = 1f;
            invalidate();
        }
    }

    public void updateTabStyle(boolean animate) {
        if (Objects.equals(currentStyle, ExteraConfig.tabStyle) && animate)
            return;

        oldStyle = currentStyle;
        currentStyle = ExteraConfig.tabStyle;

        if (animate) {
            ValueAnimator def = ValueAnimator.ofFloat(0f, 1f).setDuration(250);
            def.setStartDelay(100);
            def.setInterpolator(Easings.easeInOutQuad);
            def.addUpdateListener(animation -> {
                if (currentStyle == 1) {
                    roundedStyleProgress = (Float) animation.getAnimatedValue();
                } else if (currentStyle == 2) {
                    textStyleProgress = (Float) animation.getAnimatedValue();
                } else if (currentStyle == 3) {
                    if (oldStyle != 4) chipsStyleProgress = (Float) animation.getAnimatedValue();
                } else if (currentStyle == 4) {
                    pillsStyleProgress = (Float) animation.getAnimatedValue();
                    if (oldStyle != 3) chipsStyleProgress = pillsStyleProgress;
                }
                invalidate();
            });

            animator = ValueAnimator.ofFloat(1f, 0f).setDuration(250);
            animator.setStartDelay(100);
            animator.setInterpolator(Easings.easeInOutQuad);
            animator.addUpdateListener(animation -> {
                if (oldStyle == 1) {
                    roundedStyleProgress = (Float) animation.getAnimatedValue();
                } else if (oldStyle == 2) {
                    textStyleProgress = (Float) animation.getAnimatedValue();
                } else if (oldStyle == 3) {
                    if (currentStyle != 4)
                        chipsStyleProgress = (Float) animation.getAnimatedValue();
                } else if (oldStyle == 4) {
                    pillsStyleProgress = (Float) animation.getAnimatedValue();
                    if (currentStyle != 3)
                        chipsStyleProgress = (Float) animation.getAnimatedValue();
                }
                invalidate();
            });

            animator.start();
            def.start();
        } else {
            if (currentStyle == 1) {
                roundedStyleProgress = 1f;
            } else if (currentStyle == 2) {
                textStyleProgress = 1f;
            } else if (currentStyle == 3) {
                chipsStyleProgress = 1f;
            } else if (currentStyle == 4) {
                chipsStyleProgress = 1f;
                pillsStyleProgress = 1f;
            }
            invalidate();
        }

    }

    private String getTabName() {
        return ExteraConfig.hideAllChats ? LocaleController.getString("FilterUnread", R.string.FilterUnread) : LocaleController.getString("FilterAllChatsShort", R.string.FilterAllChatsShort);
    }

    private boolean isAllChatsTab() {
        return Objects.equals(tabName, LocaleController.getString("FilterAllChatsShort", R.string.FilterAllChatsShort));
    }

    @Override
    public void invalidate() {
        super.invalidate();
        preview.invalidate();
        seekBar.invalidate();
        lastWidth = -1;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!ExteraConfig.disableDividers)
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(21), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(21) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(331), MeasureSpec.EXACTLY));
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (lastWidth != width) {
            lastWidth = width;
        }
    }
}
