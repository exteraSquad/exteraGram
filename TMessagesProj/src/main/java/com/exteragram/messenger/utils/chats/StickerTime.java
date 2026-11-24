/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.chats;

import static org.telegram.messenger.AndroidUtilities.dp;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.StickerTimeMode;

import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.Theme;

public final class StickerTime {
    private static final int SIDE_GAP = 8;
    private static final int LEFT_INSET = 6;
    private static final int PADDING = 12;
    private static final int STATUS_WIDTH = 20;
    private static final int BUTTON_WIDTH = 32;
    private static final int ROW_TOP_OFFSET = 23;
    private static final int ROW_MIN_HEIGHT = 17;
    private static final int ROW_TEXT_PADDING = 5;

    public static final class Row {
        public MessageObject message;
        public float overStickerOffsetX;
        public float timeWidth;
        public float stickerLeft;
        public float stickerRight;
        public float stickerWidth;
        public float anchorY;
        public float cellWidth;
        public boolean sideButton;
        public boolean hasName;
        public float nameLeft;
        public float nameWidth;
        public float nameTop;
        public float nameHeight;
    }

    public static boolean isHidden(MessageObject message) {
        return ExteraConfig.getStickerTimeMode() == StickerTimeMode.HIDDEN
                && message != null
                && message.isAnyKindOfSticker();
    }

    public static boolean shouldPreserveOnPreview(MessageObject previous, MessageObject next) {
        return previous != null
                && next != null
                && previous.getId() == next.getId()
                && next.preview
                && next.isAnyKindOfSticker()
                && ExteraConfig.getStickerTimeMode() != StickerTimeMode.HIDDEN;
    }

    public static float getTimeOffsetX(Row row, float baseTimeX) {
        if (row.message == null || !row.message.isAnyKindOfSticker()) {
            return 0;
        }
        if (!isBeside(row)) {
            return row.overStickerOffsetX;
        }
        return getTimeLeft(row) + dp(LEFT_INSET) - baseTimeX;
    }

    public static float getSideButtonX(Row row, float defaultX) {
        if (!isBeside(row)) {
            return defaultX;
        }
        final float reserved = dp(SIDE_GAP) + dp(BUTTON_WIDTH);
        if (row.message.isOutOwner()) {
            return Math.min(defaultX, getTimeLeft(row) - reserved);
        }
        return Math.max(defaultX, getTimeLeft(row) + getTimeWidth(row) + dp(SIDE_GAP));
    }

    public static float getSideButtonTouchLeft(Row row, float defaultPadding) {
        return isBeside(row) && !row.message.isOutOwner() ? dp(SIDE_GAP) : defaultPadding;
    }

    public static float getSideButtonTouchRight(Row row, float defaultPadding) {
        return isBeside(row) && row.message.isOutOwner() ? dp(SIDE_GAP) + dp(BUTTON_WIDTH) : defaultPadding;
    }

    private static boolean isBeside(Row row) {
        MessageObject message = row.message;
        if (message == null
                || !message.isAnyKindOfSticker()
                || ExteraConfig.getStickerTimeMode() != StickerTimeMode.SIDE
                || message.type == MessageObject.TYPE_EMOJIS
                || row.stickerWidth <= 0) {
            return false;
        }
        final float gap = dp(SIDE_GAP);
        float from = getTimeLeft(row);
        float to = from + getTimeWidth(row);
        if (row.sideButton) {
            if (message.isOutOwner()) {
                from -= gap + dp(BUTTON_WIDTH);
            } else {
                to += gap + dp(BUTTON_WIDTH);
            }
        }
        return from >= gap && to <= row.cellWidth - gap;
    }

    private static float getTimeWidth(Row row) {
        return row.timeWidth + dp(PADDING) + (row.message.isOutOwner() ? dp(STATUS_WIDTH) : 0);
    }

    private static float getTimeLeft(Row row) {
        final float gap = dp(SIDE_GAP);
        final boolean nameInRow = row.hasName && overlapsRow(row, row.nameTop, row.nameTop + row.nameHeight);
        if (row.message.isOutOwner()) {
            float right = row.stickerLeft - gap;
            if (nameInRow) {
                right = Math.min(right, row.nameLeft - gap);
            }
            return right - getTimeWidth(row);
        }
        float left = row.stickerRight + gap;
        if (nameInRow) {
            left = Math.max(left, row.nameLeft + row.nameWidth + gap);
        }
        return left;
    }

    private static boolean overlapsRow(Row row, float top, float bottom) {
        final float rowTop = row.anchorY - dp(ROW_TOP_OFFSET);
        final float rowBottom = rowTop + Math.max(dp(ROW_MIN_HEIGHT), Theme.chat_timePaint.getTextSize() + dp(ROW_TEXT_PADDING));
        return top < rowBottom && bottom > rowTop;
    }

    private StickerTime() {
    }
}
