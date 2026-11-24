/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.messageexport.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.text.Layout;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.exteragram.messenger.messageexport.model.MessageExportSession;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.TextSelectionHelper;

final class HiddenTextSelectionHelper extends TextSelectionHelper.ChatListTextSelectionHelper {

    interface Delegate {
        boolean isBusy();

        void onHideText(MessageObject message, boolean caption, int start, int end);
    }

    private final MessageExportSession session;
    private final Delegate delegate;

    private float tapX;
    private float tapY;
    private long tapTime;
    private int tapOffset = -1;

    HiddenTextSelectionHelper(MessageExportSession session, Delegate delegate) {
        this.session = session;
        this.delegate = delegate;
    }

    @Override
    protected boolean canCopy() {
        return false;
    }

    @Override
    protected boolean canShowQuote() {
        return true;
    }

    @Override
    protected CharSequence getQuoteActionTitle() {
        ChatMessageCell cell = selectedView;
        boolean hidden = cell != null && cell.getMessageObject() != null && session.isHidden(cell.getMessageObject(), cell.hasCaptionLayout(), selectionStart, selectionEnd);
        return getString(hidden ? R.string.MessageExportShowText : R.string.MessageExportHideText);
    }

    @Override
    protected void onQuoteClick(MessageObject messageObject, int start, int end, CharSequence text) {
        ChatMessageCell cell = selectedView;
        if (cell == null || cell.getMessageObject() == null) {
            return;
        }
        MessageObject message = cell.getMessageObject();
        boolean caption = cell.hasCaptionLayout();
        if (session.isHidden(message, caption, start, end)) {
            session.unhide(message, caption, start, end);
        } else if (!isDescription && !isFactCheck && !delegate.isBusy()) {
            delegate.onHideText(message, caption, start, end);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            tapX = event.getX();
            tapY = event.getY();
            tapTime = event.getEventTime();
            tapOffset = -1;
            if (!isInSelectionMode() && maybeSelectedView != null && maybeSelectedView.getMessageObject() != null) {
                int offset = findTappedChar((int) tapX, (int) tapY);
                if (offset >= 0 && session.isHidden(maybeSelectedView.getMessageObject(), maybeSelectedView.hasCaptionLayout(), offset, offset + 1)) {
                    tapOffset = offset;
                }
            }
            return super.onTouchEvent(event) || tapOffset >= 0;
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP && tapOffset >= 0 && !isInSelectionMode() && maybeSelectedView != null && !delegate.isBusy()
                && Math.abs(event.getX() - tapX) < AndroidUtilities.touchSlop && Math.abs(event.getY() - tapY) < AndroidUtilities.touchSlop
                && event.getEventTime() - tapTime < ViewConfiguration.getLongPressTimeout()) {
            MessageObject message = maybeSelectedView.getMessageObject();
            if (message != null) {
                session.unhide(message, maybeSelectedView.hasCaptionLayout(), tapOffset, tapOffset + 1);
            }
        }
        return super.onTouchEvent(event);
    }

    private int findTappedChar(int x, int y) {
        if (!textArea.contains(x, y)) {
            return -1;
        }
        int offset = getCharOffsetFromCord(x, y, maybeTextX, maybeTextY, maybeSelectedView, true);
        Layout layout = layoutBlock.layout;
        if (offset < 0 || layout == null) {
            return -1;
        }
        float layoutX = x - maybeTextX - layoutBlock.xOffset;
        int line = layout.getLineForVertical((int) (y - maybeTextY - layoutBlock.yOffset));
        if (layoutX < layout.getLineLeft(line) || layoutX > layout.getLineRight(line)) {
            return -1;
        }
        int local = offset - layoutBlock.charOffset;
        if (local > layout.getLineStart(line)) {
            float edge = layout.getPrimaryHorizontal(local);
            if (layout.isRtlCharAt(local - 1) ? layoutX > edge : layoutX < edge) {
                offset--;
            }
        }
        return offset;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (parentView != null) {
            parentView.invalidate();
        }
    }
}
