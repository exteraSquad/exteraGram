/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player;

import static com.exteragram.messenger.utils.ui.material.ColorTones.withTone;

import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.util.SparseIntArray;

import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.utils.ui.material.ColorTones;
import com.exteragram.messenger.utils.ui.MonetUtils;
import com.google.android.material.color.ColorRoles;
import com.google.android.material.color.MaterialColors;

import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.WrappedResourceProvider;

import java.util.Arrays;

public final class PlayerColors {

    private static final double[] GLOW_HUE_SHIFT = {0, 50, -35, 0};
    private static final float[] GLOW_TONE_DARK = {42f, 48f, 36f, 60f};
    private static final float[] GLOW_TONE_LIGHT = {78f, 84f, 74f, 88f};
    private static final double GLOW_MAX_CHROMA = 70.0;
    private static final int ERROR_SEED = 0xFFE53935;
    private static final float ERROR_TONE_DARK = 68f;
    private static final float ERROR_TONE_LIGHT = 46f;
    private static final double ERROR_MAX_CHROMA = 80.0;
    private static final double[] GLOW_COVER_TONE_DARK = {36.0, 60.0};
    private static final double[] GLOW_COVER_TONE_LIGHT = {72.0, 88.0};
    private static final double SETTINGS_SURFACE_CHROMA = 6.0;
    private static final float SETTINGS_MIN_TONE_DELTA = 5f;
    private static final float SETTINGS_SWITCH_TRACK_TONE_LIGHT = 78f;
    private static final double SETTINGS_SWITCH_TRACK_CHROMA = 12.0;

    public int background;
    public int surfaceContainer;
    public int text;
    public int textSecondary;
    public int icon;
    public int accent;
    public int onAccent;
    public int tonal;
    public int onTonal;
    public int wave;
    public int track;
    public int buffer;
    public int divider;
    public final int[] glow = new int[4];
    public boolean dark;
    public boolean fromCover;

    public PlayerColors copy() {
        final PlayerColors c = new PlayerColors();
        c.set(this);
        return c;
    }

    public void set(PlayerColors o) {
        background = o.background;
        surfaceContainer = o.surfaceContainer;
        text = o.text;
        textSecondary = o.textSecondary;
        icon = o.icon;
        accent = o.accent;
        onAccent = o.onAccent;
        tonal = o.tonal;
        onTonal = o.onTonal;
        wave = o.wave;
        track = o.track;
        buffer = o.buffer;
        divider = o.divider;
        System.arraycopy(o.glow, 0, glow, 0, glow.length);
        dark = o.dark;
        fromCover = o.fromCover;
    }

    public void blend(PlayerColors from, PlayerColors to, float t) {
        background = ColorUtils.blendARGB(from.background, to.background, t);
        surfaceContainer = ColorUtils.blendARGB(from.surfaceContainer, to.surfaceContainer, t);
        text = ColorUtils.blendARGB(from.text, to.text, t);
        textSecondary = ColorUtils.blendARGB(from.textSecondary, to.textSecondary, t);
        icon = ColorUtils.blendARGB(from.icon, to.icon, t);
        accent = ColorUtils.blendARGB(from.accent, to.accent, t);
        onAccent = ColorUtils.blendARGB(from.onAccent, to.onAccent, t);
        tonal = ColorUtils.blendARGB(from.tonal, to.tonal, t);
        onTonal = ColorUtils.blendARGB(from.onTonal, to.onTonal, t);
        wave = ColorUtils.blendARGB(from.wave, to.wave, t);
        track = ColorUtils.blendARGB(from.track, to.track, t);
        buffer = ColorUtils.blendARGB(from.buffer, to.buffer, t);
        divider = ColorUtils.blendARGB(from.divider, to.divider, t);
        for (int i = 0; i < glow.length; i++) {
            glow[i] = ColorUtils.blendARGB(from.glow[i], to.glow[i], t);
        }
        dark = t < 0.5f ? from.dark : to.dark;
        fromCover = t < 0.5f ? from.fromCover : to.fromCover;
    }

    public boolean equalsTo(PlayerColors o) {
        return o != null && background == o.background && surfaceContainer == o.surfaceContainer && text == o.text
                && textSecondary == o.textSecondary && icon == o.icon && accent == o.accent && onAccent == o.onAccent
                && tonal == o.tonal && onTonal == o.onTonal && wave == o.wave && track == o.track && buffer == o.buffer
                && divider == o.divider && Arrays.equals(glow, o.glow) && dark == o.dark;
    }

