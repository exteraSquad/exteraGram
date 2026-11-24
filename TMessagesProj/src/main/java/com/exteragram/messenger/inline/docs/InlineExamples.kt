/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.inline.docs

class InlineExampleGroup(private val english: List<String>, private val russian: List<String> = english) {
    fun examples(russian: Boolean): List<String> = if (russian) this.russian else english
}

object InlineExamples {

    val functions = listOf("round", "min", "max", "abs", "sqrt", "log", "ln", "sin", "cos", "tan", "pi", "e")

    val arithmetic = InlineExampleGroup(listOf("(120 + 80) / 4 =", "√144 + 2^3 ="))
    val arithmeticFunctions = InlineExampleGroup(listOf("max(12, 40, 7) =", "sin(30) ="))
    val bases = InlineExampleGroup(listOf("hex(255) =", "dec(ff, 16) =", "0xff + 1 ="))

    val percentages = InlineExampleGroup(
        listOf("2300 - 15% =", "12% of 1200 ="),
        listOf("2300 - 15% =", "12% от 1200 =")
    )
    val shorthands = InlineExampleGroup(listOf("300k / 12 ="), listOf("300к / 12 ="))

    val currencyDefault = InlineExampleGroup(
        listOf("100€ =", "500 stars ="),
        listOf("100€ =", "500 звёзд =")
    )
    val currencyTarget = InlineExampleGroup(
        listOf("100 dollars to euros =", "5k ₽ to $ =", "50$ to GRAM ="),
        listOf("100 долларов в евро =", "5к ₽ в $ =", "50$ в GRAM =")
    )

    val unitsDefault = InlineExampleGroup(
        listOf("72°F =", "10 miles ="),
        listOf("72°F =", "10 миль =")
    )
    val unitsTarget = InlineExampleGroup(
        listOf("5 kg to lb =", "100 km/h to mph ="),
        listOf("5 кг в фунтах =", "100 км/ч в mph =")
    )
    val durations = InlineExampleGroup(
        listOf("90 min =", "2 h + 45 min ="),
        listOf("90 мин =", "2 ч + 45 мин =")
    )

    val timeZones = InlineExampleGroup(
        listOf("6pm est =", "12:00 in tokyo ="),
        listOf("18:00 utc =", "12:00 в токио =")
    )
    val currentTime = InlineExampleGroup(listOf("now in new york ="), listOf("сейчас в нью-йорке ="))

    val all = listOf(
        arithmetic, arithmeticFunctions, bases, percentages, shorthands, currencyDefault, currencyTarget,
        unitsDefault, unitsTarget, durations, timeZones, currentTime
    )
}
