package com.apphud.sampleapp

import android.app.Application
import android.content.Context
import com.apphud.sampleapp.ui.utils.PreferencesManager
import com.apphud.sampleapp.ui.utils.ResourceManager

class ColorGeneratorApplication : Application() {
    var API_KEY = "app_4sY9cLggXpMDDQMmvc5wXUPGReMp8G"

    companion object {
        private lateinit var instance: ColorGeneratorApplication
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

        ResourceManager.applicationContext = applicationContext
        PreferencesManager.init(applicationContext)

        /*if (BuildConfig.DEBUG) {
            Apphud.enableDebugLogs()
        }
        Apphud.start(this, API_KEY)
        Apphud.collectDeviceIdentifiers()*/
    }
}