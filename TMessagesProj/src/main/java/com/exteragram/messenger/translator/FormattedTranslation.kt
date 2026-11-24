/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.translator

import android.text.Html
import androidx.core.text.htmlEncode

import com.exteragram.messenger.translator.core.HttpTranslator
import com.exteragram.messenger.translator.core.TranslationError

import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import org.telegram.messenger.Utilities
import org.telegram.tgnet.SerializedData
import org.telegram.tgnet.TLRPC
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

object FormattedTranslation {

    fun interface Callback {
        fun onResult(results: List<TLRPC.TL_textWithEntities?>, error: TranslationError?)
    }

    private class Job(val dialogId: Long) {
        var cancelled = false
    }

    private class Segment(val text: String, val entities: List<Int>, val protected: Boolean)
    private class Part(
        val message: Int, val html: String, val segments: List<Segment>,
        val leading: Decoded, val trailing: Decoded
    )
    private class Range(val entity: Int, val start: Int, var end: Int)
    private class Decoded(val text: String, val ranges: List<Range>)

    @Suppress("SpellCheckingInspection")
    private const val PROTECTED_ATTRIBUTES = " translate=\"no\" class=\"notranslate\""

    private val jobs = HashSet<Job>()

    internal fun cancel(dialogId: Long?) {
        for (job in jobs) {
            if (dialogId == null || job.dialogId == dialogId) {
                job.cancelled = true
            }
        }
    }

    @JvmStatic
    fun translate(
        dialogId: Long,
        input: List<TLRPC.TL_textWithEntities>,
        fromLang: String,
        toLang: String,
        translator: HttpTranslator,
        priority: Boolean,
        callback: Callback
    ) {
        AndroidUtilities.runOnUIThread {
            val sources = input.map { source ->
                TLRPC.TL_textWithEntities().apply {
                    text = source.text
                    entities = ArrayList(source.entities)
                }
            }
            val job = Job(dialogId)
            jobs.add(job)
            val useHtml = sources.any { it.entities.isNotEmpty() }
            Utilities.globalQueue.postRunnable {
                val parts = try {
                    if (useHtml) sources.flatMapIndexed { index, source ->
                        split(index, source, minOf(translator.limits.maxCharsPerText, translator.limits.maxCharsPerRequest))
                    } else emptyList()
                } catch (e: Exception) {
                    FileLog.e(e)
                    AndroidUtilities.runOnUIThread { finish(job, sources.map { null }, TranslationError.Fatal, callback) }
                    return@postRunnable
                }
                AndroidUtilities.runOnUIThread enqueue@{
                    if (job.cancelled) {
                        finish(job, sources.map { null }, TranslationError.Cancelled, callback)
                        return@enqueue
                    }
                    ChatTranslator.translate(
                        dialogId, if (useHtml) parts.map { it.html } else sources.map { it.text ?: "" },
                        toLang, translator, fromLang, useHtml, priority
                    ) { results, error ->
                        Utilities.globalQueue.postRunnable {
                            var failure = error
                            val decoded = results.mapIndexed { index, text ->
                                if (text == null) null else try {
                                    if (useHtml) decode(parts[index], text) else Decoded(text, emptyList())
                                } catch (e: Exception) {
                                    FileLog.e(e)
                                    if (useHtml) ChatTranslator.invalidate(translator, fromLang, toLang, parts[index].html)
                                    if (failure == null) failure = TranslationError.Transient
                                    null
                                }
                            }
                            val output = sources.mapIndexed { index, source ->
                                val indices = if (useHtml) parts.indices.filter { parts[it].message == index } else listOf(index)
                                if (indices.any { decoded[it] == null }) null else try {
                                    val text = StringBuilder()
                                    val ranges = ArrayList<Range>()
                                    for (i in indices) {
                                        val part = decoded[i] ?: continue
                                        for (range in part.ranges) {
                                            ranges.add(Range(range.entity, text.length + range.start, text.length + range.end))
                                        }
                                        text.append(part.text)
                                    }
                                    TLRPC.TL_textWithEntities().apply {
                                        this.text = text.toString()
                                        val merged = ArrayList<Range>()
                                        for (range in ranges.sortedWith(compareBy({ it.entity }, { it.start }))) {
                                            val last = merged.lastOrNull()
                                            if (last != null && last.entity == range.entity && last.end <= range.start &&
                                                text.substring(last.end, range.start).isBlank()) {
                                                last.end = range.end
                                            } else {
                                                merged.add(range)
                                            }
                                        }
                                        for (range in merged) {
                                            if (range.end > range.start) {
                                                entities.add(copyEntity(source.entities[range.entity], range.start, range.end))
                                            }
                                        }
                                        entities.sortBy { it.offset }
                                    }
                                } catch (e: Exception) {
                                    FileLog.e(e)
                                    if (failure == null) failure = TranslationError.Fatal
                                    null
                                }
                            }
                            AndroidUtilities.runOnUIThread { finish(job, output, failure, callback) }
                        }
                    }
                }
            }
        }
    }

