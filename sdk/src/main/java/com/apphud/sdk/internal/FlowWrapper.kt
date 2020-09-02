package com.apphud.sdk.internal

import android.app.Activity
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.SkuDetails
import com.apphud.sdk.response

internal class FlowWrapper(
    private val billing: BillingClient
) {

    fun purchases(activity: Activity, details: SkuDetails) {
        val params = BillingFlowParams.newBuilder()
            .setSkuDetails(details)
            .build()

        billing
            .launchBillingFlow(activity, params)
            .response("Failed launchBillingFlow") {
                Log.e("Billing", "Success response launchBillingFlow")
            }
    }
}