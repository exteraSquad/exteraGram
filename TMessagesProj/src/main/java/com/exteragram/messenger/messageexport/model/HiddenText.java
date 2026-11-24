/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.messageexport.model;

import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.RichMessageLayout;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.spoilers.SpoilerEffect;

import java.util.ArrayList;

final class HiddenText {

    private static final class HiddenTextSpan extends TextStyleSpan {
        HiddenTextSpan() {
            super(createRun());
        }

        private static TextStyleRun createRun() {
            TextStyleRun run = new TextStyleRun();
            run.flags |= FLAG_STYLE_SPOILER;
            return run;
        }
    }

    private record Range(MessageObject message, boolean caption, int richBlock, int richText,
                         int start, int end, int operation) {
    }

    private interface RichTextVisitor {
        boolean visit(int blockIndex, int textIndex, int offset, TextSelectionHelper.TextLayoutBlock text);
    }

    private final ArrayList<Range> ranges = new ArrayList<>();
    private int operation;

    boolean hide(MessageObject message, boolean caption, int start, int end) {
        int size = ranges.size();
        operation++;
        if (isRich(message)) {
            forEachRichText(message.richLayout, (blockIndex, textIndex, offset, text) -> {
                int length = text.getLayout().getText().length();
                int from = Math.max(start, offset);
                int to = Math.min(end, offset + length);
                if (from < to) {
                    ranges.add(new Range(message, false, blockIndex, textIndex, from - offset, to - offset, operation));
                }
                return false;
            });
        } else {
            CharSequence text = caption ? message.caption : message.messageText;
            if (text == null) {
                return false;
            }
            int from = Math.max(0, Math.min(start, text.length()));
            int to = Math.max(from, Math.min(end, text.length()));
            if (from < to) {
                ranges.add(new Range(message, caption, -1, -1, from, to, operation));
            }
        }
        if (ranges.size() == size) {
            return false;
        }
        apply(message, true);
        return true;
    }

    ArrayList<int[]> getPlainRanges(MessageObject message, boolean caption) {
        ArrayList<int[]> result = new ArrayList<>();
        for (int i = 0; i < ranges.size(); i++) {
            Range range = ranges.get(i);
            if (range.message == message && range.richBlock < 0 && range.caption == caption) {
                result.add(new int[]{range.start, range.end});
            }
        }
        return result;
    }

    boolean isHidden(MessageObject message, boolean caption, int start, int end) {
        for (int i = 0; i < ranges.size(); i++) {
            if (overlaps(ranges.get(i), message, caption, start, end)) {
                return true;
            }
        }
        return processNativeSpoilers(message, caption, start, end, false);
    }

    boolean unhide(MessageObject message, boolean caption, int start, int end) {
        boolean removed = false;
        for (int i = ranges.size() - 1; i >= 0; i--) {
            if (overlaps(ranges.get(i), message, caption, start, end)) {
                ranges.remove(i);
                removed = true;
            }
        }
        boolean revealed = processNativeSpoilers(message, caption, start, end, true);
        if (removed) {
            apply(message, true);
        } else if (revealed) {
            refreshSpoilers(message, caption);
        }
        return removed || revealed;
    }

    private boolean processNativeSpoilers(MessageObject message, boolean caption, int start, int end, boolean reveal) {
        int to = Math.max(end, start + 1);
        if (isRich(message)) {
            boolean[] found = new boolean[1];
            forEachRichText(message.richLayout, (blockIndex, textIndex, offset, block) -> {
                CharSequence text = block.getLayout().getText();
                int from = Math.max(start - offset, 0);
                int until = Math.min(to - offset, text.length());
                if (from < until && text instanceof Spanned && processSpans((Spanned) text, from, until, reveal)) {
                    found[0] = true;
                    if (reveal) {
                        refreshRichSpoilers(block);
                    }
                    return !reveal;
                }
                return false;
            });
            return found[0];
        }
        CharSequence text = caption ? message.caption : message.messageText;
        if (!(text instanceof Spanned)) {
            return false;
        }
        int from = start;
        int until = to;
        ArrayList<TLRPC.MessageEntity> entities = reveal ? message.getEntities() : null;
        if (entities != null) {
            for (int i = entities.size() - 1; i >= 0; i--) {
                TLRPC.MessageEntity entity = entities.get(i);
                if (entity instanceof TLRPC.TL_messageEntitySpoiler && entity.offset < to && entity.offset + entity.length > start) {
                    from = Math.min(from, entity.offset);
                    until = Math.max(until, entity.offset + entity.length);
                    entities.remove(i);
                }
            }
        }
        return processSpans((Spanned) text, Math.max(0, from), Math.min(until, text.length()), reveal);
    }

    private static boolean processSpans(Spanned text, int start, int end, boolean reveal) {
        if (start >= end) {
            return false;
        }
        boolean found = false;
        TextStyleSpan[] spans = text.getSpans(start, end, TextStyleSpan.class);
        for (TextStyleSpan span : spans) {
            if (span instanceof HiddenTextSpan || !span.isSpoiler()) {
                continue;
            }
            found = true;
            if (!reveal) {
                return true;
            }
            span.getTextStyleRun().flags &= ~TextStyleSpan.FLAG_STYLE_SPOILER;
        }
        return found;
    }

    private void refreshSpoilers(MessageObject message, boolean caption) {
        if (!isRich(message) && !caption && message.messageText != null) {
            TLRPC.User fromUser = message.isFromUser() ? MessagesController.getInstance(message.currentAccount).getUser(message.messageOwner.from_id.user_id) : null;
            message.generateLayout(fromUser);
        }
        message.forceUpdate = true;
    }

