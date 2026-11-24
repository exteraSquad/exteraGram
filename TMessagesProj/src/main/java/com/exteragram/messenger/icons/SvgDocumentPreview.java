/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.icons;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Xml;

import com.caverock.androidsvg.SVG;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.xmlpull.v1.XmlPullParser;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;

public final class SvgDocumentPreview {
    public static final String THUMB_FILTER = "svg_document_thumb";

    public static final int MAX_FILE_SIZE = 16 * 1024 * 1024;
    private static final int VIEWER_SIZE = 2048;
    private static final int MAX_ELEMENTS = 20_000;
    private static final int MAX_DEPTH = 64;
    private static final int MAX_TEXT_LENGTH = 2 * 1024 * 1024;
    private static final int MAX_ATTRIBUTE_LENGTH = 256 * 1024;
    private static final int MAX_EMBEDDED_IMAGE_LENGTH = 2 * 1024 * 1024;
    private static final int MAX_EMBEDDED_IMAGE_PIXELS = 1_048_576;
    private static final int MAX_EMBEDDED_PIXELS = 2 * MAX_EMBEDDED_IMAGE_PIXELS;
    private static final int MAX_DRAWN_IMAGE_PIXELS = 16 * MAX_EMBEDDED_IMAGE_PIXELS;
    private static final int MAX_LAYER_PIXELS = 16 * MAX_EMBEDDED_IMAGE_PIXELS;
    private static final int MAX_RENDER_OPERATIONS = 200_000;
    private static final long MAX_RENDER_MILLIS = 1_500;
    private static final int CONTRAST_SAMPLE_SIZE = 64;
    private static final float MIN_TRANSPARENT_FRACTION = 0.05f;
    private static final float LIGHT_CONTENT_LUMINANCE = 0.7f;
    private static final float DARK_CONTENT_LUMINANCE = 0.3f;
    private static final int DARK_BACKDROP = 0xFF2B2B2B;
    private static final int LIGHT_BACKDROP = 0xFFF2F2F2;

    private static final Map<ImageReceiver, MessageObject> pendingThumbnails = new WeakHashMap<>();

    private SvgDocumentPreview() {}

    public static boolean isSvg(TLRPC.Document document) {
        if (document == null) {
            return false;
        }
        if ("image/svg+xml".equalsIgnoreCase(document.mime_type)) {
            return true;
        }
        String fileName = FileLoader.getDocumentFileName(document);
        return fileName.regionMatches(true, fileName.length() - 4, ".svg", 0, 4);
    }

    public static boolean isHidden(MessageObject message) {
        return isSvg(message.getDocument())
                && (message.needDrawBluredPreview() || message.hasMediaSpoilers() && !message.isMediaSpoilersRevealed);
    }

    public static ImageLocation getImageLocation(MessageObject message, boolean allowDownload) {
        TLRPC.Document document = message.getDocument();
        if (!isSvg(document) || document.size > MAX_FILE_SIZE || isHidden(message)) {
            return null;
        }
        File cached = message.mediaExists ? FileLoader.getInstance(message.currentAccount).getPathToMessage(message.messageOwner) : null;
        ImageLocation location;
        if (message.attachPathExists) {
            location = ImageLocation.getForPath(message.messageOwner.attachPath);
        } else if (cached != null && (!allowDownload || cached.exists())) {
            location = ImageLocation.getForPath(cached.getAbsolutePath());
        } else if (allowDownload) {
            location = ImageLocation.getForDocument(document);
        } else {
            return null;
        }
        location.imageType = FileLoader.IMAGE_TYPE_SVG_DOCUMENT;
        return location;
    }

    public static boolean setThumbnail(ImageReceiver receiver, MessageObject message, ImageLocation thumbLocation, String thumbFilter, Drawable thumb) {
        return setThumbnail(receiver, message, thumbLocation, thumbFilter, thumb, false);
    }

