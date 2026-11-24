/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

@file:JvmName("AiConfig")
package com.exteragram.messenger.ai

import android.content.SharedPreferences
import android.util.Base64
import com.exteragram.messenger.GSON
import com.exteragram.messenger.editor as exteraEditor
import com.exteragram.messenger.ai.data.Message
import com.exteragram.messenger.ai.data.Role
import com.exteragram.messenger.ai.data.Service
import com.exteragram.messenger.ai.data.Suggestions
import com.exteragram.messenger.backup.PreferencesUtils
import com.exteragram.messenger.config.*
import com.exteragram.messenger.preferences as exteraPreferences
import com.google.gson.reflect.TypeToken
import org.telegram.messenger.FileLog
import org.telegram.messenger.UserConfig
import androidx.core.content.edit

@JvmField
val DEFAULT_SERVICE = Service("default", "https://generativelanguage.googleapis.com/v1beta", "gemini-3.5-flash", null)

const val ON_DEVICE_DEFAULT_MODEL = "Gemini Nano"

@JvmField
val ON_DEVICE_SERVICE = Service("on-device", null, ON_DEVICE_DEFAULT_MODEL, null).apply { kind = Service.Kind.ON_DEVICE }

const val CONFIG_NAME = "aiConfig"
private const val LEGACY_SELECTED_SERVICE_KEY = "selectedService"
private const val LEGACY_CONFIG_NAME = "pillstackconfig"
private const val REPLACE_TELEGRAM_EDITOR_KEY = "replaceTelegramEditor"
private const val REPLACE_TELEGRAM_SUMMARIES_KEY = "replaceTelegramSummaries"
val preferences: SharedPreferences = PreferencesUtils.getPreferences(CONFIG_NAME)
val editor: SharedPreferences.Editor by lazy { preferences.edit() }

private val legacyConfigMigrated = migrateLegacyConfig()

// todo: remove after 2-3 updates
private fun migrateLegacyConfig() {
    val legacyPreferences = PreferencesUtils.getPreferences(LEGACY_CONFIG_NAME)
    val destinationEditor = preferences.edit()
    val migratedKeys = arrayListOf<String>()

    for (key in arrayOf("services", "roles", "history")) {
        if (!preferences.contains(key)) {
            (legacyPreferences.all[key] as? String)?.let {
                destinationEditor.putString(key, it)
                migratedKeys.add(key)
            }
        }
    }

    if (migratedKeys.isNotEmpty() && destinationEditor.commit()) {
        legacyPreferences.edit {
            migratedKeys.forEach { remove(it) }
        }
    }
}

fun ensureConfigMigrated() = legacyConfigMigrated

fun encrypt(text: String?): String? {
    if (text.isNullOrEmpty()) return null
    return Base64.encodeToString(text.toByteArray(), Base64.DEFAULT)
}

fun decrypt(text: String?): String? {
    if (text.isNullOrEmpty()) return null
    return String(Base64.decode(text, Base64.DEFAULT))
}

var saveHistory by BooleanPref(true)
var responseStreaming by BooleanPref(true)
var temperature by IntegerPref(10)
var showResponseOnly by BooleanPref(false)
var insertAsQuote by BooleanPref(true)
var onDeviceReasoning by BooleanPref(false)
var onDevicePreviewModel by BooleanPref(false)
var onDeviceFastModel by BooleanPref(false)

@JvmOverloads
fun getReplaceTelegramEditor(account: Int = UserConfig.selectedAccount) =
    exteraPreferences.getBoolean(REPLACE_TELEGRAM_EDITOR_KEY, !UserConfig.getInstance(account).isPremium)

fun setReplaceTelegramEditor(value: Boolean) = exteraEditor.putBoolean(REPLACE_TELEGRAM_EDITOR_KEY, value).apply()

