/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.utils.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.PatternMatcher
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import java.util.regex.Pattern

object MonetUtils {

    private val COLOR_MAP = hashMapOf<String, Int>()
    private val ACCENT_PREFIXES = arrayOf("a1_", "a2_", "a3_")

    private val PARAM_PATTERN = Pattern.compile("^([^(]+)\\(([^)]+)\\)?$")

    private const val ACTION_OVERLAY_CHANGED = "android.intent.action.OVERLAY_CHANGED"
    private val overlayChangeReceiver = OverlayChangeReceiver()

    private const val KEY_ALPHA = "a"
    private const val KEY_SATURATION = "s"
    private const val KEY_LIGHTNESS = "l"

    @Volatile
    private var harmonizeContextColor = 0

    init {
        COLOR_MAP["mBlack"] = R.color.black
        COLOR_MAP["mWhite"] = R.color.white

        COLOR_MAP["mRed200"] = R.color.mRed200
        COLOR_MAP["mRed500"] = R.color.mRed500
        COLOR_MAP["mRed800"] = R.color.mRed800
        COLOR_MAP["mGreen200"] = R.color.mGreen200
        COLOR_MAP["mGreen500"] = R.color.mGreen500
        COLOR_MAP["mGreen800"] = R.color.mGreen800

        if (isSupported()) {
            initSystemColors()
        }
    }

    @JvmStatic
    @ColorInt
    fun getColor(colorString: String): Int {
        if (colorString.isEmpty()) return 0

        try {
            var colorKey = colorString
            var alpha = 100
            var saturation = 100
            var lightness = 100

            val matcher = PARAM_PATTERN.matcher(colorString)
            if (matcher.find()) {
                colorKey = matcher.group(1)?.trim() ?: colorString
                val params = matcher.group(2)

                if (params != null) {
                    val pairs = params.split(",")
                    for (pair in pairs) {
                        val kv = pair.split("=")
                        if (kv.size == 2) {
                            try {
                                val key = kv[0].trim()
                                val value = kv[1].trim().toInt()

                                when (key) {
                                    KEY_ALPHA -> alpha = value
                                    KEY_SATURATION -> saturation = value
                                    KEY_LIGHTNESS -> lightness = value
                                }
                            } catch (_: NumberFormatException) {
                            }
                        }
                    }
                }
            }

            val resId = COLOR_MAP[colorKey]
            if (resId == null || resId == 0) {
                return 0
            }

            var colorValue = ApplicationLoader.applicationContext.getColor(resId)

            if (saturation != 100) {
                colorValue = ColorUtils.blendARGB(Color.WHITE, colorValue, saturation / 100f)
            }
            if (lightness != 100) {
                colorValue = ColorUtils.blendARGB(Color.BLACK, colorValue, lightness / 100f)
            }
            if (alpha != 100) {
                colorValue = ColorUtils.setAlphaComponent(colorValue, (alpha * 2.55f).toInt())
            }

            if (colorKey.startsWith("mR") || colorKey.startsWith("mG")) {
                colorValue = harmonize(colorValue)
            }

            return colorValue

        } catch (e: Exception) {
            FileLog.e(e)
            return 0
        }
    }

    @JvmStatic
    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    @JvmStatic
    @ColorInt
    fun getSystemAccentColor(index: Int, isDark: Boolean): Int {
        if (!isSupported() || index !in ACCENT_PREFIXES.indices) {
            return 0
        }
        val tone = if (isDark) 200 else 600
        return getColor(ACCENT_PREFIXES[index] + tone)
    }

