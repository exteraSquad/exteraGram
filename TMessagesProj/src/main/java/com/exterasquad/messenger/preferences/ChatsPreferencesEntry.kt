package com.exterasquad.messenger.preferences

import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.BaseFragment
import com.exterasquad.messenger.ExteraConfig
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
                title = LocaleController.getString(
                    "exteraHQVoiceMessage",
                    R.string.exteraHQVoiceMessage
                )
                summary = LocaleController.getString(
                    "exteraHQRestartRequired",
                    R.string.exteraHQRestartRequired
                )

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
    }
}
