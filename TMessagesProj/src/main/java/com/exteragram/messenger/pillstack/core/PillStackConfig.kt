/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

@file:JvmName("PillStackConfig")
package com.exteragram.messenger.pillstack.core

import android.content.SharedPreferences
import com.exteragram.messenger.backup.PreferencesUtils
import com.exteragram.messenger.config.*
import com.exteragram.messenger.pillstack.ui.pills.crypto.utils.PillStackCurrencies
import org.telegram.messenger.NotificationCenter
import java.util.HashSet

const val CONFIG_NAME = "pillstackconfig"

val preferences: SharedPreferences = PreferencesUtils.getPreferences(CONFIG_NAME)
val editor: SharedPreferences.Editor = preferences.edit()

private val sync = Any()
var configLoaded = false

var useCurrentLocation by BooleanPref(true)
var customWeatherLocation by NullableStringPref(null)
var customWeatherAddress by NullableStringPref(null)
var infiniteScrolling by BooleanPref(true)
var gramTargetCurrency by StringPref(PillStackCurrencies.TARGET_AUTO)
var btcTargetCurrency by StringPref(PillStackCurrencies.TARGET_AUTO)
var usdTargetCurrency by StringPref(PillStackCurrencies.TARGET_AUTO)
var lastActivePillId by IntegerPref(-1, "lastActivePillId")

var activePills = mutableListOf<Int>()
var hiddenPills = mutableListOf<Int>()

private val pendingUpdates = HashSet<Int>()

enum class PillType(val id: Int) {
    WEATHER(1),
    GRAM(2),
    BTC(3),
    USD(4),
    CACHE(5),
    PROXY(6);

    companion object {
        @JvmStatic
        fun getById(id: Int): PillType? = entries.find { it.id == id }
    }
}

private fun parsePillsList(data: String?): ArrayList<Int> {
    val result = arrayListOf<Int>()
    if (data.isNullOrEmpty()) return result

    val cleaned = if (data.startsWith("[")) {
        data.replace(Regex("[\\[\\]\"]"), "")
    } else data

    cleaned.split(",").forEach { part ->
        part.trim().toIntOrNull()?.let { result.add(it) }
    }
    return result
}

private fun serializePillsList(list: List<Int>): String {
    if (list.isEmpty()) return ""
    return list.joinToString(",")
}

fun getDefaultActivePills(): ArrayList<Int> = arrayListOf()

@Suppress("unused")
private val init: Unit = loadConfig()

fun loadConfig() {
    synchronized(sync) {
        if (configLoaded) return

        val activeString = preferences.getString("activePills", null)
        val hiddenString = preferences.getString("hiddenPills", null)

        if (activeString != null) {
            activePills = parsePillsList(activeString).toMutableList()
            hiddenPills = if (hiddenString != null) {
                parsePillsList(hiddenString).toMutableList()
            } else {
                mutableListOf()
            }
        } else {
            activePills = getDefaultActivePills().toMutableList()
            hiddenPills = mutableListOf()

            for (item in PillRegistry.getRegisteredPills()) {
                if (!activePills.contains(item.id())) {
                    hiddenPills.add(item.id())
                }
            }
            savePillsLayout()
        }

        sanitizePills()

        configLoaded = true
    }
}

fun reloadConfig() {
    synchronized(sync) {
        configLoaded = false
        loadConfig()
    }
}

fun sanitizePills() {
    var changed = activePills.removeAll { !PillRegistry.isRegistered(it) }
    changed = changed || hiddenPills.removeAll { !PillRegistry.isRegistered(it) }

    for (item in PillRegistry.getRegisteredPills()) {
        if (item.id() !in activePills && item.id() !in hiddenPills) {
            hiddenPills.add(item.id())
            changed = true
        }
    }

    if (changed) {
        savePillsLayout()
    }
}

fun savePillsLayout() {
    editor.putString("activePills", serializePillsList(activePills))
        .putString("hiddenPills", serializePillsList(hiddenPills))
        .apply()
}

fun saveLastActivePillId(id: Int) {
    lastActivePillId = id
    editor.putInt("lastActivePillId", id).apply()
}

fun notifySettingsChanged(vararg pillIds: Int) {
    if (pillIds.isNotEmpty()) {
        pillIds.forEach { pendingUpdates.add(it) }
        val args = pillIds.toTypedArray()
        NotificationCenter.getGlobalInstance().postNotificationName(
            NotificationCenter.pillStackSettingsChanged, *args
        )
    } else {
        for (info in PillRegistry.getRegisteredPills()) {
            pendingUpdates.add(info.id())
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.pillStackSettingsChanged)
    }
}

fun checkAndClearPendingUpdate(pillId: Int): Boolean {
    return pendingUpdates.remove(pillId)
}

fun shouldUpdatePill(args: Array<out Any>?, vararg pillIds: Int): Boolean {
    if (args.isNullOrEmpty() || pillIds.isEmpty()) return true

    for (arg in args) {
        if (arg is Int && pillIds.contains(arg)) {
            return true
        }
    }
    return false
}