    @JvmStatic
    @ColorInt
    fun getSecondaryContainerColor(isDark: Boolean): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return ApplicationLoader.applicationContext.getColor(
                if (isDark) android.R.color.system_secondary_container_dark else android.R.color.system_secondary_container_light
            )
        }
        return getColor(if (isDark) "a2_700" else "a2_100")
    }

    @JvmStatic
    @ColorInt
    fun getOnSecondaryContainerColor(isDark: Boolean): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return ApplicationLoader.applicationContext.getColor(
                if (isDark) android.R.color.system_on_secondary_container_dark else android.R.color.system_on_secondary_container_light
            )
        }
        return getColor(if (isDark) "a2_100" else "a2_900")
    }

    @JvmStatic
    @ColorInt
    fun harmonize(@ColorInt color: Int): Int {
        val contextColor = getHarmonizeContextColor()
        if (contextColor == 0) {
            return color
        }
        return MaterialColors.harmonize(color, contextColor)
    }

    @JvmStatic
    @ColorInt
    fun getHarmonizeContextColor(): Int {
        if (!isSupported()) {
            return 0
        }
        var color = harmonizeContextColor
        if (color == 0) {
            color = ApplicationLoader.applicationContext.getColor(android.R.color.system_accent1_600)
            harmonizeContextColor = color
        }
        return color
    }

    @JvmStatic
    fun registerReceiver(context: Context) {
        try {
            harmonizeContextColor = 0
            overlayChangeReceiver.register(context)
        } catch (e: Exception) {
            FileLog.e(e)
        }
    }

    @JvmStatic
    fun unregisterReceiver(context: Context) {
        try {
            overlayChangeReceiver.unregister(context)
        } catch (_: Exception) {
        }
    }

    private class OverlayChangeReceiver : BroadcastReceiver() {
        private var isRegistered = false

        fun register(context: Context) {
            if (isRegistered) return
            val packageFilter = IntentFilter(ACTION_OVERLAY_CHANGED)
            packageFilter.addDataScheme("package")
            packageFilter.addDataSchemeSpecificPart("android", PatternMatcher.PATTERN_LITERAL)
            context.registerReceiver(this, packageFilter)
            isRegistered = true
        }

        fun unregister(context: Context) {
            if (!isRegistered) return
            context.unregisterReceiver(this)
            isRegistered = false
        }

        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_OVERLAY_CHANGED == intent.action) {
                harmonizeContextColor = 0
                Theme.refreshMonetColors()
                if (Theme.isCurrentThemeMonet() || Theme.isCurrentAccentMonet()) {
                    Theme.applyTheme(Theme.getActiveTheme(), Theme.isCurrentThemeNight())
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun initSystemColors() {
        COLOR_MAP["a1_10"] = android.R.color.system_accent1_10
        COLOR_MAP["a1_50"] = android.R.color.system_accent1_50
        COLOR_MAP["a1_100"] = android.R.color.system_accent1_100
        COLOR_MAP["a1_200"] = android.R.color.system_accent1_200
        COLOR_MAP["a1_300"] = android.R.color.system_accent1_300
        COLOR_MAP["a1_400"] = android.R.color.system_accent1_400
        COLOR_MAP["a1_500"] = android.R.color.system_accent1_500
        COLOR_MAP["a1_600"] = android.R.color.system_accent1_600
        COLOR_MAP["a1_700"] = android.R.color.system_accent1_700
        COLOR_MAP["a1_800"] = android.R.color.system_accent1_800
        COLOR_MAP["a1_900"] = android.R.color.system_accent1_900

        COLOR_MAP["a2_10"] = android.R.color.system_accent2_10
        COLOR_MAP["a2_50"] = android.R.color.system_accent2_50
        COLOR_MAP["a2_100"] = android.R.color.system_accent2_100
        COLOR_MAP["a2_200"] = android.R.color.system_accent2_200
        COLOR_MAP["a2_300"] = android.R.color.system_accent2_300
        COLOR_MAP["a2_400"] = android.R.color.system_accent2_400
        COLOR_MAP["a2_500"] = android.R.color.system_accent2_500
        COLOR_MAP["a2_600"] = android.R.color.system_accent2_600
        COLOR_MAP["a2_700"] = android.R.color.system_accent2_700
        COLOR_MAP["a2_800"] = android.R.color.system_accent2_800
        COLOR_MAP["a2_900"] = android.R.color.system_accent2_900

        COLOR_MAP["a3_10"] = android.R.color.system_accent3_10
        COLOR_MAP["a3_50"] = android.R.color.system_accent3_50
        COLOR_MAP["a3_100"] = android.R.color.system_accent3_100
        COLOR_MAP["a3_200"] = android.R.color.system_accent3_200
        COLOR_MAP["a3_300"] = android.R.color.system_accent3_300
        COLOR_MAP["a3_400"] = android.R.color.system_accent3_400
        COLOR_MAP["a3_500"] = android.R.color.system_accent3_500
        COLOR_MAP["a3_600"] = android.R.color.system_accent3_600
        COLOR_MAP["a3_700"] = android.R.color.system_accent3_700
        COLOR_MAP["a3_800"] = android.R.color.system_accent3_800
        COLOR_MAP["a3_900"] = android.R.color.system_accent3_900

        COLOR_MAP["n1_10"] = android.R.color.system_neutral1_10
        COLOR_MAP["n1_50"] = android.R.color.system_neutral1_50
        COLOR_MAP["n1_100"] = android.R.color.system_neutral1_100
        COLOR_MAP["n1_200"] = android.R.color.system_neutral1_200
        COLOR_MAP["n1_300"] = android.R.color.system_neutral1_300
        COLOR_MAP["n1_400"] = android.R.color.system_neutral1_400
        COLOR_MAP["n1_500"] = android.R.color.system_neutral1_500
        COLOR_MAP["n1_600"] = android.R.color.system_neutral1_600
        COLOR_MAP["n1_700"] = android.R.color.system_neutral1_700
        COLOR_MAP["n1_800"] = android.R.color.system_neutral1_800
        COLOR_MAP["n1_900"] = android.R.color.system_neutral1_900

        COLOR_MAP["n2_10"] = android.R.color.system_neutral2_10
        COLOR_MAP["n2_50"] = android.R.color.system_neutral2_50
        COLOR_MAP["n2_100"] = android.R.color.system_neutral2_100
        COLOR_MAP["n2_200"] = android.R.color.system_neutral2_200
        COLOR_MAP["n2_300"] = android.R.color.system_neutral2_300
        COLOR_MAP["n2_400"] = android.R.color.system_neutral2_400
        COLOR_MAP["n2_500"] = android.R.color.system_neutral2_500
        COLOR_MAP["n2_600"] = android.R.color.system_neutral2_600
        COLOR_MAP["n2_700"] = android.R.color.system_neutral2_700
        COLOR_MAP["n2_800"] = android.R.color.system_neutral2_800
        COLOR_MAP["n2_900"] = android.R.color.system_neutral2_900
    }
}
