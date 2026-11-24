/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.regdate

import android.content.Context
import androidx.collection.LruCache
import com.exteragram.messenger.api.db.DatabaseHelper
import com.exteragram.messenger.api.dto.RegDateDTO
import com.exteragram.messenger.api.model.RegDateFlag
import com.exteragram.messenger.backup.InvisibleEncryptor
import com.exteragram.messenger.badges.BadgesController
import com.exteragram.messenger.utils.chats.ChatUtils
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.ContactsController
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.ui.Cells.UserInfoCell
import java.nio.charset.StandardCharsets
import java.util.function.Consumer

class RegDateController(val currentAccount: Int) {

    private val regDateCache = LruCache<Long, RegDateDTO>(256)

    fun fetchRegistrationDate(userId: Long, callback: Consumer<RegDateDTO?>) {
        val telegramMonth = telegramRegistrationMonth(userId)
        if (telegramMonth != null) {
            callback.accept(telegramMonth)
            return
        }
        val cached = regDateCache[userId]
        if (cached != null) {
            callback.accept(limitByProfilePhotos(userId, cached))
            return
        }
        if (!BadgesController.hasBadge()) {
            estimateLocally(userId, callback)
            return
        }
        ChatUtils.getInstance(currentAccount).sendBotRequest("regdate $userId", false) { message ->
            val dto = parseRegDate(message)
            AndroidUtilities.runOnUIThread {
                if (dto != null) {
                    regDateCache.put(userId, dto)
                    callback.accept(limitByProfilePhotos(userId, dto))
                } else {
                    estimateLocally(userId, callback)
                }
            }
        }
    }

    fun addRegistrationDate(userId: Long, registrationMonth: String, callback: Consumer<String?>) {
        if (!BadgesController.isDeveloper()) {
            callback.accept("прости, но нет")
            return
        }
        DatabaseHelper.isRegDateAdded(userId) { isAdded ->
            AndroidUtilities.runOnUIThread {
                if (isAdded) {
                    callback.accept("ok")
                    return@runOnUIThread
                }
                val query = "addregdate $userId $registrationMonth"
                ChatUtils.getInstance(currentAccount).sendBotRequest(query, false) { message ->
                    if (message == "ok") {
                        DatabaseHelper.setRegDateAdded(userId)
                    }
                    callback.accept(message ?: "no results")
                }
            }
        }
    }

    fun formatRegistrationDate(userId: Long, dto: RegDateDTO): String {
        val date = LocaleController.formatYearMont(dto.timestamp, true)

        return if (userId == UserConfig.getInstance(currentAccount).clientUserId) {
            when (dto.flag) {
                RegDateFlag.LT -> LocaleController.formatString(R.string.CreationDateSelfEarlier, date)
                RegDateFlag.ET -> LocaleController.formatString(R.string.CreationDateSelfLater, date)
                else -> LocaleController.formatString(R.string.CreationDateSelfApproximately, date)
            }
        } else {
            val name = ContactsController.formatName(MessagesController.getInstance(currentAccount).getUser(userId))
            when (dto.flag) {
                RegDateFlag.LT -> LocaleController.formatString(R.string.CreationDateUserEarlier, name, date)
                RegDateFlag.ET -> LocaleController.formatString(R.string.CreationDateUserLater, name, date)
                else -> LocaleController.formatString(R.string.CreationDateUserApproximately, name, date)
            }
        }
    }

    private fun telegramRegistrationMonth(userId: Long): RegDateDTO? {
        val month = MessagesController.getInstance(currentAccount).getPeerSettings(userId)?.registration_month
            ?: registrationMonths.getString(userId.toString(), null)
            ?: return null
        val timestamp = runCatching { UserInfoCell.getTimestamp(month) }.getOrNull()?.takeIf { it > 0 } ?: return null
        return RegDateDTO(timestamp, RegDateFlag.EXACT)
    }

