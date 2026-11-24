/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.chats;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Pair;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.utils.network.RemoteUtils;
import com.exteragram.messenger.utils.system.SystemUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.WebFile;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChannelAdminLogActivity;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Components.TranscribeButton;
import org.telegram.ui.ProfileActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

public class ChatUtils {

    public static final DispatchQueue utilsQueue = new DispatchQueue("utilsQueue");
    private static final ChatUtils[] Instance = new ChatUtils[UserConfig.MAX_ACCOUNT_COUNT];
    private static final Object[] lockObjects = new Object[UserConfig.MAX_ACCOUNT_COUNT];
    private static final CharsetDecoder textDecoder = StandardCharsets.UTF_8.newDecoder();
    private static SpannableStringBuilder editedIcon, channelIcon;
    private static final int MESSAGE_FLAG_HAS_REACTIONS = TLObject.FLAG_20;

    static {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            lockObjects[i] = new Object();
        }
    }

    private final int selectedAccount;

    public ChatUtils(int num) {
        selectedAccount = num;
    }

    public static CharSequence getEditedIcon() {
        if (editedIcon == null) {
            editedIcon = new SpannableStringBuilder("\u200D");
            var pencil = new ColoredImageSpan(Theme.chat_pencilIconDrawable);
            pencil.setTranslateX(-AndroidUtilities.dp(1));
            editedIcon.setSpan(pencil, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return editedIcon;
    }

    public static CharSequence getChannelIcon() {
        if (channelIcon == null) {
            channelIcon = new SpannableStringBuilder("\u200D");
            channelIcon.setSpan(new ColoredImageSpan(Theme.chat_channelIconDrawable), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return channelIcon;
    }

    public static boolean hasRestrictionReason(ArrayList<TLRPC.RestrictionReason> reasons, String reasonKey) {
        if (reasons == null || TextUtils.isEmpty(reasonKey)) {
            return false;
        }

        for (int i = 0; i < reasons.size(); ++i) {
            TLRPC.RestrictionReason reason = reasons.get(i);
            if (reason != null && reasonKey.equals(reason.reason)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isTermsRestrictedMessage(MessageObject messageObject) {
        return messageObject != null && messageObject.messageOwner != null && hasRestrictionReason(messageObject.messageOwner.restriction_reason, "terms");
    }

    public static void moveSelfToTop(ArrayList<TLObject> peers) {
        for (var i = 1; i < peers.size(); i++) {
            if (peers.get(i) instanceof TLRPC.User user && UserObject.isUserSelf(user)) {
                peers.add(0, peers.remove(i));
                return;
            }
        }
    }

    public static boolean applyChannelPostContent(TLRPC.Message groupCopy, TLRPC.Message channelPost) {
        if (groupCopy == null || channelPost == null || groupCopy.fwd_from == null ||
                groupCopy.fwd_from.saved_from_msg_id != channelPost.id ||
                !MessageObject.peersEqual(groupCopy.fwd_from.saved_from_peer, channelPost.peer_id)) {
            return false;
        }
        boolean changed = reactionsChanged(groupCopy.reactions, channelPost.reactions) ||
                !TextUtils.equals(groupCopy.message, channelPost.message) ||
                groupCopy.edit_date != channelPost.edit_date ||
                groupCopy.edit_hide != channelPost.edit_hide ||
                mediaChanged(groupCopy.media, channelPost.media) ||
                entitiesChanged(groupCopy.entities, channelPost.entities);

        groupCopy.reactions = channelPost.reactions;
        groupCopy.message = channelPost.message;
        groupCopy.media = channelPost.media;
        groupCopy.entities = channelPost.entities;
        groupCopy.edit_date = channelPost.edit_date;
        groupCopy.edit_hide = channelPost.edit_hide;
        groupCopy.flags = (groupCopy.flags & ~(TLRPC.MESSAGE_FLAG_HAS_MEDIA | TLRPC.MESSAGE_FLAG_HAS_ENTITIES | TLRPC.MESSAGE_FLAG_EDITED | MESSAGE_FLAG_HAS_REACTIONS)) |
                (groupCopy.media != null ? TLRPC.MESSAGE_FLAG_HAS_MEDIA : 0) |
                (groupCopy.entities != null && !groupCopy.entities.isEmpty() ? TLRPC.MESSAGE_FLAG_HAS_ENTITIES : 0) |
                (groupCopy.edit_date != 0 ? TLRPC.MESSAGE_FLAG_EDITED : 0) |
                (groupCopy.reactions != null ? MESSAGE_FLAG_HAS_REACTIONS : 0);
        return changed;
    }

    private static boolean reactionsChanged(TLRPC.MessageReactions oldReactions, TLRPC.MessageReactions newReactions) {
        if (oldReactions == newReactions) {
            return false;
        }
        if (oldReactions == null || newReactions == null) {
            return true;
        }
        int size = oldReactions.results == null ? 0 : oldReactions.results.size();
        if (size != (newReactions.results == null ? 0 : newReactions.results.size())) {
            return true;
        }
        for (int i = 0; i < size; i++) {
            TLRPC.ReactionCount oldCount = oldReactions.results.get(i);
            TLRPC.ReactionCount newCount = newReactions.results.get(i);
            if (oldCount == null || newCount == null) {
                if (oldCount != newCount) {
                    return true;
                }
                continue;
            }
            if (oldCount.count != newCount.count || oldCount.chosen_order != newCount.chosen_order ||
                    !ReactionsLayoutInBubble.equalsTLReaction(oldCount.reaction, newCount.reaction)) {
                return true;
            }
        }
        return false;
    }

    private static boolean mediaChanged(TLRPC.MessageMedia oldMedia, TLRPC.MessageMedia newMedia) {
        if (oldMedia == newMedia) {
            return false;
        }
        if (oldMedia == null || newMedia == null || oldMedia.getClass() != newMedia.getClass()) {
            return true;
        }
        return mediaIdentity(oldMedia) != mediaIdentity(newMedia);
    }

    private static long mediaIdentity(TLRPC.MessageMedia media) {
        if (media.photo != null) {
            return media.photo.id;
        }
        if (media.document != null) {
            return media.document.id;
        }
        if (media.webpage != null) {
            return media.webpage.id;
        }
        if (media.game != null) {
            return media.game.id;
        }
        return media.id;
    }

    private static boolean entitiesChanged(ArrayList<TLRPC.MessageEntity> oldEntities, ArrayList<TLRPC.MessageEntity> newEntities) {
        if (oldEntities == newEntities) {
            return false;
        }
        int size = oldEntities == null ? 0 : oldEntities.size();
        if (size != (newEntities == null ? 0 : newEntities.size())) {
            return true;
        }
        for (int i = 0; i < size; i++) {
            TLRPC.MessageEntity oldEntity = oldEntities.get(i);
            TLRPC.MessageEntity newEntity = newEntities.get(i);
            if (oldEntity == null || newEntity == null) {
                if (oldEntity != newEntity) {
                    return true;
                }
                continue;
            }
            if (oldEntity.getClass() != newEntity.getClass() || oldEntity.offset != newEntity.offset ||
                    oldEntity.length != newEntity.length || !TextUtils.equals(oldEntity.url, newEntity.url)) {
                return true;
            }
        }
        return false;
    }

    public static ChatUtils getInstance() {
        return getInstance(UserConfig.selectedAccount);
    }

    public static ChatUtils getInstance(int num) {
        ChatUtils localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (lockObjects) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new ChatUtils(num);
                }
            }
        }
        return localInstance;
    }

    public static String getDCName(int dc) {
        return switch (dc) {
            case 1, 3 -> "Miami FL, USA";
            case 2, 4 -> "Amsterdam, NL";
            case 5 -> "Singapore, SG";
            default -> null;
        };
    }

    // https://github.com/TDesktop-x64/tdesktop/blob/af22eed1e89d579dd9a2e2f01d2dc8c0cf2703b5/Telegram/SourceFiles/boxes/sticker_set_box.cpp#L764
    public static long extractOwnerId(long setId) {
        long ownerId = setId >> 32;

        if (((setId >> 16) & 0xff) == 0x3f) {
            ownerId |= 0x80000000L;
        }

        if (((setId >> 24) & 0xff) != 0) {
            ownerId += 0x100000000L;
        }

        return ownerId;
    }

    private static boolean fileExists(String path) {
        if (TextUtils.isEmpty(path)) {
            return false;
        }
        File file = new File(path);
        return file.exists() && file.isFile();
    }

    public CharSequence getMessageText(MessageObject selectedObject, MessageObject.GroupedMessages selectedObjectGroup) {
        CharSequence messageTextToTranslate = null;
        if (selectedObject.type != MessageObject.TYPE_EMOJIS && selectedObject.type != MessageObject.TYPE_ANIMATED_STICKER && selectedObject.type != MessageObject.TYPE_STICKER) {
            messageTextToTranslate = getMessageCaption(selectedObject, selectedObjectGroup);
            if (messageTextToTranslate == null && selectedObject.isPoll()) {
                try {
                    TLRPC.Poll poll = ((TLRPC.TL_messageMediaPoll) selectedObject.messageOwner.media).poll;
                    StringBuilder pollText;
                    pollText = new StringBuilder(poll.question.text).append("\n");
                    for (TLRPC.PollAnswer answer : poll.answers)
                        pollText.append("\n\uD83D\uDD18 ").append(answer.text == null ? "" : answer.text.text);
                    messageTextToTranslate = pollText.toString();
                } catch (Exception ignored) {
                }
            }
            if (messageTextToTranslate == null && MessageObject.isMediaEmpty(selectedObject.messageOwner)) {
                messageTextToTranslate = getMessageContent(selectedObject);
            }
            if (messageTextToTranslate != null && Emoji.fullyConsistsOfEmojis(messageTextToTranslate)) {
                messageTextToTranslate = null;
            }
        }
        if (selectedObject.translated || selectedObject.isRestrictedMessage) {
            messageTextToTranslate = null;
        }
        return messageTextToTranslate;
    }

    private CharSequence getMessageCaption(MessageObject messageObject, MessageObject.GroupedMessages group) {
        String restrictionReason = MessagesController.getInstance(selectedAccount).getRestrictionReason(messageObject.messageOwner.restriction_reason);
        if (!TextUtils.isEmpty(restrictionReason)) {
            return restrictionReason;
        }
        if (messageObject.isVoiceTranscriptionOpen() && !TranscribeButton.isTranscribing(messageObject)) {
            return messageObject.getVoiceTranscription();
        }
        if (messageObject.caption != null) {
            return messageObject.caption;
        }
        if (group == null) {
            return null;
        }
        CharSequence caption = null;
        for (int a = 0, N = group.messages.size(); a < N; a++) {
            MessageObject message = group.messages.get(a);
            if (message.caption != null) {
                if (caption != null) {
                    return null;
                }
                caption = message.caption;
            }
        }
        return caption;
    }

    private CharSequence getMessageContent(MessageObject messageObject) {
        SpannableStringBuilder str = new SpannableStringBuilder();
        String restrictionReason = MessagesController.getInstance(selectedAccount).getRestrictionReason(messageObject.messageOwner.restriction_reason);
        if (!TextUtils.isEmpty(restrictionReason)) {
            str.append(restrictionReason);
        } else if (messageObject.caption != null) {
            str.append(messageObject.caption);
        } else {
            str.append(messageObject.messageText);
        }
        return str.toString();
    }

    public ArrayList<TLRPC.MessageEntity> copyMessageEntities(ArrayList<TLRPC.MessageEntity> originalEntities) {
        if (originalEntities == null || originalEntities.isEmpty()) {
            return null;
        }
        ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
        for (TLRPC.MessageEntity entity : originalEntities) {
            if (entity instanceof TLRPC.TL_messageEntityMentionName) {
                TLRPC.TL_inputMessageEntityMentionName mention = new TLRPC.TL_inputMessageEntityMentionName();
                mention.length = entity.length;
                mention.offset = entity.offset;
                mention.user_id = getMessagesController().getInputUser(((TLRPC.TL_messageEntityMentionName) entity).user_id);
                entities.add(mention);
            } else {
                entities.add(entity);
            }
        }
        return entities;
    }

    public MessageObject getMessageForRepeat(MessageObject selectedObject, MessageObject.GroupedMessages selectedObjectGroup) {
        MessageObject messageObject = null;
        if (selectedObjectGroup != null && !selectedObjectGroup.isDocuments) {
            messageObject = getTargetMessageObjectFromGroup(selectedObjectGroup);
        } else if (!TextUtils.isEmpty(selectedObject.messageOwner.message) || selectedObject.isAnyKindOfSticker()
                || (selectedObject.messageOwner.media != null
                && !(selectedObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty)
                && !(selectedObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage))) {
            messageObject = selectedObject;
        }
        return messageObject;
    }

    public boolean hasMediaForRepeat(MessageObject selectedObject) {
        return !selectedObject.isAnyKindOfSticker()
                && selectedObject.messageOwner.media != null
                && !(selectedObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty)
                && !(selectedObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage);
    }

    private MessageObject getTargetMessageObjectFromGroup(MessageObject.GroupedMessages selectedObjectGroup) {
        MessageObject messageObject = null;
        for (MessageObject object : selectedObjectGroup.messages) {
            if (!TextUtils.isEmpty(object.messageOwner.message)) {
                if (messageObject != null) {
                    messageObject = null;
                    break;
                } else {
                    messageObject = object;
                }
            }
        }
        return messageObject;
    }

    public String getTextFromCallback(byte[] data) {
        try {
            return textDecoder.decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException e) {
            return Base64.encodeToString(data, Base64.NO_PADDING | Base64.NO_WRAP);
        }
    }

    public long getEmojiIdFrom(MessageObject messageObject, TLRPC.User currentUser) {
        if (messageObject != null && messageObject.messageOwner != null && messageObject.replyMessageObject != null && messageObject.replyMessageObject.messageOwner != null && messageObject.replyMessageObject.messageOwner.from_id != null) {
            if (DialogObject.isEncryptedDialog(messageObject.replyMessageObject.getDialogId())) {
                TLRPC.User user = messageObject.replyMessageObject.isOutOwner() ? getUserConfig().getCurrentUser() : currentUser;
                if (user != null) {
                    return UserObject.getEmojiId(user);
                }
            } else if (messageObject.replyMessageObject.isFromUser()) {
                TLRPC.User user = getMessagesController().getUser(messageObject.replyMessageObject.messageOwner.from_id.user_id);
                if (user != null) {
                    return UserObject.getEmojiId(user);
                }
            } else if (messageObject.replyMessageObject.isFromChannel()) {
                TLRPC.Chat chat = getMessagesController().getChat(messageObject.replyMessageObject.messageOwner.from_id.channel_id);
                if (chat != null) {
                    return ChatObject.getEmojiId(chat);
                }
            }
        }
        return 0;
    }

    public TLRPC.InputStickerSet getSetFrom(MessageObject messageObject, TLRPC.User currentUser) {
        return AnimatedEmojiDrawable.findStickerSet(selectedAccount, getEmojiIdFrom(messageObject, currentUser));
    }

    public TLRPC.InputStickerSet getSetFrom(TLRPC.User user) {
        return AnimatedEmojiDrawable.findStickerSet(selectedAccount, UserObject.getProfileEmojiId(user));
    }

    public TLRPC.InputStickerSet getSetFrom(TLRPC.Chat chat) {
        return AnimatedEmojiDrawable.findStickerSet(selectedAccount, ChatObject.getProfileEmojiId(chat));
    }

    public String getDC(TLRPC.User user) {
        return getDC(user, null);
    }

    public String getDC(TLRPC.Chat chat) {
        return getDC(null, chat);
    }

    private int getUserProfileDC(TLRPC.User user) {
        TLRPC.UserFull userFull = getMessagesController().getUserFull(user.id);
        if (userFull != null && userFull.profile_photo != null && !(userFull.profile_photo instanceof TLRPC.TL_photoEmpty) && userFull.profile_photo.dc_id > 0) {
            return userFull.profile_photo.dc_id;
        }
        if (user.photo != null && user.photo.personal) {
            return -1;
        }
        return user.photo != null ? user.photo.dc_id : -1;
    }

    public String getDC(TLRPC.User user, TLRPC.Chat chat) {
        int DC = 0, myDC = getConnectionsManager().getCurrentDatacenterId();
        if (user != null) {
            if (UserObject.isUserSelf(user) && myDC != -1) {
                DC = myDC;
            } else {
                DC = getUserProfileDC(user);
            }
        } else if (chat != null) {
            DC = chat.photo != null ? chat.photo.dc_id : -1;
        }
        if (DC == -1 || DC == 0) {
            return getDCName(0);
        } else {
            return String.format(Locale.ROOT, "DC%d, %s", DC, getDCName(DC));
        }
    }

    public String getName(long did) {
        String name = null;
        if (DialogObject.isEncryptedDialog(did)) {
            TLRPC.EncryptedChat encryptedChat = getMessagesController().getEncryptedChat(DialogObject.getEncryptedChatId(did));
            if (encryptedChat != null) {
                TLRPC.User user = getMessagesController().getUser(encryptedChat.user_id);
                if (user != null)
                    name = ContactsController.formatName(user.first_name, user.last_name);
            }
        } else if (DialogObject.isUserDialog(did)) {
            TLRPC.User user = getMessagesController().getUser(did);
            if (user != null) name = ContactsController.formatName(user);
        } else {
            TLRPC.Chat chat = getMessagesController().getChat(-did);
            if (chat != null) name = chat.title;
        }
        return did == getUserConfig().getClientUserId() ? LocaleController.getString(R.string.SavedMessages) : name;
    }

    public Runnable searchUserById(Long userId, Utilities.Callback<TLRPC.User> callback) {
        return searchUserById(userId, callback, null);
    }

    public Runnable searchUserById(Long userId, Utilities.Callback<TLRPC.User> callback, Utilities.Callback<Runnable> setCancelRequest) {
        if (userId == 0) {
            return null;
        }
        TLRPC.User user = getMessagesController().getUser(userId);
        if (user != null) {
            callback.run(user);
            return null;
        } else {
            return searchUser(userId, true, true, user1 -> {
                if (user1 != null && user1.access_hash != 0) {
                    callback.run(user1);
                } else {
                    callback.run(null);
                }
            }, setCancelRequest);
        }
    }

    public void refreshStickerSet(TLRPC.StickerSet set, Utilities.Callback<TLRPC.TL_messages_stickerSet> callback) {
        final TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
        req.stickerset = MediaDataController.getInputStickerSet(set);
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.TL_messages_stickerSet stickerSet) {
                MediaDataController.getInstance(selectedAccount).putStickerSet(stickerSet);
                callback.run(stickerSet);
            } else {
                callback.run(null);
            }
        }));
    }

    public void sendBotRequest(String query, boolean cache, Utilities.Callback<String> callback) {
        Pair<Long, String> botInfo = ExteraConfig.getApiBotInfo();
        long botId = botInfo.first;
        String botUsername = botInfo.second;

        TLRPC.User bot = getMessagesController().getUser(botId);
        if (bot != null) {
            sendInlineBotRequest(bot, query, cache, res -> processBotResponse(res, callback));
        } else {
            resolveUser(botUsername, botId, user -> {
                if (user != null) {
                    sendInlineBotRequest(user, query, cache, res -> processBotResponse(res, callback));
                } else {
                    callback.run(null);
                }
            });
        }
    }

    private void processBotResponse(TLRPC.messages_BotResults res, Utilities.Callback<String> callback) {
        if (res != null && !res.results.isEmpty()) {
            TLRPC.BotInlineResult result = res.results.get(0);
            if (result.send_message != null && result.send_message.message != null) {
                callback.run(result.send_message.message);
                return;
            }
        }
        callback.run(null);
    }

    public Runnable sendInlineBotRequest(TLRPC.User bot, String query, boolean cache, Utilities.Callback<TLRPC.messages_BotResults> callback) {
        return sendInlineBotRequest(bot, query, cache, callback, null);
    }

    public Runnable sendInlineBotRequest(TLRPC.User bot, String query, boolean cache, Utilities.Callback<TLRPC.messages_BotResults> callback, Utilities.Callback<Runnable> setCancelRequest) {
        if (bot == null) {
            callback.run(null);
            return null;
        }

        String key = "bot_inline_query_" + bot.id + "_" + query;
        RequestDelegate requestDelegate = (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (cache && (!(response instanceof TLRPC.messages_BotResults) || ((TLRPC.messages_BotResults) response).results.isEmpty())) {
                Runnable cancelRequest = sendInlineBotRequest(bot, query, false, callback);
                if (setCancelRequest != null) {
                    setCancelRequest.run(cancelRequest);
                }
                return;
            }

            if (response instanceof TLRPC.messages_BotResults res) {
                if (!cache && res.cache_time != 0) {
                    getMessageStorage().saveBotCache(key, res);
                }
                callback.run(res);
            } else {
                callback.run(null);
            }
        });

        if (cache) {
            getMessageStorage().getBotCache(key, requestDelegate);
            return null;
        } else {
            TLRPC.TL_messages_getInlineBotResults req = new TLRPC.TL_messages_getInlineBotResults();
            req.query = query;
            req.bot = getMessagesController().getInputUser(bot);
            req.offset = "";
            req.peer = new TLRPC.TL_inputPeerEmpty();
            int requestId = getConnectionsManager().sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors);
            return () -> getConnectionsManager().cancelRequest(requestId, false);
        }
    }

    private static Pair<Long, String> getBotInfo() {
        String botInfo = RemoteUtils.getStringConfigValue(RemoteUtils.SEARCH_BOT, "7424190611:tgdb_search_bot");
        int i = botInfo.indexOf(":");

        if (i != -1) {
            try {
                long id = Long.parseLong(botInfo.substring(0, i));
                String name = botInfo.substring(i + 1);
                return new Pair<>(id, name);
            } catch (NumberFormatException e) {
                FileLog.e(e);
            }
        }

        return new Pair<>(7424190611L, "tgdb_search_bot");
    }

    private Runnable searchUser(long userId, boolean searchUser, boolean cache, Utilities.Callback<TLRPC.User> callback) {
        return searchUser(userId, searchUser, cache, callback, null);
    }

    private Runnable searchUser(long userId, boolean searchUser, boolean cache, Utilities.Callback<TLRPC.User> callback, Utilities.Callback<Runnable> setCancelRequest) {
        var botInfo = getBotInfo();
        final long bot_id = botInfo.first;
        TLRPC.User bot = getMessagesController().getUser(bot_id);
        if (bot == null) {
            if (searchUser) {
                return resolveUser(botInfo.second, bot_id, user -> searchUser(userId, false, false, callback));
            } else {
                callback.run(null);
                return null;
            }
        }

        return sendInlineBotRequest(bot, String.valueOf(userId), cache, res -> {
            if (res == null || res.results.isEmpty()) {
                callback.run(null);
                return;
            }
            TLRPC.BotInlineResult result = res.results.get(0);
            if (result.send_message == null || TextUtils.isEmpty(result.send_message.message)) {
                callback.run(null);
                return;
            }
            String[] lines = result.send_message.message.split("\n");
            if (lines.length < 3) {
                callback.run(null);
                return;
            }
            var user1 = new TLRPC.TL_user();
            for (String line : lines) {
                line = line.replaceAll("\\p{C}", "").trim();
                if (line.startsWith("\uD83C\uDD94")) {
                    user1.id = Utilities.parseLong(line.replaceAll("\\D+", "").trim());
                } else if (line.startsWith("\uD83D\uDCE7")) {
                    user1.username = line.substring(line.indexOf('@') + 1).trim();
                }
            }
            if (user1.id == 0) {
                callback.run(null);
                return;
            }
            if (user1.username != null) {
                resolveUser(user1.username, user1.id, user -> {
                    if (user != null) {
                        callback.run(user);
                    } else {
                        user1.username = null;
                        callback.run(user1);
                    }
                });
            } else {
                callback.run(user1);
            }
        }, setCancelRequest);
    }

    public Runnable resolveUser(String username, long userId, Utilities.Callback<TLRPC.User> callback) {
        return getMessagesController().getUserNameResolver().resolve(username, (peerId) -> {
            if (peerId != null && peerId > 0 && peerId == userId) {
                callback.run(getMessagesController().getUser(userId));
            } else {
                callback.run(null);
            }
        });
    }

    public void searchChatById(long shortId, Utilities.Callback<TLRPC.Chat> callback) {
        if (shortId == 0) {
            callback.run(null);
            return;
        }
        TLRPC.Chat chat = getMessagesController().getChat(shortId);
        if (chat != null) {
            callback.run(chat);
            return;
        }
        long botQueryId;
        try {
            botQueryId = Long.parseLong("-100" + shortId);
        } catch (NumberFormatException e) {
            callback.run(null);
            return;
        }
        searchChat(botQueryId, true, true, callback);
    }

    private void searchChat(long chatId, boolean search, boolean cache, Utilities.Callback<TLRPC.Chat> callback) {
        var botInfo = getBotInfo();
        final long bot_id = botInfo.first;
        TLRPC.User bot = getMessagesController().getUser(bot_id);
        if (bot == null) {
            if (search) {
                resolveUser(botInfo.second, bot_id, user -> searchChat(chatId, false, false, callback));
            } else {
                callback.run(null);
            }
            return;
        }

        sendInlineBotRequest(bot, String.valueOf(chatId), cache, res -> {
            if (res == null || res.results.isEmpty()) {
                callback.run(null);
                return;
            }
            TLRPC.BotInlineResult result = res.results.get(0);
            if (result.send_message == null || TextUtils.isEmpty(result.send_message.message)) {
                callback.run(null);
                return;
            }
            String[] lines = result.send_message.message.split("\n");
            if (lines.length < 1) {
                callback.run(null);
                return;
            }
            var chat1 = new TLRPC.TL_channel();
            for (String line : lines) {
                line = line.replaceAll("\\p{C}", "").trim();
                if (line.startsWith("\uD83C\uDD94")) {
                    try {
                        long parsedId = Utilities.parseLong(line.replaceAll("[^\\d-]", ""));
                        if (String.valueOf(parsedId).startsWith("-100")) {
                            chat1.id = Long.parseLong(String.valueOf(parsedId).substring(4));
                        } else {
                            chat1.id = parsedId;
                        }
                    } catch (Exception ignore) {
                    }
                } else if (line.startsWith("\uD83D\uDCE7")) {
                    chat1.username = line.substring(line.indexOf('@') + 1).trim();
                }
            }
            if (chat1.id == 0) {
                callback.run(null);
                return;
            }
            if (chat1.username != null) {
                resolveChannel(chat1.username, chat -> callback.run(Objects.requireNonNullElse(chat, chat1)));
            } else {
                callback.run(chat1);
            }
        });
    }

    public MessagesController getMessagesController() {
        return MessagesController.getInstance(selectedAccount);
    }

    public MessagesStorage getMessageStorage() {
        return MessagesStorage.getInstance(selectedAccount);
    }

    public ConnectionsManager getConnectionsManager() {
        return ConnectionsManager.getInstance(selectedAccount);
    }

    public FileLoader getFileLoader() {
        return FileLoader.getInstance(selectedAccount);
    }

    public UserConfig getUserConfig() {
        return UserConfig.getInstance(selectedAccount);
    }

    public void addMessageToClipboard(MessageObject selectedObject, Runnable callback) {
        String path = getPathToMessage(selectedObject);
        if (!TextUtils.isEmpty(path)) {
            SystemUtils.addFileToClipboard(new File(path), callback);
        }
    }

    public String getPathToMessage(MessageObject messageObject) {
        if (messageObject == null) {
            return null;
        }

        var fileLoader = FileLoader.getInstance(messageObject.currentAccount);
        String path;

        if (messageObject.messageOwner != null && !TextUtils.isEmpty(messageObject.messageOwner.attachPath)) {
            path = messageObject.messageOwner.attachPath;
            if (fileExists(path)) {
                return path;
            }
        }

        if (messageObject.messageOwner != null) {
            path = fileLoader.getPathToMessage(messageObject.messageOwner).toString();
            if (fileExists(path)) {
                return path;
            }
        }

        if (messageObject.getDocument() != null) {
            path = fileLoader.getPathToAttach(messageObject.getDocument(), false).toString();
            if (fileExists(path)) {
                return path;
            }

            path = fileLoader.getPathToAttach(messageObject.getDocument(), true).toString();
            if (fileExists(path)) {
                return path;
            }
        }

        if (messageObject.cachedQuality != null && messageObject.cachedQuality.isCached() && messageObject.cachedQuality.uri != null) {
            path = messageObject.cachedQuality.uri.getPath();
            if (fileExists(path)) {
                return path;
            }
        }

        if (messageObject.qualityToSave != null) {
            path = fileLoader.getPathToAttach(messageObject.qualityToSave, null, false, true).toString();
            if (fileExists(path)) {
                return path;
            }
        }

        return null;
    }

    public boolean canSaveSticker(MessageObject messageObject) {
        return MessageObject.isStickerDocument(messageObject.getDocument()) || isEmoji(messageObject.getDocument());
    }

    public boolean isEmoji(TLRPC.Document document) {
        return MessageObject.isAnimatedEmoji(document) && !MessageObject.isAnimatedStickerDocument(document, false);
    }

    public void saveStickerToGallery(Activity activity, MessageObject messageObject, Utilities.Callback<Uri> callback) {
        saveStickerToGallery(activity, getPathToMessage(messageObject), messageObject.isVideoSticker(), callback);
    }

    public void saveStickerToGallery(Activity activity, TLRPC.Document document, Utilities.Callback<Uri> callback) {
        String path = getFileLoader().getPathToAttach(document, true).toString();
        File temp = new File(path);
        if (!temp.exists()) {
            return;
        }
        saveStickerToGallery(activity, path, MessageObject.isVideoSticker(document), callback);
    }

    public void saveStickerToGallery(Activity activity, String path, boolean isVideo, Utilities.Callback<Uri> callback) {
        utilsQueue.postRunnable(() -> {
            if (!TextUtils.isEmpty(path)) {
                try {
                    FileLog.e(path);
                    if (isVideo) {
                        AndroidUtilities.runOnUIThread(() -> MediaController.saveFile(path, activity, 1, null, null, callback));
                    } else {
                        Bitmap image = BitmapFactory.decodeFile(path);
                        if (image != null) {
                            File file = new File(path.endsWith(".webp") ? path.replace(".webp", ".png") : path + ".png");
                            FileOutputStream stream = new FileOutputStream(file);
                            image.compress(Bitmap.CompressFormat.PNG, 100, stream);
                            stream.close();
                            AndroidUtilities.runOnUIThread(() -> MediaController.saveFile(file.toString(), activity, 0, null, null, callback));
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }

    public void saveGifToGallery(Activity activity, TLRPC.Document document, Utilities.Callback<Uri> callback) {
        saveGifToGallery(activity, document, null, callback);
    }

    public void saveGifToGallery(Activity activity, TLRPC.BotInlineResult inlineResult, Utilities.Callback<Uri> callback) {
        saveGifToGallery(activity, null, inlineResult, callback);
    }

    public void saveGifToGallery(Activity activity, TLRPC.Document document, TLRPC.BotInlineResult inlineResult, Utilities.Callback<Uri> callback) {
        String path = getGifPath(document, inlineResult);
        if (TextUtils.isEmpty(path)) {
            return;
        }
        utilsQueue.postRunnable(() -> AndroidUtilities.runOnUIThread(() -> MediaController.saveFile(path, activity, 1, null, null, callback)));
    }

    private String getGifPath(TLRPC.Document document, TLRPC.BotInlineResult inlineResult) {
        if (document != null) {
            String path = getExistingPath(getFileLoader().getPathToAttach(document, false));
            if (path != null) {
                return path;
            }
            return getExistingPath(getFileLoader().getPathToAttach(document, true));
        }
        if (inlineResult == null || inlineResult.content == null) {
            return null;
        }
        WebFile webFile = WebFile.createWithWebDocument(inlineResult.content);
        String path = getExistingPath(getFileLoader().getPathToAttach(webFile, false));
        if (path != null) {
            return path;
        }
        return getExistingPath(getFileLoader().getPathToAttach(webFile, true));
    }

    private String getExistingPath(File file) {
        return file != null && file.exists() ? file.toString() : null;
    }

    public void resolveChannel(String username, Utilities.Callback<TLRPC.Chat> callback) {
        getMessagesController().getUserNameResolver().resolve(username, (peerId) -> {
            if (peerId != null && peerId < 0) {
                callback.run(getMessagesController().getChat(-peerId));
            } else {
                callback.run(null);
            }
        });
    }

    public boolean hasArchivedChats() {
        return getMessagesController().hasArchivedChatsActual();
    }

    public long getLikeDialog() {
        return ExteraConfig.getPreferences().getLong("channelToSave" + selectedAccount, getUserConfig().getClientUserId());
    }

    public void setLikeDialog(long did) {
        ExteraConfig.getEditor().putLong("channelToSave" + selectedAccount, did).apply();
    }

    public boolean hasBotsInSideMenu() {
        TLRPC.TL_attachMenuBots menuBots = MediaDataController.getInstance(selectedAccount).getAttachMenuBots();
        if (menuBots != null && menuBots.bots != null) {
            for (int i = 0; i < menuBots.bots.size(); i++) {
                TLRPC.TL_attachMenuBot bot = menuBots.bots.get(i);
                if (bot.show_in_side_menu) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean shouldAddTimestamp(MessageObject currentMessageObject, CharSequence text) {
        if (currentMessageObject.messageOwner == null) {
            return false;
        }
        boolean isActionEvent = currentMessageObject.currentEvent != null || currentMessageObject.messageOwner.action != null;
        return isActionEvent && !TextUtils.isEmpty(text);
    }

    public CharSequence addTimestamp(CharSequence source, long date, Theme.ResourcesProvider resourcesProvider) {
        long dateMillis = date * 1000L;
        String formattedDay = LocaleController.getInstance().getFormatterDay().format(dateMillis);

        SpannableStringBuilder ssb = new SpannableStringBuilder(source);
        ProfileActivity.ShowDrawable drawable = ChannelAdminLogActivity.findDrawable(source);
        if (drawable == null) {
            drawable = new ProfileActivity.ShowDrawable(formattedDay);
            drawable.textDrawable.setTypeface(AndroidUtilities.bold());
            drawable.textDrawable.setTextSize(dp(10));
            drawable.setTextColor(Theme.getThemePaint(Theme.key_paint_chatActionText, resourcesProvider).getColor());
            drawable.setBackgroundColor(0x1e000000);
        } else {
            drawable.textDrawable.setText(formattedDay, false);
        }
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        ssb.append(" S");
        ssb.setSpan(new ColoredImageSpan(drawable), ssb.length() - 1, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        return ssb;
    }
}
