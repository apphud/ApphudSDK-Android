package com.apphud.sdk.internal.xiaomi

import android.app.Activity
import com.apphud.sdk.ApphudLog
import com.xiaomi.billingclient.api.BillingClient
import com.xiaomi.billingclient.api.BillingClientStateListener
import com.xiaomi.billingclient.api.BillingResult
import com.xiaomi.billingclient.api.PurchasesUpdatedListener


class XiaomiBillingWrapper (val activity: Activity){

    private var billingClient :BillingClient
    private val billingClientStateListener: BillingClientStateListener =
        object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                ApphudLog.log("Xiaomi Billing:disconnected")
            }
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    ApphudLog.log("Xiaomi Billing: connected")
                } else {
                    ApphudLog.log("Xiaomi Billing: ${billingResult.responseCode}")
                }
            }
        }

    val purchasesUpdatedListener: PurchasesUpdatedListener =
        PurchasesUpdatedListener { billingResult, purchases ->

        }

    init {
        billingClient = BillingClient.newBuilder(activity).setListener(purchasesUpdatedListener).build()
        billingClient.enableFloatView(activity)
        billingClient.startConnection(billingClientStateListener)
    }
}
