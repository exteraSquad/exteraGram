/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.speech.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.StickerImageView;

public class LoadingModelView extends FrameLayout {

    StickerImageView imageView;
    AnimatedTextView percentsTextView;
    ProgressView progressView;
    TextView title, subtitle;

    public LoadingModelView(Context context) {
        super(context);

        imageView = new StickerImageView(context, UserConfig.selectedAccount);
        imageView.getImageReceiver().setAutoRepeat(1);
        imageView.setStickerPackName("UtyaDuck");
        imageView.setStickerNum(16);
        addView(imageView, LayoutHelper.createFrame(150, 150, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 16, 0, 0));

        percentsTextView = new AnimatedTextView(context, false, true, true);
        percentsTextView.setAnimationProperties(.35f, 0, 120, CubicBezierInterpolator.EASE_OUT);
        percentsTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        percentsTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        percentsTextView.setTextSize(AndroidUtilities.dp(24));
        percentsTextView.setTypeface(AndroidUtilities.bold());
        addView(percentsTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 32, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 16 + 150 + 16 - 6, 0, 0));

        progressView = new ProgressView(context);
        addView(progressView, LayoutHelper.createFrame(240, 5, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 16 + 150 + 16 + 28 + 16, 0, 0));

        title = new TextView(context);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title.setTypeface(AndroidUtilities.bold());
        title.setText(LocaleController.getString(R.string.DownloadingModel));
        addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 16 + 150 + 16 + 28 + 16 + 5 + 30, 0, 0));

        subtitle = new TextView(context);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitle.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitle.setText(LocaleController.getString(R.string.DownloadingModelInfo));
        addView(subtitle, LayoutHelper.createFrame(240, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 16 + 150 + 16 + 28 + 16 + 5 + 30 + 18 + 10, 0, 0));

        setProgress(0);
    }

    public void setProgress(float t) {
        int progress = (int) Math.ceil(MathUtils.clamp(t, 0, 1) * 100);
        percentsTextView.cancelAnimation();
        percentsTextView.setText(progress == 100 ? LocaleController.getString(R.string.ModelUnzipping) : String.format("%s%%", progress), !LocaleController.isRTL);
        progressView.setProgress(t);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(350), MeasureSpec.EXACTLY)
        );
    }

    static class ProgressView extends View {

        Paint in = new Paint(Paint.ANTI_ALIAS_FLAG), out = new Paint(Paint.ANTI_ALIAS_FLAG);
        float progress;
        AnimatedFloat progressT = new AnimatedFloat(this, 350, CubicBezierInterpolator.EASE_OUT);
        public ProgressView(Context context) {
            super(context);

            in.setColor(Theme.getColor(Theme.key_switchTrackChecked));
            out.setColor(Theme.multAlpha(Theme.getColor(Theme.key_switchTrackChecked), .2f));
        }

        public void setProgress(float t) {
            this.progress = t;
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);

            AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(3), AndroidUtilities.dp(3), out);

            AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth() * progressT.set(this.progress), getMeasuredHeight());
            canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(3), AndroidUtilities.dp(3), in);
        }
    }
}
