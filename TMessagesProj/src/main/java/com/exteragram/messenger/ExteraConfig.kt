package com.exteragram.messenger

import android.app.Activity
import android.content.SharedPreferences
import com.exteragram.messenger.preferences.ktx.boolean
import com.exteragram.messenger.preferences.ktx.int
import org.telegram.messenger.ApplicationLoader

object ExteraConfig {

    private val sharedPreferences: SharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE)

    // Appearance
    // Icon
    var appIcon by sharedPreferences.int("appIcon", 0)
    var chatsOnTitle by sharedPreferences.boolean("chatsOnTitle", true)
    // General
    var hideAllChats by sharedPreferences.boolean("hideAllChats", false)
    var hidePhoneNumber by sharedPreferences.boolean("hidePhoneNumber", true)
    var archiveOnPull by sharedPreferences.boolean("ArchiveOnPull", true)
    var includeArchivedChatsInForwards by sharedPreferences.boolean("IncludeArchivedChatsInForwards", true)
    var hideKeyboardOnScroll by sharedPreferences.boolean("HideKeyboardOnScroll", true)
    var showID by sharedPreferences.boolean("ShowID", false)
    var forceTabletMode by sharedPreferences.boolean("ForceTabletMode", false)
    // Fonts
    var useSystemFont by sharedPreferences.boolean("useSystemFont", false)
    // Drawer
    var newGroup by sharedPreferences.boolean("newGroup", true)
    var newSecretChat by sharedPreferences.boolean("newSecretChat", false)
    var newChannel by sharedPreferences.boolean("newChannel", false)
    var Contacts by sharedPreferences.boolean("Contacts", true)
    var Calls by sharedPreferences.boolean("Calls", false)
    var peopleNearby by sharedPreferences.boolean("peoplesNearby", false)
    var savedMessages by sharedPreferences.boolean("SavedMessages", true)
    var inviteFriends by sharedPreferences.boolean("InviteFriends", true)
    var telegramFeatures by sharedPreferences.boolean("TelegramFeatures", false)

    // Chats
    // Sticker Size
    var StickerSize by sharedPreferences.int("StickerSize", 100)
    var hideStickerTime by sharedPreferences.boolean("hideStickerTime", false)
    // Media
    var HQVoiceMessage by sharedPreferences.boolean("HQVoiceMessage", true)
    var rearVideoMessages by sharedPreferences.boolean("rearVideoMessages", false)
    // Test features
    var unlimitedPinnedChats by sharedPreferences.boolean("unlimitedPinnedChats", false)
    var pauseOnMinimize by sharedPreferences.boolean("pauseOnMinimize", false)
    var hideSendAsPeer by sharedPreferences.boolean("hideSendAsPeer", false)
    var recentStickers by sharedPreferences.boolean("recentStickers", false)

    // Security
    var hideProxySponsor by sharedPreferences.boolean("hideProxySponsor", true)
}