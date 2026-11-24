/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.network;

import androidx.annotation.NonNull;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.pillstack.core.PillStackConfig;
import com.google.gson.annotations.SerializedName;

import org.telegram.messenger.BillingController;
import org.telegram.messenger.CacheFetcher;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ExchangeRates {

    public static final String USD = "USD";
    public static final String EUR = "EUR";
    public static final String RUB = "RUB";
    public static final String GBP = "GBP";
    public static final String KZT = "KZT";
    public static final String TRY = "TRY";
    public static final String UAH = "UAH";
    public static final String PLN = "PLN";
    public static final String AED = "AED";
    public static final String CNY = "CNY";
    public static final String JPY = "JPY";
    public static final String BYN = "BYN";
    public static final String ILS = "ILS";
    public static final String CZK = "CZK";
    public static final String INR = "INR";
    public static final String GRAM = "TON";
    public static final String BTC = "BTC";
    public static final String ETH = "ETH";
    public static final String SOL = "SOL";
    public static final String USDT = "USDT";

    public static final List<String> FIAT_CURRENCIES = Collections.unmodifiableList(Arrays.asList(
            AED, "AFN", "ALL", "AMD", "AOA", "ARS", "AUD", "AWG", "AZN", "BAM", "BBD", "BDT", "BHD", "BIF",
            "BMD", "BND", "BOB", "BRL", "BSD", "BTN", "BWP", BYN, "BZD", "CAD", "CDF", "CHF", "CLP", CNY,
            "COP", "CRC", "CUP", "CVE", CZK, "DJF", "DKK", "DOP", "DZD", "EGP", "ETB", EUR, "FJD", "FKP", GBP,
            "GEL", "GHS", "GIP", "GMD", "GNF", "GTQ", "GYD", "HKD", "HNL", "HTG", "HUF", "IDR", ILS, INR,
            "IQD", "IRR", "ISK", "JMD", "JOD", JPY, "KES", "KGS", "KHR", "KMF", "KRW", "KWD", "KYD", KZT,
            "LAK", "LBP", "LKR", "LRD", "LSL", "LYD", "MAD", "MDL", "MGA", "MKD", "MMK", "MNT", "MOP", "MRU",
            "MUR", "MVR", "MWK", "MXN", "MYR", "MZN", "NAD", "NGN", "NIO", "NOK", "NPR", "NZD", "OMR", "PAB",
            "PEN", "PGK", "PHP", "PKR", PLN, "PYG", "QAR", "RON", "RSD", RUB, "RWF", "SAR", "SBD", "SCR",
            "SDG", "SEK", "SGD", "SHP", "SOS", "SRD", "SSP", "SYP", "SZL", "THB", "TJS", "TMT", "TND", "TOP",
            TRY, "TTD", "TWD", "TZS", UAH, "UGX", USD, "UYU", "UZS", "VES", "VND", "VUV", "WST", "XAF", "XCD",
            "XOF", "XPF", "YER", "ZAR", "ZMW"
    ));

    private static final Set<String> CURRENCIES = new HashSet<>(FIAT_CURRENCIES);

    static {
        CURRENCIES.addAll(Arrays.asList(GRAM, BTC, ETH, SOL, USDT));
    }

    private static final int CACHE_TTL = 5 * 60 * 1000;
    private static final String CACHE_KEY = "exchangeRatesCache";
    private static final String TIMESTAMP_KEY = "exchangeRatesTimestamp";

    private static volatile State cacheValue;

    private static final ArrayList<Utilities.Callback4<Boolean, State, Long, Boolean>> pendingRemote = new ArrayList<>();
    private static boolean remoteInFlight;

    private static class CoinbaseResponse {
        @SerializedName("data")
        Data data;
    }

    private static class Data {
        @SerializedName("currency")
        String currency;
        @SerializedName("rates")
        Map<String, String> rates;
    }

    public record State(Map<String, BigDecimal> usdRates, long time) {
        public static final DecimalFormat formatter = new DecimalFormat("#.##");

        static {
            formatter.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        }

        public BigDecimal getUsdRate(String currency) {
            if (currency == null) return null;
            return usdRates.get(normalize(currency));
        }

        public BigDecimal getRate(String from, String to) {
            BigDecimal fromUsd = getUsdRate(from);
            BigDecimal toUsd = getUsdRate(to);
            if (fromUsd == null || toUsd == null || toUsd.signum() == 0) {
                return null;
            }
            return fromUsd.divide(toUsd, 12, RoundingMode.HALF_UP);
        }

        public double formatDonate(String currency, double fallbackRate, float usdAmount) {
            BigDecimal rateBD = getRate(ExchangeRates.USD, currency);
            double rate = rateBD != null ? rateBD.doubleValue() : fallbackRate;

            double finalAmount = rate * usdAmount;

            if ("ton".equalsIgnoreCase(currency)) {
                int markupPercent = RemoteUtils.getIntConfigValue(RemoteUtils.DONATES_TON_MARKUP_PERCENT, 10);
                finalAmount += finalAmount * (markupPercent / 100.0);
            }

            return Double.parseDouble(formatter.format(finalAmount));
        }
    }

    private static final CacheFetcher<Integer, State> fetcher = new CacheFetcher<>(CACHE_TTL) {
        @Override
        protected void getLocal(int currentAccount, Integer arguments, Utilities.Callback2<Long, State> onResult) {
            onResult.run(0L, getCached());
        }

        @Override
        protected void setLocal(int currentAccount, Integer arguments, State data, long hash) {
            if (data == null) {
                return;
            }
            cacheValue = data;
            try {
                PillStackConfig.getEditor().putString(CACHE_KEY, ExteraConfig.getGSON().toJson(data)).apply();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        @Override
        protected void getRemote(int currentAccount, Integer arguments, long hash, Utilities.Callback4<Boolean, State, Long, Boolean> onResult) {
            synchronized (pendingRemote) {
                pendingRemote.add(onResult);
                if (remoteInFlight) {
                    return;
                }
                remoteInFlight = true;
            }

            Request request = new Request.Builder()
                    .url("https://api.coinbase.com/v2/exchange-rates?currency=" + USD)
                    .build();

            ExteraHttpClient.INSTANCE.getClient().newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    FileLog.e(e);
                    completeRemote(null);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    if (!response.isSuccessful()) {
                        response.close();
                        onFailure(call, new IOException("Unexpected code " + response));
                        return;
                    }
                    try (ResponseBody body = response.body()) {
                        completeRemote(parseState(ExteraConfig.getGSON().fromJson(body.charStream(), CoinbaseResponse.class)));
                    } catch (Exception e) {
                        FileLog.e(e);
                        completeRemote(null);
                    }
                }
            });
        }

        @Override
        protected boolean saveLastTimeRequested() {
            return true;
        }

        @Override
        protected long getSavedLastTimeRequested(int hashCode) {
            return PillStackConfig.getPreferences().getLong(TIMESTAMP_KEY, 0);
        }

        @Override
        protected void setSavedLastTimeRequested(int hashCode, long time) {
            PillStackConfig.getEditor().putLong(TIMESTAMP_KEY, time).apply();
        }
    };

    private static void completeRemote(State state) {
        ArrayList<Utilities.Callback4<Boolean, State, Long, Boolean>> callbacks;
        synchronized (pendingRemote) {
            remoteInFlight = false;
            callbacks = new ArrayList<>(pendingRemote);
            pendingRemote.clear();
        }
        for (Utilities.Callback4<Boolean, State, Long, Boolean> callback : callbacks) {
            if (state == null) {
                callback.run(true, null, 0L, false);
            } else {
                callback.run(false, state, 0L, true);
            }
        }
    }

    public static State getCached() {
        if (cacheValue == null) {
            try {
                String json = PillStackConfig.getPreferences().getString(CACHE_KEY, null);
                if (json != null) {
                    cacheValue = ExteraConfig.getGSON().fromJson(json, State.class);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return cacheValue;
    }

    public static State peekCached() {
        return cacheValue;
    }

    public static void clearCache() {
        PillStackConfig.getEditor().putLong(TIMESTAMP_KEY, 0).apply();
        fetcher.forceRequest(0, 0);
    }

    public static List<String> getSupportedCurrencies() {
        return new ArrayList<>(CURRENCIES);
    }

    public static boolean isSupportedCurrency(String currency) {
        return CURRENCIES.contains(normalize(currency));
    }

    public static String getLocalCurrency(int account) {
        String currency = normalize(BillingController.getInstance().getTargetCurrency(account, false));
        return CURRENCIES.contains(currency) ? currency : null;
    }

    public static void fetch(Utilities.Callback<State> callback) {
        if (callback == null) {
            return;
        }
        fetcher.fetch(0, 0, callback);
    }

    private static State parseState(CoinbaseResponse response) {
        if (response == null || response.data == null || response.data.rates == null) {
            return null;
        }
        Map<String, BigDecimal> usdRates = new HashMap<>();
        for (String code : CURRENCIES) {
            BigDecimal usdRate = parseUsdRate(code, response.data.rates);
            if (usdRate != null) {
                usdRates.put(code, usdRate);
            }
        }
        if (usdRates.isEmpty()) {
            return null;
        }
        return new State(usdRates, System.currentTimeMillis());
    }

    private static BigDecimal parseUsdRate(String currency, Map<String, String> rates) {
        if (USD.equals(currency)) {
            return BigDecimal.ONE;
        }
        String value = rates.get(currency);
        if (value == null) {
            return null;
        }
        try {
            BigDecimal rate = new BigDecimal(value);
            if (rate.signum() == 0) {
                return null;
            }
            return BigDecimal.ONE.divide(rate, 16, RoundingMode.HALF_UP);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static String normalize(String currency) {
        if (currency == null) return "";
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}
