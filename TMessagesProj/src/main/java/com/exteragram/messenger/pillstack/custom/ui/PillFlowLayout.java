/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.custom.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.LocaleController;

public class PillFlowLayout extends ViewGroup {

    private final int horizontalSpacing;
    private final int verticalSpacing;

    public PillFlowLayout(Context context, int horizontalSpacing, int verticalSpacing) {
        super(context);
        this.horizontalSpacing = horizontalSpacing;
        this.verticalSpacing = verticalSpacing;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int available = width - getPaddingLeft() - getPaddingRight();
        int x = 0;
        int rowHeight = 0;
        int height = 0;
        int childSpec = MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            LayoutParams lp = child.getLayoutParams();
            int widthSpec = lp.width > 0 ? MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY) : childSpec;
            int heightSpec = lp.height > 0 ? MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY) : MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            child.measure(widthSpec, heightSpec);
            int childWidth = child.getMeasuredWidth();
            if (x > 0 && x + childWidth > available) {
                height += rowHeight + verticalSpacing;
                x = 0;
                rowHeight = 0;
            }
            x += childWidth + horizontalSpacing;
            rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
        }
        height += rowHeight;
        setMeasuredDimension(width, height + getPaddingTop() + getPaddingBottom());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int available = width - getPaddingLeft() - getPaddingRight();
        int x = 0;
        int y = getPaddingTop();
        int rowHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            if (x > 0 && x + childWidth > available) {
                y += rowHeight + verticalSpacing;
                x = 0;
                rowHeight = 0;
            }
            int left = LocaleController.isRTL ? width - getPaddingRight() - x - childWidth : getPaddingLeft() + x;
            child.layout(left, y, left + childWidth, y + childHeight);
            x += childWidth + horizontalSpacing;
            rowHeight = Math.max(rowHeight, childHeight);
        }
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }
}
