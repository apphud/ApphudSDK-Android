package com.apphud.sdk.domain

import com.apphud.sdk.UserId

data class ApphudUser(
    val userId: UserId,
    val currencyCode: String?,
    val countryCode: String?,
    val subscriptions: MutableList<ApphudSubscription>,
    val purchases: MutableList<ApphudNonRenewingPurchase>,
    val paywalls: List<ApphudPaywall>,
    val placements: List<ApphudPlacement>?,
    val isTemporary: Boolean?,
)
