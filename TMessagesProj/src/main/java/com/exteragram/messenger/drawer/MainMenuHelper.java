/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.drawer;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.components.QRCodeSheet;
import com.exteragram.messenger.feed.ui.FeedActivity;
import com.exteragram.messenger.plugins.PluginsConstants;
import com.exteragram.messenger.plugins.PluginsController;
import com.exteragram.messenger.plugins.hooks.MenuItemRecord;
import com.exteragram.messenger.plugins.ui.PluginsActivity;
import com.exteragram.messenger.plugins.utils.MenuContextBuilder;
import com.exteragram.messenger.utils.chats.ChatUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionIntroActivity;
import org.telegram.ui.CallLogActivity;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.CameraScanActivity;
import org.telegram.ui.ChannelCreateActivity;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ContactsActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.GroupCreateActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.SettingsActivity;
import org.telegram.ui.WebAppDisclaimerAlert;
import org.telegram.ui.Wallet.WalletActivity;
import org.telegram.ui.bots.BotWebViewSheet;
import org.telegram.ui.web.SearchEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

import static org.telegram.messenger.LocaleController.getString;

public class MainMenuHelper {
    private static final long ACTION_DELAY_MS = 150L;
    private static final long QR_RESULT_DELAY_MS = 600L;
    private static final long WALLET_BOT_ID = 1985737506L;

    private MainMenuHelper() {
    }

    public record MenuItemInfo(int iconRes, CharSequence text, Runnable onClick, Runnable onLongClick) {
    }

    public record MenuContext(int currentAccount, BaseFragment fragment, Runnable archiveClick, Map<String, Object> pluginContextData) {
    }

    public record AttachMenuBotInfo(int iconRes, CharSequence text, TLRPC.TL_attachMenuBot bot, Runnable onClick, Runnable onLongClick) {
    }

    public static MenuContext createMenuContext(int currentAccount, BaseFragment fragment) {
        return new MenuContext(currentAccount, fragment, null, null);
    }

    public static MenuContext createMenuContext(int currentAccount, BaseFragment fragment, Runnable archiveClick, Map<String, Object> pluginContextData) {
        return new MenuContext(currentAccount, fragment, archiveClick, pluginContextData);
    }

    public static Map<String, Object> createPluginContextData(int currentAccount, BaseFragment fragment) {
        var builder = MenuContextBuilder.create().withAccount(currentAccount);
        if (fragment != null) {
            var context = fragment.getContext() != null ? fragment.getContext() : fragment.getParentActivity();
            builder.withContext(context);
        }
        var user = UserConfig.getInstance(currentAccount).getCurrentUser();
        if (user != null) {
            builder.withUser(user);
        }
        return builder.build();
    }

    public static List<MenuItemInfo> resolveDrawerMenuItems(int itemId, MenuContext menuContext) {
        var item = MainMenuItem.getById(itemId);
        if (item == null) {
            return Collections.emptyList();
        }

        return switch (item) {
            case BOTS -> resolveDrawerBotMenuItems(menuContext);
            case PLUGINS -> resolveDrawerPluginMenuItems(menuContext);
            default -> {
                var info = resolveMenuItem(itemId, menuContext);
                yield info == null ? Collections.emptyList() : Collections.singletonList(info);
            }
        };
    }

    public static MenuItemInfo resolveMenuItem(int itemId, int currentAccount, BaseFragment fragment) {
        return resolveMenuItem(itemId, createMenuContext(currentAccount, fragment));
    }

    public static MenuItemInfo resolveMenuItem(int itemId, int currentAccount, BaseFragment fragment, Runnable archiveClickOverride) {
        return resolveMenuItem(itemId, createMenuContext(currentAccount, fragment, archiveClickOverride, null));
    }

