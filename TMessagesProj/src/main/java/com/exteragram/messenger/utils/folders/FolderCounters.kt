/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.folders

import androidx.collection.LongSparseArray
import com.exteragram.messenger.TabCounterMode
import com.exteragram.messenger.tabCounterMode
import org.telegram.SQLite.SQLiteCursor
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.FileLog
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesController.DialogFilter
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.UserConfig
import org.telegram.messenger.support.LongSparseIntArray
import org.telegram.tgnet.TLRPC

class FolderCounters private constructor(private val account: Int) {

    private class UnreadDialog(
        val id: Long,
        val typeFlags: Int,
        val weight: Int,
        val archived: Boolean,
        val muted: Boolean,
        val chat: TLRPC.Chat?
    )

    private var mainCount = 0
    private var filterCounts = HashMap<Int, Int>()
    @Volatile
    private var recountScheduled = false

    fun getMainUnreadCount(): Int =
        if (isUnmutedOnly()) mainCount else MessagesStorage.getInstance(account).mainUnreadCount

    fun getUnreadCount(filter: DialogFilter): Int =
        if (isUnmutedOnly() || FolderRoles.getInstance(account).getMode(filter.id) != FolderRoles.MODE_NONE) filterCounts[filter.id] ?: 0 else filter.unreadCount

    fun update(
        filters: List<DialogFilter>,
        usersDict: LongSparseArray<TLRPC.User>,
        encUsersDict: LongSparseArray<TLRPC.User>,
        encryptedChatsByUsersCount: LongSparseIntArray,
        chatsDict: LongSparseArray<TLRPC.Chat>,
        mutedDialogs: LongSparseArray<Boolean>,
        archivedDialogs: LongSparseArray<Boolean>,
        dialogsWithMentions: LongSparseArray<Int>
    ) {
        val unmutedOnly = isUnmutedOnly()
        val roles = FolderRoles.getInstance(account)
        if (!unmutedOnly && !roles.hasAny()) return
        val forumMutes = if (unmutedOnly) loadForumMutes() else HashMap()
        val dialogs = ArrayList<UnreadDialog>()
        for (i in 0 until usersDict.size()) {
            val user = usersDict.valueAt(i)
            val typeFlags = when {
                ChatObject.isUserCollapsedInCommunity(chatsDict, user) -> MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS
                user.bot -> MessagesController.DIALOG_FILTER_FLAG_BOTS
                user.self || user.contact -> MessagesController.DIALOG_FILTER_FLAG_CONTACTS
                else -> MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS
            }
            val muted = forumMutes[user.id] ?: mutedDialogs.containsKey(user.id)
            dialogs += UnreadDialog(user.id, typeFlags, 1, archivedDialogs.containsKey(user.id), muted, null)
        }
        for (i in 0 until encUsersDict.size()) {
            val user = encUsersDict.valueAt(i)
            val count = encryptedChatsByUsersCount.get(user.id, 0)
            if (count == 0) continue
            val typeFlags = if (user.self || user.contact) MessagesController.DIALOG_FILTER_FLAG_CONTACTS else MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS
            dialogs += UnreadDialog(user.id, typeFlags, count, archivedDialogs.containsKey(user.id), mutedDialogs.containsKey(user.id), null)
        }
        for (i in 0 until chatsDict.size()) {
            val chat = chatsDict.valueAt(i) ?: continue
            if (ChatObject.isCommunity(chat)) continue
            val id = -chat.id
            val typeFlags = when {
                ChatObject.isChatCollapsedInCommunity(chatsDict, chat) -> MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS
                ChatObject.isChannel(chat) && !chat.megagroup -> MessagesController.DIALOG_FILTER_FLAG_CHANNELS
                else -> MessagesController.DIALOG_FILTER_FLAG_GROUPS
            }
            val muted = forumMutes[id] ?: (mutedDialogs.containsKey(id) && !dialogsWithMentions.containsKey(id))
            dialogs += UnreadDialog(id, typeFlags, 1, archivedDialogs.containsKey(id), muted, chat)
        }

        val main = if (unmutedOnly) count(dialogs, MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS or MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED, null, true) else 0
        val counts = HashMap<Int, Int>()
        for (filter in filters) {
            if (unmutedOnly || roles.getMode(filter.id) != FolderRoles.MODE_NONE) {
                counts[filter.id] = count(dialogs, filter.flags, filter, unmutedOnly)
            }
        }
        AndroidUtilities.runOnUIThread {
            mainCount = main
            filterCounts = counts
        }
    }

