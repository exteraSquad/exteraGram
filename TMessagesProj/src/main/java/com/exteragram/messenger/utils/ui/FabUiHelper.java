/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui;

import com.exteragram.messenger.ExteraConfig;

public final class FabUiHelper {

    public static final int LARGE_FAB_SIZE_DP = 56;

    private static final int DEFAULT_FAB_SIZE_DP = 48;
    private static final int MATERIAL3_FAB_BOTTOM_MARGIN_DP = 16;
    private static final int DEFAULT_FAB_BOTTOM_MARGIN_DP = 14;
    private static final int MATERIAL3_SUB_FAB_BOTTOM_MARGIN_DP = 24;
    private static final int DEFAULT_SUB_FAB_BOTTOM_MARGIN_DP = 14;
    private static final float MATERIAL3_SUB_FAB_PADDING_DP = 8f;
    private static final float DEFAULT_SUB_FAB_PADDING_DP = 5.66f;
    private static final float MATERIAL3_SUB_FAB_SQUARE_RADIUS_DP = 12f;
    private static final float DEFAULT_SUB_FAB_SQUARE_RADIUS_DP = 10f;
    private static final float MATERIAL3_FAB_ELEVATION_DP = 6f;
    private static final float DEFAULT_FAB_ELEVATION_DP = 0.5f;
    private static final float MATERIAL3_SUB_FAB_SHADOW_RADIUS_DP = MATERIAL3_FAB_ELEVATION_DP;
    private static final float MATERIAL3_SUB_FAB_SHADOW_DY_DP = 2f;
    private static final int MATERIAL3_SUB_FAB_SHADOW_COLOR = 0x30000000;
    private static final float DEFAULT_SUB_FAB_SHADOW_RADIUS_DP = 2.667f;
    private static final float DEFAULT_SUB_FAB_SHADOW_DY_DP = 0.85f;
    private static final int DEFAULT_SUB_FAB_SHADOW_COLOR = 0x20000000;

    private FabUiHelper() {
    }

    public static boolean isMaterial3Fab() {
        return ExteraConfig.getNewFabStyle();
    }

    public static int getFabSizeDp() {
        return isMaterial3Fab() ? LARGE_FAB_SIZE_DP : DEFAULT_FAB_SIZE_DP;
    }

    public static int getFabBottomMarginDp() {
        return isMaterial3Fab() ? MATERIAL3_FAB_BOTTOM_MARGIN_DP : DEFAULT_FAB_BOTTOM_MARGIN_DP;
    }

    public static int getSubFabBottomMarginDp() {
        return isMaterial3Fab() ? MATERIAL3_SUB_FAB_BOTTOM_MARGIN_DP : DEFAULT_SUB_FAB_BOTTOM_MARGIN_DP;
    }

    public static float getSubFabPaddingDp() {
        return isMaterial3Fab() ? MATERIAL3_SUB_FAB_PADDING_DP : DEFAULT_SUB_FAB_PADDING_DP;
    }

    public static float getFabElevationDp() {
        return isMaterial3Fab() ? MATERIAL3_FAB_ELEVATION_DP : DEFAULT_FAB_ELEVATION_DP;
    }

    public static float getSubFabShadowRadiusDp() {
        return isMaterial3Fab() ? MATERIAL3_SUB_FAB_SHADOW_RADIUS_DP : DEFAULT_SUB_FAB_SHADOW_RADIUS_DP;
    }

    public static float getSubFabShadowDyDp() {
        return isMaterial3Fab() ? MATERIAL3_SUB_FAB_SHADOW_DY_DP : DEFAULT_SUB_FAB_SHADOW_DY_DP;
    }

    public static int getSubFabShadowColor() {
        return isMaterial3Fab() ? MATERIAL3_SUB_FAB_SHADOW_COLOR : DEFAULT_SUB_FAB_SHADOW_COLOR;
    }

    public static float getFabCornerRadiusDp() {
        return ExteraConfig.getSquareFab()
                ? UIUtil.getFabSquareCornerRadiusDp(getFabSizeDp())
                : getFabSizeDp() / 2f;
    }

    public static float getSubFabBackgroundRadiusDp() {
        if (ExteraConfig.getSquareFab()) {
            return isMaterial3Fab() ? MATERIAL3_SUB_FAB_SQUARE_RADIUS_DP : DEFAULT_SUB_FAB_SQUARE_RADIUS_DP;
        }
        return (getFabSizeDp() - getSubFabPaddingDp() * 2f) / 2f;
    }
}
