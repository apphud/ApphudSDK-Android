package com.apphud.demo

import android.app.Application
import android.content.Context
import com.apphud.sdk.Apphud
import com.apphud.sdk.client.ApiClient

class ApphudApplication : Application() {
    var API_KEY = "app_4sY9cLggXpMDDQMmvc5wXUPGReMp8G"

    companion object {
        private lateinit var instance: ApphudApplication

        fun applicationContext(): Context {
            return instance.applicationContext
        }

        fun application(): Application {
            return instance
        }
    }

    init {
        instance = this
    }

    override fun onCreate() {
        super.onCreate()

        Apphud.enableDebugLogs()
        // Apphud.optOutOfTracking()

        if (BuildConfig.DEBUG) {
//            ApphudUtils.enableAllLogs()
            ApiClient.host = "https://api.apphudstage.com"
            this.API_KEY = "app_oBcXz2z9j8spKPL2T7sZwQaQN5Jzme"
        }

        Apphud.start(this, API_KEY)
        Apphud.collectDeviceIdentifiers()
    }
}
