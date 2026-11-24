/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.chats;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;

import java.util.ArrayList;

public final class ChatSearchLocalFilter {

    private static final int PAGE_LIMIT = 100;
    private static final int MIN_MATCHES = 10;
    private static final int MAX_REQUESTS = 5;
    private static final int MAX_FLOOD_WAIT_SECONDS = 60;
    private static final long REQUEST_DELAY_MS = 300;

    private final int currentAccount;
    private volatile int token;
    private volatile int scanRequestId;
    private String key;
    private boolean decided;
    private boolean active;
    private boolean localSender;
    private long senderId;
    private TLRPC.MessagesFilter typeFilter;
    private final int[] rawOffsetId = new int[2];
    private final boolean[] rawEndReached = new boolean[2];
    private final int[] discarded = new int[2];

    private static final class Scan {
        final int token;
        final int sourceIndex;
        final boolean localSender;
        final long senderId;
        final TLRPC.MessagesFilter typeFilter;
        final RequestDelegate delegate;
        final int flags;
        final ArrayList<TLRPC.Message> messages = new ArrayList<>();
        final ArrayList<TLRPC.User> users = new ArrayList<>();
        final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
        int requests;
        int offsetId;
        int discarded;
        boolean endReached;

        Scan(int token, int sourceIndex, boolean localSender, long senderId, TLRPC.MessagesFilter typeFilter, RequestDelegate delegate, int flags) {
            this.token = token;
            this.sourceIndex = sourceIndex;
            this.localSender = localSender;
            this.senderId = senderId;
            this.typeFilter = typeFilter;
            this.delegate = delegate;
            this.flags = flags;
        }
    }

    public ChatSearchLocalFilter(int currentAccount) {
        this.currentAccount = currentAccount;
    }

    public boolean prepare(String query, long dialogId, long replyMessageId, TLRPC.User user, TLRPC.Chat chat, ReactionsLayoutInBubble.VisibleReaction reaction, TLRPC.MessagesFilter filter, Runnable onReady) {
        rawOffsetId[0] = rawOffsetId[1] = 0;
        rawEndReached[0] = rawEndReached[1] = false;
        discarded[0] = discarded[1] = 0;
        final long sender = user != null ? user.id : chat != null ? -chat.id : 0;
        if (sender == 0 || filter == null) {
            reset();
            return false;
        }
        final String newKey = dialogId + "_" + replyMessageId + "_" + sender + "_" + filter.getClass().getName() + "_" + query + "_" + (reaction != null ? reaction.emojicon + reaction.documentId : "");
        if (decided && newKey.equals(key)) {
            active = true;
            return false;
        }
        final MessagesController messagesController = MessagesController.getInstance(currentAccount);
        final TLRPC.InputPeer peer = messagesController.getInputPeer(dialogId);
        final TLRPC.InputPeer fromPeer = user != null ? MessagesController.getInputPeer(user) : MessagesController.getInputPeer(chat);
        if (peer == null || fromPeer == null) {
            reset();
            return false;
        }
        key = newKey;
        decided = false;
        active = false;
        senderId = sender;
        typeFilter = filter;
        final int requestToken = ++token;

        final TLRPC.TL_messages_search typeRequest = createCountRequest(peer, query, dialogId, replyMessageId, reaction);
        typeRequest.filter = filter;
        final TLRPC.TL_messages_search senderRequest = createCountRequest(peer, query, dialogId, replyMessageId, reaction);
        senderRequest.from_id = fromPeer;
        senderRequest.filter = new TLRPC.TL_inputMessagesFilterEmpty();

        final int[] counts = {-1, -1};
        final ConnectionsManager connectionsManager = ConnectionsManager.getInstance(currentAccount);
        connectionsManager.sendRequest(typeRequest, (response, error) -> AndroidUtilities.runOnUIThread(() -> onCount(requestToken, 0, response, counts, onReady)));
        connectionsManager.sendRequest(senderRequest, (response, error) -> AndroidUtilities.runOnUIThread(() -> onCount(requestToken, 1, response, counts, onReady)));
        return true;
    }

    public void applyTo(TLRPC.TL_messages_search req, boolean page) {
        if (!active) {
            return;
        }
        if (localSender) {
            req.from_id = null;
            req.flags &= ~1;
        } else {
            req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        }
        if (page) {
            req.limit = PAGE_LIMIT;
        }
    }

