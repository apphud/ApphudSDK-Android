package com.apphud.sdk.client.dto

data class CustomerDto(
    val user_id: String,
    val subscriptions: List<SubscriptionDto>,
    val currency: CurrencyDto?,
    val paywalls: List<ApphudPaywallDto>?,
    val placements: List<ApphudPlacementDto>?
)