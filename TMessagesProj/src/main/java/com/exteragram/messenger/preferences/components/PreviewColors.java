/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.preferences.components;

import org.telegram.ui.ActionBar.Theme;

public class PreviewColors {

    public static int getBackgroundColor() {
        int color = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        return Theme.multAlpha(color, Theme.isCurrentThemeDark() ? 0.05f : 0.035f);
    }

    public static int getOutlineColor() {
        int color = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        float alpha = (Theme.isCurrentThemeDark() ? 0.05f : 0.035f) + 0.085f;
        return Theme.multAlpha(color, alpha);
    }

    public static int getMockColor(boolean bright) {
        int color = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2);
        return Theme.multAlpha(color, bright ? 0.4f : 0.2f);
    }
}
