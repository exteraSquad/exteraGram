/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2023

*/

package com.exteragram.messenger.components;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;

import androidx.core.content.FileProvider;

import com.exteragram.messenger.utils.ChatUtils;
import com.google.zxing.Dimension;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PopupSwipeBackLayout;
import org.telegram.ui.ProfileActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

@SuppressWarnings("resource")
public class MessageDetailsPopupWrapper {

    public ActionBarPopupWindow.ActionBarPopupWindowLayout windowLayout;
    private final BaseFragment fragment;
    private final Theme.ResourcesProvider resourcesProvider;
    private String filePath;
    private long stickerSetId;
    private long ownerId = 0;

    private final int SET_OWNER = 0;
    private final int FILE_PATH = 1;

    public MessageDetailsPopupWrapper(BaseFragment fragment, PopupSwipeBackLayout swipeBackLayout, MessageObject messageObject, Theme.ResourcesProvider resourcesProvider) {
        this.fragment = fragment;
        this.resourcesProvider = resourcesProvider;
        var context = fragment.getParentActivity();
        windowLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, 0, resourcesProvider, ActionBarPopupWindow.ActionBarPopupWindowLayout.FLAG_USE_SWIPEBACK);
        windowLayout.setFitItems(true);

        if (swipeBackLayout != null) {
            var backItem = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_arrow_back, LocaleController.getString("Back", R.string.Back), false, resourcesProvider);
            backItem.setOnClickListener(view -> swipeBackLayout.closeForeground());
            windowLayout.addView(createGap(), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));
        }

        ArrayList<Item> items = new ArrayList<>();
        if (messageObject.messageOwner.views > 0) {
            items.add(new Item(R.drawable.msg_view_file, String.format(LocaleController.getPluralString("Views", messageObject.messageOwner.views), AndroidUtilities.formatCount(messageObject.messageOwner.views)), null));
        }
        if (messageObject.messageOwner.forwards > 0) {
            items.add(new Item(R.drawable.msg_forward, String.format(LocaleController.getPluralString("Shares", messageObject.messageOwner.forwards), AndroidUtilities.formatCount(messageObject.messageOwner.forwards)), null));
        }
        if (items.size() > 0) {
            items.add(null);
        }
        items.add(new Item(R.drawable.msg_info, "ID", messageObject.messageOwner.id));
        if (messageObject.messageOwner.date > 0) {
            items.add(new Item(R.drawable.msg_calendar2, LocaleController.getString(R.string.Date), formatTime(messageObject.messageOwner.date, true)));
        }
        if (messageObject.messageOwner.fwd_from != null && messageObject.messageOwner.fwd_from.date > 0 && messageObject.messageOwner.fwd_from.date != messageObject.messageOwner.date) {
            items.add(new Item(R.drawable.msg_recent, LocaleController.getString(R.string.ForwardedDate), formatTime(messageObject.messageOwner.fwd_from.date, true)));
        }
        if (messageObject.messageOwner.edit_date > 0 && messageObject.messageOwner.edit_date != messageObject.messageOwner.date && !messageObject.messageOwner.edit_hide) {
            items.add(new Item(R.drawable.msg_edit, LocaleController.getString(R.string.EditedDate), formatTime(messageObject.messageOwner.edit_date, true)));
        }
        items.add(null);
        if (messageObject.getSize() > 0) {
            items.add(new Item(R.drawable.msg_sendfile, LocaleController.getString(R.string.FileSize), AndroidUtilities.formatFileSize(messageObject.getSize())));
        }
        if (messageObject.getMimeType() != null && !messageObject.getMimeType().isEmpty()) {
            items.add(new Item(R.drawable.msg_media, LocaleController.getString(R.string.MimeType), messageObject.getMimeType()));
        }
        if (MessageObject.getMedia(messageObject.messageOwner) != null && MessageObject.getMedia(messageObject.messageOwner).document != null) {
            for (var attribute : MessageObject.getMedia(messageObject.messageOwner).document.attributes) {
                if (attribute instanceof TLRPC.TL_documentAttributeFilename) {
                    items.add(new Item(R.drawable.msg_log, LocaleController.getString(R.string.FileName), attribute.file_name));
                }
                if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                    stickerSetId = attribute.stickerset.id;
                    items.add(new Item(SET_OWNER, R.drawable.msg_sticker, LocaleController.getString(R.string.ChannelCreator), LocaleController.getString("Loading", R.string.Loading)));
                }
            }
        }

        filePath = messageObject.messageOwner.attachPath;
        if (!TextUtils.isEmpty(filePath)) {
            File temp = new File(filePath);
            if (!temp.exists()) {
                filePath = null;
            }
        }
        if (TextUtils.isEmpty(filePath)) {
            filePath = FileLoader.getInstance(UserConfig.selectedAccount).getPathToMessage(messageObject.messageOwner).toString();
            File temp = new File(filePath);
            if (!temp.exists()) {
                filePath = null;
            }
        }
        if (TextUtils.isEmpty(filePath)) {
            filePath = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(messageObject.getDocument(), true).toString();
            File temp = new File(filePath);
            if (!temp.isFile()) {
                filePath = null;
            }
        }
        if (!TextUtils.isEmpty(filePath)) {
            items.add(new Item(FILE_PATH, R.drawable.msg_map, LocaleController.getString(R.string.FilePath), LocaleController.getString("Open", R.string.Open)));
            if (messageObject.isPhoto() || messageObject.isSticker() || messageObject.isVideoSticker() || messageObject.isVideo() || messageObject.isGif()) {
                try {
                    Dimension resolution = messageObject.isVideo() || messageObject.isVideoSticker() || messageObject.isGif() ? getVideoResolution(filePath) : getPhotoResolution(filePath);
                    items.add(new Item(R.drawable.msg_photo_crop, LocaleController.getString(R.string.Resolution), resolution.toString()));
                } catch (Exception ignored) {}
            }
            if (messageObject.isMusic() || messageObject.isVoice() || messageObject.isRoundVideo() || messageObject.isVideo() || messageObject.isGif()) {
                int bitrate = getBitrate(filePath);
                if (bitrate > 0) {
                    items.add(new Item(R.drawable.msg_noise_on, LocaleController.getString(R.string.Bitrate), bitrate + " Kbps"));
                }
            }
        }

        int dc = 0;
        if (MessageObject.getMedia(messageObject.messageOwner) != null) {
            if (MessageObject.getMedia(messageObject.messageOwner).photo != null && MessageObject.getMedia(messageObject.messageOwner).photo.dc_id > 0) {
                dc = MessageObject.getMedia(messageObject.messageOwner).photo.dc_id;
            } else if (MessageObject.getMedia(messageObject.messageOwner).document != null && MessageObject.getMedia(messageObject.messageOwner).document.dc_id > 0) {
                dc = MessageObject.getMedia(messageObject.messageOwner).document.dc_id;
            } else if (MessageObject.getMedia(messageObject.messageOwner).webpage != null && MessageObject.getMedia(messageObject.messageOwner).webpage.photo != null && MessageObject.getMedia(messageObject.messageOwner).webpage.photo.dc_id > 0) {
                dc = MessageObject.getMedia(messageObject.messageOwner).webpage.photo.dc_id;
            } else if (MessageObject.getMedia(messageObject.messageOwner).webpage != null && MessageObject.getMedia(messageObject.messageOwner).webpage.document != null && MessageObject.getMedia(messageObject.messageOwner).webpage.document.dc_id > 0) {
                dc = MessageObject.getMedia(messageObject.messageOwner).webpage.document.dc_id;
            }
        }
        if (dc != 0) {
            items.add(new Item(R.drawable.msg_satellite, LocaleController.getString(R.string.Datacenter), String.format(Locale.ROOT, "DC%d, %s", dc, ChatUtils.getDCName(dc))));
        }

        if (items.get(items.size() - 1) == null) {
            items.remove(items.size() - 1);
        }
        for (Item i : items) {
            if (i == null) {
                windowLayout.addView(createGap(), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));
                continue;
            }
            var item = ActionBarMenuItem.addItem(windowLayout, i.resId, i.title, false, resourcesProvider);
            if (i.subtitle != null) {
                item.setSubtext(i.subtitle);
                item.setItemHeight(56);
            }
            if (i.id == SET_OWNER && stickerSetId != 0) {
                ChatUtils.searchById(stickerSetId >> 32, user -> {
                    if (user != null) {
                        ownerId = user.id;
                        if (!TextUtils.isEmpty(UserObject.getPublicUsername(user))) {
                            i.subtitle = "@" + UserObject.getPublicUsername(user);
                        } else {
                            i.subtitle = ContactsController.formatName(user);
                        }
                        item.setSubtext(i.subtitle);
                    } else {
                        i.subtitle = String.valueOf(stickerSetId >> 32);
                        item.setSubtext(i.subtitle);
                    }
                });
            }
            item.setTag(i);
            item.setOnClickListener(view -> {
                if (i.id == FILE_PATH && filePath != null) {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    var uri = FileProvider.getUriForFile(context, ApplicationLoader.getApplicationId() + ".provider", new File(filePath));
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.setDataAndType(uri, messageObject.getMimeType());
                    context.startActivityForResult(Intent.createChooser(intent, LocaleController.getString("ShareFile", R.string.ShareFile)), 500);
                } else if (i.id == SET_OWNER) {
                    if (ownerId > 0) {
                        Bundle args = new Bundle();
                        args.putLong("user_id", ownerId);
                        ProfileActivity profile = new ProfileActivity(args);
                        fragment.presentFragment(profile);
                    } else {
                        copy(ChatUtils.getOwnerIds(stickerSetId));
                    }
                } else {
                    copy(i.subtitle != null ? i.subtitle : i.title);
                }
            });
            item.setOnLongClickListener(view -> {
                String text;
                if (i.id == FILE_PATH && filePath != null) {
                    text = filePath;
                } else if (i.id == SET_OWNER) {
                    text = ChatUtils.getOwnerIds(stickerSetId);
                } else {
                    text = i.subtitle != null ? i.subtitle : i.title;
                }
                copy(text);
                return true;
            });
        }
    }

    private static class Item {
        int id;
        int resId;
        String title;
        String subtitle;

        Item(int resId, String title, String subtitle) {
            this(-1, resId, title, subtitle);
        }

        Item(int resId, String title, int subtitle) {
            this(-1, resId, title, String.valueOf(subtitle));
        }

        Item(int id, int resId, String title, String subtitle) {
            this.id = id;
            this.resId = resId;
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    private String formatTime(int timestamp, boolean full) {
        if (timestamp == 0x7ffffffe) {
            return LocaleController.getString(R.string.SendWhenOnline);
        } else {
            return full ? LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime,
                    LocaleController.getInstance().formatterYear.format(new Date(timestamp * 1000L)),
                    LocaleController.getInstance().formatterDayWithSeconds.format(new Date(timestamp * 1000L))
            ) : LocaleController.formatDateAudio(timestamp, true);
        }
    }

    private View createGap() {
        View gap = new FrameLayout(fragment.getContext());
        gap.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuSeparator, resourcesProvider));
        return gap;
    }

    public static int getBitrate(String filePath) {
        int bitrate = -1;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(filePath);
            bitrate = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)) / 1000;
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            retriever.release();
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
        return bitrate;
    }

    public static Dimension getPhotoResolution(String filePath) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);
        return new Dimension(options.outWidth, options.outHeight);
    }

    public static Dimension getVideoResolution(String filePath) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        int width = 0, height = 0;
        try {
            retriever.setDataSource(filePath);
            width = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            height = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            retriever.release();
        } catch (Throwable throwable) {
            FileLog.e(throwable);
        }
        return new Dimension(width, height);
    }

    protected void copy(String text) {
    }
}
