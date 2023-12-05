package com.apphud.sdk.domain

import com.android.billingclient.api.ProductDetails

class PricingPhase(phase: ProductDetails.PricingPhase) {
    val billing_cycle_count = phase.billingCycleCount
    val billing_period = phase.billingPeriod
    var price_amount_micros = phase.priceAmountMicros
    val price_currency_code = phase.priceCurrencyCode
    var recurrence_mode = phase.recurrenceMode
}
