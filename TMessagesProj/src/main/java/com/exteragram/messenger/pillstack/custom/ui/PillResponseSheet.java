/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.custom.ui;

import android.text.TextUtils;
import android.view.View;

import com.exteragram.messenger.pillstack.custom.CustomPillFetcher;
import com.exteragram.messenger.pillstack.custom.JsonPath;
import com.exteragram.messenger.pillstack.custom.PillTemplate;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

public class PillResponseSheet extends PillSheet {

    private static final int MAX_TREE_ROWS = 300;
    private static final int MAX_ARRAY_ITEMS = 30;

    private final CustomPillFetcher.Response response;
    private final Set<String> expanded;
    private final Utilities.Callback<String> onPick;

    public PillResponseSheet(BaseFragment fragment, CustomPillFetcher.Response response, Set<String> expanded, Utilities.Callback<String> onPick) {
        super(fragment, false, null);
        this.response = response;
        this.expanded = expanded;
        this.onPick = onPick;
        setSegmented(false);
        ready();
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.CustomPillResponse);
    }

    @Override
    protected void fillSheetItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(response.status + " · " + LocaleController.formatString(R.string.CustomPillMs, response.ms)));
        int[] count = {0};
        if (response.json != null && (response.json.isJsonObject() || response.json.isJsonArray())) {
            addChildren(items, response.json, "", 0, count);
        } else if (!TextUtils.isEmpty(response.body)) {
            String body = response.body.trim().replaceAll("\\s+", " ");
            if (body.length() > 120) {
                body = body.substring(0, 120) + "…";
            }
            items.add(CustomPillCells.JsonNodeCell.Factory.of(new CustomPillCells.JsonNode(PillTemplate.META_BODY, "", body, 0, CustomPillCells.NODE_TEXT, false)));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.CustomPillResponseInfo)));
    }

    private void addChildren(ArrayList<UItem> items, JsonElement element, String base, int depth, int[] count) {
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if (count[0] >= MAX_TREE_ROWS) {
                    return;
                }
                addNode(items, entry.getKey(), JsonPath.child(base, entry.getKey()), entry.getValue(), depth, count);
            }
        } else if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            int limit = Math.min(array.size(), MAX_ARRAY_ITEMS);
            for (int i = 0; i < limit; i++) {
                if (count[0] >= MAX_TREE_ROWS) {
                    return;
                }
                addNode(items, "[" + i + "]", JsonPath.index(base, i), array.get(i), depth, count);
            }
            if (array.size() > limit) {
                count[0]++;
                items.add(CustomPillCells.JsonNodeCell.Factory.of(new CustomPillCells.JsonNode(base + "[more]", "", LocaleController.formatString(R.string.CustomPillResponseMore, array.size() - limit), depth, CustomPillCells.NODE_MORE, false)));
            }
        }
    }

    private void addNode(ArrayList<UItem> items, String key, String path, JsonElement value, int depth, int[] count) {
        count[0]++;
        if (value.isJsonObject() || value.isJsonArray()) {
            boolean isExpanded = expanded.contains(path);
            int size = value.isJsonObject() ? value.getAsJsonObject().size() : value.getAsJsonArray().size();
            String summary = value.isJsonObject() ? "{ " + size + " }" : "[ " + size + " ]";
            items.add(CustomPillCells.JsonNodeCell.Factory.of(new CustomPillCells.JsonNode(path, key, summary, depth, value.isJsonObject() ? CustomPillCells.NODE_OBJECT : CustomPillCells.NODE_ARRAY, isExpanded)));
            if (isExpanded) {
                addChildren(items, value, path, depth + 1, count);
            }
            return;
        }
        int kind;
        String text;
        if (value.isJsonNull()) {
            kind = CustomPillCells.NODE_NULL;
            text = "null";
        } else {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                kind = CustomPillCells.NODE_BOOLEAN;
                text = primitive.getAsString();
            } else if (primitive.isNumber()) {
                kind = CustomPillCells.NODE_NUMBER;
                text = primitive.getAsString();
            } else {
                kind = CustomPillCells.NODE_STRING;
                String string = primitive.getAsString().replaceAll("\\s+", " ");
                text = "\"" + (string.length() > 80 ? string.substring(0, 80) + "…" : string) + "\"";
            }
        }
        items.add(CustomPillCells.JsonNodeCell.Factory.of(new CustomPillCells.JsonNode(path, key, text, depth, kind, false)));
    }

    @Override
    protected void onItemClick(UItem item, View view) {
        if (!(item.object instanceof CustomPillCells.JsonNode node)) {
            return;
        }
        switch (node.kind()) {
            case CustomPillCells.NODE_OBJECT, CustomPillCells.NODE_ARRAY -> {
                if (!expanded.remove(node.path())) {
                    expanded.add(node.path());
                }
                update();
            }
            case CustomPillCells.NODE_MORE -> {
            }
            case CustomPillCells.NODE_TEXT -> pick("{" + PillTemplate.META_BODY + "}");
            default -> pick(PillTemplate.smartToken(node.path(), JsonPath.resolve(response.json, node.path())));
        }
    }

    private void pick(String token) {
        dismiss();
        onPick.run(token);
    }
}
