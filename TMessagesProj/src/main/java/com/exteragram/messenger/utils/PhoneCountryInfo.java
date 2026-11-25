package com.exteragram.messenger.utils;

import android.text.TextUtils;

import org.telegram.PhoneFormat.CallingCodeInfo;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.LocaleController;

import java.util.Locale;

public final class PhoneCountryInfo {

    private PhoneCountryInfo() {
    }

    public static CharSequence get(String phone) {
        if (TextUtils.isEmpty(phone)) {
            return null;
        }
        String digits = PhoneFormat.stripExceptNumbers(phone);
        if (digits.length() < 7) {
            return null;
        }

        CallingCodeInfo info = PhoneFormat.getInstance().findCallingCodeInfo(digits);
        if (info == null || info.countries == null || info.countries.isEmpty()) {
            return null;
        }

        String localCountry = Locale.getDefault().getCountry();
        String iso = info.countries.get(0);
        for (String candidate : info.countries) {
            if (candidate != null && localCountry.equalsIgnoreCase(candidate)) {
                iso = candidate;
                break;
            }
        }

        if (TextUtils.isEmpty(iso) || iso.charAt(0) == '_') {
            return null;
        }

        iso = iso.toUpperCase(Locale.ROOT);
        return LocaleController.getCountryWithFlag(iso, 12) + " (" + iso + ")";
    }
}
