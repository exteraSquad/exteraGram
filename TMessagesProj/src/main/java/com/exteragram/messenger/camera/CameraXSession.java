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
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Looper;
import android.util.Range;
import android.util.Size;
import android.view.Display;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraState;
import androidx.camera.core.ConcurrentCamera;
import androidx.camera.core.DisplayOrientedMeteringPointFactory;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.MirrorMode;
import androidx.camera.core.Preview;
import androidx.camera.core.SessionConfig;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ZoomState;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.utils.system.SystemUtils;
import com.google.common.util.concurrent.ListenableFuture;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Stories.recorder.DualCameraView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CameraXSession {

    private static final Map<CameraSelector, Boolean> STABILIZATION_SUPPORT_CACHE = new ConcurrentHashMap<>();
    private static final Range<Integer> FPS_60_RANGE = new Range<>(60, 60);
    private static final Range<Integer> FPS_30_RANGE = new Range<>(30, 30);
    private static final long MAX_60_FPS_FRAME_DURATION_NS = 16_666_667L;
    private static final int PREFERRED_PREVIEW_SHORT_EDGE = 1024;
    private static final int MAX_PREFERRED_PREVIEW_SHORT_EDGE = 1440;
    private static final int MAX_PREVIEW_LONG_EDGE = 1920;
    private static final int MAX_PREVIEW_AREA = 2_100_000;
    private static final double FIELD_OF_VIEW_WEIGHT = 2.0;
    private static final double RESOLUTION_WEIGHT = 1.0;
    private static final double CROP_WASTE_WEIGHT = 0.5;
    private static final Size FALLBACK_SENSOR_ASPECT = new Size(4, 3);
    private static final float WIDER_CAMERA_MARGIN = 0.05f;
    private static volatile Boolean seamlessSwitchingAvailableCache;
    private static volatile CameraSelector widestBackCameraSelectorCache;

    public static final String ENCODER_FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision highp float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform float alpha;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "void main() {\n" +
            "   vec4 color = texture2D(sTexture, vTextureCoord);\n" +
            "   gl_FragColor = vec4(color.rgb * alpha, alpha);\n" +
            "}\n";

    private boolean isFrontface;
    private boolean isInitiated = false;
    private boolean isDualMode = false;
    private boolean isBinding = false;
    private volatile int recordingFrameRate = 30;

    private final CameraLifecycle lifecycle;
    ProcessCameraProvider provider;

    Camera camera;
    private CameraControl cameraControl;

    Camera cameraFront;
    Camera cameraBack;
    private CameraControl cameraControlFront;
    private CameraControl cameraControlBack;

    private final Preview.SurfaceProvider surfaceProviderPrimary;
    private Preview.SurfaceProvider surfaceProviderSecondary;

    private Preview previewUseCase;
    private Preview previewUseCaseBack;

    private CameraSelector cameraSelector;
    private CameraSelector concurrentFrontSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
    private CameraSelector concurrentBackSelector = CameraSelector.DEFAULT_BACK_CAMERA;

    public static class CameraLifecycle implements LifecycleOwner {
        private final LifecycleRegistry lifecycleRegistry;

        public CameraLifecycle() {
            lifecycleRegistry = new LifecycleRegistry(this);
            lifecycleRegistry.setCurrentState(Lifecycle.State.CREATED);
        }

        public void start() {
            try {
                if (lifecycleRegistry.getCurrentState() != Lifecycle.State.DESTROYED) {
                    lifecycleRegistry.setCurrentState(Lifecycle.State.RESUMED);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        public void stop() {
            try {
                lifecycleRegistry.setCurrentState(Lifecycle.State.DESTROYED);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @NonNull
        @Override
        public Lifecycle getLifecycle() {
            return lifecycleRegistry;
        }
    }

    public CameraXSession(CameraLifecycle lifecycle, Preview.SurfaceProvider surfaceProvider) {
        this.lifecycle = lifecycle;
        this.surfaceProviderPrimary = surfaceProvider;
    }

    public interface PreviewSizeListener {
        void onPreviewSize(int width, int height);
    }

    public static Preview.SurfaceProvider createSurfaceProvider(Context context, SurfaceTexture surfaceTexture, PreviewSizeListener listener) {
        return request -> {
            try {
                Size resolution = request.getResolution();
                listener.onPreviewSize(resolution.getWidth(), resolution.getHeight());
                request.setTransformationInfoListener(ContextCompat.getMainExecutor(context), transformationInfo -> {
                    Rect cropRect = transformationInfo.getCropRect();
                    int width = cropRect.width() > 0 ? cropRect.width() : resolution.getWidth();
                    int height = cropRect.height() > 0 ? cropRect.height() : resolution.getHeight();
                    listener.onPreviewSize(width, height);
                });
                surfaceTexture.setDefaultBufferSize(resolution.getWidth(), resolution.getHeight());
                Surface surface = new Surface(surfaceTexture);
                request.provideSurface(surface, ContextCompat.getMainExecutor(context), result -> {
                    request.clearTransformationInfoListener();
                    surface.release();
                });
            } catch (Exception e) {
                FileLog.e(e);
                request.willNotProvideSurface();
            }
        };
    }

    public void setSecondSurfaceProvider(Preview.SurfaceProvider provider) {
        this.surfaceProviderSecondary = provider;
        if (isInitiated && isDualMode && previewUseCaseBack != null) {
            previewUseCaseBack.setSurfaceProvider(surfaceProviderSecondary);
        }
    }

    public boolean isInitiated() {
        return isInitiated;
    }

    public boolean isReady() {
        return isInitiated && !isBinding && camera != null;
    }

    public boolean isDualMode() {
        return isDualMode;
    }

    public boolean isFrontface() {
        return isFrontface;
    }

    public boolean isActiveCameraFrontface() {
        if (camera == null) {
            return isFrontface;
        }
        int lensFacing = camera.getCameraInfo().getLensFacing();
        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            return true;
        }
        if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            return false;
        }
        return isFrontface;
    }

    public int getRecordingFrameRate() {
        return recordingFrameRate;
    }

    public void initCamera(Context context, boolean isInitialFrontface, boolean isDualModeEnabled, Runnable onPreInit) {
        this.isFrontface = isInitialFrontface;
        ListenableFuture<ProcessCameraProvider> providerFtr = ProcessCameraProvider.getInstance(context);
        providerFtr.addListener(() -> {
            try {
                provider = providerFtr.get();

                if (lifecycle.getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) {
                    return;
                }

                isDualMode = isDualModeEnabled && supportsConcurrentFrontBackPair();
                rebindCamera();
                lifecycle.start();
                if (onPreInit != null) {
                    onPreInit.run();
                }
            } catch (Exception e) {
                FileLog.e(e);
                isInitiated = false;
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private boolean supportsConcurrentFrontBackPair() {
        if (provider == null) {
            return false;
        }
        List<List<CameraInfo>> concurrentCameras = provider.getAvailableConcurrentCameraInfos();
        for (List<CameraInfo> pair : concurrentCameras) {
            boolean hasFront = false;
            boolean hasBack = false;
            for (CameraInfo info : pair) {
                if (info.getLensFacing() == CameraSelector.LENS_FACING_FRONT) hasFront = true;
                if (info.getLensFacing() == CameraSelector.LENS_FACING_BACK) hasBack = true;
            }
            if (hasFront && hasBack) {
                return true;
            }
        }
        return false;
    }

    public void switchCamera() {
        isFrontface ^= true;
        if (isDualMode && cameraFront != null && cameraBack != null) {
            updateActiveControl(isFrontface);
            updateTorchState();
        } else {
            rebindCamera();
        }
    }

    public void resetInactiveCameraZoom() {
        if (!isDualMode) return;
        if (isFrontface) {
            applyInitialZoom(cameraBack, cameraControlBack);
        } else {
            applyInitialZoom(cameraFront, cameraControlFront);
        }
    }

    private void updateActiveControl(boolean front) {
        if (front) {
            camera = cameraFront;
            cameraControl = cameraControlFront;
        } else {
            camera = cameraBack;
            cameraControl = cameraControlBack;
        }
    }

    public void closeCamera() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            AndroidUtilities.runOnUIThread(this::closeCamera);
            return;
        }
        try {
            if (provider != null) {
                provider.unbindAll();
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            try {
                if (previewUseCase != null) {
                    previewUseCase.setSurfaceProvider(null);
                }
                if (previewUseCaseBack != null) {
                    previewUseCaseBack.setSurfaceProvider(null);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            lifecycle.stop();
            isInitiated = false;
            clearCameraReferences();
        }
    }

    private void clearCameraReferences() {
        camera = cameraFront = cameraBack = null;
        cameraControl = cameraControlFront = cameraControlBack = null;
        previewUseCase = previewUseCaseBack = null;
    }

    public boolean isFlashAvailable() {
        if (camera == null) return false;
        return camera.getCameraInfo().hasFlashUnit();
    }

    private boolean isTorchOn = false;

    public void setTorchEnabled(boolean enabled) {
        this.isTorchOn = enabled;
        updateTorchState();
    }

    private void updateTorchState() {
        try {
            if (isDualMode) {
                if (cameraControlFront != null) {
                    cameraControlFront.enableTorch(false);
                }
                if (cameraControlBack != null && cameraBack != null && cameraBack.getCameraInfo().hasFlashUnit()) {
                    cameraControlBack.enableTorch(isTorchOn && !isFrontface);
                }
            } else if (camera != null && cameraControl != null && camera.getCameraInfo().hasFlashUnit()) {
                boolean isBackCamera = camera.getCameraInfo().getLensFacing() == CameraSelector.LENS_FACING_BACK;
                cameraControl.enableTorch(isTorchOn && isBackCamera);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    private Preview buildPreview(CameraSelector selector, boolean enablePreviewStabilization,
                                 Range<Integer> targetFrameRateRange, boolean preferSixtyFpsSizes) {
        Size sensorAspect = getSensorAspect(selector);
        Set<Size> sixtyFpsSizes = preferSixtyFpsSizes ? get60FpsCapableSizes(selector) : Collections.emptySet();
        ResolutionSelector resolutionSelector = new ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionFilter((supportedSizes, ignoredRotationDegrees) ->
                        sortRoundPreviewSizes(supportedSizes, sensorAspect, sixtyFpsSizes))
                .setAllowedResolutionMode(ResolutionSelector.PREFER_CAPTURE_RATE_OVER_HIGHER_RESOLUTION)
                .build();

        Preview.Builder previewBuilder = new Preview.Builder().setResolutionSelector(resolutionSelector);
        new Camera2Interop.Extender<>(previewBuilder).setSessionCaptureCallback(CameraActiveLens.CAPTURE_CALLBACK);

        if (targetFrameRateRange != null) {
            previewBuilder.setTargetFrameRate(targetFrameRateRange);
        }

        previewBuilder.setPreviewStabilizationEnabled(!isDualMode && enablePreviewStabilization);

        if (!ExteraConfig.getCameraMirrorMode()) {
            previewBuilder.setMirrorMode(MirrorMode.MIRROR_MODE_OFF);
        }

        return previewBuilder.build();
    }

    CameraSelector widestBackCameraSelector() {
        CameraSelector cached = widestBackCameraSelectorCache;
        if (cached != null) {
            return cached;
        }
        CameraSelector widest = CameraSelector.DEFAULT_BACK_CAMERA;
        try {
            if (provider == null || !provider.hasCamera(widest)) {
                return widest;
            }
            float widestZoom = minZoomRatio(provider.getCameraInfo(widest)) - WIDER_CAMERA_MARGIN;
            for (CameraInfo info : provider.getAvailableCameraInfos()) {
                if (info.getLensFacing() != CameraSelector.LENS_FACING_BACK) {
                    continue;
                }
                float zoom = minZoomRatio(info);
                if (zoom < widestZoom) {
                    widestZoom = zoom;
                    widest = info.getCameraSelector();
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        widestBackCameraSelectorCache = widest;
        return widest;
    }

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    private static float minZoomRatio(CameraInfo info) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return 1.0f;
        }
        Range<Float> range = Camera2CameraInfo.from(info)
                .getCameraCharacteristic(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
        return range == null ? 1.0f : range.getLower();
    }

    private boolean isPreviewStabilizationSupported(CameraSelector selector) {
        try {
            if (selector == null || provider == null || !provider.hasCamera(selector)) {
                return false;
            }
            Boolean cached = STABILIZATION_SUPPORT_CACHE.get(selector);
            if (cached != null) {
                return cached;
            }
            boolean supported = Preview.getPreviewCapabilities(provider.getCameraInfo(selector)).isStabilizationSupported();
            STABILIZATION_SUPPORT_CACHE.put(selector, supported);
            return supported;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    private Camera tryBindSingleAtFrameRate(Range<Integer> targetFrameRateRange) {
        try {
            CameraInfo cameraInfo = provider.getCameraInfo(cameraSelector);
            var builder = new SessionConfig.Builder(previewUseCase);
            var supportedFrameRates = cameraInfo.getSupportedFrameRateRanges(builder.build());
            if (!supportedFrameRates.contains(targetFrameRateRange)) {
                return null;
            }
            builder.setFrameRateRange(targetFrameRateRange);
            return tryBindSingleSession(builder.build(), targetFrameRateRange.getUpper());
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private Camera tryBindSingleAtExtendedFrameRate() {
        try {
            var builder = new SessionConfig.Builder(previewUseCase);
            Range<Integer> frameRateRange = selectExtendedFpsRange(
                    provider.getCameraInfo(cameraSelector).getSupportedFrameRateRanges(builder.build()));
            if (frameRateRange == null) {
                return null;
            }
            builder.setFrameRateRange(frameRateRange);
            return tryBindSingleSession(builder.build(), frameRateRange.getUpper());
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static Range<Integer> selectExtendedFpsRange(Collection<Range<Integer>> ranges) {
        Range<Integer> best = null;
        if (ranges == null) {
            return null;
        }
        for (Range<Integer> range : ranges) {
            if (range == null || !range.getUpper().equals(FPS_60_RANGE.getUpper())) {
                continue;
            }
            if (FPS_60_RANGE.equals(range)) {
                return range;
            }
            if (best == null || range.getLower() > best.getLower()) {
                best = range;
            }
        }
        return best;
    }

    private Camera tryBindSingleSession(SessionConfig sessionConfig, int frameRate) {
        try {
            if (!provider.getCameraInfo(cameraSelector).isSessionConfigSupported(sessionConfig)) {
                return null;
            }
            Camera boundCamera = provider.bindToLifecycle(lifecycle, cameraSelector, sessionConfig);
            if (boundCamera != null) {
                recordingFrameRate = frameRate;
            }
            return boundCamera;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private void rebindCamera() {
        if (provider == null || lifecycle.getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED || isBinding) {
            return;
        }
        isBinding = true;
        recordingFrameRate = 30;
        clearCameraReferences();

        try {
            provider.unbindAll();

            if (isDualMode) {
                bindDualUseCases();
            } else {
                bindSingleUseCases();
            }
            isInitiated = camera != null;
        } catch (Exception e) {
            FileLog.e(e);
            isInitiated = false;
            try {
                provider.unbindAll();
            } catch (Exception e2) {
                FileLog.e(e2);
            }
            clearCameraReferences();
        } finally {
            isBinding = false;
        }
    }

    private void bindSingleUseCases() {
        try {
            cameraSelector = isFrontface ? CameraSelector.DEFAULT_FRONT_CAMERA : widestBackCameraSelector();
            if (!provider.hasCamera(cameraSelector)) {
                isInitiated = false;
                return;
            }

            boolean extendedFps = ExteraConfig.getExtendedFramesPerSecond();
            boolean stabilizationEnabled = ExteraConfig.getCameraStabilization() &&
                    isPreviewStabilizationSupported(cameraSelector);
            previewUseCase = buildPreview(cameraSelector, stabilizationEnabled, null, extendedFps);
            previewUseCase.setSurfaceProvider(surfaceProviderPrimary);

            if (extendedFps) {
                camera = tryBindSingleAtExtendedFrameRate();
                if (camera == null && stabilizationEnabled) {
                    previewUseCase = buildPreview(cameraSelector, false, null, true);
                    previewUseCase.setSurfaceProvider(surfaceProviderPrimary);
                    camera = tryBindSingleAtExtendedFrameRate();
                }
                if (camera == null) {
                    previewUseCase = buildPreview(cameraSelector, stabilizationEnabled, null, false);
                    previewUseCase.setSurfaceProvider(surfaceProviderPrimary);
                    camera = tryBindSingleAtFrameRate(FPS_30_RANGE);
                }
            } else {
                camera = tryBindSingleAtFrameRate(FPS_30_RANGE);
            }
            if (camera == null) {
                try {
                    camera = provider.bindToLifecycle(lifecycle, cameraSelector, previewUseCase);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            if (camera == null && stabilizationEnabled) {
                previewUseCase = buildPreview(cameraSelector, false, null, false);
                previewUseCase.setSurfaceProvider(surfaceProviderPrimary);
                camera = provider.bindToLifecycle(lifecycle, cameraSelector, previewUseCase);
            }
            if (camera == null) {
                isInitiated = false;
                return;
            }

            cameraControl = camera.getCameraControl();
            observeCameraState(camera);
            applyInitialZoom(camera, cameraControl);
            updateTorchState();
        } catch (Exception e) {
            FileLog.e(e);
            isInitiated = false;
        }
    }

    private void bindDualUseCases() {
        boolean extendedFps = ExteraConfig.getExtendedFramesPerSecond();
        Range<Integer> boundFrameRateRange = extendedFps ? FPS_60_RANGE : FPS_30_RANGE;
        ConcurrentCamera concurrentCamera = tryBindConcurrentCameras(boundFrameRateRange);
        if (concurrentCamera == null && extendedFps) {
            boundFrameRateRange = FPS_30_RANGE;
            concurrentCamera = tryBindConcurrentCameras(boundFrameRateRange);
        }
        if (concurrentCamera == null && boundFrameRateRange != null) {
            boundFrameRateRange = null;
            concurrentCamera = tryBindConcurrentCameras(null);
        }
        if (concurrentCamera == null) {
            isDualMode = false;
            bindSingleUseCases();
            return;
        }
        if (boundFrameRateRange != null) {
            recordingFrameRate = boundFrameRateRange.getLower();
        }

        for (Camera cam : concurrentCamera.getCameras()) {
            if (cam.getCameraInfo().getLensFacing() == CameraSelector.LENS_FACING_FRONT) {
                cameraFront = cam;
                cameraControlFront = cam.getCameraControl();
                applyInitialZoom(cameraFront, cameraControlFront);
            } else {
                cameraBack = cam;
                cameraControlBack = cam.getCameraControl();
                applyInitialZoom(cameraBack, cameraControlBack);
            }
            observeCameraState(cam);
        }
        updateActiveControl(isFrontface);
        updateTorchState();
    }

    private ConcurrentCamera tryBindConcurrentCameras(Range<Integer> targetFrameRateRange) {
        try {
            boolean preferSixtyFpsSizes = FPS_60_RANGE.equals(targetFrameRateRange);
            selectWidestConcurrentPair();
            previewUseCase = buildPreview(concurrentFrontSelector, false, targetFrameRateRange, preferSixtyFpsSizes);
            previewUseCase.setSurfaceProvider(surfaceProviderPrimary);

            previewUseCaseBack = buildPreview(concurrentBackSelector, false, targetFrameRateRange, preferSixtyFpsSizes);
            if (surfaceProviderSecondary != null) {
                previewUseCaseBack.setSurfaceProvider(surfaceProviderSecondary);
            }

            UseCaseGroup frontGroup = new UseCaseGroup.Builder()
                    .addUseCase(previewUseCase)
                    .build();

            ConcurrentCamera.SingleCameraConfig frontConfig = new ConcurrentCamera.SingleCameraConfig(
                    concurrentFrontSelector,
                    frontGroup,
                    lifecycle
            );

            UseCaseGroup backGroup = new UseCaseGroup.Builder()
                    .addUseCase(previewUseCaseBack)
                    .build();

            ConcurrentCamera.SingleCameraConfig backConfig = new ConcurrentCamera.SingleCameraConfig(
                    concurrentBackSelector,
                    backGroup,
                    lifecycle
            );

            List<ConcurrentCamera.SingleCameraConfig> configs = new ArrayList<>(2);
            configs.add(frontConfig);
            configs.add(backConfig);

            return provider.bindToLifecycle(configs);
        } catch (Exception e) {
            FileLog.e(e);
            try {
                provider.unbindAll();
            } catch (Exception e2) {
                FileLog.e(e2);
            }
            return null;
        }
    }

    private void selectWidestConcurrentPair() {
        concurrentFrontSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
        concurrentBackSelector = CameraSelector.DEFAULT_BACK_CAMERA;
        try {
            float widestZoom = minZoomRatio(provider.getCameraInfo(concurrentBackSelector)) - WIDER_CAMERA_MARGIN;
            for (List<CameraInfo> pair : provider.getAvailableConcurrentCameraInfos()) {
                CameraInfo front = null;
                CameraInfo back = null;
                for (CameraInfo info : pair) {
                    if (info.getLensFacing() == CameraSelector.LENS_FACING_FRONT) {
                        front = info;
                    } else if (info.getLensFacing() == CameraSelector.LENS_FACING_BACK) {
                        back = info;
                    }
                }
                if (front == null || back == null) {
                    continue;
                }
                float zoom = minZoomRatio(back);
                if (zoom < widestZoom) {
                    widestZoom = zoom;
                    concurrentFrontSelector = front.getCameraSelector();
                    concurrentBackSelector = back.getCameraSelector();
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
            concurrentFrontSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
            concurrentBackSelector = CameraSelector.DEFAULT_BACK_CAMERA;
        }
    }

    private void observeCameraState(Camera cam) {
        try {
            cam.getCameraInfo().getCameraState().observe(lifecycle, state -> {
                CameraState.StateError error = state.getError();
                if (error != null) {
                    FileLog.e("CameraX camera state error: code=" + error.getCode() + " type=" + state.getType());
                    if (error.getCause() != null) {
                        FileLog.e(error.getCause());
                    }
                }
            });
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void applyInitialZoom(Camera cam, CameraControl control) {
        if (control == null || cam == null) return;

        boolean wideAngleStart = wantsWideAngleStart(cam);
        LiveData<ZoomState> zoomStateData = cam.getCameraInfo().getZoomState();
        ZoomState currentState = wideAngleStart ? zoomStateData.getValue() : null;
        control.setZoomRatio(currentState == null ? 1.0f : Math.min(1.0f, currentState.getMinZoomRatio()));
        if (!wideAngleStart || currentState != null) {
            return;
        }
        zoomStateData.observe(lifecycle, new Observer<>() {
            @Override
            public void onChanged(ZoomState zoomState) {
                if (zoomState == null) {
                    return;
                }
                zoomStateData.removeObserver(this);
                if (wantsWideAngleStart(cam)) {
                    applyWideAngle(control, zoomState);
                }
            }
        });
    }

    private void applyWideAngle(CameraControl control, ZoomState zoomState) {
        if (zoomState != null && zoomState.getMinZoomRatio() < 1.0f) {
            control.setZoomRatio(zoomState.getMinZoomRatio());
        }
    }

    private boolean wantsWideAngleStart(Camera cam) {
        return ExteraConfig.getStartWithWideAngleCamera() &&
                cam != null &&
                cam.getCameraInfo().getLensFacing() == CameraSelector.LENS_FACING_BACK;
    }

    public float getLinearZoom() {
        if (camera == null) return 0f;
        var zoomState = camera.getCameraInfo().getZoomState().getValue();
        return zoomState != null ? zoomState.getLinearZoom() : 0;
    }

    public void setLinearZoom(float zoom) {
        if (cameraControl == null) return;
        cameraControl.setLinearZoom(zoom);
    }

    public void setZoom(float scaleFactor) {
        if (cameraControl == null || camera == null) {
            return;
        }
        var zoomState = camera.getCameraInfo().getZoomState().getValue();
        if (zoomState == null) return;
        var newZoom = Utilities.clamp(zoomState.getZoomRatio() * scaleFactor,
                zoomState.getMaxZoomRatio(),
                zoomState.getMinZoomRatio());
        cameraControl.setZoomRatio(newZoom);
    }

    public float getZoomRatio() {
        if (camera == null) return 1.0f;
        var zoomState = camera.getCameraInfo().getZoomState().getValue();
        return zoomState != null ? zoomState.getZoomRatio() : 1.0f;
    }

    public void setZoomRatio(float ratio) {
        if (cameraControl == null) return;
        cameraControl.setZoomRatio(ratio);
    }

    public float getMinZoomRatio() {
        if (camera != null) {
            var zoomState = camera.getCameraInfo().getZoomState().getValue();
            if (zoomState != null) {
                return zoomState.getMinZoomRatio();
            }
        }
        return 1.0f;
    }

    public float getMaxZoomRatio() {
        if (camera != null) {
            var zoomState = camera.getCameraInfo().getZoomState().getValue();
            if (zoomState != null) {
                return zoomState.getMaxZoomRatio();
            }
        }
        return 1.0f;
    }

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    public String getActiveCameraId() {
        if (camera == null) {
            return null;
        }
        try {
            return Camera2CameraInfo.from(camera.getCameraInfo()).getCameraId();
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public void focusToPoint(float x, float y, float viewWidth, float viewHeight) {
        if (cameraControl == null || camera == null || viewWidth <= 0 || viewHeight <= 0) {
            return;
        }
        Display display = getDefaultDisplay();
        if (display == null) {
            return;
        }
        try {
            MeteringPointFactory factory = new DisplayOrientedMeteringPointFactory(display, camera.getCameraInfo(), viewWidth, viewHeight);
            MeteringPoint point = factory.createPoint(x, y);

            FocusMeteringAction action = new FocusMeteringAction
                    .Builder(point, FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE)
                    .build();

            cameraControl.startFocusAndMetering(action);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public int getDisplayOrientation() {
        int rotation = Surface.ROTATION_0;
        Display display = getDefaultDisplay();
        if (display != null) {
            rotation = display.getRotation();
        }
        return switch (rotation) {
            case Surface.ROTATION_90 -> 90;
            case Surface.ROTATION_180 -> 180;
            case Surface.ROTATION_270 -> 270;
            default -> 0;
        };
    }

    // UTILS

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    private Size getSensorAspect(CameraSelector selector) {
        try {
            if (provider != null && selector != null && provider.hasCamera(selector)) {
                Rect activeArray = Camera2CameraInfo.from(provider.getCameraInfo(selector))
                        .getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                if (activeArray != null && activeArray.width() > 0 && activeArray.height() > 0) {
                    return new Size(activeArray.width(), activeArray.height());
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return FALLBACK_SENSOR_ASPECT;
    }

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    private Set<Size> get60FpsCapableSizes(CameraSelector selector) {
        var sizes = new HashSet<Size>();
        try {
            if (provider == null || selector == null || !provider.hasCamera(selector)) {
                return sizes;
            }
            StreamConfigurationMap map = Camera2CameraInfo.from(provider.getCameraInfo(selector))
                    .getCameraCharacteristic(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] outputSizes = map != null ? map.getOutputSizes(SurfaceTexture.class) : null;
            if (outputSizes == null) {
                return sizes;
            }
            for (Size size : outputSizes) {
                long minFrameDurationNs = map.getOutputMinFrameDuration(SurfaceTexture.class, size);
                if (minFrameDurationNs > 0 && minFrameDurationNs <= MAX_60_FPS_FRAME_DURATION_NS) {
                    sizes.add(size);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return sizes;
    }

    private static List<Size> sortRoundPreviewSizes(List<Size> supportedSizes, Size sensorAspect,
                                                    Set<Size> sixtyFpsSizes) {
        ArrayList<Size> sortedSizes = new ArrayList<>(supportedSizes);
        int minimumShortEdge = SystemUtils.getRoundVideoResolution();
        int idealShortEdge = Math.min(MAX_PREFERRED_PREVIEW_SHORT_EDGE,
                Math.max(PREFERRED_PREVIEW_SHORT_EDGE, minimumShortEdge * 2));
        double sensorRatio = aspectRatioOf(sensorAspect);
        boolean requireSixtyFps = !sixtyFpsSizes.isEmpty();
        sortedSizes.sort(Comparator
                .comparingInt((Size size) ->
                        previewTier(size, minimumShortEdge, requireSixtyFps, sixtyFpsSizes))
                .thenComparingDouble(size -> previewScore(size, sensorRatio, idealShortEdge))
                .thenComparingLong(size -> (long) size.getWidth() * size.getHeight()));
        return sortedSizes;
    }

    private static int previewTier(Size size, int minimumShortEdge, boolean requireSixtyFps,
                                   Set<Size> sixtyFpsSizes) {
        int shortEdge = Math.min(size.getWidth(), size.getHeight());
        if (shortEdge < minimumShortEdge) {
            return 3;
        }
        int longEdge = Math.max(size.getWidth(), size.getHeight());
        if (longEdge > MAX_PREVIEW_LONG_EDGE
                || (long) size.getWidth() * size.getHeight() > MAX_PREVIEW_AREA) {
            return 2;
        }
        return requireSixtyFps && !sixtyFpsSizes.contains(size) ? 1 : 0;
    }

    private static double previewScore(Size size, double sensorRatio, int idealShortEdge) {
        int shortEdge = Math.min(size.getWidth(), size.getHeight());
        int longEdge = Math.max(size.getWidth(), size.getHeight());
        if (shortEdge <= 0 || longEdge <= 0 || idealShortEdge <= 0) {
            return Double.MAX_VALUE;
        }
        double fieldOfView = Math.max(0.0, (double) longEdge / shortEdge - sensorRatio) / sensorRatio;
        double resolution = Math.abs(shortEdge - idealShortEdge) / (double) idealShortEdge;
        double cropWaste = 1.0 - (double) shortEdge / longEdge;
        return FIELD_OF_VIEW_WEIGHT * fieldOfView
                + RESOLUTION_WEIGHT * resolution
                + CROP_WASTE_WEIGHT * cropWaste;
    }

    private static double aspectRatioOf(Size size) {
        int shortEdge = Math.min(size.getWidth(), size.getHeight());
        int longEdge = Math.max(size.getWidth(), size.getHeight());
        return shortEdge > 0 ? (double) longEdge / shortEdge : 4.0 / 3.0;
    }

    private static Display getDefaultDisplay() {
        DisplayManager displayManager = (DisplayManager) ApplicationLoader.applicationContext.getSystemService(Context.DISPLAY_SERVICE);
        return displayManager != null ? displayManager.getDisplay(Display.DEFAULT_DISPLAY) : null;
    }

    public static boolean isRoundDualAvailable(Context context) {
        return DualCameraView.roundDualAvailableStatic(context) && isSeamlessSwitchingAvailable(context);
    }

    public static boolean isSeamlessSwitchingAvailable(Context context) {
        if (seamlessSwitchingAvailableCache != null) {
            return seamlessSwitchingAvailableCache;
        }
        seamlessSwitchingAvailableCache =
                SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_AVERAGE &&
                SharedConfig.allowPreparingHevcPlayers() &&
                hasConcurrentFrontBackPair(context);
        return seamlessSwitchingAvailableCache;
    }

    private static boolean hasConcurrentFrontBackPair(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return false;
        }
        PackageManager packageManager = context.getPackageManager();
        if (packageManager == null || !packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_CONCURRENT)) {
            return false;
        }
        CameraManager cameraManager = context.getSystemService(CameraManager.class);
        if (cameraManager == null) {
            return false;
        }
        try {
            for (Set<String> combination : cameraManager.getConcurrentCameraIds()) {
                boolean hasFront = false;
                boolean hasBack = false;
                for (String cameraId : combination) {
                    Integer lensFacing = cameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING);
                    if (lensFacing == null) {
                        continue;
                    }
                    if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                        hasFront = true;
                    } else if (lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                        hasBack = true;
                    }
                }
                if (hasFront && hasBack) {
                    return true;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }
}