    public static MenuItemInfo resolveMenuItem(int itemId, MenuContext menuContext) {
        var item = MainMenuItem.getById(itemId);
        if (item == null || menuContext.fragment() == null) {
            return null;
        }

        var currentAccount = menuContext.currentAccount();
        var fragment = menuContext.fragment();

        return switch (item) {
            case WALLET -> MessagesController.getInstance(currentAccount).config.walletAvailable.get()
                    ? new MenuItemInfo(R.drawable.ic_gram, getString(R.string.WalletTitle), () -> {
                        var walletActivity = new WalletActivity();
                        walletActivity.setCurrentAccount(currentAccount);
                        fragment.presentFragment(walletActivity);
                    }, null)
                    : null;
            case PROFILE -> new MenuItemInfo(R.drawable.left_status_profile, getString(R.string.MyProfile), () -> {
                var args = new Bundle();
                args.putLong("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
                args.putBoolean("my_profile", true);
                fragment.presentFragment(new ProfileActivity(args));
            }, null);
            case ARCHIVE -> new MenuItemInfo(
                    R.drawable.msg_archive,
                    getString(R.string.ArchivedChats),
                    menuContext.archiveClick() != null ? menuContext.archiveClick() : () -> {
                        var args = new Bundle();
                        args.putInt("folderId", 1);
                        fragment.presentFragment(new DialogsActivity(args));
                    },
                    null
            );
            case NEW_GROUP -> new MenuItemInfo(R.drawable.msg_groups, getString(R.string.NewGroup), () -> {
                var args = new Bundle();
                fragment.presentFragment(new GroupCreateActivity(args));
            }, null);
            case CONTACTS -> new MenuItemInfo(R.drawable.msg_contacts, getString(R.string.Contacts), () -> {
                var args = new Bundle();
                args.putBoolean("needPhonebook", true);
                args.putBoolean("needFinishFragment", false);
                fragment.presentFragment(new ContactsActivity(args));
            }, null);
            case CALLS -> new MenuItemInfo(
                    R.drawable.msg_calls,
                    getString(R.string.Calls),
                    () -> fragment.presentFragment(new CallLogActivity()),
                    null
            );
            case NEW_CHANNEL -> new MenuItemInfo(R.drawable.msg_channel, getString(R.string.NewChannel), () -> {
                SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                if (!BuildVars.DEBUG_VERSION && preferences.getBoolean("channel_intro", false)) {
                    Bundle args = new Bundle();
                    args.putInt("step", 0);
                    fragment.presentFragment(new ChannelCreateActivity(args));
                } else {
                    fragment.presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANNEL_CREATE));
                    preferences.edit().putBoolean("channel_intro", true).apply();
                }
            }, null);
            case SAVED -> new MenuItemInfo(R.drawable.msg_saved, getString(R.string.SavedMessages), () -> {
                var args = new Bundle();
                args.putLong("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
                fragment.presentFragment(new ChatActivity(args));
            }, null);
            case FEED -> new MenuItemInfo(
                            R.drawable.ic_feed,
                            getString(R.string.Feed),
                            () -> FeedActivity.presentFeed(fragment),
                            null
                    );
            case SETTINGS -> new MenuItemInfo(
                    R.drawable.msg_settings,
                    getString(R.string.Settings),
                    () -> fragment.presentFragment(new SettingsActivity()),
                    null
            );
            case PLUGINS -> PluginsController.isPluginEngineSupported()
                    ? new MenuItemInfo(
                            R.drawable.msg_plugins,
                            getString(R.string.Plugins),
                            () -> fragment.presentFragment(new PluginsActivity()),
                            null
                    )
                    : null;
            case BROWSER -> new MenuItemInfo(R.drawable.msg2_language, getString(R.string.BrowserSettingsTitle), () -> {
                var currentEngine = SearchEngine.getCurrent();
                var homepage = currentEngine.getHomepage();
                Browser.openInTelegramBrowser(
                        fragment.getParentActivity(),
                        !TextUtils.isEmpty(homepage) ? homepage : currentEngine.search_url,
                        null
                );
            }, null);
            case QR -> new MenuItemInfo(R.drawable.msg_qrcode, getString(R.string.AuthAnotherClient), () -> {
                if (LaunchActivity.instance != null && LaunchActivity.instance.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    LaunchActivity.instance.requestPermissions(new String[]{Manifest.permission.CAMERA}, ActionIntroActivity.CAMERA_PERMISSION_REQUEST_CODE);
                    return;
                }
                CameraScanActivity.showAsSheet(fragment, true, CameraScanActivity.TYPE_QR, new CameraScanActivity.CameraScanActivityDelegate() {
                    @Override
                    public boolean processQr(String link, Runnable onLoadEnd) {
                        AndroidUtilities.runOnUIThread(() -> {
                            AndroidUtilities.runOnUIThread(() -> {
                                var safeFragment = LaunchActivity.getSafeLastFragment();
                                if (safeFragment != null) {
                                    new QRCodeSheet(safeFragment, link).show();
                                }
                            }, ACTION_DELAY_MS);
                            onLoadEnd.run();
                        }, QR_RESULT_DELAY_MS);
                        return true;
                    }
                });
            }, null);
            default -> null;
        };
    }

