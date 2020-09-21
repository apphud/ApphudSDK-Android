package ru.rosbank.mbdg.myapplication

import android.app.Application
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
    }
}