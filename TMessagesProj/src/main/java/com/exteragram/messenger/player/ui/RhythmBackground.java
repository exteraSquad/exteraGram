/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player.ui;

import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;

import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.player.rhythm.RhythmTap;

import org.telegram.messenger.Utilities;

import java.util.Arrays;
import java.util.Random;

public class RhythmBackground {

    public static final int BLOB_COUNT = 4;

    private static final float SHADER_RADIUS = 100f;
    private static final float ORBIT_CENTER_Y = 0.42f;
    private static final float[] BLOB_ORBIT_X = {0.30f, 0.34f, 0.26f, 0.38f};
    private static final float[] BLOB_ORBIT_Y = {0.26f, 0.30f, 0.22f, 0.34f};
    private static final float[] BLOB_ANGLE = {0f, (float) (Math.PI / 2), (float) Math.PI, (float) (Math.PI * 1.5)};
    private static final float[] BLOB_SPIN = {1f, 0.75f, -1.25f, 0.6f};
    private static final float[] BLOB_RADIUS = {1.02f, 0.94f, 0.96f, 0.86f};
    private static final float[] BLOB_ALPHA_DARK = {0.31f, 0.26f, 0.29f, 0.23f};
    private static final float[] BLOB_ALPHA_LIGHT = {0.22f, 0.19f, 0.20f, 0.16f};
    private static final float[] GLOW_STOPS = {0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f};
    private static final float[] GLOW_FALLOFF = {1f, 0.88f, 0.6f, 0.32f, 0.12f, 0f};
    private static final int BAND_BASS = 0;
    private static final int BAND_MID = 1;
    private static final int BAND_HIGH = 2;
    private static final int[] BLOB_BAND = {BAND_BASS, BAND_MID, BAND_BASS, BAND_HIGH};

    private static final float PULSE_RADIUS = 0.05f;
    private static final float BAND_RADIUS = 0.10f;
    private static final float PULSE_ALPHA = 0.08f;
    private static final float ENERGY_ALPHA = 0.12f;
    private static final float GLOW_RADIUS = 0.7f;
    private static final float GLOW_ALPHA = 0.14f;
    private static final float SCRIM_FROM = 0.52f;
    private static final float SCRIM_ALPHA = 0.72f;

    private static final float BEAT_PULSE = 0.28f;
    private static final float BEAT_SURGE = 0.6f;
    private static final float DOWNBEAT_PULSE = 0.75f;
    private static final float DOWNBEAT_SURGE = 1.6f;
    private static final float DOWNBEAT_FULL_STRENGTH = 0.7f;
    private static final float DROP_SURGE = 3f;
    private static final float MIN_HAT = 0.35f;

    private static final float BEATS_PER_TURN = 16f;
    private static final float IDLE_TURNS_PER_S = 0.02f;
    private static final float ENERGY_TURNS_PER_S = 0.06f;
    private static final float SURGE_GAIN = 1.6f;
    private static final float SURGE_DECAY_S = 0.3f;
    private static final float PULSE_DECAY_S = 0.22f;
    private static final float BLOOM_DECAY_S = 0.9f;
    private static final float SHIMMER_DECAY_S = 0.12f;
    private static final float PRESENCE_TAU_S = 0.25f;
    private static final float SPIN_TAU_S = 0.6f;
    private static final float INTENSITY_TAU_S = 1.2f;
    private static final float COLOR_SHIFT_TAU_S = 0.7f;
    private static final float SETTLED = 0.003f;

    private static final float INTENSITY_ALPHA_BASE = 0.7f;
    private static final float INTENSITY_ALPHA_GAIN = 0.45f;
    private static final float INTENSITY_SPIN_BASE = 0.6f;
    private static final float INTENSITY_SPIN_GAIN = 0.8f;
    private static final float NEUTRAL_INTENSITY = 0.5f;

    private static final float BLOOM_ALPHA = 0.5f;
    private static final float BLOOM_START = 0.6f;
    private static final float BLOOM_GROWTH = 0.9f;
    private static final float BLOOM_WHITE = 0.45f;

