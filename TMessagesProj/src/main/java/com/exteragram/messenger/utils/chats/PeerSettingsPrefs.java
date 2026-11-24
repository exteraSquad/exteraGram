/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.chats;

import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.TLRPC;

import java.util.Map;

public class PeerSettingsPrefs {

    private static final String COMPACTED_KEY = "extera_peer_bar_compacted";
    private static final long COMPACTION_DELAY_MS = 10_000;

    private static final String[] FLAG_KEYS = {
            "dialog_bar_share",
            "dialog_bar_add",
            "dialog_bar_block",
            "dialog_bar_exception",
            "dialog_bar_location",
            "dialog_bar_archived",
            "dialog_bar_invite",
            "dialog_bar_chat_with_channel"
    };

    public static void putBar(SharedPreferences.Editor editor, long dialogId, TLRPC.PeerSettings settings) {
        editor.putBoolean("dialog_bar_report" + dialogId, settings.report_spam);
        putFlag(editor, "dialog_bar_share" + dialogId, settings.share_contact);
        putFlag(editor, "dialog_bar_add" + dialogId, settings.add_contact);
        putFlag(editor, "dialog_bar_block" + dialogId, settings.block_contact);
        putFlag(editor, "dialog_bar_exception" + dialogId, settings.need_contacts_exception);
        putFlag(editor, "dialog_bar_location" + dialogId, settings.report_geo);
        putFlag(editor, "dialog_bar_archived" + dialogId, settings.autoarchived);
        putFlag(editor, "dialog_bar_invite" + dialogId, settings.invite_members);
        editor.putString("dialog_bar_chat_with_admin_title" + dialogId, settings.request_chat_title);
        putFlag(editor, "dialog_bar_chat_with_channel" + dialogId, settings.request_chat_broadcast);
        if (settings.request_chat_date != 0) {
            editor.putInt("dialog_bar_chat_with_date" + dialogId, settings.request_chat_date);
        } else {
            editor.remove("dialog_bar_chat_with_date" + dialogId);
        }
    }

    public static void putPaidMessageStars(SharedPreferences.Editor editor, long dialogId, long stars) {
        if (stars != 0) {
            editor.putLong("dialog_bar_paying_" + dialogId, stars);
        } else {
            editor.remove("dialog_bar_paying_" + dialogId);
        }
    }

    public static void scheduleCompaction(SharedPreferences preferences) {
        AndroidUtilities.runOnUIThread(() -> compact(preferences), COMPACTION_DELAY_MS);
    }

    private static void putFlag(SharedPreferences.Editor editor, String key, boolean value) {
        if (value) {
            editor.putBoolean(key, true);
        } else {
            editor.remove(key);
        }
    }

    private static void compact(SharedPreferences preferences) {
        if (preferences.getBoolean(COMPACTED_KEY, false)) {
            return;
        }
        SharedPreferences.Editor editor = null;
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (isDefaultValue(entry.getKey(), entry.getValue())) {
                if (editor == null) {
                    editor = preferences.edit();
                }
                editor.remove(entry.getKey());
            }
        }
        if (editor != null) {
            editor.putBoolean(COMPACTED_KEY, true).apply();
        }
    }

    private static boolean isDefaultValue(String key, Object value) {
        if (!key.startsWith("dialog_bar_")) {
            return false;
        }
        if (Boolean.FALSE.equals(value)) {
            for (String flagKey : FLAG_KEYS) {
                if (key.startsWith(flagKey)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof Integer && (Integer) value == 0) {
            return key.startsWith("dialog_bar_chat_with_date");
        }
        if (value instanceof Long && (Long) value == 0) {
            return key.startsWith("dialog_bar_paying_");
        }
        return false;
    }
}
