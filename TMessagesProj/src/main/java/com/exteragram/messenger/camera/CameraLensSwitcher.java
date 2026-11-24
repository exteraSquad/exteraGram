/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.camera;

import android.os.Build;
import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;

import java.util.HashSet;
import java.util.Set;

final class CameraLensSwitcher {

    interface ZoomSink {
        void setZoomRatio(float zoom);
    }

    private static final long SETTLE_MS = 200L;
    private static final long STEP_TIMEOUT_MS = 600L;
    private static final long STUCK_DELAY_MS = 600L;
    private static final long RAMP_MS = 120L;
    private static final long RAMP_STEP_MS = 33L;
    private static final int MAX_KICKS = 2;
    private static final float STUCK_MARGIN = 1.15f;

    private static final int STATE_DIRECT = 0;
    private static final int STATE_SETTLING = 1;
    private static final int STATE_STEPPING = 2;
    private static final int STATE_RAMPING = 3;

    private static final String GUARD_KEY_PREFIX = "camera_lens_guard_";
    private static final Set<String> FUTILE_KICKS = new HashSet<>();

    private final ZoomSink sink;
    private final Runnable updateRunnable = () -> update(false);
    private final Runnable stuckRunnable = this::onStuckCheck;

    private CameraLensStops.LensSet lensSet = CameraLensStops.LensSet.EMPTY;
    private String cameraId;
    private String guardKey;
    private String activePhysicalCameraId;
    private float desiredZoom = Float.NaN;
    private float sentZoom = Float.NaN;
    private float rampFrom = Float.NaN;
    private long rampStartedAt;
    private long lensChangedAt;
    private long steppingSince;
    private boolean stepExpired;
    private boolean guarded;
    private int kicks;
    private int state = STATE_DIRECT;

    CameraLensSwitcher(ZoomSink sink) {
        this.sink = sink;
    }

    void bind(CameraLensStops.LensSet lensSet, String cameraId, String guardKey, float currentZoom) {
        stop();
        this.lensSet = lensSet;
        this.cameraId = cameraId;
        this.guardKey = guardKey == null ? null : GUARD_KEY_PREFIX + guardKey;
        activePhysicalCameraId = CameraActiveLens.get(cameraId);
        guarded = guardKey != null && Build.FINGERPRINT.equals(
                MessagesController.getGlobalMainSettings().getString(this.guardKey, null));
        sentZoom = currentZoom;
        lensChangedAt = 0L;
    }

    void stop() {
        AndroidUtilities.cancelRunOnUIThread(updateRunnable);
        AndroidUtilities.cancelRunOnUIThread(stuckRunnable);
        desiredZoom = Float.NaN;
        rampFrom = Float.NaN;
        steppingSince = 0L;
        stepExpired = false;
        kicks = 0;
        state = STATE_DIRECT;
    }

    void setZoom(float zoom) {
        desiredZoom = zoom;
        update(true);
    }

    void onActiveLensChanged() {
        String physicalCameraId = CameraActiveLens.get(cameraId);
        if (physicalCameraId == null || physicalCameraId.equals(activePhysicalCameraId)) {
            return;
        }
        activePhysicalCameraId = physicalCameraId;
        lensChangedAt = SystemClock.uptimeMillis();
        steppingSince = 0L;
        stepExpired = false;
        update(false);
    }

    private void update(boolean resend) {
        AndroidUtilities.cancelRunOnUIThread(updateRunnable);
        AndroidUtilities.cancelRunOnUIThread(stuckRunnable);
        float desired = desiredZoom;
        if (Float.isNaN(desired)) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        int active = lensSet.activeLensIndex(CameraActiveLens.get(cameraId));
        int expected = lensSet.lensIndexAt(desired);
        float target = desired;
        long recheckDelay = 0L;
        int newState = STATE_DIRECT;
        if (active < 0 || active == expected) {
            kicks = 0;
            stepExpired = false;
        } else if (guarded && expected < active) {
            long settleLeft = lensChangedAt + (SETTLE_MS << Math.min(kicks, MAX_KICKS)) - now;
            if (settleLeft > 0L) {
                target = towardDesired(lensSet.limitZoom(desired, active, 0), desired);
                recheckDelay = settleLeft;
                newState = STATE_SETTLING;
            } else if (!stepExpired) {
                float limited = towardDesired(lensSet.limitZoom(desired, active, 1), desired);
                if (limited != desired) {
                    if (steppingSince == 0L) {
                        steppingSince = now;
                    }
                    long stepLeft = steppingSince + STEP_TIMEOUT_MS - now;
                    if (stepLeft > 0L) {
                        target = limited;
                        recheckDelay = stepLeft;
                        newState = STATE_STEPPING;
                    } else {
                        stepExpired = true;
                    }
                }
            }
        }
        if (newState != STATE_STEPPING) {
            steppingSince = 0L;
        }
        if (newState == STATE_DIRECT) {
            if (state == STATE_SETTLING || state == STATE_STEPPING) {
                rampFrom = sentZoom;
                rampStartedAt = now;
            }
            float progress = (now - rampStartedAt) / (float) RAMP_MS;
            if (rampFrom > 0.0f && rampFrom != desired && progress < 1.0f) {
                target = (float) (rampFrom * Math.pow(desired / rampFrom, progress));
                recheckDelay = RAMP_STEP_MS;
                newState = STATE_RAMPING;
            } else {
                rampFrom = Float.NaN;
            }
        } else {
            rampFrom = Float.NaN;
        }
        if (resend || target != sentZoom) {
            sentZoom = target;
            sink.setZoomRatio(target);
        }
        state = newState;
        if (recheckDelay > 0L) {
            AndroidUtilities.runOnUIThread(updateRunnable, recheckDelay);
        } else if (active >= 0 && lensSet.lensIndexAt(desired * STUCK_MARGIN) < active && kicks <= MAX_KICKS
                && !FUTILE_KICKS.contains(kickKey(active, expected))) {
            AndroidUtilities.runOnUIThread(stuckRunnable, STUCK_DELAY_MS);
        }
    }

    private void onStuckCheck() {
        if (Float.isNaN(desiredZoom) || state != STATE_DIRECT) {
            return;
        }
        int active = lensSet.activeLensIndex(CameraActiveLens.get(cameraId));
        int expected = lensSet.lensIndexAt(desiredZoom);
        if (active < 0 || guardKey == null || lensSet.lensIndexAt(desiredZoom * STUCK_MARGIN) >= active) {
            return;
        }
        if (kicks >= MAX_KICKS) {
            kicks = MAX_KICKS + 1;
            FUTILE_KICKS.add(kickKey(active, expected));
            MessagesController.getGlobalMainSettings().edit().remove(guardKey).apply();
            guarded = false;
            return;
        }
        kicks++;
        MessagesController.getGlobalMainSettings().edit().putString(guardKey, Build.FINGERPRINT).apply();
        guarded = true;
        lensChangedAt = SystemClock.uptimeMillis();
        stepExpired = false;
        sentZoom = lensSet.limitZoom(desiredZoom, active, 0);
        sink.setZoomRatio(sentZoom);
        update(false);
    }

    private float towardDesired(float zoom, float desired) {
        if (Float.isNaN(sentZoom)) {
            return zoom;
        }
        return Math.max(Math.min(sentZoom, desired), Math.min(Math.max(sentZoom, desired), zoom));
    }

    private String kickKey(int active, int expected) {
        return guardKey + ":" + active + ">" + expected;
    }
}
