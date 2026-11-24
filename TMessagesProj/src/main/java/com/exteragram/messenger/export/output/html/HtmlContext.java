/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export.output.html;

import android.net.Uri;

import com.exteragram.messenger.export.ExportFormat;
import com.exteragram.messenger.export.api.ExportData;
import com.exteragram.messenger.export.api.ExportParser;
import com.exteragram.messenger.export.output.OutputFile;
import com.exteragram.messenger.export.output.Result;

import org.telegram.messenger.LocaleController;
import org.telegram.tgnet.TLRPC;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

final class HtmlContext {

    static final int PERSONAL_USERPIC_SIZE = 90;
    static final int ENTRY_USERPIC_SIZE = 48;
    static final int SERVICE_MESSAGE_PHOTO_SIZE = 60;
    static final int HISTORY_USERPIC_SIZE = 42;
    static final int SAVED_MESSAGES_COLOR_INDEX = 3;
    private static final int JOIN_WITHIN_SECONDS = 900;
    private static final int PHOTO_MAX_WIDTH = 520;
    private static final int PHOTO_MAX_HEIGHT = 520;
    private static final int PHOTO_MIN_WIDTH = 80;
    private static final int PHOTO_MIN_HEIGHT = 80;
    private static final int STICKER_MAX_WIDTH = 384;
    private static final int STICKER_MAX_HEIGHT = 384;
    private static final int STICKER_MIN_WIDTH = 80;
    private static final int STICKER_MIN_HEIGHT = 80;
    static final int STORY_THUMB_WIDTH = 45;
    static final int STORY_THUMB_HEIGHT = 80;

    static final class UserpicData {
        int colorIndex;
        int pixelSize;
        String imageLink = "";
        String largeLink = "";
        String firstName = "";
        String lastName = "";
        String tooltip = "";

        String composeName(String empty) {
            return firstName.isEmpty() && lastName.isEmpty() ? empty : (firstName + " " + lastName).trim();
        }

        void fillNames(ExportData.Peer peer) {
            if (peer.user != null) {
                firstName = peer.user.info.firstName;
                lastName = peer.user.info.lastName;
            } else if (peer.chat != null) {
                firstName = peer.name();
            }
        }

        void fillNames(String full) {
            String[] names = full.split(" ");
            firstName = names.length > 0 ? names[0] : "";
            StringBuilder last = new StringBuilder();
            for (int i = 1; i < names.length; i++) {
                if (names[i].isEmpty()) {
                    continue;
                }
                if (last.length() > 0) {
                    last.append(' ');
                }
                last.append(names[i]);
            }
            lastName = last.toString();
        }
    }

    static final class MessageInfo {
        enum Type {
            Service, Default
        }

        int id;
        long fromId;
        long viaBotId;
        int date;
        long forwardedFromId;
        String forwardedFromName = "";
        int forwardedDate;
        boolean forwarded;
        boolean showForwardedAsOriginal;
        Type type = Type.Service;
    }

    private static final class MediaData {
        String title = "";
        String description = "";
        String status = "";
        String classes = "";
        String thumb = "";
        String link = "";
    }

    private record Tag(String name, boolean block) {
    }

    private final OutputFile file;
    private final String base;
    private final String composedStart;
    private final ArrayList<Tag> tags = new ArrayList<>();
    private boolean closed;

    HtmlContext(String path, String basePath, OutputFile.Stats stats) {
        file = new OutputFile(path, stats);
        String left = path.substring(basePath.length());
        int nesting = 0;
        for (int i = 0; i < left.length(); i++) {
            if (left.charAt(i) == '/') {
                nesting++;
            }
        }
        base = "../".repeat(nesting);
        composedStart = composeStart();
    }

