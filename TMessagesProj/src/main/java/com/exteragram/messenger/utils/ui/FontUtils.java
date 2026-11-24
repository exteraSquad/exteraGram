/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.SystemFonts;
import android.os.Build;
import android.support.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;
import java.util.Locale;

public class FontUtils {

    private static final String TEST_TEXT;
    private static final int CANVAS_SIZE = AndroidUtilities.dp(20);
    private static final Paint PAINT;
    private static final int WEIGHT_NORMAL = 400;
    private static final int WEIGHT_MEDIUM = 500;
    private static final int WEIGHT_BOLD = 700;
    private static final int WEIGHT_EXTRA_BOLD = 800;
    public static final String SANS_SERIF = "sans-serif";
    private static final String ROBOTO_METRICS_TEST_TEXT = "Hamburgefontsiv0123456789";
    private static final float ROBOTO_METRICS_MAX_DIFFERENCE = 0.01f;
    public static boolean loadSystemEmojiFailed = false;
    private static volatile Boolean mediumWeightSupported = null;
    private static volatile Boolean italicSupported = null;
    private static volatile Boolean usePixelGoogleSans = null;
    private static Typeface systemEmojiTypeface;

    static {
        PAINT = new Paint();
        PAINT.setTextSize(CANVAS_SIZE);
        PAINT.setAntiAlias(true);
        PAINT.setSubpixelText(false);
        PAINT.setFakeBoldText(false);

        String lang = "en";
        try {
            if (LocaleController.getInstance() != null &&
                    LocaleController.getInstance().getCurrentLocale() != null) {
                lang = LocaleController.getInstance().getCurrentLocale().getLanguage();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        if (List.of("zh", "ja", "ko").contains(lang)) {
            TEST_TEXT = "你好";
        } else if (List.of("ar", "fa").contains(lang)) {
            TEST_TEXT = "مرحبا";
        } else if ("iw".equals(lang)) {
            TEST_TEXT = "שלום";
        } else if ("th".equals(lang)) {
            TEST_TEXT = "สวัสดี";
        } else if ("hi".equals(lang)) {
            TEST_TEXT = "नमस्ते";
        } else if (List.of("ru", "uk", "ky", "be", "sr").contains(lang)) {
            TEST_TEXT = "Привет";
        } else {
            TEST_TEXT = "R";
        }
    }

    public static boolean isMediumWeightSupported() {
        if (mediumWeightSupported == null) {
            synchronized (FontUtils.class) {
                if (mediumWeightSupported == null) {
                    mediumWeightSupported = supportsMediumWeight();
                    FileLog.d("mediumWeightSupported = " + mediumWeightSupported);
                }
            }
        }
        return mediumWeightSupported;
    }

    public static boolean isItalicSupported() {
        if (italicSupported == null) {
            synchronized (FontUtils.class) {
                if (italicSupported == null) {
                    italicSupported = rendersDifferently(createWeightedSansTypeface(WEIGHT_NORMAL, false), createWeightedSansTypeface(WEIGHT_NORMAL, true));
                    FileLog.d("italicSupported = " + italicSupported);
                }
            }
        }
        return italicSupported;
    }

    private static boolean rendersDifferently(Typeface typeface1, Typeface typeface2) {
        Canvas canvas = new Canvas();

        Bitmap bitmap1 = Bitmap.createBitmap(CANVAS_SIZE * 2, CANVAS_SIZE, Bitmap.Config.ARGB_8888);
        Bitmap bitmap2 = Bitmap.createBitmap(CANVAS_SIZE * 2, CANVAS_SIZE, Bitmap.Config.ARGB_8888);
        synchronized (PAINT) {
            canvas.setBitmap(bitmap1);
            PAINT.setTypeface(typeface1);
            canvas.drawText(TEST_TEXT, 0, CANVAS_SIZE, PAINT);

            canvas.setBitmap(bitmap2);
            PAINT.setTypeface(typeface2);
            canvas.drawText(TEST_TEXT, 0, CANVAS_SIZE, PAINT);
            PAINT.setTypeface(null);
        }

        boolean different = !bitmap1.sameAs(bitmap2);
        AndroidUtilities.recycleBitmaps(List.of(bitmap1, bitmap2));
        return different;
    }

    private static boolean differsFromRegular(Typeface typeface, boolean italic) {
        return rendersDifferently(createWeightedSansTypeface(WEIGHT_NORMAL, italic), typeface);
    }

    private static boolean supportsMediumWeight() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                differsFromRegular(createWeightedSansTypeface(WEIGHT_MEDIUM, false), false)) {
            return true;
        }
        return differsFromRegular(Typeface.create("sans-serif-medium", Typeface.NORMAL), false);
    }

