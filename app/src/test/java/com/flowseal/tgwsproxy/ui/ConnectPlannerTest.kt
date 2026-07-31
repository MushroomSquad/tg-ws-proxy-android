package com.flowseal.tgwsproxy.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectPlannerTest {
    @Test
    fun bothIncludeOff_connectDisabled() {
        val s = ConnectSnapshot(false, false, false, false)
        assertFalse(ConnectPlanner.anyIncluded(s))
        assertEquals(ConnectVisual.Disconnected, ConnectPlanner.visual(s))
        assertTrue(ConnectPlanner.servicesToStart(s).isEmpty())
    }

    @Test
    fun startOnlyIncludedNotRunning() {
        val s = ConnectSnapshot(
            includeProxy = true,
            includeVpn = true,
            proxyRunning = true,
            vpnRunning = false,
        )
        assertEquals(setOf(ServiceKind.Vpn), ConnectPlanner.servicesToStart(s))
    }

    @Test
    fun stopStopsAllRunningRegardlessOfInclude() {
        val s = ConnectSnapshot(
            includeProxy = false,
            includeVpn = true,
            proxyRunning = true,
            vpnRunning = true,
        )
        assertEquals(
            setOf(ServiceKind.Proxy, ServiceKind.Vpn),
            ConnectPlanner.servicesToStop(s),
        )
    }

    @Test
    fun statusLineFormats() {
        assertEquals(
            "All off",
            ConnectPlanner.statusLine(ConnectSnapshot(true, true, false, false)),
        )
        assertEquals(
            "Both on",
            ConnectPlanner.statusLine(ConnectSnapshot(true, true, true, true)),
        )
        assertEquals(
            "Proxy on · VPN off",
            ConnectPlanner.statusLine(ConnectSnapshot(true, true, true, false)),
        )
    }

    @Test
    fun connectingVisual() {
        val s = ConnectSnapshot(true, true, false, false, connecting = true)
        assertEquals(ConnectVisual.Connecting, ConnectPlanner.visual(s))
    }
}
