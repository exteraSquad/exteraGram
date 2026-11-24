/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.folders

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.util.Pair
import com.exteragram.messenger.TabIconsMode
import com.exteragram.messenger.icons.IconManager
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.GroupCreateUserCell
import org.telegram.ui.LaunchActivity
import java.util.Objects

object FolderUiHelper {

    const val PICKER_ROWS = 3

    @JvmField
    val folderIcons = LinkedHashMap<String, Int>().apply {
        put("\uD83D\uDC31", R.drawable.filter_cat)
        put("\uD83D\uDCD5", R.drawable.filter_book)
        put("\uD83D\uDCB0", R.drawable.filter_money)
        put("\uD83C\uDFAE", R.drawable.filter_game)
        put("\uD83D\uDCA1", R.drawable.filter_light)
        put("\uD83D\uDC4C", R.drawable.filter_like)
        put("\uD83C\uDFB5", R.drawable.filter_note)
        put("\uD83C\uDFA8", R.drawable.filter_palette)
        put("\u2708", R.drawable.filter_travel)
        put("\u26BD", R.drawable.filter_sport)
        put("\u2B50", R.drawable.filter_favorite)
        put("\uD83C\uDF93", R.drawable.filter_study)
        put("\uD83D\uDEEB", R.drawable.filter_airplane)
        put("\uD83D\uDC64", R.drawable.filter_private)
        put("\uD83D\uDC65", R.drawable.filter_group)
        put("\uD83D\uDCAC", R.drawable.filter_all)
        put("\u2705", R.drawable.filter_unread)
        put("\uD83E\uDD16", R.drawable.filter_bots)
        put("\uD83D\uDC51", R.drawable.filter_crown)
        put("\uD83C\uDF39", R.drawable.filter_flower)
        put("\uD83C\uDFE0", R.drawable.filter_home)
        put("\u2764", R.drawable.filter_love)
        put("\uD83C\uDFAD", R.drawable.filter_mask)
        put("\uD83C\uDF78", R.drawable.filter_party)
        put("\uD83D\uDCC8", R.drawable.filter_trade)
        put("\uD83D\uDCBC", R.drawable.filter_work)
        put("\uD83D\uDD14", R.drawable.filter_unmuted)
        put("\uD83D\uDCE2", R.drawable.filter_channels)
        put("\uD83D\uDCC1", R.drawable.filter_custom)
        put("\uD83D\uDCCB", R.drawable.filter_setup)
    }

    private var ownerIcon: Drawable? = null
    private var adminIcon: Drawable? = null
    private var iconKey = 0

    @JvmStatic
    fun getEmoticonFromFlags(newFilterFlags: Int): Pair<String, String> {
        var flags = newFilterFlags and MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS
        var newName = ""
        var newEmoticon = ""
        if ((flags and MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS) == MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS) {
            if ((newFilterFlags and MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ) != 0) {
                newName = getString(R.string.FilterNameUnread)
                newEmoticon = "\u2705"
            } else if ((newFilterFlags and MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) != 0) {
                newName = getString(R.string.FilterNameNonMuted)
                newEmoticon = "\uD83D\uDD14"
            }
        } else if ((flags and MessagesController.DIALOG_FILTER_FLAG_CONTACTS) != 0) {
            flags = flags and MessagesController.DIALOG_FILTER_FLAG_CONTACTS.inv()
            if (flags == 0) {
                newName = getString(R.string.FilterContacts)
                newEmoticon = "\uD83D\uDC64"
            } else if ((flags and MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS) != 0) {
                flags = flags and MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS.inv()
                if (flags == 0) {
                    newName = getString(R.string.FilterContacts)
                    newEmoticon = "\uD83D\uDC64"
                }
            }
        } else if ((flags and MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS) != 0) {
            flags = flags and MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS.inv()
            if (flags == 0) {
                newName = getString(R.string.FilterNonContacts)
                newEmoticon = "\uD83D\uDC64"
            }
        } else if ((flags and MessagesController.DIALOG_FILTER_FLAG_GROUPS) != 0) {
            flags = flags and MessagesController.DIALOG_FILTER_FLAG_GROUPS.inv()
            if (flags == 0) {
                newName = getString(R.string.FilterGroups)
                newEmoticon = "\uD83D\uDC65"
            }
        } else if ((flags and MessagesController.DIALOG_FILTER_FLAG_BOTS) != 0) {
            flags = flags and MessagesController.DIALOG_FILTER_FLAG_BOTS.inv()
            if (flags == 0) {
                newName = getString(R.string.FilterBots)
                newEmoticon = "\uD83E\uDD16"
            }
        } else if ((flags and MessagesController.DIALOG_FILTER_FLAG_CHANNELS) != 0) {
            flags = flags and MessagesController.DIALOG_FILTER_FLAG_CHANNELS.inv()
            if (flags == 0) {
                newName = getString(R.string.FilterChannels)
                newEmoticon = "\uD83D\uDCE2"
            }
        }
        return Pair.create(newName, newEmoticon)
    }

