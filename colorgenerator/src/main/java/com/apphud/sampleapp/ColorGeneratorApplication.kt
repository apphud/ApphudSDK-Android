package com.apphud.sampleapp

import android.app.Application
import com.apphud.sampleapp.ui.utils.PreferencesManager
import com.apphud.sampleapp.ui.utils.PurchaseManager
import com.apphud.sampleapp.ui.utils.ResourceManager

class ColorGeneratorApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        PurchaseManager.start(this)

        ResourceManager.applicationContext = applicationContext
        PreferencesManager.init(applicationContext)
    }
}