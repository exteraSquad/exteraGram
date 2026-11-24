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
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.FiltersView;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ItemOptions;

public final class ChatSearchFilterUiHelper {

    private static final int MENU_ICON_SIZE_DP = 24;
    private static final int CHIP_ICON_SIZE_DP = 18;

    private record Type(int iconResId, int titleResId, TLRPC.MessagesFilter filter, boolean groupsOnly) {
    }

    private static final Type[] TYPES = {
            new Type(R.drawable.msg_photos, R.string.SearchFilterPhotos, new TLRPC.TL_inputMessagesFilterPhotos(), false),
            new Type(R.drawable.msg_videocall, R.string.SearchFilterVideos, new TLRPC.TL_inputMessagesFilterVideo(), false),
            new Type(R.drawable.msg_sendfile, R.string.SharedFilesTab2, new TLRPC.TL_inputMessagesFilterDocument(), false),
            new Type(R.drawable.msg_link, R.string.SharedLinksTab2, new TLRPC.TL_inputMessagesFilterUrl(), false),
            new Type(R.drawable.msg_voice_headphones, R.string.SharedMusicTab2, new TLRPC.TL_inputMessagesFilterMusic(), false),
            new Type(R.drawable.input_mic, R.string.SearchFilterVoice, new TLRPC.TL_inputMessagesFilterVoice(), false),
            new Type(R.drawable.input_video, R.string.SearchFilterRound, new TLRPC.TL_inputMessagesFilterRoundVideo(), false),
            new Type(R.drawable.msg_gif, R.string.SharedGIFsTab2, new TLRPC.TL_inputMessagesFilterGif(), false),
            new Type(R.drawable.menu_poll_notify, R.string.SharedPollTab, new TLRPC.TL_inputMessagesFilterPoll(), false),
            new Type(R.drawable.msg_location, R.string.SearchFilterLocations, new TLRPC.TL_inputMessagesFilterGeo(), false),
            new Type(R.drawable.msg_pin, R.string.SearchFilterPinned, new TLRPC.TL_inputMessagesFilterPinned(), false),
            new Type(R.drawable.msg_mention, R.string.SearchFilterMentions, new TLRPC.TL_inputMessagesFilterMyMentions(), true),
    };

    private final ChatActivity chatActivity;
    private ActionBarMenuItem searchItem;
    private Runnable onChanged;
    private ImageView button;
    private FiltersView.MediaFilterData selected;

    public ChatSearchFilterUiHelper(ChatActivity chatActivity) {
        this.chatActivity = chatActivity;
    }

    public ImageView createButton(Context context, Theme.ResourcesProvider resourcesProvider, ActionBarMenuItem searchItem, Runnable onChanged) {
        this.searchItem = searchItem;
        this.onChanged = onChanged;
        selected = null;
        button = new ImageView(context);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setImageResource(R.drawable.msg_filter);
        button.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_searchPanelIcons, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        button.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector, resourcesProvider), 1));
        button.setContentDescription(getString(R.string.SearchFilterByType));
        button.setOnClickListener(v -> showMenu());
        return button;
    }

    public static int getButtonLeftMargin(View... buttonsBefore) {
        float margin = 0;
        for (View view : buttonsBefore) {
            if (view != null && view.getVisibility() != View.GONE) {
                margin += 45.33f;
            }
        }
        return dp(margin);
    }

    public static int getCountTextLeftMargin(View... buttons) {
        int visible = 0;
        for (View view : buttons) {
            if (view != null && view.getVisibility() != View.GONE) {
                visible++;
            }
        }
        return dp(visible == 0 ? 18 : 45.33f * visible + 14.67f);
    }

    public TLRPC.MessagesFilter getFilter() {
        return selected != null ? selected.filter : null;
    }

    public boolean onFilterCleared(FiltersView.MediaFilterData filterData) {
        if (filterData == null || filterData != selected) {
            return false;
        }
        selected = null;
        return true;
    }

    public void clear() {
        if (selected != null) {
            searchItem.removeSearchFilter(selected);
            selected = null;
        }
    }

    private void showMenu() {
        final TLRPC.Chat chat = chatActivity.getCurrentChat();
        final boolean group = chat != null && (!ChatObject.isChannel(chat) || chat.megagroup);
        final int[] location = new int[2];
        button.getLocationInWindow(location);
        final int buttonTop = location[1];
        final View actionBar = chatActivity.getActionBar();
        actionBar.getLocationInWindow(location);
        final int availableHeight = buttonTop - location[1] - actionBar.getMeasuredHeight();
        final ItemOptions options = ItemOptions.makeOptions(chatActivity, button)
                .setGravity(Gravity.NO_GRAVITY)
                .forceTop(true)
                .followScrimView()
                .setMaxHeight(availableHeight)
                .setScrimViewBackground(Theme.createCircleDrawable(dp(40), Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, chatActivity.getResourceProvider())));
        for (Type type : TYPES) {
            if (type.groupsOnly && !group) {
                continue;
            }
            options.addChecked(selected != null && selected.filter == type.filter, getMenuIcon(type.iconResId), getString(type.titleResId), () -> select(type));
        }
        options.show();
    }

    private Drawable getMenuIcon(int iconResId) {
        final LayerDrawable icon = new LayerDrawable(new Drawable[]{ContextCompat.getDrawable(chatActivity.getContext(), iconResId).mutate()});
        icon.setLayerSize(0, dp(MENU_ICON_SIZE_DP), dp(MENU_ICON_SIZE_DP));
        return icon;
    }

    private void select(Type type) {
        final boolean deselect = selected != null && selected.filter == type.filter;
        if (selected != null) {
            searchItem.removeSearchFilter(selected);
            selected = null;
        }
        if (!deselect) {
            selected = new FiltersView.MediaFilterData(type.iconResId, type.titleResId, type.filter, FiltersView.FILTER_TYPE_MEDIA);
            selected.iconSize = CHIP_ICON_SIZE_DP;
            searchItem.addSearchFilter(selected);
        }
        AndroidUtilities.hideKeyboard(searchItem.getSearchField());
        onChanged.run();
    }
}
