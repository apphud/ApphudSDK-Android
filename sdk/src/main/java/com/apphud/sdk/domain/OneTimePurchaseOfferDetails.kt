package com.apphud.sdk.domain

data class OneTimePurchaseOfferDetails (
    var priceAmountMicros: Long,
    var formattedPrice: String?,
    var priceCurrencyCode: String?,
    var offerIdToken: String?,
)