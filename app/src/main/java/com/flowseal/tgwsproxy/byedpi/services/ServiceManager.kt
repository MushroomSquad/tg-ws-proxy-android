package com.flowseal.tgwsproxy.byedpi.services

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.flowseal.tgwsproxy.byedpi.data.START_ACTION
import com.flowseal.tgwsproxy.byedpi.data.STOP_ACTION

object ServiceManager {
    private val TAG: String = ServiceManager::class.java.simpleName

    fun startVpn(context: Context) {
        Log.i(TAG, "Starting VPN")
        val intent = Intent(context, ByeDpiVpnService::class.java).setAction(START_ACTION)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopVpn(context: Context) {
        Log.i(TAG, "Stopping VPN")
        val intent = Intent(context, ByeDpiVpnService::class.java).setAction(STOP_ACTION)
        ContextCompat.startForegroundService(context, intent)
    }
}