    private static final int PARTICLE_COUNT = 80;
    private static final float PARTICLE_SMALL_DP = 0.9f;
    private static final float PARTICLE_LARGE_DP = 1.5f;
    private static final float PARTICLE_LARGE_SHARE = 0.2f;
    private static final float PARTICLE_TINT = 0.3f;
    private static final float PARTICLE_TINT_LIGHT = 0.35f;
    private static final float PARTICLE_TIME_SCALE = 0.65f;
    private static final float PARTICLE_LONGEVITY = 1.4f;
    private static final float PARTICLE_NOISE_CELLS = 6f;
    private static final float PARTICLE_NOISE_DRIFT = 0.04f;
    private static final float PARTICLE_ACCEL = 0.07f;
    private static final float PARTICLE_PUSH = 0.05f;
    private static final float PARTICLE_MAX_SPEED = 0.13f;
    private static final float PARTICLE_MIN_LAUNCH = 0.06f;
    private static final float PARTICLE_LAUNCH_RANGE = 0.08f;
    private static final float PARTICLE_DRAG = 1.2f;
    private static final float PARTICLE_KICK = 0.09f;
    private static final float PARTICLE_ALPHA = 0.16f;
    private static final float PARTICLE_INTENSITY_ALPHA = 0.2f;
    private static final float PARTICLE_SPARK_ALPHA = 0.68f;
    private static final float PARTICLE_SPARK_GROWTH = 0.5f;
    private static final float PARTICLE_SPARK_DECAY_S = 0.14f;
    private static final float PARTICLE_PAUSED_SPEED = 0.3f;
    private static final float PARTICLE_FADE_IN_S = 0.3f;
    private static final float PARTICLE_FADE_OUT_S = 0.6f;
    private static final float PARTICLE_WARMUP_S = 3f;
    private static final float PARTICLE_WARMUP_STEP_S = 1f / 30f;
    private static final float PARTICLE_CORNER_INSET = 0.3f;
    private static final float PARTICLE_MIN_COVER_SCALE = 0.4f;
    private static final float COMPACT_DIM = 0.6f;
    private static final float HAT_SPARK_SHARE = 0.4f;
    private static final float DROP_KICK = 2.6f;
    private static final float BEAT_KICK = 0.35f;
    private static final float SPARK_FLOOR = 0.15f;

    private final Paint blobPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RadialGradient[] shaders = new RadialGradient[BLOB_COUNT];
    private final int[] colors = new int[BLOB_COUNT];
    private final RhythmTap.State state = new RhythmTap.State();
    private final Random random = new Random();
    private final int[] particleColors = new int[BLOB_COUNT];
    private final float[] particleX = new float[PARTICLE_COUNT];
    private final float[] particleY = new float[PARTICLE_COUNT];
    private final float[] particleVx = new float[PARTICLE_COUNT];
    private final float[] particleVy = new float[PARTICLE_COUNT];
    private final float[] particleLife = new float[PARTICLE_COUNT];
    private final float[] particleDuration = new float[PARTICLE_COUNT];
    private final float[] particleBrightness = new float[PARTICLE_COUNT];
    private final float[] particleSpark = new float[PARTICLE_COUNT];
    private final float[] particleRadius = new float[PARTICLE_COUNT];
    private final RectF bounds = new RectF();
    private final RectF cover = new RectF();
    private final float[] flowForce = new float[2];
    private boolean particlesReady;
    private float particlePresence;
    private float particleTime;
    private float coverRadius;
    private RadialGradient bloomShader;
    private LinearGradient scrimShader;
    private int scrimColor;
    private boolean dark = true;

    private boolean active;
    private boolean animating;
    private boolean particlesEnabled;
    private float presence;
    private float energy;
    private float bass;
    private float mid;
    private float high;
    private float intensity = NEUTRAL_INTENSITY;
    private float targetIntensity = NEUTRAL_INTENSITY;
    private float pulse;
    private float surge;
    private float bloom;
    private float shimmer;
    private float bpm;
    private float spin;
    private float angle;
    private float colorShift;
    private int colorTarget;
    private long lastFrameNs;

