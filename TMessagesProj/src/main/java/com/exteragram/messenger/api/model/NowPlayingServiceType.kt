/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.api.model

import org.telegram.messenger.LocaleController
import org.telegram.messenger.R

enum class NowPlayingServiceType(val displayName: String) {
    NONE(LocaleController.getString(R.string.None)),
    LAST_FM("Last.fm"),
    STATS_FM("Stats.fm");
}