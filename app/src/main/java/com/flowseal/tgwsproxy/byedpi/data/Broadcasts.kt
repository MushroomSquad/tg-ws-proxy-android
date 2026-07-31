package com.flowseal.tgwsproxy.byedpi.data

const val STARTED_BROADCAST = "com.flowseal.tgwsproxy.byedpi.STARTED"
const val STOPPED_BROADCAST = "com.flowseal.tgwsproxy.byedpi.STOPPED"
const val FAILED_BROADCAST = "com.flowseal.tgwsproxy.byedpi.FAILED"

const val SENDER = "sender"

enum class Sender(val senderName: String) {
    Proxy("Proxy"),
    VPN("VPN"),
}
