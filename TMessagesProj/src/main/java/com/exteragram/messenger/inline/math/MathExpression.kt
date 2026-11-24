/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.inline.math

import com.exteragram.messenger.inline.rates.Currencies
import com.exteragram.messenger.inline.rates.CurrencyRates
import com.exteragram.messenger.inline.time.TimeZoneOptions
import com.exteragram.messenger.inline.time.TimeZones
import com.exteragram.messenger.inline.units.Measures

import kotlin.math.abs

class MathOptions(
    val decimalSeparator: Char = '.',
    val rates: CurrencyRates? = null,
    val localCurrency: String? = null,
    val arithmetic: Boolean = true,
    val units: Boolean = false,
    val degrees: Boolean = false,
    val grouping: Boolean = true,
    val timeZones: TimeZoneOptions? = null,
    val imperial: Boolean = false
)

class MathResult(
    val value: Double,
    val hasOperation: Boolean,
    val decimalSeparator: Char,
    val unit: MathUnit? = null,
    val russian: Boolean = false,
    val breakdown: Boolean = false,
    val grouping: Boolean = true,
    val base: Int = 10
)

class MathSuggestion(
    val insertAt: Int,
    val insertText: String,
    val value: String
)

object MathExpression {

    private const val MAX_SCAN = 64
    private const val MAX_DEPTH = 32
    private const val TRIG_EPSILON = 1e-12

    private const val TYPE_NUMBER = 0
    private const val TYPE_SYMBOL = 1
    private const val TYPE_LEFT_PAREN = 2
    private const val TYPE_RIGHT_PAREN = 3
    private const val TYPE_IDENTIFIER = 4
    private const val TYPE_ARGUMENT_SEPARATOR = 5
    private const val TYPE_UNIT = 6
    private const val TYPE_CONVERSION = 7
    private const val TYPE_OF = 8
    private const val TYPE_DIGITS = 9
    private const val TYPE_END = 10

    private const val VARIATION_SELECTOR = '\uFE0F'
    private const val SUPERSCRIPT_DIGITS = "⁰¹²³⁴⁵⁶⁷⁸⁹"

    private class MathParseError : RuntimeException(null, null, false, false)

    private val error = MathParseError()

    private class Token(val type: Int, val number: Double = 0.0, val text: String = "", val units: List<MathUnit> = emptyList()) {
        var unit: MathUnit? = units.firstOrNull()
    }

    private class Operand(val value: Double, val isPercent: Boolean, val unit: MathUnit? = null)

    fun evaluate(expression: CharSequence, options: MathOptions = MathOptions()): MathResult? = try {
        Parser(expression, options).parse()
    } catch (e: MathParseError) {
        null
    }

    fun format(result: MathResult): String? {
        if (result.base != 10) {
            return MathFormat.formatBase(result.value, result.base)
        }
        val unit = result.unit ?: return MathFormat.format(result.value, result.decimalSeparator, result.grouping)
        val number = when {
            unit.kind == UnitKind.CURRENCY -> MathFormat.formatMoney(
                result.value, Currencies.fractionDigits(unit.code), result.decimalSeparator, result.grouping
            )
            result.breakdown -> return Measures.formatDuration(unit.toBase(result.value), result.decimalSeparator, result.russian)
            else -> MathFormat.formatMeasure(result.value, result.decimalSeparator, result.grouping)
        }
        return number?.let { "$it${MathFormat.NO_BREAK_SPACE}${unit.label(result.russian)}" }
    }

    fun suggestionAt(text: CharSequence, caret: Int, options: MathOptions): MathSuggestion? {
        if (caret <= 1 || caret > text.length || text[caret - 1] != '=') {
            return null
        }
        var after = caret
        while (after < text.length && isBlank(text[after])) {
            after++
        }
        if (after < text.length) {
            val next = text[after]
            if (next.isDigit() || next == '.' || next == ',') {
                return null
            }
        }
        val equals = caret - 1
        options.timeZones?.let { zones ->
            val time = TimeZones.valueAt(text, equals, zones)
            if (time != null) {
                return suggestion(text, caret, time)
            }
        }
        var start = expressionStart(text, equals) ?: return null
        while (true) {
            if (!looksLikeDate(text, start, equals)) {
                val result = evaluate(StringBuilder(equals - start).append(text, start, equals), options)
                if (result != null) {
                    if (!result.hasOperation || result.unit == null && !options.arithmetic) {
                        return null
                    }
                    return suggestion(text, caret, format(result) ?: return null)
                }
            }
            start = nextCandidate(text, start, equals) ?: return null
        }
    }

