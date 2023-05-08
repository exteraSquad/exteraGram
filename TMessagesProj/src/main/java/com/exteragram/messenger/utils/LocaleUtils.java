/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.utils;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.view.View;

import com.exteragram.messenger.ExteraConfig;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.URLSpanNoUnderline;

public class LocaleUtils {
    public static String getActionBarTitle() {
        return getActionBarTitle(ExteraConfig.actionBarTitle);
    }

    public static String getActionBarTitle(int num) {
        TLRPC.User user = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
        String title;
        if (num == 0) {
            title = LocaleController.getString("exteraAppName", R.string.exteraAppName);
        } else if (num == 1) {
            title = LocaleController.getString("SearchAllChatsShort", R.string.SearchAllChatsShort);
        } else if (num == 2) {
            if (!TextUtils.isEmpty(UserObject.getPublicUsername(user))) {
                title = UserObject.getPublicUsername(user);
            } else {
                title = UserObject.getFirstName(user);
            }
        } else {
            title = UserObject.getFirstName(user);
        }
        return title;
    }

    public static CharSequence formatWithUsernames(String text, BaseFragment fragment) {
        int start = -1, end;
        boolean parse = false;
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(text);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '@') {
                start = i;
                parse = true;
            }
            if (parse && (i + 1 == text.length() || (!Character.isAlphabetic(text.charAt(i + 1)) && !Character.isDigit(text.charAt(i + 1))))) {
                end = i + 1;
                parse = false;
                String username = text.substring(start, end);
                try {
                    URLSpanNoUnderline urlSpan = new URLSpanNoUnderline(username) {
                        @Override
                        public void onClick(View widget) {
                            ChatUtils.getMessagesController().openByUserName(username.substring(1), fragment, 1);
                        }
                    };
                    stringBuilder.setSpan(urlSpan, start, end, 0);
                    if (i + 1 == text.length()) {
                        return stringBuilder;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                    return text;
                }
            }
        }
        return text;
    }

    public static CharSequence formatWithURLs(CharSequence charSequence) {
        Spannable spannable = new SpannableString(charSequence);
        URLSpan[] spans = spannable.getSpans(0, charSequence.length(), URLSpan.class);
        for (URLSpan urlSpan : spans) {
            URLSpan span = urlSpan;
            int start = spannable.getSpanStart(span), end = spannable.getSpanEnd(span);
            spannable.removeSpan(span);
            span = new URLSpanNoUnderline(span.getURL()) {
                @Override
                public void onClick(View widget) {
                    super.onClick(widget);
                }
            };
            spannable.setSpan(span, start, end, 0);
        }
        return spannable;
    }

    public static String capitalize(String s) {
        if (s == null)
            return null;
        char[] chars = s.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (i == 0) {
                chars[i] = Character.toUpperCase(chars[i]);
            } else if (Character.isLetter(chars[i])) {
                chars[i] = Character.toLowerCase(chars[i]);
            }
        }
        return new String(chars);
    }
}
