/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.drawer;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.exteragram.messenger.feed.FeedController;
import com.exteragram.messenger.utils.ui.UIUtil;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesStorage;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class DrawerMenuItemView extends FrameLayout {
    private static final int ITEM_HEIGHT_DP = 48;
    private static final int SELECTOR_RADIUS_DP = 12;
    private static final int ICON_SIZE_DP = 24;
    private static final int ICON_START_MARGIN_DP = 20;
    private static final int ICON_TO_TEXT_SPACING_DP = 24;
    private static final int TEXT_START_MARGIN_DP = ICON_START_MARGIN_DP + ICON_SIZE_DP + ICON_TO_TEXT_SPACING_DP;
    private static final int TEXT_END_MARGIN_DP = 16;
    private static final int TEXT_SIZE_DP = 15;

    private static final int COLOR_KEY_SELECTOR = Theme.key_listSelector;
    private static final int COLOR_KEY_ICON = Theme.key_windowBackgroundWhiteGrayIcon;
    private static final int COLOR_KEY_TEXT = Theme.key_windowBackgroundWhiteBlackText;

    private final ImageView iconView;
    private final TextView textView;
    private final DrawerUnreadBadge unreadBadge;
    private int layoutButtonId = Integer.MIN_VALUE;

    public DrawerMenuItemView(Context context) {
        super(context);
        setWillNotDraw(false);
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, dp(ITEM_HEIGHT_DP)));
        setBackground(createSelectorDrawable());
        UIUtil.applyScaleStateListAnimator(this, 16, false, false, 2, 0.04f, 1.5f);

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        iconView.setColorFilter(createIconColorFilter());
        addView(iconView, LayoutHelper.createFrame(ICON_SIZE_DP, ICON_SIZE_DP, Gravity.CENTER_VERTICAL | Gravity.LEFT, ICON_START_MARGIN_DP, 0, 0, 0));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DP);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setTextColor(Theme.getColor(COLOR_KEY_TEXT));
        textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, TEXT_START_MARGIN_DP, 0, TEXT_END_MARGIN_DP, 0));

        unreadBadge = new DrawerUnreadBadge();
    }

    public void setMenuItem(int layoutButtonId, int currentAccount, int iconRes, CharSequence text) {
        this.layoutButtonId = layoutButtonId;
        iconView.setImageResource(iconRes);
        textView.setText(text);
        updateUnreadCounter(currentAccount);
    }

    public void updateUnreadCounter(int currentAccount) {
        unreadBadge.bind(resolveUnreadCounter(currentAccount), textView);
        invalidate();
    }

    private int resolveUnreadCounter(int currentAccount) {
        if (layoutButtonId == MainMenuItem.ARCHIVE.getId()) {
            return MessagesStorage.getInstance(currentAccount).getArchiveUnreadCount();
        }
        if (layoutButtonId == MainMenuItem.FEED.getId()) {
            return FeedController.getInstance(currentAccount).getUnreadCount();
        }
        return 0;
    }

    public void updateColors() {
        setBackground(createSelectorDrawable());
        iconView.setColorFilter(createIconColorFilter());
        textView.setTextColor(Theme.getColor(COLOR_KEY_TEXT));
        invalidate();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        unreadBadge.draw(this, canvas);
    }

    private static Drawable createSelectorDrawable() {
        return Theme.createRadSelectorDrawable(
                Theme.getColor(COLOR_KEY_SELECTOR),
                SELECTOR_RADIUS_DP,
                SELECTOR_RADIUS_DP
        );
    }

    private static PorterDuffColorFilter createIconColorFilter() {
        return new PorterDuffColorFilter(Theme.getColor(COLOR_KEY_ICON), PorterDuff.Mode.SRC_IN);
    }
}
