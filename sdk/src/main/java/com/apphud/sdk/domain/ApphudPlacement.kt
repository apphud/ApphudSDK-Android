package com.apphud.sdk.domain

import java.util.UUID

data class ApphudPlacement(
    val identifier: String,
    val paywall: ApphudPaywall?,
    internal val id: String,
) {
    /**
     * @return A/B experiment name of it's paywall, if any.
     */
    var experimentName: String? = paywall?.experimentName

    companion object {
        fun createCustom(identifier: String, paywall: ApphudPaywall): ApphudPlacement {
            return ApphudPlacement(identifier, paywall, UUID.randomUUID().toString())
        }
    }
}
