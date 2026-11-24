/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public final class CoverDecoder {

    private static final int TARGET_SIZE = 1280;
    private static final int MAX_SIZE = 1600;

    private CoverDecoder() {
    }

    public static Bitmap decode(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        int size = Math.max(options.outWidth, options.outHeight);
        options.inJustDecodeBounds = false;
        options.inSampleSize = 1;
        while (size / 2 >= TARGET_SIZE) {
            options.inSampleSize *= 2;
            size /= 2;
        }
        final Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        if (bitmap == null) {
            return null;
        }
        final int largest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (largest <= MAX_SIZE) {
            return bitmap;
        }
        final float scale = (float) TARGET_SIZE / largest;
        final Bitmap scaled = Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(bitmap.getWidth() * scale)), Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
        if (scaled != bitmap) {
            bitmap.recycle();
        }
        return scaled;
    }
}
