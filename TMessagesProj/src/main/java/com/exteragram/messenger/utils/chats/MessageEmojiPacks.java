/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.chats;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_keyboard;
import org.telegram.ui.Components.AnimatedEmojiDrawable;

import java.util.ArrayList;

public final class MessageEmojiPacks {

    private MessageEmojiPacks() {
    }

    public static ArrayList<TLRPC.InputStickerSet> collect(int currentAccount, MessageObject message) {
        final ArrayList<TLRPC.InputStickerSet> sets = new ArrayList<>();
        if (message == null || message.messageOwner == null) {
            return sets;
        }
        final TLRPC.TL_messageReactions reactions = message.messageOwner.reactions;
        if (reactions != null) {
            for (TLRPC.ReactionCount count : reactions.results) {
                if (count.reaction instanceof TLRPC.TL_reactionCustomEmoji) {
                    add(sets, AnimatedEmojiDrawable.findStickerSet(currentAccount, ((TLRPC.TL_reactionCustomEmoji) count.reaction).document_id));
                }
            }
        }
        if (message.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) {
            for (TL_keyboard.KeyboardInlineButtonRow row : ((TLRPC.TL_replyInlineMarkup) message.messageOwner.reply_markup).rows) {
                for (TL_keyboard.KeyboardInlineButton button : row.buttons) {
                    if (button.style != null && button.style.icon != 0) {
                        add(sets, AnimatedEmojiDrawable.findStickerSet(currentAccount, button.style.icon));
                    }
                }
            }
        }
        return sets;
    }

    public static void addAll(ArrayList<TLRPC.InputStickerSet> target, ArrayList<TLRPC.InputStickerSet> sets) {
        for (TLRPC.InputStickerSet set : sets) {
            add(target, set);
        }
    }

    private static void add(ArrayList<TLRPC.InputStickerSet> target, TLRPC.InputStickerSet set) {
        if (set == null) {
            return;
        }
        for (TLRPC.InputStickerSet existing : target) {
            if (existing.id == set.id) {
                return;
            }
        }
        target.add(set);
    }
}
