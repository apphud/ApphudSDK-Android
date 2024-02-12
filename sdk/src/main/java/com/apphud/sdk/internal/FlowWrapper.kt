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
        offerToken: String? = null,
        oldToken: String? = null,
        replacementMode: Int?,
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

            val params: BillingFlowParams =
                if (offerToken != null) {
                    if (oldToken != null) {
                        upDowngradeBillingFlowParamsBuilder(details, offerToken, oldToken, replacementMode)
                    } else {
                        billingFlowParamsBuilder(details, offerToken).build()
                    }
                } else {
                    billingFlowParamsBuilder(details).build()
                }

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

    /**
     * BillingFlowParams Builder for upgrades and downgrades.
     *
     * @param skuDetails SkuDetails object returned by the library.
     * @param offerToken the least priced offer's offer id token returned by
     * [leastPricedOfferToken].
     * @param oldToken the purchase token of the subscription purchase being upgraded or downgraded.
     *
     * @return [BillingFlowParams] builder.
     */
    private fun upDowngradeBillingFlowParamsBuilder(
        skuDetails: SkuDetails,
        offerToken: String,
        oldToken: String,
        replacementMode: Int?
    ): BillingFlowParams {
        return  BillingFlowParams.newBuilder()
            .setSkuDetails(skuDetails)
            .setOfferToken(offerToken)
            .apply { obfuscatedAccountId?.let { setObfuscatedAccountId(it) } }
            //.setObfuscatedProfileId("")
            //.setWebHookUrl("")
            .setSubscriptionUpdateParams(BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                .setOldPurchaseToken(oldToken)
                .setSubscriptionReplacementMode(replacementMode?: BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.IMMEDIATE_AND_CHARGE_FULL_PRICE)
                .build()
            ).build()
    }

    /**
     * BillingFlowParams Builder for normal purchases.
     *
     * @param skuDetails SkuDetails object returned by the library.
     * @param offerToken the least priced offer's offer id token returned by
     * [leastPricedOfferToken].
     *
     * @return [BillingFlowParams] builder.
     */
    private fun billingFlowParamsBuilder(
        skuDetails: SkuDetails,
        offerToken: String? = null
    ): BillingFlowParams.Builder {
        offerToken?.let {
            return BillingFlowParams.newBuilder()
                .setSkuDetails(skuDetails)
                .setOfferToken(offerToken)
                .apply { obfuscatedAccountId?.let { setObfuscatedAccountId(it) } }
        } ?: run {
            return BillingFlowParams.newBuilder()
                .setSkuDetails(skuDetails)
        }
    }
}
