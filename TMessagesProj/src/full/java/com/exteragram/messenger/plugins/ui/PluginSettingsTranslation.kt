/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.ui

import com.exteragram.messenger.translator.ChatTranslator
import com.exteragram.messenger.translator.TranslatorUtils
import com.exteragram.messenger.translator.core.HttpTranslator
import com.exteragram.messenger.translator.providers.GoogleTranslator
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LanguageDetector
import org.telegram.messenger.TranslateController
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC

class PluginSettingsTranslation private constructor() {

    fun interface Listener {
        fun onTranslationChanged(failed: Boolean)
    }

    private val listeners = ArrayList<Listener>()
    private val translations = HashMap<String, String>()
    private val pending = HashSet<String>()
    private val failed = HashSet<String>()
    private var targetLanguage: String? = null
    private var detectedLanguage: String? = null
    private var detecting = false

    var enabled = false
        private set

    val isLoading: Boolean
        get() = pending.isNotEmpty()

    val isTranslatable: Boolean
        get() = detectedLanguage?.let { !TranslatorUtils.isRestrictedLanguage(it) } ?: false

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun resetFailed() {
        failed.clear()
    }

    fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) {
            return
        }
        this.enabled = enabled
        if (enabled) {
            failed.clear()
        }
        notifyListeners(false)
    }

    fun text(original: String?): String? {
        if (!enabled || original == null) {
            return original
        }
        return translations[original] ?: original
    }

    fun check(texts: Collection<String>) {
        val language = TranslatorUtils.getResolvedTargetLanguageCode()
        if (language != targetLanguage) {
            targetLanguage = language
            translations.clear()
            failed.clear()
        }

        if (detectedLanguage == null && !detecting && texts.isNotEmpty() && LanguageDetector.hasSupport()) {
            detect(texts)
        }

        if (!enabled) {
            return
        }
        val missing = texts.filter {
            it.isNotBlank() && it !in translations && it !in pending && it !in failed
        }
        if (missing.isNotEmpty()) {
            request(missing, language)
        }
    }

    private fun detect(texts: Collection<String>) {
        detecting = true
        val sample = texts.joinToString("\n").take(DETECTION_SAMPLE_LENGTH)
        LanguageDetector.detectLanguage(sample, { language ->
            AndroidUtilities.runOnUIThread {
                if (!language.isNullOrEmpty() && language != TranslateController.UNKNOWN_LANGUAGE) {
                    detectedLanguage = language
                    notifyListeners(false)
                }
            }
        }, null)
    }

    private fun request(texts: List<String>, language: String) {
        pending.addAll(texts)
        translate(texts, language) { results ->
            pending.removeAll(texts.toSet())
            if (language != targetLanguage) {
                notifyListeners(false)
                return@translate
            }
            var failedAny = false
            texts.forEachIndexed { index, text ->
                val result = results.getOrNull(index)
                if (result.isNullOrBlank()) {
                    failed.add(text)
                    failedAny = true
                } else {
                    translations[text] = result
                }
            }
            val failedAll = failedAny && translations.isEmpty()
            if (failedAll) {
                enabled = false
            }
            notifyListeners(failedAll)
        }
    }

    private fun translate(texts: List<String>, language: String, done: (List<String?>) -> Unit) {
        val translator = TranslatorUtils.getCurrentTranslator()
        if (translator is HttpTranslator) {
            ChatTranslator.translate(TRANSLATION_TAG, texts, language) { results, _ -> done(results) }
            return
        }

        val results = arrayOfNulls<String>(texts.size)
        val chunks = texts.indices.chunked(MAX_TEXTS_PER_REQUEST)
        requestEach(chunks, texts, language, results, true, 0) { done(results.toList()) }
    }

    private fun requestEach(
        chunks: List<List<Int>>,
        texts: List<String>,
        language: String,
        results: Array<String?>,
        retry: Boolean,
        position: Int,
        done: () -> Unit
    ) {
        if (position == chunks.size) {
            done()
            return
        }
        requestChunk(chunks[position], texts, language, results, retry) {
            requestEach(chunks, texts, language, results, retry, position + 1, done)
        }
    }

    private fun requestChunk(
        chunk: List<Int>,
        texts: List<String>,
        language: String,
        results: Array<String?>,
        retry: Boolean,
        done: () -> Unit
    ) {
        val req = TLRPC.TL_messages_translateText().apply {
            flags = flags or 2
            for (index in chunk) {
                text.add(TLRPC.TL_textWithEntities().apply { text = texts[index] })
            }
            to_lang = language
        }
        ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req) { res, err ->
            AndroidUtilities.runOnUIThread {
                if (res is TLRPC.TL_messages_translateResult) {
                    val unresolved = ArrayList<Int>()
                    if (res.result.size == chunk.size) {
                        chunk.forEachIndexed { position, index ->
                            val translated = res.result[position]?.text
                            if (translated.isNullOrBlank()) {
                                unresolved.add(index)
                            } else {
                                results[index] = translated
                            }
                        }
                    } else {
                        unresolved.addAll(chunk)
                    }
                    if (!retry || unresolved.isEmpty()) {
                        done()
                        return@runOnUIThread
                    }
                    requestEach(unresolved.map { listOf(it) }, texts, language, results, false, 0, done)
                } else if (err != null && "TRANSLATIONS_DISABLED_ALT".equals(err.text, ignoreCase = true)) {
                    ChatTranslator.translate(
                        TRANSLATION_TAG,
                        chunk.map { texts[it] },
                        language,
                        GoogleTranslator.getInstance()
                    ) { translated, _ ->
                        chunk.forEachIndexed { position, index ->
                            results[index] = translated.getOrNull(position)
                        }
                        done()
                    }
                } else {
                    done()
                }
            }
        }
    }

    private fun notifyListeners(failed: Boolean) {
        for (listener in ArrayList(listeners)) {
            listener.onTranslationChanged(failed)
        }
    }

    companion object {
        private const val TRANSLATION_TAG = Long.MIN_VALUE + 1
        private const val MAX_TEXTS_PER_REQUEST = 20
        private const val DETECTION_SAMPLE_LENGTH = 2000

        private val instances = HashMap<String, PluginSettingsTranslation>()

        @JvmStatic
        fun of(pluginId: String): PluginSettingsTranslation =
            instances.getOrPut(pluginId) { PluginSettingsTranslation() }
    }
}
