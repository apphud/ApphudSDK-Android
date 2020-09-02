package ru.rosbank.mbdg.myapplication

import android.app.Application
import com.apphud.sdk.client.ApiClient

class App : Application() {

    companion object {
        lateinit var app: App
    }

    override fun onCreate() {
        super.onCreate()
        app = this

        com.apphud.sdk.ApphudSdk.init(this, com.apphud.sdk.client.ApiClient.API_KEY)
        com.apphud.sdk.ApphudSdk.enableDebugLogs()
    }
}