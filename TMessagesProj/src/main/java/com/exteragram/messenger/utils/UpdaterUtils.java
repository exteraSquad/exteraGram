/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.utils;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import androidx.core.content.FileProvider;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.preferences.updater.UpdaterBottomSheet;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.TypefaceSpan;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

public class UpdaterUtils {

    public static final DispatchQueue otaQueue = new DispatchQueue("otaQueue");

    private static String uri = "https://api.github.com/repos/exteraSquad/exteraGram/releases/latest";
    private static String downloadURL = null;
    public static String version, changelog, size, uploadDate;
    public static File otaPath, versionPath, apkFile;

    private static long id = 1L;
    private static final long updateCheckInterval = 3600000L; // 1 hour

    private static boolean updateDownloaded;
    private static boolean checkingForUpdates;

    public static void checkDirs() {
        otaPath = new File(ApplicationLoader.applicationContext.getExternalFilesDir(null), "ota");
        if (version != null) {
            versionPath = new File(otaPath, version);
            apkFile = new File(versionPath, "update.apk");
            try {
                if (!versionPath.exists())
                    versionPath.mkdirs();
            } catch (Exception e) {
                FileLog.e(e);
            }
            updateDownloaded = apkFile.exists();
        }
    }

    public static void checkUpdates(BaseFragment fragment, boolean manual) {
        checkUpdates(fragment, manual, null, null);
    }

    public interface OnUpdateNotFound {
        void run();
    }

    public interface OnUpdateFound {
        void run();
    }

    public static void checkUpdates(BaseFragment fragment, boolean manual, OnUpdateNotFound onUpdateNotFound, OnUpdateFound onUpdateFound) {

        if (BuildVars.PM_BUILD || checkingForUpdates || id != 1L || (System.currentTimeMillis() - ExteraConfig.updateScheduleTimestamp < updateCheckInterval && !manual))
            return;

        checkingForUpdates = true;
        otaQueue.postRunnable(() -> {
            ExteraConfig.editor.putLong("lastUpdateCheckTime", ExteraConfig.lastUpdateCheckTime = System.currentTimeMillis()).apply();
            try {
                if (BuildVars.isBetaApp())
                    uri = uri.replace("/exteraGram/", "/exteraGram-Beta/");
                var connection = (HttpURLConnection) new URI(uri).toURL().openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", TranslatorUtils.formatUserAgent());
                connection.setRequestProperty("Content-Type", "application/json");

                var textBuilder = new StringBuilder();
                try (Reader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    int c;
                    while ((c = reader.read()) != -1)
                        textBuilder.append((char) c);
                }

                var obj = new JSONObject(textBuilder.toString());
                var arr = obj.getJSONArray("assets");

                if (arr.length() == 0)
                    return;

                String link, installedApkType = getInstalledApkType();
                String[] supportedTypes = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64", "universal"};
                loop:
                for (int i = 0; i < arr.length(); i++) {
                    downloadURL = link = arr.getJSONObject(i).getString("browser_download_url");
                    size = AndroidUtilities.formatFileSize(arr.getJSONObject(i).getLong("size"));
                    if (link.contains("beta") && BuildVars.isBetaApp()) {
                        break;
                    }
                    for (String type : supportedTypes) {
                        if (link.contains(type) && Objects.equals(installedApkType, type)) {
                            break loop;
                        }
                    }
                }
                version = obj.getString("tag_name");
                changelog = obj.getString("body");
                uploadDate = obj.getString("published_at").replaceAll("[TZ]", " ");
                uploadDate = LocaleController.formatDateTime(getMillisFromDate(uploadDate, "yyyy-M-dd hh:mm:ss") / 1000);

                if (isNewVersion(BuildVars.BUILD_VERSION_STRING, version) && fragment != null) {
                    checkDirs();
                    AndroidUtilities.runOnUIThread(() -> {
                        (new UpdaterBottomSheet(fragment.getContext(), fragment, true, version, changelog, size, downloadURL, uploadDate)).show();
                        if (onUpdateFound != null)
                            onUpdateFound.run();
                    });
                } else {
                    if (onUpdateNotFound != null)
                        AndroidUtilities.runOnUIThread(onUpdateNotFound::run);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            checkingForUpdates = false;
        }, 200);
    }

    public static void downloadApk(Context context, String link, String title) {
        if (context != null && !updateDownloaded) {
            var request = new DownloadManager.Request(Uri.parse(link));

            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE | DownloadManager.Request.NETWORK_WIFI);
            request.setTitle(title);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(context, "ota/" + version, "update.apk");

            var manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            id = manager.enqueue(request);

            var downloadBroadcastReceiver = new DownloadReceiver();
            var intentFilter = new IntentFilter();
            intentFilter.addAction("android.intent.action.DOWNLOAD_COMPLETE");
            intentFilter.addAction("android.intent.action.DOWNLOAD_NOTIFICATION_CLICKED");
            context.registerReceiver(downloadBroadcastReceiver, intentFilter);
        } else {
            installApk(context, apkFile.getAbsolutePath());
        }
    }

    public static void installApk(Context context, String path) {
        var file = new File(path);
        if (!file.exists())
            return;
        var install = new Intent(Intent.ACTION_VIEW);
        Uri fileUri;
        if (Build.VERSION.SDK_INT >= 24) {
            fileUri = FileProvider.getUriForFile(context, ApplicationLoader.getApplicationId() + ".provider", file);
        } else {
            fileUri = Uri.fromFile(file);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ApplicationLoader.applicationContext.getPackageManager().canRequestPackageInstalls()) {
            AlertsCreator.createApkRestrictedDialog(context, null).show();
            return;
        }
        if (fileUri != null) {
            install.setDataAndType(fileUri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            if (install.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(install);
            }
        }
    }

    public static boolean isNewVersion(String currentVersion, String newVersion) {
        String[] current = currentVersion.split("\\.");
        String[] latest = newVersion.split("\\.");

        int length = Math.max(current.length, latest.length);
        for (int i = 0; i < length; i++) {
            int v1 = i < current.length ? Utilities.parseInt(current[i]) : 0;
            int v2 = i < latest.length ? Utilities.parseInt(latest[i]) : 0;
            if (v1 < v2) {
                return true;
            } else if (v1 > v2) {
                return false;
            }
        }
        return false;
    }

    public static String getOtaDirSize() {
        checkDirs();
        return AndroidUtilities.formatFileSize(Utilities.getDirSize(otaPath.getAbsolutePath(), 5, true), true);
    }

    public static String getInstalledApkType() {
        try {
            var info = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            switch (info.versionCode % 10) {
                case 1:
                case 3:
                    return "armeabi-v7a";
                case 2:
                case 4:
                    return "x86";
                case 5:
                case 7:
                    return "arm64-v8a";
                case 6:
                case 8:
                    return "x86_64";
                case 0:
                case 9:
                    return "universal";
            }
        } catch (Exception e) {
            return Build.SUPPORTED_ABIS[0];
        }
        return null;
    }

    public static void cleanOtaDir() {
        checkDirs();
        cleanFolder(otaPath);
    }

    public static void cleanFolder(File folder) {
        if (folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    cleanFolder(file);
                }
            }
        }
        folder.delete();
    }

