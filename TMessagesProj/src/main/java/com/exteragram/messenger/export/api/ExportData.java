/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export.api;

import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;

public final class ExportData {

    private ExportData() {
    }

    public static class FileLocation {
        public int dcId;
        public TLRPC.InputFileLocation data;
    }

    public static class File {
        public enum SkipReason {
            None, Unavailable, FileType, FileSize, DateLimits
        }

        public FileLocation location;
        public long size;
        public byte[] content;
        public String suggestedPath = "";
        public String relativePath = "";
        public SkipReason skipReason = SkipReason.None;

        public boolean hasContent() {
            return content != null && content.length > 0;
        }
    }

    public static class Image {
        public int width;
        public int height;
        public File file = new File();
    }

    public static class Photo {
        public long id;
        public int date;
        public boolean spoilered;
        public Image image = new Image();
    }

    public static class Document {
        public long id;
        public int date;
        public File file = new File();
        public Image thumb = new Image();
        public String name = "";
        public String mime = "";
        public int width;
        public int height;
        public String stickerEmoji = "";
        public String songPerformer = "";
        public String songTitle = "";
        public int duration;
        public boolean isSticker;
        public boolean isAnimated;
        public boolean isVideoMessage;
        public boolean isVoiceMessage;
        public boolean isVideoFile;
        public boolean isAudioFile;
        public boolean spoilered;
    }

    public static class SharedContact {
        public ContactInfo info = new ContactInfo();
        public File vcard = new File();
    }

    public static class GeoPoint {
        public double latitude;
        public double longitude;
        public boolean valid;
    }

    public static class Venue {
        public GeoPoint point = new GeoPoint();
        public String title = "";
        public String address = "";
    }

    public static class Game {
        public String shortName = "";
        public String title = "";
        public String description = "";
        public long botId;
    }

    public static class Invoice {
        public String title = "";
        public String description = "";
        public String currency = "";
        public long amount;
        public int receiptMsgId;
    }

    public static class Poll {
        public record Answer(ArrayList<TextPart> text, int votes, boolean my) {
        }

        public ArrayList<TextPart> question = new ArrayList<>();
        public ArrayList<Answer> answers = new ArrayList<>();
        public int totalVotes;
        public boolean closed;
    }

    public static class TodoList {
        public record Item(int id, ArrayList<TextPart> text) {
        }

        public record Completion(long peerId, int date) {
        }

        public ArrayList<TextPart> title = new ArrayList<>();
        public ArrayList<Item> items = new ArrayList<>();
        public HashMap<Integer, Completion> completions = new HashMap<>();
        public boolean othersCanAppend;
        public boolean othersCanComplete;
    }

    public static class Dice {
        public String emoticon = "";
        public int value;
    }

    public static class StoryReference {
        public long peerId;
        public int storyId;
    }

    public static class GiveawayStart {
        public ArrayList<Long> channels = new ArrayList<>();
        public ArrayList<String> countries = new ArrayList<>();
        public String additionalPrize = "";
        public int untilDate;
        public long credits;
        public int quantity;
        public int months;
        public boolean all;
    }

    public static class GiveawayResults {
        public long channel;
        public ArrayList<Long> winners = new ArrayList<>();
        public String additionalPrize = "";
        public int untilDate;
        public int launchId;
        public int additionalPeersCount;
        public int winnersCount;
        public int unclaimedCount;
        public int months;
        public long credits;
        public boolean refunded;
        public boolean all;
    }

    public static class PaidMedia {
        public long stars;
    }

    public static final class UnsupportedMedia {
    }

    public static class Media {
        public Object content;
        public int ttl;

        public File file() {
            if (content instanceof Photo photo) {
                return photo.image.file;
            } else if (content instanceof Document document) {
                return document.file;
            } else if (content instanceof SharedContact contact) {
                return contact.vcard;
            }
            return new File();
        }

        public Image thumb() {
            if (content instanceof Document document) {
                return document.thumb;
            }
            return new Image();
        }
    }

    public static class TextPart {
        public static final String UNAVAILABLE_EMOJI = "(unavailable)";

        public enum Type {
            Text, Unknown, Mention, Hashtag, BotCommand, Url, Email, Bold, Italic, Code, Pre, TextUrl,
            MentionName, Phone, Cashtag, Underline, Strike, Blockquote, BankCard, Spoiler, CustomEmoji
        }

        public Type type = Type.Text;
        public String text = "";
        public String additional = "";
    }

    public static class InlineButton {
        public enum Type {
            Default, Url, Callback, CallbackWithPassword, RequestPhone, RequestLocation, RequestPoll, RequestPeer,
            SwitchInline, SwitchInlineSame, Game, Buy, Auth, UserProfile, WebView, SimpleWebView, CopyText
        }

