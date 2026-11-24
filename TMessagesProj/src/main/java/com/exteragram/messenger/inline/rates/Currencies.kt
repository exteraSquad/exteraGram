/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.inline.rates

import com.exteragram.messenger.inline.math.MathUnit

import java.util.Currency
import java.util.Locale

object Currencies {

    const val FALLBACK = "USD"
    const val STARS = "XTR"
    const val GRAM = "GRAM"

    private val symbols = mapOf(
        '$' to "USD",
        '€' to "EUR",
        '₽' to "RUB",
        '£' to "GBP",
        '¥' to "JPY",
        '₸' to "KZT",
        '₴' to "UAH",
        '₺' to "TRY",
        '₹' to "INR",
        '₪' to "ILS",
        '₩' to "KRW",
        '₾' to "GEL",
        '₼' to "AZN",
        '֏' to "AMD",
        '₫' to "VND",
        '฿' to "THB",
        '₱' to "PHP",
        '₦' to "NGN",
        '₿' to "BTC",
        '⭐' to STARS,
        '★' to STARS
    )

    private val labels: Map<String, String> = symbols.entries
        .filter { it.value != "BTC" && it.key != '★' }
        .associate { it.value to it.key.toString() }

    private val lowercaseCodes = setOf(
        "usd", "eur", "rub", "gbp", "kzt", "uah", "byn", "cny", "jpy", "pln", "czk", "aed", "inr", "ils",
        "chf", "cad", "aud", "uzs", "kgs", "azn", "krw", "thb", "vnd", "sgd", "hkd", "brl", "mxn", "sek",
        "huf", "btc", "eth", "usdt", "xtr"
    )

    private val conversions = setOf("в", "to", "in")

    private val words: Map<String, String> = buildMap {
        fun add(code: String, vararg forms: String) = forms.forEach { put(it, code) }
        add(
            "USD", "доллар", "доллара", "долларов", "доллары", "долларах", "долл",
            "бакс", "бакса", "баксов", "баксы", "баксах", "dollar", "dollars", "buck", "bucks"
        )
        add("EUR", "евро", "euro", "euros")
        add("RUB", "рубль", "рубля", "рублей", "рубли", "рублях", "руб", "р", "ruble", "rubles", "rouble", "roubles")
        add("GBP", "фунт", "фунта", "фунтов", "фунты", "фунтах")
        add("KZT", "тенге", "tenge")
        add("UAH", "гривна", "гривны", "гривен", "гривнах", "грн", "hryvnia", "hryvnias")
        add("CNY", "юань", "юаня", "юаней", "юани", "юанях", "yuan")
        add("JPY", "иена", "иены", "иен", "иенах", "йена", "йены", "йен", "йенах", "yen")
        add("TRY", "лира", "лиры", "лир", "лирах", "lira", "liras")
        add("PLN", "злотый", "злотого", "злотых", "злотые", "zloty", "zlotys", "zł")
        add("CZK", "kč")
        add("INR", "рупия", "рупии", "рупий", "рупиях", "rupee", "rupees")
        add("ILS", "шекель", "шекеля", "шекелей", "шекели", "шекелях", "shekel", "shekels")
        add("CHF", "франк", "франка", "франков", "франки", "франках", "franc", "francs")
        add("AED", "дирхам", "дирхама", "дирхамов", "дирхамы", "дирхамах", "dirham", "dirhams")
        add("THB", "бат", "бата", "батов", "баты", "батах", "baht")
        add("GEL", "лари", "lari")
        add("AMD", "драм", "драмов", "драмы", "драмах", "dram", "drams")
        add("UZS", "сум", "сумов", "сумы", "сумах")
        add("KGS", "сом", "сомов", "сомах", "som")
        add(
            "BTC", "биткоин", "биткоина", "биткоинов", "биткоины", "биткоинах",
            "биткойн", "биткойна", "биткойнов", "биткойны", "биткойнах", "bitcoin", "bitcoins"
        )
        add("ETH", "эфириум", "эфириума", "эфириумов", "эфириумах", "ethereum", "ether")
        add(
            GRAM, "gram", "grams", "грам", "грама", "грамов", "грамы", "грамах",
            "ton", "тон", "тона", "тонов", "тонах", "toncoin", "toncoins", "тонкоин", "тонкоина", "тонкоинов"
        )
        add("USDT", "tether")
        add(
            STARS, "звезда", "звезды", "звёзды", "звезд", "звёзд", "звездах", "звёздах", "звездами", "звёздами",
            "звезду", "star", "stars"
        )
    }

    val symbolChars: Set<Char> = symbols.keys

    fun isConversion(word: String): Boolean = word.lowercase(Locale.ROOT) in conversions

    fun symbol(c: Char, rates: CurrencyRates): MathUnit? {
        val code = symbols[c] ?: return null
        return if (rates.supports(code)) MathUnit(code, if (code == STARS) label(code) else c.toString()) else null
    }

    fun word(word: String, rates: CurrencyRates): MathUnit? {
        val lower = word.lowercase(Locale.ROOT)
        val alias = words[lower]
        val code = when {
            alias != null -> alias
            word.all { it in 'A'..'Z' } -> word
            lower in lowercaseCodes -> lower.uppercase(Locale.ROOT)
            else -> return null
        }
        if (!rates.supports(code)) {
            return null
        }
        return MathUnit(code, if (alias != null) label(code) else code)
    }

    fun unit(code: String): MathUnit = MathUnit(code, label(code))

    fun fractionDigits(code: String): Int = if (code == STARS) 0 else try {
        Currency.getInstance(code).defaultFractionDigits
    } catch (e: IllegalArgumentException) {
        -1
    }

    private fun label(code: String): String = labels[code] ?: code
}
