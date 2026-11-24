package org.telegram.ui.Components.blur3;

import static com.exteragram.messenger.debug.DebugConfig.getDisableChatFadeWallpaperBlend;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.utils.DrawableUtils;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.utils.glass.source.GlassSource;
import org.telegram.utils.glass.source.GlassSourceBitmap;
import org.telegram.utils.glass.source.GlassSourceColor;
import org.telegram.utils.glass.source.GlassSourceRenderNode;

public class BlurredBackgroundWithFadeDrawable extends Drawable implements Theme.Colorable {
    private final Paint maskFadeGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final BlurredBackgroundDrawable drawable;

    private final Matrix matrix = new Matrix();
    private Shader shader;

    private final Matrix matrixTmp = new Matrix();

    private Shader gradientShader;
    private Shader overrideFadeShader;
    private Shader overrideMaskShader;
    private Shader overrideComposeShader;
    private BitmapShader bitmapShader;
    private Shader composeShader;
    private final Matrix bitmapMatrix = new Matrix();
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overrideFadePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private Bitmap lastBitmap;
    private int overrideFadeColor;
    private boolean overrideFadeEnabled;
    private int lastOverrideFadeColor;
    private int lastOverrideFadeHeight;

    private static final float OVERRIDE_FADE_BLUR_TINT = 0.8f;
    private static final float OVERRIDE_FADE_BLUR_MASK = 0.75f;

    private int fadeHeight;
    private boolean opacity;

    private final Drawable.Callback callback = new Callback() {
        @Override
        public void invalidateDrawable(@NonNull Drawable who) {
            BlurredBackgroundWithFadeDrawable.this.invalidateSelf();
        }

        @Override
        public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
            BlurredBackgroundWithFadeDrawable.this.scheduleSelf(what, when);
        }