    @JvmStatic
    fun getIconWidth(): Int = AndroidUtilities.dp(24f)

    @JvmStatic
    fun getTotalIconWidth(mode: TabIconsMode): Int =
        if (mode != TabIconsMode.TITLES_ONLY) getIconWidth() + getPadding(mode) else 0

    @JvmStatic
    fun getTabIcon(emoji: String?): Int = emoji?.let { folderIcons[it] } ?: R.drawable.filter_custom

    @JvmStatic
    fun getPaddingTab(): Int = AndroidUtilities.dp(24f)

    private fun getPadding(mode: TabIconsMode): Int =
        if (mode == TabIconsMode.ICONS_AND_TITLES) AndroidUtilities.dp(4f) else 0

    @JvmStatic
    fun getAvatarIcon(owner: Boolean): Drawable? {
        val context = LaunchActivity.instance ?: ApplicationLoader.applicationContext
        val color = Theme.getColor(Theme.key_avatar_text)
        val key = Objects.hash(color, System.identityHashCode(context.resources), IconManager.getIcon(R.drawable.filled_owner), IconManager.getIcon(R.drawable.filled_admins))
        if (ownerIcon == null || adminIcon == null || iconKey != key) {
            ownerIcon = ContextCompat.getDrawable(context, R.drawable.filled_owner)?.mutate()
            adminIcon = ContextCompat.getDrawable(context, R.drawable.filled_admins)?.mutate()
            val filter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            ownerIcon?.colorFilter = filter
            adminIcon?.colorFilter = filter
            iconKey = key
        }
        return if (owner) ownerIcon else adminIcon
    }

    @JvmStatic
    fun getRoleName(chatType: String): String =
        getString(if (FolderRoles.modeOf(chatType) == FolderRoles.MODE_OWNER) R.string.ChannelCreator else R.string.FolderRolesAdmin)

    @JvmStatic
    fun getChipText(mode: Int): String =
        getString(if (mode == FolderRoles.MODE_OWNER) R.string.FolderRolesOwnerOnly else R.string.FolderRolesAdminOnly)

    @JvmStatic
    fun getPickerHeader(): String = getString(R.string.FolderRolesHeader)

    @JvmStatic
    fun bindPickerRow(cell: GroupCreateUserCell, row: Int, pickerFlags: Int) {
        val mode = if (row == 0) FolderRoles.MODE_OWNER else FolderRoles.MODE_ADMIN
        val chatType = FolderRoles.chatTypeOf(mode)
        cell.alpha = if (FolderRoles.supportsFlags(pickerFlags)) 1f else 0.5f
        cell.setObject(chatType, getRoleName(chatType!!), null)
        cell.setChecked(FolderRoles.fromPickerFlags(pickerFlags) == mode, false)
        cell.setCheckBoxEnabled(true)
    }
}
