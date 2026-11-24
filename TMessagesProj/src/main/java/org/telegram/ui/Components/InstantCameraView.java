/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static com.exteragram.messenger.debug.DebugConfig.getDebugCameraMetrics;
import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.net.Uri;
import android.opengl.EGL14;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Preview;
import androidx.core.graphics.ColorUtils;
import androidx.media3.exoplayer.ExoPlayer;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.camera.CameraDebugUtils;
import com.exteragram.messenger.camera.CameraXSession;
import com.exteragram.messenger.camera.InstantCameraZoomSlider;
import com.exteragram.messenger.camera.RoundVideoEncoder;
import com.exteragram.messenger.camera.RoundVideoUiHelper;
import com.exteragram.messenger.CameraType;
import com.exteragram.messenger.VideoMessagesCamera;
import com.exteragram.messenger.debug.DebugOverlayView;
import com.exteragram.messenger.debug.DebugConfig;
import com.exteragram.messenger.utils.system.SystemUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.AutoDeleteMediaTask;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.camera.Camera2Session;
import org.telegram.messenger.camera.CameraController;
import org.telegram.messenger.camera.CameraInfo;
import org.telegram.messenger.camera.CameraSession;
import org.telegram.messenger.camera.Size;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.voip.CellFlickerDrawable;
import org.telegram.ui.Stories.recorder.DualCameraView;
import org.telegram.ui.Stories.recorder.FlashViews;
import org.telegram.ui.Stories.recorder.StoryEntry;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

@SuppressLint("ViewConstructor")
public class InstantCameraView extends InstantCameraViewBase implements NotificationCenter.NotificationCenterDelegate {

    private int currentAccount = UserConfig.selectedAccount;
    private InstantViewCameraContainer cameraContainer;
    private Delegate delegate;
    private Paint paint;
    private RectF rect;
    private final FlashViews.ImageViewInvertable switchCameraButton;
    private final FlashViews.ImageViewInvertable flashButton;
    private final FlashViews flashViews;
    private RLottieDrawable flashOnDrawable, flashOffDrawable;
    private RLottieDrawable switchCameraDrawable;
    private ItemOptions itemOptions;
    private ImageView muteImageView;
    private InstantCameraZoomSlider zoomSlider;
    private float progress;
    private CameraInfo selectedCamera;
    private boolean isFrontface = true;
    private volatile boolean cameraReady;
    private AnimatorSet muteAnimation;
    private TLRPC.InputFile file;
    private TLRPC.InputEncryptedFile encryptedFile;
    private byte[] key;
    private byte[] iv;
    private long size;
    private boolean isSecretChat;
    @Nullable
    private VideoEditedInfo videoEditedInfo;
    private VideoPlayer videoPlayer;
    private Bitmap lastBitmap;
    private int recordingGuid;

    private volatile boolean cameraTextureAvailable;
    private final int[] position = new int[2];
    private final int[] cameraTexture = new int[] { Integer.MIN_VALUE, Integer.MIN_VALUE };
    private final int[] oldCameraTexture = new int[1];
    private float cameraTextureAlpha = 1.0f;

    private AnimatorSet animatorSet;

    private float cameraZoom;
    private boolean zoomWas;

    private boolean deviceHasGoodCamera;
    private boolean requestingPermissions;
    private File cameraFile;
    private File previewFile;
    private long recordStartTime;
    private long recordPlusTime;
    private boolean recording;
    private long recordedTime;
    private boolean cancelled;

    private CameraGLThread cameraThread;
    private Size[] previewSize = new Size[2];
    private Size pictureSize;
    private Size aspectRatio = SharedConfig.roundCamera16to9 ? new Size(16, 9) : new Size(4, 3);
    private TextureView textureView;
    private BackupImageView textureOverlayView;
    private final boolean useCamera2 = ExteraConfig.getCameraType() == CameraType.CAMERA_2;
    private CameraSession cameraSession;
    private volatile boolean bothCameras;
    private Camera2Session[] camera2Sessions = new Camera2Session[2];
    private Camera2Session camera2SessionCurrent;
    private boolean needDrawFlickerStub;
    private volatile long legacyCameraSwitchStartedNs;
    private volatile int legacyCameraSwitchSequence;
    private volatile int legacyCameraSwitchTargetSurface;
    private volatile boolean legacyCameraSwitchAwaitingFrame;
    private volatile String legacyCameraSwitchPath;

    private CameraXSession.CameraLifecycle camLifecycle;
    private volatile CameraXSession cameraXSession;
    private final ScaleGestureDetector scaleGestureDetector;

    private boolean isCameraSessionInitiated() {
        if (useCamera2) {
            return camera2SessionCurrent != null && camera2SessionCurrent.isInitiated();
        } else {
            return cameraSession != null && cameraSession.isInitied();
        }
    }

    private float panTranslationY;
    private float animationTranslationY;

    private final float[] mMVPMatrix = new float[16];
    private final float[] mSTMatrix = new float[16];
    private final float[] moldSTMatrix = new float[16];
    private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n" +
                    "uniform mat4 uSTMatrix;\n" +
                    "attribute vec4 aPosition;\n" +
                    "attribute vec4 aTextureCoord;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "void main() {\n" +
                    "   gl_Position = uMVPMatrix * aPosition;\n" +
                    "   vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
                    "}\n";

    private static final String FRAGMENT_SCREEN_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision lowp float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "void main() {\n" +
                    "   gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                    "}\n";

    private FloatBuffer vertexBuffer;
    private FloatBuffer textureBuffer;
    private final float[] textureCoordsData = new float[8];
    private FloatBuffer oldTextureTextureBuffer;
    private float scaleX;
    private float scaleY;

    private Size oldTexturePreviewSize;

    private boolean flipAnimationInProgress;

    private View parentView;
    public boolean opened;

    float pinchStartDistance;

    boolean isInPinchToZoomTouchMode;
    boolean maybePinchToZoomTouchMode;

    private int pointerId1, pointerId2;
    private int textureViewSize;
    private boolean isMessageTransition;
    private boolean updateTextureViewSize;
    private final Theme.ResourcesProvider resourcesProvider;

    private static final int[] ALLOW_BIG_CAMERA_WHITELIST = {
            285904780, // XIAOMI (Redmi Note 7)
            -1394191079 // samsung a31
    };

    private final LinearLayout buttonsLayout;
    private final int buttonsSizePx;


    @SuppressLint("ClickableViewAccessibility")
    public InstantCameraView(Context context, Delegate delegate, Theme.ResourcesProvider resourcesProvider, boolean isNewDesign) {
        super(context);
        buttonsSizePx = dp(isNewDesign ? 24 : 28);

        this.resourcesProvider = resourcesProvider;
        parentView = delegate.getFragmentView();
        setWillNotDraw(false);

        this.delegate = delegate;
        recordingGuid = delegate.getClassGuid();
        isSecretChat = delegate.isSecretChat();
        paint = new Paint(Paint.ANTI_ALIAS_FLAG) {
            @Override
            public void setAlpha(int a) {
                super.setAlpha(a);
                invalidate();
            }
        };
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(3));
        paint.setColor(0xffffffff);

        rect = new RectF();

        flashViews = new FlashViews(getContext(), null, this, null);
        RoundVideoUiHelper.applyFlashSettings(flashViews);

