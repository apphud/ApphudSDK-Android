package com.apphud.sampleapp.ui.utils

import android.app.Activity
import android.app.Application
import android.util.Log
import com.android.billingclient.api.ProductDetails
import com.apphud.sampleapp.BuildConfig
import com.apphud.sampleapp.R
import com.apphud.sampleapp.ui.models.HasPremiumEvent
import com.apphud.sampleapp.ui.models.PlacementJson
import com.apphud.sampleapp.ui.models.Paywall
import com.apphud.sdk.Apphud
import com.apphud.sdk.ApphudError
import com.apphud.sdk.ApphudListener
import com.apphud.sdk.ApphudPurchasesRestoreCallback
import com.apphud.sdk.ApphudUserPropertyKey
import com.apphud.sdk.ApphudUtils
import com.apphud.sdk.domain.ApphudNonRenewingPurchase
import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudPlacement
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.domain.ApphudSubscription
import com.apphud.sdk.domain.ApphudUser
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.greenrobot.eventbus.EventBus

enum class Placement (val placementId: String, val paywallId: String) {
    onboarding("pl_onboarding", "pw_onboarding"),
    main("pl_main", "pw_main"),
    settings("pl_settings", "pw_settings");

    companion object {
        fun getPlacementByName(value: String): Placement {
            val result = values().firstOrNull { it.placementId == value }
            result?.let {
                return it
            }
            return onboarding
        }
    }
}

object PurchaseManager {
    private val API_KEY = "app_q1opvXjFE1ADcjrGnvNnFVYu1tzh6d"
    private lateinit var application :Application

    var isApphudReady = false
    val gson = GsonBuilder().serializeNulls().create()
    val parser: Parser = GsonParser(gson)

    val listener = object : ApphudListener {
            override fun apphudSubscriptionsUpdated(subscriptions: List<ApphudSubscription>) {
                Log.d("ColorGenerator", "apphudSubscriptionsUpdated")
                notifyAboutPremium()
            }

            override fun apphudNonRenewingPurchasesUpdated(purchases: List<ApphudNonRenewingPurchase>) {
                Log.d("ColorGenerator", "apphudNonRenewingPurchasesUpdated")
                notifyAboutPremium()
            }

            override fun apphudFetchProductDetails(details: List<ProductDetails>) {
                Log.d("ColorGenerator", "apphudFetchProductDetails()")
            }

            override fun apphudDidChangeUserID(userId: String) {
                Log.d("ColorGenerator", "apphudDidChangeUserID()")
            }

            override fun userDidLoad(user: ApphudUser) {
                Log.d("ColorGenerator", "userDidLoad(): ${user.userId}")
            }

            override fun paywallsDidFullyLoad(paywalls: List<ApphudPaywall>) {
                Log.d("ColorGenerator", "paywallsDidFullyLoad()")
            }

            override fun placementsDidFullyLoad(placements: List<ApphudPlacement>) {
                Log.d("ColorGenerator", "placementsDidFullyLoad()")
                isApphudReady = true
            }
        }


    fun start(application: Application){
        this.application = application

        if (BuildConfig.DEBUG) {
            Apphud.enableDebugLogs()
            ApphudUtils.enableDebugLogs()
        }
        Apphud.setListener(listener)
        Apphud.start(application, API_KEY)
        Apphud.collectDeviceIdentifiers()

        AnalyticsManager.initAnalytics(application)
    }

    suspend fun getPaywallProducts(placement: Placement) :List<ApphudProduct>{
        return Apphud.placement(placement.placementId )?.paywall?.products?: listOf()
    }

    suspend fun getPlacementInfo(placement: Placement) :PlacementJson?{
        var placementJson :PlacementJson? = null
        Apphud.placement(placement.placementId)?.paywall?.json?.let{
            placementJson = parser.fromJson<PlacementJson>(Gson().toJson(it), object : TypeToken<PlacementJson>() {}.type)
        }
        return placementJson
    }

    private fun notifyAboutPremium(){
        if(Apphud.hasPremiumAccess()){
            EventBus.getDefault().post(HasPremiumEvent())
        }
    }

    fun isPremium() :Boolean? {
        return Apphud.hasPremiumAccess()
    }

    fun restorePurchases(completionHandler :(subscriptions: List<ApphudSubscription>?, purchases: List<ApphudNonRenewingPurchase>?, error: ApphudError?) -> Unit) {
        Apphud.restorePurchases(object : ApphudPurchasesRestoreCallback {
            override fun invoke(
                subscriptions: List<ApphudSubscription>?,
                purchases: List<ApphudNonRenewingPurchase>?,
                error: ApphudError?
            ) {
                completionHandler(subscriptions, purchases, error)
            }
        })
    }

    fun purchaseProduct(activity: Activity, product: ApphudProduct, completionHandler:(isSuccess: Boolean, error: ApphudError?) -> Unit) {
        if(isApphudReady){
            Apphud.purchase(activity, product) { result ->
                result.error?.let { err ->
                    completionHandler(false, err)
                } ?: run {
                    completionHandler(true, null)
                }
            }
        } else{
            completionHandler(false, ApphudError(ResourceManager.getString(R.string.error_default)))
        }
    }

    suspend fun placementShown(placement: Placement){
        Apphud.placement(placement.placementId)?.paywall?.let{
            Apphud.paywallShown(it)
        }
    }

    suspend fun placementClosed(placement: Placement){
        Apphud.placement(placement.placementId)?.paywall?.let{
            Apphud.paywallShown(it)
        }
    }

    fun addUserProperty(color: String, generationsCount: Int){
        Apphud.setUserProperty(
            key = ApphudUserPropertyKey.CustomProperty("copied_color"),
            value = color,
            setOnce = false
        )
        Apphud.incrementUserProperty(
            key = ApphudUserPropertyKey.CustomProperty("generations_count"),
            by = generationsCount)
    }
}