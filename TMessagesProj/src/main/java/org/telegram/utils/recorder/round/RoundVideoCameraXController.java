/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

*/

package org.telegram.utils.recorder.round;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.SurfaceRequest;
import androidx.core.content.ContextCompat;

import com.exteragram.messenger.camera.CameraXSession;

import java.io.IOException;

final class RoundVideoCameraXController extends RoundVideoCamera {

    private static final int PREVIEW_SHORT_SIDE = 720;
    private static final long COMMON_START_TIMEOUT_MS = 3_000L;
    private static final float MAXIMUM_PROGRESS_ZOOM_RATIO = 4f;

    private final Context context;
    private final TextureView previewView;
    private final int outputSize;
    private final int videoBitrate;
    private final int audioBitrate;
    private final boolean dualCameraEnabled;
    private final RoundVideoSession.CameraResolution cameraResolution;
    private final boolean compositionEnabled;
    private final RoundVideoDiagnostics diagnostics;
    private final RoundVideoCameraController.Callback callback;
    private final CameraManager cameraManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private HandlerThread workerThread;
    private Handler workerHandler;
    private CameraXSession cameraXSession;
    private SurfaceRequest pendingPrimaryRequest;
    private SurfaceRequest pendingSecondaryRequest;
    private volatile Surface previewSurface;
    private volatile RoundVideoMp4Writer writer;
    private volatile long timelineOffsetUs;
    private volatile RoundVideoSession.CameraFacing requestedFacing;
    private volatile RoundVideoSession.CameraFacing activeFacing;
    private volatile RoundVideoSession.FrameRate activeFrameRate = RoundVideoSession.FrameRate.FPS_30;
    private volatile RoundVideoCodecRecorder recorder;
    private volatile RoundVideoGlProcessor glProcessor;
    private RoundVideoCodecRecorder commonStartRecorder;
    private volatile Size primaryStreamSize;
    private volatile Size secondaryStreamSize;
    private volatile boolean pipelineDual;
    private volatile boolean active;
    private volatile boolean stopping;
    private volatile boolean cameraConfigured;
    private volatile boolean awaitingSwitchPreviewFrame;
    private boolean opening;
    private boolean pipelineReady;
    private boolean switchingCamera;
    private long segmentStartedNs;

    private final Runnable commonStartTimeoutRunnable = () -> {
        RoundVideoCodecRecorder pendingRecorder = commonStartRecorder;
        if (!active || stopping || pendingRecorder == null || recorder != pendingRecorder) return;
        commonStartRecorder = null;
        reportError(new IllegalStateException(
                "Timed out waiting for synchronized audio and video start"
        ));
    };

    RoundVideoCameraXController(
            @NonNull Context context,
            @NonNull TextureView previewView,
            int outputSize,
            int videoBitrate,
            int audioBitrate,
            boolean dualCameraEnabled,
            @NonNull RoundVideoSession.CameraResolution cameraResolution,
            boolean compositionEnabled,
            @NonNull RoundVideoDiagnostics diagnostics,
            @NonNull RoundVideoCameraController.Callback callback
    ) {
        this.context = context.getApplicationContext();
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        this.previewView = previewView;
        this.outputSize = outputSize;
        this.videoBitrate = videoBitrate;
        this.audioBitrate = audioBitrate;
        this.dualCameraEnabled = dualCameraEnabled;
        this.cameraResolution = cameraResolution;
        this.compositionEnabled = compositionEnabled;
        this.diagnostics = diagnostics;
        this.callback = callback;
    }

    @Override
    void startSegment(
            @NonNull RoundVideoMp4Writer writer,
            long timelineOffsetUs,
            @NonNull RoundVideoSession.CameraFacing facing
    ) {
        ensureThread();
        this.writer = writer;
        this.timelineOffsetUs = timelineOffsetUs;
        requestedFacing = facing;
        active = true;
        stopping = false;
        cameraConfigured = false;
        pipelineReady = false;
        switchingCamera = false;
        commonStartRecorder = null;
        segmentStartedNs = SystemClock.elapsedRealtimeNanos();
        diagnostics.log("CameraX segment start: facing=" + facing
                + ", timelineOffsetUs=" + timelineOffsetUs
                + ", textureAvailable=" + previewView.isAvailable());
        previewView.setSurfaceTextureListener(previewListener);
        openCameraIfReady();
    }

