package com.flowseal.tgwsproxy.proxy

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Mirrors desktop [proxy.config.start_cfproxy_domain_refresh]:
 * seed with built-in defaults, then refresh from GitHub every hour.
 */
class CfDomainRefresh(
    private val scope: CoroutineScope,
    private val balancer: Balancer,
    private val hasUserDomains: () -> Boolean,
    private val log: (String) -> Unit,
) {
    private val started = AtomicBoolean(false)
    private var job: Job? = null

    fun start() {
        if (!started.compareAndSet(false, true)) return
        balancer.updateDomainsList(Protocol.CFPROXY_DEFAULT_DOMAINS)
        job = scope.launch(Dispatchers.IO) {
            refreshOnce()
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                refreshOnce()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        started.set(false)
    }

    /** Immediate pull of CF domain list (manual "Refresh components"). */
    fun refreshNow() {
        scope.launch(Dispatchers.IO) { refreshOnce() }
    }

    private suspend fun refreshOnce() {
        if (hasUserDomains()) {
            log("CF domain refresh skipped (custom user domains set)")
            return
        }
        val fetched = fetchEncodedList()
        val pool = normalize(fetched.map { decodeDomain(it) })
        if (pool.size >= MIN_VALID_DOMAINS) {
            balancer.updateDomainsList(pool)
            log("CF proxy domain pool updated from GitHub (${pool.size} domains)")
            return
        }
        if (fetched.isNotEmpty()) {
            log(
                "Ignoring fetched CF proxy domains due to low-quality payload " +
                    "(total=${fetched.size}, valid=${pool.size}, required>=$MIN_VALID_DOMAINS)",
            )
        } else {
            log("CF proxy domain refresh failed or empty; keeping current pool")
        }
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 3_600_000L
        private const val MIN_VALID_DOMAINS = 3
        private const val DOMAINS_URL =
            "https://raw.githubusercontent.com/Flowseal/tg-ws-proxy/main/.github/cfproxy-domains.txt"
        private const val SUFFIX = ".co.uk"

        suspend fun fetchEncodedList(): List<String> = withContext(Dispatchers.IO) {
            try {
                val nonce = (1..7).map { ('a'..'z').random() }.joinToString("")
                val conn = (URL("$DOMAINS_URL?$nonce").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("User-Agent", "tg-ws-proxy-android")
                    instanceFollowRedirects = true
                }
                conn.inputStream.bufferedReader().use { reader ->
                    reader.lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .toList()
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        /** Same as Python `_dd` in proxy/config.py */
        fun decodeDomain(encoded: String): String {
            if (!encoded.endsWith(".com")) return encoded
            val p = encoded.dropLast(4)
            val n = p.count { it.isLetter() }
            val decoded = buildString {
                for (c in p) {
                    if (c.isLetter()) {
                        val base = if (c >= 'a') 'a' else 'A'
                        val shifted = ((c.code - base.code - n) % 26 + 26) % 26
                        append((base.code + shifted).toChar())
                    } else {
                        append(c)
                    }
                }
            }
            return decoded + SUFFIX
        }

        fun normalize(domains: List<String>): List<String> {
            val seen = linkedSetOf<String>()
            for (domain in domains) {
                val item = domain.trim().lowercase()
                if (!isValidDomain(item)) continue
                seen.add(item)
            }
            return seen.toList()
        }

        private fun isValidDomain(domain: String): Boolean {
            if (domain.isEmpty() || domain.length > 253) return false
            if (domain.startsWith('.') || domain.endsWith('.')) return false
            val labels = domain.split('.')
            if (labels.size < 2) return false
            for (label in labels) {
                if (label.isEmpty() || label.length > 63) return false
                if (label.first() == '-' || label.last() == '-') return false
                if (!label.all { it.isLetterOrDigit() || it == '-' }) return false
            }
            val tld = labels.last()
            return tld.length >= 2 && tld.any { it.isLetter() }
        }
    }
}
