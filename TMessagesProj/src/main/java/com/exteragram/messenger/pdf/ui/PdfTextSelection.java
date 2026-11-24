/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.pdf.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.ActionMode;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Magnifier;

import androidx.annotation.RequiresApi;
import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.pdf.PdfDocumentRenderer;
import com.exteragram.messenger.translator.TranslatorUtils;
import com.exteragram.messenger.utils.system.VibratorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LanguageDetector;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.FloatingActionMode;
import org.telegram.ui.ActionBar.FloatingToolbar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;

final class PdfTextSelection {

    private static final int HANDLE_NONE = 0;
    private static final int HANDLE_START = 1;
    private static final int HANDLE_STOP = 2;

    private static final int MENU_SHARE = 1;
    private static final int MENU_TRANSLATE = 2;

    private static final int PAGE_END = Integer.MAX_VALUE;

    private final PdfPagesView view;
    private final PdfPagesView.Delegate delegate;
    private final Theme.ResourcesProvider resourcesProvider;

    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path handlePath = new Path();
    private final RectF pageRect = new RectF();
    private final RectF startArea = new RectF();
    private final RectF stopArea = new RectF();
    private final AnimatedFloat handlesScale;

    private boolean started;
    private SparseArray<PdfDocumentRenderer.SelectedPage> pages = new SparseArray<>();
    private final SparseArray<PdfDocumentRenderer.SelectedPage> fullPages = new SparseArray<>();
    private String text;
    private int generation;
    private int version;
    private PdfDocumentRenderer.Job pendingJob;

    private int draggingHandle = HANDLE_NONE;
    private boolean longPressDrag;
    private boolean dragPending;
    private float dragX, dragY;
    private float dragOffsetX, dragOffsetY;
    private int movingPage = -1;
    private int fixedPage = -1;
    private int fixedIndex = -1;
    private float fixedX, fixedY;
    private float longPressX, longPressY;
    private final int touchSlop;

    private boolean autoScrolling;
    private long autoScrollTime;
    private final Runnable autoScrollRunnable = this::autoScroll;

    private Magnifier magnifier;
    private FloatingActionMode actionMode;
    private boolean actionsShown;
    private boolean actionsHiddenByViewport;
    private String translateFromLanguage;

    private final Runnable hideActionsRunnable = new Runnable() {
        @Override
        public void run() {
            if (actionMode != null && !actionsShown) {
                actionMode.hide(Long.MAX_VALUE);
                AndroidUtilities.runOnUIThread(this, 1000);
            }
        }
    };

    private final ActionMode.Callback2 actionCallback = new ActionMode.Callback2() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            menu.add(Menu.NONE, android.R.id.copy, 0, android.R.string.copy);
            menu.add(Menu.NONE, MENU_SHARE, 1, LocaleController.getString(R.string.ShareFile));
            menu.add(Menu.NONE, MENU_TRANSLATE, 2, LocaleController.getString(R.string.TranslateMessage));
            menu.add(Menu.NONE, android.R.id.selectAll, 3, android.R.string.selectAll);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            translateFromLanguage = null;
            updateTranslateItem(menu);
            if (text != null && LanguageDetector.hasSupport()) {
                LanguageDetector.detectLanguage(text, language -> {
                    translateFromLanguage = language;
                    updateTranslateItem(menu);
                }, e -> {
                    FileLog.e(e);
                    translateFromLanguage = null;
                    updateTranslateItem(menu);
                });
            }
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (!isActive()) {
                return true;
            }
            final int id = item.getItemId();
            if (id == android.R.id.copy) {
                AndroidUtilities.addToClipboard(text);
                delegate.onTextCopied();
                clear();
            } else if (id == MENU_SHARE) {
                delegate.onShareText(text);
                clear();
            } else if (id == MENU_TRANSLATE) {
                delegate.onTranslateText(text, translateFromLanguage, PdfTextSelection.this::showActions);
                hideActions();
            } else if (id == android.R.id.selectAll) {
                final int firstPage = pages.keyAt(0);
                final int lastPage = pages.keyAt(pages.size() - 1);
                pendingJob = PdfDocumentRenderer.Job.select(generation, firstPage, 0, 0, 0, lastPage, PAGE_END, 0, 0, getMissingFullPages(firstPage, lastPage));
                hideActions();
                view.scheduleNext();
            }
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {

        }

