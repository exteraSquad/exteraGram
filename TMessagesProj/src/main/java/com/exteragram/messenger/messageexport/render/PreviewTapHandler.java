/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.messageexport.render;

import android.view.MotionEvent;

import com.exteragram.messenger.messageexport.model.MessageExportSession;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.RichMessageLayout;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.Components.ButtonBounce;

import java.util.ArrayList;
import java.util.HashSet;

final class PreviewTapHandler {

    private final ChatMessageCell cell;
    private final MessageExportSession session;
    private MessageObject.TextLayoutBlock pressedBlock;
    private boolean mediaPressed;
    private boolean replyPressed;
    private boolean replyMediaPressed;
    private boolean richTracking;
    private TL_iv.PageBlock pressedRichMedia;
    private RichMessageLayout.RichSlideshowBlock slideshow;
    private boolean slideshowDragged;
    private float downX;
    private float downY;

    PreviewTapHandler(ChatMessageCell cell, MessageExportSession session) {
        this.cell = cell;
        this.session = session;
    }

    private boolean isReplyHit(float x, float y) {
        MessageObject message = cell.getMessageObject();
        if (message == null || cell.replyHeight <= 0 || !session.hasReply(message)) {
            return false;
        }
        if (!cell.replySelectorRect.isEmpty()) {
            return cell.replySelectorRect.contains(x, y);
        }
        return x >= cell.replyStartX && x <= cell.getBackgroundDrawableRight() && y >= cell.replyStartY && y <= cell.replyStartY + cell.replyHeight;
    }

    private boolean isReplyMediaHit(float x, float y) {
        if (!cell.needReplyImage || cell.replyImageReceiver == null || cell.replyImageReceiver.getImageWidth() <= 0) {
            return false;
        }
        float slop = AndroidUtilities.dp(4);
        return x >= cell.replyImageReceiver.getImageX() - slop && x <= cell.replyImageReceiver.getImageX2() + slop && y >= cell.replyImageReceiver.getImageY() - slop && y <= cell.replyImageReceiver.getImageY2() + slop;
    }

    static void ensureSpoilerBlur(ChatMessageCell cell) {
        if (isSpoilerBlurMissing(cell) && cell.getPhotoImage().getBitmap() != null) {
            cell.didSetImage(cell.getPhotoImage(), true, true, true);
        }
    }

    static boolean isSpoilerBlurMissing(ChatMessageCell cell) {
        MessageObject message = cell.getMessageObject();
        return message != null && message.hasMediaSpoilers() && !message.isMediaSpoilersRevealed && cell.getPhotoImage().hasImageSet() && cell.getBlurredPhotoImage().getBitmap() == null;
    }

    boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            pressedRichMedia = null;
            slideshow = null;
            RichMessageLayout.RichBlock slideshowBlock = findRichBlock(event, null);
            if (slideshowBlock instanceof RichMessageLayout.RichSlideshowBlock && dispatchToRich(event)) {
                slideshow = (RichMessageLayout.RichSlideshowBlock) slideshowBlock;
                slideshowDragged = false;
                downX = event.getX();
                downY = event.getY();
                return true;
            }
            richTracking = isRichToggleHit(event) && dispatchToRich(event);
            if (richTracking) {
                return true;
            }
            pressedRichMedia = findRichMedia(event);
            if (pressedRichMedia != null) {
                downX = event.getX();
                downY = event.getY();
                return true;
            }
        } else if (slideshow != null) {
            return onSlideshowTouch(event);
        } else if (richTracking) {
            boolean handled = dispatchToRich(event);
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                richTracking = false;
            }
            return handled;
        } else if (pressedRichMedia != null) {
            TL_iv.PageBlock block = pressedRichMedia;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_MOVE) {
                if (Math.abs(event.getX() - downX) > AndroidUtilities.touchSlop || Math.abs(event.getY() - downY) > AndroidUtilities.touchSlop) {
                    pressedRichMedia = null;
                }
                return pressedRichMedia != null;
            }
            pressedRichMedia = null;
            if (action == MotionEvent.ACTION_UP && cell.getMessageObject() != null) {
                session.toggleRichMediaSpoiler(cell.getMessageObject(), block);
                return true;
            }
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                float x = cell.getEventX(event);
                float y = cell.getEventY(event);
                replyPressed = isReplyHit(x, y);
                replyMediaPressed = replyPressed && isReplyMediaHit(x, y);
                if (replyPressed) {
                    return true;
                }
                setPressed(findBlock(x, y));
                mediaPressed = pressedBlock == null && isMediaHit(x, y);
                return pressedBlock != null || mediaPressed;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(event.getX() - downX) > AndroidUtilities.touchSlop || Math.abs(event.getY() - downY) > AndroidUtilities.touchSlop) {
                    setPressed(null);
                    mediaPressed = false;
                    replyPressed = false;
                    replyMediaPressed = false;
                }
                return pressedBlock != null || mediaPressed || replyPressed;
            case MotionEvent.ACTION_UP:
                MessageObject.TextLayoutBlock block = pressedBlock;
                boolean media = mediaPressed;
                boolean reply = replyPressed;
                boolean replyMedia = replyMediaPressed;
                setPressed(null);
                mediaPressed = false;
                replyPressed = false;
                replyMediaPressed = false;
                if (replyMedia && cell.getMessageObject() != null) {
                    session.toggleReplyMediaHidden(cell.getMessageObject());
                    return true;
                }
                if (reply && cell.getMessageObject() != null) {
                    session.toggleReplyHidden(cell.getMessageObject());
                    return true;
                }
                if (block != null && !cell.getTransitionParams().animateExpandedQuotes) {
                    toggleQuote(block);
                    return true;
                }
                if (media && cell.getMessageObject() != null) {
                    session.toggleMediaSpoiler(cell.getMessageObject());
                    return true;
                }
                return false;
            case MotionEvent.ACTION_CANCEL:
                setPressed(null);
                mediaPressed = false;
                replyPressed = false;
                replyMediaPressed = false;
                return false;
        }
        return false;
    }

    private boolean onSlideshowTouch(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_MOVE) {
            if (Math.abs(event.getX() - downX) > AndroidUtilities.touchSlop || Math.abs(event.getY() - downY) > AndroidUtilities.touchSlop) {
                slideshowDragged = true;
            }
            dispatchToRich(event);
            return true;
        }
        RichMessageLayout.RichSlideshowBlock block = slideshow;
        slideshow = null;
        if (action == MotionEvent.ACTION_UP && !slideshowDragged) {
            MotionEvent cancel = MotionEvent.obtain(event);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            dispatchToRich(cancel);
            cancel.recycle();
            int page = block.getCurrentPage();
            TL_iv.PageBlock pageBlock = page >= 0 && page < block.cells.size() ? block.cells.get(page).pageBlock : null;
            if (cell.getMessageObject() != null && (pageBlock instanceof TL_iv.pageBlockPhoto || pageBlock instanceof TL_iv.pageBlockVideo)) {
                session.toggleRichMediaSpoiler(cell.getMessageObject(), pageBlock);
            }
            return true;
        }
        dispatchToRich(event);
        return action == MotionEvent.ACTION_UP;
    }

    private boolean isRichToggleHit(MotionEvent event) {
        RichMessageLayout.RichBlock block = findRichBlock(event, null);
        boolean hit = block instanceof RichMessageLayout.RichDetailsBlock || block instanceof RichMessageLayout.RichQuoteBlock;
        return hit;
    }

    private TL_iv.PageBlock findRichMedia(MotionEvent event) {
        int[] top = new int[1];
        RichMessageLayout.RichBlock block = findRichBlock(event, top);
        TL_iv.PageBlock result = null;
        if (block instanceof RichMessageLayout.RichPhotoBlock) {
            result = ((RichMessageLayout.RichPhotoBlock) block).block;
        } else if (block instanceof RichMessageLayout.RichVideoBlock) {
            result = ((RichMessageLayout.RichVideoBlock) block).block;
        } else if (block instanceof RichMessageLayout.RichCollageBlock) {
            float x = cell.getEventX(event) - cell.textX - block.padding.left;
            float y = cell.getEventY(event) - cell.textY - top[0] - block.padding.top;
            ArrayList<RichMessageLayout.MediaCell> cells = ((RichMessageLayout.RichCollageBlock) block).cells;
            for (int i = 0; i < cells.size(); i++) {
                RichMessageLayout.MediaCell mediaCell = cells.get(i);
                if (x >= mediaCell.x && x < mediaCell.x + mediaCell.w && y >= mediaCell.y && y < mediaCell.y + mediaCell.h) {
                    result = mediaCell.pageBlock;
                    break;
                }
            }
        }
        if (!(result instanceof TL_iv.pageBlockPhoto) && !(result instanceof TL_iv.pageBlockVideo)) {
            result = null;
        }
        return result;
    }

    private RichMessageLayout.RichBlock findRichBlock(MotionEvent event, int[] outTop) {
        MessageObject message = cell.getMessageObject();
        if (message == null || message.type != MessageObject.TYPE_ARTICLE || message.richLayout == null) {
            return null;
        }
        RichMessageLayout rich = message.richLayout;
        float y = cell.getEventY(event) - cell.textY;
        float x = cell.getEventX(event) - cell.textX;
        int top = 0;
        boolean previousVisible = false;
        for (int i = 0; i < rich.blocks.size(); i++) {
            RichMessageLayout.RichBlock block = rich.blocks.get(i);
            if (!block.isVisible()) {
                continue;
            }
            if (previousVisible) {
                top += rich.getGap();
            }
            int height = block.getHeight();
            if (y >= top && y < top + height) {
                if (outTop != null) {
                    outTop[0] = top;
                }
                return block;
            }
            top += height;
            previousVisible = true;
        }
        return null;
    }

    private boolean dispatchToRich(MotionEvent event) {
        RichMessageLayout rich = cell.getMessageObject() != null ? cell.getMessageObject().richLayout : null;
        if (rich == null) {
            return false;
        }
        float dx = -cell.textX;
        float dy = cell.getEventY(event) - event.getY() - cell.textY;
        event.offsetLocation(dx, dy);
        rich.attach(cell);
        rich.setChatMessageCellDelegate(cell, cell.getDelegate());
        boolean handled = rich.onTouchEvent(event);
        event.offsetLocation(-dx, -dy);
        return handled;
    }

    private boolean isMediaHit(float x, float y) {
        MessageObject message = cell.getMessageObject();
        if (message == null || message.messageOwner == null || message.messageOwner.media == null || message.isAnyKindOfSticker() || message.isAnimatedEmoji() || message.isDice() || message.type == MessageObject.TYPE_EMOJIS) {
            return false;
        }
        ImageReceiver photo = cell.getPhotoImage();
        return photo.getVisible() && photo.hasImageSet() && photo.getImageWidth() > 0 && photo.getImageHeight() > 0 && photo.isInsideImage(x, y);
    }

    private void toggleQuote(MessageObject.TextLayoutBlock block) {
        MessageObject message = block.messageObject;
        if (message == null) {
            message = block.messageObject = cell.getPrimaryMessageObject();
        }
        if (message == null) {
            return;
        }
        boolean collapse = !block.collapsed();
        message.expandedQuotes = message.expandedQuotes == null ? new HashSet<>() : new HashSet<>(message.expandedQuotes);
        if (collapse) {
            message.expandedQuotes.remove(block.index);
        } else {
            message.expandedQuotes.add(block.index);
        }
        session.invalidateLayout(message);
    }

    private void setPressed(MessageObject.TextLayoutBlock block) {
        if (pressedBlock == block) {
            return;
        }
        if (pressedBlock != null && pressedBlock.collapsedBounce != null) {
            pressedBlock.collapsedBounce.setPressed(false);
        }
        pressedBlock = block;
        if (block != null) {
            if (block.collapsedBounce == null || block.collapsedBounce.getView() != cell) {
                block.collapsedBounce = new ButtonBounce(cell);
            }
            block.collapsedBounce.setPressed(true);
        }
    }

    private MessageObject.TextLayoutBlock findBlock(float x, float y) {
        MessageObject message = cell.getMessageObject();
        if (message == null) {
            return null;
        }
        if (message.textLayoutBlocks != null && x >= cell.textX && x <= cell.textX + message.textWidth && y >= cell.textY && y <= cell.textY + message.textHeight(cell.getTransitionParams())) {
            MessageObject.TextLayoutBlock block = findBlock(message.textLayoutBlocks, y - cell.textY);
            if (block != null) {
                return block;
            }
        }
        MessageObject.TextLayoutBlocks caption = cell.captionLayout;
        if (caption != null && y >= cell.captionY && y <= cell.captionY + caption.textHeight(cell.getTransitionParams())) {
            return findBlock(caption.textLayoutBlocks, y - cell.captionY);
        }
        return null;
    }

    private MessageObject.TextLayoutBlock findBlock(ArrayList<MessageObject.TextLayoutBlock> blocks, float y) {
        for (int i = 0; i < blocks.size(); i++) {
            MessageObject.TextLayoutBlock block = blocks.get(i);
            float top = block.textYOffset(blocks, cell.getTransitionParams());
            if (block.quoteCollapse && block.collapsedHeight < block.height && y >= top && y < top + block.padTop + block.height(cell.getTransitionParams()) + block.padBottom) {
                return block;
            }
        }
        return null;
    }
}
