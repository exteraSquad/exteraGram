/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export.output.html;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.exteragram.messenger.export.output.OutputFile;

import org.telegram.messenger.FileLog;

import java.io.FileOutputStream;
import java.util.function.UnaryOperator;

final class ExportThumbs {

    private static final int MAX_IMAGE_SIZE = 10000;

    record Thumb(String path, int width, int height) {
        static final Thumb EMPTY = new Thumb("", 0, 0);

        boolean isEmpty() {
            return path.isEmpty();
        }
    }

    record Size(int width, int height) {
        boolean isEmpty() {
            return width <= 0 || height <= 0;
        }
    }

    private ExportThumbs() {
    }

    static UnaryOperator<Size> calculateThumbSize(int maxWidth, int maxHeight, int minWidth, int minHeight, boolean expandForRetina) {
        return largeSize -> {
            int multiplier = expandForRetina ? 2 : 1;
            int checkWidth = largeSize.width() * multiplier;
            int checkHeight = largeSize.height() * multiplier;
            Size smallSize = largeSize;
            if (checkWidth > maxWidth || checkHeight > maxHeight) {
                float scale = Math.min(maxWidth / (float) largeSize.width(), maxHeight / (float) largeSize.height());
                smallSize = new Size(Math.round(largeSize.width() * scale), Math.round(largeSize.height() * scale));
            }
            Size retinaSize = new Size(smallSize.width() & ~1, smallSize.height() & ~1);
            return retinaSize.width() < minWidth || retinaSize.height() < minHeight ? new Size(0, 0) : retinaSize;
        };
    }

    static UnaryOperator<Size> fixedSize(int width, int height) {
        return largeSize -> new Size(width, height);
    }

    static Thumb write(String basePath, String largePath, UnaryOperator<Size> convertSize, boolean png, String postfix) {
        if (largePath == null || largePath.isEmpty()) {
            return Thumb.EMPTY;
        }
        String path = basePath + largePath;
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth >= MAX_IMAGE_SIZE || bounds.outHeight >= MAX_IMAGE_SIZE) {
            return Thumb.EMPTY;
        }
        Size finalSize = convertSize.apply(new Size(bounds.outWidth, bounds.outHeight));
        if (finalSize.isEmpty()) {
            return Thumb.EMPTY;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        int sampleSize = 1;
        while (bounds.outWidth / (sampleSize * 2) >= finalSize.width() && bounds.outHeight / (sampleSize * 2) >= finalSize.height()) {
            sampleSize *= 2;
        }
        options.inSampleSize = sampleSize;
        Bitmap source = null;
        Bitmap scaled = null;
        try {
            source = BitmapFactory.decodeFile(path, options);
            if (source == null) {
                return Thumb.EMPTY;
            }
            scaled = Bitmap.createScaledBitmap(source, finalSize.width(), finalSize.height(), true);
            int lastSlash = largePath.lastIndexOf('/');
            int firstDot = largePath.indexOf('.', lastSlash + 1);
            String thumb = firstDot >= 0
                    ? largePath.substring(0, firstDot) + postfix + (png ? ".png" : ".jpg")
                    : largePath + postfix + (png ? ".png" : ".jpg");
            String result = OutputFile.prepareRelativePath(basePath, thumb);
            try (FileOutputStream stream = new FileOutputStream(basePath + result)) {
                if (!scaled.compress(png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, png ? 100 : 87, stream)) {
                    return Thumb.EMPTY;
                }
            }
            return new Thumb(result, finalSize.width(), finalSize.height());
        } catch (Throwable e) {
            FileLog.e(e);
            return Thumb.EMPTY;
        } finally {
            if (scaled != null && scaled != source) {
                scaled.recycle();
            }
            if (source != null) {
                source.recycle();
            }
        }
    }
}