    public void setColors(int[] glow, int background, boolean dark) {
        this.dark = dark;
        if (!Arrays.equals(colors, glow)) {
            System.arraycopy(glow, 0, colors, 0, BLOB_COUNT);
            for (int i = 0; i < BLOB_COUNT; i++) {
                shaders[i] = createGlow(colors[i]);
            }
            bloomShader = createGlow(ColorUtils.blendARGB(colors[0], Color.WHITE, BLOOM_WHITE));
        }
        if (scrimShader == null || scrimColor != background) {
            scrimColor = background;
            scrimShader = new LinearGradient(0, 0, 0, 1, ColorUtils.setAlphaComponent(background, 0), ColorUtils.setAlphaComponent(background, (int) (0xFF * SCRIM_ALPHA)), Shader.TileMode.CLAMP);
        }
        for (int i = 0; i < BLOB_COUNT; i++) {
            particleColors[i] = dark ? ColorUtils.blendARGB(Color.WHITE, colors[i], PARTICLE_TINT) : ColorUtils.blendARGB(colors[i], Color.BLACK, PARTICLE_TINT_LIGHT);
        }
    }

    private static RadialGradient createGlow(int color) {
        final int[] gradient = new int[GLOW_STOPS.length];
        for (int i = 0; i < gradient.length; i++) {
            gradient[i] = ColorUtils.setAlphaComponent(color, Math.round(0xFF * GLOW_FALLOFF[i]));
        }
        return new RadialGradient(0, 0, SHADER_RADIUS, gradient, GLOW_STOPS, Shader.TileMode.CLAMP);
    }

    public void setParticlesEnabled(boolean particlesEnabled) {
        this.particlesEnabled = particlesEnabled;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setAnimating(boolean animating) {
        if (this.animating != animating) {
            this.animating = animating;
            lastFrameNs = 0;
        }
    }

    public boolean isVisible() {
        return active || presence > SETTLED;
    }

    public boolean draw(Canvas canvas, RectF rect, float radius, RectF coverRect, float coverCorner, float coverVisibility, float compact) {
        final long now = SystemClock.elapsedRealtimeNanos();
        final float dt = lastFrameNs == 0 ? 0f : Math.min(0.05f, (now - lastFrameNs) / 1e9f);
        lastFrameNs = now;
        bounds.set(rect);
        cover.set(coverRect);
        coverRadius = coverCorner;
        step(dt, coverVisibility * (1f - Utilities.clamp01(compact)));
        final float dim = 1f - COMPACT_DIM * Utilities.clamp01(compact);
        final float glowX = cover.isEmpty() ? rect.centerX() : cover.centerX();
        final float glowY = cover.isEmpty() ? rect.centerY() : cover.centerY();
        if (presence <= SETTLED || rect.isEmpty() || shaders[0] == null) {
            return active != (presence > SETTLED);
        }
        final float width = rect.width();
        final float height = rect.height();
        final float[] alphas = dark ? BLOB_ALPHA_DARK : BLOB_ALPHA_LIGHT;
        final float centerX = rect.centerX();
        final float centerY = rect.top + height * ORBIT_CENTER_Y;
        final float liveliness = INTENSITY_ALPHA_BASE + INTENSITY_ALPHA_GAIN * intensity;
        final int shift = (int) Math.floor(colorShift);
        final float blend = colorShift - shift;
        for (int i = 0; i < BLOB_COUNT; i++) {
            final double a = angle * BLOB_SPIN[i] + BLOB_ANGLE[i];
            final float x = centerX + (float) Math.cos(a) * BLOB_ORBIT_X[i] * width;
            final float y = centerY + (float) Math.sin(a) * BLOB_ORBIT_Y[i] * height;
            final float band = BLOB_BAND[i] == BAND_BASS ? bass : BLOB_BAND[i] == BAND_MID ? mid : high;
            final float r = BLOB_RADIUS[i] * width * (1f + PULSE_RADIUS * pulse + BAND_RADIUS * band);
            final float alpha = Math.min(0.95f, (alphas[i] + PULSE_ALPHA * pulse + ENERGY_ALPHA * energy) * liveliness) * presence * dim;
            drawBlob(canvas, rect, radius, shaders[(i + shift) % BLOB_COUNT], x, y, r, alpha * (1f - blend));
            if (blend > SETTLED) {
                drawBlob(canvas, rect, radius, shaders[(i + shift + 1) % BLOB_COUNT], x, y, r, alpha * blend);
            }
        }
        if (pulse > SETTLED) {
            drawBlob(canvas, rect, radius, shaders[shift % BLOB_COUNT], glowX, glowY, GLOW_RADIUS * width * (1f + 0.12f * pulse), GLOW_ALPHA * pulse * presence * dim);
        }
        if (bloom > SETTLED && bloomShader != null) {
            drawBlob(canvas, rect, radius, bloomShader, centerX, centerY, width * (BLOOM_START + BLOOM_GROWTH * (1f - bloom)), BLOOM_ALPHA * bloom * presence * dim);
        }
        if (particlePresence > SETTLED && particlesReady) {
            drawParticles(canvas);
        }
        matrix.setScale(1f, height * (1f - SCRIM_FROM));
        matrix.postTranslate(0, rect.top + height * SCRIM_FROM);
        scrimShader.setLocalMatrix(matrix);
        scrimPaint.setShader(scrimShader);
        scrimPaint.setAlpha((int) (0xFF * presence));
        canvas.drawRoundRect(rect, radius, radius, scrimPaint);
        return needsFrame();
    }

    private void drawBlob(Canvas canvas, RectF rect, float radius, RadialGradient shader, float x, float y, float r, float alpha) {
        if (alpha <= 0f || r <= 0f) {
            return;
        }
        matrix.setScale(r / SHADER_RADIUS, r / SHADER_RADIUS);
        matrix.postTranslate(x, y);
        shader.setLocalMatrix(matrix);
        blobPaint.setShader(shader);
        blobPaint.setAlpha((int) (0xFF * Math.min(1f, alpha)));
        canvas.drawRoundRect(rect, radius, radius, blobPaint);
    }

    private void drawParticles(Canvas canvas) {
        final float level = PARTICLE_ALPHA + PARTICLE_INTENSITY_ALPHA * intensity;
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            final float life = (float) Math.sin(particleLife[i] * Math.PI) * particleBrightness[i];
            final float flash = particleSpark[i] * shimmer;
            final float alpha = Math.min(1f, life * (level + PARTICLE_SPARK_ALPHA * flash)) * particlePresence * presence;
            if (alpha < 0.01f) {
                continue;
            }
            particlePaint.setColor(particleColors[i % BLOB_COUNT]);
            particlePaint.setAlpha((int) (0xFF * alpha));
            canvas.drawCircle(particleX[i], particleY[i], particleRadius[i] * (1f + PARTICLE_SPARK_GROWTH * flash), particlePaint);
        }
    }