    private fun suggestion(text: CharSequence, caret: Int, value: String): MathSuggestion {
        val spaced = isBlank(text[caret - 2])
        return MathSuggestion(caret, if (spaced) " $value" else value, value)
    }

    private fun looksLikeDate(text: CharSequence, from: Int, to: Int): Boolean {
        var start = from
        var end = to
        while (start < end && isBlank(text[start])) {
            start++
        }
        while (end > start && isBlank(text[end - 1])) {
            end--
        }
        if (end - start < 6) {
            return false
        }
        val groups = ArrayList<Int>(4)
        var separator = ' '
        var digits = 0
        for (index in start until end) {
            val c = text[index]
            if (c.isDigit()) {
                digits++
                continue
            }
            if (c != '/' && c != '.' && c != '-') {
                return false
            }
            if (separator == ' ') {
                separator = c
            } else if (separator != c) {
                return false
            }
            if (digits == 0 || groups.size == 2) {
                return false
            }
            groups.add(digits)
            digits = 0
        }
        if (digits == 0 || groups.size != 2) {
            return false
        }
        groups.add(digits)
        return when (separator) {
            '/', '.' -> groups[0] <= 2 && groups[1] <= 2 && (groups[2] == 2 || groups[2] == 4)
            '-' -> groups[0] == 4 && groups[1] <= 2 && groups[2] <= 2
            else -> false
        }
    }

    private fun nextCandidate(text: CharSequence, from: Int, end: Int): Int? {
        var index = from
        var previous = ' '
        while (index < end) {
            val c = text[index]
            index++
            if (c == '(') {
                return index
            }
            if (isBlank(c)) {
                while (index < end && isBlank(text[index])) {
                    index++
                }
                if (index >= end) {
                    return null
                }
                if (c == MathFormat.NO_BREAK_SPACE && previous.isDigit() && text[index].isDigit()) {
                    continue
                }
                return index
            }
            previous = c
        }
        return null
    }

    private fun expressionStart(text: CharSequence, equalsIndex: Int): Int? {
        val limit = maxOf(0, equalsIndex - MAX_SCAN)
        var index = equalsIndex - 1
        while (index >= limit && isExpressionChar(text[index])) {
            index--
        }
        var start = index + 1
        while (start < equalsIndex && isBlank(text[start])) {
            start++
        }
        if (start >= equalsIndex) {
            return null
        }
        if (start > 0) {
            val before = text[start - 1]
            if (before.isLetter()) {
                while (start < equalsIndex && !isBlank(text[start])) {
                    start++
                }
                while (start < equalsIndex && isBlank(text[start])) {
                    start++
                }
                if (start >= equalsIndex) {
                    return null
                }
            } else if (before.isDigit() || before == '_') {
                return null
            }
        }
        return start
    }

    private fun isBlank(c: Char): Boolean = c == ' ' || c == '\t' || c == MathFormat.NO_BREAK_SPACE

    private fun isWordChar(c: Char): Boolean = c.isLetter() || c.isDigit()

    private fun isSuperscript(c: Char): Boolean = c in SUPERSCRIPT_DIGITS || c == '⁺' || c == '⁻'

    private fun isExpressionChar(c: Char): Boolean =
        c.isDigit() || c.isLetter() || c == '.' || c == ',' || isBlank(c) ||
                c == '°' || c == '℃' || c == '℉' || c == VARIATION_SELECTOR || isSuperscript(c) ||
                c in MathOperators.symbolChars || c in Currencies.symbolChars

    private class Parser(private val source: CharSequence, private val options: MathOptions) {

