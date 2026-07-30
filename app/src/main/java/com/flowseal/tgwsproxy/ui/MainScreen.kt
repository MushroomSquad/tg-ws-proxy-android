package com.flowseal.tgwsproxy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowseal.tgwsproxy.proxy.ProxyConfig
import com.flowseal.tgwsproxy.proxy.ProxyServer

@Composable
fun MainScreen(
    state: UiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
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

    if (state.firstRun) {
        AlertDialog(
            onDismissRequest = onDismissFirstRun,
            title = { Text("How to connect") },
            text = {
                Text(
                    "1. Tap Start\n" +
                        "2. Tap Open in Telegram (or Copy link)\n" +
                        "3. In Telegram: Settings → Data and Storage → Proxy → enable\n" +
                        "Server: 127.0.0.1  Port: ${state.config.port}\n\n" +
                        "Keep this app running in the background. On some phones, disable battery restrictions.",
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "TgWsProxy",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (state.running) "Running on ${state.config.host}:${state.config.port}"
            else "Stopped",
            color = if (state.running) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondary,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.running) {
                Button(onClick = onStop) { Text("Stop") }
            } else {
                Button(onClick = onStart) { Text("Start") }
            }
            FilledTonalButton(onClick = onCopyLink, enabled = state.config.secret.length == 32) {
                Text("Copy link")
            }
            FilledTonalButton(onClick = onOpenTelegram, enabled = state.config.secret.length == 32) {
                Text("Open Telegram")
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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showSettings = !showSettings }) {
                Text(if (showSettings) "Hide settings" else "Settings")
            }
            OutlinedButton(onClick = onBatteryHint) { Text("Battery") }
            OutlinedButton(onClick = onClearLogs) { Text("Clear logs") }
            OutlinedButton(onClick = onExportLogs) { Text("Save logs") }
            OutlinedButton(onClick = onShareLogs) { Text("Share logs") }
            OutlinedButton(onClick = onRefreshComponents) { Text("Refresh CF") }
        }

        state.componentStatus?.let {
            Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        }

        if (showSettings) {
            SettingsForm(
                initial = state.config,
                enabled = !state.running,
                onSave = onSaveSettings,
            )
        }

        Text("Logs", fontWeight = FontWeight.SemiBold)
        Text(
            text = state.logTail.ifBlank { "No logs yet" },
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(24.dp))
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
    var cfproxy by remember(initial) { mutableStateOf(initial.fallbackCfproxy) }
    var verbose by remember(initial) { mutableStateOf(initial.verbose) }
    var checkUpdates by remember(initial) { mutableStateOf(initial.checkUpdates) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!enabled) {
            Text("Stop the proxy to edit settings.", color = MaterialTheme.colorScheme.error)
        }
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
            onValueChange = { secret = it.filter { ch -> ch.isDigit() || ch in 'a'..'f' || ch in 'A'..'F' }.take(32) },
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
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "If media/files fail: leave only 4:149.154.167.220 or clear this field.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.secondary,
        )
        OutlinedTextField(
            value = workers,
            onValueChange = { workers = it },
            label = { Text("CF worker domains") },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = userDomains,
            onValueChange = { userDomains = it },
            label = { Text("CF user domains") },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("CF proxy fallback", modifier = Modifier.weight(1f))
            Switch(checked = cfproxy, onCheckedChange = { cfproxy = it }, enabled = enabled)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Verbose logs", modifier = Modifier.weight(1f))
            Switch(checked = verbose, onCheckedChange = { verbose = it }, enabled = enabled)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Check app updates", modifier = Modifier.weight(1f))
            Switch(checked = checkUpdates, onCheckedChange = { checkUpdates = it }, enabled = enabled)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            enabled = enabled,
            onClick = {
                val p = port.toIntOrNull()
                if (p == null || p !in 1..65535) {
                    error = "Invalid port"
                    return@Button
                }
                if (secret.length != 32) {
                    error = "Secret must be 32 hex chars"
                    return@Button
                }
                val dcList = dcIp.lines().map { it.trim() }.filter { it.isNotEmpty() }
                val dcMap = try {
                    ProxyConfig.parseDcIpList(dcList)
                } catch (e: Exception) {
                    error = e.message
                    return@Button
                }
                error = null
                onSave(
                    initial.copy(
                        port = p,
                        secret = secret.lowercase(),
                        dcRedirects = dcMap,
                        cfproxyWorkerDomains = ProxyConfig.coerceDomainList(workers),
                        cfproxyUserDomains = ProxyConfig.coerceDomainList(userDomains),
                        fallbackCfproxy = cfproxy,
                        verbose = verbose,
                        checkUpdates = checkUpdates,
                    ),
                )
            },
        ) { Text("Save") }
    }
}
