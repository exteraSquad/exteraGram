/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.graphics.Paint;
import android.text.TextUtils;
import android.view.View;

import androidx.core.graphics.ColorUtils;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.utils.ui.material.Material3Tokens;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.SharedMediaLayout;
import org.telegram.ui.Components.Text;

public final class ProfileUiHelper {

    private static final int LEGACY_BAND_HEIGHT_DP = 74;

    private static final float MATERIAL3_TOP_PADDING_DP = 8f;
    private static final float MATERIAL3_BOTTOM_PADDING_DP = 12f;
    private static final float MATERIAL3_SIDE_PADDING_DP = 12f;

    private static final float MATERIAL3_CONTAINER_HEIGHT_DP = 56f;
    private static final float MATERIAL3_CONTAINER_GAP_DP = 12f;
    private static final float MATERIAL3_CONTAINER_MIN_GAP_DP = 8f;
    private static final float MATERIAL3_CONTAINER_MIN_WIDTH_DP = 64f;
    private static final float MATERIAL3_PRESSED_CORNER_RADIUS_DP = 12f;
    private static final float MATERIAL3_ICON_SIZE_DP = 24f;

    private static final float MATERIAL3_LABEL_MARGIN_TOP_DP = 6f;
    private static final float MATERIAL3_LABEL_HEIGHT_DP = 20f;
    private static final float MATERIAL3_LABEL_TEXT_SIZE_DP = 14f;
    private static final float MATERIAL3_LABEL_MIN_SCALE = 0.8f;
    private static final float MATERIAL3_LABEL_MIN_SPACING_DP = 8f;

    private static final int MATERIAL3_BAND_HEIGHT_DP = Math.round(MATERIAL3_TOP_PADDING_DP + MATERIAL3_CONTAINER_HEIGHT_DP
            + MATERIAL3_LABEL_MARGIN_TOP_DP + MATERIAL3_LABEL_HEIGHT_DP + MATERIAL3_BOTTOM_PADDING_DP);

    private static final float MATERIAL3_PRESS_SPRING_MIN_VISIBLE_CHANGE = 1f / 1000f;

    private static final float MATERIAL3_LABEL_FADE_START = 0.6f;

    private ProfileUiHelper() {
    }

    public static boolean isMaterial3Style() {
        return ExteraConfig.getNewProfileActionsStyle();
    }

    public static int getBandHeight() {
        return dp(isMaterial3Style() ? MATERIAL3_BAND_HEIGHT_DP : LEGACY_BAND_HEIGHT_DP);
    }

    public static float getSidePadding() {
        return dpf2(MATERIAL3_SIDE_PADDING_DP);
    }

    public static float getContainerGap(float availableWidth, int count) {
        final float gap = dpf2(MATERIAL3_CONTAINER_GAP_DP);
        if (count < 2) {
            return gap;
        }
        final float fitGap = (availableWidth - dpf2(MATERIAL3_CONTAINER_MIN_WIDTH_DP) * count) / (count - 1);
        return Math.max(dpf2(MATERIAL3_CONTAINER_MIN_GAP_DP), Math.min(gap, fitGap));
    }

    public static float getContainerHeight() {
        return dpf2(MATERIAL3_CONTAINER_HEIGHT_DP);
    }

    public static float getCornerRadius(float pressProgress) {
        return Math.max(0, lerp(getContainerHeight() / 2f, dpf2(MATERIAL3_PRESSED_CORNER_RADIUS_DP), pressProgress));
    }

    public static int getIconSize() {
        return dp(MATERIAL3_ICON_SIZE_DP);
    }

    public static int getStateLayerColor(int contentColor) {
        return ColorUtils.setAlphaComponent(contentColor, Math.round(0xFF * Material3Tokens.PRESSED_STATE_LAYER_OPACITY));
    }

