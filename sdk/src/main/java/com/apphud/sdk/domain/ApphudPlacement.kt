package com.apphud.sdk.domain

import java.util.UUID

data class ApphudPlacement(
    /**
     * Placement identifier configured in Apphud Mission control > Placements.
     */
    val identifier: String,
    val paywall: ApphudPaywall?,
    internal val id: String,
) {
    /**
     * @return A/B experiment name if this placement is part of an A/B test.
     */
    var experimentName: String? = paywall?.experimentName

    /**
     * @return Variation name if this placement is part of an A/B test.
     */
    val variationName: String? get() = paywall?.variationName

    companion object {
        fun createCustom(identifier: String, paywall: ApphudPaywall): ApphudPlacement {
            return ApphudPlacement(identifier, paywall, UUID.randomUUID().toString())
        }
    }
}
