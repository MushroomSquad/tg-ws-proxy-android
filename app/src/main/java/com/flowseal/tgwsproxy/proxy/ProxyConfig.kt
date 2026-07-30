package com.flowseal.tgwsproxy.proxy

data class ProxyConfig(
    var host: String = "127.0.0.1",
    var port: Int = 1443,
    var secret: String = "",
    var dcRedirects: Map<Int, String> = mapOf(
        // DC2+DC4 on fronting IP — needed when account lives on DC2 (WS via 220).
        // For media issues on non-Premium see Settings tip / docs/README.md.
        2 to "149.154.167.220",
        4 to "149.154.167.220",
    ),
    var bufferSize: Int = 256 * 1024,
    var poolSize: Int = 4,
    var fallbackCfproxy: Boolean = true,
    var cfproxyUserDomains: List<String> = emptyList(),
    var cfproxyWorkerDomains: List<String> = emptyList(),
    var forceTestDc: Boolean = false,
    var verbose: Boolean = false,
    var checkUpdates: Boolean = true,
) {
    fun secretBytes(): ByteArray {
        require(secret.length == 32) { "Secret must be 32 hex chars" }
        return secret.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    fun proxyLink(): String =
        "tg://proxy?server=$host&port=$port&secret=dd$secret"

    companion object {
        fun parseDcIpList(entries: List<String>): Map<Int, String> {
            val out = linkedMapOf<Int, String>()
            for (entry in entries) {
                val idx = entry.indexOf(':')
                require(idx > 0) { "Invalid dc_ip format: $entry" }
                val dc = entry.substring(0, idx).toInt()
                val ip = entry.substring(idx + 1)
                out[dc] = ip
            }
            return out
        }

        fun coerceDomainList(raw: String): List<String> {
            val seen = linkedSetOf<String>()
            for (part in raw.replace(',', ' ').replace(';', ' ').split(Regex("\\s+"))) {
                val item = part.trim()
                if (item.isNotEmpty()) seen.add(item)
            }
            return seen.toList()
        }
    }
}
