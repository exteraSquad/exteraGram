/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.camera;

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Build;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CameraActiveLens {

    private static final int RESULT_INTERVAL = 4;

    private static final Map<String, String> ACTIVE_PHYSICAL_CAMERA_IDS = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Runnable NOTIFY_LISTENERS = () -> {
        for (Runnable listener : LISTENERS) {
            listener.run();
        }
    };

    public static final CameraCaptureSession.CaptureCallback CAPTURE_CALLBACK = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            onCaptureResult(session.getDevice().getId(), result);
        }
    };

    private CameraActiveLens() {
    }

    public static void onCaptureResult(String logicalCameraId, TotalCaptureResult result) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || logicalCameraId == null
                || result.getFrameNumber() % RESULT_INTERVAL != 0) {
            return;
        }
        String physicalCameraId = result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID);
        if (physicalCameraId == null) {
            return;
        }
        String previous = ACTIVE_PHYSICAL_CAMERA_IDS.put(logicalCameraId, physicalCameraId);
        if (!physicalCameraId.equals(previous) && !LISTENERS.isEmpty()) {
            AndroidUtilities.runOnUIThread(NOTIFY_LISTENERS);
        }
    }

    public static String get(String logicalCameraId) {
        return logicalCameraId == null ? null : ACTIVE_PHYSICAL_CAMERA_IDS.get(logicalCameraId);
    }

    public static void addListener(Runnable listener) {
        LISTENERS.addIfAbsent(listener);
    }

    public static void removeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }
}
