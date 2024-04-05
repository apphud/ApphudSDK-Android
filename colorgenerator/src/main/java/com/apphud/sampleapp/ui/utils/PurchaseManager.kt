package com.apphud.sampleapp.ui.utils

import android.app.Application
import android.util.Log
import com.android.billingclient.api.ProductDetails
import com.apphud.sampleapp.BuildConfig
import com.apphud.sdk.Apphud
import com.apphud.sdk.ApphudListener
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
            }

            override fun apphudNonRenewingPurchasesUpdated(purchases: List<ApphudNonRenewingPurchase>) {
                Log.d("ColorGenerator", "apphudNonRenewingPurchasesUpdated")
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
    }

    suspend fun getPaywallProducts(placement: Placement) :List<ApphudProduct>{
        var result :List<ApphudProduct> =  listOf()
        if(isApphudReady){
            result = Apphud.placement(placement.placementId )?.paywall?.products?: listOf()
        }
        return result
    }

    suspend fun getPaywallColor(placement: Placement) :String?{
        var result :String? = null
        if(isApphudReady){
            Apphud.placement(placement.placementId)?.paywall?.json?.let{
                val color :PaywallColor? = parser.fromJson<PaywallColor>(Gson().toJson(it), object : TypeToken<PaywallColor>() {}.type)
                result = color?.color
            }
        }
        return result
    }

    fun isPremium() :Boolean? {
        if(isApphudReady){
            return Apphud.hasPremiumAccess()
        }
        return null
    }

}