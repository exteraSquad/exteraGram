/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.utils

import android.text.TextUtils
import com.chaquo.python.PyException
import com.chaquo.python.PyObject

object PyObjectUtils {
    @JvmStatic
    fun isSharedValue(value: Any?): Boolean {
        return value == null ||
                value is Boolean ||
                value is Number ||
                value is Char ||
                value is String
    }

    @JvmStatic
    fun closeSafely(pyObject: PyObject?) {
        if (pyObject == null) {
            return
        }

        try {
            pyObject.close()
        } catch (_: Throwable) {
        }
    }

    @JvmStatic
    fun <T> toJavaCompat(pyObject: PyObject?, clazz: Class<T>): T? {
        if (pyObject == null) {
            return null
        }

        try {
            return pyObject.toJava(clazz)
        } catch (_: PyException) {
        } catch (_: ClassCastException) {
        }

        return try {
            pyObject.callAttr("__getattribute__", "java")?.toJava(clazz)
        } catch (_: PyException) {
            null
        } catch (_: ClassCastException) {
            null
        }
    }

    @JvmStatic
    fun getString(pyObject: PyObject?, key: String, defaultValue: String?): String? {
        return getString(pyObject, key, defaultValue, false)
    }

    @JvmStatic
    fun getString(
        pyObject: PyObject?,
        key: String,
        defaultValue: String?,
        fromMap: Boolean
    ): String? {
        if (pyObject == null || TextUtils.isEmpty(key)) {
            return defaultValue
        }

        return try {
            val valueObj = if (fromMap) pyObject.callAttr("get", key) else pyObject[key]
            valueObj?.toString() ?: defaultValue
        } catch (_: PyException) {
            defaultValue
        } catch (_: ClassCastException) {
            defaultValue
        }
    }

    @JvmStatic
    fun getBoolean(pyObject: PyObject?, key: String, defaultValue: Boolean): Boolean {
        if (pyObject == null || TextUtils.isEmpty(key)) {
            return defaultValue
        }

        return try {
            pyObject[key]?.toBoolean() ?: defaultValue
        } catch (_: PyException) {
            defaultValue
        } catch (_: ClassCastException) {
            defaultValue
        }
    }

    @JvmStatic
    fun getInt(pyObject: PyObject?, key: String, defaultValue: Int): Int {
        return getInt(pyObject, key, defaultValue, false)
    }

    @JvmStatic
    fun getInt(pyObject: PyObject?, key: String, defaultValue: Int, fromMap: Boolean): Int {
        if (pyObject == null || TextUtils.isEmpty(key)) {
            return defaultValue
        }

        return try {
            val valueObj = if (fromMap) pyObject.callAttr("get", key) else pyObject[key]
            valueObj?.toInt() ?: defaultValue
        } catch (_: PyException) {
            defaultValue
        } catch (_: ClassCastException) {
            defaultValue
        }
    }

    @JvmStatic
    fun getStringArray(
        pyObject: PyObject?,
        key: String,
        defaultValue: Array<String>?
    ): Array<String>? {
        if (pyObject == null || TextUtils.isEmpty(key)) {
            return defaultValue
        }

        return try {
            val items = pyObject[key]?.toJava(Array<String>::class.java) ?: return defaultValue
            if (items.isEmpty()) defaultValue else items
        } catch (_: PyException) {
            defaultValue
        } catch (_: ClassCastException) {
            defaultValue
        }
    }
}