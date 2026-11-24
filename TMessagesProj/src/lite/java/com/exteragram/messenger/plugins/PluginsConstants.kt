/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins

class PluginsConstants private constructor() {
    companion object {
        const val APP_PAUSE = "app_pause"
        const val APP_STOP = "app_stop"
        const val APP_RESUME = "app_resume"
        const val APP_START = "app_start"

        const val PLUGINS_EXT = ".plugin"
    }

    class MenuItemTypes private constructor() {
        companion object {
            const val CHAT_ACTION_MENU = "chat_action_menu"
            const val MESSAGE_CONTEXT_MENU = "message_context_menu"
            const val PROFILE_ACTION_MENU = "profile_action_menu"
            const val DRAWER_MENU = "drawer_menu"
            const val MAIN_MENU = "main_menu"
        }
    }
}