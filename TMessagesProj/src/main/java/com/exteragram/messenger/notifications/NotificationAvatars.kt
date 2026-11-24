/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.notifications

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.NotificationsController
import org.telegram.messenger.UserObject
import org.telegram.tgnet.TLObject
import org.telegram.ui.Components.AvatarDrawable
import java.io.File

object NotificationAvatars {

    @JvmStatic
    fun loadAvatar(currentAccount: Int, dialogId: Long, avatar: File?, peer: TLObject?, personBuilder: Person.Builder) {
        if (dialogId == UserObject.OAUTH || avatar?.exists() == true) {
            NotificationsController.loadRoundAvatar(dialogId, avatar, personBuilder)
            return
        }

        if (peer == null) {
            return
        }

        val size = AndroidUtilities.dp(64f)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

        val avatarDrawable = AvatarDrawable()
        avatarDrawable.setInfo(currentAccount, peer)
        avatarDrawable.setBounds(0, 0, size, size)
        avatarDrawable.draw(Canvas(bitmap))

        personBuilder.setIcon(IconCompat.createWithBitmap(bitmap))
    }
}
