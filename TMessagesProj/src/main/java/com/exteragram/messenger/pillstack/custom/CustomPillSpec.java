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
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

public class CustomPillSpec {

    public static final int FORMAT_VERSION = 1;

    public static final String ICON_RES = "res";
    public static final String ICON_EMOJI = "emoji";
    public static final String ICON_CUSTOM_EMOJI = "custom_emoji";

    public static final String COLOR_NEUTRAL = "NEUTRAL";

    public static final String METHOD_GET = "GET";
    public static final String METHOD_POST = "POST";

    public static final String TAP_REFRESH = "refresh";
    public static final String TAP_LINK = "link";
    public static final String TAP_COPY = "copy";
    public static final String TAP_DETAILS = "details";
    public static final String TAP_NONE = "none";
    public static final String[] TAPS = {TAP_REFRESH, TAP_LINK, TAP_COPY, TAP_DETAILS, TAP_NONE};

    public static final String OP_LESS = "<";
    public static final String OP_GREATER = ">";
    public static final String OP_EQUALS = "=";
    public static final String OP_NOT_EQUALS = "!=";
    public static final String OP_EMPTY = "empty";
    public static final String[] OPS = {OP_LESS, OP_GREATER, OP_EQUALS, OP_NOT_EQUALS, OP_EMPTY};

    public static final int[] REFRESH_INTERVALS = {0, 60, 300, 900, 1800, 3600, 21600, 86400};
    public static final int DEFAULT_REFRESH = 300;

    public static final String DEFAULT_ICON = "msg_filled_datausage";

    public static final int MAX_NAME = 32;
    public static final int MAX_TEXT = 256;
    public static final int MAX_URL = 2048;
    public static final int MAX_BODY = 4096;
    public static final int MAX_HEADERS = 20;
    public static final int MAX_HEADER_NAME = 64;
    public static final int MAX_HEADER_VALUE = 1024;
    public static final int MAX_SECRETS = 10;
    public static final int MAX_RULES = 10;
    public static final int MAX_RULE_VALUE = 128;
    public static final int MAX_PATH = 256;

