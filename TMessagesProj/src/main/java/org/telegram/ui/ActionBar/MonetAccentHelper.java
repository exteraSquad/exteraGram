package org.telegram.ui.ActionBar;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.exteragram.messenger.utils.ui.MonetUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;

import java.io.File;
import java.util.ArrayList;

public final class MonetAccentHelper {

    private static final String FALLBACK_PATTERN_SLUG = "__monet_default_pattern__";
    private static final int MONET_PRIMARY_THEME_ACCENT_ID = 91;
    private static final int MONET_SECONDARY_THEME_ACCENT_ID = 90;
    private static final int MONET_TERTIARY_THEME_ACCENT_ID = 89;
    private static final int[] ACCENT_IDS = {
            MONET_PRIMARY_THEME_ACCENT_ID,
            MONET_SECONDARY_THEME_ACCENT_ID,
            MONET_TERTIARY_THEME_ACCENT_ID
    };

    private static Bitmap fallbackPatternBitmap;
    private static int fallbackPatternWidth;
    private static int fallbackPatternHeight;

    private MonetAccentHelper() {
    }

    public static void appendAccentOptions(Theme.ThemeInfo themeInfo) {
        if (!isSupported() || themeInfo == null || themeInfo.themeAccentsMap == null || themeInfo.themeAccents == null) {
            return;
        }
        for (int i = 0; i < ACCENT_IDS.length; i++) {
            ensureAccent(themeInfo, ACCENT_IDS[i], i);
        }
    }

    public static boolean refresh(Theme.ThemeInfo themeInfo) {
        if (themeInfo == null) {
            return false;
        }
        boolean requiresPatternReload = refreshAccents(themeInfo);
        refreshPreviewColors(themeInfo);
        return requiresPatternReload;
    }

    public static boolean isMonetAccent(@Nullable Theme.ThemeAccent accent) {
        if (accent == null) {
            return false;
        }
        return accent.id == MONET_PRIMARY_THEME_ACCENT_ID
                || accent.id == MONET_SECONDARY_THEME_ACCENT_ID
                || accent.id == MONET_TERTIARY_THEME_ACCENT_ID;
    }

    public static boolean canEditAccent(@Nullable Theme.ThemeAccent accent) {
        return accent != null && accent.id >= 100 && !accent.isDefault && !isMonetAccent(accent);
    }

    public static boolean hasRemotePatternWallpaper(@Nullable Theme.ThemeAccent accent) {
        return accent != null && !TextUtils.isEmpty(accent.patternSlug) && !isFallbackPattern(accent);
    }

    public static boolean isFallbackPattern(@Nullable Theme.ThemeAccent accent) {
        return accent != null && FALLBACK_PATTERN_SLUG.equals(accent.patternSlug);
    }

    public static MotionBackgroundDrawable createFallbackPatternDrawable(int backgroundColor, int gradientToColor1, int gradientToColor2, int gradientToColor3, int rotation, int intensity, boolean preview, int phase) {
        MotionBackgroundDrawable motionBackgroundDrawable;
        if (gradientToColor1 != 0) {
            if (gradientToColor2 != 0 || gradientToColor3 != 0) {
                motionBackgroundDrawable = new MotionBackgroundDrawable(backgroundColor, gradientToColor1, gradientToColor2, gradientToColor3, rotation, preview);
            } else {
                motionBackgroundDrawable = new MotionBackgroundDrawable(backgroundColor, gradientToColor1, backgroundColor, gradientToColor1, rotation, preview);
            }
        } else {
            motionBackgroundDrawable = new MotionBackgroundDrawable(backgroundColor, backgroundColor, backgroundColor, backgroundColor, rotation, preview);
        }
        motionBackgroundDrawable.setPhase(phase);
        motionBackgroundDrawable.setPatternBitmap(intensity, getFallbackPatternBitmap());
        motionBackgroundDrawable.setPatternColorFilter(motionBackgroundDrawable.getPatternColor());
        return motionBackgroundDrawable;
    }

