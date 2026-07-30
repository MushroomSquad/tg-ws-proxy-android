package com.flowseal.tgwsproxy.proxy

import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WsPool(
    private val scope: CoroutineScope,
    private val config: () -> ProxyConfig,
    private val stats: Stats,
    private val log: (String) -> Unit,
) {
    private data class Entry(val ws: RawWebSocket, val created: Long)

    private val idle = ConcurrentHashMap<Pair<Int, Boolean>, ArrayDeque<Entry>>()
    private val refilling = ConcurrentHashMap.newKeySet<Pair<Int, Boolean>>()
    private val rotating = ConcurrentHashMap<Pair<Int, Boolean>, Job>()
    private val refillFailures = ConcurrentHashMap<Pair<Int, Boolean>, Int>()
    private val refillAfter = ConcurrentHashMap<Pair<Int, Boolean>, Long>()
    private val mutex = Mutex()
    @Volatile var tryFrontingFirst = false

    suspend fun get(
        dc: Int,
        isMedia: Boolean,
        targetIp: String,
        domains: List<String>,
        allowRefill: Boolean = true,
    ): RawWebSocket? {
        val key = dc to isMedia
        val now = System.nanoTime() / 1_000_000L
        val bucket = idle.getOrPut(key) { ArrayDeque() }
        mutex.withLock {
            while (bucket.isNotEmpty()) {
                val entry = bucket.removeFirst()
                val ageMs = now - entry.created
                if (ageMs > WS_POOL_MAX_AGE_MS || entry.ws.isClosing) {
                    scope.launch { entry.ws.closeQuietly() }
                    continue
                }
                stats.poolHits++
                reportSuccess(dc, isMedia)
                if (allowRefill) scheduleRefill(key, targetIp, domains)
                return entry.ws
            }
        }
        stats.poolMisses++
        if (allowRefill) scheduleRefill(key, targetIp, domains)
        return null
    }

    fun reportSuccess(dc: Int, isMedia: Boolean) {
        val key = dc to isMedia
        refillFailures.remove(key)
        refillAfter.remove(key)
    }

    fun scheduleRefill(key: Pair<Int, Boolean>, targetIp: String, domains: List<String>) {
        val now = System.nanoTime() / 1_000_000L
        if (key in refilling || now < (refillAfter[key] ?: 0L)) return
        if (!refilling.add(key)) return
        scope.launch(Dispatchers.IO) {
            try {
                refill(key, targetIp, domains)
            } finally {
                refilling.remove(key)
            }
        }
    }

    private suspend fun refill(key: Pair<Int, Boolean>, targetIp: String, domains: List<String>) {
        val (dc, isMedia) = key
        val bucket = idle.getOrPut(key) { ArrayDeque() }
        val needed = config().poolSize - bucket.size
        if (needed <= 0) return
        var connected = 0
        repeat(needed) {
            val ws = connectOne(targetIp, domains) ?: return@repeat
            mutex.withLock {
                bucket.addLast(Entry(ws, System.nanoTime() / 1_000_000L))
            }
            connected++
            scheduleRotation(key, targetIp, domains)
        }
        if (connected > 0) {
            reportSuccess(dc, isMedia)
        } else {
            val failures = (refillFailures[key] ?: 0) + 1
            refillFailures[key] = failures
            val delayMs = minOf(
                REFILL_BACKOFF_INITIAL_MS * (1L shl minOf(failures - 1, 6)),
                REFILL_BACKOFF_MAX_MS,
            )
            refillAfter[key] = System.nanoTime() / 1_000_000L + delayMs
            log("WS pool refill failed for DC$dc${if (isMedia) "m" else ""}, retry in ${delayMs / 1000}s")
        }
    }

    private fun scheduleRotation(key: Pair<Int, Boolean>, targetIp: String, domains: List<String>) {
        if (rotating.containsKey(key)) return
        val job = scope.launch(Dispatchers.IO) {
            try {
                while (true) {
                    val bucket = idle[key] ?: return@launch
                    if (bucket.isEmpty()) return@launch
                    delay(WS_POOL_CHECK_INTERVAL_MS)
                    val now = System.nanoTime() / 1_000_000L
                    val expired = mutableListOf<RawWebSocket>()
                    mutex.withLock {
                        val ready = ArrayDeque<Entry>()
                        while (bucket.isNotEmpty()) {
                            val e = bucket.removeFirst()
                            if (now - e.created >= WS_POOL_MAX_AGE_MS || e.ws.isClosing) {
                                expired += e.ws
                            } else {
                                ready.addLast(e)
                            }
                        }
                        bucket.addAll(ready)
                    }
                    if (expired.isNotEmpty()) {
                        expired.forEach { scope.launch { it.closeQuietly() } }
                        scheduleRefill(key, targetIp, domains)
                    }
                }
            } finally {
                rotating.remove(key)
            }
        }
        rotating[key] = job
    }

    private suspend fun connectOne(targetIp: String, domains: List<String>): RawWebSocket? {
        for (domain in domains) {
            if (tryFrontingFirst) {
                connectFronted(targetIp, domain)?.let { return it }
            }
            try {
                val ws = RawWebSocket.connect(
                    targetIp, domain, timeoutMs = 8_000, bufferSize = config().bufferSize,
                )
                tryFrontingFirst = false
                return ws
            } catch (e: WsHandshakeError) {
                if (e.isRedirect) continue
                return null
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                if (tryFrontingFirst) return null
                return connectFronted(targetIp, domain)
            } catch (_: Exception) {
                return null
            }
        }
        return null
    }

    private suspend fun connectFronted(targetIp: String, domain: String): RawWebSocket? {
        return try {
            val ws = RawWebSocket.connect(
                targetIp, domain, timeoutMs = 7_000, sni = "sprinthost.ru",
                bufferSize = config().bufferSize,
            )
            stats.connectionsFronting++
            tryFrontingFirst = true
            ws
        } catch (_: Exception) {
            null
        }
    }

    fun warmup() {
        for ((dc, targetIp) in config().dcRedirects) {
            for (isMedia in listOf(false, true)) {
                val domains = Protocol.wsDomains(dc, isMedia)
                scheduleRefill(dc to isMedia, targetIp, domains)
            }
        }
        log("WS pool warmup started for ${config().dcRedirects.size} DC(s)")
    }

    fun reset() {
        rotating.values.forEach { it.cancel() }
        idle.clear()
        refilling.clear()
        rotating.clear()
        refillFailures.clear()
        refillAfter.clear()
        tryFrontingFirst = false
    }

    companion object {
        private const val WS_POOL_MAX_AGE_MS = 120_000L
        private const val WS_POOL_CHECK_INTERVAL_MS = 5_000L
        private const val REFILL_BACKOFF_INITIAL_MS = 60_000L
        private const val REFILL_BACKOFF_MAX_MS = 3_600_000L
    }
}

