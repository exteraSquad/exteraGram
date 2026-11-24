/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.appicons.ui.components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.exteragram.messenger.appicons.AppIcon;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.LayoutHelper;

@SuppressLint("ViewConstructor")
public class AppIconBulletinLayout extends Bulletin.ButtonLayout {

    private static final int PREVIEW_SIZE_DP = 40;

    public AppIconBulletinLayout(@NonNull Context context, AppIcon icon, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);

        AppIconPreviewView previewView = new AppIconPreviewView(context, resourcesProvider);
        previewView.setIcon(icon);
        addView(previewView, LayoutHelper.createFrameRelatively(PREVIEW_SIZE_DP, PREVIEW_SIZE_DP, Gravity.START | Gravity.CENTER_VERTICAL, 10, 8, 10, 8));

        TextView textView = new TextView(context);
        textView.setGravity(Gravity.START);
        textView.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        textView.setTextColor(getThemedColor(Theme.key_undo_infoColor));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textView.setTypeface(AndroidUtilities.regular());
        textView.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.AppIconChangedTo, "**" + icon.getTitle() + "**")));
        addView(textView, LayoutHelper.createFrameRelatively(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, PREVIEW_SIZE_DP + 22, 0, 16, 0));
    }
}
