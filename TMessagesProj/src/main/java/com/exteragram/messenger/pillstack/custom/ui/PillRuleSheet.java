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
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.exteragram.messenger.pillstack.custom.CustomPillSpec;
import com.exteragram.messenger.pillstack.custom.PillColors;
import com.exteragram.messenger.pillstack.custom.PillIconBadgeView;
import com.exteragram.messenger.pillstack.custom.PillTemplate;
import com.exteragram.messenger.utils.ui.PopupUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.EditTextCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;
import java.util.List;

public class PillRuleSheet extends PillSheet {

    private static final int ITEM_PATH = 1;
    private static final int ITEM_DELETE = 3;
    private static final int ITEM_OP = 10;

    private final CustomPillSpec.Rule rule;
    private final CustomPillSpec.Icon baseIcon;
    private final String baseColor;
    private final List<String> paths;
    private final boolean isNew;
    private final Utilities.Callback<CustomPillSpec.Rule> onSave;
    @Nullable
    private final Runnable onDelete;
    private final EditTextCell valueCell;
    private final EditTextCell textCell;
    private final PillIconBadgeView iconView;
    private final PillColorGrid colorGrid;
    private int shift = -4;

    public PillRuleSheet(BaseFragment fragment, CustomPillSpec.Rule rule, CustomPillSpec.Icon baseIcon, String baseColor, List<String> paths, boolean isNew, Utilities.Callback<CustomPillSpec.Rule> onSave, @Nullable Runnable onDelete) {
        super(fragment, true, LocaleController.getString(R.string.Save));
        this.rule = rule.copy();
        this.baseIcon = baseIcon;
        this.baseColor = baseColor;
        this.paths = paths;
        this.isNew = isNew;
        this.onSave = onSave;
        this.onDelete = onDelete;
        if (TextUtils.isEmpty(this.rule.path) && !paths.isEmpty()) {
            this.rule.path = paths.get(0);
        }

        valueCell = new PillEditTextCell(getContext(), LocaleController.getString(R.string.CustomPillRuleValueHint), false, CustomPillSpec.MAX_RULE_VALUE, 16, resourcesProvider);
        valueCell.setText(this.rule.value);
        valueCell.hideKeyboardOnEnter();

        textCell = new PillEditTextCell(getContext(), LocaleController.getString(R.string.CustomPillRuleTextHint), false, CustomPillSpec.MAX_TEXT, 40, resourcesProvider);
        textCell.setText(this.rule.text != null ? this.rule.text : "");
        textCell.hideKeyboardOnEnter();
        textCell.editText.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, LocaleController.isRTL ? 0 : 44, 0, LocaleController.isRTL ? 44 : 0, 0));
        FrameLayout iconButton = new FrameLayout(getContext());
        iconButton.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector)));
        iconButton.setContentDescription(LocaleController.getString(R.string.CustomPillIcon));
        iconButton.setOnClickListener(v -> new PillIconPickerSheet(fragment, this.rule.icon, baseIcon, icon -> {
            this.rule.icon = icon;
            updateIcon();
        }).show());
        textCell.addView(iconButton, LayoutHelper.createFrame(48, 48, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 8, 0, 8, 0));
        iconView = new PillIconBadgeView(getContext(), resourcesProvider, false);
        iconButton.addView(iconView, LayoutHelper.createFrame(28, 28, Gravity.CENTER));

        colorGrid = new PillColorGrid(getContext(), resourcesProvider, true, color -> {
            this.rule.color = color;
            updateIcon();
        });
        colorGrid.setSelectedColor(this.rule.color);
        updateIcon();
        ready();
    }

    private void updateIcon() {
        PillColors colors = PillColors.get(rule.color != null ? rule.color : baseColor);
        iconView.set(rule.icon != null ? rule.icon : baseIcon, 0, colors.top(), colors.bottom());
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(isNew ? R.string.CustomPillRuleNew : R.string.CustomPillRule);
    }

    public static CharSequence getOperatorName(String op) {
        return switch (op) {
            case CustomPillSpec.OP_LESS -> LocaleController.getString(R.string.CustomPillRuleLess);
            case CustomPillSpec.OP_GREATER -> LocaleController.getString(R.string.CustomPillRuleGreater);
            case CustomPillSpec.OP_EQUALS -> LocaleController.getString(R.string.CustomPillRuleEquals);
            case CustomPillSpec.OP_NOT_EQUALS -> LocaleController.getString(R.string.CustomPillRuleNotEquals);
            default -> LocaleController.getString(R.string.CustomPillRuleEmpty);
        };
    }

    public static String getOperatorSymbol(String op) {
        return switch (op) {
            case CustomPillSpec.OP_NOT_EQUALS -> "≠";
            case CustomPillSpec.OP_EMPTY -> "∅";
            default -> op;
        };
    }

    @Override
    protected void fillSheetItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.CustomPillRuleIf)));
        items.add(UItem.asButton(ITEM_PATH, R.drawable.msg_stats, LocaleController.getString(R.string.CustomPillRuleValue), TextUtils.isEmpty(rule.path) ? "—" : PillTemplate.label(rule.path)));
        items.add(UItem.asButton(ITEM_OP, R.drawable.msg_calculator, LocaleController.getString(R.string.CustomPillRuleCondition), getOperatorName(rule.op)));
        if (!CustomPillSpec.OP_EMPTY.equals(rule.op)) {
            items.add(UItem.asCustom(valueCell));
        }
        items.add(UItem.asShadow(TextUtils.isEmpty(rule.path) ? LocaleController.getString(R.string.CustomPillRuleNoValues) : rule.path.startsWith("$") ? null : rule.path));

        items.add(UItem.asHeader(LocaleController.getString(R.string.CustomPillRuleThen)));
        items.add(UItem.asCustom(colorGrid));
        items.add(UItem.asCustom(textCell));
        items.add(UItem.asShadow(LocaleController.getString(R.string.CustomPillRuleThenInfo)));

        if (!isNew && onDelete != null) {
            items.add(UItem.asButton(ITEM_DELETE, R.drawable.msg_delete, LocaleController.getString(R.string.CustomPillRuleDelete)).red());
            items.add(UItem.asShadow(null));
        }
    }

    @Override
    protected void onItemClick(UItem item, View view) {
        if (item.id == ITEM_OP) {
            CharSequence[] names = new CharSequence[CustomPillSpec.OPS.length];
            int selected = 0;
            for (int i = 0; i < names.length; i++) {
                names[i] = getOperatorName(CustomPillSpec.OPS[i]);
                if (CustomPillSpec.OPS[i].equals(rule.op)) {
                    selected = i;
                }
            }
            PopupUtils.showDialog(names, null, LocaleController.getString(R.string.CustomPillRuleCondition), selected, getContext(), index -> {
                rule.op = CustomPillSpec.OPS[index];
                update();
            }, resourcesProvider, true);
        } else if (item.id == ITEM_PATH) {
            if (paths.isEmpty()) {
                return;
            }
            CharSequence[] labels = new CharSequence[paths.size()];
            for (int i = 0; i < paths.size(); i++) {
                String path = paths.get(i);
                labels[i] = path.startsWith("$") ? PillTemplate.label(path) : path;
            }
            PopupUtils.showDialog(labels, null, LocaleController.getString(R.string.CustomPillRuleValue), Math.max(0, paths.indexOf(rule.path)), getContext(), index -> {
                rule.path = paths.get(index);
                update();
            }, resourcesProvider, true);
        } else if (item.id == ITEM_DELETE && onDelete != null) {
            onDelete.run();
            dismiss();
        }
    }

    @Override
    protected void onButtonClick() {
        if (TextUtils.isEmpty(rule.path)) {
            BotWebViewVibrationEffect.APP_ERROR.vibrate();
            return;
        }
        String value = valueCell.getText().toString().trim();
        if (!CustomPillSpec.OP_EMPTY.equals(rule.op) && value.isEmpty()) {
            BotWebViewVibrationEffect.APP_ERROR.vibrate();
            AndroidUtilities.shakeViewSpring(valueCell, shift = -shift);
            valueCell.editText.requestFocus();
            AndroidUtilities.showKeyboard(valueCell.editText);
            return;
        }
        rule.value = CustomPillSpec.OP_EMPTY.equals(rule.op) ? "" : value;
        String text = textCell.getText().toString().trim();
        rule.text = text.isEmpty() ? null : text;
        onSave.run(rule);
        dismiss();
    }
}
