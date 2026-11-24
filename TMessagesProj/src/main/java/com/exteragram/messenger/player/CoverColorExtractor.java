/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player;

import android.graphics.Bitmap;
import android.os.Build;
import android.text.TextUtils;
import android.util.LruCache;

import androidx.core.graphics.ColorUtils;
import androidx.palette.graphics.Palette;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public final class CoverColorExtractor {

    public static final int NO_SEED = 0;

    private static final int SAMPLE_SIZE = 112;
    private static final int MAX_COLORS = 24;
    private static final double MIN_CHROMA = 8.0;
    private static final double TARGET_CHROMA = 48.0;
    private static final float MIN_PROPORTION = 0.01f;
    private static final double HUE_NEIGHBORHOOD = 15.0;
    private static final float UPGRADE_RATIO = 1.5f;
    private static final int GLOW_COUNT = 4;
    private static final double GLOW_HUE_DISTANCE = 22.0;
    private static final double GLOW_TONE_DISTANCE = 22.0;
    private static final double GLOW_NEUTRAL_CHROMA = 12.0;
    private static final double GLOW_FILL_HUE_STEP = 18.0;
    private static final double GLOW_FILL_TONE_STEP = 12.0;

    private static final LruCache<String, Integer> cache = new LruCache<>(96);
    private static final LruCache<String, int[]> glowCache = new LruCache<>(96);
    private static final LruCache<String, Integer> sourceSizes = new LruCache<>(96);
    private static final LruCache<String, String> sourceKeys = new LruCache<>(96);
    private static final HashMap<String, ArrayList<Utilities.Callback<Integer>>> pending = new HashMap<>();
    private static final HashMap<String, Integer> pendingSizes = new HashMap<>();
    private static final HashMap<String, String> pendingKeys = new HashMap<>();

    private CoverColorExtractor() {
    }

    public static String getKey(MessageObject messageObject) {
        if (messageObject == null) {
            return null;
        }
        final TLRPC.Document document = messageObject.getDocument();
        if (document != null && document.id != 0) {
            return "d" + document.id;
        }
        return "m" + messageObject.getDialogId() + "_" + messageObject.getId();
    }

    public static Integer getCachedSeed(MessageObject messageObject) {
        final String key = getKey(messageObject);
        return key == null ? null : cache.get(key);
    }

    public static int[] getCachedGlow(MessageObject messageObject) {
        final String key = getKey(messageObject);
        return key == null ? null : glowCache.get(key);
    }

    public static void extract(MessageObject messageObject, ImageReceiver receiver, Utilities.Callback<Integer> callback) {
        final String key = getKey(messageObject);
        final ImageReceiver.BitmapHolder holder = key != null ? receiver.getBitmapSafe() : null;
        if (holder == null) {
            return;
        }
        if (holder.isRecycled()) {
            holder.release();
            return;
        }
        final int sourceSize = Math.max(holder.getWidth(), holder.getHeight());
        final String holderKey = holder.getKey();
        final int filterStart = holderKey != null ? holderKey.indexOf('@') : -1;
        final String sourceKey = filterStart >= 0 ? holderKey.substring(0, filterStart) : holderKey;
        final Integer cached = cache.get(key);
        final Integer cachedSize = sourceSizes.get(key);
        ArrayList<Utilities.Callback<Integer>> callbacks = pending.get(key);
        final Integer pendingSize = pendingSizes.get(key);
        if (callbacks == null && cached != null && (cachedSize == null || !isUpgrade(sourceSize, sourceKey, cachedSize, sourceKeys.get(key)))) {
            holder.release();
            if (callback != null) {
                callback.run(cached);
            }
            return;
        }
        if (callbacks != null && pendingSize != null && !isUpgrade(sourceSize, sourceKey, pendingSize, pendingKeys.get(key))) {
            holder.release();
            if (callback != null) {
                callbacks.add(callback);
            }
            return;
        }
        if (callbacks == null) {
            callbacks = new ArrayList<>();
            pending.put(key, callbacks);
        }
        if (callback != null) {
            callbacks.add(callback);
        }
        pendingSizes.put(key, sourceSize);
        pendingKeys.put(key, sourceKey);
        Utilities.globalQueue.postRunnable(() -> {
            int seed = NO_SEED;
            int[] glow = null;
            Bitmap sample = null;
            try {
                sample = createSample(holder.bitmap);
                final List<Palette.Swatch> swatches = new Palette.Builder(sample)
                        .maximumColorCount(MAX_COLORS)
                        .clearFilters()
                        .generate()
                        .getSwatches();
                seed = computeSeed(swatches);
                glow = seed == NO_SEED ? null : computeGlow(swatches, seed);
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                if (sample != null) {
                    sample.recycle();
                }
            }
            final int result = seed;
            final int[] glowResult = glow;
            AndroidUtilities.runOnUIThread(() -> {
                holder.release();
                final Integer bestSize = sourceSizes.get(key);
                if (bestSize == null || sourceSize >= bestSize) {
                    cache.put(key, result);
                    sourceSizes.put(key, sourceSize);
                    if (sourceKey != null) {
                        sourceKeys.put(key, sourceKey);
                    } else {
                        sourceKeys.remove(key);
                    }
                    if (glowResult != null) {
                        glowCache.put(key, glowResult);
                    } else {
                        glowCache.remove(key);
                    }
                }
                final Integer latest = pendingSizes.get(key);
                if (latest == null || latest != sourceSize || !TextUtils.equals(pendingKeys.get(key), sourceKey)) {
                    return;
                }
                pendingSizes.remove(key);
                pendingKeys.remove(key);
                final ArrayList<Utilities.Callback<Integer>> list = pending.remove(key);
                final Integer value = cache.get(key);
                if (list != null && value != null) {
                    for (Utilities.Callback<Integer> c : list) {
                        c.run(value);
                    }
                }
            });
        });
    }

    private static boolean isUpgrade(int size, String sourceKey, int previousSize, String previousSourceKey) {
        return size > previousSize * UPGRADE_RATIO || size >= previousSize && sourceKey != null && !sourceKey.equals(previousSourceKey);
    }

    private static Bitmap createSample(Bitmap source) {
        Bitmap bitmap = source;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.getConfig() == Bitmap.Config.HARDWARE) {
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
        }
        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        final float scale = Math.min(1f, SAMPLE_SIZE / (float) Math.max(width, height));
        final Bitmap sample = Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)), true);
        if (sample == source) {
            return source.copy(Bitmap.Config.ARGB_8888, false);
        }
        if (bitmap != source && bitmap != sample) {
            bitmap.recycle();
        }
        return sample;
    }

    private static int computeSeed(List<Palette.Swatch> swatches) {
        if (swatches.isEmpty()) {
            return NO_SEED;
        }
        final int count = swatches.size();
        final double[] hues = new double[count];
        final double[] chromas = new double[count];
        final float[] proportions = new float[count];
        final double[] lab = new double[3];
        long total = 0;
        for (Palette.Swatch swatch : swatches) {
            total += swatch.getPopulation();
        }
        if (total <= 0) {
            return NO_SEED;
        }
        for (int i = 0; i < count; i++) {
            final Palette.Swatch swatch = swatches.get(i);
            ColorUtils.colorToLAB(swatch.getRgb(), lab);
            chromas[i] = Math.hypot(lab[1], lab[2]);
            hues[i] = Math.toDegrees(Math.atan2(lab[2], lab[1]));
            proportions[i] = swatch.getPopulation() / (float) total;
        }
        int best = -1;
        int dominant = 0;
        double bestScore = -Double.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            if (proportions[i] > proportions[dominant]) {
                dominant = i;
            }
            if (chromas[i] < MIN_CHROMA || proportions[i] < MIN_PROPORTION) {
                continue;
            }
            float neighborhood = 0;
            for (int j = 0; j < count; j++) {
                if (chromas[j] >= MIN_CHROMA && hueDistance(hues[i], hues[j]) <= HUE_NEIGHBORHOOD) {
                    neighborhood += proportions[j];
                }
            }
            final double chromaScore = (chromas[i] - TARGET_CHROMA) * (chromas[i] < TARGET_CHROMA ? 0.1 : 0.3);
            final double score = neighborhood * 70.0 + chromaScore;
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return swatches.get(best >= 0 ? best : dominant).getRgb() | 0xFF000000;
    }

    private static int[] computeGlow(List<Palette.Swatch> swatches, int seed) {
        long total = 0;
        for (Palette.Swatch swatch : swatches) {
            total += swatch.getPopulation();
        }
        final int count = swatches.size();
        final Integer[] order = new Integer[count];
        final double[] scores = new double[count];
        final double[] lab = new double[3];
        for (int i = 0; i < count; i++) {
            order[i] = i;
            ColorUtils.colorToLAB(swatches.get(i).getRgb(), lab);
            final double proportion = total > 0 ? swatches.get(i).getPopulation() / (double) total : 0;
            scores[i] = proportion * (0.2 + Math.hypot(lab[1], lab[2]) / 100.0 * 1.6);
        }
        Arrays.sort(order, (a, b) -> Double.compare(scores[b], scores[a]));
        final double[][] picked = new double[GLOW_COUNT][];
        final int[] colors = new int[GLOW_COUNT];
        int size = 0;
        picked[size] = toLab(seed);
        colors[size++] = seed | 0xFF000000;
        for (int i = 0; i < count && size < GLOW_COUNT; i++) {
            final Palette.Swatch swatch = swatches.get(order[i]);
            if (total > 0 && swatch.getPopulation() / (double) total < MIN_PROPORTION) {
                continue;
            }
            final double[] candidate = toLab(swatch.getRgb());
            boolean distinct = true;
            for (int j = 0; j < size && distinct; j++) {
                distinct = isDistinct(candidate, picked[j]);
            }
            if (distinct) {
                picked[size] = candidate;
                colors[size++] = swatch.getRgb() | 0xFF000000;
            }
        }
        for (int i = size; i < GLOW_COUNT; i++) {
            final double[] base = picked[i % size];
            final double chroma = Math.hypot(base[1], base[2]);
            final double hue = Math.atan2(base[2], base[1]) + Math.toRadians(GLOW_FILL_HUE_STEP * i);
            final double tone = Math.max(0, Math.min(100, base[0] + (i % 2 == 1 ? GLOW_FILL_TONE_STEP : -GLOW_FILL_TONE_STEP)));
            colors[i] = ColorUtils.LABToColor(tone, chroma * Math.cos(hue), chroma * Math.sin(hue)) | 0xFF000000;
        }
        return colors;
    }

    private static double[] toLab(int color) {
        final double[] lab = new double[3];
        ColorUtils.colorToLAB(color, lab);
        return lab;
    }

    private static boolean isDistinct(double[] a, double[] b) {
        final double chromaA = Math.hypot(a[1], a[2]);
        final double chromaB = Math.hypot(b[1], b[2]);
        if (chromaA < GLOW_NEUTRAL_CHROMA != chromaB < GLOW_NEUTRAL_CHROMA || Math.abs(a[0] - b[0]) > GLOW_TONE_DISTANCE) {
            return true;
        }
        return hueDistance(Math.toDegrees(Math.atan2(a[2], a[1])), Math.toDegrees(Math.atan2(b[2], b[1]))) > GLOW_HUE_DISTANCE;
    }

    private static double hueDistance(double a, double b) {
        final double d = Math.abs(a - b) % 360.0;
        return d > 180.0 ? 360.0 - d : d;
    }
}
