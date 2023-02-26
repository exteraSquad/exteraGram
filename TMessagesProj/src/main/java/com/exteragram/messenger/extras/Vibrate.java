/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.extras;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.BaseFragment;

public final class Vibrate {

    private final static long time = 200L;
    private static long[] vibrationWaveFormDurationPattern = {0, 1};

    public static void disableHapticFeedback(BaseFragment fragment) {
        disableHapticFeedback(fragment.getFragmentView());
    }
    public static void disableHapticFeedback(View view) {
        if (view == null) {
            return;
        }
        view.setHapticFeedbackEnabled(false);
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                View child = ((ViewGroup) view).getChildAt(i);
                child.setHapticFeedbackEnabled(false);
            }
        }
    }

    public static void vibrate() {
        Vibrator vibrator;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = (VibratorManager) ApplicationLoader.applicationContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vibratorManager.getDefaultVibrator();
        } else {
            vibrator = AndroidUtilities.getVibrator();
        }
        if (vibrator != null && !vibrator.hasVibrator()) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    VibrationEffect vibrationEffect = VibrationEffect.createWaveform(vibrationWaveFormDurationPattern, -1);
                    vibrator.cancel();
                    vibrator.vibrate(vibrationEffect);
                    setDefaultPattern();
                } else {
                    vibrator.vibrate(time);
                }
            } catch (Exception e) {
                FileLog.e("Failed to vibrate");
            }
        }
    }

    public static void setPattern(long[] pattern) {
        vibrationWaveFormDurationPattern = pattern;
    }

    public static void setDefaultPattern() {
        Vibrate.setPattern(new long[]{0, 1});
    }
}