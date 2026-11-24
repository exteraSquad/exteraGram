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
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.PhotoViewer;

import java.io.File;
import java.util.ArrayList;

@SuppressLint("ViewConstructor")
public class QuickAttachMenu extends QuickTilesMenu {

    public interface Delegate {
        void openCamera();

        void openGallery();

        void sendPhoto(MediaController.PhotoEntry entry, boolean notify, int scheduleDate, int scheduleRepeatPeriod, boolean forceDocument);
    }

    private static final int MAX_PHOTOS = 4;

    private static Bitmap cameraThumbCache;
    private static long cameraThumbModified;
    private static boolean galleryRequested;

    private final Delegate delegate;
    private BackupImageView cameraPreview;
    private View cameraTile;

    public static QuickAttachMenu show(ChatActivity chat, View anchor, Delegate delegate) {
        ViewGroup container = getContainer(chat);
        if (container == null) {
            return null;
        }
        QuickAttachMenu menu = new QuickAttachMenu(chat, anchor, delegate, container);
        menu.start();
        return menu;
    }

    private QuickAttachMenu(ChatActivity chat, View anchor, Delegate delegate, ViewGroup container) {
        super(chat, anchor, container, 1 + MAX_PHOTOS);
        this.delegate = delegate;
        addTile(createCameraTile(), 0, true);
    }

    private View createCameraTile() {
        Context context = getContext();
        FrameLayout tile = new FrameLayout(context);
        BackupImageView preview = new BackupImageView(context);
        preview.setRoundRadius(dp(TILE_RADIUS));
        preview.setImageResource(R.drawable.icplaceholder);
        tile.addView(preview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.camera);
        icon.setColorFilter(Color.WHITE);
        tile.addView(icon, LayoutHelper.createFrame(22, 22, Gravity.CENTER));
        tile.setContentDescription(LocaleController.getString(R.string.VoipCamera));
        tile.setOnClickListener(v -> {
            if (dismissing) {
                return;
            }
            dismiss(true);
            delegate.openCamera();
        });
        cameraPreview = preview;
        cameraTile = tile;
        return tile;
    }

    private BackupImageView createPhotoTile(MediaController.PhotoEntry entry, int index) {
        BackupImageView tile = new BackupImageView(getContext());
        tile.setRoundRadius(dp(TILE_RADIUS));
        tile.setBackground(Theme.createRoundRectDrawable(dp(TILE_RADIUS), Theme.getColor(Theme.key_chat_attachPhotoBackground, chat.getResourceProvider())));
        tile.setOrientation(entry.orientation, entry.invert, true);
        tile.setImage("thumb://" + entry.imageId + ":" + entry.path, null, Theme.chat_attachEmptyDrawable);
        tile.setContentDescription(LocaleController.getString(R.string.AttachPhoto) + " " + index);
        tile.setOnClickListener(v -> {
            if (dismissing || viewerOpen || PhotoViewer.getInstance().isVisible()) {
                return;
            }
            MediaController.PhotoEntry selected = new MediaController.PhotoEntry(entry.bucketId, entry.imageId, entry.dateTaken,
                    entry.path, entry.orientation, false, entry.width, entry.height, entry.size);
            selected.invert = entry.invert;
            Runnable open = () -> openPhoto(tile, selected);
            if (selected.loadLivePhoto(open)) {
                open.run();
            }
        });
        return tile;
    }

    @Override
    protected int getOpenFullIcon() {
        return R.drawable.msg_photos;
    }

    @Override
    protected CharSequence getOpenFullText() {
        return LocaleController.getString(R.string.AllMedia);
    }

    @Override
    protected void openFull() {
        delegate.openGallery();
    }

