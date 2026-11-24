/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.feed.ads;

import android.os.SystemClock;
import android.text.TextUtils;

import com.exteragram.messenger.badges.BadgesController;
import com.exteragram.messenger.utils.chats.ChatUtils;
import com.exteragram.messenger.utils.network.RemoteUtils;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class FeedAdController {

    private static final long AD_CHANNEL_ID = 3514621311L;
    private static final String AD_CHANNEL_USERNAME = "exteraFeedAds";
    private static final int HISTORY_LIMIT = 75;
    private static final long REFRESH_TTL_MS = 30 * 60 * 1000L;
    private static final long RETRY_TTL_MS = 60 * 1000L;

    private static final String KEY_ENABLED = "feed_ads_enabled";
    private static final String KEY_EVERY = "feed_ad_every";
    private static final String KEY_FIRST_AFTER = "feed_ad_first_after";
    private static final String KEY_MIN_TRAILING = "feed_ad_min_trailing";
    private static final String KEY_SPACING_POOL1 = "feed_ad_spacing_pool1";
    private static final String KEY_SPACING_POOL2 = "feed_ad_spacing_pool2";

    private static final FeedAdController[] instances = new FeedAdController[UserConfig.MAX_ACCOUNT_COUNT];
    private static final Object[] locks = new Object[UserConfig.MAX_ACCOUNT_COUNT];

    static {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            locks[i] = new Object();
        }
    }

    public static FeedAdController getInstance(int num) {
        FeedAdController local = instances[num];
        if (local == null) {
            synchronized (locks[num]) {
                local = instances[num];
                if (local == null) {
                    instances[num] = local = new FeedAdController(num);
                }
            }
        }
        return local;
    }

    public final int currentAccount;

    private final ArrayList<FeedAd> allAds = new ArrayList<>();
    private ArrayList<FeedAd> eligibleAds = new ArrayList<>();
    private final ArrayList<FeedAd> rotation = new ArrayList<>();
    private int rotationIndex;

    private long lastLoadTime;
    private boolean lastLoadFailed;
    private boolean loading;
    private final ArrayList<Runnable> pendingLoadCallbacks = new ArrayList<>();

    private FeedAdController(int currentAccount) {
        this.currentAccount = currentAccount;
    }

    public boolean isEnabled() {
        return RemoteUtils.getBooleanConfigValue(KEY_ENABLED, true) && !eligibleAds.isEmpty();
    }

    public int getFirstAfter() {
        return Math.max(1, RemoteUtils.getIntConfigValue(KEY_FIRST_AFTER, 30));
    }

    public int getMinTrailing() {
        return Math.max(0, RemoteUtils.getIntConfigValue(KEY_MIN_TRAILING, 2));
    }

    public int getBaseEvery() {
        return Math.max(1, RemoteUtils.getIntConfigValue(KEY_EVERY, 30));
    }

    public int getEffectiveEvery() {
        int every = getBaseEvery();
        int pool = eligibleAds.size();
        if (pool <= 1) {
            return every * Math.max(1, RemoteUtils.getIntConfigValue(KEY_SPACING_POOL1, 3));
        }
        if (pool == 2) {
            return every * Math.max(1, RemoteUtils.getIntConfigValue(KEY_SPACING_POOL2, 2));
        }
        return every;
    }

    public FeedAd nextAd() {
        if (eligibleAds.isEmpty()) {
            return null;
        }
        if (rotation.isEmpty() || rotationIndex >= rotation.size()) {
            reshuffleRotation();
        }
        return rotation.get(rotationIndex++);
    }

    private void reshuffleRotation() {
        rotation.clear();
        for (FeedAd ad : eligibleAds) {
            for (int i = 0; i < Math.max(1, ad.weight); i++) {
                rotation.add(ad);
            }
        }
        Collections.shuffle(rotation);
        rotationIndex = 0;
    }

    public void ensureLoaded(Runnable onLoaded) {
        if (!loading && lastLoadTime != 0
                && SystemClock.elapsedRealtime() - lastLoadTime < (lastLoadFailed ? RETRY_TTL_MS : REFRESH_TTL_MS)) {
            recomputeEligible();
            if (onLoaded != null) {
                onLoaded.run();
            }
            return;
        }
        if (onLoaded != null) {
            pendingLoadCallbacks.add(onLoaded);
        }
        if (loading) {
            return;
        }
        loading = true;
        fetchHistory((res, error) -> {
            loading = false;
            lastLoadTime = SystemClock.elapsedRealtime();
            lastLoadFailed = error != null || res == null;
            if (!lastLoadFailed) {
                ArrayList<FeedAd> parsed = FeedAdParser.parse(res);
                allAds.clear();
                allAds.addAll(parsed);
                recomputeEligible();
            }
            ArrayList<Runnable> callbacks = new ArrayList<>(pendingLoadCallbacks);
            pendingLoadCallbacks.clear();
            for (int i = 0; i < callbacks.size(); i++) {
                callbacks.get(i).run();
            }
        });
    }

    public void recomputeEligible() {
        ArrayList<FeedAd> eligible = new ArrayList<>(allAds.size());
        for (int i = 0; i < allAds.size(); i++) {
            if (isEligible(allAds.get(i))) {
                eligible.add(allAds.get(i));
            }
        }
        boolean same = eligible.size() == eligibleAds.size();
        for (int i = 0; same && i < eligible.size(); i++) {
            same = TextUtils.equals(eligible.get(i).id, eligibleAds.get(i).id) && eligible.get(i).weight == eligibleAds.get(i).weight;
        }
        eligibleAds = eligible;
        if (!same) {
            rotation.clear();
            rotationIndex = 0;
        } else if (!rotation.isEmpty()) {
            HashMap<String, FeedAd> byId = new HashMap<>();
            for (int i = 0; i < eligible.size(); i++) {
                byId.put(eligible.get(i).id, eligible.get(i));
            }
            for (int i = 0; i < rotation.size(); i++) {
                FeedAd fresh = byId.get(rotation.get(i).id);
                if (fresh != null) {
                    rotation.set(i, fresh);
                }
            }
        }
    }

    private boolean isEligible(FeedAd ad) {
        if (!matchesLocale(ad.locales)) {
            return false;
        }
        boolean premium = UserConfig.getInstance(currentAccount).isPremium();
        if (ad.premium == FeedAd.MATCH_REQUIRE && !premium || ad.premium == FeedAd.MATCH_EXCLUDE && premium) {
            return false;
        }
        boolean hasBadge = BadgesController.INSTANCE.hasBadge();
        if (ad.badge == FeedAd.MATCH_REQUIRE && !hasBadge || ad.badge == FeedAd.MATCH_EXCLUDE && hasBadge) {
            return false;
        }
        return true;
    }

    private boolean matchesLocale(Set<String> locales) {
        if (locales == null || locales.isEmpty()) {
            return true;
        }
        LocaleController.LocaleInfo info = LocaleController.getInstance().getCurrentLocaleInfo();
        if (info == null) {
            return true;
        }
        return contains(locales, info.getLangCode()) || contains(locales, info.shortName) || contains(locales, info.baseLangCode);
    }

    private static boolean contains(Set<String> locales, String code) {
        return code != null && locales.contains(code.toLowerCase(Locale.ROOT));
    }

    private void fetchHistory(org.telegram.messenger.Utilities.Callback2<TLRPC.messages_Messages, TLRPC.TL_error> callback) {
        AccountInstance account = AccountInstance.getInstance(currentAccount);
        TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
        req.peer = account.getMessagesController().getInputPeer(-AD_CHANNEL_ID);
        req.offset_id = 0;
        req.limit = HISTORY_LIMIT;

        Runnable sendReq = () -> account.getConnectionsManager().sendRequest(req, (resp, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null || !(resp instanceof TLRPC.messages_Messages)) {
                callback.run(null, error);
            } else {
                callback.run((TLRPC.messages_Messages) resp, null);
            }
        }));

        if (req.peer != null && req.peer.access_hash != 0L) {
            sendReq.run();
        } else {
            ChatUtils.getInstance(currentAccount).resolveChannel(AD_CHANNEL_USERNAME, chat -> {
                if (chat != null && chat.id == AD_CHANNEL_ID) {
                    TLRPC.TL_inputPeerChannel peer = new TLRPC.TL_inputPeerChannel();
                    peer.channel_id = chat.id;
                    peer.access_hash = chat.access_hash;
                    req.peer = peer;
                    sendReq.run();
                } else {
                    callback.run(null, null);
                }
            });
        }
    }

    public List<FeedAd> getEligibleAds() {
        return eligibleAds;
    }
}
