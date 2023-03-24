package com.exteragram.messenger.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;

import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;

public class VerticalImageSpan extends ImageSpan {

    public VerticalImageSpan(Drawable drawable) {
        super(drawable);
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fontMetricsInt) {
        Drawable drawable = getDrawable();
        Rect rect = drawable.getBounds();
        if (fontMetricsInt != null) {
            Paint.FontMetricsInt fmPaint = paint.getFontMetricsInt();
            int fontHeight = fmPaint.descent - fmPaint.ascent;
            int drHeight = rect.bottom - rect.top;
            int centerY = fmPaint.ascent + fontHeight / 2;

            fontMetricsInt.ascent = centerY - drHeight / 2;
            fontMetricsInt.top = fontMetricsInt.ascent;
            fontMetricsInt.bottom = centerY + drHeight / 2;
            fontMetricsInt.descent = fontMetricsInt.bottom;
        }
        return rect.right;
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        Drawable drawable = getDrawable();
        canvas.save();
        Paint.FontMetricsInt fmPaint = paint.getFontMetricsInt();
        int fontHeight = fmPaint.descent - fmPaint.ascent;
        int centerY = y + fmPaint.descent - fontHeight / 2;
        int transY = centerY - (drawable.getBounds().bottom - drawable.getBounds().top) / 2;
        canvas.translate(x, transY);
        if (LocaleController.isRTL) {
            canvas.scale(-1, 1, drawable.getIntrinsicWidth() >> 1, drawable.getIntrinsicHeight() >> 1);
        }
        drawable.draw(canvas);
        canvas.restore();
    }

    public static SpannableStringBuilder createSpan(Context context, int resId, String text, String replace, String color) {
        return createSpan(context, resId, text, replace, color, null);
    }

    public static SpannableStringBuilder createSpan(Context context, int resId, String text, String replace, String color, Theme.ResourcesProvider resourcesProvider) {
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        List<Integer> beginIndexes = new ArrayList<>();
        int index = text.indexOf(replace);
        while (index >= 0) {
            beginIndexes.add(index);
            index = text.indexOf(replace, index + 1);
        }
        Drawable drawable = context.getDrawable(resId);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(color, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        if (!beginIndexes.isEmpty()) {
            for (int begin : beginIndexes) {
                builder.setSpan(new VerticalImageSpan(drawable), begin, begin + replace.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return builder;
    }
}
