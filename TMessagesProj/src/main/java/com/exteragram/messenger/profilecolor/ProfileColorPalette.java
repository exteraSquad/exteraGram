/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.profilecolor;

import android.graphics.Color;

import androidx.core.graphics.ColorUtils;

import org.telegram.ui.ActionBar.OKLCH;

public final class ProfileColorPalette {

    public static final int ROLE_BACKGROUND = 0;
    public static final int ROLE_CARD = 1;
    public static final int ROLE_TEXT = 2;
    public static final int ROLE_TEXT_SECONDARY = 3;
    public static final int ROLE_TEXT_HINT = 4;
    public static final int ROLE_ICON = 5;
    public static final int ROLE_ACCENT = 6;
    public static final int ROLE_ON_ACCENT = 7;
    public static final int ROLE_ACCENT_PRESSED = 8;
    public static final int ROLE_DIVIDER = 9;
    public static final int ROLE_SELECTOR = 10;
    public static final int ROLE_TRACK = 11;
    public static final int ROLE_PLACEHOLDER = 12;
    public static final int ROLE_LINK_SELECTION = 13;
    public static final int ROLE_RAISED = 14;
    public static final int ROLES_COUNT = 15;

    public static final int ROLE_KEEP_HUE = ROLES_COUNT;
    public static final int ROLE_KEEP_HUE_FILL = ROLES_COUNT + 1;

    private static final float WHITE_CONTENT_MIN_CONTRAST = 2.6f;
    private static final float CARD_MIN_CONTRAST = 5.6f;
    private static final double CARD_MIN_DELTA = 0.085;
    private static final double CARD_MAX_DELTA = 0.16;
    private static final double CARD_CHROMA = 0.94;

    public final int edgeColor;
    public final int centerColor;
    public final boolean lightContent;
    public final int[] colors = new int[ROLES_COUNT];
    public final int headerAccent;

    private final double hue;
    private final double chroma;

    public ProfileColorPalette(int edgeColor, int centerColor) {
        this.edgeColor = edgeColor = ColorUtils.setAlphaComponent(edgeColor, 0xFF);
        this.centerColor = centerColor = ColorUtils.setAlphaComponent(centerColor, 0xFF);

        final double[] lch = toLch(edgeColor);
        final double lightness = lch[0];
        chroma = lch[1];
        hue = lch[2];
        lightContent = ColorUtils.calculateContrast(Color.WHITE, edgeColor) >= WHITE_CONTENT_MIN_CONTRAST;

        final int background = edgeColor;
        final int card;
        final int raised;
        final int text;
        if (lightContent) {
            if (lightness >= 0.38) {
                double delta = CARD_MIN_DELTA;
                int color = fromLch(lightness - delta, chroma * CARD_CHROMA, hue);
                while (ColorUtils.calculateContrast(Color.WHITE, color) < CARD_MIN_CONTRAST && delta < CARD_MAX_DELTA) {
                    delta += 0.005;
                    color = fromLch(lightness - delta, chroma * CARD_CHROMA, hue);
                }
                card = color;
                raised = fromLch(lightness - delta - 0.06, chroma * CARD_CHROMA, hue);
            } else {
                card = fromLch(lightness + 0.062, chroma, hue);
                raised = fromLch(lightness + 0.118, chroma, hue);
            }
            text = Color.WHITE;
        } else {
            if (lightness <= 0.9) {
                card = fromLch(Math.min(0.985, lightness + 0.07), chroma * 0.8, hue);
                raised = fromLch(Math.min(1.0, lightness + 0.12), chroma * 0.6, hue);
            } else {
                card = fromLch(lightness - 0.045, chroma, hue);
                raised = fromLch(lightness - 0.09, chroma, hue);
            }
            text = fromLch(0.22, Math.min(chroma, 0.045), hue);
        }
        final int accent = lightContent ? Color.WHITE : fromLch(0.36, Math.min(Math.max(chroma, 0.04) * 1.25, 0.13), hue);

        headerAccent = lightContent ? fromLch(0.92, Math.min(Math.max(chroma, 0.02) * 0.6, 0.07), hue) : text;

        colors[ROLE_BACKGROUND] = background;
        colors[ROLE_CARD] = card;
        colors[ROLE_RAISED] = raised;
        colors[ROLE_TEXT] = text;
        colors[ROLE_TEXT_SECONDARY] = alpha(text, lightContent ? 0.8f : 0.72f);
        colors[ROLE_TEXT_HINT] = alpha(text, lightContent ? 0.6f : 0.56f);
        colors[ROLE_ICON] = alpha(text, lightContent ? 0.86f : 0.76f);
        colors[ROLE_ACCENT] = accent;
        colors[ROLE_ON_ACCENT] = lightContent ? onLight(card) : Color.WHITE;
        colors[ROLE_ACCENT_PRESSED] = ColorUtils.blendARGB(accent, card, 0.18f);
        colors[ROLE_DIVIDER] = alpha(text, lightContent ? 0.14f : 0.12f);
        colors[ROLE_SELECTOR] = alpha(text, lightContent ? 0.12f : 0.08f);
        colors[ROLE_TRACK] = alpha(text, 0.32f);
        colors[ROLE_PLACEHOLDER] = alpha(text, 0.1f);
        colors[ROLE_LINK_SELECTION] = alpha(text, 0.22f);
    }

