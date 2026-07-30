package com.flowseal.tgwsproxy.proxy

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.experimental.xor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class WsHandshakeError(
    val statusCode: Int,
    val statusLine: String,
    val location: String? = null,
) : Exception("HTTP $statusCode: $statusLine") {
    val isRedirect: Boolean get() = statusCode in listOf(301, 302, 303, 307, 308)
}

class RawWebSocket(
    private val socket: Socket,
    private val input: DataInputStream,
    private val output: BufferedOutputStream,
) : Closeable {
    @Volatile
    var closed: Boolean = false
        private set

    val isClosing: Boolean
        get() = closed || socket.isClosed || !socket.isConnected

    suspend fun send(data: ByteArray) = withContext(Dispatchers.IO) {
        if (closed) throw java.io.IOException("WebSocket closed")
        output.write(buildFrame(OP_BINARY, data, mask = true))
        output.flush()
    }

    suspend fun sendBatch(parts: List<ByteArray>) = withContext(Dispatchers.IO) {
        if (closed) throw java.io.IOException("WebSocket closed")
        for (part in parts) {
            output.write(buildFrame(OP_BINARY, part, mask = true))
        }
        output.flush()
    }

    suspend fun recv(): ByteArray? = withContext(Dispatchers.IO) {
        while (!closed) {
            val (opcode, payload) = readFrame()
            when (opcode) {
                OP_CLOSE -> {
                    closed = true
                    try {
                        output.write(buildFrame(OP_CLOSE, payload.copyOf(minOf(2, payload.size)), mask = true))
                        output.flush()
                    } catch (_: Exception) {
                    }
                    return@withContext null
                }
                OP_PING -> {
                    try {
                        output.write(buildFrame(OP_PONG, payload, mask = true))
                        output.flush()
                    } catch (_: Exception) {
                    }
                }
                OP_PONG -> Unit
                OP_TEXT, OP_BINARY -> return@withContext payload
                else -> Unit
            }
        }
        null
    }

    suspend fun closeQuietly() = withContext(Dispatchers.IO) {
        if (closed) return@withContext
        closed = true
        try {
            output.write(buildFrame(OP_CLOSE, ByteArray(0), mask = true))
            output.flush()
        } catch (_: Exception) {
        }
        try {
            socket.close()
        } catch (_: Exception) {
        }
    }

    override fun close() {
        closed = true
        try {
            socket.close()
        } catch (_: Exception) {
        }
    }

    private fun readFrame(): Pair<Int, ByteArray> {
        val hdr = ByteArray(2)
        input.readFully(hdr)
        val opcode = hdr[0].toInt() and 0x0F
        var length = hdr[1].toInt() and 0x7F
        if (length == 126) {
            length = ((input.readUnsignedByte() shl 8) or input.readUnsignedByte())
        } else if (length == 127) {
            val lenBuf = ByteArray(8)
            input.readFully(lenBuf)
            length = ByteBuffer.wrap(lenBuf).long.toInt()
        }
        val masked = (hdr[1].toInt() and 0x80) != 0
        val payload = ByteArray(length)
        if (masked) {
            val mask = ByteArray(4)
            input.readFully(mask)
            input.readFully(payload)
            return opcode to xorMask(payload, mask)
        }
        input.readFully(payload)
        return opcode to payload
    }

    companion object {
        const val OP_TEXT = 0x1
        const val OP_BINARY = 0x2
        const val OP_CLOSE = 0x8
        const val OP_PING = 0x9
        const val OP_PONG = 0xA

        private val trustAll: Array<TrustManager> = arrayOf(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            },
        )

        private val sslContext: SSLContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAll, SecureRandom())
        }

        private val random = SecureRandom()

        suspend fun connect(
            host: String,
            domain: String,
            timeoutMs: Long = 10_000,
            path: String = Protocol.WS_PATH,
            sni: String? = null,
            bufferSize: Int = 256 * 1024,
        ): RawWebSocket = withContext(Dispatchers.IO) {
            withTimeout(minOf(timeoutMs, 10_000)) {
                val sniHost = sni ?: domain
                val plain = Socket()
                plain.tcpNoDelay = true
                plain.connect(InetSocketAddress(host, 443), timeoutMs.toInt().coerceAtMost(10_000))
                try {
                    plain.soTimeout = timeoutMs.toInt().coerceAtMost(30_000)
                    plain.receiveBufferSize = bufferSize
                    plain.sendBufferSize = bufferSize
                } catch (_: Exception) {
                }

                val ssl = sslContext.socketFactory.createSocket(plain, sniHost, 443, true) as SSLSocket
                ssl.useClientMode = true
                ssl.startHandshake()

                val input = DataInputStream(BufferedInputStream(ssl.inputStream))
                val output = BufferedOutputStream(ssl.outputStream)

                val wsKey = java.util.Base64.getEncoder().encodeToString(ByteArray(16).also { random.nextBytes(it) })
                val req = buildString {
                    append("GET $path HTTP/1.1\r\n")
                    append("Host: $domain\r\n")
                    append("Upgrade: websocket\r\n")
                    append("Connection: Upgrade\r\n")
                    append("Sec-WebSocket-Key: $wsKey\r\n")
                    append("Sec-WebSocket-Version: 13\r\n")
                    append("Sec-WebSocket-Protocol: binary\r\n")
                    append("\r\n")
                }
                output.write(req.toByteArray(Charsets.US_ASCII))
                output.flush()

                val lines = mutableListOf<String>()
                while (true) {
                    val line = readLineAscii(input) ?: break
                    if (line.isEmpty()) break
                    lines += line
                }
                if (lines.isEmpty()) {
                    ssl.close()
                    throw WsHandshakeError(0, "empty response")
                }
                val first = lines[0]
                val parts = first.split(' ', limit = 3)
                val status = parts.getOrNull(1)?.toIntOrNull() ?: 0
                if (status == 101) {
                    // Connect/handshake used a short soTimeout; clear it so idle DC
                    // pauses don't kill the live session (SocketTimeoutException → Updating…).
                    try {
                        plain.soTimeout = 0
                        ssl.soTimeout = 0
                    } catch (_: Exception) {
                    }
                    return@withTimeout RawWebSocket(ssl, input, output)
                }
                val headers = mutableMapOf<String, String>()
                for (hl in lines.drop(1)) {
                    val c = hl.indexOf(':')
                    if (c > 0) {
                        headers[hl.substring(0, c).trim().lowercase()] = hl.substring(c + 1).trim()
                    }
                }
                ssl.close()
                throw WsHandshakeError(status, first, headers["location"])
            }
        }

        private fun readLineAscii(input: DataInputStream): String? {
            val sb = StringBuilder()
            while (true) {
                val b = try {
                    input.read()
                } catch (_: EOFException) {
                    -1
                }
                if (b < 0) {
                    return if (sb.isEmpty()) null else sb.toString()
                }
                if (b == '\n'.code) break
                if (b != '\r'.code) sb.append(b.toChar())
            }
            return sb.toString()
        }

        private fun buildFrame(opcode: Int, data: ByteArray, mask: Boolean): ByteArray {
            val length = data.size
            val fb = (0x80 or opcode).toByte()
            if (!mask) {
                return when {
                    length < 126 -> byteArrayOf(fb, length.toByte()) + data
                    length < 65536 -> byteArrayOf(fb, 126) + shortBe(length) + data
                    else -> byteArrayOf(fb, 127) + longBe(length.toLong()) + data
                }
            }
            val maskKey = ByteArray(4).also { random.nextBytes(it) }
            val masked = xorMask(data, maskKey)
            return when {
                length < 126 -> byteArrayOf(fb, (0x80 or length).toByte()) + maskKey + masked
                length < 65536 -> byteArrayOf(fb, (0x80 or 126).toByte()) + shortBe(length) + maskKey + masked
                else -> byteArrayOf(fb, (0x80 or 127).toByte()) + longBe(length.toLong()) + maskKey + masked
            }
        }

        private fun shortBe(v: Int): ByteArray = byteArrayOf(((v ushr 8) and 0xff).toByte(), (v and 0xff).toByte())

        private fun longBe(v: Long): ByteArray = ByteBuffer.allocate(8).putLong(v).array()

        private fun xorMask(data: ByteArray, mask: ByteArray): ByteArray {
            if (data.isEmpty()) return data
            return ByteArray(data.size) { i -> data[i] xor mask[i % 4] }
        }
    }
}
