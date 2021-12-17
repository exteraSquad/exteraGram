package com.exteragram.messenger.preferences

import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.BaseFragment
import com.exteragram.messenger.ExteraConfig
import ua.itaysonlab.tgkit.ktx.*
import ua.itaysonlab.tgkit.preference.types.TGKitSliderPreference.TGSLContract

class ChatsPreferencesEntry : BasePreferencesEntry {
    override fun getPreferences(bf: BaseFragment) = tgKitScreen(LocaleController.getString("exteraChats", R.string.exteraChats)) {
        category(LocaleController.getString("exteraStickerSize", R.string.exteraStickerSize)) {
            slider {
                contract = object : TGSLContract {
                    override fun setValue(value: Int) {
                        ExteraConfig.StickerSize = value
                    }

                    override fun getPreferenceValue(): Int {
                        return ExteraConfig.StickerSize
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
                title = LocaleController.getString("exteraStickerTime", R.string.exteraStickerTime)

                contract({
                    return@contract ExteraConfig.hideStickerTime
                }) {
                    ExteraConfig.hideStickerTime = it
                }
            }
        }


        category(LocaleController.getString("exteraMedia", R.string.exteraMedia)) {
            switch {
                title = LocaleController.getString("exteraHQVoiceMessage", R.string.exteraHQVoiceMessage)
                summary = LocaleController.getString("exteraRestartRequired", R.string.exteraRestartRequired)

                contract({
                    return@contract ExteraConfig.HQVoiceMessage
                }) {
                    ExteraConfig.HQVoiceMessage = it
                }
            }
            switch {
                title = LocaleController.getString(
                    "exteraRearVideoMessages",
                    R.string.exteraRearVideoMessages
                )

                contract({
                    return@contract ExteraConfig.rearVideoMessages
                }) {
                    ExteraConfig.rearVideoMessages = it
                }
            }
        }
        category(LocaleController.getString("exteraChats", R.string.exteraChats)) {
            switch {
                title = LocaleController.getString("exteraArchiveOnPull", R.string.exteraArchiveOnPull)

                contract({
                    return@contract ExteraConfig.archiveOnPull
                }) {
                    ExteraConfig.archiveOnPull = it
                }
            }
            switch {
                title = LocaleController.getString("exteraIncludeArchivedChatsInForwards", R.string.exteraIncludeArchivedChatsInForwards)
                summary = LocaleController.getString("exteraRestartRequired", R.string.exteraRestartRequired)

                contract({
                    return@contract ExteraConfig.includeArchivedChatsInForwards
                }) {
                    ExteraConfig.includeArchivedChatsInForwards = it
                }
            }
            switch {
                title = LocaleController.getString("exteraHideKeyboardOnScroll", R.string.exteraHideKeyboardOnScroll)

                contract({
                    return@contract ExteraConfig.hideKeyboardOnScroll
                }) {
                    ExteraConfig.hideKeyboardOnScroll = it
                }
            }
        }
    }
}
