/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player.rhythm;

import org.telegram.messenger.FourierTransform;

import java.util.Arrays;

final class RhythmAnalyzer {

    interface Listener {
        void onFrame(Frame frame);
    }

    static final class Frame {
        long timeUs;
        float bass;
        float mid;
        float high;
        float energy;
        float beat;
        float bpm;
        float hat;
        float intensity;
        boolean downbeat;
        boolean phrase;
        boolean drop;
    }

    private static final int FFT_SIZE = 2048;
    private static final int HOP = 512;

    private static final int BINS = FFT_SIZE / 2;
    private static final int BAND_COUNT = 24;
    private static final float BAND_FROM_HZ = 30f;
    private static final float BAND_TO_HZ = 12000f;
    private static final float BASS_TO_HZ = 150f;
    private static final float MID_TO_HZ = 2000f;
    private static final float HAT_FROM_HZ = 5000f;
    private static final float BASS_WEIGHT = 1f;
    private static final float MID_WEIGHT = 0.6f;
    private static final float HIGH_WEIGHT = 0.4f;
    private static final float LOG_GAIN = 1000f;
    private static final int FLUX_LAG = 2;
    private static final float SILENCE_RMS = 1e-4f;
    private static final float MIN_PEAK = 1e-4f;
    private static final float LEVEL_PEAK_DECAY = 0.999f;
    private static final float NOVELTY_PEAK_DECAY = 0.998f;

    private static final int PRE_MAX = 3;
    private static final int POST_MAX = 3;
    private static final int PRE_AVG = 10;
    private static final int POST_AVG = 4;
    private static final int LOOKAHEAD = Math.max(POST_MAX, POST_AVG);
    private static final float ONSET_DELTA = 0.07f;
    private static final float MIN_ONSET_FLUX = 0.02f;
    private static final float ONSET_GAP_S = 0.05f;
    private static final float FALLBACK_STRENGTH = 0.35f;

    private static final int HISTORY = 1024;
    private static final float TEMPO_WINDOW_S = 6f;
    private static final float TEMPO_MIN_HISTORY_S = 3f;
    private static final float TEMPO_UPDATE_S = 0.5f;
    private static final float MIN_BPM = 60f;
    private static final float MAX_BPM = 200f;
    private static final float PRIOR_BPM = 120f;
    private static final float PRIOR_OCTAVES = 1f;
    private static final float LOCK_CONFIDENCE = 0.1f;
    private static final float TEMPO_SAME = 0.05f;
    private static final float TEMPO_BLEND = 0.2f;

    private static final float BEAT_WINDOW_S = 0.05f;
    private static final float BEAT_WINDOW_FRACTION = 0.12f;
    private static final int MAX_MISSES = 4;
    private static final float SOFT_BEAT = 0.3f;
    private static final float SOFT_BEAT_MIN_NOVELTY = 0.12f;
    private static final float SOFT_BEAT_MIN_ENERGY = 0.35f;
    private static final float[] RELATED_TEMPO_RATIOS = {2f, 0.5f, 1.5f, 2f / 3f};
    private static final int TEMPO_CONFIRMATIONS = 2;
    private static final int RELATED_TEMPO_CONFIRMATIONS = 4;

    private static final float PHASE_WINDOW_S = 4f;
    private static final float LOW_PHASE_WEIGHT = 1.5f;

    private static final int HAT_PRE_MAX = 2;
    private static final int HAT_POST_MAX = 2;
    private static final int HAT_PRE_AVG = 8;
    private static final float HAT_DELTA = 0.12f;
    private static final float HAT_GAP_S = 0.06f;

    private static final int BAR_BEATS = 4;
    private static final float BAR_SCORE_DECAY = 0.85f;
    private static final float LOW_BAR_WEIGHT = 0.5f;
    private static final float HARMONIC_PEAK_DECAY = 0.99f;
    private static final int MIN_BAR_BEATS = 8;
    private static final int MAX_GRID_GAP_BEATS = 16;
    private static final float BAR_CONFIDENCE = 1.12f;
    private static final int MAX_CHROMA_FRAMES = 256;
    private static final int PHRASE_BARS = 8;
    private static final int MIN_PHRASE_BARS = 4;
    private static final float PHRASE_INTENSITY_CHANGE = 0.3f;

