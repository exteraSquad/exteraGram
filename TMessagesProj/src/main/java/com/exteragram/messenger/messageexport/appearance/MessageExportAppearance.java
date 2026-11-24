/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.messageexport.appearance;

import android.graphics.drawable.Drawable;
import android.util.SparseIntArray;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaDataController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.EmojiThemes;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ChatThemeBottomSheet;
import org.telegram.ui.Components.MotionBackgroundDrawable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class MessageExportAppearance {

    private static final String HOME_EMOTICON = "\uD83C\uDFE0";
    private static final int[] DEFAULT_WALLPAPER_COLORS = {0xffdbddbb, 0xff6ba587, 0xffd5d88d, 0xff88b884};

    private final int account;
    private final Theme.ResourcesProvider chatProvider;
    private final Drawable chatWallpaper;
    private final boolean chatDark;
    private final EmojiThemes chatPreset;
    private final boolean chatMatchesApp;
    private final String appEmoticon;

    private EmojiThemes appPreset;
    private EmojiThemes homePreset;
    private EmojiThemes loadedAppAlias;
    private final SparseIntArray[] standardColors = new SparseIntArray[2];
    private EmojiThemes preset;
    private boolean dark;

    private Theme.ResourcesProvider provider;
    private int servicePeriod;
    private MessageExportBackground background;

    public MessageExportAppearance(int account, Theme.ResourcesProvider chatProvider, Drawable chatWallpaper, EmojiThemes chatPreset, boolean chatMatchesApp) {
        this.account = account;
        this.chatProvider = chatProvider;
        this.chatWallpaper = chatWallpaper;
        this.chatPreset = chatPreset != null && !chatPreset.isAnyStub() ? chatPreset : null;
        this.chatMatchesApp = this.chatPreset == null && chatMatchesApp;
        this.appEmoticon = findAppEmoticon(account);
        this.chatDark = chatProvider != null ? chatProvider.isDark() : Theme.isCurrentThemeDark();
        this.dark = chatDark;
    }

    public EmojiThemes getPreset() {
        return preset;
    }

    public boolean isDark() {
        return dark;
    }

    public boolean isChatDark() {
        return chatDark;
    }

    public EmojiThemes getHomePreset() {
        if (homePreset == null) {
            homePreset = EmojiThemes.createHomeQrTheme(account);
        }
        return homePreset;
    }

    private EmojiThemes getAppPreset() {
        if (appPreset == null) {
            appPreset = EmojiThemes.createChatThemesDefault(account);
        }
        return appPreset;
    }

    public EmojiThemes getChatAlias() {
        if (chatPreset != null) {
            return chatPreset;
        }
        if (!chatMatchesApp) {
            return null;
        }
        if (HOME_EMOTICON.equals(appEmoticon)) {
            return getHomePreset();
        }
        return loadedAppAlias != null ? loadedAppAlias : getAppPreset();
    }

    public boolean isChatSelected() {
        return preset == null || preset == getChatAlias() || chatMatchesApp && preset == appPreset;
    }

    private String getChatEmoticon() {
        if (chatPreset != null) {
            return chatPreset.getEmoticonOrSlug();
        }
        return chatMatchesApp ? appEmoticon : null;
    }

    public boolean showsHomePreset() {
        return !HOME_EMOTICON.equals(getChatEmoticon());
    }

    public boolean isSameAsChat(EmojiThemes theme) {
        String emoticon = getChatEmoticon();
        return theme != null && emoticon != null && emoticon.equals(theme.getEmoticonOrSlug());
    }

    public boolean canRender(EmojiThemes theme, boolean dark) {
        if (theme == null) {
            return true;
        }
        if (theme.isAnyStub() || theme.isGiftTheme() || theme.getThemeGift() != null) {
            return false;
        }
        int index = dark ? 1 : 0;
        if (index >= theme.items.size()) {
            return false;
        }
        TLRPC.WallPaper wallpaper = theme.getWallpaper(index);
        return wallpaper == null || wallpaper.pattern || wallpaper.document == null;
    }

    public void onPresetsLoaded(List<EmojiThemes> themes) {
        if (!chatMatchesApp || appEmoticon == null || HOME_EMOTICON.equals(appEmoticon)) {
            return;
        }
        for (EmojiThemes theme : themes) {
            if (theme != null && !theme.isAnyStub() && appEmoticon.equals(theme.getEmoticonOrSlug())) {
                loadedAppAlias = theme;
                return;
            }
        }
    }

    public void set(EmojiThemes preset, boolean dark) {
        if (this.preset != preset || this.dark != dark) {
            this.preset = preset;
            this.dark = dark;
            provider = null;
            background = null;
        }
    }

    public void setServicePeriod(int period) {
        servicePeriod = period;
        if (provider instanceof MessageExportThemeProvider themeProvider) {
            themeProvider.setServicePeriod(period);
        }
    }

    public Theme.ResourcesProvider getProvider() {
        resolve();
        return provider;
    }

    public MessageExportBackground getBackground() {
        resolve();
        return background;
    }

    public Drawable createChatPreviewDrawable() {
        if (isChatSelected()) {
            return getBackground().createPreviewDrawable();
        }
        EmojiThemes alias = getChatAlias();
        if (dark != chatDark && alias != null && canRender(alias, dark)) {
            return createPresetPreviewDrawable(alias);
        }
        return MessageExportBackground.fromChat(chatWallpaper, chatProvider).createPreviewDrawable();
    }

    public Drawable createPresetPreviewDrawable(EmojiThemes theme) {
        if (theme == homePreset && !dark) {
            int[] colors = DEFAULT_WALLPAPER_COLORS;
            return new MotionBackgroundDrawable(colors[0], colors[1], colors[2], colors[3], true);
        }
        SparseIntArray colors = theme.getPreviewColors(account, dark ? 1 : 0);
        int wallpaperColor = colors.get(Theme.key_chat_wallpaper, 0);
        if (wallpaperColor == 0) {
            wallpaperColor = theme.createColors(account, dark ? 1 : 0).get(Theme.key_chat_wallpaper, 0xff888888);
        }
        return new MotionBackgroundDrawable(
            wallpaperColor,
            colors.get(Theme.key_chat_wallpaper_gradient_to1, 0),
            colors.get(Theme.key_chat_wallpaper_gradient_to2, 0),
            colors.get(Theme.key_chat_wallpaper_gradient_to3, 0),
            true
        );
    }

    private void resolve() {
        if (provider != null) {
            return;
        }
        SparseIntArray colors;
        boolean appWallpaper;
        if (preset == null && dark == chatDark) {
            colors = new SparseIntArray(Theme.colorsCount);
            for (int key = 0; key < Theme.colorsCount; key++) {
                colors.put(key, chatProvider != null ? chatProvider.getColor(key) : Theme.getColor(key));
            }
            background = MessageExportBackground.fromChat(chatWallpaper, chatProvider);
            appWallpaper = false;
        } else if (preset != null && !preset.showAsDefaultStub) {
            colors = preset.createColors(account, dark ? 1 : 0);
            if (preset == homePreset) {
                applyDefaultAccentColors(colors, dark);
            }
            appWallpaper = appEmoticon != null && appEmoticon.equals(preset.getEmoticonOrSlug());
        } else {
            colors = getStandardColors(dark).clone();
            appWallpaper = true;
        }
        MessageExportThemeProvider themeProvider = new MessageExportThemeProvider(colors, dark);
        if (background == null && appWallpaper) {
            background = appBackground(dark, themeProvider);
        }
        if (background == null && preset == homePreset) {
            background = homeBackground(dark, colors, themeProvider);
        }
        if (background == null) {
            background = MessageExportBackground.fromTheme(preset != null && !preset.showAsDefaultStub ? preset : null, dark, themeProvider);
        }
        themeProvider.init(background);
        themeProvider.setServicePeriod(servicePeriod);
        provider = themeProvider;
    }

    private static String findAppEmoticon(int account) {
        Theme.ThemeInfo active = Theme.getActiveTheme();
        if (active == null) {
            return null;
        }
        ArrayList<ChatThemeBottomSheet.ChatThemeItem> items = MediaDataController.getInstance(account).defaultEmojiThemes;
        for (int i = 0; i < items.size(); i++) {
            EmojiThemes theme = items.get(i) != null ? items.get(i).chatTheme : null;
            if (theme == null) {
                continue;
            }
            for (int index = 0; index < theme.items.size(); index++) {
                if (theme.items.get(index) != null && isActive(theme, index, active)) {
                    return theme.getEmoticonOrSlug();
                }
            }
        }
        return null;
    }

    private static boolean isActive(EmojiThemes theme, int index, Theme.ThemeInfo active) {
        TLRPC.TL_theme tlTheme = theme.getTlTheme(index);
        if (tlTheme != null) {
            int settingsIndex = theme.getSettingsIndex(index);
            if (tlTheme.settings == null || settingsIndex < 0 || settingsIndex >= tlTheme.settings.size() || !active.name.equals(Theme.getBaseThemeKey(tlTheme.settings.get(settingsIndex)))) {
                return false;
            }
            if (active.accentsByThemeId == null) {
                return true;
            }
            Theme.ThemeAccent accent = active.accentsByThemeId.get(tlTheme.id);
            return accent != null && accent.id == active.currentAccentId;
        }
        Theme.ThemeInfo info = theme.getThemeInfo(index);
        return info != null && active.name.equals(info.getKey()) && theme.getAccentId(index) == active.currentAccentId;
    }

    private SparseIntArray getStandardColors(boolean dark) {
        int index = dark ? 1 : 0;
        if (standardColors[index] == null) {
            standardColors[index] = appThemeColors(EmojiThemes.getDefaultThemeInfo(dark));
        }
        return standardColors[index];
    }

    private Theme.ThemeInfo homeThemeInfo(boolean dark) {
        EmojiThemes home = getHomePreset();
        int index = dark ? 1 : 0;
        Theme.ThemeInfo base = home.getThemeInfo(index);
        if (base == null) {
            return null;
        }
        Theme.ThemeInfo info = new Theme.ThemeInfo(base);
        info.setCurrentAccentId(home.getAccentId(index));
        return info;
    }

    private void applyDefaultAccentColors(SparseIntArray colors, boolean dark) {
        Theme.ThemeInfo info = homeThemeInfo(dark);
        if (info == null || !info.firstAccentIsDefault || info.currentAccentId != Theme.DEFALT_THEME_ACCENT_ID) {
            return;
        }
        int[] defaults = Theme.getDefaultColors();
        for (int key = 0; key < defaults.length; key++) {
            if (key == Theme.key_chat_wallpaper || key == Theme.key_chat_wallpaper_gradient_to1 || key == Theme.key_chat_wallpaper_gradient_to2 || key == Theme.key_chat_wallpaper_gradient_to3) {
                continue;
            }
            colors.put(key, defaults[key]);
        }
        colors.delete(Theme.key_chat_serviceBackground);
        colors.delete(Theme.key_chat_serviceBackgroundSelected);
    }

    private MessageExportBackground homeBackground(boolean dark, SparseIntArray colors, Theme.ResourcesProvider provider) {
        Theme.ThemeInfo info = homeThemeInfo(dark);
        if (info == null) {
            return null;
        }
        Theme.ThemeAccent accent = info.getAccent(false);
        File file = accent != null ? accent.getPathToWallpaper() : null;
        int intensity = accent != null ? (int) (accent.patternIntensity * 100) : info.patternIntensity;
        if (info.overrideWallpaper != null) {
            intensity = (int) (info.overrideWallpaper.intensity * 100);
        }
        boolean defaultTheme = info.firstAccentIsDefault && info.currentAccentId == Theme.DEFALT_THEME_ACCENT_ID;
        try {
            Theme.BackgroundDrawableSettings settings = Theme.createBackgroundDrawable(info, info.overrideWallpaper, colors, file, null, -1, intensity, 0, defaultTheme, false, false, accent != null && accent.patternMotion, null, true);
            if (settings.wallpaper != null) {
                return MessageExportBackground.fromChat(settings.wallpaper, provider);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return null;
    }

    private static MessageExportBackground appBackground(boolean dark, Theme.ResourcesProvider provider) {
        Theme.ThemeInfo active = Theme.getActiveTheme();
        Drawable wallpaper = active != null && active.isDark() == dark ? Theme.getCachedWallpaperNonBlocking() : null;
        return wallpaper != null ? MessageExportBackground.fromChat(wallpaper, provider) : null;
    }

    private static SparseIntArray appThemeColors(Theme.ThemeInfo themeInfo) {
        SparseIntArray noAccent = null;
        if (themeInfo != null && themeInfo.assetName != null) {
            noAccent = Theme.getThemeFileValues(null, themeInfo.assetName, null);
        } else if (themeInfo != null && themeInfo.pathToFile != null) {
            noAccent = Theme.getThemeFileValues(new File(themeInfo.pathToFile), null, null);
        }
        if (noAccent == null) {
            noAccent = new SparseIntArray();
        }
        SparseIntArray colors = noAccent.clone();
        Theme.ThemeAccent accent = themeInfo != null ? themeInfo.getAccent(false) : null;
        if (accent != null) {
            accent.fillAccentColors(noAccent, colors);
        }
        SparseIntArray fallbackKeys = Theme.getFallbackKeys();
        for (int i = 0; i < fallbackKeys.size(); i++) {
            int key = fallbackKeys.keyAt(i);
            if (colors.indexOfKey(key) < 0) {
                int index = colors.indexOfKey(fallbackKeys.valueAt(i));
                if (index >= 0) {
                    colors.put(key, colors.valueAt(index));
                }
            }
        }
        int[] defaults = Theme.getDefaultColors();
        if (defaults != null) {
            for (int i = 0; i < defaults.length; i++) {
                if (colors.indexOfKey(i) < 0) {
                    colors.put(i, defaults[i]);
                }
            }
        }
        return colors;
    }
}