        public final Type type;
        public final String text;
        public final byte[] data;
        public final String forwardText;
        public final int buttonId;

        public InlineButton(Type type, String text, byte[] data, String forwardText, int buttonId) {
            this.type = type;
            this.text = text != null ? text : "";
            this.data = data;
            this.forwardText = forwardText != null ? forwardText : "";
            this.buttonId = buttonId;
        }

        public String typeString() {
            return switch (type) {
                case Default -> "default";
                case Url -> "url";
                case Callback -> "callback";
                case CallbackWithPassword -> "callback_with_password";
                case RequestPhone -> "request_phone";
                case RequestLocation -> "request_location";
                case RequestPoll -> "request_poll";
                case RequestPeer -> "request_peer";
                case SwitchInline -> "switch_inline";
                case SwitchInlineSame -> "switch_inline_same";
                case Game -> "game";
                case Buy -> "buy";
                case Auth -> "auth";
                case UserProfile -> "user_profile";
                case WebView -> "web_view";
                case SimpleWebView -> "simple_web_view";
                case CopyText -> "copy_text";
            };
        }
    }

    public static class Reaction {
        public enum Type {
            Empty, Emoji, CustomEmoji, Paid
        }

        public record Recent(long peerId, int date) {
        }

        public Type type = Type.Empty;
        public String emoji = "";
        public String documentId = "";
        public int count;
        public ArrayList<Recent> recent = new ArrayList<>();

        public String typeString() {
            return switch (type) {
                case Empty -> "empty";
                case Emoji -> "emoji";
                case CustomEmoji -> "custom_emoji";
                case Paid -> "paid";
            };
        }

        public String id() {
            return typeString() + switch (type) {
                case Emoji -> emoji;
                case CustomEmoji -> documentId;
                default -> "";
            };
        }
    }

    public static class Message {
        public int id;
        public int date;
        public int edited;
        public long fromId;
        public long peerId;
        public long selfId;
        public long forwardedFromId;
        public String forwardedFromName = "";
        public int forwardedDate;
        public boolean forwarded;
        public boolean showForwardedAsOriginal;
        public long savedFromChatId;
        public String signature = "";
        public long viaBotId;
        public int replyToMsgId;
        public long replyToPeerId;
        public boolean out;
        public ArrayList<TextPart> text = new ArrayList<>();
        public ArrayList<Reaction> reactions = new ArrayList<>();
        public ArrayList<ArrayList<InlineButton>> inlineButtonRows = new ArrayList<>();
        public Media media = new Media();
        public TLRPC.MessageAction action;
        public Photo actionPhoto;

        public File file() {
            return actionPhoto != null ? actionPhoto.image.file : media.file();
        }

        public Image thumb() {
            return actionPhoto != null ? new Image() : media.thumb();
        }
    }

    public static class MessagesSlice {
        public ArrayList<Message> list = new ArrayList<>();
        public HashMap<Long, Peer> peers = new HashMap<>();
    }

    public static class ContactInfo {
        public long userId;
        public String firstName = "";
        public String lastName = "";
        public String phoneNumber = "";
        public int date;
        public int colorIndex;

        public String name() {
            return ExportData.composeName(firstName, lastName);
        }
    }

    public static class User {
        public long bareId;
        public ContactInfo info = new ContactInfo();
        public String username = "";
        public int colorIndex;
        public boolean isBot;
        public boolean isSelf;
        public boolean isReplies;
        public boolean isVerifyCodes;
        public TLRPC.InputUser input = new TLRPC.TL_inputUserEmpty();

        public String name() {
            return info.name();
        }
    }

    public static class Chat {
        public long bareId;
        public long migratedToChannelId;
        public String title = "";
        public String username = "";
        public int colorIndex;
        public boolean isChannel;
        public boolean isBroadcast;
        public boolean isSupergroup;
        public boolean isMonoforum;
        public boolean isMonoforumAdmin;
        public boolean isMonoforumOfPublicBroadcast;
        public boolean hasMonoforumAdminRights;
        public long monoforumLinkId;
        public TLRPC.InputPeer input = new TLRPC.TL_inputPeerEmpty();
        public TLRPC.InputPeer monoforumBroadcastInput = new TLRPC.TL_inputPeerEmpty();
    }

    public static class Peer {
        public final User user;
        public final Chat chat;

        public Peer(User user) {
            this.user = user;
            this.chat = null;
        }

        public Peer(Chat chat) {
            this.user = null;
            this.chat = chat;
        }

        public long id() {
            return user != null ? user.bareId : -chat.bareId;
        }

