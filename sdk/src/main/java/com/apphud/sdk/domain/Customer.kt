package com.apphud.sdk.domain

data class Customer(
    val user: com.apphud.sdk.domain.ApphudUser,
    val subscriptions: List<com.apphud.sdk.domain.ApphudSubscription>,
    val purchases: List<com.apphud.sdk.domain.ApphudNonRenewingPurchase>
)