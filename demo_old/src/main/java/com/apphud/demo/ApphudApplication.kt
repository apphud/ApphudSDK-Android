package com.apphud.demo

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.apphud.sdk.Apphud
import com.apphud.sdk.ApphudError
import com.apphud.sdk.ApphudUserProperty
import com.apphud.sdk.ApphudUserPropertyKey
import com.apphud.sdk.ApphudUtils
import com.apphud.sdk.client.ApiClient
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudPlacement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ApphudApplication : Application() {
    var API_KEY = "app_4sY9cLggXpMDDQMmvc5wXUPGReMp8G"

    companion object {
        private lateinit var instance: ApphudApplication

        fun applicationContext(): Context {
            return instance.applicationContext
        }

        fun application(): ApphudApplication {
            return instance
        }
    }

    init {
        instance = this
    }

    private val applicationScope = CoroutineScope(Dispatchers.Default)

    var attempt = 0

    var observerMode = false

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            ApphudUtils.enableDebugLogs()
        }
//        ApiClient.host = "https://gitlab.apphud.com"

        Apphud.start(this, API_KEY, observerMode = observerMode)
//        Apphud.invalidatePaywallsCache()
        Apphud.deferPlacements()
        Apphud.collectDeviceIdentifiers()
//        Apphud.refreshUserData()
//        testPlacements()
        Apphud.setUserProperty(ApphudUserPropertyKey.CustomProperty("custom_prop_5"), "ren6")
        Apphud.setUserProperty(ApphudUserPropertyKey.CustomProperty("custom_prop_1"), "ren7")
//        Apphud.refreshUserData()
        Apphud.forceFlushUserProperties { result ->
//            Apphud.refreshUserData()
//            Log.e("ApphudLogs", "User Properties Flushed")
            testPlacements()
//            Apphud.refreshUserData { u ->
//                Log.d("ApphudLogs", "refreshed user data")
//            }
            Apphud.paywallsDidLoadCallback { pay, err ->
                Log.d("ApphudLogs", "Fetched paywalls paywallsDidLoadCallback")
            }
        }


//        Apphud.refreshUserData()
//        fetchPlacements()
    }

    fun testPlacements() {
        Apphud.fetchPlacements { pl, e ->
            Log.d("ApphudLogs", "fetchPlacements callback called")
            val weeklyProduct = Apphud.products().firstOrNull { it.productId == "com.apphud.demo.subscriptions.weekly1" }
            val placement = pl.firstOrNull { it.identifier == "android_placement" }
            val paywall = placement?.paywall
            if (paywall != null) {
                val paywallID = placement?.paywall?.identifier
                Log.d("ApphudLogs", "Fetched paywall from android_placement = $paywallID")
                val productsInPaywall = paywall.products
                    ?.flatMap { it.productDetails?.let { listOf(it) } ?: emptyList() } ?: listOf()

                //1. ALL_GOOD вызывается на ProductDetails пусто
                //2. проверить если fetchPlacements или refreshProducts или refreshEntitlements вызывается несколько раз после flushUserProperties

                if (paywallID == "multiplan1" && weeklyProduct != null && productsInPaywall.isNotEmpty()) {
                    Log.e("ApphudLogs", "ALL_GOOD")
                } else if (weeklyProduct == null) {
                    Log.e("ApphudLogs", "ALL_BAD_PRODUCTS_NULL")
                } else {
                    Log.e("ApphudLogs", "ALL_BAD")
                }
            } else if (observerMode){
                Log.e("ApphudLogs", "ALL_GOOD_OBSERVER_MODE")
            }
        }
    }

    fun fetchPlacements() {
        if (attempt > 10) {
            Log.d("ApphudLogsDemo", "Too many attempts. Try to load placements again only when paywall is going to be shown")
            return
        }
        attempt += 1

        Log.d("ApphudLogsDemo", "Fetching Placements Started")
        Apphud.fetchPlacements { apphudPlacements, apphudError ->

            val placement = apphudPlacements.firstOrNull { it.identifier == "YOUR_PLACEMENT_ID" }
            val paywall = placement?.paywall
            // work with your paywall and it's products here

            val hasInternet = ApphudUtils.hasInternetConnection(this)
            Log.d("ApphudLogsDemo", "Internet connected: $hasInternet")

            if (apphudPlacements.isNotEmpty() && apphudError == null) {
                Log.d("ApphudLogsDemo", "Placements are loaded, all good.")
                // ---->> SUCCESS HERE
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
                        // ---->> FALLBACK PAYWALLS HERE, USE PAYWALLS WITHOUT PLACEMENTS
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
