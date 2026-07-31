package com.flowseal.tgwsproxy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flowseal.tgwsproxy.proxy.ProxyServer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTab(
    state: UiState,
    drawerExpanded: Boolean,
    onDrawerExpandedChange: (Boolean) -> Unit,
    onConnectClick: () -> Unit,
    onIncludeProxy: (Boolean) -> Unit,
    onIncludeVpn: (Boolean) -> Unit,
    onOpenTelegram: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenByeDpiSettings: () -> Unit,
    onBatteryHint: () -> Unit,
    onRefreshCf: () -> Unit,
) {
    val snap = ConnectSnapshot(
        includeProxy = state.includeProxy,
        includeVpn = state.includeVpn,
        proxyRunning = state.proxyRunning,
        vpnRunning = state.vpnRunning,
        connecting = state.connecting,
    )
    val enabled = ConnectPlanner.anyIncluded(snap)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "TgWsProxy",
                style = MaterialTheme.typography.headlineMedium,
                color = AmneziaColors.Text,
            )
            Spacer(modifier = Modifier.weight(1f))
            ConnectButton(
                visual = ConnectPlanner.visual(snap),
                enabled = enabled && !state.connecting,
                onClick = onConnectClick,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = ConnectPlanner.statusLine(snap),
                style = MaterialTheme.typography.labelMedium,
                color = AmneziaColors.Muted,
            )
            if (!enabled) {
                Text(
                    text = "Enable a service in Services below",
                    style = MaterialTheme.typography.labelMedium,
                    color = AmneziaColors.Error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            ServicesPeek(
                status = ConnectPlanner.statusLine(snap),
                onClick = { onDrawerExpandedChange(true) },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (drawerExpanded) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            LaunchedEffect(Unit) { sheetState.expand() }
            ModalBottomSheet(
                onDismissRequest = { onDrawerExpandedChange(false) },
                sheetState = sheetState,
                containerColor = AmneziaColors.Surface,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            ) {
                ServicesSheetContent(
                    state = state,
                    onIncludeProxy = onIncludeProxy,
                    onIncludeVpn = onIncludeVpn,
                    onOpenTelegram = onOpenTelegram,
                    onCopyLink = onCopyLink,
                    onOpenByeDpiSettings = onOpenByeDpiSettings,
                    onBatteryHint = onBatteryHint,
                    onRefreshCf = onRefreshCf,
                )
            }
        }
    }
}

@Composable
private fun ServicesPeek(
    status: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AmneziaColors.Surface, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(2.dp)
                .background(AmneziaColors.Border, RoundedCornerShape(1.dp)),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Services",
            style = MaterialTheme.typography.headlineLarge,
            color = AmneziaColors.Text,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = status,
            style = MaterialTheme.typography.labelMedium,
            color = AmneziaColors.Muted,
        )
    }
}

@Composable
fun ServicesSheetContent(
    state: UiState,
    onIncludeProxy: (Boolean) -> Unit,
    onIncludeVpn: (Boolean) -> Unit,
    onOpenTelegram: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenByeDpiSettings: () -> Unit,
    onBatteryHint: () -> Unit,
    onRefreshCf: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Services", style = MaterialTheme.typography.headlineMedium, color = AmneziaColors.Text)

        AmneziaCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Telegram proxy", fontWeight = FontWeight.SemiBold, color = AmneziaColors.Text)
                    Text(
                        if (state.proxyRunning) "Running" else "Stopped",
                        style = MaterialTheme.typography.labelMedium,
                        color = AmneziaColors.Muted,
                    )
                }
                Text("Include", style = MaterialTheme.typography.labelMedium, color = AmneziaColors.Muted)
                Spacer(modifier = Modifier.width(8.dp))
                AmneziaSwitch(checked = state.includeProxy, onCheckedChange = onIncludeProxy)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Port ${state.config.port} · ${ProxyServer.maskSecret(state.config.secret)}",
                style = MaterialTheme.typography.labelMedium,
                color = AmneziaColors.Muted,
            )
            Row {
                TextButton(onClick = onOpenTelegram) {
                    Text("Open Telegram", color = AmneziaColors.Accent)
                }
                TextButton(onClick = onCopyLink) {
                    Text("Copy link", color = AmneziaColors.Accent)
                }
            }
        }

        AmneziaCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("ByeDPI VPN", fontWeight = FontWeight.SemiBold, color = AmneziaColors.Text)
                    Text(
                        if (state.vpnRunning) "Running" else "Stopped",
                        style = MaterialTheme.typography.labelMedium,
                        color = AmneziaColors.Muted,
                    )
                }
                Text("Include", style = MaterialTheme.typography.labelMedium, color = AmneziaColors.Muted)
                Spacer(modifier = Modifier.width(8.dp))
                AmneziaSwitch(checked = state.includeVpn, onCheckedChange = onIncludeVpn)
            }
            Text(
                "System VPN for local DPI bypass",
                style = MaterialTheme.typography.labelMedium,
                color = AmneziaColors.Muted,
            )
            TextButton(onClick = onOpenByeDpiSettings) {
                Text("ByeDPI settings", color = AmneziaColors.Accent)
            }
        }

        AmneziaCard {
            Text("Quick actions", fontWeight = FontWeight.SemiBold, color = AmneziaColors.Text)
            TextButton(onClick = onBatteryHint) {
                Text("Battery tip", color = AmneziaColors.Accent)
            }
            TextButton(onClick = onRefreshCf) {
                Text("Update domain list", color = AmneziaColors.Accent)
            }
            Text(
                state.componentStatus ?: "Using built-in or cached domain list",
                style = MaterialTheme.typography.labelMedium,
                color = AmneziaColors.Muted,
            )
        }

        Text(
            "Connect starts only services with Include on. Stop ends all running services.",
            style = MaterialTheme.typography.labelMedium,
            color = AmneziaColors.Muted,
        )
    }
}

@Composable
fun AmneziaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = AmneziaColors.ButtonOn,
            checkedTrackColor = AmneziaColors.Accent,
            uncheckedThumbColor = AmneziaColors.Text,
            uncheckedTrackColor = AmneziaColors.Border,
        ),
    )
}
