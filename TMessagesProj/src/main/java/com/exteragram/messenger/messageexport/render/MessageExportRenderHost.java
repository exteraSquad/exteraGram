/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.messageexport.render;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.exteragram.messenger.messageexport.model.MessageExportDocument;
import com.exteragram.messenger.messageexport.model.MessageExportSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressLint("ViewConstructor")
final class MessageExportRenderHost extends FrameLayout {

    private static final int POOL_SIZE = 24;

    private final MessageExportSession session;
    private final ArrayList<MessageExportListView> pool = new ArrayList<>(POOL_SIZE);
    private int providerGeneration = -1;

    MessageExportRenderHost(Context ctx, MessageExportSession session) {
        super(ctx);
        this.session = session;
        setWillNotDraw(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    MessageExportListView obtain(MessageExportDocument.Block block, int width) {
        if (providerGeneration != session.getProviderGeneration()) {
            clear();
            providerGeneration = session.getProviderGeneration();
        }
        MessageExportListView view = null;
        for (int i = 0; i < pool.size(); i++) {
            List<MessageExportDocument.Block> blocks = pool.get(i).getBlocks();
            if (blocks != null && blocks.size() == 1 && blocks.get(0) == block) {
                view = pool.remove(i);
                break;
            }
        }
        if (view == null) {
            if (pool.size() < POOL_SIZE) {
                view = new MessageExportListView(getContext(), session, session.appearance.getProvider(), null);
                addView(view);
            } else {
                view = pool.remove(0);
            }
        }
        view.setBlocks(Collections.singletonList(block));
        view.setDrawingToBitmap(true);
        pool.add(view);
        view.measureAndLayout(width);
        return view;
    }

    void clear() {
        pool.clear();
        removeAllViews();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(0, 0);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
    }

    @Override
    public void requestLayout() {
    }

    @Override
    public void onDescendantInvalidated(@NonNull View child, @NonNull View target) {
    }

    @SuppressWarnings("deprecation")
    @Override
    public ViewParent invalidateChildInParent(int[] location, Rect dirty) {
        return null;
    }
}
