/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.vectordrawable.graphics.drawable.Animatable2Compat;

import com.exteragram.messenger.ExteraConfig;
import com.google.android.material.progressindicator.BaseProgressIndicator;
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec;
import com.google.android.material.progressindicator.DeterminateDrawable;
import com.google.android.material.progressindicator.IndeterminateDrawable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.MessageDrawable;
import org.telegram.ui.ActionBar.Theme;

import java.util.Locale;

public class RadialProgress2 implements Drawable.Callback {
    public final static int STYLE_LEGACY = 0;
    public final static int STYLE_MATERIAL3_FLAT = 1;
    public final static int STYLE_MATERIAL3_WAVY = 2;

    private static final Animatable2Compat.AnimationCallback indeterminateHideCallback = new Animatable2Compat.AnimationCallback() {
        @Override
        public void onAnimationEnd(Drawable drawable) {
            AndroidUtilities.runOnUIThread(() -> cancelIndeterminateAnimator(drawable));
        }
    };

    protected int style;

    public RectF progressRect = new RectF();
    private View parent;

    private boolean drawMiniIcon;
    private int progressColor = 0xffffffff;
    private Paint miniProgressBackgroundPaint;

    private Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint circleMiniPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public MediaActionDrawable mediaActionDrawable;
    private MediaActionDrawable miniMediaActionDrawable;

    private CircularProgressIndicatorSpec progressSpec;
    private DeterminateDrawable<CircularProgressIndicatorSpec> progressDrawable;
    private IndeterminateDrawable<CircularProgressIndicatorSpec> indeterminateDrawable;

    private CircularProgressIndicatorSpec miniProgressSpec;
    private DeterminateDrawable<CircularProgressIndicatorSpec> miniProgressDrawable;
    private IndeterminateDrawable<CircularProgressIndicatorSpec> miniIndeterminateDrawable;

    private int waveAmplitude;
    private boolean needDrawBackground = true;
    private boolean invertColors = false;
    private int lastMainProgressLevel = -1;
    private int lastMiniProgressLevel = -1;

    private int indicatorColor = -1;
    private int indicatorPressedColor = -1;
    private int trackColor = -1;
    private int trackPressedColor = -1;

    private float miniIconScale = 1.0f;
    private int circleColor;
    private int circlePressedColor;
    public int iconColor;
    private int iconPressedColor;
    private int circleColorKey = -1;
    private int circleCrossfadeColorKey = -1;
    private float circleCrossfadeColorProgress;
    private float circleCheckProgress = 1.0f;
    private int circlePressedColorKey = -1;
    public int iconColorKey = -1;
    private int iconPressedColorKey = -1;
    public ImageReceiver overlayImageView;
    private int circleRadius;
    private boolean isPressed;
    private boolean isPressedMini;
    public float overrideCircleAlpha = 1f;

    private int backgroundStroke;

    private boolean drawBackground = true;

    private Bitmap miniDrawBitmap;
    private Canvas miniDrawCanvas;

    private float overrideAlpha = 1.0f;
    private Theme.ResourcesProvider resourcesProvider;
    private int maxIconSize;
    private float overlayImageAlpha = 1f;

    public float iconScale = 1f;
    public float drawScale = 1f;

    private static final float changePercent = 0.04f;

    public RadialProgress2(View parentView) {
        this(parentView, null);
    }

    public RadialProgress2(View parentView, Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
        miniProgressBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        parent = parentView;

        overlayImageView = new ImageReceiver(parentView);
        overlayImageView.setInvalidateAll(true);

        mediaActionDrawable = new MediaActionDrawable();

        miniMediaActionDrawable = new MediaActionDrawable();
        miniMediaActionDrawable.setMini(true);
        miniMediaActionDrawable.setIcon(MediaActionDrawable.ICON_NONE, false);

        circleRadius = dp(22);
        overlayImageView.setRoundRadius(circleRadius);

        overlayPaint.setColor(0x64000000);

        if (parentView != null) {
            mediaActionDrawable.setDelegate(parentView::invalidate);
            miniMediaActionDrawable.setDelegate(parentView::invalidate);
        }
    }

    public void setParent(View parent) {
        this.parent = parent;
        overlayImageView.setParentView(parent);
        mediaActionDrawable.setDelegate(parent::invalidate);
        miniMediaActionDrawable.setDelegate(parent::invalidate);
    }

    @Keep
    public void setStyle(int style) {
        if (!ExteraConfig.getNewLoadingStyle() && style != STYLE_LEGACY) {
            style = STYLE_LEGACY;
        }
        if (this.style == style) return;

        this.style = style;
        if (style == STYLE_LEGACY) {
            mediaActionDrawable.drawProgressCircle = true;
            miniMediaActionDrawable.drawProgressCircle = true;
            stopMdcDrawable(progressDrawable, true);
            stopMdcDrawable(indeterminateDrawable, true);
            stopMdcDrawable(miniProgressDrawable, true);
            stopMdcDrawable(miniIndeterminateDrawable, true);
            progressDrawable = null;
            indeterminateDrawable = null;
            miniProgressDrawable = null;
            miniIndeterminateDrawable = null;
            progressSpec = null;
            miniProgressSpec = null;
        } else if (style == STYLE_MATERIAL3_FLAT || style == STYLE_MATERIAL3_WAVY) {
            mediaActionDrawable.drawProgressCircle = false;
            miniMediaActionDrawable.drawProgressCircle = false;

            initMdcDrawables(parent.getContext());
            if (lastMainProgressLevel >= 0) {
                progressDrawable.setLevel(lastMainProgressLevel);
            }
            if (lastMiniProgressLevel >= 0) {
                miniProgressDrawable.setLevel(lastMiniProgressLevel);
            }
            setWavy(style == STYLE_MATERIAL3_WAVY);
        }
    }

