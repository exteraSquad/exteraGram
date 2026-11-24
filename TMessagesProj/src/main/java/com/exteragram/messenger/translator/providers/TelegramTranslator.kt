/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.translator.providers

import com.exteragram.messenger.translator.TranslatorUtils
import com.exteragram.messenger.translator.core.BaseTranslator

class TelegramTranslator private constructor() : BaseTranslator() {

    override val displayName = "Telegram"

    override val supportedLanguages: Set<String> = emptySet()

    override fun translate(
        text: String,
        fromLang: String,
        toLang: String,
        callback: TranslatorUtils.TranslateCallback
    ) {
        TranslatorUtils.translateWithDefault(text, null, 0, toLang, null, callback)
    }

    companion object {
        private val shared by lazy { TelegramTranslator() }

        @JvmStatic
        fun getInstance() = shared
    }
}