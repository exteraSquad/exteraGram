/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.inline

import com.exteragram.messenger.AngleUnit
import com.exteragram.messenger.MeasureSystem
import com.exteragram.messenger.inline.math.MathOptions
import com.exteragram.messenger.inline.time.TimeZoneOptions
import com.exteragram.messenger.inlineAngleUnit
import com.exteragram.messenger.inlineCurrencyRates
import com.exteragram.messenger.inlineDigitGrouping
import com.exteragram.messenger.inlineMathResult
import com.exteragram.messenger.inlineMeasureSystem
import com.exteragram.messenger.inlineResults
import com.exteragram.messenger.inlineTimeZones
import com.exteragram.messenger.inlineUnitConversion

import org.telegram.messenger.LocaleController

import java.time.ZoneId

object InlineResults {

    private val imperialRegions = setOf("US", "LR", "MM")

    fun isImperial(): Boolean = when (inlineMeasureSystem) {
        MeasureSystem.AUTO -> LocaleController.getInstance().systemDefaultLocale?.country in imperialRegions
        MeasureSystem.METRIC -> false
        MeasureSystem.IMPERIAL -> true
    }

    fun isEnabled(): Boolean =
        inlineResults && (inlineMathResult || inlineCurrencyRates || inlineUnitConversion || inlineTimeZones)

    fun options(rates: InlineRates, decimalSeparator: Char, allFeatures: Boolean = false): MathOptions {
        val currencies = allFeatures || inlineCurrencyRates
        return MathOptions(
            decimalSeparator,
            if (currencies) rates else null,
            if (currencies) rates.localCurrency() else null,
            allFeatures || inlineMathResult,
            allFeatures || inlineUnitConversion,
            inlineAngleUnit == AngleUnit.DEGREES,
            inlineDigitGrouping,
            if (allFeatures || inlineTimeZones) TimeZoneOptions(ZoneId.systemDefault(), places = InlineZonePlaces::lookup) else null,
            isImperial()
        )
    }
}