    @Keep
    public int getStyle() {
        return style;
    }

    public boolean isMaterial3Style() {
        return style == STYLE_MATERIAL3_FLAT || style == STYLE_MATERIAL3_WAVY;
    }

    @Keep
    public void setSpecValues(int indicatorSize, int trackThickness, int trackCornerRadius, int indicatorTrackGapSize) {
        if (!isMaterial3Style()) return;

        progressSpec.indicatorSize = indicatorSize;
        progressSpec.trackThickness = trackThickness;
        progressSpec.trackCornerRadius = trackCornerRadius;
        progressSpec.useRelativeTrackCornerRadius = true;
        progressSpec.trackCornerRadiusFraction = 0.5f;
        progressSpec.indicatorTrackGapSize = indicatorTrackGapSize;
    }

    @Keep
    public void setMiniSpecValues(int indicatorSize, int trackThickness, int trackCornerRadius, int indicatorTrackGapSize) {
        if (!isMaterial3Style()) return;

        miniProgressSpec.indicatorSize = indicatorSize;
        miniProgressSpec.trackThickness = trackThickness;
        miniProgressSpec.trackCornerRadius = trackCornerRadius;
        miniProgressSpec.useRelativeTrackCornerRadius = true;
        miniProgressSpec.trackCornerRadiusFraction = 0.5f;
        miniProgressSpec.indicatorTrackGapSize = indicatorTrackGapSize;
    }

    private void initMdcDrawables(Context context) {
        progressSpec = new CircularProgressIndicatorSpec(context, null);
        miniProgressSpec = new CircularProgressIndicatorSpec(context, null);

        progressSpec.hideAnimationBehavior = BaseProgressIndicator.HIDE_OUTWARD;
        progressSpec.showAnimationBehavior = BaseProgressIndicator.SHOW_INWARD;

        miniProgressSpec.hideAnimationBehavior = BaseProgressIndicator.HIDE_OUTWARD;
        miniProgressSpec.showAnimationBehavior = BaseProgressIndicator.SHOW_INWARD;

        setSpecValues(circleRadius * 2, dp(3), dp(2), dp(4));
        setMiniSpecValues(dp(24), dp(3), dp(2), dp(2));

        progressDrawable = DeterminateDrawable.createCircularDrawable(context, progressSpec);
        progressDrawable.setCallback(this);
        progressDrawable.setVisible(false, false, false);

        indeterminateDrawable = IndeterminateDrawable.createCircularDrawable(context, progressSpec);
        indeterminateDrawable.setCallback(this);
        indeterminateDrawable.setVisible(false, false, false);
        indeterminateDrawable.registerAnimationCallback(indeterminateHideCallback);

        miniProgressDrawable = DeterminateDrawable.createCircularDrawable(context, miniProgressSpec);
        miniProgressDrawable.setCallback(this);
        miniProgressDrawable.setVisible(false, false, false);

        miniIndeterminateDrawable = IndeterminateDrawable.createCircularDrawable(context, miniProgressSpec);
        miniIndeterminateDrawable.setCallback(this);
        miniIndeterminateDrawable.setVisible(false, false, false);
        miniIndeterminateDrawable.registerAnimationCallback(indeterminateHideCallback);

        updateM3Colors();
    }