        private val tokens = ArrayList<Token>()
        private var position = 0
        private var depth = 0
        private var hasOperation = false
        private var expanded = false
        private var explicitTarget = false
        private var base = 10
        private var separator: Char? = null
        private val russian = source.any { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }

        fun parse(): MathResult {
            tokenize()
            resolveUnits()
            var result = parseConversion()
            expect(TYPE_END)
            var breakdown = false
            val unit = result.unit
            if (unit != null) {
                if (unit.kind == UnitKind.DURATION) {
                    breakdown = !explicitTarget && Measures.canBreakDown(unit)
                    if (!hasOperation && breakdown) {
                        hasOperation = Measures.breaksDown(result.value, unit)
                    }
                } else if (!hasOperation) {
                    val target = implicitTarget(unit, result.value)
                    if (target != null) {
                        result = convert(result, target)
                    }
                }
            }
            if (expanded) {
                hasOperation = true
            }
            return MathResult(
                result.value, hasOperation, separator ?: options.decimalSeparator,
                result.unit, russian, breakdown, options.grouping, base
            )
        }

        private fun implicitTarget(unit: MathUnit, value: Double): MathUnit? {
            if (unit.kind != UnitKind.CURRENCY) {
                return Measures.implicitTarget(unit, value, options.imperial)
            }
            val local = options.localCurrency ?: Currencies.FALLBACK
            val target = if (local == unit.code) Currencies.FALLBACK else local
            return if (target == unit.code) null else Currencies.unit(target)
        }

        private fun tokenize() {
            var index = 0
            while (index < source.length) {
                val c = source[index]
                if (isBlank(c) || c == VARIATION_SELECTOR) {
                    index++
                    continue
                }
                if (c.isDigit() || ((c == '.' || c == ',') && index + 1 < source.length && source[index + 1].isDigit())) {
                    index = readNumber(index)
                    continue
                }
                if (isSuperscript(c)) {
                    index = readSuperscript(index)
                    continue
                }
                if (c == '(') {
                    tokens.add(Token(TYPE_LEFT_PAREN))
                    index++
                    continue
                }
                if (c == ')') {
                    tokens.add(Token(TYPE_RIGHT_PAREN))
                    index++
                    continue
                }
                if (c == MathOperators.ARGUMENT_SEPARATOR || c == ',' && (index + 1 == source.length || isBlank(source[index + 1]))) {
                    tokens.add(Token(TYPE_ARGUMENT_SEPARATOR))
                    index++
                    continue
                }
                if (c == '°') {
                    index = readTemperature(index)
                    continue
                }
                if (c.isLetter()) {
                    val from = index
                    index = unitWordEnd(index)
                    index = addWord(source.substring(from, index), index)
                    continue
                }
                val unit = (if (options.units) Measures.symbol(c) else null)
                    ?: options.rates?.let { Currencies.symbol(c, it) }
                if (unit != null) {
                    tokens.add(Token(TYPE_UNIT, units = listOf(unit)))
                    index++
                    continue
                }
                val symbol = MathOperators.matchSymbol(source, index) ?: throw error
                tokens.add(Token(TYPE_SYMBOL, text = symbol))
                index += symbol.length
            }
            tokens.add(Token(TYPE_END))
        }

        private fun resolveUnits() {
            val kinds = HashSet<UnitKind>()
            for (token in tokens) {
                if (token.type == TYPE_UNIT && token.units.size == 1) {
                    kinds.add(token.units[0].kind)
                }
            }
            for (token in tokens) {
                if (token.type == TYPE_UNIT && token.units.size > 1) {
                    token.unit = token.units.firstOrNull { it.kind in kinds } ?: token.units[0]
                }
            }
        }

        private fun wordEnd(from: Int): Int {
            var index = from
            while (index < source.length && isWordChar(source[index])) {
                index++
            }
            return index
        }

        private fun unitWordEnd(from: Int): Int {
            val end = wordEnd(from)
            if (options.units && end < source.length && source[end] == '²' &&
                Measures.word(source.substring(from, end + 1)) != null
            ) {
                return end + 1
            }
            return end
        }

