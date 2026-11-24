/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.feed.ads;

import android.text.TextUtils;

import org.telegram.messenger.FileLog;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class FeedAdParser {

    private static final String MANIFEST_PREFIX = "feed_ad";

    private FeedAdParser() {
    }

    static ArrayList<FeedAd> parse(TLRPC.messages_Messages res) {
        ArrayList<FeedAd> ads = new ArrayList<>();
        if (res == null || res.messages == null) {
            return ads;
        }
        HashSet<String> seenIds = new HashSet<>();
        for (TLRPC.Message msg : res.messages) {
            if (!(msg instanceof TLRPC.TL_message) || msg.message == null || !msg.message.startsWith(MANIFEST_PREFIX)) {
                continue;
            }
            FeedAd ad = parseManifest(msg.message);
            if (ad != null && seenIds.add(ad.id)) {
                ads.add(ad);
            }
        }
        if (ads.isEmpty()) {
            return ads;
        }
        for (TLRPC.Message msg : res.messages) {
            if (!(msg instanceof TLRPC.TL_message)) {
                continue;
            }
            for (int i = 0; i < ads.size(); i++) {
                FeedAd ad = ads.get(i);
                if (ad.bodyMessageId != 0 && msg.id == ad.bodyMessageId) {
                    ad.bodyText = msg.message;
                    ad.entities = msg.entities != null && !msg.entities.isEmpty() ? new ArrayList<>(msg.entities) : null;
                }
                if (ad.mediaMessageId != 0 && msg.id == ad.mediaMessageId && msg.media != null) {
                    ad.media = msg.media;
                }
            }
        }
        ArrayList<FeedAd> displayable = new ArrayList<>(ads.size());
        for (int i = 0; i < ads.size(); i++) {
            if (ads.get(i).isDisplayable()) {
                displayable.add(ads.get(i));
            }
        }
        return displayable;
    }

    private static FeedAd parseManifest(String manifest) {
        try {
            FeedAd ad = new FeedAd();
            for (String line : manifest.split("\n")) {
                int eq = line.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                if (value.isEmpty()) {
                    continue;
                }
                switch (key) {
                    case "id" -> ad.id = value;
                    case "weight" -> ad.weight = Math.max(1, parseInt(value, 1));
                    case "title" -> ad.title = value;
                    case "body" -> ad.bodyMessageId = parseInt(value, 0);
                    case "media" -> ad.mediaMessageId = parseInt(value, 0);
                    case "button" -> ad.buttonText = value;
                    case "url" -> ad.url = value;
                    case "sponsor_info" -> ad.sponsorInfo = value;
                    case "additional_info" -> ad.additionalInfo = value;
                    case "color" -> ad.colorId = parseInt(value, -1);
                    case "recommended" -> ad.recommended = Boolean.parseBoolean(value);
                    case "locale" -> ad.locales = parseLocales(value);
                    case "premium" -> ad.premium = parseMatch(value);
                    case "badge" -> ad.badge = parseMatch(value);
                    default -> {
                    }
                }
            }
            return ad.id != null && !ad.id.isEmpty() ? ad : null;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static Set<String> parseLocales(String value) {
        Set<String> set = new HashSet<>();
        for (String part : value.split(",")) {
            String code = part.trim().toLowerCase(Locale.ROOT);
            if (!code.isEmpty()) {
                set.add(code);
            }
        }
        return set.isEmpty() ? null : set;
    }

    private static int parseMatch(String value) {
        if (TextUtils.equals(value, "true") || TextUtils.equals(value, "has")) {
            return FeedAd.MATCH_REQUIRE;
        }
        if (TextUtils.equals(value, "false") || TextUtils.equals(value, "none")) {
            return FeedAd.MATCH_EXCLUDE;
        }
        return FeedAd.MATCH_ANY;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
