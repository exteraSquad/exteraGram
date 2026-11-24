/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.custom;

import android.graphics.Color;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;


import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.regex.Pattern;

public record PillColors(int top, int bottom) {

    private static final int DARK_FOREGROUND = 0xE6000000;
    private static final double MIN_WHITE_CONTRAST = 1.75;
    private static final double MIN_WHITE_CONTRAST_MUTED = 2.2;
    private static final float MUTED_CHROMA = .4f;

    public int foreground() {
        return foreground(top, bottom);
    }

    public static int foreground(int top, int bottom) {
        int color = ColorUtils.blendARGB(top, bottom, .5f) | 0xFF000000;
        double contrast = ColorUtils.calculateContrast(Color.WHITE, color);
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        float chroma = (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b))) / 255f;
        boolean dark = contrast < MIN_WHITE_CONTRAST || contrast < MIN_WHITE_CONTRAST_MUTED && chroma < MUTED_CHROMA;
        return dark ? DARK_FOREGROUND : Color.WHITE;
    }

    private static final Pattern CUSTOM = Pattern.compile("#[0-9A-Fa-f]{6}");
    private static final PillColors GRAY = new PillColors(0xFF8699AA, 0xFF6E8397);
    private static final LinkedHashMap<String, PillColors> PALETTE = new LinkedHashMap<>();

    static {
        PALETTE.put("RED", new PillColors(0xFFF45255, 0xFFDF3955));
        PALETTE.put("ORANGE_DEEP", new PillColors(0xFFF28B31, 0xFFE26314));
        PALETTE.put("ORANGE_BRIGHT", new PillColors(0xFFEFA612, 0xFFE77512));
        PALETTE.put("YELLOW", new PillColors(0xFFF4C430, 0xFFE8AE10));
        PALETTE.put("GREEN", new PillColors(0xFF55CA47, 0xFF27B434));
        PALETTE.put("GREEN_DEEP", new PillColors(0xFF1D8B5D, 0xFF187B59));
        PALETTE.put("TEAL", new PillColors(0xFF2BB8A3, 0xFF169884));
        PALETTE.put("CYAN", new PillColors(0xFF32C0CE, 0xFF1D9CC6));
        PALETTE.put("BLUE", new PillColors(0xFF1CA5ED, 0xFF1488E1));
        PALETTE.put("BLUE_DEEP", new PillColors(0xFF4F85F6, 0xFF3568E8));
        PALETTE.put("INDIGO", new PillColors(0xFF7A77F5, 0xFF5B54E0));
        PALETTE.put("PURPLE", new PillColors(0xFFC46EF4, 0xFF9F55DF));
        PALETTE.put("PINK", new PillColors(0xFFF46FAE, 0xFFE0498F));
        PALETTE.put("BROWN", new PillColors(0xFFB08463, 0xFF8F6547));
        PALETTE.put("GRAY", GRAY);
        PALETTE.put("GRAPHITE", new PillColors(0xFF5D6670, 0xFF434A53));
    }

    public static String[] getPalette() {
        return PALETTE.keySet().toArray(new String[0]);
    }

    public static PillColors get(@Nullable String color) {
        if (color == null) {
            return GRAY;
        }
        PillColors colors = PALETTE.get(color);
        if (colors != null) {
            return colors;
        }
        if (isCustom(color)) {
            int base = Color.parseColor(color);
            return new PillColors(ColorUtils.blendARGB(base, Color.WHITE, 0.08f), ColorUtils.blendARGB(base, Color.BLACK, 0.08f));
        }
        return GRAY;
    }

    public static boolean isValid(@Nullable String color) {
        return color != null && (CustomPillSpec.COLOR_NEUTRAL.equals(color) || PALETTE.containsKey(color) || isCustom(color));
    }

    public static boolean isCustom(@Nullable String color) {
        return color != null && CUSTOM.matcher(color).matches();
    }

    public static String toCustom(int color) {
        return String.format(Locale.US, "#%06X", color & 0xFFFFFF);
    }
}