    private static void refreshRichSpoilers(TextSelectionHelper.TextLayoutBlock block) {
        if (block instanceof RichMessageLayout.Text richText) {
            richText.spoilersPool.addAll(richText.spoilers);
            richText.spoilers.clear();
            SpoilerEffect.addSpoilers(null, richText.layout, richText.spoilersPool, richText.spoilers);
            richText.spoilersPatchedTextLayout.set(null);
        }
    }

    void undoLast(MessageObject message) {
        int last = -1;
        for (int i = 0; i < ranges.size(); i++) {
            if (ranges.get(i).message == message) {
                last = Math.max(last, ranges.get(i).operation);
            }
        }
        for (int i = ranges.size() - 1; i >= 0; i--) {
            if (ranges.get(i).message == message && ranges.get(i).operation == last) {
                ranges.remove(i);
            }
        }
        apply(message, true);
    }

    void ensure(MessageObject message) {
        for (int i = 0; i < ranges.size(); i++) {
            if (ranges.get(i).message == message) {
                apply(message, false);
                return;
            }
        }
    }

    private boolean overlaps(Range range, MessageObject message, boolean caption, int start, int end) {
        if (range.message != message) {
            return false;
        }
        int to = Math.max(end, start + 1);
        if (range.richBlock < 0) {
            return !isRich(message) && range.caption == caption && range.start < to && range.end > start;
        }
        if (!isRich(message)) {
            return false;
        }
        int offset = richOffset(message.richLayout, range.richBlock, range.richText);
        return offset >= 0 && offset + range.start < to && offset + range.end > start;
    }

    private void apply(MessageObject message, boolean force) {
        boolean changed;
        if (isRich(message)) {
            changed = applyRich(message, force);
        } else {
            boolean textChanged = applyPlain(message, false, force);
            changed = applyPlain(message, true, force) | textChanged;
            if (textChanged && message.messageText != null) {
                TLRPC.User fromUser = message.isFromUser() ? MessagesController.getInstance(message.currentAccount).getUser(message.messageOwner.from_id.user_id) : null;
                message.generateLayout(fromUser);
            }
        }
        if (changed) {
            message.forceUpdate = true;
        }
    }

    private boolean applyPlain(MessageObject message, boolean caption, boolean force) {
        CharSequence text = caption ? message.caption : message.messageText;
        if (text == null) {
            return false;
        }
        int expected = 0;
        for (int i = 0; i < ranges.size(); i++) {
            Range range = ranges.get(i);
            if (range.message == message && range.richBlock < 0 && range.caption == caption) {
                expected++;
            }
        }
        HiddenTextSpan[] existing = text instanceof Spanned ? ((Spanned) text).getSpans(0, text.length(), HiddenTextSpan.class) : new HiddenTextSpan[0];
        if (!force && existing.length == expected || expected == 0 && existing.length == 0) {
            return false;
        }
        Spannable spannable;
        if (text instanceof Spannable) {
            spannable = (Spannable) text;
        } else {
            spannable = new SpannableStringBuilder(text);
            if (caption) {
                message.caption = spannable;
            } else {
                message.messageText = spannable;
            }
        }
        for (HiddenTextSpan span : existing) {
            spannable.removeSpan(span);
        }
        for (int i = 0; i < ranges.size(); i++) {
            Range range = ranges.get(i);
            if (range.message == message && range.richBlock < 0 && range.caption == caption && range.end <= spannable.length()) {
                spannable.setSpan(new HiddenTextSpan(), range.start, range.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return true;
    }

    private boolean applyRich(MessageObject message, boolean force) {
        boolean[] changed = new boolean[1];
        forEachRichText(message.richLayout, (blockIndex, textIndex, offset, block) -> {
            Layout layout = block.getLayout();
            if (!(layout.getText() instanceof Spannable text)) {
                return false;
            }
            int expected = 0;
            for (int i = 0; i < ranges.size(); i++) {
                Range range = ranges.get(i);
                if (range.message == message && range.richBlock == blockIndex && range.richText == textIndex) {
                    expected++;
                }
            }
            HiddenTextSpan[] existing = text.getSpans(0, text.length(), HiddenTextSpan.class);
            if (!force && existing.length == expected || expected == 0 && existing.length == 0) {
                return false;
            }
            for (HiddenTextSpan span : existing) {
                text.removeSpan(span);
            }
            for (int i = 0; i < ranges.size(); i++) {
                Range range = ranges.get(i);
                if (range.message == message && range.richBlock == blockIndex && range.richText == textIndex && range.end <= text.length()) {
                    text.setSpan(new HiddenTextSpan(), range.start, range.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            refreshRichSpoilers(block);
            changed[0] = true;
            return false;
        });
        return changed[0];
    }

    private static boolean isRich(MessageObject message) {
        return message.type == MessageObject.TYPE_ARTICLE && message.richLayout != null;
    }

    private static int richOffset(RichMessageLayout rich, int blockIndex, int textIndex) {
        int[] result = {-1};
        forEachRichText(rich, (block, text, offset, layoutBlock) -> {
            if (block == blockIndex && text == textIndex) {
                result[0] = offset;
                return true;
            }
            return false;
        });
        return result[0];
    }

    private static void forEachRichText(RichMessageLayout rich, RichTextVisitor visitor) {
        int previousBlock = -1;
        int textIndex = 0;
        for (int i = 0; i < rich.textBlocks.size(); i++) {
            int blockIndex = rich.textBlockBlockIndex.get(i);
            textIndex = blockIndex == previousBlock ? textIndex + 1 : 0;
            previousBlock = blockIndex;
            TextSelectionHelper.TextLayoutBlock block = rich.textBlocks.get(i);
            if (block == null || block.getLayout() == null) {
                continue;
            }
            if (visitor.visit(blockIndex, textIndex, rich.textBlockCharOffsets.get(i), block)) {
                return;
            }
        }
    }
}
