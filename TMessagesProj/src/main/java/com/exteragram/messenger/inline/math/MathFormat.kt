/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.inline.math

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

object MathFormat {

    const val NO_BREAK_SPACE = '\u00A0'
    const val MAX_EXACT_INTEGER = 1L shl 53

    private const val SIGNIFICANT_DIGITS = 12
    private const val MAX_ABSOLUTE = 1e12
    private const val MIN_ABSOLUTE = 1e-9
    private const val MAX_LENGTH = 24
    private const val MONEY_SIGNIFICANT_DIGITS = 6
    private const val MEASURE_FRACTION_DIGITS = 2
    private const val MEASURE_SIGNIFICANT_DIGITS = 3

    fun format(value: Double, decimalSeparator: Char, grouping: Boolean = false): String? {
        if (value.isNaN() || value.isInfinite()) {
            return null
        }
        val magnitude = abs(value)
        if (magnitude >= MAX_ABSOLUTE) {
            return null
        }
        if (magnitude != 0.0 && magnitude < MIN_ABSOLUTE) {
            return null
        }
        val text = BigDecimal.valueOf(value)
            .round(MathContext(SIGNIFICANT_DIGITS))
            .stripTrailingZeros()
            .toPlainString()
        return finish(if (grouping) group(text) else text, decimalSeparator)
    }

    fun formatMoney(value: Double, fractionDigits: Int, decimalSeparator: Char, grouping: Boolean = true): String? {
        if (value.isNaN() || value.isInfinite()) {
            return null
        }
        val magnitude = abs(value)
        if (magnitude >= MAX_ABSOLUTE) {
            return null
        }
        val decimal = BigDecimal.valueOf(value)
        if (fractionDigits >= 0) {
            val rounded = decimal.setScale(fractionDigits, RoundingMode.HALF_UP)
            if (rounded.signum() != 0 || magnitude == 0.0) {
                val whole = rounded.stripTrailingZeros().scale() <= 0
                val text = (if (whole) rounded.setScale(0) else rounded).toPlainString()
                return finish(if (grouping) group(text) else text, decimalSeparator)
            }
        }
        if (magnitude != 0.0 && magnitude < MIN_ABSOLUTE) {
            return null
        }
        val integerDigits = decimal.precision() - decimal.scale()
        val text = decimal
            .round(MathContext(max(MONEY_SIGNIFICANT_DIGITS, integerDigits)))
            .stripTrailingZeros()
            .toPlainString()
        return finish(if (grouping) group(text) else text, decimalSeparator)
    }

    fun formatMeasure(value: Double, decimalSeparator: Char, grouping: Boolean): String? {
        if (value.isNaN() || value.isInfinite()) {
            return null
        }
        val magnitude = abs(value)
        if (magnitude >= MAX_ABSOLUTE) {
            return null
        }
        if (magnitude != 0.0 && magnitude < MIN_ABSOLUTE) {
            return null
        }
        val decimal = BigDecimal.valueOf(value)
        val rounded = if (magnitude >= 1) {
            decimal.setScale(MEASURE_FRACTION_DIGITS, RoundingMode.HALF_UP)
        } else {
            decimal.round(MathContext(MEASURE_SIGNIFICANT_DIGITS))
        }
        val text = rounded.stripTrailingZeros().toPlainString()
        return finish(if (grouping) group(text) else text, decimalSeparator)
    }

    fun formatBase(value: Double, radix: Int): String? {
        if (value.isNaN() || value.isInfinite() || value != floor(value) || abs(value) > MAX_EXACT_INTEGER) {
            return null
        }
        val integer = value.toLong()
        val prefix = when (radix) {
            2 -> "0b"
            8 -> "0o"
            16 -> "0x"
            else -> ""
        }
        val digits = java.lang.Long.toString(abs(integer), radix).uppercase(Locale.ROOT)
        return (if (integer < 0) "-" else "") + prefix + digits
    }

    private fun group(plain: String): String {
        val start = if (plain.startsWith('-')) 1 else 0
        val point = plain.indexOf('.').let { if (it < 0) plain.length else it }
        if (point - start <= 3) {
            return plain
        }
        val grouped = StringBuilder(plain.length + (point - start - 1) / 3)
        grouped.append(plain, 0, start)
        for (index in start until point) {
            if (index > start && (point - index) % 3 == 0) {
                grouped.append(NO_BREAK_SPACE)
            }
            grouped.append(plain[index])
        }
        grouped.append(plain, point, plain.length)
        return grouped.toString()
    }

    private fun finish(plain: String, decimalSeparator: Char): String? {
        val text = if (plain == "-0") "0" else plain
        if (text.length > MAX_LENGTH) {
            return null
        }
        return if (decimalSeparator == '.') text else text.replace('.', decimalSeparator)
    }
}