    private static final float SHORT_ENERGY_S = 2f;
    private static final float FAST_ENERGY_S = 0.25f;
    private static final float SLOW_ENERGY_S = 4f;
    private static final float REFERENCE_DECAY = 0.99995f;
    private static final float DENSITY_WINDOW_S = 2f;
    private static final float MAX_EVENTS_PER_S = 8f;
    private static final float HAT_EVENT_WEIGHT = 0.5f;
    private static final float LOUDNESS_FLOOR_DB = -30f;
    private static final float LOUDNESS_RANGE_DB = 22f;
    private static final float BRIGHTNESS_SCALE = 3f;
    private static final float LOUDNESS_WEIGHT = 0.45f;
    private static final float DENSITY_WEIGHT = 0.35f;
    private static final float BRIGHTNESS_WEIGHT = 0.2f;
    private static final float RANGE_DRIFT = 0.00012f;
    private static final float MIN_RANGE = 0.12f;
    private static final float ABSOLUTE_WEIGHT = 0.45f;
    private static final float WARMUP_S = 3f;
    private static final float NEUTRAL_INTENSITY = 0.5f;
    private static final float DROP_RATIO = 1.8f;
    private static final float DROP_QUIET = 0.45f;
    private static final float DROP_LOOKBACK_S = 2f;
    private static final float DROP_REFERENCE = 0.5f;
    private static final float DROP_GAP_S = 6f;

    private static final int ONSETS = 16;
    private static final int EVENTS = 128;
    private static final int PENDING = 64;

    private final Listener listener;
    private final float[] window = new float[FFT_SIZE];
    private final float[] samples = new float[FFT_SIZE];
    private final float[] frameSamples = new float[FFT_SIZE];
    private final FourierTransform.FFT fft = new FourierTransform.FFT(FFT_SIZE, 48000);

    private final int[] bandFrom = new int[BAND_COUNT];
    private final int[] bandTo = new int[BAND_COUNT];
    private final float[] bandWeight = new float[BAND_COUNT];
    private final int[] bandKind = new int[BAND_COUNT];
    private final boolean[] bandHat = new boolean[BAND_COUNT];
    private final float[] bandHistory = new float[HISTORY * BAND_COUNT];
    private final float[] bandLevel = new float[BAND_COUNT];
    private final float[][] pastLevels = new float[FLUX_LAG][BAND_COUNT];

    private final float[] novelty = new float[HISTORY];
    private final float[] rawNovelty = new float[HISTORY];
    private final float[] lowNovelty = new float[HISTORY];
    private final float[] hatNovelty = new float[HISTORY];
    private final float[] hatRaw = new float[HISTORY];
    private final float[] intensityHistory = new float[HISTORY];
    private final float[] autocorrelation = new float[HISTORY];
    private final double[] tempoScores = new double[HISTORY];
    private final float[] centered = new float[HISTORY];
    private final float[] levelPeaks = new float[4];

    private final long[] onsetFrame = new long[ONSETS];
    private final float[] onsetStrength = new float[ONSETS];
    private int onsetCount;
    private int onsetHead;
    private long lastOnsetFrame;

    private final long[] eventFrame = new long[EVENTS];
    private final float[] eventWeight = new float[EVENTS];
    private int eventHead;
    private int eventCount;

    private final long[] pendingTime = new long[PENDING];
    private final float[][] pendingLevels = new float[4][PENDING];
    private final float[] pendingBeat = new float[PENDING];
    private final float[] pendingHat = new float[PENDING];
    private final float[] pendingIntensity = new float[PENDING];
    private final boolean[] pendingDownbeat = new boolean[PENDING];
    private final boolean[] pendingPhrase = new boolean[PENDING];
    private final boolean[] pendingDrop = new boolean[PENDING];
    private final Frame output = new Frame();

    private final float[] barScores = new float[BAR_BEATS];
    private final long[] gridFrames = new long[2];
    private final float[] previousChroma = new float[BAND_COUNT];
    private final float[] currentChroma = new float[BAND_COUNT];
    private final float[] column = new float[MAX_CHROMA_FRAMES];
    private int gridFrameCount;
    private boolean hasPreviousChroma;
    private long grid;
    private float gridBpm;
    private float harmonicPeak;
    private int downbeatCount;
    private int lastPhraseDownbeat;
    private float lastPhraseIntensity;

    private float shortEnergy;
    private float fastEnergy;
    private float slowEnergy;
    private float referenceEnergy;
    private float brightness;
    private float rangeLow;
    private float rangeHigh;
    private long lastDropFrame;
    private float hatPeak;
    private long lastHatFrame;

    private int sampleRate;
    private float fps;
    private int position;
    private int filled;
    private int sinceHop;
    private long frame;
    private float noveltyPeak;
    private float lowNoveltyPeak;
    private int beatWindowMax;
    private int delay;