    public static void addConfiguredItemOptions(ItemOptions options, MenuContext menuContext) {
        addConfiguredItemOptions(options, menuContext, id -> false);
    }

    public static void addConfiguredItemOptions(ItemOptions options, MenuContext menuContext, IntPredicate skipItemPredicate) {
        boolean hasVisibleItems = false;
        boolean pendingGap = false;

        for (int i = 0; i < MainMenuLayout.getMainMenuLayout().size(); i++) {
            Integer id = MainMenuLayout.getMainMenuLayout().get(i);
            if (id == null || MainMenuLayout.getMainMenuHiddenItems().contains(id)) {
                continue;
            }

            if (id == MainMenuItem.DIVIDER.getId()) {
                if (hasVisibleItems) {
                    pendingGap = true;
                }
                continue;
            }

            if (skipItemPredicate.test(id)) {
                continue;
            }

            if (pendingGap) {
                options.addGap();
                pendingGap = false;
            }

            if (addConfiguredItemOption(options, menuContext, id)) {
                hasVisibleItems = true;
            }
        }
    }

    public static List<AttachMenuBotInfo> getAttachMenuBotItems(MenuContext menuContext) {
        var fragment = menuContext.fragment();
        var launchActivity = findLaunchActivity(fragment);
        var menuBots = MediaDataController.getInstance(menuContext.currentAccount()).getAttachMenuBots();

        if (fragment == null || launchActivity == null || menuBots == null || menuBots.bots == null || menuBots.bots.isEmpty()) {
            return Collections.emptyList();
        }

        List<AttachMenuBotInfo> items = new ArrayList<>();
        for (TLRPC.TL_attachMenuBot attachMenuBot : menuBots.bots) {
            if (!attachMenuBot.show_in_side_menu) {
                continue;
            }

            items.add(new AttachMenuBotInfo(
                    getAttachMenuBotIconRes(attachMenuBot),
                    attachMenuBot.short_name,
                    attachMenuBot,
                    createAttachMenuBotClickAction(menuContext, attachMenuBot, launchActivity),
                    () -> BotWebViewSheet.deleteBot(menuContext.currentAccount(), attachMenuBot.bot_id, null)
            ));
        }
        return items;
    }

    private static boolean addConfiguredItemOption(ItemOptions options, MenuContext menuContext, int id) {
        var item = MainMenuItem.getById(id);
        if (item == null) {
            return false;
        }

        if (item == MainMenuItem.ARCHIVE && !ChatUtils.getInstance(menuContext.currentAccount()).hasArchivedChats()) {
            return false;
        }

        if (item == MainMenuItem.BOTS) {
            return addAttachMenuBotMenuItems(options, menuContext);
        }

        if (item == MainMenuItem.PLUGINS) {
            return addPluginConfiguredItem(options, menuContext, item);
        }

        var info = resolveMenuItem(id, menuContext);
        if (info == null || info.onClick() == null) {
            return false;
        }

        options.add(info.iconRes(), info.text(), info.onClick());
        bindLongClick(options, info.onLongClick());
        return true;
    }

