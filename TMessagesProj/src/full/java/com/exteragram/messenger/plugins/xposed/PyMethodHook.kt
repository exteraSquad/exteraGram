/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.xposed

import com.chaquo.python.PyException
import com.chaquo.python.PyObject
import com.exteragram.messenger.plugins.PluginsConstants
import com.exteragram.messenger.plugins.PluginsController
import com.exteragram.messenger.plugins.hooks.HookFilter
import de.robv.android.xposed.XC_MethodHook
import org.telegram.messenger.FileLog

class PyMethodHook : XC_MethodHook, AutoCloseable {
    private val pluginId: String
    private val pythonCallback: PyObject
    private val beforeHook: PyObject?
    private val afterHook: PyObject?
    private var beforeHookedFilters = ArrayList<HookFilter>()
    private var afterHookedFilters = ArrayList<HookFilter>()
    @Volatile
    private var disabled = false
    @Volatile
    private var closed = false

    constructor(pluginId: String, pythonCallback: PyObject?) : this(
        pluginId,
        pythonCallback,
        true,
        true
    )

    constructor(pluginId: String, pythonCallback: PyObject?, priority: Int) : this(
        pluginId,
        pythonCallback,
        priority,
        true,
        true
    )

    constructor(
        pluginId: String,
        pythonCallback: PyObject?,
        hasBeforeHook: Boolean,
        hasAfterHook: Boolean
    ) : super() {
        requireNotNull(pythonCallback) { "Python callback object cannot be null" }
        this.pluginId = pluginId
        this.pythonCallback = pythonCallback
        beforeHook = getCallbackIfPresent(
            pythonCallback,
            PluginsConstants.Xposed.BEFORE_HOOKED_METHOD,
            hasBeforeHook
        )
        afterHook = getCallbackIfPresent(
            pythonCallback,
            PluginsConstants.Xposed.AFTER_HOOKED_METHOD,
            hasAfterHook
        )
    }

    constructor(
        pluginId: String,
        pythonCallback: PyObject?,
        priority: Int,
        hasBeforeHook: Boolean,
        hasAfterHook: Boolean
    ) : super(priority) {
        requireNotNull(pythonCallback) { "Python callback object cannot be null" }
        this.pluginId = pluginId
        this.pythonCallback = pythonCallback
        beforeHook = getCallbackIfPresent(
            pythonCallback,
            PluginsConstants.Xposed.BEFORE_HOOKED_METHOD,
            hasBeforeHook
        )
        afterHook = getCallbackIfPresent(
            pythonCallback,
            PluginsConstants.Xposed.AFTER_HOOKED_METHOD,
            hasAfterHook
        )
    }

    fun setBeforeHookedFilters(beforeHookedFilters: ArrayList<HookFilter>) {
        this.beforeHookedFilters = beforeHookedFilters
    }

    fun setAfterHookedFilters(afterHookedFilters: ArrayList<HookFilter>) {
        this.afterHookedFilters = afterHookedFilters
    }

    fun getBeforeHookedFilters(): ArrayList<HookFilter> = beforeHookedFilters

    fun getAfterHookedFilters(): ArrayList<HookFilter> = afterHookedFilters

    override fun beforeHookedMethod(param: MethodHookParam) {
        if (disabled) {
            return
        }

        val callback = beforeHook ?: return
        if (!PluginsController.getInstance().isPluginActive(pluginId)) {
            return
        }
        try {
            if (!executeFilters(beforeHookedFilters, param, true)) {
                return
            }
            callback.call(param)
        } catch (t: Throwable) {
            handleHookError("beforeHookedMethod", t)
        }
    }

    override fun afterHookedMethod(param: MethodHookParam) {
        if (disabled) {
            return
        }

        val callback = afterHook ?: return
        if (!PluginsController.getInstance().isPluginActive(pluginId)) {
            return
        }
        try {
            if (!executeFilters(afterHookedFilters, param, false)) {
                return
            }
            callback.call(param)
        } catch (t: Throwable) {
            handleHookError("afterHookedMethod", t)
        }
    }

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        disabled = true
        beforeHook?.close()
        afterHook?.close()
    }

    private fun executeFilters(
        filters: ArrayList<HookFilter>,
        param: MethodHookParam,
        isBefore: Boolean
    ): Boolean {
        for (filter in filters) {
            if (!filter.execute(param, isBefore)) {
                return false
            }
        }
        return true
    }

    private fun getCallbackIfPresent(
        callbackObject: PyObject,
        name: String,
        enabled: Boolean
    ): PyObject? {
        return if (enabled && callbackObject.containsKey(name)) {
            callbackObject[name]
        } else {
            null
        }
    }

    private fun handleHookError(hookMethodName: String, t: Throwable) {
        if (t is PyException && t.message != null && t.message!!.contains("closed")) {
            disabled = true
            FileLog.e("Attempted to call a closed PyObject callback in $pluginId")
            return
        }
        FileLog.e("Plugin '$pluginId' crashed in $hookMethodName: ${t.message}", t)
    }
}