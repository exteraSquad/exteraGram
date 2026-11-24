/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.camera;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.hardware.Camera;
import android.os.SystemClock;
import android.view.MotionEvent;

import androidx.camera.core.ZoomState;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.camera.Camera2Session;
import org.telegram.messenger.camera.CameraSession;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.utils.recorder.round.RoundVideoSession;

import java.util.List;

@SuppressLint("ViewConstructor")
public class InstantCameraZoomSlider extends CameraZoomSliderView {

    public interface OnCameraZoomChangeListener {
        void onCameraZoomChanged(float zoom, boolean fromSlider);
    }

    private enum Backend {
        NONE,
        CAMERA_1,
        CAMERA_2,
        CAMERA_X,
        ROUND_VIDEO
    }

    private static final int BIND_RETRY_LIMIT = 25;
    private static final long BIND_RETRY_DELAY_MS = 100L;
    private static final long APPEAR_DURATION_MS = 180L;
    private static final float APPEAR_SCALE_FROM = 0.9f;
    private static final int FALLBACK_FRAME_RATE = 30;
    private static final float CIRCLE_GAP_DP = 16.0f;
    private static final float BAR_BLOCK_DP = 64.0f;
    private static final long RESET_ZOOM_DURATION_MS = 350L;
    private static final long STEP_ZOOM_DURATION_MS = 175L;
    private static final double STEP_ZOOM_COEFFICIENT = 1.75;
    private static final long LENS_INACTIVE_DELAY_MS = 700L;

    public static final AnimationProperties.FloatProperty<InstantCameraZoomSlider> OPEN_ALPHA =
            new AnimationProperties.FloatProperty<>("openAlpha") {
                @Override
                public void setValue(InstantCameraZoomSlider view, float value) {
                    view.setOpenAlpha(value);
                }

                @Override
                public Float get(InstantCameraZoomSlider view) {
                    return view.getOpenAlpha();
                }
            };

    private Backend backend = Backend.NONE;
    private CameraXSession cameraXSession;
    private Camera2Session camera2Session;
    private CameraSession camera1Session;
    private RoundVideoSession roundVideoSession;
    private String roundVideoCameraId;
    private boolean roundVideoFront;
    private ValueAnimator resetZoomAnimator;
    private ValueAnimator stepZoomAnimator;
    private LiveData<ZoomState> cameraXZoomState;
    private float[] camera1ZoomRatios = new float[0];
    private CameraLensStops.LensSet lensSet = CameraLensStops.LensSet.EMPTY;
    private String lensCameraId;
    private boolean lensInactivePending;
    private final CameraLensSwitcher lensSwitcher = new CameraLensSwitcher(this::sendRatioZoom);
    private final Runnable activeLensListener = this::onActiveLensChanged;
    private final Runnable lensInactiveRunnable = () -> {
        lensInactivePending = false;
        if (!lensSet.isExpectedLens(getZoom(), CameraActiveLens.get(lensCameraId))) {
            setSelectedLensInactive(true);
        }
    };
    private float camera1LinearZoom;
    private int camera1ZoomIndex = -1;
    private float defaultZoom = 1.0f;
    private float wideZoom = 1.0f;
    private float displayOneZoom = 1.0f;
    private OnCameraZoomChangeListener cameraZoomChangeListener;
    private int bindRetries;
    private int textureViewSize;
    private float baseTranslationY;
    private boolean animateNextConfiguration;
    private boolean switchingCamera;
    private float openAlpha;
    private float appearProgress;
    private ValueAnimator appearAnimator;
    private float pendingZoom = Float.NaN;
    private float lastAppliedZoom = Float.NaN;
    private long lastZoomAppliedAt;
    private boolean zoomFlushScheduled;

    private final Runnable bindRunnable = this::tryBind;
    private final Runnable zoomFlushRunnable = this::flushPendingZoom;
    private final Observer<ZoomState> cameraXZoomObserver = this::onCameraXZoomStateChanged;

    private final Theme.ResourcesProvider resourcesProvider;
    private BlurredBackgroundDrawable blurBackground;
    private float blurCornerRadius = -1.0f;

    public InstantCameraZoomSlider(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setVisibility(GONE);
        applyAppearProgress();
        setOnZoomChangeListener(this::applyZoom);
        applyTelegramColors();
    }

