package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.R;

public class LauncherIconController {
    public static void tryFixLauncherIconIfNeeded() {
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (isEnabled(icon)) {
                return;
            }
        }

        setIcon(LauncherIcon.DEFAULT);
    }

    public static void updateMonetIcon() {
        if (isEnabled(LauncherIcon.MONET)) {
            setIcon(LauncherIcon.DEFAULT);
            setIcon(LauncherIcon.MONET);
        }
    }

    public static boolean isEnabled(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        int i = ctx.getPackageManager().getComponentEnabledSetting(icon.getComponentName(ctx));
        return i == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || i == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == LauncherIcon.DEFAULT;
    }

    public static void setIcon(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        PackageManager pm = ctx.getPackageManager();
        for (LauncherIcon i : LauncherIcon.values()) {
            pm.setComponentEnabledSetting(i.getComponentName(ctx), i == icon ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }

    public enum LauncherIcon {
        DEFAULT("DefaultIcon", BuildVars.isBetaApp() ? R.color.ic_background_beta : R.color.ic_background, R.drawable.ic_foreground, R.string.AppIconDefault),
        MONET("MonetIcon", R.color.ic_background_monet, R.drawable.ic_foreground_monet, R.string.AppIconMonet),
        GRADIENT("GradientIcon", R.mipmap.ic_background_gradient, R.drawable.ic_foreground, R.string.AppIconGradient),
        GOOGLE("GoogleIcon", R.color.white, R.mipmap.ic_foreground_google, R.string.AppIconGoogle),
        VK("VKIcon", R.color.ic_background_vk, R.drawable.ic_foreground_vk, R.string.AppIconVK),
        DSGN480("Dsgn480Icon", R.mipmap.ic_background_480dsgn, R.mipmap.ic_foreground_480dsgn, R.string.AppIcon480DSGN),
        GLOW("GlowIcon", R.mipmap.ic_background_glow, R.mipmap.ic_foreground_glow, R.string.AppIconGlow),
        SPACE("SpaceIcon", R.mipmap.ic_background_space, R.mipmap.ic_foreground_space, R.string.AppIconSpace),
        WINTER("WinterIcon", R.mipmap.ic_background_winter, R.drawable.ic_foreground, R.string.AppIconWinter),
        RED("RedIcon", R.mipmap.ic_background_red, R.mipmap.ic_foreground_red, R.string.AppIconRed);
        public final String key;
        public final int background;
        public final int foreground;
        public final int title;
        public final boolean premium;

        private ComponentName componentName;

        public ComponentName getComponentName(Context ctx) {
            if (componentName == null) {
                componentName = new ComponentName(ctx.getPackageName(), "com.exteragram.messenger." + key);
            }
            return componentName;
        }

        LauncherIcon(String key, int background, int foreground, int title) {
            this(key, background, foreground, title, false);
        }

        LauncherIcon(String key, int background, int foreground, int title, boolean premium) {
            this.key = key;
            this.background = background;
            this.foreground = foreground;
            this.title = title;
            this.premium = premium;
        }
    }
}
