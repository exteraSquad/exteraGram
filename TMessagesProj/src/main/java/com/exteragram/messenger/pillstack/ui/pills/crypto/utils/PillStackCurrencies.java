/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.ui.pills.crypto.utils;

import com.exteragram.messenger.utils.network.ExchangeRates;

import org.telegram.messenger.BillingController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Wallet.CurrenciesController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

public final class PillStackCurrencies {

    public static final String TARGET_AUTO = "AUTO";

    private record CurrencyInfo(String symbolOverride, boolean suffixSymbol) {
    }

    private static final HashSet<String> AMBIGUOUS_SYMBOLS = new HashSet<>(Arrays.asList(
            "$", "kr", "Fr", "₩"
    ));

    private static final HashSet<String> PILL_CURRENCIES = new HashSet<>(ExchangeRates.FIAT_CURRENCIES);
    private static final Map<String, CurrencyInfo> CURRENCIES = new HashMap<>();
    public static final String[] TARGET_CURRENCIES;

    static {
        PILL_CURRENCIES.addAll(Arrays.asList(ExchangeRates.GRAM, ExchangeRates.BTC, ExchangeRates.ETH, ExchangeRates.SOL));

        addCurrency(ExchangeRates.USD, "$", false);
        addCurrency(ExchangeRates.RUB, "₽", true);
        addCurrency(ExchangeRates.KZT, "₸", true);
        addCurrency(ExchangeRates.TRY, "₺", true);
        addCurrency(ExchangeRates.UAH, "₴", true);
        addCurrency(ExchangeRates.PLN, "zł", true);
        addCurrency(ExchangeRates.CNY, "CN¥", false);
        addCurrency(ExchangeRates.BYN, "Br", true);
        addCurrency(ExchangeRates.ILS, "₪", false);
        addCurrency(ExchangeRates.CZK, "Kč", true);
        addCurrency(ExchangeRates.INR, "₹", false);

        TARGET_CURRENCIES = new String[ExchangeRates.FIAT_CURRENCIES.size() + 1];
        TARGET_CURRENCIES[0] = TARGET_AUTO;
        for (int i = 0; i < ExchangeRates.FIAT_CURRENCIES.size(); i++) {
            TARGET_CURRENCIES[i + 1] = ExchangeRates.FIAT_CURRENCIES.get(i);
        }
    }

    private PillStackCurrencies() {
    }

    private static void addCurrency(String code, String symbolOverride, boolean suffixSymbol) {
        CURRENCIES.put(normalize(code), new CurrencyInfo(symbolOverride, suffixSymbol));
    }

    public static CharSequence getTargetCurrencySubtext(String currency) {
        if (currency == null || TARGET_AUTO.equalsIgnoreCase(currency)) {
            return LocaleController.getString(R.string.QualityAuto);
        }
        return getCurrencyName(currency);
    }

    public static String getCurrencyName(String currency) {
        String normalized = normalize(currency);
        Locale locale = LocaleController.getInstance().getCurrentLocale();
        if (locale == null) {
            locale = Locale.getDefault();
        }
        try {
            return CurrenciesController.capitalizeFirstLetter(Currency.getInstance(normalized).getDisplayName(locale), locale);
        } catch (IllegalArgumentException e) {
            return normalized;
        }
    }

    public static String resolveTargetCurrency(int account, String selection) {
        String target = normalize(selection);
        if (TARGET_AUTO.equals(target)) {
            String local = ExchangeRates.getLocalCurrency(account);
            return local != null && PILL_CURRENCIES.contains(local) ? local : ExchangeRates.USD;
        }
        return PILL_CURRENCIES.contains(target) ? target : ExchangeRates.USD;
    }

    public static String[] getTargetCurrencies(String baseCurrency) {
        if (baseCurrency == null || baseCurrency.isEmpty()) {
            return TARGET_CURRENCIES;
        }
        int count = 0;
        for (String currency : TARGET_CURRENCIES) {
            if (!baseCurrency.equalsIgnoreCase(currency)) {
                count++;
            }
        }
        String[] filtered = new String[count];
        int index = 0;
        for (String currency : TARGET_CURRENCIES) {
            if (!baseCurrency.equalsIgnoreCase(currency)) {
                filtered[index++] = currency;
            }
        }
        return filtered;
    }

    public static String formatFiatPrice(BigDecimal priceValue, String targetCurrency) {
        if (priceValue == null || targetCurrency == null || targetCurrency.isEmpty()) {
            return null;
        }
        try {
            BillingController billing = BillingController.getInstance();
            int exp = Math.max(0, billing.getCurrencyExp(targetCurrency));
            BigDecimal rounded = priceValue.setScale(exp, RoundingMode.HALF_UP);
            NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);
            numberFormat.setGroupingUsed(true);
            numberFormat.setMinimumFractionDigits(exp);
            numberFormat.setMaximumFractionDigits(exp);
            String number = numberFormat.format(rounded);

            String code = normalize(targetCurrency);
            CurrencyInfo info = CURRENCIES.get(code);
            String symbol = info != null ? info.symbolOverride : null;
            boolean overridden = symbol != null;
            if (!overridden) {
                try {
                    Currency currency = Currency.getInstance(code);
                    symbol = currency.getSymbol(Locale.US);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            if (symbol == null || symbol.isEmpty() || symbol.equalsIgnoreCase(code)) {
                return number + " " + targetCurrency;
            }

            if (!overridden && AMBIGUOUS_SYMBOLS.contains(symbol)) {
                return number + " " + targetCurrency;
            }

            if (info != null && info.suffixSymbol) {
                return number + " " + symbol;
            }
            return symbol + number;
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String normalize(String currency) {
        if (currency == null) return "";
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}