    static String serializeString(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\n', ' ', ' ' -> result.append("<br>");
                case '"' -> result.append("&quot;");
                case '&' -> result.append("&amp;");
                case '\'' -> result.append("&apos;");
                case '<' -> result.append("&lt;");
                case '>' -> result.append("&gt;");
                default -> {
                    if (ch < 32) {
                        result.append(String.format(Locale.US, "&#x%02X;", (int) ch));
                    } else {
                        result.append(ch);
                    }
                }
            }
        }
        return result.toString();
    }

    static String serializeList(List<String> values) {
        int count = values.size();
        if (count == 0) {
            return "";
        } else if (count == 1) {
            return values.get(0);
        }
        StringBuilder result = new StringBuilder(values.get(0));
        for (int i = 1; i < count - 1; i++) {
            result.append(", ").append(values.get(i));
        }
        return result.append(" and ").append(values.get(count - 1)).toString();
    }

    static String makeLinks(String value) {
        String domain = "https://telegram.org/";
        StringBuilder result = new StringBuilder();
        int offset = 0;
        while (true) {
            int start = value.indexOf(domain, offset);
            if (start < 0) {
                break;
            }
            int end = start + domain.length();
            while (end < value.length()) {
                char ch = value.charAt(end);
                if (!Character.isLetterOrDigit(ch) && ch != '-' && ch != '_' && ch != '/') {
                    break;
                }
                end++;
            }
            String link = value.substring(start, end);
            result.append(value, offset, start).append("<a href=\"").append(link).append("\">").append(link).append("</a>");
            offset = end;
        }
        if (offset == 0) {
            return value;
        }
        return result.append(value.substring(offset)).toString();
    }

    static String safeHref(String value) {
        if (value == null || value.isEmpty() || value.startsWith("//")) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch <= ' ' || ch == 0x7F) {
                return null;
            }
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("%0a") || lower.contains("%0d") || lower.contains("%00")) {
            return null;
        }
        Uri uri = Uri.parse(value);
        if (uri.getScheme() == null) {
            value = "https://" + value;
            uri = Uri.parse(value);
        }
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
        switch (scheme) {
            case "http", "https" -> {
                return uri.getHost() != null && !uri.getHost().isEmpty() ? value : null;
            }
            case "mailto", "tel", "tg" -> {
                return value;
            }
            default -> {
                return null;
            }
        }
    }

    private static String formatTextLink(String text, String target) {
        String href = safeHref(target);
        return href != null ? "<a href=\"" + serializeString(href) + "\">" + text + "</a>" : text;
    }

    static String formatCustomEmoji(String customEmoji, String text, String relativeLinkBase) {
        String start;
        if (customEmoji.isEmpty()) {
            start = "<a href=\"\" onclick=\"return ShowNotLoadedEmoji();\">";
        } else if (customEmoji.equals(ExportData.TextPart.UNAVAILABLE_EMOJI)) {
            start = "<a href=\"\" onclick=\"return ShowNotAvailableEmoji();\">";
        } else {
            start = "<a href=\"" + serializeString(relativeLinkBase + customEmoji) + "\">";
        }
        return start + text + "</a>";
    }

    static String formatText(List<ExportData.TextPart> data, String internalLinksDomain, String relativeLinkBase) {
        StringBuilder result = new StringBuilder();
        for (ExportData.TextPart part : data) {
            String text = serializeString(part.text);
            String tail = text.length() > 1 ? text.substring(1) : "";
            result.append(switch (part.type) {
                case Text, Unknown, BankCard -> text;
                case Mention -> "<a href=\"" + internalLinksDomain + tail + "\">" + text + "</a>";
                case Hashtag -> "<a href=\"\" onclick=\"return ShowHashtag(" + serializeString("\"" + tail + "\"") + ")\">" + text + "</a>";
                case BotCommand -> "<a href=\"\" onclick=\"return ShowBotCommand(" + serializeString("\"" + tail + "\"") + ")\">" + text + "</a>";
                case Url -> formatTextLink(text, part.text);
                case Email -> "<a href=\"mailto:" + text + "\">" + text + "</a>";
                case Bold -> "<strong>" + text + "</strong>";
                case Italic -> "<em>" + text + "</em>";
                case Code -> "<code>" + text + "</code>";
                case Pre -> "<pre>" + text + "</pre>";
                case TextUrl -> formatTextLink(text, part.additional);
                case MentionName -> "<a href=\"\" onclick=\"return ShowMentionName()\">" + text + "</a>";
                case Phone -> "<a href=\"tel:" + text + "\">" + text + "</a>";
                case Cashtag -> "<a href=\"\" onclick=\"return ShowCashtag(" + serializeString("\"" + tail + "\"") + ")\">" + text + "</a>";
                case Underline -> "<u>" + text + "</u>";
                case Strike -> "<s>" + text + "</s>";
                case Blockquote -> "<blockquote>" + text + "</blockquote>";
                case Spoiler -> "<span class=\"spoiler hidden\" onclick=\"ShowSpoiler(this)\"><span aria-hidden=\"true\">" + text + "</span></span>";
                case CustomEmoji -> formatCustomEmoji(part.additional, text, relativeLinkBase);
            });
        }
        return result.toString();
    }

    static String noFileDescription(ExportData.File.SkipReason reason) {
        return switch (reason) {
            case Unavailable -> "Unavailable, please try again later.";
            case FileSize -> "Exceeds maximum size, change data exporting settings to download.";
            case FileType -> "Not included, change data exporting settings to download.";
            case None, DateLimits -> "";
        };
    }

    static boolean displayDate(int date, int previousDate) {
        return previousDate == 0 || !ExportFormat.dateText(date).equals(ExportFormat.dateText(previousDate));
    }

    String base() {
        return base;
    }

    String relativePath(String path) {
        return base + path;
    }

    boolean isEmpty() {
        return file.empty();
    }

    String pushTag(String name, String... attributes) {
        boolean block = true;
        boolean empty = false;
        StringBuilder inner = new StringBuilder();
        for (int i = 0; i + 1 < attributes.length; i += 2) {
            String key = attributes[i];
            String value = attributes[i + 1];
            if (key == null || key.isEmpty()) {
                continue;
            } else if (key.equals("inline")) {
                block = false;
            } else if (key.equals("empty")) {
                empty = true;
            } else {
                inner.append(' ').append(key).append("=\"").append(serializeString(value)).append('"');
            }
        }
        String result = (block ? "\n" + indent() : "") + "<" + name + inner + (empty ? "/" : "") + ">" + (block ? "\n" : "");
        if (!empty) {
            tags.add(new Tag(name, block));
        }
        return result;
    }

    String popTag() {
        Tag last = tags.remove(tags.size() - 1);
        return (last.block() ? "\n" + indent() : "") + "</" + last.name() + ">" + (last.block() ? "\n" : "");
    }

    String indent() {
        return " ".repeat(tags.size());
    }

    String pushDiv(String className) {
        return pushTag("div", "class", className);
    }

    String pushDiv(String className, String style) {
        return style.isEmpty() ? pushDiv(className) : pushTag("div", "class", className, "style", style);
    }

    private String composeStart() {
        StringBuilder result = new StringBuilder("<!DOCTYPE html>").append(pushTag("html"));
        result.append(pushTag("head"));
        result.append(pushTag("meta", "charset", "utf-8", "empty", ""));
        result.append(pushTag("title", "inline", ""));
        result.append("Exported Data");
        result.append(popTag());
        result.append(pushTag("meta", "name", "viewport", "content", "width=device-width, initial-scale=1.0", "empty", ""));
        result.append(pushTag("link", "href", relativePath("css/style.css"), "rel", "stylesheet", "empty", ""));
        result.append(pushTag("script", "src", relativePath("js/script.js"), "type", "text/javascript"));
        result.append(popTag());
        result.append(popTag());
        result.append(pushTag("body", "onload", "CheckLocation();"));
        result.append(pushDiv("page_wrap"));
        return result.toString();
    }

    Result writeBlock(String block) {
        if (closed) {
            return Result.error(file.file().getAbsolutePath());
        }
        Result result = file.writeBlock((file.empty() ? composedStart : "") + block);
        if (!result.isSuccess()) {
            closed = true;
        }
        return result;
    }

    Result close() {
        if (closed || file.empty()) {
            closed = true;
            return Result.SUCCESS;
        }
        closed = true;
        StringBuilder block = new StringBuilder();
        while (!tags.isEmpty()) {
            block.append(popTag());
        }
        return file.writeBlock(block.toString().getBytes(StandardCharsets.UTF_8));
    }

    String pushUserpic(UserpicData userpic) {
        String size = userpic.pixelSize + "px";
        String sizeStyle = "width: " + size + "; height: " + size;
        StringBuilder result = new StringBuilder();
        if (!userpic.largeLink.isEmpty()) {
            result.append(pushTag("a", "class", "userpic_link", "href", relativePath(userpic.largeLink)));
        }
        if (!userpic.imageLink.isEmpty()) {
            result.append(pushTag("img", "class", "userpic", "style", sizeStyle, "src", relativePath(userpic.imageLink), "empty", ""));
        } else {
            result.append(pushTag("div", "class", "userpic userpic" + (userpic.colorIndex + 1), "style", sizeStyle));
            result.append(pushTag("div", "class", "initials", "style", "line-height: " + size, userpic.tooltip.isEmpty() ? null : "title", userpic.tooltip));
            result.append(initial(userpic.firstName)).append(initial(userpic.lastName));
            result.append(popTag());
            result.append(popTag());
        }
        if (!userpic.largeLink.isEmpty()) {
            result.append(popTag());
        }
        return result.toString();
    }

    private static String initial(String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        int end = trimmed.offsetByCodePoints(0, 1);
        return serializeString(trimmed.substring(0, end));
    }

    String pushListEntry(UserpicData userpic, String name, String details, String info, String link) {
        return pushGenericListEntry(link, userpic, name, "", List.of(details), info);
    }

    String pushSessionListEntry(int apiId, String name, String subname, List<String> details, String info) {
        UserpicData userpic = new UserpicData();
        userpic.colorIndex = ExportParser.applicationColorIndex(apiId);
        userpic.pixelSize = ENTRY_USERPIC_SIZE;
        userpic.firstName = name;
        return pushGenericListEntry("", userpic, name, subname, details, info);
    }

    String pushGenericListEntry(String link, UserpicData userpic, String name, String subname, List<String> details, String info) {
        StringBuilder result = new StringBuilder(link.isEmpty()
                ? pushDiv("entry clearfix")
                : pushTag("a", "class", "entry block_link clearfix", "href", relativePath(link) + "#allow_back"));
        result.append(pushDiv("pull_left userpic_wrap"));
        result.append(pushUserpic(userpic));
        result.append(popTag());
        result.append(pushDiv("body"));
        if (!info.isEmpty()) {
            result.append(pushDiv("pull_right info details")).append(serializeString(info)).append(popTag());
        }
        if (!name.isEmpty()) {
            result.append(pushDiv("name bold")).append(serializeString(name)).append(popTag());
        }
        if (!subname.isEmpty()) {
            result.append(pushDiv("subname bold")).append(serializeString(subname)).append(popTag());
        }
        for (String detail : details) {
            result.append(pushDiv("details_entry details")).append(serializeString(detail)).append(popTag());
        }
        result.append(popTag());
        result.append(popTag());
        return result.toString();
    }

    String pushStoriesListEntry(String imageLink, String name, List<String> details, String info, List<ExportData.TextPart> caption,
                                String internalLinksDomain, String link) {
        StringBuilder result = new StringBuilder(pushDiv("entry clearfix"));
        result.append(link.isEmpty()
                ? pushDiv("pull_left userpic_wrap")
                : pushTag("a", "class", "pull_left userpic_wrap", "href", relativePath(link) + "#allow_back"));
        if (!imageLink.isEmpty()) {
            String sizeStyle = "width: " + STORY_THUMB_WIDTH + "px; height: " + STORY_THUMB_HEIGHT + "px";
            result.append(pushTag("img", "class", "story", "style", sizeStyle, "src", relativePath(imageLink), "empty", ""));
        }
        result.append(popTag());
        result.append(pushDiv("body"));
        if (!info.isEmpty()) {
            result.append(pushDiv("pull_right info details")).append(serializeString(info)).append(popTag());
        }
        if (!name.isEmpty()) {
            if (!link.isEmpty()) {
                result.append(pushTag("a", "class", "block_link expanded", "href", relativePath(link) + "#allow_back"));
            }
            result.append(pushDiv("name bold")).append(serializeString(name)).append(popTag());
            if (!link.isEmpty()) {
                result.append(popTag());
            }
        }
        String text = formatText(caption, internalLinksDomain, base);
        if (!text.isEmpty()) {
            result.append(pushDiv("text")).append(text).append(popTag());
        }
        for (String detail : details) {
            result.append(pushDiv("details_entry details")).append(serializeString(detail)).append(popTag());
        }
        result.append(popTag());
        result.append(popTag());
        return result.toString();
    }

    String pushHeader(String header, String path) {
        StringBuilder result = new StringBuilder(pushDiv("page_header"));
        result.append(path.isEmpty()
                ? pushDiv("content")
                : pushTag("a", "class", "content block_link", "href", relativePath(path), "onclick", "return GoBack(this)"));
        result.append(pushDiv("text bold")).append(serializeString(header)).append(popTag());
        result.append(popTag());
        result.append(popTag());
        return result.toString();
    }

    String pushSection(String header, String type, int count, String link) {
        return pushTag("a", "class", "section block_link " + type, "href", link + "#allow_back")
                + pushDiv("counter details") + count + popTag()
                + pushDiv("label bold") + serializeString(header) + popTag()
                + popTag();
    }

    String pushAbout(String text, boolean withDivider) {
        return pushDiv(withDivider ? "page_about details with_divider" : "page_about details")
                + makeLinks(serializeString(text))
                + popTag();
    }

    String pushServiceMessage(int messageId, ExportData.DialogInfo dialog, String basePath, String serialized, ExportData.Photo photo) {
        StringBuilder result = new StringBuilder(pushTag("div", "class", "message service", "id", "message" + messageId));
        result.append(pushDiv("body details")).append(serialized).append(popTag());
        if (photo != null) {
            UserpicData userpic = new UserpicData();
            userpic.colorIndex = dialog.colorIndex;
            userpic.firstName = dialog.name;
            userpic.lastName = dialog.lastName;
            userpic.pixelSize = SERVICE_MESSAGE_PHOTO_SIZE;
            userpic.largeLink = photo.image.file.relativePath;
            userpic.imageLink = writeUserpicThumb(basePath, userpic.largeLink, userpic, "_thumb");
            result.append(pushDiv("userpic_wrap")).append(pushUserpic(userpic)).append(popTag());
        }
        result.append(popTag());
        return result.toString();
    }

    static String writeUserpicThumb(String basePath, String largePath, UserpicData userpic, String postfix) {
        return ExportThumbs.write(basePath, largePath, ExportThumbs.fixedSize(userpic.pixelSize * 2, userpic.pixelSize * 2), false, postfix).path();
    }

    private static boolean messageNeedsWrap(ExportData.Message message, MessageInfo previous) {
        if (previous == null || previous.type != MessageInfo.Type.Default) {
            return true;
        } else if (message.fromId == 0 || previous.fromId != message.fromId) {
            return true;
        } else if (message.viaBotId != previous.viaBotId) {
            return true;
        } else if (displayDate(message.date, previous.date)) {
            return true;
        } else if (message.forwarded != previous.forwarded
                || message.showForwardedAsOriginal != previous.showForwardedAsOriginal
                || message.forwardedFromId != previous.forwardedFromId
                || !message.forwardedFromName.equals(previous.forwardedFromName)) {
            return true;
        }
        boolean forwarded = message.forwardedFromId != 0 || !message.forwardedFromName.isEmpty();
        return Math.abs(message.date - previous.date) > (forwarded ? 1 : JOIN_WITHIN_SECONDS);
    }

    private static boolean forwardedNeedsWrap(ExportData.Message message, MessageInfo previous) {
        if (messageNeedsWrap(message, previous)) {
            return true;
        } else if (message.forwardedFromId == 0 || message.forwardedFromId != previous.forwardedFromId) {
            return true;
        } else if (message.forwardedFromId < 0) {
            return true;
        }
        return Math.abs(message.forwardedDate - previous.forwardedDate) > JOIN_WITHIN_SECONDS;
    }

    MessageInfo pushMessage(StringBuilder out, ExportData.Message message, MessageInfo previous, ExportData.DialogInfo dialog,
                            String basePath, Map<Long, ExportData.Peer> peersMap, String internalLinksDomain,
                            BiFunction<Integer, String, String> wrapMessageLink) {
        MessageInfo info = new MessageInfo();
        info.id = message.id;
        info.fromId = message.fromId;
        info.viaBotId = message.viaBotId;
        info.date = message.date;
        info.forwardedFromId = message.forwardedFromId;
        info.forwardedFromName = message.forwardedFromName;
        info.forwardedDate = message.forwardedDate;
        info.forwarded = message.forwarded;
        info.showForwardedAsOriginal = message.showForwardedAsOriginal;
        if (message.media.content instanceof ExportData.UnsupportedMedia) {
            out.append(pushServiceMessage(message.id, dialog, basePath, "This message is not supported by this version of the app.", null));
            return info;
        }
        HtmlPeers peers = new HtmlPeers(peersMap);
        UnaryOperator<String> wrapReplyToLink = text -> wrapMessageLink.apply(message.replyToMsgId, text);
        String serviceText = HtmlServiceText.text(message, dialog, peers, internalLinksDomain, base, wrapReplyToLink);
        if (!serviceText.isEmpty()) {
            out.append(pushServiceMessage(message.id, dialog, basePath, serviceText, message.actionPhoto));
            return info;
        }
        info.type = MessageInfo.Type.Default;

        boolean wrap = messageNeedsWrap(message, previous);
        boolean showForwardedInfo = message.forwarded && !message.showForwardedAsOriginal;
        UserpicData forwardedUserpic = new UserpicData();
        if (message.forwarded) {
            forwardedUserpic.colorIndex = ExportParser.peerColorIndex(message.forwardedFromId != 0 ? message.forwardedFromId : message.id);
            forwardedUserpic.pixelSize = HISTORY_USERPIC_SIZE;
            if (message.forwardedFromId != 0) {
                forwardedUserpic.fillNames(peers.peer(message.forwardedFromId));
            } else {
                forwardedUserpic.fillNames(message.forwardedFromName);
            }
        }
        UserpicData userpic;
        if (message.showForwardedAsOriginal) {
            userpic = forwardedUserpic;
        } else {
            userpic = new UserpicData();
            userpic.colorIndex = ExportParser.peerColorIndex(message.fromId);
            userpic.pixelSize = HISTORY_USERPIC_SIZE;
            userpic.fillNames(peers.peer(message.fromId));
        }
        String via = "";
        if (message.viaBotId != 0) {
            ExportData.User bot = peers.user(message.viaBotId);
            if (!bot.username.isEmpty()) {
                via = serializeString(bot.username);
            }
        }

        StringBuilder block = new StringBuilder(pushTag("div",
                "class", wrap ? "message default clearfix" : "message default clearfix joined",
                "id", "message" + message.id));
        if (wrap) {
            block.append(pushDiv("pull_left userpic_wrap")).append(pushUserpic(userpic)).append(popTag());
        }
        block.append(pushDiv("body"));
        block.append(pushTag("div", "class", "pull_right date details", "title", ExportFormat.dateTimeWithZone(message.date)));
        block.append(ExportFormat.timeText(message.date));
        block.append(popTag());
        if (wrap) {
            block.append(pushDiv("from_name"));
            block.append(serializeString(userpic.composeName("Deleted Account")));
            if (!via.isEmpty() && (!message.forwarded || message.showForwardedAsOriginal)) {
                block.append(" via @").append(via);
            }
            block.append(popTag());
        }
        if (showForwardedInfo) {
            boolean forwardedWrap = forwardedNeedsWrap(message, previous);
            if (forwardedWrap) {
                block.append(pushDiv("pull_left forwarded userpic_wrap")).append(pushUserpic(forwardedUserpic)).append(popTag());
            }
            block.append(pushDiv("forwarded body"));
            if (forwardedWrap) {
                block.append(pushDiv("from_name"));
                block.append(serializeString(forwardedUserpic.composeName("Deleted Account")));
                if (!via.isEmpty()) {
                    block.append(" via @").append(via);
                }
                block.append(pushTag("span", "class", "date details", "title", ExportFormat.dateTimeWithZone(message.forwardedDate), "inline", ""));
                block.append(' ').append(ExportFormat.dateTime(message.forwardedDate));
                block.append(popTag());
                block.append(popTag());
            }
        }
        if (message.replyToMsgId != 0) {
            block.append(pushDiv("reply_to details"));
            if (message.replyToPeerId != 0) {
                block.append("In reply to a message in another chat");
            } else {
                block.append("In reply to ").append(wrapReplyToLink.apply("this message"));
            }
            block.append(popTag());
        }
        block.append(pushMedia(message, basePath, peers, internalLinksDomain, wrapMessageLink, wrapReplyToLink));
        String text = formatText(message.text, internalLinksDomain, base);
        if (!text.isEmpty()) {
            block.append(pushDiv("text")).append(text).append(popTag());
        }
        if (!message.inlineButtonRows.isEmpty()) {
            block.append(pushInlineButtons(message.inlineButtonRows));
        }
        if (!message.signature.isEmpty()) {
            block.append(pushDiv("signature details")).append(serializeString(message.signature)).append(popTag());
        }
        if (showForwardedInfo) {
            block.append(popTag());
        }
        if (!message.reactions.isEmpty()) {
            block.append(pushReactions(message.reactions, peers));
        }
        block.append(popTag());
        block.append(popTag());
        out.append(block);
        return info;
    }

    private String pushInlineButtons(ArrayList<ArrayList<ExportData.InlineButton>> rows) {
        StringBuilder block = new StringBuilder(pushTag("table", "class", "bot_buttons_table"));
        block.append(pushTag("tbody"));
        for (ArrayList<ExportData.InlineButton> row : rows) {
            block.append(pushTag("tr"));
            block.append(pushTag("td", "class", "bot_button_row"));
            for (int i = 0; i < row.size(); i++) {
                ExportData.InlineButton button = row.get(i);
                String data = button.data != null ? new String(button.data, StandardCharsets.UTF_8) : "";
                String content = (data.isEmpty() ? "" : "Data: " + data + " | ")
                        + (button.forwardText.isEmpty() ? "" : "Forward text: " + button.forwardText + " | ")
                        + "Type: " + button.typeString();
                String link = button.type == ExportData.InlineButton.Type.Url ? safeHref(data) : null;
                String onclick = button.type != ExportData.InlineButton.Type.Url
                        ? "return ShowTextCopied('" + content.replace("\\", "\\\\").replace("'", "\\'") + "');"
                        : null;
                block.append(pushTag("div", "class", "bot_button"));
                block.append(pushTag("a", link != null ? "href" : null, link, onclick != null ? "onclick" : null, onclick));
                block.append(pushTag("div"));
                block.append(serializeString(button.text));
                block.append(popTag());
                block.append(popTag());
                block.append(popTag());
                if (i != row.size() - 1) {
                    block.append(pushTag("div", "class", "bot_button_column_separator"));
                    block.append(popTag());
                }
            }
            block.append(popTag());
            block.append(popTag());
        }
        block.append(popTag());
        block.append(popTag());
        return block.toString();
    }

    private String pushReactions(ArrayList<ExportData.Reaction> reactions, HtmlPeers peers) {
        StringBuilder block = new StringBuilder(pushTag("span", "class", "reactions"));
        for (ExportData.Reaction reaction : reactions) {
            String reactionClass = "reaction";
            for (ExportData.Reaction.Recent recent : reaction.recent) {
                ExportData.Peer peer = peers.peer(recent.peerId());
                if (peer.user != null && peer.user.isSelf) {
                    reactionClass += " active";
                    break;
                }
            }
            if (reaction.type == ExportData.Reaction.Type.Paid) {
                reactionClass += " paid";
            }
            block.append(pushTag("span", "class", reactionClass));
            block.append(pushTag("span", "class", "emoji"));
            switch (reaction.type) {
                case Emoji -> block.append(serializeString(reaction.emoji));
                case CustomEmoji -> block.append(formatCustomEmoji(reaction.documentId, "👋", base));
                case Paid -> block.append("⭐");
                default -> {
                }
            }
            block.append(popTag());
            if (!reaction.recent.isEmpty()) {
                block.append(pushTag("span", "class", "userpics"));
                for (ExportData.Reaction.Recent recent : reaction.recent) {
                    ExportData.Peer peer = peers.peer(recent.peerId());
                    UserpicData userpic = new UserpicData();
                    userpic.colorIndex = peer.colorIndex();
                    userpic.pixelSize = 20;
                    userpic.firstName = peer.user != null ? peer.user.info.firstName : peer.name();
                    userpic.lastName = peer.user != null ? peer.user.info.lastName : "";
                    userpic.tooltip = peer.name();
                    block.append(pushUserpic(userpic));
                }
                block.append(popTag());
            }
            if (reaction.recent.isEmpty() || reaction.count > reaction.recent.size()) {
                block.append(pushTag("span", "class", "count")).append(reaction.count).append(popTag());
            }
            block.append(popTag());
        }
        block.append(popTag());
        return block.toString();
    }

    private String pushMedia(ExportData.Message message, String basePath, HtmlPeers peers, String internalLinksDomain,
                             BiFunction<Integer, String, String> wrapMessageLink, UnaryOperator<String> wrapReplyToLink) {
        MediaData data = prepareMediaData(message, peers, internalLinksDomain);
        if (!data.classes.isEmpty()) {
            return pushGenericMedia(data);
        }
        Object content = message.media.content;
        if (content instanceof ExportData.Document document) {
            if (document.isSticker) {
                return pushStickerMedia(document, basePath);
            } else if (document.isAnimated) {
                return pushAnimatedMedia(document, basePath);
            } else if (document.isVideoFile) {
                return pushVideoFileMedia(document, basePath);
            }
        } else if (content instanceof ExportData.Photo photo) {
            return pushPhotoMedia(photo, basePath);
        } else if (content instanceof ExportData.Poll poll) {
            return pushPoll(poll, internalLinksDomain);
        } else if (content instanceof ExportData.TodoList todo) {
            return pushTodoList(todo, peers, internalLinksDomain);
        } else if (content instanceof ExportData.GiveawayStart giveaway) {
            return pushGiveaway(peers, giveaway);
        } else if (content instanceof ExportData.GiveawayResults giveaway) {
            return pushGiveaway(peers, giveaway, wrapMessageLink);
        }
        return "";
    }

    private static String mapsLink(ExportData.GeoPoint point) {
        String coords = String.format(Locale.US, "%f,%f", point.latitude, point.longitude);
        return "https://maps.google.com/maps?q=" + coords + "&ll=" + coords + "&z=16";
    }

    private MediaData prepareMediaData(ExportData.Message message, HtmlPeers peers, String internalLinksDomain) {
        MediaData result = new MediaData();
        if (message.action instanceof TLRPC.TL_messageActionConferenceCall call) {
            result.classes = "media_call";
            result.title = "Group call";
            result.status = call.active ? "Ongoing" : call.missed ? "Missed" : message.out ? "Outgoing" : "Incoming";
            if (call.duration > 0) {
                result.classes += " success";
                result.status += " (" + call.duration + " seconds)";
            }
            return result;
        } else if (message.action instanceof TLRPC.TL_messageActionPhoneCall call) {
            result.classes = "media_call";
            result.title = peers.peer(message.out ? message.peerId : message.selfId).name();
            boolean missed = call.reason instanceof TLRPC.TL_phoneCallDiscardReasonMissed;
            boolean busy = call.reason instanceof TLRPC.TL_phoneCallDiscardReasonBusy;
            result.status = message.out
                    ? (missed ? "Cancelled" : "Outgoing")
                    : missed ? "Missed" : busy ? "Declined" : "Incoming";
            if (call.duration > 0) {
                result.classes += " success";
                result.status += " (" + call.duration + " seconds)";
            }
            return result;
        }
        Object content = message.media.content;
        if (content instanceof ExportData.Photo photo) {
            if (message.media.ttl != 0) {
                result.title = "Self-destructing photo";
                result.status = photo.id != 0 ? "Please view it on your mobile" : "Expired";
                result.classes = "media_photo";
            }
        } else if (content instanceof ExportData.Document document) {
            if (message.media.ttl != 0) {
                result.title = "Self-destructing video";
                result.status = document.id != 0 ? "Please view it on your mobile" : "Expired";
                result.classes = "media_video";
                return result;
            }
            boolean hasFile = !document.file.relativePath.isEmpty();
            result.link = document.file.relativePath;
            result.description = noFileDescription(document.file.skipReason);
            if (document.isSticker || document.isAnimated || document.isVideoFile) {
                return result;
            } else if (document.isVideoMessage) {
                result.title = "Video message";
                result.status = ExportFormat.duration(document.duration);
                if (!hasFile) {
                    result.status += ", " + ExportFormat.fileSize(document.file.size);
                }
                result.thumb = document.thumb.file.relativePath;
                result.classes = "media_video";
            } else if (document.isVoiceMessage) {
                result.title = "Voice message";
                result.status = ExportFormat.duration(document.duration);
                if (!hasFile) {
                    result.status += ", " + ExportFormat.fileSize(document.file.size);
                }
                result.classes = "media_voice_message";
            } else if (document.isAudioFile) {
                result.title = document.songPerformer.isEmpty() || document.songTitle.isEmpty()
                        ? "Audio file"
                        : document.songPerformer + " – " + document.songTitle;
                result.status = ExportFormat.duration(document.duration);
                if (!hasFile) {
                    result.status += ", " + ExportFormat.fileSize(document.file.size);
                }
                result.classes = "media_audio_file";
            } else {
                result.title = document.name.isEmpty() ? "File" : document.name;
                result.status = ExportFormat.fileSize(document.file.size);
                result.classes = "media_file";
            }
        } else if (content instanceof ExportData.SharedContact contact) {
            result.title = contact.info.name();
            result.classes = "media_contact";
            result.status = ExportFormat.phoneNumber(contact.info.phoneNumber);
            if (contact.vcard.hasContent()) {
                result.status += " - vCard";
                result.link = contact.vcard.relativePath;
            }
        } else if (content instanceof ExportData.GeoPoint point) {
            if (message.media.ttl != 0) {
                result.classes = "media_live_location";
                result.title = "Live location";
            } else {
                result.classes = "media_location";
                result.title = "Location";
            }
            if (point.valid) {
                result.status = String.format(Locale.US, "%f, %f", point.latitude, point.longitude);
                result.link = mapsLink(point);
            }
        } else if (content instanceof ExportData.Venue venue) {
            result.classes = "media_venue";
            result.title = venue.title;
            result.description = venue.address;
            if (venue.point.valid) {
                result.link = mapsLink(venue.point);
            }
        } else if (content instanceof ExportData.Game game) {
            result.classes = "media_game";
            result.title = game.title;
            result.description = game.description;
            if (game.botId != 0 && !game.shortName.isEmpty()) {
                ExportData.User bot = peers.user(game.botId);
                if (bot.isBot && !bot.username.isEmpty()) {
                    result.link = internalLinksDomain + bot.username + "?game=" + game.shortName;
                    result.status = result.link;
                }
            }
        } else if (content instanceof ExportData.Invoice invoice) {
            result.classes = "media_invoice";
            result.title = invoice.title;
            result.description = invoice.description;
            result.status = LocaleController.getInstance().formatCurrencyString(invoice.amount, invoice.currency);
        } else if (content instanceof ExportData.Dice dice) {
            result.classes = "media_game";
            result.title = dice.emoticon.isEmpty() ? "Dice" : dice.emoticon + " Dice";
            result.status = "Result: " + dice.value;
        } else if (content instanceof ExportData.StoryReference story) {
            result.classes = "media_photo";
            result.title = "Story";
            if (story.peerId != 0) {
                ExportData.Peer peer = peers.peer(story.peerId);
                String username = peer.user != null ? peer.user.username : peer.chat != null ? peer.chat.username : "";
                String name = peer.name();
                result.status = name.isEmpty() ? "" : "From " + name;
                if (!username.isEmpty() && story.storyId != 0) {
                    result.link = internalLinksDomain + username + "/s/" + story.storyId;
                }
            }
        } else if (content instanceof ExportData.PaidMedia paid) {
            result.classes = "media_invoice";
            result.title = "Paid media";
            result.status = paid.stars + (paid.stars == 1 ? " Star" : " Stars");
        }
        return result;
    }

    private String pushGenericMedia(MediaData data) {
        StringBuilder result = new StringBuilder(pushDiv("media_wrap clearfix"));
        if (data.link.isEmpty()) {
            result.append(pushDiv("media clearfix pull_left " + data.classes));
        } else {
            String lower = data.link.toLowerCase(Locale.ROOT);
            String href = lower.startsWith("http://") || lower.startsWith("https://") ? data.link : relativePath(data.link);
            result.append(pushTag("a", "class", "media clearfix pull_left block_link " + data.classes, "href", href));
        }
        if (data.thumb.isEmpty()) {
            result.append(pushDiv("fill pull_left")).append(popTag());
        } else {
            result.append(pushTag("img", "class", "thumb pull_left", "src", relativePath(data.thumb), "empty", ""));
        }
        result.append(pushDiv("body"));
        if (!data.title.isEmpty()) {
            result.append(pushDiv("title bold")).append(serializeString(data.title)).append(popTag());
        }
        if (!data.description.isEmpty()) {
            result.append(pushDiv("description")).append(serializeString(data.description)).append(popTag());
        }
        if (!data.status.isEmpty()) {
            result.append(pushDiv("status details")).append(serializeString(data.status)).append(popTag());
        }
        result.append(popTag());
        result.append(popTag());
        result.append(popTag());
        return result.toString();
    }

    private String sizeStyle(int width, int height) {
        return "width: " + width + "px; height: " + height + "px";
    }

    private String pushStickerMedia(ExportData.Document data, String basePath) {
        ExportThumbs.Thumb thumb = ExportThumbs.write(basePath, data.file.relativePath,
                ExportThumbs.calculateThumbSize(STICKER_MAX_WIDTH, STICKER_MAX_HEIGHT, STICKER_MIN_WIDTH, STICKER_MIN_HEIGHT, false), true, "");
        if (thumb.isEmpty()) {
            MediaData generic = new MediaData();
            generic.title = "Sticker";
            generic.status = data.stickerEmoji;
            if (data.file.relativePath.isEmpty()) {
                if (!generic.status.isEmpty()) {
                    generic.status += ", ";
                }
                generic.status += ExportFormat.fileSize(data.file.size);
            } else {
                generic.link = data.file.relativePath;
            }
            generic.description = noFileDescription(data.file.skipReason);
            generic.classes = "media_photo";
            return pushGenericMedia(generic);
        }
        return pushDiv("media_wrap clearfix")
                + pushTag("a", "class", "sticker_wrap clearfix pull_left", "href", relativePath(data.file.relativePath))
                + pushTag("img", "class", "sticker", "style", sizeStyle(thumb.width() / 2, thumb.height() / 2), "src", relativePath(thumb.path()), "empty", "")
                + popTag()
                + popTag();
    }

    private String pushAnimatedMedia(ExportData.Document data, String basePath) {
        ExportThumbs.Size size = ExportThumbs.calculateThumbSize(PHOTO_MAX_WIDTH, PHOTO_MAX_HEIGHT, PHOTO_MIN_WIDTH, PHOTO_MIN_HEIGHT, true)
                .apply(new ExportThumbs.Size(data.width, data.height));
        if (data.thumb.file.relativePath.isEmpty() || data.file.relativePath.isEmpty() || size.isEmpty()) {
            MediaData generic = new MediaData();
            generic.title = "Animation";
            generic.status = ExportFormat.fileSize(data.file.size);
            generic.link = data.file.relativePath;
            generic.description = noFileDescription(data.file.skipReason);
            generic.classes = "media_video";
            return pushGenericMedia(generic);
        }
        return pushDiv("media_wrap clearfix")
                + pushTag("a", "class", "animated_wrap clearfix pull_left", "href", relativePath(data.file.relativePath))
                + pushDiv("video_play_bg") + pushDiv("gif_play") + "GIF" + popTag() + popTag()
                + pushTag("img", "class", "animated", "style", sizeStyle(size.width() / 2, size.height() / 2), "src", relativePath(data.thumb.file.relativePath), "empty", "")
                + popTag()
                + popTag();
    }

    private String pushVideoFileMedia(ExportData.Document data, String basePath) {
        ExportThumbs.Size size = ExportThumbs.calculateThumbSize(PHOTO_MAX_WIDTH, PHOTO_MAX_HEIGHT, PHOTO_MIN_WIDTH, PHOTO_MIN_HEIGHT, true)
                .apply(new ExportThumbs.Size(data.width, data.height));
        if (data.thumb.file.relativePath.isEmpty() || data.file.relativePath.isEmpty() || size.isEmpty()) {
            MediaData generic = new MediaData();
            generic.title = "Video file";
            generic.status = ExportFormat.duration(data.duration);
            if (data.file.relativePath.isEmpty()) {
                generic.status += ", " + ExportFormat.fileSize(data.file.size);
            } else {
                generic.link = data.file.relativePath;
            }
            generic.description = noFileDescription(data.file.skipReason);
            generic.classes = "media_video";
            return pushGenericMedia(generic);
        }
        return pushDiv("media_wrap clearfix")
                + pushTag("a", "class", "video_file_wrap clearfix pull_left", "href", relativePath(data.file.relativePath))
                + pushDiv("video_play_bg") + pushDiv("video_play") + popTag() + popTag()
                + pushDiv("video_duration") + ExportFormat.duration(data.duration) + popTag()
                + pushTag("img", "class", "video_file", "style", sizeStyle(size.width() / 2, size.height() / 2), "src", relativePath(data.thumb.file.relativePath), "empty", "")
                + popTag()
                + popTag();
    }

    private String pushPhotoMedia(ExportData.Photo data, String basePath) {
        ExportThumbs.Thumb thumb = ExportThumbs.write(basePath, data.image.file.relativePath,
                ExportThumbs.calculateThumbSize(PHOTO_MAX_WIDTH, PHOTO_MAX_HEIGHT, PHOTO_MIN_WIDTH, PHOTO_MIN_HEIGHT, false), false, "_thumb");
        if (thumb.isEmpty()) {
            MediaData generic = new MediaData();
            generic.title = "Photo";
            generic.status = data.image.width + "x" + data.image.height;
            if (data.image.file.relativePath.isEmpty()) {
                generic.status += ", " + ExportFormat.fileSize(data.image.file.size);
            } else {
                generic.link = data.image.file.relativePath;
            }
            generic.description = noFileDescription(data.image.file.skipReason);
            generic.classes = "media_photo";
            return pushGenericMedia(generic);
        }
        return pushDiv("media_wrap clearfix")
                + pushTag("a", "class", "photo_wrap clearfix pull_left", "href", relativePath(data.image.file.relativePath))
                + pushTag("img", "class", "photo", "style", sizeStyle(thumb.width() / 2, thumb.height() / 2), "src", relativePath(thumb.path()), "empty", "")
                + popTag()
                + popTag();
    }

    private static String votes(int count) {
        return count > 1 ? count + " votes" : count > 0 ? count + " vote" : "No votes";
    }

    private String pushPoll(ExportData.Poll data, String internalLinksDomain) {
        StringBuilder result = new StringBuilder(pushDiv("media_wrap clearfix"));
        result.append(pushDiv("media_poll"));
        result.append(pushDiv("question bold")).append(formatText(data.question, internalLinksDomain, base)).append(popTag());
        result.append(pushDiv("details")).append(data.closed ? "Final results" : "Anonymous poll").append(popTag());
        for (ExportData.Poll.Answer answer : data.answers) {
            result.append(pushDiv("answer")).append("- ").append(formatText(answer.text(), internalLinksDomain, base));
            if (answer.votes() > 0) {
                result.append(" <span class=\"details\">").append(votes(answer.votes())).append(answer.my() ? ", chosen vote" : "").append("</span>");
            }
            result.append(popTag());
        }
        result.append(pushDiv("total details")).append(votes(data.totalVotes)).append(popTag());
        result.append(popTag());
        result.append(popTag());
        return result.toString();
    }

    private String pushTodoList(ExportData.TodoList data, HtmlPeers peers, String internalLinksDomain) {
        StringBuilder result = new StringBuilder(pushDiv("media_wrap clearfix"));
        result.append(pushDiv("media_poll"));
        result.append(pushDiv("question bold")).append(formatText(data.title, internalLinksDomain, base)).append(popTag());
        result.append(pushDiv("details")).append("To-do List").append(popTag());
        int done = 0;
        for (ExportData.TodoList.Item item : data.items) {
            ExportData.TodoList.Completion completion = data.completions.get(item.id());
            result.append(pushDiv("answer"));
            result.append(completion != null ? "\u2713 " : "- ");
            result.append(formatText(item.text(), internalLinksDomain, base));
            if (completion != null) {
                done++;
                String by = completion.peerId() != 0 ? "done by " + peers.wrapPeerName(completion.peerId()) : "done";
                result.append(" <span class=\"details\">").append(by);
                if (completion.date() != 0) {
                    result.append(", ").append(ExportFormat.dateTime(completion.date()));
                }
                result.append("</span>");
            }
            result.append(popTag());
        }
        result.append(pushDiv("total details")).append(done).append(" of ").append(data.items.size()).append(" completed").append(popTag());
        result.append(popTag());
        result.append(popTag());
        return result.toString();
    }

    String pushAudioEntry(String name, String info, List<String> details, String duration, String link) {
        StringBuilder result = new StringBuilder(pushDiv("entry clearfix"));
        result.append(link.isEmpty()
                ? pushDiv("pull_left userpic_wrap")
                : pushTag("a", "class", "pull_left userpic_wrap", "href", relativePath(link) + "#allow_back"));
        result.append(pushDiv("userpic audio_icon")).append(popTag());
        result.append(popTag());
        result.append(pushDiv("body"));
        if (!duration.isEmpty()) {
            result.append(pushDiv("pull_right info details")).append(serializeString(duration)).append(popTag());
        }
        if (!info.isEmpty()) {
            if (!link.isEmpty()) {
                result.append(pushTag("a", "class", "block_link expanded", "href", relativePath(link) + "#allow_back"));
            }
            result.append(pushDiv("name bold")).append(serializeString(info)).append(popTag());
            if (!link.isEmpty()) {
                result.append(popTag());
            }
        }
        for (String detail : details) {
            result.append(pushDiv("details_entry details")).append(serializeString(detail)).append(popTag());
        }
        result.append(popTag());
        result.append(popTag());
        return result.toString();
    }

    private String pushSectionTitle(String title) {
        return pushDiv("section_title bold") + serializeString(title) + popTag();
    }

    private String pushGiveaway(HtmlPeers peers, ExportData.GiveawayStart data) {
        StringBuilder result = new StringBuilder(pushDiv("media_wrap clearfix"));
        result.append(pushDiv("media_giveaway"));
        result.append(pushSectionTitle(data.quantity > 1 ? "Giveaway Prizes" : "Giveaway Prize"));
        if (!data.additionalPrize.isEmpty()) {
            result.append(pushDiv("section_body")).append("<b>").append(data.quantity).append("</b> ").append(serializeString(data.additionalPrize)).append(popTag());
            result.append(pushSectionTitle("with"));
        }
        result.append(pushDiv("section_body"));
        if (data.credits > 0) {
            result.append("<b>").append(data.credits).append(data.credits == 1 ? " Star" : " Stars").append("</b> will be distributed ")
                    .append(data.quantity == 1 ? "to <b>1</b> winner." : "among <b>" + data.quantity + "</b> winners.");
        } else {
            result.append("<b>").append(data.quantity).append("</b> ")
                    .append(data.quantity > 1 ? "Telegram Premium Subscriptions" : "Telegram Premium Subscription")
                    .append(" for <b>").append(data.months).append("</b> ").append(data.months > 1 ? "months." : "month.");
        }
        result.append(popTag());
        result.append(pushSectionTitle("Participants"));
        result.append(pushDiv("section_body"));
        boolean anyChannel = false;
        boolean anyGroup = false;
        ArrayList<String> channels = new ArrayList<>();
        for (long channelId : data.channels) {
            ExportData.Peer peer = peers.peer(-channelId);
            if (peer.chat != null) {
                if (peer.chat.isBroadcast) {
                    anyChannel = true;
                } else if (peer.chat.isSupergroup) {
                    anyGroup = true;
                }
            }
            channels.add("<b>" + peers.wrapPeerName(-channelId) + "</b>");
        }
        boolean many = channels.size() > 1;
        String target = anyGroup && anyChannel
                ? (many ? "groups and channels" : "group")
                : anyGroup ? (many ? "groups" : "group") : (many ? "channels" : "channel");
        String participants = data.all
                ? (anyGroup ? "All members of the " : "All subscribers of the ") + target + ": "
                : "All users who joined the " + target + " below after this date: ";
        result.append(participants).append(String.join(", ", channels));
        result.append(popTag());
        if (!data.countries.isEmpty()) {
            ArrayList<String> countries = new ArrayList<>();
            for (String code : data.countries) {
                String name = new Locale("", code).getDisplayCountry(Locale.US);
                countries.add(countryFlag(code) + " " + (name.isEmpty() ? code : name));
            }
            result.append(pushDiv("section_body")).append(serializeString("from " + String.join(", ", countries))).append(popTag());
        }
        result.append(pushSectionTitle("Winners Selection Date"));
        result.append(pushDiv("section_body")).append(ExportFormat.dateTime(data.untilDate)).append(popTag());
        result.append(popTag());
        result.append(popTag());
        return result.toString();
    }

    private static String countryFlag(String code) {
        if (code == null || code.length() != 2) {
            return "";
        }
        String upper = code.toUpperCase(Locale.ROOT);
        int first = Character.codePointAt(upper, 0) - 'A' + 0x1F1E6;
        int second = Character.codePointAt(upper, 1) - 'A' + 0x1F1E6;
        return new String(Character.toChars(first)) + new String(Character.toChars(second));
    }

    private String pushGiveaway(HtmlPeers peers, ExportData.GiveawayResults data, BiFunction<Integer, String, String> wrapMessageLink) {
        StringBuilder result = new StringBuilder(pushDiv("media_wrap clearfix"));
        result.append(pushDiv("media_giveaway"));
        boolean many = data.winnersCount > 1;
        result.append(pushSectionTitle(many ? "Winners Selected!" : "Winner Selected!"));
        result.append(pushDiv("section_body")).append("<b>").append(data.winnersCount).append("</b> ")
                .append(many ? "winners" : "winner").append(" of the ").append(wrapMessageLink.apply(data.launchId, "Giveaway"))
                .append(many ? " were" : " was").append(" randomly selected by Telegram.").append(popTag());
        result.append(pushSectionTitle(many ? "Winners" : "Winner"));
        ArrayList<String> winners = new ArrayList<>();
        for (long winner : data.winners) {
            winners.add("<b>" + peers.wrapPeerName(winner) + "</b>");
        }
        result.append(pushDiv("section_body")).append(String.join(", ", winners));
        if (data.winnersCount > data.winners.size()) {
            result.append(" and ").append(data.winnersCount - data.winners.size()).append(" more!");
        }
        result.append(popTag());
        String prize;
        if (data.credits != 0) {
            String stars = "<b>" + data.credits + "</b>" + (data.credits == 1 ? " Star" : " Stars");
            prize = data.winnersCount == 1 ? "The winner received " + stars + "." : "All winners received " + stars + " in total.";
        } else if (data.unclaimedCount != 0) {
            prize = "Some winners couldn't be selected.";
        } else if (data.winnersCount == 1) {
            prize = "The winner received their gift link in a private message.";
        } else {
            prize = "All winners received gift links in private messages.";
        }
        result.append(pushDiv("section_body")).append(prize).append(popTag());
        result.append(popTag());
        result.append(popTag());
        return result.toString();
    }
}