    private void spawnParticle(int i) {
        final float half = cover.width() / 2f;
        final double angle = random.nextFloat() * Math.PI * 2;
        final float dx = (float) Math.cos(angle);
        final float dy = (float) Math.sin(angle);
        final float major = Math.max(Math.abs(dx), Math.abs(dy));
        final float minor = Math.min(Math.abs(dx), Math.abs(dy)) / major;
        final float edge = half / major - coverRadius * PARTICLE_CORNER_INSET * minor * minor;
        particleX[i] = cover.centerX() + dx * edge;
        particleY[i] = cover.centerY() + dy * edge;
        final float speed = bounds.width() * coverScale() * (PARTICLE_MIN_LAUNCH + PARTICLE_LAUNCH_RANGE * random.nextFloat());
        particleVx[i] = dx * speed;
        particleVy[i] = dy * speed;
        particleLife[i] = 0f;
        particleDuration[i] = 0.5f + 2f * random.nextFloat();
        particleBrightness[i] = 0.6f + 0.4f * random.nextFloat();
    }

    private float coverScale() {
        return Math.max(PARTICLE_MIN_COVER_SCALE, Math.min(1f, cover.width() / Math.max(1f, bounds.width())));
    }

    private void ensureParticles() {
        if (particlesReady || cover.isEmpty() || bounds.isEmpty()) {
            return;
        }
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            spawnParticle(i);
            particleLife[i] = random.nextFloat();
            particleSpark[i] = 0f;
            particleRadius[i] = dpf2(random.nextFloat() < PARTICLE_LARGE_SHARE ? PARTICLE_LARGE_DP : PARTICLE_SMALL_DP);
        }
        particlesReady = true;
        for (float t = 0; t < PARTICLE_WARMUP_S; t += PARTICLE_WARMUP_STEP_S) {
            stepParticles(PARTICLE_WARMUP_STEP_S, 1f);
        }
    }

    private void stepParticles(float dt, float speed) {
        final float size = bounds.width();
        final float k = (float) (Math.PI * PARTICLE_NOISE_CELLS / size);
        final float ts = dt * PARTICLE_TIME_SCALE * speed;
        particleTime += ts;
        final float accel = PARTICLE_ACCEL * size;
        final float push = PARTICLE_PUSH * size;
        final float cap = PARTICLE_MAX_SPEED * size * (1f + 1.5f * surge);
        final float drag = (float) Math.exp(-dt * PARTICLE_DRAG);
        final float sparkDecay = (float) Math.exp(-dt / PARTICLE_SPARK_DECAY_S);
        final float margin = dpf2(8);
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particleLife[i] += ts * particleDuration[i] / PARTICLE_LONGEVITY;
            if (particleLife[i] >= 1f || !isInside(particleX[i], particleY[i], margin)) {
                spawnParticle(i);
                continue;
            }
            flow(particleX[i] + particleTime * PARTICLE_NOISE_DRIFT * size, particleY[i], particleTime, k);
            final float ox = particleX[i] - cover.centerX();
            final float oy = particleY[i] - cover.centerY();
            final float distance = Math.max(1f, (float) Math.hypot(ox, oy));
            particleVx[i] += (flowForce[0] * accel + ox / distance * push) * ts;
            particleVy[i] += (flowForce[1] * accel + oy / distance * push) * ts;
            final float velocity = (float) Math.hypot(particleVx[i], particleVy[i]);
            if (velocity > cap) {
                final float scale = (cap + (velocity - cap) * drag) / velocity;
                particleVx[i] *= scale;
                particleVy[i] *= scale;
            }
            particleX[i] += particleVx[i] * ts;
            particleY[i] += particleVy[i] * ts;
            particleSpark[i] *= sparkDecay;
        }
    }

    private boolean isInside(float x, float y, float margin) {
        return x >= bounds.left - margin && x <= bounds.right + margin && y >= bounds.top - margin && y <= bounds.bottom + margin;
    }

    private void flow(float x, float y, float t, float k) {
        final double a = k * 0.9 * x + 0.6 * t;
        final double b = k * 0.7 * y - 0.45 * t;
        final double c = k * 1.7 * (0.6 * x + 0.8 * y) + 0.8 * t;
        final double dx = 0.9 * k * Math.cos(a) * Math.cos(b) + 0.85 * k * 0.6 * Math.cos(c);
        final double dy = -0.7 * k * Math.sin(a) * Math.sin(b) + 0.85 * k * 0.8 * Math.cos(c);
        final double length = Math.max(1e-6, Math.hypot(dx, dy));
        flowForce[0] = (float) (dy / length);
        flowForce[1] = (float) (-dx / length);
    }

    private void kickParticles(float strength) {
        if (!particlesReady) {
            return;
        }
        final float size = bounds.width();
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            final float ox = particleX[i] - cover.centerX();
            final float oy = particleY[i] - cover.centerY();
            final float distance = Math.max(1f, (float) Math.hypot(ox, oy));
            final float push = size * PARTICLE_KICK * strength * (0.6f + 0.4f * random.nextFloat());
            particleVx[i] += ox / distance * push;
            particleVy[i] += oy / distance * push;
        }
    }

    private void sparkle(float share) {
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particleSpark[i] = random.nextFloat() < share ? 1f : Math.max(particleSpark[i], SPARK_FLOOR);
        }
    }

    private void step(float dt, float coverVisibility) {
        final RhythmTap tap = animating ? RhythmTap.getCurrent() : null;
        float targetEnergy = 0, targetBass = 0, targetMid = 0, targetHigh = 0;
        if (tap != null) {
            state.clearEvents();
            tap.read(state);
            if (state.beat > 0f) {
                final float accent = Math.min(1f, state.beat / DOWNBEAT_FULL_STRENGTH);
                pulse = Math.max(pulse, state.downbeat ? DOWNBEAT_PULSE * accent : BEAT_PULSE * state.beat);
                surge = Math.max(surge, state.downbeat ? DOWNBEAT_SURGE * accent : BEAT_SURGE * state.beat);
                kickParticles(state.downbeat ? accent : BEAT_KICK * state.beat);
            }
            if (state.phrases > 0) {
                colorTarget += state.phrases;
            }
            if (state.hat > MIN_HAT) {
                shimmer = Math.max(shimmer, state.hat);
                sparkle(HAT_SPARK_SHARE);
            }
            if (state.drop) {
                bloom = 1f;
                surge = Math.max(surge, DROP_SURGE);
                shimmer = 1f;
                kickParticles(DROP_KICK);
                sparkle(1f);
            }
            bpm = state.bpm;
            targetEnergy = state.energy;
            targetBass = state.bass;
            targetMid = state.mid;
            targetHigh = state.high;
            targetIntensity = state.intensity;
        }
        presence = approach(presence, active ? 1f : 0f, dt, PRESENCE_TAU_S, PRESENCE_TAU_S);
        energy = approach(energy, targetEnergy, dt, 0.12f, 0.35f);
        bass = approach(bass, targetBass, dt, 0.04f, 0.22f);
        mid = approach(mid, targetMid, dt, 0.08f, 0.3f);
        high = approach(high, targetHigh, dt, 0.05f, 0.25f);
        intensity = approach(intensity, animating ? targetIntensity : NEUTRAL_INTENSITY, dt, INTENSITY_TAU_S, INTENSITY_TAU_S);
        pulse *= (float) Math.exp(-dt / PULSE_DECAY_S);
        surge *= (float) Math.exp(-dt / SURGE_DECAY_S);
        bloom *= (float) Math.exp(-dt / BLOOM_DECAY_S);
        shimmer *= (float) Math.exp(-dt / SHIMMER_DECAY_S);
        colorShift = approach(colorShift, colorTarget, dt, COLOR_SHIFT_TAU_S, COLOR_SHIFT_TAU_S);
        if (colorTarget >= BLOB_COUNT && colorShift >= BLOB_COUNT) {
            colorTarget -= BLOB_COUNT;
            colorShift -= BLOB_COUNT;
        }
        final float lively = INTENSITY_SPIN_BASE + INTENSITY_SPIN_GAIN * intensity;
        particlePresence = approach(particlePresence, animating && particlesEnabled ? coverVisibility : 0f, dt, PARTICLE_FADE_IN_S, PARTICLE_FADE_OUT_S);
        if (particlePresence > SETTLED) {
            ensureParticles();
            if (particlesReady && dt > 0f) {
                stepParticles(dt, lively * (1f + 0.6f * surge) * (animating ? 1f : PARTICLE_PAUSED_SPEED));
            }
        }
        final float turns = !animating ? 0f : bpm > 0f ? bpm / 60f / BEATS_PER_TURN * lively : IDLE_TURNS_PER_S + ENERGY_TURNS_PER_S * energy;
        spin = approach(spin, turns, dt, SPIN_TAU_S, SPIN_TAU_S);
        angle = (float) ((angle + dt * spin * (1f + SURGE_GAIN * surge) * 2 * Math.PI) % (Math.PI * 2 * 60));
    }

    private boolean needsFrame() {
        return animating || Math.abs(presence - (active ? 1f : 0f)) > SETTLED || pulse > SETTLED || energy > SETTLED
                || bass > SETTLED || mid > SETTLED || high > SETTLED || bloom > SETTLED || shimmer > SETTLED || particlePresence > SETTLED
                || Math.abs(colorShift - colorTarget) > SETTLED || spin > SETTLED * 0.1f;
    }

    private static float approach(float value, float target, float dt, float upTau, float downTau) {
        if (dt <= 0f) {
            return value;
        }
        final float tau = target > value ? upTau : downTau;
        return value + (target - value) * (1f - (float) Math.exp(-dt / tau));
    }
}
