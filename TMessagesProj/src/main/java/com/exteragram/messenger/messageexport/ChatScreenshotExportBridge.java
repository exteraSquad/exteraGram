/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.messageexport;

import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.os.Build;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.View;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.ContentPreviewViewer;
import org.telegram.ui.GroupCallActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.SecretMediaViewer;
import org.telegram.ui.Stories.recorder.StoryRecorder;
import org.telegram.ui.VoIPFragment;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

public final class ChatScreenshotExportBridge {

    private static final long DEDUP_MS = 1000;

    private static final WeakHashMap<Activity, Object> callbacks = new WeakHashMap<>();
    private static WeakReference<ChatActivity> resumedChat;
    private static long lastEventTime;

    private ChatScreenshotExportBridge() {
    }

    public static void onChatResumed(ChatActivity chat) {
        Activity activity = chat.getParentActivity();
        if (activity == null || !MessageExport.isChatSupported(chat)) {
            return;
        }
        resumedChat = new WeakReference<>(chat);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Api34.register(activity);
        }
    }

    public static void onChatPaused(ChatActivity chat) {
        if (resumedChat == null || resumedChat.get() != chat) {
            return;
        }
        resumedChat = null;
        Activity activity = chat.getParentActivity();
        if (activity != null) {
            unregister(activity);
        }
    }

    public static void unregister(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Api34.unregister(activity);
        }
    }

    private static void onScreenCaptured() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastEventTime < DEDUP_MS) {
            return;
        }
        lastEventTime = now;
        if (SharedConfig.isWaitingForPasscodeEnter || PhotoViewer.hasInstance() && PhotoViewer.getInstance().isVisible()) {
            return;
        }
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (!(fragment instanceof ChatActivity)) {
            return;
        }
        ChatActivity chat = (ChatActivity) fragment;
        if (chat.isPaused() || chat.getParentActivity() == null || isChatCovered(chat) || !MessageExport.isChatSupported(chat)) {
            return;
        }
        SparseArray<MessageObject>[] snapshot = collectVisible(chat);
        if (snapshot == null) {
            return;
        }
        Bulletin.TwoLineLottieLayout layout = new Bulletin.TwoLineLottieLayout(chat.getParentActivity(), chat.getResourceProvider());
        layout.setAnimation(R.raw.tab_gallery, 28, 28);
        layout.titleTextView.setText(getString(R.string.MessageExportScreenshotTaken));
        layout.subtitleTextView.setText(getString(R.string.MessageExportScreenshotHint));
        layout.setButton(new Bulletin.UndoButton(chat.getParentActivity(), true, chat.getResourceProvider()).setText(getString(R.string.MessageExportCustomize)).setUndoAction(() -> {
            if (chat.getParentActivity() != null && chat.getFragmentView() != null && chat.getParentLayout() != null) {
                MessageExport.open(chat, snapshot);
            }
        }));
        BulletinFactory.of(chat).create(layout, Bulletin.DURATION_PROLONG).show(true);
    }

    private static boolean isChatCovered(ChatActivity chat) {
        if (chat.isInPreviewMode() || chat.getVisibleDialog() != null || chat.isTopicsPanelOpen() || BaseFragment.hasSheets(chat)) {
            return true;
        }
        if (chat.instantCameraView != null && chat.instantCameraView.getVisibility() == View.VISIBLE) {
            return true;
        }
        if (chat.scrimPopupWindow != null && chat.scrimPopupWindow.isShowing()) {
            return true;
        }
        return ContentPreviewViewer.hasInstance() && ContentPreviewViewer.getInstance().isVisible()
                || SecretMediaViewer.hasInstance() && SecretMediaViewer.getInstance().isVisible()
                || StoryRecorder.isVisible()
                || GroupCallActivity.groupCallInstance != null
                || VoIPFragment.getInstance() != null;
    }

    private static SparseArray<MessageObject>[] collectVisible(ChatActivity chat) {
        RecyclerListView list = chat.getChatListView();
        if (list == null) {
            return null;
        }
        SparseArray<MessageObject>[] selected = MessageExport.createSelection();
        int top = list.getPaddingTop();
        int bottom = list.getHeight() - list.getPaddingBottom();
        int count = 0;
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child.getBottom() <= top || child.getTop() >= bottom) {
                continue;
            }
            if (child instanceof ChatActionCell actionCell) {
                MessageObject action = actionCell.getMessageObject();
                if (action != null && !action.isDateObject) {
                    count += put(chat, selected, action);
                }
                continue;
            }
            if (!(child instanceof ChatMessageCell)) {
                continue;
            }
            ChatMessageCell cell = (ChatMessageCell) child;
            MessageObject message = cell.getMessageObject();
            MessageObject.GroupedMessages group = cell.getCurrentMessagesGroup();
            if (group != null && !group.messages.isEmpty()) {
                for (int g = 0; g < group.messages.size(); g++) {
                    count += put(chat, selected, group.messages.get(g));
                }
            } else {
                count += put(chat, selected, message);
            }
        }
        return count > 0 ? selected : null;
    }

    private static int put(ChatActivity chat, SparseArray<MessageObject>[] selected, MessageObject message) {
        return MessageExport.isMessageSupported(message) && MessageExport.select(selected, chat.getDialogId(), message) ? 1 : 0;
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private static final class Api34 {

        static void register(Activity activity) {
            if (callbacks.containsKey(activity)) {
                return;
            }
            Activity.ScreenCaptureCallback callback = ChatScreenshotExportBridge::onScreenCaptured;
            try {
                activity.registerScreenCaptureCallback(activity.getMainExecutor(), callback);
                callbacks.put(activity, callback);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        static void unregister(Activity activity) {
            Object callback = callbacks.remove(activity);
            if (callback instanceof Activity.ScreenCaptureCallback) {
                try {
                    activity.unregisterScreenCaptureCallback((Activity.ScreenCaptureCallback) callback);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
    }
}
