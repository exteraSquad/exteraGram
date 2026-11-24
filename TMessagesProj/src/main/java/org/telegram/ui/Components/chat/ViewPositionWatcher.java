package org.telegram.ui.Components.chat;

import android.graphics.PointF;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Tracks position changes of multiple Views relative to a specified ancestor ViewGroup.
 * Coordinates are computed manually by summing getX()/getY() up the hierarchy.
 *
 * Works through a single ViewTreeObserver.OnPreDrawListener attached to the given anchorView.
 */
public final class ViewPositionWatcher implements
        ViewTreeObserver.OnPreDrawListener,
        View.OnAttachStateChangeListener {

    /** Per-view callback invoked when a view's position relative to its parent changes. */
    public interface OnChangedListener {
        void onPositionChanged(@NonNull View view, @NonNull RectF rectInParent);
    }

    private final View anchorView;
    private ViewTreeObserver vto;
    private boolean listening;

    /** Per-view tracking state. */
    private static final class Tracked {
        final ViewGroup parent;
        final OnChangedListener listener;
        final RectF last = new RectF();
        boolean multiwindow;
        boolean trackReattach;
        boolean hasLast;
        boolean everAttached;
        @Nullable MultiwindowHook hook;

        Tracked(@NonNull ViewGroup parent, @NonNull OnChangedListener listener) {
            this.parent = parent;
            this.listener = listener;
        }
    }

    private static final class MultiwindowHook implements ViewTreeObserver.OnPreDrawListener {
        private final WeakReference<ViewPositionWatcher> watcherRef;
        private final WeakReference<View> viewRef;
        private ViewTreeObserver registeredOn;
        private boolean detached;

        MultiwindowHook(@NonNull ViewPositionWatcher watcher, @NonNull View view) {
            this.watcherRef = new WeakReference<>(watcher);
            this.viewRef = new WeakReference<>(view);
        }

        void ensureRegistered() {
            final View view = viewRef.get();
            if (view == null) return;
            final ViewTreeObserver vto = view.getViewTreeObserver();
            if (vto == null || !vto.isAlive() || vto == registeredOn) return;
            if (registeredOn != null && registeredOn.isAlive()) {
                registeredOn.removeOnPreDrawListener(this);
            }
            vto.removeOnPreDrawListener(this);
            vto.addOnPreDrawListener(this);
            registeredOn = vto;
            detached = false;
        }

        void detach() {
            detached = true;
            final ViewTreeObserver previous = registeredOn;
            registeredOn = null;
            if (previous != null && previous.isAlive()) {
                previous.removeOnPreDrawListener(this);
            }
            final View view = viewRef.get();
            if (view == null || !view.isAttachedToWindow()) return;
            final ViewTreeObserver vto = view.getViewTreeObserver();
            if (vto != null && vto != previous && vto.isAlive()) {
                vto.removeOnPreDrawListener(this);
            }
        }

        @Override
        public boolean onPreDraw() {
            final ViewPositionWatcher watcher = detached ? null : watcherRef.get();
            if (watcher == null) {
                detach();
                return true;
            }
            return watcher.onPreDraw();
        }
    }

    private final WeakHashMap<View, List<Tracked>> tracked = new WeakHashMap<>();
    private final WeakHashMap<View, MultiwindowHook> multiwindowHooks = new WeakHashMap<>();
    private final RectF tmpRect = new RectF(); // reused for all calculations
    private static final int[] tmpCords = new int[2];

    public ViewPositionWatcher(@NonNull View anchorView) {
        this.anchorView = anchorView;
        anchorView.addOnAttachStateChangeListener(this);
        attachIfPossible();
    }

    public void subscribe(@NonNull View view,
                          @NonNull ViewGroup parentView,
                          @NonNull OnChangedListener listener) {
        subscribe(view, parentView, listener, false);
    }

    /** Subscribe a view for tracking relative to the given parent (must be an ancestor). */
    public void subscribe(@NonNull View view,
                          @NonNull ViewGroup parentView,
                          @NonNull OnChangedListener listener,
                          boolean multiwindow) {
        subscribe(view, parentView, listener, multiwindow, false);
    }

    public void subscribe(@NonNull View view,
                          @NonNull ViewGroup parentView,
                          @NonNull OnChangedListener listener,
                          boolean multiwindow,
                          boolean trackReattach) {
        Tracked t = new Tracked(parentView, listener);
        t.multiwindow = multiwindow;
        t.trackReattach = trackReattach;
        List<Tracked> tList = tracked.get(view);
        if (tList == null) {
            tList = new ArrayList<>(1);
            tracked.put(view, tList);
        }
        tList.add(t);

        if (computeRectInParent(view, parentView, tmpRect)) {
            t.last.set(tmpRect);
            t.hasLast = true;
        }

        ensureListening();

        if (multiwindow) {
            MultiwindowHook hook = multiwindowHooks.get(view);
            if (hook == null) {
                hook = new MultiwindowHook(this, view);
                multiwindowHooks.put(view, hook);
            }
            t.hook = hook;
            hook.ensureRegistered();
        }
    }

    /** Unsubscribe a specific view. */
    public void unsubscribe(@NonNull View view) {
        tracked.remove(view);
        detachMultiwindowHook(view);
    }

    /** Clear all subscriptions. */
    public void clear() {
        tracked.clear();
        detachAllMultiwindowHooks();
    }

    /** Stop watching entirely. */
    public void shutdown() {
        detachIfListening();
        anchorView.removeOnAttachStateChangeListener(this);
        tracked.clear();
        detachAllMultiwindowHooks();
    }

    private void detachMultiwindowHook(@Nullable View view) {
        if (view == null) return;
        final MultiwindowHook hook = multiwindowHooks.remove(view);
        if (hook != null) {
            hook.detach();
        }
    }

    private void detachAllMultiwindowHooks() {
        if (multiwindowHooks.isEmpty()) return;
        for (MultiwindowHook hook : multiwindowHooks.values()) {
            hook.detach();
        }
        multiwindowHooks.clear();
    }

    // ─────────────── ViewTreeObserver lifecycle ───────────────

    private void attachIfPossible() {
        if (!anchorView.isAttachedToWindow()) return;
        ViewTreeObserver newVto = anchorView.getViewTreeObserver();
        if (newVto != null && newVto.isAlive()) {
            vto = newVto;
            if (!listening) {
                vto.addOnPreDrawListener(this);
                listening = true;
            }
        }
    }

    private void ensureListening() {
        if (!listening) attachIfPossible();
    }

    private void detachIfListening() {
        if (listening && vto != null && vto.isAlive()) {
            vto.removeOnPreDrawListener(this);
        }
        listening = false;
        vto = null;
    }

    @Override
    public void onViewAttachedToWindow(@NonNull View v) {
        attachIfPossible();
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull View v) {
        if (v == anchorView) {
            detachIfListening();
        }
    }

    // ─────────────── OnPreDraw ───────────────

    @Override
    public boolean onPreDraw() {
        // Reattach if VTO changed
        ViewTreeObserver current = anchorView.getViewTreeObserver();
        if (current != vto) {
            detachIfListening();
            attachIfPossible();
        }

        if (tracked.isEmpty()) {
            detachIfListening();
            detachAllMultiwindowHooks();
            return true;
        }

        for (Iterator<Map.Entry<View, List<Tracked>>> entryIterator = tracked.entrySet().iterator(); entryIterator.hasNext(); ) {
            var e = entryIterator.next();
            View view = e.getKey();
            List<Tracked> tList = e.getValue();
            if (view == null || tList == null || tList.isEmpty()) {
                entryIterator.remove();
                detachMultiwindowHook(view);
                continue;
            }
            if (!view.isAttachedToWindow()) {
                for (int i = tList.size() - 1; i >= 0; --i) {
                    Tracked t = tList.get(i);
                    if (t.everAttached && !t.trackReattach) {
                        tList.remove(i);
                    }
                }
                if (tList.isEmpty()) {
                    entryIterator.remove();
                    detachMultiwindowHook(view);
                }
                continue;
            }

            for (int i = tList.size() - 1; i >= 0; --i) {
                var t = tList.get(i);
                t.everAttached = true;
                if (t.multiwindow) {
                    if (t.hook != null) {
                        t.hook.ensureRegistered();
                    }
                    view.getLocationOnScreen(tmpCords);
                    tmpRect.set(tmpCords[0], tmpCords[1], tmpCords[0] + view.getWidth(), tmpCords[1] + view.getHeight());

                    t.parent.getLocationOnScreen(tmpCords);
                    tmpRect.offset(-tmpCords[0], -tmpCords[1]);
                } else {
                    if (!t.parent.isAttachedToWindow() || !computeRectInParent(view, t.parent, tmpRect)) {
                        tList.remove(i);
                        continue;
                    }
                }

                if (!t.hasLast || !tmpRect.equals(t.last)) {
                    t.last.set(tmpRect);
                    t.hasLast = true;
                    try {
                        t.listener.onPositionChanged(view, new RectF(tmpRect));
                    } catch (Throwable ignored) {
                        // Do not crash UI if callback throws
                    }
                }
            }

            if (tList.isEmpty()) {
                entryIterator.remove();
                detachMultiwindowHook(view);
            }
        }

        if (tracked.isEmpty()) {
            detachIfListening();
            detachAllMultiwindowHooks();
        }
        return true;
    }

    // ─────────────── Coordinate calculation ───────────────

    public static float computeYCoordinateInParent(@NonNull View view, @NonNull ViewGroup parentView) {
        computeRectInParent(view, parentView, tmpRectF2);
        return tmpRectF2.top;
    }

    public static float computeXCoordinateInParent(@NonNull View view, @NonNull ViewGroup parentView) {
        computeRectInParent(view, parentView, tmpRectF2);
        return tmpRectF2.left;
    }

    private static RectF tmpRectF2 = new RectF();
    public static boolean computeCoordinatesInParent(@NonNull View view,
                                                   @NonNull ViewGroup parentView, PointF out) {
        final boolean result = computeRectInParent(view, parentView, tmpRectF2);
        if (result) {
            out.x = tmpRectF2.left;
            out.y = tmpRectF2.top;
        }

        return result;
    }

    /**
     * Compute the view's rect in parentView coordinates
     * by summing getX()/getY() up the hierarchy until reaching parentView.
     *
     * @return false if parentView is not an ancestor of view.
     */

    private static final float[] tmpOffset = new float[2];

    /** The position of {@code view} in {@code root} by the sum of the positions of its ancestors; false if not under it. */
    private static boolean computeOffsetInRoot(@NonNull View view, @NonNull View root, float[] out) {
        float left = 0f, top = 0f;
        View current = view;
        while (current != root) {
            left += current.getX();
            top += current.getY();
            final ViewParent vp = current.getParent();
            if (!(vp instanceof View)) {
                return false;
            }
            final View parent = (View) vp;
            left -= parent.getScrollX();
            top -= parent.getScrollY();
            current = parent;
        }
        out[0] = left;
        out[1] = top;
        return true;
    }

    public static boolean computeRectInParent(@NonNull View view,
                                               @NonNull View parentView,
                                               @NonNull RectF out) {
        float left = 0f;
        float top = 0f;

        View current = view;
        while (current != null && current != parentView) {
            left += current.getX();
            top  += current.getY();

            ViewParent vp = current.getParent();
            if (!(vp instanceof View)) {
                break; // the root: parentView is not an ancestor
            }
            View parent = (View) vp;
            left -= parent.getScrollX();
            top  -= parent.getScrollY();

            current = parent;
        }

        if (current != parentView) {
            // parentView is not an ancestor (e.g. a menu over the tabs of a page whose glass it is): through their
            // common root, both positions summed up to it
            if (current == null || !computeOffsetInRoot(parentView, current, tmpOffset)) {
                return false;
            }
            left -= tmpOffset[0];
            top -= tmpOffset[1];
        }

        final float l = left;
        final float t = top;
        final float r = l + view.getWidth();
        final float b = t + view.getHeight();
        out.set(l, t, r, b);
        return true;
    }
}
