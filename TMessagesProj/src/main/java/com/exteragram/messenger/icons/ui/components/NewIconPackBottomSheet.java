/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.icons.ui.components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.icons.IconManager;
import com.exteragram.messenger.icons.IconPack;
import com.exteragram.messenger.icons.ui.IconPacksEditorActivity;
import com.exteragram.messenger.icons.ui.picker.IconPickerController;
import com.exteragram.messenger.utils.system.VibratorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.OutlineEditText;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.HashMap;
import java.util.UUID;

public class NewIconPackBottomSheet extends BottomSheet {

    private OutlineEditText nameField;
    private OutlineEditText authorField;
    private OutlineEditText versionField;
    private ButtonWithCounterView doneButton;

    private final BaseFragment parentFragment;
    private final IconPack packToEdit;

    public NewIconPackBottomSheet(BaseFragment parentFragment, Context context) {
        this(parentFragment, context, null);
    }

    public NewIconPackBottomSheet(BaseFragment parentFragment, Context context, IconPack packToEdit) {
        super(context, true);
        this.packToEdit = packToEdit;
        fixNavigationBar();
        waitingKeyboard = true;
        smoothKeyboardAnimationEnabled = true;
        this.parentFragment = parentFragment;
        setCustomView(createView(getContext()));
        setTitle(LocaleController.getString(packToEdit == null ? R.string.NewIconPack : R.string.EditInfo), true);
    }

    @SuppressLint("ClickableViewAccessibility")
    public View createView(Context context) {
        ScrollView fragmentView = new ScrollView(context);

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        fragmentView.addView(contentLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
        contentLayout.setOnTouchListener((v, event) -> true);

        FrameLayout frameLayout = new FrameLayout(context);
        contentLayout.addView(frameLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

        nameField = new OutlineEditText(context);
        nameField.getEditText().setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        nameField.getEditText().setFilters(new InputFilter[]{(source, start, end, dest, dstart, dend) -> {
            int keep = 64 - (dest.length() - (dend - dstart));
            if (keep < end - start) {
                VibratorUtils.vibrate();
                AndroidUtilities.shakeView(nameField);
            }
            if (keep <= 0) {
                return "";
            } else if (keep >= end - start) {
                return null;
            } else {
                keep += start;
                if (Character.isHighSurrogate(source.charAt(keep - 1))) {
                    --keep;
                    if (keep == start) {
                        return "";
                    }
                }
                return source.subSequence(start, keep);
            }
        }});
        nameField.getEditText().setImeOptions(EditorInfo.IME_ACTION_NEXT);
        nameField.setHint(LocaleController.getString(R.string.PackName));
        if (packToEdit != null) {
            nameField.getEditText().setText(packToEdit.getName());
        }
        frameLayout.addView(nameField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));
        nameField.getEditText().setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_NEXT) {
                authorField.requestFocus();
                authorField.getEditText().setSelection(authorField.getEditText().length());
                return true;
            }
            return false;
        });

        authorField = new OutlineEditText(context);
        authorField.setBackground(null);
        authorField.getEditText().setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        authorField.getEditText().setImeOptions(EditorInfo.IME_ACTION_NEXT);
        authorField.setHint(LocaleController.getString(R.string.AuthorNameOptional));
        if (packToEdit != null) {
            authorField.getEditText().setText(packToEdit.getAuthor());
        }
        frameLayout.addView(authorField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.LEFT | Gravity.TOP, 0, 68, 0, 0));
        authorField.getEditText().setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_NEXT) {
                versionField.requestFocus();
                versionField.getEditText().setSelection(versionField.getEditText().length());
                return true;
            }
            return false;
        });

        versionField = new OutlineEditText(context);
        versionField.setBackground(null);
        versionField.getEditText().setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        versionField.getEditText().setImeOptions(EditorInfo.IME_ACTION_DONE);
        versionField.setHint(LocaleController.getString(R.string.Version));
        if (packToEdit != null) {
            versionField.getEditText().setText(packToEdit.getVersion());
        } else {
            versionField.getEditText().setText("1.0");
        }
        frameLayout.addView(versionField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.LEFT | Gravity.TOP, 0, 136, 0, 0));
        versionField.getEditText().setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_DONE) {
                doneButton.callOnClick();
                return true;
            }
            return false;
        });

        doneButton = new ButtonWithCounterView(context, resourcesProvider);
        doneButton.setRound();
        doneButton.setText(LocaleController.getString(packToEdit == null ? R.string.Create : R.string.Save), false);
        doneButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        doneButton.setOnClickListener(v -> doOnDone());
        contentLayout.addView(doneButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 16, 0, 16));

        return fragmentView;
    }

    private void doOnDone() {
        if (nameField.getEditText().length() == 0) {
            VibratorUtils.vibrate();
            AndroidUtilities.shakeView(nameField);
            return;
        }

        String name = nameField.getEditText().getText().toString().trim();
        String author = authorField.getEditText().getText().toString().trim();
        String version = versionField.getEditText().getText().toString().trim();
        if (version.isEmpty()) {
            version = "1.0";
        }

        if (name.isEmpty()) {
            BulletinFactory.of(topBulletinContainer, resourcesProvider).createErrorBulletin(LocaleController.getString(R.string.NameCannotBeEmpty)).show();
            return;
        }

        String finalVersion = version;
        Utilities.globalQueue.postRunnable(() -> {
            if (packToEdit != null) {
                IconPack updatedPack = new IconPack(
                        packToEdit.getId(),
                        name,
                        author.isEmpty() ? LocaleController.getString(R.string.PluginNoAuthor) : author,
                        finalVersion,
                        packToEdit.getIcons(),
                        packToEdit.getPreinstalledMap(),
                        null
                );
                if (!IconManager.INSTANCE.saveIconPackMetadata(updatedPack)) {
                    showStorageError();
                    return;
                }
                AndroidUtilities.runOnUIThread(this::dismiss);
            } else {
                String id = "custom." + UUID.randomUUID().toString();
                IconPack newPack = new IconPack(id, name, author.isEmpty() ? LocaleController.getString(R.string.PluginNoAuthor) : author, finalVersion, new HashMap<>(), null, null);
                if (!IconManager.INSTANCE.saveIconPackMetadata(newPack)) {
                    showStorageError();
                    return;
                }
                AndroidUtilities.runOnUIThread(() -> {
                    IconManager.INSTANCE.setActiveCustomPack(id);
                    dismiss();
                    ExteraConfig.setEditingIconPackId(newPack.getId());
                    parentFragment.presentFragment(new IconPacksEditorActivity(newPack) {
                        @Override
                        public void onBecomeFullyVisible() {
                            if (LaunchActivity.instance != null) {
                                IconPickerController.setActive(LaunchActivity.instance, true);
                            }
                            super.onBecomeFullyVisible();
                        }
                    });
                });
            }
        });
    }

    private void showStorageError() {
        AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(topBulletinContainer, resourcesProvider)
                .createErrorBulletin(LocaleController.getString(R.string.IconPackErrorStorage)).show());
    }

    @Override
    public void onOpenAnimationEnd() {
        super.onOpenAnimationEnd();
        if (nameField != null && nameField.getEditText() != null) {
            nameField.getEditText().requestFocus();
            nameField.getEditText().setSelection(nameField.getEditText().length());
            AndroidUtilities.showKeyboard(nameField.getEditText());
        }
    }
}