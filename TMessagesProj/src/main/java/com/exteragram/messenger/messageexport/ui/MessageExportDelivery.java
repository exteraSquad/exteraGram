/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.messageexport.ui;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;

import androidx.collection.LongSparseArray;
import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ShareAlert;

import java.io.File;
import java.io.FileOutputStream;

final class MessageExportDelivery {

    private static final int STICKER_SIZE = 512;

    private MessageExportDelivery() {
    }

    @SuppressWarnings("deprecation")
    static void createSticker(File source, Utilities.Callback<File> callback) {
        Utilities.globalQueue.postRunnable(() -> {
            File result = null;
            Bitmap decoded = null;
            Bitmap scaled = null;
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(source.getAbsolutePath(), options);
                int max = Math.max(options.outWidth, options.outHeight);
                if (max > 0) {
                    int sample = 1;
                    while (max / (sample * 2) >= STICKER_SIZE * 2) {
                        sample *= 2;
                    }
                    options.inJustDecodeBounds = false;
                    options.inSampleSize = sample;
                    decoded = BitmapFactory.decodeFile(source.getAbsolutePath(), options);
                }
                if (decoded != null) {
                    Bitmap trimmed = trimTransparent(decoded);
                    if (trimmed != decoded) {
                        decoded.recycle();
                        decoded = trimmed;
                    }
                    float scale = Math.min(1f, STICKER_SIZE / (float) Math.max(decoded.getWidth(), decoded.getHeight()));
                    int width = Math.max(1, Math.round(decoded.getWidth() * scale));
                    int height = Math.max(1, Math.round(decoded.getHeight() * scale));
                    scaled = width == decoded.getWidth() && height == decoded.getHeight() ? decoded : Bitmap.createScaledBitmap(decoded, width, height, true);
                    File target = new File(source.getParentFile(), source.getName().replace(".png", "") + "_sticker.webp");
                    try (FileOutputStream out = new FileOutputStream(target)) {
                        Bitmap.CompressFormat format = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
                        if (scaled.compress(format, 95, out)) {
                            result = target;
                        }
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                if (scaled != null && scaled != decoded) {
                    scaled.recycle();
                }
                if (decoded != null) {
                    decoded.recycle();
                }
            }
            File file = result;
            AndroidUtilities.runOnUIThread(() -> callback.run(file));
        });
    }

    static boolean copyToClipboard(Context context, File file) {
        try {
            Uri uri = FileProvider.getUriForFile(context, ApplicationLoader.getApplicationId() + ".provider", file);
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null) {
                return false;
            }
            clipboard.setPrimaryClip(ClipData.newUri(context.getContentResolver(), "image", uri));
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    static boolean ensureSavePermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= 29 || activity == null) {
            return true;
        }
        if (activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
        return false;
    }

    static void saveToGallery(Context context, File file, Runnable onSaved) {
        MediaController.saveFile(file.getAbsolutePath(), context, 0, file.getName(), "image/png", uri -> onSaved.run(), false);
    }

    private static Bitmap trimTransparent(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] row = new int[width];
        int left = width, right = -1, top = height, bottom = -1;
        for (int y = 0; y < height; y++) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1);
            for (int x = 0; x < width; x++) {
                if ((row[x] >>> 24) != 0) {
                    if (x < left) {
                        left = x;
                    }
                    if (x > right) {
                        right = x;
                    }
                    if (y < top) {
                        top = y;
                    }
                    bottom = y;
                }
            }
        }
        if (right < left || bottom < top || left == 0 && top == 0 && right == width - 1 && bottom == height - 1) {
            return bitmap;
        }
        return Bitmap.createBitmap(bitmap, left, top, right - left + 1, bottom - top + 1);
    }

    static void shareInApp(ChatActivity fragment, File file, boolean asPhoto, boolean asSticker, Utilities.Callback<LongSparseArray<TLRPC.Dialog>> onSent) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        ShareAlert alert = new ShareAlert(fragment.getParentActivity(), fragment, null, file.getAbsolutePath(), null, null, false, null, null, false, false, false, null, fragment.getResourceProvider()) {
            @Override
            protected boolean sendFileWithCaption() {
                return true;
            }

            @Override
            protected boolean sendFileAsPhoto() {
                return asPhoto || asSticker;
            }

            @Override
            protected boolean sendFileCommentSeparately() {
                return asSticker;
            }

            @Override
            protected boolean sendFileAsHighQualityPhoto() {
                return asPhoto;
            }

            @Override
            protected void onSend(LongSparseArray<TLRPC.Dialog> dids, int count, TLRPC.TL_forumTopic topic, boolean showToast) {
                super.onSend(dids, count, topic, showToast);
                onSent.run(dids);
            }
        };
        alert.show();
    }
}
