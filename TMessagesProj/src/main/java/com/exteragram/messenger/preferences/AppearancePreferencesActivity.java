/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2022.

*/

package com.exteragram.messenger.preferences;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.ExteraUtils;
import com.exteragram.messenger.components.MainScreenSetupCell;
import com.exteragram.messenger.components.FabShapeCell;
import com.exteragram.messenger.components.SolarIconsPreview;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.LaunchActivity;

public class AppearancePreferencesActivity extends BasePreferencesActivity {

    private ValueAnimator statusBarColorAnimate;
    private Parcelable recyclerViewState = null;

    SolarIconsPreview solarIconsPreview;

    MainScreenSetupCell mainScreenSetupCell;
    private final CharSequence[] styles = new CharSequence[]{
            LocaleController.getString("Default", R.string.Default),
            LocaleController.getString("TabStyleRounded", R.string.TabStyleRounded),
            LocaleController.getString("TabStyleTextOnly", R.string.TabStyleTextOnly),
            LocaleController.getString("TabStyleChips", R.string.TabStyleChips),
            LocaleController.getString("TabStylePills", R.string.TabStylePills),
    }, titles = new CharSequence[]{
            LocaleController.getString("exteraAppName", R.string.exteraAppName),
            LocaleController.getString("SearchAllChatsShort", R.string.SearchAllChatsShort),
            LocaleController.getString("ActionBarTitleUsername", R.string.ActionBarTitleUsername),
            LocaleController.getString("ActionBarTitleName", R.string.ActionBarTitleName)
    }, events = new CharSequence[]{
            LocaleController.getString("DependsOnTheDate", R.string.DependsOnTheDate),
            LocaleController.getString("Default", R.string.Default),
            LocaleController.getString("NewYear", R.string.NewYear),
            LocaleController.getString("ValentinesDay", R.string.ValentinesDay),
            LocaleController.getString("Halloween", R.string.Halloween)
    };

    private int mainScreenHeaderRow;
    private int actionBarSetupRow;
    private int hideActionBarStatusRow;
    private int centerTitleRow;
    private int hideAllChatsRow;
    private int tabStyleRow;
    private int actionBarTitle;
    private int mainScreenInfoRow;

    private int solarIconsHeaderRow;
    private int solarIconsPreviewRow;
    private int solarIconsRow;
    private int solarIconsInfoRow;

    private int applicationHeaderRow;
    private int fabShapeRow;
    private int useSystemFontsRow;
    private int useSystemEmojiRow;
    private int transparentStatusBarRow;
    private int blurForAllThemesRow;
    private int newSwitchStyleRow;
    private int disableDividersRow;
    private int transparentNavBarRow;
    private int transparentNavBarInfoRow;

    private int iconsHeaderRow;
    private int eventChooserRow;
    private int forceSnowRow;
    private int iconsDividerRow;

    private int drawerHeaderRow;
    private int statusRow;
    private int newGroupRow;
    private int newSecretChatRow;
    private int newChannelRow;
    private int contactsRow;
    private int callsRow;
    private int peopleNearbyRow;
    private int archivedChatsRow;
    private int savedMessagesRow;
    private int scanQrRow;
    private int inviteFriendsRow;
    private int telegramFeaturesRow;
    private int drawerDividerRow;

