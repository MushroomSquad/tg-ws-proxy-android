package com.flowseal.tgwsproxy.proxy

object Protocol {
    val ZERO_64: ByteArray = ByteArray(64)
    const val HANDSHAKE_LEN = 64
    const val SKIP_LEN = 8
    const val PREKEY_LEN = 32
    const val KEY_LEN = 32
    const val IV_LEN = 16
    const val PROTO_TAG_POS = 56
    const val DC_IDX_POS = 60

    val PROTO_TAG_ABRIDGED: ByteArray = byteArrayOf(0xef.toByte(), 0xef.toByte(), 0xef.toByte(), 0xef.toByte())
    val PROTO_TAG_INTERMEDIATE: ByteArray = byteArrayOf(0xee.toByte(), 0xee.toByte(), 0xee.toByte(), 0xee.toByte())
    val PROTO_TAG_SECURE: ByteArray = byteArrayOf(0xdd.toByte(), 0xdd.toByte(), 0xdd.toByte(), 0xdd.toByte())

    const val PROTO_ABRIDGED_INT = 0xEFEFEFEF.toInt()
    const val PROTO_INTERMEDIATE_INT = 0xEEEEEEEE.toInt()
    const val PROTO_PADDED_INTERMEDIATE_INT = 0xDDDDDDDD.toInt()

    val RESERVED_FIRST_BYTES: Set<Int> = setOf(0xEF)
    val RESERVED_STARTS: Set<List<Byte>> = setOf(
        listOf(0x48, 0x45, 0x41, 0x44), // HEAD
        listOf(0x50, 0x4F, 0x53, 0x54), // POST
        listOf(0x47, 0x45, 0x54, 0x20), // GET
        listOf(0xee, 0xee, 0xee, 0xee),
        listOf(0xdd, 0xdd, 0xdd, 0xdd),
        listOf(0x16, 0x03, 0x01, 0x02),
    ).map { ints -> ints.map { (it and 0xff).toByte() } }.toSet()
    val RESERVED_CONTINUE: ByteArray = byteArrayOf(0, 0, 0, 0)

    val DC_DEFAULT_IPS: Map<Int, String> = mapOf(
        1 to "149.154.175.50",
        2 to "149.154.167.51",
        3 to "149.154.175.100",
        4 to "149.154.167.91",
        5 to "149.154.171.5",
        203 to "91.105.192.100",
    )

    val DC_TEST_IPS: Map<Int, String> = mapOf(
        1 to "149.154.175.10",
        2 to "149.154.167.40",
        3 to "149.154.175.117",
    )

    const val WS_PATH = "/apiws"
    const val WS_PATH_TEST = "/apiws_test"

    val CFPROXY_DEFAULT_DOMAINS: List<String> = listOf(
        "pclead.co.uk",
        "offshor.co.uk",
        "cakeisalie.co.uk",
        "noskomnadzor.co.uk",
        "lovetrue.co.uk",
        "sorokdva.co.uk",
        "pyatdesyatdva.co.uk",
        "kartoshka.co.uk",
        "sorokodin.co.uk",
        "pyatdesyatodin.co.uk",
        "notelega.co.uk",
        "ebally.co.uk",
        "nebally.co.uk",
        "havegreatday.co.uk",
        "pomogite.co.uk",
        "fixtelega.co.uk",
        "sadnews.co.uk",
        "onedaychamp.co.uk",
        "stopblocking.co.uk",
        "nothingthere.co.uk",
    )

    fun wsDomains(dc: Int, isMedia: Boolean?): List<String> {
        var d = dc
        if (d == 203) d = 2
        return if (isMedia == null || isMedia) {
            listOf("kws$d-1.web.telegram.org", "kws$d.web.telegram.org")
        } else {
            listOf("kws$d.web.telegram.org", "kws$d-1.web.telegram.org")
        }
    }

    fun humanBytes(n: Long): String {
        var v = n.toDouble()
        for (unit in listOf("B", "KB", "MB", "GB")) {
            if (kotlin.math.abs(v) < 1024) {
                return "%.1f%s".format(v, unit)
            }
            v /= 1024.0
        }
        return "%.1fTB".format(v)
    }
}