    private void updateM3Colors() {
        if (!isMaterial3Style()) return;

        if (progressSpec != null) {
            int baseIconColor;
            int baseCircleColor;

            if (isPressed) {
                baseIconColor = iconPressedColorKey >= 0 ? getThemedColor(iconPressedColorKey) : iconPressedColor;
                baseCircleColor = circlePressedColorKey >= 0 ? getThemedColor(circlePressedColorKey) : circlePressedColor;
            } else {
                baseIconColor = iconColorKey >= 0 ? getThemedColor(iconColorKey) : iconColor;
                baseCircleColor = circleColorKey >= 0 ? getThemedColor(circleColorKey) : circleColor;
            }

            if (overlayImageView.hasBitmapImage()) {
                float alpha = overlayImageView.getCurrentAlpha();
                if (alpha >= 1.0f) {
                    baseIconColor = 0xffffffff;
                } else {
                    int r = Color.red(baseIconColor);
                    int g = Color.green(baseIconColor);
                    int b = Color.blue(baseIconColor);
                    int a = Color.alpha(baseIconColor);

                    int rD = (int) ((0xff - r) * alpha);
                    int gD = (int) ((0xff - g) * alpha);
                    int bD = (int) ((0xff - b) * alpha);
                    int aD = (int) ((0xff - a) * alpha);
                    baseIconColor = Color.argb(a + aD, r + rD, g + gD, b + bD);
                }
            }

            int m3IndicatorColor = isPressed && indicatorPressedColor != -1 ? indicatorPressedColor : (indicatorColor != -1 ? indicatorColor : baseIconColor);

            boolean drawCircle = !overlayImageView.hasBitmapImage() || !(overlayImageView.getCurrentAlpha() >= 1.0f);
            boolean shouldDrawBack = drawCircle && drawBackground && (!isMaterial3Style() || needDrawBackground);

            if (invertColors && !shouldDrawBack) {
                m3IndicatorColor = baseCircleColor;
            }

            int m3TrackColor = isPressed && trackPressedColor != -1 ? trackPressedColor : (trackColor != -1 ? trackColor : ColorUtils.setAlphaComponent(m3IndicatorColor, getTrackAlpha()));

            if (progressSpec.indicatorColors.length == 0 || progressSpec.indicatorColors[0] != m3IndicatorColor) {
                progressSpec.indicatorColors = new int[]{m3IndicatorColor};

                var filter = new PorterDuffColorFilter(m3IndicatorColor, PorterDuff.Mode.SRC_IN);
                if (indeterminateDrawable != null) {
                    indeterminateDrawable.setColorFilter(filter);
                }
                if (progressDrawable != null) {
                    progressDrawable.setColorFilter(filter);
                }
            }
            if (progressSpec.trackColor != m3TrackColor) {
                progressSpec.trackColor = m3TrackColor;
            }
        }

        if (miniProgressSpec != null) {
            int miniColor;
            if (isPressedMini && circleCrossfadeColorKey < 0) {
                miniColor = iconPressedColorKey >= 0 ? getThemedColor(iconPressedColorKey) : iconPressedColor;
            } else {
                miniColor = iconColorKey >= 0 ? getThemedColor(iconColorKey) : iconColor;
            }

            int m3MiniIndicatorColor = isPressedMini && indicatorPressedColor != -1 ? indicatorPressedColor : (indicatorColor != -1 ? indicatorColor : miniColor);
            int m3MiniTrackColor = isPressedMini && trackPressedColor != -1 ? trackPressedColor : (trackColor != -1 ? trackColor : ColorUtils.setAlphaComponent(m3MiniIndicatorColor, getTrackAlpha()));

            if (miniProgressSpec.indicatorColors.length == 0 || miniProgressSpec.indicatorColors[0] != m3MiniIndicatorColor) {
                miniProgressSpec.indicatorColors = new int[]{m3MiniIndicatorColor};
            }
            if (miniProgressSpec.trackColor != m3MiniTrackColor) {
                miniProgressSpec.trackColor = m3MiniTrackColor;
            }
        }
    }

    private int getTrackAlpha() {
        return (int) (255 * (Theme.isCurrentThemeDark() ? 0.325f : 0.275f));
    }

    @Keep
    public void setWavy(boolean isWavy) {
        if (style != STYLE_MATERIAL3_WAVY) return;

        if (isWavy) {
            miniProgressSpec.indicatorInset = 0;
            progressSpec.indicatorInset = 0;
            setWavyValues(dp(15), dp(1.6f), dp(5), 0.05f);
        } else {
            progressSpec.indicatorInset = dp(4);
            miniProgressSpec.indicatorInset = dp(4);
            setWavyValues(0, 0, 0, 1f);
        }
    }

    public void setWavyValues(int wavelength, int waveAmplitude, int waveSpeed, float waveAmplitudeRampProgressMin) {
        if (style != STYLE_MATERIAL3_WAVY) return;

        progressSpec.wavelengthDeterminate = wavelength;
        progressSpec.wavelengthIndeterminate = wavelength;
        progressSpec.waveAmplitude = this.waveAmplitude = waveAmplitude;
        progressSpec.waveSpeed = waveSpeed;
        progressSpec.waveAmplitudeRampProgressMin = waveAmplitudeRampProgressMin;

        /* don't use wavy for miniProgressSpec
        miniProgressSpec.wavelengthDeterminate = wavelength;
        miniProgressSpec.waveAmplitude = waveAmplitude;
        miniProgressSpec.waveSpeed = waveSpeed;
        miniProgressSpec.waveAmplitudeRampProgressMin = waveAmplitudeRampProgressMin;
        */

        invalidateParent();
    }

    public void setNeedDrawBackground(boolean value) {
        needDrawBackground = value;
    }

    @Keep
    public boolean isNeedDrawBackground() {
        return needDrawBackground;
    }

    public void setInvertColors(boolean value) {
        invertColors = value;
    }

    @Keep
    public boolean isInvertColors() {
        return invertColors;
    }

    public void setResourcesProvider(Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
    }

    @Keep
    public void setAsMini() {
        mediaActionDrawable.setMini(true);
    }

    @Keep
    public void setCircleRadius(int value) {
        circleRadius = value;
        overlayImageView.setRoundRadius(circleRadius);
        if (isMaterial3Style()) progressSpec.indicatorSize = circleRadius * 2;
    }

    public void setBackgroundStroke(int value) {
        backgroundStroke = value;
        circlePaint.setStrokeWidth(value);
        circlePaint.setStyle(Paint.Style.STROKE);
        invalidateParent();
    }

    public int getRadius() {
        return circleRadius;
    }

    public void setBackgroundDrawable(MessageDrawable drawable) {
        mediaActionDrawable.setBackgroundDrawable(drawable);
        miniMediaActionDrawable.setBackgroundDrawable(drawable);
    }

    @Keep
    public void setBackgroundGradientDrawable(LinearGradient drawable) {
        mediaActionDrawable.setBackgroundGradientDrawable(drawable);
        miniMediaActionDrawable.setBackgroundGradientDrawable(drawable);
    }

