/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.inline.units

import com.exteragram.messenger.inline.math.MathFormat
import com.exteragram.messenger.inline.math.MathUnit
import com.exteragram.messenger.inline.math.UnitKind

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

object Measures {

    private const val CELSIUS_OFFSET = 273.15
    private const val FAHRENHEIT_SCALE = 5.0 / 9.0
    private const val MIN_RESCALED = 0.1
    private const val MAX_RESCALED = 1000.0
    private const val MAX_STEPPED_DOWN = 100_000.0

    private val units = HashMap<String, MathUnit>()
    private val words = HashMap<String, String>()
    private val counterparts = HashMap<String, String>()
    private val compounds = HashMap<String, String>()
    private val ladders = HashMap<String, Ladder>()

    private class Ladder(val imperial: Boolean, val codes: List<String>)

    private val breakdown = listOf("d", "h", "min", "s")

    init {
        fun unit(
            code: String,
            label: String,
            russianLabel: String,
            kind: UnitKind,
            scale: Double,
            counterpart: String?,
            vararg forms: String,
            offset: Double = 0.0
        ) {
            units[code] = MathUnit(code, label, kind, scale, offset, russianLabel)
            counterpart?.let { counterparts[code] = it }
            forms.forEach { words[it] = code }
        }

        unit(
            "mm", "mm", "мм", UnitKind.LENGTH, 0.001, "in",
            "mm", "мм", "миллиметр", "миллиметра", "миллиметров", "миллиметрах",
            "millimeter", "millimeters", "millimetre", "millimetres"
        )
        unit(
            "cm", "cm", "см", UnitKind.LENGTH, 0.01, "in",
            "cm", "см", "сантиметр", "сантиметра", "сантиметров", "сантиметрах",
            "centimeter", "centimeters", "centimetre", "centimetres"
        )
        unit(
            "m", "m", "м", UnitKind.LENGTH, 1.0, "ft",
            "m", "м", "метр", "метра", "метров", "метрах", "meter", "meters", "metre", "metres"
        )
        unit(
            "km", "km", "км", UnitKind.LENGTH, 1000.0, "mi",
            "km", "км", "километр", "километра", "километров", "километрах",
            "kilometer", "kilometers", "kilometre", "kilometres"
        )
        unit("in", "in", "in", UnitKind.LENGTH, 0.0254, "cm", "inch", "inches", "дюйм", "дюйма", "дюймов", "дюймах")
        unit("ft", "ft", "ft", UnitKind.LENGTH, 0.3048, "m", "ft", "foot", "feet", "фут", "фута", "футов", "футах")
        unit("yd", "yd", "yd", UnitKind.LENGTH, 0.9144, "m", "yd", "yard", "yards", "ярд", "ярда", "ярдов", "ярдах")
        unit("mi", "mi", "mi", UnitKind.LENGTH, 1609.344, "km", "mi", "mile", "miles", "миля", "мили", "миль", "милях")

        unit(
            "mg", "mg", "мг", UnitKind.MASS, 0.000001, null,
            "mg", "мг", "миллиграмм", "миллиграмма", "миллиграммов", "milligram", "milligrams"
        )
        unit(
            "g", "g", "г", UnitKind.MASS, 0.001, "oz",
            "g", "г", "гр", "грамм", "грамма", "граммов", "граммах", "gram", "grams", "gramme", "grammes"
        )
        unit(
            "kg", "kg", "кг", UnitKind.MASS, 1.0, "lb",
            "kg", "кг", "килограмм", "килограмма", "килограммов", "килограммах", "kilogram", "kilograms", "kilo", "kilos"
        )
        unit("t", "t", "т", UnitKind.MASS, 1000.0, "lb", "t", "т", "тонна", "тонны", "тонн", "тоннах", "tonne", "tonnes")
        unit(
            "oz", "oz", "oz", UnitKind.MASS, 0.028349523125, "g",
            "oz", "ounce", "ounces", "унция", "унции", "унций", "унциях"
        )
        unit(
            "lb", "lb", "lb", UnitKind.MASS, 0.45359237, "kg",
            "lb", "lbs", "pound", "pounds", "фунт", "фунта", "фунтов", "фунтах"
        )

        unit(
            "ml", "ml", "мл", UnitKind.VOLUME, 0.001, "floz",
            "ml", "мл", "миллилитр", "миллилитра", "миллилитров", "миллилитрах",
            "milliliter", "milliliters", "millilitre", "millilitres"
        )
        unit(
            "l", "l", "л", UnitKind.VOLUME, 1.0, "gal",
            "l", "л", "литр", "литра", "литров", "литрах", "liter", "liters", "litre", "litres"
        )
        unit("floz", "fl oz", "fl oz", UnitKind.VOLUME, 0.0295735295625, "ml", "floz")
        unit(
            "gal", "gal", "gal", UnitKind.VOLUME, 3.785411784, "l",
            "gal", "gallon", "gallons", "галлон", "галлона", "галлонов", "галлонах"
        )

        unit("m2", "m²", "м²", UnitKind.AREA, 1.0, "ft2", "m2", "m²", "м2", "м²", "sqm")
        unit("km2", "km²", "км²", UnitKind.AREA, 1_000_000.0, "mi2", "km2", "km²", "км2", "км²")
        unit(
            "ha", "ha", "га", UnitKind.AREA, 10_000.0, "acre",
            "ha", "га", "гектар", "гектара", "гектаров", "гектарах", "hectare", "hectares"
        )
        unit("ft2", "ft²", "ft²", UnitKind.AREA, 0.09290304, "m2", "ft2", "ft²", "sqft")
        unit("mi2", "mi²", "mi²", UnitKind.AREA, 2_589_988.110336, "km2", "mi2", "mi²")
        unit("acre", "ac", "ac", UnitKind.AREA, 4046.8564224, "ha", "ac", "acre", "acres", "акр", "акра", "акров", "акрах")

        unit("kmh", "km/h", "км/ч", UnitKind.SPEED, 1 / 3.6, "mph", "kmh", "kph")
        unit("ms", "m/s", "м/с", UnitKind.SPEED, 1.0, "kmh", "mps")
        unit("mph", "mph", "mph", UnitKind.SPEED, 0.44704, "kmh", "mph")
        unit("knot", "kn", "уз", UnitKind.SPEED, 1852.0 / 3600.0, "kmh", "knot", "knots", "узел", "узла", "узлов", "узлах")

        unit(
            "C", "°C", "°C", UnitKind.TEMPERATURE, 1.0, "F",
            "celsius", "цельсий", "цельсия", "цельсию", "цельсиях", offset = CELSIUS_OFFSET
        )
        unit(
            "F", "°F", "°F", UnitKind.TEMPERATURE, FAHRENHEIT_SCALE, "C",
            "fahrenheit", "фаренгейт", "фаренгейта", "фаренгейту", "фаренгейтах", offset = CELSIUS_OFFSET - 32 * FAHRENHEIT_SCALE
        )
        unit("K", "K", "K", UnitKind.TEMPERATURE, 1.0, "C", "kelvin", "kelvins", "кельвин", "кельвина", "кельвинов", "кельвинах")

        unit(
            "msec", "ms", "мс", UnitKind.DURATION, 0.001, null,
            "ms", "мс", "millisecond", "milliseconds", "миллисекунда", "миллисекунды", "миллисекунд"
        )
        unit(
            "s", "s", "с", UnitKind.DURATION, 1.0, null,
            "s", "sec", "secs", "second", "seconds", "с", "сек", "секунда", "секунды", "секунд", "секундах", "секунду"
        )
        unit(
            "min", "min", "мин", UnitKind.DURATION, 60.0, null,
            "min", "mins", "minute", "minutes", "мин", "минута", "минуты", "минут", "минутах", "минуту"
        )
        unit(
            "h", "h", "ч", UnitKind.DURATION, 3600.0, null,
            "h", "hr", "hrs", "hour", "hours", "ч", "час", "часа", "часов", "часах"
        )
        unit(
            "d", "d", "д", UnitKind.DURATION, 86_400.0, null,
            "d", "day", "days", "д", "дн", "день", "дня", "дней", "днях", "сут", "сутки", "суток"
        )
        unit(
            "w", "w", "нед", UnitKind.DURATION, 604_800.0, null,
            "w", "wk", "week", "weeks", "нед", "неделя", "недели", "недель", "неделях", "неделю"
        )
        unit(
            "mo", "mo", "мес", UnitKind.DURATION, 2_592_000.0, null,
            "mo", "month", "months", "мес", "месяц", "месяца", "месяцев", "месяцах"
        )
        unit(
            "y", "y", "г.", UnitKind.DURATION, 31_536_000.0, null,
            "y", "yr", "yrs", "year", "years", "год", "года", "лет", "годах"
        )

        compounds["km/h"] = "kmh"
        compounds["км/ч"] = "kmh"
        compounds["m/s"] = "ms"
        compounds["м/с"] = "ms"
        compounds["fl oz"] = "floz"

        fun ladder(imperial: Boolean, vararg codes: String): Ladder {
            val ladder = Ladder(imperial, codes.toList())
            codes.forEach { ladders[it] = ladder }
            return ladder
        }

        ladder(false, "mm", "cm", "m", "km")
        ladder(false, "mg", "g", "kg", "t")
        ladder(false, "ml", "l")
        ladder(false, "m2", "ha", "km2")
        ladders["yd"] = ladder(true, "in", "ft", "mi")
        ladder(true, "oz", "lb")
        ladder(true, "floz", "gal")
        ladder(true, "ft2", "acre", "mi2")
    }

