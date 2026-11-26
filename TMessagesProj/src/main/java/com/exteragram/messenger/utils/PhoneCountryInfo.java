package com.exteragram.messenger.utils;

import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import org.telegram.PhoneFormat.CallingCodeInfo;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.LocaleController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PhoneCountryInfo {

    private static final int MAX_COUNTRIES = 3;

    private PhoneCountryInfo() {
    }

    public static CharSequence get(String phone, String exactIso) {
        List<String> countries = new ArrayList<>();
        if (isValid(exactIso)) {
            countries.add(exactIso.toUpperCase(Locale.ROOT));
        } else {
            countries.addAll(countriesForPhone(phone));
        }
        if (countries.isEmpty()) {
            return null;
        }

        SpannableStringBuilder sb = new SpannableStringBuilder();
        int limit = Math.min(countries.size(), MAX_COUNTRIES);
        for (int i = 0; i < limit; i++) {
            if (i != 0) {
                sb.append(" / ");
            }
            String iso = countries.get(i);
            sb.append(LocaleController.getCountryWithFlag(iso, 12)).append(" (").append(iso).append(")");
        }
        if (countries.size() > limit) {
            sb.append(" +").append(countries.size() - limit);
        }
        return sb;
    }

    private static List<String> countriesForPhone(String phone) {
        List<String> countries = new ArrayList<>();
        if (TextUtils.isEmpty(phone)) {
            return countries;
        }
        String digits = PhoneFormat.stripExceptNumbers(phone);
        if (digits.length() < 7) {
            return countries;
        }

        CallingCodeInfo info = PhoneFormat.getInstance().findCallingCodeInfo(digits);
        if (info == null || info.countries == null) {
            return countries;
        }
        for (String country : info.countries) {
            if (isValid(country)) {
                String iso = country.toUpperCase(Locale.ROOT);
                if (!countries.contains(iso)) {
                    countries.add(iso);
                }
            }
        }
        return countries;
    }

    private static boolean isValid(String iso) {
        return !TextUtils.isEmpty(iso) && iso.length() == 2 && iso.charAt(0) != '_';
    }
}
