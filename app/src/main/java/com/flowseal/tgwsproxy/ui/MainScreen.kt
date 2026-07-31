package com.flowseal.tgwsproxy.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flowseal.tgwsproxy.proxy.ProxyConfig

@Composable
fun MainScreen(
    state: UiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onIncludeProxy: (Boolean) -> Unit,
    onIncludeVpn: (Boolean) -> Unit,
    onOpenByeDpiSettings: () -> Unit,
    onCopyLink: () -> Unit,
    onOpenTelegram: () -> Unit,
    onSaveSettings: (ProxyConfig) -> Unit,
    onClearLogs: () -> Unit,
    onExportLogs: () -> Unit = {},
    onShareLogs: () -> Unit = {},
    onDismissFirstRun: () -> Unit,
    onShowFirstRun: () -> Unit = {},
    onBatteryHint: () -> Unit,
    onDismissUpdate: () -> Unit = {},
    onOpenUpdate: (String) -> Unit = {},
    onRefreshComponents: () -> Unit = {},
) {
    var tab by remember { mutableStateOf(AppTab.Home) }
    var drawerExpanded by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var forceFirstRun by remember { mutableStateOf(false) }

    val showFirstRunDialog = state.firstRun || forceFirstRun

    if (showFirstRunDialog) {
        AlertDialog(
            onDismissRequest = {
                forceFirstRun = false
                onDismissFirstRun()
            },
            containerColor = AmneziaColors.Surface,
            titleContentColor = AmneziaColors.Text,
            textContentColor = AmneziaColors.Text,
            title = { Text("How to connect") },
            text = {
                Text(
                    "1. In Services, leave Include on for Proxy and/or VPN\n" +
                        "2. Tap CONNECT on Home\n" +
                        "3. Open Telegram or Copy link, enable the local MTProto proxy\n\n" +
                        "Server: 127.0.0.1  Port: ${state.config.port}\n" +
                        "Stop ends all running services.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        forceFirstRun = false
                        onDismissFirstRun()
                    },
                ) {
                    Text("Got it", color = AmneziaColors.Accent)
                }
            },
        )
    }

    state.updateInfo?.let { upd ->
        if (upd.apkUrl != null) {
            AlertDialog(
                onDismissRequest = onDismissUpdate,
                containerColor = AmneziaColors.Surface,
                titleContentColor = AmneziaColors.Text,
                textContentColor = AmneziaColors.Text,
                title = { Text("APK update available") },
                text = {
                    Text("New version: ${upd.latestVersion}\nAPK: ${upd.apkName ?: "app-release.apk"}")
                },
                confirmButton = {
                    TextButton(onClick = { onOpenUpdate(upd.apkUrl) }) {
                        Text("Download APK", color = AmneziaColors.ButtonOn)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissUpdate) {
                        Text("Later", color = AmneziaColors.Muted)
                    }
                },
            )
        }
    }

    BackHandler(enabled = drawerExpanded || tab != AppTab.Home) {
        when {
            drawerExpanded -> drawerExpanded = false
            tab != AppTab.Home -> tab = AppTab.Home
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmneziaColors.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
        ) {
            when (tab) {
                AppTab.Home -> HomeTab(
                    state = state,
                    drawerExpanded = drawerExpanded,
                    onDrawerExpandedChange = { drawerExpanded = it },
                    onConnectClick = {
                        val snap = ConnectSnapshot(
                            state.includeProxy,
                            state.includeVpn,
                            state.proxyRunning,
                            state.vpnRunning,
                            state.connecting,
                        )
                        if (ConnectPlanner.anyRunning(snap)) onDisconnect() else onConnect()
                    },
                    onIncludeProxy = onIncludeProxy,
                    onIncludeVpn = onIncludeVpn,
                    onOpenTelegram = onOpenTelegram,
                    onCopyLink = onCopyLink,
                    onOpenByeDpiSettings = onOpenByeDpiSettings,
                    onBatteryHint = onBatteryHint,
                    onRefreshCf = onRefreshComponents,
                )
                AppTab.Settings -> SettingsTab(
                    state = state,
                    onSave = onSaveSettings,
                    onRefreshCf = onRefreshComponents,
                    onBatteryHint = onBatteryHint,
                    onShowFirstRun = {
                        forceFirstRun = true
                        onShowFirstRun()
                    },
                    onOpenByeDpiSettings = onOpenByeDpiSettings,
                    onOpenUpdate = onOpenUpdate,
                )
                AppTab.Logs -> LogsTab(
                    state = state,
                    showLogs = showLogs,
                    onShowLogsChange = { showLogs = it },
                    onClear = onClearLogs,
                    onExport = onExportLogs,
                    onShare = onShareLogs,
                )
            }
        }
        AmneziaTabBar(
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}
