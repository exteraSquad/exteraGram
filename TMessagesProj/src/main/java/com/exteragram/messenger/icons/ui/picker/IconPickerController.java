/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.icons.ui.picker;

import android.annotation.SuppressLint;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.ui.LaunchActivity;

public class IconPickerController {
    @SuppressLint("StaticFieldLeak")
    private static IconPickerView pickerView;
    private static boolean isInitializing;

    public boolean isActive() {
        return pickerView != null;
    }

    public static void onDestroy() {
        if (pickerView != null) {
            pickerView.saveConfig();
            if (pickerView.getParent() instanceof ViewGroup) {
                ((ViewGroup) pickerView.getParent()).removeView(pickerView);
            }
        }
        pickerView = null;
    }

    public static boolean onBackPressed(boolean invoked) {
        return pickerView != null && pickerView.onBackPressed(invoked);
    }

    public static void setActive(LaunchActivity activity, boolean active) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        boolean isCurrentlyActive = (pickerView != null);
        if (active == isCurrentlyActive) {
            return;
        }

        if (active) {
            showPicker(activity);
        } else {
            hidePicker(activity);
        }
    }

    private static void showPicker(LaunchActivity activity) {
        if (ExteraConfig.getEditingIconPackId() == null) {
            return;
        }

        if (pickerView != null || isInitializing) {
            return;
        }

        isInitializing = true;
        try {
            pickerView = new IconPickerView(activity);
            FrameLayout container = activity.getMainContainerFrameLayout();

            if (container != null) {
                container.addView(pickerView, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                ));
                pickerView.showFab();
            }
        } finally {
            isInitializing = false;
        }
    }

    private static void hidePicker(LaunchActivity activity) {
        if (pickerView == null) {
            return;
        }

        IconObserver.INSTANCE.clear();

        final IconPickerView currentView = pickerView;
        pickerView = null;

        currentView.dismiss(() -> {
            FrameLayout container = activity.getMainContainerFrameLayout();
            if (container != null) {
                container.removeView(currentView);
            } else if (currentView.getParent() instanceof ViewGroup) {
                ((ViewGroup) currentView.getParent()).removeView(currentView);
            }
        });
    }
}
