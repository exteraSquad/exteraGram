/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.profilecolor.debug;

import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;

final class ProfileColorDebugMedia {

    private static final int PER_TYPE = 14;

    private ProfileColorDebugMedia() {
    }

    static void fill(ProfileActivity profile, long dialogId) {
        if (profile.getFragmentView() == null) {
            return;
        }
        final int account = profile.getCurrentAccount();
        final NotificationCenter center = NotificationCenter.getInstance(account);
        final int[] counts = new int[MediaDataController.MEDIA_TYPES_COUNT];
        final int[] types = {MediaDataController.MEDIA_PHOTOVIDEO, MediaDataController.MEDIA_FILE, MediaDataController.MEDIA_AUDIO, MediaDataController.MEDIA_URL, MediaDataController.MEDIA_MUSIC};
        for (int type : types) {
            counts[type] = PER_TYPE;
        }
        center.postNotificationName(NotificationCenter.mediaCountsDidLoad, dialogId, 0L, counts);
        for (int type : types) {
            final ArrayList<MessageObject> messages = new ArrayList<>();
            for (int i = 0; i < PER_TYPE; ++i) {
                messages.add(message(account, dialogId, type, i));
            }
            center.postNotificationName(NotificationCenter.mediaDidLoad, dialogId, PER_TYPE, messages, profile.getClassGuid(), type, true, false, 0);
        }
    }

    private static MessageObject message(int account, long dialogId, int type, int index) {
        final TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = 1000 * (type + 1) - index;
        message.date = (int) (System.currentTimeMillis() / 1000) - index * 86400 * 4 - 3600;
        message.dialog_id = dialogId;
        if (dialogId > 0) {
            message.peer_id = new TLRPC.TL_peerUser();
            message.peer_id.user_id = dialogId;
        } else {
            message.peer_id = new TLRPC.TL_peerChannel();
            message.peer_id.channel_id = -dialogId;
        }
        message.from_id = message.peer_id;
        message.flags |= 256 | 512;
        message.message = "";
        switch (type) {
            case MediaDataController.MEDIA_PHOTOVIDEO:
                message.media = photo(message.id);
                break;
            case MediaDataController.MEDIA_FILE:
                message.media = document(message.id, FILES[index % FILES.length], "application/octet-stream", 180_000 + index * 913_000L, null);
                break;
            case MediaDataController.MEDIA_AUDIO: {
                final TLRPC.TL_documentAttributeAudio audio = new TLRPC.TL_documentAttributeAudio();
                audio.voice = true;
                audio.duration = 7 + index * 5;
                audio.waveform = new byte[63];
                for (int i = 0; i < audio.waveform.length; ++i) {
                    audio.waveform[i] = (byte) ((i * 37 + index * 11) % 200);
                }
                audio.flags |= 4;
                message.media = document(message.id, "voice.ogg", "audio/ogg", 24_000 + index * 9_000L, audio);
                message.media.voice = true;
                break;
            }
            case MediaDataController.MEDIA_URL: {
                final String url = LINKS[index % LINKS.length];
                message.message = "Worth reading: " + url;
                final TLRPC.TL_messageEntityUrl entity = new TLRPC.TL_messageEntityUrl();
                entity.offset = "Worth reading: ".length();
                entity.length = url.length();
                message.entities.add(entity);
                final TLRPC.TL_webPage page = new TLRPC.TL_webPage();
                page.id = message.id;
                page.url = url;
                page.display_url = url.replace("https://", "");
                page.site_name = page.display_url.substring(0, page.display_url.indexOf('/') < 0 ? page.display_url.length() : page.display_url.indexOf('/'));
                page.title = "Notes on color, part " + (index + 1);
                page.description = "A long read about tonal palettes, contrast and why every surface deserves its own shade.";
                page.flags |= 2 | 4 | 8;
                message.media = new TLRPC.TL_messageMediaWebPage();
                message.media.webpage = page;
                break;
            }
            case MediaDataController.MEDIA_MUSIC: {
                final TLRPC.TL_documentAttributeAudio audio = new TLRPC.TL_documentAttributeAudio();
                audio.duration = 150 + index * 17;
                audio.title = TRACKS[index % TRACKS.length];
                audio.performer = "The Palettes";
                audio.flags |= 1 | 2;
                message.media = document(message.id, audio.title + ".mp3", "audio/mpeg", 4_100_000 + index * 310_000L, audio);
                break;
            }
        }
        return new MessageObject(account, message, false, true);
    }

    private static final String[] FILES = {"Design brief.pdf", "palette.zip", "Quarterly report.xlsx", "notes.txt", "build-release.apk", "Contract (signed).docx"};
    private static final String[] LINKS = {"https://telegram.org/blog", "https://exteragram.app/changelog", "https://m3.material.io/styles/color", "https://oklch.com"};
    private static final String[] TRACKS = {"Chroma", "Hue Shift", "Tonal Spot", "Gamut", "Lightness"};

    private static TLRPC.MessageMedia photo(int id) {
        final TLRPC.TL_photo photo = new TLRPC.TL_photo();
        photo.id = id;
        photo.date = (int) (System.currentTimeMillis() / 1000);
        photo.file_reference = new byte[0];
        photo.dc_id = 2;
        final TLRPC.TL_photoSize size = new TLRPC.TL_photoSize();
        size.type = "x";
        size.w = 800;
        size.h = 800;
        size.size = 90_000;
        size.location = new TLRPC.TL_fileLocationToBeDeprecated();
        size.location.volume_id = -id;
        size.location.local_id = id;
        photo.sizes.add(size);
        final TLRPC.TL_messageMediaPhoto media = new TLRPC.TL_messageMediaPhoto();
        media.photo = photo;
        media.flags |= 1;
        return media;
    }

    private static TLRPC.MessageMedia document(int id, String name, String mime, long size, TLRPC.DocumentAttribute attribute) {
        final TLRPC.TL_document document = new TLRPC.TL_document();
        document.id = id;
        document.date = (int) (System.currentTimeMillis() / 1000);
        document.file_reference = new byte[0];
        document.dc_id = 2;
        document.mime_type = mime;
        document.size = size;
        final TLRPC.TL_documentAttributeFilename filename = new TLRPC.TL_documentAttributeFilename();
        filename.file_name = name;
        document.attributes.add(filename);
        if (attribute != null) {
            document.attributes.add(attribute);
        }
        final TLRPC.TL_messageMediaDocument media = new TLRPC.TL_messageMediaDocument();
        media.document = document;
        media.flags |= 1;
        return media;
    }
}
