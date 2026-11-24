/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.components;

import android.annotation.SuppressLint;
import android.content.Context;
import com.exteragram.messenger.translator.TranslatorUtils;
import com.exteragram.messenger.utils.ui.PopupUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.Theme;

@SuppressLint("ViewConstructor")
public class TranslateBeforeSendWrapper extends ActionBarMenuSubItem {

    public TranslateBeforeSendWrapper(Context context, boolean top, boolean bottom, Theme.ResourcesProvider resourcesProvider) {
        super(context, top, bottom, resourcesProvider);
        setTextAndIcon(LocaleController.getString(R.string.TranslateTo), R.drawable.msg_translate);
        setSubtext(TranslatorUtils.getSendTargetLanguageTitle());
        setMinimumWidth(AndroidUtilities.dp(196));
        setItemHeight(56);
        setOnClickListener(v -> onClick());
        setOnLongClickListener(v -> showDialog(context));
        setRightIcon(R.drawable.msg_arrowright);
        getRightIcon().setOnClickListener(v -> showDialog(context));
    }

    protected void onClick() {
    }

    private boolean showDialog(Context context) {
        CharSequence[] baseTitles = TranslatorUtils.getTargetLanguageTitles();
        CharSequence[] titles = new CharSequence[baseTitles.length];
        System.arraycopy(baseTitles, 0, titles, 0, baseTitles.length);
        PopupUtils.showDialog(titles, LocaleController.getString(R.string.Language), TranslatorUtils.getSendTargetLanguageIndex(), context, i -> {
            TranslatorUtils.setSendTargetLanguage(TranslatorUtils.getTargetLanguageCodeByIndex(i));
            setSubtext(TranslatorUtils.getSendTargetLanguageTitle());
        });
        return true;
    }
}
