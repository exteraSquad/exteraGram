/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.profilecolor.debug;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Bundle;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ProfileActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

public final class ProfileColorDebugData {

    public static final int KIND_USER = 0;
    public static final int KIND_BOT = 1;
    public static final int KIND_CHANNEL = 2;
    public static final int KIND_GROUP = 3;
    public static final int KINDS_COUNT = 4;

    public static final String[] KIND_NAMES = {"User", "Bot", "Channel", "Group"};

    public static final class Preset {
        public final int id;
        public final String name;
        public final int[] light;
        public final int[] dark;

        Preset(int id, String name, int[] light, int[] dark) {
            this.id = id;
            this.name = name;
            this.light = light;
            this.dark = dark;
        }
    }

    private static final int CUSTOM_ID = 1000;
    private static final long USER_ID = 7_700_000_000L;
    private static final long CHAT_ID = 7_800_000_000L;
    private static final long SELF_ID = USER_ID + 99;

    public static final Preset[] PRESETS = {
            single(0, "Red", 0xBA5650, 0x9C4540),
            single(1, "Orange", 0xC27C3E, 0x945E2C),
            single(2, "Violet", 0x956AC8, 0x715099),
            single(3, "Green", 0x49A355, 0x33713B),
            single(4, "Cyan", 0x3E97AD, 0x387E87),
            single(5, "Blue", 0x5A8FBB, 0x477194),
            single(6, "Pink", 0xB85378, 0x944763),
            single(7, "Gray", 0x7F8B95, 0x4D5A66),
            pair(8, "Red + orange", 0xC9565D, 0xD97C57, 0x994343, 0xAC583E),
            pair(9, "Orange + yellow", 0xCF7244, 0xCC9433, 0x8F552F, 0xA17232),
            pair(10, "Violet + pink", 0x9662D4, 0xB966B6, 0x634691, 0x9250A2),
            pair(11, "Green + lime", 0x3D9755, 0x89A650, 0x296A43, 0x5F8F44),
            pair(12, "Cyan + teal", 0x3D95BA, 0x50AD98, 0x306C7C, 0x3E987E),
            pair(13, "Blue + cyan", 0x538BC2, 0x4DA8BD, 0x38618C, 0x458BA1),
            pair(14, "Pink + coral", 0xB04F74, 0xD1666D, 0x884160, 0xA65259),
            pair(15, "Slate", 0x637482, 0x7B8A97, 0x53606E, 0x384654),
            pair(CUSTOM_ID, "Black", 0x161618, 0x2C2C2E, 0x0A0A0B, 0x1C1C1E),
            pair(CUSTOM_ID + 1, "White", 0xF2F2F4, 0xFFFFFF, 0xF2F2F4, 0xFFFFFF),
            pair(CUSTOM_ID + 2, "Ivory", 0xBAB5A1, 0xE1DCC8, 0xBAB5A1, 0xE1DCC8),
            pair(CUSTOM_ID + 3, "Yellow", 0xD4A017, 0xF4D03F, 0xD4A017, 0xF4D03F),
            pair(CUSTOM_ID + 4, "Lime", 0x7ED321, 0xB8E986, 0x7ED321, 0xB8E986),
            pair(CUSTOM_ID + 5, "Navy", 0x0B1F4B, 0x1B3A7A, 0x0B1F4B, 0x1B3A7A),
            pair(CUSTOM_ID + 6, "Pastel pink", 0xF7B6C8, 0xFAD1DC, 0xF7B6C8, 0xFAD1DC),
            pair(CUSTOM_ID + 7, "Maroon", 0x4A0E17, 0x7A1B2A, 0x4A0E17, 0x7A1B2A),
            pair(CUSTOM_ID + 8, "Mid gray", 0x808080, 0x9A9A9A, 0x808080, 0x9A9A9A),
            pair(CUSTOM_ID + 9, "Pure red", 0xE00000, 0xFF4D4D, 0xE00000, 0xFF4D4D),
            pair(CUSTOM_ID + 10, "Pure blue", 0x0033FF, 0x3366FF, 0x0033FF, 0x3366FF),
            pair(CUSTOM_ID + 11, "Mint", 0x9FE2BF, 0xC9F2DC, 0x9FE2BF, 0xC9F2DC),
            pair(CUSTOM_ID + 12, "Brown", 0x6B4423, 0x8B5A2B, 0x6B4423, 0x8B5A2B),
            pair(CUSTOM_ID + 13, "Magenta", 0xC2185B, 0xFF4081, 0xC2185B, 0xFF4081),
            pair(CUSTOM_ID + 14, "Threshold", 0x9AA63C, 0xB7C258, 0x9AA63C, 0xB7C258),
    };

    private static Preset single(int id, String name, int light, int dark) {
        return new Preset(id, name, new int[]{light, light, lighten(light), lighten(light)}, new int[]{dark, dark, lighten(dark), lighten(dark)});
    }

    private static Preset pair(int id, String name, int light1, int light2, int dark1, int dark2) {
        return new Preset(id, name, new int[]{light1, light2, light1, light2, lighten(light2), lighten(light2)}, new int[]{dark1, dark2, dark1, dark2, lighten(dark2), lighten(dark2)});
    }

