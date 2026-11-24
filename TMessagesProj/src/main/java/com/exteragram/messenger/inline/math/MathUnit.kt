/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.inline.math

enum class UnitKind { CURRENCY, LENGTH, MASS, VOLUME, AREA, TEMPERATURE, SPEED, DURATION }

class MathUnit(
    val code: String,
    val label: String,
    val kind: UnitKind = UnitKind.CURRENCY,
    val scale: Double = 1.0,
    val offset: Double = 0.0,
    val russianLabel: String = label
) {
    fun label(russian: Boolean): String = if (russian) russianLabel else label

    fun toBase(value: Double): Double = value * scale + offset

    fun fromBase(value: Double): Double = (value - offset) / scale
}
