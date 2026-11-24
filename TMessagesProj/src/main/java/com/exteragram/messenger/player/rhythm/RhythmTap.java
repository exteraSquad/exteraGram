/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player.rhythm;

import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.ForwardingAudioSink;
import androidx.media3.exoplayer.audio.TeeAudioProcessor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@OptIn(markerClass = UnstableApi.class)
public final class RhythmTap implements TeeAudioProcessor.AudioBufferSink {

    public static final class State {
        public float bass;
        public float mid;
        public float high;
        public float energy;
        public float beat;
        public float bpm;
        public float hat;
        public float intensity = 0.5f;
        public boolean downbeat;
        public int phrases;
        public boolean drop;

        public void clearEvents() {
            beat = hat = 0f;
            downbeat = drop = false;
            phrases = 0;
        }
    }

    private static final int CAPACITY = 512;
    private static final long MAX_EXTRAPOLATION_US = 120_000;
    private static final long DISPLAY_LEAD_US = 40_000;
    private static final long STALE_EVENT_US = 300_000;
    private static final int FLAG_DOWNBEAT = 1;
    private static final int FLAG_PHRASE = 2;
    private static final int FLAG_DROP = 4;

    private static volatile boolean enabled;
    private static volatile RhythmTap current;

    private final RhythmAnalyzer analyzer = new RhythmAnalyzer(this::onFrame);
    private final long[] frameTime = new long[CAPACITY];
    private final float[] frameBass = new float[CAPACITY];
    private final float[] frameMid = new float[CAPACITY];
    private final float[] frameHigh = new float[CAPACITY];
    private final float[] frameEnergy = new float[CAPACITY];
    private final float[] frameBeat = new float[CAPACITY];
    private final float[] frameBpm = new float[CAPACITY];
    private final float[] frameHat = new float[CAPACITY];
    private final float[] frameIntensity = new float[CAPACITY];
    private final byte[] frameFlags = new byte[CAPACITY];
    private int head;
    private int count;

    private int sampleRate;
    private int channelCount;
    private int encoding = C.ENCODING_INVALID;
    private boolean analyzerReady;
    private float[] mono = new float[0];
    private volatile long baseTimeUs = C.TIME_UNSET;
    private long consumedFrames;

    private long positionUs = C.TIME_UNSET;
    private long positionAtNs;
    private boolean playing;
    private float speed = 1f;

    public static void setEnabled(boolean enabled) {
        RhythmTap.enabled = enabled;
    }

    public static RhythmTap getCurrent() {
        return enabled ? current : null;
    }

    public AudioProcessor createProcessor() {
        return new TeeAudioProcessor(this);
    }

