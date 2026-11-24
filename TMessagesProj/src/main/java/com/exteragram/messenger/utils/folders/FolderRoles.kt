/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.folders

import android.content.Context
import android.content.SharedPreferences
import com.exteragram.messenger.utils.network.BotCloudStorage
import org.json.JSONObject
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.ChatObject
import org.telegram.messenger.FileLog
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
import androidx.core.content.edit

class FolderRoles private constructor(private val account: Int) {

    private class Snapshot(val modes: Map<Int, Int>, val updatedAt: Long)

    @Volatile
    private var snapshot: Snapshot
    private var syncing = false
    private var synced = false

    private val preferences: SharedPreferences
        get() = ApplicationLoader.applicationContext.getSharedPreferences("folder_roles$account", Context.MODE_PRIVATE)

    init {
        snapshot = decode(preferences.getString(KEY, null)) ?: EMPTY
    }

    fun getMode(filterId: Int): Int = snapshot.modes[filterId] ?: MODE_NONE

    fun hasAny(): Boolean = snapshot.modes.isNotEmpty()

    fun allows(filterId: Int, chat: TLRPC.Chat): Boolean = when (getMode(filterId)) {
        MODE_OWNER -> chat.creator
        MODE_ADMIN -> ChatObject.hasAdminRights(chat)
        else -> true
    }

    fun setMode(filterId: Int, filterFlags: Int, requestedMode: Int) {
        val mode = effectiveMode(filterFlags, requestedMode)
        if (getMode(filterId) == mode) return
        val modes = pruned(snapshot.modes, filterId)
        if (mode == MODE_NONE) {
            modes.remove(filterId)
        } else {
            modes[filterId] = mode
        }
        commit(Snapshot(modes, System.currentTimeMillis()), true)
    }

    fun onFiltersChanged() {
        val userConfig = UserConfig.getInstance(account)
        if (!userConfig.isClientActivated || !userConfig.filtersLoaded) return
        if (!synced) {
            sync()
            return
        }
        val modes = pruned(snapshot.modes, null)
        if (modes.size != snapshot.modes.size) {
            commit(Snapshot(modes, System.currentTimeMillis()), true)
        }
    }

    private fun sync() {
        if (syncing) return
        syncing = true
        BotCloudStorage.getInstance(account).get(listOf(KEY)) { values ->
            syncing = false
            if (values == null) return@get
            synced = true
            val remote = values[KEY]?.let(::decode)
            val local = snapshot
            val winner = if (remote != null && remote.updatedAt > local.updatedAt) remote else local
            val modes = pruned(winner.modes, null)
            when {
                modes.size != winner.modes.size -> commit(Snapshot(modes, System.currentTimeMillis()), true)
                winner !== local -> commit(winner, false)
                remote == null && local.updatedAt == 0L -> Unit
                remote == null || remote.updatedAt < local.updatedAt -> push(local)
            }
        }
    }

    fun clear() {
        snapshot = EMPTY
        synced = false
        preferences.edit { clear() }
    }

    private fun pruned(modes: Map<Int, Int>, keepId: Int?): HashMap<Int, Int> {
        val filters = MessagesController.getInstance(account).dialogFiltersById
        val result = HashMap<Int, Int>()
        for ((id, mode) in modes) {
            if (id == keepId || filters.get(id) != null) {
                result[id] = mode
            }
        }
        return result
    }

