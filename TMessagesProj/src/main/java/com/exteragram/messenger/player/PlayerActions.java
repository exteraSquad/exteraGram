/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.FrameLayout;

import androidx.core.content.FileProvider;

import com.exteragram.messenger.utils.chats.ForwardContext;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FileRefController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.FiltersView;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.recorder.SelectAudioAlert;

import java.io.File;
import java.util.ArrayList;

public class PlayerActions {

    public interface Host {
        FrameLayout getBulletinContainer();

        Theme.ResourcesProvider getBulletinResourcesProvider();

        Theme.ResourcesProvider getSheetResourcesProvider();

        MessagesController.SavedMusicList getSavedMusicList();

        void dismissPlayer();
    }

    private final int currentAccount;
    private final LaunchActivity activity;
    private final Host host;

    public PlayerActions(int currentAccount, LaunchActivity activity, Host host) {
        this.currentAccount = currentAccount;
        this.activity = activity;
        this.host = host;
    }

    private BulletinFactory bulletins() {
        return BulletinFactory.of(host.getBulletinContainer(), host.getBulletinResourcesProvider());
    }

    private void switchAccountIfNeeded() {
        if (activity != null && UserConfig.selectedAccount != currentAccount) {
            activity.switchToAccount(currentAccount, true);
        }
    }

    public static boolean isNoForwards(int currentAccount, MessageObject messageObject) {
        if (messageObject == null) {
            return false;
        }
        final MessagesController controller = MessagesController.getInstance(currentAccount);
        final long dialogId = messageObject.getDialogId();
        return controller.isPeerNoForwards(dialogId) || messageObject.messageOwner != null && messageObject.messageOwner.noforwards;
    }

    public static boolean isServiceTrack(MessageObject messageObject) {
        return messageObject == null || messageObject.eventId != 0 || messageObject.getId() <= -2000000000;
    }

    public static File getLocalFile(MessageObject messageObject) {
        final String attachPath = messageObject.messageOwner.attachPath;
        if (!TextUtils.isEmpty(attachPath)) {
            final File file = new File(attachPath);
            if (file.exists()) {
                return file;
            }
        }
        return FileLoader.getInstance(messageObject.currentAccount).getPathToMessage(messageObject.messageOwner);
    }

    public boolean isInProfile(MessageObject messageObject) {
        final TLRPC.Document document = messageObject != null ? messageObject.getDocument() : null;
        return document != null && MessagesController.getInstance(currentAccount).getSavedMusicIds().ids.contains(document.id);
    }

    public boolean isProfileIdsLoading() {
        return MessagesController.getInstance(currentAccount).getSavedMusicIds().loading;
    }

