package com.exteragram.messenger.preferences

import android.graphics.Color
import android.os.Build
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import com.exteragram.messenger.ExteraConfig
import ua.itaysonlab.tgkit.ktx.*

class AppearancePreferencesEntry : BasePreferencesEntry {
    override fun getPreferences(bf: BaseFragment) = tgKitScreen(LocaleController.getString("Appearance", R.string.Appearance)) {
        category(LocaleController.getString("Application", R.string.Application)) {
            switch {
                title = LocaleController.getString("SystemFonts", R.string.SystemFonts)

                contract({
                    return@contract ExteraConfig.useSystemFonts
                }) {
                    ExteraConfig.useSystemFonts = it
                }
            }
            switch {
                title = LocaleController.getString("SystemEmoji", R.string.SystemEmoji)

                contract({
                    return@contract SharedConfig.useSystemEmoji
                }) {
                    SharedConfig.toggleSystemEmoji()
                }
            }
        }

        category(LocaleController.getString("General", R.string.General)) {
            switch {
                title = LocaleController.getString("HideAllChats", R.string.HideAllChats)

                contract({
                    return@contract ExteraConfig.hideAllChats
                }) {
                    ExteraConfig.hideAllChats = it
                }
            }
            switch {
                title = LocaleController.getString("HideProxySponsor", R.string.HideProxySponsor)

                contract({
                    return@contract ExteraConfig.hideProxySponsor
                }) {
                    ExteraConfig.hideProxySponsor = it
                }
            }
            switch {
                title = LocaleController.getString("HidePhoneNumber", R.string.HidePhoneNumber)
                summary = LocaleController.getString("RestartRequired", R.string.RestartRequired)

                contract({
                    return@contract ExteraConfig.hidePhoneNumber
                }) {
                    ExteraConfig.hidePhoneNumber = it
                }
            }
            switch {
                title = LocaleController.getString("ChatsOnTitle", R.string.ChatsOnTitle)
                summary = LocaleController.getString("RestartRequired", R.string.RestartRequired)

                contract({
                    return@contract ExteraConfig.chatsOnTitle
                }) {
                    ExteraConfig.chatsOnTitle = it
                }
            }
            switch {
                title = LocaleController.getString("ShowID", R.string.ShowID)

                contract({
                    return@contract ExteraConfig.showID
                }) {
                    ExteraConfig.showID = it
                }
            }
            switch {
                title = LocaleController.getString("SB", R.string.SB)
                summary = LocaleController.getString("SBDescription", R.string.SBDescription)

                contract({
                    return@contract SharedConfig.noStatusBar
                }) {
                    SharedConfig.toggleNoStatusBar()
                }
            }
            switch {
                title = LocaleController.getString("ForceTabletMode", R.string.ForceTabletMode)
                summary = LocaleController.getString("RestartRequired", R.string.RestartRequired)

                contract({
                    return@contract ExteraConfig.forceTabletMode
                }) {
                    ExteraConfig.forceTabletMode = it
                }
            }
        }
        category(LocaleController.getString("Drawer", R.string.Drawer)) {
            textDetail {
                title = LocaleController.getString("Attention", R.string.Attention)
                detail = LocaleController.getString("DrawerRR", R.string.DrawerRR)
            }
            switch {
                title = LocaleController.getString("NewGroup", R.string.NewGroup)

                contract({
                    return@contract ExteraConfig.newGroup
                }) {
                    ExteraConfig.newGroup = it
                }
            }
            switch {
                title = LocaleController.getString("NewSecretChat", R.string.NewSecretChat)

                contract({
                    return@contract ExteraConfig.newSecretChat
                }) {
                    ExteraConfig.newSecretChat = it
                }
            }
            switch {
                title = LocaleController.getString("NewChannel", R.string.NewChannel)

                contract({
                    return@contract ExteraConfig.newChannel
                }) {
                    ExteraConfig.newChannel = it
                }
            }
            switch {
                title = LocaleController.getString("Contacts", R.string.Contacts)

                contract({
                    return@contract ExteraConfig.contacts
                }) {
                    ExteraConfig.contacts = it
                }
            }
            switch {
                title = LocaleController.getString("Calls", R.string.Calls)

                contract({
                    return@contract ExteraConfig.calls
                }) {
                    ExteraConfig.calls = it
                }
            }
            switch {
                title = LocaleController.getString("PeopleNearby", R.string.PeopleNearby)
                summary = LocaleController.getString("PeopleNearbyDescription", R.string.PeopleNearbyDescription)

                contract({
                    return@contract ExteraConfig.peopleNearby
                }) {
                    ExteraConfig.peopleNearby = it
                }
            }
            switch {
                title = LocaleController.getString("ArchivedChats", R.string.ArchivedChats)

                contract({
                    return@contract ExteraConfig.archivedChats
                }) {
                    ExteraConfig.archivedChats = it
                }
            }
            switch {
                title = LocaleController.getString("SavedMessages", R.string.SavedMessages)

                contract({
                    return@contract ExteraConfig.savedMessages
                }) {
                    ExteraConfig.savedMessages = it
                }
            }
            switch {
                title = LocaleController.getString("InviteFriends", R.string.InviteFriends)

                contract({
                    return@contract ExteraConfig.inviteFriends
                }) {
                    ExteraConfig.inviteFriends = it
                }
            }
            switch {
                title = LocaleController.getString("TelegramFeatures", R.string.TelegramFeatures)

                contract({
                    return@contract ExteraConfig.telegramFeatures
                }) {
                    ExteraConfig.telegramFeatures = it
                }
            }
        }
    }
}