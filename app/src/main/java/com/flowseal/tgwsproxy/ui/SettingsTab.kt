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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowseal.tgwsproxy.BuildConfig
import com.flowseal.tgwsproxy.proxy.ProxyConfig
import com.flowseal.tgwsproxy.proxy.ProxyServer

@Composable
fun SettingsTab(
    state: UiState,
    onSave: (ProxyConfig) -> Unit,
    onRefreshCf: () -> Unit,
    onBatteryHint: () -> Unit,
    onShowFirstRun: () -> Unit,
    onOpenByeDpiSettings: () -> Unit,
    onOpenUpdate: (String) -> Unit,
) {
    var saveHint by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = AmneziaColors.Text)

        ProxySettingsCard(
            state = state,
            onSave = { cfg ->
                onSave(cfg)
                saveHint = if (state.proxyRunning) {
                    "Saved — restart proxy (Stop then Connect) to apply"
                } else {
                    "Settings saved"
                }
            },
        )
        saveHint?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = AmneziaColors.Accent)
        }

        AmneziaCard {
            Text("Cloudflare", fontWeight = FontWeight.SemiBold, color = AmneziaColors.Text)
            Text(
                "If a Telegram DC is blocked, the proxy can tunnel via Cloudflare domains. " +
                    "Update when media/proxy fails or the list is stale.",
                style = MaterialTheme.typography.labelMedium,
                color = AmneziaColors.Muted,
            )
            TextButton(onClick = onRefreshCf) {
                Text("Update domain list", color = AmneziaColors.Accent)
            }
            Text(
                state.componentStatus ?: "Using built-in or cached domain list",
                style = MaterialTheme.typography.labelMedium,
                color = AmneziaColors.Muted,
            )
        }

        AmneziaCard {
            Text("App", fontWeight = FontWeight.SemiBold, color = AmneziaColors.Text)
            Text(
                "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.labelMedium,
                color = AmneziaColors.Muted,
            )
            TextButton(onClick = onBatteryHint) {
                Text("Battery optimization", color = AmneziaColors.Accent)
            }
            TextButton(onClick = onShowFirstRun) {
                Text("How to connect tip", color = AmneziaColors.Accent)
            }
            state.updateInfo?.apkUrl?.let { url ->
                TextButton(onClick = { onOpenUpdate(url) }) {
                    Text("Download update ${state.updateInfo.latestVersion}", color = AmneziaColors.Accent)
                }
            }
        }

        AmneziaCard {
            Text("ByeDPI", fontWeight = FontWeight.SemiBold, color = AmneziaColors.Text)
            Text(
                "Full ByeDPI preference screens",
                style = MaterialTheme.typography.labelMedium,
                color = AmneziaColors.Muted,
            )
            TextButton(onClick = onOpenByeDpiSettings) {
                Text("Open ByeDPI settings", color = AmneziaColors.Accent)
            }
        }
    }
}

@Composable
private fun ProxySettingsCard(
    state: UiState,
    onSave: (ProxyConfig) -> Unit,
) {
    val initial = state.config
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

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = AmneziaColors.Text,
        unfocusedTextColor = AmneziaColors.Text,
        focusedBorderColor = AmneziaColors.Accent,
        unfocusedBorderColor = AmneziaColors.Border,
        cursorColor = AmneziaColors.Accent,
        focusedContainerColor = AmneziaColors.Bg,
        unfocusedContainerColor = AmneziaColors.Bg,
        focusedPlaceholderColor = AmneziaColors.Muted,
        unfocusedPlaceholderColor = AmneziaColors.Muted,
    )

    AmneziaCard {
        Text("Proxy", fontWeight = FontWeight.SemiBold, color = AmneziaColors.Text)
        Text(
            "127.0.0.1 · ${ProxyServer.maskSecret(state.config.secret)}",
            style = MaterialTheme.typography.labelMedium,
            color = AmneziaColors.Muted,
        )
        SettingsField(
            caption = "Port",
            value = port,
            onValueChange = { port = it },
            singleLine = true,
            colors = fieldColors,
        )
        SettingsField(
            caption = "Secret (32 hex)",
            value = secret,
            onValueChange = {
                secret = it.filter { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' }.take(32)
            },
            singleLine = true,
            colors = fieldColors,
        )
        SettingsField(
            caption = "DC IP (DC:IP per line)",
            value = dcIp,
            onValueChange = { dcIp = it },
            singleLine = false,
            minLines = 2,
            colors = fieldColors,
        )
        SettingsField(
            caption = "Own CF Worker domains",
            value = workers,
            onValueChange = { workers = it },
            colors = fieldColors,
        )
        SettingsField(
            caption = "Own CF proxy domains",
            value = userDomains,
            onValueChange = { userDomains = it },
            colors = fieldColors,
        )
        SettingsField(
            caption = "WS pool size",
            value = pool,
            onValueChange = { pool = it },
            singleLine = true,
            colors = fieldColors,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Cloudflare fallback", color = AmneziaColors.Text, modifier = Modifier.weight(1f))
            AmneziaSwitch(checked = cf, onCheckedChange = { cf = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Detailed logs", color = AmneziaColors.Text, modifier = Modifier.weight(1f))
            AmneziaSwitch(checked = verbose, onCheckedChange = { verbose = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Check APK updates", color = AmneziaColors.Text, modifier = Modifier.weight(1f))
            AmneziaSwitch(checked = checkUpdates, onCheckedChange = { checkUpdates = it })
        }
        error?.let { Text(it, color = AmneziaColors.Error) }
        AmneziaPrimaryButton(
            text = "Save",
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
        )
    }
}

@Composable
private fun SettingsField(
    caption: String,
    value: String,
    onValueChange: (String) -> Unit,
    colors: androidx.compose.material3.TextFieldColors,
    singleLine: Boolean = false,
    minLines: Int = 1,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = caption,
            style = MaterialTheme.typography.labelMedium,
            color = AmneziaColors.Muted,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            colors = colors,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
