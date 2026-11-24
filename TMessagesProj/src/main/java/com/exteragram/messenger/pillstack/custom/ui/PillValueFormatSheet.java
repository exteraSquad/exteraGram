/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.custom.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.exteragram.messenger.pillstack.custom.PillTemplate;
import com.google.gson.JsonElement;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.EditTextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PillValueFormatSheet extends PillSheet {

    private static final int ITEM_DIGITS = 1;
    private static final int ITEM_COMPACT = 2;
    private static final int ITEM_SIGN = 3;
    private static final int ITEM_PERCENT = 4;
    private static final int ITEM_DIVIDE = 5;
    private static final int ITEM_REMOVE = 6;

    private static final String[] DIGITS = {"auto", "0", "1", "2", "3", "4"};
    private static final String[] DIVIDERS = {"", "100", "1000", "1e6", "1e9"};
    private static final List<String> MANAGED = Arrays.asList("fixed", "round", "compact", "sign", "percent", "div", "default");

    private final PillTemplate.Token token;
    private final PillTemplate.Context context;
    private final Utilities.Callback<String> onApply;
    private final Runnable onRemove;
    private final ArrayList<PillTemplate.Filter> kept = new ArrayList<>();
    private final LinearLayout previewLayout;
    private final TextView previewValue;
    private final TextView previewRaw;
    private final EditTextCell defaultCell;

    private int digits;
    private boolean compact;
    private boolean sign;
    private boolean percent;
    private int divider;

    public PillValueFormatSheet(BaseFragment fragment, PillTemplate.Token token, PillTemplate.Context context, Utilities.Callback<String> onApply, Runnable onRemove) {
        super(fragment, true, LocaleController.getString(R.string.Done));
        this.token = token;
        this.context = context;
        this.onApply = onApply;
        this.onRemove = onRemove;

        String defaultValue = "";
        for (PillTemplate.Filter filter : token.filters) {
            switch (filter.name) {
                case "fixed" -> digits = Math.max(0, Arrays.asList(DIGITS).indexOf(filter.arg));
                case "round" -> digits = 1;
                case "compact" -> compact = true;
                case "sign" -> sign = true;
                case "percent" -> percent = true;
                case "div" -> divider = Math.max(0, Arrays.asList(DIVIDERS).indexOf(filter.arg));
                case "default" -> defaultValue = filter.arg != null ? filter.arg : "";
            }
            if (!MANAGED.contains(filter.name)) {
                kept.add(filter);
            }
        }

        previewLayout = new LinearLayout(getContext());
        previewLayout.setOrientation(LinearLayout.VERTICAL);
        previewLayout.setPadding(dp(21), dp(16), dp(21), dp(16));
        previewValue = new TextView(getContext());
        previewValue.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        previewValue.setTypeface(AndroidUtilities.bold());
        previewValue.setGravity(Gravity.CENTER);
        previewValue.setSingleLine();
        previewValue.setEllipsize(TextUtils.TruncateAt.END);
        previewLayout.addView(previewValue, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        previewRaw = new TextView(getContext());
        previewRaw.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        previewRaw.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO));
        previewRaw.setGravity(Gravity.CENTER);
        previewRaw.setMaxLines(2);
        previewRaw.setEllipsize(TextUtils.TruncateAt.END);
        previewLayout.addView(previewRaw, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));

        defaultCell = new PillEditTextCell(getContext(), LocaleController.getString(R.string.CustomPillFormatDefaultHint), false, 32, 8, resourcesProvider) {
            @Override
            protected void onTextChanged(CharSequence newText) {
                updatePreview();
            }
        };
        defaultCell.setText(defaultValue);
        defaultCell.hideKeyboardOnEnter();

        updatePreview();
        ready();
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.CustomPillFormat);
    }

    private String buildToken() {
        ArrayList<PillTemplate.Filter> filters = new ArrayList<>(kept);
        if (divider > 0) {
            filters.add(new PillTemplate.Filter("div", DIVIDERS[divider]));
        }
        if (digits > 0) {
            filters.add(new PillTemplate.Filter("fixed", DIGITS[digits]));
        }
        if (compact) {
            filters.add(new PillTemplate.Filter("compact", null));
        }
        if (percent) {
            filters.add(new PillTemplate.Filter("percent", null));
        }
        if (sign) {
            filters.add(new PillTemplate.Filter("sign", null));
        }
        String defaultValue = defaultCell != null ? defaultCell.getText().toString().replace("|", "").replace("}", "").replace("{", "") : "";
        if (!TextUtils.isEmpty(defaultValue)) {
            filters.add(new PillTemplate.Filter("default", defaultValue));
        }
        return PillTemplate.Token.build(token.path, filters);
    }

    private void updatePreview() {
        if (previewValue == null) {
            return;
        }
        String built = buildToken();
        previewValue.setText(PillTemplate.render(built, context));
        Object raw = PillTemplate.resolve(token.path, context);
        String rawText;
        if (raw == null) {
            rawText = LocaleController.getString(R.string.CustomPillFormatNoValue);
        } else if (raw instanceof JsonElement element) {
            rawText = element.toString();
        } else {
            rawText = String.valueOf(raw);
        }
        previewRaw.setText(token.path + " = " + (rawText.length() > 120 ? rawText.substring(0, 120) + "…" : rawText));
        int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        previewValue.setTextColor(textColor);
        previewRaw.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
    }

    @Override
    protected void fillSheetItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(previewLayout));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.CustomPillFormatDigits)));
        String[] digitLabels = DIGITS.clone();
        digitLabels[0] = LocaleController.getString(R.string.CustomPillFormatAuto);
        items.add(UItem.asSlideView(ITEM_DIGITS, digitLabels, digits, index -> {
            digits = index;
            updatePreview();
        }));
        items.add(UItem.asShadow(null));

        items.add(UItem.asCheck(ITEM_COMPACT, LocaleController.getString(R.string.CustomPillFormatCompact), LocaleController.getString(R.string.CustomPillFormatCompactInfo), false).setChecked(compact));
        items.add(UItem.asCheck(ITEM_SIGN, LocaleController.getString(R.string.CustomPillFormatSign)).setChecked(sign));
        items.add(UItem.asCheck(ITEM_PERCENT, LocaleController.getString(R.string.CustomPillFormatPercent), LocaleController.getString(R.string.CustomPillFormatPercentInfo), false).setChecked(percent));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.CustomPillFormatDivide)));
        String[] dividerLabels = {"—", "100", "1000", "10⁶", "10⁹"};
        items.add(UItem.asSlideView(ITEM_DIVIDE, dividerLabels, divider, index -> {
            divider = index;
            updatePreview();
        }));
        items.add(UItem.asShadow(LocaleController.getString(R.string.CustomPillFormatDivideInfo)));

        items.add(UItem.asHeader(LocaleController.getString(R.string.CustomPillFormatDefault)));
        items.add(UItem.asCustom(defaultCell));
        items.add(UItem.asShadow(null));

        items.add(UItem.asButton(ITEM_REMOVE, R.drawable.msg_delete, LocaleController.getString(R.string.CustomPillFormatRemove)).red());
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onItemClick(UItem item, View view) {
        boolean checked;
        switch (item.id) {
            case ITEM_COMPACT -> checked = compact = !compact;
            case ITEM_SIGN -> checked = sign = !sign;
            case ITEM_PERCENT -> checked = percent = !percent;
            case ITEM_REMOVE -> {
                onRemove.run();
                dismiss();
                return;
            }
            default -> {
                return;
            }
        }
        item.setChecked(checked);
        if (view instanceof TextCheckCell cell) {
            cell.setChecked(checked);
        }
        updatePreview();
        update();
    }

    @Override
    protected void onButtonClick() {
        onApply.run(buildToken());
        dismiss();
    }
}