        addView(flashViews.backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        scaleGestureDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
                cancelZoomAnimations();
                zoomSlider.beginPinchZoomGesture();
                return true;
            }

            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                if (cameraXSession != null) {
                    zoomSlider.scaleCameraXZoom(
                            (float) Math.pow(detector.getScaleFactor(), 2)
                    );
                    cameraZoom = cameraXSession.getLinearZoom();
                }
                return true;
            }

            @Override
            public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
                finishZoom();
                super.onScaleEnd(detector);
            }
        });

        cameraContainer = new InstantViewCameraContainer(context) {
            @Override
            public void setRotationY(float rotationY) {
                super.setRotationY(rotationY);
                InstantCameraView.this.invalidate();
            }

            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                InstantCameraView.this.invalidate();
            }
        };
        cameraContainer.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, textureViewSize, textureViewSize);
            }
        });
        cameraContainer.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (cameraXSession != null) {
                    cameraXSession.focusToPoint(event.getX(), event.getY(), v.getWidth(), v.getHeight());
                }
            }
            return false;
        });
        cameraContainer.setClipToOutline(true);
        cameraContainer.setWillNotDraw(false);

        addView(cameraContainer, new LayoutParams(AndroidUtilities.roundPlayingMessageSize, AndroidUtilities.roundPlayingMessageSize, Gravity.CENTER));
        addView(flashViews.foregroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));


        buttonsLayout = new LinearLayout(context);
        buttonsLayout.setPadding(dp(6), dp(6), dp(6), dp(6));

        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        addView(buttonsLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 56, Gravity.LEFT | Gravity.BOTTOM, 1, 0, 0, 0));

        switchCameraButton = new FlashViews.ImageViewInvertable(context);
        switchCameraButton.setScaleType(ImageView.ScaleType.CENTER);
        switchCameraButton.setContentDescription(LocaleController.getString(R.string.AccDescrSwitchCamera));
        buttonsLayout.addView(switchCameraButton, LayoutHelper.createLinear(44, 44));
        switchCameraButton.setOnClickListener(v -> {
            if (!isCameraReady()) {
                return;
            }
            boolean isCameraXDual = ExteraConfig.getCameraType() == CameraType.CAMERA_X && cameraXSession != null && cameraXSession.isDualMode();
            if (ExteraConfig.getCameraType() != CameraType.CAMERA_X) {
                if (!bothCameras) {
                    switchCamera();
                }
            } else {
                if (!isCameraXDual) {
                    switchCameraX();
                }
            }

            if (switchCameraDrawable != null) {
                switchCameraDrawable.setCurrentFrame(0);
                switchCameraDrawable.start();
            }

            if (ExteraConfig.getCameraType() == CameraType.CAMERA_X && !isCameraXDual) {
                return;
            }

            flipAnimationInProgress = true;
            ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
            valueAnimator.setDuration(450);
            valueAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            final boolean[] didSwap = new boolean[1];
            Runnable doSwap = () -> {
                if (bothCameras && ExteraConfig.getCameraType() != CameraType.CAMERA_X) {
                    switchCamera();
                } else if (isCameraXDual) {
                    switchCameraX();
                }
            };
            cameraContainer.setCameraDistance(cameraContainer.getMeasuredHeight() * 8f);
            textureOverlayView.setCameraDistance(textureOverlayView.getMeasuredHeight() * 8f);
            valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float p = (float) valueAnimator.getAnimatedValue();
                    if (p > 0.5f && !didSwap[0]) {
                        didSwap[0] = true;
                        doSwap.run();
                    }
                    float rotation = p < 0.5f ? p : p - 1f;
                    rotation *= 180;
                    cameraContainer.setRotationY(rotation);
                    textureOverlayView.setRotationY(rotation);
                }
            });
            valueAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    if (!didSwap[0]) {
                        didSwap[0] = true;
                        doSwap.run();
                    }
                    cameraContainer.setRotationY(0f);
                    textureOverlayView.setRotationY(0f);
                    flipAnimationInProgress = false;
                    invalidate();
                }
            });
            valueAnimator.start();
        });

        flashButton = new FlashViews.ImageViewInvertable(context);
        flashButton.setScaleType(ImageView.ScaleType.CENTER);
        buttonsLayout.addView(flashButton, LayoutHelper.createLinear(44, 44));
        flashButton.setOnClickListener(v -> {
            flashing = !flashing;
            updateFlash();
        });
        flashButton.setOnLongClickListener(e -> {
            if (!isFrontface || !isCameraReady()) {
                return false;
            }
            boolean wasEnabled = flashing;
            if (!flashing) {
                flashing = true;
                updateFlash();
            }
            itemOptions = RoundVideoUiHelper.showFlashOptions(this, resourcesProvider, flashButton, flashViews, () -> {
                if (!wasEnabled) {
                    flashing = false;
                    updateFlash();
                }
            });
            return true;
        });
        updateFlash();

        if (!isNewDesign) {
            flashViews.add(switchCameraButton);
            flashViews.add(flashButton);
        } else if (!resourcesProvider.isDark()) {
            switchCameraButton.setInvert(0.6f);
            flashButton.setInvert(0.6f);
        }

        muteImageView = new ImageView(context);
        muteImageView.setScaleType(ImageView.ScaleType.CENTER);
        muteImageView.setImageResource(R.drawable.video_mute);
        muteImageView.setAlpha(0.0f);
        addView(muteImageView, LayoutHelper.createFrame(48, 48, Gravity.CENTER));

        Paint blackoutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackoutPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, 40));
        textureOverlayView = new BackupImageView(getContext()) {

            CellFlickerDrawable flickerDrawable = new CellFlickerDrawable();

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (needDrawFlickerStub) {
                    flickerDrawable.setParentWidth(textureViewSize);
                    AndroidUtilities.rectTmp.set(0, 0, textureViewSize, textureViewSize);
                    float rad = AndroidUtilities.rectTmp.width() / 2f;
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, rad, rad, blackoutPaint);
                    AndroidUtilities.rectTmp.inset(dp(1), dp(1));
                    flickerDrawable.draw(canvas, AndroidUtilities.rectTmp, rad, null);
                    invalidate();
                }
            }
        };
        addView(textureOverlayView, new LayoutParams(AndroidUtilities.roundPlayingMessageSize, AndroidUtilities.roundPlayingMessageSize, Gravity.CENTER));

        zoomSlider = new InstantCameraZoomSlider(context, resourcesProvider);
        zoomSlider.setOnCameraZoomChangeListener((zoom, fromSlider) -> {
            if (fromSlider) {
                cancelZoomAnimations();
            }
            cameraZoom = zoom;
        });
        zoomSlider.setOpenAlpha(0.0f);
        addView(zoomSlider, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        if (getDebugCameraMetrics()) {
            DebugOverlayView cameraDebugOverlayView = new DebugOverlayView(context);
            cameraDebugOverlayView.setDataSource(this::populateCameraDebugOverlay);
            addView(cameraDebugOverlayView, DebugOverlayView.createLayoutParams());
        }

        setVisibilityFromPause = false;
        setVisibility(INVISIBLE);
    }

    public void setButtonsBackground(BlurredBackgroundDrawableViewFactory factory, BlurredBackgroundColorProvider colorProvider) {
        BlurredBackgroundDrawable drawable = factory.create(buttonsLayout, colorProvider);
        drawable.setPadding(dp(6));
        drawable.setRadius(dp(21));
        buttonsLayout.setBackground(drawable);

        zoomSlider.setBlurBackground(factory.create(zoomSlider, colorProvider));
    }

    private Boolean wasFlashing;
    private boolean flashing;
    private boolean frontFlashing;
    private void updateFlash() {
        final boolean shouldFrontFlash = flashing && recording && isFrontface;
        if (frontFlashing != shouldFrontFlash) {
            frontFlashing = shouldFrontFlash;
            if (frontFlashing) {
                flashViews.flashIn(null);
            } else {
                flashViews.flashOut();
            }
        }

        boolean wantTorch = flashing && recording;

        if (ExteraConfig.getCameraType() != CameraType.CAMERA_X) {
            if (useCamera2) {
                if (camera2SessionCurrent != null) {
                    camera2SessionCurrent.setFlash(wantTorch && !isFrontface);
                }
            } else {
                if (cameraSession != null) {
                    cameraSession.setTorchEnabled(wantTorch && !isFrontface);
                }
            }
        } else {
            if (isCameraReady() && cameraXSession != null) {
                cameraXSession.setTorchEnabled(wantTorch);
            }
        }

        if (flashButton != null && (wasFlashing == null || wasFlashing != flashing)) {
            flashButton.setContentDescription(LocaleController.getString(flashing ? R.string.AccDescrCameraFlashOff : R.string.AccDescrCameraFlashOn));
            if (!flashing) {
                if (flashOnDrawable == null) {
                    flashOnDrawable = new RLottieDrawable(R.raw.roundcamera_flash_on, buttonsSizePx, buttonsSizePx);
                    flashOnDrawable.setCallback(flashButton);
                }
                flashButton.setImageDrawable(flashOnDrawable);
                if (wasFlashing == null) {
                    flashOnDrawable.setCurrentFrame(flashOnDrawable.getFramesCount() - 1);
                } else {
                    flashOnDrawable.setCurrentFrame(0);
                    flashOnDrawable.start();
                }
            } else {
                if (flashOffDrawable == null) {
                    flashOffDrawable = new RLottieDrawable(R.raw.roundcamera_flash_off, buttonsSizePx, buttonsSizePx);
                    flashOffDrawable.setCallback(flashButton);
                }
                flashButton.setImageDrawable(flashOffDrawable);
                if (wasFlashing == null) {
                    flashOffDrawable.setCurrentFrame(flashOffDrawable.getFramesCount() - 1);
                } else {
                    flashOffDrawable.setCurrentFrame(0);
                    flashOffDrawable.start();
                }
            }
            wasFlashing = flashing;
        }
    }

    private int internalPaddingBottom;

    public void setInternalPadding(int padding) {
        internalPaddingBottom = padding;
        setPadding(0, 0, 0, padding);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (updateTextureViewSize) {
            int newSize;
            if ((MeasureSpec.getSize(heightMeasureSpec) - getPaddingBottom()) > MeasureSpec.getSize(widthMeasureSpec) * 1.3f) {
                newSize = AndroidUtilities.roundPlayingMessageSize;
            } else {
                newSize = AndroidUtilities.roundMessageSize;
            }
            if (newSize != textureViewSize) {
                textureViewSize = newSize;
                textureOverlayView.getLayoutParams().width = textureOverlayView.getLayoutParams().height = textureViewSize;
                cameraContainer.getLayoutParams().width = cameraContainer.getLayoutParams().height = textureViewSize;
                ((LayoutParams) muteImageView.getLayoutParams()).topMargin = textureViewSize / 2 - dp(24);
                zoomSlider.setTextureViewSize(textureViewSize);
                textureOverlayView.setRoundRadius(textureViewSize / 2);
                cameraContainer.invalidateOutline();
            }
            updateTextureViewSize = false;
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        final int flashWidthSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY);
        final int flashHeightSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY);
        flashViews.backgroundView.measure(flashWidthSpec, flashHeightSpec);
        flashViews.foregroundView.measure(flashWidthSpec, flashHeightSpec);
    }

    private boolean checkPointerIds(MotionEvent ev) {
        if (ev.getPointerCount() < 2) {
            return false;
        }
        if (pointerId1 == ev.getPointerId(0) && pointerId2 == ev.getPointerId(1)) {
            return true;
        }
        if (pointerId1 == ev.getPointerId(1) && pointerId2 == ev.getPointerId(0)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        getParent().requestDisallowInterceptTouchEvent(true);
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (getVisibility() != VISIBLE) {
            animationTranslationY = getMeasuredHeight() / 2f;
            updateTranslationY();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(currentAccount).addWeakObserver(this, NotificationCenter.fileUploaded);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploaded);
        if (flashViews != null) {
            flashViews.flashOut();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.fileUploaded) {
            final String location = (String) args[0];
            if (cameraFile != null && cameraFile.getAbsolutePath().equals(location)) {
                file = (TLRPC.InputFile) args[1];
                encryptedFile = (TLRPC.InputEncryptedFile) args[2];
                size = (Long) args[5];
                if (encryptedFile != null) {
                    key = (byte[]) args[3];
                    iv = (byte[]) args[4];
                }
            }
        }
    }

    public void destroy(boolean async) {
        cancelZoomAnimations();
        zoomSlider.unbindSession();
        if (ExteraConfig.getCameraType() != CameraType.CAMERA_X) {
            if (useCamera2) {
                for (int a = 0; a < camera2Sessions.length; ++a) {
                    if (camera2Sessions[a] != null) {
                        camera2Sessions[a].destroy(async);
                        camera2Sessions[a] = null;
                    }
                }
                camera2SessionCurrent = null;
            } else {
                if (cameraSession != null) {
                    cameraSession.destroy();
                    CameraController.getInstance().close(cameraSession, !async ? new CountDownLatch(1) : null, null);
                    cameraSession = null;
                }
            }
        } else {
            releaseCameraXSession();
        }
    }

    private void releaseCameraXSession() {
        if (cameraXSession == null) {
            return;
        }
        zoomSlider.unbindSession();
        try {
            cameraXSession.closeCamera();
        } catch (Exception e) {
            FileLog.e(e);
        }
        cameraXSession = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float x = cameraContainer.getX();
        float y = cameraContainer.getY();
        rect.set(x - dp(8), y - dp(8), x + cameraContainer.getMeasuredWidth() + dp(8), y + cameraContainer.getMeasuredHeight() + dp(8));
        if (recording) {
            recordedTime = System.currentTimeMillis() - recordStartTime + recordPlusTime;
            progress = Math.min(1f, recordedTime / (float) SystemUtils.getRoundVideoMaxDurationMs());
            invalidate();
        }

        if (progress != 0) {
            canvas.save();
            if (!flipAnimationInProgress) {
                canvas.scale(cameraContainer.getScaleX(), cameraContainer.getScaleY(), rect.centerX(), rect.centerY());
            }
            canvas.drawArc(rect, -90, 360 * progress, false, paint);
            canvas.restore();
        }
    }

    private void populateCameraDebugOverlay(DebugOverlayView.ContentBuilder builder) {
        builder.title("InstantCamera")
                .kv("front", isFrontface)
                .kv("recording", recording)
                .kv("ready", cameraReady)
                .kv("dual", bothCameras)
                .kv("surface", surfaceIndex)
                .kv("flash", flashing)
                .kv("frontFlash", frontFlashing)
                .kv("texture", cameraTextureAvailable)
                .line("preview0=" + CameraDebugUtils.formatCameraSize(previewSize[0]))
                .line("preview1=" + CameraDebugUtils.formatCameraSize(previewSize[1]))
                .line("zoom.stops=" + CameraDebugUtils.formatZoomStops(zoomSlider.getToggleStops()))
                .line("zoom.lenses=" + CameraDebugUtils.formatZoomStops(zoomSlider.getOpticalZoomRatios()));

        if (ExteraConfig.getCameraType() == CameraType.CAMERA_X) {
            builder.section("CameraX");
            if (cameraXSession == null) {
                builder.line("session=null");
                return;
            }
            builder.kv("cx.init", cameraXSession.isInitiated())
                    .kv("cx.ready", cameraXSession.isReady())
                    .kv("cx.dual", cameraXSession.isDualMode())
                    .kv("cx.front", cameraXSession.isFrontface())
                    .line("cx.zoom=" + CameraDebugUtils.safeCameraXZoomRatio(cameraXSession) + " [" + CameraDebugUtils.safeCameraXMinZoomRatio(cameraXSession) + ".." + CameraDebugUtils.safeCameraXMaxZoomRatio(cameraXSession) + "]")
                    .line("cx.fpsRanges=" + CameraDebugUtils.getCameraXSupportedFpsRanges(cameraXSession))
                    .line("bound=" + CameraDebugUtils.getCameraXBoundCameraList(cameraXSession))
                    .line("avail=" + CameraDebugUtils.getCameraXAvailableCameraList(cameraXSession))
                    .line("phys=" + CameraDebugUtils.getCameraXPhysicalCameraList(cameraXSession));
        } else if (useCamera2) {
            builder.section("Camera2");
            if (camera2SessionCurrent == null) {
                builder.line("session=null");
                return;
            }
            builder.kv("c2.init", camera2SessionCurrent.isInitiated())
                    .kv("c2.flash", camera2SessionCurrent.getFlash())
                    .kv("c2.both", bothCameras)
                    .line("c2.zoom=" + camera2SessionCurrent.getZoom() + " [" + camera2SessionCurrent.getMinZoom() + ".." + camera2SessionCurrent.getMaxZoom() + "]")
                    .line("c2.fpsRanges=" + CameraDebugUtils.getCamera2SupportedFpsRanges(camera2SessionCurrent))
                    .line("ids=" + CameraDebugUtils.getCamera2CameraList(getContext()));
        } else {
            builder.section("Camera1")
                    .kv("c1.init", cameraSession != null && cameraSession.isInitied())
                    .kv("c1.zoom", cameraZoom)
                    .line("c1.fpsRanges=" + CameraDebugUtils.getLegacySupportedFpsRanges(cameraSession))
                    .line("ids=" + CameraDebugUtils.getLegacyCameraList());
        }
    }

    private boolean setVisibilityFromPause;
    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);

        buttonsLayout.setAlpha(0.0f);
        cameraContainer.setAlpha(0.0f);
        textureOverlayView.setAlpha(0.0f);
        muteImageView.setAlpha(0.0f);
        zoomSlider.setOpenAlpha(0.0f);
        muteImageView.setScaleX(1.0f);
        muteImageView.setScaleY(1.0f);
        cameraContainer.setScaleX(setVisibilityFromPause ? 1f : 0.1f);
        cameraContainer.setScaleY(setVisibilityFromPause ? 1f : 0.1f);
        textureOverlayView.setScaleX(setVisibilityFromPause ? 1f : 0.1f);
        textureOverlayView.setScaleY(setVisibilityFromPause ? 1f : 0.1f);
        if (cameraContainer.getMeasuredWidth() != 0) {
            cameraContainer.setPivotX(cameraContainer.getMeasuredWidth() / 2);
            cameraContainer.setPivotY(cameraContainer.getMeasuredHeight() / 2);
            textureOverlayView.setPivotX(textureOverlayView.getMeasuredWidth() / 2);
            textureOverlayView.setPivotY(textureOverlayView.getMeasuredHeight() / 2);
        }
        try {
            if (visibility == VISIBLE) {
                ((Activity) getContext()).getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                ((Activity) getContext()).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void togglePause() {
        if (recording) {
            cancelled = recordedTime < 800;
            recording = false;
            updateFlash();
            if (cameraThread != null) {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.recordStopped, recordingGuid, cancelled ? 4 : 2);
                saveLastCameraBitmap();
                cameraThread.shutdown(cancelled ? 0 : 2, true, 0, 0, cancelled ? 0 : -2, 0);
                cameraThread = null;
            }
            if (cancelled) {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.audioRecordTooShort, recordingGuid, true, (int) recordedTime);
                startAnimation(false, false);
                MediaController.getInstance().requestRecordAudioFocus(false);
            } else if (videoEncoder != null) {
                if (previewFile != null) {
                    previewFile.delete();
                }
                previewFile = StoryEntry.makeCacheFile(currentAccount, true);
                videoEncoder.pause(previewFile);
            }
        } else if (videoEncoder != null) {
            // recording resumes when the new camera GL thread calls startRecording
            hideCamera(false);
            if (videoPlayer != null) {
                videoPlayer.releasePlayer(true);
                videoPlayer = null;
            }
            showCamera(true);
            try {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } catch (Exception ignore) {}
            AndroidUtilities.lockOrientation(delegate.getParentActivity());
            invalidate();
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.recordResumed);
        }
    }

    @Override
    public void setFrontface(boolean isFrontface) {
        this.isFrontface = isFrontface;
    }

    public boolean isPaused() {
        return !recording;
    }

    public void showCamera(boolean fromPaused) {
        if (textureView != null) {
            return;
        }

        camLifecycle = new CameraXSession.CameraLifecycle();

        if (switchCameraDrawable == null) {
            switchCameraDrawable = new RLottieDrawable(R.raw.roundcamera_flip, buttonsSizePx, buttonsSizePx);
            switchCameraDrawable.setCurrentFrame(0);
            switchCameraDrawable.setCallback(switchCameraButton);
        }
        switchCameraButton.setImageDrawable(switchCameraDrawable);

        textureOverlayView.setAlpha(1.0f);
        textureOverlayView.invalidate();
        if (lastBitmap == null) {
            try {
                File file = new File(ApplicationLoader.getFilesDirFixed(), "icthumb.jpg");
                lastBitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            } catch (Throwable ignore) {

            }
        }
        if (lastBitmap != null) {
            textureOverlayView.setImageBitmap(lastBitmap);
        } else {
            textureOverlayView.setImageResource(R.drawable.icplaceholder);
        }
        cameraReady = false;
        selectedCamera = null;
        if (!fromPaused) {
            if (ExteraConfig.getVideoMessagesCamera() != VideoMessagesCamera.ASK) {
                isFrontface = ExteraConfig.getVideoMessagesCamera() == VideoMessagesCamera.FRONT;
            }
            updateFlash();
            recordedTime = 0;
            progress = 0;
        }
        cancelled = false;
        file = null;
        encryptedFile = null;
        key = null;
        iv = null;
        needDrawFlickerStub = true;

        if (!initCamera()) {
            return;
        }
        if (MediaController.getInstance().getPlayingMessageObject() != null) {
            if (MediaController.getInstance().getPlayingMessageObject().isVideo() || MediaController.getInstance().getPlayingMessageObject().isRoundVideo()) {
                MediaController.getInstance().cleanupPlayer(true, true);
            } else if (SharedConfig.pauseMusicOnRecord) {
                MediaController.getInstance().pauseByRewind();
            }
        }

        if (!fromPaused) {
            cameraFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_DOCUMENT), System.currentTimeMillis() + "_" + SharedConfig.getLastLocalId() + ".mp4") {
                @Override
                public boolean delete() {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.e("delete camera file");
                    }
                    return super.delete();
                }
            };
        }

        SharedConfig.saveConfig();
        AutoDeleteMediaTask.lockFile(cameraFile);

        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("InstantCamera show round camera " + cameraFile.getAbsolutePath());
        }

        if (useCamera2) {
            bothCameras = DualCameraView.roundDualAvailableStatic(getContext());
            if (bothCameras) {
                for (int a = 0; a < 2; ++a) {
                    if (camera2Sessions[a] == null) {
                        camera2Sessions[a] = Camera2Session.create(a == 0, SystemUtils.getRoundVideoResolution(), SystemUtils.getRoundVideoResolution());
                        if (camera2Sessions[a] != null) {
                            camera2Sessions[a].setRecordingVideo(true);
                            previewSize[a] = new Size(camera2Sessions[a].getPreviewWidth(), camera2Sessions[a].getPreviewHeight());
                        }
                    }
                }
                updateFlash();
                camera2SessionCurrent = camera2Sessions[isFrontface ? 0 : 1];
                if (camera2SessionCurrent != null && camera2Sessions[isFrontface ? 1 : 0] == null) {
                    bothCameras = false;
                }
                surfaceIndex = bothCameras && !isFrontface ? 1 : 0;
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("InstantCamera legacy switch capability: dual=" + bothCameras
                            + ", initialFacing=" + (isFrontface ? "FRONT" : "BACK")
                            + ", frontSession=" + (camera2Sessions[0] != null)
                            + ", backSession=" + (camera2Sessions[1] != null));
                }
                if (camera2SessionCurrent == null) return;
            } else {
                surfaceIndex = 0;
                camera2SessionCurrent = camera2Sessions[isFrontface ? 0 : 1] = Camera2Session.create(isFrontface, SystemUtils.getRoundVideoResolution(), SystemUtils.getRoundVideoResolution());
                if (camera2SessionCurrent == null) return;
                camera2SessionCurrent.setRecordingVideo(true);
                previewSize[0] = new Size(camera2SessionCurrent.getPreviewWidth(), camera2SessionCurrent.getPreviewHeight());
            }
            bindCamera2ZoomSlider(camera2SessionCurrent);
        } else if (ExteraConfig.getCameraType() == CameraType.CAMERA_X) {
            bothCameras = CameraXSession.isRoundDualAvailable(getContext());
            surfaceIndex = bothCameras && !isFrontface ? 1 : 0;
            if (previewSize[0] == null) {
                int size = SystemUtils.getRoundVideoResolution();
                previewSize[0] = new Size(size, size);
            }
            if (previewSize[1] == null) {
                previewSize[1] = previewSize[0];
            }
        } else {
            bothCameras = false;
            surfaceIndex = 0;
        }
        textureView = new TextureView(getContext());
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("InstantCamera camera surface available");
                }
                if (cameraThread == null && surface != null) {
                    if (cancelled) {
                        return;
                    }
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("InstantCamera start create thread");
                    }
                    cameraThread = new CameraGLThread(surface, width, height);
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, final int width, final int height) {
                if (cameraThread != null) {
                    cameraThread.surfaceWidth = width;
                    cameraThread.surfaceHeight = height;
                    cameraThread.updateScale();
                }
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                cancelZoomAnimations();
                zoomSlider.unbindSession();
                if (cameraThread != null) {
                    cameraThread.shutdown(0, true, 0, 0, 0, 0);
                    cameraThread = null;
                }
                if (ExteraConfig.getCameraType() != CameraType.CAMERA_X) {
                    if (useCamera2) {
                        for (int a = 0; a < camera2Sessions.length; ++a) {
                            if (camera2Sessions[a] != null) {
                                camera2Sessions[a].destroy(false);
                                camera2Sessions[a] = null;
                            }
                        }
                        camera2SessionCurrent = null;
                    } else {
                        if (cameraSession != null) {
                            CameraController.getInstance().close(cameraSession, null, null);
                            cameraSession = null;
                        }
                    }
                } else {
                    releaseCameraXSession();
                }
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
        cameraContainer.addView(textureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        updateTextureViewSize = true;
        setVisibilityFromPause = fromPaused;
        setVisibility(VISIBLE);

        startAnimation(true, fromPaused);
        MediaController.getInstance().requestRecordAudioFocus(true);
    }

    public InstantViewCameraContainer getCameraContainer() {
        return cameraContainer;
    }

    public void startAnimation(boolean open, boolean fromPaused) {
        dispatchAnimationState(open, fromPaused);
        if (animatorSet != null) {
            animatorSet.removeAllListeners();
            animatorSet.cancel();
        }
        PipRoundVideoView pipRoundVideoView = PipRoundVideoView.getInstance();
        if (pipRoundVideoView != null) {
            pipRoundVideoView.showTemporary(!open);
        }
        if (open && !opened) {
            cameraContainer.setTranslationX(0);
            textureOverlayView.setTranslationX(0);

            animationTranslationY = fromPaused ? 0 : getMeasuredHeight() / 2f;
            updateTranslationY();
        }
        opened = open;
        if (parentView != null) {
            parentView.invalidate();
        }
        animatorSet = new AnimatorSet();
        float toX = 0;
        if (!open) {
            toX = recordedTime > 300 ? dp(24) - getMeasuredWidth() / 2f : 0;
        }
        ValueAnimator translationYAnimator = ValueAnimator.ofFloat(open ? 1f : 0f, open ? 0 : 1f);
        translationYAnimator.addUpdateListener(animation -> {
            animationTranslationY = fromPaused ? 0 : (getMeasuredHeight() / 2f) * (float) animation.getAnimatedValue();
            updateTranslationY();
        });
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(buttonsLayout, View.ALPHA, open ? 1.0f : 0.0f),
                ObjectAnimator.ofFloat(zoomSlider, InstantCameraZoomSlider.OPEN_ALPHA, open ? 1.0f : 0.0f),
                ObjectAnimator.ofFloat(muteImageView, View.ALPHA, 0.0f),
                ObjectAnimator.ofInt(paint, AnimationProperties.PAINT_ALPHA, open ? 255 : 0),
                ObjectAnimator.ofFloat(cameraContainer, View.ALPHA, open ? 1.0f : 0.0f),
                ObjectAnimator.ofFloat(cameraContainer, View.SCALE_X, open ? 1.0f : 0.1f),
                ObjectAnimator.ofFloat(cameraContainer, View.SCALE_Y, open ? 1.0f : 0.1f),
                ObjectAnimator.ofFloat(cameraContainer, View.TRANSLATION_X, toX),
                ObjectAnimator.ofFloat(textureOverlayView, View.ALPHA, open ? 1.0f : 0.0f),
                ObjectAnimator.ofFloat(textureOverlayView, View.SCALE_X, open ? 1.0f : 0.1f),
                ObjectAnimator.ofFloat(textureOverlayView, View.SCALE_Y, open ? 1.0f : 0.1f),
                ObjectAnimator.ofFloat(textureOverlayView, View.TRANSLATION_X, toX),
                translationYAnimator
        );
        if (!open) {
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animation.equals(animatorSet)) {
                        hideCamera(true);
                        setVisibilityFromPause = false;
                        setVisibility(INVISIBLE);
                    }
                }
            });
        } else {
            setTranslationX(0);
        }
        animatorSet.setDuration(180);
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.start();
    }

    private void updateTranslationY() {
        textureOverlayView.setTranslationY(animationTranslationY + panTranslationY);
        cameraContainer.setTranslationY(animationTranslationY + panTranslationY);
        zoomSlider.setBaseTranslationY(animationTranslationY + panTranslationY);
    }

    public RectF getCameraRect() {
        cameraContainer.getLocationOnScreen(position);
        return new RectF(
                position[0],
                position[1],
                position[0] + cameraContainer.getWidth(),
                position[1] + cameraContainer.getHeight()
        );
    }

    public void changeVideoPreviewState(int state, float progress) {
        if (videoPlayer == null) {
            return;
        }
        if (state == 0) {
            startProgressTimer();
            videoPlayer.play();
        } else if (state == 1) {
            stopProgressTimer();
            videoPlayer.pause();
        } else if (state == 2) {
            videoPlayer.seekTo((long) (progress * videoPlayer.getDuration()));
        }
    }

    public void send(int state, boolean notify, int scheduleDate, int scheduleRepeatPeriod, int ttl, long effectId, long stars) {
        if (textureView == null) {
            return;
        }
        stopProgressTimer();
        if (videoPlayer != null) {
            videoPlayer.releasePlayer(true);
            videoPlayer = null;
        }
        if (state == 4) {
            if (videoEncoder != null && recordedTime > 800) {
                requestStopRecording(ENCODER_SEND_SEND, new SendOptions(notify, scheduleDate, scheduleRepeatPeriod, ttl, effectId, stars));
                return;
            }
            if (BuildVars.DEBUG_VERSION && !cameraFile.exists()) {
                FileLog.e(new RuntimeException("file not found :( round video"));
            }
            if (videoEditedInfo == null) {
                videoEditedInfo = new VideoEditedInfo();
                videoEditedInfo.startTime = -1;
                videoEditedInfo.endTime = -1;
            }
            if (videoEditedInfo.needConvert()) {
                file = null;
                encryptedFile = null;
                key = null;
                iv = null;
                double totalDuration = videoEditedInfo.estimatedDuration;
                long startTime = videoEditedInfo.startTime >= 0 ? videoEditedInfo.startTime : 0;
                long endTime = videoEditedInfo.endTime >= 0 ? videoEditedInfo.endTime : videoEditedInfo.estimatedDuration;
                videoEditedInfo.estimatedDuration = endTime - startTime;
                videoEditedInfo.estimatedSize = Math.max(1, (long) (size * (videoEditedInfo.estimatedDuration / totalDuration)));
                videoEditedInfo.bitrate = SystemUtils.getRoundVideoBitrate() * 1024;
                if (videoEditedInfo.startTime > 0) {
                    videoEditedInfo.startTime *= 1000;
                }
                if (videoEditedInfo.endTime > 0) {
                    videoEditedInfo.endTime *= 1000;
                }
                FileLoader.getInstance(currentAccount).cancelFileUpload(cameraFile.getAbsolutePath(), false);
            } else {
                videoEditedInfo.estimatedSize = Math.max(1, size);
            }
            videoEditedInfo.file = file;
            videoEditedInfo.encryptedFile = encryptedFile;
            videoEditedInfo.key = key;
            videoEditedInfo.iv = iv;
            MediaController.PhotoEntry entry = new MediaController.PhotoEntry(0, 0, 0, cameraFile.getAbsolutePath(), 0, true, 0, 0, 0);
            entry.ttl = ttl;
            entry.effectId = effectId;
            delegate.sendMedia(entry, videoEditedInfo, notify, scheduleDate, scheduleRepeatPeriod, false, stars);
            if (scheduleDate != 0) {
                startAnimation(false, false);
            }
            MediaController.getInstance().requestRecordAudioFocus(false);
        } else {
            cancelled = recordedTime < 800;
            recording = false;
            flashing = false;
            updateFlash();
            int reason;
            if (cancelled) {
                reason = 4;
            } else {
                reason = state == 3 ? 2 : 5;
            }
            if (cameraThread != null) {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.recordStopped, recordingGuid, reason);
                int send;
                if (cancelled) {
                    send = 0;
                } else if (state == 3) {
                    send = 2;
                } else {
                    send = 1;
                }
                saveLastCameraBitmap();
                cameraThread.shutdown(send, notify, scheduleDate, scheduleRepeatPeriod, ttl, effectId);
                cameraThread = null;
            }
            if (cancelled) {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.audioRecordTooShort, recordingGuid, true, (int) recordedTime);
                startAnimation(false, false);
                MediaController.getInstance().requestRecordAudioFocus(false);
            }
        }
    }

    private void saveLastCameraBitmap() {
        Bitmap bitmap = textureView.getBitmap();
        if (bitmap != null && bitmap.getPixel(0, 0) != 0) {
            final Bitmap thumb = Bitmap.createScaledBitmap(bitmap, 50, 50, true);
            lastBitmap = thumb;
            Utilities.blurBitmap(thumb, 7);
            Utilities.globalQueue.postRunnable(() -> {
                try {
                    File file = new File(ApplicationLoader.getFilesDirFixed(), "icthumb.jpg");
                    FileOutputStream stream = new FileOutputStream(file);
                    thumb.compress(Bitmap.CompressFormat.JPEG, 87, stream);
                    stream.close();
                } catch (Throwable ignore) {

                }
            });
        }
    }

    public void cancel(boolean byGesture) {
        stopProgressTimer();
        if (videoPlayer != null) {
            videoPlayer.releasePlayer(true);
            videoPlayer = null;
        }
        if (textureView == null) {
            return;
        }
        cancelled = true;
        recording = false;
        flashing = false;
        updateFlash();
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.recordStopped, recordingGuid, byGesture ? 0 : 6);
        if (cameraThread != null) {
            saveLastCameraBitmap();
            cameraThread.shutdown(0, true, 0, 0, 0, 0);
            cameraThread = null;
        } else if (videoEncoder != null) {
            requestStopRecording(ENCODER_SEND_CANCEL, new SendOptions(true, 0, 0, 0, 0, 0));
        }
        if (cameraFile != null) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.e("delete camera file by cancel");
            }
            cameraFile.delete();
            AutoDeleteMediaTask.unlockFile(cameraFile);
            cameraFile = null;
        }
        MediaController.getInstance().requestRecordAudioFocus(false);
        startAnimation(false, false);
        invalidate();
    }

    public View getButtonsLayout() {
        return buttonsLayout;
    }

    @Override
    public InstantCameraZoomSlider getZoomSlider() {
        return zoomSlider;
    }

    public View getMuteImageView() {
        return muteImageView;
    }

    public Paint getPaint() {
        return paint;
    }

    public void hideCamera(boolean async) {
        destroy(async);
        cameraContainer.setTranslationX(0);
        textureOverlayView.setTranslationX(0);
        animationTranslationY = 0;
        updateTranslationY();
        MediaController.getInstance().resumeByRewind();

        if (textureView != null) {
            ViewGroup parent = (ViewGroup) textureView.getParent();
            if (parent != null) {
                parent.removeView(textureView);
            }
        }
        textureView = null;
        cameraContainer.setImageReceiver(null);
    }

    private void bindCamera2ZoomSlider(Camera2Session session) {
        if (session == null) {
            zoomSlider.unbindSession();
            return;
        }
        cameraZoom = session.getZoom();
        zoomSlider.bindSession(session);
        session.whenDone(() -> {
            if (camera2SessionCurrent == session
                    && ExteraConfig.getCameraType() == CameraType.CAMERA_2) {
                cameraZoom = session.getZoom();
                zoomSlider.bindSession(session);
            }
        });
    }

    private void switchCamera() {
        if (cameraThread == null) {
            return;
        }
        cancelZoomAnimations();
        zoomSlider.beginCameraSwitch();
        legacyCameraSwitchStartedNs = SystemClock.elapsedRealtimeNanos();
        legacyCameraSwitchSequence++;
        legacyCameraSwitchTargetSurface = useCamera2 && bothCameras
                ? 1 - surfaceIndex
                : 0;
        legacyCameraSwitchAwaitingFrame = true;
        legacyCameraSwitchPath = useCamera2
                ? bothCameras ? "DUAL_PREOPENED" : "SEQUENTIAL_CAMERA2"
                : "SEQUENTIAL_CAMERA1";
        logLegacyCameraSwitchStage("requested: from="
                + (isFrontface ? "FRONT" : "BACK")
                + ", to=" + (isFrontface ? "BACK" : "FRONT")
                + ", currentSurface=" + surfaceIndex
                + ", targetSurface=" + legacyCameraSwitchTargetSurface);
        if (!(useCamera2 && bothCameras)) {
            saveLastCameraBitmap();
            if (lastBitmap != null) {
                needDrawFlickerStub = false;
                textureOverlayView.setImageBitmap(lastBitmap);
                textureOverlayView.setAlpha(1f);
            }
        }
        isFrontface ^= true;
        if (ExteraConfig.getRememberLastUsedCamera() && ExteraConfig.getVideoMessagesCamera() != VideoMessagesCamera.ASK) {
            ExteraConfig.setVideoMessagesCamera(isFrontface ? VideoMessagesCamera.FRONT : VideoMessagesCamera.REAR);
        }
        updateFlash();
        if (useCamera2) {
            if (bothCameras) {
                camera2SessionCurrent = camera2Sessions[isFrontface ? 0 : 1];
                bindCamera2ZoomSlider(camera2SessionCurrent);
                cameraThread.flipSurfaces();
                return;
            } else {
                if (camera2SessionCurrent != null) {
                    camera2SessionCurrent.destroy(false);
                    camera2SessionCurrent = null;
                    camera2Sessions[isFrontface ? 1 : 0] = null;
                }
                camera2SessionCurrent = camera2Sessions[isFrontface ? 0 : 1] = Camera2Session.create(isFrontface, SystemUtils.getRoundVideoResolution(), SystemUtils.getRoundVideoResolution());
                if (camera2SessionCurrent == null) {
                    legacyCameraSwitchAwaitingFrame = false;
                    logLegacyCameraSwitchStage("failed: Camera2Session.create returned null");
                    return;
                }
                camera2SessionCurrent.setRecordingVideo(true);
                previewSize[0] = new Size(camera2SessionCurrent.getPreviewWidth(), camera2SessionCurrent.getPreviewHeight());
                cameraThread.setCurrentSession(camera2SessionCurrent);
                bindCamera2ZoomSlider(camera2SessionCurrent);
            }
        } else {
            if (cameraSession != null) {
                cameraSession.destroy();
                CameraController.getInstance().close(cameraSession, null, null);
                cameraSession = null;
            }
        }
        initCamera();
        cameraReady = false;
        cameraZoom = useCamera2 && camera2SessionCurrent != null
                ? camera2SessionCurrent.getZoom()
                : 0.0f;
        cameraThread.reinitForNewCamera();
    }

    private void switchCameraX() {
        if (ExteraConfig.getCameraType() != CameraType.CAMERA_X) {
            return;
        }
        cancelZoomAnimations();

        boolean actualDualMode = cameraXSession != null && cameraXSession.isDualMode();

        isFrontface ^= true;
        if (ExteraConfig.getRememberLastUsedCamera() && ExteraConfig.getVideoMessagesCamera() != VideoMessagesCamera.ASK) {
            ExteraConfig.setVideoMessagesCamera(isFrontface ? VideoMessagesCamera.FRONT : VideoMessagesCamera.REAR);
        }
        updateFlash();

        if (actualDualMode) {
            if (cameraThread != null) {
                cameraThread.flipSurfaces();
            }

            if (cameraXSession != null) {
                cameraXSession.switchCamera();
                zoomSlider.bindSession(cameraXSession);
            }
            return;
        }

        cameraReady = false;
        cameraZoom = 0;
        if (cameraXSession != null) {
            cameraXSession.switchCamera();
            zoomSlider.bindSession(cameraXSession);
        } else {
            cameraThread.reinitForNewCamera();
        }
    }

    private void logLegacyCameraSwitchStage(String stage) {
        if (!BuildVars.LOGS_ENABLED) return;
        long startedNs = legacyCameraSwitchStartedNs;
        long elapsedMs = startedNs == 0L
                ? 0L
                : (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000L;
        FileLog.d("InstantCamera legacy switch[" + legacyCameraSwitchSequence + "] "
                + stage + ", path=" + legacyCameraSwitchPath
                + ", elapsedMs=" + elapsedMs
                + ", thread=" + Thread.currentThread().getName());
    }

    // Old Camera1 API
    @Deprecated
    private boolean initCamera() {
        if (useCamera2) {
            return true;
        }
        ArrayList<CameraInfo> cameraInfos = CameraController.getInstance().getCameras();
        if (cameraInfos == null) {
            return false;
        }
        CameraInfo notFrontface = null;
        for (int a = 0; a < cameraInfos.size(); a++) {
            CameraInfo cameraInfo = cameraInfos.get(a);
            if (!cameraInfo.isFrontface()) {
                notFrontface = cameraInfo;
            }
            if (isFrontface && cameraInfo.isFrontface() || !isFrontface && !cameraInfo.isFrontface()) {
                selectedCamera = cameraInfo;
                break;
            } else {
                notFrontface = cameraInfo;
            }
        }
        if (selectedCamera == null) {
            selectedCamera = notFrontface;
        }
        if (selectedCamera == null) {
            return false;
        }

        ArrayList<Size> previewSizes = selectedCamera.getPreviewSizes();
        ArrayList<Size> pictureSizes = selectedCamera.getPictureSizes();

        previewSize[0] = chooseOptimalSize(previewSizes);
        pictureSize = chooseOptimalSize(pictureSizes);

        if (previewSize[0].mWidth != pictureSize.mWidth) {
            boolean found = false;
            for (int a = previewSizes.size() - 1; a >= 0; a--) {
                Size preview = previewSizes.get(a);
                for (int b = pictureSizes.size() - 1; b >= 0; b--) {
                    Size picture = pictureSizes.get(b);
                    if (preview.mWidth >= pictureSize.mWidth && preview.mHeight >= pictureSize.mHeight && preview.mWidth == picture.mWidth && preview.mHeight == picture.mHeight) {
                        previewSize[0] = preview;
                        pictureSize = picture;
                        found = true;
                        break;
                    }
                }
                if (found) {
                    break;
                }
            }

            if (!found) {
                for (int a = previewSizes.size() - 1; a >= 0; a--) {
                    Size preview = previewSizes.get(a);
                    for (int b = pictureSizes.size() - 1; b >= 0; b--) {
                        Size picture = pictureSizes.get(b);
                        if (preview.mWidth >= 360 && preview.mHeight >= 360 && preview.mWidth == picture.mWidth && preview.mHeight == picture.mHeight) {
                            previewSize[0] = preview;
                            pictureSize = picture;
                            found = true;
                            break;
                        }
                    }
                    if (found) {
                        break;
                    }
                }
            }
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("InstantCamera preview w = " + previewSize[0].mWidth + " h = " + previewSize[0].mHeight);
        }
        return true;
    }

    @Deprecated // used for old Camera1 API only
    private Size chooseOptimalSize(ArrayList<Size> previewSizes) {
        ArrayList<Size> sortedSizes = new ArrayList<>();
        boolean allowBigSizeCamera = allowBigSizeCamera();
        int maxVideoSize = allowBigSizeCamera ? 1440 : 1200;
        if (Build.MANUFACTURER.equalsIgnoreCase("Samsung")) {
            //1440 lead to gl crashes on samsung s9
            maxVideoSize = 1200;
        }
        for (int i = 0; i < previewSizes.size(); i++) {
            if (Math.max(previewSizes.get(i).mHeight, previewSizes.get(i).mWidth) <= maxVideoSize && Math.min(previewSizes.get(i).mHeight, previewSizes.get(i).mWidth) >= 320) {
                sortedSizes.add(previewSizes.get(i));
            }
        }
        if (sortedSizes.isEmpty() || !allowBigSizeCamera()) {
            ArrayList<Size> sizes = sortedSizes;
            if (!sortedSizes.isEmpty()) {
                sizes = sortedSizes;
            } else {
                sizes = previewSizes;
            }
            if (Build.MANUFACTURER.equalsIgnoreCase("Xiaomi")) {
                return CameraController.chooseOptimalSize(sizes, 640, 480, aspectRatio, false);
            } else {
                return CameraController.chooseOptimalSize(sizes, 480, 270, aspectRatio, false);
            }
        }
        Collections.sort(sortedSizes, (o1, o2) -> {
            float a1 = Math.abs(1f - Math.min(o1.mHeight, o1.mWidth) / (float) Math.max(o1.mHeight, o1.mWidth));
            float a2 = Math.abs(1f - Math.min(o2.mHeight, o2.mWidth) / (float) Math.max(o2.mHeight, o2.mWidth));

            if (a1 < a2) {
                return -1;
            } else if (a1 > a2) {
                return 1;
            }
            return 0;
        });
        return sortedSizes.get(0);
    }

    @Deprecated // used for old Camera1 API only
    private boolean allowBigSizeCamera() {
        if (SharedConfig.bigCameraForRound) {
            return true;
        }
        if (SharedConfig.deviceIsAboveAverage()) {
            return true;
        }
        int devicePerformanceClass = Math.max(SharedConfig.getDevicePerformanceClass(), SharedConfig.getLegacyDevicePerformanceClass());
        if (devicePerformanceClass == SharedConfig.PERFORMANCE_CLASS_HIGH) {
            return true;
        }
        int hash = (Build.MANUFACTURER + " " + Build.DEVICE).toUpperCase().hashCode();
        for (int i = 0; i < ALLOW_BIG_CAMERA_WHITELIST.length; ++i) {
            if (ALLOW_BIG_CAMERA_WHITELIST[i] == hash) {
                return true;
            }
        }
        return false;
    }

    @Deprecated // used for old Camera1 API only
    public static boolean allowBigSizeCameraDebug() {
        int devicePerformanceClass = Math.max(SharedConfig.getDevicePerformanceClass(), SharedConfig.getLegacyDevicePerformanceClass());
        if (devicePerformanceClass == SharedConfig.PERFORMANCE_CLASS_HIGH) {
            return true;
        }
        int hash = (Build.MANUFACTURER + " " + Build.DEVICE).toUpperCase().hashCode();
        for (int i = 0; i < ALLOW_BIG_CAMERA_WHITELIST.length; ++i) {
            if (ALLOW_BIG_CAMERA_WHITELIST[i] == hash) {
                return true;
            }
        }
        return false;
    }

    private void createCamera(final int index, final SurfaceTexture surfaceTexture) {
        AndroidUtilities.runOnUIThread(() -> {
            if (cameraThread == null) {
                return;
            }
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("InstantCamera create camera session " + index);
            }
            if (legacyCameraSwitchAwaitingFrame
                    && index == legacyCameraSwitchTargetSurface) {
                logLegacyCameraSwitchStage("camera open submitted: surface=" + index);
            }

            if (ExteraConfig.getCameraType() != CameraType.CAMERA_X) {
                if (useCamera2) {
                    if (bothCameras) {
                        if (camera2Sessions[index] != null) {
                            camera2Sessions[index].open(surfaceTexture);
                        }
                    } else {
                        if (index == 1) return;
                        cameraThread.setCurrentSession(camera2SessionCurrent);
                        camera2SessionCurrent.open(surfaceTexture);
                    }
                } else {
                    if (index == 1) return;
                    surfaceTexture.setDefaultBufferSize(previewSize[0].getWidth(), previewSize[0].getHeight());
                    CameraSession session = new CameraSession(selectedCamera, previewSize[0], pictureSize, ImageFormat.JPEG, true);
                    cameraSession = session;
                    updateFlash();
                    cameraThread.setCurrentSession(session);
                    CameraController.getInstance().openRound(session, surfaceTexture, () -> {
                        if (cameraSession == session) {
                            updateFlash();

                            boolean updateScale = false;
                            try {
                                Camera.Size size = session.getCurrentPreviewSize();
                                if (size.width != previewSize[0].getWidth() || size.height != previewSize[0].getHeight()) {
                                    previewSize[0] = new Size(size.width, size.height);
                                    FileLog.d("InstantCamera change preview size to w = " + previewSize[0].getWidth() + " h = " + previewSize[0].getHeight());
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                            }

                            try {
                                Camera.Size size = session.getCurrentPictureSize();
                                if (size.width != pictureSize.getWidth() || size.height != pictureSize.getHeight()) {
                                    pictureSize = new Size(size.width, size.height);
                                    FileLog.d("InstantCamera change picture size to w = " + pictureSize.getWidth() + " h = " + pictureSize.getHeight());
                                    updateScale = true;
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("InstantCamera camera initied");
                            }
                            session.setInitied();
                            zoomSlider.bindSession(session, cameraZoom);
                            if (updateScale) {
                                if (cameraThread != null) {
                                    cameraThread.reinitForNewCamera();
                                }
                            }
                        }
                    }, () -> {
                        if (cameraThread != null && cameraSession == session) {
                            cameraThread.setCurrentSession(session);
                        }
                    });
                }
            } else {
                Preview.SurfaceProvider provider = CameraXSession.createSurfaceProvider(getContext(), surfaceTexture, (width, height) -> {
                    CameraGLThread thread = cameraThread;
                    if (thread != null) {
                        thread.setCameraXPreviewSize(index, width, height);
                    }
                });

                if (index == 0) {
                    CameraXSession session = new CameraXSession(camLifecycle, provider);
                    cameraXSession = session;
                    session.initCamera(getContext(), isFrontface, bothCameras, () -> {
                        if (cameraXSession != session) {
                            return;
                        }
                        boolean actualDualMode = session.isDualMode();
                        if (!actualDualMode && bothCameras && surfaceIndex != 0 && cameraThread != null) {
                            cameraThread.flipSurfaces();
                        }
                        bothCameras = actualDualMode;
                        if (cameraThread != null) cameraThread.setOrientation();
                        zoomSlider.bindSession(session);
                    });
                    updateFlash();
                } else if (bothCameras) {
                    CameraXSession session = cameraXSession;
                    if (session != null) {
                        session.setSecondSurfaceProvider(provider);
                    }
                }
            }
        });
    }

    private int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == 0) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.e(GLES20.glGetShaderInfoLog(shader));
            }
            GLES20.glDeleteShader(shader);
            shader = 0;
        }
        return shader;
    }

    private Timer progressTimer;

    private void startProgressTimer() {
        if (progressTimer != null) {
            try {
                progressTimer.cancel();
                progressTimer = null;
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        progressTimer = new Timer();
        progressTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        if (videoPlayer != null && videoEditedInfo != null && videoEditedInfo.endTime > 0 && videoPlayer.getCurrentPosition() >= videoEditedInfo.endTime) {
                            videoPlayer.seekTo(videoEditedInfo.startTime > 0 ? videoEditedInfo.startTime : 0);
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                });
            }
        }, 0, 17);
    }

    private void stopProgressTimer() {
        if (progressTimer != null) {
            try {
                progressTimer.cancel();
                progressTimer = null;
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public void onPanTranslationUpdate(float y) {
        panTranslationY = y / 2f;
        updateTranslationY();
    }

    public TextureView getTextureView() {
        return textureView;
    }

    public void setIsMessageTransition(boolean isMessageTransition) {
        this.isMessageTransition = isMessageTransition;
    }

    public void resetCameraFile() {
        cameraFile = null;
    }

    private RoundVideoEncoder videoEncoder;
    private int encoderFrameRate = 30;

    private int resolveEncoderFrameRate() {
        if (!ExteraConfig.getExtendedFramesPerSecond()) {
            return 30;
        }
        if (useCamera2) {
            return camera2SessionCurrent != null ? camera2SessionCurrent.getRecordingFrameRate() : 30;
        }
        if (ExteraConfig.getCameraType() == CameraType.CAMERA_X) {
            CameraXSession session = cameraXSession;
            return session != null ? session.getRecordingFrameRate() : 30;
        }
        return 30;
    }

    private Bitmap firstFrameThumb;
    private volatile int surfaceIndex;

    public class CameraGLThread extends DispatchQueue {

        private final static int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
        private final static int EGL_OPENGL_ES2_BIT = 4;
        private SurfaceTexture surfaceTexture;
        private EGL10 egl10;
        private EGLDisplay eglDisplay;
        private EGLContext eglContext;
        private EGLSurface eglSurface;
        private boolean initied;

        private Object currentSession;

        private final SurfaceTexture[] cameraSurface = new SurfaceTexture[2];

        private final int DO_RENDER_MESSAGE = 0;
        private final int DO_SHUTDOWN_MESSAGE = 1;
        private final int DO_REINIT_MESSAGE = 2;
        private final int DO_SETSESSION_MESSAGE = 3;
        private final int DO_FLIP = 4;
        private final int DO_SETORIENTATION_MESSAGE = 5;
        private final int DO_SET_CAMERAX_PREVIEW_SIZE = 6;

        private int drawProgram;
        private int vertexMatrixHandle;
        private int textureMatrixHandle;
        private int positionHandle;
        private int textureHandle;

        private boolean recording;

        private Integer cameraId = 0;

        private int surfaceWidth;
        private int surfaceHeight;

        private volatile boolean running = true;

        private final AtomicInteger pendingRenderMask = new AtomicInteger(0);
        private final int[] surfaceGeneration = new int[2];
        private final RoundVideoEncoder.FrameSnapshot frameSnapshotScratch = new RoundVideoEncoder.FrameSnapshot();

        public CameraGLThread(SurfaceTexture surface, int surfaceWidth, int surfaceHeight) {
            super("CameraGLThread", false);
            surfaceTexture = surface;

            this.surfaceWidth = surfaceWidth;
            this.surfaceHeight = surfaceHeight;
            start();
            postRunnable(() -> initied = initGL());
        }

        private void updateScale() {
            int width, height;
            if (previewSize[surfaceIndex] != null) {
                width = previewSize[surfaceIndex].getWidth();
                height = previewSize[surfaceIndex].getHeight();
            } else {
                return;
            }

            float scale = surfaceWidth / (float) Math.min(width, height);

            width *= scale;
            height *= scale;

            if (width == height) {
                scaleX = 1f;
                scaleY = 1f;
            } else if (width > height) {
                scaleX = 1.0f;
                scaleY = width / (float) surfaceHeight;
            } else {
                scaleX = height / (float) surfaceWidth;
                scaleY = 1.0f;
            }
            FileLog.d("InstantCamera camera scaleX = " + scaleX + " scaleY = " + scaleY);

        }

        private void updateTextureBuffer() {
            updateScale();

            float tX = 1.0f / scaleX / 2.0f;
            float tY = 1.0f / scaleY / 2.0f;
            float[] texData = {
                    0.5f - tX, 0.5f - tY,
                    0.5f + tX, 0.5f - tY,
                    0.5f - tX, 0.5f + tY,
                    0.5f + tX, 0.5f + tY
            };

            System.arraycopy(texData, 0, textureCoordsData, 0, 8);
            textureBuffer = ByteBuffer.allocateDirect(texData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            textureBuffer.put(texData).position(0);
        }

        private void attachFrameListener(int index) {
            final SurfaceTexture st = cameraSurface[index];
            final int generation = surfaceGeneration[index];
            st.setOnFrameAvailableListener(texture -> {
                if (!running || cameraSurface[index] != texture || surfaceGeneration[index] != generation) {
                    return;
                }
                cameraTextureAvailable = true;
                requestRender(index == 0, index == 1);
            }, getHandler());
        }

        private void fillFrameSnapshot(RoundVideoEncoder.FrameSnapshot snapshot, int index, int frameCameraId) {
            SurfaceTexture st = cameraSurface[index];
            snapshot.sourceTimestampNs = st.getTimestamp();
            snapshot.arrivalTimeNs = System.nanoTime();
            snapshot.cameraId = frameCameraId;
            snapshot.surfaceIndex = index;
            snapshot.textureId = cameraTexture[index];
            st.getTransformMatrix(snapshot.stMatrix);
            System.arraycopy(mMVPMatrix, 0, snapshot.mvpMatrix, 0, 16);
            System.arraycopy(textureCoordsData, 0, snapshot.textureCoords, 0, 8);
            Size size = previewSize[index];
            snapshot.previewWidth = size != null ? size.getWidth() : 0;
            snapshot.previewHeight = size != null ? size.getHeight() : 0;
        }

        private boolean initGL() {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("InstantCamera start init gl");
            }
            egl10 = (EGL10) EGLContext.getEGL();

            eglDisplay = egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("InstantCamera eglGetDisplay failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }

            int[] version = new int[2];
            if (!egl10.eglInitialize(eglDisplay, version)) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("InstantCamera eglInitialize failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }

            int[] configsCount = new int[1];
            EGLConfig[] configs = new EGLConfig[1];
            int[] configSpec = new int[]{
                    EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                    EGL10.EGL_RED_SIZE, 8,
                    EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_ALPHA_SIZE, 0,
                    EGL10.EGL_DEPTH_SIZE, 0,
                    EGL10.EGL_STENCIL_SIZE, 0,
                    EGL10.EGL_NONE
            };
            EGLConfig eglConfig;
            if (!egl10.eglChooseConfig(eglDisplay, configSpec, configs, 1, configsCount)) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("InstantCamera eglChooseConfig failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            } else if (configsCount[0] > 0) {
                eglConfig = configs[0];
            } else {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("InstantCamera eglConfig not initialized");
                }
                finish();
                return false;
            }

            int[] attrib_list = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE};
            eglContext = egl10.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
            if (eglContext == null) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("InstantCamera eglCreateContext failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }

            if (surfaceTexture instanceof SurfaceTexture) {
                eglSurface = egl10.eglCreateWindowSurface(eglDisplay, eglConfig, surfaceTexture, null);
            } else {
                finish();
                return false;
            }

            if (eglSurface == null || eglSurface == EGL10.EGL_NO_SURFACE) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("InstantCamera createWindowSurface failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }
            if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("InstantCamera eglMakeCurrent failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }

            float[] verticesData = {
                    -1.0f, -1.0f, 0,
                    1.0f, -1.0f, 0,
                    -1.0f, 1.0f, 0,
                    1.0f, 1.0f, 0
            };

            vertexBuffer = ByteBuffer.allocateDirect(verticesData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            vertexBuffer.put(verticesData).position(0);

            updateTextureBuffer();

            android.opengl.Matrix.setIdentityM(mSTMatrix, 0);

            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SCREEN_SHADER);
            if (vertexShader != 0 && fragmentShader != 0) {
                drawProgram = GLES20.glCreateProgram();
                GLES20.glAttachShader(drawProgram, vertexShader);
                GLES20.glAttachShader(drawProgram, fragmentShader);
                GLES20.glLinkProgram(drawProgram);
                int[] linkStatus = new int[1];
                GLES20.glGetProgramiv(drawProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
                if (linkStatus[0] == 0) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.e("InstantCamera failed link shader");
                    }
                    GLES20.glDeleteProgram(drawProgram);
                    drawProgram = 0;
                } else {
                    positionHandle = GLES20.glGetAttribLocation(drawProgram, "aPosition");
                    textureHandle = GLES20.glGetAttribLocation(drawProgram, "aTextureCoord");
                    vertexMatrixHandle = GLES20.glGetUniformLocation(drawProgram, "uMVPMatrix");
                    textureMatrixHandle = GLES20.glGetUniformLocation(drawProgram, "uSTMatrix");
                }
            } else {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("InstantCamera failed creating shader");
                }
                finish();
                return false;
            }

            android.opengl.Matrix.setIdentityM(mMVPMatrix, 0);

            GLES20.glGenTextures(2, cameraTexture, 0);
            for (int a = 0; a < 2; ++a) {
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture[a]);
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

                cameraSurface[a] = new SurfaceTexture(cameraTexture[a]);
                attachFrameListener(a);
                if (ExteraConfig.getCameraType() != CameraType.CAMERA_X || a == 0 || bothCameras) {
                    createCamera(a, cameraSurface[a]);
                }
            }

            if (BuildVars.LOGS_ENABLED) {
                FileLog.e("InstantCamera gl initied");
            }

            return true;
        }

        public void reinitForNewCamera() {
            Handler handler = getHandler();
            if (handler != null) {
                sendMessage(handler.obtainMessage(DO_REINIT_MESSAGE), 0);
            }
        }

        public void finish() {
            surfaceGeneration[0]++;
            surfaceGeneration[1]++;
            pendingRenderMask.set(0);
            if (cameraSurface != null) {
                for (int a = 0; a < 2; ++a) {
                    if (cameraSurface[a] != null) {
                        cameraSurface[a].release();
                        cameraSurface[a] = null;
                    }
                }
            }
            cameraTextureAvailable = false;
            if (eglSurface != null && eglContext != null) {
                if (!eglContext.equals(egl10.eglGetCurrentContext()) || !eglSurface.equals(egl10.eglGetCurrentSurface(EGL10.EGL_DRAW))) {
                    egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);
                }
                if (cameraTexture != null && cameraTexture[0] != Integer.MIN_VALUE) {
                    GLES20.glDeleteTextures(1, cameraTexture, 0);
                    cameraTexture[0] = Integer.MIN_VALUE;
                }
                if (cameraTexture != null && cameraTexture[1] != Integer.MIN_VALUE) {
                    GLES20.glDeleteTextures(1, cameraTexture, 1);
                    cameraTexture[1] = Integer.MIN_VALUE;
                }
            }
            if (eglSurface != null) {
                egl10.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
                egl10.eglDestroySurface(eglDisplay, eglSurface);
                eglSurface = null;
            }
            if (eglContext != null) {
                egl10.eglDestroyContext(eglDisplay, eglContext);
                eglContext = null;
            }
            if (eglDisplay != null) {
                egl10.eglTerminate(eglDisplay);
                eglDisplay = null;
            }
        }

        public void setCurrentSession(CameraSession session) {
            Handler handler = getHandler();
            if (handler != null) {
                sendMessage(handler.obtainMessage(DO_SETSESSION_MESSAGE, session), 0);
            }
        }

        public void setCurrentSession(Camera2Session session) {
            Handler handler = getHandler();
            if (handler != null) {
                sendMessage(handler.obtainMessage(DO_SETSESSION_MESSAGE, session), 0);
            }
        }

        public void setOrientation() {
            Handler handler = getHandler();
            if (handler != null) {
                sendMessage(handler.obtainMessage(DO_SETORIENTATION_MESSAGE), 0);
            }
        }

        public void setCameraXPreviewSize(int index, int width, int height) {
            if (index < 0 || index >= previewSize.length || width <= 0 || height <= 0) {
                return;
            }
            Handler handler = getHandler();
            if (handler != null) {
                sendMessage(handler.obtainMessage(DO_SET_CAMERAX_PREVIEW_SIZE, index, 0, new Size(width, height)), 0);
            }
        }

        public void flipSurfaces() {
            Handler handler = getHandler();
            if (handler != null) {
                sendMessage(handler.obtainMessage(DO_FLIP), 0);
            }
        }

        private void onDraw(Integer cameraId, boolean updateTexImage1, boolean updateTexImage2) {
            if (!initied) {
                return;
            }

            if (this.cameraId != null && !this.cameraId.equals(cameraId)) {
                return;
            }

            if (!eglContext.equals(egl10.eglGetCurrentContext()) || !eglSurface.equals(egl10.eglGetCurrentSurface(EGL10.EGL_DRAW))) {
                if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.e("eglMakeCurrent failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                    }
                    return;
                }
            }
            if (updateTexImage1) {
                try {
                    cameraSurface[0].updateTexImage();
                } catch (Throwable e) {
                    FileLog.e(e);
                    return;
                }
            }
            if (updateTexImage2) {
                try {
                    cameraSurface[1].updateTexImage();
                } catch (Throwable e) {
                    FileLog.e(e);
                    return;
                }
            }

            boolean activeSurfaceUpdated = surfaceIndex == 0 ? updateTexImage1 : updateTexImage2;
            if (ExteraConfig.getCameraType() == CameraType.CAMERA_X && bothCameras && !activeSurfaceUpdated) {
                return;
            }
            boolean completesCameraSwitch = legacyCameraSwitchAwaitingFrame
                    && surfaceIndex == legacyCameraSwitchTargetSurface
                    && (surfaceIndex == 0 && updateTexImage1
                    || surfaceIndex == 1 && updateTexImage2);

            boolean captureFirstFrameThumb = false;
            if (!recording) {
                if (videoEncoder == null) {
                    sentMedia = false;
                    videoConvertFirstWrite = true;
                    encoderFinishRequested = false;
                    encoderSend = ENCODER_SEND_CANCEL;
                    encoderSendOptions = null;
                    keyframeThumbs.clear();
                    if (generateKeyframeThumbsQueue != null) {
                        generateKeyframeThumbsQueue.cleanupQueue();
                        generateKeyframeThumbsQueue.recycle();
                    }
                    generateKeyframeThumbsQueue = new DispatchQueue("keyframes_thumb_queue");
                    videoEncoder = new RoundVideoEncoder(new EncoderRenderer(), encoderCallback, isSecretChat);
                    encoderFrameRate = resolveEncoderFrameRate();
                }
                if (videoEncoder.isStarted()) {
                    if (!cameraReady) {
                        cameraReady = true;
                        AndroidUtilities.runOnUIThread(() -> textureOverlayView.animate().setDuration(120).alpha(0.0f).setInterpolator(new DecelerateInterpolator()).start());
                    }
                } else {
                    captureFirstFrameThumb = true;
                }
                AndroidUtilities.runOnUIThread(() -> {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
                });
                encoderFile = cameraFile;
                videoEncoder.startRecording(cameraFile, EGL14.eglGetCurrentContext(), encoderFrameRate);
                recording = true;
                int orientation = 0;
                if (ExteraConfig.getCameraType() != CameraType.CAMERA_X) {
                    if (currentSession instanceof CameraSession) {
                        orientation = ((CameraSession) currentSession).getCurrentOrientation();
                    } else if (currentSession instanceof Camera2Session) {
                        orientation = ((Camera2Session) currentSession).getCurrentOrientation();
                    }
                } else {
                    CameraXSession session = cameraXSession;
                    if (session != null) {
                        orientation = session.getDisplayOrientation();
                    }
                }
                if (orientation == 90 || orientation == 270) {
                    float temp = scaleX;
                    scaleX = scaleY;
                    scaleY = temp;
                }
                recording = true;
                updateFlash();
            }

            if (videoEncoder != null && (surfaceIndex == 0 && updateTexImage1 || surfaceIndex == 1 && updateTexImage2)) {
                fillFrameSnapshot(frameSnapshotScratch, surfaceIndex, bothCameras ? surfaceIndex : cameraId);
                videoEncoder.frameAvailable(frameSnapshotScratch);
            }

            cameraSurface[surfaceIndex].getTransformMatrix(mSTMatrix);

            GLES20.glUseProgram(drawProgram);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture[surfaceIndex]);

            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer);
            GLES20.glEnableVertexAttribArray(positionHandle);

            GLES20.glVertexAttribPointer(textureHandle, 2, GLES20.GL_FLOAT, false, 8, textureBuffer);
            GLES20.glEnableVertexAttribArray(textureHandle);

            GLES20.glUniformMatrix4fv(textureMatrixHandle, 1, false, mSTMatrix, 0);
            GLES20.glUniformMatrix4fv(vertexMatrixHandle, 1, false, mMVPMatrix, 0);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            GLES20.glDisableVertexAttribArray(positionHandle);
            GLES20.glDisableVertexAttribArray(textureHandle);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
            GLES20.glUseProgram(0);

            egl10.eglSwapBuffers(eglDisplay, eglSurface);
            if (completesCameraSwitch && legacyCameraSwitchAwaitingFrame) {
                legacyCameraSwitchAwaitingFrame = false;
                logLegacyCameraSwitchStage("first target frame presented: surface="
                        + surfaceIndex);
            }

            if (captureFirstFrameThumb) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (textureView == null) {
                        return;
                    }
                    if (firstFrameThumb != null) {
                        firstFrameThumb.recycle();
                        firstFrameThumb = null;
                    }
                    firstFrameThumb = textureView.getBitmap();
                });
            }
        }

        @Override
        public void handleMessage(Message inputMessage) {
            int what = inputMessage.what;

            switch (what) {
                case DO_RENDER_MESSAGE: {
                    int renderMask = pendingRenderMask.getAndSet(0);
                    if (renderMask != 0) {
                        onDraw(inputMessage.arg1, (renderMask & 1) != 0, (renderMask & 2) != 0);
                    }
                    break;
                }
                case DO_SHUTDOWN_MESSAGE:
                    synchronized (this) {
                        finish();
                    }
                    if (recording && (!(inputMessage.obj instanceof SendOptions) || ((SendOptions) inputMessage.obj).ttl != -2) && videoEncoder != null) {
                        requestStopRecording(inputMessage.arg1, inputMessage.obj instanceof SendOptions ? (SendOptions) inputMessage.obj : null);
                    }
                    Looper looper = Looper.myLooper();
                    if (looper != null) {
                        looper.quitSafely();
                    }
                    break;
                case DO_REINIT_MESSAGE: {
                    if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("InstantCamera eglMakeCurrent failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                        }
                        return;
                    }

                    surfaceGeneration[0]++;
                    Handler reinitHandler = getHandler();
                    if (reinitHandler != null) {
                        reinitHandler.removeMessages(DO_RENDER_MESSAGE);
                    }
                    int oldRenderMask = pendingRenderMask.getAndSet(0);

                    if (cameraSurface[0] != null) {
                        cameraSurface[0].getTransformMatrix(moldSTMatrix);
                        cameraSurface[0].setOnFrameAvailableListener(null);
                        cameraSurface[0].release();
                        oldCameraTexture[0] = cameraTexture[0];
                        cameraTextureAlpha = 0.0f;
                        cameraTexture[0] = 0;
                        oldTextureTextureBuffer = textureBuffer.duplicate();
                        oldTexturePreviewSize = previewSize[0];
                    }
                    cameraId++;
                    cameraReady = false;

                    GLES20.glGenTextures(1, cameraTexture, 0);
                    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture[0]);
                    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
                    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

                    cameraSurface[0] = new SurfaceTexture(cameraTexture[0]);
                    attachFrameListener(0);
                    if (legacyCameraSwitchAwaitingFrame) {
                        logLegacyCameraSwitchStage("GL input recreated: surface=0");
                    }
                    createCamera(0, cameraSurface[0]);

                    updateTextureBuffer();
                    if ((oldRenderMask & 2) != 0 && cameraSurface[1] != null) {
                        requestRender(false, true);
                    }
                    break;
                }
                case DO_SETSESSION_MESSAGE: {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("InstantCamera set gl renderer session");
                    }
                    Object newSession = inputMessage.obj;
                    if (currentSession == newSession) {
                        int rotationAngle;
                        if (currentSession instanceof CameraSession) {
                            rotationAngle = ((CameraSession) currentSession).getWorldAngle();
                        } else if (currentSession instanceof Camera2Session) {
                            rotationAngle = ((Camera2Session) currentSession).getRoundVideoRotationAngle();
                        } else {
                            rotationAngle = 0;
                        }
                        android.opengl.Matrix.setIdentityM(mMVPMatrix, 0);
                        if (rotationAngle != 0) {
                            android.opengl.Matrix.rotateM(mMVPMatrix, 0, rotationAngle, 0, 0, 1);
                        }
                    } else {
                        currentSession = newSession;
                    }
                    break;
                }
                case DO_FLIP: {
                    surfaceIndex = 1 - surfaceIndex;
                    if (legacyCameraSwitchAwaitingFrame) {
                        logLegacyCameraSwitchStage("GL surface flip applied: surface="
                                + surfaceIndex);
                    }
                    updateTextureBuffer();
                    requestRender(true, true);
                    break;
                }
                case DO_SETORIENTATION_MESSAGE: {
                    CameraXSession session = cameraXSession;
                    int rotationAngle = session != null ? session.getDisplayOrientation() : 0;
                    android.opengl.Matrix.setIdentityM(mMVPMatrix, 0);
                    if (rotationAngle != 0) {
                        android.opengl.Matrix.rotateM(mMVPMatrix, 0, rotationAngle, 0, 0, 1);
                    }
                    break;
                }
                case DO_SET_CAMERAX_PREVIEW_SIZE: {
                    int index = inputMessage.arg1;
                    Size size = (Size) inputMessage.obj;
                    previewSize[index] = size;
                    if (index == surfaceIndex) {
                        updateTextureBuffer();
                    }
                    break;
                }
            }
        }

        public void shutdown(int send, boolean notify, int scheduleDate, int scheduleRepeatPeriod, int ttl, long effectId) {
            Handler handler = getHandler();
            if (handler != null) {
                synchronized (this) {
                    running = false;
                    for (SurfaceTexture texture : cameraSurface) {
                        if (texture != null) {
                            texture.setOnFrameAvailableListener(null);
                        }
                    }

                    handler.removeMessages(DO_RENDER_MESSAGE);
                    pendingRenderMask.set(0);
                    sendMessage(handler.obtainMessage(DO_SHUTDOWN_MESSAGE, send, 0, new SendOptions(notify, scheduleDate, scheduleRepeatPeriod, ttl, effectId, 0)), 0);
                }
            }
        }

        public void requestRender(boolean updateTexImage1, boolean updateTexImage2) {
            int bits = (updateTexImage1 ? 1 : 0) | (updateTexImage2 ? 2 : 0);
            if (bits == 0) {
                return;
            }
            Handler handler = getHandler();
            if (handler != null) {
                synchronized (this) {
                    if (!running) {
                        return;
                    }
                    int prev;
                    do {
                        prev = pendingRenderMask.get();
                    } while (!pendingRenderMask.compareAndSet(prev, prev | bits));
                    if (prev == 0) {
                        sendMessage(handler.obtainMessage(DO_RENDER_MESSAGE, cameraId, 0), 0);
                    }
                }
            }
        }
    }

    public static class SendOptions {
        boolean notify;
        int scheduleDate;
        int scheduleRepeatPeriod;
        int ttl;
        long effectId;
        long stars;

        public SendOptions(boolean notify, int scheduleDate, int scheduleRepeatPeriod, int ttl, long effectId, long stars) {
            this.notify = notify;
            this.scheduleDate = scheduleDate;
            this.scheduleRepeatPeriod = scheduleRepeatPeriod;
            this.ttl = ttl;
            this.effectId = effectId;
            this.stars = stars;
        }
    }

    public static class AudioBufferInfo {
        public final static int MAX_SAMPLES = 10;
        public ByteBuffer[] buffer = new ByteBuffer[MAX_SAMPLES];
        public long[] offset = new long[MAX_SAMPLES];
        public int[] read = new int[MAX_SAMPLES];
        public int results;
        public int lastWroteBuffer;
        public boolean last;

        public AudioBufferInfo() {
            for (int i = 0; i < MAX_SAMPLES; i++) {
                buffer[i] = ByteBuffer.allocateDirect(2048);
                buffer[i].order(ByteOrder.nativeOrder());
            }
        }
    }

    public static final int ENCODER_SEND_CANCEL = 0;
    public static final int ENCODER_SEND_SEND = 1;
    public static final int ENCODER_SEND_PLAYER = 2;

    private int encoderSend;
    private SendOptions encoderSendOptions;
    private boolean encoderFinishRequested;
    private boolean sentMedia;
    private boolean videoConvertFirstWrite;
    // the file the current encoder writes to; unlike cameraFile it is not nulled by cancel()
    private File encoderFile;

    private final ArrayList<Bitmap> keyframeThumbs = new ArrayList<>();
    private DispatchQueue generateKeyframeThumbsQueue;

    private final RoundVideoEncoder.Callback encoderCallback = new RoundVideoEncoder.Callback() {
        @Override
        public void onRecordingStarted(boolean fromPause) {
            if (cancelled) {
                return;
            }
            try {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } catch (Exception ignore) {}
            AndroidUtilities.lockOrientation(delegate.getParentActivity());
            recordPlusTime = fromPause ? recordedTime : 0;
            recordStartTime = System.currentTimeMillis();
            recording = true;
            updateFlash();
            invalidate();
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.recordStarted, recordingGuid, false);
        }

        @Override
        public void onAudioAmplitude(double amplitude) {
            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.recordProgressChanged, recordingGuid, amplitude));
        }

        @Override
        public void onWriteData(long availableSize) {
            File file = encoderFile;
            if (file != null) {
                didWriteData(file, availableSize, false);
            }
        }

        @Override
        public void onPaused(File pausePreviewFile) {
            handleEncoderPaused(pausePreviewFile);
        }

        @Override
        public void onFinished(RoundVideoEncoder.FinishReason reason) {
            handleEncoderFinished(reason);
        }
    };

    private void requestStopRecording(int send, SendOptions sendOptions) {
        RoundVideoEncoder encoder = videoEncoder;
        if (encoder == null) {
            return;
        }
        if (!encoderFinishRequested) {
            encoderFinishRequested = true;
            encoderSend = send;
            encoderSendOptions = sendOptions;
            if (send == ENCODER_SEND_SEND) {
                AndroidUtilities.runOnUIThread(() -> sendMediaBeforeDone(sendOptions));
            }
        }
        if (send == ENCODER_SEND_CANCEL) {
            encoder.cancel();
        } else {
            encoder.stop();
        }
        AndroidUtilities.runOnUIThread(() -> {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512);
        });
    }

    private void sendMediaBeforeDone(SendOptions sendOptions) {
        final File videoFile = encoderFile;
        if (sentMedia || videoFile == null || videoEditedInfo != null && videoEditedInfo.needConvert() || delegate.isInScheduleMode()) {
            return;
        }
        sentMedia = true;
        videoEditedInfo = new VideoEditedInfo();
        videoEditedInfo.startTime = -1;
        videoEditedInfo.endTime = -1;
        videoEditedInfo.estimatedSize = Math.max(1, size);
        videoEditedInfo.roundVideo = true;
        videoEditedInfo.file = file;
        videoEditedInfo.encryptedFile = encryptedFile;
        videoEditedInfo.key = key;
        videoEditedInfo.iv = iv;
        videoEditedInfo.framerate = encoderFrameRate;
        videoEditedInfo.resultWidth = videoEditedInfo.originalWidth = SystemUtils.getRoundVideoResolution();
        videoEditedInfo.resultHeight = videoEditedInfo.originalHeight = SystemUtils.getRoundVideoResolution();
        videoEditedInfo.originalPath = videoFile.getAbsolutePath();
        videoEditedInfo.notReadyYet = true;
        videoEditedInfo.thumb = firstFrameThumb;
        videoEditedInfo.estimatedDuration = recordedTime;
        firstFrameThumb = null;
        MediaController.PhotoEntry entry = new MediaController.PhotoEntry(0, 0, 0, videoFile.getAbsolutePath(), 0, true, 0, 0, 0);
        if (sendOptions != null) {
            entry.ttl = sendOptions.ttl;
            entry.effectId = sendOptions.effectId;
        }
        delegate.sendMedia(entry, videoEditedInfo, sendOptions == null || sendOptions.notify, sendOptions != null ? sendOptions.scheduleDate : 0, sendOptions != null ? sendOptions.scheduleRepeatPeriod : 0, false, sendOptions != null ? sendOptions.stars : 0);
    }

    private void handleEncoderPaused(File pausePreviewFile) {
        if (pausePreviewFile == null || cancelled) {
            return;
        }
        videoEditedInfo = new VideoEditedInfo();
        videoEditedInfo.roundVideo = true;
        videoEditedInfo.startTime = -1;
        videoEditedInfo.endTime = -1;
        videoEditedInfo.file = file;
        videoEditedInfo.encryptedFile = encryptedFile;
        videoEditedInfo.key = key;
        videoEditedInfo.iv = iv;
        videoEditedInfo.estimatedSize = Math.max(1, size);
        videoEditedInfo.framerate = encoderFrameRate;
        videoEditedInfo.resultWidth = videoEditedInfo.originalWidth = SystemUtils.getRoundVideoResolution();
        videoEditedInfo.resultHeight = videoEditedInfo.originalHeight = SystemUtils.getRoundVideoResolution();
        videoEditedInfo.originalPath = pausePreviewFile.getAbsolutePath();
        setupVideoPlayer(pausePreviewFile);
        videoEditedInfo.estimatedDuration = recordedTime;
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.audioDidSent, recordingGuid, videoEditedInfo, pausePreviewFile.getAbsolutePath(), keyframeThumbs);
    }

    private void handleEncoderFinished(RoundVideoEncoder.FinishReason reason) {
        videoEncoder = null;
        final int send = reason == RoundVideoEncoder.FinishReason.COMPLETED ? encoderSend : ENCODER_SEND_CANCEL;
        final SendOptions sendOptions = encoderSendOptions;
        encoderSendOptions = null;
        if (previewFile != null) {
            previewFile.delete();
            previewFile = null;
        }
        if (send != ENCODER_SEND_PLAYER && generateKeyframeThumbsQueue != null) {
            generateKeyframeThumbsQueue.cleanupQueue();
            generateKeyframeThumbsQueue.recycle();
            generateKeyframeThumbsQueue = null;
        }
        FileLog.d("InstantCamera encoder finished send " + send);
        final File videoFile = encoderFile;
        if (send == ENCODER_SEND_CANCEL || videoFile == null) {
            if (videoFile != null) {
                FileLoader.getInstance(currentAccount).cancelFileUpload(videoFile.getAbsolutePath(), false);
            }
            MediaController.getInstance().requestRecordAudioFocus(false);
            if (reason == RoundVideoEncoder.FinishReason.FAILED && !cancelled) {
                handleEncoderFailure();
            }
            return;
        }
        if (!sentMedia) {
            sentMedia = true;
            if (videoEditedInfo == null) {
                videoEditedInfo = new VideoEditedInfo();
                videoEditedInfo.startTime = -1;
                videoEditedInfo.endTime = -1;
            }
            if (videoEditedInfo.needConvert()) {
                file = null;
                encryptedFile = null;
                key = null;
                iv = null;
                double totalDuration = videoEditedInfo.estimatedDuration;
                long startTime = videoEditedInfo.startTime >= 0 ? videoEditedInfo.startTime : 0;
                long endTime = videoEditedInfo.endTime >= 0 ? videoEditedInfo.endTime : videoEditedInfo.estimatedDuration;
                videoEditedInfo.estimatedDuration = endTime - startTime;
                videoEditedInfo.estimatedSize = Math.max(1, (long) (size * (videoEditedInfo.estimatedDuration / totalDuration)));
                videoEditedInfo.bitrate = SystemUtils.getRoundVideoBitrate() * 1024;
                if (videoEditedInfo.startTime > 0) {
                    videoEditedInfo.startTime *= 1000;
                }
                if (videoEditedInfo.endTime > 0) {
                    videoEditedInfo.endTime *= 1000;
                }
                FileLoader.getInstance(currentAccount).cancelFileUpload(videoFile.getAbsolutePath(), false);
            } else {
                videoEditedInfo.estimatedSize = Math.max(1, size);
            }
            videoEditedInfo.roundVideo = true;
            videoEditedInfo.file = file;
            videoEditedInfo.encryptedFile = encryptedFile;
            videoEditedInfo.key = key;
            videoEditedInfo.iv = iv;
            videoEditedInfo.framerate = encoderFrameRate;
            videoEditedInfo.resultWidth = videoEditedInfo.originalWidth = SystemUtils.getRoundVideoResolution();
            videoEditedInfo.resultHeight = videoEditedInfo.originalHeight = SystemUtils.getRoundVideoResolution();
            videoEditedInfo.originalPath = videoFile.getAbsolutePath();
            final VideoEditedInfo info = videoEditedInfo;
            if (send == ENCODER_SEND_SEND) {
                if (delegate.isInScheduleMode()) {
                    AlertsCreator.createScheduleDatePickerDialog(delegate.getParentActivity(), delegate.getDialogId(), (notify, scheduleDate, scheduleRepeatPeriod) -> {
                        MediaController.PhotoEntry entry = new MediaController.PhotoEntry(0, 0, 0, videoFile.getAbsolutePath(), 0, true, 0, 0, 0);
                        if (sendOptions != null) {
                            entry.ttl = sendOptions.ttl;
                            entry.effectId = sendOptions.effectId;
                        }
                        delegate.sendMedia(entry, info, notify || sendOptions == null || sendOptions.notify, scheduleDate != 0 ? scheduleDate : sendOptions != null ? sendOptions.scheduleDate : 0, scheduleRepeatPeriod != 0 ? scheduleRepeatPeriod : sendOptions != null ? sendOptions.scheduleRepeatPeriod : 0, false, sendOptions != null ? sendOptions.stars : 0);
                        startAnimation(false, false);
                    }, () -> {
                        startAnimation(false, false);
                    }, resourcesProvider);
                } else {
                    MediaController.PhotoEntry entry = new MediaController.PhotoEntry(0, 0, 0, videoFile.getAbsolutePath(), 0, true, 0, 0, 0);
                    if (sendOptions != null) {
                        entry.ttl = sendOptions.ttl;
                        entry.effectId = sendOptions.effectId;
                    }
                    delegate.sendMedia(entry, info, sendOptions == null || sendOptions.notify, sendOptions != null ? sendOptions.scheduleDate : 0, sendOptions != null ? sendOptions.scheduleRepeatPeriod : 0, false, sendOptions != null ? sendOptions.stars : 0);
                }
                videoEditedInfo = null;
            } else {
                setupVideoPlayer(videoFile);
                info.estimatedDuration = recordedTime;
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.audioDidSent, recordingGuid, info, videoFile.getAbsolutePath(), keyframeThumbs);
            }
        } else if (videoEditedInfo != null) {
            videoEditedInfo.notReadyYet = false;
        }
        didWriteData(videoFile, 0, true);
        MediaController.getInstance().requestRecordAudioFocus(false);
    }

    private void handleEncoderFailure() {
        boolean notifyStopped = recording || cameraThread != null;
        cancelled = true;
        recording = false;
        flashing = false;
        updateFlash();
        stopProgressTimer();
        if (videoPlayer != null) {
            videoPlayer.releasePlayer(true);
            videoPlayer = null;
        }
        if (notifyStopped) {
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.recordStopped, recordingGuid, 6);
        }
        if (cameraThread != null) {
            cameraThread.shutdown(0, true, 0, 0, 0, 0);
            cameraThread = null;
        }
        if (cameraFile != null) {
            cameraFile.delete();
            AutoDeleteMediaTask.unlockFile(cameraFile);
            cameraFile = null;
        }
        encoderFile = null;
        startAnimation(false, false);
        invalidate();
    }

    private void didWriteData(File file, long availableSize, boolean last) {
        if (videoConvertFirstWrite) {
            FileLoader.getInstance(currentAccount).uploadFile(file.toString(), isSecretChat, false, 1, ConnectionsManager.FileTypeVideo, false);
            videoConvertFirstWrite = false;
            if (last) {
                FileLoader.getInstance(currentAccount).checkUploadNewDataAvailable(file.toString(), isSecretChat, availableSize, last ? file.length() : 0);
            }
        } else {
            FileLoader.getInstance(currentAccount).checkUploadNewDataAvailable(file.toString(), isSecretChat, availableSize, last ? file.length() : 0);
        }
    }

    private void setupVideoPlayer(File videoFile) {
        videoPlayer = new VideoPlayer();
        videoPlayer.setDelegate(new VideoPlayer.VideoPlayerDelegate() {
            @Override
            public void onStateChanged(boolean playWhenReady, int playbackState) {
                if (videoPlayer == null) {
                    return;
                }
                if (videoPlayer.isPlaying() && playbackState == ExoPlayer.STATE_ENDED && videoEditedInfo != null) {
                    videoPlayer.seekTo(videoEditedInfo.startTime > 0 ? videoEditedInfo.startTime : 0);
                }
            }

            @Override
            public void onError(VideoPlayer player, Exception e) {
                FileLog.e(e);
            }

            @Override
            public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {

            }

            @Override
            public void onRenderedFirstFrame() {

            }
        });
        releaseCameraXSession();
        videoPlayer.setTextureView(textureView);
        videoPlayer.preparePlayer(Uri.fromFile(videoFile), "other");
        videoPlayer.play();
        videoPlayer.setMute(true);
        startProgressTimer();

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(buttonsLayout, View.ALPHA, 0.0f),
                ObjectAnimator.ofFloat(zoomSlider, InstantCameraZoomSlider.OPEN_ALPHA, 0.0f),
                ObjectAnimator.ofInt(paint, AnimationProperties.PAINT_ALPHA, 0),
                ObjectAnimator.ofFloat(muteImageView, View.ALPHA, 1.0f));
        animatorSet.setDuration(180);
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.start();
    }

    private class GenerateKeyframeThumbTask implements Runnable {

        private final AtomicBoolean inFlight;

        GenerateKeyframeThumbTask(AtomicBoolean inFlight) {
            this.inFlight = inFlight;
        }

        @Override
        public void run() {
            try {
                final TextureView textureView = InstantCameraView.this.textureView;
                if (textureView != null) {
                    try {
                        final Bitmap bitmap = textureView.getBitmap(dp(56), dp(56));
                        AndroidUtilities.runOnUIThread(() -> {
                            if ((bitmap == null || bitmap.getPixel(0, 0) == 0) && keyframeThumbs.size() > 1) {
                                keyframeThumbs.add(keyframeThumbs.get(keyframeThumbs.size() - 1));
                            } else {
                                keyframeThumbs.add(bitmap);
                            }
                        });
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            } finally {
                inFlight.set(false);
            }
        }
    }

    private class EncoderRenderer implements RoundVideoEncoder.Renderer {

        private int drawProgram;
        private int vertexMatrixHandle;
        private int textureMatrixHandle;
        private int positionHandle;
        private int textureHandle;
        private int resolutionHandle;
        private int previewSizeHandle;
        private int texelSizeHandle;
        private int alphaHandle;
        private boolean blendEnabled;
        private int videoWidth;
        private int videoHeight;
        private InstantCameraVideoEncoderOverlayHelper overlayHelper;
        private final FloatBuffer frameTextureBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        private boolean firstThumbPending = true;
        private long thumbActiveTimeNs;
        private long nextThumbActiveTimeNs;
        private final AtomicBoolean thumbTaskInFlight = new AtomicBoolean();

        @Override
        public void onEncoderSurfaceCreated(int width, int height) {
            videoWidth = width;
            videoHeight = height;
            blendEnabled = false;
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

            if (overlayHelper != null) {
                overlayHelper.destroy();
            }
            overlayHelper = new InstantCameraVideoEncoderOverlayHelper(videoWidth, videoHeight);

            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER,
                    ExteraConfig.getCameraType() == CameraType.CAMERA_X ?
                            CameraXSession.ENCODER_FRAGMENT_SHADER : createFragmentShaderV2(previewSize[0]));
            if (vertexShader != 0 && fragmentShader != 0) {
                drawProgram = GLES20.glCreateProgram();
                GLES20.glAttachShader(drawProgram, vertexShader);
                GLES20.glAttachShader(drawProgram, fragmentShader);
                GLES20.glLinkProgram(drawProgram);
                int[] linkStatus = new int[1];
                GLES20.glGetProgramiv(drawProgram, GLES20.GL_LINK_STATUS, linkStatus, 0);
                if (linkStatus[0] == 0) {
                    GLES20.glDeleteProgram(drawProgram);
                    drawProgram = 0;
                } else {
                    positionHandle = GLES20.glGetAttribLocation(drawProgram, "aPosition");
                    textureHandle = GLES20.glGetAttribLocation(drawProgram, "aTextureCoord");
                    previewSizeHandle = GLES20.glGetUniformLocation(drawProgram, "preview");
                    resolutionHandle = GLES20.glGetUniformLocation(drawProgram, "resolution");
                    alphaHandle = GLES20.glGetUniformLocation(drawProgram, "alpha");
                    vertexMatrixHandle = GLES20.glGetUniformLocation(drawProgram, "uMVPMatrix");
                    textureMatrixHandle = GLES20.glGetUniformLocation(drawProgram, "uSTMatrix");
                    texelSizeHandle = GLES20.glGetUniformLocation(drawProgram, "texelSize");
                }
            }
        }

        @Override
        public boolean onDrawEncoderFrame(long frameDeltaNs, RoundVideoEncoder.FrameSnapshot frame) {
            if (!cameraTextureAvailable || drawProgram == 0) {
                return false;
            }
            FloatBuffer vertexBuffer = InstantCameraView.this.vertexBuffer;
            FloatBuffer oldTextureBuffer = oldTextureTextureBuffer;
            if (vertexBuffer == null) {
                FileLog.d("InstantCamera encoder skip frame, no vertex buffer");
                return false;
            }
            frameTextureBuffer.clear();
            frameTextureBuffer.put(frame.textureCoords);
            frameTextureBuffer.position(0);

            if (overlayHelper != null) {
                overlayHelper.bind();
            }

            GLES20.glUseProgram(drawProgram);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer);
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glVertexAttribPointer(textureHandle, 2, GLES20.GL_FLOAT, false, 8, frameTextureBuffer);
            GLES20.glEnableVertexAttribArray(textureHandle);
            GLES20.glUniformMatrix4fv(vertexMatrixHandle, 1, false, frame.mvpMatrix, 0);

            GLES20.glUniform2f(resolutionHandle, videoWidth, videoHeight);

            if (oldCameraTexture[0] != 0 && oldTextureBuffer != null && !bothCameras) {
                if (!blendEnabled) {
                    GLES20.glEnable(GLES20.GL_BLEND);
                    blendEnabled = true;
                }
                if (oldTexturePreviewSize != null) {
                    GLES20.glUniform2f(previewSizeHandle, oldTexturePreviewSize.getWidth(), oldTexturePreviewSize.getHeight());
                }
                GLES20.glVertexAttribPointer(textureHandle, 2, GLES20.GL_FLOAT, false, 8, oldTextureBuffer);

                GLES20.glUniformMatrix4fv(textureMatrixHandle, 1, false, moldSTMatrix, 0);
                GLES20.glUniform1f(alphaHandle, 1.0f);
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oldCameraTexture[0]);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            }

            if (frame.previewWidth > 0 && frame.previewHeight > 0) {
                GLES20.glUniform2f(previewSizeHandle, frame.previewWidth, frame.previewHeight);
                GLES20.glUniform2f(texelSizeHandle, 1f / frame.previewWidth / 2f, 1f / frame.previewHeight / 2f);
            }

            final int tex = frame.textureId;
            if (tex != Integer.MIN_VALUE) {
                GLES20.glUniformMatrix4fv(textureMatrixHandle, 1, false, frame.stMatrix, 0);
                GLES20.glUniform1f(alphaHandle, cameraTextureAlpha);
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            }

            GLES20.glDisableVertexAttribArray(positionHandle);
            GLES20.glDisableVertexAttribArray(textureHandle);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
            GLES20.glUseProgram(0);

            if (overlayHelper != null) {
                overlayHelper.render(frameDeltaNs);
                if (blendEnabled) {
                    GLES20.glEnable(GLES20.GL_BLEND);
                }
            }

            thumbActiveTimeNs += frameDeltaNs;
            maybeScheduleKeyframeThumb();

            if (oldCameraTexture[0] != 0 && cameraTextureAlpha < 1.0f && !bothCameras) {
                cameraTextureAlpha += frameDeltaNs / 200000000.0f;
                if (cameraTextureAlpha > 1) {
                    GLES20.glDisable(GLES20.GL_BLEND);
                    blendEnabled = false;
                    cameraTextureAlpha = 1;
                    GLES20.glDeleteTextures(1, oldCameraTexture, 0);
                    oldCameraTexture[0] = 0;
                    if (!cameraReady) {
                        cameraReady = true;
                        AndroidUtilities.runOnUIThread(() -> textureOverlayView.animate().setDuration(120).alpha(0.0f).setInterpolator(new DecelerateInterpolator()).start());
                    }
                }
            } else if (!cameraReady) {
                cameraReady = true;
                AndroidUtilities.runOnUIThread(() -> textureOverlayView.animate().setDuration(120).alpha(0.0f).setInterpolator(new DecelerateInterpolator()).start());
            }
            return true;
        }

        @Override
        public void onEncoderSurfaceDestroyed() {
            if (overlayHelper != null) {
                overlayHelper.destroy();
                overlayHelper = null;
            }
            if (drawProgram != 0) {
                GLES20.glDeleteProgram(drawProgram);
                drawProgram = 0;
            }
        }

        private void maybeScheduleKeyframeThumb() {
            if (generateKeyframeThumbsQueue == null || SharedConfig.getDevicePerformanceClass() != SharedConfig.PERFORMANCE_CLASS_HIGH) {
                return;
            }
            if (!firstThumbPending && thumbActiveTimeNs < nextThumbActiveTimeNs) {
                return;
            }
            if (!thumbTaskInFlight.compareAndSet(false, true)) {
                return;
            }
            firstThumbPending = false;
            nextThumbActiveTimeNs = thumbActiveTimeNs + 1_100_000_000L;
            generateKeyframeThumbsQueue.postRunnable(new GenerateKeyframeThumbTask(thumbTaskInFlight));
        }
    }

    private String createFragmentShader(Size previewSize) {
        if (SharedConfig.deviceIsLow() || !allowBigSizeCamera() || previewSize != null && Math.max(previewSize.getHeight(), previewSize.getWidth()) * 0.7f < SystemUtils.getRoundVideoResolution()) {
            return "#extension GL_OES_EGL_image_external : require\n" +
                    "precision highp float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform float alpha;\n" +
                    "uniform vec2 preview;\n" +
                    "uniform vec2 resolution;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "void main() {\n" +
                    "   vec4 textColor = texture2D(sTexture, vTextureCoord);\n" +
                    "   vec2 coord = resolution * 0.5;\n" +
                    "   float radius = 0.51 * resolution.x;\n" +
                    "   float d = length(coord - gl_FragCoord.xy) - radius;\n" +
                    "   float t = clamp(d, 0.0, 1.0);\n" +
                    "   vec3 color = mix(textColor.rgb, vec3(1, 1, 1), t);\n" +
                    "   gl_FragColor = vec4(color * alpha, alpha);\n" +
                    "}\n";
        }
        //apply bilinear filtering
        return "#extension GL_OES_EGL_image_external : require\n" +
                "precision highp float;\n" +
                "varying vec2 vTextureCoord;\n" + //uv
                "uniform vec2 resolution;\n" + //rendering texture
                "uniform vec2 preview;\n" + //original texture size
                "uniform float alpha;\n" +

                "uniform samplerExternalOES sTexture;\n" +
                "void main() {\n" +
                "   vec2 coord = resolution * 0.5;\n" +
                "   float radius = 0.51 * resolution.x;\n" +
                "   float d = length(coord - gl_FragCoord.xy) - radius;\n" +
                "   float t = clamp(d, 0.0, 1.0);\n" +
                "   if (t == 0.0) {\n" +
                "       vec2 c_textureSize = preview;\n" +
                "       vec2 c_onePixel = (1.0 / c_textureSize);\n" +
                "       vec2 uv = vTextureCoord;\n" +
                "       vec2 pixel = uv * c_textureSize + 0.5;\n" +

                "       vec2 frac = fract(pixel);\n" +
                "       pixel = (floor(pixel) / c_textureSize) - vec2(c_onePixel);\n" +

                "       vec4 tl = texture2D(sTexture, pixel + vec2(0.0         , 0.0));\n" +
                "       vec4 tr = texture2D(sTexture, pixel + vec2(c_onePixel.x, 0.0));\n" +
                "       vec4 bl = texture2D(sTexture, pixel + vec2(0.0         , c_onePixel.y));\n" +
                "       vec4 br = texture2D(sTexture, pixel + vec2(c_onePixel.x, c_onePixel.y));\n" +

                "       vec4 x1 = mix(tl, tr, frac.x);\n" +
                "       vec4 x2 = mix(bl, br, frac.x);\n" +
                "       gl_FragColor = mix(x1, x2, frac.y) * alpha;" +
                "   } else {\n" +
                "       gl_FragColor = vec4(1, 1, 1, alpha);\n" +
                "   }\n" +
                "}\n";
    }

    private String createFragmentShaderV2(Size previewSize) {
        if (SharedConfig.deviceIsLow() || !allowBigSizeCamera() || previewSize != null && Math.max(previewSize.getHeight(), previewSize.getWidth()) * 0.7f < MessagesController.getInstance(currentAccount).roundVideoSize) {
            return "#extension GL_OES_EGL_image_external : require\n" +
                    "precision highp float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform float alpha;\n" +
                    "uniform vec2 preview;\n" +
                    "uniform vec2 resolution;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "void main() {\n" +
                    "   vec4 textColor = texture2D(sTexture, vTextureCoord);\n" +
                    "   gl_FragColor = vec4(textColor.rgb * alpha, alpha);\n" +
                    "}\n";
        }
        return "#extension GL_OES_EGL_image_external : require\n" +
                "precision highp float;\n" +
                "varying vec2 vTextureCoord;\n" + //uv
                "uniform vec2 resolution;\n" + //rendering texture
                "uniform vec2 preview;\n" + //original texture size
                "uniform float alpha;\n" +

                "uniform samplerExternalOES sTexture;\n" +
                "void main() {\n" +
                "   vec2 c_textureSize = preview;\n" +
                "   vec2 c_onePixel = (1.0 / c_textureSize);\n" +
                "   vec2 uv = vTextureCoord;\n" +
                "   vec2 pixel = uv * c_textureSize + 0.5;\n" +
                "   vec2 frac = fract(pixel);\n" +
                "   pixel = (floor(pixel) / c_textureSize) - vec2(c_onePixel);\n" +
                "   vec4 tl = texture2D(sTexture, pixel + vec2(0.0         , 0.0));\n" +
                "   vec4 tr = texture2D(sTexture, pixel + vec2(c_onePixel.x, 0.0));\n" +
                "   vec4 bl = texture2D(sTexture, pixel + vec2(0.0         , c_onePixel.y));\n" +
                "   vec4 br = texture2D(sTexture, pixel + vec2(c_onePixel.x, c_onePixel.y));\n" +
                "   vec4 x1 = mix(tl, tr, frac.x);\n" +
                "   vec4 x2 = mix(bl, br, frac.x);\n" +
                "   gl_FragColor = mix(x1, x2, frac.y) * alpha;\n" +
                "}\n";
    }

    public class InstantViewCameraContainer extends InstantCameraViewBase.InstantViewCameraContainer {

        ImageReceiver imageReceiver;
        float imageProgress;

        public InstantViewCameraContainer(Context context) {
            super(context);
            InstantCameraView.this.setWillNotDraw(false);
        }

        @Override
        public void setImageReceiver(ImageReceiver imageReceiver) {
            if (this.imageReceiver == null) {
                imageProgress = 0;
            }
            this.imageReceiver = imageReceiver;
            invalidate();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (imageProgress != 1f) {
                imageProgress += 16 / 250.0f;
                if (imageProgress > 1f) {
                    imageProgress = 1f;
                }
                invalidate();
            }
            if (imageReceiver != null) {
                canvas.save();
                if (imageReceiver.getImageWidth() != textureViewSize) {
                    float s = textureViewSize / imageReceiver.getImageWidth();
                    canvas.scale(s, s);
                }
                canvas.translate(-imageReceiver.getImageX(), -imageReceiver.getImageY());
                float oldAlpha = imageReceiver.getAlpha();
                imageReceiver.setAlpha(imageProgress);
                imageReceiver.draw(canvas);
                imageReceiver.setAlpha(oldAlpha);
                canvas.restore();
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getY() > getMeasuredHeight() - getPaddingBottom()) {
            return false;
        }

        if (ev.getAction() == MotionEvent.ACTION_DOWN && delegate != null) {
            if (videoPlayer != null) {
                boolean mute = !videoPlayer.isMuted();
                videoPlayer.setMute(mute);
                if (muteAnimation != null) {
                    muteAnimation.cancel();
                }
                muteAnimation = new AnimatorSet();
                muteAnimation.playTogether(
                        ObjectAnimator.ofFloat(muteImageView, View.ALPHA, mute ? 1.0f : 0.0f),
                        ObjectAnimator.ofFloat(muteImageView, View.SCALE_X, mute ? 1.0f : 0.5f),
                        ObjectAnimator.ofFloat(muteImageView, View.SCALE_Y, mute ? 1.0f : 0.5f));
                muteAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animation.equals(muteAnimation)) {
                            muteAnimation = null;
                        }
                    }
                });
                muteAnimation.setDuration(180);
                muteAnimation.setInterpolator(new DecelerateInterpolator());
                muteAnimation.start();
            } else {
                //baseFragment.checkRecordLocked(false);
            }
        }

        if (ExteraConfig.getCameraType() == CameraType.CAMERA_X) {
            getParent().requestDisallowInterceptTouchEvent(true);
            scaleGestureDetector.onTouchEvent(ev);
            return true;
        }

        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN || ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            if (maybePinchToZoomTouchMode && !isInPinchToZoomTouchMode && ev.getPointerCount() == 2 && finishZoomTransition == null && recording) {
                pinchStartDistance = (float) Math.hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0));

                pointerId1 = ev.getPointerId(0);
                pointerId2 = ev.getPointerId(1);
                isInPinchToZoomTouchMode = true;
                zoomSlider.beginPinchZoomGesture();
                zoomWas = false;
                initialCameraZoom = cameraZoom;
            }
            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                AndroidUtilities.rectTmp.set(cameraContainer.getX(), cameraContainer.getY(), cameraContainer.getX() + cameraContainer.getMeasuredWidth(), cameraContainer.getY() + cameraContainer.getMeasuredHeight());
                maybePinchToZoomTouchMode = AndroidUtilities.rectTmp.contains(ev.getX(), ev.getY());
            }
            return true;
        } else if (ev.getActionMasked() == MotionEvent.ACTION_MOVE && isInPinchToZoomTouchMode) {
            int index1 = -1;
            int index2 = -1;
            for (int i = 0; i < ev.getPointerCount(); i++) {
                if (pointerId1 == ev.getPointerId(i)) {
                    index1 = i;
                }
                if (pointerId2 == ev.getPointerId(i)) {
                    index2 = i;
                }
            }
            if (index1 == -1 || index2 == -1) {
                isInPinchToZoomTouchMode = false;
                finishZoom();
                return false;
            }

            float newDistance = (float) Math.hypot(ev.getX(index2) - ev.getX(index1), ev.getY(index2) - ev.getY(index1));

            float baseZoomSensitivity = 0.002f;
            float zoomSensitivity;

            if (useCamera2) {
                if (camera2SessionCurrent != null) {
                    zoomSensitivity = baseZoomSensitivity * (camera2SessionCurrent.getMaxZoom() - camera2SessionCurrent.getMinZoom());
                } else {
                    zoomSensitivity = baseZoomSensitivity;
                }
            } else {
                zoomSensitivity = baseZoomSensitivity;
            }

            float zoomDelta = (newDistance - pinchStartDistance) * zoomSensitivity;

            cameraZoom = initialCameraZoom + zoomDelta;

            if (useCamera2) {
                if (camera2SessionCurrent != null) {
                    cameraZoom = Utilities.clamp(cameraZoom, camera2SessionCurrent.getMaxZoom(), camera2SessionCurrent.getMinZoom());
                    camera2SessionCurrent.setZoom(cameraZoom);
                }
            } else {
                cameraZoom = Utilities.clamp(cameraZoom, 1.0f, 0.0f);
                if (cameraSession != null) {
                    cameraSession.setZoom(cameraZoom);
                }
            }
            zoomSlider.syncZoom(cameraZoom);
            zoomWas = true;
        } else if ((ev.getActionMasked() == MotionEvent.ACTION_UP || (ev.getActionMasked() == MotionEvent.ACTION_POINTER_UP && checkPointerIds(ev)) || ev.getActionMasked() == MotionEvent.ACTION_CANCEL) && isInPinchToZoomTouchMode) {
            isInPinchToZoomTouchMode = false;
            if (zoomWas) {
                finishZoom();
            } else {
                zoomSlider.endPinchZoomGesture();
            }
        }
        return true;
    }

    private float initialCameraZoom = 0f;

    private ValueAnimator finishZoomTransition;
    private ValueAnimator zoomAnimator;

    private void cancelZoomAnimations() {
        zoomSlider.cancelZoomAnimation();
        if (zoomAnimator != null) {
            zoomAnimator.cancel();
            zoomAnimator = null;
        }
        if (finishZoomTransition != null) {
            finishZoomTransition.cancel();
            finishZoomTransition = null;
        }
    }

    public void finishZoom() {
        // The pinch is over regardless of whether the zoom itself is allowed to spring back.
        zoomSlider.endPinchZoomGesture();
        if (finishZoomTransition != null || ExteraConfig.getStaticZoom()) {
            return;
        }

        if (ExteraConfig.getCameraType() == CameraType.CAMERA_X) {
            zoomSlider.animateToResetZoom();
        } else {
            if (cameraZoom > 0f) {
                finishZoomTransition = ValueAnimator.ofFloat(cameraZoom, useCamera2 ? 1 : 0);
                finishZoomTransition.addUpdateListener(valueAnimator -> {
                    cameraZoom = (float) valueAnimator.getAnimatedValue();
                    if (useCamera2) {
                        if (camera2SessionCurrent != null) {
                            camera2SessionCurrent.setZoom(cameraZoom);
                        }
                    } else {
                        if (cameraSession != null) {
                            cameraSession.setZoom(cameraZoom);
                        }
                    }
                    zoomSlider.syncZoom(cameraZoom);
                });
                finishZoomTransition.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (finishZoomTransition != null) {
                            finishZoomTransition = null;
                        }
                    }
                });
                finishZoomTransition.setDuration(350);
                finishZoomTransition.setInterpolator(CubicBezierInterpolator.DEFAULT);
                finishZoomTransition.start();
            }
        }
    }

    private void adjustZoom(boolean increase) {
        if (!isCameraReady() || (zoomAnimator != null && zoomAnimator.isRunning())) {
            return;
        }
        if (ExteraConfig.getCameraType() == CameraType.CAMERA_X) {
            zoomSlider.stepZoom(increase);
            return;
        }
        cancelZoomAnimations();
        zoomSlider.beginSteppedZoomGesture();

        double zoomCoefficient = 1.75;
        float targetZoom = cameraZoom;
        if (useCamera2) {
            if (camera2SessionCurrent == null) return;
            float minRatio = camera2SessionCurrent.getMinZoom();
            float maxRatio = camera2SessionCurrent.getMaxZoom();

            double optimalSteps = Math.log(maxRatio) / Math.log(zoomCoefficient);
            int steps = (int) Math.round(optimalSteps);
            if (steps < 1) steps = 1;
            float stepFactor = (float) Math.pow(maxRatio, 1.0 / steps);

            if (increase) {
                targetZoom = cameraZoom * stepFactor;
            } else {
                targetZoom = cameraZoom / stepFactor;
                if (targetZoom < 1.0f) {
                    targetZoom = 1.0f;
                }
            }
            targetZoom = Utilities.clamp(targetZoom, maxRatio, minRatio);
        } else {
            targetZoom += (float) 0.125 * (increase ? 1 : -1);
            targetZoom = Utilities.clamp(targetZoom, 1.0f, 0.0f);
        }

        if (cameraZoom == targetZoom) {
            return;
        }

        zoomAnimator = ValueAnimator.ofFloat(cameraZoom, targetZoom);
        zoomAnimator.setDuration(175);
        zoomAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        zoomAnimator.addUpdateListener(animation -> {
            cameraZoom = (float) animation.getAnimatedValue();
            if (useCamera2) {
                if (camera2SessionCurrent != null) {
                    camera2SessionCurrent.setZoom(cameraZoom);
                }
            } else if (cameraSession != null) {
                cameraSession.setZoom(cameraZoom);
            }
            zoomSlider.syncZoom(cameraZoom);
        });
        zoomAnimator.start();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            adjustZoom(true);
            return true;
        } else if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            adjustZoom(false);
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean isCameraReady() {
        boolean xReady = ExteraConfig.getCameraType() == CameraType.CAMERA_X ?
                cameraXSession != null && cameraXSession.isReady() :
                isCameraSessionInitiated();
        return cameraReady && xReady && cameraThread != null;
    }

    public interface Delegate {

        View getFragmentView();
        void sendMedia(MediaController.PhotoEntry entry, VideoEditedInfo videoEditedInfo, boolean notify, int scheduleDate, int scheduleRepeatPeriod, boolean b1, long stars);
        Activity getParentActivity();
        int getClassGuid();
        long getDialogId();

        default boolean isSecretChat() {
            return false;
        }

        default boolean isInScheduleMode() {
            return false;
        }
    }
}