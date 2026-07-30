package com.flowseal.tgwsproxy

import android.app.Application
import com.flowseal.tgwsproxy.util.AppLog

class TgWsProxyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
    }
}
