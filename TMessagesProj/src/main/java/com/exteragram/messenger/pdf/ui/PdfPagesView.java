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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseLongArray;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.exteragram.messenger.pdf.PdfDocumentRenderer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LoadingDrawable;
import org.telegram.ui.Components.blur3.capture.IBlur3Capture;
import org.telegram.ui.Components.blur3.capture.IBlur3Hash;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@SuppressLint("ViewConstructor")
public class PdfPagesView extends View implements IBlur3Capture {

    public interface Delegate {
        void onLinkClick(String url);

        void onViewportChanged(float dx, float dy);

        void onSearchUpdated(int current, int total, boolean finished);

        void onTextCopied();

        void onTranslateText(CharSequence text, String fromLanguage, Runnable onDismiss);

        void onShareText(String text);
    }

    private static final float MAX_ZOOM = 5f;
    private static final float DOUBLE_TAP_ZOOM = 2.5f;
    private static final int MEASURE_BATCH = 8;
    private static final int SEARCH_BATCH = 16;
    private static final int MAX_BASE_PIXELS = 2560 * 1440;
    private static final int MAX_DETAIL_PIXELS = 3840 * 2160;
    private static final int MAX_BITMAP_SIDE = 4096;
    private static final long INDICATOR_DURATION = 1200;
    private static final long PAGE_APPEAR_DURATION = 200;

    private record Detail(Bitmap bitmap, RectF region, float displayWidth) {
    }

    private final PdfDocumentRenderer renderer;
    private final Delegate delegate;
    private final Theme.ResourcesProvider resourcesProvider;
    private final int pageCount;

    private final float[] pageAspect;
    private final float[] failedDetailWidth;
    private final boolean[] measured;
    private final float[] pageTop;
    private float contentHeight;
    private int pageWidth;
    private final int pagePadding = dp(8);
    private final int pageGap = dp(8);
    private int topInset;
    private int bottomInset;

    private float zoom = 1f;
    private float scrollX, scrollY;
    private float flingOffsetY;

    private final OverScroller scroller;
    private final GestureDetector gestureDetector;
    private final ScaleGestureDetector scaleDetector;
    private ValueAnimator zoomAnimator;
    private boolean scaling;
    private boolean scaledInGesture;
    private boolean draggingThumb;
    private float thumbGrabOffset;

    private final LinkedHashMap<Integer, Bitmap> bases = new LinkedHashMap<>(16, .75f, true);
    private long basesBytes;
    private final long basesBudget;
    private final SparseArray<Detail> details = new SparseArray<>();
    private final SparseArray<ArrayList<PdfDocumentRenderer.Link>> links = new SparseArray<>();
    private final SparseBooleanArray failedPages = new SparseBooleanArray();
    private PdfDocumentRenderer.Job inFlight;
    private int lastJobType = -1;
    private int nextMeasurePage = 1;
    private boolean destroyed;

    private String searchQuery;
    private int searchGeneration;
    private int searchVersion;
    private final SparseArray<ArrayList<RectF[]>> searchMatches = new SparseArray<>();
    private int searchStartPage;
    private int searchCrawled;
    private int searchTotal;
    private int currentMatchPage = -1;
    private int currentMatchIndex;