class CfWorkerPool(
    private val scope: CoroutineScope,
    private val config: () -> ProxyConfig,
    private val stats: Stats,
    private val log: (String) -> Unit,
) {
    private data class Entry(val ws: RawWebSocket, val created: Long)

    private val idle = ConcurrentHashMap<Pair<Int, String>, ArrayDeque<Entry>>()
    private val refilling = ConcurrentHashMap.newKeySet<Pair<Int, String>>()
    private val mutex = Mutex()

    suspend fun get(dc: Int, workerDomain: String, fallbackDst: String): RawWebSocket? {
        val key = dc to workerDomain
        val now = System.nanoTime() / 1_000_000L
        val bucket = idle.getOrPut(key) { ArrayDeque() }
        mutex.withLock {
            while (bucket.isNotEmpty()) {
                val entry = bucket.removeFirst()
                if (now - entry.created > WS_POOL_MAX_AGE_MS || entry.ws.isClosing) {
                    scope.launch { entry.ws.closeQuietly() }
                    continue
                }
                stats.cfPoolHits++
                scheduleRefill(key, fallbackDst)
                return entry.ws
            }
        }
        stats.cfPoolMisses++
        scheduleRefill(key, fallbackDst)
        return null
    }

    private fun scheduleRefill(key: Pair<Int, String>, fallbackDst: String) {
        if (!refilling.add(key)) return
        scope.launch(Dispatchers.IO) {
            try {
                val bucket = idle.getOrPut(key) { ArrayDeque() }
                val needed = config().poolSize - bucket.size
                if (needed <= 0) return@launch
                val (dc, workerDomain) = key
                repeat(needed) {
                    val ws = connectOne(workerDomain, fallbackDst, dc) ?: return@repeat
                    mutex.withLock {
                        bucket.addLast(Entry(ws, System.nanoTime() / 1_000_000L))
                    }
                }
            } finally {
                refilling.remove(key)
            }
        }
    }

    private suspend fun connectOne(workerDomain: String, fallbackDst: String, dc: Int): RawWebSocket? {
        val path = "/apiws?dst=${java.net.URLEncoder.encode(fallbackDst, "UTF-8")}&dc=$dc"
        return try {
            RawWebSocket.connect(
                workerDomain, workerDomain, timeoutMs = 8_000,
                path = path, bufferSize = config().bufferSize,
            )
        } catch (_: Exception) {
            null
        }
    }

    fun warmup() {
        val cfFallbacks = Protocol.DC_DEFAULT_IPS.filterKeys { it !in config().dcRedirects }
        if (cfFallbacks.isEmpty() || config().cfproxyWorkerDomains.isEmpty()) return
        for (worker in config().cfproxyWorkerDomains) {
            for ((dc, dst) in cfFallbacks) {
                scheduleRefill(dc to worker, dst)
            }
        }
        log("CF worker pool warmup started for ${cfFallbacks.size} DC(s)")
    }

    fun reset() {
        idle.clear()
        refilling.clear()
    }

    companion object {
        private const val WS_POOL_MAX_AGE_MS = 100_000L
    }
}
