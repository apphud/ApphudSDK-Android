package com.apphud.sdk.internal.data

import com.apphud.sdk.domain.ApphudPaywall
import com.apphud.sdk.domain.ApphudPlacement
import com.apphud.sdk.domain.ApphudUser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SdkRegistrationStateTest {

    @Test
    fun `GIVEN cached user with placements EXPECT paywalls responded`() {
        val state = SdkRegistrationState(observerMode = false)

        state.markPaywallsRespondedForUser(user(placements = listOf(placement())))

        assertTrue(state.hasRespondedToPaywallsRequest)
    }

    @Test
    fun `GIVEN cached user without placements and observer mode false EXPECT paywalls not responded`() {
        val state = SdkRegistrationState(observerMode = false)

        state.markPaywallsRespondedForUser(user(placements = emptyList()))

        assertFalse(state.hasRespondedToPaywallsRequest)
    }

    @Test
    fun `GIVEN observer mode true EXPECT paywalls responded`() {
        val state = SdkRegistrationState(observerMode = true)

        state.markPaywallsRespondedForUser(user(placements = emptyList()))

        assertTrue(state.hasRespondedToPaywallsRequest)
    }

    private fun user(placements: List<ApphudPlacement>) = ApphudUser(
        userId = "user-id",
        currencyCode = null,
        countryCode = null,
        subscriptions = emptyList(),
        purchases = emptyList(),
        placements = placements,
        isTemporary = false,
    )

    private fun placement() = ApphudPlacement(
        identifier = "placement",
        paywall = ApphudPaywall(
            id = "paywall-id",
            name = "Paywall",
            identifier = "paywall",
            default = false,
            json = null,
            products = null,
            screen = null,
            experimentName = null,
            variationName = null,
            parentPaywallIdentifier = null,
            placementIdentifier = "placement",
            placementId = "placement-id",
        ),
        id = "placement-id",
    )
}