    private static Typeface systemGoogleSans = null;
    private static boolean systemGoogleSansLoaded = false;

    private static boolean isGooglePixelDevice() {
        return "google".equalsIgnoreCase(Build.MANUFACTURER) &&
                Build.MODEL != null &&
                Build.MODEL.toLowerCase(Locale.US).startsWith("pixel");
    }

    private static boolean shouldUsePixelGoogleSans() {
        if (!isGooglePixelDevice()) {
            return false;
        }
        if (usePixelGoogleSans == null) {
            synchronized (FontUtils.class) {
                if (usePixelGoogleSans == null) {
                    Typeface systemSans = Typeface.create(SANS_SERIF, Typeface.NORMAL);
                    Typeface roboto = getFontFromAssets(AndroidUtilities.TYPEFACE_ROBOTO_REGULAR);
                    usePixelGoogleSans = hasSimilarMetrics(systemSans, roboto);
                    FileLog.d("usePixelGoogleSans = " + usePixelGoogleSans);
                }
            }
        }
        return usePixelGoogleSans;
    }

    private static boolean hasSimilarMetrics(Typeface typeface1, Typeface typeface2) {
        Paint paint = new Paint();
        paint.setTextSize(100);

        float[] widths1 = new float[ROBOTO_METRICS_TEST_TEXT.length()];
        float[] widths2 = new float[ROBOTO_METRICS_TEST_TEXT.length()];

        paint.setTypeface(typeface1);
        paint.getTextWidths(ROBOTO_METRICS_TEST_TEXT, widths1);
        paint.setTypeface(typeface2);
        paint.getTextWidths(ROBOTO_METRICS_TEST_TEXT, widths2);

        for (int i = 0; i < widths1.length; i++) {
            if (widths2[i] == 0 ||
                    Math.abs(widths1[i] - widths2[i]) / widths2[i] > ROBOTO_METRICS_MAX_DIFFERENCE) {
                return false;
            }
        }
        return true;
    }

    private static Typeface getBaseSystemTypeface() {
        if (!shouldUsePixelGoogleSans()) {
            return Typeface.create(SANS_SERIF, Typeface.NORMAL);
        }
        if (!systemGoogleSansLoaded) {
            systemGoogleSansLoaded = true;
            String[] aliases = {"google-sans-text", "google-sans"};
            for (var alias : aliases) {
                var tf = Typeface.create(alias, Typeface.NORMAL);
                if (rendersDifferently(tf, Typeface.DEFAULT)) {
                    systemGoogleSans = tf;
                    FileLog.d("system google sans alias = " + alias);
                    return systemGoogleSans;
                }
            }
            if (Build.VERSION.SDK_INT >= 29) {
                var file = getGoogleSansFromSystemApi();
                if (file != null) {
                    systemGoogleSans = Typeface.createFromFile(file);
                    FileLog.d("system google sans file = " + file.getAbsolutePath());
                }
            }
        }
        return systemGoogleSans != null ? systemGoogleSans : Typeface.create(SANS_SERIF, Typeface.NORMAL);
    }

    private static Typeface systemGoogleSansMedium = null;
    private static boolean systemGoogleSansMediumLoaded = false;

