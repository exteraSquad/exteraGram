package com.exteragram.messenger

import android.app.Activity
import android.content.SharedPreferences
import com.exteragram.messenger.preferences.ktx.boolean
import com.exteragram.messenger.preferences.ktx.int
import org.telegram.messenger.ApplicationLoader

object ExteraConfig {

    private val sharedPreferences: SharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE)

    // Appearance
    // Application
    var useSystemFonts by sharedPreferences.boolean("useSystemFont", false)
    // General
    var hideAllChats by sharedPreferences.boolean("hideAllChats", false)
    var hideProxySponsor by sharedPreferences.boolean("hideProxySponsor", true)
    var hidePhoneNumber by sharedPreferences.boolean("hidePhoneNumber", true)
    var showID by sharedPreferences.boolean("showID", false)
    var chatsOnTitle by sharedPreferences.boolean("chatsOnTitle", true)
    var forceTabletMode by sharedPreferences.boolean("forceTabletMode", false)
    // Drawer
    var newGroup by sharedPreferences.boolean("newGroup", true)
    var newSecretChat by sharedPreferences.boolean("newSecretChat", false)
    var newChannel by sharedPreferences.boolean("newChannel", false)
    var contacts by sharedPreferences.boolean("contacts", true)
    var calls by sharedPreferences.boolean("calls", false)
    var peopleNearby by sharedPreferences.boolean("peoplesNearby", false)
    var archivedChats by sharedPreferences.boolean("archivedChats", true)
    var savedMessages by sharedPreferences.boolean("savedMessages", true)
    var inviteFriends by sharedPreferences.boolean("inviteFriends", true)
    var telegramFeatures by sharedPreferences.boolean("telegramFeatures", false)

    // Chats
    // Stickers
    var stickerSize by sharedPreferences.int("stickerSize", 100)
    var hideStickerTime by sharedPreferences.boolean("hideStickerTime", false)
    // General
    var hideSendAsChannel by sharedPreferences.boolean("hideSendAsChannel", false)
    var includeArchivedChatsInForwards by sharedPreferences.boolean("includeArchivedChatsInForwards", true)
    var hideKeyboardOnScroll by sharedPreferences.boolean("hideKeyboardOnScroll", false)
    var archiveOnPull by sharedPreferences.boolean("archiveOnPull", true)
    var unlimitedRecentStickers by sharedPreferences.boolean("unlimitedRecentStickers", false)
    var unlimitedPinnedChats by sharedPreferences.boolean("unlimitedPinnedChats", false)
    // Media
    var HQVoiceMessage by sharedPreferences.boolean("HQVoiceMessage", true)
    var rearVideoMessages by sharedPreferences.boolean("rearVideoMessages", false)
    var autopause by sharedPreferences.boolean("pauseOnMinimize", false)
    var disablePlayback by sharedPreferences.boolean("disablePlayback", false)
}