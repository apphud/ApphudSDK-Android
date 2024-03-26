package com.apphud.sdk.domain

data class SubscriptionOfferDetails (
    var pricingPhases: PricingPhases?,
    var basePlanId: String?,
    var offerId: String?,
    var offerToken: String?,
    var offerTags: List<String>?
)