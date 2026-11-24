/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.custom;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PillTemplate {

    public static final String META_STATUS = "$status";
    public static final String META_MS = "$ms";
    public static final String META_UPDATED = "$updated";
    public static final String META_NOW = "$now";
    public static final String META_BODY = "$body";

    public static final String MISSING = "—";

    private static final Pattern TOKEN = Pattern.compile("\\{([^{}]+)\\}");
    private static final Pattern NUMBER = Pattern.compile("[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?");
    private static final String[] AGGREGATES = {"sum", "avg", "min", "max", "count", "first", "last", "join"};
    private static final int MAX_FIXED = 8;
    private static final int MAX_BODY_TEXT = 256;

    private PillTemplate() {
    }

    public static final class Filter {
        public final String name;
        @Nullable
        public final String arg;

        public Filter(String name, @Nullable String arg) {
            this.name = name;
            this.arg = arg;
        }

        @NonNull
        @Override
        public String toString() {
            return arg == null ? name : name + ":" + arg;
        }
    }

    public static final class Token {
        public final int start;
        public final int end;
        public final String path;
        public final ArrayList<Filter> filters;

        Token(int start, int end, String path, ArrayList<Filter> filters) {
            this.start = start;
            this.end = end;
            this.path = path;
            this.filters = filters;
        }

        @Nullable
        public Filter find(String name) {
            for (Filter filter : filters) {
                if (filter.name.equals(name)) {
                    return filter;
                }
            }
            return null;
        }

        public static String build(String path, ArrayList<Filter> filters) {
            StringBuilder builder = new StringBuilder("{").append(path);
            for (Filter filter : filters) {
                builder.append('|').append(filter);
            }
            return builder.append('}').toString();
        }
    }

    public static final class Context {
        @Nullable
        public JsonElement json;
        @Nullable
        public String body;
        public int status;
        public long ms;
        public long updated;
        public long now;
        public boolean hasResponse;

        public static Context now() {
            Context context = new Context();
            context.now = System.currentTimeMillis() / 1000;
            context.updated = context.now;
            return context;
        }
    }

    public static ArrayList<Token> parse(@Nullable String template) {
        ArrayList<Token> tokens = new ArrayList<>();
        if (TextUtils.isEmpty(template)) {
            return tokens;
        }
        Matcher matcher = TOKEN.matcher(template);
        while (matcher.find()) {
            tokens.add(parseToken(matcher.group(1), matcher.start(), matcher.end()));
        }
        return tokens;
    }

    private static Token parseToken(String inner, int start, int end) {
        String[] parts = inner.split("\\|");
        String path = parts[0].trim();
        ArrayList<Filter> filters = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) {
                continue;
            }
            int colon = part.indexOf(':');
            if (colon < 0) {
                filters.add(new Filter(part, null));
            } else {
                filters.add(new Filter(part.substring(0, colon).trim(), part.substring(colon + 1)));
            }
        }
        return new Token(start, end, path, filters);
    }

    public static String render(@Nullable String template, Context context) {
        if (TextUtils.isEmpty(template)) {
            return "";
        }
        Matcher matcher = TOKEN.matcher(template);
        StringBuilder builder = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            builder.append(template, last, matcher.start());
            builder.append(evaluate(parseToken(matcher.group(1), matcher.start(), matcher.end()), context));
            last = matcher.end();
        }
        builder.append(template, last, template.length());
        return builder.toString();
    }

    public static boolean usesMeta(@Nullable String template, String meta) {
        if (TextUtils.isEmpty(template)) {
            return false;
        }
        for (Token token : parse(template)) {
            if (token.path.equals(meta)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static Object resolve(String path, Context context) {
        if (path.startsWith("$")) {
            return switch (path) {
                case META_STATUS -> context.hasResponse ? (Object) (long) context.status : null;
                case META_MS -> context.hasResponse ? (Object) context.ms : null;
                case META_UPDATED -> context.updated > 0 ? (Object) context.updated : null;
                case META_NOW -> context.now;
                case META_BODY -> context.body == null ? null : CustomPillSpec.clip(context.body.trim(), MAX_BODY_TEXT);
                default -> null;
            };
        }
        return JsonPath.resolve(context.json, path);
    }

    public static String evaluate(Token token, Context context) {
        Object value = resolve(token.path, context);
        String fixed = null;
        String defaultValue = null;
        boolean compact = false, sign = false, percent = false, ago = false, time = false, upper = false, lower = false, hasFixed = false;
        for (Filter filter : token.filters) {
            String name = filter.name;
            String arg = filter.arg;
            if (value instanceof JsonArray array && contains(AGGREGATES, name)) {
                value = aggregate(name, arg, array);
                continue;
            }
            switch (name) {
                case "div", "mul", "add", "sub" -> {
                    double x = toNumber(value);
                    double y = parseNumber(arg);
                    value = switch (name) {
                        case "div" -> x / y;
                        case "mul" -> x * y;
                        case "add" -> x + y;
                        default -> x - y;
                    };
                }
                case "abs" -> value = Math.abs(toNumber(value));
                case "round" -> {
                    hasFixed = true;
                    fixed = "0";
                }
                case "fixed" -> {
                    hasFixed = true;
                    fixed = arg;
                }
                case "until" -> {
                    double base = toNumber(value);
                    if (Double.isNaN(base)) {
                        base = context.now;
                    }
                    long target = parseDate(arg);
                    value = target < 0 ? Double.NaN : Math.max(0, Math.ceil((target - base) / 86400.0));
                }
                case "compact" -> compact = true;
                case "sign" -> sign = true;
                case "percent" -> percent = true;
                case "ago" -> ago = true;
                case "time" -> time = true;
                case "upper" -> upper = true;
                case "lower" -> lower = true;
                case "default" -> defaultValue = arg;
            }
        }
        if (isMissing(value)) {
            return defaultValue != null ? defaultValue : MISSING;
        }
        String result;
        if (ago || time) {
            double seconds = toNumber(value);
            if (Double.isNaN(seconds)) {
                return defaultValue != null ? defaultValue : MISSING;
            }
            if (seconds > 1e12) {
                seconds /= 1000;
            }
            if (ago) {
                long now = context.now > 0 ? context.now : System.currentTimeMillis() / 1000;
                result = LocaleController.formatRelativeDate(Math.max(0, now - (long) seconds));
            } else {
                result = LocaleController.getInstance().getFormatterDay().format(new Date((long) (seconds * 1000)));
            }
        } else if (value instanceof JsonElement element && element.isJsonObject()) {
            result = "{…}";
        } else if (value instanceof JsonArray array) {
            result = "[" + array.size() + "]";
        } else {
            double number = toNumber(value);
            boolean numeric = !Double.isNaN(number) && !isBoolean(value);
            if (numeric && (hasFixed || compact || sign || percent)) {
                double x = percent ? number * 100 : number;
                Integer digits = null;
                if (fixed != null) {
                    try {
                        digits = Math.max(0, Math.min(MAX_FIXED, Integer.parseInt(fixed.trim())));
                    } catch (NumberFormatException ignore) {
                    }
                }
                result = compact ? compact(x, digits) : digits != null ? fixed(x, digits) : plain(x);
                if (sign && x > 0) {
                    result = "+" + result;
                }
                if (percent) {
                    result += "%";
                }
            } else {
                result = asString(value);
            }
        }
        if (upper) {
            result = result.toUpperCase(Locale.getDefault());
        } else if (lower) {
            result = result.toLowerCase(Locale.getDefault());
        }
        return result;
    }

    public static boolean matches(CustomPillSpec.Rule rule, Context context) {
        Object value = resolve(rule.path, context);
        if (CustomPillSpec.OP_EMPTY.equals(rule.op)) {
            return value == null
                    || value instanceof JsonElement element && element.isJsonNull()
                    || TextUtils.isEmpty(asString(value));
        }
        double a = toNumber(value);
        double b = parseNumber(rule.value);
        boolean numbers = !Double.isNaN(a) && !Double.isNaN(b);
        boolean equals = numbers ? a == b : value != null && TextUtils.equals(asString(value), rule.value);
        return switch (rule.op) {
            case CustomPillSpec.OP_LESS -> numbers && a < b;
            case CustomPillSpec.OP_GREATER -> numbers && a > b;
            case CustomPillSpec.OP_EQUALS -> equals;
            case CustomPillSpec.OP_NOT_EQUALS -> !equals;
            default -> false;
        };
    }

    public static String label(String path) {
        switch (path) {
            case META_STATUS:
                return LocaleController.getString(R.string.CustomPillMetaStatus);
            case META_MS:
                return LocaleController.getString(R.string.CustomPillMetaLatency);
            case META_UPDATED:
                return LocaleController.getString(R.string.CustomPillMetaUpdated);
            case META_NOW:
                return LocaleController.getString(R.string.CustomPillMetaNow);
            case META_BODY:
                return LocaleController.getString(R.string.CustomPillMetaBody);
        }
        String trimmed = path.replaceAll("\\[[^\\]]*]$", "");
        int split = Math.max(trimmed.lastIndexOf('.'), trimmed.lastIndexOf('['));
        String label = (split >= 0 ? trimmed.substring(split + 1) : trimmed).replace("]", "").replace("\"", "");
        return TextUtils.isEmpty(label) ? path : label;
    }

    public static String smartToken(String path, @Nullable JsonElement value) {
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            double number = value.getAsDouble();
            String lower = path.toLowerCase(Locale.ROOT);
            if (number > 1e9 && (lower.contains("time") || lower.endsWith("_at") || lower.contains("unix") || lower.contains("date"))) {
                return "{" + path + "|ago}";
            }
            if (number != Math.rint(number)) {
                return "{" + path + "|fixed:2}";
            }
            if (Math.abs(number) >= 100000) {
                return "{" + path + "|compact}";
            }
        }
        return "{" + path + "}";
    }

    private static Object aggregate(String name, @Nullable String arg, JsonArray array) {
        switch (name) {
            case "count":
                return (double) array.size();
            case "first":
                return array.isEmpty() ? null : array.get(0);
            case "last":
                return array.isEmpty() ? null : array.get(array.size() - 1);
            case "join": {
                StringBuilder builder = new StringBuilder();
                String separator = arg != null ? arg : ", ";
                for (JsonElement element : array) {
                    if (builder.length() > 0) {
                        builder.append(separator);
                    }
                    builder.append(asString(element));
                }
                return builder.toString();
            }
        }
        double sum = 0, min = Double.NaN, max = Double.NaN;
        int count = 0;
        for (JsonElement element : array) {
            double number = toNumber(element);
            if (Double.isNaN(number)) {
                continue;
            }
            sum += number;
            min = Double.isNaN(min) ? number : Math.min(min, number);
            max = Double.isNaN(max) ? number : Math.max(max, number);
            count++;
        }
        return switch (name) {
            case "sum" -> sum;
            case "avg" -> count > 0 ? sum / count : Double.NaN;
            case "min" -> min;
            default -> max;
        };
    }

    private static boolean isMissing(@Nullable Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Double number) {
            return number.isNaN() || number.isInfinite();
        }
        if (value instanceof String string) {
            return string.isEmpty();
        }
        if (value instanceof JsonElement element) {
            if (element.isJsonNull()) {
                return true;
            }
            return element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() && element.getAsString().isEmpty();
        }
        return false;
    }

    private static boolean isBoolean(@Nullable Object value) {
        return value instanceof JsonPrimitive primitive && primitive.isBoolean();
    }

    public static double toNumber(@Nullable Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String string) {
            return parseNumber(string);
        }
        if (value instanceof JsonPrimitive primitive) {
            if (primitive.isNumber()) {
                try {
                    return primitive.getAsDouble();
                } catch (NumberFormatException e) {
                    return Double.NaN;
                }
            }
            if (primitive.isString()) {
                return parseNumber(primitive.getAsString());
            }
        }
        return Double.NaN;
    }

    public static double parseNumber(@Nullable String value) {
        if (value == null) {
            return Double.NaN;
        }
        String trimmed = value.trim();
        if (!NUMBER.matcher(trimmed).matches()) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static String asString(@Nullable Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Double number) {
            return plain(number);
        }
        if (value instanceof JsonElement element) {
            if (element.isJsonPrimitive()) {
                return element.getAsString();
            }
            if (element.isJsonNull()) {
                return "";
            }
            return element.isJsonArray() ? "[" + element.getAsJsonArray().size() + "]" : "{…}";
        }
        return String.valueOf(value);
    }

    private static long parseDate(@Nullable String value) {
        if (TextUtils.isEmpty(value)) {
            return -1;
        }
        String[] patterns = {"yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd"};
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setLenient(false);
                Date date = format.parse(value.trim());
                if (date != null) {
                    return date.getTime() / 1000;
                }
            } catch (Exception ignore) {
            }
        }
        return -1;
    }

    static String fixed(double value, int digits) {
        return BigDecimal.valueOf(value).setScale(digits, RoundingMode.HALF_UP).toPlainString();
    }

    static String plain(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return MISSING;
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    static String compact(double value, @Nullable Integer digits) {
        double abs = Math.abs(value);
        double[] bases = {1e12, 1e9, 1e6, 1e3};
        String[] suffixes = {"T", "B", "M", "K"};
        for (int i = 0; i < bases.length; i++) {
            if (abs >= bases[i]) {
                double x = value / bases[i];
                String number;
                if (digits != null) {
                    number = fixed(x, digits);
                } else if (Math.abs(x) >= 100) {
                    number = fixed(x, 0);
                } else {
                    number = fixed(x, 1);
                    if (number.endsWith(".0")) {
                        number = number.substring(0, number.length() - 2);
                    }
                }
                return number + suffixes[i];
            }
        }
        return digits != null ? fixed(value, digits) : plain(Math.round(value * 100) / 100.0);
    }

    private static boolean contains(String[] values, String value) {
        for (String v : values) {
            if (v.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
