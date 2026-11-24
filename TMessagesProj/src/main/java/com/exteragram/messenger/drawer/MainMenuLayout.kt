/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

@file:JvmName("MainMenuLayout")

package com.exteragram.messenger.drawer

import com.exteragram.messenger.GSON
import com.exteragram.messenger.config.hidden
import com.exteragram.messenger.editor
import com.exteragram.messenger.plugins.PluginsController
import com.exteragram.messenger.preferences
import com.exteragram.messenger.utils.addIf
import com.google.gson.reflect.TypeToken

enum class MainMenuItem(val id: Int) {
    DIVIDER(-1),
    PROFILE(18),
    ARCHIVE(14),
    BOTS(105),
    NEW_GROUP(2),
    CONTACTS(6),
    NEW_CHANNEL(3),
    CALLS(10),
    SAVED(11),
    SETTINGS(8),
    PLUGINS(102),
    BROWSER(101),
    QR(17),
    FEED(106),
    WALLET(107);

    companion object {
        @JvmStatic fun getById(id: Int): MainMenuItem? = entries.find { it.id == id }
    }
}

var mainMenuLayout = ArrayList<Int>()
var mainMenuHiddenItems = ArrayList<Int>()

internal fun loadMainMenuLayout() {
    val layoutJson = preferences.getString("mainMenuLayout", null)
    val hiddenJson = preferences.getString("mainMenuHiddenItems", null)

    if (layoutJson != null) {
        mainMenuLayout = GSON.fromJson(layoutJson, object : TypeToken<ArrayList<Int>>() {}.type)
        mainMenuHiddenItems = if (hiddenJson != null)
            GSON.fromJson(hiddenJson, object : TypeToken<ArrayList<Int>>() {}.type)
        else ArrayList()
    } else {
        mainMenuLayout = ArrayList()
        mainMenuHiddenItems = ArrayList()

        mainMenuLayout.addAll(getDefaultMainMenuLayout())

        for (item in MainMenuItem.entries) {
            if (item == MainMenuItem.DIVIDER) continue
            if (mainMenuLayout.contains(item.id)) continue
            if (item == MainMenuItem.PLUGINS && !PluginsController.isPluginEngineSupported()) continue
            mainMenuHiddenItems.add(item.id)
        }

        saveMainMenuLayout()
    }

    val pluginsId = MainMenuItem.PLUGINS.id
    if (!PluginsController.isPluginEngineSupported()) {
        mainMenuLayout.remove(pluginsId)
        mainMenuHiddenItems.remove(pluginsId)
    } else if (!mainMenuLayout.contains(pluginsId) && !mainMenuHiddenItems.contains(pluginsId)) {
        mainMenuHiddenItems.add(pluginsId)
    }

    val feedId = MainMenuItem.FEED.id
    if (!mainMenuLayout.contains(feedId) && !mainMenuHiddenItems.contains(feedId)) {
        mainMenuLayout.add(feedId)
        saveMainMenuLayout()
    }

    val walletId = MainMenuItem.WALLET.id
    if (!mainMenuLayout.contains(walletId) && !mainMenuHiddenItems.contains(walletId)) {
        val botsIndex = mainMenuLayout.indexOf(MainMenuItem.BOTS.id)
        mainMenuLayout.add(if (botsIndex >= 0) botsIndex else mainMenuLayout.size, walletId)
        saveMainMenuLayout()
    }

    mainMenuLayout.removeAll(mainMenuHiddenItems.toSet())
    ensureSettingsVisibility()
    sanitizeMenu()
}

fun saveMainMenuLayout() {
    editor.putString("mainMenuLayout", GSON.toJson(mainMenuLayout))
        .putString("mainMenuHiddenItems", GSON.toJson(mainMenuHiddenItems))
        .apply()
}

fun getDefaultMainMenuLayout(): ArrayList<Int> = ArrayList<Int>().apply {
    add(MainMenuItem.ARCHIVE.id)
    addIf(hidden(), MainMenuItem.PROFILE.id)
    add(MainMenuItem.NEW_GROUP.id)
    addIf(hidden(), MainMenuItem.CONTACTS.id)
    add(MainMenuItem.SAVED.id)
    add(MainMenuItem.FEED.id)
    add(MainMenuItem.WALLET.id)
    add(MainMenuItem.BOTS.id)
    addIf(hidden(), MainMenuItem.SETTINGS.id)
}

fun ensureSettingsVisibility() {
    val settingsId = MainMenuItem.SETTINGS.id
    if (!hidden()) return
    if (mainMenuLayout.contains(settingsId)) return

    mainMenuHiddenItems.remove(settingsId)
    mainMenuLayout.add(settingsId)
    saveMainMenuLayout()
}

private fun sanitizeMenu() {
    var changed = mainMenuLayout.removeIf { id -> id != MainMenuItem.DIVIDER.id && MainMenuItem.getById(id) == null }
    changed = changed or mainMenuHiddenItems.removeIf { id -> id != MainMenuItem.DIVIDER.id && MainMenuItem.getById(id) == null }

    for (item in MainMenuItem.entries) {
        if (item == MainMenuItem.DIVIDER) continue
        if (item == MainMenuItem.PLUGINS && !PluginsController.isPluginEngineSupported()) continue
        if (!mainMenuLayout.contains(item.id) && !mainMenuHiddenItems.contains(item.id)) {
            mainMenuHiddenItems.add(item.id)
            changed = true
        }
    }

    if (changed) saveMainMenuLayout()
}
