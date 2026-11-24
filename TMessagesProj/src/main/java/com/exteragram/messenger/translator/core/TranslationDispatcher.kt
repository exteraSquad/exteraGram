/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.translator.core

import android.os.SystemClock

import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import org.telegram.messenger.Utilities

import java.io.IOException
import kotlin.math.min

object TranslationDispatcher {

    fun interface Completion {
        fun onDone(texts: List<String>?, error: TranslationError?)
    }

    private class Job(
        val translator: HttpTranslator,
        val tag: Long,
        val texts: List<String>,
        val fromLang: String,
        val toLang: String,
        val html: Boolean,
        val completion: Completion
    ) {
        var attempt = 0
        var cancelled = false
        var call: Call? = null
    }

    private class Gate(val limits: ProviderLimits) {
        val queue = ArrayDeque<Job>()
        val running = ArrayList<Job>()
        var lastStartedAt = 0L
        var cooldownUntil = 0L
        var scheduledPumpAt = 0L
        lateinit var pumpRunnable: Runnable
    }

    private val lock = Any()
    private val gates = HashMap<String, Gate>()

    private fun gateOf(translator: HttpTranslator): Gate =
        gates.getOrPut(translator.javaClass.name) {
            Gate(translator.limits).also { gate -> gate.pumpRunnable = Runnable { pump(gate) } }
        }

    @JvmStatic
    fun enqueue(
        translator: HttpTranslator,
        tag: Long,
        texts: List<String>,
        fromLang: String,
        toLang: String,
        priority: Boolean = false,
        html: Boolean = false,
        completion: Completion
    ) {
        if (texts.isEmpty()) {
            AndroidUtilities.runOnUIThread { completion.onDone(emptyList(), null) }
            return
        }
        val gate = synchronized(lock) {
            val gate = gateOf(translator)
            val job = Job(translator, tag, texts, fromLang, toLang, html, completion)
            if (priority) gate.queue.addFirst(job) else gate.queue.addLast(job)
            gate
        }
        Utilities.globalQueue.postRunnable { pump(gate) }
    }

    @JvmStatic
    fun cancel(tag: Long) = cancelMatching { it.tag == tag }

    @JvmStatic
    fun cancelAll() = cancelMatching { true }

    private fun cancelMatching(matches: (Job) -> Boolean) {
        val aborted = ArrayList<Call>()
        val dropped = ArrayList<Job>()
        synchronized(lock) {
            for (gate in gates.values) {
                val queued = gate.queue.filter(matches)
                gate.queue.removeAll(queued)
                dropped.addAll(queued)
                for (job in gate.running) {
                    if (matches(job)) {
                        job.cancelled = true
                        job.call?.let { aborted.add(it) }
                    }
                }
            }
        }
        aborted.forEach { it.cancel() }
        if (dropped.isNotEmpty()) {
            AndroidUtilities.runOnUIThread {
                dropped.forEach { it.completion.onDone(null, TranslationError.Cancelled) }
            }
        }
    }

    private fun pump(gate: Gate) {
        while (true) {
            start(gate, nextJob(gate) ?: return)
        }
    }

    private fun nextJob(gate: Gate): Job? = synchronized(lock) {
        val limits = gate.limits
        if (gate.queue.isEmpty() || gate.running.size >= limits.maxConcurrent) {
            return@synchronized null
        }
        val now = SystemClock.elapsedRealtime()
        val readyAt = maxOf(gate.cooldownUntil, gate.lastStartedAt + limits.minIntervalMs)
        if (now < readyAt) {
            schedulePump(gate, readyAt - now)
            return@synchronized null
        }
        val job = gate.queue.removeFirst()
        gate.lastStartedAt = now
        gate.running.add(job)
        job
    }

    private fun schedulePump(gate: Gate, delay: Long) {
        val now = SystemClock.elapsedRealtime()
        val at = now + delay
        if (gate.scheduledPumpAt in (now + 1)..at) {
            return
        }
        gate.scheduledPumpAt = at
        Utilities.globalQueue.cancelRunnable(gate.pumpRunnable)
        Utilities.globalQueue.postRunnable(gate.pumpRunnable, delay)
    }

    private fun start(gate: Gate, job: Job) {
        val request = try {
            job.translator.buildRequest(job.texts, job.fromLang, job.toLang, job.html)
        } catch (e: Exception) {
            FileLog.e(e)
            finish(gate, job, null, TranslationError.Fatal)
            return
        }

        val call = job.translator.client.newCall(request)
        val cancelled = synchronized(lock) {
            if (!job.cancelled) {
                job.call = call
            }
            job.cancelled
        }
        if (cancelled) {
            finish(gate, job, null, null)
            return
        }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (call.isCanceled()) {
                    finish(gate, job, null, null)
                    return
                }
                FileLog.e(e)
                retryOrFail(gate, job, TranslationError.Transient)
            }

            override fun onResponse(call: Call, response: Response) {
                val parsed = try {
                    response.use { job.translator.parseResponse(it, job.texts.size) }
                } catch (e: Exception) {
                    FileLog.e(e)
                    ProviderResponse.Failure(TranslationError.Transient)
                }
                when (parsed) {
                    is ProviderResponse.Success -> finish(gate, job, parsed.texts, null)
                    is ProviderResponse.Failure -> retryOrFail(gate, job, parsed.error)
                }
            }
        })
    }

    private fun retryOrFail(gate: Gate, job: Job, error: TranslationError) {
        job.attempt++
        val limits = gate.limits
        if (!error.isRetryable || job.attempt >= limits.maxAttempts) {
            finish(gate, job, null, error)
            return
        }

        val delay = if (error is TranslationError.RateLimited && error.retryAfterMs > 0) {
            error.retryAfterMs
        } else {
            backoff(limits, job.attempt)
        }

        val cancelled = synchronized(lock) {
            gate.running.remove(job)
            job.call = null
            if (!job.cancelled) {
                if (error is TranslationError.RateLimited) {
                    gate.cooldownUntil = maxOf(gate.cooldownUntil, SystemClock.elapsedRealtime() + delay)
                }
                gate.queue.addFirst(job)
                schedulePump(gate, delay)
            }
            job.cancelled
        }
        if (cancelled) {
            AndroidUtilities.runOnUIThread { job.completion.onDone(null, TranslationError.Cancelled) }
            Utilities.globalQueue.postRunnable { pump(gate) }
            return
        }
        FileLog.d("translator: retrying in ${delay}ms after $error (attempt ${job.attempt})")
    }

    private fun backoff(limits: ProviderLimits, attempt: Int): Long {
        val exponential = limits.baseBackoffMs shl (attempt - 1).coerceIn(0, 16)
        val capped = min(exponential, limits.maxBackoffMs)
        return capped + Utilities.random.nextInt((capped / 4).toInt().coerceAtLeast(1))
    }

    private fun finish(gate: Gate, job: Job, texts: List<String>?, error: TranslationError?) {
        val cancelled = synchronized(lock) {
            gate.running.remove(job)
            job.call = null
            job.cancelled
        }
        AndroidUtilities.runOnUIThread {
            if (cancelled) {
                job.completion.onDone(null, TranslationError.Cancelled)
            } else {
                job.completion.onDone(texts, error)
            }
        }
        Utilities.globalQueue.postRunnable { pump(gate) }
    }
}