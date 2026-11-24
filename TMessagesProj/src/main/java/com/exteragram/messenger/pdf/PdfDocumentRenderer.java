/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pdf;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.graphics.pdf.PdfRendererPreV;
import android.graphics.pdf.RenderParams;
import android.graphics.pdf.content.PdfPageGotoLinkContent;
import android.graphics.pdf.content.PdfPageLinkContent;
import android.graphics.pdf.content.PdfPageTextContent;
import android.graphics.pdf.models.PageMatchBounds;
import android.graphics.pdf.models.selection.PageSelection;
import android.graphics.pdf.models.selection.SelectionBoundary;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.os.ext.SdkExtensions;

import androidx.annotation.RequiresExtension;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PdfDocumentRenderer {

    public static final int JOB_BASE = 0;
    public static final int JOB_DETAIL = 1;
    public static final int JOB_MEASURE = 2;
    public static final int JOB_SEARCH = 3;
    public static final int JOB_SELECT = 4;

    private static final int MAX_BITMAP_SIDE = 4096;
    private static final long MEASURE_TIME_BUDGET = 24;
    private static final long SEARCH_TIME_BUDGET = 40;

    public static final class Job {

        public final int type;
        public final int page;
        public final int count;
        public int measuredCount;

        public int targetWidth;
        public int maxPixels;
        public boolean loadLinks;

        public float pageWidthPx;
        public float displayWidth;
        public float left, top, right, bottom;
        public int width, height;

        public String query;
        public int generation;

        public int stopPage;
        public int startIndex = -1, stopIndex = -1;
        public float startX, startY, stopX, stopY;
        public int[] fullPages;
        public ArrayList<SelectedPage> selectedPages;

        public Bitmap bitmap;
        public float pageWidth, pageHeight;
        public float[] sizes;
        public ArrayList<Link> links;
        public ArrayList<ArrayList<RectF[]>> matches;

        private Job(int type, int page, int count) {
            this.type = type;
            this.page = page;
            this.count = count;
        }

        public static Job base(int page, int targetWidth, int maxPixels, boolean loadLinks) {
            final Job job = new Job(JOB_BASE, page, 1);
            job.targetWidth = targetWidth;
            job.maxPixels = maxPixels;
            job.loadLinks = loadLinks;
            return job;
        }

        public static Job detail(int page, float pageWidthPx, float displayWidth, float left, float top, float right, float bottom, int width, int height) {
            final Job job = new Job(JOB_DETAIL, page, 1);
            job.pageWidthPx = pageWidthPx;
            job.displayWidth = displayWidth;
            job.left = left;
            job.top = top;
            job.right = right;
            job.bottom = bottom;
            job.width = width;
            job.height = height;
            return job;
        }

        public static Job measure(int page, int count) {
            return new Job(JOB_MEASURE, page, count);
        }

        public static Job search(int page, int count, String query, int generation) {
            final Job job = new Job(JOB_SEARCH, page, count);
            job.query = query;
            job.generation = generation;
            return job;
        }

        public static Job select(int generation, int startPage, int startIndex, float startX, float startY, int stopPage, int stopIndex, float stopX, float stopY, int[] fullPages) {
            final Job job = new Job(JOB_SELECT, startPage, stopPage - startPage + 1);
            job.generation = generation;
            job.startIndex = startIndex;
            job.startX = startX;
            job.startY = startY;
            job.stopPage = stopPage;
            job.stopIndex = stopIndex;
            job.stopX = stopX;
            job.stopY = stopY;
            job.fullPages = fullPages;
            return job;
        }
    }

    public static final class SelectedPage {

        public final int page;
        public final String text;
        public final RectF[] rects;
        public final int[] boundaryIndex = {-1, -1};
        public final float[] boundaryX = {Float.NaN, Float.NaN};
        public final float[] boundaryY = {Float.NaN, Float.NaN};

        SelectedPage(int page, String text, RectF[] rects) {
            this.page = page;
            this.text = text;
            this.rects = rects;
        }
    }

    public static final class Link {

        public final RectF bounds;
        public final String url;
        public final int page;
        public final float y;

        Link(RectF bounds, String url, int page, float y) {
            this.bounds = bounds;
            this.url = url;
            this.page = page;
            this.y = y;
        }
    }

    private interface DocumentSource {

        int getPageCount();

        PageSource openPage(int index);

        boolean supportsText();

        void close();
    }

    private interface PageSource extends AutoCloseable {

        int getWidth();

        int getHeight();

        void render(Bitmap bitmap, Matrix matrix);

        List<PageMatchBounds> searchText(String query);

        List<PdfPageLinkContent> getLinkContents();

        List<PdfPageGotoLinkContent> getGotoLinks();

        PageSelection selectContent(SelectionBoundary start, SelectionBoundary stop);

        @Override
        void close();
    }

    private final DispatchQueue queue;
    private final DocumentSource source;
    private final Matrix matrix = new Matrix();
    private boolean closed;

    public final int pageCount;
    public final float firstPageWidth;
    public final float firstPageHeight;
    public final boolean supportsSearch;

    private PdfDocumentRenderer(DispatchQueue queue, DocumentSource source, float firstPageWidth, float firstPageHeight) {
        this.queue = queue;
        this.source = source;
        this.pageCount = source.getPageCount();
        this.firstPageWidth = firstPageWidth;
        this.firstPageHeight = firstPageHeight;
        this.supportsSearch = source.supportsText();
    }

    public static void open(File file, Utilities.Callback2<PdfDocumentRenderer, Throwable> callback) {
        final DispatchQueue queue = new DispatchQueue("pdfRenderer");
        queue.postRunnable(() -> {
            DocumentSource source = null;
            try {
                source = createSource(file);
                if (source.getPageCount() <= 0) {
                    throw new IOException("pdf has no pages");
                }
                final float width, height;
                try (PageSource page = source.openPage(0)) {
                    width = Math.max(1, page.getWidth());
                    height = Math.max(1, page.getHeight());
                }
                final PdfDocumentRenderer document = new PdfDocumentRenderer(queue, source, width, height);
                AndroidUtilities.runOnUIThread(() -> callback.run(document, null));
            } catch (Throwable e) {
                if (source != null) {
                    try {
                        source.close();
                    } catch (Throwable ignore) {

                    }
                }
                queue.recycle();
                AndroidUtilities.runOnUIThread(() -> callback.run(null, e));
            }
        });
    }

    private static DocumentSource createSource(File file) throws IOException {
        if (Build.VERSION.SDK_INT >= 31 && Build.VERSION.SDK_INT < 35 && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13) {
            final ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            try {
                return new PreVDocumentSource(descriptor);
            } catch (IOException | RuntimeException e) {
                FileLog.e(e);
                closeQuietly(descriptor);
            }
        }
        final ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        try {
            return new FrameworkDocumentSource(descriptor);
        } catch (IOException | RuntimeException e) {
            closeQuietly(descriptor);
            throw e;
        }
    }

    private static void closeQuietly(ParcelFileDescriptor descriptor) {
        try {
            descriptor.close();
        } catch (Throwable ignore) {

        }
    }

    public void run(Job job, Utilities.Callback<Job> done) {
        queue.postRunnable(() -> {
            if (!closed) {
                try {
                    switch (job.type) {
                        case JOB_BASE:
                            renderBase(job);
                            break;
                        case JOB_DETAIL:
                            renderDetail(job);
                            break;
                        case JOB_MEASURE:
                            measure(job);
                            break;
                        case JOB_SEARCH:
                            search(job);
                            break;
                        case JOB_SELECT:
                            select(job);
                            break;
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                    if (job.bitmap != null) {
                        job.bitmap.recycle();
                        job.bitmap = null;
                    }
                }
            }
            AndroidUtilities.runOnUIThread(() -> done.run(job));
        });
    }

    public void close() {
        queue.postRunnable(() -> {
            if (closed) {
                return;
            }
            closed = true;
            try {
                source.close();
            } catch (Throwable e) {
                FileLog.e(e);
            }
            queue.recycle();
        });
    }

    private void renderBase(Job job) {
        try (PageSource page = source.openPage(job.page)) {
            job.pageWidth = Math.max(1, page.getWidth());
            job.pageHeight = Math.max(1, page.getHeight());
            float scale = job.targetWidth / job.pageWidth;
            if (job.pageWidth * job.pageHeight * scale * scale > job.maxPixels) {
                scale = (float) Math.sqrt(job.maxPixels / (job.pageWidth * job.pageHeight));
            }
            scale = Math.min(scale, MAX_BITMAP_SIDE / Math.max(job.pageWidth, job.pageHeight));
            final int width = Math.max(1, Math.round(job.pageWidth * scale));
            final int height = Math.max(1, Math.round(job.pageHeight * scale));
            job.bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            job.bitmap.eraseColor(Color.WHITE);
            matrix.setScale(width / job.pageWidth, height / job.pageHeight);
            page.render(job.bitmap, matrix);
            if (job.loadLinks && source.supportsText()) {
                job.links = loadLinks(page, job.pageWidth, job.pageHeight);
            }
        }
    }

    private void renderDetail(Job job) {
        try (PageSource page = source.openPage(job.page)) {
            job.pageWidth = Math.max(1, page.getWidth());
            job.pageHeight = Math.max(1, page.getHeight());
            final float scale = job.pageWidthPx / job.pageWidth;
            final float pageHeightPx = job.pageHeight * scale;
            job.bitmap = Bitmap.createBitmap(job.width, job.height, Bitmap.Config.ARGB_8888);
            job.bitmap.eraseColor(Color.WHITE);
            matrix.setScale(scale, scale);
            matrix.postTranslate(-job.left * job.pageWidthPx, -job.top * pageHeightPx);
            page.render(job.bitmap, matrix);
        }
    }

    private void measure(Job job) {
        job.sizes = new float[job.count * 2];
        final long start = SystemClock.elapsedRealtime();
        for (int i = 0; i < job.count && (i == 0 || SystemClock.elapsedRealtime() - start < MEASURE_TIME_BUDGET); i++) {
            job.measuredCount = i + 1;
            try (PageSource page = source.openPage(job.page + i)) {
                job.sizes[i * 2] = Math.max(1, page.getWidth());
                job.sizes[i * 2 + 1] = Math.max(1, page.getHeight());
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
    }

    @SuppressLint("NewApi")
    private void search(Job job) {
        job.sizes = new float[job.count * 2];
        job.matches = new ArrayList<>(job.count);
        final long start = SystemClock.elapsedRealtime();
        for (int i = 0; i < job.count && (i == 0 || SystemClock.elapsedRealtime() - start < SEARCH_TIME_BUDGET); i++) {
            job.measuredCount = i + 1;
            final ArrayList<RectF[]> pageMatches = new ArrayList<>();
            try (PageSource page = source.openPage(job.page + i)) {
                final float width = Math.max(1, page.getWidth());
                final float height = Math.max(1, page.getHeight());
                job.sizes[i * 2] = width;
                job.sizes[i * 2 + 1] = height;
                final List<PageMatchBounds> results = page.searchText(job.query);
                for (int a = 0; a < results.size(); a++) {
                    final List<RectF> bounds = results.get(a).getBounds();
                    if (bounds.isEmpty()) {
                        continue;
                    }
                    final RectF[] rects = new RectF[bounds.size()];
                    for (int b = 0; b < rects.length; b++) {
                        rects[b] = normalize(bounds.get(b), width, height);
                    }
                    pageMatches.add(rects);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            job.matches.add(pageMatches);
        }
    }

    private void select(Job job) {
        job.selectedPages = new ArrayList<>();
        if (job.page == job.stopPage) {
            addSelection(job, job.page, job.startIndex, job.startX, job.startY, job.stopIndex, job.stopX, job.stopY, false);
            return;
        }
        addSelection(job, job.page, job.startIndex, job.startX, job.startY, Integer.MAX_VALUE, 0, 0, false);
        if (job.fullPages != null) {
            for (int page : job.fullPages) {
                addSelection(job, page, 0, 0, 0, Integer.MAX_VALUE, 0, 0, true);
            }
        }
        addSelection(job, job.stopPage, 0, 0, 0, job.stopIndex, job.stopX, job.stopY, false);
    }

    private void addSelection(Job job, int pageIndex, int startIndex, float startX, float startY, int stopIndex, float stopX, float stopY, boolean keepEmpty) {
        SelectedPage selection = null;
        try (PageSource page = source.openPage(pageIndex)) {
            selection = selectPage(page, pageIndex, startIndex, startX, startY, stopIndex, stopX, stopY);
        } catch (Throwable e) {
            FileLog.e(e);
        }
        if (selection != null) {
            job.selectedPages.add(selection);
        } else if (keepEmpty) {
            job.selectedPages.add(new SelectedPage(pageIndex, "", new RectF[0]));
        }
    }

    @SuppressLint("NewApi")
    private static SelectedPage selectPage(PageSource page, int pageIndex, int startIndex, float startX, float startY, int stopIndex, float stopX, float stopY) {
        final float width = Math.max(1, page.getWidth());
        final float height = Math.max(1, page.getHeight());
        final SelectionBoundary start = startIndex >= 0 ? new SelectionBoundary(startIndex) : new SelectionBoundary(new Point(Math.round(startX * width), Math.round(startY * height)));
        final SelectionBoundary stop = stopIndex >= 0 ? new SelectionBoundary(stopIndex) : new SelectionBoundary(new Point(Math.round(stopX * width), Math.round(stopY * height)));
        final PageSelection selection = page.selectContent(start, stop);
        if (selection == null) {
            return null;
        }
        final StringBuilder text = new StringBuilder();
        final ArrayList<RectF> rects = new ArrayList<>();
        for (PdfPageTextContent content : selection.getSelectedTextContents()) {
            text.append(content.getText());
            for (RectF bounds : content.getBounds()) {
                rects.add(normalize(bounds, width, height));
            }
        }
        if (text.length() == 0 || rects.isEmpty()) {
            return null;
        }
        final SelectedPage result = new SelectedPage(pageIndex, text.toString(), rects.toArray(new RectF[0]));
        for (int i = 0; i < 2; i++) {
            final SelectionBoundary boundary = i == 0 ? selection.getStart() : selection.getStop();
            result.boundaryIndex[i] = boundary.getIndex();
            final Point point = boundary.getPoint();
            if (point != null) {
                result.boundaryX[i] = point.x / width;
                result.boundaryY[i] = point.y / height;
            }
        }
        return result;
    }

    @SuppressLint("NewApi")
    private static ArrayList<Link> loadLinks(PageSource page, float width, float height) {
        final ArrayList<Link> links = new ArrayList<>();
        try {
            final List<PdfPageLinkContent> contents = page.getLinkContents();
            for (int i = 0; i < contents.size(); i++) {
                final PdfPageLinkContent content = contents.get(i);
                final String url = content.getUri().toString();
                for (RectF bounds : content.getBounds()) {
                    links.add(new Link(normalize(bounds, width, height), url, -1, 0));
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        try {
            final List<PdfPageGotoLinkContent> contents = page.getGotoLinks();
            for (int i = 0; i < contents.size(); i++) {
                final PdfPageGotoLinkContent content = contents.get(i);
                final PdfPageGotoLinkContent.Destination destination = content.getDestination();
                final float y = Utilities.clamp(destination.getYCoordinate() / height, 1f, 0f);
                for (RectF bounds : content.getBounds()) {
                    links.add(new Link(normalize(bounds, width, height), null, destination.getPageNumber(), y));
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return links;
    }

    private static RectF normalize(RectF bounds, float width, float height) {
        return new RectF(
                Math.min(bounds.left, bounds.right) / width,
                Math.min(bounds.top, bounds.bottom) / height,
                Math.max(bounds.left, bounds.right) / width,
                Math.max(bounds.top, bounds.bottom) / height
        );
    }

    private static final class FrameworkDocumentSource implements DocumentSource {

        private final PdfRenderer renderer;
        private Object renderParams;

        FrameworkDocumentSource(ParcelFileDescriptor descriptor) throws IOException {
            renderer = new PdfRenderer(descriptor);
        }

        @Override
        public int getPageCount() {
            return renderer.getPageCount();
        }

        @Override
        public boolean supportsText() {
            return Build.VERSION.SDK_INT >= 35;
        }

        @Override
        public void close() {
            renderer.close();
        }

        @Override
        public PageSource openPage(int index) {
            final PdfRenderer.Page page = renderer.openPage(index);
            return new PageSource() {
                @Override
                public int getWidth() {
                    return page.getWidth();
                }

                @Override
                public int getHeight() {
                    return page.getHeight();
                }

                @Override
                public void render(Bitmap bitmap, Matrix matrix) {
                    if (Build.VERSION.SDK_INT >= 35) {
                        if (renderParams == null) {
                            renderParams = new RenderParams.Builder(RenderParams.RENDER_MODE_FOR_DISPLAY)
                                    .setRenderFlags(RenderParams.FLAG_RENDER_HIGHLIGHT_ANNOTATIONS | RenderParams.FLAG_RENDER_TEXT_ANNOTATIONS)
                                    .build();
                        }
                        page.render(bitmap, null, matrix, (RenderParams) renderParams);
                    } else {
                        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    }
                }

                @Override
                public List<PageMatchBounds> searchText(String query) {
                    return Build.VERSION.SDK_INT >= 35 ? page.searchText(query) : Collections.emptyList();
                }

                @Override
                public List<PdfPageLinkContent> getLinkContents() {
                    return Build.VERSION.SDK_INT >= 35 ? page.getLinkContents() : Collections.emptyList();
                }

                @Override
                public List<PdfPageGotoLinkContent> getGotoLinks() {
                    return Build.VERSION.SDK_INT >= 35 ? page.getGotoLinks() : Collections.emptyList();
                }

                @Override
                public PageSelection selectContent(SelectionBoundary start, SelectionBoundary stop) {
                    return Build.VERSION.SDK_INT >= 35 ? page.selectContent(start, stop) : null;
                }

                @Override
                public void close() {
                    page.close();
                }
            };
        }
    }

    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 13)
    private static final class PreVDocumentSource implements DocumentSource {

        private final PdfRendererPreV renderer;
        private final RenderParams renderParams = new RenderParams.Builder(RenderParams.RENDER_MODE_FOR_DISPLAY)
                .setRenderFlags(RenderParams.FLAG_RENDER_HIGHLIGHT_ANNOTATIONS | RenderParams.FLAG_RENDER_TEXT_ANNOTATIONS)
                .build();

        PreVDocumentSource(ParcelFileDescriptor descriptor) throws IOException {
            renderer = new PdfRendererPreV(descriptor);
        }

        @Override
        public int getPageCount() {
            return renderer.getPageCount();
        }

        @Override
        public boolean supportsText() {
            return true;
        }

        @Override
        public void close() {
            renderer.close();
        }

        @Override
        public PageSource openPage(int index) {
            final PdfRendererPreV.Page page = renderer.openPage(index);
            return new PageSource() {
                @Override
                public int getWidth() {
                    return page.getWidth();
                }

                @Override
                public int getHeight() {
                    return page.getHeight();
                }

                @Override
                public void render(Bitmap bitmap, Matrix matrix) {
                    page.render(bitmap, null, matrix, renderParams);
                }

                @Override
                public List<PageMatchBounds> searchText(String query) {
                    return page.searchText(query);
                }

                @Override
                public List<PdfPageLinkContent> getLinkContents() {
                    return page.getLinkContents();
                }

                @Override
                public List<PdfPageGotoLinkContent> getGotoLinks() {
                    return page.getGotoLinks();
                }

                @Override
                public PageSelection selectContent(SelectionBoundary start, SelectionBoundary stop) {
                    return page.selectContent(start, stop);
                }

                @Override
                public void close() {
                    page.close();
                }
            };
        }
    }
}
