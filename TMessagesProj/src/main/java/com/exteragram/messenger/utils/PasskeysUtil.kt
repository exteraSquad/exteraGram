/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils

import android.app.Activity
import android.os.Build
import androidx.credentials.CredentialManager
import org.json.JSONException
import org.json.JSONObject
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.messenger.browser.Browser
import org.telegram.ui.Components.Bulletin
import org.telegram.ui.Components.BulletinFactory
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object PasskeysUtil {
    private const val TYPE_GET = "webauthn.get"
    private const val TYPE_CREATE = "webauthn.create"
    const val BITWARDEN_URL = "https://github.com/bitwarden/android"
    const val KEEPASSDX_URL = "https://github.com/Kunzisoft/KeePassDX"

    @JvmStatic
    fun showUnsupportedBulletin(factory: BulletinFactory): Bulletin =
        factory.createSimpleBulletin(
            R.raw.error,
            getString(R.string.PasskeyUnsupportedTitle),
            AndroidUtilities.replaceMultipleTags(
                getString(R.string.PasskeyUnsupportedMessage),
                { Browser.openUrl(ApplicationLoader.applicationContext, BITWARDEN_URL) },
                { Browser.openUrl(ApplicationLoader.applicationContext, KEEPASSDX_URL) })
        ).show()

    @JvmStatic
    fun computeClientDataHash(clientDataJSON: String): ByteArray =
        MessageDigest.getInstance("SHA-256")
            .digest(clientDataJSON.toByteArray(StandardCharsets.UTF_8))

    @JvmStatic
    @Throws(JSONException::class)
    fun generateClientDataJSONRaw(get: Boolean, challenge: String?, rpId: String?) =
        JSONObject().put("type", if (get) TYPE_GET else TYPE_CREATE)
            .put("challenge", challenge)
            .put("origin", rpId).toString()

    @JvmStatic
    fun openSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return
        }
        runCatching {
            CredentialManager.create(activity)
                .createSettingsPendingIntent()
                .send()
        }
    }
}