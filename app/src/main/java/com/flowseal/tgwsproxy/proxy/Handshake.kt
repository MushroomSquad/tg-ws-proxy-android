package com.flowseal.tgwsproxy.proxy

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.experimental.xor

data class HandshakeResult(
    val dcId: Int,
    val isMedia: Boolean,
    val protoTag: ByteArray,
    val clientDecPrekeyIv: ByteArray,
)

object Handshake {
    private val random = SecureRandom()

    fun tryHandshake(handshake: ByteArray, secret: ByteArray): HandshakeResult? {
        if (handshake.size != Protocol.HANDSHAKE_LEN) return null

        val decPrekeyAndIv = handshake.copyOfRange(
            Protocol.SKIP_LEN,
            Protocol.SKIP_LEN + Protocol.PREKEY_LEN + Protocol.IV_LEN,
        )
        val decPrekey = decPrekeyAndIv.copyOfRange(0, Protocol.PREKEY_LEN)
        val decIv = decPrekeyAndIv.copyOfRange(Protocol.PREKEY_LEN, Protocol.PREKEY_LEN + Protocol.IV_LEN)
        val decKey = sha256(decPrekey + secret)

        val decryptor = AesCtr.create(decKey, decIv)
        val decrypted = decryptor.update(handshake)

        val protoTag = decrypted.copyOfRange(Protocol.PROTO_TAG_POS, Protocol.PROTO_TAG_POS + 4)
        if (!protoTag.contentEquals(Protocol.PROTO_TAG_ABRIDGED) &&
            !protoTag.contentEquals(Protocol.PROTO_TAG_INTERMEDIATE) &&
            !protoTag.contentEquals(Protocol.PROTO_TAG_SECURE)
        ) {
            return null
        }

        val dcIdx = ByteBuffer.wrap(decrypted, Protocol.DC_IDX_POS, 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .short
            .toInt()
        val dcId = kotlin.math.abs(dcIdx)
        val isMedia = dcIdx < 0
        return HandshakeResult(dcId, isMedia, protoTag, decPrekeyAndIv)
    }

    fun generateRelayInit(protoTag: ByteArray, dcIdx: Int): ByteArray {
        while (true) {
            val rnd = ByteArray(Protocol.HANDSHAKE_LEN)
            random.nextBytes(rnd)
            if ((rnd[0].toInt() and 0xff) in Protocol.RESERVED_FIRST_BYTES) continue
            if (rnd.copyOfRange(0, 4).toList() in Protocol.RESERVED_STARTS) continue
            if (rnd.copyOfRange(4, 8).contentEquals(Protocol.RESERVED_CONTINUE)) continue

            val encKey = rnd.copyOfRange(Protocol.SKIP_LEN, Protocol.SKIP_LEN + Protocol.PREKEY_LEN)
            val encIv = rnd.copyOfRange(
                Protocol.SKIP_LEN + Protocol.PREKEY_LEN,
                Protocol.SKIP_LEN + Protocol.PREKEY_LEN + Protocol.IV_LEN,
            )
            val encryptor = AesCtr.create(encKey, encIv)
            val encryptedFull = encryptor.update(rnd)

            val keystreamTail = ByteArray(8) { i ->
                (encryptedFull[56 + i] xor rnd[56 + i])
            }
            val dcBytes = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(dcIdx.toShort()).array()
            val pad = ByteArray(2).also { random.nextBytes(it) }
            val tailPlain = protoTag + dcBytes + pad
            val encryptedTail = ByteArray(8) { i -> tailPlain[i] xor keystreamTail[i] }

            val result = rnd.copyOf()
            System.arraycopy(encryptedTail, 0, result, Protocol.PROTO_TAG_POS, 8)
            return result
        }
    }

    fun buildCryptoCtx(clientDecPrekeyIv: ByteArray, secret: ByteArray, relayInit: ByteArray): CryptoCtx {
        val cltDecPrekey = clientDecPrekeyIv.copyOfRange(0, Protocol.PREKEY_LEN)
        val cltDecIv = clientDecPrekeyIv.copyOfRange(Protocol.PREKEY_LEN, Protocol.PREKEY_LEN + Protocol.IV_LEN)
        val cltDecKey = sha256(cltDecPrekey + secret)

        val cltEncPrekeyIv = clientDecPrekeyIv.reversedArray()
        val cltEncKey = sha256(cltEncPrekeyIv.copyOfRange(0, Protocol.PREKEY_LEN) + secret)
        val cltEncIv = cltEncPrekeyIv.copyOfRange(Protocol.PREKEY_LEN, Protocol.PREKEY_LEN + Protocol.IV_LEN)

        val cltDecryptor = AesCtr.create(cltDecKey, cltDecIv)
        val cltEncryptor = AesCtr.create(cltEncKey, cltEncIv)
        cltDecryptor.update(Protocol.ZERO_64)

        val relayEncKey = relayInit.copyOfRange(Protocol.SKIP_LEN, Protocol.SKIP_LEN + Protocol.PREKEY_LEN)
        val relayEncIv = relayInit.copyOfRange(
            Protocol.SKIP_LEN + Protocol.PREKEY_LEN,
            Protocol.SKIP_LEN + Protocol.PREKEY_LEN + Protocol.IV_LEN,
        )
        val relayDecPrekeyIv = relayInit
            .copyOfRange(Protocol.SKIP_LEN, Protocol.SKIP_LEN + Protocol.PREKEY_LEN + Protocol.IV_LEN)
            .reversedArray()
        val relayDecKey = relayDecPrekeyIv.copyOfRange(0, Protocol.KEY_LEN)
        val relayDecIv = relayDecPrekeyIv.copyOfRange(Protocol.KEY_LEN, Protocol.KEY_LEN + Protocol.IV_LEN)

        val tgEncryptor = AesCtr.create(relayEncKey, relayEncIv)
        val tgDecryptor = AesCtr.create(relayDecKey, relayDecIv)
        tgEncryptor.update(Protocol.ZERO_64)

        return CryptoCtx(cltDecryptor, cltEncryptor, tgEncryptor, tgDecryptor)
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)
}

class CryptoCtx(
    val cltDec: AesCtr,
    val cltEnc: AesCtr,
    val tgEnc: AesCtr,
    val tgDec: AesCtr,
)