    public static List<MenuItemRecord> getPluginMenuItems(MenuContext menuContext) {
        return PluginsController.getInstance().getMenuItemsForLocation(
                PluginsConstants.MenuItemTypes.MAIN_MENU,
                getPluginContextData(menuContext)
        );
    }

    public static Runnable createPluginClickAction(MenuItemRecord item, MenuContext menuContext) {
        return () -> item.executeClick(getPluginContextData(menuContext));
    }

    public static Runnable createPluginLongClickAction(MenuItemRecord item, MenuContext menuContext) {
        if (!item.hasLongClick()) {
            return null;
        }
        return () -> item.executeLongClick(getPluginContextData(menuContext));
    }

    private static List<MenuItemInfo> resolveDrawerBotMenuItems(MenuContext menuContext) {
        var botItems = getAttachMenuBotItems(menuContext);
        if (botItems.isEmpty()) {
            return Collections.emptyList();
        }

        List<MenuItemInfo> items = new ArrayList<>(botItems.size());
        for (AttachMenuBotInfo botItem : botItems) {
            items.add(new MenuItemInfo(botItem.iconRes(), botItem.text(), botItem.onClick(), botItem.onLongClick()));
        }
        return items;
    }

    private static List<MenuItemInfo> resolveDrawerPluginMenuItems(MenuContext menuContext) {
        if (menuContext.fragment() == null) {
            return Collections.emptyList();
        }
        var fallback = resolveMenuItem(MainMenuItem.PLUGINS.getId(), menuContext);
        var pluginItems = getPluginMenuItems(menuContext);
        if (pluginItems.isEmpty()) {
            return fallback == null ? Collections.emptyList() : Collections.singletonList(fallback);
        }

        List<MenuItemInfo> items = new ArrayList<>(pluginItems.size() + (fallback != null ? 1 : 0));
        if (fallback != null) {
            items.add(fallback);
        }
        for (MenuItemRecord item : pluginItems) {
            if (item == null || TextUtils.isEmpty(item.getText())) {
                continue;
            }
            var longClick = createPluginLongClickAction(item, menuContext);
            items.add(new MenuItemInfo(
                    item.getIconResId() != 0 ? item.getIconResId() : R.drawable.msg_plugins,
                    item.getText(),
                    createPluginClickAction(item, menuContext),
                    longClick != null ? longClick : () -> menuContext.fragment().presentFragment(new PluginsActivity())
            ));
        }
        if (items.isEmpty()) {
            return fallback == null ? Collections.emptyList() : Collections.singletonList(fallback);
        }
        return items;
    }