    @Override
    boolean setCameraFacing(@NonNull RoundVideoSession.CameraFacing facing) {
        requestedFacing = facing;
        CameraXSession session = cameraXSession;
        if (!active || session == null || !session.isReady()
                || switchingCamera || facing == activeFacing) {
            return false;
        }
        RoundVideoGlProcessor processor = glProcessor;
        if (pipelineDual && session.isDualMode() && processor != null) {
            callback.onCameraSwitchStarted(facing);
            session.switchCamera();
            session.resetInactiveCameraZoom();
            updateActiveCamera(session);
            RoundVideoSession.CameraFacing switchedFacing = activeFacing;
            processor.setSecondaryInputActive(
                    switchedFacing == RoundVideoSession.CameraFacing.BACK,
                    () -> mainHandler.post(() -> {
                        if (active && activeFacing == switchedFacing) callback.onCameraSwitchFirstFrame();
                    })
            );
            diagnostics.log("CameraX dual switch: to=" + switchedFacing);
            notifyConfigured(session, true);
            return true;
        }
        switchingCamera = true;
        callback.onCameraSwitchStarted(facing);
        if (processor != null && activeFacing != null) {
            processor.beginCameraSwitch(activeFacing, facing);
        }
        diagnostics.log("CameraX switch started: from=" + activeFacing + ", to=" + facing);
        session.switchCamera();
        if (!session.isReady()) {
            reportError(new IllegalStateException("CameraX rebind failed after camera switch"));
        }
        return true;
    }

    @Override
    boolean setZoom(float progress) {
        CameraXSession session = cameraXSession;
        if (!active || session == null) return false;
        if (progress <= 0f) return true;
        float maximum = Math.min(MAXIMUM_PROGRESS_ZOOM_RATIO, session.getMaxZoomRatio());
        float clamped = Math.min(1f, progress);
        session.setZoomRatio(1f + clamped * Math.max(0f, maximum - 1f));
        return true;
    }

    @Override
    boolean setZoomRatio(float ratio) {
        CameraXSession session = cameraXSession;
        if (!active || session == null || !Float.isFinite(ratio) || ratio <= 0f) return false;
        session.setZoomRatio(Math.max(session.getMinZoomRatio(), Math.min(session.getMaxZoomRatio(), ratio)));
        return true;
    }

    @Override
    boolean setFocusPoint(float x, float y) {
        CameraXSession session = cameraXSession;
        Size size = pipelineDual && activeFacing == RoundVideoSession.CameraFacing.BACK
                ? secondaryStreamSize
                : primaryStreamSize;
        if (!active || session == null || !session.isReady() || size == null) return false;
        boolean swapped = isStreamRotated(session.getActiveCameraId());
        float width = swapped ? size.getHeight() : size.getWidth();
        float height = swapped ? size.getWidth() : size.getHeight();
        float crop = Math.min(width, height);
        float u = 0.5f + (Math.max(0f, Math.min(1f, x)) - 0.5f) * crop / width;
        float v = 0.5f + (Math.max(0f, Math.min(1f, y)) - 0.5f) * crop / height;
        session.focusToPoint(u, v, 1f, 1f);
        return true;
    }

    @Override
    boolean setTorchEnabled(boolean enabled) {
        CameraXSession session = cameraXSession;
        if (!active || session == null) return false;
        session.setTorchEnabled(enabled);
        return true;
    }

    @Override
    boolean stopSegment() {
        Handler handler = workerHandler;
        if (!active || stopping || handler == null) return false;
        stopping = true;
        diagnostics.log("CameraX segment stop requested");
        handler.post(() -> {
            cancelCommonStartWait();
            requestRecorderStop();
            mainHandler.post(() -> {
                active = false;
                closeCamera();
                handler.post(() -> {
                    releasePipeline();
                    stopping = false;
                    callback.onRecordingStopped();
                });
            });
        });
        return true;
    }

