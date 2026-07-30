package com.flowseal.tgwsproxy.proxy

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class Stats {
    @Volatile var connectionsTotal = 0L
    @Volatile var connectionsActive = 0L
    @Volatile var connectionsWs = 0L
    @Volatile var connectionsTcpFallback = 0L
    @Volatile var connectionsCfproxy = 0L
    @Volatile var connectionsFronting = 0L
    @Volatile var connectionsBad = 0L
    @Volatile var wsErrors = 0L
    @Volatile var bytesUp = 0L
    @Volatile var bytesDown = 0L
    @Volatile var poolHits = 0L
    @Volatile var poolMisses = 0L
    @Volatile var cfPoolHits = 0L
    @Volatile var cfPoolMisses = 0L

    fun summary(): String {
        val poolTotal = poolHits + poolMisses
        val poolS = if (poolTotal > 0) "$poolHits/$poolTotal" else "n/a"
        val cfTotal = cfPoolHits + cfPoolMisses
        val cfS = if (cfTotal > 0) "$cfPoolHits/$cfTotal" else "n/a"
        return "total=$connectionsTotal active=$connectionsActive ws=$connectionsWs " +
            "tcp_fb=$connectionsTcpFallback cf=$connectionsCfproxy front=$connectionsFronting " +
            "bad=$connectionsBad err=$wsErrors pool=$poolS cf_pool=$cfS " +
            "up=${Protocol.humanBytes(bytesUp)} down=${Protocol.humanBytes(bytesDown)}"
    }

    fun reset() {
        connectionsTotal = 0
        connectionsActive = 0
        connectionsWs = 0
        connectionsTcpFallback = 0
        connectionsCfproxy = 0
        connectionsFronting = 0
        connectionsBad = 0
        wsErrors = 0
        bytesUp = 0
        bytesDown = 0
        poolHits = 0
        poolMisses = 0
        cfPoolHits = 0
        cfPoolMisses = 0
    }
}

class Balancer {
    @Volatile
    private var domains: List<String> = emptyList()
    private val dcToDomain = mutableMapOf<Int, String>()

    @Synchronized
    fun updateDomainsList(domainsList: List<String>) {
        if (domains.toSet() == domainsList.toSet() && domains.size == domainsList.size) return
        domains = domainsList.toList()
        dcToDomain.clear()
        if (domains.isNotEmpty()) {
            for (dc in listOf(1, 2, 3, 4, 5, 203)) {
                dcToDomain[dc] = domains.random()
            }
        }
    }

    @Synchronized
    fun updateDomainForDc(dcId: Int, domain: String): Boolean {
        if (dcToDomain[dcId] == domain) return false
        dcToDomain[dcId] = domain
        return true
    }

    @Synchronized
    fun getDomainsForDc(dcId: Int): List<String> {
        val current = dcToDomain[dcId]
        val shuffled = domains.shuffled().toMutableList()
        val out = mutableListOf<String>()
        if (current != null) out += current
        for (d in shuffled) {
            if (d != current) out += d
        }
        return out
    }
}