    @Override
    protected void load() {
        MediaController.AlbumEntry album = MediaController.allPhotosAlbumEntry;
        ArrayList<MediaController.PhotoEntry> cached = album == null ? null : new ArrayList<>(album.photos.subList(0, Math.min(slots - 1, album.photos.size())));
        File thumbFile = new File(ApplicationLoader.getFilesDirFixed(), "cthumb.jpg");
        long thumbModified = thumbFile.lastModified();
        Bitmap knownThumb = thumbModified == cameraThumbModified ? cameraThumbCache : null;
        if (cached != null && thumbModified == cameraThumbModified) {
            showContent(knownThumb, cached);
            return;
        }
        if (album == null && !galleryRequested) {
            galleryRequested = true;
            MediaController.loadGalleryPhotosAlbums(0);
        }
        int count = slots - 1;
        Utilities.globalQueue.postRunnable(() -> {
            Bitmap thumb = knownThumb;
            if (thumb == null && thumbModified != 0) {
                try {
                    thumb = BitmapFactory.decodeFile(thumbFile.getAbsolutePath());
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
            ArrayList<MediaController.PhotoEntry> photos = cached != null ? cached : queryRecentPhotos(count);
            Bitmap cameraThumb = thumb;
            AndroidUtilities.runOnUIThread(() -> {
                cameraThumbCache = cameraThumb;
                cameraThumbModified = thumbModified;
                if (!dismissing) {
                    showContent(cameraThumb, photos);
                }
            });
        });
    }

    private void showContent(Bitmap cameraThumb, ArrayList<MediaController.PhotoEntry> photos) {
        if (cameraThumb != null) {
            cameraPreview.setImageBitmap(cameraThumb);
            invalidateTileBlur(cameraTile);
        }
        for (int i = 0; i < photos.size(); i++) {
            addTile(createPhotoTile(photos.get(i), i + 1), i + 1, true);
        }
        onContentReady();
    }

    private static ArrayList<MediaController.PhotoEntry> queryRecentPhotos(int count) {
        ArrayList<MediaController.PhotoEntry> photos = new ArrayList<>();
        if (count <= 0) {
            return photos;
        }
        String[] projection = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.ORIENTATION,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
                MediaStore.Images.Media.SIZE
        };
        String sortOrder = (Build.VERSION.SDK_INT > 28 ? MediaStore.Images.Media.DATE_MODIFIED : MediaStore.Images.Media.DATE_TAKEN) + " DESC";
        ContentResolver resolver = ApplicationLoader.applicationContext.getContentResolver();
        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        Cursor cursor;
        if (Build.VERSION.SDK_INT >= 30) {
            Bundle args = new Bundle();
            args.putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder);
            args.putString(ContentResolver.QUERY_ARG_SQL_LIMIT, String.valueOf(count * 2));
            cursor = resolver.query(uri, projection, args, null);
        } else {
            cursor = resolver.query(uri, projection, null, null, sortOrder);
        }
        try (Cursor c = cursor) {
            while (c != null && photos.size() < count && c.moveToNext()) {
                String path = c.getString(2);
                if (TextUtils.isEmpty(path)) {
                    continue;
                }
                photos.add(new MediaController.PhotoEntry(c.getInt(1), c.getInt(0), 0, path, c.getInt(3), false, c.getInt(4), c.getInt(5), c.getLong(6)));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return photos;
    }

    private void openPhoto(BackupImageView tile, MediaController.PhotoEntry entry) {
        if (dismissing || chat.getParentActivity() == null || chat.getChatActivityEnterView() == null || PhotoViewer.getInstance().isVisible()) {
            return;
        }
        AndroidUtilities.hideKeyboard(chat.getChatActivityEnterView().getEditField());
        entry.caption = chat.getChatActivityEnterView().getFieldText();
        ArrayList<Object> photos = new ArrayList<>();
        photos.add(entry);
        PhotoViewer viewer = PhotoViewer.getInstance();
        viewer.setParentActivity(chat, chat.getResourceProvider());
        viewer.openPhotoForSelect(photos, 0, 2, false, new PhotoViewer.EmptyPhotoViewerProvider() {
            @Override
            public PhotoViewer.PlaceProviderObject getPlaceForPhoto(MessageObject messageObject, TLRPC.FileLocation fileLocation, int index, boolean needPreview, boolean closing) {
                if (dismissing || !tile.isAttachedToWindow()) {
                    return null;
                }
                getUnscaledPosition(tile, placePosition);
                PhotoViewer.PlaceProviderObject place = new PhotoViewer.PlaceProviderObject();
                place.viewX = (int) placePosition[0];
                place.viewY = (int) placePosition[1];
                place.parentView = QuickAttachMenu.this;
                place.imageReceiver = tile.getImageReceiver();
                place.thumb = place.imageReceiver.getBitmapSafe();
                place.scale = ((View) tile.getParent()).getScaleX();
                int radius = dp(TILE_RADIUS);
                place.radius = new int[]{radius, radius, radius, radius};
                return place;
            }

            @Override
            public void onPreClose() {
                onViewerPreClose();
            }

            @Override
            public void onClose() {
                onViewerClosed();
            }

            @Override
            public boolean canCaptureMorePhotos() {
                return false;
            }

            @Override
            public void sendButtonPressed(int index, VideoEditedInfo videoEditedInfo, boolean notify, int scheduleDate, int scheduleRepeatPeriod, boolean forceDocument) {
                entry.editedInfo = videoEditedInfo;
                dismiss(false);
                delegate.sendPhoto(entry, notify, scheduleDate, scheduleRepeatPeriod, forceDocument);
            }
        }, chat);
        if (viewer.isVisible()) {
            onViewerOpened(tile);
        }
    }
}
