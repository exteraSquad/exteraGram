/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player.lyrics;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LrclibWordFile {

    private LrclibWordFile() {
    }

    static Lyrics read(String document, int source, String provider) {
        if (TextUtils.isEmpty(document)) {
            return null;
        }
        final Object root;
        try {
            root = new BlockReader(document).read();
        } catch (IllegalArgumentException e) {
            return null;
        }
        final List<?> entries = root instanceof Map ? asList(((Map<?, ?>) root).get("lines")) : null;
        if (entries == null) {
            return null;
        }
        final ArrayList<Lyrics.Line> lines = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            final long start = asLong(map.get("start_ms"));
            final String text = asString(map.get("text"));
            if (start < 0 || text == null) {
                continue;
            }
            final List<?> words = asList(map.get("words"));
            final Lyrics.LineBuilder builder = new Lyrics.LineBuilder();
            for (int i = 0; words != null && i < words.size(); i++) {
                if (words.get(i) instanceof Map<?, ?> word) {
                    final String part = asString(word.get("text"));
                    final long wordStart = asLong(word.get("start_ms"));
                    if (part != null && wordStart >= 0) {
                        builder.add(part, wordStart, asLong(word.get("end_ms")));
                    }
                }
            }
            if (builder.isEmpty()) {
                lines.add(new Lyrics.Line(start, text.trim()));
            } else {
                builder.closeLast(asLong(map.get("end_ms")));
                lines.add(builder.build(start));
            }
        }
        return lines.isEmpty() ? null : Lyrics.fromTimedLines(lines, source, provider);
    }

    private static List<?> asList(Object value) {
        return value instanceof List ? (List<?>) value : null;
    }

    private static String asString(Object value) {
        return value instanceof String ? (String) value : null;
    }

    private static long asLong(Object value) {
        if (!(value instanceof String)) {
            return -1;
        }
        try {
            return Long.parseLong(((String) value).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static final class Row {
        final int indent;
        final String content;

        Row(int indent, String content) {
            this.indent = indent;
            this.content = content;
        }
    }

    private static final class BlockReader {

        private final ArrayList<Row> rows = new ArrayList<>();
        private int position;

        BlockReader(String document) {
            for (String raw : document.split("\r\n|\r|\n")) {
                int indent = 0;
                while (indent < raw.length() && raw.charAt(indent) == ' ') {
                    indent++;
                }
                final String content = raw.substring(indent).trim();
                if (content.isEmpty() || content.startsWith("#") || content.equals("---") || content.equals("...")) {
                    continue;
                }
                rows.add(new Row(indent, content));
            }
        }

        Object read() {
            if (rows.isEmpty()) {
                return null;
            }
            final Object node = node(rows.get(0).indent);
            if (position != rows.size()) {
                throw new IllegalArgumentException("unexpected row " + position);
            }
            return node;
        }

        private Row peek() {
            return position < rows.size() ? rows.get(position) : null;
        }

        private static boolean isItem(Row row) {
            return row.content.equals("-") || row.content.startsWith("- ");
        }

        private Object node(int indent) {
            final Row row = peek();
            if (row == null || row.indent != indent) {
                return null;
            }
            return isItem(row) ? sequence(indent) : mapping(indent);
        }

        private List<Object> sequence(int indent) {
            final ArrayList<Object> list = new ArrayList<>();
            for (Row row = peek(); row != null && row.indent == indent && isItem(row); row = peek()) {
                final String rest = row.content.substring(1).trim();
                if (rest.isEmpty()) {
                    position++;
                    final Row child = peek();
                    list.add(child != null && child.indent > indent ? node(child.indent) : null);
                    continue;
                }
                final int restIndent = indent + row.content.indexOf(rest, 1);
                rows.set(position, new Row(restIndent, rest));
                list.add(keyEnd(rest) >= 0 ? mapping(restIndent) : scalar(restIndent, rest));
            }
            return list;
        }

        private Map<String, Object> mapping(int indent) {
            final LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            for (Row row = peek(); row != null && row.indent == indent && !isItem(row); row = peek()) {
                final int colon = keyEnd(row.content);
                if (colon < 0) {
                    throw new IllegalArgumentException("expected key at row " + position);
                }
                final String key = decode(row.content.substring(0, colon));
                final String value = row.content.substring(colon + 1).trim();
                if (value.isEmpty()) {
                    position++;
                    final Row child = peek();
                    if (child != null && (child.indent > indent || child.indent == indent && isItem(child))) {
                        map.put(key, node(child.indent));
                    } else {
                        map.put(key, null);
                    }
                } else if (value.startsWith("|") || value.startsWith(">")) {
                    position++;
                    map.put(key, blockScalar(indent, value.charAt(0) == '|'));
                } else {
                    map.put(key, scalar(indent, value));
                }
            }
            return map;
        }

        private String scalar(int indent, String first) {
            position++;
            final StringBuilder sb = new StringBuilder(first);
            for (Row row = peek(); row != null && row.indent > indent; row = peek()) {
                sb.append(' ').append(row.content);
                position++;
            }
            return decode(sb.toString());
        }

        private String blockScalar(int indent, boolean literal) {
            final StringBuilder sb = new StringBuilder();
            for (Row row = peek(); row != null && row.indent > indent; row = peek()) {
                if (!sb.isEmpty()) {
                    sb.append(literal ? '\n' : ' ');
                }
                sb.append(row.content);
                position++;
            }
            return sb.toString();
        }

        private static int keyEnd(String content) {
            int i = 0;
            final char first = content.charAt(0);
            if (first == '\'' || first == '"') {
                i = closingQuote(content, 0);
                if (i < 0) {
                    return -1;
                }
                i++;
                return i < content.length() && content.charAt(i) == ':' && (i + 1 == content.length() || content.charAt(i + 1) == ' ') ? i : -1;
            }
            for (; i < content.length(); i++) {
                if (content.charAt(i) == ':' && (i + 1 == content.length() || content.charAt(i + 1) == ' ')) {
                    return i;
                }
            }
            return -1;
        }

        private static int closingQuote(String s, int open) {
            final char quote = s.charAt(open);
            for (int i = open + 1; i < s.length(); i++) {
                final char c = s.charAt(i);
                if (quote == '"' && c == '\\') {
                    i++;
                } else if (c == quote) {
                    if (quote == '\'' && i + 1 < s.length() && s.charAt(i + 1) == '\'') {
                        i++;
                    } else {
                        return i;
                    }
                }
            }
            return -1;
        }

        private static String decode(String raw) {
            final String value = raw.trim();
            if (value.isEmpty() || value.equals("~") || value.equals("null")) {
                return null;
            }
            final char quote = value.charAt(0);
            if (quote != '\'' && quote != '"') {
                return value;
            }
            if (closingQuote(value, 0) != value.length() - 1) {
                throw new IllegalArgumentException("bad quoted scalar");
            }
            final String body = value.substring(1, value.length() - 1);
            if (quote == '\'') {
                return body.replace("''", "'");
            }
            final StringBuilder sb = new StringBuilder(body.length());
            for (int i = 0; i < body.length(); i++) {
                final char c = body.charAt(i);
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (++i >= body.length()) {
                    throw new IllegalArgumentException("dangling escape");
                }
                final char e = body.charAt(i);
                final int hex = e == 'x' ? 2 : e == 'u' ? 4 : e == 'U' ? 8 : 0;
                if (hex > 0) {
                    if (i + hex >= body.length()) {
                        throw new IllegalArgumentException("short escape");
                    }
                    sb.appendCodePoint(Integer.parseInt(body.substring(i + 1, i + 1 + hex), 16));
                    i += hex;
                    continue;
                }
                switch (e) {
                    case 'n':
                        sb.append('\n');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case '0':
                        sb.append('\0');
                        break;
                    case '_':
                        sb.append(' ');
                        break;
                    case 'N':
                        sb.append('\u0085');
                        break;
                    default:
                        sb.append(e);
                        break;
                }
            }
            return sb.toString();
        }
    }
}
