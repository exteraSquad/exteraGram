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
import android.graphics.BitmapShader;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Build;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.SparseIntArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.MessageDrawable;
import org.telegram.ui.ActionBar.Theme;

import java.util.HashMap;
import java.util.Map;

final class MessageExportThemeProvider implements Theme.ResourcesProvider {

    private final SparseIntArray colors;
    private final boolean dark;
    private final HashMap<String, Drawable> drawables = new HashMap<>();
    private final HashMap<String, Paint> paints = new HashMap<>();
    private final Matrix serviceMatrix = new Matrix();
    private MessageExportBackground background;
    private Bitmap serviceSource;
    private BitmapShader serviceShader;
    private int servicePeriod;

    MessageExportThemeProvider(SparseIntArray colors, boolean dark) {
        this.colors = colors;
        this.dark = dark;
    }

    void init(MessageExportBackground background) {
        boolean gradient = background.isGradient();
        if (gradient || colors.get(Theme.key_chat_serviceBackground, 0) == 0) {
            int[] service = background.calcServiceColors();
            colors.put(Theme.key_chat_serviceBackground, service[0]);
            colors.put(Theme.key_chat_serviceBackgroundSelected, service[1]);
        }
        if (gradient) {
            colors.put(Theme.key_chat_serviceText, 0xffffffff);
            colors.put(Theme.key_chat_serviceLink, 0xffffffff);
            colors.put(Theme.key_chat_serviceIcon, 0xffffffff);
        }
        initDrawables();
        initPaints();
        initServiceShader(background);
    }

    void setServicePeriod(int period) {
        servicePeriod = period;
    }