    @Override
    protected void updateRowsId() {
        super.updateRowsId();

        mainScreenHeaderRow = newRow();
        actionBarSetupRow = newRow();
        hideActionBarStatusRow = getUserConfig().isPremium() ? newRow() : -1;
        hideAllChatsRow = newRow();
        centerTitleRow = newRow();
        tabStyleRow = newRow();
        actionBarTitle = newRow();
        mainScreenInfoRow = newRow();

        solarIconsHeaderRow = newRow();
        solarIconsPreviewRow = newRow();
        solarIconsRow = newRow();
        solarIconsInfoRow = newRow();

        applicationHeaderRow = newRow();
        fabShapeRow = newRow();
        useSystemFontsRow = newRow();
        useSystemEmojiRow = newRow();
        blurForAllThemesRow = newRow();
        newSwitchStyleRow = newRow();
        disableDividersRow = newRow();
        transparentStatusBarRow = newRow();
        transparentNavBarRow = newRow();
        transparentNavBarInfoRow = newRow();

        iconsHeaderRow = newRow();
        eventChooserRow = newRow();
        forceSnowRow = newRow();
        iconsDividerRow = newRow();

        drawerHeaderRow = newRow();
        statusRow = getUserConfig().isPremium() ? newRow() : -1;
        archivedChatsRow = newRow();
        newGroupRow = newRow();
        newSecretChatRow = newRow();
        newChannelRow = newRow();
        contactsRow = newRow();
        callsRow = newRow();
        peopleNearbyRow = ExteraUtils.hasGps() ? newRow() : -1;
        savedMessagesRow = newRow();
        scanQrRow = newRow();
        inviteFriendsRow = newRow();
        telegramFeaturesRow = newRow();
        drawerDividerRow = newRow();
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == useSystemFontsRow) {
            ExteraConfig.editor.putBoolean("useSystemFonts", ExteraConfig.useSystemFonts ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.useSystemFonts);
            AndroidUtilities.clearTypefaceCache();
            if (getListView().getLayoutManager() != null)
                recyclerViewState = getListView().getLayoutManager().onSaveInstanceState();
            parentLayout.rebuildAllFragmentViews(true, true);
            getListView().getLayoutManager().onRestoreInstanceState(recyclerViewState);
        } else if (position == useSystemEmojiRow) {
            SharedConfig.toggleUseSystemEmoji();
            ((TextCheckCell) view).setChecked(SharedConfig.useSystemEmoji);
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == transparentStatusBarRow) {
            SharedConfig.toggleNoStatusBar();
            ((TextCheckCell) view).setChecked(SharedConfig.noStatusBar);
            int color = Theme.getColor(Theme.key_actionBarDefault, null, true);
            int alpha = ColorUtils.calculateLuminance(color) > 0.7f ? 0x0f : 0x33;
            if (statusBarColorAnimate != null && statusBarColorAnimate.isRunning()) {
                statusBarColorAnimate.end();
            }
            statusBarColorAnimate = SharedConfig.noStatusBar ? ValueAnimator.ofInt(alpha, 0) : ValueAnimator.ofInt(0, alpha);
            statusBarColorAnimate.setDuration(200);
            statusBarColorAnimate.addUpdateListener(animation -> getParentActivity().getWindow().setStatusBarColor(ColorUtils.setAlphaComponent(0, (int) animation.getAnimatedValue())));
            statusBarColorAnimate.start();
        } else if (position == blurForAllThemesRow) {
            ExteraConfig.editor.putBoolean("blurForAllThemes", ExteraConfig.blurForAllThemes ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.blurForAllThemes);
            showBulletin();
        } else if (position == centerTitleRow) {
            ExteraConfig.editor.putBoolean("centerTitle", ExteraConfig.centerTitle ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.centerTitle);
            parentLayout.rebuildAllFragmentViews(false, false);
            mainScreenSetupCell.setCenteredTitle(true);
        } else if (position == hideAllChatsRow) {
            ExteraConfig.editor.putBoolean("hideAllChats", ExteraConfig.hideAllChats ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.hideAllChats);
            parentLayout.rebuildAllFragmentViews(false, false);
            mainScreenSetupCell.setTabName(true);
        } else if (position == newSwitchStyleRow) {
            ExteraConfig.editor.putBoolean("newSwitchStyle", ExteraConfig.newSwitchStyle ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.newSwitchStyle);
            if (getListView().getLayoutManager() != null)
                recyclerViewState = getListView().getLayoutManager().onSaveInstanceState();
            parentLayout.rebuildAllFragmentViews(true, true);
            getListView().getLayoutManager().onRestoreInstanceState(recyclerViewState);
        } else if (position == disableDividersRow) {
            ExteraConfig.editor.putBoolean("disableDividers", ExteraConfig.disableDividers ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.disableDividers);
            if (getListView().getLayoutManager() != null)
                recyclerViewState = getListView().getLayoutManager().onSaveInstanceState();
            parentLayout.rebuildAllFragmentViews(true, true);
            getListView().getLayoutManager().onRestoreInstanceState(recyclerViewState);
        } else if (position == transparentNavBarRow) {
            ExteraConfig.editor.putBoolean("transparentNavBar", ExteraConfig.transparentNavBar ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.transparentNavBar);
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == statusRow) {
            ExteraConfig.toggleDrawerElements(12);
            ((TextCell) view).setChecked(ExteraConfig.changeStatus);
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == newGroupRow) {
            ExteraConfig.toggleDrawerElements(1);
            ((TextCell) view).setChecked(ExteraConfig.newGroup);
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == newSecretChatRow) {
            ExteraConfig.toggleDrawerElements(2);
            ((TextCell) view).setChecked(ExteraConfig.newSecretChat);
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == newChannelRow) {
            ExteraConfig.toggleDrawerElements(3);
            ((TextCell) view).setChecked(ExteraConfig.newChannel);
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == contactsRow) {
            ExteraConfig.toggleDrawerElements(4);
            ((TextCell) view).setChecked(ExteraConfig.contacts);
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == callsRow) {
            ExteraConfig.toggleDrawerElements(5);
            ((TextCell) view).setChecked(ExteraConfig.calls);
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == peopleNearbyRow) {
            ExteraConfig.toggleDrawerElements(6);
            ((TextCell) view).setChecked(ExteraConfig.peopleNearby);
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == archivedChatsRow) {
            ExteraConfig.toggleDrawerElements(7);
            ((TextCell) view).setChecked(ExteraConfig.archivedChats);
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == savedMessagesRow) {
            ExteraConfig.toggleDrawerElements(8);
            ((TextCell) view).setChecked(ExteraConfig.savedMessages);
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == scanQrRow) {
            ExteraConfig.toggleDrawerElements(9);
            ((TextCell) view).setChecked(ExteraConfig.scanQr);
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == inviteFriendsRow) {
            ExteraConfig.toggleDrawerElements(10);
            ((TextCell) view).setChecked(ExteraConfig.inviteFriends);
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == telegramFeaturesRow) {
            ExteraConfig.toggleDrawerElements(11);
            ((TextCell) view).setChecked(ExteraConfig.telegramFeatures);
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == forceSnowRow) {
            ExteraConfig.editor.putBoolean("forceDrawerSnow", ExteraConfig.forceDrawerSnow ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.forceDrawerSnow);
            if (SharedConfig.useLNavigation) showBulletin();
        } else if (position == eventChooserRow) {
            if (getParentActivity() == null) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("DrawerIconPack", R.string.DrawerIconPack));
            builder.setItems(events, new int[]{
                    R.drawable.msg_calendar2,
                    R.drawable.msg_block,
                    R.drawable.msg_settings_ny,
                    R.drawable.msg_saved_14,
                    R.drawable.msg_contacts_hw
            }, (dialog, which) -> {
                ExteraConfig.editor.putInt("eventType", ExteraConfig.eventType = which).apply();
                RecyclerView.ViewHolder holder = getListView().findViewHolderForAdapterPosition(eventChooserRow);
                if (holder != null) {
                    listAdapter.onBindViewHolder(holder, eventChooserRow);
                }
                if (getListView().getLayoutManager() != null)
                    recyclerViewState = getListView().getLayoutManager().onSaveInstanceState();
                parentLayout.rebuildAllFragmentViews(true, true);
                getListView().getLayoutManager().onRestoreInstanceState(recyclerViewState);
            });
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            showDialog(builder.create());
        } else if (position == hideActionBarStatusRow) {
            ExteraConfig.editor.putBoolean("hideActionBarStatus", ExteraConfig.hideActionBarStatus ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.hideActionBarStatus);
            mainScreenSetupCell.setStatusVisibility(true);
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == actionBarTitle) {
            if (getParentActivity() == null) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("ActionBarTitle", R.string.ActionBarTitle));

            LinearLayout linearLayout = new LinearLayout(getParentActivity());
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            builder.setView(linearLayout);

            for (int a = 0; a < titles.length; a++) {
                RadioColorCell cell = new RadioColorCell(getParentActivity());
                cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
                cell.setTag(a);
                cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
                cell.setTextAndValue(titles[a], ExteraConfig.actionBarTitle == a);
                cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
                linearLayout.addView(cell);
                cell.setOnClickListener(v -> {
                    Integer which = (Integer) v.getTag();
                    ExteraConfig.editor.putInt("actionBarTitle", ExteraConfig.actionBarTitle = which).apply();
                    mainScreenSetupCell.setTitle(true);
                    parentLayout.rebuildAllFragmentViews(false, false);
                    ((TextSettingsCell) view).setTextAndValue(LocaleController.getString("ActionBarTitle", R.string.ActionBarTitle), titles[which], true, false);
                    builder.getDismissRunnable().run();
                });
            }
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            showDialog(builder.create());
        } else if (position == tabStyleRow) {
            if (getParentActivity() == null) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("TabStyle", R.string.TabStyle));

            LinearLayout linearLayout = new LinearLayout(getParentActivity());
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            builder.setView(linearLayout);

            for (int a = 0; a < styles.length; a++) {
                RadioColorCell cell = new RadioColorCell(getParentActivity());
                cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
                cell.setTag(a);
                cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
                cell.setTextAndValue(styles[a], ExteraConfig.tabStyle == a);
                cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
                linearLayout.addView(cell);
                cell.setOnClickListener(v -> {
                    Integer which = (Integer) v.getTag();
                    ExteraConfig.editor.putInt("tabStyle", ExteraConfig.tabStyle = which).apply();
                    mainScreenSetupCell.setTabStyle(true);
                    parentLayout.rebuildAllFragmentViews(false, false);
                    ((TextSettingsCell) view).setTextAndValue(LocaleController.getString("TabStyle", R.string.TabStyle), styles[which], true, true);
                    builder.getDismissRunnable().run();
                });
            }
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            showDialog(builder.create());
        } else if (position == solarIconsRow) {
            ((TextCheckCell) view).setChecked(!ExteraConfig.useSolarIcons);
            solarIconsPreview.updateIcons(true);
            parentLayout.rebuildAllFragmentViews(false, false);
        }
    }

    @Override
    protected String getTitle() {
        return LocaleController.getString("Appearance", R.string.Appearance);
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            switch (type) {
                case 12:
                    FabShapeCell fabShapeCell = new FabShapeCell(mContext) {
                        @Override
                        protected void rebuildFragments() {
                            parentLayout.rebuildAllFragmentViews(false, false);
                        }
                    };
                    fabShapeCell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                    return new RecyclerListView.Holder(fabShapeCell);
                case 14:
                    mainScreenSetupCell = new MainScreenSetupCell(mContext, parentLayout);
                    mainScreenSetupCell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                    return new RecyclerListView.Holder(mainScreenSetupCell);
                case 15:
                    solarIconsPreview = new SolarIconsPreview(mContext) {
                        @Override
                        protected void reloadResources() {
                            ((LaunchActivity) getParentActivity()).reloadIcons();
                        }
                    };
                    solarIconsPreview.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                    return new RecyclerListView.Holder(solarIconsPreview);
                default:
                    return super.onCreateViewHolder(parent, type);
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 1:
                    holder.itemView.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                case 3:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == applicationHeaderRow) {
                        headerCell.setText(LocaleController.getString("Appearance", R.string.Appearance));
                    } else if (position == drawerHeaderRow) {
                        headerCell.setText(LocaleController.getString("DrawerElements", R.string.DrawerElements));
                    } else if (position == iconsHeaderRow) {
                        headerCell.setText(LocaleController.getString("DrawerOptions", R.string.DrawerOptions));
                    } else if (position == mainScreenHeaderRow) {
                        headerCell.setText(LocaleController.getString("AvatarCorners", R.string.AvatarCorners));
                    } else if (position == solarIconsHeaderRow) {
                        headerCell.setText(LocaleController.getString("IconPack", R.string.IconPack));
                    }
                    break;
                case 5:
                    TextCheckCell textCheckCell = (TextCheckCell) holder.itemView;
                    textCheckCell.setEnabled(true, null);
                    if (position == transparentStatusBarRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("TransparentStatusBar", R.string.TransparentStatusBar), SharedConfig.noStatusBar, true);
                    } else if (position == useSystemFontsRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("UseSystemFonts", R.string.UseSystemFonts), ExteraConfig.useSystemFonts, true);
                    } else if (position == useSystemEmojiRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("UseSystemEmoji", R.string.UseSystemEmoji), SharedConfig.useSystemEmoji, true);
                    } else if (position == blurForAllThemesRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("BlurForAllThemes", R.string.BlurForAllThemes), ExteraConfig.blurForAllThemes, true);
                    } else if (position == centerTitleRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("CenterTitle", R.string.CenterTitle), ExteraConfig.centerTitle, true);
                    } else if (position == hideAllChatsRow) {
                        textCheckCell.setTextAndCheck(LocaleController.formatString("HideAllChats", R.string.HideAllChats, LocaleController.getString("AllChats", R.string.FilterAllChats)), ExteraConfig.hideAllChats, true);
                    } else if (position == newSwitchStyleRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("NewSwitchStyle", R.string.NewSwitchStyle), ExteraConfig.newSwitchStyle, true);
                    } else if (position == disableDividersRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("DisableDividers", R.string.DisableDividers), ExteraConfig.disableDividers, true);
                    } else if (position == transparentNavBarRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("TransparentNavBar", R.string.TransparentNavBar), ExteraConfig.transparentNavBar, false);
                    } else if (position == forceSnowRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("DrawerForceSnow", R.string.DrawerForceSnow), ExteraConfig.forceDrawerSnow, false);
                    } else if (position == hideActionBarStatusRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("HideActionBarStatus", R.string.HideActionBarStatus), ExteraConfig.hideActionBarStatus, true);
                    } else if (position == solarIconsRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("SolarIcons", R.string.SolarIcons), ExteraConfig.useSolarIcons, false);
                    }
                    break;
                case 2:
                    TextCell textCell = (TextCell) holder.itemView;
                    textCell.setEnabled(true);
                    int[] icons = ExteraUtils.getDrawerIconPack();
                    if (position == statusRow) {
                        textCell.setTextAndCheckAndIcon(LocaleController.getString("ChangeEmojiStatus", R.string.ChangeEmojiStatus), ExteraConfig.changeStatus, R.drawable.msg_status_edit, true);
                    } else if (position == newGroupRow) {
                        textCell.setTextAndCheckAndIcon(LocaleController.getString("NewGroup", R.string.NewGroup), ExteraConfig.newGroup, icons[0], true);
                    } else if (position == newSecretChatRow) {
                        textCell.setTextAndCheckAndIcon(LocaleController.getString("NewSecretChat", R.string.NewSecretChat), ExteraConfig.newSecretChat, icons[1], true);
                    } else if (position == newChannelRow) {
                        textCell.setTextAndCheckAndIcon(LocaleController.getString("NewChannel", R.string.NewChannel), ExteraConfig.newChannel, icons[2], true);
                    } else if (position == contactsRow) {
                        textCell.setTextAndCheckAndIcon(LocaleController.getString("Contacts", R.string.Contacts), ExteraConfig.contacts, icons[3], true);
                    } else if (position == callsRow) {
                        textCell.setTextAndCheckAndIcon(LocaleController.getString("Calls", R.string.Calls), ExteraConfig.calls, icons[4], true);
                    } else if (position == peopleNearbyRow) {
                        textCell.setTextAndCheckAndIcon(LocaleController.getString("PeopleNearby", R.string.PeopleNearby), ExteraConfig.peopleNearby, icons[8], true);
                    } else if (position == archivedChatsRow) {
                        textCell.setTextAndCheckAndIcon(LocaleController.getString("ArchivedChats", R.string.ArchivedChats), ExteraConfig.archivedChats, R.drawable.msg_archive, true);
                    } else if (position == savedMessagesRow) {
                        textCell.setTextAndCheckAndIcon(LocaleController.getString("SavedMessages", R.string.SavedMessages), ExteraConfig.savedMessages, icons[5], true);
                    } else if (position == scanQrRow) {
                        textCell.setTextAndCheckAndIcon(LocaleController.getString("AuthAnotherClient", R.string.AuthAnotherClient), ExteraConfig.scanQr, R.drawable.msg_qrcode, true);
                    } else if (position == inviteFriendsRow) {
                        textCell.setTextAndCheckAndIcon(LocaleController.getString("InviteFriends", R.string.InviteFriends), ExteraConfig.inviteFriends, icons[6], true);
                    } else if (position == telegramFeaturesRow) {
                        textCell.setTextAndCheckAndIcon(LocaleController.getString("TelegramFeatures", R.string.TelegramFeatures), ExteraConfig.telegramFeatures, icons[7], false);
                    }
                    break;
                case 7:
                    TextSettingsCell textSettingsCell = (TextSettingsCell) holder.itemView;
                    if (position == eventChooserRow) {
                        textSettingsCell.setTextAndValue(LocaleController.getString("DrawerIconPack", R.string.DrawerIconPack), events[ExteraConfig.eventType], true);
                    } else if (position == actionBarTitle) {
                        textSettingsCell.setTextAndValue(LocaleController.getString("ActionBarTitle", R.string.ActionBarTitle), titles[ExteraConfig.actionBarTitle], false);
                    } else if (position == tabStyleRow) {
                        textSettingsCell.setTextAndValue(LocaleController.getString("TabStyle", R.string.TabStyle), styles[ExteraConfig.tabStyle], true);
                    }
                    break;
                case 8:
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == transparentNavBarInfoRow) {
                        cell.setText(LocaleController.getString("TransparentNavBarInfo", R.string.TransparentNavBarInfo));
                    } else if (position == solarIconsInfoRow) {
                        cell.setText(addLinkSpan(LocaleController.getString("SolarIconsInfo", R.string.SolarIconsInfo), "@Design480", "@TierOhneNation"));

                        //cell.setText(addLinkSpan(LocaleController.getString("SolarIconsInfo", R.string.SolarIconsInfo), "@Design480") × addLinkSpan("R4IN80W", "@TierOhneNation"));
                    } else if (position == mainScreenInfoRow) {
                        cell.setText(LocaleController.getString("MainScreenSetupInfo", R.string.MainScreenSetupInfo));
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == iconsDividerRow || position == drawerDividerRow) {
                return 1;
            } else if (position == statusRow || position == newGroupRow || position == newSecretChatRow || position == newChannelRow ||
                    position == contactsRow || position == callsRow || position == peopleNearbyRow || position == archivedChatsRow ||
                    position == savedMessagesRow || position == scanQrRow || position == inviteFriendsRow || position == telegramFeaturesRow) {
                return 2;
            } else if (position == applicationHeaderRow || position == drawerHeaderRow || position == iconsHeaderRow || position == mainScreenHeaderRow || position == solarIconsHeaderRow) {
                return 3;
            } else if (position == eventChooserRow || position == actionBarTitle || position == tabStyleRow) {
                return 7;
            } else if (position == transparentNavBarInfoRow || position == solarIconsInfoRow || position == mainScreenInfoRow) {
                return 8;
            } else if (position == fabShapeRow) {
                return 12;
            } else if (position == actionBarSetupRow) {
                return 14;
            } else if (position == solarIconsPreviewRow) {
                return 15;
            }
            return 5;
        }
    }
}