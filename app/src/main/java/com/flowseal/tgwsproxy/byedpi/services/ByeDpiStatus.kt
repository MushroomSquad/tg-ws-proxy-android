package com.flowseal.tgwsproxy.byedpi.services

import com.flowseal.tgwsproxy.byedpi.data.AppStatus
import com.flowseal.tgwsproxy.byedpi.data.Mode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val _running = MutableStateFlow(false)
val byeDpiRunning: StateFlow<Boolean> = _running.asStateFlow()

var appStatus = AppStatus.Halted to Mode.VPN
    private set

fun setStatus(status: AppStatus, mode: Mode) {
    appStatus = status to mode
    _running.value = status == AppStatus.Running
}