    private fun commit(newSnapshot: Snapshot, upload: Boolean) {
        snapshot = newSnapshot
        preferences.edit { putString(KEY, encode(newSnapshot)) }
        if (upload) {
            push(newSnapshot)
        }
        val messagesController = MessagesController.getInstance(account)
        messagesController.sortDialogs(null)
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload)
        val storage = MessagesStorage.getInstance(account)
        storage.storageQueue.postRunnable { storage.resetAllUnreadCounters(false) }
    }

    private fun push(value: Snapshot) {
        val encoded = encode(value)
        if (encoded.length > BotCloudStorage.MAX_VALUE_LENGTH) {
            FileLog.e("FolderRoles: snapshot too large (${encoded.length})")
            return
        }
        BotCloudStorage.getInstance(account).set(KEY, encoded)
    }

    private fun encode(value: Snapshot): String {
        val modes = JSONObject()
        for ((id, mode) in value.modes) {
            modes.put(id.toString(), mode)
        }
        return JSONObject().put("ts", value.updatedAt).put("f", modes).toString()
    }

    private fun decode(value: String?): Snapshot? {
        if (value.isNullOrEmpty()) return null
        return try {
            val json = JSONObject(value)
            val modesJson = json.optJSONObject("f")
            val modes = HashMap<Int, Int>()
            if (modesJson != null) {
                for (key in modesJson.keys()) {
                    val mode = modesJson.optInt(key, MODE_NONE)
                    if (mode == MODE_OWNER || mode == MODE_ADMIN) {
                        modes[key.toInt()] = mode
                    }
                }
            }
            Snapshot(modes, json.optLong("ts"))
        } catch (e: Exception) {
            FileLog.e(e)
            null
        }
    }

    companion object {
        const val MODE_NONE = 0
        const val MODE_OWNER = 1
        const val MODE_ADMIN = 2

        const val TYPE_OWNER = "folder_role_owner"
        const val TYPE_ADMIN = "folder_role_admin"
        const val SPAN_UID_OWNER = Long.MIN_VALUE + 10
        const val SPAN_UID_ADMIN = Long.MIN_VALUE + 11
        const val PICKER_FLAG_OWNER = 1 shl 29
        const val PICKER_FLAG_ADMIN = 1 shl 30

        private const val KEY = "folder_roles"
        private val EMPTY = Snapshot(emptyMap(), 0L)

        private val instances = arrayOfNulls<FolderRoles>(UserConfig.MAX_ACCOUNT_COUNT)

        @JvmStatic
        fun supportsFlags(filterFlags: Int): Boolean =
            filterFlags and (MessagesController.DIALOG_FILTER_FLAG_GROUPS or MessagesController.DIALOG_FILTER_FLAG_CHANNELS) != 0

        @JvmStatic
        fun effectiveMode(filterFlags: Int, mode: Int): Int = if (supportsFlags(filterFlags)) mode else MODE_NONE

        @JvmStatic
        fun chatTypeOf(mode: Int): String? = when (mode) {
            MODE_OWNER -> TYPE_OWNER
            MODE_ADMIN -> TYPE_ADMIN
            else -> null
        }

        @JvmStatic
        fun modeOf(chatType: String?): Int = when (chatType) {
            TYPE_OWNER -> MODE_OWNER
            TYPE_ADMIN -> MODE_ADMIN
            else -> MODE_NONE
        }

        @JvmStatic
        fun spanUidOf(mode: Int): Long = if (mode == MODE_OWNER) SPAN_UID_OWNER else SPAN_UID_ADMIN

        @JvmStatic
        fun isRoleSpanUid(uid: Long): Boolean = uid == SPAN_UID_OWNER || uid == SPAN_UID_ADMIN

        @JvmStatic
        fun toPickerFlags(mode: Int): Int = when (mode) {
            MODE_OWNER -> PICKER_FLAG_OWNER
            MODE_ADMIN -> PICKER_FLAG_ADMIN
            else -> 0
        }

        @JvmStatic
        fun fromPickerFlags(flags: Int): Int = when {
            flags and PICKER_FLAG_OWNER != 0 -> MODE_OWNER
            flags and PICKER_FLAG_ADMIN != 0 -> MODE_ADMIN
            else -> MODE_NONE
        }

        @JvmStatic
        fun withPickerMode(flags: Int, mode: Int): Int = (flags and (PICKER_FLAG_OWNER or PICKER_FLAG_ADMIN).inv()) or toPickerFlags(mode)

        @JvmStatic
        fun getInstance(account: Int): FolderRoles =
            synchronized(instances) {
                instances[account] ?: FolderRoles(account).also { instances[account] = it }
            }
    }
}
