/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.plugins.analyzer

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.exteragram.messenger.plugins.updates.toHex
import org.telegram.messenger.FileLog
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

internal class AnalyzerUpdateLedger private constructor(private val store: KeyStore) {
    internal data class Floor(val alias: String, val release: Long, val id: String)

    var floor: Floor? = store.aliases().asSequence().mapNotNull { alias ->
        val match = FLOOR_ALIAS.matchEntire(alias) ?: return@mapNotNull null
        val release = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
        Floor(alias, release, match.groupValues[2])
    }.maxByOrNull { it.release }
        private set

    fun seal(data: String): String {
        val key = store.getKey(STATE_KEY, null) as? SecretKey ?: generate(STATE_KEY)
        val mac = Mac.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256)
        mac.init(key)
        return mac.doFinal(data.toByteArray(Charsets.UTF_8)).toHex()
    }

    fun advance(update: AnalyzerUpdate) {
        if (floor?.id == update.id) return
        check(update.release > (floor?.release ?: 0))
        val alias = "$FLOOR_PREFIX${update.release}_${update.id}"
        generate(alias)
        floor = Floor(alias, update.release, update.id)
    }

    fun prune() {
        val keep = floor?.alias ?: return
        runCatching {
            store.aliases().asSequence().filter { it != keep && FLOOR_ALIAS.matches(it) }
                .toList().forEach(store::deleteEntry)
        }.onFailure { FileLog.e(it) }
    }

    private fun generate(alias: String): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE).apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN).build())
        }.generateKey()

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val STATE_KEY = "plugin_analyzer_ota_state"
        private const val FLOOR_PREFIX = "plugin_analyzer_ota_floor_"
        private val FLOOR_ALIAS = Regex("${FLOOR_PREFIX}([1-9][0-9]*)_([0-9a-f]{64})")

        fun open(): AnalyzerUpdateLedger? = runCatching {
            AnalyzerUpdateLedger(KeyStore.getInstance(KEYSTORE).apply { load(null) })
        }.onFailure { FileLog.e(it) }.getOrNull()
    }
}
