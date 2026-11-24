package org.telegram.ui.Components.blur3.drawable.color;

import androidx.annotation.ColorInt;
import androidx.annotation.Px;

import org.telegram.ui.ActionBar.Theme;

public interface BlurredBackgroundColorProvider {
    @ColorInt int getShadowColor();
    @ColorInt int getBackgroundColor();
    @ColorInt int getStrokeColorTop();
    @ColorInt int getStrokeColorBottom();

    @ColorInt
    default int getStrokeColorFull() {
        return Theme.getDividerColor(null);
    }

    /** A second, tight shadow around the glass: reads as its outline. 0 to not draw it. */
    default @ColorInt int getOutlineShadowColor() {
        return 0;
    }

    /** A thin even outline drawn just outside the glass. 0 to not draw it. */
    default @ColorInt int getOutlineColor() {
        return 0;
    }

    default @Px float getOutlineWidth() {
        return 0;
    }

    /** The shadow's radius and offset when they follow the theme, negative to keep the drawable's own. */
    default @Px float getShadowRadiusOverride() {
        return -1;
    }

    default @Px float getShadowDyOverride() {
        return -1;
    }
}
