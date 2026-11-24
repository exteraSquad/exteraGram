/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.appicons.ui.components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.appicons.AppIcon;
import com.exteragram.messenger.appicons.AppIconPreviewLoader;
import com.exteragram.messenger.preferences.utils.IconShapeHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

import java.util.HashMap;
import java.util.Map;

public class AppIconPreviewView extends View implements AppIconPreviewLoader.Callback {

    private static final Map<String, Path> shapeCache = new HashMap<>();

    private static final float ICON_INSET_DP = 5f;
    private static final float FADE_DURATION = 220f;

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint underlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path iconPath = new Path();
    private final Rect iconBounds = new Rect();
    private final Matrix shaderMatrix = new Matrix();

    private final Theme.ResourcesProvider resourcesProvider;

    private AppIcon icon;
    private Bitmap bitmap;
    private BitmapShader bitmapShader;
    private boolean bitmapIsExact;
    private Bitmap underlayBitmap;
    private BitmapShader underlayShader;
    private boolean crossfade;
    private int requestedSize;
    private float bitmapAlpha;
    private float scale = 1f;

    private Runnable previewReadyListener;

    public AppIconPreviewView(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
    }

    public void setOnPreviewReady(Runnable listener) {
        previewReadyListener = listener;
    }

    public void setCrossfade(boolean crossfade) {
        this.crossfade = crossfade;
    }

    public void setIcon(AppIcon icon) {
        if (this.icon == icon) {
            return;
        }
        this.icon = icon;
        if (crossfade && bitmap != null && bitmapAlpha >= 1f) {
            underlayBitmap = bitmap;
            underlayShader = null;
        }
        bitmap = null;
        bitmapShader = null;
        bitmapIsExact = false;
        bitmapAlpha = 0f;
        requestPreview();
        invalidate();
    }

    public void setPreviewScale(float scale) {
        if (this.scale == scale) {
            return;
        }
        this.scale = scale;
        invalidate();
    }

    private void requestPreview() {
        if (icon == null || getWidth() <= 0) {
            return;
        }
        int size = getWidth() - dp(ICON_INSET_DP) * 2;
        if (size <= 0) {
            return;
        }
        requestedSize = size;
        Bitmap cached = AppIconPreviewLoader.getCached(icon, size);
        if (cached != null) {
            setContent(cached, true);
            if (previewReadyListener != null) {
                previewReadyListener.run();
            }
            return;
        }
        Bitmap standIn = AppIconPreviewLoader.getAnyCached(icon);
        if (standIn != null) {
            setContent(standIn, false);
        }
        AppIconPreviewLoader.load(icon, size, this);
    }

    private void setContent(Bitmap value, boolean exact) {
        if (bitmap == value) {
            bitmapIsExact |= exact;
            return;
        }
        final boolean sharpening = bitmap != null && !bitmapIsExact && exact;
        bitmap = value;
        bitmapShader = null;
        bitmapIsExact = exact;
        if (!sharpening) {
            bitmapAlpha = !crossfade && exact && underlayBitmap == null ? 1f : 0f;
        }
    }

    @Override
    public void onPreviewReady(AppIcon icon, int size, Bitmap bitmap) {
        if (this.icon != icon || requestedSize != size) {
            return;
        }
        setContent(bitmap, true);
        invalidate();
        if (previewReadyListener != null) {
            previewReadyListener.run();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updatePaths();
        requestPreview();
    }

    private void updatePaths() {
        iconPath.rewind();
        if (getWidth() <= 0) {
            return;
        }

        float viewDp = getWidth() / AndroidUtilities.density;
        float iconDp = viewDp - ICON_INSET_DP * 2;
        if (iconDp <= 0) {
            return;
        }

        Matrix matrix = new Matrix();
        matrix.setTranslate(dp(ICON_INSET_DP), dp(ICON_INSET_DP));
        shape(iconDp).transform(matrix, iconPath);

        iconBounds.set(dp(ICON_INSET_DP), dp(ICON_INSET_DP), getWidth() - dp(ICON_INSET_DP), getHeight() - dp(ICON_INSET_DP));
    }

    private Path shape(float sizeDp) {
        String key = sizeDp + "_" + AndroidUtilities.density + "_" + ExteraConfig.getUseSystemIconShape();
        Path path = shapeCache.get(key);
        if (path == null) {
            path = IconShapeHelper.INSTANCE.getFinalIconShapePath(sizeDp, sizeDp, sizeDp * 0.28f);
            shapeCache.put(key, path);
        }
        return path;
    }

    private void drawBitmap(Canvas canvas, Bitmap bitmap, BitmapShader shader, Paint paint, int alpha) {
        shaderMatrix.setTranslate(iconBounds.left, iconBounds.top);
        shaderMatrix.preScale(iconBounds.width() / (float) bitmap.getWidth(), iconBounds.height() / (float) bitmap.getHeight());
        shader.setLocalMatrix(shaderMatrix);
        paint.setAlpha(alpha);
        canvas.drawPath(iconPath, paint);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (iconPath.isEmpty()) {
            return;
        }

        final boolean scaled = scale != 1f;
        if (scaled) {
            canvas.save();
            canvas.scale(scale, scale, getWidth() / 2f, getHeight() / 2f);
        }

        if (bitmap != null && bitmapAlpha < 1f) {
            bitmapAlpha = Math.min(1f, bitmapAlpha + 16f / FADE_DURATION);
            invalidate();
        }

        if (underlayBitmap != null && bitmapAlpha < 1f) {
            if (underlayShader == null) {
                underlayShader = new BitmapShader(underlayBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                underlayPaint.setShader(underlayShader);
            }
            drawBitmap(canvas, underlayBitmap, underlayShader, underlayPaint, 255);
        }

        if (bitmap != null) {
            if (bitmapShader == null) {
                bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                bitmapPaint.setShader(bitmapShader);
            }
            drawBitmap(canvas, bitmap, bitmapShader, bitmapPaint, (int) (255 * bitmapAlpha));
            if (bitmapAlpha >= 1f) {
                underlayBitmap = null;
                underlayShader = null;
            }
        } else if (underlayBitmap == null) {
            int from = ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_switchTrack, resourcesProvider), 0x38);
            int to = ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_switchTrack, resourcesProvider), 0x72);
            float phase = (SystemClock.uptimeMillis() % 900L) / 900f;
            float pulse = 0.5f + 0.5f * (float) Math.sin(phase * Math.PI * 2f);
            placeholderPaint.setColor(ColorUtils.blendARGB(from, to, pulse));
            canvas.drawPath(iconPath, placeholderPaint);
            postInvalidateOnAnimation();
        }

        if (scaled) {
            canvas.restore();
        }
    }
}
