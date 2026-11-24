/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.components;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

import com.exteragram.messenger.utils.chats.ChatUtils;
import com.exteragram.messenger.utils.media.JpegFingerprint;
import com.exteragram.messenger.utils.media.MediaUtils;
import com.exteragram.messenger.utils.media.OpusFingerprint;
import com.exteragram.messenger.utils.media.Mp4Fingerprint;
import com.google.zxing.Dimension;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PopupSwipeBackLayout;
import org.telegram.ui.ProfileActivity;

import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

@SuppressWarnings("resource")
public class MessageDetailsPopupWrapper {

    public static final int ITEM_HEIGHT = 48;
    public static final int SUBTITLE_ITEM_HEIGHT = 56;
    public static final int GAP_ITEM_HEIGHT = 8;
    private final BaseFragment fragment;
    private final Theme.ResourcesProvider resourcesProvider;
    private final int SET_OWNER = 0;
    private final int FILE_PATH = 1;
    private final int LOCATION = 2;
    private final int BITRATE = 3;
    private final int RESOLUTION = 4;
    private final int PLATFORM = 5;
    public LinearLayout swipeBack;
    private String filePath;
    private long ownerId = 0;
    private String[] geo;
    private JpegFingerprint photoFingerprint;
    private OpusFingerprint voiceFingerprint;
    private Mp4Fingerprint roundVideoFingerprint;

