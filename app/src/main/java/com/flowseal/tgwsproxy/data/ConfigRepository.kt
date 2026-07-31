package com.flowseal.tgwsproxy.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flowseal.tgwsproxy.proxy.ProxyConfig
import java.security.SecureRandom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("tgwsproxy_config")

class ConfigRepository(private val context: Context) {
    private object Keys {
        val host = stringPreferencesKey("host")
        val port = intPreferencesKey("port")
        val secret = stringPreferencesKey("secret")
        val dcIp = stringPreferencesKey("dc_ip")
        val cfproxy = booleanPreferencesKey("cfproxy")
        val workerDomains = stringPreferencesKey("cfproxy_worker_domain")
        val userDomains = stringPreferencesKey("cfproxy_user_domain")
        val verbose = booleanPreferencesKey("verbose")
        val firstRunDone = booleanPreferencesKey("first_run_done")
        val poolSize = intPreferencesKey("pool_size")
        val checkUpdates = booleanPreferencesKey("check_updates")
        val includeProxyConnect = booleanPreferencesKey("include_proxy_connect")
        val includeVpnConnect = booleanPreferencesKey("include_vpn_connect")
    }

    val configFlow: Flow<ProxyConfig> = context.dataStore.data.map { prefs ->
        prefs.toConfig()
    }

    val firstRunDoneFlow: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.firstRunDone] ?: false
    }

    val includeProxyFlow: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.includeProxyConnect] ?: true
    }

    val includeVpnFlow: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.includeVpnConnect] ?: true
    }

    suspend fun getConfig(): ProxyConfig = configFlow.first()

    suspend fun ensureSecret(): ProxyConfig {
        val current = getConfig()
        if (current.secret.length == 32) return current
        val secret = randomSecret()
        save(current.copy(secret = secret))
        return getConfig()
    }

    /**
     * Expand DC4-only fronting map back to DC2+DC4 on the same IP.
     * DC4-only left DC2 accounts with only CF/TCP fallback (often dead).
     */
    suspend fun migrateDc2FrontingIfNeeded(): Boolean {
        val prefs = context.dataStore.data.first()
        val raw = prefs[Keys.dcIp] ?: return false
        val parsed = runCatching {
            ProxyConfig.parseDcIpList(raw.lines().map { it.trim() }.filter { it.isNotEmpty() })
        }.getOrNull() ?: return false
        if (!isDc4OnlyFronting(parsed)) return false
        save(prefs.toConfig().copy(dcRedirects = DEFAULT_DC_REDIRECTS))
        return true
    }

    suspend fun save(config: ProxyConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.host] = config.host
            prefs[Keys.port] = config.port
            prefs[Keys.secret] = config.secret
            prefs[Keys.dcIp] = config.dcRedirects.entries.joinToString("\n") { "${it.key}:${it.value}" }
            prefs[Keys.cfproxy] = config.fallbackCfproxy
            prefs[Keys.workerDomains] = config.cfproxyWorkerDomains.joinToString(" ")
            prefs[Keys.userDomains] = config.cfproxyUserDomains.joinToString(" ")
            prefs[Keys.verbose] = config.verbose
            prefs[Keys.poolSize] = config.poolSize
            prefs[Keys.checkUpdates] = config.checkUpdates
        }
    }

    suspend fun setFirstRunDone() {
        context.dataStore.edit { it[Keys.firstRunDone] = true }
    }

    suspend fun setFirstRunDoneFalse() {
        context.dataStore.edit { it[Keys.firstRunDone] = false }
    }

    suspend fun setIncludeProxy(value: Boolean) {
        context.dataStore.edit { it[Keys.includeProxyConnect] = value }
    }

    suspend fun setIncludeVpn(value: Boolean) {
        context.dataStore.edit { it[Keys.includeVpnConnect] = value }
    }

    private fun Preferences.toConfig(): ProxyConfig {
        val dcRaw = this[Keys.dcIp]
        val dcMap = if (dcRaw == null) {
            DEFAULT_DC_REDIRECTS
        } else {
            val dcList = dcRaw.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (dcList.isEmpty()) {
                emptyMap()
            } else {
                val parsed = runCatching { ProxyConfig.parseDcIpList(dcList) }.getOrElse {
                    DEFAULT_DC_REDIRECTS
                }
                // Expand DC4-only fronting before prefs migrate writes
                if (isDc4OnlyFronting(parsed)) DEFAULT_DC_REDIRECTS else parsed
            }
        }
        return ProxyConfig(
            host = this[Keys.host] ?: "127.0.0.1",
            port = this[Keys.port] ?: 1443,
            secret = this[Keys.secret] ?: "",
            dcRedirects = dcMap,
            fallbackCfproxy = this[Keys.cfproxy] ?: true,
            cfproxyWorkerDomains = ProxyConfig.coerceDomainList(this[Keys.workerDomains] ?: ""),
            cfproxyUserDomains = ProxyConfig.coerceDomainList(this[Keys.userDomains] ?: ""),
            verbose = this[Keys.verbose] ?: false,
            poolSize = this[Keys.poolSize] ?: 4,
            checkUpdates = this[Keys.checkUpdates] ?: true,
        )
    }

    companion object {
        const val FRONTING_DC_IP = "149.154.167.220"
        val DEFAULT_DC_REDIRECTS: Map<Int, String> = mapOf(
            2 to FRONTING_DC_IP,
            4 to FRONTING_DC_IP,
        )

        fun isDc4OnlyFronting(map: Map<Int, String>): Boolean =
            map.size == 1 && map[4] == FRONTING_DC_IP

        fun randomSecret(): String {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
