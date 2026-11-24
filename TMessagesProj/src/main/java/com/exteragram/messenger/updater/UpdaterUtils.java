/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.updater;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;

import com.exteragram.messenger.badges.BadgesController;
import com.exteragram.messenger.icons.IconManager;
import com.exteragram.messenger.utils.AppUtils;
import com.exteragram.messenger.utils.network.RemoteUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.XiaomiUtilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.StickerImageView;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class UpdaterUtils {

    @SuppressLint("StaticFieldLeak")
    private static AlertDialog dialog;

    public static void getAppUpdate(Utilities.Callback2<TLRPC.TL_help_appUpdate, TLRPC.TL_error> callback) {
        RemoteUtils.getMessages((res, err) -> {
            if (err != null || res == null) {
                callback.run(null, err);
                return;
            }

            TLRPC.TL_help_appUpdate update = parseUpdateResponse(res);

            if (update.id > 0) {
                callback.run(update, null);
            } else {
                var error = new TLRPC.TL_error();
                error.text = "NO_UPDATE_METADATA";
                callback.run(update, error);
            }
        });
    }

    private static TLRPC.TL_help_appUpdate parseUpdateResponse(TLRPC.messages_Messages res) {
        TLRPC.TL_help_appUpdate update = new TLRPC.TL_help_appUpdate();

        int documentId = -1, stickerId = -1, textId = -1;

        for (TLRPC.Message msg : res.messages) {
            if (!(msg instanceof TLRPC.TL_message)) {
                continue;
            }
            if (msg.message.startsWith("update_test") && BadgesController.INSTANCE.isDeveloper()
                    || msg.message.startsWith("update_lite_beta") && BuildVars.isBetaApp() && BuildVars.IS_LITE_VERSION
                    || msg.message.startsWith("update_lite_stable") && !BuildVars.isBetaApp() && BuildVars.IS_LITE_VERSION
                    || msg.message.startsWith("update_beta") && BuildVars.isBetaApp() && !BuildVars.IS_LITE_VERSION
                    || msg.message.startsWith("update_stable") && !BuildVars.isBetaApp() && !BuildVars.IS_LITE_VERSION) {
                String[] split = msg.message.split("\n");

                if (split.length < 6) {
                    continue;
                }

                for (String line : split) {
                    String[] keyValue = line.split("=", 2);
                    if (keyValue.length == 2) {
                        String key = keyValue[0].trim();
                        String value = keyValue[1].trim();

                        switch (key) {
                            case "can_not_skip" ->
                                    update.can_not_skip = Boolean.parseBoolean(value);
                            case "version" -> update.version = value;
                            case "document" -> documentId = Integer.parseInt(value);
                            case "sticker" -> stickerId = Integer.parseInt(value);
                            case "text" -> textId = Integer.parseInt(value);
                        }
                    }
                }
                update.id = msg.id;
                break;
            }
        }

        for (TLRPC.Message msg : res.messages) {
            if (!(msg instanceof TLRPC.TL_message)) {
                continue;
            }
            if (msg.id == textId) {
                update.entities = msg.entities;
                update.text = msg.message;
                continue;
            }
            if (msg.media != null) {
                TLRPC.Document document = msg.media.getDocument();
                if (msg.id == documentId && !document.attributes.isEmpty() && document.attributes.get(0).file_name.endsWith(".apk")) {
                    update.document = document;
                    update.flags |= 2;
                    continue;
                }
                if (msg.id == stickerId && MessageObject.isStickerDocument(document)) {
                    update.sticker = document;
                    update.flags |= 8;
                }
            }
        }
        return update;
    }

    public static void installUpdate(Activity context, TLRPC.Document document) {
        if (context == null || document == null) {
            return;
        }
        if (XiaomiUtilities.isMIUI()) {
            AndroidUtilities.openForView(document, context);
            return;
        }
        var apk = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(document, true);
        if (apk == null) {
            return;
        }
        if (dialog != null && dialog.isShowing()) {
            return;
        }
        showInstallDialog(context);
        Utilities.globalQueue.postRunnable(() -> {
            var receiver = register(context, () -> {
                if (dialog != null) {
                    dialog.dismiss();
                    dialog = null;
                }
            });
            installApk(context, apk);
            Intent intent = receiver.waitIntent();
            if (intent != null) {
                context.startActivity(intent);
            }
        });
    }

    private static void showInstallDialog(Activity context) {
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 4, 4, 4, 4));

        StickerImageView imageView = new StickerImageView(context, UserConfig.selectedAccount);
        imageView.setStickerPackName("UtyaDuckFull");
        imageView.setStickerNum(0);
        imageView.getImageReceiver().setAutoRepeat(1);
        linearLayout.addView(imageView, LayoutHelper.createLinear(160, 160, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 17, 24, 17, 0));

        TextView textView = new TextView(context);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setText(LocaleController.getString(R.string.UpdateInstalling));
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 17, 20, 17, 0));

        TextView textView2 = new TextView(context);
        textView2.setGravity(Gravity.CENTER);
        textView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        textView2.setTextColor(Theme.getColor(Theme.key_dialogTextGray));
        textView2.setText(Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Settings.canDrawOverlays(context) ?
                LocaleController.getString(R.string.UpdateInstallingRelaunch) :
                LocaleController.getString(R.string.UpdateInstallingNotification));
        linearLayout.addView(textView2, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 17, 4, 17, 24));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(linearLayout);
        dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(false);
        dialog.show();
    }

    private static void installApk(Activity context, File apk) {
        var flag = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;
        var action = UpdaterUtils.class.getName();
        var intent = new Intent(action).setPackage(context.getPackageName());
        var pending = PendingIntent.getBroadcast(context, 0, intent, flag);

        var installer = context.getPackageManager().getPackageInstaller();
        var params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        }
        try (PackageInstaller.Session session = installer.openSession(installer.createSession(params))) {
            try (OutputStream out = session.openWrite(apk.getName(), 0, apk.length());
                 FileInputStream in = new FileInputStream(apk)) {
                transfer(in, out);
            }
            session.commit(pending.getIntentSender());
        } catch (IOException e) {
            FileLog.e(e);
            handleInstallError(context, apk, e);
        }
    }

    private static void handleInstallError(Activity context, File apk, IOException e) {
        AndroidUtilities.runOnUIThread(() -> {
            if (dialog != null) {
                dialog.dismiss();
                dialog = null;
            }
        });
        AlertsCreator.createSimpleAlert(context, LocaleController.getString(R.string.ErrorOccurred) + "\n" + e.getLocalizedMessage()).show();
        AndroidUtilities.openForView(apk, "install.apk", "application/vnd.android.package-archive", context, null, false);
    }

    private static void transfer(InputStream in, OutputStream out) throws IOException {
        int size = 8192;
        var buffer = new byte[size];
        int read;
        while ((read = in.read(buffer, 0, size)) >= 0) {
            out.write(buffer, 0, read);
        }
    }

    private static InstallReceiver register(Context context, Runnable onSuccess) {
        var receiver = new InstallReceiver(context, ApplicationLoader.getApplicationId(), onSuccess);
        ContextCompat.registerReceiver(context, receiver, new IntentFilter(UpdaterUtils.class.getName()), ContextCompat.RECEIVER_NOT_EXPORTED);
        return receiver;
    }

    private static class InstallReceiver extends BroadcastReceiver {
        private final Context context;
        private final String packageName;
        private final Runnable onSuccess;
        private final CountDownLatch latch = new CountDownLatch(1);
        private Intent intent = null;

        private InstallReceiver(Context context, String packageName, Runnable onSuccess) {
            this.context = context;
            this.packageName = packageName;
            this.onSuccess = onSuccess;
        }

        @Override
        public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_PACKAGE_ADDED.equals(i.getAction())) {
                Uri data = i.getData();
                if (data == null || onSuccess == null) return;
                String pkg = data.getSchemeSpecificPart();
                if (pkg.equals(packageName)) {
                    onSuccess.run();
                    context.unregisterReceiver(this);
                }
                return;
            }
            handlePackageInstallerResult(i);
        }

        @SuppressWarnings("fallthrough")
        private void handlePackageInstallerResult(Intent i) {
            int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE_INVALID);
            switch (status) {
                case PackageInstaller.STATUS_PENDING_USER_ACTION:
                    intent = IntentCompat.getParcelableExtra(i, Intent.EXTRA_INTENT, Intent.class);
                    break;
                case PackageInstaller.STATUS_FAILURE:
                case PackageInstaller.STATUS_FAILURE_BLOCKED:
                case PackageInstaller.STATUS_FAILURE_CONFLICT:
                case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                case PackageInstaller.STATUS_FAILURE_INVALID:
                case PackageInstaller.STATUS_FAILURE_STORAGE:
                    handleFailure(i);
                case PackageInstaller.STATUS_FAILURE_ABORTED:
                case PackageInstaller.STATUS_SUCCESS:
                default:
                    if (onSuccess != null) onSuccess.run();
                    context.unregisterReceiver(this);
            }
            latch.countDown();
        }

        private void handleFailure(Intent i) {
            int id = i.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, 0);
            if (id > 0) {
                var installer = context.getPackageManager().getPackageInstaller();
                var info = installer.getSessionInfo(id);
                if (info != null) {
                    installer.abandonSession(info.getSessionId());
                }
            }
            if (context instanceof LaunchActivity) {
                ((LaunchActivity) context).showBulletin(factory -> factory.createErrorBulletin(LocaleController.formatString(R.string.UpdateFailedToInstall, i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE))));
            }
        }

        public Intent waitIntent() {
            try {
                latch.await(5, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
            return intent;
        }
    }

    public static class UpdateReceiver extends BroadcastReceiver {
        @SuppressWarnings("deprecation")
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) return;

            var packageName = context.getPackageName();
            var installer = context.getPackageManager().getInstallerPackageName(packageName);
            if (!packageName.equals(installer)) return;

            launchApp(context);
        }

        private void launchApp(Context context) {
            var startIntent = new Intent(context, LaunchActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Settings.canDrawOverlays(context)) {
                context.startActivity(startIntent);
            } else {
                showNotification(context, startIntent);
            }
        }

        private void showNotification(Context context, Intent startIntent) {
            var channel = new NotificationChannelCompat.Builder("updated", NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(LocaleController.getString(R.string.UpdateApp))
                    .setLightsEnabled(false)
                    .setVibrationEnabled(false)
                    .setSound(null, null)
                    .build();
            var notificationManager = NotificationManagerCompat.from(context);
            notificationManager.createNotificationChannel(channel);
            var pendingIntent = PendingIntent.getActivity(context, 0, startIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            notificationManager.notify(8732833,
                    new NotificationCompat.Builder(context, "updated")
                            .setSmallIcon(IconManager.getNotificationIcon())
                            .setColor(AppUtils.getNotificationColor())
                            .setShowWhen(false)
                            .setContentText(LocaleController.getString(R.string.UpdateInstalledNotification))
                            .setCategory(NotificationCompat.CATEGORY_STATUS)
                            .setContentIntent(pendingIntent)
                            .build());
        }
    }
}
