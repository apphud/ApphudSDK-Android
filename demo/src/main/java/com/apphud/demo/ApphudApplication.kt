package com.apphud.demo

import android.app.Application
import android.content.Context
import com.apphud.sdk.Apphud

class ApphudApplication : Application() {

    val API_KEY = "app_4sY9cLggXpMDDQMmvc5wXUPGReMp8G"

    companion object {
        private lateinit var instance: ApphudApplication

        fun applicationContext() : Context {
            return instance.applicationContext
        }
    }

    init {
        instance = this
    }

    override fun onCreate() {
        super.onCreate()

        Apphud.enableDebugLogs()
//        Apphud.optOutOfTracking()
        Apphud.start(this, API_KEY)
        Apphud.collectDeviceIdentifiers()
    }
}