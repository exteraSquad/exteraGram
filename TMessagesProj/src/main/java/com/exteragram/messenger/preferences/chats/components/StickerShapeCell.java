/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.preferences.chats.components;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.text.TextPaint;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.preferences.components.CustomPreferenceCell;
import com.exteragram.messenger.preferences.components.PreviewBackgroundDrawable;
import com.exteragram.messenger.preferences.components.PreviewColors;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

public class StickerShapeCell extends LinearLayout implements CustomPreferenceCell {

    private final StickerShape[] stickerShape = new StickerShape[3];

    public StickerShapeCell(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        setPadding(AndroidUtilities.dp(13), AndroidUtilities.dp(10), AndroidUtilities.dp(13), 0);

        for (int a = 0; a < 3; a++) {
            boolean rounded = a == 1;
            boolean roundedAsMsg = a == 2;
            stickerShape[a] = new StickerShape(context, rounded, roundedAsMsg);
            ScaleStateListAnimator.apply(stickerShape[a], 0.03f, 1.5f);
            addView(stickerShape[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0.5f, 8, 0, 8, 0));
            int finalA = a;
            stickerShape[a].setOnClickListener(v -> {
                for (int b = 0; b < 3; b++) {
                    stickerShape[b].setSelected(v == stickerShape[b], true);
                }
                ExteraConfig.setStickerShape(finalA);
                updateStickerPreview();
            });
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        for (int a = 0; a < 3; a++) {
            stickerShape[a].invalidate();
        }
    }

    protected void updateStickerPreview() {
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(130), MeasureSpec.EXACTLY));
    }

    @Override
    public boolean isSameCell(@Nullable Object object) {
        if (this == object) return true;
        if (!(object instanceof StickerShapeCell other)) return false;
        return stickerShape == other.stickerShape;
    }

    private static class StickerShape extends FrameLayout {

        private final boolean isRounded;
        private final boolean isRoundedAsMsg;
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final PreviewBackgroundDrawable backgroundDrawable = new PreviewBackgroundDrawable(10);
        private float progress;

        public StickerShape(Context context, boolean rounded, boolean roundedAsMsg) {
            super(context);
            setWillNotDraw(false);

            isRounded = rounded;
            isRoundedAsMsg = roundedAsMsg;

            textPaint.setTextSize(AndroidUtilities.dp(13));

            setSelected(!isRounded && !isRoundedAsMsg && ExteraConfig.getStickerShape() == 0 || isRounded && ExteraConfig.getStickerShape() == 1 || isRoundedAsMsg && ExteraConfig.getStickerShape() == 2, false);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            backgroundDrawable.setBounds(0, 0, getMeasuredWidth(), AndroidUtilities.dp(80));
            backgroundDrawable.draw(canvas);

            String text = isRounded ? LocaleController.getString(R.string.StickerShapeRounded) : isRoundedAsMsg ? LocaleController.getString(R.string.StickerShapeRoundedMsg) : LocaleController.getString(R.string.Default);
            int width = (int) Math.ceil(textPaint.measureText(text));

            canvas.drawText(text, (getMeasuredWidth() - width) >> 1, AndroidUtilities.dp(102), textPaint);

            rect.set(AndroidUtilities.dp(10), AndroidUtilities.dp(10), getMeasuredWidth() - AndroidUtilities.dp(10), AndroidUtilities.dp(70));
            Theme.dialogs_onlineCirclePaint.setColor(PreviewColors.getMockColor(false));
            if (!isRounded && !isRoundedAsMsg) {
                canvas.drawRoundRect(rect, AndroidUtilities.dp(0), AndroidUtilities.dp(0), Theme.dialogs_onlineCirclePaint);
            } else if (isRounded) {
                canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), Theme.dialogs_onlineCirclePaint);
            } else {
                @SuppressLint("DrawAllocation") Rect rect1 = new Rect();
                rect.round(rect1);
                int rad = AndroidUtilities.dp(SharedConfig.bubbleRadius);
                @SuppressLint("DrawAllocation") ShapeDrawable defaultDrawable = new ShapeDrawable(new RoundRectShape(new float[]{rad, rad, rad, rad, rad, rad, rad / 3, rad / 3}, null, null));
                defaultDrawable.getPaint().setColor(Theme.dialogs_onlineCirclePaint.getColor());
                defaultDrawable.setBounds(rect1);
                defaultDrawable.draw(canvas);
            }
        }

        private void setProgress(float progress) {
            this.progress = progress;

            textPaint.setColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), Theme.getColor(Theme.key_windowBackgroundWhiteValueText), progress));
            textPaint.setTypeface(progress >= 0.5f ? AndroidUtilities.bold() : AndroidUtilities.regular());
            backgroundDrawable.setSelectionProgress(progress);
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
}