@JvmOverloads
fun getReplaceTelegramSummaries(account: Int = UserConfig.selectedAccount) =
    exteraPreferences.getBoolean(REPLACE_TELEGRAM_SUMMARIES_KEY, !UserConfig.getInstance(account).isPremium)

fun setReplaceTelegramSummaries(value: Boolean) = exteraEditor.putBoolean(REPLACE_TELEGRAM_SUMMARIES_KEY, value).apply()

var selectedServiceId by NullableStringPref(null, "selectedServiceId")
private var selectedServiceHash by IntegerPref(DEFAULT_SERVICE.legacyHash, LEGACY_SELECTED_SERVICE_KEY)
var selectedRole by StringPref(Suggestions.entries[0].role.name)

fun getSelectedService(): Service {
    val services = getServices()
    val id = selectedServiceId
    if (!id.isNullOrEmpty()) {
        val service = services.find { it.id == id }
        if (service != null) {
            return service
        }
        selectedServiceId = null
    }

    if (exteraPreferences.contains(LEGACY_SELECTED_SERVICE_KEY)) {
        val legacyHash = selectedServiceHash
        val service = services.find { it.legacyHash == legacyHash }
        if (service != null) {
            selectedServiceId = service.id
            exteraEditor.remove(LEGACY_SELECTED_SERVICE_KEY).apply()
            return service
        }
        exteraEditor.remove(LEGACY_SELECTED_SERVICE_KEY).apply()
    }

    return services.firstOrNull() ?: DEFAULT_SERVICE
}

fun setSelectedServices(service: Service) {
    selectedServiceId = service.id
    exteraEditor.remove(LEGACY_SELECTED_SERVICE_KEY).apply()
}

fun clearSelectedService() {
    selectedServiceId = null
    exteraEditor.remove(LEGACY_SELECTED_SERVICE_KEY).apply()
}

fun getServices(): ArrayList<Service> {
    val json = preferences.getString("services", null) ?: return arrayListOf()
    return try {
        val type = object : TypeToken<ArrayList<Service>>() {}.type
        val services: ArrayList<Service> = GSON.fromJson(json, type) ?: arrayListOf()
        var changed = false
        services.forEach { service ->
            if (service.ensureId()) {
                changed = true
            }
        }
        if (changed) {
            saveServices(services)
        }
        services
    } catch (e: Exception) {
        FileLog.e(e)
        arrayListOf()
    }
}

fun saveServices(services: ArrayList<Service>) {
    editor.putString("services", GSON.toJson(services)).apply()
}

fun saveRoles(roles: ArrayList<Role>) {
    editor.putString("roles", GSON.toJson(roles)).apply()
}

fun getRoles(): ArrayList<Role> {
    val json = preferences.getString("roles", null) ?: return arrayListOf()
    return try {
        val type = object : TypeToken<ArrayList<Role>>() {}.type
        GSON.fromJson(json, type) ?: arrayListOf()
    } catch (e: Exception) {
        FileLog.e(e)
        arrayListOf()
    }
}

fun setSelectedAiRole(role: Role) {
    selectedRole = role.name
}

fun clearSelectedRole() {
    editor.remove("selectedRole").apply()
}

fun getConversationHistory(): ArrayList<Message> {
    val json = preferences.getString("history", null) ?: return arrayListOf()
    return try {
        val type = object : TypeToken<ArrayList<Message>>() {}.type
        GSON.fromJson(json, type) ?: arrayListOf()
    } catch (e: Exception) {
        FileLog.e(e)
        arrayListOf()
    }
}

fun saveConversationHistory(history: ArrayList<Message>) {
    editor.putString("history", GSON.toJson(history)).apply()
}

fun clearConversationHistory() {
    editor.remove("history").apply()
}

fun removeLastFromHistory() {
    val history = getConversationHistory()
    if (history.size >= 2) {
        history.removeAt(history.size - 1)
        history.removeAt(history.size - 1)
        saveConversationHistory(history)
    }
}