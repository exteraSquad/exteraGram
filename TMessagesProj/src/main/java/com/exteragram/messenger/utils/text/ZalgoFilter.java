/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.text;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;

import com.exteragram.messenger.ExteraConfig;

import java.util.ArrayList;

public final class ZalgoFilter {
    private static final char REPLACEMENT = '\u2060';
    private static final int MAX_ALLOWED_ZALGO_MARKS_PER_SEQUENCE = 2;
    private static final int MAX_ALLOWED_SCRIPT_MARKS_PER_SEQUENCE = 4;

    private ZalgoFilter() {
    }

    public static boolean canFilter(CharSequence text) {
        return ExteraConfig.getFilterZalgo() && !TextUtils.isEmpty(text) && findReplacementRanges(text) != null;
    }

    public static CharSequence filterSpannable(CharSequence text) {
        if (!canFilter(text)) {
            return text;
        }

        if (!(text instanceof Spannable s)) {
            return filter(text);
        }

        final Spannable finalS = new SpannableString(filterEnabled(s.toString()));
        Object[] spans = s.getSpans(0, s.length(), Object.class);

        for (Object span : spans) {
            int start = Math.min(s.getSpanStart(span), finalS.length());
            int end = Math.min(s.getSpanEnd(span), finalS.length());
            int flags = s.getSpanFlags(span);

            if (start < 0) start = 0;
            if (end > finalS.length()) end = finalS.length();
            if (start > end) start = end;

            finalS.setSpan(span, start, end, flags);
        }
        return finalS;
    }

    public static String filter(CharSequence text) {
        if (text == null) {
            return null;
        }
        return filter(text.toString());
    }

    public static String filter(String text) {
        if (!ExteraConfig.getFilterZalgo() || TextUtils.isEmpty(text)) {
            return text;
        }

        return filterEnabled(text);
    }

    private static String filterEnabled(String text) {
        ArrayList<int[]> ranges = findReplacementRanges(text);
        if (ranges == null) {
            return text;
        }

        StringBuilder output = new StringBuilder(text.length());
        int lastEnd = 0;

        for (int[] range : ranges) {
            output.append(text, lastEnd, range[0]);
            appendReplacement(output, range[1] - range[0]);
            lastEnd = range[1];
        }

        return output.append(text, lastEnd, text.length()).toString();
    }

    private static ArrayList<int[]> findReplacementRanges(CharSequence text) {
        ArrayList<int[]> ranges = null;
        int markSequenceStart = -1;
        int markSequenceLength = 0;
        int maxAllowedMarks = MAX_ALLOWED_SCRIPT_MARKS_PER_SEQUENCE;
        int textLength = text.length();

        for (int i = 0; i < textLength; ) {
            int codePoint = Character.codePointAt(text, i);
            int charCount = Character.charCount(codePoint);

            int allowedMarks = getAllowedMarksPerSequence(codePoint);
            if (allowedMarks > 0) {
                if (markSequenceStart < 0) {
                    markSequenceStart = i;
                    markSequenceLength = 0;
                    maxAllowedMarks = allowedMarks;
                } else {
                    maxAllowedMarks = Math.min(maxAllowedMarks, allowedMarks);
                }
                markSequenceLength++;
            } else {
                ranges = addMarkSequenceRange(ranges, markSequenceStart, i, markSequenceLength, maxAllowedMarks);
                markSequenceStart = -1;
                markSequenceLength = 0;
                maxAllowedMarks = MAX_ALLOWED_SCRIPT_MARKS_PER_SEQUENCE;

                if (isDirectionControl(codePoint)) {
                    ranges = addRange(ranges, i, i + charCount);
                }
            }

            i += charCount;
        }

        return addMarkSequenceRange(ranges, markSequenceStart, textLength, markSequenceLength, maxAllowedMarks);
    }

    private static ArrayList<int[]> addMarkSequenceRange(
            ArrayList<int[]> ranges,
            int start,
            int end,
            int length,
            int maxAllowedMarks
    ) {
        if (start >= 0 && length > maxAllowedMarks) {
            return addRange(ranges, start, end);
        }
        return ranges;
    }

    private static ArrayList<int[]> addRange(ArrayList<int[]> ranges, int start, int end) {
        if (ranges == null) {
            ranges = new ArrayList<>();
        }
        ranges.add(new int[]{start, end});
        return ranges;
    }

    private static void appendReplacement(StringBuilder output, int charCount) {
        for (int i = 0; i < charCount; i++) {
            output.append(REPLACEMENT);
        }
    }

    private static int getAllowedMarksPerSequence(int codePoint) {
        if (codePoint < 0x0300) {
            return 0;
        }

        int type = Character.getType(codePoint);
        if (type != Character.NON_SPACING_MARK && type != Character.ENCLOSING_MARK) {
            return 0;
        }

        return isZalgoMarkRange(codePoint)
                ? MAX_ALLOWED_ZALGO_MARKS_PER_SEQUENCE
                : MAX_ALLOWED_SCRIPT_MARKS_PER_SEQUENCE;
    }

    private static boolean isZalgoMarkRange(int codePoint) {
        return (codePoint >= 0x0300 && codePoint <= 0x036F)
                || (codePoint >= 0x1AB0 && codePoint <= 0x1AFF)
                || (codePoint >= 0x1DC0 && codePoint <= 0x1DFF)
                || (codePoint >= 0x20D0 && codePoint <= 0x20FF)
                || (codePoint >= 0xFE20 && codePoint <= 0xFE2F);
    }

    private static boolean isDirectionControl(int codePoint) {
        return codePoint == 0x061C
                || codePoint == 0x200E
                || codePoint == 0x200F
                || (codePoint >= 0x202A && codePoint <= 0x202E)
                || (codePoint >= 0x2066 && codePoint <= 0x2069);
    }
}