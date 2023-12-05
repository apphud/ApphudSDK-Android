package com.apphud.sdk.domain

data class Customer(
    val user: ApphudUser,
    val subscriptions: MutableList<ApphudSubscription>,
    val purchases: MutableList<ApphudNonRenewingPurchase>,
    val paywalls: List<ApphudPaywall>,
    val isTemporary: Boolean?,
)
