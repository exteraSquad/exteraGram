/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.drawer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import static org.telegram.messenger.AndroidUtilities.dp;

import androidx.annotation.NonNull;

public class DrawerMenuView extends ScrollView {
    private static final int CONTAINER_VERTICAL_PADDING_DP = 8;
    private static final float DIVIDER_HEIGHT_DP = 1f / AndroidUtilities.density;
    private static final int DIVIDER_HORIZONTAL_MARGIN_DP = 12;
    private static final int DIVIDER_VERTICAL_MARGIN_DP = 8;

    private static final int COLOR_KEY_BACKGROUND = Theme.key_windowBackgroundWhite;

    private final LinearLayout container;
    private Runnable onItemClick;

    private final Paint topGradientPaint = new Paint();
    private LinearGradient topGradient;
    private int lastGradientColor;

    public DrawerMenuView(Context context) {
        super(context);
        setVerticalScrollBarEnabled(false);

        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, dp(CONTAINER_VERTICAL_PADDING_DP), 0, dp(CONTAINER_VERTICAL_PADDING_DP) + AndroidUtilities.navigationBarHeight);
        addView(container, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        updateGradient();
    }

    public void setOnItemClick(Runnable onItemClick) {
        this.onItemClick = onItemClick;
    }

    public void clearMenu() {
        container.removeAllViews();
    }

    public void rebuildMenu(int currentAccount, BaseFragment fragment) {
        clearMenu();
        var menuContext = MainMenuHelper.createMenuContext(currentAccount, fragment);
        boolean hasVisibleItems = false;
        boolean pendingDivider = false;

        for (int i = 0; i < MainMenuLayout.getMainMenuLayout().size(); i++) {
            int id = MainMenuLayout.getMainMenuLayout().get(i);

            if (id == MainMenuItem.DIVIDER.getId()) {
                if (hasVisibleItems) {
                    pendingDivider = true;
                }
                continue;
            }

            var items = MainMenuHelper.resolveDrawerMenuItems(id, menuContext);
            if (items.isEmpty()) {
                continue;
            }

            if (pendingDivider) {
                var divider = new View(getContext());
                divider.setBackgroundColor(Theme.getDividerColor(null));
                container.addView(divider, createDividerLayoutParams());
                pendingDivider = false;
            }

            for (var info : items) {
                var itemView = new DrawerMenuItemView(getContext());
                itemView.setMenuItem(id, currentAccount, info.iconRes(), info.text());
                itemView.setOnClickListener(v -> {
                    if (onItemClick != null) {
                        onItemClick.run();
                    }
                    if (info.onClick() != null) {
                        info.onClick().run();
                    }
                });
                if (info.onLongClick() != null) {
                    itemView.setOnLongClickListener(v -> {
                        if (onItemClick != null) {
                            onItemClick.run();
                        }
                        info.onLongClick().run();
                        return true;
                    });
                }
                container.addView(itemView);
            }
            hasVisibleItems = true;
        }
    }

    public void updateUnreadCounters(int currentAccount) {
        for (int i = 0; i < container.getChildCount(); i++) {
            var child = container.getChildAt(i);
            if (child instanceof DrawerMenuItemView itemView) {
                itemView.updateUnreadCounter(currentAccount);
            }
        }
    }

    public void updateColors() {
        for (int i = 0; i < container.getChildCount(); i++) {
            var child = container.getChildAt(i);
            if (child instanceof DrawerMenuItemView itemView) {
                itemView.updateColors();
            } else {
                child.setBackgroundColor(Theme.getDividerColor(null));
            }
        }
        updateGradient();
    }

    private void updateGradient() {
        int color = Theme.getColor(COLOR_KEY_BACKGROUND);
        if (topGradient == null || color != lastGradientColor) {
            lastGradientColor = color;
            topGradient = new LinearGradient(0, 0, 0, dp(16), new int[]{color, 0x00ffffff & color}, null, Shader.TileMode.CLAMP);
            topGradientPaint.setShader(topGradient);
            invalidate();
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        if (getScrollY() > 0) {
            canvas.save();
            canvas.translate(0, getScrollY());
            canvas.drawRect(0, 0, getWidth(), dp(16), topGradientPaint);
            canvas.restore();
        }
    }

    private static LinearLayout.LayoutParams createDividerLayoutParams() {
        return LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT,
                DIVIDER_HEIGHT_DP,
                Gravity.BOTTOM | Gravity.FILL_HORIZONTAL,
                DIVIDER_HORIZONTAL_MARGIN_DP,
                DIVIDER_VERTICAL_MARGIN_DP,
                DIVIDER_HORIZONTAL_MARGIN_DP,
                DIVIDER_VERTICAL_MARGIN_DP
        );
    }
}
