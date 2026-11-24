/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pillstack.custom;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;

import com.exteragram.messenger.pillstack.custom.ui.PillImportSheet;
import com.exteragram.messenger.utils.chats.ChatUtils;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ShareAlert;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class CustomPillIO {

    public static final String EXTENSION = ".pill";
    private static final int MAX_SIZE = 64 * 1024;

    private CustomPillIO() {
    }

    public static String export(CustomPillSpec spec) {
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(spec.toJson(false));
    }

    @Nullable
    public static CustomPillSpec parse(@Nullable String text) {
        if (TextUtils.isEmpty(text) || text.length() > MAX_SIZE) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("﻿")) {
            trimmed = trimmed.substring(1);
        }
        if (!trimmed.startsWith("{")) {
            return null;
        }
        try {
            CustomPillSpec spec = CustomPillSpec.fromJson(JsonParser.parseString(trimmed), false);
            if (spec != null) {
                spec.id = 0;
            }
            return spec;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isPillFile(@Nullable String path) {
        return path != null && path.toLowerCase(Locale.ROOT).endsWith(EXTENSION);
    }

    public static boolean isPillFile(@Nullable MessageObject messageObject) {
        if (messageObject == null || messageObject.getDocumentName() == null) {
            return false;
        }
        String path = ChatUtils.getInstance().getPathToMessage(messageObject);
        return !TextUtils.isEmpty(path) && isPillFile(path) && new File(path).exists();
    }

    @Nullable
    public static CustomPillSpec readFile(@Nullable String path) {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        File file = new File(path);
        if (!file.exists() || file.length() > MAX_SIZE) {
            return null;
        }
        try (FileInputStream stream = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int read = 0;
            while (read < data.length) {
                int count = stream.read(data, read, data.length - read);
                if (count < 0) {
                    break;
                }
                read += count;
            }
            return parse(new String(data, 0, read, StandardCharsets.UTF_8));
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public static void handleFile(BaseFragment fragment, MessageObject messageObject) {
        handleFile(fragment, ChatUtils.getInstance().getPathToMessage(messageObject));
    }

    public static void handleFile(BaseFragment fragment, @Nullable String path) {
        CustomPillSpec spec = readFile(path);
        if (spec == null) {
            BulletinFactory.of(fragment).createErrorBulletin(LocaleController.getString(R.string.CustomPillImportInvalid)).show();
            return;
        }
        showImport(fragment, spec);
    }

    public static void showImport(BaseFragment fragment, CustomPillSpec spec) {
        if (!CustomPillStore.canCreate()) {
            BulletinFactory.of(fragment).createErrorBulletin(LocaleController.formatString(R.string.CustomPillLimitReached, CustomPillStore.MAX_PILLS)).show();
            return;
        }
        fragment.showDialog(new PillImportSheet(fragment, spec));
    }

    @Nullable
    public static CustomPillSpec readClipboard(Context context) {
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = clipboard != null ? clipboard.getPrimaryClip() : null;
            if (clip == null || clip.getItemCount() == 0) {
                return null;
            }
            CharSequence text = clip.getItemAt(0).coerceToText(context);
            return text != null ? parse(text.toString()) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static void copy(BaseFragment fragment, CustomPillSpec spec) {
        if (AndroidUtilities.addToClipboard(export(spec))) {
            BulletinFactory.of(fragment).createCopyBulletin(LocaleController.getString(R.string.CustomPillCopied)).show();
        }
    }

    public static void share(BaseFragment fragment, CustomPillSpec spec) {
        String name = spec.name.replaceAll("[\\\\/:*?\"<>|\\s]+", "_").replaceAll("^_+|_+$", "");
        if (TextUtils.isEmpty(name)) {
            name = "pill";
        }
        File file = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), name + EXTENSION);
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(export(spec));
        } catch (Exception e) {
            FileLog.e(e);
            return;
        }
        fragment.showDialog(new ShareAlert(fragment.getParentActivity(), null, null, file.getAbsolutePath(), null, null, false, null, null, false, false, false, null, null) {
            @Override
            protected void onSend(LongSparseArray<TLRPC.Dialog> dids, int count, TLRPC.TL_forumTopic topic, boolean showToast) {
                if (showToast) {
                    AndroidUtilities.runOnUIThread(() -> BulletinFactory.of(fragment).createSimpleBulletin(R.raw.forward, LocaleController.getString(R.string.CustomPillShared)).show(), 250);
                }
            }
        });
    }
}
