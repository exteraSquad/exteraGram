package com.exteragram.messenger.preferences

import android.graphics.Color
import android.os.Build
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import com.exteragram.messenger.ExteraConfig
import com.exteragram.extras.ExteraExtras
import com.exteragram.extras.AppIcons
import ua.itaysonlab.tgkit.ktx.*

class AppearancePreferencesEntry : BasePreferencesEntry {
    override fun getPreferences(bf: BaseFragment) = tgKitScreen(LocaleController.getString("Appearance", R.string.Appearance)) {
        category(LocaleController.getString("Application", R.string.Application)) {
            list {
                title = LocaleController.getString("AppIcon", R.string.AppIcon)

                contractIcons({
                    return@contractIcons listOf(
                        Triple(0, LocaleController.getString("DefaultIcon", R.string.DefaultIcon), R.mipmap.ic_launcher),
                        Triple(1, LocaleController.getString("MaterialYouIcon", R.string.MaterialYouIcon), R.mipmap.ic_launcher_materialyou),
                        Triple(2, LocaleController.getString("yandexIcon", R.string.yandexIcon), R.mipmap.ic_launcher_alisa),
                        Triple(3, LocaleController.getString("the8055uIcon", R.string.the8055uIcon), R.mipmap.ic_launcher_the8055u),
                        Triple(4, LocaleController.getString("itsv1edsIcon", R.string.itsv1edsIcon), R.mipmap.ic_launcher_itsv1eds),
                        Triple(5, LocaleController.getString("asscatchemIcon", R.string.asscatchemIcon), R.mipmap.ic_launcher_asscatchem),
                        Triple(6, LocaleController.getString("ghoulghoulchanIcon", R.string.ghoulghoulchanIcon), R.mipmap.ic_launcher_ghoulghoulchan)
                    )
                }, {
                    return@contractIcons when (ExteraConfig.appIcon) {
                        1 -> LocaleController.getString("MaterialYouIcon", R.string.MaterialYouIcon)
                        2 -> LocaleController.getString("yandexIcon", R.string.yandexIcon)
                        3 -> LocaleController.getString("the8055uIcon", R.string.the8055uIcon)
                        4 -> LocaleController.getString("itsv1edsIcon", R.string.itsv1edsIcon)
                        5 -> LocaleController.getString("asscatchemIcon", R.string.asscatchemIcon)
                        6 -> LocaleController.getString("ghoulghoulchanIcon", R.string.ghoulghoulchanIcon)
                        else -> LocaleController.getString("DefaultIcon", R.string.DefaultIcon)
                    }
                }) {
                    ExteraConfig.appIcon = it
                    AppIcons.setIcon(it)
                }
            }
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
                    bf.parentActivity.window.statusBarColor = if (Theme.getColor(
                            Theme.key_actionBarDefault,
                            null,
                            true
                        ) == Color.WHITE
                    ) ExteraExtras.lightStatusbarColor else ExteraExtras.darkStatusbarColor
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