    private static int lighten(int color) {
        return androidx.core.graphics.ColorUtils.blendARGB(0xFF000000 | color, 0xFFFFFFFF, 0.55f) & 0xFFFFFF;
    }

    private ProfileColorDebugData() {
    }

    public static void installPalette(int account) {
        final StringBuilder sb = new StringBuilder();
        for (Preset preset : PRESETS) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append('#').append(preset.id).append('{');
            append(sb, preset.light);
            sb.append('@');
            append(sb, preset.dark);
            sb.append('}');
        }
        MessagesController.getInstance(account).profilePeerColors = MessagesController.PeerColors.fromString(MessagesController.PeerColors.TYPE_PROFILE, sb.toString());
    }

    private static void append(StringBuilder sb, int[] colors) {
        for (int i = 0; i < colors.length; ++i) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(0xFF000000 | colors[i]);
        }
    }

    private static TLRPC.TL_peerColor color(Preset preset) {
        if (preset == null) {
            return null;
        }
        final TLRPC.TL_peerColor color = new TLRPC.TL_peerColor();
        color.flags |= 1;
        color.color = preset.id;
        return color;
    }

    private static TLRPC.TL_user user(long id, String firstName, String lastName, String username, Preset preset) {
        final TLRPC.TL_user user = new TLRPC.TL_user();
        user.id = id;
        user.access_hash = 1;
        user.first_name = firstName;
        user.last_name = lastName;
        user.username = username;
        user.flags |= 1 | 2 | 4 | 8;
        user.status = new TLRPC.TL_userStatusOnline();
        user.status.expires = Integer.MAX_VALUE;
        user.profile_color = color(preset);
        if (user.profile_color != null) {
            user.flags2 |= 512;
        }
        return user;
    }

    public static void open(BaseFragment fragment, int kind, Preset preset, boolean media, boolean photo) {
        final int account = fragment.getCurrentAccount();
        final MessagesController controller = MessagesController.getInstance(account);
        installPalette(account);
        final UserConfig userConfig = UserConfig.getInstance(account);
        if (!userConfig.isClientActivated()) {
            userConfig.setCurrentUser(user(SELF_ID, "Debug", "Self", null, null));
        }
        final Bundle args = new Bundle();
        final long dialogId;
        if (kind == KIND_USER || kind == KIND_BOT) {
            final boolean bot = kind == KIND_BOT;
            final long id = USER_ID + (bot ? 1 : 0);
            final TLRPC.TL_user user = user(id, bot ? "Tinted" : "Alicia", bot ? "Bot" : "Torreaux", bot ? "tintedbot" : "souic", preset);
            user.bot = bot;
            user.premium = !bot;
            user.verified = bot;
            user.phone = bot ? null : "12025550147";
            if (photo) {
                user.photo = new TLRPC.TL_userProfilePhoto();
                user.photo.photo_id = id;
                user.photo.dc_id = 2;
                user.photo.photo_small = photoLocation(account, id, 1, 160);
                user.photo.photo_big = photoLocation(account, id, 2, 800);
                user.flags |= 32;
            }
            controller.putUser(user, false);

            final TLRPC.TL_userFull full = new TLRPC.TL_userFull();
            full.id = id;
            full.user = user;
            full.about = bot ? "Paints every profile it can reach. Try @souic or https://exteragram.app" : "Keep calm and send telegrams. Find me at @helloapple, write to hello@example.com or visit https://telegram.org";
            full.flags |= 2;
            full.phone_calls_available = !bot;
            full.video_calls_available = !bot;
            full.common_chats_count = bot ? 0 : 3;
            full.notify_settings = new TLRPC.TL_peerNotifySettings();
            full.settings = new TLRPC.TL_peerSettings();
            if (!bot) {
                full.birthday = new TL_account.TL_birthday();
                full.birthday.day = 24;
                full.birthday.month = 11;
                full.birthday.year = 1998;
                full.birthday.flags |= 1;
                full.flags2 |= 32;

                full.business_work_hours = new TL_account.TL_businessWorkHours();
                full.business_work_hours.timezone_id = "Europe/London";
                for (int day = 0; day < 7; ++day) {
                    final TL_account.TL_businessWeeklyOpen open = new TL_account.TL_businessWeeklyOpen();
                    open.start_minute = day * 24 * 60 + (preset != null && preset.id % 2 == 0 ? 0 : 9 * 60);
                    open.end_minute = day * 24 * 60 + (preset != null && preset.id % 2 == 0 ? 24 * 60 - 1 : 9 * 60 + 1);
                    full.business_work_hours.weekly_open.add(open);
                }
                full.flags2 |= 1;

                full.business_location = new TLRPC.TL_businessLocation();
                full.business_location.address = "221B Baker Street, London";
                full.flags2 |= 2;
            } else {
                full.bot_info = new org.telegram.tgnet.tl.TL_bots.TL_botInfo();
                full.bot_info.user_id = id;
                full.bot_info.description = "A bot for testing profile colors.";
                full.flags |= 8;
            }
            controller.processUserInfo(user, full, false, true, 0, new ArrayList<>(), new java.util.HashMap<>(), 0, true);
            args.putLong("user_id", id);
            dialogId = id;
        } else {
            final boolean group = kind == KIND_GROUP;
            final long id = CHAT_ID + (group ? 1 : 0);
            final TLRPC.TL_channel chat = new TLRPC.TL_channel();
            chat.id = id;
            chat.title = group ? "Tinted Lounge" : "Tinted Daily";
            chat.username = group ? "tintedlounge" : "tinteddaily";
            chat.flags |= 64;
            chat.megagroup = group;
            chat.broadcast = !group;
            chat.verified = !group;
            chat.participants_count = group ? 6 : 104200;
            chat.default_banned_rights = new TLRPC.TL_chatBannedRights();
            chat.profile_color = color(preset);
            if (chat.profile_color != null) {
                chat.flags2 |= 256;
            }
            controller.putChat(chat, false);

            final TLRPC.TL_channelFull full = new TLRPC.TL_channelFull();
            full.id = id;
            full.about = group ? "A place to argue about colors. Rules: be kind, read https://telegram.org/faq before asking." : "Daily notes about interface colors. Contact @souic, more at https://exteragram.app";
            full.participants_count = chat.participants_count;
            full.admins_count = 2;
            full.online_count = 3;
            full.can_view_participants = group;
            full.notify_settings = new TLRPC.TL_peerNotifySettings();
            full.chat_photo = new TLRPC.TL_photoEmpty();
            if (group) {
                final String[] names = {"Alicia Torreaux", "Roberto Diaz", "Mike Renoir", "Digital Nomads", "Jessica Brown", "Veronica Hall"};
                full.participants = new TLRPC.TL_chatParticipants();
                full.participants.chat_id = id;
                for (int i = 0; i < names.length; ++i) {
                    final String[] parts = names[i].split(" ");
                    final TLRPC.TL_user member = user(USER_ID + 10 + i, parts[0], parts[1], null, null);
                    member.status.expires = i < 3 ? Integer.MAX_VALUE : (int) (System.currentTimeMillis() / 1000) - 3600 * (i + 1);
                    if (i >= 3) {
                        member.status = new TLRPC.TL_userStatusOffline();
                        member.status.expires = (int) (System.currentTimeMillis() / 1000) - 3600 * (i + 1);
                    }
                    controller.putUser(member, false);
                    final TLRPC.TL_chatChannelParticipant participant = new TLRPC.TL_chatChannelParticipant();
                    participant.user_id = member.id;
                    participant.date = (int) (System.currentTimeMillis() / 1000);
                    if (i == 0) {
                        participant.channelParticipant = new TLRPC.TL_channelParticipantCreator();
                        participant.channelParticipant.rank = "Owner";
                    } else if (i == 1) {
                        participant.channelParticipant = new TLRPC.TL_channelParticipantAdmin();
                        participant.channelParticipant.rank = "Designer";
                    } else {
                        participant.channelParticipant = new TLRPC.TL_channelParticipant();
                    }
                    participant.channelParticipant.peer = new TLRPC.TL_peerUser();
                    participant.channelParticipant.peer.user_id = member.id;
                    participant.channelParticipant.date = participant.date;
                    full.participants.participants.add(participant);
                }
            }
            controller.putChatFull(full);
            args.putLong("chat_id", id);
            dialogId = -id;
        }
        AndroidUtilities.runOnUIThread(() -> {
            final ProfileActivity profile = new ProfileActivity(args);
            fragment.presentFragment(profile);
            if (media) {
                AndroidUtilities.runOnUIThread(() -> ProfileColorDebugMedia.fill(profile, dialogId), 600);
            }
        });
    }

    private static TLRPC.FileLocation photoLocation(int account, long id, int localId, int size) {
        final TLRPC.TL_fileLocationToBeDeprecated location = new TLRPC.TL_fileLocationToBeDeprecated();
        location.volume_id = -id;
        location.local_id = localId;
        final File file = FileLoader.getInstance(account).getPathToAttach(location, true);
        if (!file.exists()) {
            final Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            final Canvas canvas = new Canvas(bitmap);
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setShader(new LinearGradient(0, 0, size * 0.3f, size, new int[]{0xFF1D2B53, 0xFF3A7BD5, 0xFFF2A65A, 0xFFF7E1A0}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, size, size, paint);
            paint.setShader(null);
            paint.setColor(0xFFFFF3C4);
            canvas.drawCircle(size * 0.68f, size * 0.62f, size * 0.09f, paint);
            paint.setColor(0xFF14213D);
            for (int i = 0; i < 6; ++i) {
                canvas.drawRect(size * i / 6f, size * (0.78f + 0.06f * ((i * 7) % 3)), size * (i + 1) / 6f, size, paint);
            }
            try (FileOutputStream stream = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream);
            } catch (Exception e) {
                FileLog.e(e);
            }
            bitmap.recycle();
        }
        return location;
    }

    public static void restore(int account) {
        final UserConfig userConfig = UserConfig.getInstance(account);
        if (userConfig.getClientUserId() == SELF_ID) {
            userConfig.clearConfig();
        }
    }
}
