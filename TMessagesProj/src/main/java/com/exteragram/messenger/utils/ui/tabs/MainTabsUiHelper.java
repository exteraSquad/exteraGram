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
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.Shape;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.config.BottomNavigationBar;
import com.exteragram.messenger.utils.ui.material.Material3Tokens;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.MainTabsLayout;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProviderBuilder;

public final class MainTabsUiHelper {

    private static final int FLOATING_TABS_INNER_PADDING_DP = DialogsActivity.MAIN_TABS_MARGIN + 4;
    private static final int FLOATING_TABS_MAX_WIDTH_DP = 328 + DialogsActivity.MAIN_TABS_MARGIN * 2;
    private static final float FLOATING_BACKGROUND_INSET_DP = DialogsActivity.MAIN_TABS_MARGIN - 0.334f;
    private static final int FLOATING_TABS_FAB_OFFSET_DP = DialogsActivity.MAIN_TABS_HEIGHT + DialogsActivity.MAIN_TABS_MARGIN;
    private static final int FLOATING_TAB_HEIGHT_DP = DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS - FLOATING_TABS_INNER_PADDING_DP * 2;
    private static final int FLOATING_TAB_ICON_TOP_DP = 4;
    private static final int FLOATING_TAB_PADDING_DP = 16;
    private static final int FLOATING_TAB_ICON_ONLY_PADDING_DP = 24;
    private static final int MAIN_TAB_ICON_SIZE_DP = 24;
    private static final int MAIN_TAB_COUNTER_ICON_OFFSET_DP = 6;

    private static final int MATERIAL3_NAV_BAR_CONTAINER_HEIGHT_DP = 64;
    private static final int MATERIAL3_NAV_BAR_ICON_ONLY_CONTAINER_HEIGHT_DP = 56;
    private static final int MATERIAL3_NAV_BAR_ITEM_PADDING_VERTICAL_DP = 6;
    private static final int MATERIAL3_NAV_BAR_ACTIVE_INDICATOR_WIDTH_DP = 56;
    private static final int MATERIAL3_NAV_BAR_ACTIVE_INDICATOR_HEIGHT_DP = 32;
    private static final int MATERIAL3_NAV_BAR_ACTIVE_INDICATOR_MARGIN_HORIZONTAL_DP = 4;
    private static final int MATERIAL3_NAV_BAR_ACTIVE_INDICATOR_LABEL_PADDING_DP = 4;
    private static final int MATERIAL3_NAV_BAR_LABEL_HEIGHT_DP = 16;
    private static final float MATERIAL3_NAV_BAR_LABEL_LETTER_SPACING = 0.04166667f;
    private static final float MATERIAL3_NAV_BAR_ACTIVE_INDICATOR_ALPHA = 0.125f;
    private static final float MATERIAL3_NAV_BAR_SPRING_MIN_VISIBLE_CHANGE = 0.01f;
    private static final float FLOATING_SELECTED_BACKGROUND_SCALE_FROM = 0.6f;

    private MainTabsUiHelper() {
    }

    public static boolean isMaterial3NavigationBar() {
        return ExteraConfig.getNewNavigationBarStyle();
    }

    private static int getMaterial3ContainerHeightDp() {
        return ExteraConfig.getHideNavigationBarLabels() ? MATERIAL3_NAV_BAR_ICON_ONLY_CONTAINER_HEIGHT_DP : MATERIAL3_NAV_BAR_CONTAINER_HEIGHT_DP;
    }

    public static int getTabsViewHeightDp() {
        return isMaterial3NavigationBar() ? getMaterial3ContainerHeightDp() : DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS;
    }

    public static int getTabsFabOffsetDp() {
        return isMaterial3NavigationBar() ? getMaterial3ContainerHeightDp() : FLOATING_TABS_FAB_OFFSET_DP;
    }

    public static int getAdditionalNavigationBarHeight(boolean hasMainTabs) {
        return hasMainTabs && !BottomNavigationBar.hidden() && !BottomNavigationBar.floating() ? dp(getTabsViewHeightDp()) : 0;
    }

    public static int getFloatingTabsPadding(boolean hasMainTabs) {
        return hasMainTabs && BottomNavigationBar.floating() ? dp(getTabsViewHeightDp()) : 0;
    }

    public static int getTabsFabOffset(boolean hasMainTabs) {
        return hasMainTabs && BottomNavigationBar.visible() ? dp(getTabsFabOffsetDp()) : 0;
    }