    public int sendRequest(int sourceIndex, TLRPC.TL_messages_search req, RequestDelegate delegate, int flags) {
        final ConnectionsManager connectionsManager = ConnectionsManager.getInstance(currentAccount);
        if (!active) {
            return connectionsManager.sendRequest(req, delegate, flags);
        }
        final Scan scan = new Scan(token, sourceIndex, localSender, senderId, typeFilter, delegate, flags);
        scan.offsetId = req.offset_id;
        return sendPage(scan, req);
    }

    public boolean isActive() {
        return active;
    }

    public boolean isEndReached(int sourceIndex, boolean endReached) {
        return active ? rawEndReached[sourceIndex] : endReached;
    }

    public int getOffsetId(int sourceIndex, int offsetId) {
        return active ? rawOffsetId[sourceIndex] : offsetId;
    }

    public void cancel() {
        token++;
        if (scanRequestId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(scanRequestId, true);
            scanRequestId = 0;
        }
    }

    public void reset() {
        cancel();
        key = null;
        decided = false;
        active = false;
    }

    private TLRPC.TL_messages_search createCountRequest(TLRPC.InputPeer peer, String query, long dialogId, long replyMessageId, ReactionsLayoutInBubble.VisibleReaction reaction) {
        final TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = peer;
        req.q = query;
        req.limit = 1;
        if (replyMessageId != 0) {
            final MessagesController messagesController = MessagesController.getInstance(currentAccount);
            if (dialogId == UserConfig.getInstance(currentAccount).getClientUserId() || messagesController.isMonoForum(dialogId)) {
                req.saved_peer_id = messagesController.getInputPeer(replyMessageId);
                req.flags |= 4;
            } else {
                req.top_msg_id = (int) replyMessageId;
                req.flags |= 2;
            }
        }
        if (reaction != null) {
            req.saved_reaction.add(reaction.toTLReaction());
            req.flags |= 8;
        }
        return req;
    }

    private void onCount(int requestToken, int index, TLObject response, int[] counts, Runnable onReady) {
        if (requestToken != token) {
            return;
        }
        counts[index] = response instanceof TLRPC.messages_Messages res ? getCount(res) : Integer.MAX_VALUE;
        if (counts[0] < 0 || counts[1] < 0) {
            return;
        }
        localSender = counts[0] <= counts[1];
        decided = true;
        active = true;
        onReady.run();
    }