    public void setImageOverlay(TLRPC.PhotoSize image, TLRPC.Document document, Object parentObject) {
        final String filter = String.format(Locale.US, "%d_%d", circleRadius * 2, circleRadius * 2);
        overlayImageView.setImage(ImageLocation.getForDocument(image, document), filter, null, null, parentObject, 1);
    }

    public void setImageOverlay(TLRPC.PhotoSize image, TLRPC.PhotoSize thumb, TLRPC.Document document, Object parentObject) {
        final String filter = String.format(Locale.US, "%d_%d", circleRadius * 2, circleRadius * 2);
        overlayImageView.setImage(image == null ? null : ImageLocation.getForDocument(image, document), filter, thumb == null ? null : ImageLocation.getForDocument(thumb, document), filter, null, 0, null, parentObject, 1);
    }

    public void setImageOverlay(String url) {
        overlayImageView.setImage(url, url != null ? String.format(Locale.US, "%d_%d", circleRadius * 2, circleRadius * 2) : null, null, null, -1);
    }

    public void setImageOverlay(Bitmap bitmap) {
        overlayImageView.setImageBitmap(bitmap);
    }

    public void onAttachedToWindow() {
        overlayImageView.onAttachedToWindow();
    }

    public void onDetachedFromWindow() {
        stopMdcDrawable(progressDrawable, false);
        stopMdcDrawable(indeterminateDrawable, false);
        stopMdcDrawable(miniProgressDrawable, false);
        stopMdcDrawable(miniIndeterminateDrawable, false);
        overlayImageView.onDetachedFromWindow();
    }

    public void setColorKeys(int circle, int circlePressed, int icon, int iconPressed) {
        circleColorKey = circle;
        circlePressedColorKey = circlePressed;
        iconColorKey = icon;
        iconPressedColorKey = iconPressed;
    }

    @Keep
    public void setM3Colors(int indicator, int indicatorPressed, int track, int trackPressed) {
        indicatorColor = indicator;
        indicatorPressedColor = indicatorPressed;
        trackColor = track;
        trackPressedColor = trackPressed;
    }

    @Keep
    public void setM3Colors(int indicator, int track) {
        setM3Colors(indicator, indicator, track, track);
    }

    @Keep
    public void setColors(int circle, int circlePressed, int icon, int iconPressed) {
        circleColor = circle;
        circlePressedColor = circlePressed;
        iconColor = icon;
        iconPressedColor = iconPressed;
        circleColorKey = -1;
        circlePressedColorKey = -1;
        iconColorKey = -1;
        iconPressedColorKey = -1;
    }

    public void setCircleCrossfadeColor(int color, float progress, float checkProgress) {
        circleCrossfadeColorKey = color;
        circleCrossfadeColorProgress = progress;
        circleCheckProgress = checkProgress;
        miniIconScale = 1.0f;
        if (color >= 0) {
            initMiniIcons();
        }
    }

    public void setDrawBackground(boolean value) {
        drawBackground = value;
    }

    public void setProgressRect(int left, int top, int right, int bottom) {
        progressRect.set(left, top, right, bottom);
    }

    public void setProgressRect(float left, float top, float right, float bottom) {
        progressRect.set(left, top, right, bottom);
    }

    public RectF getProgressRect() {
        return progressRect;
    }

    public void setProgressColor(int color) {
        progressColor = color;
    }

    public void setMiniProgressBackgroundColor(int color) {
        miniProgressBackgroundPaint.setColor(color);
    }

    public void setProgress(float value, boolean animated) {
        setProgress(value, animated, false);
    }

    public void setProgress(float value, boolean animated, boolean force) {
        if (!force && isMaterial3Style() && value == 0f && getProgress() > 0f) return;
        if (!force && value == getProgress()) return;

        int level = (int) (value * 10000);
        if (drawMiniIcon) {
            miniMediaActionDrawable.setProgress(value, animated);
            if (isMaterial3Style()) {
                miniProgressDrawable.setLevel(level);
                updateM3Colors();
                validateVisibleDrawables(value, getMiniIcon(), miniProgressDrawable, miniIndeterminateDrawable, animated);
            }
            lastMiniProgressLevel = level;
        } else {
            mediaActionDrawable.setProgress(value, animated);
            if (isMaterial3Style()) {
                progressDrawable.setLevel(level);
                updateM3Colors();
                validateVisibleDrawables(value, getIcon(), progressDrawable, indeterminateDrawable, animated);
            }
            lastMainProgressLevel = level;
        }
    }

    private void validateVisibleDrawables(float progress, int currentIcon, DeterminateDrawable<CircularProgressIndicatorSpec> determinateDrawable, IndeterminateDrawable<CircularProgressIndicatorSpec> indeterminateDrawable, boolean animated) {
        boolean draw = progress < 1f && (iconIsCancel(currentIcon) || progress > changePercent && currentIcon == MediaActionDrawable.ICON_DOWNLOAD);
        if (draw) {
            if (progress <= changePercent) {
                if (!indeterminateDrawable.isVisible() && parent != null && parent.isAttachedToWindow())
                    indeterminateDrawable.setVisible(true, true, true);
                if (determinateDrawable.isVisible())
                    determinateDrawable.setVisible(false, false, animated);
            } else {
                if (indeterminateDrawable.isVisible())
                    indeterminateDrawable.setVisible(false, false, false);
                if (!determinateDrawable.isVisible())
                    determinateDrawable.setVisible(true, false, false);
            }
        } else {
            if (determinateDrawable.isVisible())
                determinateDrawable.setVisible(false, false, animated);
            if (indeterminateDrawable.isVisible())
                indeterminateDrawable.setVisible(false, false, animated);
        }
    }

