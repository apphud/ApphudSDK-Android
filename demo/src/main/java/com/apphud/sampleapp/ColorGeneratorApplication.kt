package com.apphud.sampleapp

import android.app.Application
import com.apphud.sampleapp.ui.utils.ApphudSdkManager
import com.apphud.sampleapp.ui.utils.PreferencesManager
import com.apphud.sampleapp.ui.utils.ResourceManager

class ColorGeneratorApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        ApphudSdkManager.start(this)

        ResourceManager.applicationContext = applicationContext
        PreferencesManager.init(applicationContext)
    }
}