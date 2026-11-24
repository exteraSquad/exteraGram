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
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JsonPath {

    private static final Pattern SEGMENT = Pattern.compile("\\[(-?\\d+|\\*)]|\\[\"([^\"]*)\"]|([^.\\[\\]]+)");
    private static final Pattern PLAIN_KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*");
    private static final int MAX_RESULTS = 10000;

    private JsonPath() {
    }

    @Nullable
    public static JsonElement resolve(@Nullable JsonElement root, @Nullable String path) {
        if (root == null || TextUtils.isEmpty(path)) {
            return null;
        }
        ArrayList<JsonElement> current = new ArrayList<>(1);
        current.add(root);
        boolean many = false;
        Matcher matcher = SEGMENT.matcher(path);
        while (matcher.find()) {
            ArrayList<JsonElement> next = new ArrayList<>();
            String index = matcher.group(1);
            String key = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            for (JsonElement element : current) {
                if (element == null || element.isJsonNull()) {
                    continue;
                }
                if (index != null) {
                    if (!element.isJsonArray()) {
                        continue;
                    }
                    JsonArray array = element.getAsJsonArray();
                    if ("*".equals(index)) {
                        many = true;
                        for (JsonElement item : array) {
                            if (next.size() >= MAX_RESULTS) {
                                break;
                            }
                            next.add(item);
                        }
                    } else {
                        int i;
                        try {
                            i = Integer.parseInt(index);
                        } catch (NumberFormatException e) {
                            continue;
                        }
                        if (i < 0) {
                            i += array.size();
                        }
                        if (i >= 0 && i < array.size()) {
                            next.add(array.get(i));
                        }
                    }
                } else if (element.isJsonArray() && "length".equals(key)) {
                    next.add(new JsonPrimitive(element.getAsJsonArray().size()));
                } else if (element.isJsonObject()) {
                    JsonElement child = element.getAsJsonObject().get(key);
                    if (child != null) {
                        next.add(child);
                    }
                }
            }
            current = next;
        }
        if (many) {
            JsonArray result = new JsonArray(current.size());
            for (JsonElement element : current) {
                result.add(element);
            }
            return result;
        }
        return current.isEmpty() ? null : current.get(0);
    }

    public static String child(String base, String key) {
        if (PLAIN_KEY.matcher(key).matches()) {
            return TextUtils.isEmpty(base) ? key : base + "." + key;
        }
        return base + "[\"" + key.replace("\"", "") + "\"]";
    }

    public static String index(String base, int index) {
        return base + "[" + index + "]";
    }
}
