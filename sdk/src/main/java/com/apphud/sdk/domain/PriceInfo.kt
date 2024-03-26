package com.apphud.sdk.domain

import com.xiaomi.billingclient.api.SkuDetails


class PriceInfo(phase: SkuDetails.PricingPhase) {
    val billing_cycle_count = phase.billingCycleCount
    val billing_period = phase.billingPeriod
    var price_amount_micros = phase.priceAmountMicros
    val price_currency_code = phase.priceCurrencyCode
    var recurrence_mode = phase.recurrenceMode
}
