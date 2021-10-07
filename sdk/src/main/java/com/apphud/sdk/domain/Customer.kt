package com.apphud.sdk.domain

data class Customer(
    val user: ApphudUser,
    val subscriptions: List<ApphudSubscription>,
    val purchases: List<ApphudNonRenewingPurchase>,
    val paywalls: List<ApphudPaywall>
)