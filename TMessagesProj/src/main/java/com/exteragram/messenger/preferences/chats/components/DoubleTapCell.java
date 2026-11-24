/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.preferences.chats.components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.HapticFeedbackConstants;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.utils.chats.DoubleTapUtils;
import com.exteragram.messenger.preferences.components.CustomPreferenceCell;
import com.exteragram.messenger.preferences.components.PreviewColors;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.MessageDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.LayoutHelper;

public class DoubleTapCell extends LinearLayout implements CustomPreferenceCell {

    private static final int[] ICON_WIDTH = new int[]{AndroidUtilities.dp(12), AndroidUtilities.dp(12)};
    private final RectF rect = new RectF();
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint[] circleOutlinePaint = new Paint[2];
    private final MessageDrawable[] messages = new MessageDrawable[]{
            new MessageDrawable(MessageDrawable.TYPE_TEXT, false, false),
            new MessageDrawable(MessageDrawable.TYPE_TEXT, true, false)
    };
    private final ValueAnimator[] animator = new ValueAnimator[2];
    private final ValueAnimator[] circleAnimator = new ValueAnimator[2];
    private final ValueAnimator[] circleSizeAnimator = new ValueAnimator[2];
    private final float[] circleSizeProgress = new float[4];
    private final float[] iconChangingProgress = new float[2];
    private final float[] circleProgress = new float[4];
    private final int[] actionIcon = new int[2];

    private final FrameLayout preview;

