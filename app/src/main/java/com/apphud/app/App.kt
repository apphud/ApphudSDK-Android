package com.apphud.app

import android.app.Application
import android.util.Log
import com.apphud.sdk.Apphud
import com.apphud.sdk.ApphudUserPropertyKey

class App : Application() {

    companion object {
        lateinit var app: App
    }

    override fun onCreate() {
        super.onCreate()
        app = this

        Apphud.enableDebugLogs()
        //Apphud.setUserProperty(key = ApphudUserPropertyKey.CustomProperty("nickname"), value = "Vlad")
        Apphud.start(this, Constants.API_KEY)
    }
}