    private void initServiceShader(MessageExportBackground background) {
        this.background = background;
        if (SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW) {
            return;
        }
        serviceSource = background.createServiceSource();
        if (serviceSource == null || serviceSource.isRecycled()) {
            serviceSource = null;
            return;
        }
        serviceShader = new BitmapShader(serviceSource, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            serviceShader.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
        }
        ColorMatrix colorMatrix = new ColorMatrix();
        if (background.isGradient()) {
            if (background.getMotionIntensity() >= 0) {
                colorMatrix.setSaturation(1.6f);
                AndroidUtilities.multiplyBrightnessColorMatrix(colorMatrix, dark ? .97f : .92f);
                AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, dark ? +.12f : -.06f);
            } else {
                colorMatrix.setSaturation(1.1f);
                AndroidUtilities.multiplyBrightnessColorMatrix(colorMatrix, dark ? .4f : .8f);
                AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, dark ? +.08f : -.06f);
            }
        } else {
            colorMatrix.setSaturation(1.6f);
            AndroidUtilities.multiplyBrightnessColorMatrix(colorMatrix, dark ? .9f : .84f);
            AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, dark ? +.04f : +.06f);
        }
        Paint action = paints.get(Theme.key_paint_chatActionBackground);
        if (action != null) {
            action.setAlpha(0xff);
            action.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
            action.setShader(serviceShader);
            action.setFilterBitmap(true);
        }
        Paint selected = paints.get(Theme.key_paint_chatActionBackgroundSelected);
        if (selected != null) {
            ColorMatrix selectedMatrix = new ColorMatrix(colorMatrix);
            AndroidUtilities.adjustSaturationColorMatrix(selectedMatrix, +.26f);
            AndroidUtilities.multiplyBrightnessColorMatrix(selectedMatrix, .92f);
            selected.setAlpha(0xff);
            selected.setColorFilter(new ColorMatrixColorFilter(selectedMatrix));
            selected.setShader(serviceShader);
            selected.setFilterBitmap(true);
        }
        Paint darken = paints.get(Theme.key_paint_chatActionBackgroundDarken);
        if (darken == null) {
            paints.put(Theme.key_paint_chatActionBackgroundDarken, darken = new Paint(Paint.ANTI_ALIAS_FLAG));
        }
        darken.setColor(0);
        darken.setAlpha(0);
    }

    @Override
    public void applyServiceShaderMatrix(int w, int h, float translationX, float translationY) {
        if (serviceShader == null || servicePeriod <= 0 || w <= 0) {
            return;
        }
        long tile = (long) Math.floor(translationY / servicePeriod);
        float local = translationY - tile * servicePeriod;
        background.mapServiceShader(serviceMatrix, serviceSource, w, servicePeriod);
        if ((tile & 1) != 0) {
            serviceMatrix.postScale(1, -1, 0, servicePeriod / 2f);
        }
        serviceMatrix.postTranslate(-translationX, -local);
        serviceShader.setLocalMatrix(serviceMatrix);
    }

    @Override
    public int getColor(int key) {
        if (key == Theme.key_chat_outBubbleGradientAnimated) {
            return 0;
        }
        int index = colors.indexOfKey(key);
        if (index >= 0) {
            return colors.valueAt(index);
        }
        int fallbackKey = Theme.getFallbackKey(key);
        if (fallbackKey >= 0) {
            index = colors.indexOfKey(fallbackKey);
            if (index >= 0) {
                return colors.valueAt(index);
            }
        }
        return Theme.getColor(key);
    }

    @Override
    public int getCurrentColor(int key) {
        return getColor(key);
    }

    @Override
    public Drawable getDrawable(String drawableKey) {
        return drawables.get(drawableKey);
    }

    @Override
    public Paint getPaint(String paintKey) {
        return paints.get(paintKey);
    }

    @Override
    public boolean isDark() {
        return dark;
    }

    @Override
    public boolean hasGradientService() {
        return serviceShader != null;
    }

    private void initDrawables() {
        for (Map.Entry<String, Drawable> entry : Theme.getThemeDrawablesMap().entrySet()) {
            Drawable drawable;
            switch (entry.getKey()) {
                case Theme.key_drawable_msgIn:
                    drawable = new MessageDrawable(MessageDrawable.TYPE_TEXT, false, false, this);
                    break;
                case Theme.key_drawable_msgInSelected:
                    drawable = new MessageDrawable(MessageDrawable.TYPE_TEXT, false, true, this);
                    break;
                case Theme.key_drawable_msgInMedia:
                    drawable = new MessageDrawable(MessageDrawable.TYPE_MEDIA, false, false, this);
                    break;
                case Theme.key_drawable_msgInMediaSelected:
                    drawable = new MessageDrawable(MessageDrawable.TYPE_MEDIA, false, true, this);
                    break;
                case Theme.key_drawable_msgOut:
                    drawable = new MessageDrawable(MessageDrawable.TYPE_TEXT, true, false, this);
                    break;
                case Theme.key_drawable_msgOutSelected:
                    drawable = new MessageDrawable(MessageDrawable.TYPE_TEXT, true, true, this);
                    break;
                case Theme.key_drawable_msgOutMedia:
                    drawable = new MessageDrawable(MessageDrawable.TYPE_MEDIA, true, false, this);
                    break;
                case Theme.key_drawable_msgOutMediaSelected:
                    drawable = new MessageDrawable(MessageDrawable.TYPE_MEDIA, true, true, this);
                    break;
                default:
                    Drawable.ConstantState state = entry.getValue() != null ? entry.getValue().getConstantState() : null;
                    drawable = state != null ? state.newDrawable().mutate() : null;
                    if (drawable != null) {
                        int colorKey = Theme.getThemeDrawableColorKey(entry.getKey());
                        if (colorKey >= 0) {
                            Theme.setDrawableColor(drawable, getColor(colorKey));
                        }
                    }
            }
            if (drawable != null) {
                drawables.put(entry.getKey(), drawable);
            }
        }
    }

    private void initPaints() {
        for (Map.Entry<String, Paint> entry : Theme.getThemePaintsMap().entrySet()) {
            Paint oldPaint = entry.getValue();
            Paint newPaint;
            if (oldPaint instanceof TextPaint) {
                newPaint = new TextPaint();
                newPaint.setTextSize(oldPaint.getTextSize());
                newPaint.setTypeface(oldPaint.getTypeface());
            } else {
                newPaint = new Paint();
            }
            if ((oldPaint.getFlags() & Paint.ANTI_ALIAS_FLAG) != 0) {
                newPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
            }
            int colorKey = Theme.getThemePaintColorKey(entry.getKey());
            if (colorKey >= 0 && !Theme.key_paint_chatActionBackgroundDarken.equals(entry.getKey())) {
                newPaint.setColor(getColor(colorKey));
            }
            paints.put(entry.getKey(), newPaint);
        }
    }
}
