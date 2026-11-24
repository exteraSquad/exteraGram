/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.custom.ui;

import android.text.InputType;
import android.text.TextUtils;
import android.view.View;

import com.exteragram.messenger.pillstack.custom.CustomPillSpec;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.EditTextCell;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

public class PillKeyValueSheet extends PillSheet {

    public static final int MODE_HEADERS = 0;
    public static final int MODE_SECRETS = 1;

    private static final int ITEM_ENTRY = 100;
    private static final int ITEM_DELETE = 1;
    private static final int ITEM_CANCEL = 2;

    private final int mode;
    private final ArrayList<String[]> entries;
    private final Utilities.Callback<ArrayList<String[]>> onChanged;
    private final EditTextCell nameCell;
    private final EditTextCell valueCell;
    private int editingIndex = -1;
    private int shift = -4;

    public PillKeyValueSheet(BaseFragment fragment, int mode, ArrayList<String[]> entries, Utilities.Callback<ArrayList<String[]>> onChanged) {
        super(fragment, true, LocaleController.getString(R.string.Add));
        this.mode = mode;
        this.entries = new ArrayList<>();
        for (String[] entry : entries) {
            this.entries.add(new String[]{entry[0], entry[1]});
        }
        this.onChanged = onChanged;

        boolean secrets = mode == MODE_SECRETS;
        nameCell = new PillEditTextCell(getContext(), LocaleController.getString(secrets ? R.string.CustomPillSecretNameHint : R.string.CustomPillHeaderNameHint), false, secrets ? 32 : CustomPillSpec.MAX_HEADER_NAME, 8, resourcesProvider);
        nameCell.editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        valueCell = new PillEditTextCell(getContext(), LocaleController.getString(secrets ? R.string.CustomPillSecretValueHint : R.string.CustomPillHeaderValueHint), false, CustomPillSpec.MAX_HEADER_VALUE, 64, resourcesProvider);
        valueCell.editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        valueCell.whenHitEnter(this::onButtonClick);
        ready();
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(mode == MODE_SECRETS ? R.string.CustomPillSecrets : R.string.CustomPillHeaders);
    }

    @Override
    protected void fillSheetItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        boolean secrets = mode == MODE_SECRETS;
        if (!entries.isEmpty()) {
            for (int i = 0; i < entries.size(); i++) {
                String[] entry = entries.get(i);
                CharSequence value;
                if (secrets) {
                    value = TextUtils.isEmpty(entry[1]) ? LocaleController.getString(R.string.CustomPillSecretNotSet) : "••••••";
                } else {
                    value = entry[1];
                }
                items.add(UItem.asButton(ITEM_ENTRY + i, secrets ? "{secret." + entry[0] + "}" : entry[0], value));
            }
            items.add(UItem.asShadow(LocaleController.getString(R.string.CustomPillKeyValueEditInfo)));
        }
        items.add(UItem.asHeader(LocaleController.getString(editingIndex >= 0 ? R.string.CustomPillKeyValueEditing : R.string.CustomPillKeyValueNew)));
        items.add(UItem.asCustom(nameCell));
        items.add(UItem.asCustom(valueCell));
        if (editingIndex >= 0) {
            items.add(UItem.asButton(ITEM_CANCEL, R.drawable.msg_cancel, LocaleController.getString(R.string.Cancel)));
            items.add(UItem.asButton(ITEM_DELETE, R.drawable.msg_delete, LocaleController.getString(R.string.Delete)).red());
        }
        items.add(UItem.asShadow(LocaleController.getString(secrets ? R.string.CustomPillSecretsInfo : R.string.CustomPillHeadersInfo)));
    }

    @Override
    protected void onItemClick(UItem item, View view) {
        if (item.id >= ITEM_ENTRY && item.id - ITEM_ENTRY < entries.size()) {
            editingIndex = item.id - ITEM_ENTRY;
            String[] entry = entries.get(editingIndex);
            nameCell.setText(entry[0]);
            valueCell.setText(entry[1]);
            setButtonText();
            update();
            valueCell.editText.requestFocus();
            AndroidUtilities.showKeyboard(valueCell.editText);
        } else if (item.id == ITEM_DELETE && editingIndex >= 0) {
            entries.remove(editingIndex);
            resetForm();
            onChanged.run(new ArrayList<>(entries));
        } else if (item.id == ITEM_CANCEL) {
            resetForm();
        }
    }

    private void resetForm() {
        editingIndex = -1;
        nameCell.setText("");
        valueCell.setText("");
        setButtonText();
        update();
    }

    private void setButtonText() {
        if (button != null) {
            button.setText(LocaleController.getString(editingIndex >= 0 ? R.string.Save : R.string.Add), true);
        }
    }

    @Override
    protected void onButtonClick() {
        String name = nameCell.getText().toString().trim();
        String value = valueCell.getText().toString();
        boolean secrets = mode == MODE_SECRETS;
        boolean validName = secrets ? CustomPillSpec.SECRET_NAME.matcher(name).matches() : CustomPillSpec.isValidHeaderName(name);
        if (!validName) {
            showError(nameCell);
            return;
        }
        if (!CustomPillSpec.isValidHeaderValue(value) || !secrets && TextUtils.isEmpty(value.trim())) {
            showError(valueCell);
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            if (i != editingIndex && entries.get(i)[0].equalsIgnoreCase(name)) {
                showError(nameCell);
                return;
            }
        }
        int limit = secrets ? CustomPillSpec.MAX_SECRETS : CustomPillSpec.MAX_HEADERS;
        if (editingIndex >= 0) {
            entries.set(editingIndex, new String[]{name, value});
        } else if (entries.size() < limit) {
            entries.add(new String[]{name, value});
        } else {
            showError(nameCell);
            return;
        }
        resetForm();
        onChanged.run(new ArrayList<>(entries));
    }

    private void showError(EditTextCell cell) {
        BotWebViewVibrationEffect.APP_ERROR.vibrate();
        AndroidUtilities.shakeViewSpring(cell, shift = -shift);
        cell.editText.requestFocus();
        AndroidUtilities.showKeyboard(cell.editText);
    }
}
