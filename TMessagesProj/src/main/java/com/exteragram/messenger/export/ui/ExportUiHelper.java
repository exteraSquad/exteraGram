/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.view.View;
import android.view.ViewGroup;

import com.exteragram.messenger.export.ExportController;
import com.exteragram.messenger.export.ExportState;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.LaunchActivity;

import java.util.Locale;

public final class ExportUiHelper {

    private static final String EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents";
    private static final String DOWNLOADS_AUTHORITY = "com.android.providers.downloads.documents";

    public static final String ACTION_SHOW = "com.exteragram.messenger.export.SHOW";

    private ExportUiHelper() {
    }

    public static boolean canExportHistory(TLRPC.Chat chat, boolean topic) {
        if (chat == null) {
            return !topic;
        }
        if (chat instanceof TLRPC.TL_chatForbidden || chat instanceof TLRPC.TL_channelForbidden || chat.kicked) {
            return false;
        }
        return !topic || ChatObject.isForum(chat) && !ChatObject.isMonoForum(chat);
    }

    public static void addHistoryMenuItem(ActionBarMenuItem menu, int id, boolean topic, int afterId, int... beforeIds) {
        View item = menu.addSubItem(id, R.drawable.msg_download, LocaleController.getString(topic ? R.string.ExportTopicHistory : R.string.ExportChatHistory));
        if (!(item.getParent() instanceof ViewGroup parent)) {
            return;
        }
        int index = -1;
        View after = menu.getSubItem(afterId);
        if (after != null && after.getParent() == parent) {
            index = parent.indexOfChild(after) + 1;
        } else {
            for (int beforeId : beforeIds) {
                View before = menu.getSubItem(beforeId);
                if (before == null || before.getParent() != parent) {
                    continue;
                }
                int position = parent.indexOfChild(before);
                if (position > 0 && parent.getChildAt(position - 1) instanceof ActionBarPopupWindow.GapView) {
                    position--;
                }
                index = index < 0 ? position : Math.min(index, position);
            }
        }
        if (index >= 0 && index < parent.indexOfChild(item)) {
            parent.removeView(item);
            parent.addView(item, index);
        }
    }

    public static boolean hasVisibleState(ExportState state) {
        return state instanceof ExportState.Processing
                || state instanceof ExportState.Finished
                || state instanceof ExportState.ApiError
                || state instanceof ExportState.IoError;
    }

    public static boolean hasVisibleState(int account) {
        return hasVisibleState(ExportController.getInstance(account).getState());
    }

    public static int percent(ExportState.Processing state) {
        return Math.round(Math.min(1f, Math.max(0f, state.progress())) * 100);
    }

    public static String summary(ExportState.Finished state) {
        return AndroidUtilities.formatFileSize(state.bytesCount);
    }

    public static String displayPath(String path) {
        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        if (!path.startsWith(root)) {
            return path;
        }
        String relative = path.substring(root.length());
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        return relative;
    }

    public static void showStopDialog(BaseFragment fragment, Context context, Theme.ResourcesProvider resourcesProvider, int account) {
        AlertDialog dialog = new AlertDialog.Builder(context, resourcesProvider)
                .setTitle(LocaleController.getString(R.string.ExportStop))
                .setMessage(LocaleController.getString(R.string.ExportStopConfirm))
                .setPositiveButton(LocaleController.getString(R.string.ExportStopAction), (d, which) -> ExportController.getInstance(account).cancel())
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create();
        if (fragment != null) {
            fragment.showDialog(dialog);
        } else {
            dialog.show();
        }
        dialog.redPositive();
    }

