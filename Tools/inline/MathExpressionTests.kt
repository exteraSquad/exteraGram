import com.exteragram.messenger.inline.math.MathExpression
import com.exteragram.messenger.inline.math.MathFormat
import com.exteragram.messenger.inline.math.MathOptions
import com.exteragram.messenger.inline.docs.InlineExamples
import com.exteragram.messenger.inline.rates.CurrencyRates
import com.exteragram.messenger.inline.time.TimeZoneOptions
import java.time.ZoneId
import java.time.ZonedDateTime

private var passed = 0
private val failures = ArrayList<String>()

private fun show(value: String?) = value ?: "<none>"

private fun check(label: String, expected: String?, actual: String?) {
    if (expected == actual) {
        passed++
    } else {
        failures.add("$label\n      expected: ${show(expected)}\n      actual:   ${show(actual)}")
    }
}

private fun result(expression: String, separator: Char): String? {
    val evaluated = MathExpression.evaluate(expression, MathOptions(separator, grouping = false)) ?: return null
    if (!evaluated.hasOperation) {
        return null
    }
    return MathExpression.format(evaluated)
}

private fun expr(expression: String, expected: String?, separator: Char = '.') {
    val suffix = if (separator == '.') "" else ", '$separator'"
    check("evaluate(\"$expression\"$suffix)", expected, result(expression, separator))
}

private fun escape(text: String) = text.replace("\n", "\\n")

private fun ghost(text: String, caret: Int, expected: String?, separator: Char = '.') {
    val suggestion = MathExpression.suggestionAt(text, caret, MathOptions(separator, grouping = false))
    check("suggestionAt(\"${escape(text)}\", $caret)", expected, suggestion?.value)
}

private fun ghost(text: String, expected: String?, separator: Char = '.') =
    ghost(text, text.length, expected, separator)

private fun inserted(text: String, expected: String?) {
    val suggestion = MathExpression.suggestionAt(text, text.length, MathOptions('.', grouping = false))
    check("insertText(\"${escape(text)}\")", expected, suggestion?.insertText)
}

private fun section(name: String) = println("  $name")

private class FakeRates(private val usd: Map<String, Double>, private val supported: Set<String>) : CurrencyRates {
    override fun supports(code: String) = code in supported

    override fun rate(from: String, to: String): Double? {
        val fromUsd = usd[from] ?: return null
        val toUsd = usd[to] ?: return null
        return fromUsd / toUsd
    }
}

private val usdRates = mapOf(
    "USD" to 1.0,
    "EUR" to 1.25,
    "RUB" to 0.0125,
    "JPY" to 0.008,
    "TRY" to 0.025,
    "KZT" to 0.002,
    "VND" to 0.00004,
    "BTC" to 100000.0
)

private val rates = FakeRates(usdRates, usdRates.keys + "GBP")
private val offline = FakeRates(emptyMap(), usdRates.keys)

private val fullRates = FakeRates(
    mapOf("USD" to 1.0, "EUR" to 1.25, "RUB" to 0.0125, "GBP" to 1.5, "XTR" to 0.02, "GRAM" to 5.0),
    setOf("USD", "EUR", "RUB", "GBP", "XTR", "GRAM")
)
private val moscow = ZoneId.of("Europe/Moscow")
private val winter = ZonedDateTime.of(2026, 1, 15, 12, 0, 0, 0, moscow)
private val summer = ZonedDateTime.of(2026, 7, 15, 12, 0, 0, 0, moscow)

private val placeNames = mapOf(
    "осло" to "Europe/Oslo", "oslo" to "Europe/Oslo", "стокгольм" to "Europe/Stockholm", "вена" to "Europe/Vienna",
    "норвегия" to "Europe/Oslo", "китай" to "Asia/Shanghai", "филиппины" to "Asia/Manila", "кипр" to "Asia/Nicosia",
    "севастополь" to "Europe/Simferopol", "sao paulo" to "America/Sao_Paulo"
)

private fun fullOptions(
    arithmetic: Boolean = true,
    currencies: Boolean = true,
    units: Boolean = true,
    zones: Boolean = true,
    degrees: Boolean = true,
    grouping: Boolean = true,
    imperial: Boolean = false,
    local: ZoneId = moscow,
    now: ZonedDateTime = winter,
    places: ((String) -> String?)? = placeNames::get
) = MathOptions(
    '.', if (currencies) fullRates else null, if (currencies) "RUB" else null, arithmetic, units, degrees, grouping,
    if (zones) TimeZoneOptions(local, now, places) else null, imperial
)

private fun full(text: String, expected: String?, options: MathOptions = fullOptions()) {
    val suggestion = MathExpression.suggestionAt(text, text.length, options)
    check("full(\"${escape(text)}\")", expected, suggestion?.value?.replace(MathFormat.NO_BREAK_SPACE, ' '))
}

