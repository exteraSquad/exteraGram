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
    override fun getPreferences(bf: BaseFragment) = tgKitScreen(LocaleController.getString("exteraAppearance", R.string.exteraAppearance)) {
        category(LocaleController.getString("exteraApplication", R.string.exteraApplication)) {
            list {
                title = LocaleController.getString("exteraAppIcon", R.string.exteraAppIcon)

                contractIcons({
                    return@contractIcons listOf(
                        Triple(0, LocaleController.getString("exteraDefaultIcon", R.string.exteraDefaultIcon), R.mipmap.ic_launcher),
                        Triple(1, LocaleController.getString("exteraMaterialYouIcon", R.string.exteraMaterialYouIcon), R.mipmap.ic_launcher_materialyou)
                    )
                }, {
                    return@contractIcons when (ExteraConfig.appIcon) {
                        1 -> LocaleController.getString("exteraMaterialYouIcon", R.string.exteraMaterialYouIcon)
                        else -> LocaleController.getString("exteraDefaultIcon", R.string.exteraDefaultIcon)
                    }
                }) {
                    ExteraConfig.appIcon = it
                    AppIcons.setIcon(it)
                }
            }
        }

        category(LocaleController.getString("exteraGeneral", R.string.exteraGeneral)) {
            switch {
                title = LocaleController.getString("exteraHideAllChats", R.string.exteraHideAllChats)

                contract({
                    return@contract ExteraConfig.hideAllChats
                }) {
                    ExteraConfig.hideAllChats = it
                }
            }
            switch {
                title = LocaleController.getString("exteraChatsOnTitle", R.string.exteraChatsOnTitle)
                summary = LocaleController.getString("exteraRestartRequired", R.string.exteraRestartRequired)

                contract({
                    return@contract ExteraConfig.chatsOnTitle
                }) {
                    ExteraConfig.chatsOnTitle = it
                }
            }
            switch {
                title = LocaleController.getString("exteraSB", R.string.exteraSB)
                summary = LocaleController.getString("exteraSBdesc", R.string.exteraSBdesc)

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
                title = LocaleController.getString("exteraHidePhoneNumber", R.string.exteraHidePhoneNumber)
                summary = LocaleController.getString("exteraRestartRequired", R.string.exteraRestartRequired)

                contract({
                    return@contract ExteraConfig.hidePhoneNumber
                }) {
                    ExteraConfig.hidePhoneNumber = it
                }
            }
            switch {
                title = LocaleController.getString("exteraShowID", R.string.exteraShowID)
                contract({
                    return@contract ExteraConfig.showID
                }) {
                    ExteraConfig.showID = it
                }
            }
        }


        category(LocaleController.getString("exteraFonts", R.string.exteraFonts)) {
            textDetail {
                title = LocaleController.getString("exteraAttention", R.string.exteraAttention)
                detail = LocaleController.getString("exteraFontDesc", R.string.exteraFontDesc)
            }
            switch {
                title = LocaleController.getString("exteraSystemEmoji", R.string.exteraSystemEmoji)

                contract({
                    return@contract SharedConfig.useSystemEmoji
                }) {
                    SharedConfig.toggleSystemEmoji()
                }
            }
            switch {
                title = LocaleController.getString("exteraSystemFont", R.string.exteraSystemFont)

                contract({
                    return@contract ExteraConfig.useSystemFont
                }) {
                    ExteraConfig.useSystemFont = it
                }
            }
        }

        category(LocaleController.getString("exteraDrawer", R.string.exteraDrawer)) {
            textDetail {
                title = LocaleController.getString("exteraAttention", R.string.exteraAttention)
                detail = LocaleController.getString("exteraDrawerRR", R.string.exteraDrawerRR)
            }
            switch {
                title = LocaleController.getString("exteraNewGroup", R.string.exteraNewGroup)

                contract({
                    return@contract ExteraConfig.newGroup
                }) {
                    ExteraConfig.newGroup = it
                }
            }
            switch {
                title = LocaleController.getString("exteraNewSecretChat", R.string.exteraNewSecretChat)

                contract({
                    return@contract ExteraConfig.newSecretChat
                }) {
                    ExteraConfig.newSecretChat = it
                }
            }
            switch {
                title = LocaleController.getString("exteraNewChannel", R.string.exteraNewChannel)

                contract({
                    return@contract ExteraConfig.newChannel
                }) {
                    ExteraConfig.newChannel = it
                }
            }
            switch {
                title = LocaleController.getString("exteraContacts", R.string.exteraContacts)

                contract({
                    return@contract ExteraConfig.Contacts
                }) {
                    ExteraConfig.Contacts = it
                }
            }
            switch {
                title = LocaleController.getString("exteraCalls", R.string.exteraCalls)

                contract({
                    return@contract ExteraConfig.Calls
                }) {
                    ExteraConfig.Calls = it
                }
            }
            switch {
                title = LocaleController.getString("exteraPeopleNearby", R.string.exteraPeopleNearby)
                summary = LocaleController.getString("exteraPeopleNearbyDesc", R.string.exteraPeopleNearbyDesc)

                contract({
                    return@contract ExteraConfig.peopleNearby
                }) {
                    ExteraConfig.peopleNearby = it
                }
            }
            switch {
                title = LocaleController.getString("exteraSavedMessages", R.string.exteraSavedMessages)

                contract({
                    return@contract ExteraConfig.savedMessages
                }) {
                    ExteraConfig.savedMessages = it
                }
            }
            switch {
                title = LocaleController.getString("exteraInviteFriends", R.string.exteraInviteFriends)

                contract({
                    return@contract ExteraConfig.inviteFriends
                }) {
                    ExteraConfig.inviteFriends = it
                }
            }
            switch {
                title = LocaleController.getString("exteraTelegramFeatures", R.string.exteraTelegramFeatures)

                contract({
                    return@contract ExteraConfig.telegramFeatures
                }) {
                    ExteraConfig.telegramFeatures = it
                }
            }
        }
    }
}