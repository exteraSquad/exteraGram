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
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.Range;

import androidx.annotation.OptIn;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.camera.Camera2Session;
import org.telegram.messenger.camera.CameraController;
import org.telegram.messenger.camera.CameraSession;
import org.telegram.messenger.camera.Size;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CameraDebugUtils {

    private CameraDebugUtils() {
    }

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    public static String getCameraXAvailableCameraList(CameraXSession session) {
        ProcessCameraProvider provider = session.provider;
        if (provider == null) {
            return "provider=null";
        }
        StringBuilder result = new StringBuilder();
        try {
            if (session.isDualMode()) {
                List<List<CameraInfo>> concurrentCameras = provider.getAvailableConcurrentCameraInfos();
                int pairIndex = 0;
                for (List<CameraInfo> pair : concurrentCameras) {
                    boolean hasFront = false;
                    boolean hasBack = false;
                    StringBuilder pairText = new StringBuilder();
                    for (CameraInfo info : pair) {
                        Integer lensFacing = info.getLensFacing();
                        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                            hasFront = true;
                        } else if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            hasBack = true;
                        }
                        if (pairText.length() > 0) {
                            pairText.append(" + ");
                        }
                        pairText.append(formatCameraXInfo(info));
                    }
                    if (!hasFront || !hasBack) {
                        continue;
                    }
                    if (result.length() > 0) {
                        result.append('\n');
                    }
                    result.append("pair").append(pairIndex++).append(": ").append(pairText);
                }
            } else {
                for (CameraInfo info : provider.getAvailableCameraInfos()) {
                    if (result.length() > 0) {
                        result.append(", ");
                    }
                    result.append(formatCameraXInfo(info));
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
            return "error=" + e.getClass().getSimpleName();
        }
        return result.length() == 0 ? "none" : result.toString();
    }

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    public static String getCameraXBoundCameraList(CameraXSession session) {
        StringBuilder result = new StringBuilder();
        Camera cameraFront = session.cameraFront;
        Camera cameraBack = session.cameraBack;
        Camera camera = session.camera;
        if (cameraFront != null) {
            result.append("front=").append(formatCameraXInfo(cameraFront.getCameraInfo()));
        }
        if (cameraBack != null) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append("back=").append(formatCameraXInfo(cameraBack.getCameraInfo()));
        }
        if (result.length() == 0 && camera != null) {
            result.append("active=").append(formatCameraXInfo(camera.getCameraInfo()));
        }
        return result.length() == 0 ? "none" : result.toString();
    }

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    public static String getCameraXPhysicalCameraList(CameraXSession session) {
        Camera camera = session.camera;
        if (camera == null) {
            return "none";
        }
        try {
            StringBuilder result = new StringBuilder();
            for (CameraInfo info : camera.getCameraInfo().getPhysicalCameraInfos()) {
                if (result.length() > 0) {
                    result.append(", ");
                }
                result.append(formatCameraXInfo(info));
            }
            return result.length() == 0 ? "none" : result.toString();
        } catch (Exception e) {
            FileLog.e(e);
            return "error=" + e.getClass().getSimpleName();
        }
    }

    public static String getCamera2CameraList(Context context) {
        try {
            CameraManager cameraManager = context.getSystemService(CameraManager.class);
            if (cameraManager == null) {
                return "manager=null";
            }
            StringBuilder text = new StringBuilder();
            for (String cameraId : cameraManager.getCameraIdList()) {
                if (text.length() > 0) {
                    text.append(", ");
                }
                Integer facing = cameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING);
                text.append(lensFacingToShortString(facing)).append(cameraId);
            }
            return text.length() == 0 ? "none" : text.toString();
        } catch (Exception e) {
            FileLog.e(e);
            return "error=" + e.getClass().getSimpleName();
        }
    }

    public static String getLegacyCameraList() {
        ArrayList<org.telegram.messenger.camera.CameraInfo> cameras = CameraController.getInstance().getCameras();
        if (cameras == null || cameras.isEmpty()) {
            return "none";
        }
        StringBuilder text = new StringBuilder();
        for (org.telegram.messenger.camera.CameraInfo info : cameras) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(info.isFrontface() ? 'f' : 'b').append(info.getCameraId());
        }
        return text.toString();
    }

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    public static String getCameraXSupportedFpsRanges(CameraXSession session) {
        if (session == null) {
            return "none";
        }
        try {
            CameraInfo info = null;
            if (session.camera != null) {
                info = session.camera.getCameraInfo();
            } else if (session.isDualMode()) {
                Camera activeCamera = session.isFrontface() ? session.cameraFront : session.cameraBack;
                if (activeCamera != null) {
                    info = activeCamera.getCameraInfo();
                }
            } else if (session.provider != null) {
                CameraSelector selector = session.isFrontface() ? CameraSelector.DEFAULT_FRONT_CAMERA : session.widestBackCameraSelector();
                if (session.provider.hasCamera(selector)) {
                    info = session.provider.getCameraInfo(selector);
                }
            }
            if (info == null) {
                return "none";
            }
            Range<Integer>[] ranges = Camera2CameraInfo.from(info)
                    .getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            return formatCamera2FpsRanges(ranges);
        } catch (Exception e) {
            FileLog.e(e);
            return "error=" + e.getClass().getSimpleName();
        }
    }

    public static String getCamera2SupportedFpsRanges(Context context, String cameraId) {
        if (cameraId == null) {
            return "none";
        }
        try {
            CameraManager cameraManager = context.getSystemService(CameraManager.class);
            if (cameraManager == null) {
                return "manager=null";
            }
            return formatCamera2FpsRanges(cameraManager.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES));
        } catch (Exception e) {
            FileLog.e(e);
            return "error=" + e.getClass().getSimpleName();
        }
    }

    public static String getCamera2SupportedFpsRanges(Camera2Session session) {
        if (session == null) {
            return "none";
        }
        try {
            return formatCamera2FpsRanges(session.getAvailableFpsRanges());
        } catch (Exception e) {
            FileLog.e(e);
            return "error=" + e.getClass().getSimpleName();
        }
    }

    @SuppressWarnings("deprecation")
    public static String getLegacySupportedFpsRanges(CameraSession session) {
        if (session == null || session.cameraInfo == null || session.cameraInfo.getCamera() == null) {
            return "none";
        }
        try {
            List<int[]> ranges = session.cameraInfo.getCamera().getParameters().getSupportedPreviewFpsRange();
            return formatLegacyFpsRanges(ranges);
        } catch (Exception e) {
            FileLog.e(e);
            return "error=" + e.getClass().getSimpleName();
        }
    }

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    public static String formatCameraXInfo(CameraInfo info) {
        String cameraId;
        try {
            cameraId = Camera2CameraInfo.from(info).getCameraId();
        } catch (Exception e) {
            FileLog.e(e);
            cameraId = "?";
        }
        float intrinsicZoomRatio = 1.0f;
        try {
            intrinsicZoomRatio = info.getIntrinsicZoomRatio();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return lensFacingToShortString(info.getLensFacing()) + cameraId + "@" + intrinsicZoomRatio;
    }

    public static String formatZoomStops(float[] stops) {
        if (stops == null || stops.length == 0) {
            return "none";
        }
        StringBuilder text = new StringBuilder();
        for (float stop : stops) {
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(formatZoomStop(stop));
        }
        return text.toString();
    }

    private static String formatZoomStop(float stop) {
        String text = String.format(Locale.US, "%.3f", stop);
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && text.charAt(end - 1) == '.') {
            end--;
        }
        return text.substring(0, end);
    }

    public static char lensFacingToShortString(Integer lensFacing) {
        if (lensFacing == null) {
            return '?';
        }
        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            return 'f';
        }
        if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            return 'b';
        }
        return '?';
    }

    public static String formatCameraSize(Size size) {
        if (size == null) {
            return "n/a";
        }
        return size.getWidth() + "x" + size.getHeight();
    }

    public static float safeCameraXZoomRatio(CameraXSession cameraXSession) {
        try {
            return cameraXSession != null ? cameraXSession.getZoomRatio() : 1.0f;
        } catch (Exception e) {
            FileLog.e(e);
            return -1.0f;
        }
    }

    public static float safeCameraXMinZoomRatio(CameraXSession cameraXSession) {
        try {
            return cameraXSession != null ? cameraXSession.getMinZoomRatio() : 1.0f;
        } catch (Exception e) {
            FileLog.e(e);
            return -1.0f;
        }
    }

    public static float safeCameraXMaxZoomRatio(CameraXSession cameraXSession) {
        try {
            return cameraXSession != null ? cameraXSession.getMaxZoomRatio() : 1.0f;
        } catch (Exception e) {
            FileLog.e(e);
            return -1.0f;
        }
    }

    private static String formatCamera2FpsRanges(Range<Integer>[] ranges) {
        if (ranges == null || ranges.length == 0) {
            return "none";
        }
        StringBuilder text = new StringBuilder();
        for (Range<Integer> range : ranges) {
            if (range == null) {
                continue;
            }
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(formatFpsRange(range.getLower(), range.getUpper(), false));
        }
        return text.length() == 0 ? "none" : text.toString();
    }

    private static String formatLegacyFpsRanges(List<int[]> ranges) {
        if (ranges == null || ranges.isEmpty()) {
            return "none";
        }
        StringBuilder text = new StringBuilder();
        for (int[] range : ranges) {
            if (range == null || range.length < 2) {
                continue;
            }
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(formatFpsRange(range[0], range[1], true));
        }
        return text.length() == 0 ? "none" : text.toString();
    }

    private static String formatFpsRange(int lower, int upper, boolean scaleByThousand) {
        return formatFpsValue(lower, scaleByThousand) + "-" + formatFpsValue(upper, scaleByThousand);
    }

    private static String formatFpsValue(int value, boolean scaleByThousand) {
        float fps = scaleByThousand ? value / 1000.0f : value;
        if (Math.abs(fps - Math.round(fps)) < 0.05f) {
            return Integer.toString(Math.round(fps));
        }
        return String.format(Locale.US, "%.1f", fps);
    }
}