    public AudioSink wrap(AudioSink sink) {
        return new ForwardingAudioSink(sink) {
            @Override
            public boolean handleBuffer(@NonNull ByteBuffer buffer, long presentationTimeUs, int encodedAccessUnitCount) throws InitializationException, WriteException {
                if (baseTimeUs == C.TIME_UNSET) {
                    baseTimeUs = presentationTimeUs;
                    consumedFrames = 0;
                }
                return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount);
            }

            @Override
            public long getCurrentPositionUs(boolean sourceEnded) {
                final long position = super.getCurrentPositionUs(sourceEnded);
                onPosition(position);
                return position;
            }

            @Override
            public void setPlaybackParameters(@NonNull PlaybackParameters playbackParameters) {
                super.setPlaybackParameters(playbackParameters);
                synchronized (RhythmTap.this) {
                    speed = playbackParameters.speed;
                }
            }

            @Override
            public void play() {
                super.play();
                setPlaying(true);
            }

            @Override
            public void pause() {
                super.pause();
                setPlaying(false);
            }

            @Override
            public void flush() {
                super.flush();
                clear();
            }

            @Override
            public void reset() {
                super.reset();
                clear();
                if (current == RhythmTap.this) {
                    current = null;
                }
            }
        };
    }

    @Override
    public void flush(int sampleRateHz, int channelCount, int encoding) {
        this.sampleRate = sampleRateHz;
        this.channelCount = channelCount;
        this.encoding = encoding;
        analyzerReady = false;
        clear();
    }

    @Override
    public void handleBuffer(@NonNull ByteBuffer buffer) {
        final int bytesPerSample = encoding == C.ENCODING_PCM_16BIT ? 2 : encoding == C.ENCODING_PCM_FLOAT ? 4 : 0;
        if (bytesPerSample == 0 || channelCount <= 0 || sampleRate <= 0 || baseTimeUs == C.TIME_UNSET) {
            return;
        }
        final int frames = buffer.remaining() / (bytesPerSample * channelCount);
        if (!enabled) {
            analyzerReady = false;
            consumedFrames += frames;
            return;
        }
        current = this;
        if (!analyzerReady) {
            analyzer.reset(sampleRate);
            analyzerReady = true;
            clearFrames();
        }
        final ByteBuffer data = buffer.duplicate().order(ByteOrder.nativeOrder());
        final int start = data.position();
        if (mono.length < frames) {
            mono = new float[frames];
        }
        final float scale = 1f / channelCount;
        for (int i = 0; i < frames; i++) {
            float sum = 0;
            for (int c = 0; c < channelCount; c++) {
                final int index = start + (i * channelCount + c) * bytesPerSample;
                sum += bytesPerSample == 2 ? data.getShort(index) / 32768f : data.getFloat(index);
            }
            mono[i] = sum * scale;
        }
        analyzer.process(mono, frames, baseTimeUs + consumedFrames * 1_000_000L / sampleRate);
        consumedFrames += frames;
    }

    private synchronized void onFrame(RhythmAnalyzer.Frame frame) {
        if (count == CAPACITY) {
            head = (head + 1) % CAPACITY;
            count--;
        }
        final int index = (head + count) % CAPACITY;
        frameTime[index] = frame.timeUs;
        frameBass[index] = frame.bass;
        frameMid[index] = frame.mid;
        frameHigh[index] = frame.high;
        frameEnergy[index] = frame.energy;
        frameBeat[index] = frame.beat;
        frameBpm[index] = frame.bpm;
        frameHat[index] = frame.hat;
        frameIntensity[index] = frame.intensity;
        frameFlags[index] = (byte) ((frame.downbeat ? FLAG_DOWNBEAT : 0) | (frame.phrase ? FLAG_PHRASE : 0) | (frame.drop ? FLAG_DROP : 0));
        count++;
    }

    public synchronized boolean read(State out) {
        if (positionUs == C.TIME_UNSET) {
            return false;
        }
        long position = positionUs + DISPLAY_LEAD_US;
        if (playing) {
            position += Math.min(MAX_EXTRAPOLATION_US, (long) ((SystemClock.elapsedRealtimeNanos() - positionAtNs) / 1000f * speed));
        }
        boolean consumed = false;
        while (count > 0 && frameTime[head] <= position) {
            out.bass = frameBass[head];
            out.mid = frameMid[head];
            out.high = frameHigh[head];
            out.energy = frameEnergy[head];
            out.bpm = frameBpm[head] * speed;
            out.intensity = frameIntensity[head];
            if (position - frameTime[head] <= STALE_EVENT_US) {
                out.beat = Math.max(out.beat, frameBeat[head]);
                out.hat = Math.max(out.hat, frameHat[head]);
                final int flags = frameFlags[head];
                out.downbeat |= (flags & FLAG_DOWNBEAT) != 0;
                out.drop |= (flags & FLAG_DROP) != 0;
                if ((flags & FLAG_PHRASE) != 0) {
                    out.phrases++;
                }
            }
            head = (head + 1) % CAPACITY;
            count--;
            consumed = true;
        }
        return consumed;
    }

    private synchronized void onPosition(long position) {
        if (position == AudioSink.CURRENT_POSITION_NOT_SET) {
            return;
        }
        positionUs = position;
        positionAtNs = SystemClock.elapsedRealtimeNanos();
    }

    private synchronized void setPlaying(boolean playing) {
        this.playing = playing;
        positionAtNs = SystemClock.elapsedRealtimeNanos();
    }

    private synchronized void clearFrames() {
        head = count = 0;
    }

    private synchronized void clear() {
        head = count = 0;
        positionUs = C.TIME_UNSET;
        baseTimeUs = C.TIME_UNSET;
        consumedFrames = 0;
        analyzerReady = false;
    }
}