    public void forward(MessageObject messageObject, boolean noQuote) {
        if (activity == null || messageObject == null) {
            return;
        }
        switchAccountIfNeeded();
        final Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        args.putBoolean("canSelectTopics", true);
        args.putBoolean(ForwardContext.FORWARD_NOQUOTE, noQuote);
        final DialogsActivity fragment = new DialogsActivity(args);
        final ArrayList<MessageObject> messages;
        final TLRPC.TL_document document;
        if (messageObject.getId() < 0) {
            messages = null;
            if (!(messageObject.getDocument() instanceof TLRPC.TL_document doc)) {
                return;
            }
            document = doc;
        } else {
            messages = new ArrayList<>();
            messages.add(messageObject);
            document = null;
        }
        final MessagesController.SavedMusicList savedMusicList = host.getSavedMusicList();
        fragment.setDelegate((fragment1, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
            if (dids.size() > 1 || dids.get(0).dialogId == selfId || message != null || messages == null) {
                for (int a = 0; a < dids.size(); a++) {
                    final long did = dids.get(a).dialogId;
                    final MessageObject topicMessage = getTopicStartMessage(dids.get(a));
                    if (message != null) {
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(SendMessagesHelper.SendMessageParams.of(message.toString(), did, topicMessage, topicMessage, null, true, null, null, null, notify, scheduleDate, scheduleRepeatPeriod, null, false));
                    }
                    if (messages != null) {
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(messages, did, noQuote, false, notify, scheduleDate, topicMessage, 0, 0);
                    } else {
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(SendMessagesHelper.SendMessageParams.of(document, null, messageObject.messageOwner.attachPath, did, topicMessage, topicMessage, null, null, null, null, notify, scheduleDate, scheduleRepeatPeriod, 0, savedMusicList, null, false, false));
                    }
                }
                if (topicsFragment != null) {
                    topicsFragment.removeSelfFromStack();
                }
                fragment1.finishFragment();
                showForwardBulletin(dids.size() == 1 ? dids.get(0).dialogId : 0, dids.size());
            } else {
                final MessagesStorage.TopicKey topicKey = dids.get(0);
                final long did = topicKey.dialogId;
                final Bundle chatArgs = new Bundle();
                chatArgs.putBoolean("scrollToTopOnResume", true);
                if (DialogObject.isEncryptedDialog(did)) {
                    chatArgs.putInt("enc_id", DialogObject.getEncryptedChatId(did));
                } else if (DialogObject.isUserDialog(did)) {
                    chatArgs.putLong("user_id", did);
                } else {
                    chatArgs.putLong("chat_id", -did);
                }
                chatArgs.putBoolean(ForwardContext.FORWARD_NOQUOTE, noQuote);
                final ChatActivity chatActivity = new ChatActivity(chatArgs);
                if (topicKey.topicId != 0 || topicsFragment != null) {
                    ForumUtilities.applyTopic(chatActivity, topicKey);
                    fragment1.removeSelfFromStack();
                }
                if (activity.presentFragment(chatActivity, true, false)) {
                    chatActivity.setForwardParams(noQuote);
                    chatActivity.showFieldPanelForForward(true, messages);
                } else {
                    fragment1.finishFragment();
                }
            }
            return true;
        });
        activity.presentFragment(fragment);
        host.dismissPlayer();
    }

    private MessageObject getTopicStartMessage(MessagesStorage.TopicKey topicKey) {
        final MessagesController messagesController = MessagesController.getInstance(currentAccount);
        if (topicKey.topicId == 0 || messagesController.isMonoForum(topicKey.dialogId)) {
            return null;
        }
        final TLRPC.TL_forumTopic topic = messagesController.getTopicsController().findTopic(-topicKey.dialogId, topicKey.topicId);
        if (topic == null || topic.topicStartMessage == null) {
            return null;
        }
        final MessageObject topicMessage = new MessageObject(currentAccount, topic.topicStartMessage, false, false);
        topicMessage.isTopicMainMessage = true;
        return topicMessage;
    }

    public void forwardTo(MessageObject messageObject, long dialogId, boolean bulletin) {
        if (messageObject == null) {
            return;
        }
        switchAccountIfNeeded();
        if (messageObject.getId() < 0) {
            if (!(messageObject.getDocument() instanceof TLRPC.TL_document document)) {
                return;
            }
            SendMessagesHelper.getInstance(currentAccount).sendMessage(SendMessagesHelper.SendMessageParams.of(document, null, messageObject.messageOwner.attachPath, dialogId, null, null, null, null, null, null, true, 0, 0, 0, host.getSavedMusicList(), null, false, false));
        } else {
            final ArrayList<MessageObject> messages = new ArrayList<>();
            messages.add(messageObject);
            SendMessagesHelper.getInstance(currentAccount).sendMessage(messages, dialogId, false, false, true, 0, 0);
        }
        if (bulletin) {
            showForwardBulletin(dialogId, 1);
        }
    }