private fun money(
    text: String,
    expected: String?,
    local: String? = "RUB",
    source: CurrencyRates = rates,
    arithmetic: Boolean = true
) {
    val suggestion = MathExpression.suggestionAt(text, text.length, MathOptions('.', source, local, arithmetic))
    check("money(\"${escape(text)}\", $local)", expected, suggestion?.value?.replace(MathFormat.NO_BREAK_SPACE, ' '))
}

private fun moneyText(value: Double, fractionDigits: Int, separator: Char = '.'): String? =
    MathFormat.formatMoney(value, fractionDigits, separator)?.replace(MathFormat.NO_BREAK_SPACE, ' ')

fun main() {
    println("math expression tests")

    section("arithmetic and precedence")
    expr("2+2", "4")
    expr("2-3", "-1")
    expr("6*7", "42")
    expr("10/4", "2.5")
    expr("1+2*3", "7")
    expr("(1+2)*3", "9")
    expr("2*(3+4)-5", "9")
    expr("100/10/2", "5")
    expr("10-2-3", "5")
    expr("2+3*4-6/3", "12")
    expr("((1+2)*(3+4))", "21")
    expr(" 2+2 ", "4")
    expr("2 + 2", "4")

    section("power")
    expr("2^10", "1024")
    expr("2^3^2", "512")
    expr("2^-1", "0.5")
    expr("-2^2", "-4")
    expr("(-2)^2", "4")
    expr("4^0.5", "2")
    expr("2^0", "1")
    expr("2^-1+3", "3.5")

    section("superscript powers")
    expr("2⁵", "32")
    expr("2¹⁰", "1024")
    expr("2⁻³", "0.125")
    expr("2⁺³", "8")
    expr("2⁰", "1")
    expr("-2²", "-4")
    expr("(-2)²", "4")
    expr("(1+2)³", "27")
    expr("2+3²*4", "38")
    expr("2^3²", "512")
    expr("max(2;3)²", "9")
    expr("0xA²", "100")
    expr("2⁻", null)
    expr("2²⁺³", null)

    section("unary")
    expr("-5+1", "-4")
    expr("+7-2", "5")
    expr("3*-2", "-6")
    expr("3--2", "5")
    expr("2++3", "5")
    expr("--5", null)

    section("percent")
    expr("100+15%", "115")
    expr("100-15%", "85")
    expr("200*15%", "30")
    expr("200/15%", "1333.33333333")
    expr("50%", "0.5")
    expr("100+15%+10%", "126.5")
    expr("1000-10%-10%", "810")
    expr("50%*2", "1")
    expr("20%+30%", "0.26")
    expr("100+(15%)", "100.15")

    section("decimal separators")
    expr("1.5+1", "2.5")
    expr("1,5+1", "2,5")
    expr("2+2", "4", ',')
    expr("10/4", "2,5", ',')
    expr("1,5+1", "2,5", ',')
    expr(".5+.5", "1")
    expr("1,234+1", null)
    expr("0,123+1", "1,123")
    expr("1,2345+1", "2,2345")
    expr("1.234+1", "2.234")
    expr("1.2.3", null)
    expr("2..3", null)
    expr("5.", null)
    expr("5.+1", null)

    section("floating point noise")
    expr("0.1+0.2", "0.3")
    expr("1/3", "0.333333333333")
    expr("0.1*3", "0.3")
    expr("2.675*100", "267.5")
    expr("0.1+0.7", "0.8")
    expr("0.3-0.1", "0.2")

    section("operator aliases")
    expr("3×4", "12")
    expr("12÷4", "3")
    expr("5−2", "3")
    expr("−5+8", "3")

    section("rejected input")
    expr("1/0", null)
    expr("0/0", null)
    expr("(-8)^0.5", null)
    expr("9^9^9", null)
    expr("()", null)
    expr("2+", null)
    expr("+", null)
    expr("2 3", null)
    expr("(2+3", null)
    expr("2+3)", null)
    expr("", null)
    expr("abc", null)
    expr("2**3", null)
    expr("2+*3", null)
    expr("%", null)

    section("requires an operation")
    expr("5", null)
    expr("(5)", null)
    expr("-5", null)
    expr("((7))", null)
    expr("2024", null)
    expr("0", null)

    section("formatting bounds")
    expr("1000000*1000000", null)
    expr("999999*999999", "999998000001")
    expr("100000000000+1", "100000000001")
    expr("1/10000000000", null)
    expr("1/1000000000", "0.000000001")
    expr("0*5", "0")
    expr("5-5", "0")
    expr("-1*0", "0")
    expr("1000*1000", "1000000")
    check("format(NaN)", null, MathFormat.format(Double.NaN, '.'))
    check("format(Infinity)", null, MathFormat.format(Double.POSITIVE_INFINITY, '.'))

    section("suggestion scanner")
    ghost("2+2=", "4")
    ghost("2 + 2 =", "4")
    ghost("100+15%=", "115")
    ghost("скажи 2+2=", "4")
    ghost("(1+2)*3=", "9")
    ghost("2+2= ", 4, "4")
    ghost("2+2=\nhi", 4, "4")
    ghost("5=", null)
    ghost("2024=", null)
    ghost("-5=", null)
    ghost("1.2.3=", null)
    ghost("a=b=", null)
    ghost("v2.0+1=", null)
    ghost("year2024+1=", null)
    ghost("example.com/2+2=", null)
    ghost("2, 3, 4=", null)
    ghost("2+2==", null)
    ghost("17*82=1394 19229+193929=", "213158")
    ghost("23+45=68 привет 45+78=", "123")
    ghost("1394 19229+193929=", "213158")
    ghost("итого 100 200+300=", "500")
    ghost("2 3+4=", "7")
    ghost("1394 +5=", "1399")
    ghost("100 =", null)
    ghost("5 5=", null)
    ghost("abc 5 2+3=", "5")
    ghost(">=", null)
    ghost("!=", null)
    ghost("hello", null)
    ghost("=", null)
    ghost("=)", 1, null)
    ghost("2+2=4", 4, null)
    ghost("2+2= привет", 4, "4")
    ghost("2+2=)", 4, "4")
    ghost("2+2= 4", 4, null)
    ghost("2+2=.5", 4, null)
    ghost("2+2= x", 4, "4")
    ghost("2+2=", 3, null)
    ghost("2+2=", 0, null)
    ghost("1,5+1=", "2,5")
    ghost("10/4=", "2,5", ',')

    section("superscript powers in messages")
    ghost("Посчитай 2⁵=", "32")
    ghost("📐 Площадь: (2+3)² =", "25")
    ghost("скидка\nОсталось 2⁻³ =", "0.125")
    ghost("Доля: 2⁻³ =", "0,125", ',')
    ghost("проверка (2,5² + 1) =", "7,25")
    ghost("поправка: (2⁵=), если я правильно записал", "поправка: (2⁵=".length, "32")
    ghost("2⁵= дальше текст", "2⁵=".length, "32")
    ghost("2⁵=\nдальше текст", "2⁵=".length, "32")
    ghost("2⁵=32, дальше текст", "2⁵=".length, null)
    ghost("2⁻³= 0,125", "2⁻³=".length, null)
    ghost("2⁻³=,125", "2⁻³=".length, null)
    ghost("результат: 2⁵", null)
    ghost("выражение x²=", null)
    ghost("формула E=mc²=", null)
    ghost("артикул A2⁵=", null)
    ghost("примечание ⁵=", null)

    val twoPowers = "Сначала 2⁵ =; затем 3² ="
    val firstEquals = twoPowers.indexOf('=') + 1
    ghost(twoPowers, firstEquals, "32")
    ghost(twoPowers, "9")
    ghost(twoPowers, firstEquals - 1, null)
    val firstPower = MathExpression.suggestionAt(twoPowers, firstEquals, MathOptions(grouping = false))
    val withFirstAnswer = firstPower?.let {
        StringBuilder(twoPowers).insert(it.insertAt, it.insertText).toString()
    }
    check("insert first power in a message", "Сначала 2⁵ = 32; затем 3² =", withFirstAnswer)
    withFirstAnswer?.let { ghost(it, "9") }
    ghost("2⁵=32 3²=", "9")
    ghost("2⁵= 3²=", "9")
    ghost("2^5=32\n3²=", "9")
    ghost("2⁵=32; 3^2=", "9")
    ghost("2⁵=, 3²=", "9")

    section("functions and constants")
    expr("sqrt(16)", "4")
    expr("sqrt(2)", "1.41421356237")
    expr("cbrt(27)", "3")
    expr("abs(-7)", "7")
    expr("sign(-3)", "-1")
    expr("round(2.5)", "3")
    expr("floor(2.9)", "2")
    expr("ceil(2.1)", "3")
    expr("min(3;1;2)", "1")
    expr("max(3;1;2)", "3")
    expr("log(1000)", "3")
    expr("log2(1024)", "10")
    expr("ln(1)", "0")
    expr("exp(0)", "1")
    expr("sin(0)", "0")
    expr("cos(0)", "1")
    expr("atan2(1;1)", "0.785398163397")
    expr("2*pi", "6.28318530718")
    expr("2*π", "6.28318530718")
    expr("e", null)
    expr("e*1", "2.71828182846")
    expr("sqrt(16)+2", "6")
    expr("sqrt(4*4)", "4")
    expr("max(2;3)^2", "9")
    expr("√9", "3")
    expr("√9+1", "4")
    expr("5!", "120")
    expr("0!", "1")
    expr("fact(5)", "120")
    expr("3!+1", "7")

    section("remaining functions")
    expr("tan(0)", "0")
    expr("asin(1)", "1.57079632679")
    expr("acos(1)", "0")
    expr("atan(1)", "0.785398163397")
    expr("sinh(0)", "0")
    expr("cosh(0)", "1")
    expr("tanh(0)", "0")
    expr("SQRT(16)", "4")
    expr("Min(3;1)", "1")
    expr("π", null)
    expr("√(16)", "4")

    section("nesting, precedence and limits")
    expr("sqrt(sqrt(16))", "2")
    expr("max(min(5;3);2)", "3")
    expr("sqrt(16)+sqrt(9)", "7")
    expr("2^3!", "64")
    expr("-3!", "-6")
    expr("sqrt(16)%", "0.04")
    expr("100+sqrt(16)%", "104")
    expr("(".repeat(40) + "1+1" + ")".repeat(40), null)
    expr("(((1+1)))", "2")
    ghost("1+".repeat(40) + "1=", null)
    ghost("1+".repeat(20) + "1=", "21")

    section("function errors")
    expr("sqrt(-1)", null)
    expr("ln(0)", null)
    expr("sqrt()", null)
    expr("sqrt(1;2)", null)
    expr("atan2(1)", null)
    expr("nope(1)", null)
    expr("sqrt", null)
    expr("sqrt 4", null)
    expr("2.5!", null)
    expr("(-3)!", null)
    expr("200!", null)

    section("words do not become expressions")
    ghost("привет=", null)
    ghost("hello world=", null)
    ghost("скажи 2+2=", "4")
    ghost("v2.0+1=", null)
    ghost("year2024+1=", null)
    ghost("example.com/2+2=", null)
    ghost("pi=", null)
    ghost("sin=", null)
    ghost("итого sqrt(16)=", "4")
    ghost("2!=", "2")

    section("dates are not divisions")
    ghost("1/2/2024=", null)
    ghost("1/2/24=", null)
    ghost("12/25/24=", null)
    ghost("01.02.2024=", null)
    ghost("2024-01-15=", null)
    ghost("сегодня 1/2/2024=", null)
    ghost("100/5/2=", "10")
    ghost("2024/2=", "1012")
    ghost("10/4=", "2.5")
    ghost("1.5+1=", "2.5")
    ghost("2020-10=", "2010")

    section("ordinary text never triggers")
    ghost("как дела=", null)
    ghost("итого=", null)
    ghost("ответ=", null)
    ghost("Сумма к оплате=", null)
    ghost("width=", null)
    ghost("H2O=", null)
    ghost("E=mc2=", null)
    ghost("это точно так=", null)
    ghost("да=", null)
    ghost("=", null)
    ghost(" =", null)
    ghost("()=", null)
    ghost("()+()=", null)
    ghost("√=", null)
    ghost("π=", null)
    ghost("e=", null)
    ghost("pi=", null)
    ghost("max=", null)
    ghost("sin=", null)
    ghost("f(x)=", null)
    ghost("-e=", null)

    section("code and assignment operators")
    ghost("x=", null)
    ghost("a==", null)
    ghost("i+=", null)
    ghost("x*=", null)
    ghost("x/=", null)
    ghost("x-=", null)
    ghost("2+=", null)
    ghost("2*=", null)
    ghost("a!=", null)
    ghost("a>=", null)
    ghost("a<=", null)
    ghost("count >=", null)
    ghost("if (a != b)=", null)

    section("data that is not arithmetic")
    ghost("16:9=", null)
    ghost("10:30=", null)
    ghost("$100=", null)
    ghost("v1.0=", null)
    ghost("v2.0.1=", null)
    ghost("1=", null)
    ghost("100=", null)
    ghost("2024=", null)
    ghost("?id=", null)
    ghost("&page=", null)
    ghost("https://t.me/a?b=", null)
    ghost("№5=", null)
    ghost("2х2=", null)

    section("long expressions")
    expr("sqrt(16)+max(3;7)*2-min(4;9)+(100-15%)/5+2^6-abs(-8)", "87")
    expr("1+".repeat(30) + "1", "31")
    expr("2*".repeat(10) + "1", "1024")
    expr("100-10%-10%-10%-10%", "65.61")
    expr("0.123456789012345+0", "0.123456789012")
    expr("(".repeat(25) + "1+1" + ")".repeat(25), "2")
    expr("(".repeat(40) + "1+1" + ")".repeat(40), null)
    expr("((1+2)*(3+4)-(5+6))/(7-2)", "2")

    section("scan window boundary")
    ghost("1+".repeat(31) + "11=", "42")
    ghost("1+".repeat(31) + "111=", null)
    ghost("итого " + "1+".repeat(9) + "1=", "10")
    ghost("a" + "1+".repeat(31) + "11=", null)
    ghost("а " + "1+".repeat(31) + "11=", "42")

    section("known permissive cases")
    ghost("2!=", "2")
    ghost("5!=", "120")
    ghost("123-45-67=", "11")
    ghost("+7 999 123-45-67=", "11")
    ghost("1/2/2024=", null)

    section("real usage")
    ghost("1500+2300=", "3800")
    ghost("4500/3=", "1500")
    ghost("2500-15%=", "2125")
    ghost("1200*1.2=", "1440")
    ghost("1000000/7=", "142857.142857")
    ghost("итого: 2+2=", "4")
    ghost("(2+2=", "4")
    ghost("((2+2=", "4")
    ghost("посчитай (10+5)*2=", "30")
    ghost("скажи 2+2=", "4")
    ghost("итого sqrt(16)=", "4")
    ghost("√16+9=", "13")
    ghost("min(3;1)*2=", "2")
    ghost("2^10=", "1024")
    ghost("-5+8=", "3")
    ghost("1,5+1=", "2,5")
    ghost("e*2=", "5.43656365692")
    ghost("это будет 100+15%=", "115")
    inserted("2 + 2 =", " 4")

    section("inserted text")
    inserted("2+2=", "4")
    inserted("2 + 2 =", " 4")
    inserted("100+15%=", "115")
    inserted("5=", null)

    section("money formatting")
    check("formatMoney(150, 2)", "150", moneyText(150.0, 2))
    check("formatMoney(8477.4, 2)", "8 477.40", moneyText(8477.4, 2))
    check("formatMoney(8477.4, 2, ',')", "8 477,40", moneyText(8477.4, 2, ','))
    check("formatMoney(0, 2)", "0", moneyText(0.0, 2))
    check("formatMoney(-50, 2)", "-50", moneyText(-50.0, 2))
    check("formatMoney(-1234567.5, 2)", "-1 234 567.50", moneyText(-1234567.5, 2))
    check("formatMoney(999, 2)", "999", moneyText(999.0, 2))
    check("formatMoney(12500.4, 0)", "12 500", moneyText(12500.4, 0))
    check("formatMoney(0.00004, 2)", "0.00004", moneyText(0.00004, 2))
    check("formatMoney(15264825.3, -1)", "15 264 825", moneyText(15264825.3, -1))
    check("formatMoney(65.54368486, -1)", "65.5437", moneyText(65.54368486, -1))
    check("formatMoney(0.00116847300909, -1)", "0.00116847", moneyText(0.00116847300909, -1))
    check("formatMoney(1e12, 2)", null, moneyText(1e12, 2))
    check("formatMoney(1500) separator", "1 500", MathFormat.formatMoney(1500.0, 2, '.'))
    check("money label separator", "8 000 ₽", MathExpression.suggestionAt("100$=", 5, MathOptions('.', rates, "RUB"))?.value)

    section("currency conversion")
    money("100$ в ₽=", "8 000 ₽")
    money("100 usd to eur=", "80 EUR")
    money("100 USD in EUR=", "80 EUR")
    money("100 долларов в рублях=", "8 000 ₽")
    money("100 Долларов В рублях=", "8 000 ₽")
    money("сколько будет 100 баксов в рублях=", "8 000 ₽")
    money("$100 в евро=", "80 €")
    money("€100 to $=", "125 $")
    money("1000 ₽ в $=", "12.50 $")
    money("10,5 € в $=", "13,13 $")
    money("100$ в иенах=", "12 500 ¥")
    money("100$ в BTC=", "0.001 BTC")
    money("1 ₫ в $=", "0.00004 $")
    money("100 руб. в $=", "1.25 $")
    money("USD 100 в RUB=", "8 000 RUB")
    money("100 TRY в ₽=", "200 ₽")
    money("100$ в ₽ в €=", "80 €")
    money("100$ в $=", "100 $")
    money("100$ в ₽ =", "8 000 ₽")
    money("100$ в ₽=", "8 000 ₽", local = null)

    section("implicit target")
    money("100$=", "8 000 ₽")
    money("100 евро=", "10 000 ₽")
    money("1000₽=", "12.50 $")
    money("100€=", "125 $", local = "USD")
    money("100$=", null, local = "USD")
    money("100$=", null, local = null)
    money("1000₽=", "12.50 $", local = null)
    money("100 тенге=", "16 ₽")
    money("-100$=", "-8 000 ₽")
    money("(100$)=", "8 000 ₽")
    money("100 $ =", "8 000 ₽")
    inserted("2 + 2 =", " 4")

    section("currency arithmetic")
    money("100$+50$=", "150 $")
    money("100$ + 50€=", "162.50 $")
    money("100$ + 15%=", "115 $")
    money("100$ - 150$=", "-50 $")
    money("100$*2=", "200 $")
    money("2*$100=", "200 $")
    money("100$/4=", "25 $")
    money("100$/50$=", "2")
    money("100$*15%=", "15 $")
    money("100$ + 5=", "105 $")
    money("(100$+50$)*2=", "300 $")
    money("(100$ + 50$) в ₽=", "12 000 ₽")
    money("(100$ в ₽)+1000₽=", "9 000 ₽")
    money("100 usd + 50 usd=", "150 USD")

    section("currency errors")
    money("$100*$2=", null)
    money("100$^2=", null)
    money("sqrt(100$)=", null)
    money("√100$=", null)
    money("100$%=", null)
    money("15%$=", null)
    money("100$!=", null)
    money("2/100$=", null)
    money("100 в рублях=", null)
    money("я положил 100$ в банк=", null)
    money("$=", null)
    money("в рублях=", null)
    money("100$$=", null)
    money("$100$=", null)
    money("100 GBP в $=", null)
    money("100$ в ₽=", null, source = offline)
    money("100$=", null, source = offline)
    money("100$+50$=", "150 $", source = offline)

    section("currency words stay text")
    money("2 cup + 1 cup=", null)
    money("try 2+2=", "4")
    money("100 try в ₽=", null)
    money("100 to 200=", null)
    money("в 2+2=", "4")
    money("2 in 1=", null)
    money("pi*2=", "6.28318530718")
    money("e*2=", "5.43656365692")
    money("max(3;1)*2=", "6")
    money("итого 100 200+300=", "500")
    money("1 500=", null)
    money("5 5=", null)
    money("Это 100$. 2+2=", "4")

    section("grouped amounts")
    money("1 500 ₽=", "18.75 $")
    money("1 500 ₽ в $=", "18.75 $")
    money("$1 500=", "120 000 ₽")
    money("10 000,50 ₽ в $=", "125,01 $")
    money("1 000 000 ₽ в $=", "12 500 $")
    money("итого 2 1 500 ₽=", "18.75 $")
    money("1 5000 ₽=", "62.50 $")
    money("1 500 ₽ + 2 000 ₽=", "3 500 ₽")
    money("8 000 ₽ в $=", "100 $")
    money("100$= 8 000 ₽ + 2 000 ₽=", "10 000 ₽")

    section("currencies without calculator")
    money("2+2=", null, arithmetic = false)
    money("sqrt(16)=", null, arithmetic = false)
    money("100$=", "8 000 ₽", arithmetic = false)
    money("100$ в €=", "80 €", arithmetic = false)
    money("100$*2=", "200 $", arithmetic = false)
    money("100$/50$=", null, arithmetic = false)
    money("итого 2+2 и 100$=", "8 000 ₽", arithmetic = false)

    section("long messages")
    money("Слушай, я тут вот подумал, а сколько это примерно будет 100 долларов в рублях=", "8 000 ₽")
    money("Короче, мне тут пришёл счёт за подписку, и там написано 100 евро=", "10 000 ₽")

    section("multipliers")
    full("300к / 12=", "25 000")
    full("5к=", "5 000")
    full("$5k=", "400 000 ₽")
    full("5к$=", "400 000 ₽")
    full("1.5кк руб в $=", "18 750 $")
    full("2 тыс + 300=", "2 300")
    full("3 млн / 2=", "1 500 000")
    full("5 тыс. руб=", "62.50 $")
    full("100 к=", null)
    full("2kkk / 1000=", "2 000 000")
    full("2 kkk / 1000=", null)

    section("percent of")
    full("12% от 1200=", "144")
    full("12% of 1200=", "144")
    full("скидка 15% от 2000=", "300")
    full("1000 + 10% от 500=", "1 050")
    full("10% от 100$=", "10 $")
    full("5 от 10=", null)

    section("stars, gram and aliases")
    full("500 звёзд=", "800 ₽")
    full("500⭐=", "800 ₽")
    full("500⭐\uFE0F=", "800 ₽")
    full("10$ в звёзды=", "500 ⭐")
    full("100 stars to $=", "2 $")
    full("100 евро в звёзды =", "6 250 ⭐")
    full("10 ton=", "4 000 ₽")
    full("10 тон в $=", "50 $")
    full("10 gram=", "4 000 ₽")
    full("10 GRAM в $=", "50 $")
    full("50$ в GRAM=", "10 GRAM")
    full("10 TON в $=", "50 $")
    full("100 грамм=", "0.1 кг")
    full("5 kg + 100 gram=", "5.1 kg")
    full("350р*4=", "1 400 ₽")

    section("units")
    full("72°F=", "22.22 °C")
    full("22 °C=", "71.6 °F")
    full("100℃=", "212 °F")
    full("300 K в °C=", null)
    full("300 кельвинов в цельсиях=", "26.85 °C")
    full("10 миль=", "16.09 км")
    full("10 miles=", "16.09 km")
    full("5 кг в фунтах=", "11.02 lb")
    full("5кг=", "5 000 г")
    full("100 фунтов=", "12 000 ₽")
    full("100 км/ч в mph=", "62.14 mph")
    full("5 м/с=", "18 км/ч")
    full("2 л=", "2 000 мл")
    full("1 га в акрах=", "2.47 ac")
    full("5 км + 300 м=", "5.3 км")
    full("1 500 км=", "932.06 mi")
    full("6 футов в см=", "182.88 см")
    full("5 кг в м=", null)
    full("100 км/ч =", "62.14 mph")
    full("50 узлов =", "92.6 км/ч")
    full("100 м² =", "1 076.39 ft²")
    full("Площадь: 5 000м² в га =", "0.5 га")
    full("Площадь: 2 м² + 3 м² =", "5 м²")
    full("1000 мл =", "1 л")
    full("2 fl oz =", "59.15 ml")
    full("3 фута =", "0.914 м")
    full("0 °C =", "32 °F")
    full("-40 °C =", "-40 °F")
    full("5 км=", "5 000 м", fullOptions(arithmetic = false))
    full("2+2=", null, fullOptions(arithmetic = false))

    section("units in the home system")
    full("10 см =", "0.1 м")
    full("180 см =", "1.8 м")
    full("5 мм =", "0.5 см")
    full("5 см =", "50 мм")
    full("10 мм =", "1 см")
    full("0.3 м =", "30 см")
    full("300 м =", "0.3 км")
    full("1 500 м =", "1.5 км")
    full("50 м =", "5 000 см")
    full("150 км =", "93.21 mi")
    full("250 г =", "0.25 кг")
    full("150 мг =", "0.15 г")
    full("2 500 кг =", "2.5 т")
    full("5 т =", "5 000 кг")
    full("500 мл =", "0.5 л")
    full("5 000 м² =", "0.5 га")
    full("5 га =", "50 000 м²")
    full("300 га =", "3 км²")
    full("-250 г =", "-0.25 кг")
    full("10 см =", "3.94 in", fullOptions(imperial = true))
    full("5кг=", "11.02 lb", fullOptions(imperial = true))
    full("30 inches =", "2.5 ft", fullOptions(imperial = true))
    full("100 yards =", "300 ft", fullOptions(imperial = true))
    full("8 oz =", "0.5 lb", fullOptions(imperial = true))
    full("72°F =", "22.22 °C", fullOptions(imperial = true))
    full("22 °C =", "71.6 °F", fullOptions(imperial = true))

    section("durations")
    full("90 мин=", "1 ч 30 мин")
    full("90 min=", "1 h 30 min")
    full("2 ч + 45 мин=", "2 ч 45 мин")
    full("45 мин=", null)
    full("2.5 часа=", "2 ч 30 мин")
    full("100000 с=", "1 д 3 ч 46 мин 40 с")
    full("2 ч в мин=", "120 мин")
    full("5 лет=", null)
    full("1.5 мин=", "1 мин 30 с")
    full("0.5 мин=", "30 с")
    full("-90 мин =", "-1 ч 30 мин")
    full("100 с =", "1 мин 40 с")

    section("angles")
    full("sin(30)=", "0.5")
    full("cos(90)=", "0")
    full("tan(45)=", "1")
    full("asin(1)=", "90")
    full("sin(30)=", "-0.988031624093", fullOptions(degrees = false))

    section("digit grouping")
    full("1000*1000=", "1 000 000")
    full("1000*1000=", "1000000", fullOptions(grouping = false))
    full("1\u00A0024+1=", "1 025")
    full("8\u00A0000 ₽ + 1=", "8 001 ₽")
    full("100$=", "8000 ₽", fullOptions(grouping = false))

    section("number bases")
    ghost("hex(255) =", "0xFF")
    ghost("bin(10) =", "0b1010")
    ghost("oct(8) =", "0o10")
    ghost("hex(-255) =", "-0xFF")
    ghost("hex(2.5) =", null)
    ghost("hex(0b1111) =", "0xF")
    ghost("bin(2^60) =", null)
    ghost("dec(ff, 16) =", "255")
    ghost("dec(FF; 16) =", "255")
    ghost("dec(1010, 2) =", "10")
    ghost("dec(777, 8) =", "511")
    ghost("dec(0xff) =", "255")
    ghost("dec(0x1f, 16) =", "31")
    ghost("dec(z, 36) =", "35")
    ghost("dec(12, 2) =", null)
    ghost("dec(10, 37) =", null)
    ghost("dec(ff, 16) + 1 =", "256")
    ghost("0xff + 1 =", "256")
    ghost("0b1010 =", "10")
    ghost("0o17 * 2 =", "30")
    ghost("0x1g =", null)
    ghost("255 в hex =", "0xFF")
    ghost("255 to bin =", "0b11111111")
    ghost("0xff in dec =", "255")
    ghost("(255 в hex) + 1 =", null)
    ghost("hex(255) + 1 =", null)
    ghost("max(1, 5) =", "5")
    ghost("max(1,5) =", "1,5")
    ghost("max(1, 2, 3) * 2 =", "6")
    full("hex(100$) =", null)
    full("100$ в hex =", null)

    section("time zones")
    full("18:00 utc=", "21:00")
    full("12:00 в токио=", "18:00")
    full("12:00 in tokyo=", "18:00")
    full("18:00 мск=", null)
    full("6pm est=", "2:00 am (+1)")
    full("в 18:00 по мск в лондоне=", "15:00")
    full("23:00 мск в токио=", "05:00 (+1)")
    full("01:00 мск в нью-йорке=", "17:00 (-1)")
    full("сейчас в нью-йорке=", "04:00")
    full("now in new york=", "04:00")
    full("18:00 utc+5=", "16:00")
    full("18:00 gmt-5=", "02:00 (+1)")
    full("18 cet=", "20:00")
    full("18 привет=", null)
    full("18:00=", null)
    full("18:00 привет=", null)
    full("в 18.00 по мск в екб=", "20:00")
    full("12:00 в нью йорке=", "04:00")
    full("12:00 в нью-йорке=", "05:00", fullOptions(now = summer))
    full("25:00 utc=", null)
    full("13pm utc=", null)
    full("шрифт 12 pt=", null)
    full("6:30 pm est =", "2:30 am (+1)")
    full("18:00 по токио =", "12:00")
    full("в 18:00 мск в мск =", null)
    full("1:00 AM =", "01:00")
    full("1 pm=", "13:00")
    full("12:30 am =", "00:30")
    full("12pm =", "12:00")
    full("13pm =", null)
    full("сейчас в нью-йорке=15:44\nсейчас в москве =", "12:00")
    full("сейчас в токио=10:00 сейчас в нью-йорке=", "04:00")

    section("time zone places")
    full("12:00 в осло=", "10:00")
    full("12:00 in oslo=", "10:00")
    full("12:00 в стокгольме=", "10:00")
    full("12:00 в вене=", "10:00")
    full("12:00 в норвегии=", "10:00")
    full("12:00 в китае=", "17:00")
    full("12:00 на филиппинах=", "17:00")
    full("12:00 на кипре=", "11:00")
    full("12:00 по осло=", "14:00")
    full("12:00 в sao paulo=", "06:00")
    full("12:00 в осле=", null)
    full("12:00 в домах=", null)
    full("12:00 на дне=", null)
    full("12:00 в осло=", null, fullOptions(places = null))
    full("12:00 в праге=", "10:00", fullOptions(places = null))

    section("feature switches")
    full("72°F =", null, fullOptions(units = false))
    full("90 мин =", null, fullOptions(units = false))
    full("18:00 utc =", null, fullOptions(zones = false))
    full("100 фунтов =", "45.36 кг", fullOptions(currencies = false))
    full("100 gram =", "0.1 kg", fullOptions(currencies = false))
    full("500 звёзд =", null, fullOptions(currencies = false))
    full("hex(255) =", null, fullOptions(arithmetic = false))
    full("12% от 1200 =", null, fullOptions(arithmetic = false))
    full("Посчитай 2⁵ =", null, fullOptions(arithmetic = false))
    full("5к$ =", "400 000 ₽", fullOptions(arithmetic = false))
    full("18:00 utc =", "21:00", fullOptions(arithmetic = false))

    section("known permissive units")
    full("5 км * 3 м =", "300 см")
    full("2024 г. =", "2.02 кг")

    section("documentation examples")
    val berlin = fullOptions(local = ZoneId.of("Europe/Berlin"))
    val documented = mapOf(
        "(120 + 80) / 4 =" to "50",
        "√144 + 2^3 =" to "20",
        "max(12, 40, 7) =" to "40",
        "hex(255) =" to "0xFF",
        "dec(ff, 16) =" to "255",
        "0xff + 1 =" to "256",
        "sin(30) =" to "0.5",
        "2300 - 15% =" to "1 955",
        "12% of 1200 =" to "144",
        "12% от 1200 =" to "144",
        "300k / 12 =" to "25 000",
        "300к / 12 =" to "25 000",
        "100€ =" to "10 000 ₽",
        "500 stars =" to "800 ₽",
        "500 звёзд =" to "800 ₽",
        "100 dollars to euros =" to "80 €",
        "100 долларов в евро =" to "80 €",
        "5k ₽ to $ =" to "62.50 $",
        "5к ₽ в $ =" to "62.50 $",
        "50$ to GRAM =" to "10 GRAM",
        "50$ в GRAM =" to "10 GRAM",
        "72°F =" to "22.22 °C",
        "10 miles =" to "16.09 km",
        "10 миль =" to "16.09 км",
        "5 kg to lb =" to "11.02 lb",
        "5 кг в фунтах =" to "11.02 lb",
        "100 km/h to mph =" to "62.14 mph",
        "100 км/ч в mph =" to "62.14 mph",
        "90 min =" to "1 h 30 min",
        "90 мин =" to "1 ч 30 мин",
        "2 h + 45 min =" to "2 h 45 min",
        "2 ч + 45 мин =" to "2 ч 45 мин",
        "6pm est =" to "12:00 am (+1)",
        "18:00 utc =" to "19:00",
        "12:00 in tokyo =" to "20:00",
        "12:00 в токио =" to "20:00",
        "now in new york =" to "04:00",
        "сейчас в нью-йорке =" to "04:00"
    )
    for (group in InlineExamples.all) {
        for (russian in listOf(false, true)) {
            for (example in group.examples(russian)) {
                val suggestion = MathExpression.suggestionAt(example, example.length, berlin)
                check("example(\"$example\")", documented[example], suggestion?.value?.replace(MathFormat.NO_BREAK_SPACE, ' '))
            }
        }
    }

    println()
    if (failures.isEmpty()) {
        println("all $passed checks passed")
    } else {
        failures.forEach { println("FAIL  $it") }
        println()
        println("${failures.size} failed, $passed passed")
        kotlin.system.exitProcess(1)
    }
}
