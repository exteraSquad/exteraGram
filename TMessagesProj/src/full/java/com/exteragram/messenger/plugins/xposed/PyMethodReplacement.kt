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
import com.exteragram.messenger.plugins.utils.PyObjectUtils
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import org.telegram.messenger.FileLog
import java.lang.reflect.InvocationTargetException

class PyMethodReplacement : XC_MethodReplacement, AutoCloseable {
    private val pluginId: String
    private val pythonCallback: PyObject
    private val replaceHook: PyObject
    @Volatile
    private var disabled = false
    @Volatile
    private var closed = false

    constructor(pluginId: String, pythonCallback: PyObject?) : super() {
        requireNotNull(pythonCallback) { "Python callback object cannot be null" }
        require(pythonCallback.containsKey(PluginsConstants.Xposed.REPLACE_HOOKED_METHOD)) {
            "Python callback object must contain a method named 'replaceHookedMethod'"
        }
        this.pluginId = pluginId
        this.pythonCallback = pythonCallback
        replaceHook = requireNotNull(pythonCallback[PluginsConstants.Xposed.REPLACE_HOOKED_METHOD])
    }

    constructor(pluginId: String, pythonCallback: PyObject?, priority: Int) : super(priority) {
        requireNotNull(pythonCallback) { "Python callback object cannot be null" }
        require(pythonCallback.containsKey(PluginsConstants.Xposed.REPLACE_HOOKED_METHOD)) {
            "Python callback object must contain a method named 'replaceHookedMethod'"
        }
        this.pluginId = pluginId
        this.pythonCallback = pythonCallback
        replaceHook = requireNotNull(pythonCallback[PluginsConstants.Xposed.REPLACE_HOOKED_METHOD])
    }

    override fun replaceHookedMethod(param: MethodHookParam): Any? {
        if (disabled || !PluginsController.getInstance().isPluginActive(pluginId)) {
            return try {
                XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
            } catch (e: InvocationTargetException) {
                throw e.targetException ?: e
            }
        }

        var pyResult: PyObject? = null
        var javaResult: Any? = null
        return try {
            pyResult = replaceHook.call(param)
            javaResult = pyResult?.toJava(Any::class.java)
            javaResult
        } catch (t: Throwable) {
            handleHookError(t)
            null
        } finally {
            if (!PyObjectUtils.isSharedValue(javaResult)) {
                PyObjectUtils.closeSafely(pyResult)
            }
        }
    }

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        disabled = true
        replaceHook.close()
    }

    private fun handleHookError(t: Throwable) {
        if (t is PyException && t.message != null && t.message!!.contains("closed")) {
            disabled = true
            FileLog.e("Attempted to call a closed PyObject callback in $pluginId")
            return
        }
        FileLog.e("Plugin '$pluginId' crashed in replaceHookedMethod: ${t.message}", t)
    }
}