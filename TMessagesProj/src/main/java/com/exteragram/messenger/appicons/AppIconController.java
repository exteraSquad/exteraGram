/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.appicons;

import android.content.Context;
import android.content.pm.PackageManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class AppIconController {

    private static List<AppIcon> allIcons;
    private static AppIcon selectedIcon;

    private AppIconController() {
    }

    public static List<AppIcon> getAllIcons() {
        if (allIcons == null) {
            allIcons = Collections.unmodifiableList(Arrays.asList(GeneratedAppIcons.create()));
        }
        return allIcons;
    }

    public static List<AppIcon> getAvailableIcons() {
        List<AppIcon> icons = new ArrayList<>();
        for (AppIcon icon : getAllIcons()) {
            if (icon.isAvailable()) {
                icons.add(icon);
            }
        }
        return icons;
    }

    public static AppIcon findById(String id) {
        for (AppIcon icon : getAllIcons()) {
            if (icon.id.equals(id)) {
                return icon;
            }
        }
        return null;
    }

    public static AppIcon getDefaultIcon() {
        AppIcon icon = findById(AppIcon.DEFAULT_ID);
        return icon != null ? icon : getAllIcons().get(0);
    }

    public static boolean isEnabled(AppIcon icon) {
        Context context = ApplicationLoader.applicationContext;
        int state = context.getPackageManager().getComponentEnabledSetting(icon.getComponentName(context));
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                || state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon.isDefault();
    }

    public static AppIcon getSelectedIcon() {
        if (selectedIcon == null) {
            for (AppIcon icon : getAllIcons()) {
                if (isEnabled(icon)) {
                    selectedIcon = icon;
                    break;
                }
            }
            if (selectedIcon == null) {
                selectedIcon = getDefaultIcon();
            }
        }
        return selectedIcon;
    }

    public static void setIcon(AppIcon icon) {
        selectedIcon = icon;
        final List<AppIcon> all = getAllIcons();
        Utilities.globalQueue.postRunnable(() -> applyIcon(icon, all));
    }

    private static void applyIcon(AppIcon icon, List<AppIcon> all) {
        Context context = ApplicationLoader.applicationContext;
        PackageManager packageManager = context.getPackageManager();
        packageManager.setComponentEnabledSetting(icon.getComponentName(context),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        for (AppIcon other : all) {
            if (other == icon) {
                continue;
            }
            packageManager.setComponentEnabledSetting(other.getComponentName(context),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }

    public static void fixLauncherIconIfNeeded() {
        final List<AppIcon> all = getAllIcons();
        final AppIcon fallback = getDefaultIcon();
        Utilities.globalQueue.postRunnable(() -> {
            for (AppIcon icon : all) {
                if (isEnabled(icon)) {
                    AndroidUtilities.runOnUIThread(() -> selectedIcon = icon);
                    if (icon.isMonet()) {
                        applyIcon(fallback, all);
                        applyIcon(icon, all);
                    }
                    return;
                }
            }
            AndroidUtilities.runOnUIThread(() -> selectedIcon = fallback);
            applyIcon(fallback, all);
        });
    }
}
