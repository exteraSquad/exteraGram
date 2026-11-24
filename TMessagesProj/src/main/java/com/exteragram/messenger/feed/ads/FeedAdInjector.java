/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.feed.ads;

import com.exteragram.messenger.feed.FeedChatIntegration;
import com.exteragram.messenger.feed.FeedMessageUtils;

import org.telegram.messenger.MessageObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public final class FeedAdInjector {

    private final int currentAccount;
    private final FeedChatIntegration.Host host;

    private final HashMap<MessageObject, MessageObject> adByAnchor = new HashMap<>();
    private final HashMap<MessageObject, Integer> slotOrdinalByAnchor = new HashMap<>();

    public FeedAdInjector(int currentAccount, FeedChatIntegration.Host host) {
        this.currentAccount = currentAccount;
        this.host = host;
    }

    public void clear() {
        adByAnchor.clear();
        slotOrdinalByAnchor.clear();
    }

    public void refresh(MessageObject unreadDivider) {
        if (!host.isListReady()) {
            return;
        }
        ArrayList<MessageObject> messages = host.getMessages();
        FeedAdController controller = FeedAdController.getInstance(currentAccount);
        boolean enabled = controller.isEnabled();
        boolean changed = false;

        for (MessageObject anchor : new ArrayList<>(adByAnchor.keySet())) {
            if (!enabled || !messages.contains(anchor)) {
                changed |= removeAd(messages, adByAnchor.remove(anchor));
                slotOrdinalByAnchor.remove(anchor);
            }
        }

        if (enabled) {
            changed |= revalidateKeptAds(messages, controller);
            for (Map.Entry<MessageObject, MessageObject> entry : adByAnchor.entrySet()) {
                MessageObject anchor = entry.getKey();
                MessageObject ad = entry.getValue();
                int anchorIndex = messages.indexOf(anchor);
                if (anchorIndex < 0) {
                    continue;
                }
                int current = messages.indexOf(ad);
                int target = anchorIndex + 1;
                if (current != target) {
                    if (current >= 0) {
                        messages.remove(current);
                        host.notifyMessageRemoved(current);
                        target = messages.indexOf(anchor) + 1;
                    }
                    messages.add(target, ad);
                    host.notifyMessageInserted(target);
                    changed = true;
                }
            }

            for (AnchorSlot slot : computeNewAnchors(messages, unreadDivider, controller)) {
                MessageObject anchor = slot.anchor;
                FeedAd next = controller.nextAd();
                if (next == null) {
                    break;
                }
                int anchorIndex = messages.indexOf(anchor);
                if (anchorIndex < 0) {
                    continue;
                }
                MessageObject ad = FeedAdFactory.createAdMessageObject(currentAccount, next);
                ad.stableId = host.nextStableId();
                adByAnchor.put(anchor, ad);
                slotOrdinalByAnchor.put(anchor, slot.ordinal);
                messages.add(anchorIndex + 1, ad);
                host.notifyMessageInserted(anchorIndex + 1);
                changed = true;
            }
        }

        if (changed) {
            host.onFeedListChanged();
            host.invalidateVisiblePart();
        }
    }

    private boolean revalidateKeptAds(ArrayList<MessageObject> messages, FeedAdController controller) {
        if (adByAnchor.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (MessageObject anchor : new ArrayList<>(adByAnchor.keySet())) {
            int anchorIndex = messages.indexOf(anchor);
            if (anchorIndex < 0) {
                continue;
            }
            int end = lastGroupMemberIndex(messages, anchorIndex);
            if (end != anchorIndex) {
                MessageObject ad = adByAnchor.remove(anchor);
                Integer slotOrdinal = slotOrdinalByAnchor.remove(anchor);
                MessageObject newAnchor = messages.get(end);
                if (adByAnchor.containsKey(newAnchor)) {
                    changed |= removeAd(messages, ad);
                } else {
                    adByAnchor.put(newAnchor, ad);
                    if (slotOrdinal != null) {
                        slotOrdinalByAnchor.put(newAnchor, slotOrdinal);
                    }
                }
            }
        }
        HashMap<MessageObject, Integer> ordinalByPost = new HashMap<>();
        int count = 0;
        for (int i = 0; i < messages.size(); i++) {
            if (FeedMessageUtils.isPostRow(messages.get(i))) {
                ordinalByPost.put(messages.get(i), count++);
            }
        }
        int baseEvery = controller.getBaseEvery();
        int minTrailing = controller.getMinTrailing();
        TreeMap<Integer, MessageObject> byOrdinal = new TreeMap<>();
        for (MessageObject anchor : adByAnchor.keySet()) {
            Integer ordinal = ordinalByPost.get(anchor);
            if (ordinal != null) {
                byOrdinal.put(ordinal, anchor);
            }
        }
        int previous = Integer.MIN_VALUE;
        for (Map.Entry<Integer, MessageObject> entry : byOrdinal.entrySet()) {
            int ordinal = entry.getKey();
            if (ordinal < minTrailing || previous != Integer.MIN_VALUE && ordinal - previous < baseEvery) {
                MessageObject anchor = entry.getValue();
                changed |= removeAd(messages, adByAnchor.remove(anchor));
                slotOrdinalByAnchor.remove(anchor);
            } else {
                previous = ordinal;
            }
        }
        return changed;
    }

    private boolean removeAd(ArrayList<MessageObject> messages, MessageObject ad) {
        if (ad == null) {
            return false;
        }
        int index = messages.indexOf(ad);
        if (index >= 0) {
            messages.remove(index);
            host.notifyMessageRemoved(index);
            return true;
        }
        return false;
    }

    private ArrayList<AnchorSlot> computeNewAnchors(ArrayList<MessageObject> messages, MessageObject unreadDivider, FeedAdController controller) {
        ArrayList<Integer> postIndices = new ArrayList<>();
        HashMap<MessageObject, Integer> ordinalByPost = new HashMap<>();
        for (int i = 0; i < messages.size(); i++) {
            if (FeedMessageUtils.isPostRow(messages.get(i))) {
                ordinalByPost.put(messages.get(i), postIndices.size());
                postIndices.add(i);
            }
        }
        ArrayList<AnchorSlot> anchors = new ArrayList<>();
        int total = postIndices.size();
        if (total == 0) {
            return anchors;
        }
        int firstAfter = controller.getFirstAfter();
        int every = controller.getEffectiveEvery();
        int minTrailing = controller.getMinTrailing();

        int readingStart = 0;
        int dividerIndex = unreadDivider != null ? messages.indexOf(unreadDivider) : -1;
        if (dividerIndex >= 0) {
            for (int idx : postIndices) {
                if (idx < dividerIndex) {
                    readingStart++;
                } else {
                    break;
                }
            }
        }

        TreeSet<Integer> candidates = new TreeSet<>();
        for (int x = readingStart - firstAfter; x >= 0; x -= every) {
            candidates.add(x);
        }
        for (int x = readingStart + firstAfter; x <= total - 1; x += every) {
            candidates.add(x);
        }

        ArrayList<Integer> pinnedOrdinals = new ArrayList<>();
        for (MessageObject anchor : adByAnchor.keySet()) {
            if (ordinalByPost.containsKey(anchor)) {
                Integer slotOrdinal = slotOrdinalByAnchor.get(anchor);
                pinnedOrdinals.add(slotOrdinal != null ? slotOrdinal : ordinalByPost.get(anchor));
            }
        }

        for (int candidate : candidates) {
            if (candidate < minTrailing) {
                continue;
            }
            int adjusted = lastGroupMemberIndex(messages, postIndices.get(candidate));
            MessageObject anchor = messages.get(adjusted);
            Integer ordinal = ordinalByPost.get(anchor);
            if (ordinal == null || adByAnchor.containsKey(anchor)) {
                continue;
            }
            boolean tooClose = false;
            for (int pinned : pinnedOrdinals) {
                if (Math.abs(candidate - pinned) < every) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) {
                continue;
            }
            anchors.add(new AnchorSlot(anchor, candidate));
            pinnedOrdinals.add(candidate);
        }
        return anchors;
    }

    private static int lastGroupMemberIndex(ArrayList<MessageObject> messages, int index) {
        MessageObject post = messages.get(index);
        long groupId = post.getGroupId();
        if (groupId == 0) {
            return index;
        }
        int last = index;
        for (int i = index + 1; i < messages.size(); i++) {
            MessageObject row = messages.get(i);
            if (!FeedMessageUtils.isPostRow(row) || row.getGroupId() != groupId) {
                break;
            }
            last = i;
        }
        return last;
    }

    private record AnchorSlot(MessageObject anchor, int ordinal) {
    }
}
