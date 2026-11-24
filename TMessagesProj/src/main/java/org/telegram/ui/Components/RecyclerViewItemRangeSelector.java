package org.telegram.ui.Components;

import android.util.SparseBooleanArray;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

import androidx.recyclerview.widget.RecyclerView;

public class RecyclerViewItemRangeSelector implements RecyclerView.OnItemTouchListener {

    private RecyclerView recyclerView;

    private int lastDraggedIndex = -1;
    private int initialSelection = -1;
    private int currentSelection = -1;
    private boolean dragSelectActive;
    private final SparseBooleanArray initialSelectedStates = new SparseBooleanArray();

    private int hotspotTopBoundStart;
    private int hotspotTopBoundEnd;
    private int hotspotBottomBoundStart;
    private int hotspotBottomBoundEnd;
    private boolean inTopHotspot;
    private boolean inBottomHotspot;

    private int autoScrollVelocity;
    private boolean isAutoScrolling;
    private float lastTouchX;
    private float lastTouchY;

    private int hotspotHeight = AndroidUtilities.dp(80);
    private int hotspotOffsetTop;
    private int hotspotOffsetBottom;

    private RecyclerViewItemRangeSelectorDelegate delegate;

    private static final int AUTO_SCROLL_DELAY = 15;

    public interface RecyclerViewItemRangeSelectorDelegate {
        int getItemCount();
        void setSelected(View view, int index, boolean selected);
        boolean isSelected(int index);
        boolean isIndexSelectable(int index);
        void onStartStopSelection(boolean start);
    }