        @Override
        public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
            BlurredBackgroundWithFadeDrawable.this.unscheduleSelf(what);
        }
    };

    public BlurredBackgroundWithFadeDrawable(BlurredBackgroundDrawable drawable) {
        this.drawable = drawable;
        maskFadeGradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        bitmapPaint.setFilterBitmap(true);
        setFadeHeight(dp(40), false);
        drawable.setCallback(callback);
    }

    public void setFadeHeight(int fadeHeight, boolean opacity) {
        if (this.fadeHeight == fadeHeight && this.opacity == opacity) {
            return;
        }

        this.fadeHeight = fadeHeight;
        this.opacity = opacity;
        maskFadeGradientPaint.setShader(shader = createGradient(Color.BLACK, opacity));
        colorStaticPaint.setShader(null);

        matrix.reset();
        matrix.setScale(1, fadeHeight);
        if (fadeHeight < 0) {
            matrix.postTranslate(0, -fadeHeight);
        }
        shader.setLocalMatrix(matrix);
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        drawable.setBounds(bounds);
    }

    private final Paint colorStaticPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int colorStaticLast;
    private boolean ignoreFastWay;

    public void setIgnoreFastWay(boolean ignoreFastWay) {
        this.ignoreFastWay = ignoreFastWay;
    }

    public void setOverrideFadeColor(int color) {
        if (overrideFadeColor != color) {
            overrideFadeColor = color;
            overrideFadeShader = null;
        }
        overrideFadeEnabled = true;
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect bounds = getBounds();
        if (bounds.isEmpty() || alpha == 0) {
            return;
        }
        if (overrideFadeEnabled) {
            drawOverrideFade(canvas, bounds);
            return;
        }

        GlassSource source = drawable.getUnwrappedSource();
        if (!ignoreFastWay && source instanceof GlassSourceColor) {
            // fast way - just draw gradient

            final int color = ((GlassSourceColor) source).getColor();
            if (colorStaticLast != color ||gradientShader == null) {
                gradientShader = createGradient(color, opacity);
                colorStaticLast = color;
                colorStaticPaint.setShader(gradientShader);
            }

            int offset = 0;
            if (fadeHeight < 0) {
                offset = bounds.height() + fadeHeight;
            }

            matrixTmp.set(matrix);
            matrixTmp.postTranslate(bounds.left, bounds.top + offset);
            gradientShader.setLocalMatrix(matrixTmp);

            colorStaticPaint.setAlpha(alpha);
            canvas.drawRect(bounds, colorStaticPaint);
            return;
        }

        if (!ignoreFastWay && source instanceof GlassSourceBitmap && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // fast way - just draw gradient

            final GlassSourceBitmap s = (GlassSourceBitmap) source;
            final Bitmap bitmap = s.getBitmap();
            if (bitmap == null) {
                return;
            }

            final int color = Color.BLACK;
            boolean changed = false;
            if (colorStaticLast != color || gradientShader == null) {
                gradientShader = createGradient(color, opacity);
                colorStaticLast = color;
                changed = true;
            }

            if (bitmapShader == null || lastBitmap != bitmap) {
                lastBitmap = bitmap;
                bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bitmapShader.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
                }
                changed = true;
            }

            if (changed || composeShader == null) {
                composeShader = new ComposeShader(bitmapShader, gradientShader, PorterDuff.Mode.DST_IN);
                bitmapPaint.setShader(composeShader);
            }

            int offset = 0;
            if (fadeHeight < 0) {
                offset = bounds.height() + fadeHeight;
            }

            matrixTmp.set(matrix);
            matrixTmp.postTranslate(bounds.left, bounds.top + offset);
            gradientShader.setLocalMatrix(matrixTmp);

            bitmapMatrix.set(s.getMatrix());
            bitmapMatrix.postTranslate(-drawable.getSourceOffsetX(), -drawable.getSourceOffsetY());
            bitmapShader.setLocalMatrix(bitmapMatrix);

            bitmapPaint.setAlpha(alpha);
            canvas.drawRect(bounds, bitmapPaint);
            return;
        }

        //if (source instanceof GlassSourceRenderNode) {
        //    return;
        //}
        final int save = canvas.saveLayerAlpha(bounds.left, bounds.top, bounds.right, bounds.bottom, alpha);
        int offset = 0;
        if (fadeHeight < 0) {
            offset = bounds.height() + fadeHeight;
        }

        drawable.draw(canvas);
        canvas.translate(bounds.left, bounds.top + offset);
        canvas.drawRect(0, - offset, bounds.width(), bounds.height() - offset, maskFadeGradientPaint);
        canvas.restoreToCount(save);
    }

    private void drawOverrideFade(@NonNull Canvas canvas, Rect bounds) {
        final int overrideFadeHeight = Math.max(1, bounds.height());
        if (lastOverrideFadeColor != overrideFadeColor || lastOverrideFadeHeight != overrideFadeHeight || overrideFadeShader == null) {
            lastOverrideFadeColor = overrideFadeColor;
            lastOverrideFadeHeight = overrideFadeHeight;
            overrideFadeShader = createOverrideFadeGradient(overrideFadeColor, overrideFadeHeight, 1f);
            overrideMaskShader = createOverrideFadeGradient(Color.BLACK, overrideFadeHeight, OVERRIDE_FADE_BLUR_MASK);
            overrideComposeShader = null;
            overrideFadePaint.setShader(overrideFadeShader);
        }

        matrixTmp.reset();
        matrixTmp.postTranslate(bounds.left, bounds.top);

        final GlassSource source = drawable.getUnwrappedSource();
        if (!ignoreFastWay && !getDisableChatFadeWallpaperBlend() && source instanceof GlassSourceBitmap s && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            final Bitmap bitmap = s.getBitmap();
            if (bitmap != null) {
                if (bitmapShader == null || lastBitmap != bitmap) {
                    lastBitmap = bitmap;
                    bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        bitmapShader.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
                    }
                    overrideComposeShader = null;
                }
                if (overrideComposeShader == null) {
                    overrideComposeShader = new ComposeShader(bitmapShader, overrideMaskShader, PorterDuff.Mode.DST_IN);
                    bitmapPaint.setShader(overrideComposeShader);
                }

                overrideMaskShader.setLocalMatrix(matrixTmp);

                bitmapMatrix.set(s.getMatrix());
                bitmapMatrix.postTranslate(-drawable.getSourceOffsetX(), -drawable.getSourceOffsetY());
                bitmapShader.setLocalMatrix(bitmapMatrix);

                bitmapPaint.setAlpha(alpha);
                canvas.drawRect(bounds, bitmapPaint);

                overrideFadeShader.setLocalMatrix(matrixTmp);
                overrideFadePaint.setAlpha(Math.round(alpha * OVERRIDE_FADE_BLUR_TINT));
                canvas.drawRect(bounds, overrideFadePaint);
                return;
            }
        }

        overrideFadeShader.setLocalMatrix(matrixTmp);
        overrideFadePaint.setAlpha(alpha);
        canvas.drawRect(bounds, overrideFadePaint);
    }

    private int alpha = 255;

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        //
    }

    @Override
    public int getOpacity() {
        return PixelFormat.UNKNOWN;
    }

    private static LinearGradient createGradient(int color, boolean opacity) {
        final int alpha = Color.alpha(color);

        if (opacity) {
            return new LinearGradient(0, 0, 0, 1, new int[]{
                ColorUtils.setAlphaComponent(color, 0),
                ColorUtils.setAlphaComponent(color, 0x60 * alpha / 285),
                ColorUtils.setAlphaComponent(color, 0xB0 * alpha / 285),
                ColorUtils.setAlphaComponent(color, 0xE8 * alpha / 285),
            }, null, Shader.TileMode.CLAMP);
        }

        return new LinearGradient(0, 0, 0, 1, new int[]{
            ColorUtils.setAlphaComponent(color, 0),
            ColorUtils.setAlphaComponent(color, 0x60 * alpha / 255),
            ColorUtils.setAlphaComponent(color, 0xB0 * alpha / 255),
            ColorUtils.setAlphaComponent(color, 0xE8 * alpha / 255),
            ColorUtils.setAlphaComponent(color, 0xFF * alpha / 255),
        }, null, Shader.TileMode.CLAMP);
    }

    private static LinearGradient createOverrideFadeGradient(int color, int height, float alphaScale) {
        boolean isDark = Theme.isCurrentThemeDark();

        final float[] alphas = {
                isDark ? 0.95f : 1.0f,
                isDark ? 0.90f : 0.93f,
                isDark ? 0.78f : 0.83f,
                0.62f,
                0.40f,
                0.18f,
                0.05f,
                0f,
        };

        final int[] colors = new int[alphas.length];
        for (int i = 0; i < alphas.length; i++) {
            colors[i] = ColorUtils.setAlphaComponent(color, Math.round(255 * alphas[i] * alphaScale));
        }

        return new LinearGradient(
                0, 0,
                0, Math.max(1, height),
                colors,
                new float[]{
                        0.00f,
                        0.50f,
                        0.65f,
                        0.75f,
                        0.84f,
                        0.92f,
                        0.97f,
                        1.00f
                },
                Shader.TileMode.CLAMP
        );
    }




    public static void fillAlphaGradientColors(
        int[] outColors,
        int baseColor,
        int alphaStart,
        int alphaEnd,
        Interpolator interpolator
    ) {
        if (outColors == null || outColors.length == 0) {
            return;
        }

        final int count = outColors.length;
        if (count == 1) {
            outColors[0] = ColorUtils.setAlphaComponent(baseColor, (alphaStart + alphaEnd) / 2);
            return;
        }

        for (int i = 0; i < count; i++) {
            final float t = (float) i / (count - 1);
            final float it = interpolator != null ? interpolator.getInterpolation(t) : t;

            final int alpha = MathUtils.clamp(lerp(alphaStart, alphaEnd, it), 0, 255);

            outColors[i] = ColorUtils.setAlphaComponent(baseColor, alpha);
        }
    }

    @Override
    public void updateColors() {
        try {
            drawable.updateColors();
            invalidateSelf();
    
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
