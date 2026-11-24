/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.messageexport.render;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

public final class MessageExportWatermark {

    private static final int TOP_GAP = 8;
    private static final int BOTTOM_GAP = 12;
    private static final int PILL_HEIGHT = 26;
    private static final int MARK_HEIGHT = 14;
    private static final int PILL_PADDING = (PILL_HEIGHT - MARK_HEIGHT) / 2;
    private static final int PILL_PADDING_RIGHT = PILL_PADDING + 2;
    private static final int CONTENT_PADDING = 8;

    private final Drawable mark;
    private final RectF pill = new RectF();
    private int lastColor;

    public MessageExportWatermark(Context context) {
        mark = ContextCompat.getDrawable(context, R.drawable.extera_watermark).mutate();
    }

    public static boolean isEnabled() {
        return ExteraConfig.getMessageExportWatermark();
    }

    public static int getBottomInset() {
        return dp(isEnabled() ? TOP_GAP + PILL_HEIGHT + BOTTOM_GAP : CONTENT_PADDING);
    }

    public void draw(Canvas canvas, int width, float contentBottom, Theme.ResourcesProvider provider) {
        if (!isEnabled()) {
            pill.setEmpty();
            return;
        }
        float left = (width - getPillWidth()) / 2f;
        float top = Math.round(contentBottom) + dp(TOP_GAP);
        if (provider != null && provider.hasGradientService()) {
            canvas.save();
            canvas.translate(left, top);
            provider.applyServiceShaderMatrix(width, 0, left, top);
            drawPill(canvas, 0, 0, provider);
            canvas.restore();
            pill.offset(left, top);
        } else {
            drawPill(canvas, left, top, provider);
        }
    }

    public void drawPill(Canvas canvas, float left, float top, Theme.ResourcesProvider provider) {
        int color = Theme.getColor(Theme.key_chat_serviceText, provider);
        if (color != lastColor) {
            lastColor = color;
            mark.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        }
        int pillHeight = getPillHeight();
        pill.set(left, top, left + getPillWidth(), top + pillHeight);
        canvas.drawRoundRect(pill, pillHeight / 2f, pillHeight / 2f, Theme.getThemePaint(Theme.key_paint_chatActionBackground, provider));
        int markWidth = getMarkWidth();
        int markHeight = dp(MARK_HEIGHT);
        int markLeft = Math.round(pill.left + dp(PILL_PADDING));
        int markTop = Math.round(pill.centerY() - markHeight / 2f);
        mark.setBounds(markLeft, markTop, markLeft + markWidth, markTop + markHeight);
        mark.draw(canvas);
    }

    public RectF getBounds() {
        return pill;
    }

    public int getPillWidth() {
        return getMarkWidth() + dp(PILL_PADDING) + dp(PILL_PADDING_RIGHT);
    }

    public int getPillHeight() {
        return dp(PILL_HEIGHT);
    }

    private int getMarkWidth() {
        return Math.round(dp(MARK_HEIGHT) * mark.getIntrinsicWidth() / (float) mark.getIntrinsicHeight());
    }
}
