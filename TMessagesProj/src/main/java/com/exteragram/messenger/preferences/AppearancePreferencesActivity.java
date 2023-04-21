/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.preferences;

import android.content.Context;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.ExteraUtils;
import com.exteragram.messenger.components.FabShapeCell;
import com.exteragram.messenger.components.MainScreenSetupCell;
import com.exteragram.messenger.components.SolarIconsPreview;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.LaunchActivity;

public class AppearancePreferencesActivity extends BasePreferencesActivity {

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
    }, tabIcons = new CharSequence[]{
            LocaleController.getString("TabTitleStyleTextWithIcons", R.string.TabTitleStyleTextWithIcons),
            LocaleController.getString("TabTitleStyleTextOnly", R.string.TabTitleStyleTextOnly),
            LocaleController.getString("TabTitleStyleIconsOnly", R.string.TabTitleStyleIconsOnly)
    }, events = new CharSequence[]{
            LocaleController.getString("DependsOnTheDate", R.string.DependsOnTheDate),
            LocaleController.getString("Default", R.string.Default),
            LocaleController.getString("NewYear", R.string.NewYear),
            LocaleController.getString("ValentinesDay", R.string.ValentinesDay),
            LocaleController.getString("Halloween", R.string.Halloween)
    };

    private int actionBarSetupRow;
    private int hideActionBarStatusRow;
    private int centerTitleRow;
    private int hideAllChatsRow;
    private int tabTitleRow;
    private int tabStyleRow;
    private int actionBarTitleRow;
    private int mainScreenInfoRow;

    private int solarIconsHeaderRow;
    private int solarIconsPreviewRow;
    private int solarIconsRow;
    private int solarIconsInfoRow;

    private int appearanceHeaderRow;
    private int fabShapeRow;
    private int forceBlurRow;
    private int forceSnowRow;
    private int useSystemFontsRow;
    private int useSystemEmojiRow;
    private int newSwitchStyleRow;
    private int disableDividersRow;
    private int alternativeNavigationRow;
    private int appearanceDividerRow;

    private int drawerOptionsHeaderRow;
    private int eventChooserRow;
    private int alternativeOpenAnimationRow;
    private int drawerOptionsDividerRow;

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

        actionBarSetupRow = newRow();
        hideActionBarStatusRow = getUserConfig().isPremium() ? newRow() : -1;
        hideAllChatsRow = newRow();
        centerTitleRow = newRow();
        tabStyleRow = newRow();
        tabTitleRow = newRow();
        actionBarTitleRow = newRow();
        mainScreenInfoRow = newRow();

        solarIconsHeaderRow = newRow();
        solarIconsPreviewRow = newRow();
        solarIconsRow = newRow();
        solarIconsInfoRow = newRow();

        appearanceHeaderRow = newRow();
        fabShapeRow = newRow();
        forceBlurRow = newRow();
        forceSnowRow = newRow();
        useSystemFontsRow = newRow();
        useSystemEmojiRow = newRow();
        newSwitchStyleRow = newRow();
        disableDividersRow = newRow();
        alternativeNavigationRow = newRow();
        appearanceDividerRow = newRow();

        drawerOptionsHeaderRow = newRow();
        eventChooserRow = newRow();
        alternativeOpenAnimationRow = newRow();
        drawerOptionsDividerRow = newRow();

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
        }  else if (position == forceBlurRow) {
            ExteraConfig.editor.putBoolean("forceBlur", ExteraConfig.forceBlur ^= true).apply();
            if (!SharedConfig.chatBlurEnabled() && ExteraConfig.forceBlur || SharedConfig.chatBlurEnabled() && !ExteraConfig.forceBlur) {
                SharedConfig.toggleChatBlur();
            }
            ((TextCheckCell) view).setChecked(ExteraConfig.forceBlur);
        } else if (position == alternativeOpenAnimationRow) {
            ExteraConfig.editor.putBoolean("alternativeOpenAnimation", ExteraConfig.alternativeOpenAnimation ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.alternativeOpenAnimation);
        } else if (position == alternativeNavigationRow) {
            SharedConfig.editor.putBoolean("useLNavigation", SharedConfig.useLNavigation ^= true).apply();
            if (SharedConfig.useLNavigation) {
                MessagesController.getGlobalMainSettings().edit().putBoolean("view_animations", true).apply();
                SharedConfig.setAnimationsEnabled(true);
            }
            SharedConfig.saveConfig();
            ((TextCheckCell) view).setChecked(SharedConfig.useLNavigation);
            parentLayout.rebuildAllFragmentViews(false, false);
            showBulletin();
        } else if (position == centerTitleRow) {
            ExteraConfig.editor.putBoolean("centerTitle", ExteraConfig.centerTitle ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.centerTitle);
            parentLayout.rebuildAllFragmentViews(false, false);
            mainScreenSetupCell.updateCenteredTitle(true);
        } else if (position == hideAllChatsRow) {
            ExteraConfig.editor.putBoolean("hideAllChats", ExteraConfig.hideAllChats ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.hideAllChats);
            parentLayout.rebuildAllFragmentViews(false, false);
            mainScreenSetupCell.updateTabName(true);
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
            ExteraConfig.editor.putBoolean("forceSnow", ExteraConfig.forceSnow ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.forceSnow);
            showBulletin();
        } else if (position == eventChooserRow) {
            if (getParentActivity() == null) {
                return;
            }
            ExteraUtils.showDialog(events, new int[]{
                    R.drawable.msg_calendar2, R.drawable.msg_block,
                    R.drawable.msg_settings_ny, R.drawable.msg_saved_14, R.drawable.msg_contacts_hw
            }, LocaleController.getString("DrawerIconSet", R.string.DrawerIconSet), ExteraConfig.eventType, getContext(), which -> {
                ExteraConfig.editor.putInt("eventType", ExteraConfig.eventType = which).apply();
                listAdapter.notifyItemChanged(eventChooserRow, payload);
                listAdapter.notifyItemRangeChanged(statusRow, 12);
                getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
            });
        } else if (position == hideActionBarStatusRow) {
            ExteraConfig.editor.putBoolean("hideActionBarStatus", ExteraConfig.hideActionBarStatus ^= true).apply();
            ((TextCheckCell) view).setChecked(ExteraConfig.hideActionBarStatus);
            mainScreenSetupCell.updateStatus(true);
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == actionBarTitleRow) {
            if (getParentActivity() == null) {
                return;
            }
            ExteraUtils.showDialog(titles, LocaleController.getString("ActionBarTitle", R.string.ActionBarTitle), ExteraConfig.actionBarTitle, getContext(), i -> {
                ExteraConfig.editor.putInt("actionBarTitle", ExteraConfig.actionBarTitle = i).apply();
                mainScreenSetupCell.updateTitle(true);
                parentLayout.rebuildAllFragmentViews(false, false);
                listAdapter.notifyItemChanged(actionBarTitleRow, payload);
            });
        } else if (position == tabTitleRow) {
            if (getParentActivity() == null) {
                return;
            }
            ExteraUtils.showDialog(tabIcons, LocaleController.getString("TabTitleStyle", R.string.TabTitleStyle), ExteraConfig.tabIcons, getContext(), i -> {
                ExteraConfig.editor.putInt("tabIcons", ExteraConfig.tabIcons = i).apply();
                listAdapter.notifyItemChanged(tabTitleRow, payload);
                getNotificationCenter().postNotificationName(NotificationCenter.dialogFiltersUpdated);
            });
        } else if (position == tabStyleRow) {
            if (getParentActivity() == null) {
                return;
            }
            ExteraUtils.showDialog(styles, LocaleController.getString("TabStyle", R.string.TabStyle), ExteraConfig.tabStyle, getContext(), i -> {
                ExteraConfig.editor.putInt("tabStyle", ExteraConfig.tabStyle = i).apply();
                mainScreenSetupCell.updateTabStyle(true);
                listAdapter.notifyItemChanged(tabStyleRow, payload);
                getNotificationCenter().postNotificationName(NotificationCenter.dialogFiltersUpdated);
            });
        } else if (position == solarIconsRow) {
            ((TextCheckCell) view).setChecked(!ExteraConfig.useSolarIcons);
            solarIconsPreview.updateIcons(true);
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
                            Theme.reloadAllResources(getParentActivity());
                            parentLayout.rebuildAllFragmentViews(false, false);
                        }
                    };
                    solarIconsPreview.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                    return new RecyclerListView.Holder(solarIconsPreview);
                default:
                    return super.onCreateViewHolder(parent, type);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean payload) {
            switch (holder.getItemViewType()) {
                case 1:
                    holder.itemView.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                case 3:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == appearanceHeaderRow) {
                        headerCell.setText(LocaleController.getString("Appearance", R.string.Appearance));
                    } else if (position == drawerHeaderRow) {
                        headerCell.setText(LocaleController.getString("DrawerElements", R.string.DrawerElements));
                    } else if (position == drawerOptionsHeaderRow) {
                        headerCell.setText(LocaleController.getString("DrawerOptions", R.string.DrawerOptions));
                    } else if (position == solarIconsHeaderRow) {
                        headerCell.setText(LocaleController.getString("IconPack", R.string.IconPack));
                    }
                    break;
                case 5:
                    TextCheckCell textCheckCell = (TextCheckCell) holder.itemView;
                    textCheckCell.setEnabled(true, null);
                    if (position == useSystemFontsRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("UseSystemFonts", R.string.UseSystemFonts), ExteraConfig.useSystemFonts, true);
                    } else if (position == useSystemEmojiRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("UseSystemEmoji", R.string.UseSystemEmoji), SharedConfig.useSystemEmoji, true);
                    } else if (position == forceBlurRow) {
                        textCheckCell.setTextAndValueAndCheck(LocaleController.getString("ForceBlur", R.string.ForceBlur), LocaleController.getString("ForceBlurInfo", R.string.ForceBlurInfo), ExteraConfig.forceBlur, true, true);
                    } else if (position == forceSnowRow) {
                        textCheckCell.setTextAndValueAndCheck(LocaleController.getString("ForceSnow", R.string.ForceSnow), LocaleController.getString("ForceSnowInfo", R.string.ForceSnowInfo), ExteraConfig.forceSnow, true, true);
                    } else if (position == alternativeNavigationRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("AlternativeNavigation", R.string.AlternativeNavigation), SharedConfig.useLNavigation, false);
                    } else if (position == centerTitleRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("CenterTitle", R.string.CenterTitle), ExteraConfig.centerTitle, true);
                    } else if (position == hideAllChatsRow) {
                        textCheckCell.setTextAndCheck(LocaleController.formatString("HideAllChats", R.string.HideAllChats, LocaleController.getString("AllChats", R.string.FilterAllChats)), ExteraConfig.hideAllChats, true);
                    } else if (position == newSwitchStyleRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("NewSwitchStyle", R.string.NewSwitchStyle), ExteraConfig.newSwitchStyle, true);
                    } else if (position == disableDividersRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("DisableDividers", R.string.DisableDividers), ExteraConfig.disableDividers, true);
                    } else if (position == hideActionBarStatusRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("HideActionBarStatus", R.string.HideActionBarStatus), ExteraConfig.hideActionBarStatus, true);
                    } else if (position == solarIconsRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("SolarIcons", R.string.SolarIcons), ExteraConfig.useSolarIcons, false);
                    } else if (position == alternativeOpenAnimationRow) {
                        textCheckCell.setTextAndCheck(LocaleController.getString("DrawerAlternativeOpeningAnimation", R.string.DrawerAlternativeOpeningAnimation), ExteraConfig.alternativeOpenAnimation, false);
                    }
                    break;
                case 2:
                    TextCell textCell = (TextCell) holder.itemView;
                    textCell.setEnabled(true);
                    int[] icons = ExteraUtils.getDrawerIconPack();
                    if (position == statusRow) {
                        textCell.setTextAndCheckAndIcon(LocaleController.getString("ChangeEmojiStatus", R.string.ChangeEmojiStatus), ExteraConfig.changeStatus, R.drawable.msg_smile_status, true);
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
                        textSettingsCell.setTextAndValue(LocaleController.getString("DrawerIconSet", R.string.DrawerIconSet), events[ExteraConfig.eventType], payload, true);
                    } else if (position == actionBarTitleRow) {
                        textSettingsCell.setTextAndValue(LocaleController.getString("ActionBarTitle", R.string.ActionBarTitle), titles[ExteraConfig.actionBarTitle], payload, false);
                    } else if (position == tabTitleRow) {
                        textSettingsCell.setTextAndValue(LocaleController.getString("TabTitleStyle", R.string.TabTitleStyle), tabIcons[ExteraConfig.tabIcons], payload, true);
                    } else if (position == tabStyleRow) {
                        textSettingsCell.setTextAndValue(LocaleController.getString("TabStyle", R.string.TabStyle), styles[ExteraConfig.tabStyle], payload, true);
                    }
                    break;
                case 8:
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == appearanceDividerRow) {
                        cell.setText(LocaleController.getString("AlternativeNavigationInfo", R.string.AlternativeNavigationInfo));
                    } else if (position == solarIconsInfoRow) {
                        cell.setText(ExteraUtils.formatWithUsernames(LocaleController.getString("SolarIconsInfo", R.string.SolarIconsInfo), AppearancePreferencesActivity.this));
                    } else if (position == mainScreenInfoRow) {
                        cell.setText(LocaleController.getString("MainScreenSetupInfo", R.string.MainScreenSetupInfo));
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == drawerDividerRow || position == drawerOptionsDividerRow) {
                return 1;
            } else if (position == statusRow || position == newGroupRow || position == newSecretChatRow || position == newChannelRow ||
                    position == contactsRow || position == callsRow || position == peopleNearbyRow || position == archivedChatsRow ||
                    position == savedMessagesRow || position == scanQrRow || position == inviteFriendsRow || position == telegramFeaturesRow) {
                return 2;
            } else if (position == appearanceHeaderRow || position == drawerHeaderRow || position == drawerOptionsHeaderRow || position == solarIconsHeaderRow) {
                return 3;
            } else if (position == eventChooserRow || position == actionBarTitleRow || position == tabStyleRow || position == tabTitleRow) {
                return 7;
            } else if (position == appearanceDividerRow || position == solarIconsInfoRow || position == mainScreenInfoRow) {
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