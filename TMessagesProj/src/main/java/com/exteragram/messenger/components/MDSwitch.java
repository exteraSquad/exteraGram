package com.exteragram.messenger.components;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.HapticFeedbackConstants;
import android.view.View;

import com.google.android.material.materialswitch.MaterialSwitch;

import org.telegram.ui.ActionBar.Theme;

public class MDSwitch extends View {

    private final MaterialSwitch materialSwitch;
    private final Theme.ResourcesProvider resourcesProvider;

    public MDSwitch(Context context) {
        this(context, null);
    }

    public MDSwitch(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        materialSwitch = new MaterialSwitch(context);
    }

    public boolean isChecked() {
        return materialSwitch.isChecked();
    }

    public void setChecked(boolean checked) {
        materialSwitch.setChecked(checked);
    }


    public boolean hasIcon() {
        return materialSwitch.getThumbIconDrawable() != null;
    }

    public void setIcon(int icon) {
        Drawable iconDrawable = getResources().getDrawable(icon).mutate();
        materialSwitch.setThumbIconDrawable(iconDrawable);
        invalidate();
    }
}
