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
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.os.Build;
import android.util.Range;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.FileLog;

import java.util.Set;

public final class RoundVideoCamera2Helper {

    public static final long FOCUS_HOLD_MS = 5000L;
    private static final float FOCUS_REGION_FRACTION = 1f / 6f;
    private static final float WIDER_CAMERA_MARGIN = 0.05f;

    private RoundVideoCamera2Helper() {
    }

    @Nullable
    public static MeteringRectangle createFocusRegion(
            @NonNull CameraCharacteristics characteristics,
            @NonNull Size streamSize,
            int cropSize,
            int displayRotation,
            float zoomRatio,
            float x,
            float y
    ) {
        Rect activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (activeArray == null || cropSize <= 0 || streamSize.getWidth() <= 0 || streamSize.getHeight() <= 0) {
            return null;
        }
        Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
        Integer sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        boolean front = lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_FRONT;
        int orientation = sensorOrientation == null ? 0 : sensorOrientation;
        int rotation = front ? (orientation + displayRotation) % 360 : (orientation - displayRotation + 360) % 360;
        boolean swapped = rotation == 90 || rotation == 270;

        float orientedWidth = swapped ? streamSize.getHeight() : streamSize.getWidth();
        float orientedHeight = swapped ? streamSize.getWidth() : streamSize.getHeight();
        float crop = Math.min(cropSize, Math.min(orientedWidth, orientedHeight));
        float u = 0.5f + (clamp(x, 0f, 1f) - 0.5f) * crop / orientedWidth;
        float v = 0.5f + (clamp(y, 0f, 1f) - 0.5f) * crop / orientedHeight;
        if (front) {
            u = 1f - u;
        }
        float sensorX;
        float sensorY;
        switch (rotation) {
            case 90:
                sensorX = v;
                sensorY = 1f - u;
                break;
            case 180:
                sensorX = 1f - u;
                sensorY = 1f - v;
                break;
            case 270:
                sensorX = 1f - v;
                sensorY = u;
                break;
            default:
                sensorX = u;
                sensorY = v;
                break;
        }

        float baseWidth = activeArray.width();
        float baseHeight = activeArray.height();
        float baseLeft = 0f;
        float baseTop = 0f;
        if (!usesZoomRatio(characteristics) && zoomRatio > 1f) {
            baseWidth /= zoomRatio;
            baseHeight /= zoomRatio;
            baseLeft = (activeArray.width() - baseWidth) / 2f;
            baseTop = (activeArray.height() - baseHeight) / 2f;
        }
        float streamAspect = streamSize.getWidth() / (float) streamSize.getHeight();
        float fieldWidth = baseWidth;
        float fieldHeight = baseHeight;
        if (baseWidth / baseHeight > streamAspect) {
            fieldWidth = baseHeight * streamAspect;
        } else {
            fieldHeight = baseWidth / streamAspect;
        }
        float fieldLeft = baseLeft + (baseWidth - fieldWidth) / 2f;
        float fieldTop = baseTop + (baseHeight - fieldHeight) / 2f;

        int centerX = Math.round(fieldLeft + sensorX * fieldWidth);
        int centerY = Math.round(fieldTop + sensorY * fieldHeight);
        int halfSize = Math.max(1, Math.round(Math.min(fieldWidth, fieldHeight) * FOCUS_REGION_FRACTION / 2f));
        int maxX = activeArray.width() - 1;
        int maxY = activeArray.height() - 1;
        Rect region = new Rect(
                clamp(centerX - halfSize, 0, maxX),
                clamp(centerY - halfSize, 0, maxY),
                clamp(centerX + halfSize, 0, maxX),
                clamp(centerY + halfSize, 0, maxY)
        );
        if (region.isEmpty()) {
            return null;
        }
        return new MeteringRectangle(region, MeteringRectangle.METERING_WEIGHT_MAX - 1);
    }

