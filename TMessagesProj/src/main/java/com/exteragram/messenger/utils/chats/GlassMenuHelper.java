/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.chats;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.LiteMode;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ReactionsContainerLayout;
import org.telegram.ui.Components.ScrimOptions;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProvider;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProviderBuilder;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.utils.Blur3Utils;
import org.telegram.utils.glass.source.GlassSourceBitmap;

public class GlassMenuHelper {

    private static final int MENU_RADIUS_DP = 12;
    private static final int MENU_PADDING_DP = 8;
    private static final float GAP_ALPHA_LIGHT = 0.06f;
    private static final float GAP_ALPHA_DARK = 0.03f;

    private GlassMenuHelper() {
    }

    public static void captureBlur(GlassSourceBitmap source, BlurredBackgroundDrawableViewFactory factory, View root) {
        ScrimOptions.makeGlobalBlurBitmaps((bitmapBackground, bitmapMenu) -> {
            source.setBitmap(bitmapMenu);
            Blur3Utils.checkBitmapSourceMatrixScale(source, root);
            factory.invalidateAllLinkedViews();
        });
    }

    public static boolean isEnabled(Theme.ResourcesProvider resourcesProvider) {
        return ExteraConfig.getGlassMessageMenu()
                && BlurredBackgroundProviderImpl.checkBlurEnabled(resourcesProvider);
    }

    public static boolean isEnabled(int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        return ExteraConfig.getGlassMessageMenu()
                && BlurredBackgroundProviderImpl.checkBlurEnabled(currentAccount, resourcesProvider);
    }

    public static int separatorColor(boolean glass, Theme.ResourcesProvider resourcesProvider) {
        if (!glass) {
            return Theme.getColor(Theme.key_actionBarDefaultSubmenuSeparator, resourcesProvider);
        }
        final boolean dark = resourcesProvider != null ? resourcesProvider.isDark() : Theme.isCurrentThemeDark();
        return Theme.multAlpha(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider), dark ? GAP_ALPHA_DARK : GAP_ALPHA_LIGHT);
    }

    public static BlurredBackgroundDrawable createPanelBackground(BlurredBackgroundDrawableViewFactory factory, Theme.ResourcesProvider resourcesProvider, View view) {
        return factory.create(view, true)
                .setColorProvider(scrimMenuBackground(resourcesProvider))
                .setRadius(dp(MENU_RADIUS_DP))
                .setPadding(dp(MENU_PADDING_DP))
                .setHasPadding(true);
    }

    public static BlurredBackgroundDrawable createFill(BlurredBackgroundDrawableViewFactory factory, Theme.ResourcesProvider resourcesProvider, View view) {
        return factory.create(view, true)
                .setColorProvider(scrimMenuBackgroundFill(resourcesProvider));
    }

    public static void applyToPopup(BlurredBackgroundDrawableViewFactory factory, Theme.ResourcesProvider resourcesProvider, ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout) {
        popupLayout.setBackground(createPanelBackground(factory, resourcesProvider, popupLayout));
        popupLayout.setGlassBackgroundFactory(factory);
        if (popupLayout.getSwipeBack() != null) {
            popupLayout.getSwipeBack().setForegroundDrawable(createFill(factory, resourcesProvider, popupLayout.getSwipeBack()));
        }
    }

    public static void applyToReusedMenu(BlurredBackgroundDrawableViewFactory factory, GlassSourceBitmap source, Theme.ResourcesProvider resourcesProvider, ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout, View root, boolean glass) {
        applyToReusedMenu(factory, resourcesProvider, popupLayout, glass);
        if (popupLayout != null && glass) {
            captureBlur(source, factory, root);
        }
    }

    public static void applyToReusedMenu(BlurredBackgroundDrawableViewFactory factory, Theme.ResourcesProvider resourcesProvider, ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout, boolean glass) {
        if (popupLayout == null) {
            return;
        }
        if (glass) {
            applyToPopup(factory, resourcesProvider, popupLayout);
            applyToGaps(popupLayout, separatorColor(true, resourcesProvider));
        } else {
            // restore popup
            popupLayout.setGlassBackgroundFactory(null);
            popupLayout.setBackgroundDrawable(ContextCompat.getDrawable(popupLayout.getContext(), R.drawable.popup_fixed_alert4).mutate());
            popupLayout.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider));
            if (popupLayout.getSwipeBack() != null) {
                popupLayout.getSwipeBack().setForegroundDrawable(null);
            }
            applyToGaps(popupLayout, separatorColor(false, resourcesProvider), true);
        }
    }

    public static void applyToReactions(BlurredBackgroundDrawableViewFactory factory, Theme.ResourcesProvider resourcesProvider, ReactionsContainerLayout reactionsLayout) {
        reactionsLayout.setGlassBackground(factory, scrimMenuBackgroundFill(resourcesProvider));
    }

    public static void applyToGaps(View view, int color) {
        applyToGaps(view, color, false);
    }

    public static void applyToGaps(View view, int color, boolean dividerVisible) {
        if (view instanceof ActionBarPopupWindow.GapView) {
            ((ActionBarPopupWindow.GapView) view).setColor(color);
            ((ActionBarPopupWindow.GapView) view).setDividerVisible(dividerVisible);
        } else if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); ++i) {
                applyToGaps(group.getChildAt(i), color, dividerVisible);
            }
        }
    }

    public static void draw(BlurredBackgroundDrawable drawable, Canvas canvas, RectF rect, float radius, int alpha) {
        draw(drawable, canvas, rect, radius, alpha, 0, 0);
    }

    public static void draw(BlurredBackgroundDrawable drawable, Canvas canvas, RectF rect, float radius, int alpha, float offsetX, float offsetY) {
        final boolean shifted = offsetX != 0 || offsetY != 0;
        if (shifted) {
            canvas.save();
            canvas.translate(-offsetX, -offsetY);
        }
        drawable.setRadius(radius);
        drawable.setAlpha(alpha);
        drawable.setBounds(
                Math.round(rect.left + offsetX), Math.round(rect.top + offsetY),
                Math.round(rect.right + offsetX), Math.round(rect.bottom + offsetY));
        drawable.draw(canvas);
        if (shifted) {
            canvas.restore();
        }
    }

    private static BlurredBackgroundProviderBuilder scrimMenuGlass(Theme.ResourcesProvider resourcesProvider) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
                .setBackgroundColor((r, isDark) -> {
                    final int color = Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground);
                    if (!LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR)) {
                        return color;
                    }
                    return Theme.multAlpha(color, isDark ? 0.85f : 0.825f);
                })
                .setStrokeColorTop(0, 0)
                .setStrokeColorBottom(0, 0)
                .setStrokeColorFull(0, 0)
                .setStrokeWidth(0, 0);
    }

    public static BlurredBackgroundProvider scrimMenuBackground(Theme.ResourcesProvider resourcesProvider) {
        return scrimMenuGlass(resourcesProvider)
                .setShadowColor(0x26000000, 0)
                .setShadowLayer(dpf2(4f), 0, 0)
                .setShadowAlwaysVisible()
                .build();
    }

    public static BlurredBackgroundProvider scrimMenuBackgroundFill(Theme.ResourcesProvider resourcesProvider) {
        return scrimMenuGlass(resourcesProvider)
                .setShadowColor(0, 0)
                .build();
    }
}