        private fun skipBlanks(from: Int): Int {
            var index = from
            while (index < source.length && isBlank(source[index])) {
                index++
            }
            return index
        }

        private fun readTemperature(from: Int): Int {
            if (!options.units) {
                throw error
            }
            val index = skipBlanks(from + 1)
            if (index >= source.length || index + 1 < source.length && isWordChar(source[index + 1])) {
                throw error
            }
            val unit = Measures.temperature(source[index]) ?: throw error
            tokens.add(Token(TYPE_UNIT, units = listOf(unit)))
            return index + 1
        }

        private fun addWord(word: String, end: Int): Int {
            val name = word.lowercase()
            if (name in MathOperators.percentOf) {
                tokens.add(Token(TYPE_OF))
                return end
            }
            val rates = options.rates
            if (Currencies.isConversion(word)) {
                tokens.add(Token(TYPE_CONVERSION))
                return end
            }
            val next = skipBlanks(end)
            val call = next < source.length && source[next] == '('
            if (call && name == MathOperators.DECIMAL) {
                tokens.add(Token(TYPE_IDENTIFIER, text = name))
                tokens.add(Token(TYPE_LEFT_PAREN))
                val from = skipBlanks(next + 1)
                val to = wordEnd(from)
                if (to > from) {
                    tokens.add(Token(TYPE_DIGITS, text = source.substring(from, to)))
                }
                return to
            }
            if (!call) {
                if (options.units) {
                    val compound = readCompound(word, end)
                    if (compound >= 0) {
                        return compound
                    }
                }
                val reserved = name in MathOperators.constants || name in MathOperators.bases || MathOperators.functionFor(name) != null
                val candidates = ArrayList<MathUnit>(2)
                if (rates != null && !reserved) {
                    Currencies.word(word, rates)?.let { candidates.add(it) }
                }
                if (options.units) {
                    Measures.word(word)?.let { candidates.add(it) }
                }
                if (candidates.isNotEmpty()) {
                    tokens.add(Token(TYPE_UNIT, units = candidates))
                    val abbreviated = end < source.length && source[end] == '.' &&
                            (end + 1 == source.length || !source[end + 1].isDigit())
                    return if (abbreviated) end + 1 else end
                }
            }
            tokens.add(Token(TYPE_IDENTIFIER, text = name))
            return end
        }

        private fun readCompound(first: String, end: Int): Int {
            val slash = end < source.length && source[end] == '/'
            val from = if (slash) end + 1 else skipBlanks(end)
            if (from >= source.length || !source[from].isLetter() || !slash && from == end) {
                return -1
            }
            val to = wordEnd(from)
            val unit = Measures.compound(first, if (slash) '/' else ' ', source.substring(from, to)) ?: return -1
            tokens.add(Token(TYPE_UNIT, units = listOf(unit)))
            return to
        }

        private fun readNumber(from: Int): Int {
            val radix = if (source[from] == '0' && from + 2 < source.length) MathOperators.radixFor(source[from + 1]) else 0
            if (radix != 0 && Character.digit(source[from + 2], radix) >= 0) {
                val end = wordEnd(from + 2)
                tokens.add(Token(TYPE_NUMBER, number = parseDigits(source.substring(from + 2, end), radix)))
                expanded = true
                return end
            }
            var end = scanNumber(from, false)
            if (options.rates != null || options.units) {
                val grouped = scanNumber(from, true)
                if (grouped > end && isAmountAt(grouped)) {
                    end = grouped
                }
            }
            val literal = StringBuilder(end - from)
            for (index in from until end) {
                val c = source[index]
                if (!isBlank(c)) {
                    literal.append(if (c == ',') '.' else c)
                }
            }
            var number = literal.toString().toDoubleOrNull() ?: throw error
            val glued = end < source.length && source[end].isLetter()
            val wordStart = if (glued) end else skipBlanks(end)
            if (wordStart > end || glued) {
                if (wordStart < source.length && source[wordStart].isLetter()) {
                    val wordEnd = wordEnd(wordStart)
                    val word = source.substring(wordStart, wordEnd).lowercase()
                    val multiplier = MathOperators.multipliers[word]
                    if (multiplier != null && (glued || word !in MathOperators.gluedMultipliers)) {
                        number *= multiplier
                        expanded = true
                        end = if (wordEnd < source.length && source[wordEnd] == '.' &&
                            (wordEnd + 1 == source.length || !source[wordEnd + 1].isDigit())
                        ) wordEnd + 1 else wordEnd
                    }
                }
            }
            tokens.add(Token(TYPE_NUMBER, number = number))
            return end
        }

