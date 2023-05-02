package com.exteragram.messenger.components;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;

import androidx.core.content.FileProvider;

import com.exteragram.messenger.ExteraUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
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

public class MessageDetailsPopupWrapper {

    public ActionBarPopupWindow.ActionBarPopupWindowLayout windowLayout;
    private final BaseFragment fragment;
    private final Theme.ResourcesProvider resourcesProvider;
    private String filePath;
    private long stickerSetId;
    private long ownerId = 0;

    public MessageDetailsPopupWrapper(BaseFragment fragment, PopupSwipeBackLayout swipeBackLayout, MessageObject messageObject, Theme.ResourcesProvider resourcesProvider) {
        this.fragment = fragment;
        this.resourcesProvider = resourcesProvider;
        var context = fragment.getParentActivity();
        windowLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, 0, resourcesProvider, ActionBarPopupWindow.ActionBarPopupWindowLayout.FLAG_USE_SWIPEBACK);
        windowLayout.setFitItems(true);

        View gap = new FrameLayout(fragment.getContext());
        gap.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuSeparator, resourcesProvider));

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
        if (messageObject.messageOwner.date != 0) {
            items.add(new Item(R.drawable.msg_calendar2, LocaleController.getString(R.string.Date), formatTime(messageObject.messageOwner.date, true)));
        }
        if (messageObject.messageOwner.fwd_from != null && messageObject.messageOwner.fwd_from.date != 0 && messageObject.messageOwner.fwd_from.date != messageObject.messageOwner.date) {
            items.add(new Item(R.drawable.msg_recent, LocaleController.getString(R.string.ForwardedDate), formatTime(messageObject.messageOwner.fwd_from.date, true)));
        }
        if (messageObject.messageOwner.edit_date != 0 && messageObject.messageOwner.edit_date != messageObject.messageOwner.date && !messageObject.messageOwner.edit_hide) {
            items.add(new Item(R.drawable.msg_edit, LocaleController.getString(R.string.EditedDate), formatTime(messageObject.messageOwner.edit_date, true)));
        }
        items.add(null);
        if (messageObject.getSize() != 0) {
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
                    items.add(new Item(R.drawable.msg_openprofile, LocaleController.getString(R.string.ChannelCreator), LocaleController.getString("Loading", R.string.Loading)));
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
            items.add(new Item(R.drawable.msg_map, LocaleController.getString(R.string.FilePath), LocaleController.getString("Open", R.string.Open)));
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
            items.add(new Item(R.drawable.msg_satellite, LocaleController.getString(R.string.Datacenter), String.format(Locale.ROOT, "DC%d, %s", dc, ExteraUtils.getDCName(dc))));
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
            if (i.resId == R.drawable.msg_openprofile && stickerSetId != 0) {
                ExteraUtils.getInfoAboutOwner(stickerSetId >> 32, (id, username) -> {
                    ownerId = id;
                    i.subtitle = "@" + username;
                    item.setSubtext(i.subtitle);
                }, id -> {
                    i.subtitle = String.valueOf(id);
                    item.setSubtext(i.subtitle);
                });
            }
            item.setTag(i);
            item.setOnClickListener(view -> {
                if (i.resId == R.drawable.msg_map && filePath != null) {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    var uri = FileProvider.getUriForFile(context, ApplicationLoader.getApplicationId() + ".provider", new File(filePath));
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.setDataAndType(uri, messageObject.getMimeType());
                    context.startActivityForResult(Intent.createChooser(intent, LocaleController.getString("ShareFile", R.string.ShareFile)), 500);
                } else if (i.resId == R.drawable.msg_openprofile) {
                    if (ownerId > 0) {
                        Bundle args = new Bundle();
                        args.putLong("user_id", ownerId);
                        ProfileActivity profile = new ProfileActivity(args);
                        fragment.presentFragment(profile);
                    } else {
                        copy(ExteraUtils.getOwnerIds(stickerSetId));
                    }
                } else {
                    copy(i.subtitle != null ? i.subtitle : i.title);
                }
            });
            item.setOnLongClickListener(view -> {
                String text;
                if (i.resId == R.drawable.msg_map && filePath != null) {
                    text = filePath;
                } else if (i.resId == R.drawable.msg_openprofile) {
                    text = ExteraUtils.getOwnerIds(stickerSetId);
                } else if (i.subtitle != null) {
                    text = i.subtitle;
                } else {
                    text = i.title;
                }
                copy(text);
                return true;
            });
        }
    }

    private static class Item {
        int resId;
        String title;
        String subtitle;

        Item(int resId, String title, int subtitle) {
            this(resId, title, String.valueOf(subtitle));
        }

        Item(int resId, String title, String subtitle) {
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

    protected void copy(String text) {
    }
}