    private fun estimateLocally(userId: Long, callback: Consumer<RegDateDTO?>) {
        Utilities.globalQueue.postRunnable {
            val dto = LocalRegDates.estimate(userId)
            AndroidUtilities.runOnUIThread {
                callback.accept(dto?.let { limitByProfilePhotos(userId, it) })
            }
        }
    }

    private fun limitByProfilePhotos(userId: Long, dto: RegDateDTO): RegDateDTO {
        if (dto.flag == RegDateFlag.EXACT) {
            return dto
        }
        val messagesController = MessagesController.getInstance(currentAccount)
        var photoDate = messagesController.getUserFull(userId)?.profile_photo?.date?.takeIf { it > 0 }?.toLong() ?: Long.MAX_VALUE
        messagesController.getDialogPhotos(userId)?.photos?.forEach { photo ->
            if (photo != null && photo.date > 0) {
                photoDate = minOf(photoDate, photo.date.toLong())
            }
        }
        return if (photoDate < dto.timestamp) RegDateDTO(photoDate, RegDateFlag.LT) else dto
    }

    private fun parseRegDate(message: String?): RegDateDTO? {
        if (message == null || message.startsWith("failed")) {
            return null
        }
        return try {
            Gson().fromJson(message, RegDateDTO::class.java)?.takeIf { it.timestamp > 0 && it.flag != null }
        } catch (e: Exception) {
            FileLog.e(e)
            null
        }
    }

    private object LocalRegDates {
        private var ids: LongArray? = null
        private var dates: LongArray? = null

        fun estimate(userId: Long): RegDateDTO? {
            load()
            val ids = ids!!
            val dates = dates!!
            if (ids.isEmpty()) {
                return null
            }
            val index = ids.binarySearch(userId)
            return when {
                index >= 0 -> RegDateDTO(dates[index], RegDateFlag.EXACT)
                userId < ids.first() -> RegDateDTO(dates.first(), RegDateFlag.LT)
                userId > ids.last() -> RegDateDTO(dates.last(), RegDateFlag.ET)
                else -> {
                    val to = -index - 1
                    val from = to - 1
                    val ratio = (userId - ids[from]) / (ids[to] - ids[from]).toDouble()
                    RegDateDTO((dates[from] + (dates[to] - dates[from]) * ratio).toLong(), RegDateFlag.INTERPOLATED)
                }
            }
        }

        private fun load() {
            if (ids != null) {
                return
            }
            val points = try {
                val bytes = ApplicationLoader.applicationContext.assets.open("extera/registration_dates.bin").use { it.readBytes() }
                val json = Gson().fromJson(InvisibleEncryptor.decode(String(bytes, StandardCharsets.UTF_8)), JsonObject::class.java)
                json.entrySet().map { it.key.toLong() to it.value.asLong }.sortedBy { it.first }
            } catch (e: Exception) {
                FileLog.e(e)
                emptyList()
            }
            dates = LongArray(points.size) { points[it].second }
            ids = LongArray(points.size) { points[it].first }
        }
    }

    companion object {
        private val Instance = arrayOfNulls<RegDateController>(UserConfig.MAX_ACCOUNT_COUNT)
        private val lockObjects = Array(UserConfig.MAX_ACCOUNT_COUNT) { Any() }
        private val registrationMonths by lazy {
            ApplicationLoader.applicationContext.getSharedPreferences("registration_months", Context.MODE_PRIVATE)
        }

        @JvmStatic
        fun saveRegistrationMonth(userId: Long, month: String?) {
            if (month.isNullOrEmpty() || registrationMonths.getString(userId.toString(), null) == month) {
                return
            }
            registrationMonths.edit().putString(userId.toString(), month).apply()
        }

        @JvmStatic
        fun getInstance(num: Int): RegDateController {
            var localInstance = Instance[num]
            if (localInstance == null) {
                synchronized(lockObjects[num]) {
                    localInstance = Instance[num]
                    if (localInstance == null) {
                        localInstance = RegDateController(num)
                        Instance[num] = localInstance
                    }
                }
            }
            return localInstance!!
        }
    }
}
