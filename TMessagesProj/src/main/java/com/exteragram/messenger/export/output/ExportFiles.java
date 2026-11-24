/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export.output;

import android.content.res.AssetManager;
import android.os.Environment;
import android.text.TextUtils;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.export.ExportFormat;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class ExportFiles {

    private static final String ASSETS_ROOT = "extera/export";
    private static final String[] ASSET_FOLDERS = {"css", "js", "images"};

    private ExportFiles() {
    }

    public static File defaultBaseFolder() {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        String folder = ExteraConfig.getCustomSavePath();
        return TextUtils.isEmpty(folder) ? downloads : new File(downloads, folder);
    }

    public static File baseFolder(String outputFolder) {
        return TextUtils.isEmpty(outputFolder) ? defaultBaseFolder() : new File(outputFolder);
    }

    public static boolean isWritable(File folder) {
        if (!folder.isDirectory() && !folder.mkdirs()) {
            return false;
        }
        File probe = new File(folder, ".export_probe_" + System.nanoTime());
        try {
            if (!probe.createNewFile()) {
                return false;
            }
            return probe.delete() || !probe.exists();
        } catch (IOException | SecurityException e) {
            return false;
        }
    }

    public static String createExportFolder(String outputFolder, boolean singlePeer) {
        String name = (singlePeer ? "ChatExport_" : "DataExport_") + ExportFormat.folderDatePart(System.currentTimeMillis());
        File base = baseFolder(outputFolder);
        File folder = new File(base, name);
        for (int attempt = 1; folder.exists(); attempt++) {
            folder = new File(base, name + " (" + attempt + ")");
        }
        if (!folder.mkdirs()) {
            return null;
        }
        return folder.getAbsolutePath() + "/";
    }

    public static Result copyAssets(String path, OutputFile.Stats stats) {
        AssetManager assets = ApplicationLoader.applicationContext.getAssets();
        new OutputFile(path + "images/.nomedia", null).writeBlock(new byte[0]);
        for (String folder : ASSET_FOLDERS) {
            String[] names;
            try {
                names = assets.list(ASSETS_ROOT + "/" + folder);
            } catch (IOException e) {
                FileLog.e(e);
                return Result.error(path + folder);
            }
            if (names == null) {
                continue;
            }
            for (String name : names) {
                OutputFile output = new OutputFile(path + folder + "/" + name, stats);
                try (InputStream input = assets.open(ASSETS_ROOT + "/" + folder + "/" + name)) {
                    Result result = output.writeBlock(readAll(input));
                    if (!result.isSuccess()) {
                        return result;
                    }
                } catch (IOException e) {
                    FileLog.e(e);
                    return Result.error(output.file().getAbsolutePath());
                }
            }
        }
        return Result.SUCCESS;
    }

    public static String readText(File file) {
        try (InputStream input = new FileInputStream(file)) {
            return new String(readAll(input), StandardCharsets.UTF_8);
        } catch (IOException e) {
            FileLog.e(e);
            return null;
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    public static String fileNameFromUserString(String name) {
        StringBuilder result = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            boolean bad = ch < 32
                    || ch == '‎' || ch == '‏'
                    || ch == '‪' || ch == '‫' || ch == '‭' || ch == '‮'
                    || ch == '⁦' || ch == '⁧'
                    || ch == '/' || ch == '\\' || ch == '<' || ch == '>' || ch == ':'
                    || ch == '"' || ch == '|' || ch == '?' || ch == '*';
            result.append(bad ? '_' : ch);
        }
        int length = result.length();
        if (length == 0 || result.charAt(length - 1) == ' ' || result.charAt(length - 1) == '.') {
            result.append('_');
        }
        return result.toString();
    }
}
