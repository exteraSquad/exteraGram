/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.hooks

import com.exteragram.hook.HookHandle
import org.telegram.messenger.FileLog
import java.util.concurrent.atomic.AtomicBoolean

class PartialHookRecord(private val handle: HookHandle) : HookRecord {
    private val cleanedUp = AtomicBoolean()

    override fun cleanup() {
        if (!cleanedUp.compareAndSet(false, true)) {
            return
        }
        try {
            handle.unhook()
        } catch (t: Throwable) {
            FileLog.e("Error during partial hook cleanup", t)
        }
    }

    override fun matches(criteria: Any?): Boolean = criteria === handle

    override fun equals(other: Any?): Boolean = other is PartialHookRecord && other.handle === handle

    override fun hashCode(): Int = System.identityHashCode(handle)
}
