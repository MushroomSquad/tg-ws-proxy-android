package com.flowseal.tgwsproxy

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flowseal.tgwsproxy.byedpi.services.ServiceManager
import com.flowseal.tgwsproxy.byedpi.ui.ByeDpiSettingsActivity
import com.flowseal.tgwsproxy.service.ProxyForegroundService
import com.flowseal.tgwsproxy.ui.AppViewModel
import com.flowseal.tgwsproxy.ui.MainScreen
import com.flowseal.tgwsproxy.ui.TgWsTheme
import com.flowseal.tgwsproxy.util.AppLog
import java.io.File

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* no-op */ }

    private var pendingLogSave: File? = null

    private val saveLogLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val src = pendingLogSave
        pendingLogSave = null
        if (uri == null || src == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { input -> input.copyTo(out) }
            } ?: throw IllegalStateException("no output stream")
            Toast.makeText(this, "Logs saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            ServiceManager.startVpn(this)
        } else {
            Toast.makeText(this, getString(R.string.vpn_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()

        setContent {
            TgWsTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val vm: AppViewModel = viewModel(factory = AppViewModel.factory(application))
                    val state by vm.state.collectAsState()
                    MainScreen(
                        state = state,
                        onStartProxy = {
                            ProxyForegroundService.start(this)
                            vm.refresh()
                        },
                        onStopProxy = {
                            ProxyForegroundService.stop(this)
                            vm.refresh()
                        },
                        onStartVpn = { requestVpnAndStart(vm) },
                        onStopVpn = {
                            ServiceManager.stopVpn(this)
                            vm.refresh()
                        },
                        onOpenByeDpiSettings = {
                            startActivity(Intent(this, ByeDpiSettingsActivity::class.java))
                        },
                        onCopyLink = {
                            vm.copyLink(this)
                            Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show()
                        },
                        onOpenTelegram = {
                            val link = state.config.proxyLink()
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, link.toUri()))
                            } catch (_: Exception) {
                                Toast.makeText(this, "Telegram not found", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSaveSettings = { cfg ->
                            vm.saveConfig(cfg)
                            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
                        },
                        onClearLogs = { vm.clearLogs() },
                        onExportLogs = { startSaveLogs() },
                        onShareLogs = {
                            if (!vm.shareLogs(this)) {
                                Toast.makeText(this, "No logs to share", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDismissFirstRun = { vm.dismissFirstRun() },
                        onBatteryHint = { openBatterySettings() },
                        onDismissUpdate = { vm.dismissUpdate() },
                        onOpenUpdate = { url ->
                            try {
                                startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                            } catch (_: Exception) {
                                Toast.makeText(this, "Cannot open link", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onRefreshComponents = {
                            vm.refreshComponentsNow()
                            Toast.makeText(this, "Updating CF domain list…", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }
    }

    private fun requestVpnAndStart(vm: AppViewModel) {
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            vpnPermissionLauncher.launch(prepare)
        } else {
            ServiceManager.startVpn(this)
            vm.refresh()
        }
    }

    private fun startSaveLogs() {
        val file = AppLog.snapshotForExport(this)
        if (file == null) {
            Toast.makeText(this, "No logs to save", Toast.LENGTH_SHORT).show()
            return
        }
        pendingLogSave = file
        saveLogLauncher.launch(file.name)
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun openBatterySettings() {
        try {
            val pkg = packageName
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(pkg)) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$pkg")
                    },
                )
            } else {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