        @Override
        public void onGetContentRect(ActionMode mode, View originatingView, Rect outRect) {
            if (!isActive()) {
                return;
            }
            final float visibleTop = view.getTopInset();
            final float visibleBottom = view.getHeight() - view.getBottomInset();
            float left = Float.MAX_VALUE, top = Float.MAX_VALUE, right = -Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
            for (int pass = 0; pass < 2 && left > right; pass++) {
                for (int i = 0; i < pages.size(); i++) {
                    final PdfDocumentRenderer.SelectedPage selection = pages.valueAt(i);
                    view.getPageRect(selection.page, pageRect);
                    for (RectF bounds : selection.rects) {
                        final float rectTop = pageRect.top + bounds.top * pageRect.height();
                        final float rectBottom = pageRect.top + bounds.bottom * pageRect.height();
                        if (pass == 0 && (rectBottom < visibleTop || rectTop > visibleBottom)) {
                            continue;
                        }
                        left = Math.min(left, pageRect.left + bounds.left * pageRect.width());
                        top = Math.min(top, rectTop);
                        right = Math.max(right, pageRect.left + bounds.right * pageRect.width());
                        bottom = Math.max(bottom, rectBottom);
                    }
                }
            }
            top = Utilities.clamp(top, visibleBottom, visibleTop);
            bottom = Utilities.clamp(bottom, visibleBottom, top);
            outRect.set(
                    Math.round(Utilities.clamp(left, view.getWidth(), 0)),
                    Math.round(top),
                    Math.round(Utilities.clamp(right, view.getWidth(), 0)),
                    Math.round(bottom) + 1
            );
        }
    };

    PdfTextSelection(PdfPagesView view, PdfPagesView.Delegate delegate, Theme.ResourcesProvider resourcesProvider) {
        this.view = view;
        this.delegate = delegate;
        this.resourcesProvider = resourcesProvider;
        touchSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
        handlesScale = new AnimatedFloat(view::invalidateOverlay, 0, 250, CubicBezierInterpolator.EASE_OUT_QUINT);
        selectionPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
        updateColors();
    }

    void updateColors() {
        final int accent = Theme.getColor(Theme.key_chat_TextSelectionCursor, resourcesProvider);
        selectionPaint.setColor(ColorUtils.blendARGB(0xFFFFFFFF, accent | 0xFF000000, .35f));
        handlePaint.setColor(accent);
    }

    boolean isActive() {
        return pages.size() > 0;
    }

    boolean isDragging() {
        return draggingHandle != HANDLE_NONE;
    }

    boolean isLongPressDrag() {
        return longPressDrag;
    }

    int getVersion() {
        return version;
    }

    PdfDocumentRenderer.Job takePendingJob() {
        final PdfDocumentRenderer.Job job = pendingJob;
        pendingJob = null;
        return job;
    }

    void startAt(int page, float x, float y) {
        clear();
        view.getPageRect(page, pageRect);
        final float pageX = Utilities.clamp((x - pageRect.left) / pageRect.width(), 1f, 0f);
        final float pageY = Utilities.clamp((y - pageRect.top) / pageRect.height(), 1f, 0f);
        started = true;
        movingPage = page;
        pendingJob = PdfDocumentRenderer.Job.select(generation, page, -1, pageX, pageY, page, -1, pageX, pageY, null);
        draggingHandle = HANDLE_STOP;
        longPressDrag = true;
        longPressX = x;
        longPressY = y;
        dragX = x;
        dragY = y;
        dragOffsetX = 0;
        dragOffsetY = 0;
        view.performHapticFeedback(VibratorUtils.getType(HapticFeedbackConstants.LONG_PRESS), HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
        view.scheduleNext();
    }

    boolean onDown(float x, float y) {
        if (!isActive()) {
            return false;
        }
        if (startArea.contains(x, y)) {
            draggingHandle = HANDLE_START;
        } else if (stopArea.contains(x, y)) {
            draggingHandle = HANDLE_STOP;
        } else {
            return false;
        }
        final PdfDocumentRenderer.SelectedPage selection = draggingHandle == HANDLE_START ? pages.valueAt(0) : pages.valueAt(pages.size() - 1);
        view.getPageRect(selection.page, pageRect);
        final RectF bounds = draggingHandle == HANDLE_START ? selection.rects[0] : selection.rects[selection.rects.length - 1];
        final float anchorX = pageRect.left + (draggingHandle == HANDLE_START ? bounds.left : bounds.right) * pageRect.width();
        final float anchorY = pageRect.top + bounds.centerY() * pageRect.height();
        dragOffsetX = anchorX - x;
        dragOffsetY = anchorY - y;
        dragX = x;
        dragY = y;
        movingPage = selection.page;
        longPressDrag = false;
        captureFixedBoundary(draggingHandle == HANDLE_START);
        hideActions();
        return true;
    }

    void onMove(float x, float y) {
        if (longPressDrag && Math.hypot(x - longPressX, y - longPressY) < touchSlop) {
            return;
        }
        longPressX = Float.NaN;
        dragX = x;
        dragY = y;
        startAutoScroll();
        if (!isActive()) {
            dragPending = true;
            return;
        }
        requestDragSelection();
        showMagnifier();
    }

    void onUp() {
        draggingHandle = HANDLE_NONE;
        longPressDrag = false;
        dragPending = false;
        stopAutoScroll();
        hideMagnifier();
        showActions();
        view.invalidateOverlay();
    }

    boolean clear() {
        final boolean hadSelection = started;
        generation++;
        version++;
        started = false;
        pages = new SparseArray<>();
        fullPages.clear();
        text = null;
        movingPage = -1;
        fixedPage = -1;
        fixedIndex = -1;
        pendingJob = null;
        draggingHandle = HANDLE_NONE;
        longPressDrag = false;
        dragPending = false;
        stopAutoScroll();
        startArea.setEmpty();
        stopArea.setEmpty();
        hideMagnifier();
        finishActions();
        view.invalidate();
        view.invalidateOverlay();
        return hadSelection;
    }

    void onViewportChanged() {
        if (isActive() && draggingHandle == HANDLE_NONE && actionsShown) {
            hideActions();
            actionsHiddenByViewport = true;
        }
    }

    void onSettled() {
        if (actionsHiddenByViewport) {
            showActions();
        }
    }

    void onJobDone(PdfDocumentRenderer.Job job) {
        if (job.generation != generation || !started) {
            return;
        }
        final SparseArray<PdfDocumentRenderer.SelectedPage> selected = new SparseArray<>();
        if (job.selectedPages != null) {
            for (PdfDocumentRenderer.SelectedPage selection : job.selectedPages) {
                if (selection.page > job.page && selection.page < job.stopPage) {
                    fullPages.put(selection.page, selection);
                } else if (selection.rects.length > 0) {
                    selected.put(selection.page, selection);
                }
            }
            for (int i = job.page + 1; i < job.stopPage; i++) {
                final PdfDocumentRenderer.SelectedPage selection = fullPages.get(i);
                if (selection != null && selection.rects.length > 0) {
                    selected.put(i, selection);
                }
            }
        }
        if (selected.size() == 0) {
            if (!isActive()) {
                clear();
            }
            return;
        }
        final String selectedText = joinText(selected);
        final boolean first = !isActive();
        if (!first && draggingHandle != HANDLE_NONE && !autoScrolling && !selectedText.equals(text)) {
            AndroidUtilities.vibrateCursor(view);
        }
        text = selectedText;
        pages = selected;
        version++;
        if (first) {
            handlesScale.set(0, true);
            captureFixedBoundary(false);
            if (dragPending) {
                dragPending = false;
                requestDragSelection();
            }
        }
        if (draggingHandle == HANDLE_NONE) {
            showActions();
        } else {
            showMagnifier();
        }
        view.invalidate();
        view.invalidateOverlay();
    }

    void drawHighlight(Canvas canvas, int page, RectF pageRect) {
        final PdfDocumentRenderer.SelectedPage selection = pages.get(page);
        if (selection == null) {
            return;
        }
        for (RectF bounds : selection.rects) {
            canvas.drawRect(
                    pageRect.left + bounds.left * pageRect.width(),
                    pageRect.top + bounds.top * pageRect.height(),
                    pageRect.left + bounds.right * pageRect.width(),
                    pageRect.top + bounds.bottom * pageRect.height(),
                    selectionPaint
            );
        }
    }

    void drawHandles(Canvas canvas) {
        startArea.setEmpty();
        stopArea.setEmpty();
        if (!isActive()) {
            return;
        }
        final float scale = handlesScale.set(1f);
        final PdfDocumentRenderer.SelectedPage firstPage = pages.valueAt(0);
        final RectF first = firstPage.rects[0];
        view.getPageRect(firstPage.page, pageRect);
        drawHandle(canvas, pageRect.left + first.left * pageRect.width(), pageRect.top + first.bottom * pageRect.height(), true, scale, startArea);
        final PdfDocumentRenderer.SelectedPage lastPage = pages.valueAt(pages.size() - 1);
        final RectF last = lastPage.rects[lastPage.rects.length - 1];
        view.getPageRect(lastPage.page, pageRect);
        drawHandle(canvas, pageRect.left + last.right * pageRect.width(), pageRect.top + last.bottom * pageRect.height(), false, scale, stopArea);
    }

    private void drawHandle(Canvas canvas, float x, float y, boolean start, float scale, RectF area) {
        if (y < view.getTopInset() || y > view.getHeight() - view.getBottomInset()) {
            return;
        }
        final float size = dp(22);
        final float left = start ? x - size : x;
        canvas.save();
        canvas.translate(left, y);
        canvas.scale(scale, scale, start ? size : 0, 0);
        handlePath.rewind();
        handlePath.addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CCW);
        handlePath.addRect(start ? size / 2f : 0, 0, start ? size : size / 2f, size / 2f, Path.Direction.CCW);
        canvas.drawPath(handlePath, handlePaint);
        canvas.restore();
        area.set(left, y - size, left + size, y + size);
        area.inset(-dp(8), -dp(8));
    }

    private static String joinText(SparseArray<PdfDocumentRenderer.SelectedPage> selected) {
        if (selected.size() == 1) {
            return selected.valueAt(0).text;
        }
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < selected.size(); i++) {
            if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
                builder.append('\n');
            }
            builder.append(selected.valueAt(i).text);
        }
        return builder.toString();
    }

    private int[] getMissingFullPages(int fromPage, int toPage) {
        int count = 0;
        for (int i = fromPage + 1; i < toPage; i++) {
            if (fullPages.get(i) == null) {
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        final int[] missing = new int[count];
        for (int i = fromPage + 1, a = 0; i < toPage; i++) {
            if (fullPages.get(i) == null) {
                missing[a++] = i;
            }
        }
        return missing;
    }

    private void captureFixedBoundary(boolean stopSide) {
        final PdfDocumentRenderer.SelectedPage selection = stopSide ? pages.valueAt(pages.size() - 1) : pages.valueAt(0);
        final RectF bounds = stopSide ? selection.rects[selection.rects.length - 1] : selection.rects[0];
        final float edgeX = stopSide ? bounds.right : bounds.left;
        int closest = -1;
        float closestDistance = Float.MAX_VALUE;
        for (int i = 0; i < 2; i++) {
            if (Float.isNaN(selection.boundaryX[i])) {
                continue;
            }
            final float distance = Math.abs(selection.boundaryX[i] - edgeX) + Math.abs(selection.boundaryY[i] - bounds.centerY());
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = i;
            }
        }
        fixedPage = selection.page;
        fixedIndex = -1;
        if (closest >= 0) {
            fixedX = selection.boundaryX[closest];
            fixedY = selection.boundaryY[closest];
        } else if (selection.boundaryIndex[0] >= 0 && selection.boundaryIndex[1] >= 0) {
            fixedIndex = stopSide ? Math.max(selection.boundaryIndex[0], selection.boundaryIndex[1]) : Math.min(selection.boundaryIndex[0], selection.boundaryIndex[1]);
        } else {
            view.getPageRect(fixedPage, pageRect);
            final float inset = Math.min(bounds.width() / 2f, dp(2) / pageRect.width());
            fixedX = stopSide ? edgeX - inset : edgeX + inset;
            fixedY = bounds.centerY();
        }
    }

    private void requestDragSelection() {
        final float x = dragX + dragOffsetX;
        final float y = dragY + dragOffsetY;
        movingPage = view.getPageAtY(y);
        view.getPageRect(movingPage, pageRect);
        final float pageX = Utilities.clamp((x - pageRect.left) / pageRect.width(), 1f, 0f);
        final float pageY = Utilities.clamp((y - pageRect.top) / pageRect.height(), 1f, 0f);
        if (movingPage > fixedPage) {
            pendingJob = PdfDocumentRenderer.Job.select(generation, fixedPage, fixedIndex, fixedX, fixedY, movingPage, -1, pageX, pageY, getMissingFullPages(fixedPage, movingPage));
        } else if (movingPage < fixedPage || draggingHandle == HANDLE_START) {
            pendingJob = PdfDocumentRenderer.Job.select(generation, movingPage, -1, pageX, pageY, fixedPage, fixedIndex, fixedX, fixedY, getMissingFullPages(movingPage, fixedPage));
        } else {
            pendingJob = PdfDocumentRenderer.Job.select(generation, fixedPage, fixedIndex, fixedX, fixedY, movingPage, -1, pageX, pageY, null);
        }
        view.scheduleNext();
    }

    private void startAutoScroll() {
        if (!autoScrolling && getAutoScrollSpeed() != 0) {
            autoScrolling = true;
            autoScrollTime = SystemClock.uptimeMillis();
            view.postOnAnimation(autoScrollRunnable);
        }
    }

    private void stopAutoScroll() {
        autoScrolling = false;
        view.removeCallbacks(autoScrollRunnable);
    }

    private float getAutoScrollSpeed() {
        final float zone = dp(64);
        final float top = view.getTopInset() + zone;
        final float bottom = view.getHeight() - view.getBottomInset() - zone;
        final float progress;
        if (dragY < top) {
            progress = -Math.min(1f, (top - dragY) / zone);
        } else if (dragY > bottom) {
            progress = Math.min(1f, (dragY - bottom) / zone);
        } else {
            return 0;
        }
        return Math.signum(progress) * AndroidUtilities.lerp(dp(120), dp(1600), Math.abs(progress));
    }

    private void autoScroll() {
        final float speed = draggingHandle == HANDLE_NONE ? 0 : getAutoScrollSpeed();
        final long now = SystemClock.uptimeMillis();
        final float delta = speed * Math.min(32, now - autoScrollTime) / 1000f;
        autoScrollTime = now;
        if (speed == 0 || !view.scrollForSelection(delta)) {
            autoScrolling = false;
            return;
        }
        if (isActive()) {
            requestDragSelection();
            showMagnifier();
        }
        view.postOnAnimation(autoScrollRunnable);
    }

    private void showMagnifier() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !isActive() || draggingHandle == HANDLE_NONE) {
            return;
        }
        final float anchorY = dragY + dragOffsetY;
        float y = anchorY;
        final PdfDocumentRenderer.SelectedPage selection = pages.get(movingPage);
        if (selection != null) {
            view.getPageRect(selection.page, pageRect);
            final float firstY = pageRect.top + selection.rects[0].centerY() * pageRect.height();
            final float lastY = pageRect.top + selection.rects[selection.rects.length - 1].centerY() * pageRect.height();
            y = Math.abs(anchorY - firstY) < Math.abs(anchorY - lastY) ? firstY : lastY;
        }
        final float x = Utilities.clamp(dragX + dragOffsetX, view.getWidth(), 0);
        if (magnifier == null) {
            magnifier = createMagnifier();
        }
        magnifier.show(x, y);
    }

    @SuppressWarnings("deprecation")
    @RequiresApi(Build.VERSION_CODES.P)
    private Magnifier createMagnifier() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? new Magnifier.Builder(view).build() : new Magnifier(view);
    }

    private void hideMagnifier() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && magnifier != null) {
            magnifier.dismiss();
            magnifier = null;
        }
    }

    private void showActions() {
        if (!isActive() || draggingHandle != HANDLE_NONE) {
            return;
        }
        if (actionMode == null) {
            final FloatingToolbar toolbar = new FloatingToolbar(view.getContext(), view, FloatingToolbar.STYLE_THEME, resourcesProvider);
            actionMode = new FloatingActionMode(view.getContext(), actionCallback, view, toolbar);
            actionCallback.onCreateActionMode(actionMode, actionMode.getMenu());
        }
        actionsHiddenByViewport = false;
        if (!actionsShown) {
            actionCallback.onPrepareActionMode(actionMode, actionMode.getMenu());
        }
        actionMode.updateViewLocationInWindow();
        actionMode.invalidateContentRect();
        if (!actionsShown) {
            AndroidUtilities.cancelRunOnUIThread(hideActionsRunnable);
            actionsShown = true;
            actionMode.hide(1);
        }
    }

    private void hideActions() {
        if (actionMode != null && actionsShown) {
            actionsShown = false;
            hideActionsRunnable.run();
        }
        actionsShown = false;
    }

    private void finishActions() {
        AndroidUtilities.cancelRunOnUIThread(hideActionsRunnable);
        actionsShown = false;
        actionsHiddenByViewport = false;
        if (actionMode != null) {
            actionMode.finish();
            actionMode = null;
        }
    }

    private void updateTranslateItem(Menu menu) {
        final MenuItem item = menu.findItem(MENU_TRANSLATE);
        if (item != null) {
            item.setVisible(translateFromLanguage != null && !TranslatorUtils.isRestrictedLanguage(translateFromLanguage) || !LanguageDetector.hasSupport());
        }
    }
}
