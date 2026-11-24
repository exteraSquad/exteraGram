/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.custom;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.SparseArray;

import androidx.annotation.Nullable;

import com.exteragram.messenger.backup.PreferencesUtils;
import com.exteragram.messenger.pillstack.core.PillRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CustomPillStore {

    public static final String CONFIG_NAME = "custompills";
    public static final String KEY_PILLS = "pills";
    private static final String KEY_SECRETS = "secrets";
    private static final String KEY_CACHE_PREFIX = "cache_";

    public static final int ID_MIN = 10000;
    public static final int ID_MAX = 10999;
    public static final int MAX_PILLS = 50;

    private static final Object sync = new Object();
    private static final ArrayList<CustomPillSpec> pills = new ArrayList<>();
    private static final SparseArray<CustomPillResult> results = new SparseArray<>();
    private static final SparseArray<CustomPillFetcher.Response> responses = new SparseArray<>();
    private static HashMap<Integer, HashMap<String, String>> secrets;
    private static boolean loaded;

    private CustomPillStore() {
    }

    private static SharedPreferences getPreferences() {
        return PreferencesUtils.getPreferences(CONFIG_NAME);
    }

    private static void ensureLoaded() {
        synchronized (sync) {
            if (loaded) {
                return;
            }
            loaded = true;
            pills.clear();
            String data = getPreferences().getString(KEY_PILLS, null);
            pills.addAll(parseList(data));
        }
    }

    public static ArrayList<CustomPillSpec> parseList(@Nullable String data) {
        ArrayList<CustomPillSpec> list = new ArrayList<>();
        if (TextUtils.isEmpty(data)) {
            return list;
        }
        try {
            JsonElement element = JsonParser.parseString(data);
            if (!element.isJsonArray()) {
                return list;
            }
            for (JsonElement item : element.getAsJsonArray()) {
                CustomPillSpec spec = CustomPillSpec.fromJson(item, true);
                if (spec != null && isCustomId(spec.id) && find(list, spec.id) == null && list.size() < MAX_PILLS) {
                    list.add(spec);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return list;
    }

    public static boolean isCustomId(int id) {
        return id >= ID_MIN && id <= ID_MAX;
    }

    public static List<CustomPillSpec> getAll() {
        ensureLoaded();
        synchronized (sync) {
            return new ArrayList<>(pills);
        }
    }

    public static int getCount() {
        ensureLoaded();
        synchronized (sync) {
            return pills.size();
        }
    }

    @Nullable
    public static CustomPillSpec get(int id) {
        ensureLoaded();
        synchronized (sync) {
            return find(pills, id);
        }
    }

    @Nullable
    private static CustomPillSpec find(List<CustomPillSpec> list, int id) {
        for (CustomPillSpec spec : list) {
            if (spec.id == id) {
                return spec;
            }
        }
        return null;
    }

    public static boolean canCreate() {
        return getCount() < MAX_PILLS;
    }

    public static int allocateId() {
        ensureLoaded();
        synchronized (sync) {
            if (pills.size() >= MAX_PILLS) {
                return 0;
            }
            for (int id = ID_MIN; id <= ID_MAX; id++) {
                if (find(pills, id) == null && !PillRegistry.isRegistered(id)) {
                    return id;
                }
            }
        }
        return 0;
    }

    public static void registerAll() {
        for (CustomPillSpec spec : getAll()) {
            PillRegistry.register(createInfo(spec));
        }
    }

    public static PillRegistry.PillInfo createInfo(CustomPillSpec spec) {
        PillColors colors = PillColors.get(spec.color);
        final int id = spec.id;
        return new PillRegistry.PillInfo(id, spec.name, resolveIconRes(spec.icon), colors.top(), colors.bottom(),
                (context, resourcesProvider) -> new CustomPill(context, resourcesProvider, id));
    }

    public static int resolveIconRes(@Nullable CustomPillSpec.Icon icon) {
        if (icon != null && icon.isRes()) {
            int res = resolveIconRes(icon.value);
            if (res != 0) {
                return res;
            }
        }
        return R.drawable.msg_filled_datausage;
    }

    public static int resolveIconRes(String name) {
        Context context = ApplicationLoader.applicationContext;
        try {
            return context.getResources().getIdentifier(name, "drawable", context.getPackageName());
        } catch (Exception e) {
            return 0;
        }
    }

    public static void save(CustomPillSpec spec, boolean activate, @Nullable Map<String, String> newSecrets, @Nullable CustomPillFetcher.Response response) {
        ensureLoaded();
        CustomPillSpec copy = spec.copy();
        boolean added;
        boolean requestChanged;
        synchronized (sync) {
            int index = -1;
            for (int i = 0; i < pills.size(); i++) {
                if (pills.get(i).id == copy.id) {
                    index = i;
                    break;
                }
            }
            added = index < 0;
            requestChanged = added || !requestSignature(pills.get(index)).equals(requestSignature(copy));
            if (added) {
                pills.add(copy);
            } else {
                pills.set(index, copy);
            }
            persist();
        }
        boolean secretsChanged = newSecrets != null && !getSecrets(copy.id).equals(newSecrets);
        if (secretsChanged) {
            setSecrets(copy.id, newSecrets);
        }
        if (response != null && copy.http) {
            responses.put(copy.id, response);
        } else if (requestChanged || secretsChanged) {
            responses.remove(copy.id);
        }
        CustomPillFetcher.Response last = copy.http ? responses.get(copy.id) : null;
        if (last != null || !copy.http) {
            putResult(copy.id, CustomPillResult.build(copy, last));
        } else {
            clearResult(copy.id);
        }
        PillRegistry.register(createInfo(copy));
        if (added && activate) {
            PillRegistry.activatePill(copy.id);
        }
    }

    public static void delete(int id) {
        ensureLoaded();
        synchronized (sync) {
            CustomPillSpec spec = find(pills, id);
            if (spec == null) {
                return;
            }
            pills.remove(spec);
            persist();
        }
        responses.remove(id);
        clearResult(id);
        setSecrets(id, null);
        PillRegistry.unregister(id);
    }

    public static void move(int fromId, int toId) {
        ensureLoaded();
        synchronized (sync) {
            CustomPillSpec from = find(pills, fromId);
            CustomPillSpec to = find(pills, toId);
            if (from == null || to == null) {
                return;
            }
            int toIndex = pills.indexOf(to);
            pills.remove(from);
            pills.add(toIndex, from);
            persist();
        }
    }

    public static String requestSignature(CustomPillSpec spec) {
        JsonObject object = spec.toJson(false);
        JsonObject signature = new JsonObject();
        JsonElement request = object.get("request");
        if (request != null) {
            signature.add("request", request);
        }
        JsonElement secretNames = object.get("secrets");
        if (secretNames != null) {
            signature.add("secrets", secretNames);
        }
        return signature.toString();
    }

    private static void persist() {
        JsonArray array = new JsonArray();
        for (CustomPillSpec spec : pills) {
            array.add(spec.toJson(true));
        }
        getPreferences().edit().putString(KEY_PILLS, array.toString()).apply();
    }

    public static void reload() {
        List<CustomPillSpec> old = getAll();
        synchronized (sync) {
            loaded = false;
            secrets = null;
        }
        results.clear();
        responses.clear();
        SharedPreferences.Editor editor = getPreferences().edit();
        for (String key : getPreferences().getAll().keySet()) {
            if (key.startsWith(KEY_CACHE_PREFIX)) {
                editor.remove(key);
            }
        }
        editor.apply();
        ensureLoaded();
        PillRegistry.beginTransaction();
        for (CustomPillSpec spec : old) {
            PillRegistry.unregister(spec.id);
        }
        registerAll();
        PillRegistry.endTransaction();
    }

    private static void ensureSecretsLoaded() {
        synchronized (sync) {
            if (secrets != null) {
                return;
            }
            secrets = new HashMap<>();
            String data = getPreferences().getString(KEY_SECRETS, null);
            if (TextUtils.isEmpty(data)) {
                return;
            }
            try {
                JsonObject object = JsonParser.parseString(data).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    HashMap<String, String> values = new HashMap<>();
                    for (Map.Entry<String, JsonElement> value : entry.getValue().getAsJsonObject().entrySet()) {
                        values.put(value.getKey(), value.getValue().getAsString());
                    }
                    secrets.put(Integer.parseInt(entry.getKey()), values);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public static HashMap<String, String> getSecrets(int id) {
        ensureSecretsLoaded();
        synchronized (sync) {
            HashMap<String, String> values = secrets.get(id);
            return values != null ? new HashMap<>(values) : new HashMap<>();
        }
    }

    public static void setSecrets(int id, @Nullable Map<String, String> values) {
        ensureSecretsLoaded();
        synchronized (sync) {
            if (values == null || values.isEmpty()) {
                secrets.remove(id);
            } else {
                secrets.put(id, new HashMap<>(values));
            }
            JsonObject object = new JsonObject();
            for (Map.Entry<Integer, HashMap<String, String>> entry : secrets.entrySet()) {
                JsonObject valuesObject = new JsonObject();
                for (Map.Entry<String, String> value : entry.getValue().entrySet()) {
                    valuesObject.addProperty(value.getKey(), value.getValue());
                }
                object.add(String.valueOf(entry.getKey()), valuesObject);
            }
            getPreferences().edit().putString(KEY_SECRETS, object.toString()).apply();
        }
    }

    public static boolean hasMissingSecrets(CustomPillSpec spec) {
        if (!spec.http || spec.secrets.isEmpty()) {
            return false;
        }
        HashMap<String, String> values = getSecrets(spec.id);
        for (String name : spec.secrets) {
            if (TextUtils.isEmpty(values.get(name))) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static CustomPillResult getResult(int id) {
        CustomPillResult result = results.get(id);
        if (result != null) {
            return result;
        }
        CustomPillSpec spec = get(id);
        if (spec == null) {
            return null;
        }
        String data = getPreferences().getString(KEY_CACHE_PREFIX + id, null);
        if (TextUtils.isEmpty(data)) {
            return null;
        }
        try {
            result = CustomPillResult.fromJson(JsonParser.parseString(data), spec);
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (result != null) {
            results.put(id, result);
        }
        return result;
    }

    public static void putResult(int id, CustomPillResult result) {
        results.put(id, result);
        if (result.state == CustomPillResult.STATE_DATA) {
            getPreferences().edit().putString(KEY_CACHE_PREFIX + id, result.toJson().toString()).apply();
        }
    }

    public static void clearResult(int id) {
        results.remove(id);
        getPreferences().edit().remove(KEY_CACHE_PREFIX + id).apply();
    }

    public static void putResponse(int id, CustomPillFetcher.Response response) {
        responses.put(id, response);
    }

    @Nullable
    public static CustomPillFetcher.Response getResponse(int id) {
        return responses.get(id);
    }
}
