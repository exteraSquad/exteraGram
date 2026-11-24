/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.inline.math

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.roundToLong
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh

enum class UnitRule { SAME, SCALE, RATIO, NONE }

class InfixOperator(
    val symbols: List<String>,
    val precedence: Int,
    val units: UnitRule,
    val rightAssociative: Boolean = false,
    val apply: (left: Double, right: Double, rightIsPercent: Boolean) -> Double
)

class PrefixOperator(
    val symbols: List<String>,
    val precedence: Int,
    val operation: Boolean = false,
    val apply: (value: Double) -> Double
)

class PostfixOperator(
    val symbols: List<String>,
    val percent: Boolean = false,
    val apply: (value: Double) -> Double
)

enum class AngleUse { NONE, ARGUMENT, RESULT }

class MathFunction(
    val name: String,
    val arity: IntRange,
    val angle: AngleUse = AngleUse.NONE,
    val apply: (args: DoubleArray) -> Double
)

object MathOperators {

    const val PRECEDENCE_ADDITIVE = 10
    const val PRECEDENCE_MULTIPLICATIVE = 20
    const val PRECEDENCE_PREFIX = 30
    const val PRECEDENCE_POWER = 40

    const val ARGUMENT_SEPARATOR = ';'

    private const val MAX_FACTORIAL = 170

    val infix = listOf(
        InfixOperator(listOf("+"), PRECEDENCE_ADDITIVE, UnitRule.SAME) { left, right, percent ->
            if (percent) left + left * right else left + right
        },
        InfixOperator(listOf("-", "−"), PRECEDENCE_ADDITIVE, UnitRule.SAME) { left, right, percent ->
            if (percent) left - left * right else left - right
        },
        InfixOperator(listOf("*", "×"), PRECEDENCE_MULTIPLICATIVE, UnitRule.SCALE) { left, right, _ -> left * right },
        InfixOperator(listOf("/", "÷"), PRECEDENCE_MULTIPLICATIVE, UnitRule.RATIO) { left, right, _ -> left / right },
        InfixOperator(listOf("^"), PRECEDENCE_POWER, UnitRule.NONE, rightAssociative = true) { left, right, _ ->
            Math.pow(left, right)
        }
    )

    val prefix = listOf(
        PrefixOperator(listOf("-", "−"), PRECEDENCE_PREFIX) { value -> -value },
        PrefixOperator(listOf("+"), PRECEDENCE_PREFIX) { value -> value },
        PrefixOperator(listOf("√"), PRECEDENCE_POWER, operation = true) { value -> sqrt(value) }
    )

    val postfix = listOf(
        PostfixOperator(listOf("%"), percent = true) { value -> value / 100.0 },
        PostfixOperator(listOf("!")) { value -> factorial(value) }
    )

    val functions = listOf(
        MathFunction("sqrt", 1..1) { sqrt(it[0]) },
        MathFunction("cbrt", 1..1) { cbrt(it[0]) },
        MathFunction("abs", 1..1) { abs(it[0]) },
        MathFunction("sign", 1..1) { sign(it[0]) },
        MathFunction("round", 1..1) { it[0].roundToLong().toDouble() },
        MathFunction("floor", 1..1) { floor(it[0]) },
        MathFunction("ceil", 1..1) { ceil(it[0]) },
        MathFunction("fact", 1..1) { factorial(it[0]) },
        MathFunction("min", 1..Int.MAX_VALUE) { args -> args.min() },
        MathFunction("max", 1..Int.MAX_VALUE) { args -> args.max() },
        MathFunction("log", 1..1) { log10(it[0]) },
        MathFunction("log2", 1..1) { log2(it[0]) },
        MathFunction("ln", 1..1) { ln(it[0]) },
        MathFunction("exp", 1..1) { exp(it[0]) },
        MathFunction("sin", 1..1, AngleUse.ARGUMENT) { sin(it[0]) },
        MathFunction("cos", 1..1, AngleUse.ARGUMENT) { cos(it[0]) },
        MathFunction("tan", 1..1, AngleUse.ARGUMENT) { tan(it[0]) },
        MathFunction("asin", 1..1, AngleUse.RESULT) { asin(it[0]) },
        MathFunction("acos", 1..1, AngleUse.RESULT) { acos(it[0]) },
        MathFunction("atan", 1..1, AngleUse.RESULT) { atan(it[0]) },
        MathFunction("atan2", 2..2, AngleUse.RESULT) { atan2(it[0], it[1]) },
        MathFunction("sinh", 1..1) { sinh(it[0]) },
        MathFunction("cosh", 1..1) { cosh(it[0]) },
        MathFunction("tanh", 1..1) { tanh(it[0]) }
    )

    val multipliers: Map<String, Double> = buildMap {
        fun add(value: Double, vararg forms: String) = forms.forEach { put(it, value) }
        add(1e3, "к", "k", "тыс", "тысяча", "тысячи", "тысяч", "thousand", "thousands")
        add(1e6, "кк", "kk", "млн", "миллион", "миллиона", "миллионов", "million", "millions")
        add(1e9, "ккк", "kkk", "млрд", "миллиард", "миллиарда", "миллиардов", "billion", "billions")
    }

    val gluedMultipliers = setOf("к", "k", "кк", "kk", "ккк", "kkk")

    val percentOf = setOf("от", "of")

    const val DECIMAL = "dec"
    const val MIN_RADIX = 2
    const val MAX_RADIX = 36

    val bases = mapOf("bin" to 2, "oct" to 8, "hex" to 16, DECIMAL to 10)

    fun radixFor(prefix: Char): Int = when (prefix) {
        'b', 'B' -> 2
        'o', 'O' -> 8
        'x', 'X' -> 16
        else -> 0
    }

    fun stripRadixPrefix(digits: String, radix: Int): String =
        if (digits.length > 2 && digits[0] == '0' && radixFor(digits[1]) == radix) digits.substring(2) else digits

    val constants = mapOf(
        "pi" to Math.PI,
        "π" to Math.PI,
        "e" to Math.E
    )

    private val symbols: List<String> = buildList {
        infix.forEach { addAll(it.symbols) }
        prefix.forEach { addAll(it.symbols) }
        postfix.forEach { addAll(it.symbols) }
    }.distinct().sortedByDescending { it.length }

    val symbolChars: Set<Char> = buildSet {
        symbols.forEach { symbol -> symbol.forEach { add(it) } }
        constants.keys.forEach { name -> name.forEach { if (!it.isLetter()) add(it) } }
        add('(')
        add(')')
        add(ARGUMENT_SEPARATOR)
    }

    fun matchSymbol(text: CharSequence, at: Int): String? =
        symbols.firstOrNull { symbol ->
            at + symbol.length <= text.length && text.regionMatches(at, symbol, 0, symbol.length)
        }

    fun infixFor(symbol: String): InfixOperator? = infix.firstOrNull { symbol in it.symbols }

    fun prefixFor(symbol: String): PrefixOperator? = prefix.firstOrNull { symbol in it.symbols }

    fun postfixFor(symbol: String): PostfixOperator? = postfix.firstOrNull { symbol in it.symbols }

    fun functionFor(name: String): MathFunction? = functions.firstOrNull { it.name == name }

    private fun factorial(value: Double): Double {
        if (value < 0 || value != floor(value) || value > MAX_FACTORIAL) {
            return Double.NaN
        }
        var result = 1.0
        for (i in 2..value.toInt()) {
            result *= i
        }
        return result
    }
}
