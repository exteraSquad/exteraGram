/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.custom.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.exteragram.messenger.pillstack.custom.CustomPill;
import com.exteragram.messenger.pillstack.custom.CustomPillSpec;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

@SuppressLint("ViewConstructor")
public class PillPreviewView extends LinearLayout {

    private final Theme.ResourcesProvider resourcesProvider;
    private final FrameLayout field;
    private final ImageView searchIcon;
    private final TextView hintView;
    private final CustomPill pill;
    private final TextView statusView;

    public PillPreviewView(Context context, Theme.ResourcesProvider resourcesProvider, CustomPillSpec spec) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setOrientation(VERTICAL);

        field = new FrameLayout(context) {
            @Override
            protected void measureChildWithMargins(View child, int parentWidthMeasureSpec, int widthUsed, int parentHeightMeasureSpec, int heightUsed) {
                if (child == pill) {
                    widthUsed += dp(48) + hintView.getMeasuredWidth() + dp(8);
                }
                super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed);
            }
        };
        addView(field, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40, 16, 16, 16, 0));

        searchIcon = new ImageView(context);
        searchIcon.setScaleType(ImageView.ScaleType.CENTER);
        searchIcon.setImageResource(R.drawable.outline_search_1_24);
        field.addView(searchIcon, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 12, 0, 12, 0));

        hintView = new TextView(context);
        hintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        hintView.setTypeface(AndroidUtilities.regular());
        hintView.setText(LocaleController.getString(R.string.Search));
        hintView.setSingleLine();
        hintView.setEllipsize(TextUtils.TruncateAt.END);
        field.addView(hintView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 48, 0, 48, 0));

        pill = new CustomPill(context, resourcesProvider, spec);
        field.addView(pill, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), 6, 0, 6, 0));

        statusView = new TextView(context);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        statusView.setTypeface(AndroidUtilities.regular());
        statusView.setGravity(Gravity.CENTER);
        statusView.setSingleLine();
        statusView.setEllipsize(TextUtils.TruncateAt.END);
        addView(statusView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 10, 16, 14));

        updateColors();
    }

    public CustomPill getPill() {
        return pill;
    }

    public void setStatus(CharSequence status) {
        statusView.setText(status);
    }

    public void updateColors() {
        int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        field.setBackground(Theme.createRoundRectDrawable(dp(20), Theme.multAlpha(textColor, Theme.isCurrentThemeDark() ? 0.07f : 0.05f)));
        searchIcon.setColorFilter(Theme.multAlpha(textColor, 0.6f), PorterDuff.Mode.MULTIPLY);
        hintView.setTextColor(Theme.multAlpha(textColor, 0.5f));
        statusView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        pill.updateColors();
    }
}
