/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.components.supporter;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;

public class SupporterFeatureCell extends FrameLayout {

    private final Theme.ResourcesProvider resourcesProvider;
    private final FrameLayout content;
    private final LinkSpanDrawable.LinksTextView subtitleView;
    private final ImageView nextIcon;

    public SupporterFeatureCell(Context context, int icon, CharSequence title, CharSequence subtitle, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        final boolean isRtl = LocaleController.isRTL;
        final int gravity = isRtl ? Gravity.RIGHT : Gravity.LEFT;

        content = new FrameLayout(context);
        addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        ImageView iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        iconView.setImageResource(icon);
        iconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), PorterDuff.Mode.SRC_IN));
        content.addView(iconView, LayoutHelper.createFrame(24, 24, gravity | Gravity.TOP, isRtl ? 0 : 21, 10, isRtl ? 21 : 0, 0));

        LinearLayout textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setGravity(gravity);
        textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        subtitleView = new LinkSpanDrawable.LinksTextView(context, resourcesProvider);
        subtitleView.setText(subtitle);
        subtitleView.setTypeface(AndroidUtilities.regular());
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        subtitleView.setLinkTextColor(Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider));
        subtitleView.setLineSpacing(dp(2), 1f);
        subtitleView.setGravity(gravity);
        textLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));

        content.addView(textLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, gravity | Gravity.TOP, isRtl ? 48 : 64, 8, isRtl ? 64 : 48, 9));

        nextIcon = new ImageView(context);
        nextIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        nextIcon.setImageResource(R.drawable.msg_arrowright);
        nextIcon.setColorFilter(Theme.getColor(Theme.key_switchTrack, resourcesProvider));
        nextIcon.setScaleX(isRtl ? -1 : 1);
        nextIcon.setVisibility(View.GONE);
        content.addView(nextIcon, LayoutHelper.createFrame(24, 24, (isRtl ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 18, 0, 18, 0));
    }

    public void setSubtitle(CharSequence text) {
        subtitleView.setText(text);
    }

    public void setAction(Runnable action) {
        nextIcon.setVisibility(action != null ? View.VISIBLE : View.GONE);
        content.setBackground(action != null ? Theme.getSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), false) : null);
        if (action != null) {
            content.setOnClickListener(v -> action.run());
        } else {
            content.setOnClickListener(null);
            content.setClickable(false);
        }
    }
}
