package com.flowseal.tgwsproxy.util

import android.content.Context
import com.flowseal.tgwsproxy.proxy.CfDomainRefresh
import com.flowseal.tgwsproxy.proxy.Protocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ComponentRefreshResult(
    val ok: Boolean,
    val domainCount: Int,
    val message: String,
)

/**
 * Pulls remote updatable components (CF domain list), not the upstream desktop Releases page.
 */
object ComponentUpdater {
    suspend fun refreshCfDomains(context: Context): ComponentRefreshResult = withContext(Dispatchers.IO) {
        AppLog.init(context)
        val fetched = CfDomainRefresh.fetchEncodedList()
        val pool = CfDomainRefresh.normalize(fetched.map { CfDomainRefresh.decodeDomain(it) })
        if (pool.size >= 3) {
            // Persist last good pool for next cold start
            context.getSharedPreferences("components", Context.MODE_PRIVATE)
                .edit()
                .putString("cf_domains", pool.joinToString("\n"))
                .apply()
            AppLog.i("CF domains refreshed: ${pool.size}")
            ComponentRefreshResult(true, pool.size, "CF domains updated: ${pool.size}")
        } else if (fetched.isEmpty()) {
            AppLog.w("CF domain refresh failed (network/empty)")
            ComponentRefreshResult(false, 0, "Refresh failed — no data from GitHub")
        } else {
            AppLog.w("CF domain refresh ignored (valid=${pool.size})")
            ComponentRefreshResult(false, pool.size, "Refresh ignored — only ${pool.size} valid domains")
        }
    }

    fun loadCachedDomains(context: Context): List<String> {
        val raw = context.getSharedPreferences("components", Context.MODE_PRIVATE)
            .getString("cf_domains", null) ?: return Protocol.CFPROXY_DEFAULT_DOMAINS
        val list = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return if (list.size >= 3) list else Protocol.CFPROXY_DEFAULT_DOMAINS
    }
}
