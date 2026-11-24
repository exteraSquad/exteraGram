/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.translator

import org.telegram.messenger.AndroidUtilities

import com.exteragram.messenger.translator.core.BaseTranslator
import com.exteragram.messenger.translator.core.HttpTranslator
import com.exteragram.messenger.translator.core.ProviderLimits
import com.exteragram.messenger.translator.core.TranslationDispatcher
import com.exteragram.messenger.translator.core.TranslationError

object ChatTranslator {

    fun interface Callback {
        fun onResult(results: List<String?>, error: TranslationError?)
    }

    private const val CACHE_LIMIT = 300

    private val cache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, String>) = size > CACHE_LIMIT
    }

    private class Piece(val messageIndex: Int, val text: String) {
        var translated: String? = null
    }

    @JvmStatic
    fun translate(dialogId: Long, texts: List<String>, toLang: String, callback: Callback) {
        translate(dialogId, texts, toLang, TranslatorUtils.getCurrentTranslator(), callback)
    }

    @JvmStatic
    fun translate(dialogId: Long, texts: List<String>, toLang: String, current: BaseTranslator, callback: Callback) {
        translate(dialogId, texts, toLang, current, "auto", false, false, callback)
    }

    internal fun translate(
        dialogId: Long, texts: List<String>, toLang: String, current: BaseTranslator,
        fromLang: String, html: Boolean, priority: Boolean, callback: Callback
    ) {
        if (current !is HttpTranslator) {
            AndroidUtilities.runOnUIThread {
                callback.onResult(arrayOfNulls<String>(texts.size).toList(), TranslationError.Fatal)
            }
            return
        }
        val translator: HttpTranslator = current
        val limits = translator.limits

        val results = arrayOfNulls<String>(texts.size)
        val pieces = ArrayList<Piece>()
        for (i in texts.indices) {
            val text = texts[i]
            if (text.isBlank()) {
                results[i] = text
                continue
            }
            val cached = synchronized(cache) { cache[cacheKey(translator, fromLang, toLang, html, text)] }
            if (cached != null) {
                results[i] = cached
                continue
            }
            for (part in if (html) listOf(text) else split(text, limits.maxCharsPerText)) {
                pieces.add(Piece(i, part))
            }
        }

        if (pieces.isEmpty()) {
            AndroidUtilities.runOnUIThread { callback.onResult(results.toList(), null) }
            return
        }

        val chunks = chunk(pieces, limits)
        var remaining = chunks.size
        var firstError: TranslationError? = null

        for (chunk in chunks) {
            TranslationDispatcher.enqueue(
                translator,
                dialogId,
                chunk.map { it.text },
                fromLang,
                toLang,
                priority,
                html
            ) { translated, error ->
                if (translated != null && translated.size == chunk.size) {
                    for (i in chunk.indices) {
                        chunk[i].translated = translated[i]
                    }
                } else if (error is TranslationError.Cancelled || firstError == null) {
                    firstError = error ?: TranslationError.Transient
                }
                remaining--
                if (remaining == 0) {
                    assemble(translator, fromLang, toLang, html, texts, pieces, results)
                    callback.onResult(results.toList(), firstError)
                }
            }
        }
    }

    @JvmStatic
    fun cancel(dialogId: Long) {
        FormattedTranslation.cancel(dialogId)
        TranslationDispatcher.cancel(dialogId)
    }

    @JvmStatic
    fun cancelAll() {
        FormattedTranslation.cancel(null)
        TranslationDispatcher.cancelAll()
    }

    @JvmStatic
    fun clearCache() {
        synchronized(cache) { cache.clear() }
    }

    internal fun invalidate(translator: BaseTranslator, fromLang: String, toLang: String, text: String) {
        synchronized(cache) { cache.remove(cacheKey(translator, fromLang, toLang, true, text)) }
    }

    private fun assemble(
        translator: HttpTranslator,
        fromLang: String,
        toLang: String,
        html: Boolean,
        texts: List<String>,
        pieces: List<Piece>,
        results: Array<String?>
    ) {
        val joined = StringBuilder()
        var index = 0
        while (index < pieces.size) {
            val messageIndex = pieces[index].messageIndex
            var end = index
            var complete = true
            while (end < pieces.size && pieces[end].messageIndex == messageIndex) {
                if (pieces[end].translated == null) {
                    complete = false
                }
                end++
            }
            if (complete) {
                joined.setLength(0)
                for (i in index until end) {
                    val original = pieces[i].text
                    val translated = pieces[i].translated ?: continue
                    if (html) {
                        joined.append(translated)
                    } else if (original.isBlank()) {
                        joined.append(original)
                    } else {
                        joined.append(original.takeWhile { it.isWhitespace() })
                        joined.append(translated.trim())
                        joined.append(original.takeLastWhile { it.isWhitespace() })
                    }
                }
                val value = joined.toString()
                results[messageIndex] = value
                synchronized(cache) { cache[cacheKey(translator, fromLang, toLang, html, texts[messageIndex])] = value }
            }
            index = end
        }
    }

    private fun split(text: String, limit: Int): List<String> {
        if (text.length <= limit) {
            return listOf(text)
        }
        val parts = ArrayList<String>()
        var start = 0
        while (start < text.length) {
            if (text.length - start <= limit) {
                parts.add(text.substring(start))
                break
            }
            val end = start + limit
            var cut = text.lastIndexOf('\n', end - 1)
            if (cut <= start) {
                cut = text.lastIndexOf(' ', end - 1)
            }
            if (cut <= start) {
                cut = end
            } else {
                cut++
            }
            if (cut < text.length && Character.isHighSurrogate(text[cut - 1]) && Character.isLowSurrogate(text[cut])) cut--
            parts.add(text.substring(start, cut))
            start = cut
        }
        return parts
    }

    private fun chunk(pieces: List<Piece>, limits: ProviderLimits): List<List<Piece>> {
        val chunks = ArrayList<List<Piece>>()
        var current = ArrayList<Piece>()
        var chars = 0
        for (piece in pieces) {
            if (current.isNotEmpty() &&
                (current.size >= limits.maxTextsPerRequest || chars + piece.text.length > limits.maxCharsPerRequest)
            ) {
                chunks.add(current)
                current = ArrayList()
                chars = 0
            }
            current.add(piece)
            chars += piece.text.length
        }
        if (current.isNotEmpty()) {
            chunks.add(current)
        }
        return chunks
    }

    private fun cacheKey(translator: BaseTranslator, fromLang: String, toLang: String, html: Boolean, text: String) =
        "${translator.javaClass.simpleName} $fromLang $toLang $html $text"
}