    private void showForwardBulletin(long dialogId, int count) {
        final BaseFragment lastFragment = LaunchActivity.getLastFragment();
        if (lastFragment == null) {
            return;
        }
        final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
        final CharSequence text;
        if (count == 1 && dialogId == selfId) {
            text = getString(R.string.FwdMessageToSavedMessages);
        } else if (count == 1 && dialogId > 0) {
            text = formatString(R.string.FwdMessageToUser, DialogObject.getShortName(dialogId));
        } else if (count == 1 && dialogId < 0) {
            text = formatString(R.string.FwdMessageToGroup, DialogObject.getShortName(dialogId));
        } else {
            text = LocaleController.formatPluralStringComma("FwdMessageToManyChats", count);
        }
        BulletinFactory.of(lastFragment).createSimpleBulletin(R.raw.forward, text).show();
    }

    public void share(MessageObject messageObject) {
        if (activity == null || messageObject == null) {
            return;
        }
        try {
            final File file = getLocalFile(messageObject);
            if (file.exists()) {
                final Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType(messageObject.getMimeType());
                try {
                    intent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(ApplicationLoader.applicationContext, ApplicationLoader.getApplicationId() + ".provider", file));
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignore) {
                    intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
                }
                activity.startActivity(Intent.createChooser(intent, getString(R.string.ShareFile)));
            } else {
                final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setTitle(getString(R.string.AppName));
                builder.setPositiveButton(getString(R.string.OK), null);
                builder.setMessage(getString(R.string.PleaseDownload));
                builder.show();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void saveToMusic(MessageObject messageObject) {
        if (activity == null || messageObject == null) {
            return;
        }
        if ((Build.VERSION.SDK_INT <= 28 || BuildVars.NO_SCOPED_STORAGE) && activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
            return;
        }
        String fileName = FileLoader.getDocumentFileName(messageObject.getDocument());
        if (TextUtils.isEmpty(fileName)) {
            fileName = messageObject.getFileName();
        }
        MediaController.saveFile(getLocalFile(messageObject).toString(), activity, 3, fileName, messageObject.getDocument() != null ? messageObject.getDocument().mime_type : "", uri -> bulletins().createDownloadBulletin(BulletinFactory.FileType.AUDIO).show());
    }

    public void showInChat(MessageObject messageObject) {
        if (activity == null || messageObject == null) {
            return;
        }
        switchAccountIfNeeded();
        final Bundle args = new Bundle();
        long did = messageObject.getDialogId();
        if (DialogObject.isEncryptedDialog(did)) {
            args.putInt("enc_id", DialogObject.getEncryptedChatId(did));
        } else if (DialogObject.isUserDialog(did)) {
            args.putLong("user_id", did);
        } else {
            final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
            if (chat != null && chat.migrated_to != null) {
                args.putLong("migrated_to", did);
                did = -chat.migrated_to.channel_id;
            }
            args.putLong("chat_id", -did);
        }
        args.putInt("message_id", messageObject.getId());
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats);
        activity.presentFragment(new ChatActivity(args), false, false);
        host.dismissPlayer();
    }

    public void searchArtist(String query) {
        if (activity == null || TextUtils.isEmpty(query)) {
            return;
        }
        if (MessagesController.getInstance(currentAccount).getTotalDialogsCount() <= 10) {
            return;
        }
        if (activity.getActionBarLayout().getLastFragment() instanceof DialogsActivity dialogsActivity) {
            if (!dialogsActivity.onlyDialogsAdapter()) {
                dialogsActivity.setShowSearch(query, FiltersView.FILTER_INDEX_MUSIC);
                host.dismissPlayer();
                return;
            }
        }
        final DialogsActivity fragment = new DialogsActivity(null);
        fragment.setSearchString(query);
        fragment.setInitialSearchType(FiltersView.FILTER_INDEX_MUSIC);
        activity.presentFragment(fragment, false, false);
        host.dismissPlayer();
    }

    public void setInProfile(MessageObject messageObject, boolean save, Runnable done) {
        saveToProfile(messageObject, save, done, false);
    }

    private void saveToProfile(MessageObject messageObject, boolean save, Runnable done, boolean triedFileRef) {
        final TLRPC.Document document = messageObject.getDocument();
        if (document == null) {
            return;
        }
        final long documentId = document.id;
        final TLRPC.TL_account_saveMusic req = new TLRPC.TL_account_saveMusic();
        req.unsave = !save;
        req.id = new TLRPC.TL_inputDocument();
        req.id.id = documentId;
        req.id.access_hash = document.access_hash;
        req.id.file_reference = document.file_reference;
        if (req.id.file_reference == null) {
            req.id.file_reference = new byte[0];
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
            if (err != null && FileRefController.isFileRefError(err.text)) {
                if (triedFileRef || messageObject.getId() < 0) {
                    AndroidUtilities.runOnUIThread(() -> bulletins().showForError(err));
                    return;
                }
                refetchMessage(messageObject, message -> saveToProfile(new MessageObject(currentAccount, message, false, true), save, done, true));
                return;
            } else if (err != null) {
                AndroidUtilities.runOnUIThread(() -> bulletins().showForError(err));
                return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                MessagesController.getInstance(currentAccount).getSavedMusicIds().update(documentId, save);
                final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
                final TLRPC.UserFull userInfo = MessagesController.getInstance(currentAccount).getUserFull(selfId);
                if (userInfo != null) {
                    if (save) {
                        userInfo.flags2 |= TLObject.FLAG_21;
                        userInfo.saved_music = document;
                    } else if (userInfo.saved_music != null && userInfo.saved_music.id == documentId) {
                        userInfo.flags2 &= ~TLObject.FLAG_21;
                        userInfo.saved_music = null;
                    }
                    MessagesStorage.getInstance(currentAccount).updateUserInfo(userInfo, true);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.profileMusicUpdated, selfId);
                }
                if (done != null) {
                    done.run();
                }
            });
        });
    }

    private interface MessageCallback {
        void run(TLRPC.Message message);
    }

    private void refetchMessage(MessageObject messageObject, MessageCallback callback) {
        final int messageId = messageObject.getId();
        final TLObject request;
        if (messageObject.getDialogId() >= 0) {
            final TLRPC.TL_messages_getMessages req = new TLRPC.TL_messages_getMessages();
            req.id.add(messageId);
            request = req;
        } else {
            final TLRPC.TL_channels_getMessages req = new TLRPC.TL_channels_getMessages();
            req.channel = MessagesController.getInstance(currentAccount).getInputChannel(-messageObject.getDialogId());
            req.id.add(messageId);
            request = req;
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(request, (res, err) -> {
            if (res instanceof TLRPC.messages_Messages messages) {
                TLRPC.Message message = null;
                for (int i = 0; i < messages.messages.size(); ++i) {
                    if (messages.messages.get(i).id == messageId) {
                        message = messages.messages.get(i);
                        break;
                    }
                }
                if (message != null) {
                    callback.run(message);
                } else {
                    AndroidUtilities.runOnUIThread(() -> bulletins().createErrorBulletin(formatString(R.string.UnknownErrorCode, "CLIENT_MESSAGE_NOT_FOUND")).show());
                }
            } else if (err != null) {
                AndroidUtilities.runOnUIThread(() -> bulletins().showForError(err));
            }
        });
    }

    public void addToMyList(Runnable onListChanged) {
        if (activity == null) {
            return;
        }
        final Theme.ResourcesProvider resourcesProvider = host.getSheetResourcesProvider();
        new SelectAudioAlert(activity, true, null, audio -> {
            final MessagesController.SavedMusicList savedMusicList = host.getSavedMusicList();
            if (audio == null || savedMusicList == null) {
                return;
            }
            final TLRPC.Document document = audio.getDocument();
            if (document == null) {
                return;
            }
            if (document.id != 0) {
                final TLRPC.TL_account_saveMusic req = new TLRPC.TL_account_saveMusic();
                req.id = new TLRPC.TL_inputDocument();
                req.id.id = document.id;
                req.id.access_hash = document.access_hash;
                req.id.file_reference = document.file_reference;
                savedMusicList.add(document);
                syncPlaylist(savedMusicList);
                MessagesController.getInstance(currentAccount).getSavedMusicIds().update(document.id, true);
                if (onListChanged != null) {
                    onListChanged.run();
                }
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);
                return;
            }
            final File localFile = new File(audio.messageOwner.attachPath);
            if (!localFile.exists()) {
                return;
            }
            final AlertDialog progressDialog = new AlertDialog(activity, AlertDialog.ALERT_TYPE_SPINNER, resourcesProvider);
            progressDialog.showDelayed(180);
            FileLoader.getInstance(currentAccount).uploadFile(localFile.getAbsolutePath(), file -> {
                if (file == null) {
                    progressDialog.dismiss();
                    return;
                }
                final TLRPC.TL_messages_uploadMedia req = new TLRPC.TL_messages_uploadMedia();
                req.peer = MessagesController.getInstance(currentAccount).getInputPeer(UserConfig.getInstance(currentAccount).getClientUserId());
                req.media = new TLRPC.TL_inputMediaUploadedDocument();
                req.media.file = file;
                req.media.mime_type = document.mime_type;
                req.media.attributes.addAll(document.attributes);
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    progressDialog.dismiss();
                    if (res instanceof TLRPC.TL_messageMediaDocument media) {
                        final TLRPC.TL_account_saveMusic saveReq = new TLRPC.TL_account_saveMusic();
                        saveReq.id = new TLRPC.TL_inputDocument();
                        saveReq.id.id = media.document.id;
                        saveReq.id.access_hash = media.document.access_hash;
                        saveReq.id.file_reference = media.document.file_reference;
                        final MessagesController.SavedMusicList list = host.getSavedMusicList();
                        if (list != null) {
                            list.add(media.document);
                            syncPlaylist(list);
                        }
                        MessagesController.getInstance(currentAccount).getSavedMusicIds().update(media.document.id, true);
                        if (onListChanged != null) {
                            onListChanged.run();
                        }
                        ConnectionsManager.getInstance(currentAccount).sendRequest(saveReq, null);
                    }
                }));
            });
        }, resourcesProvider).withoutSavedMusic().show();
    }

    private static void syncPlaylist(MessagesController.SavedMusicList savedMusicList) {
        final ArrayList<MessageObject> playlist = MediaController.getInstance().getPlaylist();
        playlist.clear();
        playlist.addAll(savedMusicList.list);
    }

    public void addBackToMyList(MessageObject messageObject, Runnable onAdded) {
        setInProfile(messageObject, true, () -> {
            final MessagesController.SavedMusicList savedMusicList = host.getSavedMusicList();
            if (savedMusicList != null && !savedMusicList.list.contains(messageObject)) {
                savedMusicList.list.add(0, messageObject);
                savedMusicList.updateFirstMusic();
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.musicListLoaded, savedMusicList);
            }
            if (onAdded != null) {
                onAdded.run();
            }
        });
    }

    public void removeFromMyList(MessageObject messageObject, Runnable onRemoved) {
        setInProfile(messageObject, false, () -> {
            final MessagesController.SavedMusicList savedMusicList = host.getSavedMusicList();
            if (savedMusicList != null) {
                savedMusicList.remove(messageObject);
                if (savedMusicList.list.isEmpty()) {
                    MediaController.getInstance().cleanup();
                    host.dismissPlayer();
                    return;
                }
                MediaController.getInstance().removeFromPlaylist(messageObject);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.musicListLoaded, savedMusicList);
            }
            if (onRemoved != null) {
                onRemoved.run();
            }
        });
    }
}