    public MessageDetailsPopupWrapper(BaseFragment fragment, PopupSwipeBackLayout swipeBackLayout, MessageObject messageObject, Theme.ResourcesProvider resourcesProvider) {
        this.fragment = fragment;
        this.resourcesProvider = resourcesProvider;
        var context = fragment.getParentActivity();

        swipeBack = new LinearLayout(context);
        swipeBack.setOrientation(LinearLayout.VERTICAL);
        ScrollView swipeBackScrollView = new ScrollView(context) {
            final AnimatedFloat alphaFloat = new AnimatedFloat(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
            Drawable topShadowDrawable;
            private boolean wasCanScrollVertically;

            @Override
            public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
                super.onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed);
                boolean canScrollVertically = canScrollVertically(-1);
                if (wasCanScrollVertically != canScrollVertically) {
                    invalidate();
                    wasCanScrollVertically = canScrollVertically;
                }
            }

            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                super.dispatchDraw(canvas);

                float alpha = .5f * alphaFloat.set(canScrollVertically(-1) ? 1 : 0);
                if (alpha > 0) {
                    if (topShadowDrawable == null) {
                        topShadowDrawable = ContextCompat.getDrawable(getContext(), R.drawable.header_shadow);
                    }
                    if (topShadowDrawable != null) {
                        topShadowDrawable.setBounds(
                                0, getScrollY(), getWidth(), getScrollY() + topShadowDrawable.getIntrinsicHeight()
                        );
                        topShadowDrawable.setAlpha((int) (0xFF * alpha));
                        topShadowDrawable.draw(canvas);
                    }
                }
            }
        };
        LinearLayout swipeBackScroll = new LinearLayout(context);
        swipeBackScrollView.addView(swipeBackScroll);
        swipeBackScroll.setOrientation(LinearLayout.VERTICAL);

        ActionBarMenuSubItem backCell = new ActionBarMenuSubItem(fragment.getParentActivity(), true, false, resourcesProvider);
        backCell.setItemHeight(44);
        backCell.setTextAndIcon(LocaleController.getString(R.string.Back), R.drawable.msg_arrow_back);
        backCell.getTextView().setPadding(LocaleController.isRTL ? 0 : AndroidUtilities.dp(40), 0, LocaleController.isRTL ? AndroidUtilities.dp(40) : 0, 0);
        backCell.setOnClickListener(v1 -> swipeBackLayout.closeForeground());
        swipeBack.addView(backCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        swipeBackScroll.addView(createGap(), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, GAP_ITEM_HEIGHT));

        int maxHeight = 380;
        int totalHeight = 0;

        ArrayList<Item> items = new ArrayList<>();
        if (messageObject.messageOwner.views > 0) {
            items.add(new Item(R.drawable.msg_view_file, String.format(LocaleController.getPluralString("Views", messageObject.messageOwner.views), AndroidUtilities.formatCount(messageObject.messageOwner.views)), null));
        }
        if (messageObject.messageOwner.forwards > 0) {
            items.add(new Item(R.drawable.msg_forward, String.format(LocaleController.getPluralString("Shares", messageObject.messageOwner.forwards), AndroidUtilities.formatCount(messageObject.messageOwner.forwards)), null));
        }
        if (!items.isEmpty()) {
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
                    if (attribute.stickerset != null) {
                        ownerId = ChatUtils.extractOwnerId(attribute.stickerset.id);
                        if (ownerId > 0) {
                            items.add(new Item(SET_OWNER, R.drawable.msg_sticker, LocaleController.getString(R.string.ChannelCreator), String.valueOf(ownerId)));
                        }
                    }
                }
            }
        }

        filePath = ChatUtils.getInstance().getPathToMessage(messageObject);
        if (!TextUtils.isEmpty(filePath)) {
            items.add(new Item(FILE_PATH, R.drawable.msg_map, LocaleController.getString(R.string.FilePath), LocaleController.getString(R.string.Open)));
        }

        var isAudio = messageObject.isVoice() || messageObject.isMusic();
        var isVideo = messageObject.isVideo() || messageObject.isRoundVideo() || messageObject.isVideoSticker() || messageObject.isGif();
        var isPhotoAsDocument = isPhotoAsDocument(messageObject);
        var isPhoto = isPhotoAsDocument || messageObject.isPhoto() || messageObject.isSticker();
        if ((isPhoto || messageObject.isVoice() || messageObject.isRoundVideo()) && !TextUtils.isEmpty(filePath)) {
            items.add(new Item(PLATFORM, R.drawable.menu_devices, LocaleController.getString(R.string.Platform), LocaleController.getString(R.string.NumberUnknown)));
        }
        if (isVideo || isPhoto) {
            items.add(new Item(RESOLUTION, R.drawable.msg_photo_crop, LocaleController.getString(R.string.Resolution), "0x0"));
        }
        if (isPhotoAsDocument && !TextUtils.isEmpty(filePath)) {
            items.add(new Item(LOCATION, R.drawable.msg_location, LocaleController.getString(R.string.ShareLocation), "0.0, 0.0"));
        }
        if (isVideo || isAudio) {
            items.add(new Item(BITRATE, R.drawable.msg_noise_on, LocaleController.getString(R.string.Bitrate),  "0 Kbps"));

            var duration = (int) messageObject.getDuration();
            if (duration > 0) {
                items.add(new Item(R.drawable.msg2_animations, LocaleController.getString(R.string.Duration), AndroidUtilities.formatShortDuration(duration)));
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
                swipeBackScroll.addView(createGap(), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, GAP_ITEM_HEIGHT));
                totalHeight += GAP_ITEM_HEIGHT;
                continue;
            }

            var item = new ActionBarMenuSubItem(fragment.getParentActivity(), false, false, resourcesProvider);
            item.setTextAndIcon(i.title, i.resId);
            item.setMinimumWidth(AndroidUtilities.dp(196));
            item.setOnClickListener(v1 -> swipeBackLayout.closeForeground());
            swipeBackScroll.addView(item, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, ITEM_HEIGHT));
            totalHeight += ITEM_HEIGHT;

            if (i.subtitle != null) {
                item.setSubtext(i.subtitle);

                item.subtextView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
                item.subtextView.setMarqueeRepeatLimit(-1);
                item.subtextView.setSelected(true);

                item.setItemHeight(SUBTITLE_ITEM_HEIGHT);
                totalHeight += GAP_ITEM_HEIGHT;
            }
            if (i.id == SET_OWNER && ownerId > 0) {
                ChatUtils.getInstance().searchUserById(ownerId, user -> {
                    if (user != null) {
                        if (!TextUtils.isEmpty(UserObject.getPublicUsername(user))) {
                            i.subtitle = "@" + UserObject.getPublicUsername(user);
                        } else {
                            i.subtitle = ContactsController.formatName(user);
                        }
                        item.setSubtext(i.subtitle);
                    }
                });
            } else if (i.id == LOCATION) {
                ChatUtils.utilsQueue.postRunnable(() -> {
                    geo = getLatLongFromPhoto(new File(filePath));
                    AndroidUtilities.runOnUIThread(() -> {
                        if (geo != null) {
                            item.setSubtext(geo[0] + ", " + geo[1]);
                            i.subtitle = geo[0] + ", " + geo[1];
                        } else {
                            item.setVisibility(View.GONE);
                        }
                    });
                });
            } else if (i.id == BITRATE) {
                ChatUtils.utilsQueue.postRunnable(() -> {
                    var bitrate = getBitrate(messageObject, filePath);
                    AndroidUtilities.runOnUIThread(() -> {
                        if (bitrate > 0) {
                            item.setSubtext(bitrate + " Kbps");
                            i.subtitle = bitrate + " Kbps";
                        } else {
                            item.setVisibility(View.GONE);
                        }
                    });
                });
            } else if (i.id == RESOLUTION) {
                ChatUtils.utilsQueue.postRunnable(() -> {
                    Dimension resolution = isVideo ? getVideoResolution(messageObject, filePath) : getPhotoResolution(messageObject, filePath);
                    AndroidUtilities.runOnUIThread(() -> {
                        if (resolution != null) {
                            item.setSubtext(resolution.toString());
                            i.subtitle = resolution.toString();
                        } else {
                            item.setVisibility(View.GONE);
                        }
                    });
                });
            } else if (i.id == PLATFORM) {
                ChatUtils.utilsQueue.postRunnable(() -> {
                    String platform;
                    if (messageObject.isVoice()) {
                        voiceFingerprint = OpusFingerprint.parse(filePath);
                        platform = MediaUtils.getVoicePlatform(voiceFingerprint);
                    } else if (messageObject.isRoundVideo()) {
                        roundVideoFingerprint = Mp4Fingerprint.parse(filePath);
                        platform = MediaUtils.getRoundVideoPlatform(roundVideoFingerprint);
                    } else {
                        photoFingerprint = JpegFingerprint.parse(filePath);
                        platform = MediaUtils.getPhotoPlatform(photoFingerprint);
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        if (!TextUtils.isEmpty(platform)) {
                            item.setSubtext(platform);
                            i.subtitle = platform;
                        } else {
                            item.setVisibility(View.GONE);
                        }
                    });
                });
            }
            item.setTag(i);
            item.setOnClickListener(view -> {
                closeMenu();
                if (i.id == FILE_PATH && !TextUtils.isEmpty(filePath)) {
                    Uri uri;
                    try {
                        uri = FileProvider.getUriForFile(context, ApplicationLoader.getApplicationId() + ".provider", new File(filePath));
                    } catch (IllegalArgumentException e) {
                        // java.lang.IllegalArgumentException: Failed to find configured root that contains /data/data/.../cache/...
                        FileLog.e(e);
                        return;
                    }

                    if (isPhoto || isVideo) {
                        var viewIntent = new Intent(Intent.ACTION_VIEW);
                        viewIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        viewIntent.setDataAndType(uri, messageObject.getMimeType());

                        var packageManager = context.getPackageManager();
                        var activities = packageManager.queryIntentActivities(viewIntent, 0);

                        if (!activities.isEmpty()) {
                            context.startActivity(viewIntent);
                            return;
                        }
                    }

                    var shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                    shareIntent.setDataAndType(uri, messageObject.getMimeType());
                    context.startActivityForResult(Intent.createChooser(shareIntent, LocaleController.getString(R.string.ShareFile)), 500);
                } else if (i.id == SET_OWNER) {
                    if (i.subtitle.startsWith("@")) {
                        Bundle args = new Bundle();
                        args.putLong("user_id", ownerId);
                        ProfileActivity profile = new ProfileActivity(args);
                        fragment.presentFragment(profile);
                    } else {
                        copy(String.valueOf(ownerId));
                    }
                } else if (i.id == LOCATION) {
                    Browser.openUrl(fragment.getParentActivity(), String.format("https://maps.google.com/?q=%s,%s", geo[0], geo[1]));
                } else {
                    copy(i.subtitle != null ? i.subtitle : i.title);
                }
            });
            item.setOnLongClickListener(view -> {
                String text;
                if (i.id == FILE_PATH && !TextUtils.isEmpty(filePath)) {
                    text = filePath;
                } else if (i.id == SET_OWNER) {
                    text = String.valueOf(ownerId);
                } else if (i.id == PLATFORM && photoFingerprint != null) {
                    text = photoFingerprint.describe();
                } else if (i.id == PLATFORM && voiceFingerprint != null) {
                    text = voiceFingerprint.vendor;
                } else if (i.id == PLATFORM && roundVideoFingerprint != null) {
                    text = roundVideoFingerprint.describe();
                } else {
                    text = i.subtitle != null ? i.subtitle : i.title;
                }
                copy(text);
                return true;
            });
        }

        if (totalHeight > maxHeight && Math.abs(totalHeight - maxHeight) > SUBTITLE_ITEM_HEIGHT * 2) {
            swipeBack.addView(swipeBackScrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, maxHeight));
        } else {
            swipeBack.addView(swipeBackScrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
    }

    public static int getBitrate(MessageObject message, String filePath) {
        int bitrate = -1;
        if (!TextUtils.isEmpty(filePath)) {
            try {
                bitrate = getBitrateFromPath(filePath);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        if (bitrate == -1) {
            try {
                bitrate = getBitrateFromAttributes(message);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        return bitrate;
    }

    public static int getBitrateFromPath(String filePath) {
        int bitrate = -1;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(filePath);
            bitrate = Integer.parseInt(Objects.requireNonNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE))) / 1000;
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

    public static int getBitrateFromAttributes(MessageObject message) {
        long size = MessageObject.getMessageSize(message.messageOwner);
        if (size <= 0) {
            return -1;
        }

        int bitrate = -1;
        if (MessageObject.getMedia(message.messageOwner) != null && MessageObject.getMedia(message.messageOwner).document != null) {
            for (var attribute : MessageObject.getMedia(message.messageOwner).document.attributes) {
                if ((attribute instanceof TLRPC.TL_documentAttributeAudio || attribute instanceof TLRPC.TL_documentAttributeVideo) && attribute.duration > 0) {
                    bitrate = (int) (size / attribute.duration * 8 / 1000);
                    break;
                }
            }
        }
        return bitrate;
    }

    public static Dimension getPhotoResolution(MessageObject message, String filePath) {
        Dimension res = null;

        if (!TextUtils.isEmpty(filePath)) {
            try {
                res = getPhotoResolutionFromPath(filePath);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        if (res == null) {
            try {
                res = getPhotoResolutionFromAttributes(message);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        return res;
    }

    public static Dimension getPhotoResolutionFromPath(String filePath) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);
        return new Dimension(options.outWidth, options.outHeight);
    }

    public static Dimension getPhotoResolutionFromAttributes(MessageObject message) {
        Dimension res = null;
        if (MessageObject.getMedia(message.messageOwner) != null && MessageObject.getMedia(message.messageOwner).photo != null) {
            var size = FileLoader.getClosestPhotoSizeWithSize(MessageObject.getMedia(message.messageOwner).photo.sizes, AndroidUtilities.getPhotoSize(), false, null, true);
            if (size != null && size.w > 0 && size.h > 0) {
                res = new Dimension(size.w, size.h);
            }

            if (res == null) {
                var videoSize = FileLoader.getClosestVideoSizeWithSize(MessageObject.getMedia(message.messageOwner).photo.video_sizes, AndroidUtilities.getPhotoSize(), false, true);
                if (videoSize != null && videoSize.w > 0 && videoSize.h > 0) {
                    res = new Dimension(videoSize.w, videoSize.h);
                }
            }
        } else if (MessageObject.getMedia(message.messageOwner) != null && MessageObject.getMedia(message.messageOwner).document != null) {
            for (var attribute : MessageObject.getMedia(message.messageOwner).document.attributes) {
                if (attribute instanceof TLRPC.TL_documentAttributeImageSize && attribute.w > 0 && attribute.h > 0) {
                    res = new Dimension(attribute.w, attribute.h);
                    break;
                }
            }
        }
        return res;
    }

    public static Dimension getVideoResolution(MessageObject message, String filePath) {
        Dimension res = null;

        if (!TextUtils.isEmpty(filePath)) {
            try {
                res = getVideoResolutionFromPath(filePath);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        if (res == null) {
            try {
                res = getVideoResolutionFromAttributes(message);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        return res;
    }

    public static Dimension getVideoResolutionFromPath(String filePath) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        int width = 0, height = 0;
        try {
            retriever.setDataSource(filePath);
            width = Integer.parseInt(Objects.requireNonNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)));
            height = Integer.parseInt(Objects.requireNonNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)));
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

    public static Dimension getVideoResolutionFromAttributes(MessageObject message) {
        Dimension res = null;
        if (MessageObject.getMedia(message.messageOwner) != null && MessageObject.getMedia(message.messageOwner).document != null) {
            for (var attribute : MessageObject.getMedia(message.messageOwner).document.attributes) {
                if (attribute instanceof TLRPC.TL_documentAttributeVideo && attribute.w > 0 && attribute.h > 0) {
                    res = new Dimension(attribute.w, attribute.h);
                    break;
                }
            }
        }
        return res;
    }

    public static String[] getLatLongFromPhoto(File photoFile) {
        try {
            ExifInterface exif = new ExifInterface(photoFile.getAbsolutePath());

            String gpsLatitude = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE);
            String gpsLongitude = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE);
            String gpsLatitudeRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF);
            String gpsLongitudeRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF);

            if (gpsLatitude != null && gpsLongitude != null &&
                    gpsLatitudeRef != null && gpsLongitudeRef != null) {

                double latitude = convertToDegrees(gpsLatitude);
                if ("S".equalsIgnoreCase(gpsLatitudeRef)) {
                    latitude = -latitude;
                }

                double longitude = convertToDegrees(gpsLongitude);
                if ("W".equalsIgnoreCase(gpsLongitudeRef)) {
                    longitude = -longitude;
                }

                DecimalFormat formatter = new DecimalFormat("#.######");
                formatter.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.ENGLISH));
                return new String[]{formatter.format(latitude), formatter.format(longitude)};
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    private static double convertToDegrees(String gpsValue) {
        String[] parts = gpsValue.split(",");

        double degrees = convertToDouble(parts[0]);
        double minutes = convertToDouble(parts[1]);
        double seconds = convertToDouble(parts[2]);

        return degrees + (minutes / 60.0) + (seconds / 3600.0);
    }

    private static double convertToDouble(String rational) {
        String[] fraction = rational.split("/");
        if (fraction.length == 1) {
            return Double.parseDouble(fraction[0]);
        } else if (fraction.length == 2) {
            double numerator = Double.parseDouble(fraction[0]);
            double denominator = Double.parseDouble(fraction[1]);
            if (denominator == 0) {
                FileLog.e("Division by zero in GPS data");
                return 0;
            }
            return numerator / denominator;
        } else {
            FileLog.e("Invalid rational number format: " + rational);
            return 0;
        }
    }

    private boolean isPhotoAsDocument(MessageObject message) {
        try {
            if (MessageObject.getMedia(message.messageOwner) != null && MessageObject.getMedia(message.messageOwner).document != null) {
                for (var attribute : MessageObject.getMedia(message.messageOwner).document.attributes) {
                    if (attribute instanceof TLRPC.TL_documentAttributeImageSize && attribute.w > 0 && attribute.h > 0) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    private String formatTime(int timestamp, boolean full) {
        if (timestamp == 0x7ffffffe) {
            return LocaleController.getString(R.string.SendWhenOnline);
        } else {
            return full ? LocaleController.formatString(R.string.formatDateAtTime,
                    LocaleController.getInstance().getFormatterYear().format(new Date(timestamp * 1000L)),
                    LocaleController.getInstance().getFormatterDayWithSeconds().format(new Date(timestamp * 1000L))
            ) : LocaleController.formatDateAudio(timestamp, true);
        }
    }

    private View createGap() {
        ActionBarPopupWindow.GapView gap = new ActionBarPopupWindow.GapView(fragment.getContext(), resourcesProvider);
        gap.setDividerVisible(false);
        return gap;
    }

    protected void copy(String text) {
    }

    protected void closeMenu() {
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
}
