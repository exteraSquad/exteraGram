/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.camera;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.util.Size;
import android.util.SizeF;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class CameraLensStops {

    private static final float EPSILON = 0.0001f;
    private static final float[] NO_RATIOS = new float[0];

    private static final float[] SNAP_RATIOS = {
            1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f, 8.0f, 10.0f, 12.0f, 15.0f, 20.0f
    };
    private static final float[] MARKETED_RATIOS = {2.0f, 3.0f, 5.0f, 10.0f};
    private static final float[] ROUND_RATIOS = {1.5f, 2.0f, 3.0f, 5.0f, 10.0f, 15.0f, 20.0f, 30.0f};
    private static final float[] RULER_LADDER = {1.0f, 2.0f, 5.0f, 10.0f, 30.0f};

    private static final float SNAP_TOLERANCE_BELOW = 0.03f;
    private static final float SNAP_TOLERANCE_ABOVE = 0.10f;
    private static final float MARKETED_TOLERANCE = 0.12f;
    private static final float[] GOOGLE_MARKETED_RATIOS = {5.0f};
    private static final float DEVICE_MARKETED_TOLERANCE = 0.20f;
    private static final float SAME_LENS_FOCAL_TOLERANCE = 0.01f;
    private static final float LENS_SWITCH_TOLERANCE = 0.01f;
    private static final float MAIN_LENS_TOLERANCE = 0.12f;
    private static final float RESCALE_TRIGGER = 1.25f;
    private static final float MIN_TELEPHOTO_RATIO = 1.15f;
    private static final float REACH_FACTOR = 2.0f;
    private static final double MIN_GAP_OCTAVES = 2.0;
    private static final double MIN_RULER_OCTAVES = 0.55;
    private static final int TARGET_TOGGLE_STOPS = 4;
    private static final int MAX_TOGGLE_STOPS = 5;

    private static final Map<String, LensSet> LENS_CACHE = new HashMap<>();

    public static final class LensSet {

        public static final LensSet EMPTY = new LensSet(NO_RATIOS, NO_RATIOS, new String[0][]);

        private final float[] ratios;
        private final float[] lensRatios;
        private final String[][] physicalCameraIds;

        private LensSet(float[] ratios, float[] lensRatios, String[][] physicalCameraIds) {
            this.ratios = ratios;
            this.lensRatios = lensRatios;
            this.physicalCameraIds = physicalCameraIds;
        }

        public float[] getRatios() {
            return Arrays.copyOf(ratios, ratios.length);
        }

        public boolean isExpectedLens(float zoom, String activePhysicalCameraId) {
            int active = activeLensIndex(activePhysicalCameraId);
            if (active < 0) {
                return true;
            }
            int expected = lensIndexAt(zoom * (1.0f + LENS_SWITCH_TOLERANCE));
            return active == expected || active == expected - 1 && zoom < ratios[expected];
        }

        float limitZoom(float zoom, int activeLens, int reach) {
            return lensIndexAt(zoom) < activeLens - reach ? lensRatios[activeLens - reach] : zoom;
        }

        int activeLensIndex(String activePhysicalCameraId) {
            if (activePhysicalCameraId == null || ratios.length < 2) {
                return -1;
            }
            for (int i = 0; i < physicalCameraIds.length; i++) {
                for (String physicalCameraId : physicalCameraIds[i]) {
                    if (physicalCameraId.equals(activePhysicalCameraId)) {
                        return i;
                    }
                }
            }
            return -1;
        }

        int lensIndexAt(float zoom) {
            int index = 0;
            for (int i = 1; i < lensRatios.length; i++) {
                if (lensRatios[i] <= zoom) {
                    index = i;
                }
            }
            return index;
        }
    }

    private CameraLensStops() {
    }

    public static LensSet lensSet(Context context, String logicalCameraId, float minZoom) {
        if (context == null || logicalCameraId == null
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return LensSet.EMPTY;
        }
        LensSet cached;
        synchronized (LENS_CACHE) {
            cached = LENS_CACHE.get(logicalCameraId);
        }
        if (cached == null) {
            cached = readLensSet(context, logicalCameraId);
            synchronized (LENS_CACHE) {
                LENS_CACHE.put(logicalCameraId, cached);
            }
        }
        return normalizeLensSet(cached, minZoom);
    }

    private static LensSet normalizeLensSet(LensSet raw, float minZoom) {
        float[] rawRatios = raw.ratios;
        if (rawRatios.length < 2) {
            return LensSet.EMPTY;
        }
        float scale = 1.0f;
        if (minZoom > 0.0f && rawRatios[0] > minZoom * RESCALE_TRIGGER) {
            scale = minZoom / rawRatios[0];
        }
        float mainLens = 0.0f;
        double mainLensDistance = Double.MAX_VALUE;
        for (float rawRatio : rawRatios) {
            double distance = Math.abs(octaves(1.0f, rawRatio * scale));
            if (distance < mainLensDistance) {
                mainLensDistance = distance;
                mainLens = rawRatio * scale;
            }
        }
        if (Math.abs(mainLens - 1.0f) > MAIN_LENS_TOLERANCE) {
            return LensSet.EMPTY;
        }
        float[] deviceRatios = deviceMarketedRatios();
        ArrayList<Float> ratios = new ArrayList<>(rawRatios.length);
        ArrayList<Float> lensRatios = new ArrayList<>(rawRatios.length);
        ArrayList<String[]> physicalCameraIds = new ArrayList<>(rawRatios.length);
        for (int i = 0; i < rawRatios.length; i++) {
            float lensRatio = rawRatios[i] * scale / mainLens;
            addLens(ratios, lensRatios, physicalCameraIds,
                    snapToNiceRatio(lensRatio, deviceRatios), lensRatio, raw.physicalCameraIds[i]);
        }
        return new LensSet(toArray(ratios), toArray(lensRatios),
                physicalCameraIds.toArray(new String[0][]));
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private static LensSet readLensSet(Context context, String logicalCameraId) {
        CameraManager manager;
        CameraCharacteristics logical;
        Set<String> physicalIds;
        float reference;
        try {
            manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) {
                return LensSet.EMPTY;
            }
            logical = manager.getCameraCharacteristics(logicalCameraId);
            physicalIds = logical.getPhysicalCameraIds();
            if (physicalIds == null || physicalIds.size() < 2) {
                return LensSet.EMPTY;
            }
            reference = halfFieldOfViewTangent(logical);
            if (reference <= 0.0f) {
                return LensSet.EMPTY;
            }
        } catch (Exception e) {
            FileLog.e(e);
            return LensSet.EMPTY;
        }
        ArrayList<Float> lensFocalLengths = new ArrayList<>(physicalIds.size());
        ArrayList<Float> lensTangents = new ArrayList<>(physicalIds.size());
        ArrayList<String[]> lensCameraIds = new ArrayList<>(physicalIds.size());
        for (String physicalId : physicalIds) {
            try {
                CameraCharacteristics physical = manager.getCameraCharacteristics(physicalId);
                float tangent = halfFieldOfViewTangent(physical);
                if (tangent <= 0.0f) {
                    continue;
                }
                float focalLength = physical.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)[0];
                int sameLens = -1;
                for (int i = 0; i < lensFocalLengths.size(); i++) {
                    if (Math.abs(lensFocalLengths.get(i) - focalLength) <= focalLength * SAME_LENS_FOCAL_TOLERANCE) {
                        sameLens = i;
                        break;
                    }
                }
                if (sameLens < 0) {
                    lensFocalLengths.add(focalLength);
                    lensTangents.add(tangent);
                    lensCameraIds.add(new String[]{physicalId});
                } else {
                    lensTangents.set(sameLens, Math.max(tangent, lensTangents.get(sameLens)));
                    lensCameraIds.set(sameLens, join(lensCameraIds.get(sameLens), new String[]{physicalId}));
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        ArrayList<Float> ratios = new ArrayList<>(lensTangents.size());
        ArrayList<Float> lensRatios = new ArrayList<>(lensTangents.size());
        ArrayList<String[]> physicalCameraIds = new ArrayList<>(lensTangents.size());
        for (int i = 0; i < lensTangents.size(); i++) {
            float ratio = reference / lensTangents.get(i);
            addLens(ratios, lensRatios, physicalCameraIds, ratio, ratio, lensCameraIds.get(i));
        }
        float[] rawRatios = toArray(ratios);
        return new LensSet(rawRatios, rawRatios, physicalCameraIds.toArray(new String[0][]));
    }

    private static void addLens(ArrayList<Float> ratios, ArrayList<Float> lensRatios,
                                ArrayList<String[]> physicalCameraIds,
                                float ratio, float lensRatio, String[] cameraIds) {
        if (ratio <= 0.0f || !Float.isFinite(ratio) || lensRatio <= 0.0f || !Float.isFinite(lensRatio)) {
            return;
        }
        int index = 0;
        while (index < ratios.size() && ratios.get(index) < ratio - EPSILON) {
            index++;
        }
        if (index < ratios.size() && Math.abs(ratios.get(index) - ratio) <= EPSILON) {
            physicalCameraIds.set(index, join(physicalCameraIds.get(index), cameraIds));
        } else {
            ratios.add(index, ratio);
            lensRatios.add(index, lensRatio);
            physicalCameraIds.add(index, cameraIds);
        }
    }

    private static String[] join(String[] first, String[] second) {
        String[] joined = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        return joined;
    }

    private static float halfFieldOfViewTangent(CameraCharacteristics characteristics) {
        float[] focalLengths = characteristics.get(
                CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        SizeF physicalSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        Rect activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        Size pixelArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
        Integer sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        if (focalLengths == null || focalLengths.length == 0 || physicalSize == null
                || activeArray == null || pixelArray == null || sensorOrientation == null) {
            return 0.0f;
        }
        float sensorLength;
        float activeLength;
        float pixelLength;
        if (sensorOrientation % 180 == 90) {
            sensorLength = physicalSize.getHeight();
            activeLength = activeArray.height();
            pixelLength = pixelArray.getHeight();
        } else {
            sensorLength = physicalSize.getWidth();
            activeLength = activeArray.width();
            pixelLength = pixelArray.getWidth();
        }
        float focalLength = focalLengths[0];
        if (focalLength <= 0.0f || sensorLength <= 0.0f
                || activeLength <= 0.0f || pixelLength <= 0.0f) {
            return 0.0f;
        }
        return sensorLength * activeLength / pixelLength / (2.0f * focalLength);
    }

    public static float[] buildToggleStops(boolean front, float min, float max, float[] opticalRatios) {
        float[] telephotoRatios = telephotoRatios(opticalRatios, max);
        if (telephotoRatios.length == 0) {
            return boundStops(
                    front ? new float[]{min, 1.0f, 2.0f} : new float[]{min, 1.0f, 2.0f, 5.0f},
                    min, max, false);
        }
        ArrayList<Float> stops = new ArrayList<>(MAX_TOGGLE_STOPS + 1);
        if (min < 1.0f - EPSILON) {
            addDistinctStop(stops, min);
        }
        addDistinctStop(stops, Utilities.clamp(1.0f, max, min));
        for (float ratio : telephotoRatios) {
            addDistinctStop(stops, ratio);
        }
        fillWideGaps(stops);
        addReachStop(stops, telephotoRatios[telephotoRatios.length - 1], max);
        dropCrowdedStops(stops);
        return toArray(stops);
    }

    public static float[] buildRulerStops(float min, float max, float[] toggleStops) {
        ArrayList<Float> stops = new ArrayList<>(toggleStops.length + RULER_LADDER.length + 2);
        addDistinctStop(stops, min);
        for (float stop : toggleStops) {
            addDistinctStop(stops, Utilities.clamp(stop, max, min));
        }
        addDistinctStop(stops, max);
        for (float candidate : RULER_LADDER) {
            if (candidate < min - EPSILON || candidate > max + EPSILON) {
                continue;
            }
            if (nearestOctaveDistance(stops, candidate) < MIN_RULER_OCTAVES) {
                continue;
            }
            addDistinctStop(stops, candidate);
        }
        return toArray(stops);
    }

    private static float[] telephotoRatios(float[] opticalRatios, float max) {
        if (opticalRatios == null || opticalRatios.length == 0) {
            return NO_RATIOS;
        }
        ArrayList<Float> ratios = new ArrayList<>(opticalRatios.length);
        for (float ratio : opticalRatios) {
            if (ratio >= MIN_TELEPHOTO_RATIO && ratio <= max + EPSILON) {
                addDistinctStop(ratios, ratio);
            }
        }
        return toArray(ratios);
    }

    private static void fillWideGaps(ArrayList<Float> stops) {
        while (stops.size() < TARGET_TOGGLE_STOPS) {
            int widestIndex = -1;
            double widestGap = MIN_GAP_OCTAVES;
            for (int i = 1; i < stops.size(); i++) {
                double gap = octaves(stops.get(i - 1), stops.get(i));
                if (gap > widestGap) {
                    widestGap = gap;
                    widestIndex = i;
                }
            }
            if (widestIndex < 0) {
                return;
            }
            float candidate = chooseRoundRatio(stops.get(widestIndex - 1), stops.get(widestIndex));
            if (candidate <= 0.0f) {
                return;
            }
            int size = stops.size();
            addDistinctStop(stops, candidate);
            if (stops.size() == size) {
                return;
            }
        }
    }

    private static float chooseRoundRatio(float lower, float upper) {
        double target = Math.sqrt((double) lower * upper);
        float best = 0.0f;
        double bestDistance = Double.MAX_VALUE;
        for (float candidate : ROUND_RATIOS) {
            if (candidate <= lower + EPSILON || candidate >= upper - EPSILON) {
                continue;
            }
            double distance = Math.abs(octaves(candidate, (float) target));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private static void addReachStop(ArrayList<Float> stops, float topTelephoto, float max) {
        if (stops.size() >= TARGET_TOGGLE_STOPS || stops.isEmpty()) {
            return;
        }
        float reach = snapToNiceRatio(topTelephoto * REACH_FACTOR, NO_RATIOS);
        if (reach > max + EPSILON || reach <= stops.get(stops.size() - 1) + EPSILON) {
            return;
        }
        addDistinctStop(stops, reach);
    }

    private static void dropCrowdedStops(ArrayList<Float> stops) {
        while (stops.size() > MAX_TOGGLE_STOPS) {
            int tightestIndex = -1;
            double tightestGap = Double.MAX_VALUE;
            for (int i = 1; i < stops.size() - 1; i++) {
                double gap = octaves(stops.get(i - 1), stops.get(i));
                if (gap < tightestGap) {
                    tightestGap = gap;
                    tightestIndex = i;
                }
            }
            if (tightestIndex < 0) {
                return;
            }
            stops.remove(tightestIndex);
        }
    }

    private static float[] deviceMarketedRatios() {
        return "Google".equalsIgnoreCase(Build.MANUFACTURER) ? GOOGLE_MARKETED_RATIOS : NO_RATIOS;
    }

    private static float snapToNiceRatio(float ratio, float[] deviceRatios) {
        if (!Float.isFinite(ratio) || ratio <= 0.0f) {
            return 0.0f;
        }
        float best = 0.0f;
        float bestDistance = Float.MAX_VALUE;
        for (float candidate : deviceRatios) {
            float distance = Math.abs(candidate - ratio) / ratio;
            if (distance <= DEVICE_MARKETED_TOLERANCE && distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        if (best > 0.0f) {
            return best;
        }
        for (float candidate : MARKETED_RATIOS) {
            float distance = Math.abs(candidate - ratio) / ratio;
            if (distance <= MARKETED_TOLERANCE && distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        if (best > 0.0f) {
            return best;
        }
        for (float candidate : SNAP_RATIOS) {
            float offset = (candidate - ratio) / ratio;
            if (offset < -SNAP_TOLERANCE_BELOW || offset > SNAP_TOLERANCE_ABOVE) {
                continue;
            }
            float distance = Math.abs(offset);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best > 0.0f ? best : Math.round(ratio * 10.0f) / 10.0f;
    }

    private static double nearestOctaveDistance(ArrayList<Float> stops, float value) {
        double nearest = Double.MAX_VALUE;
        for (int i = 0; i < stops.size(); i++) {
            nearest = Math.min(nearest, Math.abs(octaves(stops.get(i), value)));
        }
        return nearest;
    }

    private static double octaves(float from, float to) {
        if (from <= 0.0f || to <= 0.0f) {
            return 0.0;
        }
        return Math.log((double) to / from) / Math.log(2.0);
    }

    private static float[] boundStops(float[] source, float min, float max, boolean includeEndpoints) {
        ArrayList<Float> result = new ArrayList<>(source.length + 2);
        if (includeEndpoints) {
            addDistinctStop(result, min);
        }
        for (float value : source) {
            if (value <= 0.0f || !Float.isFinite(value)) {
                continue;
            }
            if (value < min - EPSILON) {
                if (!includeEndpoints) {
                    addDistinctStop(result, min);
                }
            } else if (value <= max + EPSILON) {
                addDistinctStop(result, Utilities.clamp(value, max, min));
            }
        }
        if (includeEndpoints) {
            addDistinctStop(result, max);
        }
        return toArray(result);
    }

    private static void addDistinctStop(ArrayList<Float> values, float value) {
        if (value <= 0.0f || !Float.isFinite(value)) {
            return;
        }
        for (int i = 0; i < values.size(); i++) {
            float existing = values.get(i);
            if (Math.abs(existing - value) <= EPSILON) {
                return;
            }
            if (existing > value) {
                values.add(i, value);
                return;
            }
        }
        values.add(value);
    }

    private static float[] toArray(ArrayList<Float> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = values.get(i);
        }
        return result;
    }
}