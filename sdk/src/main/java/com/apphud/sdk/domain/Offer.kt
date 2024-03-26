package com.apphud.sdk.domain

import com.xiaomi.billingclient.api.SkuDetails

class Offer(skuDetails: SkuDetails, offerTokenId: String) {
    val base_plan_id: String? = skuDetails.subscriptionOfferDetails?.find { it.offerToken == offerTokenId }?.basePlanId
    val offer_id: String? = skuDetails.subscriptionOfferDetails?.find { it.offerToken == offerTokenId }?.offerId
    val pricing_phases: List<PriceInfo>? =
        skuDetails.subscriptionOfferDetails?.find {
            it.offerToken == offerTokenId
        }?.pricingPhases?.pricingPhaseList?.map { PriceInfo(it) }
}