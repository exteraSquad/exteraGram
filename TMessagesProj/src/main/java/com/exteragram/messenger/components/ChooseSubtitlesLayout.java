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

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PopupSwipeBackLayout;

public class ChooseSubtitlesLayout {

    public final ActionBarPopupWindow.ActionBarPopupWindowLayout layout;
    private final ActionBarMenuSubItem disableItem;

    public ChooseSubtitlesLayout(Context context, PopupSwipeBackLayout swipeBackLayout, Callback callback) {
        layout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, 0, null);
        layout.setFitItems(true);

        ActionBarMenuSubItem backItem = ActionBarMenuItem.addItem(layout, R.drawable.msg_arrow_back, LocaleController.getString(R.string.Back), false, null);
        backItem.setOnClickListener(view -> swipeBackLayout.closeForeground());
        backItem.setColors(0xfffafafa, 0xfffafafa);
        backItem.setSelectorColor(0x0fffffff);

        FrameLayout gap = new FrameLayout(context);
        gap.setMinimumWidth(dp(196));
        gap.setBackgroundColor(0xff181818);
        layout.addView(gap);
        LinearLayout.LayoutParams gapLayoutParams = (LinearLayout.LayoutParams) gap.getLayoutParams();
        gapLayoutParams.gravity = LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT;
        gapLayoutParams.width = LayoutHelper.MATCH_PARENT;
        gapLayoutParams.height = dp(8);
        gap.setLayoutParams(gapLayoutParams);

        ActionBarMenuSubItem chooseItem = ActionBarMenuItem.addItem(layout, R.drawable.msg_folders, LocaleController.getString(R.string.ChooseSubtitles), false, null);
        chooseItem.setColors(0xfffafafa, 0xfffafafa);
        chooseItem.setSelectorColor(0x0fffffff);
        chooseItem.setOnClickListener(view -> callback.onChooseSubtitles());

        disableItem = ActionBarMenuItem.addItem(layout, R.drawable.msg_cancel, LocaleController.getString(R.string.DisableSubtitles), false, null);
        disableItem.setColors(0xfffafafa, 0xfffafafa);
        disableItem.setSelectorColor(0x0fffffff);
        disableItem.setOnClickListener(view -> callback.onDisableSubtitles());
    }

    public void update(boolean hasSubtitles) {
        disableItem.setVisibility(hasSubtitles ? View.VISIBLE : View.GONE);
    }

    public interface Callback {
        void onChooseSubtitles();
        void onDisableSubtitles();
    }
}