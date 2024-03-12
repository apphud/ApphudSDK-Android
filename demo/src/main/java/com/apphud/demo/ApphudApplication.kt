package com.apphud.demo

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.apphud.sdk.Apphud
import com.apphud.sdk.ApphudUtils
import com.apphud.sdk.client.ApiClient
import com.apphud.sdk.domain.ApphudPlacement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ApphudApplication : Application() {
    var API_KEY = "app_4sY9cLggXpMDDQMmvc5wXUPGReMp8G"

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

    private val applicationScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            ApphudUtils.enableAllLogs()
        }
        Apphud.start(this, API_KEY)
        Apphud.collectDeviceIdentifiers()
    }
}
