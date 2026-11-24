/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.appicons;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AppIconPreviewLoader {

    public interface Callback {
        void onPreviewReady(AppIcon icon, int size, Bitmap bitmap);
    }

    private static final int HUE_BUCKETS = 12;
    private static final int ACCENT_SAMPLES = 24;

    private static final float ACCENT_TEXT_MIN_SATURATION = 0.15f;
    private static final float ACCENT_TEXT_LIGHTNESS_DARK = 0.72f;
    private static final float ACCENT_TEXT_LIGHTNESS_LIGHT = 0.42f;
    private static final float ACCENT_TEXT_TONE_DARK = 70f;
    private static final float ACCENT_TEXT_TONE_LIGHT = 42f;

    private static final float ACCENT_TINT_MAX_SATURATION = 0.6f;
    private static final float ACCENT_TINT_LIGHTNESS_DARK = 0.22f;
    private static final float ACCENT_TINT_LIGHTNESS_LIGHT = 0.86f;
    private static final float ACCENT_TINT_TONE_DARK = 20f;
    private static final float ACCENT_TINT_TONE_LIGHT = 88f;

    private static final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private static final Map<String, List<Callback>> pending = new HashMap<>();
    private static final List<Integer> knownSizes = new ArrayList<>();
    private static final Map<String, Integer> accents = new HashMap<>();
    private static LruCache<String, Bitmap> cache;

    private AppIconPreviewLoader() {
    }

    private static LruCache<String, Bitmap> getCache() {
        if (cache == null) {
            int limit = (int) Math.min(12 * 1024 * 1024L, Runtime.getRuntime().maxMemory() / 16);
            cache = new LruCache<String, Bitmap>(limit) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };
        }
        return cache;
    }

    private static String key(AppIcon icon, int size) {
        return icon.id + "@" + size;
    }

    public static Bitmap getCached(AppIcon icon, int size) {
        return getCache().get(key(icon, size));
    }

    public static Bitmap getAnyCached(AppIcon icon) {
        Bitmap best = null;
        for (int i = 0; i < knownSizes.size(); ++i) {
            final Bitmap bitmap = getCache().get(key(icon, knownSizes.get(i)));
            if (bitmap != null && (best == null || bitmap.getWidth() > best.getWidth())) {
                best = bitmap;
            }
        }
        return best;
    }

    public static void trimAbove(int size) {
        for (Map.Entry<String, Bitmap> entry : getCache().snapshot().entrySet()) {
            if (entry.getValue().getWidth() >= size) {
                getCache().remove(entry.getKey());
            }
        }
    }

    public static int getAccent(AppIcon icon) {
        if (icon.color != 0) {
            return icon.color;
        }
        Integer accent = accents.get(icon.id);
        if (accent != null) {
            return accent;
        }
        if (icon.isBackgroundColor()) {
            accent = getBackgroundColor(icon);
            final float[] hsl = new float[3];
            ColorUtils.colorToHSL(accent, hsl);
            if (isNeutral(hsl)) {
                return 0;
            }
            accents.put(icon.id, accent);
            return accent;
        }
        return 0;
    }

    private static int getBackgroundColor(AppIcon icon) {
        try {
            return ContextCompat.getColor(ApplicationLoader.applicationContext, icon.getBackground());
        } catch (Throwable e) {
            FileLog.e(e);
            return 0;
        }
    }

    public static int getAccentTextColor(AppIcon icon, int fallbackColorKey, Theme.ResourcesProvider resourcesProvider) {
        final int accent = icon == null ? 0 : getAccent(icon);
        if (accent != 0) {
            final float[] hsl = new float[3];
            ColorUtils.colorToHSL(accent, hsl);
            if (hsl[1] >= ACCENT_TEXT_MIN_SATURATION) {
                if (Theme.isCurrentThemeDark()) {
                    hsl[2] = Math.max(hsl[2], ACCENT_TEXT_LIGHTNESS_DARK);
                    return clampTone(hsl, ACCENT_TEXT_TONE_DARK, 100f);
                }
                hsl[2] = Math.min(hsl[2], ACCENT_TEXT_LIGHTNESS_LIGHT);
                return clampTone(hsl, 0f, ACCENT_TEXT_TONE_LIGHT);
            }
        }
        return Theme.getColor(fallbackColorKey, resourcesProvider);
    }

    public static int getAccentTint(AppIcon icon) {
        final int accent = icon == null ? 0 : getAccent(icon);
        if (accent == 0) {
            return 0;
        }
        final float[] hsl = new float[3];
        ColorUtils.colorToHSL(accent, hsl);
        hsl[1] = Math.min(hsl[1], ACCENT_TINT_MAX_SATURATION);
        if (Theme.isCurrentThemeDark()) {
            hsl[2] = ACCENT_TINT_LIGHTNESS_DARK;
            return clampTone(hsl, ACCENT_TINT_TONE_DARK, 100f);
        }
        hsl[2] = ACCENT_TINT_LIGHTNESS_LIGHT;
        return clampTone(hsl, 0f, ACCENT_TINT_TONE_LIGHT);
    }

    private static int clampTone(float[] hsl, float minTone, float maxTone) {
        final double[] lab = new double[3];
        ColorUtils.colorToLAB(ColorUtils.HSLToColor(hsl), lab);
        if (lab[0] >= minTone && lab[0] <= maxTone) {
            return ColorUtils.HSLToColor(hsl);
        }
        final float tone = lab[0] < minTone ? minTone : maxTone;
        float low = 0f, high = 1f;
        for (int i = 0; i < 16; ++i) {
            hsl[2] = (low + high) / 2f;
            ColorUtils.colorToLAB(ColorUtils.HSLToColor(hsl), lab);
            if (lab[0] < tone) {
                low = hsl[2];
            } else {
                high = hsl[2];
            }
        }
        hsl[2] = (low + high) / 2f;
        return ColorUtils.HSLToColor(hsl);
    }

    public static void load(AppIcon icon, int size, Callback callback) {
        if (size <= 0) {
            return;
        }
        if (!knownSizes.contains(size)) {
            knownSizes.add(size);
        }
        final String key = key(icon, size);
        Bitmap cached = getCache().get(key);
        if (cached != null) {
            callback.onPreviewReady(icon, size, cached);
            return;
        }

        List<Callback> callbacks = pending.get(key);
        if (callbacks != null) {
            callbacks.add(callback);
            return;
        }
        callbacks = new ArrayList<>();
        callbacks.add(callback);
        pending.put(key, callbacks);

        final boolean sampleAccent = !accents.containsKey(icon.id) && getAccent(icon) == 0;
        Utilities.globalQueue.postRunnable(() -> {
            final Bitmap bitmap = render(icon, size);
            final int accent = bitmap == null || !sampleAccent ? 0
                    : extractAccent(bitmap, icon.isBackgroundColor() ? getBackgroundColor(icon) : 0);
            AndroidUtilities.runOnUIThread(() -> {
                if (bitmap != null) {
                    getCache().put(key, bitmap);
                }
                if (accent != 0 && !accents.containsKey(icon.id)) {
                    accents.put(icon.id, accent);
                }
                List<Callback> waiting = pending.remove(key);
                if (waiting == null || bitmap == null) {
                    return;
                }
                for (Callback waiter : waiting) {
                    waiter.onPreviewReady(icon, size, bitmap);
                }
            });
        });
    }

    private static Bitmap render(AppIcon icon, int size) {
        try {
            Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(result);
            int inset = size / 4;
            Rect bounds = new Rect(-inset, -inset, size + inset, size + inset);

            if (icon.isBackgroundColor()) {
                canvas.drawColor(ContextCompat.getColor(ApplicationLoader.applicationContext, icon.getBackground()));
            } else {
                draw(canvas, icon.getBackground(), bounds);
            }
            draw(canvas, icon.getForeground(), bounds);
            return result;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    private static boolean isNeutral(float[] hsl) {
        return hsl[1] < 0.15f || hsl[2] < 0.06f || hsl[2] > 0.94f;
    }

    private static int extractAccent(Bitmap bitmap, int background) {
        final float stepX = (bitmap.getWidth() - 1) / (float) (ACCENT_SAMPLES - 1);
        final float stepY = (bitmap.getHeight() - 1) / (float) (ACCENT_SAMPLES - 1);
        final float[] hsl = new float[3];
        final float[] weights = new float[HUE_BUCKETS];
        final float[] hues = new float[HUE_BUCKETS];
        final float[] saturations = new float[HUE_BUCKETS];
        final float[] lightnesses = new float[HUE_BUCKETS];

        float neutralCount = 0f, neutralLightness = 0f, coloredCount = 0f;
        for (int i = 0; i < ACCENT_SAMPLES; ++i) {
            for (int j = 0; j < ACCENT_SAMPLES; ++j) {
                final int pixel = bitmap.getPixel((int) (i * stepX), (int) (j * stepY));
                if (Color.alpha(pixel) < 200 || pixel == background) {
                    continue;
                }
                ColorUtils.colorToHSL(pixel, hsl);
                if (isNeutral(hsl)) {
                    neutralCount++;
                    neutralLightness += hsl[2];
                    continue;
                }
                final int bucket = Math.min(HUE_BUCKETS - 1, (int) (hsl[0] / 360f * HUE_BUCKETS));
                final float chroma = hsl[1] * (1f - Math.abs(2f * hsl[2] - 1f));
                weights[bucket] += chroma;
                hues[bucket] += hsl[0] * chroma;
                saturations[bucket] += hsl[1] * chroma;
                lightnesses[bucket] += hsl[2] * chroma;
                coloredCount++;
            }
        }

        int best = -1;
        for (int i = 0; i < HUE_BUCKETS; ++i) {
            if (best == -1 || weights[i] > weights[best]) {
                best = i;
            }
        }
        if (best == -1 || weights[best] <= 0f || coloredCount < neutralCount * 0.15f) {
            if (neutralCount <= 0f) {
                return 0;
            }
            hsl[0] = 0f;
            hsl[1] = 0f;
            hsl[2] = neutralLightness / neutralCount;
            return ColorUtils.HSLToColor(hsl);
        }

        hsl[0] = hues[best] / weights[best];
        hsl[1] = saturations[best] / weights[best];
        hsl[2] = lightnesses[best] / weights[best];
        return ColorUtils.HSLToColor(hsl);
    }

    private static void draw(Canvas canvas, int resource, Rect bounds) {
        if (resource == 0) {
            return;
        }
        Context context = ApplicationLoader.applicationContext;
        Resources resources = context.getResources();

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        options.inScaled = false;
        BitmapFactory.decodeResource(resources, resource, options);

        if (options.outWidth > 0 && options.outHeight > 0) {
            int sampleSize = 1;
            while (options.outWidth / (sampleSize * 2) >= bounds.width()) {
                sampleSize *= 2;
            }
            options.inJustDecodeBounds = false;
            options.inSampleSize = sampleSize;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bitmap = BitmapFactory.decodeResource(resources, resource, options);
            if (bitmap != null) {
                canvas.drawBitmap(bitmap, null, bounds, paint);
                return;
            }
        }

        Drawable drawable = ContextCompat.getDrawable(context, resource);
        if (drawable instanceof BitmapDrawable && ((BitmapDrawable) drawable).getBitmap() != null) {
            canvas.drawBitmap(((BitmapDrawable) drawable).getBitmap(), null, bounds, paint);
        } else if (drawable != null) {
            drawable.setBounds(bounds);
            drawable.draw(canvas);
        }
    }
}
