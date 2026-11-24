/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.text;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.text.style.ReplacementSpan;
import android.text.style.URLSpan;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.utils.network.RemoteUtils;
import com.exteragram.messenger.utils.ui.ColorRectSpan;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LinkifyPort;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.URLSpanReplacement;
import org.telegram.ui.FilterCreateActivity;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocaleUtils {
    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("\\[([^]]+?)]\\(" + LinkifyPort.WEB_URL_REGEX + "\\)");
    private static final Pattern HEX_PATTERN = Pattern.compile("(?<![a-zA-Z0-9])#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})(?![a-zA-Z0-9])");

    public static String normalizeResourceLanguage(String language) {
        if (TextUtils.isEmpty(language)) {
            return null;
        }
        language = language.toLowerCase(Locale.US);
        if ("he".equals(language)) {
            return "iw";
        } else if ("no".equals(language)) {
            return "nb";
        }
        return language;
    }

    public static String normalizeResourceRegion(String language, String region) {
        if (TextUtils.isEmpty(region)) {
            return null;
        }
        language = normalizeResourceLanguage(language);
        region = region.toUpperCase(Locale.US);
        if ("zh".equals(language)) {
            if ("HANS".equals(region) || "CN".equals(region) || "SG".equals(region)) {
                return "CN";
            } else if ("HANT".equals(region) || "TW".equals(region) || "HK".equals(region) || "MO".equals(region)) {
                return "TW";
            }
        }
        return region;
    }

    public static String getActionBarTitle() {
        return getActionBarTitle(UserConfig.selectedAccount);
    }

    public static String getActionBarTitle(int account) {
        String title;
        int actionBarTitle = ExteraConfig.getTitleText();
        switch (actionBarTitle) {
            case 0 -> title = LocaleController.getString(R.string.exteraAppName);
            case 3 -> title = LocaleController.getString(R.string.FilterChats);
            default -> {
                TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
                title = actionBarTitle == 1 && !TextUtils.isEmpty(UserObject.getPublicUsername(user)) ? UserObject.getPublicUsername(user) : UserObject.getFirstName(user);
            }
        }
        return title;
    }

    public static CharSequence formatWithUsernames(CharSequence text) {
        return formatWithUsernames(text, LaunchActivity.getSafeLastFragment());
    }

    public static CharSequence formatWithUsernames(CharSequence text, BaseFragment fragment) {
        return formatWithUsernames(text, fragment, null);
    }

    public static CharSequence formatWithUsernames(CharSequence text, BaseFragment fragment, Runnable onClick) {
        if (TextUtils.isEmpty(text)) {
            return text;
        }
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(text);
        int start = -1;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '@') {
                start = i;
            } else if (start != -1 && (i + 1 == text.length() ||
                    !(Character.isLetterOrDigit(text.charAt(i + 1)) || text.charAt(i + 1) == '_'))) {
                int end = i + 1;
                if (end - start > 1) {
                    URLSpan[] spans = stringBuilder.getSpans(start, end, URLSpan.class);
                    if (spans != null && spans.length > 0) {
                        start = -1;
                        continue;
                    }
                    String username = text.subSequence(start, end).toString();
                    try {
                        URLSpanNoUnderline urlSpan = new URLSpanNoUnderline(username) {
                            @Override
                            public void onClick(View widget) {
                                if (onClick != null) {
                                    onClick.run();
                                }
                                if (fragment != null && fragment.getMessagesController() != null) {
                                    fragment.getMessagesController().openByUserName(username.substring(1), fragment, 1);
                                }
                            }
                        };
                        stringBuilder.setSpan(urlSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                start = -1;
            }
        }

        return stringBuilder;
    }

    public static CharSequence formatWithHtmlURLs(CharSequence charSequence) {
        if (TextUtils.isEmpty(charSequence)) {
            return charSequence;
        }
        Spannable spannable = new SpannableString(charSequence);
        URLSpan[] spans = spannable.getSpans(0, charSequence.length(), URLSpan.class);
        SpannableStringBuilder ssb = new SpannableStringBuilder(spannable);

        for (URLSpan urlSpan : spans) {
            int start = ssb.getSpanStart(urlSpan);
            int end = ssb.getSpanEnd(urlSpan);
            String url = urlSpan.getURL();
            ssb.removeSpan(urlSpan);

            URLSpanNoUnderline newSpan = new URLSpanNoUnderline(url) {
                @Override
                public void onClick(View widget) {
                    super.onClick(widget);
                }
            };
            ssb.setSpan(newSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return ssb;
    }

    public static CharSequence formatWithURLs(CharSequence charSequence) {
        if (TextUtils.isEmpty(charSequence)) {
            return charSequence;
        }

        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(charSequence);
        Matcher matcher = LinkifyPort.WEB_URL.matcher(charSequence);

        while (matcher.find()) {
            try {
                URLSpanNoUnderline urlSpan = new URLSpanNoUnderline(ensureUrlHasHttps(matcher.group(0))) {
                    @Override
                    public void onClick(View widget) {
                        super.onClick(widget);
                    }
                };
                stringBuilder.setSpan(urlSpan, matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        return stringBuilder;
    }

    public static CharSequence fullyFormatText(CharSequence text) {
        return fullyFormatText(text, null, null);
    }

    public static CharSequence fullyFormatText(CharSequence text, BaseFragment fragment, Runnable onClick) {
        if (TextUtils.isEmpty(text)) {
            return text;
        }

        text = formatWithURLs(text);

        CharSequence[] arr = new CharSequence[]{text};
        parseMarkdownLinks(arr, onClick);
        text = arr[0];

        if (fragment != null && onClick != null) {
            text = formatWithUsernames(text, fragment, onClick);
        } else {
            text = formatWithUsernames(text);
        }

        text = AndroidUtilities.replaceTags(text);

        return text;
    }

    public static CharSequence fromHtml(String text) {
        return new SpannableString(Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY));
    }

    public static String capitalize(String s) {
        if (s == null)
            return null;
        char[] chars = s.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (i == 0) {
                chars[i] = Character.toUpperCase(chars[i]);
            } else if (Character.isLetter(chars[i])) {
                chars[i] = Character.toLowerCase(chars[i]);
            }
        }
        return new String(chars);
    }

    public static String getAppName() {
        try {
            return ApplicationLoader.applicationContext.getString(R.string.exteraAppName);
        } catch (Exception e) {
            return "exteraGram";
        }
    }

    public static void parseMarkdownLinks(CharSequence[] message) {
        parseMarkdownLinks(message, null);
    }

    public static void parseMarkdownLinks(CharSequence[] message, Runnable onClick) {
        if (message == null || message.length == 0 || message[0] == null) {
            return;
        }
        Spannable spannable = message[0] instanceof Spannable ? (Spannable) message[0] : Spannable.Factory.getInstance().newSpannable(message[0].toString());
        Matcher matcher = MARKDOWN_LINK_PATTERN.matcher(spannable);
        ArrayList<String> sources = new ArrayList<>();
        ArrayList<CharSequence> destinations = new ArrayList<>();
        while (matcher.find()) {
            int textStart = matcher.start(1);
            int textEnd = matcher.end(1);
            if (textStart >= 0 && textEnd >= 0 && textStart <= textEnd && textEnd <= spannable.length()) {
                SpannableStringBuilder destination = new SpannableStringBuilder(spannable.subSequence(textStart, textEnd));
                var urlSpan = new URLSpanReplacement(ensureUrlHasHttps(matcher.group(2))) {
                    @Override
                    public void onClick(View widget) {
                        if (onClick != null) {
                            onClick.run();
                        }
                        AlertsCreator.showOpenUrlAlert(widget.getContext(), getURL(), true, true, true, false, 0, null, null);
                    }
                };
                destination.setSpan(urlSpan, 0, destination.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sources.add(matcher.group(0));
                destinations.add(destination);
            }
        }
        if (!sources.isEmpty()) {
            message[0] = TextUtils.replace(message[0],
                    sources.toArray(new String[0]),
                    destinations.toArray(new CharSequence[0])
            );
        }
    }

    public static CharSequence applyNewSpan(CharSequence str) {
        return applyBadge(str, "NEW", Theme.key_featuredStickers_addButton, Theme.key_featuredStickers_buttonText);
    }

    public static CharSequence applyBadge(CharSequence str, CharSequence badge, int backgroundColorKey, int textColorKey) {
        if (str == null) str = "";
        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(str);
        spannableStringBuilder.append("  d");
        FilterCreateActivity.NewSpan span = new FilterCreateActivity.NewSpan(10);
        span.setText(badge == null ? "" : badge.toString());
        span.setTypeface(AndroidUtilities.bold());
        span.setColor(Theme.getColor(backgroundColorKey));
        span.setTextColor(Theme.getColor(textColorKey));
        spannableStringBuilder.setSpan(span, spannableStringBuilder.length() - 1, spannableStringBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannableStringBuilder;
    }

    public static SpannableStringBuilder replaceArrows(Context context, CharSequence text, @DrawableRes int icon) {
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        int index = TextUtils.indexOf(builder, "->");
        while (index >= 0) {
            ColoredImageSpan span = new ColoredImageSpan(ContextCompat.getDrawable(context, icon).mutate(), ColoredImageSpan.ALIGN_CENTER);
            if (LocaleController.isRTL) {
                span.rotate(180);
            }
            builder.replace(index, index + 2, ">");
            builder.setSpan(span, index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            index = TextUtils.indexOf(builder, "->", index + 1);
        }
        return builder;
    }

    public static String ensureUrlHasHttps(String text) {
        if (text == null) return null;
        if (LinkifyPort.WEB_URL.matcher(text).matches()) {
            if (!text.startsWith("http://") && !text.startsWith("https://")) {
                if (!text.contains("://")) {
                    return AndroidUtilities.defaultUrlScheme(text) + text;
                }
            }
        }
        return text;
    }

    public static final String CUSTOM_EMOJI_PREFIX = "tg://emoji?id=";

    public static boolean parseCustomEmojis(CharSequence messageText, ArrayList<TLRPC.MessageEntity> entities) {
        if (messageText == null || entities == null || entities.isEmpty()) {
            return false;
        }

        boolean replaced = false;
        for (int i = 0; i < entities.size(); i++) {
            if (!(entities.get(i) instanceof TLRPC.TL_messageEntityTextUrl urlEntity)) {
                continue;
            }
            if (urlEntity.url == null || !urlEntity.url.startsWith(CUSTOM_EMOJI_PREFIX)) {
                continue;
            }
            if (urlEntity.offset < 0 || urlEntity.length <= 0 || urlEntity.offset + urlEntity.length > messageText.length()) {
                continue;
            }

            long docId;
            try {
                docId = Long.parseLong(urlEntity.url.substring(CUSTOM_EMOJI_PREFIX.length()));
            } catch (NumberFormatException e) {
                FileLog.e("Failed to parse custom emoji id: " + urlEntity.url, e);
                continue;
            }

            int[] emojiOnly = new int[1];
            var emojis = Emoji.parseEmojis(messageText.subSequence(urlEntity.offset, urlEntity.offset + urlEntity.length).toString(), emojiOnly);
            if (emojiOnly[0] <= 0 || emojis.size() != 1) {
                continue;
            }

            TLRPC.TL_messageEntityCustomEmoji customEmojiEntity = new TLRPC.TL_messageEntityCustomEmoji();
            customEmojiEntity.document_id = docId;
            customEmojiEntity.offset = urlEntity.offset;
            customEmojiEntity.length = urlEntity.length;
            customEmojiEntity.local = true;
            entities.set(i, customEmojiEntity);
            replaced = true;
        }

        return replaced;
    }

    public static boolean isCustomEmojiOnlyLinkMessage(TLRPC.Message message) {
        if (message == null || message.entities == null || message.entities.isEmpty()) {
            return false;
        }
        if (!MessageObject.isMediaEmptyWebpage(message)) {
            return false;
        }

        boolean hasEmojiLink = false;
        for (int i = 0; i < message.entities.size(); i++) {
            TLRPC.MessageEntity entity = message.entities.get(i);
            if (entity instanceof TLRPC.TL_messageEntityTextUrl urlEntity) {
                if (urlEntity.url == null || !urlEntity.url.startsWith(CUSTOM_EMOJI_PREFIX)) {
                    return false;
                }
                hasEmojiLink = true;
            } else if (entity instanceof TLRPC.TL_messageEntityCustomEmoji customEmoji) {
                if (customEmoji.local) {
                    hasEmojiLink = true;
                }
            } else if (entity instanceof TLRPC.TL_messageEntityUrl || entity instanceof TLRPC.TL_messageEntityEmail) {
                return false;
            }
        }
        return hasEmojiLink;
    }

    public static void replaceCustomEmojis(int currentAccount, long dialogId, ArrayList<TLRPC.MessageEntity> entities) {
        if (entities == null || entities.isEmpty() || !canUseLocalPremiumEmojis(currentAccount)) {
            return;
        }
        if (dialogId == UserConfig.getInstance(currentAccount).getClientUserId()) {
            return;
        }

        HashSet<Long> groupEmoji = null;
        if (dialogId < 0) {
            TLRPC.ChatFull chatInfo = MessagesController.getInstance(currentAccount).getChatFull(-dialogId);
            if (chatInfo != null && chatInfo.emojiset != null) {
                TLRPC.TL_messages_stickerSet stickerSet = MediaDataController.getInstance(currentAccount).getGroupStickerSetById(chatInfo.emojiset);
                if (stickerSet != null && stickerSet.documents != null) {
                    groupEmoji = new HashSet<>();
                    for (TLRPC.Document document : stickerSet.documents) {
                        groupEmoji.add(document.id);
                    }
                }
            }
        }

        for (int i = 0; i < entities.size(); i++) {
            if (!(entities.get(i) instanceof TLRPC.TL_messageEntityCustomEmoji emoji)) {
                continue;
            }
            if (groupEmoji != null && groupEmoji.contains(emoji.document_id)) {
                continue;
            }
            TLRPC.Document document = emoji.document != null ? emoji.document : AnimatedEmojiDrawable.findDocument(currentAccount, emoji.document_id);
            if (MessageObject.isFreeEmoji(document)) {
                continue;
            }
            entities.set(i, toCustomEmojiLink(emoji));
        }
    }

    public static void replaceLocalCustomEmojis(ArrayList<TLRPC.MessageEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return;
        }
        for (int i = 0; i < entities.size(); i++) {
            if (entities.get(i) instanceof TLRPC.TL_messageEntityCustomEmoji emoji && emoji.local) {
                entities.set(i, toCustomEmojiLink(emoji));
            }
        }
    }

    public static ArrayList<TLRPC.MessageEntity> swapLocalCustomEmojis(TLRPC.Message message) {
        if (message == null || message.entities == null) {
            return null;
        }
        for (int i = 0; i < message.entities.size(); i++) {
            if (message.entities.get(i) instanceof TLRPC.TL_messageEntityCustomEmoji emoji && emoji.local) {
                ArrayList<TLRPC.MessageEntity> original = message.entities;
                message.entities = new ArrayList<>(original);
                replaceLocalCustomEmojis(message.entities);
                return original;
            }
        }
        return null;
    }

    public static void restoreLocalCustomEmojis(TLRPC.Message message, ArrayList<TLRPC.MessageEntity> original) {
        if (message != null && original != null) {
            message.entities = original;
        }
    }

    private static TLRPC.TL_messageEntityTextUrl toCustomEmojiLink(TLRPC.TL_messageEntityCustomEmoji emoji) {
        TLRPC.TL_messageEntityTextUrl entity = new TLRPC.TL_messageEntityTextUrl();
        entity.offset = emoji.offset;
        entity.length = emoji.length;
        entity.url = CUSTOM_EMOJI_PREFIX + emoji.document_id;
        return entity;
    }

    public static boolean canUseLocalPremiumEmojis() {
        return canUseLocalPremiumEmojis(UserConfig.selectedAccount);
    }

    public static boolean canUseLocalPremiumEmojis(int account) {
        return RemoteUtils.getBooleanConfigValue(RemoteUtils.LOCAL_PREMIUM_EMOJIS, false) && !UserConfig.getInstance(account).isPremium();
    }

    public static Spannable createCopySpan(BaseFragment fragment) {
        SpannableString copyText = new SpannableString(" ");
        Drawable copyIcon = ContextCompat.getDrawable(fragment.getParentActivity(), R.drawable.msg_copy).mutate();
        copyIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_undo_cancelColor, fragment.getResourceProvider()), PorterDuff.Mode.SRC_IN));
        copyIcon.setBounds(0, 0, dp(22), dp(22));
        copyText.setSpan(new ImageSpan(copyIcon, ImageSpan.ALIGN_BOTTOM), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return copyText;
    }

    public static CharSequence insertHexColorsPreview(CharSequence cs) {
        if (TextUtils.isEmpty(cs) || !containsHash(cs))
            return cs;

        var spannable = (cs instanceof Spannable s) ? s : new SpannableString(cs);
        for (var colorRectSpan : spannable.getSpans(0, spannable.length(), ColorRectSpan.class)) {
            spannable.removeSpan(colorRectSpan);
        }

        var matcher = HEX_PATTERN.matcher(spannable);
        while (matcher.find()) {
            int end = matcher.end();
            int previewStart = end - 1;

            if (hasConflictingHexPreviewSpan(spannable, previewStart, end))
                continue;

            try {
                var color = Color.parseColor(matcher.group());
                spannable.setSpan(new ColorRectSpan(color), previewStart, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (IllegalArgumentException e) {
                FileLog.e("Invalid HEX color: " + matcher.group());
            }
        }

        return spannable;
    }

    private static boolean hasConflictingHexPreviewSpan(Spannable spannable, int start, int end) {
        for (var span : spannable.getSpans(start, end, Object.class)) {
            if (span instanceof ColorRectSpan) {
                continue;
            }
            int spanStart = spannable.getSpanStart(span),
                    spanEnd = spannable.getSpanEnd(span);
            if (spanStart >= end || spanEnd <= start) {
                continue;
            }
            if (span instanceof ReplacementSpan) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsHash(CharSequence cs) {
        for (int i = 0; i < cs.length(); i++) {
            if (cs.charAt(i) == '#') {
                return true;
            }
        }
        return false;
    }
}