    private static int getTabsViewHeight(int bottomInset) {
        return isMaterial3NavigationBar()
                ? dp(getMaterial3ContainerHeightDp()) + bottomInset
                : dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS);
    }

    private static int getTabsInnerPaddingVertical() {
        return isMaterial3NavigationBar() ? 0 : dp(FLOATING_TABS_INNER_PADDING_DP);
    }

    public static void applyTabsLayoutStyle(MainTabsLayout tabsView) {
        final boolean material3NavigationBar = isMaterial3NavigationBar();
        final int tabsPaddingH = material3NavigationBar ? 0 : dp(FLOATING_TABS_INNER_PADDING_DP);
        final int tabsPaddingV = getTabsInnerPaddingVertical();
        tabsView.setPadding(tabsPaddingH, tabsPaddingV, tabsPaddingH, tabsPaddingV);
        tabsView.setMaxWidth(material3NavigationBar ? 0 : dp(FLOATING_TABS_MAX_WIDTH_DP));
        tabsView.setFillAvailableWidth(material3NavigationBar);
        tabsView.setSwipeSelectionEnabled(!material3NavigationBar);
        tabsView.setDrawTopDivider(material3NavigationBar);
    }

    public static void applyTabsBottomInset(MainTabsLayout tabsView, View wrapper, int leftInset, int rightInset, int bottomInset) {
        final int desiredHeight = getTabsViewHeight(bottomInset);
        final var tabsLp = (ViewGroup.MarginLayoutParams) tabsView.getLayoutParams();
        if (tabsLp.height != desiredHeight) {
            tabsLp.height = desiredHeight;
            tabsView.setLayoutParams(tabsLp);
        }
        final int paddingBottom = getTabsInnerPaddingVertical() + (isMaterial3NavigationBar() ? bottomInset : 0);
        if (tabsView.getPaddingBottom() != paddingBottom) {
            tabsView.setPadding(tabsView.getPaddingLeft(), tabsView.getPaddingTop(), tabsView.getPaddingRight(), paddingBottom);
        }
        wrapper.setPadding(leftInset, 0, rightInset, isMaterial3NavigationBar() ? 0 : bottomInset);
    }

    public static void applyTabsVisibilityScale(View wrapper, View tabsView, float scale) {
        final float wrapperScale = isMaterial3NavigationBar() ? 1f : scale;
        wrapper.setPivotX(tabsView.getLeft() + tabsView.getWidth() / 2f);
        wrapper.setPivotY(tabsView.getTop() + tabsView.getHeight() / 2f);
        wrapper.setScaleX(wrapperScale);
        wrapper.setScaleY(wrapperScale);
    }

    public static Drawable createMainTabsScrimBackground(Theme.ResourcesProvider resourcesProvider, boolean circle) {
        return createMainTabsScrimBackground(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), circle);
    }

    private static Drawable createMainTabsScrimBackground(int backgroundColor, boolean circle) {
        final ShapeDrawable background;
        if (circle) {
            background = Theme.createCircleDrawable(dp(40), backgroundColor);
        } else if (isMaterial3NavigationBar()) {
            background = new ShapeDrawable(new MainTabIndicatorShape());
            background.getPaint().setColor(backgroundColor);
        } else {
            background = Theme.createRoundRectDrawable(dp(28), backgroundColor);
        }
        background.getPaint().setShadowLayer(
                dp(6), 0, dp(1), Theme.multAlpha(0xFF000000, 0.15f)
        );
        return background;
    }

    private static final class MainTabIndicatorShape extends Shape {
        @Override
        public void draw(Canvas canvas, Paint paint) {
            final RectF rect = AndroidUtilities.rectTmp;
            setMainTabSelectedIndicatorBounds(rect, getWidth(), (int) getHeight(), 1f);
            final float radius = rect.height() / 2f;
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
    }

    public static float getBackgroundRadius() {
        return isMaterial3NavigationBar() ? 0 : dp(DialogsActivity.MAIN_TABS_HEIGHT / 2f);
    }

    public static int getBackgroundInset() {
        return isMaterial3NavigationBar() ? 0 : dp(FLOATING_BACKGROUND_INSET_DP);
    }

    private static float getMaterial3IndicatorTopDp() {
        return ExteraConfig.getHideNavigationBarLabels()
                ? (getMaterial3ContainerHeightDp() - MATERIAL3_NAV_BAR_ACTIVE_INDICATOR_HEIGHT_DP) / 2f
                : MATERIAL3_NAV_BAR_ITEM_PADDING_VERTICAL_DP;
    }

    public static float getMainTabIconTopDp() {
        if (isMaterial3NavigationBar()) {
            return getMaterial3IndicatorTopDp() + (MATERIAL3_NAV_BAR_ACTIVE_INDICATOR_HEIGHT_DP - MAIN_TAB_ICON_SIZE_DP) / 2f;
        }
        return ExteraConfig.getHideNavigationBarLabels()
                ? (FLOATING_TAB_HEIGHT_DP - MAIN_TAB_ICON_SIZE_DP) / 2f
                : FLOATING_TAB_ICON_TOP_DP;
    }

    public static float getMainTabAvatarTopDp() {
        return getMainTabIconTopDp() + 1;
    }

    public static float getMainTabIconOnlyWidth() {
        return dp(MAIN_TAB_ICON_SIZE_DP + (FLOATING_TAB_ICON_ONLY_PADDING_DP - FLOATING_TAB_PADDING_DP) * 2);
    }

    public static void applyMaterial3MainTabStyle(TextView label) {
        label.setIncludeFontPadding(false);
        label.setLetterSpacing(MATERIAL3_NAV_BAR_LABEL_LETTER_SPACING);
        label.setLayoutParams(LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                MATERIAL3_NAV_BAR_LABEL_HEIGHT_DP,
                Gravity.CENTER_HORIZONTAL | Gravity.TOP,
                0,
                MATERIAL3_NAV_BAR_ITEM_PADDING_VERTICAL_DP
                        + MATERIAL3_NAV_BAR_ACTIVE_INDICATOR_HEIGHT_DP
                        + MATERIAL3_NAV_BAR_ACTIVE_INDICATOR_LABEL_PADDING_DP,
                0,
                0
        ));
    }

    public static <K> SpringAnimation createMaterial3IndicatorSizeSpring(K target, FloatPropertyCompat<K> property) {
        return new SpringAnimation(target, property)
                .setMinimumVisibleChange(MATERIAL3_NAV_BAR_SPRING_MIN_VISIBLE_CHANGE)
                .setSpring(new SpringForce(0f)
                        .setDampingRatio(Material3Tokens.DEFAULT_SPATIAL_DAMPING)
                        .setStiffness(Material3Tokens.DEFAULT_SPATIAL_STIFFNESS));
    }

    public static <K> SpringAnimation createMaterial3ColorSpring(K target, FloatPropertyCompat<K> property) {
        return new SpringAnimation(target, property)
                .setMinimumVisibleChange(MATERIAL3_NAV_BAR_SPRING_MIN_VISIBLE_CHANGE)
                .setSpring(new SpringForce(0f)
                        .setDampingRatio(Material3Tokens.EFFECTS_DAMPING)
                        .setStiffness(Material3Tokens.EFFECTS_STIFFNESS));
    }

    public static float getMainTabCounterCenterY(boolean mainNavigationTab) {
        return dpf2((mainNavigationTab ? getMainTabIconTopDp() : FLOATING_TAB_ICON_TOP_DP) + MAIN_TAB_COUNTER_ICON_OFFSET_DP);
    }

    public static float getSelectedBackgroundScale(float progress) {
        return lerp(FLOATING_SELECTED_BACKGROUND_SCALE_FROM, 1f, progress);
    }

    public static int getMainTabSelectedIndicatorColor(int color, float alpha) {
        return Theme.multAlpha(color, MATERIAL3_NAV_BAR_ACTIVE_INDICATOR_ALPHA * alpha);
    }

    public static void setMainTabSelectedIndicatorBounds(RectF out, float viewWidth, int viewHeight, float sizeProgress) {
        final int indicatorHorizontalMargin = dp(MATERIAL3_NAV_BAR_ACTIVE_INDICATOR_MARGIN_HORIZONTAL_DP);
        final float indicatorWidth = Math.min(
                dp(MATERIAL3_NAV_BAR_ACTIVE_INDICATOR_WIDTH_DP),
                Math.max(0, viewWidth - indicatorHorizontalMargin * 2f)
        ) * Math.max(0, sizeProgress);
        final float indicatorHeight = Math.min(dp(MATERIAL3_NAV_BAR_ACTIVE_INDICATOR_HEIGHT_DP), viewHeight);
        final float indicatorLeft = (viewWidth - indicatorWidth) / 2f;
        final float indicatorTop = dp(getMaterial3IndicatorTopDp());
        out.set(indicatorLeft, indicatorTop, indicatorLeft + indicatorWidth, indicatorTop + indicatorHeight);
    }

    public static void applyBackgroundStroke(BlurredBackgroundProviderBuilder builder) {
        if (isMaterial3NavigationBar()) {
            builder
                    .setStrokeColorTop(0, 0)
                    .setStrokeColorBottom(0, 0)
                    .setStrokeColorFull(0, 0)
                    .setStrokeWidth(0, 0);
        } else {
            builder
                    .setStrokeColorTop(0x11000000, 0x06FFFFFF)
                    .setStrokeColorBottom(0x20000000, 0x11FFFFFF)
                    .setStrokeWidth(dpf2(0.4f), dpf2(0.4f));
        }
    }

    public static void setBlurBounds(RectF out, View rootView, int bottomInset) {
        final int bottom;
        final int top;
        if (isMaterial3NavigationBar()) {
            bottom = rootView.getMeasuredHeight();
            top = bottom - dp(getMaterial3ContainerHeightDp()) - bottomInset;
        } else {
            bottom = rootView.getMeasuredHeight() - bottomInset - dp(DialogsActivity.MAIN_TABS_MARGIN);
            top = bottom - dp(DialogsActivity.MAIN_TABS_HEIGHT);
        }
        out.set(0, top, rootView.getMeasuredWidth(), bottom);
    }
}