    public float getProgress() {
        return drawMiniIcon ? miniMediaActionDrawable.getProgress() : mediaActionDrawable.getProgress();
    }

    private void invalidateParent() {
        int offset = dp(2);
        parent.invalidate((int) progressRect.left - offset, (int) progressRect.top - offset, (int) progressRect.right + offset * 2, (int) progressRect.bottom + offset * 2);
    }

    public int getIcon() {
        return mediaActionDrawable.getCurrentIcon();
    }

    public int getMiniIcon() {
        return miniMediaActionDrawable.getCurrentIcon();
    }

    @Keep
    public void setIcon(int icon, boolean ifSame, boolean animated) {
        boolean same = icon == mediaActionDrawable.getCurrentIcon();
        if (ifSame && same) {
            return;
        }

        if (isMaterial3Style() && mediaActionDrawable.getProgress() > 0.999f && iconIsCancel(icon))
            setProgress(0f, false, true);

        if (isMaterial3Style() && !same) {
            updateM3Colors();
            validateVisibleDrawables(icon, mediaActionDrawable.getCurrentIcon(), getProgress(), progressDrawable, indeterminateDrawable, animated);
        }

        mediaActionDrawable.setIcon(icon, animated);
        if (parent != null) {
            if (!animated) {
                parent.invalidate();
            } else {
                invalidateParent();
            }
        }
    }

    private void validateVisibleDrawables(int newIcon, int currentIcon, float progress, DeterminateDrawable<CircularProgressIndicatorSpec> determinateDrawable, IndeterminateDrawable<CircularProgressIndicatorSpec> indeterminateDrawable, boolean animated) {
        boolean draw = iconIsCancel(newIcon) || progress > changePercent && progress < 1f && iconIsCancel(currentIcon) && newIcon == MediaActionDrawable.ICON_DOWNLOAD;
        if (draw) {
            boolean indeterminateVisible = progress <= changePercent;
            if (determinateDrawable.isVisible() == indeterminateVisible)
                determinateDrawable.setVisible(!indeterminateVisible, false, false);
            if (indeterminateDrawable.isVisible() != indeterminateVisible && (!indeterminateVisible || (parent != null && parent.isAttachedToWindow())))
                indeterminateDrawable.setVisible(indeterminateVisible, true, indeterminateVisible);
        } else {
            if (determinateDrawable.isVisible())
                determinateDrawable.setVisible(false, false, animated);
            if (indeterminateDrawable.isVisible())
                indeterminateDrawable.setVisible(false, false, animated);
        }
    }

    public void setMiniIconScale(float scale) {
        miniIconScale = scale;
    }

    public void setMiniIcon(int icon, boolean ifSame, boolean animated) {
        if (icon != MediaActionDrawable.ICON_DOWNLOAD && icon != MediaActionDrawable.ICON_CANCEL && icon != MediaActionDrawable.ICON_NONE) {
            return;
        }

        boolean same = icon == miniMediaActionDrawable.getCurrentIcon();
        if (ifSame && same) {
            return;
        }
        if (drawMiniIcon && isMaterial3Style() && !same) {
            updateM3Colors();
            validateVisibleDrawables(icon, miniMediaActionDrawable.getCurrentIcon(), getProgress(), miniProgressDrawable, miniIndeterminateDrawable, animated);
        }
        miniMediaActionDrawable.setIcon(icon, animated);
        drawMiniIcon = icon != MediaActionDrawable.ICON_NONE || miniMediaActionDrawable.getTransitionProgress() < 1.0f;
        if (drawMiniIcon) {
            initMiniIcons();
        }
        if (!animated) {
            parent.invalidate();
        } else {
            invalidateParent();
        }
    }

    public void initMiniIcons() {
        if (miniDrawBitmap == null) {
            try {
                miniDrawBitmap = Bitmap.createBitmap(dp(48), dp(48), Bitmap.Config.ARGB_8888);
                miniDrawCanvas = new Canvas(miniDrawBitmap);
            } catch (Throwable ignore) {

            }
        }
    }

    public boolean swapIcon(int icon) {
        return mediaActionDrawable.setIcon(icon, false);
    }

    public void setPressed(boolean value, boolean mini) {
        if (mini) {
            isPressedMini = value;
        } else {
            isPressed = value;
        }
        updateM3Colors();
        invalidateParent();
    }

    public void setOverrideAlpha(float alpha) {
        overrideAlpha = alpha;
    }

    public float getOverrideAlpha() {
        return overrideAlpha;
    }

    public float getWholeAlpha() {
        final int currentIcon = mediaActionDrawable.getCurrentIcon();
        final int prevIcon = mediaActionDrawable.getPreviousIcon();

        if (backgroundStroke != 0) {
            if (currentIcon == MediaActionDrawable.ICON_CANCEL) {
                return 1.0f - mediaActionDrawable.getTransitionProgress();
            } else if (prevIcon == MediaActionDrawable.ICON_CANCEL) {
                return mediaActionDrawable.getTransitionProgress();
            } else {
                return 1.0f;
            }
        } else if ((currentIcon == MediaActionDrawable.ICON_CANCEL || currentIcon == MediaActionDrawable.ICON_CHECK || currentIcon == MediaActionDrawable.ICON_EMPTY || currentIcon == MediaActionDrawable.ICON_GIF || currentIcon == MediaActionDrawable.ICON_PLAY) && prevIcon == MediaActionDrawable.ICON_NONE) {
            return mediaActionDrawable.getTransitionProgress();
        } else {
            return currentIcon != MediaActionDrawable.ICON_NONE ? 1.0f : 1.0f - mediaActionDrawable.getTransitionProgress();
        }
    }