    @Override
    void release() {
        active = false;
        previewView.setSurfaceTextureListener(null);
        closeCamera();
        Handler handler = workerHandler;
        HandlerThread thread = workerThread;
        workerHandler = null;
        workerThread = null;
        if (handler == null || thread == null) return;
        handler.post(() -> {
            cancelCommonStartWait();
            requestRecorderStop();
            releasePipeline();
            thread.quitSafely();
        });
    }

    private void ensureThread() {
        if (workerThread != null) return;
        workerThread = new HandlerThread("RoundVideoCameraX");
        workerThread.start();
        workerHandler = new Handler(workerThread.getLooper());
    }

    private void openCameraIfReady() {
        if (!active || opening || cameraXSession != null || !previewView.isAvailable()) return;
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            reportError(new SecurityException("Camera permission is not granted"));
            return;
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            reportError(new SecurityException("Audio recording permission is not granted"));
            return;
        }
        SurfaceTexture previewTexture = previewView.getSurfaceTexture();
        if (previewTexture == null) return;
        int previewSize = Math.max(1, Math.min(
                PREVIEW_SHORT_SIDE,
                Math.min(previewView.getWidth(), previewView.getHeight())
        ));
        previewTexture.setDefaultBufferSize(previewSize, previewSize);
        if (previewSurface == null) previewSurface = new Surface(previewTexture);
        previewView.setTransform(new Matrix());
        opening = true;
        CameraXSession session = new CameraXSession(
                new CameraXSession.CameraLifecycle(),
                request -> onSurfaceRequested(request, false)
        );
        session.setSecondSurfaceProvider(request -> onSurfaceRequested(request, true));
        cameraXSession = session;
        boolean dualRequested = dualCameraEnabled && CameraXSession.isRoundDualAvailable(context);
        diagnostics.log("CameraX open requested: facing=" + requestedFacing
                + ", dual=" + dualRequested
                + ", glPreview=" + previewSize);
        session.initCamera(
                context,
                requestedFacing == RoundVideoSession.CameraFacing.FRONT,
                dualRequested,
                () -> onCameraBound(session)
        );
    }

    private void onCameraBound(@NonNull CameraXSession session) {
        if (cameraXSession != session || !active) return;
        opening = false;
        if (!session.isReady()) {
            reportError(new IllegalStateException("Unable to bind CameraX camera"));
            return;
        }
        updateActiveCamera(session);
        diagnostics.log("CameraX bound: id=" + session.getActiveCameraId()
                + ", dual=" + session.isDualMode()
                + ", facing=" + activeFacing
                + ", fps=" + activeFrameRate.getValue()
                + ", segmentElapsedMs=" + elapsedMs(segmentStartedNs));
        if (recorder != null) {
            onPipelineReady();
            return;
        }
        Surface preview = previewSurface;
        RoundVideoSession.FrameRate frameRate = activeFrameRate;
        boolean dual = session.isDualMode();
        Handler handler = workerHandler;
        if (handler == null) return;
        handler.post(() -> createPipeline(preview, frameRate, dual));
    }

    private void createPipeline(
            @Nullable Surface preview,
            @NonNull RoundVideoSession.FrameRate frameRate,
            boolean dual
    ) {
        if (!active || stopping) return;
        try {
            RoundVideoCodecRecorder codecRecorder = new RoundVideoCodecRecorder(
                    writer,
                    timelineOffsetUs,
                    outputSize,
                    videoBitrate,
                    audioBitrate,
                    frameRate.getValue(),
                    diagnostics,
                    this::reportError,
                    callback::onAudioAmplitude,
                    callback::onSegmentInterrupted
            );
            recorder = codecRecorder;
            Surface encoderSurface = codecRecorder.prepare();
            RoundVideoGlProcessor processor = new RoundVideoGlProcessor(
                    new Size(outputSize, outputSize),
                    encoderSurface,
                    preview,
                    dual,
                    outputSize,
                    outputSize,
                    false,
                    compositionEnabled,
                    diagnostics,
                    0,
                    codecRecorder,
                    this::reportError
            );
            processor.setFirstFrameListener(() -> postToWorker(this::startRecordingAfterFirstFrame));
            glProcessor = processor;
            pipelineDual = dual;
            processor.start();
            mainHandler.post(this::onPipelineReady);
        } catch (IOException | RuntimeException e) {
            reportError(e);
        }
    }

    private void onPipelineReady() {
        if (!active) return;
        pipelineReady = true;
        servePendingRequests();
    }

    private void onSurfaceRequested(@NonNull SurfaceRequest request, boolean secondary) {
        if (!active || cameraXSession == null) {
            request.willNotProvideSurface();
            return;
        }
        diagnostics.log("CameraX surface requested: input=" + (secondary ? 1 : 0)
                + ", size=" + request.getResolution()
                + ", segmentElapsedMs=" + elapsedMs(segmentStartedNs));
        SurfaceRequest previous = secondary ? pendingSecondaryRequest : pendingPrimaryRequest;
        if (previous != null && previous != request) previous.willNotProvideSurface();
        if (secondary) {
            pendingSecondaryRequest = request;
        } else {
            pendingPrimaryRequest = request;
        }
        servePendingRequests();
    }

    private void servePendingRequests() {
        if (!pipelineReady) return;
        SurfaceRequest primary = pendingPrimaryRequest;
        SurfaceRequest secondary = pendingSecondaryRequest;
        pendingPrimaryRequest = null;
        pendingSecondaryRequest = null;
        boolean dual = pipelineDual;
        if (!dual && secondary != null) {
            secondary.willNotProvideSurface();
            secondary = null;
        }
        if (primary == null && secondary == null) return;
        CameraXSession session = cameraXSession;
        RoundVideoGlProcessor processor = glProcessor;
        Handler handler = workerHandler;
        if (session == null || processor == null || handler == null) {
            willNotProvideSurfaces(primary, secondary);
            return;
        }
        SurfaceRequest primaryRequest = primary;
        SurfaceRequest secondaryRequest = secondary;
        Size primarySize = primary == null ? null : primary.getResolution();
        Size secondarySize = secondary == null ? secondaryStreamSize : secondary.getResolution();
        boolean timestampRealtime = isTimestampRealtime(session.getActiveCameraId());
        boolean secondaryActive = dual && !session.isActiveCameraFrontface();
        handler.post(() -> {
            try {
                if (primarySize != null) {
                    processor.updateInputConfiguration(
                            primarySize,
                            Math.min(primarySize.getWidth(), primarySize.getHeight()),
                            timestampRealtime
                    );
                }
                if (dual && secondarySize != null) {
                    processor.configureSecondaryInput(
                            secondarySize,
                            Math.min(secondarySize.getWidth(), secondarySize.getHeight()),
                            timestampRealtime
                    );
                }
                if (dual) processor.setSecondaryInputActive(secondaryActive);
            } catch (RuntimeException e) {
                mainHandler.post(() -> willNotProvideSurfaces(primaryRequest, secondaryRequest));
                reportError(e);
                return;
            }
            mainHandler.post(() -> provideSurfaces(primaryRequest, secondaryRequest, processor));
        });
    }

    private void provideSurfaces(
            @Nullable SurfaceRequest primary,
            @Nullable SurfaceRequest secondary,
            @NonNull RoundVideoGlProcessor processor
    ) {
        CameraXSession session = cameraXSession;
        if (!active || session == null || glProcessor != processor) {
            willNotProvideSurfaces(primary, secondary);
            return;
        }
        if (primary != null) {
            primaryStreamSize = primary.getResolution();
            provideSurface(primary, processor.getCameraSurface(), false);
        }
        if (secondary != null) {
            secondaryStreamSize = secondary.getResolution();
            provideSurface(secondary, processor.getSecondaryCameraSurface(), true);
        }
        updateActiveCamera(session);
        boolean activeSecondary = pipelineDual && activeFacing == RoundVideoSession.CameraFacing.BACK;
        if (cameraConfigured && (activeSecondary ? secondary : primary) == null) return;
        if ((activeSecondary ? secondaryStreamSize : primaryStreamSize) == null) return;
        boolean wasSwitch = switchingCamera;
        switchingCamera = false;
        cameraConfigured = true;
        if (wasSwitch) {
            processor.onCameraSessionConfigured();
            awaitingSwitchPreviewFrame = true;
        }
        diagnostics.log("CameraX surfaces provided: primary="
                + (primary == null ? "kept" : primaryStreamSize)
                + ", secondary=" + (secondary == null ? "kept" : secondaryStreamSize)
                + ", facing=" + activeFacing
                + ", switch=" + wasSwitch
                + ", segmentElapsedMs=" + elapsedMs(segmentStartedNs));
        notifyConfigured(session, wasSwitch);
        if (requestedFacing != null && requestedFacing != activeFacing) {
            mainHandler.post(() -> setCameraFacing(requestedFacing));
        }
    }

    private void provideSurface(
            @NonNull SurfaceRequest request,
            @NonNull Surface surface,
            boolean secondary
    ) {
        request.provideSurface(
                surface,
                ContextCompat.getMainExecutor(context),
                result -> diagnostics.log("CameraX surface released: input=" + (secondary ? 1 : 0)
                        + ", result=" + result.getResultCode())
        );
    }

    private static void willNotProvideSurfaces(
            @Nullable SurfaceRequest primary,
            @Nullable SurfaceRequest secondary
    ) {
        if (primary != null) primary.willNotProvideSurface();
        if (secondary != null) secondary.willNotProvideSurface();
    }

    private void notifyConfigured(@NonNull CameraXSession session, boolean cameraSwitch) {
        Size size = pipelineDual && activeFacing == RoundVideoSession.CameraFacing.BACK
                ? secondaryStreamSize
                : primaryStreamSize;
        callback.onConfigured(
                new RoundVideoCameraController.CameraInfo(
                        session.getActiveCameraId(),
                        activeFacing,
                        cameraResolution,
                        activeFrameRate,
                        size,
                        session.getMaxZoomRatio(),
                        activeFacing == RoundVideoSession.CameraFacing.BACK && session.isFlashAvailable()
                ),
                cameraSwitch
        );
    }

    private void updateActiveCamera(@NonNull CameraXSession session) {
        activeFacing = session.isActiveCameraFrontface()
                ? RoundVideoSession.CameraFacing.FRONT
                : RoundVideoSession.CameraFacing.BACK;
        activeFrameRate = session.getRecordingFrameRate() >= 60
                ? RoundVideoSession.FrameRate.FPS_60
                : RoundVideoSession.FrameRate.FPS_30;
    }

    private void startRecordingAfterFirstFrame() {
        RoundVideoCodecRecorder currentRecorder = recorder;
        if (!active || stopping || !cameraConfigured
                || currentRecorder == null || currentRecorder.isStarted()) {
            return;
        }
        try {
            commonStartRecorder = currentRecorder;
            Handler handler = workerHandler;
            if (handler != null) {
                handler.removeCallbacks(commonStartTimeoutRunnable);
                handler.postDelayed(commonStartTimeoutRunnable, COMMON_START_TIMEOUT_MS);
            }
            currentRecorder.startRecording(
                    () -> postToWorker(() -> startVideoAfterAudioReady(currentRecorder)),
                    () -> postToWorker(() -> announceRecordingStarted(currentRecorder))
            );
            diagnostics.log("CameraX first frame received; audio startup requested: segmentElapsedMs="
                    + elapsedMs(segmentStartedNs));
        } catch (RuntimeException e) {
            cancelCommonStartWait();
            reportError(e);
        }
    }

    private void startVideoAfterAudioReady(@NonNull RoundVideoCodecRecorder expectedRecorder) {
        RoundVideoGlProcessor processor = glProcessor;
        if (!active || stopping || recorder != expectedRecorder || processor == null) return;
        try {
            processor.startRecording(expectedRecorder.getTimeOriginNs());
        } catch (RuntimeException e) {
            cancelCommonStartWait();
            reportError(e);
        }
    }

    private void announceRecordingStarted(@NonNull RoundVideoCodecRecorder expectedRecorder) {
        if (!active || stopping || recorder != expectedRecorder) return;
        cancelCommonStartWait();
        diagnostics.log("CameraX common A/V start completed: segmentElapsedMs="
                + elapsedMs(segmentStartedNs));
        callback.onRecordingStarted();
    }

    private void requestRecorderStop() {
        RoundVideoCodecRecorder currentRecorder = recorder;
        RoundVideoGlProcessor processor = glProcessor;
        if (currentRecorder == null) return;
        long stopPresentationTimeUs = currentRecorder.requestStop();
        if (processor != null && stopPresentationTimeUs != Long.MAX_VALUE) {
            processor.stopRecordingAt(stopPresentationTimeUs);
        }
    }

    private void releasePipeline() {
        RoundVideoGlProcessor processor = glProcessor;
        glProcessor = null;
        if (processor != null) processor.stop();
        Surface preview = previewSurface;
        previewSurface = null;
        if (preview != null) preview.release();
        RoundVideoCodecRecorder currentRecorder = recorder;
        recorder = null;
        if (currentRecorder != null) currentRecorder.stop();
        primaryStreamSize = null;
        secondaryStreamSize = null;
        pipelineDual = false;
    }

    private void closeCamera() {
        CameraXSession session = cameraXSession;
        cameraXSession = null;
        opening = false;
        pipelineReady = false;
        switchingCamera = false;
        cameraConfigured = false;
        awaitingSwitchPreviewFrame = false;
        SurfaceRequest primary = pendingPrimaryRequest;
        SurfaceRequest secondary = pendingSecondaryRequest;
        pendingPrimaryRequest = null;
        pendingSecondaryRequest = null;
        if (primary != null) primary.willNotProvideSurface();
        if (secondary != null) secondary.willNotProvideSurface();
        if (session != null) session.closeCamera();
    }

    private void cancelCommonStartWait() {
        Handler handler = workerHandler;
        if (handler != null) handler.removeCallbacks(commonStartTimeoutRunnable);
        commonStartRecorder = null;
    }

    private void postToWorker(@NonNull Runnable runnable) {
        Handler handler = workerHandler;
        if (handler != null) handler.post(runnable);
    }

    private boolean isTimestampRealtime(@Nullable String cameraId) {
        CameraCharacteristics characteristics = getCharacteristics(cameraId);
        Integer source = characteristics == null
                ? null
                : characteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE);
        return source != null && source == CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME;
    }

    private boolean isStreamRotated(@Nullable String cameraId) {
        CameraCharacteristics characteristics = getCharacteristics(cameraId);
        Integer sensorOrientation = characteristics == null
                ? null
                : characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        if (sensorOrientation == null) return false;
        int displayRotation = previewView.getDisplay() == null
                ? 0
                : previewView.getDisplay().getRotation() * 90;
        return (sensorOrientation - displayRotation + 360) % 180 != 0;
    }

    @Nullable
    private CameraCharacteristics getCharacteristics(@Nullable String cameraId) {
        if (cameraId == null || cameraManager == null) return null;
        try {
            return cameraManager.getCameraCharacteristics(cameraId);
        } catch (Exception e) {
            diagnostics.error("CameraX characteristics unavailable", e);
            return null;
        }
    }

    private void reportError(@NonNull Exception error) {
        diagnostics.error("CameraX camera error", error);
        callback.onError(error);
    }

    private static long elapsedMs(long startedNs) {
        return (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000L;
    }

    private final TextureView.SurfaceTextureListener previewListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(
                        @NonNull SurfaceTexture surfaceTexture,
                        int width,
                        int height
                ) {
                    openCameraIfReady();
                }

                @Override
                public void onSurfaceTextureSizeChanged(
                        @NonNull SurfaceTexture surfaceTexture,
                        int width,
                        int height
                ) {
                }

                @Override
                public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
                    if (active) {
                        reportError(new IllegalStateException("Preview SurfaceTexture was destroyed"));
                    }
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
                    if (awaitingSwitchPreviewFrame) {
                        awaitingSwitchPreviewFrame = false;
                        callback.onCameraSwitchFirstFrame();
                    }
                }
            };
}