    public static long getMillisFromDate(String d, String format) {
        @SuppressLint("SimpleDateFormat")
        var sdf = new SimpleDateFormat(format);
        try {
            Date date = sdf.parse(d);
            assert date != null;
            return date.getTime();
        } catch (Exception ignore) {
            return 1L;
        }
    }

    public static SpannableStringBuilder replaceTags(CharSequence str) {
        try {
            int start;
            int end;
            StringBuilder stringBuilder = new StringBuilder(str);
            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(str);
            String symbol = "", font = AndroidUtilities.TYPEFACE_ROBOTO_REGULAR;
            for (int i = 0; i < 3; i++) {
                switch (i) {
                    case 0:
                        symbol = "**";
                        font = AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM;
                        break;
                    case 1:
                        symbol = "_";
                        font = AndroidUtilities.TYPEFACE_ROBOTO_ITALIC;
                        break;
                    case 2:
                        symbol = "`";
                        font = AndroidUtilities.TYPEFACE_ROBOTO_MONO;
                        break;
                }
                while ((start = stringBuilder.indexOf(symbol)) != -1) {
                    stringBuilder.replace(start, start + symbol.length(), "");
                    spannableStringBuilder.replace(start, start + symbol.length(), "");
                    end = stringBuilder.indexOf(symbol);
                    if (end >= 0) {
                        stringBuilder.replace(end, end + symbol.length(), "");
                        spannableStringBuilder.replace(end, end + symbol.length(), "");
                        spannableStringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface(font)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
            return spannableStringBuilder;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return new SpannableStringBuilder(str);
    }

    public static class DownloadReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent){
            String action = intent.getAction();
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 1L);
                DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(downloadId);
                Cursor cursor = downloadManager.query(query);
                if (cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    int status = cursor.getInt(columnIndex);
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        installApk(context, apkFile.getAbsolutePath());
                        id = 1L;
                        updateDownloaded = false;
                    } else {
                        // ignore for now
                    }
                }
                cursor.close();
            } else if (DownloadManager.ACTION_NOTIFICATION_CLICKED.equals(action)) {
                try {
                    Intent viewDownloadIntent = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
                    viewDownloadIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(viewDownloadIntent);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
    }
}