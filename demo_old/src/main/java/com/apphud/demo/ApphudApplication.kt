package com.apphud.demo

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import com.apphud.sdk.Apphud
import com.apphud.sdk.ApphudAttributionData
import com.apphud.sdk.ApphudAttributionProvider
import com.apphud.sdk.ApphudError
import com.apphud.sdk.ApphudRuleCallback
import com.apphud.sdk.ApphudScreenDismissAction
import com.apphud.sdk.ApphudUtils
import com.apphud.sdk.ApphudPurchaseResult
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.domain.Rule
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

internal class ApphudApplication : Application() {

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

    @Volatile
    private var currentActivity: Activity? = null

    var attempt = 0
    override fun onCreate() {
        super.onCreate()
        trackCurrentActivity()
        ApphudUtils.enableAllLogs()
        if (BuildConfig.APPHUD_BASE_URL.isNotEmpty()) {
            ApphudUtils.overrideBaseUrl(BuildConfig.APPHUD_BASE_URL)
        }
        Apphud.start(
            this,
            BuildConfig.APPHUD_API_KEY,
            observerMode = false,
            ruleCallback = ruleCallback,
            deeplinkHandler = { attribution, kind, uri ->
                Log.d("ApphudLogsDemo", "deeplinkHandler: kind=$kind, uri=$uri, attribution=$attribution")
            },
        )
        Apphud.setAttribution(ApphudAttributionData(rawData = mapOf("odm_info" to "123445")), provider = ApphudAttributionProvider.GOOGLE)
        Apphud.collectDeviceIdentifiers()
        requestAndSubmitPushToken()
        fetchPlacements()
    }

    /**
     * Fetches the current FCM registration token and submits it to Apphud. New tokens are handled
     * in [DemoFirebaseMessagingService.onNewToken].
     */
    private fun requestAndSubmitPushToken() {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.e("ApphudLogsDemo", "Fetching FCM token failed", task.exception)
                    return@addOnCompleteListener
                }
                val token = task.result
                Log.d("ApphudLogsDemo", "FCM token: $token")
                Apphud.submitPushNotificationsToken(token) { success ->
                    Log.d("ApphudLogsDemo", "submitPushNotificationsToken success=$success")
                }
            }
    }

    private fun trackCurrentActivity() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                currentActivity = activity
            }

            override fun onActivityPaused(activity: Activity) {
                if (currentActivity === activity) currentActivity = null
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private val ruleCallback = object : ApphudRuleCallback {
        override fun provideActivity(): Activity? {
            return currentActivity
        }

        override fun shouldPerformRule(rule: Rule): Boolean {
            Log.d("ApphudLogsDemo", "Rule: shouldPerformRule ${rule.ruleName}")
            return true
        }

        override fun shouldShowScreen(rule: Rule): Boolean {
            Log.d("ApphudLogsDemo", "Rule: shouldShowScreen ${rule.ruleName}")
            return true
        }

        override fun onScreenAppeared(rule: Rule) {
            Log.d("ApphudLogsDemo", "Rule: onScreenAppeared ${rule.screenName}")
        }

        override fun onScreenWillDismiss(rule: Rule, error: ApphudError?) {
            Log.d("ApphudLogsDemo", "Rule: onScreenWillDismiss ${rule.screenName}, error=${error?.message}")
        }

        override fun onScreenDidDismiss(rule: Rule) {
            Log.d("ApphudLogsDemo", "Rule: onScreenDidDismiss ${rule.screenName}")
        }

        override fun onWillPurchase(rule: Rule, product: ApphudProduct?) {
            Log.d("ApphudLogsDemo", "Rule: onWillPurchase ${product?.productId}")
        }

        override fun onPurchaseCompleted(rule: Rule, result: ApphudPurchaseResult) {
            Log.d("ApphudLogsDemo", "Rule: onPurchaseCompleted, error=${result.error?.message}")
        }

        override fun onDidSelectSurveyAnswer(rule: Rule, question: String, answer: String) {
            Log.d("ApphudLogsDemo", "Rule: onDidSelectSurveyAnswer question='$question' answer='$answer'")
        }

        override fun onScreenDismissAction(rule: Rule): ApphudScreenDismissAction {
            return ApphudScreenDismissAction.THANK_AND_CLOSE
        }

        override fun onRulePaywallWithoutScreen(rule: Rule, paywall: ApphudPaywall) {
            Log.d("ApphudLogsDemo", "Rule: onRulePaywallWithoutScreen paywall=${paywall.identifier}")
            // Present the paywall using your own UI here.
        }
    }

    fun fetchPlacements() {
        if (attempt > 10) {
            Log.d(
                "ApphudLogsDemo",
                "Too many attempts. Try to load placements again only when paywall is going to be shown"
            )
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
                Log.d(
                    "ApphudLogsDemo",
                    "Placements are loaded, however there is Google Billing Issue (${apphudError.billingErrorTitle()}): ask user to sign in to Google Play and try again later."
                )
                // Developer can retry fetchPlacements() immediately or after user taps "Try again" button in your custom UI.
                fetchPlacements()
            } else if (apphudError?.networkIssue() == true) {
                Log.d(
                    "ApphudLogsDemo",
                    "Failed to load placements due to Internet connection issue, ask user to connect to the Internet and try again later."
                )
                // Developer can retry fetchPlacements() immediately or after user taps "Try again" button in your custom UI.
                fetchPlacements()
            } else {
                // unknown or server-side error, try to load fallback paywalls
                Apphud.loadFallbackPaywalls { paywalls, fallbackError ->
                    if (!paywalls.isNullOrEmpty() && fallbackError?.billingErrorTitle() == null) {
                        Log.d(
                            "ApphudLogsDemo",
                            "Fallback paywalls are loaded from JSON, use them instead of placements"
                        )
                        // Grab the paywall and display it
                        // ---->> FALLBACK PAYWALLS HERE, USE PAYWALLS WITHOUT PLACEMENTS
                    } else if (fallbackError?.billingErrorTitle() != null) {
                        Log.d(
                            "ApphudLogsDemo",
                            "Fallback paywalls are loaded, however there is Google Billing Issue (${fallbackError.billingErrorTitle()}): ask user to sign in to Google Play and try again later."
                        )
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
