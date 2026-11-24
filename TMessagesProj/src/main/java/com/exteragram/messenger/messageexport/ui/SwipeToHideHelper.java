/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.messageexport.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.exteragram.messenger.messageexport.render.MessageExportListView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.ArrayList;

final class SwipeToHideHelper extends RecyclerView.ItemDecoration implements MessageExportListView.TouchInterceptor {

    interface Listener {
        boolean canHide(ArrayList<MessageObject> messages);

        void onHide(ArrayList<MessageObject> messages);
    }

    private static final int MAX_OFFSET_DP = 110;
    private static final int THRESHOLD_DP = 60;

    private final MessageExportListView listView;
    private final TextSelectionHelper.ChatListTextSelectionHelper selectionHelper;
    private final Listener listener;
    private final int touchSlop;
    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<ChatMessageCell> cells = new ArrayList<>();
    private final ArrayList<MessageObject> swipedMessages = new ArrayList<>();
    private Drawable icon;

    private float downX;
    private float downY;
    private ChatMessageCell downCell;
    private boolean swiping;
    private boolean thresholdPassed;
    private float offset;
    private ValueAnimator backAnimator;

    private SwipeToHideHelper(MessageExportListView listView, TextSelectionHelper.ChatListTextSelectionHelper selectionHelper, Listener listener) {
        this.listView = listView;
        this.selectionHelper = selectionHelper;
        this.listener = listener;
        touchSlop = ViewConfiguration.get(listView.getContext()).getScaledTouchSlop();
    }

    static void attach(MessageExportListView listView, TextSelectionHelper.ChatListTextSelectionHelper selectionHelper, Listener listener) {
        SwipeToHideHelper helper = new SwipeToHideHelper(listView, selectionHelper, listener);
        listView.setTouchInterceptor(helper);
        listView.addItemDecoration(helper);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        return handle(e);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        return handle(e) || swiping;
    }

    private boolean handle(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                downCell = null;
                swiping = false;
                if (!selectionHelper.isInSelectionMode()) {
                    View child = listView.findChildViewUnder(downX, downY);
                    if (child instanceof ChatMessageCell && ((ChatMessageCell) child).getMessageObject() != null) {
                        downCell = (ChatMessageCell) child;
                    }
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                if (downCell == null) {
                    return false;
                }
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                if (!swiping) {
                    if (dx < -touchSlop && Math.abs(dx) > Math.abs(dy) * 1.5f && listView.getScrollState() == RecyclerView.SCROLL_STATE_IDLE) {
                        if (!listener.canHide(messagesOf(downCell))) {
                            downCell = null;
                            return false;
                        }
                        start();
                        downX = e.getX();
                    } else if (Math.abs(dy) > touchSlop) {
                        downCell = null;
                    }
                    return swiping;
                }
                setOffset(Math.max(-dp(MAX_OFFSET_DP), Math.min(0, e.getX() - downX)));
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (swiping) {
                    finish(e.getActionMasked() == MotionEvent.ACTION_UP && thresholdPassed);
                    return true;
                }
                downCell = null;
                return false;
        }
        return swiping;
    }

    private static ArrayList<MessageObject> messagesOf(ChatMessageCell cell) {
        ArrayList<MessageObject> messages = new ArrayList<>();
        MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
        if (group != null) {
            messages.addAll(group.messages);
        } else if (cell.getMessageObject() != null) {
            messages.add(cell.getMessageObject());
        }
        return messages;
    }

    private void start() {
        swiping = true;
        thresholdPassed = false;
        if (backAnimator != null) {
            backAnimator.cancel();
        }
        cells.clear();
        swipedMessages.clear();
        swipedMessages.addAll(messagesOf(downCell));
        MessageObject.GroupedMessages group = downCell.getCurrentMessagesGroup();
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof ChatMessageCell && (child == downCell || group != null && ((ChatMessageCell) child).getCurrentMessagesGroup() == group)) {
                cells.add((ChatMessageCell) child);
            }
        }
        listView.getParent().requestDisallowInterceptTouchEvent(true);
        selectionHelper.clear();
    }

    private void setOffset(float value) {
        offset = value;
        boolean passed = value <= -dp(THRESHOLD_DP);
        if (passed != thresholdPassed) {
            thresholdPassed = passed;
            if (passed) {
                AndroidUtilities.vibrateCursor(listView);
            }
        }
        for (int i = 0; i < cells.size(); i++) {
            cells.get(i).setSlidingOffset(value);
        }
        listView.invalidate();
    }

    private void finish(boolean hide) {
        swiping = false;
        downCell = null;
        ArrayList<ChatMessageCell> swiped = new ArrayList<>(cells);
        float from = offset;
        if (backAnimator != null) {
            backAnimator.cancel();
        }
        ArrayList<MessageObject> messages = new ArrayList<>(swipedMessages);
        swipedMessages.clear();
        if (hide && !listener.canHide(messages)) {
            hide = false;
            AndroidUtilities.shakeViewSpring(listView, 4);
        }
        if (!hide) {
            backAnimator = ValueAnimator.ofFloat(1f, 0f);
            backAnimator.addUpdateListener(animation -> {
                offset = from * (float) animation.getAnimatedValue();
                for (int i = 0; i < swiped.size(); i++) {
                    swiped.get(i).setSlidingOffset(offset);
                }
                listView.invalidate();
            });
            backAnimator.setDuration(200);
            backAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            backAnimator.start();
            return;
        }
        offset = 0;
        cells.clear();
        listView.invalidate();
        if (!messages.isEmpty()) {
            listener.onHide(messages);
        }
    }

    @Override
    public void onDrawOver(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (offset >= 0 || cells.isEmpty()) {
            return;
        }
        float top = Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        for (int i = 0; i < cells.size(); i++) {
            ChatMessageCell cell = cells.get(i);
            top = Math.min(top, cell.getTop() + cell.getBackgroundDrawableTop());
            bottom = Math.max(bottom, cell.getTop() + cell.getBackgroundDrawableBottom());
        }
        if (icon == null) {
            icon = ContextCompat.getDrawable(parent.getContext(), R.drawable.msg_stories_stealth);
            if (icon != null) {
                icon = icon.mutate();
            }
        }
        Theme.ResourcesProvider provider = listView.getProvider();
        float progress = Math.min(1f, -offset / dp(THRESHOLD_DP));
        float cx = parent.getMeasuredWidth() + offset / 2f;
        float cy = (top + bottom) / 2f;
        float radius = dp(16) * (0.5f + 0.5f * progress);
        circlePaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_chat_serviceBackground, provider), progress));
        canvas.drawCircle(cx, cy, radius, circlePaint);
        if (icon != null) {
            int size = (int) (dp(22) * (0.5f + 0.5f * progress));
            icon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_serviceText, provider), PorterDuff.Mode.SRC_IN));
            icon.setAlpha((int) (255 * progress));
            icon.setBounds((int) (cx - size / 2f), (int) (cy - size / 2f), (int) (cx + size / 2f), (int) (cy + size / 2f));
            icon.draw(canvas);
        }
    }
}
