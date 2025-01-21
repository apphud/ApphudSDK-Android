package com.apphud.sdk.domain

data class PricingPhase(
    var billingPeriod: String?,
    var priceCurrencyCode: String?,
    var formattedPrice: String?,
    var priceAmountMicros: Long,
    var recurrenceMode: RecurrenceMode,
    var billingCycleCount: Int,
)