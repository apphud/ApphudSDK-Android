package com.apphud.sdk.internal

import android.app.Activity
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.isSuccess
import com.apphud.sdk.logMessage
import com.xiaomi.billingclient.api.BillingClient
import com.xiaomi.billingclient.api.BillingFlowParams
import com.xiaomi.billingclient.api.SkuDetails

internal class FlowWrapper(private val billing: BillingClient) {
    var obfuscatedAccountId: String? = null

    fun purchases(
        activity: Activity,
        details: SkuDetails,
        deviceId: String? = null,
    ) {
        obfuscatedAccountId =
            deviceId?.let {
                val regex = Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")
                if (regex.matches(input = it)) {
                    it
                } else {
                    null
                }
            }

        try {

            val params = BillingFlowParams.newBuilder().setSkuDetails(details)
                //.setObfuscatedAccountId("xxx") //TODO changes
                //.setObfuscatedProfileId("yyy")
                //.setWebHookUrl("https://")
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
        } catch (ex: Exception) {
            ex.message?.let { ApphudLog.logE(it) }
        }
    }
}