    public float getOpenAlpha() {
        return openAlpha;
    }

    public void setOpenAlpha(float alpha) {
        if (openAlpha != alpha) {
            openAlpha = alpha;
            setAlpha(openAlpha * appearProgress);
        }
    }

    private void setAppearProgress(float progress) {
        if (appearProgress != progress) {
            appearProgress = progress;
            applyAppearProgress();
        }
    }

    private void applyAppearProgress() {
        setAlpha(openAlpha * appearProgress);
        float scale = APPEAR_SCALE_FROM + (1.0f - APPEAR_SCALE_FROM) * appearProgress;
        setScaleX(scale);
        setScaleY(scale);
    }

    private void showAnimated() {
        setEnabled(true);
        if (!ExteraConfig.getZoomSlider()) {
            hideImmediately();
            return;
        }
        if (getVisibility() != VISIBLE) {
            setVisibility(VISIBLE);
        }
        if (appearAnimator != null || appearProgress >= 1.0f) {
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(appearProgress, 1.0f);
        appearAnimator = animator;
        animator.addUpdateListener(animation -> setAppearProgress((float) animation.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (appearAnimator == animation) {
                    appearAnimator = null;
                    setAppearProgress(1.0f);
                }
            }
        });
        animator.setDuration(APPEAR_DURATION_MS);
        animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animator.start();
    }

    private void hideImmediately() {
        cancelAppearAnimation();
        setAppearProgress(0.0f);
        setVisibility(GONE);
    }

    private void cancelAppearAnimation() {
        ValueAnimator animator = appearAnimator;
        if (animator != null) {
            appearAnimator = null;
            animator.cancel();
        }
    }

    private void applyTelegramColors() {
        int background = Theme.getColor(Theme.key_chat_messagePanelBackground, resourcesProvider);
        int accent = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
        int onAccent = Theme.getColor(Theme.key_chats_actionIcon, resourcesProvider);
        int marks = Theme.getColor(Theme.key_chat_messagePanelText, resourcesProvider);
        setColors(background, marks, accent, accent, onAccent);
        setToggleTextColor(marks);
    }

    public void setBlurBackground(BlurredBackgroundDrawable background) {
        if (blurBackground != background) {
            blurBackground = background;
            blurCornerRadius = -1.0f;
            invalidate();
        }
    }

    @Override
    protected boolean drawPillBackground(Canvas canvas, RectF pillBounds, float cornerRadius) {
        if (blurBackground == null) {
            return false;
        }
        if (blurCornerRadius != cornerRadius) {
            blurCornerRadius = cornerRadius;
            blurBackground.setRadius(cornerRadius);
        }
        blurBackground.setBounds(
                Math.round(pillBounds.left),
                Math.round(pillBounds.top),
                Math.round(pillBounds.right),
                Math.round(pillBounds.bottom)
        );
        blurBackground.draw(canvas);
        return true;
    }

    public void setOnCameraZoomChangeListener(OnCameraZoomChangeListener listener) {
        cameraZoomChangeListener = listener;
    }

    public void bindSession(CameraXSession session) {
        boolean animateConfiguration = session != null
                && getVisibility() == VISIBLE
                && isLaidOut()
                && (switchingCamera
                        || backend == Backend.CAMERA_X && cameraXSession == session);
        switchingCamera = false;
        if (animateConfiguration) {
            prepareZoomConfigurationTransition();
        }
        resetBinding(!animateConfiguration);
        if (session == null) {
            return;
        }
        backend = Backend.CAMERA_X;
        cameraXSession = session;
        animateNextConfiguration = animateConfiguration;
        if (animateConfiguration) {
            setEnabled(false);
            setExpanded(false, true);
        }
        tryBind();
    }

    public void bindSession(Camera2Session session) {
        boolean reconfigureInPlace = session != null && switchingCamera;
        switchingCamera = false;
        resetBinding(!reconfigureInPlace);
        if (session == null) {
            return;
        }
        backend = Backend.CAMERA_2;
        camera2Session = session;
        animateNextConfiguration = reconfigureInPlace;
        tryBind();
    }

