/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.player;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.text.TextUtils;

import com.exteragram.messenger.player.motion.ArtworkRepository;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public final class PlayerCovers {

    private static final int THUMB_SIZE = 360;
    private static final int BIG_THUMB_SIZE = 1280;
    private static final int LOW_RES_SIZE = 640;

    private PlayerCovers() {
    }

    private static TLRPC.PhotoSize getDocumentThumb(MessageObject messageObject, int size) {
        final TLRPC.Document document = messageObject.getDocument();
        final TLRPC.PhotoSize thumb = document != null ? FileLoader.getClosestPhotoSizeWithSize(document.thumbs, size) : null;
        return thumb instanceof TLRPC.TL_photoSize || thumb instanceof TLRPC.TL_photoSizeProgressive ? thumb : null;
    }

    private static ImageLocation getDocumentThumbLocation(MessageObject messageObject, int size) {
        final TLRPC.PhotoSize thumb = getDocumentThumb(messageObject, size);
        return thumb != null ? ImageLocation.getForDocument(thumb, messageObject.getDocument()) : null;
    }

    public static boolean needsArtwork(MessageObject messageObject) {
        if (messageObject == null || messageObject.isVoice() || messageObject.isRoundVideo()) {
            return false;
        }
        final AudioInfo audioInfo = getPlayingAudioInfo(messageObject);
        final Bitmap embedded = audioInfo != null ? audioInfo.getCover() : null;
        if (embedded != null) {
            return Math.max(embedded.getWidth(), embedded.getHeight()) < LOW_RES_SIZE;
        }
        final TLRPC.PhotoSize thumb = getDocumentThumb(messageObject, BIG_THUMB_SIZE);
        return thumb == null || Math.max(thumb.w, thumb.h) < LOW_RES_SIZE;
    }

    public static ImageLocation getThumbLocation(MessageObject messageObject) {
        final ImageLocation documentThumb = getDocumentThumbLocation(messageObject, THUMB_SIZE);
        if (documentThumb != null) {
            return documentThumb;
        }
        final String smallArtworkUrl = messageObject.getArtworkUrl(true);
        if (smallArtworkUrl != null) {
            return ImageLocation.getForPath(smallArtworkUrl);
        }
        return null;
    }

    public static AudioInfo getPlayingAudioInfo(MessageObject messageObject) {
        final MediaController controller = MediaController.getInstance();
        final MessageObject playing = controller.getPlayingMessageObject();
        if (playing == null || messageObject == null || playing.getId() != messageObject.getId() || playing.getDialogId() != messageObject.getDialogId()) {
            return null;
        }
        return controller.getAudioInfo();
    }

    public static boolean hasCover(MessageObject messageObject) {
        if (messageObject == null) {
            return false;
        }
        final AudioInfo audioInfo = getPlayingAudioInfo(messageObject);
        if (audioInfo != null && audioInfo.getCover() != null) {
            return true;
        }
        return !TextUtils.isEmpty(messageObject.getArtworkUrl(false)) || getThumbLocation(messageObject) != null;
    }

    public static boolean setCover(ImageReceiver receiver, MessageObject messageObject, int sizeDp, boolean big) {
        if (messageObject == null) {
            clearCover(receiver);
            return false;
        }
        final AudioInfo audioInfo = getPlayingAudioInfo(messageObject);
        final String filter = sizeDp + "_" + sizeDp;
        final String artwork = big && needsArtwork(messageObject) ? ArtworkRepository.getCached(TrackQuery.forMessage(messageObject)) : null;
        if (artwork != null) {
            if (audioInfo != null && audioInfo.getCover() != null) {
                receiver.setImage(ImageLocation.getForPath(artwork), filter, new BitmapDrawable(ApplicationLoader.applicationContext.getResources(), audioInfo.getCover()), null, messageObject, 1);
            } else {
                final ImageLocation documentCover = getDocumentThumbLocation(messageObject, BIG_THUMB_SIZE);
                receiver.setImage(ImageLocation.getForPath(artwork), filter, documentCover != null ? documentCover : getThumbLocation(messageObject), filter, 0, null, messageObject, 1);
            }
            return true;
        }
        if (audioInfo != null && audioInfo.getCover() != null) {
            receiver.setImageBitmap(big ? audioInfo.getCover() : audioInfo.getSmallCover() != null ? audioInfo.getSmallCover() : audioInfo.getCover());
            return true;
        }
        final ImageLocation thumbLocation = getThumbLocation(messageObject);
        final ImageLocation documentCover = big ? getDocumentThumbLocation(messageObject, BIG_THUMB_SIZE) : null;
        if (documentCover != null) {
            receiver.setImage(documentCover, filter, thumbLocation, filter, 0, null, messageObject, 1);
            return true;
        }
        final String artworkUrl = big ? messageObject.getArtworkUrl(false) : null;
        if (!TextUtils.isEmpty(artworkUrl)) {
            receiver.setImage(ImageLocation.getForPath(artworkUrl), filter, thumbLocation, filter, 0, null, messageObject, 1);
            return true;
        } else if (thumbLocation != null) {
            receiver.setImage(thumbLocation, filter, null, null, 0, null, messageObject, 1);
            return true;
        }
        clearCover(receiver);
        return false;
    }

    private static void clearCover(ImageReceiver receiver) {
        receiver.setImageBitmap((Bitmap) null);
    }

    public static void preloadNeighbors(int currentAccount) {
        forEachNeighbor(messageObject -> preload(currentAccount, messageObject));
    }

    public static void onFileLoaded(String fileName) {
        forEachNeighbor(messageObject -> {
            if (fileName.equals(FileLoader.getAttachFileName(messageObject.getDocument()))) {
                AudioInfoCache.preload(messageObject);
            }
        });
    }

    private static void forEachNeighbor(Utilities.Callback<MessageObject> action) {
        final MediaController controller = MediaController.getInstance();
        final ArrayList<MessageObject> playlist = controller.getPlaylist();
        if (playlist.size() <= 1) {
            return;
        }
        final int playingIndex = controller.getPlayingMessageObjectNum();
        if (playingIndex < 0 || playingIndex >= playlist.size()) {
            return;
        }
        final int nextIndex = (playingIndex + 1) % playlist.size();
        final int prevIndex = (playingIndex - 1 + playlist.size()) % playlist.size();
        action.run(playlist.get(nextIndex));
        if (prevIndex != nextIndex) {
            action.run(playlist.get(prevIndex));
        }
    }

    private static void preload(int currentAccount, MessageObject messageObject) {
        if (messageObject == null) {
            return;
        }
        AudioInfoCache.preload(messageObject);
        final ImageLocation thumbLocation = getThumbLocation(messageObject);
        if (thumbLocation == null) {
            return;
        }
        if (thumbLocation.path != null) {
            ImageLoader.getInstance().preloadArtwork(thumbLocation.path);
        } else {
            FileLoader.getInstance(currentAccount).loadFile(thumbLocation, messageObject, null, FileLoader.PRIORITY_LOW, 1);
        }
    }

    public static TLObject getVoicePeer(MessageObject messageObject) {
        final int currentAccount = messageObject.currentAccount;
        final MessagesController controller = MessagesController.getInstance(currentAccount);
        final TLRPC.Message message = messageObject.messageOwner;
        if (messageObject.isOutOwner()) {
            return UserConfig.getInstance(currentAccount).getCurrentUser();
        }
        long dialogId = 0;
        if (message != null && message.fwd_from != null && message.fwd_from.from_id != null) {
            dialogId = DialogObject.getPeerDialogId(message.fwd_from.from_id);
        }
        if (dialogId == 0) {
            dialogId = messageObject.getFromChatId();
        }
        if (dialogId == 0) {
            dialogId = messageObject.getDialogId();
        }
        return dialogId > 0 ? controller.getUser(dialogId) : controller.getChat(-dialogId);
    }
}
