/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.preferences.appearance.components;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.preferences.components.CustomPreferenceCell;
import com.exteragram.messenger.preferences.components.PreviewBackgroundDrawable;
import com.exteragram.messenger.preferences.components.PreviewColors;
import com.exteragram.messenger.utils.ui.FabUiHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Easings;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.util.Arrays;

public class FabShapeCell extends LinearLayout implements CustomPreferenceCell {

    private final FabShape[] fabShape = new FabShape[2];

    public FabShapeCell(Context context) {
        super(context);
        setWillNotDraw(false);
        setOrientation(HORIZONTAL);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        setPadding(AndroidUtilities.dp(13), AndroidUtilities.dp(15), AndroidUtilities.dp(13), AndroidUtilities.dp(21));

        for (int a = 0; a < 2; a++) {
            boolean square = a == 1;
            fabShape[a] = new FabShape(context, square);
            ScaleStateListAnimator.apply(fabShape[a], 0.03f, 1.5f);
            addView(fabShape[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0.5f, 8, 0, 8, 0));
            fabShape[a].setOnClickListener(v -> {
                for (int b = 0; b < 2; b++) {
                    fabShape[b].setSelected(v == fabShape[b], true);
                }
                ExteraConfig.setSquareFab(square);
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
        canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(110), MeasureSpec.EXACTLY));
    }

    @Override
    public boolean isSameCell(@Nullable Object object) {
        if (this == object) return true;
        if (!(object instanceof FabShapeCell other)) return false;
        return Arrays.equals(fabShape, other.fabShape);
    }

    private static class FabShape extends FrameLayout {

        private final RectF rect = new RectF();
        private final PreviewBackgroundDrawable backgroundDrawable = new PreviewBackgroundDrawable(12);
        private final boolean squareFab;
        private float progress;

        public FabShape(Context context, boolean square) {
            super(context);
            setWillNotDraw(false);

            squareFab = square;
            setBackground(backgroundDrawable);

            setSelected(square && ExteraConfig.getSquareFab() || !square && !ExteraConfig.getSquareFab(), false);
        }

        @SuppressLint("DrawAllocation")
        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            int cx = AndroidUtilities.dp(22);
            int cy = AndroidUtilities.dp(21);
            int rad = cx / 2;
            for (int a = 0; a < 2; a++) {
                cy += AndroidUtilities.dp(a == 0 ? 0 : 32);
                Theme.dialogs_onlineCirclePaint.setColor(PreviewColors.getMockColor(false));
                canvas.drawRoundRect(cx - rad, cy - rad, cx + rad, cy + rad, ExteraConfig.getAvatarCorners(rad * 2, true), ExteraConfig.getAvatarCorners(rad * 2, true), Theme.dialogs_onlineCirclePaint);

                for (int i = 0; i < 2; i++) {
                    Theme.dialogs_onlineCirclePaint.setColor(PreviewColors.getMockColor(i == 0));
                    rect.set(AndroidUtilities.dp(41), cy - AndroidUtilities.dp(7 - i * 10), getMeasuredWidth() - AndroidUtilities.dp(i == 0 ? 70 : 55), cy - AndroidUtilities.dp(7 - 4 - i * 10));
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), Theme.dialogs_onlineCirclePaint);
                }
            }
            Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            rect.set(getMeasuredWidth() - AndroidUtilities.dp(42), getMeasuredHeight() - AndroidUtilities.dp(12), getMeasuredWidth() - AndroidUtilities.dp(12), getMeasuredHeight() - AndroidUtilities.dp(42));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(squareFab ? 9 : 100), AndroidUtilities.dp(squareFab ? 9 : 100), Theme.dialogs_onlineCirclePaint);

            Drawable edit = ContextCompat.getDrawable(getContext(), R.drawable.filled_fab_compose_32);
            if (edit != null) {
                edit.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.SRC_IN));
                float scale = rect.width() / AndroidUtilities.dp(FabUiHelper.getFabSizeDp());
                int iconWidth = Math.round(edit.getIntrinsicWidth() * scale);
                int iconHeight = Math.round(edit.getIntrinsicHeight() * scale);
                int iconLeft = (int) (rect.centerX() - iconWidth / 2f);
                int iconTop = (int) (rect.centerY() - iconHeight / 2f);
                edit.setBounds(iconLeft, iconTop, iconLeft + iconWidth, iconTop + iconHeight);
                edit.draw(canvas);
            }
        }

        private void setProgress(float progress) {
            this.progress = progress;
            backgroundDrawable.setSelectionProgress(progress);
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
