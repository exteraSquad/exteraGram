/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.inline

import android.os.SystemClock

import com.exteragram.messenger.inline.rates.Currencies
import com.exteragram.messenger.inline.rates.CurrencyRates
import com.exteragram.messenger.inlineTargetCurrency
import com.exteragram.messenger.utils.network.ExchangeRates

import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.MessagesController
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities

class InlineRates(private val onUpdated: Runnable) : CurrencyRates {

    companion object {
        private const val RETRY_INTERVAL = 30 * 1000L
        private const val REFRESH_INTERVAL = 5 * 60 * 1000L
        private const val MAX_AGE = 24 * 60 * 60 * 1000L
        private const val STARS_RATE_SCALE = 100_000.0

        fun normalize(code: String): String = if (code == ExchangeRates.GRAM) Currencies.GRAM else code

        private fun rateCode(code: String): String = if (code == Currencies.GRAM) ExchangeRates.GRAM else code
    }

    private var requesting = false
    private var requestedAt = 0L

    private var localAccount = -1
    private var localCurrency: String? = null

    override fun supports(code: String): Boolean = code == Currencies.STARS || ExchangeRates.isSupportedCurrency(rateCode(code))

    override fun rate(from: String, to: String): Double? {
        val fromUsd = usdRate(from) ?: return null
        val toUsd = usdRate(to) ?: return null
        return if (toUsd == 0.0) null else fromUsd / toUsd
    }

    private fun usdRate(code: String): Double? {
        if (code == Currencies.STARS) {
            val rate = MessagesController.getInstance(UserConfig.selectedAccount).starsUsdSellRate1000
            return if (rate > 0) rate / STARS_RATE_SCALE else null
        }
        val state = ExchangeRates.peekCached()
        if (state == null || System.currentTimeMillis() - state.time() > MAX_AGE) {
            request(RETRY_INTERVAL)
            return null
        }
        val rate = state.getUsdRate(rateCode(code))
        if (rate == null) {
            request(RETRY_INTERVAL)
            return null
        }
        request(REFRESH_INTERVAL)
        return rate.toDouble()
    }

    fun localCurrency(): String? {
        val target = normalize(inlineTargetCurrency)
        if (supports(target)) {
            return target
        }
        val account = UserConfig.selectedAccount
        if (account != localAccount) {
            localAccount = account
            localCurrency = ExchangeRates.getLocalCurrency(account)
        }
        return localCurrency
    }

    private fun request(interval: Long) {
        val now = SystemClock.elapsedRealtime()
        if (requesting || requestedAt != 0L && now - requestedAt < interval) {
            return
        }
        requesting = true
        requestedAt = now
        Utilities.globalQueue.postRunnable {
            ExchangeRates.getCached()
            AndroidUtilities.runOnUIThread {
                ExchangeRates.fetch {
                    requesting = false
                    onUpdated.run()
                }
            }
        }
    }
}
