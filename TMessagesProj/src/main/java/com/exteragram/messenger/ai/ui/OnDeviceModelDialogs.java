/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.ai.ui;

import static org.telegram.messenger.LocaleController.getString;

import com.exteragram.messenger.ai.network.backend.OnDeviceAvailability;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

public final class OnDeviceModelDialogs {

    private OnDeviceModelDialogs() {
    }

    public static void showDownloadDialog(BaseFragment fragment) {
        if (fragment == null || fragment.getContext() == null || OnDeviceAvailability.isDownloading()) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getContext());
        builder.setTitle(getString(R.string.AIOnDeviceDownloadTitle));
        builder.setSubtitle(AndroidUtilities.replaceTags(getString(R.string.AIOnDeviceDownloadInfo)));
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.setPositiveButton(getString(R.string.AIOnDeviceDownloadButton), (dialog, which) -> startDownload(fragment));
        fragment.showDialog(builder.create());
    }

    private static void startDownload(BaseFragment fragment) {
        int modelKey = OnDeviceAvailability.getModelKey();
        OnDeviceAvailability.download(modelKey, createListener(modelKey));
        OnDeviceModelNotifications.showProgress(modelKey, -1);
        BulletinFactory.of(fragment).createSimpleBulletin(R.raw.ic_download, getString(R.string.AIOnDeviceDownloadStarted)).show();
    }

    private static OnDeviceAvailability.DownloadListener createListener(int modelKey) {
        return new OnDeviceAvailability.DownloadListener() {
            @Override
            public void onStarted(long totalBytes) {
                OnDeviceModelNotifications.showProgress(modelKey, -1);
            }

            @Override
            public void onProgress(float progress, long downloadedBytes) {
                OnDeviceModelNotifications.showProgress(modelKey, progress < 0 ? -1 : (int) (progress * 100));
            }

            @Override
            public void onCompleted() {
                OnDeviceModelNotifications.showCompleted(modelKey);
            }

            @Override
            public void onFailed(Exception e) {
                FileLog.e("AI_ON_DEVICE_DOWNLOAD_FAILED", e);
                OnDeviceModelNotifications.cancel(modelKey);
            }
        };
    }
}
