package com.flowseal.tgwsproxy.proxy

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Splits TCP stream data into individual MTProto transport packets
 * so each can be sent as a separate WS frame.
 *
 * Uses a growable byte buffer with an offset cursor (like the Python bytearray
 * version) — critical for media uploads; ArrayList&lt;Byte&gt;+removeAt(0) was O(n²).
 */
class MsgSplitter(relayInit: ByteArray, private val proto: Int) {
    private val dec: AesCtr = AesCtr.create(
        relayInit.copyOfRange(8, 40),
        relayInit.copyOfRange(40, 56),
    ).also { it.update(Protocol.ZERO_64) }

    private var cipherBuf = ByteArray(0)
    private var plainBuf = ByteArray(0)
    private var size = 0
    private var disabled = false

    fun split(chunk: ByteArray): List<ByteArray> {
        if (chunk.isEmpty()) return emptyList()
        if (disabled) return listOf(chunk)

        ensureCapacity(size + chunk.size)
        System.arraycopy(chunk, 0, cipherBuf, size, chunk.size)
        val plain = dec.update(chunk)
        System.arraycopy(plain, 0, plainBuf, size, plain.size)
        size += chunk.size

        val parts = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < size) {
            val packetLen = nextPacketLen(offset, size - offset) ?: break
            if (packetLen <= 0) {
                parts += cipherBuf.copyOfRange(offset, size)
                offset = size
                disabled = true
                break
            }
            parts += cipherBuf.copyOfRange(offset, offset + packetLen)
            offset += packetLen
        }

        if (offset > 0) {
            val remain = size - offset
            if (remain > 0) {
                System.arraycopy(cipherBuf, offset, cipherBuf, 0, remain)
                System.arraycopy(plainBuf, offset, plainBuf, 0, remain)
            }
            size = remain
        }
        return parts
    }

    fun flush(): List<ByteArray> {
        if (size == 0) return emptyList()
        val tail = cipherBuf.copyOfRange(0, size)
        size = 0
        return listOf(tail)
    }

    private fun ensureCapacity(needed: Int) {
        if (cipherBuf.size >= needed) return
        var cap = if (cipherBuf.isEmpty()) 4096 else cipherBuf.size
        while (cap < needed) cap *= 2
        cipherBuf = cipherBuf.copyOf(cap)
        plainBuf = plainBuf.copyOf(cap)
    }

    private fun nextPacketLen(offset: Int, avail: Int): Int? {
        if (avail <= 0) return null
        return when (proto) {
            Protocol.PROTO_ABRIDGED_INT -> nextAbridged(offset, avail)
            Protocol.PROTO_INTERMEDIATE_INT,
            Protocol.PROTO_PADDED_INTERMEDIATE_INT,
            -> nextIntermediate(offset, avail)
            else -> 0
        }
    }

    private fun nextAbridged(offset: Int, avail: Int): Int? {
        val first = plainBuf[offset].toInt() and 0xff
        val headerLen: Int
        val payloadLen: Int
        if (first == 0x7F || first == 0xFF) {
            if (avail < 4) return null
            payloadLen = (
                (plainBuf[offset + 1].toInt() and 0xff) or
                    ((plainBuf[offset + 2].toInt() and 0xff) shl 8) or
                    ((plainBuf[offset + 3].toInt() and 0xff) shl 16)
                ) * 4
            headerLen = 4
        } else {
            payloadLen = (first and 0x7F) * 4
            headerLen = 1
        }
        if (payloadLen <= 0) return 0
        val packetLen = headerLen + payloadLen
        if (avail < packetLen) return null
        return packetLen
    }

    private fun nextIntermediate(offset: Int, avail: Int): Int? {
        if (avail < 4) return null
        val payloadLen = ByteBuffer.wrap(plainBuf, offset, 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .int and 0x7FFFFFFF
        if (payloadLen <= 0) return 0
        // Guard absurd lengths (corrupt stream) — disable splitter
        if (payloadLen > 16 * 1024 * 1024) return 0
        val packetLen = 4 + payloadLen
        if (avail < packetLen) return null
        return packetLen
    }
}