    fun word(word: String): MathUnit? = words[word.lowercase(Locale.ROOT)]?.let { units[it] }

    fun compound(first: String, separator: Char, second: String): MathUnit? {
        val key = first.lowercase(Locale.ROOT) + separator + second.lowercase(Locale.ROOT)
        return compounds[key]?.let { units[it] }
    }

    fun temperature(scale: Char): MathUnit? = when (scale) {
        'C', 'c', 'С', 'с' -> units["C"]
        'F', 'f', 'Ф', 'ф' -> units["F"]
        'K', 'k', 'К', 'к' -> units["K"]
        else -> null
    }

    fun symbol(c: Char): MathUnit? = when (c) {
        '℃' -> units["C"]
        '℉' -> units["F"]
        else -> null
    }

    fun implicitTarget(unit: MathUnit, value: Double, imperial: Boolean): MathUnit? {
        val ladder = ladders[unit.code]
        if (ladder != null && ladder.imperial == imperial) {
            rescale(ladder, unit, abs(unit.toBase(value)))?.let { return it }
        }
        return counterparts[unit.code]?.let { units[it] }
    }

    private fun rescale(ladder: Ladder, unit: MathUnit, base: Double): MathUnit? {
        for (code in ladder.codes.asReversed()) {
            val target = units.getValue(code)
            val amount = target.fromBase(base)
            if (code != unit.code && amount >= MIN_RESCALED && amount < MAX_RESCALED) {
                return target
            }
        }
        val index = ladder.codes.indexOf(unit.code)
        if (index <= 0) {
            return null
        }
        val smaller = units.getValue(ladder.codes[index - 1])
        val amount = smaller.fromBase(base)
        return if (amount >= 1 && amount < MAX_STEPPED_DOWN) smaller else null
    }

