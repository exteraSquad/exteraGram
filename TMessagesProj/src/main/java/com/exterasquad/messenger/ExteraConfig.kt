package com.exterasquad.messenger

import android.app.Activity
import android.content.SharedPreferences
import android.content.pm.PackageManager
import com.exterasquad.messenger.preferences.ktx.boolean
import com.exterasquad.messenger.preferences.ktx.int
import com.exterasquad.messenger.preferences.ktx.string
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MessagesController

import kotlin.system.exitProcess

object ExteraConfig {

    private val sharedPreferences: SharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE)
    // Security
    var hideProxySponsor by sharedPreferences.boolean("ex_hideProxySponsor", true)

    // Chats
    var StickerSize by sharedPreferences.int("ex_StickerSize", 100)
    var hideStickerTime by sharedPreferences.boolean("ex_hideStickerTime", false)

    var HQVoiceMessage by sharedPreferences.boolean("ex_HQVoiceMessage", true)
    var rearVideoMessages by sharedPreferences.boolean("ex_rearVideoMessages", false)

    // Appearance
    var flatActionbar by sharedPreferences.boolean("ex_flatActionbar", true)
    var hideAllChats by sharedPreferences.boolean("ex_hideAllChats", false)

    // Test appearance
    var useSystemFont by sharedPreferences.boolean("ex_useSystemFont", false)

    var newGroup by sharedPreferences.boolean("ex_newGroup", true)
    var newSecretChat by sharedPreferences.boolean("ex_newSecretChat", false)
    var newChannel by sharedPreferences.boolean("ex_newChannel", false)
    var Contacts by sharedPreferences.boolean("ex_Contacts", true)
    var Calls by sharedPreferences.boolean("ex_Calls", false)
    var peopleNearby by sharedPreferences.boolean("ex_peoplesNearby", false)
    var savedMessages by sharedPreferences.boolean("ex_SavedMessages", true)
    var inviteFriends by sharedPreferences.boolean("ex_InviteFriends", true)
    var telegramFeatures by sharedPreferences.boolean("ex_TelegramFeatures", false)

}