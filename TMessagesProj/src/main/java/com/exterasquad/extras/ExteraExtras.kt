package com.exterasquad.extras

import android.graphics.drawable.BitmapDrawable
import androidx.annotation.ColorInt
import org.telegram.messenger.SharedConfig

object ExteraExtras {

    var exteraVersion = "8.2.7"
    var exteraCodename = "sudo"
    @JvmField
    var currentAccountBitmap: BitmapDrawable? = null

    @JvmStatic
    @get:ColorInt
    val lightStatusbarColor: Int
        get() = if (SharedConfig.noStatusBar) {
            0x00000000
        } else {
            0x0f000000
        }

    @JvmStatic
    @get:ColorInt
    val darkStatusbarColor: Int
        get() = if (SharedConfig.noStatusBar) {
            0x00000000
        } else {
            0x33000000
        }

}