    public void bindSession(RoundVideoSession session, RoundVideoSession.CameraCapabilities capabilities) {
        String cameraId = capabilities.getActiveCameraId();
        RoundVideoSession.CameraFacing facing = capabilities.getActiveFacing();
        if (session == null || cameraId == null || facing == null) {
            return;
        }
        if (backend == Backend.ROUND_VIDEO
                && roundVideoSession == session
                && cameraId.equals(roundVideoCameraId)) {
            return;
        }
        boolean animateConfiguration = session != null
                && getVisibility() == VISIBLE
                && isLaidOut()
                && (switchingCamera
                        || backend == Backend.ROUND_VIDEO && roundVideoSession == session);
        switchingCamera = false;
        if (animateConfiguration) {
            prepareZoomConfigurationTransition();
        }
        resetBinding(!animateConfiguration);
        backend = Backend.ROUND_VIDEO;
        roundVideoSession = session;
        roundVideoCameraId = cameraId;
        roundVideoFront = facing == RoundVideoSession.CameraFacing.FRONT;
        animateNextConfiguration = animateConfiguration;
        if (animateConfiguration) {
            setEnabled(false);
            setExpanded(false, true);
        }
        tryBind();
    }

    public void bindSession(CameraSession session, float currentLinearZoom) {
        boolean reconfigureInPlace = session != null && switchingCamera;
        switchingCamera = false;
        resetBinding(!reconfigureInPlace);
        if (session == null) {
            return;
        }
        backend = Backend.CAMERA_1;
        camera1Session = session;
        camera1LinearZoom = Utilities.clamp01(currentLinearZoom);
        animateNextConfiguration = reconfigureInPlace;
        tryBind();
    }

    public void beginCameraSwitch() {
        if (backend == Backend.NONE || getVisibility() != VISIBLE || !isLaidOut()) {
            return;
        }
        switchingCamera = true;
        prepareZoomConfigurationTransition();
        setEnabled(false);
        setExpanded(false, true);
    }

    public void unbindSession() {
        switchingCamera = false;
        resetBinding(true);
    }

    private void resetBinding(boolean hide) {
        setExternalZoomGestureActive(false);
        cancelZoomAnimation();
        resetZoomThrottle();
        backend = Backend.NONE;
        detachCameraXZoomObserver();
        setZoom(getZoom());
        cameraXSession = null;
        camera2Session = null;
        camera1Session = null;
        roundVideoSession = null;
        roundVideoCameraId = null;
        roundVideoFront = false;
        camera1ZoomRatios = new float[0];
        lensSet = CameraLensStops.LensSet.EMPTY;
        lensCameraId = null;
        lensSwitcher.bind(lensSet, null, null, Float.NaN);
        updateLensIndicator();
        camera1ZoomIndex = -1;
        defaultZoom = 1.0f;
        wideZoom = 1.0f;
        displayOneZoom = 1.0f;
        bindRetries = 0;
        removeCallbacks(bindRunnable);
        animateNextConfiguration = false;
        if (hide) {
            cancelZoomConfigurationTransition();
            setEnabled(true);
            setExpanded(false, false);
            hideImmediately();
        }
    }

    public void syncZoom() {
        switch (backend) {
            case CAMERA_X:
                if (cameraXSession != null && cameraXSession.isReady()) {
                    setZoom(cameraXSession.getZoomRatio());
                }
                break;
            case CAMERA_2:
                if (camera2Session != null && camera2Session.isInitiated()) {
                    setZoom(camera2Session.getZoom());
                }
                break;
            case CAMERA_1:
                if (camera1ZoomRatios.length > 1) {
                    setZoom(camera1RatioForLinearZoom(camera1LinearZoom));
                }
                break;
            case ROUND_VIDEO:
            case NONE:
                break;
        }
    }

    public void syncZoom(float zoom) {
        if (backend == Backend.CAMERA_1) {
            camera1LinearZoom = Utilities.clamp01(zoom);
            camera1ZoomIndex = -1;
            if (camera1ZoomRatios.length > 1) {
                setZoom(camera1RatioForLinearZoom(camera1LinearZoom));
            }
        } else if (backend == Backend.CAMERA_2) {
            setZoom(zoom);
        } else {
            syncZoom();
        }
    }

    public void beginExternalZoomGesture() {
        if (!isRatioBackend()) {
            return;
        }
        discardPendingZoom();
        setZoom(getZoom());
    }

    public void beginPinchZoomGesture() {
        beginExternalZoomGesture();
        setExternalZoomGestureActive(true);
    }

