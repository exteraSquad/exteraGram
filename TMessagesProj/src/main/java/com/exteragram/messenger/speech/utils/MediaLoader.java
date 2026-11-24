/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.speech.utils;

import com.exteragram.messenger.utils.chats.ChatUtils;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

public class MediaLoader implements NotificationCenter.NotificationCenterDelegate {

    private final AccountInstance currentAccount;
    private final ArrayList<MessageObject> messageObjects;
    private final HashMap<String, MessageObject> loadingMessageObjects = new HashMap<>();
    private final MessagesStorage.IntCallback onFinishRunnable;
    private int copiedFiles;
    private CountDownLatch waitingForFile;

    private MediaLoader(AccountInstance accountInstance, ArrayList<MessageObject> messages, MessagesStorage.IntCallback onFinish) {
        currentAccount = accountInstance;
        messageObjects = messages;
        onFinishRunnable = onFinish;

        currentAccount.getNotificationCenter().addObserver(this, NotificationCenter.fileLoaded);
        currentAccount.getNotificationCenter().addObserver(this, NotificationCenter.fileLoadFailed);
    }

    public static void loadFiles(AccountInstance accountInstance, ArrayList<MessageObject> messages, MessagesStorage.IntCallback onFinish) {
        new MediaLoader(accountInstance, messages, onFinish).start();
    }

    public void start() {
        new Thread(() -> {
            try {
                ChatUtils chatUtils = ChatUtils.getInstance();
                for (int b = 0, N = messageObjects.size(); b < N; b++) {
                    MessageObject message = messageObjects.get(b);
                    if (chatUtils.getPathToMessage(message) == null) {
                        waitingForFile = new CountDownLatch(1);
                        addMessageToLoad(message);
                        waitingForFile.await();
                    }
                    if (chatUtils.getPathToMessage(message) != null) {
                        copiedFiles++;
                    }
                }
                checkIfFinished();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }).start();
    }

    private void checkIfFinished() {
        if (!loadingMessageObjects.isEmpty()) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                if (onFinishRunnable != null) {
                    AndroidUtilities.runOnUIThread(() -> onFinishRunnable.run(copiedFiles));
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            currentAccount.getNotificationCenter().removeObserver(this, NotificationCenter.fileLoaded);
            currentAccount.getNotificationCenter().removeObserver(this, NotificationCenter.fileLoadFailed);
        });
    }

    private void addMessageToLoad(MessageObject messageObject) {
        AndroidUtilities.runOnUIThread(() -> {
            TLRPC.Document document = messageObject.getDocument();
            if (document == null) {
                return;
            }
            String fileName = FileLoader.getAttachFileName(document);
            loadingMessageObjects.put(fileName, messageObject);
            currentAccount.getFileLoader().loadFile(document, messageObject, FileLoader.PRIORITY_LOW, messageObject.shouldEncryptPhotoOrVideo() ? 2 : 0);
        });
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.fileLoaded || id == NotificationCenter.fileLoadFailed) {
            String fileName = (String) args[0];
            if (loadingMessageObjects.remove(fileName) != null) {
                waitingForFile.countDown();
            }
        }
    }
}
