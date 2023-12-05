package com.apphud.sdk.client.dto

data class CustomerDto(
    val id: String,
    val user_id: String,
    val locale: String,
    val subscriptions: List<SubscriptionDto>,
    val currency: CurrencyDto?,
    val paywalls: List<ApphudPaywallDto>?,
)