    public static boolean applyFocusRegion(
            @NonNull CaptureRequest.Builder builder,
            @NonNull CameraCharacteristics characteristics,
            @NonNull MeteringRectangle region
    ) {
        MeteringRectangle[] regions = {region};
        Integer maxAeRegions = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE);
        if (maxAeRegions != null && maxAeRegions > 0) {
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, regions);
        }
        Integer maxAfRegions = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
        int[] afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
        if (maxAfRegions == null || maxAfRegions <= 0 || !contains(afModes, CaptureRequest.CONTROL_AF_MODE_AUTO)) {
            return false;
        }
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
        builder.set(CaptureRequest.CONTROL_AF_REGIONS, regions);
        return true;
    }

    public static void applyStabilization(
            @NonNull CaptureRequest.Builder builder,
            @NonNull CameraCharacteristics characteristics
    ) {
        if (!ExteraConfig.getCameraStabilization()) {
            return;
        }
        int[] modes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && contains(modes, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION)) {
            builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION);
        } else if (contains(modes, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)) {
            builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON);
        }
    }

    @Nullable
    public static float[] readZoomRange(@NonNull Context context, @Nullable String cameraId) {
        if (cameraId == null) {
            return null;
        }
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (manager == null) {
                return null;
            }
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            if (usesZoomRatio(characteristics)) {
                Range<Float> range = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                return new float[]{range.getLower(), range.getUpper()};
            }
            Float maxDigitalZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
            return new float[]{1f, maxDigitalZoom == null ? 1f : maxDigitalZoom};
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public static boolean isPreferredBackCamera(
            @NonNull CameraManager manager,
            @NonNull String candidateId,
            @NonNull CameraCharacteristics candidate,
            @NonNull String currentId,
            @NonNull CameraCharacteristics current,
            boolean keepConcurrentPair
    ) {
        if (keepConcurrentPair) {
            boolean candidatePaired = isInConcurrentPair(manager, candidateId);
            if (candidatePaired != isInConcurrentPair(manager, currentId)) {
                return candidatePaired;
            }
        }
        float candidateZoom = minZoomRatio(candidate);
        float currentZoom = minZoomRatio(current);
        if (Math.abs(candidateZoom - currentZoom) > WIDER_CAMERA_MARGIN) {
            return candidateZoom < currentZoom;
        }
        return Boolean.TRUE.equals(candidate.get(CameraCharacteristics.FLASH_INFO_AVAILABLE))
                && !Boolean.TRUE.equals(current.get(CameraCharacteristics.FLASH_INFO_AVAILABLE));
    }

    private static boolean isInConcurrentPair(@NonNull CameraManager manager, @NonNull String cameraId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false;
        }
        try {
            for (Set<String> cameraIds : manager.getConcurrentCameraIds()) {
                if (cameraIds.size() > 1 && cameraIds.contains(cameraId)) {
                    return true;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    private static float minZoomRatio(@NonNull CameraCharacteristics characteristics) {
        if (!usesZoomRatio(characteristics)) {
            return 1f;
        }
        return characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE).getLower();
    }

    public static float getInitialZoomRatio(@Nullable CameraCharacteristics characteristics) {
        if (characteristics == null || !ExteraConfig.getStartWithWideAngleCamera() || !usesZoomRatio(characteristics)) {
            return 1f;
        }
        Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
        if (facing == null || facing != CameraCharacteristics.LENS_FACING_BACK) {
            return 1f;
        }
        Range<Float> range = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
        return Math.min(1f, range.getLower());
    }

    public static void applyInitialZoom(@NonNull CaptureRequest.Builder builder, @NonNull CameraCharacteristics characteristics) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && usesZoomRatio(characteristics)) {
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, getInitialZoomRatio(characteristics));
        }
    }

    private static boolean usesZoomRatio(@NonNull CameraCharacteristics characteristics) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false;
        }
        Range<Float> range = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
        return range != null;
    }

    private static boolean contains(@Nullable int[] values, int target) {
        if (values == null) {
            return false;
        }
        for (int value : values) {
            if (value == target) {
                return true;
            }
        }
        return false;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