    private static Runnable createAttachMenuBotClickAction(MenuContext menuContext, TLRPC.TL_attachMenuBot attachMenuBot, LaunchActivity launchActivity) {
        return () -> {
            if (attachMenuBot.inactive || attachMenuBot.side_menu_disclaimer_needed) {
                var context = menuContext.fragment().getContext() != null ? menuContext.fragment().getContext() : launchActivity;
                WebAppDisclaimerAlert.show(context, allowSendMessage -> {
                    TLRPC.TL_messages_toggleBotInAttachMenu botRequest = new TLRPC.TL_messages_toggleBotInAttachMenu();
                    botRequest.bot = MessagesController.getInstance(menuContext.currentAccount()).getInputUser(attachMenuBot.bot_id);
                    botRequest.enabled = true;
                    botRequest.write_allowed = true;
                    ConnectionsManager.getInstance(menuContext.currentAccount()).sendRequest(
                            botRequest,
                            (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                                attachMenuBot.inactive = attachMenuBot.side_menu_disclaimer_needed = false;
                                LaunchActivity.showAttachMenuBot(launchActivity, menuContext.currentAccount(), attachMenuBot, null, true);
                                MediaDataController.getInstance(menuContext.currentAccount()).updateAttachMenuBotsInCache();
                            }),
                            ConnectionsManager.RequestFlagInvokeAfter | ConnectionsManager.RequestFlagFailOnServerErrors
                    );
                }, null, null);
            } else {
                LaunchActivity.showAttachMenuBot(launchActivity, menuContext.currentAccount(), attachMenuBot, null, true);
            }
        };
    }

    private static boolean addPluginConfiguredItem(ItemOptions options, MenuContext menuContext, MainMenuItem item) {
        var info = resolveMenuItem(item.getId(), menuContext);
        if (info == null || info.onClick() == null) {
            return false;
        }
        addPluginsItem(options, info.iconRes(), info.text(), getPluginMenuItems(menuContext), getPluginContextData(menuContext), null, info.onClick(), info.onClick());
        return true;
    }

    /**
     * The plugins item of a menu: a page with the plugins' items, or {@code onClickWithoutItems} when there are none.
     * {@code beforeAction} runs after the menu closes, before an item's action.
     */
    public static void addPluginsItem(ItemOptions options, int iconRes, CharSequence text, List<MenuItemRecord> records, Map<String, Object> contextData,
                                      Runnable beforeAction, Runnable onClickWithoutItems, Runnable onLongClick) {
        ItemOptions swipeback = null;
        for (MenuItemRecord record : records) {
            if (TextUtils.isEmpty(record.getText())) {
                continue;
            }
            if (swipeback == null) {
                swipeback = options.makeSwipeback();
                swipeback.add(R.drawable.ic_ab_back, getString(R.string.Back), options::closeSwipeback);
                swipeback.addGap();
            }
            swipeback.add(record.getIconResId(), record.getText(), () -> {
                options.dismiss();
                if (beforeAction != null) {
                    beforeAction.run();
                }
                record.executeClick(contextData);
            });
            var added = swipeback.getLast();
            if (record.hasLongClick() && added != null) {
                added.setOnLongClickListener(v -> {
                    options.dismiss();
                    if (beforeAction != null) {
                        beforeAction.run();
                    }
                    record.executeLongClick(contextData);
                    return true;
                });
            }
        }
        if (swipeback == null && onClickWithoutItems == null) {
            return;
        }
        final ItemOptions pluginsSwipeback = swipeback;
        options.add(iconRes, text, () -> {
            if (pluginsSwipeback != null) {
                options.openSwipeback(pluginsSwipeback);
            } else {
                onClickWithoutItems.run();
            }
        });
        var last = options.getLast();
        if (pluginsSwipeback != null && last != null) {
            last.setRightIcon(R.drawable.msg_arrowright);
            if (onLongClick != null) {
                last.setOnLongClickListener(v -> {
                    options.dismiss();
                    onLongClick.run();
                    return true;
                });
            }
        }
    }

    private static boolean addAttachMenuBotMenuItems(ItemOptions options, MenuContext menuContext) {
        var botItems = getAttachMenuBotItems(menuContext);
        if (botItems.isEmpty()) {
            return false;
        }

        for (var botItem : botItems) {
            options.addBot(botItem.bot(), botItem.onClick(), botItem.onLongClick());
        }
        return true;
    }

    private static void bindLongClick(ItemOptions options, Runnable onLongClick) {
        if (onLongClick == null) {
            return;
        }

        var last = options.getLast();
        if (last == null) {
            return;
        }

        last.setOnLongClickListener(v -> {
            options.dismiss();
            onLongClick.run();
            return true;
        });
    }

    private static Map<String, Object> getPluginContextData(MenuContext menuContext) {
        return menuContext.pluginContextData() != null
                ? menuContext.pluginContextData()
                : createPluginContextData(menuContext.currentAccount(), menuContext.fragment());
    }

    private static int getAttachMenuBotIconRes(TLRPC.TL_attachMenuBot bot) {
        return bot.bot_id == WALLET_BOT_ID ? R.drawable.menu_wallet : R.drawable.msg_bot;
    }

    private static LaunchActivity findLaunchActivity(BaseFragment fragment) {
        if (fragment == null) {
            return LaunchActivity.instance;
        }
        var context = fragment.getContext() != null ? fragment.getContext() : fragment.getParentActivity();
        var activity = AndroidUtilities.findActivity(context);
        return activity instanceof LaunchActivity launchActivity ? launchActivity : LaunchActivity.instance;
    }
}
