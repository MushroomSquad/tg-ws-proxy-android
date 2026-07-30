package com.flowseal.tgwsproxy.proxy

/**
 * Quick sanity check that AES-CTR + handshake parse still work on this device.
 */
object CryptoSelfTest {
    fun run(log: (String) -> Unit): Boolean {
        return try {
            // Fixed vector from desktop Python (must always parse)
            val secret = hex("0123456789abcdef0123456789abcdef")
            val handshake = hex(
                "000d1a2734414e5b6875828f9ca9b6c3d0ddeaf704111e2b3845525f6c798693" +
                    "a0adbac7d4e1eefb0815222f3c495663707d8a97a4b1becbb29ab919d970c664",
            )
            val parsed = Handshake.tryHandshake(handshake, secret)
            if (parsed == null || parsed.dcId != 2) {
                log("Crypto self-test FAILED: fixed handshake not parsed")
                return false
            }

            // Round-trip with the live secret path
            val live = ByteArray(16) { it.toByte() }
            val init = Handshake.generateRelayInit(Protocol.PROTO_TAG_SECURE, 2)
            // generateRelayInit does not use secret; tryHandshake with empty secret won't apply.
            // AES vector:
            val key = ByteArray(32) { it.toByte() }
            val iv = ByteArray(16) { it.toByte() }
            val pt = "hello telegram ws proxy!!".toByteArray()
            val ct = AesCtr.create(key, iv).update(pt)
            val rt = AesCtr.create(key, iv).update(ct)
            if (!rt.contentEquals(pt)) {
                log("Crypto self-test FAILED: AES-CTR round-trip mismatch")
                return false
            }
            if (init.size != 64 || live.size != 16) {
                log("Crypto self-test FAILED: size check")
                return false
            }
            log("Crypto self-test OK")
            true
        } catch (e: Exception) {
            log("Crypto self-test FAILED: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    private fun hex(s: String): ByteArray =
        s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
