/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui.tabs;

import static org.telegram.messenger.AndroidUtilities.dp;

import androidx.annotation.Nullable;

public final class HideOnScrollTracker {

    private static final int HIDE_DISTANCE_DP = 16;
    private static final int SHOW_DISTANCE_DP = 48;

    private int distance;

    @Nullable
    public Boolean onScrolled(int dy, boolean atBottom) {
        if (dy == 0) {
            return null;
        }
        if (atBottom) {
            distance = 0;
            return false;
        }
        if (distance != 0 && (dy > 0) != (distance > 0)) {
            distance = 0;
        }
        distance += dy;
        if (distance >= dp(HIDE_DISTANCE_DP)) {
            return true;
        }
        if (distance <= -dp(SHOW_DISTANCE_DP)) {
            return false;
        }
        return null;
    }

    public void reset() {
        distance = 0;
    }
}
