/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.preferences.components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

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
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.utils.FolderIcons;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.LayoutHelper;

import java.util.Objects;

@SuppressLint("ViewConstructor")
public class FoldersPreviewCell extends FrameLayout {

    private final FrameLayout preview;

    private final RectF rect = new RectF();
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float hideAllChatsProgress;
    private float roundedStyleProgress = 0f;
    private float chipsStyleProgress = 0f;
    private float textStyleProgress = 0f;
    private float pillsStyleProgress = 0f;
    private float iconProgress = 0f, titleProgress = 0f;
    private float counterProgress = 0f;
    private int oldStyle, currentStyle;
    private String allChatsTabName;
    private String allChatsTabIcon;

    private ValueAnimator animator;

    private final String[][] filters = new String[][]{
            {LocaleController.getString("FilterAllChats", R.string.FilterAllChats), "\uD83D\uDCAC"},
            {LocaleController.getString("FilterGroups", R.string.FilterGroups), "\uD83D\uDC65"},
            {LocaleController.getString("FilterBots", R.string.FilterBots), "\uD83E\uDD16"},
            {LocaleController.getString("FilterChannels", R.string.FilterChannels), "\uD83D\uDCE2"},
            {LocaleController.getString("FilterNameNonMuted", R.string.FilterNameNonMuted), "\uD83D\uDD14"},
            {LocaleController.getString("FilterContacts", R.string.FilterContacts), "\uD83C\uDFE0"},
            {LocaleController.getString("FilterUnread", R.string.FilterUnread), "\u2705"},
            {LocaleController.getString("FilterNonContacts", R.string.FilterNonContacts), "\uD83C\uDFAD"},
    };

