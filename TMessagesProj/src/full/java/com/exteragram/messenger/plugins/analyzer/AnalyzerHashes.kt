/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.analyzer

import android.content.Context
import java.io.IOException

internal fun analyzerBuildId(context: Context): String? = try {
    context.assets.open("plugin_analyzer/build_id").use { it.readBytes() }.toString(Charsets.UTF_8).trim().ifEmpty { null }
} catch (_: IOException) {
    null
}
