/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.inline.time

import java.time.DateTimeException
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale

class TimeZoneOptions(val local: ZoneId, val now: ZonedDateTime? = null, val places: ((String) -> String?)? = null)

object TimeZones {

    private const val MAX_SCAN = 64
    private const val MAX_ZONE_WORDS = 3
    private const val MOSCOW_OFFSET_HOURS = 3
    private const val MAX_OFFSET_HOURS = 18

    private val targetMarkers = setOf("в", "во", "на", "to", "in")
    private val sourceMarkers = setOf("по")
    private val nowWords = setOf("сейчас", "now")
    private val meridiems = setOf("am", "pm", "a.m.", "p.m.")

    private val timePattern = Regex("^(\\d{1,2})(?:[:.](\\d{2}))?(am|pm|a\\.m\\.|p\\.m\\.)?$")
    private val offsetPattern = Regex("^(utc|gmt|msk|мск) ?([+-]) ?(\\d{1,2})(?::?(\\d{2}))?$")

    private val caseEndings = listOf(
        "ии" to listOf("ия"),
        "ах" to listOf("ы", "и"),
        "ях" to listOf("и"),
        "е" to listOf("", "а", "я", "ь", "й"),
        "и" to listOf("ь")
    )

    private val zones = HashMap<String, String>()

