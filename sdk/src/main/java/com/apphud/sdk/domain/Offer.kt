package com.apphud.sdk.domain

import com.android.billingclient.api.ProductDetails

class Offer(productDetails: ProductDetails, offerTokenId: String) {
    val base_plan_id: String? = productDetails.subscriptionOfferDetails?.find { it.offerToken == offerTokenId }?.basePlanId
    val offer_id: String? = productDetails.subscriptionOfferDetails?.find { it.offerToken == offerTokenId }?.offerId
    val pricing_phases: List<PricePhase>? =
        productDetails.subscriptionOfferDetails?.find {
            it.offerToken == offerTokenId
        }?.pricingPhases?.pricingPhaseList?.map { PricePhase(it) }
}
