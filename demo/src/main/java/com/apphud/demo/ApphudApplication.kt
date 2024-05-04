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
            ApphudUtils.enableDebugLogs()
        }
//        Apphud.invalidatePaywallsCache()
        Apphud.start(this, API_KEY, observerMode = false)
        Apphud.collectDeviceIdentifiers()

        fetchPlacements()
    }

    fun fetchPlacements() {
        Log.d("ApphudLogsDemo", "Fetching Placements Started")
        Apphud.fetchPlacements { apphudPlacements, apphudError ->

            val hasInternet = ApphudUtils.hasInternetConnection(this)
            Log.d("ApphudLogsDemo", "Internet connected: $hasInternet")

            if (apphudPlacements.isNotEmpty() && apphudError == null) {
                Log.d("ApphudLogsDemo", "Placements are loaded, all good.")
            } else if (apphudError?.billingErrorTitle() != null) {
                Log.d("ApphudLogsDemo", "Placements are loaded, however there is Google Billing Issue (${apphudError.billingErrorTitle()}): ask user to sign in to Google Play and try again later.")
                // Developer can retry fetchPlacements() immediately or after user taps "Try again" button in your custom UI.
                fetchPlacements()
            } else if (apphudError?.networkIssue() == true) {
                Log.d("ApphudLogsDemo", "Failed to load placements due to Internet connection issue, ask user to connect to the Internet and try again later.")
                // Developer can retry fetchPlacements() immediately or after user taps "Try again" button in your custom UI.
                fetchPlacements()
            } else {
                // unknown or server-side error, try to load fallback paywalls
                Apphud.loadFallbackPaywalls { paywalls, fallbackError ->
                    if (!paywalls.isNullOrEmpty() && fallbackError?.billingErrorTitle() == null) {
                        Log.d("ApphudLogsDemo", "Fallback paywalls are loaded from JSON, use them instead of placements")
                        // Grab the paywall and display it
                    } else if (fallbackError?.billingErrorTitle() != null) {
                        Log.d("ApphudLogsDemo", "Fallback paywalls are loaded, however there is Google Billing Issue (${fallbackError.billingErrorTitle()}): ask user to sign in to Google Play and try again later.")
                        // Developer can retry fetchPlacements() immediately or after user taps "Try again" button in your custom UI.
                        fetchPlacements()
                    } else {
                        Log.d("ApphudLogsDemo", "Fallback paywalls JSON is missing or invalid.")
                        // Developer can retry fetchPlacements() immediately or after user taps "Try again" button in your custom UI.
                        fetchPlacements()
                    }
                }
            }
        }
    }
}