    @Keep
    public void draw(Canvas canvas) {
        if (mediaActionDrawable.getCurrentIcon() == MediaActionDrawable.ICON_NONE && mediaActionDrawable.getTransitionProgress() >= 1.0f || progressRect.isEmpty()) {
            return;
        }

        final boolean hasDrawScale = drawScale != 1f;
        if (hasDrawScale) {
            canvas.save();
            canvas.scale(drawScale, drawScale, progressRect.centerX(), progressRect.centerY());
        }

        int currentIcon = mediaActionDrawable.getCurrentIcon();
        final int prevIcon = mediaActionDrawable.getPreviousIcon();
        final float wholeAlpha = getWholeAlpha();

        if (isPressedMini && circleCrossfadeColorKey < 0) {
            if (iconPressedColorKey >= 0) {
                miniMediaActionDrawable.setColor(getThemedColor(iconPressedColorKey));
            } else {
                miniMediaActionDrawable.setColor(iconPressedColor);
            }
            if (circlePressedColorKey >= 0) {
                circleMiniPaint.setColor(getThemedColor(circlePressedColorKey));
            } else {
                circleMiniPaint.setColor(circlePressedColor);
            }
        } else {
            if (iconColorKey >= 0) {
                miniMediaActionDrawable.setColor(getThemedColor(iconColorKey));
            } else {
                miniMediaActionDrawable.setColor(iconColor);
            }
            if (circleColorKey >= 0) {
                if (circleCrossfadeColorKey >= 0) {
                    circleMiniPaint.setColor(AndroidUtilities.getOffsetColor(getThemedColor(circleColorKey), getThemedColor(circleCrossfadeColorKey), circleCrossfadeColorProgress, circleCheckProgress));
                } else {
                    circleMiniPaint.setColor(getThemedColor(circleColorKey));
                }
            } else {
                circleMiniPaint.setColor(circleColor);
            }
        }

        int color;
        if (isPressed) {
            if (iconPressedColorKey >= 0) {
                mediaActionDrawable.setColor(color = getThemedColor(iconPressedColorKey));
                mediaActionDrawable.setBackColor(getThemedColor(circlePressedColorKey));
            } else {
                mediaActionDrawable.setColor(color = iconPressedColor);
                mediaActionDrawable.setBackColor(circlePressedColor);
            }
            if (circlePressedColorKey >= 0) {
                circlePaint.setColor(getThemedColor(circlePressedColorKey));
            } else {
                circlePaint.setColor(circlePressedColor);
            }
        } else {
            if (iconColorKey >= 0) {
                mediaActionDrawable.setColor(color = getThemedColor(iconColorKey));
                mediaActionDrawable.setBackColor(getThemedColor(circleColorKey));
            } else {
                mediaActionDrawable.setColor(color = iconColor);
                mediaActionDrawable.setBackColor(circleColor);
            }
            if (circleColorKey >= 0) {
                circlePaint.setColor(getThemedColor(circleColorKey));
            } else {
                circlePaint.setColor(circleColor);
            }
        }

        if ((drawMiniIcon || circleCrossfadeColorKey >= 0) && miniDrawCanvas != null) {
            miniDrawBitmap.eraseColor(0);
        }

        int originalAlpha = circlePaint.getAlpha();
        circlePaint.setAlpha((int) (originalAlpha * wholeAlpha * overrideAlpha * overrideCircleAlpha));
        originalAlpha = circleMiniPaint.getAlpha();
        circleMiniPaint.setAlpha((int) (originalAlpha * wholeAlpha * overrideAlpha));

        boolean drawCircle = true;
        int centerX;
        int centerY;
        if ((drawMiniIcon || circleCrossfadeColorKey >= 0) && miniDrawCanvas != null) {
            centerX = (int) Math.ceil(progressRect.width() / 2);
            centerY = (int) Math.ceil(progressRect.height() / 2);
        } else {
            centerX = (int) progressRect.centerX();
            centerY = (int) progressRect.centerY();
        }

        if (overlayImageView.hasBitmapImage()) {
            float alpha = overlayImageView.getCurrentAlpha();
            overlayPaint.setAlpha((int) (0x64 * alpha * wholeAlpha * overrideAlpha));
            int c;
            if (alpha >= 1.0f) {
                drawCircle = false;
                c = 0xffffffff;
            } else {
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);
                int a = Color.alpha(color);

                int rD = (int) ((0xff - r) * alpha);
                int gD = (int) ((0xff - g) * alpha);
                int bD = (int) ((0xff - b) * alpha);
                int aD = (int) ((0xff - a) * alpha);
                c = Color.argb(a + aD, r + rD, g + gD, b + bD);
            }
            mediaActionDrawable.setColor(c);

            overlayImageView.setImageCoords(centerX - circleRadius, centerY - circleRadius, circleRadius * 2, circleRadius * 2);
        }

