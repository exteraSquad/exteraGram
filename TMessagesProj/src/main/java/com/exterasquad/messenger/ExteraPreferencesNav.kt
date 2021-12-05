package com.exterasquad.messenger

import com.exterasquad.messenger.preferences.*
import ua.itaysonlab.tgkit.TGKitSettingsFragment

object ExteraPreferencesNav {
    @JvmStatic
    fun createMainMenu() = TGKitSettingsFragment(MainPreferencesEntry())

    fun createAppearance() = TGKitSettingsFragment(AppearancePreferencesEntry())
    fun createChats() = TGKitSettingsFragment(ChatsPreferencesEntry())
    fun createSecurity() = TGKitSettingsFragment(SecurityPreferencesEntry())
}