package org.telegram.ui.Components.Premium;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.ui.Components.Premium.PremiumAppIconsPreviewView.ICONS_ROUND_RADIUS;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.preferences.utils.IconShapeHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

import java.util.HashMap;
import java.util.Map;

public class AdaptiveIconImageView extends ImageView {
    private static final Map<String, Path> shapeCache = new HashMap<>();
    private static final Map<Integer, Drawable.ConstantState> drawableStateCache = new HashMap<>();

    private Drawable foreground;
    private int boundBackgroundRes;
    private int boundForegroundRes;
    private int requestId;
    private boolean loading;
    private final Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Path iconPath = new Path();
    Path outlinePath = new Path();
    private int outerPadding = dp(5);
    private int backgroundOuterPadding = dp(42);

    public AdaptiveIconImageView(Context context) {
        super(context);
        placeholderPaint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_switchTrack), 0x55));
    }

    public void bindIconResources(int backgroundRes, int foregroundRes) {
        if (boundBackgroundRes == backgroundRes && boundForegroundRes == foregroundRes && !loading) {
            return;
        }
        boundBackgroundRes = backgroundRes;
        boundForegroundRes = foregroundRes;
        final int localRequestId = ++requestId;

        Drawable cachedBackground = getCachedDrawable(backgroundRes);
        Drawable cachedForeground = getCachedDrawable(foregroundRes);
        if (cachedBackground != null && cachedForeground != null) {
            loading = false;
            animate().cancel();
            setAlpha(1f);
            setImageDrawable(cachedBackground);
            foreground = cachedForeground;
            invalidate();
            return;
        }

        loading = true;
        setImageDrawable(null);
        foreground = null;
        animate().cancel();
        setAlpha(1f);
        invalidate();

        Utilities.globalQueue.postRunnable(() -> {
            Drawable background = loadDrawable(backgroundRes);
            Drawable foregroundDrawable = loadDrawable(foregroundRes);
            AndroidUtilities.runOnUIThread(() -> {
                if (localRequestId != requestId) {
                    return;
                }
                setAlpha(0f);
                setImageDrawable(background);
                foreground = foregroundDrawable;
                loading = false;
                animate().alpha(1f).setDuration(160).start();
                invalidate();
            });
        });
    }

    private Drawable loadDrawable(int resId) {
        Drawable.ConstantState state;
        synchronized (drawableStateCache) {
            state = drawableStateCache.get(resId);
        }
        if (state != null) {
            return state.newDrawable(ApplicationLoader.applicationContext.getResources()).mutate();
        }

        Drawable drawable = ContextCompat.getDrawable(ApplicationLoader.applicationContext, resId);
        if (drawable == null) {
            return null;
        }
        Drawable.ConstantState constantState = drawable.getConstantState();
        if (constantState != null) {
            synchronized (drawableStateCache) {
                drawableStateCache.put(resId, constantState);
            }
            return constantState.newDrawable(ApplicationLoader.applicationContext.getResources()).mutate();
        }
        return drawable;
    }

    private Drawable getCachedDrawable(int resId) {
        Drawable.ConstantState state;
        synchronized (drawableStateCache) {
            state = drawableStateCache.get(resId);
        }
        if (state == null) {
            return null;
        }
        return state.newDrawable(ApplicationLoader.applicationContext.getResources()).mutate();
    }

    public void setForeground(int resId) {
        foreground = loadDrawable(resId);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updatePath();
    }

    public void setPadding(int padding) {
        setPadding(padding, padding, padding, padding);
    }

    public void setOuterPadding(int outerPadding) {
        this.outerPadding = outerPadding;
    }

    public void setBackgroundOuterPadding(int backgroundOuterPadding) {
        this.backgroundOuterPadding = backgroundOuterPadding;
    }

    @Override
    public void draw(Canvas canvas) {
        if (!iconPath.isEmpty()) {
            canvas.clipPath(iconPath);
        }
        canvas.save();
        canvas.scale(1f + backgroundOuterPadding / (float) getWidth(), 1f + backgroundOuterPadding / (float) getHeight(), getWidth() / 2f, getHeight() / 2f);
        super.draw(canvas);
        canvas.restore();

        if (loading && !iconPath.isEmpty()) {
            int from = ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_switchTrack), 0x38);
            int to = ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_switchTrack), 0x72);
            float phase = (SystemClock.uptimeMillis() % 900L) / 900f;
            float pulse = 0.5f + 0.5f * (float) Math.sin(phase * Math.PI * 2f);
            placeholderPaint.setColor(ColorUtils.blendARGB(from, to, pulse));
            canvas.drawPath(iconPath, placeholderPaint);
            postInvalidateOnAnimation();
        }

        if (foreground != null) {
            foreground.setBounds(-outerPadding, -outerPadding, getWidth() + outerPadding, getHeight() + outerPadding);
            foreground.draw(canvas);
        }
    }

    private void updatePath() {
        iconPath.rewind();
        outlinePath.rewind();

        float viewWidthDp = getWidth() / AndroidUtilities.density;
        if (viewWidthDp <= 0) {
            return;
        }
        float iconPaddingDp = getPaddingLeft() / AndroidUtilities.density;
        float iconSizeDp = viewWidthDp - iconPaddingDp * 2;

        float outlinePaddingDp = 2f;
        float outlineSizeDp = viewWidthDp - outlinePaddingDp * 2;

        float iconCornerRadius = ICONS_ROUND_RADIUS * (iconSizeDp / viewWidthDp);
        float outlineCornerRadius = ICONS_ROUND_RADIUS * (outlineSizeDp / viewWidthDp);

        String iconKey = "i_" + iconSizeDp + "_" + iconCornerRadius;
        Path p_icon = shapeCache.get(iconKey);
        if (p_icon == null) {
            p_icon = IconShapeHelper.INSTANCE.getFinalIconShapePath(iconSizeDp, iconSizeDp, iconCornerRadius);
            shapeCache.put(iconKey, p_icon);
        }

        String outlineKey = "o_" + outlineSizeDp + "_" + outlineCornerRadius;
        Path p_outline = shapeCache.get(outlineKey);
        if (p_outline == null) {
            p_outline = IconShapeHelper.INSTANCE.getFinalIconShapePath(outlineSizeDp, outlineSizeDp, outlineCornerRadius);
            shapeCache.put(outlineKey, p_outline);
        }

        Matrix m = new Matrix();

        m.setTranslate(getPaddingLeft(), getPaddingTop());
        p_icon.transform(m, iconPath);

        m.setTranslate(dp(outlinePaddingDp), dp(outlinePaddingDp));
        p_outline.transform(m, outlinePath);
    }
}
