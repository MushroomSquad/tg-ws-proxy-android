package com.flowseal.tgwsproxy.byedpi.services

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.flowseal.tgwsproxy.MainActivity
import com.flowseal.tgwsproxy.R
import com.flowseal.tgwsproxy.byedpi.core.ByeDpiProxy
import com.flowseal.tgwsproxy.byedpi.core.ByeDpiProxyPreferences
import com.flowseal.tgwsproxy.byedpi.core.TProxyService
import com.flowseal.tgwsproxy.byedpi.core.VpnProtector
import com.flowseal.tgwsproxy.byedpi.data.AppStatus
import com.flowseal.tgwsproxy.byedpi.data.FAILED_BROADCAST
import com.flowseal.tgwsproxy.byedpi.data.Mode
import com.flowseal.tgwsproxy.byedpi.data.SENDER
import com.flowseal.tgwsproxy.byedpi.data.START_ACTION
import com.flowseal.tgwsproxy.byedpi.data.STARTED_BROADCAST
import com.flowseal.tgwsproxy.byedpi.data.STOP_ACTION
import com.flowseal.tgwsproxy.byedpi.data.STOPPED_BROADCAST
import com.flowseal.tgwsproxy.byedpi.data.Sender
import com.flowseal.tgwsproxy.byedpi.data.ServiceStatus
import com.flowseal.tgwsproxy.byedpi.utility.createConnectionNotification
import com.flowseal.tgwsproxy.byedpi.utility.getPreferences
import com.flowseal.tgwsproxy.byedpi.utility.getStringNotNull
import com.flowseal.tgwsproxy.byedpi.utility.registerNotificationChannel
import com.flowseal.tgwsproxy.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class ByeDpiVpnService : LifecycleVpnService() {
    private val byeDpiProxy = ByeDpiProxy()
    private var proxyJob: Job? = null
    private var tunFd: ParcelFileDescriptor? = null
    private val mutex = Mutex()
    private var stopping: Boolean = false

    companion object {
        private val TAG: String = ByeDpiVpnService::class.java.simpleName
        private const val FOREGROUND_SERVICE_ID: Int = 2001
        private const val NOTIFICATION_CHANNEL_ID: String = "ByeDPIVpn"

        @Volatile
        private var status: ServiceStatus = ServiceStatus.Disconnected

        fun isConnected(): Boolean = status == ServiceStatus.Connected
    }

    override fun onCreate() {
        super.onCreate()
        registerNotificationChannel(
            this,
            NOTIFICATION_CHANNEL_ID,
            R.string.byedpi_vpn_channel_name,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return when (intent?.action) {
            START_ACTION -> {
                lifecycleScope.launch { start() }
                START_STICKY
            }
            STOP_ACTION -> {
                lifecycleScope.launch { stop() }
                START_NOT_STICKY
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent?.action}")
                START_NOT_STICKY
            }
        }
    }

    override fun onRevoke() {
        Log.i(TAG, "VPN revoked")
        AppLog.i("ByeDPI VPN revoked by system")
        lifecycleScope.launch { stop() }
    }

    private suspend fun start() {
        Log.i(TAG, "Starting")
        if (status == ServiceStatus.Connected) {
            Log.w(TAG, "VPN already connected")
            return
        }
        try {
            mutex.withLock {
                VpnProtector.attach(this)
                startProxy()
                startTun2Socks()
            }
            updateStatus(ServiceStatus.Connected)
            startForegroundNotification()
            AppLog.i("ByeDPI VPN started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            AppLog.e("ByeDPI VPN failed to start", e)
            updateStatus(ServiceStatus.Failed)
            stop()
        }
    }

    private fun startForegroundNotification() {
        val notification: Notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                FOREGROUND_SERVICE_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(FOREGROUND_SERVICE_ID, notification)
        }
    }

    private suspend fun stop() {
        Log.i(TAG, "Stopping")
        mutex.withLock {
            stopping = true
            try {
                stopTun2Socks()
                stopProxy()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop VPN", e)
                AppLog.e("ByeDPI VPN stop error", e)
            } finally {
                VpnProtector.detach()
                stopping = false
            }
        }
        updateStatus(ServiceStatus.Disconnected)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        AppLog.i("ByeDPI VPN stopped")
    }

    private suspend fun startProxy() {
        if (proxyJob != null) {
            throw IllegalStateException("Proxy already running")
        }
        val preferences = ByeDpiProxyPreferences.fromSharedPreferences(getPreferences())
        proxyJob = lifecycleScope.launch(Dispatchers.IO) {
            val code = byeDpiProxy.startProxy(preferences)
            withContext(Dispatchers.Main) {
                if (code != 0) {
                    Log.e(TAG, "Proxy stopped with code $code")
                    AppLog.e("ByeDPI proxy exited with code $code")
                    updateStatus(ServiceStatus.Failed)
                } else if (!stopping) {
                    stop()
                    updateStatus(ServiceStatus.Disconnected)
                }
            }
        }
    }

    private suspend fun stopProxy() {
        if (status == ServiceStatus.Disconnected) {
            return
        }
        runCatching { byeDpiProxy.stopProxy() }
        proxyJob?.join()
        proxyJob = null
    }

    private fun startTun2Socks() {
        if (tunFd != null) {
            throw IllegalStateException("VPN already established")
        }
        val sharedPreferences = getPreferences()
        val port = sharedPreferences.getString("byedpi_proxy_port", null)?.toInt() ?: 1080
        val dns = sharedPreferences.getStringNotNull("dns_ip", "1.1.1.1")
        val ipv6 = sharedPreferences.getBoolean("ipv6_enable", false)

        val tun2socksConfig = """
            |misc:
            |  task-stack-size: 81920
            |socks5:
            |  mtu: 8500
            |  address: 127.0.0.1
            |  port: $port
            |  udp: udp
        """.trimMargin()

        val configPath = File.createTempFile("byedpi_tun", ".yml", cacheDir).apply {
            writeText(tun2socksConfig)
        }

        val fd = createBuilder(dns, ipv6).establish()
            ?: throw IllegalStateException("VPN connection failed")
        this.tunFd = fd
        TProxyService.TProxyStartService(configPath.absolutePath, fd.fd)
    }

    private fun stopTun2Socks() {
        runCatching { TProxyService.TProxyStopService() }
        tunFd?.close()
        tunFd = null
    }

    private fun updateStatus(newStatus: ServiceStatus) {
        status = newStatus
        setStatus(
            when (newStatus) {
                ServiceStatus.Connected -> AppStatus.Running
                ServiceStatus.Disconnected, ServiceStatus.Failed -> {
                    proxyJob = null
                    AppStatus.Halted
                }
            },
            Mode.VPN,
        )
        val intent = Intent(
            when (newStatus) {
                ServiceStatus.Connected -> STARTED_BROADCAST
                ServiceStatus.Disconnected -> STOPPED_BROADCAST
                ServiceStatus.Failed -> FAILED_BROADCAST
            },
        )
        intent.putExtra(SENDER, Sender.VPN.ordinal)
        sendBroadcast(intent)
    }

    private fun createNotification(): Notification =
        createConnectionNotification(
            this,
            NOTIFICATION_CHANNEL_ID,
            R.string.byedpi_notification_title,
            R.string.byedpi_vpn_notification_content,
            ByeDpiVpnService::class.java,
        )

    private fun createBuilder(dns: String, ipv6: Boolean): Builder {
        val builder = Builder()
        builder.setSession("ByeDPI")
        builder.setConfigureIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        builder.addAddress("10.10.10.10", 32)
            .addRoute("0.0.0.0", 0)
        if (ipv6) {
            builder.addAddress("fd00::1", 128)
                .addRoute("::", 0)
        }
        if (dns.isNotBlank()) {
            builder.addDnsServer(dns)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }
        // Do NOT call addDisallowedApplication(packageName): TgWsProxy outbound
        // must enter the TUN so Telegram MTProto gets DPI bypass. ByeDPI sockets
        // are protected via VpnProtector / vpn_protect_fd instead.
        return builder
    }
}