        private fun readSuperscript(from: Int): Int {
            var end = from
            val sign = when (source[end]) {
                '⁺' -> "+"
                '⁻' -> "-"
                else -> null
            }
            if (sign != null) {
                end++
            }
            val digitsStart = end
            var number = 0.0
            while (end < source.length) {
                val digit = SUPERSCRIPT_DIGITS.indexOf(source[end])
                if (digit < 0) {
                    break
                }
                number = number * 10 + digit
                end++
            }
            if (end == digitsStart || end < source.length && isSuperscript(source[end])) {
                throw error
            }
            tokens.add(Token(TYPE_SYMBOL, text = "^"))
            if (sign != null) {
                tokens.add(Token(TYPE_SYMBOL, text = sign))
            }
            tokens.add(Token(TYPE_NUMBER, number = number))
            return end
        }

        private fun scanNumber(from: Int, grouping: Boolean): Int {
            var index = from
            val integerStart = index
            while (index < source.length && source[index].isDigit()) {
                index++
            }
            var integerDigits = index - integerStart
            if (integerDigits in 1..3) {
                while (index + 3 < source.length && isGroupSeparator(source[index], grouping) && isDigitGroup(index + 1)) {
                    index += 4
                    integerDigits += 3
                }
            }
            var fractionDigits = 0
            val current = if (index < source.length) source[index] else ' '
            if ((current == '.' || current == ',') && index + 1 < source.length && source[index + 1].isDigit()) {
                index++
                val fractionStart = index
                while (index < source.length && source[index].isDigit()) {
                    index++
                }
                fractionDigits = index - fractionStart
                if (current == ',' && fractionDigits == 3 && integerDigits in 1..3 && source[integerStart] != '0') {
                    throw error
                }
                if (separator == null) {
                    separator = current
                }
            }
            if (integerDigits == 0 && fractionDigits == 0) {
                throw error
            }
            return index
        }

        private fun isGroupSeparator(c: Char, spaces: Boolean): Boolean =
            c == MathFormat.NO_BREAK_SPACE || spaces && isBlank(c)

        private fun isAmountAt(end: Int): Boolean {
            if (tokens.lastOrNull()?.type == TYPE_UNIT) {
                return true
            }
            val index = skipBlanks(end)
            if (index == source.length) {
                return false
            }
            val c = source[index]
            if (options.rates?.let { Currencies.symbol(c, it) } != null || options.units && (c == '°' || Measures.symbol(c) != null)) {
                return true
            }
            if (!c.isLetter()) {
                return false
            }
            val word = source.substring(index, unitWordEnd(index))
            return options.rates?.let { Currencies.word(word, it) } != null || options.units && Measures.word(word) != null
        }

        private fun isDigitGroup(at: Int): Boolean =
            source[at].isDigit() && source[at + 1].isDigit() && source[at + 2].isDigit() &&
                    (at + 3 == source.length || !source[at + 3].isDigit())

        private fun peek(): Token = tokens[position]

        private fun next(): Token = tokens[position++]

        private fun expect(type: Int): Token {
            val token = next()
            if (token.type != type) {
                throw error
            }
            return token
        }

