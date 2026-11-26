package com.apphud.sdk

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.apphud.sdk.domain.ApphudProduct
import com.apphud.sdk.internal.domain.model.PurchasedPlan
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class PurchasedPlanTest {

    private fun offer(basePlan: String, token: String): ProductDetails.SubscriptionOfferDetails = mockk {
        every { basePlanId } returns basePlan
        every { offerToken } returns token
    }

    private val offers = listOf(offer("weekly", "weekly-token"), offer("annual", "annual-token"))
    private val subscription: ProductDetails = mockk {
        every { productType } returns BillingClient.ProductType.SUBS
        every { subscriptionOfferDetails } returns offers
    }
    private val oneTime: ProductDetails = mockk {
        every { productType } returns BillingClient.ProductType.INAPP
    }

    private fun bundle(basePlan: String?): ApphudProduct = mockk {
        every { id } returns "bundle"
        every { productId } returns "pro"
        every { basePlanId } returns basePlan
    }

    @Test
    fun `GIVEN a bundle of the base plan bought EXPECT both`() {
        assertEquals(PurchasedPlan("annual", "bundle"), PurchasedPlan.of(bundle("annual"), subscription, "annual-token"))
    }

    @Test
    fun `GIVEN the offer of another base plan EXPECT the plan bought and no bundle`() {
        assertEquals(PurchasedPlan("weekly", null), PurchasedPlan.of(bundle("annual"), subscription, "weekly-token"))
    }

    @Test
    fun `GIVEN an offer token Google Play didn't list EXPECT the product's base plan`() {
        assertEquals(PurchasedPlan("annual", "bundle"), PurchasedPlan.of(bundle("annual"), subscription, "unknown"))
    }

    @Test
    fun `GIVEN a bundle without a base plan EXPECT the plan of the offer`() {
        assertEquals(PurchasedPlan("weekly", "bundle"), PurchasedPlan.of(bundle(null), subscription, "weekly-token"))
    }

    @Test
    fun `GIVEN a blank base plan EXPECT it treated as none`() {
        assertEquals(PurchasedPlan("weekly", "bundle"), PurchasedPlan.of(bundle(""), subscription, "weekly-token"))
    }

    @Test
    fun `GIVEN a one-time product EXPECT only the bundle`() {
        assertEquals(PurchasedPlan(null, "bundle"), PurchasedPlan.of(bundle(null), oneTime, "weekly-token"))
    }

    @Test
    fun `GIVEN a product made up from a product id EXPECT no bundle`() {
        val product = ApphudProduct.apphudProduct("pro")

        assertEquals(PurchasedPlan("weekly", null), PurchasedPlan.of(product, subscription, "weekly-token"))
    }
}
