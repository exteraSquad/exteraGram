/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export;

import com.exteragram.messenger.config.PrefEnum;

import org.telegram.tgnet.TLRPC;

public class ExportSettings {

    public static final int PersonalInfo = 0x001;
    public static final int Userpics = 0x002;
    public static final int Contacts = 0x004;
    public static final int Sessions = 0x008;
    public static final int OtherData = 0x010;
    public static final int PersonalChats = 0x020;
    public static final int BotChats = 0x040;
    public static final int PrivateGroups = 0x080;
    public static final int PublicGroups = 0x100;
    public static final int PrivateChannels = 0x200;
    public static final int PublicChannels = 0x400;
    public static final int Stories = 0x800;
    public static final int ProfileMusic = 0x1000;

    public static final int GroupsMask = PrivateGroups | PublicGroups;
    public static final int ChannelsMask = PrivateChannels | PublicChannels;
    public static final int GroupsChannelsMask = GroupsMask | ChannelsMask;
    public static final int NonChannelChatsMask = PersonalChats | BotChats | PrivateGroups;
    public static final int AnyChatsMask = PersonalChats | BotChats | GroupsChannelsMask;

    public static final int DefaultTypes = PersonalInfo | Userpics | Contacts | Stories | ProfileMusic | PersonalChats | PrivateGroups;
    public static final int DefaultFullChats = PersonalChats | BotChats;

    public static final long MaxFileSize = 4000L * 1024 * 1024;

    public enum Format implements PrefEnum {
        Html(0), Json(1), HtmlAndJson(2);

        private final int prefId;

        Format(int prefId) {
            this.prefId = prefId;
        }

        @Override
        public int getPrefId() {
            return prefId;
        }
    }

    public String path = "";
    public String outputFolder = "";
    public Format format = Format.Html;
    public int types = DefaultTypes;
    public int fullChats = DefaultFullChats;
    public final MediaSettings media = new MediaSettings();

    public TLRPC.InputPeer singlePeer = new TLRPC.TL_inputPeerEmpty();
    public int singlePeerFrom;
    public int singlePeerTill;
    public int singleTopicRootId;
    public String singleTopicTitle = "";

    public boolean onlySinglePeer() {
        return !(singlePeer instanceof TLRPC.TL_inputPeerEmpty);
    }

    public boolean onlySingleTopic() {
        return onlySinglePeer() && singleTopicRootId != 0;
    }

    public boolean has(int type) {
        return (types & type) != 0;
    }

    public ExportSettings copy() {
        ExportSettings result = new ExportSettings();
        result.path = path;
        result.outputFolder = outputFolder;
        result.format = format;
        result.types = types;
        result.fullChats = fullChats;
        result.media.types = media.types;
        result.media.sizeLimit = media.sizeLimit;
        result.singlePeer = singlePeer;
        result.singlePeerFrom = singlePeerFrom;
        result.singlePeerTill = singlePeerTill;
        result.singleTopicRootId = singleTopicRootId;
        result.singleTopicTitle = singleTopicTitle;
        return result;
    }

    public static ExportSettings forSinglePeer(TLRPC.InputPeer peer, int topicRootId, String topicTitle) {
        ExportSettings result = new ExportSettings();
        result.singlePeer = peer;
        result.types = AnyChatsMask;
        result.fullChats = AnyChatsMask;
        result.singleTopicRootId = topicRootId;
        result.singleTopicTitle = topicTitle != null ? topicTitle : "";
        return result;
    }

    public static class MediaSettings {
        public static final int Photo = 0x01;
        public static final int Video = 0x02;
        public static final int VoiceMessage = 0x04;
        public static final int VideoMessage = 0x08;
        public static final int Sticker = 0x10;
        public static final int GIF = 0x20;
        public static final int File = 0x40;

        public int types = Photo;
        public long sizeLimit = 8L * 1024 * 1024;
    }
}
