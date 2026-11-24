/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.hooks

import de.robv.android.xposed.XC_MethodHook
import org.telegram.messenger.FileLog
import java.lang.reflect.Member
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean

class XposedHookRecord(
    private val unhookObject: XC_MethodHook.Unhook?,
) : HookRecord {
    private val cleanedUp = AtomicBoolean()

    val hookedMember: Member?
        get() = unhookObject?.hookedMethod

    init {
        retainCallback(unhookObject?.callback as? AutoCloseable)
    }

    override fun cleanup() {
        if (!cleanedUp.compareAndSet(false, true)) {
            return
        }

        val unhook = unhookObject ?: return
        try {
            unhook.unhook()
        } catch (t: Throwable) {
            FileLog.e("Error during Xposed unhook cleanup", t)
        } finally {
            try {
                releaseCallback(unhook.callback as? AutoCloseable)
            } catch (t: Throwable) {
                FileLog.e("Error during Xposed hook callback cleanup", t)
            }
        }
    }

    override fun matches(criteria: Any?): Boolean {
        return criteria is XC_MethodHook.Unhook && unhookObject === criteria
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        other as XposedHookRecord
        return unhookObject === other.unhookObject
    }

    override fun hashCode(): Int {
        return unhookObject.hashCode()
    }

    companion object {
        private val callbackReferences = Collections.synchronizedMap(
            IdentityHashMap<AutoCloseable, Int>()
        )

        private fun retainCallback(callback: AutoCloseable?) {
            if (callback == null) {
                return
            }

            synchronized(callbackReferences) {
                callbackReferences[callback] = (callbackReferences[callback] ?: 0) + 1
            }
        }

        private fun releaseCallback(callback: AutoCloseable?) {
            if (callback == null) {
                return
            }

            var shouldClose = false
            synchronized(callbackReferences) {
                val count = callbackReferences[callback] ?: 0
                if (count <= 1) {
                    callbackReferences.remove(callback)
                    shouldClose = true
                } else {
                    callbackReferences[callback] = count - 1
                }
            }

            if (shouldClose) {
                callback.close()
            }
        }
    }
}