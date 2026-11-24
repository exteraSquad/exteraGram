/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.system

import android.app.Application
import android.os.Build
import java.io.File

object AppProcess {
    const val PLUGIN_ANALYZER_SUFFIX = ":analyzer"

    @JvmStatic
    val name: String by lazy { readName() }

    @JvmStatic
    val isPluginAnalyzer: Boolean
        get() = name.endsWith(PLUGIN_ANALYZER_SUFFIX)

    private fun readName(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName().orEmpty()
        }
        return try {
            val bytes = File("/proc/self/cmdline").readBytes()
            val end = bytes.indexOf(0.toByte()).let { if (it < 0) bytes.size else it }
            String(bytes, 0, end, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }
}
