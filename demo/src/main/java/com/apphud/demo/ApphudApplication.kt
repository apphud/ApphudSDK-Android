package com.apphud.demo

import android.app.Application
import android.content.Context
import android.util.Log
import com.apphud.sdk.Apphud
import com.apphud.sdk.ApphudListener
import com.apphud.sdk.ApphudUtils
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudPlacement
import com.apphud.sdk.domain.ApphudSubscription
import com.apphud.sdk.domain.ApphudUser
import com.xiaomi.billingclient.api.SkuDetails

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
            ApphudUtils.enableAllLogs()
        }

        Apphud.start(this, API_KEY)
        Apphud.collectDeviceIdentifiers()
    }
}