    public static int countLeadingMonetAccents(@Nullable ArrayList<Theme.ThemeAccent> accents) {
        if (accents == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0, size = accents.size(); i < size; i++) {
            if (!isMonetAccent(accents.get(i))) {
                break;
            }
            count++;
        }
        return count;
    }

    private static boolean isSupported() {
        return MonetUtils.isSupported();
    }

    private static void ensureAccent(Theme.ThemeInfo themeInfo, int id, int index) {
        Theme.ThemeAccent accent = themeInfo.themeAccentsMap.get(id);
        if (accent == null) {
            accent = new Theme.ThemeAccent();
            accent.id = id;
            accent.parentTheme = themeInfo;
            themeInfo.themeAccentsMap.put(id, accent);
            themeInfo.themeAccents.add(accent);
            themeInfo.defaultAccentCount++;
        }
        fillAccentValues(themeInfo, accent, index, false);
    }

    private static boolean refreshAccents(Theme.ThemeInfo themeInfo) {
        if (!isSupported() || themeInfo.themeAccentsMap == null) {
            return false;
        }
        boolean requiresPatternReload = false;
        for (int i = 0; i < ACCENT_IDS.length; i++) {
            Theme.ThemeAccent accent = themeInfo.themeAccentsMap.get(ACCENT_IDS[i]);
            if (accent != null) {
                requiresPatternReload |= fillAccentValues(themeInfo, accent, i, true);
            }
        }
        return requiresPatternReload;
    }

    private static boolean fillAccentValues(Theme.ThemeInfo themeInfo, Theme.ThemeAccent accent, int index, boolean invalidatePatternCache) {
        File oldWallpaperFile = accent.getPathToWallpaper();
        int accentColor = MonetUtils.getSystemAccentColor(index, themeInfo.isDark());
        Theme.ThemeAccent sourceAccent = getSourceAccent(themeInfo, accent.id);

        accent.accentColor = accentColor;
        accent.accentColor2 = 0;
        accent.myMessagesAccentColor = accentColor;
        accent.myMessagesGradientAccentColor1 = 0;
        accent.myMessagesGradientAccentColor2 = 0;
        accent.myMessagesGradientAccentColor3 = 0;
        accent.myMessagesAnimated = false;
        fillWallpaperValues(themeInfo, accent, sourceAccent, accentColor);
        if (sourceAccent != null && !TextUtils.isEmpty(sourceAccent.patternSlug)) {
            accent.patternSlug = sourceAccent.patternSlug;
            accent.patternIntensity = sourceAccent.patternIntensity;
            accent.patternMotion = sourceAccent.patternMotion;
        } else if (themeInfo.firstAccentIsDefault) {
            accent.patternSlug = FALLBACK_PATTERN_SLUG;
            accent.patternIntensity = 0.34f;
            accent.patternMotion = false;
        } else {
            accent.patternSlug = "";
            accent.patternIntensity = 0;
            accent.patternMotion = false;
        }
        if (invalidatePatternCache) {
            deleteCachedWallpaper(oldWallpaperFile);
            deleteCachedWallpaper(accent.getPathToWallpaper());
        }
        return invalidatePatternCache && hasRemotePatternWallpaper(accent);
    }

    private static void fillWallpaperValues(Theme.ThemeInfo themeInfo, Theme.ThemeAccent accent, @Nullable Theme.ThemeAccent sourceAccent, int accentColor) {
        if (sourceAccent == null || !hasWallpaperColors(sourceAccent)) {
            accent.backgroundOverrideColor = 0;
            accent.backgroundGradientOverrideColor1 = 0;
            accent.backgroundGradientOverrideColor2 = 0;
            accent.backgroundGradientOverrideColor3 = 0;
            accent.backgroundRotation = 45;
            return;
        }
        accent.backgroundOverrideColor = changeWallpaperColor(themeInfo, accentColor, sourceAccent.backgroundOverrideColor);
        accent.backgroundGradientOverrideColor1 = changeWallpaperColor(themeInfo, accentColor, sourceAccent.backgroundGradientOverrideColor1);
        accent.backgroundGradientOverrideColor2 = changeWallpaperColor(themeInfo, accentColor, sourceAccent.backgroundGradientOverrideColor2);
        accent.backgroundGradientOverrideColor3 = changeWallpaperColor(themeInfo, accentColor, sourceAccent.backgroundGradientOverrideColor3);
        accent.backgroundRotation = sourceAccent.backgroundRotation;
    }

