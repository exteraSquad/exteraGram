/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.config

import com.exteragram.messenger.backup.PreferencesUtils.BackupItem
import com.exteragram.messenger.editor as exteraEditor
import com.exteragram.messenger.preferences as exteraPreferences
import kotlin.reflect.KProperty

val allDelegates = mutableListOf<BasePref<*>>()
val registeredKeys = mutableListOf<BackupItem>()

abstract class BasePref<T>(val defaultValue: T, val backupKey: String? = null) {
    @Volatile
    protected var cache: T? = null
    @Volatile
    private var loaded = false
    lateinit var key: String

    protected abstract val backupClass: Class<*>

    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): BasePref<T> {
        key = backupKey ?: property.name
        registeredKeys.add(BackupItem(key, backupClass))
        allDelegates.add(this)
        return this
    }

    @Suppress("UNCHECKED_CAST")
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        if (!loaded) {
            cache = fetch(key, defaultValue)
            loaded = true
        }
        return cache as T
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        if (loaded && cache == value) return
        cache = value
        loaded = true
        save(key, value)
    }

    fun invalidate() {
        loaded = false
        cache = null
    }

    abstract fun fetch(key: String, default: T): T
    abstract fun save(key: String, value: T)
}

class BooleanPref(def: Boolean, backupKey: String? = null) : BasePref<Boolean>(def, backupKey) {
    override val backupClass: Class<*> = Boolean::class.javaObjectType
    override fun fetch(key: String, default: Boolean) = exteraPreferences.getBoolean(key, default)
    override fun save(key: String, value: Boolean) = exteraEditor.putBoolean(key, value).apply()
}

class IntegerPref(def: Int, backupKey: String? = null) : BasePref<Int>(def, backupKey) {
    override val backupClass: Class<*> = Int::class.javaObjectType
    override fun fetch(key: String, default: Int) = exteraPreferences.getInt(key, default)
    override fun save(key: String, value: Int) = exteraEditor.putInt(key, value).apply()
}

class SanitizedIntegerPref(
    def: Int,
    backupKey: String? = null,
    private val sanitize: (Int) -> Int
) : BasePref<Int>(def, backupKey) {
    override val backupClass: Class<*> = Int::class.javaObjectType
    override fun fetch(key: String, default: Int) = sanitize(exteraPreferences.getInt(key, default))
    override fun save(key: String, value: Int) = exteraEditor.putInt(key, sanitize(value)).apply()
}

class FloatPref(def: Float, backupKey: String? = null) : BasePref<Float>(def, backupKey) {
    override val backupClass: Class<*> = Float::class.javaObjectType
    override fun fetch(key: String, default: Float): Float = try {
        exteraPreferences.getFloat(key, default)
    } catch (_: ClassCastException) {
        try { exteraPreferences.getInt(key, default.toInt()).toFloat() } catch (_: Exception) { default }
    }
    override fun save(key: String, value: Float) = exteraEditor.putFloat(key, value).apply()
}

class LongPref(def: Long, backupKey: String? = null) : BasePref<Long>(def, backupKey) {
    override val backupClass: Class<*> = Long::class.javaObjectType
    override fun fetch(key: String, default: Long) = exteraPreferences.getLong(key, default)
    override fun save(key: String, value: Long) = exteraEditor.putLong(key, value).apply()
}

class StringPref(def: String, backupKey: String? = null) : BasePref<String>(def, backupKey) {
    override val backupClass: Class<*> = String::class.java
    override fun fetch(key: String, default: String) = exteraPreferences.getString(key, default) ?: default
    override fun save(key: String, value: String) = exteraEditor.putString(key, value).apply()
}

class NullableStringPref(def: String?, backupKey: String? = null) : BasePref<String?>(def, backupKey) {
    override val backupClass: Class<*> = String::class.java
    override fun fetch(key: String, default: String?) = exteraPreferences.getString(key, default)
    override fun save(key: String, value: String?) = exteraEditor.putString(key, value).apply()
}

class StringSetPref(def: Set<String>, backupKey: String? = null) : BasePref<Set<String>>(def, backupKey) {
    override val backupClass: Class<*> = Set::class.java
    override fun fetch(key: String, default: Set<String>) = exteraPreferences.getStringSet(key, default) ?: default
    override fun save(key: String, value: Set<String>) = exteraEditor.putStringSet(key, value).apply()
}

interface PrefEnum {
    val prefId: Int
}

class EnumPref<E>(def: E, backupKey: String? = null) : BasePref<E>(def, backupKey) where E : Enum<E>, E : PrefEnum {
    override val backupClass: Class<*> = Int::class.javaObjectType
    override fun fetch(key: String, default: E): E {
        val id = exteraPreferences.getInt(key, default.prefId)
        return try {
            default.declaringJavaClass.enumConstants?.firstOrNull { it.prefId == id } ?: default
        } catch (_: Exception) {
            default
        }
    }

    override fun save(key: String, value: E) = exteraEditor.putInt(key, value.prefId).apply()
}
