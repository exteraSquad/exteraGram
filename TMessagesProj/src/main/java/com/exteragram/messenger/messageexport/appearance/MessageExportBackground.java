/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.messageexport.appearance;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.EmojiThemes;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatBackgroundDrawable;
import org.telegram.ui.Components.MotionBackgroundDrawable;

public final class MessageExportBackground {

    private final Drawable drawable;
    private final Bitmap bitmap;
    private final int color;
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix();
    private Runnable onUpdate;

    private MessageExportBackground(Drawable drawable, Bitmap bitmap, int color) {
        this.drawable = drawable;
        this.bitmap = bitmap;
        this.color = color | 0xff000000;
    }

    static MessageExportBackground fromChat(Drawable source, Theme.ResourcesProvider provider) {
        if (source instanceof ChatBackgroundDrawable) {
            source = ((ChatBackgroundDrawable) source).getDrawable(false);
        }
        if (source instanceof MotionBackgroundDrawable motion) {
            int[] colors = motion.getColors();
            MotionBackgroundDrawable copy = new MotionBackgroundDrawable(colors[0], colors[1], colors[2], colors[3], false);
            copy.setPhase(motion.getPhase());
            Bitmap pattern = motion.getPatternBitmap();
            if (pattern != null && !pattern.isRecycled()) {
                copy.setPatternBitmap(motion.getIntensity(), pattern);
                copy.setPatternColorFilter(copy.getPatternColor());
            }
            return new MessageExportBackground(copy, null, colors[0]);
        }
        if (source instanceof ColorDrawable) {
            return new MessageExportBackground(null, null, ((ColorDrawable) source).getColor());
        }
        if (source instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) source).getBitmap();
            if (bitmap != null && !bitmap.isRecycled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.getConfig() == Bitmap.Config.HARDWARE) {
                bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
            }
            if (bitmap != null && !bitmap.isRecycled()) {
                return new MessageExportBackground(null, bitmap, 0xff000000);
            }
        }
        if (source != null && source.getConstantState() != null) {
            return new MessageExportBackground(source.getConstantState().newDrawable().mutate(), null, Theme.getColor(Theme.key_chat_wallpaper, provider));
        }
        return fromTheme(null, false, provider);
    }

    static MessageExportBackground fromTheme(EmojiThemes theme, boolean dark, Theme.ResourcesProvider provider) {
        int c0 = Theme.getColor(Theme.key_chat_wallpaper, provider);
        int c1 = Theme.getColor(Theme.key_chat_wallpaper_gradient_to1, provider);
        int c2 = Theme.getColor(Theme.key_chat_wallpaper_gradient_to2, provider);
        int c3 = Theme.getColor(Theme.key_chat_wallpaper_gradient_to3, provider);
        if (c1 == 0 && c2 == 0 && c3 == 0) {
            return new MessageExportBackground(null, null, c0);
        }
        MotionBackgroundDrawable motion = new MotionBackgroundDrawable(c0, c1, c2, c3, false);
        MessageExportBackground background = new MessageExportBackground(motion, null, c0);
        if (theme != null) {
            int index = dark ? 1 : 0;
            int intensity = theme.getWallpaper(index) != null && theme.getWallpaper(index).settings != null ? theme.getWallpaper(index).settings.intensity : 0;
            motion.setPatternBitmap(intensity);
            int patternColor = motion.getPatternColor();
            theme.loadWallpaper(index, pair -> {
                if (pair == null || pair.second == null || pair.second.bitmap == null) {
                    return;
                }
                motion.setPatternBitmap(intensity, pair.second.bitmap);
                motion.setPatternColorFilter(patternColor);
                if (background.onUpdate != null) {
                    background.onUpdate.run();
                }
            });
        }
        return background;
    }

    Bitmap createServiceSource() {
        if (drawable instanceof MotionBackgroundDrawable) {
            return ((MotionBackgroundDrawable) drawable).getBitmap();
        }
        if (bitmap == null || bitmap.isRecycled()) {
            return null;
        }
        int w, h;
        if (bitmap.getWidth() > bitmap.getHeight()) {
            w = 40;
            h = Math.max(1, (int) (40f / bitmap.getWidth() * bitmap.getHeight()));
        } else {
            h = 40;
            w = Math.max(1, (int) (40f / bitmap.getHeight() * bitmap.getWidth()));
        }
        Bitmap small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        new Canvas(small).drawBitmap(bitmap, null, new Rect(0, 0, w, h), new Paint(Paint.FILTER_BITMAP_FLAG));
        Utilities.blurBitmap(small, 3);
        return small;
    }

    float getMotionIntensity() {
        return drawable instanceof MotionBackgroundDrawable ? ((MotionBackgroundDrawable) drawable).getIntensity() : 0;
    }

    void mapServiceShader(Matrix target, Bitmap source, int width, int period) {
        if (drawable instanceof MotionBackgroundDrawable) {
            target.setScale(width / (float) source.getWidth(), period / (float) source.getHeight());
        } else {
            float scale = Math.max(width / (float) source.getWidth(), period / (float) source.getHeight());
            target.setScale(scale, scale);
            target.postTranslate((width - source.getWidth() * scale) / 2f, (period - source.getHeight() * scale) / 2f);
        }
    }

    public void setOnUpdate(Runnable onUpdate) {
        this.onUpdate = onUpdate;
    }

    boolean isGradient() {
        return drawable instanceof MotionBackgroundDrawable;
    }

    int[] calcServiceColors() {
        Drawable source;
        if (drawable instanceof MotionBackgroundDrawable) {
            source = new ColorDrawable(averageColor(((MotionBackgroundDrawable) drawable).getColors()));
        } else if (drawable != null) {
            source = drawable;
        } else if (bitmap != null) {
            source = new BitmapDrawable(null, bitmap);
        } else {
            source = new ColorDrawable(color);
        }
        return AndroidUtilities.calcDrawableColor(source);
    }

    Drawable createPreviewDrawable() {
        if (drawable instanceof MotionBackgroundDrawable) {
            int[] colors = ((MotionBackgroundDrawable) drawable).getColors();
            return new MotionBackgroundDrawable(colors[0], colors[1], colors[2], colors[3], true);
        }
        if (bitmap != null) {
            return new Drawable() {
                private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
                private final Matrix previewMatrix = new Matrix();

                @Override
                public void draw(@NonNull Canvas canvas) {
                    Rect bounds = getBounds();
                    drawCenterCrop(canvas, bitmap, previewMatrix, paint, bounds.left, bounds.top, bounds.width(), bounds.height());
                }

                @Override
                public void setAlpha(int alpha) {
                    paint.setAlpha(alpha);
                }

                @Override
                public void setColorFilter(ColorFilter colorFilter) {
                    paint.setColorFilter(colorFilter);
                }

                @Override
                public int getOpacity() {
                    return PixelFormat.TRANSLUCENT;
                }
            };
        }
        if (drawable != null && drawable.getConstantState() != null) {
            return drawable.getConstantState().newDrawable().mutate();
        }
        return new ColorDrawable(color);
    }

    private static int averageColor(int[] colors) {
        int r = 0, g = 0, b = 0, count = 0;
        for (int c : colors) {
            if (c == 0) {
                continue;
            }
            r += (c >> 16) & 0xff;
            g += (c >> 8) & 0xff;
            b += c & 0xff;
            count++;
        }
        if (count == 0) {
            return 0xff000000;
        }
        return 0xff000000 | (r / count) << 16 | (g / count) << 8 | (b / count);
    }

    public void draw(Canvas canvas, int width, float top, float height, int period) {
        if (drawable == null && bitmap == null) {
            canvas.drawColor(color);
            return;
        }
        period = Math.max(1, period);
        long first = (long) Math.floor(top / period);
        long last = (long) Math.floor((top + height) / period);
        for (long k = first; k <= last; k++) {
            canvas.save();
            canvas.translate(0, k * period - top);
            canvas.clipRect(0, 0, width, period);
            if ((k & 1) != 0) {
                canvas.scale(1, -1, 0, period / 2f);
            }
            drawTile(canvas, width, period);
            canvas.restore();
        }
    }

    private void drawTile(Canvas canvas, int width, int height) {
        if (drawable != null) {
            drawable.setBounds(0, 0, width, height);
            drawable.draw(canvas);
            return;
        }
        drawCenterCrop(canvas, bitmap, matrix, bitmapPaint, 0, 0, width, height);
    }

    private static void drawCenterCrop(Canvas canvas, Bitmap bitmap, Matrix matrix, Paint paint, int left, int top, int width, int height) {
        float scale = Math.max(width / (float) bitmap.getWidth(), height / (float) bitmap.getHeight());
        matrix.setScale(scale, scale);
        matrix.postTranslate(left + (width - bitmap.getWidth() * scale) / 2f, top + (height - bitmap.getHeight() * scale) / 2f);
        canvas.drawBitmap(bitmap, matrix, paint);
    }
}
