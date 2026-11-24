/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.messageexport.model;

import android.content.Context;

import com.exteragram.messenger.messageexport.appearance.MessageExportAppearance;

import org.telegram.messenger.ChatMessageSharedResources;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.EmojiThemes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;

public final class MessageExportSession {

    public interface Listener {
        void onContentChanged();

        void onBeforeChange();
    }

    public final MessageExportDocument document;
    public final MessageExportAppearance appearance;
    public final ChatMessageSharedResources sharedResources;

    private final HiddenText hiddenText = new HiddenText();
    private final ReplyMasking replyMasking = new ReplyMasking();
    private final IdentityHashMap<MessageObject, Boolean> mediaCopies = new IdentityHashMap<>();
    private final HashSet<Long> hiddenAvatars = new HashSet<>();
    private Listener listener;
    private int providerGeneration;

    public MessageExportSession(Context context, MessageExportDocument document, MessageExportAppearance appearance) {
        this.document = document;
        this.appearance = appearance;
        this.sharedResources = new ChatMessageSharedResources(context);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public int getProviderGeneration() {
        return providerGeneration;
    }

    public void setAppearance(EmojiThemes preset, boolean dark) {
        beforeChange();
        appearance.set(preset, dark);
        providerGeneration++;
        document.touch();
        document.rebuild();
        notifyChanged();
    }

    public void setExcluded(ArrayList<MessageObject> messages, boolean excluded) {
        beforeChange();
        for (int i = 0; i < messages.size(); i++) {
            document.setExcluded(messages.get(i), excluded);
        }
        document.rebuild();
        notifyChanged();
    }

    public void setDateHidden(MessageObject dateObject, boolean hidden) {
        beforeChange();
        document.setDateHidden(dateObject, hidden);
        document.rebuild();
        notifyChanged();
    }

    public void toggleMediaSpoiler(MessageObject message) {
        beforeChange();
        TLRPC.Message owner = message.messageOwner;
        if (owner.media == null) {
            return;
        }
        if (!mediaCopies.containsKey(message)) {
            TLRPC.MessageMedia copy = TLObject.deepCopy(owner.media, TLRPC.MessageMedia::TLdeserialize);
            if (copy == null) {
                return;
            }
            owner.media = copy;
            mediaCopies.put(message, Boolean.TRUE);
        }
        owner.media.spoiler = !owner.media.spoiler;
        message.isMediaSpoilersRevealed = false;
        syncReplies();
        invalidateLayout(message);
    }

    public void toggleRichMediaSpoiler(MessageObject message, TL_iv.PageBlock block) {
        beforeChange();
        if (block instanceof TL_iv.pageBlockPhoto photo) {
            photo.spoiler = !photo.spoiler;
        } else if (block instanceof TL_iv.pageBlockVideo video) {
            video.spoiler = !video.spoiler;
        } else {
            return;
        }
        invalidateLayout(message);
    }

    public void invalidateAll() {
        beforeChange();
        document.invalidateAll();
        document.rebuild();
        notifyChanged();
    }

    public void invalidateLayout(MessageObject message) {
        beforeChange();
        message.forceUpdate = true;
        document.touch();
        notifyChanged();
    }

    public MessageObject hideText(MessageObject message, boolean caption, int start, int end) {
        beforeChange();
        MessageObject target = resolveTextOwner(message, caption);
        if (!hiddenText.hide(target, caption, start, end)) {
            return null;
        }
        forceGroupUpdate(target);
        syncReplies();
        document.touch();
        notifyChanged();
        return target;
    }

    public boolean isHidden(MessageObject message, boolean caption, int start, int end) {
        return hiddenText.isHidden(resolveTextOwner(message, caption), caption, start, end);
    }

    public void unhide(MessageObject message, boolean caption, int start, int end) {
        beforeChange();
        MessageObject target = resolveTextOwner(message, caption);
        if (hiddenText.unhide(target, caption, start, end)) {
            forceGroupUpdate(target);
            syncReplies();
            document.touch();
            notifyChanged();
        }
    }

    public void undoLastHide(MessageObject message) {
        beforeChange();
        hiddenText.undoLast(message);
        forceGroupUpdate(message);
        syncReplies();
        document.touch();
        notifyChanged();
    }

    private MessageObject resolveTextOwner(MessageObject message, boolean caption) {
        MessageObject.GroupedMessages group = caption && message.caption == null ? findGroup(message) : null;
        if (group == null) {
            return message;
        }
        MessageObject owner = null;
        for (int i = 0; i < group.messages.size(); i++) {
            MessageObject m = group.messages.get(i);
            if (m.caption != null) {
                if (owner != null) {
                    return message;
                }
                owner = m;
            }
        }
        return owner != null ? owner : message;
    }

    private void forceGroupUpdate(MessageObject message) {
        MessageObject.GroupedMessages group = findGroup(message);
        if (group != null) {
            for (int i = 0; i < group.messages.size(); i++) {
                group.messages.get(i).forceUpdate = true;
            }
        }
    }

    private MessageObject.GroupedMessages findGroup(MessageObject message) {
        for (int i = 0; i < document.blocks.size(); i++) {
            MessageExportDocument.Block block = document.blocks.get(i);
            if (block.group != null && block.messages.contains(message)) {
                return block.group;
            }
        }
        return null;
    }

    public boolean isAvatarHidden(long peerId) {
        return hiddenAvatars.contains(peerId);
    }

    public void toggleAvatarHidden(long peerId) {
        beforeChange();
        if (!hiddenAvatars.remove(peerId)) {
            hiddenAvatars.add(peerId);
        }
        document.touch();
        notifyChanged();
    }

    public boolean hasReply(MessageObject message) {
        return replyMasking.hasReply(message);
    }

    public void toggleReplyMediaHidden(MessageObject message) {
        beforeChange();
        MessageObject target = replyMasking.findMediaTarget(message, document.getMessages());
        if (target != null) {
            toggleMediaSpoiler(target);
            return;
        }
        replyMasking.toggleMedia(message);
        syncReplies();
        document.touch();
        notifyChanged();
    }

    public boolean isReplyAuthorHidden(MessageObject message) {
        return replyMasking.isAuthorHidden(message);
    }

    public void toggleReplyHidden(MessageObject message) {
        beforeChange();
        replyMasking.toggle(message, document.getMessages());
        syncReplies();
        document.touch();
        notifyChanged();
    }

    private void syncReplies() {
        replyMasking.sync(document.getMessages(), hiddenText, document.context.account);
    }

    public void ensureHiddenSpans(MessageObject message) {
        hiddenText.ensure(message);
    }

    private void beforeChange() {
        if (listener != null) {
            listener.onBeforeChange();
        }
    }

    private void notifyChanged() {
        if (listener != null) {
            listener.onContentChanged();
        }
    }
}