    init {
        fun add(zone: String, vararg names: String) = names.forEach { zones[normalize(it)] = zone }

        add("UTC", "utc")
        add("GMT", "gmt")
        add("Europe/Moscow", "msk", "мск", "москва", "москве", "moscow", "питер", "питере", "спб", "петербург", "петербурге",
            "санкт-петербург", "санкт-петербурге", "st petersburg", "saint petersburg")
        add("Europe/Kaliningrad", "калининград", "калининграде", "kaliningrad")
        add("Europe/Samara", "самара", "самаре", "samara")
        add("Asia/Yekaterinburg", "екб", "екатеринбург", "екатеринбурге", "yekaterinburg")
        add("Asia/Omsk", "омск", "омске", "omsk")
        add("Asia/Novosibirsk", "нск", "новосибирск", "новосибирске", "novosibirsk")
        add("Asia/Krasnoyarsk", "красноярск", "красноярске", "krasnoyarsk")
        add("Asia/Irkutsk", "иркутск", "иркутске", "irkutsk")
        add("Asia/Yakutsk", "якутск", "якутске", "yakutsk")
        add("Asia/Vladivostok", "владивосток", "владивостоке", "vladivostok")
        add("Asia/Magadan", "магадан", "магадане", "magadan")
        add("Asia/Kamchatka", "камчатка", "камчатке", "kamchatka")
        add("Europe/Kiev", "киев", "киеве", "київ", "києві", "kyiv", "kiev")
        add("Europe/Minsk", "минск", "минске", "minsk")
        add("Asia/Almaty", "алматы", "almaty", "астана", "астане", "astana")
        add("Asia/Tashkent", "ташкент", "ташкенте", "tashkent")
        add("Asia/Bishkek", "бишкек", "бишкеке", "bishkek")
        add("Asia/Dushanbe", "душанбе", "dushanbe")
        add("Asia/Tbilisi", "тбилиси", "tbilisi")
        add("Asia/Yerevan", "ереван", "ереване", "yerevan")
        add("Asia/Baku", "баку", "baku")
        add("Europe/Chisinau", "кишинев", "кишиневе", "chisinau")
        add("Europe/Riga", "рига", "риге", "riga")
        add("Europe/Vilnius", "вильнюс", "вильнюсе", "vilnius")
        add("Europe/Tallinn", "таллин", "таллине", "tallinn")
        add("Europe/Helsinki", "eet", "eest", "хельсинки", "helsinki")
        add("Europe/Warsaw", "варшава", "варшаве", "warsaw")
        add("Europe/Prague", "прага", "праге", "prague")
        add("Europe/Berlin", "cet", "cest", "берлин", "берлине", "berlin")
        add("Europe/Paris", "париж", "париже", "paris")
        add("Europe/Rome", "рим", "риме", "rome")
        add("Europe/Madrid", "мадрид", "мадриде", "madrid", "барселона", "барселоне", "barcelona")
        add("Europe/Amsterdam", "амстердам", "амстердаме", "amsterdam")
        add("Europe/London", "bst", "лондон", "лондоне", "london")
        add("Europe/Lisbon", "wet", "west", "лиссабон", "лиссабоне", "lisbon")
        add("Europe/Istanbul", "стамбул", "стамбуле", "istanbul", "анталья", "анталье", "antalya")
        add("Asia/Jerusalem", "тель-авив", "тель-авиве", "tel aviv", "иерусалим", "иерусалиме", "jerusalem")
        add("Asia/Dubai", "дубай", "дубае", "dubai")
        add("Africa/Cairo", "каир", "каире", "cairo")
        add("Asia/Kolkata", "ist", "дели", "delhi", "new delhi", "мумбаи", "mumbai")
        add("Asia/Bangkok", "бангкок", "бангкоке", "bangkok", "пхукет", "пхукете", "phuket")
        add("Asia/Makassar", "бали", "bali")
        add("Asia/Singapore", "sgt", "сингапур", "сингапуре", "singapore")
        add("Asia/Hong_Kong", "hkt", "гонконг", "гонконге", "hong kong")
        add("Asia/Shanghai", "пекин", "пекине", "beijing", "шанхай", "шанхае", "shanghai")
        add("Asia/Seoul", "kst", "сеул", "сеуле", "seoul")
        add("Asia/Tokyo", "jst", "токио", "tokyo")
        add("Australia/Perth", "awst", "перт", "перте", "perth")
        add("Australia/Sydney", "aest", "aedt", "сидней", "сиднее", "sydney")
        add("Australia/Melbourne", "мельбурн", "мельбурне", "melbourne")
        add("Pacific/Auckland", "nzst", "nzdt", "окленд", "окленде", "auckland")
        add("America/New_York", "est", "edt", "нью-йорк", "нью-йорке", "new york", "nyc", "вашингтон", "вашингтоне",
            "washington", "майами", "miami")
        add("America/Toronto", "торонто", "toronto")
        add("America/Chicago", "cst", "cdt", "чикаго", "chicago")
        add("America/Denver", "mst", "mdt", "денвер", "денвере", "denver")
        add("America/Los_Angeles", "pst", "pdt", "лос-анджелес", "лос-анджелесе", "los angeles", "сан-франциско",
            "san francisco", "сиэтл", "сиэтле", "seattle")
        add("America/Vancouver", "ванкувер", "ванкувере", "vancouver")
        add("America/Anchorage", "akst", "akdt", "анкоридж", "анкоридже", "anchorage")
        add("Pacific/Honolulu", "hst", "гонолулу", "honolulu")
        add("America/Mexico_City", "мехико", "mexico city")
        add("America/Sao_Paulo", "сан-паулу", "sao paulo")
        add("America/Argentina/Buenos_Aires", "буэнос-айрес", "буэнос-айресе", "buenos aires")
    }

