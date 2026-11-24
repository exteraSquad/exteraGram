/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.speech.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.DialogInterface;
import android.widget.TextView;

import com.exteragram.messenger.speech.VoiceRecognitionController;
import com.exteragram.messenger.translator.TranslatorUtils;
import com.exteragram.messenger.utils.ui.PopupUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;

import java.util.ArrayList;
import java.util.List;

public final class RecognitionModelDialogs {

    private RecognitionModelDialogs() {
    }

    public static CharSequence getRecognitionLanguageOption(String languageCode) {
        String title = TranslatorUtils.getLanguageTitleSystem(languageCode);
        String displayName = TranslatorUtils.getLanguageDisplayName(languageCode);
        return displayName == null || displayName.equalsIgnoreCase(title) ? title : title + " – " + displayName;
    }

    public static void showDownloadDialog(BaseFragment fragment, String languageCode, VoiceRecognitionController.RecognitionModel model, Runnable onDownloaded) {
        if (fragment.getContext() == null) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getContext());
        builder.setTitle(getString(R.string.MissingLanguageModel));
        builder.setSubtitle(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.ModelDownloadInfo, TranslatorUtils.getLanguageTitleSystem(languageCode))));
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.formatString(R.string.ModelDownload, AndroidUtilities.formatFileSize(model.getSize())), (dialog, which) -> {
            fragment.dismissCurrentDialog();
            LoadingModelView loadingModelView = new LoadingModelView(fragment.getContext());
            AlertDialog.Builder progressBuilder = new AlertDialog.Builder(fragment.getContext());
            progressBuilder.setView(loadingModelView);
            AlertDialog alert = progressBuilder.create();
            alert.setCanceledOnTouchOutside(false);
            alert.setCancelable(false);

            final boolean[] done = new boolean[]{false};
            final float[] progressValue = new float[]{0};
            Runnable updateProgress = () -> loadingModelView.setProgress(progressValue[0]);

            final long[] start = new long[]{-1};
            AndroidUtilities.runOnUIThread(() -> {
                if (!done[0]) {
                    start[0] = System.currentTimeMillis();
                    alert.show();
                }
            }, 150);

            VoiceRecognitionController.getInstance().downloadModel("vosk", languageCode, new VoiceRecognitionController.DownloadModelCallback() {
                @Override
                public void onProgress(float progress) {
                    progressValue[0] = progress;
                    AndroidUtilities.cancelRunOnUIThread(updateProgress);
                    AndroidUtilities.runOnUIThread(updateProgress);
                }

                @Override
                public void onCompleted() {
                    AndroidUtilities.runOnUIThread(() -> {
                        done[0] = true;
                        loadingModelView.setProgress(1F);
                        if (start[0] > 0) {
                            AndroidUtilities.runOnUIThread(alert::dismiss, Math.max(0, 1000 - (System.currentTimeMillis() - start[0])));
                        } else {
                            alert.dismiss();
                        }
                        onDownloaded.run();
                        BulletinFactory.of(fragment).createSuccessBulletin(getString(R.string.ModelDownloaded)).show();
                    });
                }

                @Override
                public void onError(Exception e) {
                    AndroidUtilities.runOnUIThread(() -> {
                        alert.dismiss();
                        BulletinFactory.of(fragment).createErrorBulletin(getString(R.string.ModelError)).show();
                    });
                }
            });
        });
        fragment.showDialog(builder.create());
    }

    public static void showDeleteFlow(BaseFragment fragment, List<VoiceRecognitionController.RecognitionModel> downloadedModels, Utilities.Callback<VoiceRecognitionController.RecognitionModel> onDeleted) {
        if (fragment.getContext() == null || downloadedModels.isEmpty()) {
            return;
        }

        if (downloadedModels.size() == 1) {
            showDeleteConfirmDialog(fragment, downloadedModels.get(0), onDeleted);
            return;
        }

        ArrayList<CharSequence> options = new ArrayList<>(downloadedModels.size());
        for (VoiceRecognitionController.RecognitionModel downloadedModel : downloadedModels) {
            options.add(getRecognitionLanguageOption(downloadedModel.getLanguage()));
        }
        PopupUtils.showDialogWithoutRadio(options, getString(R.string.DeleteRecognitionModel), fragment.getContext(), i -> {
            if (i < 0 || i >= downloadedModels.size()) {
                return;
            }
            showDeleteConfirmDialog(fragment, downloadedModels.get(i), onDeleted);
        });
    }

    private static void showDeleteConfirmDialog(BaseFragment fragment, VoiceRecognitionController.RecognitionModel model, Utilities.Callback<VoiceRecognitionController.RecognitionModel> onDeleted) {
        if (fragment.getContext() == null) {
            return;
        }

        var builder = new AlertDialog.Builder(fragment.getContext());
        builder.setTitle(getString(R.string.DeleteRecognitionModel));
        builder.setSubtitle(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.DeleteRecognitionModelInfo, getRecognitionLanguageOption(model.getLanguage()))));
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.setPositiveButton(getString(R.string.Delete), (dialog, which) -> {
            VoiceRecognitionController.getInstance().deleteModel("vosk", model.getLanguage(), new VoiceRecognitionController.DeleteModelCallback() {
                @Override
                public void onCompleted() {
                    AndroidUtilities.runOnUIThread(() -> {
                        onDeleted.run(model);
                        BulletinFactory.of(fragment).createSuccessBulletin(getString(R.string.RecognitionModelDeleted)).show();
                    });
                }

                @Override
                public void onError(Exception e) {
                    AndroidUtilities.runOnUIThread(() ->
                            BulletinFactory.of(fragment).createErrorBulletin(getString(R.string.RecognitionModelDeleteError)).show()
                    );
                }
            });
        });
        AlertDialog dialog = builder.create();
        fragment.showDialog(dialog);
        TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }
}
