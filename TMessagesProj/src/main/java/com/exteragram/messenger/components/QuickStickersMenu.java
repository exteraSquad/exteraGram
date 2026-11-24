/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BackupImageView;

import java.util.ArrayList;

@SuppressLint("ViewConstructor")
public class QuickStickersMenu extends QuickTilesMenu implements NotificationCenter.NotificationCenterDelegate {

    public interface Delegate {
        void sendSticker(TLRPC.Document sticker, MessageObject.SendAnimationData animationData);

        void openStickers();
    }

    private static final int MAX_STICKERS = 5;
    private static final String IMAGE_FILTER = "66_66";

    private final Delegate delegate;
    private final int currentAccount;
    private boolean waitingForRecents;

    public static QuickStickersMenu show(ChatActivity chat, View anchor, Delegate delegate) {
        ViewGroup container = getContainer(chat);
        if (container == null) {
            return null;
        }
        QuickStickersMenu menu = new QuickStickersMenu(chat, anchor, delegate, container);
        menu.start();
        return menu;
    }

    private QuickStickersMenu(ChatActivity chat, View anchor, Delegate delegate, ViewGroup container) {
        super(chat, anchor, container, MAX_STICKERS);
        this.delegate = delegate;
        this.currentAccount = chat.getCurrentAccount();
    }

    @Override
    protected void load() {
        MediaDataController controller = MediaDataController.getInstance(currentAccount);
        if (controller.getRecentStickersNoCopy(MediaDataController.TYPE_IMAGE).isEmpty()) {
            waitingForRecents = true;
            NotificationCenter.getInstance(currentAccount).addWeakObserver(this, NotificationCenter.recentDocumentsDidLoad);
            controller.loadRecents(MediaDataController.TYPE_IMAGE, false, true, false);
        } else {
            addStickers();
        }
    }

    private void addStickers() {
        ArrayList<TLRPC.Document> recent = MediaDataController.getInstance(currentAccount).getRecentStickersNoCopy(MediaDataController.TYPE_IMAGE);
        int count = Math.min(slots, recent.size());
        for (int i = 0; i < count; i++) {
            addTile(createStickerTile(recent.get(i)), i, false);
        }
        if (count == 0) {
            dismiss(false);
        } else {
            onContentReady();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.recentDocumentsDidLoad && !(Boolean) args[0] && (Integer) args[1] == MediaDataController.TYPE_IMAGE) {
            stopWaiting();
            if (!dismissing) {
                addStickers();
            }
        }
    }

    private void stopWaiting() {
        if (waitingForRecents) {
            waitingForRecents = false;
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recentDocumentsDidLoad);
        }
    }

    @Override
    protected void onFinished() {
        stopWaiting();
    }

    @Override
    protected int getOpenFullIcon() {
        return R.drawable.msg_sticker;
    }

    @Override
    protected CharSequence getOpenFullText() {
        return LocaleController.getString(R.string.StickersName);
    }

    @Override
    protected void openFull() {
        delegate.openStickers();
    }

    @Override
    protected void drawAnchorBackground(Canvas canvas, int width, int height, Paint paint) {
        AndroidUtilities.rectTmp.set(dp(1), dp(3), width - dp(1), height - dp(3));
        canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(21), dp(21), paint);
    }

    private BackupImageView createStickerTile(TLRPC.Document sticker) {
        BackupImageView tile = new BackupImageView(getContext());
        tile.setAspectFit(true);
        tile.setLayerNum(1);
        TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(sticker.thumbs, 90);
        SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(sticker, Theme.key_windowBackgroundGray, 1.0f, 1f, chat.getResourceProvider());
        String parent = "recent";
        if (MessageObject.canAutoplayAnimatedSticker(sticker)) {
            if (svgThumb != null) {
                tile.getImageReceiver().setImage(ImageLocation.getForDocument(sticker), IMAGE_FILTER, ImageLocation.getForDocument(thumb, sticker), null, null, null, svgThumb, 0, null, parent, 1);
            } else {
                tile.getImageReceiver().setImage(ImageLocation.getForDocument(sticker), IMAGE_FILTER, ImageLocation.getForDocument(thumb, sticker), null, (String) null, parent, 1);
            }
        } else if (thumb != null) {
            tile.getImageReceiver().setImage(ImageLocation.getForDocument(thumb, sticker), IMAGE_FILTER, svgThumb, "webp", parent, 1);
        } else {
            tile.getImageReceiver().setImage(ImageLocation.getForDocument(sticker), IMAGE_FILTER, svgThumb, "webp", parent, 1);
        }
        tile.setContentDescription(MessageObject.findAnimatedEmojiEmoticon(sticker, null));
        tile.setOnClickListener(v -> {
            if (dismissing) {
                return;
            }
            MessageObject.SendAnimationData animationData = createAnimationData(tile);
            if (animationData != null) {
                dismissWithout(tile);
            } else {
                dismiss(true);
            }
            delegate.sendSticker(sticker, animationData);
        });
        return tile;
    }

    private MessageObject.SendAnimationData createAnimationData(BackupImageView tile) {
        ImageReceiver imageReceiver = tile.getImageReceiver();
        if (!imageReceiver.hasNotThumb()) {
            return null;
        }
        getUnscaledPosition(tile, placePosition);
        View tileView = (View) tile.getParent();
        MessageObject.SendAnimationData data = new MessageObject.SendAnimationData();
        data.x = placePosition[0] + tile.getWidth() / 2f;
        data.y = placePosition[1] + tile.getHeight() / 2f;
        data.width = tile.getWidth() * tileView.getScaleX();
        data.height = tile.getHeight() * tileView.getScaleY();
        return data;
    }
}
