/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.view.View;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class SpeedPitchMenuItem {

    private final ActionBarMenuItem.Item gap;
    private final ActionBarMenuItem.Item item;
    private final ActionBarMenuSubItem cell;

    public SpeedPitchMenuItem(ActionBarMenuItem menuItem, Theme.ResourcesProvider resourcesProvider) {
        cell = new ActionBarMenuSubItem(menuItem.getContext(), 2, false, false, resourcesProvider);
        cell.setTextAndIcon(getString(R.string.PlayerSpeedPitch), R.drawable.msg_tone_on);
        cell.setMinimumWidth(dp(196));
        cell.setOnClickListener(v -> {
            ExteraConfig.setPlayerSpeedPitch(!ExteraConfig.getPlayerSpeedPitch());
            cell.setChecked(ExteraConfig.getPlayerSpeedPitch());
            MediaController.getInstance().restoreMusicPlaybackSpeed();
        });
        gap = menuItem.lazilyAddColoredGap();
        item = menuItem.lazilyAddView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
    }

    public void update(boolean isMusic) {
        final int visibility = isMusic && ExteraConfig.getNewPlayerStyle() ? View.VISIBLE : View.GONE;
        gap.setVisibility(visibility);
        item.setVisibility(visibility);
        cell.setChecked(ExteraConfig.getPlayerSpeedPitch());
    }
}
