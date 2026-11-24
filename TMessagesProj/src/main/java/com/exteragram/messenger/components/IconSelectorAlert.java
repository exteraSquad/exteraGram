/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;

import com.exteragram.messenger.utils.folders.FolderUiHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

public class IconSelectorAlert {

    public static void show(BaseFragment fragment, View view, String selectedIcon, OnIconSelectedListener onIconSelectedListener) {
        Context context = fragment.getContext();
        if (context == null) {
            return;
        }
        Theme.ResourcesProvider resourcesProvider = fragment.getResourceProvider();
        int selectedColor = Theme.getColor(Theme.key_windowBackgroundWhiteValueText, resourcesProvider);
        int iconColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon, resourcesProvider);
        int selectorColor = Theme.getColor(Theme.key_listSelector, resourcesProvider);

        View cell = (View) view.getParent();
        ItemOptions options = ItemOptions.makeOptions(fragment, cell)
                .setGravity(Gravity.LEFT)
                .translate(view.getX() - dp(8 + 4 + 1), 0);
        if (cell.getParent() instanceof RecyclerListView) {
            options.setScrimViewBackground(((RecyclerListView) cell.getParent()).getClipBackground(cell));
        }

        GridLayout gridLayout = new GridLayout(context);
        int columnCount = 6;
        while (columnCount > 1 && dp(50 * columnCount + 40) > AndroidUtilities.displaySize.x) {
            columnCount--;
        }
        gridLayout.setColumnCount(columnCount);
        for (String icon : FolderUiHelper.folderIcons.keySet()) {
            boolean selected = icon.equals(selectedIcon);
            ImageView imageView = new ImageView(context);
            imageView.setScaleType(ImageView.ScaleType.FIT_XY);
            imageView.setPadding(dp(6), dp(6), dp(6), dp(6));
            imageView.setImageResource(FolderUiHelper.getTabIcon(icon));
            imageView.setColorFilter(new PorterDuffColorFilter(selected ? selectedColor : iconColor, PorterDuff.Mode.MULTIPLY));
            imageView.setBackground(Theme.createRadSelectorDrawable(selected ? Theme.multAlpha(selectedColor, 40 / 255f) : 0, selectorColor, 7, 7));
            imageView.setOnClickListener(v -> {
                options.dismiss();
                if (!selected) {
                    onIconSelectedListener.onIconSelected(icon);
                }
            });
            gridLayout.addView(imageView, LayoutHelper.createFrame(48, 48, Gravity.CENTER, 1, 1, 1, 1));
        }
        options.addView(gridLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 4, 4, 4, 4));
        options.show();
    }

    public interface OnIconSelectedListener {
        void onIconSelected(String emoticon);
    }
}
