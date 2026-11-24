/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export.output.html;

import com.exteragram.messenger.export.ExportFormat;
import com.exteragram.messenger.export.api.ExportData;
import com.exteragram.messenger.export.api.ExportParser;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

final class HtmlServiceText {

    private HtmlServiceText() {
    }

    private static String quoted(String title) {
        return "&laquo;" + HtmlContext.serializeString(title) + "&raquo;";
    }

    private static String plural(int count, String one, String many) {
        return count + " " + (count == 1 ? one : many);
    }

    private static final long NANOS_IN_UNIT = 1_000_000_000L;
    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};

    private static String starsAmount(TL_stars.StarsAmount amount) {
        if (amount instanceof TL_stars.TL_starsTonAmount) {
            return ExportFormat.number(amount.amount / (double) NANOS_IN_UNIT) + " TON";
        }
        return amount.amount + " stars";
    }

    private static String indexList(List<Integer> values) {
        ArrayList<String> list = new ArrayList<>(values.size());
        for (int value : values) {
            list.add(String.valueOf(value));
        }
        return HtmlContext.serializeList(list);
    }

    private static String money(long amount, String currency) {
        return HtmlContext.serializeString(LocaleController.getInstance().formatCurrencyString(amount, currency));
    }

    static String text(ExportData.Message message, ExportData.DialogInfo dialog, HtmlPeers peers, String internalLinksDomain,
                       String relativeLinkBase, Function<String, String> wrapReplyToLink) {
        boolean isChannel = dialog.isChannel();
        TLRPC.MessageAction action = message.action;
        if (action == null) {
            return "";
        }
        String from = peers.wrapPeerName(message.fromId);
        if (action instanceof TLRPC.TL_messageActionChatCreate) {
            return from + " created group " + quoted(action.title)
                    + (action.users.isEmpty() ? "" : " with members " + peers.wrapUserNames(action.users));
        } else if (action instanceof TLRPC.TL_messageActionChatEditTitle) {
            return isChannel
                    ? "Channel title changed to " + quoted(action.title)
                    : from + " changed group title to " + quoted(action.title);
        } else if (action instanceof TLRPC.TL_messageActionChatEditPhoto) {
            return isChannel ? "Channel photo changed" : from + " changed group photo";
        } else if (action instanceof TLRPC.TL_messageActionChatDeletePhoto) {
            return isChannel ? "Channel photo removed" : from + " removed group photo";
        } else if (action instanceof TLRPC.TL_messageActionChatAddUser) {
            return from + " invited " + peers.wrapUserNames(action.users);
        } else if (action instanceof TLRPC.TL_messageActionChatDeleteUser) {
            return from + " removed " + peers.wrapUserName(action.user_id);
        } else if (action instanceof TLRPC.TL_messageActionChatJoinedByLink) {
            return from + " joined group by link from " + peers.wrapUserName(action.inviter_id);
        } else if (action instanceof TLRPC.TL_messageActionChannelCreate) {
            return "Channel " + quoted(action.title) + " created";
        } else if (action instanceof TLRPC.TL_messageActionChatMigrateTo) {
            return from + " converted this group to a supergroup";
        } else if (action instanceof TLRPC.TL_messageActionChannelMigrateFrom) {
            return from + " converted a basic group to this supergroup " + quoted(action.title);
        } else if (action instanceof TLRPC.TL_messageActionPinMessage) {
            return from + " pinned " + wrapReplyToLink.apply("this message");
        } else if (action instanceof TLRPC.TL_messageActionHistoryClear) {
            return "History cleared";
        } else if (action instanceof TLRPC.TL_messageActionGameScore) {
            return from + " scored " + action.score + " in " + wrapReplyToLink.apply("this game");
        } else if (action instanceof TLRPC.TL_messageActionPaymentSent) {
            String amount = money(action.total_amount, action.currency);
            if (action.recurring_used) {
                return "You were charged " + amount + " via recurring payment";
            }
            return "You have successfully transferred " + amount + " for " + wrapReplyToLink.apply("this invoice")
                    + (action.recurring_init ? " and allowed future recurring payments" : "");
        } else if (action instanceof TLRPC.TL_messageActionScreenshotTaken) {
            return from + " took a screenshot";
        } else if (action instanceof TLRPC.TL_messageActionCustomAction) {
            return HtmlContext.serializeString(action.message);
        } else if (action instanceof TLRPC.TL_messageActionBotAllowed allowed) {
            if (allowed.attach_menu) {
                return "You allowed this bot to message you when you added it in the attachment menu.";
            } else if (allowed.from_request) {
                return "You allowed this bot to message you in his web-app.";
            } else if (allowed.app != null) {
                return "You allowed this bot to message you when you opened " + HtmlContext.serializeString(allowed.app.title);
            }
            return "You allowed this bot to message you when you logged in on " + HtmlContext.serializeString(allowed.domain);
        } else if (action instanceof TLRPC.TL_messageActionSecureValuesSent sent) {
            ArrayList<String> list = new ArrayList<>();
            for (TLRPC.SecureValueType type : sent.types) {
                String name = secureValueName(type);
                if (!name.isEmpty()) {
                    list.add(name);
                }
            }
            return "You have sent the following documents: " + HtmlContext.serializeList(list);
        } else if (action instanceof TLRPC.TL_messageActionContactSignUp) {
            return from + " joined Telegram";
        } else if (action instanceof TLRPC.TL_messageActionGeoProximityReached reached) {
            long fromId = reached.from_id != null ? MessageObject.getPeerId(reached.from_id) : 0;
            long toId = reached.to_id != null ? MessageObject.getPeerId(reached.to_id) : 0;
            String distance = reached.distance >= 1000
                    ? (reached.distance / 1000) + " km"
                    : plural(reached.distance, "meter", "meters");
            if (fromId == message.selfId) {
                return "You are now within " + distance + " from " + peers.wrapPeerName(toId);
            } else if (toId == message.selfId) {
                return peers.wrapPeerName(fromId) + " is now within " + distance + " from you";
            }
            return peers.wrapPeerName(fromId) + " is now within " + distance + " from " + peers.wrapPeerName(toId);
        } else if (action instanceof TLRPC.TL_messageActionGroupCall) {
            String duration = action.duration != 0 ? " (" + plural(action.duration, "second", "seconds") + ")" : "";
            return isChannel ? "Voice chat" + duration : from + " started voice chat" + duration;
        } else if (action instanceof TLRPC.TL_messageActionInviteToGroupCall) {
            return from + " invited " + peers.wrapUserNames(action.users) + " to the voice chat";
        } else if (action instanceof TLRPC.TL_messageActionSetMessagesTTL ttl) {
            String period = ttlPeriod(ttl.period);
            if (isChannel) {
                return ttl.period != 0 ? "New messages will auto-delete in " + period : "New messages will not auto-delete";
            }
            return ttl.period != 0
                    ? from + " has set messages to auto-delete in " + period
                    : from + " has set messages not to auto-delete";
        } else if (action instanceof TLRPC.TL_messageActionGroupCallScheduled scheduled) {
            String date = ExportFormat.dateTime(scheduled.schedule_date);
            return isChannel ? "Voice chat scheduled for " + date : from + " scheduled a voice chat for " + date;
        } else if (action instanceof TLRPC.TL_messageActionSetChatTheme theme) {
            String emoticon = theme.theme instanceof TLRPC.TL_chatTheme chatTheme ? chatTheme.emoticon : null;
            if (emoticon == null || emoticon.isEmpty()) {
                return isChannel ? "Channel theme was disabled" : from + " disabled chat theme";
            }
            return isChannel
                    ? "Channel theme was changed to " + HtmlContext.serializeString(emoticon)
                    : from + " changed chat theme to " + HtmlContext.serializeString(emoticon);
        } else if (action instanceof TLRPC.TL_messageActionChatJoinedByRequest) {
            return from + " joined group by request";
        } else if (action instanceof TLRPC.TL_messageActionWebViewDataSent sent) {
            return "You have just successfully transferred data from the " + quoted(sent.text) + " button to the bot";
        } else if (action instanceof TLRPC.TL_messageActionGiftPremium) {
            if (action.amount == 0 || action.currency == null) {
                return from + " sent you a gift.";
            }
            int months = action.months != 0 ? action.months : action.days / 30;
            return from + " sent you a gift for " + money(action.amount, action.currency)
                    + ": Telegram Premium for " + plural(Math.max(months, 1), "month", "months") + ".";
        } else if (action instanceof TLRPC.TL_messageActionTopicCreate) {
            return from + " created topic " + quoted(action.title);
        } else if (action instanceof TLRPC.TL_messageActionTopicEdit edit) {
            ArrayList<String> parts = new ArrayList<>();
            if (edit.title != null && !edit.title.isEmpty()) {
                parts.add("title to " + quoted(edit.title));
            }
            if (edit.icon_emoji_id != 0) {
                parts.add("icon to &laquo;" + edit.icon_emoji_id + "&raquo;");
            }
            return parts.isEmpty() ? from + " changed topic" : from + " changed topic " + String.join(", ", parts);
        } else if (action instanceof TLRPC.TL_messageActionSuggestProfilePhoto) {
            return from + " suggests to use this photo";
        } else if (action instanceof TLRPC.TL_messageActionRequestedPeer requested) {
            ArrayList<String> names = new ArrayList<>();
            for (TLRPC.Peer peer : requested.peers) {
                names.add(peers.wrapPeerName(MessageObject.getPeerId(peer)));
            }
            return "You shared " + HtmlContext.serializeList(names) + " with the bot";
        } else if (action instanceof TLRPC.TL_messageActionSetChatWallPaper wallPaper) {
            return wallPaper.same
                    ? from + " set " + wrapReplyToLink.apply("the same background") + " for this chat"
                    : from + " set a new background for this chat";
        } else if (action instanceof TLRPC.TL_messageActionGiftCode code) {
            int months = code.months != 0 ? code.months : code.days / 30;
            String prize = "Telegram Premium for " + plural(Math.max(months, 1), "month", "months");
            if (code.unclaimed) {
                return "This is an unclaimed " + prize + " prize in a giveaway organized by a channel.";
            } else if (code.via_giveaway) {
                return "You won a " + prize + " prize in a giveaway organized by a channel.";
            }
            return "You've received a " + prize + " gift from a channel.";
        } else if (action instanceof TLRPC.TL_messageActionGiveawayLaunch) {
            return from + " just started a giveaway of Telegram Premium subscriptions to its followers.";
        } else if (action instanceof TLRPC.TL_messageActionGiveawayResults results) {
            if (results.winners_count == 0) {
                return "No winners of the giveaway could be selected.";
            } else if (results.unclaimed_count != 0) {
                return results.stars
                        ? "Some winners of the giveaway were randomly selected by Telegram and received their prize."
                        : "Some winners of the giveaway were randomly selected by Telegram and received private messages with giftcodes.";
            }
            String winners = plural(results.winners_count, "winner", "winners");
            return results.stars
                    ? winners + " of the giveaway " + (results.winners_count == 1 ? "was" : "were") + " randomly selected by Telegram and received their prize."
                    : winners + " of the giveaway " + (results.winners_count == 1 ? "was" : "were") + " randomly selected by Telegram and received private messages with giftcodes.";
        } else if (action instanceof TLRPC.TL_messageActionBoostApply boost) {
            return from + " boosted the group " + plural(boost.boosts, "time", "times");
        } else if (action instanceof TLRPC.TL_messageActionPaymentRefunded refunded) {
            long peerId = refunded.peer != null ? MessageObject.getPeerId(refunded.peer) : 0;
            return peers.wrapPeerName(peerId) + " refunded back " + money(refunded.total_amount, refunded.currency);
        } else if (action instanceof TLRPC.TL_messageActionGiftStars gift) {
            if (gift.amount == 0 || gift.currency == null) {
                return from + " sent you a gift of " + plural((int) gift.stars, "Telegram Star", "Telegram Stars") + ".";
            }
            return from + " sent you a gift for " + money(gift.amount, gift.currency) + ": " + plural((int) gift.stars, "Telegram Star", "Telegram Stars") + ".";
        } else if (action instanceof TLRPC.TL_messageActionPrizeStars prize) {
            long peerId = prize.boost_peer != null ? MessageObject.getPeerId(prize.boost_peer) : 0;
            return "You won a prize in a giveaway organized by " + peers.wrapPeerName(peerId)
                    + ". Your prize is " + plural((int) prize.stars, "Telegram Star", "Telegram Stars") + ".";
        } else if (action instanceof TLRPC.TL_messageActionStarGift gift) {
            long stars = gift.gift != null ? gift.gift.stars : 0;
            return from + " sent you a gift for " + plural((int) stars, "Star", "Stars") + ".";
        } else if (action instanceof TLRPC.TL_messageActionPaidMessagesRefunded refunded) {
            return from + " refunded " + plural((int) refunded.stars, "Star", "Stars") + " for " + plural(refunded.count, "message", "messages") + ".";
        } else if (action instanceof TLRPC.TL_messageActionPaidMessagesPrice price) {
            return price.stars > 0
                    ? from + " changed the price for messages to " + plural((int) price.stars, "Star", "Stars") + "."
                    : from + " made messages free.";
        } else if (action instanceof TLRPC.TL_messageActionSuggestBirthday suggest && suggest.birthday != null) {
            int month = suggest.birthday.month;
            return from + " suggests to add a date of birth: " + suggest.birthday.day
                    + (month >= 1 && month <= 12 ? " " + MONTHS[month - 1] : "")
                    + (suggest.birthday.year != 0 ? " " + suggest.birthday.year : "");
        } else if (action instanceof TLRPC.TL_messageActionChatJoinedViaCommunity) {
            return from + " joined group via a community";
        } else if (action instanceof TLRPC.TL_messageActionChangeCommunity) {
            return from + " changed the community of this group";
        } else if (action instanceof TLRPC.TL_messageActionPhoneNumberRequest) {
            return from + " requested your phone number";
        } else if (action instanceof TLRPC.TL_messageActionSetSameChatWallPaper) {
            return from + " set " + wrapReplyToLink.apply("the same background") + " for this chat";
        } else if (action instanceof TLRPC.TL_messageActionGiftTon gift) {
            String amount = ExportFormat.number(gift.cryptoAmount / (double) NANOS_IN_UNIT) + " TON";
            if (gift.amount == 0 || gift.currency == null) {
                return from + " sent you a gift of " + amount + ".";
            }
            return from + " sent you a gift for " + money(gift.amount, gift.currency) + ": " + amount + ".";
        } else if (action instanceof TLRPC.TL_messageActionGramTransfer transfer) {
            String amount = ExportFormat.number(transfer.amount / (double) NANOS_IN_UNIT) + " GRAM";
            String address = transfer.peer_address == null || transfer.peer_address.isEmpty()
                    ? ""
                    : " (" + HtmlContext.serializeString(transfer.peer_address) + ")";
            long dialogUser = dialog.peerId;
            String sender = dialogUser == 333000 || dialogUser == 777000 ? "Someone" : from;
            String result = message.out
                    ? "You sent " + amount + " to " + peers.wrapPeerName(dialog.peerId) + address
                    : sender + address + " sent you " + amount;
            if (transfer.transaction_id != null && !transfer.transaction_id.isEmpty()) {
                result += ", transaction " + HtmlContext.serializeString(transfer.transaction_id);
            }
            if (transfer.comment_encrypted) {
                result += ", with an encrypted comment";
            } else if (transfer.comment != null && !transfer.comment.isEmpty()) {
                result += ", with comment: " + quoted(transfer.comment);
            }
            return result;
        } else if (action instanceof TLRPC.TL_messageActionStarGiftUnique gift) {
            String title = gift.gift != null && gift.gift.title != null ? " " + quoted(gift.gift.title) : "";
            if (gift.upgrade) {
                return from + " upgraded the gift" + title + " to a unique collectible.";
            } else if (gift.transferred) {
                return from + " transferred a unique collectible" + title + ".";
            }
            return from + " sent you a unique collectible" + title + ".";
        } else if (action instanceof TLRPC.TL_messageActionStarGiftPurchaseOffer offer) {
            String price = offer.price != null ? " for " + starsAmount(offer.price) : "";
            if (offer.accepted) {
                return "The offer to buy a gift" + price + " was accepted.";
            } else if (offer.declined) {
                return "The offer to buy a gift" + price + " was declined.";
            }
            return from + " offered to buy a gift" + price + ".";
        } else if (action instanceof TLRPC.TL_messageActionStarGiftPurchaseOfferDeclined offer) {
            String price = offer.price != null ? " for " + starsAmount(offer.price) : "";
            return offer.expired
                    ? "The offer to buy a gift" + price + " has expired."
                    : "The offer to buy a gift" + price + " was declined.";
        } else if (action instanceof TLRPC.TL_messageActionTodoCompletions completions) {
            String done = indexList(completions.completed);
            String notDone = indexList(completions.incompleted);
            String list = wrapReplyToLink.apply("this todo list");
            if (done.isEmpty()) {
                return from + " marked " + notDone + " as not done yet in " + list + ".";
            } else if (notDone.isEmpty()) {
                return from + " marked " + done + " as done in " + list + ".";
            }
            return from + " marked " + done + " as done and " + notDone + " as not done yet in " + list + ".";
        } else if (action instanceof TLRPC.TL_messageActionTodoAppendTasks append) {
            ArrayList<String> tasks = new ArrayList<>();
            for (TLRPC.TodoItem item : append.list) {
                tasks.add("&quot;" + HtmlContext.formatText(ExportParser.parseText(item.title), internalLinksDomain, relativeLinkBase) + "&quot;");
            }
            return from + " added tasks: " + String.join(", ", tasks);
        } else if (action instanceof TLRPC.TL_messageActionPollAppendAnswer append) {
            return from + " added &quot;" + HtmlContext.serializeString(pollAnswerText(append.answer)) + "&quot; to the poll.";
        } else if (action instanceof TLRPC.TL_messageActionPollDeleteAnswer delete) {
            return from + " removed &quot;" + HtmlContext.serializeString(pollAnswerText(delete.answer)) + "&quot; from the poll.";
        } else if (action instanceof TLRPC.TL_messageActionSuggestedPostApproval approval) {
            return from + (approval.rejected ? " rejected " : " approved ") + "your suggested post"
                    + (approval.price != null && approval.price.amount != 0 ? ", for " + starsAmount(approval.price) : "")
                    + (approval.schedule_date != 0
                    ? ", " + ExportFormat.dateText(approval.schedule_date) + " at " + ExportFormat.timeText(approval.schedule_date)
                    : "")
                    + (approval.reject_comment == null || approval.reject_comment.isEmpty()
                    ? "."
                    : ", with comment: &quot;" + HtmlContext.serializeString(approval.reject_comment) + "&quot;");
        } else if (action instanceof TLRPC.TL_messageActionSuggestedPostSuccess success) {
            String price = success.price != null ? starsAmount(success.price) : "0 stars";
            return "The paid post was shown for 24 hours and " + price + " were transferred to the channel.";
        } else if (action instanceof TLRPC.TL_messageActionSuggestedPostRefund refund) {
            return refund.payer_initiated
                    ? "The user refunded the payment, post was deleted."
                    : "The admin deleted the post early, the payment was refunded.";
        } else if (action instanceof TLRPC.TL_messageActionNoForwardsToggle toggle) {
            return from + (toggle.new_value ? " disabled sharing in this chat" : " enabled sharing in this chat");
        } else if (action instanceof TLRPC.TL_messageActionNoForwardsRequest) {
            return from + " requested to enable sharing in this chat";
        } else if (action instanceof TLRPC.TL_messageActionNewCreatorPending pending) {
            return peers.wrapUserName(pending.new_creator_id) + " will become the new main admin in 7 days if " + from + " does not return";
        } else if (action instanceof TLRPC.TL_messageActionChangeCreator change) {
            return from + " made " + peers.wrapUserName(change.new_creator_id) + " the new main admin of the group";
        } else if (action instanceof TLRPC.TL_messageActionManagedBotCreated created) {
            return from + " created a bot " + peers.wrapUserName(created.bot_id);
        } else if (action instanceof TLRPC.TL_messageActionWalletTonConnectRequest request) {
            String topic = request.topic == null || request.topic.isEmpty() ? "" : " " + quoted(request.topic);
            return request.accepted
                    ? "You approved a TON Connect request" + topic + "."
                    : request.declined
                    ? "You declined a TON Connect request" + topic + "."
                    : "You received a TON Connect request" + topic + ".";
        } else if (action instanceof TLRPC.TL_messageActionNoPaidMessagesTokenUsed) {
            return from + " started a chat via a free messages link";
        } else if (action instanceof TLRPC.TL_messageActionAttachMenuBotAllowed) {
            return "You allowed this bot to message you when you added it in the attachment menu.";
        } else if (action instanceof TLRPC.TL_messageActionPhoneCall
                || action instanceof TLRPC.TL_messageActionConferenceCall
                || action instanceof TLRPC.TL_messageActionEmpty) {
            return "";
        }
        return "This service message is not supported by the export.";
    }

    private static String pollAnswerText(TLRPC.PollAnswer answer) {
        return answer != null && answer.text != null && answer.text.text != null ? answer.text.text : "";
    }

    private static String ttlPeriod(int period) {
        if (period >= 86400 * 30) {
            return plural(period / (86400 * 30), "month", "months");
        } else if (period >= 86400 * 7) {
            return plural(period / (86400 * 7), "week", "weeks");
        } else if (period >= 86400) {
            return plural(period / 86400, "day", "days");
        } else if (period >= 3600) {
            return plural(period / 3600, "hour", "hours");
        } else if (period >= 60) {
            return plural(period / 60, "minute", "minutes");
        }
        return plural(period, "second", "seconds");
    }

    private static String secureValueName(TLRPC.SecureValueType type) {
        if (type instanceof TLRPC.TL_secureValueTypePersonalDetails) {
            return "Personal details";
        } else if (type instanceof TLRPC.TL_secureValueTypePassport) {
            return "Passport";
        } else if (type instanceof TLRPC.TL_secureValueTypeDriverLicense) {
            return "Driver license";
        } else if (type instanceof TLRPC.TL_secureValueTypeIdentityCard) {
            return "Identity card";
        } else if (type instanceof TLRPC.TL_secureValueTypeInternalPassport) {
            return "Internal passport";
        } else if (type instanceof TLRPC.TL_secureValueTypeAddress) {
            return "Address information";
        } else if (type instanceof TLRPC.TL_secureValueTypeUtilityBill) {
            return "Utility bill";
        } else if (type instanceof TLRPC.TL_secureValueTypeBankStatement) {
            return "Bank statement";
        } else if (type instanceof TLRPC.TL_secureValueTypeRentalAgreement) {
            return "Rental agreement";
        } else if (type instanceof TLRPC.TL_secureValueTypePassportRegistration) {
            return "Passport registration";
        } else if (type instanceof TLRPC.TL_secureValueTypeTemporaryRegistration) {
            return "Temporary registration";
        } else if (type instanceof TLRPC.TL_secureValueTypePhone) {
            return "Phone number";
        } else if (type instanceof TLRPC.TL_secureValueTypeEmail) {
            return "Email";
        }
        return "";
    }
}