    private int onLight(int color) {
        if (ColorUtils.calculateContrast(color, Color.WHITE) >= 4.5f) {
            return color;
        }
        final double[] lch = toLch(color);
        double lightness = lch[0];
        int result = color;
        for (int i = 0; i < 12 && ColorUtils.calculateContrast(result, Color.WHITE) < 4.5f; ++i) {
            lightness -= 0.04;
            result = fromLch(Math.max(0, lightness), lch[1], lch[2]);
        }
        return result;
    }

    public int keepHue(int color, boolean fill) {
        final double[] lch = toLch(color);
        if (Double.isNaN(lch[2]) || lch[1] < 0.03) {
            return fill ? colors[ROLE_ACCENT] : colors[ROLE_TEXT];
        }
        final int surface = colors[ROLE_CARD];
        double lightness;
        double maxChroma;
        if (lightContent) {
            lightness = fill ? 0.8 : 0.87;
            maxChroma = fill ? 0.14 : 0.105;
        } else {
            lightness = fill ? 0.5 : 0.42;
            maxChroma = 0.19;
        }
        int result = fromLch(lightness, Math.min(lch[1], maxChroma), lch[2]);
        final float target = fill ? 2.4f : 3.6f;
        for (int i = 0; i < 6 && ColorUtils.calculateContrast(result, surface) < target; ++i) {
            lightness += lightContent ? 0.025 : -0.04;
            result = fromLch(Math.max(0, Math.min(1, lightness)), Math.min(lch[1], maxChroma), lch[2]);
        }
        return ColorUtils.setAlphaComponent(result, Color.alpha(color));
    }

    public boolean sameSource(int edgeColor, int centerColor) {
        return this.edgeColor == ColorUtils.setAlphaComponent(edgeColor, 0xFF) && this.centerColor == ColorUtils.setAlphaComponent(centerColor, 0xFF);
    }

    private static int alpha(int color, float alpha) {
        return ColorUtils.setAlphaComponent(color, Math.round(0xFF * alpha));
    }

    private static double[] toLch(int color) {
        return OKLCH.rgb2oklch(OKLCH.rgb2srgbLinear(OKLCH.rgb(color)));
    }

    private static int fromLch(double lightness, double chroma, double hue) {
        final double[] lch = new double[]{Math.max(0, Math.min(1, lightness)), Math.max(0, chroma), hue};
        double[] linear = OKLCH.oklch2rgb(lch);
        for (int i = 0; i < 40 && !inGamut(linear); ++i) {
            lch[1] *= 0.93;
            linear = OKLCH.oklch2rgb(lch);
        }
        return OKLCH.rgb(OKLCH.srgbLinear2rgb(linear));
    }

    private static boolean inGamut(double[] linear) {
        for (int i = 0; i < 3; ++i) {
            if (linear[i] < -0.0005 || linear[i] > 1.0005) {
                return false;
            }
        }
        return true;
    }
}
