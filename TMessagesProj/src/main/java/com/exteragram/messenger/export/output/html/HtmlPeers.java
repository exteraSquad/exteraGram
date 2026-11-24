/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export.output.html;

import com.exteragram.messenger.export.api.ExportData;
import com.exteragram.messenger.export.api.ExportParser;

import java.util.List;
import java.util.Map;

final class HtmlPeers {

    private final Map<Long, ExportData.Peer> peers;

    HtmlPeers(Map<Long, ExportData.Peer> peers) {
        this.peers = peers;
    }

    ExportData.Peer peer(long peerId) {
        ExportData.Peer peer = peers.get(peerId);
        return peer != null ? peer : ExportParser.emptyPeer(peerId);
    }

    ExportData.User user(long userId) {
        ExportData.Peer peer = peers.get(userId);
        return peer != null && peer.user != null ? peer.user : ExportParser.emptyPeer(Math.abs(userId)).user;
    }

    String wrapPeerName(long peerId) {
        String name = peer(peerId).name();
        return name.isEmpty() ? "Deleted" : HtmlContext.serializeString(name);
    }

    String wrapUserName(long userId) {
        String name = user(userId).name();
        return name.isEmpty() ? "Deleted Account" : HtmlContext.serializeString(name);
    }

    String wrapUserNames(List<Long> userIds) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < userIds.size(); i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append(wrapUserName(userIds.get(i)));
        }
        return result.toString();
    }
}
