/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.preferences.utils;

import android.text.TextUtils;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.ai.ui.activities.AiPreferencesActivity;
import com.exteragram.messenger.feed.ui.FeedChannelsActivity;
import com.exteragram.messenger.plugins.PluginsController;
import com.exteragram.messenger.plugins.ui.PluginsInfoActivity;
import com.exteragram.messenger.pillstack.ui.PillStackPreferencesActivity;
import com.exteragram.messenger.preferences.BasePreferencesActivity;
import com.exteragram.messenger.preferences.GeneralPreferencesActivity;
import com.exteragram.messenger.preferences.MainPreferencesActivity;
import com.exteragram.messenger.preferences.OtherPreferencesActivity;
import com.exteragram.messenger.preferences.appearance.AppearancePreferencesActivity;
import com.exteragram.messenger.preferences.appearance.AppNavigationPreferencesActivity;
import com.exteragram.messenger.preferences.chats.ChatsPreferencesActivity;
import com.exteragram.messenger.preferences.chats.InlineResultsPreferencesActivity;
import com.exteragram.messenger.preferences.chats.SwipeActionsPreferencesActivity;
import com.exteragram.messenger.utils.text.LocaleUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProfileActivity.SearchAdapter;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class SettingsRegistry {

    public static final String deeplinkPrefix = "exteraSettings";
    public static final String deeplinkPluginIdQueryKey = "p";
    public static final String deeplinkSettingQueryKey = "s";
    public static final String SHRUG_MAN = "🤷‍♂️";

    private final ConcurrentHashMap<Integer, Entry> preparedEntries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Entry> entriesStringAlias = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> primaryAliases = new ConcurrentHashMap<>();
    private static final Map<Class<? extends BaseFragment>, Integer> categoriesIcons = Map.ofEntries(
            Map.entry(MainPreferencesActivity.class, R.drawable.extera_outline),
            Map.entry(GeneralPreferencesActivity.class, R.drawable.msg_media),
            Map.entry(AppearancePreferencesActivity.class, R.drawable.msg_theme),
            Map.entry(ChatsPreferencesActivity.class, R.drawable.msg_discussion),
            Map.entry(PluginsInfoActivity.class, R.drawable.msg_plugins),
            Map.entry(OtherPreferencesActivity.class, R.drawable.msg_fave),
            Map.entry(AiPreferencesActivity.class, R.drawable.msg_bot),
            Map.entry(AppNavigationPreferencesActivity.class, R.drawable.msg_list),
            Map.entry(PillStackPreferencesActivity.class, R.drawable.outline_header_search),
            Map.entry(FeedChannelsActivity.class, R.drawable.ic_feed),
            Map.entry(SwipeActionsPreferencesActivity.class, R.drawable.menu_reply),
            Map.entry(InlineResultsPreferencesActivity.class, R.drawable.msg_calculator)
    );

    public static List<String> newFeatures = List.of(
            // General
            "customSavePath",
            "defaultProfileTab",
            "disableNotificationDelay",

            // Chats
            "Camera-ExtendedSettings-StartWithWideAngle",
            "zoomSlider",
            "widePosts",
            "swipeActions",
            "swipeActionsLoop",
            "swipeActionsReversed",
            "aiFeatures",
            "inlineMathResult",
            "inlineCurrencyRates",
            "inlineUnitConversion",
            "inlineTimeZones",
            "stickerTime",

            // Appearance
            "hideDialogsSearchBar",
            "Appearance-M3Styles-ChatHeader",
            "Appearance-M3Styles-NavigationBar",
            "hideNavigationBarLabels",
            "Appearance-M3Styles-FloatingButton",
            "Appearance-Sections",
            "glassOutlineStyle",
            "glassMessageMenu",
            "pillStackCustom",

            // Feed
            "feedBottomTab",

            // AI
            "aiTemperature",
            "AI-Service-Reasoning"
    );

    private boolean entriesFetched;
    private String entriesLangCode;

    public static SettingsRegistry getInstance() {
        return SingletonHolder.INSTANCE;
    }

    public static boolean isValidForSearch(UItem item) {
        if (item.id == 0 || TextUtils.isEmpty(item.text)) {
            FileLog.e(String.format(
                    "[Extera] UItems with ID 0 or empty text cannot be added as search result. (UItem ID: %s; View type: %s; View: %s; Text: %s; Subtext: %s)",
                    item.id, item.viewType, item.view == null ? null : item.view.getClass().getName(), item.text, TextUtils.concat(item.subtext, item.animatedText)
            ));
            return false;
        }
        return true;
    }

    public static boolean isValidForLinkAliases(UItem item) {
        if (item.id == 0) {
            FileLog.e(String.format(
                    "[Extera] Cannot set link aliases for UItems with ID 0. (UItem ID: %s; View type: %s; View: %s; Text: %s; Subtext: %s)",
                    item.id, item.viewType, item.view == null ? null : item.view.getClass().getName(), item.text, TextUtils.concat(item.subtext, item.animatedText)
            ));
            return false;
        }
        return true;
    }

    public void addSearchEntry(BaseFragment fragment, UItem item) {
        if (!isValidForSearch(item))
            return;

        var entry = Entry.fromUItem(fragment, item);
        var guid = generateGUIDForUItem(fragment.getClass(), item);
        if (!preparedEntries.containsKey(guid))
            FileLog.d("[Extera] Added an entry: " + entry);
        preparedEntries.putIfAbsent(entry.guid, entry);
    }

    public static boolean markAsNewFeature(String alias) {
        if (!newFeatures.contains(alias) || ExteraConfig.getDoNotMarkAsNew().contains(alias)) return false;

        Long timestamp = ExteraConfig.getNewFeaturesShowedAt().get(alias);
        if (timestamp == null || timestamp == 0L) {
            ExteraConfig.getNewFeaturesShowedAt().put(alias, System.currentTimeMillis());
            ExteraConfig.getEditor().putString("newFeaturesShowedAt", ExteraConfig.getGSON().toJson(ExteraConfig.getNewFeaturesShowedAt())).apply();
        } else if (Math.abs(System.currentTimeMillis() - timestamp) > TimeUnit.DAYS.toMillis(1)) {
            ExteraConfig.getNewFeaturesShowedAt().remove(alias);
            ExteraConfig.getEditor().putString("newFeaturesShowedAt", ExteraConfig.getGSON().toJson(ExteraConfig.getNewFeaturesShowedAt()));

            ExteraConfig.getDoNotMarkAsNew().add(alias);
            ExteraConfig.getEditor().putString("doNotMarkAsNew", ExteraConfig.getGSON().toJson(ExteraConfig.getDoNotMarkAsNew())).apply();
            return false;
        }
        return true;
    }

    public void addLinkAliasForOption(String alias, BaseFragment fragment, UItem item) {
        if (!isValidForLinkAliases(item))
            return;

        if (markAsNewFeature(alias) && item.text != null && item.text.length() > 0 && item.text.toString().charAt(item.text.toString().length() - 1) != 'd')
            item.text = LocaleUtils.applyNewSpan(item.text.toString());

        if (entriesStringAlias.containsKey(alias)) {
            FileLog.d("[Extera] Key '" + alias + "' already linked to an entry.");
            return;
        }

        var guid = generateGUIDForUItem(fragment.getClass(), item);
        var entry = preparedEntries.get(guid);
        if (entry == null)
            entry = Entry.fromUItem(fragment, item);

        FileLog.d(String.format("[Extera] Added link alias %s for an entry %s", alias, entry));
        entriesStringAlias.put(alias, entry);
        primaryAliases.putIfAbsent(entry.guid, alias);
    }

    public void handleLink(String alias, String pluginId) {
        FileLog.d("[Extera] Setting link handler called with alias " + alias);
        if (pluginId != null && !TextUtils.isEmpty(pluginId)) {
            PluginsController.openPluginSettings(pluginId, alias);
            return;
        }

        createEntriesIfNeeded();

        var entry = entriesStringAlias.get(alias);
        if (entry == null) {
            onSettingNotFound();
            return;
        }

        FileLog.d("[Extera] Found entry for alias: " + entry);
        FileLog.d("[Extera] Opening fragment...");
        openActivity(entry.fragmentClass, entry.itemId);
    }

    public void onSettingNotFound() {
        onSettingNotFound(LaunchActivity.getLastFragment());
    }

    public void onSettingNotFound(BaseFragment fragment) {
        BulletinFactory.of(fragment).createEmojiBulletin(SHRUG_MAN, LocaleController.getString(R.string.NoSuchSetting)).show();
    }

    public String getFirstSettingLink(Class<? extends BaseFragment> fragmentClass, UItem item) {
        var alias = primaryAliases.get(generateGUIDForUItem(fragmentClass, item));
        if (alias == null)
            return null;
        return "https://t.me/" + deeplinkPrefix + "?" + deeplinkSettingQueryKey + "=" + alias;
    }

    public SearchAdapter.SearchResult[] getSearchResults(SearchAdapter searchAdapter) {
        createEntriesIfNeeded();
        return preparedEntries.values().stream()
                .map(e -> e.toSearchResult(searchAdapter))
                .toArray(SearchAdapter.SearchResult[]::new);
    }

    private int getCategoryIcon(Class<? extends BaseFragment> activity) {
        return Objects.requireNonNullElse(categoriesIcons.get(activity), 0);
    }

    private BaseFragment initiateFragment(Class<? extends BaseFragment> fragmentClass) {
        try {
            var lastFragment = LaunchActivity.getLastFragment();
            if (lastFragment == null)
                return null;

            var fragment = fragmentClass.getDeclaredConstructor().newInstance();
            fragment.setParentFragment(lastFragment);
            fragment.createActionBar(lastFragment.getContext());
            return fragment;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private void openActivity(Class<? extends BaseFragment> fragmentClass, Integer itemId) {
        var lastFragment = LaunchActivity.getLastFragment();
        if (lastFragment == null)
            return;

        BaseFragment openFragment;
        try {
            openFragment = fragmentClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            FileLog.e(e);
            return;
        }

        AndroidUtilities.runOnUIThread(() -> lastFragment.presentFragment(openFragment));
        if (itemId != null && openFragment instanceof BasePreferencesActivity prefs)
            AndroidUtilities.runOnUIThread(() -> prefs.scrollToItem(itemId));
    }

    public boolean isSearchIndexReady() {
        return entriesFetched && TextUtils.equals(entriesLangCode, getCurrentLangCode());
    }

    private static String getCurrentLangCode() {
        var localeInfo = LocaleController.getInstance().getCurrentLocaleInfo();
        return localeInfo == null ? "" : localeInfo.getKey();
    }

    private void createEntriesIfNeeded() {
        var langCode = getCurrentLangCode();
        if (entriesFetched) {
            if (TextUtils.equals(entriesLangCode, langCode)) return;
            FileLog.d("[Extera] Language changed to " + langCode + ", rebuilding entries...");
            preparedEntries.clear();
        }
        FileLog.d("[Extera] Initialising activities...");
        categoriesIcons.keySet().forEach(this::initiateFragment);
        entriesFetched = true;
        entriesLangCode = langCode;
    }

    private static int generateGUIDForUItem(Class<?> fragmentClass, UItem item) {
        return Objects.hash(fragmentClass.getName(), item.id);
    }

    private record Entry(int guid, int itemId, String title, String subtext, int icon,
                         Class<? extends BaseFragment> fragmentClass) {

        public static Entry fromUItem(BaseFragment fragment, UItem item) {
            var fragmentClass = fragment.getClass();
            var text = item.text == null ? null : String.valueOf(item.text);
            var subtext = (fragment instanceof BasePreferencesActivity p) ? p.getTitle() : null;
            var icon = SettingsRegistry.getInstance().getCategoryIcon(fragmentClass);
            return new Entry(
                    generateGUIDForUItem(fragmentClass, item),
                    item.id, text, subtext, icon, fragmentClass
            );
        }

        public SearchAdapter.SearchResult toSearchResult(SearchAdapter adapter) {
            return adapter.new SearchResult(
                    guid, title, String.valueOf(itemId), subtext, icon,
                    () -> SettingsRegistry.getInstance().openActivity(fragmentClass, itemId)
            );
        }
    }

    private static class SingletonHolder {
        private static final SettingsRegistry INSTANCE = new SettingsRegistry();
    }
}