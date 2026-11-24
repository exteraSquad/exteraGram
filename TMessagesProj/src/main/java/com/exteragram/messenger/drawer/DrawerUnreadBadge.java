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
import android.widget.TextView;
import org.telegram.ui.ActionBar.Theme;

import static org.telegram.messenger.AndroidUtilities.dp;

final class DrawerUnreadBadge {
    private static final float BADGE_TOP_DP = 12.5f;
    private static final float BADGE_HEIGHT_DP = 23f;
    private static final float BADGE_RADIUS_DP = 11.5f;
    private static final float BADGE_RIGHT_MARGIN_DP = 16.5f;
    private static final float MIN_TEXT_WIDTH_DP = 10f;
    private static final int BADGE_HORIZONTAL_PADDING_DP = 14;
    private static final int TEXT_BASELINE_DP = 16;
    private static final int TEXT_RIGHT_PADDING_DP = 12;

    private final RectF rect = new RectF();

    private int counter;
    private int defaultTextPaddingEnd = Integer.MIN_VALUE;
    private String text;
    private int textWidth;
    private int badgeWidth;
    private boolean visible;

    void bind(int counter, TextView textView) {
        this.counter = counter;
        update(textView);
    }

    void update(TextView textView) {
        rememberDefaultTextPadding(textView);

        visible = false;
        text = null;
        textWidth = 0;
        badgeWidth = 0;

        if (counter <= 0) {
            restoreTextPadding(textView);
            return;
        }

        visible = true;
        text = Integer.toString(counter);
        textWidth = (int) Math.ceil(Theme.dialogs_countTextPaint.measureText(text));
        final int countWidth = Math.max(dp(MIN_TEXT_WIDTH_DP), textWidth);
        badgeWidth = countWidth + dp(BADGE_HORIZONTAL_PADDING_DP);

        applyTextPadding(textView, defaultTextPaddingEnd + badgeWidth + dp(TEXT_RIGHT_PADDING_DP));
    }

    void draw(View parent, Canvas canvas) {
        if (!visible) {
            return;
        }

        final float top = dp(BADGE_TOP_DP);
        final float left = parent.getMeasuredWidth() - dp(BADGE_RIGHT_MARGIN_DP) - badgeWidth;
        rect.set(left, top, left + badgeWidth, top + dp(BADGE_HEIGHT_DP));

        canvas.drawRoundRect(rect, dp(BADGE_RADIUS_DP), dp(BADGE_RADIUS_DP), Theme.dialogs_countGrayPaint);
        canvas.drawText(text, rect.left + (rect.width() - textWidth) / 2f, top + dp(TEXT_BASELINE_DP), Theme.dialogs_countTextPaint);
    }

    private void rememberDefaultTextPadding(TextView textView) {
        if (defaultTextPaddingEnd != Integer.MIN_VALUE) {
            return;
        }
        defaultTextPaddingEnd = textView.getPaddingEnd();
    }

    private void restoreTextPadding(TextView textView) {
        applyTextPadding(textView, defaultTextPaddingEnd == Integer.MIN_VALUE ? 0 : defaultTextPaddingEnd);
    }

    private void applyTextPadding(TextView textView, int paddingEnd) {
        if (textView.getPaddingEnd() == paddingEnd) {
            return;
        }
        textView.setPaddingRelative(textView.getPaddingStart(), textView.getPaddingTop(), paddingEnd, textView.getPaddingBottom());
    }
}
