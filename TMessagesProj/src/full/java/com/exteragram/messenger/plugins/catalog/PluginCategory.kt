/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.catalog

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme

enum class PluginCategory(val id: String, @param:StringRes val titleRes: Int, @param:DrawableRes val iconRes: Int, val colorKey: Int) {
    APPEARANCE("appearance", R.string.PluginCategoryAppearance, R.drawable.filter_palette, Theme.key_avatar_nameInMessageViolet),
    CHATS("chats", R.string.PluginCategoryChats, R.drawable.filter_all, Theme.key_avatar_nameInMessageBlue),
    MEDIA("media", R.string.PluginCategoryMedia, R.drawable.filter_media, Theme.key_avatar_nameInMessageOrange),
    PRIVACY("privacy", R.string.PluginCategoryPrivacy, R.drawable.filter_lock, Theme.key_avatar_nameInMessageGreen),
    TOOLS("tools", R.string.PluginCategoryTools, R.drawable.filter_work, Theme.key_avatar_nameInMessageRed),
    INTEGRATIONS("integrations", R.string.PluginCategoryIntegrations, R.drawable.filter_bots, Theme.key_avatar_nameInMessageCyan),
    FUN("fun", R.string.PluginCategoryFun, R.drawable.filter_game, Theme.key_avatar_nameInMessagePink),
    LIBRARIES("libraries", R.string.PluginCategoryLibraries, R.drawable.filter_book, Theme.key_windowBackgroundWhiteGrayText);

    companion object {
        private const val MAX_CATEGORIES = 3

        fun parse(value: String?): List<PluginCategory> {
            if (value.isNullOrBlank()) {
                return emptyList()
            }
            return value.split(',')
                .mapNotNull { id -> entries.firstOrNull { it.id == id.trim().lowercase() } }
                .distinct()
                .take(MAX_CATEGORIES)
        }
    }
}