    public static Intent createShowIntent(Context context, int account) {
        Intent intent = new Intent(context, LaunchActivity.class);
        intent.setAction(ACTION_SHOW);
        intent.putExtra("currentAccount", account);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    public static String stepTitle(ExportState.Processing state) {
        return switch (state.step) {
            case Initializing -> LocaleController.getString(R.string.ExportStateInitializing);
            case DialogsList -> LocaleController.getString(R.string.ExportStateChatsList);
            case PersonalInfo -> LocaleController.getString(R.string.ExportOptionInfo);
            case Userpics -> LocaleController.getString(R.string.ExportOptionUserpics);
            case Stories -> LocaleController.getString(R.string.ExportOptionStories);
            case ProfileMusic -> LocaleController.getString(R.string.ExportOptionProfileMusic);
            case Contacts -> LocaleController.getString(R.string.ExportOptionContacts);
            case Sessions -> LocaleController.getString(R.string.ExportOptionSessions);
            case OtherData -> LocaleController.getString(R.string.ExportOptionOther);
            case Dialogs -> LocaleController.getString(R.string.ExportSectionChats);
            case Topic -> LocaleController.getString(R.string.ExportTopicHistory);
        };
    }

    public static int stepIcon(ExportState.Processing state) {
        if (state.step == ExportState.Processing.Step.Dialogs || state.step == ExportState.Processing.Step.Topic) {
            switch (state.entityType) {
                case VerifyCodes -> {
                    return R.drawable.settings_privacy;
                }
                case Topic -> {
                    return R.drawable.msg_filled_general;
                }
                case Other -> {
                    return R.drawable.settings_chat;
                }
                default -> {
                }
            }
        }
        return switch (state.step) {
            case Initializing -> R.drawable.input_schedule;
            case DialogsList -> R.drawable.tabs_chats_active_24;
            case PersonalInfo -> R.drawable.settings_account;
            case Userpics -> R.drawable.filled_premium_camera;
            case Stories -> R.drawable.msg_filled_stories;
            case ProfileMusic -> R.drawable.files_music;
            case Contacts -> R.drawable.msg_filled_menu_groups;
            case Sessions -> R.drawable.settings_devices;
            case OtherData -> R.drawable.settings_folders;
            case Dialogs -> R.drawable.settings_chat;
            case Topic -> R.drawable.msg_filled_general;
        };
    }

    public static IconBackgroundColors stepColor(ExportState.Processing state) {
        if ((state.step == ExportState.Processing.Step.Dialogs || state.step == ExportState.Processing.Step.Topic) && state.entityType == ExportState.Processing.EntityType.VerifyCodes) {
            return IconBackgroundColors.GREEN;
        }
        return switch (state.step) {
            case Initializing, Dialogs -> IconBackgroundColors.BLUE;
            case DialogsList -> IconBackgroundColors.GREEN;
            case PersonalInfo -> IconBackgroundColors.ORANGE_DEEP;
            case Userpics -> IconBackgroundColors.BLUE_DEEP;
            case Stories -> IconBackgroundColors.PURPLE;
            case ProfileMusic -> IconBackgroundColors.RED;
            case Contacts -> IconBackgroundColors.ORANGE;
            case Sessions -> IconBackgroundColors.CYAN;
            case OtherData -> IconBackgroundColors.GRAY;
            case Topic -> IconBackgroundColors.ORANGE_BRIGHT;
        };
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    public static int fileIcon(String name) {
        return switch (extension(name)) {
            case "jpg", "jpeg", "png", "webp", "heic", "bmp" -> R.drawable.msg_filled_reply_image;
            case "mp4", "mov", "webm", "mkv", "avi", "3gp", "gif" -> R.drawable.filled_profile_video_24;
            case "ogg", "oga", "opus" -> R.drawable.msg_filled_reply_voice;
            case "mp3", "m4a", "flac", "wav", "aac" -> R.drawable.calls_headphones;
            case "tgs" -> R.drawable.stickers_filled;
            default -> R.drawable.msg_filled_reply_files;
        };
    }

    public static IconBackgroundColors fileColor(String name) {
        return switch (extension(name)) {
            case "jpg", "jpeg", "png", "webp", "heic", "bmp" -> IconBackgroundColors.BLUE_DEEP;
            case "mp4", "mov", "webm", "mkv", "avi", "3gp", "gif" -> IconBackgroundColors.PURPLE;
            case "ogg", "oga", "opus" -> IconBackgroundColors.RED;
            case "mp3", "m4a", "flac", "wav", "aac" -> IconBackgroundColors.ORANGE;
            case "tgs" -> IconBackgroundColors.CYAN;
            default -> IconBackgroundColors.GREEN;
        };
    }

    public static String overallCounter(ExportState.Processing state) {
        String chats = chatsCounter(state);
        if (!chats.isEmpty()) {
            return LocaleController.getString(R.string.ExportSectionChats) + " · " + chats;
        }
        if (state.stepIndex > 0 && state.stepCount > 0) {
            return LocaleController.formatString(R.string.ExportStepCounter, state.stepIndex, state.stepCount);
        }
        return stepTitle(state);
    }

    public static String entityTitle(ExportState.Processing state) {
        if (state.step != ExportState.Processing.Step.Dialogs && state.step != ExportState.Processing.Step.Topic) {
            return "";
        }
        return switch (state.entityType) {
            case SavedMessages -> LocaleController.getString(R.string.SavedMessages);
            case RepliesMessages -> LocaleController.getString(R.string.RepliesTitle);
            case VerifyCodes -> LocaleController.getString(R.string.ExportVerifyCodes);
            default -> state.entityName.isEmpty() ? LocaleController.getString(R.string.HiddenName) : state.entityName;
        };
    }

    public static String resolveTreePath(Uri treeUri) {
        try {
            String documentId = DocumentsContract.getTreeDocumentId(treeUri);
            String authority = treeUri.getAuthority();
            if (DOWNLOADS_AUTHORITY.equals(authority)) {
                if (documentId.startsWith("raw:")) {
                    return documentId.substring("raw:".length());
                }
                return "downloads".equals(documentId)
                        ? Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath()
                        : null;
            } else if (!EXTERNAL_STORAGE_AUTHORITY.equals(authority)) {
                return null;
            }
            int colon = documentId.indexOf(':');
            String volume = colon >= 0 ? documentId.substring(0, colon) : documentId;
            String relative = colon >= 0 ? documentId.substring(colon + 1) : "";
            String root;
            if ("primary".equalsIgnoreCase(volume)) {
                root = Environment.getExternalStorageDirectory().getAbsolutePath();
            } else if ("home".equalsIgnoreCase(volume)) {
                root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getAbsolutePath();
            } else {
                root = "/storage/" + volume;
            }
            return relative.isEmpty() ? root : root + "/" + relative;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public static boolean openFolder(Activity activity, String path) {
        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        if (!path.startsWith(root)) {
            return false;
        }
        String relative = path.substring(root.length());
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        while (relative.endsWith("/")) {
            relative = relative.substring(0, relative.length() - 1);
        }
        Uri uri = DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, "primary:" + relative);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR);
        try {
            activity.startActivity(intent);
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    public static boolean isDelayError(ExportState state) {
        return state instanceof ExportState.ApiError apiError && apiError.isDelay();
    }

    public static String delayDate(long until) {
        return LocaleController.formatDateTime(until / 1000, true);
    }

    public static String errorText(ExportState state) {
        if (state instanceof ExportState.IoError ioError) {
            return LocaleController.formatString(R.string.ExportErrorIo, ioError.path);
        }
        if (!(state instanceof ExportState.ApiError apiError)) {
            return "";
        }
        String text = apiError.text;
        if (text.contains("TAKEOUT_INVALID")) {
            return LocaleController.getString(R.string.ExportInvalid);
        } else if (apiError.isDelay()) {
            long until = apiError.delayUntil();
            int hours = (int) ((until - apiError.time) / 3600000L);
            String hoursText = hours <= 0
                    ? LocaleController.getString(R.string.ExportDelayLessThanHour)
                    : LocaleController.formatPluralString("Hours", hours);
            return LocaleController.formatString(R.string.ExportDelay, hoursText, delayDate(until));
        }
        return LocaleController.formatString(R.string.ExportErrorApi, text);
    }

    public static String progressCounter(ExportState.Processing state) {
        int index;
        int count;
        if (state.step == ExportState.Processing.Step.Dialogs || state.step == ExportState.Processing.Step.Topic) {
            index = state.itemIndex;
            count = state.itemCount;
        } else if (state.step == ExportState.Processing.Step.DialogsList) {
            return "";
        } else {
            index = state.entityIndex;
            count = state.entityCount;
        }
        if (count <= 0) {
            return "";
        }
        return LocaleController.formatString(R.string.ImportCount, LocaleController.formatNumber(Math.min(index, count), ' '), LocaleController.formatNumber(count, ' '));
    }

    public static String bytesCounter(ExportState.Processing state) {
        if (state.bytesCount <= 0 || state.bytesName.isEmpty()) {
            return "";
        }
        return LocaleController.formatString(R.string.ImportCount, AndroidUtilities.formatFileSize(Math.min(state.bytesLoaded, state.bytesCount)), AndroidUtilities.formatFileSize(state.bytesCount));
    }

    public static String chatsCounter(ExportState.Processing state) {
        if (state.step != ExportState.Processing.Step.Dialogs || state.entityCount <= 0) {
            return "";
        }
        return LocaleController.formatString(R.string.ImportCount, LocaleController.formatNumber(Math.min(state.entityIndex + 1, state.entityCount), ' '), LocaleController.formatNumber(state.entityCount, ' '));
    }
}