    private int sendPage(Scan scan, TLRPC.TL_messages_search req) {
        scan.requests++;
        final int requestId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> onPage(scan, req, response, error), scan.flags);
        scanRequestId = requestId;
        return requestId;
    }

    private void onPage(Scan scan, TLRPC.TL_messages_search req, TLObject response, TLRPC.TL_error error) {
        if (scan.token != token) {
            return;
        }
        if (!(response instanceof TLRPC.messages_Messages res)) {
            final int floodWait = error != null && error.text != null && error.text.startsWith("FLOOD_WAIT_") ? Utilities.parseInt(error.text) : 0;
            if (floodWait > 0 && floodWait <= MAX_FLOOD_WAIT_SECONDS) {
                Utilities.stageQueue.postRunnable(() -> {
                    if (scan.token == token) {
                        sendPage(scan, req);
                    }
                }, floodWait * 1000L);
                return;
            }
            scanRequestId = 0;
            scan.delegate.run(response, error);
            return;
        }
        final int available = Math.min(res.messages.size(), req.limit - 1);
        int processed = 0;
        while (processed < available && scan.messages.size() < req.limit - 1) {
            final TLRPC.Message message = res.messages.get(processed++);
            if (scan.localSender ? MessageObject.getPeerId(message.from_id) == scan.senderId : matchesType(message, scan.typeFilter)) {
                scan.messages.add(message);
            } else {
                scan.discarded++;
            }
        }
        scan.users.addAll(res.users);
        scan.chats.addAll(res.chats);
        if (processed > 0) {
            scan.offsetId = res.messages.get(processed - 1).id;
        }
        scan.endReached = processed == available && res.messages.size() < req.limit;
        final int found = scan.messages.size();
        if (!scan.endReached && (found == 0 || found < MIN_MATCHES && scan.requests < MAX_REQUESTS)) {
            final TLRPC.TL_messages_search next = copy(req);
            next.offset_id = scan.offsetId;
            Utilities.stageQueue.postRunnable(() -> {
                if (scan.token == token) {
                    sendPage(scan, next);
                }
            }, REQUEST_DELAY_MS);
            return;
        }
        scanRequestId = 0;
        rawOffsetId[scan.sourceIndex] = scan.offsetId;
        rawEndReached[scan.sourceIndex] = scan.endReached;
        discarded[scan.sourceIndex] += scan.discarded;
        final TLRPC.TL_messages_messagesSlice result = new TLRPC.TL_messages_messagesSlice();
        result.messages = scan.messages;
        result.users = scan.users;
        result.chats = scan.chats;
        result.count = Math.max(getCount(res) - discarded[scan.sourceIndex], found);
        scan.delegate.run(result, null);
    }

    private static int getCount(TLRPC.messages_Messages res) {
        return res instanceof TLRPC.TL_messages_messagesSlice || res instanceof TLRPC.TL_messages_channelMessages ? res.count : res.messages.size();
    }

    private static TLRPC.TL_messages_search copy(TLRPC.TL_messages_search req) {
        final TLRPC.TL_messages_search copy = new TLRPC.TL_messages_search();
        copy.flags = req.flags;
        copy.peer = req.peer;
        copy.q = req.q;
        copy.from_id = req.from_id;
        copy.saved_peer_id = req.saved_peer_id;
        copy.saved_reaction = req.saved_reaction;
        copy.top_msg_id = req.top_msg_id;
        copy.filter = req.filter;
        copy.min_date = req.min_date;
        copy.max_date = req.max_date;
        copy.offset_id = req.offset_id;
        copy.add_offset = req.add_offset;
        copy.limit = req.limit;
        copy.max_id = req.max_id;
        copy.min_id = req.min_id;
        copy.hash = req.hash;
        return copy;
    }

    private static boolean matchesType(TLRPC.Message message, TLRPC.MessagesFilter filter) {
        final TLRPC.MessageMedia media = MessageObject.getMedia(message);
        final TLRPC.Document document = media != null ? media.document : null;
        if (filter instanceof TLRPC.TL_inputMessagesFilterPhotos) {
            return media instanceof TLRPC.TL_messageMediaPhoto;
        } else if (filter instanceof TLRPC.TL_inputMessagesFilterVideo) {
            return MessageObject.isVideoDocument(document);
        } else if (filter instanceof TLRPC.TL_inputMessagesFilterDocument) {
            return document != null && !MessageObject.isVideoDocument(document) && !MessageObject.isRoundVideoDocument(document) && !MessageObject.isVoiceDocument(document) && !MessageObject.isMusicDocument(document) && !MessageObject.isGifDocument(document) && !MessageObject.isStickerDocument(document) && !MessageObject.isAnimatedStickerDocument(document, true);
        } else if (filter instanceof TLRPC.TL_inputMessagesFilterUrl) {
            return media instanceof TLRPC.TL_messageMediaWebPage || hasUrlEntity(message);
        } else if (filter instanceof TLRPC.TL_inputMessagesFilterMusic) {
            return MessageObject.isMusicDocument(document);
        } else if (filter instanceof TLRPC.TL_inputMessagesFilterVoice) {
            return MessageObject.isVoiceDocument(document);
        } else if (filter instanceof TLRPC.TL_inputMessagesFilterRoundVideo) {
            return MessageObject.isRoundVideoDocument(document);
        } else if (filter instanceof TLRPC.TL_inputMessagesFilterGif) {
            return MessageObject.isGifDocument(document);
        } else if (filter instanceof TLRPC.TL_inputMessagesFilterPoll) {
            return media instanceof TLRPC.TL_messageMediaPoll;
        } else if (filter instanceof TLRPC.TL_inputMessagesFilterGeo) {
            return MessageObject.isLocationMessage(message);
        } else if (filter instanceof TLRPC.TL_inputMessagesFilterPinned) {
            return message.pinned;
        } else if (filter instanceof TLRPC.TL_inputMessagesFilterMyMentions) {
            return message.mentioned;
        }
        return true;
    }

    private static boolean hasUrlEntity(TLRPC.Message message) {
        if (message.entities == null) {
            return false;
        }
        for (int i = 0; i < message.entities.size(); i++) {
            final TLRPC.MessageEntity entity = message.entities.get(i);
            if (entity instanceof TLRPC.TL_messageEntityUrl || entity instanceof TLRPC.TL_messageEntityTextUrl) {
                return true;
            }
        }
        return false;
    }
}