        private fun parseExpression(minPrecedence: Int): Operand {
            if (++depth > MAX_DEPTH) {
                throw error
            }
            try {
                var left = parseUnary()
                while (true) {
                    val token = peek()
                    if (token.type == TYPE_OF) {
                        if (!left.isPercent || MathOperators.PRECEDENCE_MULTIPLICATIVE < minPrecedence) {
                            break
                        }
                        next()
                        val right = parseExpression(MathOperators.PRECEDENCE_MULTIPLICATIVE + 1)
                        if (right.isPercent) {
                            throw error
                        }
                        hasOperation = true
                        left = Operand(left.value * right.value, false, right.unit)
                        continue
                    }
                    if (token.type != TYPE_SYMBOL) {
                        break
                    }
                    val operator = MathOperators.infixFor(token.text) ?: break
                    if (operator.precedence < minPrecedence) {
                        break
                    }
                    next()
                    val right = parseExpression(
                        if (operator.rightAssociative) operator.precedence else operator.precedence + 1
                    )
                    hasOperation = true
                    left = combine(operator, left, right)
                }
                return left
            } finally {
                depth--
            }
        }

        private fun parseConversion(): Operand {
            var operand = parseExpression(0)
            while (peek().type == TYPE_CONVERSION) {
                next()
                val target = next()
                val radix = if (target.type == TYPE_IDENTIFIER) MathOperators.bases[target.text] else null
                if (radix != null) {
                    if (operand.unit != null || peek().type != TYPE_END) {
                        throw error
                    }
                    base = radix
                    hasOperation = true
                    continue
                }
                if (target.type != TYPE_UNIT) {
                    throw error
                }
                operand = convert(operand, target.unit ?: throw error)
                explicitTarget = true
            }
            return operand
        }

        private fun convert(operand: Operand, target: MathUnit): Operand {
            val unit = operand.unit ?: throw error
            hasOperation = true
            return Operand(convertValue(operand.value, unit, target), false, target)
        }

        private fun convertValue(value: Double, from: MathUnit, to: MathUnit): Double {
            if (from.code == to.code) {
                return value
            }
            if (from.kind != to.kind) {
                throw error
            }
            if (from.kind == UnitKind.CURRENCY) {
                return value * (options.rates?.rate(from.code, to.code) ?: throw error)
            }
            return to.fromBase(from.toBase(value))
        }

        private fun combine(operator: InfixOperator, left: Operand, right: Operand): Operand {
            val leftUnit = left.unit
            val rightUnit = right.unit
            if (leftUnit == null && rightUnit == null) {
                return Operand(operator.apply(left.value, right.value, right.isPercent), false)
            }
            var rightValue = right.value
            val unit = when (operator.units) {
                UnitRule.SAME -> {
                    if (leftUnit != null && rightUnit != null) {
                        rightValue = convertValue(rightValue, rightUnit, leftUnit)
                    }
                    leftUnit ?: rightUnit
                }
                UnitRule.SCALE -> {
                    if (leftUnit != null && rightUnit != null) {
                        throw error
                    }
                    leftUnit ?: rightUnit
                }
                UnitRule.RATIO -> when {
                    leftUnit == null -> throw error
                    rightUnit == null -> leftUnit
                    else -> {
                        rightValue = convertValue(rightValue, rightUnit, leftUnit)
                        null
                    }
                }
                UnitRule.NONE -> throw error
            }
            return Operand(operator.apply(left.value, rightValue, right.isPercent), false, unit)
        }

        private fun parseUnary(): Operand {
            val token = peek()
            if (token.type == TYPE_UNIT) {
                val unit = token.unit ?: throw error
                if (unit.kind != UnitKind.CURRENCY) {
                    throw error
                }
                next()
                val operand = parsePostfix()
                if (operand.unit != null || operand.isPercent) {
                    throw error
                }
                return Operand(operand.value, false, unit)
            }
            if (token.type == TYPE_SYMBOL) {
                val operator = MathOperators.prefixFor(token.text)
                if (operator != null) {
                    next()
                    val operand = parseExpression(operator.precedence)
                    if (operator.operation) {
                        if (operand.unit != null) {
                            throw error
                        }
                        hasOperation = true
                    }
                    return Operand(operator.apply(operand.value), operand.isPercent, operand.unit)
                }
            }
            return parsePostfix()
        }