        int restore = Integer.MIN_VALUE;
        if (miniDrawCanvas != null && circleCrossfadeColorKey >= 0 && circleCheckProgress != 1.0f) {
            restore = miniDrawCanvas.save();
            float scaleMini = 1.0f - 0.1f * (1.0f - circleCheckProgress);
            miniDrawCanvas.scale(scaleMini, scaleMini, centerX, centerY);
        }

        boolean shouldDrawBack = drawCircle && drawBackground && (!isMaterial3Style() || needDrawBackground);

        if (shouldDrawBack) {
            if ((drawMiniIcon || circleCrossfadeColorKey >= 0) && miniDrawCanvas != null) {
                miniDrawCanvas.drawCircle(centerX, centerY, circleRadius, circlePaint);
            } else {
                if (currentIcon != MediaActionDrawable.ICON_NONE || wholeAlpha != 0) {
                    if (backgroundStroke != 0) {
                        canvas.drawCircle(centerX, centerY, (circleRadius - dp(3.5f)), circlePaint);
                    } else {
                        canvas.drawCircle(centerX, centerY, circleRadius, circlePaint);
                    }
                }
            }
        }

        if (overlayImageView.hasBitmapImage()) {
            overlayImageView.setAlpha(wholeAlpha * overrideAlpha * overlayImageAlpha);

            if ((drawMiniIcon || circleCrossfadeColorKey >= 0) && miniDrawCanvas != null) {
                overlayImageView.draw(miniDrawCanvas);
                miniDrawCanvas.drawCircle(centerX, centerY, circleRadius, overlayPaint);
            } else {
                overlayImageView.draw(canvas);
                canvas.drawCircle(centerX, centerY, circleRadius, overlayPaint);
            }
        }

        int iconSize = circleRadius;
        if (maxIconSize > 0 && iconSize > maxIconSize) {
            iconSize = maxIconSize;
        }
        if (iconScale != 1f) {
            canvas.save();
            canvas.scale(iconScale, iconScale, centerX, centerY);
        }

        mediaActionDrawable.setBounds(centerX - iconSize, centerY - iconSize, centerX + iconSize, centerY + iconSize);
        mediaActionDrawable.setHasOverlayImage(overlayImageView.hasBitmapImage());

        Canvas targetCanvas = ((drawMiniIcon || circleCrossfadeColorKey >= 0) && miniDrawCanvas != null) ? miniDrawCanvas : canvas;

        boolean useMaterial3 = ExteraConfig.getNewLoadingStyle() && isMaterial3Style();
        float progress = getProgress();
        int miniIcon = miniMediaActionDrawable.getCurrentIcon();
        boolean drawMaterial3Progress = useMaterial3 && ((!drawMiniIcon && iconIsCancel(currentIcon) || drawMiniIcon && iconIsCancel(miniIcon)) || (progress > changePercent && progress < 1f && (drawMiniIcon && miniIcon == MediaActionDrawable.ICON_DOWNLOAD || !drawMiniIcon && currentIcon == MediaActionDrawable.ICON_DOWNLOAD)));
        boolean isIndeterminate = !drawMiniIcon && indeterminateDrawable != null && indeterminateDrawable.isVisible() || drawMiniIcon && miniIndeterminateDrawable != null && miniIndeterminateDrawable.isVisible();

        if (drawMaterial3Progress && !drawMiniIcon) {
            updateM3Colors();

            if (style == STYLE_MATERIAL3_WAVY) {
                progressSpec.waveAmplitude = shouldDrawBack ? 0 : (int) (waveAmplitude * getWaveScale(currentIcon, prevIcon));
            }

            int mdcAlpha = (int) (255 * wholeAlpha * overrideAlpha);
            if (progress <= changePercent && !isIndeterminate && parent != null && parent.isAttachedToWindow()) {
                indeterminateDrawable.setVisible(true, true, true);
                isIndeterminate = true;
            }
            if (!isIndeterminate) {
                progressDrawable.setBounds(centerX - iconSize, centerY - iconSize, centerX + iconSize, centerY + iconSize);
                progressDrawable.setAlpha(mdcAlpha);
                progressDrawable.draw(targetCanvas);
            } else {
                indeterminateDrawable.setBounds(centerX - iconSize, centerY - iconSize, centerX + iconSize, centerY + iconSize);
                indeterminateDrawable.setAlpha(mdcAlpha);
                indeterminateDrawable.draw(targetCanvas);
            }
        }

        if ((drawMiniIcon || circleCrossfadeColorKey >= 0)) {
            if (miniDrawCanvas != null) {
                mediaActionDrawable.draw(miniDrawCanvas);
            } else {
                mediaActionDrawable.draw(canvas);
            }
        } else {
            mediaActionDrawable.setOverrideAlpha(overrideAlpha);
            mediaActionDrawable.draw(canvas);
        }

        if (restore != Integer.MIN_VALUE && miniDrawCanvas != null) {
            miniDrawCanvas.restoreToCount(restore);
        }