    private static final Pattern RES_NAME = Pattern.compile("[a-z0-9_]{1,128}");
    private static final Pattern HEADER_NAME = Pattern.compile("[A-Za-z0-9!#$%&'*+.^_`|~-]{1," + MAX_HEADER_NAME + "}");
    public static final Pattern SECRET_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,31}");

    public int id;
    public String name = "";
    public Icon icon = new Icon(ICON_RES, DEFAULT_ICON);
    public String color = COLOR_NEUTRAL;
    public boolean http = true;
    public String method = METHOD_GET;
    public String url = "";
    public final ArrayList<Header> headers = new ArrayList<>();
    public String body = "";
    public int refresh = DEFAULT_REFRESH;
    public final ArrayList<String> secrets = new ArrayList<>();
    public String text = "";
    public final ArrayList<Rule> rules = new ArrayList<>();
    public String tap = TAP_REFRESH;
    public String link = "";

    public static final class Icon {
        public final String type;
        public final String value;

        public Icon(String type, String value) {
            this.type = type;
            this.value = value;
        }

        public boolean isRes() {
            return ICON_RES.equals(type);
        }

        public boolean isEmoji() {
            return ICON_EMOJI.equals(type);
        }

        public boolean isCustomEmoji() {
            return ICON_CUSTOM_EMOJI.equals(type);
        }

        public long getDocumentId() {
            try {
                return Long.parseLong(value);
            } catch (Exception e) {
                return 0;
            }
        }

        JsonObject toJson() {
            JsonObject object = new JsonObject();
            object.addProperty("type", type);
            object.addProperty("value", value);
            return object;
        }

        @Nullable
        static Icon fromJson(@Nullable JsonElement element) {
            if (element == null || !element.isJsonObject()) {
                return null;
            }
            JsonObject object = element.getAsJsonObject();
            String type = getString(object, "type", 16);
            String value = getString(object, "value", 128);
            if (type == null || value == null) {
                return null;
            }
            switch (type) {
                case ICON_RES -> {
                    return RES_NAME.matcher(value).matches() ? new Icon(type, value) : null;
                }
                case ICON_EMOJI -> {
                    return value.length() <= 32 && !TextUtils.isEmpty(value.trim()) ? new Icon(type, value) : null;
                }
                case ICON_CUSTOM_EMOJI -> {
                    Icon icon = new Icon(type, value);
                    return icon.getDocumentId() != 0 ? icon : null;
                }
            }
            return null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Icon other)) return false;
            return TextUtils.equals(type, other.type) && TextUtils.equals(value, other.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, value);
        }
    }

    public static final class Header {
        public String name;
        public String value;

        public Header(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

    public static final class Rule {
        public String path = "";
        public String op = OP_LESS;
        public String value = "";
        @Nullable
        public String color;
        @Nullable
        public Icon icon;
        @Nullable
        public String text;

        public Rule copy() {
            Rule rule = new Rule();
            rule.path = path;
            rule.op = op;
            rule.value = value;
            rule.color = color;
            rule.icon = icon;
            rule.text = text;
            return rule;
        }

        JsonObject toJson() {
            JsonObject object = new JsonObject();
            JsonArray condition = new JsonArray();
            condition.add(path);
            condition.add(op);
            condition.add(value);
            object.add("if", condition);
            JsonObject then = new JsonObject();
            if (color != null) {
                then.addProperty("color", color);
            }
            if (icon != null) {
                then.add("icon", icon.toJson());
            }
            if (!TextUtils.isEmpty(text)) {
                then.addProperty("text", text);
            }
            object.add("then", then);
            return object;
        }

        @Nullable
        static Rule fromJson(JsonElement element) {
            if (element == null || !element.isJsonObject()) {
                return null;
            }
            JsonObject object = element.getAsJsonObject();
            JsonElement condition = object.get("if");
            if (condition == null || !condition.isJsonArray() || condition.getAsJsonArray().size() < 2) {
                return null;
            }
            JsonArray array = condition.getAsJsonArray();
            Rule rule = new Rule();
            rule.path = asString(array.get(0), MAX_PATH);
            rule.op = asString(array.get(1), 8);
            rule.value = array.size() > 2 ? asString(array.get(2), MAX_RULE_VALUE) : "";
            if (TextUtils.isEmpty(rule.path) || !contains(OPS, rule.op)) {
                return null;
            }
            if (rule.value == null) {
                rule.value = "";
            }
            JsonElement then = object.get("then");
            if (then != null && then.isJsonObject()) {
                JsonObject thenObject = then.getAsJsonObject();
                String color = getString(thenObject, "color", 32);
                rule.color = PillColors.isValid(color) ? color : null;
                rule.icon = Icon.fromJson(thenObject.get("icon"));
                rule.text = getString(thenObject, "text", MAX_TEXT);
            }
            return rule;
        }
    }

    public boolean isColored() {
        return !COLOR_NEUTRAL.equals(color);
    }

    public int getRefreshInterval() {
        return http ? refresh : 0;
    }

    public CustomPillSpec copy() {
        CustomPillSpec spec = new CustomPillSpec();
        spec.id = id;
        spec.name = name;
        spec.icon = icon;
        spec.color = color;
        spec.http = http;
        spec.method = method;
        spec.url = url;
        for (Header header : headers) {
            spec.headers.add(new Header(header.name, header.value));
        }
        spec.body = body;
        spec.refresh = refresh;
        spec.secrets.addAll(secrets);
        spec.text = text;
        for (Rule rule : rules) {
            spec.rules.add(rule.copy());
        }
        spec.tap = tap;
        spec.link = link;
        return spec;
    }

    public JsonObject toJson(boolean withId) {
        JsonObject object = new JsonObject();
        object.addProperty("v", FORMAT_VERSION);
        if (withId) {
            object.addProperty("id", id);
        }
        object.addProperty("name", name);
        object.add("icon", icon.toJson());
        object.addProperty("color", color);
        if (http) {
            JsonObject request = new JsonObject();
            request.addProperty("method", method);
            request.addProperty("url", url);
            if (!headers.isEmpty()) {
                JsonObject headersObject = new JsonObject();
                for (Header header : headers) {
                    headersObject.addProperty(header.name, header.value);
                }
                request.add("headers", headersObject);
            }
            if (METHOD_POST.equals(method) && !TextUtils.isEmpty(body)) {
                request.addProperty("body", body);
            }
            object.add("request", request);
            object.addProperty("refresh", refresh);
            if (!secrets.isEmpty()) {
                JsonArray secretsArray = new JsonArray();
                for (String secret : secrets) {
                    secretsArray.add(secret);
                }
                object.add("secrets", secretsArray);
            }
        }
        object.addProperty("text", text);
        if (!rules.isEmpty()) {
            JsonArray rulesArray = new JsonArray();
            for (Rule rule : rules) {
                rulesArray.add(rule.toJson());
            }
            object.add("rules", rulesArray);
        }
        if (TAP_LINK.equals(tap)) {
            JsonObject tapObject = new JsonObject();
            tapObject.addProperty("open", link);
            object.add("tap", tapObject);
        } else {
            object.addProperty("tap", tap);
        }
        return object;
    }

    @Nullable
    public static CustomPillSpec fromJson(@Nullable JsonElement element, boolean withId) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        int version = getInt(object, "v", FORMAT_VERSION);
        if (version > FORMAT_VERSION || version < 1) {
            return null;
        }
        CustomPillSpec spec = new CustomPillSpec();
        if (withId) {
            spec.id = getInt(object, "id", 0);
        }
        String name = getString(object, "name", 256);
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(name.trim())) {
            return null;
        }
        spec.name = clip(name.trim(), MAX_NAME);
        Icon icon = Icon.fromJson(object.get("icon"));
        if (icon != null) {
            spec.icon = icon;
        }
        String color = getString(object, "color", 32);
        spec.color = PillColors.isValid(color) ? color : COLOR_NEUTRAL;

        JsonElement requestElement = object.get("request");
        if (requestElement != null && requestElement.isJsonObject()) {
            JsonObject request = requestElement.getAsJsonObject();
            spec.http = true;
            String method = getString(request, "method", 8);
            spec.method = METHOD_POST.equalsIgnoreCase(method) ? METHOD_POST : METHOD_GET;
            String url = getString(request, "url", MAX_URL);
            if (!isValidUrl(url)) {
                return null;
            }
            spec.url = url.trim();
            JsonElement headersElement = request.get("headers");
            if (headersElement != null && headersElement.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : headersElement.getAsJsonObject().entrySet()) {
                    if (spec.headers.size() >= MAX_HEADERS) {
                        break;
                    }
                    String value = asString(entry.getValue(), MAX_HEADER_VALUE);
                    if (isValidHeaderName(entry.getKey()) && value != null && isValidHeaderValue(value)) {
                        spec.headers.add(new Header(entry.getKey(), value));
                    }
                }
            }
            String body = getString(request, "body", MAX_BODY);
            spec.body = body != null ? body : "";
            spec.refresh = normalizeRefresh(getInt(object, "refresh", DEFAULT_REFRESH));
            JsonElement secretsElement = object.get("secrets");
            if (secretsElement != null && secretsElement.isJsonArray()) {
                for (JsonElement secretElement : secretsElement.getAsJsonArray()) {
                    String secret = asString(secretElement, 32);
                    if (secret != null && SECRET_NAME.matcher(secret).matches() && !spec.secrets.contains(secret) && spec.secrets.size() < MAX_SECRETS) {
                        spec.secrets.add(secret);
                    }
                }
            }
        } else {
            spec.http = false;
            spec.url = "";
        }

        String text = getString(object, "text", MAX_TEXT);
        spec.text = text != null ? text : "";

        JsonElement rulesElement = object.get("rules");
        if (rulesElement != null && rulesElement.isJsonArray()) {
            for (JsonElement ruleElement : rulesElement.getAsJsonArray()) {
                if (spec.rules.size() >= MAX_RULES) {
                    break;
                }
                Rule rule = Rule.fromJson(ruleElement);
                if (rule != null) {
                    spec.rules.add(rule);
                }
            }
        }

        JsonElement tapElement = object.get("tap");
        if (tapElement != null && tapElement.isJsonObject()) {
            String link = getString(tapElement.getAsJsonObject(), "open", MAX_URL);
            if (isValidLink(link)) {
                spec.tap = TAP_LINK;
                spec.link = link.trim();
            } else {
                spec.tap = TAP_NONE;
            }
        } else {
            String tap = tapElement != null ? asString(tapElement, 16) : null;
            spec.tap = tap != null && contains(TAPS, tap) && !TAP_LINK.equals(tap) ? tap : TAP_REFRESH;
        }
        return spec;
    }

    public static int normalizeRefresh(int refresh) {
        if (refresh <= 0) {
            return 0;
        }
        return Math.max(60, Math.min(refresh, 86400));
    }

    public static boolean isValidUrl(@Nullable String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        String trimmed = url.trim();
        int start = schemeLength(trimmed);
        if (trimmed.length() > MAX_URL || start == 0 || trimmed.length() <= start) {
            return false;
        }
        char first = trimmed.charAt(start);
        return first != '/' && first != '?' && first != '#' && !Character.isWhitespace(first);
    }

    private static int schemeLength(String url) {
        if (url.regionMatches(true, 0, "https://", 0, 8)) {
            return 8;
        }
        if (url.regionMatches(true, 0, "http://", 0, 7)) {
            return 7;
        }
        return 0;
    }

    public boolean usesSecrets() {
        if (containsSecret(url) || METHOD_POST.equals(method) && containsSecret(body)) {
            return true;
        }
        for (Header header : headers) {
            if (containsSecret(header.value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsSecret(@Nullable String value) {
        return value != null && value.contains("{" + CustomPillFetcher.VAR_SECRET_PREFIX);
    }

    public boolean hasInsecureSecrets() {
        return http && isValidUrl(url) && !isSecureUrl(url) && usesSecrets();
    }

    public static boolean isSecureUrl(@Nullable String url) {
        if (url == null) {
            return false;
        }
        String trimmed = url.trim();
        if (trimmed.regionMatches(true, 0, "https://", 0, 8)) {
            return true;
        }
        HttpUrl parsed = HttpUrl.parse(trimmed);
        return parsed != null && isLocalHost(parsed.host());
    }

    public static boolean isLocalHost(@Nullable String host) {
        if (TextUtils.isEmpty(host)) {
            return false;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        if (lower.equals("localhost") || lower.endsWith(".localhost") || lower.endsWith(".local") || lower.endsWith(".lan")
                || lower.endsWith(".home.arpa") || lower.endsWith(".internal") || lower.endsWith(".ts.net")) {
            return true;
        }
        if (lower.indexOf(':') >= 0) {
            return lower.equals("::1") || lower.startsWith("fc") || lower.startsWith("fd") || lower.startsWith("fe8") || lower.startsWith("fe9") || lower.startsWith("fea") || lower.startsWith("feb");
        }
        String[] parts = lower.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            try {
                octets[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return false;
            }
            if (octets[i] < 0 || octets[i] > 255) {
                return false;
            }
        }
        int a = octets[0], b = octets[1];
        return a == 10 || a == 127 || a == 192 && b == 168 || a == 172 && b >= 16 && b <= 31 || a == 169 && b == 254 || a == 100 && b >= 64 && b <= 127;
    }

    public static boolean isValidLink(@Nullable String link) {
        if (TextUtils.isEmpty(link)) {
            return false;
        }
        String trimmed = link.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return trimmed.length() <= MAX_URL && ((lower.startsWith("https://") && trimmed.length() > 8 || lower.startsWith("http://") && trimmed.length() > 7) || lower.startsWith("tg://") && trimmed.length() > 5);
    }

    public static boolean isValidHeaderName(@Nullable String name) {
        return name != null && HEADER_NAME.matcher(name).matches();
    }

    public static boolean isValidHeaderValue(@Nullable String value) {
        return value != null && value.length() <= MAX_HEADER_VALUE && value.indexOf('\n') < 0 && value.indexOf('\r') < 0;
    }

    public static boolean contains(String[] values, String value) {
        for (String v : values) {
            if (v.equals(value)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    static String clip(@NonNull String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        int end = max;
        if (Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    @Nullable
    static String getString(JsonObject object, String key, int maxLength) {
        return asString(object.get(key), maxLength);
    }

    @Nullable
    static String asString(@Nullable JsonElement element, int maxLength) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        String value = element.getAsString();
        return value.length() <= maxLength ? value : null;
    }

    static int getInt(JsonObject object, String key, int defaultValue) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return defaultValue;
        }
        try {
            return element.getAsInt();
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
