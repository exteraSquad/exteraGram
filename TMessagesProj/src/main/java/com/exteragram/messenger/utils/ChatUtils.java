/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.utils;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.Locale;

public class ChatUtils {
    private static boolean useFallback;

    public static String getDC(TLRPC.User user) {
        return getDC(user, null);
    }

    public static String getDC(TLRPC.Chat chat) {
        return getDC(null, chat);
    }

    public static String getDC(TLRPC.User user, TLRPC.Chat chat) {
        int DC = 0, myDC = getConnectionsManager().getCurrentDatacenterId();
        if (user != null) {
            if (UserObject.isUserSelf(user) && myDC != -1) {
                DC = myDC;
            } else {
                DC = user.photo != null ? user.photo.dc_id : -1;
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

    public static String getDCName(int dc) {
        switch (dc) {
            case 1:
            case 3:
                return "Miami FL, USA";
            case 2:
            case 4:
                return "Amsterdam, NL";
            case 5:
                return "Singapore, SG";
            default:
                return null;
        }
    }

    public static boolean isSubscribedTo(long id) {
        TLRPC.Chat chat = getMessagesController().getChat(id);
        return chat != null && !chat.left && !chat.kicked;
    }

    public static String getName(long did) {
        int currentAccount = UserConfig.selectedAccount;
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
            if (user != null) name = ContactsController.formatName(user.first_name, user.last_name);
        } else {
            TLRPC.Chat chat = getMessagesController().getChat(-did);
            if (chat != null) name = chat.title;
        }
        return did == UserConfig.getInstance(currentAccount).getClientUserId() ? LocaleController.getString("SavedMessages", R.string.SavedMessages) : name;
    }

    public interface SearchCallback {
        void run(TLRPC.User user);
    }

    public static void searchById(Long userId, SearchCallback callback) {
        if (userId == 0) {
            return;
        }
        TLRPC.User user = getMessagesController().getUser(userId);
        if (user != null) {
            useFallback = false;
            callback.run(user);
        } else {
            searchUser(userId, true, true, user1 -> {
                if (user1 != null && user1.access_hash != 0) {
                    useFallback = false;
                    callback.run(user1);
                } else {
                    if (!useFallback) {
                        useFallback = true;
                        searchById(0x100000000L + userId, callback);
                    } else {
                        useFallback = false;
                        callback.run(null);
                    }
                }
            });
        }
    }

    private static void searchUser(long userId, boolean searchUser, boolean cache, SearchCallback callback) {
        final long bot_id = 1696868284L;
        TLRPC.User bot = getMessagesController().getUser(bot_id);
        if (bot == null) {
            if (searchUser) {
                resolveUser("tgdb_bot", bot_id, user -> searchUser(userId, false, false, callback));
            } else {
                callback.run(null);
            }
            return;
        }

        String key = "user_search_" + userId;
        RequestDelegate requestDelegate = (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (cache && (!(response instanceof TLRPC.messages_BotResults) || ((TLRPC.messages_BotResults) response).results.isEmpty())) {
                searchUser(userId, searchUser, false, callback);
                return;
            }

            if (response instanceof TLRPC.messages_BotResults) {
                TLRPC.messages_BotResults res = (TLRPC.messages_BotResults) response;
                if (!cache && res.cache_time != 0) {
                    getMessageStorage().saveBotCache(key, res);
                }
                if (res.results.isEmpty()) {
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
            } else {
                callback.run(null);
            }
        });

        if (cache) {
            getMessageStorage().getBotCache(key, requestDelegate);
        } else {
            TLRPC.TL_messages_getInlineBotResults req = new TLRPC.TL_messages_getInlineBotResults();
            req.query = String.valueOf(userId);
            req.bot = getMessagesController().getInputUser(bot);
            req.offset = "";
            req.peer = new TLRPC.TL_inputPeerEmpty();
            getConnectionsManager().sendRequest(req, requestDelegate, ConnectionsManager.RequestFlagFailOnServerErrors);
        }
    }

    private static void resolveUser(String userName, long userId, SearchCallback callback) {
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = userName;
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response != null) {
                TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                getMessagesController().putUsers(res.users, false);
                getMessagesController().putChats(res.chats, false);
                getMessageStorage().putUsersAndChats(res.users, res.chats, true, true);
                callback.run(res.peer.user_id == userId ? getMessagesController().getUser(userId) : null);
            } else {
                callback.run(null);
            }
        }));
    }

    public static String getOwnerIds(long stickerSetId) {
        return "int32: " + (stickerSetId >> 32) + '\n' +
                "int64: " + (0x100000000L + (stickerSetId >> 32));
    }

    public static MessagesController getMessagesController() {
        return MessagesController.getInstance(UserConfig.selectedAccount);
    }

    public static MessagesStorage getMessageStorage() {
        return MessagesStorage.getInstance(UserConfig.selectedAccount);
    }

    public static ConnectionsManager getConnectionsManager() {
        return ConnectionsManager.getInstance(UserConfig.selectedAccount);
    }

    public static FileLoader getFileLoader() {
        return FileLoader.getInstance(UserConfig.selectedAccount);
    }

    public static void addMessageToClipboard(MessageObject selectedObject, Runnable callback) {
        String path = getPathToMessage(selectedObject);
        if (!TextUtils.isEmpty(path)) {
            SystemUtils.addFileToClipboard(new File(path), callback);
        }
    }

    public static String getPathToMessage(MessageObject messageObject) {
        String path = messageObject.messageOwner.attachPath;
        if (!TextUtils.isEmpty(path)) {
            File temp = new File(path);
            if (!temp.exists()) {
                path = null;
            }
        }
        if (TextUtils.isEmpty(path)) {
            path = getFileLoader().getPathToMessage(messageObject.messageOwner).toString();
            File temp = new File(path);
            if (!temp.exists()) {
                path = null;
            }
        }
        if (TextUtils.isEmpty(path)) {
            path = getFileLoader().getPathToAttach(messageObject.getDocument(), true).toString();
            File temp = new File(path);
            if (!temp.exists()) {
                return null;
            }
        }
        return path;
    }
}
