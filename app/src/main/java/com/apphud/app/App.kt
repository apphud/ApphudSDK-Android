package com.apphud.app

import android.app.Application
import android.util.Log
import com.apphud.sdk.Apphud

class App : Application() {

    companion object {
        lateinit var app: App
    }

    override fun onCreate() {
        super.onCreate()
        app = this

        Apphud.enableDebugLogs()
        Apphud.start(this, Constants.API_KEY)
        Apphud.getPaywalls { paywalls, error ->
            print("paywalls finished fetching")
        }
    }
}