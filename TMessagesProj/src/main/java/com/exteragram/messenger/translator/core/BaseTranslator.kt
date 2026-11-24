/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.translator.core

import org.telegram.messenger.LocaleController
import org.telegram.messenger.R

import java.util.Locale

import com.exteragram.messenger.translator.TranslatorUtils

abstract class BaseTranslator {

    abstract val supportedLanguages: Set<String>

    open val displayName: String
        get() = LocaleController.getString(R.string.AutomaticTranslation)

    abstract fun translate(
        text: String,
        fromLang: String,
        toLang: String,
        callback: TranslatorUtils.TranslateCallback
    )

    fun isLanguageSupported(lang: String): Boolean {
        if (supportedLanguages.isEmpty()) {
            return true
        }
        val code = TranslatorUtils.primaryLanguageOf(lang) ?: return false
        return supportedLanguages.any { it.lowercase(Locale.US).substringBefore('-') == code }
    }
}