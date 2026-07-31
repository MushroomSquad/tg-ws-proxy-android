package com.flowseal.tgwsproxy.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.flowseal.tgwsproxy.byedpi.services.byeDpiRunning
import com.flowseal.tgwsproxy.data.ConfigRepository
import com.flowseal.tgwsproxy.proxy.ProxyConfig
import com.flowseal.tgwsproxy.service.ProxyForegroundService
import com.flowseal.tgwsproxy.util.AppLog
import com.flowseal.tgwsproxy.util.ComponentUpdater
import com.flowseal.tgwsproxy.util.UpdateChecker
import com.flowseal.tgwsproxy.util.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val config: ProxyConfig = ProxyConfig(),
    val proxyRunning: Boolean = false,
    val vpnRunning: Boolean = false,
    val logTail: String = "",
    val firstRun: Boolean = true,
    val updateInfo: UpdateInfo? = null,
    val componentStatus: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ConfigRepository(app)

    private val refresh = MutableStateFlow(0)
    private val updateInfo = MutableStateFlow<UpdateInfo?>(null)
    private val componentStatus = MutableStateFlow<String?>(null)

    private data class Extra(val refresh: Int, val update: UpdateInfo?, val component: String?)
    private data class ServiceFlags(val proxy: Boolean, val vpn: Boolean)

    val state: StateFlow<UiState> = combine(
        repo.configFlow,
        combine(ProxyForegroundService.running, byeDpiRunning) { p, v -> ServiceFlags(p, v) },
        AppLog.tail,
        repo.firstRunDoneFlow,
        combine(refresh, updateInfo, componentStatus) { r, u, c -> Extra(r, u, c) },
    ) { config, services, logs, firstDone, extra ->
        UiState(
            config = if (config.secret.isEmpty()) config.copy(secret = "…") else config,
            proxyRunning = services.proxy || ProxyForegroundService.isRunning(),
            vpnRunning = services.vpn,
            logTail = logs,
            firstRun = !firstDone,
            updateInfo = extra.update,
            componentStatus = extra.component,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        viewModelScope.launch {
            if (repo.migrateDc2FrontingIfNeeded()) {
                AppLog.i("DC IP restored to 2+4:149.154.167.220 (DC2 WS path)")
            }
            val cfg = repo.ensureSecret()
            refresh.value++
            val result = ComponentUpdater.refreshCfDomains(getApplication())
            componentStatus.value = result.message
            ProxyForegroundService.refreshComponents()
            if (cfg.checkUpdates) {
                updateInfo.value = UpdateChecker.check(getApplication())
            }
        }
    }

    fun refresh() {
        refresh.value++
    }

    fun refreshComponentsNow() {
        viewModelScope.launch {
            componentStatus.value = "Updating Cloudflare domain list…"
            val result = ComponentUpdater.refreshCfDomains(getApplication())
            componentStatus.value = result.message
            ProxyForegroundService.refreshComponents()
            AppLog.i(result.message)
            if (repo.getConfig().checkUpdates) {
                updateInfo.value = UpdateChecker.check(getApplication(), force = true)
            }
        }
    }

    fun dismissUpdate() {
        updateInfo.value = null
    }

    fun saveConfig(config: ProxyConfig) {
        viewModelScope.launch {
            repo.save(config)
            refresh.value++
            if (config.checkUpdates) {
                updateInfo.value = UpdateChecker.check(getApplication())
            } else {
                updateInfo.value = null
            }
        }
    }

    fun copyLink(context: Context) {
        val link = state.value.config.proxyLink()
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("tg-proxy", link))
    }

    fun clearLogs() {
        AppLog.clear()
    }

    fun shareLogs(context: Context): Boolean {
        val file = AppLog.snapshotForExport(context) ?: return false
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share logs"))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun dismissFirstRun() {
        viewModelScope.launch {
            repo.setFirstRunDone()
            refresh.value++
        }
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AppViewModel(app) as T
                }
            }
    }
}
