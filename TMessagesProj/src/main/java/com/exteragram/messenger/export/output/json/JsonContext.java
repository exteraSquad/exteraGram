/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export.output.json;

import com.exteragram.messenger.export.ExportFormat;
import com.exteragram.messenger.export.api.ExportData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class JsonContext {

    public static final boolean OBJECT = true;
    public static final boolean ARRAY = false;

    public record Item(String key, String value) {
    }

    public final ArrayList<Boolean> nesting = new ArrayList<>();

    public void push(boolean type) {
        nesting.add(type);
    }

    public void pop() {
        nesting.remove(nesting.size() - 1);
    }

    public static String serializeString(String value) {
        if (value == null) {
            value = "";
        }
        StringBuilder result = new StringBuilder(value.length() + 2);
        result.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case ' ' -> result.append("\\u2028");
                case ' ' -> result.append("\\u2029");
                default -> {
                    if (ch < 32) {
                        result.append(String.format(Locale.US, "\\u%04X", (int) ch));
                    } else {
                        result.append(ch);
                    }
                }
            }
        }
        return result.append('"').toString();
    }

    public static String serializeDate(int date) {
        return serializeString(ExportFormat.isoDate(date));
    }

    public static String serializeDateRaw(int date) {
        return serializeString(String.valueOf(date));
    }

    public static String stringAllowEmpty(String data) {
        return data == null || data.isEmpty() ? "" : serializeString(data);
    }

    public static String stringAllowNull(String data) {
        return data == null || data.isEmpty() ? "null" : serializeString(data);
    }

    public static String number(double value) {
        return Double.isFinite(value) ? String.format(Locale.US, "%f", value) : "null";
    }

    public static String bool(boolean value) {
        return value ? "true" : "false";
    }

    public String indentation() {
        return indentation(nesting.size());
    }

    public static String indentation(int size) {
        return " ".repeat(size);
    }

    public String serializeObject(List<Item> values) {
        String indent = indentation();
        push(OBJECT);
        String next = "\n" + indentation();
        pop();
        StringBuilder result = new StringBuilder("{");
        boolean first = true;
        for (Item item : values) {
            if (item.value() == null || item.value().isEmpty()) {
                continue;
            }
            if (!first) {
                result.append(',');
            }
            first = false;
            result.append(next).append(serializeString(item.key())).append(": ").append(item.value());
        }
        return result.append('\n').append(indent).append('}').toString();
    }

    public String serializeObject(Item... values) {
        return serializeObject(List.of(values));
    }

    public String serializeArray(List<String> values) {
        String indent = indentation();
        String next = "\n" + indentation(nesting.size() + 1);
        StringBuilder result = new StringBuilder("[");
        boolean first = true;
        for (String value : values) {
            if (!first) {
                result.append(',');
            }
            first = false;
            result.append(next).append(value);
        }
        return result.append('\n').append(indent).append(']').toString();
    }

    public static String textPartType(ExportData.TextPart.Type type) {
        return switch (type) {
            case Unknown -> "unknown";
            case Mention -> "mention";
            case Hashtag -> "hashtag";
            case BotCommand -> "bot_command";
            case Url -> "link";
            case Email -> "email";
            case Bold -> "bold";
            case Italic -> "italic";
            case Code -> "code";
            case Pre -> "pre";
            case Text -> "plain";
            case TextUrl -> "text_link";
            case MentionName -> "mention_name";
            case Phone -> "phone";
            case Cashtag -> "cashtag";
            case Underline -> "underline";
            case Strike -> "strikethrough";
            case Blockquote -> "blockquote";
            case BankCard -> "bank_card";
            case Spoiler -> "spoiler";
            case CustomEmoji -> "custom_emoji";
        };
    }

    public String serializeText(List<ExportData.TextPart> data, boolean serializeToObjects) {
        if (data.isEmpty()) {
            return serializeToObjects ? "[]" : serializeString("");
        }
        push(ARRAY);
        ArrayList<String> text = new ArrayList<>(data.size());
        for (ExportData.TextPart part : data) {
            if (part.type == ExportData.TextPart.Type.Text && !serializeToObjects) {
                text.add(serializeString(part.text));
                continue;
            }
            String additionalName;
            String additionalValue;
            switch (part.type) {
                case MentionName -> {
                    additionalName = "user_id";
                    additionalValue = part.additional;
                }
                case CustomEmoji -> {
                    additionalName = "document_id";
                    additionalValue = serializeString(part.additional);
                }
                case Pre -> {
                    additionalName = "language";
                    additionalValue = serializeString(part.additional);
                }
                case TextUrl -> {
                    additionalName = "href";
                    additionalValue = serializeString(part.additional);
                }
                case Blockquote -> {
                    additionalName = "collapsed";
                    additionalValue = bool(!part.additional.isEmpty());
                }
                default -> {
                    additionalName = "none";
                    additionalValue = "";
                }
            }
            text.add(serializeObject(
                    new Item("type", serializeString(textPartType(part.type))),
                    new Item("text", serializeString(part.text)),
                    new Item(additionalName, additionalValue)));
        }
        pop();
        if (!serializeToObjects && data.size() == 1 && data.get(0).type == ExportData.TextPart.Type.Text) {
            return text.get(0);
        }
        return serializeArray(text);
    }
}