    private Runnable autoScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (recyclerView == null) {
                return;
            }
            if (inTopHotspot) {
                recyclerView.scrollBy(0, -autoScrollVelocity);
                applySelectionAtTouchPosition();
                AndroidUtilities.runOnUIThread(this);
            } else if (inBottomHotspot) {
                recyclerView.scrollBy(0, autoScrollVelocity);
                applySelectionAtTouchPosition();
                AndroidUtilities.runOnUIThread(this);
            }
        }
    };

    public RecyclerViewItemRangeSelector(RecyclerViewItemRangeSelectorDelegate recyclerViewItemRangeSelectorDelegate) {
        delegate = recyclerViewItemRangeSelectorDelegate;
    }

    private void disableAutoScroll() {
        hotspotHeight = -1;
        hotspotOffsetTop = -1;
        hotspotOffsetBottom = -1;
    }

    @Override
    public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
        boolean adapterIsEmpty = rv.getAdapter() == null || rv.getAdapter().getItemCount() == 0;
        boolean result = dragSelectActive && !adapterIsEmpty;

        if (result) {
            recyclerView = rv;

            if (hotspotHeight > -1) {
                hotspotTopBoundStart = hotspotOffsetTop;
                hotspotTopBoundEnd = hotspotOffsetTop + hotspotHeight;
                hotspotBottomBoundStart = rv.getMeasuredHeight() - hotspotHeight - hotspotOffsetBottom;
                hotspotBottomBoundEnd = rv.getMeasuredHeight() - hotspotOffsetBottom;
            }
        }

        if (result && (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL)) {
            onDragSelectionStop();
        }
        return result;
    }

    @Override
    public void onTouchEvent(RecyclerView rv, MotionEvent e) {
        lastTouchX = e.getX();
        lastTouchY = e.getY();
        View v = rv.findChildViewUnder(e.getX(), e.getY());
        int itemPosition;
        if (v != null) {
            itemPosition = rv.getChildAdapterPosition(v);
        } else {
            itemPosition = RecyclerView.NO_POSITION;
        }
        float y = e.getY();
        switch (e.getAction()) {
            case MotionEvent.ACTION_UP: {
                onDragSelectionStop();
                return;
            }
            case MotionEvent.ACTION_CANCEL: {
                onDragSelectionStop();
                return;
            }
            case MotionEvent.ACTION_MOVE: {
                if (hotspotHeight > -1) {
                    if (y >= hotspotTopBoundStart && y <= hotspotTopBoundEnd) {
                        inBottomHotspot = false;
                        if (!inTopHotspot) {
                            inTopHotspot = true;
                            AndroidUtilities.cancelRunOnUIThread(autoScrollRunnable);
                            AndroidUtilities.runOnUIThread(autoScrollRunnable);
                        }
                        float simulatedFactor = (hotspotTopBoundEnd - hotspotTopBoundStart);
                        float simulatedY = y - hotspotTopBoundStart;
                        autoScrollVelocity = (int) (simulatedFactor - simulatedY) / 2;
                    } else if (y >= hotspotBottomBoundStart && y <= hotspotBottomBoundEnd) {
                        inTopHotspot = false;
                        if (!inBottomHotspot) {
                            inBottomHotspot = true;
                            AndroidUtilities.cancelRunOnUIThread(autoScrollRunnable);
                            AndroidUtilities.runOnUIThread(autoScrollRunnable);
                        }
                        float simulatedY = y + hotspotBottomBoundEnd;
                        float simulatedFactor = (hotspotBottomBoundStart + hotspotBottomBoundEnd);
                        autoScrollVelocity = (int) (simulatedY - simulatedFactor) / 2;
                    } else if (inTopHotspot || inBottomHotspot) {
                        AndroidUtilities.cancelRunOnUIThread(autoScrollRunnable);
                        inTopHotspot = false;
                        inBottomHotspot = false;
                    }
                }

                if (itemPosition != RecyclerView.NO_POSITION) {
                    if (lastDraggedIndex == itemPosition || !isSelectableIndex(itemPosition)) {
                        return;
                    }
                    applyRangeSelection(itemPosition);
                    lastDraggedIndex = itemPosition;
                    return;
                }
                break;
            }
        }
    }

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {

    }

    public boolean startSelection(View view, int selection) {
        if (dragSelectActive) {
            return false;
        }

        lastDraggedIndex = -1;
        initialSelection = -1;
        currentSelection = -1;
        initialSelectedStates.clear();
        AndroidUtilities.cancelRunOnUIThread(autoScrollRunnable);
        inTopHotspot = false;
        inBottomHotspot = false;

        if (!isSelectableIndex(selection)) {
            dragSelectActive = false;
            return false;
        }

        delegate.onStartStopSelection(true);
        dragSelectActive = true;
        lastDraggedIndex = initialSelection = currentSelection = selection;
        setIndexSelected(view, selection, !getInitialSelected(selection));

        return true;
    }

    private void applyRangeSelection(int newSelection) {
        if (initialSelection == -1 || currentSelection == -1) {
            return;
        }
        int oldStart = Math.min(initialSelection, currentSelection);
        int oldEnd = Math.max(initialSelection, currentSelection);
        int newStart = Math.min(initialSelection, newSelection);
        int newEnd = Math.max(initialSelection, newSelection);

        if (newStart < oldStart) {
            toggleRangeSelected(newStart, oldStart - 1);
        }
        if (newEnd > oldEnd) {
            toggleRangeSelected(oldEnd + 1, newEnd);
        }
        if (oldStart < newStart) {
            restoreRangeSelected(oldStart, newStart - 1);
        }
        if (oldEnd > newEnd) {
            restoreRangeSelected(newEnd + 1, oldEnd);
        }
        currentSelection = newSelection;
    }

    private void applySelectionAtTouchPosition() {
        if (recyclerView == null) {
            return;
        }
        View view = recyclerView.findChildViewUnder(lastTouchX, lastTouchY);
        if (view == null) {
            return;
        }
        int position = recyclerView.getChildAdapterPosition(view);
        if (position != RecyclerView.NO_POSITION && position != lastDraggedIndex && isSelectableIndex(position)) {
            applyRangeSelection(position);
            lastDraggedIndex = position;
        }
    }

    private void setIndexSelected(int index, boolean selected) {
        RecyclerView.ViewHolder holder = recyclerView != null ? recyclerView.findViewHolderForAdapterPosition(index) : null;
        setIndexSelected(holder != null ? holder.itemView : null, index, selected);
    }

    private void setIndexSelected(View view, int index, boolean selected) {
        if (!isSelectableIndex(index)) {
            return;
        }
        if (delegate.isSelected(index) == selected) {
            return;
        }
        delegate.setSelected(view, index, selected);
    }

    private void toggleRangeSelected(int start, int end) {
        for (int index = start; index <= end; index++) {
            if (isSelectableIndex(index)) {
                setIndexSelected(index, !getInitialSelected(index));
            }
        }
    }

    private void restoreRangeSelected(int start, int end) {
        for (int index = start; index <= end; index++) {
            if (isSelectableIndex(index)) {
                setIndexSelected(index, getInitialSelected(index));
            }
        }
    }

    private boolean getInitialSelected(int index) {
        if (initialSelectedStates.indexOfKey(index) < 0) {
            initialSelectedStates.put(index, delegate.isSelected(index));
        }
        return initialSelectedStates.get(index);
    }

    private boolean isSelectableIndex(int index) {
        return index >= 0 && index < delegate.getItemCount() && delegate.isIndexSelectable(index);
    }

    private void onDragSelectionStop() {
        if (!dragSelectActive) {
            return;
        }
        dragSelectActive = false;
        lastDraggedIndex = -1;
        initialSelection = -1;
        currentSelection = -1;
        initialSelectedStates.clear();
        inTopHotspot = false;
        inBottomHotspot = false;
        AndroidUtilities.cancelRunOnUIThread(autoScrollRunnable);
        delegate.onStartStopSelection(false);
    }
}