object Bridge {
    suspend fun bridgeWsReencrypt(
        clientIn: BufferedInputStream,
        clientOut: BufferedOutputStream,
        ws: RawWebSocket,
        label: String,
        ctx: CryptoCtx,
        stats: Stats,
        log: (String) -> Unit,
        dc: Int? = null,
        isMedia: Boolean = false,
        splitter: MsgSplitter? = null,
    ) {
        val dcTag = if (dc != null) "DC$dc${if (isMedia) "m" else ""}" else "DC?"
        var upBytes = 0L
        var downBytes = 0L
        var upPackets = 0
        var downPackets = 0
        val start = System.nanoTime()
        var closeReason = "normal"

        coroutineScope {
            val up = async(Dispatchers.IO) {
                try {
                    val buf = ByteArray(256 * 1024)
                    while (isActive) {
                        val n = clientIn.read(buf)
                        if (n <= 0) {
                            val tail = splitter?.flush().orEmpty()
                            if (tail.isNotEmpty()) ws.send(tail[0])
                            break
                        }
                        stats.bytesUp += n
                        upBytes += n
                        upPackets++
                        val chunk = buf.copyOf(n)
                        val plain = ctx.cltDec.update(chunk)
                        val out = ctx.tgEnc.update(plain)
                        if (splitter != null) {
                            val parts = splitter.split(out)
                            if (parts.isEmpty()) continue
                            if (parts.size > 1) ws.sendBatch(parts) else ws.send(parts[0])
                        } else {
                            ws.send(out)
                        }
                    }
                } catch (e: Exception) {
                    closeReason = "client: ${e.javaClass.simpleName}"
                }
            }
            val down = async(Dispatchers.IO) {
                try {
                    while (isActive) {
                        val data = ws.recv()
                        if (data == null) {
                            if (closeReason == "normal") closeReason = "upstream: ws_close"
                            break
                        }
                        stats.bytesDown += data.size
                        downBytes += data.size
                        downPackets++
                        val plain = ctx.tgDec.update(data)
                        val out = ctx.cltEnc.update(plain)
                        clientOut.write(out)
                        // Must flush every packet — auth replies are tiny; delayed flush
                        // leaves Telegram stuck on "Connecting…" (regression vs v1).
                        clientOut.flush()
                    }
                } catch (e: Exception) {
                    closeReason = "upstream: ${e.javaClass.simpleName}"
                }
            }
            select {
                up.onAwait {}
                down.onAwait {}
            }
            up.cancel()
            down.cancel()
            runCatching { up.await() }
            runCatching { down.await() }
        }

        val elapsed = (System.nanoTime() - start) / 1e9
        log(
            "[$label] $dcTag WS session closed ($closeReason): " +
                "^${Protocol.humanBytes(upBytes)} ($upPackets pkts) " +
                "v${Protocol.humanBytes(downBytes)} ($downPackets pkts) in ${"%.1f".format(elapsed)}s",
        )
        runCatching { ws.closeQuietly() }
        runCatching { clientOut.close() }
    }

    suspend fun bridgeTcpReencrypt(
        clientIn: BufferedInputStream,
        clientOut: BufferedOutputStream,
        remoteIn: BufferedInputStream,
        remoteOut: BufferedOutputStream,
        label: String,
        ctx: CryptoCtx,
        stats: Stats,
        log: (String) -> Unit,
    ) {
        var upBytes = 0L
        var downBytes = 0L
        var upPackets = 0
        var downPackets = 0
        val start = System.nanoTime()
        coroutineScope {
            val jobs = listOf(
                launch(Dispatchers.IO) {
                    forward(clientIn, remoteOut, true, ctx, stats) { n ->
                        upBytes += n
                        upPackets++
                    }
                },
                launch(Dispatchers.IO) {
                    forward(remoteIn, clientOut, false, ctx, stats) { n ->
                        downBytes += n
                        downPackets++
                    }
                },
            )
            select {
                jobs[0].onJoin {}
                jobs[1].onJoin {}
            }
            jobs.forEach { it.cancel() }
            jobs.forEach { runCatching { it.join() } }
        }
        runCatching { clientOut.close() }
        runCatching { remoteOut.close() }
        val elapsed = (System.nanoTime() - start) / 1e9
        log(
            "[$label] TCP bridge closed: " +
                "^${Protocol.humanBytes(upBytes)} ($upPackets pkts) " +
                "v${Protocol.humanBytes(downBytes)} ($downPackets pkts) in ${"%.1f".format(elapsed)}s",
        )
    }

    private suspend fun forward(
        src: BufferedInputStream,
        dst: BufferedOutputStream,
        isUp: Boolean,
        ctx: CryptoCtx,
        stats: Stats,
        onChunk: ((Int) -> Unit)? = null,
    ) {
        val buf = ByteArray(65536)
        while (true) {
            val n = withContext(Dispatchers.IO) { src.read(buf) }
            if (n <= 0) break
            val chunk = buf.copyOf(n)
            val out = if (isUp) {
                stats.bytesUp += n
                ctx.tgEnc.update(ctx.cltDec.update(chunk))
            } else {
                stats.bytesDown += n
                ctx.cltEnc.update(ctx.tgDec.update(chunk))
            }
            onChunk?.invoke(n)
            withContext(Dispatchers.IO) {
                dst.write(out)
                dst.flush()
            }
        }
    }
}

object Fallback {
    private const val MAX_CF_DOMAIN_TRIES = 3
    private const val TCP_FIRST_BYTE_MS = 4_000
    private const val FRONTING_IP = "149.154.167.220"

