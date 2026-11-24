/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.appicons;

import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

import com.exteragram.messenger.utils.AppUtils;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

public final class AppIcon {

    public static final String DEFAULT_ID = "default";

    private static final String AVAILABILITY_WINTER = "winter";
    private static final String AVAILABILITY_MONET = "monet";

    private static final String AUTHOR_SELF = "exteraGram";

    public final String id;
    public final String component;
    public final String availability;
    public final int titleRes;
    public final String title;
    public final int descriptionRes;
    public final String description;
    public final int color;
    public final String authorUsername;
    public final String authorName;
    public final int background;
    public final boolean backgroundIsColor;
    public final int foreground;
    public final int monochrome;

    private ComponentName componentName;

    private AppIcon(String id, String component, String availability, int titleRes, String title,
                    int descriptionRes, String description, int color, String authorUsername, String authorName,
                    int background, boolean backgroundIsColor, int foreground, int monochrome) {
        this.id = id;
        this.component = component;
        this.availability = availability;
        this.titleRes = titleRes;
        this.title = title;
        this.descriptionRes = descriptionRes;
        this.description = description;
        this.color = color;
        this.authorUsername = authorUsername;
        this.authorName = authorName;
        this.background = background;
        this.backgroundIsColor = backgroundIsColor;
        this.foreground = foreground;
        this.monochrome = monochrome;
    }

    public static AppIcon of(String id, String component, String availability, int titleRes, String title,
                             int descriptionRes, String description, int color, String authorUsername, String authorName,
                             int background, boolean backgroundIsColor, int foreground, int monochrome) {
        return new AppIcon(id, component, availability, titleRes, title, descriptionRes, description, color,
                authorUsername, authorName, background, backgroundIsColor, foreground, monochrome);
    }

    public ComponentName getComponentName(Context context) {
        if (componentName == null) {
            componentName = new ComponentName(context.getPackageName(), component);
        }
        return componentName;
    }

    public boolean isDefault() {
        return DEFAULT_ID.equals(id);
    }

    public boolean isMonet() {
        return AVAILABILITY_MONET.equals(availability);
    }

    public boolean isAvailable() {
        if (AVAILABILITY_WINTER.equals(availability)) {
            return AppUtils.isWinter();
        }
        if (isMonet()) {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2;
        }
        return true;
    }

    public int getBackground() {
        return isDefault() && BuildVars.isBetaApp() ? R.mipmap.ic_launcher_beta_background : background;
    }

    public boolean isBackgroundColor() {
        return backgroundIsColor && !(isDefault() && BuildVars.isBetaApp());
    }

    public int getForeground() {
        return isDefault() && BuildVars.isBetaApp() ? R.mipmap.ic_launcher_beta_foreground : foreground;
    }

    public CharSequence getTitle() {
        return titleRes != 0 ? LocaleController.getString(titleRes) : title;
    }

    public CharSequence getDescription() {
        if (descriptionRes != 0) {
            return LocaleController.getString(descriptionRes);
        }
        return description;
    }

    public String getAuthor() {
        if (authorName != null) {
            return authorName;
        }
        if (authorUsername == null || AUTHOR_SELF.equalsIgnoreCase(authorUsername)) {
            return null;
        }
        return "@" + authorUsername;
    }
}
