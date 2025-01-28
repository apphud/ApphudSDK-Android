package com.apphud.sdk.domain

import com.android.billingclient.api.ProductDetails
import com.google.gson.annotations.SerializedName

internal class PriceInfo(phase: ProductDetails.PricingPhase) {
    @SerializedName("billing_cycle_count")
    val billingCycleCount = phase.billingCycleCount
    @SerializedName("billing_period")
    val billingPeriod = phase.billingPeriod
    @SerializedName("price_amount_micros")
    var priceAmountMicros = phase.priceAmountMicros
    @SerializedName("price_currency_code")
    val priceCurrencyCode = phase.priceCurrencyCode
    @SerializedName("recurrence_mode")
    var recurrenceMode = phase.recurrenceMode
}