    private fun finish(job: Job, results: List<TLRPC.TL_textWithEntities?>, error: TranslationError?, callback: Callback) {
        jobs.remove(job)
        callback.onResult(if (job.cancelled) results.map { null } else results, if (job.cancelled) TranslationError.Cancelled else error)
    }

    private fun split(message: Int, source: TLRPC.TL_textWithEntities, limit: Int): List<Part> {
        val text = source.text ?: ""
        if (text.length <= limit) {
            val whole = encode(message, source, 0, text.length)
            if (whole.html.length <= limit) return listOf(whole)
        }
        val parts = ArrayList<Part>()
        var start = 0
        do {
            var low = start
            var high = minOf(text.length, start + limit)
            while (low < high) {
                val mid = low + (high - low + 1) / 2
                if (encode(message, source, start, mid).html.length <= limit) low = mid else high = mid - 1
            }
            var end = low
            if (end < text.length) {
                val newline = text.lastIndexOf('\n', end - 1)
                val space = text.lastIndexOf(' ', end - 1)
                val boundary = if (newline > start) newline else space
                if (boundary > start) end = boundary + 1
                if (end > start && Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
            }
            require(end > start || text.isEmpty())
            parts.add(encode(message, source, start, end))
            start = end
        } while (start < text.length)
        return parts
    }

    private fun encode(message: Int, source: TLRPC.TL_textWithEntities, start: Int, end: Int): Part {
        val text = source.text ?: ""
        val valid = source.entities.indices.filter {
            val entity = source.entities[it]
            entity.offset >= 0 && entity.length > 0 && entity.offset.toLong() + entity.length <= text.length &&
                entity.offset < end && entity.offset + entity.length > start
        }
        var contentStart = start
        while (contentStart < end && text[contentStart].isWhitespace()) contentStart++
        var contentEnd = end
        while (contentEnd > contentStart && text[contentEnd - 1].isWhitespace()) contentEnd--
        val boundaries = sortedSetOf(contentStart, contentEnd)
        for (i in valid) {
            val entity = source.entities[i]
            boundaries.add(entity.offset.coerceIn(contentStart, contentEnd))
            boundaries.add((entity.offset + entity.length).coerceIn(contentStart, contentEnd))
        }
        val html = StringBuilder()
        val segments = ArrayList<Segment>()
        for ((left, right) in boundaries.zipWithNext()) {
            val active = valid.filter { source.entities[it].offset <= left && source.entities[it].offset + source.entities[it].length >= right }
            val value = text.substring(left, right)
            val escaped = value.htmlEncode().replace("\n", "<br>").replace("\r", "&#13;").replace("\t", "&#9;")
            if (active.isEmpty()) {
                html.append(escaped)
            } else {
                val protected = active.any {
                    when (source.entities[it]) {
                        is TLRPC.TL_messageEntityBold, is TLRPC.TL_messageEntityItalic,
                        is TLRPC.TL_messageEntityUnderline, is TLRPC.TL_messageEntityStrike,
                        is TLRPC.TL_messageEntitySpoiler, is TLRPC.TL_messageEntityBlockquote,
                        is TLRPC.TL_messageEntityTextUrl -> false
                        else -> true
                    }
                }
                html.append("<span id=\"tg${segments.size}\"")
                if (protected) html.append(PROTECTED_ATTRIBUTES)
                html.append('>').append(escaped).append("</span>")
                segments.add(Segment(value, active, protected))
            }
        }
        return Part(message, html.toString(), segments, originalRange(source, valid, start, contentStart), originalRange(source, valid, contentEnd, end))
    }

    private fun originalRange(source: TLRPC.TL_textWithEntities, entities: List<Int>, start: Int, end: Int): Decoded {
        val ranges = entities.mapNotNull { index ->
            val entity = source.entities[index]
            val left = maxOf(start, entity.offset)
            val right = minOf(end, entity.offset + entity.length)
            if (left < right) Range(index, left - start, right - start) else null
        }
        return Decoded((source.text ?: "").substring(start, end), ranges)
    }

    private fun decode(part: Part, html: String): Decoded {
        val text = StringBuilder()
        val ranges = ArrayList<Range>()
        val seen = HashSet<Int>()
        val handler = object : DefaultHandler() {
            var active: Int? = null
            private var start = 0
            private var depth = 0
            private var activeDepth = 0

            override fun startElement(uri: String?, localName: String, qName: String?, attributes: Attributes) {
                depth++
                if (localName == "script" || localName == "style") throw SAXException("Unexpected translation markup")
                if (localName == "br") text.append('\n')
                val id = attributes.getValue("id")
                if (id != null && id.startsWith("tg")) {
                    val index = id.substring(2).toIntOrNull()
                    if (index == null || index !in part.segments.indices || active != null) throw SAXException("Invalid translation span")
                    active = index
                    activeDepth = depth
                    start = text.length
                    if (!seen.add(index)) throw SAXException("Duplicate translation span")
                }
            }

            override fun characters(ch: CharArray, offset: Int, length: Int) {
                text.appendRange(ch, offset, offset + length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                val index = active
                if (index != null && depth == activeDepth) {
                    val segment = part.segments[index]
                    if (segment.protected) {
                        text.setLength(start)
                        text.append(segment.text)
                    }
                    for (entity in segment.entities) ranges.add(Range(entity, start, text.length))
                    active = null
                }
                depth--
            }
        }
        Html.fromHtml("<translation-root>$html</translation-root>", Html.FROM_HTML_MODE_LEGACY, null) { opening, tag, _, reader ->
            if (opening && tag == "translation-root") reader.contentHandler = handler
        }
        require(handler.active == null && seen.size == part.segments.size)
        require(part.html.isEmpty() || text.isNotBlank())
        var contentStart = 0
        while (contentStart < text.length && text[contentStart].isWhitespace()) contentStart++
        var contentEnd = text.length
        while (contentEnd > contentStart && text[contentEnd - 1].isWhitespace()) contentEnd--
        val restoredRanges = ArrayList<Range>()
        restoredRanges.addAll(part.leading.ranges)
        for (range in ranges) {
            val start = range.start.coerceIn(contentStart, contentEnd) - contentStart + part.leading.text.length
            val end = range.end.coerceIn(contentStart, contentEnd) - contentStart + part.leading.text.length
            if (start < end) restoredRanges.add(Range(range.entity, start, end))
        }
        val trailingOffset = part.leading.text.length + contentEnd - contentStart
        for (range in part.trailing.ranges) {
            restoredRanges.add(Range(range.entity, trailingOffset + range.start, trailingOffset + range.end))
        }
        return Decoded(part.leading.text + text.substring(contentStart, contentEnd) + part.trailing.text, restoredRanges)
    }

    private fun copyEntity(source: TLRPC.MessageEntity, start: Int, end: Int): TLRPC.MessageEntity {
        val output = SerializedData()
        try {
            source.serializeToStream(output)
            val input = SerializedData(output.toByteArray())
            try {
                return TLRPC.MessageEntity.TLdeserialize(input, input.readInt32(true), true).apply {
                    offset = start
                    length = end - start
                    if (this is TLRPC.TL_messageEntityCustomEmoji && source is TLRPC.TL_messageEntityCustomEmoji) document = source.document
                }
            } finally {
                input.cleanup()
            }
        } finally {
            output.cleanup()
        }
    }
}
