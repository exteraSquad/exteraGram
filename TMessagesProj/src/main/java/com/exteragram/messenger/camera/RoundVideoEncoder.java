/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.camera;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTimestamp;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLUtils;
import android.os.SystemClock;
import android.view.Surface;

import com.exteragram.messenger.utils.system.SystemUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.video.MP4Builder;
import org.telegram.messenger.video.MediaCodecVideoConvertor;
import org.telegram.messenger.video.Mp4Movie;
import org.telegram.ui.Components.PermissionRequest;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Round (instant) video recorder.
 * <p>
 * Owns the AVC/AAC MediaCodecs, the AudioRecord capture worker, the encoder EGL
 * display/context/window surface over the codec input surface, the recording clock
 * and ordered MP4Builder writes. Every codec, muxer, EGL and state transition is
 * serialized on one dedicated encoder queue; the public methods are thread safe
 * and stop/cancel are idempotent.
 * <p>
 * The caller supplies a {@link Renderer} that draws the current camera texture
 * state into the encoder surface (all InstantCamera specific shaders, crossfade
 * and overlay rendering stay on the caller side) and a {@link Callback} that is
 * driven when recording actually starts, pauses, writes data or finishes.
 * <p>
 * Timing model: one monotonic time domain ({@link System#nanoTime()}). Source
 * SurfaceTexture timestamps are mapped onto it through an offset that is
 * re-anchored on camera switches and invalid or implausible timestamps, so
 * presentation times can never go negative or jump. The output is VFR: each
 * accepted frame keeps its real capture time (at most one frame per 1/fps slot),
 * active recording time excludes paused intervals, and audio presentation time
 * is derived from the exact number of submitted PCM frames.
 */
public class RoundVideoEncoder {

    public enum FinishReason {
        /** The recording was finalized and the output file is valid. */
        COMPLETED,
        /** The recording was discarded on request; the output file is deleted. */
        CANCELLED,
        /** An unrecoverable error occurred; the output file is deleted. */
        FAILED
    }

    /**
     * One camera frame's state, captured on the camera GL thread right after
     * {@link SurfaceTexture#updateTexImage()}. Instances are reused, not immutable:
     * the snapshot handed to {@link Renderer#onDrawEncoderFrame} is encoder-owned
     * and stays unchanged only for the duration of that synchronous call.
     */
    public static final class FrameSnapshot {
        public long sourceTimestampNs;
        public long arrivalTimeNs;
        public int cameraId;
        public int surfaceIndex;
        public int textureId;
        public final float[] stMatrix = new float[16];
        public final float[] mvpMatrix = new float[16];
        public final float[] textureCoords = new float[8];
        public int previewWidth;
        public int previewHeight;

        public void copyFrom(FrameSnapshot other) {
            sourceTimestampNs = other.sourceTimestampNs;
            arrivalTimeNs = other.arrivalTimeNs;
            cameraId = other.cameraId;
            surfaceIndex = other.surfaceIndex;
            textureId = other.textureId;
            System.arraycopy(other.stMatrix, 0, stMatrix, 0, 16);
            System.arraycopy(other.mvpMatrix, 0, mvpMatrix, 0, 16);
            System.arraycopy(other.textureCoords, 0, textureCoords, 0, 8);
            previewWidth = other.previewWidth;
            previewHeight = other.previewHeight;
        }
    }

    public interface Renderer {
        /**
         * Called on the encoder thread with the encoder EGL context current after the
         * codec input surface becomes available (both on first start and on resume).
         */
        void onEncoderSurfaceCreated(int videoWidth, int videoHeight);

        /**
         * Draws one frame into the encoder surface. Called on the encoder thread with
         * the encoder EGL context current. {@code frameDeltaNs} is the active-time
         * distance to the previously encoded frame (0 after camera switches and at
         * segment starts). {@code frame} is the encoder-owned snapshot of the
         * submission being encoded; it is reused between calls and must not be
         * retained past this call. Return false to skip the frame.
         */
        boolean onDrawEncoderFrame(long frameDeltaNs, FrameSnapshot frame);

        /**
         * Called on the encoder thread with the encoder EGL context still current
         * before it is destroyed (on pause and on final release).
         */
        void onEncoderSurfaceDestroyed();
    }

    public interface Callback {
        /**
         * Recording (or a resumed segment) is prepared and running. UI thread.
         */
        void onRecordingStarted(boolean fromPause);

        /**
         * Microphone amplitude of the last read audio buffer. Audio capture thread.
         */
        void onAudioAmplitude(double amplitude);

        /**
         * Data was flushed to the output file and can be uploaded incrementally.
         * Encoder thread.
         */
        void onWriteData(long availableSize);

        /**
         * The pause snapshot was written and the recorder is paused. UI thread.
         */
        void onPaused(File previewFile);

        /**
         * Terminal callback, the recorder cannot be reused after it. UI thread.
         */
        void onFinished(FinishReason reason);
    }

    private static final String VIDEO_MIME_TYPE = "video/avc";
    private static final String AUDIO_MIME_TYPE = "audio/mp4a-latm";
    private static final int FRAME_RATE = 30;
    private static final int EXTENDED_FRAME_RATE = 60;
    private static final int IFRAME_INTERVAL = 1;
    private static final int AUDIO_SAMPLE_RATE = 48000;
    private static final long AUDIO_CODEC_FRAME_DURATION_US = 1024L * 1_000_000L / AUDIO_SAMPLE_RATE;
    // first frames after a camera (re)start are skipped while exposure settles
    private static final long SETTLE_TIME_NS = 200_000_000L;
    // source timestamps jumping further than this are treated as a discontinuity
    private static final long MAX_SOURCE_JUMP_NS = 1_000_000_000L;
    private static final long EOS_DRAIN_TIMEOUT_MS = 5000;
    private static final long PAUSE_DRAIN_TIMEOUT_MS = 500;
    private static final long AUDIO_BATCH_POOL_WAIT_MS = 250;
    private static final int MAX_PENDING_AUDIO_BATCHES = 24;
    private static final int AUDIO_BATCH_POOL_SIZE = MAX_PENDING_AUDIO_BATCHES + 1;

    private static final int STATE_IDLE = 0;
    private static final int STATE_PREPARING = 1;
    private static final int STATE_RECORDING = 2;
    private static final int STATE_PAUSING = 3;
    private static final int STATE_PAUSED = 4;
    private static final int STATE_STOPPING = 5;
    private static final int STATE_RELEASED = 6;
    private static final int STATE_FAILED = 7;

    private static final int DEFERRED_FINISH_NONE = 0;
    private static final int DEFERRED_FINISH_STOP = 1;
    private static final int DEFERRED_FINISH_CANCEL = 2;

    private static final int DEFERRED_AUDIO_CLEANUP_NONE = 0;
    private static final int DEFERRED_AUDIO_CLEANUP_CANCEL = 1;
    private static final int DEFERRED_AUDIO_CLEANUP_FAILURE = 2;

    private class AudioChunkBatch {
        public static final int CHUNK_SIZE = 2048;
        public static final int MAX_CHUNKS = 10;
        public final byte[][] data = new byte[MAX_CHUNKS][];
        public final ByteBuffer[] buffer = new ByteBuffer[MAX_CHUNKS];
        public final long[] startTimeNs = new long[MAX_CHUNKS];
        public final Runnable deliveryRunnable = () -> handleAudioBatch(this);
        public int results;
        public int drained;

        public AudioChunkBatch() {
            for (int a = 0; a < MAX_CHUNKS; a++) {
                data[a] = new byte[CHUNK_SIZE];
                buffer[a] = ByteBuffer.wrap(data[a]).order(ByteOrder.nativeOrder());
            }
        }
    }

    private class AudioCaptureSession {
        public final int generation;
        public final AudioRecord audioRecorder;
        public final AtomicBoolean stopRequested = new AtomicBoolean(false);
        public final Object recorderLock = new Object();
        public final Runnable stopRunnable;
        public final Runnable completionRunnable;
        public Thread thread;
        public boolean recorderReleased;

        public AudioCaptureSession(AudioRecord audioRecorder, int generation) {
            this.audioRecorder = audioRecorder;
            this.generation = generation;
            stopRunnable = this::stopRecorder;
            completionRunnable = () -> handleAudioCaptureFinished(this);
        }

        public void requestStop() {
            boolean scheduleStop = stopRequested.compareAndSet(false, true);
            Thread captureThread = thread;
            if (captureThread != null && captureThread != Thread.currentThread()) {
                captureThread.interrupt();
            }
            if (scheduleStop && !Utilities.globalQueue.postRunnable(stopRunnable)) {
                FileLog.e("RoundVideoEncoder unable to schedule AudioRecord stop");
            }
        }

        private void stopRecorder() {
            synchronized (recorderLock) {
                if (!recorderReleased) {
                    stopAudioRecorder(audioRecorder);
                }
            }
        }

        public void releaseRecorder() {
            synchronized (recorderLock) {
                if (recorderReleased) {
                    return;
                }
                recorderReleased = true;
                releaseAudioRecorder(audioRecorder);
            }
        }
    }

    private final Renderer renderer;
    private final Callback callback;
    private final boolean isSecretChat;
    private final DispatchQueue encoderQueue;

    // set once from the first startRecording() call, read from the GL thread
    private volatile boolean started;
    // first stop()/cancel() wins, every further request is ignored
    private final AtomicBoolean finishRequested = new AtomicBoolean(false);

    // written on the encoder thread only
    private volatile int state = STATE_IDLE;

    private File videoFile;
    private File fileToWrite;
    private boolean writingToDifferentFile;
    private int videoWidth;
    private int videoHeight;
    private int videoBitrate;
    // latched once per recording so frame-slot deduplication stays consistent across pause/resume
    private int frameRate = FRAME_RATE;
    private long maxDurationUs;
    private boolean allowSendingWhileRecording;

    private MediaCodec videoEncoder;
    private MediaCodec audioEncoder;
    private Surface inputSurface;
    private MediaCodec.BufferInfo videoBufferInfo;
    private MediaCodec.BufferInfo audioBufferInfo;
    private MP4Builder mediaMuxer;
    private int videoTrackIndex = -5;
    private int audioTrackIndex = -5;
    private int prependHeaderSize;
    private boolean firstEncode;

    private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
    private EGLConfig eglConfig;

    private final Object pendingFrameLock = new Object();
    private boolean pendingFrameSet;
    private final FrameSnapshot pendingFrame = new FrameSnapshot();
    private final FrameSnapshot currentFrame = new FrameSnapshot();

    // recording clock (encoder thread only)
    private boolean sourceAnchorSet;
    private long sourceAnchorSourceNs;
    private long sourceAnchorMonotonicNs;
    private long lastSourceTimestampNs;
    private int lastCameraId = Integer.MIN_VALUE;
    private long segmentFirstArrivalNs = -1;
    private long segmentVideoOriginNs = -1;
    private long segmentActiveBaseNs;
    private long lastVideoFrameIndex = -1;
    private long lastVideoActiveTimeNs;
    private long minVideoFrameDeltaNs;
    private long lastSubmittedVideoPtsUs = -1;
    private long lastMuxedVideoPtsUs = -1;
    private boolean videoEosSignalled;
    private boolean videoEosSeen;

    // audio pipeline (encoder thread only, except the session and thread-safe free-batch pool)
    private boolean audioCaptureRunning;
    private int audioCaptureGeneration;
    private final Object audioCaptureLock = new Object();
    private AudioCaptureSession activeAudioCapture;
    private final ArrayBlockingQueue<AudioChunkBatch> audioBatchPool = new ArrayBlockingQueue<>(AUDIO_BATCH_POOL_SIZE);
    private boolean audioBatchesPrepared;
    private final ArrayList<AudioChunkBatch> pendingAudio = new ArrayList<>();
    private long audioSegmentBaseUs = -1;
    private long audioSegmentFramesSubmitted;
    private long audioTotalEndUs;
    private long lastSubmittedAudioEndUs = -1;
    private long lastMuxedAudioPtsUs = -1;
    private long audioCapUs;
    private boolean audioEosQueued;
    private boolean audioEosSeen;

    private boolean waitingAudioTail;
    private int deferredFinish = DEFERRED_FINISH_NONE;
    private int deferredAudioCleanup = DEFERRED_AUDIO_CLEANUP_NONE;
    private File pausePreviewFile;
    private EGLContext pendingResumeContext;

    public RoundVideoEncoder(Renderer renderer, Callback callback, boolean isSecretChat) {
        this.renderer = renderer;
        this.callback = callback;
        this.isSecretChat = isSecretChat;
        encoderQueue = new DispatchQueue("RoundVideoEncoder", false);
        encoderQueue.setPriority(Thread.MAX_PRIORITY);
        encoderQueue.start();
    }

    public boolean isStarted() {
        return started;
    }

    /**
     * Starts a new recording, or resumes a paused one when a new camera GL context
     * appears after {@link #pause}. Called on the camera GL thread; the shared EGL
     * context must be the caller's current context.
     */
    public void startRecording(File outputFile, EGLContext sharedContext, int requestedFrameRate) {
        started = true;
        encoderQueue.postRunnable(() -> handleStart(outputFile, sharedContext, requestedFrameRate));
    }

    /**
     * Submits the current camera texture state. Called on the camera GL thread right
     * after SurfaceTexture.updateTexImage() with a caller-owned snapshot of this
     * frame's state; the snapshot is copied out before the call returns. Pending
     * submissions are coalesced: the newest snapshot wins, the frame scheduler
     * decides whether it is encoded.
     */
    public void frameAvailable(FrameSnapshot snapshot) {
        if (!started) {
            return;
        }
        synchronized (pendingFrameLock) {
            pendingFrame.copyFrom(snapshot);
            if (pendingFrameSet) {
                return;
            }
            pendingFrameSet = true;
        }
        encoderQueue.postRunnable(this::handleFrame);
    }

    /**
     * Pauses the recording and writes a playable snapshot of everything recorded so
     * far into {@code previewFile} without finalizing the main file. The paused
     * interval is excluded from the output timeline.
     */
    public void pause(File previewFile) {
        AudioCaptureSession session = getActiveAudioCapture();
        encoderQueue.postRunnable(() -> handlePause(previewFile));
        requestAudioCaptureStop(session);
    }

    /**
     * Finalizes the recording. Idempotent, first stop()/cancel() wins.
     */
    public void stop() {
        if (!finishRequested.compareAndSet(false, true)) {
            return;
        }
        AudioCaptureSession session = getActiveAudioCapture();
        encoderQueue.postRunnable(() -> handleFinish(false));
        requestAudioCaptureStop(session);
    }

    /**
     * Discards the recording and releases everything. Idempotent, first
     * stop()/cancel() wins.
     */
    public void cancel() {
        if (!finishRequested.compareAndSet(false, true)) {
            return;
        }
        AudioCaptureSession session = getActiveAudioCapture();
        encoderQueue.postRunnable(() -> handleFinish(true));
        requestAudioCaptureStop(session);
    }

    private void handleStart(File outputFile, EGLContext sharedContext, int requestedFrameRate) {
        if (state == STATE_PAUSED) {
            handleResume(sharedContext);
            return;
        }
        if (state == STATE_PAUSING) {
            // resume requested before the pause snapshot finished, run it right
            // after finishPause() instead of dropping it
            pendingResumeContext = sharedContext;
            return;
        }
        if (state != STATE_IDLE) {
            return;
        }
        state = STATE_PREPARING;
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("RoundVideoEncoder start " + outputFile);
        }
        try {
            videoFile = outputFile;
            videoWidth = SystemUtils.getRoundVideoResolution();
            videoHeight = videoWidth;
            videoBitrate = SystemUtils.getRoundVideoBitrate() * 1024;
            frameRate = requestedFrameRate == EXTENDED_FRAME_RATE ? EXTENDED_FRAME_RATE : FRAME_RATE;
            maxDurationUs = SystemUtils.getRoundVideoMaxDurationMs() * 1000L;
            audioCapUs = maxDurationUs;
            allowSendingWhileRecording = SharedConfig.deviceIsHigh();
            prepareAudioBatches();

            videoBufferInfo = new MediaCodec.BufferInfo();
            audioBufferInfo = new MediaCodec.BufferInfo();

            MediaFormat audioFormat = new MediaFormat();
            audioFormat.setString(MediaFormat.KEY_MIME, AUDIO_MIME_TYPE);
            audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, AUDIO_SAMPLE_RATE);
            audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, SystemUtils.getRoundAudioBitrate() * 1024);
            audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AudioChunkBatch.CHUNK_SIZE * AudioChunkBatch.MAX_CHUNKS);

            audioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
            audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            audioEncoder.start();

            MediaFormat format = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, videoWidth, videoHeight);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, videoBitrate);
            format.setInteger("max-bitrate", videoBitrate);
            videoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
            try {
                if (videoEncoder.getCodecInfo().getCapabilitiesForType(VIDEO_MIME_TYPE).getEncoderCapabilities().isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)) {
                    format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
            videoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = videoEncoder.createInputSurface();
            videoEncoder.start();
            firstEncode = true;

            fileToWrite = videoFile;
            writingToDifferentFile = false;
            if (ImageLoader.isSdCardPath(videoFile)) {
                try {
                    fileToWrite = new File(ApplicationLoader.getFilesDirFixed(), "camera_tmp.mp4");
                    if (fileToWrite.exists()) {
                        fileToWrite.delete();
                    }
                    writingToDifferentFile = true;
                } catch (Throwable e) {
                    FileLog.e(e);
                    fileToWrite = videoFile;
                    writingToDifferentFile = false;
                }
            }
            Mp4Movie movie = new Mp4Movie();
            movie.setCacheFile(fileToWrite);
            movie.setRotation(0);
            movie.setSize(videoWidth, videoHeight);
            mediaMuxer = new MP4Builder().createMovie(movie, isSecretChat, false);
            mediaMuxer.setAllowSyncFiles(false);

            createEncoderEgl(sharedContext);
            startAudioCapture();

            state = STATE_RECORDING;
            AndroidUtilities.runOnUIThread(() -> callback.onRecordingStarted(false));
        } catch (Throwable e) {
            FileLog.e(e);
            fail();
        }
    }

    private void handleResume(EGLContext sharedContext) {
        if (state != STATE_PAUSED) {
            return;
        }
        state = STATE_PREPARING;
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("RoundVideoEncoder resume");
        }
        try {
            // new camera, new SurfaceTexture timestamp origin: re-anchor everything
            sourceAnchorSet = false;
            segmentFirstArrivalNs = -1;
            segmentVideoOriginNs = -1;
            audioSegmentBaseUs = -1;
            audioCapUs = maxDurationUs;

            createEncoderEgl(sharedContext);
            startAudioCapture();

            state = STATE_RECORDING;
            AndroidUtilities.runOnUIThread(() -> callback.onRecordingStarted(true));
        } catch (Throwable e) {
            FileLog.e(e);
            fail();
        }
    }

    private void handleFrame() {
        synchronized (pendingFrameLock) {
            if (!pendingFrameSet) {
                return;
            }
            pendingFrameSet = false;
            currentFrame.copyFrom(pendingFrame);
        }
        long sourceTimestampNs = currentFrame.sourceTimestampNs;
        long arrivalTimeNs = currentFrame.arrivalTimeNs;
        int cameraId = currentFrame.cameraId;
        if (state != STATE_RECORDING) {
            return;
        }
        try {
            drainEncoders();
        } catch (Exception e) {
            FileLog.e(e);
            fail();
            return;
        }
        feedPendingAudio();

        // map the source timestamp onto the monotonic clock, re-anchoring on camera
        // switches and on invalid or implausible timestamps
        boolean discontinuity = cameraId != lastCameraId;
        lastCameraId = cameraId;
        long frameTimeNs;
        if (sourceTimestampNs <= 0) {
            frameTimeNs = arrivalTimeNs;
            sourceAnchorSet = false;
            discontinuity = true;
        } else {
            if (!sourceAnchorSet || discontinuity || sourceTimestampNs <= lastSourceTimestampNs || sourceTimestampNs - lastSourceTimestampNs > MAX_SOURCE_JUMP_NS) {
                sourceAnchorSourceNs = sourceTimestampNs;
                sourceAnchorMonotonicNs = arrivalTimeNs;
                sourceAnchorSet = true;
                discontinuity = true;
                frameTimeNs = arrivalTimeNs;
            } else {
                frameTimeNs = sourceAnchorMonotonicNs + (sourceTimestampNs - sourceAnchorSourceNs);
            }
            lastSourceTimestampNs = sourceTimestampNs;
        }

        if (segmentFirstArrivalNs == -1) {
            segmentFirstArrivalNs = arrivalTimeNs;
        }
        if (arrivalTimeNs - segmentFirstArrivalNs < SETTLE_TIME_NS) {
            return;
        }

        if (segmentVideoOriginNs == -1) {
            // first accepted frame of this segment: continue right after the last
            // encoded frame of the previous segment (time 0 for a fresh recording)
            long activeTimeNs = lastVideoFrameIndex < 0 ? 0 : lastVideoActiveTimeNs + videoFallbackFrameDurationNs();
            if (activeTimeNs / 1000 >= maxDurationUs) {
                return;
            }
            long frameIndex = activeTimeNs * frameRate / 1_000_000_000L;
            segmentVideoOriginNs = frameTimeNs;
            segmentActiveBaseNs = activeTimeNs;
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("RoundVideoEncoder segment origin at " + activeTimeNs + "ns slot " + frameIndex);
            }
            if (acceptFrame(frameIndex, activeTimeNs, 0)) {
                feedPendingAudio();
            }
            return;
        }

        long activeTimeNs = segmentActiveBaseNs + (frameTimeNs - segmentVideoOriginNs);
        long frameIndex = activeTimeNs * frameRate / 1_000_000_000L;
        if (frameIndex <= lastVideoFrameIndex) {
            // frame rate limit: this frame slot already got a frame
            return;
        }
        if (activeTimeNs / 1000 >= maxDurationUs) {
            return;
        }
        acceptFrame(frameIndex, activeTimeNs, discontinuity ? 0 : activeTimeNs - lastVideoActiveTimeNs);
    }

    private boolean acceptFrame(long frameIndex, long activeTimeNs, long frameDeltaNs) {
        if (!makeEglCurrent()) {
            fail();
            return false;
        }
        boolean drawn;
        try {
            drawn = renderer.onDrawEncoderFrame(frameDeltaNs, currentFrame);
        } catch (Throwable e) {
            FileLog.e(e);
            fail();
            return false;
        }
        if (!drawn) {
            return false;
        }
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, activeTimeNs);
        if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
            FileLog.e("RoundVideoEncoder eglSwapBuffers failed at frame " + frameIndex + ": " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
            fail();
            return false;
        }
        if (lastVideoFrameIndex >= 0 && frameDeltaNs > 0) {
            if (minVideoFrameDeltaNs == 0 || frameDeltaNs < minVideoFrameDeltaNs) {
                minVideoFrameDeltaNs = frameDeltaNs;
            }
        }
        lastVideoFrameIndex = frameIndex;
        lastVideoActiveTimeNs = activeTimeNs;
        lastSubmittedVideoPtsUs = activeTimeNs / 1000;
        return true;
    }

    private void handleAudioBatch(AudioChunkBatch batch) {
        boolean accept = state == STATE_RECORDING || state == STATE_PAUSING || state == STATE_STOPPING && waitingAudioTail;
        if (accept) {
            pendingAudio.add(batch);
            if (segmentVideoOriginNs == -1 && pendingAudio.size() > MAX_PENDING_AUDIO_BATCHES) {
                // video never started, keep only the most recent pre-origin audio
                recycleAudioBatch(pendingAudio.remove(0));
            }
            try {
                drainEncoders();
            } catch (Exception e) {
                FileLog.e(e);
                fail();
                return;
            }
            feedPendingAudio();
        } else {
            recycleAudioBatch(batch);
        }
    }

    private void handleAudioCaptureFinished(AudioCaptureSession session) {
        if (session.generation != audioCaptureGeneration) {
            return;
        }
        audioCaptureRunning = false;
        if (deferredAudioCleanup != DEFERRED_AUDIO_CLEANUP_NONE) {
            int cleanup = deferredAudioCleanup;
            deferredAudioCleanup = DEFERRED_AUDIO_CLEANUP_NONE;
            if (cleanup == DEFERRED_AUDIO_CLEANUP_CANCEL) {
                finalizeCancel();
            } else {
                finalizeFailure();
            }
            return;
        }
        if (state == STATE_PAUSING) {
            finishPause();
        } else if (state == STATE_STOPPING && waitingAudioTail) {
            waitingAudioTail = false;
            finalizeStop();
        } else if (state == STATE_RECORDING) {
            FileLog.e("RoundVideoEncoder audio capture ended unexpectedly");
            fail();
        }
    }

    /**
     * Establishes the audio base of the current segment once the first accepted
     * video frame defined the segment origin: whole pre-origin buffers are dropped,
     * a partial one is trimmed to the origin, and if the microphone genuinely
     * started late the measured offset is preserved.
     */
    private void alignAudioSegment() {
        while (!pendingAudio.isEmpty()) {
            AudioChunkBatch batch = pendingAudio.get(0);
            for (; batch.drained < batch.results; batch.drained++) {
                ByteBuffer bb = batch.buffer[batch.drained];
                int frames = bb.remaining() / 2;
                if (frames <= 0) {
                    continue;
                }
                long startTimeNs = batch.startTimeNs[batch.drained];
                long endTimeNs = startTimeNs + frames * 1_000_000_000L / AUDIO_SAMPLE_RATE;
                if (endTimeNs <= segmentVideoOriginNs) {
                    continue;
                }
                long effectiveStartNs = startTimeNs;
                if (startTimeNs < segmentVideoOriginNs) {
                    int trimFrames = (int) ((segmentVideoOriginNs - startTimeNs) * AUDIO_SAMPLE_RATE / 1_000_000_000L);
                    if (trimFrames >= frames) {
                        continue;
                    }
                    bb.position(bb.position() + trimFrames * 2);
                    effectiveStartNs = startTimeNs + trimFrames * 1_000_000_000L / AUDIO_SAMPLE_RATE;
                }
                long activeTimeUs = (segmentActiveBaseNs + (effectiveStartNs - segmentVideoOriginNs)) / 1000;
                audioSegmentBaseUs = Math.max(activeTimeUs, audioTotalEndUs);
                audioSegmentFramesSubmitted = 0;
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("RoundVideoEncoder audio segment base " + audioSegmentBaseUs + "us");
                }
                return;
            }
            pendingAudio.remove(0);
            recycleAudioBatch(batch);
        }
    }

    private void feedPendingAudio() {
        if (audioEncoder == null || audioEosQueued || segmentVideoOriginNs == -1) {
            return;
        }
        if (audioSegmentBaseUs == -1) {
            alignAudioSegment();
            if (audioSegmentBaseUs == -1) {
                return;
            }
        }
        while (hasFeedablePendingAudio()) {
            long capFramesLeft = (audioCapUs - audioSegmentBaseUs) * AUDIO_SAMPLE_RATE / 1_000_000L - audioSegmentFramesSubmitted;
            if (capFramesLeft <= 0) {
                recyclePendingAudio();
                return;
            }
            int inputBufferIndex;
            ByteBuffer inputBuffer;
            try {
                inputBufferIndex = audioEncoder.dequeueInputBuffer(0);
                if (inputBufferIndex < 0) {
                    return;
                }
                inputBuffer = audioEncoder.getInputBuffer(inputBufferIndex);
            } catch (Exception e) {
                FileLog.e(e);
                failIfActive();
                return;
            }
            if (inputBuffer == null) {
                FileLog.e("RoundVideoEncoder audio input buffer was null");
                failIfActive();
                return;
            }
            inputBuffer.clear();
            long presentationTimeUs = audioSegmentBaseUs + audioSegmentFramesSubmitted * 1_000_000L / AUDIO_SAMPLE_RATE;
            int written = 0;
            boolean capped = false;
            while (!pendingAudio.isEmpty() && !capped) {
                AudioChunkBatch batch = pendingAudio.get(0);
                boolean batchDone = true;
                for (; batch.drained < batch.results; batch.drained++) {
                    ByteBuffer bb = batch.buffer[batch.drained];
                    int remaining = bb.remaining();
                    if (remaining <= 0) {
                        continue;
                    }
                    if (remaining / 2 > capFramesLeft) {
                        bb.limit(bb.position() + (int) capFramesLeft * 2);
                        remaining = bb.remaining();
                        capped = true;
                        if (remaining <= 0) {
                            break;
                        }
                    }
                    if (inputBuffer.remaining() < remaining) {
                        batchDone = false;
                        break;
                    }
                    inputBuffer.put(bb);
                    audioSegmentFramesSubmitted += remaining / 2;
                    capFramesLeft -= remaining / 2;
                    written += remaining;
                    if (capped) {
                        break;
                    }
                }
                if (!batchDone) {
                    break;
                }
                if (batch.drained >= batch.results) {
                    pendingAudio.remove(0);
                    recycleAudioBatch(batch);
                }
                if (capped) {
                    // everything past the cap is dropped, nothing left to submit
                    recyclePendingAudio();
                }
            }
            audioTotalEndUs = audioSegmentBaseUs + audioSegmentFramesSubmitted * 1_000_000L / AUDIO_SAMPLE_RATE;
            try {
                if (written > 0) {
                    audioEncoder.queueInputBuffer(inputBufferIndex, 0, written, presentationTimeUs, 0);
                    lastSubmittedAudioEndUs = audioTotalEndUs;
                } else {
                    audioEncoder.queueInputBuffer(inputBufferIndex, 0, 0, presentationTimeUs, 0);
                    return;
                }
            } catch (Exception e) {
                FileLog.e(e);
                failIfActive();
                return;
            }
        }
    }

    private void failIfActive() {
        if (state == STATE_PREPARING || state == STATE_RECORDING || state == STATE_PAUSING || state == STATE_PAUSED) {
            fail();
        }
    }

    private boolean hasFeedablePendingAudio() {
        while (!pendingAudio.isEmpty()) {
            AudioChunkBatch batch = pendingAudio.get(0);
            boolean hasData = false;
            for (int a = batch.drained; a < batch.results; a++) {
                if (batch.buffer[a].remaining() > 0) {
                    hasData = true;
                    break;
                }
            }
            if (hasData) {
                return true;
            }
            pendingAudio.remove(0);
            recycleAudioBatch(batch);
        }
        return false;
    }

    private void handlePause(File previewFile) {
        if (state != STATE_RECORDING) {
            return;
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("RoundVideoEncoder pause");
        }
        state = STATE_PAUSING;
        pausePreviewFile = previewFile;
        // the paused interval must not appear on the timeline: cap audio at the end
        // of the last encoded video frame and wait for the capture tail
        audioCapUs = Math.min(audioCapUs, videoEndTimeUs());
        if (audioCaptureRunning) {
            stopAudioCapture(false);
            // finishPause() runs after the capture thread releases AudioRecord
        } else {
            finishPause();
        }
    }

    private void finishPause() {
        feedPendingAudio();
        try {
            long deadline = SystemClock.elapsedRealtime() + PAUSE_DRAIN_TIMEOUT_MS;
            while (SystemClock.elapsedRealtime() < deadline) {
                drainVideoOnce(10_000);
                drainAudioOnce(10_000);
                feedPendingAudio();
                if (!hasFeedablePendingAudio() && hasReachedPauseTargets()) {
                    break;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
            fail();
            return;
        }
        recyclePendingAudio();
        if (mediaMuxer != null && pausePreviewFile != null) {
            try {
                mediaMuxer.setAllowSyncFiles(allowSendingWhileRecording);
                mediaMuxer.finishMovie(pausePreviewFile);
                mediaMuxer.setAllowSyncFiles(false);
            } catch (Exception e) {
                FileLog.e(e);
                fail();
                return;
            }
        }
        // the encoder EGL context is recreated on resume so it can share with the
        // new camera GL context; the codecs and MP4 tracks stay alive
        releaseEgl();
        final File previewFile = pausePreviewFile;
        pausePreviewFile = null;
        state = STATE_PAUSED;
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("RoundVideoEncoder paused, video end " + videoEndTimeUs() + "us audio end " + audioTotalEndUs + "us");
        }
        if (deferredFinish != DEFERRED_FINISH_NONE) {
            // stop/cancel arrived while the snapshot was written, the UI is not
            // waiting for the pause preview anymore
            boolean cancel = deferredFinish == DEFERRED_FINISH_CANCEL;
            deferredFinish = DEFERRED_FINISH_NONE;
            pendingResumeContext = null;
            handleFinish(cancel);
        } else if (pendingResumeContext != null) {
            // resume arrived while the snapshot was written
            EGLContext sharedContext = pendingResumeContext;
            pendingResumeContext = null;
            handleResume(sharedContext);
        } else {
            AndroidUtilities.runOnUIThread(() -> callback.onPaused(previewFile));
        }
    }

    private void handleFinish(boolean cancel) {
        if (state == STATE_RELEASED || state == STATE_FAILED || state == STATE_STOPPING) {
            return;
        }
        if (state == STATE_PAUSING) {
            deferredFinish = cancel ? DEFERRED_FINISH_CANCEL : DEFERRED_FINISH_STOP;
            return;
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("RoundVideoEncoder finish cancel=" + cancel + " state=" + state);
        }
        if (state == STATE_IDLE) {
            state = STATE_RELEASED;
            scheduleQueueRecycle();
            FinishReason reason = cancel ? FinishReason.CANCELLED : FinishReason.COMPLETED;
            AndroidUtilities.runOnUIThread(() -> callback.onFinished(reason));
            return;
        }
        state = STATE_STOPPING;
        if (cancel) {
            if (stopAudioCapture(true)) {
                finalizeCancel();
            } else {
                deferredAudioCleanup = DEFERRED_AUDIO_CLEANUP_CANCEL;
            }
            return;
        }
        audioCapUs = Math.min(audioCapUs, videoEndTimeUs());
        if (videoEncoder != null && !videoEosSignalled) {
            try {
                videoEncoder.signalEndOfInputStream();
                videoEosSignalled = true;
            } catch (Exception e) {
                FileLog.e(e);
                videoEosSeen = true;
            }
        }
        if (audioCaptureRunning) {
            stopAudioCapture(false);
            waitingAudioTail = true;
            // finalizeStop() runs after the capture thread releases AudioRecord
        } else {
            finalizeStop();
        }
    }

    private void finalizeStop() {
        if (segmentVideoOriginNs == -1 && audioSegmentBaseUs == -1) {
            // no video was ever accepted in this segment, drop unaligned audio
            recyclePendingAudio();
        }
        boolean muxerFinished = drainToEndOfStream();
        releaseEgl();
        releaseCodecs();
        if (mediaMuxer != null) {
            try {
                mediaMuxer.setAllowSyncFiles(allowSendingWhileRecording);
                mediaMuxer.finishMovie();
            } catch (Exception e) {
                FileLog.e(e);
                muxerFinished = false;
            }
            FileLog.d("RoundVideoEncoder finished muxer, video end " + videoEndTimeUs() + "us audio end " + audioTotalEndUs + "us");
            if (writingToDifferentFile) {
                if (videoFile.exists()) {
                    try {
                        videoFile.delete();
                    } catch (Exception e) {
                        FileLog.e("RoundVideoEncoder copying fileToWrite to videoFile, deleting videoFile error " + videoFile);
                        FileLog.e(e);
                    }
                }
                if (!fileToWrite.renameTo(videoFile)) {
                    FileLog.e("RoundVideoEncoder unable to rename file, try move file");
                    try {
                        if (AndroidUtilities.copyFile(fileToWrite, videoFile)) {
                            fileToWrite.delete();
                        } else {
                            muxerFinished = false;
                            FileLog.e("RoundVideoEncoder unable to copy file");
                        }
                    } catch (IOException e) {
                        muxerFinished = false;
                        FileLog.e(e);
                        FileLog.e("RoundVideoEncoder unable to move file");
                    }
                }
            }
        }
        if (!muxerFinished) {
            if (fileToWrite != null) {
                try {
                    fileToWrite.delete();
                } catch (Throwable ignore) {}
            }
            if (videoFile != null && !videoFile.equals(fileToWrite)) {
                try {
                    videoFile.delete();
                } catch (Throwable ignore) {}
            }
        }
        releaseInputSurface();
        recyclePendingAudio();
        state = muxerFinished ? STATE_RELEASED : STATE_FAILED;
        scheduleQueueRecycle();
        FinishReason reason = muxerFinished ? FinishReason.COMPLETED : FinishReason.FAILED;
        AndroidUtilities.runOnUIThread(() -> callback.onFinished(reason));
    }

    private void finalizeCancel() {
        releaseEgl();
        releaseCodecs();
        releaseInputSurface();
        recyclePendingAudio();
        if (mediaMuxer != null) {
            try {
                mediaMuxer.finishMovie();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (fileToWrite != null) {
            try {
                fileToWrite.delete();
            } catch (Throwable ignore) {}
        }
        if (videoFile != null) {
            try {
                videoFile.delete();
            } catch (Throwable ignore) {}
        }
        state = STATE_RELEASED;
        scheduleQueueRecycle();
        AndroidUtilities.runOnUIThread(() -> callback.onFinished(FinishReason.CANCELLED));
    }

    private void fail() {
        if (state == STATE_RELEASED || state == STATE_FAILED || deferredAudioCleanup != DEFERRED_AUDIO_CLEANUP_NONE) {
            return;
        }
        FileLog.e("RoundVideoEncoder failed in state " + state);
        state = STATE_STOPPING;
        if (!stopAudioCapture(true)) {
            deferredAudioCleanup = DEFERRED_AUDIO_CLEANUP_FAILURE;
            return;
        }
        finalizeFailure();
    }

    private void finalizeFailure() {
        releaseEgl();
        releaseCodecs();
        releaseInputSurface();
        recyclePendingAudio();
        if (mediaMuxer != null) {
            try {
                mediaMuxer.finishMovie();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (fileToWrite != null) {
            try {
                fileToWrite.delete();
            } catch (Throwable ignore) {}
        }
        if (videoFile != null) {
            try {
                videoFile.delete();
            } catch (Throwable ignore) {}
        }
        state = STATE_FAILED;
        scheduleQueueRecycle();
        AndroidUtilities.runOnUIThread(() -> callback.onFinished(FinishReason.FAILED));
    }

    /**
     * True once everything submitted to the codecs before the pause has actually
     * been muxed: the last submitted video frame and, for audio, everything except
     * the tail the AAC encoder cannot emit until it gets more input.
     */
    private boolean hasReachedPauseTargets() {
        boolean videoReached = lastSubmittedVideoPtsUs < 0 || lastMuxedVideoPtsUs >= lastSubmittedVideoPtsUs;
        long audioTargetPtsUs = lastSubmittedAudioEndUs < 0 ? -1 : Math.max(0, lastSubmittedAudioEndUs - AUDIO_CODEC_FRAME_DURATION_US);
        boolean audioReached = audioTargetPtsUs < 0 || lastMuxedAudioPtsUs >= audioTargetPtsUs;
        return videoReached && audioReached;
    }

    private void scheduleQueueRecycle() {
        encoderQueue.postRunnable(encoderQueue::recycle);
    }

    // duration assumed for the last frame of a segment, whose real end is unknown:
    // the smallest delta seen so far, or the nominal frame interval before that
    private long videoFallbackFrameDurationNs() {
        if (minVideoFrameDeltaNs > 0) {
            return Math.max(minVideoFrameDeltaNs, 1_000_000L);
        }
        return 1_000_000_000L / frameRate;
    }

    private long videoEndTimeUs() {
        if (lastVideoFrameIndex < 0) {
            return 0;
        }
        return (lastVideoActiveTimeNs + videoFallbackFrameDurationNs()) / 1000;
    }

    private boolean drainToEndOfStream() {
        long deadline = SystemClock.elapsedRealtime() + EOS_DRAIN_TIMEOUT_MS;
        if (audioEncoder == null) {
            audioEosSeen = true;
        }
        if (videoEncoder == null || !videoEosSignalled) {
            videoEosSeen = true;
        }
        while ((!videoEosSeen || !audioEosSeen) && SystemClock.elapsedRealtime() < deadline) {
            if (!audioEosQueued && !audioEosSeen) {
                feedPendingAudio();
                if (!hasFeedablePendingAudio()) {
                    queueAudioEndOfStream();
                }
            }
            try {
                if (!videoEosSeen) {
                    drainVideoOnce(10_000);
                }
                if (!audioEosSeen) {
                    drainAudioOnce(10_000);
                }
            } catch (Exception e) {
                FileLog.e(e);
                break;
            }
        }
        if (!videoEosSeen || !audioEosSeen) {
            FileLog.e("RoundVideoEncoder end of stream drain timed out, video=" + videoEosSeen + " audio=" + audioEosSeen);
        }
        return videoEosSeen && audioEosSeen;
    }

    private void queueAudioEndOfStream() {
        try {
            int inputBufferIndex = audioEncoder.dequeueInputBuffer(10_000);
            if (inputBufferIndex >= 0) {
                audioEncoder.queueInputBuffer(inputBufferIndex, 0, 0, audioTotalEndUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                audioEosQueued = true;
            }
        } catch (Exception e) {
            FileLog.e(e);
            audioEosSeen = true;
        }
    }

    private void drainEncoders() throws Exception {
        if (videoEncoder != null) {
            while (drainVideoOnce(0)) {
            }
        }
        if (audioEncoder != null) {
            while (drainAudioOnce(0)) {
            }
        }
    }

    private boolean drainVideoOnce(long timeoutUs) throws Exception {
        if (videoEncoder == null) {
            return false;
        }
        int encoderStatus = videoEncoder.dequeueOutputBuffer(videoBufferInfo, timeoutUs);
        if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
            return false;
        }
        if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
            return true;
        }
        if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            MediaFormat newFormat = videoEncoder.getOutputFormat();
            if (videoTrackIndex == -5 && mediaMuxer != null) {
                videoTrackIndex = mediaMuxer.addTrack(newFormat, false);
                if (newFormat.containsKey(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES) && newFormat.getInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES) == 1) {
                    ByteBuffer spsBuff = newFormat.getByteBuffer("csd-0");
                    ByteBuffer ppsBuff = newFormat.getByteBuffer("csd-1");
                    prependHeaderSize = (spsBuff == null ? 0 : spsBuff.limit()) + (ppsBuff == null ? 0 : ppsBuff.limit());
                }
            }
            return true;
        }
        if (encoderStatus < 0) {
            return false;
        }
        try {
            ByteBuffer encodedData = videoEncoder.getOutputBuffer(encoderStatus);
            if (encodedData == null) {
                throw new RuntimeException("videoEncoderOutputBuffer " + encoderStatus + " was null");
            }
            if (videoBufferInfo.size > 1) {
                if ((videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                    if (prependHeaderSize != 0 && (videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                        videoBufferInfo.offset += prependHeaderSize;
                        videoBufferInfo.size -= prependHeaderSize;
                    }
                    if (firstEncode && (videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                        MediaCodecVideoConvertor.cutOfNalData(VIDEO_MIME_TYPE, encodedData, videoBufferInfo);
                        firstEncode = false;
                    }
                    if (mediaMuxer != null && videoTrackIndex >= 0) {
                        long availableSize = mediaMuxer.writeSampleData(videoTrackIndex, encodedData, videoBufferInfo, true);
                        lastMuxedVideoPtsUs = Math.max(lastMuxedVideoPtsUs, videoBufferInfo.presentationTimeUs);
                        if (availableSize != 0 && !writingToDifferentFile && allowSendingWhileRecording) {
                            callback.onWriteData(availableSize);
                        }
                    }
                } else if (videoTrackIndex == -5 && mediaMuxer != null) {
                    byte[] csd = new byte[videoBufferInfo.size];
                    encodedData.limit(videoBufferInfo.offset + videoBufferInfo.size);
                    encodedData.position(videoBufferInfo.offset);
                    encodedData.get(csd);
                    ByteBuffer sps = null;
                    ByteBuffer pps = null;
                    for (int a = videoBufferInfo.size - 1; a >= 0; a--) {
                        if (a > 3) {
                            if (csd[a] == 1 && csd[a - 1] == 0 && csd[a - 2] == 0 && csd[a - 3] == 0) {
                                sps = ByteBuffer.allocate(a - 3);
                                pps = ByteBuffer.allocate(videoBufferInfo.size - (a - 3));
                                sps.put(csd, 0, a - 3).position(0);
                                pps.put(csd, a - 3, videoBufferInfo.size - (a - 3)).position(0);
                                break;
                            }
                        } else {
                            break;
                        }
                    }
                    MediaFormat newFormat = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, videoWidth, videoHeight);
                    if (sps != null) {
                        newFormat.setByteBuffer("csd-0", sps);
                        newFormat.setByteBuffer("csd-1", pps);
                    }
                    videoTrackIndex = mediaMuxer.addTrack(newFormat, false);
                }
            }
            if ((videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                videoEosSeen = true;
                return false;
            }
        } finally {
            videoEncoder.releaseOutputBuffer(encoderStatus, false);
        }
        return true;
    }

    private boolean drainAudioOnce(long timeoutUs) throws Exception {
        if (audioEncoder == null) {
            return false;
        }
        int encoderStatus = audioEncoder.dequeueOutputBuffer(audioBufferInfo, timeoutUs);
        if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
            return false;
        }
        if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
            return true;
        }
        if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            MediaFormat newFormat = audioEncoder.getOutputFormat();
            if (audioTrackIndex == -5 && mediaMuxer != null) {
                audioTrackIndex = mediaMuxer.addTrack(newFormat, true);
            }
            return true;
        }
        if (encoderStatus < 0) {
            return false;
        }
        try {
            ByteBuffer encodedData = audioEncoder.getOutputBuffer(encoderStatus);
            if (encodedData == null) {
                throw new RuntimeException("audioEncoderOutputBuffer " + encoderStatus + " was null");
            }
            if ((audioBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                audioBufferInfo.size = 0;
            }
            if (audioBufferInfo.size != 0 && mediaMuxer != null && audioTrackIndex >= 0) {
                long availableSize = mediaMuxer.writeSampleData(audioTrackIndex, encodedData, audioBufferInfo, false);
                lastMuxedAudioPtsUs = Math.max(lastMuxedAudioPtsUs, audioBufferInfo.presentationTimeUs);
                if (availableSize != 0 && !writingToDifferentFile && allowSendingWhileRecording) {
                    callback.onWriteData(availableSize);
                }
            }
            if ((audioBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                audioEosSeen = true;
                return false;
            }
        } finally {
            audioEncoder.releaseOutputBuffer(encoderStatus, false);
        }
        return true;
    }

    private void createEncoderEgl(EGLContext sharedContext) {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("EGL already set up");
        }
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("unable to get EGL14 display");
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            eglDisplay = EGL14.EGL_NO_DISPLAY;
            throw new RuntimeException("unable to initialize EGL14");
        }
        int[] attribList = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                0x3142 /* EGL_RECORDABLE_ANDROID */, 1,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.length, numConfigs, 0)) {
            throw new RuntimeException("Unable to find a suitable EGLConfig");
        }
        int[] contextAttribs = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        eglConfig = configs[0];
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, sharedContext, contextAttribs, 0);
        if (eglContext == null || eglContext == EGL14.EGL_NO_CONTEXT) {
            eglContext = EGL14.EGL_NO_CONTEXT;
            throw new RuntimeException("eglCreateContext failed " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }
        int[] surfaceAttribs = {
                EGL14.EGL_NONE
        };
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, inputSurface, surfaceAttribs, 0);
        if (eglSurface == null || eglSurface == EGL14.EGL_NO_SURFACE) {
            eglSurface = EGL14.EGL_NO_SURFACE;
            throw new RuntimeException("eglCreateWindowSurface failed " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw new RuntimeException("eglMakeCurrent failed " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }
        try {
            renderer.onEncoderSurfaceCreated(videoWidth, videoHeight);
        } catch (Throwable e) {
            throw new RuntimeException("encoder renderer initialization failed", e);
        }
    }

    private boolean makeEglCurrent() {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglSurface == EGL14.EGL_NO_SURFACE) {
            return false;
        }
        if (!eglContext.equals(EGL14.eglGetCurrentContext()) || !eglSurface.equals(EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW))) {
            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                FileLog.e("RoundVideoEncoder eglMakeCurrent failed " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
                return false;
            }
        }
        return true;
    }

    private void releaseEgl() {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            return;
        }
        if (makeEglCurrent()) {
            try {
                renderer.onEncoderSurfaceDestroyed();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        if (eglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(eglDisplay, eglSurface);
            eglSurface = EGL14.EGL_NO_SURFACE;
        }
        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
        if (eglContext != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglDestroyContext(eglDisplay, eglContext);
            eglContext = EGL14.EGL_NO_CONTEXT;
        }
        EGL14.eglReleaseThread();
        EGL14.eglTerminate(eglDisplay);
        eglDisplay = EGL14.EGL_NO_DISPLAY;
        eglConfig = null;
    }

    private void releaseInputSurface() {
        if (inputSurface != null) {
            try {
                inputSurface.release();
            } catch (Throwable e) {
                FileLog.e(e);
            }
            inputSurface = null;
        }
    }

    private void releaseCodecs() {
        if (videoEncoder != null) {
            try {
                videoEncoder.stop();
            } catch (Throwable e) {
                FileLog.e(e);
            }
            try {
                videoEncoder.release();
            } catch (Throwable e) {
                FileLog.e(e);
            }
            videoEncoder = null;
        }
        if (audioEncoder != null) {
            try {
                audioEncoder.stop();
            } catch (Throwable e) {
                FileLog.e(e);
            }
            try {
                audioEncoder.release();
            } catch (Throwable e) {
                FileLog.e(e);
            }
            audioEncoder = null;
            setBluetoothScoOn(false);
        }
    }

    private void recyclePendingAudio() {
        for (int a = 0; a < pendingAudio.size(); a++) {
            recycleAudioBatch(pendingAudio.get(a));
        }
        pendingAudio.clear();
    }

    private void prepareAudioBatches() {
        if (audioBatchesPrepared) {
            return;
        }
        for (int a = 0; a < AUDIO_BATCH_POOL_SIZE; a++) {
            audioBatchPool.add(new AudioChunkBatch());
        }
        audioBatchesPrepared = true;
    }

    private AudioChunkBatch obtainAudioBatch() throws InterruptedException {
        AudioChunkBatch batch = audioBatchPool.poll(AUDIO_BATCH_POOL_WAIT_MS, TimeUnit.MILLISECONDS);
        if (batch == null) {
            return null;
        }
        batch.results = 0;
        batch.drained = 0;
        return batch;
    }

    private void recycleAudioBatch(AudioChunkBatch batch) {
        if (!audioBatchPool.offer(batch)) {
            FileLog.e("RoundVideoEncoder audio batch pool overflow");
        }
    }

    private void startAudioCapture() {
        setBluetoothScoOn(true);
        int recordBufferSize = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (recordBufferSize <= 0) {
            recordBufferSize = 3584;
        }
        int bufferSize = AudioChunkBatch.CHUNK_SIZE * 24;
        if (bufferSize < recordBufferSize) {
            bufferSize = ((recordBufferSize / AudioChunkBatch.CHUNK_SIZE) + 1) * AudioChunkBatch.CHUNK_SIZE * 2;
        }
        AudioRecord audioRecorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        AudioCaptureSession session = null;
        boolean threadStarted = false;
        try {
            if (audioRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new RuntimeException("AudioRecord init failed");
            }
            audioRecorder.startRecording();
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("RoundVideoEncoder initied audio record with channels " + audioRecorder.getChannelCount() + " sample rate = " + audioRecorder.getSampleRate() + " bufferSize = " + bufferSize);
            }
            audioCaptureRunning = true;
            int generation = ++audioCaptureGeneration;
            session = new AudioCaptureSession(audioRecorder, generation);
            Thread thread = new Thread(new AudioCaptureRunnable(session), "RoundVideoAudioCapture");
            session.thread = thread;
            thread.setPriority(Thread.MAX_PRIORITY);
            synchronized (audioCaptureLock) {
                activeAudioCapture = session;
            }
            thread.start();
            threadStarted = true;
        } finally {
            if (!threadStarted) {
                synchronized (audioCaptureLock) {
                    if (activeAudioCapture == session) {
                        activeAudioCapture = null;
                    }
                }
                audioCaptureRunning = false;
                if (session != null) {
                    session.releaseRecorder();
                } else {
                    releaseAudioRecorder(audioRecorder);
                }
            }
        }
    }

    private AudioCaptureSession getActiveAudioCapture() {
        synchronized (audioCaptureLock) {
            return activeAudioCapture;
        }
    }

    private void requestAudioCaptureStop(AudioCaptureSession session) {
        if (session != null) {
            session.requestStop();
        }
    }

    private boolean stopAudioCapture(boolean wait) {
        AudioCaptureSession session = getActiveAudioCapture();
        requestAudioCaptureStop(session);
        if (session == null) {
            return true;
        }
        Thread audioThread = session.thread;
        if (wait && audioThread != null && audioThread != Thread.currentThread()) {
            try {
                audioThread.join(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (audioThread.isAlive()) {
                FileLog.e("RoundVideoEncoder audio thread did not stop within timeout");
            }
        }
        return audioThread == null || !audioThread.isAlive();
    }

    private void stopAudioRecorder(AudioRecord audioRecorder) {
        try {
            if (audioRecorder.getRecordingState() != AudioRecord.RECORDSTATE_STOPPED) {
                audioRecorder.stop();
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private void releaseAudioRecorder(AudioRecord audioRecorder) {
        stopAudioRecorder(audioRecorder);
        try {
            audioRecorder.release();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private class AudioCaptureRunnable implements Runnable {

        private final AudioCaptureSession session;
        private final AudioTimestamp audioTimestamp = new AudioTimestamp();

        public AudioCaptureRunnable(AudioCaptureSession session) {
            this.session = session;
        }

        @Override
        public void run() {
            boolean hasAudioTimestamp;
            long framesRead = 0;
            try {
                boolean done = false;
                while (!done && !session.stopRequested.get()) {
                    AudioChunkBatch batch;
                    try {
                        batch = obtainAudioBatch();
                    } catch (InterruptedException e) {
                        if (session.stopRequested.get()) {
                            break;
                        }
                        continue;
                    }
                    if (batch == null) {
                        if (!session.stopRequested.get()) {
                            FileLog.e("RoundVideoEncoder audio batch pool stalled");
                        }
                        break;
                    }
                    boolean posted = false;
                    try {
                        if (session.stopRequested.get()) {
                            done = true;
                            continue;
                        }
                        // one sample position <-> monotonic time mapping per batch is enough
                        try {
                            hasAudioTimestamp = session.audioRecorder.getTimestamp(audioTimestamp, AudioTimestamp.TIMEBASE_MONOTONIC) == AudioRecord.SUCCESS;
                        } catch (Exception e) {
                            hasAudioTimestamp = false;
                        }
                        for (int a = 0; a < AudioChunkBatch.MAX_CHUNKS; a++) {
                            ByteBuffer byteBuffer = batch.buffer[a];
                            byteBuffer.clear();
                            int readResult = session.audioRecorder.read(batch.data[a], 0, AudioChunkBatch.CHUNK_SIZE);
                            if (readResult <= 0) {
                                done = true;
                                break;
                            }
                            byteBuffer.position(0);
                            byteBuffer.limit(readResult);
                            if (a % 2 == 0) {
                                double s = 0;
                                for (int i = 0; i < readResult / 2; i++) {
                                    short p = byteBuffer.getShort();
                                    s += p * p;
                                }
                                byteBuffer.position(0);
                                double amplitude = Math.sqrt(s / readResult / 2);
                                try {
                                    callback.onAudioAmplitude(amplitude);
                                } catch (Throwable e) {
                                    FileLog.e(e);
                                }
                            }
                            long chunkFrames = readResult / 2;
                            long chunkStartNs;
                            if (hasAudioTimestamp) {
                                chunkStartNs = audioTimestamp.nanoTime + (framesRead - audioTimestamp.framePosition) * 1_000_000_000L / AUDIO_SAMPLE_RATE;
                            } else {
                                chunkStartNs = System.nanoTime() - chunkFrames * 1_000_000_000L / AUDIO_SAMPLE_RATE;
                            }
                            batch.startTimeNs[a] = chunkStartNs;
                            batch.results = a + 1;
                            framesRead += chunkFrames;
                            if (session.stopRequested.get()) {
                                done = true;
                                break;
                            }
                        }
                        if (batch.results > 0) {
                            posted = postBatch(batch);
                        }
                    } finally {
                        if (!posted) {
                            recycleAudioBatch(batch);
                        }
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                session.releaseRecorder();
                synchronized (audioCaptureLock) {
                    if (activeAudioCapture == session) {
                        activeAudioCapture = null;
                    }
                }
                if (!encoderQueue.getHandler().post(session.completionRunnable) && BuildVars.LOGS_ENABLED) {
                    FileLog.e("RoundVideoEncoder unable to post audio capture completion");
                }
            }
        }

        private boolean postBatch(AudioChunkBatch batch) {
            return encoderQueue.getHandler().post(batch.deliveryRunnable);
        }
    }

    private void setBluetoothScoOn(boolean scoOn) {
        AudioManager am = (AudioManager) ApplicationLoader.applicationContext.getSystemService(Context.AUDIO_SERVICE);
        if (SharedConfig.recordViaSco && !PermissionRequest.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            SharedConfig.recordViaSco = false;
            SharedConfig.saveConfig();
        }
        if (am.isBluetoothScoAvailableOffCall() && SharedConfig.recordViaSco || !scoOn) {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            try {
                if (btAdapter != null && btAdapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothAdapter.STATE_CONNECTED || !scoOn) {
                    if (scoOn && !am.isBluetoothScoOn()) {
                        am.startBluetoothSco();
                    } else if (!scoOn && am.isBluetoothScoOn()) {
                        am.stopBluetoothSco();
                    }
                }
            } catch (SecurityException ignored) {
            } catch (Throwable e) {
                FileLog.e(e);
                try {
                    if (!scoOn && am.isBluetoothScoOn()) {
                        am.stopBluetoothSco();
                    }
                } catch (Exception e2) {
                    FileLog.e(e2);
                }
            }
        }
    }
}
