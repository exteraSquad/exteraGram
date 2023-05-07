/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.preferences.components;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.exteragram.messenger.utils.AppUtils;
import com.exteragram.messenger.utils.MonetUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class InfoSettingsCell extends FrameLayout {

    public final TextView textView;

    public InfoSettingsCell(Context context) {
        super(context);

        Drawable arrow = ContextCompat.getDrawable(context, R.drawable.ic_logo_foreground).mutate();
        Theme.ThemeInfo theme = Theme.getActiveTheme();
        int color = BuildVars.isBetaApp() ? 0xff747F9F : 0xffF54142;

        if (theme.isMonet() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            color = MonetUtils.getColor(theme.isDark() ? "n1_800" : "a1_100");
            arrow.setColorFilter(new PorterDuffColorFilter(MonetUtils.getColor(theme.isDark() ? "a1_100" : "n2_700"), PorterDuff.Mode.MULTIPLY));
        } else {
            arrow.setAlpha((int) (70 * 2.55f));
        }

        ImageView logo = new ImageView(context);
        logo.setScaleType(ImageView.ScaleType.CENTER);
        logo.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(108), color));
        //TODO: logo.setBackground(new GradientArrowBackground(context, color));
        logo.setImageDrawable(arrow);
        addView(logo, LayoutHelper.createFrame(108, 108, Gravity.CENTER | Gravity.TOP, 0, 20, 0, 0));

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setText(String.format("%s | %s", AppUtils.getAppName(), BuildVars.BUILD_VERSION_STRING));
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setPadding(0, 0, 0, 0);
        textView.setGravity(Gravity.CENTER);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER | Gravity.TOP, 50, 145, 50, 0));

        TextView valueTextView = new TextView(context);
        valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        valueTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_REGULAR));
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        valueTextView.setText(LocaleController.getString("AboutExteraDescription", R.string.AboutExteraDescription));
        valueTextView.setGravity(Gravity.CENTER);
        valueTextView.setLines(0);
        valueTextView.setMaxLines(0);
        valueTextView.setSingleLine(false);
        valueTextView.setPadding(0, 0, 0, 0);
        addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER | Gravity.TOP, 60, 180, 60, 27));

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    }
}
