/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui;

import android.content.Context;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class PopupUtils {

    public static void showDialog(ArrayList<? extends CharSequence> items, String title, int selected, Context context, OnItemClickListener listener) {
        showDialog(items.stream().map(String::valueOf).toArray(CharSequence[]::new), null, title, selected, context, listener, null, true);
    }

    public static void showDialog(ArrayList<? extends CharSequence> items, int[] icons, String title, int selected, Context context, OnItemClickListener listener) {
        showDialog(items.stream().map(String::valueOf).toArray(CharSequence[]::new), icons, title, selected, context, listener, null, true);
    }

    public static void showDialog(CharSequence[] items, String title, int selected, Context context, OnItemClickListener listener) {
        showDialog(items, null, title, selected, context, listener, null, true);
    }

    public static void showDialog(CharSequence[] items, int[] icons, String title, int selected, Context context, OnItemClickListener listener) {
        showDialog(items, icons, title, selected, context, listener, null, true);
    }

    public static void showDialog(CharSequence[] items, int[] icons, String title, int selected, Context context, OnItemClickListener listener, Theme.ResourcesProvider resourcesProvider) {
        showDialog(items, icons, title, selected, context, listener, resourcesProvider, true);
    }

    public static void showDialog(CharSequence[] items, int[] icons, String title, int selected, Context context, OnItemClickListener listener, Theme.ResourcesProvider resourcesProvider, boolean useRadioCell) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(title);
        if (useRadioCell) {
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
            if (icons != null) {
                builder.setItems(items, icons, (dialog, which) -> {
                    builder.getDismissRunnable().run();
                    listener.onClick(which);
                });
            } else {
                builder.setItems(items, (dialog, which) -> {
                    builder.getDismissRunnable().run();
                    listener.onClick(which);
                });
            }
            builder.create();
        }
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }

    public static void showDialogWithoutRadio(ArrayList<? extends CharSequence> items, String title, Context context, OnItemClickListener listener) {
        showDialog(items.stream().map(String::valueOf).toArray(CharSequence[]::new), null, title, -1, context, listener, null, false);
    }

    public static void showMultiSelectDialog(CharSequence[] items, boolean[] checked, String title, Context context, OnMultiSelectListener listener, Theme.ResourcesProvider resourcesProvider) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle(title);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < items.length; i++) {
            CheckBoxCell cell = new CheckBoxCell(context, CheckBoxCell.TYPE_CHECK_BOX_ROUND, 21, true, resourcesProvider);
            cell.getCheckBoxRound().setColor(Theme.key_switch2TrackChecked, Theme.key_radioBackground, Theme.key_checkboxCheck);
            cell.setText(items[i], null, i < checked.length && checked[i], i < items.length - 1);
            cell.setOnClickListener(v -> cell.setChecked(!cell.isChecked(), true));
            cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), Theme.RIPPLE_MASK_ALL));
            layout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        builder.setView(layout);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
            boolean[] selected = new boolean[layout.getChildCount()];
            for (int i = 0; i < selected.length; i++) {
                selected[i] = ((CheckBoxCell) layout.getChildAt(i)).isChecked();
            }
            listener.onClick(selected);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }

    public interface OnItemClickListener {
        void onClick(int i);
    }

    public interface OnMultiSelectListener {
        void onClick(boolean[] checked);
    }
}