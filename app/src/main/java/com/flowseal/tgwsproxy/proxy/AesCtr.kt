package com.flowseal.tgwsproxy.proxy

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-CTR as in the first working Android build (platform AES/CTR/NoPadding).
 * Writes into an explicit buffer so providers that return null from update(byte[])
 * still work.
 */
class AesCtr(key: ByteArray, iv: ByteArray) {
    private val cipher: Cipher = Cipher.getInstance("AES/CTR/NoPadding").apply {
        init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.copyOf(), "AES"),
            IvParameterSpec(iv.copyOf()),
        )
    }

    fun update(data: ByteArray): ByteArray {
        if (data.isEmpty()) return ByteArray(0)
        // Prefer in-place API (never null). Same semantics as cryptography CTR update().
        val out = ByteArray(data.size)
        val n = cipher.update(data, 0, data.size, out, 0)
        if (n == data.size) return out
        // Fallback identical to first build for odd providers
        val alt = cipher.update(data)
        if (alt != null && alt.size == data.size) return alt
        throw IllegalStateException("AES/CTR produced $n bytes, expected ${data.size}")
    }

    companion object {
        fun create(key: ByteArray, iv: ByteArray): AesCtr {
            require(key.size == 16 || key.size == 24 || key.size == 32) { "AES key must be 16/24/32" }
            require(iv.size == 16) { "CTR IV must be 16 bytes" }
            return AesCtr(key, iv)
        }
    }
}
