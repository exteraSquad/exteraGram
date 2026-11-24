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

import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;

public class CustomPillResult {

    public static final int STATE_DATA = 0;
    public static final int STATE_ERROR = 1;
    public static final int STATE_LOCATION = 2;

    private static final int MAX_TEXT = 64;

    public int state;
    public String text = "";
    public String color = CustomPillSpec.COLOR_NEUTRAL;
    public CustomPillSpec.Icon icon;
    public int error;
    public int status;
    public long ms;
    public long bytes;
    public long time;
    public String link = "";
    public final ArrayList<String[]> details = new ArrayList<>();

    public static CustomPillResult build(CustomPillSpec spec, @Nullable CustomPillFetcher.Response response) {
        CustomPillResult result = new CustomPillResult();
        result.icon = spec.icon;
        result.color = spec.color;
        if (spec.http) {
            if (response == null) {
                result.state = STATE_ERROR;
                result.error = CustomPillFetcher.ERROR_NETWORK;
                return result;
            }
            result.status = response.status;
            result.ms = response.ms;
            result.bytes = response.bytes;
            result.time = response.time;
            if (response.error == CustomPillFetcher.ERROR_LOCATION) {
                result.state = STATE_LOCATION;
                result.error = response.error;
                return result;
            }
            if (response.error != CustomPillFetcher.ERROR_NONE || !response.isSuccessful() && !usesStatus(spec)) {
                result.state = STATE_ERROR;
                result.error = response.error;
                return result;
            }
        } else {
            result.time = System.currentTimeMillis() / 1000;
        }
        PillTemplate.Context context = createContext(spec, response);
        String text = PillTemplate.render(spec.text, context);
        for (CustomPillSpec.Rule rule : spec.rules) {
            if (PillTemplate.matches(rule, context)) {
                if (rule.color != null) {
                    result.color = rule.color;
                }
                if (rule.icon != null) {
                    result.icon = rule.icon;
                }
                if (!TextUtils.isEmpty(rule.text)) {
                    text = PillTemplate.render(rule.text, context);
                }
                break;
            }
        }
        result.text = normalizeText(text);
        for (PillTemplate.Token token : PillTemplate.parse(spec.text)) {
            result.details.add(new String[]{PillTemplate.label(token.path), PillTemplate.evaluate(token, context)});
        }
        if (CustomPillSpec.TAP_LINK.equals(spec.tap)) {
            result.link = PillTemplate.render(spec.link, context).trim();
        }
        return result;
    }

    public static CustomPillResult buildPreview(CustomPillSpec spec, @Nullable CustomPillFetcher.Response response) {
        if (!spec.http || response != null) {
            return build(spec, response);
        }
        CustomPillFetcher.Response empty = new CustomPillFetcher.Response();
        empty.status = 200;
        empty.time = System.currentTimeMillis() / 1000;
        return build(spec, empty);
    }

    public static PillTemplate.Context createContext(CustomPillSpec spec, @Nullable CustomPillFetcher.Response response) {
        PillTemplate.Context context = PillTemplate.Context.now();
        if (spec.http && response != null && response.error == CustomPillFetcher.ERROR_NONE) {
            context.hasResponse = true;
            context.json = response.json;
            context.body = response.body;
            context.status = response.status;
            context.ms = response.ms;
            context.updated = response.time;
        }
        return context;
    }

    public static boolean usesStatus(CustomPillSpec spec) {
        if (PillTemplate.usesMeta(spec.text, PillTemplate.META_STATUS)) {
            return true;
        }
        for (CustomPillSpec.Rule rule : spec.rules) {
            if (PillTemplate.META_STATUS.equals(rule.path) || PillTemplate.usesMeta(rule.text, PillTemplate.META_STATUS)) {
                return true;
            }
        }
        return false;
    }

    public static boolean usesClock(CustomPillSpec spec) {
        return PillTemplate.usesMeta(spec.text, PillTemplate.META_NOW);
    }

    private static String normalizeText(String text) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return PillTemplate.MISSING;
        }
        return CustomPillSpec.clip(normalized, MAX_TEXT);
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("state", state);
        object.addProperty("text", text);
        object.addProperty("color", color);
        if (icon != null) {
            object.add("icon", icon.toJson());
        }
        object.addProperty("error", error);
        object.addProperty("status", status);
        object.addProperty("ms", ms);
        object.addProperty("bytes", bytes);
        object.addProperty("time", time);
        object.addProperty("link", link);
        JsonArray detailsArray = new JsonArray();
        for (String[] detail : details) {
            JsonArray pair = new JsonArray();
            pair.add(detail[0]);
            pair.add(detail[1]);
            detailsArray.add(pair);
        }
        object.add("details", detailsArray);
        return object;
    }

    @Nullable
    public static CustomPillResult fromJson(@Nullable JsonElement element, CustomPillSpec spec) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        try {
            JsonObject object = element.getAsJsonObject();
            CustomPillResult result = new CustomPillResult();
            result.state = CustomPillSpec.getInt(object, "state", STATE_DATA);
            String text = CustomPillSpec.getString(object, "text", MAX_TEXT);
            result.text = text != null ? text : PillTemplate.MISSING;
            String color = CustomPillSpec.getString(object, "color", 32);
            result.color = PillColors.isValid(color) ? color : spec.color;
            CustomPillSpec.Icon icon = CustomPillSpec.Icon.fromJson(object.get("icon"));
            result.icon = icon != null ? icon : spec.icon;
            result.error = CustomPillSpec.getInt(object, "error", 0);
            result.status = CustomPillSpec.getInt(object, "status", 0);
            result.ms = object.has("ms") ? object.get("ms").getAsLong() : 0;
            result.bytes = object.has("bytes") ? object.get("bytes").getAsLong() : 0;
            result.time = object.has("time") ? object.get("time").getAsLong() : 0;
            String link = CustomPillSpec.getString(object, "link", CustomPillSpec.MAX_URL);
            result.link = link != null ? link : "";
            JsonElement detailsElement = object.get("details");
            if (detailsElement != null && detailsElement.isJsonArray()) {
                for (JsonElement pair : detailsElement.getAsJsonArray()) {
                    if (pair.isJsonArray() && pair.getAsJsonArray().size() == 2) {
                        result.details.add(new String[]{pair.getAsJsonArray().get(0).getAsString(), pair.getAsJsonArray().get(1).getAsString()});
                    }
                }
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }
}