    public void endPinchZoomGesture() {
        setExternalZoomGestureActive(false);
    }

    public void beginSteppedZoomGesture() {
        beginExternalZoomGesture();
        setExpanded(true, true);
    }

    public void scaleCameraXZoom(float factor) {
        if (isRatioBackend() && Float.isFinite(factor)) {
            setCameraXZoomRatio(getZoom() * factor);
        }
    }

    public void setCameraXZoomRatio(float zoom) {
        if (!isRatioBackend()) {
            return;
        }
        float sliderZoom = Utilities.clamp(zoom, getMaximumZoom(), getMinimumZoom());
        setZoom(sliderZoom);
        lensSwitcher.setZoom(sliderZoom);
        updateLensIndicator();
    }

    public float getCameraXResetZoom() {
        boolean front = backend == Backend.ROUND_VIDEO
                ? roundVideoFront
                : cameraXSession != null && cameraXSession.isActiveCameraFrontface();
        return ExteraConfig.getStartWithWideAngleCamera() && !front ? wideZoom : defaultZoom;
    }

    public void finishPinchZoomGesture() {
        endPinchZoomGesture();
        if (!ExteraConfig.getStaticZoom()) {
            animateToResetZoom();
        }
    }

    public void animateToResetZoom() {
        if (!isRatioBackend()) {
            return;
        }
        cancelZoomAnimation();
        float currentRatio = getZoom();
        float targetRatio = Utilities.clamp(getCameraXResetZoom(), getMaximumZoom(), getMinimumZoom());
        if (currentRatio < displayOneZoom
                && targetRatio >= displayOneZoom
                && getMinimumZoom() < displayOneZoom) {
            return;
        }
        if (Math.abs(currentRatio - targetRatio) <= 0.001f) {
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(currentRatio, targetRatio);
        resetZoomAnimator = animator;
        animator.addUpdateListener(animation -> setCameraXZoomRatio((float) animation.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (resetZoomAnimator == animation) {
                    resetZoomAnimator = null;
                }
            }
        });
        animator.setDuration(RESET_ZOOM_DURATION_MS);
        animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animator.start();
    }

    public void stepZoom(boolean increase) {
        if (!isRatioBackend() || stepZoomAnimator != null && stepZoomAnimator.isRunning()) {
            return;
        }
        cancelZoomAnimation();
        beginSteppedZoomGesture();
        float currentRatio = getZoom();
        float minRatio = getMinimumZoom();
        float maxRatio = getMaximumZoom();
        float displayOneRatio = Utilities.clamp(displayOneZoom, maxRatio, minRatio);
        int steps = Math.max(1, (int) Math.round(Math.log(maxRatio / displayOneRatio) / Math.log(STEP_ZOOM_COEFFICIENT)));
        float stepFactor = (float) Math.pow(maxRatio / displayOneRatio, 1.0 / steps);
        float targetRatio;
        if (increase) {
            targetRatio = currentRatio < displayOneRatio ? displayOneRatio : currentRatio * stepFactor;
        } else if (currentRatio > displayOneRatio) {
            targetRatio = Math.max(displayOneRatio, currentRatio / stepFactor);
        } else {
            targetRatio = minRatio;
        }
        targetRatio = Utilities.clamp(targetRatio, maxRatio, minRatio);
        ValueAnimator animator = ValueAnimator.ofFloat(currentRatio, targetRatio);
        stepZoomAnimator = animator;
        animator.setDuration(STEP_ZOOM_DURATION_MS);
        animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animator.addUpdateListener(animation -> setCameraXZoomRatio((float) animation.getAnimatedValue()));
        animator.start();
    }

    public void cancelZoomAnimation() {
        ValueAnimator animator = resetZoomAnimator;
        if (animator != null) {
            resetZoomAnimator = null;
            animator.cancel();
        }
        animator = stepZoomAnimator;
        if (animator != null) {
            stepZoomAnimator = null;
            animator.cancel();
        }
    }

    private boolean isRatioBackend() {
        return backend == Backend.CAMERA_X && cameraXSession != null
                || backend == Backend.ROUND_VIDEO && roundVideoSession != null;
    }

    public float[] getOpticalZoomRatios() {
        return lensSet.getRatios();
    }

    public float getDisplayOneZoom() {
        return displayOneZoom;
    }

