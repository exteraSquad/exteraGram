package com.exteragram.messenger.preferences

import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.BaseFragment
import com.exteragram.messenger.ExteraConfig
import ua.itaysonlab.tgkit.ktx.*
import ua.itaysonlab.tgkit.preference.types.TGKitSliderPreference.TGSLContract

class ChatsPreferencesEntry : BasePreferencesEntry {
    override fun getPreferences(bf: BaseFragment) = tgKitScreen(LocaleController.getString("Chats", R.string.Chats)) {
        category(LocaleController.getString("StickerSize", R.string.StickerSize)) {
            slider {
                contract = object : TGSLContract {
                    override fun setValue(value: Int) {
                        ExteraConfig.stickerSize = value
                    }

                    override fun getPreferenceValue(): Int {
                        return ExteraConfig.stickerSize
                    }

                    override fun getMin(): Int {
                        return 50
                    }

                    override fun getMax(): Int {
                        return 100
                    }
                }
            }
            switch {
                title = LocaleController.getString("StickerTime", R.string.StickerTime)

                contract({
                    return@contract ExteraConfig.hideStickerTime
                }) {
                    ExteraConfig.hideStickerTime = it
                }
            }
        }
        category(LocaleController.getString("General", R.string.General)) {
            switch {
                title = LocaleController.getString("HideSendAsChannel", R.string.HideSendAsChannel)

                contract({
                    return@contract ExteraConfig.hideSendAsChannel
                }) {
                    ExteraConfig.hideSendAsChannel = it
                }
            }
            switch {
                title = LocaleController.getString("HideKeyboardOnScroll", R.string.HideKeyboardOnScroll)

                contract({
                    return@contract ExteraConfig.hideKeyboardOnScroll
                }) {
                    ExteraConfig.hideKeyboardOnScroll = it
                }
            }
            switch {
                title = LocaleController.getString("DateOfForwardedMsg", R.string.DateOfForwardedMsg)

                contract({
                    return@contract ExteraConfig.dateOfForwardedMsg
                }) {
                    ExteraConfig.dateOfForwardedMsg = it
                }
            }
            switch {
                title = LocaleController.getString("ShowSeconds", R.string.ShowSeconds)

                contract({
                    return@contract ExteraConfig.showSeconds
                }) {
                    ExteraConfig.showSeconds = it
                }
            }
            switch {
                title = LocaleController.getString("ArchiveOnPull", R.string.ArchiveOnPull)

                contract({
                    return@contract ExteraConfig.archiveOnPull
                }) {
                    ExteraConfig.archiveOnPull = it
                }
            }
            switch {
                title = LocaleController.getString("IncludeArchivedChatsInForwards", R.string.IncludeArchivedChatsInForwards)

                contract({
                    return@contract ExteraConfig.includeArchivedChatsInForwards
                }) {
                    ExteraConfig.includeArchivedChatsInForwards = it
                }
            }
            switch {
                title = LocaleController.getString("UnlimitedPinnedChats", R.string.UnlimitedPinnedChats)
                summary = LocaleController.getString("UnlimitedPinnedChatsDescription", R.string.UnlimitedPinnedChatsDescription)

                contract({
                    return@contract ExteraConfig.unlimitedPinnedChats
                }) {
                    ExteraConfig.unlimitedPinnedChats = it
                }
            }
            switch {
                title = LocaleController.getString("UnlimitedRecentStickers", R.string.UnlimitedRecentStickers)

                contract({
                    return@contract ExteraConfig.unlimitedRecentStickers
                }) {
                    ExteraConfig.unlimitedRecentStickers = it
                }
            }
        }
        category(LocaleController.getString("Media", R.string.Media)) {
            switch {
                title = LocaleController.getString("HQVoiceMessage", R.string.HQVoiceMessage)
                summary = LocaleController.getString("RestartRequired", R.string.RestartRequired)

                contract({
                    return@contract ExteraConfig.HQVoiceMessage
                }) {
                    ExteraConfig.HQVoiceMessage = it
                }
            }
            switch {
                title = LocaleController.getString("RearVideoMessages", R.string.RearVideoMessages)

                contract({
                    return@contract ExteraConfig.rearVideoMessages
                }) {
                    ExteraConfig.rearVideoMessages = it
                }
            }
            switch {
                title = LocaleController.getString("Autopause", R.string.Autopause)
                summary = LocaleController.getString("AutopauseDescription", R.string.AutopauseDescription)

                contract({
                    return@contract ExteraConfig.autopause
                }) {
                    ExteraConfig.autopause = it
                }
            }
            switch {
                title = LocaleController.getString("DisablePlayback", R.string.DisablePlayback)
                summary = LocaleController.getString("DisablePlaybackDescription", R.string.DisablePlaybackDescription)

                contract({
                    return@contract ExteraConfig.disablePlayback
                }) {
                    ExteraConfig.disablePlayback = it
                }
            }
        }
    }
}