    public DoubleTapCell(Context context) {
        super(context);
        setWillNotDraw(false);
        setOrientation(VERTICAL);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        setPadding(AndroidUtilities.dp(13), 0, AndroidUtilities.dp(13), AndroidUtilities.dp(10));

        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(AndroidUtilities.dp(1f) / 2f);
        outlinePaint.setColor(PreviewColors.getOutlineColor());

        preview = new FrameLayout(context) {
            @SuppressLint("DrawAllocation")
            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                Rect rect1 = new Rect();

                float stroke = outlinePaint.getStrokeWidth() / 2;

                for (int i = 0; i < 2; i++) {
                    if (i == 0) {
                        rect.set(stroke + AndroidUtilities.dp(8), stroke + AndroidUtilities.dp(10), getMeasuredWidth() / 2f - AndroidUtilities.dp(8) - stroke, AndroidUtilities.dp(75) - stroke);
                    } else {
                        canvas.translate(0, AndroidUtilities.dp(80));
                        rect.set(stroke + getMeasuredWidth() / 2f + AndroidUtilities.dp(8), stroke + AndroidUtilities.dp(5), getMeasuredWidth() - AndroidUtilities.dp(8) - stroke, AndroidUtilities.dp(70) - stroke);
                    }
                    rect.round(rect1);
                    messages[i].setBounds(rect1);
                    Theme.dialogs_onlineCirclePaint.setColor(PreviewColors.getBackgroundColor());
                    messages[i].draw(canvas, Theme.dialogs_onlineCirclePaint);
                    messages[i].draw(canvas, outlinePaint);

                    for (int j = 0; j < 2; j++) {
                        circleOutlinePaint[j] = new Paint(Paint.ANTI_ALIAS_FLAG);
                        circleOutlinePaint[j].setStyle(Paint.Style.STROKE);
                        circleOutlinePaint[j].setColor(ColorUtils.blendARGB(0x00, PreviewColors.getMockColor(true), circleProgress[i + 2 * j]));
                        circleOutlinePaint[j].setStrokeWidth(AndroidUtilities.dp(1.5f) * circleProgress[i + 2 * j] * circleProgress[i + 2 * j]);
                        canvas.drawCircle((i == 0 ? 1 : 3) * getMeasuredWidth() / 4f, getMeasuredHeight() / 4f + AndroidUtilities.dpf2(i == 0 ? 3f : -2f), AndroidUtilities.dp(25 - 6 * j) * circleSizeProgress[i + 2 * j], circleOutlinePaint[j]);
                    }

                    Drawable icon = ContextCompat.getDrawable(context, actionIcon[i]);
                    if (i == 0)
                        icon.setBounds(getMeasuredWidth() / 4 - ICON_WIDTH[i], (int) (getMeasuredHeight() / 4 - ICON_WIDTH[i] + AndroidUtilities.dpf2(3f)), getMeasuredWidth() / 4 + ICON_WIDTH[i], (int) (getMeasuredHeight() / 4 + ICON_WIDTH[i] + AndroidUtilities.dpf2(3f)));
                    else
                        icon.setBounds(3 * getMeasuredWidth() / 4 - ICON_WIDTH[i], (int) (getMeasuredHeight() / 4 - ICON_WIDTH[i] - AndroidUtilities.dpf2(2f)), 3 * getMeasuredWidth() / 4 + ICON_WIDTH[i], (int) (getMeasuredHeight() / 4 + ICON_WIDTH[i] - AndroidUtilities.dpf2(2f)));

                    icon.setBounds(
                            icon.getBounds().left - AndroidUtilities.dp(4 - 4 * iconChangingProgress[i]),
                            icon.getBounds().top - AndroidUtilities.dp(4 - 4 * iconChangingProgress[i]),
                            icon.getBounds().right + AndroidUtilities.dp(4 - 4 * iconChangingProgress[i]),
                            icon.getBounds().bottom + AndroidUtilities.dp(4 - 4 * iconChangingProgress[i])
                    );
                    icon.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(0x00, Theme.getColor(Theme.key_chats_menuItemIcon), iconChangingProgress[i]), PorterDuff.Mode.MULTIPLY));
                    icon.draw(canvas);
                }
            }
        };
        preview.setWillNotDraw(false);
        addView(preview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        updateIcons(0, false);
    }

    @SuppressLint("Recycle")
    public void updateIcons(int inv, boolean animate) {
        for (int i = 0; i < 2; i++) {
            if (i == 0 && inv == 2 || i == 1 && inv == 1) continue;
            if (animate) {
                int finalI = i;
                for (int j = 0; j < 2; j++) {
                    int finalJ = j;
                    circleSizeAnimator[j] = ValueAnimator.ofFloat(0f, 1f).setDuration(1300);
                    circleSizeAnimator[j].setStartDelay(j * 60L);
                    circleSizeAnimator[j].setInterpolator(Easings.easeInOutQuad);
                    circleSizeAnimator[j].addUpdateListener(animation -> {
                        circleSizeProgress[finalJ * 2 + finalI] = (Float) animation.getAnimatedValue();
                        invalidate();
                    });

                    circleAnimator[j] = ValueAnimator.ofFloat(0f, 1f).setDuration(700);
                    circleAnimator[j].setStartDelay(150 + j * 80L);
                    circleAnimator[j].setInterpolator(Easings.easeInOutQuad);
                    circleAnimator[j].addUpdateListener(animation -> {
                        circleProgress[finalJ * 2 + finalI] = (Float) animation.getAnimatedValue();
                        invalidate();
                    });
                    circleAnimator[j].addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            circleAnimator[finalJ].setFloatValues(1f, 0f);
                            circleAnimator[finalJ].setDuration(700);
                            circleAnimator[finalJ].removeAllListeners();
                            circleAnimator[finalJ].start();
                        }
                    });
                    circleSizeAnimator[j].start();
                    circleAnimator[j].start();
                }

                animator[i] = ValueAnimator.ofFloat(1f, 0f).setDuration(250);
                animator[i].setInterpolator(Easings.easeInOutQuad);
                animator[i].addUpdateListener(animation -> {
                    iconChangingProgress[finalI] = (Float) animation.getAnimatedValue();
                    invalidate();
                });
                animator[i].addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        actionIcon[finalI] = DoubleTapUtils.getDoubleTapActionIcon(finalI == 0 ? ExteraConfig.getDoubleTapAction() : ExteraConfig.getDoubleTapActionOutOwner(), finalI == 1);
                        animator[finalI].setFloatValues(0f, 1f);
                        animator[finalI].removeAllListeners();
                        animator[finalI].addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                super.onAnimationEnd(animation);
                                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                            }
                        });
                        animator[finalI].start();
                    }
                });
                animator[i].start();
            } else {
                circleSizeProgress[i] = 0f;
                circleProgress[i] = 0f;
                iconChangingProgress[i] = 1f;
                actionIcon[i] = DoubleTapUtils.getDoubleTapActionIcon(i == 0 ? ExteraConfig.getDoubleTapAction() : ExteraConfig.getDoubleTapActionOutOwner(), i == 1);
                invalidate();
            }
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        preview.invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(170), MeasureSpec.EXACTLY));
    }

    @Override
    public boolean isSameCell(@Nullable Object object) {
        if (this == object) return true;
        if (!(object instanceof DoubleTapCell other)) return false;
        return actionIcon == other.actionIcon;
    }
}