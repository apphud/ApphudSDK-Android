package com.apphud.demo.mi

import android.app.Application
import android.content.Context

class ApphudApplication : Application() {

    companion object {
        private lateinit var instance: ApphudApplication

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
    }
}
