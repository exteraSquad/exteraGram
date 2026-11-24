/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.feed.ads;

import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Set;

public final class FeedAd {

    public static final int MATCH_ANY = 0;
    public static final int MATCH_REQUIRE = 1;
    public static final int MATCH_EXCLUDE = 2;

    public String id;
    public int weight = 1;

    public String title;
    public CharSequence bodyText;
    public ArrayList<TLRPC.MessageEntity> entities;
    public TLRPC.MessageMedia media;

    public String buttonText;
    public String url;
    public String sponsorInfo;
    public String additionalInfo;
    public int colorId = -1;
    public boolean recommended;

    int bodyMessageId;
    int mediaMessageId;

    public Set<String> locales;
    public int premium = MATCH_ANY;
    public int badge = MATCH_ANY;

    boolean isDisplayable() {
        return id != null && !id.isEmpty() && (bodyText != null || title != null || media != null);
    }
}
