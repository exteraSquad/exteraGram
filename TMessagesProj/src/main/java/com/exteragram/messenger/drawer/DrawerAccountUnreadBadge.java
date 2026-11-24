/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.drawer;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;

import static org.telegram.messenger.AndroidUtilities.dp;

final class DrawerAccountUnreadBadge {
    private static final float BADGE_HEIGHT_DP = 23f;
    private static final float BADGE_RADIUS_DP = 11.5f;
    private static final float BADGE_RIGHT_MARGIN_DP = 12.5f;
    private static final float MIN_TEXT_WIDTH_DP = 10f;
    private static final int BADGE_HORIZONTAL_PADDING_DP = 14;
    private static final int TEXT_RIGHT_PADDING_DP = 12;

    private final RectF rect = new RectF();

    private int account = -1;
    private String text;
    private int textWidth;
    private int countWidth;
    private int badgeWidth;
    private boolean visible;

    void bind(int account, SimpleTextView textView) {
        this.account = account;
        update(textView);
    }

    void update(SimpleTextView textView) {
        visible = false;
        text = null;
        textWidth = 0;
        countWidth = 0;
        badgeWidth = 0;

        if (account < 0 || UserConfig.getActivatedAccountsCount() <= 1 || !NotificationsController.getInstance(account).showBadgeNumber) {
            textView.setRightPadding(0);
            return;
        }

        final int counter = MessagesStorage.getInstance(account).getMainUnreadCount();
        if (counter <= 0) {
            textView.setRightPadding(0);
            return;
        }

        visible = true;
        text = Integer.toString(counter);
        textWidth = (int) Math.ceil(Theme.dialogs_countTextPaint.measureText(text));
        countWidth = Math.max(dp(MIN_TEXT_WIDTH_DP), textWidth);
        badgeWidth = countWidth + dp(BADGE_HORIZONTAL_PADDING_DP);

        textView.setRightPadding(badgeWidth + dp(TEXT_RIGHT_PADDING_DP));
    }

    void draw(View parent, Canvas canvas) {
        if (!visible) {
            return;
        }

        final float badgeHeight = dp(BADGE_HEIGHT_DP);
        final float top = (parent.getMeasuredHeight() - badgeHeight) / 2f;
        final float left = parent.getMeasuredWidth() - dp(BADGE_RIGHT_MARGIN_DP) - badgeWidth;
        rect.set(left, top, left + badgeWidth, top + badgeHeight);

        canvas.drawRoundRect(rect, dp(BADGE_RADIUS_DP), dp(BADGE_RADIUS_DP), Theme.dialogs_countPaint);

        final float textY = rect.centerY() - ((Theme.dialogs_countTextPaint.descent() + Theme.dialogs_countTextPaint.ascent()) / 2f);
        canvas.drawText(text, rect.left + (rect.width() - textWidth) / 2f, textY, Theme.dialogs_countTextPaint);
    }
}