    public FoldersPreviewCell(Context context) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_switchTrack), 0x3F));
        outlinePaint.setStrokeWidth(Math.max(2, dp(1f)));

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

                rect.set(0, 0, w, h);
                Theme.dialogs_onlineCirclePaint.setColor(Color.argb(20, r, g, b));
                canvas.drawRoundRect(rect, dp(8), dp(8), Theme.dialogs_onlineCirclePaint);

                float stroke = outlinePaint.getStrokeWidth() / 2;
                rect.set(stroke, stroke, w - stroke, h - stroke);
                canvas.drawRoundRect(rect, dp(8), dp(8), outlinePaint);

                float startY = h - dp(4) - dpf2(4.5f * chipsStyleProgress) - stroke;

                @SuppressLint("DrawAllocation") Path tab = new Path();
                tab.addRect(0, startY + dp(4), getMeasuredWidth(), startY + dp(10), Path.Direction.CCW);
                canvas.clipPath(tab, Region.Op.DIFFERENCE);

                textPaint.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));

                float startX = dp(25);
                for (int i = 0; i < filters.length; i++) {
                    textPaint.setTextSize(dp(15));
                    if (i == 0) {
                        textPaint.setColor(ColorUtils.blendARGB(0x00, Theme.getColor(Theme.key_windowBackgroundWhiteValueText), hideAllChatsProgress));
                        textPaint.setTextScaleX(hideAllChatsProgress * titleProgress);
                        Theme.dialogs_onlineCirclePaint.setColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhiteValueText), ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteValueText), 0x2F), chipsStyleProgress));
                        Theme.dialogs_onlineCirclePaint.setColor(ColorUtils.blendARGB(0x00, Theme.dialogs_onlineCirclePaint.getColor(), hideAllChatsProgress));
                    } else {
                        textPaint.setColor(ColorUtils.blendARGB(0x00, color, titleProgress));
                        textPaint.setTextScaleX(titleProgress);
                    }
                    String name = i == 0 ? allChatsTabName : filters[i][0];
                    Drawable icon = context.getDrawable(FolderIcons.getTabIcon(i == 0 ? allChatsTabIcon : filters[i][1])).mutate();
                    icon.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(0x00, i == 0 ? textPaint.getColor() : color, iconProgress), PorterDuff.Mode.MULTIPLY));
                    float sw = textPaint.measureText(name) + dp(30 + 4) * iconProgress + (i == 0 ? dpf2(24) * counterProgress : 1) + 14 * (1 - iconProgress) * titleProgress - dp(4) * iconProgress * (1 - titleProgress) * counterProgress;
                    if (i == 0) {
                        canvas.drawRoundRect(
                                startX,
                                startY + dpf2(6) * textStyleProgress - dpf2(37.5f) * chipsStyleProgress,
                                startX + sw + dpf2(4) * (1 - titleProgress) * (1 - counterProgress) + dpf2(22) * chipsStyleProgress,
                                startY + dp(8) - dpf2(4) * roundedStyleProgress - dpf2(9.5f) * chipsStyleProgress,
                                dpf2(8 + 15 * pillsStyleProgress),
                                dpf2(8 + 15 * pillsStyleProgress),
                                Theme.dialogs_onlineCirclePaint);
                        float iconOffset = startX + dpf2(6) * (1 - titleProgress) * (1 - counterProgress) + dpf2(11) * chipsStyleProgress;
                        icon.setBounds((int) (iconOffset), (int) h / 2 - dp(13), (int) (dpf2(26) * iconProgress * hideAllChatsProgress + iconOffset), (int) h / 2 + dp(13));
                        canvas.drawText(name, startX + dp(30 * iconProgress) + dpf2(10) * chipsStyleProgress + 7f * (1 - iconProgress) * titleProgress, startY - dp(14), textPaint);
                        textPaint.setTextScaleX(counterProgress);
                        textPaint.setTextSize(dp(14 * hideAllChatsProgress * counterProgress));
                        textPaint.setColor(ColorUtils.blendARGB(0x00, Color.argb(20, r, g, b), counterProgress));
                        Path path = new Path();
                        textPaint.getTextPath("3", 0, 1, (int) (startX + sw - dpf2(15.5f) + dpf2(12) * chipsStyleProgress - dp(1) * (1 - titleProgress)), (int) (startY - dpf2(15f)), path);
                        canvas.clipPath(path, Region.Op.DIFFERENCE);

                        textPaint.setColor(ColorUtils.blendARGB(0x00, Theme.getColor(Theme.key_windowBackgroundWhiteValueText), counterProgress * hideAllChatsProgress));
                        canvas.drawCircle(startX + sw - dpf2(11.5f) + dpf2(12) * chipsStyleProgress - dp(1) * (1 - titleProgress), h / 2, dp(10 * counterProgress * hideAllChatsProgress), textPaint);

                        startX += dp(25) + sw + dpf2(22) * chipsStyleProgress;
                    } else {
                        icon.setBounds((int) startX, (int) h / 2 - dp(13), (int) startX + dp(26 * iconProgress), (int) h / 2 + dp(13));
                        canvas.drawText(name, startX + dp(30) * iconProgress, startY - dp(14), textPaint);
                        startX += dp(25) + sw + dpf2(5) * chipsStyleProgress;
                    }
                    icon.draw(canvas);
                }
            }
        };
        preview.setWillNotDraw(false);
        addView(preview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.CENTER, 21, 15, 21, 21));
        updateTabStyle(false);
        updateTabIcons(false);
        updateTabTitle(false);
        updateAllChatsTabName(false);
        updateTabCounter(false);
    }

    public void updateAllChatsTabName(boolean animate) {
        if (Objects.equals(allChatsTabName, getAllChatsTabName()) && animate)
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
                    allChatsTabName = getAllChatsTabName();
                    allChatsTabIcon = getAllChatsTabIcon();
                    animator.setFloatValues(0f, 1f);
                    animator.removeAllListeners();
                    animator.start();
                }
            });
            animator.start();
        } else {
            allChatsTabName = getAllChatsTabName();
            allChatsTabIcon = getAllChatsTabIcon();
            hideAllChatsProgress = 1f;
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
                switch (currentStyle) {
                    case 1:
                        roundedStyleProgress = (Float) animation.getAnimatedValue();
                        break;
                    case 2:
                        textStyleProgress = (Float) animation.getAnimatedValue();
                        break;
                    case 3:
                        if (oldStyle != 4)
                            chipsStyleProgress = (Float) animation.getAnimatedValue();
                        break;
                    case 4:
                        pillsStyleProgress = (Float) animation.getAnimatedValue();
                        if (oldStyle != 3)
                            chipsStyleProgress = pillsStyleProgress;
                        break;
                }
                invalidate();
                requestLayout();
            });

            animator = ValueAnimator.ofFloat(1f, 0f).setDuration(250);
            animator.setStartDelay(100);
            animator.setInterpolator(Easings.easeInOutQuad);
            animator.addUpdateListener(animation -> {
                switch (oldStyle) {
                    case 1:
                        roundedStyleProgress = (Float) animation.getAnimatedValue();
                        break;
                    case 2:
                        textStyleProgress = (Float) animation.getAnimatedValue();
                        break;
                    case 3:
                        if (currentStyle != 4)
                            chipsStyleProgress = (Float) animation.getAnimatedValue();
                        break;
                    case 4:
                        pillsStyleProgress = (Float) animation.getAnimatedValue();
                        if (currentStyle != 3)
                            chipsStyleProgress = (Float) animation.getAnimatedValue();
                        break;
                }
                invalidate();
            });

            animator.start();
            def.start();
        } else {
            switch (currentStyle) {
                case 1:
                    roundedStyleProgress = 1f;
                    break;
                case 2:
                    textStyleProgress = 1f;
                    break;
                case 3:
                    chipsStyleProgress = 1f;
                    break;
                case 4:
                    chipsStyleProgress = 1f;
                    pillsStyleProgress = 1f;
                    break;
            }
            invalidate();
        }

    }

    public void updateTabTitle(boolean animate) {
        float to = ExteraConfig.tabIcons != 2 ? 1 : 0;
        if (to == titleProgress && animate)
            return;
        if (animate) {
            animator = ValueAnimator.ofFloat(titleProgress, to).setDuration(250);
            animator.setInterpolator(Easings.easeInOutQuad);
            animator.addUpdateListener(animation -> {
                titleProgress = (Float) animation.getAnimatedValue();
                invalidate();
            });
            animator.start();
        } else {
            titleProgress = to;
            invalidate();
        }
    }

    public void updateTabIcons(boolean animate) {
        float to = ExteraConfig.tabIcons != 1 ? 1 : 0;
        if (to == iconProgress && animate)
            return;
        if (animate) {
            animator = ValueAnimator.ofFloat(iconProgress, to).setDuration(250);
            animator.setInterpolator(Easings.easeInOutQuad);
            animator.addUpdateListener(animation -> {
                iconProgress = (Float) animation.getAnimatedValue();
                invalidate();
            });
            animator.start();
        } else {
            iconProgress = to;
            invalidate();
        }
    }

    public void updateTabCounter(boolean animate) {
        float to = ExteraConfig.tabCounter ? 1 : 0;
        if (to == counterProgress && animate)
            return;
        if (animate) {
            animator = ValueAnimator.ofFloat(counterProgress, to).setDuration(250);
            animator.setInterpolator(Easings.easeInOutQuad);
            animator.addUpdateListener(animation -> {
                counterProgress = (Float) animation.getAnimatedValue();
                invalidate();
            });
            animator.start();
        } else {
            counterProgress = to;
            invalidate();
        }
    }

    private String getAllChatsTabName() {
        return ExteraConfig.hideAllChats ? filters[6][0] : filters[0][0];
    }

    private String getAllChatsTabIcon() {
        return ExteraConfig.hideAllChats ? filters[6][1] : filters[0][1];
    }

    @Override
    public void invalidate() {
        super.invalidate();
        preview.invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!ExteraConfig.disableDividers)
            canvas.drawLine(LocaleController.isRTL ? 0 : dp(21), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? dp(21) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(86 + 8 * chipsStyleProgress), MeasureSpec.EXACTLY));
    }
}
