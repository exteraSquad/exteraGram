/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui;

import android.graphics.Color;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.Paint.Views.EditTextOutline;

public final class PaintTextUiHelper {

    public static final int TYPE_OUTLINED = 4;

    private PaintTextUiHelper() {
    }

    public static int applyOutlined(EditTextOutline editText, int color) {
        final int textColor = AndroidUtilities.computePerceivedBrightness(color) >= .721f ? Color.BLACK : Color.WHITE;
        editText.setFrameColor(0);
        editText.setStrokeColor(color);
        editText.setTextColor(textColor);
        return textColor;
    }
}
