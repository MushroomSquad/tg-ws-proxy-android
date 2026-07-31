package com.flowseal.tgwsproxy.ui

enum class ConnectVisual {
    Disconnected,
    Connecting,
    Connected,
}

enum class ServiceKind {
    Proxy,
    Vpn,
}

data class ConnectSnapshot(
    val includeProxy: Boolean,
    val includeVpn: Boolean,
    val proxyRunning: Boolean,
    val vpnRunning: Boolean,
    val connecting: Boolean = false,
)

object ConnectPlanner {
    fun anyIncluded(s: ConnectSnapshot): Boolean =
        s.includeProxy || s.includeVpn

    fun anyRunning(s: ConnectSnapshot): Boolean =
        s.proxyRunning || s.vpnRunning

    fun visual(s: ConnectSnapshot): ConnectVisual = when {
        s.connecting -> ConnectVisual.Connecting
        anyRunning(s) -> ConnectVisual.Connected
        else -> ConnectVisual.Disconnected
    }

    fun statusLine(s: ConnectSnapshot): String = when {
        !s.proxyRunning && !s.vpnRunning -> "All off"
        s.proxyRunning && s.vpnRunning -> "Both on"
        s.proxyRunning && !s.vpnRunning -> "Proxy on · VPN off"
        else -> "Proxy off · VPN on"
    }

    fun servicesToStart(s: ConnectSnapshot): Set<ServiceKind> = buildSet {
        if (s.includeProxy && !s.proxyRunning) add(ServiceKind.Proxy)
        if (s.includeVpn && !s.vpnRunning) add(ServiceKind.Vpn)
    }

    fun servicesToStop(s: ConnectSnapshot): Set<ServiceKind> = buildSet {
        if (s.proxyRunning) add(ServiceKind.Proxy)
        if (s.vpnRunning) add(ServiceKind.Vpn)
    }
}