    private static boolean hasWallpaperColors(Theme.ThemeAccent accent) {
        return accent.backgroundOverrideColor != 0
                || accent.backgroundGradientOverrideColor1 != 0
                || accent.backgroundGradientOverrideColor2 != 0
                || accent.backgroundGradientOverrideColor3 != 0;
    }

    private static long changeWallpaperColor(Theme.ThemeInfo themeInfo, int accentColor, long color) {
        if (color == 0 || color == 0x100000000L) {
            return 0;
        }
        if (accentColor == 0 || themeInfo.accentBaseColor == 0 || accentColor == themeInfo.accentBaseColor) {
            return color;
        }
        float[] baseHsv = new float[3];
        float[] accentHsv = new float[3];
        Color.colorToHSV(themeInfo.accentBaseColor, baseHsv);
        Color.colorToHSV(accentColor, accentHsv);
        return Theme.changeColorAccent(baseHsv, accentHsv, (int) color, themeInfo.isDark(), (int) color);
    }

    private static void deleteCachedWallpaper(@Nullable File wallpaperFile) {
        if (wallpaperFile != null && wallpaperFile.exists()) {
            wallpaperFile.delete();
        }
    }

    private static Theme.ThemeAccent getSourceAccent(Theme.ThemeInfo themeInfo, int accentId) {
        if (themeInfo.themeAccentsMap == null || themeInfo.themeAccents == null) {
            return null;
        }
        int sourceAccentId = themeInfo.firstAccentIsDefault ? Theme.DEFALT_THEME_ACCENT_ID : 0;
        Theme.ThemeAccent sourceAccent = themeInfo.themeAccentsMap.get(sourceAccentId);
        if (sourceAccent != null && sourceAccent.id != accentId) {
            return sourceAccent;
        }
        for (int i = 0, size = themeInfo.themeAccents.size(); i < size; i++) {
            Theme.ThemeAccent accent = themeInfo.themeAccents.get(i);
            if (!isMonetAccent(accent) && accent.id != accentId) {
                return accent;
            }
        }
        return null;
    }

    private static void refreshPreviewColors(Theme.ThemeInfo themeInfo) {
        if (!isSupported() || !themeInfo.isMonet()) {
            return;
        }
        if ("Monet Light".equals(themeInfo.name)) {
            themeInfo.setPreviewBackgroundColor(MonetUtils.getColor("n1_10"));
            themeInfo.setPreviewInColor(MonetUtils.getColor("n1_50"));
            themeInfo.setPreviewOutColor(MonetUtils.getColor("a1_600"));
        } else if ("Monet Dark".equals(themeInfo.name)) {
            themeInfo.setPreviewBackgroundColor(MonetUtils.getColor("n1_900"));
            themeInfo.setPreviewInColor(MonetUtils.getColor("n1_800"));
            themeInfo.setPreviewOutColor(MonetUtils.getColor("a1_200"));
        } else if ("Monet Black".equals(themeInfo.name)) {
            themeInfo.setPreviewBackgroundColor(MonetUtils.getColor("mBlack"));
            themeInfo.setPreviewInColor(MonetUtils.getColor("n1_800"));
            themeInfo.setPreviewOutColor(MonetUtils.getColor("a1_200"));
        }
    }

    private static Bitmap getFallbackPatternBitmap() {
        int width = Math.max(1, Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y));
        int height = Math.max(1, Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y));
        synchronized (MonetAccentHelper.class) {
            if (fallbackPatternBitmap == null
                    || fallbackPatternBitmap.isRecycled()
                    || fallbackPatternWidth != width
                    || fallbackPatternHeight != height) {
                fallbackPatternBitmap = SvgHelper.getBitmap(R.raw.default_pattern, width, height, Color.BLACK);
                fallbackPatternWidth = width;
                fallbackPatternHeight = height;
            }
            return fallbackPatternBitmap;
        }
    }
}