    fun scheduleRecount() {
        if (!isUnmutedOnly() && !FolderRoles.getInstance(account).hasAny() || recountScheduled) return
        recountScheduled = true
        val storage = MessagesStorage.getInstance(account)
        storage.storageQueue.postRunnable({
            recountScheduled = false
            storage.resetAllUnreadCounters(false)
        }, RECOUNT_DELAY_MS)
    }

    private fun loadForumMutes(): HashMap<Long, Boolean> {
        val storage = MessagesStorage.getInstance(account)
        val messagesController = MessagesController.getInstance(account)
        val result = HashMap<Long, Boolean>()
        var cursor: SQLiteCursor? = null
        try {
            cursor = storage.database.queryFinalized("SELECT did, topic_id, unread_mentions FROM topics WHERE unread_count > 0 OR unread_mentions > 0")
            while (cursor.next()) {
                val did = cursor.longValue(0)
                if (result[did] == false || !storage.isForum(did, FORUM_TYPES)) continue
                result[did] = cursor.intValue(2) == 0 && messagesController.isDialogMuted(did, cursor.longValue(1))
            }
        } catch (e: Exception) {
            FileLog.e(e)
        } finally {
            cursor?.dispose()
        }
        return result
    }

    private fun count(dialogs: List<UnreadDialog>, flags: Int, filter: DialogFilter?, unmutedOnly: Boolean): Int {
        val alwaysShow = filter?.alwaysShow?.toHashSet()
        val neverShow = filter?.neverShow?.toHashSet()
        val roles = FolderRoles.getInstance(account)
        var result = 0
        for (dialog in dialogs) {
            if (unmutedOnly && dialog.muted) continue
            val included = when {
                neverShow?.contains(dialog.id) == true -> false
                alwaysShow?.contains(dialog.id) == true -> true
                dialog.archived && flags and MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_ARCHIVED != 0 -> false
                dialog.muted && flags and MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED != 0 -> false
                filter != null && dialog.chat != null && !roles.allows(filter.id, dialog.chat) -> false
                else -> flags and dialog.typeFlags == dialog.typeFlags
            }
            if (included) {
                result += dialog.weight
            }
        }
        return result
    }

    companion object {
        private const val RECOUNT_DELAY_MS = 500L
        private const val FORUM_TYPES = MessagesStorage.FORUM_TYPE_CHAT or MessagesStorage.FORUM_TYPE_BOT or MessagesStorage.FORUM_TYPE_DIRECT

        private val instances = arrayOfNulls<FolderCounters>(UserConfig.MAX_ACCOUNT_COUNT)
        private val lockObjects = Array(UserConfig.MAX_ACCOUNT_COUNT) { Any() }

        @JvmStatic
        fun getInstance(num: Int): FolderCounters {
            var localInstance = instances[num]
            if (localInstance == null) {
                synchronized(lockObjects[num]) {
                    localInstance = instances[num]
                    if (localInstance == null) {
                        localInstance = FolderCounters(num)
                        instances[num] = localInstance
                    }
                }
            }
            return localInstance!!
        }

        @JvmStatic
        fun recountAll() {
            if (!isUnmutedOnly()) return
            for (account in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
                if (!UserConfig.getInstance(account).isClientActivated) continue
                val storage = MessagesStorage.getInstance(account)
                storage.storageQueue.postRunnable { storage.resetAllUnreadCounters(false) }
            }
        }

        private fun isUnmutedOnly(): Boolean = tabCounterMode == TabCounterMode.UNMUTED
    }
}
