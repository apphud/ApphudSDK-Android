package com.apphud.sdk.domain

data class ApphudPlacement(
    val identifier: String,
    val paywall: ApphudPaywall?,
    val experimentName: String?,
    internal val id: String,
)