    private float bpm;
    private float confidence;
    private float candidateBpm;
    private int candidateCount;
    private long lastTempoFrame;
    private boolean locked;
    private double nextBeat;
    private int misses;
    private int phaseDisagreements;

    RhythmAnalyzer(Listener listener) {
        this.listener = listener;
        for (int i = 0; i < FFT_SIZE; i++) {
            window[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (FFT_SIZE - 1)));
        }
        reset(0);
    }

    void reset(int sampleRate) {
        this.sampleRate = sampleRate;
        fps = sampleRate > 0 ? (float) sampleRate / HOP : 0f;
        position = filled = sinceHop = 0;
        frame = 0;
        noveltyPeak = lowNoveltyPeak = hatPeak = harmonicPeak = MIN_PEAK;
        Arrays.fill(levelPeaks, MIN_PEAK);
        for (float[] levels : pastLevels) {
            Arrays.fill(levels, 0f);
        }
        Arrays.fill(novelty, 0f);
        Arrays.fill(rawNovelty, 0f);
        Arrays.fill(lowNovelty, 0f);
        Arrays.fill(hatNovelty, 0f);
        Arrays.fill(hatRaw, 0f);
        Arrays.fill(intensityHistory, 0f);
        Arrays.fill(bandHistory, 0f);
        Arrays.fill(barScores, 0f);
        onsetCount = onsetHead = eventCount = eventHead = 0;
        lastOnsetFrame = lastHatFrame = lastDropFrame = Long.MIN_VALUE / 2;
        gridFrameCount = 0;
        hasPreviousChroma = false;
        grid = 0;
        gridBpm = 0f;
        downbeatCount = 0;
        lastPhraseDownbeat = -PHRASE_BARS * 2;
        lastPhraseIntensity = 0f;
        shortEnergy = fastEnergy = slowEnergy = referenceEnergy = brightness = 0f;
        rangeLow = rangeHigh = -1f;
        bpm = confidence = candidateBpm = 0f;
        candidateCount = 0;
        lastTempoFrame = 0;
        locked = false;
        misses = phaseDisagreements = 0;
        if (sampleRate <= 0) {
            return;
        }
        beatWindowMax = Math.max(2, Math.round(BEAT_WINDOW_S * fps));
        delay = Math.min(PENDING - 1, LOOKAHEAD + beatWindowMax * 2 + 2);
        final float binHz = (float) sampleRate / FFT_SIZE;
        final double ratio = Math.pow(BAND_TO_HZ / BAND_FROM_HZ, 1.0 / BAND_COUNT);
        int previousTo = 1;
        for (int b = 0; b < BAND_COUNT; b++) {
            final double lowHz = BAND_FROM_HZ * Math.pow(ratio, b);
            final double highHz = lowHz * ratio;
            final int from = Math.max(previousTo, (int) Math.floor(lowHz / binHz));
            final int to = Math.min(BINS - 1, Math.max(from + 1, (int) Math.ceil(highHz / binHz)));
            bandFrom[b] = from;
            bandTo[b] = to;
            previousTo = to;
            final double center = Math.sqrt(lowHz * highHz);
            bandKind[b] = center < BASS_TO_HZ ? 0 : center < MID_TO_HZ ? 1 : 2;
            bandWeight[b] = bandKind[b] == 0 ? BASS_WEIGHT : bandKind[b] == 1 ? MID_WEIGHT : HIGH_WEIGHT;
            bandHat[b] = center > HAT_FROM_HZ;
        }
    }

    float getBpm() {
        return locked ? bpm : 0f;
    }

    void process(float[] mono, int count, long startUs) {
        if (sampleRate <= 0) {
            return;
        }
        for (int i = 0; i < count; i++) {
            samples[position] = mono[i];
            position = (position + 1) & (FFT_SIZE - 1);
            if (filled < FFT_SIZE) {
                filled++;
            }
            if (++sinceHop >= HOP && filled == FFT_SIZE) {
                sinceHop = 0;
                final long endUs = startUs + (i + 1) * 1_000_000L / sampleRate;
                analyze(endUs - FFT_SIZE / 2 * 1_000_000L / sampleRate);
            }
        }
    }

    private void analyze(long timeUs) {
        double sumSquares = 0;
        for (int i = 0; i < FFT_SIZE; i++) {
            final float s = samples[(position + i) & (FFT_SIZE - 1)];
            if (i >= FFT_SIZE - HOP) {
                sumSquares += s * s;
            }
            frameSamples[i] = s * window[i];
        }
        fft.forward(frameSamples);
        final float rms = (float) Math.sqrt(sumSquares / HOP);
        final boolean silent = rms < SILENCE_RMS;
        final float norm = 4f / FFT_SIZE;
        final float[] lagged = pastLevels[(int) (frame % FLUX_LAG)];
        float flux = 0f, weights = 0f, lowFlux = 0f, hatFlux = 0f;
        int lowBands = 0, hatBands = 0;
        final int historyIndex = (int) (frame & (HISTORY - 1));
        float bass = 0f, mid = 0f, high = 0f;
        int bassCount = 0, midCount = 0, highCount = 0;
        for (int b = 0; b < BAND_COUNT; b++) {
            float sum = 0f;
            for (int i = bandFrom[b]; i < bandTo[b]; i++) {
                sum += fft.getBand(i);
            }
            final float level = silent ? 0f : (float) Math.log1p(LOG_GAIN * sum * norm / (bandTo[b] - bandFrom[b]));
            bandLevel[b] = level;
            bandHistory[historyIndex * BAND_COUNT + b] = level;
            float reference = lagged[b];
            if (b > 0) {
                reference = Math.max(reference, lagged[b - 1]);
            }
            if (b < BAND_COUNT - 1) {
                reference = Math.max(reference, lagged[b + 1]);
            }
            final float rise = Math.max(0f, level - reference);
            flux += bandWeight[b] * rise;
            weights += bandWeight[b];
            if (bandKind[b] == 0) {
                lowFlux += rise;
                lowBands++;
            }
            if (bandHat[b]) {
                hatFlux += rise;
                hatBands++;
            }
            if (bandKind[b] == 0) {
                bass += level;
                bassCount++;
            } else if (bandKind[b] == 1) {
                mid += level;
                midCount++;
            } else {
                high += level;
                highCount++;
            }
        }
        System.arraycopy(bandLevel, 0, lagged, 0, BAND_COUNT);
        flux /= weights;

        final int index = (int) (frame & (HISTORY - 1));
        rawNovelty[index] = flux;
        noveltyPeak = Math.max(flux, noveltyPeak * NOVELTY_PEAK_DECAY);
        novelty[index] = silent ? 0f : flux / Math.max(noveltyPeak, MIN_PEAK);
        lowFlux /= Math.max(1, lowBands);
        lowNoveltyPeak = Math.max(lowFlux, lowNoveltyPeak * NOVELTY_PEAK_DECAY);
        lowNovelty[index] = silent ? 0f : lowFlux / Math.max(lowNoveltyPeak, MIN_PEAK);
        hatFlux /= Math.max(1, hatBands);
        hatRaw[index] = hatFlux;
        hatPeak = Math.max(hatFlux, hatPeak * NOVELTY_PEAK_DECAY);
        hatNovelty[index] = silent ? 0f : hatFlux / Math.max(hatPeak, MIN_PEAK);

        final int slot = (int) (frame % PENDING);
        pendingTime[slot] = timeUs;
        pendingBeat[slot] = 0f;
        pendingHat[slot] = 0f;
        pendingDownbeat[slot] = pendingPhrase[slot] = pendingDrop[slot] = false;
        pendingLevels[0][slot] = silent ? 0f : square(normalize(0, bass / Math.max(1, bassCount)));
        pendingLevels[1][slot] = silent ? 0f : square(normalize(1, mid / Math.max(1, midCount)));
        pendingLevels[2][slot] = silent ? 0f : square(normalize(2, high / Math.max(1, highCount)));
        pendingLevels[3][slot] = silent ? 0f : normalize(3, rms);
        updateSections(rms, silent ? 0f : high / Math.max(1, highCount), slot);

        final long decided = frame - LOOKAHEAD;
        if (decided >= PRE_AVG) {
            pickOnset(decided);
            pickHat(decided);
            if (frame - lastTempoFrame >= Math.round(TEMPO_UPDATE_S * fps) && frame >= Math.round(TEMPO_MIN_HISTORY_S * fps)) {
                lastTempoFrame = frame;
                updateTempo();
                checkPhase(decided);
            }
            track(decided);
        }

        final long out = frame - delay;
        if (out >= 0) {
            final int o = (int) (out % PENDING);
            output.timeUs = pendingTime[o];
            output.bass = pendingLevels[0][o];
            output.mid = pendingLevels[1][o];
            output.high = pendingLevels[2][o];
            output.energy = pendingLevels[3][o];
            output.beat = pendingBeat[o];
            output.bpm = getBpm();
            output.hat = pendingHat[o];
            output.intensity = pendingIntensity[o];
            output.downbeat = pendingDownbeat[o];
            output.phrase = pendingPhrase[o];
            output.drop = pendingDrop[o];
            listener.onFrame(output);
        }
        frame++;
    }

    private void pickOnset(long c) {
        final float value = novelty[(int) (c & (HISTORY - 1))];
        if (rawNovelty[(int) (c & (HISTORY - 1))] < MIN_ONSET_FLUX || c - lastOnsetFrame < Math.round(ONSET_GAP_S * fps)) {
            return;
        }
        for (long i = c - PRE_MAX; i <= c + POST_MAX; i++) {
            final float other = novelty[(int) (i & (HISTORY - 1))];
            if (i < c && other >= value || i > c && other > value) {
                return;
            }
        }
        float average = 0f;
        for (long i = c - PRE_AVG; i <= c + POST_AVG; i++) {
            average += novelty[(int) (i & (HISTORY - 1))];
        }
        average /= PRE_AVG + POST_AVG + 1;
        if (value < average + ONSET_DELTA) {
            return;
        }
        lastOnsetFrame = c;
        final int slot = (onsetHead + onsetCount) % ONSETS;
        if (onsetCount == ONSETS) {
            onsetHead = (onsetHead + 1) % ONSETS;
        } else {
            onsetCount++;
        }
        onsetFrame[slot] = c;
        onsetStrength[slot] = Math.min(1f, value);
        addEvent(c, 1f);
    }

    private void pickHat(long c) {
        final int at = (int) (c & (HISTORY - 1));
        final float value = hatNovelty[at];
        if (hatRaw[at] < MIN_ONSET_FLUX || c - lastHatFrame < Math.round(HAT_GAP_S * fps)) {
            return;
        }
        for (long i = c - HAT_PRE_MAX; i <= c + HAT_POST_MAX; i++) {
            final float other = hatNovelty[(int) (i & (HISTORY - 1))];
            if (i < c && other >= value || i > c && other > value) {
                return;
            }
        }
        float average = 0f;
        for (long i = c - HAT_PRE_AVG; i <= c + HAT_POST_MAX; i++) {
            average += hatNovelty[(int) (i & (HISTORY - 1))];
        }
        average /= HAT_PRE_AVG + HAT_POST_MAX + 1;
        if (value < average + HAT_DELTA) {
            return;
        }
        lastHatFrame = c;
        if (c > frame - delay) {
            final int slot = (int) (c % PENDING);
            pendingHat[slot] = Math.max(pendingHat[slot], Math.min(1f, value));
        }
        addEvent(c, HAT_EVENT_WEIGHT);
    }

    private void addEvent(long at, float weight) {
        final int slot = (eventHead + eventCount) % EVENTS;
        if (eventCount == EVENTS) {
            eventHead = (eventHead + 1) % EVENTS;
        } else {
            eventCount++;
        }
        eventFrame[slot] = at;
        eventWeight[slot] = weight;
    }

    private void updateSections(float rms, float highLevel, int slot) {
        if (frame == 0) {
            shortEnergy = fastEnergy = slowEnergy = rms;
        }
        shortEnergy += (rms - shortEnergy) * smoothing(SHORT_ENERGY_S);
        fastEnergy += (rms - fastEnergy) * smoothing(FAST_ENERGY_S);
        slowEnergy += (rms - slowEnergy) * smoothing(SLOW_ENERGY_S);
        brightness += (highLevel - brightness) * smoothing(SHORT_ENERGY_S);
        referenceEnergy = Math.max(shortEnergy, referenceEnergy * REFERENCE_DECAY);

        final long windowStart = frame - Math.round(DENSITY_WINDOW_S * fps);
        while (eventCount > 0 && eventFrame[eventHead] < windowStart) {
            eventHead = (eventHead + 1) % EVENTS;
            eventCount--;
        }
        float events = 0f;
        for (int i = 0; i < eventCount; i++) {
            events += eventWeight[(eventHead + i) % EVENTS];
        }
        final float density = clamp01(events / DENSITY_WINDOW_S / MAX_EVENTS_PER_S);
        final float loudness = clamp01((decibels(shortEnergy) - LOUDNESS_FLOOR_DB) / LOUDNESS_RANGE_DB);
        final float feature = LOUDNESS_WEIGHT * loudness + DENSITY_WEIGHT * density + BRIGHTNESS_WEIGHT * clamp01(brightness / BRIGHTNESS_SCALE);
        if (rangeLow < 0f) {
            rangeLow = rangeHigh = feature;
        }
        rangeHigh = feature > rangeHigh ? feature : rangeHigh - RANGE_DRIFT;
        rangeLow = feature < rangeLow ? feature : rangeLow + RANGE_DRIFT;
        final float relative = rangeHigh - rangeLow > MIN_RANGE ? (feature - rangeLow) / (rangeHigh - rangeLow) : NEUTRAL_INTENSITY;
        final float intensity = frame < Math.round(WARMUP_S * fps) ? NEUTRAL_INTENSITY : clamp01(ABSOLUTE_WEIGHT * feature + (1f - ABSOLUTE_WEIGHT) * relative);
        intensityHistory[(int) (frame & (HISTORY - 1))] = intensity;
        pendingIntensity[slot] = intensity;

        final long lookback = frame - Math.round(DROP_LOOKBACK_S * fps);
        if (frame - lastDropFrame > Math.round(DROP_GAP_S * fps) && frame > Math.round(WARMUP_S * fps) && fastEnergy > DROP_RATIO * slowEnergy
                && intensityHistory[(int) (lookback & (HISTORY - 1))] < DROP_QUIET && fastEnergy > DROP_REFERENCE * referenceEnergy) {
            lastDropFrame = frame;
            pendingDrop[slot] = true;
        }
    }

    private float smoothing(float seconds) {
        return 1f - (float) Math.exp(-1.0 / (fps * seconds));
    }

    private static float decibels(float value) {
        return 20f * (float) Math.log10(Math.max(value, 1e-6f));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private void gridStep(long beatFrame, float strength) {
        final int beatSlot = (int) (grid % BAR_BEATS);
        float lowEvidence = 0f;
        for (long i = beatFrame - 1; i <= beatFrame + 1; i++) {
            lowEvidence = Math.max(lowEvidence, lowNovelty[(int) (i & (HISTORY - 1))]);
        }
        barScores[beatSlot] = barScores[beatSlot] * BAR_SCORE_DECAY + (1f - BAR_SCORE_DECAY) * LOW_BAR_WEIGHT * lowEvidence;
        gridBpm = bpm;
        if (gridFrameCount == 2) {
            gridFrames[0] = gridFrames[1];
            gridFrames[1] = beatFrame;
        } else {
            gridFrames[gridFrameCount++] = beatFrame;
        }
        if (gridFrameCount == 2 && gridFrames[1] - gridFrames[0] >= 4) {
            intervalChroma(gridFrames[0], gridFrames[1]);
            if (hasPreviousChroma) {
                float change = 0f;
                int bands = 0;
                for (int b = 0; b < BAND_COUNT; b++) {
                    if (bandKind[b] != 2) {
                        change += Math.abs(currentChroma[b] - previousChroma[b]);
                        bands++;
                    }
                }
                change /= Math.max(1, bands);
                harmonicPeak = Math.max(change, harmonicPeak * HARMONIC_PEAK_DECAY);
                final int previousSlot = (int) ((grid + BAR_BEATS - 1) % BAR_BEATS);
                barScores[previousSlot] = barScores[previousSlot] * BAR_SCORE_DECAY + (1f - BAR_SCORE_DECAY) * change / Math.max(harmonicPeak, MIN_PEAK);
            }
            System.arraycopy(currentChroma, 0, previousChroma, 0, BAND_COUNT);
            hasPreviousChroma = true;
        }
        grid++;
        int best = 0;
        for (int i = 1; i < BAR_BEATS; i++) {
            if (barScores[i] > barScores[best]) {
                best = i;
            }
        }
        float second = 0f;
        for (int i = 0; i < BAR_BEATS; i++) {
            if (i != best) {
                second = Math.max(second, barScores[i]);
            }
        }
        if (beatFrame <= frame - delay || beatFrame > frame) {
            return;
        }
        final int slot = (int) (beatFrame % PENDING);
        if (grid >= MIN_BAR_BEATS && barScores[best] > second * BAR_CONFIDENCE && beatSlot == best) {
            pendingDownbeat[slot] = strength > 0f;
            downbeatCount++;
            final float intensity = intensityHistory[(int) (beatFrame & (HISTORY - 1))];
            final int bars = downbeatCount - lastPhraseDownbeat;
            if (strength > 0f && bars >= MIN_PHRASE_BARS && (bars >= PHRASE_BARS || Math.abs(intensity - lastPhraseIntensity) > PHRASE_INTENSITY_CHANGE)) {
                pendingPhrase[slot] = true;
                lastPhraseDownbeat = downbeatCount;
                lastPhraseIntensity = intensity;
            }
        }
        if (strength > 0f) {
            emitBeat(beatFrame, strength);
        }
    }

    private void intervalChroma(long from, long to) {
        final int frames = (int) Math.min(MAX_CHROMA_FRAMES, to - from);
        for (int b = 0; b < BAND_COUNT; b++) {
            if (bandKind[b] == 2) {
                currentChroma[b] = 0f;
                continue;
            }
            for (int i = 0; i < frames; i++) {
                column[i] = bandHistory[(int) ((from + i) & (HISTORY - 1)) * BAND_COUNT + b];
            }
            Arrays.sort(column, 0, frames);
            currentChroma[b] = column[frames / 2];
        }
    }

    private void track(long c) {
        if (!locked) {
            final int onset = findOnset(c, c);
            if (onset < 0) {
                return;
            }
            final float strength = onsetStrength[onset];
            if (bpm > 0f && confidence >= LOCK_CONFIDENCE) {
                locked = true;
                misses = phaseDisagreements = 0;
                final double period = period();
                final double start = bestGrid(c);
                final boolean onBeat = Math.abs(start - c) <= beatWindow(period);
                realignGrid(onBeat ? c : start + period);
                nextBeat = start + period;
                if (onBeat) {
                    gridStep(c, 0.4f + 0.6f * strength);
                }
            } else if (strength >= FALLBACK_STRENGTH) {
                emitBeat(c, 0.8f * strength);
            }
            return;
        }
        final double period = period();
        final int beatWindow = beatWindow(period);
        while (locked && c >= nextBeat + beatWindow) {
            final long predicted = Math.round(nextBeat);
            final int onset = findOnset(predicted - beatWindow, predicted + beatWindow);
            if (onset >= 0) {
                gridStep(onsetFrame[onset], 0.4f + 0.6f * onsetStrength[onset]);
                nextBeat = onsetFrame[onset] + period;
                misses = 0;
            } else {
                final boolean audible = maxNovelty(predicted - beatWindow, predicted + beatWindow) >= SOFT_BEAT_MIN_NOVELTY || energyAt(predicted) >= SOFT_BEAT_MIN_ENERGY;
                gridStep(predicted, audible ? SOFT_BEAT : 0f);
                nextBeat += period;
                if (++misses > MAX_MISSES) {
                    locked = false;
                }
            }
        }
    }

    private int beatWindow(double period) {
        return Math.max(2, Math.min(beatWindowMax, (int) Math.round(period * BEAT_WINDOW_FRACTION)));
    }

    private double bestGrid(long c) {
        final double period = period();
        final int beats = Math.max(2, (int) (PHASE_WINDOW_S * fps / period));
        double bestScore = -1;
        double best = c;
        for (int phase = 0; phase < (int) Math.ceil(period); phase++) {
            double score = 0;
            for (int k = 0; k < beats; k++) {
                final long t = Math.round(c - phase - k * period);
                if (t < 1 || t > frame) {
                    continue;
                }
                float value = 0f;
                for (long i = t - 1; i <= Math.min(t + 1, frame); i++) {
                    final int at = (int) (i & (HISTORY - 1));
                    value = Math.max(value, novelty[at] + LOW_PHASE_WEIGHT * lowNovelty[at]);
                }
                score += value;
            }
            if (score > bestScore) {
                bestScore = score;
                best = c - phase;
            }
        }
        return best;
    }

    private void checkPhase(long c) {
        if (!locked) {
            return;
        }
        final double period = period();
        final double aligned = bestGrid(c);
        double offset = (nextBeat - aligned) % period;
        if (offset < 0) {
            offset += period;
        }
        offset = Math.min(offset, period - offset);
        if (offset <= beatWindow(period)) {
            phaseDisagreements = 0;
        } else if (++phaseDisagreements >= 2) {
            phaseDisagreements = 0;
            nextBeat = aligned + period;
            realignGrid(nextBeat);
        }
    }

    private void realignGrid(double nextGridBeat) {
        if (gridFrameCount == 0) {
            return;
        }
        final long skipped = Math.round((nextGridBeat - gridFrames[gridFrameCount - 1]) / period()) - 1;
        if (skipped >= -1 && skipped <= MAX_GRID_GAP_BEATS && gridBpm > 0f && Math.abs(bpm - gridBpm) / gridBpm < TEMPO_SAME) {
            grid = Math.max(0, grid + skipped);
        } else {
            grid = 0;
            Arrays.fill(barScores, 0f);
        }
        gridFrameCount = 0;
        hasPreviousChroma = false;
    }

    private int findOnset(long from, long to) {
        int best = -1;
        for (int i = 0; i < onsetCount; i++) {
            final int slot = (onsetHead + i) % ONSETS;
            if (onsetFrame[slot] >= from && onsetFrame[slot] <= to && (best < 0 || onsetStrength[slot] > onsetStrength[best])) {
                best = slot;
            }
        }
        return best;
    }

    private float energyAt(long at) {
        return at <= frame - delay || at > frame ? 0f : pendingLevels[3][(int) (at % PENDING)];
    }

    private float maxNovelty(long from, long to) {
        float max = 0f;
        for (long i = Math.max(0, from); i <= to && i <= frame; i++) {
            max = Math.max(max, novelty[(int) (i & (HISTORY - 1))]);
        }
        return max;
    }

    private void emitBeat(long beatFrame, float strength) {
        if (beatFrame <= frame - delay || beatFrame > frame) {
            return;
        }
        final int slot = (int) (beatFrame % PENDING);
        pendingBeat[slot] = Math.max(pendingBeat[slot], Math.min(1f, strength));
    }

    private double period() {
        return 60.0 * fps / bpm;
    }

    private void updateTempo() {
        final int n = (int) Math.min(Math.min(frame, HISTORY), Math.round(TEMPO_WINDOW_S * fps));
        final int minLag = (int) Math.floor(60f * fps / MAX_BPM);
        final int maxLag = (int) Math.ceil(60f * fps / MIN_BPM);
        final int maxAutocorrelationLag = (maxLag + 1) * 2;
        if (n <= maxAutocorrelationLag + 1) {
            return;
        }
        double mean = 0;
        for (int i = 0; i < n; i++) {
            mean += novelty[(int) ((frame - n + i) & (HISTORY - 1))];
        }
        mean /= n;
        for (int i = 0; i < n; i++) {
            centered[i] = (float) (novelty[(int) ((frame - n + i) & (HISTORY - 1))] - mean);
        }
        for (int lag = 0; lag <= maxAutocorrelationLag; lag++) {
            double sum = 0;
            for (int i = lag; i < n; i++) {
                sum += centered[i] * centered[i - lag];
            }
            autocorrelation[lag] = (float) (sum / (n - lag));
        }
        final float energy = autocorrelation[0];
        if (energy <= 0f) {
            return;
        }
        int bestLag = -1;
        double bestScore = 0;
        final double[] scores = tempoScores;
        for (int lag = Math.max(1, minLag - 1); lag <= maxLag + 1; lag++) {
            final double lagBpm = 60.0 * fps / lag;
            final double octaves = Math.log(lagBpm / PRIOR_BPM) / Math.log(2) / PRIOR_OCTAVES;
            final double prior = Math.exp(-0.5 * octaves * octaves);
            final double score = prior * (Math.max(0f, autocorrelation[lag]) + 0.5 * Math.max(0f, autocorrelation[lag * 2]));
            scores[lag] = score;
            if (lag >= minLag && lag <= maxLag && score > bestScore) {
                bestScore = score;
                bestLag = lag;
            }
        }
        if (bestLag < 0) {
            return;
        }
        double lag = bestLag;
        final double left = scores[bestLag - 1];
        final double right = scores[bestLag + 1];
        final double curvature = left - 2 * bestScore + right;
        if (curvature < 0) {
            lag += Math.max(-0.5, Math.min(0.5, 0.5 * (left - right) / curvature));
        }
        final float estimate = (float) (60.0 * fps / lag);
        confidence = Math.max(0f, autocorrelation[bestLag]) / energy;
        if (bpm <= 0f || Math.abs(estimate - bpm) / bpm < TEMPO_SAME) {
            bpm = bpm <= 0f ? estimate : bpm + (estimate - bpm) * TEMPO_BLEND;
            candidateCount = 0;
        } else if (candidateBpm > 0f && Math.abs(estimate - candidateBpm) / candidateBpm < TEMPO_SAME) {
            if (++candidateCount >= (isRelatedTempo(estimate) ? RELATED_TEMPO_CONFIRMATIONS : TEMPO_CONFIRMATIONS)) {
                bpm = estimate;
                candidateCount = 0;
                locked = false;
            }
        } else {
            candidateBpm = estimate;
            candidateCount = 1;
        }
    }

    private boolean isRelatedTempo(float estimate) {
        for (float ratio : RELATED_TEMPO_RATIOS) {
            if (Math.abs(estimate - bpm * ratio) / (bpm * ratio) < TEMPO_SAME) {
                return true;
            }
        }
        return false;
    }

    private float normalize(int band, float value) {
        levelPeaks[band] = Math.max(value, levelPeaks[band] * LEVEL_PEAK_DECAY);
        return value / Math.max(levelPeaks[band], MIN_PEAK);
    }

    private static float square(float value) {
        return value * value;
    }
}
