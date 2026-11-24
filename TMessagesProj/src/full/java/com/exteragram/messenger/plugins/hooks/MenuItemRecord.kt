/*
 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.
*/

package com.exteragram.messenger.plugins.hooks

import android.content.Context
import android.text.TextUtils
import com.chaquo.python.PyObject
import com.exteragram.messenger.plugins.PluginsConstants
import com.exteragram.messenger.plugins.PluginsController
import com.exteragram.messenger.plugins.utils.PyObjectUtils
import org.mvel2.MVEL
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import java.io.Serializable
import java.util.UUID

class MenuItemRecord(
    val pluginId: String,
    pyData: PyObject,
) {
    @Volatile
    private var removed = false

    val itemId: String

    val menuType: String? =
        PyObjectUtils.getString(pyData, PluginsConstants.MenuItemProperties.MENU_TYPE, null, true)

    val text: String? =
        PyObjectUtils.getString(pyData, PluginsConstants.MenuItemProperties.TEXT, null, true)

    val onClickCallback: PyObject? =
        pyData.callAttr("get", PluginsConstants.MenuItemProperties.ON_CLICK)

    val onLongClickCallback: PyObject? =
        pyData.callAttr("get", PluginsConstants.MenuItemProperties.ON_LONG_CLICK)

    val iconName: String?

    val iconResId: Int

    val subtext: String?

    val conditionString: String?

    val priority: Int

    init {
        val pyItemId =
            PyObjectUtils.getString(pyData, PluginsConstants.MenuItemProperties.ITEM_ID, null, true)
        itemId = pyItemId.takeUnless { it.isNullOrEmpty() } ?: UUID.randomUUID().toString()

        iconName =
            PyObjectUtils.getString(pyData, PluginsConstants.MenuItemProperties.ICON, null, true)
        subtext =
            PyObjectUtils.getString(pyData, PluginsConstants.MenuItemProperties.SUBTEXT, null, true)
        conditionString = PyObjectUtils.getString(
            pyData,
            PluginsConstants.MenuItemProperties.CONDITION,
            null,
            true
        )
        priority =
            PyObjectUtils.getInt(pyData, PluginsConstants.MenuItemProperties.PRIORITY, 0, true)

        var tempIconResId = 0
        if (!TextUtils.isEmpty(iconName)) {
            try {
                val context: Context = ApplicationLoader.applicationContext
                tempIconResId =
                    context.resources.getIdentifier(iconName, "drawable", context.packageName)
            } catch (_: Exception) {
            }
        }
        iconResId = tempIconResId

        if (TextUtils.isEmpty(menuType) || TextUtils.isEmpty(text) || onClickCallback == null) {
            throw IllegalArgumentException("MenuItemRecord missing essential fields: menuType, text, or onClickCallback.")
        }
    }

    fun markRemoved() {
        removed = true
    }

    fun executeClick(contextData: Any?) {
        if (removed || !PluginsController.getInstance().isPluginActive(pluginId)) {
            return
        }
        try {
            onClickCallback?.call(contextData)
        } catch (e: Exception) {
            FileLog.e(e)
        }
    }

    fun hasLongClick(): Boolean = onLongClickCallback != null

    fun executeLongClick(contextData: Any?) {
        if (removed || !PluginsController.getInstance().isPluginActive(pluginId)) {
            return
        }
        try {
            onLongClickCallback?.call(contextData)
        } catch (e: Exception) {
            FileLog.e(e)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        other as MenuItemRecord
        return itemId == other.itemId && pluginId == other.pluginId
    }

    override fun hashCode(): Int {
        var result = itemId.hashCode()
        result = 31 * result + pluginId.hashCode()
        return result
    }

    private val compiledCondition: Serializable? by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val condition = conditionString
        if (condition.isNullOrEmpty()) {
            null
        } else {
            try {
                MVEL.compileExpression(condition)
            } catch (e: Exception) {
                FileLog.e(e)
                null
            }
        }
    }

    fun checkCondition(contextData: Map<String, Any>?): Boolean {
        if (conditionString.isNullOrEmpty() || contextData == null) {
            return true
        }
        val compiled = compiledCondition ?: return false
        return try {
            val result = MVEL.executeExpression(compiled, contextData, Boolean::class.java)
            result ?: false
        } catch (_: Exception) {
            false
        }
    }
}