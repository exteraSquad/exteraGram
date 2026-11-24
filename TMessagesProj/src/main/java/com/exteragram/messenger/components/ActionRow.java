/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.util.ArrayList;
import java.util.List;

@SuppressLint("ViewConstructor")
public class ActionRow extends FrameLayout {

    private final int HORIZONTAL_PADDING_DP = 10;
    private final int ITEM_SIZE_DP = 40;
    private final int GAP_DP = 8;

    private final FrameLayout buttonsView;
    private final List<ActionItem> currentItems = new ArrayList<>();

    public static class ActionItem {
        @DrawableRes
        public int icon;
        public boolean enabled;
        public View.OnClickListener action;
        @Nullable
        public View.OnLongClickListener longAction;

        public ActionItem(@DrawableRes int icon, boolean enabled, View.OnClickListener action) {
            this(icon, enabled, action, null);
        }

        public ActionItem(@DrawableRes int icon, boolean enabled, View.OnClickListener action, @Nullable View.OnLongClickListener longAction) {
            this.icon = icon;
            this.enabled = enabled;
            this.action = action;
            this.longAction = longAction;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ActionItem that = (ActionItem) o;
            return icon == that.icon;
        }

        @Override
        public int hashCode() {
            return icon;
        }
    }

    public ActionRow(Context context, Theme.ResourcesProvider resourcesProvider, List<ActionItem> items) {
        super(context);

        buttonsView = new FrameLayout(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                for (int i = 0; i < getChildCount(); i++) {
                    int dist = ((right - left) - dp(HORIZONTAL_PADDING_DP * 2 + ITEM_SIZE_DP * getChildCount())) / (getChildCount() - 1 > 0 ? getChildCount() - 1 : 1);
                    int l = dp(HORIZONTAL_PADDING_DP + ITEM_SIZE_DP * i) + dist * i;
                    int t = dp(GAP_DP);
                    View child = getChildAt(i);
                    child.layout(l, t, l + child.getMeasuredWidth(), t + child.getMeasuredHeight());
                }
            }
        };
        addView(buttonsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        updateItems(items, resourcesProvider);
    }

    public void updateItems(List<ActionItem> items, Theme.ResourcesProvider resourcesProvider) {
        buttonsView.removeAllViews();
        currentItems.clear();

        List<ActionItem> disabledItems = new ArrayList<>();
        int addedCount = 0;

        for (ActionItem item : items) {
            if (item.enabled && addedCount < 4) {
                addImageButton(getContext(), resourcesProvider, buttonsView, item, addedCount);
                currentItems.add(item);
                addedCount++;
            } else {
                disabledItems.add(item);
            }
        }

        for (ActionItem item : disabledItems) {
            if (addedCount < 4) {
                addImageButton(getContext(), resourcesProvider, buttonsView, item, addedCount);
                currentItems.add(item);
                addedCount++;
            } else {
                break;
            }
        }
    }

    private void addImageButton(Context context, Theme.ResourcesProvider resourcesProvider, FrameLayout buttonsView, ActionItem item, int index) {
        ImageView button = new ImageView(context) {
            @Override
            public void setEnabled(boolean enabled) {
                super.setEnabled(enabled);
                setAlpha(enabled ? 1f : 0.5f);
            }
        };
        ScaleStateListAnimator.apply(button, .15f, 1.5f);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setEnabled(item.enabled);
        button.setImageDrawable(ContextCompat.getDrawable(context, item.icon).mutate());
        button.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, resourcesProvider),
                PorterDuff.Mode.MULTIPLY
        ));
        button.setBackground(Theme.createSelectorDrawable(
                Theme.getColor(Theme.key_dialogButtonSelector, resourcesProvider),
                1,
                dp(ITEM_SIZE_DP / 2f)
        ));
        button.setOnClickListener(item.action);
        if (item.longAction != null) {
            button.setOnLongClickListener(item.longAction);
        }
        button.setTag(item);

        button.setAlpha(0f);
        button.setTranslationX(dp(12));

        buttonsView.addView(button, LayoutHelper.createFrame(ITEM_SIZE_DP, ITEM_SIZE_DP, Gravity.LEFT | Gravity.TOP));

        button.post(() -> button.animate()
                .alpha(item.enabled ? 1f : 0.5f)
                .translationX(0)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setStartDelay(100L + index * 35L)
                .setDuration(400L)
                .start());
    }

    public boolean isItemPresent(@DrawableRes int iconResId) {
        for (int i = 0; i < buttonsView.getChildCount(); i++) {
            View child = buttonsView.getChildAt(i);
            if (child instanceof ImageView) {
                Object tag = child.getTag();
                if (tag instanceof ActionItem && ((ActionItem) tag).icon == iconResId) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isItemPresent(ActionItem item) {
        return currentItems.contains(item);
    }
}