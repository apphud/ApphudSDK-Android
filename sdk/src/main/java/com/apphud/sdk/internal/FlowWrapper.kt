package com.apphud.sdk.internal

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.ProductDetails
import com.apphud.sdk.Apphud
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.isSuccess
import com.apphud.sdk.logMessage

internal class FlowWrapper(private val billing: BillingClient) {

    var obfuscatedAccountId :String? = null

    fun purchases(activity: Activity, details: ProductDetails,
                  offerToken: String? = null,
                  oldToken: String? = null,
                  prorationMode: Int?,
                  deviceId: String? = null) {

        obfuscatedAccountId = deviceId?.let{
            val regex = Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")
            if(regex.matches(input = it)){
                it
            }else null
        }

        try{
            val params :BillingFlowParams =
                if(offerToken != null){
                    if(oldToken!= null){
                        upDowngradeBillingFlowParamsBuilder(details, offerToken, oldToken, prorationMode)
                    }else{
                        billingFlowParamsBuilder(details, offerToken)
                    }
                }else{
                    billingFlowParamsBuilder(details)
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
        }catch (ex: Exception){
            ex.message?.let { ApphudLog.logE(it) }
        }
    }

    /**
     * BillingFlowParams Builder for upgrades and downgrades.
     *
     * @param productDetails ProductDetails object returned by the library.
     * @param offerToken offer id token
     * @param oldToken the purchase token of the subscription purchase being upgraded or downgraded.
     *
     * @return [BillingFlowParams].
     */
    private fun upDowngradeBillingFlowParamsBuilder(
        productDetails: ProductDetails,
        offerToken: String,
        oldToken: String,
        prorationMode: Int?
    ): BillingFlowParams {
        val pMode = prorationMode ?: BillingFlowParams.ProrationMode.IMMEDIATE_AND_CHARGE_FULL_PRICE
        return BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .setOfferToken(offerToken)
                    .build()
            )
        ).setSubscriptionUpdateParams(
            BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                .setOldPurchaseToken(oldToken)
                .setReplaceProrationMode(
                    pMode
                )
                .build()
        )
        .apply { obfuscatedAccountId?.let { setObfuscatedAccountId(it) } }
        .build()
    }

    /**
     * BillingFlowParams Builder for normal purchases.
     *
     * @param productDetails ProductDetails object returned by the library.
     * @param offerToken  offer id token
     *
     * @return [BillingFlowParams].
     */
    private fun billingFlowParamsBuilder(
        productDetails: ProductDetails,
        offerToken: String
    ): BillingFlowParams {
        return BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .setOfferToken(offerToken)
                    .build()
            )
        )
        .apply { obfuscatedAccountId?.let { setObfuscatedAccountId(it) } }
        .build()
    }

    /**
     * BillingFlowParams Builder for normal purchases.
     *
     * @param productDetails ProductDetails object returned by the library.
     *
     * @return [BillingFlowParams].
     */
    private fun billingFlowParamsBuilder(
        productDetails: ProductDetails
    ): BillingFlowParams{
        return BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .build()
            )
        )
        .apply { obfuscatedAccountId?.let { setObfuscatedAccountId(it) } }
        .build()
    }
}