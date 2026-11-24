/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.custom.ui;

import android.view.View;

import androidx.annotation.Nullable;

import com.exteragram.messenger.pillstack.custom.CustomPillIO;
import com.exteragram.messenger.pillstack.custom.CustomPillSpec;
import com.exteragram.messenger.pillstack.custom.CustomPillTemplates;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;
import java.util.List;

public class PillTemplatesSheet extends PillSheet {

    private static final int ITEM_PASTE = 1;
    private static final int ROW_TEMPLATES = 100;

    private final Utilities.Callback<CustomPillTemplates.Template> onTemplate;
    private final Utilities.Callback<CustomPillSpec> onImport;
    private final List<CustomPillTemplates.Template> templates = CustomPillTemplates.getAll();
    @Nullable
    private CustomPillSpec clipboardSpec;

    public PillTemplatesSheet(BaseFragment fragment, Utilities.Callback<CustomPillTemplates.Template> onTemplate, Utilities.Callback<CustomPillSpec> onImport) {
        super(fragment, false, null);
        this.onTemplate = onTemplate;
        this.onImport = onImport;
        setSegmented(false);
        clipboardSpec = CustomPillIO.readClipboard(getContext());
        ready();
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.CustomPillNew);
    }

    @Override
    protected void fillSheetItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.CustomPillTemplates)).onBind(view -> view.setTag(RecyclerListView.TAG_NOT_SECTION)));
        for (int i = 0; i < templates.size(); i += 2) {
            items.add(CustomPillCells.TemplateRowCell.Factory.of(ROW_TEMPLATES + i, templates.subList(i, Math.min(templates.size(), i + 2)), template -> {
                dismiss();
                onTemplate.run(template);
            }));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.CustomPillTemplatesInfo)));
        if (clipboardSpec != null) {
            items.add(UItem.asButton(ITEM_PASTE, R.drawable.msg_copy, LocaleController.getString(R.string.PasteFromClipboard)).accent());
            items.add(UItem.asShadow(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.CustomPillPasteFound, clipboardSpec.name))));
        }
    }

    @Override
    protected void onItemClick(UItem item, View view) {
        if (item.id == ITEM_PASTE && clipboardSpec != null) {
            dismiss();
            onImport.run(clipboardSpec);
        }
    }
}