    public static PlayerColors fromTheme(Theme.ResourcesProvider resourcesProvider) {
        final PlayerColors c = new PlayerColors();
        c.background = Theme.getColor(Theme.key_player_background, resourcesProvider);
        c.dark = !ColorTones.isLightSurface(c.background);
        c.text = Theme.getColor(Theme.key_player_actionBarTitle, resourcesProvider);
        c.textSecondary = Theme.getColor(Theme.key_player_time, resourcesProvider);
        c.icon = Theme.getColor(Theme.key_player_button, resourcesProvider);
        c.accent = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
        c.onAccent = Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Theme.isCurrentThemeMonet(resourcesProvider)) {
            c.onAccent = Theme.getColor(Theme.key_chats_actionIcon, resourcesProvider);
            c.tonal = MonetUtils.getSecondaryContainerColor(c.dark);
            c.onTonal = MonetUtils.getOnSecondaryContainerColor(c.dark);
        } else {
            c.tonal = withTone(c.accent, c.dark ? 30f : 90f, 24.0);
            c.onTonal = withTone(c.accent, c.dark ? 90f : 30f, c.dark ? 24.0 : 40.0);
        }
        c.wave = Theme.getColor(Theme.key_player_progress, resourcesProvider);
        c.track = Theme.getColor(Theme.key_player_progressBackground, resourcesProvider);
        c.buffer = ColorUtils.blendARGB(c.track, c.wave, 0.35f);
        c.surfaceContainer = withTone(c.background, c.dark ? 16f : 94f, 8.0);
        c.divider = ColorUtils.setAlphaComponent(c.textSecondary, 0x24);
        c.setGlow(c.accent);
        return c;
    }

    public static PlayerColors fromSeed(int seed, boolean dark) {
        final PlayerColors c = new PlayerColors();
        final ColorRoles roles = MaterialColors.getColorRoles(seed, !dark);
        c.dark = dark;
        c.fromCover = true;
        c.background = withTone(seed, dark ? 10f : 98f, 6.0);
        c.surfaceContainer = withTone(seed, dark ? 17f : 93f, 8.0);
        c.text = withTone(seed, dark ? 93f : 10f, 4.0);
        c.textSecondary = withTone(seed, dark ? 70f : 44f, 10.0);
        c.icon = withTone(seed, dark ? 90f : 14f, 8.0);
        c.accent = roles.getAccent();
        c.onAccent = roles.getOnAccent();
        c.tonal = withTone(seed, dark ? 30f : 90f, 24.0);
        c.onTonal = withTone(seed, dark ? 90f : 30f, dark ? 24.0 : 40.0);
        c.wave = roles.getAccent();
        c.track = withTone(seed, dark ? 24f : 90f, 16.0);
        c.buffer = ColorUtils.blendARGB(c.track, c.wave, 0.35f);
        c.divider = ColorUtils.setAlphaComponent(c.textSecondary, 0x24);
        c.setGlow(seed);
        return c;
    }

    private void setGlow(int seed) {
        final double[] lab = new double[3];
        ColorUtils.colorToLAB(seed, lab);
        final double chroma = Math.min(Math.hypot(lab[1], lab[2]), GLOW_MAX_CHROMA);
        final double hue = Math.atan2(lab[2], lab[1]);
        for (int i = 0; i < glow.length; i++) {
            final double h = hue + Math.toRadians(GLOW_HUE_SHIFT[i]);
            final float t = dark ? GLOW_TONE_DARK[i] : GLOW_TONE_LIGHT[i];
            glow[i] = ColorUtils.LABToColor(t, chroma * Math.cos(h), chroma * Math.sin(h)) | 0xFF000000;
        }
    }

    public void setCoverGlow(int[] colors) {
        final double[] lab = new double[3];
        final double[] tones = dark ? GLOW_COVER_TONE_DARK : GLOW_COVER_TONE_LIGHT;
        for (int i = 0; i < glow.length && i < colors.length; i++) {
            ColorUtils.colorToLAB(colors[i], lab);
            final double chroma = Math.hypot(lab[1], lab[2]);
            final double scale = chroma > GLOW_MAX_CHROMA ? GLOW_MAX_CHROMA / chroma : 1.0;
            final double tone = Math.max(tones[0], Math.min(tones[1], lab[0]));
            glow[i] = ColorUtils.LABToColor(tone, lab[1] * scale, lab[2] * scale) | 0xFF000000;
        }
    }

    public static int getMiniPlayerAccent(int themeAccent, Theme.ResourcesProvider resourcesProvider) {
        final Integer seed = getMiniPlayerSeed();
        if (seed == null) {
            return themeAccent;
        }
        return MaterialColors.getColorRoles(seed, !isDarkSurface(resourcesProvider)).getAccent();
    }

    public static int getMiniPlayerText(int themeText, Theme.ResourcesProvider resourcesProvider) {
        final Integer seed = getMiniPlayerSeed();
        if (seed == null) {
            return themeText;
        }
        return withTone(seed, isDarkSurface(resourcesProvider) ? 93f : 10f, 4.0);
    }

    public static int getMiniPlayerSecondary(int themeSecondary, Theme.ResourcesProvider resourcesProvider) {
        final Integer seed = getMiniPlayerSeed();
        if (seed == null) {
            return themeSecondary;
        }
        return withTone(seed, isDarkSurface(resourcesProvider) ? 70f : 44f, 10.0);
    }

    private static Integer getMiniPlayerSeed() {
        if (!ExteraConfig.getPlayerMiniCoverColors()) {
            return null;
        }
        final MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        if (playing == null || playing.isVoice() || playing.isRoundVideo()) {
            return null;
        }
        final Integer seed = CoverColorExtractor.getCachedSeed(playing);
        return seed == null || seed == CoverColorExtractor.NO_SEED ? null : seed;
    }

    private static boolean isDarkSurface(Theme.ResourcesProvider resourcesProvider) {
        return !ColorTones.isLightSurface(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
    }

    public Theme.ResourcesProvider wrap(Theme.ResourcesProvider base) {
        if (!fromCover) {
            return base;
        }
        return new CoverResourcesProvider(base, buildOverrides(), dark);
    }

    public Theme.ResourcesProvider wrapSettings(Theme.ResourcesProvider base) {
        if (!fromCover) {
            return new CoverResourcesProvider(base, new SparseIntArray(), dark);
        }
        final SparseIntArray overrides = buildOverrides();
        final int basePage = Theme.getColor(Theme.key_windowBackgroundGray, base);
        final float baseCardTone = ColorTones.getTone(Theme.getColor(Theme.key_windowBackgroundWhite, base));
        final float basePageTone = ColorTones.getTone(basePage);
        final boolean black = dark && basePage == Color.BLACK;
        final float cardTone = dark ? Math.max(baseCardTone, basePageTone + SETTINGS_MIN_TONE_DELTA) : baseCardTone;
        final float pageTone = dark ? basePageTone : Math.min(basePageTone, baseCardTone - SETTINGS_MIN_TONE_DELTA);
        final int container = withTone(background, cardTone, SETTINGS_SURFACE_CHROMA);
        final int settingsBackground = black ? Color.BLACK : withTone(background, pageTone, SETTINGS_SURFACE_CHROMA);
        overrides.put(Theme.key_dialogBackground, black ? settingsBackground : container);
        overrides.put(Theme.key_windowBackgroundGray, settingsBackground);
        overrides.put(Theme.key_windowBackgroundWhite, container);
        overrides.put(Theme.key_windowBackgroundWhiteBlueHeader, accent);
        overrides.put(Theme.key_windowBackgroundWhiteBlueText, accent);
        overrides.put(Theme.key_windowBackgroundWhiteGrayIcon, textSecondary);
        overrides.put(Theme.key_windowBackgroundWhiteGrayText4, textSecondary);
        overrides.put(Theme.key_windowBackgroundWhiteLinkText, accent);
        overrides.put(Theme.key_windowBackgroundWhiteValueText, accent);
        overrides.put(Theme.key_switchTrack, dark ? textSecondary : withTone(background, SETTINGS_SWITCH_TRACK_TONE_LIGHT, SETTINGS_SWITCH_TRACK_CHROMA));
        overrides.put(Theme.key_switchTrackChecked, accent);
        overrides.put(Theme.key_switch2TrackChecked, accent);
        overrides.put(Theme.key_radioBackground, textSecondary);
        overrides.put(Theme.key_checkboxCheck, onAccent);
        overrides.put(Theme.key_switchTrackBlueThumbChecked, onAccent);
        overrides.put(Theme.key_switchTrackBlueSelector, ColorUtils.setAlphaComponent(text, 0x14));
        overrides.put(Theme.key_switchTrackBlueSelectorChecked, ColorUtils.setAlphaComponent(accent, 0x24));
        overrides.put(Theme.key_settings_listSelector, ColorUtils.setAlphaComponent(text, 0x14));
        overrides.put(Theme.key_sheet_scrollUp, ColorUtils.setAlphaComponent(textSecondary, 0x60));
        return new CoverResourcesProvider(base, overrides, dark);
    }

    private SparseIntArray buildOverrides() {
        final SparseIntArray overrides = new SparseIntArray();
        overrides.put(Theme.key_dialogBackground, background);
        overrides.put(Theme.key_windowBackgroundWhite, background);
        overrides.put(Theme.key_player_background, background);
        overrides.put(Theme.key_dialogTextBlack, text);
        overrides.put(Theme.key_windowBackgroundWhiteBlackText, text);
        overrides.put(Theme.key_windowBackgroundWhiteGrayText, textSecondary);
        overrides.put(Theme.key_windowBackgroundWhiteGrayText2, textSecondary);
        overrides.put(Theme.key_player_actionBarTitle, text);
        overrides.put(Theme.key_player_time, textSecondary);
        overrides.put(Theme.key_player_button, icon);
        overrides.put(Theme.key_player_buttonActive, accent);
        overrides.put(Theme.key_player_progress, wave);
        overrides.put(Theme.key_player_progressBackground, track);
        overrides.put(Theme.key_featuredStickers_addButton, accent);
        overrides.put(Theme.key_featuredStickers_addButtonPressed, accent);
        overrides.put(Theme.key_featuredStickers_buttonText, onAccent);
        overrides.put(Theme.key_actionBarDefaultSubmenuBackground, surfaceContainer);
        overrides.put(Theme.key_actionBarDefaultSubmenuItem, text);
        overrides.put(Theme.key_actionBarDefaultSubmenuItemIcon, textSecondary);
        overrides.put(Theme.key_groupcreate_sectionText, textSecondary);
        overrides.put(Theme.key_actionBarDefaultSubmenuSeparator, ColorUtils.setAlphaComponent(textSecondary, 0x18));
        overrides.put(Theme.key_listSelector, ColorUtils.setAlphaComponent(text, 0x14));
        overrides.put(Theme.key_dialogButtonSelector, ColorUtils.setAlphaComponent(text, 0x14));
        overrides.put(Theme.key_divider, divider);
        overrides.put(Theme.key_windowBackgroundWhiteBlueText4, accent);
        overrides.put(Theme.key_chat_inLoader, accent);
        overrides.put(Theme.key_chat_inMediaIcon, onAccent);
        overrides.put(Theme.key_chat_inLoaderSelected, accent);
        overrides.put(Theme.key_chat_inMediaIconSelected, onAccent);
        overrides.put(Theme.key_chat_inAudioProgress, background);
        overrides.put(Theme.key_chat_inAudioSelectedProgress, background);
        overrides.put(Theme.key_dialogEmptyImage, textSecondary);
        overrides.put(Theme.key_dialogEmptyText, textSecondary);
        overrides.put(Theme.key_emptyListPlaceholder, textSecondary);
        overrides.put(Theme.key_progressCircle, accent);
        overrides.put(Theme.key_dialogSearchBackground, surfaceContainer);
        overrides.put(Theme.key_dialogSearchHint, textSecondary);
        overrides.put(Theme.key_dialogSearchIcon, textSecondary);
        overrides.put(Theme.key_dialogSearchText, text);
        overrides.put(Theme.key_dialogButton, accent);
        overrides.put(Theme.key_dialogTextLink, accent);
        overrides.put(Theme.key_dialogTextGray3, textSecondary);
        overrides.put(Theme.key_dialogIcon, textSecondary);
        overrides.put(Theme.key_dialogScrollGlow, background);
        overrides.put(Theme.key_dialog_inlineProgress, accent);
        overrides.put(Theme.key_dialog_inlineProgressBackground, surfaceContainer);
        overrides.put(Theme.key_windowBackgroundWhiteBlueText, accent);
        final int error = withTone(ERROR_SEED, dark ? ERROR_TONE_DARK : ERROR_TONE_LIGHT, ERROR_MAX_CHROMA);
        overrides.put(Theme.key_text_RedRegular, error);
        overrides.put(Theme.key_text_RedBold, error);
        return overrides;
    }

    private static final class CoverResourcesProvider extends WrappedResourceProvider {

        private final Theme.ResourcesProvider base;
        private final boolean dark;
        private final Paint dividerPaint = new Paint();

        CoverResourcesProvider(Theme.ResourcesProvider base, SparseIntArray overrides, boolean dark) {
            super(base);
            this.base = base;
            this.dark = dark;
            sparseIntArray = overrides;
        }

        @Override
        public int getColorOrDefault(int key) {
            final int index = sparseIntArray.indexOfKey(key);
            return index >= 0 ? sparseIntArray.valueAt(index) : super.getColorOrDefault(key);
        }

        @Override
        public int getCurrentColor(int key) {
            final int index = sparseIntArray.indexOfKey(key);
            return index >= 0 ? sparseIntArray.valueAt(index) : super.getCurrentColor(key);
        }

        @Override
        public Paint getPaint(String paintKey) {
            if (Theme.key_paint_divider.equals(paintKey)) {
                dividerPaint.setColor(Theme.getColor(Theme.key_divider, this));
                return dividerPaint;
            }
            return super.getPaint(paintKey);
        }

        @Override
        public boolean isDark() {
            return dark;
        }

        @Override
        public boolean isMonet() {
            return base != null ? base.isMonet() : Theme.isCurrentThemeMonet();
        }
    }
}
