/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.components;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.LayoutHelper;

public class FabShapeCell extends LinearLayout {

    private static class FabShape extends FrameLayout {

        private final RectF rect = new RectF();
        private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean squareFab;
        private float progress;

        public FabShape(Context context, boolean square) {
            super(context);
            setWillNotDraw(false);

            squareFab = square;

            outlinePaint.setStyle(Paint.Style.STROKE);
            outlinePaint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_switchTrack), 0x3F));
            outlinePaint.setStrokeWidth(Math.max(2, AndroidUtilities.dp(1f)));

            setSelected(square && ExteraConfig.squareFab || !square && !ExteraConfig.squareFab, false);
        }

        @SuppressLint("DrawAllocation")
        @Override
        protected void onDraw(Canvas canvas) {
            int color = Theme.getColor(Theme.key_switchTrack);
            int r = Color.red(color);
            int g = Color.green(color);
            int b = Color.blue(color);

            rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
            Theme.dialogs_onlineCirclePaint.setColor(Color.argb(20, r, g, b));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(8), AndroidUtilities.dp(8), Theme.dialogs_onlineCirclePaint);

            float stroke = outlinePaint.getStrokeWidth() / 2;
            rect.set(stroke, stroke, getMeasuredWidth() - stroke, getMeasuredHeight() - stroke);
            canvas.drawRoundRect(rect, AndroidUtilities.dp(8), AndroidUtilities.dp(8), outlinePaint);

            int cx = AndroidUtilities.dp(22);
            int cy = AndroidUtilities.dp(21);
            int rad = cx / 2;
            for (int a = 0; a < 2; a++) {
                cy += AndroidUtilities.dp(a == 0 ? 0 : 32);
                Theme.dialogs_onlineCirclePaint.setColor(Color.argb(90, r, g, b));
                canvas.drawRoundRect(cx - rad, cy - rad, cx + rad, cy + rad, ExteraConfig.getAvatarCorners(rad * 2, true), ExteraConfig.getAvatarCorners(rad * 2, true), Theme.dialogs_onlineCirclePaint);

                for (int i = 0; i < 2; i++) {
                    Theme.dialogs_onlineCirclePaint.setColor(Color.argb(i == 0 ? 204 : 90, r, g, b));
                    rect.set(AndroidUtilities.dp(41), cy - AndroidUtilities.dp(7 - i * 10), getMeasuredWidth() - AndroidUtilities.dp(i == 0 ? 90 : 70), cy - AndroidUtilities.dp(7 - 4 - i * 10));
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), Theme.dialogs_onlineCirclePaint);
                }
            }
            Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_chats_actionBackground));
            rect.set(getMeasuredWidth() - AndroidUtilities.dp(42), getMeasuredHeight() - AndroidUtilities.dp(12), getMeasuredWidth() - AndroidUtilities.dp(12), getMeasuredHeight() - AndroidUtilities.dp(42));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(squareFab ? 9 : 100), AndroidUtilities.dp(squareFab ? 9 : 100), Theme.dialogs_onlineCirclePaint);

            Drawable edit = ContextCompat.getDrawable(getContext(), R.drawable.floating_pencil);
            edit.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.MULTIPLY));
            edit.setBounds(getMeasuredWidth() - AndroidUtilities.dp(33), getMeasuredHeight() - AndroidUtilities.dp(32.5f), getMeasuredWidth() - AndroidUtilities.dp(21), getMeasuredHeight() - AndroidUtilities.dp(20.5f));
            edit.draw(canvas);
        }

        private void setProgress(float progress) {
            this.progress = progress;

            outlinePaint.setColor(ColorUtils.blendARGB(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_switchTrack), 0x3F), Theme.getColor(Theme.key_windowBackgroundWhiteValueText), progress));
            outlinePaint.setStrokeWidth(Math.max(2, AndroidUtilities.dp(AndroidUtilities.lerp(1f, 2f, progress))));
            invalidate();
        }

        private void setSelected(boolean selected, boolean animate) {
            float to = selected ? 1 : 0;
            if (to == progress && animate) {
                return;
            }

            if (animate) {
                ValueAnimator animator = ValueAnimator.ofFloat(progress, to).setDuration(250);
                animator.setInterpolator(Easings.easeInOutQuad);
                animator.addUpdateListener(animation -> setProgress((Float) animation.getAnimatedValue()));
                animator.start();
            } else {
                setProgress(to);
            }
        }
    }

    private final FabShape[] fabShape = new FabShape[2];

    public FabShapeCell(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        setPadding(AndroidUtilities.dp(13), AndroidUtilities.dp(15), AndroidUtilities.dp(13), AndroidUtilities.dp(21));

        for (int a = 0; a < 2; a++) {
            boolean square = a == 1;
            fabShape[a] = new FabShape(context, square);
            addView(fabShape[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0.5f, 8, 0, 8, 0));
            fabShape[a].setOnClickListener(v -> {
                for (int b = 0; b < 2; b++) {
                    fabShape[b].setSelected(v == fabShape[b], true);
                }
                ExteraConfig.editor.putBoolean("squareFab", ExteraConfig.squareFab = square).apply();
                rebuildFragments();
            });
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        for (int a = 0; a < 2; a++) {
            fabShape[a].invalidate();
        }
    }

    protected void rebuildFragments() {
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!ExteraConfig.disableDividers)
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(21), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(21) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(110), MeasureSpec.EXACTLY));
    }
}
