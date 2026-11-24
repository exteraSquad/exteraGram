/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.debug

import android.app.Activity
import android.content.Intent
import androidx.core.content.FileProvider
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import java.io.File

internal fun debugFilesDir(name: String): File {
    val context = ApplicationLoader.applicationContext
    return File(context.getExternalFilesDir(null) ?: context.cacheDir, name)
}

internal fun shareDebugFile(activity: Activity, file: File, mimeType: String, title: String) {
    try {
        val uri = FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", file)
        val intent = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.startActivity(Intent.createChooser(intent, title))
    } catch (e: Exception) {
        FileLog.e(e)
    }
}