    private static Typeface getSystemGoogleSansMedium() {
        if (!shouldUsePixelGoogleSans()) {
            return null;
        }
        if (!systemGoogleSansMediumLoaded) {
            systemGoogleSansMediumLoaded = true;
            String[] aliases = {"variable-title-medium-emphasized", "variable-title-medium"};
            for (String alias : aliases) {
                var tf = Typeface.create(alias, Typeface.NORMAL);
                if (rendersDifferently(tf, Typeface.DEFAULT)) {
                    systemGoogleSansMedium = tf;
                    FileLog.d("system google sans medium alias = " + alias);
                    return systemGoogleSansMedium;
                }
            }
        }
        return systemGoogleSansMedium;
    }

    private static Typeface createWeightedSansTypeface(int weight, boolean italic) {
        if (weight >= WEIGHT_MEDIUM && weight < WEIGHT_BOLD) {
            var medium = getSystemGoogleSansMedium();
            if (medium != null) {
                if (italic) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        return Typeface.create(medium, weight, true);
                    } else {
                        return Typeface.create(medium, Typeface.ITALIC);
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    return Typeface.create(medium, weight, false);
                }
                return medium;
            }
        }

        Typeface base = getBaseSystemTypeface();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Typeface.create(base, weight, italic);
        }
        if (weight >= WEIGHT_BOLD) {
            return Typeface.create(base, italic ? Typeface.BOLD_ITALIC : Typeface.BOLD);
        }
        if (italic) {
            return Typeface.create(base, Typeface.ITALIC);
        }
        return base;
    }

    private static Typeface resolveSansTypeface(int weight, boolean italic) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface weighted = createWeightedSansTypeface(weight, italic);
            if (weight == WEIGHT_NORMAL || differsFromRegular(weighted, italic)) {
                return weighted;
            }
        }

        Typeface base = getBaseSystemTypeface();

        if (weight >= WEIGHT_EXTRA_BOLD) {
            Typeface black = Typeface.create("sans-serif-black", italic ? Typeface.ITALIC : Typeface.NORMAL);
            if (differsFromRegular(black, italic)) {
                return black;
            }
            return Typeface.create(base, italic ? Typeface.BOLD_ITALIC : Typeface.BOLD);
        }

        if (weight >= WEIGHT_MEDIUM) {
            if (weight < WEIGHT_BOLD) {
                var titleMedium = getSystemGoogleSansMedium();
                if (titleMedium != null) {
                    if (differsFromRegular(titleMedium, italic)) {
                        return titleMedium;
                    }
                }
            }
            Typeface medium = Typeface.create("sans-serif-medium", italic ? Typeface.ITALIC : Typeface.NORMAL);
            if (differsFromRegular(medium, italic)) {
                return medium;
            }
            return Typeface.create(base, italic ? Typeface.BOLD_ITALIC : Typeface.BOLD);
        }

        return Typeface.create(base, italic ? Typeface.ITALIC : Typeface.NORMAL);
    }

    public static Typeface getSystemTypeface(String assetPath) {
        return switch (assetPath) {
            case AndroidUtilities.TYPEFACE_ROBOTO_MONO -> Typeface.MONOSPACE;
            case AndroidUtilities.TYPEFACE_ROBOTO_CONDENSED_BOLD ->
                    Typeface.create("sans-serif-condensed", Typeface.BOLD);
            case AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM_ITALIC ->
                    resolveSansTypeface(WEIGHT_MEDIUM, true);
            case AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM ->
                    resolveSansTypeface(WEIGHT_MEDIUM, false);
            case AndroidUtilities.TYPEFACE_ROBOTO_ITALIC ->
                    resolveSansTypeface(WEIGHT_NORMAL, true);
            case AndroidUtilities.TYPEFACE_ROBOTO_REGULAR ->
                    resolveSansTypeface(WEIGHT_NORMAL, false);
            case AndroidUtilities.TYPEFACE_ROBOTO_EXTRA_BOLD ->
                    resolveSansTypeface(WEIGHT_EXTRA_BOLD, false);
            default -> null;
        };
    }

    public static File getSystemEmojiFontPath() {
        File fontFile;

        if (Build.VERSION.SDK_INT >= 29) {
            fontFile = getFontFromSystemApi();
            if (fontFile != null) return fontFile;
        }

        fontFile = getFontFallback();
        if (fontFile != null) return fontFile;

        return getFontFromFontsXml();
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private static File getGoogleSansFromSystemApi() {
        try {
            for (Font font : SystemFonts.getAvailableFonts()) {
                File file = font.getFile();
                if (file != null) {
                    String name = file.getName().toLowerCase(Locale.ROOT);
                    if ((name.contains("googlesanstext") || name.contains("google-sans-text") || name.contains("googlesans") || name.contains("google-sans"))
                            && !name.contains("medium") && !name.contains("bold") && !name.contains("italic") && !name.contains("condensed")) {
                        return file;
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private static File getFontFromSystemApi() {
        try {
            File fallbackEmoji = null;
            for (Font font : SystemFonts.getAvailableFonts()) {
                File file = font.getFile();
                if (file != null) {
                    String name = file.getName().toLowerCase(Locale.ROOT);

                    if (name.contains("samsungcoloremoji")) {
                        return file;
                    }

                    if (fallbackEmoji == null && name.contains("emoji")) {
                        fallbackEmoji = file;
                    }
                }
            }
            return fallbackEmoji;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static File getFontFallback() {
        String[] fallbackFonts = {
                "/system/fonts/SamsungColorEmoji.ttf", // Samsung
                "/system/fonts/NotoColorEmoji.ttf",    // AOSP
                "/system/fonts/AndroidEmoji.ttf"       // Legacy
        };

        for (String path : fallbackFonts) {
            File fallbackFile = new File(path);
            if (fallbackFile.exists()) {
                FileLog.d("emoji font file fallback = " + path);
                return fallbackFile;
            }
        }
        return null;
    }

    private static File getFontFromFontsXml() {
        try (var br = new BufferedReader(new FileReader("/system/etc/fonts.xml"))) {
            String line;
            var ignored = false;
            while ((line = br.readLine()) != null) {
                var trimmed = line.trim();
                if (trimmed.startsWith("<family") && trimmed.contains("ignore=\"true\"")) {
                    ignored = true;
                } else if (trimmed.startsWith("</family>")) {
                    ignored = false;
                } else if (trimmed.startsWith("<font") && !ignored) {
                    int start = trimmed.indexOf(">");
                    int end = trimmed.indexOf("<", 1);
                    if (start > 0 && end > 0) {
                        var fontName = trimmed.substring(start + 1, end);
                        if (fontName.toLowerCase(Locale.ROOT).contains("emoji")) {
                            File file = new File("/system/fonts/" + fontName);
                            if (file.exists()) {
                                FileLog.d("emoji font file fonts.xml = " + fontName);
                                return file;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public static Typeface getSystemEmojiTypeface() {
        if (!loadSystemEmojiFailed && systemEmojiTypeface == null) {
            var font = getSystemEmojiFontPath();
            if (font != null) {
                systemEmojiTypeface = Typeface.createFromFile(font);
            }
            if (systemEmojiTypeface == null) {
                loadSystemEmojiFailed = true;
            }
        }
        return systemEmojiTypeface;
    }

    public static Typeface getFontFromAssets(String assetPath) {
        Typeface t;
        if (Build.VERSION.SDK_INT >= 26) {
            Typeface.Builder builder = new Typeface.Builder(ApplicationLoader.applicationContext.getAssets(), assetPath);
            if (assetPath.contains("medium")) {
                builder.setWeight(700);
            }
            if (assetPath.contains("italic")) {
                builder.setItalic(true);
            }
            t = builder.build();
        } else {
            t = Typeface.createFromAsset(ApplicationLoader.applicationContext.getAssets(), assetPath);
        }
        return t;
    }
}