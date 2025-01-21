package com.apphud.sdk.domain

import com.android.billingclient.api.ProductDetails
import com.google.gson.annotations.SerializedName

internal class Offer(productDetails: ProductDetails, offerTokenId: String) {
    @SerializedName("base_plan_id")
    val basePlanId: String? = productDetails.subscriptionOfferDetails?.find { it.offerToken == offerTokenId }?.basePlanId
    @SerializedName("offer_id")
    val offerId: String? = productDetails.subscriptionOfferDetails?.find { it.offerToken == offerTokenId }?.offerId
    @SerializedName("pricing_phases")
    val pricingPhases: List<PriceInfo>? =
        productDetails.subscriptionOfferDetails?.find {
            it.offerToken == offerTokenId
        }?.pricingPhases?.pricingPhaseList?.map { PriceInfo(it) }
}
