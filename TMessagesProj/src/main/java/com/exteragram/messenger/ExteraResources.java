package com.exteragram.messenger;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import com.exteragram.messenger.extras.IconReplacer;

public class ExteraResources extends Resources {

    public ExteraResources(Resources resources) {
        super(resources.getAssets(), resources.getDisplayMetrics(), resources.getConfiguration());
    }

    @androidx.annotation.Nullable
    @Override
    public Drawable getDrawableForDensity(int id, int density, @androidx.annotation.Nullable Theme theme) {
        return super.getDrawableForDensity(IconReplacer.replace(id), density, theme);
    }
}
