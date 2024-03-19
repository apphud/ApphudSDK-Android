package com.apphud.demo.mi

import android.app.Application
import android.content.Context

class ApphudApplication : Application() {
    var API_KEY = "app_oBcXz2z9j8spKPL2T7sZwQaQN5Jzme"
    //var API_KEY = "app_4sY9cLggXpMDDQMmvc5wXUPGReMp8G"

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
