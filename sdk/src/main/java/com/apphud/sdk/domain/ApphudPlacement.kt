package com.apphud.sdk.domain

data class ApphudPlacement(
    val identifier: String,
    val paywall: ApphudPaywall?,
    internal val id: String,
) {
    /**
     * @return A/B experiment name of it's paywall, if any.
     */
    var experimentName: String? = paywall?.experimentName
}
