package com.exteragram.extras

import androidx.annotation.ColorInt
import org.telegram.messenger.SharedConfig

object ExteraExtras {

    var exteraVersion = "8.4.2"
    var exteraCodename = "cattus"

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