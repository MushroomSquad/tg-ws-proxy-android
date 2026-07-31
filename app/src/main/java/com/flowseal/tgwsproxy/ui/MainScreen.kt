package com.flowseal.tgwsproxy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowseal.tgwsproxy.proxy.ProxyConfig
import com.flowseal.tgwsproxy.proxy.ProxyServer
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    state: UiState,
    onStartProxy: () -> Unit,
    onStopProxy: () -> Unit,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
    onOpenByeDpiSettings: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenTelegram: () -> Unit,
    onSaveSettings: (ProxyConfig) -> Unit,
    onClearLogs: () -> Unit,
    onExportLogs: () -> Unit = {},
    onShareLogs: () -> Unit = {},
    onDismissFirstRun: () -> Unit,
    onBatteryHint: () -> Unit,
    onDismissUpdate: () -> Unit = {},
    onOpenUpdate: (String) -> Unit = {},
    onRefreshComponents: () -> Unit = {},
) {
    var showSettings by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    if (state.firstRun) {
        AlertDialog(
            onDismissRequest = onDismissFirstRun,
            title = { Text("How to connect") },
            text = {
                Text(
                    "ByeDPI VPN (optional):\n" +
                        "1. Tap Start VPN and allow the VPN permission\n" +
                        "2. System traffic goes through local DPI bypass\n\n" +
                        "Telegram proxy:\n" +
                        "1. Tap Start proxy\n" +
                        "2. Tap Open in Telegram (or Copy link)\n" +
                        "3. In Telegram: Settings → Data and Storage → Proxy → enable\n" +
                        "Server: 127.0.0.1  Port: ${state.config.port}\n\n" +
                        "Both can run together. Keep this app in the background.",
                )
            },
            confirmButton = {
                TextButton(onClick = onDismissFirstRun) { Text("Got it") }
            },
        )
    }

    state.updateInfo?.let { upd ->
        if (upd.apkUrl != null) {
            AlertDialog(
                onDismissRequest = onDismissUpdate,
                title = { Text("APK update available") },
                text = {
                    Text("New version: ${upd.latestVersion}\nAPK: ${upd.apkName ?: "app-release.apk"}")
                },
                confirmButton = {
                    TextButton(onClick = { onOpenUpdate(upd.apkUrl) }) { Text("Download APK") }
                },
                dismissButton = {
                    TextButton(onClick = onDismissUpdate) { Text("Later") }
                },
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "TgWsProxy",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            Text("ByeDPI VPN", fontWeight = FontWeight.SemiBold)
            Text(
                text = if (state.vpnRunning) "VPN connected" else "VPN stopped",
                color = if (state.vpnRunning) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondary,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.vpnRunning) {
                    Button(onClick = onStopVpn) { Text("Stop VPN", maxLines = 1) }
                } else {
                    Button(onClick = onStartVpn) { Text("Start VPN", maxLines = 1) }
                }
                OutlinedButton(onClick = onOpenByeDpiSettings) {
                    Text("ByeDPI settings", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            HorizontalDivider()

            Text("Telegram proxy", fontWeight = FontWeight.SemiBold)
            Text(
                text = if (state.proxyRunning) "Running on ${state.config.host}:${state.config.port}"
                else "Stopped",
                color = if (state.proxyRunning) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondary,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.proxyRunning) {
                    Button(onClick = onStopProxy) { Text("Stop proxy", maxLines = 1) }
                } else {
                    Button(onClick = onStartProxy) { Text("Start proxy", maxLines = 1) }
                }
                FilledTonalButton(onClick = onCopyLink, enabled = state.config.secret.length == 32) {
                    Text("Copy link", maxLines = 1)
                }
                FilledTonalButton(onClick = onOpenTelegram, enabled = state.config.secret.length == 32) {
                    Text("Open Telegram", maxLines = 1)
                }
            }

            Text("Secret: ${ProxyServer.maskSecret(state.config.secret)}")
            Text(
                text = state.config.proxyLink().let {
                    if (it.contains("dd") && state.config.secret.length == 32) it
                    else "tg://proxy?…"
                },
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { showSettings = !showSettings }) {
                    Text(
                        if (showSettings) "Hide proxy settings" else "Proxy settings",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(onClick = onBatteryHint) {
                    Text("Battery", maxLines = 1)
                }
            }

            Text("Cloudflare fallback", fontWeight = FontWeight.SemiBold)
            Text(
                text = "If a Telegram DC is blocked, the proxy can tunnel via Cloudflare domains. " +
                    "The domain list is downloaded from the project GitHub " +
                    "(.github/cfproxy-domains.txt) and cached on the phone. " +
                    "Update only when media/proxy fails or the list is stale.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onRefreshComponents) {
                    Text("Update domain list", maxLines = 1)
                }
            }
            Text(
                text = state.componentStatus ?: "Using built-in or cached domain list",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.secondary,
            )

            if (showSettings) {
                SettingsForm(
                    initial = state.config,
                    enabled = !state.proxyRunning,
                    onSave = onSaveSettings,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Logs", fontWeight = FontWeight.SemiBold)
                Switch(checked = showLogs, onCheckedChange = { showLogs = it })
            }
            if (showLogs) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onClearLogs) {
                        Text("Clear", maxLines = 1)
                    }
                    OutlinedButton(onClick = onExportLogs) {
                        Text("Save", maxLines = 1)
                    }
                    OutlinedButton(onClick = onShareLogs) {
                        Text("Share", maxLines = 1)
                    }
                }
                Text(
                    text = state.logTail.ifBlank { "No logs yet" },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.height(72.dp))
        }

        if (scrollState.value > 240) {
            FloatingActionButton(
                onClick = { scope.launch { scrollState.animateScrollTo(0) } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Scroll to top",
                )
            }
        }
    }
}

@Composable
private fun SettingsForm(
    initial: ProxyConfig,
    enabled: Boolean,
    onSave: (ProxyConfig) -> Unit,
) {
    var port by remember(initial) { mutableStateOf(initial.port.toString()) }
    var secret by remember(initial) { mutableStateOf(initial.secret) }
    var dcIp by remember(initial) {
        mutableStateOf(initial.dcRedirects.entries.joinToString("\n") { "${it.key}:${it.value}" })
    }
    var workers by remember(initial) {
        mutableStateOf(initial.cfproxyWorkerDomains.joinToString(" "))
    }
    var userDomains by remember(initial) {
        mutableStateOf(initial.cfproxyUserDomains.joinToString(" "))
    }
    var cf by remember(initial) { mutableStateOf(initial.fallbackCfproxy) }
    var verbose by remember(initial) { mutableStateOf(initial.verbose) }
    var pool by remember(initial) { mutableStateOf(initial.poolSize.toString()) }
    var checkUpdates by remember(initial) { mutableStateOf(initial.checkUpdates) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("Port") },
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = secret,
            onValueChange = { secret = it.filter { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' }.take(32) },
            label = { Text("Secret (32 hex)") },
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = dcIp,
            onValueChange = { dcIp = it },
            label = { Text("DC IP (DC:IP per line; empty = always fallback)") },
            enabled = enabled,
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "If media/files fail: leave only 4:149.154.167.220 or clear this field.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = "Cloudflare: used only when direct DC connect fails. Leave empty to use the " +
                "shared GitHub list (Update domain list on the main screen).",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.secondary,
        )
        OutlinedTextField(
            value = workers,
            onValueChange = { workers = it },
            label = { Text("Own CF Worker domains (optional)") },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = userDomains,
            onValueChange = { userDomains = it },
            label = { Text("Own CF proxy domains (optional)") },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = pool,
            onValueChange = { pool = it },
            label = { Text("WS pool size") },
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Use Cloudflare fallback", modifier = Modifier.weight(1f))
            Switch(checked = cf, onCheckedChange = { cf = it }, enabled = enabled)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Detailed proxy logs", modifier = Modifier.weight(1f))
            Switch(checked = verbose, onCheckedChange = { verbose = it }, enabled = enabled)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Check APK updates", modifier = Modifier.weight(1f))
            Switch(checked = checkUpdates, onCheckedChange = { checkUpdates = it }, enabled = enabled)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            enabled = enabled,
            onClick = {
                try {
                    val p = port.toInt()
                    require(p in 1..65535)
                    require(secret.length == 32)
                    val dcList = dcIp.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    onSave(
                        ProxyConfig(
                            host = "127.0.0.1",
                            port = p,
                            secret = secret.lowercase(),
                            dcRedirects = if (dcList.isEmpty()) emptyMap()
                            else ProxyConfig.parseDcIpList(dcList),
                            fallbackCfproxy = cf,
                            cfproxyWorkerDomains = ProxyConfig.coerceDomainList(workers),
                            cfproxyUserDomains = ProxyConfig.coerceDomainList(userDomains),
                            verbose = verbose,
                            poolSize = pool.toInt().coerceIn(0, 32),
                            checkUpdates = checkUpdates,
                        ),
                    )
                    error = null
                } catch (e: Exception) {
                    error = e.message ?: "Invalid settings"
                }
            },
        ) { Text("Save settings") }
    }
}
