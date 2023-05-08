/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.utils;

import android.content.Context;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.RadioColorCell;

import java.util.ArrayList;

public class PopupUtils {

    public interface OnItemClickListener {
        void onClick(int i);
    }

    public static void showDialog(ArrayList<? extends CharSequence> items, String title, int selected, Context context, OnItemClickListener listener) {
        showDialog(items.stream().map(String::valueOf).toArray(CharSequence[]::new), null, title, selected, context, listener, null);
    }

    public static void showDialog(ArrayList<? extends CharSequence> items, int[] icons, String title, int selected, Context context, OnItemClickListener listener) {
        showDialog(items.stream().map(String::valueOf).toArray(CharSequence[]::new), icons, title, selected, context, listener, null);
    }

    public static void showDialog(CharSequence[] items, String title, int selected, Context context, OnItemClickListener listener) {
        showDialog(items, null, title, selected, context, listener, null);
    }

    public static void showDialog(CharSequence[] items, int[] icons, String title, int selected, Context context, OnItemClickListener listener) {
        showDialog(items, icons, title, selected, context, listener, null);
    }

    public static void showDialog(CharSequence[] items, int[] icons, String title, int selected, Context context, OnItemClickListener listener, Theme.ResourcesProvider resourcesProvider) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(title);
        if (icons == null) {
            final LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            builder.setView(linearLayout);
            for (int a = 0; a < items.length; a++) {
                RadioColorCell cell = new RadioColorCell(context);
                cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
                cell.setTag(a);
                cell.setCheckColor(Theme.getColor(Theme.key_radioBackground, resourcesProvider), Theme.getColor(Theme.key_dialogRadioBackgroundChecked, resourcesProvider));
                cell.setTextAndValue(items[a], selected == a);
                cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
                linearLayout.addView(cell);
                cell.setOnClickListener(v -> {
                    Integer which = (Integer) v.getTag();
                    builder.getDismissRunnable().run();
                    listener.onClick(which);
                });
            }
        } else {
            builder.setItems(items, icons, (dialog, which) -> {
                builder.getDismissRunnable().run();
                listener.onClick(which);
            });
            builder.create();
        }
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.show();
    }
}
