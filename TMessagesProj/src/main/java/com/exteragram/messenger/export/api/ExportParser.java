/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export.api;

import android.text.TextUtils;
import android.util.Base64;
import android.webkit.MimeTypeMap;

import com.exteragram.messenger.export.ExportFormat;
import com.exteragram.messenger.export.ExportSettings;
import com.exteragram.messenger.export.output.ExportFiles;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_keyboard;
import org.telegram.tgnet.tl.TL_stories;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class ExportParser {

    private static final int MIGRATED_MESSAGES_ID_SHIFT = -1_000_000_000;
    private static final int[] PEER_COLOR_MAP = {0, 7, 4, 1, 6, 3, 5};

    private ExportParser() {
    }

    public static int peerColorIndex(long bareId) {
        return PEER_COLOR_MAP[(int) (Math.abs(bareId) % PEER_COLOR_MAP.length)];
    }

    private static int colorIndex(int colorId, long bareId) {
        return colorId >= 0 && colorId < PEER_COLOR_MAP.length ? PEER_COLOR_MAP[colorId] : peerColorIndex(bareId);
    }

    public static int applicationColorIndex(int apiId) {
        return switch (apiId) {
            case 1, 7 -> 0;
            case 6, 21724 -> 1;
            case 2834 -> 2;
            case 2496 -> 3;
            case 2040 -> 4;
            case 1429 -> 5;
            default -> peerColorIndex(apiId);
        };
    }

    public static long stringBarePeerId(String data) {
        long result = 0xFF;
        for (int i = 0; i < data.length(); i++) {
            result = ((result * 239) + data.charAt(i)) & 0xFF;
        }
        return result;
    }

    private static String nonNull(String value) {
        return value != null ? value : "";
    }

    public static ArrayList<ExportData.TextPart> parseText(String data, List<TLRPC.MessageEntity> entities) {
        data = nonNull(data);
        ArrayList<ExportData.TextPart> result = new ArrayList<>();
        int size = data.length();
        int offset = 0;
        if (entities != null) {
            for (TLRPC.MessageEntity entity : entities) {
                int start = entity.offset;
                int length = entity.length;
                if (start < offset || length <= 0 || start + length > size) {
                    continue;
                }
                if (start > offset) {
                    ExportData.TextPart plain = new ExportData.TextPart();
                    plain.text = data.substring(offset, start);
                    result.add(plain);
                }
                ExportData.TextPart part = new ExportData.TextPart();
                part.type = textPartType(entity);
                part.text = data.substring(start, start + length);
                if (entity instanceof TLRPC.TL_messageEntityPre) {
                    part.additional = nonNull(entity.language);
                } else if (entity instanceof TLRPC.TL_messageEntityTextUrl) {
                    part.additional = nonNull(entity.url);
                } else if (entity instanceof TLRPC.TL_messageEntityMentionName mention) {
                    part.additional = String.valueOf(mention.user_id);
                } else if (entity instanceof TLRPC.TL_inputMessageEntityMentionName mention && mention.user_id != null) {
                    part.additional = String.valueOf(mention.user_id.user_id);
                } else if (entity instanceof TLRPC.TL_messageEntityCustomEmoji emoji) {
                    part.additional = String.valueOf(emoji.document_id);
                } else if (entity instanceof TLRPC.TL_messageEntityBlockquote) {
                    part.additional = entity.collapsed ? "1" : "";
                }
                result.add(part);
                offset = start + length;
            }
        }
        if (size > offset) {
            ExportData.TextPart plain = new ExportData.TextPart();
            plain.text = data.substring(offset);
            result.add(plain);
        }
        return result;
    }

    private static ExportData.TextPart.Type textPartType(TLRPC.MessageEntity entity) {
        if (entity instanceof TLRPC.TL_messageEntityMention) {
            return ExportData.TextPart.Type.Mention;
        } else if (entity instanceof TLRPC.TL_messageEntityHashtag) {
            return ExportData.TextPart.Type.Hashtag;
        } else if (entity instanceof TLRPC.TL_messageEntityBotCommand) {
            return ExportData.TextPart.Type.BotCommand;
        } else if (entity instanceof TLRPC.TL_messageEntityUrl) {
            return ExportData.TextPart.Type.Url;
        } else if (entity instanceof TLRPC.TL_messageEntityEmail) {
            return ExportData.TextPart.Type.Email;
        } else if (entity instanceof TLRPC.TL_messageEntityBold) {
            return ExportData.TextPart.Type.Bold;
        } else if (entity instanceof TLRPC.TL_messageEntityItalic) {
            return ExportData.TextPart.Type.Italic;
        } else if (entity instanceof TLRPC.TL_messageEntityCode) {
            return ExportData.TextPart.Type.Code;
        } else if (entity instanceof TLRPC.TL_messageEntityPre) {
            return ExportData.TextPart.Type.Pre;
        } else if (entity instanceof TLRPC.TL_messageEntityTextUrl) {
            return ExportData.TextPart.Type.TextUrl;
        } else if (entity instanceof TLRPC.TL_messageEntityMentionName || entity instanceof TLRPC.TL_inputMessageEntityMentionName) {
            return ExportData.TextPart.Type.MentionName;
        } else if (entity instanceof TLRPC.TL_messageEntityPhone) {
            return ExportData.TextPart.Type.Phone;
        } else if (entity instanceof TLRPC.TL_messageEntityCashtag) {
            return ExportData.TextPart.Type.Cashtag;
        } else if (entity instanceof TLRPC.TL_messageEntityUnderline) {
            return ExportData.TextPart.Type.Underline;
        } else if (entity instanceof TLRPC.TL_messageEntityStrike) {
            return ExportData.TextPart.Type.Strike;
        } else if (entity instanceof TLRPC.TL_messageEntityBlockquote) {
            return ExportData.TextPart.Type.Blockquote;
        } else if (entity instanceof TLRPC.TL_messageEntityBankCard) {
            return ExportData.TextPart.Type.BankCard;
        } else if (entity instanceof TLRPC.TL_messageEntitySpoiler) {
            return ExportData.TextPart.Type.Spoiler;
        } else if (entity instanceof TLRPC.TL_messageEntityCustomEmoji) {
            return ExportData.TextPart.Type.CustomEmoji;
        }
        return ExportData.TextPart.Type.Unknown;
    }

    public static ArrayList<ExportData.TextPart> parseText(TLRPC.TL_textWithEntities text) {
        return text != null ? parseText(text.text, text.entities) : new ArrayList<>();
    }

    public static String plainText(ArrayList<ExportData.TextPart> parts) {
        StringBuilder result = new StringBuilder();
        for (ExportData.TextPart part : parts) {
            result.append(part.text);
        }
        return result.toString();
    }

    public static ExportData.ContactInfo parseContactInfo(TLRPC.User data) {
        ExportData.ContactInfo result = new ExportData.ContactInfo();
        if (data != null) {
            result.userId = data.id;
            result.colorIndex = colorIndex(UserObject.getColorId(data), data.id);
            if (data instanceof TLRPC.TL_user) {
                result.firstName = nonNull(data.first_name);
                result.lastName = nonNull(data.last_name);
                result.phoneNumber = nonNull(data.phone);
            }
        }
        return result;
    }

    public static ExportData.User parseUser(TLRPC.User data) {
        ExportData.User result = new ExportData.User();
        result.info = parseContactInfo(data);
        if (data == null) {
            return result;
        }
        result.bareId = data.id;
        result.colorIndex = result.info.colorIndex;
        TLRPC.TL_inputUser input = new TLRPC.TL_inputUser();
        input.user_id = data.id;
        if (data instanceof TLRPC.TL_user) {
            result.username = nonNull(UserObject.getPublicUsername(data));
            result.isBot = data.bot;
            result.isSelf = data.self;
            result.isReplies = data.id == UserObject.REPLY_BOT;
            result.isVerifyCodes = data.id == UserObject.VERIFY;
            input.access_hash = data.access_hash;
        }
        result.input = input;
        return result;
    }

    public static ExportData.Chat parseChat(TLRPC.Chat data) {
        ExportData.Chat result = new ExportData.Chat();
        if (data == null) {
            return result;
        }
        result.bareId = data.id;
        result.title = nonNull(data.title);
        result.colorIndex = colorIndex(ChatObject.getColorId(data), data.id);
        if (data instanceof TLRPC.TL_channel || data instanceof TLRPC.TL_channelForbidden) {
            result.isChannel = true;
            result.isBroadcast = data.broadcast;
            result.isSupergroup = data.megagroup;
            TLRPC.TL_inputPeerChannel input = new TLRPC.TL_inputPeerChannel();
            input.channel_id = data.id;
            input.access_hash = data.access_hash;
            result.input = input;
            if (data instanceof TLRPC.TL_channel) {
                result.username = nonNull(ChatObject.getPublicUsername(data));
                result.isMonoforum = data.monoforum;
                result.hasMonoforumAdminRights = data.broadcast
                        && (data.creator || (data.admin_rights != null && data.admin_rights.manage_direct_messages));
                result.monoforumLinkId = data.linked_monoforum_id;
            }
        } else {
            TLRPC.TL_inputPeerChat input = new TLRPC.TL_inputPeerChat();
            input.chat_id = data.id;
            result.input = input;
            if (data instanceof TLRPC.TL_chat && data.migrated_to instanceof TLRPC.TL_inputChannel migratedTo) {
                result.migratedToChannelId = migratedTo.channel_id;
            }
        }
        return result;
    }

    public static HashMap<Long, ExportData.Peer> parsePeersLists(List<TLRPC.User> users, List<TLRPC.Chat> chats) {
        HashMap<Long, ExportData.Peer> result = new LinkedHashMap<>();
        if (users != null) {
            for (TLRPC.User user : users) {
                ExportData.User parsed = parseUser(user);
                result.put(parsed.bareId, new ExportData.Peer(parsed));
            }
        }
        if (chats != null) {
            for (TLRPC.Chat chat : chats) {
                ExportData.Chat parsed = parseChat(chat);
                result.put(-parsed.bareId, new ExportData.Peer(parsed));
            }
        }
        for (ExportData.Peer peer : result.values()) {
            if (peer.chat != null && peer.chat.isMonoforum) {
                ExportData.Peer linked = result.get(-peer.chat.monoforumLinkId);
                if (linked != null && linked.chat != null) {
                    peer.chat.isMonoforumAdmin = linked.chat.hasMonoforumAdminRights;
                    peer.chat.isMonoforumOfPublicBroadcast = !TextUtils.isEmpty(linked.chat.username);
                }
            }
        }
        return result;
    }

    public static ExportData.Peer emptyPeer(long peerId) {
        if (peerId > 0) {
            ExportData.User user = new ExportData.User();
            user.bareId = peerId;
            user.info.userId = peerId;
            user.colorIndex = user.info.colorIndex = peerColorIndex(peerId);
            return new ExportData.Peer(user);
        }
        ExportData.Chat chat = new ExportData.Chat();
        chat.bareId = -peerId;
        chat.colorIndex = peerColorIndex(-peerId);
        return new ExportData.Peer(chat);
    }

    public static ExportData.DialogInfo.Type dialogTypeFromUser(ExportData.User user) {
        if (user.isSelf) {
            return ExportData.DialogInfo.Type.Self;
        } else if (user.isReplies) {
            return ExportData.DialogInfo.Type.Replies;
        } else if (user.isVerifyCodes) {
            return ExportData.DialogInfo.Type.VerifyCodes;
        } else if (user.isBot) {
            return ExportData.DialogInfo.Type.Bot;
        }
        return ExportData.DialogInfo.Type.Personal;
    }

    public static ExportData.DialogInfo.Type dialogTypeFromChat(ExportData.Chat chat) {
        if (chat.isMonoforum && !chat.isMonoforumAdmin) {
            return ExportData.DialogInfo.Type.Personal;
        } else if (chat.isMonoforumAdmin) {
            return chat.isMonoforumOfPublicBroadcast
                    ? ExportData.DialogInfo.Type.PublicSupergroup
                    : ExportData.DialogInfo.Type.PrivateSupergroup;
        } else if (chat.username.isEmpty()) {
            return chat.isBroadcast
                    ? ExportData.DialogInfo.Type.PrivateChannel
                    : chat.isSupergroup
                    ? ExportData.DialogInfo.Type.PrivateSupergroup
                    : ExportData.DialogInfo.Type.PrivateGroup;
        }
        return chat.isBroadcast
                ? ExportData.DialogInfo.Type.PublicChannel
                : ExportData.DialogInfo.Type.PublicSupergroup;
    }

    public static int settingsFromDialogType(ExportData.DialogInfo.Type type) {
        return switch (type) {
            case Self, Personal -> ExportSettings.PersonalChats;
            case Bot -> ExportSettings.BotChats;
            case PrivateGroup, PrivateSupergroup -> ExportSettings.PrivateGroups;
            case PublicSupergroup -> ExportSettings.PublicGroups;
            case PrivateChannel -> ExportSettings.PrivateChannels;
            case PublicChannel -> ExportSettings.PublicChannels;
            default -> 0;
        };
    }

    public static ExportData.DialogInfo dialogInfoFromUser(ExportData.User user) {
        ExportData.DialogInfo result = new ExportData.DialogInfo();
        TLRPC.TL_inputPeerUser input = new TLRPC.TL_inputPeerUser();
        input.user_id = user.bareId;
        input.access_hash = user.input.access_hash;
        result.input = input;
        result.name = user.info.firstName;
        result.lastName = user.info.lastName;
        result.peerId = user.bareId;
        result.colorIndex = user.colorIndex;
        result.type = dialogTypeFromUser(user);
        return result;
    }

    public static ExportData.DialogInfo dialogInfoFromChat(ExportData.Chat chat) {
        ExportData.DialogInfo result = new ExportData.DialogInfo();
        result.input = chat.input;
        result.name = chat.title;
        result.peerId = -chat.bareId;
        result.colorIndex = chat.colorIndex;
        result.type = dialogTypeFromChat(chat);
        result.migratedToChannelId = chat.migratedToChannelId;
        result.isMonoforum = chat.isMonoforum;
        if (chat.isMonoforumAdmin) {
            result.monoforumBroadcastInput = chat.monoforumBroadcastInput;
        }
        return result;
    }

    public static ExportData.DialogsInfo parseDialogsInfo(TLRPC.messages_Dialogs data) {
        ExportData.DialogsInfo result = new ExportData.DialogsInfo();
        HashMap<Long, ExportData.Peer> peers = parsePeersLists(data.users, data.chats);
        HashMap<String, Integer> topDates = new HashMap<>();
        for (TLRPC.Message message : data.messages) {
            if (!(message instanceof TLRPC.TL_messageEmpty)) {
                topDates.put(MessageObject.getPeerId(message.peer_id) + "_" + message.id, message.date);
            }
        }
        for (TLRPC.Dialog dialog : data.dialogs) {
            long peerId = MessageObject.getPeerId(dialog.peer);
            ExportData.Peer peer = peers.get(peerId);
            ExportData.DialogInfo info;
            if (peer == null) {
                info = new ExportData.DialogInfo();
                info.peerId = peerId;
            } else if (peer.user != null) {
                info = dialogInfoFromUser(peer.user);
            } else {
                info = dialogInfoFromChat(peer.chat);
            }
            info.topMessageId = dialog.top_message;
            Integer date = topDates.get(peerId + "_" + dialog.top_message);
            info.topMessageDate = date != null ? date : 0;
            result.chats.add(info);
        }
        return result;
    }

    public static ExportData.DialogsInfo parseLeftChannelsInfo(TLRPC.messages_Chats data) {
        ExportData.DialogsInfo result = new ExportData.DialogsInfo();
        for (TLRPC.Chat chat : data.chats) {
            ExportData.DialogInfo info = dialogInfoFromChat(parseChat(chat));
            info.isLeftChannel = true;
            result.left.add(info);
        }
        return result;
    }

    public static boolean addMigrateFromSlice(ExportData.DialogInfo to, ExportData.DialogInfo from, int splitIndex, int splitsCount) {
        boolean good = to.migratedFromInput instanceof TLRPC.TL_inputPeerEmpty
                || (to.migratedFromInput instanceof TLRPC.TL_inputPeerChat chat && from.bareId() == chat.chat_id);
        if (!good) {
            return false;
        }
        for (int i = 0; i < from.splits.size(); i++) {
            to.splits.add(from.splits.get(i) - splitsCount);
            to.messagesCountPerSplit.add(from.messagesCountPerSplit.get(i));
        }
        to.migratedFromInput = from.input;
        to.splits.add(splitIndex - splitsCount);
        to.messagesCountPerSplit.add(0);
        return true;
    }

    public static void finalizeDialogsInfo(ExportData.DialogsInfo info, ExportSettings settings) {
        int digits = String.valueOf(Math.max(info.size() - 1, 0)).length();
        int index = 0;
        for (ExportData.DialogInfo dialog : info.chats) {
            String number = ExportFormat.padLeft(String.valueOf(++index), digits, '0');
            dialog.relativePath = settings.onlySinglePeer() ? "" : "chats/chat_" + number + "/";
            int setting = settingsFromDialogType(dialog.type);
            dialog.onlyMyMessages = dialog.type != ExportData.DialogInfo.Type.Personal
                    && (settings.fullChats & setting) != setting;
            Collections.sort(dialog.splits);
        }
        for (ExportData.DialogInfo dialog : info.left) {
            String number = ExportFormat.padLeft(String.valueOf(++index), digits, '0');
            dialog.relativePath = "chats/chat_" + number + "/";
            dialog.onlyMyMessages = true;
        }
    }

    public static ExportData.PersonalInfo parsePersonalInfo(TLRPC.TL_users_userFull data) {
        ExportData.PersonalInfo result = new ExportData.PersonalInfo();
        if (!data.users.isEmpty()) {
            result.user = parseUser(data.users.get(0));
        }
        if (data.full_user != null) {
            result.bio = nonNull(data.full_user.about);
        }
        return result;
    }

    public static ExportData.Photo parsePhoto(TLRPC.Photo data, String suggestedPath) {
        ExportData.Photo result = new ExportData.Photo();
        if (data == null) {
            return result;
        }
        result.id = data.id;
        if (data instanceof TLRPC.TL_photo) {
            result.date = data.date;
            result.image = parseMaxImage(data, suggestedPath);
        }
        return result;
    }

    private static boolean isRealPhotoSize(TLRPC.PhotoSize size) {
        return size != null
                && !(size instanceof TLRPC.TL_photoSizeEmpty)
                && !(size instanceof TLRPC.TL_photoStrippedSize)
                && !(size instanceof TLRPC.TL_photoPathSize);
    }

    private static void fillImageFile(ExportData.File file, TLRPC.PhotoSize size, long fallbackSize) {
        if (size instanceof TLRPC.TL_photoCachedSize && size.bytes != null) {
            file.content = size.bytes;
            file.size = size.bytes.length;
        } else if (size instanceof TLRPC.TL_photoSizeProgressive progressive && !progressive.sizes.isEmpty()) {
            file.size = progressive.sizes.get(progressive.sizes.size() - 1);
        } else {
            file.size = size.size > 0 ? size.size : fallbackSize;
        }
    }

    private static ExportData.Image parseMaxImage(TLRPC.Photo photo, String suggestedPath) {
        ExportData.Image result = new ExportData.Image();
        result.file.suggestedPath = suggestedPath;
        TLRPC.PhotoSize best = null;
        long maxArea = 0;
        for (TLRPC.PhotoSize size : photo.sizes) {
            if (isRealPhotoSize(size)) {
                long area = (long) size.w * size.h;
                if (area > maxArea) {
                    maxArea = area;
                    best = size;
                }
            }
        }
        if (best == null) {
            return result;
        }
        result.width = best.w;
        result.height = best.h;
        TLRPC.TL_inputPhotoFileLocation location = new TLRPC.TL_inputPhotoFileLocation();
        location.id = photo.id;
        location.access_hash = photo.access_hash;
        location.file_reference = photo.file_reference;
        location.thumb_size = best.type;
        result.file.location = new ExportData.FileLocation();
        result.file.location.dcId = photo.dc_id;
        result.file.location.data = location;
        fillImageFile(result.file, best, 0);
        return result;
    }

    public static ExportData.Document parseDocument(ExportData.ParseMediaContext context, TLRPC.Document data, String suggestedFolder, int date) {
        ExportData.Document result = new ExportData.Document();
        if (!(data instanceof TLRPC.TL_document)) {
            if (data != null) {
                result.id = data.id;
            }
            return result;
        }
        result.id = data.id;
        result.date = data.date;
        result.mime = nonNull(data.mime_type);
        parseAttributes(result, data.attributes);

        result.file.size = data.size;
        TLRPC.TL_inputDocumentFileLocation location = new TLRPC.TL_inputDocumentFileLocation();
        location.id = data.id;
        location.access_hash = data.access_hash;
        location.file_reference = data.file_reference;
        location.thumb_size = "";
        result.file.location = new ExportData.FileLocation();
        result.file.location.dcId = data.dc_id;
        result.file.location.data = location;
        result.file.suggestedPath = suggestedFolder
                + documentFolder(result) + "/"
                + ExportFiles.fileNameFromUserString(computeDocumentName(context, result, date));
        result.thumb = parseDocumentThumb(data, result.file.suggestedPath);
        return result;
    }

    private static void parseAttributes(ExportData.Document result, List<TLRPC.DocumentAttribute> attributes) {
        for (TLRPC.DocumentAttribute attribute : attributes) {
            if (attribute instanceof TLRPC.TL_documentAttributeImageSize) {
                result.width = attribute.w;
                result.height = attribute.h;
            } else if (attribute instanceof TLRPC.TL_documentAttributeAnimated) {
                result.isAnimated = true;
            } else if (attribute instanceof TLRPC.TL_documentAttributeSticker || attribute instanceof TLRPC.TL_documentAttributeCustomEmoji) {
                result.isSticker = true;
                result.stickerEmoji = nonNull(attribute.alt);
            } else if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                if (attribute.round_message) {
                    result.isVideoMessage = true;
                } else {
                    result.isVideoFile = true;
                }
                result.width = attribute.w;
                result.height = attribute.h;
                result.duration = (int) attribute.duration;
            } else if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                if (attribute.voice) {
                    result.isVoiceMessage = true;
                } else {
                    result.isAudioFile = true;
                }
                result.songPerformer = nonNull(attribute.performer);
                result.songTitle = nonNull(attribute.title);
                result.duration = (int) attribute.duration;
            } else if (attribute instanceof TLRPC.TL_documentAttributeFilename) {
                result.name = nonNull(attribute.file_name);
            }
        }
        if (result.isSticker && result.isVideoFile) {
            result.isVideoFile = false;
        }
    }

    private static String extensionForMime(String mime) {
        String lower = mime.toLowerCase(Locale.US);
        switch (lower) {
            case "application/x-tgsticker":
                return ".tgs";
            case "application/x-tgwallpattern":
                return ".tgv";
            case "application/x-tdesktop-theme":
            case "application/x-tgtheme-tdesktop":
                return ".tdesktop-theme";
            case "application/x-tdesktop-palette":
                return ".tdesktop-palette";
            case "audio/ogg":
                return ".ogg";
            case "image/jpeg":
                return ".jpg";
        }
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(lower);
        return TextUtils.isEmpty(extension) ? "" : "." + extension;
    }

    private static String computeDocumentName(ExportData.ParseMediaContext context, ExportData.Document data, int date) {
        if (!data.name.isEmpty()) {
            return data.name;
        }
        String extension = extensionForMime(data.mime);
        if (data.isVoiceMessage) {
            boolean isMp3 = "audio/mp3".equalsIgnoreCase(data.mime);
            return "audio_" + (++context.audios) + ExportFormat.fileNameDatePart(date) + (isMp3 ? ".mp3" : ".ogg");
        } else if (data.isVideoFile) {
            return "video_" + (++context.videos) + ExportFormat.fileNameDatePart(date) + (extension.isEmpty() ? ".mov" : extension);
        }
        return "file_" + (++context.files) + ExportFormat.fileNameDatePart(date) + (extension.isEmpty() ? ".unknown" : extension);
    }

    private static String documentFolder(ExportData.Document data) {
        if (data.isVideoFile) {
            return "video_files";
        } else if (data.isAnimated) {
            return "animations";
        } else if (data.isSticker) {
            return "stickers";
        } else if (data.isVoiceMessage) {
            return "voice_messages";
        } else if (data.isVideoMessage) {
            return "round_video_messages";
        }
        return "files";
    }

    private static ExportData.Image parseDocumentThumb(TLRPC.Document document, String documentPath) {
        TLRPC.PhotoSize best = null;
        long maxArea = -1;
        for (TLRPC.PhotoSize size : document.thumbs) {
            long area = isRealPhotoSize(size) ? (long) size.w * size.h : 0;
            if (area > maxArea) {
                maxArea = area;
                best = size;
            }
        }
        ExportData.Image result = new ExportData.Image();
        if (!isRealPhotoSize(best)
                || (best instanceof TLRPC.TL_photoSizeProgressive progressive && progressive.sizes.isEmpty())) {
            return result;
        }
        result.width = best.w;
        result.height = best.h;
        TLRPC.TL_inputDocumentFileLocation location = new TLRPC.TL_inputDocumentFileLocation();
        location.id = document.id;
        location.access_hash = document.access_hash;
        location.file_reference = document.file_reference;
        location.thumb_size = best.type;
        result.file.location = new ExportData.FileLocation();
        result.file.location.dcId = document.dc_id;
        result.file.location.data = location;
        fillImageFile(result.file, best, 0);
        result.file.suggestedPath = documentPath + "_thumb.jpg";
        return result;
    }

    private static String photoFileName(int index, int date) {
        return "photo_" + index + ExportFormat.fileNameDatePart(date) + ".jpg";
    }

    private static String storyFileName(int index, int date, String extension) {
        return "story_" + index + ExportFormat.fileNameDatePart(date) + extension;
    }

    public static ExportData.Message parseMessage(ExportData.ParseMediaContext context, TLRPC.Message data, String mediaFolder) {
        ExportData.Message result = new ExportData.Message();
        result.id = data.id;
        if (data instanceof TLRPC.TL_messageEmpty) {
            return result;
        }
        result.date = data.date;
        result.out = data.out;
        result.selfId = context.selfPeerId;
        result.peerId = MessageObject.getPeerId(data.peer_id);
        result.fromId = data.from_id != null ? MessageObject.getPeerId(data.from_id) : result.peerId;
        if (data.reply_to instanceof TLRPC.TL_messageReplyHeader header && header.reply_to_msg_id != 0) {
            result.replyToMsgId = header.reply_to_msg_id;
            result.replyToPeerId = header.reply_to_peer_id != null ? MessageObject.getPeerId(header.reply_to_peer_id) : 0;
            if (result.replyToPeerId == result.peerId) {
                result.replyToPeerId = 0;
            }
        }
        if (data.reactions != null) {
            result.reactions = parseReactions(data.reactions);
        }
        if (data instanceof TLRPC.TL_messageService) {
            result.action = data.action;
            if (data.action instanceof TLRPC.TL_messageActionChatEditPhoto || data.action instanceof TLRPC.TL_messageActionSuggestProfilePhoto) {
                result.actionPhoto = parsePhoto(data.action.photo, mediaFolder + "photos/" + photoFileName(++context.photos, data.date));
            }
            return result;
        }
        result.edited = data.edit_date;
        if (data.fwd_from != null) {
            result.forwardedFromId = data.fwd_from.from_id != null ? MessageObject.getPeerId(data.fwd_from.from_id) : 0;
            result.forwardedFromName = nonNull(data.fwd_from.from_name);
            result.forwardedDate = data.fwd_from.date;
            result.savedFromChatId = data.fwd_from.saved_from_peer != null ? MessageObject.getPeerId(data.fwd_from.saved_from_peer) : 0;
            result.forwarded = result.forwardedFromId != 0 || !result.forwardedFromName.isEmpty();
            result.showForwardedAsOriginal = result.forwarded && result.savedFromChatId != 0;
        }
        result.signature = nonNull(data.post_author);
        result.viaBotId = data.via_bot_id;
        if (data.media != null && !(data.media instanceof TLRPC.TL_messageMediaEmpty)) {
            context.botId = result.viaBotId != 0
                    ? result.viaBotId
                    : result.forwardedFromId > 0 ? result.forwardedFromId : Math.max(result.fromId, 0);
            result.media = parseMedia(context, data.media, mediaFolder, result.date);
            if (result.media.ttl != 0 && !data.out) {
                clearMediaFiles(result.media);
            }
            context.botId = 0;
        }
        if (data.reply_markup instanceof TLRPC.TL_replyInlineMarkup markup) {
            result.inlineButtonRows = parseButtonRows(markup);
        }
        result.text = parseText(data.message, data.entities);
        return result;
    }

    private static void clearMediaFiles(ExportData.Media media) {
        if (media.content instanceof ExportData.Photo photo) {
            photo.image.file = new ExportData.File();
        } else if (media.content instanceof ExportData.Document document) {
            document.file = new ExportData.File();
            document.thumb.file = new ExportData.File();
        }
    }

    private static ExportData.Media parseMedia(ExportData.ParseMediaContext context, TLRPC.MessageMedia data, String folder, int date) {
        ExportData.Media result = new ExportData.Media();
        if (data instanceof TLRPC.TL_messageMediaPhoto) {
            ExportData.Photo photo = data.photo != null
                    ? parsePhoto(data.photo, folder + "photos/" + photoFileName(++context.photos, date))
                    : new ExportData.Photo();
            photo.spoilered = data.spoiler;
            result.ttl = data.ttl_seconds;
            result.content = photo;
        } else if (data instanceof TLRPC.TL_messageMediaDocument) {
            ExportData.Document document = data.document != null
                    ? parseDocument(context, data.document, folder, date)
                    : new ExportData.Document();
            document.spoilered = data.spoiler;
            result.ttl = data.ttl_seconds;
            result.content = document;
        } else if (data instanceof TLRPC.TL_messageMediaContact) {
            ExportData.SharedContact contact = new ExportData.SharedContact();
            contact.info.userId = data.user_id;
            contact.info.firstName = nonNull(data.first_name);
            contact.info.lastName = nonNull(data.last_name);
            contact.info.phoneNumber = nonNull(data.phone_number);
            if (!TextUtils.isEmpty(data.vcard)) {
                contact.vcard.content = data.vcard.getBytes(StandardCharsets.UTF_8);
                contact.vcard.size = contact.vcard.content.length;
                contact.vcard.suggestedPath = folder + "contacts/contact_" + (++context.contacts) + ".vcard";
            }
            result.content = contact;
        } else if (data instanceof TLRPC.TL_messageMediaGeoLive) {
            result.content = parseGeoPoint(data.geo);
            result.ttl = data.period;
        } else if (data instanceof TLRPC.TL_messageMediaVenue) {
            ExportData.Venue venue = new ExportData.Venue();
            venue.point = parseGeoPoint(data.geo);
            venue.title = nonNull(data.title);
            venue.address = nonNull(data.address);
            result.content = venue;
        } else if (data instanceof TLRPC.TL_messageMediaGeo) {
            result.content = parseGeoPoint(data.geo);
        } else if (data instanceof TLRPC.TL_messageMediaGame) {
            ExportData.Game game = new ExportData.Game();
            if (data.game != null) {
                game.title = nonNull(data.game.title);
                game.description = nonNull(data.game.description);
                game.shortName = nonNull(data.game.short_name);
            }
            game.botId = context.botId;
            result.content = game;
        } else if (data instanceof TLRPC.TL_messageMediaInvoice) {
            ExportData.Invoice invoice = new ExportData.Invoice();
            invoice.title = nonNull(data.title);
            invoice.description = nonNull(data.description);
            invoice.currency = nonNull(data.currency);
            invoice.amount = data.total_amount;
            invoice.receiptMsgId = data.receipt_msg_id;
            result.content = invoice;
        } else if (data instanceof TLRPC.TL_messageMediaPoll pollMedia) {
            result.content = parsePoll(pollMedia);
        } else if (data instanceof TLRPC.TL_messageMediaGiveaway giveaway) {
            result.content = parseGiveaway(giveaway);
        } else if (data instanceof TLRPC.TL_messageMediaGiveawayResults giveawayResults) {
            result.content = parseGiveawayResults(giveawayResults);
        } else if (data instanceof TLRPC.TL_messageMediaToDo todo) {
            result.content = parseTodoList(todo);
        } else if (data instanceof TLRPC.TL_messageMediaDice dice) {
            ExportData.Dice content = new ExportData.Dice();
            content.emoticon = nonNull(dice.emoticon);
            content.value = dice.value;
            result.content = content;
        } else if (data instanceof TLRPC.TL_messageMediaStory story) {
            ExportData.StoryReference content = new ExportData.StoryReference();
            content.peerId = story.peer != null ? MessageObject.getPeerId(story.peer) : 0;
            content.storyId = story.id;
            result.content = content;
        } else if (data instanceof TLRPC.TL_messageMediaPaidMedia paidMedia) {
            ExportData.PaidMedia paid = new ExportData.PaidMedia();
            paid.stars = paidMedia.stars_amount;
            result.content = paid;
        } else if (data instanceof TLRPC.TL_messageMediaUnsupported) {
            result.content = new ExportData.UnsupportedMedia();
        }
        return result;
    }

    private static ExportData.Poll parsePoll(TLRPC.TL_messageMediaPoll data) {
        ExportData.Poll result = new ExportData.Poll();
        if (data.poll == null) {
            return result;
        }
        result.question = parseText(data.poll.question);
        result.closed = data.poll.closed;
        HashMap<String, TLRPC.PollAnswerVoters> votes = new HashMap<>();
        if (data.results != null && data.results.results != null) {
            result.totalVotes = data.results.total_voters;
            for (TLRPC.PollAnswerVoters voters : data.results.results) {
                votes.put(Base64.encodeToString(voters.option, Base64.NO_WRAP), voters);
            }
        }
        for (TLRPC.PollAnswer answer : data.poll.answers) {
            TLRPC.PollAnswerVoters voters = answer.option != null ? votes.get(Base64.encodeToString(answer.option, Base64.NO_WRAP)) : null;
            result.answers.add(new ExportData.Poll.Answer(parseText(answer.text), voters != null ? voters.voters : 0, voters != null && voters.chosen));
        }
        return result;
    }

    private static ExportData.TodoList parseTodoList(TLRPC.TL_messageMediaToDo data) {
        ExportData.TodoList result = new ExportData.TodoList();
        if (data.todo != null) {
            result.title = parseText(data.todo.title);
            result.othersCanAppend = data.todo.others_can_append;
            result.othersCanComplete = data.todo.others_can_complete;
            for (TLRPC.TodoItem item : data.todo.list) {
                result.items.add(new ExportData.TodoList.Item(item.id, parseText(item.title)));
            }
        }
        for (TLRPC.TodoCompletion completion : data.completions) {
            long peerId = completion.completed_by != null ? MessageObject.getPeerId(completion.completed_by) : 0;
            result.completions.put(completion.id, new ExportData.TodoList.Completion(peerId, completion.date));
        }
        return result;
    }

    private static ExportData.GiveawayStart parseGiveaway(TLRPC.TL_messageMediaGiveaway data) {
        ExportData.GiveawayStart result = new ExportData.GiveawayStart();
        result.untilDate = data.until_date;
        result.credits = data.stars;
        result.quantity = data.quantity;
        result.months = data.months;
        result.all = !data.only_new_subscribers;
        result.channels.addAll(data.channels);
        result.countries.addAll(data.countries_iso2);
        result.additionalPrize = nonNull(data.prize_description);
        return result;
    }

    private static ExportData.GiveawayResults parseGiveawayResults(TLRPC.TL_messageMediaGiveawayResults data) {
        ExportData.GiveawayResults result = new ExportData.GiveawayResults();
        result.channel = data.channel_id;
        result.untilDate = data.until_date;
        result.launchId = data.launch_msg_id;
        result.additionalPeersCount = data.additional_peers_count;
        result.winnersCount = data.winners_count;
        result.unclaimedCount = data.unclaimed_count;
        result.months = data.months;
        result.credits = data.stars;
        result.refunded = data.refunded;
        result.all = !data.only_new_subscribers;
        result.winners.addAll(data.winners);
        result.additionalPrize = nonNull(data.prize_description);
        return result;
    }

    private static ExportData.GeoPoint parseGeoPoint(TLRPC.GeoPoint data) {
        ExportData.GeoPoint result = new ExportData.GeoPoint();
        if (data instanceof TLRPC.TL_geoPoint) {
            result.latitude = data.lat;
            result.longitude = data._long;
            result.valid = true;
        }
        return result;
    }

    private static ExportData.Reaction parseReaction(TLRPC.Reaction data) {
        ExportData.Reaction result = new ExportData.Reaction();
        if (data instanceof TLRPC.TL_reactionEmoji emoji) {
            result.type = ExportData.Reaction.Type.Emoji;
            result.emoji = nonNull(emoji.emoticon);
        } else if (data instanceof TLRPC.TL_reactionCustomEmoji customEmoji) {
            result.type = ExportData.Reaction.Type.CustomEmoji;
            result.documentId = String.valueOf(customEmoji.document_id);
        } else if (data instanceof TLRPC.TL_reactionPaid) {
            result.type = ExportData.Reaction.Type.Paid;
        }
        return result;
    }

    private static ArrayList<ExportData.Reaction> parseReactions(TLRPC.MessageReactions data) {
        LinkedHashMap<String, ExportData.Reaction> reactions = new LinkedHashMap<>();
        for (TLRPC.ReactionCount count : data.results) {
            ExportData.Reaction reaction = parseReaction(count.reaction);
            reaction.count = count.count;
            reactions.putIfAbsent(reaction.id(), reaction);
        }
        for (TLRPC.MessagePeerReaction recent : data.recent_reactions) {
            ExportData.Reaction parsed = parseReaction(recent.reaction);
            ExportData.Reaction reaction = reactions.get(parsed.id());
            if (reaction == null) {
                reactions.put(parsed.id(), reaction = parsed);
            }
            reaction.recent.add(new ExportData.Reaction.Recent(MessageObject.getPeerId(recent.peer_id), recent.date));
        }
        return new ArrayList<>(reactions.values());
    }

    private static byte[] utf8(String value) {
        return value != null ? value.getBytes(StandardCharsets.UTF_8) : null;
    }

    private static ArrayList<ArrayList<ExportData.InlineButton>> parseButtonRows(TLRPC.TL_replyInlineMarkup markup) {
        ArrayList<ArrayList<ExportData.InlineButton>> rows = new ArrayList<>();
        for (TL_keyboard.KeyboardInlineButtonRow tlRow : markup.rows) {
            ArrayList<ExportData.InlineButton> row = new ArrayList<>();
            for (TL_keyboard.KeyboardInlineButton button : tlRow.buttons) {
                ExportData.InlineButton parsed = parseButton(button.getText(), button.getType());
                if (parsed != null) {
                    row.add(parsed);
                }
            }
            if (!row.isEmpty()) {
                rows.add(row);
            }
        }
        return rows;
    }

    private static ExportData.InlineButton parseButton(String text, TL_keyboard.ButtonTypeProto type) {
        if (type instanceof TL_keyboard.TL_buttonTypeDefault) {
            return new ExportData.InlineButton(ExportData.InlineButton.Type.Default, text, null, null, 0);
        } else if (type instanceof TL_keyboard.TL_inlineButtonTypeCallback callback) {
            return new ExportData.InlineButton(callback.requires_password
                    ? ExportData.InlineButton.Type.CallbackWithPassword
                    : ExportData.InlineButton.Type.Callback, text, callback.data, null, 0);
        } else if (type instanceof TL_keyboard.TL_buttonTypeRequestGeoLocation) {
            return new ExportData.InlineButton(ExportData.InlineButton.Type.RequestLocation, text, null, null, 0);
        } else if (type instanceof TL_keyboard.TL_buttonTypeRequestPhone) {
            return new ExportData.InlineButton(ExportData.InlineButton.Type.RequestPhone, text, null, null, 0);
        } else if (type instanceof TL_keyboard.TL_buttonTypeRequestPeer requestPeer) {
            return new ExportData.InlineButton(ExportData.InlineButton.Type.RequestPeer, text, null, null, requestPeer.button_id);
        } else if (type instanceof TL_keyboard.TL_inlineButtonTypeUrl url) {
            return new ExportData.InlineButton(ExportData.InlineButton.Type.Url, text, utf8(url.url), null, 0);
        } else if (type instanceof TL_keyboard.TL_inlineButtonTypeSwitchInline switchInline) {
            return new ExportData.InlineButton(switchInline.same_peer
                    ? ExportData.InlineButton.Type.SwitchInlineSame
                    : ExportData.InlineButton.Type.SwitchInline, text, utf8(switchInline.query), null, 0);
        } else if (type instanceof TL_keyboard.TL_inlineButtonTypeGame) {
            return new ExportData.InlineButton(ExportData.InlineButton.Type.Game, text, null, null, 0);
        } else if (type instanceof TL_keyboard.TL_inlineButtonTypeBuy) {
            return new ExportData.InlineButton(ExportData.InlineButton.Type.Buy, text, null, null, 0);
        } else if (type instanceof TL_keyboard.TL_inlineButtonTypeUrlAuth urlAuth) {
            return new ExportData.InlineButton(ExportData.InlineButton.Type.Auth, text, utf8(urlAuth.url), urlAuth.fwd_text, urlAuth.button_id);
        } else if (type instanceof TL_keyboard.TL_buttonTypeRequestPoll requestPoll) {
            return new ExportData.InlineButton(ExportData.InlineButton.Type.RequestPoll, text, requestPoll.quiz ? new byte[]{1} : null, null, 0);
        } else if (type instanceof TL_keyboard.TL_inlineButtonTypeUserProfile userProfile) {
            return new ExportData.InlineButton(ExportData.InlineButton.Type.UserProfile, text, utf8(String.valueOf(userProfile.user_id)), null, 0);
        } else if (type instanceof TL_keyboard.TL_inlineButtonTypeWebView webView) {
            return new ExportData.InlineButton(ExportData.InlineButton.Type.WebView, text, utf8(webView.url), null, 0);
        } else if (type instanceof TL_keyboard.TL_buttonTypeSimpleWebView simpleWebView) {
            return new ExportData.InlineButton(ExportData.InlineButton.Type.SimpleWebView, text, utf8(simpleWebView.url), null, 0);
        } else if (type instanceof TL_keyboard.TL_inlineButtonTypeCopy copy) {
            return new ExportData.InlineButton(ExportData.InlineButton.Type.CopyText, text, utf8(copy.copy_text), null, 0);
        }
        return null;
    }

    public static ExportData.MessagesSlice parseMessagesSlice(ExportData.ParseMediaContext context, TLRPC.messages_Messages data, String mediaFolder) {
        ExportData.MessagesSlice result = new ExportData.MessagesSlice();
        for (int i = data.messages.size() - 1; i >= 0; i--) {
            result.list.add(parseMessage(context, data.messages.get(i), mediaFolder));
        }
        result.peers = parsePeersLists(data.users, data.chats);
        return result;
    }

    public static ExportData.MessagesSlice adjustMigrateMessageIds(ExportData.MessagesSlice slice) {
        for (ExportData.Message message : slice.list) {
            message.id += MIGRATED_MESSAGES_ID_SHIFT;
            if (message.replyToMsgId != 0 && message.replyToPeerId == 0) {
                message.replyToMsgId += MIGRATED_MESSAGES_ID_SHIFT;
            }
        }
        return slice;
    }

    public static ArrayList<ExportData.Message> parseProfileMusicSlice(ExportData.ParseMediaContext context, List<TLRPC.Document> documents, int baseIndex) {
        ArrayList<ExportData.Message> result = new ArrayList<>();
        for (TLRPC.Document document : documents) {
            ExportData.Message message = new ExportData.Message();
            message.id = ++baseIndex;
            ExportData.Document parsed = parseDocument(context, document, "profile_music/", 0);
            message.media.content = parsed;
            if (parsed.isAudioFile) {
                result.add(message);
            }
        }
        return result;
    }

    public static ArrayList<ExportData.Photo> parseUserpicsSlice(List<TLRPC.Photo> data, int baseIndex) {
        ArrayList<ExportData.Photo> result = new ArrayList<>(data.size());
        for (TLRPC.Photo photo : data) {
            result.add(parsePhoto(photo, "profile_pictures/" + photoFileName(++baseIndex, photo.date)));
        }
        return result;
    }

    public static ExportData.StoriesSlice parseStoriesSlice(List<TL_stories.StoryItem> data, int baseIndex) {
        ExportData.StoriesSlice result = new ExportData.StoriesSlice();
        for (TL_stories.StoryItem story : data) {
            result.lastId = story.id;
            int date = story.date;
            ExportData.Media media = new ExportData.Media();
            if (story.media instanceof TLRPC.TL_messageMediaPhoto) {
                ExportData.Photo photo = story.media.photo != null
                        ? parsePhoto(story.media.photo, "stories/" + storyFileName(++baseIndex, date, ".jpg"))
                        : new ExportData.Photo();
                photo.spoilered = story.media.spoiler;
                media.content = photo;
            } else if (story.media instanceof TLRPC.TL_messageMediaDocument && story.media.document != null) {
                ExportData.Document document = parseDocument(new ExportData.ParseMediaContext(), story.media.document, "stories/", date);
                String extension = extensionForMime(document.mime);
                document.file.suggestedPath = "stories/" + storyFileName(++baseIndex, date, extension.isEmpty() ? ".mp4" : extension);
                document.thumb.file.suggestedPath = document.file.suggestedPath + "_thumb.jpg";
                document.spoilered = story.media.spoiler;
                media.content = document;
            } else {
                result.skipped++;
                continue;
            }
            ExportData.Story parsed = new ExportData.Story();
            parsed.id = story.id;
            parsed.date = date;
            parsed.expires = story.expire_date;
            parsed.media = media;
            parsed.pinned = story.pinned;
            parsed.caption = parseText(story.caption, story.entities);
            result.list.add(parsed);
        }
        return result;
    }

    public static ExportData.ContactsList parseContactsList(List<TakeoutRequests.TL_savedPhoneContact> data) {
        ExportData.ContactsList result = new ExportData.ContactsList();
        for (TakeoutRequests.TL_savedPhoneContact contact : data) {
            ExportData.ContactInfo info = new ExportData.ContactInfo();
            info.firstName = nonNull(contact.first_name);
            info.lastName = nonNull(contact.last_name);
            info.phoneNumber = nonNull(contact.phone);
            info.date = contact.date;
            info.colorIndex = peerColorIndex(stringBarePeerId(info.phoneNumber));
            result.list.add(info);
        }
        return result;
    }

    public static boolean appendTopPeers(ExportData.ContactsList to, TLRPC.contacts_TopPeers data) {
        if (data instanceof TLRPC.TL_contacts_topPeersDisabled) {
            return true;
        }
        if (!(data instanceof TLRPC.TL_contacts_topPeers topPeers)) {
            return false;
        }
        HashMap<Long, ExportData.Peer> peers = parsePeersLists(topPeers.users, topPeers.chats);
        for (TLRPC.TL_topPeerCategoryPeers category : topPeers.categories) {
            ArrayList<ExportData.TopPeer> list;
            if (category.category instanceof TLRPC.TL_topPeerCategoryCorrespondents) {
                list = to.correspondents;
            } else if (category.category instanceof TLRPC.TL_topPeerCategoryBotsInline) {
                list = to.inlineBots;
            } else if (category.category instanceof TLRPC.TL_topPeerCategoryPhoneCalls) {
                list = to.phoneCalls;
            } else {
                continue;
            }
            for (TLRPC.TL_topPeer topPeer : category.peers) {
                long peerId = MessageObject.getPeerId(topPeer.peer);
                ExportData.TopPeer parsed = new ExportData.TopPeer();
                ExportData.Peer peer = peers.get(peerId);
                parsed.peer = peer != null ? peer : emptyPeer(peerId);
                parsed.rating = topPeer.rating;
                list.add(parsed);
            }
        }
        return true;
    }

    public static ArrayList<Integer> sortedContactsIndices(ExportData.ContactsList data) {
        ArrayList<String> names = new ArrayList<>(data.list.size());
        ArrayList<Integer> indices = new ArrayList<>(data.list.size());
        for (int i = 0; i < data.list.size(); i++) {
            ExportData.ContactInfo info = data.list.get(i);
            names.add((info.firstName + " " + info.lastName).toLowerCase(Locale.ROOT));
            indices.add(i);
        }
        indices.sort(Comparator.comparing(names::get));
        return indices;
    }

    public static ExportData.SessionsList parseSessionsList(TL_account.authorizations sessions, TL_account.webAuthorizations webSessions) {
        ExportData.SessionsList result = new ExportData.SessionsList();
        result.list.addAll(sessions.authorizations);
        if (webSessions != null) {
            HashMap<Long, ExportData.Peer> users = parsePeersLists(webSessions.users, null);
            for (TLRPC.TL_webAuthorization session : webSessions.authorizations) {
                ExportData.Peer bot = users.get(session.bot_id);
                result.webList.add(new ExportData.WebSession(
                        bot != null && bot.user != null ? bot.user.username : "",
                        nonNull(session.domain),
                        nonNull(session.browser),
                        nonNull(session.platform),
                        session.date_created,
                        session.date_active,
                        nonNull(session.ip),
                        nonNull(session.region)));
            }
        }
        return result;
    }

    public static int singleMessageDate(TLRPC.messages_Messages data) {
        if (data == null || data.messages.isEmpty() || data.messages.get(0) instanceof TLRPC.TL_messageEmpty) {
            return 0;
        }
        return data.messages.get(0).date;
    }

    public static boolean singleMessageAfter(TLRPC.messages_Messages data, int date) {
        int single = singleMessageDate(data);
        return single > 0 && single > date;
    }

    public static boolean singleMessageBefore(TLRPC.messages_Messages data, int date) {
        int single = singleMessageDate(data);
        return single > 0 && single < date;
    }

    public static boolean skipMessageByDate(ExportData.Message message, ExportSettings settings) {
        boolean goodFrom = settings.singlePeerFrom <= 0 || settings.singlePeerFrom <= message.date;
        boolean goodTill = settings.singlePeerTill <= 0 || message.date < settings.singlePeerTill;
        return !goodFrom || !goodTill;
    }

    public static boolean refreshFileReference(ExportData.FileLocation to, ExportData.FileLocation from) {
        if (to == null || from == null || to.data == null || from.data == null
                || to.dcId != from.dcId || to.data.getClass() != from.data.getClass()) {
            return false;
        }
        if (to.data instanceof TLRPC.TL_inputPhotoFileLocation || to.data instanceof TLRPC.TL_inputDocumentFileLocation) {
            if (to.data.id != from.data.id || !Objects.equals(to.data.thumb_size, from.data.thumb_size)) {
                return false;
            }
            to.data.file_reference = from.data.file_reference;
            return true;
        }
        return false;
    }

    public static String computeLocationKey(ExportData.FileLocation location) {
        if (location.data instanceof TLRPC.TL_inputDocumentFileLocation) {
            return location.dcId + "_doc_" + location.data.id + "_" + location.data.thumb_size;
        } else if (location.data instanceof TLRPC.TL_inputPhotoFileLocation) {
            return location.dcId + "_photo_" + location.data.id + "_" + location.data.thumb_size;
        } else if (location.data instanceof TakeoutRequests.TL_inputTakeoutFileLocation) {
            return location.dcId + "_takeout";
        }
        return null;
    }
}
