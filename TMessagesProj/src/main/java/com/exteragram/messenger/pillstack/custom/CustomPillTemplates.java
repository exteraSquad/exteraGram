/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.custom;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.IconBackgroundColors;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public final class CustomPillTemplates {

    public static final String BLANK = "blank";
    public static final String CRYPTO = "crypto";
    public static final String CURRENCY = "currency";
    public static final String GITHUB = "github";
    public static final String STATUS = "status";
    public static final String WEATHER = "weather";
    public static final String WALLET = "wallet";
    public static final String COUNTDOWN = "countdown";

    private CustomPillTemplates() {
    }

    public record Template(String id, int titleRes, String subtitle, String icon, String color) {
        public CharSequence getTitle() {
            return LocaleController.getString(titleRes);
        }
    }

    public static List<Template> getAll() {
        ArrayList<Template> list = new ArrayList<>();
        list.add(new Template(BLANK, R.string.CustomPillTemplateBlank, LocaleController.getString(R.string.CustomPillTemplateBlankInfo), "pillstack_code", CustomPillSpec.COLOR_NEUTRAL));
        list.add(new Template(CRYPTO, R.string.CustomPillTemplateCrypto, "CoinGecko", "pillstack_btc", IconBackgroundColors.ORANGE_BRIGHT.name()));
        list.add(new Template(CURRENCY, R.string.CustomPillTemplateCurrency, "open.er-api.com", "pillstack_usd", IconBackgroundColors.GREEN_DEEP.name()));
        list.add(new Template(GITHUB, R.string.CustomPillTemplateGitHub, "api.github.com", "pillstack_github", IconBackgroundColors.GRAY.name()));
        list.add(new Template(STATUS, R.string.CustomPillTemplateStatus, LocaleController.getString(R.string.CustomPillTemplateStatusInfo), "pillstack_status", IconBackgroundColors.GREEN.name()));
        list.add(new Template(WEATHER, R.string.CustomPillTemplateWeather, "Open-Meteo", "weather_cloudy", IconBackgroundColors.BLUE_ALT.name()));
        list.add(new Template(WALLET, R.string.CustomPillTemplateWallet, "toncenter.com", "pillstack_wallet", IconBackgroundColors.BLUE_DEEP.name()));
        list.add(new Template(COUNTDOWN, R.string.CustomPillTemplateCountdown, LocaleController.getString(R.string.CustomPillTemplateCountdownInfo), "filled_poll_deadline_24", IconBackgroundColors.PURPLE.name()));
        return list;
    }

    public static CustomPillSpec create(String id) {
        CustomPillSpec spec = new CustomPillSpec();
        switch (id) {
            case CRYPTO -> {
                spec.name = "Bitcoin";
                spec.icon = new CustomPillSpec.Icon(CustomPillSpec.ICON_RES, "pillstack_btc");
                spec.color = IconBackgroundColors.ORANGE_BRIGHT.name();
                spec.url = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd&include_24hr_change=true";
                spec.text = "{bitcoin.usd|compact} $ {bitcoin.usd_24h_change|fixed:1|sign}%";
                CustomPillSpec.Rule rule = new CustomPillSpec.Rule();
                rule.path = "bitcoin.usd_24h_change";
                rule.op = CustomPillSpec.OP_LESS;
                rule.value = "0";
                rule.color = IconBackgroundColors.RED.name();
                spec.rules.add(rule);
                spec.tap = CustomPillSpec.TAP_DETAILS;
            }
            case CURRENCY -> {
                boolean ruble = "ru".equals(LocaleController.getInstance().getCurrentLocale().getLanguage());
                spec.name = LocaleController.getString(R.string.CustomPillTemplateCurrencyName);
                spec.icon = new CustomPillSpec.Icon(CustomPillSpec.ICON_RES, "pillstack_usd");
                spec.color = IconBackgroundColors.GREEN_DEEP.name();
                spec.url = "https://open.er-api.com/v6/latest/USD";
                spec.text = ruble ? "{rates.RUB|fixed:2} ₽" : "{rates.EUR|fixed:3} €";
                spec.refresh = 3600;
                spec.tap = CustomPillSpec.TAP_DETAILS;
            }
            case GITHUB -> {
                spec.name = "GitHub";
                spec.icon = new CustomPillSpec.Icon(CustomPillSpec.ICON_RES, "pillstack_github");
                spec.url = "https://api.github.com/repos/DrKLO/Telegram";
                spec.text = "{stargazers_count|compact}";
                spec.refresh = 3600;
                spec.tap = CustomPillSpec.TAP_LINK;
                spec.link = "https://github.com/{full_name}";
            }
            case STATUS -> {
                spec.name = LocaleController.getString(R.string.CustomPillTemplateStatusName);
                spec.icon = new CustomPillSpec.Icon(CustomPillSpec.ICON_RES, "pillstack_status");
                spec.url = "https://example.com";
                spec.text = LocaleController.getString(R.string.CustomPillTemplateStatusText);
                CustomPillSpec.Rule rule = new CustomPillSpec.Rule();
                rule.path = PillTemplate.META_STATUS;
                rule.op = CustomPillSpec.OP_NOT_EQUALS;
                rule.value = "200";
                rule.color = IconBackgroundColors.RED.name();
                rule.text = LocaleController.getString(R.string.CustomPillTemplateStatusDown);
                spec.rules.add(rule);
                spec.refresh = 60;
            }
            case WEATHER -> {
                spec.name = LocaleController.getString(R.string.CustomPillTemplateWeather);
                spec.icon = new CustomPillSpec.Icon(CustomPillSpec.ICON_RES, "weather_cloudy");
                spec.color = IconBackgroundColors.BLUE_ALT.name();
                spec.url = "https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&current=temperature_2m,wind_speed_10m";
                spec.text = LocaleController.getString(R.string.CustomPillTemplateWeatherText);
                spec.refresh = 1800;
                spec.tap = CustomPillSpec.TAP_DETAILS;
            }
            case WALLET -> {
                spec.name = LocaleController.getString(R.string.CustomPillTemplateWallet);
                spec.icon = new CustomPillSpec.Icon(CustomPillSpec.ICON_RES, "pillstack_wallet");
                spec.color = IconBackgroundColors.BLUE_DEEP.name();
                spec.url = "https://toncenter.com/api/v2/getAddressBalance?address={secret.address}";
                spec.secrets.add("address");
                spec.text = "{result|div:1e9|fixed:2} GRAM";
                spec.refresh = 900;
            }
            case COUNTDOWN -> {
                Calendar calendar = Calendar.getInstance();
                int nextYear = calendar.get(Calendar.YEAR) + 1;
                spec.name = LocaleController.getString(R.string.CustomPillTemplateCountdownName);
                spec.icon = new CustomPillSpec.Icon(CustomPillSpec.ICON_RES, "filled_poll_deadline_24");
                spec.color = IconBackgroundColors.PURPLE.name();
                spec.http = false;
                spec.text = LocaleController.formatString(R.string.CustomPillTemplateCountdownText, "{$now|until:" + nextYear + "-01-01}");
                spec.tap = CustomPillSpec.TAP_NONE;
            }
            default -> {
                spec.name = "";
                spec.icon = new CustomPillSpec.Icon(CustomPillSpec.ICON_RES, "pillstack_code");
                spec.url = "https://";
            }
        }
        return spec;
    }
}
