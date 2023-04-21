/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.exteragram.messenger.icons.BaseIconSet;

public class ExteraResources extends Resources {

    private final Resources mResources;
    private BaseIconSet current = ExteraConfig.getIconPack();

    public void getActiveIconPack() {
        current = ExteraConfig.getIconPack();
    }

    public ExteraResources(@NonNull Resources resources) {
        super(resources.getAssets(), resources.getDisplayMetrics(), resources.getConfiguration());
        mResources = resources;
    }

    @Nullable
    @Override
    public Drawable getDrawableForDensity(int id, int density, @Nullable Theme theme) {
        return mResources.getDrawableForDensity(current.getIcon(id), density, theme);
    }

//    @NonNull
//    @Override
//    public InputStream openRawResource(int id) throws NotFoundException {
//        return super.openRawResource(current.getIcon(id));
//    }
}
