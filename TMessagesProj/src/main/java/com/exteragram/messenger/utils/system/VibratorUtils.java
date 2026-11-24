/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.system;

import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.ViewGroup;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

public class VibratorUtils {

    private static final Vibrator vibrator;

    static {
        vibrator = ApplicationLoader.applicationContext.getSystemService(Vibrator.class);
    }

    private static boolean isVibrationAllowed() {
        return ExteraConfig.getInAppVibration() && vibrator.hasVibrator();
    }

    @SuppressWarnings("deprecation")
    public static void vibrate(long time) {
        if (!isVibrationAllowed()) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                VibrationEffect effect = VibrationEffect.createOneShot(time, VibrationEffect.DEFAULT_AMPLITUDE);
                vibrator.vibrate(effect);
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else {
            try {
                vibrator.vibrate(time);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public static void vibrate() {
        vibrate(200L);
    }

    public static void vibrateEffect(VibrationEffect effect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isVibrationAllowed()) {
            try {
                vibrator.cancel();
            } catch (Exception e) {
                FileLog.e(e);
            }

            try {
                vibrator.vibrate(effect);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public static void disableHapticFeedback(View view) {
        if (view == null) {
            return;
        }

        view.setHapticFeedbackEnabled(false);

        if (view instanceof ViewGroup viewGroup) {
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View childView = viewGroup.getChildAt(i);
                disableHapticFeedback(childView);
            }
        }
    }

    public static int getType(int original) {
        return ExteraConfig.getInAppVibration() ? original : -1;
    }
}
