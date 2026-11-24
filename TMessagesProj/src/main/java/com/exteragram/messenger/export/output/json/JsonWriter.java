/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export.output.json;

import com.exteragram.messenger.export.ExportFormat;
import com.exteragram.messenger.export.ExportSettings;
import com.exteragram.messenger.export.api.ExportData;
import com.exteragram.messenger.export.api.ExportParser;
import com.exteragram.messenger.export.output.AbstractWriter;
import com.exteragram.messenger.export.output.ExportEnvironment;
import com.exteragram.messenger.export.output.ExportFiles;
import com.exteragram.messenger.export.output.OutputFile;
import com.exteragram.messenger.export.output.Result;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class JsonWriter extends AbstractWriter {

    private static final String MAIN_FILE = "result.json";

    private enum DialogsMode {
        None, Chats, Left
    }

    private final JsonContext context = new JsonContext();
    private ExportSettings settings;
    private OutputFile output;
    private boolean currentNestingHadItem;
    private DialogsMode dialogsMode = DialogsMode.None;

    private String pushNesting(boolean type) {
        context.push(type);
        currentNestingHadItem = false;
        return type == JsonContext.OBJECT ? "{" : "[";
    }

    private String prepareObjectItemStart(String key) {
        String result = (currentNestingHadItem ? ",\n" : "\n") + context.indentation() + JsonContext.serializeString(key) + ": ";
        currentNestingHadItem = true;
        return result;
    }

    private String prepareArrayItemStart() {
        String result = (currentNestingHadItem ? ",\n" : "\n") + context.indentation();
        currentNestingHadItem = true;
        return result;
    }

    private String popNesting() {
        boolean type = context.nesting.get(context.nesting.size() - 1);
        context.pop();
        currentNestingHadItem = true;
        return "\n" + context.indentation() + (type == JsonContext.OBJECT ? '}' : ']');
    }

    @Override
    public String mainFilePath() {
        return settings.path + MAIN_FILE;
    }

    @Override
    public Result start(ExportSettings settings, OutputFile.Stats stats) {
        this.settings = settings;
        output = new OutputFile(settings.path + MAIN_FILE, stats);
        if (settings.onlySinglePeer()) {
            return Result.SUCCESS;
        }
        return output.writeBlock(pushNesting(JsonContext.OBJECT)
                + prepareObjectItemStart("about")
                + JsonContext.serializeString(ExportEnvironment.ABOUT_TELEGRAM));
    }

    @Override
    public Result writePersonal(ExportData.PersonalInfo data) {
        ExportData.ContactInfo info = data.user.info;
        return output.writeBlock(prepareObjectItemStart("personal_information") + context.serializeObject(
                new JsonContext.Item("user_id", String.valueOf(data.user.bareId)),
                new JsonContext.Item("first_name", JsonContext.serializeString(info.firstName)),
                new JsonContext.Item("last_name", JsonContext.serializeString(info.lastName)),
                new JsonContext.Item("phone_number", JsonContext.serializeString(ExportFormat.phoneNumber(info.phoneNumber))),
                new JsonContext.Item("username", data.user.username.isEmpty() ? "" : JsonContext.serializeString("@" + data.user.username)),
                new JsonContext.Item("bio", JsonContext.stringAllowEmpty(data.bio))));
    }

    private static String skipReasonText(ExportData.File file, String noun) {
        return switch (file.skipReason) {
            case Unavailable -> "(" + noun + " unavailable, please try again later)";
            case FileSize -> "(" + noun + " exceeds maximum size. Change data exporting settings to download.)";
            case FileType -> "(" + noun + " not included. Change data exporting settings to download.)";
            case None, DateLimits -> file.relativePath;
        };
    }

    @Override
    public Result writeUserpicsStart(int count) {
        return output.writeBlock(prepareObjectItemStart("profile_pictures") + pushNesting(JsonContext.ARRAY));
    }

    @Override
    public Result writeUserpicsSlice(ArrayList<ExportData.Photo> data) {
        StringBuilder block = new StringBuilder();
        for (ExportData.Photo userpic : data) {
            block.append(prepareArrayItemStart()).append(context.serializeObject(
                    new JsonContext.Item("date", userpic.date != 0 ? JsonContext.serializeDate(userpic.date) : ""),
                    new JsonContext.Item("date_unixtime", userpic.date != 0 ? JsonContext.serializeDateRaw(userpic.date) : ""),
                    new JsonContext.Item("photo", JsonContext.serializeString(skipReasonText(userpic.image.file, "Photo")))));
        }
        return output.writeBlock(block.toString());
    }

    @Override
    public Result writeUserpicsEnd() {
        return output.writeBlock(popNesting());
    }

    @Override
    public Result writeStoriesStart(int count) {
        return output.writeBlock(prepareObjectItemStart("stories") + pushNesting(JsonContext.ARRAY));
    }

    @Override
    public Result writeStoriesSlice(ExportData.StoriesSlice data) {
        if (data.list.isEmpty()) {
            return Result.SUCCESS;
        }
        StringBuilder block = new StringBuilder();
        for (ExportData.Story story : data.list) {
            block.append(prepareArrayItemStart()).append(context.serializeObject(
                    new JsonContext.Item("date", story.date != 0 ? JsonContext.serializeDate(story.date) : ""),
                    new JsonContext.Item("date_unixtime", story.date != 0 ? JsonContext.serializeDateRaw(story.date) : ""),
                    new JsonContext.Item("expires", story.expires != 0 ? JsonContext.serializeDate(story.expires) : ""),
                    new JsonContext.Item("expires_unixtime", story.expires != 0 ? JsonContext.serializeDateRaw(story.expires) : ""),
                    new JsonContext.Item("pinned", JsonContext.bool(story.pinned)),
                    new JsonContext.Item("media", JsonContext.serializeString(skipReasonText(story.file(), "Photo")))));
        }
        return output.writeBlock(block.toString());
    }

    @Override
    public Result writeStoriesEnd() {
        return output.writeBlock(popNesting());
    }

    @Override
    public Result writeProfileMusicStart(int count) {
        return output.writeBlock(prepareObjectItemStart("profile_music") + pushNesting(JsonContext.ARRAY));
    }

    @Override
    public Result writeProfileMusicSlice(ArrayList<ExportData.Message> data) {
        if (data.isEmpty()) {
            return Result.SUCCESS;
        }
        StringBuilder block = new StringBuilder();
        for (ExportData.Message message : data) {
            block.append(prepareArrayItemStart())
                    .append(JsonMessageSerializer.serialize(context, message, new HashMap<>(), ""));
        }
        return output.writeBlock(block.toString());
    }

    @Override
    public Result writeProfileMusicEnd() {
        return output.writeBlock(popNesting());
    }

    @Override
    public Result writeContactsList(ExportData.ContactsList data) {
        Result result = writeSavedContacts(data);
        return result.isSuccess() ? writeFrequentContacts(data) : result;
    }

    private Result writeSavedContacts(ExportData.ContactsList data) {
        StringBuilder block = new StringBuilder(prepareObjectItemStart("contacts"));
        block.append(pushNesting(JsonContext.OBJECT));
        block.append(prepareObjectItemStart("about")).append(JsonContext.serializeString(ExportEnvironment.ABOUT_CONTACTS));
        block.append(prepareObjectItemStart("list")).append(pushNesting(JsonContext.ARRAY));
        for (int index : ExportParser.sortedContactsIndices(data)) {
            ExportData.ContactInfo contact = data.list.get(index);
            block.append(prepareArrayItemStart());
            if (contact.firstName.isEmpty() && contact.lastName.isEmpty() && contact.phoneNumber.isEmpty()) {
                block.append(context.serializeObject(
                        new JsonContext.Item("date", JsonContext.serializeDate(contact.date)),
                        new JsonContext.Item("date_unixtime", JsonContext.serializeDateRaw(contact.date))));
            } else {
                block.append(context.serializeObject(
                        new JsonContext.Item("user_id", contact.userId != 0 ? String.valueOf(contact.userId) : ""),
                        new JsonContext.Item("first_name", JsonContext.serializeString(contact.firstName)),
                        new JsonContext.Item("last_name", JsonContext.serializeString(contact.lastName)),
                        new JsonContext.Item("phone_number", JsonContext.serializeString(ExportFormat.phoneNumber(contact.phoneNumber))),
                        new JsonContext.Item("date", JsonContext.serializeDate(contact.date)),
                        new JsonContext.Item("date_unixtime", JsonContext.serializeDateRaw(contact.date))));
            }
        }
        block.append(popNesting());
        return output.writeBlock(block + popNesting());
    }

    private static String topPeerType(ExportData.Peer peer) {
        if (peer.chat == null) {
            return "user";
        } else if (peer.chat.username.isEmpty()) {
            return peer.chat.isBroadcast ? "private_channel" : peer.chat.isSupergroup ? "private_supergroup" : "private_group";
        }
        return peer.chat.isBroadcast ? "public_channel" : "public_supergroup";
    }

    private void appendTopPeers(StringBuilder block, ArrayList<ExportData.TopPeer> peers, String category) {
        for (ExportData.TopPeer top : peers) {
            block.append(prepareArrayItemStart()).append(context.serializeObject(
                    new JsonContext.Item("id", String.valueOf(top.peer.bareId())),
                    new JsonContext.Item("category", JsonContext.serializeString(category)),
                    new JsonContext.Item("type", JsonContext.serializeString(topPeerType(top.peer))),
                    new JsonContext.Item("name", JsonContext.stringAllowNull(top.peer.name())),
                    new JsonContext.Item("rating", JsonContext.number(top.rating))));
        }
    }

    private Result writeFrequentContacts(ExportData.ContactsList data) {
        StringBuilder block = new StringBuilder(prepareObjectItemStart("frequent_contacts"));
        block.append(pushNesting(JsonContext.OBJECT));
        block.append(prepareObjectItemStart("about")).append(JsonContext.serializeString(ExportEnvironment.ABOUT_FREQUENT));
        block.append(prepareObjectItemStart("list")).append(pushNesting(JsonContext.ARRAY));
        appendTopPeers(block, data.correspondents, "people");
        appendTopPeers(block, data.inlineBots, "inline_bots");
        appendTopPeers(block, data.phoneCalls, "calls");
        block.append(popNesting());
        return output.writeBlock(block + popNesting());
    }

    @Override
    public Result writeSessionsList(ExportData.SessionsList data) {
        Result result = writeSessions(data);
        return result.isSuccess() ? writeWebSessions(data) : result;
    }

    private Result writeSessions(ExportData.SessionsList data) {
        StringBuilder block = new StringBuilder(prepareObjectItemStart("sessions"));
        block.append(pushNesting(JsonContext.OBJECT));
        block.append(prepareObjectItemStart("about")).append(JsonContext.serializeString(ExportEnvironment.ABOUT_SESSIONS));
        block.append(prepareObjectItemStart("list")).append(pushNesting(JsonContext.ARRAY));
        for (TLRPC.TL_authorization session : data.list) {
            block.append(prepareArrayItemStart()).append(context.serializeObject(
                    new JsonContext.Item("last_active", JsonContext.serializeDate(session.date_active)),
                    new JsonContext.Item("last_active_unixtime", JsonContext.serializeDateRaw(session.date_active)),
                    new JsonContext.Item("last_ip", JsonContext.serializeString(session.ip)),
                    new JsonContext.Item("last_country", JsonContext.serializeString(session.country)),
                    new JsonContext.Item("last_region", JsonContext.serializeString(session.region)),
                    new JsonContext.Item("application_name", JsonContext.stringAllowNull(session.app_name)),
                    new JsonContext.Item("application_version", JsonContext.stringAllowEmpty(session.app_version)),
                    new JsonContext.Item("device_model", JsonContext.serializeString(session.device_model)),
                    new JsonContext.Item("platform", JsonContext.serializeString(session.platform)),
                    new JsonContext.Item("system_version", JsonContext.serializeString(session.system_version)),
                    new JsonContext.Item("created", JsonContext.serializeDate(session.date_created)),
                    new JsonContext.Item("created_unixtime", JsonContext.serializeDateRaw(session.date_created))));
        }
        block.append(popNesting());
        return output.writeBlock(block + popNesting());
    }

    private Result writeWebSessions(ExportData.SessionsList data) {
        StringBuilder block = new StringBuilder(prepareObjectItemStart("web_sessions"));
        block.append(pushNesting(JsonContext.OBJECT));
        block.append(prepareObjectItemStart("about")).append(JsonContext.serializeString(ExportEnvironment.ABOUT_WEB_SESSIONS));
        block.append(prepareObjectItemStart("list")).append(pushNesting(JsonContext.ARRAY));
        for (ExportData.WebSession session : data.webList) {
            block.append(prepareArrayItemStart()).append(context.serializeObject(
                    new JsonContext.Item("last_active", JsonContext.serializeDate(session.lastActive())),
                    new JsonContext.Item("last_active_unixtime", JsonContext.serializeDateRaw(session.lastActive())),
                    new JsonContext.Item("last_ip", JsonContext.serializeString(session.ip())),
                    new JsonContext.Item("last_region", JsonContext.serializeString(session.region())),
                    new JsonContext.Item("bot_username", JsonContext.stringAllowNull(session.botUsername())),
                    new JsonContext.Item("domain_name", JsonContext.stringAllowNull(session.domain())),
                    new JsonContext.Item("browser", JsonContext.serializeString(session.browser())),
                    new JsonContext.Item("platform", JsonContext.serializeString(session.platform())),
                    new JsonContext.Item("created", JsonContext.serializeDate(session.created())),
                    new JsonContext.Item("created_unixtime", JsonContext.serializeDateRaw(session.created()))));
        }
        block.append(popNesting());
        return output.writeBlock(block + popNesting());
    }

    @Override
    public Result writeOtherData(ExportData.File data) {
        if (data.skipReason != ExportData.File.SkipReason.None || data.relativePath.isEmpty()) {
            return Result.SUCCESS;
        }
        File file = new File(settings.path + data.relativePath);
        String content = ExportFiles.readText(file);
        if (content == null) {
            return Result.error(file.getAbsolutePath());
        } else if (content.trim().isEmpty()) {
            return Result.SUCCESS;
        }
        StringBuilder block = new StringBuilder(prepareObjectItemStart("other_data"));
        try {
            Object value = new JSONTokener(content).nextValue();
            if (!(value instanceof JSONObject) && !(value instanceof JSONArray)) {
                return Result.error(file.getAbsolutePath());
            }
            appendJsonValue(block, value);
        } catch (JSONException e) {
            return Result.error(file.getAbsolutePath());
        }
        return output.writeBlock(block.toString());
    }

    private void appendJsonValue(StringBuilder block, Object value) throws JSONException {
        if (value instanceof JSONObject object) {
            block.append(pushNesting(JsonContext.OBJECT));
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                block.append(prepareObjectItemStart(key));
                appendJsonValue(block, object.get(key));
            }
            block.append(popNesting());
        } else if (value instanceof JSONArray array) {
            block.append(pushNesting(JsonContext.ARRAY));
            for (int i = 0; i < array.length(); i++) {
                block.append(prepareArrayItemStart());
                appendJsonValue(block, array.get(i));
            }
            block.append(popNesting());
        } else if (value == null || value == JSONObject.NULL) {
            block.append("null");
        } else if (value instanceof Boolean bool) {
            block.append(JsonContext.bool(bool));
        } else if (value instanceof Number number) {
            block.append(number);
        } else {
            block.append(JsonContext.serializeString(value.toString()));
        }
    }

    @Override
    public Result writeDialogsStart(ExportData.DialogsInfo data) {
        return Result.SUCCESS;
    }

    private static String dialogTypeString(ExportData.DialogInfo.Type type) {
        return switch (type) {
            case Unknown -> "";
            case Self -> "saved_messages";
            case Replies -> "replies";
            case VerifyCodes -> "verification_codes";
            case Personal -> "personal_chat";
            case Bot -> "bot_chat";
            case PrivateGroup -> "private_group";
            case PrivateSupergroup -> "private_supergroup";
            case PublicSupergroup -> "public_supergroup";
            case PrivateChannel -> "private_channel";
            case PublicChannel -> "public_channel";
        };
    }

    @Override
    public Result writeDialogStart(ExportData.DialogInfo data) {
        if (!settings.onlySinglePeer()) {
            Result result = validateDialogsMode(data.isLeftChannel);
            if (!result.isSuccess()) {
                return result;
            }
        }
        StringBuilder block = new StringBuilder(settings.onlySinglePeer() ? "" : prepareArrayItemStart());
        block.append(pushNesting(JsonContext.OBJECT));
        if (!data.isSavedOrService()) {
            block.append(prepareObjectItemStart("name")).append(JsonContext.stringAllowNull(ExportData.composeName(data.name, data.lastName)));
        }
        block.append(prepareObjectItemStart("type")).append(JsonContext.stringAllowNull(dialogTypeString(data.type)));
        block.append(prepareObjectItemStart("id")).append(data.bareId());
        block.append(prepareObjectItemStart("messages"));
        block.append(pushNesting(JsonContext.ARRAY));
        return output.writeBlock(block.toString());
    }

    private Result validateDialogsMode(boolean isLeftChannel) {
        DialogsMode mode = isLeftChannel ? DialogsMode.Left : DialogsMode.Chats;
        if (dialogsMode == mode) {
            return Result.SUCCESS;
        } else if (dialogsMode != DialogsMode.None) {
            Result result = writeChatsEnd();
            if (!result.isSuccess()) {
                return result;
            }
        }
        dialogsMode = mode;
        return writeChatsStart(isLeftChannel ? "left_chats" : "chats", isLeftChannel ? ExportEnvironment.ABOUT_LEFT_CHATS : ExportEnvironment.ABOUT_CHATS);
    }

    private Result writeChatsStart(String listName, String about) {
        return output.writeBlock(prepareObjectItemStart(listName)
                + pushNesting(JsonContext.OBJECT)
                + prepareObjectItemStart("about") + JsonContext.serializeString(about)
                + prepareObjectItemStart("list") + pushNesting(JsonContext.ARRAY));
    }

    private Result writeChatsEnd() {
        String block = popNesting();
        return output.writeBlock(block + popNesting());
    }

    @Override
    public Result writeDialogSlice(ExportData.MessagesSlice data) {
        StringBuilder block = new StringBuilder();
        for (ExportData.Message message : data.list) {
            if (ExportParser.skipMessageByDate(message, settings)) {
                continue;
            }
            block.append(prepareArrayItemStart())
                    .append(JsonMessageSerializer.serialize(context, message, data.peers, ExportEnvironment.INTERNAL_LINKS_DOMAIN));
        }
        return block.length() == 0 ? Result.SUCCESS : output.writeBlock(block.toString());
    }

    @Override
    public Result writeDialogEnd() {
        String block = popNesting();
        return output.writeBlock(block + popNesting());
    }

    @Override
    public Result writeDialogsEnd() {
        if (settings.onlySinglePeer() || dialogsMode == DialogsMode.None) {
            return Result.SUCCESS;
        }
        return writeChatsEnd();
    }

    @Override
    public Result finish() {
        if (settings.onlySinglePeer()) {
            return Result.SUCCESS;
        }
        return output.writeBlock(popNesting());
    }
}
