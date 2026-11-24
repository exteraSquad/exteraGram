/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.export.output.html;

import com.exteragram.messenger.export.ExportFormat;
import com.exteragram.messenger.export.ExportSettings;
import com.exteragram.messenger.export.api.ExportData;
import com.exteragram.messenger.export.api.ExportParser;
import com.exteragram.messenger.export.output.AbstractWriter;
import com.exteragram.messenger.export.output.ExportEnvironment;
import com.exteragram.messenger.export.output.ExportFiles;
import com.exteragram.messenger.export.output.OutputFile;
import com.exteragram.messenger.export.output.Result;

import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HtmlWriter extends AbstractWriter {

    private static final int MESSAGES_IN_FILE = 1000;
    private static final int CHATS_PRIORITY = 0;
    private static final int CONTACTS_PRIORITY = 2;
    private static final int FREQUENT_CONTACTS_PRIORITY = 3;
    private static final int USERPICS_PRIORITY = 4;
    private static final int STORIES_PRIORITY = 5;
    private static final int PROFILE_MUSIC_PRIORITY = 6;
    private static final int SESSIONS_PRIORITY = 7;
    private static final int WEB_SESSIONS_PRIORITY = 8;
    private static final int OTHER_PRIORITY = 9;

    private static final String MAIN_FILE = "export_results.html";
    private static final String CHATS_FILE = "lists/chats.html";
    private static final String USERPICS_FILE = "lists/profile_pictures.html";
    private static final String STORIES_FILE = "lists/stories.html";
    private static final String PROFILE_MUSIC_FILE = "lists/profile_music.html";

    private enum DialogsMode {
        None, Chats, Left
    }

    private record SavedSection(int priority, String label, String type, int count, String path) {
    }

    private ExportSettings settings;
    private OutputFile.Stats stats;

    private HtmlContext summary;
    private boolean summaryNeedDivider;
    private boolean haveSections;
    private final ArrayList<SavedSection> savedSections = new ArrayList<>();

    private int selfColorIndex;
    private ExportData.PersonalInfo delayedPersonalInfo;

    private HtmlContext userpics;
    private int userpicsCount;

    private HtmlContext stories;
    private int storiesCount;

    private HtmlContext profileMusic;
    private int profileMusicCount;

    private HtmlContext chats;
    private DialogsMode dialogsMode = DialogsMode.None;

    private HtmlContext chat;
    private ExportData.DialogInfo dialog;
    private boolean chatFileEmpty;
    private int messagesCount;
    private int dateMessageId;
    private HtmlContext.MessageInfo lastMessageInfo;
    private final ArrayList<Integer> lastMessageIdsPerFile = new ArrayList<>();

    private HtmlContext fileWithRelativePath(String path) {
        return new HtmlContext(settings.path + path, settings.path, stats);
    }

    private static String messagesFile(int index) {
        return "messages" + (index > 0 ? String.valueOf(index + 1) : "") + ".html";
    }

    @Override
    public String mainFilePath() {
        return settings.path + (settings.onlySinglePeer() ? messagesFile(0) : MAIN_FILE);
    }

    @Override
    public Result start(ExportSettings settings, OutputFile.Stats stats) {
        this.settings = settings;
        this.stats = stats;
        Result result = ExportFiles.copyAssets(settings.path, stats);
        if (!result.isSuccess() || settings.onlySinglePeer()) {
            return result;
        }
        summary = fileWithRelativePath(MAIN_FILE);
        return summary.writeBlock(summary.pushHeader("Exported Data", "") + summary.pushDiv("page_body"));
    }

    @Override
    public Result writePersonal(ExportData.PersonalInfo data) {
        selfColorIndex = data.user.info.colorIndex;
        if (settings.has(ExportSettings.Userpics)) {
            delayedPersonalInfo = data;
            return Result.SUCCESS;
        }
        return writePreparedPersonal(data, "");
    }

    private Result writeDelayedPersonal(String userpicPath) {
        if (delayedPersonalInfo == null) {
            return Result.SUCCESS;
        }
        ExportData.PersonalInfo info = delayedPersonalInfo;
        delayedPersonalInfo = null;
        Result result = writePreparedPersonal(info, userpicPath);
        if (!result.isSuccess()) {
            return result;
        }
        if (userpicsCount != 0) {
            pushUserpicsSection();
        }
        return Result.SUCCESS;
    }

    private Result writePreparedPersonal(ExportData.PersonalInfo data, String userpicPath) {
        ExportData.ContactInfo info = data.user.info;
        HtmlContext.UserpicData userpic = new HtmlContext.UserpicData();
        userpic.colorIndex = selfColorIndex;
        userpic.pixelSize = HtmlContext.PERSONAL_USERPIC_SIZE;
        userpic.largeLink = userpicPath.isEmpty() ? "" : USERPICS_FILE;
        userpic.imageLink = HtmlContext.writeUserpicThumb(settings.path, userpicPath, userpic, "_info");
        userpic.firstName = info.firstName;
        userpic.lastName = info.lastName;

        StringBuilder block = new StringBuilder(summary.pushDiv("personal_info clearfix"));
        block.append(summary.pushDiv("pull_right userpic_wrap")).append(summary.pushUserpic(userpic)).append(summary.popTag());
        pushRows(block, "names", "First name", info.firstName, "Last name", info.lastName);
        pushRows(block, "info", "Phone number", ExportFormat.phoneNumber(info.phoneNumber),
                "Username", data.user.username.isEmpty() ? "" : "@" + data.user.username);
        pushRows(block, "bio", "Bio", data.bio);
        block.append(summary.popTag());
        summaryNeedDivider = true;
        return summary.writeBlock(block.toString());
    }

    private void pushRows(StringBuilder block, String name, String... values) {
        block.append(summary.pushDiv("rows " + name));
        for (int i = 0; i + 1 < values.length; i += 2) {
            if (values[i + 1] == null || values[i + 1].isEmpty()) {
                continue;
            }
            block.append(summary.pushDiv("row"));
            block.append(summary.pushDiv("label details")).append(HtmlContext.serializeString(values[i])).append(summary.popTag());
            block.append(summary.pushDiv("value bold")).append(HtmlContext.serializeString(values[i + 1])).append(summary.popTag());
            block.append(summary.popTag());
        }
        block.append(summary.popTag());
    }

    private void pushSection(int priority, String label, String type, int count, String path) {
        savedSections.add(new SavedSection(priority, label, type, count, path));
    }

    private void pushUserpicsSection() {
        pushSection(USERPICS_PRIORITY, "Profile pictures", "photos", userpicsCount, USERPICS_FILE);
    }

    private Result writeSections() {
        if (savedSections.isEmpty()) {
            return Result.SUCCESS;
        } else if (!haveSections) {
            Result result = summary.writeBlock(summary.pushDiv(summaryNeedDivider ? "sections with_divider" : "sections"));
            if (!result.isSuccess()) {
                return result;
            }
            haveSections = true;
            summaryNeedDivider = false;
        }
        savedSections.sort(Comparator.comparingInt(SavedSection::priority));
        StringBuilder block = new StringBuilder();
        for (SavedSection section : savedSections) {
            block.append(summary.pushSection(section.label(), section.type(), section.count(), summary.relativePath(section.path())));
        }
        savedSections.clear();
        return summary.writeBlock(block.toString());
    }

    @Override
    public Result writeUserpicsStart(int count) {
        userpicsCount = count;
        if (count == 0) {
            return Result.SUCCESS;
        }
        userpics = fileWithRelativePath(USERPICS_FILE);
        Result result = userpics.writeBlock(userpics.pushHeader("Profile pictures", MAIN_FILE)
                + userpics.pushDiv("page_body list_page")
                + userpics.pushDiv("entry_list"));
        if (!result.isSuccess()) {
            return result;
        }
        if (delayedPersonalInfo == null) {
            pushUserpicsSection();
        }
        return Result.SUCCESS;
    }

    private static String skipStatus(ExportData.File file, String noun) {
        return switch (file.skipReason) {
            case Unavailable -> "(" + noun + " unavailable, please try again later)";
            case FileSize -> "(" + noun + " exceeds maximum size. Change data exporting settings to download.)";
            case FileType -> "(" + noun + " not included. Change data exporting settings to download.)";
            case None, DateLimits -> ExportFormat.fileSize(file.size);
        };
    }

    @Override
    public Result writeUserpicsSlice(ArrayList<ExportData.Photo> data) {
        if (data.isEmpty()) {
            return Result.SUCCESS;
        }
        Result result = writeDelayedPersonal(data.get(0).image.file.relativePath);
        if (!result.isSuccess() || userpics == null) {
            return result;
        }
        StringBuilder block = new StringBuilder();
        for (ExportData.Photo photo : data) {
            String path = photo.image.file.relativePath;
            HtmlContext.UserpicData userpic = new HtmlContext.UserpicData();
            userpic.colorIndex = selfColorIndex;
            userpic.pixelSize = HtmlContext.ENTRY_USERPIC_SIZE;
            userpic.imageLink = HtmlContext.writeUserpicThumb(settings.path, path, userpic, "_thumb");
            userpic.firstName = path;
            block.append(userpics.pushListEntry(userpic, path.isEmpty() ? "Photo unavailable" : path,
                    skipStatus(photo.image.file, "Photo"), photo.date > 0 ? ExportFormat.dateTime(photo.date) : "", path));
        }
        return userpics.writeBlock(block.toString());
    }

    @Override
    public Result writeUserpicsEnd() {
        Result result = writeDelayedPersonal("");
        if (!result.isSuccess()) {
            return result;
        } else if (userpics != null) {
            HtmlContext file = userpics;
            userpics = null;
            return file.close();
        }
        return Result.SUCCESS;
    }

    @Override
    public Result writeStoriesStart(int count) {
        storiesCount = count;
        if (count == 0) {
            return Result.SUCCESS;
        }
        stories = fileWithRelativePath(STORIES_FILE);
        return stories.writeBlock(stories.pushHeader("Stories archive", MAIN_FILE)
                + stories.pushDiv("page_body list_page")
                + stories.pushDiv("entry_list"));
    }

    @Override
    public Result writeStoriesSlice(ExportData.StoriesSlice data) {
        storiesCount -= data.skipped;
        if (data.list.isEmpty() || stories == null) {
            return Result.SUCCESS;
        }
        StringBuilder block = new StringBuilder();
        for (ExportData.Story story : data.list) {
            ArrayList<String> status = new ArrayList<>();
            if (story.pinned) {
                status.add("Saved to Profile");
            }
            if (story.expires > 0) {
                status.add("Expiring: " + ExportFormat.dateTime(story.expires));
            }
            status.add(skipStatus(story.file(), "Story"));
            String path = story.file().relativePath;
            String image = story.thumb().file.relativePath.isEmpty() ? path : story.thumb().file.relativePath;
            String imageLink = ExportThumbs.write(settings.path, image,
                    ExportThumbs.fixedSize(HtmlContext.STORY_THUMB_WIDTH * 2, HtmlContext.STORY_THUMB_HEIGHT * 2), false, "_thumb").path();
            block.append(stories.pushStoriesListEntry(imageLink, path.isEmpty() ? "Story unavailable" : path, status,
                    story.date > 0 ? ExportFormat.dateTime(story.date) : "", story.caption, ExportEnvironment.INTERNAL_LINKS_DOMAIN, path));
        }
        return stories.writeBlock(block.toString());
    }

    @Override
    public Result writeStoriesEnd() {
        if (storiesCount > 0) {
            pushSection(STORIES_PRIORITY, "Stories archive", "stories", storiesCount, STORIES_FILE);
        }
        if (stories != null) {
            HtmlContext file = stories;
            stories = null;
            return file.close();
        }
        return Result.SUCCESS;
    }

    @Override
    public Result writeProfileMusicStart(int count) {
        profileMusicCount = count;
        if (count == 0) {
            return Result.SUCCESS;
        }
        profileMusic = fileWithRelativePath(PROFILE_MUSIC_FILE);
        return profileMusic.writeBlock(profileMusic.pushHeader("Profile Music", MAIN_FILE)
                + profileMusic.pushDiv("page_body list_page")
                + profileMusic.pushDiv("entry_list"));
    }

    @Override
    public Result writeProfileMusicSlice(ArrayList<ExportData.Message> data) {
        if (data.isEmpty() || profileMusic == null) {
            return Result.SUCCESS;
        }
        StringBuilder block = new StringBuilder();
        for (ExportData.Message message : data) {
            if (!(message.media.content instanceof ExportData.Document document) || !document.isAudioFile) {
                continue;
            }
            String path = document.file.relativePath;
            String title = !document.songTitle.isEmpty() ? document.songTitle : !document.name.isEmpty() ? document.name : "Unknown Track";
            String performer = !document.songPerformer.isEmpty() ? document.songPerformer : "Unknown Artist";
            block.append(profileMusic.pushAudioEntry(path.isEmpty() ? "File unavailable" : path, performer + " - " + title,
                    List.of(skipStatus(document.file, "File")), document.duration > 0 ? ExportFormat.duration(document.duration) : "", path));
        }
        return profileMusic.writeBlock(block.toString());
    }

    @Override
    public Result writeProfileMusicEnd() {
        if (profileMusicCount > 0) {
            pushSection(PROFILE_MUSIC_PRIORITY, "Profile Music", "music", profileMusicCount, PROFILE_MUSIC_FILE);
        }
        if (profileMusic != null) {
            HtmlContext file = profileMusic;
            profileMusic = null;
            return file.close();
        }
        return Result.SUCCESS;
    }

    @Override
    public Result writeContactsList(ExportData.ContactsList data) {
        Result result = writeSavedContacts(data);
        return result.isSuccess() ? writeFrequentContacts(data) : result;
    }

    private Result writeSavedContacts(ExportData.ContactsList data) {
        if (data.list.isEmpty()) {
            return Result.SUCCESS;
        }
        String filename = "lists/contacts.html";
        HtmlContext file = fileWithRelativePath(filename);
        StringBuilder block = new StringBuilder(file.pushHeader("Contacts", MAIN_FILE));
        block.append(file.pushDiv("page_body list_page"));
        block.append(file.pushAbout(ExportEnvironment.ABOUT_CONTACTS, false));
        block.append(file.pushDiv("entry_list"));
        for (int index : ExportParser.sortedContactsIndices(data)) {
            ExportData.ContactInfo contact = data.list.get(index);
            HtmlContext.UserpicData userpic = new HtmlContext.UserpicData();
            userpic.colorIndex = contact.colorIndex;
            userpic.pixelSize = HtmlContext.ENTRY_USERPIC_SIZE;
            userpic.firstName = contact.firstName;
            userpic.lastName = contact.lastName;
            if (contact.userId != 0) {
                userpic.tooltip = "ID: " + contact.userId;
            }
            block.append(file.pushListEntry(userpic, userpic.composeName("Deleted Account"),
                    ExportFormat.phoneNumber(contact.phoneNumber), ExportFormat.dateTime(contact.date), ""));
        }
        Result result = file.writeBlock(block.toString());
        if (!result.isSuccess()) {
            return result;
        }
        result = file.close();
        if (!result.isSuccess()) {
            return result;
        }
        pushSection(CONTACTS_PRIORITY, "Contacts", "contacts", data.list.size(), filename);
        return Result.SUCCESS;
    }

    private void appendTopPeers(HtmlContext file, StringBuilder block, ArrayList<ExportData.TopPeer> peers, String category) {
        for (ExportData.TopPeer top : peers) {
            HtmlContext.UserpicData userpic = new HtmlContext.UserpicData();
            userpic.colorIndex = ExportParser.peerColorIndex(top.peer.bareId());
            userpic.pixelSize = HtmlContext.ENTRY_USERPIC_SIZE;
            if (top.peer.chat != null) {
                userpic.firstName = top.peer.name();
            } else if (top.peer.user.isSelf) {
                userpic.firstName = "Saved messages";
            } else {
                userpic.firstName = top.peer.user.info.firstName;
                userpic.lastName = top.peer.user.info.lastName;
            }
            block.append(file.pushListEntry(userpic, userpic.composeName("Deleted Account"),
                    "Rating: " + ExportFormat.number(top.rating), category, ""));
        }
    }

    private Result writeFrequentContacts(ExportData.ContactsList data) {
        int size = data.correspondents.size() + data.inlineBots.size() + data.phoneCalls.size();
        if (size == 0) {
            return Result.SUCCESS;
        }
        String filename = "lists/frequent.html";
        HtmlContext file = fileWithRelativePath(filename);
        StringBuilder block = new StringBuilder(file.pushHeader("Frequent contacts", MAIN_FILE));
        block.append(file.pushDiv("page_body list_page"));
        block.append(file.pushAbout(ExportEnvironment.ABOUT_FREQUENT, false));
        block.append(file.pushDiv("entry_list"));
        appendTopPeers(file, block, data.correspondents, "people");
        appendTopPeers(file, block, data.inlineBots, "inline bots");
        appendTopPeers(file, block, data.phoneCalls, "calls");
        Result result = file.writeBlock(block.toString());
        if (!result.isSuccess()) {
            return result;
        }
        result = file.close();
        if (!result.isSuccess()) {
            return result;
        }
        pushSection(FREQUENT_CONTACTS_PRIORITY, "Frequent contacts", "frequent", size, filename);
        return Result.SUCCESS;
    }

    @Override
    public Result writeSessionsList(ExportData.SessionsList data) {
        Result result = writeSessions(data);
        return result.isSuccess() ? writeWebSessions(data) : result;
    }

    private Result writeSessions(ExportData.SessionsList data) {
        if (data.list.isEmpty()) {
            return Result.SUCCESS;
        }
        String filename = "lists/sessions.html";
        HtmlContext file = fileWithRelativePath(filename);
        StringBuilder block = new StringBuilder(file.pushHeader("Sessions", MAIN_FILE));
        block.append(file.pushDiv("page_body list_page"));
        block.append(file.pushAbout(ExportEnvironment.ABOUT_SESSIONS, false));
        block.append(file.pushDiv("entry_list"));
        for (TLRPC.TL_authorization session : data.list) {
            String appName = session.app_name == null || session.app_name.isEmpty() ? "Unknown" : session.app_name;
            String region = session.region != null ? session.region : "";
            String country = session.country != null ? session.country : "";
            block.append(file.pushSessionListEntry(session.api_id,
                    appName + " " + (session.app_version != null ? session.app_version : ""),
                    session.device_model + ", " + session.platform + " " + session.system_version,
                    List.of(session.ip + " – " + region + (region.isEmpty() || country.isEmpty() ? "" : ", ") + country,
                            "Last active: " + ExportFormat.dateTime(session.date_active),
                            "Created: " + ExportFormat.dateTime(session.date_created)),
                    ""));
        }
        Result result = file.writeBlock(block.toString());
        if (!result.isSuccess()) {
            return result;
        }
        result = file.close();
        if (!result.isSuccess()) {
            return result;
        }
        pushSection(SESSIONS_PRIORITY, "Sessions", "sessions", data.list.size(), filename);
        return Result.SUCCESS;
    }

    private Result writeWebSessions(ExportData.SessionsList data) {
        if (data.webList.isEmpty()) {
            return Result.SUCCESS;
        }
        String filename = "lists/web_sessions.html";
        HtmlContext file = fileWithRelativePath(filename);
        StringBuilder block = new StringBuilder(file.pushHeader("Web sessions", MAIN_FILE));
        block.append(file.pushDiv("page_body list_page"));
        block.append(file.pushAbout(ExportEnvironment.ABOUT_WEB_SESSIONS, false));
        block.append(file.pushDiv("entry_list"));
        for (ExportData.WebSession session : data.webList) {
            block.append(file.pushSessionListEntry((int) (0x1000 + ExportParser.stringBarePeerId(session.domain())),
                    session.domain().isEmpty() ? "Unknown" : session.domain(),
                    session.platform() + ", " + session.browser(),
                    List.of(session.ip() + " – " + session.region(),
                            "Last active: " + ExportFormat.dateTime(session.lastActive()),
                            "Created: " + ExportFormat.dateTime(session.created())),
                    session.botUsername().isEmpty() ? "" : "@" + session.botUsername()));
        }
        Result result = file.writeBlock(block.toString());
        if (!result.isSuccess()) {
            return result;
        }
        result = file.close();
        if (!result.isSuccess()) {
            return result;
        }
        pushSection(WEB_SESSIONS_PRIORITY, "Web sessions", "web", data.webList.size(), filename);
        return Result.SUCCESS;
    }

    @Override
    public Result writeOtherData(ExportData.File data) {
        if (!data.relativePath.isEmpty()) {
            pushSection(OTHER_PRIORITY, "Other data", "other", 1, data.relativePath);
        }
        return Result.SUCCESS;
    }

    @Override
    public Result writeDialogsStart(ExportData.DialogsInfo data) {
        if (data.size() == 0 || settings.onlySinglePeer()) {
            return Result.SUCCESS;
        }
        chats = fileWithRelativePath(CHATS_FILE);
        Result result = chats.writeBlock(chats.pushHeader("Chats", MAIN_FILE) + chats.pushDiv("page_body list_page"));
        if (!result.isSuccess()) {
            return result;
        }
        pushSection(CHATS_PRIORITY, "Chats", "chats", data.size(), CHATS_FILE);
        return writeSections();
    }

    @Override
    public Result writeDialogStart(ExportData.DialogInfo data) {
        chat = fileWithRelativePath(data.relativePath + messagesFile(0));
        chatFileEmpty = true;
        messagesCount = 0;
        dateMessageId = 0;
        lastMessageInfo = null;
        lastMessageIdsPerFile.clear();
        dialog = data;
        return Result.SUCCESS;
    }

    private String wrapMessageLink(int messageId, String text) {
        for (int index = 0; index < lastMessageIdsPerFile.size(); index++) {
            if (messageId <= lastMessageIdsPerFile.get(index)) {
                return "<a href=\"" + messagesFile(index) + "#go_to_message" + messageId + "\">" + text + "</a>";
            }
        }
        return "<a href=\"#go_to_message" + messageId + "\" onclick=\"return GoToMessage(" + messageId + ")\">" + text + "</a>";
    }

    private Result writeDialogOpening(int index) {
        String name = dialog.name.isEmpty() && dialog.lastName.isEmpty()
                ? "Deleted Account"
                : ExportData.composeName(dialog.name, dialog.lastName);
        StringBuilder block = new StringBuilder(chat.pushHeader(name, settings.onlySinglePeer() ? "" : CHATS_FILE));
        block.append(chat.pushDiv("page_body chat_page"));
        block.append(chat.pushDiv("history"));
        if (index > 0) {
            block.append(chat.pushTag("a", "class", "pagination block_link", "href", messagesFile(index - 1)));
            block.append("Previous messages");
            block.append(chat.popTag());
        }
        return chat.writeBlock(block.toString());
    }

    private Result switchToNextChatFile(int index) {
        String nextPath = messagesFile(index);
        Result result = chat.writeBlock(chat.pushTag("a", "class", "pagination block_link", "href", nextPath) + "Next messages" + chat.popTag());
        if (!result.isSuccess()) {
            return result;
        }
        result = chat.close();
        if (!result.isSuccess()) {
            return result;
        }
        chat = fileWithRelativePath(dialog.relativePath + nextPath);
        chatFileEmpty = true;
        return Result.SUCCESS;
    }

    @Override
    public Result writeDialogSlice(ExportData.MessagesSlice data) {
        int oldIndex = messagesCount > 0 ? (messagesCount - 1) / MESSAGES_IN_FILE : 0;
        HtmlContext.MessageInfo previous = lastMessageInfo;
        HtmlContext.MessageInfo saved = null;
        StringBuilder block = new StringBuilder();
        for (ExportData.Message message : data.list) {
            if (ExportParser.skipMessageByDate(message, settings)) {
                continue;
            }
            int newIndex = messagesCount / MESSAGES_IN_FILE;
            if (oldIndex != newIndex) {
                Result result = chat.writeBlock(block.toString());
                if (!result.isSuccess()) {
                    return result;
                }
                result = switchToNextChatFile(newIndex);
                if (!result.isSuccess()) {
                    return result;
                }
                lastMessageIdsPerFile.add(saved != null ? saved.id : lastMessageInfo.id);
                block = new StringBuilder();
                lastMessageInfo = null;
                previous = null;
                saved = null;
                oldIndex = newIndex;
            }
            if (chatFileEmpty) {
                Result result = writeDialogOpening(oldIndex);
                if (!result.isSuccess()) {
                    return result;
                }
                chatFileEmpty = false;
            }
            if (HtmlContext.displayDate(message.date, previous != null ? previous.date : 0)) {
                block.append(chat.pushServiceMessage(--dateMessageId, dialog, settings.path, ExportFormat.dateText(message.date), null));
            }
            saved = chat.pushMessage(block, message, previous, dialog, settings.path, data.peers,
                    ExportEnvironment.INTERNAL_LINKS_DOMAIN, this::wrapMessageLink);
            ++messagesCount;
            previous = saved;
        }
        if (saved != null) {
            lastMessageInfo = saved;
        }
        return block.length() == 0 ? Result.SUCCESS : chat.writeBlock(block.toString());
    }

    private Result writeEmptySinglePeer() {
        if (!settings.onlySinglePeer() || messagesCount != 0) {
            return Result.SUCCESS;
        }
        Result result = writeDialogOpening(0);
        if (!result.isSuccess()) {
            return result;
        }
        return chat.writeBlock(chat.pushServiceMessage(--dateMessageId, dialog, settings.path, "No exported messages", null));
    }

    private static String typeString(ExportData.DialogInfo.Type type) {
        return switch (type) {
            case Unknown -> "unknown";
            case Self, Replies, VerifyCodes, Personal -> "private";
            case Bot -> "bot";
            case PrivateGroup, PrivateSupergroup, PublicSupergroup -> "group";
            case PrivateChannel, PublicChannel -> "channel";
        };
    }

    private static String deletedString(ExportData.DialogInfo.Type type) {
        return switch (type) {
            case Unknown, Self, Replies, VerifyCodes, Personal, Bot -> "Deleted Account";
            case PrivateGroup, PrivateSupergroup, PublicSupergroup -> "Deleted Group";
            case PrivateChannel, PublicChannel -> "Deleted Channel";
        };
    }

    private static String countString(int count, boolean outgoing) {
        if (count == 1) {
            return outgoing ? "1 outgoing message" : "1 message";
        } else if (count == 0) {
            return outgoing ? "No outgoing messages" : "No messages";
        }
        return count + (outgoing ? " outgoing messages" : " messages");
    }

    @Override
    public Result writeDialogEnd() {
        Result result = writeEmptySinglePeer();
        if (!result.isSuccess()) {
            return result;
        }
        HtmlContext file = chat;
        chat = null;
        result = file.close();
        if (!result.isSuccess() || settings.onlySinglePeer()) {
            return result;
        }
        HtmlContext.UserpicData userpic = new HtmlContext.UserpicData();
        userpic.colorIndex = dialog.isSavedOrService() ? HtmlContext.SAVED_MESSAGES_COLOR_INDEX : ExportParser.peerColorIndex(dialog.bareId());
        userpic.pixelSize = HtmlContext.ENTRY_USERPIC_SIZE;
        userpic.firstName = switch (dialog.type) {
            case Self -> "Saved messages";
            case Replies -> "Replies";
            case VerifyCodes -> "Verification Codes";
            default -> dialog.name;
        };
        if (dialog.type == ExportData.DialogInfo.Type.Personal || dialog.type == ExportData.DialogInfo.Type.Bot) {
            userpic.lastName = dialog.lastName;
        }
        result = validateDialogsMode(dialog.isLeftChannel);
        if (!result.isSuccess()) {
            return result;
        }
        return chats.writeBlock(chats.pushListEntry(userpic, userpic.composeName(deletedString(dialog.type)),
                countString(messagesCount, dialog.onlyMyMessages), typeString(dialog.type),
                messagesCount > 0 ? dialog.relativePath + messagesFile(0) : ""));
    }

    private Result validateDialogsMode(boolean isLeftChannel) {
        DialogsMode mode = isLeftChannel ? DialogsMode.Left : DialogsMode.Chats;
        if (dialogsMode == mode) {
            return Result.SUCCESS;
        } else if (dialogsMode != DialogsMode.None) {
            Result result = chats.writeBlock(chats.popTag());
            if (!result.isSuccess()) {
                return result;
            }
        }
        dialogsMode = mode;
        return chats.writeBlock(chats.pushAbout(isLeftChannel ? ExportEnvironment.ABOUT_LEFT_CHATS : ExportEnvironment.ABOUT_CHATS, false)
                + chats.pushDiv("entry_list"));
    }

    @Override
    public Result writeDialogsEnd() {
        if (chats != null) {
            HtmlContext file = chats;
            chats = null;
            return file.close();
        }
        return Result.SUCCESS;
    }

    @Override
    public Result finish() {
        if (settings.onlySinglePeer()) {
            return Result.SUCCESS;
        }
        Result result = writeSections();
        if (!result.isSuccess()) {
            return result;
        }
        StringBuilder block = new StringBuilder();
        if (haveSections) {
            block.append(summary.popTag());
            summaryNeedDivider = true;
            haveSections = false;
        }
        block.append(summary.pushAbout(ExportEnvironment.ABOUT_TELEGRAM, summaryNeedDivider));
        result = summary.writeBlock(block.toString());
        return result.isSuccess() ? summary.close() : result;
    }
}