    private void tryBind() {
        float min;
        float max;
        boolean frontCamera = false;
        switch (backend) {
            case CAMERA_X:
                if (cameraXSession == null || !cameraXSession.isReady()) {
                    retryBinding();
                    return;
                }
                ZoomState zoomState = cameraXSession.camera.getCameraInfo().getZoomState().getValue();
                if (zoomState == null) {
                    retryBinding();
                    return;
                }
                min = zoomState.getMinZoomRatio();
                max = zoomState.getMaxZoomRatio();
                frontCamera = cameraXSession.isActiveCameraFrontface();
                break;
            case CAMERA_2:
                if (camera2Session == null || !camera2Session.isInitiated()) {
                    retryBinding();
                    return;
                }
                min = camera2Session.getMinZoom();
                max = camera2Session.getMaxZoom();
                break;
            case CAMERA_1:
                if (camera1Session == null || !camera1Session.isInitied()) {
                    retryBinding();
                    return;
                }
                camera1ZoomRatios = readCamera1ZoomRatios(camera1Session);
                if (camera1ZoomRatios == null) {
                    camera1ZoomRatios = new float[0];
                    retryBinding();
                    return;
                }
                min = camera1ZoomRatios.length == 0 ? 1.0f : camera1ZoomRatios[0];
                max = camera1ZoomRatios.length == 0
                        ? 1.0f
                        : camera1ZoomRatios[camera1ZoomRatios.length - 1];
                break;
            case ROUND_VIDEO:
                float[] range = RoundVideoCamera2Helper.readZoomRange(getContext(), roundVideoCameraId);
                if (roundVideoSession == null || range == null) {
                    retryBinding();
                    return;
                }
                min = range[0];
                max = range[1];
                frontCamera = roundVideoFront;
                break;
            case NONE:
            default:
                return;
        }

        if (!Float.isFinite(min) || !Float.isFinite(max) || min <= 0.0f || max <= min) {
            animateNextConfiguration = false;
            cancelZoomConfigurationTransition();
            setEnabled(true);
            hideImmediately();
            return;
        }
        defaultZoom = Utilities.clamp(1.0f, max, min);
        wideZoom = min;
        displayOneZoom = 1.0f;
        setDisplayNormalizationFactor(1.0f);
        if (backend == Backend.CAMERA_X) {
            lensCameraId = cameraXSession.getActiveCameraId();
        } else if (backend == Backend.ROUND_VIDEO) {
            lensCameraId = roundVideoCameraId;
        } else {
            lensCameraId = null;
        }
        lensSet = CameraLensStops.lensSet(getContext(), lensCameraId, min);
        float[] toggleStops = CameraLensStops.buildToggleStops(frontCamera, min, max, lensSet.getRatios());
        float[] rulerStops = CameraLensStops.buildRulerStops(min, max, toggleStops);
        float configuredZoom;
        if (backend == Backend.CAMERA_X || backend == Backend.ROUND_VIDEO) {
            configuredZoom = getCameraXResetZoom();
        } else if (backend == Backend.CAMERA_2) {
            configuredZoom = camera2Session.getZoom();
        } else {
            configuredZoom = camera1RatioForLinearZoom(camera1LinearZoom);
        }
        configuredZoom = Utilities.clamp(configuredZoom, max, min);
        boolean animateConfiguration = animateNextConfiguration;
        animateNextConfiguration = false;
        setZoomConfiguration(
                min,
                max,
                toggleStops,
                rulerStops,
                configuredZoom,
                animateConfiguration
        );
        if (!animateConfiguration) {
            setExpanded(false, false);
        }
        if (backend == Backend.CAMERA_X) {
            attachCameraXZoomObserver();
            cameraXSession.setZoomRatio(configuredZoom);
        } else if (backend == Backend.ROUND_VIDEO) {
            roundVideoSession.setZoomRatio(configuredZoom);
        }
        boolean dualMode = backend == Backend.CAMERA_X && cameraXSession.isDualMode();
        lensSwitcher.bind(lensSet, lensCameraId,
                lensCameraId != null && dualMode ? lensCameraId + "/dual" : lensCameraId, configuredZoom);
        showAnimated();
        updateLensIndicator();
    }

