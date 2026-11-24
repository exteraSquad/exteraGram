/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

@file:JvmName("IconPacksLayout")

package com.exteragram.messenger.icons

import com.exteragram.messenger.GSON
import com.exteragram.messenger.IconPackType
import com.exteragram.messenger.editor
import com.exteragram.messenger.iconPack
import com.exteragram.messenger.preferences
import com.google.gson.reflect.TypeToken

private val BASE_PACK_IDS = arrayOf(IconPack.ID_DEFAULT, IconPack.ID_SOLAR, IconPack.ID_REMIX)

private fun IconPackType.basePackId(): String = when (this) {
    IconPackType.DEFAULT -> IconPack.ID_DEFAULT
    IconPackType.SOLAR -> IconPack.ID_SOLAR
    IconPackType.REMIX -> IconPack.ID_REMIX
}

var iconPacksLayout = ArrayList<String>()
var iconPacksHidden = ArrayList<String>()

internal fun loadIconPacksLayout() {
    val layoutJson = preferences.getString("iconPacksLayout", null)
    val hiddenJson = preferences.getString("iconPacksHidden", null)
    if (layoutJson != null) {
        iconPacksLayout = GSON.fromJson(layoutJson, object : TypeToken<ArrayList<String>>() {}.type)
        iconPacksHidden = if (hiddenJson != null)
            GSON.fromJson(hiddenJson, object : TypeToken<ArrayList<String>>() {}.type)
        else ArrayList()

        // todo: remove after 2-3 updates
        var changedLoad = false
        var firstBaseId: String? = null
        val iterator = iconPacksLayout.iterator()
        while (iterator.hasNext()) {
            val id = iterator.next()
            if (id.startsWith(IconPack.PREFIX_BASE)) {
                if (firstBaseId == null) {
                    firstBaseId = id
                } else {
                    iterator.remove()
                    changedLoad = true
                }
            }
        }
        if (firstBaseId == null) {
            iconPacksLayout.add(IconPack.ID_DEFAULT)
            changedLoad = true
        }
        if (changedLoad) saveIconPacksLayout()
    } else {
        iconPacksLayout = ArrayList()
        iconPacksHidden = ArrayList()
        val activeBaseId = iconPack.basePackId()
        for (pack in BASE_PACK_IDS) {
            if (pack == activeBaseId) iconPacksLayout.add(pack) else iconPacksHidden.add(pack)
        }
        saveIconPacksLayout()
    }

    var changed = false
    for (baseId in BASE_PACK_IDS) {
        if (baseId !in iconPacksLayout && baseId !in iconPacksHidden) {
            iconPacksHidden.add(baseId)
            changed = true
        }
    }
    if (changed) saveIconPacksLayout()
}

fun saveIconPacksLayout() {
    iconPack = when (iconPacksLayout.firstOrNull { it.startsWith(IconPack.PREFIX_BASE) }) {
        IconPack.ID_SOLAR -> IconPackType.SOLAR
        IconPack.ID_REMIX -> IconPackType.REMIX
        else -> IconPackType.DEFAULT
    }
    editor.putString("iconPacksLayout", GSON.toJson(iconPacksLayout))
        .putString("iconPacksHidden", GSON.toJson(iconPacksHidden))
        .apply()
}
