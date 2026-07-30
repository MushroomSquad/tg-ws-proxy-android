package com.flowseal.tgwsproxy.proxy

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class ProxyServer(
    private var config: ProxyConfig,
    private val log: (String) -> Unit,
    private val cachedCfDomains: List<String>? = null,
) {
    private val running = AtomicBoolean(false)
    private var scope: CoroutineScope? = null
    private var acceptJob: Job? = null
    private var serverSocket: ServerSocket? = null

    val stats = Stats()
    private val balancer = Balancer()
    private var wsPool: WsPool? = null
    private var cfWorkerPool: CfWorkerPool? = null
    private var cfDomainRefresh: CfDomainRefresh? = null

    private val wsBlacklist = ConcurrentHashMap.newKeySet<String>()
    private val dcFailUntil = ConcurrentHashMap<String, Long>()
    private val ipFailUntil = ConcurrentHashMap<String, Long>()

    @Synchronized
    fun updateConfig(newConfig: ProxyConfig) {
        config = newConfig
    }

    fun isRunning(): Boolean = running.get()

    fun refreshCfDomains() {
        cfDomainRefresh?.refreshNow()
            ?: log("CF refresh unavailable (proxy stopped or custom domains set)")
    }

    @Synchronized
    fun start() {
        if (running.get()) return
        val secret = config.secret
        require(secret.length == 32) { "Secret must be 32 hex characters" }

        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        this.scope = scope
        stats.reset()
        wsBlacklist.clear()
        dcFailUntil.clear()
        ipFailUntil.clear()

        wsPool = WsPool(scope, { config }, stats, log)
        cfWorkerPool = CfWorkerPool(scope, { config }, stats, log)

        if (config.fallbackCfproxy) {
            val user = config.cfproxyUserDomains
            if (user.isNotEmpty()) {
                balancer.updateDomainsList(user)
            } else {
                val seed = cachedCfDomains?.takeIf { it.size >= 3 }
                    ?: Protocol.CFPROXY_DEFAULT_DOMAINS
                balancer.updateDomainsList(seed)
                val refresh = CfDomainRefresh(
                    scope = scope,
                    balancer = balancer,
                    hasUserDomains = { config.cfproxyUserDomains.isNotEmpty() },
                    log = log,
                )
                cfDomainRefresh = refresh
                refresh.start()
            }
        }

        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(java.net.InetSocketAddress(InetAddress.getByName(config.host), config.port))
        serverSocket = ss
        running.set(true)

        if (!CryptoSelfTest.run(log)) {
            log("WARNING: crypto self-test failed — handshakes may fail on this device")
        }

        log("=".repeat(60))
        log("  Telegram MTProto WS Bridge Proxy (Android)")
        log("  Listening on   ${config.host}:${config.port}")
        log("  Secret:        ${maskSecret(config.secret)}")
        log("  If bad handshake: Open in Telegram again (secret must match)")
        log("  Target DC IPs:")
        for (dc in config.dcRedirects.keys.sorted()) {
            log("    DC$dc: ${config.dcRedirects[dc]}")
        }
        if (config.fallbackCfproxy) {
            log("  CF proxy:      enabled")
        }
        if (config.cfproxyWorkerDomains.isNotEmpty()) {
            log("  CF worker:     ${config.cfproxyWorkerDomains.joinToString()}")
        }
        log("=".repeat(60))
        log("  Connect: ${config.proxyLink()}")
        log("=".repeat(60))

        acceptJob = scope.launch {
            wsPool?.warmup()
            cfWorkerPool?.warmup()
            while (isActive && running.get()) {
                val client = try {
                    ss.accept()
                } catch (_: Exception) {
                    break
                }
                launch { handleClient(client) }
            }
        }
    }

    @Synchronized
    fun stop() {
        if (!running.getAndSet(false)) return
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptJob?.cancel()
        acceptJob = null
        cfDomainRefresh?.stop()
        cfDomainRefresh = null
        wsPool?.reset()
        cfWorkerPool?.reset()
        scope?.cancel()
        scope = null
        log("Proxy stopped. Final stats: ${stats.summary()}")
    }

    private suspend fun handleClient(socket: Socket) {
        stats.connectionsTotal++
        stats.connectionsActive++
        val peer = socket.remoteSocketAddress?.toString() ?: "?"
        val label = peer.removePrefix("/")
        try {
            socket.tcpNoDelay = true
            try {
                socket.receiveBufferSize = config.bufferSize
                socket.sendBufferSize = config.bufferSize
                socket.soTimeout = 0
            } catch (_: Exception) {
            }

            val input = BufferedInputStream(socket.inputStream, config.bufferSize.coerceAtLeast(64 * 1024))
            val output = BufferedOutputStream(socket.outputStream, config.bufferSize.coerceAtLeast(64 * 1024))
            val secretBytes = config.secretBytes()

            val handshake = withTimeout(10_000) {
                val buf = ByteArray(Protocol.HANDSHAKE_LEN)
                var off = 0
                while (off < Protocol.HANDSHAKE_LEN) {
                    val n = withContext(Dispatchers.IO) { input.read(buf, off, Protocol.HANDSHAKE_LEN - off) }
                    if (n < 0) throw java.io.EOFException()
                    off += n
                }
                buf
            }

            val result = Handshake.tryHandshake(handshake, secretBytes)
            if (result == null) {
                stats.connectionsBad++
                val b0 = handshake[0].toInt() and 0xff
                log(
                    "[$label] bad handshake (wrong secret or proto) " +
                        "b0=0x${"%02X".format(b0)} secret=${maskSecret(config.secret)} — " +
                        "re-open tg://proxy link from this app",
                )
                return
            }

            var dc = result.dcId
            val isMedia = result.isMedia
            val protoTag = result.protoTag
            val isTestDc = config.forceTestDc || dc >= 10000
            if (dc >= 10000) {
                log("[$label] test DC$dc -> DC${dc - 10000}")
                dc -= 10000
            }

            val protoInt = when {
                protoTag.contentEquals(Protocol.PROTO_TAG_ABRIDGED) -> Protocol.PROTO_ABRIDGED_INT
                protoTag.contentEquals(Protocol.PROTO_TAG_INTERMEDIATE) -> Protocol.PROTO_INTERMEDIATE_INT
                else -> Protocol.PROTO_PADDED_INTERMEDIATE_INT
            }
            val dcIdx = if (isMedia) -dc else dc
            val mediaTag = if (isMedia) " media" else ""
            log(
                "[$label] handshake ok: DC$dc$mediaTag proto=0x${"%08X".format(protoInt)}",
            )

            val relayInit = Handshake.generateRelayInit(protoTag, dcIdx)
            val ctx = Handshake.buildCryptoCtx(result.clientDecPrekeyIv, secretBytes, relayInit)
            val dcKey = "$dc${if (isTestDc) "t" else ""}${if (isMedia) "m" else ""}"
            val now = System.nanoTime() / 1_000_000L
            val wsPath = if (isTestDc) Protocol.WS_PATH_TEST else Protocol.WS_PATH
            val target = config.dcRedirects[dc]
            val isAnyCfFallback = config.fallbackCfproxy || config.cfproxyWorkerDomains.isNotEmpty()

            val splitter = runCatching { MsgSplitter(relayInit, protoInt) }.getOrNull()

            if (dc !in config.dcRedirects ||
                dcKey in wsBlacklist ||
                (target != null && now < (ipFailUntil[target] ?: 0L) && isAnyCfFallback)
            ) {
                when {
                    dc !in config.dcRedirects -> log("[$label] DC$dc not in config -> fallback")
                    dcKey in wsBlacklist -> log("[$label] DC$dc$mediaTag WS blacklisted -> fallback")
                    else -> log("[$label] DC$dc$mediaTag WS connect to $target was timed out -> fallback")
                }
                val ok = Fallback.doFallback(
                    input, output, relayInit, label, dc, isTestDc, isMedia, mediaTag,
                    ctx, config, stats, balancer, cfWorkerPool!!, log, splitter,
                )
                if (!ok) log("[$label] DC$dc$mediaTag no fallback available")
                return
            }

            val wsTimeout = if (now < (dcFailUntil[dcKey] ?: 0L)) 2_000L else 5_000L
            val domains = Protocol.wsDomains(dc, isMedia)
            var ws: RawWebSocket? = null
            var wsFailedRedirect = false
            var wsTimedOut = false
            var allRedirects = true
            val allowPoolRefill = now >= (ipFailUntil[target] ?: 0L)

            if (!isTestDc) {
                ws = wsPool?.get(dc, isMedia, target!!, domains, allowRefill = allowPoolRefill)
                if (ws != null) {
                    log("[$label] DC$dc$mediaTag -> pool hit via $target")
                }
            }

            if (ws == null) {
                for (domain in domains) {
                    val url = "wss://$domain$wsPath"
                    log("[$label] DC$dc$mediaTag -> $url via $target")
                    try {
                        ws = RawWebSocket.connect(
                            target!!, domain, timeoutMs = wsTimeout,
                            path = wsPath, bufferSize = config.bufferSize,
                        )
                        allRedirects = false
                        break
                    } catch (e: WsHandshakeError) {
                        stats.wsErrors++
                        if (e.isRedirect) {
                            wsFailedRedirect = true
                            log("[$label] DC$dc$mediaTag got ${e.statusCode} from $domain -> ${e.location ?: "?"}")
                            continue
                        } else {
                            allRedirects = false
                            log("[$label] DC$dc$mediaTag WS handshake: ${e.statusLine}")
                        }
                    } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                        stats.wsErrors++
                        wsTimedOut = true
                        log("[$label] DC$dc$mediaTag WS connect timed out via $domain")
                        break
                    } catch (e: Exception) {
                        stats.wsErrors++
                        allRedirects = false
                        log("[$label] DC$dc$mediaTag WS connect failed: ${e.javaClass.simpleName}: ${e.message}")
                    }
                }
            }

            if (ws == null) {
                if (wsTimedOut && target != null) {
                    ipFailUntil[target] = now + IP_FAIL_COOLDOWN_MS
                    log("[$label] DC$dc$mediaTag WS connect to $target timed out, cooldown for ${IP_FAIL_COOLDOWN_MS / 1000}s")
                }
                if (wsFailedRedirect && allRedirects) {
                    wsBlacklist.add(dcKey)
                    log("[$label] DC$dc$mediaTag blacklisted for WS (all 302)")
                } else {
                    dcFailUntil[dcKey] = now + DC_FAIL_COOLDOWN_MS
                }
                val ok = Fallback.doFallback(
                    input, output, relayInit, label, dc, isTestDc, isMedia, mediaTag,
                    ctx, config, stats, balancer, cfWorkerPool!!, log, splitter,
                )
                if (ok) log("[$label] DC$dc$mediaTag fallback closed")
                return
            }

            dcFailUntil.remove(dcKey)
            if (target != null) ipFailUntil.remove(target)
            wsPool?.reportSuccess(dc, isMedia)
            stats.connectionsWs++

            ws.send(relayInit)
            Bridge.bridgeWsReencrypt(
                input, output, ws, label, ctx, stats, log, dc, isMedia, splitter,
            )
        } catch (e: Exception) {
            if (config.verbose) {
                log("[$label] unexpected: ${e.javaClass.simpleName}: ${e.message}")
            }
        } finally {
            stats.connectionsActive--
            runCatching { socket.close() }
        }
    }

    companion object {
        private const val IP_FAIL_COOLDOWN_MS = 3_600_000L
        private const val DC_FAIL_COOLDOWN_MS = 60_000L

        fun maskSecret(secret: String): String {
            if (secret.length < 8) return "****"
            return secret.take(4) + "…" + secret.takeLast(4)
        }
    }
}
