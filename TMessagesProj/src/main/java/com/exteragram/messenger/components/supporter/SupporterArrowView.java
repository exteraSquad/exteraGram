/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.components.supporter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.GLIcon.GLIconRenderer;
import org.telegram.ui.Components.Premium.GLIcon.GLIconTextureView;
import org.telegram.ui.Components.Premium.GLIcon.Icon3D;
import org.telegram.ui.Components.Premium.StarParticlesView;

public class SupporterArrowView extends FrameLayout {

    public final GLIconTextureView iconView;
    private final Theme.ResourcesProvider resourcesProvider;
    private BadgeIconView badgeView;
    private boolean iconHidden;
    public final int color1, color2;

    public SupporterArrowView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setClipChildren(false);

        final int particlesCount = switch (SharedConfig.getDevicePerformanceClass()) {
            case SharedConfig.PERFORMANCE_CLASS_HIGH -> 90;
            case SharedConfig.PERFORMANCE_CLASS_AVERAGE -> 50;
            default -> 25;
        };
        StarParticlesView particlesView = new StarParticlesView(context, particlesCount) {
            @Override
            protected void configure() {
                super.configure();
                drawable.useGradient = false;
                drawable.useBlur = false;
                drawable.forceMaxAlpha = true;
                drawable.checkBounds = true;
                drawable.colorKey = Theme.key_featuredStickers_addButton;
                drawable.resourcesProvider = resourcesProvider;
                drawable.init();
            }
        };
        addView(particlesView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 190, Gravity.CENTER));

        final int accentColor = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
        color1 = shiftColor(accentColor, -10f, .75f, 1.15f);
        color2 = shiftColor(accentColor, 18f, .6f, 1.1f);
        iconView = createIcon(context, resourcesProvider, color1, color2, ColorUtils.blendARGB(accentColor, Theme.getColor(Theme.key_dialogBackground, resourcesProvider), 0.5f));
        iconView.setStarParticlesView(particlesView);
        addView(iconView, LayoutHelper.createFrame(160, 160, Gravity.CENTER));
    }

    public static GLIconTextureView createIcon(Context context, Theme.ResourcesProvider resourcesProvider, int color1, int color2, int edgeColor) {
        GLIconTextureView iconView = new GLIconTextureView(context, GLIconRenderer.DIALOG_STYLE, Icon3D.TYPE_EXTERA_ARROW) {
            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                setPaused(false);
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                setPaused(true);
            }
        };
        Bitmap bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888);
        new Canvas(bitmap).drawColor(edgeColor);
        iconView.setBackgroundBitmap(bitmap);
        iconView.mRenderer.resourcesProvider = resourcesProvider;
        iconView.mRenderer.setColors(color1, color2);
        return iconView;
    }

    public static void setNumber(GLIconTextureView iconView, String number) {
        Utilities.globalQueue.postRunnable(() -> {
            final Bitmap mask = SupporterArrowEngraving.createMask(number);
            AndroidUtilities.runOnUIThread(() -> iconView.mRenderer.setEngraving(mask));
        });
    }

    public void setBadge(int account, long documentId) {
        if (badgeView == null) {
            removeView(iconView);
            badgeView = new BadgeIconView(getContext());
            addView(badgeView, LayoutHelper.createFrame(130, 130, Gravity.CENTER));
        }
        badgeView.set(account, documentId, Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
    }

    public void setNumber(String number) {
        setNumber(iconView, number);
    }

    public View getIconView() {
        return badgeView != null ? badgeView : iconView;
    }

    public boolean hasBadge() {
        return badgeView != null;
    }

    public void setIconHidden(boolean hidden) {
        if (iconHidden != hidden) {
            iconHidden = hidden;
            invalidate();
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == getIconView() && iconHidden) {
            return true;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    static int shiftColor(int color, float hueShift, float saturationScale, float valueScale) {
        final float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + hueShift + 360f) % 360f;
        hsv[1] = Math.min(1f, hsv[1] * saturationScale);
        hsv[2] = Math.min(1f, hsv[2] * valueScale);
        return Color.HSVToColor(Color.alpha(color), hsv);
    }

    private static class BadgeIconView extends View {

        private AnimatedEmojiDrawable drawable;
        private PorterDuffColorFilter colorFilter;

        BadgeIconView(Context context) {
            super(context);
        }

        void set(int account, long documentId, int color) {
            if (drawable != null && isAttachedToWindow()) {
                drawable.removeView(this);
            }
            drawable = AnimatedEmojiDrawable.make(account, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_LARGE_140, documentId);
            colorFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
            if (isAttachedToWindow()) {
                drawable.addView(this);
            }
            invalidate();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (drawable != null) {
                drawable.addView(this);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (drawable != null) {
                drawable.removeView(this);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (drawable != null) {
                drawable.setBounds(0, 0, getWidth(), getHeight());
                final boolean tint = drawable.canOverrideColor();
                if (tint) {
                    drawable.setColorFilter(colorFilter);
                }
                drawable.draw(canvas);
                if (tint) {
                    drawable.setColorFilter(null);
                }
            }
        }
    }
}
