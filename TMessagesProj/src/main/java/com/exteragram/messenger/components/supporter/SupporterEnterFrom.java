/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.components.supporter;

import android.graphics.drawable.Drawable;
import android.view.View;

import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.Cells.ChatMessageCell;

public final class SupporterEnterFrom {

    public final View view;
    public final Drawable drawable;
    public final float screenX;
    public final float screenY;
    public final float scale;

    private SupporterEnterFrom(View view, Drawable drawable, float x, float y) {
        this.view = view;
        this.drawable = drawable;
        final int[] location = new int[2];
        view.getLocationOnScreen(location);
        this.scale = view.getScaleX();
        this.screenX = location[0] + x * scale;
        this.screenY = location[1] + y * view.getScaleY();
    }

    public static SupporterEnterFrom of(ChatMessageCell cell) {
        if (cell == null || cell.currentNameStatusDrawable == null) {
            return null;
        }
        return new SupporterEnterFrom(cell, cell.currentNameStatusDrawable, cell.getNameStatusX(), cell.getNameStatusY());
    }

    public static SupporterEnterFrom ofSecondary(ChatMessageCell cell) {
        if (cell == null || cell.currentNameEmojiStatusDrawable == null || cell.currentNameEmojiStatusDrawable.getBounds().isEmpty()) {
            return null;
        }
        final Drawable drawable = cell.currentNameEmojiStatusDrawable;
        return new SupporterEnterFrom(cell, drawable, drawable.getBounds().centerX(), drawable.getBounds().centerY());
    }

    public static SupporterEnterFrom of(SimpleTextView textView, Drawable drawable) {
        if (textView == null || drawable == null) {
            return null;
        }
        if (textView.getRightDrawable() == drawable) {
            return new SupporterEnterFrom(textView, drawable, textView.rightDrawableX, textView.rightDrawableY);
        } else if (textView.getRightDrawable2() == drawable) {
            return new SupporterEnterFrom(textView, drawable, textView.rightDrawable2X, textView.rightDrawable2Y);
        }
        return null;
    }

    void invalidate() {
        if (view instanceof ChatMessageCell) {
            ((ChatMessageCell) view).invalidateOutbounds();
        } else {
            view.invalidate();
        }
    }
}
