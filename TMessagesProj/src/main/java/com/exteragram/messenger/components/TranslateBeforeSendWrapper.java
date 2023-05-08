/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.components;

import android.annotation.SuppressLint;
import android.content.Context;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.utils.PopupUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.Theme;

import java.util.Arrays;

@SuppressLint("ViewConstructor")
public class TranslateBeforeSendWrapper extends ActionBarMenuSubItem {

    public TranslateBeforeSendWrapper(Context context, boolean top, boolean bottom, Theme.ResourcesProvider resourcesProvider) {
        super(context, top, bottom, resourcesProvider);
        setTextAndIcon(LocaleController.getString("TranslateTo", R.string.TranslateTo), R.drawable.msg_translate);
        setSubtext(ExteraConfig.getCurrentLangName());
        setMinimumWidth(AndroidUtilities.dp(196));
        setItemHeight(56);
        setOnClickListener(v -> onClick());
        setRightIcon(R.drawable.msg_arrowright);
        getRightIcon().setOnClickListener(v -> PopupUtils.showDialog(ExteraConfig.supportedLanguages, LocaleController.getString("Language", R.string.Language), Arrays.asList(ExteraConfig.supportedLanguages).indexOf(ExteraConfig.targetLanguage), context, i -> {
            ExteraConfig.editor.putString("targetLanguage", ExteraConfig.targetLanguage = (String) ExteraConfig.supportedLanguages[i]).apply();
            setSubtext(ExteraConfig.getCurrentLangName());
        }));
    }

    protected void onClick() {
    }
}