        private fun parsePostfix(): Operand {
            val primary = parsePrimary()
            var value = primary.value
            var unit = primary.unit
            var isPercent = false
            while (true) {
                val token = peek()
                if (token.type == TYPE_UNIT) {
                    if (unit != null || isPercent) {
                        throw error
                    }
                    next()
                    unit = token.unit
                    continue
                }
                if (token.type != TYPE_SYMBOL) {
                    break
                }
                val operator = MathOperators.postfixFor(token.text) ?: break
                if (unit != null) {
                    throw error
                }
                next()
                value = operator.apply(value)
                hasOperation = true
                isPercent = operator.percent
            }
            return Operand(value, isPercent, unit)
        }

        private fun parsePrimary(): Operand {
            val token = next()
            return when (token.type) {
                TYPE_NUMBER -> Operand(token.number, false)
                TYPE_LEFT_PAREN -> {
                    val inner = parseConversion()
                    expect(TYPE_RIGHT_PAREN)
                    Operand(inner.value, false, inner.unit)
                }
                TYPE_IDENTIFIER -> Operand(parseIdentifier(token.text), false)
                else -> throw error
            }
        }

        private fun parseIdentifier(name: String): Double {
            MathOperators.constants[name]?.let { return it }
            MathOperators.bases[name]?.let { return parseBase(name, it) }
            val function = MathOperators.functionFor(name) ?: throw error
            expect(TYPE_LEFT_PAREN)
            val args = ArrayList<Double>(2)
            if (peek().type != TYPE_RIGHT_PAREN) {
                args.add(parseArgument())
                while (peek().type == TYPE_ARGUMENT_SEPARATOR) {
                    next()
                    args.add(parseArgument())
                }
            }
            expect(TYPE_RIGHT_PAREN)
            if (args.size !in function.arity) {
                throw error
            }
            hasOperation = true
            val values = args.toDoubleArray()
            val degrees = options.degrees && function.angle != AngleUse.NONE
            if (degrees && function.angle == AngleUse.ARGUMENT) {
                for (i in values.indices) {
                    values[i] = Math.toRadians(values[i])
                }
            }
            val value = function.apply(values)
            return when {
                function.angle == AngleUse.ARGUMENT && abs(value) < TRIG_EPSILON -> 0.0
                degrees && function.angle == AngleUse.RESULT -> Math.toDegrees(value)
                else -> value
            }
        }

        private fun parseBase(name: String, radix: Int): Double {
            val whole = position == 1
            expect(TYPE_LEFT_PAREN)
            val value = if (name == MathOperators.DECIMAL) {
                val digits = expect(TYPE_DIGITS).text
                if (peek().type == TYPE_ARGUMENT_SEPARATOR) {
                    next()
                    val from = parseArgument()
                    if (from != Math.floor(from) || from.toInt() !in MathOperators.MIN_RADIX..MathOperators.MAX_RADIX) {
                        throw error
                    }
                    parseDigits(MathOperators.stripRadixPrefix(digits, from.toInt()), from.toInt())
                } else {
                    val prefix = if (digits.length > 2 && digits[0] == '0') MathOperators.radixFor(digits[1]) else 0
                    if (prefix != 0) parseDigits(digits.substring(2), prefix) else parseDigits(digits, 10)
                }
            } else {
                parseArgument()
            }
            expect(TYPE_RIGHT_PAREN)
            if (name != MathOperators.DECIMAL) {
                if (!whole || peek().type != TYPE_END) {
                    throw error
                }
                base = radix
            }
            hasOperation = true
            return value
        }

        private fun parseDigits(digits: String, radix: Int): Double {
            if (digits.isEmpty()) {
                throw error
            }
            var value = 0L
            for (c in digits) {
                val digit = Character.digit(c, radix)
                if (digit < 0) {
                    throw error
                }
                value = value * radix + digit
                if (value > MathFormat.MAX_EXACT_INTEGER) {
                    throw error
                }
            }
            return value.toDouble()
        }

        private fun parseArgument(): Double {
            val argument = parseExpression(0)
            if (argument.unit != null) {
                throw error
            }
            return argument.value
        }
    }
}
