/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.utils

import android.app.Activity
import androidx.core.content.edit
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import java.io.File

object NativeCrashHandler {
    private const val CRASH_FLAG_FILENAME = "native_crash.flag"

    @JvmStatic
    external fun init(flagPath: String)

    @JvmStatic
    fun checkAndHandleNativeCrash() {
        val flagFile = File(ApplicationLoader.getFilesDirFixed(), CRASH_FLAG_FILENAME)
        if (flagFile.exists()) {
            FileLog.e("Native crash detected. Enabling safe mode for plugins.")
            val preferences = ApplicationLoader.applicationContext.getSharedPreferences(
                "plugin_settings",
                Activity.MODE_PRIVATE
            )
            preferences.edit { putBoolean("had_crash", true) }
            flagFile.delete()
        }
    }

    @JvmStatic
    fun getCrashFlagPath(): String {
        return File(ApplicationLoader.getFilesDirFixed(), CRASH_FLAG_FILENAME).absolutePath
    }
}