/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.ai.ui;

import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.TypefaceSpan;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.utils.code.highlight.CodeHighlighting;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.Components.MarkdownParser;
import org.telegram.ui.Components.QuoteSpan;
import org.telegram.ui.iv.RichMessageConvert;

import java.util.ArrayList;

public final class MarkdownPreview {

    private MarkdownPreview() {
    }

    public static CharSequence format(String content) {
        if (TextUtils.isEmpty(content)) {
            return "";
        }

        ArrayList<TL_iv.PageBlock> blocks = new ArrayList<>();
        try {
            MarkdownParser.parse(content, blocks);
        } catch (Throwable t) {
            FileLog.e(t);
            blocks.clear();
        }
        if (blocks.isEmpty()) {
            return content;
        }

        CharSequence text = RichMessageConvert.blocksToCharSequence(blocks);
        if (TextUtils.isEmpty(text)) {
            return content;
        }

        if (text instanceof Spannable spannable) {
            for (CodeHighlighting.Span span : spannable.getSpans(0, spannable.length(), CodeHighlighting.Span.class)) {
                int start = spannable.getSpanStart(span);
                int end = spannable.getSpanEnd(span);
                spannable.removeSpan(span);
                spannable.setSpan(new TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        AndroidUtilities.removeSpans(text, QuoteSpan.QuoteStyleSpan.class);
        AndroidUtilities.removeSpans(text, QuoteSpan.class);

        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return end == text.length() ? text : text.subSequence(0, end);
    }
}
