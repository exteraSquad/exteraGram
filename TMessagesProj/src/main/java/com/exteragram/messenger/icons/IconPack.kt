/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.icons

import android.content.Context
import android.util.SparseIntArray
import java.io.File

data class IconPack(
    val id: String,
    val name: String,
    val author: String,
    val version: String = "1.0",
    val icons: Map<String, String> = emptyMap(),
    val preinstalledMap: SparseIntArray? = null,
    val location: File? = null
) {
    val isBase: Boolean get() = id.startsWith(PREFIX_BASE)

    fun hasIcon(resId: Int, context: Context): Boolean {
        if (preinstalledMap != null) {
            return preinstalledMap.get(resId, -1) != -1
        }
        val resourceName = try {
            context.resources.getResourceEntryName(resId)
        } catch (_: Exception) {
            return false
        }
        return icons.containsKey(resourceName)
    }

    companion object {
        const val PREFIX_BASE = "base."
        const val ID_DEFAULT = "base.default"
        const val ID_SOLAR = "base.solar"
        const val ID_REMIX = "base.remix"
    }
}
