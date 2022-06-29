package com.apphud.sdk.internal

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.SkuDetails
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.isSuccess
import com.apphud.sdk.logMessage

internal class FlowWrapper(private val billing: BillingClient) {

    fun purchases(activity: Activity, details: SkuDetails, deviceId: String? = null) {
        val builder = BillingFlowParams.newBuilder()
            .setSkuDetails(details)

        deviceId?.let{
            val regex = Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")
            if(regex.matches(input = it)){
                builder.setObfuscatedAccountId(it)
            }
        }

        val params = builder.build()

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