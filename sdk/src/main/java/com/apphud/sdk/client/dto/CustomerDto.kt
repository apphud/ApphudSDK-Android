package com.apphud.sdk.client.dto

import com.google.gson.annotations.SerializedName

internal data class CustomerDto(
    @SerializedName("user_id")
    val userId: String,
    val subscriptions: List<SubscriptionDto>,
    val currency: CurrencyDto?,
    val paywalls: List<ApphudPaywallDto>?,
    val placements: List<ApphudPlacementDto>?,
)
