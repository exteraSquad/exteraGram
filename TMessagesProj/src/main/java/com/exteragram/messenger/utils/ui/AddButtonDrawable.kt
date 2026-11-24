/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui

import android.content.Context
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import org.telegram.messenger.AndroidUtilities.dp
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.CombinedDrawable

class AddButtonDrawable private constructor(
    private val circle: Drawable,
    private val plus: Drawable
) : CombinedDrawable(circle, plus) {

    constructor(context: Context) : this(
        requireNotNull(ContextCompat.getDrawable(context, R.drawable.poll_add_circle)).mutate(),
        requireNotNull(ContextCompat.getDrawable(context, R.drawable.poll_add_plus)).mutate()
    )

    init {
        translateX = dp(2f).toFloat()
    }

    fun updateColors(resourcesProvider: Theme.ResourcesProvider?) {
        circle.colorFilter = PorterDuffColorFilter(Theme.getColor(Theme.key_switchTrackChecked, resourcesProvider), PorterDuff.Mode.MULTIPLY)
        plus.colorFilter = PorterDuffColorFilter(Theme.getColor(Theme.key_checkboxCheck, resourcesProvider), PorterDuff.Mode.MULTIPLY)
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {}
}
