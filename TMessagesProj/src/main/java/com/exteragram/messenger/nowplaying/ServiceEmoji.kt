/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.nowplaying

enum class ServiceEmoji(val documentId: Long) {
    MUSIC(5271627010681108586L),
    SPOTIFY(5271857023359681001L),
    TELEGRAM(5325674462522144646L);

    companion object {
        fun fromString(name: String?): ServiceEmoji {
            return entries.find { it.name.equals(name, ignoreCase = true) }
                ?: MUSIC
        }
    }
}