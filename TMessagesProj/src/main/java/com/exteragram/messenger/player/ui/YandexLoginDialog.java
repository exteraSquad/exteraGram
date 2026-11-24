/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.exteragram.messenger.player.motion.YandexCanvasRepository;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.support.customtabs.CustomTabsIntent;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public final class YandexLoginDialog {

    public interface Callback {
        void onResult(boolean success);
    }

    private final Context context;
    private final Theme.ResourcesProvider resourcesProvider;
    private final Callback callback;

    private AlertDialog progressDialog;
    private AlertDialog codeDialog;
    private Runnable pollRunnable;
    private long deadline;
    private boolean finished;

    private YandexLoginDialog(Context context, Theme.ResourcesProvider resourcesProvider, Callback callback) {
        this.context = context;
        this.resourcesProvider = resourcesProvider;
        this.callback = callback;
    }

    public static void show(Context context, Theme.ResourcesProvider resourcesProvider, Callback callback) {
        new YandexLoginDialog(context, resourcesProvider, callback).start();
    }

    private void start() {
        progressDialog = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER, resourcesProvider);
        progressDialog.setOnCancelListener(dialog -> cancel());
        progressDialog.show();
        YandexCanvasRepository.requestDeviceCode(code -> {
            if (finished) {
                return;
            }
            progressDialog.dismiss();
            progressDialog = null;
            if (code == null) {
                finish(false, true);
                return;
            }
            showCode(code);
            deadline = SystemClock.elapsedRealtime() + code.expiresInMs();
            schedulePoll(code);
        });
    }

    private void showCode(YandexCanvasRepository.DeviceCode code) {
        final TextView codeView = new TextView(context);
        codeView.setText(code.userCode());
        codeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        codeView.setTypeface(AndroidUtilities.bold());
        codeView.setLetterSpacing(0.2f);
        codeView.setGravity(Gravity.CENTER);
        codeView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourcesProvider));
        codeView.setBackground(Theme.createRoundRectDrawable(dp(12), Theme.getColor(Theme.key_dialogSearchBackground, resourcesProvider)));
        codeView.setPadding(dp(12), dp(14), dp(12), dp(14));
        codeView.setOnClickListener(v -> {
            AndroidUtilities.addToClipboard(code.userCode());
            Toast.makeText(context, getString(R.string.TextCopied), Toast.LENGTH_SHORT).show();
        });

        final FrameLayout container = new FrameLayout(context);
        container.addView(codeView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 24, 0, 24, 0));

        codeDialog = new AlertDialog.Builder(context, resourcesProvider)
                .setTitle(getString(R.string.PlayerYandexLogin))
                .setMessage(LocaleController.formatString(R.string.PlayerYandexLoginInfo, code.verificationUrl()))
                .setView(container)
                .setPositiveButton(getString(R.string.PlayerYandexLoginOpen), (dialog, which) -> openSite(code))
                .setNegativeButton(getString(R.string.Cancel), (dialog, which) -> cancel())
                .setOnDismissListener(dialog -> cancel())
                .create();
        codeDialog.setDismissDialogByButtons(false);
        codeDialog.show();
    }

    private void openSite(YandexCanvasRepository.DeviceCode code) {
        AndroidUtilities.addToClipboard(code.userCode());
        final Uri uri = Uri.parse(code.verificationUrl());
        try {
            new CustomTabsIntent.Builder()
                    .setToolbarColor(Theme.getColor(Theme.key_actionBarBrowser))
                    .setShowTitle(true)
                    .build()
                    .launchUrl(context, uri);
        } catch (Exception e) {
            FileLog.e(e);
            try {
                context.startActivity(new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e2) {
                FileLog.e(e2);
                finish(false, true);
            }
        }
    }

    private void schedulePoll(YandexCanvasRepository.DeviceCode code) {
        pollRunnable = () -> {
            pollRunnable = null;
            YandexCanvasRepository.pollToken(code.deviceCode(), result -> {
                if (finished) {
                    return;
                }
                if (result == YandexCanvasRepository.POLL_SUCCESS) {
                    finish(true, true);
                } else if (result == YandexCanvasRepository.POLL_FAILED || SystemClock.elapsedRealtime() >= deadline) {
                    finish(false, true);
                } else {
                    schedulePoll(code);
                }
            });
        };
        AndroidUtilities.runOnUIThread(pollRunnable, code.intervalMs());
    }

    private void cancel() {
        finish(false, false);
    }

    private void finish(boolean success, boolean notify) {
        if (finished) {
            return;
        }
        finished = true;
        if (pollRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(pollRunnable);
            pollRunnable = null;
        }
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
        if (codeDialog != null) {
            codeDialog.dismiss();
            codeDialog = null;
        }
        if (notify) {
            callback.onResult(success);
        }
    }
}