    public static float getLabelAlpha(float fraction) {
        return Utilities.clamp01((fraction - MATERIAL3_LABEL_FADE_START) / (1f - MATERIAL3_LABEL_FADE_START));
    }

    public static Text createLabel(CharSequence title) {
        return new Text(title, MATERIAL3_LABEL_TEXT_SIZE_DP, AndroidUtilities.bold());
    }

    public static float getLabelTopOffset(Text label) {
        final Paint.FontMetricsInt fontMetrics = label.getFontMetricsInt();
        return dpf2(MATERIAL3_LABEL_MARGIN_TOP_DP) + fontMetrics.ascent - fontMetrics.top;
    }

    public static float fitLabel(Text label, CharSequence title, float columnWidth) {
        label.setText(title);
        final float maxWidth = columnWidth - dpf2(MATERIAL3_LABEL_MIN_SPACING_DP);
        final float width = label.getCurrentWidth();
        if (width <= maxWidth || maxWidth <= 0) {
            return 1f;
        }
        final float scale = maxWidth / width;
        if (scale >= MATERIAL3_LABEL_MIN_SCALE) {
            return scale;
        }
        label.setText(TextUtils.ellipsize(title, label.paint, maxWidth / MATERIAL3_LABEL_MIN_SCALE, TextUtils.TruncateAt.END));
        return MATERIAL3_LABEL_MIN_SCALE;
    }

    public static <K> SpringAnimation createPressSpring(K target, FloatPropertyCompat<K> property) {
        return new SpringAnimation(target, property)
                .setMinimumVisibleChange(MATERIAL3_PRESS_SPRING_MIN_VISIBLE_CHANGE)
                .setSpring(new SpringForce()
                        .setDampingRatio(Material3Tokens.FAST_SPATIAL_DAMPING)
                        .setStiffness(Material3Tokens.FAST_SPATIAL_STIFFNESS));
    }

    public static int limitScrollForShortProfile(LinearLayoutManager layoutManager, View firstRow, int bottomPaddingRow, int headerHeight, int dy) {
        final View lastRow = layoutManager.findViewByPosition(bottomPaddingRow);
        if (lastRow == null) {
            return dy;
        }
        final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) lastRow.getLayoutParams();
        final int headerOffset = firstRow.getTop() - headerHeight;
        final int bottomAtRest = layoutManager.getDecoratedBottom(lastRow) + params.bottomMargin - headerOffset;
        if (bottomAtRest <= layoutManager.getHeight()) {
            return Math.min(dy, Math.max(0, headerOffset));
        }
        return dy;
    }

    public static void anchorTopChild(LinearLayoutManager layoutManager, RecyclerView.State state) {
        if (state.isPreLayout() || layoutManager.hasPendingScrollPosition()) {
            return;
        }
        int anchorPosition = RecyclerView.NO_POSITION;
        int anchorTop = Integer.MAX_VALUE;
        for (int i = 0; i < layoutManager.getChildCount(); i++) {
            final View child = layoutManager.getChildAt(i);
            if (child == null) {
                continue;
            }
            final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child.getLayoutParams();
            final int position = params.getViewLayoutPosition();
            if (params.isItemRemoved() || position < 0 || position >= state.getItemCount()) {
                continue;
            }
            final int top = layoutManager.getDecoratedTop(child) - params.topMargin;
            if (top < anchorTop) {
                anchorTop = top;
                anchorPosition = position;
            }
        }
        if (anchorPosition != RecyclerView.NO_POSITION) {
            layoutManager.scrollToPositionWithOffset(anchorPosition, anchorTop - layoutManager.getPaddingTop());
        }
    }

    public static int getPreferredTab() {
        return switch (ExteraConfig.getDefaultProfileTab()) {
            case MEDIA -> SharedMediaLayout.TAB_PHOTOVIDEO;
            case COMMON_GROUPS -> SharedMediaLayout.TAB_COMMON_GROUPS;
            default -> -1;
        };
    }
}
