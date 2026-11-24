/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.GlassOutlineStyle;

import org.telegram.ui.ActionBar.Theme;

public final class GlassUiHelper {

    public static final int SIDE_PADDING_DP = 12;
    public static final float PILL_SIDE_MARGIN_DP = SIDE_PADDING_DP - 9.66f;
    public static final int TITLE_START_GAP_DP = 24;
    public static final int TITLE_START_NO_BACK_DP = 12;
    public static final int TITLE_TEXT_SIZE_CENTERED = 18;
    public static final int TITLE_TEXT_SIZE_START = 19;

    public static final float[] HEADER_FADE_ALPHAS = {1f, .89f, .67f, .43f, .19f, .05f, 0f};
    public static final float[] HEADER_FADE_POSITIONS = {0f, .3f, .5f, .68f, .84f, .94f, 1f};

    public static final int SOLID_STROKE_COLOR = 0x26FFFFFF;

    private static final Paint solidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF solidRect = new RectF();

    static {
        solidPaint.setStyle(Paint.Style.STROKE);
    }

    private GlassUiHelper() {
    }

    public static boolean drawOutline(Canvas c, Path shape, RectF r, float radius, float alpha) {
        final GlassOutlineStyle style = ExteraConfig.getGlassOutlineStyle();
        if (style == GlassOutlineStyle.GLARE) return false;
        if (style != GlassOutlineStyle.SOLID || alpha <= 0.004f) return true;
        solidPaint.setColor(Theme.multAlpha(SOLID_STROKE_COLOR, Math.min(1f, alpha)));
        if (shape == null) {
            solidPaint.setStrokeWidth(1f);
            solidRect.set(r);
            solidRect.inset(0.5f, 0.5f);
            final float rr = Math.max(0f, Math.min(radius, Math.min(r.width(), r.height()) / 2f) - 0.5f);
            c.drawRoundRect(solidRect, rr, rr, solidPaint);
        } else {
            solidPaint.setStrokeWidth(2f);
            c.save();
            c.clipPath(shape);
            c.drawPath(shape, solidPaint);
            c.restore();
        }
        return true;
    }
}