        public long bareId() {
            return user != null ? user.bareId : chat.bareId;
        }

        public String name() {
            return user != null ? user.name() : chat.title;
        }

        public int colorIndex() {
            return user != null ? user.colorIndex : chat.colorIndex;
        }

        public TLRPC.InputPeer input() {
            if (user != null) {
                if (user.input instanceof TLRPC.TL_inputUser inputUser) {
                    TLRPC.TL_inputPeerUser peer = new TLRPC.TL_inputPeerUser();
                    peer.user_id = inputUser.user_id;
                    peer.access_hash = inputUser.access_hash;
                    return peer;
                }
                return new TLRPC.TL_inputPeerEmpty();
            }
            return chat.input;
        }
    }

    public static class DialogInfo {
        public enum Type {
            Unknown, Self, Replies, VerifyCodes, Personal, Bot, PrivateGroup, PrivateSupergroup, PublicSupergroup,
            PrivateChannel, PublicChannel
        }

        public Type type = Type.Unknown;
        public String name = "";
        public String lastName = "";
        public TLRPC.InputPeer input = new TLRPC.TL_inputPeerEmpty();
        public int topMessageId;
        public int topMessageDate;
        public long peerId;
        public int colorIndex;
        public TLRPC.InputPeer migratedFromInput = new TLRPC.TL_inputPeerEmpty();
        public long migratedToChannelId;
        public boolean isMonoforum;
        public TLRPC.InputPeer monoforumBroadcastInput = new TLRPC.TL_inputPeerEmpty();

        public ArrayList<Integer> splits = new ArrayList<>();
        public ArrayList<Integer> messagesCountPerSplit = new ArrayList<>();

        public boolean onlyMyMessages;
        public boolean isLeftChannel;
        public String relativePath = "";

        public long bareId() {
            return Math.abs(peerId);
        }

        public boolean isSavedOrService() {
            return type == Type.Self || type == Type.Replies || type == Type.VerifyCodes;
        }

        public boolean isChannel() {
            return type == Type.PrivateChannel || type == Type.PublicChannel;
        }

        public boolean isSupergroup() {
            return type == Type.PrivateSupergroup || type == Type.PublicSupergroup;
        }
    }

    public static class DialogsInfo {
        public ArrayList<DialogInfo> chats = new ArrayList<>();
        public ArrayList<DialogInfo> left = new ArrayList<>();

        public int size() {
            return chats.size() + left.size();
        }

        public DialogInfo itemAt(int index) {
            if (index < 0) {
                return null;
            } else if (index < chats.size()) {
                return chats.get(index);
            } else if (index - chats.size() < left.size()) {
                return left.get(index - chats.size());
            }
            return null;
        }
    }

    public static class PersonalInfo {
        public User user = new User();
        public String bio = "";
    }

    public static class Story {
        public int id;
        public int date;
        public int expires;
        public Media media = new Media();
        public boolean pinned;
        public ArrayList<TextPart> caption = new ArrayList<>();

        public File file() {
            return media.file();
        }

        public Image thumb() {
            return media.thumb();
        }
    }

    public static class StoriesSlice {
        public ArrayList<Story> list = new ArrayList<>();
        public int lastId;
        public int skipped;
    }

    public static class TopPeer {
        public Peer peer;
        public double rating;
    }

    public static class ContactsList {
        public ArrayList<ContactInfo> list = new ArrayList<>();
        public ArrayList<TopPeer> correspondents = new ArrayList<>();
        public ArrayList<TopPeer> inlineBots = new ArrayList<>();
        public ArrayList<TopPeer> phoneCalls = new ArrayList<>();
    }

    public record WebSession(String botUsername, String domain, String browser, String platform, int created,
                             int lastActive, String ip, String region) {
    }

    public static class SessionsList {
        public ArrayList<TLRPC.TL_authorization> list = new ArrayList<>();
        public ArrayList<WebSession> webList = new ArrayList<>();
    }

    public static class ParseMediaContext {
        public long selfPeerId;
        public int photos;
        public int audios;
        public int videos;
        public int files;
        public int contacts;
        public long botId;
    }

    public record FileOrigin(int split, TLRPC.InputPeer peer, int messageId, int storyId, long customEmojiId) {
        public static final FileOrigin EMPTY = new FileOrigin(0, null, 0, 0, 0);
    }

    public static String composeName(String firstName, String lastName) {
        boolean hasFirst = firstName != null && !firstName.isEmpty();
        boolean hasLast = lastName != null && !lastName.isEmpty();
        if (hasFirst && hasLast) {
            return firstName + " " + lastName;
        }
        return hasFirst ? firstName : hasLast ? lastName : "";
    }
}