    fun canBreakDown(unit: MathUnit): Boolean = unit.code in breakdown

    fun breaksDown(value: Double, unit: MathUnit): Boolean {
        if (unit.code !in breakdown) {
            return false
        }
        val parts = parts(abs(value * unit.scale)) ?: return false
        return parts.size > 1 || parts[0].first != unit.code
    }

    fun formatDuration(seconds: Double, decimalSeparator: Char, russian: Boolean): String? {
        if (seconds.isNaN() || seconds.isInfinite()) {
            return null
        }
        val parts = parts(abs(seconds)) ?: return null
        val text = StringBuilder()
        if (seconds < 0) {
            text.append('-')
        }
        parts.forEachIndexed { index, (code, amount) ->
            if (index > 0) {
                text.append(' ')
            }
            val number = MathFormat.format(amount, decimalSeparator, false) ?: return null
            text.append(number).append(MathFormat.NO_BREAK_SPACE).append(units.getValue(code).label(russian))
        }
        return text.toString()
    }

    private fun parts(seconds: Double): List<Pair<String, Double>>? {
        if (seconds >= 1e12) {
            return null
        }
        if (seconds < 60) {
            return listOf("s" to seconds)
        }
        var rest = seconds.roundToLong()
        val parts = ArrayList<Pair<String, Double>>(4)
        for (code in breakdown) {
            val size = units.getValue(code).scale.toLong()
            val amount = floor(rest.toDouble() / size).toLong()
            if (amount > 0) {
                parts.add(code to amount.toDouble())
                rest -= amount * size
            }
        }
        return parts.ifEmpty { listOf("s" to 0.0) }
    }
}