    private fun formatErr(e: Exception): String = when (e) {
        is WsHandshakeError -> "WsHandshakeError ${e.statusCode} ${e.statusLine}"
        else -> "${e.javaClass.simpleName}: ${e.message}"
    }

    /** Second TCP target when default DC IP resets / blackholes. */
    private fun alternateTcpIp(dc: Int, primary: String, config: ProxyConfig): String? {
        val fromConfig = config.dcRedirects[dc]
        val candidates = listOfNotNull(fromConfig, FRONTING_IP).distinct()
        return candidates.firstOrNull { it != primary }
    }

    suspend fun doFallback(
        clientIn: BufferedInputStream,
        clientOut: BufferedOutputStream,
        relayInit: ByteArray,
        label: String,
        dc: Int,
        isTestDc: Boolean,
        isMedia: Boolean,
        mediaTag: String,
        ctx: CryptoCtx,
        config: ProxyConfig,
        stats: Stats,
        balancer: Balancer,
        cfWorkerPool: CfWorkerPool,
        log: (String) -> Unit,
        splitter: MsgSplitter? = null,
    ): Boolean {
        val ipTable = if (isTestDc) Protocol.DC_TEST_IPS else Protocol.DC_DEFAULT_IPS
        val fallbackDst = ipTable[dc]
        val useCf = config.fallbackCfproxy && !isTestDc
        val workerDomains = config.cfproxyWorkerDomains

        val methods = mutableListOf<String>()
        if (workerDomains.isNotEmpty() && fallbackDst != null) methods += "cf_worker"
        // Media files usually land on a DC not in dc_ip (DC4-only config). CF burns
        // the client timeout; hit real DC IP over TCP first for media.
        if (isMedia) {
            if (fallbackDst != null) methods += "tcp"
            if (useCf) methods += "cf"
        } else {
            if (useCf) methods += "cf"
            if (fallbackDst != null) methods += "tcp"
        }

        for (method in methods) {
            when (method) {
                "cf_worker" -> {
                    if (cfWorkerFallback(
                            clientIn, clientOut, relayInit, label, ctx, dc, isTestDc, isMedia,
                            fallbackDst!!, config, stats, cfWorkerPool, log, splitter,
                        )
                    ) return true
                }
                "cf" -> {
                    if (cfProxyFallback(
                            clientIn, clientOut, relayInit, label, ctx, dc, isMedia,
                            config, stats, balancer, log, splitter,
                        )
                    ) return true
                }
                "tcp" -> {
                    val primary = fallbackDst!!
                    log("[$label] DC$dc$mediaTag -> TCP fallback to $primary:443")
                    if (tcpFallback(clientIn, clientOut, primary, relayInit, label, ctx, config, stats, log)) {
                        return true
                    }
                    val alt = alternateTcpIp(dc, primary, config)
                    if (alt != null) {
                        log("[$label] DC$dc$mediaTag -> TCP fallback retry $alt:443")
                        if (tcpFallback(clientIn, clientOut, alt, relayInit, label, ctx, config, stats, log)) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    private suspend fun cfWorkerFallback(
        clientIn: BufferedInputStream,
        clientOut: BufferedOutputStream,
        relayInit: ByteArray,
        label: String,
        ctx: CryptoCtx,
        dc: Int,
        isTestDc: Boolean,
        isMedia: Boolean,
        fallbackDst: String,
        config: ProxyConfig,
        stats: Stats,
        cfWorkerPool: CfWorkerPool,
        log: (String) -> Unit,
        splitter: MsgSplitter?,
    ): Boolean {
        val mediaTag = if (isMedia) " media" else ""
        val domains = config.cfproxyWorkerDomains.shuffled()
        for (workerDomain in domains) {
            var ws = if (!isTestDc) cfWorkerPool.get(dc, workerDomain, fallbackDst) else null
            if (ws != null) {
                log("[$label] DC$dc$mediaTag -> CF worker pool hit for $fallbackDst")
            } else {
                val query = "dst=${URLEncoder.encode(fallbackDst, "UTF-8")}&dc=$dc"
                val path = "/apiws?$query"
                log("[$label] DC$dc$mediaTag -> trying CF worker $workerDomain for $fallbackDst")
                ws = try {
                    RawWebSocket.connect(
                        workerDomain, workerDomain, timeoutMs = 10_000,
                        path = path, bufferSize = config.bufferSize,
                    )
                } catch (e: Exception) {
                    log("[$label] DC$dc$mediaTag CF worker $workerDomain failed: ${formatErr(e)}")
                    null
                }
            }
            if (ws == null) continue
            stats.connectionsCfproxy++
            ws.send(relayInit)
            Bridge.bridgeWsReencrypt(
                clientIn, clientOut, ws, label, ctx, stats, log, dc, isMedia, splitter,
            )
            return true
        }
        return false
    }

    private suspend fun cfProxyFallback(
        clientIn: BufferedInputStream,
        clientOut: BufferedOutputStream,
        relayInit: ByteArray,
        label: String,
        ctx: CryptoCtx,
        dc: Int,
        isMedia: Boolean,
        config: ProxyConfig,
        stats: Stats,
        balancer: Balancer,
        log: (String) -> Unit,
        splitter: MsgSplitter?,
    ): Boolean {
        val mediaTag = if (isMedia) " media" else ""
        log("[$label] DC$dc$mediaTag -> trying CF proxy")
        var ws: RawWebSocket? = null
        var chosen: String? = null
        val candidates = balancer.getDomainsForDc(dc).take(MAX_CF_DOMAIN_TRIES)
        for (base in candidates) {
            val domain = "kws$dc.$base"
            try {
                ws = RawWebSocket.connect(domain, domain, timeoutMs = 10_000, bufferSize = config.bufferSize)
                chosen = base
                break
            } catch (e: Exception) {
                log("[$label] DC$dc$mediaTag CF proxy failed: ${formatErr(e)}")
                if (e is WsHandshakeError && e.statusCode in listOf(502, 503)) {
                    return false
                }
            }
        }
        val connected = ws ?: return false
        if (chosen != null && balancer.updateDomainForDc(dc, chosen)) {
            log("[$label] Switched active CF domain")
        }
        stats.connectionsCfproxy++
        connected.send(relayInit)
        Bridge.bridgeWsReencrypt(
            clientIn, clientOut, connected, label, ctx, stats, log, dc, isMedia, splitter,
        )
        return true
    }

    private suspend fun tcpFallback(
        clientIn: BufferedInputStream,
        clientOut: BufferedOutputStream,
        dst: String,
        relayInit: ByteArray,
        label: String,
        ctx: CryptoCtx,
        config: ProxyConfig,
        stats: Stats,
        log: (String) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val sock = try {
            withTimeout(10_000) {
                Socket().also {
                    it.tcpNoDelay = true
                    it.connect(InetSocketAddress(dst, 443), 10_000)
                    try {
                        it.receiveBufferSize = config.bufferSize
                        it.sendBufferSize = config.bufferSize
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
            log("[$label] TCP fallback to $dst:443 failed: ${formatErr(e)}")
            return@withContext false
        }
        val remoteIn = BufferedInputStream(sock.inputStream)
        val remoteOut = BufferedOutputStream(sock.outputStream)
        try {
            remoteOut.write(relayInit)
            remoteOut.flush()
            // Real DC may accept TCP then blackhole; require first reply before bridging.
            sock.soTimeout = TCP_FIRST_BYTE_MS
            val first = ByteArray(65536)
            val n = try {
                remoteIn.read(first)
            } catch (e: java.net.SocketTimeoutException) {
                log("[$label] TCP fallback dead (no reply) from $dst:443")
                runCatching { sock.close() }
                return@withContext false
            } finally {
                try {
                    sock.soTimeout = 0
                } catch (_: Exception) {
                }
            }
            if (n <= 0) {
                log("[$label] TCP fallback dead (no reply) from $dst:443")
                runCatching { sock.close() }
                return@withContext false
            }
            val out = ctx.cltEnc.update(ctx.tgDec.update(first.copyOf(n)))
            clientOut.write(out)
            clientOut.flush()
            stats.bytesDown += n
            stats.connectionsTcpFallback++
            Bridge.bridgeTcpReencrypt(clientIn, clientOut, remoteIn, remoteOut, label, ctx, stats, log)
            true
        } catch (e: Exception) {
            log("[$label] TCP fallback to $dst:443 failed: ${formatErr(e)}")
            false
        } finally {
            runCatching { sock.close() }
        }
    }
}