    private int firstVisible, lastVisible = -1;
    private int currentPage = -1;
    private long lastActivityTime;
    private float overlayVisibility;
    private final SparseLongArray baseAppearTime = new SparseLongArray();

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint placeholderPaint = new Paint();
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final AnimatedTextView.AnimatedTextDrawable indicatorText = new AnimatedTextView.AnimatedTextDrawable(true, true, true);
    private final LoadingDrawable skeletonDrawable = new LoadingDrawable();
    private final Path skeletonPath = new Path();
    private final RectF skeletonRect = new RectF();
    private final AnimatedFloat overlayAlpha = new AnimatedFloat(this::invalidateOverlay, 0, 220, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat thumbOnLightBackground = new AnimatedFloat(this::invalidateOverlay, 0, 220, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final RectF rect = new RectF();
    private final RectF rect2 = new RectF();
    private final RectF sampleRect = new RectF();
    private final Runnable invalidateRunnable = this::invalidateOverlay;
    private View overlayView;
    private final PdfTextSelection textSelection;
    private boolean textSelectionEnabled;

    public PdfPagesView(Context context, PdfDocumentRenderer renderer, Delegate delegate, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.renderer = renderer;
        this.delegate = delegate;
        this.resourcesProvider = resourcesProvider;

        pageCount = renderer.pageCount;
        pageAspect = new float[pageCount];
        failedDetailWidth = new float[pageCount];
        measured = new boolean[pageCount];
        pageTop = new float[pageCount];
        Arrays.fill(pageAspect, renderer.firstPageHeight / renderer.firstPageWidth);
        measured[0] = true;
        basesBudget = Math.min(64L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 4);

        highlightPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
        indicatorText.setTextSize(dp(13));
        indicatorText.setTypeface(AndroidUtilities.bold());
        indicatorText.setGravity(Gravity.CENTER);
        indicatorText.setAnimationProperties(.25f, 0, 280, CubicBezierInterpolator.EASE_OUT_QUINT);
        indicatorText.setCallback(this);
        skeletonDrawable.setCallback(this);
        textSelection = new PdfTextSelection(this, delegate, resourcesProvider);
        updateColors();

        scroller = new OverScroller(context);
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
                setScrollInternal(scrollX + distanceX, scrollY + distanceY);
                noteActivity();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                if (scaledInGesture) {
                    return false;
                }
                fling(velocityX, velocityY);
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                if (textSelection.isActive()) {
                    textSelection.clear();
                    return true;
                }
                return handleTap(e.getX(), e.getY());
            }

            @Override
            public void onLongPress(@NonNull MotionEvent e) {
                if (!textSelectionEnabled || scaledInGesture || draggingThumb) {
                    return;
                }
                for (int i = firstVisible; i <= lastVisible; i++) {
                    getPageRect(i, rect);
                    if (rect.contains(e.getX(), e.getY())) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                        scroller.forceFinished(true);
                        textSelection.startAt(i, e.getX(), e.getY());
                        return;
                    }
                }
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDoubleTapEvent(@NonNull MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_UP && !scaledInGesture) {
                    animateZoom(zoom > 1.05f ? 1f : DOUBLE_TAP_ZOOM, e.getX(), e.getY());
                }
                return true;
            }
        });
        gestureDetector.setIsLongpressEnabled(false);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
                scaling = true;
                scaledInGesture = true;
                scroller.forceFinished(true);
                return true;
            }

            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                setZoomInternal(Utilities.clamp(zoom * detector.getScaleFactor(), MAX_ZOOM, 1f), detector.getFocusX(), detector.getFocusY());
                noteActivity();
                return true;
            }

            @Override
            public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
                scaling = false;
            }
        });
    }

    public void setInsets(int top, int bottom) {
        if (topInset != top || bottomInset != bottom) {
            topInset = top;
            bottomInset = bottom;
            setScrollInternal(scrollX, scrollY);
            invalidate();
            invalidateOverlay();
        }
    }

    public View createOverlayView(Context context) {
        overlayView = new View(context) {
            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                drawOverlay(canvas);
            }

            @Override
            protected boolean verifyDrawable(@NonNull Drawable who) {
                return who == indicatorText || super.verifyDrawable(who);
            }
        };
        indicatorText.setCallback(overlayView);
        return overlayView;
    }

    public void setTextSelectionEnabled(boolean enabled) {
        textSelectionEnabled = enabled && renderer.supportsSearch;
        gestureDetector.setIsLongpressEnabled(textSelectionEnabled);
        if (!textSelectionEnabled) {
            textSelection.clear();
        }
    }

    public boolean hasTextSelection() {
        return textSelection.isActive();
    }

    public void clearTextSelection() {
        textSelection.clear();
    }

    public void updateColors() {
        textSelection.updateColors();
        final int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        placeholderPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        skeletonDrawable.setColors(Theme.multAlpha(textColor, .06f), Theme.multAlpha(textColor, .13f));
        invalidate();
        invalidateOverlay();
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == skeletonDrawable || who == indicatorText || super.verifyDrawable(who);
    }

    public boolean isZoomed() {
        return zoom > 1.01f;
    }

    public void search(String query) {
        final String normalized = query == null ? null : query.trim();
        if (TextUtils.isEmpty(normalized)) {
            if (searchQuery == null) {
                return;
            }
            searchQuery = null;
        } else if (normalized.equals(searchQuery)) {
            return;
        } else {
            searchQuery = normalized;
        }
        searchGeneration++;
        searchVersion++;
        searchMatches.clear();
        searchStartPage = Math.max(0, currentPage);
        searchCrawled = 0;
        searchTotal = 0;
        currentMatchPage = -1;
        currentMatchIndex = 0;
        invalidate();
        notifySearchUpdated();
        scheduleNext();
    }

    public void searchNavigate(boolean forward) {
        if (currentMatchPage < 0) {
            return;
        }
        final ArrayList<RectF[]> currentList = searchMatches.get(currentMatchPage);
        if (forward && currentList != null && currentMatchIndex + 1 < currentList.size()) {
            currentMatchIndex++;
        } else if (!forward && currentMatchIndex > 0) {
            currentMatchIndex--;
        } else {
            for (int i = 1; i <= pageCount; i++) {
                final int page = forward ? (currentMatchPage + i) % pageCount : (currentMatchPage - i + pageCount) % pageCount;
                final ArrayList<RectF[]> list = searchMatches.get(page);
                if (list != null && !list.isEmpty()) {
                    currentMatchPage = page;
                    currentMatchIndex = forward ? 0 : list.size() - 1;
                    break;
                }
            }
        }
        searchVersion++;
        scrollToCurrentMatch();
        notifySearchUpdated();
        invalidate();
    }

    public void destroy() {
        destroyed = true;
        scroller.forceFinished(true);
        if (zoomAnimator != null) {
            zoomAnimator.cancel();
        }
        removeCallbacks(invalidateRunnable);
        textSelection.clear();
        clearBitmaps();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        final int newPageWidth = Math.max(1, width - pagePadding * 2);
        if (newPageWidth != pageWidth) {
            final boolean hadLayout = pageWidth > 0;
            final float maxX = getMaxScrollX();
            final float fractionX = maxX > 0 ? scrollX / maxX : 0;
            final int anchorPage = hadLayout ? getPageAt(getAnchorContentY()) : 0;
            final float anchorOffset = hadLayout ? (getAnchorContentY() - pageTop[anchorPage]) / getPageHeight(anchorPage) : 0;
            pageWidth = newPageWidth;
            clearBitmaps();
            updateLayout();
            if (hadLayout) {
                scrollY = (pageTop[anchorPage] + anchorOffset * getPageHeight(anchorPage)) * zoom + getCenterOffset();
                scrollX = fractionX * getMaxScrollX();
            } else {
                noteActivity();
            }
        }
        setScrollInternal(scrollX, scrollY);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (destroyed) {
            return false;
        }
        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            scaledInGesture = false;
            if (zoomAnimator != null) {
                zoomAnimator.cancel();
            }
            scroller.forceFinished(true);
            if (textSelection.onDown(event.getX(), event.getY())) {
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            if (isThumbHit(event.getX(), event.getY())) {
                draggingThumb = true;
                thumbGrabOffset = event.getY() - getThumbTop();
                getParent().requestDisallowInterceptTouchEvent(true);
                invalidateOverlay();
                return true;
            }
        }
        if (textSelection.isDragging()) {
            final boolean longPressDrag = textSelection.isLongPressDrag();
            if (action == MotionEvent.ACTION_MOVE) {
                textSelection.onMove(event.getX(), event.getY());
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                textSelection.onUp();
            }
            if (longPressDrag) {
                gestureDetector.onTouchEvent(event);
            }
            return true;
        }
        if (draggingThumb) {
            if (action == MotionEvent.ACTION_MOVE) {
                final float trackHeight = getTrackHeight() - getThumbHeight();
                if (trackHeight > 0) {
                    final float progress = Utilities.clamp((event.getY() - thumbGrabOffset - getTrackTop()) / trackHeight, 1f, 0f);
                    setScrollInternal(scrollX, progress * getMaxScrollY());
                }
                noteActivity();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                draggingThumb = false;
                noteActivity();
                onInteractionSettled();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            scaling = false;
            onInteractionSettled();
        }
        return true;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (!destroyed && event.isFromSource(InputDevice.SOURCE_CLASS_POINTER) && event.getActionMasked() == MotionEvent.ACTION_SCROLL) {
            final float delta = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if (delta != 0) {
                if ((event.getMetaState() & KeyEvent.META_CTRL_ON) != 0) {
                    setZoomInternal(Utilities.clamp(zoom * (delta > 0 ? 1.1f : 1 / 1.1f), MAX_ZOOM, 1f), event.getX(), event.getY());
                } else {
                    setScrollInternal(scrollX, scrollY - delta * dp(64));
                }
                noteActivity();
                onInteractionSettled();
                return true;
            }
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    public boolean canScrollHorizontally(int direction) {
        return direction < 0 ? scrollX > .5f : scrollX < getMaxScrollX() - .5f;
    }

    @Override
    public boolean canScrollVertically(int direction) {
        return direction < 0 ? scrollY > .5f : scrollY < getMaxScrollY() - .5f;
    }

    @Override
    public void computeScroll() {
        if (!scroller.isFinished() && scroller.computeScrollOffset()) {
            setScrollInternal(scroller.getCurrX(), scroller.getCurrY() + flingOffsetY);
            noteActivity();
            if (scroller.isFinished()) {
                onInteractionSettled();
            } else {
                postInvalidateOnAnimation();
            }
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        drawPages(canvas, 0, getHeight());
        if (overlayView == null) {
            drawOverlay(canvas);
        }
    }

    @Override
    public void capture(Canvas canvas, RectF position) {
        canvas.save();
        canvas.clipRect(position);
        drawPages(canvas, position.top, position.bottom);
        canvas.restore();
    }

    @Override
    public void captureCalculateHash(IBlur3Hash builder, RectF position) {
        builder.addF(scrollX);
        builder.addF(scrollY);
        builder.addF(zoom);
        builder.add(getWidth());
        builder.add(getHeight());
        builder.add(topInset);
        builder.add(searchVersion);
        builder.add(textSelection.getVersion());
        if (pageWidth <= 0) {
            return;
        }
        final int first = getPageAt(getContentY(position.top));
        final int last = getPageAt(getContentY(position.bottom));
        for (int i = first; i <= last; i++) {
            builder.add(System.identityHashCode(bases.get(i)));
            final Detail detail = details.get(i);
            builder.add(detail == null ? 0 : System.identityHashCode(detail.bitmap()));
        }
    }

    private void drawPages(Canvas canvas, float top, float bottom) {
        if (pageWidth <= 0 || bottom <= top) {
            return;
        }
        final int first = getPageAt(getContentY(top));
        final int last = getPageAt(getContentY(bottom));
        final long now = SystemClock.uptimeMillis();
        for (int i = first; i <= last; i++) {
            getPageRect(i, rect);
            if (rect.bottom < top || rect.top > bottom) {
                continue;
            }
            final Bitmap base = bases.get(i);
            float baseAlpha = 1f;
            final long appearTime = base != null ? baseAppearTime.get(i) : 0;
            if (appearTime > 0) {
                baseAlpha = Math.min(1f, (now - appearTime) / (float) PAGE_APPEAR_DURATION);
                if (baseAlpha >= 1f) {
                    baseAppearTime.delete(i);
                } else {
                    invalidate();
                }
            }
            if (base == null || baseAlpha < 1f) {
                canvas.drawRect(rect, placeholderPaint);
                if (!failedPages.get(i)) {
                    drawSkeleton(canvas, rect);
                }
            }
            if (base != null) {
                bitmapPaint.setAlpha(Math.round(255 * CubicBezierInterpolator.EASE_OUT.getInterpolation(baseAlpha)));
                canvas.drawBitmap(base, null, rect, bitmapPaint);
                bitmapPaint.setAlpha(255);
            }
            final Detail detail = details.get(i);
            if (detail != null) {
                rect2.set(
                        rect.left + detail.region().left * rect.width(),
                        rect.top + detail.region().top * rect.height(),
                        rect.left + detail.region().right * rect.width(),
                        rect.top + detail.region().bottom * rect.height()
                );
                canvas.drawBitmap(detail.bitmap(), null, rect2, bitmapPaint);
            }
            final ArrayList<RectF[]> matches = searchMatches.get(i);
            if (matches != null) {
                final float padding = dp(1);
                for (int a = 0; a < matches.size(); a++) {
                    highlightPaint.setColor(i == currentMatchPage && a == currentMatchIndex ? 0xFFFFA733 : 0xFFFFE669);
                    for (RectF bounds : matches.get(a)) {
                        rect2.set(
                                rect.left + bounds.left * rect.width() - padding,
                                rect.top + bounds.top * rect.height() - padding,
                                rect.left + bounds.right * rect.width() + padding,
                                rect.top + bounds.bottom * rect.height() + padding
                        );
                        canvas.drawRoundRect(rect2, padding * 2, padding * 2, highlightPaint);
                    }
                }
            }
            textSelection.drawHighlight(canvas, i, rect);
        }
    }

    private void drawSkeleton(Canvas canvas, RectF page) {
        final float width = page.width();
        final float left = page.left + width * .1f;
        final float lineWidth = width * .8f;
        final float lineHeight = Math.max(dp(3), width * .02f);
        final float step = lineHeight * 2.2f;
        final float bottom = page.bottom - page.height() * .08f;
        float y = page.top + page.height() * .08f;
        skeletonPath.rewind();
        skeletonRect.set(left, y, left + lineWidth * .55f, y + lineHeight * 1.5f);
        skeletonPath.addRoundRect(skeletonRect, lineHeight * .75f, lineHeight * .75f, Path.Direction.CW);
        y += lineHeight * 1.5f + step * 1.5f;
        for (int line = 0; y + lineHeight <= bottom; line++) {
            final boolean paragraphEnd = line % 5 == 4;
            skeletonRect.set(left, y, left + lineWidth * (paragraphEnd ? .6f : 1f), y + lineHeight);
            skeletonPath.addRoundRect(skeletonRect, lineHeight / 2f, lineHeight / 2f, Path.Direction.CW);
            y += paragraphEnd ? step * 2f : step;
        }
        skeletonDrawable.usePath(skeletonPath);
        skeletonDrawable.setBounds(page);
        skeletonDrawable.draw(canvas);
    }

    private void drawOverlay(Canvas canvas) {
        textSelection.drawHandles(canvas);
        final long sinceActivity = SystemClock.uptimeMillis() - lastActivityTime;
        final float alpha = overlayVisibility = overlayAlpha.set(draggingThumb || sinceActivity < INDICATOR_DURATION);
        if (alpha <= 0) {
            return;
        }
        if (pageCount > 1 && currentPage >= 0) {
            final float width = indicatorText.getCurrentWidth() + dp(20);
            final float left = (getWidth() - width) / 2f;
            rect2.set(left, topInset + dp(10), left + width, topInset + dp(10 + 26));
            overlayPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_chat_mediaTimeBackground, resourcesProvider), alpha));
            canvas.drawRoundRect(rect2, dp(13), dp(13), overlayPaint);
            final int textColor = Theme.getColor(Theme.key_chat_mediaTimeText, resourcesProvider);
            indicatorText.setTextColor(textColor);
            indicatorText.setAlpha(Math.round(Color.alpha(textColor) * alpha));
            indicatorText.setBounds(rect2);
            indicatorText.draw(canvas);
        }
        if (hasThumb()) {
            final float top = getThumbTop();
            final float right = getWidth() - dp(3);
            rect2.set(right - dp(draggingThumb ? 6 : 4), top, right, top + getThumbHeight());
            final float onLight = thumbOnLightBackground.set(isLightBehind(rect2.centerX(), rect2.top, rect2.bottom));
            final int thumbColor = draggingThumb ? Theme.getColor(Theme.key_fastScrollActive, resourcesProvider) : ColorUtils.blendARGB(0xB3FFFFFF, 0x8A000000, onLight);
            overlayPaint.setColor(Theme.multAlpha(thumbColor, alpha));
            canvas.drawRoundRect(rect2, dp(3), dp(3), overlayPaint);
        }
    }

    private boolean isLightBehind(float x, float top, float bottom) {
        final int samples = 5;
        float brightness = 0;
        for (int i = 0; i < samples; i++) {
            brightness += getBrightnessAt(x, top + (bottom - top) * (i + .5f) / samples);
        }
        return brightness / samples > .6f;
    }

    private float getBrightnessAt(float x, float y) {
        final int page = getPageAt(getContentY(y));
        getPageRect(page, sampleRect);
        if (!sampleRect.contains(x, y)) {
            return AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        }
        final Bitmap base = bases.get(page);
        if (base == null || base.isRecycled()) {
            return AndroidUtilities.computePerceivedBrightness(placeholderPaint.getColor());
        }
        final int bitmapX = Utilities.clamp((int) ((x - sampleRect.left) / sampleRect.width() * base.getWidth()), base.getWidth() - 1, 0);
        final int bitmapY = Utilities.clamp((int) ((y - sampleRect.top) / sampleRect.height() * base.getHeight()), base.getHeight() - 1, 0);
        return AndroidUtilities.computePerceivedBrightness(base.getPixel(bitmapX, bitmapY));
    }

    private boolean hasThumb() {
        return getMaxScrollY() > getHeight() * .5f;
    }

    private float getTrackTop() {
        return topInset + dp(8);
    }

    private float getTrackHeight() {
        return getHeight() - topInset - bottomInset - dp(16);
    }

    private float getThumbHeight() {
        final float total = contentHeight * zoom + topInset + bottomInset;
        return Math.min(getTrackHeight(), Math.max(dp(36), getTrackHeight() * getHeight() / Math.max(1, total)));
    }

    private float getThumbTop() {
        final float maxY = getMaxScrollY();
        return getTrackTop() + (getTrackHeight() - getThumbHeight()) * (maxY > 0 ? scrollY / maxY : 0);
    }

    private boolean isThumbHit(float x, float y) {
        if (!hasThumb() || overlayVisibility < .5f || x < getWidth() - dp(32)) {
            return false;
        }
        final float top = getThumbTop();
        return y >= top - dp(16) && y <= top + getThumbHeight() + dp(16);
    }

    private void noteActivity() {
        lastActivityTime = SystemClock.uptimeMillis();
        removeCallbacks(invalidateRunnable);
        postDelayed(invalidateRunnable, INDICATOR_DURATION);
        invalidateOverlay();
        textSelection.onViewportChanged();
    }

    private void onInteractionSettled() {
        if (isSettled()) {
            textSelection.onSettled();
        }
        scheduleNext();
    }

    int getTopInset() {
        return topInset;
    }

    int getPageAtY(float y) {
        return getPageAt(getContentY(y));
    }

    boolean scrollForSelection(float dy) {
        final float oldScrollY = scrollY;
        setScrollInternal(scrollX, scrollY + dy);
        if (scrollY == oldScrollY) {
            return false;
        }
        noteActivity();
        return true;
    }

    int getBottomInset() {
        return bottomInset;
    }

    void invalidateOverlay() {
        if (overlayView != null) {
            overlayView.invalidate();
        } else {
            invalidate();
        }
    }

    private boolean handleTap(float x, float y) {
        for (int i = firstVisible; i <= lastVisible; i++) {
            getPageRect(i, rect);
            if (!rect.contains(x, y)) {
                continue;
            }
            final ArrayList<PdfDocumentRenderer.Link> pageLinks = links.get(i);
            if (pageLinks == null) {
                return false;
            }
            final float px = (x - rect.left) / rect.width();
            final float py = (y - rect.top) / rect.height();
            final float slopX = dp(6) / rect.width();
            final float slopY = dp(6) / rect.height();
            for (int a = 0; a < pageLinks.size(); a++) {
                final PdfDocumentRenderer.Link link = pageLinks.get(a);
                if (px < link.bounds.left - slopX || px > link.bounds.right + slopX || py < link.bounds.top - slopY || py > link.bounds.bottom + slopY) {
                    continue;
                }
                if (link.url != null) {
                    delegate.onLinkClick(link.url);
                } else if (link.page >= 0 && link.page < pageCount) {
                    scroller.forceFinished(true);
                    setScrollInternal(scrollX, (pageTop[link.page] + link.y * getPageHeight(link.page)) * zoom + getCenterOffset() - pagePadding);
                    noteActivity();
                    onInteractionSettled();
                }
                return true;
            }
            return false;
        }
        return false;
    }

    private void scrollToCurrentMatch() {
        final ArrayList<RectF[]> list = currentMatchPage < 0 ? null : searchMatches.get(currentMatchPage);
        if (list == null || currentMatchIndex >= list.size()) {
            return;
        }
        float left = 1, top = 1, right = 0, bottom = 0;
        for (RectF bounds : list.get(currentMatchIndex)) {
            left = Math.min(left, bounds.left);
            top = Math.min(top, bounds.top);
            right = Math.max(right, bounds.right);
            bottom = Math.max(bottom, bounds.bottom);
        }
        getPageRect(currentMatchPage, rect);
        final float matchLeft = rect.left + left * rect.width();
        final float matchRight = rect.left + right * rect.width();
        final float matchTop = rect.top + top * rect.height();
        final float matchBottom = rect.top + bottom * rect.height();
        final float visibleTop = topInset + dp(8);
        final float visibleBottom = getHeight() - bottomInset - dp(8);
        final boolean fitsVertically = matchTop >= visibleTop && matchBottom <= visibleBottom;
        final boolean fitsHorizontally = matchLeft >= 0 && matchRight <= getWidth();
        if (fitsVertically && fitsHorizontally) {
            return;
        }
        final float dx = fitsHorizontally ? 0 : (matchLeft + matchRight) / 2f - getWidth() / 2f;
        final float dy = fitsVertically ? 0 : (matchTop + matchBottom) / 2f - (visibleTop + visibleBottom) / 2f;
        final float targetX = Utilities.clamp(scrollX + dx, getMaxScrollX(), 0);
        final float targetY = Utilities.clamp(scrollY + dy, getMaxScrollY(), 0);
        scroller.forceFinished(true);
        flingOffsetY = 0;
        scroller.startScroll(Math.round(scrollX), Math.round(scrollY), Math.round(targetX - scrollX), Math.round(targetY - scrollY), 320);
        postInvalidateOnAnimation();
    }

    private void notifySearchUpdated() {
        int current = -1;
        if (currentMatchPage >= 0) {
            current = currentMatchIndex;
            for (int i = 0; i < searchMatches.size(); i++) {
                if (searchMatches.keyAt(i) < currentMatchPage) {
                    current += searchMatches.valueAt(i).size();
                }
            }
        }
        delegate.onSearchUpdated(current, searchTotal, searchQuery == null || searchCrawled >= pageCount);
    }

    private void fling(float velocityX, float velocityY) {
        scroller.forceFinished(true);
        flingOffsetY = 0;
        scroller.fling(Math.round(scrollX), Math.round(scrollY), Math.round(-velocityX), Math.round(-velocityY), 0, Math.round(getMaxScrollX()), 0, Math.round(getMaxScrollY()));
        postInvalidateOnAnimation();
    }

    private void animateZoom(float target, float focusX, float focusY) {
        if (zoomAnimator != null) {
            zoomAnimator.cancel();
        }
        final float from = zoom;
        final ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.addUpdateListener(a -> {
            setZoomInternal(AndroidUtilities.lerp(from, target, (float) a.getAnimatedValue()), focusX, focusY);
            noteActivity();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (zoomAnimator == animation) {
                    zoomAnimator = null;
                    onInteractionSettled();
                }
            }
        });
        animator.setDuration(280);
        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        zoomAnimator = animator;
        animator.start();
    }

    private void setZoomInternal(float newZoom, float focusX, float focusY) {
        if (pageWidth <= 0) {
            return;
        }
        final float contentX = (scrollX + focusX) / zoom;
        final float contentY = (scrollY + focusY - getContentOffset()) / zoom;
        zoom = newZoom;
        setScrollInternal(contentX * zoom - focusX, contentY * zoom + getContentOffset() - focusY);
    }

    private void setScrollInternal(float x, float y) {
        final float oldX = scrollX, oldY = scrollY;
        scrollX = Utilities.clamp(x, getMaxScrollX(), 0);
        scrollY = Utilities.clamp(y, getMaxScrollY(), 0);
        onViewportChanged();
        delegate.onViewportChanged(scrollX - oldX, scrollY - oldY);
    }

    private float getMaxScrollX() {
        return Math.max(0, getWidth() * zoom - getWidth());
    }

    private float getMaxScrollY() {
        return Math.max(0, contentHeight * zoom + topInset + bottomInset - getHeight());
    }

    private float getCenterOffset() {
        return Math.max(0, (getHeight() - topInset - bottomInset - contentHeight * zoom) / 2f);
    }

    private float getContentOffset() {
        return topInset + getCenterOffset();
    }

    private float getContentY(float screenY) {
        return (screenY + scrollY - getContentOffset()) / zoom;
    }

    private float getAnchorContentY() {
        return getContentY(topInset);
    }

    private float getPageHeight(int page) {
        return pageWidth * pageAspect[page];
    }

    void getPageRect(int page, RectF out) {
        out.left = pagePadding * zoom - scrollX;
        out.top = pageTop[page] * zoom - scrollY + getContentOffset();
        out.right = out.left + pageWidth * zoom;
        out.bottom = out.top + getPageHeight(page) * zoom;
    }

    private int getPageAt(float contentY) {
        int low = 0, high = pageCount - 1;
        while (low < high) {
            final int mid = (low + high + 1) >>> 1;
            if (pageTop[mid] <= contentY) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    private void updateLayout() {
        float y = pagePadding;
        for (int i = 0; i < pageCount; i++) {
            pageTop[i] = y;
            y += getPageHeight(i);
            if (i < pageCount - 1) {
                y += pageGap;
            }
        }
        contentHeight = y + pagePadding;
    }

    private void relayoutKeepingAnchor() {
        final float anchorY = getAnchorContentY();
        final int anchorPage = getPageAt(anchorY);
        final float anchorOffset = (anchorY - pageTop[anchorPage]) / getPageHeight(anchorPage);
        final float oldScrollY = scrollY;
        updateLayout();
        setScrollInternal(scrollX, (pageTop[anchorPage] + anchorOffset * getPageHeight(anchorPage)) * zoom + getCenterOffset());
        flingOffsetY += scrollY - oldScrollY;
        invalidate();
    }

    private void onViewportChanged() {
        if (pageWidth <= 0 || getHeight() <= 0) {
            return;
        }
        firstVisible = getPageAt(getContentY(0));
        lastVisible = getPageAt(getContentY(getHeight()));
        for (int i = details.size() - 1; i >= 0; i--) {
            final int page = details.keyAt(i);
            if (page < firstVisible || page > lastVisible) {
                AndroidUtilities.recycleBitmap(details.valueAt(i).bitmap());
                details.removeAt(i);
            }
        }
        final int page = scrollY >= getMaxScrollY() - 1 && getMaxScrollY() > 0 ? pageCount - 1 : getPageAt(getContentY((topInset + getHeight() - bottomInset) / 2f));
        if (page != currentPage) {
            indicatorText.setText(LocaleController.formatString(R.string.Of, page + 1, pageCount), currentPage >= 0 && overlayVisibility > 0, page < currentPage);
            currentPage = page;
        }
        invalidate();
        invalidateOverlay();
        scheduleNext();
    }

    private boolean isSettled() {
        return !scaling && zoomAnimator == null && scroller.isFinished();
    }

    void scheduleNext() {
        if (destroyed || inFlight != null || pageWidth <= 0 || getHeight() <= 0) {
            return;
        }
        final PdfDocumentRenderer.Job job = pickJob();
        if (job != null) {
            inFlight = job;
            renderer.run(job, this::onJobDone);
        }
    }

    private PdfDocumentRenderer.Job pickJob() {
        if (lastJobType != PdfDocumentRenderer.JOB_SELECT || !needsVisibleBase()) {
            final PdfDocumentRenderer.Job selectJob = textSelection.takePendingJob();
            if (selectJob != null) {
                return selectJob;
            }
        }
        if (needsBase(currentPage)) {
            return createBaseJob(currentPage);
        }
        for (int i = firstVisible; i <= lastVisible; i++) {
            if (needsBase(i)) {
                return createBaseJob(i);
            }
        }
        if (searchQuery != null && searchCrawled < pageCount) {
            final int page = (searchStartPage + searchCrawled) % pageCount;
            final int count = Math.min(SEARCH_BATCH, Math.min(pageCount - page, pageCount - searchCrawled));
            return PdfDocumentRenderer.Job.search(page, count, searchQuery, searchGeneration);
        }
        if (isSettled()) {
            for (int i = firstVisible; i <= lastVisible; i++) {
                final PdfDocumentRenderer.Job job = createDetailJob(i);
                if (job != null) {
                    return job;
                }
            }
        }
        if (lastVisible + 1 < pageCount && needsBase(lastVisible + 1)) {
            return createBaseJob(lastVisible + 1);
        }
        if (firstVisible - 1 >= 0 && needsBase(firstVisible - 1)) {
            return createBaseJob(firstVisible - 1);
        }
        while (nextMeasurePage < pageCount && measured[nextMeasurePage]) {
            nextMeasurePage++;
        }
        if (nextMeasurePage < pageCount) {
            int count = 1;
            while (count < MEASURE_BATCH && nextMeasurePage + count < pageCount && !measured[nextMeasurePage + count]) {
                count++;
            }
            return PdfDocumentRenderer.Job.measure(nextMeasurePage, count);
        }
        return null;
    }

    private boolean needsVisibleBase() {
        if (needsBase(currentPage)) {
            return true;
        }
        for (int i = firstVisible; i <= lastVisible; i++) {
            if (needsBase(i)) {
                return true;
            }
        }
        return false;
    }

    private boolean needsBase(int page) {
        return page >= 0 && page < pageCount && !bases.containsKey(page) && !failedPages.get(page);
    }

    private PdfDocumentRenderer.Job createBaseJob(int page) {
        return PdfDocumentRenderer.Job.base(page, pageWidth, MAX_BASE_PIXELS, renderer.supportsSearch);
    }

    private PdfDocumentRenderer.Job createDetailJob(int page) {
        final Bitmap base = bases.get(page);
        if (base == null) {
            return null;
        }
        getPageRect(page, rect);
        final Detail detail = details.get(page);
        if (rect.width() <= base.getWidth() * 1.1f) {
            if (detail != null) {
                AndroidUtilities.recycleBitmap(detail.bitmap());
                details.remove(page);
            }
            return null;
        }
        final float visibleLeft = (Math.max(rect.left, 0) - rect.left) / rect.width();
        final float visibleRight = (Math.min(rect.right, getWidth()) - rect.left) / rect.width();
        final float visibleTop = (Math.max(rect.top, 0) - rect.top) / rect.height();
        final float visibleBottom = (Math.min(rect.bottom, getHeight()) - rect.top) / rect.height();
        if (visibleRight <= visibleLeft || visibleBottom <= visibleTop || Math.abs(failedDetailWidth[page] - rect.width()) < 1) {
            return null;
        }
        if (detail != null && Math.abs(detail.displayWidth() - rect.width()) < 1 &&
                detail.region().left <= visibleLeft + .0001f && detail.region().right >= visibleRight - .0001f &&
                detail.region().top <= visibleTop + .0001f && detail.region().bottom >= visibleBottom - .0001f) {
            return null;
        }
        final float margin = getHeight() * .25f / rect.height();
        float top = Math.max(0, visibleTop - margin);
        float bottom = Math.min(1, visibleBottom + margin);
        final float width = (visibleRight - visibleLeft) * rect.width();
        float height = (bottom - top) * rect.height();
        if (width * height > MAX_DETAIL_PIXELS || height > MAX_BITMAP_SIDE) {
            top = visibleTop;
            bottom = visibleBottom;
            height = (bottom - top) * rect.height();
        }
        float scale = Math.min(1f, (float) Math.sqrt(MAX_DETAIL_PIXELS / Math.max(1, width * height)));
        scale = Math.min(scale, MAX_BITMAP_SIDE / Math.max(width, height));
        final int bitmapWidth = Math.max(1, Math.round(width * scale));
        final int bitmapHeight = Math.max(1, Math.round(height * scale));
        return PdfDocumentRenderer.Job.detail(page, rect.width() * scale, rect.width(), visibleLeft, top, visibleRight, bottom, bitmapWidth, bitmapHeight);
    }

    private void onJobDone(PdfDocumentRenderer.Job job) {
        if (destroyed) {
            if (job.bitmap != null) {
                job.bitmap.recycle();
            }
            return;
        }
        switch (job.type) {
            case PdfDocumentRenderer.JOB_BASE:
                if (job.bitmap == null) {
                    failedPages.put(job.page, true);
                    break;
                }
                applyPageSize(job.page, job.pageWidth, job.pageHeight, true);
                putBase(job.page, job.bitmap);
                if (job.links != null) {
                    links.put(job.page, job.links);
                }
                break;
            case PdfDocumentRenderer.JOB_DETAIL:
                if (job.bitmap == null) {
                    failedDetailWidth[job.page] = job.displayWidth;
                    break;
                }
                if (job.page < firstVisible || job.page > lastVisible) {
                    AndroidUtilities.recycleBitmap(job.bitmap);
                    break;
                }
                final Detail old = details.get(job.page);
                if (old != null) {
                    AndroidUtilities.recycleBitmap(old.bitmap());
                }
                details.put(job.page, new Detail(job.bitmap, new RectF(job.left, job.top, job.right, job.bottom), job.displayWidth));
                break;
            case PdfDocumentRenderer.JOB_MEASURE:
                if (applyPageSizes(job)) {
                    relayoutKeepingAnchor();
                }
                break;
            case PdfDocumentRenderer.JOB_SEARCH:
                if (job.generation == searchGeneration && searchQuery != null) {
                    onSearchJobDone(job);
                }
                break;
            case PdfDocumentRenderer.JOB_SELECT:
                textSelection.onJobDone(job);
                break;
        }
        if (inFlight == job) {
            inFlight = null;
        }
        lastJobType = job.type;
        invalidate();
        scheduleNext();
    }

    private void onSearchJobDone(PdfDocumentRenderer.Job job) {
        final boolean changed = applyPageSizes(job);
        final int done = Math.max(1, job.measuredCount);
        boolean found = false;
        for (int i = 0; i < done; i++) {
            final ArrayList<RectF[]> list = job.matches != null && i < job.matches.size() ? job.matches.get(i) : null;
            if (list == null || list.isEmpty()) {
                continue;
            }
            searchMatches.put(job.page + i, list);
            searchTotal += list.size();
            if (currentMatchPage < 0) {
                currentMatchPage = job.page + i;
                currentMatchIndex = 0;
                found = true;
            }
        }
        searchCrawled += done;
        searchVersion++;
        if (changed) {
            relayoutKeepingAnchor();
        }
        if (found) {
            scrollToCurrentMatch();
        }
        notifySearchUpdated();
    }

    private boolean applyPageSizes(PdfDocumentRenderer.Job job) {
        boolean changed = false;
        final int done = Math.max(1, job.measuredCount);
        for (int i = 0; i < done; i++) {
            final float width = job.sizes != null ? job.sizes[i * 2] : 0;
            final float height = job.sizes != null ? job.sizes[i * 2 + 1] : 0;
            changed |= applyPageSize(job.page + i, width, height, false);
        }
        return changed;
    }

    private boolean applyPageSize(int page, float width, float height, boolean relayout) {
        measured[page] = true;
        if (width <= 0 || height <= 0) {
            return false;
        }
        final float aspect = height / width;
        if (Math.abs(pageAspect[page] - aspect) < .0005f) {
            return false;
        }
        pageAspect[page] = aspect;
        final Detail detail = details.get(page);
        if (detail != null) {
            AndroidUtilities.recycleBitmap(detail.bitmap());
            details.remove(page);
        }
        if (relayout) {
            relayoutKeepingAnchor();
        }
        return true;
    }

    private void putBase(int page, Bitmap bitmap) {
        if (page >= firstVisible && page <= lastVisible) {
            baseAppearTime.put(page, SystemClock.uptimeMillis());
        } else {
            baseAppearTime.delete(page);
        }
        final Bitmap old = bases.put(page, bitmap);
        if (old != null) {
            basesBytes -= old.getByteCount();
            AndroidUtilities.recycleBitmap(old);
        }
        basesBytes += bitmap.getByteCount();
        final Iterator<Map.Entry<Integer, Bitmap>> iterator = bases.entrySet().iterator();
        while (basesBytes > basesBudget && iterator.hasNext()) {
            final Map.Entry<Integer, Bitmap> entry = iterator.next();
            final int key = entry.getKey();
            if (key >= firstVisible - 1 && key <= lastVisible + 1) {
                continue;
            }
            basesBytes -= entry.getValue().getByteCount();
            AndroidUtilities.recycleBitmap(entry.getValue());
            iterator.remove();
        }
    }

    private void clearBitmaps() {
        final ArrayList<Bitmap> bitmaps = new ArrayList<>(bases.values());
        for (int i = 0; i < details.size(); i++) {
            bitmaps.add(details.valueAt(i).bitmap());
        }
        bases.clear();
        details.clear();
        baseAppearTime.clear();
        basesBytes = 0;
        failedPages.clear();
        AndroidUtilities.recycleBitmaps(bitmaps);
    }
}
