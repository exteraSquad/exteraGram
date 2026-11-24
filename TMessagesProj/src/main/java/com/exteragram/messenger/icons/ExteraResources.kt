/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.icons

import android.content.res.AssetManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import com.exteragram.messenger.icons.ui.picker.IconObserver
import org.telegram.messenger.FileLog

@Suppress("DEPRECATION")
class ExteraResources(private val mResources: Resources) : Resources(
    mResources.assets, mResources.displayMetrics, mResources.configuration
) {
    val sourceAssets: AssetManager = mResources.assets

    init {
        IconManager.initialize()
    }

    override fun getDrawableForDensity(id: Int, density: Int, theme: Theme?): Drawable? {
        IconObserver.log(id)
        IconManager.getDrawable(id, density, theme, this)?.let {
            return it
        }

        val iconId = try {
            IconManager.getIcon(id)
        } catch (e: Throwable) {
            FileLog.e(e)
            id
        }
        return mResources.getDrawableForDensity(iconId, density, theme)
    }

    @Throws(NotFoundException::class)
    fun getOriginalDrawable(@DrawableRes id: Int): Drawable? {
        return try {
            mResources.getDrawable(id, null)
        } catch (_: NotFoundException) {
            null
        }
    }
}