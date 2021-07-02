package com.apphud.sdk.internal

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.SkuDetails
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.client.ApphudClient
import com.apphud.sdk.isSuccess
import com.apphud.sdk.logMessage

internal class FlowWrapper(private val billing: BillingClient) {

    fun purchases(activity: Activity, details: SkuDetails, client: ApphudClient) {
        val params = BillingFlowParams.newBuilder()
            .setSkuDetails(details)
            .build()

        billing.launchBillingFlow(activity, params)
            .also {
                when (it.isSuccess()) {
                    true -> {
                        ApphudLog.log("Success response launch Billing Flow")
                    }
                    else -> {
                        val message = "Failed launch Billing Flow"
                        it.logMessage(message)
                    }
                }
            }

    }
}