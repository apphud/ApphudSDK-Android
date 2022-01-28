package com.apphud.app

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import com.apphud.sdk.Apphud

class ApphudApplication : Application() {

    companion object {
        private lateinit var instance: ApphudApplication

        fun applicationContext() : Context {
            return instance.applicationContext
        }

        fun application() : Application {
            return instance
        }
    }

    init {
        instance = this
    }

    override fun onCreate() {
        super.onCreate()
    }
}