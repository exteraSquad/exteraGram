/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.preferences.components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

@SuppressLint("ViewConstructor")
public class AvatarCornersPreviewCell extends FrameLayout {

    private final FrameLayout preview;

    private final RectF rect = new RectF();
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int lastWidth;

    private final AltSeekbar seekBar;

    private boolean needDivider = false;

    public interface onDrag {
        void run(float progress);
    }

    public AvatarCornersPreviewCell(Context context, INavigationLayout fragment) {
        super(context);
        setWillNotDraw(false);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        int startCornersSize = 0, endCornersSize = 30;
        seekBar = new AltSeekbar(context, (float p) -> {
            ExteraConfig.editor.putFloat("avatarCorners", ExteraConfig.avatarCorners = p).apply();
            invalidate();
            fragment.rebuildAllFragmentViews(false, false);
        }, startCornersSize, endCornersSize, LocaleController.getString("AvatarCorners", R.string.AvatarCorners), LocaleController.getString("AvatarCornersLeft", R.string.AvatarCornersLeft), LocaleController.getString("AvatarCornersRight", R.string.AvatarCornersRight));
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

                rect.set(0, 0, w, h);
                Theme.dialogs_onlineCirclePaint.setColor(Color.argb(20, r, g, b));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(8), AndroidUtilities.dp(8), Theme.dialogs_onlineCirclePaint);

                float stroke = outlinePaint.getStrokeWidth() / 2;
                rect.set(stroke, stroke, w - stroke, h - stroke);
                canvas.drawRoundRect(rect, AndroidUtilities.dp(8), AndroidUtilities.dp(8), outlinePaint);

                Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_chats_onlineCircle));
                canvas.drawCircle(AndroidUtilities.dp(68), h / 2.0f + AndroidUtilities.dpf2(20.5f), AndroidUtilities.dp(7), Theme.dialogs_onlineCirclePaint);

                Theme.dialogs_onlineCirclePaint.setColor(Color.argb(204, r, g, b));
                canvas.drawRoundRect(AndroidUtilities.dp(92), h / 2.0f - AndroidUtilities.dpf2(7.5f), w - AndroidUtilities.dp(90), h / 2.0f - AndroidUtilities.dpf2(15.5f), w / 2.0f, w / 2.0f, Theme.dialogs_onlineCirclePaint);

                @SuppressLint("DrawAllocation") Path online = new Path();
                online.addCircle(AndroidUtilities.dp(68), h / 2.0f + AndroidUtilities.dpf2(20.5f), AndroidUtilities.dp(12), Path.Direction.CCW);
                canvas.clipPath(online, Region.Op.DIFFERENCE);

                Theme.dialogs_onlineCirclePaint.setColor(Color.argb(90, r, g, b));
                canvas.drawRoundRect(AndroidUtilities.dp(92), h / 2.0f + AndroidUtilities.dpf2(7.5f), w - AndroidUtilities.dp(50), h / 2.0f + AndroidUtilities.dp(15.5f), w / 2.0f, w / 2.0f, Theme.dialogs_onlineCirclePaint);
                canvas.drawRoundRect(AndroidUtilities.dp(20), h / 2.0f - AndroidUtilities.dp(28), AndroidUtilities.dp(76), h / 2.0f + AndroidUtilities.dp(28), ExteraConfig.getAvatarCorners(56), ExteraConfig.getAvatarCorners(56), Theme.dialogs_onlineCirclePaint);
            }
        };
        preview.setWillNotDraw(false);
        addView(preview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.CENTER, 21, 112, 21, 21));
    }

    public void setNeedDivider(boolean needDivider) {
        this.needDivider = needDivider;
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
        if (!ExteraConfig.disableDividers && needDivider)
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(21), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(21) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(112 + 89 + 21), MeasureSpec.EXACTLY));
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (lastWidth != width) {
            lastWidth = width;
        }
    }
}
