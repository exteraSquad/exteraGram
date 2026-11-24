/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.custom.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.EditTextCell;

@SuppressLint("ViewConstructor")
public class PillEditTextCell extends EditTextCell {

    private static final int LIMIT_SPACE_DP = 42;

    private final int maxLength;
    private final int showLimitWhenNear;
    private final int baseRightPadding;

    public PillEditTextCell(Context context, String hint, boolean multiline, int maxLength, int showLimitWhenNear, Theme.ResourcesProvider resourcesProvider) {
        super(context, hint, multiline, false, maxLength, resourcesProvider);
        this.maxLength = maxLength;
        this.showLimitWhenNear = showLimitWhenNear;
        setShowLimitWhenNear(showLimitWhenNear);
        editText.setTypeface(AndroidUtilities.regular());
        baseRightPadding = editText.getPaddingRight() - dp(LIMIT_SPACE_DP);
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateLimitSpace();
            }
        });
        updateLimitSpace();
    }

    private void updateLimitSpace() {
        int length = editText.length();
        boolean limitVisible = length > 0 && maxLength - length <= showLimitWhenNear;
        int right = baseRightPadding + (limitVisible ? dp(LIMIT_SPACE_DP) : 0);
        if (editText.getPaddingRight() != right) {
            editText.setPadding(editText.getPaddingLeft(), editText.getPaddingTop(), right, editText.getPaddingBottom());
        }
    }
}