    private static boolean setThumbnail(ImageReceiver receiver, MessageObject message, ImageLocation thumbLocation, String thumbFilter, Drawable thumb, boolean retried) {
        pendingThumbnails.remove(receiver);
        ImageLocation location = getImageLocation(message, false);
        if (location == null) {
            return false;
        }
        if (location.path != null && !new File(location.path).exists()) {
            if (!retried) {
                pendingThumbnails.put(receiver, message);
                ImageLocation retryThumbLocation = thumbLocation;
                String retryThumbFilter = thumbFilter;
                FileLoader.getInstance(message.currentAccount).getFileDatabase().getQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> {
                    if (pendingThumbnails.get(receiver) == message) {
                        setThumbnail(receiver, message, retryThumbLocation, retryThumbFilter, thumb, true);
                    }
                }));
            }
            return !retried;
        }
        if (thumbLocation == null) {
            TLRPC.Document document = message.getDocument();
            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320);
            if (size != null && !(size instanceof TLRPC.TL_photoSizeEmpty)) {
                thumbLocation = ImageLocation.getForDocument(size, document);
                thumbFilter = "40_40";
            }
        }
        receiver.setImage(location, THUMB_FILTER, thumbLocation, thumbFilter, thumb, 0, null, message, 1);
        return true;
    }

    public static BitmapDrawable decode(File file, boolean thumbnail) {
        if (file == null || !file.isFile() || file.length() > MAX_FILE_SIZE) {
            return null;
        }

        Bitmap bitmap = null;
        try {
            SVG svg = load(file);

            float sourceWidth = svg.getDocumentWidth();
            float sourceHeight = svg.getDocumentHeight();
            if (!(sourceWidth > 0 && sourceHeight > 0 && Float.isFinite(sourceWidth) && Float.isFinite(sourceHeight))) {
                RectF viewBox = svg.getDocumentViewBox();
                sourceWidth = viewBox != null ? viewBox.width() : 300;
                sourceHeight = viewBox != null ? viewBox.height() : 150;
            }
            if (!(sourceWidth > 0 && sourceHeight > 0 && Float.isFinite(sourceWidth) && Float.isFinite(sourceHeight))) {
                throw new PreviewLimitException("invalid viewport");
            }

            int size = thumbnail ? AndroidUtilities.dp(68) : VIEWER_SIZE;
            int width = Math.max(1, Math.round(size * (sourceWidth / Math.max(sourceWidth, sourceHeight))));
            int height = Math.max(1, Math.round(size * (sourceHeight / Math.max(sourceWidth, sourceHeight))));
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new PreviewCanvas(bitmap);
            canvas.scale(width / sourceWidth, height / sourceHeight);
            svg.renderToCanvas(canvas, new RectF(0, 0, sourceWidth, sourceHeight));
            applyContrastBackdrop(bitmap);
            return new BitmapDrawable(ApplicationLoader.applicationContext.getResources(), bitmap);
        } catch (Exception | StackOverflowError | OutOfMemoryError e) {
            if (bitmap != null) {
                bitmap.recycle();
            }
            if (!(e instanceof PreviewLimitException)) {
                FileLog.e(e);
            }
            return null;
        }
    }

    static Bitmap renderIcon(File file, int width, int height) {
        if (file == null || !file.isFile() || file.length() > MAX_FILE_SIZE) {
            return null;
        }

        Bitmap bitmap = null;
        try {
            SVG svg = load(file);
            svg.setDocumentWidth(width);
            svg.setDocumentHeight(height);
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            svg.renderToCanvas(new PreviewCanvas(bitmap));
            return bitmap;
        } catch (Exception | StackOverflowError | OutOfMemoryError e) {
            if (bitmap != null) {
                bitmap.recycle();
            }
            if (!(e instanceof PreviewLimitException)) {
                FileLog.e(e);
            }
            return null;
        }
    }

    private static SVG load(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file)) {
            validate(new BufferedInputStream(input));
            input.getChannel().position(0);
            return SVG.getFromInputStream(input);
        }
    }

    private static void applyContrastBackdrop(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scale = Math.min(1f, (float) CONTRAST_SAMPLE_SIZE / Math.max(width, height));
        int sampleWidth = Math.max(1, Math.round(width * scale));
        int sampleHeight = Math.max(1, Math.round(height * scale));
        Bitmap sample = scale < 1f ? Bitmap.createScaledBitmap(bitmap, sampleWidth, sampleHeight, true) : bitmap;
        int[] pixels = new int[sampleWidth * sampleHeight];
        sample.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight);
        if (sample != bitmap) {
            sample.recycle();
        }
        int transparent = 0;
        double weight = 0;
        double luminance = 0;
        for (int pixel : pixels) {
            int alpha = pixel >>> 24;
            if (alpha < 128) {
                transparent++;
            }
            if (alpha == 0) {
                continue;
            }
            float a = alpha / 255f;
            float r = ((pixel >> 16) & 0xFF) / 255f;
            float g = ((pixel >> 8) & 0xFF) / 255f;
            float b = (pixel & 0xFF) / 255f;
            luminance += (0.2126f * r + 0.7152f * g + 0.0722f * b) * a;
            weight += a;
        }
        float transparentFraction = (float) transparent / pixels.length;
        float contentLuminance = weight > 0 ? (float) (luminance / weight) : 0.5f;
        int backdrop = 0;
        if (transparentFraction >= MIN_TRANSPARENT_FRACTION) {
            if (contentLuminance >= LIGHT_CONTENT_LUMINANCE) {
                backdrop = DARK_BACKDROP;
            } else if (contentLuminance <= DARK_CONTENT_LUMINANCE) {
                backdrop = LIGHT_BACKDROP;
            }
        }
        if (backdrop != 0) {
            new Canvas(bitmap).drawColor(backdrop, PorterDuff.Mode.DST_OVER);
        }
    }

    private static void validate(BufferedInputStream input) throws Exception {
        input.mark(2);
        if (input.read() == 0x1f && input.read() == 0x8b) {
            throw new PreviewLimitException("compressed SVG");
        }
        input.reset();
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        parser.setInput(input, null);
        int elements = 0;
        long textLength = 0;
        long embeddedPixels = 0;
        for (int event = parser.nextToken(); event != XmlPullParser.END_DOCUMENT; event = parser.nextToken()) {
            if (event == XmlPullParser.DOCDECL) {
                String declaration = parser.getText();
                if (declaration == null || declaration.indexOf('[') >= 0) {
                    throw new PreviewLimitException("internal document declaration");
                }
            } else if (event == XmlPullParser.START_TAG) {
                if (++elements > MAX_ELEMENTS || parser.getDepth() > MAX_DEPTH) {
                    throw new PreviewLimitException("XML complexity");
                }
                String name = parser.getName();
                if ("pattern".equals(name)) {
                    throw new PreviewLimitException("pattern rendering");
                }
                for (int i = 0; i < parser.getAttributeCount(); i++) {
                    String value = parser.getAttributeValue(i);
                    if ("image".equals(name) && "href".equals(parser.getAttributeName(i))) {
                        embeddedPixels += getEmbeddedImagePixels(value.trim());
                        if (embeddedPixels > MAX_EMBEDDED_PIXELS) {
                            throw new PreviewLimitException("combined embedded image pixels");
                        }
                    } else {
                        if (value.length() > MAX_ATTRIBUTE_LENGTH) {
                            throw new PreviewLimitException("attribute size");
                        }
                        textLength += value.length();
                    }
                }
            } else if (event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT) {
                textLength += parser.getText().length();
            }
            if (textLength > MAX_TEXT_LENGTH) {
                throw new PreviewLimitException("XML text size");
            }
        }
    }

    private static long getEmbeddedImagePixels(String href) {
        int comma = href.indexOf(',');
        if (!href.startsWith("data:image/") || comma < 12 || !href.regionMatches(comma - 7, ";base64", 0, 7)
                || href.length() - comma - 1 > MAX_EMBEDDED_IMAGE_LENGTH) {
            throw new PreviewLimitException("embedded image source or encoded size");
        }
        byte[] data = Base64.decode(href.substring(comma + 1), Base64.DEFAULT);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);
        long pixels = (long) options.outWidth * options.outHeight;
        if (options.outWidth <= 0 || options.outHeight <= 0 || pixels > MAX_EMBEDDED_IMAGE_PIXELS) {
            throw new PreviewLimitException("embedded image dimensions");
        }
        return pixels;
    }

    private static final class PreviewCanvas extends Canvas {
        private final long deadline = SystemClock.uptimeMillis() + MAX_RENDER_MILLIS;
        private final long bitmapPixels;
        private final ArrayDeque<Integer> layers = new ArrayDeque<>();
        private int operations;
        private long drawnImagePixels;

        PreviewCanvas(Bitmap bitmap) {
            super(bitmap);
            bitmapPixels = (long) bitmap.getWidth() * bitmap.getHeight();
        }

        private void checkRenderBudget() {
            if (++operations > MAX_RENDER_OPERATIONS || getSaveCount() > MAX_DEPTH * 2 || SystemClock.uptimeMillis() > deadline) {
                throw new PreviewLimitException("render budget");
            }
        }

        private void checkLayerBudget() {
            checkRenderBudget();
            int count = getSaveCount();
            while (!layers.isEmpty() && layers.peek() >= count) {
                layers.pop();
            }
            if (bitmapPixels * (layers.size() + 1) > MAX_LAYER_PIXELS) {
                throw new PreviewLimitException("compositing layers");
            }
            layers.push(count);
        }

        @Override
        public int save() {
            checkRenderBudget();
            return super.save();
        }

        @SuppressWarnings("unused")
        public int save(int saveFlags) {
            checkRenderBudget();
            return super.save();
        }

        @Override
        public void drawPath(Path path, Paint paint) {
            checkRenderBudget();
            super.drawPath(path, paint);
        }

        @Override
        @SuppressWarnings("deprecation")
        public int saveLayer(RectF bounds, Paint paint, int saveFlags) {
            checkLayerBudget();
            return super.saveLayer(bounds, paint, saveFlags);
        }

        @Override
        @SuppressWarnings("deprecation")
        public int saveLayerAlpha(RectF bounds, int alpha, int saveFlags) {
            checkLayerBudget();
            return super.saveLayerAlpha(bounds, alpha, saveFlags);
        }

        @Override
        public void drawBitmap(Bitmap bitmap, float left, float top, Paint paint) {
            checkRenderBudget();
            drawnImagePixels += (long) bitmap.getWidth() * bitmap.getHeight();
            if (drawnImagePixels > MAX_DRAWN_IMAGE_PIXELS) {
                throw new PreviewLimitException("repeated embedded images");
            }
            super.drawBitmap(bitmap, left, top, paint);
        }
    }

    private static final class PreviewLimitException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        PreviewLimitException(String message) {
            super(message);
        }
    }
}