    private void retryBinding() {
        if (backend != Backend.NONE && bindRetries++ < BIND_RETRY_LIMIT) {
            postDelayed(bindRunnable, BIND_RETRY_DELAY_MS);
        } else {
            animateNextConfiguration = false;
            cancelZoomConfigurationTransition();
            setEnabled(true);
            hideImmediately();
        }
    }

    private void applyZoom(float zoom) {
        pendingZoom = zoom;
        scheduleZoomFlush();
    }

    private void scheduleZoomFlush() {
        if (!zoomFlushScheduled) {
            zoomFlushScheduled = true;
            postOnAnimation(zoomFlushRunnable);
        }
    }

    private void flushPendingZoom() {
        zoomFlushScheduled = false;
        float zoom = pendingZoom;
        if (Float.isNaN(zoom) || backend == Backend.NONE) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (!Float.isNaN(lastAppliedZoom)) {
            if (zoom == lastAppliedZoom) {
                return;
            }
            if (now - lastZoomAppliedAt < getZoomUpdateIntervalMs()) {
                scheduleZoomFlush();
                return;
            }
        }
        lastAppliedZoom = zoom;
        lastZoomAppliedAt = now;
        sendZoomToCamera(zoom);
    }

    private long getZoomUpdateIntervalMs() {
        int frameRate = 0;
        if (backend == Backend.CAMERA_X && cameraXSession != null) {
            frameRate = cameraXSession.getRecordingFrameRate();
        } else if (backend == Backend.CAMERA_2 && camera2Session != null) {
            frameRate = camera2Session.getRecordingFrameRate();
        } else if (backend == Backend.ROUND_VIDEO && roundVideoSession != null
                && roundVideoSession.getActiveFrameRate() != null) {
            frameRate = roundVideoSession.getActiveFrameRate().getValue();
        }
        if (frameRate <= 0) {
            frameRate = FALLBACK_FRAME_RATE;
        }
        return Math.max(1L, 1000L / frameRate);
    }

    private void discardPendingZoom() {
        removeCallbacks(zoomFlushRunnable);
        zoomFlushScheduled = false;
        pendingZoom = Float.NaN;
    }

    private void resetZoomThrottle() {
        discardPendingZoom();
        lastAppliedZoom = Float.NaN;
        lastZoomAppliedAt = 0L;
    }

    private void sendZoomToCamera(float zoom) {
        float cameraZoom;
        switch (backend) {
            case CAMERA_X:
                if (cameraXSession == null) {
                    return;
                }
                lensSwitcher.setZoom(zoom);
                cameraZoom = cameraXSession.getLinearZoom();
                break;
            case CAMERA_2:
                if (camera2Session == null) {
                    return;
                }
                camera2Session.setZoom(zoom);
                cameraZoom = zoom;
                break;
            case CAMERA_1:
                if (camera1Session == null || camera1ZoomRatios.length < 2) {
                    return;
                }
                int index = camera1ZoomIndexForRatio(zoom);
                camera1LinearZoom = camera1LinearZoomForIndex(index);
                if (index != camera1ZoomIndex) {
                    camera1ZoomIndex = index;
                    camera1Session.setZoom(camera1LinearZoom);
                }
                cameraZoom = camera1LinearZoom;
                break;
            case ROUND_VIDEO:
                if (roundVideoSession == null) {
                    return;
                }
                lensSwitcher.setZoom(zoom);
                cameraZoom = zoom;
                break;
            case NONE:
            default:
                return;
        }
        if (cameraZoomChangeListener != null) {
            cameraZoomChangeListener.onCameraZoomChanged(cameraZoom, true);
        }
        updateLensIndicator();
    }

    private void sendRatioZoom(float zoom) {
        if (backend == Backend.CAMERA_X && cameraXSession != null) {
            cameraXSession.setZoomRatio(zoom);
        } else if (backend == Backend.ROUND_VIDEO && roundVideoSession != null) {
            roundVideoSession.setZoomRatio(zoom);
        }
    }

    private void onActiveLensChanged() {
        updateLensIndicator();
        lensSwitcher.onActiveLensChanged();
    }

    private void updateLensIndicator() {
        if (lensSet.isExpectedLens(getZoom(), CameraActiveLens.get(lensCameraId))) {
            removeCallbacks(lensInactiveRunnable);
            lensInactivePending = false;
            setSelectedLensInactive(false);
        } else if (!lensInactivePending) {
            lensInactivePending = true;
            postDelayed(lensInactiveRunnable, LENS_INACTIVE_DELAY_MS);
        }
    }

