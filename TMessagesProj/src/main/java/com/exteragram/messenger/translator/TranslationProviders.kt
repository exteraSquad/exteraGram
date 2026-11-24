/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.translator

import org.telegram.messenger.MessagesController
import org.telegram.messenger.UserConfig

import com.exteragram.messenger.translationProvider
import com.exteragram.messenger.translator.core.BaseTranslator
import com.exteragram.messenger.translator.providers.GoogleTranslator
import com.exteragram.messenger.translator.providers.MicrosoftTranslator
import com.exteragram.messenger.translator.providers.TelegramTranslator
import com.exteragram.messenger.translator.providers.YandexTranslator

object TranslationProviders {

    @JvmField
    val ALL: List<BaseTranslator> = listOf(
        TelegramTranslator.getInstance(),
        GoogleTranslator.getInstance(),
        YandexTranslator.getInstance(),
        MicrosoftTranslator.getInstance()
    )

    @JvmStatic
    val lastIndex: Int
        get() = ALL.size - 1

    @JvmStatic
    fun names(): Array<CharSequence> = Array(ALL.size) { ALL[it].displayName }

    @JvmStatic
    fun current(): BaseTranslator = ALL.getOrElse(translationProvider) { ALL[1] }

    @JvmStatic
    fun isTelegram(): Boolean = current() is TelegramTranslator

    @JvmStatic
    fun isAlternative(): Boolean = !isTelegram()

    @JvmStatic
    fun isChatTranslationUnlocked(currentAccount: Int, dialogId: Long): Boolean {
        if (isChatTranslationUnlocked(currentAccount)) {
            return true
        }
        val chat = MessagesController.getInstance(currentAccount).getChat(-dialogId)
        return chat != null && chat.autotranslation
    }

    @JvmStatic
    fun isChatTranslationUnlocked(currentAccount: Int): Boolean =
        isAlternative() || UserConfig.getInstance(currentAccount).isPremium
}