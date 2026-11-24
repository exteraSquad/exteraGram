/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export.output.json;

import android.util.Base64;

import com.exteragram.messenger.export.ExportFormat;
import com.exteragram.messenger.export.api.ExportData;
import com.exteragram.messenger.export.api.ExportParser;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class JsonMessageSerializer {

    private static final long NANOS_IN_UNIT = 1_000_000_000L;

    private final JsonContext context;
    private final ExportData.Message message;
    private final Map<Long, ExportData.Peer> peers;
    private final String internalLinksDomain;
    private final ArrayList<JsonContext.Item> values = new ArrayList<>();

    private JsonMessageSerializer(JsonContext context, ExportData.Message message, Map<Long, ExportData.Peer> peers, String internalLinksDomain) {
        this.context = context;
        this.message = message;
        this.peers = peers;
        this.internalLinksDomain = internalLinksDomain;
    }

    static String serialize(JsonContext context, ExportData.Message message, Map<Long, ExportData.Peer> peers, String internalLinksDomain) {
        if (message.media.content instanceof ExportData.UnsupportedMedia) {
            return context.serializeObject(
                    new JsonContext.Item("id", String.valueOf(message.id)),
                    new JsonContext.Item("type", JsonContext.serializeString("unsupported")));
        }
        return new JsonMessageSerializer(context, message, peers, internalLinksDomain).serialize();
    }

    private void pushBare(String key, String value) {
        if (value != null && !value.isEmpty()) {
            values.add(new JsonContext.Item(key, value));
        }
    }

    private void push(String key, String value) {
        if (value != null && !value.isEmpty()) {
            pushBare(key, JsonContext.serializeString(value));
        }
    }

    private void push(String key, long value) {
        pushBare(key, String.valueOf(value));
    }

    private void push(String key, boolean value) {
        pushBare(key, JsonContext.bool(value));
    }

    private void pushPeerId(String key, long peerId) {
        pushBare(key, wrapPeerId(peerId));
    }

    private String wrapPeerId(long peerId) {
        if (peerId >= 0) {
            return JsonContext.serializeString("user" + peerId);
        }
        ExportData.Peer peer = peers.get(peerId);
        boolean basicGroup = peer != null && peer.chat != null && !peer.chat.isChannel;
        return JsonContext.serializeString((basicGroup ? "chat" : "channel") + (-peerId));
    }

    private String wrapPeerName(long peerId) {
        ExportData.Peer peer = peers.get(peerId);
        return JsonContext.stringAllowNull(peer != null ? peer.name() : "");
    }

    private String wrapUserName(long userId) {
        ExportData.Peer peer = peers.get(userId);
        return JsonContext.stringAllowNull(peer != null && peer.user != null ? peer.user.name() : "");
    }

    private void pushFrom(String label) {
        if (message.fromId != 0) {
            pushBare(label, wrapPeerName(message.fromId));
            pushPeerId(label + "_id", message.fromId);
        }
    }

    private void pushActor() {
        pushFrom("actor");
    }

    private void pushAction(String action) {
        push("action", action);
    }

    private void pushReplyToMsgId(String label) {
        if (message.replyToMsgId != 0) {
            push(label, message.replyToMsgId);
            if (message.replyToPeerId != 0) {
                pushPeerId("reply_to_peer_id", message.replyToPeerId);
            }
        }
    }

    private void pushUserNames(List<Long> userIds, String label) {
        ArrayList<String> list = new ArrayList<>(userIds.size());
        for (long userId : userIds) {
            list.add(wrapUserName(userId));
        }
        pushBare(label, context.serializeArray(list));
    }

    private void pushTTL(String label) {
        if (message.media.ttl != 0) {
            push(label, message.media.ttl);
        }
    }

    private void pushPath(ExportData.File file, String label) {
        push(label, switch (file.skipReason) {
            case Unavailable -> "(File unavailable, please try again later)";
            case FileSize -> "(File exceeds maximum size. Change data exporting settings to download.)";
            case FileType -> "(File not included. Change data exporting settings to download.)";
            case None -> file.relativePath;
            case DateLimits -> "";
        });
    }

    private void pushStarsAmount(String prefix, TL_stars.StarsAmount amount) {
        if (amount == null) {
            return;
        }
        boolean ton = amount instanceof TL_stars.TL_starsTonAmount;
        push(prefix + "_whole", ton ? amount.amount / NANOS_IN_UNIT : amount.amount);
        push(prefix + "_nano", ton ? amount.amount % NANOS_IN_UNIT : amount.nanos);
        push(prefix + "_currency", ton ? "TON" : "Stars");
    }

    private void pushPhoto(ExportData.Image image) {
        pushPath(image.file, "photo");
        push("photo_file_size", image.file.size);
        if (image.width != 0 && image.height != 0) {
            push("width", image.width);
            push("height", image.height);
        }
    }

    private String serialize() {
        values.add(new JsonContext.Item("id", String.valueOf(message.id)));
        values.add(new JsonContext.Item("type", JsonContext.serializeString(message.action != null ? "service" : "message")));
        values.add(new JsonContext.Item("date", JsonContext.serializeDate(message.date)));
        values.add(new JsonContext.Item("date_unixtime", JsonContext.serializeDateRaw(message.date)));
        context.push(JsonContext.OBJECT);
        if (message.edited != 0) {
            pushBare("edited", JsonContext.serializeDate(message.edited));
            pushBare("edited_unixtime", JsonContext.serializeDateRaw(message.edited));
        }
        if (message.action != null) {
            serializeAction(message.action);
        } else {
            pushFrom("from");
            push("author", message.signature);
            if (message.forwardedFromId != 0) {
                pushBare("forwarded_from", wrapPeerName(message.forwardedFromId));
                pushPeerId("forwarded_from_id", message.forwardedFromId);
            } else if (!message.forwardedFromName.isEmpty()) {
                pushBare("forwarded_from", JsonContext.stringAllowNull(message.forwardedFromName));
            }
            if (message.savedFromChatId != 0) {
                pushBare("saved_from", wrapPeerName(message.savedFromChatId));
            }
            pushReplyToMsgId("reply_to_message_id");
            if (message.viaBotId != 0) {
                ExportData.Peer bot = peers.get(message.viaBotId);
                if (bot != null && bot.user != null && !bot.user.username.isEmpty()) {
                    push("via_bot", "@" + bot.user.username);
                }
            }
        }
        serializeMedia();
        pushBare("text", context.serializeText(message.text, false));
        pushBare("text_entities", context.serializeText(message.text, true));
        serializeInlineButtons();
        serializeReactions();
        context.pop();
        return context.serializeObject(values);
    }

    private void serializeAction(TLRPC.MessageAction action) {
        if (action instanceof TLRPC.TL_messageActionChatCreate) {
            pushActor();
            pushAction("create_group");
            push("title", action.title);
            pushUserNames(action.users, "members");
        } else if (action instanceof TLRPC.TL_messageActionChatEditTitle) {
            pushActor();
            pushAction("edit_group_title");
            push("title", action.title);
        } else if (action instanceof TLRPC.TL_messageActionChatEditPhoto) {
            pushActor();
            pushAction("edit_group_photo");
            if (message.actionPhoto != null) {
                pushPhoto(message.actionPhoto.image);
            }
        } else if (action instanceof TLRPC.TL_messageActionChatDeletePhoto) {
            pushActor();
            pushAction("delete_group_photo");
        } else if (action instanceof TLRPC.TL_messageActionChatAddUser) {
            pushActor();
            pushAction("invite_members");
            pushUserNames(action.users, "members");
        } else if (action instanceof TLRPC.TL_messageActionChatDeleteUser) {
            pushActor();
            pushAction("remove_members");
            pushUserNames(List.of(action.user_id), "members");
        } else if (action instanceof TLRPC.TL_messageActionChatJoinedByLink) {
            pushActor();
            pushAction("join_group_by_link");
            pushBare("inviter", wrapUserName(action.inviter_id));
        } else if (action instanceof TLRPC.TL_messageActionChannelCreate) {
            pushActor();
            pushAction("create_channel");
            push("title", action.title);
        } else if (action instanceof TLRPC.TL_messageActionChatMigrateTo) {
            pushActor();
            pushAction("migrate_to_supergroup");
        } else if (action instanceof TLRPC.TL_messageActionChannelMigrateFrom) {
            pushActor();
            pushAction("migrate_from_group");
            push("title", action.title);
        } else if (action instanceof TLRPC.TL_messageActionPinMessage) {
            pushActor();
            pushAction("pin_message");
            pushReplyToMsgId("message_id");
        } else if (action instanceof TLRPC.TL_messageActionHistoryClear) {
            pushActor();
            pushAction("clear_history");
        } else if (action instanceof TLRPC.TL_messageActionGameScore) {
            pushActor();
            pushAction("score_in_game");
            pushReplyToMsgId("game_message_id");
            push("score", action.score);
        } else if (action instanceof TLRPC.TL_messageActionPaymentSent) {
            pushAction("send_payment");
            push("amount", action.total_amount);
            push("currency", action.currency);
            pushReplyToMsgId("invoice_message_id");
            if (action.recurring_used) {
                push("recurring", "used");
            } else if (action.recurring_init) {
                push("recurring", "init");
            }
        } else if (action instanceof TLRPC.TL_messageActionPhoneCall) {
            pushActor();
            pushAction("phone_call");
            if (action.duration != 0) {
                push("duration_seconds", action.duration);
            }
            push("discard_reason", discardReason(action.reason));
        } else if (action instanceof TLRPC.TL_messageActionConferenceCall call) {
            pushActor();
            pushAction("conference_call");
            if (call.duration != 0) {
                push("duration_seconds", call.duration);
            }
            push("is_active", call.active);
            push("is_missed", call.missed);
        } else if (action instanceof TLRPC.TL_messageActionScreenshotTaken) {
            pushActor();
            pushAction("take_screenshot");
        } else if (action instanceof TLRPC.TL_messageActionCustomAction) {
            pushActor();
            push("information_text", action.message);
        } else if (action instanceof TLRPC.TL_messageActionBotAllowed allowed) {
            if (allowed.attach_menu) {
                pushAction("attach_menu_bot_allowed");
            } else if (allowed.from_request) {
                pushAction("web_app_bot_allowed");
            } else if (allowed.app != null) {
                pushAction("allow_sending_messages");
                push("reason_app_id", allowed.app.id);
                push("reason_app_name", allowed.app.title);
            } else {
                pushAction("allow_sending_messages");
                push("reason_domain", allowed.domain);
            }
        } else if (action instanceof TLRPC.TL_messageActionSecureValuesSent sent) {
            pushAction("send_passport_values");
            ArrayList<String> list = new ArrayList<>();
            for (TLRPC.SecureValueType type : sent.types) {
                list.add(JsonContext.serializeString(secureValueType(type)));
            }
            pushBare("values", context.serializeArray(list));
        } else if (action instanceof TLRPC.TL_messageActionContactSignUp) {
            pushActor();
            pushAction("joined_telegram");
        } else if (action instanceof TLRPC.TL_messageActionGeoProximityReached reached) {
            pushAction("proximity_reached");
            long fromId = reached.from_id != null ? MessageObject.getPeerId(reached.from_id) : 0;
            long toId = reached.to_id != null ? MessageObject.getPeerId(reached.to_id) : 0;
            if (fromId != 0) {
                pushBare("from", wrapPeerName(fromId));
                pushPeerId("from_id", fromId);
            }
            if (toId != 0) {
                pushBare("to", wrapPeerName(toId));
                pushPeerId("to_id", toId);
            }
            push("distance", reached.distance);
        } else if (action instanceof TLRPC.TL_messageActionGroupCall) {
            pushActor();
            pushAction("group_call");
            if (action.duration != 0) {
                push("duration", action.duration);
            }
        } else if (action instanceof TLRPC.TL_messageActionInviteToGroupCall) {
            pushActor();
            pushAction("invite_to_group_call");
            pushUserNames(action.users, "members");
        } else if (action instanceof TLRPC.TL_messageActionSetMessagesTTL ttl) {
            pushActor();
            pushAction("set_messages_ttl");
            push("period", ttl.period);
        } else if (action instanceof TLRPC.TL_messageActionGroupCallScheduled scheduled) {
            pushActor();
            pushAction("group_call_scheduled");
            push("schedule_date", scheduled.schedule_date);
        } else if (action instanceof TLRPC.TL_messageActionSetChatTheme theme) {
            pushActor();
            pushAction("edit_chat_theme");
            if (theme.theme instanceof TLRPC.TL_chatTheme chatTheme) {
                push("emoticon", chatTheme.emoticon);
            }
        } else if (action instanceof TLRPC.TL_messageActionChatJoinedByRequest) {
            pushActor();
            pushAction("join_group_by_request");
        } else if (action instanceof TLRPC.TL_messageActionWebViewDataSent sent) {
            pushAction("send_webview_data");
            push("text", sent.text);
        } else if (action instanceof TLRPC.TL_messageActionGiftPremium) {
            pushActor();
            pushAction("send_premium_gift");
            if (action.currency != null && action.amount != 0) {
                push("cost", ExportFormat.number(action.amount) + " " + action.currency);
            }
            if (action.days != 0) {
                push("days", action.days);
            } else if (action.months != 0) {
                push("months", action.months);
            }
        } else if (action instanceof TLRPC.TL_messageActionTopicCreate) {
            pushActor();
            pushAction("topic_created");
            push("title", action.title);
        } else if (action instanceof TLRPC.TL_messageActionTopicEdit edit) {
            pushActor();
            pushAction("topic_edit");
            push("new_title", edit.title);
            if (edit.icon_emoji_id != 0) {
                push("new_icon_emoji_id", edit.icon_emoji_id);
            }
        } else if (action instanceof TLRPC.TL_messageActionSuggestProfilePhoto) {
            pushActor();
            pushAction("suggest_profile_photo");
            if (message.actionPhoto != null) {
                pushPhoto(message.actionPhoto.image);
            }
        } else if (action instanceof TLRPC.TL_messageActionRequestedPeer requested) {
            pushActor();
            pushAction("requested_peer");
            push("button_id", requested.button_id);
            ArrayList<String> list = new ArrayList<>();
            for (TLRPC.Peer peer : requested.peers) {
                list.add(String.valueOf(MessageObject.getPeerId(peer)));
            }
            pushBare("peers", context.serializeArray(list));
        } else if (action instanceof TLRPC.TL_messageActionGiftCode code) {
            pushAction("gift_code_prize");
            push("gift_code", code.slug);
            if (code.boost_peer != null) {
                pushPeerId("boost_peer_id", MessageObject.getPeerId(code.boost_peer));
            }
            push("days", code.days);
            push("unclaimed", code.unclaimed);
            push("via_giveaway", code.via_giveaway);
        } else if (action instanceof TLRPC.TL_messageActionGiveawayLaunch) {
            pushAction("giveaway_launch");
        } else if (action instanceof TLRPC.TL_messageActionGiveawayResults results) {
            pushAction("giveaway_results");
            push("winners", results.winners_count);
            push("unclaimed", results.unclaimed_count);
            push("stars", results.stars);
        } else if (action instanceof TLRPC.TL_messageActionSetChatWallPaper wallPaper) {
            pushActor();
            pushAction(wallPaper.same ? "set_same_chat_wallpaper" : "set_chat_wallpaper");
            pushReplyToMsgId("message_id");
        } else if (action instanceof TLRPC.TL_messageActionBoostApply boost) {
            pushActor();
            pushAction("boost_apply");
            push("boosts", boost.boosts);
        } else if (action instanceof TLRPC.TL_messageActionPaymentRefunded refunded) {
            pushAction("refunded_payment");
            push("amount", refunded.total_amount);
            push("currency", refunded.currency);
            if (refunded.peer != null) {
                long peerId = MessageObject.getPeerId(refunded.peer);
                pushBare("peer_name", wrapPeerName(peerId));
                pushPeerId("peer_id", peerId);
            }
            if (refunded.charge != null) {
                push("charge_id", refunded.charge.id);
            }
        } else if (action instanceof TLRPC.TL_messageActionGiftStars gift) {
            pushActor();
            pushAction("send_stars_gift");
            if (gift.currency != null && gift.amount != 0) {
                push("cost", ExportFormat.number(gift.amount) + " " + gift.currency);
            }
            push("amount_whole", gift.stars);
            push("amount_nano", 0);
        } else if (action instanceof TLRPC.TL_messageActionPrizeStars prize) {
            pushActor();
            pushAction("stars_prize");
            if (prize.boost_peer != null) {
                long peerId = MessageObject.getPeerId(prize.boost_peer);
                pushPeerId("boost_peer_id", peerId);
                pushBare("boost_peer_name", wrapPeerName(peerId));
            }
            push("stars", prize.stars);
            push("is_unclaimed", prize.unclaimed);
            push("giveaway_msg_id", prize.giveaway_msg_id);
            push("transaction_id", prize.transaction_id);
        } else if (action instanceof TLRPC.TL_messageActionStarGift gift) {
            pushActor();
            pushAction("send_star_gift");
            if (gift.gift != null) {
                push("gift_id", gift.gift.id);
                push("stars", gift.gift.stars);
                push("is_limited", gift.gift.availability_total > 0);
            }
            push("is_anonymous", gift.name_hidden);
            if (gift.message != null) {
                pushBare("gift_text", context.serializeText(ExportParser.parseText(gift.message.text, gift.message.entities), false));
            }
        } else if (action instanceof TLRPC.TL_messageActionPaidMessagesRefunded refunded) {
            pushActor();
            pushAction("paid_messages_refund");
            push("messages_count", refunded.count);
            push("stars_count", refunded.stars);
        } else if (action instanceof TLRPC.TL_messageActionPaidMessagesPrice price) {
            pushActor();
            pushAction("paid_messages_price_change");
            push("price_stars", price.stars);
            push("is_broadcast_messages_allowed", (price.flags & 1) != 0);
        } else if (action instanceof TLRPC.TL_messageActionSuggestBirthday suggest && suggest.birthday != null) {
            pushActor();
            pushAction("suggest_birthday");
            push("day", suggest.birthday.day);
            push("month", suggest.birthday.month);
            if (suggest.birthday.year != 0) {
                push("year", suggest.birthday.year);
            }
        } else if (action instanceof TLRPC.TL_messageActionChatJoinedViaCommunity joined) {
            pushActor();
            pushAction("join_group_via_community");
            push("community_id", joined.community_id);
        } else if (action instanceof TLRPC.TL_messageActionChangeCommunity changed) {
            pushActor();
            pushAction("change_community");
            push("community_id", changed.community_id);
        } else if (action instanceof TLRPC.TL_messageActionPhoneNumberRequest) {
            pushActor();
            pushAction("requested_phone_number");
        } else if (action instanceof TLRPC.TL_messageActionSetSameChatWallPaper) {
            pushActor();
            pushAction("set_same_chat_wallpaper");
            pushReplyToMsgId("message_id");
        } else if (action instanceof TLRPC.TL_messageActionGiftTon gift) {
            pushActor();
            pushAction("send_ton_gift");
            if (gift.currency != null && gift.amount != 0) {
                push("cost", ExportFormat.number(gift.amount) + " " + gift.currency);
            }
            push("amount_whole", gift.cryptoAmount / NANOS_IN_UNIT);
            push("amount_nano", gift.cryptoAmount % NANOS_IN_UNIT);
            push("transaction_id", gift.transaction_id);
        } else if (action instanceof TLRPC.TL_messageActionGramTransfer transfer) {
            pushActor();
            pushAction("gram_transfer");
            push("amount_whole", transfer.amount / NANOS_IN_UNIT);
            push("amount_nano", transfer.amount % NANOS_IN_UNIT);
            push("peer_address", transfer.peer_address);
            push("transaction_id", transfer.transaction_id);
            push("comment_encrypted", transfer.comment_encrypted);
            push(transfer.comment_encrypted ? "encrypted_comment_base64" : "comment", transfer.comment);
        } else if (action instanceof TLRPC.TL_messageActionStarGiftUnique gift) {
            pushActor();
            pushAction("send_star_gift");
            push("is_unique", true);
            if (gift.gift != null) {
                push("gift_id", gift.gift.id);
            }
            push("is_upgrade", gift.upgrade);
            push("is_transferred", gift.transferred);
            push("is_anonymous", gift.name_hidden);
            if (gift.message != null) {
                pushBare("gift_text", context.serializeText(ExportParser.parseText(gift.message), false));
            }
        } else if (action instanceof TLRPC.TL_messageActionStarGiftPurchaseOffer offer) {
            pushActor();
            pushAction("star_gift_purchase_offer");
            if (offer.gift != null) {
                push("gift_id", offer.gift.id);
            }
            pushStarsAmount("price_amount", offer.price);
            push("accepted", offer.accepted);
            push("declined", offer.declined);
            if (offer.expires_at != 0) {
                pushBare("expires", JsonContext.serializeDate(offer.expires_at));
            }
        } else if (action instanceof TLRPC.TL_messageActionStarGiftPurchaseOfferDeclined offer) {
            pushActor();
            pushAction("star_gift_purchase_offer_declined");
            if (offer.gift != null) {
                push("gift_id", offer.gift.id);
            }
            pushStarsAmount("price_amount", offer.price);
            push("expired", offer.expired);
        } else if (action instanceof TLRPC.TL_messageActionTodoCompletions completions) {
            pushActor();
            pushAction("todo_completions");
            pushBare("completed", integerArray(completions.completed));
            pushBare("incompleted", integerArray(completions.incompleted));
        } else if (action instanceof TLRPC.TL_messageActionTodoAppendTasks append) {
            pushActor();
            pushAction("todo_append_tasks");
            context.push(JsonContext.ARRAY);
            ArrayList<String> items = new ArrayList<>();
            for (TLRPC.TodoItem item : append.list) {
                items.add(context.serializeObject(
                        new JsonContext.Item("text", context.serializeText(ExportParser.parseText(item.title), false)),
                        new JsonContext.Item("id", String.valueOf(item.id))));
            }
            context.pop();
            pushBare("items", context.serializeArray(items));
        } else if (action instanceof TLRPC.TL_messageActionPollAppendAnswer append) {
            pushActor();
            pushAction("poll_append_answer");
            push("option", pollAnswerText(append.answer));
        } else if (action instanceof TLRPC.TL_messageActionPollDeleteAnswer delete) {
            pushActor();
            pushAction("poll_delete_answer");
            push("option", pollAnswerText(delete.answer));
        } else if (action instanceof TLRPC.TL_messageActionSuggestedPostApproval approval) {
            pushActor();
            pushAction("process_suggested_post");
            if (approval.rejected) {
                pushBare("rejected", "true");
                push("comment", approval.reject_comment);
            } else {
                pushStarsAmount("price_amount", approval.price);
                if (approval.schedule_date != 0) {
                    pushBare("scheduled_date", JsonContext.serializeDate(approval.schedule_date));
                }
            }
        } else if (action instanceof TLRPC.TL_messageActionSuggestedPostSuccess success) {
            pushActor();
            pushAction("suggested_post_success");
            pushStarsAmount("price_amount", success.price);
        } else if (action instanceof TLRPC.TL_messageActionSuggestedPostRefund refund) {
            pushActor();
            pushAction("suggested_post_refund");
            push("user_initiated", refund.payer_initiated);
        } else if (action instanceof TLRPC.TL_messageActionNoForwardsToggle toggle) {
            pushActor();
            pushAction("no_forwards_toggle");
            push("new_value", toggle.new_value);
        } else if (action instanceof TLRPC.TL_messageActionNoForwardsRequest request) {
            pushActor();
            pushAction("no_forwards_request");
            push("expired", request.expired);
            push("new_value", request.new_value);
        } else if (action instanceof TLRPC.TL_messageActionNewCreatorPending pending) {
            pushActor();
            pushAction("new_creator_pending");
            pushBare("new_creator", wrapUserName(pending.new_creator_id));
        } else if (action instanceof TLRPC.TL_messageActionChangeCreator change) {
            pushActor();
            pushAction("change_creator");
            pushBare("new_creator", wrapUserName(change.new_creator_id));
        } else if (action instanceof TLRPC.TL_messageActionManagedBotCreated created) {
            pushActor();
            pushAction("managed_bot_created");
            pushBare("bot", wrapUserName(created.bot_id));
        } else if (action instanceof TLRPC.TL_messageActionWalletTonConnectRequest request) {
            pushActor();
            pushAction("ton_connect_request");
            push("session_id", request.session_id);
            push("expires", request.expires);
            push("topic", request.topic);
            push("trace_id", request.trace_id);
            push("accepted", request.accepted);
            push("declined", request.declined);
        } else if (action instanceof TLRPC.TL_messageActionNoPaidMessagesTokenUsed) {
            pushActor();
            pushAction("no_paid_messages_token_used");
        } else if (action instanceof TLRPC.TL_messageActionAttachMenuBotAllowed) {
            pushAction("attach_menu_bot_allowed");
        } else if (!(action instanceof TLRPC.TL_messageActionEmpty)) {
            pushActor();
            pushAction("unsupported");
        }
    }

    private static String pollAnswerText(TLRPC.PollAnswer answer) {
        return answer != null && answer.text != null && answer.text.text != null ? answer.text.text : "";
    }

    private static String integerArray(List<Integer> values) {
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                result.append(',');
            }
            result.append(values.get(i));
        }
        return result.append(']').toString();
    }

    private static String discardReason(TLRPC.PhoneCallDiscardReason reason) {
        if (reason instanceof TLRPC.TL_phoneCallDiscardReasonBusy) {
            return "busy";
        } else if (reason instanceof TLRPC.TL_phoneCallDiscardReasonDisconnect) {
            return "disconnect";
        } else if (reason instanceof TLRPC.TL_phoneCallDiscardReasonHangup) {
            return "hangup";
        } else if (reason instanceof TLRPC.TL_phoneCallDiscardReasonMissed) {
            return "missed";
        }
        return "";
    }

    private static String secureValueType(TLRPC.SecureValueType type) {
        if (type instanceof TLRPC.TL_secureValueTypePersonalDetails) {
            return "personal_details";
        } else if (type instanceof TLRPC.TL_secureValueTypePassport) {
            return "passport";
        } else if (type instanceof TLRPC.TL_secureValueTypeDriverLicense) {
            return "driver_license";
        } else if (type instanceof TLRPC.TL_secureValueTypeIdentityCard) {
            return "identity_card";
        } else if (type instanceof TLRPC.TL_secureValueTypeInternalPassport) {
            return "internal_passport";
        } else if (type instanceof TLRPC.TL_secureValueTypeAddress) {
            return "address_information";
        } else if (type instanceof TLRPC.TL_secureValueTypeUtilityBill) {
            return "utility_bill";
        } else if (type instanceof TLRPC.TL_secureValueTypeBankStatement) {
            return "bank_statement";
        } else if (type instanceof TLRPC.TL_secureValueTypeRentalAgreement) {
            return "rental_agreement";
        } else if (type instanceof TLRPC.TL_secureValueTypePassportRegistration) {
            return "passport_registration";
        } else if (type instanceof TLRPC.TL_secureValueTypeTemporaryRegistration) {
            return "temporary_registration";
        } else if (type instanceof TLRPC.TL_secureValueTypePhone) {
            return "phone_number";
        } else if (type instanceof TLRPC.TL_secureValueTypeEmail) {
            return "email";
        }
        return "";
    }

    private void serializeMedia() {
        Object content = message.media.content;
        if (content instanceof ExportData.Photo photo) {
            pushPhoto(photo.image);
            if (photo.spoilered) {
                push("media_spoiler", true);
            }
            pushTTL("self_destruct_period_seconds");
        } else if (content instanceof ExportData.Document document) {
            pushPath(document.file, "file");
            push("file_name", document.name);
            push("file_size", document.file.size);
            if (document.thumb.width > 0) {
                pushPath(document.thumb.file, "thumbnail");
                push("thumbnail_file_size", document.thumb.file.size);
            }
            if (document.isSticker) {
                push("media_type", "sticker");
                push("sticker_emoji", document.stickerEmoji);
            } else if (document.isVideoMessage) {
                push("media_type", "video_message");
            } else if (document.isVoiceMessage) {
                push("media_type", "voice_message");
            } else if (document.isAnimated) {
                push("media_type", "animation");
            } else if (document.isVideoFile) {
                push("media_type", "video_file");
            } else if (document.isAudioFile) {
                push("media_type", "audio_file");
                push("performer", document.songPerformer);
                push("title", document.songTitle);
            }
            push("mime_type", document.mime);
            if (document.duration != 0) {
                push("duration_seconds", document.duration);
            }
            if (document.width != 0 && document.height != 0) {
                push("width", document.width);
                push("height", document.height);
            }
            if (document.spoilered) {
                push("media_spoiler", true);
            }
            pushTTL("self_destruct_period_seconds");
        } else if (content instanceof ExportData.SharedContact contact) {
            pushBare("contact_information", context.serializeObject(
                    new JsonContext.Item("first_name", JsonContext.serializeString(contact.info.firstName)),
                    new JsonContext.Item("last_name", JsonContext.serializeString(contact.info.lastName)),
                    new JsonContext.Item("phone_number", JsonContext.serializeString(ExportFormat.phoneNumber(contact.info.phoneNumber)))));
            if (contact.vcard.hasContent()) {
                pushPath(contact.vcard, "contact_vcard");
                push("contact_vcard_file_size", contact.vcard.size);
            }
        } else if (content instanceof ExportData.GeoPoint point) {
            pushBare("location_information", point.valid
                    ? context.serializeObject(
                    new JsonContext.Item("latitude", JsonContext.number(point.latitude)),
                    new JsonContext.Item("longitude", JsonContext.number(point.longitude)))
                    : "null");
            pushTTL("live_location_period_seconds");
        } else if (content instanceof ExportData.Venue venue) {
            push("place_name", venue.title);
            push("address", venue.address);
            if (venue.point.valid) {
                pushBare("location_information", context.serializeObject(
                        new JsonContext.Item("latitude", JsonContext.number(venue.point.latitude)),
                        new JsonContext.Item("longitude", JsonContext.number(venue.point.longitude))));
            }
        } else if (content instanceof ExportData.Game game) {
            push("game_title", game.title);
            push("game_description", game.description);
            if (game.botId != 0 && !game.shortName.isEmpty()) {
                ExportData.Peer bot = peers.get(game.botId);
                if (bot != null && bot.user != null && bot.user.isBot && !bot.user.username.isEmpty()) {
                    push("game_link", internalLinksDomain + bot.user.username + "?game=" + game.shortName);
                }
            }
        } else if (content instanceof ExportData.Invoice invoice) {
            pushBare("invoice_information", context.serializeObject(
                    new JsonContext.Item("title", JsonContext.serializeString(invoice.title)),
                    new JsonContext.Item("description", JsonContext.serializeString(invoice.description)),
                    new JsonContext.Item("amount", String.valueOf(invoice.amount)),
                    new JsonContext.Item("currency", JsonContext.serializeString(invoice.currency)),
                    new JsonContext.Item("receipt_message_id", invoice.receiptMsgId != 0 ? String.valueOf(invoice.receiptMsgId) : "")));
        } else if (content instanceof ExportData.Poll poll) {
            context.push(JsonContext.OBJECT);
            context.push(JsonContext.ARRAY);
            ArrayList<String> answers = new ArrayList<>(poll.answers.size());
            for (ExportData.Poll.Answer answer : poll.answers) {
                answers.add(context.serializeObject(
                        new JsonContext.Item("text", context.serializeText(answer.text(), false)),
                        new JsonContext.Item("voters", String.valueOf(answer.votes())),
                        new JsonContext.Item("chosen", JsonContext.bool(answer.my()))));
            }
            context.pop();
            String serializedAnswers = context.serializeArray(answers);
            context.pop();
            pushBare("poll", context.serializeObject(
                    new JsonContext.Item("question", context.serializeText(poll.question, false)),
                    new JsonContext.Item("closed", JsonContext.bool(poll.closed)),
                    new JsonContext.Item("total_voters", String.valueOf(poll.totalVotes)),
                    new JsonContext.Item("answers", serializedAnswers)));
        } else if (content instanceof ExportData.TodoList todo) {
            context.push(JsonContext.OBJECT);
            context.push(JsonContext.ARRAY);
            ArrayList<String> items = new ArrayList<>(todo.items.size());
            for (ExportData.TodoList.Item item : todo.items) {
                ExportData.TodoList.Completion completion = todo.completions.get(item.id());
                ArrayList<JsonContext.Item> fields = new ArrayList<>();
                fields.add(new JsonContext.Item("text", context.serializeText(item.text(), false)));
                fields.add(new JsonContext.Item("id", String.valueOf(item.id())));
                if (completion != null) {
                    if (completion.peerId() != 0) {
                        fields.add(new JsonContext.Item("completed_by", wrapPeerName(completion.peerId())));
                        fields.add(new JsonContext.Item("completed_by_id", wrapPeerId(completion.peerId())));
                    }
                    fields.add(new JsonContext.Item("completed_date", JsonContext.serializeDate(completion.date())));
                }
                items.add(context.serializeObject(fields));
            }
            context.pop();
            String serializedItems = context.serializeArray(items);
            context.pop();
            pushBare("todo_list", context.serializeObject(
                    new JsonContext.Item("title", context.serializeText(todo.title, false)),
                    new JsonContext.Item("others_can_append", JsonContext.bool(todo.othersCanAppend)),
                    new JsonContext.Item("others_can_complete", JsonContext.bool(todo.othersCanComplete)),
                    new JsonContext.Item("answers", serializedItems)));
        } else if (content instanceof ExportData.Dice dice) {
            push("dice_emoticon", dice.emoticon);
            push("dice_value", dice.value);
        } else if (content instanceof ExportData.StoryReference story) {
            push("story_id", story.storyId);
            if (story.peerId != 0) {
                pushBare("story_from", wrapPeerName(story.peerId));
                pushPeerId("story_from_id", story.peerId);
            }
        } else if (content instanceof ExportData.GiveawayStart giveaway) {
            context.push(JsonContext.ARRAY);
            ArrayList<String> channels = new ArrayList<>();
            for (long channel : giveaway.channels) {
                channels.add(String.valueOf(channel));
            }
            String serializedChannels = context.serializeArray(channels);
            ArrayList<String> countries = new ArrayList<>();
            for (String country : giveaway.countries) {
                countries.add(JsonContext.serializeString(country));
            }
            String serializedCountries = context.serializeArray(countries);
            context.pop();
            pushBare("giveaway_information", context.serializeObject(
                    new JsonContext.Item("quantity", String.valueOf(giveaway.quantity)),
                    new JsonContext.Item("months", String.valueOf(giveaway.months)),
                    new JsonContext.Item("until_date", JsonContext.serializeDate(giveaway.untilDate)),
                    new JsonContext.Item("channels", serializedChannels),
                    new JsonContext.Item("countries", serializedCountries),
                    new JsonContext.Item("additional_prize", JsonContext.serializeString(giveaway.additionalPrize)),
                    new JsonContext.Item("stars", String.valueOf(giveaway.credits)),
                    new JsonContext.Item("is_only_new_subscribers", JsonContext.bool(!giveaway.all))));
        } else if (content instanceof ExportData.GiveawayResults results) {
            context.push(JsonContext.ARRAY);
            ArrayList<String> winners = new ArrayList<>();
            for (long winner : results.winners) {
                winners.add(String.valueOf(winner));
            }
            String serializedWinners = context.serializeArray(winners);
            context.pop();
            pushBare("giveaway_results", context.serializeObject(
                    new JsonContext.Item("channel", String.valueOf(results.channel)),
                    new JsonContext.Item("winners", serializedWinners),
                    new JsonContext.Item("additional_prize", JsonContext.serializeString(results.additionalPrize)),
                    new JsonContext.Item("until_date", JsonContext.serializeDate(results.untilDate)),
                    new JsonContext.Item("launch_message_id", String.valueOf(results.launchId)),
                    new JsonContext.Item("additional_peers_count", String.valueOf(results.additionalPeersCount)),
                    new JsonContext.Item("winners_count", String.valueOf(results.winnersCount)),
                    new JsonContext.Item("unclaimed_count", String.valueOf(results.unclaimedCount)),
                    new JsonContext.Item("months", String.valueOf(results.months)),
                    new JsonContext.Item("stars", String.valueOf(results.credits)),
                    new JsonContext.Item("is_refunded", JsonContext.bool(results.refunded)),
                    new JsonContext.Item("is_only_new_subscribers", JsonContext.bool(!results.all))));
        } else if (content instanceof ExportData.PaidMedia paid) {
            push("paid_stars_amount", paid.stars);
        }
    }

    private void serializeInlineButtons() {
        if (message.inlineButtonRows.isEmpty()) {
            return;
        }
        context.push(JsonContext.ARRAY);
        ArrayList<String> rows = new ArrayList<>(message.inlineButtonRows.size());
        for (ArrayList<ExportData.InlineButton> row : message.inlineButtonRows) {
            context.push(JsonContext.ARRAY);
            ArrayList<String> buttons = new ArrayList<>(row.size());
            for (ExportData.InlineButton button : row) {
                ArrayList<JsonContext.Item> items = new ArrayList<>();
                items.add(new JsonContext.Item("type", JsonContext.serializeString(button.typeString())));
                if (!button.text.isEmpty()) {
                    items.add(new JsonContext.Item("text", JsonContext.serializeString(button.text)));
                }
                if (button.data != null && button.data.length > 0) {
                    boolean isCallback = button.type == ExportData.InlineButton.Type.Callback
                            || button.type == ExportData.InlineButton.Type.CallbackWithPassword;
                    if (isCallback) {
                        String encoded = Base64.encodeToString(button.data, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
                        items.add(new JsonContext.Item("dataBase64", JsonContext.serializeString(encoded)));
                        items.add(new JsonContext.Item("data", JsonContext.serializeString("")));
                    } else {
                        items.add(new JsonContext.Item("data", JsonContext.serializeString(new String(button.data, StandardCharsets.UTF_8))));
                    }
                }
                if (!button.forwardText.isEmpty()) {
                    items.add(new JsonContext.Item("forward_text", JsonContext.serializeString(button.forwardText)));
                }
                if (button.buttonId != 0) {
                    items.add(new JsonContext.Item("button_id", String.valueOf(button.buttonId)));
                }
                buttons.add(context.serializeObject(items));
            }
            context.pop();
            rows.add(context.serializeArray(buttons));
        }
        context.pop();
        pushBare("inline_bot_buttons", context.serializeArray(rows));
    }

    private void serializeReactions() {
        if (message.reactions.isEmpty()) {
            return;
        }
        context.push(JsonContext.ARRAY);
        ArrayList<String> reactions = new ArrayList<>(message.reactions.size());
        for (ExportData.Reaction reaction : message.reactions) {
            context.push(JsonContext.OBJECT);
            ArrayList<JsonContext.Item> items = new ArrayList<>();
            items.add(new JsonContext.Item("type", JsonContext.serializeString(reaction.typeString())));
            items.add(new JsonContext.Item("count", String.valueOf(reaction.count)));
            if (reaction.type == ExportData.Reaction.Type.Emoji) {
                items.add(new JsonContext.Item("emoji", JsonContext.serializeString(reaction.emoji)));
            } else if (reaction.type == ExportData.Reaction.Type.CustomEmoji) {
                items.add(new JsonContext.Item("document_id", JsonContext.serializeString(reaction.documentId)));
            }
            if (!reaction.recent.isEmpty()) {
                context.push(JsonContext.ARRAY);
                ArrayList<String> recents = new ArrayList<>(reaction.recent.size());
                for (ExportData.Reaction.Recent recent : reaction.recent) {
                    context.push(JsonContext.ARRAY);
                    recents.add(context.serializeObject(
                            new JsonContext.Item("from", wrapPeerName(recent.peerId())),
                            new JsonContext.Item("from_id", wrapPeerId(recent.peerId())),
                            new JsonContext.Item("date", JsonContext.serializeDate(recent.date()))));
                    context.pop();
                }
                items.add(new JsonContext.Item("recent", context.serializeArray(recents)));
                context.pop();
            }
            reactions.add(context.serializeObject(items));
            context.pop();
        }
        pushBare("reactions", context.serializeArray(reactions));
        context.pop();
    }
}
