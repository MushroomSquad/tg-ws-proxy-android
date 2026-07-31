package com.flowseal.tgwsproxy.byedpi.core

import android.net.VpnService

/**
 * Bridges [VpnService.protect] into the ByeDPI native layer so SOCKS outbound
 * sockets bypass the TUN, while TgWsProxy sockets still go through the VPN.
 */
object VpnProtector {
    init {
        System.loadLibrary("byedpi")
    }

    fun attach(service: VpnService) {
        nativeSetVpnService(service)
    }

    fun detach() {
        nativeClearVpnService()
    }

    @JvmStatic
    private external fun nativeSetVpnService(vpnService: VpnService?)

    @JvmStatic
    private external fun nativeClearVpnService()
}