    fun valueAt(text: CharSequence, equalsIndex: Int, options: TimeZoneOptions): String? {
        var from = maxOf(0, equalsIndex - MAX_SCAN)
        for (i in equalsIndex - 1 downTo from) {
            if (text[i] == '\n' || text[i] == '=') {
                from = i + 1
                break
            }
        }
        val words = text.subSequence(from, equalsIndex).toString()
            .lowercase(Locale.ROOT)
            .replace('ё', 'е')
            .replace('−', '-')
            .split(' ', '\t', '\u00A0')
            .filter { it.isNotEmpty() }
        var end = words.size

        var target: ZoneId? = null
        for (count in MAX_ZONE_WORDS downTo 1) {
            if (end - count - 1 < 0 || words[end - count - 1] !in targetMarkers) {
                continue
            }
            target = zone(words, end - count, end, options.places) ?: continue
            end -= count + 1
            break
        }

        var source: ZoneId? = null
        for (count in MAX_ZONE_WORDS downTo 1) {
            if (end - count < 1) {
                continue
            }
            source = zone(words, end - count, end, options.places) ?: continue
            end -= count
            if (words[end - 1] in sourceMarkers && end > 1) {
                end--
            }
            break
        }

        if (end < 1) {
            return null
        }
        var time = words[end - 1]
        if (time in meridiems && end >= 2) {
            time = words[end - 2] + time
        }
        if (source == null && target == null && meridiems.none { time.endsWith(it) }) {
            return null
        }

        val local = options.local
        val now = options.now ?: ZonedDateTime.now(local)
        if (time in nowWords) {
            if (target == null || source != null) {
                return null
            }
            return format(now.withZoneSameInstant(target).toLocalTime(), null)
        }

        val match = timePattern.matchEntire(time) ?: return null
        var hour = match.groupValues[1].toInt()
        val minutes = match.groupValues[2]
        val meridiem = match.groupValues[3].ifEmpty { null }
        if (minutes.isEmpty() && meridiem == null && source == null) {
            return null
        }
        val minute = if (minutes.isEmpty()) 0 else minutes.toInt()
        if (minute > 59) {
            return null
        }
        if (meridiem != null) {
            if (hour !in 1..12) {
                return null
            }
            hour = hour % 12 + if (meridiem.startsWith('p')) 12 else 0
        } else if (hour > 23) {
            return null
        }

        if (source == null && target == null) {
            return format(LocalTime.of(hour, minute), null)
        }

        val sourceZone = source ?: local
        val targetZone = target ?: local
        val start = ZonedDateTime.of(now.withZoneSameInstant(sourceZone).toLocalDate(), LocalTime.of(hour, minute), sourceZone)
        val converted = start.withZoneSameInstant(targetZone)
        if (converted.toLocalDateTime() == start.toLocalDateTime()) {
            return null
        }
        val shift = converted.toLocalDate().toEpochDay() - start.toLocalDate().toEpochDay()
        val value = format(converted.toLocalTime(), meridiem)
        return when {
            shift > 0 -> "$value (+$shift)"
            shift < 0 -> "$value ($shift)"
            else -> value
        }
    }

    private fun format(time: LocalTime, meridiem: String?): String {
        val minute = time.minute.toString().padStart(2, '0')
        if (meridiem == null) {
            return "${time.hour.toString().padStart(2, '0')}:$minute"
        }
        val hour = (time.hour + 11) % 12 + 1
        val suffix = if (time.hour < 12) "am" else "pm"
        return "$hour:$minute $suffix"
    }

    private fun zone(words: List<String>, from: Int, to: Int, places: ((String) -> String?)?): ZoneId? {
        val key = words.subList(from, to).joinToString(" ")
        for (name in forms(normalize(key))) {
            val id = zones[name] ?: places?.invoke(name) ?: continue
            return try {
                ZoneId.of(id)
            } catch (e: DateTimeException) {
                null
            }
        }
        val match = offsetPattern.matchEntire(key) ?: return null
        val base = if (match.groupValues[1] == "msk" || match.groupValues[1] == "мск") MOSCOW_OFFSET_HOURS * 60 else 0
        val hours = match.groupValues[3].toInt()
        val minutes = match.groupValues[4].ifEmpty { "0" }.toInt()
        if (hours > MAX_OFFSET_HOURS || minutes > 59) {
            return null
        }
        val sign = if (match.groupValues[2] == "-") -1 else 1
        val total = base + sign * (hours * 60 + minutes)
        return try {
            ZoneOffset.ofTotalSeconds(total * 60)
        } catch (e: DateTimeException) {
            null
        }
    }

    private fun forms(name: String): List<String> {
        val (ending, replacements) = caseEndings.firstOrNull { name.endsWith(it.first) && name.length > it.first.length + 2 }
            ?: return listOf(name)
        val stem = name.dropLast(ending.length)
        return listOf(name) + replacements.map { stem + it }
    }

    fun normalize(name: String): String = name.lowercase(Locale.ROOT).replace('ё', 'е').replace('-', ' ')
}
