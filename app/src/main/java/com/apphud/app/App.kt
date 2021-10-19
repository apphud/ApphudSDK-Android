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
            error?.let{
                Log.d("Apphud", "PAYWALLS LOADING 1a FAILED")
            }
            paywalls?.let{
                Log.d("Apphud", "PAYWALLS LOADED 1a")
            }
        }
        Apphud.getPaywalls { paywalls, error ->
            error?.let{
                Log.d("Apphud", "PAYWALLS LOADING 2a FAILED")
            }
            paywalls?.let{
                Log.d("Apphud", "PAYWALLS LOADED 2a")
            }
        }
        Apphud.getPaywalls { paywalls, error ->
            error?.let{
                Log.d("Apphud", "PAYWALLS LOADING 3a FAILED")
            }
            paywalls?.let{
                Log.d("Apphud", "PAYWALLS LOADED 3a")
            }
        }
    }
}