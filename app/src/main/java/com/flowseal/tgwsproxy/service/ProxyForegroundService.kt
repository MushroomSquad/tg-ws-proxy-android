package com.flowseal.tgwsproxy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.flowseal.tgwsproxy.MainActivity
import com.flowseal.tgwsproxy.R
import com.flowseal.tgwsproxy.data.ConfigRepository
import com.flowseal.tgwsproxy.proxy.ProxyServer
import com.flowseal.tgwsproxy.util.AppLog
import com.flowseal.tgwsproxy.util.ComponentUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
class ProxyForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var server: ProxyServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        createChannel()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopProxy()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startProxy()
        }
        return START_STICKY
    }

    private fun startProxy() {
        if (server?.isRunning() == true) {
            AppLog.i("Proxy already running")
            publishState(true)
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val repo = ConfigRepository(applicationContext)
                val config = repo.ensureSecret()
                AppLog.verbose = config.verbose
                val cached = ComponentUpdater.loadCachedDomains(applicationContext)
                val notification = buildNotification(config.port)
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                val srv = ProxyServer(
                    config,
                    log = { msg -> routeProxyLog(msg, config.verbose) },
                    cachedCfDomains = cached,
                )
                server = srv
                srv.start()
                publishState(true)
                AppLog.i("Foreground service started on ${config.host}:${config.port}")
                AppLog.i("Connect with THIS app's link (secret ${ProxyServer.maskSecret(config.secret)})")
            } catch (e: Exception) {
                AppLog.e("Failed to start proxy", e)
                publishState(false)
                stopSelf()
            }
        }
    }

    private fun stopProxy() {
        runCatching { server?.stop() }
        server = null
        publishState(false)
        AppLog.i("Foreground service stopped")
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopProxy()
        scope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun createChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        mgr.createNotificationChannel(channel)
    }

    private fun routeProxyLog(msg: String, verbose: Boolean) {
        if (verbose) {
            AppLog.i(msg)
            return
        }
        // Keep post-handshake diagnostics visible without Verbose; demote only noisy chatter.
        val important =
            msg.contains("bad handshake") ||
                msg.contains("handshake ok") ||
                msg.contains("Listening") ||
                msg.contains("Proxy stopped") ||
                msg.contains("CF proxy domain") ||
                msg.contains("no fallback") ||
                msg.contains("blacklisted") ||
                msg.contains("Failed") ||
                msg.contains("failed") ||
                msg.contains("Connect:") ||
                msg.contains("Secret:") ||
                msg.contains("session closed") ||
                msg.contains("wss://") ||
                msg.contains("fallback") ||
                msg.contains("pool hit") ||
                msg.contains("Crypto self-test") ||
                msg.contains("TCP bridge") ||
                msg.startsWith("=") ||
                msg.startsWith("  Telegram") ||
                msg.startsWith("  Listening") ||
                msg.startsWith("  Target") ||
                msg.startsWith("  CF ") ||
                msg.startsWith("    DC") ||
                msg.startsWith("  If bad handshake")
        if (important) AppLog.i(msg) else AppLog.d(msg)
    }

    private fun buildNotification(port: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, ProxyForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, port))
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.flowseal.tgwsproxy.STOP"
        private const val CHANNEL_ID = "proxy"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        private var instance: ProxyForegroundService? = null

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private fun publishState(value: Boolean) {
            _running.value = value
        }

        fun start(context: Context) {
            val intent = Intent(context, ProxyForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ProxyForegroundService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }

        fun isRunning(): Boolean = instance?.server?.isRunning() == true || _running.value

        fun refreshComponents() {
            val srv = instance?.server
            if (srv?.isRunning() == true) {
                srv.refreshCfDomains()
            }
        }
    }
}