package com.flowseal.tgwsproxy.proxy

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AesCtrTest {
    @Test
    fun matchesPythonVector() {
        val key = ByteArray(32) { it.toByte() }
        val iv = ByteArray(16) { it.toByte() }
        val pt = hex("68656c6c6f2074656c656772616d2077732070726f78792121")
        val expected = hex("320b683b67db05f39c4b324f63ae86e513d360a7acfd217c74")
        val ct = AesCtr.create(key, iv).update(pt)
        assertArrayEquals(expected, ct)
        val roundTrip = AesCtr.create(key, iv).update(ct)
        assertArrayEquals(pt, roundTrip)
    }
}

class CfDomainDecodeTest {
    @Test
    fun decodesBuiltInSample() {
        // virkgj.com -> pclead.co.uk (from desktop CFPROXY_DEFAULT_DOMAINS)
        assertEquals("pclead.co.uk", CfDomainRefresh.decodeDomain("virkgj.com"))
        assertEquals("offshor.co.uk", CfDomainRefresh.decodeDomain("vmmzovy.com"))
    }
}

class HandshakeTest {
    @Test
    fun parsesFixedHandshake() {
        val secret = hex("0123456789abcdef0123456789abcdef")
        val handshake = hex(
            "000d1a2734414e5b6875828f9ca9b6c3d0ddeaf704111e2b3845525f6c798693" +
                "a0adbac7d4e1eefb0815222f3c495663707d8a97a4b1becbb29ab919d970c664",
        )
        val result = Handshake.tryHandshake(handshake, secret)
        assertNotNull(result)
        assertEquals(2, result!!.dcId)
        assertFalse(result.isMedia)
        assertArrayEquals(Protocol.PROTO_TAG_SECURE, result.protoTag)
    }

    @Test
    fun rejectsWrongSecret() {
        val handshake = hex(
            "000d1a2734414e5b6875828f9ca9b6c3d0ddeaf704111e2b3845525f6c798693" +
                "a0adbac7d4e1eefb0815222f3c495663707d8a97a4b1becbb29ab919d970c664",
        )
        val wrong = hex("ffffffffffffffffffffffffffffffff")
        assertNull(Handshake.tryHandshake(handshake, wrong))
    }

    @Test
    fun relayInitRoundTripShape() {
        val init = Handshake.generateRelayInit(Protocol.PROTO_TAG_SECURE, 2)
        assertEquals(64, init.size)
        // Without secret hashing, decrypt with raw key from init[8:56]
        val key = init.copyOfRange(8, 40)
        val iv = init.copyOfRange(40, 56)
        val plain = AesCtr.create(key, iv).update(init)
        assertArrayEquals(Protocol.PROTO_TAG_SECURE, plain.copyOfRange(56, 60))
    }
}

class MsgSplitterTest {
    @Test
    fun splitsAbridgedPackets() {
        val relay = Handshake.generateRelayInit(Protocol.PROTO_TAG_ABRIDGED, 2)
        val splitter = MsgSplitter(relay, Protocol.PROTO_ABRIDGED_INT)

        val plainPacket = byteArrayOf(0x01) + byteArrayOf(1, 2, 3, 4)
        val enc = AesCtr.create(relay.copyOfRange(8, 40), relay.copyOfRange(40, 56))
        enc.update(Protocol.ZERO_64)
        val cipher = enc.update(plainPacket)
        val parts = splitter.split(cipher)
        assertEquals(1, parts.size)
        assertArrayEquals(cipher, parts[0])
    }

    @Test
    fun handlesLargeChunkedUploadWithoutQuadraticBlowup() {
        val relay = Handshake.generateRelayInit(Protocol.PROTO_TAG_SECURE, 4)
        val splitter = MsgSplitter(relay, Protocol.PROTO_PADDED_INTERMEDIATE_INT)
        val enc = AesCtr.create(relay.copyOfRange(8, 40), relay.copyOfRange(40, 56))
        enc.update(Protocol.ZERO_64)

        // Many small intermediate packets (~1MB total) — must finish quickly
        val payload = ByteArray(1020) { it.toByte() }
        val header = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(payload.size).array()
        val plainPacket = header + payload
        val start = System.nanoTime()
        var totalParts = 0
        repeat(1024) {
            val cipher = enc.update(plainPacket)
            totalParts += splitter.split(cipher).size
        }
        val ms = (System.nanoTime() - start) / 1_000_000.0
        assertEquals(1024, totalParts)
        // Should be well under a second on any reasonable machine
        assertTrue("splitter too slow: ${ms}ms", ms < 2000.0)
    }
}

private fun hex(s: String): ByteArray =
    s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