        if ((drawMiniIcon || circleCrossfadeColorKey >= 0)) {
            int offset;
            int size;
            float cx;
            float cy;
            if (Math.abs(progressRect.width() - dp(44)) < AndroidUtilities.density) {
                offset = 0;
                size = 20;
                cx = progressRect.centerX() + dp(16 + offset);
                cy = progressRect.centerY() + dp(16 + offset);
            } else {
                offset = 2;
                size = 22;
                cx = progressRect.centerX() + dp(18);
                cy = progressRect.centerY() + dp(18);
            }
            int halfSize = size / 2;

            float alpha;
            if (drawMiniIcon) {
                alpha = miniMediaActionDrawable.getCurrentIcon() != MediaActionDrawable.ICON_NONE ? 1.0f : 1.0f - miniMediaActionDrawable.getTransitionProgress();
                if (alpha == 0.0f) {
                    drawMiniIcon = false;
                }
            } else {
                alpha = 1.0f;
            }

            if (miniDrawCanvas != null) {
                miniDrawCanvas.drawCircle(dp(18 + size + offset), dp(18 + size + offset), dp(halfSize + 1) * alpha * miniIconScale, Theme.checkboxSquare_eraserPaint);
            } else {
                miniProgressBackgroundPaint.setColor(progressColor);
                canvas.drawCircle(cx, cy, dp(12), miniProgressBackgroundPaint);
            }

            if (miniDrawCanvas != null) {
                canvas.drawBitmap(miniDrawBitmap, (int) progressRect.left, (int) progressRect.top, null);
            }

            restore = Integer.MIN_VALUE;
            if (miniIconScale < 1.0f) {
                restore = canvas.save();
                canvas.scale(miniIconScale, miniIconScale, cx, cy);
            }

            canvas.drawCircle(cx, cy, dp(halfSize) * alpha + dp(1) * (1.0f - circleCheckProgress), circleMiniPaint);

            if (drawMiniIcon) {
                miniMediaActionDrawable.setBounds((int) (cx - dp(halfSize) * alpha), (int) (cy - dp(halfSize) * alpha), (int) (cx + dp(halfSize) * alpha), (int) (cy + dp(halfSize) * alpha));

                if (drawMaterial3Progress) {
                    updateM3Colors();

                    int ringHalf = dp(12);
                    if (progress <= changePercent) {
                        miniIndeterminateDrawable.setBounds((int) (cx - ringHalf * alpha), (int) (cy - ringHalf * alpha), (int) (cx + ringHalf * alpha), (int) (cy + ringHalf * alpha));
                        miniIndeterminateDrawable.setAlpha((int) (255 * alpha));
                        miniIndeterminateDrawable.draw(canvas);
                    } else {
                        miniProgressDrawable.setBounds((int) (cx - ringHalf * alpha), (int) (cy - ringHalf * alpha), (int) (cx + ringHalf * alpha), (int) (cy + ringHalf * alpha));
                        miniProgressDrawable.setAlpha((int) (255 * alpha));
                        miniProgressDrawable.draw(canvas);
                    }
                }

                miniMediaActionDrawable.draw(canvas);
            }
            if (restore != Integer.MIN_VALUE) {
                canvas.restoreToCount(restore);
            }
        }
        if (iconScale != 1f) {
            canvas.restore();
        }
        if (hasDrawScale) {
            canvas.restore();
        }
    }

    private static boolean iconIsCancel(int icon) {
        return icon == MediaActionDrawable.ICON_CANCEL ||
                icon == MediaActionDrawable.ICON_CANCEL_FILL ||
                icon == MediaActionDrawable.ICON_CANCEL_NOPROFRESS ||
                icon == MediaActionDrawable.ICON_CANCEL_PERCENT;
    }

    private float getWaveScale(int currentIcon, int prevIcon) {
        float currentWaveTarget = iconIsCancel(currentIcon) ? 1f : 0f;
        float prevWaveTarget = iconIsCancel(prevIcon) ? 1f : 0f;

        return prevWaveTarget + (currentWaveTarget - prevWaveTarget) * mediaActionDrawable.getTransitionProgress();
    }

    public int getCircleColorKey() {
        return circleColorKey;
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public void setMaxIconSize(int maxSize) {
        this.maxIconSize = maxSize;
    }

    public void setOverlayImageAlpha(float overlayImageAlpha) {
        this.overlayImageAlpha = overlayImageAlpha;
    }

    public float getTransitionProgress() {
        return drawMiniIcon ? miniMediaActionDrawable.getTransitionProgress() : mediaActionDrawable.getTransitionProgress();
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable drawable) {
        invalidateParent();
    }

    @Override
    public void scheduleDrawable(@NonNull Drawable drawable, @NonNull Runnable runnable, long l) {
        if (parent != null) {
            parent.scheduleDrawable(drawable, runnable, l);
        }
    }

    @Override
    public void unscheduleDrawable(@NonNull Drawable drawable, @NonNull Runnable runnable) {
        if (parent != null) {
            parent.unscheduleDrawable(drawable, runnable);
        }
    }

    private static void stopMdcDrawable(Drawable drawable, boolean clearCallback) {
        if (drawable == null) {
            return;
        }
        if (drawable instanceof IndeterminateDrawable) {
            ((IndeterminateDrawable<?>) drawable).stop();
            cancelIndeterminateAnimator(drawable);
        } else if (drawable instanceof Animatable) {
            ((Animatable) drawable).stop();
        } else {
            drawable.setVisible(false, false);
        }
        if (clearCallback) {
            drawable.setCallback(null);
        }
    }

    private static void cancelIndeterminateAnimator(Drawable drawable) {
        IndeterminateDrawable<?> indeterminate = (IndeterminateDrawable<?>) drawable;
        if (indeterminate.isVisible() || indeterminate.isRunning()) {
            return;
        }
        indeterminate.setVisible(true, false, false);
        indeterminate.setVisible(false, false, false);
    }
}