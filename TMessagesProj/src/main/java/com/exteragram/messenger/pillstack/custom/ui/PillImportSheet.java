/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.custom.ui;

import android.net.Uri;
import android.text.TextUtils;

import com.exteragram.messenger.pillstack.custom.CustomPillFetcher;
import com.exteragram.messenger.pillstack.custom.CustomPillResult;
import com.exteragram.messenger.pillstack.custom.CustomPillSpec;
import com.exteragram.messenger.pillstack.custom.CustomPillStore;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

public class PillImportSheet extends PillSheet {

    private final CustomPillSpec spec;
    private final PillPreviewView previewView;

    public PillImportSheet(BaseFragment fragment, CustomPillSpec spec) {
        super(fragment, false, LocaleController.getString(R.string.CustomPillImportAdd));
        this.spec = spec;
        previewView = new PillPreviewView(getContext(), resourcesProvider, spec);
        previewView.getPill().setPreview(CustomPillResult.buildPreview(spec, null), false, false);
        previewView.setStatus(LocaleController.getString(R.string.CustomPillImportPreviewInfo));
        ready();
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.CustomPillImportTitle);
    }

    public static String getHost(CustomPillSpec spec) {
        if (!spec.http) {
            return null;
        }
        try {
            String host = Uri.parse(spec.url.trim()).getHost();
            return TextUtils.isEmpty(host) ? spec.url : host;
        } catch (Exception e) {
            return spec.url;
        }
    }

    public static String getSharedData(CustomPillSpec spec) {
        ArrayList<String> shared = new ArrayList<>();
        String request = spec.http ? spec.url + " " + spec.body + " " + headersText(spec) : "";
        if (request.contains("{" + CustomPillFetcher.VAR_LANG + "}")) {
            shared.add(LocaleController.getString(R.string.CustomPillImportSharesLanguage));
        }
        if (request.contains("{" + CustomPillFetcher.VAR_CURRENCY + "}")) {
            shared.add(LocaleController.getString(R.string.CustomPillImportSharesCurrency));
        }
        if (CustomPillFetcher.needsLocation(spec)) {
            shared.add(LocaleController.getString(R.string.CustomPillImportSharesLocation));
        }
        if (shared.isEmpty()) {
            return LocaleController.getString(R.string.CustomPillImportSharesNothing);
        }
        return TextUtils.join(", ", shared);
    }

    private static String headersText(CustomPillSpec spec) {
        StringBuilder builder = new StringBuilder();
        for (CustomPillSpec.Header header : spec.headers) {
            builder.append(header.value).append(' ');
        }
        return builder.toString();
    }

    @Override
    protected void fillSheetItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(previewView));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(spec.name));
        ArrayList<Object[]> rows = new ArrayList<>();
        String host = getHost(spec);
        rows.add(new Object[]{R.drawable.msg_link, LocaleController.getString(R.string.CustomPillImportHost), host != null ? host : LocaleController.getString(R.string.CustomPillImportNoRequests)});
        if (spec.http) {
            rows.add(new Object[]{R.drawable.msg_contacts, LocaleController.getString(R.string.CustomPillImportShares), getSharedData(spec)});
            rows.add(new Object[]{R.drawable.msg_recent, LocaleController.getString(R.string.CustomPillRefresh), CustomPillEditorActivity.getRefreshName(spec.refresh)});
            if (!spec.secrets.isEmpty()) {
                rows.add(new Object[]{R.drawable.msg_secret, LocaleController.getString(R.string.CustomPillImportSecrets), TextUtils.join(", ", spec.secrets)});
            }
        }
        if (CustomPillSpec.TAP_LINK.equals(spec.tap)) {
            String link = spec.link;
            try {
                String linkHost = Uri.parse(link).getHost();
                if (!TextUtils.isEmpty(linkHost)) {
                    link = linkHost;
                }
            } catch (Exception ignore) {
            }
            rows.add(new Object[]{R.drawable.msg_openin, LocaleController.getString(R.string.CustomPillImportOpens), link});
        }
        for (int i = 0; i < rows.size(); i++) {
            Object[] row = rows.get(i);
            TextCell cell = new TextCell(getContext(), resourcesProvider);
            cell.setTextAndValueAndIcon((CharSequence) row[1], (CharSequence) row[2], (int) row[0], i < rows.size() - 1);
            items.add(UItem.asCustom(cell));
        }
        items.add(UItem.asShadow(LocaleController.getString(spec.http ? R.string.CustomPillImportWarning : R.string.CustomPillImportWarningLocal)));
    }

    @Override
    protected void onButtonClick() {
        int id = CustomPillStore.allocateId();
        if (id == 0) {
            BulletinFactory.of(fragment).createErrorBulletin(LocaleController.formatString(R.string.CustomPillLimitReached, CustomPillStore.MAX_PILLS)).show();
            dismiss();
            return;
        }
        spec.id = id;
        CustomPillStore.save(spec, false, null, null);
        dismiss();
        CustomPillEditorActivity editor = new CustomPillEditorActivity(id);
        editor.setOpenSecretsOnStart(CustomPillStore.hasMissingSecrets(spec));
        fragment.presentFragment(editor);
    }
}
