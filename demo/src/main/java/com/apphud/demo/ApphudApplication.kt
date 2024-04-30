package com.apphud.demo

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.apphud.sdk.Apphud
import com.apphud.sdk.ApphudError
import com.apphud.sdk.ApphudUtils
import com.apphud.sdk.client.ApiClient
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudPlacement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
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
        Apphud.start(this, API_KEY, observerMode = false)
        Apphud.collectDeviceIdentifiers()

        Apphud.fetchPlacements { placements, error ->
            if (placements.isNotEmpty()) {
                val paywall = placements.find { it.identifier == "main" }?.paywall
                paywall?.let { setupPaywall(it) }
            } else {
                loadFromFallbackOrError(error)
            }
        }
    }

    fun loadFromFallbackOrError(error: ApphudError?) {
        Apphud.loadFallbackPaywalls { pwls, _ ->
            if (!pwls.isNullOrEmpty()) {
                val paywall = pwls.find { it.identifier == "main" }
                paywall?.let { setupPaywall(it) }
            } else {
                // failed to load placements, and load fallback paywalls. See `error`
            }
        }
    }

    fun setupPaywall(paywall: ApphudPaywall) {

    }
}