    private void attachCameraXZoomObserver() {
        CameraXSession session = cameraXSession;
        if (session == null || session.camera == null) {
            return;
        }
        LiveData<ZoomState> zoomState = session.camera.getCameraInfo().getZoomState();
        if (cameraXZoomState == zoomState) {
            return;
        }
        detachCameraXZoomObserver();
        cameraXZoomState = zoomState;
        cameraXZoomState.observeForever(cameraXZoomObserver);
    }

    private void detachCameraXZoomObserver() {
        if (cameraXZoomState != null) {
            cameraXZoomState.removeObserver(cameraXZoomObserver);
            cameraXZoomState = null;
        }
    }

    private void onCameraXZoomStateChanged(ZoomState zoomState) {
        if (backend != Backend.CAMERA_X || zoomState == null) {
            return;
        }
        if (cameraZoomChangeListener != null) {
            cameraZoomChangeListener.onCameraZoomChanged(zoomState.getLinearZoom(), false);
        }
    }

    @SuppressWarnings("deprecation")
    private static float[] readCamera1ZoomRatios(CameraSession session) {
        try {
            Camera camera = session.cameraInfo != null ? session.cameraInfo.getCamera() : null;
            if (camera == null) {
                return null;
            }
            Camera.Parameters parameters = camera.getParameters();
            if (parameters == null || !parameters.isZoomSupported()) {
                return new float[0];
            }
            List<Integer> ratios = parameters.getZoomRatios();
            if (ratios == null || ratios.size() < 2) {
                return new float[0];
            }
            float[] result = new float[ratios.size()];
            for (int i = 0; i < result.length; i++) {
                Integer ratio = ratios.get(i);
                result[i] = ratio == null ? 1.0f : Math.max(1.0f, ratio / 100.0f);
            }
            return result;
        } catch (Exception ignored) {
            return null;
        }
    }

    private float camera1RatioForLinearZoom(float linearZoom) {
        int last = camera1ZoomRatios.length - 1;
        int index = Math.min((int) (Utilities.clamp01(linearZoom) * last), last);
        return camera1ZoomRatios[index];
    }

    private int camera1ZoomIndexForRatio(float ratio) {
        int closest = 0;
        float distance = Float.MAX_VALUE;
        for (int i = 0; i < camera1ZoomRatios.length; i++) {
            float candidateDistance = Math.abs(camera1ZoomRatios[i] - ratio);
            if (candidateDistance < distance) {
                closest = i;
                distance = candidateDistance;
            }
        }
        return closest;
    }

    private float camera1LinearZoomForIndex(int index) {
        int last = camera1ZoomRatios.length - 1;
        if (index <= 0) {
            return 0.0f;
        }
        if (index >= last) {
            return 1.0f;
        }
        return (index + 0.001f) / last;
    }

    public void setTextureViewSize(int size) {
        if (textureViewSize != size) {
            textureViewSize = size;
            applyPosition();
        }
    }

    public void setBaseTranslationY(float translationY) {
        if (baseTranslationY != translationY) {
            baseTranslationY = translationY;
            applyPosition();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || getAlpha() < 0.5f) {
            return false;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        applyPosition();
    }

    private void applyPosition() {
        setTranslationY(baseTranslationY
                + textureViewSize / 2.0f
                + AndroidUtilities.dp(CIRCLE_GAP_DP + BAR_BLOCK_DP)
                - getMeasuredHeight() / 2.0f);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        CameraActiveLens.addListener(activeLensListener);
        boolean needsBinding = backend != Backend.NONE
                && (getVisibility() != VISIBLE || !isEnabled() || animateNextConfiguration);
        if (needsBinding) {
            bindRetries = 0;
            tryBind();
        } else if (backend == Backend.CAMERA_X) {
            attachCameraXZoomObserver();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        CameraActiveLens.removeListener(activeLensListener);
        removeCallbacks(lensInactiveRunnable);
        lensInactivePending = false;
        lensSwitcher.stop();
        removeCallbacks(bindRunnable);
        discardPendingZoom();
        detachCameraXZoomObserver();
        if (appearAnimator != null) {
            cancelAppearAnimation();
            setAppearProgress(getVisibility() == VISIBLE ? 1.0f : 0.0f);
        }
    }
}